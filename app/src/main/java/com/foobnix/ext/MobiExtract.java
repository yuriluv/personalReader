package com.foobnix.ext;

import com.foobnix.android.utils.LOG;

import java.io.IOException;

public class MobiExtract {

    public static FooterNote extract(String inputPath, final String outputDir, String hashCode) throws IOException {
        // MOBI support removed
        return new FooterNote("", null);
    }

    public static EbookMeta getBookMetaInformation(String path, boolean onlyTitle) throws IOException {
        // MOBI support removed
        return EbookMeta.Empty();
    }

    public static String getBookOverview(String path) {
        // MOBI support removed
        return "";
    }

    public static byte[] getBookCover(String path) {
        // MOBI support removed
        return null;
    }

}
