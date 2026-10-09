package com.personalfitnesscoach.engine.program

import com.personalfitnesscoach.engine.conditioning.Aerobic
import com.personalfitnesscoach.engine.conditioning.AerobicBlock
import com.personalfitnesscoach.engine.conditioning.ModalityExclusions
import com.personalfitnesscoach.engine.conditioning.ModalitySelection
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.model.Zone
import com.personalfitnesscoach.engine.registry.RuleIds
import com.personalfitnesscoach.engine.safety.ConditionLimits
import com.personalfitnesscoach.engine.safety.HiitProtocol
import com.personalfitnesscoach.engine.safety.ImpactLevel
import com.personalfitnesscoach.engine.calc.Volume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** FL-001 to FL-004 and the Registry 1.1 changes to AGE-001, CON-006 and PH-001. */
class FatLossTest {
    private val fatLoss = Blueprint.plan(listOf(Goal.FAT_LOSS)).value
    private val gym = FULL_GYM + setOf("treadmill", "stationary_bike", "air_bike", "stair_climber")

    private fun weekOf(program: Program, type: BlockType, inBlock: Int = 2): Int = (1..52).first { w ->
        val c = Blueprint.context(program, w)
        c.block.type == type && c.kind == WeekKind.LOADING && c.weekInBlock == minOf(inBlock, c.block.loadingWeeks)
    }

    private fun input(age: Int, days: Int, type: BlockType = BlockType.BUILD, level: Level = Level.INTERMEDIATE, weeks: Int = 40,
                      lastEq: Double = 180.0, optIn: Boolean = false, conditions: ConditionLimits = ConditionLimits.NONE, injuries: Set<Joint> = emptySet(),
                      program: Program = fatLoss) =
        WeekInput(level, weeks, days, gym, Blueprint.context(program, weekOf(program, type)), age = age, sessionMinutes = 60, hiitBaseReady = true,
            hiitDoneEver = 4, priorities = listOf(Goal.FAT_LOSS), lastWeekEquivalentMinutes = lastEq, lastWeekAerobicMinutes = 120.0,
            lastWeekHiitWorkMinutes = 20.0, hiitOptIn = optIn, conditions = conditions, injuries = injuries)

    private fun plan(i: WeekInput) = WeekPlanner.plan(i, fatLoss).value

    // ------------------------------------------------------------------ FL-001

    @Test fun `TC-FL-001a lose fat keep muscle is the default from 30, every goal stays selectable, with a realistic expectation`() {
        val o = FatLoss.options(GoalContext(age = 35)).value
        assertEquals(Goal.FAT_LOSS, o.default)
        assertEquals(Goal.entries.toSet(), o.offered.toSet())
        assertTrue(o.weightFeatures)
        assertEquals(Goal.GENERAL_FITNESS, FatLoss.options(GoalContext(age = 25)).value.default)
        assertTrue(Goal.FAT_LOSS in FatLoss.options(GoalContext(age = 25)).value.offered)
        val e = FatLoss.expectation(true)!!
        assertEquals(-3..-2, e.weightKg); assertEquals(-5..-2, e.waistCm); assertEquals(150..300, e.aerobicMinutes)
        // Weight features off: no expectation, and the default becomes general fitness.
        val off = FatLoss.options(GoalContext(age = 45, weightFeaturesOff = true)).value
        assertEquals(Goal.GENERAL_FITNESS, off.default); assertFalse(off.weightFeatures); assertNull(FatLoss.expectation(off.weightFeatures))
        // MOD-001 2.0.0: treadmill running starts switched off for the fat-loss goal.
        assertEquals(setOf(Modality.TREADMILL_RUN), ModalityExclusions.defaults(fatLossGoal = true))
    }

