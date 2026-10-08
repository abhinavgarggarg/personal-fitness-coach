package com.personalfitnesscoach.engine.progression

import com.personalfitnesscoach.engine.calc.PlateMath
import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.Num
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.model.Exercise
import com.personalfitnesscoach.engine.model.FormCheck
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.model.SetLog
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds

enum class ProgressionAction { LOAD_UP, REPS_UP, RANGE_EXTENDED, LOAD_JUMP, HOLD, REDUCE, REDUCE_AND_REVIEW }

/** What the next exposure of one exercise should look like. */
data class Prescription(
    val load: Double,
    val repRange: IntRange,
    /** Rep goal for the next exposure (bottom of the range after a normal load step). */
    val repTarget: Int,
    val action: ProgressionAction,
    /** PROG-008: the coach should offer an easier variation. */
    val offerRegression: Boolean = false,
)

/** The last exposure of one exercise, plus the context the progression rules need. */
data class ExposureInput(
    val exercise: Exercise,
    val repRange: IntRange,
    val targetRir: Double,
    val load: Double,
    val sets: List<SetLog>,
    /** Every load the user's equipment can make for this exercise (PlateMath.loads). */
    val available: List<Double>,
    /** Tier FULL or MODIFIED and fewer than 2 fatigue signals (PROG-001). */
    val recoveryOk: Boolean,
    /** The previous exposure ended in a PROG-004 reduction. */
    val previousWasReduction: Boolean = false,
    /** Load before the first of two consecutive reductions (for the -10% case). */
    val loadBeforeReductions: Double? = null,
    /** "Form felt solid?" answered "no" on this many consecutive exposures, including this one. */
    val consecutiveFormNo: Int = 0,
    /** For the PROG-007 / SAF-005 intensity cap on load jumps (AGE-001 lowers it at 65+). */
    val level: Level = Level.INTERMEDIATE,
    val age: Int? = null,
)

/**
 * Progressive overload (PROG-001 to PROG-004, PROG-008), following the Phase 1 pseudocode
 * `next_prescription`. Effort is handled as RIR internally: RPE = 10 − RIR (INT-001), so
 * "mean RPE ≤ target + 0.5" is "mean RIR ≥ target RIR − 0.5". Sets without a rating count as
 * on target. Every return carries one Decision with the rule IDs and a reason key.
 */
