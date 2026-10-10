package com.personalfitnesscoach.app

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.personalfitnesscoach.data.android.StepSensor
import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.model.ConditionsRecord
import com.personalfitnesscoach.data.core.model.EquipmentRecord
import com.personalfitnesscoach.data.core.model.ExerciseState
import com.personalfitnesscoach.data.core.model.PreferencesRecord
import com.personalfitnesscoach.data.core.model.Profile
import com.personalfitnesscoach.data.core.model.ScreeningRecord
import com.personalfitnesscoach.data.core.session.NewSet
import com.personalfitnesscoach.data.core.session.SetKind
import com.personalfitnesscoach.data.core.time.FixedClock
import com.personalfitnesscoach.data.room.PfcDatabase
import com.personalfitnesscoach.data.room.RoomRowStore
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.program.Goal
import com.personalfitnesscoach.engine.safety.ScreeningAnswers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * The data layer on real Android versions (device matrix, Android 10–16): Room on the phone's own SQLite, a planned and logged
 * workout feeding the next prescription, the encrypted backup with the platform's PBKDF2 and AES-GCM, and the step counter staying
 * silent without permission.
 */
@RunWith(AndroidJUnit4::class)
class DataOnDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun aWorkoutIsPlannedLoggedAndBackedUpOnTheDevice() = runBlocking {
        val name = "device-test-${System.nanoTime()}.db"
        val db = PfcDatabase.open(ctx, name)
        try {
            val clock = FixedClock(0).also { it.setDay(LocalDate.of(2026, 10, 5).toEpochDay().toInt()) }
            val d = PfcData(RoomRowStore(db), clock, "device-test")
            val today = clock.today()
            d.docs.put(Profile, Profile(birthYear = 1980, level = Level.INTERMEDIATE, daysPerWeek = 3, priorities = listOf(Goal.STRENGTH), createdDay = today))
            d.docs.put(ScreeningRecord, ScreeningRecord(ScreeningAnswers(false, false, false, false, false, false, false, true), today))
            d.docs.put(ConditionsRecord, ConditionsRecord(emptyList(), today))
            d.docs.put(EquipmentRecord, EquipmentRecord(Library.all.flatMap { it.allEquipment }.toSet()))
            d.docs.put(PreferencesRecord, PreferencesRecord())
            d.startProgramIfNeeded()
            val t = d.planToday()!!
            val day = t.next ?: t.week.days.first()
            val w = d.generate(t, day, Tier.FULL).value
            val id = d.sessions.start(w, day.template, day.weekday)
            val stored = d.sessions.load(id)!!
            for (e in stored.exercises) {
                val item = e.doc
                if (item.calibrating && item.load != null) d.sessions.logSet(e.row.id, NewSet(0, SetKind.CALIBRATION, item.load, (item.reps.first + item.reps.last) / 2, rir = 3.0), "{}")
                else repeat(item.sets) { i -> d.sessions.logSet(e.row.id, NewSet(i, SetKind.WORKING, item.load, item.reps.first, rir = 2.0), "{\"set\":$i}") }
            }
            d.finishWorkout(id, 6.0, w.plannedMinutes, w.conditioning.map { it.workMinutes })
            assertTrue(d.docs.all(ExerciseState).isNotEmpty())
            assertNull(d.sessions.active())

            val before = d.store.snapshot()
            val bytes = d.backup.export("device password".toCharArray()).second
            d.eraseAll()
            assertTrue(d.store.snapshot().isEmpty)
            d.restore(d.backup.inspect(bytes, "device password".toCharArray())) { _, _ -> }
            assertEquals(before, d.store.snapshot())
        } finally {
            db.close()
            ctx.deleteDatabase(name)
        }
    }

    /** "Erase all my data" leaves nothing readable in the database file or its write-ahead log (Part 4 re-check finding 10). */
    @Test fun erasingEverythingLeavesNoTraceOnTheDevice() = runBlocking {
        val name = "device-erase-${System.nanoTime()}.db"
        val db = PfcDatabase.open(ctx, name)
        try {
            val store = RoomRowStore(db)
            val marker = "ERASE-MARKER-${System.nanoTime()}"
            repeat(300) { i -> store.putDoc(com.personalfitnesscoach.data.core.store.DocRow("probe", "k$i", null, "{\"m\":\"$marker-$i\"}", 1L)) }
            store.eraseAll()
            db.close()
            val files = listOf(name, "$name-wal", "$name-journal").map { ctx.getDatabasePath(it) }.filter { it.exists() }
            assertTrue(files.isNotEmpty())
            for (f in files) assertTrue("${f.name} still holds erased data", !String(f.readBytes(), Charsets.ISO_8859_1).contains(marker))
        } finally {
            ctx.deleteDatabase(name)
        }
    }

    @Test fun theStepCounterIsSilentWithoutPermission() = runBlocking {
        val s = StepSensor(ctx)
        assertEquals(PackageManager.PERMISSION_DENIED, ctx.checkSelfPermission(StepSensor.PERMISSION))
        assertNull(s.read(500))
    }

    @Test fun theAppDataOpensInPrivateStorage() {
        val app = ctx.applicationContext as PfcApplication
        assertNotNull(app.data)
        assertTrue(ctx.getDatabasePath(PfcDatabase.FILE_NAME).parentFile!!.absolutePath.startsWith(ctx.applicationInfo.dataDir))
    }
}
