package com.personalfitnesscoach.engine.progression

import com.personalfitnesscoach.engine.calc.Inventory
import com.personalfitnesscoach.engine.calc.PlateMath
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.model.CostClass
import com.personalfitnesscoach.engine.model.Exercise
import com.personalfitnesscoach.engine.model.FormCheck
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.LoadType
import com.personalfitnesscoach.engine.model.Muscle
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.model.SetLog
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.registry.RuleIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

private val BAR = PlateMath.loads(LoadType.BARBELL, Inventory())
private val DB = PlateMath.loads(LoadType.DUMBBELL, Inventory())

private val SQUAT = Exercise("squat", "Back squat", Pattern.SQUAT, setOf(Muscle.QUADS, Muscle.GLUTES), setOf(Muscle.ADDUCTORS),
    loadType = LoadType.BARBELL, costClass = CostClass.HEAVY_BILATERAL, defaultRepRange = 5..5, maxExtendedReps = 8, trackE1rm = true)
private val DB_ROW = Exercise("db-row", "One-arm dumbbell row", Pattern.HORIZONTAL_PULL, setOf(Muscle.LATS, Muscle.UPPER_BACK),
    setOf(Muscle.BICEPS), loadType = LoadType.DUMBBELL, costClass = CostClass.FREE_WEIGHT_COMPOUND, defaultRepRange = 8..12)
private val CURL = Exercise("curl", "Dumbbell curl", Pattern.ISOLATION, setOf(Muscle.BICEPS), loadType = LoadType.DUMBBELL,
    costClass = CostClass.ISOLATION_OR_CORE, defaultRepRange = 10..15, maxExtendedReps = 20, failureSafe = true)

private fun sets(load: Double, vararg reps: Int, rir: Double = 2.0, form: FormCheck = FormCheck.YES) =
    reps.map { SetLog(load, it, rir, formOk = form) }

class ProgressionTest {
    private fun squat(rir: Double, vararg reps: Int, recoveryOk: Boolean = true) =
        ExposureInput(SQUAT, 5..5, 2.0, 100.0, sets(100.0, *reps, rir = rir), BAR, recoveryOk)

    @Test fun `TC-PROG-001a Phase 1 example squat 100 x 5 x 3 at RPE 7-5 progresses to 102-5 kg`() {
        val r = Progression.next(squat(2.5, 5, 5, 5))
        assertEquals(102.5, r.value.load, 1e-9)
        assertEquals(5, r.value.repTarget)
        assertEquals(ProgressionAction.LOAD_UP, r.value.action)
        assertTrue(r.decisions.single().ruleIds.containsAll(listOf(RuleIds.PROG_001, RuleIds.PROG_003)))
    }

    @Test fun `TC-PROG-001b no load increase without recovery or when effort ran above target`() {
        assertEquals(ProgressionAction.HOLD, Progression.next(squat(2.5, 5, 5, 5, recoveryOk = false)).value.action)
        val hard = Progression.next(squat(1.0, 5, 5, 5)) // RPE 9 against target 8
        assertEquals(ProgressionAction.HOLD, hard.value.action)
        assertEquals(100.0, hard.value.load, 1e-9)
    }

    @Test fun `TC-PROG-002a Phase 1 example light dumbbell jump 30 to 32-5 kg resets to 13 reps`() {
        val e = ExposureInput(DB_ROW, 8..15, 2.0, 30.0, sets(30.0, 15, 15, 15), DB, true)
        val r = Progression.next(e)
        assertEquals(32.5, r.value.load, 1e-9)
        assertEquals(13, r.value.repTarget)
        assertEquals(ProgressionAction.LOAD_JUMP, r.value.action)
        assertEquals(15, Progression.maxExtendedReps(DB_ROW))
        assertEquals(18, Progression.maxExtendedReps(CURL)) // isolation: default top 15 + 3, under the cap of 20
    }

