package edu.stanford.communication.screenomics.network;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.util.Log;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

import edu.stanford.communication.screenomics.TextBasedData.EventOperationManager;
import edu.stanford.communication.screenomics.TextBasedData.HashMapPool;
import edu.stanford.communication.screenomics.modulemanager.ModuleCharacteristics;
import edu.stanford.communication.screenomics.modulemanager.ModuleController;

/**
 * Receives connectivity broadcasts and reports network events. Uses NetworkInfo for payload structure.
 * Owned by NetworkCollectionController. Also used by app for permission flow (same receiver type).
 */
public class NetworkUpdater extends BroadcastReceiver {

    private static final AtomicReference<String> lastNetworkStatus = new AtomicReference<>("");
    private static final AtomicReference<Boolean> lastIsWifi = new AtomicReference<>(null);

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ModuleController.ENABLE_NETWORK) {
            return;
        }
        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connMgr == null) return;

        android.net.NetworkInfo activeNetworkInfo = connMgr.getActiveNetworkInfo();
        HashMap<String, String> networkMap = HashMapPool.getMap();
        try {
            if (activeNetworkInfo != null && activeNetworkInfo.isConnected()) {
                boolean isWifi = activeNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI;
                String newNetworkStatus = isWifi ? "Connected-to-Wifi" : "Connected-to-DataPlan";
                if (!newNetworkStatus.equals(lastNetworkStatus.get())) {
                    networkMap.putAll(NetworkInfo.buildNetworkEventMap(newNetworkStatus));
                    EventOperationManager.getInstance(context).addEvent(
                            ModuleCharacteristics.getInstance().getNetworkEventCharacteristics(), networkMap);
                    lastNetworkStatus.set(newNetworkStatus);
                    lastIsWifi.set(isWifi);
                    Log.d("NetworkUpdater", "Updated network status: " + newNetworkStatus);
                }
            } else {
                if (lastIsWifi.get() != null) {
                    String disconnectStatus = lastIsWifi.get() ? "Disconnected-from-Wifi" : "Disconnected-from-DataPlan";
                    if (!disconnectStatus.equals(lastNetworkStatus.get())) {
                        networkMap.putAll(NetworkInfo.buildNetworkEventMap(disconnectStatus));
                        EventOperationManager.getInstance(context).addEvent(
                                ModuleCharacteristics.getInstance().getNetworkEventCharacteristics(), networkMap);
                        lastNetworkStatus.set(disconnectStatus);
                        lastIsWifi.set(null);
                        Log.d("NetworkUpdater", "Updated network status: " + disconnectStatus);
                    }
                }
            }
        } finally {
            HashMapPool.releaseMap(networkMap);
        }
    }
}
