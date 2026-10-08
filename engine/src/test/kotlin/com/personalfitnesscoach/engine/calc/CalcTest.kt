package com.personalfitnesscoach.engine.calc

import com.personalfitnesscoach.engine.model.CostClass
import com.personalfitnesscoach.engine.model.Exercise
import com.personalfitnesscoach.engine.model.LoadType
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.model.Muscle
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.model.SetLog
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.model.Zone
import com.personalfitnesscoach.engine.registry.Registry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EffortTest {
    @Test fun `TC-INT-001a RIR is 10 minus RPE inside 6 to 10`() {
        assertEquals(2.0, Effort.rirFromRpe(8.0)!!, 1e-9)
        assertEquals(4.0, Effort.rirFromRpe(6.0)!!, 1e-9)
        assertEquals(0.0, Effort.rirFromRpe(9.5)!!, 1e-9)
        assertEquals(0.0, Effort.rirFromRpe(10.0)!!, 1e-9)
    }

    @Test fun `TC-INT-001b RPE below 6 is not used`() {
        assertNull(Effort.rirFromRpe(5.5))
        assertNull(Effort.rirFromRpe(10.5))
    }

    @Test fun `TC-VOL-001a hard set is RIR 4 or less`() {
        assertTrue(Effort.isHardSet(SetLog(100.0, 5, rir = 4.0)))
        assertTrue(Effort.isHardSet(SetLog(100.0, 5, rir = null)))
    }

    @Test fun `TC-VOL-001b warm-ups and easy sets do not count`() {
        assertFalse(Effort.isHardSet(SetLog(100.0, 5, rir = 5.0)))
        assertFalse(Effort.isHardSet(SetLog(60.0, 5, rir = 2.0, warmup = true)))
    }
}

class E1rmTest {
    @Test fun `TC-INT-004a e1RM from 60 kg x 8 at RIR 2 is 80 kg (Phase 1 worked example)`() {
        assertEquals(80.0, E1rm.fromSet(60.0, 8, 2.0)!!, 1e-9)
    }

    @Test fun `TC-INT-004b sets outside the valid range do not update e1RM`() {
        assertNull("reps + RIR > 12", E1rm.fromSet(50.0, 15, 2.0))
        assertNull("RIR > 3", E1rm.fromSet(50.0, 5, 4.0))
        assertNull("unrated", E1rm.fromSet(50.0, 5, null))
    }

    @Test fun `TC-INT-004c smoothing moves 40 percent and a single rise is capped at 5 percent`() {
        assertEquals(104.0, E1rm.update(100.0, 110.0).value!!, 1e-9) // 100 + 0.4 × 10
        assertEquals(105.0, E1rm.update(100.0, 130.0).value!!, 1e-9) // 112 capped at +5%
        assertEquals(96.0, E1rm.update(100.0, 90.0).value!!, 1e-9)  // drops are not capped
        assertEquals(80.0, E1rm.update(null, 80.0).value!!, 1e-9)
    }

    @Test fun `TC-INT-005a 80 kg e1RM for 5 at RIR 2 prescribes 65 kg on a 2-point-5 kg barbell`() {
        val loads = PlateMath.loads(LoadType.BARBELL, Inventory())
        val r = E1rm.prescribe(80.0, 5, 2.0, loads)
        assertEquals(65.0, r.value, 1e-9) // raw 64.86 → 65 is within the 2% tolerance
        assertTrue(r.decisions.single().ruleIds.containsAll(listOf("INT-005", "PROG-003")))
    }

    @Test fun `TC-INT-005b load formula is the inverse of the e1RM formula`() {
        val e = E1rm.fromSet(100.0, 5, 2.0)!!
        assertEquals(100.0, E1rm.loadFor(e, 5, 2.0), 1e-9)
    }
}

class PlateMathTest {
    private val bar = PlateMath.loads(LoadType.BARBELL, Inventory())

    @Test fun `TC-PROG-003a barbell loads come in 2-point-5 kg steps from the empty bar`() {
        assertEquals(20.0, bar.first(), 1e-9)
        assertTrue(bar.contains(62.5) && bar.contains(65.0) && bar.contains(102.5))
        assertFalse(bar.contains(63.75))
    }

    @Test fun `TC-PROG-003b choose prefers the closest load not more than 2 percent above target`() {
        assertEquals(65.0, PlateMath.choose(64.86, bar), 1e-9)
        assertEquals(32.5, PlateMath.choose(33.75, bar), 1e-9) // 35 is >2% above → excluded
        assertEquals(20.0, PlateMath.choose(10.0, bar), 1e-9)  // below the bar → lightest load
    }

