package com.personalfitnesscoach.engine.program

import com.personalfitnesscoach.engine.calc.Volume
import com.personalfitnesscoach.engine.conditioning.Aerobic
import com.personalfitnesscoach.engine.conditioning.AerobicBlock
import com.personalfitnesscoach.engine.conditioning.ConditioningPurpose
import com.personalfitnesscoach.engine.conditioning.HiitMenu
import com.personalfitnesscoach.engine.conditioning.Interval
import com.personalfitnesscoach.engine.conditioning.ModalityContext
import com.personalfitnesscoach.engine.conditioning.ModalitySelection
import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.Num
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.dose.DoseGoal
import com.personalfitnesscoach.engine.dose.Effort
import com.personalfitnesscoach.engine.dose.Order
import com.personalfitnesscoach.engine.dose.Reps
import com.personalfitnesscoach.engine.dose.Rest
import com.personalfitnesscoach.engine.dose.RestWindow
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.Exercise
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.model.Muscle
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.model.Zone
import com.personalfitnesscoach.engine.planning.Conditioning
import com.personalfitnesscoach.engine.planning.PlanItem
import com.personalfitnesscoach.engine.planning.Priority
import com.personalfitnesscoach.engine.planning.SessionPlan
import com.personalfitnesscoach.engine.planning.TimeBudget
import com.personalfitnesscoach.engine.program.Blueprint.Exposure
import com.personalfitnesscoach.engine.progression.Warmup
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds
import com.personalfitnesscoach.engine.safety.Caps
import com.personalfitnesscoach.engine.safety.ConditioningBlock
import com.personalfitnesscoach.engine.safety.HiitContext
import com.personalfitnesscoach.engine.safety.HiitProtocol
import com.personalfitnesscoach.engine.safety.PatternIssue
import com.personalfitnesscoach.engine.safety.ScreeningMode
import com.personalfitnesscoach.engine.safety.Session
import com.personalfitnesscoach.engine.safety.SessionExercise
import com.personalfitnesscoach.engine.safety.WeekChecks

/** Everything the week planner needs. Weekdays are 0 = Monday … 6 = Sunday. */
data class WeekInput(
    val level: Level,
    val weeksTraining: Int,
    val daysPerWeek: Int?,
    val equipment: Set<String>,
    val week: WeekContext,
    val age: Int? = null,
    val availableDays: Set<Int> = (0..6).toSet(),
    val preferredDays: Set<Int> = emptySet(),
    val sessionMinutes: Int = 60,
    /** DEL-002 chose a deload for this deload-or-pivot week (otherwise it is a pivot week). */
    val deload: Boolean = false,
    val priorities: List<Goal> = emptyList(),
    val focusMuscles: Set<Muscle> = emptySet(),
    val screening: ScreeningMode = ScreeningMode.STANDARD,
    /** HIIT-003: screening clear, calibration done and ≥ 3 weeks of Z1 base. */
    val hiitBaseReady: Boolean = false,
    val hiitDoneEver: Int = 0,
    /** Sessions already done with the protocol chosen this week (PROG-006 progression). */
    val hiitExposures: Int = 0,
    val z1SessionMinutes: Double = 15.0,
    val lastWeekAerobicMinutes: Double = 0.0,
    val carryOrRotationLastWeek: Set<Pattern> = emptySet(),
    val injuries: Set<Joint> = emptySet(),
    val blockedTags: Set<String> = emptySet(),
    val jointLimits: Map<Joint, Int> = emptyMap(),
    val excludedIds: Set<String> = emptySet(),
    val preferences: Map<String, Double> = emptyMap(),
    val favourites: Set<String> = emptySet(),
    val crowded: Boolean = false,
    val ladderRungs: Map<String, String> = emptyMap(),
    val coreLifts: Map<String, String> = emptyMap(),
    val previousBlockChoices: Map<String, String> = emptyMap(),
    val modalityPreferences: Map<Modality, Double> = emptyMap(),
)

data class PlannedSlot(
    val spec: SlotSpec,
    val exercise: Exercise,
    val sets: Int,
    val reps: IntRange,
    val unit: DoseUnit,
    val perSide: Boolean,
    val targetRir: Double,
    val priority: Priority,
    val rest: RestWindow,
    val main: Boolean,
    /** Sets before any deload cut (DEL-003), so later checks cut from the normal plan, not twice. */
    val fullSets: Int = sets,
) {
    val power: Boolean get() = spec.role == SlotRole.POWER
}

data class PlannedConditioning(
    val modality: Modality,
    val zone: Zone,
    val workMinutes: Double,
    val restMinutes: Double = 0.0,
    val interval: Interval? = null,
    val hiit: Boolean = false,
    val impact: Int = 0,
    val purpose: ConditioningPurpose = ConditioningPurpose.STEADY,
) {
    val protocol: HiitProtocol? get() = interval?.protocol
    fun toBlock(): ConditioningBlock = ConditioningBlock(modality, zone, workMinutes, restMinutes, hiit, impact, protocol)
}

data class PlannedDay(
    val weekday: Int,
    val template: DayTemplate,
    val slots: List<PlannedSlot>,
    val conditioning: List<PlannedConditioning>,
    val mobilityMinutes: Double,
    val heavyLower: Boolean,
    val conditioningPriority: Boolean = false,
) {
    val workingSets: Int get() = slots.sumOf { it.sets }
    fun toSession(tier: Tier = Tier.FULL): Session = Session(tier,
        slots.map { SessionExercise(it.exercise, it.sets, it.reps.last, it.targetRir, main = it.main, fullTierRir = it.targetRir) },
        conditioning.map { it.toBlock() })
}

data class WeekPlan(
    val days: List<PlannedDay>,
    val blockType: BlockType,
    val blockWeek: Int,
    val loadingWeeks: Int,
    val deload: Boolean,
    /** ADH-003: main lift per slot key for this block. */
    val coreLifts: Map<String, String>,
    val weeklySets: Map<Muscle, Double>,
    val aerobic: List<AerobicBlock>,
    val issues: List<PatternIssue>,
)

