package com.personalfitnesscoach.data.core.engine

import com.personalfitnesscoach.data.core.Fixtures
import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.model.ExerciseState
import com.personalfitnesscoach.data.core.model.KnownNumber
import com.personalfitnesscoach.data.core.model.ProgramRecord
import com.personalfitnesscoach.data.core.model.StepsRecord
import com.personalfitnesscoach.data.core.model.StoredCondition
import com.personalfitnesscoach.data.core.model.WeekSummary
import com.personalfitnesscoach.data.core.model.WeightRecord
import com.personalfitnesscoach.data.core.session.NewSet
import com.personalfitnesscoach.data.core.session.SetKind
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.engine.calc.CheckIn
import com.personalfitnesscoach.engine.calc.PlateMath
import com.personalfitnesscoach.engine.generation.SessionGenerator
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.Exercise
import com.personalfitnesscoach.engine.model.FormCheck
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.LoadType
import com.personalfitnesscoach.engine.program.Goal
import com.personalfitnesscoach.engine.program.Individual
import com.personalfitnesscoach.engine.progression.Calibration
import com.personalfitnesscoach.engine.progression.CalibrationStep
import com.personalfitnesscoach.engine.progression.ProgressionCaps
import com.personalfitnesscoach.engine.safety.ClearanceScope
import com.personalfitnesscoach.engine.safety.SessionValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Weeks of training through the data layer only, the way the app will run (Part 5 screens call exactly these methods): open the
 * app, check in, plan today, generate, start, log every set, finish — with storage as the only memory between days. Checks that
 * history really feeds the engine (calibration → e1RM → prescriptions → heavier loads), that every stored session passes the
 * validator against the week as stored, that the weekly clock and totals roll over, and that the incremental state equals a
 * rebuild from the logged sets and survives a backup round trip.
 */
class DataLayerSimulationTest {
    data class Persona(val name: String, val level: Level, val days: Int, val minutes: Int, val birthYear: Int, val gym: Set<String>, val priorities: List<Goal>,
                       val conditions: List<StoredCondition> = emptyList(), val known: List<KnownNumber> = emptyList(), val steps: Boolean = false)

    data class Result(val sessions: Int, val violations: List<String>, val firstE1rm: Map<String, Double>, val lastE1rm: Map<String, Double>,
                      val capBreaks: List<String>, val program: ProgramRecord, val summaries: List<WeekSummary>,
                      /** Generated full-load doses per exercise, in order (calibration excluded): load and top of the rep range. */
                      val loads: Map<String, List<Pair<Double, Int>>>)

    private class User(level: Level) {
        val factor = level.pick(1.0, 1.4, 1.8)
        val true1rm = HashMap<String, Double>()
        fun strength(e: Exercise) = true1rm.getOrPut(e.id) { 80.0 * maxOf(0.05, Individual.startFraction(e)) * 5 * factor }
        fun possible(e: Exercise, load: Double) = maxOf(0, Math.floor(30.0 * (strength(e) / load - 1.0)).toInt())
        fun grow(e: Exercise) { true1rm[e.id] = strength(e) * 1.003 }
    }

