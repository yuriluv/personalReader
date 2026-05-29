package org.ebookdroid.ui.viewer;

import org.emdev.ui.AbstractActionActivity;

public class VerticalViewActivity extends AbstractActionActivity<VerticalViewActivity, ViewerActivityController> {
    @Override
    protected ViewerActivityController createController() {
        return new ViewerActivityController(this);
    }
}