/** SCH-001 to SCH-003 with volume allocation (VOL, FREQ, PAT, CORE-003) and the weekly conditioning plan (AER, HIIT, CON, PH-001). */
object WeekPlanner {
    private val TARGET_MUSCLES = listOf(Muscle.CHEST, Muscle.LATS, Muscle.UPPER_BACK, Muscle.SIDE_DELTS, Muscle.REAR_DELTS,
        Muscle.BICEPS, Muscle.TRICEPS, Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES)

    private fun selection(i: WeekInput) = SelectionContext(i.level, i.weeksTraining, i.equipment, i.blockedTags, i.jointLimits, i.excludedIds,
        i.preferences, i.favourites, i.crowded, i.ladderRungs, i.coreLifts, i.previousBlockChoices)

    /** The block whose dose applies this week (a pivot week previews the next block; flex weeks maintain). */
    private fun effective(i: WeekInput, program: Program?): Triple<BlockType, Int, Int> {
        val b = i.week.block
        return when (i.week.kind) {
            WeekKind.DELOAD_OR_PIVOT -> if (i.deload) Triple(b.type, b.loadingWeeks, b.loadingWeeks) else {
                val next = program?.blocks?.getOrNull(i.week.blockIndex + 1)
                Triple(next?.type ?: b.type, 1, next?.loadingWeeks ?: b.loadingWeeks)
            }
            WeekKind.FLEX -> Triple(BlockType.CONSOLIDATION, 1, P.PER_002.default_weeks)
            else -> Triple(b.type, i.week.weekInBlock, b.loadingWeeks)
        }
    }

    fun plan(i: WeekInput, program: Program? = null): EngineResult<WeekPlan> {
        if (i.week.kind == WeekKind.DELOAD_OR_PIVOT && i.deload) return deloadWeek(i, program)
        val d = ArrayList<Decision>()
        val (type, blockWeek, loadingWeeks) = effective(i, program)
        val deload = (i.week.kind == WeekKind.DELOAD_OR_PIVOT && i.deload) || type == BlockType.REVIEW
        val light = deload || type == BlockType.CALIBRATE
        val dose = Blueprint.dose(type, i.level)
        val days = Frequency.trainingDays(i.daysPerWeek)
        val templates = Templates.forDays(days)
        val assign = Templates.assign(templates, i.availableDays, i.preferredDays, i.level)
        d += assign.decisions
        val order = assign.value.order
        val weekdays = assign.value.days

        // Slots (power only outside calibration/deload; AGE-001 keeps power year-round from 50).
        val powerOk = !light && (dose.power || (i.age != null && i.age >= 50 && P.AGE_001.age_50.power_year_round))
        val secondPower = powerOk && type == BlockType.POWER_ATHLETICISM
        val ctx = selection(i)
        val used = HashSet<String>()
        val coreLifts = LinkedHashMap<String, String>()
        val dayList = ArrayList<PlannedDay>()
        var firstLower = true
        for ((idx, t) in order.withIndex()) {
            val isLowerish = t == DayTemplate.FB_A || t == DayTemplate.LOWER_H || t == DayTemplate.FB_B || t == DayTemplate.LOWER_M
            val withPower = powerOk && isLowerish && firstLower && (t == DayTemplate.FB_A || t == DayTemplate.LOWER_H)
            if (withPower) firstLower = false
            val specs = Templates.slots(t, days, withPower, secondPower && (t == DayTemplate.FB_B || t == DayTemplate.LOWER_M))
                .map { s -> if (s.role == SlotRole.CARRY_OR_ROTATION) resolveCarryRotation(s, i) else s }
            val slots = ArrayList<PlannedSlot>()
            var p1Given = false
            for (spec in specs) {
                val r = Selector.select(spec, ctx, used)
                d += r.decisions
                val ex = r.value ?: continue
                used += ex.id
                if (spec.role == SlotRole.MAIN) coreLifts[spec.key] = ex.id
                val priority = when (spec.role) {
                    SlotRole.MAIN -> if (!p1Given) { p1Given = true; Priority.P1 } else Priority.P3
                    SlotRole.POWER, SlotRole.SECONDARY -> Priority.P3
                    SlotRole.ACCESSORY -> Priority.P4
                    else -> Priority.P5
                }
                slots += doseSlot(spec, ex, i, type, blockWeek, loadingWeeks, dose, deload, light, priority)
            }
            dayList += PlannedDay(weekdays[idx], t, slots, emptyList(), if (deload) 5.0 else 0.0, t.heavyLower)
        }
        var week = dayList.sortedBy { it.weekday }

        // Weekly conditioning plan (AER, HIIT, CON, MOD, FREQ-005).
        val cond = conditioning(week, i, type, blockWeek, deload, light)
        week = cond.value; d += cond.decisions
        // Fit each session to the usual session length, keeping weekly coverage (D-051).
        week = week.map { trimToTime(it, i, d) }
        // Volume allocation toward the block's weekly targets, within every cap and the session length.
        val alloc = allocate(week, i, dose, blockWeek, deload)
        week = alloc.value; d += alloc.decisions
        // Safety net: TIME-002 compression if a session still does not fit (normally a no-op).
        week = week.map { fitDay(it, i, d) }

        // Week checks: PAT-001..004 (the validator re-checks every session), FREQ-002, AER-002, PH-001.
        val sessions = week.map { it.toSession() }
        val issues = WeekChecks.issues(sessions, days, i.carryOrRotationLastWeek)
        d += issues.decisions
        val aerobic = week.flatMap { day -> day.conditioning.map { AerobicBlock(it.zone, it.workMinutes, day.weekday) } }
        d += Aerobic.whoCheck(aerobic, week.count { it.slots.isNotEmpty() }).decisions
        val weekly = Volume.weekly(week.flatMap { day -> day.slots.map { it.exercise to it.sets.toDouble() } })
        d += Decision(DecisionKind.VOLUME_CHANGE, listOf(RuleIds.SCH_001, RuleIds.PER_001, RuleIds.FREQ_002), ReasonKey.WEEK_PLANNED,
            inputs = mapOf("block" to type.name, "blockWeek" to blockWeek, "deload" to deload, "days" to days),
            outputs = mapOf("weekdays" to week.map { "${it.weekday}:${it.template}" }, "underExposed" to Frequency.underExposed(sessions).map { it.name }.sorted()))
        return EngineResult(WeekPlan(week, type, blockWeek, loadingWeeks, deload, coreLifts, weekly, aerobic, issues.value), d)
    }

