package edu.stanford.communication.screenomics.interactions;

import java.util.HashMap;

/**
 * Single source for interaction (accessibility) event payload structure. Used by InteractionsUpdater.
 */
public final class InteractionsInfo {

    private InteractionsInfo() {}

    /** Build event map for an interaction activity (e.g. "clicked", "scroll-left", "scroll-up"). */
    public static HashMap<String, String> buildInteractionEventMap(String activity) {
        HashMap<String, String> map = new HashMap<>();
        map.put("activity", activity);
        return map;
    }
}
