package edu.stanford.communication.screenomics.screenshots;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Handles screenshot-related alarms: 0 (2 min), 5 (30 min ask again), 8 (legacy), 10-13 (user-pause follow-up).
 * Delegates to the handler set by the app so m01 does not depend on app UI/notification code.
 */
public class ScreenshotAlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "ScreenshotAlarmReceiver";
    private static volatile ScreenshotAlarmHandler sHandler;

    public static void setHandler(ScreenshotAlarmHandler handler) {
        sHandler = handler;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        int requestCode = intent.getIntExtra("request_code", -1);
        if (requestCode < 0) return;
        if (requestCode != 0 && requestCode != 5 && requestCode != 8 && requestCode != 10
                && requestCode != 11 && requestCode != 12 && requestCode != 13) {
            return;
        }
        ScreenshotAlarmHandler handler = sHandler;
        if (handler != null) {
            handler.handle(requestCode, context);
        } else {
            Log.w(TAG, "No handler set for request code " + requestCode);
        }
    }
}
