package com.personalfitnesscoach.app.flow

import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.backup.BackupSink
import com.personalfitnesscoach.data.core.coach.CoachAction
import com.personalfitnesscoach.data.core.coach.OutOfScope
import com.personalfitnesscoach.data.core.coach.Question
import com.personalfitnesscoach.data.core.model.EquipmentRecord
import com.personalfitnesscoach.data.core.model.ExerciseState
import com.personalfitnesscoach.data.core.model.Profile
import com.personalfitnesscoach.data.core.model.WeekSummary
import com.personalfitnesscoach.data.core.onboarding.OnboardingStep
import com.personalfitnesscoach.data.core.player.Change
import com.personalfitnesscoach.data.core.player.LiftEntry
import com.personalfitnesscoach.data.core.player.PlayerView
import com.personalfitnesscoach.data.core.player.SessionPlayer
import com.personalfitnesscoach.data.core.player.SetTarget
import com.personalfitnesscoach.data.core.player.Step
import com.personalfitnesscoach.data.core.session.SetKind
import com.personalfitnesscoach.data.core.session.Stage
import com.personalfitnesscoach.data.core.session.Status
import com.personalfitnesscoach.data.core.session.WorkoutDoc
import com.personalfitnesscoach.data.core.store.InMemoryRowStore
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.data.core.time.FixedClock
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.Exercise
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.LoadType
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.model.Zone
import com.personalfitnesscoach.engine.program.SrpePrompt
import com.personalfitnesscoach.engine.program.WeekPlan
import com.personalfitnesscoach.engine.program.WeekPlanner
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds
import com.personalfitnesscoach.engine.safety.PainAction
import com.personalfitnesscoach.engine.safety.PainKind
import com.personalfitnesscoach.engine.safety.SessionValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Phase 4 Part 2 (E2): the 30 scenarios of `docs/phase4/scenarios.md`, S01 to S30, each driven through the app's own controller — the
 * calls the screens make — on the real data layer and engine, over simulated days. They run on every push.
 *
 * Every scenario checks after each call that the app neither failed nor reported an error. The simulated person reports effort
 * honestly from a modelled strength (a true one-rep max per exercise that grows a little with training), so weights found and progressed
 * are realistic.
 */
class ScenarioSuiteTest {
    private val monday = Days.of(LocalDate.of(2026, 10, 5))

    // ================================================================================================================ the kit
    private class FakePlatform : Platform {
        var cancelled = 0
        override val stepCounterAvailable = false
        override suspend fun readSteps(): StepReadingData? = null
        override fun cancelAlerts() { cancelled++ }
    }

    /** The phone: its storage, clock and platform outlive the app; [restart] is the app started again over them (process death). */
    private inner class World {
        val store = InMemoryRowStore()
        val clock = FixedClock(0, ZoneId.of("UTC")).also { it.setDay(monday, hour = 8) }
        val platform = FakePlatform()
        var c = newController()
        fun newController() = AppController({ PfcData(store, clock, "scenario") }, platform, "scenario")
        fun restart(): AppController { c = newController(); return c }
        fun data() = PfcData(store, clock, "scenario-check")
        fun day(d: Int, hour: Int = 8) = clock.setDay(monday + d, hour)
        fun view(): PlayerView = (c.s() as? Screen.Workout ?: error("expected the workout, got ${c.s()}")).view
    }

    private data class Persona(
        val birthYear: Int, val months: ExperienceBand, val comfortable: Set<String>, val gym: GymPreset,
        val conditions: Map<String, ConditionAnswers> = emptyMap(), val yes: Set<ScreeningQuestion> = setOf(ScreeningQuestion.REGULARLY_ACTIVE),
        /** Strength relative to a trained adult ([Body]). */
        val strength: Double = 1.0,
    )

    /** J1's person: 41, one to three years of training, a full gym. */
    private val lifter = Persona(1985, ExperienceBand.ONE_TO_3_YEARS, setOf("squat", "hinge", "press", "row"), GymPreset.FULL_GYM)

    /** 66, a beginner at home with dumbbells. */
    private val older = Persona(1960, ExperienceBand.UNDER_6, emptySet(), GymPreset.HOME_DUMBBELLS, strength = 0.45)

    private fun AppController.s() = screen.value

    private fun AppController.ok(where: String) {
        val s = s()
        assertFalse("$where: the app failed: $s", s is Screen.Failed)
        assertNull("$where: an error was reported", error.value)
    }

    /** Onboarding as the screens do it, in order, with the persona's answers; ends on Today. */
    private suspend fun World.onboard(p: Persona = lifter): AppController {
        c.start(); c.ok("start"); c.submitOnboarding()
        c.editOnboarding { it.copy(screening = ScreeningQuestion.entries.associateWith { q -> q in p.yes }) }; c.submitOnboarding()
        c.editOnboarding { it.copy(birthYear = p.birthYear, months = p.months, comfortable = p.comfortable, bodyweightKg = 80.0) }; c.submitOnboarding()
        c.editOnboarding { it.copy(conditions = p.conditions, noneOfThese = p.conditions.isEmpty()) }; c.submitOnboarding()
        c.submitOnboarding(); c.submitOnboarding()
        c.editOnboarding { it.copy(gym = c.preset(p.gym), gymPreset = p.gym) }; c.submitOnboarding()
        repeat(4) { c.submitOnboarding() }
        c.ok("onboarding")
        assertTrue("onboarding ends on Today, not ${c.s()}", c.s() is Screen.Today)
        return c
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
        private fun trained(e: Exercise) { max[e.id] = oneRm(e) * 1.002 }

        /**
         * One set as asked, as far as the body allows, with honest reps in reserve. A working set stops at the target effort (reps in
         * reserve), within the rep range; a ramp set does its target reps or as many as it can.
         */
        fun entry(e: Exercise, t: SetTarget): LiftEntry {
            val calibration = t.kind == SetKind.CALIBRATION
            val load = t.load
            return when {
                t.unit == DoseUnit.SECONDS -> LiftEntry(load, null, seconds = t.reps.last, rir = t.targetRir ?: 3.0)
                load == null || load <= 0.0 -> LiftEntry(load, if (calibration) t.reps.first else t.reps.last, rir = t.targetRir ?: 2.0)
                else -> {
                    val can = possible(e, load)
                    val aim = t.targetRir?.let { r -> maxOf(1, can - Math.round(r).toInt()) } ?: t.reps.last
                    val reps = minOf(if (calibration) t.reps.first else minOf(t.reps.last, aim), can)
                    if (t.kind == SetKind.WORKING) trained(e)
                    LiftEntry(load, reps, rir = minOf(5, can - reps).toDouble())
                }
            }
        }
    }

    /**
     * Every set of the workout on screen, with the clock moving as in a gym, then Finish. `tired`: the main lifts' working sets fall two
     * reps short of the target, at no reps in reserve (stalled lifts, DEL-001 F1). Returns the number of sets logged.
     */
    private suspend fun World.doWorkout(where: String, body: Body, tired: Boolean = false): Int {
        var logged = 0
        var guard = 0
        while (guard++ < 400) {
            val s = c.s()
            if (s is Screen.Workout && s.sheet != null) { c.closeSheet(); continue }
            val v = (s as? Screen.Workout ?: error("$where: expected the workout, got $s")).view
            when (val step = v.step) {
                is Step.Warmup -> { clock.ms += (step.minutes * 60_000).toLong(); c.completeStage(Stage.WARMUP) }
                is Step.BoneLoading -> { clock.ms += 300_000; c.completeStage(Stage.BONE_LOADING) }
                is Step.Lift -> {
                    val t = step.lift.next!!
                    clock.ms += 40_000L + 1000L * (v.state.restSec ?: 0)
                    val e = if (tired && step.lift.item.main && t.kind == SetKind.WORKING && t.unit == DoseUnit.REPS) LiftEntry(t.load, maxOf(1, t.reps.first - 2), rir = 0.0)
                        else body.entry(step.lift.exercise, t)
                    c.log(step.lift.rowId, e, confirmed = true, expected = t)
                    logged++
                }
                is Step.Conditioning -> { clock.ms += (step.item.workMinutes * 60_000).toLong(); c.logConditioning(step.index, step.item.workMinutes) }
                is Step.Balance -> { clock.ms += (step.minutes * 60_000).toLong(); c.completeStage(Stage.BALANCE) }
                is Step.Cooldown -> { clock.ms += ((step.minutes + step.mobilityMinutes) * 60_000).toLong(); c.completeStage(Stage.COOLDOWN) }
                Step.Done -> { c.finishWorkout(); c.ok("$where: finish"); return logged }
            }
            c.ok("$where: step $guard")
        }
        error("$where: the workout did not end")
    }

    /** From the start of the workout to the first exercise (warm-up, a bone-loading block, conditioning planned first). */
    private suspend fun World.toLifts() {
        var g = 0
        while (g++ < 20) {
            when (val st = view().step) {
                is Step.Warmup -> { clock.ms += (st.minutes * 60_000).toLong(); c.completeStage(Stage.WARMUP) }
                is Step.BoneLoading -> c.completeStage(Stage.BONE_LOADING)
                is Step.Conditioning -> { clock.ms += (st.item.workMinutes * 60_000).toLong(); c.logConditioning(st.index, st.item.workMinutes) }
                else -> return
            }
            c.ok("to the lifts")
        }
    }

    /** Logs the next set of one exercise (its ramp-up sets skipped) as the body allows. */
    private suspend fun World.logNext(rowId: Long, body: Body) {
        if (view().lifts.first { it.rowId == rowId }.next?.kind == SetKind.WARMUP) c.skipRamp(rowId)
        val l = view().lifts.first { it.rowId == rowId }
        val t = l.next!!
        clock.ms += 60_000
        c.log(rowId, body.entry(l.exercise, t), confirmed = true, expected = t)
        c.ok("log ${l.exercise.id}")
    }

    private data class DayResult(val trained: Boolean, val preview: PreviewModel?, val workoutId: Long?)

    /**
     * The person trains on the week's planned weekdays: today is one, or the session offered is today's. A session missed earlier in the
     * week is offered on the next one (REG-002: the plan shifts), as Today shows it.
     */
    private fun TodayModel.trainingDay(): Boolean = next != null && !doneToday &&
        (nextIsToday || plan.week.days.any { it.weekday == Days.weekday(today) && (it.slots.isNotEmpty() || it.conditioning.isNotEmpty()) })

