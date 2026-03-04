package edu.stanford.communication.screenomics.power;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.HashMap;
import java.util.Objects;

import edu.stanford.communication.screenomics.FirebaseSettings.UtilsForFirebaseSettings;
import edu.stanford.communication.screenomics.TextBasedData.EventOperationManager;
import edu.stanford.communication.screenomics.TextBasedData.HashMapPool;
import edu.stanford.communication.screenomics.modulemanager.ModuleCharacteristics;

/**
 * Reports system power events (e.g. power on). Used by PowerCollectionController and BootReceiver.
 */
public final class PowerUpdater {

    private static final String TAG = "PowerUpdater";

    private PowerUpdater() {}

    /**
     * Report a "power on" SystemPowerEvent if the user is logged in.
     */
    public static void reportPowerOn(Context context) {
        if (UtilsForFirebaseSettings.getSubjectId(context).isEmpty()) {
            return;
        }
        HashMap<String, String> map = HashMapPool.getMap();
        map.putAll(PowerInfo.buildPowerOnEventMap());
        EventOperationManager.getInstance(context).addEvent(
                ModuleCharacteristics.getInstance().getSystemPowerEventCharacteristics(), map);
        HashMapPool.releaseMap(map);
    }

    /**
     * Receiver for boot completed. Reports power-on event when user is logged in.
     * Referenced from AndroidManifest as PowerUpdater.BootReceiver.
     */
    public static final class BootReceiver extends BroadcastReceiver {
        public static final String QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON";

        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                if (Objects.equals(intent.getAction(), Intent.ACTION_BOOT_COMPLETED)
                        || Objects.equals(intent.getAction(), QUICKBOOT_POWERON)) {
                    reportPowerOn(context);
                } else {
                    Log.e(TAG, "BootReceiver received wrong action: " + intent.getAction());
                }
            } catch (Exception e) {
                Log.e(TAG, "BootReceiver error", e);
            }
        }
    }
}
