package com.personalfitnesscoach.data.core.engine

import com.personalfitnesscoach.data.core.model.ConditionsRecord
import com.personalfitnesscoach.data.core.model.EquipmentRecord
import com.personalfitnesscoach.data.core.model.ExerciseState
import com.personalfitnesscoach.data.core.model.KnownNumber
import com.personalfitnesscoach.data.core.model.PainRecord
import com.personalfitnesscoach.data.core.model.PreferencesRecord
import com.personalfitnesscoach.data.core.model.Profile
import com.personalfitnesscoach.data.core.model.ProgramRecord
import com.personalfitnesscoach.data.core.model.ReadinessRecord
import com.personalfitnesscoach.data.core.model.RecoveryRecord
import com.personalfitnesscoach.data.core.model.ScreeningRecord
import com.personalfitnesscoach.data.core.model.WaistRecord
import com.personalfitnesscoach.data.core.model.WalkRecord
import com.personalfitnesscoach.data.core.model.WeekPlanRecord
import com.personalfitnesscoach.data.core.model.WeekSummary
import com.personalfitnesscoach.data.core.model.WeightRecord
import com.personalfitnesscoach.data.core.model.dayKey
import com.personalfitnesscoach.data.core.repo.Docs
import com.personalfitnesscoach.data.core.session.SessionLog
import com.personalfitnesscoach.data.core.session.StoredWorkout
import com.personalfitnesscoach.data.core.time.AppClock
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.engine.calc.Baseline
import com.personalfitnesscoach.engine.calc.E1rm
import com.personalfitnesscoach.engine.calc.FatigueInputs
import com.personalfitnesscoach.engine.calc.FatigueSignals
import com.personalfitnesscoach.engine.calc.LiftTrend
import com.personalfitnesscoach.engine.calc.Signal
import com.personalfitnesscoach.engine.calc.Ssu
import com.personalfitnesscoach.engine.calc.Volume
import com.personalfitnesscoach.engine.calc.Workload
import com.personalfitnesscoach.engine.conditioning.AerobicBlock
import com.personalfitnesscoach.engine.conditioning.Aerobic
import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.generation.GenerationRequest
import com.personalfitnesscoach.engine.generation.SessionGenerator
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Muscle
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.model.Zone
import com.personalfitnesscoach.engine.program.Blueprint
import com.personalfitnesscoach.engine.program.Individual
import com.personalfitnesscoach.engine.program.PlannedDay
import com.personalfitnesscoach.engine.program.Program
import com.personalfitnesscoach.engine.program.WeekContext
import com.personalfitnesscoach.engine.program.WeekInput
import com.personalfitnesscoach.engine.program.WeekKind
import com.personalfitnesscoach.engine.program.WeekPlan
import com.personalfitnesscoach.engine.program.WeekPlanner
import com.personalfitnesscoach.engine.progress.BodyProgress
import com.personalfitnesscoach.engine.progress.StrengthPoint
import com.personalfitnesscoach.engine.progress.StrengthTrend
import com.personalfitnesscoach.engine.progress.WaistEntry
import com.personalfitnesscoach.engine.progress.WeightEntry
import com.personalfitnesscoach.engine.progress.WeightTrend
import com.personalfitnesscoach.engine.progression.KnownStart
import com.personalfitnesscoach.engine.progression.Prescription
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.safety.ConditionLimits
import com.personalfitnesscoach.engine.safety.Conditions
import com.personalfitnesscoach.engine.safety.PainAction
import com.personalfitnesscoach.engine.safety.PainGate
import com.personalfitnesscoach.engine.safety.PainKind
import com.personalfitnesscoach.engine.safety.RegionConstraint
import com.personalfitnesscoach.engine.safety.RegionHistory
import com.personalfitnesscoach.engine.safety.Screening
import com.personalfitnesscoach.engine.safety.ScreeningMode
import com.personalfitnesscoach.engine.safety.SessionValidator
import com.personalfitnesscoach.engine.safety.ValidationContext

