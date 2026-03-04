package edu.stanford.communication.screenomics.FirebaseSettings;

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import edu.stanford.communication.screenomics.modulemanager.EventTimestamp;

/**
 * Fetches server time from Firebase and sets it on the canonical {@link EventTimestamp}
 * so all event timestamps across the app use real-world time.
 */
public final class ServerTimeSync {

    private static final String TAG = "ServerTimeSync";

    private ServerTimeSync() {}

    /**
     * Fetches server time from Firebase and sets the reference on {@link EventTimestamp}.
     * Call from the app (e.g. on startup or when capture starts).
     */
    public static void retrieveServerTime() {
        DatabaseReference offsetRef = FirebaseDatabase.getInstance().getReference(".info/serverTimeOffset");
        offsetRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Long offset = dataSnapshot.getValue(Long.class);
                if (offset != null) {
                    long serverTime = System.currentTimeMillis() + offset;
                    long elapsed = SystemClock.elapsedRealtime();
                    EventTimestamp.setServerTime(serverTime, elapsed);
                    Log.i(TAG, "server time established!");
                } else {
                    Log.e(TAG, "retrieveServerTime() returned null data");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "retrieveServerTime() listener cancelled");
            }
        });
    }
}
