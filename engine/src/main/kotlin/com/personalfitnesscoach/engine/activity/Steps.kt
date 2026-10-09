package com.personalfitnesscoach.engine.activity

import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.Num
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds

/** One day's step count from the phone's step counter (D-062); `day` is an epoch day. */
data class DaySteps(val day: Int, val steps: Int)

/**
 * The step target's state between weeks (STEP-001). `baseline` is null until 7 valid days exist; `lowWeeks` counts weeks
 * in a row with the target met on ≤ 2 days.
 */
data class StepState(val baseline: Int? = null, val target: Int? = null, val lowWeeks: Int = 0)

/** STEP-001: a daily step target by age band, reached gradually from the user's own baseline. */
object StepTarget {
    private enum class Band { UNDER_50, AGE_50_59, AGE_60_64, AGE_65_PLUS }

    private fun band(age: Int?): Band = when {
        age == null || age < 50 -> Band.UNDER_50
        age < 60 -> Band.AGE_50_59
        age < 65 -> Band.AGE_60_64
        else -> Band.AGE_65_PLUS
    }

    /** The band's target (9,000 / 8,000 / 7,500 / 7,000). */
    fun bandTarget(age: Int?): Int = when (band(age)) {
        Band.UNDER_50 -> P.STEP_001.targets.age_30_49; Band.AGE_50_59 -> P.STEP_001.targets.age_50_59
        Band.AGE_60_64 -> P.STEP_001.targets.age_60_64; Band.AGE_65_PLUS -> P.STEP_001.targets.age_65_plus
    }

    /** The band's range, shown with the target. */
    fun bandRange(age: Int?): IntRange {
        val r = when (band(age)) {
            Band.UNDER_50 -> P.STEP_001.ranges.age_30_49; Band.AGE_50_59 -> P.STEP_001.ranges.age_50_59
            Band.AGE_60_64 -> P.STEP_001.ranges.age_60_64; Band.AGE_65_PLUS -> P.STEP_001.ranges.age_65_plus
        }
        return r[0]..r[1]
    }

    private fun p(age: Int?): Double = if (band(age) == Band.UNDER_50) P.STEP_001.p.under_50 else P.STEP_001.p.from_50

    private fun cap(age: Int?): Int = when (band(age)) {
        Band.UNDER_50 -> P.STEP_001.increment_cap.under_50; Band.AGE_50_59 -> P.STEP_001.increment_cap.age_50_59
        Band.AGE_60_64 -> P.STEP_001.increment_cap.age_60_64; Band.AGE_65_PLUS -> P.STEP_001.increment_cap.age_65_plus
    }

    fun roundTo(x: Double): Int = (Math.round(x / P.STEP_001.round_to) * P.STEP_001.round_to).toInt()

    /** A day counts once the phone recorded ≥ 500 steps (otherwise it was probably not carried). */
    fun valid(d: DaySteps): Boolean = d.steps >= P.STEP_001.valid_day_min_steps

    /** Baseline: the median of the first 7 valid days, rounded to 50; null until there are 7. */
    fun baseline(days: List<DaySteps>): Int? {
        val first = days.filter { valid(it) }.sortedBy { it.day }.take(P.STEP_001.baseline_days)
        if (first.size < P.STEP_001.baseline_days) return null
        val s = first.map { it.steps }.sorted()
        val median = if (s.size % 2 == 1) s[s.size / 2].toDouble() else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
        return roundTo(median)
    }

    /** The weekly step increment: p × last week's mean, rounded to 50, between 250 and the band's cap. */
    fun increment(age: Int?, lastWeekMean: Double): Int = roundTo(p(age) * lastWeekMean).coerceIn(P.STEP_001.increment_min, cap(age))

    /**
     * Start the target once the baseline exists: the baseline, never above the band target. Before that the state is kept and
     * the app just shows the steps (STEP_BASELINE_PENDING).
     */
    fun start(state: StepState, days: List<DaySteps>, age: Int?): EngineResult<StepState> {
        if (state.target != null) return EngineResult(state)
        val b = baseline(days) ?: return EngineResult(state, listOf(Decision(DecisionKind.VOLUME_CHANGE, listOf(RuleIds.STEP_001),
            ReasonKey.STEP_BASELINE_PENDING, outputs = mapOf("validDays" to days.count { valid(it) }))))
        val t = minOf(b, bandTarget(age))
        return EngineResult(StepState(b, t, 0), listOf(Decision(DecisionKind.VOLUME_CHANGE, listOf(RuleIds.STEP_001), ReasonKey.STEP_TARGET_SET,
            inputs = mapOf("baseline" to b, "age" to age), outputs = mapOf("target" to t, "bandTarget" to bandTarget(age)))))
    }

