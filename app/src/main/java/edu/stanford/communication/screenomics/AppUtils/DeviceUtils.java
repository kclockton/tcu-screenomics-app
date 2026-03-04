package edu.stanford.communication.screenomics.AppUtils;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;

import java.util.HashMap;
import java.util.Map;

/**
 * Device info: phone specs, battery, etc.
 */
public final class DeviceUtils {

    private DeviceUtils() {}

    public static Map<String, String> createPhoneSpecMap() {
        HashMap<String, String> map = new HashMap<>();
        map.put("fingerprint", Build.FINGERPRINT);
        map.put("manufacturer", Build.MANUFACTURER);
        map.put("brand", Build.BRAND);
        map.put("model", Build.MODEL);
        map.put("product", Build.PRODUCT);
        map.put("display-id", Build.DISPLAY);
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
