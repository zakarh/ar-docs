import calendar
import configparser
import http.client
import io
import mimetypes
import traceback
from uuid import uuid4

import requests
from flask import Flask, Response, abort, render_template, request, jsonify
from jinja2 import Markup
from requests_toolbelt.multipart.encoder import MultipartEncoder

from apiclient.discovery import build
import httplib2
from oauth2client import client
from oauth2client.service_account import ServiceAccountCredentials

import json

import time
import datetime


config = configparser.ConfigParser()
config.read("app.cfg")

host = config["address"]["host"]
port = config["address"]["port"]
route = config["address"]["route"]

endpoint = f"http://{host}:{port}/{route}"

print(endpoint)

# Google API
service_account_email = config["google-api"]["service_account_email"]
service_account_key = config["google-api"]["service_account_key"]

with open(service_account_key) as f:
    service_account_key_data = json.loads(f.read())

credentials = ServiceAccountCredentials.from_json_keyfile_dict(
    service_account_key_data,
    scopes=["https://www.googleapis.com/auth/androidpublisher"]
)

http = httplib2.Http()
service_account = credentials.authorize(http)


app = Flask(__name__)


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
        file = request.files["file"]
        mp_encoder = MultipartEncoder(
            fields={
                "packageName": request.form["packageName"],
                "subscriptionId": request.form["subscriptionId"],
                "tokens": request.form["tokens"],
            }
        )
        response = requests.post(
             f"{endpoint}/{status}",
            data=mp_encoder,
            headers={"Content-Type": mp_encoder.content_type}
        )
        return response.json
    except Exception:
        traceback.print_exc()
        return abort(500)


@app.route("/api", methods=["POST"])
def upload_file():
    try:
        file = request.files["file"]
        mp_encoder = MultipartEncoder(
            fields={
                "id_token": request.form["id_token"],
                "registration_token": request.form["registration_token"],
                "file": (file.filename, file.read(), file.content_type),
            }
        )
        response = requests.post(
            endpoint,
            data=mp_encoder,
            headers={"Content-Type": mp_encoder.content_type}
        )
        return response.text
    except Exception:
        traceback.print_exc()
        return abort(500)


@app.route("/api/<user_id>/<document_id>/pages/<page_id>.gltf", methods=["GET"])
def gltf_data(user_id: str, document_id: str, page_id: str) -> str:
    try:
        response = requests.get(
            f"{endpoint}/{user_id}/{document_id}/pages/{page_id}.gltf")
        return response.text
    except Exception:
        traceback.print_exc()
        return abort(500)


@app.route("/api/<user_id>/<document_id>/pages/<page_id>.bin", methods=["GET"])
def bin_data(user_id: str, document_id: str, page_id: str) -> bytes:
    try:
        response = requests.get(
            f"{endpoint}/{user_id}/{document_id}/pages/{page_id}.bin")
        return response.content
    except Exception:
        traceback.print_exc()
        return abort(500)


if __name__ == "__main__":
    app.run(host="127.0.0.1", port="5000", debug=True)