    @Test fun `TC-FL-001b pregnancy and cancer in treatment remove the goal and weight features, after birth it returns from 12 weeks but not as default`() {
        for (c in listOf(GoalContext(35, pregnant = true), GoalContext(50, cancerActiveTreatment = true))) {
            val o = FatLoss.options(c).value
            assertFalse(Goal.FAT_LOSS in o.offered); assertFalse(o.weightFeatures); assertEquals(Goal.GENERAL_FITNESS, o.default)
        }
        val early = FatLoss.options(GoalContext(32, postpartumWeeks = 8)).value
        assertFalse(Goal.FAT_LOSS in early.offered)
        val later = FatLoss.options(GoalContext(32, postpartumWeeks = 20)).value
        assertTrue(Goal.FAT_LOSS in later.offered); assertEquals(Goal.GENERAL_FITNESS, later.default); assertTrue(later.weightFeatures)
        assertTrue(FatLoss.options(GoalContext(32, pregnant = true)).decisions.all { RuleIds.FL_001 in it.ruleIds })
    }

    // ------------------------------------------------------------------ FL-002

    @Test fun `TC-FL-002a the weekly target by age grows at most 15 percent toward the band and starts at the 150 floor`() {
        assertEquals(200..300, FatLoss.targetRange(45)); assertEquals(180..250, FatLoss.targetRange(62)); assertEquals(150..250, FatLoss.targetRange(70))
        assertEquals(150.0, FatLoss.weeklyTarget(45, 0.0).value.thisWeek, 1e-9)
        assertEquals(150.0, FatLoss.weeklyTarget(45, 100.0).value.thisWeek, 1e-9) // never below the floor (review R3-12)
        assertEquals(150.0, FatLoss.weeklyTarget(45, 50.0).value.thisWeek, 1e-9)
        assertEquals(172.5, FatLoss.weeklyTarget(45, 150.0).value.thisWeek, 1e-9)
        assertEquals(200.0, FatLoss.weeklyTarget(45, 180.0).value.thisWeek, 1e-9)
        assertEquals(200.0, FatLoss.weeklyTarget(45, 260.0).value.thisWeek, 1e-9) // holds at the band's lower bound
        // The plan meets this week's target: gym work first, brisk walks for the rest.
        val p = plan(input(45, 3, lastEq = 150.0))
        val a = p.activity!!
        assertEquals(172.5, a.target.thisWeek, 1e-9)
        assertTrue(a.plannedEquivalent <= 172.5 + 1e-9 || a.walk.minutesPerWeek == 0.0)
        assertEquals(a.plannedEquivalent, a.gymZ1Minutes + a.walk.minutesPerWeek + 2 * (a.z2Minutes + a.hiitWorkMinutes), 0.15)
    }

    @Test fun `TC-FL-002b 65 plus grows 12 percent a week, walks fill the gap within the Z1 range and PH-001 counts them`() {
        assertEquals(150.0, FatLoss.weeklyTarget(70, 150.0).value.thisWeek, 1e-9) // 65+: the band's lower bound is the floor
        assertEquals(150.0, FatLoss.weeklyTarget(70, 100.0).value.thisWeek, 1e-9)
        assertEquals(12, FatLoss.weeklyTarget(70, 100.0).value.growthPct)
        val r = WeekPlanner.plan(input(45, 3, lastEq = 250.0), fatLoss)
        val a = r.value.activity!!
        assertEquals(200.0, a.target.thisWeek, 1e-9)
        assertEquals(200.0, a.plannedEquivalent, 0.15)
        assertTrue(a.walk.minutesPerWeek > 0 && a.walk.minutesEach in 10..45 && a.walk.walks in 1..7)
        assertTrue(a.gymZ1Minutes + a.walk.minutesPerWeek <= 200.0 + 1e-9) // FL-003 Z1 range top
        assertTrue(r.decisions.any { it.reason == ReasonKey.WALKS_PLANNED && RuleIds.STEP_002 in it.ruleIds })
        // The walks close the WHO floor, so PH-001 reports no shortfall.
        assertTrue(r.decisions.none { it.reason == ReasonKey.WHO_FLOOR_SHORT })
    }

    // ------------------------------------------------------------------ FL-003

