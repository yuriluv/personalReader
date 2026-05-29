package com.foobnix.opds;

import android.content.Context;
import okhttp3.OkHttpClient;

public class OPDS {
    public static final String USER_AGENT = "LibreraReader";
    public static OkHttpClient client = new OkHttpClient();

    public static String getHttpResponse(String text, String arg1, String arg2) {
        return "";
    }

    public static String getHttpResponseNoException(String url) {
        return "";
    }

    public static Feed getFeed(String uri, Context context) {
        return null;
    }
}
