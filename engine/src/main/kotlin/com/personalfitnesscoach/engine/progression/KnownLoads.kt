package com.personalfitnesscoach.engine.progression

import com.personalfitnesscoach.engine.calc.E1rm
import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.Num
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds

/**
 * A number the user entered for one exercise (CAL-002): a recent set (load × reps with reps in reserve) or a best lift
 * (counted as 0 reps in reserve). `daysAgo` is how long ago they last did the exercise.
 */
data class KnownEntry(val exerciseId: String, val loadKg: Double, val reps: Int, val rir: Double?, val bestLift: Boolean = false, val daysAgo: Int = 0) {
    val effectiveRir: Double? get() = if (bestLift) P.CAL_002.best_lift_rir.toDouble() else rir
}

/** How a known entry is used. */
sealed class KnownStart {
    /** The entry seeds the e1RM (`seedE1rm` = 90% of the reduced estimate); the first working load is INT-005 from it. */
    data class Estimate(val estimate: Double, val reductionPct: Int, val seedE1rm: Double, val firstLoad: Double) : KnownStart()

    /** The entry is too old: a normal CAL-001 ramp runs, never above this load. */
    data class Ceiling(val maxLoad: Double) : KnownStart()

    /** Not a valid entry (reps + RIR > 12, RIR > 3, no RIR, no load): calibration as usual. */
    object Invalid : KnownStart()
}

/** CAL-002: starting from the user's own numbers. */
object KnownLoads {
    /** Age bands of an entry: 0 = full value, 1 = −10%, 2 = −20%, 3 = ceiling only; 60+ or a flagged screen moves one band longer. */
    fun band(daysAgo: Int, age: Int?, flaggedScreen: Boolean): Int {
        val a = P.CAL_002.entry_age_days
        var b = when {
            daysAgo < a.full_below -> 0
            daysAgo < a.minus10_below -> 1
            daysAgo < a.minus20_below -> 2
            else -> 3
        }
        if ((age != null && age >= 60) || flaggedScreen) b = minOf(3, b + P.CAL_002.older_or_flagged_shift)
        return b
    }

    /**
     * @param targetReps / targetRir today's prescription for the slot (INT-005 turns the seed into a load)
     * @param available the loads the equipment allows
     */
    fun start(e: KnownEntry, age: Int?, flaggedScreen: Boolean, targetReps: Int, targetRir: Double, available: List<Double>): EngineResult<KnownStart> {
        val rir = e.effectiveRir
        val est = if (rir == null || e.reps + rir > P.CAL_002.valid_reps_plus_rir_max + 1e-9) null else E1rm.fromSet(e.loadKg, e.reps, rir)
        val inputs = mapOf("exercise" to e.exerciseId, "load" to e.loadKg, "reps" to e.reps, "rir" to rir, "bestLift" to e.bestLift, "daysAgo" to e.daysAgo,
            "age" to age, "flagged" to flaggedScreen)
        if (est == null || available.isEmpty()) return EngineResult(KnownStart.Invalid, listOf(Decision(DecisionKind.CALIBRATION,
            listOf(RuleIds.CAL_002, RuleIds.INT_004), ReasonKey.KNOWN_LOAD_IGNORED, inputs)))
        val pct = P.CAL_002.start_pct_of_e1rm / 100.0
        val b = band(e.daysAgo, age, flaggedScreen)
        if (b == 3) {
            val ceiling = E1rm.prescribe(est * pct, targetReps, targetRir, available).value
            return EngineResult(KnownStart.Ceiling(ceiling), listOf(Decision(DecisionKind.CALIBRATION, listOf(RuleIds.CAL_002, RuleIds.CAL_001),
                ReasonKey.KNOWN_LOAD_USED, inputs, mapOf("band" to b, "ceiling" to ceiling))))
        }
        val reduction = when (b) { 1 -> P.CAL_002.reduction_pct.minus10; 2 -> P.CAL_002.reduction_pct.minus20; else -> 0 }
        val reduced = est * (1.0 - reduction / 100.0)
        val seed = Num.round2(reduced * pct)
        val first = E1rm.prescribe(seed, targetReps, targetRir, available)
        return EngineResult(KnownStart.Estimate(Num.round2(reduced), reduction, seed, first.value), first.decisions + Decision(DecisionKind.CALIBRATION,
            listOf(RuleIds.CAL_002, RuleIds.CAL_001, RuleIds.INT_004, RuleIds.INT_005), ReasonKey.KNOWN_LOAD_USED, inputs,
            mapOf("band" to b, "estimate" to Num.round2(est), "reductionPct" to reduction, "seed" to seed, "firstLoad" to first.value)))
    }
}
