package edu.stanford.communication.screenomics.Alarm;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.HashMap;

import edu.stanford.communication.screenomics.Activity.AppRunningActivity;
import edu.stanford.communication.screenomics.DatabaseHelper.InterCommunicationPreference;
import edu.stanford.communication.screenomics.DatabaseHelper.LogInPreference;
import edu.stanford.communication.screenomics.screenshots.CaptureUploadService;
import edu.stanford.communication.screenomics.screenshots.CaptureUploadStarter;
import edu.stanford.communication.screenomics.screenshots.ScreenshotModuleHost;
import edu.stanford.communication.screenomics.Activity.LoginActivity;
import edu.stanford.communication.screenomics.Services.ScreenMonitorService;
import edu.stanford.communication.screenomics.TextBasedData.EventOperationManager;
import edu.stanford.communication.screenomics.TextBasedData.EventUploader;
import edu.stanford.communication.screenomics.TextBasedData.HashMapPool;
import edu.stanford.communication.screenomics.ScreenomicsApplication;
import edu.stanford.communication.screenomics.modulemanager.EventTimestamp;
import edu.stanford.communication.screenomics.modulemanager.ModuleCharacteristics;
import edu.stanford.communication.screenomics.power.PowerCollectionController;

public class BroadcastReceiverForAlarm extends BroadcastReceiver
{

    EventOperationManager eventOperationManager;
    ModuleCharacteristics moduleCharacteristics;

    EventTimestamp timestamp = new EventTimestamp();

    @Override
    public void onReceive(Context context, Intent intent) {

        SetAlarm alarm = new SetAlarm();

        eventOperationManager = EventOperationManager.getInstance(context);

        moduleCharacteristics = ModuleCharacteristics.getInstance();

        InterCommunicationPreference InterPrefrence = new InterCommunicationPreference(context);

        int requestCode = intent.getIntExtra("request_code", 0);

//        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);

        LogInPreference sharedPref = new LogInPreference(context);

//        String status = sharedPref.getString("user_subj_id", "");
        String status = sharedPref.GetUserSubjId();

        switch (requestCode) {

            case 0:
            case 5:
            case 8:
            case 10:
            case 11:
            case 12:
            case 13:
                // Handled by ScreenshotAlarmReceiver and ScreenshotAlarmHandlerImpl (m01 + app)
                break;

            case 1:
                // For 7 Pm
                ScreenshotModuleHost host = (ScreenshotModuleHost) context.getApplicationContext();
                if (!host.isCaptureServiceRunning(context) && isMyServiceRunning(ScreenMonitorService.class, context)) {

                    NotificationHelper manage = new NotificationHelper(context);
                    manage.Create_Channel();
                    manage.SendStartServiceNotification("Screen capture is paused. Please reopen the app to resume.", status);
                    alarm.SetAlarmFor7Am(context);
                    Log.d("Alarm","Request code 1 in if");
                    LogEvent(context);

                    Log.d("Alarm","Request code 0 in if");

                } else if (!host.isCaptureServiceRunning(context) && !isMyServiceRunning(ScreenMonitorService.class, context)) {

                    NotificationHelper manage = new NotificationHelper(context);
                    manage.Create_Channel();
                    manage.SendStartServiceNotification("App is not running. To resume data collection, please reopen the app.", status);
                    alarm.SetAlarmFor7Am(context);
                    Log.d("Alarm","Request code 1 in if");
                    LogEvent(context);

                    Log.d("Alarm","Request code 0 in if");
                }

//
//                if (!isMyServiceRunning(CaptureUploadService.class, context)) {
//                    NotificationHelper manage = new NotificationHelper(context);
//                    manage.Create_Channel();
//                    manage.SendStartServiceNotification("It seems that screenomics is not running. Please start again.", status);
//                    alarm.SetAlarmFor7Am(context);
//                    Log.d("Alarm","Request code 1 in if");
//                    LogEvent(context);
//
//                }

                break;

            case 2:
                // For 7 Am
                ScreenshotModuleHost host2 = (ScreenshotModuleHost) context.getApplicationContext();
                if (!host2.isCaptureServiceRunning(context) && isMyServiceRunning(ScreenMonitorService.class, context)) {

                    NotificationHelper manage = new NotificationHelper(context);
                    manage.Create_Channel();
                    manage.SendStartServiceNotification("Screen capture is paused. Please reopen the app to resume.", status);
                    alarm.SetAlarmFor7Pm(context);
                    Log.d("Alarm","Request code 1 in if");
                    LogEvent(context);

                    Log.d("Alarm","Request code 0 in if");

                } else if (!host2.isCaptureServiceRunning(context) && !isMyServiceRunning(ScreenMonitorService.class, context)) {

                    NotificationHelper manage = new NotificationHelper(context);
                    manage.Create_Channel();
                    manage.SendStartServiceNotification("App is not running. To resume data collection, please reopen the app.", status);
                    alarm.SetAlarmFor7Pm(context);
                    Log.d("Alarm","Request code 1 in if");
                    LogEvent(context);

                    Log.d("Alarm","Request code 0 in if");
                }

//                if (!isMyServiceRunning(CaptureUploadService.class, context)) {
//                    NotificationHelper manage = new NotificationHelper(context);
//                    manage.Create_Channel();
//                    manage.SendStartServiceNotification("It seems that screenomics is not running. Please start again.", status);
////                    alarm.SetAlarmFor7Am(context);
////                    alarm.check7AmIsCloserOrNotTime(context);
//                    alarm.SetAlarmFor7Pm(context);
//
//                    Log.d("Alarm","Request code 2 in if");
//                    LogEvent(context);
//
//                }
                break;

//            case 4:
//
//                LogEventsInFirebase firebase = new LogEventsInFirebase();
//
//                firebase.LogEvent(LogEventsInFirebase.GetSimpleClassName("StepCountEvent"),LogEventsInFirebase.GetDefaultMapWithAdditionalValue("step-count","count",pref.GetStepCount()),context);
//                pref.StepCount("0");
//                pref.ResetStepCount(true);
//
////                UploadEventsToFireStore.LogTicker(context,);
//
//                SetAlarm alarm1 = new SetAlarm();
//                alarm1.SetAlarmFor30MinToUploadStepCount(context);
//
//                break;

            case 6:

                SetAlarm alarm3 = new SetAlarm();
                if (!isMyServiceRunning(ScreenMonitorService.class,context)){

                    attemptServiceRestart(context);

                }

                alarm3.SetAlarmFor4HoursToAskUserAgainToOpenTheAppAndStartTheService(context);

                break;

            case 7:

                SetAlarm alarm4 = new SetAlarm();
                if (!isMyServiceRunning(ScreenMonitorService.class,context)){

                    attemptServiceRestart(context);

                }

                alarm4.SetAlarmFor4HoursToAskUserAgainToOpenTheAppAndStartTheService(context);

                break;

        }

    }

