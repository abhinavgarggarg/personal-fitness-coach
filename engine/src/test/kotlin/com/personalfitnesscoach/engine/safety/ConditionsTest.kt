package com.personalfitnesscoach.engine.safety

import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.generation.GenerationRequest
import com.personalfitnesscoach.engine.generation.SessionGenerator
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.model.Zone
import com.personalfitnesscoach.engine.program.Blueprint
import com.personalfitnesscoach.engine.program.BlockType
import com.personalfitnesscoach.engine.program.FULL_GYM
import com.personalfitnesscoach.engine.program.Goal
import com.personalfitnesscoach.engine.program.WeekInput
import com.personalfitnesscoach.engine.program.WeekKind
import com.personalfitnesscoach.engine.program.WeekPlanner
import com.personalfitnesscoach.engine.planning.RangeOfMotion
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SAF-010 health-condition profiles: resolve, merge, and their effect on the planner, generator and validator. */
class ConditionsTest {
    private fun resolve(vararg u: UserCondition) = Conditions.resolve(u.toList()).value
    private val program = Blueprint.plan(listOf(Goal.GENERAL_FITNESS)).value
    private val gym = FULL_GYM + setOf("treadmill", "stationary_bike", "air_bike", "stair_climber")
    private fun week(type: BlockType) = (1..52).first { Blueprint.context(program, it).block.type == type && Blueprint.context(program, it).kind == WeekKind.LOADING }
    private fun input(c: ConditionLimits, days: Int = 3, age: Int = 50, type: BlockType = BlockType.CONDITIONING) =
        WeekInput(Level.INTERMEDIATE, 40, days, gym, Blueprint.context(program, week(type)), age = age, hiitBaseReady = true, hiitDoneEver = 4,
            lastWeekHiitWorkMinutes = 20.0, conditions = c)

    @Test fun `TC-SAF-010a several conditions merge with the most restrictive value per control`() {
        assertEquals("hbp_not_controlled", Conditions.hbpEntryId(ControlStatus.NOT_SURE))
        assertEquals("hbp_controlled", Conditions.hbpEntryId(ControlStatus.YES))
        val r = Conditions.resolve(listOf(UserCondition("hbp_not_controlled"), UserCondition("oa_knee")))
        val m = r.value
        assertEquals(setOf("hbp_not_controlled", "oa_knee"), m.entries)
        assertEquals(Zone.Z1, m.maxZone) // before_vigorous without a doctor's OK
        assertEquals(HiitPermission.NO, m.hiit)
        assertEquals(3.0, m.minRir!!, 1e-9)
        assertFalse(m.failureAllowed)
        assertEquals(ImpactLevel.LOW, m.impact)
        assertTrue(m.avoidTags.containsAll(setOf("breath_hold_max", "isometric_heavy", "jumping")))
        assertEquals(2, m.jointLimits[Joint.KNEE])
        assertEquals(5, m.extraWarmupMin); assertEquals(5, m.extraCooldownMin)
        assertTrue("hbp_not_controlled" in m.clearancePrompts)
        assertEquals(setOf("deep_knee_flexion"), m.rangeLimitedTags)
        assertTrue(r.decisions.any { it.reason == ReasonKey.CONDITIONS_MERGED && RuleIds.SAF_010 in it.ruleIds })
        // A doctor's OK unlocks only its scope: vigorous → Z2 (no intervals); intervals → Z3 and intervals after the base.
        val vig = resolve(UserCondition("hbp_not_controlled", clearance = setOf(ClearanceScope.VIGOROUS)))
        assertEquals(Zone.Z2, vig.maxZone); assertEquals(HiitPermission.NO, vig.hiit)
        val iv = resolve(UserCondition("hbp_not_controlled", clearance = setOf(ClearanceScope.VIGOROUS, ClearanceScope.INTERVALS)))
        assertEquals(Zone.Z3, iv.maxZone); assertEquals(HiitPermission.AFTER_BASE, iv.hiit)
        assertFalse(iv.hiitAllowed(3)); assertTrue(iv.hiitAllowed(4))
        // Control status: asthma "not sure" counts as not controlled.
        val asthma = resolve(UserCondition("asthma", controlled = ControlStatus.NOT_SURE))
        assertEquals(Zone.Z1, asthma.maxZone); assertEquals(HiitPermission.NO, asthma.hiit)
        assertTrue(asthma.prompts.any { it.contains("reviewed") })
        assertEquals(HiitPermission.YES, resolve(UserCondition("asthma", controlled = ControlStatus.YES)).hiit)
        // Interval machine lists intersect; a child entry brings its parent.
        val feet = resolve(UserCondition("diabetes_feet"), UserCondition("obesity_severe"))
        assertEquals(setOf(Modality.ROWER, Modality.STATIONARY_BIKE), feet.hiitModalities)
        assertTrue("obesity" in feet.entries && feet.obesity)
        // No condition: nothing is limited.
        assertEquals(ConditionLimits.NONE, Conditions.resolve(emptyList()).value)
        // The table never carries clinical values or medicine names (SAF-010).
        val text = GeneratedConditions.entries.flatMap { it.prompts + it.stopSigns + it.asks }.joinToString(" ").lowercase()
        for (w in listOf("mmhg", "mmol", "mg/dl", "insulin", "metformin", "units of")) assertFalse(w, text.contains(w))
        assertEquals(P.SAF_010.entries, GeneratedConditions.entries.map { it.id })
    }

