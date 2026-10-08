package com.personalfitnesscoach.engine.program

import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.Exercise
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.LoadType
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.planning.Substitution
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds

/** What the exercise selector needs to know about the user and the gym. */
data class SelectionContext(
    val level: Level,
    val weeksTraining: Int,
    val equipment: Set<String>,
    val blockedTags: Set<String> = emptySet(),
    val jointLimits: Map<Joint, Int> = emptyMap(),
    val excludedIds: Set<String> = emptySet(),
    val preferences: Map<String, Double> = emptyMap(),
    val favourites: Set<String> = emptySet(),
    val crowded: Boolean = false,
    /** Current rung per bodyweight/core ladder (family → exercise ID), from history. */
    val ladderRungs: Map<String, String> = emptyMap(),
    /** ADH-003: this block's main lifts (slot key → exercise ID), fixed once chosen. */
    val coreLifts: Map<String, String> = emptyMap(),
    /** ADH-003: last block's accessory choices (slot key → exercise ID), rotated away from when a close alternative exists. */
    val previousBlockChoices: Map<String, String> = emptyMap(),
    val library: List<Exercise> = Library.all,
) {
    /** EQ-001: barbell main lifts once technique is reliable (beyond beginner, or 8+ weeks in). */
    val techniqueReliable: Boolean get() = level != Level.BEGINNER || weeksTraining >= 8
}

/** Exercise selection for one slot (GEN-001 step 4, EQ-001, ADH-003, BW-001 ladders). Deterministic: ties by ID. */
object Selector {
    private val CORE_PATTERNS = setOf(Pattern.ANTI_EXTENSION, Pattern.ANTI_ROTATION, Pattern.ANTI_LATERAL_FLEXION)

    fun allowed(e: Exercise, ctx: SelectionContext): Boolean =
        !e.userAddOnly &&
            ctx.equipment.containsAll(e.equipment) &&
            e.equipment.none { it in Substitution.MOD001_EQUIPMENT } &&
            e.id !in ctx.excludedIds &&
            e.limitationTags.none { it in ctx.blockedTags } &&
            ctx.jointLimits.all { (j, lim) -> e.stress(j) <= lim } &&
            e.skill <= Substitution.levelNumber(ctx.level) + P.SUB_001.skill_margin

    /**
     * BW-001: on a ladder only one rung is eligible. Ladders can cross patterns (squat-family rungs 4–5
     * are split squats), so the rung is chosen among the ladder's exercises of the candidate's own
     * pattern: the highest one not above the user's current rung (or the level's default rung).
     */
    private fun rungOk(e: Exercise, ctx: SelectionContext): Boolean {
        val fam = e.family ?: return true
        val samePattern = ctx.library.filter { it.family == fam && it.pattern == e.pattern }.map { it.rung }.distinct().sorted()
        if (samePattern.isEmpty()) return true
        val reference = ctx.ladderRungs[fam]?.let { Library[it] }?.rung
            ?: minOf(ctx.level.pick(1, 2, 3), ctx.library.filter { it.family == fam }.maxOfOrNull { it.rung } ?: 1)
        val rung = samePattern.lastOrNull { it <= reference } ?: samePattern.first()
        return e.rung == rung
    }

    fun matches(e: Exercise, spec: SlotSpec): Boolean = when (spec.role) {
        SlotRole.POWER -> e.powerCapable
        SlotRole.CARRY -> e.pattern == Pattern.LOADED_CARRY
        SlotRole.ROTATION -> e.trains(Pattern.ROTATION)
        SlotRole.CORE -> spec.pattern != null && e.trains(spec.pattern) && (e.pattern in CORE_PATTERNS || e.pattern == Pattern.LOADED_CARRY || e.pattern == Pattern.HORIZONTAL_PULL)
        SlotRole.CARRY_OR_ROTATION -> false // resolved to CARRY or ROTATION by the planner
        // Jumps and ballistic throws (bodyweight power drills) only ever fill power slots: a box jump is not a squat main lift (CON-004, ORD-001).
        else -> !(e.powerCapable && e.loadType == LoadType.BODYWEIGHT) && when {
            spec.pattern == Pattern.ISOLATION -> e.pattern == Pattern.ISOLATION && spec.muscle != null && spec.muscle in e.primary
            spec.pattern == Pattern.LUNGE -> e.pattern == Pattern.LUNGE && e.unilateral
            else -> e.pattern == spec.pattern
        }
    }

    private fun role(spec: SlotSpec, ctx: SelectionContext): EquipmentRole = when {
        spec.role == SlotRole.MAIN -> EquipmentRole.MAIN_LIFT
        ctx.crowded -> EquipmentRole.CROWDED_ACCESSORY
        else -> EquipmentRole.ACCESSORY
    }