    private void LogEvent(Context context) {
        edu.stanford.communication.screenomics.LogEvents.AlarmManagerNotificationEventLogger.log(context, "delivered");
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

        Intent activity_intent = null;

        PowerCollectionController.reportPowerOn(ctx);

        // Check if the user is logged in.
        int login_result = LoginActivity.ensureCompleteLogin(ctx, null);

        if(login_result == -1) {
            SetAlarm alarm = new SetAlarm();
            alarm.CancelAlarm(ctx,5);
        }

        else {
            if (edu.stanford.communication.screenomics.PermissionScreens.PermissionParentActivity.isPermissionFlowActive()) {
                return;
            }
            if (edu.stanford.communication.screenomics.ScreenomicsApplication.getAppInForeground()) {
                return;
            }
            activity_intent = new Intent(ctx.getApplicationContext(), CaptureUploadStarter.class);
            activity_intent.putExtra(CaptureUploadService.EXTRA_STARTUP_INSTIGATOR, "user");
            activity_intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        }

        if (activity_intent != null) {
            activity_intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity_intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            ctx.startActivity(activity_intent);
        }

    }

    private void attemptServiceRestart(Context context) {

        int login_result = LoginActivity.ensureCompleteLogin(context, null);

        if (login_result == -1) {
            SetAlarm alarm = new SetAlarm();
            alarm.CancelAlarm(context,7);
            return;
        }

        ContextCompat.startForegroundService(context, new Intent(context, ScreenMonitorService.class));

//        context.startForegroundService(new Intent(context, ScreenMonitorService.class));
//
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            context.startForegroundService(new Intent(context, ScreenMonitorService.class));
//        }else{
//            context.startService(new Intent(context, ScreenMonitorService.class));
//
//        }

    }
}