/** The user as the engine sees them on one day: everything resolved from storage (profile, screening, conditions, injuries). */
data class UserState(
    val today: Int,
    val profile: Profile,
    val age: Int?,
    val screening: ScreeningMode,
    val equipment: EquipmentRecord,
    val preferences: PreferencesRecord,
    val conditions: ConditionLimits,
    val injuries: Individual.InjuryPlan,
    val decisions: List<Decision>,
) {
    /** SAF-001 conservative mode, also while a condition's doctor's OK is outstanding. */
    val effectiveScreening: ScreeningMode get() = if (conditions.conservative) ScreeningMode.CONSERVATIVE else screening
    val flaggedScreen: Boolean get() = effectiveScreening != ScreeningMode.STANDARD
}

/** History → engine inputs (FS-5): the maps the session generator reads. */
data class HistoryMaps(
    val e1rm: Map<String, Double>,
    val progression: Map<String, Prescription>,
    val calibrationLoads: Map<String, Double>,
    val calibrationCeilings: Map<String, Double>,
    val lastWeekLoads: Map<String, Double>,
    val bodyweightKg: Double?,
    val decisions: List<Decision> = emptyList(),
)

/** Totals of a span of days, from finished workouts and logged walks. */
data class Totals(
    val completed: Int,
    val aerobicMinutes: Double,
    val z1Minutes: Double,
    val z2PlusMinutes: Double,
    val hiitSessions: Int,
    val hiitWorkMinutes: Double,
    val walkingMinutes: Double,
    val equivalentMinutes: Double,
    val ssu: Double,
    val workload: Double,
    val sets: Map<Muscle, Double>,
    val longestZ1Block: Double,
    val carryOrRotation: Set<Pattern>,
    val sprints: Int,
    val impactSessions: Int,
)

/** Body progress for the progress screen (FL-004/005). */
data class BodyView(val weight: WeightTrend, val waistCm: Double?, val waistDay: Int?, val waistDue: Boolean, val strength: StrengthTrend)

/**
 * The bridge between what is stored and what the engine reads (Phase 2 section 8, "repositories feeding the engine"). Every method
 * reads storage and calls engine rules; nothing here makes a training decision of its own.
 */
class EngineBridge(private val docs: Docs, private val log: SessionLog, private val clock: AppClock) {

    // ------------------------------------------------------------------------------------------------ the user
    suspend fun user(today: Int = clock.today()): UserState? {
        val profile = docs.get(Profile) ?: return null
        val d = ArrayList<Decision>()
        val screening = docs.get(ScreeningRecord)?.let { s ->
            Screening.evaluate(s.answers, clearanceConfirmed = s.clearanceConfirmedDay != null).also { d += it.decisions }.value.mode
        } ?: ScreeningMode.CONSERVATIVE // not screened yet: fail closed
        val conditions = conditions(today).also { d += it.decisions }.value
        val program = docs.get(ProgramRecord)
        val injuries = Individual.injuries(profile.priorInjuries, weeksSince(program?.startDay ?: profile.createdDay, today)).also { d += it.decisions }.value
        return UserState(today, profile, profile.age(today), screening, docs.get(EquipmentRecord) ?: EquipmentRecord(emptySet()),
            docs.get(PreferencesRecord) ?: PreferencesRecord(), conditions, injuries, d)
    }

    /** SAF-010 limits for `today`: each picked condition with its time-based facts as of today, merged "strictest wins". */
    suspend fun conditions(today: Int = clock.today()): EngineResult<ConditionLimits> {
        val rec = docs.get(ConditionsRecord) ?: return EngineResult(ConditionLimits.NONE)
        if (rec.items.isEmpty()) return EngineResult(ConditionLimits.NONE)
        val weeksWithSessions = doneWeekStarts(rec.items.minOf { it.addedDay }, today)
        return Conditions.resolve(rec.items.map { c -> c.toUserCondition(today, weeksWithSessions.count { it >= Days.weekStart(c.addedDay) }) })
    }

    /** Monday dates of weeks in `fromDay..toDay` with at least one finished workout. */
    private suspend fun doneWeekStarts(fromDay: Int, toDay: Int): Set<Int> = log.done(fromDay, toDay).map { Days.weekStart(it.day) }.toSet()

    private fun weeksSince(day: Int, today: Int) = maxOf(0, today - day) / 7

