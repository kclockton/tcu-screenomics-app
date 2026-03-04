package edu.stanford.communication.screenomics.LogEvents;

import android.content.Context;
import java.util.HashMap;
import edu.stanford.communication.screenomics.TextBasedData.EventOperationManager;
import edu.stanford.communication.screenomics.TextBasedData.HashMapPool;
import edu.stanford.communication.screenomics.modulemanager.ModuleCharacteristics;

public final class ScreenOnOffEventLogger {
    private ScreenOnOffEventLogger() {}
    public static void log(Context context, boolean screenOn, boolean notification) {
        HashMap<String, String> map = HashMapPool.getMap();
        map.put("screen", screenOn ? "on" : "off");
        map.put("notification", notification ? "yes" : "no");
        EventOperationManager.getInstance(context).addEvent(
                ModuleCharacteristics.getInstance().getPowerScreenOnOffCharacteristics(), map);
        HashMapPool.releaseMap(map);
    }
}
