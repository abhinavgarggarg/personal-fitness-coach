package com.personalfitnesscoach.app.ui

import android.content.Context
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.personalfitnesscoach.R
import com.personalfitnesscoach.app.flow.AppController
import com.personalfitnesscoach.app.flow.ExperienceBand
import com.personalfitnesscoach.app.flow.GymPreset
import com.personalfitnesscoach.app.flow.Platform
import com.personalfitnesscoach.app.flow.Screen
import com.personalfitnesscoach.app.flow.ScreeningQuestion
import com.personalfitnesscoach.app.flow.StepReadingData
import com.personalfitnesscoach.app.ui.theme.PfcTheme
import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.onboarding.OnboardingStep
import com.personalfitnesscoach.data.core.player.Step
import com.personalfitnesscoach.data.core.session.SetKind
import com.personalfitnesscoach.data.core.store.InMemoryRowStore
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.data.core.time.FixedClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * The real screens on the JVM (Robolectric): every tap goes through the same controller and data layer as on the phone (in memory
 * here). The clock is a fixed Monday in the past, so rest countdowns are already over and nothing waits on real time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScreensTest {
    @get:Rule val compose = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private fun s(id: Int, vararg args: Any): String = ctx.getString(id, *args)
    private val monday = Days.of(LocalDate.of(2026, 10, 5))

    private class FakePlatform : Platform {
        override val stepCounterAvailable = false
        override suspend fun readSteps(): StepReadingData? = null
        override fun cancelAlerts() = Unit
    }

    private fun launch(prepare: suspend (AppController) -> Unit = {}): AppController {
        val clock = FixedClock(0, ZoneId.of("UTC")).also { it.setDay(monday, hour = 8) }
        val store = InMemoryRowStore()
        val c = AppController({ PfcData(store, clock, "test") }, FakePlatform(), "test")
        runBlocking { prepare(c) }
        val actions = Actions(CoroutineScope(SupervisorJob()), c, Dispatchers.Main, doubleTapMs = 0)
        compose.setContent { PfcTheme { PfcApp(actions) } }
        compose.waitForIdle()
        return c
    }

    /** A control's words, or the spoken name of an icon-only control (the tab bar, the settings button; D-099). */
    private fun named(text: String, substring: Boolean = false) = hasText(text, substring = substring) or hasContentDescription(text, substring = substring)

    /** Taps the n-th node with this text, scrolling to it first when it sits in a scrolling screen (dialogs don't scroll). */
    private fun tap(text: String, index: Int = 0) {
        val n = compose.onAllNodes(named(text))[index]
        runCatching { n.performScrollTo() }
        n.performClick()
        compose.waitForIdle()
    }

    private fun shown(text: String, substring: Boolean = false) {
        val n = compose.onAllNodes(named(text, substring))[0]
        runCatching { n.performScrollTo() }
        n.assertIsDisplayed()
    }

    /** NFR-09: every tappable control is at least 48 dp in both directions (Android's minimum; primary actions are 56). */
    private fun assertTouchTargets() {
        val nodes: List<SemanticsNode> = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue(nodes.isNotEmpty())
        for (n in nodes) {
            val d = n.layoutInfo.density.density
            assertTrue("target ${n.size} at density $d: ${n.config}", n.size.height / d >= 47.5f && n.size.width / d >= 47.5f)
        }
    }

    private suspend fun onboardQuickly(c: AppController) {
        c.start()
        c.submitOnboarding()
        c.editOnboarding { it.copy(screening = ScreeningQuestion.entries.associateWith { q -> q == ScreeningQuestion.REGULARLY_ACTIVE }) }
        c.submitOnboarding()
        c.editOnboarding { it.copy(birthYear = 1985, months = ExperienceBand.ONE_TO_3_YEARS, comfortable = setOf("squat", "hinge", "press", "row")) }
        c.submitOnboarding()
        c.editOnboarding { it.copy(noneOfThese = true) }
        c.submitOnboarding()
        c.submitOnboarding() // goal: the suggested one
        c.submitOnboarding() // schedule: defaults
        c.editOnboarding { it.copy(gym = c.preset(GymPreset.FULL_GYM), gymPreset = GymPreset.FULL_GYM) }
        c.submitOnboarding()
        repeat(4) { c.submitOnboarding() } // limitations, numbers, plan, alerts
    }

    @Test fun `J1 onboarding through the screens reaches Today with a big Start button`() {
        val c = launch()
        shown(s(R.string.onb_welcome_title))
        assertTouchTargets()
        tap(s(R.string.onb_welcome_accept))
        // PO feedback (UAT): required questions carry a star, with a legend.
        shown(s(R.string.onb_required_legend))
        shown(s(R.string.onb_required_mark, s(com.personalfitnesscoach.app.ui.text.Labels.screening(ScreeningQuestion.entries.first()))), substring = true)
        // SAF-001: Continue stays off until all eight are answered.
        val yes = s(R.string.action_yes)
        val no = s(R.string.action_no)
        for (i in 0 until 7) tap(no, i)
        tap(yes, 7)
        assertTouchTargets()
        tap(s(R.string.action_continue))
        assertEquals(OnboardingStep.ABOUT_YOU, (c.screen.value as Screen.Onboarding).step)
        shown(s(R.string.screen_result_standard))
        compose.onNodeWithText(s(R.string.onb_required_mark, s(R.string.onb_birth_year))).performScrollTo().performTextInput("1985")
        compose.waitForIdle()
        tap(s(R.string.exp_1_3))
        listOf(R.string.lift_squat, R.string.lift_hinge, R.string.lift_press, R.string.lift_row).forEach { tap(s(it)) }
        tap(s(R.string.action_continue))
        assertEquals(OnboardingStep.CONDITIONS, (c.screen.value as Screen.Onboarding).step)
        tap(s(R.string.onb_cond_none))
        tap(s(R.string.action_continue))
        // FL-001: a 41-year-old gets "Lose fat, keep muscle" suggested and already picked, with the realistic expectation.
        shown(s(R.string.goal_fat_loss), substring = true)
        shown(s(R.string.onb_goal_suggested), substring = true)
        tap(s(R.string.action_continue))
        tap(s(R.string.action_continue)) // schedule
        tap(s(R.string.preset_full_gym))
        tap(s(R.string.action_continue))
        tap(s(R.string.action_continue)) // limitations
        tap(s(R.string.action_continue)) // own numbers
        shown(s(R.string.onb_plan_title))
        tap(s(R.string.action_continue))
        tap(s(R.string.onb_alerts_skip))
        assertTrue(c.screen.value is Screen.Today)
        shown(s(R.string.today_start))
        assertTouchTargets()
    }

    @Test fun `J2 Today, check-in, preview and the first sets of a workout through the screens`() {
        val c = launch { onboardQuickly(it) }
        assertTrue(c.screen.value is Screen.Today)
        tap(s(R.string.today_start))
        assertTrue(c.screen.value is Screen.CheckIn)
        shown(s(R.string.checkin_sleep))
        assertTouchTargets()
        tap(s(R.string.checkin_submit))
        val preview = c.screen.value as Screen.Preview
        shown(s(R.string.preview_level, s(com.personalfitnesscoach.app.ui.text.Labels.tier(preview.model.tier))), substring = true)
        assertTouchTargets()
        tap(s(R.string.preview_start))
        assertTrue(c.screen.value is Screen.Workout)
        // Warm-up, then the first lift: the effort answer logs the set (a weight-finding set confirms its reps) and the rest timer follows.
        shown(s(R.string.wk_warmup_title))
        tap(s(R.string.wk_stage_next))
        var guard = 0
        while ((c.screen.value as Screen.Workout).view.step !is Step.Lift && guard++ < 5) {
            val step = (c.screen.value as Screen.Workout).view.step
            if (step is Step.Conditioning) tap(s(R.string.wk_cond_done)) else tap(s(R.string.wk_stage_next))
        }
        // WU-002 ramp-up sets, if any, are skipped here so the effort question shows.
        val first = ((c.screen.value as Screen.Workout).view.step as Step.Lift).lift
        if (first.next?.kind == SetKind.WARMUP) tap(s(R.string.wk_skip_ramp))
        val before = (c.screen.value as Screen.Workout).view.lifts.sumOf { it.sets.size }
        assertTouchTargets()
        val target = ((c.screen.value as Screen.Workout).view.step as Step.Lift).lift.next!!
        tap("3")
        // A weight-finding set answered "3" with its reps untouched asks once whether all were done (D-116).
        if (target.kind == SetKind.CALIBRATION) {
            assertTouchTargets()
            tap(s(R.string.wk_cal_all_yes, target.reps.last))
        }
        val after = (c.screen.value as Screen.Workout).view
        assertEquals(before + 1, after.lifts.sumOf { it.sets.size })
        shown(s(R.string.wk_rest_over))
        // "Something hurts" is one tap away and closes without changing anything.
        tap(s(R.string.wk_hurts))
        shown(s(R.string.sheet_hurts_title))
        assertTouchTargets()
        tap(s(R.string.action_close))
        // End early: what was done is kept and the summary asks one question.
        tap(s(R.string.wk_end))
        tap(s(R.string.sheet_end_finish))
        assertTrue(c.screen.value is Screen.Done)
        shown(s(R.string.done_title))
        tap(s(R.string.done_back))
        assertTrue(c.screen.value is Screen.Today)
    }

    @Test fun `A5 a warning sign during the workout stops it and shows the emergency number`() {
        val c = launch { onboardQuickly(it) }
        tap(s(R.string.today_start))
        tap(s(R.string.checkin_submit))
        tap(s(R.string.preview_start))
        tap(s(R.string.wk_red_flag))
        tap(s(R.string.rf_chest_pain_pressure_tightness))
        tap(s(R.string.sheet_red_stop))
        assertTrue(c.screen.value is Screen.Stop)
        shown(s(R.string.stop_title))
        shown(s(R.string.stop_call, "112"))
        tap(s(R.string.stop_ok))
        // Today keeps the stop until the user confirms (SAF-002).
        shown(s(R.string.today_stop_confirm))
    }

    @Test fun `settings - erase needs two confirmations and returns to the welcome screen`() {
        val c = launch { onboardQuickly(it) }
        tap(s(R.string.action_settings))
        assertTrue(c.screen.value is Screen.Settings)
        assertTouchTargets()
        tap(s(R.string.set_erase_title), 1)
        shown(s(R.string.erase_confirm1_title))
        tap(s(R.string.erase_go))
        shown(s(R.string.erase_confirm2_title))
        tap(s(R.string.erase_go_final))
        assertEquals(OnboardingStep.WELCOME, (c.screen.value as Screen.Onboarding).step)
    }

    @Test fun `Part 6 - History, Progress and Coach open from the tab bar and the coach answers a suggested question`() {
        val c = launch { onboardQuickly(it) }
        tap(s(R.string.tab_history))
        assertTrue(c.screen.value is Screen.History)
        shown(s(R.string.hist_empty))
        assertTouchTargets()
        tap(s(R.string.tab_progress))
        assertTrue(c.screen.value is Screen.Progress)
        tap(s(R.string.prog_add_weight))
        shown(s(R.string.prog_weight_entry))
        assertTouchTargets()
        tap(s(R.string.action_cancel))
        tap(s(R.string.tab_coach))
        assertTrue(c.screen.value is Screen.Coach)
        assertTouchTargets()
        tap(s(R.string.q_what_it_trains))
        assertTrue((c.screen.value as Screen.Coach).model.answer != null)
        shown(s(R.string.action_why))
        tap(s(R.string.action_why))
        shown(s(R.string.why_title))
        tap(s(R.string.action_back))
        tap(s(R.string.gemini_ask))
        shown(s(R.string.gemini_intro))
        assertTouchTargets()
        tap(s(R.string.action_cancel))
        tap(s(R.string.tab_today))
        assertTrue(c.screen.value is Screen.Today)
    }

    @Test fun `Part 6 - settings open the goal editor with the onboarding page and a theme choice`() {
        val c = launch { onboardQuickly(it) }
        tap(s(R.string.action_settings))
        shown(s(R.string.set_training))
        shown(s(R.string.theme_dark))
        tap(s(R.string.set_goals))
        assertTrue(c.screen.value is Screen.EditProfile)
        shown(s(R.string.onb_goal_title), substring = true)
        assertTouchTargets()
        tap(s(R.string.action_cancel))
        assertTrue(c.screen.value is Screen.Settings)
    }

    @Config(sdk = [35], fontScale = 2.0f)
    @Test fun `200 percent text - the coach still shows every question and the tab bar`() {
        val c = launch { onboardQuickly(it) }
        tap(s(R.string.tab_coach))
        shown(s(R.string.q_how_hard))
        shown(s(R.string.tab_history))
        assertTouchTargets()
        assertTrue(c.screen.value is Screen.Coach)
    }

    /** Review R7-03: at 200% text the set target's weight stays on one line ("102.5" never breaks), and so do the timer's digits. */
    @Config(sdk = [35], fontScale = 2.0f)
    @Test fun `200 percent text - a heavy target and a long timer keep their numbers whole`() {
        compose.setContent {
            PfcTheme {
                androidx.compose.foundation.layout.Column {
                    com.personalfitnesscoach.app.ui.workout.TargetNumbers(102.5, s(R.string.reps_exact, 10))
                    com.personalfitnesscoach.app.ui.workout.RestCountdown(25 * 60_000L, 25 * 60_000L, null, {}, {}, {})
                }
            }
        }
        compose.waitForIdle()
        // (text, its height on screen at normal text size in dp: 76 sp numbers, the 60 sp rest timer) — never smaller at 200% (re-check N1).
        for ((text, normal) in listOf("102.5" to 76f, s(R.string.wk_rest_left, 25, 0) to 60f)) {
            val node = compose.onAllNodes(named(text))[0].fetchSemanticsNode()
            val layouts = ArrayList<androidx.compose.ui.text.TextLayoutResult>()
            node.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val layout = layouts.single()
            assertEquals("$text on one line", 1, layout.lineCount)
            // The line ends inside the text's box (a pixel or two of rounding from the tight letter spacing is allowed): nothing cut off.
            assertTrue("$text fits its width: ${layout.getLineRight(0)} of ${layout.size.width}", layout.getLineRight(0) <= layout.size.width + 2f)
            // The size the text is drawn at, in dp, through the phone's own sp → dp conversion (non-linear at large text sizes).
            val drawn = with(compose.density) { layout.layoutInput.style.fontSize.toDp().value }
            assertTrue("$text is not smaller than at normal text size: drawn at $drawn dp (style ${layout.layoutInput.style.fontSize}, " +
                "font scale ${compose.density.fontScale}, box ${layout.size}, density ${compose.density.density})", drawn >= normal * 0.95f)
        }
    }

    @Config(sdk = [35], fontScale = 2.0f)
    @Test fun `200 percent text - the check-in and the workout still show every control`() {
        val c = launch { onboardQuickly(it) }
        tap(s(R.string.today_start))
        shown(s(R.string.checkin_submit))
        assertTouchTargets()
        tap(s(R.string.checkin_submit))
        tap(s(R.string.preview_start))
        assertTrue(c.screen.value is Screen.Workout)
        shown(s(R.string.wk_hurts))
        shown(s(R.string.wk_red_flag))
    }

    @Test fun `PROG-008 - after two form-not-solid answers the workout offers the easier version, and one tap swaps to it`() {
        val clock = FixedClock(0, ZoneId.of("UTC")).also { it.setDay(monday, hour = 8) }
        val store = InMemoryRowStore()
        var data: PfcData? = null
        val c = AppController({ PfcData(store, clock, "test").also { data = it } }, FakePlatform(), "test")
        runBlocking {
            onboardQuickly(c)
            // Every lift with an easier version has "form not solid" twice on record (Progression sets offerRegression).
            for (e in com.personalfitnesscoach.engine.library.Library.all) if (e.regressionId != null)
                data!!.docs.put(com.personalfitnesscoach.data.core.model.ExerciseState, com.personalfitnesscoach.data.core.model.ExerciseState(e.id,
                    prescription = com.personalfitnesscoach.engine.progression.Prescription(20.0, e.defaultRepRange, e.defaultRepRange.first,
                        com.personalfitnesscoach.engine.progression.ProgressionAction.HOLD, offerRegression = true), lastDoneDay = monday - 2))
            c.openCheckIn(); c.submitCheckIn(); c.startWorkout()
            var guard = 0
            while ((c.screen.value as Screen.Workout).view.step !is Step.Lift && guard++ < 6) (c.screen.value as Screen.Workout).view.stage?.let { c.completeStage(it) }
            val offered = (c.screen.value as Screen.Workout).view.lifts.firstOrNull { it.item.easierOffer != null && !it.hasLoggedWork }
            org.junit.Assert.assertNotNull("some lift today offers its easier version", offered)
            c.select(offered!!.rowId)
        }
        val actions = Actions(CoroutineScope(SupervisorJob()), c, Dispatchers.Main, doubleTapMs = 0)
        compose.setContent { PfcTheme { PfcApp(actions) } }
        compose.waitForIdle()
        val lift = ((c.screen.value as Screen.Workout).view.step as Step.Lift).lift
        val easier = lift.item.easierOffer!!
        val label = s(R.string.wk_try_easier, com.personalfitnesscoach.app.ui.workout.exerciseName(easier))
        shown(label)
        assertTouchTargets()
        tap(label)
        val after = (c.screen.value as Screen.Workout).view.lifts.first { it.rowId == lift.rowId }
        assertEquals(easier, after.exercise.id)
        assertEquals(null, after.item.easierOffer)
    }

    @Test fun `PO feedback - the workout has a Plan view with every part of the session, and Close returns to the workout`() {
        val c = launch { onboardQuickly(it); it.openCheckIn(); it.submitCheckIn(); it.startWorkout() }
        tap(s(R.string.wk_plan))
        shown(s(R.string.plan_title))
        // Every part of the session is listed (some rows sit below the first screen of a small phone).
        fun listed(text: String) = assertTrue("$text is in the plan", compose.onAllNodes(named(text, substring = true)).fetchSemanticsNodes().isNotEmpty())
        listed(s(R.string.wk_warmup_title))
        listed(s(R.string.wk_cooldown_title))
        val v = (c.screen.value as Screen.Workout).view
        v.lifts.forEach { listed(it.exercise.name) }
        assertTouchTargets()
        tap(s(R.string.action_close))
        assertTrue(c.screen.value is Screen.Workout)
        assertEquals(null, (c.screen.value as Screen.Workout).sheet)
    }
}
