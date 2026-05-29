package com.foobnix.mobi.parser;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

public class IOUtils {
    public static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    public static void copyClose(InputStream in, OutputStream out) {
        try {
            if (in != null && out != null) {
                copy(in, out);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception e) {}
            try {
                if (out != null) out.close();
            } catch (Exception e) {}
        }
    }
}
