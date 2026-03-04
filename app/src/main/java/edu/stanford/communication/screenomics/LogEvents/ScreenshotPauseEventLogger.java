package edu.stanford.communication.screenomics.LogEvents;

import android.content.Context;
import java.util.HashMap;
import edu.stanford.communication.screenomics.TextBasedData.EventMapBuilder;
import edu.stanford.communication.screenomics.TextBasedData.EventUploaderToFireStore;
import edu.stanford.communication.screenomics.modulemanager.ModuleController;

/** App-level lifecycle event: screenshot capture Paused/Resumed. uploadSingleEvent. No-op if screenshots disabled. Ticker: [type] or [type cause]. */
public final class ScreenshotPauseEventLogger {
    private ScreenshotPauseEventLogger() {}
    public static void log(Context context, String type, String cause) {
        if (!ModuleController.ENABLE_SCREENSHOTS) return;
        EventUploaderToFireStore uploader = EventUploaderToFireStore.getInstance(context);
        if (uploader == null) return;
        HashMap<String, String> extra = new HashMap<>();
        if (cause != null && !cause.isEmpty()) extra.put("cause", cause);
        uploader.uploadSingleEvent("ScreenshotPauseEvent",
                EventMapBuilder.buildCompleteMap(extra.isEmpty() ? null : extra, type != null ? type : ""),
                context.getApplicationContext());
    }
}
