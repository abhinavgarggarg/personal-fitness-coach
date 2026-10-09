package com.personalfitnesscoach.engine.simulation

import com.personalfitnesscoach.engine.calc.Volume
import com.personalfitnesscoach.engine.generation.GenerationRequest
import com.personalfitnesscoach.engine.generation.SessionGenerator
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.model.Zone
import com.personalfitnesscoach.engine.program.Blueprint
import com.personalfitnesscoach.engine.program.FULL_GYM
import com.personalfitnesscoach.engine.program.Goal
import com.personalfitnesscoach.engine.program.Individual
import com.personalfitnesscoach.engine.program.Selector
import com.personalfitnesscoach.engine.program.SelectionContext
import com.personalfitnesscoach.engine.program.WeekInput
import com.personalfitnesscoach.engine.program.WeekKind
import com.personalfitnesscoach.engine.program.WeekPlanner
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.safety.Caps
import com.personalfitnesscoach.engine.safety.ScreeningMode
import com.personalfitnesscoach.engine.safety.SessionValidator
import com.personalfitnesscoach.engine.safety.ValidationContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** Gym set-ups the planner must cope with. */
object Gyms {
    val FULL = FULL_GYM
    val HOME_DUMBBELLS = setOf("dumbbells", "bench", "incline_bench", "pullup_bar", "bands", "mat", "jump_rope", "kettlebells")
    val MACHINES = setOf("cable", "cable_row", "lat_pulldown", "leg_press", "hack_squat", "smith_machine", "chest_press_machine",
        "shoulder_press_machine", "row_machine", "assisted_pullup_machine", "leg_extension", "leg_curl", "calf_raise_machine", "pec_deck",
        "hip_thrust_machine", "hip_adductor_machine", "back_extension", "bench", "mat", "rower", "elliptical")
    val BODYWEIGHT = setOf("mat", "pullup_bar")
    val ALL = listOf(FULL, HOME_DUMBBELLS, MACHINES, BODYWEIGHT)
}

/**
 * Randomised (seeded, so reproducible) checks that every planned week and generated session
 * respects the hard rules, whatever the profile, gym, schedule, pain or readiness.
 */
class PlannerPropertyTest {
    private val program = Blueprint.plan(listOf(Goal.STRENGTH, Goal.CARDIO)).value

    private fun randomInput(rnd: Random): WeekInput {
        val level = Level.entries[rnd.nextInt(3)]
        val days = 2 + rnd.nextInt(5)
        // A third of profiles have restricted days, sometimes fewer than the days they asked for (review findings R04, R16).
        val available = if (rnd.nextInt(3) == 0) (0..6).shuffled(rnd).take(2 + rnd.nextInt(6)).toSet() else (0..6).toSet()
        val injuries = Joint.entries.filter { rnd.nextInt(8) == 0 }.toSet()
        val inj = Individual.injuries(injuries, weeksSinceStart = rnd.nextInt(8)).value
        val week = 1 + rnd.nextInt(52)
        val gym = Gyms.ALL[rnd.nextInt(Gyms.ALL.size)] + (if (rnd.nextInt(3) == 0) setOf("plyo_box", "jump_rope") else emptySet())
        return WeekInput(level, rnd.nextInt(120), days, gym, Blueprint.context(program, week),
            age = 18 + rnd.nextInt(60), availableDays = available, preferredDays = (0..6).filter { rnd.nextBoolean() }.toSet(),
            sessionMinutes = listOf(30, 45, 60, 75, 90)[rnd.nextInt(5)], deload = rnd.nextBoolean(),
            screening = if (rnd.nextInt(6) == 0) ScreeningMode.CONSERVATIVE else ScreeningMode.STANDARD,
            hiitBaseReady = rnd.nextBoolean(), hiitDoneEver = rnd.nextInt(5), injuries = injuries, blockedTags = inj.blockedTags,
            jointLimits = inj.regions.associate { it.region to (it.maxStress ?: 4) }, crowded = rnd.nextBoolean())
    }