    @Test fun `TC-PROG-002b reps before load and range extension before a big jump`() {
        val ext = Progression.next(ExposureInput(DB_ROW, 8..12, 2.0, 30.0, sets(30.0, 12, 12, 12), DB, true))
        assertEquals(ProgressionAction.RANGE_EXTENDED, ext.value.action)
        assertEquals(8..13, ext.value.repRange)
        assertEquals(30.0, ext.value.load, 1e-9)
        val reps = Progression.next(ExposureInput(DB_ROW, 8..12, 2.0, 30.0, sets(30.0, 10, 9, 9), DB, true))
        assertEquals(ProgressionAction.REPS_UP, reps.value.action)
        assertEquals(11, reps.value.repTarget)
    }

    @Test fun `TC-PROG-004a effort 1 to 1-5 points above target holds the load`() {
        val r = Progression.next(squat(0.75, 5, 5, 5)) // RPE 9.25 vs 8
        assertEquals(ProgressionAction.HOLD, r.value.action)
        assertEquals(ReasonKey.LOAD_HOLD_EFFORT_HIGH, r.decisions.single().reason)
    }

    @Test fun `TC-PROG-004b too hard reduces 5 percent, a second time 10 percent from the original with review`() {
        val first = Progression.next(squat(0.0, 5, 4, 4))
        assertEquals(ProgressionAction.REDUCE, first.value.action)
        assertEquals(95.0, first.value.load, 1e-9)
        val second = Progression.next(ExposureInput(SQUAT, 5..5, 2.0, 95.0, sets(95.0, 4, 4, 3, rir = 0.0), BAR, true,
            previousWasReduction = true, loadBeforeReductions = 100.0))
        assertEquals(ProgressionAction.REDUCE_AND_REVIEW, second.value.action)
        assertEquals(90.0, second.value.load, 1e-9)
        assertTrue(RuleIds.REG_001 in second.decisions.single().ruleIds)
    }

    @Test fun `TC-PROG-007a weekly intensity cap is 5 percent, 7-5 for beginners`() {
        assertEquals(105.0, ProgressionCaps.maxLoadThisWeek(100.0, Level.INTERMEDIATE, BAR), 1e-9)
        assertEquals(107.5, ProgressionCaps.maxLoadThisWeek(100.0, Level.BEGINNER, BAR), 1e-9)
        val capped = ProgressionCaps.capLoad(110.0, 100.0, Level.ADVANCED, BAR)
        assertEquals(105.0, capped.value, 1e-9)
        assertEquals(ReasonKey.LOAD_CAPPED_WEEKLY, capped.decisions.single().reason)
    }

    @Test fun `TC-PROG-007c an equipment step bigger than the cap is allowed once the e1RM supports it, one step a week`() {
        // Dumbbells in 2.5 kg steps: 20 → 22.5 kg is +12.5 %, above the 5 % cap (D-055).
        assertEquals(20.0, ProgressionCaps.maxLoadThisWeek(20.0, Level.INTERMEDIATE, DB), 1e-9)
        assertEquals(20.0, ProgressionCaps.maxLoadThisWeek(20.0, Level.INTERMEDIATE, DB, supported = 21.0), 1e-9)
        assertEquals(22.5, ProgressionCaps.maxLoadThisWeek(20.0, Level.INTERMEDIATE, DB, supported = 22.5), 1e-9)
        val c = ProgressionCaps.capLoad(25.0, 20.0, Level.INTERMEDIATE, DB)
        assertEquals(22.5, c.value, 1e-9)
        assertTrue(c.decisions.any { it.reason == ReasonKey.LOAD_DISCRETE_STEP })
        assertTrue(c.decisions.any { it.reason == ReasonKey.LOAD_CAPPED_WEEKLY })
        // Fine plate steps keep the plain percentage cap.
        assertEquals(105.0, ProgressionCaps.capLoad(110.0, 100.0, Level.ADVANCED, BAR).value, 1e-9)
    }

    @Test fun `TC-PROG-007b set, aerobic and HIIT caps`() {
        assertEquals(12, ProgressionCaps.maxSetsNextWeek(10))
        assertEquals(115.0, ProgressionCaps.maxAerobicMinutesNextWeek(100.0), 1e-9)
        assertEquals(8.0, ProgressionCaps.maxHiitWorkMinutesNextWeek(6.0), 1e-9)
    }

