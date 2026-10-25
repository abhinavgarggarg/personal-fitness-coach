package com.personalfitnesscoach.engine.safety

import com.personalfitnesscoach.engine.calc.Ssu
import com.personalfitnesscoach.engine.calc.Volume
import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.model.Exercise
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.model.Muscle
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.model.Zone
import com.personalfitnesscoach.engine.conditioning.ModalitySelection
import com.personalfitnesscoach.engine.planning.SubContext
import com.personalfitnesscoach.engine.planning.Substitution
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds

/** One planned exercise in a session. */
data class SessionExercise(
    val exercise: Exercise,
    val sets: Int,
    val reps: Int,
    val targetRir: Double,
    /** Planned load as a fraction of today's normal working load (1.0 = normal). */
    val loadFactor: Double = 1.0,
    val main: Boolean = false,
    /** The last set is planned to failure (RIR 0, INT-003). */
    val lastSetToFailure: Boolean = false,
    /** Target RIR in the FULL-day plan, so the MODIFIED +1 RIR shift (RDY-004) can be checked. */
    val fullTierRir: Double? = null,
    /**
     * CAL-001: the weight is still being found today. The ramp's sets start easy (5+ in reserve), so for VOL-007 the item is expected to
     * count as two sets at the target effort ([SessionValidator.CALIBRATION_SETS_COUNTED]): the set that finds the weight and the hard
     * one before it. Its logged sets are counted at their own effort afterwards.
     */
    val calibrating: Boolean = false,
)

/** HIIT-002 protocol families (null = not an interval protocol). */
enum class HiitProtocol { LONG, MEDIUM, SHORT, SPRINT }

data class ConditioningBlock(
    val modality: Modality,
    val zone: Zone,
    val workMinutes: Double,
    val restMinutes: Double = 0.0,
    val hiit: Boolean = false,
    val impact: Int = 0,
    val protocol: HiitProtocol? = null,
    /**
     * Work seconds of one interval repeat (HIIT-002), or null for steady work. An interval block only changes by whole repeats, so
     * what the validator approves is exactly what is shown and played (review R8-01).
     */
    val repSec: Int? = null,
) {
    /** HIIT-001: flagged HIIT, an interval protocol, any Z4 work, or ≥ 6 min of Z3 work. */
    val countsAsHiit: Boolean
        get() = hiit || protocol != null || zone == Zone.Z4 ||
            (zone == Zone.Z3 && workMinutes >= P.HIIT_001.count_if_z3_work_minutes_gte)
    val totalMinutes get() = workMinutes + restMinutes
}

data class Session(
    val tier: Tier,
    val exercises: List<SessionExercise>,
    val conditioning: List<ConditioningBlock> = emptyList(),
    /** Planned warm-up and cool-down minutes; null = not given (a bare test session). Checked against WU-003/TIME-001 + SAF-010 extras. */
    val warmupMinutes: Double? = null,
    val cooldownMinutes: Double? = null,
    /** CON-004 1.1.0 / SAF-010: the bone-loading block's variant, or null for none. */
    val boneLoading: BoneLoadingVariant? = null,
) {
    val workingSets get() = exercises.sumOf { it.sets }
    /** Z3 work in blocks that do not count as HIIT on their own; ≥ 6 min in total counts as one HIIT (HIIT-001). */
    val shortZ3Minutes get() = conditioning.filter { !it.countsAsHiit && it.zone == Zone.Z3 }.sumOf { it.workMinutes }
    val hiitBlocks get() = conditioning.count { it.countsAsHiit } + if (shortZ3Minutes >= P.HIIT_001.count_if_z3_work_minutes_gte) 1 else 0
}

/** Everything the validator checks a session against. */
data class ValidationContext(
    val level: Level,
    /** Weeks of training; 0 when unknown (fail-closed for base periods and the beginner failure ban; review R3-14). */
    val weeksTraining: Int = 0,
    /** Age for the warm-up floor (WU-004) and age caps. */
    val age: Int? = null,
    val screening: ScreeningMode = ScreeningMode.STANDARD,
    /** SAF-003 joint limits for today (0 = no loading of that joint). */
    val jointLimits: Map<Joint, Int> = emptyMap(),
    /** SAF-004 persistent-pain regions. */
    val regions: List<RegionConstraint> = emptyList(),
    val blockedTags: Set<String> = emptySet(),
    val excludedIds: Set<String> = emptySet(),
    /** MOD-001 2.0.0: conditioning modalities this user doesn't use. */
    val excludedModalities: Set<Modality> = emptySet(),
    val weekSetsSoFar: Map<Muscle, Double> = emptyMap(),
    val weekSsuSoFar: Double = 0.0,
    /** Ssu.weeklyLimit for this user; null = level ceiling. */
    val weeklySsuLimit: Double? = null,
    val hiitThisWeekSoFar: Int = 0,
    val hiitAllowedThisWeek: Int = P.HIIT_001.default_max,
    /** HIIT-003: screening clear, calibration done and ≥ 3 weeks of Z1 base. Fail-closed: false unless the caller confirms. */
    val hiitBaseReady: Boolean = false,
    val sprintsThisWeekSoFar: Int = 0,
    val hoursSinceLastHiit: Double? = null,
    val hoursToNextHeavyLower: Double? = null,
    /** CON-004: impact sessions already done this week, and whether heavy legs are tomorrow. */
    val impactSessionsThisWeekSoFar: Int = 0,
    val heavyLegsNextDay: Boolean = false,
    val weekLoadSoFar: Double = 0.0,
    /** LOAD-005 planning cap for the week (Workload.planningCap), null before 14 days of history. */
    val weeklyLoadCap: Double? = null,
    /** Personal AU per SSU (VOL-007 k), null until learned. */
    val k: Double? = null,
    /**
     * Last completed week's SSU; before k is learned LOAD-005 is applied to SSU week over week (D-041).
     * Pass it from day 14 (LOAD-006: no planning cap before 14 days of history).
     */
    val lastWeekSsu: Double? = null,
    /** This week follows its own ramp (deload, post-deload or return-to-training week: DEL-004, REG-004), so LOAD-005 does not apply. */
    val loadCapExempt: Boolean = false,
    /**
     * VOL-007 / LOAD-005 week pacing: the share of what is left of the week's SSU and load budget that this session may use. It is
     * this session's planned SSU over the planned SSU of every session still to do this week, this one included; 1.0 = all of what is
     * left. Without it, a plan that grew faster than the caps was trimmed only in the week's last session, sometimes down to nothing
     * (Part 8 journey test). Pacing only ever lowers the limit.
     */
    val weekShare: Double = 1.0,
    /**
     * The weekly limits' floor in SSU (D-110, [WeeklyLimits.PLAN_FLOOR_PCT] of the week's plan, never above the spike guard): neither
     * VOL-007 nor LOAD-005 cuts the week below it (paced like the limits). Null = no floor.
     */
    val weekFloorSsu: Double? = null,
    val inDeload: Boolean = false,
    /**
     * Working sets of the FULL-day plan, so tier and deload set cuts (RDY-004, DEL-003) can be checked.
     * When unknown, the cut is applied to the level's session cap instead (fail-closed).
     */
    val fullTierWorkingSets: Int? = null,
    /** Equipment here today; null = unknown (bare test sessions). An empty set means bodyweight only (EQ-003; review R3-10). */
    val equipmentToday: Set<String>? = null,
    val library: List<Exercise> = emptyList(),
    /**
     * FL-003 day gates from the week plan (review R3-09): impact work allowed at all (under 50, no obesity, no lower-limb pain for the
     * fat-loss goal), and the machines intervals may use (null = any).
     */
    val planImpactAllowed: Boolean = true,
    val planIntervalModalities: Set<Modality>? = null,
    /** SAF-010 merged health-condition limits: tags, joints, zone, intervals, impact, effort and failure. */
    val conditions: ConditionLimits = ConditionLimits.NONE,
    /** CON-004 2.0.0: small hops may be the bone-loading block today (else heel drops only). */
    val boneHopsAllowed: Boolean = true,
    /** Resistance work was done yesterday (SAF-010 "no strength on consecutive days", type 2 diabetes; D-071). */
    val strengthYesterday: Boolean = false,
) {
    /** Tags to avoid today: limitation tags plus the condition entries' (SAF-010). */
    val allBlockedTags: Set<String> get() = blockedTags + conditions.avoidTags
    /** Joint limits today: pain limits and condition limits, the lower of the two per joint. */
    val allJointLimits: Map<Joint, Int> get() =
        if (conditions.jointLimits.isEmpty()) jointLimits
        else (jointLimits.keys + conditions.jointLimits.keys).associateWith { minOf(jointLimits[it] ?: 4, conditions.jointLimits[it] ?: 4) }
    /** SAF-001 conservative mode, also while a condition's doctor's OK is outstanding (SAF-010 "always"). */
    val effectiveScreening: ScreeningMode get() = if (conditions.conservative) ScreeningMode.CONSERVATIVE else screening
}

