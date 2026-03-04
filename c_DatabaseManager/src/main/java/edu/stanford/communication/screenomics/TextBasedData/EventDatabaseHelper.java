package edu.stanford.communication.screenomics.TextBasedData;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import android.database.SQLException;
import java.util.List;

public class EventDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "EventDatabaseHelper";
    private static final String DATABASE_NAME = "UserEvents.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_NAME = "events";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_EVENT_NAME = "event_name";
    private static final String COLUMN_EVENT_DATA = "event_data";

    private static volatile EventDatabaseHelper instance;
    private SQLiteDatabase database;

    private EventDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // Sql New Function

    public synchronized void openDatabase() {
        if (database == null || !database.isOpen()) {
            database = this.getWritableDatabase();
        }
    }

    public static EventDatabaseHelper getInstance(Context context) {
        if (instance == null) {
            synchronized (EventDatabaseHelper.class) {
                if (instance == null) {
                    instance = new EventDatabaseHelper(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NAME + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_EVENT_NAME + " TEXT UNIQUE, "
                + COLUMN_EVENT_DATA + " TEXT)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    // Sql New Function

//    public synchronized void openDatabase() {
//        if (database == null || !database.isOpen()) {
//            database = this.getWritableDatabase();
//        }
//    }

    // Sql New Function

    public synchronized void closeDatabase() {
        if (database != null && database.isOpen()) {
            database.close();
            database = null;
        }
    }

    // Sql New Function

    public Cursor getLimitedEvents(int limit) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM "+TABLE_NAME+" ORDER BY id ASC LIMIT ?", new String[]{String.valueOf(limit)});
    }

    // Sql New Function

    public int getTotalEventCount() {
        int count = 0;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_NAME, null);
            if (cursor != null && cursor.moveToFirst()) {
                count = cursor.getInt(0); // Get the count from the first column
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching total event count: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            db.close(); // Close database after operation
        }

        return count;
    }

    public void insertEvent(String eventName, String eventData) {
        try {
            openDatabase();
            ContentValues contentValues = new ContentValues();
            contentValues.put(COLUMN_EVENT_NAME, eventName);
            contentValues.put(COLUMN_EVENT_DATA, eventData);
            database.insertWithOnConflict(TABLE_NAME, null, contentValues, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (SQLException e) {
            Log.e(TAG, "Error inserting event: ", e);
        }
    }

    public void insertEventsBatch(List<EventEntity> events) {
        try {
            openDatabase();
            database.beginTransaction();
            for (EventEntity event : events) {
                ContentValues contentValues = new ContentValues();
                contentValues.put(COLUMN_EVENT_NAME, event.getEventName());
                contentValues.put(COLUMN_EVENT_DATA, event.getEventData());
                database.insertWithOnConflict(TABLE_NAME, null, contentValues, SQLiteDatabase.CONFLICT_REPLACE);
            }
            database.setTransactionSuccessful();
        } catch (SQLException e) {
            Log.e(TAG, "Error inserting batch events: ", e);
        } finally {
            database.endTransaction();
        }
    }

    public void deleteEvent(int id) {

        Log.e(TAG, "Delete event called");

        try {
            openDatabase();

            int i = database.delete(TABLE_NAME, COLUMN_ID + "=?", new String[]{String.valueOf(id)});

            Log.e(TAG, "trying deleting event: "  + String.valueOf(i));

        } catch (SQLException e) {
            Log.e(TAG, "Error deleting event: ", e);
        }
    }

    public Cursor getAllEvents() {
        openDatabase();
//        return database.rawQuery("SELECT * FROM " + TABLE_NAME, null);
        return database.rawQuery("SELECT * FROM " + TABLE_NAME + " ORDER BY id ASC LIMIT 10", null);

    }

    public void deleteDatabase(Context context) {
        closeDatabase();
        context.deleteDatabase(DATABASE_NAME);
        Log.d(TAG, "Database deleted.");
    }
}

// Sql New Function class

class EventEntity {
    private String eventName;
    private String eventData;

    public EventEntity(String eventName, String eventData) {
        this.eventName = eventName;
        this.eventData = eventData;
    }

    public String getEventName() {
        return eventName;
    }

    public String getEventData() {
        return eventData;
    }
}
