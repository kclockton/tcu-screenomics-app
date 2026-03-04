package edu.stanford.communication.screenomics.network;

import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.util.Log;

import edu.stanford.communication.screenomics.modulemanager.ModuleController;

/**
 * Owns network (connectivity) data collection within the network module.
 * The app calls startCollecting/stopCollecting; this module registers/unregisters
 * the connectivity receiver.
 */
public final class NetworkCollectionController {

    private static final String TAG = "NetworkCollectionCtrl";
    private static NetworkUpdater sUpdater;

    /**
     * Start network connectivity event collection when the monitor service is running.
     * No-op if network module is disabled.
     */
    public static void startCollecting(Context context) {
        if (!ModuleController.ENABLE_NETWORK) {
            return;
        }
        if (sUpdater != null) {
            try {
                context.unregisterReceiver(sUpdater);
            } catch (Exception ignored) { }
            sUpdater = null;
        }
        sUpdater = new NetworkUpdater();
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        context.registerReceiver(sUpdater, filter);
        Log.d(TAG, "Network collection started");
    }

    /**
     * Stop network collection.
     */
    public static void stopCollecting(Context context) {
        if (sUpdater == null) {
            return;
        }
        try {
            context.unregisterReceiver(sUpdater);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering network receiver: " + e.getMessage());
        }
        sUpdater = null;
        Log.d(TAG, "Network collection stopped");
    }
}
