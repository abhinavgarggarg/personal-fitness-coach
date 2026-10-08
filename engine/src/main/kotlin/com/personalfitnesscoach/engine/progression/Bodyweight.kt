package com.personalfitnesscoach.engine.progression

import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.Exercise
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds

/** One set of a bodyweight, assisted or core exercise; `reps` is seconds for holds. RIR null = not rated. */
data class BwSet(val reps: Int, val rir: Double?)

data class BwSession(val sets: List<BwSet>, val assistanceKg: Double? = null)

enum class BwAction { REPS_UP, STEP_UP, ADD_LOAD, LESS_ASSISTANCE, HOLD, STEP_DOWN }

data class BwDecision(
    val action: BwAction,
    /** The exercise to do next time (changes on STEP_UP / STEP_DOWN). */
    val exerciseId: String,
    val targetReps: Int,
    val assistanceKg: Double? = null,
)

/**
 * Bodyweight, assisted and core progression (BW-001, BW-002, PROG-005, CORE-002). Levers run
 * reps → range → tempo → leverage → unilateral/less assistance → added load; the curated
 * ladders in the library encode the middle levers, so a step up means the next rung.
 */
object Bodyweight {
    private val CORE_FAMILIES = setOf("anti_extension", "anti_rotation", "anti_lateral_flexion", "rotation", "crawl", "bracing")

    /** BW-002 top of range: push-ups 15, pull-ups 10–12 (the exercise's own top within that), squats 20. */
    fun topOfRange(ex: Exercise): Int = when (ex.family) {
        "push_up" -> P.BW_002.top_of_range.push_up
        "pull_up" -> ex.defaultRepRange.last.coerceIn(P.BW_002.top_of_range.pull_up[0], P.BW_002.top_of_range.pull_up[1])
        "squat" -> P.BW_002.top_of_range.squat
        else -> ex.defaultRepRange.last
    }

    fun isCoreLadder(ex: Exercise): Boolean = ex.family in CORE_FAMILIES ||
        (ex.family == null && (ex.pattern.coreCategory || ex.pattern == Pattern.ROTATION))

    /**
     * Next prescription from the most recent sessions (oldest first). `assistanceStepKg` is the
     * machine's stack step for assisted exercises; `next`/`previous` are the ladder neighbours.
     */
    fun next(
        ex: Exercise,
        history: List<BwSession>,
        next: Exercise?,
        previous: Exercise?,
        assistanceStepKg: Double = 5.0,
    ): EngineResult<BwDecision> {
        val last = history.lastOrNull()
        val lo = ex.defaultRepRange.first
        fun out(a: BwAction, id: String, reps: Int, assist: Double?, reason: ReasonKey, rules: List<String>) =
            EngineResult(BwDecision(a, id, reps, assist), listOf(Decision(DecisionKind.REP_CHANGE, rules, reason,
                inputs = mapOf("exercise" to ex.id, "sessions" to history.size), outputs = mapOf("action" to a.name, "next" to id, "reps" to reps, "assistance" to assist))))
        if (last == null || last.sets.isEmpty()) return out(BwAction.HOLD, ex.id, lo, last?.assistanceKg, ReasonKey.BW_HOLD, listOf(RuleIds.PROG_005))
        val recent2 = history.takeLast(P.BW_002.consecutive_sessions)
        val twoSessions = recent2.size == P.BW_002.consecutive_sessions

        // CORE-002: 3 solid sets of 30–45 s (or 10–12/side) at about RIR 2, two sessions running → next rung.
        if (isCoreLadder(ex)) {
            val need = if (ex.unit == DoseUnit.SECONDS) P.CORE_002.hold_seconds[0] else P.CORE_002.reps_per_side[0]
            val solid = twoSessions && recent2.all { s -> s.sets.count { it.reps >= need && (it.rir ?: -1.0) >= 2.0 } >= 3 }
            if (solid && next != null) return out(BwAction.STEP_UP, next.id, next.defaultRepRange.first, null, ReasonKey.BW_STEP_UP, listOf(RuleIds.CORE_002, RuleIds.PROG_005))
            val target = minOf(maxOf(last.sets.minOf { it.reps } + (if (ex.unit == DoseUnit.SECONDS) 5 else 1), lo),
                if (ex.unit == DoseUnit.SECONDS) P.CORE_002.hold_seconds[1] else P.CORE_002.reps_per_side[1])
            return out(BwAction.REPS_UP, ex.id, target, null, ReasonKey.REPS_UP, listOf(RuleIds.CORE_002, RuleIds.BW_001))
        }

        // BW-002 assisted: 3 × 8 at RIR ≥ 2 removes one assistance step; with no assistance left, move up the ladder.
        if (ex.assisted) {
            val a = P.BW_002.assisted_pullup
            val earned = last.sets.count { it.reps >= a.reps && (it.rir ?: -1.0) >= a.rir } >= a.sets
            val assist = last.assistanceKg ?: 0.0
            if (earned) {
                val less = assist - assistanceStepKg
                return if (less > 1e-9) out(BwAction.LESS_ASSISTANCE, ex.id, lo, less, ReasonKey.BW_LESS_ASSISTANCE, listOf(RuleIds.BW_002, RuleIds.PROG_005))
                else if (next != null) out(BwAction.STEP_UP, next.id, next.defaultRepRange.first, null, ReasonKey.BW_STEP_UP, listOf(RuleIds.BW_002, RuleIds.BW_001))
                else out(BwAction.HOLD, ex.id, lo, assist, ReasonKey.BW_HOLD, listOf(RuleIds.BW_002))
            }
            return out(BwAction.REPS_UP, ex.id, minOf(ex.defaultRepRange.last, maxOf(lo, last.sets.minOf { it.reps } + 1)), assist,
                ReasonKey.REPS_UP, listOf(RuleIds.BW_001, RuleIds.PROG_005))
        }

        // BW-002: every set at the top of the range at RIR ≥ 1 for two sessions → next rung (or added load at the top).
        val top = topOfRange(ex)
        val atTop = twoSessions && recent2.all { s -> s.sets.all { it.reps >= top && (it.rir ?: -1.0) >= P.BW_002.min_rir } }
        if (atTop) {
            return if (next != null) out(BwAction.STEP_UP, next.id, next.defaultRepRange.first, null, ReasonKey.BW_STEP_UP, listOf(RuleIds.BW_002, RuleIds.BW_001, RuleIds.PROG_005))
            else out(BwAction.ADD_LOAD, ex.id, ex.defaultRepRange.first, null, ReasonKey.BW_ADD_LOAD, listOf(RuleIds.BW_001, RuleIds.PROG_005))
        }
        // BW-001 regressions run the levers backwards: below the range on ≥ 2 sets in two sessions → previous rung.
        val struggling = twoSessions && recent2.all { s -> s.sets.count { it.reps < lo } >= 2 }
        if (struggling && previous != null) return out(BwAction.STEP_DOWN, previous.id, previous.defaultRepRange.first, null, ReasonKey.BW_STEP_DOWN, listOf(RuleIds.BW_001, RuleIds.PROG_005))
        // Reps first (BW-001): aim one rep above the weakest set, up to the top of the range.
        val target = minOf(top, maxOf(lo, last.sets.minOf { it.reps } + 1))
        return out(if (target > last.sets.minOf { it.reps }) BwAction.REPS_UP else BwAction.HOLD, ex.id, target, null,
            ReasonKey.REPS_UP, listOf(RuleIds.BW_001, RuleIds.PROG_005))
    }

    /** BW-001 lever order, for explanations. */
    val levers: List<String> get() = P.BW_001.order
}
