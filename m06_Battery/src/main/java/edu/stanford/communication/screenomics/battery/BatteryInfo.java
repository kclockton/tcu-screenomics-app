package edu.stanford.communication.screenomics.battery;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Single source for battery event payload structure. Used by BatteryUpdater.
 */
public final class BatteryInfo {

    private BatteryInfo() {}

    /** Build event map for battery state (low/okay). */
    public static Map<String, String> buildBatteryStateMap(Context context, String action) {
        HashMap<String, String> map = new HashMap<>();
        map.put("percentage", String.valueOf(getBatteryPercentage(context)));
        map.put("action", action);
        return map;
    }

    /** Build event map for charging (connected/disconnected). */
    public static Map<String, String> buildChargingEventMap(Context context, String charging) {
        HashMap<String, String> map = new HashMap<>();
        map.put("percentage", String.valueOf(getBatteryPercentage(context)));
        map.put("charging", charging);
        return map;
    }

    public static int getBatteryPercentage(Context context) {
        Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 1);
            float percent = (level * 100.f) / scale;
            return (int) percent;
        }
        return -1;
    }
}