    fun score(e: Exercise, spec: SlotSpec, ctx: SelectionContext, usedThisWeek: Set<String>): Double {
        val roleFit = if (spec.role == SlotRole.SECONDARY)
            (EquipmentRoles.fit(e, EquipmentRole.MAIN_LIFT, ctx.techniqueReliable) + EquipmentRoles.fit(e, role(spec, ctx), ctx.techniqueReliable)) / 2
        else EquipmentRoles.fit(e, role(spec, ctx), ctx.techniqueReliable)
        val target = if (spec.role == SlotRole.MAIN) ctx.level.pick(2, 3, 3) else ctx.level.pick(1, 2, 2)
        val difficulty = 1.0 - Math.abs(e.difficulty - target) / 4.0
        val pref = ctx.preferences[e.id] ?: 0.5
        var s = 0.5 * roleFit + 0.25 * difficulty + 0.15 * pref + (if (e.id in ctx.favourites) 0.1 else 0.0)
        // Main lifts are loaded compounds where possible (they carry e1RM progression).
        if (spec.role == SlotRole.MAIN && !(e.dosedAsCompound && e.trackE1rm)) s -= 0.3
        if (spec.role == SlotRole.POWER) s += 0.1 * (5 - e.skill) / 4.0
        if (e.id in usedThisWeek) s -= 0.3
        return Math.round(s * 1000.0) / 1000.0
    }

    /**
     * Fill one slot. Main lifts keep the block's choice when it is still allowed (ADH-003);
     * accessories rotate away from last block's pick when a close alternative exists, except favourites.
     */
    fun select(spec: SlotSpec, ctx: SelectionContext, usedThisWeek: Set<String> = emptySet()): EngineResult<Exercise?> {
        ctx.coreLifts[spec.key]?.let { id ->
            val kept = Library[id]
            if (kept != null && allowed(kept, ctx) && matches(kept, spec)) return EngineResult(kept, listOf(Decision(DecisionKind.SUBSTITUTION,
                listOf(RuleIds.ADH_003, RuleIds.GEN_001), ReasonKey.CORE_LIFT_KEPT, inputs = mapOf("slot" to spec.key), outputs = mapOf("exercise" to id))))
        }
        var pool = ctx.library.filter { matches(it, spec) && allowed(it, ctx) && rungOk(it, ctx) }
        var adjacent = false
        // Nothing for this pattern today (kit, pain, limitation tags): an adjacent pattern keeps the slot (SUB-002 scores adjacency 0.5).
        if (pool.isEmpty() && spec.pattern != null && spec.pattern != Pattern.ISOLATION && spec.role in setOf(SlotRole.MAIN, SlotRole.SECONDARY, SlotRole.ACCESSORY)) {
            pool = ctx.library.filter { it.pattern.isAdjacentTo(spec.pattern) && it.pattern != Pattern.ISOLATION && allowed(it, ctx) && rungOk(it, ctx) &&
                (it.pattern != Pattern.LUNGE || it.unilateral) }
            adjacent = pool.isNotEmpty()
        }
        if (pool.isEmpty()) return EngineResult(null, listOf(Decision(DecisionKind.SUBSTITUTION, listOf(RuleIds.GEN_001, RuleIds.SUB_001),
            ReasonKey.SLOT_EMPTY, inputs = mapOf("slot" to spec.key))))
        val ranked = pool.map { it to score(it, spec, ctx, usedThisWeek) }.sortedWith(compareByDescending<Pair<Exercise, Double>> { it.second }.thenBy { it.first.id })
        var pick = ranked.first()
        var rotated = false
        val prev = ctx.previousBlockChoices[spec.key]
        if (spec.role != SlotRole.MAIN && prev == pick.first.id && pick.first.id !in ctx.favourites) {
            ranked.drop(1).firstOrNull { pick.second - it.second <= 0.1 + 1e-9 }?.let { pick = it; rotated = true }
        }
        val e = pick.first
        return EngineResult(e, listOf(Decision(DecisionKind.SUBSTITUTION, if (rotated) listOf(RuleIds.ADH_003, RuleIds.GEN_001) else listOf(RuleIds.GEN_001, RuleIds.EQ_001),
            if (rotated) ReasonKey.ACCESSORY_ROTATED else ReasonKey.SLOT_FILLED, inputs = mapOf("slot" to spec.key),
            outputs = mapOf("exercise" to e.id, "score" to pick.second, "loaded" to (e.loadType != LoadType.BODYWEIGHT), "adjacentPattern" to adjacent))))
    }
}
