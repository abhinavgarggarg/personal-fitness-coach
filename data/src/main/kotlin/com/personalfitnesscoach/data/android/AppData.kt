package com.personalfitnesscoach.data.android

import android.content.Context
import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.time.SystemClock
import com.personalfitnesscoach.data.room.PfcDatabase
import com.personalfitnesscoach.data.room.RoomRowStore

/** The app's single data layer: one Room database in private storage, the system clock, the app's version for backups. */
object AppData {
    @Volatile private var instance: PfcData? = null

    fun get(context: Context, appVersion: String): PfcData = instance ?: synchronized(this) {
        instance ?: PfcData(RoomRowStore(PfcDatabase.open(context.applicationContext)), SystemClock(), appVersion).also { instance = it }
    }
}
