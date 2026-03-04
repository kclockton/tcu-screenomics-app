package edu.stanford.communication.screenomics.Activity;
import edu.stanford.communication.screenomics.R;

import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.communication.screenomics.AppUtils.AppPreferences;
import edu.stanford.communication.screenomics.DatabaseHelper.LogInPreference;
import edu.stanford.communication.screenomics.FirebaseSettings.FirebaseManagerSingleton;
import edu.stanford.communication.screenomics.FirebaseSettings.UtilsForFirebaseSettings;
import edu.stanford.communication.screenomics.TextBasedData.EventDatabaseHelper;
import edu.stanford.communication.screenomics.TextBasedData.EventMapBuilder;
import edu.stanford.communication.screenomics.PermissionScreens.PermissionChecker;
import edu.stanford.communication.screenomics.PermissionScreens.PermissionParentActivity;
import edu.stanford.communication.screenomics.R;
import edu.stanford.communication.screenomics.TextBasedData.EventUploaderToFireStore;
import edu.stanford.communication.screenomics.Utils;
import edu.stanford.communication.screenomics.modulemanager.ModuleController;
import edu.stanford.communication.screenomics.specs.SpecsCollectionController;
import edu.stanford.communication.screenomics.modulemanager.EventTimestamp;

/**
 * @author Jack Boffa
 * Login screen where the user can create an account or login with code/email/password.
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = LoginActivity.class.getName();

    // Instance variable to hold the password restriction
    private String PASSWORD_RESTRICTION;

    // UI references.
    private EditText mCodeView;
    private EditText mNumberView;
    private AutoCompleteTextView mEmailView;
    private EditText mPasswordView;
    private View mProgressView;
    private View mLoginFormView;

    Button signin;
    public static boolean IsUserWantLoginOrRegister = false;

    // Login async task.
    Task<AuthResult> mLoginTask;

    PermissionChecker checker;

    // Firebase.
    private FirebaseAuth mAuth;

    private AppPreferences sharedPref;
    private LogInPreference LoginPref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize the password restriction
        PASSWORD_RESTRICTION = getString(R.string.password_restriction);

        checker = new PermissionChecker(LoginActivity.this);
        SetStatusBarColor();
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Set up the login form.
        mCodeView = (EditText) findViewById(R.id.login_code);
        mNumberView = (EditText) findViewById(R.id.login_number);
        mEmailView = (AutoCompleteTextView) findViewById(R.id.login_email);
        mPasswordView = (EditText) findViewById(R.id.login_password);
        mLoginFormView = findViewById(R.id.login_form);
        mProgressView = findViewById(R.id.login_progress);

        // Make the code EditText all-caps.
        InputFilter[] editFilters = mCodeView.getFilters();
        InputFilter[] newFilters = new InputFilter[editFilters.length + 1];
        System.arraycopy(editFilters, 0, newFilters, 0, editFilters.length);
        newFilters[editFilters.length] = new InputFilter.AllCaps();
        mCodeView.setFilters(newFilters);

        // Make the Privacy Policy link active.
        TextView priv = findViewById(R.id.login_text_privacy);
        priv.setMovementMethod(LinkMovementMethod.getInstance());

        androidx.constraintlayout.widget.ConstraintLayout layout = findViewById(R.id.linearLayout);

        sharedPref = new AppPreferences(LoginActivity.this);
        LoginPref = new LogInPreference(LoginActivity.this);

        if (!sharedPref.GetShowConsentDialog()){
            Dialog dialog = new Dialog(LoginActivity.this);
            dialog.setContentView(R.layout.consent_dialog);
            dialog.setCancelable(false);
            Objects.requireNonNull(dialog.getWindow()).setGravity(Gravity.CENTER);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            CardView ConsentCard;
            CardView DonotConsentCard;

            ConsentCard = (CardView) dialog.findViewById(R.id.ConsentCard);
            DonotConsentCard = (CardView) dialog.findViewById(R.id.DonotConsentCard);

            ConsentCard.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    sharedPref.Put_SHOW_CONSENT_DIALOG(true);
                    dialog.dismiss();
                    Toast.makeText(LoginActivity.this, "Thank you for providing consent.", Toast.LENGTH_SHORT).show();
                }
            });

            DonotConsentCard.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    sharedPref.Put_SHOW_CONSENT_DIALOG(false);
                    Toast.makeText(LoginActivity.this, "The user consent is required \nbefore the study participation", Toast.LENGTH_SHORT).show();
                }
            });

            dialog.show();
        }

        // Add listeners for login and register.

        signin = (Button) findViewById(R.id.login_sign_in);
        signin.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {

                EventDatabaseHelper.getInstance(LoginActivity.this).deleteDatabase(LoginActivity.this);

                IsUserWantLoginOrRegister = true;

                attemptLogin(false);
            }
        });

        Button register = (Button) findViewById(R.id.login_register);
        register.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {

                EventDatabaseHelper.getInstance(LoginActivity.this).deleteDatabase(LoginActivity.this);

                IsUserWantLoginOrRegister = true;

                String code = mCodeView.getText().toString();

                if (!containsSpecialCharacters(code)){
                    mCodeView.setError("Make sure code does not contain special characters.");

                }else {
                    attemptLogin(true);
                }

            }
        });

        // Initialize Firebase authentication.
        mAuth = FirebaseAuth.getInstance();

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

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Do first-time init stuff.
//        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        if (!Utils.isInstallCodeSet(this))
        {
            onFirstRun();
        }
    }

    private void onFirstRun()
    {

        // Generate random install code and set it in sharedprefs.
        String installcode = Utils.setRandomInstallCode(this);
        String timestamp = new EventTimestamp().getTimestring();

        // Enter and Record this in firebase.
        Map<String, Object> data = new HashMap<>();
        data.put(timestamp, installcode);

        FirebaseManagerSingleton.getFirestore().collection("install").document(timestamp).set(data);

    }

    /**
     * Attempts to sign in the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private void attemptLogin(boolean register)
    {
        // Do nothing if a login is already in progress.
        if (mLoginTask != null) return;

        // Reset errors.
        mCodeView.setError(null);
        mNumberView.setError(null);
        mEmailView.setError(null);
        mPasswordView.setError(null);

        // Store values at the time of the login attempt.
        String code = mCodeView.getText().toString();
        String number = mNumberView.getText().toString();
        String email = mEmailView.getText().toString();
        String password = mPasswordView.getText().toString();

        boolean cancel = false;
        View focusView = null;

        // Check for a valid password, if the user entered one.
        if (TextUtils.isEmpty(password) || !isPasswordValid(password)) {
            mPasswordView.setError(getString(R.string.error_invalid_password));
            focusView = mPasswordView;
            cancel = true;
        }
        if (register && !password.equals(PASSWORD_RESTRICTION))
        {
            mPasswordView.setError(getString(R.string.error_invalid_password));
            focusView = mPasswordView;
            cancel = true;
        }

        // Check for a valid email address.
        if (TextUtils.isEmpty(email)) {
            mEmailView.setError(getString(R.string.error_field_required));
            focusView = mEmailView;
            cancel = true;
        } else if (!isEmailValid(email)) {
            mEmailView.setError(getString(R.string.error_invalid_email));
            focusView = mEmailView;
            cancel = true;
        }

        // Check for a valid number.
        if (TextUtils.isEmpty(number))
        {
            mNumberView.setError(getString(R.string.error_field_required));
            focusView = mNumberView;
            cancel = true;
        }

        // Check for a valid code.
        if (TextUtils.isEmpty(code))
        {
            mCodeView.setError(getString(R.string.error_field_required));
            focusView = mCodeView;
            cancel = true;
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
            return;
        }

        // Show a progress spinner, and kick off a background task to
        // perform the user login attempt.
        showProgress(true);

        /*mAuthTask = new UserLoginTask(email, password);
        mAuthTask.execute((Void) null);*/

        if (register)
        {
            createAccount(calcSubjectId(code, number, email), password);
        }
        else
        {
            signIn(calcSubjectId(code, number, email), password);
        }
    }

    private void createAccount(String subjId, String password)
    {
        mLoginTask = mAuth.createUserWithEmailAndPassword(subjId, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d(TAG, "createUserWithEmail:success");
                            FirebaseUser user = mAuth.getCurrentUser();
                            onUserAcquired(user, true);

                        } else {
                            // If sign in fails, display a message to the user.
                            Log.w(TAG, "createUserWithEmail:failure", task.getException());
                            Toast.makeText(LoginActivity.this, "Registration failed.",
                                    Toast.LENGTH_SHORT).show();

                            showProgress(false);
                        }

                        mLoginTask = null;
                    }
                });
    }

    private void signIn(String subjId, String password)
    {

        mLoginTask = mAuth.signInWithEmailAndPassword(subjId, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d(TAG, "signInWithEmail:success");
                            FirebaseUser user = mAuth.getCurrentUser();
                            onUserAcquired(user, false);

                        } else {
                            // If sign in fails, display a message to the user.
                            Log.w(TAG, "signInWithEmail:failure", task.getException());
                            Toast.makeText(LoginActivity.this, "Could not log in with the provided information.",
                                    Toast.LENGTH_SHORT).show();
                            showProgress(false);
                        }

                        mLoginTask = null;
                    }
                });
    }

    private void onUserAcquired(final FirebaseUser user, boolean register)
    {

        // This flag will avoid automatic login attempts
        if (user == null || user.getEmail() == null)
        {
            showProgress(false);
            return;
        }

        // Get code and data subject ID.
        final String code_id = mCodeView.getText().toString().toUpperCase();
//            final String dataSubjectId = Utils.getDataSubjectId(user.getEmail());
        String dataSubjectId = mCodeView.getText().toString().toUpperCase()+"_" + mNumberView.getText().toString();

        DocumentReference collectionReference = FirebaseFirestore.getInstance().collection("users").document(dataSubjectId).collection("specs").document("specs");

        // If registering, do some initialization of the user in the database.
        if (register) {
            String complete_userId = mEmailView.getText().toString().toLowerCase();

            HashMap<String, Object> data = new HashMap<>();
            data.put("email", complete_userId);

            FirebaseFirestore.getInstance().collection("user_directory").document(dataSubjectId).set(data).addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {

                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {

                }
            });

            // One-time specs log at registration; Specs module owns all specs event collection.
            if (ModuleController.ENABLE_SPECS) {
                SpecsCollectionController.logSpecsOnceNow(this);
            }
        }

        // On first run, we want to copy group-specific settings in to the user's profile.

        // First, check if the user has settings set for them in the database.
        Log.d(TAG, "Checking if user settings have been populated yet...");

        collectionReference.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot value, @Nullable FirebaseFirestoreException error) {
                if (error == null){
                    loginComplete(user);
                }else {
                    loginComplete(user);
                }
            }
        });

    }

    private void loginComplete(FirebaseUser user)
    {

        if (!IsUserWantLoginOrRegister){
            user = null;
            return;
        }

        if (user != null && !mEmailView.getText().toString().isEmpty()){
            // New code
            LogInPreference sharedPref = new LogInPreference(getApplicationContext());

            // Now that user-acquired stuff completed, populate shared pref values so the login is official.
            String pref_id = user.getEmail();
            sharedPref.AddUserSubjId(pref_id);
            sharedPref.AddUserPassword(mPasswordView.getText().toString());
            final String code_id = mCodeView.getText().toString().toUpperCase();
            sharedPref.AddUserCode(code_id);
            String email = mEmailView.getText().toString();
            sharedPref.AddUserEmail(email);
            String number = mNumberView.getText().toString();
            sharedPref.AddUserNumber(number);

            Intent intent;
            intent = new Intent(this, PermissionParentActivity.class);
            IsUserWantLoginOrRegister = false;
            PermissionParentActivity.resetPermissionFlowCompletedForNewSession();
            startActivity(intent);

            edu.stanford.communication.screenomics.LogEvents.LogInOutEventLogger.log(getApplicationContext(), "login");
            finish();
        }

    }

    public boolean containsSpecialCharacters(String input) {
        // Define a regex pattern that matches any character that is not a letter or digit
        String alphanumericPattern = "^[a-zA-Z0-9]+$";

        // Compile the pattern
        Pattern pattern = Pattern.compile(alphanumericPattern);

        // Create a matcher for the input string
        Matcher matcher = pattern.matcher(input);

        // Return true if the input matches the alphanumeric pattern, false otherwise
        return matcher.matches();
    }

    private boolean isEmailValid(String email) {
        return email.contains("@");
    }

    private boolean isPasswordValid(String password) {
        return password.length() >= 4;
    }

    /**
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show) {

        // The progress spinner is a bit janky. Let's just use a toast instead.
        if (show) {
            Toast.makeText(this, "Authenticating...", Toast.LENGTH_SHORT).show();
        }

        // Disable all of the controls.
        findViewById(R.id.login_sign_in).setEnabled(!show);
        findViewById(R.id.login_register).setEnabled(!show);
        mCodeView.setEnabled(!show);
        mNumberView.setEnabled(!show);
        mEmailView.setEnabled(!show);
        mPasswordView.setEnabled(!show);

        /*int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

        mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        mLoginFormView.animate().setDuration(shortAnimTime).alpha(
                show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
            }
        });

        mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
        mProgressView.animate().setDuration(shortAnimTime).alpha(
                show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        });*/
    }

    public static String calcSubjectId(String code, String number, String email)
    {
        return code.toUpperCase() + "_" + number + "_" + email;
    }

    /**
     * Helper function that logs out the user, called from other activities.
     * @param context App context
     * @param showLoginActivity Whether to open the LoginActivity after logging out.
     */
    public static void userLogOut(Context context, boolean showLoginActivity)
    {

        // New code
        LogInPreference sharedPref = new LogInPreference(context);
        sharedPref.AddUserSubjId("");
        sharedPref.AddUserCode("");
        sharedPref.AddUserEmail("");
        sharedPref.AddUserNumber("");
        sharedPref.AddUserPassword("");

        sharedPref.clearAllPreferences();

        // Log out of Firebase.
        FirebaseAuth auth = FirebaseAuth.getInstance();
        auth.signOut();

        // Stop the CaptureUploadService (screenshot module).
        ((edu.stanford.communication.screenomics.screenshots.ScreenshotModuleHost) context.getApplicationContext()).stopCaptureService(context);

        // Start LoginActivity if desired.
        if (showLoginActivity)
        {
            Intent intent = new Intent(context, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(intent);

        }
    }

    /**
     * Static utility method that makes sure the user is fully logged in. If the user is not logged
     * in at all, this method will do nothing other than returning -1. If the user is logged in in
     * shared prefs but not in FirebaseAuth, this method will do a Firebase login.
     * @param context App context
     * @param listener If the user is logged in in sharedprefs but not Firebase, this listener will
     * be invoked during the Firebase login.
     * @return -1 if logged out; 0 if not logged in in Firebase (in which case a Firebase login is
     * now being attempted); 1 if fully logged in already.
     */
    public static int ensureCompleteLogin(Context context, OnCompleteListener<AuthResult> listener)
    {

        // New Code

        LogInPreference sharedPref = new LogInPreference(context);
        String subj_id = sharedPref.GetUserSubjId();
        String password = sharedPref.GetUserPassword();

        // Check for a shared prefs login.
        if (subj_id.isEmpty()) {
            return -1;
        }

        FirebaseAuth auth = FirebaseAuth.getInstance();

        // If not logged in with Firebase, do a login there (the user doesn't have to know).
        if (auth.getCurrentUser() == null)
        {
            Task<AuthResult> task = auth.signInWithEmailAndPassword(subj_id, password);
            if (listener != null) task.addOnCompleteListener(listener);
            return 0;
        }

        // We were fully logged in.
        return 1;
    }

}

