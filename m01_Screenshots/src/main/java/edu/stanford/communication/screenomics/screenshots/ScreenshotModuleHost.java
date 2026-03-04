package edu.stanford.communication.screenomics.screenshots;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;

/**
 * Implemented by the app (e.g. Application) so the screenshot module can launch activities,
 * check app state, and schedule work without depending on app classes.
 */
public interface ScreenshotModuleHost {

    /** Which activity started the capture: 0 = CaptureUploadStarter, 1 = AppRunningActivity. */
    int getWhichActivityStartedService();

    /** Result code from the activity that granted media projection (for whichActivity 0 or 1). */
    int getMediaProjectionResultCode(int whichActivity);

    /** Intent from the activity that granted media projection (for whichActivity 0 or 1). */
    Intent getMediaProjectionIntent(int whichActivity);

    /** For pre-O: the MediaProjection instance from the activity (may be null). */
    MediaProjection getMediaProjection(int whichActivity);

    /** Launch the main app/permission activity (e.g. AppRunningActivity). */
    void launchAppRunningActivity();

    /** Launch the capture-starter activity to request media projection. justRestarted = true if retrying after null. */
    void launchCaptureStarter(boolean justRestarted);

    /** Launch the capture-starter activity from an alarm (e.g. request codes 0, 5). */
    void launchCaptureStarterFromAlarm(Context context);

    /** True if CaptureUploadService is currently running. */
    boolean isCaptureServiceRunning(Context context);

    /** Stop CaptureUploadService (e.g. on logout). */
    void stopCaptureService(Context context);

    /** Show "resume capture" / "reopen app" notification (used by screenshot alarm handler and others). */
    void sendResumeCaptureNotification(Context context, String message, String status);

    /** Launch the permission flow activity (e.g. PermissionParentActivity). */
    void launchPermissionActivity();

    boolean isPermissionFlowActive();
    boolean isAppInForeground();
    boolean isScreenMonitorServiceRunning();
    void startScreenMonitorService();

    /** @return true if user is logged in (subject id non-empty). */
    boolean ensureLogin();
    void userLogOut(boolean stopServices);

    /** For the foreground service notification: click opens main app. */
    PendingIntent getNotificationContentIntent();
    String getNotificationTitle();
    String getNotificationText();
    int getNotificationIconResId();

    /** Schedule 4-hour alarm to ask user to open app / restart monitor (request code 6). */
    void schedule4HourAlarm(Context context);

    /** Kick off server time retrieval for timestamps (optional). */
    void retrieveServerTime();
}
