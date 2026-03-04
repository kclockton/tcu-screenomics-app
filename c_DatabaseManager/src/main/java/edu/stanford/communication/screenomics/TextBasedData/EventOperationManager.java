package edu.stanford.communication.screenomics.TextBasedData;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import edu.stanford.communication.screenomics.modulemanager.EventTimestamp;
import edu.stanford.communication.screenomics.modulemanager.ModuleCharacteristics;

/**
 * May 7, 2025
 * This class is used to perform CRUD operations on local database
 */
public class EventOperationManager {
    private static final String TAG = "EventOperationManager";
    private static EventOperationManager instance;  // Singleton instance
    private final EventDatabaseHelper databaseHelper;
    private final ExecutorService executorService;
    private final List<EventData> eventBuffer = new ArrayList<>(); // Buffer to store events
    private static final int BATCH_SIZE = 50;  // Adjust batch size for optimization
    /** Flush buffer to SQLite at least this often so events reach users/.../events. */
    private static final long PERIODIC_FLUSH_INTERVAL_MS = 60_000;
    /** First flush runs after this delay so events (e.g. registration specs) reach SQLite soon. */
    private static final long FIRST_FLUSH_DELAY_MS = 2_000;
    private static final Object LOCK = new Object();
    EventTimestamp timestamp = new EventTimestamp();// Synchronization lock
    private final Handler flushHandler;
    private final Runnable periodicFlushRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (LOCK) {
                if (!eventBuffer.isEmpty()) {
                    flushEventsToDB();
                }
            }
            if (flushHandler != null) {
                flushHandler.postDelayed(this, PERIODIC_FLUSH_INTERVAL_MS);
            }
        }
    };

    // Private constructor for Singleton pattern
    private EventOperationManager(Context context) {
        this.databaseHelper = EventDatabaseHelper.getInstance(context.getApplicationContext());
        this.executorService = Executors.newSingleThreadExecutor(); // Background thread
        this.flushHandler = new Handler(Looper.getMainLooper());
        this.flushHandler.postDelayed(periodicFlushRunnable, FIRST_FLUSH_DELAY_MS);
    }

    // Memory management New function

    // Singleton instance method (Thread-Safe)
    public static synchronized EventOperationManager getInstance(Context context) {
        if (instance == null) {
            instance = new EventOperationManager(context);
        }
        return instance;
    }

    // Memory management New function

    public void addEvent(Map<String, String> moduleInfo, HashMap<String, String> eventDetails) {
        synchronized (LOCK) {

//            if (eventBuffer.size() >= MAX_BUFFER_SIZE) {
//                Log.w(TAG, "Event buffer size exceeded maximum. Forcing flush.");
//                flushEventsToDB();
//            }

            EventData event = new EventData.Builder(moduleInfo.get("type"),moduleInfo.get("className"))
                    .addFields(eventDetails)
                    .build();

            Log.d(TAG, "Event : " + event.toMap().toString());

            eventBuffer.add(event);  // Add event to buffer

            // Flush specs (and any buffered events) soon so they are in SQLite for upload
            if (Objects.equals(moduleInfo.get("className"), "SpecsEvent")) {
                forceFlushToDB();
            }

            if (Objects.equals(moduleInfo.get("updateTicker"), "1")) {

//                Log.d(TAG, " Update ticker" + moduleInfo.get("updateTicker") + " Event : " + event.toMap().toString());
                    DataStorage.getInstance().addEvent("MostRecentEventTime", timestamp.getTimestringFriendly());
                    DataStorage.getInstance().addEvent(moduleInfo.get("className"), timestamp.getTimestringFriendly() + UpdateTickerField(moduleInfo.get("className"), eventDetails));

            }

            // Check if we need to batch write
            if (eventBuffer.size() >= BATCH_SIZE) {
                flushEventsToDB(); // Batch insert into SQLite
            }
        }
    }

    // Memory management New function

    /** Request buffer flush to SQLite so events are available for upload. Flush runs asynchronously. */
    public void forceFlushToDB() {
        synchronized (LOCK) {
            if (!eventBuffer.isEmpty()) {
                flushEventsToDB();
            }
        }
    }

    // Batch insert events into SQLite
    private void flushEventsToDB() {

        if (!executorService.isShutdown()) {
            executorService.execute(() -> {
                synchronized (LOCK) {
                    if (!eventBuffer.isEmpty()) {
                        try {
                            List<EventData> batch = new ArrayList<>(eventBuffer); // Copy buffer
                            eventBuffer.clear(); // Clear buffer before inserting

                            databaseHelper.openDatabase(); // Ensure DB is open
                            for (EventData event : batch) {
                                databaseHelper.insertEvent(event.getUniqueEventId(), MapUtils.serializeMap(event.toMap()));
                            }
                            Log.d(TAG, "Batch inserted " + batch.size() + " events into DB.");
                        } catch (Exception e) {
                            Log.e(TAG, "Error inserting batch events: " + e.getMessage());
                        }
                    }
                }
            });

        }
    }

    public String UpdateTickerField(String ModuleInfo, HashMap<String, String> EventData) {
        Log.e(TAG, "ModuleInfo: " + ModuleInfo);
        StringBuilder result = new StringBuilder(" [");
        if (Objects.equals(ModuleInfo, "InternetEvent")){
            result.append(EventData.get("activity"));
        } else if (Objects.equals(ModuleInfo, "LogInOutEvent")) {
            result.append(EventData.get("type"));
        } else if (Objects.equals(ModuleInfo, "ScreenOnOffEvent")) {
            result.append(EventData.get("screen"));
        } else if (Objects.equals(ModuleInfo, "ScreenshotPauseEvent")) {
            result.append(EventData.get("type"));
            String cause = EventData.get("cause");
            if (cause != null && !cause.isEmpty()) {
                result.append(" ").append(cause);
            }
        } else if (Objects.equals(ModuleInfo, "SpecsEvent")) {
            result.append("specs");
        } else {
            return "";
        }
        result.append("]");
        return result.toString();
    }

    // Flush remaining events on app shutdown or when needed
//    public void flushAndShutdown() {
//        flushEventsToDB();  // Final flush
//        executorService.shutdown(); // Shut down executor service
//    }

    public void flushAndShutdown() {
        flushEventsToDB();  // Final flush
        executorService.shutdown(); // Shut down executor service

        try {
            // Wait for tasks to complete with a timeout
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // FIX: Add proper cleanup method
    public void destroy() {
        if (flushHandler != null) {
            flushHandler.removeCallbacks(periodicFlushRunnable);
        }
        if (executorService != null && !executorService.isShutdown()) {
            flushEventsToDB();
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(3, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Release singleton instance
        synchronized (EventOperationManager.class) {
            instance = null;
        }
    }

}