    @Test fun `TC-FL-003a the mix changes with age - fewer intervals, low impact from 50, power and balance from 50`() {
        fun m(age: Int, optIn: Boolean = false) = FatLoss.mix(age, Level.INTERMEDIATE, 4, 40, conditioningBlock = false, hiitOptIn = optIn).value
        assertEquals(listOf(2, 1, 1, 0, 0), listOf(35, 45, 55, 62, 70).map { m(it).hiit })
        assertEquals(listOf(20, 20, 15, 0, 0), listOf(35, 45, 55, 62, 70).map { m(it).z2Minutes })
        assertEquals(listOf(false, false, true, true, true), listOf(35, 45, 55, 62, 70).map { m(it).lowImpactOnly })
        assertEquals(listOf(false, false, true, true, true), listOf(35, 45, 55, 62, 70).map { m(it).powerSlot })
        assertEquals(listOf(0, 0, 10, 20, 30), listOf(35, 45, 55, 62, 70).map { m(it).balanceMinutesWeek })
        assertTrue(listOf(35, 45, 55, 62, 70).all { m(it).strengthDays == 3 && m(it).z1Minutes == 150..200 })
        assertFalse(m(55).sprints)
        // 60+: intervals are offered once the base is there, planned only when accepted.
        assertTrue(m(62).hiitOffered); assertEquals(1, m(62, optIn = true).hiit); assertFalse(m(62, optIn = true).hiitOffered)
        // 65+ also needs to have been active before (not a beginner).
        assertFalse(FatLoss.mix(70, Level.BEGINNER, 3, 40, false).value.hiitOffered)
        // No intervals before the band's base weeks.
        assertEquals(0, FatLoss.mix(35, Level.INTERMEDIATE, 4, 3, false).value.hiit)
        // A conditioning block adds one, up to the band's maximum (HIIT-001 conditions apply in the planner).
        assertEquals(3, FatLoss.mix(35, Level.INTERMEDIATE, 4, 40, conditioningBlock = true).value.hiit)
        assertEquals(2, FatLoss.mix(55, Level.INTERMEDIATE, 4, 40, conditioningBlock = true).value.hiit)
        // Beginners don't get the extra session (HIIT-001's third session needs intermediate or advanced).
        assertEquals(2, FatLoss.mix(35, Level.BEGINNER, 4, 40, conditioningBlock = true).value.hiit)
    }

    @Test fun `TC-FL-003b the planner keeps 3 strength days, low-impact intervals from 50 and balance work, and the HIIT cap`() {
        for (days in 3..6) {
            val p = plan(input(55, days))
            assertEquals("days=$days", 3, p.days.count { it.template.strength })
            for (b in p.days.flatMap { it.conditioning }.filter { it.hiit }) assertTrue(b.modality.name, b.modality in FatLoss.LOW_IMPACT_MODALITIES)
            assertTrue(p.days.flatMap { it.slots }.filter { it.power }.all { it.exercise.impact == 0 })
            assertTrue(p.days.flatMap { it.conditioning }.none { it.protocol == HiitProtocol.SPRINT || it.zone == Zone.Z4 })
            assertTrue(p.days.flatMap { it.conditioning }.none { ModalitySelection.isImpact(it.modality) })
        }
        assertEquals(2, plan(input(45, 2)).days.count { it.template.strength })
        // Balance: 65+ three days of 10 minutes; with only 2 training days the third is a home session.
        val old = plan(input(70, 3))
        assertEquals(3, old.days.count { it.balanceMinutes == 10.0 })
        val old2 = plan(input(70, 2))
        assertEquals(2, old2.days.count { it.balanceMinutes > 0 }); assertEquals(1, old2.activity!!.homeBalanceSessions)
        assertEquals(2, plan(input(62, 4)).days.count { it.balanceMinutes > 0 })
        assertEquals(0, plan(input(35, 4)).days.count { it.balanceMinutes > 0 })
        // 60+ and 65+: no intervals unless accepted; the offer is visible.
        val p62 = plan(input(62, 4))
        assertTrue(p62.days.flatMap { it.conditioning }.none { it.hiit }); assertTrue(p62.activity!!.hiitOffered)
        assertTrue(plan(input(62, 4, optIn = true)).days.flatMap { it.conditioning }.count { it.hiit } in 0..2)
        // Living with obesity at 35: no jumping power work or impact cardio, intervals on low-impact machines only.
        val ob = plan(input(35, 4, conditions = ConditionLimits(obesity = true)))
        assertTrue(ob.days.flatMap { it.slots }.none { it.exercise.impact > 0 })
        assertTrue(ob.days.flatMap { it.conditioning }.filter { it.hiit }.all { it.modality in FatLoss.LOW_IMPACT_MODALITIES })
        // Knee pain at 35 also switches impact off.
        assertTrue(plan(input(35, 4, injuries = setOf(Joint.KNEE))).days.flatMap { it.slots }.none { it.exercise.impact > 0 })
    }

