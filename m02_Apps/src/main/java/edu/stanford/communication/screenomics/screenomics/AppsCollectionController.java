package edu.stanford.communication.screenomics.screenomics;

import android.content.Context;
import android.util.Log;

import edu.stanford.communication.screenomics.FirebaseSettings.SettingsManager;
import edu.stanford.communication.screenomics.modulemanager.ModuleController;

/**
 * Owns apps-related data collection: foreground app and screen on/off.
 * The app calls startCollecting/stopCollecting; this module uses AppsUpdater.
 */
public final class AppsCollectionController {

    private static final String TAG = "AppsCollectionCtrl";
    private static AppsUpdater sUpdater;

    public static void startCollecting(Context context) {
        if (!ModuleController.ENABLE_APPS && !ModuleController.ENABLE_POWER) {
            return;
        }
        if (sUpdater != null) {
            sUpdater.stopForegroundChecking();
            try {
                sUpdater.unregisterScreenOnOffReceiver(context);
            } catch (Exception ignored) { }
            sUpdater = null;
        }
        long interval = SettingsManager.val("foreground-app-check-interval");
        sUpdater = new AppsUpdater(context, interval);
        if (ModuleController.ENABLE_APPS) {
            sUpdater.startForegroundChecking();
        }
        if (ModuleController.ENABLE_POWER) {
            sUpdater.registerScreenOnOffReceiver(context);
        }
    }

    public static void stopCollecting(Context context) {
        if (sUpdater == null) {
            return;
        }
        sUpdater.stopForegroundChecking();
        try {
            sUpdater.unregisterScreenOnOffReceiver(context);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering screen on/off receiver: " + e.getMessage());
        }
        sUpdater = null;
        Log.d(TAG, "Apps collection stopped");
    }
}
