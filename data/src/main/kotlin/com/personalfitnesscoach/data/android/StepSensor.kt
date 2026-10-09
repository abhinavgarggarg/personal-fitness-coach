package com.personalfitnesscoach.data.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.model.SettingsRecord
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** One reading of the step counter: the running total since the phone restarted, and when it was counted. */
data class StepReading(val atMs: Long, val elapsedMs: Long, val counter: Long)

/**
 * The phone's built-in step counter (D-062, CR-005). Read once when the app opens or returns to the foreground — no background
 * service, no Health Connect, no other app's data. Needs the ACTIVITY_RECOGNITION runtime permission, which the app asks for only
 * when the user switches step tracking on; without it (or on a phone with no step counter) nothing is read and the app works fully.
 */
class StepSensor(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager?
    private val sensor: Sensor? get() = manager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    /** The phone has a step counter. */
    val available: Boolean get() = sensor != null

    val permissionGranted: Boolean get() = app.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** The current counter value, or null without a sensor, without permission, or when no value arrives in time. */
    suspend fun read(timeoutMs: Long = 3_000): StepReading? {
        val s = sensor ?: return null
        val m = manager ?: return null
        if (!permissionGranted) return null
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        m.unregisterListener(this)
                        if (!cont.isActive) return
                        // event.timestamp is when the value was counted (nanoseconds since boot); convert it to wall-clock time.
                        val elapsedMs = event.timestamp / 1_000_000
                        val atMs = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - elapsedMs)
                        cont.resume(StepReading(atMs, elapsedMs, event.values[0].toLong()))
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                cont.invokeOnCancellation { m.unregisterListener(listener) }
                if (!m.registerListener(listener, s, SensorManager.SENSOR_DELAY_NORMAL)) {
                    m.unregisterListener(listener)
                    cont.resume(null)
                }
            }
        }
    }

    companion object {
        const val PERMISSION = Manifest.permission.ACTIVITY_RECOGNITION
    }
}

/** Reads the step counter into the day totals when tracking is on and allowed; returns whether a reading was stored. */
suspend fun syncSteps(data: PfcData, sensor: StepSensor): Boolean {
    if (data.docs.get(SettingsRecord)?.stepTracking != true) return false
    val r = sensor.read() ?: return false
    data.recordStepReading(r.atMs, r.elapsedMs, r.counter)
    return true
}
