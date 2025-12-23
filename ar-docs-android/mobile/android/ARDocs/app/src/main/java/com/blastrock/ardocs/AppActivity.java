package com.blastrock.ardocs;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.text.HtmlCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.reward.RewardItem;
import com.google.android.gms.ads.reward.RewardedVideoAd;
import com.google.android.gms.ads.reward.RewardedVideoAdListener;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.assets.RenderableSource;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class AppActivity extends FragmentActivity implements RewardedVideoAdListener {
    private static final int REQUEST_READ_EXTERNAL_STORAGE = 1000;
    private static final int REQUEST_FILE_PICKER = 2000;
    private static final int REQUEST_SIGN_IN = 3000;

    private String sku = "android.test.purchased";

    private Billing billing;
    private Authentication authentication;
    private Advertisement advertisement;

    private final String ipAddress = BuildConfig.DOC_API_IP_ADDRESS;
    private final String apiURL = ipAddress + "/api";

    private GoogleSignInClient googleSignInClient;
    private GoogleSignInAccount googleSignInAccount;
    private FirebaseAuth firebaseAuth;
    private FirebaseDatabase firebaseDatabase;
    private RewardedVideoAd mRewardedVideoAd;

    // AR
    private ArFragment arFragment;
    private ModelRenderable currentModelRenderable;
    private String activeAnchorNodeName;

    // Document Data
    private HashMap<String, Document> documents = new HashMap<>();
    private String documentID;
    private Integer pageCount;
    private String documentName;

    private Timer timer = new Timer();

    private static Object call() {

        return null;
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        String format = "REQUEST CODE: %s, RESULT CODE: %s";
        String msg = String.format(format, requestCode, resultCode);
        Log.w("onActivityResult", msg);
        if (resultCode != RESULT_OK) {
            return;
        }
        if (requestCode == REQUEST_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                googleSignInAccount = task.getResult(ApiException.class);
                assert googleSignInAccount != null;
                authenticate(googleSignInAccount);
            } catch (ApiException e) {
                updateUI(null);
            }
        }
        if (requestCode == REQUEST_FILE_PICKER) {
            Uri uri = data.getData();
            String filename = getRealPathFromURI(uri);
            DocumentDao.NumberOfDocuments(AppActivity.this, firebaseDatabase.getReference(), firebaseAuth.getUid(), new CallbackInterface.CallbackLong() {
                @Override
                public void call(long value) {
                    Log.d(this.toString(), "LONG: " + String.valueOf(value));
                    if (value < getResources().getInteger(R.integer.MAXIMUM_DOCUMENTS)) {
                        Alert.uploadDocumentAlertDialog(AppActivity.this, filename, new CallbackInterface.CallbackVoid() {
                            @Override
                            public void call() {
                                uploadDocument(uri, filename);
                            }
                        });
                    } else {
                        Alert.documentLimitReached(AppActivity.this);
                    }
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app);

        sku = "ardocs.subscription.premium.monthly";

        this.authentication = new Authentication();
        this.advertisement = new Advertisement();
        this.billing = new Billing(this);

        // Setup visibility of views.
        // Make views visible.
        findViewById(R.id.splashScreen).setVisibility(View.VISIBLE);
        findViewById(R.id.loginScreen).setVisibility(View.VISIBLE);
        findViewById(R.id.warningScreen).setVisibility(View.VISIBLE);

        // Make views gone.
        findViewById(R.id.appScreen).setVisibility(View.GONE);
        findViewById(R.id.adView).setVisibility(View.GONE);
        findViewById(R.id.pagingControls).setVisibility(View.GONE);
        findViewById(R.id.infoDisplay).setVisibility(View.INVISIBLE);
        findViewById(R.id.closeDocument).setVisibility(View.INVISIBLE);

        setupGoogleAdMob();
        setupGoogleSignIn();
        setupFirebaseResources();

        // Sign out the last user of the application.
        signOut();

        // Setup the Google Sign In button
        // Google Sign In
        SignInButton signInButton = findViewById(R.id.buttonSignIn);
        signInButton.setSize(SignInButton.SIZE_STANDARD);
        signInButton.setOnClickListener(v -> signIn());

        // [Format and set text below Google Sign In view.]
        String statement = getString(R.string.sign_in_statement);
        Spanned spanned = HtmlCompat.fromHtml(statement, HtmlCompat.FROM_HTML_MODE_COMPACT);
        ((TextView) findViewById(R.id.sign_in_statement)).setMovementMethod(LinkMovementMethod.getInstance());
        ((TextView) findViewById(R.id.sign_in_statement)).setText(spanned);

        findViewById(R.id.buttonWarningConfirmation).setOnClickListener(v -> {
            showApp();
        });


        findViewById(R.id.openMenu).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Initializing the popup menu and giving the reference as current context
                PopupMenu popupMenu = new PopupMenu(AppActivity.this, findViewById(R.id.popupAnchor));

                // Disable options if subscribed
                Menu menu = popupMenu.getMenu();
                billing.isActive(AppActivity.this, sku, new CallbackInterface.CallbackBoolean() {
                    @Override
                    public void call(Boolean value) {
                        if (value) {
                            for (int i = 0; i < menu.size(); i++) {
                                if (menu.getItem(i).getItemId() == R.id.disableAds) {
                                    menu.getItem(i).setEnabled(Boolean.FALSE);
                                } else if (menu.getItem(i).getItemId() == R.id.premium) {
                                    menu.getItem(i).setEnabled(Boolean.FALSE);
                                }
                            }
                        }
                    }
                });

                // Inflating popup menu from popup_menu.xml file
                popupMenu.getMenuInflater().inflate(R.menu.ui_menu, popupMenu.getMenu());
                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        // Toast message on menu item clicked
                        Toast.makeText(AppActivity.this, "You Clicked " + menuItem.getTitle(), Toast.LENGTH_SHORT).show();
                        // set item as selected to persist highlight
                        menuItem.setChecked(false);
                        // close drawer when item is tapped
//                        layoutMenu.closeDrawers();
                        if (menuItem.getItemId() == R.id.signOut) {
                            signOut();
                        }
                        if (menuItem.getItemId() == R.id.premium) {
                            Alert.upgradeNotice(AppActivity.this, new CallbackInterface.CallbackVoid() {
                                @Override
                                public void call() {
                                    billing.LaunchPurchaseFlow(AppActivity.this, sku, new CallbackInterface.CallbackVoid() {
                                        @Override
                                        public void call() {
                                            Log.d(this.toString(), "Purchase completed");
                                            Advertisement.disableBannerAd(AppActivity.this);
                                        }
                                    });
                                }
                            });
                        }
                        if (menuItem.getItemId() == R.id.resetSession) {
                            recreate();
                        }
                        if (menuItem.getItemId() == R.id.eraseData) {
//                        documentDataDeletionAlertDialog();
                            Alert.eraseMyData(AppActivity.this, new CallbackInterface.CallbackVoid() {
                                @Override
                                public void call() {
                                    Alert.deleteMyDataConfirmation(AppActivity.this, new CallbackInterface.CallbackVoid() {
                                        @Override
                                        public void call() {
                                            Alert.deleteMyDataAction(AppActivity.this, new CallbackInterface.CallbackVoid() {
                                                @Override
                                                public void call() {
                                                    deleteDocuments();
                                                }
                                            });
                                        }
                                    });
                                }
                            });
                        }
                        if (menuItem.getItemId() == R.id.disableAds) {
                            Alert.disableAds(AppActivity.this, new CallbackInterface.CallbackVoid() {
                                @Override
                                public void call() {
                                    if (mRewardedVideoAd.isLoaded()) {
                                        mRewardedVideoAd.show();
                                    }
                                }
                            });
                        }
//                        layoutMenu.closeDrawer(GravityCompat.START);
                        return true;
                    }
                });
                // Showing the popup menu
                hideDefaultControls(AppActivity.this);
                popupMenu.show();
            }
        });

        findViewById(R.id.addDocument).setOnClickListener(v -> onBrowse());
        findViewById(R.id.closeDocument).setOnClickListener(v -> {
            List<Node> nodeList = new ArrayList<>(arFragment.getArSceneView().getScene().getChildren());
            for (Node childNode : nodeList) {
                if (childNode instanceof AnchorNode) {
                    if (((AnchorNode) childNode).getAnchor() != null) {
                        if (Objects.equals(childNode.getName(), activeAnchorNodeName)) {
                            Objects.requireNonNull(((AnchorNode) childNode).getAnchor()).detach();
                            childNode.setParent(null);
                            findViewById(R.id.pagingControls).setVisibility(View.GONE);
                            findViewById(R.id.closeDocument).setVisibility(View.GONE);
                            findViewById(R.id.infoDisplay).setVisibility(View.INVISIBLE);
                            activeAnchorNodeName = null;
                        }
                    }
                }
            }
        });
        findViewById(R.id.pageLeft).setOnClickListener(v -> {
            List<Node> nodeList = new ArrayList<>(arFragment.getArSceneView().getScene().getChildren());
            for (Node childNode : nodeList) {
                if (childNode instanceof AnchorNode) {
                    if (((AnchorNode) childNode).getAnchor() != null) {
                        Log.d("NODE:", MessageFormat.format("{0} {1} {2}", childNode.getName(), activeAnchorNodeName, childNode.getName().equals(activeAnchorNodeName)));
                        if (Objects.equals(childNode.getName(), activeAnchorNodeName)) {
                            Objects.requireNonNull(documents.get(activeAnchorNodeName)).back();
                            Integer currentPage = Objects.requireNonNull(documents.get(activeAnchorNodeName)).getCurrentPage() + 1;
                            Integer pageCount = Objects.requireNonNull(documents.get(activeAnchorNodeName)).getPageCount();
                            String msg = String.format("%s / %s", currentPage, pageCount);
                            ((TextView) findViewById(R.id.pageNumber)).setText(msg);
                        }
                    }
                }
            }
        });
        findViewById(R.id.pageRight).setOnClickListener(v -> {
            List<Node> nodeList = new ArrayList<>(arFragment.getArSceneView().getScene().getChildren());
            for (Node childNode : nodeList) {
                if (childNode instanceof AnchorNode) {
                    if (((AnchorNode) childNode).getAnchor() != null) {
                        Log.d("NODE:", MessageFormat.format("{0} {1} {2}", childNode.getName(), activeAnchorNodeName, childNode.getName().equals(activeAnchorNodeName)));
                        if (Objects.equals(childNode.getName(), activeAnchorNodeName)) {
                            Objects.requireNonNull(documents.get(activeAnchorNodeName)).forward();
                            Integer currentPage = Objects.requireNonNull(documents.get(activeAnchorNodeName)).getCurrentPage() + 1;
                            Integer pageCount = Objects.requireNonNull(documents.get(activeAnchorNodeName)).getPageCount();
                            String msg = String.format("%s / %s", currentPage, pageCount);
                            ((TextView) findViewById(R.id.pageNumber)).setText(msg);
                        }
                    }
                }
            }
        });
        findViewById(R.id.pageNumber).setOnClickListener(v -> {
            LayoutInflater li = LayoutInflater.from(this);
            View promptsView = li.inflate(R.layout.page_prompt, null);

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);

            // set prompts.xml to alertdialog builder
            alertDialogBuilder.setView(promptsView);

            final EditText userInput = (EditText) promptsView
                    .findViewById(R.id.editTextDialogUserInput);

            // set dialog message
            alertDialogBuilder
                    .setCancelable(false)
                    .setPositiveButton("OK",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    // get user input and set it to result
                                    // edit text
                                    Integer pageNo = Integer.parseInt(userInput.getText().toString());

                                    List<Node> nodeList = new ArrayList<>(arFragment.getArSceneView().getScene().getChildren());
                                    for (Node childNode : nodeList) {
                                        if (childNode instanceof AnchorNode) {
                                            if (((AnchorNode) childNode).getAnchor() != null) {
                                                Log.d("NODE:", MessageFormat.format("{0} {1} {2}", childNode.getName(), activeAnchorNodeName, childNode.getName() == activeAnchorNodeName));
                                                if (Objects.equals(childNode.getName(), activeAnchorNodeName)) {
                                                    documents.get(activeAnchorNodeName).setPage(pageNo - 1);
                                                    Integer currentPage = documents.get(activeAnchorNodeName).getCurrentPage() + 1;
                                                    Integer pageCount = documents.get(activeAnchorNodeName).getPageCount();
                                                    String msg = String.format("%s / %s", currentPage, pageCount);
                                                    ((TextView) findViewById(R.id.pageNumber)).setText(msg);
                                                }
                                            }
                                        }
                                    }
                                }
                            })
                    .setNegativeButton("Cancel",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                }
                            });

            // create alert dialog
            AlertDialog alertDialog = alertDialogBuilder.create();

            // show it
            alertDialog.show();
        });


        // ARCore
        /* When you build a Renderable, Sceneform loads model and related resources
         * in the background while returning a CompletableFuture.
         * Call thenAccept(), handle(), or check isDone() before calling get().
         */
        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
        assert arFragment != null;
        arFragment.setOnTapArPlaneListener(
                (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
                    if (currentModelRenderable != null) {
                        Anchor anchor = hitResult.createAnchor();
                        addNodeToScene(currentModelRenderable, anchor);
                        TextView textViewInfoPane = findViewById(R.id.infoDisplay);
                        textViewInfoPane.setText("");
                        textViewInfoPane.setVisibility(View.INVISIBLE);
                        currentModelRenderable = null;
                    }
                    findViewById(R.id.infoDisplay).setVisibility(View.INVISIBLE);
                    ((TextView) findViewById(R.id.infoDisplay)).setText("");
                    findViewById(R.id.closeDocument).setVisibility(View.GONE);
                    findViewById(R.id.pagingControls).setVisibility(View.GONE);
                });

    }

    public static void hideDefaultControls(@NonNull final Activity activity) {
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        final Window window = activity.getWindow();

        if (window == null) {
            return;
        }

        final View decorView = window.getDecorView();

        if (decorView != null) {
            int uiOptions = decorView.getSystemUiVisibility();

            if (Build.VERSION.SDK_INT >= 14) {
                uiOptions |= View.SYSTEM_UI_FLAG_LOW_PROFILE;
            }

            if (Build.VERSION.SDK_INT >= 16) {
                uiOptions |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            }

            if (Build.VERSION.SDK_INT >= 19) {
                uiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            }

            decorView.setSystemUiVisibility(uiOptions);
        }
    }

    @Override
    public void onStart() {
        fadeOut(findViewById(R.id.splashScreen), 2500, 300);
        super.onStart();
    }

    @Override
    public void onResume() {
        mRewardedVideoAd.resume(this);
        super.onResume();
    }

    @Override
    public void onPause() {
        mRewardedVideoAd.pause(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mRewardedVideoAd.destroy(this);
        signOut();
        super.onDestroy();
    }

    @Override
    public void onRewarded(RewardItem reward) {
        // Reward the user.
        Advertisement.disableBannerAd(AppActivity.this, timer, 3600);
    }

    @Override
    public void onRewardedVideoAdLeftApplication() {

    }

    @Override
    public void onRewardedVideoAdClosed() {
        // Load the next rewarded video ad.
        advertisement.loadRewardedVideoAd(AppActivity.this, mRewardedVideoAd);
    }

    @Override
    public void onRewardedVideoAdFailedToLoad(int errorCode) {
//        Toast.makeText(this, "onRewardedVideoAdFailedToLoad", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRewardedVideoAdLoaded() {
//        Toast.makeText(this, "onRewardedVideoAdLoaded", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRewardedVideoAdOpened() {
//        Toast.makeText(this, "onRewardedVideoAdOpened", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRewardedVideoStarted() {
//        Toast.makeText(this, "onRewardedVideoStarted", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRewardedVideoCompleted() {
//        Toast.makeText(this, "onRewardedVideoCompleted", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onPrepareOptionsMenu (Menu menu) {
        return true;
    }

    private void uploadDocument(Uri uri, String filename) {
        Toast.makeText(this, "Please wait. Processing Document.", Toast.LENGTH_LONG).show();
        // Get permissions to read the file system.
        requestRead();
        try {
            // Attempt to write the file to an output stream.
            InputStream inputStream = getContentResolver().openInputStream(uri);
            Integer index = Objects.requireNonNull(getContentResolver().getType(uri)).lastIndexOf("/") + 1;
            String extension = Objects.requireNonNull(getContentResolver().getType(uri)).substring(index);
//            File tempFile = new File(getCacheDir(), "file." + extension);
            File tempFile = new File(getCacheDir(), getRealPathFromURI(uri));
            OutputStream outputStream = new FileOutputStream(tempFile);
            Log.d("processDocument", "full path: " + getCacheDir() + "file." + extension);
            Log.d("processDocument", "filename: " + getRealPathFromURI(uri));
            try {
                byte[] buffer = new byte[10 * 1024]; // or other buffer size
                int read;
                assert inputStream != null;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                outputStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            // Perform a call to the API and use the call back
            // to process the result.
            // NOTE: NEED DETAILED DESCRIPTION
            FirebaseUser mUser = FirebaseAuth.getInstance().getCurrentUser();
            mUser.getIdToken(true)
                    .addOnCompleteListener(new OnCompleteListener<GetTokenResult>() {
                        public void onComplete(@NonNull Task<GetTokenResult> task) {
                            if (task.isSuccessful()) {
                                String idToken = task.getResult().getToken();
                                Log.d("processDocument", idToken);
                                uploadDocumentRequest(tempFile, idToken, new okhttp3.Callback() {
                                    @Override
                                    public void onFailure(okhttp3.Call call, IOException e) {
                                        Log.d("processDocument", "onFailure: IOException: " + e.toString());
                                    }

                                    @Override
                                    public void onResponse(okhttp3.Call call, Response response) throws IOException {
                                        if (response.isSuccessful()) {
                                            String docID = response.body().string();
                                            Log.d("processDocument", "onResponse: documentID: " + documentID);

                                            DatabaseReference pagesRef = firebaseDatabase.getReference(String.format("%s/user/%s/%s/page_count", AppActivity.this.getResources().getString(R.string.APP_MODE), mUser.getUid(), docID));
                                            Log.d("processDocument", "onResponse: pagesRef: " + pagesRef.toString());
                                            ValueEventListener pagesListener = new ValueEventListener() {
                                                @Override
                                                public void onDataChange(DataSnapshot dataSnapshot) {
                                                    Integer pageCount = dataSnapshot.getValue(Integer.class);
                                                    AppActivity.this.runOnUiThread(() -> {
                                                        // Stuff that updates the UI.
                                                        Toast.makeText(AppActivity.this, "Document Ready!", Toast.LENGTH_LONG).show();
                                                        // textViewInfoPane.setText(R.string.ready_prompt);
                                                        // Create button for document.
                                                        LinearLayout layoutDocuments = findViewById(R.id.documentList);
                                                        Button buttonDocument = new Button(AppActivity.this);
                                                        buttonDocument.setWidth(layoutDocuments.getWidth());
                                                        buttonDocument.setBackgroundResource(R.drawable.ic_doc_light);
                                                        // Set params for button.
                                                        final float scale = AppActivity.this.getResources().getDisplayMetrics().density;
                                                        int pixels = (int) (56 * scale + 0.5f);
                                                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                                                LinearLayout.LayoutParams.MATCH_PARENT,
                                                                LinearLayout.LayoutParams.MATCH_PARENT
                                                        );

                                                        params.setMargins(0, 32, 0, 0);
                                                        buttonDocument.setLayoutParams(params);
                                                        CompletableFuture<ModelRenderable> model = createModelRenderable(apiURL, firebaseAuth.getUid(), docID);
                                                        // Add event listener.
                                                        buttonDocument.setOnClickListener(v -> {
                                                            documentRenderAlertDialog(layoutDocuments, buttonDocument, model, filename, docID, pageCount);
                                                        });
                                                        // Add button to documentsLayout.
                                                        layoutDocuments.addView(buttonDocument);
                                                    });
                                                }

                                                @Override
                                                public void onCancelled(DatabaseError databaseError) {
                                                    // Getting Post failed, log a message
                                                    Log.w("onCancelled", "loadPost:onCancelled", databaseError.toException());
                                                    // ...
                                                }
                                            };
                                            pagesRef.addListenerForSingleValueEvent(pagesListener);
                                        } else {
                                            // Request not successful
                                            Log.d("processDocument", "FAIL: " + Objects.requireNonNull(response.body()).string());
                                        }
                                    }
                                });
                            } else {
                                // Handle error -> task.getException();
                            }
                        }
                    });
        } catch (FileNotFoundException e) {
            Log.d("GOODBYE ERROR", e.toString());
            e.printStackTrace();
        }
    }

    public void requestRead() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_READ_EXTERNAL_STORAGE);
        }
    }

    public String getRealPathFromURI(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    public void documentRenderAlertDialog(LinearLayout linearLayout, Button button, CompletableFuture<ModelRenderable> model, String filename, String documentID, Integer pageCount) {
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle("Render Document");
        String message = String.format("Are you sure you want to render the document %s?", filename);
        alertDialog.setMessage(message);
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "YES",
                (dialog, which) -> {
                    this.documentID = documentID;
                    this.pageCount = pageCount;
                    getModelRenderable(model, filename);
                    dialog.dismiss();
                });
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "NO",
                (dialog, which) -> dialog.dismiss());
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "DELETE",
                (dialog, which) -> {
                    linearLayout.removeView(button);
                    Document.delete(firebaseDatabase, getResources().getString(R.string.APP_MODE), firebaseAuth.getUid(), documentID);
                    dialog.dismiss();
                });
        alertDialog.show();
    }

    private CompletableFuture<ModelRenderable> createModelRenderable(String apiURL, String userID, String docID) {
        String assetURL = String.format("%s/%s/%s/pages/0.gltf", apiURL, userID, docID);
        return ModelRenderable
                .builder()
                .setSource(this, RenderableSource.builder().setSource(
                        this,
                        Uri.parse(assetURL),
                        RenderableSource.SourceType.GLTF2)
                        .setScale(0.20f)
                        .setRecenterMode(RenderableSource.RecenterMode.ROOT)
                        .build())
                .setRegistryId(assetURL)
                .build();
    }

    public void getModelRenderable(CompletableFuture<ModelRenderable> model, String filename) {
        ((TextView) findViewById(R.id.infoDisplay)).setText(String.format("Preparing %s.", filename));
        findViewById(R.id.infoDisplay).setVisibility(View.VISIBLE);
        findViewById(R.id.closeDocument).setVisibility(View.GONE);
        findViewById(R.id.pagingControls).setVisibility(View.GONE);
        model.thenAccept(renderable -> {
            ((TextView) findViewById(R.id.infoDisplay)).setText(String.format("Tap to render %s.", filename));
            currentModelRenderable = renderable;
            documentName = filename;
            Log.d("MODEL RENDERABLE", currentModelRenderable.toString());
        }).exceptionally(
                throwable -> {
                    Log.d("ERROR", "MODEL RENDERABLE");
                    ((TextView) findViewById(R.id.infoDisplay)).setText(String.format("Unable to load %s.", filename));
                    documentName = "";
                    return null;
                });
    }

    private void addNodeToScene(ModelRenderable modelRenderable, Anchor anchor) {
        AnchorNode anchorNode = new AnchorNode(anchor);
        anchorNode.setName(UUID.randomUUID().toString());
        String anchorNodeName = anchorNode.getName();
        anchorNode.setParent(arFragment.getArSceneView().getScene());

        TransformableNode node = new TransformableNode(arFragment.getTransformationSystem());
        node.setParent(anchorNode);
        node.setRenderable(modelRenderable);
        node.getScaleController().setMaxScale(1.00f);
        node.getScaleController().setMinScale(0.10f);
        node.setOnTapListener((HitTestResult hitTestResult, MotionEvent me) -> {
            activeAnchorNodeName = anchorNodeName;
            Document document = documents.get(activeAnchorNodeName);
            assert document != null;
            Integer currentPage = document.getCurrentPage() + 1;
            Integer pageCount = document.getPageCount();
            Log.d("ROTATION", Float.toString(node.getLocalRotation().x));
            Log.d("ELEVATION", Float.toString(node.getWorldPosition().z));
            document.rotate.setCurrent((int) node.getLocalRotation().x);
            document.elevate.setCurrent((int) node.getWorldPosition().z);

            ImageButton buttonClose = findViewById(R.id.closeDocument);
            ImageButton buttonPageLeft = findViewById(R.id.pageLeft);
            ImageButton buttonPageRight = findViewById(R.id.pageRight);
            ((TextView) findViewById(R.id.infoDisplay)).setText(document.getFileName());
            findViewById(R.id.infoDisplay).setVisibility(View.VISIBLE);
            buttonClose.setVisibility(View.VISIBLE);
            if (pageCount > 1) {
                String msg = String.format("%s / %s", currentPage, pageCount);
                TextView textViewDocumentPage = findViewById(R.id.pageNumber);
                textViewDocumentPage.setText(msg);
                textViewDocumentPage.setVisibility(View.VISIBLE);
                findViewById(R.id.pagingControls).setVisibility(View.VISIBLE);
            }
        });

        // Create a document that references the transformable node.
        // This will allow the user to render multi-page documents.
        Log.d("DOCUMENT ID", documentID);
        Document document = new Document();
        document.setContext(this);
        document.setApiURL(apiURL);
        document.setUserID(firebaseAuth.getUid());
        document.setDocumentID(documentID);
        document.setTransformableNode(node);
        document.setPageCount(pageCount);
        document.setFileName(documentName);
        documents.put(anchorNodeName, document);
        documentName = "";
    }

    private void uploadDocumentRequest(File file, String id_token, okhttp3.Callback callback) {
        Log.d("RE", file.getName());
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.connectTimeout(24, TimeUnit.HOURS) // connect timeout
                .writeTimeout(24, TimeUnit.HOURS) // write timeout
                .readTimeout(24, TimeUnit.HOURS); // read timeout
        OkHttpClient client = builder.build();
        Log.d("AUTH TOKEN", id_token);

        FirebaseInstanceId.getInstance().getInstanceId()
                .addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
                    @Override
                    public void onComplete(@NonNull Task<InstanceIdResult> task) {
                        if (!task.isSuccessful()) {
                            Log.w("getInstanceId failed", task.getException());
                            return;
                        }
                        // Get new Instance ID token
                        String registration_token = task.getResult().getToken();
                        Log.d("FORM_ID_TOKEN", id_token);
                        Log.d("FORM_REG_TOKEN", registration_token);
                        RequestBody requestBody = new MultipartBody.Builder()
                                .setType(MultipartBody.FORM)
                                .addFormDataPart("file", file.getName(), RequestBody.create(MediaType.parse("*/*"), file))
                                .addFormDataPart("id_token", Objects.requireNonNull(id_token))
                                .addFormDataPart("registration_token", Objects.requireNonNull(registration_token))
                                .build();
                        Request request = new Request.Builder()
                                .url(apiURL)
                                .post(requestBody)
                                .build();
                        try {
                            okhttp3.Call call = client.newCall(request);
                            call.enqueue(callback);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
    }


    public void onBrowse() {
        DocumentDao.NumberOfDocuments(AppActivity.this, firebaseDatabase.getReference(), firebaseAuth.getUid(), new CallbackInterface.CallbackLong() {
            @Override
            public void call(long value) {
                Log.d(this.toString(), "LONG: " + String.valueOf(value));
                if (value < getResources().getInteger(R.integer.MAXIMUM_DOCUMENTS)) {
                    Intent FilePicker;
                    Intent intent;
                    FilePicker = new Intent(Intent.ACTION_GET_CONTENT);
                    FilePicker.addCategory(Intent.CATEGORY_OPENABLE);
                    FilePicker.setType("*/*");
                    String[] mimeTypes = {"image/jpeg", "image/jpg", "image/png", "application/pdf"};
                    FilePicker.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

                    intent = Intent.createChooser(FilePicker, "Pick a File");
                    startActivityForResult(intent, REQUEST_FILE_PICKER);
                } else {
                    Alert.documentLimitReached(AppActivity.this);
                }
            }
        });
    }

    private void signIn() {
        Log.d("signIn", "Signing into selected Google account.");
        Intent signInIntent = googleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, REQUEST_SIGN_IN);
    }

    private void signOut() {
        if (GoogleSignIn.getLastSignedInAccount(this) != null) {
            googleSignInClient.signOut();
        }
        FirebaseAuth.getInstance().signOut();
        updateUI(null);
        clearSessionData();
    }

    private void fadeOut(View layout, int delay, int duration) {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    if (layout.getVisibility() != View.GONE) {
                        Animation fadeOut = new AlphaAnimation(1, 0);
                        fadeOut.setInterpolator(new AccelerateInterpolator()); //and this
                        fadeOut.setStartOffset(0);
                        fadeOut.setDuration(duration);
                        AnimationSet animation = new AnimationSet(false); //change to false
                        animation.addAnimation(fadeOut);
                        layout.startAnimation(animation);
                        layout.setVisibility(View.GONE);
                    }
                });
            }
        }, delay);
    }

    private void fadeIn(View layout, int delay, int duration) {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    if (layout.getVisibility() != View.VISIBLE) {
                        Animation fadeOut = new AlphaAnimation(0, 1);
                        fadeOut.setInterpolator(new AccelerateInterpolator()); //and this
                        fadeOut.setStartOffset(0);
                        fadeOut.setDuration(duration);
                        AnimationSet animation = new AnimationSet(false); //change to false
                        animation.addAnimation(fadeOut);
                        layout.setVisibility(View.VISIBLE);
                        layout.startAnimation(animation);
                    }
                });
            }
        }, delay);
    }


    private void firebaseAuthenticateGoogle(GoogleSignInAccount googleSignInAccount) {
        AuthCredential credential = GoogleAuthProvider.getCredential(googleSignInAccount.getIdToken(), null);
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
                        updateUI(firebaseUser);
                    } else {
                        updateUI(null);
                    }
                });
    }

    private void showLogin() {
        fadeIn(findViewById(R.id.loginScreen), 0, 250);
        fadeOut(findViewById(R.id.adView), 0, 250);
        fadeOut(findViewById(R.id.appScreen), 0, 250);
    }

    private void showApp() {
        fadeIn(findViewById(R.id.appScreen), 0, 250);
        fadeIn(findViewById(R.id.adView), 0, 250);
        fadeOut(findViewById(R.id.warningScreen), 0, 250);
    }

    private void showWarningScreen() {
        fadeIn(findViewById(R.id.warningScreen), 0, 100);
        fadeOut(findViewById(R.id.loginScreen), 0, 100);
    }


    private void updateUI(FirebaseUser firebaseUser) {
        if (firebaseUser == null) {
            showLogin();
        } else {
            firebaseUser.getIdToken(true)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // Load user documents
                            Log.d("DOCLIST:", Integer.toString(((LinearLayout) findViewById(R.id.documentList)).getChildCount()));
                            if (((LinearLayout) findViewById(R.id.documentList)).getChildCount() <= 0) {
                                loadDocuments(firebaseAuth.getUid());
                            }
                            // Update UI if subscription is active
                            billing.isActive(AppActivity.this, sku, new CallbackInterface.CallbackBoolean() {
                                @Override
                                public void call(Boolean value) {
                                    if (value) {
                                        advertisement.disableBannerAd(AppActivity.this);
                                    }
                                }
                            });



                            showWarningScreen();
                        } else {
                            showLogin();
                        }
                    });
        }
    }

    public void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(getString(R.string.default_web_client_id))
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
        googleSignInAccount = GoogleSignIn.getLastSignedInAccount(this);
    }

    public void setupGoogleAdMob() {
        AdRequest adRequest = new AdRequest.Builder().build();
        AdView adView = findViewById(R.id.adView);
        adView.loadAd(adRequest);

        // Load Reward Ads
        MobileAds.initialize(this, getResources().getString(R.string.ADMOB_APP_ID));

        // Use an activity context to get the rewarded video instance.
        mRewardedVideoAd = MobileAds.getRewardedVideoAdInstance(this);
        mRewardedVideoAd.setRewardedVideoAdListener(this);

        advertisement.loadRewardedVideoAd(AppActivity.this, mRewardedVideoAd);
    }

    private void loadRewardedVideoAd() {
        mRewardedVideoAd.loadAd(getResources().getString(R.string.ADMOB_ID_VIDEO),
                new AdRequest.Builder().build());
    }

    public void setupFirebaseResources() {
        firebaseAuth = FirebaseAuth.getInstance();
        firebaseDatabase = FirebaseDatabase.getInstance();
    }

    public void authenticate() {
        if (googleSignInAccount != null) {
            firebaseAuthenticateGoogle(googleSignInAccount);
        } else {
            signOut();
        }
    }

    public void authenticate(GoogleSignInAccount googleSignInAccount) {
        if (googleSignInAccount != null) {
            firebaseAuthenticateGoogle(googleSignInAccount);
        } else {
            signOut();
        }
    }

    public void clearSessionData() {
        currentModelRenderable = null;
        activeAnchorNodeName = "";

        documents.clear();
        documentID = "";
        pageCount = 0;

        LinearLayout layoutDocuments = findViewById(R.id.documentList);
        layoutDocuments.removeAllViews();
    }

    public void loadDocuments(String userID) {
        LinearLayout layoutDocuments = findViewById(R.id.documentList);
        layoutDocuments.removeAllViewsInLayout();
        DatabaseReference databaseReference = firebaseDatabase.getReference();
        DatabaseReference userDocuments = databaseReference.child(String.format("%s/user/%s", getResources().getString(R.string.APP_MODE), userID));

        ValueEventListener userDocumentsListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Log.d(String.valueOf(this), "loadDocuments:onDataChange:PATH: " + dataSnapshot.getRef().toString());
                Log.d(String.valueOf(this), "loadDocuments:onDataChange:COUNT: " + dataSnapshot.getChildrenCount());
                dataSnapshot.getChildren().forEach(doc -> {
                    String docID = doc.getKey();
                    String filename = doc.child("name").getValue().toString();
                    Integer pageCount = Integer.parseInt(doc.child("page_count").getValue().toString());

                    AppActivity.this.runOnUiThread(() -> {
                        // Create renderable model.
                        CompletableFuture<ModelRenderable> model = createModelRenderable(apiURL, firebaseAuth.getUid(), docID);

                        // Create button for document.
                        Button buttonDocument = new Button(AppActivity.this);
                        buttonDocument.setWidth(layoutDocuments.getWidth());
                        buttonDocument.setBackgroundResource(R.drawable.ic_doc_light);

                        // Set params for button.
                        final float scale = AppActivity.this.getResources().getDisplayMetrics().density;
                        int pixels = (int) (56 * scale + 0.5f);
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.MATCH_PARENT
                        );
                        params.setMargins(0, 32, 0, 0);
                        buttonDocument.setLayoutParams(params);

                        buttonDocument.setOnClickListener(v -> {
                            documentRenderAlertDialog(layoutDocuments, buttonDocument, model, filename, docID, pageCount);
                        });

                        layoutDocuments.addView(buttonDocument);
                    });
                });
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Getting Post failed, log a message
                Log.w("onCancelled", "loadPost:onCancelled", databaseError.toException());
                // ...
            }
        };
        userDocuments.addListenerForSingleValueEvent(userDocumentsListener);
    }

    public void deleteDocuments() {
        try {
            LinearLayout layoutDocuments = findViewById(R.id.documentList);
            layoutDocuments.removeAllViewsInLayout();
            FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
            String firebaseUserUid = firebaseUser.getUid();
            DatabaseReference userDocuments = firebaseDatabase.getReference(String.format("%s/user/%s", getResources().getString(R.string.APP_MODE), firebaseUserUid));
            userDocuments.removeValue((databaseError, databaseReference) -> {
            });
            DatabaseReference documentData = firebaseDatabase.getReference(String.format("%s/document/%s", getResources().getString(R.string.APP_MODE), firebaseUserUid));
            documentData.removeValue((databaseError, databaseReference) -> {
            });
        } catch (Exception e) {
            Log.e("deleteDocuments:Exception", e.toString());
        }
    }
}
