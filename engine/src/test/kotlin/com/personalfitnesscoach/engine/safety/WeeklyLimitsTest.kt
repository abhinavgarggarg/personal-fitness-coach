package com.personalfitnesscoach.engine.safety

import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.model.CostClass
import com.personalfitnesscoach.engine.model.Exercise
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.LoadType
import com.personalfitnesscoach.engine.model.Muscle
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.model.Zone
import com.personalfitnesscoach.engine.program.Blueprint
import com.personalfitnesscoach.engine.program.Goal
import com.personalfitnesscoach.engine.program.SlotRole
import com.personalfitnesscoach.engine.program.WeekPlanner
import com.personalfitnesscoach.engine.program.weekInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4 Part 1 (D-110), proposal for Registry 2.0.0: the weekly limits' reference weeks, the week planned within the limits, the floor
 * and the spike guard. The year-long simulations that chose them are `YearOfTrainingTest` (data layer).
 */
class WeeklyLimitsTest {
    private fun ex(id: String, m: Muscle) = Exercise(id, id, Pattern.HORIZONTAL_PULL, setOf(m), loadType = LoadType.STACK, costClass = CostClass.MACHINE_OR_CABLE_COMPOUND)

    /** Six exercises of 4 sets at RIR 2: 6 × 4 × 0.8 = 19.2 SSU. */
    private val big = Session(Tier.FULL, List(6) { SessionExercise(ex("x$it", Muscle.entries[it]), 4, 10, 2.0) })

    @Test fun `TC-VOL-007h reference weeks count at the level of the sessions done and leave out weeks cut by more than half`() {
        val weeks = listOf(
            WeeklyLimits.Week(planned = 3, done = 3, ssu = 30.0, load = 900.0),
            WeeklyLimits.Week(planned = 3, done = 2, ssu = 20.0, load = 600.0),   // one missed: counts as 3 sessions at that level
            WeeklyLimits.Week(planned = 3, done = 1, ssu = 10.0, load = 300.0),   // cut by more than half: left out, like a missed week
            WeeklyLimits.Week(planned = 4, done = 2, ssu = 20.0, load = 500.0),   // exactly half: kept
            WeeklyLimits.Week(planned = 3, done = 4, ssu = 40.0, load = 1200.0),  // more than planned: as it was, never scaled down
            WeeklyLimits.Week(planned = 3, done = 0, ssu = 0.0, load = 0.0),
        )
        val ref = WeeklyLimits.reference(weeks)
        assertEquals(listOf(30.0, 30.0, 40.0, 40.0), ref.map { it.ssu })
        assertEquals(listOf(900.0, 900.0, 1000.0, 1200.0), ref.map { it.load })
        // The load-to-stress ratio (VOL-007's k) is unchanged by the scaling.
        assertEquals(weeks[1].load / weeks[1].ssu, ref[1].load / ref[1].ssu, 1e-9)
    }

    @Test fun `TC-VOL-007i the week is planned within its limit, conditioning included, and never below half of its plan`() {
        val program = Blueprint.plan(listOf(Goal.STRENGTH, Goal.MUSCLE, Goal.GENERAL_FITNESS)).value
        val input = weekInput(3, 6, program = program)
        val open = WeekPlanner.plan(input, program).value
        val full = open.days.sumOf { SessionValidator.sessionSsu(it.toSession()) }
        assertNull("no limit, nothing cut", open.uncappedSsu)
        assertEquals(full, open.plannedSsu, 1e-9)

        // A limit at 85% of the plan: the week fits, it says why, and every day keeps its first main lift at 2 sets or more.
        val r = WeekPlanner.plan(input.copy(weeklySsuLimit = 0.85 * full), program)
        val fitted = r.value
        val ssu = fitted.days.sumOf { SessionValidator.sessionSsu(it.toSession()) }
        assertTrue("within the limit ($ssu of ${0.85 * full})", ssu <= 0.85 * full + 1e-9)
        assertEquals(full, fitted.plannedSsu, 1e-9)
        assertTrue(r.decisions.any { it.reason == ReasonKey.WEEK_WITHIN_LIMIT && it.outputs["fits"] == true })
        for (day in fitted.days.filter { it.slots.any { s -> s.spec.role == SlotRole.MAIN } })
            assertTrue("${day.template}: main lift kept", day.slots.first { it.spec.role == SlotRole.MAIN }.sets >= 2)
        // The other exercises' sets go before main lifts (TIME-001 order): here they are enough.
        val mainSets = { p: List<com.personalfitnesscoach.engine.program.PlannedDay> -> p.sumOf { d -> d.slots.filter { it.spec.role == SlotRole.MAIN }.sumOf { it.sets } } }
        assertEquals(mainSets(open.days), mainSets(fitted.days))

        // A limit far below: never below half of the plan (the floor, about a deload week).
        val low = WeekPlanner.plan(input.copy(weeklySsuLimit = 0.1 * full), program).value
        val lowSsu = low.days.sumOf { SessionValidator.sessionSsu(it.toSession()) }
        // Never below the floor (one set's worth of rounding); the planner's own floors may stop it above it — the session check then
        // applies the limits to each day.
        assertTrue("not below the floor ($lowSsu)", lowSsu >= 0.5 * full - 1.5)
        assertTrue("cut as far as the plan allows ($lowSsu)", lowSsu < 0.85 * full)
        // The spike guard holds the week down too, but not below the floor.
        val guarded = WeekPlanner.plan(input.copy(weeklySsuLimit = full, weeklySsuGuard = 0.7 * full), program).value
        assertTrue(guarded.days.sumOf { SessionValidator.sessionSsu(it.toSession()) } <= 0.7 * full + 1e-9)
        val belowFloor = WeekPlanner.plan(input.copy(weeklySsuLimit = full, weeklySsuGuard = 0.2 * full), program).value
        assertTrue(belowFloor.days.sumOf { SessionValidator.sessionSsu(it.toSession()) } >= 0.5 * full - 1.5)
    }

