package com.personalfitnesscoach.engine.generation

import com.personalfitnesscoach.engine.calc.E1rm
import com.personalfitnesscoach.engine.calc.Inventory
import com.personalfitnesscoach.engine.calc.PlateMath
import com.personalfitnesscoach.engine.conditioning.Concurrent
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
    /** The progression engine's next prescription per exercise (PROG-001..008), used when there is no e1RM: load and rep range. */
    val progression: Map<String, Prescription> = emptyMap(),
    /** CAL-001 in progress over sessions 1–4: where today's calibration ramp starts, per exercise. */
    val calibrationLoads: Map<String, Double> = emptyMap(),
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
    val excludedIds: Set<String> = emptySet(),
    val preferences: Map<String, Double> = emptyMap(),
    val inDeload: Boolean = false,
    /** Week-so-far state for SAF-008 (sets, SSU, HIIT, workload, spacing). Level, screening and limits are filled in here. */
    val week: ValidationContext = ValidationContext(Level.BEGINNER),
)

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
)

/**
 * GEN-001: load state → safety/readiness gates → slots → exercise per slot → dose → tier changes →
 * warm-up → time fit → safety validator → output with the Decision Log. Deterministic: the same
 * request always gives the same workout; ties are broken by exercise ID.
 */
object SessionGenerator {
    private val STEPS = listOf("load_state", "safety_readiness_gates", "slots", "exercise_per_slot", "dose", "tier_changes",
        "warmup", "time_fit", "safety_validator", "output")

    fun generate(r: GenerationRequest): EngineResult<Workout> {
        val d = ArrayList<Decision>()
        // 2) Safety and readiness gates.
        val stop = RedFlags.check(r.redFlags)
        d += stop.decisions
        if (stop.value != null) return EngineResult(rest(stop.value), d + done(r, Tier.RECOVERY, true))
        val ill = RedFlags.illnessGate(r.illnessSymptoms)
        d += ill.decisions
        val tier = ill.value?.let { Tier.min(it, r.tier) } ?: r.tier

        // 3) Slots from the week plan; 4) exercise per slot (planned lift if allowed today, else the best swap).
        val sub = SubContext(r.equipmentToday, r.level, mergedLimits(r), r.blockedTags, r.excludedIds, r.preferences, r.painCaution + r.regions.map { it.region })
        val slots = ArrayList<PlannedSlot>()
        if (tier != Tier.RECOVERY) for (s in r.day.slots) {
            if (s.power && !Concurrent.powerAllowed(r.hiitEarlierToday, atSessionStart = true)) {
                d += Decision(DecisionKind.SUBSTITUTION, listOf(RuleIds.CON_005), ReasonKey.POWER_DROPPED_AFTER_HIIT, inputs = mapOf("exercise" to s.exercise.id)); continue
            }
            if (allowedToday(s.exercise, r, sub)) { slots += s; continue }
            val swap = Substitution.options(s.exercise, Library.all.filter { !it.userAddOnly }, sub)
            d += swap.decisions
            val pick = swap.value.autoPick?.exercise
            if (pick == null) { d += Decision(DecisionKind.SUBSTITUTION, listOf(RuleIds.GEN_001), ReasonKey.SLOT_EMPTY, inputs = mapOf("slot" to s.spec.key)); continue }
            slots += s.copy(exercise = pick, rest = Rest.forSets(pick, s.reps.last), unit = pick.unit, perSide = pick.unilateral,
                reps = if (pick.unit == s.unit) s.reps else pick.defaultRepRange)
        }
        // PROG-002: without an e1RM the progression engine owns the rep range (it may have been extended).
        for ((k, s) in slots.withIndex()) {
            val p = r.progression[s.exercise.id]
            if (p != null && s.exercise.id !in r.e1rm && s.unit == DoseUnit.REPS) slots[k] = s.copy(reps = p.repRange)
        }
        // ORD-001/002: power, primary, secondary, accessories, core; conditioning first on conditioning-priority days.
        val ordered = Order.sort(slots, { slotOf(it) }, r.day.conditioningPriority)

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
            if (tier != Tier.FULL || r.inDeload) lastToFailure = false
            item(s, r, tier, rir, lastToFailure, d)
        }
        items = trimForTier(items, tier, r.inDeload, fullSets, d)
        var conditioning = r.day.conditioning.map { adaptConditioning(it, tier, r) }.filterNotNull()
        if (tier == Tier.RECOVERY) conditioning = listOfNotNull(conditioning.firstOrNull()?.let { ConditioningBlock(it.modality, Zone.Z1, P.RDY_004.RECOVERY.optional_z1_minutes[0].toDouble()) })
        if (tier != r.tier || r.inDeload) d += Decision(DecisionKind.TIER_CAP, listOf(RuleIds.RDY_004) + if (r.inDeload) listOf(RuleIds.DEL_003) else emptyList(),
            ReasonKey.TIER_CAPPED_BY_ITEM, inputs = mapOf("requested" to r.tier, "deload" to r.inDeload), outputs = mapOf("tier" to tier))

