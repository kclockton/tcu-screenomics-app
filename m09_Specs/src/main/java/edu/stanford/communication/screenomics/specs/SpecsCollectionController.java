package edu.stanford.communication.screenomics.specs;

import android.content.Context;
import android.util.Log;

import java.util.HashMap;

import edu.stanford.communication.screenomics.FirebaseSettings.SettingsManager;
import edu.stanford.communication.screenomics.TextBasedData.EventOperationManager;
import edu.stanford.communication.screenomics.modulemanager.ModuleCharacteristics;
import edu.stanford.communication.screenomics.modulemanager.ModuleController;

/**
 * Owns specs (device specs) data collection within the specs module.
 * The app calls startCollecting/stopCollecting; this module starts/stops
 * the periodic specs logger based on module flag.
 * Call logSpecsOnceNow() once immediately after user registration/login when the module is enabled.
 */
public final class SpecsCollectionController {

    private static final String TAG = "SpecsCollectionCtrl";
    private static SpecsUpdater sUpdater;

    /**
     * Log device specs once immediately (e.g. right after user registration/login).
     * No-op if specs module is disabled. Uses the same event type and data as periodic logging.
     */
    public static void logSpecsOnceNow(Context context) {
        if (!ModuleController.ENABLE_SPECS) {
            return;
        }
        EventOperationManager.getInstance(context).addEvent(
                ModuleCharacteristics.getInstance().getAndroidSpecsEventCharacteristics(),
                new HashMap<>(SpecsInfo.createPhoneSpecMap()));
    }

    /**
     * Start device specs collection when the monitor service is running.
     * No-op if specs module is disabled.
     */
    public static void startCollecting(Context context) {
        if (!ModuleController.ENABLE_SPECS) {
            return;
        }
        if (sUpdater != null) {
            sUpdater.stopTimer();
            sUpdater = null;
        }
        long interval = SettingsManager.val("specs-check-interval");
        sUpdater = new SpecsUpdater(interval, context);
        sUpdater.startTimer();
        Log.d(TAG, "Specs collection started");
    }

    /**
     * Stop specs collection.
     */
    public static void stopCollecting(Context context) {
        if (sUpdater == null) {
            return;
        }
        sUpdater.stopTimer();
        sUpdater = null;
        Log.d(TAG, "Specs collection stopped");
    }
}