data class Violation(val ruleId: String, val code: String, val index: Int = -1, val detail: String = "")

data class ValidatedSession(val session: Session, val fallbackUsed: Boolean, val corrections: Int)

/**
 * Joint stress (0–4) of each conditioning modality, used to keep conditioning out of a painful
 * region (SAF-003, SAF-004). Expert Practice values, decision D-042; REQUIRES VALIDATION.
 */
object ModalityJoints {
    private val TABLE: Map<Modality, Map<Joint, Int>> = mapOf(
        Modality.ROWER to mapOf(Joint.KNEE to 2, Joint.HIP to 2, Joint.SPINE to 2, Joint.SHOULDER to 1, Joint.ELBOW to 1),
        Modality.SKIERG to mapOf(Joint.SHOULDER to 2, Joint.SPINE to 2, Joint.HIP to 1, Joint.ELBOW to 1),
        Modality.ELLIPTICAL to mapOf(Joint.KNEE to 1, Joint.HIP to 1, Joint.ANKLE to 1),
        Modality.SLED to mapOf(Joint.KNEE to 2, Joint.HIP to 2, Joint.ANKLE to 2, Joint.SPINE to 1),
        Modality.BATTLE_ROPES to mapOf(Joint.SHOULDER to 2, Joint.ELBOW to 1, Joint.WRIST to 1, Joint.SPINE to 1),
        Modality.KETTLEBELL to mapOf(Joint.HIP to 2, Joint.SPINE to 2, Joint.WRIST to 1, Joint.SHOULDER to 1),
        Modality.CARRIES to mapOf(Joint.SPINE to 2, Joint.WRIST to 1, Joint.SHOULDER to 1, Joint.HIP to 1),
        Modality.MEDBALL to mapOf(Joint.SHOULDER to 2, Joint.SPINE to 2, Joint.ELBOW to 1, Joint.WRIST to 1),
        Modality.BODYWEIGHT_CIRCUIT to mapOf(Joint.KNEE to 2, Joint.SHOULDER to 2, Joint.WRIST to 2, Joint.ANKLE to 2, Joint.HIP to 1),
        Modality.JUMP_ROPE to mapOf(Joint.ANKLE to 3, Joint.KNEE to 2),
        // MOD-001 2.0.0 machines (Expert Practice, D-066).
        Modality.TREADMILL_WALK to mapOf(Joint.KNEE to 1, Joint.HIP to 1, Joint.ANKLE to 1),
        Modality.STATIONARY_BIKE to mapOf(Joint.KNEE to 1, Joint.HIP to 1),
        Modality.AIR_BIKE to mapOf(Joint.KNEE to 1, Joint.HIP to 1, Joint.SHOULDER to 1, Joint.ELBOW to 1),
        Modality.STAIR_MACHINE to mapOf(Joint.KNEE to 2, Joint.HIP to 2, Joint.ANKLE to 1),
        Modality.TREADMILL_RUN to mapOf(Joint.KNEE to 3, Joint.ANKLE to 3, Joint.HIP to 2),
    )

    fun stress(m: Modality, j: Joint): Int = TABLE[m]?.get(j) ?: 0
}

/**
 * SAF-008 final pre-render validator. [violations] is the single definition of "valid";
 * [validate] corrects (trim, swap, raise effort, lower load, change conditioning) until no
 * violation remains, and otherwise falls back to a LIGHT session, then Z1 only, then rest.
 * It never throws and never returns an unvalidated session.
 */
object SessionValidator {
    /**
     * A weight still being found is expected to count as this many sets at the target effort (VOL-007): a ramp's last two sets are
     * usually within 4 reps in reserve (review R8-06, which found one set too few).
     */
    const val CALIBRATION_SETS_COUNTED = P.VOL_007.calibration_sets_counted
    private const val MAX_STEPS = 500
    private val IMPACT_JOINTS = setOf(Joint.KNEE, Joint.ANKLE, Joint.HIP, Joint.SPINE)
    /** Replacement order for conditioning; a no-jump bodyweight circuit is last because it needs no equipment (review R3-10). */
    private val SWAP_ORDER = listOf(Modality.ELLIPTICAL, Modality.ROWER, Modality.SKIERG, Modality.STATIONARY_BIKE, Modality.TREADMILL_WALK,
        Modality.BODYWEIGHT_CIRCUIT)

    // ---------------------------------------------------------------- checks

