package edu.stanford.communication.screenomics.screenshots;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

import edu.stanford.communication.screenomics.DatabaseHelper.InterCommunicationPreference;
import edu.stanford.communication.screenomics.DatabaseHelper.LogInPreference;
import edu.stanford.communication.screenomics.TextBasedData.EventOperationManager;
import edu.stanford.communication.screenomics.TextBasedData.HashMapPool;
import edu.stanford.communication.screenomics.modulemanager.EventTimestamp;
import edu.stanford.communication.screenomics.modulemanager.ModuleCharacteristics;

/**
 * Implementation of screenshot alarm handling (request codes 0, 5, 8, 10–13).
 * Uses ScreenshotModuleHost for app-specific behavior (launch activity, notifications, service checks).
 */
public class ScreenshotAlarmHandlerImpl implements ScreenshotAlarmHandler {

    private static final String TAG = "ScreenshotAlarmHandler";

    private final ScreenshotModuleHost host;

    public ScreenshotAlarmHandlerImpl(ScreenshotModuleHost host) {
        this.host = host;
    }

    @Override
    public void handle(int requestCode, Context context) {
        LogInPreference sharedPref = new LogInPreference(context);
        String status = sharedPref.GetUserSubjId();
        InterCommunicationPreference interPref = new InterCommunicationPreference(context);
        ScreenshotsCollectionController scheduler = new ScreenshotsCollectionController();

        switch (requestCode) {
            case 0:
                if (!host.isCaptureServiceRunning(context)) {
                    String pauseCause = interPref.getPauseCause();
                    boolean screenOn = false;
                    try {
                        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                        screenOn = pm != null && pm.isInteractive();
                    } catch (Exception ignored) {}
                    if ("system".equals(pauseCause) && screenOn && !host.isAppInForeground()) {
                        host.launchCaptureStarterFromAlarm(context);
                        Log.d(TAG, "Request code 0: system pause, screen on -> show popup");
                    } else if (host.isScreenMonitorServiceRunning()) {
                        host.sendResumeCaptureNotification(context, "Screen capture is paused. Please reopen the app to resume.", status);
                        logEvent(context);
                        Log.d(TAG, "Request code 0: send notification");
                    } else {
                        host.sendResumeCaptureNotification(context, "App is not running. To resume data collection, please reopen the app.", status);
                        logEvent(context);
                        Log.d(TAG, "Request code 0: app not running notification");
                    }
                } else {
                    scheduler.cancelAlarm(context, 0);
                }
                break;
            case 5:
                if (!host.isCaptureServiceRunning(context)) {
                    host.launchCaptureStarterFromAlarm(context);
                } else {
                    scheduler.cancelAlarm(context, 5);
                }
                break;
            case 8:
                if (!host.isCaptureServiceRunning(context)) {
                    host.sendResumeCaptureNotification(context, "Screen capture is paused. Please reopen the app to resume.", status);
                    Log.d(TAG, "Request code 8: legacy user-pause notification");
                }
                break;
            case 10:
            case 11:
            case 12:
            case 13:
                if (!host.isCaptureServiceRunning(context) && "user".equals(interPref.getPauseCause())) {
                    if (host.isScreenMonitorServiceRunning()) {
                        host.sendResumeCaptureNotification(context, "Screen capture is paused. Please reopen the app to resume.", status);
                    } else {
                        host.sendResumeCaptureNotification(context, "App is not running. To resume data collection, please reopen the app.", status);
                    }
                    logEvent(context);
                    Log.d(TAG, "Request code " + requestCode + ": user-pause follow-up notification");
                }
                break;
            default:
                break;
        }
    }

    private void logEvent(Context context) {
        ModuleCharacteristics moduleCharacteristics = ModuleCharacteristics.getInstance();
        EventTimestamp timestamp = new EventTimestamp();
        java.util.HashMap<String, String> map = HashMapPool.getMap();
        java.util.HashMap<String, String> map1 = HashMapPool.getMap();
        java.util.HashMap<String, String> map2 = HashMapPool.getMap();
        map.put("activity", "delivered");
        map2.put(moduleCharacteristics.getAlarmManagerCharacteristics().get("className"), timestamp.getTimestringFriendly());
        map1.put("MostRecentEventTime", timestamp.getTimestringFriendly());
        EventOperationManager.getInstance(context)
                .addEvent(moduleCharacteristics.getAlarmManagerCharacteristics(), map);
        HashMapPool.releaseMap(map);
        HashMapPool.releaseMap(map1);
        HashMapPool.releaseMap(map2);
    }
}
