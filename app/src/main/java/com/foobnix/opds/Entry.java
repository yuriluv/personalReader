package com.foobnix.opds;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Entry implements Serializable {
    public String homeUrl = "";
    public String appState = "";
    public String title = "";
    public String link = "";
    public String year = "";
    public String content = "";
    public String summary = "";
    public String author = "";
    public String authorUrl = "";
    public String category = "";
    public String logo = "";
    public String getStatus() { return ""; }
    public List<Link> links = new ArrayList<>();

    public void setAppState(String... args) {
        if (args.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (String arg : args) {
                sb.append(arg).append(";");
            }
            this.appState = sb.toString();
        }
    }
}
