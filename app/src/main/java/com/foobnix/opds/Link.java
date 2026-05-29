package com.foobnix.opds;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

public class Link implements Serializable {
    public static final String TYPE_LOGO = "logo";
    public String href = "";
    public String title = "";
    public String type = "";
    public String author = "";
    public String rel = "";
    public static final String APPLICATION_ATOM_XML = "application/atom+xml";

    public Link() {}

    public Link(String href) {
        this.href = href;
    }

    public boolean isOpdsLink() { return false; }
    public boolean isThumbnail() { return false; }
    public boolean isSearchLink() { return false; }
    public boolean isImageLink() { return false; }
    public String getDownloadName() { return ""; }
}
