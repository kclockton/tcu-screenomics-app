package edu.stanford.communication.screenomics.screenshots;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.HashMap;
import java.util.List;

import edu.stanford.communication.screenomics.DatabaseHelper.InterCommunicationPreference;
import edu.stanford.communication.screenomics.DatabaseHelper.LogInPreference;
import edu.stanford.communication.screenomics.FirebaseSettingsObserver;
import edu.stanford.communication.screenomics.MediaProjectionDied;
import edu.stanford.communication.screenomics.FirebaseSettings.SettingsManager;
import edu.stanford.communication.screenomics.TextBasedData.EventOperationManager;
import edu.stanford.communication.screenomics.TextBasedData.EventUploaderToFireStore;
import edu.stanford.communication.screenomics.TextBasedData.HashMapPool;
import edu.stanford.communication.screenomics.modulemanager.ModuleCharacteristics;
import edu.stanford.communication.screenomics.modulemanager.ModuleController;

public class CaptureUploadService extends Service {
    /** Intent extra: who triggered capture start ("user", "boot", "media-projection-null"). Set by app when starting CaptureUploadStarter or CaptureUploadService. */
    public static final String EXTRA_STARTUP_INSTIGATOR = "startup_instigator";

    private final String TAG = "CaptureUploadService";
    private static boolean mIsRunning = false;

    // Broadcasts
    public static final String ACTION_SCREENSHOT = "edu.stanford.communication.screenomics.ACTION_SCREENSHOT";

    // User login data
    private String subject_id;

    // Media projection
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;

    // Foreground service notification
    private Notification notification;
    private static final int NOTIFICATION_ID = 1472894;
    private static final String NOTIFICATION_CHANNEL_ID = "23742";
    private static final CharSequence NOTIFICATION_CHANNEL_NAME = "Screenomics_Channel";

    LogInPreference LogPref;

    // Screen orientation change detection
    private boolean detectOrientationChanges;
    private int lastScreenOrientation;

    // Screen on/off
    private BroadcastReceiver wakeReceiver;

    private InterCommunicationPreference prefrence;

    ScreenshotsUpdater screenshotsUpdater;

    private final ScreenshotsCollectionController screenshotsController = new ScreenshotsCollectionController();

    ModuleCharacteristics moduleCharacteristics = ModuleCharacteristics.getInstance();

    private ScreenshotModuleHost getHost() {
        return (ScreenshotModuleHost) getApplication();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

    }

    /**
     * Service startup.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (intent != null && intent.hasExtra(EXTRA_STARTUP_INSTIGATOR)) {
                ScreenshotsUpdater.startupInstigator = intent.getStringExtra(EXTRA_STARTUP_INSTIGATOR);
            }
            // Start notification first to avoid ANR
            startNotification(getHost().getNotificationTitle(), getHost().getNotificationText());

            // Initialize key components
            initializeComponents();

//            Intent intent2 = new Intent(this, ScreenOnActivity.class);
//            intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            startActivity(intent2);

            // Check if user is logged in
            if (!getHost().ensureLogin()) {
                getHost().userLogOut(true);
                return START_NOT_STICKY;
            }

            // Initialize the SettingsManager
            initializeSettingsManager();

            // Start wake detection
            startWakeDetection();

            // Initialize screen capture
            initializeScreenCapture();

            // Start ScreenMonitorService (text-based events: battery, location, apps, etc.)
            if (!getHost().isScreenMonitorServiceRunning()) {
                getHost().startScreenMonitorService();
            }

            // Mark service as running
            mIsRunning = true;

            return START_STICKY_COMPATIBILITY;
        } catch (Exception e) {
            Log.e(TAG, "Error starting service", e);
            stopSelf();
            return START_NOT_STICKY;
        }
    }

    /**
     * Initialize core components
     */
    private void initializeComponents() {
        prefrence = new InterCommunicationPreference(getApplicationContext());
        prefrence.clearPauseCause();

        // Log service resumed event (cause is always "user" — resume requires permission regrant).
        prefrence.getAndClearResumeCause();
        ScreenshotsCollectionController.logScreenshotPauseEvent(getApplicationContext(), "Resumed", "user");

        screenshotsController.cancelAlarm(getApplicationContext(), 0);
        screenshotsController.cancelAlarm(getApplicationContext(), 8);
        screenshotsController.cancelUserPauseFollowUpAlarms(getApplicationContext());
        LogPref = new LogInPreference(getApplicationContext());

        getHost().schedule4HourAlarm(getApplicationContext());

        // Initialize media projection manager
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

    }

