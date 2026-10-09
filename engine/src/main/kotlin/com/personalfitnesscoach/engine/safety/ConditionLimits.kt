package com.personalfitnesscoach.engine.safety

import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.model.Zone

/** SAF-010 impact levels, least to most permissive (the table's `merge_orders.impact`). */
enum class ImpactLevel(val key: String) {
    NONE("none"), LOW("low"), LOW_VOLUME("low_volume"), ONLY_IF_ALREADY("only_if_already_doing_it"), AS_TOLERATED("as_tolerated"), ENCOURAGED("encouraged");

    /** Jumping power work and impact cardio may be planned (≤ 1 a week under CON-004). */
    val allowsImpact: Boolean get() = this >= ONLY_IF_ALREADY

    companion object { fun of(key: String): ImpactLevel = entries.first { it.key == key } }
}

/** SAF-010 interval permission, least to most permissive (`merge_orders.hiit`). */
enum class HiitPermission(val key: String) {
    NO("no"), AFTER_BASE("after_base"), AS_TOLERATED("as_tolerated"), YES("yes");

    companion object { fun of(key: String): HiitPermission = entries.first { it.key == key } }
}

/**
 * The merged health-condition limits for today (SAF-010: each control takes its most restrictive value). The defaults mean
 * "no condition": nothing is limited. Produced by [Conditions.resolve]; read by the week planner, the session generator and
 * the session validator.
 */
data class ConditionLimits(
    /** Table entries that apply (resolved: phases, control status and clearance applied). */
    val entries: Set<String> = emptySet(),
    /** Highest aerobic zone (AER-001). Z4 = no limit; the table's Z3 also rules out sprints (Z4). */
    val maxZone: Zone = Zone.Z4,
    val hiit: HiitPermission = HiitPermission.YES,
    /** Weeks of regular training before intervals (the highest entry value). */
    val hiitBaseWeeks: Int = 0,
    /** Intervals only on low-impact machines (FL-003 list). */
    val hiitLowImpactOnly: Boolean = false,
    /** Intervals only on these modalities (intersection of entries' lists); null = any. Empty = no intervals. */
    val hiitModalities: Set<Modality>? = null,
    val impact: ImpactLevel = ImpactLevel.AS_TOLERATED,
    /** Lowest reps in reserve on any working set (the highest entry value); null = the app's default. */
    val minRir: Double? = null,
    /** When set, [minRir] applies only to exercises with one of these tags (low back pain: spinal loading). */
    val minRirTags: Set<String>? = null,
    val failureAllowed: Boolean = true,
    val avoidTags: Set<String> = emptySet(),
    /** Exercises with these tags are done through a shorter, comfortable range (MOB-004). */
    val rangeLimitedTags: Set<String> = emptySet(),
    val jointLimits: Map<Joint, Int> = emptyMap(),
    val extraWarmupMin: Int = 0,
    val extraCooldownMin: Int = 0,
    /** Scheduling (type 2 diabetes): no more than this many days in a row without activity; null = no limit. */
    val maxConsecutiveInactiveDays: Int? = null,
    val strengthOnConsecutiveDays: Boolean = true,
    /** Clearance `always` not yet confirmed: SAF-001 conservative mode. */
    val conservative: Boolean = false,
    /** Effort by feel (RPE/talk test), not heart-rate numbers. */
    val effortByFeel: Boolean = false,
    /** Living with obesity: FL-003 impact gate (BMI ≥ 30) and low-impact intervals. */
    val obesity: Boolean = false,
    /** Osteoporosis: a short bone-loading block most days (CON-004 1.1.0 exemption) and required balance/back-extensor work. */
    val boneLoading: Boolean = false,
    val balanceSessionsPerWeek: Int = 0,
    val backExtensorSessionsPerWeek: Int = 0,
    /** FL-001 effects: the fat-loss goal is not offered / weight features are off. */
    val fatLossOffered: Boolean = true,
    val weightFeatures: Boolean = true,
    val prompts: List<String> = emptyList(),
    val stopSigns: List<String> = emptyList(),
    /** Clearance prompts to show now (entry IDs whose clearance should be asked for today). */
    val clearancePrompts: Set<String> = emptySet(),
    /** Entries that tell the user to follow their care provider instead of a training plan (`block_if` answered yes). */
    val blocked: Set<String> = emptySet(),
) {
    val any: Boolean get() = entries.isNotEmpty()

    /** True when intervals are allowed at all given `regularWeeks` of training. */
    fun hiitAllowed(regularWeeks: Int): Boolean = when (hiit) {
        HiitPermission.NO -> false
        HiitPermission.AFTER_BASE -> regularWeeks >= hiitBaseWeeks
        else -> true
    } && maxZone >= Zone.Z3 && hiitModalities?.isEmpty() != true && !conservative

    companion object { val NONE = ConditionLimits() }
}