object Progression {
    fun next(e: ExposureInput): EngineResult<Prescription> {
        val work = e.sets.filter { !it.warmup }
        require(work.isNotEmpty()) { "an exposure needs at least one working set" }
        val lo = e.repRange.first
        val hi = e.repRange.last
        val meanRir = work.map { it.rir ?: e.targetRir }.average()
        val over = e.targetRir - meanRir // how many RPE points harder than planned
        val stepCapPct = minOf(P.PROG_001.max_step_pct, ProgressionCaps.intensityPctPerWeek(e.level, e.age))
        val inputs = mapOf("load" to e.load, "reps" to work.map { it.reps }, "meanRir" to Num.round2(meanRir),
            "targetRir" to e.targetRir, "range" to "$lo-$hi")

        fun result(p: Prescription, rules: List<String>, reason: ReasonKey, extra: List<Decision> = emptyList()) =
            EngineResult(p, listOf(Decision(DecisionKind.LOAD_CHANGE, rules, reason, inputs,
                mapOf("load" to p.load, "range" to "${p.repRange.first}-${p.repRange.last}", "repTarget" to p.repTarget, "action" to p.action))) + extra)

        // PROG-008: technique gate first.
        val worstForm = work.map { it.formOk }.maxByOrNull { it.ordinal } ?: FormCheck.YES
        if (worstForm != FormCheck.YES) {
            val regress = worstForm == FormCheck.NO && e.consecutiveFormNo >= P.PROG_008.regress_after_no
            val extra = ArrayList<Decision>()
            if (worstForm == FormCheck.NO) extra += Decision(DecisionKind.LOAD_CHANGE, listOf(RuleIds.PROG_008), ReasonKey.TECHNIQUE_CUE)
            if (regress) extra += Decision(DecisionKind.SUBSTITUTION, listOf(RuleIds.PROG_008), ReasonKey.REGRESSION_OFFERED,
                inputs = mapOf("consecutiveNo" to e.consecutiveFormNo))
            return result(Prescription(e.load, e.repRange, repTargetHold(work, lo, hi), ProgressionAction.HOLD, offerRegression = regress),
                listOf(RuleIds.PROG_008), ReasonKey.LOAD_HOLD_TECHNIQUE, extra)
        }

        // PROG-004: reduce when clearly too hard or reps fell short on ≥2 sets.
        val setsBelow = work.count { it.reps < lo }
        if (over > P.PROG_004.hold_band[1] || setsBelow >= 2) {
            return if (e.previousWasReduction) {
                val base = e.loadBeforeReductions ?: e.load
                val target = base * (1.0 - P.PROG_004.second_reduce_pct / 100.0)
                val load = lowerLoad(target, e.load, e.available)
                result(Prescription(load, e.repRange, lo, ProgressionAction.REDUCE_AND_REVIEW),
                    listOf(RuleIds.PROG_004, RuleIds.REG_001), ReasonKey.LOAD_REDUCE_REPEATED)
            } else {
                val load = lowerLoad(e.load * (1.0 - P.PROG_004.reduce_pct / 100.0), e.load, e.available)
                result(Prescription(load, e.repRange, lo, ProgressionAction.REDUCE), listOf(RuleIds.PROG_004), ReasonKey.LOAD_REDUCE_EFFORT_HIGH)
            }
        }
        if (over > P.PROG_004.hold_band[0]) {
            return result(Prescription(e.load, e.repRange, repTargetHold(work, lo, hi), ProgressionAction.HOLD),
                listOf(RuleIds.PROG_004), ReasonKey.LOAD_HOLD_EFFORT_HIGH)
        }

        val allAtTop = work.all { it.reps >= hi }
        if (allAtTop && over <= P.PROG_001.rpe_tolerance) {
            if (!e.recoveryOk) {
                return result(Prescription(e.load, e.repRange, hi, ProgressionAction.HOLD), listOf(RuleIds.PROG_001), ReasonKey.LOAD_HOLD_RECOVERY)
            }
            val step = nextStep(e.load, e.available)
            if (step != null && pct(step, e.load) <= stepCapPct + 1e-9) {
                return result(Prescription(step, e.repRange, lo, ProgressionAction.LOAD_UP),
                    listOf(RuleIds.PROG_001, RuleIds.PROG_003), ReasonKey.LOAD_UP_TARGET_MET)
            }
            val maxReps = maxExtendedReps(e.exercise)
            if (hi < maxReps) {
                // PROG-002: the next load step is more than 5% (or there is none): extend the range by one rep.
                val range = lo..(hi + 1)
                return result(Prescription(e.load, range, hi + 1, ProgressionAction.RANGE_EXTENDED),
                    listOf(RuleIds.PROG_002), ReasonKey.RANGE_EXTENDED)
            }
            if (step == null) {
                return result(Prescription(e.load, e.repRange, hi, ProgressionAction.HOLD), listOf(RuleIds.PROG_002), ReasonKey.LOAD_HOLD_TOP_OF_RANGE)
            }
            // PROG-002 jump, limited by PROG-007 / SAF-005: the estimated 1RM implied by the new target
            // (load × (1 + (reps + RIR)/30)) may rise by at most the weekly intensity cap (decision D-043).
            val cap = 1.0 + ProgressionCaps.intensityPctPerWeek(e.level, e.age) / 100.0
            val before = implied(e.load, hi, e.targetRir)
            val proportional = maxOf(lo, Math.floor(hi * e.load / step + 1e-9).toInt())
            val reps = (proportional downTo lo).firstOrNull { implied(step, it, e.targetRir) <= before * cap + 1e-9 }
            if (reps == null) {
                // Even the bottom of the range would be too big a jump: hold and offer another variation.
                return result(Prescription(e.load, e.repRange, hi, ProgressionAction.HOLD, offerRegression = false), listOf(RuleIds.PROG_002, RuleIds.PROG_007),
                    ReasonKey.LOAD_HOLD_TOP_OF_RANGE, listOf(Decision(DecisionKind.SUBSTITUTION, listOf(RuleIds.PROG_007, RuleIds.SUB_002), ReasonKey.SWAP_OFFERED_ONLY,
                        inputs = mapOf("nextLoad" to step))))
            }
            return result(Prescription(step, e.repRange, reps, ProgressionAction.LOAD_JUMP),
                listOf(RuleIds.PROG_002, RuleIds.PROG_003, RuleIds.PROG_007), ReasonKey.LOAD_JUMP_REPS_RESET)
        }
        if (allAtTop) {
            // Reps hit but effort a little above target (between +0.5 and +1): hold and consolidate.
            return result(Prescription(e.load, e.repRange, hi, ProgressionAction.HOLD), listOf(RuleIds.PROG_001), ReasonKey.LOAD_HOLD_EFFORT_HIGH)
        }
        // PROG-002: keep the load, add at least one rep on at least one set.
        return result(Prescription(e.load, e.repRange, Num.clampInt(work.maxOf { it.reps } + 1, lo, hi), ProgressionAction.REPS_UP),
            listOf(RuleIds.PROG_002), ReasonKey.REPS_UP)
    }