    @Test fun `TC-SAF-010b doctor's-OK entries, phases, unlocks and sub-flags resolve before the merge`() {
        // "always": SAF-001 conservative mode until a doctor's OK; then the entry's limits up to the confirmed scope.
        val heart = resolve(UserCondition("heart"))
        assertTrue(heart.conservative); assertEquals(Zone.Z1, heart.maxZone); assertEquals(HiitPermission.NO, heart.hiit)
        assertEquals(3.0, heart.minRir!!, 1e-9); assertTrue(heart.impact <= ImpactLevel.LOW); assertTrue(heart.effortByFeel)
        val heartVig = resolve(UserCondition("heart", clearance = setOf(ClearanceScope.LIGHT_MODERATE, ClearanceScope.VIGOROUS)))
        assertFalse(heartVig.conservative); assertEquals(Zone.Z2, heartVig.maxZone); assertEquals(2.0, heartVig.minRir!!, 1e-9); assertEquals(HiitPermission.NO, heartVig.hiit)
        val heartInt = resolve(UserCondition("heart", clearance = setOf(ClearanceScope.INTERVALS)))
        assertEquals(Zone.Z3, heartInt.maxZone); assertEquals(HiitPermission.AFTER_BASE, heartInt.hiit)
        assertTrue(resolve(UserCondition("heart", subFlags = setOf("recent_breastbone_surgery"))).avoidTags.contains("overhead_heavy"))
        // Pregnancy: lying on the back or front avoided from week 20; fat-loss goal and weight features off; impact only if already doing it.
        val preg = resolve(UserCondition("pregnancy", pregnancyWeek = 22, clearance = setOf(ClearanceScope.LIGHT_MODERATE)))
        assertTrue(preg.avoidTags.containsAll(setOf("supine_lying", "prone_lying"))); assertFalse(preg.fatLossOffered); assertFalse(preg.weightFeatures)
        assertFalse(preg.impact.allowsImpact)
        assertFalse("supine_lying" in resolve(UserCondition("pregnancy", pregnancyWeek = 12)).avoidTags)
        assertTrue(resolve(UserCondition("pregnancy", pregnancyWeek = 12, alreadyDoingImpact = true, clearance = setOf(ClearanceScope.LIGHT_MODERATE))).impact.allowsImpact)
        // Postpartum phases: weeks 0–6, 6–12 only after the attestation, 12+ impact once the return-to-impact checks are passed.
        val pp3 = resolve(UserCondition("postpartum", weeksSinceBirth = 3))
        assertEquals(4.0, pp3.minRir!!, 1e-9); assertEquals(ImpactLevel.NONE, pp3.impact); assertTrue("jumping" in pp3.avoidTags); assertFalse(pp3.fatLossOffered)
        assertEquals(4.0, resolve(UserCondition("postpartum", weeksSinceBirth = 8)).minRir!!, 1e-9)
        assertEquals(3.0, resolve(UserCondition("postpartum", weeksSinceBirth = 8, attested = true)).minRir!!, 1e-9)
        val pp20 = resolve(UserCondition("postpartum", weeksSinceBirth = 20, attested = true))
        assertEquals(ImpactLevel.NONE, pp20.impact); assertEquals(HiitPermission.NO, pp20.hiit); assertTrue(pp20.fatLossOffered)
        val pp20ok = resolve(UserCondition("postpartum", weeksSinceBirth = 20, attested = true, impactChecksPassed = true))
        assertTrue(pp20ok.impact.allowsImpact); assertEquals(HiitPermission.AFTER_BASE, pp20ok.hiit); assertFalse("jumping" in pp20ok.avoidTags)
        // Severe obesity: Z1, no impact and knee 2 for 8 weeks; then Z3, low impact by opt-in, knee 3.
        val sev0 = resolve(UserCondition("obesity_severe"))
        assertEquals(Zone.Z1, sev0.maxZone); assertEquals(ImpactLevel.NONE, sev0.impact); assertEquals(2, sev0.jointLimits[Joint.KNEE])
        val sev8 = resolve(UserCondition("obesity_severe", weeks = 8, impactOptIn = true))
        assertEquals(Zone.Z3, sev8.maxZone); assertEquals(3, sev8.jointLimits[Joint.KNEE])
        // Osteoarthritis: jumping allowed again only after the pain rule held for 4 weeks and the user opts in.
        assertTrue("jumping" in resolve(UserCondition("oa_knee", weeks = 6, impactOptIn = true, painRuleMetWeeks = 2)).avoidTags)
        val oaOk = resolve(UserCondition("oa_knee", weeks = 6, impactOptIn = true, painRuleMetWeeks = 4))
        assertFalse("jumping" in oaOk.avoidTags); assertEquals(3, oaOk.jointLimits[Joint.KNEE]); assertTrue(oaOk.impact.allowsImpact)
        // Cancer: active treatment makes the doctor's OK "always" and removes the fat-loss goal; spread to bones = no impact.
        val ca = resolve(UserCondition("cancer", subFlags = setOf("in_active_treatment")))
        assertTrue(ca.conservative); assertFalse(ca.fatLossOffered); assertFalse(ca.weightFeatures)
        assertEquals(ImpactLevel.NONE, resolve(UserCondition("cancer", subFlags = setOf("cancer_has_spread_to_the_bones"), clearance = setOf(ClearanceScope.VIGOROUS))).impact)
        assertFalse(resolve(UserCondition("cancer")).conservative)
        // Osteoporosis: bone loading and required work — but not when another entry limits impact (osteoarthritis).
        val ost = resolve(UserCondition("osteoporosis"))
        assertTrue(ost.boneLoading); assertEquals(2, ost.balanceSessionsPerWeek); assertEquals(2, ost.backExtensorSessionsPerWeek)
        assertFalse(resolve(UserCondition("osteoporosis"), UserCondition("oa_knee")).boneLoading)
        // Low back pain: the RIR floor applies to spinal loading only; a flare adds a spine limit and avoids loading and flexion.
        val lbp = resolve(UserCondition("low_back_pain"))
        assertEquals(2.0, lbp.minRirFor(setOf("spinal_loading"))!!, 1e-9); assertNull(lbp.minRirFor(setOf("overhead")))
        val flare = resolve(UserCondition("low_back_pain", flare = true))
        assertEquals(2, flare.jointLimits[Joint.SPINE]); assertTrue(flare.avoidTags.containsAll(setOf("spinal_loading", "spinal_flexion")))
        // block_if answered yes: follow the care provider instead of a plan.
        assertEquals(setOf("pregnancy"), resolve(UserCondition("pregnancy", blockIfYes = true)).blocked)
        // Type 2 diabetes scheduling.
        val t2 = resolve(UserCondition("t2d"))
        assertEquals(2, t2.maxConsecutiveInactiveDays); assertFalse(t2.strengthOnConsecutiveDays)
        assertTrue(resolve(UserCondition("t1d")).strengthBeforeCardio)
    }

