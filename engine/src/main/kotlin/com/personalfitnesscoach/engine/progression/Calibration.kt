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

    /**
     * The ramp ends here: the working load is found (RIR 3, or no safe heavier step, D-038), with the starting e1RM where valid. At the
     * 5-set limit with the load still easy (RIR 4+), [stillEasy]: the next session carries on from it (CAL-001 over sessions 1–4, D-115).
     */
    data class Found(val workingLoad: Double, val startE1rm: Double?, val stillEasy: Boolean = false) : CalibrationStep()

    /** Too hard (RIR ≤ 2): stop now; next session uses this load −5%. */
    data class Stop(val nextSessionLoad: Double) : CalibrationStep()
}

/**
 * Initial load calibration with effort ratings only — no maximal lifts, nothing above RPE 8.
 * The increase uses the middle of each band (25% for RIR 5+, 12.5% for RIR 4), rounded per
 * PROG-003; this reproduces the Phase 1 worked example (20 → 25 → 30 → 32.5 kg).
 */
object Calibration {
    /** D-070: at RIR 5+ a coarse step may at most double the load. */
    private const val COARSE_STEP_MAX = 2.0

    /** D-116 (option C): free-weight coarse steps only below this age. */
    private const val FREE_WEIGHT_COARSE_AGE_BELOW = 60

    /** Starting load when the user enters known working weights: 90% of them, rounded down to real equipment. */
    fun startFromKnown(knownWorkingLoad: Double, available: List<Double>): Double {
        val target = knownWorkingLoad * P.CAL_001.known_weights_start_pct / 100.0
        return available.filter { it <= target + 1e-9 }.maxOrNull() ?: (available.minOrNull() ?: PlateMath.round(target))
    }

    /**
     * Coarse steps: selectorised machines and cables for everyone (D-070); dumbbells, kettlebells and barbells only when
     * [freeWeights] — see [freeWeightCoarseSteps] (D-116).
     */
    fun coarseStepsAllowed(loadType: com.personalfitnesscoach.engine.model.LoadType, freeWeights: Boolean = false): Boolean = when (loadType) {
        com.personalfitnesscoach.engine.model.LoadType.STACK -> true
        com.personalfitnesscoach.engine.model.LoadType.DUMBBELL, com.personalfitnesscoach.engine.model.LoadType.KETTLEBELL,
        com.personalfitnesscoach.engine.model.LoadType.BARBELL -> freeWeights
        else -> false
    }

    /**
     * D-116 (the PO's option C): free weights take a coarse step only for someone who is not a beginner, is under 60 (age known), has a
     * standard screening (not gentle mode) and no health condition. Everyone else keeps the band.
     */
    fun freeWeightCoarseSteps(level: com.personalfitnesscoach.engine.model.Level, age: Int?, flaggedScreen: Boolean, hasCondition: Boolean): Boolean =
        level != com.personalfitnesscoach.engine.model.Level.BEGINNER && age != null && age < FREE_WEIGHT_COARSE_AGE_BELOW && !flaggedScreen && !hasCondition

    /**
     * Every ramp set stops at its target reps or with this many reps in reserve, whichever comes first (D-116): CAL-001's working-load
     * rating, one rep inside RPE 8. The bands alone can't keep a set within RPE 8 — at 10 reps, +10–15% from "4 more" is about 5 reps
     * fewer, near failure — and people tend to judge themselves about a rep closer to failure than they are (Halperin 2022), so the
     * stop keeps "never above RPE 8" with a margin. The reps done and the rating then give the working load or its estimate.
     */
    const val RAMP_STOP_RIR = 3.0

    /** Conservative first guess: the lightest load the equipment allows. */
    fun startingLoad(available: List<Double>): Double? = available.minOrNull()

