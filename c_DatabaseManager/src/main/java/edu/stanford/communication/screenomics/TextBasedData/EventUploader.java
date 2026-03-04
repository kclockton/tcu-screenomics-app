package edu.stanford.communication.screenomics.TextBasedData;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.firebase.firestore.FirebaseFirestore;

import edu.stanford.communication.screenomics.DatabaseHelper.LogInPreference;
import edu.stanford.communication.screenomics.FirebaseSettings.FirebaseManagerSingleton;
import edu.stanford.communication.screenomics.FirebaseSettings.UtilsForFirebaseSettings;
import edu.stanford.communication.screenomics.TextBasedData.DataStorage;
import edu.stanford.communication.screenomics.TextBasedData.NetworkUtils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

// Memory management New Class

public class EventUploader {

    private static volatile EventUploader instance;
    private final String TAG = "EventUploader";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DataStorage dataStorage = DataStorage.getInstance();
    private boolean isUploading = false;
    private final Context context;
    private final Lock uploadLock = new ReentrantLock();

    private boolean lowMemoryLock = false;

    // Private constructor to prevent direct instantiation
    private EventUploader(Context context) {
        this.context = context.getApplicationContext(); // Prevent memory leaks
    }

    // Singleton getInstance() method (Thread-safe)
    public static EventUploader getInstance(Context context) {
        if (instance == null) {
            synchronized (EventUploader.class) {
                if (instance == null) {
                    instance = new EventUploader(context);
                }
            }
        }
        return instance;
    }

    public void acquireLowMemoryLock() {
        lowMemoryLock = true;
    }

    public void releaseLowMemoryLock(){
        lowMemoryLock = false;
    }

    // Runnable to upload events every 10 seconds
    private final Runnable uploadRunnable = new Runnable() {
        @Override
        public void run() {
            uploadEvents(); // Upload in a synchronized manner
            handler.postDelayed(this, 5000);
        }
    };

    public void startUploading() {
        handler.postDelayed(uploadRunnable, 5000);
    }

    public void stopUploading() {
        handler.removeCallbacks(uploadRunnable);
    }

    private void uploadEvents() {
        if (uploadLock.tryLock()) { // Acquire lock if available (non-blocking)
            try {
                if (!isUploading && NetworkUtils.isInternetAvailable(context)) {
                    isUploading = true;

                    String subject_id = UtilsForFirebaseSettings.getCodeAndNumber(context);
//                    String subject_id = sharedPref.GetUserSubjId();

                    if (!subject_id.isEmpty()) {
                        Map<String, String> eventData = dataStorage.getEvents();
                        if (eventData.isEmpty()) {
                            isUploading = false;
                            return;
                        }

                        final String firestorePath = "ticker/" + subject_id;
                        FirebaseManagerSingleton.getFirestore().collection("ticker").document(subject_id)
                                .set(eventData, SetOptions.merge())
                                .addOnCompleteListener(new OnCompleteListener<Void>() {
                                    @Override
                                    public void onComplete(@NonNull Task<Void> task) {
                                        isUploading = false;
                                        if (task.isSuccessful()) {
                                            Log.d(TAG, "Pushed to Firestore " + firestorePath + ", events: " + eventData);
                                            dataStorage.clearEvents();
                                            // Also upload SQLite events to users/.../events
                                            EventUploaderToFireStore.getInstance(context).startUploadOfflineToOnlineEvents(false);
                                        } else {
                                            Exception e = task.getException();
                                            Log.e(TAG, "Firestore write failed for " + firestorePath + ": " + (e != null ? e.getMessage() : "unknown"), e);
                                            // Do not clear events so they can be retried
                                        }
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Firestore write failed for " + firestorePath, e);
                                    isUploading = false;
                                });
                    }
                }
            } finally {
                uploadLock.unlock(); // Ensure lock is released after execution
            }
        } else {
            Log.d(TAG, "Upload already in progress, skipping this request.");
        }

    }

    public void uploadImmediately(Map<String, String> eventData) {
        if (eventData == null || eventData.isEmpty()) {
            Log.d(TAG, "No data to upload.");
            return;
        }

        // Acquire lock to ensure only one thread uploads at a time
//        uploadLock.lock();

            if (NetworkUtils.isInternetAvailable(context)) {
//                String subject_id = sharedPref.GetUserSubjId();
                String subject_id = UtilsForFirebaseSettings.getCodeAndNumber(context);

                if (!subject_id.isEmpty()) {
                    Log.d(TAG, "Uploading event data immediately: " + eventData);

                    final String path = "ticker/" + subject_id;
                    FirebaseManagerSingleton.getFirestore().collection("ticker").document(subject_id)
                            .set(eventData, SetOptions.merge())
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Immediate upload OK: " + path);
                                dataStorage.clearEvents();
                            })
                            .addOnFailureListener(e -> Log.e(TAG, "Immediate upload failed for " + path, e));
                }
            }

//        finally {
//            uploadLock.unlock(); // Always release the lock
//        }
    }

    public void destroy() {
        stopUploading();
        handler.removeCallbacksAndMessages(null);  // Remove all callbacks

        // Release singleton instance
        synchronized (EventUploader.class) {
            instance = null;
        }
    }

}
