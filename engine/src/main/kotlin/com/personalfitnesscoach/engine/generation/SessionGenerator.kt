package com.personalfitnesscoach.engine.generation

import com.personalfitnesscoach.engine.calc.E1rm
import com.personalfitnesscoach.engine.calc.Inventory
import com.personalfitnesscoach.engine.calc.PlateMath
import com.personalfitnesscoach.engine.conditioning.Circuits
import com.personalfitnesscoach.engine.conditioning.Concurrent
import com.personalfitnesscoach.engine.conditioning.ModalitySelection
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.Num
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.dose.Order
import com.personalfitnesscoach.engine.dose.OrderSlot
import com.personalfitnesscoach.engine.dose.Rest
import com.personalfitnesscoach.engine.dose.RestWindow
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.Exercise
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.LoadType
import com.personalfitnesscoach.engine.model.Muscle
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.model.Zone
import com.personalfitnesscoach.engine.planning.DrillDose
import com.personalfitnesscoach.engine.planning.Mobility
import com.personalfitnesscoach.engine.planning.Priority
import com.personalfitnesscoach.engine.planning.RangeOfMotion
import com.personalfitnesscoach.engine.planning.SessionPlan
import com.personalfitnesscoach.engine.planning.SubContext
import com.personalfitnesscoach.engine.planning.Substitution
import com.personalfitnesscoach.engine.planning.TimeBudget
import com.personalfitnesscoach.engine.planning.TimeModel
import com.personalfitnesscoach.engine.program.Express
import com.personalfitnesscoach.engine.program.Individual
import com.personalfitnesscoach.engine.program.PlannedConditioning
import com.personalfitnesscoach.engine.program.PlannedDay
import com.personalfitnesscoach.engine.program.PlannedSlot
import com.personalfitnesscoach.engine.program.SlotRole
import com.personalfitnesscoach.engine.program.WeekPlanner
import com.personalfitnesscoach.engine.progression.Prescription
import com.personalfitnesscoach.engine.progression.ProgressionCaps
import com.personalfitnesscoach.engine.progression.Warmup
import com.personalfitnesscoach.engine.progression.WarmupSet
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds
import com.personalfitnesscoach.engine.safety.Caps
import com.personalfitnesscoach.engine.safety.ConditioningBlock
import com.personalfitnesscoach.engine.safety.RedFlags
import com.personalfitnesscoach.engine.safety.RegionConstraint
import com.personalfitnesscoach.engine.safety.SafetyStop
import com.personalfitnesscoach.engine.safety.ScreeningMode
import com.personalfitnesscoach.engine.safety.Session
import com.personalfitnesscoach.engine.safety.SessionExercise
import com.personalfitnesscoach.engine.safety.SessionValidator
import com.personalfitnesscoach.engine.safety.ValidationContext
import com.personalfitnesscoach.engine.safety.ConditionLimits

/** Everything GEN-001 needs for one session (step 1: load state). */
data class GenerationRequest(
    val day: PlannedDay,
    val level: Level,
    val weeksTraining: Int,
    val minutes: Int,
    val equipmentToday: Set<String>,
    /** Readiness tier after RDY-001..007 (the user's choice already applied). */
    val tier: Tier = Tier.FULL,
    val age: Int? = null,
    val screening: ScreeningMode = ScreeningMode.STANDARD,
    val redFlags: Set<String> = emptySet(),
    val illnessSymptoms: Set<String> = emptySet(),
    val inventory: Inventory = Inventory(),
    /** Current e1RM per exercise ID (INT-004). */
    val e1rm: Map<String, Double> = emptyMap(),
    /**
     * The progression engine's next-exposure prescription per exercise (FS-5, PROG-001..008): load and rep range. It sets
     * the load whenever it was made for today's rep range (same bottom of range), for e1RM lifts too; otherwise the e1RM
     * (INT-005) or calibration does (D-055).
     */
    val progression: Map<String, Prescription> = emptyMap(),
    /** CAL-001 in progress over sessions 1–4: where today's calibration ramp starts, per exercise. */
    val calibrationLoads: Map<String, Double> = emptyMap(),
    /** CAL-002: an old known number caps the calibration ramp for that exercise (KnownStart.Ceiling; review R3-14). */
    val calibrationCeilings: Map<String, Double> = emptyMap(),
    /** Last week's working load per slot and exercise ([SessionGenerator.loadKey]); caps the weekly rise (PROG-007, SAF-005). */
    val lastWeekLoads: Map<String, Double> = emptyMap(),
    val bodyweightKg: Double? = null,
    val personalFactor: Double = 1.0,
    val crowded: Boolean = false,
    /** CON-005: HIIT already done earlier today removes power work. */
    val hiitEarlierToday: Boolean = false,
    /** Joint/tendon pain today allows continuing with a shorter range (SAF-003 continue-with-caution). */
    val painCaution: Set<Joint> = emptySet(),
    val jointLimits: Map<Joint, Int> = emptyMap(),
    val regions: List<RegionConstraint> = emptyList(),
    val blockedTags: Set<String> = emptySet(),
    /** MOD-001 2.0.0: conditioning modalities this user doesn't use. */
    val excludedModalities: Set<com.personalfitnesscoach.engine.model.Modality> = emptySet(),
    val excludedIds: Set<String> = emptySet(),
    val preferences: Map<String, Double> = emptyMap(),
    val inDeload: Boolean = false,
    /** Week-so-far state for SAF-008 (sets, SSU, HIIT, workload, spacing). Level, screening and limits are filled in here. */
    val week: ValidationContext = ValidationContext(Level.BEGINNER),
    /** SAF-010 merged health-condition limits ([com.personalfitnesscoach.engine.safety.Conditions.resolve]). */
    val conditions: ConditionLimits = ConditionLimits.NONE,
    /** EQ-003: the user wants jumping moves in bodyweight circuits (used only where impact is allowed; no-jump by default). */
    val circuitJumps: Boolean = false,
)

