package edu.stanford.communication.screenomics.network;

import java.util.HashMap;
import java.util.Map;

/**
 * Single source for network (connectivity) event payload structure. Used by NetworkUpdater.
 */
public final class NetworkInfo {

    private NetworkInfo() {}

    /** Build event map for network activity (e.g. Connected-to-Wifi, Disconnected-from-DataPlan). */
    public static Map<String, String> buildNetworkEventMap(String activity) {
        HashMap<String, String> map = new HashMap<>();
        map.put("activity", activity);
        return map;
    }
}
