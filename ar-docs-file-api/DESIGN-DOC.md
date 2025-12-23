# Design Document

This is simplified design document to provide an overview of what the application does.

## Architecture Overview

- A request is made to the API to convert the document.
- The document is temporarily saved to the server to be converted.
- Use Poppler to convert PDFs into images (Skip if an image).
- Use Blender to convert the images into OBJ file objects.
- Use Node.js to convert the OBJ file objects into GLTF file objects.
- The GLTF file objects are saved to a Firebase Realtime Database.

## API Overview

- The route **/api** will accept only **POST** requests.
  - Create a temporary directory to convert the document in the payload.
    - Reference file and form data in the payload.
    - Verify and authenticate id_token using Firebase Admin.
    - Create a folder called "data" to store converted files.
    - Save the document to the temporary directory.
    - Process the document and save the converted documents to the folder "data".
    - Prepare documents in the folder "data" and upload them to the Firebase Realtime Database.
- The route **/api/<user_id>/<document_id>/pages/<page_id>.gltf** will accept **GET** requests and retrieve the JSON data for the desired GLTF file object.
  - Use the user_id, document_id, and page_id to reference the desired JSON data associated with the GLTF file object.
- The route **/api/<user_id>/<document_id>/pages/<page_id>.bin** will accept **GET** requests and retrieve the binary data for the desired GLTF file object.
  - Use the user_id, document_id, and page_id to reference the desired binary data associated with the GLTF file object.

## Conversion Overview

- Determine the type of document that has been requested to be converted.
  - If a PDF convert the pages of the document into images.
- Start a sub process using Blender to convert the images into OBJ file objects.
- Start a sub process using Node.js to convert the OBJ file objects into GLTF file objects.
