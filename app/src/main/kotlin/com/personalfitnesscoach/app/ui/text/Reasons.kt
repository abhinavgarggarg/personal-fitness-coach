package com.personalfitnesscoach.app.ui.text

/**
 * Which decisions explain a screen ("Why it looks like this"). Routine steps that every session has (the plan was made, the safety
 * check passed, a weight was rounded …) stay in the full "Why?" log and are not repeated here, so the lines shown are the ones that
 * changed something for the user (COACH-001: brief).
 */
object Reasons {
    private val ROUTINE = setOf(
        "READINESS_SCORED", "LOAD_FROM_E1RM", "LOAD_ROUNDED", "LOAD_FROM_PROGRESSION", "LOAD_DISCRETE_STEP", "VALIDATOR_PASSED", "REPS_FROM_GOAL",
        "RIR_TARGET", "MODALITY_CHOSEN", "HIIT_PROTOCOL_CHOSEN", "MOBILITY_PLANNED", "PROGRAM_PLANNED", "WEEK_PLANNED", "SESSION_GENERATED",
        "SLOT_FILLED", "CORE_LIFT_KEPT", "ACCESSORY_ROTATED", "VOLUME_ALLOCATED", "CONDITIONING_PLANNED", "NEXT_IN_SEQUENCE", "WARMUP_BUILT",
        "CONDITIONS_MERGED", "CONDITION_LIMIT_APPLIED", "CONDITION_STATUS_APPLIED", "LEVEL_CLASSIFIED", "ACTIVITY_TARGET_SET", "FAT_LOSS_MIX",
        "WALKS_PLANNED", "BALANCE_PLANNED", "SCREEN_STANDARD", "STEP_BASELINE_PENDING", "STEP_TARGET_SET", "STEP_TARGET_UP", "STEP_TARGET_HELD",
        "STEP_TARGET_DOWN", "BRISK_WALKS_COUNTED", "BW_HOLD", "SRPE_PROMPT", "WEIGHT_TREND", "WAIST_AVERAGED", "AEROBIC_DURATION_UP",
        "GOAL_DEFAULT_CHOSEN", "BONE_LOADING_VARIANT", "SWAP_OR_LATER", "ADDITION_OK", "STRENGTH_TREND",
    )

    /** What happened during the session itself (a set, a swap, a pain report): shown at the time, not as "what changed for next time". */
    private val IN_SESSION = listOf("INSESSION_", "CALIBRATION_STEP", "PAIN_", "SAFETY_", "SWAP_", "TIME_", "VALIDATOR_", "ADDITION_", "TIER_", "SRPE_")

    /** The summary's "What changed for next time" (A8): only the decisions about the next sessions. */
    fun nextTime(names: List<String>, max: Int = 8): List<String> = shown(names, Int.MAX_VALUE).filter { n -> IN_SESSION.none { n.startsWith(it) } }.take(max)

    /** The distinct reasons worth a line, in the order they were decided, at most [max]. */
    fun shown(names: List<String>, max: Int = 8): List<String> = names.filter { it !in ROUTINE }.distinct().take(max)
}