    /**
     * DEL-003: the same days and exercises as the block's last loading week, sets −50% per session
     * (lowest priority and largest first, every exercise keeps a set), RIR ≥ 3, no power, no HIIT,
     * Z1 only and extra mobility.
     */
    private fun deloadWeek(i: WeekInput, program: Program?): EngineResult<WeekPlan> {
        val b = i.week.block
        val normalCtx = WeekContext(b.endWeek, b, b.loadingWeeks, WeekKind.LOADING, i.week.blockIndex)
        val normal = plan(i.copy(week = normalCtx, deload = false), program)
        val pre = P.DEL_003
        val days = normal.value.days.map { day ->
            var slots = day.slots.filter { !it.power }.map { it.copy(fullSets = it.sets, targetRir = maxOf(it.targetRir, pre.min_rir.toDouble())) }
            // The validator holds a deload session to half the normal working sets (DEL-003), so the plan does too:
            // lowest priority and largest first, then the lowest-priority exercises go (the primary lift always stays).
            val target = maxOf(1, Math.floor(slots.sumOf { it.sets } * (1.0 - pre.sets_reduction_pct / 100.0) + 1e-9).toInt())
            while (slots.sumOf { it.sets } > target) {
                val k = slots.indices.filter { slots[it].sets > 1 }.maxWithOrNull(compareBy({ slots[it].priority.ordinal }, { slots[it].sets }, { it })) ?: break
                slots = slots.mapIndexed { j, s -> if (j == k) s.copy(sets = s.sets - 1) else s }
            }
            while (slots.sumOf { it.sets } > target) {
                val k = slots.indices.filter { slots[it].priority != Priority.P1 }.maxWithOrNull(compareBy({ slots[it].priority.ordinal }, { it })) ?: break
                slots = slots.filterIndexed { j, _ -> j != k }
            }
            val cond = day.conditioning.map { c ->
                if (c.zone == Zone.Z1 && !c.hiit) c else PlannedConditioning(c.modality, Zone.Z1,
                    minOf(c.workMinutes + c.restMinutes, P.RDY_004.LIGHT.z1_minutes[1].toDouble()).coerceAtLeast(P.FREQ_005.min_block_minutes.toDouble()))
            }
            day.copy(slots = slots, conditioning = cond, mobilityMinutes = maxOf(day.mobilityMinutes, 5.0), conditioningPriority = false)
        }
        val weekly = Volume.weekly(days.flatMap { day -> day.slots.map { it.exercise to it.sets.toDouble() } })
        val aerobic = days.flatMap { day -> day.conditioning.map { AerobicBlock(it.zone, it.workMinutes, day.weekday) } }
        val issues = WeekChecks.issues(days.map { it.toSession() }, days.size, i.carryOrRotationLastWeek)
        val dec = normal.decisions + issues.decisions + Decision(DecisionKind.DELOAD, listOf(RuleIds.DEL_003), ReasonKey.DELOAD_NOW,
            inputs = mapOf("block" to b.type.name), outputs = mapOf("sets" to days.sumOf { it.workingSets }, "normalSets" to normal.value.days.sumOf { it.workingSets }))
        return EngineResult(WeekPlan(days, b.type, b.loadingWeeks + 1, b.loadingWeeks, true, normal.value.coreLifts, weekly, aerobic, issues.value), dec)
    }

    private fun resolveCarryRotation(s: SlotSpec, i: WeekInput): SlotSpec =
        if (Pattern.LOADED_CARRY in i.carryOrRotationLastWeek) s.copy(role = SlotRole.ROTATION, pattern = Pattern.ROTATION)
        else s.copy(role = SlotRole.CARRY, pattern = Pattern.LOADED_CARRY)

    /** Rep range from the exercise's own range intersected with the rule range. */
    private fun clip(rule: IntRange, ex: Exercise): IntRange {
        val lo = maxOf(rule.first, ex.defaultRepRange.first)
        val hi = minOf(rule.last, maxOf(ex.defaultRepRange.last, ex.maxExtendedReps))
        return if (lo <= hi) lo..hi else ex.defaultRepRange
    }