/** CON-004 1.1.0: the osteoporosis entry's short bone-loading block (not an impact session); the variant fits today's pain limits. */
data class BoneLoadingBlock(val minutes: Double, val landings: Int, val description: String,
                            val variant: com.personalfitnesscoach.engine.safety.BoneLoadingVariant = com.personalfitnesscoach.engine.safety.BoneLoadingVariant.SMALL_HOPS)

/** One exercise of the generated workout. `load` is kg (assistance kg for assisted moves), null for bodyweight, holds and carries without load. */
data class WorkoutItem(
    val exercise: Exercise,
    val slotKey: String,
    val role: SlotRole,
    val priority: Priority,
    val sets: Int,
    val reps: IntRange,
    val unit: DoseUnit,
    val perSide: Boolean,
    val targetRir: Double,
    val lastSetToFailure: Boolean,
    val load: Double?,
    val loadFactor: Double,
    val rest: RestWindow,
    val warmupSets: List<WarmupSet>,
    val range: RangeOfMotion,
    val calibrating: Boolean,
    val main: Boolean,
)

data class Workout(
    val tier: Tier,
    val items: List<WorkoutItem>,
    val conditioning: List<ConditioningBlock>,
    val warmupMinutes: Double,
    val warmupDrills: List<DrillDose>,
    val cooldown: List<DrillDose>,
    val plannedMinutes: Double,
    val expressOffered: Boolean,
    val safetyStop: SafetyStop?,
    val fallbackUsed: Boolean,
    /** The exact session the validator approved (SAF-008); nothing else is ever shown. */
    val validated: Session,
    /** The FULL-day working sets the tier and deload cuts were measured from (for audits and re-validation). */
    val fullTierWorkingSets: Int = 0,
    /** Interval structure (repeats, work/rest seconds, CR10) for each validated conditioning block, or null for steady work (HIIT-002). */
    val intervals: List<com.personalfitnesscoach.engine.conditioning.Interval?> = emptyList(),
    /** ORD-002 / CON-001: conditioning comes before the lifts today. */
    val conditioningFirst: Boolean = false,
    /** Mobility minutes in the session (deload extra mobility, RDY-004 LIGHT 5–10 min). */
    val mobilityMinutes: Double = 0.0,
    /** FL-003 / AGE-001 balance drills (supported), after the main work. */
    val balanceDrills: List<DrillDose> = emptyList(),
    /** SAF-010: the condition entries' prompts and stop signs for today (shown with the session). */
    val conditionPrompts: List<String> = emptyList(),
    val stopSigns: List<String> = emptyList(),
    /** SAF-010: go by feel (talk test, CR10), not heart-rate numbers. */
    val effortByFeel: Boolean = false,
    /** CON-004 1.1.0 / SAF-010 osteoporosis: a bone-loading block on training days. */
    val boneLoading: BoneLoadingBlock? = null,
    /** SAF-010 `block_if`: these entries say to follow the care provider's advice instead of a training plan, so nothing is planned. */
    val followCareProvider: Set<String> = emptySet(),
    /** EQ-003: the moves for each bodyweight-circuit block (aligned with `conditioning`; null for other modalities). */
    val circuits: List<com.personalfitnesscoach.engine.conditioning.CircuitPlan?> = emptyList(),
)

/**
 * GEN-001: load state → safety/readiness gates → slots → exercise per slot → dose → tier changes →
 * warm-up → time fit → safety validator → output with the Decision Log. Deterministic: the same
 * request always gives the same workout; ties are broken by exercise ID.
 */
object SessionGenerator {
    private val STEPS = listOf("load_state", "safety_readiness_gates", "slots", "exercise_per_slot", "dose", "tier_changes",
        "warmup", "time_fit", "safety_validator", "output")

