package com.personalfitnesscoach.engine.dose

import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.Exercise
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Muscle
import com.personalfitnesscoach.engine.model.Objective
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds

/** What a slot is trying to train today; picks the REP rule. */
enum class DoseGoal { STRENGTH, HYPERTROPHY, ENDURANCE, POWER, CORE }

/** A rep (or seconds, or metres) target with the %1RM band it implies, when one applies. */
data class RepScheme(
    val range: IntRange,
    val unit: DoseUnit,
    val perSide: Boolean,
    val pct1rm: ClosedFloatingPointRange<Double>?,
    val ruleId: String,
    /** REP-004: sets × reps must stay below this; null when no total cap applies. */
    val maxTotalReps: Int? = null,
    /** REP-001: 1–3-rep sets stay at RPE ≤ 8, i.e. RIR ≥ 2. */
    val minRir: Double? = null,
)

/** REP-001 to REP-006. */
object Reps {
    private fun range(l: List<Int>) = l[0]..l[1]

    /** Clip the rule range to the exercise's own range when they overlap; otherwise keep the rule range. */
    private fun clip(rule: IntRange, ex: Exercise): IntRange {
        val lo = maxOf(rule.first, ex.defaultRepRange.first)
        val hi = minOf(rule.last, maxOf(ex.defaultRepRange.last, ex.maxExtendedReps))
        return if (lo <= hi) lo..hi else rule
    }

    fun forSlot(ex: Exercise, goal: DoseGoal, level: Level, advancedLowReps: Boolean = false): EngineResult<RepScheme> {
        val core = ex.pattern.coreCategory || ex.pattern == Pattern.ROTATION || goal == DoseGoal.CORE
        val scheme = when {
            // REP-006 / carries and crawls: distance from the exercise itself.
            ex.unit == DoseUnit.METRES -> RepScheme(ex.defaultRepRange, DoseUnit.METRES, ex.unilateral, null, RuleIds.REP_006)
            // REP-005 core: holds 10–45 s or 6–12 controlled reps per side.
            core && ex.unit == DoseUnit.SECONDS -> RepScheme(clip(range(P.REP_005.hold_seconds), ex), DoseUnit.SECONDS, ex.unilateral, null, RuleIds.REP_005)
            core -> RepScheme(clip(range(P.REP_005.reps_per_side), ex), DoseUnit.REPS, ex.unilateral, null, RuleIds.REP_005)
            // REP-004 power: only on power-capable exercises, total reps per exercise below 24.
            goal == DoseGoal.POWER && ex.powerCapable -> RepScheme(range(P.REP_004.reps), DoseUnit.REPS, ex.unilateral,
                P.REP_004.pct_1rm[0].toDouble()..P.REP_004.pct_1rm[1].toDouble(), RuleIds.REP_004, P.REP_004.max_total_reps)
            // REP-003 endurance: 15–30 reps, or 30–60 s for holds.
            goal == DoseGoal.ENDURANCE && ex.unit == DoseUnit.SECONDS -> RepScheme(range(P.REP_003.seconds), DoseUnit.SECONDS, ex.unilateral, null, RuleIds.REP_003)
            goal == DoseGoal.ENDURANCE -> RepScheme(clip(range(P.REP_003.reps), ex), DoseUnit.REPS, ex.unilateral,
                0.0..P.REP_003.pct_1rm_max.toDouble(), RuleIds.REP_003)
            // REP-001 strength: only for loaded compounds that track e1RM; 1–3 only for advanced lifters who ask for it.
            goal == DoseGoal.STRENGTH && ex.dosedAsCompound && ex.trackE1rm ->
                if (advancedLowReps && level == Level.ADVANCED) RepScheme(range(P.REP_001.advanced_low_reps), DoseUnit.REPS, ex.unilateral,
                    null, RuleIds.REP_001, minRir = 10.0 - P.REP_001.advanced_low_rep_max_rpe)
                else RepScheme(range(P.REP_001.reps), DoseUnit.REPS, ex.unilateral, P.REP_001.pct_1rm[0].toDouble()..P.REP_001.pct_1rm[1].toDouble(), RuleIds.REP_001)
            ex.unit == DoseUnit.SECONDS -> RepScheme(ex.defaultRepRange, DoseUnit.SECONDS, ex.unilateral, null, RuleIds.REP_002)
            // REP-002 hypertrophy (and the fallback for strength goals on accessories).
            ex.dosedAsCompound -> RepScheme(clip(range(P.REP_002.compound), ex), DoseUnit.REPS, ex.unilateral,
                P.REP_002.pct_1rm[0].toDouble()..P.REP_002.pct_1rm[1].toDouble(), RuleIds.REP_002)
            else -> RepScheme(clip(range(P.REP_002.isolation), ex), DoseUnit.REPS, ex.unilateral, null, RuleIds.REP_002)
        }
        return EngineResult(scheme, listOf(Decision(DecisionKind.REP_CHANGE, listOf(scheme.ruleId), ReasonKey.REPS_FROM_GOAL,
            inputs = mapOf("exercise" to ex.id, "goal" to goal.name), outputs = mapOf("range" to "${scheme.range.first}-${scheme.range.last}", "unit" to scheme.unit.name))))
    }

