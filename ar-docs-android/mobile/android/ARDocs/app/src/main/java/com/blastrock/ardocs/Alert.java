package com.blastrock.ardocs;

import android.app.Activity;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

public class Alert {
    public static void disableAds(Activity activity, CallbackInterface.CallbackVoid call) {
        AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
        alertDialog.setTitle("Disable Ads");
        String message = String.format("Watch an advertisement to disable Ads for about 1 hour or until the end of your session. Which ever comes first.");
        alertDialog.setMessage(message);
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "YES",
                (dialog, which) -> {
                    dialog.dismiss();
                    call.call();

                });
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "NO",
                (dialog, which) -> dialog.dismiss());
        alertDialog.show();
    }

    public static void upgradeNotice(Activity activity, CallbackInterface.CallbackVoid call) {
        AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
        alertDialog.setTitle("Upgrade to Premium");
        String message = String.format("Upgrade to premium to disable ads for 1 month at $1.99.");
        alertDialog.setMessage(message);
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "YES",
                (dialog, which) -> {
                    dialog.dismiss();
                    call.call();
                });
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "NO",
                (dialog, which) -> dialog.dismiss());
        alertDialog.show();
    }

    public static void documentLimitReached(Activity activity) {
        AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
        alertDialog.setTitle("Storage Limit Reached");
        String message = String.format("Maximum of %s documents allowed.", activity.getResources().getInteger(R.integer.MAXIMUM_DOCUMENTS));
        alertDialog.setMessage(message);
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK",
                (dialog, which) -> {
                    dialog.dismiss();
                });
        alertDialog.show();
    }

    public static void eraseMyData(Activity activity, CallbackInterface.CallbackVoid call) {
        AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
        alertDialog.setTitle("Erase My Data");
        String message = String.format("Performing this action will erase all of your data on our services. This action cannot be undone. You will be prompted to confirm this action.");
        alertDialog.setMessage(message);
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "YES",
                (dialog, which) -> {
                    call.call();
                    dialog.dismiss();
                });
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "NO",
                (dialog, which) -> dialog.dismiss());
        alertDialog.show();
    }

    public static void deleteMyDataConfirmation(Activity activity, CallbackInterface.CallbackVoid call) {
        AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
        alertDialog.setTitle("Delete My Data: Confirmation");
        String message = String.format("Confirm this action to erase all of your data on our services. Once deleted the data will be gone forever. You will be prompted to perform this action.");
        alertDialog.setMessage(message);
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Yes",
                (dialog, which) -> {
                    dialog.dismiss();
                    call.call();
                });
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "CANCEL",
                (dialog, which) -> dialog.dismiss());
        alertDialog.show();
    }

    public static void deleteMyDataAction(Activity activity, CallbackInterface.CallbackVoid call) {
        AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
        alertDialog.setTitle("Delete My Data: Action");
        String message = String.format("Press DELETE to erase all of your data from our services.");
        alertDialog.setMessage(message);
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "DELETE",
                (dialog, which) -> {
                    dialog.dismiss();
                    call.call();
                });
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "CANCEL",
                (dialog, which) -> dialog.dismiss());
        alertDialog.show();
    }

    public static void uploadDocumentAlertDialog(Activity activity, String fileName, CallbackInterface.CallbackVoid call) {
        AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
        alertDialog.setTitle("Upload Document");
        String message = String.format("Upload the document %s?", fileName);
        alertDialog.setMessage(message);
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "YES",
                (dialog, which) -> {
                    dialog.dismiss();
                    call.call();
                });
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "NO",
                (dialog, which) -> dialog.dismiss());
        alertDialog.show();
    }
}