    // ------------------------------------------------------------------ FL-004

    @Test fun `TC-FL-004a the fat-loss year follows FL-004 and every block keeps at least 2 strength days`() {
        val seq = fatLoss.blocks.map { it.type }
        assertEquals(listOf(BlockType.CALIBRATE, BlockType.FOUNDATION, BlockType.BUILD, BlockType.CONDITIONING, BlockType.STRENGTH, BlockType.BUILD_2,
            BlockType.CONDITIONING_2, BlockType.ATHLETIC_LOW_IMPACT, BlockType.CONSOLIDATION, BlockType.REVIEW), seq)
        // PER-006: fat loss as #1 lengthens the conditioning blocks.
        assertTrue(fatLoss.blocks.filter { it.type.isConditioning }.all { it.loadingWeeks == 6 })
        assertTrue(fatLoss.totalWeeks <= 52)
        for (t in listOf(BlockType.FOUNDATION, BlockType.BUILD, BlockType.CONDITIONING, BlockType.STRENGTH, BlockType.BUILD_2, BlockType.CONDITIONING_2,
            BlockType.ATHLETIC_LOW_IMPACT, BlockType.CONSOLIDATION)) for (days in 2..6) {
            val p = plan(input(45, days, type = t))
            assertTrue("$t days=$days", p.days.count { d -> d.slots.isNotEmpty() } >= 2)
        }
        assertTrue(Blueprint.plan(listOf(Goal.FAT_LOSS)).decisions.any { RuleIds.FL_004 in it.ruleIds })
    }

    @Test fun `TC-FL-004b other goals keep the PER-003 year, the new blocks reuse their doses and the athletic block is low-impact`() {
        val general = Blueprint.plan(listOf(Goal.GENERAL_FITNESS)).value.blocks.map { it.type }
        assertTrue(BlockType.STRENGTH_2 in general && BlockType.POWER_ATHLETICISM in general && BlockType.CONDITIONING_2 !in general)
        for (l in Level.entries) {
            assertEquals(Blueprint.dose(BlockType.CONDITIONING, l), Blueprint.dose(BlockType.CONDITIONING_2, l))
            assertEquals(Blueprint.dose(BlockType.POWER_ATHLETICISM, l), Blueprint.dose(BlockType.ATHLETIC_LOW_IMPACT, l))
        }
        // Athletic block: power exercises lifted fast, never jumped, and no impact cardio, even at 35.
        for (days in 3..5) {
            val p = plan(input(35, days, type = BlockType.ATHLETIC_LOW_IMPACT))
            assertTrue(p.days.flatMap { it.slots }.filter { it.power }.isNotEmpty())
            assertTrue(p.days.flatMap { it.slots }.all { it.exercise.impact == 0 })
            assertTrue(p.days.flatMap { it.conditioning }.none { ModalitySelection.isImpact(it.modality) })
        }
    }

    // ------------------------------------------------------------------ AGE-001 1.1.0, CON-006 1.1.0, PH-001 1.1.0

