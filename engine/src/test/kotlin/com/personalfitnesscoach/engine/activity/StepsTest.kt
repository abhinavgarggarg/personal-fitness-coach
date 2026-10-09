package com.personalfitnesscoach.engine.activity

import com.personalfitnesscoach.engine.core.ReasonKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StepsTest {
    private fun week(start: Int, vararg steps: Int) = steps.mapIndexed { k, s -> DaySteps(start + k, s) }

    @Test fun `TC-STEP-001a baseline is the median of the first 7 valid days and the target starts there, capped by the band`() {
        assertEquals(listOf(9000, 9000, 8000, 7500, 7000), listOf(25, 45, 55, 62, 70).map { StepTarget.bandTarget(it) })
        assertEquals(6000..8000, StepTarget.bandRange(70))
        // Days under 500 steps (phone not carried) don't count; 6 valid days are not enough yet.
        val six = week(0, 4000, 300, 5200, 6100, 4800, 100, 5000, 5600)
        assertNull(StepTarget.baseline(six))
        assertEquals(ReasonKey.STEP_BASELINE_PENDING, StepTarget.start(StepState(), six, 45).decisions.single().reason)
        val seven = six + DaySteps(8, 7000)
        assertEquals(5200, StepTarget.baseline(seven)) // median of 4000, 5200, 6100, 4800, 5000, 5600, 7000
        val s = StepTarget.start(StepState(), seven, 45).value
        assertEquals(5200, s.baseline); assertEquals(5200, s.target)
        // An already-active walker starts at the band target, not above it.
        assertEquals(7000, StepTarget.start(StepState(), week(0, 11000, 12000, 10500, 9800, 13000, 12500, 11800), 70).value.target)
        // Increments: p × mean (0.15 under 50, 0.10 from 50), rounded to 50, between 250 and the band cap.
        assertEquals(800, StepTarget.increment(45, 5300.0))   // 795 → 800
        assertEquals(1000, StepTarget.increment(45, 9000.0))  // 1350 → cap 1000
        assertEquals(250, StepTarget.increment(70, 1500.0))   // 150 → floor 250
        assertEquals(400, StepTarget.increment(70, 6000.0))   // 600 → cap 400 at 65+
        assertEquals(500, StepTarget.increment(62, 6000.0))   // 600 → cap 500 at 60–64
        assertEquals(600, StepTarget.increment(55, 6000.0))   // 600, cap 750
    }

    @Test fun `TC-STEP-001b the target rises after 5 met days, holds at 3-4, drops after 2 poor weeks, never above the band or below the baseline`() {
        val s = StepState(baseline = 5000, target = 6000)
        val good = week(10, 6500, 6200, 7000, 6100, 6300, 4000, 3000) // met on 5 days, mean 5586
        val up = StepTarget.nextWeek(s, good, 45)
        assertEquals(ReasonKey.STEP_TARGET_UP, up.decisions.single().reason)
        assertEquals(6000 + StepTarget.increment(45, good.map { it.steps }.average()), up.value.target)
        // No rise in a deload week or with a lower-limb pain flag.
        assertEquals(6000, StepTarget.nextWeek(s, good, 45, deloadWeek = true).value.target)
        assertEquals(6000, StepTarget.nextWeek(s, good, 45, lowerLimbPain = true).value.target)
        // Never above the band target.
        assertEquals(9000, StepTarget.nextWeek(StepState(5000, 8900), week(0, 9500, 9600, 9700, 9800, 9900, 9100, 9200), 45).value.target)
        // 3–4 days met: hold.
        val ok = week(20, 6100, 6200, 6300, 5000, 5000, 5000, 5000)
        assertEquals(ReasonKey.STEP_TARGET_HELD, StepTarget.nextWeek(s, ok, 45).decisions.single().reason)
        assertEquals(6000, StepTarget.nextWeek(s, ok, 45).value.target)
        // ≤ 2 days met: the first week holds, the second in a row lowers by one increment, not below the baseline.
        val poor = week(30, 6100, 4000, 4200, 4100, 3900, 4000, 4300)
        val w1 = StepTarget.nextWeek(s, poor, 45).value
        assertEquals(6000, w1.target); assertEquals(1, w1.lowWeeks)
        val w2 = StepTarget.nextWeek(w1, poor, 45)
        assertEquals(ReasonKey.STEP_TARGET_DOWN, w2.decisions.single().reason)
        assertEquals(maxOf(5000, 6000 - StepTarget.increment(45, poor.map { it.steps }.average())), w2.value.target)
        assertEquals(0, w2.value.lowWeeks)
        val low = StepTarget.nextWeek(StepTarget.nextWeek(StepState(5800, 6000), poor, 45).value, poor, 45).value
        assertEquals(5800, low.target)
    }

    @Test fun `TC-STEP-002a only bouts of 10+ minutes at 100+ steps a minute count`() {
        fun day(vararg runs: Pair<Int, Int>): List<Int> = runs.flatMap { (minutes, cadence) -> List(minutes) { cadence } }
        assertEquals(12.0, BriskWalks.z1Minutes(day(30 to 0, 12 to 110, 20 to 40)).value, 1e-9)
        assertEquals(0.0, BriskWalks.z1Minutes(day(30 to 0, 9 to 120, 20 to 40)).value, 1e-9)
        assertEquals(0.0, BriskWalks.z1Minutes(day(30 to 0, 15 to 99)).value, 1e-9)
        assertEquals(25.0, BriskWalks.z1Minutes(day(10 to 105, 5 to 20, 15 to 130)).value, 1e-9)
        assertEquals(listOf(0 until 10, 15 until 30), BriskWalks.bouts(day(10 to 105, 5 to 20, 15 to 130)))
        // A bout running to the end of the day still counts.
        assertEquals(11.0, BriskWalks.z1Minutes(day(5 to 0, 11 to 100)).value, 1e-9)
    }

    @Test fun `TC-STEP-002b logged walks count once, short or easy ones don't count`() {
        val steps = List(60) { 0 } + List(20) { 115 } + List(60) { 0 } // a detected bout at minutes 60–79
        // A logged 30-minute walk from minute 70 overlaps the bout by 10 minutes: 20 + 30 − 10 = 40.
        assertEquals(40.0, BriskWalks.z1Minutes(steps, listOf(LoggedWalk(70, 30))).value, 1e-9)
        assertEquals(20.0, BriskWalks.z1Minutes(steps, listOf(LoggedWalk(200, 8))).value, 1e-9)
        assertEquals(20.0, BriskWalks.z1Minutes(steps, listOf(LoggedWalk(200, 30, brisk = false))).value, 1e-9)
        assertEquals(45.0, BriskWalks.z1Minutes(emptyList(), listOf(LoggedWalk(0, 45))).value, 1e-9)
        assertTrue(BriskWalks.z1Minutes(steps).decisions.single().ruleIds.contains("STEP-002"))
    }
}