    // ------------------------------------------------------------------------------------------------ history → engine
    /**
     * The generator's history maps (FS-5, D-055, CAL-001/002, PROG-007). `day` is the session being generated: the user's own
     * numbers turn into e1RM seeds or calibration ceilings for its slots.
     */
    suspend fun history(u: UserState, day: PlannedDay? = null): HistoryMaps {
        val today = u.today
        val states = docs.all(ExerciseState).associateBy { it.exerciseId }
        val e1rm = HashMap<String, Double>()
        val prog = HashMap<String, Prescription>()
        val cal = HashMap<String, Double>()
        val ceilings = HashMap<String, Double>()
        for (s in states.values) {
            s.e1rm?.let { e1rm[s.exerciseId] = it }
            s.prescription?.let { prog[s.exerciseId] = it }
            if (s.e1rm == null) s.calibration?.let { c -> cal[s.exerciseId] = c.nextLoad; c.ceiling?.let { ceilings[s.exerciseId] = it } }
        }
        // CAL-002: a number not yet used becomes an e1RM seed (recent) or a ramp ceiling (old) for the slot it fills today.
        val d = ArrayList<Decision>()
        val ctx = progressContext(u, recoveryOk = true)
        val slots = day?.slots?.associateBy { it.exercise.id } ?: emptyMap()
        for (k in docs.all(KnownNumber)) {
            val s = states[k.exerciseId]
            if (s != null && (s.knownApplied || s.e1rm != null)) continue
            val ex = Library[k.exerciseId] ?: continue
            val slot = slots[k.exerciseId]
            val reps = slot?.reps?.let { ExerciseProgress.calibrationReps(it) } ?: ExerciseProgress.calibrationReps(ex.defaultRepRange)
            val rir = slot?.targetRir ?: 2.0
            val r = ExerciseProgress.known(k, ex, today, ctx, reps, rir)
            d += r.decisions
            when (val v = r.value) {
                is KnownStart.Estimate -> e1rm.putIfAbsent(k.exerciseId, v.seedE1rm)
                is KnownStart.Ceiling -> ceilings[k.exerciseId] = minOf(v.maxLoad, ceilings[k.exerciseId] ?: Double.MAX_VALUE)
                KnownStart.Invalid -> Unit
            }
        }
        return HistoryMaps(e1rm, prog, cal, ceilings, lastWeekLoads(today), latestWeight(), d)
    }

    /**
     * PROG-007 / SAF-005 reference loads: for each slot and exercise, the heaviest full-load working set of its latest exposure
     * before this week (8 weeks back at most). A calibration exposure contributes the heaviest ramp load rated RIR 3 or more —
     * the load the ramp found, never one that was too hard.
     */
    suspend fun lastWeekLoads(today: Int): Map<String, Double> {
        val weekStart = Days.weekStart(today)
        val out = HashMap<String, Double>()
        for (w in log.done(weekStart - 56, weekStart - 1).asReversed()) {
            for (e in w.exercises) {
                val key = SessionGenerator.loadKey(e.row.slotKey, e.exerciseId)
                if (key in out) continue
                val load = if (e.doc.calibrating) e.calibration.filter { (it.rir ?: -1.0) >= 3.0 }.mapNotNull { it.loadKg }.maxOrNull()
                    else if (e.doc.loadFactor >= 1.0 - 1e-9) e.working.mapNotNull { it.loadKg }.maxOrNull() else null
                if (load != null && load > 0) out[key] = load
            }
        }
        return out
    }

    suspend fun latestWeight(): Double? = docs.all(WeightRecord).maxByOrNull { it.day }?.kg

    fun progressContext(u: UserState, recoveryOk: Boolean) =
        ProgressContext(u.equipment.inventory, u.profile.level, u.age, u.flaggedScreen, recoveryOk)