    /** GEN-001 step 5 at plan level: reps (REP + blueprint), RIR (INT-002 within the block band), sets and rest. */
    private fun doseSlot(spec: SlotSpec, ex: Exercise, i: WeekInput, type: BlockType, blockWeek: Int, loadingWeeks: Int,
                     dose: BlockDose, deload: Boolean, light: Boolean, priority: Priority): PlannedSlot {
        val core = spec.role == SlotRole.CORE || spec.role == SlotRole.CARRY || spec.role == SlotRole.ROTATION
        val scheme = when {
            spec.role == SlotRole.POWER -> Reps.forSlot(ex, DoseGoal.POWER, i.level).value
            core -> Reps.forSlot(ex, DoseGoal.CORE, i.level).value
            else -> Reps.forSlot(ex, DoseGoal.HYPERTROPHY, i.level).value
        }
        val reps = when {
            spec.role == SlotRole.POWER || core || ex.unit != DoseUnit.REPS -> scheme.range
            // Loaded compounds follow the block's rep range (e1RM sets the load); only the top is bounded by the exercise.
            (spec.role == SlotRole.MAIN || spec.role == SlotRole.SECONDARY) && ex.dosedAsCompound && ex.trackE1rm ->
                Blueprint.mainReps(type, i.level, if (spec.role == SlotRole.MAIN) spec.exposure else Exposure.MODERATE)
                    .let { r -> r.first..minOf(r.last, ex.maxExtendedReps) }
            else -> clip(dose.accessoryReps, ex)
        }
        val compound = ex.dosedAsCompound
        var rir = when {
            spec.role == SlotRole.POWER -> 3.0
            core -> 2.0
            else -> {
                val t = Effort.targetRir(i.level, compound, i.weeksTraining, blockWeek, loadingWeeks).value
                val band = if (spec.role == SlotRole.MAIN || spec.role == SlotRole.SECONDARY) dose.mainRir else dose.accessoryRir
                val beginnerFloor = Effort.range(i.level, compound, i.weeksTraining).start
                maxOf(t.coerceIn(band.start, band.endInclusive), if (i.level == Level.BEGINNER && i.weeksTraining < 8) beginnerFloor else 0.0)
            }
        }
        if (light) rir = maxOf(rir, P.INT_002.deload_or_light_min_rir.toDouble())
        if (i.screening == ScreeningMode.CONSERVATIVE) rir = maxOf(rir, P.SAF_001.conservative_mode.min_rir.toDouble())
        var sets = when (spec.role) {
            SlotRole.POWER -> minOf(3, Reps.maxPowerSets(reps.last))
            SlotRole.MAIN -> dose.mainSetsOverride ?: if (i.level != Level.BEGINNER && spec.exposure == Exposure.HEAVY &&
                (type.isStrengthEmphasis || type == BlockType.POWER_ATHLETICISM)) 4 else 3
            SlotRole.SECONDARY -> 3
            else -> 2
        }
        val full = sets
        if (deload) sets = maxOf(1, Math.ceil(sets * (1.0 - P.DEL_003.sets_reduction_pct / 100.0)).toInt())
        return PlannedSlot(spec, ex, sets, reps, scheme.unit, ex.unilateral, rir, priority, Rest.forSets(ex, reps.last), spec.role == SlotRole.MAIN, full)
    }

    // ------------------------------------------------------------------ volume allocation

    private fun weekly(week: List<PlannedDay>) = Volume.weekly(week.flatMap { d -> d.slots.map { it.exercise to it.sets.toDouble() } })

    /** Weekly fractional-set target for a muscle this week (VOL-003 band, VOL-006 step, PER-006 bonus). */
    fun target(m: Muscle, i: WeekInput, dose: BlockDose, blockWeek: Int, deload: Boolean): Double {
        val top = i.priorities.firstOrNull()
        val bonus = Blueprint.prioritySetBonus(m in i.focusMuscles && top in setOf(Goal.MUSCLE, Goal.STRENGTH, Goal.BODY_COMPOSITION))
        val step = i.level.pick(P.VOL_006.step.beginner, P.VOL_006.step.intermediate, P.VOL_006.step.advanced)[0]
        val t = minOf(dose.setsStart + bonus + (blockWeek - 1) * step, maxOf(dose.setsTop, dose.setsStart + bonus))
        val capped = minOf(t, Volume.weeklyCap(i.level), Caps.weeklySetsPerMuscle(i.level)).toDouble()
        return if (deload) capped * (1.0 - P.DEL_003.sets_reduction_pct / 100.0) else capped
    }

