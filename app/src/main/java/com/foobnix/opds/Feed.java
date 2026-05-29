package com.foobnix.opds;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Feed implements Serializable {
    public String title = "";
    public String subtitle = "";
    public String homeUrl = "";
    public List<Entry> entries = new ArrayList<>();
    public List<Link> links = new ArrayList<>();
    public String parentTitle = "";
    public boolean isNeedLoginPassword = false;
    public String icon = null;
}
