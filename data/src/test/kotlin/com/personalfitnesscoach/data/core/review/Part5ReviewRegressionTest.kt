package com.personalfitnesscoach.data.core.review

import com.personalfitnesscoach.data.core.Fixtures
import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.model.ConditionsRecord
import com.personalfitnesscoach.data.core.model.ExerciseState
import com.personalfitnesscoach.data.core.model.StoredCondition
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
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.LoadType
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.program.Goal
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.safety.ModalityJoints
import com.personalfitnesscoach.engine.safety.PainKind
import com.personalfitnesscoach.engine.safety.PainReport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Part 5 independent review (R5-xx): the session player and the data rules behind the screens. Each test is a former probe. */
class Part5ReviewRegressionTest {
    private suspend fun trained(minutes: Int = 60, conditions: List<StoredCondition> = emptyList(), priorities: List<Goal>? = null,
                                days: Int = 3): Triple<PfcData, FixedClock, SessionPlayer> {
        val (d, clock) = Fixtures.data()
        if (priorities != null) Fixtures.onboard(d, minutes = minutes, conditions = conditions, priorities = priorities, days = days)
        else Fixtures.onboard(d, minutes = minutes, conditions = conditions, days = days)
        for (ex in Library.all) d.docs.put(ExerciseState, ExerciseState(ex.id, e1rm = 100.0, e1rmDay = Fixtures.MONDAY - 3, lastDoneDay = Fixtures.MONDAY - 3, exposures = 6))
        return Triple(d, clock, SessionPlayer(d))
    }

    /** Starts the first planned day of the week whose generated session matches `pick`. */
    private suspend fun start(d: PfcData, tier: Tier = Tier.FULL, minutes: Int = 60, pick: (Workout) -> Boolean = { true }): Long? {
        val t = d.planToday()!!
        d.checkIns.record(t.user, CheckIn(3, 3, 3, 3))
        for (day in t.week.days) {
            val g = d.generateSession(t, day, tier, minutes)
            if (pick(g.workout)) return d.startSession(g)
        }
        return null
    }

    private suspend fun logAll(p: SessionPlayer, clock: FixedClock, rowId: Long) {
        p.skipRamp(rowId)
        var tg = p.view()!!.lifts.first { it.rowId == rowId }.next
        while (tg != null) {
            clock.ms += 60_000
            p.log(rowId, LiftEntry(tg.load, if (tg.unit == DoseUnit.SECONDS) null else if (tg.kind == SetKind.CALIBRATION) tg.reps.first else tg.reps.last,
                seconds = if (tg.unit == DoseUnit.SECONDS) tg.reps.last else null, rir = tg.targetRir ?: 3.0))
            tg = p.view()!!.lifts.first { it.rowId == rowId }.next
        }
    }

    @Test fun `R5-02 a pain stop on the knee also changes or drops conditioning that loads the knee, and later swaps still pass`() = runBlocking {
        val (d, _, p) = trained(minutes = 75)
        start(d, minutes = 75) { w -> w.conditioning.any { ModalityJoints.stress(it.modality, Joint.KNEE) >= 1 } &&
            w.items.any { (it.exercise.jointStress[Joint.KNEE] ?: 0) >= 2 } } ?: error("no session with knee-loading lifts and conditioning")
        p.completeStage(Stage.WARMUP)
        val lift = p.view()!!.lifts.first { (it.exercise.jointStress[Joint.KNEE] ?: 0) >= 2 }
        val r = p.reportPain(lift.rowId, PainReport(Joint.KNEE, PainKind.JOINT_OR_TENDON, 8))
        val limit = r.outcome.regionMaxStress ?: 0
        val after = r.view!!
        val loading = after.conditioning.withIndex().filter { (i, c) -> i !in after.state.conditioningDone && c.workMinutes > 0 && ModalityJoints.stress(c.modality, Joint.KNEE) > limit }
        assertTrue("conditioning still loading the knee: ${loading.map { it.value.modality }}", loading.isEmpty())
        val before = r.view!!.conditioning
        assertTrue("outcome=${r.outcome} changes=${r.changes} conditioning=${before.map { it.modality to it.workMinutes }} done=${after.state.conditioningDone}",
            r.changes.any { it is com.personalfitnesscoach.data.core.player.Change.ConditioningSwapped || it is com.personalfitnesscoach.data.core.player.Change.ConditioningDropped })
        // The whole session is valid again, so a later swap of an exercise not started is judged on its own merits.
        val other = after.lifts.firstOrNull { !it.finished && !it.hasLoggedWork && (it.exercise.jointStress[Joint.KNEE] ?: 0) == 0 }
        if (other != null) {
            val pick = p.swapOptions(other.rowId).options.firstOrNull { (it.exercise.jointStress[Joint.KNEE] ?: 0) <= limit }?.exercise?.id
            if (pick != null) { val sw = p.swap(other.rowId, pick); assertFalse("swap ${other.exercise.id} -> $pick refused: ${sw.decisions.map { it.outputs }}", sw.refused) }
        }
    }