    @Test fun `planned weeks respect equipment, limits, caps, HIIT rules and spacing for random profiles`() {
        val rnd = Random((System.getenv("PFC_SEED") ?: "20261008").toLong())
        repeat((System.getenv("PFC_CASES") ?: "250").toInt()) { n ->
            val i = randomInput(rnd)
            val r = WeekPlanner.plan(i, program)
            val plan = r.value
            val tag = "case $n: ${i.level} ${i.daysPerWeek}d ${i.sessionMinutes}min wk${i.week.programWeek} ${i.week.kind} deload=${i.deload} ${i.screening}"
            // Deterministic.
            assertEquals(tag, plan, WeekPlanner.plan(i, program).value)
            val ctx = SelectionContext(i.level, i.weeksTraining, i.equipment, i.blockedTags, i.jointLimits)
            val slots = plan.days.flatMap { it.slots }
            assertTrue(tag, slots.all { Selector.allowed(it.exercise, ctx) })
            // Caps: weekly per muscle, per session per muscle, per session total.
            val weekly = Volume.weekly(slots.map { it.exercise to it.sets.toDouble() })
            assertTrue("$tag over weekly cap: ${weekly.filterValues { it > Caps.weeklySetsPerMuscle(i.level) + 1e-9 }} " +
                plan.days.joinToString { d -> d.template.name + d.slots.map { "${it.exercise.id}x${it.sets}" } },
                weekly.values.all { it <= Caps.weeklySetsPerMuscle(i.level) + 1e-9 })
            for (d in plan.days) {
                assertTrue(tag, d.workingSets <= Caps.workingSetsPerSession(i.level))
                val direct = Volume.directPerSession(d.slots.map { it.exercise to it.sets.toDouble() })
                assertTrue(tag, direct.values.all { it <= Caps.directSetsPerMuscleSession(i.level) + 1e-9 })
                assertTrue(tag, d.slots.all { it.sets >= 1 })
            }
            // Days: never more than 6, always inside the available days.
            assertTrue(tag, plan.days.size <= Caps.trainingDaysPerWeek())
            assertTrue(tag, plan.days.all { it.weekday in i.availableDays })
            // HIIT: count, prerequisites, deload/calibration/conservative, CON-003 and spacing.
            val hiitDays = plan.days.filter { d -> d.conditioning.any { it.hiit } }.map { it.weekday }
            assertTrue(tag, hiitDays.size <= P.HIIT_001.default_max)
            val noHiit = !i.hiitBaseReady || i.screening != ScreeningMode.STANDARD || plan.deload ||
                plan.blockType == com.personalfitnesscoach.engine.program.BlockType.CALIBRATE
            if (noHiit) assertTrue(tag, hiitDays.isEmpty())
            val heavy = plan.days.filter { it.heavyLower }.map { it.weekday }.toSet()
            for (h in hiitDays) assertTrue("$tag hiit $h heavy $heavy", ((h + 1) % 7) !in heavy)
            if (i.screening == ScreeningMode.CONSERVATIVE) {
                assertTrue(tag, plan.days.all { d -> d.conditioning.none { it.zone >= Zone.Z3 } })
                assertTrue(tag, slots.all { it.targetRir >= P.SAF_001.conservative_mode.min_rir })
            }
            if (plan.deload) assertTrue(tag, slots.all { it.targetRir >= P.DEL_003.min_rir } && slots.none { it.power })
            // SCH-002 spacing whatever days are available: heavy lower days ≥ 48 h apart (wrapping round the week)
            // and no more than 3 hard days in a row (review finding R04).
            for (a in heavy) for (b in heavy) if (a != b) assertTrue("$tag avail ${i.availableDays} heavy $heavy", ((b - a + 7) % 7) >= 2)
            assertTrue("$tag avail ${i.availableDays}", com.personalfitnesscoach.engine.program.Templates.maxHardRun(plan.days.map { it.weekday },
                plan.days.map { it.template }) <= P.SCH_002.max_consecutive_hard_days)
            // CON-004: at most one impact session a week, counting plyometric power work too (R03).
            val impactDays = plan.days.filter { d -> d.slots.any { it.exercise.impact > 0 } || d.conditioning.any { it.impact > 0 } }
            assertTrue("$tag impact days: " + impactDays.map { d -> "${d.weekday}:${d.slots.filter { it.exercise.impact > 0 }.map { "${it.spec.role}:${it.exercise.id}" }}+${d.conditioning.filter { it.impact > 0 }.map { it.modality }}" },
                impactDays.size <= P.CON_004.impact_sessions_per_week_max)
            // Never crunches by default (CORE-001).
            assertTrue(tag, slots.none { it.exercise.userAddOnly })
            // An exercise at most once a day; jumps and throws only in power slots.
            for (d in plan.days) assertEquals(tag, d.slots.size, d.slots.map { it.exercise.id }.toSet().size)
            assertTrue(tag, slots.none { it.spec.role != com.personalfitnesscoach.engine.program.SlotRole.POWER &&
                it.spec.role != com.personalfitnesscoach.engine.program.SlotRole.ROTATION && it.exercise.powerCapable &&
                it.exercise.loadType == com.personalfitnesscoach.engine.model.LoadType.BODYWEIGHT })
        }
    }

