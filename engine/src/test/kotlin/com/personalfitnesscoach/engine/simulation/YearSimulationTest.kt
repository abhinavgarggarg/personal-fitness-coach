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
import com.personalfitnesscoach.engine.safety.ClearanceScope
import com.personalfitnesscoach.engine.safety.Conditions
import com.personalfitnesscoach.engine.safety.UserCondition
import com.personalfitnesscoach.engine.safety.SessionValidator
import com.personalfitnesscoach.engine.safety.ValidationContext
import org.junit.Assert.assertEquals
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
        val priorities: List<Goal>, val conditions: List<UserCondition> = emptyList(), val adherence: (Int) -> Double,
    )

    data class YearResult(val clockWeek: Int, val sessions: Int, val fallbacks: Int, val startE1rm: Map<String, Double>, val endE1rm: Map<String, Double>, val deloads: Int,
        val progressionLoads: Map<String, Double> = emptyMap(), val startWeek: Map<String, Int> = emptyMap(),
        /** Fat-loss goal: this week's FL-002 target, week by week. */
        val activityTargets: List<Double> = emptyList(), val hiitSessions: Int = 0)

    private fun simulate(p: Persona, seed: Long): YearResult {
        val rnd = Random(seed)
        val program = Blueprint.plan(p.priorities).value
        val limits = Conditions.resolve(p.conditions).value
        var lastEq = 0.0
        val targets = ArrayList<Double>()
        var hiitTotal = 0
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
                hiitBaseReady = week > 3 && clock > 2, hiitDoneEver = hiitDoneEver, coreLifts = coreLifts, carryOrRotationLastWeek = carryRotLast,
                lastWeekEquivalentMinutes = lastEq, conditions = limits)
            val plan = WeekPlanner.plan(input, program).value
            // Registry 1.1: the plan honours the condition limits and, for the fat-loss goal, keeps ≥ 2 strength days and an activity plan.
            val planned = plan.days.flatMap { it.slots }
            assertTrue("${p.name} week $week tags", planned.none { s -> s.exercise.limitationTags.any { it in limits.avoidTags } })
            assertTrue("${p.name} week $week zone", plan.days.flatMap { it.conditioning }.all { it.zone <= limits.maxZone })
            if (input.fatLoss) {
                assertTrue("${p.name} week $week strength days", plan.days.count { it.template.strength } >= minOf(2, plan.days.size))
                targets += plan.activity!!.target.thisWeek
            }
            coreLifts = coreLifts + plan.coreLifts
            carryRotLast = plan.days.flatMap { it.slots }.map { it.exercise.pattern }.filter { it == Pattern.LOADED_CARRY || it == Pattern.ROTATION }.toSet()
            val toDo = Math.round(plan.days.size * p.adherence(week)).toInt()
            // The user walks as planned in the weeks they train as planned (part of it otherwise).
            lastEq = (plan.activity?.plannedEquivalent ?: 0.0) * (if (plan.days.isEmpty()) 1.0 else toDo.toDouble() / plan.days.size)
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
                    inDeload = plan.deload, conditions = limits)
                val req = GenerationRequest(day, p.level, week, p.minutes, p.gym, tier, p.age, inventory = inv, e1rm = e1rm.toMap(),
                    progression = nextLoads.toMap(), calibrationLoads = calLoads.toMap(), lastWeekLoads = lastWeekLoads, inDeload = plan.deload, week = vctx,
                    conditions = limits)
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
                if (w.validated.hiitBlocks > 0) { hiitThisWeek++; lastHiitDay = day.weekday; hiitDoneEver++; hiitTotal++ }
                assertTrue("${p.name} week $week items", w.items.none { it.exercise.limitationTags.any { t -> t in limits.avoidTags } })
                if (!limits.failureAllowed) assertTrue("${p.name} week $week failure", w.items.none { it.lastSetToFailure })
            }
            // Week-level hard caps.
            assertTrue("${p.name} week $week sets $weekSets", weekSets.values.all { it <= Caps.weeklySetsPerMuscle(p.level) + 1e-9 })
            assertTrue("${p.name} week $week SSU $weekSsu", weekSsu <= Caps.weeklySsu(p.level) + 1e-9)
            assertTrue("${p.name} week $week HIIT $hiitThisWeek", hiitThisWeek <= 2)
            lastWeekLoads = HashMap(lastWeekLoads + thisWeekLoads)
            clock = Blueprint.advanceClock(program, clock, plan.days.size, toDo).value.nextClockWeek
        }
        return YearResult(clock, sessions, fallbacks, startE1rm, e1rm, deloads, nextLoads.mapValues { it.value.load }, startWeek.toMap(), targets, hiitTotal)
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

    @Test fun `a fat-loss year for one user per age band keeps every rule and reaches the weekly activity target`() {
        val machines = Gyms.MACHINES + setOf("stationary_bike", "treadmill")
        val personas = listOf(
            Persona("fl-35", Level.INTERMEDIATE, 3, 60, 35, full + setOf("stationary_bike"), listOf(Goal.FAT_LOSS)) { 1.0 },
            Persona("fl-45-hbp", Level.BEGINNER, 4, 45, 45, Gyms.HOME_DUMBBELLS + setOf("rower"), listOf(Goal.FAT_LOSS),
                listOf(UserCondition("hbp_controlled"))) { if (it % 8 == 0) 0.5 else 1.0 },
            Persona("fl-55-oa-knee", Level.INTERMEDIATE, 4, 60, 55, full + machines, listOf(Goal.FAT_LOSS), listOf(UserCondition("oa_knee"))) { 1.0 },
            Persona("fl-62-t2d-obesity", Level.BEGINNER, 3, 45, 62, machines, listOf(Goal.FAT_LOSS),
                listOf(UserCondition("t2d", clearance = setOf(ClearanceScope.VIGOROUS)), UserCondition("obesity"))) { 1.0 },
            Persona("fl-70-osteoporosis", Level.BEGINNER, 2, 45, 70, machines, listOf(Goal.FAT_LOSS), listOf(UserCondition("osteoporosis"))) { 1.0 },
        )
        val results = personas.mapIndexed { i, p -> p.name to simulate(p, 500L + i) }.toMap()
        for ((name, r) in results) {
            val tracked = r.endE1rm.filterKeys { it in r.startE1rm }
            println("SIM $name: sessions=${r.sessions} fallbacks=${r.fallbacks} hiit=${r.hiitSessions} targets=${r.activityTargets.take(5)}…${r.activityTargets.takeLast(1)} " +
                "e1RM improved=${tracked.count { (k, v) -> v > r.startE1rm.getValue(k) * 1.02 }}/${tracked.size}")
            assertTrue("$name ran sessions", r.sessions > 50)
            assertTrue("$name fallbacks ${r.fallbacks}/${r.sessions}", r.fallbacks <= r.sessions / 10)
            // FL-002: starts at the 150 floor, grows ≤ 15% (12% at 65+) a week, and reaches the band's lower bound.
            val p = personas.first { it.name == name }
            val band = com.personalfitnesscoach.engine.program.FatLoss.targetRange(p.age)
            assertEquals(150.0, r.activityTargets.first(), 1e-9)
            // A week trained only in part lowers next week's target (growth is from what was done); it climbs back within 4 weeks.
            var below = 0; var worst = 0
            for (x in r.activityTargets) { below = if (x < band.first - 1e-9) below + 1 else 0; worst = maxOf(worst, below) }
            assertTrue("$name ${r.activityTargets}", r.activityTargets.any { it >= band.first - 1e-9 } && worst <= 4)
        }
        // Strength still improves on the fat-loss programme.
        for (name in listOf("fl-35", "fl-55-oa-knee")) {
            val b = results.getValue(name)
            val tracked = b.endE1rm.filterKeys { (b.startWeek[it] ?: 99) <= 20 }
            val improved = tracked.count { (k, v) -> v > b.startE1rm.getValue(k) * 1.02 }
            assertTrue("$name improved $improved of ${tracked.size}", tracked.isNotEmpty() && improved * 2 >= tracked.size)
        }
        // 60+: no intervals unless accepted.
        assertEquals(0, results.getValue("fl-62-t2d-obesity").hiitSessions)
        assertEquals(0, results.getValue("fl-70-osteoporosis").hiitSessions)
    }
}
