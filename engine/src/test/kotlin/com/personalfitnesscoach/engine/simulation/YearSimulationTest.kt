package com.personalfitnesscoach.engine.simulation

import com.personalfitnesscoach.engine.calc.Deload
import com.personalfitnesscoach.engine.calc.DeloadAction
import com.personalfitnesscoach.engine.calc.E1rm
import com.personalfitnesscoach.engine.calc.Inventory
import com.personalfitnesscoach.engine.calc.PlateMath
import com.personalfitnesscoach.engine.calc.Volume
import com.personalfitnesscoach.engine.generation.GenerationRequest
import com.personalfitnesscoach.engine.generation.SessionGenerator
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Muscle
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.program.Blueprint
import com.personalfitnesscoach.engine.program.Goal
import com.personalfitnesscoach.engine.program.WeekInput
import com.personalfitnesscoach.engine.program.WeekKind
import com.personalfitnesscoach.engine.program.WeekPlanner
import com.personalfitnesscoach.engine.progression.ProgressionCaps
import com.personalfitnesscoach.engine.safety.Caps
import com.personalfitnesscoach.engine.safety.SessionValidator
import com.personalfitnesscoach.engine.safety.ValidationContext
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * A year in the life of five users: plan every week, generate every session they turn up for
 * (with random readiness), "perform" it with realistic noise, feed the results back, and check
 * the hard rules hold from the first week to the last.
 */
class YearSimulationTest {
    data class Persona(
        val name: String, val level: Level, val days: Int, val minutes: Int, val age: Int, val gym: Set<String>,
        val priorities: List<Goal>, val adherence: (Int) -> Double,
    )

    data class YearResult(val clockWeek: Int, val sessions: Int, val fallbacks: Int, val startE1rm: Map<String, Double>, val endE1rm: Map<String, Double>, val deloads: Int,
        val progressionLoads: Map<String, Double> = emptyMap(), val startWeek: Map<String, Int> = emptyMap())