    // ------------------------------------------------------------------------------------------------ pain (SAF-003/004)
    /** SAF-004 persistent-pain regions from the last 90 days of joint/tendon reports. */
    suspend fun regions(today: Int): EngineResult<List<RegionConstraint>> {
        val reports = docs.between(PainRecord, today - 90, today).filter { it.report.kind == PainKind.JOINT_OR_TENDON && it.report.rating >= 1 }
        if (reports.isEmpty()) return EngineResult(emptyList())
        val done = log.done(today - 90, today)
        val out = ArrayList<RegionConstraint>()
        val d = ArrayList<Decision>()
        for ((region, rs) in reports.groupBy { it.report.region }.toSortedMap()) {
            val sessions = rs.map { it.workoutId ?: -it.day.toLong() }.toSet().size
            val last = rs.maxOf { it.atMs }
            val after = done.filter { (it.row.startedAtMs ?: Days.startMs(it.day, clock.zone())) > last }
            val painFree = after.size
            val exitDay = if (painFree >= P.SAF_004.pain_free_sessions_to_exit) after[P.SAF_004.pain_free_sessions_to_exit - 1].day else null
            val h = RegionHistory(region, sessions, rs.maxOf { it.day } - rs.minOf { it.day }, painFree,
                weeksSinceExit = exitDay?.let { (today - it) / 7 } ?: 0,
                conservativeFlag = rs.any { it.action == PainAction.STOP_REGION || it.action == PainAction.STOP_EXERCISE })
            val c = PainGate.regionConstraint(h)
            d += c.decisions
            c.value?.let { out += it }
        }
        return EngineResult(out, d)
    }

    /** SAF-003 today: joint limits from today's reports, and joints where "continue with caution" allows a shorter range. */
    suspend fun painToday(today: Int): Pair<Map<Joint, Int>, Set<Joint>> {
        val limits = HashMap<Joint, Int>()
        val caution = HashSet<Joint>()
        for (p in docs.between(PainRecord, today, today)) {
            if (p.resolvedDay != null && p.resolvedDay <= today) continue
            val o = PainGate.assess(p.report).value
            o.regionMaxStress?.let { limits[p.report.region] = minOf(it, limits[p.report.region] ?: 4) }
            if (o.action == PainAction.CONTINUE_CAUTION) caution += p.report.region
        }
        return limits to caution
    }

    // ------------------------------------------------------------------------------------------------ totals
    /** Totals of `fromDay..toDay` (inclusive) from finished workouts as done and logged walks (PH-001, STEP-002, LOAD-001, VOL-007). */
    suspend fun totals(fromDay: Int, toDay: Int): Totals {
        val done = log.done(fromDay, toDay)
        val walks = docs.between(WalkRecord, fromDay, toDay)
        var z1 = 0.0; var z2p = 0.0; var hiitWork = 0.0; var hiit = 0; var ssu = 0.0; var load = 0.0; var longestZ1 = 0.0
        var sprints = 0; var impact = 0
        val sets = HashMap<Muscle, Double>()
        val aerobic = ArrayList<AerobicBlock>()
        val carryRot = HashSet<Pattern>()
        for (w in done) {
            val session = doneSession(w)
            val blocks = w.doc.conditioning.mapNotNull { it.doneBlock() }
            for (b in blocks) {
                aerobic += AerobicBlock(b.zone, b.workMinutes, w.day)
                if (b.zone == Zone.Z1) { z1 += b.workMinutes; longestZ1 = maxOf(longestZ1, b.workMinutes) } else z2p += b.workMinutes
                if (b.countsAsHiit) hiitWork += b.workMinutes
                if (b.protocol == com.personalfitnesscoach.engine.safety.HiitProtocol.SPRINT) sprints++
            }
            if (session.hiitBlocks > 0) hiit++
            if (blocks.any { it.impact >= 3 }) impact++
            ssu += SessionValidator.sessionSsu(session)
            val rpe = w.row.sessionRpe
            val minutes = w.row.actualMinutes
            if (rpe != null && minutes != null) load += Workload.sessionLoad(rpe, minutes)
            Volume.weekly(session.exercises.map { it.exercise to it.sets.toDouble() }).forEach { (m, s) -> sets[m] = (sets[m] ?: 0.0) + s }
            session.exercises.map { it.exercise.pattern }.filterTo(carryRot) { it == Pattern.LOADED_CARRY || it == Pattern.ROTATION }
        }
        val walking = walks.filter { it.brisk }.sumOf { it.minutes.toDouble() }
        return Totals(done.size, z1 + z2p, z1, z2p, hiit, hiitWork, walking, Aerobic.whoEquivalentMinutes(aerobic, walking), ssu, load, sets,
            longestZ1, carryRot, sprints, impact)
    }