    @Test fun `generated sessions always pass the validator for random days, tiers, time, pain and missing kit`() {
        val rnd = Random((System.getenv("PFC_SEED") ?: "77").toLong())
        repeat((System.getenv("PFC_CASES") ?: "120").toInt()) { n ->
            val i = randomInput(rnd)
            val plan = WeekPlanner.plan(i, program).value
            val day = plan.days.getOrNull(rnd.nextInt(maxOf(1, plan.days.size))) ?: return@repeat
            val today = i.equipment.filter { rnd.nextInt(5) != 0 }.toSet()
            val tier = Tier.entries[rnd.nextInt(4)]
            val pain = Joint.entries.filter { rnd.nextInt(10) == 0 }.toSet()
            val week = ValidationContext(i.level, hiitBaseReady = i.hiitBaseReady, hiitThisWeekSoFar = rnd.nextInt(3),
                hoursSinceLastHiit = if (rnd.nextBoolean()) null else rnd.nextInt(96).toDouble(),
                hoursToNextHeavyLower = if (rnd.nextBoolean()) null else rnd.nextInt(96).toDouble(),
                weekSetsSoFar = Volume.weekly(plan.days.take(rnd.nextInt(3)).flatMap { d -> d.slots.map { it.exercise to it.sets.toDouble() } }))
            val req = GenerationRequest(day, i.level, i.weeksTraining, listOf(15, 25, 40, 60, 90)[rnd.nextInt(5)], today, tier, i.age, i.screening,
                painCaution = pain, jointLimits = i.jointLimits + pain.associateWith { 2 }, blockedTags = i.blockedTags,
                inDeload = plan.deload, hiitEarlierToday = rnd.nextInt(6) == 0, crowded = i.crowded,
                e1rm = Library.all.filter { it.trackE1rm && rnd.nextBoolean() }.associate { it.id to 20.0 + rnd.nextInt(150) }, week = week)
            val w = SessionGenerator.generate(req).value
            val ctx = week.copy(level = i.level, weeksTraining = i.weeksTraining, screening = i.screening, jointLimits = req.jointLimits,
                blockedTags = i.blockedTags, inDeload = plan.deload, fullTierWorkingSets = w.fullTierWorkingSets, equipmentToday = today,
                library = Library.all.filter { !it.userAddOnly })
            val v = SessionValidator.violations(w.validated, ctx)
            assertTrue("case $n ${day.template} $tier: $v", v.isEmpty())
            assertTrue(w.items.all { it.exercise.usableWith(today) })
            assertTrue(w.items.all { it.exercise.limitationTags.none { t -> t in i.blockedTags } })
            if (w.tier == Tier.RECOVERY) assertTrue(w.items.isEmpty())
            if (req.hiitEarlierToday) assertTrue(w.items.none { it.role == com.personalfitnesscoach.engine.program.SlotRole.POWER })
            assertTrue(w.items.all { it.sets >= 1 && it.targetRir >= 1.0 })
            assertTrue(w.items.all { it.lastSetToFailure.not() || it.exercise.failureSafe })
            assertEquals("case $n ${w.items.map { it.exercise.id }}", w.items.size, w.items.map { it.exercise.id }.toSet().size)
        }
    }

    @Test fun `deload and pivot weeks keep the same weekdays as the loading weeks`() {
        val i = WeekInput(Level.INTERMEDIATE, 30, 4, FULL_GYM, Blueprint.context(program, Blueprint.deloadWeeks(program)[1]), sessionMinutes = 60)
        assertEquals(WeekKind.DELOAD_OR_PIVOT, i.week.kind)
        val deload = WeekPlanner.plan(i.copy(deload = true), program).value
        val loading = WeekPlanner.plan(i.copy(week = Blueprint.context(program, Blueprint.deloadWeeks(program)[1] - 1)), program).value
        assertEquals(loading.days.map { it.weekday }, deload.days.map { it.weekday })
        val pivot = WeekPlanner.plan(i.copy(deload = false), program).value
        assertEquals(program.blocks[i.week.blockIndex + 1].type, pivot.blockType)
    }
}
