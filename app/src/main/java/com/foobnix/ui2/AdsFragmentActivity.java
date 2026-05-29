package com.foobnix.ui2;

import android.content.Context;
import androidx.appcompat.app.AppCompatActivity;

public class AdsFragmentActivity extends AppCompatActivity {
    private boolean rewardLoaded = false;
    private boolean rewardActivated = true;

    public void showInterstitial() {
    }

    public void showInterstitialNoFinish() {
    }

    public void onDestroyBanner() {
    }

    public void onRewardLoaded() {
    }

    public boolean isRewardActivated() {
        return rewardActivated;
    }

    public boolean isRewardLoaded() {
        return rewardLoaded;
    }

    public void showRewardVideo(Object callback) {
    }
}
