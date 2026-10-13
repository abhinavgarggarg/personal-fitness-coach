package com.personalfitnesscoach.data.core.player

import com.personalfitnesscoach.data.core.Fixtures
import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.model.ExerciseState
import com.personalfitnesscoach.data.core.session.ItemDoc
import com.personalfitnesscoach.data.core.session.SetKind
import com.personalfitnesscoach.data.core.session.Stage
import com.personalfitnesscoach.engine.calc.CheckIn
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.safety.PainKind
import com.personalfitnesscoach.engine.safety.PainReport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** TIME-002 supersets in the guided workout (Part 8, D-109): what the time fit pairs is played as a superset, so it takes the time promised. */
class SupersetPlayerTest {

    private suspend fun pairedSession(unpaired: Boolean = false): Pair<PfcData, SessionPlayer> {
        for (minutes in listOf(40, 35, 45, 30, 50)) {
            val (d, _) = Fixtures.data()
            Fixtures.onboard(d, days = 2, minutes = 60)
            for (ex in Library.all) d.docs.put(ExerciseState, ExerciseState(ex.id, e1rm = 100.0, e1rmDay = Fixtures.MONDAY - 3, lastDoneDay = Fixtures.MONDAY - 3, exposures = 6))
            val t = d.planToday()!!
            d.checkIns.record(t.user, CheckIn(3, 3, 3, 3))
            val g = d.generateSession(t, t.next ?: t.week.days.first(), Tier.FULL, minutes)
            if (g.workout.items.none { it.supersetWith != null }) continue
            // Both partners point to each other, train different muscles and are neither main lifts nor being calibrated.
            for (a in g.workout.items.filter { it.supersetWith != null }) {
                val b = g.workout.items.single { it.slotKey == a.supersetWith }
                assertEquals(a.slotKey, b.supersetWith)
                assertTrue((a.exercise.primary intersect b.exercise.primary).isEmpty() && !a.main && !b.main && !a.calibrating && !b.calibrating)
            }
            val id = d.startSession(g)
            if (unpaired) d.sessions.load(id)!!.exercises.forEach { e -> d.sessions.updateItem(e.row.id, e.doc.copy(supersetWith = null)) }
            return d to SessionPlayer(d)
        }
        error("no session needed a superset")
    }

    private suspend fun logNext(p: SessionPlayer, l: LiftView): LogResult {
        val n = l.next!!
        val entry = if (n.unit == DoseUnit.SECONDS) LiftEntry(n.load, null, seconds = n.reps.last, rir = n.targetRir ?: 2.0)
            else LiftEntry(n.load, n.reps.last, rir = n.targetRir ?: 2.0)
        return p.log(l.rowId, entry)
    }

    @Test fun `a superset alternates the partners' sets with the REST-004 rest, then the session goes on`() = runBlocking {
        val (_, p) = pairedSession()
        p.completeStage(Stage.WARMUP)
        var guard = 0
        var alternations = 0
        while (guard++ < 200) {
            val v = p.view()!!
            val lift = (v.step as? Step.Lift)?.lift ?: break
            if (lift.next?.kind == SetKind.WARMUP) { p.skipRamp(lift.rowId); continue }
            val partner = SessionPlayer.supersetPartner(lift, v.lifts)
            val r = logNext(p, lift)
            if (partner != null && partner.next?.kind == SetKind.WORKING) {
                // The partner is next, after the superset rest.
                assertEquals(P.REST_004.default_s, r.restSec)
                assertEquals(partner.rowId, ((r.view.step as Step.Lift).lift.rowId))
                alternations++
            } else assertEquals(lift.item.restDefaultSec, r.restSec)
        }
        assertTrue("sets alternated ($alternations)", alternations >= 2)
        // Every planned working set was done: nothing was skipped by the alternation.
        val done = p.view()!!.lifts
        assertTrue(done.all { it.finished })
        assertTrue(done.all { it.workingDone >= it.item.sets - it.item.addedSets || it.item.calibrating })
    }

