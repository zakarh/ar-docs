package com.blastrock.ardocs;

import android.app.Activity;
import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

public class DocumentDao {

    public static void NumberOfDocuments(Activity activity, DatabaseReference dbr, String uid, CallbackInterface.CallbackLong call) {
        String resource = activity.getResources().getString(R.string.APP_MODE);
        String path = String.format("%s/user/%s", resource, uid);
        Log.d("NumberOfDocuments:path", path);
        dbr.child(path).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                call.call(dataSnapshot.getChildrenCount());
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.d("NumberOfDocuments:onCancelled", databaseError.toString());
            }
        });
    }
}