    /**
     * Initialize Settings Manager
     */
    private void initializeSettingsManager() {
        if (!SettingsManager.exists()) {
            Log.i("SettingsManager", "start setting manager");
            SettingsManager.create(new FirebaseSettingsObserver() {
                @Override
                public void onSettingsChanged(List<String> changedSettings) {

                }
            }).load(this, new SettingsDatabaseListener());
        }

    }

    /**
     * Initialize screen capture functionality
     */
    private void initializeScreenCapture() {
        // Request screen capture permission only when screenshots module is enabled
        if (ModuleController.ENABLE_SCREENSHOTS) {
            AskForScreenCapture();
        }

        if (ModuleController.ENABLE_SCREENSHOTS) {
            postStartProjection();

            if (mediaProjectionManager == null || screenshotsUpdater.mediaProjection == null) {
                init_handleMediaProjectionNull();
                return;
            }
        }

        // Initialize screen orientation tracking
        detectOrientationChanges = true;
        lastScreenOrientation = getResources().getConfiguration().orientation;

        // Check current screen state
        checkScreenState();

        // Start screen capture
        if (screenshotsUpdater != null) {
            screenshotsUpdater.createVirtualDisplay();
        }

        // Start uploading text data
    }

    /**
     * Check the current screen state
     */
    private void checkScreenState() {
        DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        if (dm != null && screenshotsUpdater != null) {
            for (Display display : dm.getDisplays()) {
                if (display.getState() != Display.STATE_OFF) {
                    screenshotsUpdater.screenAwake = true;
                    Log.i(TAG, "Screen is on");
                    break;
                }
            }
        }

        getHost().retrieveServerTime();
    }

    /**
     * Request screen capture permission and initialize screen capture
     */
    private void AskForScreenCapture() {
        try {
            int which = getHost().getWhichActivityStartedService();
            if (which == 0 || which == 1) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mediaProjection = mediaProjectionManager.getMediaProjection(
                            getHost().getMediaProjectionResultCode(which),
                            getHost().getMediaProjectionIntent(which));

                    if (ModuleController.ENABLE_SCREENSHOTS) {
                        initializeScreenshotCapture();
                        screenshotsUpdater.mediaProjection = mediaProjection;
                        screenshotsUpdater.StartNewThreadToCapture();
                    }
                    registerMediaProjectionCallback();
                } else {
                    mediaProjection = getHost().getMediaProjection(which);
                    if (mediaProjection != null && ModuleController.ENABLE_SCREENSHOTS) {
                        initializeScreenshotCapture();
                        screenshotsUpdater.mediaProjection = mediaProjection;
                        screenshotsUpdater.StartNewThreadToCapture();
                    }
                    registerMediaProjectionCallback();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing media projection", e);
            launchPermissionActivity();
        }
    }

    /**
     * Initialize screenshot capture component
     */
    private void initializeScreenshotCapture() {
        screenshotsUpdater = new ScreenshotsUpdater(
                getApplicationContext(),
                SettingsManager.val("screenshot-interval"),
                SettingsManager.val("screenshot-check-interval"),
                SettingsManager.val("data-nontext-upload-wifi-only"),
                SettingsManager.val("data-nontext-upload-interval"),
                SettingsManager.val("kill-switch"),
                SettingsManager.val("screenshot-absolute-timing"),
                new MediaProjectionDied() {
                    @Override
                    public void MaybeMediaProjectionDied() {
                        mediaProjection.stop();
                        screenshotsUpdater.destroyVirtualDisplay();
                        stopSelf();
                    }
                }
        );
    }

