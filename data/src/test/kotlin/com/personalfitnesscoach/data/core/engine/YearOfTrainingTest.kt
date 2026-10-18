package com.personalfitnesscoach.data.core.engine

import com.personalfitnesscoach.data.core.Fixtures
import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.model.ExerciseState
import com.personalfitnesscoach.data.core.model.PreferencesRecord
import com.personalfitnesscoach.data.core.model.StoredCondition
import com.personalfitnesscoach.data.core.session.NewSet
import com.personalfitnesscoach.data.core.session.SetKind
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.engine.calc.CheckIn
import com.personalfitnesscoach.engine.calc.PlateMath
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.Exercise
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.LoadType
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.program.Goal
import com.personalfitnesscoach.engine.program.Individual
import com.personalfitnesscoach.engine.program.WeekPlanner
import com.personalfitnesscoach.engine.progression.Calibration
import com.personalfitnesscoach.engine.progression.CalibrationStep
import com.personalfitnesscoach.engine.registry.RuleIds
import com.personalfitnesscoach.engine.safety.ClearanceScope
import com.personalfitnesscoach.engine.safety.SessionValidator
import com.personalfitnesscoach.engine.safety.WeeklyLimits
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Phase 4 Part 1 (D-110, D-111): a year of training through the data layer for the Phase 2 personas and the condition personas, under
 * four ways of turning up — every planned session; interrupted (a missed week, a week cut short by an illness reported at the check-in,
 * and a warning sign that stops training until it is confirmed, each twice); about 4 in 5 sessions at random; and sparse (one session a
 * week for ten weeks, then every session). It measures what the weekly limits (VOL-007, LOAD-005) keep of the planned work, how often a
 * day is emptied, how the main lifts progress, the largest week against the three before it, and that every session shown passes the
 * validator against the week as stored (SAF-008). Each run is reported as a "Measured" line on CI, so a change to the limits is
 * measured, not guessed. It fails if a week outside a return or a deload goes above the spike guard (independent review, Phase 4 Part 1).
 */
class YearOfTrainingTest {
    enum class Turnout { EVERY, INTERRUPTED, MOSTLY, SPARSE }

    data class Persona(val name: String, val level: Level, val days: Int, val minutes: Int, val birthYear: Int, val gym: Set<String>,
                       val priorities: List<Goal>, val conditions: List<StoredCondition> = emptyList(), val poorSleep: Boolean = false,
                       val excludedModalities: Set<Modality> = emptySet(), val strength: Double = 1.0)

    data class Week(var planned: Double = 0.0, var kept: Double = 0.0, var sessions: Int = 0, var recovery: Int = 0, var capped: Int = 0, var deload: Boolean = false, var byVol: Int = 0, var byLoad: Int = 0, var limit: Double? = null, var loadCap: Double? = null, var k: Double? = null, var done: Double = 0.0,
                    /** Sessions this week in a return to training (REG-003/004/005), where the spike guard is off. */
                    var returnSessions: Int = 0)

    data class Result(val weeks: Map<Int, Week>, val violations: List<String>, val e1rmGain: Double?) {
        fun kept(range: IntRange): Double? = weeks.filterKeys { it in range }.values.let { w ->
            val p = w.sumOf { it.planned }; if (p <= 0.0) null else w.sumOf { it.kept } / p }

        /**
         * Each week's training stress as done against the mean of the three calendar weeks before it as done (missed weeks 0), where the
         * spike guard applies: from week 3 (21 days), outside deloads and weeks spent wholly in a return, after weeks with some training.
         * A week whose first session is a return session is checked: the guard holds its other sessions (re-check).
         */
        fun spikes(): List<Pair<Int, Double>> = weeks.keys.filter { it >= 3 }.mapNotNull { i ->
            val w = weeks.getValue(i)
            if (w.deload || w.returnSessions >= w.sessions || w.done <= 0.0) return@mapNotNull null
            val mean = (1..3).sumOf { weeks[i - it]?.done ?: 0.0 } / 3.0
            if (mean <= 0.0) null else i to w.done / mean
        }
    }

    private class Body(level: Level, private val scale: Double) {
        private val factor = level.pick(1.0, 1.4, 1.8)
        private val true1rm = HashMap<String, Double>()
        fun strength(e: Exercise) = true1rm.getOrPut(e.id) { 80.0 * maxOf(0.05, Individual.startFraction(e)) * 5 * factor * scale }
        fun possible(e: Exercise, load: Double) = maxOf(0, Math.floor(30.0 * (strength(e) / load - 1.0)).toInt())
        fun grow(e: Exercise) { true1rm[e.id] = strength(e) * 1.003 }
    }

