package com.personalfitnesscoach.app

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.personalfitnesscoach.R
import com.personalfitnesscoach.app.flow.AppController
import com.personalfitnesscoach.app.flow.ExperienceBand
import com.personalfitnesscoach.app.flow.GymPreset
import com.personalfitnesscoach.app.flow.Screen
import com.personalfitnesscoach.app.flow.ScreeningQuestion
import com.personalfitnesscoach.app.platform.AndroidPlatform
import com.personalfitnesscoach.app.platform.RestAlerts
import com.personalfitnesscoach.app.ui.Actions
import com.personalfitnesscoach.app.ui.PfcApp
import com.personalfitnesscoach.app.ui.theme.PfcTheme
import com.personalfitnesscoach.data.core.PfcData
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

/**
 * The screens on real phones (every emulator of the matrix): a workout started from Today through the check-in and preview, with
 * Room storage, the app's own action dispatching and the platform's alerts — and the end-of-rest alarm firing on every Android version.
 */
@RunWith(AndroidJUnit4::class)
class ScreensOnDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun s(id: Int, vararg args: Any): String = ctx.getString(id, *args)
    private val dbs = ArrayList<PfcDatabase>()

    @After fun close() { dbs.forEach { it.close() } }

    private fun controller(): AppController {
        val clock = FixedClock(0, ZoneId.of("UTC")).also { it.setDay(Days.of(LocalDate.of(2026, 10, 5)), hour = 8) }
        return AppController({ PfcData(RoomRowStore(PfcDatabase.inMemory(ctx).also { dbs += it }), clock, "device-test") }, AndroidPlatform(ctx), "device-test")
    }

    private fun tapThenWait(text: String, until: () -> Boolean) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        val n = compose.onAllNodesWithText(text)[0]
        runCatching { n.performScrollTo() }
        n.performClick()
        compose.waitUntil(15_000, until)
    }

    @Test fun aWorkoutStartsFromTodayThroughTheScreens() {
        val c = controller()
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
        // The test taps as soon as the screen changes; the double-tap window is for people and is off here.
        val actions = Actions(CoroutineScope(SupervisorJob()), c, doubleTapMs = 0)
        compose.setContent { PfcTheme { PfcApp(actions) } }
        tapThenWait(s(R.string.today_start)) { c.screen.value is Screen.CheckIn }
        tapThenWait(s(R.string.checkin_submit)) { c.screen.value is Screen.Preview }
        tapThenWait(s(R.string.preview_start)) { c.screen.value is Screen.Workout }
        tapThenWait(s(R.string.wk_hurts)) { (c.screen.value as? Screen.Workout)?.sheet != null }
        tapThenWait(s(R.string.action_close)) { (c.screen.value as? Screen.Workout)?.sheet == null }
        tapThenWait(s(R.string.wk_end)) { (c.screen.value as? Screen.Workout)?.sheet != null }
        tapThenWait(s(R.string.sheet_end_discard)) { c.screen.value is Screen.Today }
    }

    @Test fun theEndOfRestAlarmFires() {
        val before = RestAlerts.fired
        RestAlerts.schedule(ctx, System.currentTimeMillis() + 3_000)
        val deadline = System.currentTimeMillis() + 30_000
        while (RestAlerts.fired == before && System.currentTimeMillis() < deadline) Thread.sleep(200)
        RestAlerts.cancel(ctx)
        assertTrue("the rest alarm did not fire within 30 s", RestAlerts.fired > before)
    }
}