    @Test fun `TC-PROG-003c dumbbell and stack loads follow the inventory`() {
        val inv = Inventory(dumbbells = listOf(10.0, 12.5, 15.0), stack = Stack(5.0, 50.0, 7.5))
        assertEquals(listOf(10.0, 12.5, 15.0), PlateMath.loads(LoadType.DUMBBELL, inv))
        assertEquals(listOf(5.0, 12.5, 20.0, 27.5, 35.0, 42.5, 50.0), PlateMath.loads(LoadType.STACK, inv))
        assertEquals(12.5, PlateMath.nextAbove(10.0, PlateMath.loads(LoadType.DUMBBELL, inv))!!, 1e-9)
        assertNull(PlateMath.nextAbove(15.0, PlateMath.loads(LoadType.DUMBBELL, inv)))
    }
}

class VolumeTest {
    private val bench = Exercise("bench", "Bench press", Pattern.HORIZONTAL_PUSH, setOf(Muscle.CHEST),
        setOf(Muscle.TRICEPS, Muscle.FRONT_DELTS), loadType = LoadType.BARBELL, costClass = CostClass.FREE_WEIGHT_COMPOUND)
    private val pushdown = Exercise("pushdown", "Triceps pushdown", Pattern.ISOLATION, setOf(Muscle.TRICEPS),
        loadType = LoadType.STACK, costClass = CostClass.ISOLATION_OR_CORE)

    @Test fun `TC-VOL-002a primary muscles get 1 and secondary 0-point-5 per set`() {
        val c = Volume.credit(bench, 3.0)
        assertEquals(3.0, c[Muscle.CHEST]!!, 1e-9)
        assertEquals(1.5, c[Muscle.TRICEPS]!!, 1e-9)
        assertNull(c[Muscle.LATS])
    }

    @Test fun `TC-VOL-002b weekly credit sums across exercises`() {
        val w = Volume.weekly(listOf(bench to 3.0, pushdown to 3.0, bench to 2.0))
        assertEquals(5.0, w[Muscle.CHEST]!!, 1e-9)
        assertEquals(1.5 + 3.0 + 1.0, w[Muscle.TRICEPS]!!, 1e-9)
    }

    @Test fun `TC-VOL-003a landmarks match the registry by level`() {
        assertEquals(listOf(12, 16, 20), Level.entries.map { Volume.weeklyCap(it) })
        assertEquals(listOf(6, 8, 10), Level.entries.map { Volume.blockStart(it) })
        assertEquals(listOf(4, 4, 6), Level.entries.map { Volume.maintenance(it) })
    }

    @Test fun `TC-VOL-003b the next-week target never exceeds the cap`() {
        assertEquals(12, Volume.nextWeekTarget(Level.BEGINNER, 12, progressed = true, fatigueActive = false))
        assertEquals(16, Volume.nextWeekTarget(Level.INTERMEDIATE, 15, progressed = true, fatigueActive = false))
    }

    @Test fun `TC-VOL-004a fatigue cuts accessory sets by 30 percent, at least 1`() {
        assertEquals(2, Volume.cutAccessories(3))
        assertEquals(1, Volume.cutAccessories(1))
        assertEquals(7, Volume.cutAccessories(10))
    }

    @Test fun `TC-VOL-004b no volume increase while fatigue is active`() {
        assertEquals(10, Volume.nextWeekTarget(Level.INTERMEDIATE, 10, progressed = true, fatigueActive = true))
    }

    @Test fun `TC-VOL-005a per-session caps are 6, 8 and 10 direct sets`() {
        assertEquals(listOf(6, 8, 10), Level.entries.map { Volume.perSessionCap(it) })
    }

    @Test fun `TC-VOL-005b direct per-session sets count only primary muscles`() {
        val d = Volume.directPerSession(listOf(bench to 4.0, pushdown to 3.0))
        assertEquals(4.0, d[Muscle.CHEST]!!, 1e-9)
        assertEquals(3.0, d[Muscle.TRICEPS]!!, 1e-9)
    }

    @Test fun `TC-VOL-006a progression adds 1 set for beginners and up to 2 otherwise`() {
        assertEquals(7, Volume.nextWeekTarget(Level.BEGINNER, 6, progressed = true, fatigueActive = false))
        assertEquals(10, Volume.nextWeekTarget(Level.INTERMEDIATE, 8, progressed = true, fatigueActive = false))
    }

