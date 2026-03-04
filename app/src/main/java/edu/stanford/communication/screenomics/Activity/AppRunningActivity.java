package edu.stanford.communication.screenomics.Activity;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;

import android.graphics.Color;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.util.concurrent.ServiceManager;

import java.util.List;

import edu.stanford.communication.screenomics.Alarm.SetAlarm;
import edu.stanford.communication.screenomics.AppUtils.InAppUpdate;
import edu.stanford.communication.screenomics.DatabaseHelper.InterCommunicationPreference;
import edu.stanford.communication.screenomics.FirebaseSettingsObserver;
import edu.stanford.communication.screenomics.PermissionScreens.PermissionChecker;
import edu.stanford.communication.screenomics.PermissionScreens.PermissionParentActivity;
import edu.stanford.communication.screenomics.screenshots.CaptureUploadService;
import edu.stanford.communication.screenomics.R;
import edu.stanford.communication.screenomics.FirebaseSettings.SettingsManager;
import edu.stanford.communication.screenomics.Services.ScreenMonitorService;
import edu.stanford.communication.screenomics.Utils;
import edu.stanford.communication.screenomics.modulemanager.ModuleController;
import edu.stanford.communication.screenomics.modulemanager.ModulePermissions;

public class AppRunningActivity extends AppCompatActivity {

    PermissionChecker checker;
    public static int ResultCode;
    public static Intent intent1;
    private SwitchCompat ServiceTurnOnOff;
    private TextView arTitle;
    private TextView arDescription;
    private static final int REQUEST_CODE = 100;
    private MediaProjectionManager mediaProjectionManager;
    private static MediaProjection mediaProjection;

    SetAlarm alarm2;

    private InAppUpdate inAppUpdate;

    private boolean IsUserClickedTurnOnOff = false;

    private InterCommunicationPreference interCommunicationPrefrence;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_running);

        ServiceTurnOnOff = findViewById(R.id.ServiceTurnOnOff);
        arTitle = findViewById(R.id.ar_title);
        arDescription = findViewById(R.id.ar_description);

        // Hide pause/resume switch when screenshots module is off (it only controls screen capture).
        if (!ModulePermissions.needMediaProjection()) {
            ServiceTurnOnOff.setVisibility(View.GONE);
            ConstraintLayout root = findViewById(R.id.ar_root);
            ConstraintSet set = new ConstraintSet();
            set.clone(root);
            set.connect(R.id.ar_title, ConstraintSet.TOP, R.id.ar_logo, ConstraintSet.BOTTOM);
            set.applyTo(root);
        }

        SetStatusBarColor();

        alarm2 = new SetAlarm();
        alarm2.SetAlarmFor7Am(getApplicationContext());
        alarm2.SetAlarmFor7Pm(getApplicationContext());
        interCommunicationPrefrence = new InterCommunicationPreference(AppRunningActivity.this);

        DecideIsServiceRunningOrNot();

        inAppUpdate = new InAppUpdate(AppRunningActivity.this,getWindow().getDecorView().getRootView());

        try {
            inAppUpdate.checkForAppUpdate();
        }catch (Exception ignored){}
        IsUserClickedTurnOnOff = true;

        checker = new PermissionChecker(AppRunningActivity.this);

        interCommunicationPrefrence.WhichActivityCalledStartService(1);

        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isMyServiceRunning(CaptureUploadService.class,AppRunningActivity.this)){
                    setResult(Activity.RESULT_CANCELED);

                    finishAffinity();
                }
            }
        });

        ShowBatteryOptimizationDialog();

        startScreenMonitorService();