    @Test fun `TC-SAF-010c the planner, generator and validator apply the merged limits`() {
        val heart = resolve(UserCondition("heart"))
        val plan = WeekPlanner.plan(input(heart, days = 4), program).value
        val blocks = plan.days.flatMap { it.conditioning }
        assertTrue(blocks.all { it.zone == Zone.Z1 && !it.hiit })
        assertTrue(plan.days.flatMap { it.slots }.all { it.targetRir >= 3.0 && it.exercise.limitationTags.none { t -> t in heart.avoidTags } })
        for (day in plan.days.filter { it.slots.isNotEmpty() }) {
            val w = SessionGenerator.generate(GenerationRequest(day, Level.INTERMEDIATE, 40, 75, gym, Tier.FULL, age = 50,
                week = ValidationContext(Level.INTERMEDIATE, hiitBaseReady = true), conditions = heart)).value
            assertTrue(w.items.none { it.lastSetToFailure } && w.items.all { it.targetRir >= 3.0 })
            assertTrue(w.validated.conditioning.all { it.zone == Zone.Z1 && !it.countsAsHiit })
            assertTrue(w.warmupMinutes >= 10.0 + heart.extraWarmupMin - 1e-9)
            assertTrue(w.effortByFeel && w.stopSigns.isNotEmpty())
            assertTrue(w.items.none { it.exercise.limitationTags.any { t -> t in heart.avoidTags } })
        }
        // The validator corrects a session that breaks the limits: Z2 → Z1 for a Z1 cap, intervals moved to an allowed machine.
        val z1 = ValidationContext(Level.INTERMEDIATE, hiitBaseReady = true, conditions = ConditionLimits(maxZone = Zone.Z1))
        val v = SessionValidator.validate(Session(Tier.FULL, emptyList(), listOf(ConditioningBlock(Modality.ROWER, Zone.Z2, 20.0))), z1).value
        assertEquals(Zone.Z1, v.session.conditioning.single().zone)
        val feet = ValidationContext(Level.INTERMEDIATE, hiitBaseReady = true, conditions = resolve(UserCondition("diabetes_feet")), weeksTraining = 52)
        val hiit = Session(Tier.FULL, emptyList(), listOf(ConditioningBlock(Modality.SKIERG, Zone.Z3, 4.0, 4.0, hiit = true, protocol = HiitProtocol.SHORT)))
        val fixed = SessionValidator.validate(hiit, feet).value.session.conditioning.single()
        assertTrue(fixed.modality in setOf(Modality.ROWER, Modality.STATIONARY_BIKE) || !fixed.countsAsHiit)
        // Avoided tags and condition joint limits rule exercises out in the validator too.
        val back = ValidationContext(Level.INTERMEDIATE, conditions = resolve(UserCondition("osteoporosis_spine_fracture")), library = Library.all,
            equipmentToday = FULL_GYM)
        assertFalse(SessionValidator.exerciseAllowed(Library.require("crunch"), back))
        assertFalse(SessionValidator.exerciseAllowed(Library.require("deadlift"), back)) // spine limit 2
        // A blocked entry plans nothing: follow the care provider.
        val blocked = SessionGenerator.generate(GenerationRequest(plan.days.first { it.slots.isNotEmpty() }, Level.INTERMEDIATE, 40, 60, gym, Tier.FULL,
            conditions = resolve(UserCondition("pregnancy", blockIfYes = true)))).value
        assertTrue(blocked.items.isEmpty() && blocked.followCareProvider == setOf("pregnancy"))
    }

