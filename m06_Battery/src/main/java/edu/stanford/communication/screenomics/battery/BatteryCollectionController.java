package edu.stanford.communication.screenomics.battery;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import edu.stanford.communication.screenomics.modulemanager.ModuleController;

/**
 * Owns battery data collection within the battery module.
 * The app calls startCollecting/stopCollecting; this module registers/unregisters
 * the receiver and decides whether collection is enabled.
 */
public final class BatteryCollectionController {

    private static final String TAG = "BatteryCollectionCtrl";
    private static BatteryUpdater sUpdater;

    /**
     * Start battery state and charging event collection when the monitor service is running.
     * No-op if battery module is disabled.
     */
    public static void startCollecting(Context context) {
        if (!ModuleController.ENABLE_BATTERY) {
            return;
        }
        if (sUpdater != null) {
            try {
                context.unregisterReceiver(sUpdater);
            } catch (Exception ignored) { }
            sUpdater = null;
        }
        sUpdater = new BatteryUpdater();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        context.registerReceiver(sUpdater, filter);
        Log.d(TAG, "Battery collection started");
    }

    /**
     * Stop battery collection (e.g. when the monitor service stops).
     */
    public static void stopCollecting(Context context) {
        if (sUpdater == null) {
            return;
        }
        try {
            context.unregisterReceiver(sUpdater);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering battery receiver: " + e.getMessage());
        }
        sUpdater = null;
        Log.d(TAG, "Battery collection stopped");
    }
}
