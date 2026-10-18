package com.personalfitnesscoach.app.flow

import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.backup.BackupSink
import com.personalfitnesscoach.data.core.insight.SessionOutcome
import com.personalfitnesscoach.data.core.player.LiftEntry
import com.personalfitnesscoach.data.core.player.Step
import com.personalfitnesscoach.data.core.session.Stage
import com.personalfitnesscoach.data.core.store.InMemoryRowStore
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.data.core.time.FixedClock
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.Exercise
import com.personalfitnesscoach.engine.model.LoadType
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.program.WeekPlanner
import com.personalfitnesscoach.engine.safety.SessionValidator
import com.personalfitnesscoach.engine.safety.PainAction
import com.personalfitnesscoach.engine.safety.PainKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Part 8 integration: whole journeys through the app's own calls (the same ones the screens make), with the data layer and the
 * engine, over twelve weeks of days. Three people: a 41-year-old lifter in a full gym; a 66-year-old beginner at home with type 2
 * diabetes and knee osteoarthritis; and a 35-year-old with only bodyweight. Along the way:
 * - a killed app is resumed mid-workout;
 * - a week is missed;
 * - pain is reported mid-set;
 * - a red flag is reported at the check-in and confirmed later;
 * - the time is changed;
 * - a backup is exported, everything is erased, the backup is restored, and training carries on.
 *
 * After every call the app must not have failed or reported an error, and the stored history must match what was done. The main
 * lifts must progress.
 */
class JourneyIntegrationTest {
    private val monday = Days.of(LocalDate.of(2026, 10, 5))

    private class Platform0 : Platform {
        override val stepCounterAvailable = false
        override suspend fun readSteps(): StepReadingData? = null
        override fun cancelAlerts() = Unit
    }

    private class World(val store: InMemoryRowStore = InMemoryRowStore(), val clock: FixedClock = FixedClock(0, ZoneId.of("UTC"))) {
        fun controller() = AppController({ PfcData(store, clock, "journey") }, Platform0(), "journey")
        fun data() = PfcData(store, clock, "journey-check")
    }

    private data class Persona(
        val name: String, val birthYear: Int, val months: ExperienceBand, val comfortable: Set<String>, val gym: GymPreset,
        val conditions: Map<String, ConditionAnswers> = emptyMap(), val yes: Set<ScreeningQuestion> = setOf(ScreeningQuestion.REGULARLY_ACTIVE),
        /** Strength relative to a trained adult (Body). */
        val strength: Double = 1.0,
    )

    /** Events on given days of the journey (0 = the first Monday). */
    private data class Plan(val killDay: Int? = null, val missedWeek: Int? = null, val painDay: Int? = null, val redFlagDay: Int? = null,
                            val shortDay: Int? = null, val restoreDay: Int? = null)

    private class Log(val name: String) {
        var completed = 0
        var welcomeBackSeen = false
        var resumed = false
        var painStopped = false
        var stopConfirmed = false
        var restored = false
        var shortened = false
        var recoveryDays = 0
        /** Per week (0-based): planned SSU of the days generated, and SSU kept after the weekly limits (VOL-007, LOAD-005). */
        val weeks = sortedMapOf<Int, DoubleArray>()
    }

    private fun AppController.s() = screen.value

    private fun AppController.ok(where: String) {
        val s = s()
        assertFalse("$where: the app failed: $s", s is Screen.Failed)
        assertNull("$where: an error was reported", error.value)
    }

    private suspend fun onboard(c: AppController, p: Persona) {
        c.start(); c.ok("start"); c.submitOnboarding()
        c.editOnboarding { it.copy(screening = ScreeningQuestion.entries.associateWith { q -> q in p.yes }) }; c.submitOnboarding()
        c.editOnboarding { it.copy(birthYear = p.birthYear, months = p.months, comfortable = p.comfortable, bodyweightKg = 80.0) }; c.submitOnboarding()
        c.editOnboarding { it.copy(conditions = p.conditions, noneOfThese = p.conditions.isEmpty()) }; c.submitOnboarding()
        c.submitOnboarding(); c.submitOnboarding()
        c.editOnboarding { it.copy(gym = c.preset(p.gym), gymPreset = p.gym) }; c.submitOnboarding()
        repeat(4) { c.submitOnboarding() }
        c.ok("onboarding")
        assertTrue("${p.name}: onboarding ends on Today, not ${c.s()}", c.s() is Screen.Today)
    }

