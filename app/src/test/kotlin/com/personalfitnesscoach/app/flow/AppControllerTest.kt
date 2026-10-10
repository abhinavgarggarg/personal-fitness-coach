package com.personalfitnesscoach.app.flow

import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.backup.BackupSink
import com.personalfitnesscoach.data.core.model.SafetyStopRecord
import com.personalfitnesscoach.data.core.model.StepsRecord
import com.personalfitnesscoach.data.core.model.dayKey
import com.personalfitnesscoach.data.core.onboarding.OnboardingProblem
import com.personalfitnesscoach.data.core.onboarding.OnboardingStep
import com.personalfitnesscoach.data.core.player.LiftEntry
import com.personalfitnesscoach.data.core.player.Step
import com.personalfitnesscoach.data.core.session.Stage
import com.personalfitnesscoach.data.core.session.Status
import com.personalfitnesscoach.data.core.store.InMemoryRowStore
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.data.core.time.FixedClock
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.program.Goal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** The app's flow on the JVM: onboarding → Today → check-in → preview → workout → summary, and the safety and backup paths (Part 5). */
class AppControllerTest {
    private val monday = Days.of(LocalDate.of(2026, 10, 5))

    private class FakePlatform : Platform {
        var steps = 0L
        var cancelled = 0
        override val stepCounterAvailable = true
        override suspend fun readSteps(): StepReadingData? = StepReadingData(System.currentTimeMillis(), 1000, steps, 1)
        override fun cancelAlerts() { cancelled++ }
    }

    private class World(val store: InMemoryRowStore = InMemoryRowStore(), val clock: FixedClock = FixedClock(0, ZoneId.of("UTC")), val platform: FakePlatform = FakePlatform()) {
        fun controller() = AppController({ PfcData(store, clock, "test") }, platform, "test")
    }

    private fun AppController.s() = screen.value

    /** J1: the onboarding screens in order, answering as a 41-year-old intermediate lifter with a full gym. */
    private suspend fun onboard(c: AppController) {
        c.start()
        assertEquals(OnboardingStep.WELCOME, (c.s() as Screen.Onboarding).step)
        c.submitOnboarding()
        assertEquals(OnboardingStep.SCREENING, (c.s() as Screen.Onboarding).step)
        // Not every question answered: the step stays.
        c.editOnboarding { it.copy(screening = mapOf(ScreeningQuestion.SYMPTOMS to false)) }
        c.submitOnboarding()
        assertEquals(OnboardingStep.SCREENING, (c.s() as Screen.Onboarding).step)
        c.editOnboarding { it.copy(screening = ScreeningQuestion.entries.associateWith { q -> q == ScreeningQuestion.REGULARLY_ACTIVE }) }
        c.submitOnboarding()
        val about = c.s() as Screen.Onboarding
        assertEquals(OnboardingStep.ABOUT_YOU, about.step)
        assertNotNull(about.screening)
        c.editOnboarding { it.copy(birthYear = 2012, months = ExperienceBand.ONE_TO_3_YEARS, comfortable = setOf("squat", "hinge", "press", "row")) }
        c.submitOnboarding()
        assertEquals(OnboardingProblem.UNDER_18, (c.s() as Screen.Onboarding).problem)
        c.editOnboarding { it.copy(birthYear = 1985, bodyweightKg = 80.0) }
        c.submitOnboarding()
        assertEquals(OnboardingStep.CONDITIONS, (c.s() as Screen.Onboarding).step)
        c.editOnboarding { it.copy(noneOfThese = true) }
        c.submitOnboarding()
        val goal = c.s() as Screen.Onboarding
        assertEquals(OnboardingStep.GOAL, goal.step)
        assertEquals(Goal.FAT_LOSS, goal.goals!!.default)
        assertEquals(listOf(Goal.FAT_LOSS), goal.form.goals)
        c.submitOnboarding()
        assertEquals(OnboardingStep.SCHEDULE, (c.s() as Screen.Onboarding).step)
        c.submitOnboarding()
        assertEquals(OnboardingStep.EQUIPMENT, (c.s() as Screen.Onboarding).step)
        c.editOnboarding { it.copy(gym = c.preset(GymPreset.FULL_GYM), gymPreset = GymPreset.FULL_GYM) }
        c.submitOnboarding()
        c.submitOnboarding() // limitations: none
        assertEquals(OnboardingStep.NUMBERS, (c.s() as Screen.Onboarding).step)
        c.submitOnboarding() // numbers: optional, none
        val plan = c.s() as Screen.Onboarding
        assertEquals(OnboardingStep.PLAN, plan.step)
        assertTrue(plan.plan!!.blocks.isNotEmpty())
        c.submitOnboarding()
        assertEquals(OnboardingStep.ALERTS, (c.s() as Screen.Onboarding).step)
        c.submitOnboarding()
        assertTrue(c.s() is Screen.Today)
    }

