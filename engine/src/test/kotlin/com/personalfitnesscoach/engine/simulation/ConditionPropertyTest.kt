package com.personalfitnesscoach.engine.simulation

import com.personalfitnesscoach.engine.calc.Volume
import com.personalfitnesscoach.engine.conditioning.ModalitySelection
import com.personalfitnesscoach.engine.generation.GenerationRequest
import com.personalfitnesscoach.engine.generation.SessionGenerator
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.model.Zone
import com.personalfitnesscoach.engine.program.Blueprint
import com.personalfitnesscoach.engine.program.FatLoss
import com.personalfitnesscoach.engine.program.Goal
import com.personalfitnesscoach.engine.program.WeekInput
import com.personalfitnesscoach.engine.program.WeekPlanner
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.safety.ClearanceScope
import com.personalfitnesscoach.engine.safety.ConditionLimits
import com.personalfitnesscoach.engine.safety.Conditions
import com.personalfitnesscoach.engine.safety.ControlStatus
import com.personalfitnesscoach.engine.safety.GeneratedConditions
import com.personalfitnesscoach.engine.safety.ScreeningMode
import com.personalfitnesscoach.engine.safety.SessionValidator
import com.personalfitnesscoach.engine.safety.UserCondition
import com.personalfitnesscoach.engine.safety.ValidationContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Randomised (seeded) checks for Registry 1.1: fat-loss users of every age band and random combinations of health conditions,
 * with random answers (control status, doctor's-OK scopes, weeks, phases, opt-ins). Every planned week and generated session must
 * honour the merged SAF-010 limits and pass the validator.
 */
class ConditionPropertyTest {
    private val fatLoss = Blueprint.plan(listOf(Goal.FAT_LOSS)).value
    private val general = Blueprint.plan(listOf(Goal.GENERAL_FITNESS, Goal.STRENGTH)).value
    private val machines = setOf("treadmill", "stationary_bike", "air_bike", "stair_climber")

    private fun randomCondition(rnd: Random, id: String): UserCondition {
        val scopes = when (rnd.nextInt(4)) {
            0 -> emptySet(); 1 -> setOf(ClearanceScope.LIGHT_MODERATE); 2 -> setOf(ClearanceScope.VIGOROUS)
            else -> setOf(ClearanceScope.VIGOROUS, ClearanceScope.INTERVALS)
        }
        return UserCondition(id, weeks = rnd.nextInt(20), controlled = ControlStatus.entries[rnd.nextInt(3)], clearance = scopes,
            subFlags = setOf("in_active_treatment", "cancer_has_spread_to_the_bones", "lymphoedema", "recent_breastbone_surgery", "heart_failure")
                .filter { rnd.nextInt(5) == 0 }.toSet(),
            pregnancyWeek = if (id == "pregnancy") rnd.nextInt(40) else null, weeksSinceBirth = if (id == "postpartum") rnd.nextInt(52) else null,
            attested = rnd.nextBoolean(), impactChecksPassed = rnd.nextBoolean(), impactOptIn = rnd.nextBoolean(), painRuleMetWeeks = rnd.nextInt(8),
            previouslyVigorous = rnd.nextBoolean(), alreadyDoingImpact = rnd.nextBoolean(), supineUncomfortable = rnd.nextInt(4) == 0, flare = rnd.nextInt(4) == 0)
    }

    /** Can `k` days be picked from `available` with no two on consecutive days (wrapping round the week)? */
    private fun spreadPossible(available: Set<Int>, k: Int): Boolean {
        val days = available.sorted()
        fun go(from: Int, chosen: List<Int>): Boolean {
            if (chosen.size == k) return true
            for (j in from until days.size) {
                val d = days[j]
                if (chosen.none { ((it + 1) % 7) == d || ((d + 1) % 7) == it }) if (go(j + 1, chosen + d)) return true
            }
            return false
        }
        return go(0, emptyList())
    }

    private data class Case(val i: WeekInput, val picked: List<UserCondition>, val limits: ConditionLimits, val fatLoss: Boolean)

    private fun randomCase(rnd: Random): Case {
        val ids = GeneratedConditions.entries.map { it.id }
        val picked = ids.shuffled(rnd).take(rnd.nextInt(4)).map { randomCondition(rnd, it) }
        val limits = Conditions.resolve(picked).value
        val fl = rnd.nextInt(3) != 0 && limits.fatLossOffered
        val program = if (fl) fatLoss else general
        val level = Level.entries[rnd.nextInt(3)]
        val available = if (rnd.nextInt(3) == 0) (0..6).shuffled(rnd).take(2 + rnd.nextInt(6)).toSet() else (0..6).toSet()
        val gym = Gyms.ALL[rnd.nextInt(Gyms.ALL.size)] + (if (rnd.nextBoolean()) machines else emptySet()) + (if (rnd.nextInt(3) == 0) setOf("plyo_box", "jump_rope") else emptySet())
        val i = WeekInput(level, rnd.nextInt(60), 2 + rnd.nextInt(5), gym, Blueprint.context(program, 1 + rnd.nextInt(52)), age = 30 + rnd.nextInt(50),
            availableDays = available, sessionMinutes = listOf(30, 45, 60, 75, 90)[rnd.nextInt(5)], deload = rnd.nextBoolean(),
            priorities = if (fl) listOf(Goal.FAT_LOSS) else listOf(Goal.GENERAL_FITNESS), hiitBaseReady = rnd.nextBoolean(), hiitDoneEver = rnd.nextInt(5),
            lastWeekEquivalentMinutes = rnd.nextInt(300).toDouble(), lastWeekAerobicMinutes = rnd.nextInt(200).toDouble(), lastWeekHiitWorkMinutes = rnd.nextInt(20).toDouble(),
            hiitOptIn = rnd.nextBoolean(), conditions = limits, injuries = Joint.entries.filter { rnd.nextInt(10) == 0 }.toSet())
        return Case(i, picked, limits, fl)
    }

    @Test fun `planned weeks honour merged condition limits and the fat-loss mix for random users`() {
        val rnd = Random((System.getenv("PFC_SEED") ?: "20261009").toLong())
        repeat((System.getenv("PFC_CASES") ?: "250").toInt()) { n ->
            val (i, picked, c, fl) = randomCase(rnd)
            val program = if (fl) fatLoss else general
            val plan = WeekPlanner.plan(i, program).value
            val tag = "case $n: age ${i.age} ${i.level} ${i.daysPerWeek}d fl=$fl ${picked.map { it.id }} zone=${c.maxZone} hiit=${c.hiit} impact=${c.impact}"
            assertEquals(tag, plan, WeekPlanner.plan(i, program).value)
            val slots = plan.days.flatMap { it.slots }
            val blocks = plan.days.flatMap { it.conditioning }
            // Tags, joints, effort.
            assertTrue(tag, slots.none { s -> s.exercise.limitationTags.any { it in c.avoidTags } })
            assertTrue(tag, slots.all { s -> c.jointLimits.all { (j, lim) -> s.exercise.stress(j) <= lim } })
            assertTrue(tag, slots.all { s -> s.targetRir >= (c.minRirFor(s.exercise.limitationTags) ?: 0.0) - 1e-9 })
            if (c.conservative) assertTrue(tag, slots.all { it.targetRir >= P.SAF_001.conservative_mode.min_rir })
            if (!c.failureAllowed) assertTrue(tag, slots.all { it.targetRir >= 1.0 })
            // Zones, intervals and impact.
            assertTrue(tag, blocks.all { it.zone <= c.maxZone })
            if (!c.hiitAllowed(i.weeksTraining)) assertTrue(tag, blocks.none { it.hiit })
            for (b in blocks.filter { it.hiit }) {
                c.hiitModalities?.let { assertTrue("$tag ${b.modality}", b.modality in it) }
                if (c.hiitLowImpactOnly || c.obesity) assertTrue("$tag ${b.modality}", b.modality in FatLoss.LOW_IMPACT_MODALITIES)
            }
            if (!c.impact.allowsImpact) {
                assertTrue(tag, slots.none { it.exercise.impact > 0 })
                assertTrue(tag, blocks.none { it.impact > 0 || ModalitySelection.isImpact(it.modality) })
            }
            // Fat-loss shape (FL-003, FL-004): ≥ 2 strength days, at most 3 on weekly plans with ≥ 3 days; activity accounted.
            if (fl) {
                val strength = plan.days.count { it.template.strength }
                if (plan.days.size >= 2) assertTrue(tag, strength >= 2)
                assertTrue(tag, strength <= P.FL_003.strength_days.default)
                val a = plan.activity!!
                assertTrue(tag, a.walk.minutesPerWeek >= 0.0 && a.gymZ1Minutes + a.walk.minutesPerWeek <= maxOf(200.0, a.gymZ1Minutes) + 1e-9)
                if (i.age!! >= 50) assertTrue(tag, blocks.filter { it.hiit }.all { it.modality in FatLoss.LOW_IMPACT_MODALITIES })
                if (i.age!! >= 60 && !i.hiitOptIn) assertTrue(tag, blocks.none { it.hiit })
            }
            // Type 2 diabetes scheduling.
            // Type 2 diabetes: strength on non-consecutive days whenever the available days allow it.
            if (!c.strengthOnConsecutiveDays) {
                val s = plan.days.filter { it.template.strength }.map { it.weekday }.toSet()
                if (spreadPossible(i.availableDays, s.size)) assertTrue("$tag $s avail ${i.availableDays}", s.none { ((it + 1) % 7) in s })
            }
        }
    }

    @Test fun `generated sessions pass the validator with the merged limits for random users`() {
        val rnd = Random((System.getenv("PFC_SEED") ?: "91").toLong())
        repeat((System.getenv("PFC_CASES") ?: "150").toInt()) { n ->
            val (i, picked, c, fl) = randomCase(rnd)
            val plan = WeekPlanner.plan(i, if (fl) fatLoss else general).value
            val day = plan.days.getOrNull(rnd.nextInt(maxOf(1, plan.days.size))) ?: return@repeat
            val today = if (rnd.nextInt(5) == 0) emptySet() else i.equipment.filter { rnd.nextInt(6) != 0 }.toSet()
            val tier = Tier.entries[rnd.nextInt(4)]
            val week = ValidationContext(i.level, weeksTraining = i.weeksTraining, hiitBaseReady = i.hiitBaseReady, hiitThisWeekSoFar = rnd.nextInt(2),
                impactSessionsThisWeekSoFar = rnd.nextInt(2),
                weekSetsSoFar = Volume.weekly(plan.days.take(rnd.nextInt(2)).flatMap { d -> d.slots.map { it.exercise to it.sets.toDouble() } }))
            val req = GenerationRequest(day, i.level, i.weeksTraining, listOf(20, 30, 45, 60, 90)[rnd.nextInt(5)], today, tier, i.age, ScreeningMode.STANDARD,
                inDeload = plan.deload, week = week, conditions = c, circuitJumps = rnd.nextBoolean())
            val w = SessionGenerator.generate(req).value
            val tag = "case $n ${day.template} $tier ${picked.map { it.id }}"
            if (c.blocked.isNotEmpty()) { assertTrue(tag, w.items.isEmpty() && w.validated.conditioning.isEmpty()); return@repeat }
            val ctx = week.copy(level = i.level, fullTierWorkingSets = w.fullTierWorkingSets, equipmentToday = today, inDeload = plan.deload,
                library = Library.all.filter { !it.userAddOnly }, conditions = c)
            val v = SessionValidator.violations(w.validated, ctx)
            assertTrue("$tag: $v", v.isEmpty())
            assertTrue(tag, w.items.none { it.exercise.limitationTags.any { t -> t in c.avoidTags } })
            assertTrue(tag, w.items.all { it.targetRir >= (c.minRirFor(it.exercise.limitationTags) ?: 1.0) - 1e-9 })
            if (!c.failureAllowed) assertTrue(tag, w.items.none { it.lastSetToFailure })
            assertTrue(tag, w.validated.conditioning.all { it.zone <= c.maxZone })
            assertTrue(tag, w.circuits.filterNotNull().all { cp -> cp.moves.none { m -> m.tags.any { it in c.avoidTags } } })
            if (!c.impact.allowsImpact) assertTrue(tag, w.circuits.filterNotNull().all { cp -> cp.moves.none { it.jumping } })
            assertTrue(tag, w.items.all { it.exercise.usableWith(today) })
            assertTrue(tag, w.balanceDrills.all { d -> d.drill.tags.none { it in c.avoidTags } })
            assertTrue(tag, (w.warmupDrills + w.cooldown).all { d -> d.drill.tags.none { it in c.avoidTags } })
            if (w.boneLoading != null) assertTrue(tag, w.boneLoading!!.minutes <= P.CON_004.bone_loading_block_max_minutes + 1e-9)
            assertTrue(tag, w.validated.conditioning.all { it.zone != Zone.Z4 || c.maxZone >= Zone.Z4 })
        }
    }
}
