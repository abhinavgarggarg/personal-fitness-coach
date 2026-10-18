package com.personalfitnesscoach.data.core.engine

import com.personalfitnesscoach.data.core.Fixtures
import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.model.WeekSummary
import com.personalfitnesscoach.data.core.session.NewSet
import com.personalfitnesscoach.data.core.session.SetKind
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.engine.calc.CheckIn
import com.personalfitnesscoach.engine.calc.Ssu
import com.personalfitnesscoach.engine.calc.Workload
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.safety.SessionValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The week-so-far inputs of the weekly limits (VOL-007, LOAD-005) as the data layer builds them (Part 8, D-107). */
class WeekBudgetTest {

    /** Generates, does and finishes the session planned for today: calibration finds the planned weight at once, working sets as planned. */
    private suspend fun doToday(d: PfcData, rpe: Double = 7.0, minutes: Double = 50.0): Long? {
        val t = checkNotNull(d.planToday())
        val next = t.next ?: return null
        if (next.weekday != Days.weekday(t.user.today)) return null
        val ci = d.checkIns.record(t.user, CheckIn(4, 4, 3, 4))
        val w = d.generate(t, next, ci.record.tier).value
        val id = d.sessions.start(w, next.template, next.weekday, inDeload = t.week.deload)
        val stored = checkNotNull(d.sessions.load(id))
        for ((i, e) in stored.exercises.withIndex()) {
            val item = w.items[i]
            if (item.calibrating && item.load != null) {
                d.sessions.logSet(e.row.id, NewSet(0, SetKind.CALIBRATION, item.load, item.reps.first, rir = 2.0), "{}")
            } else repeat(item.sets) { n ->
                val set = if (item.unit == DoseUnit.SECONDS) NewSet(n, SetKind.WORKING, item.load, null, item.reps.last, rir = item.targetRir)
                    else NewSet(n, SetKind.WORKING, item.load, item.reps.last, rir = item.targetRir)
                d.sessions.logSet(e.row.id, set, "{}")
            }
        }
        d.finishWorkout(id, sessionRpe = rpe, actualMinutes = minutes, conditioningDoneMinutes = w.conditioning.map { it.workMinutes })
        return id
    }

    @Test fun `TC-VOL-007e each session's share is its planned SSU over what is still owed this week`() = runBlocking {
        val (d, clock) = Fixtures.data()
        Fixtures.onboard(d)
        val t = checkNotNull(d.planToday())
        val days = t.week.days
        assertEquals(3, days.size)
        fun ssu(i: Int) = SessionValidator.sessionSsu(days[i].toSession())
        fun day(i: Int) = Fixtures.MONDAY + days[i].weekday
        // Monday: all three sessions are still to do.
        assertEquals(ssu(0) / (ssu(0) + ssu(1) + ssu(2)), d.bridge.weekShare(t.week, days[0], day(0)), 1e-9)
        // Monday missed: on Wednesday it can still be caught up, so it keeps its part (SCH-003, review R8-08).
        assertEquals(ssu(1) / (ssu(0) + ssu(1) + ssu(2)), d.bridge.weekShare(t.week, days[1], day(1)), 1e-9)
        // Monday done: Wednesday shares only with Friday; Friday, the last, may use all that is left.
        clock.setDay(day(0), hour = 9)
        assertTrue(doToday(d) != null)
        assertEquals(ssu(1) / (ssu(1) + ssu(2)), d.bridge.weekShare(t.week, days[1], day(1)), 1e-9)
        // On Sunday nothing else can follow: the whole remainder, whatever is still owed.
        assertEquals(1.0, d.bridge.weekShare(t.week, days[1], Fixtures.MONDAY + 6), 1e-9)
        // A session outside the plan gets no share.
        assertEquals(1.0, d.bridge.weekShare(t.week, null, Fixtures.MONDAY), 1e-9)
    }