    @Test fun `TC-PROG-008a unsure or no form holds the load, no adds a coaching cue`() {
        val unsure = Progression.next(ExposureInput(SQUAT, 5..5, 2.0, 100.0, sets(100.0, 5, 5, 5, rir = 3.0, form = FormCheck.UNSURE), BAR, true))
        assertEquals(ProgressionAction.HOLD, unsure.value.action)
        assertEquals(ReasonKey.LOAD_HOLD_TECHNIQUE, unsure.decisions.first().reason)
        val no = Progression.next(ExposureInput(SQUAT, 5..5, 2.0, 100.0, sets(100.0, 5, 5, 5, rir = 3.0, form = FormCheck.NO), BAR, true, consecutiveFormNo = 1))
        assertTrue(no.decisions.any { it.reason == ReasonKey.TECHNIQUE_CUE })
        assertFalse(no.value.offerRegression)
    }

    @Test fun `TC-PROG-008b two no answers in a row offer an easier variation`() {
        val r = Progression.next(ExposureInput(SQUAT, 5..5, 2.0, 100.0, sets(100.0, 5, 5, 5, form = FormCheck.NO), BAR, true, consecutiveFormNo = 2))
        assertTrue(r.value.offerRegression)
        assertTrue(r.decisions.any { it.reason == ReasonKey.REGRESSION_OFFERED })
    }

    @Test fun `TC-PROG-003c lowerLoad always really lowers when a lighter load exists`() {
        assertEquals(27.5, Progression.lowerLoad(28.5, 30.0, DB), 1e-9)
        assertEquals(2.5, Progression.lowerLoad(1.0, 2.5, DB), 1e-9) // nothing lighter: stays
    }

    /** Invariants over 3,000 seeded random exposures. */
    @Test fun `TC-PROG-001c property - loads stay on real equipment and rises stay within the rules`() {
        val rnd = Random(20261007)
        repeat(3000) {
            val ex = listOf(SQUAT, DB_ROW, CURL)[rnd.nextInt(3)]
            val avail = if (ex.loadType == LoadType.BARBELL) BAR else DB
            val load = avail[1 + rnd.nextInt(avail.size - 2)]
            val lo = ex.defaultRepRange.first
            val hi = lo + rnd.nextInt(Progression.maxExtendedReps(ex) - lo + 1)
            val target = 1.0 + rnd.nextInt(3)
            val n = 1 + rnd.nextInt(4)
            val s = List(n) { SetLog(load, maxOf(1, lo - 2 + rnd.nextInt(hi - lo + 4)), rnd.nextInt(6).toDouble(), formOk = FormCheck.entries[if (rnd.nextInt(10) == 0) 1 + rnd.nextInt(2) else 0]) }
            val r = Progression.next(ExposureInput(ex, lo..hi, target, load, s, avail, rnd.nextBoolean())).value
            assertTrue("load must exist", r.load in avail)
            when (r.action) {
                ProgressionAction.LOAD_UP -> assertTrue((r.load - load) / load <= 0.05 + 1e-9)
                ProgressionAction.LOAD_JUMP -> assertTrue("reps reduced on a jump", r.repTarget <= hi && r.load > load)
                ProgressionAction.REDUCE, ProgressionAction.REDUCE_AND_REVIEW -> assertTrue(r.load <= load)
                else -> assertEquals(load, r.load, 1e-9)
            }
            assertTrue(r.repTarget in r.repRange)
            assertTrue(r.repRange.last <= Progression.maxExtendedReps(ex) || r.repRange.last == hi)
        }
    }
}

class AutoregulationTest {
    @Test fun `TC-INT-007a an easy first set with reps hit adds one increment`() {
        val r = Autoregulation.adjust(100.0, 100.0, SetLog(100.0, 5, 4.0), 5, 5, 2.0, BAR, tierMaxLoad = Double.POSITIVE_INFINITY)
        assertEquals(102.5, r.value, 1e-9)
        assertEquals(ReasonKey.INSESSION_LOAD_UP, r.decisions.single().reason)
        // Light dumbbells: the next step (12.5 → 15, +20%) is more than 5%, so nothing changes.
        assertEquals(12.5, Autoregulation.adjust(12.5, 12.5, SetLog(12.5, 12, 5.0), 12, 8, 2.0, DB, tierMaxLoad = Double.POSITIVE_INFINITY).value, 1e-9)
    }