    @Test fun `the partner survives a restart and the item document keeps it`() = runBlocking {
        val (d, p) = pairedSession()
        p.completeStage(Stage.WARMUP)
        val first = p.view()!!.lifts.first { it.item.supersetWith != null }
        val again = SessionPlayer(PfcData(d.store, d.clock, "test")).view()!!
        val same = again.lifts.single { it.rowId == first.rowId }
        assertNotNull(SessionPlayer.supersetPartner(same, again.lifts))
        assertEquals(first.item, ItemDoc.decode(first.item.encode()))
        // A document without a partner is written exactly as before (no new field).
        assertTrue("supersetWith" !in first.item.copy(supersetWith = null).encode())
    }

    /** Plays the session in order up to the first working set of a superset; returns the pair (A just done, B next). */
    private suspend fun toFirstPairedSet(p: SessionPlayer): Pair<LiftView, LiftView> {
        p.completeStage(Stage.WARMUP)
        var guard = 0
        while (guard++ < 200) {
            val v = p.view()!!
            val lift = (v.step as? Step.Lift)?.lift ?: break
            if (lift.next?.kind == SetKind.WARMUP) { p.skipRamp(lift.rowId); continue }
            val partner = SessionPlayer.supersetPartner(lift, v.lifts)
            logNext(p, lift)
            if (partner != null && partner.next?.kind == SetKind.WORKING) return lift to partner
        }
        error("no superset reached")
    }

    /** The rest shown before the next set is never below that exercise's minimum, unless it is the set of a running superset. */
    private fun assertRestFits(v: PlayerView) {
        val l = (v.step as? Step.Lift)?.lift ?: return
        val sec = v.state.restSec ?: return
        if (v.state.restStartedAtMs == null || SessionPlayer.supersetPartner(l, v.lifts) != null) return
        assertTrue("${l.exercise.id}: rest $sec s, its minimum ${l.item.restMinSec} s", sec >= l.item.restMinSec)
    }

    @Test fun `do it later on the partner ends the superset - the own rest applies and the deferred exercise waits its turn`() = runBlocking {
        val (_, p) = pairedSession()
        val (a, b) = toFirstPairedSet(p)
        val v = p.doLater(b.rowId)
        assertRestFits(v)
        assertNull(v.lifts.single { it.rowId == a.rowId }.item.supersetWith)
        assertNull(v.lifts.single { it.rowId == b.rowId }.item.supersetWith)
        // A goes on with its own sets; B comes back only at the end of the list.
        val nowA = v.lifts.single { it.rowId == a.rowId }
        if (!nowA.finished) {
            val r = logNext(p, nowA)
            if (!r.view.lifts.single { it.rowId == a.rowId }.finished) assertNotEquals(b.rowId, (r.view.step as Step.Lift).lift.rowId)
            assertEquals(nowA.item.restDefaultSec, r.restSec)
        }
    }

    @Test fun `skipping or stopping the partner for pain leaves a rest that fits the next exercise`() = runBlocking {
        run {
            val (_, p) = pairedSession()
            val (_, b) = toFirstPairedSet(p)
            assertRestFits(p.skipExercise(b.rowId))
        }
        run {
            val (_, p) = pairedSession()
            val (_, b) = toFirstPairedSet(p)
            val joint = b.exercise.jointStress.maxByOrNull { it.value }!!.key
            val r = p.reportPain(b.rowId, PainReport(joint, PainKind.JOINT_OR_TENDON, 5))
            r.view?.let { assertRestFits(it) }
        }
        Unit
    }

    @Test fun `changing the time left drops the exercise cut, not its superset partner with it`() = runBlocking {
        for (minutes in listOf(4, 6, 8, 10, 12, 15, 20)) {
            val (_, paired) = pairedSession()
            paired.completeStage(Stage.WARMUP)
            val kept = paired.changeTime(minutes).view.lifts.count { it.status != com.personalfitnesscoach.data.core.session.Status.SKIPPED }
            val (_, plain) = pairedSession(unpaired = true)
            plain.completeStage(Stage.WARMUP)
            val keptPlain = plain.changeTime(minutes).view.lifts.count { it.status != com.personalfitnesscoach.data.core.session.Status.SKIPPED }
            // Supersets save time, so the paired session never keeps less work than the same session played one exercise at a time.
            assertTrue("$minutes min left: paired kept $kept, unpaired kept $keptPlain", kept >= keptPlain)
        }
    }
}