    @Test fun `TC-VOL-007f a weight being found counts as planned - two sets at the target effort, whatever the ramp`() = runBlocking {
        val (d, clock) = Fixtures.data()
        Fixtures.onboard(d)
        clock.setDay(Fixtures.MONDAY, hour = 9)
        val t = checkNotNull(d.planToday())
        val next = checkNotNull(t.next)
        val w = d.generate(t, next, d.checkIns.record(t.user, CheckIn(4, 4, 3, 4)).record.tier).value
        val i = w.items.indexOfFirst { it.calibrating && it.load != null }
        assertTrue("the first week calibrates", i >= 0)
        val id = d.sessions.start(w, next.template, next.weekday, inDeload = false)
        val row = checkNotNull(d.sessions.load(id)).exercises[i].row.id
        val load = w.items[i].load!!
        listOf(5.0, 3.0, 2.0).forEachIndexed { n, rir -> d.sessions.logSet(row, NewSet(n, SetKind.CALIBRATION, load + 5.0 * n, 8, rir = rir), "{}") }
        val stored = checkNotNull(d.sessions.load(id))
        // Two sets at the target effort, as the plan counts it (D-110): the easy ramp set no longer makes the week look light.
        val planned = SessionValidator.sessionSsu(com.personalfitnesscoach.engine.safety.Session(com.personalfitnesscoach.engine.model.Tier.FULL,
            listOf(com.personalfitnesscoach.engine.safety.SessionExercise(w.items[i].exercise, 3, 8, w.items[i].targetRir, calibrating = true))))
        assertEquals(planned, d.bridge.calibrationSsu(stored), 1e-9)
        // One set logged (the ramp stopped at once) counts one.
        val (e, eclock) = Fixtures.data()
        Fixtures.onboard(e)
        eclock.setDay(Fixtures.MONDAY, hour = 9)
        val et = checkNotNull(e.planToday())
        val en = checkNotNull(et.next)
        val ew = e.generate(et, en, e.checkIns.record(et.user, CheckIn(4, 4, 3, 4)).record.tier).value
        val eid = e.sessions.start(ew, en.template, en.weekday, inDeload = false)
        val erow = checkNotNull(e.sessions.load(eid)).exercises[i].row.id
        e.sessions.logSet(erow, NewSet(0, SetKind.CALIBRATION, load, 8, rir = 2.0), "{}")
        assertEquals(planned / 2, e.bridge.calibrationSsu(checkNotNull(e.sessions.load(eid))), 1e-9)
    }

    @Test fun `TC-LOAD-005h once k is learned each session done counts as k times its SSU or its measured load, whichever is higher`() = runBlocking {
        val (d, clock) = Fixtures.data()
        Fixtures.onboard(d)
        // Five weeks of training (k needs 4 weeks with both a load and SSU), then Monday of week 6.
        for (day in Fixtures.MONDAY until Fixtures.MONDAY + 35) { clock.setDay(day, hour = 9); doToday(d) }
        val monday6 = Fixtures.MONDAY + 35
        clock.setDay(monday6, hour = 9)
        // A long, hard Monday: its measured load is far from k × its SSU.
        assertTrue(doToday(d, rpe = 9.0, minutes = 120.0) != null)
        clock.setDay(monday6 + 2, hour = 9)
        val t = checkNotNull(d.planToday())
        val c = d.bridge.validationContext(t.user, t.program, t.week, t.next)
        val k = checkNotNull(c.k) { "k learned after 4 weeks" }
        val soFar = d.bridge.totals(Days.weekStart(monday6), monday6 + 2)
        assertTrue(soFar.ssu > 0.0)
        // The hard Monday counts at its measured load, which is more than k × its SSU (review R8-02): never less room than measured.
        assertTrue(soFar.workload > k * soFar.ssu)
        assertEquals(soFar.workload, c.weekLoadSoFar, 1e-6)
        assertTrue("the cap is still set by measured loads", c.weeklyLoadCap != null)
        // An easy, short Monday counts as k × its SSU, so a day lighter than predicted never makes room for more.
        val (e, eclock) = Fixtures.data()
        Fixtures.onboard(e)
        for (day in Fixtures.MONDAY until Fixtures.MONDAY + 35) { eclock.setDay(day, hour = 9); doToday(e) }
        eclock.setDay(monday6, hour = 9)
        assertTrue(doToday(e, rpe = 2.0, minutes = 15.0) != null)
        eclock.setDay(monday6 + 2, hour = 9)
        val et = checkNotNull(e.planToday())
        val ec = e.bridge.validationContext(et.user, et.program, et.week, et.next)
        val esoFar = e.bridge.totals(Days.weekStart(monday6), monday6 + 2)
        assertTrue(esoFar.workload < ec.k!! * esoFar.ssu)
        assertEquals(ec.k!! * esoFar.ssu, ec.weekLoadSoFar, 1e-6)
        assertNotEquals(esoFar.workload, ec.weekLoadSoFar, 1.0)
    }

