package com.foobnix.ui2;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

public class AdsFragmentActivity extends AppCompatActivity {
    private boolean rewardLoaded = false;
    private boolean rewardActivated = true;
    private Handler handler;
    private boolean doubleBackToExitPressedOnce = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        handler = new Handler(Looper.getMainLooper());
        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                onBackPressedAction();
            }
        });
    }

    public void showInterstitial() {
        onFinishActivity();
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

    public void showRewardVideo(Runnable callback) {
        if (callback != null) {
            callback.run();
        }
    }

    public void onBackPressedAction() {
        if (doubleBackToExitPressedOnce) {
            if (handler != null) {
                handler.removeCallbacksAndMessages(null);
            }
            onBackPressedFinishImpl();
            return;
        }
        doubleBackToExitPressedOnce = true;

        if (this instanceof MainTabs2 && handler != null) {
            handler.postDelayed(() -> {
                doubleBackToExitPressedOnce = false;
                onBackPressedImpl();
            }, 500);
        } else {
            onBackPressedImpl();
        }
    }

    public void onBackPressedImpl() {
        finish();
    }

    public void onBackPressedFinishImpl() {
        onFinishActivity();
    }

    public void onFinishActivity() {
        finish();
    }
}
