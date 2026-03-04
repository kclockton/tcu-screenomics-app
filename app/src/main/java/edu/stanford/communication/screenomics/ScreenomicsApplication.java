package edu.stanford.communication.screenomics;

import android.app.Activity;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import edu.stanford.communication.screenomics.Activity.AppRunningActivity;
import edu.stanford.communication.screenomics.screenshots.CaptureUploadStarter;
import edu.stanford.communication.screenomics.Alarm.NotificationHelper;
import edu.stanford.communication.screenomics.Alarm.SetAlarm;
import edu.stanford.communication.screenomics.FirebaseSettings.FirebaseManagerSingleton;
import edu.stanford.communication.screenomics.PermissionScreens.PermissionParentActivity;
import edu.stanford.communication.screenomics.Services.ScreenMonitorService;
import edu.stanford.communication.screenomics.screenshots.ScreenshotAlarmHandlerImpl;
import edu.stanford.communication.screenomics.screenshots.ScreenshotAlarmReceiver;
import edu.stanford.communication.screenomics.screenshots.ScreenshotModuleHost;
import edu.stanford.communication.screenomics.screenshots.CaptureUploadService;

/**
 * Tracks whether the app (excluding CaptureUploadStarter) is in the foreground.
 * Implements ScreenshotModuleHost so the screenshot module (m01) can launch activities and schedule work.
 */
public class ScreenomicsApplication extends Application implements ScreenshotModuleHost {

    private static int sResumedActivityCount = 0;

    /** True when any app activity other than CaptureUploadStarter is in the foreground. */
    public static boolean getAppInForeground() {
        return sResumedActivityCount > 0;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        FirebaseManagerSingleton.init(this);

        ScreenshotAlarmReceiver.setHandler(new ScreenshotAlarmHandlerImpl(this));

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}

            @Override
            public void onActivityStarted(@NonNull Activity activity) {}

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                if (!(activity instanceof CaptureUploadStarter)) {
                    sResumedActivityCount++;
                }
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                if (!(activity instanceof CaptureUploadStarter)) {
                    sResumedActivityCount--;
                    if (sResumedActivityCount < 0) sResumedActivityCount = 0;
                }
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {}

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {}
        });
    }

    // --- ScreenshotModuleHost ---

    @Override
    public int getWhichActivityStartedService() {
        return new edu.stanford.communication.screenomics.DatabaseHelper.InterCommunicationPreference(this).GET_WhichActivityStartedService();
    }

    @Override
    public int getMediaProjectionResultCode(int whichActivity) {
        return whichActivity == 0 ? CaptureUploadStarter.getResultCode() : AppRunningActivity.getResultCode();
    }

    @Override
    public Intent getMediaProjectionIntent(int whichActivity) {
        return whichActivity == 0 ? CaptureUploadStarter.getIntents() : AppRunningActivity.getIntents();
    }

    @Override
    public MediaProjection getMediaProjection(int whichActivity) {
        return whichActivity == 0 ? CaptureUploadStarter.getMediaProjection() : AppRunningActivity.getMediaProjection();
    }

    @Override
    public void launchAppRunningActivity() {
        Intent intent = new Intent(this, AppRunningActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void launchCaptureStarter(boolean justRestarted) {
        Intent intent = new Intent(this, CaptureUploadStarter.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
    }

    @Override
    public void launchCaptureStarterFromAlarm(Context context) {
        Intent intent = new Intent(context, CaptureUploadStarter.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        context.startActivity(intent);
    }

    @Override
    public boolean isCaptureServiceRunning(Context context) {
        android.app.ActivityManager manager = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return false;
        for (android.app.ActivityManager.RunningServiceInfo info : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (CaptureUploadService.class.getName().equals(info.service.getClassName())) return true;
        }
        return false;
    }

    @Override
    public void stopCaptureService(Context context) {
        context.stopService(new Intent(context, CaptureUploadService.class));
    }

    @Override
    public void sendResumeCaptureNotification(Context context, String message, String status) {
        NotificationHelper manage = new NotificationHelper(context);
        manage.Create_Channel();
        manage.SendStartServiceNotification(message, status);
    }

    @Override
    public void launchPermissionActivity() {
        Intent intent = new Intent(this, PermissionParentActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public boolean isPermissionFlowActive() {
        return PermissionParentActivity.isPermissionFlowActive();
    }

    @Override
    public boolean isAppInForeground() {
        return getAppInForeground();
    }

    @Override
    public boolean isScreenMonitorServiceRunning() {
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return false;
        for (android.app.ActivityManager.RunningServiceInfo info : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (ScreenMonitorService.class.getName().equals(info.service.getClassName())) return true;
        }
        return false;
    }

    @Override
    public void startScreenMonitorService() {
        ContextCompat.startForegroundService(this, new Intent(this, ScreenMonitorService.class));
    }

    @Override
    public boolean ensureLogin() {
        return !android.text.TextUtils.isEmpty(new edu.stanford.communication.screenomics.DatabaseHelper.LogInPreference(this).GetUserSubjId());
    }

    @Override
    public void userLogOut(boolean stopServices) {
        edu.stanford.communication.screenomics.Activity.LoginActivity.userLogOut(this, stopServices);
    }

    @Override
    public PendingIntent getNotificationContentIntent() {
        return PendingIntent.getActivity(this, 0, new Intent(this, AppRunningActivity.class), PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public String getNotificationTitle() {
        return getString(R.string.noti_title);
    }

    @Override
    public String getNotificationText() {
        return getString(R.string.noti_text);
    }

    @Override
    public int getNotificationIconResId() {
        return R.drawable.logo_stanford;
    }

    @Override
    public void schedule4HourAlarm(Context context) {
        new SetAlarm().SetAlarmFor4HoursToAskUserAgainToOpenTheAppAndStartTheService(context);
    }

    @Override
    public void retrieveServerTime() {
        edu.stanford.communication.screenomics.FirebaseSettings.ServerTimeSync.retrieveServerTime();
    }
}
