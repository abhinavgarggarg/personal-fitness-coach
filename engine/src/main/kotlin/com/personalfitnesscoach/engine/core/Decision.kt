package com.personalfitnesscoach.engine.core

/**
 * One explainable engine decision (Phase 1 A4.4). The engine never writes sentences:
 * it records which rules fired, on which inputs, with which outputs, plus a reason key
 * that the coach layer renders into plain language.
 */
data class Decision(
    val kind: DecisionKind,
    val ruleIds: List<String>,
    val reason: ReasonKey,
    val inputs: Map<String, Any?> = emptyMap(),
    val outputs: Map<String, Any?> = emptyMap(),
)

enum class DecisionKind {
    READINESS, TIER_CAP, LOAD_PRESCRIPTION, LOAD_CHANGE, REP_CHANGE, VOLUME_CHANGE,
    DELOAD, RETURN_TO_TRAINING, WARMUP, TIME_FIT, SUBSTITUTION, SAFETY, CALIBRATION,
    WORKLOAD_FLAG, USER_ADDITION, SCREENING,
}

/** Keys for the coach's plain-language templates (rendered outside the engine). */
enum class ReasonKey {
    // readiness
    READINESS_SCORED, TIER_CAPPED_BY_SLEEP, TIER_CAPPED_BY_ITEM, TIER_FLOOR_RAW_LOW,
    TIER_STEPPED_DOWN_FATIGUE, TIER_CHOSEN_BY_USER, TIER_CHOICE_REFUSED,
    // loads and progression
    LOAD_FROM_E1RM, LOAD_ROUNDED, LOAD_UP_TARGET_MET, LOAD_HOLD_EFFORT_HIGH, LOAD_HOLD_TECHNIQUE,
    LOAD_REDUCE_EFFORT_HIGH, LOAD_REDUCE_REPEATED, REPS_UP, RANGE_EXTENDED, LOAD_JUMP_REPS_RESET,
    LOAD_HOLD_RECOVERY, LOAD_HOLD_TOP_OF_RANGE, LOAD_CAPPED_WEEKLY, TECHNIQUE_CUE, LOAD_JUMP_UNDERLOADED, LOAD_DISCRETE_STEP, LOAD_FROM_PROGRESSION,
    INSESSION_LOAD_UP, INSESSION_LOAD_DOWN, INSESSION_LIMIT_REACHED, REGRESSION_OFFERED, FAILED_TARGETS_REVIEW,
    // volume, workload, deload
    ACCESSORIES_CUT_FATIGUE, WORKLOAD_SPIKE_FLAG, MONOTONY_FLAG, PLAN_CAPPED_BY_LOAD,
    DELOAD_NOW, LIGHTER_WEEK, DELOAD_BLOCK_END, PIVOT_WEEK, LIGHTER_WEEK_OFFER,
    // return to training
    MISSED_SHIFTED, RETURN_MODIFIED, RETURN_RAMP, RECALIBRATE, ILLNESS_REST, ILLNESS_RETURN, ILLNESS_MILD_CHOICE,
    // warm-up, time, substitution
    WARMUP_BUILT, WARMUP_COMPRESSED, TIME_COMPRESSED, TIME_EXTENDED, TIME_EXPRESS_OFFERED,
    SWAP_CHOSEN, SWAP_OFFERED_ONLY, SWAP_NONE_AVAILABLE, TIME_NOT_FITTABLE,
    // calibration
    CALIBRATION_STEP, CALIBRATION_DONE, CALIBRATION_STOPPED, CALIBRATION_CAPPED,
    // safety
    SCREEN_STANDARD, SCREEN_CONSERVATIVE, SCREEN_MODERATE_ONLY, SAFETY_STOP_RED_FLAG,
    PAIN_CONTINUE_CAUTION, PAIN_STOP_EXERCISE, PAIN_STOP_REGION, PAIN_SEE_PROFESSIONAL,
    VALIDATOR_TRIMMED, VALIDATOR_SWAPPED, VALIDATOR_REMOVED_HIIT, VALIDATOR_FALLBACK_LIGHT,
    VALIDATOR_PASSED, VALIDATOR_REMOVED_EXERCISE, VALIDATOR_EFFORT_RAISED, VALIDATOR_LOAD_LOWERED,
    VALIDATOR_CONDITIONING_CHANGED, HIIT_CAP, REGION_CONSERVATIVE, PATTERN_IMBALANCE,
    ADDITION_OK, ADDITION_WARN_CONFIRM, ADDITION_BLOCKED,
    // dose, conditioning and mobility (Part 2)
    REPS_FROM_GOAL, RIR_TARGET, AEROBIC_DURATION_UP, WHO_FLOOR_SHORT, MODALITY_CHOSEN, HIIT_PROTOCOL_CHOSEN,
    BW_HOLD, BW_STEP_UP, BW_STEP_DOWN, BW_ADD_LOAD, BW_LESS_ASSISTANCE, MOBILITY_PLANNED,
    // profile and adherence
    LEVEL_CLASSIFIED, LEVEL_PROMOTED, LEVEL_TEMPORARILY_LOWER, INJURY_SENSITIVE, SRPE_PROMPT,
    // program, week and session generation
    PROGRAM_PLANNED, BLOCK_CLOCK_PAUSED, BLOCK_RESTARTED, WEEK_PLANNED, WEEK_DAYS_REDUCED, DAY_SPACING_ADJUSTED,
    SESSION_GENERATED, SLOT_FILLED, SLOT_EMPTY, CORE_LIFT_KEPT, ACCESSORY_ROTATED, VOLUME_ALLOCATED,
    CONDITIONING_PLANNED, POWER_DROPPED_AFTER_HIIT, EXPRESS_SESSION, NEXT_IN_SEQUENCE, SWAP_OR_LATER, AEROBIC_CHECK_FAILED, ILLNESS_REST_DAY, LOAD_CANNOT_REDUCE,
}

/** Every engine entry point returns a value plus the decisions that produced it. */
data class EngineResult<T>(val value: T, val decisions: List<Decision> = emptyList()) {
    fun <R> map(f: (T) -> R): EngineResult<R> = EngineResult(f(value), decisions)
    operator fun plus(more: List<Decision>): EngineResult<T> = EngineResult(value, decisions + more)
}

/** Small helpers shared by the calculators. */
object Num {
    fun round2(x: Double): Double = Math.round(x * 100.0) / 100.0
    fun round1(x: Double): Double = Math.round(x * 10.0) / 10.0
    fun clamp(x: Double, lo: Double, hi: Double): Double = if (x < lo) lo else if (x > hi) hi else x
    fun clampInt(x: Int, lo: Int, hi: Int): Int = if (x < lo) lo else if (x > hi) hi else x
}
