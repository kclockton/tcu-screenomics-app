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

    private final String CONSENT_MAIN = "CONSENT_MAIN";
    private final String CONSENT_FUTURE_DATA = "CONSENT_FUTURE_DATA";
    private final String CONSENT_FUTURE_CONTACT = "CONSENT_FUTURE_CONTACT";
    private final String CONSENT_PENDING_UPLOAD = "CONSENT_PENDING_UPLOAD";

    public void PutConsentMain(boolean value) {
        SharedPreferences.Editor editor = context.getSharedPreferences(sharedPrefName, MODE_PRIVATE).edit();
        editor.putBoolean(CONSENT_MAIN, value);
        editor.apply();
    }

    public boolean GetConsentMain() {
        SharedPreferences prefs = context.getSharedPreferences(sharedPrefName, MODE_PRIVATE);
        return prefs.getBoolean(CONSENT_MAIN, false);
    }

    public void PutConsentFutureData(boolean value) {
        SharedPreferences.Editor editor = context.getSharedPreferences(sharedPrefName, MODE_PRIVATE).edit();
        editor.putBoolean(CONSENT_FUTURE_DATA, value);
        editor.apply();
    }

    public boolean GetConsentFutureData() {
        SharedPreferences prefs = context.getSharedPreferences(sharedPrefName, MODE_PRIVATE);
        return prefs.getBoolean(CONSENT_FUTURE_DATA, false);
    }

    public void PutConsentFutureContact(boolean value) {
        SharedPreferences.Editor editor = context.getSharedPreferences(sharedPrefName, MODE_PRIVATE).edit();
        editor.putBoolean(CONSENT_FUTURE_CONTACT, value);
        editor.apply();
    }

    public boolean GetConsentFutureContact() {
        SharedPreferences prefs = context.getSharedPreferences(sharedPrefName, MODE_PRIVATE);
        return prefs.getBoolean(CONSENT_FUTURE_CONTACT, false);
    }

    public void PutConsentPendingUpload(boolean value) {
        SharedPreferences.Editor editor = context.getSharedPreferences(sharedPrefName, MODE_PRIVATE).edit();
        editor.putBoolean(CONSENT_PENDING_UPLOAD, value);
        editor.apply();
    }

    public boolean GetConsentPendingUpload() {
        SharedPreferences prefs = context.getSharedPreferences(sharedPrefName, MODE_PRIVATE);
        return prefs.getBoolean(CONSENT_PENDING_UPLOAD, false);
    }
}