        // 7) Warm-up: RAMP minutes, dynamic drills for today's patterns (MOB-001), ramp-up sets for the first two main lifts (WU-002).
        val wu = Warmup.minutes(10.0, compressed = false, age = r.age)
        d += wu.decisions
        val patterns = items.map { it.exercise.pattern }.toSet()
        val drills = Mobility.warmupDrills(patterns, r.equipmentToday, restricted = mergedLimits(r).filterValues { it <= 1 }.keys, age = r.age)
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
        val plan = SessionPlan(wu.value, P.TIME_001.cooldown_min_minutes.toDouble(), WeekPlanner.planItems(dayForFit),
            coreMobilityMin = r.day.mobilityMinutes)
        val fit = TimeBudget.fit(plan, r.minutes.toDouble(), r.age, r.personalFactor, r.crowded)
        d += fit.decisions
        val byId = fit.value.plan.items.associateBy { it.id }
        items = items.mapIndexedNotNull { idx, it -> byId["$idx:${it.exercise.id}"]?.let { p -> it.copy(sets = p.sets) } }
        conditioning = conditioning.mapIndexedNotNull { idx, c ->
            val p = byId["c$idx:${c.modality}"] ?: return@mapIndexedNotNull null
            val k = p.conditioning ?: return@mapIndexedNotNull c
            if (k.rounds > 1) c.copy(workMinutes = k.rounds * k.workSec / 60.0, restMinutes = k.rounds * k.restSec / 60.0) else c.copy(workMinutes = k.workSec / 60.0)
        }
        val warmupMin = fit.value.plan.warmupMin
        val cooldown = Mobility.cooldown(trained, fit.value.plan.cooldownMin, r.equipmentToday)
        d += cooldown.decisions

        // 9) Safety validator (SAF-008): corrected or replaced, never shown unvalidated.
        val session = Session(tier, items.map { SessionExercise(it.exercise, it.sets, it.reps.last, it.targetRir, it.loadFactor, it.main, it.lastSetToFailure, fullTierRir = slotRir(it, r)) }, conditioning)
        val ctx = r.week.copy(level = r.level, weeksTraining = r.weeksTraining, screening = r.screening, jointLimits = mergedLimits(r), regions = r.regions,
            blockedTags = r.blockedTags, excludedIds = r.excludedIds, inDeload = r.inDeload, fullTierWorkingSets = fullSets,
            equipmentToday = r.equipmentToday, library = Library.all.filter { !it.userAddOnly })
        val v = SessionValidator.validate(session, ctx)
        d += v.decisions
        val final = rebuild(items, v.value.session, r)
        val minutes = TimeModel.minutes(SessionPlan(warmupMin, fit.value.plan.cooldownMin,
            WeekPlanner.planItems(r.day.copy(slots = final.map { toSlot(it) }, conditioning = v.value.session.conditioning.map { toPlanned(it) })),
            coreMobilityMin = r.day.mobilityMinutes), r.personalFactor)

