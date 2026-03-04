package edu.stanford.communication.screenomics.TextBasedData;

import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.installations.Utils;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.communication.screenomics.DatabaseHelper.LogInPreference;
import edu.stanford.communication.screenomics.modulemanager.EventTimestamp;
import edu.stanford.communication.screenomics.FirebaseSettings.FirebaseManagerSingleton;
import edu.stanford.communication.screenomics.FirebaseSettings.UtilsForFirebaseSettings;
import edu.stanford.communication.screenomics.modulemanager.ModuleCharacteristics;

/**
 * May 7, 2025
 * This class is used to upload events to firebase
 *
 */

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

// Memory management New Class
// some changes were done in existing functions to reduce overload on memory

public class EventUploaderToFireStore {

    private static final String TAG = "EventUploaderToFireStore";
    private static EventUploaderToFireStore instance;
    private final EventTimestamp timestamp;
    private final Context context;
    LogInPreference sharedPref;

    private EventUploaderToFireStore(Context context) {
        this.timestamp = new EventTimestamp();
        this.context = context.getApplicationContext();
        sharedPref = new LogInPreference(context.getApplicationContext());
    }

    public static synchronized EventUploaderToFireStore getInstance(Context context) {
        if (instance == null) {
            instance = new EventUploaderToFireStore(context);
        }
        return instance;
    }

    /** Delay after forceFlushToDB() so the async flush can write to SQLite before we read. */
    private static final int FLUSH_BEFORE_READ_DELAY_MS = 800;

    private final Handler uploadHandler = new Handler(Looper.getMainLooper());
    private AtomicBoolean isUploading = new AtomicBoolean(false);
    private AtomicBoolean isMemoryLow = new AtomicBoolean(false);

    public void startUploadOfflineToOnlineEvents(boolean IsUserTryingToLogout) {

        if (!NetworkUtils.isInternetAvailable(context)) {
            return;
        }

        if (IsUserTryingToLogout) {
            isUploading.set(false);
        } else {
            if (isMemoryLow.get()) {
                return;
            }
        }

        if (isUploading.get()) {
            Log.d(TAG, "Upload in progress, waiting for next round...");
            return;
        }

        EventOperationManager.getInstance(context).forceFlushToDB();
        isUploading.set(true);

        uploadHandler.postDelayed(() -> {
            Cursor cursor = null;
            try {
                WeakReference<Context> contextRef = new WeakReference<>(context);
                EventDatabaseHelper dbHelper = EventDatabaseHelper.getInstance(contextRef.get());
                cursor = dbHelper.getLimitedEvents(300);
                List<Integer> eventIds = new ArrayList<>();
                List<Map<String, Object>> eventsList = new ArrayList<>();

                if (cursor == null || cursor.getCount() == 0) {
                    Log.d(TAG, "No offline events to upload.");
                    isUploading.set(false);
                    return;
                }

                String subjectId = UtilsForFirebaseSettings.getCodeAndNumber(context);
                if (subjectId.isEmpty()) {
                    Log.d(TAG, "Subject ID is empty. Cannot upload.");
                    isUploading.set(false);
                    return;
                }

                while (cursor.moveToNext()) {
                    int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                    String eventName = cursor.getString(cursor.getColumnIndexOrThrow("event_name"));
                    String eventData = cursor.getString(cursor.getColumnIndexOrThrow("event_data"));
                    eventIds.add(id);
                    Map<String, Object> eventMap = new HashMap<>();
                    eventMap.put("event_name", eventName);
                    eventMap.put("event_data", eventData);
                    eventsList.add(eventMap);
                }

                FirebaseFirestore db = FirebaseManagerSingleton.getFirestore();
                CollectionReference eventCollection = db.collection("users").document(subjectId).collection("events");
                CollectionReference specsCollection = db.collection("users").document(subjectId).collection("specs");
                uploadBatch(eventCollection, specsCollection, eventsList, eventIds, dbHelper, IsUserTryingToLogout);
            } catch (Exception e) {
                Log.e(TAG, "Error processing events from SQLite: ", e);
                isUploading.set(false);
            } finally {
                if (cursor != null) {
                    try {
                        cursor.close();
                    } catch (Exception e) {
                        Log.e(TAG, "Error closing cursor: ", e);
                    }
                }
                isUploading.set(false);
            }
        }, FLUSH_BEFORE_READ_DELAY_MS);
    }

// Memory management New function