    fun violations(s: Session, c: ValidationContext): List<Violation> {
        val v = ArrayList<Violation>()
        val strength = s.exercises
        // MOD-001, pain limits, CON-004 impact for conditioning.
        s.conditioning.forEachIndexed { i, b ->
            if (b.modality in c.excludedModalities) v += Violation(RuleIds.MOD_001, "MODALITY", i)
            else if (b.modality in c.conditions.avoidModalities) v += Violation(RuleIds.SAF_010, "MODALITY", i)
            else if (c.equipmentToday != null && !ModalitySelection.available(b.modality, c.equipmentToday)) v += Violation(RuleIds.SUB_001, "MODALITY", i)
            else if (!modalityAllowed(b.modality, c)) v += Violation(RuleIds.SAF_003, "CONDITIONING_JOINT", i)
            if (b.impact > 0 && !impactAllowed(c)) v += Violation(RuleIds.CON_004, "IMPACT_NOT_ALLOWED", i)
            if (b.impact > 0 && c.regions.any { it.noJumping && it.region in IMPACT_JOINTS }) v += Violation(RuleIds.SAF_004, "IMPACT_NOT_ALLOWED", i)
            // SAF-010: the highest zone and which machines intervals may use.
            if (b.zone > c.conditions.maxZone) v += Violation(RuleIds.SAF_010, "ZONE_ABOVE_LIMIT", i)
            else if (b.countsAsHiit && !intervalModalityAllowed(b.modality, c)) v += Violation(RuleIds.SAF_010, "INTERVAL_MODALITY", i)
        }
        // WU-003 / TIME-001 / SAF-010: the warm-up and cool-down never go below their floors, condition extras included (review R3-02).
        // RECOVERY days hold only the optional easy Z1 block, which starts gently by itself (re-review N2).
        if (s.tier != Tier.RECOVERY && (s.exercises.isNotEmpty() || s.conditioning.isNotEmpty())) {
            val wFloor = com.personalfitnesscoach.engine.planning.TimeBudget.warmupFloor(c.age, c.conditions.extraWarmupMin.toDouble())
            if (s.warmupMinutes != null && s.warmupMinutes < wFloor - 1e-9)
                v += Violation(if (c.conditions.extraWarmupMin > 0) RuleIds.SAF_010 else RuleIds.WU_003, "WARMUP_SHORT", detail = "${s.warmupMinutes}<$wFloor")
            val cFloor = P.TIME_001.cooldown_min_minutes + c.conditions.extraCooldownMin.toDouble()
            if (s.cooldownMinutes != null && s.cooldownMinutes < cFloor - 1e-9)
                v += Violation(if (c.conditions.extraCooldownMin > 0) RuleIds.SAF_010 else RuleIds.TIME_001, "COOLDOWN_SHORT", detail = "${s.cooldownMinutes}<$cFloor")
        }
        // CON-004 1.1.0 / SAF-010: a bone-loading block only with the osteoporosis entry and within pain limits and blocked tags (review R3-11).
        s.boneLoading?.let { bl ->
            if (!c.conditions.boneLoading || !BoneLoading.allowed(bl, c.allJointLimits, c.allBlockedTags, c.regions, hopsAllowed = c.boneHopsAllowed))
                v += Violation(RuleIds.SAF_010, "BONE_LOADING_NOT_ALLOWED", detail = bl.name)
        }
        // SAF-010 (type 2 diabetes): never resistance work on consecutive days — enforced here so no screen or moved session can bypass it.
        if (strength.isNotEmpty() && c.strengthYesterday && !c.conditions.strengthOnConsecutiveDays) v += Violation(RuleIds.SAF_010, "STRENGTH_CONSECUTIVE")
        // Exclusions, pain limits, tags (limitation and SAF-010), impact for exercises.
        strength.forEachIndexed { i, e -> if (!exerciseAllowed(e.exercise, c)) v += Violation(RuleIds.SAF_003, "EXERCISE_NOT_ALLOWED", i, e.exercise.id) }
        // RDY-004 tier compliance.
        if (s.tier == Tier.RECOVERY) {
            if (strength.isNotEmpty()) v += Violation(RuleIds.RDY_004, "RECOVERY_NO_RESISTANCE")
            s.conditioning.forEachIndexed { i, b ->
                if (b.zone != Zone.Z1 || b.countsAsHiit || b.totalMinutes > P.RDY_004.RECOVERY.optional_z1_minutes[1]) v += Violation(RuleIds.RDY_004, "Z1_ONLY", i)
            }
        }
        if (s.tier == Tier.LIGHT || c.inDeload) {
            s.conditioning.forEachIndexed { i, b -> if (b.zone != Zone.Z1 || b.countsAsHiit) v += Violation(if (c.inDeload) RuleIds.DEL_003 else RuleIds.RDY_004, "Z1_ONLY", i) }
        }
        if (s.tier == Tier.LIGHT && s.conditioning.sumOf { it.totalMinutes } > P.RDY_004.LIGHT.z1_minutes[1] + 1e-9) v += Violation(RuleIds.RDY_004, "LIGHT_CONDITIONING_MINUTES")
        val setCap = tierSetCap(s.tier, c)
        if (setCap != null && s.workingSets > setCap) v += Violation(if (c.inDeload) RuleIds.DEL_003 else RuleIds.RDY_004, "TIER_SETS", detail = "$setCap")
        val minRir = minRirFor(s.tier, c)
        strength.forEachIndexed { i, e ->
            if (e.targetRir < requiredRir(e, s.tier, c, minRir) - 1e-9) v += Violation(RuleIds.RDY_004, "EFFORT_TOO_HIGH", i)
            if (e.loadFactor > maxLoadFor(e, s.tier, c) + 1e-9) v += Violation(RuleIds.RDY_004, "LOAD_TOO_HIGH", i)
            if (e.sets < 1) v += Violation(RuleIds.SAF_008, "EMPTY_EXERCISE", i)
        }
        // INT-003 failure policy.
        val rir0Allowed = Caps.rir0ExercisesPerSession(c.level, c.weeksTraining, s.tier, c.inDeload, c.effectiveScreening)
        var failures = 0
        strength.forEachIndexed { i, e ->
            if (e.lastSetToFailure) {
                failures++
                val regionNoFailure = regionsFor(e.exercise, c).any { it.noFailure }
                if (!e.exercise.failureSafe || failures > rir0Allowed || regionNoFailure) v += Violation(RuleIds.INT_003, "FAILURE_NOT_ALLOWED", i)
                else if (!c.conditions.failureAllowed) v += Violation(RuleIds.SAF_010, "FAILURE_NOT_ALLOWED", i)
            }
        }
        // VOL-005 direct sets per muscle per session; VOL-008 / SAF-005 working sets per session.
        val direct = Volume.directPerSession(strength.map { it.exercise to it.sets.toDouble() })
        val directCap = Caps.directSetsPerMuscleSession(c.level)
        for ((m, sets) in direct) if (sets > directCap + 1e-9) v += Violation(RuleIds.VOL_005, "DIRECT_SETS", detail = m.name)
        if (s.workingSets > Caps.workingSetsPerSession(c.level)) v += Violation(RuleIds.VOL_008, "SESSION_SETS")
        // SAF-005 weekly fractional sets per muscle.
        val weeklyCap = Caps.weeklySetsPerMuscle(c.level)
        val credit = Volume.weekly(strength.map { it.exercise to it.sets.toDouble() })
        for ((m, add) in credit) if ((c.weekSetsSoFar[m] ?: 0.0) + add > weeklyCap + 1e-9) v += Violation(RuleIds.SAF_005, "WEEKLY_SETS", detail = m.name)
        // HIIT and Z3: SAF-001 screening, HIIT-001/003/004/005, CON-003, RDY-004 tiers, DEL-003.
        val z3Allowed = c.effectiveScreening == ScreeningMode.STANDARD && !c.inDeload && (s.tier == Tier.FULL || s.tier == Tier.MODIFIED)
        val hiitAllowed = c.effectiveScreening == ScreeningMode.STANDARD && !c.inDeload && s.tier == Tier.FULL && c.hiitBaseReady &&
            c.conditions.hiitAllowed(c.weeksTraining) &&
            (c.hoursSinceLastHiit ?: Double.MAX_VALUE) >= P.HIIT_004.min_hours &&
            (c.hoursToNextHeavyLower ?: Double.MAX_VALUE) >= P.CON_003.hours_before_heavy_lower
        s.conditioning.forEachIndexed { i, b ->
            if (b.countsAsHiit && !hiitAllowed) v += Violation(RuleIds.HIIT_004, "HIIT_NOT_ALLOWED", i)
            else if (!b.countsAsHiit && b.zone >= Zone.Z3 && !z3Allowed) v += Violation(RuleIds.SAF_001, "Z3_NOT_ALLOWED", i)
            if (b.protocol == HiitProtocol.SPRINT && (c.level != Level.ADVANCED || c.sprintsThisWeekSoFar >= P.HIIT_002.sprint.per_week_max ||
                    c.conditions.maxZone < Zone.Z4)) v += Violation(RuleIds.HIIT_002, "SPRINT_NOT_ALLOWED", i)
            val workCap = hiitWorkCap(b)
            if (workCap != null && b.workMinutes > workCap + 1e-9) v += Violation(RuleIds.HIIT_005, "HIIT_WORK_CAP", i)
        }
        if (s.shortZ3Minutes >= P.HIIT_001.count_if_z3_work_minutes_gte && !hiitAllowed) {
            v += Violation(RuleIds.HIIT_001, "HIIT_NOT_ALLOWED", s.conditioning.indexOfLast { !it.countsAsHiit && it.zone == Zone.Z3 })
        }
        if (s.hiitBlocks > 1 || (s.hiitBlocks > 0 && c.hiitThisWeekSoFar + 1 > c.hiitAllowedThisWeek)) v += Violation(RuleIds.HIIT_001, "HIIT_COUNT")
        if (strength.isNotEmpty() && s.conditioning.sumOf { it.totalMinutes } > P.HIIT_005.post_strength_conditioning_min_max + 1e-9) v += Violation(RuleIds.HIIT_005, "POST_STRENGTH_CONDITIONING")
        // VOL-007 weekly SSU and LOAD-005 planned load.
        val ssu = sessionSsu(s)
        val ssuLimit = paced(weeklySsuLimit(c), c.weekSsuSoFar, c)
        if (ssu > 0 && c.weekSsuSoFar + ssu > ssuLimit + 1e-9) v += Violation(RuleIds.VOL_007, "WEEKLY_SSU")
        if (ssu > 0 && overLoadCap(c, ssu)) v += Violation(RuleIds.LOAD_005, "WEEKLY_LOAD")
        return v
    }

