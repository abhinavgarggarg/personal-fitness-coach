package com.personalfitnesscoach.engine.calc

import com.personalfitnesscoach.engine.model.SetLog
import com.personalfitnesscoach.engine.registry.P

/** RPE ↔ RIR (INT-001) and the hard-set definition (VOL-001). */
object Effort {
    /** RIR for a working-set RPE; null outside the valid 6–10 range (too imprecise to use). */
    fun rirFromRpe(rpe: Double): Double? {
        if (rpe < P.INT_001.valid_rpe_min || rpe > P.INT_001.valid_rpe_max) return null
        return if (rpe >= 9.5) 0.0 else 10.0 - rpe
    }

    fun rpeFromRir(rir: Double): Double = (10.0 - rir).coerceIn(P.INT_001.valid_rpe_min.toDouble(), 10.0)

    /**
     * A hard set ends within 4 reps of failure. Unrated working sets are treated as hard
     * (they were prescribed at a hard-set RIR), warm-ups never count.
     */
    fun isHardSet(set: SetLog): Boolean =
        !set.warmup && (set.rir == null || set.rir <= P.VOL_001.max_rir)
}
