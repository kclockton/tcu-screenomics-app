package edu.stanford.communication.screenomics.PermissionScreens;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.ViewPager;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import edu.stanford.communication.screenomics.Activity.AppRunningActivity;
import edu.stanford.communication.screenomics.screenshots.CaptureUploadStarter;
import edu.stanford.communication.screenomics.FirebaseSettingsObserver;
import edu.stanford.communication.screenomics.screenshots.CaptureUploadService;
import edu.stanford.communication.screenomics.R;
import edu.stanford.communication.screenomics.FirebaseSettings.SettingsManager;
import edu.stanford.communication.screenomics.TextBasedData.EventOperationManager;
import edu.stanford.communication.screenomics.TextBasedData.HashMapPool;
import edu.stanford.communication.screenomics.interactions.InteractionsUpdater;
import edu.stanford.communication.screenomics.modulemanager.ModuleController;
import edu.stanford.communication.screenomics.modulemanager.ModulePermissions;
import edu.stanford.communication.screenomics.modulemanager.ModuleCharacteristics;
import edu.stanford.communication.screenomics.network.NetworkUpdater;

public class PermissionParentActivity extends AppCompatActivity {

    /** True while this activity is in the foreground (user going through permission screens). */
    private static volatile boolean sPermissionFlowActive = false;

    /** True after onPause() has run at least once (user left the permission flow at least once). */
    private static volatile boolean sPermissionFlowHasPausedOnce = false;

    /**
     * True only after the user has completed the permission flow and reached the main interface.
     * Until then, automatic media projection popup (service/alarm/restart) is suppressed.
     */
    private static volatile boolean sPermissionFlowCompleted = false;

    public static boolean isPermissionFlowActive() {
        return sPermissionFlowActive;
    }

    /**
     * True during the very first continuous stay in the permission flow (before the user has left the activity once).
     */
    public static boolean isInInitialPermissionSetup() {
        return sPermissionFlowActive && !sPermissionFlowHasPausedOnce;
    }

    /**
     * True only after the user has passed all permission screens and reached the main interface.
     * Used to allow the media projection popup only after setup is done (e.g. when system pauses the app).
     */
    public static boolean hasUserCompletedPermissionFlow() {
        return sPermissionFlowCompleted;
    }

    /** Call when user has completed the permission flow and we are navigating to main. */
    public static void markPermissionFlowCompleted() {
        sPermissionFlowCompleted = true;
    }

    /** Call only when starting permission flow from login; resets so popup is blocked until they complete. */
    public static void resetPermissionFlowCompletedForNewSession() {
        sPermissionFlowCompleted = false;
    }

    private boolean IsTheAppRunFirstTime = true;
    private ViewPager Viewpager;
    private ImageView BackwardImg;
    private ImageView ForwardImg;
    private ViewPagerAdapter viewPagerAdapter;

    private TextView CounterTxt;

    private int TotalPages = 0;

    private static final int REQUEST_CODE = 90;

    BroadcastReceiver broadcastReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sPermissionFlowActive = true;
        setContentView(R.layout.permission_parent);

        IsTheAppRunFirstTime = true;

        CounterTxt = (TextView) findViewById(R.id.CounterTxt);

        Viewpager = (ViewPager) findViewById(R.id.Viewpager);
        BackwardImg = (ImageView) findViewById(R.id.BackwardImg);
        ForwardImg = (ImageView) findViewById(R.id.ForwardImg);

        SetStatusBarColor();

        viewPagerAdapter = new ViewPagerAdapter(getSupportFragmentManager());

        if (isMyServiceRunning(CaptureUploadService.class)){
            startActivity(new Intent(PermissionParentActivity.this, AppRunningActivity.class));
        }else {
            AddViewPageAccordingToPermission();
        }