    /**
     * One day as the person lives it: the app is opened at 8:00; on a training day, the check-in (changed by `checkIn`), the preview,
     * every set, the rating and back to Today. A day emptied by the weekly limits is a recovery day: nothing to start.
     */
    private suspend fun World.trainDay(d: Int, body: Body, checkIn: (CheckInForm) -> CheckInForm = { it }, rpe: Int = 6, tired: Boolean = false): DayResult {
        val where = "day $d"
        day(d)
        c.onForeground(); c.ok("$where: open")
        if (c.s() !is Screen.Today) { c.refreshToday(); c.ok("$where: Today") }
        val t = (c.s() as Screen.Today).model
        if (t.stopOpen != null || t.painStop || !t.trainingDay()) return DayResult(false, null, null)
        c.openCheckIn(); c.ok("$where: check-in")
        if (c.s() !is Screen.CheckIn) return DayResult(false, null, null)
        c.editCheckIn(checkIn); c.submitCheckIn(); c.ok("$where: preview")
        val pv = (c.s() as? Screen.Preview ?: return DayResult(false, null, null).also { c.refreshToday() }).model
        val wk = pv.session.workout
        if (wk.items.isEmpty() && wk.conditioning.isEmpty()) { c.closeToToday(); return DayResult(false, pv, null) }
        c.startWorkout(); c.ok("$where: start")
        val id = view().workoutId
        doWorkout(where, body, tired)
        assertTrue("$where: the summary follows, not ${c.s()}", c.s() is Screen.Done)
        c.rateSummary(rpe); c.ok("$where: rating")
        c.closeToToday(); c.ok("$where: back to Today")
        return DayResult(true, pv, id)
    }

    private suspend fun World.train(days: IntRange, body: Body): List<DayResult> = days.map { trainDay(it, body) }

    private fun ssu(week: WeekPlan): Double = week.days.sumOf { SessionValidator.sessionSsu(it.toSession()) }

    /** This week as the programme plans it without the weekly limits (VOL-007, LOAD-005), and as planned within them. */
    private suspend fun World.weekAgainstPlan(): Pair<Double, Double> {
        val dd = data()
        val t = dd.planToday()!!
        val full = WeekPlanner.plan(dd.bridge.weekInput(t.user, t.program).copy(weeklySsuLimit = null, weeklyLoadSsu = null, weeklySsuGuard = null),
            dd.bridge.blueprint(t.program)).value
        return ssu(full) to ssu(t.week)
    }

    private fun previewOf(c: AppController): PreviewModel = (c.s() as? Screen.Preview ?: error("expected the preview, got ${c.s()}")).model

    // ================================================================================================================ first days
    @Test fun `S01 first day - a profile, a first week, a FULL session that starts by finding the weights, every set saved`() = runBlocking {
        val w = World()
        val c = w.onboard()
        val today = (c.s() as Screen.Today).model
        assertNotNull("a profile", w.data().docs.get(Profile))
        assertTrue("a first week", today.plan.week.days.count { it.slots.isNotEmpty() } >= 2)
        assertNotNull("today's session", today.next)
        assertTrue(today.nextIsToday)
        assertEquals(7, today.week.size)
        c.openCheckIn(); c.submitCheckIn(); c.ok("check-in")
        val p = previewOf(c)
        assertEquals(Tier.FULL, p.tier)
        val loaded = p.session.workout.items.filter { it.exercise.loadType in SessionPlayer.LOADED && !it.exercise.assisted && it.unit == DoseUnit.REPS && it.load != null }
        assertTrue("loaded exercises in the first session", loaded.isNotEmpty())
        assertTrue("each loaded exercise starts by finding its weight: ${loaded.filter { !it.calibrating }.map { it.exercise.id }}", loaded.all { it.calibrating })
        c.startWorkout(); c.ok("start")
        val id = w.view().workoutId
        val logged = w.doWorkout("S01", Body(1.0))
        val done = c.s() as Screen.Done
        val stored = w.data().sessions.load(id)!!
        assertEquals(Status.DONE, stored.row.status)
        assertEquals("every set logged is saved", logged, stored.exercises.sumOf { it.sets.size })
        assertFalse("not rated yet", done.rated)
        assertNotEquals("the summary asks for the effort rating", SrpePrompt.Ask.NEVER, done.summary.ask)
        c.rateSummary(6); c.ok("rating")
        assertTrue((c.s() as Screen.Done).rated)
        assertEquals("one rating, saved", 6.0, w.data().sessions.load(id)!!.row.sessionRpe!!, 0.0)
        c.closeToToday()
        val after = (c.s() as Screen.Today).model
        assertTrue("Today shows the day as done", after.doneToday)
        assertEquals(DayState.DONE, after.week.single { it.today }.state)
    }

    @Test fun `S02 a warning sign in screening - gentle mode with the doctor line, until a doctor's OK is recorded`() = runBlocking {
        val w = World()
        val c = w.onboard(lifter.copy(yes = setOf(ScreeningQuestion.SYMPTOMS, ScreeningQuestion.REGULARLY_ACTIVE)))
        val t = (c.s() as Screen.Today).model
        assertTrue("gentle mode", t.conservative)
        assertEquals("the 'check with your doctor' line", ConservativeReason.SCREENING_DOCTOR, t.conservativeReason)
        // Four weeks in gentle mode: no intervals or hard cardio planned, and lifting effort stays moderate (SAF-001 conservative mode).
        val body = Body(1.0)
        var sessions = 0
        for (d in 0 until 28) {
            val r = w.trainDay(d, body)
            val plan = (c.s() as Screen.Today).model.plan.week
            assertTrue("day $d: no intervals or hard cardio planned",
                plan.days.flatMap { it.conditioning }.none { it.hiit || it.interval != null || it.zone > Zone.Z2 })
            val wk = r.preview?.session?.workout ?: continue
            assertTrue("day $d: no hard cardio in the session", wk.conditioning.none { it.countsAsHiit || it.zone > Zone.Z2 })
            assertTrue("day $d: at least 3 reps in reserve, never to failure", wk.items.all { it.targetRir >= P.SAF_001.conservative_mode.min_rir && !it.lastSetToFailure })
            if (r.trained) sessions++
        }
        assertTrue("trained in gentle mode ($sessions)", sessions >= 8)
        c.openSettings()
        assertTrue((c.s() as Screen.Settings).model.screeningClearanceNeeded)
        c.confirmScreeningClearance(); c.ok("doctor's OK")
        c.closeSettings()
        val after = (c.s() as Screen.Today).model
        assertFalse("the doctor's OK ends gentle mode", after.conservative)
        assertNull(after.conservativeReason)
    }

    @Test fun `S03 onboarding closed after three steps opens again at the same step with the answers kept`() = runBlocking {
        val w = World()
        val c = w.c
        c.start(); c.submitOnboarding()
        val screening = ScreeningQuestion.entries.associateWith { q -> q == ScreeningQuestion.REGULARLY_ACTIVE }
        c.editOnboarding { it.copy(screening = screening) }; c.submitOnboarding()
        c.editOnboarding { it.copy(birthYear = 1985, months = ExperienceBand.ONE_TO_3_YEARS, comfortable = setOf("squat", "row"), bodyweightKg = 80.0) }
        c.submitOnboarding(); c.ok("three steps")
        assertEquals(OnboardingStep.CONDITIONS, (c.s() as Screen.Onboarding).step)
        // The app is closed; opened again later.
        w.clock.ms += 3 * 3_600_000L
        val c2 = w.restart()
        c2.start(); c2.ok("reopened")
        val o = c2.s() as Screen.Onboarding
        assertEquals("the same step", OnboardingStep.CONDITIONS, o.step)
        assertEquals(screening, o.form.screening)
        assertEquals(1985, o.form.birthYear)
        assertEquals(ExperienceBand.ONE_TO_3_YEARS, o.form.months)
        assertEquals(setOf("squat", "row"), o.form.comfortable)
        assertEquals(80.0, o.form.bodyweightKg!!, 0.0)
        // And it carries on from there to Today.
        c2.editOnboarding { it.copy(noneOfThese = true) }; c2.submitOnboarding()
        c2.submitOnboarding(); c2.submitOnboarding()
        c2.editOnboarding { it.copy(gym = c2.preset(GymPreset.FULL_GYM), gymPreset = GymPreset.FULL_GYM) }; c2.submitOnboarding()
        repeat(4) { c2.submitOnboarding() }
        c2.ok("finished")
        assertTrue(c2.s() is Screen.Today)
    }

    @Test fun `S04 two health conditions at 66 - no impact, strength days apart, the prompts with the session, balance work`() = runBlocking {
        val w = World()
        val c = w.onboard(older.copy(conditions = mapOf("t2d" to ConditionAnswers(), "oa_knee" to ConditionAnswers())))
        val today = (c.s() as Screen.Today).model
        assertTrue("the condition prompts are on Today", today.conditionPrompts.isNotEmpty())
        val body = Body(older.strength)
        val strengthDone = ArrayList<Int>()
        for (d in 0 until 14) {
            if (d % 7 == 0) {
                w.day(d); c.onForeground()
                val week = w.data().planToday()!!.week
                val strength = week.days.filter { it.slots.isNotEmpty() }.map { it.weekday }.sorted()
                assertTrue("week ${d / 7}: strength days $strength", strength.size >= 2)
                assertTrue("week ${d / 7}: strength days are not on consecutive days: $strength", strength.zipWithNext().all { (a, b) -> b - a >= 2 })
                assertTrue("week ${d / 7}: no jumping or impact work", week.days.all { day -> day.slots.all { it.exercise.impact == 0 } && day.conditioning.all { it.impact == 0 } })
                // AGE-001 at 65+: balance work on 3 days a week — in the sessions, or as short home sessions on other days (FL-003).
                val balanceDays = week.days.count { it.balanceMinutes > 0.0 } + (week.activity?.homeBalanceSessions ?: 0)
                println("METRIC S04 week ${d / 7}: balance on ${week.days.count { it.balanceMinutes > 0.0 }} training days + ${week.activity?.homeBalanceSessions ?: 0} at home")
                assertTrue("week ${d / 7}: balance work on ${P.AGE_001.age_65.balance_days}+ days ($balanceDays)", balanceDays >= P.AGE_001.age_65.balance_days)
                // Type 2 diabetes: never more than two days in a row without activity; short walks fill longer gaps (SAF-010, STEP-002).
                val active = week.days.filter { it.slots.isNotEmpty() || it.conditioning.isNotEmpty() }.map { it.weekday }.toSet() + week.walkDays
                var run = 0
                var longest = 0
                for (k in 1..7) { val wd = ((active.minOrNull() ?: 0) + k) % 7; if (wd in active) run = 0 else { run++; longest = maxOf(longest, run) } }
                assertTrue("week ${d / 7}: $longest days in a row without activity", longest <= 2)
            }
            val r = w.trainDay(d, body)
            val wk = r.preview?.session?.workout ?: continue
            if (!r.trained) continue
            assertTrue("day $d: the condition prompts come with the session", wk.conditionPrompts.isNotEmpty())
            assertTrue("day $d: no impact", wk.items.all { it.exercise.impact == 0 } && wk.conditioning.all { it.impact == 0 })
            assertTrue("day $d: knee within the condition's limit", wk.items.all { it.exercise.stress(Joint.KNEE) <= 2 })
            if (wk.items.isNotEmpty()) strengthDone += d
        }
        assertTrue("strength sessions done: $strengthDone", strengthDone.size >= 4)
        assertTrue("never strength on consecutive days: $strengthDone", strengthDone.zipWithNext().all { (a, b) -> b - a >= 2 })
    }

