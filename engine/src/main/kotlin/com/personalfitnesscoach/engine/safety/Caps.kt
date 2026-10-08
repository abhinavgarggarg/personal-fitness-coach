package com.personalfitnesscoach.engine.safety

import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds

/** Inputs for the conditional third HIIT session (HIIT-001). */
data class HiitContext(
    val level: Level,
    val tierToday: Tier,
    val fatigueSignals: Int,
    val workloadFlag: Boolean,
    val conditioningBlock: Boolean,
    val screening: ScreeningMode = ScreeningMode.STANDARD,
    val age: Int? = null,
    val inDeload: Boolean = false,
)

/**
 * SAF-005 hard caps. The engine never prescribes beyond these; neither the AI coach nor repeated
 * taps can bypass them (user additions go through [UserAdditions] instead).
 */
object Caps {
    fun weeklySetsPerMuscle(l: Level): Int = with(P.SAF_005.fractional_sets_per_muscle_week) { l.pick(beginner, intermediate, advanced) }
    fun directSetsPerMuscleSession(l: Level): Int = with(P.SAF_005.direct_sets_per_muscle_session) { l.pick(beginner, intermediate, advanced) }
    fun workingSetsPerSession(l: Level): Int = with(P.SAF_005.working_sets_per_session) { l.pick(beginner, intermediate, advanced) }
    fun weeklySsu(l: Level): Int = with(P.SAF_005.weekly_ssu) { l.pick(beginner, intermediate, advanced) }
    fun trainingDaysPerWeek(): Int = P.SAF_005.training_days_per_week
    fun intensityRisePctPerWeek(l: Level): Double = with(P.SAF_005.intensity_rise_per_lift_per_week_pct) { l.pick(beginner, intermediate.toDouble(), advanced.toDouble()) }

    /** INT-003 / SAF-005: planned RIR-0 exercises allowed in one session. */
    fun rir0ExercisesPerSession(l: Level, weeksTraining: Int, tier: Tier, inDeload: Boolean, screening: ScreeningMode): Int = when {
        screening != ScreeningMode.STANDARD -> 0
        tier.name !in P.INT_003.allowed_tiers -> 0
        inDeload -> 0
        l == Level.BEGINNER && weeksTraining < P.INT_003.beginner_lockout_weeks -> P.SAF_005.rir0_exercises_per_session.beginner_first_8_weeks
        else -> minOf(P.SAF_005.rir0_exercises_per_session.otherwise, P.INT_003.max_exercises_per_session)
    }

    /** HIIT-001 / AGE-001 / SAF-001 / DEL-003: HIIT sessions allowed this week. */
    fun hiitPerWeek(c: HiitContext): EngineResult<Int> {
        val max = when {
            c.screening != ScreeningMode.STANDARD || c.inDeload -> 0
            c.age != null && c.age >= 50 -> minOf(P.AGE_001.age_50.hiit_max, P.HIIT_001.default_max)
            c.level != Level.BEGINNER && c.tierToday == Tier.FULL && c.fatigueSignals == 0 && !c.workloadFlag && c.conditioningBlock ->
                P.HIIT_001.conditional_max
            else -> P.HIIT_001.default_max
        }
        val rules = listOf(RuleIds.HIIT_001, RuleIds.SAF_005) + (if (c.age != null && c.age >= 50) listOf(RuleIds.AGE_001) else emptyList())
        return EngineResult(max, listOf(Decision(DecisionKind.SAFETY, rules, ReasonKey.HIIT_CAP,
            inputs = mapOf("level" to c.level, "tier" to c.tierToday, "signals" to c.fatigueSignals, "flag" to c.workloadFlag, "block" to c.conditioningBlock, "age" to c.age),
            outputs = mapOf("hiitMax" to max))))
    }

