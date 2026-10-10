package com.personalfitnesscoach.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.personalfitnesscoach.app.flow.AppController
import com.personalfitnesscoach.app.flow.ConditionAnswers
import com.personalfitnesscoach.app.flow.ExperienceBand
import com.personalfitnesscoach.app.flow.GymPreset
import com.personalfitnesscoach.app.flow.PainForm
import com.personalfitnesscoach.app.flow.Platform
import com.personalfitnesscoach.app.flow.Screen
import com.personalfitnesscoach.app.flow.ScreeningQuestion
import com.personalfitnesscoach.app.flow.StepReadingData
import com.personalfitnesscoach.app.ui.theme.PfcTheme
import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.player.LiftEntry
import com.personalfitnesscoach.data.core.player.Step
import com.personalfitnesscoach.data.core.session.SetKind
import com.personalfitnesscoach.data.core.session.Stage
import com.personalfitnesscoach.data.core.store.InMemoryRowStore
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.data.core.time.FixedClock
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.safety.ControlStatus
import com.personalfitnesscoach.engine.safety.PainKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pictures of every Part 5 screen for review (the Screenshots workflow commits them to docs/phase3/screens). Runs only when
 * PFC_SCREENSHOTS names an output folder; otherwise it is skipped. The screen is a phone's width and tall enough to show a whole
 * scrolling screen in one picture.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp-hdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotsTest {
    @get:Rule val compose = createComposeRule()
    private val monday = Days.of(LocalDate.of(2026, 10, 5))

    private class NoPlatform : Platform {
        override val stepCounterAvailable = true
        override suspend fun readSteps(): StepReadingData? = null
        override fun cancelAlerts() = Unit
    }

    private lateinit var out: File
    private var n = 0

    private fun shot(name: String) {
        compose.waitForIdle()
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        n++
        File(out, "%02d-%s.png".format(n, name)).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun everyScreen() {
        val dir = System.getenv("PFC_SCREENSHOTS")
        assumeTrue("set PFC_SCREENSHOTS to an output folder", !dir.isNullOrBlank())
        out = File(dir!!).also { it.mkdirs() }
        val clock = FixedClock(0, ZoneId.of("UTC")).also { it.setDay(monday, hour = 8) }
        val c = AppController({ PfcData(InMemoryRowStore(), clock, "1.0.0") }, NoPlatform(), "1.0.0")
        val actions = Actions(CoroutineScope(SupervisorJob()), c, Dispatchers.Main)
        compose.setContent { PfcTheme { PfcApp(actions) } }
        fun step(block: suspend AppController.() -> Unit) { runBlocking { c.block() }; compose.waitForIdle() }

        step { start() }
        shot("welcome")
        step { submitOnboarding() }
        step { editOnboarding { it.copy(screening = ScreeningQuestion.entries.associateWith { q -> q == ScreeningQuestion.REGULARLY_ACTIVE }) } }
        shot("screening")
        step { submitOnboarding() }
        step { editOnboarding { it.copy(birthYear = 1984, months = ExperienceBand.ONE_TO_3_YEARS, comfortable = setOf("squat", "hinge", "press", "row"), bodyweightKg = 84.0) } }
        shot("about-you")
        step { submitOnboarding() }
        step { editOnboarding { it.copy(conditions = mapOf(AppController.HBP to ConditionAnswers(status = ControlStatus.YES), "oa_knee" to ConditionAnswers())) } }
        shot("conditions")
        step { editOnboarding { it.copy(conditions = emptyMap(), noneOfThese = true) } }
        step { submitOnboarding() }
        shot("goal")
        step { submitOnboarding() }
        shot("schedule")
        step { submitOnboarding() }
        step { editOnboarding { it.copy(gym = preset(GymPreset.FULL_GYM), gymPreset = GymPreset.FULL_GYM) } }
        shot("equipment")
        step { submitOnboarding() }
        shot("injuries")
        step { submitOnboarding() }
        shot("own-numbers")
        step { submitOnboarding() }
        shot("plan")
        step { submitOnboarding() }
        shot("alerts")
        step { submitOnboarding() }
        shot("today")
        step { openCheckIn() }
        step { editCheckIn { it.copy(sleep = 4, energy = 4) } }
        shot("check-in")
        step { editCheckIn { it.copy(pain = PainForm(Joint.KNEE, PainKind.JOINT_OR_TENDON, 2)) } }
        shot("check-in-pain")
        step { editCheckIn { it.copy(pain = null) } }
        step { submitCheckIn() }
        shot("preview")
        step { startWorkout() }
        shot("workout-warmup")
        step { completeStage(Stage.WARMUP) }
        var guard = 0
        while ((c.screen.value as Screen.Workout).view.step !is Step.Lift && guard++ < 6) {
            when (val st = (c.screen.value as Screen.Workout).view.step) {
                is Step.Conditioning -> step { logConditioning(st.index, st.item.workMinutes) }
                is Step.BoneLoading -> step { completeStage(Stage.BONE_LOADING) }
                else -> break
            }
        }
        shot("workout-first-set")
        val lift = ((c.screen.value as Screen.Workout).view.step as Step.Lift).lift
        val t = lift.next!!
        if (t.kind == SetKind.WARMUP) step { skipRamp(lift.rowId) }
        val t2 = ((c.screen.value as Screen.Workout).view.step as Step.Lift).lift.next!!
        step { log(lift.rowId, LiftEntry(t2.load, t2.reps.first, rir = 4.0), confirmed = true) }
        shot("workout-after-a-set")
        step { openReplace(lift.rowId, occupied = true) }
        shot("sheet-busy")
        step { closeSheet() }
        step { openHurts(lift.rowId) }
        step { editHurts { it.copy(region = Joint.KNEE, kind = PainKind.JOINT_OR_TENDON, rating = 5) } }
        shot("sheet-something-hurts")
        step { submitHurts() }
        shot("sheet-pain-result")
        step { closeSheet() }
        step { openChangeTime() }
        shot("sheet-change-time")
        step { closeSheet() }
        step { openRedFlag() }
        shot("sheet-warning-signs")
        step { closeSheet() }
        step { openEnd() }
        shot("sheet-end")
        step { finishWorkout(early = true) }
        shot("summary")
        step { closeToToday() }
        step { openSettings() }
        shot("settings")
        // A red flag at Wednesday's check-in (SAF-002): the stop, then Today keeping it until the user confirms.
        clock.setDay(monday + 2, hour = 8)
        step { closeSettings() }
        step { openCheckIn() }
        step { editCheckIn { it.copy(redFlags = setOf("chest_pain_pressure_tightness")) } }
        step { submitCheckIn() }
        shot("safety-stop")
        step { closeToToday() }
        shot("today-paused")
    }

}
