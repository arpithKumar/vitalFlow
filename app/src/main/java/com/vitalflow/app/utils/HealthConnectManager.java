package com.vitalflow.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import androidx.health.connect.client.HealthConnectClient;
import androidx.health.connect.client.aggregate.AggregateMetric;
import androidx.health.connect.client.aggregate.AggregationResult;
import androidx.health.connect.client.permission.HealthPermission;
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord;
import androidx.health.connect.client.records.HeartRateRecord;
import androidx.health.connect.client.records.SleepSessionRecord;
import androidx.health.connect.client.records.StepsRecord;
import androidx.health.connect.client.request.AggregateRequest;
import androidx.health.connect.client.request.ReadRecordsRequest;
import androidx.health.connect.client.response.ReadRecordsResponse;
import androidx.health.connect.client.time.TimeRangeFilter;
import androidx.health.connect.client.units.Energy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kotlin.coroutines.Continuation;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KClass;
import kotlinx.coroutines.BuildersKt;

public class HealthConnectManager {

    private final HealthConnectClient client;
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // SharedPreferences to store the "Start of Day" sensor value
    private static final String PREFS_NAME = "vital_step_prefs";
    private static final String KEY_BASELINE = "baseline_steps";
    private static final String KEY_LAST_DATE = "last_step_date";
    private static final String KEY_LAST_SENSOR_VAL = "last_sensor_value";

    public HealthConnectManager(Context context) {
        this.context = context;
        int status = HealthConnectClient.getSdkStatus(context);
        if (status == HealthConnectClient.SDK_AVAILABLE) {
            this.client = HealthConnectClient.getOrCreate(context);
        } else {
            this.client = null;
        }
    }

    public boolean isAvailable() {
        return client != null || hasStepSensor();
    }

    private boolean hasStepSensor() {
        SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        return sm != null && sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null;
    }

    public Set<String> getRequiredPermissions() {
        Set<String> permissions = new HashSet<>();
        if (client == null) return permissions;

        KClass<StepsRecord> stepsClass = JvmClassMappingKt.getKotlinClass(StepsRecord.class);
        permissions.add(HealthPermission.getReadPermission(stepsClass));
        permissions.add(HealthPermission.getWritePermission(stepsClass));

        KClass<HeartRateRecord> hrClass = JvmClassMappingKt.getKotlinClass(HeartRateRecord.class);
        permissions.add(HealthPermission.getReadPermission(hrClass));
        permissions.add(HealthPermission.getWritePermission(hrClass));

        permissions.add(HealthPermission.getReadPermission(JvmClassMappingKt.getKotlinClass(SleepSessionRecord.class)));
        permissions.add(HealthPermission.getReadPermission(JvmClassMappingKt.getKotlinClass(ActiveCaloriesBurnedRecord.class)));

        return permissions;
    }

    // --- HYBRID STEPS LOGIC (Health Connect + Sensor Fallback) ---
    public interface OnStepsReadListener {
        void onStepsRead(long steps);

        void onError(Throwable t);
    }

