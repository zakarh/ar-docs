import base64
import configparser
import datetime
import glob
import json
import os
import shutil
import tempfile
import time
import traceback
from pathlib import Path
from queue import PriorityQueue

import firebase_admin
import httplib2
from apiclient.discovery import build
from firebase_admin import auth, credentials, db, messaging
from flask import (Flask, Response, abort, flash, redirect, request,
                   send_from_directory, url_for)
from oauth2client import client
from oauth2client.service_account import ServiceAccountCredentials
from werkzeug.serving import WSGIRequestHandler
from werkzeug.utils import secure_filename

from doc import Doc
from helper import current_nanosec_time, extract_num, gen_id

config = configparser.ConfigParser()
config.read('app.cfg')

development_mode = True if config['app']['development_mode'] == 'True' else False
service_account_key = config['firebase']['service_account_key']
realtime_database_url = config['firebase']['realtime_database_url']

doc = Doc()

# Google API
service_account_email = config["google-api"]["service_account_email"]
service_account_key = config["google-api"]["service_account_key"]

with open(service_account_key) as f:
    service_account_key_data = json.loads(f.read())

service_credentials = ServiceAccountCredentials.from_json_keyfile_dict(
    service_account_key_data,
    scopes=["https://www.googleapis.com/auth/androidpublisher"]
)

http = httplib2.Http()
service_account = service_credentials.authorize(http)

# Firebase:
firebase_credentials = credentials.Certificate(service_account_key)
default_app = firebase_admin.initialize_app(firebase_credentials, {
    "databaseURL": realtime_database_url
})

# Firebase database reference.
user_ref = db.reference("production/user")
document_ref = db.reference("production/document")

if development_mode:
    user_ref = db.reference("development/user")
    document_ref = db.reference("development/document")


# Flask:
app = Flask(__name__)
app.secret_key = gen_id(r=1)
WSGIRequestHandler.protocol_version = "HTTP/1.1"


def send_notification(registration_token: str, document: str):
    try:
        message = messaging.Message(
            notification=messaging.Notification(
                title='Your document is ready for viewing.',
                body=document,
            ),
            token=registration_token,
        )
        messaging.send(message)
    except Exception:
        traceback.print_exc()


def active_subscription(uri):
    try:
        response, content = service_account.request(uri)
        current_time = datetime.timedelta(0, 0, 0, time.time()*1000)
        expiry_time = datetime.timedelta(0, 0, 0, int(
            json.loads(content).get("expiryTimeMillis", 0)))
        active = current_time < expiry_time
        print(current_time, '<', expiry_time, "expired", active)
        return active
    except Exception:  # Default
        print("active_subscription")
        traceback.print_exc()
        return False


@app.route("/status", methods=["POST"])
def status():
    try:
        packageName = request.form["packageName"]
        subscriptionId = request.form["subscriptionId"]
        token = request.form["tokens"]

        uri = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{}/purchases/subscriptions/{}/tokens/{}"
        uri = uri.format(packageName, subscriptionId, token)

        if active_subscription(uri):
            # Status: Active
            response = Response(
                response=json.dumps({"status": 1}),
                status=200,
                mimetype='application/json'
            )
            return response
        else:
            # Status: Inactive
            response = Response(
                response=json.dumps({"status": 0}),
                status=200,
                mimetype='application/json'
            )
            return response
    except Exception:
        # Status: Failed to retrieve
        print('@app.route("/status", methods=["POST"])')
        traceback.print_exc()
        response = Response(
            response=json.dumps({"status": -1}),
            status=500,
            mimetype='application/json'
        )
        return response


@app.route("/api/<user_id>/<document_id>/pages/<page_id>.gltf", methods=["GET"])
def gltf_data(user_id: str, document_id: str, page_id: str) -> str:
    data = document_ref.child(user_id).child(document_id).child(
        "pages").child(f"{page_id}").get("gltf")
    return data[0]["gltf"]


@app.route("/api/<user_id>/<document_id>/pages/<page_id>.bin", methods=["GET"])
def bin_data(user_id: str, document_id: str, page_id: str) -> bytes:
    data = document_ref.child(user_id).child(document_id).child(
        "pages").child(f"{page_id}").get("bin")
    decoded_bytes = base64.b64decode(data[0]["bin"])
    return decoded_bytes


