package com.personalfitnesscoach.app.flow

import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.model.Profile
import com.personalfitnesscoach.data.core.model.SettingsRecord
import com.personalfitnesscoach.data.core.player.LiftEntry
import com.personalfitnesscoach.data.core.player.Step
import com.personalfitnesscoach.data.core.session.SetKind
import com.personalfitnesscoach.data.core.session.Stage
import com.personalfitnesscoach.data.core.store.InMemoryRowStore
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.data.core.time.FixedClock
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.program.Goal
import com.personalfitnesscoach.engine.safety.ClearanceScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Part 5 re-check of the review fixes (N2, N3, N4, N8), driven as the screens drive the app. */
class Part5RecheckFlowTest {
    private val monday = Days.of(LocalDate.of(2026, 10, 5))

    private class FakePlatform : Platform {
        override val stepCounterAvailable = false
        override suspend fun readSteps(): StepReadingData? = null
        override fun cancelAlerts() = Unit
    }

    private val store = InMemoryRowStore()
    private val clock = FixedClock(0, ZoneId.of("UTC")).also { it.setDay(monday, hour = 8) }
    private fun controller() = AppController({ PfcData(store, clock, "test") }, FakePlatform(), "test")
    private fun data() = PfcData(store, clock, "t")
    private fun AppController.s() = screen.value

    private suspend fun onboard(c: AppController) {
        c.start(); c.submitOnboarding()
        c.editOnboarding { it.copy(screening = ScreeningQuestion.entries.associateWith { q -> q == ScreeningQuestion.REGULARLY_ACTIVE }) }; c.submitOnboarding()
        c.editOnboarding { it.copy(birthYear = 1990, months = ExperienceBand.ONE_TO_3_YEARS, comfortable = setOf("squat", "hinge", "press", "row")) }; c.submitOnboarding()
        c.editOnboarding { it.copy(conditions = emptyMap(), noneOfThese = true) }; c.submitOnboarding()
        c.submitOnboarding(); c.submitOnboarding()
        c.editOnboarding { it.copy(gym = c.preset(GymPreset.FULL_GYM)) }; c.submitOnboarding()
        repeat(4) { c.submitOnboarding() }
        assertTrue(c.s() is Screen.Today)
    }

    /** Starts today's session from Today, logs one set and ends early. */
    private suspend fun oneSetSession(c: AppController) {
        c.openCheckIn(); c.submitCheckIn(); c.startWorkout()
        if ((c.s() as Screen.Workout).view.step is Step.Warmup) c.completeStage(Stage.WARMUP)
        if ((c.s() as Screen.Workout).view.step is Step.BoneLoading) c.completeStage(Stage.BONE_LOADING)
        val lift = ((c.s() as Screen.Workout).view.step as Step.Lift).lift
        c.skipRamp(lift.rowId)
        clock.ms += 3 * 60_000L
        val t = ((c.s() as Screen.Workout).view.step as Step.Lift).lift.next!!
        c.log(lift.rowId, LiftEntry(t.load, if (t.unit == DoseUnit.SECONDS) null else if (t.kind == SetKind.CALIBRATION) t.reps.first else t.reps.last,
            seconds = if (t.unit == DoseUnit.SECONDS) t.reps.last else null, rir = 3.0), expected = t)
        c.openEnd()
        c.finishWorkout(early = true)
    }

    @Test fun `N2 adding pregnancy in Settings drops fat loss, switches weight features off and says so`() = runBlocking {
        val c = controller()
        onboard(c)
        val d = data()
        d.docs.update(Profile) { it!!.copy(priorities = listOf(Goal.FAT_LOSS, Goal.GENERAL_FITNESS)) }
        c.openSettings(); c.openConditionsEditor()
        c.editConditions { it.copy(noneOfThese = false, conditions = mapOf("pregnancy" to ConditionAnswers(pregnancyWeek = 14, clearance = setOf(ClearanceScope.LIGHT_MODERATE)))) }
        c.saveConditions()
        val flow = (c.s() as Screen.Settings).model.flow as SettingsFlow.GoalsChanged
        assertEquals(listOf(Goal.FAT_LOSS), flow.removed)
        assertEquals(listOf(Goal.GENERAL_FITNESS), flow.now)
        assertTrue(flow.weightFeaturesOff)
        assertFalse(Goal.FAT_LOSS in d.docs.get(Profile)!!.priorities)
        assertTrue(d.docs.get(SettingsRecord)!!.weightFeaturesOff)
        c.settingsFlow(null)
        assertNull((c.s() as Screen.Settings).model.flow)
    }

    @Test fun `N3 the pregnancy 'not OK'd' answer reopens as given, and OK'd later lifts the block`() = runBlocking {
        val c = controller()
        onboard(c)
        c.openSettings(); c.openConditionsEditor()
        c.editConditions { it.copy(noneOfThese = false, conditions = mapOf("pregnancy" to ConditionAnswers(pregnancyWeek = 14, attestNotOk = true))) }
        c.saveConditions()
        c.openConditionsEditor()
        val a = (c.s() as Screen.EditConditions).form.conditions["pregnancy"]!!
        assertTrue(a.attestNotOk)
        assertFalse(a.attested)
        assertFalse("the separate box stays as it was", a.blockIfYes)
        c.editConditions { f -> f.copy(conditions = f.conditions.mapValues { (_, x) -> x.copy(attested = true, attestNotOk = false) }) }
        c.saveConditions()
        val stored = data().docs.get(com.personalfitnesscoach.data.core.model.ConditionsRecord)!!.items.single()
        assertFalse(stored.attestNotOk)
        assertFalse(stored.toUserCondition(monday, 0).blockIfYes)
    }

    @Test fun `N8 after a later day done early, the summary names the next day not done yet`() = runBlocking {
        val c = controller()
        onboard(c)
        oneSetSession(c) // Monday's session
        clock.setDay(monday + 1, hour = 8) // Tuesday: the next planned day (later in the week) is offered today
        c.start()
        val moved = data().planToday()!!.next!!.weekday
        assertTrue("a later planned day is offered on Tuesday ($moved)", moved > 1)
        oneSetSession(c)
        val done = c.s() as Screen.Done
        val doneDays = data().planToday()!!.doneWeekdays
        assertTrue(moved in doneDays)
        val next = assertNotNullAnd(done.next)
        assertNotEquals("the day just done is not named again", moved, next.weekday)
        assertFalse("named ${next.weekday}, already done ($doneDays)", next.weekday in doneDays)
        assertTrue(next.weekday > 1)
    }

    private fun <T : Any> assertNotNullAnd(v: T?): T { assertNotNull(v); return v!! }

    @Test fun `N4 a backup password waits for the file picker in the controller and is wiped once taken`() {
        val c = controller()
        val first = "secret".toCharArray()
        c.holdPickerPassword(first)
        val second = "other".toCharArray()
        c.holdPickerPassword(second)
        assertArrayEquals("the replaced password is wiped", "      ".toCharArray(), first)
        assertArrayEquals("other".toCharArray(), c.takePickerPassword())
        assertNull("taken once", c.takePickerPassword())
        c.holdPickerPassword(CharArray(0))
        assertNull("an empty password is no password", c.takePickerPassword())
        assertNull("no restore waiting", c.pendingRestoreToken())
        assertNotEquals(first.toList(), "secret".toList())
    }
}
