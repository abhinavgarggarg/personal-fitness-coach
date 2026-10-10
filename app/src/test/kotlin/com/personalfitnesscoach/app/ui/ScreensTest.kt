package com.personalfitnesscoach.app.ui

import android.content.Context
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
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
        val actions = Actions(CoroutineScope(SupervisorJob()), c, Dispatchers.Main)
        compose.setContent { PfcTheme { PfcApp(actions) } }
        compose.waitForIdle()
        return c
    }

    /** Taps the n-th node with this text, scrolling to it first when it sits in a scrolling screen (dialogs don't scroll). */
    private fun tap(text: String, index: Int = 0) {
        val n = compose.onAllNodesWithText(text)[index]
        runCatching { n.performScrollTo() }
        n.performClick()
        compose.waitForIdle()
    }

    private fun shown(text: String, substring: Boolean = false) {
        val n = compose.onAllNodesWithText(text, substring = substring)[0]
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
        // SAF-001: Continue stays off until all eight are answered.
        val yes = s(R.string.action_yes)
        val no = s(R.string.action_no)
        for (i in 0 until 7) tap(no, i)
        tap(yes, 7)
        assertTouchTargets()
        tap(s(R.string.action_continue))
        assertEquals(OnboardingStep.ABOUT_YOU, (c.screen.value as Screen.Onboarding).step)
        shown(s(R.string.screen_result_standard))
        compose.onNodeWithText(s(R.string.onb_birth_year)).performScrollTo().performTextInput("1985")
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
        // Warm-up, then the first lift: the effort answer logs the set in one tap and the rest timer follows.
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
        tap("3")
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

}