    /** REP-004: the most sets allowed for a power exercise at `reps` per set (sets × reps < 24). */
    fun maxPowerSets(reps: Int): Int = if (reps <= 0) 0 else P.REP_004.max_total_reps / reps

    /** REP-004 stop rule: end the power set when bar or body speed visibly drops. */
    fun stopPowerSet(speedDropped: Boolean): Boolean = speedDropped

    /** REP-006: how conditioning is measured per modality (first unit is the default). */
    fun conditioningUnits(m: com.personalfitnesscoach.engine.model.Modality): List<com.personalfitnesscoach.engine.library.ConditioningUnit> =
        com.personalfitnesscoach.engine.library.GeneratedLibrary.modalities.firstOrNull { it.modality == m }?.units.orEmpty()

    fun goalFor(objective: Objective): DoseGoal = when (objective) {
        Objective.STRENGTH -> DoseGoal.STRENGTH
        Objective.POWER -> DoseGoal.POWER
        Objective.ENDURANCE, Objective.CONDITIONING, Objective.GPP -> DoseGoal.ENDURANCE
        Objective.CORE -> DoseGoal.CORE
        Objective.HYPERTROPHY, Objective.MOBILITY -> DoseGoal.HYPERTROPHY
    }
}

/** A rest window: the user may start anywhere in [min, max]; the timer alerts at `default` (REST-007). */
data class RestWindow(val minSec: Int, val defaultSec: Int, val maxSec: Int, val ruleId: String)

enum class RestStatus { TOO_SHORT, IN_RANGE, ALERT, LONG }

/** REST-001 to REST-007. */
object Rest {
    val heavy get() = RestWindow(P.REST_001.min_s, P.REST_001.default_s, P.REST_001.max_s, RuleIds.REST_001)
    val moderate get() = RestWindow(P.REST_002.min_s, P.REST_002.default_s, P.REST_002.max_s, RuleIds.REST_002)
    val accessory get() = RestWindow(P.REST_003.min_s, P.REST_003.default_s, P.REST_003.max_s, RuleIds.REST_003)
    val superset get() = RestWindow(P.REST_004.min_s, P.REST_004.default_s, P.REST_004.max_s, RuleIds.REST_004)
    val circuit get() = RestWindow(P.REST_005.min_s, P.REST_005.default_s, P.REST_005.max_s, RuleIds.REST_005)
    val core get() = RestWindow(P.REST_006.min_s, P.REST_006.default_s, P.REST_006.max_s, RuleIds.REST_006)

    /**
     * Rest between sets of one exercise. Heavy = a compound at ≤ 6 reps or ≥ 80% 1RM (REST-001);
     * other compounds REST-002; core and rotation REST-006; everything else REST-003.
     */
    fun forSets(ex: Exercise, reps: Int, pct1rm: Double? = null): RestWindow = when {
        ex.pattern.coreCategory || ex.pattern == Pattern.ROTATION || ex.primary == setOf(Muscle.CORE) -> core
        ex.dosedAsCompound && (reps <= 6 || (pct1rm ?: 0.0) >= 80.0) -> heavy
        ex.dosedAsCompound -> moderate
        else -> accessory
    }