    private fun simulate(p: Persona, seed: Long): YearResult {
        val rnd = Random(seed)
        val program = Blueprint.plan(p.priorities).value
        val inv = Inventory()
        var clock = 1
        val e1rm = HashMap<String, Double>()
        val startE1rm = HashMap<String, Double>()
        val startWeek = HashMap<String, Int>()
        val nextLoads = HashMap<String, com.personalfitnesscoach.engine.progression.Prescription>()
        val calLoads = HashMap<String, Double>()
        val calSessions = HashMap<String, Int>()
        // The user's true strength: a 1RM per exercise that grows a little with every exposure (diminishing by level).
        val levelFactor = p.level.pick(1.0, 1.4, 1.8)
        val growth = p.level.pick(0.004, 0.002, 0.001)
        val true1rm = HashMap<String, Double>()
        fun trueOf(e: com.personalfitnesscoach.engine.model.Exercise) =
            true1rm.getOrPut(e.id) { 80.0 * maxOf(0.05, com.personalfitnesscoach.engine.program.Individual.startFraction(e)) * 5 * levelFactor }
        fun possible(e: com.personalfitnesscoach.engine.model.Exercise, load: Double) = maxOf(0, Math.floor(30.0 * (trueOf(e) / load - 1.0)).toInt())
        var lastWeekLoads = HashMap<String, Double>()
        var coreLifts = emptyMap<String, String>()
        var blockIndex = -1
        var hiitDoneEver = 0
        var sessions = 0; var fallbacks = 0; var deloads = 0
        var carryRotLast = emptySet<Pattern>()
        for (week in 1..52) {
            val ctx = Blueprint.context(program, clock)
            if (ctx.blockIndex != blockIndex) { blockIndex = ctx.blockIndex; coreLifts = emptyMap() }
            val signals = if (rnd.nextInt(6) == 0) 1 + rnd.nextInt(3) else 0
            val deload = ctx.kind == WeekKind.DELOAD_OR_PIVOT && Deload.decide(signals, if (signals >= 2) 2 else 0, atBlockEnd = true, 0, p.age,
                justFinishedLighterWeek = false).value.let { it == DeloadAction.DELOAD_NOW || it == DeloadAction.DELOAD_AT_BLOCK_END }
            if (deload) deloads++
            val input = WeekInput(p.level, week, p.days, p.gym, ctx, age = p.age, sessionMinutes = p.minutes, deload = deload, priorities = p.priorities,
                hiitBaseReady = week > 3 && clock > 2, hiitDoneEver = hiitDoneEver, coreLifts = coreLifts, carryOrRotationLastWeek = carryRotLast)
            val plan = WeekPlanner.plan(input, program).value
            coreLifts = coreLifts + plan.coreLifts
            carryRotLast = plan.days.flatMap { it.slots }.map { it.exercise.pattern }.filter { it == Pattern.LOADED_CARRY || it == Pattern.ROTATION }.toSet()
            val toDo = Math.round(plan.days.size * p.adherence(week)).toInt()
            val weekSets = HashMap<Muscle, Double>()
            var weekSsu = 0.0
            var hiitThisWeek = 0
            var lastHiitDay: Int? = null
            val thisWeekLoads = HashMap<String, Double>()
            for (day in plan.days.take(toDo)) {
                val tier = rnd.nextInt(100).let { if (it < 70) Tier.FULL else if (it < 90) Tier.MODIFIED else if (it < 98) Tier.LIGHT else Tier.RECOVERY }
                val nextHeavy = plan.days.filter { it.heavyLower && it.weekday > day.weekday }.minOfOrNull { it.weekday }
                val vctx = ValidationContext(p.level, weeksTraining = week, weekSetsSoFar = weekSets.toMap(), weekSsuSoFar = weekSsu,
                    hiitThisWeekSoFar = hiitThisWeek, hiitBaseReady = input.hiitBaseReady,
                    hoursSinceLastHiit = lastHiitDay?.let { (day.weekday - it) * 24.0 }, hoursToNextHeavyLower = nextHeavy?.let { (it - day.weekday) * 24.0 },
                    inDeload = plan.deload)
                val req = GenerationRequest(day, p.level, week, p.minutes, p.gym, tier, p.age, inventory = inv, e1rm = e1rm.toMap(),
                    progression = nextLoads.toMap(), calibrationLoads = calLoads.toMap(), lastWeekLoads = lastWeekLoads, inDeload = plan.deload, week = vctx)
                val w = SessionGenerator.generate(req).value
                sessions++
                if (w.fallbackUsed) fallbacks++
                // The validated session obeys every rule against the week so far.
                val check = vctx.copy(fullTierWorkingSets = w.fullTierWorkingSets, equipmentToday = p.gym, library = Library.all.filter { !it.userAddOnly })
                val v = SessionValidator.violations(w.validated, check)
                assertTrue("${p.name} week $week ${day.template} $tier: $v", v.isEmpty())
                // PROG-007 / SAF-005: no exposure rises faster than the weekly intensity cap.
                for (it in w.items) {
                    val key = SessionGenerator.loadKey(it.slotKey, it.exercise.id)
                    val load = it.load ?: continue
                    val last = lastWeekLoads[key]
                    if (last != null && !it.calibrating && !it.exercise.assisted && it.exercise.id in e1rm) {
                        // At most the PROG-007 percentage, or one equipment step when that step is bigger (D-055).
                        val avail = PlateMath.loadsFor(it.exercise, inv)
                        val max = maxOf(ProgressionCaps.maxLoadThisWeek(last, p.level, avail, p.age), avail.filter { a -> a > last + 1e-9 }.minOrNull() ?: last)
                        assertTrue("${p.name} week $week ${it.exercise.id}: $last → $load (max $max)", load <= maxOf(max, last) + 1e-9)
                    }
                    if (it.loadFactor >= 1.0 - 1e-9) thisWeekLoads[key] = maxOf(thisWeekLoads[key] ?: 0.0, load)
                    // "Perform" the sets against the user's true strength.
                    if (it.exercise.trackE1rm && it.unit == com.personalfitnesscoach.engine.model.DoseUnit.REPS) {
                        val ex = it.exercise
                        val avail = PlateMath.loadsFor(ex, inv)
                        val target = (it.reps.first + it.reps.last) / 2
                        if (it.calibrating) {
                            // CAL-001 ramp within the session.
                            var cur = load
                            for (set in 1..5) {
                                val rir = minOf(6, possible(ex, cur) - target).coerceAtLeast(0).toDouble()
                                when (val step = com.personalfitnesscoach.engine.progression.Calibration.next(cur, target, rir, set, avail).value) {
                                    is com.personalfitnesscoach.engine.progression.CalibrationStep.Continue -> cur = step.nextLoad
                                    is com.personalfitnesscoach.engine.progression.CalibrationStep.Found -> {
                                        val est = step.startE1rm
                                        if (est != null) { e1rm[ex.id] = est; startE1rm.putIfAbsent(ex.id, est); startWeek.putIfAbsent(ex.id, week); calLoads.remove(ex.id) }
                                        else if ((calSessions.merge(ex.id, 1, Int::plus) ?: 1) < 4) calLoads[ex.id] = step.workingLoad // CAL-001 runs over sessions 1–4
                                        else { nextLoads[ex.id] = com.personalfitnesscoach.engine.progression.Prescription(step.workingLoad, it.reps, it.reps.first,
                                            com.personalfitnesscoach.engine.progression.ProgressionAction.HOLD); calLoads.remove(ex.id) } // then progression takes over
                                        break
                                    }
                                    is com.personalfitnesscoach.engine.progression.CalibrationStep.Stop -> { calLoads[ex.id] = step.nextSessionLoad; break }
                                }
                            }
                        } else {
                            val can = possible(ex, load)
                            val reps = minOf(it.reps.last, maxOf(1, can))
                            val rir = minOf(5, can - reps).coerceAtLeast(0).toDouble()
                            val session = E1rm.fromSet(load, reps, rir)
                            if (session != null) E1rm.update(e1rm[ex.id], session).value?.let { est -> e1rm[ex.id] = est; startE1rm.putIfAbsent(ex.id, est); startWeek.putIfAbsent(ex.id, week) }
                            // FS-5: after every full-load exposure the progression engine (PROG-001..008) writes the next prescription;
                            // the generator uses it for the same rep range and the e1RM for a new one (D-055).
                            if (it.loadFactor >= 1.0 - 1e-9) {
                                val sets = List(it.sets) { com.personalfitnesscoach.engine.model.SetLog(load, reps, rir) }
                                val next = com.personalfitnesscoach.engine.progression.Progression.next(com.personalfitnesscoach.engine.progression.ExposureInput(
                                    ex, it.reps, it.targetRir, load, sets, avail, recoveryOk = tier == Tier.FULL || tier == Tier.MODIFIED, level = p.level, age = p.age)).value
                                nextLoads[ex.id] = next
                            }
                        }
                        true1rm[ex.id] = trueOf(ex) * (1 + growth)
                    }
                }
                Volume.weekly(w.validated.exercises.map { it.exercise to it.sets.toDouble() }).forEach { (m, s) -> weekSets[m] = (weekSets[m] ?: 0.0) + s }
                weekSsu += SessionValidator.sessionSsu(w.validated)
                if (w.validated.hiitBlocks > 0) { hiitThisWeek++; lastHiitDay = day.weekday; hiitDoneEver++ }
            }
            // Week-level hard caps.
            assertTrue("${p.name} week $week sets $weekSets", weekSets.values.all { it <= Caps.weeklySetsPerMuscle(p.level) + 1e-9 })
            assertTrue("${p.name} week $week SSU $weekSsu", weekSsu <= Caps.weeklySsu(p.level) + 1e-9)
            assertTrue("${p.name} week $week HIIT $hiitThisWeek", hiitThisWeek <= 2)
            lastWeekLoads = HashMap(lastWeekLoads + thisWeekLoads)
            clock = Blueprint.advanceClock(program, clock, plan.days.size, toDo).value.nextClockWeek
        }
        return YearResult(clock, sessions, fallbacks, startE1rm, e1rm, deloads, nextLoads.mapValues { it.value.load }, startWeek.toMap())
    }

