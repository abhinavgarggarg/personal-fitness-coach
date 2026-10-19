package com.personalfitnesscoach.data.core.review

import com.personalfitnesscoach.data.core.Fixtures
import com.personalfitnesscoach.data.core.engine.ExerciseProgress
import com.personalfitnesscoach.data.core.engine.ProgressContext
import com.personalfitnesscoach.data.core.model.ExerciseState
import com.personalfitnesscoach.data.core.model.ProgramRecord
import com.personalfitnesscoach.data.core.session.ItemDoc
import com.personalfitnesscoach.data.core.session.SetKind
import com.personalfitnesscoach.data.core.session.Status
import com.personalfitnesscoach.data.core.session.StoredExercise
import com.personalfitnesscoach.data.core.session.StoredWorkout
import com.personalfitnesscoach.data.core.session.WorkoutDoc
import com.personalfitnesscoach.data.core.store.ExerciseRow
import com.personalfitnesscoach.data.core.store.SetRow
import com.personalfitnesscoach.data.core.store.WorkoutRow
import com.personalfitnesscoach.engine.calc.E1rm
import com.personalfitnesscoach.engine.calc.Inventory
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.planning.Priority
import com.personalfitnesscoach.engine.program.SlotRole
import com.personalfitnesscoach.engine.progression.Prescription
import com.personalfitnesscoach.engine.progression.ProgressionAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 4 Part 4: the lightest load that is too heavy, from logged sets to the next plan, and the new-block re-check. */
class Part4AuditDataTest {
    private val bench = Library.require("bench-press")
    private val ctx = ProgressContext(Inventory(), Level.BEGINNER, 40, flaggedScreen = false, recoveryOk = true)
    private val day = 20_000

    private fun item(calibrating: Boolean, load: Double = 20.0, recheck: Boolean = false, offer: String? = null) =
        ItemDoc(SlotRole.MAIN, Priority.P1, 3, 8..12, DoseUnit.REPS, targetRir = 2.0, load = load, restMinSec = 120, restDefaultSec = 150,
            restMaxSec = 180, calibrating = calibrating, main = true, lightestRecheck = recheck, easierOffer = offer)

    private fun exposure(item: ItemDoc, vararg sets: Triple<String, Double, Pair<Int, Double?>>) = StoredExercise(
        ExerciseRow(1, 1, 0, bench.id, "main", "DONE", item.encode()), item,
        sets.mapIndexed { i, (kind, load, rr) -> SetRow(i + 1L, 1, i, kind, load, rr.first, rir = rr.second, loggedAtMs = i.toLong()) })

    private fun cal(load: Double, reps: Int, rir: Double?) = Triple(SetKind.CALIBRATION, load, reps to rir)
    private fun work(load: Double, reps: Int, rir: Double?) = Triple(SetKind.WORKING, load, reps to rir)

    @Test fun `AUD-10 working sets at the empty bar short of the range mark it too heavy, and sets in the range do not`() {
        val short = ExerciseProgress.apply(ExerciseState(bench.id), bench, exposure(item(false), work(20.0, 4, 1.0), work(20.0, 4, 1.0)), day, ctx, null).value
        val p = short.state.prescription!!
        assertEquals(20.0, p.load, 0.0)
        assertTrue("4 reps at RIR 1 cannot become 8 at RIR 2", p.lightestTooHeavy)
        val fine = ExerciseProgress.apply(ExerciseState(bench.id), bench, exposure(item(false), work(20.0, 8, 2.0), work(20.0, 8, 2.0)), day, ctx, null).value
        assertFalse(fine.state.prescription!!.lightestTooHeavy)
    }

    @Test fun `AUD-11 a ramp set not lifted at the lightest load ends the ramp as too heavy, instead of ramping again at the same load`() {
        val r = ExerciseProgress.apply(ExerciseState(bench.id), bench, exposure(item(true), cal(20.0, 0, 0.0)), day, ctx, null).value
        val p = r.state.prescription!!
        assertEquals(20.0, p.load, 0.0)
        assertTrue(p.lightestTooHeavy)
        assertNull(r.state.calibration)
    }