    /**
     * Register media projection callback
     */
    private void registerMediaProjectionCallback() {
        if (screenshotsUpdater != null && screenshotsUpdater.mediaProjection != null) {
            screenshotsUpdater.mediaProjection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onCapturedContentResize(int width, int height) {
                    super.onCapturedContentResize(width, height);
                }
            }, screenshotsUpdater.handler);
        } else {
            launchPermissionActivity();
        }
    }

    /**
     * Launch permission activity when media projection is not available
     */
    private void launchPermissionActivity() {
        getHost().launchAppRunningActivity();
        stopSelf();
    }

    /**
     * Register callback for media projection
     */
    private void postStartProjection() {
        if (getHost().getWhichActivityStartedService() == 0 && screenshotsUpdater != null && screenshotsUpdater.mediaProjection != null) {
            screenshotsUpdater.mediaProjection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    try {
                        if (!getHost().isScreenMonitorServiceRunning()) {
                            getHost().startScreenMonitorService();
                        }

                        screenshotsUpdater.destroyVirtualDisplay();

                        if (ModuleController.ENABLE_SCREENSHOTS && prefrence != null) {
                            boolean screenOn = false;
                            try {
                                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                                screenOn = pm != null && pm.isInteractive();
                            } catch (Exception ignored) {}
                            prefrence.putPauseCause(screenOn ? "user" : "system");
                        }

                        Log.d("ScreenshotsUpdater", "mediaProjection stopped early");

//                        final MediaProjection.Callback that = this;

                        // If the service is still running, something bad has happened
//                        if (screenshotsUpdater.isRunning) {
//                            Log.d("ScreenshotCapture", "mediaProjection stopped early inside if");
//                            restartServiceWithPermission();
//                        }

                        if (screenshotsUpdater.mediaProjection != null) {
                            screenshotsUpdater.mediaProjection.unregisterCallback(this);
                        }

//                        DestroyTheObject();

                    } catch (Exception e) {
                        Log.e(TAG, "Error in onStop callback", e);
                    }
                    stopSelf();

                }

                @Override
                public void onCapturedContentResize(int width, int height) {
                    super.onCapturedContentResize(width, height);
                }

                @Override
                public void onCapturedContentVisibilityChanged(boolean isVisible) {
                    super.onCapturedContentVisibilityChanged(isVisible);
                }
            }, screenshotsUpdater.handler);
        }
    }

    /**
     * Restart service with permission
     */
    private void restartServiceWithPermission() {
        getHost().launchPermissionActivity();
        stopSelf();
    }

    /**
     * Handle media projection null error
     */
    private void init_handleMediaProjectionNull() {
        if (getHost().getWhichActivityStartedService() == 0) {
            if (getHost().isPermissionFlowActive()) {
                Log.d(TAG, "User is on permission screen; not showing media projection dialog until they leave.");
                stopSelf();
                return;
            }
            if (getHost().isAppInForeground()) {
                Log.d(TAG, "App is in foreground; not showing media projection dialog.");
                stopSelf();
                return;
            }
            if (screenshotsUpdater.mJustRestarted) {
                Log.e(TAG, "MediaProjection was null AGAIN! Prompting the user to restart now.");
                getHost().launchPermissionActivity();
            } else {
                Log.e(TAG, "MediaProjection was null on startup, Android probably killed us. Restarting now...");
                screenshotsUpdater.startupInstigator = "media-projection-null";
                screenshotsUpdater.mJustRestarted = true;
                getHost().launchCaptureStarter(true);
            }
            stopSelf();
        }
    }

    /**
     * Restart the screenshot capture process
     */
    private void restartScreenshotCapture() {
        stopScreenshotCapture();
    }

    /**
     * Stop the screenshot capture process
     */
    private void stopScreenshotCapture() {
        Log.d("ScreenshotMonitor", "Stopping screenshot capture...");

        try {
            if (screenshotsUpdater != null) {
                screenshotsUpdater.AcquireImageMutex();
                screenshotsUpdater.destroyVirtualDisplay();
                screenshotsUpdater.createVirtualDisplay();
                screenshotsUpdater.ReleaseImageMutex();
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Error stopping screenshot capture", e);
        }
    }

    @Override
    public void onDestroy() {
        DestroyTheObject();

        stopWakeDetection();

        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);

        super.onDestroy();
    }

    private void DestroyTheObject(){
        try {

            // Log service paused event (cause in event for Firestore/ticker)
            if (prefrence != null) {
                String cause = prefrence.getPauseCause();
                if (cause == null) {
                    cause = "system";
                    prefrence.putPauseCause("system");
                }
                ScreenshotsCollectionController.logScreenshotPauseEvent(getApplicationContext(), "Paused", cause);
            }

            // Clean up resources
            cleanupResources();

            // Mark service as not running
            mIsRunning = false;

            // Destroy virtual display
            if (screenshotsUpdater != null) {
                screenshotsUpdater.destroyVirtualDisplay();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
        }
    }

    private boolean isMyServiceRunning(Class<?> serviceClass, Context context) {

        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Clean up resources
     */
    private void cleanupResources() {
        try {
            if (prefrence != null) {
                String cause = prefrence.getPauseCause();
                if ("user".equals(cause)) {
                    screenshotsController.schedule2MinAlarm(getApplicationContext());
                    screenshotsController.schedule30MinAskAgainAlarm(getApplicationContext());
                    screenshotsController.scheduleUserPauseFollowUpAlarms(getApplicationContext());
                } else {
                    screenshotsController.schedule2MinAlarm(getApplicationContext());
                    screenshotsController.schedule30MinAskAgainAlarm(getApplicationContext());
                }
                screenshotsController.cancelAlarm(getApplicationContext(), 4);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up resources", e);
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);

        // Pause upload on low memory
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            screenshotsUpdater.uploader.pauseUpload();
            EventUploaderToFireStore.getInstance(this).SetIsMemoryLow(true);
        }
        // Resume on moderate memory
        else if (level <= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN && screenshotsUpdater.uploader.isUploadPaused()) {
            screenshotsUpdater.uploader.resumeUpload();
            EventUploaderToFireStore.getInstance(this).SetIsMemoryLow(false);

        }

        // Report an event that memory is low
        if (moduleCharacteristics != null) {
            HashMap<String, String> lowEventMap = HashMapPool.getMap(); // Get from pool
            lowEventMap.put("level", "" + level);

            EventOperationManager.getInstance(this).addEvent(
                    moduleCharacteristics.getLowMemoryEventCharacteristics(), lowEventMap);

            HashMapPool.releaseMap(lowEventMap);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

//        // Check for orientation changes if enabled
//        if (detectOrientationChanges && newConfig.orientation != lastScreenOrientation) {
//            lastScreenOrientation = newConfig.orientation;
//
//            // Handle orientation change if needed
//            if (screenshotsUpdater != null) {
//                screenshotsUpdater.destroyVirtualDisplay();
//                screenshotsUpdater.createVirtualDisplay();
//            }
//        }
    }

    /**
     * Stops the capture service and launches the main app activity (e.g. from logout).
     */
    public static void killCaptureUpload(Context context, ScreenshotModuleHost host) {
        if (host != null) {
            host.launchAppRunningActivity();
        }
        Intent stopIntent = new Intent(context, CaptureUploadService.class);
        context.stopService(stopIntent);
    }


    /**
     * Sets up and displays an ongoing notification for this service.
     */
    private void startNotification(String title, String text) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                Log.e(TAG, "Could not get NotificationManager");
                return;
            }

            PendingIntent contentIntent = getHost().getNotificationContentIntent();

            // Create notification channel for Android O and above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                int importance = NotificationManager.IMPORTANCE_LOW;
                NotificationChannel notificationChannel = new NotificationChannel(
                        NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_NAME, importance);
                notificationChannel.enableLights(false);
                notificationChannel.setShowBadge(true);
                notificationChannel.enableVibration(false);
                nm.createNotificationChannel(notificationChannel);
            }

            notification = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setOngoing(true)
                    .setSmallIcon(getHost().getNotificationIconResId())
                    .setContentIntent(contentIntent)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .build();

            // Show notification
            nm.notify(NOTIFICATION_ID, notification);

            // Start foreground service
            // Start foreground service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting notification", e);
        }
    }

    /**
     * 15 May 2025
     * Now when ever the screen turns off then we will stop the
     * media projection on android version 15 or higher
     * because according to the android documentation media projection will
     * be released when the screen turns off in android version 15 QPR1 or higher
     * Start wake detection to handle screen on/off events
     */
    private void startWakeDetection() {
        try {
            wakeReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action != null) {
                        if (action.equals(Intent.ACTION_SCREEN_ON)) {
                            if (screenshotsUpdater != null) {
                                screenshotsUpdater.screenAwake = true;

                            }else{
                                mediaProjection.stop();
                                screenshotsUpdater.destroyVirtualDisplay();
                                stopSelf();
                            }
                        } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                            if (screenshotsUpdater != null) {
                                
                                if (isAndroid15Higher()){
                                    Log.e(TAG,"Android version higher stop the service");
                                    mediaProjection.stop();
                                    screenshotsUpdater.destroyVirtualDisplay();
                                    stopSelf();
                                }
                                else{
                                    Log.e(TAG,"In screen off else part  ");

                                    screenshotsUpdater.screenAwake = false;
                                }

                            }
                        }
                    }
                }
            };

            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            registerReceiver(wakeReceiver, filter);
        } catch (Exception e) {
            Log.e(TAG, "Error starting wake detection", e);
        }
    }

    public  boolean isDeviceSecure(Context context) {
        KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguardManager != null) {
            return keyguardManager.isDeviceSecure();  // true if any lock (PIN/pattern/password/biometric) is set
        }
        return false;
    }

    public boolean isAndroid15Higher() {
        return Build.VERSION.SDK_INT >= 35; // Future versions beyond Android 15
    }

    /**
     * Stop wake detection
     */
    private void stopWakeDetection() {

        try {
            if (wakeReceiver != null) {
                unregisterReceiver(wakeReceiver);
//                wakeReceiver = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping wake detection", e);
        }
    }

    /**
     * Check if the service is running
     * @return true if the service is running, false otherwise
     */
    public static boolean isRunning() {
        return mIsRunning;
    }

    /**
     * Inner class to handle Settings database callbacks
     */
    public static class SettingsDatabaseListener implements SettingsManager.DatabaseListener{

        @Override
        public void onSuccess() {

        }

        @Override
        public void onFailure() {

        }
    }
}