    private fun turnsUp(t: Turnout, week: Int, nthThisWeek: Int, rnd: Random): Boolean = when (t) {
        Turnout.EVERY -> true
        // A missed week (week 6), and a week cut short after its first session by illness (week 14), then again in the second half.
        Turnout.INTERRUPTED -> week != 5 && week != 31 && !((week == 13 || week == 39) && nthThisWeek >= 1)
        Turnout.MOSTLY -> rnd.nextInt(5) != 0
        // One session a week for ten weeks (weeks 4-13), then every session: the limits must not let a full week in at once.
        Turnout.SPARSE -> week !in 4..13 || nthThisWeek == 0
    }

    private suspend fun year(p: Persona, turnout: Turnout, weeks: Int, seed: Long): Result {
        val (d, clock) = Fixtures.data()
        Fixtures.onboard(d, p.level, p.birthYear, p.days, p.minutes, p.priorities, p.gym, p.conditions)
        if (p.excludedModalities.isNotEmpty()) d.docs.put(PreferencesRecord, PreferencesRecord(excludedModalities = p.excludedModalities))
        val rnd = Random(seed)
        val body = Body(p.level, p.strength)
        val out = sortedMapOf<Int, Week>()
        val violations = ArrayList<String>()
        val firstE1rm = HashMap<String, Double>()
        var stopDay: Int? = null
        val ill = HashSet<Int>()
        val stopped = HashSet<Int>()
        for (day in Fixtures.MONDAY until Fixtures.MONDAY + 7 * weeks) {
            clock.setDay(day, hour = 7)
            val wk = (day - Fixtures.MONDAY) / 7
            // A warning-sign stop lasts until it is confirmed (SAF-002): here two days later.
            val stop = stopDay
            if (stop != null) {
                if (day < stop + 2) continue
                d.confirmStopResolved()
                stopDay = null
            }
            val t = checkNotNull(d.planToday())
            val next = t.next ?: continue
            if (t.week.days.none { it.weekday == Days.weekday(day) }) continue
            val week = out.getOrPut(wk) { Week() }
            val nth = week.sessions + week.recovery
            if (turnout == Turnout.INTERRUPTED) {
                // Ill on the second session day of weeks 13 and 39 (SAF-007), reported at the check-in; the rest of that week is missed and
                // the return after it follows REG-005.
                if ((wk == 13 || wk == 39) && nth == 1 && ill.add(wk)) {
                    clock.setDay(day, hour = 18)
                    d.checkIns.record(t.user, CheckIn(3, 2, 3, 3), illnessSymptoms = setOf(com.personalfitnesscoach.engine.safety.RedFlags.SYSTEMIC.first()))
                    continue
                }
                // A warning sign at the check-in of the first session of weeks 20 and 44 (SAF-002).
                if ((wk == 20 || wk == 44) && nth == 0 && stopped.add(wk)) {
                    clock.setDay(day, hour = 18)
                    d.checkIns.record(t.user, CheckIn(3, 3, 3, 3), redFlags = setOf("chest_pain_pressure_tightness"))
                    stopDay = day
                    continue
                }
            }
            if (!turnsUp(turnout, wk, nth, rnd)) continue
            clock.setDay(day, hour = 18)
            val checkIn = if (p.poorSleep && rnd.nextBoolean()) CheckIn(2, 2, 3, 2, sleepHours = 5.0) else CheckIn(3 + rnd.nextInt(2), 3 + rnd.nextInt(2), 3, 4)
            val ci = d.checkIns.record(t.user, checkIn)
            val gen = d.generate(t, next, ci.record.tier)
            val w = gen.value
            if (w.safetyStop != null || w.followCareProvider.isNotEmpty()) continue
            val vctx = d.bridge.validationContext(t.user, t.program, t.week, next)
            SessionValidator.violations(w.validated, vctx.copy(fullTierWorkingSets = w.fullTierWorkingSets)).forEach { violations += "${p.name} ${Days.date(day)} $it" }
            if (week.limit == null) { week.limit = vctx.weeklySsuLimit; week.loadCap = vctx.weeklyLoadCap; week.k = vctx.k }
            if (d.bridge.returnPlan(t.user, t.program).value.inReturn) week.returnSessions++
            // What the programme asked for: the day as planned before the weekly limits (the planner now fits the week within them).
            val uncapped = WeekPlanner.plan(d.bridge.weekInput(t.user, t.program).copy(weeklySsuLimit = null, weeklyLoadSsu = null, weeklySsuGuard = null),
                d.bridge.blueprint(t.program)).value
                .days.firstOrNull { it.weekday == next.weekday && it.template == next.template } ?: next
            week.planned += SessionValidator.sessionSsu(uncapped.toSession())
            week.kept += SessionValidator.sessionSsu(w.validated)
            week.deload = week.deload || t.week.deload
            gen.decisions.firstOrNull { it.reason.name == "PLAN_CAPPED_BY_LOAD" }?.let { c ->
                week.capped++
                if (RuleIds.VOL_007 in c.ruleIds) week.byVol++
                if (RuleIds.LOAD_005 in c.ruleIds) week.byLoad++
            }
            if (w.items.isEmpty() && w.conditioning.isEmpty()) { week.recovery++; continue }
            val id = d.sessions.start(w, next.template, next.weekday, inDeload = t.week.deload)
            val stored = checkNotNull(d.sessions.load(id))
            for ((i, e) in stored.exercises.withIndex()) {
                val item = w.items[i]
                val ex = item.exercise
                var idx = 0
                fun set(kind: String, load: Double?, reps: Int?, secs: Int?, rir: Double?) = NewSet(idx++, kind, load, reps, secs, rir = rir)
                val loaded = item.load != null && ex.loadType in setOf(LoadType.BARBELL, LoadType.DUMBBELL, LoadType.KETTLEBELL, LoadType.STACK) && !ex.assisted
                if (loaded && item.calibrating) {
                    val avail = PlateMath.loadsFor(ex, t.user.equipment.inventory)
                    val target = ExerciseProgress.calibrationReps(item.reps)
                    var cur = item.load!!
                    for (n in 1..5) {
                        val rir = minOf(6, body.possible(ex, cur) - target).coerceAtLeast(0).toDouble()
                        d.sessions.logSet(e.row.id, set(SetKind.CALIBRATION, cur, target, null, rir), "{}")
                        val step = Calibration.next(cur, target, rir, n, avail, null, Calibration.coarseStepsAllowed(ex.loadType)).value
                        if (step is CalibrationStep.Continue) cur = step.nextLoad else break
                    }
                } else if (loaded && item.unit == DoseUnit.REPS) {
                    repeat(item.sets) {
                        val can = body.possible(ex, item.load!!)
                        val reps = minOf(item.reps.last, maxOf(1, can))
                        d.sessions.logSet(e.row.id, set(SetKind.WORKING, item.load, reps, null, minOf(5, can - reps).coerceAtLeast(0).toDouble()), "{}")
                    }
                } else repeat(item.sets) {
                    if (item.unit == DoseUnit.SECONDS) d.sessions.logSet(e.row.id, set(SetKind.WORKING, item.load, null, item.reps.first, item.targetRir), "{}")
                    else d.sessions.logSet(e.row.id, set(SetKind.WORKING, item.load, item.reps.last, null, item.targetRir), "{}")
                }
                if (ex.trackE1rm && !item.calibrating) body.grow(ex)
            }
            d.finishWorkout(id, sessionRpe = 6.0 + rnd.nextInt(3), actualMinutes = w.plannedMinutes, conditioningDoneMinutes = w.conditioning.map { it.workMinutes })
            week.sessions++
            checkNotNull(d.sessions.load(id)).let { fin -> week.done += SessionValidator.sessionSsu(d.bridge.doneSession(fin)) + d.bridge.calibrationSsu(fin) }
            d.docs.all(ExerciseState).forEach { s -> s.e1rm?.let { firstE1rm.putIfAbsent(s.exerciseId, it) } }
        }
        val last = d.docs.all(ExerciseState).mapNotNull { s -> s.e1rm?.let { s.exerciseId to it } }.toMap()
        val gains = last.mapNotNull { (k, v) -> firstE1rm[k]?.takeIf { it > 0 }?.let { v / it } }
        return Result(out, violations, if (gains.isEmpty()) null else gains.sorted()[gains.size / 2] - 1.0)
    }

