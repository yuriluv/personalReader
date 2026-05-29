package com.foobnix.tts;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

public class TTSControlsView extends FrameLayout {
    public TTSControlsView(Context context) {
        super(context);
    }

    public TTSControlsView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TTSControlsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setDC(Object dc) {}
    public void addOnDialogRunnable(Runnable runnable) {}
}
