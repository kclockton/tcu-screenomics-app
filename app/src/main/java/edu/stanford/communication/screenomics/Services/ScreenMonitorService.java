package edu.stanford.communication.screenomics.Services;

import android.app.ActivityManager;
import android.app.AlarmManager;
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
import android.media.projection.MediaProjectionManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import edu.stanford.communication.screenomics.Activity.AppRunningActivity;
import edu.stanford.communication.screenomics.screenshots.CaptureUploadStarter;
import edu.stanford.communication.screenomics.screenshots.CaptureUploadService;
import edu.stanford.communication.screenomics.Activity.LoginActivity;
import edu.stanford.communication.screenomics.Alarm.SetAlarm;
import edu.stanford.communication.screenomics.PermissionScreens.PermissionParentActivity;
import edu.stanford.communication.screenomics.DatabaseHelper.InterCommunicationPreference;
import edu.stanford.communication.screenomics.DatabaseHelper.LogInPreference;
import edu.stanford.communication.screenomics.FirebaseSettings.FirebaseManagerSingleton;
import edu.stanford.communication.screenomics.FirebaseSettings.SettingsManager;
import edu.stanford.communication.screenomics.FirebaseSettingsObserver;
import edu.stanford.communication.screenomics.R;
import edu.stanford.communication.screenomics.TextBasedData.EventMapBuilder;
import edu.stanford.communication.screenomics.TextBasedData.EventOperationManager;
import edu.stanford.communication.screenomics.TextBasedData.EventUploader;
import edu.stanford.communication.screenomics.TextBasedData.EventUploaderToFireStore;
import edu.stanford.communication.screenomics.activites.ActivitiesCollectionController;
import edu.stanford.communication.screenomics.apps.AppsCollectionController;
import edu.stanford.communication.screenomics.battery.BatteryCollectionController;
import edu.stanford.communication.screenomics.locations.LocationsCollectionController;
import edu.stanford.communication.screenomics.modulemanager.ModuleController;
import edu.stanford.communication.screenomics.network.NetworkCollectionController;
import edu.stanford.communication.screenomics.specs.SpecsCollectionController;

/**
 * This file take care of all the text based data
 * From Adding Data to offline database and then uploading
 * it online to firebase
*/
public class ScreenMonitorService extends Service {
    private Notification notification;

    private static final String TAG = "ScreenMonitorService";
    private static final String NOTIFICATION_CHANNEL_ID = "32312";
    private static final String NOTIFICATION_CHANNEL_NAME = "Screemoniter_name";
    private static final int NOTIFICATION_ID = 1001;
    private static final long MIN_SERVICE_RUNTIME = TimeUnit.HOURS.toMillis(5); // 5 hours
    private static final long MAX_SERVICE_RUNTIME = TimeUnit.HOURS.toMillis(6); // 6 hours
    /** Minimum time between showing media projection popup on screen-on; avoids duplicate popups when both SCREEN_ON and USER_PRESENT fire. */
    private static final long SCREEN_ON_RESTART_DEBOUNCE_MS = TimeUnit.SECONDS.toMillis(2);
    private static long sLastScreenOnRestartAttemptMs = 0;

    private BroadcastReceiver screenReceiver;
    private Handler autoStopHandler;
    private Runnable autoStopRunnable;

    private long plannedStopTime;

    RefreshSettings refreshSettings;

    EventUploaderToFireStore uploaderToFireStore;

    EventUploader uploader;
    private final Handler UploadTextDataHandler = new Handler(Looper.getMainLooper());

    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MyApp::ScreenCaptureWakeLock");
        wakeLock.acquire();

        PostNotification("Syncing data...");

        // Register receivers for screen and user presence events
        registerReceivers();

        EventOperationManager.getInstance(this);
        EventUploader.getInstance(this);
        FirebaseManagerSingleton.getFirestore();
        EventUploaderToFireStore.getInstance(this);

        // Set up auto-stop mechanism
        scheduleServiceAutoStop();

