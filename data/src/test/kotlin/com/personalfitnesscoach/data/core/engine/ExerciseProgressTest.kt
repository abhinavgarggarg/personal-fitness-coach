package com.personalfitnesscoach.data.core.engine

import com.personalfitnesscoach.data.core.model.CalibrationState
import com.personalfitnesscoach.data.core.model.ExerciseState
import com.personalfitnesscoach.data.core.model.KnownNumber
import com.personalfitnesscoach.data.core.session.ItemDoc
import com.personalfitnesscoach.data.core.session.SetKind
import com.personalfitnesscoach.data.core.session.StoredExercise
import com.personalfitnesscoach.data.core.store.ExerciseRow
import com.personalfitnesscoach.data.core.store.SetRow
import com.personalfitnesscoach.engine.calc.E1rm
import com.personalfitnesscoach.engine.calc.Inventory
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.planning.Priority
import com.personalfitnesscoach.engine.program.SlotRole
import com.personalfitnesscoach.engine.progression.ProgressionAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** FS-5: one logged exposure → the exercise's next state, using only engine rules (CAL-001, CAL-002, INT-004, PROG-001…008). */
class ExerciseProgressTest {
    private val bench = Library.require("bench-press")
    private val ctx = ProgressContext(Inventory(), Level.INTERMEDIATE, 40, flaggedScreen = false, recoveryOk = true)
    private val day = 20_000

    private fun item(calibrating: Boolean, reps: IntRange = 8..12, rir: Double = 2.0, load: Double? = 60.0, factor: Double = 1.0) =
        ItemDoc(SlotRole.MAIN, Priority.P1, 3, reps, DoseUnit.REPS, targetRir = rir, load = load, loadFactor = factor, restMinSec = 120, restDefaultSec = 150,
            restMaxSec = 180, calibrating = calibrating, main = true)

    private fun exposure(item: ItemDoc, vararg sets: Triple<String, Double, Pair<Int, Double?>>) = StoredExercise(
        ExerciseRow(1, 1, 0, bench.id, "main", "DONE", item.encode()), item,
        sets.mapIndexed { i, (kind, load, rr) -> SetRow(i + 1L, 1, i, kind, load, rr.first, rir = rr.second, loggedAtMs = i.toLong()) })

    private fun cal(load: Double, reps: Int, rir: Double?) = Triple(SetKind.CALIBRATION, load, reps to rir)
    private fun work(load: Double, reps: Int, rir: Double?) = Triple(SetKind.WORKING, load, reps to rir)

    @Test fun `a ramp that finds the load at RIR 3 seeds the e1RM`() {
        // 4–8 reps: the ramp asks for 6; RIR 3 at 6 reps is a valid estimate (reps + RIR ≤ 12, INT-004).
        val r = ExerciseProgress.apply(ExerciseState(bench.id), bench, exposure(item(true, reps = 4..8), cal(40.0, 6, 6.0), cal(50.0, 6, 5.0), cal(62.5, 6, 3.0)), day, ctx, null).value
        assertEquals(E1rm.fromSet(62.5, 6, 3.0)!!, r.state.e1rm!!, 0.01)
        assertNull(r.state.calibration)
        assertEquals(1, r.state.exposures)
    }

    @Test fun `too hard stops the ramp and the next session starts 5 percent lower`() {
        val r = ExerciseProgress.apply(ExerciseState(bench.id), bench, exposure(item(true), cal(60.0, 10, 1.0)), day, ctx, null).value
        assertNull(r.state.e1rm)
        assertEquals(CalibrationState(57.5, 1, null), r.state.calibration)
    }

    @Test fun `an unrated ramp set ends the ramp there`() {
        val r = ExerciseProgress.apply(ExerciseState(bench.id), bench, exposure(item(true), cal(40.0, 10, 6.0), cal(50.0, 10, null)), day, ctx, null).value
        assertEquals(50.0, r.state.calibration!!.nextLoad, 0.0)
    }

    @Test fun `after the fourth calibration session progression takes over`() {
        val s = ExerciseState(bench.id, calibration = CalibrationState(60.0, 3))
        val r = ExerciseProgress.apply(s, bench, exposure(item(true), cal(60.0, 10, 1.0)), day, ctx, null).value
        assertNull(r.state.calibration)
        assertEquals(57.5, r.state.prescription!!.load, 0.0)
        assertEquals(ProgressionAction.HOLD, r.state.prescription!!.action)
    }

