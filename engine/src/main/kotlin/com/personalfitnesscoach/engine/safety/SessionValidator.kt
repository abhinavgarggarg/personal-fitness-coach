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
) {
    /** HIIT-001: flagged HIIT, an interval protocol, any Z4 work, or ≥ 6 min of Z3 work. */
    val countsAsHiit: Boolean
        get() = hiit || protocol != null || zone == Zone.Z4 ||
            (zone == Zone.Z3 && workMinutes >= P.HIIT_001.count_if_z3_work_minutes_gte)
    val totalMinutes get() = workMinutes + restMinutes
}

data class Session(val tier: Tier, val exercises: List<SessionExercise>, val conditioning: List<ConditioningBlock> = emptyList()) {
    val workingSets get() = exercises.sumOf { it.sets }
    /** Z3 work in blocks that do not count as HIIT on their own; ≥ 6 min in total counts as one HIIT (HIIT-001). */
    val shortZ3Minutes get() = conditioning.filter { !it.countsAsHiit && it.zone == Zone.Z3 }.sumOf { it.workMinutes }
    val hiitBlocks get() = conditioning.count { it.countsAsHiit } + if (shortZ3Minutes >= P.HIIT_001.count_if_z3_work_minutes_gte) 1 else 0
}

/** Everything the validator checks a session against. */
data class ValidationContext(
    val level: Level,
    val weeksTraining: Int = 52,
    val screening: ScreeningMode = ScreeningMode.STANDARD,
    /** SAF-003 joint limits for today (0 = no loading of that joint). */
    val jointLimits: Map<Joint, Int> = emptyMap(),
    /** SAF-004 persistent-pain regions. */
    val regions: List<RegionConstraint> = emptyList(),
    val blockedTags: Set<String> = emptySet(),
    val excludedIds: Set<String> = emptySet(),
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
    val inDeload: Boolean = false,
    /**
     * Working sets of the FULL-day plan, so tier and deload set cuts (RDY-004, DEL-003) can be checked.
     * When unknown, the cut is applied to the level's session cap instead (fail-closed).
     */
    val fullTierWorkingSets: Int? = null,
    val equipmentToday: Set<String> = emptySet(),
    val library: List<Exercise> = emptyList(),
)

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
    private const val MAX_STEPS = 500
    private val IMPACT_JOINTS = setOf(Joint.KNEE, Joint.ANKLE, Joint.HIP, Joint.SPINE)
    private val SWAP_ORDER = listOf(Modality.ELLIPTICAL, Modality.ROWER, Modality.SKIERG)

    // ---------------------------------------------------------------- checks

    fun violations(s: Session, c: ValidationContext): List<Violation> {
        val v = ArrayList<Violation>()
        val strength = s.exercises
        // MOD-001, pain limits, CON-004 impact for conditioning.
        s.conditioning.forEachIndexed { i, b ->
            if (b.modality.excluded) v += Violation(RuleIds.MOD_001, "MODALITY", i)
            else if (!modalityAllowed(b.modality, c)) v += Violation(RuleIds.SAF_003, "CONDITIONING_JOINT", i)
            if (b.impact > 0 && !impactAllowed(c)) v += Violation(RuleIds.CON_004, "IMPACT_NOT_ALLOWED", i)
            if (b.impact > 0 && c.regions.any { it.noJumping && it.region in IMPACT_JOINTS }) v += Violation(RuleIds.SAF_004, "IMPACT_NOT_ALLOWED", i)
        }
        // Exclusions, pain limits, tags, impact for exercises.
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
        val rir0Allowed = Caps.rir0ExercisesPerSession(c.level, c.weeksTraining, s.tier, c.inDeload, c.screening)
        var failures = 0
        strength.forEachIndexed { i, e ->
            if (e.lastSetToFailure) {
                failures++
                val regionNoFailure = regionsFor(e.exercise, c).any { it.noFailure }
                if (!e.exercise.failureSafe || failures > rir0Allowed || regionNoFailure) v += Violation(RuleIds.INT_003, "FAILURE_NOT_ALLOWED", i)
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
        val z3Allowed = c.screening == ScreeningMode.STANDARD && !c.inDeload && (s.tier == Tier.FULL || s.tier == Tier.MODIFIED)
        val hiitAllowed = c.screening == ScreeningMode.STANDARD && !c.inDeload && s.tier == Tier.FULL && c.hiitBaseReady &&
            (c.hoursSinceLastHiit ?: Double.MAX_VALUE) >= P.HIIT_004.min_hours &&
            (c.hoursToNextHeavyLower ?: Double.MAX_VALUE) >= P.CON_003.hours_before_heavy_lower
        s.conditioning.forEachIndexed { i, b ->
            if (b.countsAsHiit && !hiitAllowed) v += Violation(RuleIds.HIIT_004, "HIIT_NOT_ALLOWED", i)
            else if (!b.countsAsHiit && b.zone >= Zone.Z3 && !z3Allowed) v += Violation(RuleIds.SAF_001, "Z3_NOT_ALLOWED", i)
            if (b.protocol == HiitProtocol.SPRINT && (c.level != Level.ADVANCED || c.sprintsThisWeekSoFar >= P.HIIT_002.sprint.per_week_max)) v += Violation(RuleIds.HIIT_002, "SPRINT_NOT_ALLOWED", i)
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
        val ssuLimit = c.weeklySsuLimit ?: Caps.weeklySsu(c.level).toDouble()
        if (ssu > 0 && c.weekSsuSoFar + ssu > ssuLimit + 1e-9) v += Violation(RuleIds.VOL_007, "WEEKLY_SSU")
        if (ssu > 0 && overLoadCap(c, ssu)) v += Violation(RuleIds.LOAD_005, "WEEKLY_LOAD")
        return v
    }

    fun sessionSsu(s: Session): Double =
        Ssu.sets(s.exercises.flatMap { e -> List(e.sets) { e.exercise.costClass to e.targetRir } }) +
            s.conditioning.sumOf { Ssu.conditioning(it.modality, it.zone, it.workMinutes, it.restMinutes) }

    private fun overLoadCap(c: ValidationContext, ssu: Double): Boolean {
        if (c.loadCapExempt) return false
        val k = c.k
        val cap = c.weeklyLoadCap
        if (k != null && cap != null) return c.weekLoadSoFar + k * ssu > cap + 1e-9
        val last = c.lastWeekSsu
        if (k == null && last != null && last > 0) {
            return c.weekSsuSoFar + ssu > last * (1.0 + P.LOAD_005.early_week_over_week_pct / 100.0) + 1e-9
        }
        return false
    }

    // ---------------------------------------------------------------- correction

    fun validate(input: Session, c: ValidationContext): EngineResult<ValidatedSession> {
        val log = ArrayList<Decision>()
        try {
            var s = input
            var steps = 0
            while (steps < MAX_STEPS) {
                val v = violations(s, c)
                if (v.isEmpty()) {
                    log += Decision(DecisionKind.SAFETY, listOf(RuleIds.SAF_008), ReasonKey.VALIDATOR_PASSED, outputs = mapOf("corrections" to steps))
                    return EngineResult(ValidatedSession(s, false, steps), log)
                }
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
            candidates += Session(tier, kept, listOfNotNull(z1))
        }
        if (z1 != null) candidates += Session(tier, emptyList(), listOf(z1))
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
            val easy = s.tier == Tier.LIGHT || s.tier == Tier.RECOVERY || c.inDeload
            return b.copy(zone = if (easy) Zone.Z1 else Zone.Z2, hiit = false, protocol = null, restMinutes = 0.0,
                workMinutes = b.totalMinutes.coerceIn(r[0].toDouble(), r[1].toDouble()))
        }
        return when (v.code) {
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
            "RECOVERY_NO_RESISTANCE" -> s.copy(exercises = emptyList()) to d(ReasonKey.VALIDATOR_REMOVED_EXERCISE, mapOf("removed" to s.exercises.map { it.exercise.id }))
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
            "HIIT_NOT_ALLOWED", "Z3_NOT_ALLOWED", "SPRINT_NOT_ALLOWED" -> setBlock(v.index, steady(s.conditioning[v.index])) to d(ReasonKey.VALIDATOR_REMOVED_HIIT)
            "HIIT_COUNT" -> {
                val i = s.conditioning.indexOfLast { it.countsAsHiit }.let { if (it >= 0) it else s.conditioning.indexOfLast { b -> b.zone == Zone.Z3 } }
                setBlock(i, steady(s.conditioning[i])) to d(ReasonKey.VALIDATOR_REMOVED_HIIT)
            }
            "HIIT_WORK_CAP" -> {
                val b = s.conditioning[v.index]
                val cap = hiitWorkCap(b)!!
                val f = cap / b.workMinutes
                setBlock(v.index, b.copy(workMinutes = cap, restMinutes = b.restMinutes * f)) to d(ReasonKey.VALIDATOR_CONDITIONING_CHANGED, mapOf("workMinutes" to cap))
            }
            "WEEKLY_SSU", "WEEKLY_LOAD" -> {
                val t = trim(s, mainsAllowed = false) { true }
                when {
                    t != null -> t to d(ReasonKey.VALIDATOR_TRIMMED)
                    s.conditioning.isNotEmpty() -> setBlock(s.conditioning.lastIndex, null) to d(ReasonKey.VALIDATOR_CONDITIONING_CHANGED)
                    else -> trim(s) { true }?.let { it to d(ReasonKey.VALIDATOR_TRIMMED) }
                }
            }
            else -> null
        }
    }

    /**
     * Remove one set, choosing (from the end of the session) a non-main exercise with >1 set,
     * else remove a 1-set non-main exercise, else (if allowed) take a set from a main lift or remove it.
     */
    private fun trim(s: Session, mainsAllowed: Boolean = true, match: (SessionExercise) -> Boolean): Session? {
        val ex = s.exercises
        fun dec(i: Int) = s.copy(exercises = ex.mapIndexed { j, e -> if (j == i) e.copy(sets = e.sets - 1) else e })
        fun drop(i: Int) = s.copy(exercises = ex.filterIndexed { j, _ -> j != i })
        ex.indices.reversed().firstOrNull { !ex[it].main && match(ex[it]) && ex[it].sets > 1 }?.let { return dec(it) }
        ex.indices.reversed().firstOrNull { !ex[it].main && match(ex[it]) }?.let { return drop(it) }
        if (!mainsAllowed) return null
        ex.indices.reversed().firstOrNull { ex[it].main && match(ex[it]) && ex[it].sets > 1 }?.let { return dec(it) }
        ex.indices.reversed().firstOrNull { ex[it].main && match(ex[it]) }?.let { return drop(it) }
        return null
    }

    // ---------------------------------------------------------------- helpers

    private fun regionsFor(e: Exercise, c: ValidationContext) = c.regions.filter { e.stress(it.region) >= 1 }

    /** CON-004: impact work at most once a week and never the day before heavy legs. */
    fun impactAllowed(c: ValidationContext): Boolean =
        c.impactSessionsThisWeekSoFar < P.CON_004.impact_sessions_per_week_max && !c.heavyLegsNextDay

    /** Hard filters shared by validation and swaps: MOD-001, exclusions, pain limits, tags, impact. */
    fun exerciseAllowed(e: Exercise, c: ValidationContext): Boolean {
        if (e.equipment.any { it in Substitution.MOD001_EQUIPMENT } || e.id in c.excludedIds) return false
        if (e.limitationTags.any { it in c.blockedTags }) return false
        if (e.impact > 0 && !impactAllowed(c)) return false
        for ((j, limit) in c.jointLimits) if (e.stress(j) > limit) return false
        for (r in c.regions) {
            val max = r.maxStress
            if (max != null && e.stress(r.region) > max) return false
            if (r.noJumping && e.impact > 0 && e.stress(r.region) >= 1) return false
        }
        return true
    }

    /** A conditioning modality may be used only if it is not excluded and keeps every painful joint within its limit. */
    fun modalityAllowed(m: Modality, c: ValidationContext): Boolean {
        if (m.excluded) return false
        for ((j, limit) in c.jointLimits) if (ModalityJoints.stress(m, j) > limit) return false
        for (r in c.regions) { val max = r.maxStress; if (max != null && ModalityJoints.stress(m, r.region) > max) return false }
        return true
    }

    private fun swapFor(original: Exercise, s: Session, c: ValidationContext): Exercise? {
        val inSession = s.exercises.map { it.exercise.id }.toSet()
        val pool = c.library.filter { it.id !in inSession && exerciseAllowed(it, c) }
        if (pool.isEmpty()) return null
        val ctx = SubContext(equipmentToday = c.equipmentToday, level = c.level, jointLimits = c.jointLimits, blockedTags = c.blockedTags, excludedIds = c.excludedIds)
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
        if (c.screening == ScreeningMode.CONSERVATIVE) m = maxOf(m, P.SAF_001.conservative_mode.min_rir.toDouble())
        return m
    }

    private fun requiredRir(e: SessionExercise, tier: Tier, c: ValidationContext, minRir: Double): Double {
        val region = regionsFor(e.exercise, c).mapNotNull { it.minRir }.maxOrNull()?.toDouble() ?: 0.0
        // MODIFIED: FULL-plan RIR + 1; when the FULL plan is unknown, assume the lowest planned RIR (1) + 1 (fail-closed).
        val shift = if (tier == Tier.MODIFIED) (e.fullTierRir ?: 1.0) + P.RDY_004.MODIFIED.rir_offset else 0.0
        return maxOf(minRir, region, shift)
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
