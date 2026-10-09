package com.personalfitnesscoach.engine.progress

import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.Num
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds

/** A weight reading (optional, FL-005); `day` is an epoch day. */
data class WeightEntry(val day: Int, val kg: Double)

/** A waist measurement session: up to three readings in cm, averaged (FL-005). */
data class WaistEntry(val day: Int, val readingsCm: List<Double>)

data class WeightTrend(
    /** Mean of the last 7 days' readings, or null without a reading in that window. */
    val mean7: Double?,
    /** Change per week over the last 4 weeks (7-day mean now vs 4 weeks ago); null until both exist. */
    val kgPerWeek: Double?,
    /** The 7-day mean fell more than 1 kg a week for 3 weeks in a row: show the neutral check-in. */
    val rateCheckIn: Boolean,
)

/**
 * One exposure of a lift for the FL-005 strength trend: the best working set of the day (load × reps), and the e1RM after it when the
 * lift tracks one (INT-004). Bodyweight-only work (no load) is not trended here.
 */
data class StrengthPoint(val day: Int, val exerciseId: String, val load: Double, val reps: Int, val e1rm: Double? = null)

enum class TrendDirection { UP, FLAT, DOWN }

/** One lift's trend: best index in the last two weeks against the two weeks ending `trend_weeks` earlier. */
data class LiftTrend(val exerciseId: String, val now: Double, val then: Double, val changePct: Double, val direction: TrendDirection)

data class StrengthTrend(val lifts: List<LiftTrend>) {
    val improved: Int get() = lifts.count { it.direction == TrendDirection.UP }
    val flat: Int get() = lifts.count { it.direction == TrendDirection.FLAT }
    val down: Int get() = lifts.count { it.direction == TrendDirection.DOWN }
}

/** Reference line shown as information only (FL-005, South Asian waist cut-offs); never a target. */
enum class ReferenceSex { MAN, WOMAN }

/**
 * FL-005: waist and strength are the main progress measures; weight is secondary. Weight is shown as a 7-day mean and only the
 * 4-week trend is judged. No food advice anywhere.
 */
object BodyProgress {
    /** 7-day mean ending on `asOf` (inclusive). */
    fun mean7(entries: List<WeightEntry>, asOf: Int): Double? {
        val w = entries.filter { it.day in (asOf - P.FL_005.weight_mean_days + 1)..asOf }
        return if (w.isEmpty()) null else Num.round2(w.map { it.kg }.average())
    }

    fun weightTrend(entries: List<WeightEntry>, asOf: Int): EngineResult<WeightTrend> {
        val now = mean7(entries, asOf)
        val weeks = P.FL_005.trend_weeks
        val then = mean7(entries, asOf - 7 * weeks)
        val perWeek = if (now != null && then != null) Num.round2((now - then) / weeks) else null
        // Rate check-in: each of the last 3 weekly steps fell by more than 1 kg.
        val means = (0..P.FL_005.rate_flag_weeks).map { mean7(entries, asOf - 7 * it) }
        val fastLoss = means.all { it != null } && (0 until P.FL_005.rate_flag_weeks).all { k -> means[k + 1]!! - means[k]!! > P.FL_005.rate_flag_kg_week + 1e-9 }
        val t = WeightTrend(now, perWeek, fastLoss)
        val d = ArrayList<Decision>()
        d += Decision(DecisionKind.VOLUME_CHANGE, listOf(RuleIds.FL_005), ReasonKey.WEIGHT_TREND,
            inputs = mapOf("asOf" to asOf, "readings" to entries.size), outputs = mapOf("mean7" to now, "kgPerWeek" to perWeek))
        if (fastLoss) d += Decision(DecisionKind.SAFETY, listOf(RuleIds.FL_005), ReasonKey.WEIGHT_RATE_CHECK_IN,
            inputs = mapOf("weeklyMeans" to means), outputs = mapOf("checkIn" to true))
        return EngineResult(t, d)
    }

    /** Change within ±2% counts as flat. */
    private const val FLAT_PCT = 2.0
    private const val WINDOW_DAYS = 14

    /**
     * FL-005 strength trend (review R3-13): strength is a main progress measure, also without weight readings and for lifts that never
     * get an e1RM. Each exposure's index is its e1RM when there is one, else load × (1 + reps / 30) from its best set — an estimate used
     * only to compare the same lift with itself. The best index in the last 14 days is compared with the best in the 14 days ending
     * `trend_weeks` (4) weeks earlier; lifts without both windows are left out.
     */
    fun strengthTrend(points: List<StrengthPoint>, asOf: Int): EngineResult<StrengthTrend> {
        val gap = 7 * P.FL_005.trend_weeks
        fun index(x: StrengthPoint): Double? = x.e1rm ?: if (x.load > 0 && x.reps > 0) x.load * (1.0 + x.reps / 30.0) else null
        fun best(id: String, end: Int): Double? = points.filter { it.exerciseId == id && it.day in (end - WINDOW_DAYS + 1)..end }.mapNotNull(::index).maxOrNull()
        val lifts = points.map { it.exerciseId }.distinct().sorted().mapNotNull { id ->
            val now = best(id, asOf) ?: return@mapNotNull null
            val then = best(id, asOf - gap) ?: return@mapNotNull null
            val pct = Num.round1((now / then - 1.0) * 100.0)
            LiftTrend(id, Num.round1(now), Num.round1(then), pct,
                when { pct > FLAT_PCT -> TrendDirection.UP; pct < -FLAT_PCT -> TrendDirection.DOWN; else -> TrendDirection.FLAT })
        }
        val t = StrengthTrend(lifts)
        return EngineResult(t, listOf(Decision(DecisionKind.VOLUME_CHANGE, listOf(RuleIds.FL_005), ReasonKey.STRENGTH_TREND,
            inputs = mapOf("asOf" to asOf, "points" to points.size), outputs = mapOf("improved" to t.improved, "flat" to t.flat, "down" to t.down))))
    }

    /** Waist: the average of the session's readings (three are asked for; one or two are accepted and averaged). */
    fun waist(e: WaistEntry): EngineResult<Double?> {
        val r = e.readingsCm.filter { it > 0 }.take(P.FL_005.waist_readings)
        val avg = if (r.isEmpty()) null else Num.round1(r.average())
        return EngineResult(avg, listOf(Decision(DecisionKind.VOLUME_CHANGE, listOf(RuleIds.FL_005), ReasonKey.WAIST_AVERAGED,
            inputs = mapOf("readings" to r.size), outputs = mapOf("waistCm" to avg))))
    }

    /** A waist measurement is due once 2 weeks have passed since the last one (and overdue after 4); due now if never taken. */
    fun waistDue(lastDay: Int?, today: Int): Boolean = lastDay == null || today - lastDay >= 7 * P.FL_005.waist_every_weeks[0]

    fun waistOverdue(lastDay: Int?, today: Int): Boolean = lastDay != null && today - lastDay > 7 * P.FL_005.waist_every_weeks[1]

    /** The information-only reference line for the chart. */
    fun referenceLineCm(sex: ReferenceSex?): Int? = when (sex) {
        ReferenceSex.MAN -> P.FL_005.reference_lines_cm.men
        ReferenceSex.WOMAN -> P.FL_005.reference_lines_cm.women
        null -> null
    }
}
