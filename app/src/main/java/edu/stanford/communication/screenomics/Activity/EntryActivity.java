package edu.stanford.communication.screenomics.Activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import edu.stanford.communication.screenomics.PermissionScreens.PermissionParentActivity;
import edu.stanford.communication.screenomics.screenshots.CaptureUploadService;

/**
 * @author Jack Boffa
 * Activity that starts when the app is opened. This activity is not visible, but dispatches
 * the user to either LoginActivity or AppRunningActivity depending on if they're logged in.
 */
public class EntryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // Determine if the user has login data saved.
        int login_result = LoginActivity.ensureCompleteLogin(this, null);

        Intent intent;

//        // Check if there are any saved crashed logs pending upload.
//        if (CrashReportActivity.doCrashLogsExist(this))
//        {
//            intent = new Intent(this, CrashReportActivity.class);
//        }
//        // If the user is not logged in, go to the login page.
//        else
        if (login_result == -1)
        {
            intent = new Intent(this, LoginActivity.class);
        }
        // If so, check if CaptureUpload is running. If not, go to capture startup.
        else if (!CaptureUploadService.isRunning())
        {

//            PermissionChecker checker = new PermissionChecker(EntryActivity.this);

//            if (!checker.CheckIsAnyPermissionLeftFromBeingAllowed()) {
//                intent = new Intent(this, CapturePermissionActivity.class);
                intent = new Intent(this, PermissionParentActivity.class);
//            }
//            else {
//                intent = new Intent(this, PermissionParentActivity.class);
//            }

//            if (UsagePermissionActivity.appHasUsageAccess(this)) {
//                intent = new Intent(this, CapturePermissionActivity.class);
//            }
//            else {
//
//
//                intent = new Intent(this, UsagePermissionActivity.class);
//            }
        }
        // Otherwise, go to the screen that shows when the service is already running.
        else
        {
            intent = new Intent(this, AppRunningActivity.class);
        }

        startActivity(intent);

        // Exit this activity.
        finish();
    }
}