    @Test fun `TC-VOL-006b no progress means no added sets`() {
        assertEquals(8, Volume.nextWeekTarget(Level.INTERMEDIATE, 8, progressed = false, fatigueActive = false))
    }

    @Test fun `TC-VOL-008a session caps are 20, 25 and 30 working sets`() {
        assertEquals(listOf(20, 25, 30), Level.entries.map { Volume.sessionSetCap(it) })
    }

    @Test fun `TC-VOL-008b registry version is embedded for traceability`() {
        assertEquals("1.0.1", Registry.VERSION)
        assertEquals(140, Registry.RULE_COUNT)
    }
}

class SsuTest {
    @Test fun `TC-VOL-007a Phase 1 worked example totals 25-point-0 SSU`() {
        val sets = Ssu.sets(
            List(6) { CostClass.FREE_WEIGHT_COMPOUND to 2.0 } + List(2) { CostClass.HEAVY_BILATERAL to 2.0 } +
                List(4) { CostClass.MACHINE_OR_CABLE_COMPOUND to 2.0 } + List(6) { CostClass.ISOLATION_OR_CORE to 1.0 } +
                List(3) { CostClass.ISOLATION_OR_CORE to 3.0 },
        )
        val cond = Ssu.conditioning(Modality.ROWER, Zone.Z2, 12.0)
        assertEquals(25.04, sets + cond, 1e-9)
    }

    @Test fun `TC-VOL-007b effort factors and weekly limits`() {
        assertEquals(1.2, Ssu.effort(0.0), 1e-9)
        assertEquals(0.8, Ssu.effort(3.0), 1e-9)
        assertEquals(0.0, Ssu.effort(5.0), 1e-9)
        assertEquals(140.0, Ssu.weeklyLimit(Level.INTERMEDIATE, listOf(100.0, 110.0, 120.0), weeksOfHistory = 2), 1e-9)
        assertEquals(126.5, Ssu.weeklyLimit(Level.INTERMEDIATE, listOf(100.0, 110.0, 120.0), weeksOfHistory = 6), 1e-9)
    }
}

class ReadinessTest {
    @Test fun `TC-RDY-001a all-normal ratings score 50`() {
        assertEquals(50.0, Readiness.raw(CheckIn(3, 3, 3, 3)), 1e-9)
        assertEquals(100.0, Readiness.raw(CheckIn(5, 5, 5, 5)), 1e-9)
        assertEquals(0.0, Readiness.raw(CheckIn(1, 1, 1, 1)), 1e-9)
    }

    @Test fun `TC-RDY-001b weights sleep 0-30 energy 0-30 soreness 0-25 stress 0-15`() {
        assertEquals(36.25, Readiness.raw(CheckIn(2, 3, 2, 3)), 1e-9)
        assertEquals(42.5, Readiness.raw(CheckIn(2, 3, 3, 3)), 1e-9)
    }

    @Test fun `TC-RDY-002a Phase 1 worked example blends to 29-75`() {
        val b = Baseline(58.0, 9.0, 20)
        assertEquals(29.75, Readiness.blended(36.25, b), 1e-9)
    }

    @Test fun `TC-RDY-002b no baseline before 14 check-ins and a habitual low rater is judged against herself`() {
        assertNull(Baseline.of(List(13) { 40.0 }))
        val low = Baseline(35.0, 8.0, 20)
        assertEquals(41.0, Readiness.blended(35.0, low), 1e-9) // 0.6×35 + 0.4×50
        val usual = CheckIn(2, 3, 3, 1) // raw 35: her normal day
        assertEquals(Tier.MODIFIED, Readiness.tier(usual, null, 0).value.tier)
        assertEquals(Tier.FULL, Readiness.tier(usual, low, 0).value.tier)
    }

    @Test fun `TC-RDY-003a tier bands 40, 28 and 15`() {
        assertEquals(Tier.FULL, Readiness.band(40.0))
        assertEquals(Tier.MODIFIED, Readiness.band(39.9))
        assertEquals(Tier.MODIFIED, Readiness.band(28.0))
        assertEquals(Tier.LIGHT, Readiness.band(15.0))
        assertEquals(Tier.RECOVERY, Readiness.band(14.9))
    }

    @Test fun `TC-RDY-003b a rating of 1 for sleep caps the day at MODIFIED`() {
        val r = Readiness.tier(CheckIn(1, 5, 5, 5), null, 0)
        assertEquals(Tier.MODIFIED, r.value.tier)
        assertTrue(r.decisions.any { "RDY-003" in it.ruleIds })
    }