    fun sessionSsu(s: Session): Double =
        Ssu.sets(s.exercises.flatMap { e -> List(if (e.calibrating) minOf(e.sets, CALIBRATION_SETS_COUNTED) else e.sets) { e.exercise.costClass to e.targetRir } }) +
            s.conditioning.sumOf { Ssu.conditioning(it.modality, it.zone, it.workMinutes, it.restMinutes) }

    /** VOL-007's weekly limit for this week: the limit given (else the level ceiling), never below the floor (D-110) nor above the ceiling. */
    fun weeklySsuLimit(c: ValidationContext): Double {
        val ceiling = Caps.weeklySsu(c.level).toDouble()
        return maxOf(c.weeklySsuLimit ?: ceiling, minOf(c.weekFloorSsu ?: 0.0, ceiling))
    }

    private fun overLoadCap(c: ValidationContext, ssu: Double): Boolean {
        if (c.loadCapExempt) return false
        // The floor (D-110): LOAD-005 never cuts the week below it, whatever the measured loads so far.
        c.weekFloorSsu?.let { f -> if (c.weekSsuSoFar + ssu <= paced(f, c.weekSsuSoFar, c) + 1e-9) return false }
        val k = c.k
        val cap = c.weeklyLoadCap
        if (k != null && cap != null) return c.weekLoadSoFar + k * ssu > paced(cap, c.weekLoadSoFar, c) + 1e-9
        val last = c.lastWeekSsu
        if (k == null && last != null && last > 0) {
            return c.weekSsuSoFar + ssu > paced(last * (1.0 + P.LOAD_005.early_week_over_week_pct / 100.0), c.weekSsuSoFar, c) + 1e-9
        }
        return false
    }

    /**
     * Today's part of a weekly limit (week pacing, see [ValidationContext.weekShare]): what is already done plus this session's share
     * of what is left. A limit already reached stays as it is, so any further work is over it.
     */
    fun paced(limit: Double, soFar: Double, c: ValidationContext): Double =
        if (limit > soFar) soFar + (limit - soFar) * c.weekShare.coerceIn(0.0, 1.0) else limit

    /** The SSU this session must lose to meet its paced weekly limits (VOL-007, LOAD-005); 0 when it is within them. */
    fun weeklyExcessSsu(s: Session, c: ValidationContext): Double {
        val ssu = sessionSsu(s)
        var over = c.weekSsuSoFar + ssu - paced(weeklySsuLimit(c), c.weekSsuSoFar, c)
        if (!c.loadCapExempt) {
            val k = c.k
            val cap = c.weeklyLoadCap
            val last = c.lastWeekSsu
            var load = 0.0
            if (k != null && cap != null) {
                if (k > 0) load = (c.weekLoadSoFar + k * ssu - paced(cap, c.weekLoadSoFar, c)) / k
            } else if (k == null && last != null && last > 0) {
                load = c.weekSsuSoFar + ssu - paced(last * (1.0 + P.LOAD_005.early_week_over_week_pct / 100.0), c.weekSsuSoFar, c)
            }
            // Never more than it takes to come down to the floor (D-110).
            c.weekFloorSsu?.let { f -> load = minOf(load, c.weekSsuSoFar + ssu - paced(f, c.weekSsuSoFar, c)) }
            over = maxOf(over, load)
        }
        return maxOf(0.0, over)
    }

    /**
     * The last conditioning block, shortened just enough for the weekly limits (half-minute steps, rounded down), or null when that
     * would take more than TIME-002's 40% off key conditioning — then the block is dropped instead.
     */
    private fun shortenedForWeek(s: Session, c: ValidationContext): ConditioningBlock? {
        val b = s.conditioning.last()
        val perMinute = Ssu.conditioning(b.modality, b.zone, b.workMinutes, b.restMinutes) / b.workMinutes
        if (b.workMinutes <= 0.0 || perMinute <= 0.0) return null
        val room = b.workMinutes - weeklyExcessSsu(s, c) / perMinute
        // Steady work in half-minute steps; intervals by whole repeats (R8-01). Either way rounded down, so it fits.
        val rep = b.repSec?.takeIf { it > 0 }
        val left = if (rep != null) Math.floor(room * 60.0 / rep + 1e-9) * rep / 60.0 else Math.floor(room * 2.0) / 2.0
        if (left <= 0.0) return null
        if (left < b.workMinutes * (1.0 - P.TIME_002.p2_max_cut_pct / 100.0) - 1e-9) return null
        return b.copy(workMinutes = left, restMinutes = b.restMinutes * left / b.workMinutes)
    }