    /** REST-007: what the rest timer shows after `elapsedSec`. */
    fun status(elapsedSec: Int, w: RestWindow): RestStatus = when {
        elapsedSec < w.minSec -> RestStatus.TOO_SHORT
        elapsedSec > w.maxSec -> RestStatus.LONG
        elapsedSec >= w.defaultSec -> RestStatus.ALERT
        else -> RestStatus.IN_RANGE
    }

    /** REST-007: the order rests shrink under time pressure (P5 first, heavy P1 last; never below minimum). */
    val compressionOrder: List<com.personalfitnesscoach.engine.planning.Priority>
        get() = P.REST_007.compression_order.map { com.personalfitnesscoach.engine.planning.Priority.valueOf(it) }
}

/** INT-002 RIR targets and the INT-006 effort prompt. */
object Effort {
    /** RIR range for a level and exercise kind; beginners after week 8 use the intermediate ranges (D-047). */
    fun range(level: Level, compound: Boolean, weeksTraining: Int): ClosedFloatingPointRange<Double> {
        fun r(l: List<Int>) = l[0].toDouble()..l[1].toDouble()
        return when {
            level == Level.BEGINNER && weeksTraining < 8 -> {
                val v = (if (compound) P.INT_002.beginner_first_8_weeks.compound else P.INT_002.beginner_first_8_weeks.isolation).toDouble()
                v..v
            }
            level == Level.ADVANCED -> r(if (compound) P.INT_002.advanced.compound else P.INT_002.advanced.isolation)
            else -> r(if (compound) P.INT_002.intermediate.compound else P.INT_002.intermediate.isolation)
        }
    }

    /**
     * Target RIR for a block week: the range midpoint, +1 in week 1, −1 in the last loading week
     * (never below the range floor); deload, LIGHT days and conservative screening never below RIR 3.
     */
    fun targetRir(
        level: Level,
        compound: Boolean,
        weeksTraining: Int,
        blockWeek: Int,
        blockLoadingWeeks: Int,
        lightOrDeload: Boolean = false,
    ): EngineResult<Double> {
        val r = range(level, compound, weeksTraining)
        val mid = (r.start + r.endInclusive) / 2.0
        var t = when {
            blockWeek <= 1 -> mid + P.INT_002.block_week1_offset
            blockWeek >= blockLoadingWeeks -> maxOf(r.start, mid + P.INT_002.block_last_week_offset)
            else -> mid
        }
        if (lightOrDeload) t = maxOf(t, P.INT_002.deload_or_light_min_rir.toDouble())
        return EngineResult(t, listOf(Decision(DecisionKind.LOAD_PRESCRIPTION, listOf(RuleIds.INT_002), ReasonKey.RIR_TARGET,
            inputs = mapOf("level" to level.name, "compound" to compound, "blockWeek" to blockWeek), outputs = mapOf("rir" to t))))
    }

    enum class PromptStyle { RIR_QUESTION, RIR_WITH_RPE, RPE }

    /** INT-006: beginners answer "how many more could you have done?" for 4 weeks; RPE words come in gradually. */
    fun promptStyle(level: Level, weeksTraining: Int): PromptStyle = when {
        level != Level.BEGINNER -> PromptStyle.RPE
        weeksTraining < P.INT_006.weeks -> PromptStyle.RIR_QUESTION
        weeksTraining < 2 * P.INT_006.weeks -> PromptStyle.RIR_WITH_RPE
        else -> PromptStyle.RPE
    }

    val rirOptions: List<String> get() = P.INT_006.rir_options

    /** Maps an INT-006 answer to RIR. "4+" counts as 4, so the set still counts as hard toward caps (conservative). */
    fun rirFromAnswer(answer: String): Double? = when (answer.trim()) {
        "0" -> 0.0; "1" -> 1.0; "2" -> 2.0; "3" -> 3.0; "4+", "4" -> 4.0
        else -> null
    }
}