    private suspend fun run(p: Persona, weeks: Int, seed: Long): Pair<PfcData, Result> {
        val (d, clock) = Fixtures.data()
        Fixtures.onboard(d, p.level, p.birthYear, p.days, p.minutes, p.priorities, p.gym, p.conditions)
        p.known.forEach { d.docs.put(KnownNumber, it) }
        if (p.steps) d.setStepTracking(true)
        val rnd = Random(seed)
        val user = User(p.level)
        val violations = ArrayList<String>()
        val caps = ArrayList<String>()
        val first = HashMap<String, Double>()
        val loads = LinkedHashMap<String, MutableList<Pair<Double, Int>>>()
        var sessions = 0
        var counter = 0L
        for (day in Fixtures.MONDAY until Fixtures.MONDAY + 7 * weeks) {
            clock.setDay(day, hour = 7)
            if (p.steps) { counter += 6000 + rnd.nextInt(5000); d.recordStepReading(clock.nowMs(), clock.nowMs() - Days.startMs(Fixtures.MONDAY - 1, clock.zone()), counter) }
            if (Days.weekday(day) == 0) d.docs.put(WeightRecord, WeightRecord(day, 90.0 - (day - Fixtures.MONDAY) * 0.03))
            val t = checkNotNull(d.planToday()) { "planned" }
            val next = t.next ?: continue
            if (next.weekday != Days.weekday(day)) continue
            clock.setDay(day, hour = 18)
            val ci = d.checkIns.record(t.user, CheckIn(3 + rnd.nextInt(2), 3 + rnd.nextInt(2), 3, 4))
            val gen = d.generate(t, next, ci.record.tier)
            val w = gen.value
            if (w.safetyStop != null || w.followCareProvider.isNotEmpty()) continue
            // The session the app saves passes the validator against the week as stored (SAF-008).
            val vctx = d.bridge.validationContext(t.user, t.program, t.week, next)
            SessionValidator.violations(w.validated, vctx.copy(fullTierWorkingSets = w.fullTierWorkingSets)).forEach { violations += "${Days.date(day)} $it" }
            // PROG-007 / SAF-005: no load rises faster than the weekly cap over the stored reference load (or one equipment step, D-055).
            val lastLoads = d.bridge.lastWeekLoads(day)
            for (it in w.items) {
                val load = it.load ?: continue
                val last = lastLoads[SessionGenerator.loadKey(it.slotKey, it.exercise.id)] ?: continue
                if (it.calibrating || it.exercise.assisted) continue
                val avail = PlateMath.loadsFor(it.exercise, t.user.equipment.inventory)
                val max = maxOf(ProgressionCaps.maxLoadThisWeek(last, p.level, avail, t.user.age), avail.filter { a -> a > last + 1e-9 }.minOrNull() ?: last)
                if (load > maxOf(max, last) + 1e-9) caps += "${it.exercise.id} $last → $load (max $max)"
            }
            w.items.filter { !it.calibrating && it.load != null && it.loadFactor >= 1.0 - 1e-9 }.forEach { loads.getOrPut(it.exercise.id) { ArrayList() } += it.load!! to it.reps.last }
            val id = d.sessions.start(w, next.template, Days.weekday(day), inDeload = t.week.deload)
            val stored = d.sessions.load(id)!!
            for ((i, e) in stored.exercises.withIndex()) {
                val item = w.items[i]
                val ex = item.exercise
                check(e.exerciseId == ex.id)
                var idx = 0
                fun set(kind: String, load: Double?, reps: Int?, secs: Int?, rir: Double?) = NewSet(idx++, kind, load, reps, secs, rir = rir,
                    formCheck = if (rnd.nextInt(40) == 0) FormCheck.UNSURE else FormCheck.YES)
                val loaded = item.load != null && ex.loadType in setOf(LoadType.BARBELL, LoadType.DUMBBELL, LoadType.KETTLEBELL, LoadType.STACK) && !ex.assisted
                if (loaded && item.calibrating) {
                    val avail = PlateMath.loadsFor(ex, t.user.equipment.inventory)
                    val target = ExerciseProgress.calibrationReps(item.reps)
                    var cur = item.load!!
                    for (n in 1..5) {
                        val rir = minOf(6, user.possible(ex, cur) - target).coerceAtLeast(0).toDouble()
                        d.sessions.logSet(e.row.id, set(SetKind.CALIBRATION, cur, target, null, rir), "{}")
                        val step = Calibration.next(cur, target, rir, n, avail, null, Calibration.coarseStepsAllowed(ex.loadType)).value
                        if (step is CalibrationStep.Continue) cur = step.nextLoad else break
                    }
                } else if (loaded && item.unit == DoseUnit.REPS) {
                    repeat(item.sets) {
                        val can = user.possible(ex, item.load!!)
                        val reps = minOf(item.reps.last, maxOf(1, can))
                        d.sessions.logSet(e.row.id, set(SetKind.WORKING, item.load, reps, null, minOf(5, can - reps).coerceAtLeast(0).toDouble()), "{}")
                    }
                } else {
                    repeat(item.sets) {
                        if (item.unit == DoseUnit.SECONDS) d.sessions.logSet(e.row.id, set(SetKind.WORKING, item.load, null, item.reps.first, 2.0), "{}")
                        else d.sessions.logSet(e.row.id, set(SetKind.WORKING, item.load, item.reps.first, null, 2.0), "{}")
                    }
                }
                if (ex.trackE1rm) user.grow(ex)
            }
            d.finishWorkout(id, sessionRpe = 6.0, actualMinutes = w.plannedMinutes, conditioningDoneMinutes = w.conditioning.map { it.workMinutes })
            sessions++
            d.docs.all(ExerciseState).forEach { s -> s.e1rm?.let { first.putIfAbsent(s.exerciseId, it) } }
        }
        clock.setDay(Fixtures.MONDAY + 7 * weeks, hour = 7)
        d.onAppOpen()
        val last = d.docs.all(ExerciseState).mapNotNull { s -> s.e1rm?.let { s.exerciseId to it } }.toMap()
        return d to Result(sessions, violations, first, last, caps, d.docs.get(ProgramRecord)!!, d.docs.all(WeekSummary), loads)
    }