    @Test fun `TC-INT-007b a hard set drops 5 percent and the net change stays within 10 percent`() {
        assertEquals(95.0, Autoregulation.adjust(100.0, 100.0, SetLog(100.0, 5, 0.0), 5, 5, 2.0, BAR, tierMaxLoad = Double.POSITIVE_INFINITY).value, 1e-9)
        assertEquals(95.0, Autoregulation.adjust(100.0, 100.0, SetLog(100.0, 3, 2.0), 5, 5, 2.0, BAR, tierMaxLoad = Double.POSITIVE_INFINITY).value, 1e-9)
        val limit = Autoregulation.adjust(100.0, 90.0, SetLog(90.0, 3, 0.0), 5, 5, 2.0, BAR, tierMaxLoad = Double.POSITIVE_INFINITY)
        assertEquals(90.0, limit.value, 1e-9)
        assertEquals(ReasonKey.INSESSION_LIMIT_REACHED, limit.decisions.single().reason)
        val rnd = Random(7)
        var load = 100.0
        repeat(200) {
            load = Autoregulation.adjust(100.0, load, SetLog(load, 1 + rnd.nextInt(8), rnd.nextInt(6).toDouble()), 5, 5, 2.0, BAR, tierMaxLoad = Double.POSITIVE_INFINITY).value
            assertTrue(load in 90.0..110.0)
        }
    }
}

class CalibrationTest {
    @Test fun `TC-CAL-001a Phase 1 example finds 32-5 kg x 8 at RIR 3 with e1RM 44-4`() {
        val s1 = Calibration.next(20.0, 8, 5.0, 1, BAR).value as CalibrationStep.Continue
        assertEquals(25.0, s1.nextLoad, 1e-9)
        val s2 = Calibration.next(25.0, 8, 5.0, 2, BAR).value as CalibrationStep.Continue
        assertEquals(30.0, s2.nextLoad, 1e-9)
        val s3 = Calibration.next(30.0, 8, 4.0, 3, BAR).value as CalibrationStep.Continue
        assertEquals(32.5, s3.nextLoad, 1e-9)
        val done = Calibration.next(32.5, 8, 3.0, 4, BAR).value as CalibrationStep.Found
        assertEquals(32.5, done.workingLoad, 1e-9)
        assertEquals(44.42, done.startE1rm!!, 0.005)
    }

    @Test fun `TC-CAL-001b too hard stops at minus 5 percent, 5-set limit, known weights start at 90 percent`() {
        val stop = Calibration.next(32.5, 8, 2.0, 2, BAR).value as CalibrationStep.Stop
        assertEquals(30.0, stop.nextSessionLoad, 1e-9)
        val capped = Calibration.next(40.0, 8, 5.0, 5, BAR)
        assertTrue(capped.value is CalibrationStep.Found)
        assertEquals(ReasonKey.CALIBRATION_CAPPED, capped.decisions.single().reason)
        assertEquals(52.5, Calibration.startFromKnown(60.0, BAR), 1e-9)
        assertEquals(20.0, Calibration.startingLoad(BAR))
        // Property: a ramp never adds more than 30% (+ one rounding step) and never continues after RIR ≤ 2.
        val rnd = Random(11)
        repeat(1000) {
            val load = BAR[rnd.nextInt(BAR.size - 1)]
            val rir = rnd.nextInt(7).toDouble()
            val step = Calibration.next(load, 8, rir, 1 + rnd.nextInt(5), BAR).value
            if (rir <= 2.0) assertTrue(step is CalibrationStep.Stop)
            if (step is CalibrationStep.Continue) assertTrue(step.nextLoad > load && step.nextLoad <= load * 1.30 + 2.5)
        }
    }
}

