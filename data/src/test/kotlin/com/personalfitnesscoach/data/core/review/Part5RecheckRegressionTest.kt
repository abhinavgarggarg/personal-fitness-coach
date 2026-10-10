package com.personalfitnesscoach.data.core.review

import com.personalfitnesscoach.data.core.Fixtures
import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.model.ConditionsRecord
import com.personalfitnesscoach.data.core.model.ExerciseState
import com.personalfitnesscoach.data.core.model.Profile
import com.personalfitnesscoach.data.core.model.SettingsRecord
import com.personalfitnesscoach.data.core.model.StoredCondition
import com.personalfitnesscoach.data.core.onboarding.Onboarding
import com.personalfitnesscoach.data.core.player.LiftEntry
import com.personalfitnesscoach.data.core.player.SessionPlayer
import com.personalfitnesscoach.data.core.player.Step
import com.personalfitnesscoach.data.core.session.SetKind
import com.personalfitnesscoach.data.core.session.Stage
import com.personalfitnesscoach.data.core.session.Status
import com.personalfitnesscoach.data.core.time.FixedClock
import com.personalfitnesscoach.engine.calc.CheckIn
import com.personalfitnesscoach.engine.generation.Workout
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.program.Goal
import com.personalfitnesscoach.engine.safety.ClearanceScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Part 5 re-check of the review fixes (N1–N8): session time, the conditions editor's effect on goals, stored answers, the next day. */
class Part5RecheckRegressionTest {
    private suspend fun trained(minutes: Int = 60, conditions: List<StoredCondition> = emptyList(), priorities: List<Goal>? = null,
                                birthYear: Int = 1980): Triple<PfcData, FixedClock, SessionPlayer> {
        val (d, clock) = Fixtures.data()
        if (priorities != null) Fixtures.onboard(d, minutes = minutes, conditions = conditions, priorities = priorities, birthYear = birthYear)
        else Fixtures.onboard(d, minutes = minutes, conditions = conditions, birthYear = birthYear)
        for (ex in Library.all) d.docs.put(ExerciseState, ExerciseState(ex.id, e1rm = 100.0, e1rmDay = Fixtures.MONDAY - 3, lastDoneDay = Fixtures.MONDAY - 3, exposures = 6))
        return Triple(d, clock, SessionPlayer(d))
    }

    private suspend fun start(d: PfcData, minutes: Int = 60, pick: (Workout) -> Boolean = { true }): Long {
        val t = d.planToday()!!
        d.checkIns.record(t.user, CheckIn(3, 3, 3, 3))
        for (day in listOfNotNull(t.next) + t.week.days) {
            val g = d.generateSession(t, day, Tier.FULL, minutes)
            if (pick(g.workout)) return d.startSession(g)
        }
        error("no planned session matches")
    }

    private suspend fun logAll(p: SessionPlayer, clock: FixedClock, rowId: Long) {
        p.skipRamp(rowId)
        var tg = p.view()!!.lifts.first { it.rowId == rowId }.next
        while (tg != null) {
            clock.ms += 90_000
            p.log(rowId, LiftEntry(tg.load, if (tg.unit == DoseUnit.SECONDS) null else if (tg.kind == SetKind.CALIBRATION) tg.reps.first else tg.reps.last,
                seconds = if (tg.unit == DoseUnit.SECONDS) tg.reps.last else null, rir = tg.targetRir ?: 3.0), tg)
            tg = p.view()!!.lifts.first { it.rowId == rowId }.next
        }
    }

    /** One set of the first lift, three minutes after the last tap. */
    private suspend fun oneSet(p: SessionPlayer, clock: FixedClock) {
        var v = p.view()!!
        if (v.step is Step.Warmup) v = p.completeStage(Stage.WARMUP)
        if (v.step is Step.BoneLoading) v = p.completeStage(Stage.BONE_LOADING)
        val lift = (v.step as Step.Lift).lift
        p.skipRamp(lift.rowId)
        clock.ms += 3 * 60_000L
        val t = p.view()!!.lifts.first { it.rowId == lift.rowId }.next!!
        p.log(lift.rowId, LiftEntry(t.load, t.reps.last, rir = t.targetRir ?: 3.0), t)
    }

