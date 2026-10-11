package com.personalfitnesscoach.data.core.insight

import com.personalfitnesscoach.data.core.Fixtures
import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.model.ExerciseState
import com.personalfitnesscoach.data.core.model.SettingsRecord
import com.personalfitnesscoach.data.core.model.WaistRecord
import com.personalfitnesscoach.data.core.model.WeightRecord
import com.personalfitnesscoach.data.core.player.LiftEntry
import com.personalfitnesscoach.data.core.player.SessionPlayer
import com.personalfitnesscoach.data.core.player.Step
import com.personalfitnesscoach.data.core.session.SetKind
import com.personalfitnesscoach.data.core.session.Stage
import com.personalfitnesscoach.data.core.time.FixedClock
import com.personalfitnesscoach.engine.calc.CheckIn
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.program.Achievement
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** History (H1/H2) and progress (P1/P2) read back from what was logged; corrections are rebuilt and logged (FS-5). */
class InsightsTest {
    private suspend fun setup(): Triple<PfcData, FixedClock, SessionPlayer> {
        val (d, clock) = Fixtures.data()
        Fixtures.onboard(d)
        for (ex in Library.all) d.docs.put(ExerciseState, ExerciseState(ex.id, e1rm = 100.0, e1rmDay = Fixtures.MONDAY - 3, lastDoneDay = Fixtures.MONDAY - 3, exposures = 6))
        return Triple(d, clock, SessionPlayer(d))
    }

    /** Today's planned session, all sets logged as prescribed (or only the first lift when `partial`). */
    private suspend fun session(d: PfcData, clock: FixedClock, p: SessionPlayer, partial: Boolean = false, extraReps: Int = 0) {
        val t = d.planToday()!!
        d.checkIns.record(t.user, CheckIn(3, 3, 3, 3))
        d.startSession(d.generateSession(t, t.next!!, Tier.FULL, 60))
        var v = p.completeStage(Stage.WARMUP)
        if (v.step is Step.BoneLoading) v = p.completeStage(Stage.BONE_LOADING)
        for (l in if (partial) v.lifts.take(1) else v.lifts) {
            p.skipRamp(l.rowId)
            var tg = p.view()!!.lifts.first { it.rowId == l.rowId }.next
            while (tg != null) {
                clock.ms += 90_000
                p.log(l.rowId, LiftEntry(tg.load, if (tg.unit == DoseUnit.SECONDS) null else if (tg.kind == SetKind.CALIBRATION) tg.reps.first else tg.reps.last + extraReps,
                    seconds = if (tg.unit == DoseUnit.SECONDS) tg.reps.last else null, rir = tg.targetRir ?: 3.0), tg)
                tg = p.view()!!.lifts.first { it.rowId == l.rowId }.next
            }
        }
        p.finish(6.0, endedEarly = partial)
    }

    @Test fun `history lists finished sessions newest first, with how each ended`() = runBlocking {
        val (d, clock, p) = setup()
        session(d, clock, p)
        clock.setDay(Fixtures.MONDAY + 2, hour = 8)
        session(d, clock, p, partial = true)
        val h = Insights(d).history(Fixtures.MONDAY - 30, Fixtures.MONDAY + 7)
        assertEquals(2, h.size)
        assertEquals(listOf(SessionOutcome.PARTIAL, SessionOutcome.DONE), h.map { it.outcome })
        assertTrue(h.all { it.sets > 0 && (it.minutes ?: 0.0) > 0.0 && it.sessionRpe == 6.0 })
        val detail = Insights(d).session(h.last().workoutId)!!
        assertTrue(detail.editable)
        assertTrue(detail.exercises.isNotEmpty() && detail.exercises.all { e -> e.sets.isNotEmpty() || e.status != "DONE" })
        assertTrue("its decisions come with it", detail.decisions.isNotEmpty())
    }