    fun generate(request: GenerationRequest): EngineResult<Workout> {
        val d = ArrayList<Decision>()
        // SAF-010: condition limits join the limitation tags, joint limits and screening mode, so every later step honours them.
        val c = request.conditions
        val r = if (!c.any) request else request.copy(blockedTags = request.blockedTags + c.avoidTags,
            jointLimits = (request.jointLimits.keys + c.jointLimits.keys).associateWith { minOf(request.jointLimits[it] ?: 4, c.jointLimits[it] ?: 4) },
            screening = if (c.conservative) ScreeningMode.CONSERVATIVE else request.screening)
        if (c.blocked.isNotEmpty()) {
            d += Decision(DecisionKind.SAFETY, listOf(RuleIds.SAF_010), ReasonKey.CONDITION_BLOCKED, outputs = mapOf("entries" to c.blocked.sorted()))
            return EngineResult(rest(null).copy(followCareProvider = c.blocked, conditionPrompts = c.prompts, stopSigns = c.stopSigns), d + done(r, Tier.RECOVERY, true))
        }
        // 2) Safety and readiness gates.
        val stop = RedFlags.check(r.redFlags)
        d += stop.decisions
        if (stop.value != null) return EngineResult(rest(stop.value), d + done(r, Tier.RECOVERY, true))
        val ill = RedFlags.illnessGate(r.illnessSymptoms)
        d += ill.decisions
        // SAF-007: illness locks the day to rest — no lifts, no conditioning, no warm-up (the optional Z1 of RECOVERY is for readiness only).
        if (ill.value != null) return EngineResult(rest(null), d + Decision(DecisionKind.SAFETY, listOf(RuleIds.SAF_007), ReasonKey.ILLNESS_REST_DAY) + done(r, Tier.RECOVERY, true))
        val tier = r.tier

        // 3) Slots from the week plan; 4) exercise per slot (planned lift if allowed today, else the best swap).
        val sub = SubContext(r.equipmentToday, r.level, mergedLimits(r), r.blockedTags, r.excludedIds, r.preferences, r.painCaution + r.regions.map { it.region })
        val slots = ArrayList<PlannedSlot>()
        if (tier != Tier.RECOVERY) for (s in r.day.slots) {
            if (s.power && !Concurrent.powerAllowed(r.hiitEarlierToday, atSessionStart = true)) {
                d += Decision(DecisionKind.SUBSTITUTION, listOf(RuleIds.CON_005), ReasonKey.POWER_DROPPED_AFTER_HIIT, inputs = mapOf("exercise" to s.exercise.id)); continue
            }
            if (allowedToday(s.exercise, r, sub)) { slots += s; continue }
            // A swap never repeats an exercise already in today's session; jumps or throws only replace power work, and
            // power work is only replaced by power work (a slow tempo squat is not a box jump) — otherwise it is left out today.
            val taken = (r.day.slots.map { it.exercise.id } + slots.map { it.exercise.id }).toSet()
            val swap = Substitution.options(s.exercise, Library.all.filter { !it.userAddOnly && it.id !in taken &&
                (if (s.spec.role == SlotRole.POWER) it.powerCapable else !it.powerOnly) }, sub)
            d += swap.decisions
            val pick = swap.value.autoPick?.exercise
            if (pick == null) { d += Decision(DecisionKind.SUBSTITUTION, listOf(RuleIds.GEN_001), ReasonKey.SLOT_EMPTY, inputs = mapOf("slot" to s.spec.key)); continue }
            slots += s.copy(exercise = pick, rest = Rest.forSets(pick, s.reps.last), unit = pick.unit, perSide = pick.unilateral,
                reps = if (pick.unit == s.unit) s.reps else pick.defaultRepRange)
        }
        // PROG-002: the progression engine owns the rep range (it may have been extended) when there is no e1RM, or when
        // its prescription was made for today's range (D-055).
        for ((k, s) in slots.withIndex()) {
            val p = r.progression[s.exercise.id]
            if (p != null && s.unit == DoseUnit.REPS && (s.exercise.id !in r.e1rm || p.repRange.first == s.reps.first)) slots[k] = s.copy(reps = p.repRange)
        }
        // ORD-001/002: power, primary, secondary, accessories, core; conditioning first on conditioning-priority days.
        val ordered = Order.sort(slots, { slotOf(it) }, r.day.conditioningPriority && !c.strengthBeforeCardio)

        // 5) Dose (loads) and 6) tier changes (RDY-004, DEL-003, SAF-001 conservative mode).
        // The FULL-day reference for tier and deload cuts: the normal plan (before any DEL-003 cut the week planner applied).
        val fullSets = if (r.inDeload) ordered.sumOf { maxOf(it.fullSets, it.sets) } else ordered.sumOf { it.sets }
        val rir0Allowed = Caps.rir0ExercisesPerSession(r.level, r.weeksTraining, tier, r.inDeload, r.screening)
        var failures = 0
        var items = ordered.map { s ->
            val rirPlan = s.targetRir
            var rir = rirPlan
            var lastToFailure = false
            if (rir < 1.0) {
                if (s.exercise.failureSafe && failures < rir0Allowed) { lastToFailure = true; failures++ }
                rir = 1.0
            }
            if (tier == Tier.MODIFIED) rir += P.RDY_004.MODIFIED.rir_offset
            if (tier == Tier.LIGHT) rir = maxOf(rir, P.RDY_004.LIGHT.min_rir.toDouble())
            if (r.inDeload) rir = maxOf(rir, P.DEL_003.min_rir.toDouble())
            if (r.screening == ScreeningMode.CONSERVATIVE) rir = maxOf(rir, P.SAF_001.conservative_mode.min_rir.toDouble())
            r.regions.filter { reg -> s.exercise.stress(reg.region) > 0 }.mapNotNull { it.minRir }.maxOrNull()?.let { rir = maxOf(rir, it.toDouble()) }
            // SAF-010: the entries' RIR floor and no training to failure.
            c.minRirFor(s.exercise.limitationTags)?.let { if (it > rir) { rir = it; lastToFailure = false } }
            if (!c.failureAllowed) lastToFailure = false
            if (tier != Tier.FULL || r.inDeload) lastToFailure = false
            item(s, r, tier, rir, lastToFailure, d)
        }.filterNotNull()
        items = trimForTier(items, tier, r.inDeload, fullSets, d)
        var conditioning = r.day.conditioning.map { adaptConditioning(it, tier, r) }.filterNotNull()
        // EQ-003 / SUB-001: a cardio machine that isn't here today becomes a bodyweight circuit (no-jump unless the user wants jumps
        // and impact is allowed; jumping circuits then count as impact work under CON-004).
        conditioning = conditioning.map { b ->
            if (ModalitySelection.available(b.modality, r.equipmentToday)) b else {
                // Impact only when a jumping move is actually chosen (review R3-09), so an all-no-jump circuit never uses up CON-004's allowance.
                val jumps = jumpsOk(r) && Circuits.build(b.zone, b.workMinutes, null, r.level, jumping = true, jointLimits = mergedLimits(r),
                    avoidTags = r.blockedTags, offset = r.day.weekday).value?.moves?.any { it.jumping } == true
                d += Decision(DecisionKind.SUBSTITUTION, listOf(RuleIds.EQ_003, RuleIds.SUB_001), ReasonKey.AWAY_FROM_GYM,
                    inputs = mapOf("modality" to b.modality.name), outputs = mapOf("to" to Modality.BODYWEIGHT_CIRCUIT.name, "jumping" to jumps))
                b.copy(modality = Modality.BODYWEIGHT_CIRCUIT, impact = if (jumps) 1 else 0)
            }
        }
        if (tier == Tier.RECOVERY) conditioning = listOfNotNull(conditioning.firstOrNull()?.let {
            it.copy(zone = Zone.Z1, workMinutes = P.RDY_004.RECOVERY.optional_z1_minutes[0].toDouble(), restMinutes = 0.0, hiit = false, protocol = null) })
        if (tier != r.tier || r.inDeload) d += Decision(DecisionKind.TIER_CAP, listOf(RuleIds.RDY_004) + if (r.inDeload) listOf(RuleIds.DEL_003) else emptyList(),
            ReasonKey.TIER_CAPPED_BY_ITEM, inputs = mapOf("requested" to r.tier, "deload" to r.inDeload), outputs = mapOf("tier" to tier))

        // 7) Warm-up: RAMP minutes, dynamic drills for today's patterns (MOB-001), ramp-up sets for the first two main lifts (WU-002).
        val wu0 = Warmup.minutes(10.0, compressed = false, age = r.age)
        // SAF-010: extra warm-up minutes from the condition entries (the highest applies).
        // A RECOVERY day's optional easy Z1 block starts gently by itself, so the condition extras apply to training sessions only (re-review N2).
        val extrasApply = tier != Tier.RECOVERY
        val extraWarm = if (extrasApply) c.extraWarmupMin else 0
        val extraCool = if (extrasApply) c.extraCooldownMin else 0
        val wu = if (extraWarm > 0) wu0.map { it + extraWarm } + listOf(Decision(DecisionKind.WARMUP, listOf(RuleIds.SAF_010),
            ReasonKey.CONDITION_LIMIT_APPLIED, outputs = mapOf("extraWarmupMin" to extraWarm))) else wu0
        d += wu.decisions
        val patterns = items.map { it.exercise.pattern }.toSet()
        val drills = Mobility.warmupDrills(patterns, r.equipmentToday, restricted = mergedLimits(r).filterValues { it <= 1 }.keys, age = r.age,
            jointLimits = mergedLimits(r), avoidTags = r.blockedTags)
        d += drills.decisions
        var mainSeen = 0
        items = items.map { it ->
            if (!it.main || it.load == null || mainSeen >= 2) it else {
                val first = mainSeen == 0
                mainSeen++
                val ramp = Warmup.rampSets(it.load, it.reps.first, r.e1rm[it.exercise.id], it.exercise.loadType,
                    PlateMath.loadsFor(it.exercise, r.inventory), firstMainLift = first, barKg = barKg(it.exercise, r.inventory))
                d += ramp.decisions
                it.copy(warmupSets = ramp.value)
            }
        }

        // 8) Time fit (TIME-001..004): compress or extend to the minutes available.
        val trained = items.flatMap { it.exercise.primary }.toSet()
        val dayForFit = r.day.copy(slots = items.map { toSlot(it) }, conditioning = conditioning.map { toPlanned(it) })
        // RDY-004: LIGHT days add 5–10 minutes of mobility.
        val mobilityMin = if (tier == Tier.LIGHT) maxOf(r.day.mobilityMinutes, P.RDY_004.LIGHT.mobility_minutes[0].toDouble()) else r.day.mobilityMinutes
        // FL-003 / AGE-001: the day's balance minutes stay on every tier except RECOVERY (balance work is low effort).
        val balanceMin = if (tier == Tier.RECOVERY) 0.0 else r.day.balanceMinutes
        // CON-004 1.1.0: the osteoporosis bone-loading block (≤ 5 min) on training days while impact stays "encouraged" — small hops,
        // or heel drops when pain limits, a jumping block or a no-jumping region rule hops out; none when neither fits (review R3-11).
        val boneVariant = if (c.boneLoading && tier != Tier.RECOVERY && items.isNotEmpty())
            com.personalfitnesscoach.engine.safety.BoneLoading.choose(mergedLimits(r), r.blockedTags, r.regions, r.painCaution) else null
        val bone = boneVariant?.let { variant ->
            val e = com.personalfitnesscoach.engine.safety.Conditions["osteoporosis"]
            BoneLoadingBlock(P.CON_004.bone_loading_block_max_minutes.toDouble(), e?.boneLoadingImpactsMin ?: 50, e?.boneLoadingText ?: "bone-loading block", variant)
        }
        if (bone != null) d += Decision(DecisionKind.WARMUP, listOf(RuleIds.CON_004, RuleIds.SAF_010), ReasonKey.BONE_LOADING_BLOCK,
            outputs = mapOf("minutes" to bone.minutes, "landings" to bone.landings, "variant" to bone.variant.name))
        else if (c.boneLoading && tier != Tier.RECOVERY && items.isNotEmpty()) d += Decision(DecisionKind.WARMUP, listOf(RuleIds.CON_004, RuleIds.SAF_010),
            ReasonKey.BONE_LOADING_VARIANT, outputs = mapOf("variant" to null, "reason" to "pain_limits"))
        val extraMin = balanceMin + (bone?.minutes ?: 0.0)
        val plan = SessionPlan(wu.value, P.TIME_001.cooldown_min_minutes.toDouble() + extraCool, WeekPlanner.planItems(dayForFit),
            coreMobilityMin = mobilityMin + extraMin)
        val fit = TimeBudget.fit(plan, r.minutes.toDouble(), r.age, r.personalFactor, r.crowded,
            extraWarmupMin = extraWarm.toDouble(), extraCooldownMin = extraCool.toDouble())
        d += fit.decisions
        val byId = fit.value.plan.items.associateBy { it.id }
        items = items.mapIndexedNotNull { idx, it -> byId["$idx:${it.exercise.id}"]?.let { p -> it.copy(sets = p.sets) } }
        conditioning = conditioning.mapIndexedNotNull { idx, c ->
            val p = byId["c$idx:${c.modality}"] ?: return@mapIndexedNotNull null
            val k = p.conditioning ?: return@mapIndexedNotNull c
            if (k.rounds > 1) c.copy(workMinutes = k.rounds * k.workSec / 60.0, restMinutes = k.rounds * k.restSec / 60.0) else c.copy(workMinutes = k.workSec / 60.0)
        }
        val warmupMin = fit.value.plan.warmupMin
        val cooldown = Mobility.cooldown(trained, fit.value.plan.cooldownMin, r.equipmentToday, mergedLimits(r), r.blockedTags)
        d += cooldown.decisions
        val balance = Mobility.balanceDrills(balanceMin, r.equipmentToday, mergedLimits(r), r.blockedTags, offset = r.day.weekday)
        d += balance.decisions

        // 9) Safety validator (SAF-008): corrected or replaced, never shown unvalidated.
        val session = Session(tier, items.map { SessionExercise(it.exercise, it.sets, it.reps.last, it.targetRir, it.loadFactor, it.main, it.lastSetToFailure, fullTierRir = slotRir(it, r)) },
            conditioning, warmupMinutes = warmupMin, cooldownMinutes = fit.value.plan.cooldownMin, boneLoading = bone?.variant)
        val ctx = r.week.copy(level = r.level, weeksTraining = r.weeksTraining, age = r.age, screening = r.screening, jointLimits = mergedLimits(r), regions = r.regions,
            blockedTags = r.blockedTags, excludedIds = r.excludedIds, inDeload = r.inDeload, fullTierWorkingSets = fullSets,
            equipmentToday = r.equipmentToday, library = Library.all.filter { !it.userAddOnly },
            excludedModalities = r.week.excludedModalities + r.excludedModalities, conditions = c,
            planImpactAllowed = r.day.impactAllowed && r.week.planImpactAllowed,
            planIntervalModalities = listOfNotNull(r.day.intervalModalities, r.week.planIntervalModalities).reduceOrNull { a, b -> a intersect b })
        val v = SessionValidator.validate(session, ctx)
        d += v.decisions
        val final = rebuild(items, v.value.session, r)
        val finalWarmup = v.value.session.warmupMinutes ?: warmupMin
        val finalCooldown = v.value.session.cooldownMinutes ?: fit.value.plan.cooldownMin
        val finalBone = bone?.takeIf { v.value.session.boneLoading != null }?.copy(variant = v.value.session.boneLoading!!)
        val minutes = TimeModel.minutes(SessionPlan(finalWarmup, finalCooldown,
            WeekPlanner.planItems(r.day.copy(slots = final.map { toSlot(it) }, conditioning = v.value.session.conditioning.map { toPlanned(it) })),
            coreMobilityMin = mobilityMin + balanceMin + (finalBone?.minutes ?: 0.0)), r.personalFactor)
        // Interval structure for each validated block: kept when the validator left the block's zone and modality alone.
        val intervals = v.value.session.conditioning.mapIndexed { j, b ->
            val planned = conditioning.getOrNull(j)
            val iv = r.day.conditioning.firstOrNull { it.modality == planned?.modality && it.zone == b.zone }?.interval
            if (iv == null || planned == null || planned.modality != b.modality || planned.zone != b.zone || iv.workSec <= 0) null
            else iv.copy(reps = maxOf(1, Math.round(b.workMinutes * 60 / iv.workSec).toInt()))
        }

        // EQ-003: the moves for each bodyweight-circuit block.
        val circuits = v.value.session.conditioning.mapIndexed { j, b ->
            if (b.modality != Modality.BODYWEIGHT_CIRCUIT) null else {
                val res = Circuits.build(b.zone, b.workMinutes, intervals.getOrNull(j), r.level, jumping = b.impact > 0 && jumpsOk(r),
                    jointLimits = mergedLimits(r), avoidTags = r.blockedTags, offset = r.day.weekday)
                d += res.decisions
                res.value
            }
        }

        // 10) Output.
        val w = Workout(v.value.session.tier, final, v.value.session.conditioning, finalWarmup, drills.value, cooldown.value, Num.round1(minutes),
            fit.value.expressOffered, null, v.value.fallbackUsed, v.value.session, fullSets, intervals, r.day.conditioningPriority && !c.strengthBeforeCardio,
            mobilityMin, balance.value, c.prompts, c.stopSigns, c.effortByFeel, finalBone, circuits = circuits)
        return EngineResult(w, d + done(r, w.tier, false))
    }