    @Test fun `AUD-12 a ramp stopped short at the bar gives an estimate the plan can tell is too heavy`() {
        // 3 reps with about 3 left at 20 kg: the e1RM (24 kg) supports 18 kg for 8 reps at RIR 2, so the bar is too heavy for 8-12.
        val r = ExerciseProgress.apply(ExerciseState(bench.id), bench, exposure(item(true), cal(20.0, 3, 3.0)), day, ctx, null).value
        val est = r.state.e1rm ?: E1rm.fromSet(20.0, 3, 3.0)!!
        assertTrue(com.personalfitnesscoach.engine.calc.PlateMath.belowLightest(E1rm.loadFor(est, 8, 2.0), listOf(20.0, 22.5)) ||
            r.state.prescription?.lightestTooHeavy == true)
    }

    @Test fun `AUD-13 the re-check ramp in a new block starts the exercise afresh`() = runBlocking {
        val (d, _) = Fixtures.data()
        Fixtures.onboard(d)
        val u = d.bridge.user()!!
        val old = ExerciseState(bench.id, e1rm = 21.0, e1rmDay = Fixtures.MONDAY, prescription = Prescription(20.0, 8..12, 8, ProgressionAction.REDUCE,
            lightestTooHeavy = true), prescriptionDay = Fixtures.MONDAY, lastDoneDay = Fixtures.MONDAY, exposures = 3)
        d.docs.put(ExerciseState, old)
        // Four weeks later (no layoff), the new block's ramp: 20 kg for 10 reps easily, then 25 kg with 3 left.
        val doc = item(true, recheck = true)
        val w = StoredWorkout(WorkoutRow(1, Fixtures.MONDAY + 28, Status.DONE, "FB_A", registryVersion = "3.0.1", json = WorkoutDoc(0, 60.0, 5.0).encode()),
            WorkoutDoc(0, 60.0, 5.0), listOf(exposure(doc, cal(20.0, 10, 5.0), cal(25.0, 10, 3.0))))
        d.progress.record(w, u)
        val s = d.docs.get(ExerciseState, bench.id)!!
        // 10 + 3 is not a valid estimate (INT-004), so the found load is the working load; the old 21 kg estimate is gone.
        assertNull(s.e1rm)
        val p = s.prescription!!
        assertTrue("found at 25 kg or less: ${p.load}", p.load in 20.0..25.0)
        assertFalse("the old too-heavy prescription is gone", p.lightestTooHeavy)
        assertEquals(4, s.exposures)
    }

    @Test fun `AUD-14 the flags survive storage`() {
        val st = ExerciseState(bench.id, prescription = Prescription(20.0, 8..12, 8, ProgressionAction.HOLD, lightestTooHeavy = true))
        val back = ExerciseState.decode(ExerciseState.encode(st))
        assertTrue(back.prescription!!.lightestTooHeavy)
        val doc = item(true, recheck = true, offer = "db-bench-press")
        val again = ItemDoc.decode(doc.encode())
        assertTrue(again.lightestRecheck); assertEquals("db-bench-press", again.easierOffer)
        assertNull("an unknown exercise is not offered", ItemDoc.decode(doc.copy(easierOffer = "no-such-exercise").encode()).easierOffer)
        assertFalse(ItemDoc.decode(item(false).encode()).lightestRecheck)
    }

    @Test fun `AUD-15 the plan gets each exercise's last day and the block start, so the re-check can happen`() = runBlocking {
        val (d, _) = Fixtures.data()
        Fixtures.onboard(d)
        d.docs.put(ExerciseState, ExerciseState(bench.id, e1rm = 21.0, lastDoneDay = Fixtures.MONDAY - 3))
        val t = d.planToday()!!
        val req = d.bridge.request(t.user, t.program, t.week, t.next ?: t.week.days.first(), Tier.FULL)
        assertEquals(Fixtures.MONDAY - 3, req.lastDone[bench.id])
        assertEquals(d.docs.get(ProgramRecord)!!.blockStartDay, req.blockStartDay)
        assertNotNull(req.blockStartDay)
    }
}