    @Test fun `TC-LOAD-005j the limits come from reference weeks - a week cut short counts at its sessions' level, one cut by more than half is left out`() = runBlocking {
        val (d, clock) = Fixtures.data()
        Fixtures.onboard(d)
        // Five weeks of real training, so the load cap, k and the spike guard all apply (independent review: without history they were null).
        for (day in Fixtures.MONDAY until Fixtures.MONDAY + 35) { clock.setDay(day, hour = 9); doToday(d) }
        val monday = Fixtures.MONDAY + 35
        clock.setDay(monday, hour = 9)
        // Opening the app closes the past weeks (it writes their summaries), so the stored weeks are set after it.
        val t = checkNotNull(d.planToday())
        // Five finished weeks before this one, oldest first: all 3 sessions, 2 of 3, 1 of 3 (left out), all 3, 2 of 3.
        listOf(Triple(3, 30.0, 900.0), Triple(2, 20.0, 600.0), Triple(1, 10.0, 300.0), Triple(3, 30.0, 900.0), Triple(2, 20.0, 600.0))
            .forEachIndexed { i, (done, ssu, load) -> d.docs.put(WeekSummary, WeekSummary(Fixtures.MONDAY + 7 * i, i + 1, 3, done, ssu = ssu, workload = load)) }
        val refs = d.bridge.referenceWeeks(monday).map { it.second }
        assertEquals(listOf(30.0, 30.0, 30.0, 30.0), refs.map { it.ssu })
        assertEquals(listOf(900.0, 900.0, 900.0, 900.0), refs.map { it.load })
        val lim = d.bridge.weekLimits(t.user, t.program, deload = false)
        // VOL-007: 1.15 × 30 = 34.5, not 1.15 × 23.3 from the weeks as done; k (load ÷ SSU) is the same either way.
        assertEquals(34.5, lim.ssuLimit, 1e-9)
        assertEquals(30.0, lim.k!!, 1e-9)
        // LOAD-005 through the bridge, on the same reference weeks: 1.20 × 900, so 36 SSU at k = 30.
        assertEquals(1080.0, lim.loadCap!!, 1e-9)
        assertEquals(36.0, lim.loadSsu!!, 1e-9)
        // The spike guard: twice the last three weeks as done (10, 30 and 20 SSU) = 40.
        assertEquals(40.0, lim.guard!!, 1e-9)
        // The session check: the floor is half of this week's plan, never above the level ceiling nor the guard; the limit is at least the
        // floor and never above the guard.
        val c = d.bridge.validationContext(t.user, t.program, t.week, t.next)
        assertEquals(minOf(0.5 * t.week.plannedSsu, Ssu.weeklyCeiling(t.user.profile.level).toDouble(), 40.0), c.weekFloorSsu!!, 1e-9)
        assertEquals(com.personalfitnesscoach.engine.safety.WeeklyLimits.bounded(34.5, c.weekFloorSsu!!, 40.0), c.weeklySsuLimit!!, 1e-9)
        assertTrue(c.weeklySsuLimit!! >= c.weekFloorSsu!! - 1e-9 && c.weeklySsuLimit!! <= 40.0 + 1e-9)
        assertEquals(1080.0, Workload.planningCap(refs.map { it.load }, 40)!!, 1e-9)
    }

