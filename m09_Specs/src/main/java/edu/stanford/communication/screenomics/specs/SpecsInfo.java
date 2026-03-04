package edu.stanford.communication.screenomics.specs;

import android.os.Build;

import java.util.HashMap;
import java.util.Map;

/**
 * Single source for device/build specs. Used at registration (once, from app) and on specs-check-interval (SpecsUpdater).
 */
public class SpecsInfo {

    /**
     * Device/build specs collected for registration and periodic upload.
     */
    public static Map<String, String> createPhoneSpecMap() {
        HashMap<String, String> map = new HashMap<>();
        map.put("fingerprint", Build.FINGERPRINT);
        map.put("manufacturer", Build.MANUFACTURER);
        map.put("brand", Build.BRAND);
        map.put("model", Build.MODEL);
        map.put("product", Build.PRODUCT);
        map.put("display-id", Build.DISPLAY);
        map.put("android-version", Build.VERSION.RELEASE);
        return map;
    }
}