    /**
     * When the last conditioning block would lose more than 40%: the same block made easy instead (steady Z1, as the validator makes
     * any disallowed hard work easy, D-039), shortened to fit if need be but never below FREQ-005's 10-minute block. The minutes of
     * activity stay where the week's limit allows (FL-002, PH-001); dropping a whole block left weeks far below their limit, and the
     * next limits followed them down (Phase 4 simulations, D-110). Easy work is done on a low-impact machine: jumping or other impact
     * work (jump rope, jumping circuits) moves to the first allowed modality of the usual swap order (a bodyweight circuit without jumps
     * last), as for a disallowed modality, and is dropped when there is none (independent review: "easy" jump rope for the whole block, rest included, doubled the impact time).
     * Null when the block is already easy or even 10 easy minutes are over.
     */
    private fun easierForWeek(s: Session, c: ValidationContext): ConditioningBlock? {
        val b = s.conditioning.last()
        if (b.zone == Zone.Z1 && !b.hiit) return null
        val lowImpact = b.impact == 0 && b.modality in SWAP_ORDER
        val modality = if (lowImpact) b.modality else SWAP_ORDER.firstOrNull { modalityAllowed(it, c) } ?: return null
        val easy = b.copy(modality = modality, impact = 0, zone = Zone.Z1, hiit = false, protocol = null, restMinutes = 0.0, repSec = null, workMinutes = b.totalMinutes)
        val perMinute = Ssu.conditioning(easy.modality, Zone.Z1, 1.0)
        if (easy.workMinutes <= 0.0 || perMinute <= 0.0) return null
        val room = easy.workMinutes - weeklyExcessSsu(s.copy(conditioning = s.conditioning.dropLast(1) + easy), c) / perMinute
        val left = minOf(easy.workMinutes, Math.floor(room * 2.0) / 2.0)
        return if (left >= P.FREQ_005.min_block_minutes - 1e-9) easy.copy(workMinutes = left) else null
    }

    // ---------------------------------------------------------------- correction

    fun validate(input: Session, c: ValidationContext): EngineResult<ValidatedSession> {
        val log = ArrayList<Decision>()
        try {
            var s = input
            var steps = 0
            var weeklyCut = false
            while (steps < MAX_STEPS) {
                val v = violations(s, c)
                if (v.isEmpty()) {
                    val back = if (weeklyCut) giveBackForWeek(input, s, c) else s
                    val given = back.exercises.sumOf { it.sets } - s.exercises.sumOf { it.sets }
                    log += Decision(DecisionKind.SAFETY, listOf(RuleIds.SAF_008), ReasonKey.VALIDATOR_PASSED,
                        outputs = if (given > 0) mapOf("corrections" to steps, "setsGivenBack" to given) else mapOf("corrections" to steps))
                    return EngineResult(ValidatedSession(back, false, steps), log)
                }
                if (v.first().code == "WEEKLY_SSU" || v.first().code == "WEEKLY_LOAD") weeklyCut = true
                val (next, d) = correct(s, v.first(), c) ?: break
                s = next
                log += d
                steps++
            }
        } catch (e: Exception) {
            log += Decision(DecisionKind.SAFETY, listOf(RuleIds.SAF_008), ReasonKey.VALIDATOR_FALLBACK_LIGHT, outputs = mapOf("error" to (e::class.simpleName ?: "error")))
        }
        return fallback(input, c, log)
    }

    private fun fallback(input: Session, c: ValidationContext, log: MutableList<Decision>): EngineResult<ValidatedSession> {
        val tier = Tier.min(input.tier, Tier.LIGHT)
        val z1 = easyZ1(input, c, P.RDY_004.LIGHT.z1_minutes[0].toDouble())
        val candidates = ArrayList<Session>()
        if (tier == Tier.LIGHT) {
            val kept = input.exercises.filter { exerciseAllowed(it.exercise, c) }.take(4).map {
                it.copy(sets = minOf(2, maxOf(1, it.sets)), targetRir = maxOf(it.targetRir, minRirFor(Tier.LIGHT, c),
                    regionsFor(it.exercise, c).mapNotNull { r -> r.minRir }.maxOrNull()?.toDouble() ?: 0.0),
                    loadFactor = minOf(it.loadFactor, P.RDY_004.LIGHT.max_main_load_pct / 100.0, P.DEL_003.load_pct[1] / 100.0),
                    lastSetToFailure = false)
            }
            candidates += input.copy(tier = tier, exercises = kept, conditioning = listOfNotNull(z1), boneLoading = null, warmupMinutes = floorWarmup(input, c),
                cooldownMinutes = floorCooldown(input, c))
        }
        if (z1 != null) candidates += input.copy(tier = tier, exercises = emptyList(), conditioning = listOf(z1), boneLoading = null,
            warmupMinutes = floorWarmup(input, c), cooldownMinutes = floorCooldown(input, c))
        candidates += Session(Tier.RECOVERY, emptyList(), emptyList())
        for (s in candidates) {
            val ok = try { violations(s, c).isEmpty() } catch (e: Exception) { false }
            if (ok) {
                log += Decision(DecisionKind.SAFETY, listOf(RuleIds.SAF_008), ReasonKey.VALIDATOR_FALLBACK_LIGHT,
                    outputs = mapOf("tier" to s.tier, "exercises" to s.exercises.size, "conditioning" to s.conditioning.size))
                return EngineResult(ValidatedSession(s, true, log.size), log)
            }
        }
        // Rest is always valid; this line exists so the function cannot return an unvalidated session.
        return EngineResult(ValidatedSession(Session(Tier.RECOVERY, emptyList()), true, log.size), log)
    }

    private fun floorWarmup(s: Session, c: ValidationContext): Double? =
        s.warmupMinutes?.let { maxOf(it, com.personalfitnesscoach.engine.planning.TimeBudget.warmupFloor(c.age, c.conditions.extraWarmupMin.toDouble())) }

    private fun floorCooldown(s: Session, c: ValidationContext): Double? =
        s.cooldownMinutes?.let { maxOf(it, P.TIME_001.cooldown_min_minutes + c.conditions.extraCooldownMin.toDouble()) }

    private fun easyZ1(input: Session, c: ValidationContext, minutes: Double): ConditioningBlock? {
        val preferred = input.conditioning.map { it.modality }.firstOrNull { it in SWAP_ORDER && modalityAllowed(it, c) }
        val m = preferred ?: SWAP_ORDER.firstOrNull { modalityAllowed(it, c) } ?: return null
        return ConditioningBlock(m, Zone.Z1, minutes)
    }

