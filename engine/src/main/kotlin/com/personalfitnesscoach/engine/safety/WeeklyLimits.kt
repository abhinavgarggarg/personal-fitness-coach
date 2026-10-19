package com.personalfitnesscoach.engine.safety

import com.personalfitnesscoach.engine.registry.P

/**
 * The weekly limits' reference weeks, floor and spike guard (VOL-007 2.0.0, LOAD-005 2.0.0; Registry 2.0.0, approved 19 Oct 2026, D-112,
 * D-113). The numbers are VOL-007's registry parameters.
 *
 * The limits compare a week with the last three weeks. Measured against the weeks as done, a missed session made its week look light
 * and the next limit followed it down: below about 87% turnout (1 / 1.15) the limits fell week after week, until days were empty
 * (twelve-month simulations, `YearOfTrainingTest`). So a past week counts at the level of the sessions that were done, a week with
 * fewer than half of its planned sessions is left out like a missed week, and the limits never cut a week below half of its plan. The
 * spike guard is above both: a week is never more than twice the last three weeks as done, floor included (independent review,
 * Phase 4 Part 1).
 */
object WeeklyLimits {
    /** A week with fewer than this share of its planned sessions done is not a reference week. */
    const val REFERENCE_MIN_TURNOUT_PCT = P.VOL_007.reference_min_turnout_pct

    /** Reference weeks are taken from this many calendar weeks before the current one at most, so an old week never sets today's limit. */
    const val REFERENCE_WINDOW_WEEKS = P.VOL_007.reference_window_weeks

    /**
     * The limits never cut a week below this share of the training stress (SSU) it was planned with (unless the spike guard is lower):
     * about the training stress of a deload week (DEL-003), though with the week's normal exercises and effort.
     */
    const val PLAN_FLOOR_PCT = P.VOL_007.plan_floor_pct

    /** One finished week as stored: sessions planned and done, its SSU and its measured load (LOAD-001). */
    data class Week(val planned: Int, val done: Int, val ssu: Double, val load: Double)

    /**
     * The reference weeks, oldest first: weeks with at least [REFERENCE_MIN_TURNOUT_PCT] of their sessions done, each counted at the level
     * of the sessions done (SSU and load × planned / done). A week with every session done, or more than planned, counts as it was.
     */
    fun reference(weeks: List<Week>): List<Week> = weeks
        .filter { it.done > 0 && it.done * 100 >= it.planned * REFERENCE_MIN_TURNOUT_PCT }
        .map { w -> if (w.planned > w.done) w.copy(ssu = w.ssu * w.planned / w.done, load = w.load * w.planned / w.done) else w }

    /** The floor for a week planned with [plannedSsu]: the weekly limits never go below it. */
    fun floorSsu(plannedSsu: Double): Double = maxOf(0.0, plannedSsu) * PLAN_FLOOR_PCT / 100.0

    /**
     * The spike guard: a week is never more than this times the mean SSU of the last three weeks as they were done (a missed week
     * counts 0). Reference weeks are counted at the level of the sessions done, so after weeks of turning up seldom a full week would
     * otherwise be allowed at once; this holds it to twice what was done, and the floor gives way to it. It never applies during a
     * return or a deload, whose own ramps set the dose (REG-003/004/005, DEL-003); the week after a lighter week is not exempt.
     */
    const val SPIKE_GUARD = P.VOL_007.spike_guard_multiplier

    /** The spike guard starts this many days after the first workout, so the three weeks it averages are all part of the programme. */
    const val SPIKE_GUARD_AFTER_DAYS = P.VOL_007.spike_guard_after_days

    /** The spike guard's limit from the last three weeks as done (oldest first; missed weeks 0), or null when they had no training. */
    fun spikeGuard(lastThreeDone: List<Double>): Double? {
        if (lastThreeDone.size < 3) return null
        val mean = lastThreeDone.takeLast(3).average()
        return if (mean > 0.0) SPIKE_GUARD * mean else null
    }

    /** The floor as it applies: never above the spike guard. */
    fun floorUnder(floor: Double, guard: Double?): Double = if (guard != null) minOf(floor, guard) else floor

    /** A week's limit with the floor and the guard: at least the floor, at most the guard — the guard wins. */
    fun bounded(limit: Double, floor: Double, guard: Double?): Double = maxOf(limit, floorUnder(floor, guard)).let { if (guard != null) minOf(it, guard) else it }
}