    /**
     * @param load the load just lifted for the target reps
     * @param targetReps the reps done, up to the target (0 = not lifted)
     * @param reportedRir the answer to "how many more could you have done?" (use 5.0 for "5+")
     * @param setsDone ramp sets completed for this exercise including this one
     * @param ceiling CAL-002: an old known number caps the ramp (the ramp never goes above it)
     * @param coarseStepsAllowed selectorised machines and cables (LoadType.STACK): at RIR 5+ a step beyond the band is taken when
     *   the equipment has nothing smaller, up to double the load (D-070). Free weights keep the band (no overshoot with a dumbbell
     *   or kettlebell, Part 1 review).
     */
    fun next(load: Double, targetReps: Int, reportedRir: Double, setsDone: Int, available: List<Double>, ceiling: Double? = null,
             coarseStepsAllowed: Boolean = false): EngineResult<CalibrationStep> {
        val inputs = mapOf("load" to load, "reps" to targetReps, "rir" to reportedRir, "setsDone" to setsDone)
        fun d(reason: ReasonKey, out: Map<String, Any?>) =
            listOf(Decision(DecisionKind.CALIBRATION, listOf(RuleIds.CAL_001, RuleIds.PROG_003), reason, inputs, out))

        // A set not lifted at all (0 reps) is a failed attempt whatever effort was given: the ramp stops (review of D-115).
        if (targetReps < 1 || reportedRir <= P.CAL_001.stop_rir_lte + 1e-9) {
            val next = Progression.lowerLoad(load * (1.0 - P.CAL_001.stop_reduction_pct / 100.0), load, available)
            return EngineResult(CalibrationStep.Stop(next), d(ReasonKey.CALIBRATION_STOPPED, mapOf("nextSessionLoad" to next)))
        }
        if (reportedRir < 4.0 - 1e-9) {
            val e = E1rm.fromSet(load, targetReps, reportedRir)
            return EngineResult(CalibrationStep.Found(load, e), d(ReasonKey.CALIBRATION_DONE, mapOf("workingLoad" to load, "e1rm" to e)))
        }
        if (setsDone >= P.CAL_001.max_ramp_sets) {
            // Ramp-set limit with the load still easy: the ramp ends for today and goes on from this load next session (D-115).
            return EngineResult(CalibrationStep.Found(load, null, stillEasy = true), d(ReasonKey.CALIBRATION_CONTINUES, mapOf("nextSessionLoad" to load)))
        }
        val band = if (reportedRir >= 5.0 - 1e-9) P.CAL_001.rir_5plus_increase_pct else P.CAL_001.rir_4_increase_pct
        val mid = (band[0] + band[1]) / 2.0
        var next = PlateMath.choose(load * (1.0 + mid / 100.0), available)
        var coarse = false
        if (next <= load + 1e-9) {
            // Rounding fell back to this load: try the next heavier one, but only if it stays inside the band
            // (+ the PROG-003 2% tolerance). Coarse dumbbell or kettlebell steps must not overshoot RPE 8.
            val up = PlateMath.nextAbove(load, available)
            val limit = load * (1.0 + band[1] / 100.0) * (1.0 + P.PROG_003.over_target_tolerance_pct / 100.0)
            next = if (up != null && up <= limit + 1e-9) up else load
            // Very light loads on a machine stack (a 5 kg step from 5 kg is +100%): at RIR 5+ the next step is taken anyway,
            // up to double the load, so calibration does not stop at a load far below a working weight (review R3-13, D-070). The next
            // set's effort rating still governs: RIR ≤ 2 stops, RIR 3 is the working load.
            if (coarseStepsAllowed && next <= load + 1e-9 && reportedRir >= 5.0 - 1e-9 && up != null && up <= load * COARSE_STEP_MAX + 1e-9) { next = up; coarse = true }
        }
        if (ceiling != null && next > ceiling + 1e-9) next = maxOf(load, available.filter { it <= ceiling + 1e-9 }.maxOrNull() ?: load)
        if (next <= load + 1e-9) {
            // No safe heavier step (top of the equipment, the next step is too big, or the CAL-002 ceiling): this is the working load.
            return EngineResult(CalibrationStep.Found(load, null), d(ReasonKey.CALIBRATION_CAPPED, mapOf("workingLoad" to load)))
        }
        return EngineResult(CalibrationStep.Continue(next), d(ReasonKey.CALIBRATION_STEP, mapOf("nextLoad" to next, "coarseStep" to coarse)))
    }
}
