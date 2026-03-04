package edu.stanford.communication.screenomics.power;

import java.util.HashMap;
import java.util.Map;

/**
 * Single source for power (SystemPowerEvent) payload structure. Used by PowerUpdater.
 */
public final class PowerInfo {

    private PowerInfo() {}

    /** Build event map for power-on report. */
    public static Map<String, String> buildPowerOnEventMap() {
        HashMap<String, String> map = new HashMap<>();
        map.put("power", "on");
        return map;
    }
}
