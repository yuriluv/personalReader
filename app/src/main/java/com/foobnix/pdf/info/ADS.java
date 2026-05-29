package com.foobnix.pdf.info;

import android.app.Activity;
import android.content.Context;

public class ADS {
    private final static ADS instance = new ADS();

    public static synchronized ADS get() {
        return instance;
    }

    private ADS() {}

    public static long secondsRemain(long time) {
        return 0;
    }

    public static void hideAdsTemp(Activity a) {}

    public void showInterstitial(Activity a) {}

    public boolean isRewardActivated() {
        return true;
    }

    public void showRewardedAd(Activity a, Object listener) {}

    public boolean isRewardsLoaded() {
        return false;
    }

    public void loadRewardedAd(Activity a, Runnable onRewardLoaded) {}

    public void loadInterstitial(Activity a) {}

    public void showBanner(Activity a) {}

    public void onPauseBanner() {}

    public void onResumeBanner(Activity a) {}

    public void onDestroyBanner() {}

    public static String getByTestID(Context c) {
        return "";
    }
}
