package com.personalfitnesscoach.app

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.personalfitnesscoach.app.flow.AppController
import com.personalfitnesscoach.app.flow.ExperienceBand
import com.personalfitnesscoach.app.flow.GymPreset
import com.personalfitnesscoach.app.flow.Screen
import com.personalfitnesscoach.app.flow.ScreeningQuestion
import com.personalfitnesscoach.app.platform.AndroidPlatform
import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.player.LiftEntry
import com.personalfitnesscoach.data.core.player.Step
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.data.core.time.FixedClock
import com.personalfitnesscoach.data.room.PfcDatabase
import com.personalfitnesscoach.data.room.RoomRowStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/**
 * Phase 4 Part 4 (NFR-02, NFR-03): planning today's session and logging sets on the phone itself, with the app's own database file
 * (write-ahead logging), through the same controller the screens use. The numbers are reported in the Devices run (tag PFC-PERF); the
 * test fails only at twice the targets, since emulators vary — the targets themselves are checked on your phone in UAT.
 */
@RunWith(AndroidJUnit4::class)
class SpeedOnDeviceTest {
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "speed-test.db"
    private var db: PfcDatabase? = null

    @After fun close() { db?.close(); ctx.deleteDatabase(name) }

    private fun ms(block: () -> Unit): Double { val t = System.nanoTime(); block(); return (System.nanoTime() - t) / 1e6 }
    private fun median(xs: List<Double>) = xs.sorted()[xs.size / 2]

    @Test fun planningAndLoggingAreQuickOnThePhone() {
        ctx.deleteDatabase(name)
        val clock = FixedClock(0, ZoneId.of("UTC")).also { it.setDay(Days.of(LocalDate.of(2026, 10, 5)), hour = 8) }
        val c = AppController({ PfcData(RoomRowStore(PfcDatabase.open(ctx, name).also { db = it }), clock, "device-test") }, AndroidPlatform(ctx), "device-test")
        runBlocking {
            c.start(); c.submitOnboarding()
            c.editOnboarding { it.copy(screening = ScreeningQuestion.entries.associateWith { q -> q == ScreeningQuestion.REGULARLY_ACTIVE }) }; c.submitOnboarding()
            c.editOnboarding { it.copy(birthYear = 1985, months = ExperienceBand.ONE_TO_3_YEARS, comfortable = setOf("squat", "hinge", "press", "row")) }; c.submitOnboarding()
            c.editOnboarding { it.copy(noneOfThese = true) }; c.submitOnboarding()
            c.submitOnboarding(); c.submitOnboarding()
            c.editOnboarding { it.copy(gym = c.preset(GymPreset.FULL_GYM)) }; c.submitOnboarding()
            repeat(4) { c.submitOnboarding() }
        }
        assertTrue(c.screen.value is Screen.Today)
        runBlocking { c.openCheckIn() }
        // The check-in's answer plans today's session (generation and the safety validator) and shows the preview.
        val plan = ms { runBlocking { c.submitCheckIn() } }
        assertTrue(c.screen.value is Screen.Preview)
        val start = ms { runBlocking { c.startWorkout() } }
        runBlocking { repeat(6) { val v = (c.screen.value as Screen.Workout).view; if (v.step !is Step.Lift) v.stage?.let { c.completeStage(it) } } }
        val logs = ArrayList<Double>()
        repeat(8) {
            val w = c.screen.value as? Screen.Workout ?: return@repeat
            val lift = (w.view.step as? Step.Lift)?.lift ?: return@repeat
            val t = lift.next ?: return@repeat
            logs += ms { runBlocking { c.log(lift.rowId, LiftEntry(t.load, t.reps.last, rir = 3.0), confirmed = true, expected = t) } }
            runBlocking { c.skipRest() }
        }
        val line = "plan today's session ${"%.0f".format(plan)} ms (target 500); start it ${"%.0f".format(start)} ms; " +
            "log a set median ${if (logs.isEmpty()) "-" else "%.1f".format(median(logs))} ms, slowest ${if (logs.isEmpty()) "-" else "%.1f".format(logs.max())} ms over ${logs.size} sets (target 100)"
        Log.i("PFC-PERF", line)
        assertTrue("no set was logged", logs.isNotEmpty())
        assertTrue(line, plan < 1000.0 && median(logs) < 200.0)
    }
}
