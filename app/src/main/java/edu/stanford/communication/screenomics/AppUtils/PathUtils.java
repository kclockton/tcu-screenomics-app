package edu.stanford.communication.screenomics.AppUtils;

import android.content.Context;

import android.os.Environment;

/**
 * Paths for crash logs and app data.
 */
public final class PathUtils {

    private PathUtils() {}

    public static String getCrashLogDirectory(Context context) {
        java.io.File ext = context.getExternalFilesDir(null);
        if (ext != null) return ext.getAbsolutePath() + "/logs/";
        return getBackupCrashLogDirectory();
    }

    public static String getBackupCrashLogDirectory() {
        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/screenomics_logs/";
    }
}
