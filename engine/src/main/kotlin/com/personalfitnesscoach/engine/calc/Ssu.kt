package com.personalfitnesscoach.engine.calc

import com.personalfitnesscoach.engine.model.CostClass
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.model.Zone
import com.personalfitnesscoach.engine.registry.P

/** Systemic stress units for planning (VOL-007). */
object Ssu {
    fun cost(c: CostClass): Double = when (c) {
        CostClass.ISOLATION_OR_CORE -> P.VOL_007.C.isolation_or_core
        CostClass.MACHINE_OR_CABLE_COMPOUND -> P.VOL_007.C.machine_or_cable_compound
        CostClass.FREE_WEIGHT_COMPOUND -> P.VOL_007.C.free_weight_compound_carry_sled
        CostClass.HEAVY_BILATERAL -> P.VOL_007.C.heavy_bilateral_squat_hinge_ge80pct
    }

    /** Effort factor E: RIR 0 → 1.2, RIR 1–2 → 1.0, RIR 3–4 → 0.8, easier sets → 0 (not hard). */
    fun effort(rir: Double): Double = when {
        rir < 0.5 -> P.VOL_007.E.rir0
        rir <= 2.5 -> P.VOL_007.E.rir1_2
        rir <= 4.0 -> P.VOL_007.E.rir3_4
        else -> 0.0
    }

    fun modalityFactor(m: Modality): Double = when (m) {
        Modality.ROWER, Modality.SKIERG, Modality.ELLIPTICAL -> P.VOL_007.J.rower_skierg_elliptical
        Modality.JUMP_ROPE, Modality.BODYWEIGHT_CIRCUIT -> P.VOL_007.J.jump_rope_jumping_circuits
        else -> P.VOL_007.J.ropes_kettlebell_sled_carries
    }

    fun zonePerMinute(z: Zone): Double = when (z) {
        Zone.Z1 -> P.VOL_007.Z_per_min.Z1
        Zone.Z2 -> P.VOL_007.Z_per_min.Z2
        Zone.Z3, Zone.Z4 -> P.VOL_007.Z_per_min.Z3_work
    }

    fun sets(entries: List<Pair<CostClass, Double>>): Double = entries.sumOf { (c, rir) -> cost(c) * effort(rir) }

    /** Conditioning block: work minutes at the zone rate, interval rest at the rest rate. */
    fun conditioning(m: Modality, zone: Zone, workMinutes: Double, restMinutes: Double = 0.0): Double =
        (workMinutes * zonePerMinute(zone) + restMinutes * P.VOL_007.Z_per_min.interval_rest) * modalityFactor(m)

    fun weeklyCeiling(level: Level): Int =
        level.pick(P.VOL_007.weekly_ceiling.beginner, P.VOL_007.weekly_ceiling.intermediate, P.VOL_007.weekly_ceiling.advanced)

    /** Planning limit: level ceiling, and after 4 weeks also 1.15 × the 3-week rolling mean. */
    fun weeklyLimit(level: Level, last3WeeksActual: List<Double>, weeksOfHistory: Int): Double {
        val ceiling = weeklyCeiling(level).toDouble()
        if (weeksOfHistory < P.VOL_007.k_after_weeks || last3WeeksActual.isEmpty()) return ceiling
        return minOf(ceiling, P.VOL_007.rolling_multiplier * last3WeeksActual.average())
    }
}
