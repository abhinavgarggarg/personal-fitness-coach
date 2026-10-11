package com.personalfitnesscoach.app.flow

import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.coach.CoachAction
import com.personalfitnesscoach.data.core.coach.OutOfScope
import com.personalfitnesscoach.data.core.coach.Question
import com.personalfitnesscoach.data.core.model.Profile
import com.personalfitnesscoach.data.core.model.SettingsRecord
import com.personalfitnesscoach.data.core.model.Theme
import com.personalfitnesscoach.data.core.player.LiftEntry
import com.personalfitnesscoach.data.core.player.Step
import com.personalfitnesscoach.data.core.session.SetKind
import com.personalfitnesscoach.data.core.session.Stage
import com.personalfitnesscoach.data.core.store.InMemoryRowStore
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.data.core.time.FixedClock
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.Joint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Part 6 driven as the screens drive it: tabs, history corrections, progress entries, the coach and its actions, settings editors. */
class Part6FlowTest {
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

    /** Today's session with one logged set, ended early. */
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

    @Test fun `the four tabs open their top screens and only they show the tab bar`() = runBlocking {
        val c = controller()
        onboard(c)
        assertEquals(Tab.TODAY, c.s().tab())
        c.openTab(Tab.HISTORY); assertTrue((c.s() as Screen.History).items.isEmpty()); assertEquals(Tab.HISTORY, c.s().tab())
        c.openTab(Tab.PROGRESS); assertTrue(c.s() is Screen.Progress)
        c.openTab(Tab.COACH); assertTrue(c.s() is Screen.Coach)
        c.openTab(Tab.TODAY); assertTrue(c.s() is Screen.Today)
        c.openCheckIn(); assertNull("no tab bar during the check-in", c.s().tab())
    }

    @Test fun `a past set is corrected from history, impossible values are refused, and a set can be deleted`() = runBlocking {
        val c = controller()
        onboard(c)
        oneSetSession(c)
        c.openTab(Tab.HISTORY)
        val item = (c.s() as Screen.History).items.single()
        c.openSession(item.workoutId)
        val detail = c.s() as Screen.SessionDetail
        val set = detail.detail.exercises.flatMap { it.sets }.first { it.kind != SetKind.WARMUP }
        c.startSetEdit(set.setId)
        c.changeSetEdit { it.copy(reps = (it.reps ?: 0) + 2) }
        c.saveSetEdit()
        val after = (c.s() as Screen.SessionDetail).detail.exercises.flatMap { it.sets }.first { it.setId == set.setId }
        assertEquals((set.reps ?: 0) + 2, after.reps)
        assertTrue(data().decisions.forDay(monday).any { it.reason == "HISTORY_EDITED" })
        c.startSetEdit(set.setId)
        c.changeSetEdit { it.copy(reps = -3) }
        c.saveSetEdit()
        assertTrue("refused, nothing saved", (c.s() as Screen.SessionDetail).problem)
        c.cancelSetEdit()
        c.startSetEdit(set.setId)
        c.deleteEditedSet()
        assertTrue((c.s() as Screen.SessionDetail).detail.exercises.flatMap { it.sets }.none { it.setId == set.setId })
    }

    @Test fun `weight and waist are entered from Progress, and nothing shows when weight features are off`() = runBlocking {
        val c = controller()
        onboard(c)
        c.openTab(Tab.PROGRESS)
        c.openBodyEntry(waist = false)
        c.changeBodyEntry(BodyEntry.Weight(82.5))
        c.saveBodyEntry()
        assertEquals(82.5, (c.s() as Screen.Progress).overview.body!!.weight.mean7!!, 0.01)
        c.openBodyEntry(waist = true)
        c.changeBodyEntry(BodyEntry.Waist(listOf(94.0, 94.4)))
        c.saveBodyEntry()
        assertEquals(94.2, (c.s() as Screen.Progress).overview.body!!.waistCm!!, 0.05)
        c.openBodyEntry(waist = false)
        c.changeBodyEntry(BodyEntry.Weight(900.0))
        c.saveBodyEntry()
        assertTrue("an impossible weight is refused", (c.s() as Screen.Progress).problem)
        c.openSettings(); c.setWeightFeatures(false)
        c.openTab(Tab.PROGRESS)
        assertNull((c.s() as Screen.Progress).overview.body)
    }

    @Test fun `the coach fits today to the minutes asked through the check-in and the engine`() = runBlocking {
        val c = controller()
        onboard(c)
        c.openTab(Tab.COACH)
        c.editCoachText("I only have 30 minutes today")
        c.askCoach()
        val ans = (c.s() as Screen.Coach).model.answer!!
        assertEquals(Question.ONLY_N_MINUTES, ans.question)
        val act = ans.actions.single() as CoachAction.ShorterSession
        assertEquals(30, act.minutes)
        c.coachAction(act)
        val ci = c.s() as Screen.CheckIn
        assertEquals("the check-in comes first, with the minutes asked", 30, ci.form.minutes)
        c.submitCheckIn()
        val p = c.s() as Screen.Preview
        assertEquals(30, p.model.minutes)
        val reasons = p.model.reasons.map { it.reason.name }
        assertTrue("fitted by the engine, or it says it can't: ${p.model.session.workout.plannedMinutes} $reasons",
            p.model.session.workout.plannedMinutes <= 30.5 || "TIME_NOT_FITTABLE" in reasons)
        assertTrue(data().decisions.forDay(monday).any { it.reason == "COACH_REQUEST" })
        // After the check-in the coach goes straight to the preview, and "tired" offers an easier level only.
        c.closeToToday(); c.openTab(Tab.COACH)
        c.askSuggested(Question.TIRED_TODAY)
        assertEquals(listOf<CoachAction>(CoachAction.EasierLevel), (c.s() as Screen.Coach).model.answer!!.actions)
        val before = p.model.tier
        c.coachAction(CoachAction.EasierLevel)
        assertTrue("easier: ${(c.s() as Screen.Preview).model.tier} after $before", (c.s() as Screen.Preview).model.tier.ordinal < before.ordinal)
    }