    // ================================================================================================================ everyday training
    @Test fun `S05 a normal gym day week after week - a main lift's target goes up`() = runBlocking {
        val w = World()
        w.onboard()
        val body = Body(1.0)
        // Five weeks: the first exposures find the weights (CAL-001), then the working weeks. After each session, each main lift's next
        // target as the exercise page shows it (weight × reps, PROG-001/002), and the weight each session asks for, per lift and rep range.
        val mains = HashSet<String>()
        val targets = LinkedHashMap<String, MutableList<Pair<Double, Int>>>()
        val asked = LinkedHashMap<String, MutableList<Double>>()
        for (d in 0 until 35) {
            val r = w.trainDay(d, body)
            if (!r.trained) continue
            r.preview!!.session.workout.items.filter { it.main && !it.calibrating && it.load != null }.forEach {
                mains += it.exercise.id
                asked.getOrPut("${it.exercise.id} ${it.reps}") { ArrayList() } += it.load!!
            }
            for (st in w.data().docs.all(ExerciseState)) if (st.exerciseId in mains) st.prescription?.let { p ->
                val list = targets.getOrPut(st.exerciseId) { ArrayList() }
                if (list.lastOrNull() != p.load to p.repTarget) list += p.load to p.repTarget
            }
        }
        println("METRIC S05 main-lift targets over 5 weeks: " + targets.entries.joinToString { (k, v) -> "$k ${v.joinToString("→") { "${it.first}x${it.second}" }}" } +
            "; weights asked: " + asked.entries.joinToString { (k, v) -> "$k ${v.joinToString("→")}" })
        val up = targets.filterValues { v -> v.size >= 2 && (v.last().first > v.first().first + 1e-9 || (v.last().first == v.first().first && v.last().second > v.first().second)) }
        assertTrue("a main lift's target load or reps went up: $targets", up.isNotEmpty())
        assertTrue("and a session asked for more weight at the same reps: $asked", asked.values.any { it.size >= 2 && it.last() > it.first() + 1e-9 })
        assertTrue("sessions done", w.data().completedWorkouts() >= 14)
    }

    @Test fun `S06 a bad night - MODIFIED at most with the reason, never more than one step harder`() = runBlocking {
        val w = World()
        val c = w.onboard()
        c.openCheckIn()
        c.editCheckIn { it.copy(sleep = 2, sleepHours = 4.5) }
        c.submitCheckIn(); c.ok("check-in")
        val p = previewOf(c)
        assertTrue("tier ${p.tier}", p.tier <= Tier.MODIFIED)
        assertTrue("the reason is shown: ${p.reasons.map { it.reason }}", w.data().decisions.forDay(monday).any { it.reason == ReasonKey.TIER_CAPPED_BY_SLEEP.name })
        assertTrue("easier is always possible", p.tier == Tier.RECOVERY || p.canChoose.any { it < p.tier })
        assertTrue("never more than one step harder: ${p.canChoose}", p.canChoose.all { it.ordinal <= p.tier.ordinal + 1 })
        // Asking for FULL anyway: at most one step above what the engine set.
        c.chooseTier(Tier.FULL); c.ok("harder")
        assertTrue("got ${previewOf(c).tier}", previewOf(c).tier.ordinal <= p.tier.ordinal + 1)
        c.closeToToday()
        assertTrue((c.s() as Screen.Today).model.reasons.any { it.reason == ReasonKey.TIER_CAPPED_BY_SLEEP.name })
    }

    @Test fun `S07 a red flag at the check-in stops training until confirmed, then the first session back is LIGHT`() = runBlocking {
        val w = World()
        val c = w.onboard()
        c.openCheckIn()
        c.editCheckIn { it.copy(redFlags = setOf("chest_pain_pressure_tightness")) }
        c.submitCheckIn(); c.ok("red flag")
        assertTrue("training stops at once", c.s() is Screen.Stop)
        // Two days later, not yet confirmed: still stopped, no check-in.
        assertFalse(w.trainDay(2, Body(1.0)).trained)
        assertNotNull((c.s() as Screen.Today).model.stopOpen)
        c.openCheckIn()
        assertTrue("no check-in while the stop is open", c.s() is Screen.Today)
        c.confirmStopResolved(); c.ok("confirmed")
        assertNull((c.s() as Screen.Today).model.stopOpen)
        c.openCheckIn(); c.submitCheckIn()
        val p = previewOf(c)
        assertEquals("the first session back is LIGHT", Tier.LIGHT, p.tier)
        assertTrue("nothing harder offered: ${p.canChoose}", p.canChoose.all { it < Tier.LIGHT })
    }

    @Test fun `S08 illness - no training that day, and the first session after it is lighter and says why`() = runBlocking {
        val w = World()
        val c = w.onboard()
        c.openCheckIn()
        c.editCheckIn { it.copy(illness = setOf("fever")) }
        c.submitCheckIn(); c.ok("fever")
        val sick = previewOf(c)
        assertEquals(Tier.RECOVERY, sick.tier)
        assertTrue("no training that day", sick.session.workout.items.isEmpty() && sick.session.workout.conditioning.isEmpty())
        assertTrue(sick.reasons.any { it.reason == ReasonKey.ILLNESS_REST_DAY })
        c.startWorkout()
        assertTrue("nothing to start", c.s() is Screen.Today)
        // The next days: the first session offered after the fever.
        var first: PreviewModel? = null
        for (d in 1..6) {
            w.day(d); c.onForeground()
            if (!(c.s() as Screen.Today).model.trainingDay()) continue
            c.openCheckIn(); c.submitCheckIn(); c.ok("day $d")
            first = previewOf(c)
            break
        }
        assertNotNull("a session after the illness", first)
        assertEquals("lighter", Tier.LIGHT, first!!.tier)
        assertTrue("says why", w.data().decisions.forDay(w.clock.today()).any { it.reason == ReasonKey.ILLNESS_RETURN.name })
    }

    @Test fun `S09 back after three weeks off at 66 - welcome back and a lighter first session with the return reason`() = runBlocking {
        val w = World()
        val c = w.onboard(older)
        val body = Body(older.strength)
        w.train(0 until 21, body)
        val before = w.data().sessions.done(monday, monday + 20)
        assertTrue("three weeks trained (${before.size})", before.size >= 6)
        // Three weeks without opening the app; back on the Monday after.
        val back = 42
        w.day(back); c.onForeground(); c.refreshToday()
        val t = (c.s() as Screen.Today).model
        assertTrue("welcome back", t.welcomeBack)
        assertTrue(t.nextIsToday)
        c.openCheckIn(); c.submitCheckIn(); c.ok("back")
        val p = previewOf(c)
        val ret = w.data().decisions.forDay(monday + back).filter { it.reason == ReasonKey.RETURN_RAMP.name || it.reason == ReasonKey.RECALIBRATE.name }
        assertTrue("the return reason: ${w.data().decisions.forDay(monday + back).map { it.reason }}", ret.isNotEmpty())
        assertTrue("with the age rule (AGE-001)", ret.any { RuleIds.REG_004 in it.ruleIds && RuleIds.AGE_001 in it.ruleIds })
        // Lighter than before the break: fewer sets than the last session of the same kind, and no heavier loads.
        val wk = p.session.workout
        val last = before.lastOrNull { it.template == p.session.day.template } ?: before.last()
        val setsBefore = last.exercises.filter { it.row.status != Status.SKIPPED }.sumOf { it.doc.sets }
        val setsNow = wk.items.sumOf { it.sets }
        println("METRIC S09 sets before the break $setsBefore, first session back $setsNow")
        assertTrue("fewer sets ($setsNow vs $setsBefore)", setsNow < setsBefore)
        // Each exercise's working load from its last exposure before the break: never more after it (REG-004 week 1: 80% of the
        // pre-break load, rounded down to real dumbbells).
        val loadsBefore = HashMap<String, Double>()
        for (s in before) for (e in s.exercises) e.working.mapNotNull { it.loadKg }.maxOrNull()?.let { loadsBefore[e.exerciseId] = it }
        var compared = 0
        for (item in wk.items) {
            val was = loadsBefore[item.exercise.id] ?: continue
            val now = item.load ?: continue
            compared++
            assertTrue("${item.exercise.id}: $now kg after the break, $was kg before", now <= was + 1e-9)
            if (!item.calibrating) assertTrue("${item.exercise.id}: recorded as reduced (${item.loadFactor})", item.loadFactor < 1.0)
        }
        assertTrue("weights compared ($compared)", compared >= 2)
    }

    @Test fun `S10 a whole week missed - welcome back without guilt, the best streak kept, the plan carries on`() = runBlocking {
        val w = World()
        val c = w.onboard()
        val body = Body(1.0)
        w.train(0 until 14, body)
        w.day(14); c.onForeground(); c.refreshToday()
        val bestBefore = (c.s() as Screen.Today).model.streak.best
        assertTrue("two weeks in a row ($bestBefore)", bestBefore >= 2)
        // Week 3: nothing. Back on the Monday after.
        w.day(21); c.onForeground(); c.refreshToday(); c.ok("back")
        val t = (c.s() as Screen.Today).model
        assertTrue("welcome back", t.welcomeBack)
        assertTrue("the best streak is kept (${t.streak.best} vs $bestBefore)", t.streak.best >= bestBefore)
        assertTrue("the plan carries on with the next session today", t.nextIsToday && t.next != null)
        assertEquals("no sessions added to make up", 3, t.plan.week.days.count { it.slots.isNotEmpty() || it.conditioning.isNotEmpty() })
        c.openCheckIn(); c.submitCheckIn(); c.ok("check-in")
        val p = previewOf(c)
        assertTrue(p.session.workout.items.isNotEmpty())
        assertTrue("the return after a week off (REG-003): ${w.data().decisions.forDay(monday + 21).map { it.reason }}",
            w.data().decisions.forDay(monday + 21).any { it.reason == ReasonKey.RETURN_MODIFIED.name })
        assertTrue("MODIFIED at most", p.tier <= Tier.MODIFIED)
        c.startWorkout(); w.doWorkout("S10 back", body); c.rateSummary(6); c.closeToToday()
        c.openTab(Tab.PROGRESS)
        val o = (c.s() as Screen.Progress).overview
        assertTrue("the progress tab keeps the best streak", o.streak.best >= bestBefore)
        assertTrue("and gives the welcome-back milestone (ADH-002): ${o.achievements}", com.personalfitnesscoach.engine.program.Achievement.WELCOME_BACK in o.achievements)
    }

