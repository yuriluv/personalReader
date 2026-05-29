package com.foobnix.opds;

public class Hrefs {
    public static String fixHref(String href, String base) {
        if (href == null) return "";
        if (href.startsWith("http://") || href.startsWith("https://")) {
            return href;
        }
        if (base == null) return href;
        if (href.startsWith("/")) {
            int schemeEnd = base.indexOf("://");
            if (schemeEnd > 0) {
                int pathStart = base.indexOf("/", schemeEnd + 3);
                if (pathStart > 0) {
                    return base.substring(0, pathStart) + href;
                }
            }
            return base + href;
        }
        if (base.endsWith("/")) {
            return base + href;
        }
        return base + "/" + href;
    }
}
