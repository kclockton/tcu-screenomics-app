package edu.stanford.communication.screenomics.Activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import edu.stanford.communication.screenomics.PermissionScreens.PermissionParentActivity;
import edu.stanford.communication.screenomics.screenshots.CaptureUploadService;

/**
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

        if (login_result == -1)
        {
            intent = new Intent(this, LoginActivity.class);
        }
        // If so, check if CaptureUpload is running. If not, go to capture startup.
        else if (!CaptureUploadService.isRunning())
        {
                intent = new Intent(this, PermissionParentActivity.class);

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