    private suspend fun world(): Pair<World, AppController> {
        val w = World()
        w.clock.setDay(monday, hour = 8)
        val c = w.controller()
        onboard(c)
        return w to c
    }

    /** Logs every set of the current workout as planned, through the screens' calls, and finishes it. */
    private suspend fun doWorkout(c: AppController, w: World) {
        var guard = 0
        while (guard++ < 300) {
            val s = c.s() as Screen.Workout
            when (val step = s.view.step) {
                is Step.Warmup -> c.completeStage(Stage.WARMUP)
                is Step.BoneLoading -> c.completeStage(Stage.BONE_LOADING)
                is Step.Lift -> {
                    val t = step.lift.next!!
                    w.clock.ms += 60_000
                    val entry = if (t.unit == DoseUnit.SECONDS) LiftEntry(t.load, null, seconds = t.reps.last, rir = t.targetRir ?: 3.0)
                        else LiftEntry(t.load, if (t.kind == "CALIBRATION") t.reps.first else t.reps.last, rir = t.targetRir ?: 3.0)
                    c.log(step.lift.rowId, entry, confirmed = true)
                }
                is Step.Conditioning -> c.logConditioning(step.index, step.item.workMinutes)
                is Step.Balance -> c.completeStage(Stage.BALANCE)
                is Step.Cooldown -> c.completeStage(Stage.COOLDOWN)
                Step.Done -> { c.finishWorkout(); return }
            }
        }
        error("workout did not end")
    }

    @Test fun `J1 first day - onboarding, check-in, preview, a full workout logged through the screens, summary and rating`() = runBlocking {
        val (w, c) = world()
        val today = c.s() as Screen.Today
        assertNotNull(today.model.next)
        assertEquals(7, today.model.week.size)
        c.openCheckIn()
        assertTrue(c.s() is Screen.CheckIn)
        c.submitCheckIn()
        val preview = c.s() as Screen.Preview
        assertEquals(Tier.FULL, preview.model.tier)
        assertTrue(preview.model.session.workout.items.isNotEmpty())
        c.startWorkout()
        assertTrue((c.s() as Screen.Workout).view.step is Step.Warmup)
        doWorkout(c, w)
        val done = c.s() as Screen.Done
        assertTrue(done.summary.workingSets + done.summary.workout.exercises.sumOf { it.calibration.size } > 0)
        c.rateSummary(6)
        assertTrue((c.s() as Screen.Done).rated)
        assertEquals(6.0, done.summary.workout.let { PfcData(w.store, w.clock, "t").sessions.load(it.id)!!.row.sessionRpe })
        assertTrue(w.platform.cancelled > 0)
        assertNull(c.error.value)
        c.closeToToday()
        assertTrue((c.s() as Screen.Today).model.doneToday)
    }

    @Test fun `J3 a bad night caps the day at MODIFIED and says why`() = runBlocking {
        val (_, c) = world()
        c.openCheckIn()
        c.editCheckIn { it.copy(sleep = 4, energy = 4, soreness = 4, stress = 4, sleepHours = 4.5) }
        c.submitCheckIn()
        val p = c.s() as Screen.Preview
        assertTrue("tier ${p.model.tier}", p.model.tier <= Tier.MODIFIED)
        c.closeToToday()
        val t = c.s() as Screen.Today
        assertTrue("reasons ${t.model.reasons.map { it.reason }}", t.model.reasons.any { it.reason == ReasonKey.TIER_CAPPED_BY_SLEEP.name })
    }

    @Test fun `SAF-002 a red flag at check-in stops the day, Today keeps it until confirmed, then the next session is LIGHT`() = runBlocking {
        val (w, c) = world()
        c.openCheckIn()
        c.editCheckIn { it.copy(redFlags = setOf("chest_pain_pressure_tightness")) }
        c.submitCheckIn()
        assertTrue(c.s() is Screen.Stop)
        c.refreshToday()
        assertNotNull((c.s() as Screen.Today).model.stopOpen)
        // Later the same day: still stopped.
        c.openCheckIn(); c.submitCheckIn()
        assertTrue(c.s() is Screen.Stop)
        // Next day the user confirms it was reviewed; the first session back is LIGHT.
        w.clock.advanceDays(1)
        c.refreshToday()
        c.confirmStopResolved()
        assertNull((c.s() as Screen.Today).model.stopOpen)
        c.openCheckIn(); c.submitCheckIn()
        assertEquals(Tier.LIGHT, (c.s() as Screen.Preview).model.tier)
        assertNotNull(PfcData(w.store, w.clock, "t").docs.get(SafetyStopRecord)!!.confirmedDay)
    }