    private val full = Library.all.flatMap { it.allEquipment }.toSet() + setOf("rower", "skierg", "elliptical", "sled", "battle_ropes", "jump_rope", "medicine_ball")

    @Test fun `a full year for five different users keeps every hard rule`() {
        val personas = listOf(
            Persona("beginner-3d", Level.BEGINNER, 3, 60, 30, full, listOf(Goal.GENERAL_FITNESS, Goal.STRENGTH)) { 1.0 },
            Persona("intermediate-4d", Level.INTERMEDIATE, 4, 75, 45, full, listOf(Goal.STRENGTH, Goal.MUSCLE, Goal.CARDIO)) { if (it % 10 == 0) 0.75 else 1.0 },
            Persona("advanced-5d-home", Level.ADVANCED, 5, 60, 55, Gyms.HOME_DUMBBELLS + setOf("rower"), listOf(Goal.MUSCLE, Goal.POWER)) { 1.0 },
            Persona("older-2d-machines", Level.BEGINNER, 2, 45, 68, Gyms.MACHINES, listOf(Goal.GENERAL_FITNESS)) { 1.0 },
            Persona("busy-6d", Level.INTERMEDIATE, 6, 90, 25, full, listOf(Goal.CARDIO, Goal.STRENGTH)) { if (it % 4 == 0) 0.34 else 1.0 },
        )
        val results = personas.mapIndexed { i, p -> p.name to simulate(p, 100L + i) }.toMap()
        for ((name, r) in results) {
            val tracked = r.endE1rm.filterKeys { it in r.startE1rm }
            println("SIM $name: sessions=${r.sessions} fallbacks=${r.fallbacks} deloads=${r.deloads} clockWeek=${r.clockWeek} " +
                "e1RM improved=${tracked.count { (k, v) -> v > r.startE1rm.getValue(k) * 1.02 }}/${tracked.size} " +
                "loads=${r.progressionLoads.entries.take(6)} median gain=${tracked.map { (k, v) -> v / r.startE1rm.getValue(k) }.sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] }}")
            assertTrue("$name ran sessions", r.sessions > 50)
            assertTrue("$name fallbacks ${r.fallbacks}/${r.sessions}", r.fallbacks <= r.sessions / 10)
        }
        // Full adherence reaches the review/flex weeks; missed weeks pause the clock instead of cutting blocks.
        assertTrue("${results.getValue("beginner-3d").clockWeek}", results.getValue("beginner-3d").clockWeek >= 50)
        assertTrue(results.getValue("busy-6d").clockWeek < results.getValue("beginner-3d").clockWeek)
        // The tracked lifts got stronger over the year: lifts with an e1RM from the first 20 weeks had time to improve,
        // and most of them did — dumbbells and light bars included (D-055).
        for (name in listOf("beginner-3d", "intermediate-4d", "busy-6d")) {
            val b = results.getValue(name)
            val tracked = b.endE1rm.filterKeys { (b.startWeek[it] ?: 99) <= 20 }
            val improved = tracked.count { (k, v) -> v > b.startE1rm.getValue(k) * 1.02 }
            assertTrue("$name improved $improved of ${tracked.size}", tracked.isNotEmpty() && improved * 2 >= tracked.size)
        }
        // Light dumbbells and machine stacks do not stay at the lightest load all year (D-052).
        for ((name, r) in results) assertTrue("$name ${r.progressionLoads}", r.progressionLoads.values.count { it > 10.0 } * 2 >= r.progressionLoads.size)
    }
}
