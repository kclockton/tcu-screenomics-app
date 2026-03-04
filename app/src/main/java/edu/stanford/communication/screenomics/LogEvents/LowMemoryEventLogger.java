package edu.stanford.communication.screenomics.LogEvents;

import android.content.Context;
import java.util.HashMap;
import edu.stanford.communication.screenomics.TextBasedData.EventOperationManager;
import edu.stanford.communication.screenomics.TextBasedData.HashMapPool;
import edu.stanford.communication.screenomics.modulemanager.ModuleCharacteristics;

public final class LowMemoryEventLogger {
    private LowMemoryEventLogger() {}
    public static void log(Context context, int level) {
        HashMap<String, String> map = HashMapPool.getMap();
        map.put("level", String.valueOf(level));
        EventOperationManager.getInstance(context).addEvent(
                ModuleCharacteristics.getInstance().getLowMemoryEventCharacteristics(), map);
        HashMapPool.releaseMap(map);
    }
}