    @Test fun `TC-VOL-007j the limits never cut a week below its floor, and the floor never passes the level ceiling`() {
        // 5 SSU left by the limit, but a floor of 12: the session keeps 12.
        val c = ValidationContext(Level.INTERMEDIATE, weeklySsuLimit = 5.0, weekFloorSsu = 12.0)
        assertEquals(12.0, SessionValidator.weeklySsuLimit(c), 1e-9)
        val out = SessionValidator.sessionSsu(SessionValidator.validate(big, c).value.session)
        assertTrue("kept to the floor ($out)", out <= 12.0 + 1e-9 && out > 12.0 - 0.8 - 1e-9)
        // A limit above the floor is the limit.
        assertEquals(15.0, SessionValidator.weeklySsuLimit(c.copy(weeklySsuLimit = 15.0)), 1e-9)
        // Never above VOL-007's level ceiling (140 SSU for an intermediate).
        assertEquals(140.0, SessionValidator.weeklySsuLimit(c.copy(weekFloorSsu = 500.0)), 1e-9)
        // The spike guard: twice the mean of the last three weeks as done; none without training in them.
        assertEquals(40.0, WeeklyLimits.spikeGuard(listOf(10.0, 20.0, 30.0))!!, 1e-9)
        assertNull(WeeklyLimits.spikeGuard(listOf(0.0, 0.0, 0.0)))
        assertNull(WeeklyLimits.spikeGuard(listOf(10.0, 20.0)))
    }

    @Test fun `TC-LOAD-005k LOAD-005 never cuts the week below the floor, whatever the loads measured so far`() {
        // The week's load cap is used up already (sessions that felt hard), but 5 SSU done is under the floor of 15: 10 more are allowed.
        val c = ValidationContext(Level.INTERMEDIATE, k = 20.0, weeklyLoadCap = 1000.0, weekLoadSoFar = 1000.0, weekSsuSoFar = 5.0, weekFloorSsu = 15.0)
        val out = SessionValidator.sessionSsu(SessionValidator.validate(big, c).value.session)
        assertTrue("up to the floor ($out)", out <= 10.0 + 1e-9 && out > 10.0 - 0.8 - 1e-9)
        // Without the floor, nothing is left (as before).
        val none = SessionValidator.validate(big, c.copy(weekFloorSsu = null)).value.session
        assertTrue(none.exercises.isEmpty())
        // Above the floor the cap applies as usual: 5 SSU left by the cap, floor 3 → 5.
        val usual = SessionValidator.sessionSsu(SessionValidator.validate(big, c.copy(weekLoadSoFar = 900.0, weekSsuSoFar = 0.0, weekFloorSsu = 3.0)).value.session)
        assertTrue("cap applies ($usual)", usual <= 5.0 + 1e-9 && usual > 5.0 - 0.8 - 1e-9)
    }

    @Test fun `TC-LOAD-005f(2) hard conditioning made easy keeps its minutes where the limit allows`() {
        // 20 min Z2 rower (14.4 SSU) and 6 SSU left: Z1 (0.36 a minute) for 16.5 min rather than nothing.
        val s = Session(Tier.FULL, emptyList(), listOf(ConditioningBlock(com.personalfitnesscoach.engine.model.Modality.ROWER, Zone.Z2, 20.0)))
        val b = SessionValidator.validate(s, ValidationContext(Level.INTERMEDIATE, weeklySsuLimit = 6.0)).value.session.conditioning.single()
        assertEquals(Zone.Z1, b.zone)
        assertEquals(16.5, b.workMinutes, 1e-9)
    }
}
