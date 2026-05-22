package edu.stanford.communication.screenomics.screenomics;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import edu.stanford.communication.screenomics.TextBasedData.EventOperationManager;
import edu.stanford.communication.screenomics.TextBasedData.HashMapPool;
import edu.stanford.communication.screenomics.modulemanager.ModuleCharacteristics;

/**
 * Foreground app (interval) and screen on/off (broadcast) updates. Uses AppsInfo for payload structure.
 * Owned by AppsCollectionController.
 */
public class AppsUpdater {

    private static final String TAG = "AppsUpdater";

    private final Context context;
    private long foregroundInterval;
    private String lastForegroundApp = "";
    private final Handler handler = new Handler();
    private Runnable foregroundRunnable;

    private static final ScreenOnOffReceiver sScreenOnOffReceiver = new ScreenOnOffReceiver();

    public AppsUpdater(Context context, long foregroundInterval) {
        this.context = context.getApplicationContext();
        this.foregroundInterval = foregroundInterval;
    }

    public void startForegroundChecking() {
        if (foregroundRunnable != null) {
            handler.removeCallbacks(foregroundRunnable);
        }
        foregroundRunnable = new Runnable() {
            @Override
            public void run() {
                String current = getForegroundApp();
                if (!current.equals(lastForegroundApp)) {
                    if (!current.isEmpty()) {
                        HashMap<String, String> map = HashMapPool.getMap();
                        map.putAll(AppsInfo.buildForegroundAppEventMap(current));
                        EventOperationManager.getInstance(context).addEvent(
                                ModuleCharacteristics.getInstance().getForegroundAppModuleCharacteristics(), map);
                        HashMapPool.releaseMap(map);
                    }
                    lastForegroundApp = current;
                }
                handler.postDelayed(this, foregroundInterval);
            }
        };
        handler.post(foregroundRunnable);
        Log.d(TAG, "Foreground app collection started");
    }

    public void stopForegroundChecking() {
        if (foregroundRunnable != null) {
            handler.removeCallbacks(foregroundRunnable);
            foregroundRunnable = null;
            Log.d(TAG, "Foreground app collection stopped");
        }
    }

    public void registerScreenOnOffReceiver(Context context) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        context.registerReceiver(sScreenOnOffReceiver, filter);
        Log.d(TAG, "Screen on/off collection started");
    }

    public void unregisterScreenOnOffReceiver(Context context) {
        try {
            context.unregisterReceiver(sScreenOnOffReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering screen on/off receiver: " + e.getMessage());
        }
        Log.d(TAG, "Screen on/off collection stopped");
    }

    private String getForegroundApp() {
        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) return "";
        long time = System.currentTimeMillis();
        List<UsageStats> appList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 10, time);
        if (appList != null && !appList.isEmpty()) {
            SortedMap<Long, UsageStats> sortedMap = new TreeMap<>();
            for (UsageStats usageStats : appList) {
                sortedMap.put(usageStats.getLastTimeUsed(), usageStats);
            }
            if (!sortedMap.isEmpty()) {
                String packageName = sortedMap.get(sortedMap.lastKey()).getPackageName();
                Log.d(TAG, "Current foreground app: " + packageName);
                return packageName;
            }
        }
        return "";
    }

    private static final class ScreenOnOffReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(action)) {
                PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                boolean fromNotification = powerManager != null && !powerManager.isInteractive();
                logScreenOnOff(true, fromNotification, context);
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                logScreenOnOff(false, false, context);
            }
        }

        private void logScreenOnOff(boolean screenOn, boolean notification, Context context) {
            Log.d("AppsUpdater", "screenOn: " + screenOn + " Notification: " + notification);
            HashMap<String, String> map = HashMapPool.getMap();
            map.putAll(AppsInfo.buildScreenOnOffEventMap(screenOn, notification));
            EventOperationManager.getInstance(context).addEvent(
                    ModuleCharacteristics.getInstance().getPowerScreenOnOffCharacteristics(), map);
            HashMapPool.releaseMap(map);
        }
    }
}
