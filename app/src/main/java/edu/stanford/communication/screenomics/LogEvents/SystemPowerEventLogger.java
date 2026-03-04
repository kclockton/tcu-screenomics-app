package edu.stanford.communication.screenomics.LogEvents;

import android.content.Context;
import java.util.HashMap;
import edu.stanford.communication.screenomics.FirebaseSettings.UtilsForFirebaseSettings;
import edu.stanford.communication.screenomics.TextBasedData.EventOperationManager;
import edu.stanford.communication.screenomics.TextBasedData.HashMapPool;
import edu.stanford.communication.screenomics.modulemanager.ModuleCharacteristics;

public final class SystemPowerEventLogger {
    private SystemPowerEventLogger() {}
    public static void log(Context context) {
        if (UtilsForFirebaseSettings.getSubjectId(context).isEmpty()) return;
        HashMap<String, String> map = HashMapPool.getMap();
        map.put("power", "on");
        EventOperationManager.getInstance(context).addEvent(
                ModuleCharacteristics.getInstance().getSystemPowerEventCharacteristics(), map);
        HashMapPool.releaseMap(map);
    }
}
