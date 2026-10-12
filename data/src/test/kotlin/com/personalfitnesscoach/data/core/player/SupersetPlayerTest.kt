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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** TIME-002 supersets in the guided workout (Part 8, D-109): what the time fit pairs is played as a superset, so it takes the time promised. */
class SupersetPlayerTest {

    private suspend fun pairedSession(): Pair<PfcData, SessionPlayer> {
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
            d.startSession(g)
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
            val partner = p.supersetPartner(lift, v.lifts)
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
        assertNotNull(p.supersetPartner(same, again.lifts))
        assertEquals(first.item, ItemDoc.decode(first.item.encode()))
        // A document without a partner is written exactly as before (no new field).
        assertTrue("supersetWith" !in first.item.copy(supersetWith = null).encode())
    }
}