    @Test fun `correcting a past set rebuilds the estimated max and is logged, impossible values are refused`() = runBlocking {
        val (d, clock, p) = setup()
        session(d, clock, p)
        val ins = Insights(d)
        val w = ins.session(ins.history(Fixtures.MONDAY, Fixtures.MONDAY).single().workoutId)!!
        val ex = w.exercises.first { e -> e.sets.any { it.kind == SetKind.WORKING && (it.loadKg ?: 0.0) > 0 } }
        val set = ex.sets.first { it.kind == SetKind.WORKING }
        val before = d.docs.get(ExerciseState, ex.exerciseId)!!.e1rm!!
        ins.correctSet(set.setId, set.loadKg!! + 20.0, set.reps, set.seconds, 0.0)
        val after = d.docs.get(ExerciseState, ex.exerciseId)!!.e1rm!!
        assertTrue("estimated max rebuilt: $before → $after", after > before)
        assertTrue(d.decisions.forDay(Fixtures.MONDAY).any { it.reason == "HISTORY_EDITED" })
        val snap = d.store.snapshot()
        try { ins.correctSet(set.setId, 5000.0, 5, null, 2.0); fail("5000 kg accepted") } catch (e: IllegalArgumentException) { }
        try { ins.correctSet(set.setId, 50.0, -4, null, 2.0); fail("negative reps accepted") } catch (e: IllegalArgumentException) { }
        assertEquals("a refused correction changes nothing", snap, d.store.snapshot())
        ins.deleteSet(set.setId)
        assertTrue(ins.session(w.item.workoutId)!!.exercises.first { it.rowId == ex.rowId }.sets.none { it.setId == set.setId })
        assertEquals(2, d.decisions.forDay(Fixtures.MONDAY).count { it.reason == "HISTORY_EDITED" })
    }

    @Test fun `progress shows lifts, weeks, streak, achievements, body and steps`() = runBlocking {
        val (d, clock, p) = setup()
        // Two weeks of training, a little better each session.
        var reps = 0
        for (week in 0..1) for (offset in listOf(0, 2, 4)) {
            clock.setDay(Fixtures.MONDAY + week * 7 + offset, hour = 8)
            if (d.planToday()?.next == null) continue
            session(d, clock, p, extraReps = reps++ / 3)
        }
        clock.setDay(Fixtures.MONDAY + 14, hour = 8)
        d.onAppOpen()
        val ins = Insights(d)
        ins.logWeight(90.0, Fixtures.MONDAY + 13)
        ins.logWeight(89.6, Fixtures.MONDAY + 14)
        ins.logWaist(listOf(95.0, 95.4, 95.2), Fixtures.MONDAY + 14)
        val o = ins.overview(Fixtures.MONDAY + 14)
        assertTrue("sessions: ${o.sessionsCompleted}", o.sessionsCompleted >= 5)
        assertTrue(o.lifts.isNotEmpty() && o.lifts.first().points.isNotEmpty())
        assertEquals(2, o.weeks.size)
        assertTrue(o.weeks.all { it.completed >= 2 && it.strengthDays >= 2 })
        assertTrue(o.streak.current >= 2)
        assertTrue(Achievement.FIRST_SESSION in o.achievements)
        assertNotNull(o.body)
        assertEquals(2, o.body!!.weights.size)
        assertEquals(95.2, o.body!!.waistCm!!, 0.05)
        // P2: one exercise over time.
        val id = o.lifts.first().exerciseId
        val e = ins.exercise(id, Fixtures.MONDAY + 14)
        assertTrue(e.points.size >= 2 && e.best != null)
    }

    @Test fun `with weight features off nothing about weight or waist is shown or stored`() = runBlocking {
        val (d, _, _) = setup()
        d.docs.update(SettingsRecord) { (it ?: SettingsRecord()).copy(weightFeaturesOff = true) }
        val ins = Insights(d)
        assertNull(ins.overview().body)
        try { ins.logWeight(80.0); fail("weight stored with weight features off") } catch (e: IllegalStateException) { }
        try { ins.logWaist(listOf(90.0)); fail("waist stored with weight features off") } catch (e: IllegalStateException) { }
        assertTrue(d.docs.all(WeightRecord).isEmpty() && d.docs.all(WaistRecord).isEmpty())
    }

    @Test fun `an exercise marked don't suggest is no longer planned, and a favourite is kept`() = runBlocking {
        val (d, _, _) = setup()
        val ins = Insights(d)
        val planned = d.planToday()!!.week.days.flatMap { it.slots }.map { it.exercise.id }
        val skip = planned.first()
        ins.setFavourite(skip, true)
        ins.setExcluded(skip, true)
        assertTrue(ins.exercise(skip).excluded)
        assertFalse("excluding removes the favourite", ins.exercise(skip).favourite)
        assertFalse("$skip still planned", d.planToday()!!.week.days.flatMap { it.slots }.any { it.exercise.id == skip })
        assertEquals(listOf(skip), ins.excluded())
        ins.setExcluded(skip, false)
        assertTrue(ins.excluded().isEmpty())
        try { ins.setExcluded("not-an-exercise", true); fail("unknown exercise accepted") } catch (e: IllegalArgumentException) { }
    }
}
