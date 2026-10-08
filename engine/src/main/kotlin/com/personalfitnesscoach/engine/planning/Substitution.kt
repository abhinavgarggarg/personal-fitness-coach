package com.personalfitnesscoach.engine.planning

import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.Num
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.model.Exercise
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds

/** Everything the hard filters need to know about today (SUB-001). */
data class SubContext(
    val equipmentToday: Set<String>,
    val level: Level,
    /** Highest joint-stress rating (0–4) tolerated per joint today, from pain reports and limitations. */
    val jointLimits: Map<Joint, Int> = emptyMap(),
    /** Limitation tags that rule an exercise out (e.g. "shoulder_overhead_pain"). */
    val blockedTags: Set<String> = emptySet(),
    /** Exercises the user excluded. */
    val excludedIds: Set<String> = emptySet(),
    /** SUB-003 preference scores 0–1 by exercise ID; 0.5 when unknown. */
    val preferences: Map<String, Double> = emptyMap(),
    /** Joints that are sensitive today — joint-stress fit penalises extra stress there twice as hard. */
    val sensitiveJoints: Set<Joint> = emptySet(),
)

/** The eight SUB-002 component fits, each 0–1. */
data class Fits(
    val pattern: Double,
    val objective: Double,
    val muscle: Double,
    val equipment: Double,
    val joint: Double,
    val difficulty: Double,
    val fatigue: Double,
    val preference: Double,
)

data class Candidate(val exercise: Exercise, val score: Double, val fits: Fits)

/** Ranked swap options; `autoPick` is null when no candidate may be chosen automatically. */
data class SwapOptions(val ranked: List<Candidate>, val autoPick: Candidate?)

/**
 * Exercise substitution. Component fits are defined in Phase 3 (decision D-033) because
 * Phase 1 fixed only the weights; the Phase 1 worked-example totals (0.955 / 0.760) are
 * reproduced from its component values in the tests.
 */
object Substitution {
    /** Equipment IDs that belong to MOD-001 excluded modalities. */
    val MOD001_EQUIPMENT: Set<String> = (P.MOD_001.excluded + P.MOD_001.excluded_pending_confirmation +
        listOf("treadmill", "upright_bike", "recumbent_bike", "spin_bike", "stair_climber", "air_bike", "fan_bike")).toSet()

    fun levelNumber(level: Level): Int = level.pick(1, 2, 3)

    /** SUB-001 hard filters. */
    fun passesFilters(c: Exercise, original: Exercise, ctx: SubContext): Boolean =
        c.id != original.id &&
            ctx.equipmentToday.containsAll(c.equipment) &&
            c.equipment.none { it in MOD001_EQUIPMENT } &&
            c.id !in ctx.excludedIds &&
            c.limitationTags.none { it in ctx.blockedTags } &&
            ctx.jointLimits.all { (j, limit) -> c.stress(j) <= limit } &&
            c.skill <= levelNumber(ctx.level) + P.SUB_001.skill_margin

    fun patternFit(c: Exercise, o: Exercise): Double = when {
        c.pattern == o.pattern -> P.SUB_002.pattern_scores.same
        c.pattern.isAdjacentTo(o.pattern) -> P.SUB_002.pattern_scores.adjacent
        else -> P.SUB_002.pattern_scores.other
    }

    fun fits(c: Exercise, o: Exercise, ctx: SubContext): Fits {
        val objective = if (o.objectives.isEmpty()) 1.0 else (c.objectives intersect o.objectives).size.toDouble() / o.objectives.size
        // Primary-muscle overlap: full credit when the candidate trains it as primary, half as secondary.
        val muscle = if (o.primary.isEmpty()) 1.0 else o.primary.sumOf { m ->
            when (m) { in c.primary -> 1.0; in c.secondary -> 0.5; else -> 0.0 }
        } / o.primary.size
        val equipment = when {
            c.equipment == o.equipment -> 1.0
            c.loadType == o.loadType -> 0.7
            else -> 0.5
        }
        var excess = 0.0
        for (j in Joint.entries) {
            val e = maxOf(0, c.stress(j) - o.stress(j)).toDouble()
            excess += if (j in ctx.sensitiveJoints) 2 * e else e
        }
        val joint = Num.clamp(1.0 - excess / 4.0, 0.0, 1.0)
        val difficulty = Num.clamp(1.0 - Math.abs(c.difficulty - o.difficulty) / 4.0, 0.0, 1.0)
        val fatigue = Num.clamp(1.0 - Math.abs(c.fatigueSystemic - o.fatigueSystemic) / 4.0, 0.0, 1.0)
        val pref = ctx.preferences[c.id] ?: 0.5
        return Fits(patternFit(c, o), objective, muscle, equipment, joint, difficulty, fatigue, pref)
    }

    /** SUB-002 weighted score. */
    fun score(f: Fits): Double = with(P.SUB_002.weights) {
        pattern * f.pattern + objective * f.objective + muscle * f.muscle + equipment * f.equipment +
            joint * f.joint + difficulty * f.difficulty + fatigue * f.fatigue + preference * f.preference
    }

    /** Filter, score and rank; the top 3 are offered, and only same-or-adjacent patterns are auto-picked. */
    fun options(original: Exercise, library: List<Exercise>, ctx: SubContext, top: Int = 3): EngineResult<SwapOptions> {
        val ranked = library.asSequence()
            .filter { passesFilters(it, original, ctx) }
            .map { val f = fits(it, original, ctx); Candidate(it, Math.round(score(f) * 1000.0) / 1000.0, f) }
            .sortedWith(compareByDescending<Candidate> { it.score }.thenByDescending { it.fits.pattern }.thenBy { it.exercise.id })
            .take(top).toList()
        val auto = ranked.firstOrNull { it.fits.pattern >= P.SUB_002.min_pattern_for_auto }
        val reason = when {
            ranked.isEmpty() -> ReasonKey.SWAP_NONE_AVAILABLE
            auto == null -> ReasonKey.SWAP_OFFERED_ONLY
            else -> ReasonKey.SWAP_CHOSEN
        }
        return EngineResult(SwapOptions(ranked, auto), listOf(Decision(DecisionKind.SUBSTITUTION,
            listOf(RuleIds.SUB_001, RuleIds.SUB_002), reason, inputs = mapOf("original" to original.id),
            outputs = mapOf("ranked" to ranked.map { "${it.exercise.id}:${it.score}" }, "auto" to auto?.exercise?.id))))
    }

    /** SUB-003: accept/reject moves the preference by ±0.05 within 0–1. */
    fun updatePreference(current: Double, accepted: Boolean): Double {
        val step = if (accepted) P.SUB_003.step else -P.SUB_003.step
        return Num.round2(Num.clamp(current + step, P.SUB_003.bounds[0].toDouble(), P.SUB_003.bounds[1].toDouble()))
    }
}