    private fun allocate(input: List<PlannedDay>, i: WeekInput, dose: BlockDose, blockWeek: Int, deload: Boolean): EngineResult<List<PlannedDay>> {
        var week = input
        val weeklyCap = minOf(Volume.weeklyCap(i.level), Caps.weeklySetsPerMuscle(i.level)).toDouble()
        val directCap = Caps.directSetsPerMuscleSession(i.level)
        val sessionCap = minOf(Volume.sessionSetCap(i.level), Caps.workingSetsPerSession(i.level))
        fun setSets(dayIdx: Int, slotIdx: Int, n: Int) {
            week = week.mapIndexed { di, day -> if (di != dayIdx) day else day.copy(slots = day.slots.mapIndexed { si, s -> if (si == slotIdx) s.copy(sets = n) else s }) }
        }
        val adjustable = setOf(SlotRole.ACCESSORY, SlotRole.SECONDARY)
        // 1) Never above the weekly cap: trim accessories, then secondaries, largest first (min 1 set).
        for (m in Muscle.entries) {
            var guard = 0
            while ((weekly(week)[m] ?: 0.0) > weeklyCap + 1e-9 && guard++ < 200) {
                val cand = week.flatMapIndexed { di, day -> day.slots.mapIndexedNotNull { si, s ->
                    if (s.spec.role in adjustable && s.sets > 1 && (m in s.exercise.primary || m in s.exercise.secondary)) Triple(di, si, s) else null } }
                    .sortedWith(compareBy<Triple<Int, Int, PlannedSlot>>({ if (it.third.spec.role == SlotRole.ACCESSORY) 0 else 1 }, { -it.third.sets }, { it.first }, { it.second }))
                    .firstOrNull() ?: break
                setSets(cand.first, cand.second, cand.third.sets - 1)
            }
        }
        // 2) Grow toward the target where it fits every cap; spread sets, accessories first (never in a deload or review week).
        val maxSets = if (deload) 2 else 4
        var progress = !deload
        var guard = 0
        while (progress && guard++ < 400) {
            progress = false
            val now = weekly(week)
            val needy = TARGET_MUSCLES.map { it to target(it, i, dose, blockWeek, deload) - (now[it] ?: 0.0) }
                .filter { it.second >= 1.0 - 1e-9 }.sortedWith(compareByDescending<Pair<Muscle, Double>> { it.second }.thenBy { it.first.ordinal })
            for ((m, _) in needy) {
                val options = week.flatMapIndexed { di, day -> day.slots.mapIndexedNotNull { si, s ->
                    if (s.spec.role in adjustable && s.sets < maxSets && m in s.exercise.primary) Triple(di, si, s) else null } }
                    .sortedWith(compareBy<Triple<Int, Int, PlannedSlot>>({ if (it.third.spec.role == SlotRole.ACCESSORY) 0 else 1 }, { it.third.sets }, { it.first }, { it.second }))
                for ((di, si, s) in options) {
                    val day = week[di]
                    if (day.workingSets + 1 > sessionCap) continue
                    if (!fits(day.copy(slots = day.slots.mapIndexed { k, x -> if (k == si) x.copy(sets = x.sets + 1) else x }), i)) continue
                    val direct = Volume.directPerSession(day.slots.map { it.exercise to it.sets.toDouble() })
                    if (s.exercise.primary.any { (direct[it] ?: 0.0) + 1 > directCap + 1e-9 }) continue
                    val credit = Volume.credit(s.exercise, 1.0)
                    if (credit.any { (k, v) -> (now[k] ?: 0.0) + v > weeklyCap + 1e-9 }) continue
                    setSets(di, si, s.sets + 1); progress = true
                    break
                }
                if (progress) break
            }
        }
        // CORE-003 minimum: reach 6 core sets a week (≤ 4 per session, ≤ 3 per exercise), making room on that
        // day by taking a set from its largest accessory or secondary when the session would run long.
        val isCore: (PlannedSlot) -> Boolean = { it.spec.role == SlotRole.CORE || it.spec.role == SlotRole.ROTATION }
        var coreGuard = 0
        while (!deload && week.sumOf { d -> d.slots.filter(isCore).sumOf { it.sets } } < P.CORE_003.weekly_sets[0] && coreGuard++ < 20) {
            val cand = week.flatMapIndexed { di, day -> day.slots.mapIndexedNotNull { si, s ->
                if (isCore(s) && s.sets < 3 && day.slots.filter(isCore).sumOf { it.sets } < P.CORE_003.max_sets_per_session) Triple(di, si, s) else null } }
                .minWithOrNull(compareBy<Triple<Int, Int, PlannedSlot>>({ it.third.sets }, { it.first }, { it.second })) ?: break
            setSets(cand.first, cand.second, cand.third.sets + 1)
            val day = week[cand.first]
            if (!fits(day, i)) {
                val room = day.slots.withIndex().filter { (it.value.spec.role in adjustable) && it.value.sets > 1 }
                    .maxWithOrNull(compareBy({ if (it.value.spec.role == SlotRole.ACCESSORY) 1 else 0 }, { it.value.sets }, { -it.index }))
                if (room != null) setSets(cand.first, room.index, room.value.sets - 1)
            }
        }
        // 3) PAT-002 (pull:push 1.0–1.5) and PAT-003 (knee:hip 0.67–1.5): add sets to the short side where time and caps allow,
        //    otherwise trim the long side's adjustable sets (never below 1).
        fun ratioFix(isNum: (Exercise) -> Boolean, isDen: (Exercise) -> Boolean, min: Double, max: Double) {
            var g = 0
            while (g++ < 60) {
                val all = week.flatMap { it.slots }
                val num = all.filter { isNum(it.exercise) }.sumOf { it.sets }
                val den = all.filter { isDen(it.exercise) }.sumOf { it.sets }
                if (num == 0 || den == 0) break
                val r = num.toDouble() / den
                val (grow, shrink) = when {
                    r < min - 1e-9 -> isNum to isDen
                    r > max + 1e-9 -> isDen to isNum
                    else -> break
                }
                val add = week.flatMapIndexed { di, day -> day.slots.mapIndexedNotNull { si, s ->
                    if (grow(s.exercise) && s.spec.role in adjustable && s.sets < maxSets && day.workingSets < sessionCap &&
                        fits(day.copy(slots = day.slots.mapIndexed { k, x -> if (k == si) x.copy(sets = x.sets + 1) else x }), i) &&
                        Volume.credit(s.exercise, 1.0).all { (k, v) -> (weekly(week)[k] ?: 0.0) + v <= weeklyCap + 1e-9 }) Triple(di, si, s) else null } }
                    .minWithOrNull(compareBy<Triple<Int, Int, PlannedSlot>>({ it.third.sets }, { it.first }, { it.second }))
                if (add != null) { setSets(add.first, add.second, add.third.sets + 1); continue }
                val cut = week.flatMapIndexed { di, day -> day.slots.mapIndexedNotNull { si, s ->
                    if (shrink(s.exercise) && (s.spec.role in adjustable || s.spec.role == SlotRole.POWER) && s.sets > 1) Triple(di, si, s) else null } }
                    .maxWithOrNull(compareBy<Triple<Int, Int, PlannedSlot>>({ it.third.sets }, { -it.first }, { -it.second })) ?: break
                setSets(cut.first, cut.second, cut.third.sets - 1)
            }
        }
        ratioFix({ it.pattern.isPull }, { it.pattern.isPush }, P.PAT_002.min, P.PAT_002.max)
        ratioFix({ it.pattern.isKneeDominant }, { it.pattern.isHipDominant }, P.PAT_003.min, P.PAT_003.max)
        // 4) CORE-003: ≤ 4 core sets per session and 6–12 a week.
        week = week.map { day ->
            var coreSets = 0
            day.copy(slots = day.slots.map { s ->
                if (s.spec.role != SlotRole.CORE && s.spec.role != SlotRole.ROTATION) s else {
                    val n = minOf(s.sets, P.CORE_003.max_sets_per_session - coreSets).coerceAtLeast(1)
                    coreSets += n; s.copy(sets = n)
                }
            })
        }
        val after = weekly(week)
        val short = TARGET_MUSCLES.filter { (after[it] ?: 0.0) < target(it, i, dose, blockWeek, deload) - 1e-9 }
        return EngineResult(week, listOf(Decision(DecisionKind.VOLUME_CHANGE, listOf(RuleIds.VOL_003, RuleIds.VOL_005, RuleIds.VOL_006, RuleIds.VOL_008,
            RuleIds.PER_006, RuleIds.PAT_002, RuleIds.CORE_003), ReasonKey.VOLUME_ALLOCATED,
            inputs = mapOf("blockWeek" to blockWeek, "deload" to deload),
            outputs = mapOf("sets" to TARGET_MUSCLES.associate { it.name to Num.round1(after[it] ?: 0.0) }, "belowTarget" to short.map { it.name }))))
    }

