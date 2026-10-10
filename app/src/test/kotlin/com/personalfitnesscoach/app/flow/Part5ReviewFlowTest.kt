package com.personalfitnesscoach.app.flow

import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.model.ConditionsRecord
import com.personalfitnesscoach.data.core.player.LiftEntry
import com.personalfitnesscoach.data.core.player.Step
import com.personalfitnesscoach.data.core.session.SetKind
import com.personalfitnesscoach.data.core.session.Stage
import com.personalfitnesscoach.data.core.store.InMemoryRowStore
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.data.core.time.FixedClock
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.safety.PainKind
import com.personalfitnesscoach.engine.safety.ScreeningMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Part 5 independent review (R5-xx): the app flow, driven exactly as the screens drive it. */
class Part5ReviewFlowTest {
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

    private suspend fun onboard(c: AppController, yes: Set<ScreeningQuestion> = setOf(ScreeningQuestion.REGULARLY_ACTIVE),
                                conditions: Map<String, ConditionAnswers> = emptyMap()) {
        c.start(); c.submitOnboarding()
        c.editOnboarding { it.copy(screening = ScreeningQuestion.entries.associateWith { q -> q in yes }) }; c.submitOnboarding()
        c.editOnboarding { it.copy(birthYear = 1985, months = ExperienceBand.ONE_TO_3_YEARS, comfortable = setOf("squat", "hinge", "press", "row")) }; c.submitOnboarding()
        c.editOnboarding { it.copy(conditions = conditions, noneOfThese = conditions.isEmpty()) }; c.submitOnboarding()
        c.submitOnboarding(); c.submitOnboarding()
        c.editOnboarding { it.copy(gym = c.preset(GymPreset.FULL_GYM)) }; c.submitOnboarding()
        repeat(4) { c.submitOnboarding() }
        assertTrue(c.s() is Screen.Today)
    }

    @Test fun `R5-01 pain at the check-in that ends the session gives no session today`() = runBlocking {
        val c = controller()
        onboard(c)
        c.openCheckIn()
        c.editCheckIn { it.copy(pain = PainForm(Joint.SPINE, PainKind.JOINT_OR_TENDON, 8, descriptors = setOf("sharp"), wholeBody = true)) }
        c.submitCheckIn()
        val p = c.s() as Screen.PainDay
        assertTrue(p.outcome.endSession)
        c.closeToToday()
        assertTrue((c.s() as Screen.Today).model.painStop)
        // Start is not offered, and the check-in can't be opened again today.
        c.openCheckIn()
        assertTrue(c.s() is Screen.Today)
    }

    @Test fun `R5-01 a milder pain at the check-in shows its line in the preview`() = runBlocking {
        val c = controller()
        onboard(c)
        c.openCheckIn()
        c.editCheckIn { it.copy(pain = PainForm(Joint.KNEE, PainKind.JOINT_OR_TENDON, 5)) }
        c.submitCheckIn()
        val p = c.s() as Screen.Preview
        assertTrue(p.model.reasons.any { it.reason.name.startsWith("PAIN_") })
    }

    @Test fun `R5-09 question 5 - gentle mode says why, and answering the questions again ends it`() = runBlocking {
        val c = controller()
        onboard(c, yes = setOf(ScreeningQuestion.LIMIT_OR_PREGNANCY, ScreeningQuestion.REGULARLY_ACTIVE))
        val t = (c.s() as Screen.Today).model
        assertTrue(t.conservative)
        assertEquals(ConservativeReason.CLINICIAN, t.conservativeReason)
        c.openRescreen()
        ScreeningQuestion.entries.forEach { q -> c.answerRescreen(q, q == ScreeningQuestion.REGULARLY_ACTIVE) }
        c.submitRescreen()
        val f = (c.s() as Screen.Settings).model.flow as SettingsFlow.Rescreen
        assertEquals(ScreeningMode.STANDARD, f.result!!.mode)
        c.closeSettings()
        assertFalse((c.s() as Screen.Today).model.conservative)
    }

    @Test fun `R5-09 the health questions are due again after 12 months`() = runBlocking {
        val c = controller()
        onboard(c)
        assertFalse((c.s() as Screen.Today).model.rescreenDue)
        clock.advanceDays(366)
        c.refreshToday()
        assertTrue((c.s() as Screen.Today).model.rescreenDue)
    }

    @Test fun `R5-12 condition answers change in settings, keeping start days, and the back-flare switch is on Today`() = runBlocking {
        val c = controller()
        onboard(c, conditions = mapOf("low_back_pain" to ConditionAnswers()))
        assertEquals(false, (c.s() as Screen.Today).model.backFlare)
        c.setBackFlare(true)
        assertEquals(true, (c.s() as Screen.Today).model.backFlare)
        assertTrue(data().docs.get(ConditionsRecord)!!.items.first { it.id == "low_back_pain" }.flare)
        clock.advanceDays(10)
        c.openSettings()
        c.openConditionsEditor()
        c.editConditions { it.copy(conditions = it.conditions + ("oa_knee" to ConditionAnswers(impactOptIn = true))) }
        c.saveConditions()
        assertTrue(c.s() is Screen.Settings)
        val items = data().docs.get(ConditionsRecord)!!.items
        assertEquals(monday, items.first { it.id == "low_back_pain" }.addedDay)
        assertTrue(items.first { it.id == "low_back_pain" }.flare)
        assertEquals(monday + 10, items.first { it.id == "oa_knee" }.addedDay)
        assertTrue(items.first { it.id == "oa_knee" }.impactOptIn)
    }

    @Test fun `R5-14 the first session after a safety stop offers no harder level`() = runBlocking {
        val c = controller()
        onboard(c)
        c.openCheckIn()
        c.editCheckIn { it.copy(redFlags = setOf("chest_pain_pressure_tightness")) }
        c.submitCheckIn()
        clock.advanceDays(1)
        c.refreshToday()
        c.confirmStopResolved()
        c.openCheckIn(); c.submitCheckIn()
        val p = c.s() as Screen.Preview
        assertEquals(Tier.LIGHT, p.model.tier)
        assertTrue("options ${p.model.canChoose}", p.model.canChoose.all { it.ordinal < Tier.LIGHT.ordinal })
    }

    @Test fun `R5-10 and R5-26 a late second tap logs nothing, and the summary names the next session`() = runBlocking {
        val c = controller()
        onboard(c)
        c.openCheckIn(); c.submitCheckIn(); c.startWorkout(); c.completeStage(Stage.WARMUP)
        val lift = ((c.s() as Screen.Workout).view.step as Step.Lift).lift
        c.skipRamp(lift.rowId)
        val t = ((c.s() as Screen.Workout).view.step as Step.Lift).lift.next!!
        val e = LiftEntry(t.load, if (t.unit == DoseUnit.SECONDS) null else if (t.kind == SetKind.CALIBRATION) t.reps.first else t.reps.last,
            seconds = if (t.unit == DoseUnit.SECONDS) t.reps.last else null, rir = 3.0)
        c.log(lift.rowId, e, expected = t)
        c.log(lift.rowId, e, expected = t)
        assertEquals(1, (c.s() as Screen.Workout).view.lifts.first { it.rowId == lift.rowId }.sets.count { it.kind != SetKind.WARMUP })
        c.openEnd()
        c.finishWorkout(early = true)
        assertNotNull((c.s() as Screen.Done).next)
    }

    @Test fun `R5-24 an unusual smallest plate is kept`() {
        val inv = Increments(smallestPlateKg = 2.0).inventory()
        assertTrue(inv.plates.keys.contains(2.0))
        assertFalse(inv.plates.keys.any { it < 2.0 })
    }
}