    /**
     * Each Monday from week 2 to week 7, the share of the week's plan (in training stress, SSU) that is planned within the weekly limits,
     * for a person who does at most `perWeek` sessions a week. Null for a deload week.
     */
    private suspend fun keptByWeek(perWeek: Int, weeks: Int = 7): Map<Int, Double?> {
        val w = World()
        val c = w.onboard()
        val body = Body(1.0)
        val done = IntArray(weeks + 1)
        val kept = LinkedHashMap<Int, Double?>()
        for (d in 0 until weeks * 7) {
            val week = d / 7
            if (d % 7 == 0 && week >= 2) {
                w.day(d); c.onForeground()
                val (full, planned) = w.weekAgainstPlan()
                kept[week] = if (w.data().planToday()!!.week.deload) null else planned / full
            }
            if (done[week] >= perWeek) continue
            if (w.trainDay(d, body).trained) done[week]++
        }
        assertTrue("$perWeek sessions a week: ${done.toList()}", done.take(weeks - 1).all { it == perWeek })
        return kept
    }

    @Test fun `S11 two sessions in three for six weeks - the weekly limits don't fall with the missed sessions`() = runBlocking {
        val two = keptByWeek(2)
        val all = keptByWeek(3)
        println("METRIC S11 share of the week's plan kept by the weekly limits, by week: two in three " +
            two.entries.joinToString { (k, v) -> "$k ${v?.let { "%.0f%%".format(100 * it) } ?: "deload"}" } +
            "; every session " + all.entries.joinToString { (k, v) -> "$k ${v?.let { "%.0f%%".format(100 * it) } ?: "deload"}" })
        for ((week, share) in two) {
            share ?: continue
            val every = all[week] ?: continue
            // Registry 2.0.0: a week's limits are set by the sessions that were done, at the level they were planned, so turning up two
            // times in three keeps about the same share of the plan as turning up every time — the limits don't follow the misses down.
            assertTrue("week $week: two in three keeps ${"%.0f".format(100 * share)}%, every session ${"%.0f".format(100 * every)}%", share >= every - 0.10)
        }
        val later = two.filterKeys { it >= 4 }.values.filterNotNull()
        assertTrue("from week 4 the plan is kept: $two", later.isNotEmpty() && later.all { it >= 0.90 })
    }

    // ================================================================================================================ time
    @Test fun `S12 only 30 minutes - at most 33 minutes, keeping the warm-up and the first main lift, or the express session with the reason`() = runBlocking {
        val w = World()
        val c = w.onboard()
        val body = Body(1.0)
        w.train(0 until 7, body)
        var checked = 0
        for (d in 7 until 21) {
            w.day(d); c.onForeground(); c.refreshToday()
            val t = (c.s() as Screen.Today).model
            if (!t.trainingDay()) continue
            val firstMain = t.next!!.slots.firstOrNull { it.main }?.exercise?.id
            c.openCheckIn(); c.editCheckIn { it.copy(minutes = 30) }; c.submitCheckIn(); c.ok("day $d: 30 minutes")
            val p = previewOf(c)
            val wk = p.session.workout
            assertTrue("day $d: ${wk.plannedMinutes} min", wk.plannedMinutes <= 33.0 + 1e-9)
            if (p.session.express) assertTrue("day $d: the express session says why", p.reasons.any { it.reason == ReasonKey.TIME_NOT_FITTABLE || it.reason == ReasonKey.EXPRESS_SESSION })
            else {
                assertTrue("day $d: the warm-up stays", wk.warmupMinutes > 0.0)
                if (firstMain != null) assertTrue("day $d: the first main lift ($firstMain) stays: ${wk.items.map { it.exercise.id }}", wk.items.any { it.exercise.id == firstMain })
            }
            checked++
            c.startWorkout(); w.doWorkout("S12 day $d", body); c.rateSummary(6); c.closeToToday()
        }
        assertTrue("short days checked ($checked)", checked >= 4)
    }

    /** From the warm-up to two logged sets of the first exercise, then "only `minutes` left"; returns the view before and after. */
    private suspend fun World.onlyLeft(minutes: Int, body: Body): Triple<PlayerView, PlayerView, WorkoutNotice.Replanned> {
        c.openCheckIn(); c.submitCheckIn(); c.startWorkout(); c.ok("start")
        toLifts()
        val first = (view().step as Step.Lift).lift.rowId
        logNext(first, body); logNext(first, body)
        val before = view()
        c.openChangeTime()
        assertTrue((c.s() as Screen.Workout).sheet is WorkoutSheet.ChangeTime)
        c.changeTime(minutes); c.ok("$minutes minutes")
        val s = c.s() as Screen.Workout
        return Triple(before, s.view, s.notice as WorkoutNotice.Replanned)
    }

    private fun checkRefit(where: String, before: PlayerView, v: PlayerView, notice: WorkoutNotice.Replanned) {
        assertTrue("$where: something was cut: ${notice.changes}", notice.changes.isNotEmpty())
        assertNotEquals("$where: the warm-up done is not asked again", Stage.WARMUP, v.stage)
        assertTrue("$where: the cool-down stays", Stage.COOLDOWN in v.stages)
        assertEquals("$where: the cool-down keeps its length", before.workout.doc.cooldownMinutes, v.workout.doc.cooldownMinutes)
        for (l in before.lifts) assertEquals("$where: logged sets stay", l.sets.size, v.lifts.first { it.rowId == l.rowId }.sets.size)
    }

    @Test fun `S13 only 15 minutes left mid-workout - the rest is fitted again, the warm-up done, the cool-down and the logged sets stay`() = runBlocking {
        val body = Body(1.0)
        // Once the weights are found: what is left fits the 15 minutes, and what was cut beyond the need comes back — so no more
        // than one exercise's set and rest (about 4 minutes) is left unused (TIME-001/002, D-115).
        val w = World()
        val c = w.onboard()
        w.train(0 until 21, body)
        var d = 21
        while (true) {
            w.day(d); c.onForeground(); c.refreshToday()
            val t = (c.s() as Screen.Today).model
            if (t.trainingDay()) {
                c.openCheckIn(); c.submitCheckIn()
                if (previewOf(c).session.workout.items.none { it.calibrating }) { c.closeToToday(); break }
                c.startWorkout(); w.doWorkout("day $d", body); c.rateSummary(6); c.closeToToday()
            }
            assertTrue("a day with every weight found", ++d < 49)
        }
        val (before, v, notice) = w.onlyLeft(15, body)
        println("METRIC S13 'only 15 minutes left': ${v.remainingMinutes} min left to do (was ${before.remainingMinutes})")
        assertTrue("fitted within 15 minutes: ${v.remainingMinutes}", v.remainingMinutes <= 15.5)
        assertTrue("and uses them, giving back what fits: ${v.remainingMinutes} min, changes ${notice.changes}", v.remainingMinutes >= 15.0 - 4.0)
        assertNull(notice.shortestSafeMinutes)
        checkRefit("weights found", before, v, notice)
        val id = v.workoutId
        val logged = before.lifts.sumOf { it.sets.size }
        w.doWorkout("S13", body)
        val stored = w.data().sessions.load(id)!!
        assertEquals(Status.DONE, stored.row.status)
        assertTrue("the logged sets are in the session", stored.exercises.sumOf { it.sets.size } >= logged)

        // While weights are still being found: a ramp can't be cut set by set, so it stays whole (a main lift's) or goes; when even
        // that is more than 15 minutes, the shortest safe rest of the session is shown instead (D-071).
        val w2 = World()
        val c2 = w2.onboard()
        w2.train(0 until 7, body)
        w2.day(7); c2.onForeground()
        val (b2, v2, n2) = w2.onlyLeft(15, body)
        val ramps = b2.lifts.filter { !it.finished && it.item.calibrating }.map { it.rowId }.toSet()
        println("METRIC S13 'only 15 minutes left' while weights are found: ${v2.remainingMinutes} min left (was ${b2.remainingMinutes}), " +
            "shortest ${n2.shortestSafeMinutes}, ${ramps.size} ramps open, changes ${n2.changes}")
        assertTrue("a weight is still being found when time runs short", ramps.isNotEmpty())
        checkRefit("weights being found", b2, v2, n2)
        if (n2.shortestSafeMinutes == null) {
            assertTrue("fitted: ${v2.remainingMinutes}", v2.remainingMinutes <= 15.5)
            // Something that could be cut set by set went: only because the time was needed. The estimate counts an open ramp at a
            // typical 3 sets, the fit at its full 5 — at most about 2.5 minutes a set more for each ramp still open.
            val cut = n2.changes.any { (it is Change.Skipped && it.rowId !in ramps) || it is Change.SetsReduced }
            val openRamps = v2.lifts.count { !it.finished && it.rowId in ramps }
            if (cut) assertTrue("cut only as needed: ${v2.remainingMinutes} min with $openRamps ramps open, changes ${n2.changes}",
                v2.remainingMinutes + 5.0 * openRamps >= 15.0 - 4.0)
        } else assertTrue("only what can't be cut is left: ${v2.lifts.filter { !it.finished }.map { it.exercise.id to it.item.priority }}",
            v2.lifts.filter { !it.finished }.all { it.item.priority <= com.personalfitnesscoach.engine.planning.Priority.P2 })
    }

    @Test fun `S14 90 minutes on a 60-minute plan - the session stays the planned length`() = runBlocking {
        val w = World()
        val c = w.onboard()
        val body = Body(1.0)
        w.train(0 until 7, body)
        w.day(7); c.onForeground()
        assertEquals("a 60-minute plan", 60, w.data().docs.get(Profile)!!.sessionMinutes)
        c.openCheckIn(); c.editCheckIn { it.copy(minutes = 90) }; c.submitCheckIn(); c.ok("90 minutes")
        val long = previewOf(c).session.workout
        c.previewMinutes(60); c.ok("60 minutes")
        val usual = previewOf(c).session.workout
        assertEquals("no extra training", usual.items.map { it.exercise.id to it.sets }, long.items.map { it.exercise.id to it.sets })
        assertEquals(usual.conditioning.map { it.workMinutes }, long.conditioning.map { it.workMinutes })
        assertTrue("${long.plannedMinutes} vs ${usual.plannedMinutes}", long.plannedMinutes <= usual.plannedMinutes + 0.5)
        assertTrue(w.data().decisions.forDay(monday + 7).none { it.reason == ReasonKey.TIME_EXTENDED.name })
    }

