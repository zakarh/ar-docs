import glob
import os
import subprocess
import time
import traceback
from pathlib import Path

import numpy as np
from PIL import Image


class Doc():
    def __init__(self):
        self.supported_documents = set([
            "pdf"
        ])
        self.supported_images = set([
            "jpg", "jpeg", "png"
        ])
        self.supported_files = self.supported_documents | self.supported_images

    def get_name(self, filename: str) -> str:
        name = Path(filename).name
        i = name.rfind(".")
        if name != "":
            return name[:i]
        return None

    def get_suffix(self, filename: str) -> str:
        suffix = Path(filename).suffix
        if len(suffix) != 0:
            return Path(filename).suffix.lstrip(".").lower()
        return None

    def verify(self, filename: str) -> bool:
        return self.get_suffix(filename) in self.supported_files

    def extract(self, filename: str) -> tuple:
        return (self.get_name(filename), self.get_suffix(filename))

    def convert(self, directory: str, filename: str) -> bool:
        try:
            name, suffix = self.extract(filename)
            if name and suffix:
                if suffix in self.supported_documents:
                    if self.to_jpeg(directory, filename):
                        if self.to_obj(directory):
                            if self.to_gltf(directory):
                                return True
                elif suffix in self.supported_images:
                    if self.to_obj(directory):
                        if self.to_gltf(directory):
                            return True
            return False
        except Exception:
            traceback.print_exc()

    def to_jpeg(self, directory: str, filename: str) -> bool:
        try:
            absolute_path = os.path.join(os.getcwd(), directory)
            absolute_data_path = os.path.join(absolute_path, "data")
            name, extension = self.extract(filename)
            if extension == "pdf":
                commands = " && ".join([
                    f"cd {absolute_path}",
                    f"pdftoppm -jpeg -r 600 {filename} {absolute_data_path}/image"
                ])
                output = subprocess.check_output(commands, shell=True)
                # print(os.listdir(absolute_data_path))
                # print("SUCCESS")
                return True
            return False
        except Exception:
            traceback.print_exc()

    def to_obj(self, directory: str) -> bool:
        """
        Convert images in a directory into *.obj.
        """
        try:
            path_to_data = os.path.join(os.getcwd(), directory, "data")
            path_to_blender = os.path.join(
                os.getcwd(), "assets", "blender", "blender")
            # Process each image in the data directory.
            for filename in os.listdir(path_to_data):
                name, extension = self.extract(filename)
                if name and extension:
                    path_to_image = os.path.join(
                        path_to_data, filename)
                    # Use OpenCV to get the height, width, and channels of the image:
                    image = Image.open(path_to_image)
                    # image_data = np.array(cv2.imread(path_to_image))
                    width, height = image.size
                    channels = len(image.mode)
                    arguments = " ".join([
                        path_to_data,
                        filename, str(height), str(width), str(channels)
                    ])
                    # print(arguments)
                    commands = " && ".join([
                        f"{path_to_blender} --background --python blend.py -- {arguments}"
                    ])
                    # Run process in foreground (TESTING):
                    subprocess.check_output(commands, shell=True)
                    # output = subprocess.check_output(commands, shell=True)
                    # print(output.decode("utf-8"))
                    # print(os.listdir(absolute_data_path))
                else:
                    return False
            return True
        except Exception:
            traceback.print_exc()

    def to_gltf(self, directory: str) -> bool:
        try:
            absolute_path = os.path.join(os.getcwd(), directory)
            absolute_data_path = os.path.join(absolute_path, "data")
            absolute_data_obj = os.path.join(absolute_data_path, "*.obj")
            absolute_obj2gltf_bin = os.path.join(
                os.getcwd(), "assets", "obj2gltf", "bin")
            # print(os.listdir(os.getcwd()))
            for obj in glob.glob(absolute_data_obj):
                name, extension = self.extract(obj)
                if name and extension:
                    try:
                        commands = " && ".join([
                            # f"cd {absolute_data_path}",
                            f"nodejs {absolute_obj2gltf_bin}/obj2gltf.js -i {obj} -o {absolute_data_path}/{name}.gltf"
                        ])
                        subprocess.check_output(commands, shell=True)
                        # output = subprocess.check_output(commands, shell=True)
                        # print(output.decode("utf-8"))
                        # print(os.listdir(absolute_data_path))
                    except Exception:
                        traceback.print_exc()
                else:
                    return False
            return True
        except Exception:
            traceback.print_exc()
