package com.personalfitnesscoach.app

import android.app.Application
import com.personalfitnesscoach.BuildConfig
import com.personalfitnesscoach.app.flow.AppController
import com.personalfitnesscoach.app.platform.AndroidPlatform
import com.personalfitnesscoach.app.platform.RestAlerts
import com.personalfitnesscoach.app.ui.Actions
import com.personalfitnesscoach.data.android.AppData
import com.personalfitnesscoach.data.core.PfcData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The app process. The data layer opens lazily, the first time a screen needs it; nothing runs in the background (NFR-05). The
 * controller and its scope live here, so turning the phone or recreating the screen never loses a tap or the workout's state.
 */
class PfcApplication : Application() {
    val data: PfcData by lazy { AppData.get(this, BuildConfig.VERSION_NAME) }

    /** Taps are handled off the main thread (database and engine work), one at a time (AppController). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val controller: AppController by lazy { AppController({ data }, AndroidPlatform(this), BuildConfig.VERSION_NAME) }

    val actions: Actions by lazy { Actions(scope, controller) }

    override fun onCreate() {
        super.onCreate()
        RestAlerts.createChannel(this)
    }
}
