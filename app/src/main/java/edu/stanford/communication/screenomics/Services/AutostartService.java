package edu.stanford.communication.screenomics.Services;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;
import androidx.core.content.ContextCompat;

import edu.stanford.communication.screenomics.screenshots.CaptureUploadStarter;
import edu.stanford.communication.screenomics.Activity.LoginActivity;
import edu.stanford.communication.screenomics.Alarm.SetAlarm;
import edu.stanford.communication.screenomics.power.PowerCollectionController;
import edu.stanford.communication.screenomics.screenshots.CaptureUploadService;
import edu.stanford.communication.screenomics.screenshots.ScreenshotsCollectionController;

/**
 * May 7, 2025
 * Service that starts Screenomics when the phone boots.
 */
public class AutostartService extends BroadcastReceiver
{
    public static final String TAG = "AutostartReceiver";
    public static final String QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON";

    @Override
    public void onReceive(Context context, Intent intent)
    {
            if (!isMyServiceRunning(CaptureUploadService.class, context))
            {

                Log.d(TAG, "Trying autostart now...");

                StartTheService(context);
            }
            else
            {
                Log.e(TAG, "CaptureUploadService is already running! (at least according to isRunning())");
            }

    }

    private boolean isMyServiceRunning(Class<?> serviceClass, Context context) {

        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void StartTheService(Context ctx){

        Intent activity_intent;

        PowerCollectionController.reportPowerOn(ctx);

        // Check if the user is logged in.
        int login_result = LoginActivity.ensureCompleteLogin(ctx, null);

        // If not logged in, we gotta get in the user's face dood. Show then the login page.
//        else
        if(login_result == -1) {
            activity_intent = new Intent(ctx, LoginActivity.class);
        }
        else {

            SetAlarm alarm = new SetAlarm();
            ScreenshotsCollectionController screenshotController = new ScreenshotsCollectionController();
            screenshotController.schedule2MinAlarm(ctx);
            alarm.SetAlarmFor4HoursToAskUserAgainToOpenTheAppAndStartTheService(ctx);
            screenshotController.schedule30MinAskAgainAlarm(ctx);

            ContextCompat.startForegroundService(ctx,new Intent(ctx,ScreenMonitorService.class));

//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                ctx.startForegroundService(new Intent(ctx, ScreenMonitorService.class));
//            }else{
//
//                ctx.startService(new Intent(ctx, ScreenMonitorService.class));
//            }

            if (edu.stanford.communication.screenomics.PermissionScreens.PermissionParentActivity.isPermissionFlowActive()) {
                return;
            }
            if (edu.stanford.communication.screenomics.ScreenomicsApplication.getAppInForeground()) {
                return;
            }
            activity_intent = new Intent(ctx, CaptureUploadStarter.class);
            activity_intent.putExtra(CaptureUploadService.EXTRA_STARTUP_INSTIGATOR, "boot");
            activity_intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        }

        if (activity_intent != null) {
            activity_intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity_intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            ctx.startActivity(activity_intent);
        }

    }

}