    /**
     * STEP-001 weekly update from last week's 7 days. Rise by one increment when the target was met on ≥ 5 days (never in a
     * deload week or with a lower-limb pain flag, never above the band target); hold at 3–4 days; after 2 weeks in a row with
     * ≤ 2 days met, lower by one increment (never below the baseline).
     */
    fun nextWeek(state: StepState, lastWeek: List<DaySteps>, age: Int?, deloadWeek: Boolean = false, lowerLimbPain: Boolean = false): EngineResult<StepState> {
        val target = state.target ?: return EngineResult(state)
        val validDays = lastWeek.filter { valid(it) }
        val mean = if (validDays.isEmpty()) 0.0 else validDays.map { it.steps }.average()
        val met = lastWeek.count { it.steps >= target }
        val inc = increment(age, mean)
        val band = bandTarget(age)
        val (next, reason) = when {
            met >= P.STEP_001.advance_days_met && !deloadWeek && !lowerLimbPain && target < band ->
                StepState(state.baseline, minOf(band, target + inc), 0) to ReasonKey.STEP_TARGET_UP
            met >= P.STEP_001.hold_days_met[0] -> StepState(state.baseline, target, 0) to ReasonKey.STEP_TARGET_HELD
            met <= P.STEP_001.lower_if_days_met_lte && state.lowWeeks + 1 >= P.STEP_001.lower_after_weeks ->
                StepState(state.baseline, maxOf(state.baseline ?: 0, target - inc), 0) to ReasonKey.STEP_TARGET_DOWN
            met <= P.STEP_001.lower_if_days_met_lte -> StepState(state.baseline, target, state.lowWeeks + 1) to ReasonKey.STEP_TARGET_HELD
            else -> StepState(state.baseline, target, 0) to ReasonKey.STEP_TARGET_HELD
        }
        return EngineResult(next, listOf(Decision(DecisionKind.VOLUME_CHANGE, listOf(RuleIds.STEP_001), reason,
            inputs = mapOf("target" to target, "daysMet" to met, "mean" to Num.round1(mean), "deload" to deloadWeek, "lowerLimbPain" to lowerLimbPain),
            outputs = mapOf("target" to next.target, "increment" to inc, "lowWeeks" to next.lowWeeks))))
    }
}

/** A walk the user logged themselves (STEP-002 allows it): start as minute of the day, and length in minutes. */
data class LoggedWalk(val startMinute: Int, val minutes: Int, val brisk: Boolean = true)

/** STEP-002: only brisk-walk bouts count as Z1 minutes, so walking is never counted twice. */
object BriskWalks {
    /**
     * Bouts in a day's minute-by-minute step counts (index = minute of the day): runs of ≥ 10 minutes in a row at
     * ≥ 100 steps/min. Returned as minute ranges.
     */
    fun bouts(stepsPerMinute: List<Int>): List<IntRange> {
        val out = ArrayList<IntRange>()
        var start = -1
        for (m in 0..stepsPerMinute.size) {
            val brisk = m < stepsPerMinute.size && stepsPerMinute[m] >= P.STEP_002.cadence_min_steps_per_min
            if (brisk && start < 0) start = m
            if (!brisk && start >= 0) {
                if (m - start >= P.STEP_002.bout_min_minutes) out += start until m
                start = -1
            }
        }
        return out
    }

    /**
     * The day's Z1 walking minutes: detected bouts plus brisk walks the user logged, overlaps counted once. Logged walks shorter
     * than 10 minutes don't count, the same as detected ones.
     */
    fun z1Minutes(stepsPerMinute: List<Int>, logged: List<LoggedWalk> = emptyList()): EngineResult<Double> {
        val ranges = bouts(stepsPerMinute) + logged.filter { it.brisk && it.minutes >= P.STEP_002.bout_min_minutes }
            .map { it.startMinute until it.startMinute + it.minutes }
        val minutes = HashSet<Int>()
        for (r in ranges) minutes.addAll(r)
        val total = minutes.size.toDouble()
        return EngineResult(total, listOf(Decision(DecisionKind.VOLUME_CHANGE, listOf(RuleIds.STEP_002, RuleIds.PH_001), ReasonKey.BRISK_WALKS_COUNTED,
            inputs = mapOf("detectedBouts" to bouts(stepsPerMinute).size, "loggedWalks" to logged.size), outputs = mapOf("z1Minutes" to total))))
    }
}
