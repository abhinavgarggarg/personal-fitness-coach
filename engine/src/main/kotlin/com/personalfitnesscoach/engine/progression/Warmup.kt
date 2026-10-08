package com.personalfitnesscoach.engine.progression

import com.personalfitnesscoach.engine.calc.PlateMath
import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.model.LoadType
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds

data class WarmupSet(val load: Double, val reps: IntRange)

/** Ramp-up sets (WU-002), warm-up length and compression (WU-001, WU-003, WU-004). */
object Warmup {
    private class Step(val pct: Double?, val reps: IntRange) // pct null = empty bar

    private fun reps(x: Any?): IntRange = when (x) {
        is Number -> x.toInt()..x.toInt()
        is List<*> -> (x[0] as Number).toInt()..(x[1] as Number).toInt()
        else -> error("bad rep spec $x")
    }

    private fun steps(spec: List<Any?>): List<Step> = spec.map {
        val pair = it as List<*>
        Step(if (pair[0] == "bar") null else (pair[0] as Number).toDouble(), reps(pair[1]))
    }

    private val FIRST = steps(P.WU_002.first_lift)
    private val SECOND = steps(P.WU_002.second_lift)

    /**
     * Ramp sets before the working sets of a main lift.
     * @param firstMainLift true for the session's first main lift, false for the second
     * @param e1rm current estimate, used for the "≥80% of e1RM" condition; null if unknown
     * @param barKg the bar weight for barbell lifts (ignored otherwise)
     */
    fun rampSets(
        workingLoad: Double,
        workingReps: Int,
        e1rm: Double?,
        loadType: LoadType,
        available: List<Double>,
        firstMainLift: Boolean = true,
        barKg: Double = 20.0,
    ): EngineResult<List<WarmupSet>> {
        if (loadType == LoadType.BODYWEIGHT || loadType == LoadType.TIME || loadType == LoadType.DISTANCE || available.isEmpty()) {
            return EngineResult(emptyList())
        }
        val out = ArrayList<WarmupSet>()
        val isBar = loadType == LoadType.BARBELL
        if (firstMainLift && isBar && workingLoad <= barKg + P.WU_002.light_threshold_kg_over_bar + 1e-9) {
            val single = P.WU_002.light_single_set
            val load = roundDown(workingLoad * single[0], available)
            if (load < workingLoad - 1e-9) out += WarmupSet(load, single[1].toInt()..single[1].toInt())
        } else {
            val heavy = workingReps <= P.WU_002.heavy_step_condition.reps_lte ||
                (e1rm != null && workingLoad >= e1rm * P.WU_002.heavy_step_condition.or_pct_e1rm_gte / 100.0)
            val plan = if (firstMainLift) FIRST else SECOND
            for ((i, s) in plan.withIndex()) {
                val lastStep = firstMainLift && i == plan.lastIndex
                if (lastStep && !heavy) continue
                val load = if (s.pct == null) {
                    if (!isBar) continue else barKg
                } else roundDown(workingLoad * s.pct, available)
                val prev = out.lastOrNull()?.load ?: -1.0
                if (load > prev + 1e-9 && load < workingLoad - 1e-9) out += WarmupSet(load, s.reps)
            }
        }
        return EngineResult(out, listOf(Decision(DecisionKind.WARMUP, listOf(RuleIds.WU_002), ReasonKey.WARMUP_BUILT,
            inputs = mapOf("working" to workingLoad, "reps" to workingReps, "first" to firstMainLift),
            outputs = mapOf("sets" to out.map { "${it.load}x${it.reps.first}" }))))
    }

    /** WU-003: under time pressure keep at most 3 ramp sets — drop the lightest ones first. */
    fun compressRamp(sets: List<WarmupSet>): List<WarmupSet> = if (sets.size <= 3) sets else sets.takeLast(3)

    /**
     * Warm-up length in minutes: 8–12 normally (WU-001), floor 5 when compressed (WU-003),
     * plus 2 extra raise minutes at age 50+ (WU-004, lower bound kept under compression).
     */
    fun minutes(planned: Double, compressed: Boolean, age: Int?): EngineResult<Double> {
        val extra = if (age != null && age >= 50) P.WU_004.age_50_extra_min[0].toDouble() else 0.0
        val total = P.WU_001.total_min
        val base = if (compressed) P.WU_003.floor_minutes.toDouble() else planned.coerceIn(total[0].toDouble(), total[1].toDouble())
        val m = base + extra
        val rules = listOf(if (compressed) RuleIds.WU_003 else RuleIds.WU_001) + if (extra > 0) listOf(RuleIds.WU_004) else emptyList()
        return EngineResult(m, listOf(Decision(DecisionKind.WARMUP, rules, if (compressed) ReasonKey.WARMUP_COMPRESSED else ReasonKey.WARMUP_BUILT,
            inputs = mapOf("planned" to planned, "age" to age), outputs = mapOf("minutes" to m))))
    }

    /** WU-004 / AGE-001: from 65 a balance drill is part of every warm-up (drill content arrives with the exercise library). */
    fun balanceDrillRequired(age: Int?): Boolean = age != null && age >= 65 && P.WU_004.age_65_balance_drill

    /** Never below the 5-minute floor (+ age extra): the warm-up is never removed. */
    fun floorMinutes(age: Int?): Double = minutes(0.0, compressed = true, age = age).value

    private fun roundDown(target: Double, available: List<Double>): Double =
        available.filter { it <= target + 1e-9 }.maxOrNull() ?: PlateMath.choose(target, available)
}
