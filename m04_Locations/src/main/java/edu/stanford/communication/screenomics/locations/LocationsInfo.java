package edu.stanford.communication.screenomics.locations;

import android.location.Location;

import java.util.HashMap;
import java.util.Map;

/**
 * Single source for location event payload structure. Used by LocationsUpdater when reporting.
 */
public final class LocationsInfo {

    private LocationsInfo() {}

    /**
     * Build the event map for a location report. Use null location when no location is available.
     * Time is set by the event pipeline (time, time-local); no separate timestamp field.
     */
    public static Map<String, String> buildLocationEventMap(Location location) {
        HashMap<String, String> map = new HashMap<>();
        if (location != null) {
            map.put("lat", String.valueOf(location.getLatitude()));
            map.put("lng", String.valueOf(location.getLongitude()));
            map.put("accuracy", String.valueOf(location.getAccuracy()));
        } else {
            map.put("lat", "null");
            map.put("lng", "null");
        }
        return map;
    }
}