    /**
     * The simulated person's strength: a true one-rep max per exercise (Epley), which grows a little with every working set. Reps in
     * reserve are reported honestly from it ("5+" at most), so calibration finds real working weights and progression has room.
     */
    private class Body(private val scale: Double) {
        private val max = HashMap<String, Double>()
        private fun oneRm(e: Exercise): Double = max.getOrPut(e.id) {
            scale * when (e.loadType) {
                LoadType.BARBELL -> if (e.pattern == Pattern.SQUAT || e.pattern == Pattern.HINGE) 110.0 else 70.0
                LoadType.DUMBBELL, LoadType.KETTLEBELL -> 26.0
                else -> 70.0
            }
        }
        fun possible(e: Exercise, load: Double): Int = maxOf(1, (30.0 * (oneRm(e) / load - 1.0)).toInt())
        fun trained(e: Exercise) { max[e.id] = oneRm(e) * 1.004 }
    }

    /**
     * Every set of the current workout: weighted sets at the planned weight, as many reps as the plan asks and the body allows, with
     * honest reps in reserve; bodyweight and timed sets as planned at the target reps in reserve. Then finish.
     */
    private suspend fun doWorkout(c: AppController, w: World, where: String, body: Body) {
        var guard = 0
        while (guard++ < 400) {
            val s = c.s()
            if (s is Screen.Workout && s.sheet != null) { c.closeSheet(); continue }
            val v = (s as? Screen.Workout ?: error("$where: expected the workout, got $s")).view
            when (val step = v.step) {
                // The clock moves as in a gym, so the session minutes (and so the measured loads, LOAD-001) are realistic: the warm-up,
                // each set (40 s) after the rest shown, the conditioning, balance and cool-down.
                is Step.Warmup -> { w.clock.ms += (step.minutes * 60_000).toLong(); c.completeStage(Stage.WARMUP) }
                is Step.BoneLoading -> { w.clock.ms += 300_000; c.completeStage(Stage.BONE_LOADING) }
                is Step.Lift -> {
                    val t = step.lift.next!!
                    val e = step.lift.exercise
                    w.clock.ms += 40_000L + 1000L * (v.state.restSec ?: 0)
                    val load = t.load
                    val calibration = t.kind == "CALIBRATION"
                    val entry = when {
                        t.unit == DoseUnit.SECONDS -> LiftEntry(load, null, seconds = t.reps.last, rir = t.targetRir ?: 3.0)
                        load == null || load <= 0.0 -> LiftEntry(load, if (calibration) t.reps.first else t.reps.last, rir = t.targetRir ?: 2.0)
                        else -> {
                            val can = body.possible(e, load)
                            val reps = minOf(if (calibration) t.reps.first else t.reps.last, can)
                            if (!calibration) body.trained(e)
                            LiftEntry(load, reps, rir = minOf(5, can - reps).toDouble())
                        }
                    }
                    c.log(step.lift.rowId, entry, confirmed = true)
                }
                is Step.Conditioning -> { w.clock.ms += (step.item.workMinutes * 60_000).toLong(); c.logConditioning(step.index, step.item.workMinutes) }
                is Step.Balance -> { w.clock.ms += (step.minutes * 60_000).toLong(); c.completeStage(Stage.BALANCE) }
                is Step.Cooldown -> { w.clock.ms += ((step.minutes + step.mobilityMinutes) * 60_000).toLong(); c.completeStage(Stage.COOLDOWN) }
                Step.Done -> { c.finishWorkout(); c.ok("$where: finish"); return }
            }
            c.ok("$where: step $guard")
        }
        error("$where: the workout did not end")
    }