    /** A finished workout as it was done: working sets logged per exercise, conditioning minutes logged. */
    fun doneSession(w: StoredWorkout): com.personalfitnesscoach.engine.safety.Session {
        val exercises = w.exercises.mapNotNull { e ->
            val ex = Library[e.exerciseId] ?: return@mapNotNull null
            val n = e.working.size
            if (n == 0) null else com.personalfitnesscoach.engine.safety.SessionExercise(ex, n, e.doc.reps.last, e.doc.targetRir, e.doc.loadFactor, e.doc.main,
                e.doc.lastSetToFailure)
        }
        return com.personalfitnesscoach.engine.safety.Session(Tier.valueOf(w.row.tier ?: Tier.FULL.name), exercises,
            w.doc.conditioning.mapNotNull { it.doneBlock() })
    }

    /** Daily LOAD-001 loads for the `days` days ending yesterday, oldest first (rest days 0). */
    suspend fun dailyLoads(today: Int, days: Int): List<Double> {
        val byDay = log.done(today - days, today - 1).groupBy { it.day }
        return ((today - days) until today).map { d -> byDay[d].orEmpty().sumOf { w ->
            val rpe = w.row.sessionRpe; val min = w.row.actualMinutes
            if (rpe != null && min != null) Workload.sessionLoad(rpe, min) else 0.0 } }
    }

    // ------------------------------------------------------------------------------------------------ fatigue (DEL-001)
    /** The six fatigue signals' inputs from history (DEL-001), and the signals active today. */
    suspend fun fatigue(u: UserState, program: ProgramRecord?): Pair<FatigueInputs, Set<Signal>> {
        val today = u.today
        val done = log.done(today - 42, today)
        val lifts = (program?.coreLifts?.values ?: emptyList()).distinct().sorted().mapNotNull { id ->
            val values = done.flatMap { w -> w.exercises.filter { it.exerciseId == id } }.mapNotNull { E1rm.sessionBest(it.workingLogs()) }
            if (values.isEmpty()) null else LiftTrend(id, values.max(), values.takeLast(3))
        }
        val missed = done.takeLast(4).map { w -> w.exercises.count { e -> e.doc.main && e.working.any { (it.reps ?: Int.MAX_VALUE) < e.doc.reps.first } } }
        val rpeOver = done.mapNotNull { w -> val r = w.row.sessionRpe; val p = w.doc.plannedSessionRpe; if (r != null && p != null) r - p else null }
        val readiness = docs.between(ReadinessRecord, today - P.RDY_002.window_days, today)
        val last7 = readiness.filter { it.day > today - 7 }
        val baseline = Baseline.of(readiness.filter { it.day < today }.map { it.rRaw })
        val pains = docs.between(PainRecord, today - 6, today)
        val repeatedAche = pains.groupBy { it.report.region }.values.any { it.size >= 2 }
        val daily = dailyLoads(today, 42)
        val runDown = docs.between(RecoveryRecord, today - 6, today).count { it.runDown }
        val inputs = FatigueInputs(lifts, missed, rpeOver, last7.takeIf { it.isNotEmpty() }?.map { it.rFinal }?.average(),
            last7.takeIf { it.isNotEmpty() }?.map { it.rRaw }?.average(), baseline, readiness.map { it.checkIn.soreness }, repeatedAche,
            if (daily.size >= P.LOAD_004.min_days) Workload.ewma(daily)?.ratio else null, Workload.monotony(daily.takeLast(7)), runDown)
        return inputs to FatigueSignals.active(inputs)
    }

    // ------------------------------------------------------------------------------------------------ the week
    fun blueprint(program: ProgramRecord): Program = Blueprint.plan(program.priorities).value
    fun context(program: ProgramRecord): WeekContext = Blueprint.context(blueprint(program), program.clockWeek)

    /** HIIT-003: screening clear, calibration done and ≥ 3 weeks with Z1 work. */
    suspend fun hiitBaseReady(u: UserState, program: ProgramRecord, ctx: WeekContext): Boolean {
        if (u.effectiveScreening != ScreeningMode.STANDARD || ctx.kind == WeekKind.CALIBRATION) return false
        return docs.all(WeekSummary).count { it.z1Minutes > 0.0 } >= P.HIIT_003.base_weeks
    }