    @Test fun `TC-RDY-004a worked example is MODIFIED`() {
        val r = Readiness.tier(CheckIn(2, 3, 2, 3, sleepHours = 4.5), Baseline(58.0, 9.0, 20), 0)
        assertEquals(29.75, r.value.r, 1e-9)
        assertEquals(Tier.MODIFIED, r.value.tier)
    }

    @Test fun `TC-RDY-004b raw floor below 15 caps at LIGHT even with a low baseline`() {
        val r = Readiness.tier(CheckIn(1, 1, 2, 2), Baseline(0.0, 5.0, 30), 0)
        assertEquals(10.0, r.value.rRaw, 1e-9)
        assertEquals(33.5, r.value.r, 1e-9) // blended score alone would allow MODIFIED
        assertEquals(Tier.LIGHT, r.value.tier)
        assertTrue(r.decisions.any { it.reason == com.personalfitnesscoach.engine.core.ReasonKey.TIER_FLOOR_RAW_LOW })
    }

    @Test fun `TC-RDY-005a under 4 hours caps at LIGHT`() {
        assertEquals(Tier.LIGHT, Readiness.tier(CheckIn(4, 4, 4, 4, sleepHours = 3.5), null, 0).value.tier)
    }

    @Test fun `TC-RDY-005b 4 to 5 hours caps at MODIFIED and 5 or more does not cap`() {
        assertEquals(Tier.MODIFIED, Readiness.tier(CheckIn(4, 4, 4, 4, sleepHours = 4.0), null, 0).value.tier)
        assertEquals(Tier.FULL, Readiness.tier(CheckIn(4, 4, 4, 4, sleepHours = 5.0), null, 0).value.tier)
    }

    @Test fun `TC-RDY-006a two fatigue signals step FULL down to MODIFIED`() {
        assertEquals(Tier.MODIFIED, Readiness.tier(CheckIn(4, 4, 4, 4), null, 2).value.tier)
    }

    @Test fun `TC-RDY-006b signals never push below LIGHT`() {
        assertEquals(Tier.LIGHT, Readiness.tier(CheckIn(2, 2, 2, 2), null, 3).value.tier)
        assertEquals(Tier.RECOVERY, Readiness.tier(CheckIn(1, 1, 1, 2), null, 3).value.tier)
    }

    @Test fun `TC-RDY-007a an easier tier is always allowed`() {
        assertEquals(Tier.LIGHT, Readiness.userChoice(Tier.FULL, Tier.LIGHT, lockedBySafety = true).value)
    }

    @Test fun `TC-RDY-007b harder by one step only and never when locked`() {
        assertEquals(Tier.FULL, Readiness.userChoice(Tier.MODIFIED, Tier.FULL, lockedBySafety = false).value)
        assertEquals(Tier.LIGHT, Readiness.userChoice(Tier.LIGHT, Tier.FULL, lockedBySafety = false).value)
        assertEquals(Tier.MODIFIED, Readiness.userChoice(Tier.MODIFIED, Tier.FULL, lockedBySafety = true).value)
    }
}

class WorkloadTest {
    private val normal = listOf(400.0, 0.0, 400.0, 0.0, 400.0, 0.0, 400.0)
    private val spike = listOf(650.0, 0.0, 650.0, 0.0, 650.0, 500.0, 650.0)

    @Test fun `TC-LOAD-001a session load is RPE times minutes`() {
        assertEquals(420.0, Workload.sessionLoad(7.0, 60.0), 1e-9)
    }

    @Test fun `TC-LOAD-001b session RPE outside 0 to 10 is rejected`() {
        try { Workload.sessionLoad(11.0, 60.0); throw AssertionError("expected rejection") } catch (e: IllegalArgumentException) { }
    }

    @Test fun `TC-LOAD-003a weekly load, monotony and strain use the sample SD`() {
        assertEquals(1600.0, Workload.weekly(normal), 1e-9)
        assertEquals(1.07, Workload.monotony(normal)!!, 0.005)
        assertEquals(1710.0, Workload.strain(normal)!!, 1.0)
    }

    @Test fun `TC-LOAD-003b identical daily loads give no monotony value instead of infinity`() {
        assertNull(Workload.monotony(List(7) { 300.0 }))
    }

    @Test fun `TC-LOAD-004a Phase 1 worked example EWMA ratio is 1-41 on day 42`() {
        val e = Workload.ewma(List(5) { normal }.flatten() + spike)!!
        assertEquals(460.0, e.acute, 0.5)
        assertEquals(326.0, e.chronic, 0.5)
        assertEquals(1.41, e.ratio!!, 0.005)
    }

