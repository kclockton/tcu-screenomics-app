package edu.stanford.communication.screenomics.screenshots;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;

import androidx.core.content.ContextCompat;

import java.util.HashSet;
import java.util.Set;

import edu.stanford.communication.screenomics.DatabaseHelper.InterCommunicationPreference;
import edu.stanford.communication.screenomics.modulemanager.ModulePermissions;

/**
 * Invisible activity that requests media projection and launches CaptureUploadService.
 * An activity is required because we need startActivityForResult() to get the MediaProjection.
 * Uses ScreenshotModuleHost for app-level behavior (permission flow, foreground check, launch main app).
 */
public class CaptureUploadStarter extends Activity {

    /** Set by permission flow when user taps Allow; prevents service/alarm from showing dialog during setup. */
    public static final String EXTRA_FROM_PERMISSION_FLOW = "from_permission_flow";

    private static final int REQUEST_CODE = 100;
    private MediaProjectionManager mediaProjectionManager;
    private static MediaProjection mediaProjection;

    public static int ResultCode;
    public static Intent intent1;

    private static final Set<CaptureUploadStarter> sLiveInstances = new HashSet<>();

    private InterCommunicationPreference interCommunicationPrefrence;

    private ScreenshotModuleHost getHost() {
        return (ScreenshotModuleHost) getApplication();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        interCommunicationPrefrence = new InterCommunicationPreference(this);

        if (!ModulePermissions.needMediaProjection()) {
            Intent serv_intent = new Intent(getApplicationContext(), CaptureUploadService.class);
            if (getIntent().hasExtra(CaptureUploadService.EXTRA_STARTUP_INSTIGATOR)) {
                serv_intent.putExtra(CaptureUploadService.EXTRA_STARTUP_INSTIGATOR,
                        getIntent().getStringExtra(CaptureUploadService.EXTRA_STARTUP_INSTIGATOR));
            }
            ContextCompat.startForegroundService(getApplicationContext(), serv_intent);
            getHost().launchAppRunningActivity();
            finish();
            return;
        }

        boolean fromPermissionFlow = getIntent().getBooleanExtra(EXTRA_FROM_PERMISSION_FLOW, false);
        if (!fromPermissionFlow) {
            if (getHost().isPermissionFlowActive() || getHost().isAppInForeground()) {
                finish();
                return;
            }
        }

        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        interCommunicationPrefrence.WhichActivityCalledStartService(0);
        synchronized (sLiveInstances) {
            sLiveInstances.add(this);
        }
        Intent intent = mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(intent, REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        finishOtherInstances(this);
        if (requestCode != REQUEST_CODE) return;

        if (resultCode != Activity.RESULT_OK) {
            setResult(resultCode);
            finish();
            return;
        }

        interCommunicationPrefrence.WhichActivityCalledStartService(0);
        ResultCode = resultCode;
        intent1 = intent;

        Intent serv_intent = new Intent(getApplicationContext(), CaptureUploadService.class);
        if (getIntent().hasExtra(CaptureUploadService.EXTRA_STARTUP_INSTIGATOR)) {
            serv_intent.putExtra(CaptureUploadService.EXTRA_STARTUP_INSTIGATOR,
                    getIntent().getStringExtra(CaptureUploadService.EXTRA_STARTUP_INSTIGATOR));
        }
        ContextCompat.startForegroundService(getApplicationContext(), serv_intent);

        setResult(Activity.RESULT_OK);
        getHost().launchAppRunningActivity();
        finish();
    }

    public static MediaProjection getMediaProjection() {
        return mediaProjection;
    }

    public static Intent getIntents() {
        return intent1;
    }

    public static int getResultCode() {
        return ResultCode;
    }

    @Override
    protected void onDestroy() {
        synchronized (sLiveInstances) {
            sLiveInstances.remove(this);
        }
        super.onDestroy();
    }

    private static void finishOtherInstances(CaptureUploadStarter exclude) {
        CaptureUploadStarter[] others;
        synchronized (sLiveInstances) {
            others = sLiveInstances.toArray(new CaptureUploadStarter[0]);
        }
        for (CaptureUploadStarter other : others) {
            if (other != null && other != exclude && !other.isFinishing()) {
                other.finish();
            }
        }
    }
}