    // ================================================================================================================ in the workout
    @Test fun `S15 the machine is busy - up to three replacements available here, a swap changes it, do it later moves it to the end`() = runBlocking {
        val w = World()
        val c = w.onboard()
        val body = Body(1.0)
        w.train(0 until 7, body)
        w.day(7); c.onForeground()
        c.openCheckIn(); c.submitCheckIn(); c.startWorkout(); c.ok("start")
        w.toLifts()
        val waiting = w.view().lifts.filter { !it.hasLoggedWork && !it.finished }
        assertTrue("exercises to come (${waiting.size})", waiting.size >= 3)
        val busy = waiting[1]
        c.openReplace(busy.rowId, occupied = true); c.ok("occupied")
        val choice = ((c.s() as Screen.Workout).sheet as WorkoutSheet.Replace).choice
        assertTrue("1 to 3 replacements: ${choice.options.map { it.exercise.id }}", choice.options.size in 1..3)
        val gym = w.data().docs.get(EquipmentRecord)!!.gym
        assertTrue("all available in this gym", choice.options.all { it.exercise.usableWith(gym) })
        assertTrue("each with how well it fits (the reason shown)", choice.options.all { it.score > 0.0 && it.fits.pattern > 0.0 })
        assertTrue("the reason is recorded", choice.decisions.isNotEmpty())
        assertTrue("do it later is offered", choice.canDoLater)
        val pick = choice.options.first().exercise.id
        c.swap(busy.rowId, pick); c.ok("swap")
        val swapped = c.s() as Screen.Workout
        assertTrue("the swap was accepted: ${swapped.notice}", swapped.notice is WorkoutNotice.Replanned)
        assertEquals(pick, swapped.view.lifts.first { it.rowId == busy.rowId }.exercise.id)
        val later = waiting[2]
        c.doLater(later.rowId); c.ok("later")
        assertEquals("moved to the end", later.rowId, w.view().lifts.maxByOrNull { it.position }!!.rowId)
        w.doWorkout("S15", body)
        assertTrue(c.s() is Screen.Done)
    }

    /** The first day whose session has an exercise loading the knee more than a little, started and at the lifts. */
    private suspend fun World.kneeDay(body: Body): Long {
        for (d in 0 until 14) {
            day(d); c.onForeground(); c.refreshToday()
            val t = (c.s() as Screen.Today).model
            if (!t.trainingDay()) continue
            c.openCheckIn(); c.submitCheckIn(); c.ok("day $d")
            if (previewOf(c).session.workout.items.none { it.exercise.stress(Joint.KNEE) >= 2 }) {
                c.startWorkout(); doWorkout("day $d", body); c.rateSummary(6); c.closeToToday(); continue
            }
            c.startWorkout(); toLifts()
            return view().lifts.first { !it.finished && it.exercise.stress(Joint.KNEE) >= 2 }.rowId
        }
        error("no session loaded the knee")
    }

    @Test fun `S16 knee pain 5 of 10 mid-set - that exercise stops, pain-free alternatives, nothing loads the knee more than a little`() = runBlocking {
        val w = World()
        val c = w.onboard()
        val body = Body(1.0)
        val knee = w.kneeDay(body)
        c.select(knee)
        w.logNext(knee, body)
        val setsBefore = w.view().lifts.associate { it.rowId to it.sets.size }
        c.openHurts(knee)
        c.editHurts { it.copy(region = Joint.KNEE, kind = PainKind.JOINT_OR_TENDON, rating = 5) }
        c.submitHurts(); c.ok("pain")
        val s = c.s() as Screen.Workout
        val res = s.sheet as WorkoutSheet.HurtsResult
        assertEquals(PainAction.STOP_EXERCISE, res.outcome.action)
        val stopped = s.view.lifts.first { it.rowId == knee }
        assertTrue("that exercise stops", stopped.finished)
        assertEquals("its logged set stays", setsBefore[knee], stopped.sets.size)
        val alt = res.alternatives
        assertNotNull("pain-free alternatives are offered", alt)
        assertTrue("alternatives: ${alt!!.options.map { it.exercise.id to it.exercise.stress(Joint.KNEE) }}",
            alt.options.isNotEmpty() && alt.options.all { it.exercise.stress(Joint.KNEE) <= P.SAF_003.substitute_max_joint_stress })
        val open = s.view.lifts.filter { !it.finished }
        assertTrue("nothing left loads the knee more than a little: ${open.map { it.exercise.id to it.exercise.stress(Joint.KNEE) }}",
            open.all { it.exercise.stress(Joint.KNEE) <= P.SAF_003.substitute_max_joint_stress })
        c.closeSheet()
        c.takeAlternative(knee, alt.options.first().exercise.id, Joint.KNEE); c.ok("alternative")
        val id = w.view().workoutId
        w.doWorkout("S16", body)
        // After the report, nothing that loads the knee more than a little got a set.
        for (e in w.data().sessions.load(id)!!.exercises) if (com.personalfitnesscoach.engine.library.Library.require(e.exerciseId).stress(Joint.KNEE) > P.SAF_003.substitute_max_joint_stress)
            assertEquals("${e.exerciseId}: no set after the pain report", setsBefore[e.row.id] ?: 0, e.sets.size)
    }

    @Test fun `S17 sharp pain 8 of 10 - the region stops and no exercise for it is offered again that day`() = runBlocking {
        val w = World()
        val c = w.onboard()
        val body = Body(1.0)
        val knee = w.kneeDay(body)
        c.select(knee)
        w.logNext(knee, body)
        val setsBefore = w.view().lifts.associate { it.rowId to it.sets.size }
        c.openHurts(knee)
        c.editHurts { it.copy(region = Joint.KNEE, kind = PainKind.JOINT_OR_TENDON, rating = 8, descriptors = setOf("sharp")) }
        c.submitHurts(); c.ok("sharp pain")
        val s = c.s() as Screen.Workout
        val res = s.sheet as WorkoutSheet.HurtsResult
        assertEquals(PainAction.STOP_REGION, res.outcome.action)
        assertTrue("see a professional", res.outcome.suggestProfessional)
        val open = s.view.lifts.filter { !it.finished }
        assertTrue("exercises still to do", open.any { !it.hasLoggedWork })
        assertTrue("nothing left loads the knee: ${open.map { it.exercise.id to it.exercise.stress(Joint.KNEE) }}", open.all { it.exercise.stress(Joint.KNEE) == 0 })
        c.closeSheet()
        for (l in open.filter { !it.hasLoggedWork }) {
            c.openReplace(l.rowId, occupied = false); c.ok("replace ${l.exercise.id}")
            val options = ((c.s() as Screen.Workout).sheet as WorkoutSheet.Replace).choice.options
            assertTrue("${l.exercise.id}: no knee exercise offered: ${options.map { it.exercise.id to it.exercise.stress(Joint.KNEE) }}",
                options.all { it.exercise.stress(Joint.KNEE) == 0 })
            c.closeSheet()
        }
        val id = w.view().workoutId
        w.doWorkout("S17", body)
        for (e in w.data().sessions.load(id)!!.exercises) if (com.personalfitnesscoach.engine.library.Library.require(e.exerciseId).stress(Joint.KNEE) > 0)
            assertEquals("${e.exerciseId}: no set after the pain report", setsBefore[e.row.id] ?: 0, e.sets.size)
        c.rateSummary(6); c.closeToToday()
        val t = (c.s() as Screen.Today).model
        assertTrue("the day is done: nothing more today", t.doneToday)
        assertTrue("Today says why", t.reasons.any { it.reason == ReasonKey.PAIN_STOP_REGION.name })
    }

    @Test fun `S18 fainting during the workout - it ends at once, the stop shows, nothing is left running`() = runBlocking {
        val w = World()
        val c = w.onboard()
        val body = Body(1.0)
        c.openCheckIn(); c.submitCheckIn(); c.startWorkout()
        w.toLifts()
        val row = (w.view().step as Step.Lift).lift.rowId
        w.logNext(row, body)
        val id = w.view().workoutId
        val alarms = w.platform.cancelled
        c.openRedFlag()
        c.submitRedFlag(setOf("fainting_or_near_fainting")); c.ok("fainting")
        assertTrue("the stop screen", c.s() is Screen.Stop)
        assertNull("no workout left running", w.data().sessions.active())
        assertTrue("the rest alarm is cancelled", w.platform.cancelled > alarms)
        assertTrue("kept as stopped for safety", w.data().sessions.load(id)!!.doc.stoppedBySafety)
        // Opened again: no workout to resume, and the stop is still open.
        val c2 = w.restart()
        c2.start(); c2.ok("reopened")
        assertTrue("not offered back: ${c2.s()}", c2.s() is Screen.Today)
        assertNotNull((c2.s() as Screen.Today).model.stopOpen)
    }