    @Test fun `TC-LOAD-004b no ratio flag before 28 days of data`() {
        val short = listOf(100.0) + List(10) { 900.0 }
        assertTrue(Workload.flags(short).none { "LOAD-004" in it.ruleIds })
    }

    @Test fun `TC-LOAD-005a planning cap is 1-2 times the 3-week mean after 4 weeks`() {
        assertEquals(1920.0, Workload.planningCap(listOf(1600.0, 1600.0, 1600.0, 1600.0, 1600.0), 35)!!, 1e-9)
    }

    @Test fun `TC-LOAD-005b early weeks may rise at most 20 percent`() {
        assertEquals(1440.0, Workload.planningCap(listOf(1200.0), 14)!!, 1e-9)
    }

    @Test fun `TC-LOAD-006a under 14 days there is no planning cap`() {
        assertNull(Workload.planningCap(listOf(1200.0), 10))
    }

    @Test fun `TC-LOAD-006b with no completed week there is no planning cap`() {
        assertNull(Workload.planningCap(emptyList(), 20))
    }
}

class FatigueTest {
    @Test fun `TC-DEL-001a declining main lifts and effort creep are detected`() {
        val s = FatigueSignals.active(FatigueInputs(
            lifts = listOf(LiftTrend("squat", 120.0, listOf(115.0, 114.0)), LiftTrend("bench", 90.0, listOf(86.0, 85.0))),
            sessionRpeOverPlan = listOf(1.5, 2.0, 0.5, 1.6),
        ))
        assertTrue(Signal.F1_PERFORMANCE in s)
        assertTrue(Signal.F2_EFFORT_CREEP in s)
    }

    @Test fun `TC-DEL-001b readiness, soreness, workload and self-report signals`() {
        val s = FatigueSignals.active(FatigueInputs(
            readinessMean7d = 40.0, baseline = Baseline(55.0, 8.0, 20),
            sorenessRatings = listOf(2, 3, 2, 4, 1), ewmaRatio = 1.6, runDownTaps7d = 2,
        ))
        assertEquals(setOf(Signal.F3_READINESS, Signal.F4_SORENESS, Signal.F5_WORKLOAD, Signal.F6_SELF_REPORT), s)
        assertTrue(FatigueSignals.active(FatigueInputs()).isEmpty())
    }

    @Test fun `TC-DEL-002a three signals mean deload now, two for two sessions a lighter week`() {
        assertEquals(DeloadAction.DELOAD_NOW, Deload.decide(3, 0, false, 2, justFinishedLighterWeek = false).value)
        assertEquals(DeloadAction.LIGHTER_WEEK, Deload.decide(2, 2, false, 2, justFinishedLighterWeek = false).value)
        assertEquals(DeloadAction.NONE, Deload.decide(2, 1, false, 2, justFinishedLighterWeek = false).value)
    }

    @Test fun `TC-DEL-002b block end and the 10-week offer, and 2 signals deload at age 50 plus`() {
        assertEquals(DeloadAction.DELOAD_AT_BLOCK_END, Deload.decide(1, 0, true, 3, justFinishedLighterWeek = false).value)
        assertEquals(DeloadAction.PIVOT_WEEK, Deload.decide(0, 0, true, 3, justFinishedLighterWeek = false).value)
        assertEquals(DeloadAction.OFFER_LIGHTER_WEEK, Deload.decide(0, 0, false, 10, justFinishedLighterWeek = false).value)
        assertEquals(DeloadAction.DELOAD_NOW, Deload.decide(2, 0, false, 2, age = 55, justFinishedLighterWeek = false).value)
    }

    @Test fun `TC-DEL-003a deload halves sets and keeps loads at 85 to 90 percent`() {
        val p = Deload.prescription()
        assertEquals(0.5, p.setsFactor, 1e-9)
        assertEquals(0.85, p.loadFactorLow, 1e-9)
        assertEquals(0.90, p.loadFactorHigh, 1e-9)
    }

    @Test fun `TC-DEL-003b deload has no HIIT and RIR at least 3`() {
        val p = Deload.prescription()
        assertFalse(p.hiitAllowed)
        assertEquals(3, p.minRir)
        assertEquals(7, p.days)
    }

    @Test fun `TC-DEL-004a resume at 100 percent when recovered`() {
        assertEquals(1.0, Deload.resumeLoadFactor(0), 1e-9)
    }

    @Test fun `TC-DEL-004b resume at 95 percent if a signal remains`() {
        assertEquals(0.95, Deload.resumeLoadFactor(1), 1e-9)
    }
}
