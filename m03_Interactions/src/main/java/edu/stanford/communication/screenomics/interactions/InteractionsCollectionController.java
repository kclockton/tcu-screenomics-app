package edu.stanford.communication.screenomics.interactions;

import android.content.Context;

import edu.stanford.communication.screenomics.DatabaseHelper.LogInPreference;
import edu.stanford.communication.screenomics.modulemanager.ModuleController;

/**
 * Gate for interactions (accessibility) collection. The accessibility service (InteractionsUpdater)
 * is started by the system when enabled; this controller provides shouldCollect() so the service
 * only reports events when the module is on and the user is logged in.
 */
public final class InteractionsCollectionController {

    private InteractionsCollectionController() {}

    /** True if interaction events should be reported (user logged in and module enabled). */
    public static boolean shouldCollect(Context context) {
        if (!ModuleController.ENABLE_INTERACTIONS) {
            return false;
        }
        LogInPreference sharedPref = new LogInPreference(context.getApplicationContext());
        String subjectId = sharedPref.GetUserSubjId().replace(".", "").replace("@", "").replace("_", "");
        return !subjectId.isEmpty();
    }
}