    private fun correct(s: Session, v: Violation, c: ValidationContext): Pair<Session, Decision>? {
        fun d(reason: ReasonKey, out: Map<String, Any?> = emptyMap()) =
            Decision(DecisionKind.SAFETY, listOf(RuleIds.SAF_008, v.ruleId), reason, inputs = mapOf("violation" to v.code, "detail" to v.detail), outputs = out)
        fun setEx(i: Int, f: (SessionExercise) -> SessionExercise) = s.copy(exercises = s.exercises.mapIndexed { j, e -> if (j == i) f(e) else e })
        fun setBlock(i: Int, b: ConditioningBlock?) = s.copy(conditioning = s.conditioning.mapIndexedNotNull { j, x -> if (j == i) b else x })
        fun steady(b: ConditioningBlock): ConditioningBlock {
            val r = P.RDY_004.MODIFIED.hiit_replacement_minutes
            val easy = s.tier == Tier.LIGHT || s.tier == Tier.RECOVERY || c.inDeload || c.conditions.maxZone < Zone.Z2 ||
                c.effectiveScreening != ScreeningMode.STANDARD
            return b.copy(zone = if (easy) Zone.Z1 else Zone.Z2, hiit = false, protocol = null, restMinutes = 0.0, repSec = null,
                workMinutes = b.totalMinutes.coerceIn(r[0].toDouble(), r[1].toDouble()))
        }
        return when (v.code) {
            "WARMUP_SHORT" -> s.copy(warmupMinutes = com.personalfitnesscoach.engine.planning.TimeBudget.warmupFloor(c.age, c.conditions.extraWarmupMin.toDouble())) to
                d(ReasonKey.VALIDATOR_WARMUP_RESTORED)
            "COOLDOWN_SHORT" -> s.copy(cooldownMinutes = P.TIME_001.cooldown_min_minutes + c.conditions.extraCooldownMin.toDouble()) to d(ReasonKey.VALIDATOR_WARMUP_RESTORED)
            "BONE_LOADING_NOT_ALLOWED" -> {
                val alt = if (c.conditions.boneLoading) BoneLoading.choose(c.allJointLimits, c.allBlockedTags, c.regions, hopsAllowed = c.boneHopsAllowed) else null
                s.copy(boneLoading = alt) to d(ReasonKey.VALIDATOR_CONDITIONING_CHANGED, mapOf("boneLoading" to alt?.name))
            }
            "MODALITY", "CONDITIONING_JOINT" -> {
                val b = s.conditioning[v.index]
                val alt = SWAP_ORDER.firstOrNull { it != b.modality && modalityAllowed(it, c) && (b.impact == 0 || impactAllowed(c)) }
                if (alt != null) setBlock(v.index, b.copy(modality = alt, impact = 0)) to d(ReasonKey.VALIDATOR_CONDITIONING_CHANGED, mapOf("from" to b.modality, "to" to alt))
                else setBlock(v.index, null) to d(ReasonKey.VALIDATOR_CONDITIONING_CHANGED, mapOf("removed" to b.modality))
            }
            "IMPACT_NOT_ALLOWED" -> {
                val b = s.conditioning[v.index]
                val alt = SWAP_ORDER.firstOrNull { modalityAllowed(it, c) }
                if (alt != null) setBlock(v.index, b.copy(modality = alt, impact = 0)) to d(ReasonKey.VALIDATOR_CONDITIONING_CHANGED, mapOf("from" to b.modality, "to" to alt))
                else setBlock(v.index, null) to d(ReasonKey.VALIDATOR_CONDITIONING_CHANGED, mapOf("removed" to b.modality))
            }
            "EXERCISE_NOT_ALLOWED", "EMPTY_EXERCISE" -> {
                val e = s.exercises[v.index]
                val swap = if (v.code == "EMPTY_EXERCISE") null else swapFor(e.exercise, s, c)
                if (swap != null) setEx(v.index) { it.copy(exercise = swap) } to d(ReasonKey.VALIDATOR_SWAPPED, mapOf("from" to e.exercise.id, "to" to swap.id))
                else s.copy(exercises = s.exercises.filterIndexed { j, _ -> j != v.index }) to d(ReasonKey.VALIDATOR_REMOVED_EXERCISE, mapOf("removed" to e.exercise.id))
            }
            "RECOVERY_NO_RESISTANCE", "STRENGTH_CONSECUTIVE" ->
                s.copy(exercises = emptyList()) to d(ReasonKey.VALIDATOR_REMOVED_EXERCISE, mapOf("removed" to s.exercises.map { it.exercise.id }))
            "Z1_ONLY" -> {
                val b = s.conditioning[v.index]
                val max = if (s.tier == Tier.RECOVERY) P.RDY_004.RECOVERY.optional_z1_minutes[1].toDouble() else b.totalMinutes
                setBlock(v.index, b.copy(zone = Zone.Z1, hiit = false, protocol = null, restMinutes = 0.0, workMinutes = minOf(b.totalMinutes, max))) to
                    d(ReasonKey.VALIDATOR_CONDITIONING_CHANGED, mapOf("zone" to Zone.Z1))
            }
            "LIGHT_CONDITIONING_MINUTES", "POST_STRENGTH_CONDITIONING" -> {
                val limit = if (v.code == "LIGHT_CONDITIONING_MINUTES") P.RDY_004.LIGHT.z1_minutes[1].toDouble() else P.HIIT_005.post_strength_conditioning_min_max.toDouble()
                val excess = s.conditioning.sumOf { it.totalMinutes } - limit
                val i = s.conditioning.lastIndex
                val b = s.conditioning[i]
                val shorter = b.totalMinutes - excess
                val next = if (shorter <= 0.0) null else {
                    val f = shorter / b.totalMinutes
                    b.copy(workMinutes = b.workMinutes * f, restMinutes = b.restMinutes * f)
                }
                setBlock(i, next) to d(ReasonKey.VALIDATOR_CONDITIONING_CHANGED, mapOf("minutes" to limit))
            }
            "TIER_SETS" -> trim(s) { true }?.let { it to d(ReasonKey.VALIDATOR_TRIMMED, mapOf("cap" to v.detail)) }
            "EFFORT_TOO_HIGH" -> {
                val e = s.exercises[v.index]
                val target = requiredRir(e, s.tier, c, minRirFor(s.tier, c))
                setEx(v.index) { it.copy(targetRir = target, lastSetToFailure = false) } to d(ReasonKey.VALIDATOR_EFFORT_RAISED, mapOf("rir" to target))
            }
            "LOAD_TOO_HIGH" -> {
                val max = maxLoadFor(s.exercises[v.index], s.tier, c)
                setEx(v.index) { it.copy(loadFactor = max) } to d(ReasonKey.VALIDATOR_LOAD_LOWERED, mapOf("loadFactor" to max))
            }
            "FAILURE_NOT_ALLOWED" -> setEx(v.index) { it.copy(lastSetToFailure = false, targetRir = maxOf(it.targetRir, 1.0)) } to d(ReasonKey.VALIDATOR_EFFORT_RAISED)
            "DIRECT_SETS" -> {
                val m = Muscle.valueOf(v.detail)
                trim(s) { m in it.exercise.primary }?.let { it to d(ReasonKey.VALIDATOR_TRIMMED, mapOf("muscle" to m)) }
            }
            "WEEKLY_SETS" -> {
                val m = Muscle.valueOf(v.detail)
                trim(s) { m in it.exercise.primary || m in it.exercise.secondary }?.let { it to d(ReasonKey.VALIDATOR_TRIMMED, mapOf("muscle" to m)) }
            }
            "SESSION_SETS" -> trim(s) { true }?.let { it to d(ReasonKey.VALIDATOR_TRIMMED) }
            "HIIT_NOT_ALLOWED", "Z3_NOT_ALLOWED", "SPRINT_NOT_ALLOWED", "ZONE_ABOVE_LIMIT" -> setBlock(v.index, steady(s.conditioning[v.index])) to d(ReasonKey.VALIDATOR_REMOVED_HIIT)
            "INTERVAL_MODALITY" -> {
                val b = s.conditioning[v.index]
                val alt = (c.conditions.hiitModalities?.sortedBy { it.name } ?: SWAP_ORDER).firstOrNull { modalityAllowed(it, c) && intervalModalityAllowed(it, c) &&
                    (c.equipmentToday == null || ModalitySelection.available(it, c.equipmentToday)) }
                if (alt != null) setBlock(v.index, b.copy(modality = alt, impact = 0)) to d(ReasonKey.VALIDATOR_CONDITIONING_CHANGED, mapOf("from" to b.modality, "to" to alt))
                else setBlock(v.index, steady(b)) to d(ReasonKey.VALIDATOR_REMOVED_HIIT)
            }
            "HIIT_COUNT" -> {
                val i = s.conditioning.indexOfLast { it.countsAsHiit }.let { if (it >= 0) it else s.conditioning.indexOfLast { b -> b.zone == Zone.Z3 } }
                setBlock(i, steady(s.conditioning[i])) to d(ReasonKey.VALIDATOR_REMOVED_HIIT)
            }
            "HIIT_WORK_CAP" -> {
                val b = s.conditioning[v.index]
                val cap = hiitWorkCap(b)!!
                // Whole repeats only (R8-01): the cap rounded down to a number of repeats; under one repeat the block becomes steady work.
                val work = b.repSec?.takeIf { it > 0 }?.let { rep -> Math.floor(cap * 60.0 / rep + 1e-9) * rep / 60.0 } ?: cap
                if (work <= 0.0) setBlock(v.index, steady(b)) to d(ReasonKey.VALIDATOR_REMOVED_HIIT)
                else setBlock(v.index, b.copy(workMinutes = work, restMinutes = b.restMinutes * work / b.workMinutes)) to
                    d(ReasonKey.VALIDATOR_CONDITIONING_CHANGED, mapOf("workMinutes" to work))
            }
            "WEEKLY_SSU", "WEEKLY_LOAD" -> {
                // TIME-001 priorities: accessory sets first, then the key conditioning (shortened by up to 40%, else dropped), then main lifts.
                val t = trim(s, mainsAllowed = false, ssu = true) { true }
                when {
                    t != null -> t to d(ReasonKey.VALIDATOR_TRIMMED)
                    s.conditioning.isNotEmpty() -> {
                        val shorter = shortenedForWeek(s, c) ?: easierForWeek(s, c)
                        setBlock(s.conditioning.lastIndex, shorter) to
                            d(ReasonKey.VALIDATOR_CONDITIONING_CHANGED, shorter?.let { mapOf("workMinutes" to it.workMinutes, "zone" to it.zone.name) } ?: emptyMap())
                    }
                    else -> trim(s, ssu = true) { true }?.let { it to d(ReasonKey.VALIDATOR_TRIMMED) }
                }
            }
            else -> null
        }
    }