    // ------------------------------------------------------------------ session length

    /** Planned minutes of a day with supersets paired, as the time fit would pair them (TIME-004, ORD-003). */
    fun minutes(day: PlannedDay, i: WeekInput): Double {
        if (day.slots.isEmpty() && day.conditioning.isEmpty()) return 0.0
        val items = TimeBudget.pairSupersets(planItems(day), i.crowded)
        return com.personalfitnesscoach.engine.planning.TimeModel.minutes(SessionPlan(warmupMinutes(i), P.TIME_001.cooldown_min_minutes.toDouble(), items,
            coreMobilityMin = day.mobilityMinutes))
    }

    private fun fits(day: PlannedDay, i: WeekInput): Boolean = minutes(day, i) <= i.sessionMinutes + 1e-9

    /**
     * Shapes a planned session to the usual session length while keeping weekly coverage (D-051):
     * accessories and extra sets go first, every pattern keeps at least one set, core, carry and
     * rotation slots are never dropped here. Anything still too long is left to TIME-002.
     */
    private fun trimToTime(day: PlannedDay, i: WeekInput, d: MutableList<Decision>): PlannedDay {
        if (fits(day, i)) return day
        var cur = day
        val steps = ArrayList<String>()
        fun slotsWhere(pred: (PlannedSlot) -> Boolean) = cur.slots.withIndex().filter { pred(it.value) }
        fun reduce(label: String, pred: (PlannedSlot) -> Boolean, floor: Int): Boolean {
            while (!fits(cur, i)) {
                val c = slotsWhere { pred(it) && it.sets > floor }.maxWithOrNull(compareBy({ it.value.sets }, { it.index })) ?: return false
                cur = cur.copy(slots = cur.slots.mapIndexed { k, s -> if (k == c.index) s.copy(sets = s.sets - 1) else s })
                steps += "$label:${c.value.exercise.id}"
            }
            return true
        }
        val iso: (PlannedSlot) -> Boolean = { it.spec.role == SlotRole.ACCESSORY && it.spec.pattern == Pattern.ISOLATION }
        val acc: (PlannedSlot) -> Boolean = { it.spec.role == SlotRole.ACCESSORY }
        val sec: (PlannedSlot) -> Boolean = { it.spec.role == SlotRole.SECONDARY || it.spec.role == SlotRole.POWER }
        val core: (PlannedSlot) -> Boolean = { it.spec.role == SlotRole.CORE || it.spec.role == SlotRole.CARRY || it.spec.role == SlotRole.ROTATION }
        val mainNotP1: (PlannedSlot) -> Boolean = { it.spec.role == SlotRole.MAIN && it.priority != Priority.P1 }
        val done = reduce("acc_to_2", acc, 2) || reduce("sec_to_3", sec, 3) || reduce("main_to_3", { it.spec.role == SlotRole.MAIN }, 3) ||
            run {
                // Post-strength Z1 to its 10-minute minimum (FREQ-005).
                cur = cur.copy(conditioning = cur.conditioning.map { c ->
                    if (c.zone == Zone.Z1 && c.interval == null && cur.slots.isNotEmpty()) c.copy(workMinutes = minOf(c.workMinutes, P.FREQ_005.min_block_minutes.toDouble())) else c })
                fits(cur, i)
            } || reduce("iso_to_1", iso, 1) || reduce("sec_to_2", sec, 2) ||
            run {
                while (!fits(cur, i)) {
                    val last = slotsWhere(iso).lastOrNull() ?: break
                    steps += "iso_drop:${last.value.exercise.id}"
                    cur = cur.copy(slots = cur.slots.filterIndexed { k, _ -> k != last.index })
                }
                fits(cur, i)
            } || reduce("core_to_1", core, 1) || reduce("main_to_2", mainNotP1, 2) || reduce("acc_to_1", acc, 1) || reduce("sec_to_1", sec, 1)
        if (steps.isNotEmpty() || !done) d += Decision(DecisionKind.TIME_FIT, listOf(RuleIds.TIME_001, RuleIds.TIME_004, RuleIds.PAT_001), ReasonKey.TIME_COMPRESSED,
            inputs = mapOf("weekday" to day.weekday, "minutes" to i.sessionMinutes), outputs = mapOf("steps" to steps, "fits" to done, "planned" to Num.round1(minutes(cur, i))))
        return cur
    }

    // ------------------------------------------------------------------ conditioning