    /** HIIT-001: ≥6 min of Z3 work, or a circuit at CR10 ≥7 for ≥8 min, counts as a HIIT session. */
    fun countsAsHiit(zone3WorkMinutes: Double, circuitCr10: Int? = null, circuitMinutes: Double = 0.0): Boolean =
        zone3WorkMinutes >= P.HIIT_001.count_if_z3_work_minutes_gte ||
            (circuitCr10 != null && circuitCr10 >= P.HIIT_001.count_if_circuit_cr10_gte && circuitMinutes >= P.HIIT_001.circuit_minutes_gte)
}

enum class AdditionVerdict { OK, WARN_CONFIRM, BLOCKED }

/** SAF-006: the user may deviate; beyond a cap needs per-addition confirmation; an absolute ceiling stops runaway. */
object UserAdditions {
    /**
     * @param muscleWeeklyAfter fractional weekly sets for the most-loaded muscle the addition touches, after it
     * @param sessionSetsAfter working sets in today's session after the addition
     * @param muscleSessionDirectAfter direct (primary) sets today for the most-loaded muscle the addition touches
     */
    fun addSets(level: Level, muscleWeeklyAfter: Double, sessionSetsAfter: Int, touchesPainfulRegion: Boolean, confirmed: Boolean,
                muscleSessionDirectAfter: Double): EngineResult<AdditionVerdict> {
        val weeklyCap = Caps.weeklySetsPerMuscle(level).toDouble()
        val beyondCap = muscleWeeklyAfter > weeklyCap + 1e-9 || sessionSetsAfter > Caps.workingSetsPerSession(level) ||
            muscleSessionDirectAfter > Caps.directSetsPerMuscleSession(level) + 1e-9
        val absWeekly = weeklyCap * P.SAF_006.absolute_ceiling.weekly_per_muscle_multiplier_of_cap
        val verdict = when {
            touchesPainfulRegion && P.SAF_006.block_painful_region -> AdditionVerdict.BLOCKED
            muscleWeeklyAfter > absWeekly + 1e-9 || sessionSetsAfter > P.SAF_006.absolute_ceiling.working_sets_per_session -> AdditionVerdict.BLOCKED
            beyondCap -> if (confirmed) AdditionVerdict.OK else AdditionVerdict.WARN_CONFIRM
            else -> AdditionVerdict.OK
        }
        return EngineResult(verdict, listOf(decision(verdict, mapOf("weeklyAfter" to muscleWeeklyAfter, "sessionAfter" to sessionSetsAfter,
            "directAfter" to muscleSessionDirectAfter, "painful" to touchesPainfulRegion, "confirmed" to confirmed, "beyondCap" to beyondCap))))
    }

    fun addHiit(hiitThisWeekAfter: Int, allowedThisWeek: Int, screening: ScreeningMode, confirmed: Boolean): EngineResult<AdditionVerdict> {
        val verdict = when {
            screening != ScreeningMode.STANDARD -> AdditionVerdict.BLOCKED // clearance needed first (SAF-001)
            hiitThisWeekAfter > P.SAF_006.absolute_ceiling.hiit_sessions_per_week -> AdditionVerdict.BLOCKED
            hiitThisWeekAfter > allowedThisWeek -> if (confirmed) AdditionVerdict.OK else AdditionVerdict.WARN_CONFIRM
            else -> AdditionVerdict.OK
        }
        return EngineResult(verdict, listOf(decision(verdict, mapOf("hiitAfter" to hiitThisWeekAfter, "allowed" to allowedThisWeek, "confirmed" to confirmed))))
    }

    private fun decision(v: AdditionVerdict, inputs: Map<String, Any?>) = Decision(DecisionKind.USER_ADDITION, listOf(RuleIds.SAF_006, RuleIds.SAF_005),
        when (v) { AdditionVerdict.OK -> ReasonKey.ADDITION_OK; AdditionVerdict.WARN_CONFIRM -> ReasonKey.ADDITION_WARN_CONFIRM; AdditionVerdict.BLOCKED -> ReasonKey.ADDITION_BLOCKED },
        inputs, mapOf("verdict" to v))
}
