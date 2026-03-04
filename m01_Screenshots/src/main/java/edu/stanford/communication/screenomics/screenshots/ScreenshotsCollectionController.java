package edu.stanford.communication.screenomics.screenshots;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;
import java.util.HashMap;

import edu.stanford.communication.screenomics.TextBasedData.EventMapBuilder;
import edu.stanford.communication.screenomics.TextBasedData.EventUploaderToFireStore;
import edu.stanford.communication.screenomics.modulemanager.ModuleController;

/**
 * Schedules and cancels screenshot-related alarms (2 min, 30 min ask again, user-pause 7 AM/7 PM).
 * Uses ScreenshotAlarmReceiver so all screenshot alarm handling stays in m01 or app handler.
 */
public class ScreenshotsCollectionController {

    /** 2 min after pause: show popup or send notification. */
    public void schedule2MinAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ScreenshotAlarmReceiver.class);
        intent.putExtra("request_code", 0);
        PendingIntent pendingIntent = pendingIntent(context, 0, intent);
        if (pendingIntent != null && alarmManager != null) {
            long trigger = System.currentTimeMillis() + 2 * 60 * 1000;
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, trigger, pendingIntent);
        }
    }

    /** 30 min: try to start capture again (popup). */
    public void schedule30MinAskAgainAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ScreenshotAlarmReceiver.class);
        intent.putExtra("request_code", 5);
        PendingIntent pendingIntent = pendingIntent(context, 5, intent);
        if (pendingIntent != null && alarmManager != null) {
            long trigger = System.currentTimeMillis() + 30 * 60 * 1000;
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, trigger, pendingIntent);
        }
    }

    /** User-pause follow-up: next day 7 AM, next day 7 PM, day-after-next 7 AM, day-after-next 7 PM. */
    public void scheduleUserPauseFollowUpAlarms(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        long now = System.currentTimeMillis();
        int[] requestCodes = { 10, 11, 12, 13 };
        int[] hour = { 7, 19, 7, 19 };
        int[] dayOffset = { 1, 1, 2, 2 };
        for (int i = 0; i < 4; i++) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(now);
            cal.set(Calendar.HOUR_OF_DAY, hour[i]);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            cal.add(Calendar.DAY_OF_MONTH, dayOffset[i]);
            Intent intent = new Intent(context, ScreenshotAlarmReceiver.class);
            intent.putExtra("request_code", requestCodes[i]);
            PendingIntent pi = pendingIntent(context, requestCodes[i], intent);
            if (pi != null) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            }
        }
    }

    public void cancelAlarm(Context context, int requestCode) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        Intent intent = new Intent(context, ScreenshotAlarmReceiver.class);
        PendingIntent pi = pendingIntent(context, requestCode, intent);
        if (pi != null) alarmManager.cancel(pi);
    }

    public void cancelUserPauseFollowUpAlarms(Context context) {
        for (int code = 10; code <= 13; code++) {
            cancelAlarm(context, code);
        }
    }

    /**
     * Log ScreenshotPauseEvent (Paused/Resumed) to Firestore/ticker. Call from anywhere that
     * pauses or resumes screenshot capture (e.g. CaptureUploadService, app logout).
     * No-op if screenshots module is disabled.
     *
     * @param context application context
     * @param type    "Paused" or "Resumed"
     * @param cause   optional cause (e.g. "user", "system"); can be null
     */
    public static void logScreenshotPauseEvent(Context context, String type, String cause) {
        if (!ModuleController.ENABLE_SCREENSHOTS) {
            return;
        }
        EventUploaderToFireStore uploader = EventUploaderToFireStore.getInstance(context);
        if (uploader == null) {
            return;
        }
        HashMap<String, String> extra = new HashMap<>();
        if (cause != null && !cause.isEmpty()) {
            extra.put("cause", cause);
        }
        uploader.uploadSingleEvent("ScreenshotPauseEvent",
                EventMapBuilder.buildCompleteMap(extra.isEmpty() ? null : extra, type),
                context.getApplicationContext());
    }

    private static PendingIntent pendingIntent(Context context, int requestCode, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_MUTABLE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE);
        }
        return null;
    }
}