//        Intent intent = getIntent();
//        boolean str = intent.getBooleanExtra("start_service",false);
//
//        if (str){
//            Toast.makeText(this, "true", Toast.LENGTH_SHORT).show();
//            ServiceTurnOnOff.setChecked(true);
//        }

        ServiceTurnOnOff.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if (!SettingsManager.exists()) {
                    SettingsManager.create(new FirebaseSettingsObserver() {
                        @Override
                        public void onSettingsChanged(List<String> changedSettings) {

                        }
                    });
                }

                SettingsManager.get().load(AppRunningActivity.this, new SettingsManager.DatabaseListener() {
                    @Override
                    public void onSuccess() {
                        // Settings loaded success fully
                    }

                    @Override
                    public void onFailure() {
                        // Failed to load the settings? Too bad!
                    }
                });

                Intent serv_intent = new Intent(AppRunningActivity.this, CaptureUploadService.class);

                if (IsUserClickedTurnOnOff){
                    if (isChecked){

                        PermissionChecker permissionChecker = new PermissionChecker(AppRunningActivity.this);
                        if (permissionChecker.CheckIsAnyPermissionLeftFromBeingAllowed()){
                            startActivity(new Intent(AppRunningActivity.this, PermissionParentActivity.class));
                            finish();
                        } else if (!PermissionParentActivity.hasUserCompletedPermissionFlow()) {
                            // Do not show media projection dialog until user has completed permission flow.
                            startActivity(new Intent(AppRunningActivity.this, PermissionParentActivity.class));
                            finish();
                        } else {

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

                                startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);

                            } else {
                                mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

                                startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);

                            }
                        }

                    }else {

                        AlertDialog.Builder builder = new AlertDialog.Builder(AppRunningActivity.this);
                        builder.setMessage("Do you want to turn off the application service?");
                        builder.setTitle("Confirmation");
                        builder.setCancelable(false);
                        builder.setPositiveButton("Turn Off", (DialogInterface.OnClickListener) (dialog, which) -> {

//                            alarm2.SetAlarmFor30MinToAskUserAgainToStartService(AppRunningActivity.this);
                            interCommunicationPrefrence.putPauseCause("user");
                            stopService(serv_intent);
                            ServiceIsOffLabel();

                            dialog.cancel();

                        });

                        builder.setNegativeButton("No", (DialogInterface.OnClickListener) (dialog, which) -> {
                            DecideIsServiceRunningOrNot();

                            dialog.cancel();
                        });

                        AlertDialog alertDialog = builder.create();
                        alertDialog.show();
                    }
                }
            }
        });

    }

    private void startScreenMonitorService() {

        if (!isMyServiceRunning(ScreenMonitorService.class,AppRunningActivity.this)){
            Intent serviceIntent = new Intent(this, ScreenMonitorService.class);

            ContextCompat.startForegroundService(this, serviceIntent);
        }

    }

    private void ShowBatteryOptimizationDialog(){

        int value = interCommunicationPrefrence.GetShowBatteryOptimizationDialog();

            if (value % 5 != 0){
                interCommunicationPrefrence.PutShowBatteryOptimizationDialog(interCommunicationPrefrence.GetShowBatteryOptimizationDialog() + 1);
                return;
            }else {
                interCommunicationPrefrence.PutShowBatteryOptimizationDialog(interCommunicationPrefrence.GetShowBatteryOptimizationDialog() + 1);
            }

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        String packageName = getPackageName();
        boolean isIgnoring = powerManager.isIgnoringBatteryOptimizations(packageName);

        if (!isIgnoring) {

            Log.i("AppRunningActivity","ignoring run");

            AlertDialog.Builder builder = new AlertDialog.Builder(AppRunningActivity.this);
            builder.setMessage("To ensure you receive important updates and notifications, please add our app to your whitelist. This will help us provide you with a better experience.\n" +
                    "Would you like to whitelist the Screenomics app now?");
            builder.setTitle("Whitelist Screenomics App");
            builder.setCancelable(false);
            builder.setPositiveButton("Yes", (DialogInterface.OnClickListener) (dialog, which) -> {

                try {
                    Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                    dialog.cancel();
                }catch (Exception e){
                    PowerManager powerManager1 = (PowerManager) getSystemService(Context.POWER_SERVICE);
                    if (powerManager1 != null && !powerManager1.isIgnoringBatteryOptimizations(getPackageName())) {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    }
                }

            });

            builder.setNegativeButton("No", (DialogInterface.OnClickListener) (dialog, which) -> {

                dialog.cancel();
            });

            AlertDialog alertDialog = builder.create();
            alertDialog.show();
            // The app is ignoring battery optimizations.
        }else {
            Log.i("AppRunningActivity","else run");

        }

    }

    private void SetStatusBarColor(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }

        WindowInsetsController controller = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            controller = getWindow().getInsetsController();

            if (controller != null) {
                controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());

                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

            }

            // Create a fake status bar (Scrim View) to add color
            View statusBarScrim = new View(this);
            statusBarScrim.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, getStatusBarHeight()+ 20));
            statusBarScrim.setBackgroundColor(Color.parseColor("#B71C1C")); // Change to your desired color

            // Add the scrim to your root layout
            FrameLayout rootLayout = findViewById(android.R.id.content);
            rootLayout.addView(statusBarScrim);
        }
    }

    private int getStatusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resourceId > 0 ? getResources().getDimensionPixelSize(resourceId) : 0;
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();

    }

    private void IsAnyPermissionLeftAskForIt(){
        if (isMyServiceRunning(CaptureUploadService.class,AppRunningActivity.this)){

            checker.checkPermissions();
//            checker.AskForLeftPermission();

        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        IsAnyPermissionLeftAskForIt();
        DecideIsServiceRunningOrNot();
        IsUserClickedTurnOnOff = true;
        interCommunicationPrefrence.WhichActivityCalledStartService(1);
        inAppUpdate.onResume();

    }

    private void DecideIsServiceRunningOrNot(){
        IsUserClickedTurnOnOff = false;
//        if (isMyServiceRunning(CaptureUploadService.class,AppRunningActivity.this)){
//            ServiceTurnOnOff.setChecked(true);
//            IsUserClickedTurnOnOff = true;
//            arTitle.setText(getText(R.string.ar_prompt_title));
//            arDescription.setText(getText(R.string.ar_prompt_description));
//        }else {
//            ServiceTurnOnOff.setChecked(false);
//            IsUserClickedTurnOnOff = true;
//            arTitle.setText(R.string.service_is_not_running);
//            arDescription.setText(R.string.app_is_not_running_please_turn_on_the_above_button_to_start_the_app);
//
//        }

        if (!isMyServiceRunning(CaptureUploadService.class,AppRunningActivity.this) && !isMyServiceRunning(ScreenMonitorService.class,AppRunningActivity.this)){
            if (ServiceTurnOnOff.getVisibility() == View.VISIBLE) ServiceTurnOnOff.setChecked(false);
            IsUserClickedTurnOnOff = true;
            arTitle.setText(getText(R.string.service_is_not_running));
            arDescription.setText("App is not running. To resume data collection, please reopen the app.");
        }
        else if (!isMyServiceRunning(CaptureUploadService.class, AppRunningActivity.this) && isMyServiceRunning(ScreenMonitorService.class, AppRunningActivity.this)) {
            if (ServiceTurnOnOff.getVisibility() == View.VISIBLE) ServiceTurnOnOff.setChecked(false);
            IsUserClickedTurnOnOff = true;
            arTitle.setText(getText(R.string.ar_prompt_title));
            arDescription.setText(ModulePermissions.needMediaProjection()
                    ? "Screen capture is paused. Please reopen the app to resume."
                    : getText(R.string.ar_description_paused_no_screenshots));
        }
        else if (isMyServiceRunning(CaptureUploadService.class,AppRunningActivity.this) && isMyServiceRunning(ScreenMonitorService.class,AppRunningActivity.this)){
            if (ServiceTurnOnOff.getVisibility() == View.VISIBLE) ServiceTurnOnOff.setChecked(true);
            IsUserClickedTurnOnOff = true;
            arTitle.setText(getText(R.string.ar_prompt_title));
            arDescription.setText(ModulePermissions.needMediaProjection()
                    ? getText(R.string.ar_prompt_description)
                    : getText(R.string.ar_description_no_screenshots));
        }

    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

//        if (!checker.IsPermissionDialogShowing()){
//            if (isMyServiceRunning(CaptureUploadService.class,AppRunningActivity.this)){
//                checker.checkPermissions();
//            }
//        }

    }

    private void ServiceIsOffLabel(){
        IsUserClickedTurnOnOff = false;
        if (ServiceTurnOnOff.getVisibility() == View.VISIBLE) ServiceTurnOnOff.setChecked(false);
        IsUserClickedTurnOnOff = true;

        arTitle.setText(R.string.service_is_not_running);

        if (!isMyServiceRunning(CaptureUploadService.class,AppRunningActivity.this) && !isMyServiceRunning(ScreenMonitorService.class,AppRunningActivity.this)){
            arDescription.setText("App is not running. To resume data collection, please reopen the app.");
        }
        else if (!isMyServiceRunning(CaptureUploadService.class, AppRunningActivity.this) && isMyServiceRunning(ScreenMonitorService.class, AppRunningActivity.this)) {
            arDescription.setText(ModulePermissions.needMediaProjection()
                    ? "Screen capture is paused. Please reopen the app to resume."
                    : getText(R.string.ar_description_paused_no_screenshots));
        }

        Intent serv_intent = new Intent(AppRunningActivity.this, CaptureUploadService.class);
        stopService(serv_intent);
    }

    private void ServiceIsOnLabel(){
        IsUserClickedTurnOnOff = false;
        ServiceTurnOnOff.setChecked(true);
        arTitle.setText(getText(R.string.ar_prompt_title));
        arDescription.setText(getText(R.string.ar_prompt_description));

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        Utils.createOptionsMenu(menu);

        return super.onCreateOptionsMenu(menu);

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        Utils.optionsItemSelected(this, item);
        return super.onOptionsItemSelected(item);
    }

    private static boolean isMyServiceRunning(Class<?> serviceClass, Context context) {

        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        // Make sure this is the result of starting media projection.

        super.onActivityResult(requestCode, resultCode, intent);

        inAppUpdate.onActivityResult(requestCode, resultCode);

        if (requestCode == REQUEST_CODE) {

            // If the result is not OK, the user probably didn't give us permission. Abort.
            if (resultCode != Activity.RESULT_OK) {
                setResult(resultCode);
                DecideIsServiceRunningOrNot();
//                alarm2.SetAlarmFor30MinToAskUserAgainToStartService(AppRunningActivity.this);
                Toast.makeText(this, getText(R.string.cp_toast_nostart), Toast.LENGTH_SHORT).show();
                return;
            }else{
                startScreenMonitorService();
            }

            interCommunicationPrefrence.WhichActivityCalledStartService(1);

            ResultCode = resultCode;
            intent1 = intent;

            // Start the CaptureUploadService.
            Intent serv_intent = new Intent(AppRunningActivity.this, CaptureUploadService.class);

//            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, intent);

            ContextCompat.startForegroundService(AppRunningActivity.this, serv_intent);

//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                startForegroundService(serv_intent);
//
//            } else {
//
//                startService(serv_intent);
//
//            }

            alarm2.CancelAlarm(AppRunningActivity.this,5);

            DecideIsServiceRunningOrNot();
            // Close this activity.
            setResult(Activity.RESULT_OK);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        inAppUpdate.onDestroy();
    }

    public static MediaProjection getMediaProjection()
    {
        return mediaProjection;
    }

    public static Intent getIntents()
    {
        return intent1;
    }

    public static int getResultCode()
    {
        return ResultCode;
    }
}
