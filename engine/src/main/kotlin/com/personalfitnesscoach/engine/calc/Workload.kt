package com.personalfitnesscoach.engine.calc

import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds

data class Ewma(val acute: Double, val chronic: Double) {
    val ratio: Double? get() = if (chronic > 0.0) acute / chronic else null
}

/** Session-RPE workload (LOAD-001 to LOAD-006). Daily loads are oldest first, rest days as 0. */
object Workload {
    /** LOAD-001: session RPE (CR10) × minutes, with pauses longer than 10 min removed by the caller. */
    fun sessionLoad(sessionRpe: Double, minutes: Double): Double {
        require(sessionRpe in 0.0..10.0) { "session RPE is 0–10" }
        return sessionRpe * minutes
    }

    fun weekly(last7: List<Double>): Double = last7.takeLast(7).sum()

    /** LOAD-003: mean ÷ sample SD over 7 days; null when the SD is zero. */
    fun monotony(last7: List<Double>): Double? {
        val w = last7.takeLast(7)
        if (w.size < 2) return null
        val m = w.average()
        val sd = Math.sqrt(w.sumOf { (it - m) * (it - m) } / (w.size - 1))
        return if (sd <= 1e-9) null else m / sd
    }

    fun strain(last7: List<Double>): Double? = monotony(last7)?.let { weekly(last7) * it }

    /** LOAD-004: exponentially weighted moving averages, seeded with the first day. */
    fun ewma(daily: List<Double>): Ewma? {
        if (daily.isEmpty()) return null
        var a = daily.first()
        var c = daily.first()
        for (x in daily.drop(1)) {
            a = P.LOAD_004.acute_lambda * x + (1 - P.LOAD_004.acute_lambda) * a
            c = P.LOAD_004.chronic_lambda * x + (1 - P.LOAD_004.chronic_lambda) * c
        }
        return Ewma(a, c)
    }

    /** Soft flags only (never an injury claim): EWMA ratio > 1.5 after 28 days, monotony > 2.0. */
    fun flags(daily: List<Double>): List<Decision> {
        val out = ArrayList<Decision>()
        if (daily.size >= P.LOAD_004.min_days) {
            val r = ewma(daily)?.ratio
            if (r != null && r > P.LOAD_004.flag_gt) {
                out += Decision(DecisionKind.WORKLOAD_FLAG, listOf(RuleIds.LOAD_004), ReasonKey.WORKLOAD_SPIKE_FLAG, outputs = mapOf("ratio" to r))
            }
        }
        val mono = monotony(daily)
        if (mono != null && mono > P.LOAD_003.monotony_flag) {
            out += Decision(DecisionKind.WORKLOAD_FLAG, listOf(RuleIds.LOAD_003), ReasonKey.MONOTONY_FLAG, outputs = mapOf("monotony" to mono))
        }
        return out
    }

    /**
     * LOAD-005/LOAD-006: highest predicted weekly load the planner may prescribe, or null when
     * there is not enough history (< 14 days: only per-session caps apply).
     * `completedWeeks` are weekly loads, oldest first.
     */
    fun planningCap(completedWeeks: List<Double>, daysOfHistory: Int): Double? {
        if (daysOfHistory < P.LOAD_006.phase1_days || completedWeeks.isEmpty()) return null
        return if (completedWeeks.size >= 4) {
            P.LOAD_005.multiplier * completedWeeks.takeLast(P.LOAD_005.history_weeks).average()
        } else {
            completedWeeks.last() * (1.0 + P.LOAD_005.early_week_over_week_pct / 100.0)
        }
    }
}