    @Test fun `S19 finding a working weight - at most 5 ramp sets, never to failure, and the next session uses it`() = runBlocking {
        val w = World()
        w.onboard()
        // Six weeks as planned, then every ramp in the log is checked against the next time the exercise came round, by CAL-001's own
        // words (read from the stored sets, not from the app's state): RIR ≤ 2 → stop, next time lighter; RIR 3 → the working weight;
        // still easy after 5 sets → carry on from there next time, within 4 sessions; no safe heavier step (D-038) → the working weight.
        // "Confirm at the second exposure" means working sets, not another ramp (D-115).
        w.train(0 until 42, Body(1.0))
        val log = w.data().sessions.done(monday, monday + 41)
        val loaded = setOf(LoadType.BARBELL, LoadType.DUMBBELL, LoadType.KETTLEBELL, LoadType.STACK)
        val byExercise = LinkedHashMap<String, MutableList<com.personalfitnesscoach.data.core.session.StoredExercise>>()
        for (wk in log) for (e in wk.exercises) if (e.row.status == Status.DONE) byExercise.getOrPut(e.exerciseId) { ArrayList() } += e
        val cases = HashMap<String, Int>()
        for ((id, exposures) in byExercise) {
            val ex = com.personalfitnesscoach.engine.library.Library.require(id)
            if (ex.loadType !in loaded || ex.assisted || ex.unit != DoseUnit.REPS) continue
            assertTrue("$id: weight found within ${P.CAL_001.sessions[1]} sessions: ${exposures.count { it.doc.calibrating }}",
                exposures.count { it.doc.calibrating } <= P.CAL_001.sessions[1])
            for ((i, a) in exposures.withIndex()) {
                if (!a.doc.calibrating || a.calibration.isEmpty()) continue
                val ramp = a.calibration.sortedBy { it.setIndex }
                assertTrue("$id: ${ramp.size} ramp sets", ramp.size <= P.CAL_001.max_ramp_sets)
                // A ramp ends at the first set with 2 or fewer in reserve (RPE 8): it never asks for a set closer to failure.
                assertTrue("$id: ${ramp.map { it.rir }}", ramp.dropLast(1).all { (it.rir ?: 5.0) > P.CAL_001.stop_rir_lte })
                val last = ramp.last()
                val rir = last.rir ?: continue
                val load = last.loadKg!!
                val b = exposures.getOrNull(i + 1) ?: continue
                val nth = exposures.take(i + 1).count { it.doc.calibrating }
                val case = when {
                    rir <= P.CAL_001.stop_rir_lte -> "too hard"
                    rir < 4.0 -> "found"
                    ramp.size >= P.CAL_001.max_ramp_sets && nth < P.CAL_001.sessions[1] -> "still easy"
                    else -> "working weight"
                }
                cases.merge(case, 1, Int::plus)
                when (case) {
                    "still easy" -> {
                        assertTrue("$id: a ramp still easy after 5 sets goes on next time", b.doc.calibrating)
                        assertEquals("$id: from where it ended", load, b.calibration.sortedBy { it.setIndex }.firstOrNull()?.loadKg ?: b.doc.load!!, 1e-9)
                    }
                    "too hard" -> {
                        assertFalse("$id: after a too-hard ramp set, working sets next time", b.doc.calibrating)
                        assertTrue("$id: lighter than the too-hard set: ${b.doc.load} after $load", b.doc.load!! < load - 1e-9)
                    }
                    else -> {
                        assertFalse("$id ($case): the weight found is confirmed with working sets next time, not ramped to again", b.doc.calibrating)
                        // The same weight; at most one small step more when the found set also gave an estimated max and today's
                        // target is 2 in reserve rather than the ramp's 3 (INT-005).
                        assertTrue("$id ($case): ${b.doc.load} after $load", b.doc.load!! <= load * 1.06 + 1e-9)
                        assertTrue("$id ($case): working sets done", b.working.isNotEmpty())
                    }
                }
            }
        }
        println("METRIC S19 ramps by how they ended: $cases")
        assertTrue("both a ramp that went on and a weight found were seen: $cases", (cases["still easy"] ?: 0) >= 1 &&
            (cases["found"] ?: 0) + (cases["too hard"] ?: 0) + (cases["working weight"] ?: 0) >= 3)
    }

    /** One non-lift step of the workout, done as planned. */
    private suspend fun World.doWorkoutStep(step: Step) {
        when (step) {
            is Step.Warmup -> { clock.ms += (step.minutes * 60_000).toLong(); c.completeStage(Stage.WARMUP) }
            is Step.BoneLoading -> c.completeStage(Stage.BONE_LOADING)
            is Step.Conditioning -> { clock.ms += (step.item.workMinutes * 60_000).toLong(); c.logConditioning(step.index, step.item.workMinutes) }
            is Step.Balance -> c.completeStage(Stage.BALANCE)
            is Step.Cooldown -> c.completeStage(Stage.COOLDOWN)
            else -> Unit
        }
        c.ok("step")
    }

    @Test fun `S20 a short day with a superset - the pair alternates with a short rest, and skipping one gives the other its own rest`() = runBlocking {
        val w = World()
        val c = w.onboard()
        val body = Body(1.0)
        w.train(0 until 14, body)
        var paired = false
        for (d in 14 until 28) {
            w.day(d); c.onForeground(); c.refreshToday()
            val t = (c.s() as Screen.Today).model
            if (!t.trainingDay()) continue
            c.openCheckIn(); c.editCheckIn { it.copy(minutes = 40) }; c.submitCheckIn()
            for (m in listOf(40, 35, 45, 50)) {
                if (previewOf(c).session.workout.items.any { it.supersetWith != null }) break
                c.previewMinutes(m)
            }
            if (previewOf(c).session.workout.items.none { it.supersetWith != null }) {
                c.startWorkout(); w.doWorkout("day $d", body); c.rateSummary(6); c.closeToToday(); continue
            }
            c.startWorkout(); w.toLifts()
            // Play in order up to the first working set of a superset.
            var guard = 0
            while (guard++ < 200) {
                val v = w.view()
                val lift = (v.step as? Step.Lift)?.lift ?: break
                val partner = SessionPlayer.supersetPartner(lift, v.lifts)
                if (lift.next?.kind == SetKind.WARMUP) { c.skipRamp(lift.rowId); continue }
                w.logNext(lift.rowId, body)
                if (partner == null || partner.next?.kind != SetKind.WORKING || lift.item.sets - lift.workingDone < 2) continue
                assertTrue("the pair doesn't compete (ORD-003): ${lift.exercise.primary} / ${partner.exercise.primary}",
                    (lift.exercise.primary intersect partner.exercise.primary).isEmpty() && !lift.item.main && !partner.item.main)
                val after = w.view()
                assertEquals("the partner's set is next", partner.rowId, (after.step as Step.Lift).lift.rowId)
                assertEquals("after the superset's short rest (REST-004)", P.REST_004.default_s, after.state.restSec)
                // The partner's set, then back to the first exercise, again after the short rest: the sets alternate.
                w.logNext(partner.rowId, body)
                val back = w.view()
                assertEquals("back to the first exercise", lift.rowId, (back.step as Step.Lift).lift.rowId)
                assertEquals(P.REST_004.default_s, back.state.restSec)
                // The partner is skipped now: the superset ends and the first exercise gets its own rest.
                c.skipExercise(partner.rowId); c.ok("skip the partner")
                val v2 = w.view()
                val next = (v2.step as Step.Lift).lift
                assertEquals("the first exercise goes on", lift.rowId, next.rowId)
                assertNull("the superset has ended", SessionPlayer.supersetPartner(next, v2.lifts))
                assertTrue("its own rest (${v2.state.restSec} s, minimum ${next.item.restMinSec} s)", (v2.state.restSec ?: 0) >= next.item.restMinSec)
                paired = true
                break
            }
            w.doWorkout("S20 day $d", body)
            if (paired) break
            c.rateSummary(6); c.closeToToday()
        }
        assertTrue("a short day had a superset", paired)
    }

    @Test fun `S21 the app is killed mid-workout - offered back exactly where it stopped, with every set saved`() = runBlocking {
        val w = World()
        val c = w.onboard()
        val body = Body(1.0)
        w.train(0 until 7, body)
        w.day(7); c.onForeground()
        c.openCheckIn(); c.submitCheckIn(); c.startWorkout()
        w.toLifts()
        val lifts = w.view().lifts.filter { !it.finished }
        w.logNext(lifts[0].rowId, body); w.logNext(lifts[0].rowId, body)
        c.select(lifts[1].rowId); w.logNext(lifts[1].rowId, body)
        val before = w.view()
        // Process death: the app is started again over the same storage.
        val c2 = w.restart()
        c2.start(); c2.ok("restart")
        val r = c2.s() as Screen.Resume
        assertEquals("every set saved", before.lifts.map { it.rowId to it.sets.size }, r.view.lifts.map { it.rowId to it.sets.size })
        c2.resumeWorkout(); c2.ok("resume")
        val after = w.view()
        assertEquals("exactly where it stopped", before.step, after.step)
        assertEquals("the rest timer too", before.restEndsAtMs, after.restEndsAtMs)
        val id = after.workoutId
        val more = w.doWorkout("S21", body)
        val stored = w.data().sessions.load(id)!!
        assertEquals(before.lifts.sumOf { it.sets.size } + more, stored.exercises.sumOf { it.sets.size })
    }

    // ================================================================================================================ the coach
    @Test fun `S22 Why on today's session - each reason opens its rules with their titles and how sure the evidence is`() = runBlocking {
        val w = World()
        val c = w.onboard()
        c.openCheckIn(); c.editCheckIn { it.copy(sleep = 2, sleepHours = 5.0) }; c.submitCheckIn()
        val p = previewOf(c)
        val reasons = p.reasons.distinctBy { it.reason }
        assertTrue(reasons.isNotEmpty())
        for (r in reasons) {
            assertTrue("${r.reason}: names its rules", r.ruleIds.isNotEmpty())
            c.openWhy(r.reason.name, r.ruleIds); c.ok("why ${r.reason}")
            val why = (c.s() as Screen.Coach).model.why!!
            assertEquals(r.reason.name, why.reason)
            assertEquals(r.ruleIds.distinct(), why.rules.map { it.id })
            assertTrue("${r.reason}: titles and evidence: ${why.rules}", why.rules.all { it.title != null && it.evidence != null })
            c.closeWhy()
            assertTrue("back to the preview", c.s() is Screen.Preview)
        }
    }

    @Test fun `S23 out-of-scope questions - a supplement question is referred, chest pain leads to the warning-sign stop`() = runBlocking {
        val w = World()
        val c = w.onboard()
        c.openTab(Tab.COACH)
        c.editCoachText("Which supplements should I take for muscle?"); c.askCoach(); c.ok("supplements")
        val a = (c.s() as Screen.Coach).model.answer!!
        assertEquals(OutOfScope.SUPPLEMENTS, a.outOfScope)
        assertTrue("referred, not answered", a.facts.isEmpty() && a.actions.isEmpty())
        assertTrue(RuleIds.SAF_009 in a.rules)
        c.editCoachText("I have chest pain"); c.askCoach(); c.ok("chest pain")
        val urgent = (c.s() as Screen.Coach).model.answer!!
        assertEquals(OutOfScope.URGENT_SYMPTOMS, urgent.outOfScope)
        assertEquals(listOf<CoachAction>(CoachAction.OpenWarningSigns), urgent.actions)
        c.coachAction(urgent.actions.single())
        c.pickWarningSign("chest_pain_pressure_tightness", true)
        c.reportWarningSigns(); c.ok("warning sign")
        assertTrue(c.s() is Screen.Stop)
        c.refreshToday()
        assertNotNull("training stopped until confirmed", (c.s() as Screen.Today).model.stopOpen)
    }

