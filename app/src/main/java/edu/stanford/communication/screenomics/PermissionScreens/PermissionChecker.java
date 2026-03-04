package edu.stanford.communication.screenomics.PermissionScreens;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import edu.stanford.communication.screenomics.FirebaseSettingsObserver;
import edu.stanford.communication.screenomics.R;
import edu.stanford.communication.screenomics.FirebaseSettings.SettingsManager;
import edu.stanford.communication.screenomics.interactions.InteractionsUpdater;
import edu.stanford.communication.screenomics.modulemanager.ModuleController;
import edu.stanford.communication.screenomics.modulemanager.ModulePermissions;

import android.accessibilityservice.AccessibilityService;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import java.util.List;

public class PermissionChecker {

    Activity Activity;

    static String TAG = "PermissionChecker";

    AlertDialog alertDialog;

    private AlertDialog permissionDialog;

    public PermissionChecker(android.app.Activity activity) {
        Activity = activity;
    }

    private void AskForPermission(String Permission,String PermissionHeader){

        Dexter.withContext(Activity)
                .withPermission(Permission)
                .withListener(new PermissionListener() {
                    @Override public void onPermissionGranted(PermissionGrantedResponse response) {

                    }
                    @Override public void onPermissionDenied(PermissionDeniedResponse response) {

                        ShowDialog(Activity.getString(R.string.do_not_allow_note) + " Click on opens settings and allow settings." + PermissionHeader.toLowerCase()+".",PermissionHeader,Activity.getString(R.string.open_setting),true);

                    }
                    @Override public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {

                    }
                }).check();
    }

    public void checkPermissions() {
        // Load settings from SettingsManager
        if (!SettingsManager.exists()) {
            SettingsManager.create(new FirebaseSettingsObserver() {
                @Override
                public void onSettingsChanged(List<String> changedSettings) {

                }
            });
        }
        SettingsManager.get().load(Activity, new SettingsManager.DatabaseListener() {
            @Override
            public void onSuccess() {
                checkAndRequestPermissions();
            }

            @Override
            public void onFailure() {
                // Handle failure case, if needed
                checkAndRequestPermissions();
            }
        });
    }

