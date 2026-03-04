package edu.stanford.communication.screenomics.PermissionScreens;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.viewpager.widget.ViewPager;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import edu.stanford.communication.screenomics.Activity.AppRunningActivity;
import edu.stanford.communication.screenomics.screenshots.CaptureUploadStarter;
import edu.stanford.communication.screenomics.screenshots.CaptureUploadService;
import edu.stanford.communication.screenomics.modulemanager.ModulePermissions;
import edu.stanford.communication.screenomics.R;
import edu.stanford.communication.screenomics.interactions.InteractionsUpdater;

public class PreviewFrag extends Fragment {

    private ConstraintLayout ParentLay;
    private ConstraintLayout contentDesLayout;
    private TextView Header;
    private CardView AllowCard;
    private TextView AllowTxt;
    private LinearLayout DoNotAllowCard;
    private TextView DoNotAllowTxt;
    private ScrollView DescriptionTxt;
    private TextView Description;

    public static String WhichScreenIsSelected = null;

    Boolean checkForIsPermissionAllowed = false;
    String PermissionHeader;
    String PermissionDescription;

    PermissionChecker permissionChecker;

    public PreviewFrag() {
        // Required empty public constructor
    }

    public PreviewFrag(String PermissionHeader, String PermissionDescription) {
        this.PermissionDescription = PermissionDescription;
        this.PermissionHeader = PermissionHeader;
    }

    public PreviewFrag(String PermissionHeader, String PermissionDescription,boolean IsBold) {
        this.PermissionDescription = PermissionDescription;
        this.PermissionHeader = PermissionHeader;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_preview, container, false);

        contentDesLayout = view.findViewById(R.id.content_des_layout);
        Header =  view.findViewById(R.id.Header);
        AllowCard = view.findViewById(R.id.AllowCard);
        AllowTxt = view.findViewById(R.id.AllowTxt);
        DoNotAllowCard = view.findViewById(R.id.DoNotAllowCard);
        DoNotAllowTxt = view.findViewById(R.id.DoNotAllowTxt);
        DescriptionTxt = view.findViewById(R.id.DescriptionTxt);
        Description = view.findViewById(R.id.Description);

        permissionChecker = new PermissionChecker(requireActivity());

        Header.setText(PermissionHeader);

        Description.setText(PermissionDescription);

        DoNotAllowCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                permissionChecker.ShowDialog(getString(R.string.do_not_allow_note),PermissionHeader,null,false);

            }
        });

        AllowCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkForIsPermissionAllowed = true;
                if (PermissionHeader.equals(getString(R.string.usage_permission_header))){
                    WhichScreenIsSelected = getString(R.string.usage_permission_header);

                    Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                    startActivity(intent);

                } else if (PermissionHeader.equals(getString(R.string.notification_permission_header))) {
                    AskForPermission(Manifest.permission.POST_NOTIFICATIONS);
                    WhichScreenIsSelected = "NOPE";
                }else if (PermissionHeader.equals(getString(R.string.read_notification_permission_header))) {
                    WhichScreenIsSelected = getString(R.string.read_notification_permission_header);

                    Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                    startActivity(intent);

                }else if (PermissionHeader.equals(getString(R.string.accessibility_permission_header))) {
                    // if not construct intent to request permission
                    WhichScreenIsSelected = getString(R.string.accessibility_permission_header);

                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    // request permission via start activity for result
                    startActivity(intent);
                }else if (PermissionHeader.equals(getString(R.string.physical_permission_header))) {
                    WhichScreenIsSelected = "NOPE";
                    AskForPermission(Manifest.permission.ACTIVITY_RECOGNITION);
                }else if (PermissionHeader.equals(getString(R.string.location_permission_header))) {
                    WhichScreenIsSelected = "NOPE";
                    AskForPermission(Manifest.permission.ACCESS_FINE_LOCATION);
                }else if (PermissionHeader.equals(getString(R.string.media_projection_permission_header))) {
                    if (!permissionChecker.CheckIsAnyPermissionLeftFromBeingAllowed()) {
                        WhichScreenIsSelected = "NOPE";
                        beginCapture();
                    } else {
                        Toast.makeText(requireContext(), "Complete the other required permissions first to start", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        return view;
    }

    private void beginCapture() {
        if (ModulePermissions.needMediaProjection()) {
            // Need media projection: use CaptureUploadStarter (shows system permission dialog).
            Intent intent = new Intent(requireContext(), CaptureUploadStarter.class);
            intent.putExtra(CaptureUploadStarter.EXTRA_FROM_PERMISSION_FLOW, true);
            intent.putExtra(CaptureUploadService.EXTRA_STARTUP_INSTIGATOR, "user");
            startActivityForResult(intent, 90);
        } else {
            // Screenshots module off: start service directly and go to main screen (no media projection).
            Intent serviceIntent = new Intent(requireContext(), CaptureUploadService.class);
            serviceIntent.putExtra(CaptureUploadService.EXTRA_STARTUP_INSTIGATOR, "user");
            ContextCompat.startForegroundService(requireContext(), serviceIntent);
            startActivity(new Intent(requireContext(), AppRunningActivity.class));
            requireActivity().finish();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        CheckForPermissionAndSetButtonToFade(true);

    }

    public void CheckForPermissionAndSetButtonToFade(boolean IsPermission){

        checkForIsPermissionAllowed = IsPermission;

        if (checkForIsPermissionAllowed){
            if (PermissionHeader != null){
                if (PermissionHeader.equals(getString(R.string.usage_permission_header))){
                    if (UsagePermissionActivity.appHasUsageAccess(requireContext())){
//                    ((PermissionParentActivity)requireActivity()).onResume();
//                    super.onResume();
                        SetButtonTextToAllowed();
                    }

                }else if (PermissionHeader.equals(getString(R.string.physical_permission_header))) {
                    if (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.ACTIVITY_RECOGNITION)
                            == PackageManager.PERMISSION_GRANTED) {
                        SetButtonTextToAllowed();

//                    ((PermissionParentActivity)requireActivity()).ScrollToNextPage();

                    }
                }else if (PermissionHeader.equals(getString(R.string.location_permission_header))) {
                    if (ContextCompat.checkSelfPermission(requireActivity(), android.Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {
                        SetButtonTextToAllowed();

                    }
                }

                else if (PermissionHeader.equals(getString(R.string.read_notification_permission_header))) {
                    ContentResolver contentResolver = requireActivity().getContentResolver();
                    String enabledNotificationListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners");
                    String packageName = requireActivity().getPackageName();

                    if (PermissionChecker.isNotificationServiceEnabled(requireContext())){
                        SetButtonTextToAllowed();
                    }

                }else if (PermissionHeader.equals(getString(R.string.accessibility_permission_header))) {

                    if (PermissionChecker.isAccessibilityServiceEnabled(requireContext(), InteractionsUpdater.class)){
                        SetButtonTextToAllowed();
                    }

                } else if (PermissionHeader.equals(getString(R.string.notification_permission_header))) {

                    if (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED) {

                        SetButtonTextToAllowed();
                    }

                } else {
                    checkForIsPermissionAllowed = false;
                }
            }

        }
    }

    private void SetButtonTextToAllowed() {
        AllowCard.setAlpha(0.5f);
        AllowCard.setClickable(false);
        AllowTxt.setText(R.string.allowed);
        DoNotAllowCard.setVisibility(View.GONE);

        // If this is the last permission screen and all required permissions are now met, go to main.
        if (isOnLastPermissionPage() && !permissionChecker.CheckIsAnyPermissionLeftFromBeingAllowed()) {
            beginCapture();
        }
    }

    /** True if the current ViewPager page is the last one (so no media projection or other screen follows). */
    private boolean isOnLastPermissionPage() {
        ViewPager pager = requireActivity().findViewById(R.id.Viewpager);
        return pager != null
                && pager.getAdapter() != null
                && pager.getAdapter().getCount() > 0
                && pager.getCurrentItem() == pager.getAdapter().getCount() - 1;
    }

    private void AskForPermission(String Permission){

        Dexter.withContext(requireContext())
                .withPermission(Permission)
                .withListener(new PermissionListener() {
                    @Override public void onPermissionGranted(PermissionGrantedResponse response) {
                        ((PermissionParentActivity)requireActivity()).ScrollToNextPage();

                        SetButtonTextToAllowed();
                    }
                    @Override public void onPermissionDenied(PermissionDeniedResponse response) {

                        permissionChecker.ShowDialog(getString(R.string.do_not_allow_note) + " Click on opens settings and allow " + PermissionHeader.toLowerCase()+".",PermissionHeader,getString(R.string.open_setting),false);
//                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
//                        Uri uri = Uri.fromParts("package", requireContext().getPackageName(), null);
//                        intent.setData(uri);
//                        startActivity(intent);
                    }
                    @Override public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {
                        token.cancelPermissionRequest();
                        token.continuePermissionRequest();

                    }
                }).check();
    }

}