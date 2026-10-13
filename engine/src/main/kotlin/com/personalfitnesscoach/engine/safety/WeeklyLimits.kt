package com.personalfitnesscoach.engine.safety

/**
 * The weekly limits' reference weeks and floor (VOL-007, LOAD-005; Phase 4 Part 1, D-110). PROPOSAL: Registry 2.0.0 is waiting for the
 * Product Owner's approval; until it is applied these numbers live here, then they move to the registry (P.VOL_007).
 *
 * The limits compare a week with the last three weeks. Measured against the weeks as done, a missed session made its week look light
 * and the next limit followed it down: below about 87% turnout (1 / 1.15) the limits fell week after week, until days were empty
 * (twelve-month simulations, `YearOfTrainingTest`). So a past week counts at the level of the sessions that were done, a week with
 * fewer than half of its planned sessions is left out like a missed week, and the limits never cut a week below half of its plan. Above
 * that floor, a week is never more than twice the last three weeks as done (the spike guard).
 */
object WeeklyLimits {
    /** A week with fewer than this share of its planned sessions done is not a reference week. */
    const val REFERENCE_MIN_TURNOUT_PCT = 50

    /** The limits never cut a week below this share of the training stress (SSU) it was planned with — about a deload week (DEL-003). */
    const val PLAN_FLOOR_PCT = 50

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
     * otherwise be allowed at once; this holds it to twice what was done. The floor stays allowed: half the plan is about the training
     * stress of a deload week (DEL-003). It never applies during a return or a deload, whose own ramps set the dose (REG-003/004/005).
     */
    const val SPIKE_GUARD = 2.0

    /** The spike guard starts this many days after the first workout, so the three weeks it averages are all part of the programme. */
    const val SPIKE_GUARD_AFTER_DAYS = 21

    /** The spike guard's limit from the last three weeks as done (oldest first; missed weeks 0), or null when they had no training. */
    fun spikeGuard(lastThreeDone: List<Double>): Double? {
        if (lastThreeDone.size < 3) return null
        val mean = lastThreeDone.takeLast(3).average()
        return if (mean > 0.0) SPIKE_GUARD * mean else null
    }
}