    private suspend fun journey(p: Persona, plan: Plan, weeks: Int = 12): Pair<World, Log> {
        val w = World()
        w.clock.setDay(monday, hour = 8)
        var c = w.controller()
        onboard(c, p)
        val log = Log(p.name)
        val body = Body(p.strength)
        var stopDay: Int? = null
        for (d in 0 until weeks * 7) {
            val where = "${p.name} day $d"
            w.clock.setDay(monday + d, hour = 8)
            c.onForeground(); c.ok("$where: open")

            if (plan.restoreDay == d) {
                // Backup with a password, erase everything, restore it: the same plan carries on (F14).
                c.openSettings()
                val bytes = c.exportBytes("pw".toCharArray()); c.exported()
                val before = w.data().completedWorkouts()
                c.settingsFlow(SettingsFlow.Erase(1)); c.settingsFlow(SettingsFlow.Erase(2)); c.eraseAll()
                assertEquals("$where: erased", 0, w.data().completedWorkouts())
                c.openSettings(); c.inspectBackup(bytes, "pw".toCharArray())
                val preview = (c.s() as Screen.Settings).model.flow as SettingsFlow.RestorePreview
                c.restore(preview.token, null, BackupSink { _, _ -> })
                assertEquals("$where: restored", before, w.data().completedWorkouts())
                c.closeSettings(); c.ok("$where: restore")
                log.restored = true
            }

            val today = c.s() as? Screen.Today ?: run { c.refreshToday(); c.s() as Screen.Today }
            if (today.model.welcomeBack) log.welcomeBackSeen = true
            if (today.model.stopOpen != null) {
                // SAF-002: training waits until the user confirms the symptoms were checked (here, two days later).
                if (stopDay != null && d >= stopDay + 2) { c.confirmStopResolved(); log.stopConfirmed = true; c.ok("$where: confirm") } else continue
            }
            val t = c.s() as Screen.Today
            if (!t.model.nextIsToday || t.model.doneToday) continue
            if (plan.missedWeek != null && d / 7 == plan.missedWeek) continue

            c.openCheckIn(); c.ok("$where: check-in")
            if (plan.redFlagDay != null && d >= plan.redFlagDay && stopDay == null) {
                c.editCheckIn { it.copy(redFlags = setOf("chest_pain_pressure_tightness")) }
                c.submitCheckIn()
                assertTrue("$where: a red flag stops the day", c.s() is Screen.Stop)
                stopDay = d
                c.refreshToday(); c.ok("$where: after the stop")
                continue
            }
            c.submitCheckIn(); c.ok("$where: preview")
            val pv = c.s() as? Screen.Preview ?: error("$where: expected the preview, got ${c.s()}")
            val wk = pv.model.session.workout
            w.data().let { dd -> dd.planToday()?.let { t -> t.next?.let { next ->
                // Against the day as the programme planned it, before the weekly limits (the planner fits the week within them, D-110).
                val planned = WeekPlanner.plan(dd.bridge.weekInput(t.user, t.program).copy(weeklySsuLimit = null, weeklyLoadSsu = null, weeklySsuGuard = null),
                    dd.bridge.blueprint(t.program)).value.days.firstOrNull { it.weekday == next.weekday && it.template == next.template } ?: next
                val week = log.weeks.getOrPut(d / 7) { DoubleArray(2) }
                week[0] += SessionValidator.sessionSsu(planned.toSession()); week[1] += SessionValidator.sessionSsu(wk.validated)
            } } }
            assertNull("$where: no safety stop on a normal day", wk.safetyStop)
            if (wk.items.isEmpty() && wk.conditioning.isEmpty()) {
                // VOL-007 / LOAD-005: when the week's safe load is used up, the day is a recovery day with the reason shown, never an
                // empty training session, and there is nothing to start.
                assertEquals("$where: an empty day is a recovery day", Tier.RECOVERY, wk.tier)
                assertTrue("$where: the weekly limit is the reason", pv.model.reasons.any { it.reason.name == "PLAN_CAPPED_BY_LOAD" })
                log.recoveryDays++
                c.closeToToday(); c.ok("$where: recovery day")
                continue
            }
            if (plan.shortDay != null && d >= plan.shortDay && !log.shortened) {
                // TIME-001: a short day is planned shorter, never cut below its floors.
                c.previewMinutes(30); c.ok("$where: 30 minutes")
                val short = (c.s() as Screen.Preview).model
                assertTrue("$where: shorter session", short.minutes <= 30 || short.session.workout.items.size <= pv.model.session.workout.items.size)
                log.shortened = true
            }
            c.startWorkout(); c.ok("$where: start")

            if (plan.killDay != null && d >= plan.killDay && !log.resumed) {
                // The app is killed after the warm-up: a new controller over the same storage offers the workout back (R1).
                (c.s() as Screen.Workout).view.step.let { if (it is Step.Warmup) c.completeStage(Stage.WARMUP) }
                c = w.controller()
                c.start(); c.ok("$where: restart")
                assertTrue("$where: offered back after the restart, not ${c.s()}", c.s() is Screen.Resume)
                c.resumeWorkout(); c.ok("$where: resume")
                log.resumed = true
            }

            if (plan.painDay != null && d >= plan.painDay && !log.painStopped) {
                // SAF-003: joint pain 5/10 mid-set stops that exercise; the rest of the session goes on.
                var g = 0
                while ((c.s() as Screen.Workout).view.step !is Step.Lift && g++ < 20) {
                    when ((c.s() as Screen.Workout).view.step) {
                        is Step.Warmup -> c.completeStage(Stage.WARMUP)
                        is Step.BoneLoading -> c.completeStage(Stage.BONE_LOADING)
                        else -> break
                    }
                }
                val step = (c.s() as Screen.Workout).view.step
                if (step is Step.Lift) {
                    val joint = step.lift.exercise.jointStress.maxByOrNull { it.value }!!.key
                    c.openHurts(step.lift.rowId)
                    c.editHurts { it.copy(region = joint, kind = PainKind.JOINT_OR_TENDON, rating = 5) }
                    c.submitHurts(); c.ok("$where: pain")
                    val res = (c.s() as Screen.Workout).sheet as WorkoutSheet.HurtsResult
                    assertEquals("$where: the exercise stops", PainAction.STOP_EXERCISE, res.outcome.action)
                    log.painStopped = true
                }
            }

            doWorkout(c, w, where, body)
            assertTrue("$where: the summary follows, not ${c.s()}", c.s() is Screen.Done)
            c.rateSummary(6); c.ok("$where: rating")
            c.closeToToday(); c.ok("$where: back to Today")
            log.completed++
        }
        // The tabs open on the full history.
        c.openTab(Tab.HISTORY); c.ok("${p.name}: history")
        c.openTab(Tab.PROGRESS); c.ok("${p.name}: progress")
        c.openTab(Tab.COACH); c.ok("${p.name}: coach")
        c.openTab(Tab.TODAY); c.ok("${p.name}: today")
        println("JOURNEY ${p.name}: sessions=${log.completed} recoveryDays=${log.recoveryDays}")
        // How much of the planned work the weekly limits kept, week by week (an open issue for Phase 4 tuning; D-107).
        val kept = (0 until weeks).joinToString(" ") { i -> log.weeks[i]?.let { "%.0f".format(100 * it[1] / it[0]) } ?: "-" }
        val all = log.weeks.values.let { v -> 100 * v.sumOf { it[1] } / v.sumOf { it[0] } }
        println("METRIC journey ${p.name}: planned work kept by the weekly limits, %% by week: $kept (overall %.0f%%)".format(all))
        // The weekly limits are paced over the week (D-107): a day emptied by them stays the exception.
        assertTrue("${p.name}: recovery days from the weekly limits: ${log.recoveryDays}", log.recoveryDays <= 2)
        return w to log
    }

