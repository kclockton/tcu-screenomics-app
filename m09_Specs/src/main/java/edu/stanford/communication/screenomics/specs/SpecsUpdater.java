package edu.stanford.communication.screenomics.specs;

import android.content.Context;
import android.os.Handler;

import java.util.HashMap;

import edu.stanford.communication.screenomics.TextBasedData.EventOperationManager;
import edu.stanford.communication.screenomics.modulemanager.ModuleCharacteristics;

/**
 * Updates device specs on a fixed interval and sends them via EventOperationManager
 * (same pipeline as other events). Same pattern as LocationsUpdater. Used by SpecsCollectionController.
 */
public class SpecsUpdater {
    private final Handler handler = new Handler();
    private long intervalMs;
    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            sendSpecsToPipeline();
            handler.postDelayed(this, intervalMs);
        }
    };

    private final Context context;

    public SpecsUpdater(long intervalMs, Context context) {
        this.intervalMs = intervalMs;
        this.context = context;
    }

    public void startTimer() {
        handler.postDelayed(runnable, intervalMs);
    }

    public void stopTimer() {
        handler.removeCallbacks(runnable);
    }

    private void sendSpecsToPipeline() {
        EventOperationManager.getInstance(context).addEvent(
                ModuleCharacteristics.getInstance().getAndroidSpecsEventCharacteristics(),
                new HashMap<>(SpecsInfo.createPhoneSpecMap()));
    }
}