    private val noRunningBikesOrStairs = Fixtures.FULL_GYM - setOf("stationary_bike", "treadmill")

    /** The Phase 2 personas (P1–P5) and the Research Update 1.1 condition personas. */
    private val personas = listOf(
        Persona("P1 you", Level.INTERMEDIATE, 3, 60, 1984, noRunningBikesOrStairs, listOf(Goal.FAT_LOSS),
            excludedModalities = setOf(Modality.TREADMILL_RUN, Modality.STATIONARY_BIKE, Modality.STAIR_MACHINE)),
        Persona("P2 returning beginner", Level.BEGINNER, 3, 45, 1978, Fixtures.FULL_GYM, listOf(Goal.FAT_LOSS), strength = 0.7),
        Persona("P3 time-crunched", Level.INTERMEDIATE, 4, 35, 1990, Fixtures.FULL_GYM, listOf(Goal.STRENGTH, Goal.MUSCLE, Goal.GENERAL_FITNESS)),
        Persona("P5 poor sleeper", Level.INTERMEDIATE, 3, 60, 1982, Fixtures.FULL_GYM, listOf(Goal.MUSCLE, Goal.GENERAL_FITNESS), poorSleep = true),
        Persona("Advanced 4 days", Level.ADVANCED, 4, 75, 1992, Fixtures.FULL_GYM, listOf(Goal.STRENGTH, Goal.MUSCLE), strength = 1.2),
        Persona("Beginner 66 T2D + knee", Level.BEGINNER, 3, 45, 1960, setOf("dumbbells", "bench", "bands", "mat"), listOf(Goal.FAT_LOSS),
            conditions = listOf(StoredCondition("t2d", Fixtures.MONDAY), StoredCondition("oa_knee", Fixtures.MONDAY)), strength = 0.45),
        Persona("Fat loss 52 hypertension", Level.BEGINNER, 3, 50, 1974, Fixtures.FULL_GYM, listOf(Goal.FAT_LOSS),
            conditions = listOf(StoredCondition("hbp_controlled", Fixtures.MONDAY, clearance = setOf(ClearanceScope.VIGOROUS))), strength = 0.8),
        // Bodyweight only at home: the conditioning is circuits, so it carries much of the week's training stress.
        Persona("Bodyweight 35 home", Level.BEGINNER, 3, 45, 1991, setOf("mat"), listOf(Goal.FAT_LOSS), strength = 0.8),
    )

