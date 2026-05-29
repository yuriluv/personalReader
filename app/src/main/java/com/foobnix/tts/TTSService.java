package com.foobnix.tts;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

public class TTSService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void playPause(Context context, Object dc) {}
    public static void updateTimer() {}
    public static void openSettingsIntent(Context context) {}
    public static void playBookPage(int page, Object... args) {}
    public static boolean isTTSGranted(Context context) { return true; }
    public static boolean isServiceRunning(Class<?> clazz, Context context) { return false; }
}
