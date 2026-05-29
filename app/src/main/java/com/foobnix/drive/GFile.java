package com.foobnix.drive;

import android.content.Context;
import java.io.File;

public class GFile {
    public static String debugOut = "";
    public static boolean isNeedUpdate = false;
    public static int REQUEST_CODE_SIGN_IN = 9001;
    public static int timeout = 0;

    public static void deleteRemoteFile(File file) {}
    public static void runSyncService(Context context) {}
    public static void runSyncService(Context context, boolean force) {}
    public static long getLastModified(File file) { return file != null ? file.lastModified() : 0; }
    public static void sycnronizeAll(Context context) {}
    public static void logout(Context context) {}
    public static String getDisplayInfo(Context context) { return ""; }
    public static void init(Context context) {}
}