    private static final String EVENT_TYPE_ANDROID_SPECS = "android-specs";

    private void uploadBatch(CollectionReference eventCollection, CollectionReference specsCollection, List<Map<String, Object>> eventsData, List<Integer> eventIds, EventDatabaseHelper dbHelper, boolean IsUserTryingLogout) {

        FirebaseFirestore db = FirebaseManagerSingleton.getFirestore();
        WriteBatch batch = db.batch();
        Gson gson = new Gson();

        if (eventsData.isEmpty()) {
            isUploading.set(false);
            return;
        }

        Log.d(TAG, "Uploading batch of " + eventsData.size() + " events");

        for (Map<String, Object> event : eventsData) {
            String eventName = (String) event.get("event_name");
            String eventDataJson = (String) event.get("event_data");

            if (eventName == null || eventName.isEmpty() || eventDataJson == null) continue;

            Map<String, Object> data = gson.fromJson(eventDataJson, Map.class);
            if (data == null) continue;

            Object typeObj = data.get("type");
            boolean isSpecs = EVENT_TYPE_ANDROID_SPECS.equals(typeObj != null ? typeObj.toString() : null);

            DocumentReference ref = isSpecs ? specsCollection.document(eventName) : eventCollection.document(eventName);
            batch.set(ref, data);
        }

        batch.commit().addOnSuccessListener(aVoid -> {
            Log.d(TAG, "Batch upload successful. Deleting uploaded events...");

            for (int id : eventIds) {
                dbHelper.deleteEvent(id);
                Log.d(TAG, "Deleted event ID: " + id);
            }

            isUploading.set(false);

            if (IsUserTryingLogout){
                if (EventDatabaseHelper.getInstance(context).getTotalEventCount() > 0){
                    startUploadOfflineToOnlineEvents(true);
                }else {
                    EventDatabaseHelper.getInstance(context).deleteDatabase(context);
                }
            }

        }).addOnFailureListener(e -> {
            Log.e(TAG, "Batch upload failed: " + e.getMessage());
            isUploading.set(false);
        });
    }

    // Memory management New function

    public void uploadSingleEvent(String eventName, HashMap<String, String> eventData, Context context) {

        if (!NetworkUtils.isInternetAvailable(context)) return;

//        String subjectId = sharedPref.GetUserSubjId().replace(".", "").replace("@", "").replace("_", "");
        String subjectId = UtilsForFirebaseSettings.getCodeAndNumber(context);

        if (!subjectId.isEmpty()) {

            FirebaseFirestore db = FirebaseManagerSingleton.getFirestore();

            HashMap<String, String> map = HashMapPool.getMap();
            HashMap<String, String> map1 = HashMapPool.getMap();
            map.put(eventName, timestamp.getTimestringFriendly() + EventOperationManager.getInstance(context).UpdateTickerField(eventName, eventData));
            map1.put("MostRecentEventTime", timestamp.getTimestringFriendly());

            String eventDocPath = "users/" + subjectId + "/events/" + GenerateEventId(eventName);
            db.collection("users")
                    .document(subjectId)
                    .collection("events")
                    .document(GenerateEventId(eventName))
                    .set(eventData)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Event written to Firestore: " + eventDocPath))
                    .addOnFailureListener(e -> Log.e(TAG, "Firestore event write failed: " + eventDocPath + " - " + e.getMessage(), e));

            EventUploader.getInstance(context).uploadImmediately(map);
            EventUploader.getInstance(context).uploadImmediately(map1);

            HashMapPool.releaseMap(map);
            HashMapPool.releaseMap(map1);
        }
    }

    public void SetIsMemoryLow(boolean isLowMemory) {
        isMemoryLow.set(isLowMemory);
    }

    public String GenerateEventId(String className) {
        return  className + " "+ timestamp +"_"+ ModuleCharacteristics.getInstance().getLocationEventCharacteristics().get("id");
    }

    // Add a cleanup method
    public static synchronized void destroy() {
        if (instance != null) {
            // Release any resources
            instance = null;
        }
    }

}