    /** The week planner's input for the week containing `today` (SCH, VOL, FREQ, AER, FL-002/003, SAF-010). */
    suspend fun weekInput(u: UserState, program: ProgramRecord): WeekInput {
        val today = u.today
        val ctx = context(program)
        val weekStart = Days.weekStart(today)
        val last = docs.between(WeekSummary, weekStart - 7, weekStart - 7).firstOrNull()
        val soFar = totals(weekStart, today)
        val (painLimits, _) = painToday(today)
        val p = u.profile
        val prefs = u.preferences
        return WeekInput(
            level = p.level, weeksTraining = program.weeksTraining, daysPerWeek = p.daysPerWeek, equipment = u.equipment.availableOn(today), week = ctx,
            age = u.age, availableDays = p.availableDays, preferredDays = p.preferredDays, sessionMinutes = p.sessionMinutes,
            deload = program.deloadThisWeek, priorities = p.priorities, focusMuscles = p.focusMuscles, screening = u.screening,
            hiitBaseReady = hiitBaseReady(u, program, ctx), hiitDoneEver = program.hiitDoneEver, hiitExposures = soFar.hiitSessions,
            z1SessionMinutes = program.z1SessionMinutes, lastWeekAerobicMinutes = last?.aerobicMinutes ?: 0.0,
            lastWeekHiitWorkMinutes = last?.hiitWorkMinutes ?: 0.0, carryOrRotationLastWeek = program.carryOrRotationLastWeek,
            injuries = u.injuries.sensitiveJoints, blockedTags = u.injuries.blockedTags, jointLimits = painLimits,
            excludedIds = prefs.excludedIds, preferences = prefs.exerciseScores, favourites = prefs.favourites, crowded = prefs.crowdedGym,
            ladderRungs = program.ladderRungs, coreLifts = program.coreLifts, previousBlockChoices = program.previousBlockChoices,
            modalityPreferences = prefs.modalityScores, excludedModalities = prefs.excludedModalities,
            lastWeekEquivalentMinutes = last?.equivalentMinutes ?: 0.0, hiitOptIn = prefs.hiitOptIn, conditions = u.conditions,
        )
    }

    /**
     * Plans the week containing `today` and remembers what was planned (for the block clock) and the block's main lifts (ADH-003).
     * The plan is recomputed from current data each time, so equipment, condition or pain changes reach it at once.
     */
    suspend fun planWeek(u: UserState, program: ProgramRecord): EngineResult<WeekPlan> {
        val input = weekInput(u, program)
        val plan = WeekPlanner.plan(input, blueprint(program))
        val weekStart = Days.weekStart(u.today)
        docs.put(WeekPlanRecord, WeekPlanRecord(weekStart, plan.value.days.map { it.weekday to it.template }, plan.value.deload, plan.value.walkDays))
        if (plan.value.coreLifts.any { (k, v) -> program.coreLifts[k] != v }) {
            docs.update(ProgramRecord) { cur -> (cur ?: program).let { it.copy(coreLifts = it.coreLifts + plan.value.coreLifts) } }
        }
        return plan
    }

    /** SAF-008 week-so-far context for one session on `today` (sets, SSU, HIIT spacing, impact, workload caps, conditions). */
    suspend fun validationContext(u: UserState, program: ProgramRecord, plan: WeekPlan, day: PlannedDay?): ValidationContext {
        val today = u.today
        val weekStart = Days.weekStart(today)
        val soFar = totals(weekStart, today)
        val lastHiit = log.done(today - 14, today).lastOrNull { doneSession(it).hiitBlocks > 0 }
        val weekday = Days.weekday(today)
        val nextHeavy = plan.days.filter { it.heavyLower && it.weekday > weekday }.minOfOrNull { it.weekday }
        val summaries = docs.all(WeekSummary).sortedBy { it.weekStartDay }
        val historyDays = maxOf(0, today - program.startDay)
        val (painLimits, _) = painToday(today)
        val regions = regions(today).value + u.injuries.regions
        return ValidationContext(
            level = u.profile.level, weeksTraining = program.weeksTraining, age = u.age, screening = u.screening, jointLimits = painLimits,
            regions = regions, blockedTags = u.injuries.blockedTags, excludedIds = u.preferences.excludedIds,
            excludedModalities = u.preferences.excludedModalities, weekSetsSoFar = soFar.sets, weekSsuSoFar = soFar.ssu,
            weeklySsuLimit = Ssu.weeklyLimit(u.profile.level, summaries.takeLast(3).map { it.ssu }, summaries.size),
            hiitThisWeekSoFar = soFar.hiitSessions, hiitBaseReady = hiitBaseReady(u, program, context(program)), sprintsThisWeekSoFar = soFar.sprints,
            hoursSinceLastHiit = lastHiit?.let { (clock.nowMs() - (it.row.endedAtMs ?: it.row.startedAtMs ?: clock.nowMs())) / 3_600_000.0 },
            hoursToNextHeavyLower = nextHeavy?.let { (it - weekday) * 24.0 },
            impactSessionsThisWeekSoFar = soFar.impactSessions, heavyLegsNextDay = nextHeavy == weekday + 1,
            weekLoadSoFar = soFar.workload, weeklyLoadCap = Workload.planningCap(summaries.map { it.workload }, historyDays),
            lastWeekSsu = if (historyDays >= P.LOAD_006.phase1_days) summaries.lastOrNull()?.ssu else null,
            loadCapExempt = plan.deload || program.justFinishedLighterWeek, inDeload = plan.deload,
            equipmentToday = u.equipment.availableOn(today), library = Library.all.filter { !it.userAddOnly },
            planImpactAllowed = day?.impactAllowed ?: true, planIntervalModalities = day?.intervalModalities, conditions = u.conditions,
        )
    }