if development_mode:
    @app.route("/dev", methods=["GET", "POST"])
    def dev():
        if request.method == "GET":
            return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="utf-8"/>
                    <title>upload</title>
                </head>
                <body>
                    <form action="/dev" method="post" enctype="multipart/form-data">
                        <p><input type="file" name="file">
                        <p><button type="submit">Submit</button>
                    </form>
                </body>
                </html>"""

        if request.method == "POST":
            last = current_nanosec_time()
            # Create a temporary directory.
            try:
                with tempfile.TemporaryDirectory(dir="") as directory:
                    try:
                        # Reference files and form data.
                        form_data = request.form.to_dict()
                        document = request.files["file"]
                    except Exception:
                        traceback.print_exc()
                        return abort(500)
                    try:
                        user_id = '_ardocs_development'
                        if user_id == "":
                            return abort(500)
                    except Exception:
                        traceback.print_exc()
                        return abort(500)
                    try:
                        # Create the 'data' directory to store converted files.
                        os.mkdir(os.path.join(os.getcwd(), directory, "data"))
                    except Exception:
                        traceback.print_exc()
                        return abort(500)
                    try:
                        # Secure document filename and save the document to the temporary directory.
                        filename = secure_filename(document.filename)
                        document.save(os.path.join(directory, filename))
                    except Exception:
                        traceback.print_exc()
                        return abort(500)
                    try:
                        # Convert the document page(s) into GLTFs.
                        if doc.verify(filename):
                            suffix = doc.get_suffix(filename)
                            if suffix in doc.supported_images:
                                shutil.copyfile(
                                    os.path.join(directory, filename),
                                    os.path.join(directory, "data", filename),
                                    follow_symlinks=False
                                )
                            result = doc.convert(directory, filename)
                            if result == False:
                                return abort(500)
                    except Exception:
                        traceback.print_exc()
                        return abort(500)
                    try:
                        # Get path to gltf(s) in the 'data' directory.
                        path_to_gltfs = os.path.join(
                            os.getcwd(), directory, "data", "*.gltf")
                        # Get a list of GLTFs at the provided path.
                        gltfs = glob.glob(path_to_gltfs)
                        # Generate the document ID.
                        document_id = gen_id()
                        # Upload the document data to firebase.
                        user_ref.child(user_id).child(document_id).set({
                            "name": filename,
                            "page_count": len(gltfs)
                        })
                        # Process and upload the data for each GLTF.
                        document_ref.child(user_id).child(document_id).set({
                            "pages": ""
                        })
                        for page_id, gltf_path in enumerate(sorted(gltfs, key=extract_num)):
                            with open(gltf_path) as f:
                                # Read the GLTF as json.
                                data = json.loads(f.read())
                                # Extract the URI.
                                uri = data["buffers"][0]["uri"]
                                # Clean the base64 string extracted from the URI.
                                part_to_remove = "data:application/octet-stream;base64,"
                                slice_index = len(part_to_remove)
                                base64_bytes = uri[slice_index:]
                                # Set the URI to <page_id>.bin.
                                data["buffers"][0]["uri"] = f"{page_id}.bin"
                                # Save data to firebase.
                                document_ref.child(user_id).child(document_id).child("pages").child(f"{page_id}").set({
                                    "gltf": json.dumps(data),
                                    "bin": base64_bytes
                                })
                        # time.sleep(60 * 5)
                        print('time', (current_nanosec_time() - last) / 1e9)
                        return document_id
                    except Exception:
                        traceback.print_exc()
                        return abort(500)
            except Exception:
                traceback.print_exc()
                return abort(500)


@app.route("/api", methods=["POST"])
def upload_file():
    """Upload a file and convert it into a GLTF and save it to Firebase."""
    if request.method == "POST":
        # Create a temporary directory.
        try:
            with tempfile.TemporaryDirectory(dir="") as directory:
                try:
                    # Reference files and form data.
                    form_data = request.form.to_dict()
                    id_token = form_data["id_token"]
                    registration_token = form_data["registration_token"]
                    document = request.files["file"]
                except Exception:
                    traceback.print_exc()
                    return abort(500)
                try:
                    # Verify token with Firebase Admin.
                    decoded_token = auth.verify_id_token(
                        id_token, app=firebase_admin.get_app())
                    user_id = decoded_token.get("uid", "")
                    if user_id == "":
                        return abort(500)
                except Exception:
                    traceback.print_exc()
                    return abort(500)

                try:
                    # Generate document_id and post it to the database.
                    document_id = gen_id()
                    # Process and upload the data for each GLTF.
                    user_ref.child(user_id).child(document_id).set({
                        "pages": ""
                    })
                except Exception:
                    try:
                        user_ref.child(user_id).child(document_id).delete()
                    except Exception:
                        traceback.print_exc()
                    traceback.print_exc()
                    return abort(500)

                try:
                    # Create the 'data' directory to store converted files.
                    os.mkdir(os.path.join(os.getcwd(), directory, "data"))
                except Exception:
                    try:
                        user_ref.child(user_id).child(document_id).delete()
                    except Exception:
                        traceback.print_exc()
                    traceback.print_exc()
                    return abort(500)
                try:
                    # Secure document filename and save the document to the temporary directory.
                    filename = secure_filename(document.filename)
                    document.save(os.path.join(directory, filename))
                except Exception:
                    try:
                        user_ref.child(user_id).child(document_id).delete()
                    except Exception:
                        traceback.print_exc()
                    traceback.print_exc()
                    return abort(500)
                try:
                    # Convert the document page(s) into GLTFs.
                    if doc.verify(filename):
                        suffix = doc.get_suffix(filename)
                        if suffix in doc.supported_images:
                            shutil.copyfile(
                                os.path.join(directory, filename),
                                os.path.join(directory, "data", filename),
                                follow_symlinks=False
                            )
                        result = doc.convert(directory, filename)
                        if result == False:
                            return abort(500)
                except Exception:
                    try:
                        user_ref.child(user_id).child(document_id).delete()
                    except Exception:
                        traceback.print_exc()
                    traceback.print_exc()
                    return abort(500)
                try:
                    # Get path to gltf(s) in the 'data' directory.
                    path_to_gltfs = os.path.join(
                        os.getcwd(), directory, "data", "*.gltf")
                    # Get a list of GLTFs at the provided path.
                    gltfs = glob.glob(path_to_gltfs)

                    # Upload the document data to firebase.
                    user_ref.child(user_id).child(document_id).set({
                        "name": filename,
                        "page_count": len(gltfs)
                    })
                    # Process and upload the data for each GLTF.
                    document_ref.child(user_id).child(document_id).set({
                        "pages": ""
                    })
                    for page_id, gltf_path in enumerate(sorted(gltfs, key=extract_num)):
                        with open(gltf_path) as f:
                            # Read the GLTF as json.
                            data = json.loads(f.read())
                            # Extract the URI.
                            uri = data["buffers"][0]["uri"]
                            # Clean the base64 string extracted from the URI.
                            part_to_remove = "data:application/octet-stream;base64,"
                            slice_index = len(part_to_remove)
                            base64_bytes = uri[slice_index:]
                            # Set the URI to <page_id>.bin.
                            data["buffers"][0]["uri"] = f"{page_id}.bin"
                            # Save data to firebase.
                            document_ref.child(user_id).child(document_id).child("pages").child(f"{page_id}").set({
                                "gltf": json.dumps(data),
                                "bin": base64_bytes
                            })
                    try:
                        send_notification(registration_token, filename)
                    except Exception:
                        traceback.print_exc()
                    return document_id
                except Exception:
                    try:
                        user_ref.child(user_id).child(document_id).delete()
                        document_ref.child(user_id).child(document_id).delete()
                    except Exception:
                        traceback.print_exc()
                    traceback.print_exc()
                    return abort(500)
        except Exception:
            traceback.print_exc()
            return abort(500)


if __name__ == "__main__":
    app.run(host="127.0.0.1", port="7000", debug=True)