    /**
     * Remove one set, choosing (from the end of the session) a non-main exercise with >1 set,
     * else remove a 1-set non-main exercise, else (if allowed) take a set from a main lift or remove it. For SSU (`ssu`), an exercise
     * still being calibrated counts as one set whatever its set count, so it is removed whole rather than a set at a time.
     */
    private fun trim(s: Session, mainsAllowed: Boolean = true, ssu: Boolean = false, match: (SessionExercise) -> Boolean): Session? {
        val ex = s.exercises
        fun dec(i: Int) = s.copy(exercises = ex.mapIndexed { j, e -> if (j == i) e.copy(sets = e.sets - 1) else e })
        fun drop(i: Int) = s.copy(exercises = ex.filterIndexed { j, _ -> j != i })
        fun splittable(e: SessionExercise) = e.sets > 1 && !(ssu && e.calibrating)
        ex.indices.reversed().firstOrNull { !ex[it].main && match(ex[it]) && splittable(ex[it]) }?.let { return dec(it) }
        ex.indices.reversed().firstOrNull { !ex[it].main && match(ex[it]) }?.let { return drop(it) }
        if (!mainsAllowed) return null
        ex.indices.reversed().firstOrNull { ex[it].main && match(ex[it]) && splittable(ex[it]) }?.let { return dec(it) }
        ex.indices.reversed().firstOrNull { ex[it].main && match(ex[it]) }?.let { return drop(it) }
        return null
    }

    /**
     * After the weekly limits (VOL-007, LOAD-005) cut a session, gives back the sets that still fit, most important first: main lifts,
     * then the others in session order, each filled back as far as it fits before the next (a weight still being found whole), every
     * set kept only if the whole session still passes every check. The cuts go in TIME-001 order (accessory sets from the end, then the
     * key conditioning, then main lifts), so when the conditioning had to be shortened or dropped as well, accessory sets cut before it
     * may fit again: without this, a day whose conditioning alone was over its share lost every set too and became a recovery day
     * (Part 8 journey test). Never more than planned.
     */
    private fun giveBackForWeek(input: Session, cut: Session, c: ValidationContext): Session {
        // Exercises are matched by ID; a session listing one twice keeps its cuts.
        if (input.exercises.distinctBy { it.exercise.id }.size != input.exercises.size) return cut
        var cur = cut
        val order = input.exercises.indices.sortedWith(compareBy({ !input.exercises[it].main }, { it }))
        fun inputIndex(e: SessionExercise) = input.exercises.indexOfFirst { it.exercise.id == e.exercise.id }
        for (i in order) {
            val want = input.exercises[i]
            while (true) {
                val at = cur.exercises.indexOfFirst { it.exercise.id == want.exercise.id }
                val candidate = if (at >= 0) {
                    val have = cur.exercises[at]
                    if (have.sets >= want.sets) break
                    val more = if (have.calibrating) want.sets - have.sets else 1
                    cur.copy(exercises = cur.exercises.mapIndexed { j, e -> if (j == at) e.copy(sets = e.sets + more) else e })
                } else {
                    // Back in its place among the exercises still there.
                    val pos = cur.exercises.count { inputIndex(it) in 0 until i }
                    cur.copy(exercises = cur.exercises.toMutableList().apply { add(pos, if (want.calibrating) want else want.copy(sets = 1)) })
                }
                if (violations(candidate, c).isEmpty()) cur = candidate else break
            }
        }
        return cur
    }

    // ---------------------------------------------------------------- helpers

    private fun regionsFor(e: Exercise, c: ValidationContext) = c.regions.filter { e.stress(it.region) >= 1 }