    @Test fun `S24 Ask Gemini - the exact text is shown first, and a health question is referred, not shared`() = runBlocking {
        val w = World()
        val c = w.onboard()
        c.openCheckIn(); c.submitCheckIn(); c.closeToToday()
        c.openTab(Tab.COACH)
        val q = "How can I make my squat feel more stable?"
        c.editCoachText(q); c.prepareGeminiShare(); c.ok("share")
        val share = (c.s() as Screen.Coach).model.share!!
        assertEquals("the question, as typed", q, share.question)
        val planned = w.data().planToday()!!.next!!.slots.map { it.exercise.id }
        assertTrue("only today's exercise names", share.exercises.isNotEmpty() && planned.containsAll(share.exercises))
        // Nothing personal beyond the plan's shape: no loads, records, answers or measurements are in the shared content at all.
        assertEquals(setOf("template", "minutes", "tier", "exercises", "reasons", "gentleMode", "question"),
            com.personalfitnesscoach.data.core.coach.ShareContent::class.java.declaredFields.map { it.name }.filter { !it.startsWith("$") }.toSet())
        c.closeGeminiShare()
        for ((text, scope) in listOf("My knee hurts when I squat, what should I do?" to OutOfScope.PAIN,
                "Can I take ibuprofen before training?" to OutOfScope.MEDICATION, "I feel chest pain when running" to OutOfScope.URGENT_SYMPTOMS)) {
            c.editCoachText(text); c.prepareGeminiShare(); c.ok(text)
            val m = (c.s() as Screen.Coach).model
            assertNull("$text: not shared", m.share)
            assertEquals("$text: referred", scope, m.answer!!.outOfScope)
        }
    }

    // ================================================================================================================ progress and data
    @Test fun `S25 tired for days in a row - a lighter week with about half the sets and the reason`() = runBlocking {
        val w = World()
        val c = w.onboard()
        val body = Body(1.0)
        val weekSets = HashMap<Int, Int>()
        fun sets(t: TodayModel) = t.plan.week.days.sumOf { d -> d.slots.sumOf { it.sets } }
        var lighter: Int? = null
        for (d in 0 until 7 * 7) {
            if (d % 7 == 0) {
                w.day(d); c.onForeground(); c.refreshToday()
                val t = (c.s() as Screen.Today).model
                weekSets[d / 7] = sets(t)
                if (d >= 21 && (t.plan.week.deload || t.plan.program.lighterThisWeek)) { lighter = d / 7; break }
            }
            // Two normal weeks, then tired: poor sleep and energy, very sore, every session very hard, the main lifts falling short.
            val tired = d >= 14
            w.trainDay(d, body, checkIn = { if (tired) it.copy(sleep = 2, energy = 2, soreness = 1, stress = 2, sleepHours = 5.5) else it },
                rpe = if (tired) 9 else 6, tired = tired)
        }
        println("METRIC S25 planned sets by week: ${weekSets.toSortedMap()}, lighter week: $lighter")
        assertNotNull("a lighter week was planned", lighter)
        val week = lighter!!
        val reasons = w.data().decisions.forDay(monday + 7 * week).map { it.reason }
        assertTrue("with the reason: $reasons", reasons.any { it == ReasonKey.DELOAD_NOW.name || it == ReasonKey.LIGHTER_WEEK.name })
        assertTrue("Today says why", (c.s() as Screen.Today).model.reasons.any { it.reason == ReasonKey.DELOAD_NOW.name || it.reason == ReasonKey.LIGHTER_WEEK.name })
        val t = (c.s() as Screen.Today).model
        // Hard sessions, poor check-ins, soreness and stalled lifts are 3 or more fatigue signals at 41: a deload now (DEL-002).
        assertTrue("a deload week (DEL-002/003), not only a lighter week", t.plan.week.deload)
        assertTrue(reasons.contains(ReasonKey.DELOAD_NOW.name))
        // DEL-003: at most half the sets of the normal week it replaces — that week planned here without the deload, independently of
        // the deload's own numbers. Each day is rounded down and every exercise keeps at least a set or goes, so it is 35–50%.
        val dd = w.data()
        val tp = dd.planToday()!!
        val input = dd.bridge.weekInput(tp.user, tp.program)
        val normal = WeekPlanner.plan(input.copy(week = input.week.copy(kind = com.personalfitnesscoach.engine.program.WeekKind.LOADING), deload = false,
            weeklySsuLimit = null, weeklyLoadSsu = null, weeklySsuGuard = null), dd.bridge.blueprint(tp.program)).value
        val normalSets = normal.days.sumOf { d -> d.slots.sumOf { it.sets } }
        val now = weekSets.getValue(week)
        println("METRIC S25 the lighter week: $now sets, against $normalSets in the normal week it replaces and ${weekSets[week - 1]} the week before")
        assertTrue("about half the sets: $now of $normalSets", now <= 0.5 * normalSets + 1e-9 && now >= 0.35 * normalSets)
        assertTrue("easy effort in the lighter week (RIR ${P.DEL_003.min_rir}+)", t.plan.week.days.all { d -> d.slots.all { it.targetRir >= P.DEL_003.min_rir } })
        assertTrue("no intervals in it (DEL-003)", t.plan.week.days.flatMap { it.conditioning }.none { it.hiit || it.interval != null })
    }

    @Test fun `S26 six weeks of training - progress shows rising strength, weekly minutes against 150 and the weeks in a row`() = runBlocking {
        val w = World()
        val c = w.onboard()
        w.train(0 until 42, Body(1.0))
        w.day(42); c.onForeground()
        c.openTab(Tab.PROGRESS); c.ok("progress")
        val o = (c.s() as Screen.Progress).overview
        assertTrue("sessions counted (${o.sessionsCompleted})", o.sessionsCompleted >= 15)
        val rising = o.lifts.filter { l ->
            val pts = l.points
            val e = pts.mapNotNull { it.e1rm }
            pts.size >= 3 && ((e.size >= 3 && e.last() > e.first()) || pts.last().bestLoad > pts.first().bestLoad ||
                (pts.last().bestLoad == pts.first().bestLoad && pts.last().bestReps > pts.first().bestReps))
        }
        assertTrue("rising strength: ${o.lifts.map { l -> l.exerciseId to l.points.map { it.bestLoad to it.bestReps } }}", rising.isNotEmpty())
        assertEquals("the weekly target is 150 minutes (PH-001)", 150, P.PH_001.target_equivalent_minutes)
        assertTrue("the finished weeks are shown (${o.weeks.size})", o.weeks.size >= 5)
        assertTrue("weekly minutes counted: ${o.weeks.map { it.equivalentMinutes }}", o.weeks.all { it.equivalentMinutes > 0.0 })
        // The line on each week: 150 equivalent minutes and 2 strength days (PH-001).
        for (wl in o.weeks) assertEquals("week ${wl.weekStartDay}", wl.equivalentMinutes >= 150.0 && wl.strengthDays >= 2, wl.whoFloorMet)
        assertTrue("strength days counted: ${o.weeks.map { it.strengthDays }}", o.weeks.all { it.strengthDays >= 2 })
        assertEquals(o.weeks.count { it.whoFloorMet }, o.whoWeeks)
        println("METRIC S26 weekly minutes: ${o.weeks.map { "%.0f".format(it.equivalentMinutes) }}, weeks at 150+: ${o.whoWeeks}, streak ${o.streak.current}")
        assertTrue("weeks in a row: ${o.streak}", o.streak.current >= 5)
        c.openTab(Tab.TODAY)
        assertEquals("Today shows the same weeks in a row", o.streak.current, (c.s() as Screen.Today).model.streak.current)
    }

    @Test fun `S27 correcting a past set - saved, the estimated maximum follows, impossible values are refused`() = runBlocking {
        val w = World()
        val c = w.onboard()
        w.train(0 until 21, Body(1.0))
        // A lift with an estimated maximum (INT-004) and a session with working sets of it.
        val tracked = w.data().docs.all(ExerciseState).filter { com.personalfitnesscoach.engine.library.Library[it.exerciseId]?.trackE1rm == true }.map { it.exerciseId }.toSet()
        val workout = w.data().sessions.done(monday, monday + 20).last { wk -> wk.exercises.any { it.exerciseId in tracked && it.working.any { s -> (s.loadKg ?: 0.0) > 0.0 } } }
        val lift = workout.exercises.first { it.exerciseId in tracked && it.working.any { s -> (s.loadKg ?: 0.0) > 0.0 } }.exerciseId
        c.openExercise(lift); c.ok("exercise")
        val point = (c.s() as Screen.ExerciseDetail).history.points.first { it.day == workout.day }
        val stateBefore = (c.s() as Screen.ExerciseDetail).history.state?.e1rm
        c.openTab(Tab.HISTORY)
        c.openSession(workout.id); c.ok("session")
        val detail = c.s() as Screen.SessionDetail
        val set = detail.detail.exercises.first { it.exerciseId == lift }.sets.filter { it.kind == SetKind.WORKING }.maxByOrNull { (it.loadKg ?: 0.0) * 100 + (it.reps ?: 0) }!!
        // The set was really 2 more kilos for 8 reps, 2 in reserve (an effort the estimate can use, INT-004).
        val load = set.loadKg!! + 2.5
        c.startSetEdit(set.setId)
        c.changeSetEdit { it.copy(loadKg = load, reps = 8, rir = 2.0) }
        c.saveSetEdit(); c.ok("corrected")
        val saved = (c.s() as Screen.SessionDetail).detail.exercises.flatMap { it.sets }.first { it.setId == set.setId }
        assertEquals("saved", 8, saved.reps)
        assertEquals(load, saved.loadKg!!, 0.0)
        c.openExercise(lift)
        val h = (c.s() as Screen.ExerciseDetail).history
        val after = h.points.first { it.day == workout.day }
        val expected = com.personalfitnesscoach.engine.calc.E1rm.fromSet(load, 8, 2.0)!!
        assertEquals("the estimated maximum follows: ${point.e1rm} → ${after.e1rm}", expected, after.e1rm!!, 0.01)
        assertNotEquals("and the lift's estimate with it", stateBefore, h.state?.e1rm)
        // Impossible values are refused, and nothing changes.
        c.openTab(Tab.HISTORY); c.openSession(workout.id)
        for (bad in listOf<(SetEdit) -> SetEdit>({ it.copy(reps = -3) }, { it.copy(loadKg = 600.0) }, { it.copy(loadKg = 2000.0) }, { it.copy(reps = 500) }, { it.copy(rir = 12.0) })) {
            c.startSetEdit(set.setId); c.changeSetEdit(bad); c.saveSetEdit()
            assertTrue("refused", (c.s() as Screen.SessionDetail).problem)
            c.cancelSetEdit()
            assertEquals("nothing changed", saved, (c.s() as Screen.SessionDetail).detail.exercises.flatMap { it.sets }.first { it.setId == set.setId })
        }
    }

