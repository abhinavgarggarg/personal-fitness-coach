package com.personalfitnesscoach.data.android

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager

/** D-062: the step counter is read only with a sensor and the permission; the reading carries the time it was counted. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StepSensorTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val manager get() = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    @Test fun `no sensor means no reading`() = runBlocking {
        shadowOf(app).grantPermissions(StepSensor.PERMISSION)
        val s = StepSensor(app)
        assertFalse(s.available)
        assertNull(s.read(200))
    }

    @Test fun `no permission means no reading`() = runBlocking {
        shadowOf(manager).addSensor(ShadowSensor.newInstance(Sensor.TYPE_STEP_COUNTER))
        shadowOf(app).denyPermissions(StepSensor.PERMISSION)
        val s = StepSensor(app)
        assertTrue(s.available)
        assertFalse(s.permissionGranted)
        assertNull(s.read(200))
    }

    @Test fun `a reading is the counter with the time it was counted`() = runBlocking {
        val sensor = ShadowSensor.newInstance(Sensor.TYPE_STEP_COUNTER)
        shadowOf(manager).addSensor(sensor)
        shadowOf(app).grantPermissions(StepSensor.PERMISSION)
        val s = StepSensor(app)
        val pending = async { s.read(2_000) }
        yield()
        val event = ShadowSensorManager.createSensorEvent(1)
        event.sensor = sensor
        event.values[0] = 12345f
        event.timestamp = SystemClock.elapsedRealtimeNanos()
        shadowOf(manager).sendSensorEventToListeners(event)
        val r = pending.await()
        assertNotNull(r)
        assertEquals(12345L, r!!.counter)
        assertEquals(event.timestamp / 1_000_000, r.elapsedMs)
        assertTrue(Math.abs(r.atMs - System.currentTimeMillis()) < 5_000)
    }
}
