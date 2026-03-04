package edu.stanford.communication.screenomics.AppUtils;

import android.content.Context;
import android.text.TextUtils;

import java.util.UUID;

import edu.stanford.communication.screenomics.DatabaseHelper.LogInPreference;

/** Subject/group/install identity and session helpers. */
public final class AuthHelper {

    private AuthHelper() {}

    public static String getSubjectId(Context context) {
        LogInPreference sharedPref = new LogInPreference(context);
        return sharedPref.GetUserSubjId();
    }

    public static String getGroupCode(Context context) {
        LogInPreference sharedPref = new LogInPreference(context);
        return sharedPref.GetUserCode();
    }

    public static String getDataSubjectId(Context context) {
        return getDataSubjectId(getSubjectId(context));
    }

    public static String getDataSubjectId(String subjectId) {
        return subjectId.replaceAll("[^a-zA-Z0-9]", "");
    }

    public static boolean isInstallCodeSet(Context context) {
        return !TextUtils.isEmpty(getInstallCode(context));
    }

    public static String getInstallCode(Context context) {
        LogInPreference sharedPref = new LogInPreference(context);
        return sharedPref.GetInstallCode();
    }

    public static String setRandomInstallCode(Context context) {
        LogInPreference pref = new LogInPreference(context);
        String installcode = UUID.randomUUID().toString();
        pref.AddInstallCode(installcode);
        return installcode;
    }
}
