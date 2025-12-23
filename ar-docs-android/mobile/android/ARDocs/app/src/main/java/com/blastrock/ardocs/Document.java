package com.blastrock.ardocs;

import android.content.Context;
import android.net.Uri;

import com.google.ar.sceneform.assets.RenderableSource;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.concurrent.CompletableFuture;

public class Document {
    public seekBarController rotate = new seekBarController(0, 0, 360);
    public seekBarController elevate = new seekBarController(0, 0, 100);
    private Context context;
    private TransformableNode node;
    private String fileName;
    private String apiURL;
    private String userID;
    private String documentID;
    private int pageCount;
    private int currentPage = 0;

    public static void delete(FirebaseDatabase firebaseDatabase, String databaseType, String userID, String documentID) {
        DatabaseReference document = firebaseDatabase.getReference(String.format("%s/user/%s/%s", databaseType, userID, documentID));
        document.removeValue();
    }

    public void update() {
        String assetURL = String.format("%s/%s/%s/pages/%s.gltf", apiURL, userID, documentID, currentPage);
        CompletableFuture<ModelRenderable> model = ModelRenderable
                .builder()
                .setSource(context, RenderableSource.builder().setSource(context,
                        Uri.parse(assetURL),
                        RenderableSource.SourceType.GLTF2)
                        .setScale(0.20f)
                        .setRecenterMode(RenderableSource.RecenterMode.ROOT)
                        .build())
                .setRegistryId(assetURL)
                .build();
        model.thenAccept(renderable -> {
            node.setRenderable(renderable);
        }).exceptionally(
                throwable -> null);
    }

    public void setPage(int i) {
        currentPage = Integer.max(0, Integer.min(pageCount, i));
        update();
    }

    public void back() {
        currentPage = Integer.max(0, currentPage - 1);
        update();
    }

    public Integer getCurrentPage() {
        return currentPage;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public void setPageCount(Integer pageCount) {
        this.pageCount = pageCount;
    }

    public void forward() {
        currentPage = Integer.min(pageCount - 1, currentPage + 1);
        update();
    }

    public TransformableNode getNode() {
        return node;
    }

    public String getFileName() {
        return this.fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public void setApiURL(String apiURL) {
        this.apiURL = apiURL;
    }

    public void setUserID(String userID) {
        this.userID = userID;
    }

    public void setDocumentID(String documentID) {
        this.documentID = documentID;
    }

    public void setTransformableNode(TransformableNode node) {
        this.node = node;
    }

}