        // 10) Output.
        val w = Workout(v.value.session.tier, final, v.value.session.conditioning, warmupMin, drills.value, cooldown.value, Num.round1(minutes),
            fit.value.expressOffered, null, v.value.fallbackUsed, v.value.session, fullSets)
        return EngineResult(w, d + done(r, w.tier, false))
    }

    /** Key for [GenerationRequest.lastWeekLoads]: heavy and moderate exposures of a lift are capped separately. */
    fun loadKey(slotKey: String, exerciseId: String): String = "$slotKey|$exerciseId"

    /** ADH-004: the 20–30-minute express version of today's session, one tap away. */
    fun express(r: GenerationRequest): EngineResult<Workout> {
        val minutes = (Express.minutes.first + Express.minutes.last) / 2
        val out = generate(r.copy(minutes = minutes))
        return out + listOf(Decision(DecisionKind.TIME_FIT, listOf(RuleIds.ADH_004, RuleIds.TIME_002), ReasonKey.EXPRESS_SESSION,
            inputs = mapOf("minutes" to minutes), outputs = mapOf("planned" to out.value.plannedMinutes)))
    }

    // ------------------------------------------------------------------ helpers

    private fun done(r: GenerationRequest, tier: Tier, stopped: Boolean) = Decision(DecisionKind.SAFETY, listOf(RuleIds.GEN_001), ReasonKey.SESSION_GENERATED,
        inputs = mapOf("template" to r.day.template.name, "minutes" to r.minutes, "tier" to r.tier.name),
        outputs = mapOf("steps" to (if (stopped) STEPS.take(2) else STEPS), "tier" to tier.name))

    private fun rest(stop: SafetyStop) = Workout(Tier.RECOVERY, emptyList(), emptyList(), 0.0, emptyList(), emptyList(), 0.0, false, stop, false,
        Session(Tier.RECOVERY, emptyList()))

    private fun mergedLimits(r: GenerationRequest): Map<Joint, Int> {
        val out = r.jointLimits.toMutableMap()
        for (reg in r.regions) reg.maxStress?.let { out[reg.region] = minOf(out[reg.region] ?: 4, it) }
        return out
    }

    private fun allowedToday(e: Exercise, r: GenerationRequest, sub: SubContext): Boolean =
        r.equipmentToday.containsAll(e.equipment) && e.id !in r.excludedIds && e.limitationTags.none { it in r.blockedTags } &&
            sub.jointLimits.all { (j, lim) -> e.stress(j) <= lim } && e.equipment.none { it in Substitution.MOD001_EQUIPMENT }

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
        if (est == null) r.calibrationLoads[e.id]?.let { return PlateMath.choose(it, available) to true }
        // Without an e1RM the progression engine's next load is used as is: Progression.next already applies
        // PROG-007 to load jumps through the implied intensity (PROG-002 resets reps on a jump).
        if (est == null) return (r.progression[e.id]?.load ?: return Individual.calibrationStart(e, r.bodyweightKg, available) to true) to false
        val proposed = E1rm.prescribe(est, (s.reps.first + s.reps.last) / 2, rir, available).also { d += it.decisions }.value
        // PROG-007 / SAF-005: an e1RM-based load never rises faster than the weekly intensity cap over last week's load for this exposure.
        val last = r.lastWeekLoads[loadKey(s.spec.key, e.id)] ?: return proposed to false
        val capped = ProgressionCaps.capLoad(proposed, last, r.level, available, r.age)
        d += capped.decisions
        return capped.value to false
    }

    private fun item(s: PlannedSlot, r: GenerationRequest, tier: Tier, rir: Double, lastToFailure: Boolean, d: MutableList<Decision>): WorkoutItem {
        val e = s.exercise
        val (normal, calibrating) = normalLoad(s, e, r, s.targetRir.coerceAtLeast(1.0), d)
        // RDY-004 caps main lifts (95% MODIFIED, 85% LIGHT); DEL-003 caps everything at 90% in a deload. Round down to real loads.
        var factor = SessionValidator.maxLoadFactor(s.main, tier, r.inDeload)
        if (s.spec.role == SlotRole.POWER) factor = minOf(factor, 1.0)
        var load = normal
        if (normal != null && factor < 1.0) {
            val avail = PlateMath.loadsFor(e, r.inventory)
            load = avail.filter { it <= normal * factor + 1e-9 }.maxOrNull() ?: avail.min()
            if (e.assisted) load = normal // more assistance would be easier; keep it simple and never lighter on the body
        }
        val lf = if (normal == null || load == null || normal <= 0.0) factor.coerceAtMost(1.0) else minOf(factor, load / normal)
        val range = Mobility.rangeFor(painCaution = e.jointStress.keys.any { it in r.painCaution })
        return WorkoutItem(e, s.spec.key, s.spec.role, s.priority, s.sets, s.reps, s.unit, s.perSide, rir, lastToFailure, load, Num.round2(lf),
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
            tier == Tier.LIGHT || r.inDeload -> ConditioningBlock(c.modality, Zone.Z1, minOf(c.workMinutes + c.restMinutes, P.RDY_004.LIGHT.z1_minutes[1].toDouble())
                .coerceAtLeast(P.RDY_004.LIGHT.z1_minutes[0].toDouble()))
            tier == Tier.MODIFIED && b.countsAsHiit -> ConditioningBlock(c.modality,
                if (r.screening == ScreeningMode.STANDARD) Zone.Z2 else Zone.Z1, P.RDY_004.MODIFIED.hiit_replacement_minutes[0].toDouble())
            else -> b
        }
    }

    private fun toSlot(it: WorkoutItem) = PlannedSlot(com.personalfitnesscoach.engine.program.SlotSpec(it.slotKey, it.role, it.exercise.pattern),
        it.exercise, it.sets, it.reps, it.unit, it.perSide, it.targetRir, it.priority, it.rest, it.main)

    private fun toPlanned(b: ConditioningBlock) = PlannedConditioning(b.modality, b.zone, b.workMinutes, b.restMinutes,
        b.protocol?.let { p -> com.personalfitnesscoach.engine.conditioning.Interval(p, 1, Math.round(b.workMinutes * 60).toInt(), Math.round(b.restMinutes * 60).toInt(), 7..9) },
        b.hiit, b.impact)

    /** Rebuild workout items from the validated session (the validator may trim, swap or remove). */
    private fun rebuild(before: List<WorkoutItem>, s: Session, r: GenerationRequest): List<WorkoutItem> =
        s.exercises.mapIndexed { idx, e ->
            val orig = before.firstOrNull { it.exercise.id == e.exercise.id } ?: before.getOrNull(idx)
            if (orig == null) {
                WorkoutItem(e.exercise, "validator:$idx", SlotRole.ACCESSORY, Priority.P4, e.sets, e.exercise.defaultRepRange, e.exercise.unit, e.exercise.unilateral,
                    e.targetRir, e.lastSetToFailure, null, e.loadFactor, Rest.forSets(e.exercise, e.reps), emptyList(), RangeOfMotion.FULL, false, e.main)
            } else if (orig.exercise.id != e.exercise.id) {
                val slot = toSlot(orig).copy(exercise = e.exercise, sets = e.sets, targetRir = e.targetRir, rest = Rest.forSets(e.exercise, orig.reps.last))
                item(slot, r, s.tier, e.targetRir, e.lastSetToFailure, ArrayList()).copy(warmupSets = emptyList())
            } else {
                val scaled = if (orig.load != null && e.loadFactor < orig.loadFactor - 1e-9) {
                    val avail = PlateMath.loadsFor(e.exercise, r.inventory)
                    val normal = orig.load / maxOf(orig.loadFactor, 1e-9)
                    avail.filter { it <= normal * e.loadFactor + 1e-9 }.maxOrNull() ?: avail.minOrNull()
                } else orig.load
                orig.copy(sets = e.sets, targetRir = e.targetRir, lastSetToFailure = e.lastSetToFailure, loadFactor = e.loadFactor, load = scaled)
            }
        }
}