    private fun conditioning(input: List<PlannedDay>, i: WeekInput, type: BlockType, blockWeek: Int, deload: Boolean, light: Boolean): EngineResult<List<PlannedDay>> {
        val d = ArrayList<Decision>()
        val week = input.toMutableList()
        val heavyDays = week.filter { it.heavyLower }.map { it.weekday }.toSet()
        val powerDays = week.filter { day -> day.slots.any { it.power } }.map { it.weekday }.toSet()
        fun nextDayHeavyOrPower(wd: Int) = ((wd + 1) % 7) in heavyDays || ((wd + 1) % 7) in powerDays
        val jointSensitivity = if (i.injuries.isNotEmpty() || (i.age ?: 0) >= 50) 0.8 else 0.4
        val recent = ArrayList<Modality>()
        var impactUsed = 0
        fun pick(day: PlannedDay, purpose: ConditioningPurpose): Modality? {
            val legs = if (day.heavyLower || nextDayHeavyOrPower(day.weekday)) 1.0 else 0.3
            val impactOk = impactUsed < P.CON_004.impact_sessions_per_week_max && !nextDayHeavyOrPower(day.weekday)
            val r = ModalitySelection.rank(ModalityContext(i.equipment, purpose, legs, jointSensitivity, recent.toList(), i.modalityPreferences, i.jointLimits, impactOk))
            val m = r.value.firstOrNull()?.modality ?: return null
            recent += m
            if (ModalitySelection.isImpact(m)) impactUsed++
            return m
        }
        // HIIT count: blueprint quota within HIIT-001/AGE-001/SAF-001/DEL-003 caps and only with a Z1 base (HIIT-003).
        val quota = Blueprint.hiitPerWeek(type, i.level, blockWeek, week.size)
        val cap = Caps.hiitPerWeek(HiitContext(i.level, Tier.FULL, 0, false, type == BlockType.CONDITIONING, i.screening, i.age, deload)).value
        val hiitCount = if (light || !i.hiitBaseReady || i.screening != ScreeningMode.STANDARD) 0 else minOf(quota, cap, P.HIIT_001.default_max)
        // Z1 block length after strength: AER-003 duration progression, 10–20 min, a third of the session at most.
        val z1Next = Aerobic.nextZ1Minutes(i.z1SessionMinutes, i.lastWeekAerobicMinutes, 0.0, maxOf(1, week.size)).value
        val z1Post = z1Next.coerceIn(P.FREQ_005.min_block_minutes.toDouble(), maxOf(P.FREQ_005.min_block_minutes.toDouble(), minOf(20.0, i.sessionMinutes / 6.0)))
        // HIIT days: conditioning days first, then upper days, then full-body, then lower; never the day before heavy legs or power (CON-003/005).
        fun rank(t: DayTemplate) = when (t) {
            DayTemplate.COND, DayTemplate.COND_CORE -> 0; DayTemplate.UPPER_H, DayTemplate.UPPER_M -> 1
            DayTemplate.FB_C -> 2; DayTemplate.FB_A, DayTemplate.FB_B -> 3; else -> 4
        }
        val hiitDays = ArrayList<Int>()
        repeat(hiitCount) {
            val c = week.filter { it.template != DayTemplate.EASY_AEROBIC_MOBILITY && it.weekday !in hiitDays && !nextDayHeavyOrPower(it.weekday) &&
                hiitDays.all { h -> Math.abs(h - it.weekday) >= P.HIIT_004.min_hours / 24 && 7 - Math.abs(h - it.weekday) >= P.HIIT_004.min_hours / 24 } }
                .sortedWith(compareBy<PlannedDay>({ rank(it.template) }, { day -> if (hiitDays.any { h -> Math.abs(h - day.weekday) < P.HIIT_004.preferred_hours / 24 }) 1 else 0 }, { it.weekday }))
                .firstOrNull() ?: return@repeat
            hiitDays += c.weekday
        }
        val protocol = HiitMenu.choose(type.kind, i.level, i.hiitDoneEver, 0, blockWeek)
        if (hiitDays.isNotEmpty()) d += protocol.decisions
        var interval = HiitMenu.start(protocol.value, i.level, i.hiitDoneEver == 0)
        repeat(i.hiitExposures) { interval = HiitMenu.progress(interval) }
        val capMin = HiitMenu.workCapMinutes(protocol.value)
        while (interval.workMinutes > capMin + 1e-9 && interval.reps > 1) interval = interval.copy(reps = interval.reps - 1)
        // Tempo (Z2) once a week in build/conditioning/consolidation blocks once the Z1 base exists (AER-003).
        val dose = Blueprint.dose(type, i.level)
        var tempoLeft = if (light || !i.hiitBaseReady || i.screening == ScreeningMode.CONSERVATIVE) 0 else dose.tempoPerWeek
        for ((idx, day) in week.withIndex()) {
            val blocks = ArrayList<PlannedConditioning>()
            var conditioningPriority = false
            when {
                day.weekday in hiitDays -> {
                    val m = pick(day, ConditioningPurpose.INTERVALS)
                    if (m != null) {
                        val z = if (interval.protocol == HiitProtocol.SPRINT) Zone.Z4 else Zone.Z3
                        blocks += PlannedConditioning(m, z, interval.workMinutes, interval.reps * interval.restSec / 60.0, interval, true,
                            if (ModalitySelection.isImpact(m)) 1 else 0, ConditioningPurpose.INTERVALS)
                        conditioningPriority = !day.template.strength
                    }
                }
                day.template == DayTemplate.EASY_AEROBIC_MOBILITY -> pick(day, ConditioningPurpose.STEADY)?.let {
                    blocks += PlannedConditioning(it, Zone.Z1, P.AER_003.z1_target_minutes[0].toDouble() + 5.0)
                }
                !day.template.strength && tempoLeft > 0 -> pick(day, ConditioningPurpose.STEADY)?.let {
                    val b = HiitMenu.band(null)
                    blocks += PlannedConditioning(it, Zone.Z2, b.reps.first * b.work.first / 60.0, b.reps.first * b.rest.first / 60.0,
                        Interval(null, b.reps.first, b.work.first, b.rest.first, b.cr10))
                    tempoLeft--; conditioningPriority = true
                }
                !day.template.strength -> pick(day, ConditioningPurpose.STEADY)?.let { blocks += PlannedConditioning(it, Zone.Z1, 30.0) ; conditioningPriority = true }
                tempoLeft > 0 && !day.heavyLower && (day.template == DayTemplate.UPPER_H || day.template == DayTemplate.UPPER_M || day.template == DayTemplate.FB_C) ->
                    pick(day, ConditioningPurpose.STEADY)?.let {
                        val b = HiitMenu.band(null)
                        blocks += PlannedConditioning(it, Zone.Z2, b.reps.first * b.work.first / 60.0, b.reps.first * b.rest.first / 60.0,
                            Interval(null, b.reps.first, b.work.first, b.rest.first, b.cr10))
                        tempoLeft--
                    }
                else -> pick(day, ConditioningPurpose.STEADY)?.let { blocks += PlannedConditioning(it, Zone.Z1, z1Post) }
            }
            val mobility = if (day.template == DayTemplate.EASY_AEROBIC_MOBILITY) P.MOB_005.minutes[0].toDouble() + 5.0 else day.mobilityMinutes
            week[idx] = day.copy(conditioning = blocks, mobilityMinutes = mobility, conditioningPriority = conditioningPriority)
        }
        // AER-002: keep ≥ 75% of aerobic minutes in Z1 — turn tempo back into Z1 if the share falls short.
        fun blocks() = week.flatMap { day -> day.conditioning.map { AerobicBlock(it.zone, it.workMinutes, day.weekday) } }
        if (!Aerobic.distributionOk(blocks())) {
            for ((idx, day) in week.withIndex()) week[idx] = day.copy(conditioning = day.conditioning.map {
                if (it.zone == Zone.Z2 && !it.hiit) PlannedConditioning(it.modality, Zone.Z1, it.workMinutes + it.restMinutes) else it })
        }
        d += Decision(DecisionKind.VOLUME_CHANGE, listOf(RuleIds.AER_002, RuleIds.AER_003, RuleIds.HIIT_001, RuleIds.HIIT_002, RuleIds.HIIT_003,
            RuleIds.CON_003, RuleIds.CON_004, RuleIds.FREQ_005, RuleIds.MOD_002), ReasonKey.CONDITIONING_PLANNED,
            inputs = mapOf("block" to type.name, "hiitQuota" to quota, "hiitCap" to cap, "baseReady" to i.hiitBaseReady),
            outputs = mapOf("hiitDays" to hiitDays, "blocks" to week.flatMap { day -> day.conditioning.map { "${day.weekday}:${it.modality}:${it.zone}:${Num.round1(it.workMinutes)}" } }))
        return EngineResult(week, d)
    }

