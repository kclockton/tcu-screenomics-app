package edu.stanford.communication.screenomics.PermissionScreens;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.List;

import edu.stanford.communication.screenomics.FirebaseSettingsObserver;
import edu.stanford.communication.screenomics.FirebaseSettings.SettingsManager;
import edu.stanford.communication.screenomics.R;
import edu.stanford.communication.screenomics.Utils;
import edu.stanford.communication.screenomics.network.NetworkUpdater;
import edu.stanford.communication.screenomics.screenshots.CaptureUploadStarter;
import edu.stanford.communication.screenomics.screenshots.CaptureUploadService;

/**
 * Activity that asks the user to press a button to start screen capture.
 * Handles permission flow and media projection request.
 */
public class CapturePermissionActivity extends AppCompatActivity {

    private static final String TAG = CapturePermissionActivity.class.getName();
    private static final int REQUEST_CODE = 90;
    private static final int GPS_PERM_REQUEST_CODE = 234;

    private Button start;
    private android.content.BroadcastReceiver broadcastReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture_permission);
        SetStatusBarColor();

        start = (Button) findViewById(R.id.cp_start);
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(CapturePermissionActivity.this, Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                        String[] permissions = new String[0];
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            permissions = new String[]{Manifest.permission.POST_NOTIFICATIONS};
                            ActivityCompat.requestPermissions(CapturePermissionActivity.this, permissions, 0);
                        }
                    } else {
                        CheckForNotificationAccess();
                    }
                } else {
                    CheckForNotificationAccess();
                }
            }
        });

        broadcastReceiver = new NetworkUpdater();
        registerReceiver(broadcastReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        start.setText(R.string.cp_button_loading);
        start.setEnabled(false);

        if (!SettingsManager.exists()) {
            SettingsManager.create(new FirebaseSettingsObserver() {
                @Override
                public void onSettingsChanged(List<String> changedSettings) {}
            });
        }
        SettingsManager.get().load(this, new SettingsManager.DatabaseListener() {
            @Override
            public void onSuccess() {
                start.setText(R.string.cp_button_start);
                loadInstructionImage();
                start.setEnabled(true);
            }

            @Override
            public void onFailure() {
                start.setText(R.string.cp_button_start);
                loadInstructionImage();
                start.setEnabled(true);
            }
        });
    }

    private void SetStatusBarColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
            View statusBarScrim = new View(this);
            statusBarScrim.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, getStatusBarHeight() + 20));
            statusBarScrim.setBackgroundColor(Color.parseColor("#B71C1C"));
            FrameLayout rootLayout = findViewById(android.R.id.content);
            rootLayout.addView(statusBarScrim);
        }
    }

    private int getStatusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resourceId > 0 ? getResources().getDimensionPixelSize(resourceId) : 0;
    }

    private void checkForGPS() {
        if (SettingsManager.val("gps-enabled") == 1 && Build.VERSION.SDK_INT >= 23) {
            String[] permissions = {android.Manifest.permission.ACCESS_FINE_LOCATION};
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, permissions, GPS_PERM_REQUEST_CODE);
            } else {
                beginCapture();
            }
        } else {
            beginCapture();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void CheckForNotificationAccess() {
        int accessEnabled = 0;
        try {
            accessEnabled = Settings.Secure.getInt(this.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }
        ContentResolver contentResolver = this.getContentResolver();
        String enabledNotificationListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners");
        String packageName = this.getPackageName();

        if (enabledNotificationListeners == null || !enabledNotificationListeners.contains(packageName)) {
            Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            startActivity(intent);
            Toast.makeText(this, "Please turn on notification access to start the screenomics", Toast.LENGTH_SHORT).show();
        } else if (accessEnabled == 0) {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else if (ContextCompat.checkSelfPermission(CapturePermissionActivity.this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
            String[] permissions = new String[0];
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                permissions = new String[]{Manifest.permission.ACTIVITY_RECOGNITION};
                ActivityCompat.requestPermissions(this, permissions, 0);
            } else {
                beginCapture();
            }
        } else if (ContextCompat.checkSelfPermission(CapturePermissionActivity.this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            checkForGPS();
        } else {
            beginCapture();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == GPS_PERM_REQUEST_CODE) {
            if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
                Toast.makeText(this, "Allow Location Permission", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void beginCapture() {
        Intent intent = new Intent(this, CaptureUploadStarter.class);
        intent.putExtra(CaptureUploadStarter.EXTRA_FROM_PERMISSION_FLOW, true);
        intent.putExtra(CaptureUploadService.EXTRA_STARTUP_INSTIGATOR, "user");
        startActivityForResult(intent, REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CODE) return;
        if (resultCode == Activity.RESULT_OK) {
            finish();
        } else {
            Toast.makeText(this, R.string.cp_toast_nostart, Toast.LENGTH_SHORT).show();
        }
    }

    private void loadInstructionImage() {
        ImageView img = findViewById(R.id.cp_image_instruction);
        int drawable_id = R.drawable.cp_instruction_nogps;
        if (SettingsManager.val("gps-enabled") == 1
                && ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            drawable_id = R.drawable.cp_instruction_gps;
        }
        img.setImageDrawable(getResources().getDrawable(drawable_id, getTheme()));
        findViewById(R.id.cp_loading).setVisibility(View.GONE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Utils.createOptionsMenu(menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Utils.optionsItemSelected(this, item);
        return super.onOptionsItemSelected(item);
    }
}
