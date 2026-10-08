package com.personalfitnesscoach.engine.calc

import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.Num
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.model.SetLog
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds

/** Estimated one-rep max, Epley with RIR adjustment (INT-004) and load selection (INT-005). */
object E1rm {
    private val divisor = P.INT_004.divisor.toDouble()

    /** e1RM from one set, or null when the set is outside the valid range (reps+RIR ≤ 12, RIR ≤ 3). */
    fun fromSet(load: Double, reps: Int, rir: Double?): Double? {
        if (rir == null || reps < 1 || load <= 0.0) return null
        if (rir > P.INT_004.max_rir) return null
        if (reps + rir > P.INT_004.max_reps_plus_rir) return null
        return load * (1.0 + (reps + rir) / divisor)
    }

    /** Best valid working-set estimate in a session. */
    fun sessionBest(sets: List<SetLog>): Double? =
        sets.filter { !it.warmup }.mapNotNull { fromSet(it.load, it.reps, it.rir) }.maxOrNull()

    /** Smoothed update E ← E + α(S − E); a single rise is capped at +5%. */
    fun update(current: Double?, session: Double?): EngineResult<Double?> {
        if (session == null) return EngineResult(current)
        if (current == null) return EngineResult(Num.round2(session))
        val smoothed = current + P.INT_004.smoothing_alpha * (session - current)
        val capped = minOf(smoothed, current * (1.0 + P.INT_004.max_single_rise_pct / 100.0))
        return EngineResult(Num.round2(capped))
    }

    /** Raw load for a target (reps, RIR) from an e1RM, before rounding to real equipment. */
    fun loadFor(e1rm: Double, reps: Int, rir: Double): Double = e1rm / (1.0 + (reps + rir) / P.INT_005.divisor)

    /** Load for a target, rounded to the user's equipment (INT-005 + PROG-003). */
    fun prescribe(e1rm: Double, reps: Int, rir: Double, available: List<Double>): EngineResult<Double> {
        val raw = loadFor(e1rm, reps, rir)
        val chosen = PlateMath.choose(raw, available)
        return EngineResult(
            chosen,
            listOf(
                Decision(
                    DecisionKind.LOAD_PRESCRIPTION, listOf(RuleIds.INT_005, RuleIds.PROG_003), ReasonKey.LOAD_FROM_E1RM,
                    inputs = mapOf("e1rm" to Num.round2(e1rm), "reps" to reps, "rir" to rir),
                    outputs = mapOf("raw" to Num.round2(raw), "load" to chosen),
                ),
            ),
        )
    }
}
