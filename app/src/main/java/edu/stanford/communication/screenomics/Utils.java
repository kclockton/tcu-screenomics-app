package edu.stanford.communication.screenomics;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.util.Map;

import edu.stanford.communication.screenomics.Activity.LoginActivity;
import edu.stanford.communication.screenomics.Alarm.SetAlarm;
import edu.stanford.communication.screenomics.AppUtils.AuthHelper;
import edu.stanford.communication.screenomics.AppUtils.DeviceUtils;
import edu.stanford.communication.screenomics.AppUtils.PathUtils;
import edu.stanford.communication.screenomics.Services.ScreenMonitorService;
import edu.stanford.communication.screenomics.TextBasedData.EventDatabaseHelper;
import edu.stanford.communication.screenomics.TextBasedData.EventMapBuilder;
import edu.stanford.communication.screenomics.TextBasedData.EventOperationManager;
import edu.stanford.communication.screenomics.TextBasedData.NetworkUtils;
import edu.stanford.communication.screenomics.TextBasedData.EventUploaderToFireStore;

public class Utils {

    public static String getSubjectId(Context context) {
        return AuthHelper.getSubjectId(context);
    }

    public static String getGroupCode(Context context) {
        return AuthHelper.getGroupCode(context);
    }

    public static String getDataSubjectId(Context context) {
        return AuthHelper.getDataSubjectId(context);
    }

    public static String getDataSubjectId(String subjectId) {
        return AuthHelper.getDataSubjectId(subjectId);
    }

    public static void createOptionsMenu(Menu menu)
    {
//        menu.add(Menu.NONE, 2, Menu.NONE, R.string.menu_dumplog);
        menu.add(Menu.NONE, 1, Menu.NONE, R.string.menu_zlogout);
    }

    public static Map<String, String> createPhoneSpecMap() {
        return DeviceUtils.createPhoneSpecMap();
    }

    public static boolean isInstallCodeSet(Context context) {
        return AuthHelper.isInstallCodeSet(context);
    }

    public static String getInstallCode(Context context) {
        return AuthHelper.getInstallCode(context);
    }

    public static String setRandomInstallCode(Context context) {
        return AuthHelper.setRandomInstallCode(context);
    }

    public static String getCrashLogDirectory(Context context) {
        return PathUtils.getCrashLogDirectory(context);
    }

    public static String getBackupCrashLogDirectory() {
        return PathUtils.getBackupCrashLogDirectory();
    }

    public static int getBatteryPercentage(Context context) {
        return DeviceUtils.getBatteryPercentage(context);
    }

    public static void optionsItemSelected(Activity activity, MenuItem item)
    {
        switch (item.getItemId())
        {
            case 1:
                // Do logout.

                int waitTime = estimateLogoutWaitTime(activity);

                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setMessage("Are you sure ?");
                builder.setTitle("Confirmation");
                builder.setCancelable(false);
                builder.setPositiveButton("Logout", (DialogInterface.OnClickListener) (dialog, which) -> {

                    if (NetworkUtils.isInternetAvailable(activity)){
                        LoginActivity.IsUserWantLoginOrRegister = false;

                        edu.stanford.communication.screenomics.LogEvents.LogInOutEventLogger.log(activity.getApplicationContext(), "log-out");
                        edu.stanford.communication.screenomics.LogEvents.ScreenshotPauseEventLogger.log(activity.getApplicationContext(), "Paused", "user");

                        EventUploaderToFireStore uploaderToFireStore = EventUploaderToFireStore.getInstance(activity);
                        uploaderToFireStore.startUploadOfflineToOnlineEvents(true);

                        Toast.makeText(activity, "Please Wait.....", Toast.LENGTH_SHORT).show();

                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {

                                activity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        LoginActivity.userLogOut(activity,true);
                                        LoginActivity.IsUserWantLoginOrRegister = false;
                                        Toast.makeText(activity, "Successfully Logged Out", Toast.LENGTH_SHORT).show();
                                        LoginActivity.userLogOut(activity, true);

                                        activity.stopService(new Intent(activity, ScreenMonitorService.class));

                                        SetAlarm alarm = new SetAlarm();
                                        alarm.CancelAlarm(activity,5);
//                                        EventDatabaseHelper.getInstance(activity).deleteDatabase(activity);
                                        activity.finishAffinity();
                                    }
                                });

                            }
                        }, waitTime * 1000);

                    }else {
                        Toast.makeText(activity, "Please turn on internet to log out.", Toast.LENGTH_SHORT).show();
                    }
                    dialog.cancel();

                });

                builder.setNegativeButton("No", (DialogInterface.OnClickListener) (dialog, which) -> {

                    dialog.cancel();
                });

                AlertDialog alertDialog = builder.create();
                alertDialog.show();

                break;

                // For Dump log button

//            case 2:
//                // Create and upload logcat.
////                boolean result = LogMaker.uploadLog(LogMaker.dumpLogcat(activity));
////                Toast.makeText(activity, result ? R.string.toast_dumplog_success : R.string.toast_dumplog_fail, Toast.LENGTH_SHORT).show();
//
//                int a = 5/0;
//
//                break;
        }
    }

    // Memory management New function

    // This function is used to calculate the estimated time the user will have to wait before logging out. So we can upload our remaining data to firebase.

    private static int estimateLogoutWaitTime(Activity activity) {
        EventDatabaseHelper dbHelper = EventDatabaseHelper.getInstance(activity);
        int totalEntries = dbHelper.getTotalEventCount(); // Get total SQLite entries
        int batchSize = 500; // Recommended Firestore batch size
        int uploadTimePerBatch = 3; // Approximate seconds per batch (can be adjusted)

        if (totalEntries == 0) return 0; // No data, no wait time

        int totalBatches = (int) Math.ceil((double) totalEntries / batchSize);
        return totalBatches * uploadTimePerBatch; // Estimated wait time in seconds
    }

}
