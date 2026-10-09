package com.personalfitnesscoach.data.room

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * The app's one database (Phase 2 section 7, decision D-073). Schema version 1; every later change ships an explicit, tested
 * migration — there is no destructive fallback anywhere (Phase 2 data rule 2; CI checks the source for it).
 *
 * Foreign keys: Room's generated open code turns `PRAGMA foreign_keys` on for every connection because the entities declare
 * foreign keys; RoomRowStoreTest proves enforcement and cascades rather than trusting that.
 */
@Database(entities = [DocEntity::class, WorkoutEntity::class, ExerciseEntity::class, SetEntity::class, ActiveEntity::class], version = 1, exportSchema = true)
abstract class PfcDatabase : RoomDatabase() {
    abstract fun dao(): PfcDao

    companion object {
        const val FILE_NAME = "pfc.db"

        /**
         * Opens the database in the app's private storage on the phone's own SQLite (no bundled SQLite in the APK, NFR-10),
         * with write-ahead logging (Phase 2 data rule 4).
         */
        fun open(context: Context, name: String = FILE_NAME): PfcDatabase =
            Room.databaseBuilder<PfcDatabase>(context.applicationContext, name)
                .setDriver(AndroidSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()

        /** An in-memory database for tests (same schema and rules). */
        fun inMemory(context: Context): PfcDatabase =
            Room.inMemoryDatabaseBuilder<PfcDatabase>(context.applicationContext)
                .setDriver(AndroidSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
    }
}