    private fun checkCommon(name: String, weeks: Int, days: Int, r: Result) {
        assertTrue("$name: validator ${r.violations.take(5)}", r.violations.isEmpty())
        assertTrue("$name: weekly cap ${r.capBreaks.take(5)}", r.capBreaks.isEmpty())
        assertTrue("$name: sessions ${r.sessions}", r.sessions >= weeks * days * 8 / 10)
        assertEquals("$name: every finished week summarised", weeks, r.summaries.size)
        assertTrue("$name: completed counts ${r.summaries.map { it.completed }}", r.summaries.sumOf { it.completed } == r.sessions)
        assertTrue("$name: clock moved (${r.program.clockWeek})", r.program.clockWeek >= weeks - 1)
        assertEquals(weeks, r.program.weeksTraining)
        // History feeds the engine: exercises done 4+ times at full load mostly progressed — heavier, or more reps at the same load
        // (PROG-002 double progression, D-052/D-055). Light free weights move by reps first, then jump (D-052).
        val repeated = r.loads.filterValues { it.size >= 4 }
        val progressed = repeated.count { (_, l) -> l.last().first > l.first().first + 1e-9 || (l.last().first >= l.first().first - 1e-9 && l.last().second > l.first().second) }
        assertTrue("$name: progressed $progressed of ${repeated.size}: ${repeated.entries.take(6)}", repeated.size >= 3 && progressed * 2 >= repeated.size)
    }

    private suspend fun checkRebuildAndBackup(d: PfcData) {
        // The incremental state equals a rebuild from the logged sets.
        val incremental = d.docs.all(ExerciseState).sortedBy { it.exerciseId }
        d.progress.rebuild(d.bridge.user()!!)
        val rebuilt = d.docs.all(ExerciseState).sortedBy { it.exerciseId }
        assertEquals(incremental.map { it.exerciseId }, rebuilt.map { it.exerciseId })
        for ((a, b) in incremental.zip(rebuilt)) assertEquals(a.exerciseId, a, b)
        // A backup of the whole history restores identically, and planning continues from it.
        val before = d.store.snapshot()
        val bytes = d.backup.export(iterations = 10_000).second
        d.eraseAll()
        d.restore(d.backup.inspect(bytes)) { _, _ -> }
        assertEquals(before, d.store.snapshot())
        assertTrue(d.planToday() != null)
    }

    @Test fun `ten weeks of strength training through storage`() = runBlocking {
        val p = Persona("intermediate-3d", Level.INTERMEDIATE, 3, 60, 1985, Fixtures.FULL_GYM, listOf(Goal.STRENGTH, Goal.MUSCLE, Goal.GENERAL_FITNESS))
        val (d, r) = run(p, 10, 11L)
        println("DATA-SIM ${p.name}: sessions=${r.sessions} e1RM=${r.lastE1rm.size} clock=${r.program.clockWeek} first=${r.firstE1rm.entries.take(3)} last=${r.lastE1rm.entries.take(3)}")
        checkCommon(p.name, 10, 3, r)
        assertTrue("e1RMs found", r.lastE1rm.size >= 3)
        val heavier = r.loads.filterValues { it.size >= 4 }.count { (_, l) -> l.last().first > l.first().first + 1e-9 }
        assertTrue("barbell and machine lifts got heavier ($heavier)", heavier >= 3)
        // Calibration finished and progression took over: prescriptions exist and loads went up from the first week.
        val states = d.docs.all(ExerciseState)
        assertTrue(states.count { it.prescription != null } >= 3)
        assertTrue("decisions logged", d.decisions.forDay(Fixtures.MONDAY).isNotEmpty())
        checkRebuildAndBackup(d)
    }

    @Test fun `a fat-loss user aged 62 with type 2 diabetes, own numbers and step tracking`() = runBlocking {
        val gym = Fixtures.FULL_GYM - setOf("barbell")
        val p = Persona("fl-62-t2d", Level.BEGINNER, 3, 45, 1964, gym, listOf(Goal.FAT_LOSS),
            conditions = listOf(StoredCondition("t2d", Fixtures.MONDAY, clearance = setOf(ClearanceScope.VIGOROUS))),
            known = listOf(KnownNumber("goblet-squat", 16.0, 10, 2.0, false, Fixtures.MONDAY - 5, Fixtures.MONDAY),
                KnownNumber("db-bench-press", 14.0, 10, 2.0, false, Fixtures.MONDAY - 120, Fixtures.MONDAY)),
            steps = true)
        val (d, r) = run(p, 8, 23L)
        println("DATA-SIM ${p.name}: sessions=${r.sessions} e1RM=${r.lastE1rm.size} clock=${r.program.clockWeek} summaries=${r.summaries.map { it.equivalentMinutes }}")
        checkCommon(p.name, 8, 3, r)
        // T2D: no strength on consecutive days (D-071), stored history proves it.
        val strengthDays = d.sessions.done(Fixtures.MONDAY, Fixtures.MONDAY + 56).filter { w -> w.template.strength }.map { it.day }.sorted()
        assertTrue("strength on consecutive days: $strengthDays", strengthDays.zipWithNext().none { (a, b) -> b - a == 1 })
        // Steps were recorded for every day and a step target started once the baseline existed (STEP-001).
        assertTrue(d.docs.all(StepsRecord).size >= 50)
        assertTrue(d.docs.get(com.personalfitnesscoach.data.core.model.StepStateRecord)?.target != null)
        // The recent own number seeded the e1RM; the old one capped calibration instead (CAL-002).
        val squat = d.docs.get(ExerciseState, "goblet-squat")
        if (squat != null) assertTrue(squat.knownApplied)
        checkRebuildAndBackup(d)
    }

}
