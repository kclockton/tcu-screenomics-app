package edu.stanford.communication.screenomics.DatabaseHelper;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;

public class InterCommunicationPreference {

    /** May 7, 2025 This file helps the app to send signals about the work **/

    private Context context;
    private final String Shared_prefrence = "inter_communication_pref";
    private final String IsNewNotificationPopped = "notificationPopped";
    private final String IsUserTurnedOffTheService = "user_turn_off_service_or_device_stopped";
    /** Reason capture was paused: "user" (user tapped pause) or "system" (media projection stopped, etc.). Cleared after read. */
    private final String PauseCause = "pause_cause";
    /** When user-initiated pause started (epoch ms). Used to suppress popup for 30 min and for 7 AM/7 PM follow-up. */
    private final String UserPauseTimestampMs = "user_pause_timestamp_ms";

    private final String IsDeviceOff = "isDeviceOff";

    // This is used to send message to the screen capture service So that we can get mediaprojection instance according to activity
    private final String WhichActivityStartedService = "WhichActivityStartedService";
    private final String BATTERY_OPTIMIZATION = "BatteryOptimization";

    private SharedPreferences.Editor editor;
    private SharedPreferences prefs;

    public InterCommunicationPreference(Context context) {
        this.context = context;
    }

    public void PutNewNotificationPopped(boolean value){

        editor = context.getSharedPreferences(Shared_prefrence, MODE_PRIVATE).edit();
        editor.putBoolean(IsNewNotificationPopped,value);
        editor.apply();
    }

    public void WhichActivityCalledStartService(int value){
        editor = context.getSharedPreferences(Shared_prefrence, MODE_PRIVATE).edit();
        editor.putInt(WhichActivityStartedService,value);
        editor.apply();
    }

    public boolean GET_NewNotificationPopped(){
        prefs = context.getSharedPreferences(Shared_prefrence, MODE_PRIVATE);
        return prefs.getBoolean(IsNewNotificationPopped,false);
    }

    public int GET_WhichActivityStartedService(){
        prefs = context.getSharedPreferences(Shared_prefrence, MODE_PRIVATE);
        // 0 for the CaptureUploadStater
        // 1 for the appRunningActivity
        return prefs.getInt(WhichActivityStartedService,0);
    }

    // New Function Battery Optimization

    public void PutShowBatteryOptimizationDialog(int value){

        editor = context.getSharedPreferences(Shared_prefrence, MODE_PRIVATE).edit();
        editor.putInt(BATTERY_OPTIMIZATION,value);
        editor.apply();
    }

    // New Function Battery Optimization

    public int GetShowBatteryOptimizationDialog(){

        prefs = context.getSharedPreferences(Shared_prefrence, MODE_PRIVATE);
        return prefs.getInt(BATTERY_OPTIMIZATION,0);
    }

    /** Set before stopping the service when user taps the pause/turn-off button. Persisted so alarm can read it. */
    public void putPauseCause(String cause) {
        editor = context.getSharedPreferences(Shared_prefrence, MODE_PRIVATE).edit();
        editor.putString(PauseCause, cause);
        if ("user".equals(cause)) {
            editor.putLong(UserPauseTimestampMs, System.currentTimeMillis());
        }
        editor.apply();
    }

    /** Get pause cause without clearing (for alarm logic). Returns null if never set. */
    public String getPauseCause() {
        prefs = context.getSharedPreferences(Shared_prefrence, MODE_PRIVATE);
        return prefs.getString(PauseCause, null);
    }

    /** Get and clear the pause cause (for uploading event). Cleared when service starts. */
    public String getAndClearPauseCause() {
        prefs = context.getSharedPreferences(Shared_prefrence, MODE_PRIVATE);
        String cause = prefs.getString(PauseCause, null);
        editor = context.getSharedPreferences(Shared_prefrence, MODE_PRIVATE).edit();
        editor.remove(PauseCause);
        editor.apply();
        return cause;
    }

    /** Clear pause cause when service starts (resumed). Also clears user-pause timestamp. */
    public void clearPauseCause() {
        editor = context.getSharedPreferences(Shared_prefrence, MODE_PRIVATE).edit();
        editor.remove(PauseCause);
        editor.remove(UserPauseTimestampMs);
        editor.apply();
    }

    /** Time when user-initiated pause started (epoch ms), or 0 if not set. Used to suppress popup for 30 min. */
    public long getUserPauseTimestampMs() {
        prefs = context.getSharedPreferences(Shared_prefrence, MODE_PRIVATE);
        return prefs.getLong(UserPauseTimestampMs, 0L);
    }

    private final String ResumeCause = "resume_cause";

    /** Set before starting the service: "user" (turned switch on) or "system" (e.g. screen-on re-request). */
    public void putResumeCause(String cause) {
        editor = context.getSharedPreferences(Shared_prefrence, MODE_PRIVATE).edit();
        editor.putString(ResumeCause, cause);
        editor.apply();
    }

    /** Get and clear resume cause for Resumed event upload. */
    public String getAndClearResumeCause() {
        prefs = context.getSharedPreferences(Shared_prefrence, MODE_PRIVATE);
        String cause = prefs.getString(ResumeCause, null);
        editor = context.getSharedPreferences(Shared_prefrence, MODE_PRIVATE).edit();
        editor.remove(ResumeCause);
        editor.apply();
        return cause != null ? cause : "system";
    }

}
