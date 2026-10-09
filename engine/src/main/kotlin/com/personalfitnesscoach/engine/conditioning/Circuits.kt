package com.personalfitnesscoach.engine.conditioning

import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.library.CircuitMove
import com.personalfitnesscoach.engine.library.GeneratedLibrary
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.model.Zone
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds

/** A bodyweight circuit: the moves in order, each worked for `workSec` then `restSec`, `rounds` times through the list. */
data class CircuitPlan(val moves: List<CircuitMove>, val workSec: Int, val restSec: Int, val rounds: Int, val cr10: IntRange) {
    val stations: Int get() = rounds * moves.size
    val workMinutes: Double get() = stations * workSec / 60.0
    val totalMinutes: Double get() = stations * (workSec + restSec) / 60.0
    /** HIIT-001: a circuit at CR10 ≥ 7 for ≥ 8 minutes counts as a HIIT session. */
    val countsAsHiit: Boolean get() = cr10.first >= P.HIIT_001.count_if_circuit_cr10_gte && totalMinutes >= P.HIIT_001.circuit_minutes_gte
}

/** EQ-003 bodyweight circuits: no-jump moves by default; jumping only where impact is allowed and the user wants it. */
object Circuits {
    private val moves: List<CircuitMove> get() = GeneratedLibrary.modalities.first { it.modality == Modality.BODYWEIGHT_CIRCUIT }.moves

    /** Moves allowed today: no jumping unless allowed, no avoided tag, every joint within its limit. */
    fun allowedMoves(jumping: Boolean, jointLimits: Map<Joint, Int> = emptyMap(), avoidTags: Set<String> = emptySet()): List<CircuitMove> =
        moves.filter { m -> (jumping || !m.jumping) && m.tags.none { it in avoidTags } && jointLimits.all { (j, lim) -> m.stress(j) <= lim } }

    /**
     * Build the circuit for a conditioning block of `workMinutes` (steady) or with an interval structure (HIIT).
     * Steady Z1/Z2: 45 s per move with 15 s to change. Intervals keep their work/rest; beginners work 1:2 (EQ-003, HIIT-002).
     * `offset` rotates the move list (the weekday) for variety. Null when no move is allowed.
     */
    fun build(zone: Zone, workMinutes: Double, interval: Interval?, level: Level, jumping: Boolean, jointLimits: Map<Joint, Int> = emptyMap(),
              avoidTags: Set<String> = emptySet(), offset: Int = 0): EngineResult<CircuitPlan?> {
        val pool = allowedMoves(jumping, jointLimits, avoidTags).sortedWith(compareBy({ it.jumping }, { it.id }))
        if (pool.isEmpty()) return EngineResult(null, listOf(Decision(DecisionKind.SUBSTITUTION, listOf(RuleIds.EQ_003), ReasonKey.SWAP_NONE_AVAILABLE)))
        val k = Math.floorMod(offset, pool.size)
        val chosen = (pool.drop(k) + pool.take(k)).take(5)
        val plan = if (interval != null) {
            val rest = if (level == Level.BEGINNER) maxOf(interval.restSec, interval.workSec * 2) else interval.restSec
            val stations = maxOf(1, interval.reps)
            CircuitPlan(chosen, interval.workSec, rest, maxOf(1, Math.ceil(stations / chosen.size.toDouble()).toInt()), interval.cr10)
        } else {
            val cr10 = Zones.cr10Range(zone)
            val stations = maxOf(chosen.size, Math.round(workMinutes * 60 / 60.0).toInt())
            CircuitPlan(chosen, 45, 15, maxOf(1, Math.ceil(stations / chosen.size.toDouble()).toInt()), cr10)
        }
        return EngineResult(plan, listOf(Decision(DecisionKind.SUBSTITUTION, listOf(RuleIds.EQ_003, RuleIds.HIIT_001), ReasonKey.AWAY_FROM_GYM,
            inputs = mapOf("zone" to zone.name, "jumping" to jumping, "interval" to (interval != null)),
            outputs = mapOf("moves" to chosen.map { it.id }, "work" to plan.workSec, "rest" to plan.restSec, "rounds" to plan.rounds, "countsAsHiit" to plan.countsAsHiit))))
    }
}
