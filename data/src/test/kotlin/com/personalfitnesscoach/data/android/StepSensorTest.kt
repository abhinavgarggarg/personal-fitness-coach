package com.personalfitnesscoach.data.android

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor

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

    @Test fun `a reading is the counter with the time it was counted`() {
        // Counted 2 s before now (event timestamp 98 s after boot, now 100 s after boot).
        val r = StepSensor.reading(98_000_000_000L, 12345f, nowMs = 1_000_000L, elapsedNowMs = 100_000L, bootCount = 4)
        assertEquals(StepReading(998_000L, 98_000L, 12345L, 4), r)
    }

    @Test fun `registering on a phone with a step counter and permission waits for an event, then gives up in time`() = runBlocking {
        shadowOf(manager).addSensor(ShadowSensor.newInstance(Sensor.TYPE_STEP_COUNTER))
        shadowOf(app).grantPermissions(StepSensor.PERMISSION)
        val s = StepSensor(app)
        assertTrue(s.available && s.permissionGranted)
        assertNull("no event arrives under Robolectric: the read times out instead of hanging", s.read(300))
    }
}