    @Test fun `TC-AGE-001c from 60 conditioning and review weeks keep block-start volume`() {
        val start = Volume.blockStart(Level.INTERMEDIATE)
        assertTrue(Blueprint.dose(BlockType.CONDITIONING, Level.INTERMEDIATE).setsStart < start)
        assertEquals(start, Blueprint.dose(BlockType.CONDITIONING, Level.INTERMEDIATE, age = 62).setsStart)
        assertEquals(start, Blueprint.dose(BlockType.REVIEW, Level.INTERMEDIATE, age = 62).setsStart)
        assertEquals(Blueprint.dose(BlockType.CONDITIONING, Level.INTERMEDIATE), Blueprint.dose(BlockType.CONDITIONING, Level.INTERMEDIATE, age = 59))
        // The review week at 62 is light (RIR ≥ 3) but not cut to half.
        val general = Blueprint.plan(listOf(Goal.GENERAL_FITNESS)).value
        val review = (1..53).first { Blueprint.context(general, it).block.type == BlockType.REVIEW }
        fun reviewPlan(age: Int) = WeekPlanner.plan(WeekInput(Level.INTERMEDIATE, 40, 3, FULL_GYM, Blueprint.context(general, review), age = age), general)
        val young = reviewPlan(45).value; val older = reviewPlan(62)
        assertTrue(young.deload); assertFalse(older.value.deload)
        assertTrue(older.value.days.sumOf { it.workingSets } > young.days.sumOf { it.workingSets })
        assertTrue(older.value.days.flatMap { it.slots }.all { it.targetRir >= 3.0 })
        assertTrue(older.decisions.any { it.reason == ReasonKey.AGE_VOLUME_FLOOR })
    }

    @Test fun `TC-CON-006c for the fat-loss goal brisk walks are outside the strength-block cap`() {
        val r = WeekPlanner.plan(input(40, 3, type = BlockType.STRENGTH, lastEq = 260.0), fatLoss)
        val a = r.value.activity!!
        assertTrue(a.walk.minutesPerWeek > 0)
        assertTrue(Aerobic.strengthBlockOk(r.value.aerobic))
        assertTrue(r.decisions.none { it.reason == ReasonKey.AEROBIC_CHECK_FAILED && RuleIds.CON_006 in it.ruleIds })
        assertTrue(r.value.aerobic.sumOf { it.workMinutes } + a.walk.minutesPerWeek > 0)
    }

    @Test fun `TC-PH-001c brisk walks count as Z1 in the WHO accounting, with no double count`() {
        val gym = listOf(AerobicBlock(Zone.Z1, 40.0, 0), AerobicBlock(Zone.Z3, 10.0, 2))
        assertEquals(60.0, Aerobic.whoEquivalentMinutes(gym), 1e-9)
        assertEquals(150.0, Aerobic.whoEquivalentMinutes(gym, walkingMinutes = 90.0), 1e-9)
        assertTrue(Aerobic.whoCheck(gym, 3, walkingMinutes = 90.0).value.meetsAerobic)
        assertFalse(Aerobic.whoCheck(gym, 3, walkingMinutes = 89.0).value.meetsAerobic)
        assertEquals(1.0, Aerobic.whoCheck(gym, 3, walkingMinutes = 89.0).value.suggestedWalkingMinutes, 1e-9)
    }

    @Test fun `walk plans are 10 to 45 minute bouts that cover the minutes`() {
        assertEquals(0, FatLoss.walkPlan(0.0).walks)
        for (m in listOf(15.0, 60.0, 119.5, 175.0, 250.0)) {
            val w = FatLoss.walkPlan(m)
            assertTrue("$m", w.walks in 1..7 && w.minutesEach in 10..45)
            assertTrue("$m", w.walks * w.minutesEach >= m - 1e-9)
        }
        assertNotNull(FatLoss.mix(35, Level.BEGINNER, 3, 0, false).value)
    }
}
