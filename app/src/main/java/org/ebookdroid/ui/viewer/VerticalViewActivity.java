package org.ebookdroid.ui.viewer;

import android.util.DisplayMetrics;

import org.emdev.ui.AbstractActionActivity;

public class VerticalViewActivity extends AbstractActionActivity<VerticalViewActivity, ViewerActivityController> {

    public VerticalViewActivity view;
    public static DisplayMetrics DM;

    @Override
    protected ViewerActivityController createController() {
        return new ViewerActivityController(this);
    }
}
