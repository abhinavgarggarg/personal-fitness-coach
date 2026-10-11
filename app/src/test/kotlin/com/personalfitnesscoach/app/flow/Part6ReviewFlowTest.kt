package com.personalfitnesscoach.app.flow

import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.coach.CoachAction
import com.personalfitnesscoach.data.core.coach.OutOfScope
import com.personalfitnesscoach.data.core.coach.Question
import com.personalfitnesscoach.data.core.model.WeightRecord
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Part 6 independent review (R6-xx): the app flow, driven exactly as the screens drive it. */
class Part6ReviewFlowTest {
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
        c.editOnboarding { it.copy(birthYear = 1985, months = ExperienceBand.ONE_TO_3_YEARS, comfortable = setOf("squat", "hinge", "press", "row")) }; c.submitOnboarding()
        c.editOnboarding { it.copy(conditions = emptyMap(), noneOfThese = true) }; c.submitOnboarding()
        c.submitOnboarding(); c.submitOnboarding()
        c.editOnboarding { it.copy(gym = c.preset(GymPreset.FULL_GYM)) }; c.submitOnboarding()
        repeat(4) { c.submitOnboarding() }
        assertTrue(c.s() is Screen.Today)
    }

    private suspend fun painStopDay(c: AppController) {
        c.openCheckIn()
        c.editCheckIn { it.copy(pain = PainForm(Joint.SPINE, PainKind.JOINT_OR_TENDON, 8, descriptors = setOf("sharp"), wholeBody = true)) }
        c.submitCheckIn()
        assertTrue(c.s() is Screen.PainDay)
        c.closeToToday()
        assertTrue((c.s() as Screen.Today).model.painStop)
    }

    /** Starts today's workout and stops in the first lift's rest. */
    private suspend fun startWorkout(c: AppController) {
        c.openCheckIn(); c.submitCheckIn(); c.startWorkout()
        if ((c.s() as Screen.Workout).view.step is Step.Warmup) c.completeStage(Stage.WARMUP)
        if ((c.s() as Screen.Workout).view.step is Step.BoneLoading) c.completeStage(Stage.BONE_LOADING)
        val lift = ((c.s() as Screen.Workout).view.step as Step.Lift).lift
        c.skipRamp(lift.rowId)
        val t = ((c.s() as Screen.Workout).view.step as Step.Lift).lift.next!!
        c.log(lift.rowId, LiftEntry(t.load, if (t.unit == DoseUnit.SECONDS) null else if (t.kind == SetKind.CALIBRATION) t.reps.first else t.reps.last,
            seconds = if (t.unit == DoseUnit.SECONDS) t.reps.last else null, rir = 3.0), expected = t)
    }

    @Test fun `R6-03 no coach action gets round a day the pain gate stopped`() = runBlocking {
        val c = controller()
        onboard(c)
        painStopDay(c)
        c.openTab(Tab.COACH)
        for (action in listOf(CoachAction.ShorterSession(20), CoachAction.EasierLevel, CoachAction.OpenPreview, CoachAction.OpenCheckIn)) {
            c.openTab(Tab.COACH)
            c.coachAction(action)
            assertTrue("$action led to ${c.s()::class.simpleName}", c.s() is Screen.Today)
        }
        assertTrue(data().decisions.forDay(monday).none { it.reason == "COACH_REQUEST" })
    }

    @Test fun `R6-04 the check-in the coach opens keeps the morning's answers, so a hard morning can't be undone`() = runBlocking {
        val c = controller()
        onboard(c)
        c.openCheckIn()
        c.editCheckIn { it.copy(sleep = 1, energy = 1, stress = 1, sleepHours = 5.0) }
        c.submitCheckIn()
        val low = (c.s() as Screen.Preview).model.tier
        assertTrue(low.ordinal < Tier.FULL.ordinal)
        c.closeToToday()
        c.openTab(Tab.COACH)
        c.editCoachText("my knee hurts a bit")
        c.askCoach()
        val a = (c.s() as Screen.Coach).model.answer!!
        assertEquals(OutOfScope.PAIN, a.outOfScope)
        c.coachAction(a.actions.single())
        val f = (c.s() as Screen.CheckIn).form
        assertEquals(1, f.sleep); assertEquals(1, f.energy); assertEquals(1, f.stress); assertEquals(5.0, f.sleepHours!!, 0.0)
        c.editCheckIn { it.copy(pain = PainForm(Joint.KNEE, PainKind.JOINT_OR_TENDON, 2)) }
        c.submitCheckIn()
        val again = (c.s() as Screen.Preview).model.tier
        assertTrue("still $low or lower, got $again", again.ordinal <= low.ordinal)
    }

    @Test fun `R6-05 a workout in progress keeps its place - no tabs under Why, the alarm stays, Today and the editors lead back to it`() = runBlocking {
        val c = controller()
        onboard(c)
        startWorkout(c)
        val w = c.s() as Screen.Workout
        assertNotNull("resting", w.view.restEndsAtMs)
        c.openWhy("LOAD_FROM_E1RM", listOf("INT-004"))
        val coach = c.s() as Screen.Coach
        assertNull("workout mode has no tabs", coach.tab())
        assertEquals("the rest alarm stays set", w.view.restEndsAtMs, coach.restEndsAtMs())
        // Even if something reached them, Today, a tab and the settings editors lead back to the workout.
        c.openTab(Tab.TODAY); assertTrue(c.s() is Screen.Workout)
        c.openTab(Tab.HISTORY); assertTrue(c.s() is Screen.Workout)
        c.openProfileEditor(ProfileSection.EQUIPMENT); assertTrue(c.s() is Screen.Workout)
        c.coachAction(CoachAction.OpenCheckIn); assertTrue(c.s() is Screen.Workout)
        c.openWhy("LOAD_FROM_E1RM", listOf("INT-004"))
        c.closeWhy()
        assertTrue("back to the workout", c.s() is Screen.Workout)
    }

    @Test fun `R6-06 Ask Gemini refers symptom and medicine questions instead of sharing them`() = runBlocking {
        val c = controller()
        onboard(c)
        c.openTab(Tab.COACH)
        c.editCoachText("I have chest pain, should I train?")
        c.prepareGeminiShare()
        val m = (c.s() as Screen.Coach).model
        assertNull(m.share)
        assertEquals(OutOfScope.URGENT_SYMPTOMS, m.answer!!.outOfScope)
        c.editCoachText("Can I take ibuprofen before training?")
        c.prepareGeminiShare()
        assertNull((c.s() as Screen.Coach).model.share)
        assertEquals(OutOfScope.MEDICATION, (c.s() as Screen.Coach).model.answer!!.outOfScope)
        // A day the pain gate stopped: the shared text has no session in it.
        painStopDay(c)
        c.openTab(Tab.COACH)
        c.editCoachText("What should I do this week?")
        c.prepareGeminiShare()
        val share = (c.s() as Screen.Coach).model.share!!
        assertNull(share.template); assertTrue(share.exercises.isEmpty())
    }

    @Test fun `R6-10 history reaches back to the last session after a long break`() = runBlocking {
        val c = controller()
        onboard(c)
        startWorkout(c)
        c.openEnd(); c.finishWorkout(early = true)
        clock.setDay(monday + 120, hour = 8)
        c.refreshToday()
        c.openTab(Tab.HISTORY)
        val h = c.s() as Screen.History
        assertEquals(1, h.items.size)
        assertFalse(h.hasOlder)
    }

    @Test fun `R6-13 Why returns to the answer, and leaving the warning signs keeps the way back`() = runBlocking {
        val c = controller()
        onboard(c)
        val id = data().planToday()!!.week.days.flatMap { it.slots }.first().exercise.id
        c.openTab(Tab.PROGRESS)
        c.openExercise(id)
        c.openCoach(id)
        c.askSuggested(Question.WHAT_IT_TRAINS)
        val answer = (c.s() as Screen.Coach).model.answer!!
        c.openWhy(answer.question!!.name, answer.rules)
        c.closeWhy()
        assertEquals("back to the answer", answer, (c.s() as Screen.Coach).model.answer)
        c.coachAction(CoachAction.CheckWarningSigns)
        assertEquals(emptySet<String>(), (c.s() as Screen.Coach).model.warning)
        c.closeWarning()
        assertNull((c.s() as Screen.Coach).model.warning)
        c.closeCoach()
        assertTrue("still the way back to the exercise", c.s() is Screen.ExerciseDetail)
    }

    @Test fun `R6-14 the first weight starts empty and isn't saved without a number`() = runBlocking {
        val c = controller()
        onboard(c)
        c.openTab(Tab.PROGRESS)
        c.openBodyEntry(waist = false)
        assertEquals(BodyEntry.Weight(null), (c.s() as Screen.Progress).entry)
        c.saveBodyEntry()
        assertTrue((c.s() as Screen.Progress).problem)
        assertTrue(data().docs.all(WeightRecord).isEmpty())
    }

    @Test fun `R6-15 only a request to the engine is logged as a coach request`() = runBlocking {
        val c = controller()
        onboard(c)
        c.openTab(Tab.COACH)
        c.coachAction(CoachAction.OpenProgress)
        c.openTab(Tab.COACH)
        c.coachAction(CoachAction.AskAbout(Question.WHAT_IT_TRAINS, data().planToday()!!.week.days.flatMap { it.slots }.first().exercise.id))
        assertTrue(data().decisions.forDay(monday).none { it.reason == "COACH_REQUEST" })
        c.coachAction(CoachAction.ShorterSession(30))
        assertTrue(data().decisions.forDay(monday).any { it.reason == "COACH_REQUEST" })
    }

    @Test fun `R6 re-check N1 illness reported in the morning stays for the day, from Today or from the coach`() = runBlocking {
        val c = controller()
        onboard(c)
        c.openCheckIn()
        c.editCheckIn { it.copy(illness = setOf("fever")) }
        c.submitCheckIn()
        val morning = (c.s() as Screen.Preview).model.tier
        assertEquals(Tier.RECOVERY, morning)
        c.closeToToday()
        c.openTab(Tab.COACH)
        c.editCoachText("I have a fever, can I do 20 min?")
        c.askCoach()
        val a = (c.s() as Screen.Coach).model.answer!!
        assertEquals(OutOfScope.ILLNESS, a.outOfScope)
        c.coachAction(a.actions.single())
        assertEquals("filled in", setOf("fever"), (c.s() as Screen.CheckIn).form.illness)
        // Even with the illness unticked, the day stays at the illness level.
        c.editCheckIn { it.copy(illness = emptySet()) }
        c.submitCheckIn()
        assertEquals(Tier.RECOVERY, (c.s() as Screen.Preview).model.tier)
        c.closeToToday()
        c.openCheckIn()
        assertEquals(setOf("fever"), (c.s() as Screen.CheckIn).form.illness)
    }
}
