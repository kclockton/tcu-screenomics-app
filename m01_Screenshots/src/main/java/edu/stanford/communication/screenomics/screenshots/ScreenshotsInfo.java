package edu.stanford.communication.screenomics.screenshots;

import java.util.HashMap;

/**
 * Single source for screenshot-related event payload structure. Used by ScreenshotsUpdater
 * and by supplementary classes (e.g. ImageUploaderToCloudStorage) when reporting events.
 */
public final class ScreenshotsInfo {

    private ScreenshotsInfo() {}

    /** Build event map for capture startup (instigator, app version, install code). */
    public static HashMap<String, String> buildCaptureStartupMap(String instigator,
                                                                String versionCode, String versionName, String installCode) {
        HashMap<String, String> map = new HashMap<>();
        map.put("instigator", instigator != null ? instigator : "");
        map.put("app-version-code", versionCode != null ? versionCode : "");
        map.put("app-version-name", versionName != null ? versionName : "");
        map.put("install-code", installCode != null ? installCode : "");
        return map;
    }

    /** Build event map for a successful screenshot. */
    public static HashMap<String, String> buildScreenshotEventMap(String filename,
                                                                  String screenshotOrderedTime, String screenshotOrderedTimeLocal) {
        HashMap<String, String> map = new HashMap<>();
        map.put("filename", filename != null ? filename : "(no image)");
        map.put("screenshot-ordered-time", screenshotOrderedTime != null ? screenshotOrderedTime : "");
        map.put("screenshot-ordered-time-local", screenshotOrderedTimeLocal != null ? screenshotOrderedTimeLocal : "");
        return map;
    }

    /** Build event map for a screenshot failure. */
    public static HashMap<String, String> buildScreenshotFailureMap(String filename, String error) {
        HashMap<String, String> map = new HashMap<>();
        map.put("filename", filename != null ? filename : "(no image)");
        map.put("error", error != null ? error : "");
        return map;
    }

    /** Build event map for screenshot upload phase (used by ImageUploaderToCloudStorage). */
    public static HashMap<String, String> buildScreenshotUploadEventMap(String phase, boolean error,
                                                                         String message, int remainingImgs, long remainingBytes) {
        HashMap<String, String> map = new HashMap<>();
        map.put("phase", phase != null ? phase : "");
        map.put("error", error ? "yes" : "no");
        map.put("message", message != null ? message : "");
        map.put("remaining-imgs", String.valueOf(remainingImgs));
        map.put("remaining-bytes", String.valueOf(remainingBytes));
        return map;
    }
}