    /** Key for [GenerationRequest.lastWeekLoads]: heavy and moderate exposures of a lift are capped separately. */
    fun loadKey(slotKey: String, exerciseId: String): String = "$slotKey|$exerciseId"

    /**
     * ADH-004: the 20–30-minute express version of today's session, one tap away. It keeps the
     * primary lift (P1), one other compound and one core exercise, drops conditioning, and is fitted
     * to 30 minutes by TIME-002; the validator still runs last.
     */
    fun express(r: GenerationRequest): EngineResult<Workout> {
        val slots = r.day.slots
        val p1 = slots.firstOrNull { it.priority == Priority.P1 } ?: slots.firstOrNull { it.main }
        val second = slots.firstOrNull { it !== p1 && (it.spec.role == SlotRole.MAIN || it.spec.role == SlotRole.SECONDARY) &&
            it.exercise.pattern != p1?.exercise?.pattern }
        val core = slots.firstOrNull { it.spec.role == SlotRole.CORE }
        val minutes = Express.minutes.last
        // Smaller and smaller versions until one fits 30 minutes: core 2 → 1 set, second compound 2 → 1, core out,
        // P1 3 → 2 sets, second compound out. The P1 lift is always kept.
        val p1Sets = p1?.let { minOf(it.sets, 3) } ?: 0
        val variants = listOf(Triple(p1Sets, 2, 2), Triple(p1Sets, 2, 1), Triple(p1Sets, 1, 1), Triple(p1Sets, 1, 0),
            Triple(minOf(p1Sets, 2), 1, 0), Triple(minOf(p1Sets, 2), 0, 0))
        var keep: List<PlannedSlot> = emptyList()
        var out: EngineResult<Workout>? = null
        for ((a, b, c) in variants) {
            keep = listOfNotNull(p1?.let { it.copy(sets = maxOf(1, a)) }, second?.takeIf { b > 0 }?.let { it.copy(sets = minOf(it.sets, b)) },
                core?.takeIf { c > 0 }?.let { it.copy(sets = minOf(it.sets, c)) })
            out = generate(r.copy(day = r.day.copy(slots = keep, conditioning = emptyList(), mobilityMinutes = 0.0, conditioningPriority = false, balanceMinutes = 0.0), minutes = minutes))
            if (out.value.plannedMinutes <= minutes + 1e-9) break
        }
        val res = out!!
        val dec = ArrayList<Decision>()
        dec += Decision(DecisionKind.TIME_FIT, listOf(RuleIds.ADH_004, RuleIds.TIME_002), ReasonKey.EXPRESS_SESSION,
            inputs = mapOf("minutes" to minutes), outputs = mapOf("planned" to res.value.plannedMinutes, "kept" to keep.map { it.exercise.id }))
        // A condition's longer warm-up and cool-down are never cut to fit (review R3-02); when even the smallest version needs more than
        // 30 minutes the app says how long the shortest safe session is and offers an easy walk instead.
        if (res.value.plannedMinutes > minutes + 1e-9) dec += Decision(DecisionKind.TIME_FIT, listOf(RuleIds.ADH_004, RuleIds.SAF_010, RuleIds.WU_003),
            ReasonKey.EXPRESS_TOO_SHORT, inputs = mapOf("minutes" to minutes),
            outputs = mapOf("shortestSafeMinutes" to Math.ceil(res.value.plannedMinutes).toInt(), "extraWarmupMin" to r.conditions.extraWarmupMin,
                "extraCooldownMin" to r.conditions.extraCooldownMin, "offerWalk" to true))
        return res + dec
    }

