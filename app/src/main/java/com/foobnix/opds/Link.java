package com.foobnix.opds;

import java.io.Serializable;

public class Link implements Serializable {
    public String href = "";
    public String title = "";
    public String type = "";
    public boolean isOpdsLink() { return false; }
}
