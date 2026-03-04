package edu.stanford.communication.screenomics.activites;

import java.util.HashMap;
import java.util.Map;

/**
 * Single source for activities (step count) event payload structure. Used by ActivitiesUpdater.
 */
public final class ActivitiesInfo {

    private ActivitiesInfo() {}

    /** Build the event map for a step-count report. */
    public static Map<String, String> buildStepCountEventMap(int steps) {
        HashMap<String, String> map = new HashMap<>();
        map.put("count", String.valueOf(steps));
        return map;
    }
}