    @Test fun `R5-03 change time before or right after the warm-up never jumps over the warm-up or the lifts it kept`() = runBlocking {
        for (afterWarmup in listOf(false, true)) for (m in listOf(10, 15, 20, 24)) {
            val (d, _, p) = trained(minutes = 75)
            start(d, minutes = 75) { w -> w.conditioning.isNotEmpty() && !w.conditioningFirst && w.items.isNotEmpty() } ?: error("no session")
            if (afterWarmup) p.completeStage(Stage.WARMUP)
            val v = p.changeTime(m).view
            val open = v.lifts.filter { !it.finished }
            if (!afterWarmup) assertTrue("m=$m before the warm-up: ${v.step}", v.step is Step.Warmup)
            else if (open.isNotEmpty()) assertTrue("m=$m kept lifts ${open.map { it.exercise.id }} skipped: ${v.step}", v.step is Step.Lift)
        }
    }

    @Test fun `R5-04 a heavier weight typed for one set never becomes a next target above the in-session limit or the tier cap`() = runBlocking {
        val (d, _, p) = trained()
        start(d, tier = Tier.MODIFIED) { w -> w.items.any { it.exercise.loadType == LoadType.BARBELL && it.load != null && !it.calibrating && it.sets >= 2 } }
        p.completeStage(Stage.WARMUP)
        val lift = p.view()!!.lifts.first { it.exercise.loadType == LoadType.BARBELL && it.item.load != null && it.item.unit == DoseUnit.REPS && !it.item.calibrating && it.item.sets >= 2 }
        p.skipRamp(lift.rowId)
        val tg = p.view()!!.lifts.first { it.rowId == lift.rowId }.next!!
        val planned = lift.item.load!!
        val heavy = Math.round(planned * 1.4 / 2.5) * 2.5
        val r = p.log(lift.rowId, LiftEntry(heavy, tg.reps.last, rir = tg.targetRir))
        val next = r.view.lifts.first { it.rowId == lift.rowId }.next!!
        assertTrue("next ${next.load} above ${planned * (1 + P.INT_007.net_limit_pct / 100.0)}", next.load!! <= planned * (1 + P.INT_007.net_limit_pct / 100.0) + 1e-9)
    }

    @Test fun `R5-05 a workout ended a day later counts only the time it was actually used`() = runBlocking {
        val (d, clock, p) = trained()
        start(d)
        p.completeStage(Stage.WARMUP)
        val lift = p.view()!!.lifts.first()
        p.skipRamp(lift.rowId)
        repeat(2) { clock.ms += 5 * 60_000; val tg = p.view()!!.lifts.first { it.rowId == lift.rowId }.next!!
            p.log(lift.rowId, LiftEntry(tg.load, if (tg.kind == SetKind.CALIBRATION) tg.reps.first else tg.reps.last, seconds = if (tg.unit == DoseUnit.SECONDS) tg.reps.last else null, rir = 3.0)) }
        clock.ms += 30 * 3_600_000L
        val s = p.finish(null, endedEarly = true)
        assertTrue("minutes ${s.minutes}", s.minutes < 60)
    }

    @Test fun `R5-06 a workout with only conditioning logged is ended, never discarded`() = runBlocking {
        val (d, _, p) = trained()
        start(d) { w -> w.conditioning.isNotEmpty() } ?: error("no session with conditioning")
        p.completeStage(Stage.WARMUP)
        p.logConditioning(0, p.view()!!.conditioning[0].workMinutes)
        assertFalse(p.discard())
    }

    @Test fun `R5-07 skipping an exercise after logged sets keeps them for history and progression`() = runBlocking {
        val (d, clock, p) = trained()
        start(d) { w -> w.items.any { !it.calibrating && it.unit == DoseUnit.REPS && it.sets >= 3 && it.load != null } }
        p.completeStage(Stage.WARMUP)
        val lift = p.view()!!.lifts.first { !it.item.calibrating && it.item.unit == DoseUnit.REPS && it.item.sets >= 3 && it.item.load != null }
        p.skipRamp(lift.rowId)
        val before = d.docs.get(ExerciseState, lift.exercise.id)!!.exposures
        repeat(2) { clock.ms += 60_000; val tg = p.view()!!.lifts.first { it.rowId == lift.rowId }.next!!; p.log(lift.rowId, LiftEntry(tg.load, tg.reps.last, rir = tg.targetRir)) }
        p.skipExercise(lift.rowId)
        val s = p.finish(null, endedEarly = true)
        val row = s.workout.exercises.first { it.row.id == lift.rowId }
        assertTrue(row.row.status != Status.SKIPPED)
        assertEquals(2, row.working.size)
        assertEquals(before + 1, d.docs.get(ExerciseState, lift.exercise.id)!!.exposures)
    }

