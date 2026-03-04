package edu.stanford.communication.screenomics.AppUtils;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * App-level preferences for consent dialog, step count, and related session/UI state.
 * (Formerly Pref in Alarm package.)
 */

public class AppPreferences {

    private final String SHOW_CONSENT_DIALOG = "SHOW_CONSENT_DIALOG";

    private final Context context;
    private final String sharedPrefName = "screenomics_settings";

    public AppPreferences(Context context) {
        this.context = context;
    }

    public void Put_SHOW_CONSENT_DIALOG(boolean IsShowed) {
        SharedPreferences.Editor editor = context.getSharedPreferences(sharedPrefName, MODE_PRIVATE).edit();
        editor.putBoolean(SHOW_CONSENT_DIALOG, IsShowed);
        editor.apply();
    }

    public boolean GetShowConsentDialog() {
        SharedPreferences prefs = context.getSharedPreferences(sharedPrefName, MODE_PRIVATE);
        return prefs.getBoolean(SHOW_CONSENT_DIALOG, false);
    }
}
