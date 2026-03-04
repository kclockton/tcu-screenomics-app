package edu.stanford.communication.screenomics.activites;

import android.content.Context;
import android.util.Log;

import edu.stanford.communication.screenomics.FirebaseSettings.SettingsManager;
import edu.stanford.communication.screenomics.modulemanager.ModuleController;

/**
 * Owns physical activity (step count) data collection within the activities module.
 * The app calls startCollecting/stopCollecting; this module starts/stops ActivitiesUpdater.
 */
public final class ActivitiesCollectionController {

    private static final String TAG = "ActivitiesCollectionCtrl";
    private static ActivitiesUpdater sUpdater;

    public static void startCollecting(Context context) {
        if (SettingsManager.val("pa-enabled") != 1 || !ModuleController.ENABLE_ACTIVITIES) {
            return;
        }
        if (sUpdater != null) {
            sUpdater.stop();
            sUpdater = null;
        }
        long interval = SettingsManager.val("pa-stepcounts-interval");
        sUpdater = new ActivitiesUpdater(context, interval);
        sUpdater.start();
        Log.d(TAG, "Activities (step) collection started");
    }

    public static void stopCollecting(Context context) {
        if (sUpdater == null) {
            return;
        }
        sUpdater.stop();
        sUpdater = null;
        Log.d(TAG, "Activities collection stopped");
    }
}