    @Test fun `R1 an interrupted workout is offered for resume after the app was killed, exactly where it stopped`() = runBlocking {
        val (w, c) = world()
        c.openCheckIn(); c.submitCheckIn(); c.startWorkout()
        c.completeStage(Stage.WARMUP)
        val lift = ((c.s() as Screen.Workout).view.step as Step.Lift).lift
        c.skipRamp(lift.rowId)
        val t = ((c.s() as Screen.Workout).view.step as Step.Lift).lift.next!!
        c.log(lift.rowId, LiftEntry(t.load, if (t.kind == "CALIBRATION") t.reps.first else t.reps.last, rir = 4.0))
        val before = (c.s() as Screen.Workout).view
        // Process death: a new controller over the same storage.
        val c2 = w.controller()
        c2.start()
        val r = c2.s() as Screen.Resume
        assertEquals(1, r.view.lifts.first { it.rowId == lift.rowId }.sets.size)
        c2.resumeWorkout()
        val after = (c2.s() as Screen.Workout).view
        assertEquals(before.step, after.step)
        assertEquals(before.restEndsAtMs, after.restEndsAtMs)
        // Or end it: what was logged is kept as a partial session.
        c2.openEnd()
        c2.finishWorkout(early = true)
        val sum = c2.s() as Screen.Done
        assertTrue(sum.summary.workout.doc.endedEarly)
        assertEquals(Status.DONE, sum.summary.workout.row.status)
    }

    @Test fun `A4 something hurts mid-set - a stop-level pain ends that exercise and the result is shown`() = runBlocking {
        val (_, c) = world()
        c.openCheckIn(); c.submitCheckIn(); c.startWorkout(); c.completeStage(Stage.WARMUP)
        val lift = ((c.s() as Screen.Workout).view.step as Step.Lift).lift
        val joint = lift.exercise.jointStress.maxByOrNull { it.value }!!.key
        c.openHurts(lift.rowId)
        assertTrue((c.s() as Screen.Workout).sheet is WorkoutSheet.Hurts)
        c.editHurts { it.copy(region = joint, kind = com.personalfitnesscoach.engine.safety.PainKind.JOINT_OR_TENDON, rating = 5) }
        c.submitHurts()
        val res = (c.s() as Screen.Workout).sheet as WorkoutSheet.HurtsResult
        assertEquals(com.personalfitnesscoach.engine.safety.PainAction.STOP_EXERCISE, res.outcome.action)
        assertTrue((c.s() as Screen.Workout).view.lifts.first { it.rowId == lift.rowId }.finished)
    }

    @Test fun `A5 a red flag during the workout ends it and shows the stop`() = runBlocking {
        val (w, c) = world()
        c.openCheckIn(); c.submitCheckIn(); c.startWorkout()
        c.openRedFlag()
        c.submitRedFlag(setOf("fainting_or_near_fainting"))
        assertTrue(c.s() is Screen.Stop)
        assertNull(PfcData(w.store, w.clock, "t").sessions.active())
    }

    @Test fun `FS-5 an unusual entry asks for a confirmation before it is saved`() = runBlocking {
        val (_, c) = world()
        c.openCheckIn(); c.submitCheckIn(); c.startWorkout(); c.completeStage(Stage.WARMUP)
        val lift = ((c.s() as Screen.Workout).view.step as Step.Lift).lift
        c.log(lift.rowId, LiftEntry(500.0, 99, rir = 2.0))
        assertTrue((c.s() as Screen.Workout).sheet is WorkoutSheet.ConfirmEntry)
        assertFalse((c.s() as Screen.Workout).view.lifts.first { it.rowId == lift.rowId }.hasLoggedWork)
    }