    private fun report(p: Persona, t: Turnout, r: Result, weeks: Int) {
        if (System.getenv("PFC_SIM_WEEKLY") != null) r.weeks.forEach { (i, w) ->
            println("WEEK ${p.name} ${t.name} $i: planned ${"%.1f".format(w.planned)} kept ${"%.1f".format(w.kept)} done ${"%.1f".format(w.done)} sessions ${w.sessions} recovery ${w.recovery} " +
                "capped ${w.capped} (vol ${w.byVol}, load ${w.byLoad}) limit ${w.limit?.let { "%.1f".format(it) }} loadCap/k ${if (w.loadCap != null && w.k != null) "%.1f".format(w.loadCap!! / w.k!!) else "-"} deload ${w.deload}") }
        val quarters = (0 until weeks step 13).map { q -> r.kept(q until minOf(weeks, q + 13))?.let { "%.0f".format(it * 100) } ?: "-" }
        val trained = r.weeks.values.filter { it.sessions + it.recovery > 0 }
        val underHalf = trained.count { it.planned > 0 && it.kept < 0.5 * it.planned }
        val capped = trained.sumOf { it.capped }; val days = trained.sumOf { it.sessions + it.recovery }
        val spikes = r.spikes()
        println("METRIC year ${p.name}, ${t.name.lowercase()}: planned work kept by quarter ${quarters.joinToString("/")}%; weeks under half $underHalf of ${trained.size}; " +
            "sessions cut by the limits $capped of $days (VOL-007 ${trained.sumOf { it.byVol }}, LOAD-005 ${trained.sumOf { it.byLoad }}); recovery days ${trained.sumOf { it.recovery }}; deload weeks ${trained.count { it.deload }}; " +
            "median e1RM change ${r.e1rmGain?.let { "%+.0f%%".format(it * 100) } ?: "-"}; largest week ${"%.2f".format(spikes.maxOfOrNull { it.second } ?: 0.0)}x the 3 before " +
            "(${spikes.count { it.second > 1.5 + 1e-9 }} above 1.5x)")
    }

    @Test fun `a year of training - what the weekly limits keep`() = runBlocking {
        val weeks = (System.getenv("PFC_SIM_WEEKS") ?: "52").toInt()
        val all = ArrayList<String>()
        val spikes = ArrayList<String>()
        // For local experiments: PFC_SIM_ONLY picks personas by name prefix, PFC_SIM_TURNOUT one way of turning up.
        val only = System.getenv("PFC_SIM_ONLY")?.split(",")
        val turnout = System.getenv("PFC_SIM_TURNOUT")?.let { Turnout.valueOf(it) }
        for ((i, p) in personas.withIndex()) for (t in Turnout.entries) {
            if (only != null && only.none { p.name.startsWith(it) } || turnout != null && t != turnout) continue
            val r = year(p, t, weeks, 1000L + i * 10 + t.ordinal)
            report(p, t, r, weeks)
            all += r.violations
            // The spike guard (D-112): outside returns and deloads, no week above twice the three before it as done.
            r.spikes().filter { it.second > WeeklyLimits.SPIKE_GUARD + 1e-6 }.forEach { (w, x) -> spikes += "${p.name} ${t.name} week $w: ${"%.2f".format(x)}x" }
        }
        assertTrue("every session shown passes the validator: ${all.take(5)}", all.isEmpty())
        assertTrue("no week above the spike guard: ${spikes.take(5)}", spikes.isEmpty())
    }
}