    @Test fun `out-of-scope questions are referred, and warning signs from the coach stop training`() = runBlocking {
        val c = controller()
        onboard(c)
        c.openTab(Tab.COACH)
        c.editCoachText("Should I take creatine?")
        c.askCoach()
        val a = (c.s() as Screen.Coach).model.answer!!
        assertEquals(OutOfScope.SUPPLEMENTS, a.outOfScope)
        assertTrue(a.actions.isEmpty() && a.facts.isEmpty())
        c.editCoachText("I have chest pain right now")
        c.askCoach()
        val urgent = (c.s() as Screen.Coach).model.answer!!
        assertEquals(OutOfScope.URGENT_SYMPTOMS, urgent.outOfScope)
        c.coachAction(urgent.actions.single())
        assertEquals(emptySet<String>(), (c.s() as Screen.Coach).model.warning)
        c.pickWarningSign("chest_pain_pressure_tightness", true)
        c.reportWarningSigns()
        assertTrue(c.s() is Screen.Stop)
    }

    @Test fun `Why opens from Today and goes back there, and Ask Gemini shows the text first`() = runBlocking {
        val c = controller()
        onboard(c)
        c.openCheckIn()
        c.editCheckIn { it.copy(sleep = 1, energy = 1, sleepHours = 4.0) }
        c.submitCheckIn()
        c.closeToToday()
        val reason = (c.s() as Screen.Today).model.reasons.first()
        c.openWhy(reason.reason, reason.ruleIds)
        val why = (c.s() as Screen.Coach).model.why!!
        assertEquals(reason.reason, why.reason)
        assertTrue(why.rules.isNotEmpty() && why.rules.all { it.title != null })
        c.closeWhy()
        assertTrue("back to Today", c.s() is Screen.Today)
        c.openTab(Tab.COACH)
        c.editCoachText("Why is today lighter?")
        c.prepareGeminiShare()
        val share = (c.s() as Screen.Coach).model.share!!
        assertEquals("Why is today lighter?", share.question)
        assertNotNull(share.template)
        c.closeGeminiShare()
        assertNull((c.s() as Screen.Coach).model.share)
    }

    @Test fun `goals, week, gym, injuries and theme are changed in Settings and the plan follows`() = runBlocking {
        val c = controller()
        onboard(c)
        c.openSettings()
        c.openProfileEditor(ProfileSection.SCHEDULE)
        c.editProfile { it.copy(daysPerWeek = 4, availableDays = setOf(0, 1, 3, 4, 5), preferredDays = setOf(0, 1, 3, 4)) }
        c.saveProfile()
        assertEquals(SettingsFlow.ProfileSaved(ProfileSection.SCHEDULE), (c.s() as Screen.Settings).model.flow)
        assertEquals(4, data().docs.get(Profile)!!.daysPerWeek)
        assertEquals(4, data().planToday()!!.week.days.count { it.slots.isNotEmpty() || it.conditioning.isNotEmpty() })
        c.openProfileEditor(ProfileSection.EQUIPMENT)
        c.editProfile { it.copy(gym = it.gym - "barbell") }
        c.saveProfile()
        val planned = data().planToday()!!.week.days.flatMap { it.slots }.map { it.exercise }
        assertTrue("no barbell work without a barbell", planned.none { "barbell" in it.equipment })
        c.openProfileEditor(ProfileSection.INJURIES)
        c.editProfile { it.copy(joints = it.joints + Joint.KNEE) }
        c.saveProfile()
        assertTrue(Joint.KNEE in data().docs.get(Profile)!!.priorInjuries.keys)
        c.openProfileEditor(ProfileSection.GOALS)
        val goals = (c.s() as Screen.EditProfile).goals!!
        c.editProfile { it.copy(goals = listOf(goals.offered.first())) }
        c.saveProfile()
        assertEquals(listOf(goals.offered.first()), data().docs.get(Profile)!!.priorities)
        c.setTheme(Theme.LIGHT)
        assertEquals(Theme.LIGHT, c.theme.value)
        assertEquals(Theme.LIGHT, data().docs.get(SettingsRecord)!!.theme)
    }

    @Test fun `an exercise can be liked or skipped from its page and suggested again from Settings`() = runBlocking {
        val c = controller()
        onboard(c)
        val id = data().planToday()!!.week.days.flatMap { it.slots }.first().exercise.id
        c.openTab(Tab.PROGRESS)
        c.openExercise(id)
        c.setFavourite(id, true)
        assertTrue((c.s() as Screen.ExerciseDetail).history.favourite)
        c.setExcluded(id, true)
        assertTrue((c.s() as Screen.ExerciseDetail).history.excluded)
        c.openCoach(id)
        assertEquals(id, (c.s() as Screen.Coach).model.exerciseId)
        c.closeCoach()
        assertTrue("back to the exercise", c.s() is Screen.ExerciseDetail)
        c.openSettings()
        assertEquals(listOf(id), (c.s() as Screen.Settings).model.excludedExercises)
        c.setExcluded(id, false)
        assertTrue((c.s() as Screen.Settings).model.excludedExercises.isEmpty())
        assertFalse(Library[id] == null)
    }
}
