package org.ebookdroid.ui.viewer;

import android.util.DisplayMetrics;

import org.ebookdroid.ui.viewer.stubs.ViewStub;
import org.emdev.ui.AbstractActionActivity;

public class VerticalViewActivity extends AbstractActionActivity<VerticalViewActivity, ViewerActivityController> {

    public IView view = ViewStub.STUB;
    public static DisplayMetrics DM;

    @Override
    protected ViewerActivityController createController() {
        return new ViewerActivityController(this);
    }
}
