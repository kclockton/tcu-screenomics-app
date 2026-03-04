package edu.stanford.communication.screenomics.activites;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import edu.stanford.communication.screenomics.TextBasedData.EventOperationManager;
import edu.stanford.communication.screenomics.TextBasedData.HashMapPool;
import edu.stanford.communication.screenomics.modulemanager.ModuleCharacteristics;

/**
 * Updates step count at a consistent interval. Same pattern as SpecsUpdater, LocationsUpdater.
 * Uses ActivitiesInfo for event payload. Owned by ActivitiesCollectionController.
 */
public class ActivitiesUpdater implements SensorEventListener {

    private static final String TAG = "ActivitiesUpdater";
    private final SensorManager sensorManager;
    private final Sensor stepCounterSensor;
    private int totalSteps = 0;
    private int previousTotalSteps = 0;
    private final Context context;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> scheduledTask;
    private long intervalMillis;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ActivitiesUpdater(Context context, long intervalMillis) {
        this.context = context;
        this.intervalMillis = intervalMillis;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        } else {
            stepCounterSensor = null;
            Log.e(TAG, "SensorManager is not available");
        }
    }

    public void start() {
        if (stepCounterSensor != null) {
            sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL);
            startScheduledReporting();
            Log.d(TAG, "Step counter started with interval: " + intervalMillis + "ms");
        } else {
            Log.e(TAG, "Step counter sensor not available on this device");
            startScheduledReporting();
        }
    }

    public void stop() {
        if (stepCounterSensor != null) {
            sensorManager.unregisterListener(this);
        }
        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            scheduledTask.cancel(false);
            Log.d(TAG, "Step counter stopped");
        }
        scheduler.shutdown();
    }

    private void startScheduledReporting() {
        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            scheduledTask.cancel(false);
        }
        scheduledTask = scheduler.scheduleWithFixedDelay(() -> {
            int currentSteps = totalSteps - previousTotalSteps;
            Log.d(TAG, "Steps in interval: " + currentSteps);
            mainHandler.post(() -> sendStepsToPipeline(currentSteps));
            previousTotalSteps = totalSteps;
        }, 0, intervalMillis, TimeUnit.MILLISECONDS);
    }

    public void updateIntervalIfNeeded(long newIntervalMillis) {
        if (this.intervalMillis != newIntervalMillis) {
            Log.d(TAG, "Updating interval from " + this.intervalMillis + " to " + newIntervalMillis);
            this.intervalMillis = newIntervalMillis;
            startScheduledReporting();
        } else {
            Log.d(TAG, "New interval is the same as current. No changes made.");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            totalSteps = (int) event.values[0];
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(TAG, "Step sensor accuracy changed to: " + accuracy);
    }

    private void sendStepsToPipeline(int steps) {
        Map<String, String> map = ActivitiesInfo.buildStepCountEventMap(steps);
        HashMap<String, String> stepsCount = HashMapPool.getMap();
        stepsCount.putAll(map);
        EventOperationManager.getInstance(context).addEvent(
                ModuleCharacteristics.getInstance().getStepCountEventCharacteristics(),
                stepsCount);
        HashMapPool.releaseMap(stepsCount);
    }
}
