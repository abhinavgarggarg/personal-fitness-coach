package com.personalfitnesscoach.engine.calc

import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds

/** Performance trend for one main lift: its recent best and the latest exposures (oldest first). */
data class LiftTrend(val lift: String, val recentBest: Double, val latest: List<Double>)

/** Everything the six fatigue signals look at (DEL-001). Lists are oldest first. */
data class FatigueInputs(
    val lifts: List<LiftTrend> = emptyList(),
    val liftsMissedPerSession: List<Int> = emptyList(),
    val sessionRpeOverPlan: List<Double> = emptyList(),
    val readinessMean7d: Double? = null,
    val rawMean7d: Double? = null,
    val baseline: Baseline? = null,
    val sorenessRatings: List<Int> = emptyList(),
    val repeatedAche7d: Boolean = false,
    val ewmaRatio: Double? = null,
    val monotony: Double? = null,
    val runDownTaps7d: Int = 0,
)

enum class Signal { F1_PERFORMANCE, F2_EFFORT_CREEP, F3_READINESS, F4_SORENESS, F5_WORKLOAD, F6_SELF_REPORT }

object FatigueSignals {
    fun active(i: FatigueInputs): Set<Signal> {
        val s = HashSet<Signal>()
        // F1: ≥2 main lifts ≥3% below recent best on the last 2 exposures, or targets missed on ≥2 lifts in the last 2 sessions.
        val drop = 1.0 - P.DEL_001.F1.drop_pct / 100.0
        val n = P.DEL_001.F1.consecutive_exposures
        val declining = i.lifts.count { t -> t.latest.size >= n && t.latest.takeLast(n).all { it <= t.recentBest * drop } }
        val missed = i.liftsMissedPerSession.size >= n && i.liftsMissedPerSession.takeLast(n).all { it >= P.DEL_001.F1.min_lifts }
        if (declining >= P.DEL_001.F1.min_lifts || missed) s += Signal.F1_PERFORMANCE
        // F2: session RPE ≥ plan + 1.5 in 3 of the last 4 sessions.
        val (need, of) = P.DEL_001.F2.of_last.let { it[0] to it[1] }
        if (i.sessionRpeOverPlan.takeLast(of).count { it >= P.DEL_001.F2.rpe_over_plan } >= need) s += Signal.F2_EFFORT_CREEP
        // F3: 7-day readiness mean > 1 SD below baseline, or raw mean < 35 before a baseline exists.
        val b = i.baseline
        if (b != null && i.readinessMean7d != null) {
            if (i.readinessMean7d < b.mean - P.DEL_001.F3.sd_below * b.sd) s += Signal.F3_READINESS
        } else if (i.rawMean7d != null && i.rawMean7d < P.DEL_001.F3.raw_mean_below) s += Signal.F3_READINESS
        // F4: soreness ≤ 2 on 3 of the last 5 check-ins, or the same ache twice in 7 days.
        val (sNeed, sOf) = P.DEL_001.F4.of_last.let { it[0] to it[1] }
        if (i.sorenessRatings.takeLast(sOf).count { it <= P.DEL_001.F4.soreness_lte } >= sNeed || i.repeatedAche7d) s += Signal.F4_SORENESS
        // F5: EWMA ratio > 1.5 or monotony > 2.0.
        if ((i.ewmaRatio ?: 0.0) > P.DEL_001.F5.ewma_gt || (i.monotony ?: 0.0) > P.DEL_001.F5.monotony_gt) s += Signal.F5_WORKLOAD
        // F6: "I feel run down" twice in 7 days.
        if (i.runDownTaps7d >= P.DEL_001.F6.times) s += Signal.F6_SELF_REPORT
        return s
    }
}

enum class DeloadAction { NONE, LIGHTER_WEEK, DELOAD_NOW, DELOAD_AT_BLOCK_END, PIVOT_WEEK, OFFER_LIGHTER_WEEK }

data class DeloadPrescription(
    val days: Int,
    val setsFactor: Double,
    val loadFactorLow: Double,
    val loadFactorHigh: Double,
    val minRir: Int,
    val hiitAllowed: Boolean,
)

/** Deload decision, prescription and resumption (DEL-002 to DEL-004; AGE-001 lowers the trigger to 2 signals at 50+). */
object Deload {
    fun decide(
        signalsNow: Int,
        sessionsWithTwoOrMoreSignals: Int,
        atBlockEnd: Boolean,
        weeksSinceLighterWeek: Int,
        age: Int? = null,
        /** A DEL-002 lighter week has just finished: still ≥ 2 signals now means deload (required: no safe default). */
        justFinishedLighterWeek: Boolean,
    ): EngineResult<DeloadAction> {
        val deloadAt = if (age != null && age >= 50) P.AGE_001.age_50.deload_signals else P.DEL_002.deload_now_signals
        val action = when {
            signalsNow >= deloadAt -> DeloadAction.DELOAD_NOW
            justFinishedLighterWeek && signalsNow >= P.DEL_002.lighter_week_signals -> DeloadAction.DELOAD_NOW
            atBlockEnd && signalsNow >= P.DEL_002.block_end_signals -> DeloadAction.DELOAD_AT_BLOCK_END
            atBlockEnd -> DeloadAction.PIVOT_WEEK
            signalsNow >= P.DEL_002.lighter_week_signals && sessionsWithTwoOrMoreSignals >= 2 -> DeloadAction.LIGHTER_WEEK
            weeksSinceLighterWeek >= P.DEL_002.offer_after_weeks -> DeloadAction.OFFER_LIGHTER_WEEK
            else -> DeloadAction.NONE
        }
        val reason = when (action) {
            DeloadAction.DELOAD_NOW -> ReasonKey.DELOAD_NOW
            DeloadAction.LIGHTER_WEEK -> ReasonKey.LIGHTER_WEEK
            DeloadAction.DELOAD_AT_BLOCK_END -> ReasonKey.DELOAD_BLOCK_END
            DeloadAction.PIVOT_WEEK -> ReasonKey.PIVOT_WEEK
            DeloadAction.OFFER_LIGHTER_WEEK -> ReasonKey.LIGHTER_WEEK_OFFER
            DeloadAction.NONE -> null
        }
        val decisions = if (reason == null) emptyList() else listOf(
            Decision(DecisionKind.DELOAD, listOf(RuleIds.DEL_001, RuleIds.DEL_002), reason,
                inputs = mapOf("signals" to signalsNow, "blockEnd" to atBlockEnd, "weeksSinceLighter" to weeksSinceLighterWeek, "age" to age),
                outputs = mapOf("action" to action)),
        )
        return EngineResult(action, decisions)
    }

    fun prescription(): DeloadPrescription = DeloadPrescription(
        days = P.DEL_003.days[1],
        setsFactor = 1.0 - P.DEL_003.sets_reduction_pct / 100.0,
        loadFactorLow = P.DEL_003.load_pct[0] / 100.0,
        loadFactorHigh = P.DEL_003.load_pct[1] / 100.0,
        minRir = P.DEL_003.min_rir,
        hiitAllowed = P.DEL_003.hiit,
    )

    /** DEL-004: first load after a deload — 100% if no signal remains, otherwise 95%. */
    fun resumeLoadFactor(signalsRemaining: Int): Double =
        if (signalsRemaining == 0) P.DEL_004.load_pct[1] / 100.0 else P.DEL_004.load_pct[0] / 100.0
}