class ReturnToTrainingTest {
    @Test fun `TC-REG-001a three failures within 3 weeks trigger a review`() {
        val r = ReturnToTraining.failedTargets(listOf(1, 2, 3), 3)
        assertTrue(r.value)
        assertTrue(r.decisions.single().ruleIds.contains(RuleIds.DEL_001))
    }

    @Test fun `TC-REG-001b failures spread over more than 3 weeks do not`() {
        assertFalse(ReturnToTraining.failedTargets(listOf(0, 2, 3), 3).value)
    }

    @Test fun `TC-REG-002a one or two missed sessions just shift the plan`() {
        val r = ReturnToTraining.afterBreak(daysOff = 5, consecutiveMissed = 2)
        assertEquals(ReasonKey.MISSED_SHIFTED, r.decisions.single().reason)
        assertEquals(ReturnPlan(), r.value)
    }

    @Test fun `TC-REG-002b the plan never doubles up`() {
        for (missed in 0..2) assertTrue(ReturnToTraining.afterBreak(4, missed).value.setsFactor <= 1.0)
    }

    @Test fun `TC-REG-003a 7 to 13 days off - first session MODIFIED at 95 percent`() {
        val r = ReturnToTraining.afterBreak(daysOff = 10, consecutiveMissed = 3)
        assertEquals(Tier.MODIFIED, r.value.tierCap)
        assertEquals(0.95, r.value.loadFactor, 1e-9)
    }

    @Test fun `TC-REG-003b three missed sessions also count, and only the first session back is modified`() {
        assertEquals(Tier.MODIFIED, ReturnToTraining.afterBreak(6, 3).value.tierCap)
        assertEquals(ReturnPlan(), ReturnToTraining.afterBreak(10, 3, sessionsSinceReturn = 1).value)
    }

    @Test fun `TC-REG-004a 14 to 27 and 28 to 55 day ramps`() {
        fun p(days: Int, week: Int) = ReturnToTraining.afterBreak(days, 0, weeksSinceReturn = week).value
        assertEquals(listOf(0.9, 0.7, 0.0), p(20, 0).let { listOf(it.loadFactor, it.setsFactor, if (it.hiitAllowed) 1.0 else 0.0) })
        assertEquals(listOf(0.95, 0.85, 1.0), p(20, 1).let { listOf(it.loadFactor, it.setsFactor, if (it.hiitAllowed) 1.0 else 0.0) })
        assertEquals(ReturnPlan(), p(20, 2))
        assertEquals(0.8, p(40, 0).loadFactor, 1e-9); assertEquals(0.6, p(40, 0).setsFactor, 1e-9); assertFalse(p(40, 1).hiitAllowed)
        assertEquals(0.85, p(40, 1).loadFactor, 1e-9); assertEquals(0.75, p(40, 1).setsFactor, 1e-9)
        assertEquals(0.9, p(40, 2).loadFactor, 1e-9); assertTrue(p(40, 2).hiitAllowed)
        assertEquals(1.0, p(40, 3).setsFactor, 1e-9) // 60 + 3 × 15 = 105 → capped at 100
    }

    @Test fun `TC-REG-004b 56 days recalibrates, age 60 or a flagged screen uses the next band`() {
        assertTrue(ReturnToTraining.afterBreak(60, 0).value.recalibrate)
        assertEquals(0.8, ReturnToTraining.afterBreak(20, 0, age = 62).value.loadFactor, 1e-9)
        assertTrue(ReturnToTraining.afterBreak(40, 0, flaggedScreen = true).value.recalibrate)
        assertEquals(ReasonKey.RECALIBRATE, ReturnToTraining.afterBreak(90, 0).decisions.single().reason)
    }

    @Test fun `TC-REG-005a no training until 24 h symptom-free without fever medicine, then LIGHT, MODIFIED, normal`() {
        assertEquals(ReasonKey.ILLNESS_REST, ReturnToTraining.afterIllness(12, false, 0).decisions.single().reason)
        assertEquals(ReasonKey.ILLNESS_REST, ReturnToTraining.afterIllness(72, true, 0).decisions.single().reason)
        assertEquals(Tier.LIGHT, ReturnToTraining.afterIllness(30, false, 0).value.tierCap)
        assertEquals(Tier.MODIFIED, ReturnToTraining.afterIllness(30, false, 1).value.tierCap)
        assertNull(ReturnToTraining.afterIllness(30, false, 2).value.tierCap)
        assertFalse(ReturnToTraining.afterIllness(30, false, 3).value.hiitAllowed)
        assertTrue(ReturnToTraining.afterIllness(30, false, 4).value.hiitAllowed)
    }

