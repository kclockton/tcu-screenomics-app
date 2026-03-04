package edu.stanford.communication.screenomics.NonTextBasedData;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import edu.stanford.communication.screenomics.FirebaseSettings.SettingsManager;

/**
 * Standard scheduler for file upload cycles to Cloud Storage (screenshots, audio, etc.).
 * Runs at the interval defined by the config, checks {@link CloudStorageUploadPolicy#shouldRunUpload}
 * before each run, then invokes the module's one-shot upload runnable.
 * <p>
 * Usage: the module creates a runnable that performs one upload cycle (no self-reschedule).
 * The module creates {@code new CloudStorageUploadScheduler(context, handler, config, moduleUploadRunnable)}
 * and posts it once (e.g. {@code handler.post(scheduler)}). Each run, the scheduler reschedules
 * itself and, if policy allows, runs the module's runnable.
 */
public class CloudStorageUploadScheduler implements Runnable {

    private static final String TAG = "CloudStorageUploadScheduler";

    private final Context context;
    private final Handler handler;
    private final CloudStorageUploadConfig config;
    private final Runnable oneShotUploadTask;

    public CloudStorageUploadScheduler(Context context, Handler handler, CloudStorageUploadConfig config, Runnable oneShotUploadTask) {
        this.context = context.getApplicationContext();
        this.handler = handler;
        this.config = config;
        this.oneShotUploadTask = oneShotUploadTask;
    }

    @Override
    public void run() {
        long intervalMs = SettingsManager.exists()
                ? SettingsManager.val(config.getIntervalSettingKey())
                : 5 * 60 * 1000L; // fallback 5 min
        handler.postDelayed(this, intervalMs);

        String skipReason = CloudStorageUploadPolicy.getSkipReason(context, config);
        if (skipReason != null) {
            Log.d(TAG, "Upload cycle skipped: " + skipReason + ". No ScreenshotUploadEvent this cycle.");
            return;
        }

        oneShotUploadTask.run();
    }
}
