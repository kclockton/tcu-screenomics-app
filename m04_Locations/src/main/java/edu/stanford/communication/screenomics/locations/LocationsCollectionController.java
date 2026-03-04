package edu.stanford.communication.screenomics.locations;

import android.content.Context;
import android.util.Log;

import edu.stanford.communication.screenomics.FirebaseSettings.SettingsManager;
import edu.stanford.communication.screenomics.modulemanager.ModuleController;

/**
 * Owns location (GPS) data collection within the locations module.
 * The app calls startCollecting/stopCollecting; this module starts/stops
 * LocationsUpdater based on settings and module flag.
 */
public final class LocationsCollectionController {

    private static final String TAG = "LocationsCollectionCtrl";
    private static LocationsUpdater sUpdater;

    public static void startCollecting(Context context) {
        if (SettingsManager.val("gps-enabled") != 1 || !ModuleController.ENABLE_LOCATIONS) {
            Log.d("Module Manager", "Gps Module Is off");
            return;
        }
        if (sUpdater != null) {
            sUpdater.stopLocationUpdates();
            sUpdater = null;
        }
        long interval = SettingsManager.val("gps-location-interval");
        sUpdater = new LocationsUpdater(context, interval);
        if (sUpdater.checkLocationPermission()) {
            sUpdater.startLocationUpdates();
            Log.d(TAG, "Location collection started");
        }
    }

    public static void stopCollecting(Context context) {
        if (sUpdater == null) {
            return;
        }
        sUpdater.stopLocationUpdates();
        sUpdater = null;
        Log.d(TAG, "Location collection stopped");
    }
}
