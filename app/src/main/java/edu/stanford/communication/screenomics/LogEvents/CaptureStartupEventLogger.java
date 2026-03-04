package edu.stanford.communication.screenomics.LogEvents;

import android.content.Context;
import java.util.HashMap;
import edu.stanford.communication.screenomics.TextBasedData.EventOperationManager;
import edu.stanford.communication.screenomics.TextBasedData.HashMapPool;
import edu.stanford.communication.screenomics.modulemanager.ModuleCharacteristics;

public final class CaptureStartupEventLogger {
    private CaptureStartupEventLogger() {}
    public static void log(Context ctx, String instigator, String versionCode, String versionName, String installCode) {
        HashMap<String, String> map = HashMapPool.getMap();
        map.put("instigator", instigator != null ? instigator : "");
        map.put("app-version-code", versionCode != null ? versionCode : "");
        map.put("app-version-name", versionName != null ? versionName : "");
        map.put("install-code", installCode != null ? installCode : "");
        EventOperationManager.getInstance(ctx).addEvent(
                ModuleCharacteristics.getInstance().getCaptureStartupCharacteristics(), map);
        HashMapPool.releaseMap(map);
    }
}