    /** Everything GEN-001 needs for one planned day (step 1, "load state"). */
    suspend fun request(
        u: UserState,
        program: ProgramRecord,
        plan: WeekPlan,
        day: PlannedDay,
        tier: Tier,
        minutes: Int = u.profile.sessionMinutes,
        redFlags: Set<String> = emptySet(),
        illnessSymptoms: Set<String> = emptySet(),
        awayFromGym: Boolean = false,
    ): GenerationRequest {
        val today = u.today
        val h = history(u, day)
        val (painLimits, caution) = painToday(today)
        val earlierToday = log.done(today, today).any { doneSession(it).hiitBlocks > 0 }
        val equipmentToday = if (awayFromGym) u.equipment.homeKit else u.equipment.availableOn(today)
        return GenerationRequest(
            day = day, level = u.profile.level, weeksTraining = program.weeksTraining, minutes = minutes, equipmentToday = equipmentToday, tier = tier,
            age = u.age, screening = u.screening, redFlags = redFlags, illnessSymptoms = illnessSymptoms, inventory = u.equipment.inventory,
            e1rm = h.e1rm, progression = h.progression, calibrationLoads = h.calibrationLoads, calibrationCeilings = h.calibrationCeilings,
            lastWeekLoads = h.lastWeekLoads, bodyweightKg = h.bodyweightKg, crowded = u.preferences.crowdedGym, hiitEarlierToday = earlierToday,
            painCaution = caution, jointLimits = painLimits, regions = regions(today).value + u.injuries.regions, blockedTags = u.injuries.blockedTags,
            excludedModalities = u.preferences.excludedModalities, excludedIds = u.preferences.excludedIds, preferences = u.preferences.exerciseScores,
            inDeload = plan.deload, week = validationContext(u, program, plan, day), conditions = u.conditions, circuitJumps = u.preferences.circuitJumps,
        )
    }

    // ------------------------------------------------------------------------------------------------ body progress
    /** Weight trend, latest waist and strength trend (FL-004, FL-005). */
    suspend fun body(today: Int = clock.today()): BodyView {
        val weights = docs.all(WeightRecord).map { WeightEntry(it.day, it.kg) }
        val waist = docs.all(WaistRecord).maxByOrNull { it.day }
        val points = log.done(today - 7 * P.FL_005.trend_weeks - 28, today).flatMap { w ->
            w.exercises.mapNotNull { e ->
                val best = e.workingLogs().filter { it.load > 0 }.maxByOrNull { it.load * (1.0 + it.reps / 30.0) } ?: return@mapNotNull null
                StrengthPoint(w.day, e.exerciseId, best.load, best.reps, E1rm.sessionBest(e.workingLogs()))
            }
        }
        return BodyView(BodyProgress.weightTrend(weights, today).value, waist?.let { BodyProgress.waist(WaistEntry(it.day, it.readingsCm)).value },
            waist?.day, BodyProgress.waistDue(waist?.day, today), BodyProgress.strengthTrend(points, today).value)
    }

    suspend fun weekPlanRecord(weekStart: Int): WeekPlanRecord? = docs.get(WeekPlanRecord, dayKey(weekStart))
}
