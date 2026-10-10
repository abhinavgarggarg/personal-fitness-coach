package com.personalfitnesscoach.app.platform

import android.content.Context
import com.personalfitnesscoach.app.flow.Platform
import com.personalfitnesscoach.app.flow.StepReadingData
import com.personalfitnesscoach.data.android.StepSensor

/** What only the phone can do, for the app's flow: the step counter (D-078) and the rest-timer alerts. */
class AndroidPlatform(context: Context) : Platform {
    private val app = context.applicationContext
    private val sensor = StepSensor(app)

    override val stepCounterAvailable: Boolean get() = sensor.available

    override suspend fun readSteps(): StepReadingData? = sensor.read()?.let { StepReadingData(it.atMs, it.elapsedMs, it.counter, it.bootCount) }

    override fun cancelAlerts() = RestAlerts.cancel(app)

    override val alertsAllowed: Boolean get() = androidx.core.app.NotificationManagerCompat.from(app).areNotificationsEnabled()
}