    // ------------------------------------------------------------------ helpers

    private fun done(r: GenerationRequest, tier: Tier, stopped: Boolean) = Decision(DecisionKind.SAFETY, listOf(RuleIds.GEN_001), ReasonKey.SESSION_GENERATED,
        inputs = mapOf("template" to r.day.template.name, "minutes" to r.minutes, "tier" to r.tier.name),
        outputs = mapOf("steps" to (if (stopped) STEPS.take(2) else STEPS), "tier" to tier.name))

    private fun rest(stop: SafetyStop?) = Workout(Tier.RECOVERY, emptyList(), emptyList(), 0.0, emptyList(), emptyList(), 0.0, false, stop, false,
        Session(Tier.RECOVERY, emptyList()))

    /** Jumping circuit moves: the user asked for them, impact is allowed by the conditions and the CON-004 week so far. */
    private fun jumpsOk(r: GenerationRequest): Boolean = r.circuitJumps && r.conditions.impact.allowsImpact && r.day.impactAllowed && r.week.planImpactAllowed &&
        r.week.impactSessionsThisWeekSoFar < P.CON_004.impact_sessions_per_week_max && !r.week.heavyLegsNextDay

    private fun mergedLimits(r: GenerationRequest): Map<Joint, Int> {
        val out = r.jointLimits.toMutableMap()
        for (reg in r.regions) reg.maxStress?.let { out[reg.region] = minOf(out[reg.region] ?: 4, it) }
        return out
    }

