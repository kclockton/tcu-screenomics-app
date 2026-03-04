package edu.stanford.communication.screenomics.LogEvents;

import android.content.Context;
import java.util.HashMap;
import edu.stanford.communication.screenomics.TextBasedData.EventOperationManager;
import edu.stanford.communication.screenomics.TextBasedData.HashMapPool;
import edu.stanford.communication.screenomics.modulemanager.ModuleCharacteristics;

public final class AlarmManagerNotificationEventLogger {
    private AlarmManagerNotificationEventLogger() {}
    public static void log(Context context, String activity) {
        HashMap<String, String> map = HashMapPool.getMap();
        String a = activity != null ? activity : "delivered";
        map.put("activity", a);
        EventOperationManager.getInstance(context).addEvent(
                ModuleCharacteristics.getInstance().getAlarmManagerCharacteristics(), map);
        HashMapPool.releaseMap(map);
    }
}
