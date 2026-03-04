package edu.stanford.communication.screenomics.Services;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;

import edu.stanford.communication.screenomics.FirebaseSettings.SettingsManager;

/**
 * Periodically refreshes settings from the database while ScreenMonitorService is running.
 */
public class RefreshSettings {

    private Context context;
    private long intervalMillis;
    private Handler handler;
    private Runnable runnableTask;
    private ScreenMonitorService service1;

    public RefreshSettings(ScreenMonitorService service, Context context, long intervalMillis) {
        this.context = context;
        this.service1 = service;
        this.intervalMillis = intervalMillis;
        this.handler = new Handler();
    }

    public void start() {
        runnableTask = new Runnable() {
            @Override
            public void run() {
                performTask();
                handler.postDelayed(this, intervalMillis);
            }
        };
        handler.post(runnableTask);
    }

    public void updateIntervalIfNeeded(long newIntervalMillis) {
        if (this.intervalMillis != newIntervalMillis) {
            stop();
            this.intervalMillis = newIntervalMillis;
            start();
        }
    }

    public void stop() {
        handler.removeCallbacks(runnableTask);
    }

    private void performTask() {
        if (SettingsManager.exists()) {
            ScreenMonitorService service = service1;
            ScreenMonitorService.SettingsDatabaseListener listener = null;
            if (service != null) listener = new ScreenMonitorService.SettingsDatabaseListener();
            if (isMyServiceRunning(ScreenMonitorService.class, context)) {
                SettingsManager.get().loadFromDatabase(context, false, listener);
            } else {
                stop();
            }
        }
    }

    private boolean isMyServiceRunning(Class<?> serviceClass, Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return false;
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