    private fun allowedToday(e: Exercise, r: GenerationRequest, sub: SubContext): Boolean =
        e.usableWith(r.equipmentToday) && e.id !in r.excludedIds && e.limitationTags.none { it in r.blockedTags } &&
            sub.jointLimits.all { (j, lim) -> e.stress(j) <= lim } && e.equipment.none { it in Substitution.CARDIO_MACHINE_EQUIPMENT }

    private fun slotOf(s: PlannedSlot): OrderSlot = when (s.spec.role) {
        SlotRole.POWER -> OrderSlot.POWER
        SlotRole.MAIN -> if (s.priority == Priority.P1) OrderSlot.PRIMARY_COMPOUND else OrderSlot.SECONDARY_COMPOUND
        SlotRole.SECONDARY -> OrderSlot.SECONDARY_COMPOUND
        SlotRole.ACCESSORY -> OrderSlot.ACCESSORIES
        else -> OrderSlot.CORE
    }

    private fun barKg(e: Exercise, inv: Inventory): Double = when (e.bar) {
        null, "barbell" -> inv.barKg
        "landmine" -> 0.0
        else -> inv.bars[e.bar] ?: inv.barKg
    }

    private fun slotRir(it: WorkoutItem, r: GenerationRequest): Double =
        r.day.slots.firstOrNull { s -> s.spec.key == it.slotKey }?.targetRir?.let { maxOf(it, 1.0) } ?: it.targetRir

    /** Normal working load (FULL day) from e1RM (INT-005), the progression engine, or calibration (CAL-001, IND-001). */
    private fun normalLoad(s: PlannedSlot, e: Exercise, r: GenerationRequest, rir: Double, d: MutableList<Decision>): Pair<Double?, Boolean> {
        if (e.loadType == LoadType.BODYWEIGHT || e.loadType == LoadType.TIME || e.loadType == LoadType.DISTANCE) return null to false
        val available = PlateMath.loadsFor(e, r.inventory)
        if (available.isEmpty()) return null to false
        if (e.assisted) return (r.progression[e.id]?.load ?: PlateMath.choose(available.max() * 0.5, available)) to (e.id !in r.progression)
        val est = r.e1rm[e.id]
        val ceiling = r.calibrationCeilings[e.id]
        fun capped(x: Double): Double = if (ceiling == null || x <= ceiling + 1e-9) x else available.filter { it <= ceiling + 1e-9 }.maxOrNull() ?: available.min()
        if (est == null) r.calibrationLoads[e.id]?.let { return capped(PlateMath.choose(it, available)) to true }
        // FS-5 / D-055: the next-exposure prescription (PROG-001..008) sets the load whenever it was made for today's rep
        // range, for e1RM lifts too, so double progression (PROG-002) and the under-loaded jump (D-052) work within a block.
        // Progression.next already applies PROG-007 to load jumps through the implied intensity (PROG-002 resets reps on a
        // jump). A new range (the next block, the other DUP exposure) takes its load from the e1RM (INT-005).
        val prog = r.progression[e.id]
        if (prog != null && (est == null || prog.repRange.first == s.reps.first)) {
            d += Decision(DecisionKind.LOAD_PRESCRIPTION, listOf(RuleIds.PROG_001, RuleIds.PROG_002), ReasonKey.LOAD_FROM_PROGRESSION,
                inputs = mapOf("exercise" to e.id, "action" to prog.action.name), outputs = mapOf("load" to prog.load, "reps" to prog.repRange.toString()))
            return prog.load to false
        }
        if (est == null) return Individual.calibrationStart(e, r.bodyweightKg, available)?.let { capped(it) } to true
        val proposed = E1rm.prescribe(est, (s.reps.first + s.reps.last) / 2, rir, available).also { d += it.decisions }.value
        // PROG-007 / SAF-005: an e1RM-based load never rises faster than the weekly intensity cap over last week's load for this exposure.
        val last = r.lastWeekLoads[loadKey(s.spec.key, e.id)] ?: return proposed to false
        val capped = ProgressionCaps.capLoad(proposed, last, r.level, available, r.age)
        d += capped.decisions
        return capped.value to false
    }