    @Test fun `TC-VOL-007m the spike guard holds after a lighter week, and is off only in a deload, a return or the first three weeks`() = runBlocking {
        val (d, clock) = Fixtures.data()
        Fixtures.onboard(d)
        for (day in Fixtures.MONDAY until Fixtures.MONDAY + 35) {
            clock.setDay(day, hour = 9)
            if (day == Fixtures.MONDAY + 14) {
                // Day 14 of the programme: too early for the guard (its three weeks must all be part of the programme).
                val t = checkNotNull(d.planToday())
                assertNull(d.bridge.weekLimits(t.user, t.program, deload = false).guard)
            }
            doToday(d)
        }
        clock.setDay(Fixtures.MONDAY + 35, hour = 9)
        val t = checkNotNull(d.planToday())
        for (i in 0..4) d.docs.put(WeekSummary, WeekSummary(Fixtures.MONDAY + 7 * i, i + 1, 3, 3, ssu = 30.0, workload = 900.0))
        assertEquals(60.0, d.bridge.weekLimits(t.user, t.program, deload = false).guard!!, 1e-9)
        // A deload sets its own dose (DEL-003): no guard, and LOAD-005 is exempt.
        d.bridge.weekLimits(t.user, t.program, deload = true).let { assertNull(it.guard); assertTrue(it.loadExempt) }
        // The week after a lighter week: LOAD-005 is exempt, so the guard must stay (independent review, finding 1).
        val after = d.bridge.weekLimits(t.user, t.program.copy(justFinishedLighterWeek = true), deload = false)
        assertTrue(after.loadExempt)
        assertEquals(60.0, after.guard!!, 1e-9)
        assertNull(after.loadSsu)
        // A missed week counts as nothing: 2 × (30 + 30 + 0) / 3 = 40.
        d.docs.put(WeekSummary, WeekSummary(Fixtures.MONDAY + 28, 5, 3, 0))
        assertEquals(40.0, d.bridge.weekLimits(t.user, t.program, deload = false).guard!!, 1e-9)

        // A return after two weeks off (REG-003): the return ramp sets the dose, no guard.
        val (e, eclock) = Fixtures.data()
        Fixtures.onboard(e)
        for (day in Fixtures.MONDAY until Fixtures.MONDAY + 21) { eclock.setDay(day, hour = 9); doToday(e) }
        eclock.setDay(Fixtures.MONDAY + 35, hour = 9)
        val et = checkNotNull(e.planToday())
        assertTrue("in a return", e.bridge.returnPlan(et.user, et.program).value.inReturn)
        assertNull(e.bridge.weekLimits(et.user, et.program, deload = false).guard)
    }

    @Test fun `TC-VOL-007o reference weeks come from the last eight weeks only`() = runBlocking {
        val (d, clock) = Fixtures.data()
        Fixtures.onboard(d)
        for (day in Fixtures.MONDAY until Fixtures.MONDAY + 21) { clock.setDay(day, hour = 9); doToday(d) }
        // Three full weeks, then weeks without training. In week 8 the three are still within the window (weeks 0 to 7); in week 11 they
        // are not, so old weeks no longer set the limit (independent review, finding 6).
        clock.setDay(Fixtures.MONDAY + 7 * 8, hour = 9)
        checkNotNull(d.planToday())
        assertEquals(3, d.bridge.referenceWeeks(Fixtures.MONDAY + 7 * 8).size)
        clock.setDay(Fixtures.MONDAY + 7 * 11, hour = 9)
        checkNotNull(d.planToday())
        assertTrue(d.bridge.referenceWeeks(Fixtures.MONDAY + 7 * 11).isEmpty())
    }
}
