package com.personalfitnesscoach.data.core.engine

import com.personalfitnesscoach.data.core.Fixtures
import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.session.NewSet
import com.personalfitnesscoach.data.core.session.SetKind
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.engine.calc.CheckIn
import com.personalfitnesscoach.engine.calc.Ssu
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.safety.SessionValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    @Test fun `TC-VOL-007f calibration sets count at their logged effort`() = runBlocking {
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
        val c = Ssu.cost(w.items[i].exercise.costClass)
        // 5 in reserve is an easy ramp set (0); 3 counts 0.8; 2 counts 1.0 (VOL-007 E factors).
        assertEquals(c * (0.0 + 0.8 + 1.0), d.bridge.calibrationSsu(stored), 1e-9)
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
}