    /** CON-004: impact work at most once a week and never the day before heavy legs; SAF-010 may rule impact out entirely. */
    fun impactAllowed(c: ValidationContext): Boolean =
        c.impactSessionsThisWeekSoFar < P.CON_004.impact_sessions_per_week_max && !c.heavyLegsNextDay && c.conditions.impact.allowsImpact && c.planImpactAllowed

    /** SAF-010 / FL-003: intervals only on the machines a condition allows (low-impact list, or the entry's own list). */
    fun intervalModalityAllowed(m: Modality, c: ValidationContext): Boolean {
        val list = c.conditions.hiitModalities
        if (list != null && m !in list) return false
        c.planIntervalModalities?.let { if (m !in it) return false }
        if (c.conditions.hiitLowImpactOnly && m !in LOW_IMPACT_INTERVALS) return false
        return true
    }

    private val LOW_IMPACT_INTERVALS: Set<Modality> by lazy { P.FL_003.low_impact_modalities.mapNotNull { Modality.byKey(it) }.toSet() }

    /** Hard filters shared by validation and swaps: MOD-001, exclusions, pain limits, tags, impact. */
    fun exerciseAllowed(e: Exercise, c: ValidationContext): Boolean {
        if (e.equipment.any { it in Substitution.CARDIO_MACHINE_EQUIPMENT } || e.id in c.excludedIds) return false
        if (e.limitationTags.any { it in c.allBlockedTags }) return false
        if (e.impact > 0 && !impactAllowed(c)) return false
        for ((j, limit) in c.allJointLimits) if (e.stress(j) > limit) return false
        for (r in c.regions) {
            val max = r.maxStress
            if (max != null && e.stress(r.region) > max) return false
            if (r.noJumping && e.impact > 0 && e.stress(r.region) >= 1) return false
        }
        return true
    }

    /** A conditioning modality may be used only if the user hasn't excluded it (MOD-001) and it keeps every painful joint within its limit. */
    fun modalityAllowed(m: Modality, c: ValidationContext): Boolean {
        if (m in c.excludedModalities || m in c.conditions.avoidModalities) return false
        // Equipment here today (review R3-10): every conditioning correction only picks a machine that is actually here.
        if (c.equipmentToday != null && !ModalitySelection.available(m, c.equipmentToday)) return false
        for ((j, limit) in c.allJointLimits) if (ModalityJoints.stress(m, j) > limit) return false
        for (r in c.regions) { val max = r.maxStress; if (max != null && ModalityJoints.stress(m, r.region) > max) return false }
        return true
    }

    private fun swapFor(original: Exercise, s: Session, c: ValidationContext): Exercise? {
        val inSession = s.exercises.map { it.exercise.id }.toSet()
        val pool = c.library.filter { it.id !in inSession && exerciseAllowed(it, c) }
        if (pool.isEmpty()) return null
        val ctx = SubContext(equipmentToday = c.equipmentToday ?: emptySet(), level = c.level, jointLimits = c.allJointLimits, blockedTags = c.allBlockedTags, excludedIds = c.excludedIds)
        return Substitution.options(original, pool, ctx).value.autoPick?.exercise
    }

    private fun hiitWorkCap(b: ConditioningBlock): Double? = when (b.protocol) {
        HiitProtocol.LONG, HiitProtocol.MEDIUM -> P.HIIT_005.long_work_min_max.toDouble()
        HiitProtocol.SHORT -> P.HIIT_005.short_work_min_max.toDouble()
        HiitProtocol.SPRINT -> P.HIIT_005.sprint_work_min_max
        null -> null
    }

    /** RDY-004 / DEL-003: working sets allowed after the tier or deload cut, or null when unknown. */
    private fun tierSetCap(tier: Tier, c: ValidationContext): Int? {
        val base = c.fullTierWorkingSets ?: Caps.workingSetsPerSession(c.level)
        var cut = when (tier) {
            Tier.MODIFIED -> P.RDY_004.MODIFIED.sets_reduction_pct
            Tier.LIGHT -> P.RDY_004.LIGHT.sets_reduction_pct
            else -> 0
        }
        if (c.inDeload) cut = maxOf(cut, P.DEL_003.sets_reduction_pct)
        if (cut == 0) return null
        return maxOf(1, Math.floor(base * (1.0 - cut / 100.0) + 1e-9).toInt())
    }

    private fun minRirFor(tier: Tier, c: ValidationContext): Double {
        var m = 1.0 // INT-003: only the last set of a FailureSafe exercise may be RIR 0, via lastSetToFailure
        if (tier == Tier.LIGHT) m = maxOf(m, P.RDY_004.LIGHT.min_rir.toDouble())
        if (c.inDeload) m = maxOf(m, P.DEL_003.min_rir.toDouble())
        if (c.effectiveScreening == ScreeningMode.CONSERVATIVE) m = maxOf(m, P.SAF_001.conservative_mode.min_rir.toDouble())
        return m
    }

    private fun requiredRir(e: SessionExercise, tier: Tier, c: ValidationContext, minRir: Double): Double {
        val region = regionsFor(e.exercise, c).mapNotNull { it.minRir }.maxOrNull()?.toDouble() ?: 0.0
        // MODIFIED: FULL-plan RIR + 1; when the FULL plan is unknown, assume the lowest planned RIR (1) + 1 (fail-closed).
        val shift = if (tier == Tier.MODIFIED) (e.fullTierRir ?: 1.0) + P.RDY_004.MODIFIED.rir_offset else 0.0
        // SAF-010: the condition entries' RIR floor (all exercises, or only tagged ones such as spinal loading for low back pain).
        val condition = c.conditions.minRirFor(e.exercise.limitationTags) ?: 0.0
        return maxOf(minRir, region, shift, condition)
    }

    /** RDY-004 caps main lifts on MODIFIED (95%) and LIGHT (85%); DEL-003 caps every exercise at 90% in a deload. */
    private fun maxLoadFor(e: SessionExercise, tier: Tier, c: ValidationContext): Double {
        var m = 1.0
        if (e.main && tier == Tier.MODIFIED) m = P.RDY_004.MODIFIED.max_main_load_pct / 100.0
        if (e.main && tier == Tier.LIGHT) m = P.RDY_004.LIGHT.max_main_load_pct / 100.0
        if (c.inDeload) m = minOf(m, P.DEL_003.load_pct[1] / 100.0)
        return m
    }

    /** Highest in-session load for a tier (used by INT-007 autoregulation). */
    fun maxLoadFactor(main: Boolean, tier: Tier, inDeload: Boolean): Double =
        maxLoadFor(SessionExercise(PLACEHOLDER, 1, 1, 3.0, main = main), tier, ValidationContext(Level.BEGINNER, inDeload = inDeload))

    private val PLACEHOLDER = Exercise("_", "_", com.personalfitnesscoach.engine.model.Pattern.ISOLATION, emptySet(),
        loadType = com.personalfitnesscoach.engine.model.LoadType.BODYWEIGHT, costClass = com.personalfitnesscoach.engine.model.CostClass.ISOLATION_OR_CORE)
}