        Log.d(TAG, "Service created and started in foreground");
    }

    private void registerReceivers() {
        // Screen events receiver
        IntentFilter screenFilter = new IntentFilter();
        screenFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenFilter.addAction(Intent.ACTION_USER_PRESENT);

        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action != null) {
                    if (action.equals(Intent.ACTION_USER_PRESENT) || action.equals(Intent.ACTION_SCREEN_ON)) {
                        if (!isMyServiceRunning(CaptureUploadService.class, context)) {
                            attemptServiceRestart();
                        }
                    }
                }
            }
        };

        registerReceiver(screenReceiver, screenFilter);

    }

    private static boolean isMyServiceRunning(Class<?> serviceClass, Context context) {

        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void scheduleServiceAutoStop() {
        // Calculate a random runtime between 5-6 hours
        Random random = new Random();
        long randomRuntime = MIN_SERVICE_RUNTIME +
                random.nextInt((int) (MAX_SERVICE_RUNTIME - MIN_SERVICE_RUNTIME));

        plannedStopTime = System.currentTimeMillis() + randomRuntime;

        Log.d(TAG, "Service scheduled to auto-stop after " +
                TimeUnit.MILLISECONDS.toHours(randomRuntime) + " hours and " +
                TimeUnit.MILLISECONDS.toMinutes(randomRuntime) % 60 + " minutes");

        autoStopHandler = new Handler();
        autoStopRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Auto-stop timer triggered. Stopping and scheduling restart.");
                stopSelf();
            }
        };

        // Schedule the auto-stop
        autoStopHandler.postDelayed(autoStopRunnable, randomRuntime);
    }

    private void scheduleServiceRestart() {
        SetAlarm alarm = new SetAlarm();
        alarm.setRestartScreenObserverServiceAfter1Min(this);
    }

    private void PostNotification(String text) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                Log.e(TAG, "Could not get NotificationManager");
                return;
            }

            // Create pending intent for notification click
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                    new Intent(this, AppRunningActivity.class),
                    PendingIntent.FLAG_IMMUTABLE);

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

            // Calculate remaining runtime if auto-stop is scheduled
            String statusText = text;
            if (plannedStopTime > 0) {
                long remainingTime = plannedStopTime - System.currentTimeMillis();
                if (remainingTime > 0) {
                    long hours = TimeUnit.MILLISECONDS.toHours(remainingTime);
                    long minutes = TimeUnit.MILLISECONDS.toMinutes(remainingTime) % 60;
                    statusText = text + " (Auto-restart in " + hours + "h " + minutes + "m)";
                }
            }

            // Build the notification
            notification = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle("Screenomics")
                    .setContentText(statusText)
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.logo_stanford)
                    .setContentIntent(contentIntent)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .build();

            // Show notification
            nm.notify(NOTIFICATION_ID, notification);

            // Start foreground service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting notification", e);
        }
    }

    private void initializeComponents() {
        // Initialize uploaders
        uploaderToFireStore = EventUploaderToFireStore.getInstance(this);
        uploader = EventUploader.getInstance(this);
        uploader.startUploading();

        // Module collection is started in startMonitoringComponents via each module's controller.
    }

    
    private void initializeSettingsManager() {
        if (!SettingsManager.exists()) {
            Log.i("SettingsManager", "start setting manager");
            SettingsManager.create(new FirebaseSettingsObserver() {
                @Override
                public void onSettingsChanged(List<String> changedSettings) {

                }
            }).load(this, new SettingsDatabaseListener());
        }

        // Start settings refresh
        refreshSettings = new RefreshSettings(this, this, SettingsManager.val("settings-refresh-interval"));
        refreshSettings.start();

    }

    private void startMonitoringComponents() {
        BatteryCollectionController.startCollecting(this);
        AppsCollectionController.startCollecting(this);
        LocationsCollectionController.startCollecting(this);
        ActivitiesCollectionController.startCollecting(this);
        NetworkCollectionController.startCollecting(this);
        SpecsCollectionController.startCollecting(this);

        startTextUploadInterval();
    }

    /**
     * Runnable to upload text data periodically
     */
    private final Runnable textDataUploadTask = new Runnable() {
        @Override
        public void run() {
            StartUploadingOfflineTextDataToOnline();
            UploadTextDataHandler.postDelayed(this, SettingsManager.val("data-text-upload-interval"));
        }
    };

    /**
     * Start text data upload interval
     */
    private void startTextUploadInterval() {
        UploadTextDataHandler.postDelayed(textDataUploadTask, SettingsManager.val("data-text-upload-interval"));
    }

    /**
     * Stop text data upload interval
     */
    private void stopTextUploadInterval() {
        if (UploadTextDataHandler != null) {
            UploadTextDataHandler.removeCallbacks(textDataUploadTask);
        }
    }

    /**
     * Upload offline text data to online
     */
    private void StartUploadingOfflineTextDataToOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

            if (activeNetwork != null) {
                boolean shouldUpload = false;

                // Check if we should upload based on connection type
                if (SettingsManager.val("data-text-upload-wifi-only") == 1 &&
                        activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                    shouldUpload = true;
                } else if (SettingsManager.val("data-text-upload-wifi-only") == 0 &&
                        (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE ||
                                activeNetwork.getType() == ConnectivityManager.TYPE_WIFI)) {
                    shouldUpload = true;
                }

                if (shouldUpload) {
                    uploaderToFireStore.startUploadOfflineToOnlineEvents(false);
                }
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        initializeComponents();

        initializeSettingsManager();

        startMonitoringComponents();

        return START_STICKY_COMPATIBILITY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not providing binding
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service is being destroyed");

        // Cancel the auto-stop timer if service is manually destroyed
        if (autoStopHandler != null && autoStopRunnable != null) {
            autoStopHandler.removeCallbacks(autoStopRunnable);
        }

        if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }

        // Unregister receivers
        if (screenReceiver != null) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering screen receiver: " + e.getMessage());
            }
        }

        // Stop uploaders
        if (uploader != null) {
            uploader.stopUploading();
        }

        if (refreshSettings != null) {
            refreshSettings.stop();
        }

        BatteryCollectionController.stopCollecting(this);
        AppsCollectionController.stopCollecting(this);
        LocationsCollectionController.stopCollecting(this);
        ActivitiesCollectionController.stopCollecting(this);
        NetworkCollectionController.stopCollecting(this);
        SpecsCollectionController.stopCollecting(this);

        // Stop text upload interval
        stopTextUploadInterval();

        scheduleServiceRestart();

        EventOperationManager.getInstance(this).destroy();
        EventUploader.getInstance(this).destroy();
        FirebaseManagerSingleton.shutdown();
        EventUploaderToFireStore.destroy();
        SettingsManager.destroy(this);

        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);

        super.onDestroy();
    }

    // The restart method you provided
    private void attemptServiceRestart() {
            long now = SystemClock.elapsedRealtime();
            if (now - sLastScreenOnRestartAttemptMs < SCREEN_ON_RESTART_DEBOUNCE_MS) {
                Log.d(TAG, "Skipping attemptServiceRestart: debounce window");
                return;
            }
            sLastScreenOnRestartAttemptMs = now;

            InterCommunicationPreference interPref = new InterCommunicationPreference(this);
            if ("user".equals(interPref.getPauseCause())) {
                long userPauseMs = interPref.getUserPauseTimestampMs();
                if (userPauseMs != 0 && System.currentTimeMillis() - userPauseMs < TimeUnit.MINUTES.toMillis(30)) {
                    Log.d(TAG, "Skipping attemptServiceRestart: user-initiated pause within 30 min");
                    return;
                }
            }

            if (edu.stanford.communication.screenomics.ScreenomicsApplication.getAppInForeground()) {
                Log.d(TAG, "App is in foreground; not showing media projection dialog.");
                return;
            }

//            Intent restartIntent = new Intent(this, AppRunningActivity.class);
////            restartIntent.putExtra("start_service", true);
//            restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            restartIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_NO_ANIMATION);
//            restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//            this.startActivity(restartIntent);

        Intent activity_intent = null;

        // Check if the user is logged in.
        int login_result = LoginActivity.ensureCompleteLogin(this, null);

        if (login_result == -1) {
            return;
        }

        if (PermissionParentActivity.isPermissionFlowActive()) {
            return;
        }

        if (edu.stanford.communication.screenomics.ScreenomicsApplication.getAppInForeground()) {
            return;
        }

        activity_intent = new Intent(this.getApplicationContext(), CaptureUploadStarter.class);
        activity_intent.putExtra(CaptureUploadService.EXTRA_STARTUP_INSTIGATOR, "user");
        activity_intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        activity_intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY); // Don't leave activity in back stack; reduces "app taking over" feel

        if (activity_intent != null) {
            activity_intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity_intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            this.startActivity(activity_intent);
        }

    }

    public static class SettingsDatabaseListener implements SettingsManager.DatabaseListener{

        @Override
        public void onSuccess() {

        }

        @Override
        public void onFailure() {

        }
    }
}