package com.foobnix.pdf.info.wrapper;

import com.foobnix.android.utils.LOG;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.AppsConfig;
import com.foobnix.ui2.fragment.BookmarksFragment2;
import com.foobnix.ui2.fragment.BrowseFragment2;
import com.foobnix.ui2.fragment.FavoritesFragment2;
import com.foobnix.ui2.fragment.PrefFragment2;
import com.foobnix.ui2.fragment.RecentFragment2;
import com.foobnix.ui2.fragment.SearchFragment2;
import com.foobnix.ui2.fragment.UIFragment;

import java.util.ArrayList;
import java.util.List;

public enum UITab {

    SearchFragment(0, SearchFragment2.PAIR.first, SearchFragment2.PAIR.second, SearchFragment2.class, true), //
    BrowseFragment(1, BrowseFragment2.PAIR.first, BrowseFragment2.PAIR.second, BrowseFragment2.class, true), //
    RecentFragment(2, RecentFragment2.PAIR.first, RecentFragment2.PAIR.second, RecentFragment2.class, true), //
    StarsFragment(3, FavoritesFragment2.PAIR.first, FavoritesFragment2.PAIR.second, FavoritesFragment2.class, true), //
    BookmarksFragment(4, BookmarksFragment2.PAIR.first, BookmarksFragment2.PAIR.second, BookmarksFragment2.class, true), //
    PrefFragment(6, PrefFragment2.PAIR.first, PrefFragment2.PAIR.second, PrefFragment2.class, true); //

    public int index;
    private int name;
    private int icon;
    private Class<? extends UIFragment> clazz;
    private boolean isVisible;

    private UITab(int index, int name, int icon, Class<? extends UIFragment> clazz, boolean isVisible) {
        this.index = index;
        this.name = name;
        this.icon = icon;
        this.clazz = clazz;
        this.isVisible = isVisible;
    }

    public int getIndex() {
        return index;
    }

    public int getName() {
        return name;
    }

    public int getIcon() {
        return icon;
    }

    public Class<? extends UIFragment> getClazz() {
        return clazz;
    }

    public static UITab getByIndex(int index) {
        for (UITab tab : values()) {
            if (tab.index == index) {
                return tab;
            }
        }
        return SearchFragment;
    }

    public static List<UITab> getOrdered() {
        synchronized (AppState.get().tabsOrder9) {
            String input = AppState.get().tabsOrder9;
            LOG.d("getOrdered", input);
            List<UITab> list = new ArrayList<UITab>();
            List<Integer> seenIndices = new ArrayList<>();
            for (String pair : input.split(",")) {
                String[] tab = pair.split("#");
                int id = Integer.valueOf(tab[0]);
                boolean isVisible = tab[1].equals("1");
                // Skip unknown tab indices inherited from original Librera settings
                // (e.g., removed OPDS / Google Drive tabs)
                boolean knownTab = false;
                for (UITab t : values()) {
                    if (t.index == id) { knownTab = true; break; }
                }
                if (!knownTab) {
                    LOG.d("getOrdered", "Skipping unknown tab index: " + id);
                    continue;
                }
                // Skip duplicate tab indices
                if (seenIndices.contains(id)) {
                    LOG.d("getOrdered", "Skipping duplicate tab index: " + id);
                    continue;
                }
                seenIndices.add(id);
                UITab byIndex = getByIndex(id);
                byIndex.setVisible(isVisible);
                list.add(byIndex);
            }
            // Ensure all known tabs are present even if missing from stored order
            for (UITab t : values()) {
                if (!seenIndices.contains(t.index)) {
                    t.setVisible(t == PrefFragment ? false : true);
                    list.add(t);
                    seenIndices.add(t.index);
                }
            }
            return list;
        }
    }

    public static int getCurrentTabIndex(UITab tab) {
        List<UITab> ordered = getOrdered();
        int count = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).isVisible) {
                count++;
            }
            if (ordered.get(i) == tab) {
                return count;
            }
        }
        return 0;

    }

    public static boolean isShowRecent() {
        synchronized (AppState.get().tabsOrder9) {
            return AppState.get().tabsOrder9.contains(UITab.RecentFragment.index + "#1");
        }
    }

    public static boolean isShowLibrary() {
        synchronized (AppState.get().tabsOrder9) {
            return AppState.get().tabsOrder9.contains(UITab.SearchFragment.index + "#1");
        }
    }

    public static boolean isShowPreferences() {
        synchronized (AppState.get().tabsOrder9) {
            return AppState.get().tabsOrder9.contains(UITab.PrefFragment.index + "#1");
        }
    }

    public static boolean isShowCloudsPreferences() {
        return false;
    }

    public boolean isVisible() {
        return isVisible;
    }

    public void setVisible(boolean isVisible) {
        this.isVisible = isVisible;
    }

}
