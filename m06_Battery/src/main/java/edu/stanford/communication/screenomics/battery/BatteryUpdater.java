package edu.stanford.communication.screenomics.battery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.HashMap;

import edu.stanford.communication.screenomics.TextBasedData.EventOperationManager;
import edu.stanford.communication.screenomics.TextBasedData.HashMapPool;
import edu.stanford.communication.screenomics.modulemanager.ModuleCharacteristics;

/**
 * Receives battery-related broadcasts and reports events. Uses BatteryInfo for payload structure.
 * Owned by BatteryCollectionController.
 */
public class BatteryUpdater extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        HashMap<String, String> batteryMap = HashMapPool.getMap();
        try {
            switch (action) {
                case Intent.ACTION_BATTERY_LOW:
                    batteryMap.putAll(BatteryInfo.buildBatteryStateMap(context, "low"));
                    EventOperationManager.getInstance(context).addEvent(
                            ModuleCharacteristics.getInstance().getBatteryStateEventCharacteristics(), batteryMap);
                    break;
                case Intent.ACTION_BATTERY_OKAY:
                    batteryMap.putAll(BatteryInfo.buildBatteryStateMap(context, "okay"));
                    EventOperationManager.getInstance(context).addEvent(
                            ModuleCharacteristics.getInstance().getBatteryStateEventCharacteristics(), batteryMap);
                    break;
                case Intent.ACTION_POWER_CONNECTED:
                    batteryMap.putAll(BatteryInfo.buildChargingEventMap(context, "yes"));
                    EventOperationManager.getInstance(context).addEvent(
                            ModuleCharacteristics.getInstance().getBatteryChargingEventCharacteristics(), batteryMap);
                    break;
                case Intent.ACTION_POWER_DISCONNECTED:
                    batteryMap.putAll(BatteryInfo.buildChargingEventMap(context, "no"));
                    EventOperationManager.getInstance(context).addEvent(
                            ModuleCharacteristics.getInstance().getBatteryChargingEventCharacteristics(), batteryMap);
                    break;
                default:
                    break;
            }
        } finally {
            HashMapPool.releaseMap(batteryMap);
        }
    }
}