    /**
     * Dose one slot. RDY-004 caps main lifts (95% MODIFIED, 85% LIGHT) and DEL-003 caps everything at 90% in
     * a deload; loads round down to real equipment and the reported load factor is the real one. When even the
     * lightest load is above today's cap, the exercise's regression is tried; if that does not help either, the
     * exercise is left out today (null). Assisted moves get more assistance instead of less load.
     */
    private fun item(s: PlannedSlot, r: GenerationRequest, tier: Tier, rir: Double, lastToFailure: Boolean, d: MutableList<Decision>, allowRegression: Boolean = true): WorkoutItem? {
        val e = s.exercise
        val (normal, calibrating) = normalLoad(s, e, r, s.targetRir.coerceAtLeast(1.0), d)
        var factor = SessionValidator.maxLoadFactor(s.main, tier, r.inDeload)
        if (s.spec.role == SlotRole.POWER) factor = minOf(factor, 1.0)
        var load = normal
        var lf = 1.0
        if (normal != null && factor < 1.0) {
            val avail = PlateMath.loadsFor(e, r.inventory)
            when {
                e.assisted -> { load = PlateMath.nextAbove(normal, avail) ?: normal; lf = factor }
                // Calibration starts at the lightest sensible load and CAL-001 governs it; today's cap is met by construction.
                calibrating -> { load = avail.filter { it <= normal + 1e-9 }.maxOrNull() ?: normal; lf = factor }
                else -> {
                    load = avail.filter { it <= normal * factor + 1e-9 }.maxOrNull() ?: avail.min()
                    lf = if (normal > 0) load / normal else factor
                }
            }
        }
        if (lf > factor + 1e-9) {
            val reg = if (allowRegression) Library.regressionOf(e)?.takeIf { allowedToday(it, r, SubContext(r.equipmentToday, r.level, mergedLimits(r), r.blockedTags, r.excludedIds)) } else null
            val alt = reg?.let { item(s.copy(exercise = it, unit = it.unit, perSide = it.unilateral, rest = Rest.forSets(it, s.reps.last)), r, tier, rir, lastToFailure, d, allowRegression = false) }
            d += Decision(DecisionKind.LOAD_CHANGE, listOf(RuleIds.RDY_004, RuleIds.PROG_003), ReasonKey.LOAD_CANNOT_REDUCE,
                inputs = mapOf("exercise" to e.id, "normal" to normal, "lightest" to load, "cap" to factor), outputs = mapOf("regression" to alt?.exercise?.id))
            return alt
        }
        // MOB-004 1.1.0: pain caution or a SAF-010 range-limited tag (deep knee or hip bending with osteoarthritis) shortens the range.
        val range = Mobility.rangeFor(painCaution = e.jointStress.keys.any { it in r.painCaution } || e.limitationTags.any { it in r.conditions.rangeLimitedTags })
        return WorkoutItem(e, s.spec.key, s.spec.role, s.priority, s.sets, s.reps, s.unit, s.perSide, rir, lastToFailure, load, Num.round2(minOf(lf, 1.0)),
            Rest.forSets(e, s.reps.last, r.e1rm[e.id]?.let { est -> load?.let { it / est * 100 } }), emptyList(), range, calibrating, s.main)
    }

    /** RDY-004 / DEL-003 set cuts on the whole session: lowest priority first, each exercise keeps ≥ 1 set, then drop. */
    private fun trimForTier(items: List<WorkoutItem>, tier: Tier, deload: Boolean, fullSets: Int, d: MutableList<Decision>): List<WorkoutItem> {
        var cut = when (tier) { Tier.MODIFIED -> P.RDY_004.MODIFIED.sets_reduction_pct; Tier.LIGHT -> P.RDY_004.LIGHT.sets_reduction_pct; else -> 0 }
        if (deload) cut = maxOf(cut, P.DEL_003.sets_reduction_pct)
        if (cut == 0 || tier == Tier.RECOVERY) return items
        val cap = maxOf(1, Math.floor(fullSets * (1.0 - cut / 100.0) + 1e-9).toInt())
        val out = items.toMutableList()
        val order = out.indices.sortedWith(compareByDescending<Int> { out[it].priority.ordinal }.thenByDescending { it })
        var total = out.sumOf { it.sets }
        while (total > cap) {
            val i = order.firstOrNull { out[it].sets > 1 } ?: break
            out[i] = out[i].copy(sets = out[i].sets - 1); total--
            if (order.none { out[it].sets > 1 }) break
        }
        var kept = out.toList()
        for (i in order) { if (kept.sumOf { it.sets } <= cap) break; kept = kept.filter { it !== out[i] } }
        d += Decision(DecisionKind.VOLUME_CHANGE, listOf(if (deload) RuleIds.DEL_003 else RuleIds.RDY_004), ReasonKey.TIER_CAPPED_BY_ITEM,
            inputs = mapOf("fullSets" to fullSets, "tier" to tier.name), outputs = mapOf("sets" to kept.sumOf { it.sets }))
        return kept
    }

    /** RDY-004: MODIFIED swaps HIIT for 15–20 min steady work; LIGHT and deloads keep Z1 only (10–20 min); RECOVERY handled by the caller. */
    private fun adaptConditioning(c: PlannedConditioning, tier: Tier, r: GenerationRequest): ConditioningBlock? {
        val b = c.toBlock()
        return when {
            // Conversions keep the block's impact flag, so CON-004 and SAF-004 still see jump rope as impact work.
            tier == Tier.LIGHT || r.inDeload -> b.copy(zone = Zone.Z1, hiit = false, protocol = null, restMinutes = 0.0,
                workMinutes = minOf(c.workMinutes + c.restMinutes, P.RDY_004.LIGHT.z1_minutes[1].toDouble()).coerceAtLeast(P.RDY_004.LIGHT.z1_minutes[0].toDouble()))
            tier == Tier.MODIFIED && b.countsAsHiit -> b.copy(zone = if (r.screening == ScreeningMode.STANDARD) Zone.Z2 else Zone.Z1, hiit = false, protocol = null,
                restMinutes = 0.0, workMinutes = P.RDY_004.MODIFIED.hiit_replacement_minutes[0].toDouble())
            else -> b
        }
    }

