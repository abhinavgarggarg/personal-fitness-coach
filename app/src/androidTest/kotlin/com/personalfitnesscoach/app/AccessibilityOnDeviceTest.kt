package com.personalfitnesscoach.app

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityViewCheckResult
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
import com.personalfitnesscoach.app.flow.AppController
import com.personalfitnesscoach.app.flow.ExperienceBand
import com.personalfitnesscoach.app.flow.GymPreset
import com.personalfitnesscoach.app.flow.Screen
import com.personalfitnesscoach.app.flow.ScreeningQuestion
import com.personalfitnesscoach.app.flow.Tab
import com.personalfitnesscoach.app.platform.AndroidPlatform
import com.personalfitnesscoach.app.ui.Actions
import com.personalfitnesscoach.app.ui.PfcApp
import com.personalfitnesscoach.app.ui.theme.PfcTheme
import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.player.LiftEntry
import com.personalfitnesscoach.data.core.player.Step
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.data.core.time.FixedClock
import com.personalfitnesscoach.data.room.PfcDatabase
import com.personalfitnesscoach.data.room.RoomRowStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Phase 4 Part 4 (NFR-09, D-120): Android's own accessibility checks (the Accessibility Test Framework that Google Play's pre-launch
 * report uses) on every main screen, on each emulator of the Devices matrix: a name a screen reader can read on every control, touch
 * targets, text contrast measured on the real pixels, and duplicate or misleading labels. Every result is listed in the test log
 * (tag PFC-A11Y); an error fails the test.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityOnDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbs = ArrayList<PfcDatabase>()
    private val results = LinkedHashSet<String>()
    private val errors = LinkedHashSet<String>()
    private var screen = "start"

    @After fun close() { dbs.forEach { it.close() } }

    private fun controller(): AppController {
        val clock = FixedClock(0, ZoneId.of("UTC")).also { it.setDay(Days.of(LocalDate.of(2026, 10, 5)), hour = 8) }
        return AppController({ PfcData(RoomRowStore(PfcDatabase.inMemory(ctx).also { dbs += it }), clock, "device-test") }, AndroidPlatform(ctx), "device-test")
    }

    private fun describe(r: AccessibilityViewCheckResult): String {
        val e = r.element
        val label = listOfNotNull(e?.text?.toString(), e?.contentDescription?.toString()).joinToString(" / ").take(60)
        return "$screen | ${r.type} | ${r.accessibilityHierarchyCheck?.simpleName} | ${r.getMessage(Locale.ENGLISH).toString().take(240)} | $label"
    }

    /** Runs the checks on the whole window once the screen has settled. */
    private fun check(name: String) {
        screen = name
        compose.waitForIdle()
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    private fun workout(): Screen.Workout = c.screen.value as Screen.Workout
    private lateinit var c: AppController

    @Test fun theMainScreensPassAndroidsAccessibilityChecks() {
        val validator = AccessibilityValidator().setRunChecksFromRootView(true).setCaptureScreenshots(true).setThrowExceptionFor(null)
            .addCheckListener(object : AccessibilityValidator.AccessibilityCheckListener {
                override fun onResults(context: Context, found: List<AccessibilityViewCheckResult>) {
                    for (r in found) {
                        if (r.type != AccessibilityCheckResultType.ERROR && r.type != AccessibilityCheckResultType.WARNING) continue
                        val line = describe(r)
                        results += line
                        if (r.type == AccessibilityCheckResultType.ERROR) errors += line
                    }
                }
            })
        compose.enableAccessibilityChecks(validator)
        c = controller()
        val actions = Actions(CoroutineScope(SupervisorJob()), c, doubleTapMs = 0)
        compose.setContent { PfcTheme { PfcApp(actions) } }
        runBlocking { c.start() }; check("Onboarding: welcome")
        runBlocking { c.submitOnboarding() }; check("Onboarding: screening")
        runBlocking {
            c.editOnboarding { it.copy(screening = ScreeningQuestion.entries.associateWith { q -> q == ScreeningQuestion.REGULARLY_ACTIVE }) }; c.submitOnboarding()
        }
        check("Onboarding: about you")
        runBlocking {
            c.editOnboarding { it.copy(birthYear = 1985, months = ExperienceBand.ONE_TO_3_YEARS, comfortable = setOf("squat", "hinge", "press", "row")) }; c.submitOnboarding()
        }
        check("Onboarding: health")
        runBlocking { c.editOnboarding { it.copy(noneOfThese = true) }; c.submitOnboarding(); c.submitOnboarding(); c.submitOnboarding() }
        check("Onboarding: equipment")
        runBlocking { c.editOnboarding { it.copy(gym = c.preset(GymPreset.FULL_GYM)) }; c.submitOnboarding(); repeat(4) { c.submitOnboarding() } }
        assertTrue(c.screen.value is Screen.Today)
        check("Today")
        runBlocking { c.openCheckIn() }; check("Check-in")
        runBlocking { c.submitCheckIn() }; check("Preview")
        runBlocking { c.startWorkout() }; check("Workout: first part")
        runBlocking { repeat(6) { val v = workout().view; if (v.step !is Step.Lift) v.stage?.let { c.completeStage(it) } } }
        check("Workout: a set")
        val lift = (workout().view.step as? Step.Lift)?.lift
        val t = lift?.next
        if (lift != null && t != null) {
            runBlocking { c.log(lift.rowId, LiftEntry(t.load, t.reps.last, rir = 3.0), confirmed = true, expected = t) }
            check("Workout: rest or next set")
        }
        runBlocking { c.openHurts(lift?.rowId) }; check("Something hurts")
        runBlocking { c.closeSheet(); c.openEnd() }; check("End the session")
        runBlocking { c.discardWorkout() }
        runBlocking { c.openTab(Tab.HISTORY) }; check("History")
        runBlocking { c.openTab(Tab.PROGRESS) }; check("Progress")
        runBlocking { c.openTab(Tab.COACH) }; check("Coach")
        runBlocking { c.openSettings() }; check("Settings")

        for (line in results) Log.i("PFC-A11Y", line)
        Log.i("PFC-A11Y", "${results.size} findings, ${errors.size} errors")
        assertTrue("Accessibility errors (${errors.size}):\n" + errors.joinToString("\n"), errors.isEmpty())
    }
}
