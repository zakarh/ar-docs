package com.blastrock.ardocs;

import android.app.Activity;
import android.view.View;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.reward.RewardedVideoAd;

import java.util.Timer;
import java.util.TimerTask;

public class Advertisement {
    public void enableAd() {

    }

    public void disableAd() {

    }

    public static void disableBannerAd(Activity activity) {
        ConstraintLayout layout = activity.findViewById(R.id.adContainer);
        layout.setVisibility(View.GONE);
    }

    public static void disableBannerAd(Activity activity, Timer timer, int seconds) {
        ConstraintLayout layout = activity.findViewById(R.id.adContainer);
        layout.setVisibility(View.GONE);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                layout.setVisibility(View.VISIBLE);
            }
        }, seconds * 1000);
    }

    public void loadRewardedVideoAd(Activity activity, RewardedVideoAd rewardedVideoAd) {
        String s = activity.getResources().getString(R.string.ADMOB_ID_VIDEO);
        rewardedVideoAd.loadAd(s, new AdRequest.Builder().build());
    }
}
