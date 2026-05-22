package edu.stanford.communication.screenomics.screenomics;

import java.util.HashMap;
import java.util.Map;

/**
 * Single source for apps module event payload structure (foreground app, screen on/off). Used by AppsUpdater.
 */
public final class AppsInfo {

    private AppsInfo() {}

    /** Build event map for foreground app change. */
    public static Map<String, String> buildForegroundAppEventMap(String packageName) {
        HashMap<String, String> map = new HashMap<>();
        map.put("package", packageName);
        return map;
    }

    /** Build event map for screen on/off. */
    public static Map<String, String> buildScreenOnOffEventMap(boolean screenOn, boolean notification) {
        HashMap<String, String> map = new HashMap<>();
        map.put("screen", screenOn ? "on" : "off");
        map.put("notification", notification ? "yes" : "no");
        return map;
    }
}
