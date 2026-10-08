package com.personalfitnesscoach.engine.calc

import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.Num
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds

/** The four daily ratings, each 1–5 where 3 = normal (soreness 5 = none, stress 5 = calm). */
data class CheckIn(
    val sleep: Int,
    val energy: Int,
    val soreness: Int,
    val stress: Int,
    val sleepHours: Double? = null,
) {
    init {
        require(listOf(sleep, energy, soreness, stress).all { it in 1..5 }) { "ratings must be 1–5" }
    }
}

/** Personal baseline from the last 28 days of raw scores. */
data class Baseline(val mean: Double, val sd: Double, val count: Int) {
    companion object {
        fun of(rawScores: List<Double>): Baseline? {
            if (rawScores.size < P.RDY_002.min_checkins) return null
            val m = rawScores.average()
            val sd = Math.sqrt(rawScores.sumOf { (it - m) * (it - m) } / (rawScores.size - 1))
            return Baseline(m, sd, rawScores.size)
        }
    }
}

data class ReadinessResult(val rRaw: Double, val r: Double, val tier: Tier)

/** Readiness score and session tier (RDY-001 to RDY-007). Pain and red flags are separate gates (SAF-002, SAF-003). */
object Readiness {
    fun raw(c: CheckIn): Double = P.RDY_001.multiplier * (
        P.RDY_001.weights.sleep_quality * (c.sleep - 1) +
            P.RDY_001.weights.energy * (c.energy - 1) +
            P.RDY_001.weights.soreness * (c.soreness - 1) +
            P.RDY_001.weights.stress * (c.stress - 1)
        )

    fun personal(rRaw: Double, b: Baseline): Double {
        val z = (rRaw - b.mean) / maxOf(b.sd, P.RDY_002.min_sd.toDouble())
        val zc = Num.clamp(z, -P.RDY_002.z_clamp.toDouble(), P.RDY_002.z_clamp.toDouble())
        return P.RDY_002.pers_center + P.RDY_002.pers_scale * zc
    }

    fun blended(rRaw: Double, b: Baseline?): Double =
        if (b == null || b.count < P.RDY_002.min_checkins) rRaw
        else P.RDY_002.raw_weight * rRaw + P.RDY_002.pers_weight * personal(rRaw, b)

    fun band(r: Double): Tier = when {
        r >= P.RDY_003.FULL_min -> Tier.FULL
        r >= P.RDY_003.MODIFIED_min -> Tier.MODIFIED
        r >= P.RDY_003.LIGHT_min -> Tier.LIGHT
        else -> Tier.RECOVERY
    }

    /** Full tier decision for a check-in. `activeSignals` = number of DEL-001 fatigue signals. */
    fun tier(c: CheckIn, baseline: Baseline?, activeSignals: Int): EngineResult<ReadinessResult> {
        val d = ArrayList<Decision>()
        val rRaw = Num.round2(raw(c))
        val r = Num.round2(blended(rRaw, baseline))
        var t = band(r)
        d += Decision(DecisionKind.READINESS, listOf(RuleIds.RDY_001, RuleIds.RDY_002, RuleIds.RDY_003), ReasonKey.READINESS_SCORED,
            inputs = mapOf("sleep" to c.sleep, "energy" to c.energy, "soreness" to c.soreness, "stress" to c.stress, "baseline" to baseline?.mean),
            outputs = mapOf("rRaw" to rRaw, "r" to r, "tier" to t))
        if (rRaw < P.RDY_002.raw_floor_for_light_cap && t.ordinal > Tier.LIGHT.ordinal) {
            t = Tier.LIGHT
            d += Decision(DecisionKind.TIER_CAP, listOf(RuleIds.RDY_002), ReasonKey.TIER_FLOOR_RAW_LOW, outputs = mapOf("tier" to t))
        }
        if ((c.sleep == 1 || c.energy == 1 || c.soreness == 1) && t.ordinal > Tier.MODIFIED.ordinal) {
            t = Tier.MODIFIED
            d += Decision(DecisionKind.TIER_CAP, listOf(RuleIds.RDY_003), ReasonKey.TIER_CAPPED_BY_ITEM, outputs = mapOf("tier" to t))
        }
        val hours = c.sleepHours
        if (hours != null) {
            val cap = when {
                hours < P.RDY_005.light_cap_below_h -> Tier.LIGHT
                hours < P.RDY_005.modified_cap_below_h -> Tier.MODIFIED
                else -> Tier.FULL
            }
            if (cap.ordinal < t.ordinal) {
                t = cap
                d += Decision(DecisionKind.TIER_CAP, listOf(RuleIds.RDY_005), ReasonKey.TIER_CAPPED_BY_SLEEP,
                    inputs = mapOf("sleepHours" to hours), outputs = mapOf("tier" to t))
            }
        }
        if (activeSignals >= P.RDY_006.min_signals && t.ordinal > Tier.LIGHT.ordinal) {
            t = t.stepDown()
            d += Decision(DecisionKind.TIER_CAP, listOf(RuleIds.RDY_006, RuleIds.DEL_001), ReasonKey.TIER_STEPPED_DOWN_FATIGUE,
                inputs = mapOf("signals" to activeSignals), outputs = mapOf("tier" to t))
        }
        return EngineResult(ReadinessResult(rRaw, r, t), d)
    }

    /**
     * RDY-007: an easier tier is always allowed; a harder one only one step up, and never when a
     * safety rule, the pain gate or screening conservative mode set the limit.
     */
    fun userChoice(engineTier: Tier, requested: Tier, lockedBySafety: Boolean): EngineResult<Tier> {
        val allowed = requested.ordinal <= engineTier.ordinal ||
            (!lockedBySafety && requested.ordinal - engineTier.ordinal <= P.RDY_007.max_steps_up)
        val result = if (allowed) requested else engineTier
        val reason = if (allowed) ReasonKey.TIER_CHOSEN_BY_USER else ReasonKey.TIER_CHOICE_REFUSED
        return EngineResult(result, listOf(Decision(DecisionKind.TIER_CAP, listOf(RuleIds.RDY_007), reason,
            inputs = mapOf("engineTier" to engineTier, "requested" to requested, "locked" to lockedBySafety),
            outputs = mapOf("tier" to result))))
    }
}