    @Test fun `N1 a 30-minute steady block after the lifts is session time, not idle time`() = runBlocking {
        val (d, clock, p) = trained(minutes = 90)
        val id = start(d, 90) { w -> !w.conditioningFirst && w.items.isNotEmpty() && w.conditioning.isNotEmpty() }
        val w0 = d.sessions.load(id)!!
        d.sessions.updateDoc(id, w0.doc.copy(conditioning = w0.doc.conditioning.mapIndexed { i, c ->
            if (i == 0) c.copy(workMinutes = 30.0, restMinutes = 0.0, interval = null, hiit = false, protocol = null) else c }))
        var v = p.completeStage(Stage.WARMUP)
        if (v.step is Step.BoneLoading) v = p.completeStage(Stage.BONE_LOADING)
        for (l in v.lifts) logAll(p, clock, l.rowId)
        v = p.view()!!
        val step = v.step as Step.Conditioning
        assertEquals("the screen shows the block after the lifts", 0, step.index)
        val before = v.elapsedMinutes
        clock.ms += 30 * 60_000L
        assertTrue("during the ride the minutes keep counting", p.view()!!.elapsedMinutes >= before + 29.5)
        val after = p.logConditioning(step.index, 30.0)
        assertTrue("the ride counts: ${after.elapsedMinutes} after $before", after.elapsedMinutes >= before + 29.5)
        var s = after
        while (s.step !is Step.Done) s = p.completeStage(s.stage!!)
        val sum = p.finish(null)
        assertTrue("stored minutes include the ride: ${sum.workout.row.actualMinutes}", (sum.workout.row.actualMinutes ?: 0.0) >= before + 29.5)
    }

    @Test fun `N5 a workout reopened the next day before its first tap does not store the night as session time`() = runBlocking {
        val (d, clock, p) = trained()
        start(d)
        clock.ms += 26 * 3_600_000L // the phone died right after Start; the app is opened the next evening
        assertTrue("elapsed after the gap: ${p.view()!!.elapsedMinutes}", p.view()!!.elapsedMinutes < 1.0)
        oneSet(p, clock)
        val sum = p.finish(null, endedEarly = true)
        val m = sum.workout.row.actualMinutes!!
        assertTrue("stored minutes: $m", m in 2.5..5.0)
    }

    @Test fun `a long warm-up without a tap is still session time (heart condition, 20+ minutes)`() = runBlocking {
        val heart = listOf(StoredCondition("heart", Fixtures.MONDAY, clearance = setOf(ClearanceScope.VIGOROUS)))
        val (d, clock, p) = trained(minutes = 75, conditions = heart)
        start(d, 75)
        val warmup = p.view()!!.workout.doc.warmupMinutes
        assertTrue("a long warm-up ($warmup min)", warmup >= 15.0)
        // Done slowly, without a tap: 8 minutes longer than planned, past the general 20-minute allowance.
        val took = warmup + 8.0
        clock.ms += (took * 60_000).toLong()
        val v = p.completeStage(Stage.WARMUP)
        assertTrue("elapsed ${v.elapsedMinutes} after a $took-minute warm-up", v.elapsedMinutes >= took - 0.1)
    }

    @Test fun `N6 an idle gap ended by tapping Pause is not session time`() = runBlocking {
        val (d, clock, p) = trained()
        start(d)
        p.completeStage(Stage.WARMUP)
        clock.ms += 3 * 3_600_000L // the phone left in a bag for three hours
        p.pause()
        clock.ms += 60_000
        p.resume()
        oneSet(p, clock)
        val m = p.finish(null, endedEarly = true).workout.row.actualMinutes!!
        assertTrue("stored minutes: $m", m < 10.0)
    }

    @Test fun `a red flag after a long idle gap stores the session's own minutes`() = runBlocking {
        val (d, clock, p) = trained()
        val id = start(d)
        p.completeStage(Stage.WARMUP)
        clock.ms += 5 * 60_000L
        oneSet(p, clock)
        clock.ms += 3 * 3_600_000L
        assertNotNull(p.redFlag(setOf("chest_pain_pressure_tightness")))
        val w = d.sessions.load(id)!!
        assertEquals(Status.DONE, w.row.status)
        assertTrue("stored minutes: ${w.row.actualMinutes}", (w.row.actualMinutes ?: 999.0) < 15.0)
    }