    public void checkAndRequestPermissions() {
        boolean hasNotificationPermission = true;
        boolean hasActivityPermission = true;
        boolean hasLocationPermission = true;

        // Check for POST_NOTIFICATIONS permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = ContextCompat.checkSelfPermission(Activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            if (!hasNotificationPermission) {
                AskForPermission(Manifest.permission.POST_NOTIFICATIONS, Activity.getString(R.string.notification_permission_header));
            }
        }

        // Check for ACTIVITY_RECOGNITION only if m05_Activites is enabled and "pa-enabled" is set
        if (SettingsManager.val("pa-enabled") == 1 && ModulePermissions.needActivityRecognition()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                hasActivityPermission = ContextCompat.checkSelfPermission(Activity, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
                if (!hasActivityPermission) {
                    AskForPermission(Manifest.permission.ACTIVITY_RECOGNITION, Activity.getString(R.string.physical_permission_header));
                }
            }
        }

        // Check for ACCESS_FINE_LOCATION only if m04_Locations is enabled and "gps-enabled" is set
        if (SettingsManager.val("gps-enabled") == 1 && ModulePermissions.needLocation() && Build.VERSION.SDK_INT >= 23) {
            hasLocationPermission = ContextCompat.checkSelfPermission(Activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            if (!hasLocationPermission) {
                AskForPermission(Manifest.permission.ACCESS_FINE_LOCATION, Activity.getString(R.string.location_permission_header));
            }
        }

        // If any permission is not granted, show the permission dialog
        if (!hasNotificationPermission || !hasActivityPermission || !hasLocationPermission) {

            String description = "Please grant";
            int permissionCount = 0;

            if (!hasNotificationPermission){

               description = description.concat(" Notifications Permission ");
               permissionCount = permissionCount + 1;

            }
            if (!hasActivityPermission){
                if (permissionCount == 1) {
                    description = description.concat(",Physical Activity Permission ");

                }
                permissionCount = permissionCount + 1;

            }
            if (!hasLocationPermission) {
                if (permissionCount > 1) {
                    description = description.concat("and");
                }else {
                    description = description.concat(",");
                }
                description = description.concat(" Precise Location Permission");

            }

            description = description.concat(" in the app settings.");

            showPermissionDialog(description);
        } else {
            dismissPermissionDialog();
        }
    }

    public void showPermissionDialog(String description) {

        if (Activity.isFinishing() || Activity.isDestroyed()) {
            return;
        }

        if (permissionDialog == null || !permissionDialog.isShowing()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(Activity);
            builder.setTitle("Permission(s) Required");
            builder.setMessage(description);
            builder.setCancelable(true);
            builder.setPositiveButton("Open Settings", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    openAppSettings();
                }
            });

            permissionDialog = builder.create();
            permissionDialog.show();
        }
    }

    private void dismissPermissionDialog() {
        if (permissionDialog != null && permissionDialog.isShowing()) {
            permissionDialog.dismiss();
        }
    }

    public boolean IsPermissionDialogShowing() {
        if (permissionDialog != null && permissionDialog.isShowing()) {
            return true;
        }
        return false;
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", Activity.getPackageName(), null);
        intent.setData(uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity.startActivity(intent);
    }

    /**
     * Returns true if any permission required by enabled modules is not yet granted.
     * Only checks permissions that are needed per ModuleController (and Firestore for pa/gps).
     * Call only when SettingsManager has already been loaded (e.g. from permission flow).
     */
    public boolean CheckIsAnyPermissionLeftFromBeingAllowed() {
        boolean IsLeft = false;

        if (ModulePermissions.needUsageAccess() && !UsagePermissionActivity.appHasUsageAccess(Activity)) {
            IsLeft = true;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(Activity, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                IsLeft = true;
            }
        }

        if (ModulePermissions.needNotificationListener()) {
            ContentResolver contentResolver = Activity.getContentResolver();
            String enabledNotificationListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners");
            String packageName = Activity.getPackageName();
            if (enabledNotificationListeners == null || !enabledNotificationListeners.contains(packageName)) {
                IsLeft = true;
            }
        }

        if (ModulePermissions.needAccessibility() && !isAccessServiceEnabled(Activity, InteractionsUpdater.class)) {
            IsLeft = true;
        }

        if (SettingsManager.val("pa-enabled") == 1 && ModulePermissions.needActivityRecognition()) {
            if (ContextCompat.checkSelfPermission(Activity, Manifest.permission.ACTIVITY_RECOGNITION)
                    != PackageManager.PERMISSION_GRANTED) {
                IsLeft = true;
            }
        }

        if (SettingsManager.val("gps-enabled") == 1 && ModulePermissions.needLocation() && Build.VERSION.SDK_INT >= 23) {
            if (ContextCompat.checkSelfPermission(Activity, android.Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                IsLeft = true;
            }
        }

        return IsLeft;
    }

    public static boolean checkLocationPermission(Context context) {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public void ShowDialog(String description, String header,String btnText,boolean IsFromAppRunningActivity){

        if (btnText == null || btnText.isEmpty()){
            btnText = Activity.getString(android.R.string.yes);
        }

        String finalBtnText = btnText;

        if (alertDialog == null || !alertDialog.isShowing()){
            alertDialog = new AlertDialog.Builder(Activity)
                    .setTitle(header)
                    .setMessage(description)
                    .setNegativeButton(btnText, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                            if (finalBtnText.equals(Activity.getString(android.R.string.yes))){
                                dialog.cancel();
                            }else {
                                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package", Activity.getPackageName(), null);
                                intent.setData(uri);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // Open in a new task
                                Activity.startActivity(intent);
                                dialog.cancel();
                            }

                        }
                    }).show();
            // Specifying a listener allows you to take an action before dismissing the dialog.
            // The dialog is automatically dismissed when a dialog button is clicked.
//                .setPositiveButton("Open Setting", new DialogInterface.OnClickListener() {
//                    public void onClick(DialogInterface dialog, int which) {
//                        // Continue with delete operation
//                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
//                        Uri uri = Uri.fromParts("package", Activity.getPackageName(), null);
//                        intent.setData(uri);
//                        Activity.startActivity(intent);
//                    }
//                })
            if (IsFromAppRunningActivity){
                alertDialog.setCancelable(true);

            }
        }

    }

    public static boolean isNotificationServiceEnabled(Context context) {
        String pkgName = context.getPackageName();
        final String flat = Settings.Secure.getString(context.getContentResolver(),
                "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            final String[] names = flat.split(":");
            for (String name : names) {
                final ComponentName componentName = ComponentName.unflattenFromString(name);
                if (componentName != null) {
                    if (TextUtils.equals(pkgName, componentName.getPackageName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isAccessibilityServiceEnabled(Context context, Class<? extends AccessibilityService> service) {
        String serviceId = context.getPackageName() + "/" + service.getCanonicalName();
        String enabledServices = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices != null) {
            TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
            splitter.setString(enabledServices);
            while (splitter.hasNext()) {
                String componentName = splitter.next();
                if (componentName.equalsIgnoreCase(serviceId)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isAccessServiceEnabled(Context context, Class accessibilityServiceClass)
    {
        String prefString = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

        return prefString!= null && prefString.contains(context.getPackageName() + "/" + accessibilityServiceClass.getName());
    }

    public static boolean isAccessibilitySettingsOn(Context mContext,Activity activity) {
        int accessibilityEnabled = 0;
        final String service = activity.getPackageName() + "/" + InteractionsUpdater.class.getCanonicalName();
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    mContext.getApplicationContext().getContentResolver(),
                    android.provider.Settings.Secure.ACCESSIBILITY_ENABLED);
            Log.v(TAG, "accessibilityEnabled = " + accessibilityEnabled);
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "Error finding setting, default accessibility to not found: "
                    + e.getMessage());
        }
        TextUtils.SimpleStringSplitter mStringColonSplitter = new TextUtils.SimpleStringSplitter(':');

        if (accessibilityEnabled == 1) {
            Log.v(TAG, "***ACCESSIBILITY IS ENABLED*** -----------------");
            String settingValue = Settings.Secure.getString(
                    mContext.getApplicationContext().getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue);
                while (mStringColonSplitter.hasNext()) {
                    String accessibilityService = mStringColonSplitter.next();

                    Log.v(TAG, "-------------- > accessibilityService :: " + accessibilityService + " " + service);
                    if (accessibilityService.equalsIgnoreCase(service)) {
                        Log.v(TAG, "We've found the correct setting - accessibility is switched on!");
                        return true;
                    }
                }
            }
        } else {
            Log.v(TAG, "***ACCESSIBILITY IS DISABLED***");
        }

        return false;
    }

}
