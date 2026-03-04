package edu.stanford.communication.screenomics.power;

import android.content.Context;

/**
 * Owns power (SystemPowerEvent) reporting. Delegates to PowerUpdater.
 */
public final class PowerCollectionController {

    /** Report a "power on" SystemPowerEvent if the user is logged in. */
    public static void reportPowerOn(Context context) {
        PowerUpdater.reportPowerOn(context);
    }
}
