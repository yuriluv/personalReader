package com.foobnix.work;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;

public class SynctornizatoinWorker extends MessageWorker {
    public SynctornizatoinWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @Override
    boolean doWorkInner() {
        return true;
    }
}
