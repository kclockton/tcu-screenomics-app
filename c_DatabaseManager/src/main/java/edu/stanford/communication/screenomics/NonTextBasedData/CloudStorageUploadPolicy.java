package edu.stanford.communication.screenomics.NonTextBasedData;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.text.TextUtils;

import edu.stanford.communication.screenomics.DatabaseHelper.LogInPreference;
import edu.stanford.communication.screenomics.FirebaseSettings.SettingsManager;

/**
 * Central policy for "should we run a file upload cycle to Cloud Storage?"
 * Checks kill-switch, wifi-only (when configured), and login state.
 */
public final class CloudStorageUploadPolicy {

    private CloudStorageUploadPolicy() {}

    public static boolean shouldRunUpload(Context context, CloudStorageUploadConfig config) {
        return getSkipReason(context, config) == null;
    }

    /**
     * Returns the reason upload was skipped, or null if upload should run.
     * Use this so the scheduler can log the exact reason in one place.
     */
    public static String getSkipReason(Context context, CloudStorageUploadConfig config) {
        if (context == null || config == null) return "context or config is null";
        if (!SettingsManager.exists()) return "SettingsManager does not exist";

        if (SettingsManager.val(config.getKillSwitchSettingKey()) == 1) return "kill-switch is ON";
        if (TextUtils.isEmpty(new LogInPreference(context.getApplicationContext()).GetUserSubjId())) return "user not logged in (no subject ID)";

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return "ConnectivityManager is null";
        Network activeNetwork = cm.getActiveNetwork();
        if (activeNetwork == null) return "no active network";
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
        if (capabilities == null) return "no network capabilities";
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "no WiFi or cellular";
        if (SettingsManager.val(config.getWifiOnlySettingKey()) == 1 && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "wifi-only is ON but not on WiFi";

        return null;
    }
}