    @Test fun `R5-08 choosing an exercise during the warm-up skips nothing, and a block never reached counts as not done`() = runBlocking {
        val (d, clock, p) = trained()
        start(d) { w -> w.conditioning.isNotEmpty() && w.items.isNotEmpty() } ?: error("no session")
        val v = p.select(p.view()!!.lifts.last().rowId)
        assertTrue(v.step is Step.Warmup)
        p.completeStage(Stage.WARMUP)
        logAll(p, clock, p.view()!!.lifts.first().rowId)
        val s = p.finish(null, endedEarly = true)
        assertTrue(s.workout.doc.conditioning.all { it.doneWorkMinutes == 0.0 })
    }

    @Test fun `R5-10 a late second tap for a set already logged is ignored`() = runBlocking {
        val (d, _, p) = trained()
        start(d)
        p.completeStage(Stage.WARMUP)
        val lift = p.view()!!.lifts.first()
        p.skipRamp(lift.rowId)
        val tg = p.view()!!.lifts.first { it.rowId == lift.rowId }.next!!
        val e = LiftEntry(tg.load, if (tg.kind == SetKind.CALIBRATION) tg.reps.first else tg.reps.last, seconds = if (tg.unit == DoseUnit.SECONDS) tg.reps.last else null, rir = 3.0)
        p.log(lift.rowId, e, expected = tg)
        val second = p.log(lift.rowId, e, expected = tg)
        assertTrue(second.ignored)
        assertEquals(1, p.view()!!.lifts.first { it.rowId == lift.rowId }.sets.count { it.kind != SetKind.WARMUP })
    }

    @Test fun `R5-13 a finding-your-weight exercise cut by a re-fit stops asking for sets`() = runBlocking {
        val (d, _) = Fixtures.data()
        Fixtures.onboard(d, level = com.personalfitnesscoach.engine.model.Level.BEGINNER)
        val p = SessionPlayer(d)
        val t = d.planToday()!!
        d.checkIns.record(t.user, CheckIn(3, 3, 3, 3))
        d.startSession(d.generateSession(t, t.next ?: t.week.days.first(), Tier.FULL, 60))
        p.completeStage(Stage.WARMUP)
        val cals = p.view()!!.lifts.filter { it.next?.kind == SetKind.CALIBRATION }
        assertTrue(cals.isNotEmpty())
        for (c in cals) { val tg = p.view()!!.lifts.first { it.rowId == c.rowId }.next!!; p.log(c.rowId, LiftEntry(tg.load, tg.reps.first, rir = 5.0)) }
        val r = p.changeTime(5)
        val still = r.view.lifts.filter { l -> cals.any { it.rowId == l.rowId } && r.changes.any { ch -> ch is com.personalfitnesscoach.data.core.player.Change.Skipped && ch.rowId == l.rowId } && l.next != null }
        assertTrue("still asking: ${still.map { it.exercise.id }}", still.isEmpty())
    }

    @Test fun `R5-12 knee arthritis - trained weeks within the pain rule count, a painful week starts the count again`() = runBlocking {
        val (d, clock, p) = trained(conditions = listOf(StoredCondition("oa_knee", Fixtures.MONDAY)))
        suspend fun weekOfTraining(pain: Int?) {
            start(d)
            p.completeStage(Stage.WARMUP)
            logAll(p, clock, p.view()!!.lifts.first().rowId)
            if (pain != null) d.reportPain(PainReport(Joint.KNEE, PainKind.JOINT_OR_TENDON, pain))
            p.finish(null, endedEarly = true)
            clock.advanceDays(7)
            d.onAppOpen()
        }
        fun weeks() = runBlocking { d.docs.get(ConditionsRecord)!!.items.first { it.id == "oa_knee" }.painRuleMetWeeks }
        weekOfTraining(null)
        assertEquals(1, weeks())
        weekOfTraining(2)
        assertEquals(2, weeks())
        weekOfTraining(7)
        assertEquals(0, weeks())
        // A week without training leaves the count as it was.
        clock.advanceDays(7)
        d.onAppOpen()
        assertEquals(0, weeks())
    }
}