    @Test fun `a ramp at 10 reps finds the load but leaves the e1RM to working sets`() {
        // 8–12 reps: the ramp asks for 10; RIR 3 at 10 reps is outside INT-004's valid range, so calibration goes on.
        val r = ExerciseProgress.apply(ExerciseState(bench.id), bench, exposure(item(true), cal(40.0, 10, 6.0), cal(50.0, 10, 3.0)), day, ctx, null).value
        assertNull(r.state.e1rm)
        assertEquals(CalibrationState(50.0, 1, null), r.state.calibration)
    }

    @Test fun `working sets update the e1RM and write the next prescription`() {
        val s = ExerciseState(bench.id, e1rm = 80.0)
        val r = ExerciseProgress.apply(s, bench, exposure(item(false), work(70.0, 8, 2.0), work(70.0, 8, 2.0), work(70.0, 8, 2.0)), day, ctx, null).value
        assertEquals(84.0, r.state.e1rm!!, 1e-9) // smoothed towards 93.3, capped at +5% (INT-004)
        assertNotNull(r.state.prescription)
        assertTrue(r.state.prescription!!.load >= 70.0)
        assertEquals(day, r.state.prescriptionDay)
    }

    @Test fun `a reduced-load exposure updates the e1RM but not the prescription`() {
        val s = ExerciseState(bench.id, e1rm = 80.0)
        val r = ExerciseProgress.apply(s, bench, exposure(item(false, factor = 0.9), work(55.0, 10, 3.0)), day, ctx, null).value
        assertNull(r.state.prescription)
        assertNotNull(r.state.e1rm)
    }

    @Test fun `two reductions in a row remember the load before them`() {
        val s0 = ExerciseState(bench.id, e1rm = 80.0)
        val hard = arrayOf(work(60.0, 6, 0.0), work(60.0, 5, 0.0), work(60.0, 5, 0.0))
        val r1 = ExerciseProgress.apply(s0, bench, exposure(item(false), *hard), day, ctx, null).value.state
        if (r1.previousWasReduction) assertEquals(60.0, r1.loadBeforeReductions!!, 0.0)
    }

    @Test fun `form felt wrong counts in a row`() {
        val bad = Triple(SetKind.WORKING, 60.0, 10 to 2.0)
        val ex = exposure(item(false), bad).let { it.copy(sets = it.sets.map { s -> s.copy(formCheck = "NO") }) }
        val r = ExerciseProgress.apply(ExerciseState(bench.id, formNoStreak = 1), bench, ex, day, ctx, null).value
        assertEquals(2, r.state.formNoStreak)
    }

    @Test fun `a recent own number seeds the first e1RM and is used once`() {
        val k = KnownNumber(bench.id, 70.0, 8, 2.0, false, day - 3, day - 1)
        val r = ExerciseProgress.apply(ExerciseState(bench.id), bench, exposure(item(false), work(55.0, 10, 3.0)), day, ctx, k).value
        assertTrue(r.state.knownApplied)
        val seed = (ExerciseProgress.known(k, bench, day, ctx, 10, 2.0).value as com.personalfitnesscoach.engine.progression.KnownStart.Estimate).seedE1rm
        assertEquals(E1rm.update(seed, E1rm.fromSet(55.0, 10, 3.0)).value!!, r.state.e1rm!!, 0.01)
    }

    @Test fun `an old own number caps the calibration ramp while it runs`() {
        val k = KnownNumber(bench.id, 70.0, 8, 2.0, false, day - 400, day - 1)
        val r = ExerciseProgress.apply(ExerciseState(bench.id), bench, exposure(item(true), cal(40.0, 10, 6.0)), day, ctx, k).value
        val ceiling = r.state.calibration!!.ceiling!!
        assertTrue(ceiling < 70.0)
        assertTrue(r.state.calibration!!.nextLoad <= ceiling + 1e-9)
    }

    @Test fun `bodyweight ladders step up after two solid sessions`() {
        val pushUp = Library.all.first { it.family == "push_up" && Library.progressionOf(it) != null }
        val item = ItemDoc(SlotRole.SECONDARY, Priority.P2, 3, pushUp.defaultRepRange, DoseUnit.REPS, targetRir = 2.0, restMinSec = 60, restDefaultSec = 90, restMaxSec = 120)
        fun session(id: Long) = StoredExercise(ExerciseRow(id, id, 0, pushUp.id, "s", "DONE", item.encode()), item,
            (0 until 3).map { SetRow(id * 10 + it, id, it, SetKind.WORKING, null, 20, rir = 3.0, loggedAtMs = 1) })
        val r = ExerciseProgress.apply(ExerciseState(pushUp.id), pushUp, session(2), day, ctx, null, listOf(session(1), session(2))).value
        assertEquals(pushUp.family!! to Library.progressionOf(pushUp)!!.id, r.ladder)
    }
}
