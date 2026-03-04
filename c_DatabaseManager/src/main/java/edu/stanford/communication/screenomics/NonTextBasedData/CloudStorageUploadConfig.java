package edu.stanford.communication.screenomics.NonTextBasedData;

/**
 * Configuration for uploading files to Cloud Storage (screenshots, audio, etc.).
 * Identifies which SettingsManager keys to use for upload interval, wifi-only, and kill-switch.
 * Use the default instance for the shared pipeline, or create with custom keys for a
 * module-specific pipeline (e.g. audio with its own interval).
 * <p>
 * Note: Data collection modules that upload files still use {@link edu.stanford.communication.screenomics.TextBasedData.EventOperationManager EventOperationManager}
 * for their <em>event</em> data (e.g. "upload started", "capture started"); only the file upload
 * scheduling and policy use this package.
 */
public class CloudStorageUploadConfig {

    private final String intervalSettingKey;
    private final String wifiOnlySettingKey;
    private final String killSwitchSettingKey;

    /**
     * Shared config used by screenshots (and optionally other modules that share the same
     * data-nontext-* and kill-switch settings).
     */
    public static final CloudStorageUploadConfig DEFAULT = new CloudStorageUploadConfig(
            "data-nontext-upload-interval",
            "data-nontext-upload-wifi-only",
            "kill-switch"
    );

    public CloudStorageUploadConfig(String intervalSettingKey, String wifiOnlySettingKey, String killSwitchSettingKey) {
        this.intervalSettingKey = intervalSettingKey;
        this.wifiOnlySettingKey = wifiOnlySettingKey;
        this.killSwitchSettingKey = killSwitchSettingKey;
    }

    public String getIntervalSettingKey() {
        return intervalSettingKey;
    }

    public String getWifiOnlySettingKey() {
        return wifiOnlySettingKey;
    }

    public String getKillSwitchSettingKey() {
        return killSwitchSettingKey;
    }
}