    @Test fun `TC-REG-005b a mild head cold without fever allows LIGHT or MODIFIED`() {
        val r = ReturnToTraining.afterIllness(0, false, 0, mildHeadColdOnly = true)
        assertEquals(Tier.MODIFIED, r.value.tierCap)
        assertFalse(r.value.hiitAllowed)
    }
}

class WarmupTest {
    @Test fun `TC-WU-002a Phase 1 example squat 100 x 5 ramps 20, 50, 70, 85`() {
        val r = Warmup.rampSets(100.0, 5, null, LoadType.BARBELL, BAR).value
        assertEquals(listOf(20.0, 50.0, 70.0, 85.0), r.map { it.load })
        assertEquals(listOf(8..10, 5..5, 3..3, 1..2), r.map { it.reps })
    }

    @Test fun `TC-WU-002b no 85 percent step for lighter work, single set near the bar, second lift ramp`() {
        assertEquals(listOf(20.0, 50.0, 70.0), Warmup.rampSets(100.0, 10, 140.0, LoadType.BARBELL, BAR).value.map { it.load })
        assertEquals(listOf(20.0, 50.0, 70.0, 85.0), Warmup.rampSets(100.0, 8, 120.0, LoadType.BARBELL, BAR).value.map { it.load }) // ≥80% e1RM
        assertEquals(listOf(WarmupSet(20.0, 8..8)), Warmup.rampSets(27.5, 8, null, LoadType.BARBELL, BAR).value)
        assertEquals(listOf(47.5, 62.5), Warmup.rampSets(80.0, 8, null, LoadType.BARBELL, BAR, firstMainLift = false).value.map { it.load })
        assertTrue(Warmup.rampSets(0.0, 10, null, LoadType.BODYWEIGHT, emptyList()).value.isEmpty())
        val d = Warmup.rampSets(30.0, 5, null, LoadType.DUMBBELL, DB).value.map { it.load }
        assertEquals(listOf(15.0, 20.0, 25.0), d) // no empty-bar step for dumbbells
    }

    @Test fun `TC-WU-001a warm-up is 8 to 12 minutes`() {
        assertEquals(8.0, Warmup.minutes(6.0, false, null).value, 1e-9)
        assertEquals(12.0, Warmup.minutes(15.0, false, null).value, 1e-9)
    }

    @Test fun `TC-WU-001b planned length inside the range is kept`() {
        assertEquals(10.0, Warmup.minutes(10.0, false, 30).value, 1e-9)
    }

    @Test fun `TC-WU-003a compressed warm-up keeps a 5-minute floor and at most 3 ramp sets`() {
        assertEquals(5.0, Warmup.minutes(10.0, true, null).value, 1e-9)
        val ramp = Warmup.rampSets(100.0, 5, null, LoadType.BARBELL, BAR).value
        assertEquals(listOf(50.0, 70.0, 85.0), Warmup.compressRamp(ramp).map { it.load })
    }

    @Test fun `TC-WU-003b the warm-up is never removed`() {
        for (age in listOf(null, 25, 55, 70)) assertTrue(Warmup.floorMinutes(age) >= 5.0)
    }

    @Test fun `TC-WU-004a age 50 plus adds 2 minutes of raise`() {
        assertEquals(12.0, Warmup.minutes(10.0, false, 55).value, 1e-9)
        assertTrue(Warmup.minutes(10.0, false, 55).decisions.single().ruleIds.contains(RuleIds.WU_004))
    }

    @Test fun `TC-WU-004b the age extra survives compression`() {
        assertEquals(7.0, Warmup.floorMinutes(60), 1e-9)
        assertEquals(5.0, Warmup.floorMinutes(49), 1e-9)
    }
}
