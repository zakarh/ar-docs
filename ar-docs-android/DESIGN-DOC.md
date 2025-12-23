# Design Document

This is simplified design document to provide an overview of what the app does.

## Architecture Overview - Flow

- When the app is started it requires the user to login to the app.
- When the user is authenticated they are presented the main view of the app.
- Using the menu a user can select a document on their device to convert into a GLTF to be rendered.
- When a user's document is converted it is made available in a sidebar from which the user can select. If selected the user has the option to render the document or delete it from the database.
- When a user selects a document and it is ready to render the user can tap in the augmented reality space to render the document. Once rendered the user can rotate and scale the document. A user can render multiple of the same document at a time.
- A user can tap on a rendered document which provides information on the document and controls to page left and right or select a page (for multi-page documents), and  close the document if desired.

## API Overview

The API is public facing and routes HTTPS requests to [docs-api](https://github.com/zakarh/docs-api). The routes are all the same.

- The route **/api** will accept only **POST** requests.
- The route **/api/<user_id>/<document_id>/pages/<page_id>.gltf** will accept **GET** requests and retrieve the JSON data for the desired GLTF file object.
- The route **/api/<user_id>/<document_id>/pages/<page_id>.bin** will accept **GET** requests and retrieve the binary data for the desired GLTF file object.
