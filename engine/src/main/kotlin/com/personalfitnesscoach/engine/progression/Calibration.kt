package com.personalfitnesscoach.engine.progression

import com.personalfitnesscoach.engine.calc.E1rm
import com.personalfitnesscoach.engine.calc.PlateMath
import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds

/** Outcome of one calibration set (CAL-001). */
sealed class CalibrationStep {
    /** Do another ramp set at this load. */
    data class Continue(val nextLoad: Double) : CalibrationStep()

    /** Working load found (RIR 3, or the 5-set limit was reached); starting e1RM where valid. */
    data class Found(val workingLoad: Double, val startE1rm: Double?) : CalibrationStep()

    /** Too hard (RIR ≤ 2): stop now; next session uses this load −5%. */
    data class Stop(val nextSessionLoad: Double) : CalibrationStep()
}

/**
 * Initial load calibration with effort ratings only — no maximal lifts, nothing above RPE 8.
 * The increase uses the middle of each band (25% for RIR 5+, 12.5% for RIR 4), rounded per
 * PROG-003; this reproduces the Phase 1 worked example (20 → 25 → 30 → 32.5 kg).
 */
object Calibration {
    /** Starting load when the user enters known working weights: 90% of them, rounded down to real equipment. */
    fun startFromKnown(knownWorkingLoad: Double, available: List<Double>): Double {
        val target = knownWorkingLoad * P.CAL_001.known_weights_start_pct / 100.0
        return available.filter { it <= target + 1e-9 }.maxOrNull() ?: (available.minOrNull() ?: PlateMath.round(target))
    }

    /** Conservative first guess: the lightest load the equipment allows. */
    fun startingLoad(available: List<Double>): Double? = available.minOrNull()

    /**
     * @param load the load just lifted for the target reps
     * @param reportedRir the answer to "how many more could you have done?" (use 5.0 for "5+")
     * @param setsDone ramp sets completed for this exercise including this one
     * @param ceiling CAL-002: an old known number caps the ramp (the ramp never goes above it)
     */
    fun next(load: Double, targetReps: Int, reportedRir: Double, setsDone: Int, available: List<Double>, ceiling: Double? = null): EngineResult<CalibrationStep> {
        val inputs = mapOf("load" to load, "reps" to targetReps, "rir" to reportedRir, "setsDone" to setsDone)
        fun d(reason: ReasonKey, out: Map<String, Any?>) =
            listOf(Decision(DecisionKind.CALIBRATION, listOf(RuleIds.CAL_001, RuleIds.PROG_003), reason, inputs, out))

        if (reportedRir <= P.CAL_001.stop_rir_lte + 1e-9) {
            val next = Progression.lowerLoad(load * (1.0 - P.CAL_001.stop_reduction_pct / 100.0), load, available)
            return EngineResult(CalibrationStep.Stop(next), d(ReasonKey.CALIBRATION_STOPPED, mapOf("nextSessionLoad" to next)))
        }
        if (reportedRir < 4.0 - 1e-9) {
            val e = E1rm.fromSet(load, targetReps, reportedRir)
            return EngineResult(CalibrationStep.Found(load, e), d(ReasonKey.CALIBRATION_DONE, mapOf("workingLoad" to load, "e1rm" to e)))
        }
        if (setsDone >= P.CAL_001.max_ramp_sets) {
            // Ramp-set limit: keep the last load as the working load; progression continues from there.
            return EngineResult(CalibrationStep.Found(load, null), d(ReasonKey.CALIBRATION_CAPPED, mapOf("workingLoad" to load)))
        }
        val band = if (reportedRir >= 5.0 - 1e-9) P.CAL_001.rir_5plus_increase_pct else P.CAL_001.rir_4_increase_pct
        val mid = (band[0] + band[1]) / 2.0
        var next = PlateMath.choose(load * (1.0 + mid / 100.0), available)
        if (next <= load + 1e-9) {
            // Rounding fell back to this load: try the next heavier one, but only if it stays inside the band
            // (+ the PROG-003 2% tolerance). Coarse dumbbell or kettlebell steps must not overshoot RPE 8.
            val up = PlateMath.nextAbove(load, available)
            val limit = load * (1.0 + band[1] / 100.0) * (1.0 + P.PROG_003.over_target_tolerance_pct / 100.0)
            next = if (up != null && up <= limit + 1e-9) up else load

        }
        if (ceiling != null && next > ceiling + 1e-9) next = maxOf(load, available.filter { it <= ceiling + 1e-9 }.maxOrNull() ?: load)
        if (next <= load + 1e-9) {
            // No safe heavier step (top of the equipment, the next step is too big, or the CAL-002 ceiling): this is the working load.
            return EngineResult(CalibrationStep.Found(load, null), d(ReasonKey.CALIBRATION_CAPPED, mapOf("workingLoad" to load)))
        }
        return EngineResult(CalibrationStep.Continue(next), d(ReasonKey.CALIBRATION_STEP, mapOf("nextLoad" to next)))
    }
}