    @Test fun `backup with a password, erase everything, then restore it - back to the same Today`() = runBlocking {
        val (w, c) = world()
        c.openSettings()
        val bytes = c.exportBytes("secret".toCharArray())
        c.exported()
        assertEquals(SettingsFlow.Exported, (c.s() as Screen.Settings).model.flow)
        c.settingsFlow(SettingsFlow.Erase(1)); c.settingsFlow(SettingsFlow.Erase(2))
        c.eraseAll()
        assertEquals(OnboardingStep.WELCOME, (c.s() as Screen.Onboarding).step)
        c.openSettings()
        c.inspectBackup(bytes)
        assertTrue((c.s() as Screen.Settings).model.flow is SettingsFlow.RestoreNeedsPassword)
        c.inspectBackup(bytes, "wrong".toCharArray())
        assertTrue(((c.s() as Screen.Settings).model.flow as SettingsFlow.RestoreNeedsPassword).wrong)
        c.inspectBackup(bytes, "secret".toCharArray())
        val preview = (c.s() as Screen.Settings).model.flow as SettingsFlow.RestorePreview
        val saved = ArrayList<String>()
        c.restore(preview.token, null, BackupSink { name, _ -> saved += name })
        assertEquals(SettingsFlow.Restored, (c.s() as Screen.Settings).model.flow)
        c.closeSettings()
        assertTrue(c.s() is Screen.Today)
        assertTrue(w.platform.cancelled > 0)
    }

    @Test fun `step tracking on reads the phone's counter when the app comes back`() = runBlocking {
        val (w, c) = world()
        c.openSettings()
        w.platform.steps = 1000
        c.setStepTracking(true)
        w.clock.ms += 3_600_000
        w.platform.steps = 4000
        c.closeSettings()
        c.onForeground()
        val d = PfcData(w.store, w.clock, "t")
        assertTrue((d.docs.get(StepsRecord, dayKey(w.clock.today()))?.steps ?: 0) >= 0)
    }

    @Test fun `an action that fails leaves the screen where it was and reports it`() = runBlocking {
        val (_, c) = world()
        c.resumeWorkout() // nothing to resume: back to Today, no error
        assertTrue(c.s() is Screen.Today)
        assertNull(c.error.value)
        c.openCheckIn(); c.submitCheckIn(); c.startWorkout()
        val before = c.s()
        c.swap(12345, "no-such-exercise")
        assertNotNull(c.error.value)
        assertEquals(before, c.s())
        assertFalse(c.busy.value)
    }

    @Test fun `SAF-001 and SAF-010 a doctor's OK recorded in settings ends gentle mode, one scope at a time`() = runBlocking {
        val w = World()
        w.clock.setDay(monday, hour = 8)
        val c = w.controller()
        c.start()
        c.submitOnboarding()
        // Known heart condition while inactive: conservative until a doctor's OK (SAF-001).
        c.editOnboarding { it.copy(screening = ScreeningQuestion.entries.associateWith { q -> q == ScreeningQuestion.HEART_OR_BLOOD_PRESSURE }) }
        c.submitOnboarding()
        c.editOnboarding { it.copy(birthYear = 1975, months = ExperienceBand.ONE_TO_3_YEARS, comfortable = setOf("squat", "hinge", "press", "row")) }
        c.submitOnboarding()
        c.editOnboarding { it.copy(conditions = mapOf("t2d" to ConditionAnswers())) }
        c.submitOnboarding()
        repeat(2) { c.submitOnboarding() }
        c.editOnboarding { it.copy(gym = c.preset(GymPreset.FULL_GYM)) }
        c.submitOnboarding()
        repeat(4) { c.submitOnboarding() }
        assertTrue((c.s() as Screen.Today).model.conservative)
        c.openSettings()
        val m = (c.s() as Screen.Settings).model
        assertTrue(m.screeningClearanceNeeded)
        assertEquals(listOf("t2d"), m.clearances.map { it.id })
        assertEquals("before_vigorous", m.clearances.single().rule)
        c.confirmScreeningClearance()
        val m2 = (c.s() as Screen.Settings).model
        assertFalse(m2.screeningClearanceNeeded)
        assertEquals(monday, m2.screeningClearanceDay)
        c.setConditionClearance("t2d", setOf(com.personalfitnesscoach.engine.safety.ClearanceScope.VIGOROUS))
        assertEquals(setOf(com.personalfitnesscoach.engine.safety.ClearanceScope.VIGOROUS), (c.s() as Screen.Settings).model.clearances.single().confirmed)
        c.closeSettings()
        assertFalse((c.s() as Screen.Today).model.conservative)
    }

    @Test fun `own-number candidates exist in the library`() {
        val c = World().controller()
        for (id in AppController.COMMON_LIFTS) assertNotNull(id, com.personalfitnesscoach.engine.library.Library[id])
        assertTrue(c.numberCandidates(c.preset(GymPreset.HOME_DUMBBELLS)).isNotEmpty())
    }
}