    public void readTodaySteps(OnStepsReadListener listener) {
        // 1. Try Health Connect First
        if (client != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    Instant startTime = LocalDateTime.now().toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant();
                    Instant endTime = Instant.now();

                    Set<AggregateMetric<?>> metrics = new HashSet<>();
                    metrics.add(StepsRecord.COUNT_TOTAL);

                    AggregateRequest request = new AggregateRequest(
                            metrics,
                            TimeRangeFilter.between(startTime, endTime),
                            Collections.emptySet()
                    );

                    AggregationResult result = runBlocking((scope, continuation) -> client.aggregate(request, continuation));
                    Long totalSteps = result.get(StepsRecord.COUNT_TOTAL);

                    if (totalSteps != null && totalSteps > 0) {
                        listener.onStepsRead(totalSteps);
                    } else {
                        // Health Connect returned 0 -> Trigger Fallback
                        readFromPhoneSensor(listener);
                    }
                } catch (Exception e) {
                    // Health Connect Failed -> Trigger Fallback
                    readFromPhoneSensor(listener);
                }
            }, executor);
        } else {
            // No Health Connect -> Go straight to Sensor
            readFromPhoneSensor(listener);
        }
    }

    private void readFromPhoneSensor(OnStepsReadListener listener) {
        SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);

        if (stepSensor == null) {
            listener.onStepsRead(0);
            return;
        }

        // Use main thread handler for SensorEventListener
        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        mainHandler.post(() -> {
            SensorEventListener sensorListener = new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent event) {
                    if (event != null && event.values.length > 0) {
                        long rawSteps = (long) event.values[0];
                        // Calculate DAILY steps based on baseline
                        long dailySteps = calculateDailySteps(rawSteps);
                        listener.onStepsRead(dailySteps);
                    }
                    // Unregister immediately to save battery (we just needed one read)
                    sensorManager.unregisterListener(this);
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {
                }
            };

            boolean registered = sensorManager.registerListener(sensorListener, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
            if (!registered) {
                listener.onStepsRead(0);
            }
        });
    }

    // --- LOGIC TO RESET STEPS DAILY ---
    private long calculateDailySteps(long currentRawSteps) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        String todayDate = LocalDate.now().toString();
        String savedDate = prefs.getString(KEY_LAST_DATE, "");
        long baseline = prefs.getLong(KEY_BASELINE, -1);

        long lastSensorVal = prefs.getLong(KEY_LAST_SENSOR_VAL, -1);

        boolean rebooted = currentRawSteps < lastSensorVal;

        if (!todayDate.equals(savedDate)) {
            // --- IT IS A NEW DAY ---

            if (!rebooted && lastSensorVal > 0) {
                baseline = lastSensorVal;
            } else {
                baseline = currentRawSteps;
            }

            // Save new date and baseline
            prefs.edit()
                    .putString(KEY_LAST_DATE, todayDate)
                    .putLong(KEY_BASELINE, baseline)
                    .apply();
        } else if (rebooted) {
            baseline = 0;
            prefs.edit().putLong(KEY_BASELINE, 0).apply();
        }

        prefs.edit().putLong(KEY_LAST_SENSOR_VAL, currentRawSteps).apply();
        long stepsToday = currentRawSteps - baseline;
        return Math.max(0, stepsToday);
    }

    // --- HEART RATE LOGIC ---
    public interface OnHeartRateReadListener {
        void onHeartRateRead(long avgBpm);

        void onError(Throwable t);
    }

    public void readAvgHeartRate(OnHeartRateReadListener listener) {
        if (client == null) return;

        CompletableFuture.runAsync(() -> {
            try {
                Instant startTime = LocalDateTime.now().toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant();
                Instant endTime = Instant.now();

                Set<AggregateMetric<?>> metrics = new HashSet<>();
                metrics.add(HeartRateRecord.BPM_AVG);

                AggregateRequest request = new AggregateRequest(metrics, TimeRangeFilter.between(startTime, endTime), Collections.emptySet());
                AggregationResult result = runBlocking((scope, continuation) -> client.aggregate(request, continuation));
                Long avgRate = result.get(HeartRateRecord.BPM_AVG);
                listener.onHeartRateRead(avgRate != null ? avgRate : 0);

            } catch (Exception e) {
                listener.onError(e);
            }
        }, executor);
    }

    // --- OTHER METRICS (Sleep/Calories) ---
    public interface OnDeepDataReadListener {
        void onDataRead(long calories, String sleepDuration);

        void onError(Throwable t);
    }

    public void readSleepAndCalories(OnDeepDataReadListener listener) {
        if (client == null) return;
        CompletableFuture.runAsync(() -> {
            try {
                Instant startTime = Instant.now().minus(java.time.Duration.ofDays(1));
                Instant endTime = Instant.now();
                TimeRangeFilter timeRange = TimeRangeFilter.between(startTime, endTime);

                Set<AggregateMetric<?>> metrics = new HashSet<>();
                metrics.add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL);
                AggregateRequest calReq = new AggregateRequest(metrics, timeRange, Collections.emptySet());
                AggregationResult calRes = runBlocking((scope, cont) -> client.aggregate(calReq, cont));
                Energy energy = calRes.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL);
                long calories = (energy != null) ? (long) energy.getKilocalories() : 0;

                ReadRecordsRequest<SleepSessionRecord> sleepReq = new ReadRecordsRequest<>(
                        JvmClassMappingKt.getKotlinClass(SleepSessionRecord.class),
                        timeRange, Collections.emptySet(), true, 10, null);

                ReadRecordsResponse<SleepSessionRecord> sleepRes = runBlocking((scope, cont) -> client.readRecords(sleepReq, cont));

                long totalMins = 0;
                for (SleepSessionRecord session : sleepRes.getRecords()) {
                    totalMins += java.time.Duration.between(session.getStartTime(), session.getEndTime()).toMinutes();
                }

                String formattedSleep = (totalMins / 60) + "h " + (totalMins % 60) + "m";
                listener.onDataRead(calories, formattedSleep);
            } catch (Exception e) {
                listener.onError(e);
            }
        }, executor);
    }

    // --- PERMISSION HELPERS ---
    public CompletableFuture<Set<String>> checkPermissions(Set<String> permissions) {
        if (client == null) return CompletableFuture.completedFuture(Collections.emptySet());
        return CompletableFuture.supplyAsync(() -> {
            try {
                return runBlocking((scope, continuation) -> client.getPermissionController().getGrantedPermissions(continuation));
            } catch (Exception e) {
                return Collections.emptySet();
            }
        }, executor);
    }

    private <T> T runBlocking(KotlinSuspendFunction<T> function) throws InterruptedException {
        return BuildersKt.runBlocking(
                EmptyCoroutineContext.INSTANCE,
                (scope, continuation) -> function.invoke(scope, continuation)
        );
    }

    interface KotlinSuspendFunction<T> {
        Object invoke(Object scope, Continuation<? super T> continuation);
    }
}