    /** PROG-002 limit: up to +3 reps above the default range, max 15 for compounds and 20 for isolation. */
    fun maxExtendedReps(ex: Exercise): Int {
        val cap = if (ex.pattern == Pattern.ISOLATION || ex.pattern.coreCategory) P.PROG_002.isolation_max_reps else P.PROG_002.compound_max_reps
        return minOf(ex.defaultRepRange.last + P.PROG_002.range_extension_max, cap, ex.maxExtendedReps)
    }

    /** Smallest available load at least 2.5% heavier; if none, the next heavier load; null at the top. */
    fun nextStep(load: Double, available: List<Double>): Double? {
        val above = available.filter { it > load + 1e-9 }.sorted()
        return above.firstOrNull { pct(it, load) >= P.PROG_001.min_step_pct - 1e-9 } ?: above.lastOrNull()
    }

    /** A load reduction that really reduces: rounded per PROG-003, else the next load down. */
    fun lowerLoad(target: Double, current: Double, available: List<Double>): Double {
        if (available.isEmpty()) return PlateMath.round(target)
        val chosen = PlateMath.choose(target, available)
        if (chosen < current - 1e-9) return chosen
        return available.filter { it < current - 1e-9 }.maxOrNull() ?: current
    }

    private fun implied(load: Double, reps: Int, rir: Double): Double = load * (1.0 + (reps + rir) / P.INT_004.divisor)

    private fun repTargetHold(work: List<SetLog>, lo: Int, hi: Int): Int = Num.clampInt(work.maxOf { it.reps }, lo, hi)
    private fun pct(next: Double, load: Double): Double = (next - load) / load * 100.0
}

/** PROG-007 weekly caps. */
object ProgressionCaps {
    /** PROG-007 weekly intensity cap in percent; AGE-001 multiplies it by 0.8 at age 65+. */
    fun intensityPctPerWeek(level: Level, age: Int? = null): Double {
        val base = if (level == Level.BEGINNER) P.PROG_007.intensity_pct_week.beginner else P.PROG_007.intensity_pct_week.other.toDouble()
        return if (age != null && age >= 65) base * P.AGE_001.age_65.progression_cap_multiplier else base
    }

    /** Highest load this week given last week's load for the same reps and effort. */
    fun maxLoadThisWeek(lastWeekLoad: Double, level: Level, available: List<Double>, age: Int? = null): Double {
        val limit = lastWeekLoad * (1.0 + intensityPctPerWeek(level, age) / 100.0)
        return available.filter { it <= limit + 1e-9 }.maxOrNull() ?: lastWeekLoad
    }

