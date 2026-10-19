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

    /** Dumbbells, kettlebells and barbells (a big step on them goes by [rangeBelow] the count given, D-116). */
    fun isFreeWeight(loadType: com.personalfitnesscoach.engine.model.LoadType): Boolean = loadType == com.personalfitnesscoach.engine.model.LoadType.DUMBBELL ||
        loadType == com.personalfitnesscoach.engine.model.LoadType.KETTLEBELL || loadType == com.personalfitnesscoach.engine.model.LoadType.BARBELL

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

    /**
     * The answers to "about how many more?" on a set where the next weight could only be a big step: the lower ends of 5–9, 10–19,
     * 20–29, 30–49 and 50 or more. A plain "5+" may mean exactly 5, and at 10 reps that makes the one-rep max as low as 1.5 × the load, so a
     * doubled weight could be beyond it (review of D-116): the count is what lets a big step be taken safely.
     */
    val MORE_CHOICES: List<Double> = listOf(5.0, 10.0, 20.0, 30.0, 50.0)

    /**
     * On free weights a big step goes by the range below the one chosen ("50 or more" counts as 30, "30–49" as 20 …): a count one
     * range too high must still leave the first reps well short of the maximum (re-check of D-116). A machine stack, which a set can
     * be stopped on safely, goes by the range chosen.
     */
    fun rangeBelow(count: Double): Double = MORE_CHOICES.lastOrNull { it < count - 1e-9 } ?: 0.0

    /** A big step leaves, on the person's own count, at least this many reps before the stop at [RAMP_STOP_RIR] (review of D-116). */
    const val BIG_STEP_MIN_REPS = 2

    /**
     * The heaviest load a big step may reach after [reps] done with at least [rir] more: the one-rep max that count implies at the least
     * (Epley), less [BIG_STEP_MIN_REPS] + [RAMP_STOP_RIR] reps. After "5+" at 10 reps that is +29%, inside the band anyway; "30 or
     * more" allows a doubled weight.
     */
    fun bigStepLimit(load: Double, reps: Int, rir: Double): Double =
        load * (1.0 + (reps + rir) / P.INT_004.divisor) / (1.0 + (BIG_STEP_MIN_REPS + RAMP_STOP_RIR) / P.INT_004.divisor)

    /**
     * Whether the next ramp set after [load] could only be a big step (no heavier step inside the band, the next one at most double):
     * the screen then asks "about how many more?" instead of "5+".
     */
    fun bigStepAhead(load: Double?, available: List<Double>, coarseStepsAllowed: Boolean, ceiling: Double? = null): Boolean {
        if (!coarseStepsAllowed || load == null) return false
        if (ceiling != null && (PlateMath.nextAbove(load, available) ?: return false) > ceiling + 1e-9) return false
        val band = P.CAL_001.rir_5plus_increase_pct
        if (PlateMath.choose(load * (1.0 + (band[0] + band[1]) / 200.0), available) > load + 1e-9) return false
        val up = PlateMath.nextAbove(load, available) ?: return false
        return up > load * (1.0 + band[1] / 100.0) * (1.0 + P.PROG_003.over_target_tolerance_pct / 100.0) + 1e-9 && up <= load * COARSE_STEP_MAX + 1e-9
    }

    /** Conservative first guess: the lightest load the equipment allows. */
    fun startingLoad(available: List<Double>): Double? = available.minOrNull()

    /**
     * @param load the load just lifted for the target reps
     * @param targetReps the reps done, up to the target (0 = not lifted)
     * @param reportedRir the answer to "how many more could you have done?" (use 5.0 for "5+")
     * @param setsDone ramp sets completed for this exercise including this one
     * @param ceiling CAL-002: an old known number caps the ramp (the ramp never goes above it)
     * @param coarseStepsAllowed machine stacks (D-070), and free weights outside the risk groups (D-116): when the equipment has
     *   nothing inside the band, a bigger step up to double the load — only as far as [bigStepLimit] allows on the person's count.
     * @param askedReps the reps the set asked for: a set stopped short of them ends the ramp (review of D-116)
     * @param freeWeight a dumbbell, kettlebell or barbell: a big step goes by [rangeBelow] the count given
     */
    fun next(load: Double, targetReps: Int, reportedRir: Double, setsDone: Int, available: List<Double>, ceiling: Double? = null,
             coarseStepsAllowed: Boolean = false, askedReps: Int = targetReps, freeWeight: Boolean = false): EngineResult<CalibrationStep> {
        val inputs = mapOf("load" to load, "reps" to targetReps, "rir" to reportedRir, "setsDone" to setsDone)
        fun d(reason: ReasonKey, out: Map<String, Any?>) =
            listOf(Decision(DecisionKind.CALIBRATION, listOf(RuleIds.CAL_001, RuleIds.PROG_003), reason, inputs, out))

        // A set not lifted at all (0 reps) is a failed attempt whatever effort was given: the ramp stops (review of D-115).
        if (targetReps < 1 || reportedRir <= P.CAL_001.stop_rir_lte + 1e-9) {
            val next = Progression.lowerLoad(load * (1.0 - P.CAL_001.stop_reduction_pct / 100.0), load, available)
            return EngineResult(CalibrationStep.Stop(next), d(ReasonKey.CALIBRATION_STOPPED, mapOf("nextSessionLoad" to next)))
        }
        // Stopped short of the reps asked for (the stop at about 3 left): the weight is found, whatever more was rated — the estimate
        // goes by the stop's 3 at most, so a set stopped early is never followed by a step sized for a full one (review of D-116).
        if (reportedRir < 4.0 - 1e-9 || targetReps < askedReps) {
            val e = E1rm.fromSet(load, targetReps, minOf(reportedRir, RAMP_STOP_RIR))
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
            // Very light loads (a 5 kg step from 5 kg is +100%): at RIR 5+ the next step is taken anyway, up to double the load, so
            // calibration does not stop far below a working weight (review R3-13, D-070, D-116) — but only as far as the person's count
            // of reps left supports with room to spare ([bigStepLimit]); a plain "5+" never allows it. The next set still stops at
            // about 3 left, and its rating governs.
            if (coarseStepsAllowed && next <= load + 1e-9 && reportedRir >= 5.0 - 1e-9 && up != null && up <= load * COARSE_STEP_MAX + 1e-9 &&
                up <= bigStepLimit(load, targetReps, if (freeWeight) rangeBelow(reportedRir) else reportedRir) + 1e-9) { next = up; coarse = true }
        }
        if (ceiling != null && next > ceiling + 1e-9) next = maxOf(load, available.filter { it <= ceiling + 1e-9 }.maxOrNull() ?: load)
        if (next <= load + 1e-9) {
            // No safe heavier step (top of the equipment, the next step is too big, or the CAL-002 ceiling): this is the working load.
            return EngineResult(CalibrationStep.Found(load, null), d(ReasonKey.CALIBRATION_CAPPED, mapOf("workingLoad" to load)))
        }
        return EngineResult(CalibrationStep.Continue(next), d(ReasonKey.CALIBRATION_STEP, mapOf("nextLoad" to next, "coarseStep" to coarse)))
    }
}