    // ------------------------------------------------------------------ time pre-fit

    /** Planned session items for the time model (TIME-001 priorities; ORD-003 pairing classes). */
    fun planItems(day: PlannedDay): List<PlanItem> =
        day.slots.mapIndexed { idx, s ->
            val tempo = s.exercise.tempoSecPerRep * (if (s.perSide) 2 else 1)
            PlanItem("$idx:${s.exercise.id}", s.priority, s.sets, s.reps.last, s.rest.defaultSec, s.rest.minSec, s.exercise.setupSec, tempo,
                s.exercise.stationKey, s.exercise.primary, supersetEligible = Order.supersetEligible(s.main, s.targetRir) && s.priority != Priority.P1,
                pairClass = Order.pairClass(s.exercise))
        } + day.conditioning.mapIndexed { idx, c ->
            val cond = c.interval?.let { Conditioning(it.workSec, it.restSec, it.reps) } ?: Conditioning(Math.round(c.workMinutes * 60).toInt(), 0, 1)
            PlanItem("c$idx:${c.modality}", Priority.P2, station = c.modality.name.lowercase(), setupSec = 30, conditioning = cond)
        }

    fun warmupMinutes(i: WeekInput): Double = Warmup.minutes(10.0, compressed = false, age = i.age).value

    private fun fitDay(day: PlannedDay, i: WeekInput, d: MutableList<Decision>): PlannedDay {
        if (day.slots.isEmpty() && day.conditioning.isEmpty()) return day
        val plan = SessionPlan(warmupMin = warmupMinutes(i), cooldownMin = P.TIME_001.cooldown_min_minutes.toDouble(), items = planItems(day),
            coreMobilityMin = day.mobilityMinutes)
        val fit = TimeBudget.fit(plan, i.sessionMinutes.toDouble(), i.age, crowded = i.crowded)
        if (fit.decisions.isNotEmpty()) d += fit.decisions
        val byId = fit.value.plan.items.associateBy { it.id }
        val slots = day.slots.mapIndexedNotNull { idx, s -> byId["$idx:${s.exercise.id}"]?.let { s.copy(sets = it.sets) } }
        val cond = day.conditioning.mapIndexedNotNull { idx, c ->
            val item = byId["c$idx:${c.modality}"] ?: return@mapIndexedNotNull null
            val k = item.conditioning ?: return@mapIndexedNotNull c
            if (c.interval != null) c.copy(interval = c.interval.copy(reps = k.rounds), workMinutes = k.rounds * k.workSec / 60.0, restMinutes = k.rounds * k.restSec / 60.0)
            else c.copy(workMinutes = k.workSec / 60.0)
        }
        return day.copy(slots = slots, conditioning = cond)
    }

    // ------------------------------------------------------------------ SCH-003

    /** SCH-003: training on an unplanned day runs the next session in sequence, whatever weekday it was planned for. */
    fun nextSession(plan: WeekPlan, completedWeekdays: Set<Int>): PlannedDay? =
        plan.days.firstOrNull { it.weekday !in completedWeekdays && (it.slots.isNotEmpty() || it.conditioning.isNotEmpty()) }

    /**
     * SCH-003: "only N days this week" re-plans the week for N days — main patterns first (each
     * template starts with them), PAT-001 coverage kept by the template, HIIT within its cap.
     */
    fun replanReduced(i: WeekInput, daysLeft: Int, availableDays: Set<Int>, program: Program? = null): EngineResult<WeekPlan> {
        val n = daysLeft.coerceIn(P.FREQ_001.min_days, P.FREQ_001.max_days)
        val r = plan(i.copy(daysPerWeek = n, availableDays = availableDays), program)
        return r + listOf(Decision(DecisionKind.VOLUME_CHANGE, listOf(RuleIds.SCH_003), ReasonKey.WEEK_DAYS_REDUCED,
            inputs = mapOf("daysLeft" to daysLeft), outputs = mapOf("days" to r.value.days.map { it.weekday })))
    }
}