    fun capLoad(proposed: Double, lastWeekLoad: Double, level: Level, available: List<Double>, age: Int? = null): EngineResult<Double> {
        val max = maxLoadThisWeek(lastWeekLoad, level, available, age)
        return if (proposed <= max + 1e-9) EngineResult(proposed)
        else EngineResult(max, listOf(Decision(DecisionKind.LOAD_CHANGE, listOf(RuleIds.PROG_007), ReasonKey.LOAD_CAPPED_WEEKLY,
            inputs = mapOf("proposed" to proposed, "lastWeek" to lastWeekLoad), outputs = mapOf("load" to max))))
    }

    fun maxSetsNextWeek(current: Int): Int = current + P.PROG_007.sets_per_week
    fun maxAerobicMinutesNextWeek(current: Double): Double = current * (1.0 + P.PROG_007.aerobic_pct_week / 100.0)
    fun maxHiitWorkMinutesNextWeek(current: Double): Double = current + P.PROG_007.hiit_work_min_week
}

/**
 * In-session autoregulation (INT-007): after a working set, nudge the next set's load.
 * Net change is limited to ±10% of the planned load for the exercise in this session.
 */
object Autoregulation {
    /**
     * The day's highest load for an exercise: no extra cap on FULL days, otherwise the RDY-004 / DEL-003
     * fraction of the normal working load (e.g. 85% for a main lift on a LIGHT day).
     */
    fun tierMaxLoad(normalLoad: Double, main: Boolean, tier: com.personalfitnesscoach.engine.model.Tier, inDeload: Boolean): Double {
        val f = com.personalfitnesscoach.engine.safety.SessionValidator.maxLoadFactor(main, tier, inDeload)
        return if (f >= 1.0) Double.POSITIVE_INFINITY else normalLoad * f
    }

    /**
     * @param tierMaxLoad highest load the day allows (RDY-004 / DEL-003 cap applied to the normal working
     *   load, e.g. 85% on a LIGHT day); increases never go above it.
     */
    fun adjust(
        plannedLoad: Double,
        currentLoad: Double,
        set: SetLog,
        targetReps: Int,
        rangeBottom: Int,
        targetRir: Double,
        available: List<Double>,
        tierMaxLoad: Double,
    ): EngineResult<Double> {
        val rir = set.rir ?: return EngineResult(currentLoad)
        val rpeDelta = targetRir - rir // positive = harder than planned
        val maxUp = plannedLoad * (1.0 + P.INT_007.net_limit_pct / 100.0)
        val minDown = plannedLoad * (1.0 - P.INT_007.net_limit_pct / 100.0)
        val inputs = mapOf("planned" to plannedLoad, "current" to currentLoad, "reps" to set.reps, "rir" to rir, "targetRir" to targetRir)
        fun d(reason: ReasonKey, load: Double) = listOf(Decision(DecisionKind.LOAD_CHANGE, listOf(RuleIds.INT_007), reason, inputs, mapOf("load" to load)))

        if (rpeDelta > P.INT_007.hard_threshold || set.reps < rangeBottom) {
            val down = Progression.lowerLoad(currentLoad * (1.0 - P.INT_007.step_down_pct / 100.0), currentLoad, available)
            if (down < minDown - 1e-9) return EngineResult(currentLoad, d(ReasonKey.INSESSION_LIMIT_REACHED, currentLoad))
            return EngineResult(down, d(ReasonKey.INSESSION_LOAD_DOWN, down))
        }
        if (rpeDelta < P.INT_007.easy_threshold && set.reps >= targetReps) {
            val up = PlateMath.nextAbove(currentLoad, available) ?: return EngineResult(currentLoad)
            if ((up - currentLoad) / currentLoad * 100.0 > P.INT_007.step_up_max_pct + 1e-9) return EngineResult(currentLoad)
            if (up > maxUp + 1e-9 || up > tierMaxLoad + 1e-9) return EngineResult(currentLoad, d(ReasonKey.INSESSION_LIMIT_REACHED, currentLoad))
            return EngineResult(up, d(ReasonKey.INSESSION_LOAD_UP, up))
        }
        return EngineResult(currentLoad)
    }
}
