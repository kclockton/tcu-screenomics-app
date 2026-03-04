package edu.stanford.communication.screenomics.screenshots;

import android.content.Context;

/**
 * Called by ScreenshotAlarmReceiver when screenshot-related alarms fire (0, 5, 8, 10–13).
 * App sets the handler so it can show notifications, log to Firestore, and start activities.
 */
public interface ScreenshotAlarmHandler {
    void handle(int requestCode, Context context);
}