        LogIsAppOpenedByNotificationOrManually();
    }

    private void LogIsAppOpenedByNotificationOrManually() {
        if (Objects.equals(getIntent().getStringExtra(getString(R.string.open_by_notification)), getString(R.string.open_by_notification))) {
            edu.stanford.communication.screenomics.LogEvents.AlarmManagerNotificationEventLogger.log(PermissionParentActivity.this, "opened");
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
                    FrameLayout.LayoutParams.MATCH_PARENT, getStatusBarHeight() + 20));
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

    public void ScrollToNextPage(){
//        Viewpager.arrowScroll(View.FOCUS_RIGHT);
        Viewpager.setCurrentItem(Viewpager.getCurrentItem() + 1,true);
    }

    private void AddViewPageAccordingToPermission(){

        ProgressDialog dialog = new ProgressDialog(PermissionParentActivity.this);
        dialog.setMessage("Loading Settings. Please Wait....");
        dialog.setCancelable(false);
        dialog.show();

        if (!SettingsManager.exists()) {
            SettingsManager.create(new FirebaseSettingsObserver() {
                @Override
                public void onSettingsChanged(List<String> changedSettings) {

                }
            });
        }

        SettingsManager.get().load(this, new SettingsManager.DatabaseListener() {
            @Override
            public void onSuccess() {

                // Settings loaded success fully
            }

            @Override
            public void onFailure() {
                // Failed to load the settings? Too bad!
            }
        });

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Only show permission screens for enabled modules (and Firestore when applicable).
                if (ModulePermissions.needUsageAccess()
                        && !UsagePermissionActivity.appHasUsageAccess(PermissionParentActivity.this)) {
                    viewPagerAdapter.add(new PreviewFrag(getString(R.string.usage_permission_header), getString(R.string.usage_permission_description)));
                    TotalPages = TotalPages + 1;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(PermissionParentActivity.this, Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                        viewPagerAdapter.add(new PreviewFrag(getString(R.string.notification_permission_header), getString(R.string.notification_permission_description)));
                        TotalPages = TotalPages + 1;
                    }
                }

                if (ModulePermissions.needNotificationListener()) {
                    ContentResolver contentResolver = getContentResolver();
                    String enabledNotificationListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners");
                    String packageName = getPackageName();
                    if (enabledNotificationListeners == null || !enabledNotificationListeners.contains(packageName)) {
                        viewPagerAdapter.add(new PreviewFrag(getString(R.string.read_notification_permission_header), getString(R.string.read_notification_permission_description)));
                        TotalPages = TotalPages + 1;
                    }
                }

                if (ModulePermissions.needAccessibility()
                        && !PermissionChecker.isAccessServiceEnabled(PermissionParentActivity.this, InteractionsUpdater.class)) {
                    viewPagerAdapter.add(new PreviewFrag(getString(R.string.accessibility_permission_header), getString(R.string.accessibility_permission_description)));
                    TotalPages = TotalPages + 1;
                }

                if (SettingsManager.val("pa-enabled") == 1 && ModulePermissions.needActivityRecognition()) {
                    if (ContextCompat.checkSelfPermission(PermissionParentActivity.this, Manifest.permission.ACTIVITY_RECOGNITION)
                            != PackageManager.PERMISSION_GRANTED) {
                        viewPagerAdapter.add(new PreviewFrag(getString(R.string.physical_permission_header), getString(R.string.physical_permission_description)));
                        TotalPages = TotalPages + 1;
                    }
                }

                if (SettingsManager.val("gps-enabled") == 1 && ModulePermissions.needLocation()) {
                    if (ContextCompat.checkSelfPermission(PermissionParentActivity.this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                            != PackageManager.PERMISSION_GRANTED) {
                        viewPagerAdapter.add(new PreviewFrag(getString(R.string.location_permission_header), getString(R.string.location_access_permission_description)));
                        TotalPages = TotalPages + 1;
                    }
                }

                if (ModulePermissions.needMediaProjection() && !isMyServiceRunning(CaptureUploadService.class)) {
                    viewPagerAdapter.add(new PreviewFrag(getString(R.string.media_projection_permission_header), getString(R.string.media_projection_permission_description)));
                    TotalPages = TotalPages + 1;
                }

                // No permission screens needed — required permissions already met; go to main.
                if (TotalPages == 0) {
                    if (dialog.isShowing()) {
                        dialog.dismiss();
                    }
                    markPermissionFlowCompleted();
                    if (ModulePermissions.needMediaProjection()) {
                        startActivity(new Intent(PermissionParentActivity.this, CaptureUploadStarter.class));
                    } else {
                        ContextCompat.startForegroundService(
                                PermissionParentActivity.this,
                                new Intent(PermissionParentActivity.this, CaptureUploadService.class));
                        startActivity(new Intent(PermissionParentActivity.this, AppRunningActivity.class));
                    }
                    finish();
                    return;
                }

                CounterTxt.setText(String.valueOf(Viewpager.getCurrentItem() + 1) + "/" + TotalPages);

                Viewpager.setAdapter(viewPagerAdapter);

                broadcastReceiver = new NetworkUpdater();

                registerReceiver(broadcastReceiver,new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

                if (Viewpager.getCurrentItem() == 0) {
                    BackwardImg.setVisibility(View.INVISIBLE);
                } else {
                    BackwardImg.setVisibility(View.VISIBLE);
                }

                Viewpager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                    @Override
                    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

                    }

                    @Override
                    public void onPageSelected(int position) {

                        androidx.fragment.app.Fragment fragment = viewPagerAdapter.getItem(position);

                        if (fragment instanceof PreviewFrag){

                            ((PreviewFrag)fragment).CheckForPermissionAndSetButtonToFade(true);
                        }

                        CounterTxt.setText(String.valueOf(position + 1) +"/"+ TotalPages);

                        int currentItem = Viewpager.getCurrentItem();

                        if (currentItem == (Viewpager.getAdapter().getCount()-1)) {
                            ForwardImg.setVisibility(View.INVISIBLE);
                        } else {
                            ForwardImg.setVisibility(View.VISIBLE);
                        }

                        if (currentItem == 0) {
                            BackwardImg.setVisibility(View.INVISIBLE);
                        } else {
                            BackwardImg.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onPageScrollStateChanged(int state) {

                    }

                });

                BackwardImg.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                        Viewpager.setCurrentItem(Viewpager.getCurrentItem() -1 );
                    }
                });

                ForwardImg.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Viewpager.setCurrentItem(Viewpager.getCurrentItem() + 1 );

                    }
                });

                if (dialog.isShowing()){
                    dialog.dismiss();
                }

            }
        }, 2000);

    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {

        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();

//        LogIsAppOpenedByNotificationOrManually();
        if (!IsTheAppRunFirstTime){
            if (PreviewFrag.WhichScreenIsSelected != null) {
                if (PreviewFrag.WhichScreenIsSelected.equals(getString(R.string.usage_permission_header))) {
                    if (UsagePermissionActivity.appHasUsageAccess(PermissionParentActivity.this)) {

                        ScrollToNextPage();

                    }
                } else if (PreviewFrag.WhichScreenIsSelected.equals(getString(R.string.read_notification_permission_header))) {

//                ContentResolver contentResolver = getContentResolver();
//                String enabledNotificationListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners");
//                String packageName = getPackageName();

                    if (PermissionChecker.isNotificationServiceEnabled(PermissionParentActivity.this)){
                        ScrollToNextPage();
                    }

//                    if (enabledNotificationListeners != null || enabledNotificationListeners.contains(packageName)) {
//                    ScrollToNextPage();
//                }

                }
                else if (PreviewFrag.WhichScreenIsSelected.equals(getString(R.string.accessibility_permission_header))) {
                    int accessEnabled = 0;
                    try {
                        accessEnabled = Settings.Secure.getInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);

                    } catch (Settings.SettingNotFoundException e) {
                        e.printStackTrace();
                    }

                    if(accessEnabled != 0){

                        ScrollToNextPage();
                    }

                }

            }
        }

        IsTheAppRunFirstTime = false;
        sPermissionFlowActive = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        sPermissionFlowActive = false;
        sPermissionFlowHasPausedOnce = true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CODE) return;

        // If starting capture succeeded, go to the "App Running" screen.
        if (resultCode == Activity.RESULT_OK) {
            markPermissionFlowCompleted();
            finish();
        }

        // Otherwise, notify the user of the issue.
        else {
            Toast.makeText(this, R.string.cp_toast_nostart, Toast.LENGTH_SHORT).show();
        }
    }
}