package com.personalfitnesscoach.app

import android.app.Application
import com.personalfitnesscoach.BuildConfig
import com.personalfitnesscoach.data.android.AppData
import com.personalfitnesscoach.data.core.PfcData

/** The app process. The data layer opens lazily, the first time a screen needs it (nothing runs in the background). */
class PfcApplication : Application() {
    val data: PfcData by lazy { AppData.get(this, BuildConfig.VERSION_NAME) }
}
