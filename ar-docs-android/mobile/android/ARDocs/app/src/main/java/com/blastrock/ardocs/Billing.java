package com.blastrock.ardocs;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchaseHistoryRecord;
import com.android.billingclient.api.PurchaseHistoryResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Billing {
    private BillingClient billingClient;
    private List<String> skuList = new ArrayList<>();
    private Activity activity;

    public Billing(Activity activity) {
        this.activity = activity;
        this.billingClient = BillingClient.newBuilder(activity)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases()
                .build();
        this.addSkus();
        this.billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    Log.d(this.toString(), "Billing setup complete!");
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
                Log.d(this.toString(), "Billing is disconnected!");
            }
        });
    }


    public void LaunchPurchaseFlow(Activity activity, String sku, CallbackInterface.CallbackVoid call) {
        SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
        Log.d(this.toString() + ":LaunchPurchaseFlow", this.skuList.toString() + "");
        params.setSkusList(this.skuList).setType(BillingClient.SkuType.SUBS);
        billingClient.querySkuDetailsAsync(params.build(),
                new SkuDetailsResponseListener() {
                    @Override
                    public void onSkuDetailsResponse(BillingResult billingResult,
                                                     List<SkuDetails> skuDetailsList) {
                        // Process the result.
                        // Retrieve a value for "skuDetails" by calling querySkuDetailsAsync().
                        SkuDetails skuDetails = null;
                        Log.d(this.toString() + ":LaunchPurchaseFlow:onSkuDetailsResponse", skuDetailsList.size() + "");

                        for (SkuDetails s : skuDetailsList) {
                            Log.d(this.toString() + ":LaunchPurchaseFlow:onSkuDetailsResponse", s.getSku() + ", " + s.getTitle() + ", " + sku);
                            if (s.getSku().equals(sku)) {
                                skuDetails = s;
                                break;
                            }
                        }
                        if (skuDetails != null) {
                            BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                                    .setSkuDetails(skuDetails)
                                    .build();
                            int responseCode = billingClient.launchBillingFlow(activity, billingFlowParams).getResponseCode();

                            // Handle the result.
                            Log.d(this.toString(), "PURCHASE_FLOW_RESULT: " + responseCode);
                            if (responseCode == BillingClient.BillingResponseCode.OK) {
                                try {
                                    call.call();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                });
    }

    public void isActive(Activity activity, String sku, CallbackInterface.CallbackBoolean callback) {
        Purchase.PurchasesResult purchasesResult = billingClient.queryPurchases(BillingClient.SkuType.SUBS);
        purchasesResult.getPurchasesList();
        Boolean purchaseFound = Boolean.FALSE;
        Log.d(activity.toString() + ":isActive:queryPurchases:getResponseCode", purchasesResult.getResponseCode()  + "");
        if (purchasesResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                && purchasesResult != null) {
            Log.d(activity.toString() + ":isActive:queryPurchases:getPurchasesList:size", String.valueOf(purchasesResult.getPurchasesList().size()));
            for (Purchase purchase : purchasesResult.getPurchasesList()) {
                Log.d(activity.toString() + ":isActive:queryPurchases:purchases", purchase.getOriginalJson());
                if (purchase.getSku().equals(sku)) {
                    String verify_url = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/%s/purchases/subscriptions/%s/tokens/%s";
                    String v = String.format(verify_url, activity.getPackageName(), purchase.getSku(), purchase.getPurchaseToken());
                    // Perform GET request to verify purchase
                    Log.d(this.toString(), "queryPurchases:VERIFICATION_URL : " + v);
                    purchaseFound = Boolean.TRUE;
                }
            }
        }

        if (!purchaseFound){
            callback.call(Boolean.FALSE);
            return;
        }

        billingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.SUBS,
                new PurchaseHistoryResponseListener() {
                    @Override
                    public void onPurchaseHistoryResponse(@NonNull BillingResult billingResult, @Nullable List<PurchaseHistoryRecord> purchases) {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                                && purchases != null) {
//                            AcknowledgePurchaseResponseListener acknowledgePurchaseResponseListener = new AcknowledgePurchaseResponseListener() {
//                                @Override
//                                public void onAcknowledgePurchaseResponse(@NonNull BillingResult billingResult) {
//                                    Log.d(activity.toString(), "Purchase Acknowledged");
//                                }
//                            };
                            Boolean purchaseFound = Boolean.FALSE;
                            for (PurchaseHistoryRecord purchase : purchases) {
                                Log.d(activity.toString() + ":isActive:onPurchaseHistoryResponse:purchases", purchase.getOriginalJson());
                                if (purchase.getSku().equals(sku)) {
                                    purchaseFound = Boolean.TRUE;
                                    String verify_url = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/%s/purchases/subscriptions/%s/tokens/%s";
                                    String v = String.format(verify_url, activity.getPackageName(), purchase.getSku(), purchase.getPurchaseToken());
                                    // Perform GET request to verify purchase
                                    Log.d(activity.toString() + ":queryPurchaseHistoryAsync:onPurchaseHistoryResponse:VERIFICATION_URL", v);
                                    subscriptionStatus(activity.getPackageName(), purchase.getSku(), purchase.getPurchaseToken(),  new okhttp3.Callback() {
                                        @Override
                                        public void onFailure(okhttp3.Call call, IOException e) {
                                            Log.d(activity.toString() +":queryPurchaseHistoryAsync:onPurchaseHistoryResponse:onFailure:", e.toString());
                                            callback.call(Boolean.FALSE);
                                        }

                                        @Override
                                        public void onResponse(Call call, Response response) throws IOException {
                                            Log.d(activity.toString() +":queryPurchaseHistoryAsync:onPurchaseHistoryResponse:onResponse:", response.toString());
                                            // notice string() call
                                            String resStr = response.body().string();
                                            try {
                                                JSONObject json = new JSONObject(resStr);
                                                Boolean value = Boolean.parseBoolean(json.get("status").toString());
                                                callback.call(value);
                                            } catch (JSONException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    });
                                }
                            }
                            if (!purchaseFound){
                                callback.call(Boolean.FALSE);
                            }
                        } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
                            // Handle an error caused by a user cancelling the purchase flow.
                            Log.d(this.toString(), "USER_CANCELED: " + billingResult.getResponseCode());
                            callback.call(Boolean.FALSE);
                        } else {
                            // Handle any other error codes.
                            Log.d(this.toString(), "PURCHASE_ERROR: " + billingResult.getResponseCode());
                            callback.call(Boolean.FALSE);
                        }
                    }
                });
    }

    private void subscriptionStatus(String packageName, String sku, String purchaseToken, okhttp3.Callback callback){
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.connectTimeout(5, TimeUnit.MINUTES) // connect timeout
                .writeTimeout(5, TimeUnit.MINUTES) // write timeout
                .readTimeout(5, TimeUnit.MINUTES); // read timeout
        OkHttpClient client = builder.build();

        String uri = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/%s/purchases/subscriptions/%s/tokens/%s";
        uri = String.format(uri, packageName, sku, purchaseToken);

        // Get new Instance ID token
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("packageName", packageName)
                .addFormDataPart("subscriptionId", sku)
                .addFormDataPart("tokens", purchaseToken)
                .build();
        Request request = new Request.Builder()
                .url(uri)
                .post(requestBody)
                .build();
        try {
            okhttp3.Call call = client.newCall(request);
            call.enqueue(callback);
        } catch (Exception e) {
            e.printStackTrace();
        }


    }


    private PurchasesUpdatedListener purchasesUpdatedListener = new PurchasesUpdatedListener() {
        @Override
        public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                    && purchases != null) {
                for (Purchase purchase : purchases) {
                    handlePurchase(purchase);
                }
            } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
                // Handle an error caused by a user cancelling the purchase flow.
                Log.d(this.toString(), "USER_CANCELED: " + billingResult.getResponseCode());
            } else {
                // Handle any other error codes.
                Log.d(this.toString(), "PURCHASE_ERROR: " + billingResult.getResponseCode());
            }
        }
    };


    private void handlePurchase(Purchase purchase) {
        // Purchase retrieved from BillingClient#queryPurchases or your PurchasesUpdatedListener.
        // Purchase purchase = ...;

        // Verify the purchase.
        // Ensure entitlement was not already granted for this purchaseToken.
        // Grant entitlement to the user.
        AcknowledgePurchaseResponseListener acknowledgePurchaseResponseListener = new AcknowledgePurchaseResponseListener() {
            @Override
            public void onAcknowledgePurchaseResponse(@NonNull BillingResult billingResult) {
                Log.d(Billing.this.activity.toString(), "Purchase Acknowledged");
            }
        };
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged()) {
                AcknowledgePurchaseParams acknowledgePurchaseParams =
                        AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.getPurchaseToken())
                                .build();
                billingClient.acknowledgePurchase(acknowledgePurchaseParams, acknowledgePurchaseResponseListener);
            }
        }
    }

    private void addSkus() {
        this.skuList.add("ardocs.subscription.premium.monthly");
        this.skuList.add("android.tests.purchased");
    }
}