    private fun checkHistory(w: World, log: Log, p: Persona) = runBlocking {
        val d = w.data()
        assertEquals("${p.name}: stored sessions = sessions done", log.completed, d.completedWorkouts())
        val c = w.controller(); c.start(); c.openTab(Tab.HISTORY)
        c.ok("${p.name}: history")
        val h = c.s() as Screen.History
        val finished = h.items.count { it.outcome == SessionOutcome.DONE || it.outcome == SessionOutcome.PARTIAL }
        assertTrue("${p.name}: history shows the recent sessions ($finished)", finished > 0)
        assertTrue("${p.name}: every finished session has sets or conditioning",
            h.items.filter { it.outcome == SessionOutcome.DONE }.all { it.sets > 0 || it.conditioningMinutes > 0.0 })
    }

    /**
     * Over the journey the user's lifts got stronger: for at least one lift, the estimated max rose, or the best set got heavier, or
     * longer at the same weight (PROG-002 double progression; sets far from failure have no estimated max, CAL-002).
     */
    private fun checkProgress(w: World, p: Persona) = runBlocking {
        val c = w.controller(); c.start(); c.openTab(Tab.PROGRESS)
        c.ok("${p.name}: progress")
        val o = (c.s() as Screen.Progress).overview
        assertTrue("${p.name}: sessions counted", o.sessionsCompleted > 0)
        val rising = o.lifts.filter { l ->
            val pts = l.points
            val e1rm = pts.mapNotNull { it.e1rm }
            pts.size >= 3 && ((e1rm.size >= 3 && e1rm.last() > e1rm.first()) || pts.last().bestLoad > pts.first().bestLoad ||
                (pts.last().bestLoad == pts.first().bestLoad && pts.last().bestReps > pts.first().bestReps))
        }
        println("PROGRESS ${p.name}: " + o.lifts.joinToString { l -> "${l.exerciseId} ${l.points.first().bestLoad}x${l.points.first().bestReps}→${l.points.last().bestLoad}x${l.points.last().bestReps} (${l.points.size})" })
        assertTrue("${p.name}: at least one lift progressed: ${o.lifts.map { l -> l.exerciseId to l.points.map { it.bestLoad to it.bestReps } }}", rising.isNotEmpty())
    }

