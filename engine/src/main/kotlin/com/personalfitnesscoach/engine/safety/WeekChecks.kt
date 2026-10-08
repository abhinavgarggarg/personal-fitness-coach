package com.personalfitnesscoach.engine.safety

import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.model.Muscle
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds

data class PatternIssue(val ruleId: String, val code: String, val detail: String = "")

/**
 * Week-level balance checks used by SAF-008 (PAT-001 to PAT-004). The week planner re-plans when
 * these report issues; a single session cannot fix weekly balance on its own.
 */
object WeekChecks {
    private val LIFTING = listOf(Pattern.SQUAT, Pattern.HINGE, Pattern.LUNGE, Pattern.HORIZONTAL_PUSH, Pattern.HORIZONTAL_PULL, Pattern.VERTICAL_PUSH, Pattern.VERTICAL_PULL)
    private val MAIN = listOf(Pattern.SQUAT, Pattern.HINGE, Pattern.HORIZONTAL_PUSH, Pattern.HORIZONTAL_PULL)
    private val CORE = listOf(Pattern.ANTI_EXTENSION, Pattern.ANTI_ROTATION, Pattern.ANTI_LATERAL_FLEXION)
    private val LOWER = setOf(Muscle.QUADS, Muscle.GLUTES, Muscle.HAMSTRINGS, Muscle.ADDUCTORS, Muscle.CALVES)

    /**
     * @param week every planned session of the week
     * @param carryOrRotationLastWeek patterns among {LOADED_CARRY, ROTATION} trained last week
     */
    fun issues(week: List<Session>, daysPerWeek: Int, carryOrRotationLastWeek: Set<Pattern> = emptySet()): EngineResult<List<PatternIssue>> {
        val out = ArrayList<PatternIssue>()
        // SAF-005 / FREQ-001: at most 6 training days and always at least one full rest day.
        // A RECOVERY day (rest with optional easy Z1) is a rest day, not a training day.
        val trainingDays = week.count { it.tier != com.personalfitnesscoach.engine.model.Tier.RECOVERY && (it.exercises.isNotEmpty() || it.conditioning.isNotEmpty()) }
        if (trainingDays > P.SAF_005.training_days_per_week) out += PatternIssue(RuleIds.SAF_005, "TOO_MANY_DAYS", "$trainingDays")
        val exercises = week.flatMap { it.exercises }
        // Coverage counts an exercise's second pattern too (a suitcase carry covers anti-lateral flexion);
        // the PAT-002/003 ratios below use main patterns only.
        fun n(p: Pattern) = exercises.count { it.exercise.trains(p) }
        // PAT-001 coverage.
        for (p in LIFTING) if (n(p) < P.PAT_001.lifting_patterns_min) out += PatternIssue(RuleIds.PAT_001, "MISSING_PATTERN", p.name)
        for (p in CORE) if (n(p) < P.PAT_001.core_categories_min) out += PatternIssue(RuleIds.PAT_001, "MISSING_CORE", p.name)
        if (daysPerWeek >= 3) {
            for (p in MAIN) if (n(p) < P.PAT_001.main_patterns_min) out += PatternIssue(RuleIds.PAT_001, "MAIN_PATTERN_ONCE", p.name)
            if (n(Pattern.LOADED_CARRY) < P.PAT_001.carry_min) out += PatternIssue(RuleIds.PAT_001, "MISSING_CARRY")
            if (n(Pattern.ROTATION) == 0 && Pattern.ROTATION !in carryOrRotationLastWeek) out += PatternIssue(RuleIds.PAT_001, "MISSING_ROTATION")
        } else {
            // 2 days: carry and rotation alternate weeks.
            if (n(Pattern.LOADED_CARRY) == 0 && n(Pattern.ROTATION) == 0) {
                val due = if (Pattern.LOADED_CARRY in carryOrRotationLastWeek) Pattern.ROTATION else Pattern.LOADED_CARRY
                out += PatternIssue(RuleIds.PAT_001, if (due == Pattern.LOADED_CARRY) "MISSING_CARRY" else "MISSING_ROTATION")
            }
        }
        // PAT-002 pull:push and PAT-003 knee:hip, in sets.
        val pull = exercises.filter { it.exercise.pattern.isPull }.sumOf { it.sets }
        val push = exercises.filter { it.exercise.pattern.isPush }.sumOf { it.sets }
        ratioIssue(pull, push, P.PAT_002.min, P.PAT_002.max)?.let { out += PatternIssue(RuleIds.PAT_002, "PULL_PUSH_RATIO", it) }
        val knee = exercises.filter { it.exercise.pattern.isKneeDominant }.sumOf { it.sets }
        val hip = exercises.filter { it.exercise.pattern.isHipDominant }.sumOf { it.sets }
        ratioIssue(knee, hip, P.PAT_003.min, P.PAT_003.max)?.let { out += PatternIssue(RuleIds.PAT_003, "KNEE_HIP_RATIO", it) }
        // PAT-004 unilateral lower body.
        val uni = exercises.count { it.exercise.unilateral && it.exercise.primary.any { m -> m in LOWER } }
        val need = if (daysPerWeek >= 4) P.PAT_004.min_when_4plus_days else P.PAT_004.min
        if (uni < need) out += PatternIssue(RuleIds.PAT_004, "UNILATERAL_LOWER", "$uni<$need")
        val d = if (out.isEmpty()) emptyList() else listOf(Decision(DecisionKind.SAFETY, out.map { it.ruleId }.distinct(), ReasonKey.PATTERN_IMBALANCE,
            outputs = mapOf("issues" to out.map { "${it.code}:${it.detail}" })))
        return EngineResult(out, d)
    }

    /** null when num/den is within [min, max]; a ratio with nothing on either side is not an issue. */
    private fun ratioIssue(num: Int, den: Int, min: Double, max: Double): String? {
        if (num == 0 && den == 0) return null
        if (den == 0) return "$num:0"
        val r = num.toDouble() / den
        return if (r < min - 1e-9 || r > max + 1e-9) String.format(java.util.Locale.ROOT, "%.2f", r) else null
    }
}