    @Test fun `N7 asking for less time than a condition's floors allow says how short the session can be`() = runBlocking {
        val heart = listOf(StoredCondition("heart", Fixtures.MONDAY, clearance = setOf(ClearanceScope.VIGOROUS)))
        val (d, _, p) = trained(minutes = 75, conditions = heart)
        start(d, 75)
        val warmup = p.view()!!.workout.doc.warmupMinutes
        val r = p.changeTime(20)
        val shortest = r.shortestSafeMinutes
        assertNotNull("with a heart condition the floors exceed 20 minutes (left ${r.view.remainingMinutes})", shortest)
        assertTrue("shortest $shortest > asked 20", shortest!! > 20)
        assertTrue("shortest $shortest matches what is left ${r.view.remainingMinutes}", r.view.remainingMinutes <= shortest + 0.01 &&
            r.view.remainingMinutes > shortest - 1.0)
        // The warm-up still to do went to its floor, which keeps the condition's extra minutes (R3-02).
        val after = r.view.workout.doc.warmupMinutes
        assertTrue("warm-up $warmup → $after", after <= warmup && after >= 5.0 + 10.0 - 1e-9)
        // Thirty minutes fits once the warm-up and rests are fitted: what is shown is what was asked (the earlier 34.8, P9).
        val (d3, _, p3) = trained(minutes = 75, conditions = heart)
        start(d3, 75)
        val r3 = p3.changeTime(30)
        assertNull(r3.shortestSafeMinutes)
        assertTrue("left ${r3.view.remainingMinutes}", r3.view.remainingMinutes <= 30.5)
        val (d2, _, p2) = trained(minutes = 75)
        start(d2, 75)
        val r2 = p2.changeTime(45)
        assertNull("nothing to say when the time fits (left ${r2.view.remainingMinutes})", r2.shortestSafeMinutes)
        assertTrue(r2.view.remainingMinutes <= 45.5)
    }

    @Test fun `N2 adding pregnancy after onboarding drops the fat-loss goal and switches weight features off`() = runBlocking {
        val (d, _, _) = trained(priorities = listOf(Goal.FAT_LOSS, Goal.GENERAL_FITNESS), birthYear = 1990)
        val o = Onboarding(d)
        assertNull("nothing to change without a condition that rules fat loss out", o.reconcileGoals())
        d.docs.put(ConditionsRecord, ConditionsRecord(listOf(StoredCondition("pregnancy", Fixtures.MONDAY, pregnancyWeek = 14,
            clearance = setOf(ClearanceScope.LIGHT_MODERATE))), Fixtures.MONDAY))
        val change = o.reconcileGoals()!!
        assertEquals(listOf(Goal.FAT_LOSS), change.removed)
        assertEquals(listOf(Goal.GENERAL_FITNESS), change.priorities)
        assertTrue(change.weightFeaturesOff)
        assertEquals(listOf(Goal.GENERAL_FITNESS), d.docs.get(Profile)!!.priorities)
        assertTrue(d.docs.get(SettingsRecord)!!.weightFeaturesOff)
        val offered = o.goalOptions(true).value
        assertTrue(d.docs.get(Profile)!!.priorities.all { it in offered.offered })
        assertNull("a second save changes nothing", o.reconcileGoals())
        // Only fat loss as a goal: the default replaces it.
        val (d2, _, _) = trained(priorities = listOf(Goal.FAT_LOSS), birthYear = 1990)
        d2.docs.put(ConditionsRecord, ConditionsRecord(listOf(StoredCondition("cancer", Fixtures.MONDAY, subFlags = setOf("in_active_treatment"))), Fixtures.MONDAY))
        val c2 = Onboarding(d2).reconcileGoals()!!
        assertEquals(listOf(Goal.GENERAL_FITNESS), c2.priorities)
        assertFalse("the week is no longer planned as fat loss", d2.planToday()!!.user.profile.priorities.contains(Goal.FAT_LOSS))
    }

    @Test fun `N3 the pregnancy 'not OK'd' answer is stored as given and still means follow the provider`() {
        val c = StoredCondition("pregnancy", Fixtures.MONDAY, pregnancyWeek = 14, attestNotOk = true)
        val back = ConditionsRecord.decode(ConditionsRecord.encode(ConditionsRecord(listOf(c), Fixtures.MONDAY))).items.single()
        assertTrue(back.attestNotOk)
        assertFalse("not stored as the separate block_if answer", back.blockIfYes)
        assertTrue("the engine follows the provider", back.toUserCondition(Fixtures.MONDAY, 0).blockIfYes)
        val ok = c.copy(attestNotOk = false, attested = true)
        assertFalse("OK'd later: no block", ok.toUserCondition(Fixtures.MONDAY, 0).blockIfYes)
    }

    @Test fun `N8 a later planned day done early is listed as done this week`() = runBlocking {
        val (d, clock, p) = trained()
        val t = d.planToday()!!
        val later = t.week.days.last { it.weekday > 0 && (it.slots.isNotEmpty() || it.conditioning.isNotEmpty()) }
        d.checkIns.record(t.user, CheckIn(3, 3, 3, 3))
        d.startSession(d.generateSession(t, later, Tier.FULL, 60))
        oneSet(p, clock)
        p.finish(null, endedEarly = true)
        assertTrue("done weekdays ${d.planToday()!!.doneWeekdays} include ${later.weekday}", later.weekday in d.planToday()!!.doneWeekdays)
    }
}