    @Test fun `TC-SAF-010d scheduling, required work and range limits reach the plan`() {
        // Type 2 diabetes: strength on non-consecutive days (3 at most), and no more than 2 inactive days in a row.
        val t2 = resolve(UserCondition("t2d"))
        for (days in 2..5) {
            val p = WeekPlanner.plan(input(t2, days = days), program).value
            val strength = p.days.filter { it.template.strength }.map { it.weekday }.toSet()
            assertTrue("days=$days", strength.size <= 3)
            assertTrue("days=$days $strength", strength.none { ((it + 1) % 7) in strength })
            val active = p.days.filter { it.slots.isNotEmpty() || it.conditioning.isNotEmpty() }.map { it.weekday }.toSet() + p.walkDays
            var run = 0; var worst = 0
            for (k in 0 until 14) { if ((k % 7) in active) run = 0 else { run++; worst = maxOf(worst, run) } }
            assertTrue("days=$days worst=$worst", worst <= 2)
        }
        // Osteoporosis: back-extensor work and balance on 2 days.
        val ost = resolve(UserCondition("osteoporosis"))
        val p = WeekPlanner.plan(input(ost, days = 3), program).value
        assertTrue(p.days.count { d -> d.slots.any { it.exercise.id in WeekPlanner.BACK_EXTENSOR } } >= 2)
        assertEquals(2, p.days.count { it.balanceMinutes > 0 })
        assertTrue(p.days.flatMap { it.slots }.none { it.exercise.limitationTags.any { t -> t in ost.avoidTags } })
        // MOB-004 1.1.0: a range-limited tag (knee osteoarthritis) shortens the range of deep knee bending.
        val oa = resolve(UserCondition("oa_knee"))
        val oaPlan = WeekPlanner.plan(input(oa, days = 3, type = BlockType.BUILD), program).value
        val day = oaPlan.days.first { d -> d.slots.any { "deep_knee_flexion" in it.exercise.limitationTags } }
        val w = SessionGenerator.generate(GenerationRequest(day, Level.INTERMEDIATE, 40, 75, gym, Tier.FULL, conditions = oa,
            week = ValidationContext(Level.INTERMEDIATE))).value
        val deep = w.items.filter { "deep_knee_flexion" in it.exercise.limitationTags }
        assertTrue(deep.isNotEmpty() && deep.all { it.range == RangeOfMotion.REDUCED })
        assertTrue(w.items.filter { "deep_knee_flexion" !in it.exercise.limitationTags && it.exercise.stress(Joint.KNEE) == 0 }.all { it.range == RangeOfMotion.FULL })
        assertNotNull(Conditions["oa_knee"])
    }
}