    @Test fun `S28 backup, erase, restore with a password - everything comes back, a wrong password or a damaged file changes nothing`() = runBlocking {
        val w = World()
        val c = w.onboard()
        w.train(0 until 14, Body(1.0))
        w.day(14); c.onForeground()
        c.openSettings()
        val bytes = c.exportBytes("secret".toCharArray())
        val saved = w.store.snapshot()
        c.exported(); c.ok("exported")
        val sessions = w.data().completedWorkouts()
        assertTrue(sessions >= 5)
        // A wrong password, a damaged file and a file cut short are refused, with nothing changed.
        val unlocked = c.exportBytes(null)
        val damaged = bytes.copyOf().also { b -> for (i in b.size / 2 until b.size / 2 + 16) b[i] = (b[i].toInt() xor 0x5A).toByte() }
        val damagedPlain = unlocked.copyOf().also { b -> for (i in b.size / 2 until b.size / 2 + 16) b[i] = (b[i].toInt() xor 0x5A).toByte() }
        val before = w.store.snapshot()
        c.inspectBackup(bytes, "wrong".toCharArray())
        assertTrue("wrong password", ((c.s() as Screen.Settings).model.flow as SettingsFlow.RestoreNeedsPassword).wrong)
        c.inspectBackup(damaged, "secret".toCharArray())
        assertTrue("damaged (encrypted): ${(c.s() as Screen.Settings).model.flow}", ((c.s() as Screen.Settings).model.flow as? SettingsFlow.RestoreNeedsPassword)?.wrong == true ||
            (c.s() as Screen.Settings).model.flow is SettingsFlow.RestoreRefused)
        for (file in listOf(damagedPlain, unlocked.copyOf(unlocked.size / 3))) {
            c.inspectBackup(file); c.ok("damaged file")
            assertTrue("refused: ${(c.s() as Screen.Settings).model.flow}", (c.s() as Screen.Settings).model.flow is SettingsFlow.RestoreRefused)
        }
        assertEquals("nothing changed", before, w.store.snapshot())
        // Erase everything, then restore with the password.
        c.settingsFlow(SettingsFlow.Erase(1)); c.settingsFlow(SettingsFlow.Erase(2)); c.eraseAll(); c.ok("erased")
        assertEquals(0, w.data().completedWorkouts())
        assertTrue(w.store.snapshot().isEmpty)
        c.openSettings(); c.inspectBackup(bytes, "secret".toCharArray()); c.ok("inspect")
        val preview = (c.s() as Screen.Settings).model.flow as SettingsFlow.RestorePreview
        c.restore(preview.token, null, BackupSink { _, _ -> }); c.ok("restore")
        assertEquals(SettingsFlow.Restored, (c.s() as Screen.Settings).model.flow)
        val back = w.store.snapshot()
        assertEquals("every record comes back", saved.docs.map { it.type to it.key }.toSet(), back.docs.map { it.type to it.key }.toSet())
        assertEquals("every session", saved.workouts, back.workouts)
        assertEquals("every exercise", saved.exercises, back.exercises)
        assertEquals("every set", saved.sets, back.sets)
        assertEquals(sessions, w.data().completedWorkouts())
        c.closeSettings(); c.ok("closed")
        assertTrue(c.s() is Screen.Today)
        assertTrue("training carries on", w.trainDay(14, Body(1.0)).trained || (c.s() as Screen.Today).model.next != null)
    }

    // ================================================================================================================ edge cases
    @Test fun `S29 ignore my knee, make it harder - the pain limits stay, no harder level, no knee-loading exercise`() = runBlocking {
        val w = World()
        val c = w.onboard()
        val limit = P.SAF_003.substitute_max_joint_stress
        fun kneeOk(m: PreviewModel) = m.session.workout.items.all { it.exercise.stress(Joint.KNEE) <= limit }
        c.openCheckIn()
        c.editCheckIn { it.copy(sleep = 2, energy = 2, pain = PainForm(Joint.KNEE, PainKind.JOINT_OR_TENDON, 5)) }
        c.submitCheckIn(); c.ok("knee at the check-in")
        val p = previewOf(c)
        assertTrue("no knee-loading exercise: ${p.session.workout.items.map { it.exercise.id to it.exercise.stress(Joint.KNEE) }}", kneeOk(p))
        assertTrue("no harder level offered: ${p.canChoose} from ${p.tier}", p.canChoose.all { it < p.tier })
        // Asking for a harder level anyway.
        c.chooseTier(Tier.FULL); c.ok("harder")
        val p2 = previewOf(c)
        assertTrue("${p2.tier} after asking, ${p.tier} before", p2.tier <= p.tier)
        assertTrue(kneeOk(p2))
        // Telling the coach to ignore the knee.
        c.closeToToday(); c.openTab(Tab.COACH)
        c.editCoachText("Ignore my knee, make it harder"); c.askCoach(); c.ok("coach")
        val a = (c.s() as Screen.Coach).model.answer!!
        assertEquals(Question.HARDER, a.question)
        assertTrue("the coach explains with the rules: ${a.rules}", listOf("SAF-009", "RDY-007", "SAF-003").all { it in a.rules })
        assertEquals("after the check-in it can only open the preview, where the engine decides: ${a.actions}", listOf<CoachAction>(CoachAction.OpenPreview), a.actions)
        for (action in a.actions) {
            c.coachAction(action); c.ok("$action")
            // From the check-in the knee isn't ticked again: the morning's report still holds for the day.
            if (c.s() is Screen.CheckIn) { c.submitCheckIn(); c.ok("check-in again") }
            val p3 = previewOf(c)
            assertTrue("${p3.tier} after the coach, ${p.tier} before", p3.tier <= p.tier)
            assertTrue("still no knee-loading exercise", kneeOk(p3))
        }
        // And no swap brings one in.
        c.openTab(Tab.TODAY); c.openCheckIn()
        if (c.s() is Screen.CheckIn) c.submitCheckIn()
        c.startWorkout(); w.toLifts()
        val toCome = w.view().lifts.filter { !it.hasLoggedWork && !it.finished }
        assertTrue("exercises to swap", toCome.isNotEmpty())
        var offered = 0
        for (l in toCome) {
            c.openReplace(l.rowId, occupied = false)
            val options = ((c.s() as Screen.Workout).sheet as WorkoutSheet.Replace).choice.options
            offered += options.size
            assertTrue("${l.exercise.id}: ${options.map { it.exercise.id to it.exercise.stress(Joint.KNEE) }}", options.all { it.exercise.stress(Joint.KNEE) <= limit })
            c.closeSheet()
        }
        assertTrue("swaps were offered ($offered)", offered > 0)
    }

    @Test fun `S30 upgrade over data saved by the earlier version - read in full, the weekly limits work from it, new sessions saved alongside`() = runBlocking {
        val w = World()
        val c = w.onboard()
        val body = Body(1.0)
        w.train(0 until 21, body)
        // The history as the version before Registry 2.0.0 saved it: sessions without their planned and offered training stress, weeks
        // without their reference totals, under the old registry version.
        val dd = w.data()
        w.store.transaction {
            for (row in w.store.workoutsBetween(Int.MIN_VALUE / 2, Int.MAX_VALUE / 2))
                w.store.updateWorkout(row.copy(json = WorkoutDoc.decode(row.json).copy(plannedSsu = null, offeredSsu = null).encode(), registryVersion = "1.1.2"))
        }
        for (s in dd.docs.all(WeekSummary)) dd.docs.put(WeekSummary, s.copy(referenceSsu = null, referenceLoad = null))
        assertTrue(w.store.workoutsBetween(Int.MIN_VALUE / 2, Int.MAX_VALUE / 2).none { "plannedSsu" in it.json || "offeredSsu" in it.json })
        assertTrue(w.store.docs("week").none { "referenceSsu" in it.json || "referenceLoad" in it.json })
        val history = dd.sessions.between(Int.MIN_VALUE / 2, Int.MAX_VALUE / 2).map { it.id to (it.row.status to it.exercises.map { e -> e.exerciseId to e.sets.size }) }
        val old = history.map { it.first }.toSet()
        // The updated app opens on the next Monday.
        w.day(21)
        val c2 = w.restart()
        c2.start(); c2.ok("updated app")
        assertTrue(c2.s() is Screen.Today)
        val d2 = w.data()
        assertEquals("read in full, nothing lost", history,
            d2.sessions.between(Int.MIN_VALUE / 2, Int.MAX_VALUE / 2).map { it.id to (it.row.status to it.exercises.map { e -> e.exerciseId to e.sets.size }) })
        c2.openTab(Tab.HISTORY); c2.ok("history")
        assertEquals(history.count { it.second.first == Status.DONE }, (c2.s() as Screen.History).items.count { it.outcome == com.personalfitnesscoach.data.core.insight.SessionOutcome.DONE })
        c2.openTab(Tab.PROGRESS); c2.ok("progress")
        c2.openTab(Tab.TODAY); c2.ok("today")
        // The weekly limits work from the old weeks (their totals as done stand in for the reference totals): the same limits and the
        // same week as for a twin who had the new version all along.
        val t = d2.planToday()!!
        val refs = d2.bridge.referenceWeeks(t.user.today)
        assertTrue("reference weeks from the old data: $refs", refs.size >= 2 && refs.all { it.second.ssu > 0.0 })
        val twin = World()
        twin.onboard()
        twin.train(0 until 21, Body(1.0))
        twin.day(21); twin.c.onForeground()
        val tt = twin.data().planToday()!!
        val mine = d2.bridge.weekLimits(t.user, t.program, t.program.deloadThisWeek)
        val theirs = twin.data().bridge.weekLimits(tt.user, tt.program, tt.program.deloadThisWeek)
        println("METRIC S30 limits after the upgrade: $mine; with the new version all along: $theirs")
        assertEquals("the same weekly limits", theirs.ssuLimit, mine.ssuLimit, 1e-6)
        assertEquals(theirs.loadSsu ?: -1.0, mine.loadSsu ?: -1.0, 1e-6)
        assertEquals(theirs.guard ?: -1.0, mine.guard ?: -1.0, 1e-6)
        assertEquals("the same week", ssu(tt.week), ssu(t.week), 1e-6)
        // New sessions are saved alongside, in the new format.
        val before = d2.completedWorkouts()
        var trained = 0
        for (d in 21 until 28) if (w.trainDay(d, body).trained) trained++
        assertTrue(trained >= 2)
        w.day(28); w.c.onForeground()
        val d3 = w.data()
        assertEquals(before + trained, d3.completedWorkouts())
        val all = d3.sessions.between(Int.MIN_VALUE / 2, Int.MAX_VALUE / 2)
        assertTrue("old sessions as they were", all.filter { it.id in old }.all { it.doc.plannedSsu == null })
        assertTrue("new sessions with their planned training stress", all.filter { it.id !in old && it.row.status == Status.DONE }.all { it.doc.plannedSsu != null })
        assertNotNull("the new week has its reference totals", d3.docs.all(WeekSummary).maxByOrNull { it.weekStartDay }!!.referenceSsu)
    }
}