    private fun toSlot(it: WorkoutItem) = PlannedSlot(com.personalfitnesscoach.engine.program.SlotSpec(it.slotKey, it.role, it.exercise.pattern),
        it.exercise, it.sets, it.reps, it.unit, it.perSide, it.targetRir, it.priority, it.rest, it.main)

    private fun toPlanned(b: ConditioningBlock) = PlannedConditioning(b.modality, b.zone, b.workMinutes, b.restMinutes,
        b.protocol?.let { p -> com.personalfitnesscoach.engine.conditioning.Interval(p, 1, Math.round(b.workMinutes * 60).toInt(), Math.round(b.restMinutes * 60).toInt(), 7..9) },
        b.hiit, b.impact)

    /**
     * Rebuild workout items from the validated session. The validator keeps the order and may trim, swap or
     * remove, so items are matched in sequence: same exercise → same item; otherwise a removed item is skipped
     * when the exercise appears later, or the item was swapped (it then gets a role, rep range, load and ramp of its own).
     */
    private fun rebuild(before: List<WorkoutItem>, s: Session, r: GenerationRequest): List<WorkoutItem> {
        val out = ArrayList<WorkoutItem>()
        var p = 0
        var mainSeen = 0
        for ((idx, e) in s.exercises.withIndex()) {
            val ahead = (p until before.size).firstOrNull { before[it].exercise.id == e.exercise.id }
            val orig: WorkoutItem? = if (ahead != null) before[ahead].also { p = ahead + 1 } else before.getOrNull(p)?.also { p++ }
            val built = when {
                orig == null -> WorkoutItem(e.exercise, "validator:$idx", SlotRole.ACCESSORY, Priority.P4, e.sets, e.exercise.defaultRepRange, e.exercise.unit,
                    e.exercise.unilateral, e.targetRir, e.lastSetToFailure, null, e.loadFactor, Rest.forSets(e.exercise, e.reps), emptyList(), RangeOfMotion.FULL, false, e.main)
                orig.exercise.id != e.exercise.id -> {
                    val role = if (orig.role == SlotRole.POWER && !e.exercise.powerCapable) SlotRole.SECONDARY else orig.role
                    val reps = if (role != orig.role || e.exercise.unit != orig.unit) e.exercise.defaultRepRange else orig.reps
                    val slot = toSlot(orig).copy(spec = com.personalfitnesscoach.engine.program.SlotSpec(orig.slotKey, role, e.exercise.pattern),
                        exercise = e.exercise, sets = e.sets, reps = reps, unit = e.exercise.unit, perSide = e.exercise.unilateral,
                        targetRir = e.targetRir, rest = Rest.forSets(e.exercise, reps.last))
                    item(slot, r, s.tier, e.targetRir, e.lastSetToFailure, ArrayList())?.copy(loadFactor = minOf(e.loadFactor, 1.0))
                        ?: WorkoutItem(e.exercise, orig.slotKey, role, orig.priority, e.sets, reps, e.exercise.unit, e.exercise.unilateral, e.targetRir,
                            e.lastSetToFailure, PlateMath.loadsFor(e.exercise, r.inventory).minOrNull(), e.loadFactor, Rest.forSets(e.exercise, reps.last),
                            emptyList(), RangeOfMotion.FULL, true, e.main)
                }
                else -> {
                    val scaled = if (orig.load != null && e.loadFactor < orig.loadFactor - 1e-9) {
                        val avail = PlateMath.loadsFor(e.exercise, r.inventory)
                        val normal = orig.load / maxOf(orig.loadFactor, 1e-9)
                        avail.filter { it <= normal * e.loadFactor + 1e-9 }.maxOrNull() ?: avail.minOrNull()
                    } else orig.load
                    orig.copy(sets = e.sets, targetRir = e.targetRir, lastSetToFailure = e.lastSetToFailure, loadFactor = e.loadFactor, load = scaled)
                }
            }
            // WU-002 ramp-up sets for the first two main lifts, including ones the validator swapped in.
            val withRamp = if (built.main && built.load != null && mainSeen < 2) {
                val first = mainSeen == 0
                mainSeen++
                if (built.warmupSets.isNotEmpty() && orig?.exercise?.id == built.exercise.id) built
                else built.copy(warmupSets = Warmup.rampSets(built.load, built.reps.first, r.e1rm[built.exercise.id], built.exercise.loadType,
                    PlateMath.loadsFor(built.exercise, r.inventory), firstMainLift = first, barKg = barKg(built.exercise, r.inventory)).value)
            } else built
            out += withRamp
        }
        return out
    }
}

/**
 * EQ-003: training away from the gym today. One tap sets today's equipment to the saved home kit (bodyweight only by default)
 * and the generator substitutes as usual: bodyweight ladders for strength, no-jump circuits for conditioning, the full warm-up.
 */
object AwayFromGym {
    /** The default home kit: bodyweight only ("bodyweight" in EQ-003 means no equipment). */
    val defaultKit: Set<String> get() = P.EQ_003.default_home_kit.filter { it != "bodyweight" }.toSet()

    fun generate(r: GenerationRequest, homeKit: Set<String> = defaultKit): EngineResult<Workout> {
        val res = SessionGenerator.generate(r.copy(equipmentToday = homeKit, crowded = false))
        return res + listOf(Decision(DecisionKind.SUBSTITUTION, listOf(RuleIds.EQ_003), ReasonKey.AWAY_FROM_GYM,
            inputs = mapOf("homeKit" to homeKit.sorted()), outputs = mapOf("exercises" to res.value.items.map { it.exercise.id },
                "conditioning" to res.value.conditioning.map { it.modality.name })))
    }
}