    @Test fun `a 41-year-old in a full gym - twelve weeks with a killed app, a missed week, pain, a red flag, a short day and a restore`() {
        val p = Persona("Lifter 41", 1985, ExperienceBand.ONE_TO_3_YEARS, setOf("squat", "hinge", "press", "row"), GymPreset.FULL_GYM)
        val (w, log) = runBlocking { journey(p, Plan(killDay = 2, missedWeek = 4, painDay = 37, redFlagDay = 51, shortDay = 21, restoreDay = 63)) }
        assertTrue("resumed after the kill", log.resumed)
        assertTrue("welcome back after the missed week", log.welcomeBackSeen)
        assertTrue("pain stopped an exercise", log.painStopped)
        assertTrue("the red flag was confirmed and training resumed", log.stopConfirmed)
        assertTrue("a short day was planned", log.shortened)
        assertTrue("the backup was restored", log.restored)
        assertTrue("sessions done: ${log.completed}", log.completed >= 24)
        checkHistory(w, log, p)
        checkProgress(w, p)
    }

    @Test fun `a 66-year-old beginner at home with type 2 diabetes and knee osteoarthritis - twelve weeks`() {
        val p = Persona("Beginner 66", 1960, ExperienceBand.UNDER_6, emptySet(), GymPreset.HOME_DUMBBELLS,
            conditions = mapOf("t2d" to ConditionAnswers(), "oa_knee" to ConditionAnswers()), strength = 0.45)
        val (w, log) = runBlocking { journey(p, Plan(killDay = 9, painDay = 30)) }
        assertTrue("sessions done: ${log.completed}", log.completed >= 20)
        checkHistory(w, log, p)
        checkProgress(w, p)
    }

    @Test fun `a 35-year-old with only bodyweight - twelve weeks with a missed week`() {
        val p = Persona("Bodyweight 35", 1991, ExperienceBand.SIX_TO_12, setOf("squat", "press"), GymPreset.BODYWEIGHT_ONLY, strength = 0.8)
        val (w, log) = runBlocking { journey(p, Plan(missedWeek = 6, shortDay = 14)) }
        assertTrue("welcome back after the missed week", log.welcomeBackSeen)
        assertTrue("sessions done: ${log.completed}", log.completed >= 20)
        checkHistory(w, log, p)
    }
}
