package com.personalfitnesscoach.engine.safety

import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Zone

/** One time phase of an entry (postpartum: weeks 0–6, 6–12, 12–52 after birth). */
data class ConditionPhase(
    val fromWeek: Int,
    val toWeek: Int,
    val maxZone: Zone,
    /** "no", "after_base", … or "after_impact_unlocked". */
    val hiit: String,
    val minRir: Int?,
    /** "none" or "progressive" (impact returns once the user records passing the return-to-impact checks). */
    val impact: String,
    /** Something the user confirms before this phase's limits apply (else the previous phase's limits stay). */
    val attest: String?,
    val until: String?,
    val impactUnlock: String?,
    val focus: List<String>,
)

/** Limits after a doctor's OK of a given scope (heart). */
data class AfterClearance(val scope: String, val maxZone: Zone?, val minRir: Int?, val hiit: String?)

/**
 * One row of the health-condition table (rules/health_conditions_v1.0.json, SAF-010), generated into
 * [GeneratedConditions]. Strings that the merge orders define (clearance, hiit, impact) stay strings here and are
 * interpreted by [Conditions]; every key in the table maps to a field, and the generator fails on unknown keys.
 */
data class ConditionEntry(
    val id: String,
    val name: String,
    val group: String,
    val parent: String?,
    val optional: Boolean,
    val clearance: String,
    val clearancePromptNow: Boolean,
    val clearanceText: String?,
    /** Sub-flags (snake_case of the table text) that make the clearance "always" (cancer). */
    val clearanceAlwaysIf: List<String>,
    /** Null when the entry is "by_phase". */
    val maxZone: Zone?,
    val hiit: String,
    val hiitBaseWeeks: Int,
    val hiitNeedsScope: String?,
    val hiitLowImpactOnly: Boolean,
    /** MOD-002 matrix keys; null = any. */
    val hiitModalities: Set<String>?,
    val failureAllowed: Boolean,
    val impact: String,
    val impactMax: String?,
    val impactNoneIf: List<String>,
    val impactUnlockAfterWeeks: Int?,
    val impactUnlockOptIn: Boolean,
    val impactUnlockNeedsPainRule: Boolean,
    /** Null when absent (app default) or "by_phase". */
    val minRir: Int?,
    val minRirTags: Set<String>?,
    val avoidTags: Set<String>,
    val avoidTagsAtStart: Set<String>,
    val avoidTagsEarly: Set<String>,
    val avoidTagsFromWeek: Map<Int, Set<String>>,
    val avoidSupineAnyTimeIf: String?,
    val rangeLimitedTags: Set<String>,
    val jointLimits: Map<Joint, Int>,
    val jointLimitUnlock: Map<Joint, Int>,
    val jointLimitUnlockAfterWeeks: Int?,
    val jointLimitUnlockNeedsPainRule: Boolean,
    val extraWarmupMin: Int,
    val extraCooldownMin: Int,
    val maxZoneAfterWeeks: Int?,
    val maxZoneAfterWeeksZone: Zone?,
    val maxZoneAfterClearance: Zone?,
    val maxZoneIfPreviouslyVigorousAndOk: Zone?,
    val afterClearance: List<AfterClearance>,
    val askControlStatus: Boolean,
    val ifNotControlledMaxZone: Zone?,
    val ifNotControlledHiit: String?,
    val ifNotControlledPrompt: String?,
    val maxConsecutiveInactiveDays: Int?,
    val strengthOnConsecutiveDays: Boolean?,
    val phases: List<ConditionPhase>,
    val impactChecks: List<String>,
    val boneLoading: Boolean,
    val boneLoadingText: String?,
    val boneLoadingImpactsMin: Int?,
    val balancePerWeek: Int,
    val backExtensorPerWeek: Int,
    val effortBy: String?,
    val defaultOrder: String?,
    val warmupStyle: String?,
    /** Heart sub-flags: flag → what it means for training. */
    val subFlags: Map<String, String>,
    val flareJointLimits: Map<Joint, Int>,
    val flareAvoidTags: Set<String>,
    val flareKeep: List<String>,
    val flareExitSessions: Int?,
    val painRuleDuringMax: Int?,
    val blockIf: List<String>,
    val attestIf: List<String>,
    val asks: List<String>,
    val prompts: List<String>,
    val stopSigns: List<String>,
    val environment: List<String>,
    val positionsNote: String?,
    val neverRecommend: List<String>,
    val goalEffects: String?,
    val goalEffectsIf: String?,
    val notEncoded: List<String>,
    val pendingVerification: List<String>,
    val sources: List<String>,
    val confidence: String,
)
