package com.vitalflow.app.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.edit
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthConnectManager(
    private val context: Context,
) {
    private val client: HealthConnectClient? by lazy {
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // --- SHARED PREFS FOR SENSOR FALLBACK ---
    companion object {
        private const val PREFS_NAME = "vital_step_prefs"
        private const val KEY_BASELINE = "baseline_steps"
        private const val KEY_LAST_DATE = "last_step_date"
        private const val KEY_LAST_SENSOR_VAL = "last_sensor_value"
    }

    fun isAvailable(): Boolean = client != null || hasStepSensor()

    private fun hasStepSensor(): Boolean {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        return sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    }

    fun getRequiredPermissions(): Set<String> =
        setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getWritePermission(StepsRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getWritePermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        )

    suspend fun checkPermissions(): Set<String> = client?.permissionController?.getGrantedPermissions() ?: emptySet()

    // --- 1. READ STEPS (Hybrid: Health Connect -> Fallback to Sensor) ---
    fun readTodaySteps(callback: (Long) -> Unit) {
        scope.launch {
            var steps: Long? = null

            // Try Health Connect
            if (client != null) {
                try {
                    val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
                    val end = Instant.now()

                    val response =
                        client!!.aggregate(
                            AggregateRequest(
                                metrics = setOf(StepsRecord.COUNT_TOTAL),
                                timeRangeFilter = TimeRangeFilter.between(start, end),
                            ),
                        )
                    steps = response[StepsRecord.COUNT_TOTAL]
                } catch (e: Exception) {
                    Log.e("HealthConnect", "Error reading steps", e)
                }
            }

            // If found, return. If not (or 0), try Sensor.
            if (steps != null && steps > 0) {
                withContext(Dispatchers.Main) { callback(steps) }
            } else {
                readFromPhoneSensor(callback)
            }
        }
    }

    private fun readFromPhoneSensor(callback: (Long) -> Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val stepSensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        if (stepSensor == null) {
            callback(0)
            return
        }

        val handler = Handler(Looper.getMainLooper())
        handler.post {
            val listener =
                object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent?) {
                        event?.let {
                            val raw = it.values[0].toLong()
                            val daily = calculateDailySteps(raw)
                            callback(daily)
                        }
                        sm.unregisterListener(this)
                    }

                    override fun onAccuracyChanged(
                        sensor: Sensor?,
                        accuracy: Int,
                    ) {
                    }
                }
            sm.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun calculateDailySteps(raw: Long): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val savedDate = prefs.getString(KEY_LAST_DATE, "")
        var baseline = prefs.getLong(KEY_BASELINE, -1)
        val lastVal = prefs.getLong(KEY_LAST_SENSOR_VAL, -1)

        val rebooted = raw < lastVal

        if (today != savedDate) {
            // New Day
            baseline = if (!rebooted && lastVal > 0) lastVal else raw
            prefs
                .edit {
                    putString(KEY_LAST_DATE, today)
                        .putLong(KEY_BASELINE, baseline)
                }
        } else if (rebooted) {
            baseline = 0
            prefs.edit { putLong(KEY_BASELINE, 0) }
        }

        prefs.edit { putLong(KEY_LAST_SENSOR_VAL, raw) }
        return (raw - baseline).coerceAtLeast(0)
    }

    // --- 2. READ HEART RATE ---
    fun readAvgHeartRate(callback: (Long) -> Unit) {
        scope.launch {
            if (client == null) {
                withContext(Dispatchers.Main) { callback(0) }
                return@launch
            }
            try {
                val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
                val end = Instant.now()

                val response =
                    client!!.aggregate(
                        AggregateRequest(
                            metrics = setOf(HeartRateRecord.BPM_AVG),
                            timeRangeFilter = TimeRangeFilter.between(start, end),
                        ),
                    )
                val bpm = response[HeartRateRecord.BPM_AVG] ?: 0L
                withContext(Dispatchers.Main) { callback(bpm) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { callback(0) }
            }
        }
    }

    // --- 3. READ SLEEP & CALORIES ---
    fun readSleepAndCalories(callback: (Long, String) -> Unit) {
        scope.launch {
            if (client == null) {
                withContext(Dispatchers.Main) { callback(0, "0h 0m") }
                return@launch
            }
            try {
                val start = Instant.now().minus(java.time.Duration.ofDays(1))
                val end = Instant.now()
                val range = TimeRangeFilter.between(start, end)

                // Calories
                val calResponse =
                    client!!.aggregate(
                        AggregateRequest(
                            metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                            timeRangeFilter = range,
                        ),
                    )
                val calories =
                    calResponse[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.toLong()
                        ?: 0L

                // Sleep (Raw Read)
                val sleepResponse =
                    client!!.readRecords(
                        ReadRecordsRequest(
                            recordType = SleepSessionRecord::class,
                            timeRangeFilter = range,
                        ),
                    )

                var totalMins = 0L
                for (session in sleepResponse.records) {
                    val duration =
                        java.time.Duration
                            .between(session.startTime, session.endTime)
                            .toMinutes()
                    totalMins += duration
                }

                val sleepStr = "${totalMins / 60}h ${totalMins % 60}m"

                withContext(Dispatchers.Main) { callback(calories, sleepStr) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { callback(0, "0h 0m") }
            }
        }
    }

    fun checkPermissions(callback: (Set<String>) -> Unit) {
        scope.launch {
            val granted = client?.permissionController?.getGrantedPermissions() ?: emptySet()
            withContext(Dispatchers.Main) {
                callback(granted)
            }
        }
    }
}
