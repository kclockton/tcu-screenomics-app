package edu.stanford.communication.screenomics.LogEvents;

import android.content.Context;
import edu.stanford.communication.screenomics.TextBasedData.EventMapBuilder;
import edu.stanford.communication.screenomics.TextBasedData.EventUploaderToFireStore;

/** App-level lifecycle event: login or log-out. uploadSingleEvent. Payload: type (login/log-out). Ticker: [type]. */
public final class LogInOutEventLogger {
    private LogInOutEventLogger() {}
    public static void log(Context context, String type) {
        EventUploaderToFireStore uploader = EventUploaderToFireStore.getInstance(context);
        if (uploader == null) return;
        uploader.uploadSingleEvent("LogInOutEvent",
                EventMapBuilder.buildCompleteMap(null, type != null ? type : ""),
                context.getApplicationContext());
    }
}
