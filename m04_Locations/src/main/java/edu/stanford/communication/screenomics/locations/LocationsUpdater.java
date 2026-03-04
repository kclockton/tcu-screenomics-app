package edu.stanford.communication.screenomics.locations;

import edu.stanford.communication.screenomics.TextBasedData.EventOperationManager;
import edu.stanford.communication.screenomics.modulemanager.EventTimestamp;
import edu.stanford.communication.screenomics.modulemanager.ModuleCharacteristics;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Captures user location at a consistent interval. Same pattern as SpecsUpdater.
 * Uses LocationsInfo for event payload structure. Owned by LocationsCollectionController.
 */
public class LocationsUpdater {

    private static final String TAG = "LocationsUpdater";
    private static final long LOCATION_PREFETCH_OFFSET_MS = 15000;

    private final Context context;
    private final FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private long intervalMillis;
    private Location lastLocation;
    private Location pendingLocation;
    private final EventTimestamp timestamp;
    private long nextScheduledReportTime = 0;

    private final ScheduledExecutorService reportScheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> scheduledReportTask;
    private final ScheduledExecutorService prefetchScheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> scheduledPrefetchTask;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public LocationsUpdater(Context context, long intervalMillis) {
        this.context = context;
        this.intervalMillis = intervalMillis;
        this.timestamp = new EventTimestamp();
        this.lastLocation = null;
        this.pendingLocation = null;
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }

    private void buildLocationRequest() {
        locationRequest = new LocationRequest.Builder(intervalMillis / 4)
                .setMinUpdateIntervalMillis(1000)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateDelayMillis(intervalMillis / 2)
                .build();
    }

    private void createLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null) return;
                lastLocation = result.getLastLocation();
                Log.d(TAG, "Location updated: " +
                        (lastLocation != null ?
                                lastLocation.getLatitude() + ", " + lastLocation.getLongitude() :
                                "null"));
            }
        };
    }

    private void startScheduledReporting() {
        cancelScheduledTasks();
        mainHandler.post(() -> {
            reportCurrentLocation();
            nextScheduledReportTime = System.currentTimeMillis() + intervalMillis;
            schedulePrefetchTask();
        });
        scheduledReportTask = reportScheduler.scheduleWithFixedDelay(() -> {
            long currentTime = System.currentTimeMillis();
            long actualDelay = currentTime - nextScheduledReportTime;
            if (Math.abs(actualDelay) > 1000) {
                Log.w(TAG, "Detected timing drift of " + actualDelay + "ms");
            }
            mainHandler.post(this::reportCurrentLocation);
            nextScheduledReportTime += intervalMillis;
            schedulePrefetchTask();
        }, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
        Log.d(TAG, "Scheduled location reporting started with interval: " + intervalMillis + "ms");
    }

    private void schedulePrefetchTask() {
        long timeUntilNextReport = nextScheduledReportTime - System.currentTimeMillis();
        long prefetchTime = Math.max(100, timeUntilNextReport - LOCATION_PREFETCH_OFFSET_MS);
        if (scheduledPrefetchTask != null && !scheduledPrefetchTask.isCancelled()) {
            scheduledPrefetchTask.cancel(false);
        }
        scheduledPrefetchTask = prefetchScheduler.schedule(this::prefetchLocation, prefetchTime, TimeUnit.MILLISECONDS);
        Log.d(TAG, "Location prefetch scheduled " + prefetchTime + "ms from now for next report at " + nextScheduledReportTime);
    }

    private void prefetchLocation() {
        if (!checkLocationPermission()) {
            Log.w(TAG, "Location permission not granted for prefetch.");
            return;
        }
        try {
            fusedLocationClient.getCurrentLocation(LocationRequest.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            pendingLocation = location;
                            Log.d(TAG, "Prefetched location for next report: " + location.getLatitude() + ", " + location.getLongitude());
                        } else {
                            Log.w(TAG, "Failed to prefetch location (null)");
                        }
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Error prefetching location: " + e.getMessage()));
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException during location prefetch: " + e.getMessage());
        }
    }

    private void reportCurrentLocation() {
        long currentTime = System.currentTimeMillis();
        long timeDifference = currentTime - nextScheduledReportTime;
        if (Math.abs(timeDifference) > 500) {
            Log.w(TAG, "Reporting time offset: " + timeDifference + "ms");
        }
        Location locationToReport = pendingLocation != null ? pendingLocation : lastLocation;
        if (locationToReport != null) {
            Log.d(TAG, "Reporting location: " + locationToReport.getLatitude() + ", " + locationToReport.getLongitude() + " at time: " + currentTime);
        } else {
            Log.d(TAG, "Reporting null location (no location available)");
        }
        Map<String, String> locationMap = LocationsInfo.buildLocationEventMap(locationToReport);
        EventOperationManager.getInstance(context).addEvent(
                ModuleCharacteristics.getInstance().getLocationEventCharacteristics(),
                new HashMap<>(locationMap));
        pendingLocation = null;
    }

    public void startLocationUpdates() {
        if (!checkLocationPermission()) {
            Log.w(TAG, "Location permission not granted.");
            return;
        }
        createLocationCallback();
        buildLocationRequest();
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, context.getMainLooper());
            startScheduledReporting();
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while requesting location updates: " + e.getMessage());
        }
    }

    public void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
        cancelScheduledTasks();
        reportScheduler.shutdown();
        prefetchScheduler.shutdown();
        Log.d(TAG, "Location updates stopped");
    }

    private void cancelScheduledTasks() {
        if (scheduledReportTask != null && !scheduledReportTask.isCancelled()) {
            scheduledReportTask.cancel(false);
        }
        if (scheduledPrefetchTask != null && !scheduledPrefetchTask.isCancelled()) {
            scheduledPrefetchTask.cancel(false);
        }
    }

    public void updateIntervalIfNeeded(long newIntervalMillis) {
        if (this.intervalMillis != newIntervalMillis) {
            Log.d(TAG, "Updating interval from " + intervalMillis + " to " + newIntervalMillis);
            this.intervalMillis = newIntervalMillis;
            stopLocationUpdates();
            buildLocationRequest();
            startLocationUpdates();
        } else {
            Log.d(TAG, "New interval is the same as current. No changes made.");
        }
    }

    public boolean checkLocationPermission() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
}
