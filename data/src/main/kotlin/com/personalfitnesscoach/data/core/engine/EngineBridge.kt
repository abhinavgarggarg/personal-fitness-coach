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
import com.personalfitnesscoach.data.core.model.SafetyStopRecord
import com.personalfitnesscoach.data.core.model.ScreeningRecord
import com.personalfitnesscoach.data.core.model.WaistRecord
import com.personalfitnesscoach.data.core.model.WalkRecord
import com.personalfitnesscoach.data.core.model.WeekPlanRecord
import com.personalfitnesscoach.data.core.model.WeekSummary
import com.personalfitnesscoach.data.core.model.WeightRecord
import com.personalfitnesscoach.data.core.model.dayKey
import com.personalfitnesscoach.data.core.repo.Docs
import com.personalfitnesscoach.data.core.session.SessionLog
import com.personalfitnesscoach.data.core.session.Status
import com.personalfitnesscoach.data.core.session.StoredWorkout
import com.personalfitnesscoach.data.core.time.AppClock
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.engine.calc.Baseline
import com.personalfitnesscoach.engine.calc.E1rm
import com.personalfitnesscoach.engine.calc.FatigueInputs
import com.personalfitnesscoach.engine.calc.PlateMath
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
import com.personalfitnesscoach.engine.progression.ReturnPlan
import com.personalfitnesscoach.engine.progression.ReturnToTraining
import com.personalfitnesscoach.engine.activity.BriskWalks
import com.personalfitnesscoach.engine.activity.LoggedWalk
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.safety.ConditionLimits
import com.personalfitnesscoach.engine.safety.Conditions
import com.personalfitnesscoach.engine.safety.PainAction
import com.personalfitnesscoach.engine.safety.PainGate
import com.personalfitnesscoach.engine.safety.PainKind
import com.personalfitnesscoach.engine.safety.RegionConstraint
import com.personalfitnesscoach.engine.safety.RegionHistory
import com.personalfitnesscoach.engine.safety.RedFlags
import com.personalfitnesscoach.engine.safety.SafetyStop
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

/** Today's limits from stored safety state (SAF-002 stop, REG return, DEL-002 lighter week). */
data class DayGate(val tierCap: Tier?, val stop: SafetyStop?, val returnPlan: ReturnPlan, val decisions: List<Decision>) {
    fun cap(t: Tier): Tier = tierCap?.let { Tier.min(t, it) } ?: t
    /** A safety rule set today's limit, so RDY-007 allows no harder choice. */
    val locked: Boolean get() = stop != null || tierCap != null
}

/**
 * REG-002…005 for today, and the day before which an exercise's stored state is stale: after a layoff of 56 days or more (REG-004),
 * every exercise last done before `recalibrateBefore` recalibrates on its first exposure back — once each (D-076).
 */
data class ReturnInfo(val plan: ReturnPlan, val recalibrateBefore: Int? = null)

/**
 * GEN-001's request for one planned day, and the load factor each exercise's history was scaled by (REG-003…005 return, DEL-004
 * resume). The factors are recorded on the session's items, so a reduced exposure never becomes the base of the next one.
 */
data class PreparedSession(val request: GenerationRequest, val loadFactors: Map<String, Double>, val decisions: List<Decision>)

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
        val injuries = injuryPlan(profile.priorInjuries, today).also { d += it.decisions }.value
        return UserState(today, profile, profile.age(today), screening, docs.get(EquipmentRecord) ?: EquipmentRecord(emptySet()),
            docs.get(PreferencesRecord) ?: PreferencesRecord(), conditions, injuries, d)
    }

    /** SAF-010 limits for `today`: each picked condition with its time-based facts as of today, merged "strictest wins". */
    suspend fun conditions(today: Int = clock.today()): EngineResult<ConditionLimits> {
        // Not answered yet: conservative mode until the picker has been answered (fail closed, like missing screening).
        val rec = docs.get(ConditionsRecord) ?: return EngineResult(ConditionLimits.NONE.copy(conservative = true))
        if (rec.items.isEmpty()) return EngineResult(ConditionLimits.NONE)
        // "Weeks of training" for time-based unlocks: finished calendar weeks that began on or after the day the condition was added
        // and had at least one completed session (the current, unfinished week never counts).
        val thisWeek = Days.weekStart(today)
        val weeksWithSessions = doneWeekStarts(rec.items.minOf { it.addedDay }, today).filter { it < thisWeek }
        return Conditions.resolve(rec.items.map { c -> c.toUserCondition(today, weeksWithSessions.count { it >= c.addedDay }) })
    }

    /** IND-001 per injury: conservative for 4 weeks from the day each was added, then "sensitive" for swaps. */
    private fun injuryPlan(prior: Map<Joint, Int>, today: Int): EngineResult<Individual.InjuryPlan> {
        if (prior.isEmpty()) return Individual.injuries(emptySet(), 0)
        val parts = prior.entries.sortedBy { it.key.ordinal }.map { (j, added) -> Individual.injuries(setOf(j), weeksSince(added, today)) }
        return EngineResult(Individual.InjuryPlan(parts.flatMap { it.value.blockedTags }.toSet(), parts.flatMap { it.value.regions },
            parts.flatMap { it.value.sensitiveJoints }.toSet()), parts.flatMap { it.decisions })
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
     * before this week (8 weeks back at most). A calibration exposure contributes the heaviest ramp load lifted for the ramp's
     * target reps (review R3-13: the load the ramp reached, not where it started).
     */
    suspend fun lastWeekLoads(today: Int): Map<String, Double> {
        val weekStart = Days.weekStart(today)
        val out = HashMap<String, Double>()
        for (w in log.done(weekStart - 56, weekStart - 1).asReversed()) {
            for (e in w.exercises) {
                val key = SessionGenerator.loadKey(e.row.slotKey, e.exerciseId)
                if (key in out) continue
                val load = if (e.doc.calibrating) e.calibration.filter { (it.reps ?: 0) >= ExerciseProgress.calibrationReps(e.doc.reps) }.mapNotNull { it.loadKg }.maxOrNull()
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
        // Joint/tendon pain, and any report the pain gate stopped (sharp, swelling, after a fall … whatever kind was tapped).
        val reports = docs.between(PainRecord, today - 90, today).filter {
            (it.report.kind == PainKind.JOINT_OR_TENDON && it.report.rating >= 1) || it.action == PainAction.STOP_REGION || it.action == PainAction.STOP_EXERCISE }
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
            val blocks = session.conditioning
            for (b in blocks) {
                aerobic += AerobicBlock(b.zone, b.workMinutes, w.day)
                if (b.zone == Zone.Z1) { z1 += b.workMinutes; longestZ1 = maxOf(longestZ1, b.workMinutes) } else z2p += b.workMinutes
                if (b.countsAsHiit) hiitWork += b.workMinutes
                if (b.protocol == com.personalfitnesscoach.engine.safety.HiitProtocol.SPRINT) sprints++
            }
            if (session.hiitBlocks > 0) hiit++
            if (blocks.any { it.impact > 0 } || session.exercises.any { it.exercise.impact > 0 }) impact++
            ssu += SessionValidator.sessionSsu(session)
            val rpe = w.row.sessionRpe
            val minutes = w.row.actualMinutes
            if (rpe != null && minutes != null) load += Workload.sessionLoad(rpe, minutes)
            Volume.weekly(session.exercises.map { it.exercise to it.sets.toDouble() }).forEach { (m, s) -> sets[m] = (sets[m] ?: 0.0) + s }
            session.exercises.map { it.exercise.pattern }.filterTo(carryRot) { it == Pattern.LOADED_CARRY || it == Pattern.ROTATION }
        }
        // STEP-002: brisk walks of 10 minutes or more, overlaps counted once (BriskWalks), per day.
        val walking = walks.groupBy { it.day }.values.sumOf { day ->
            BriskWalks.z1Minutes(emptyList(), day.map { LoggedWalk(it.startMinute, it.minutes, it.brisk) }).value }
        return Totals(done.size, z1 + z2p, z1, z2p, hiit, hiitWork, walking, Aerobic.whoEquivalentMinutes(aerobic, walking), ssu, load, sets,
            longestZ1, carryRot, sprints, impact)
    }

    /**
     * A finished workout as it was done: working sets logged per exercise; conditioning as logged, or as planned when the minutes
     * were not recorded (so HIIT, impact and stress are never under-counted), never more than planned.
     */
    fun doneSession(w: StoredWorkout): com.personalfitnesscoach.engine.safety.Session {
        val exercises = w.exercises.mapNotNull { e ->
            val ex = Library[e.exerciseId] ?: return@mapNotNull null
            val n = e.working.size
            if (n == 0) null else com.personalfitnesscoach.engine.safety.SessionExercise(ex, n, e.doc.reps.last, e.doc.targetRir, e.doc.loadFactor, e.doc.main,
                e.doc.lastSetToFailure)
        }
        return com.personalfitnesscoach.engine.safety.Session(Tier.valueOf(w.row.tier ?: Tier.FULL.name), exercises,
            w.doc.conditioning.mapNotNull { it.countedBlock() })
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
        // LOAD-004/006: workload ratios only from real history (no zero padding before the first session).
        val historyDays = historyDays(today, program)
        val daily = dailyLoads(today, minOf(42, historyDays))
        val runDown = docs.between(RecoveryRecord, today - 6, today).count { it.runDown }
        // F3 compares like with like: the 7-day mean of raw scores against the baseline of raw scores (RDY-002).
        val rawMean = last7.takeIf { it.isNotEmpty() }?.map { it.rRaw }?.average()
        val inputs = FatigueInputs(lifts, missed, rpeOver, rawMean, rawMean, baseline, readiness.map { it.checkIn.soreness }, repeatedAche,
            if (daily.size >= P.LOAD_004.min_days) Workload.ewma(daily)?.ratio else null, if (daily.size >= 7) Workload.monotony(daily.takeLast(7)) else null, runDown)
        return inputs to FatigueSignals.active(inputs)
    }

    /** Days since the first finished workout (0 without one). */
    private suspend fun historyDays(today: Int, program: ProgramRecord?): Int {
        val first = log.done(minOf(program?.startDay ?: today, today) - 3650, today).firstOrNull()?.day ?: return 0
        return maxOf(0, today - first)
    }

    // ------------------------------------------------------------------------------------------------ the week
    fun blueprint(program: ProgramRecord): Program = Blueprint.plan(program.priorities).value
    fun context(program: ProgramRecord): WeekContext = Blueprint.context(blueprint(program), program.clockWeek)

    /** HIIT-003: screening clear, calibration done and ≥ 3 weeks with Z1 work; never during a return that pauses HIIT (REG-004/005). */
    suspend fun hiitBaseReady(u: UserState, program: ProgramRecord, ctx: WeekContext): Boolean {
        if (u.effectiveScreening != ScreeningMode.STANDARD || ctx.kind == WeekKind.CALIBRATION) return false
        if (!returnPlan(u, program).value.hiitAllowed) return false
        return docs.all(WeekSummary).count { it.z1Minutes > 0.0 } >= P.HIIT_003.base_weeks
    }

    /** The planner's week context: an inserted DEL-002 deload is planned as a deload week (the clock waits for it). */
    fun plannedContext(program: ProgramRecord): WeekContext {
        val ctx = context(program)
        return if (program.insertedDeload(ctx)) ctx.copy(kind = WeekKind.DELOAD_OR_PIVOT) else ctx
    }

    /**
     * The last finished week that had sessions, within 4 weeks: the reference for week-over-week caps (AER-003, PROG-007, LOAD-005).
     * A missed week never turns a cap off; after a longer gap the return rules (REG-003/004) set the dose instead.
     */
    suspend fun lastActiveWeek(today: Int): WeekSummary? {
        val ws = Days.weekStart(today)
        return docs.between(WeekSummary, ws - 28, ws - 1).lastOrNull { it.completed > 0 }
    }

    /** The week planner's input for the week containing `today` (SCH, VOL, FREQ, AER, FL-002/003, SAF-010). */
    suspend fun weekInput(u: UserState, program: ProgramRecord): WeekInput {
        val today = u.today
        val ctx = plannedContext(program)
        val last = lastActiveWeek(today)
        val sinceBlock = log.done(program.blockStartDay, today).count { w -> doneSession(w).conditioning.any { it.protocol != null } }
        val (painLimits, _) = painToday(today)
        val p = u.profile
        val prefs = u.preferences
        return WeekInput(
            level = p.level, weeksTraining = program.weeksTraining, daysPerWeek = p.daysPerWeek, equipment = u.equipment.availableOn(today), week = ctx,
            age = u.age, availableDays = p.availableDays, preferredDays = p.preferredDays, sessionMinutes = p.sessionMinutes,
            deload = program.deloadThisWeek, priorities = p.priorities, focusMuscles = p.focusMuscles, screening = u.screening,
            hiitBaseReady = hiitBaseReady(u, program, ctx), hiitDoneEver = program.hiitDoneEver, hiitExposures = sinceBlock,
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
        val z1 = plan.value.days.filter { it.template.strength }.flatMap { it.conditioning }.filter { it.zone == Zone.Z1 && !it.hiit }.maxOfOrNull { it.workMinutes }
        docs.put(WeekPlanRecord, WeekPlanRecord(weekStart, plan.value.days.map { it.weekday to it.template }, plan.value.deload, plan.value.walkDays, z1))
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
        val nextHeavy = daysToNextHeavyLower(plan, day, today)
        val active = docs.all(WeekSummary).filter { it.completed > 0 && it.weekStartDay < weekStart }.sortedBy { it.weekStartDay }
        val historyDays = historyDays(today, program)
        val (painLimits, _) = painToday(today)
        val regions = regions(today).value + u.injuries.regions
        val ret = returnPlan(u, program).value
        return ValidationContext(
            level = u.profile.level, weeksTraining = program.weeksTraining, age = u.age, screening = u.screening, jointLimits = painLimits,
            regions = regions, blockedTags = u.injuries.blockedTags, excludedIds = u.preferences.excludedIds,
            excludedModalities = u.preferences.excludedModalities, weekSetsSoFar = soFar.sets, weekSsuSoFar = soFar.ssu,
            weeklySsuLimit = Ssu.weeklyLimit(u.profile.level, active.takeLast(3).map { it.ssu }, active.size),
            hiitThisWeekSoFar = soFar.hiitSessions, hiitBaseReady = hiitBaseReady(u, program, plannedContext(program)), sprintsThisWeekSoFar = soFar.sprints,
            hoursSinceLastHiit = lastHiit?.let { (clock.nowMs() - (it.row.endedAtMs ?: it.row.startedAtMs ?: clock.nowMs())) / 3_600_000.0 },
            hoursToNextHeavyLower = nextHeavy?.let { it * 24.0 }, heavyLegsNextDay = nextHeavy == 1,
            impactSessionsThisWeekSoFar = soFar.impactSessions,
            weekLoadSoFar = soFar.workload, weeklyLoadCap = Workload.planningCap(active.map { it.workload }, historyDays), k = learnedK(active),
            lastWeekSsu = if (historyDays >= P.LOAD_006.phase1_days) lastActiveWeek(today)?.ssu else null,
            loadCapExempt = plan.deload || program.justFinishedLighterWeek || ret.inReturn, inDeload = plan.deload,
            equipmentToday = u.equipment.availableOn(today), library = Library.all.filter { !it.userAddOnly },
            planImpactAllowed = day?.impactAllowed ?: true, planIntervalModalities = day?.intervalModalities, conditions = u.conditions,
            strengthYesterday = strengthDoneOn(today - 1),
        )
    }

    /**
     * Resistance work was logged in a finished workout on `day` (SAF-010 "no strength on consecutive days"): any working or calibration
     * set of a done exercise — what was lifted, not what the day's template was called.
     */
    suspend fun strengthDoneOn(day: Int): Boolean = log.done(day, day).any { w ->
        w.exercises.any { e -> e.row.status == Status.DONE && e.sets.any { it.kind != com.personalfitnesscoach.data.core.session.SetKind.WARMUP } } }

    /**
     * CON-003/CON-004: days until the next possible heavy-lower session, at the planner's day granularity. A planned heavy-lower day
     * still to come counts from its weekday; one already passed but not done may be caught up tomorrow; next week repeats this
     * week's pattern. The day being generated is not counted.
     */
    suspend fun daysToNextHeavyLower(plan: WeekPlan, day: PlannedDay?, today: Int): Int? {
        val weekday = Days.weekday(today)
        val handled = log.between(Days.weekStart(today), today).filter { it.row.status == Status.DONE || it.row.status == Status.SKIPPED }.map { it.doc.weekday }.toSet()
        val pending = plan.days.filter { it.heavyLower && it.weekday !in handled && !(day != null && it.weekday == day.weekday && it.template == day.template) }
            .map { if (it.weekday > weekday) it.weekday - weekday else 1 }
        val nextWeek = plan.days.filter { it.heavyLower }.map { it.weekday + 7 - weekday }
        return (pending + nextWeek).minOrNull()
    }

    /**
     * VOL-007 k (AU per SSU) once 4 weeks with both a session rating and stress exist: the median of the weekly load ÷ SSU ratios
     * (re-check finding 11: a ratio of sums lets one unusual week move k).
     */
    fun learnedK(active: List<WeekSummary>): Double? {
        val ratios = active.filter { it.workload > 0 && it.ssu > 0 }.takeLast(P.VOL_007.k_after_weeks).map { it.workload / it.ssu }.sorted()
        if (ratios.size < P.VOL_007.k_after_weeks) return null
        val n = ratios.size
        return if (n % 2 == 1) ratios[n / 2] else (ratios[n / 2 - 1] + ratios[n / 2]) / 2.0
    }

    // ------------------------------------------------------------------------------------------------ return, stops, lighter weeks
    /** REG-002…005 for today (the plan only; see [returnInfo]). */
    suspend fun returnPlan(u: UserState, program: ProgramRecord?): EngineResult<ReturnPlan> =
        returnInfo(u, program).let { EngineResult(it.value.plan, it.decisions) }

    /**
     * REG-002…005 for today. Two parts, merged so the stricter value of each control wins (re-check finding 2):
     *  - the ramp from the latest earlier gap of 7 days or more, while it still runs (REG-003/004), counted from the first session back;
     *  - today's gap since the last finished session and the planned sessions missed since then (REG-002/003/004).
     * A missed session inside a ramp therefore never ends the ramp. After a layoff of 56 days or more, exercises last done before the
     * return recalibrate once each ([ReturnInfo.recalibrateBefore], D-076). A recent illness adds REG-005.
     */
    suspend fun returnInfo(u: UserState, program: ProgramRecord?): EngineResult<ReturnInfo> {
        val today = u.today
        val done = log.done(today - 400, today)
        val days = done.map { it.day }.filter { it < today }
        val d = ArrayList<Decision>()
        var plan = ReturnPlan()
        var recalibrateBefore: Int? = null
        if (days.isNotEmpty()) {
            val i = (days.size - 1 downTo 1).firstOrNull { days[it] - days[it - 1] >= P.REG_003.days[0] }
            if (i != null) {
                val back = days[i]
                val r = ReturnToTraining.afterBreak(back - days[i - 1], 0, (today - back) / 7, done.count { it.day >= back }, u.age, u.flaggedScreen)
                if (r.value.recalibrate) recalibrateBefore = back
                if (r.value.inReturn) { d += r.decisions; plan = r.value }
            }
            val last = days.last()
            val gapNow = today - last
            val missed = missedPlannedSince(last, today)
            if (gapNow >= P.REG_003.days[0] || missed >= 1) {
                val r = ReturnToTraining.afterBreak(gapNow, missed, 0, done.count { it.day == today }, u.age, u.flaggedScreen)
                d += r.decisions
                if (r.value.recalibrate) recalibrateBefore = today
                plan = merge(plan, r.value)
            }
        }
        // REG-005: after an illness (today's symptoms are the check-in's SAF-007 gate).
        val ill = (docs.between(RecoveryRecord, today - 28, today).filter { it.illness }.map { it.day } +
            docs.between(ReadinessRecord, today - 28, today).filter { it.illnessSymptoms.isNotEmpty() }.map { it.day }).toSortedSet()
        val lastIll = ill.lastOrNull { it < today }
        if (lastIll != null && ill.none { it == today }) {
            var first = lastIll
            while (first - 1 in ill) first--
            val hour = ((clock.nowMs() - Days.startMs(today, clock.zone())) / 3_600_000L).toInt()
            val symptomFree = (today - lastIll - 1) * 24 + hour
            val r = ReturnToTraining.afterIllness(symptomFree, false, done.count { it.day > lastIll }, false, lastIll - first + 1, (today - lastIll) / 7,
                u.age, u.flaggedScreen)
            if (r.value.inReturn || r.value.tierCap != null || r.value.setsFactor < 1.0) {
                d += r.decisions; plan = merge(plan, r.value)
                if (r.value.recalibrate) recalibrateBefore = maxOf(recalibrateBefore ?: Int.MIN_VALUE, lastIll + 1)
            }
        }
        return EngineResult(ReturnInfo(plan, recalibrateBefore), d)
    }

    private fun merge(a: ReturnPlan, b: ReturnPlan) = ReturnPlan(minOf(a.loadFactor, b.loadFactor), minOf(a.setsFactor, b.setsFactor),
        listOfNotNull(a.tierCap, b.tierCap).minByOrNull { it.ordinal }, a.hiitAllowed && b.hiitAllowed, a.recalibrate || b.recalibrate, a.inReturn || b.inReturn)

    /** Planned sessions between the last finished one and today that were not done (SKIPPED counts as missed): REG-002/003. */
    suspend fun missedPlannedSince(lastDone: Int, today: Int): Int {
        var n = 0
        var ws = Days.weekStart(lastDone)
        while (ws <= Days.weekStart(today)) {
            docs.get(WeekPlanRecord, dayKey(ws))?.days?.forEach { (wd, _) -> if (ws + wd in (lastDone + 1) until today) n++ }
            ws += 7
        }
        return n
    }

    /**
     * Today's limits from stored safety state, applied to every session: an unconfirmed SAF-002 stop (no training), the first session
     * after a confirmed stop (LIGHT), the return rules (REG-003…005) and a DEL-002 lighter week (MODIFIED).
     */
    suspend fun gate(u: UserState, program: ProgramRecord?): DayGate {
        val today = u.today
        val d = ArrayList<Decision>()
        var cap: Tier? = null
        fun capAt(t: Tier) { cap = cap?.let { Tier.min(it, t) } ?: t }
        var stop: SafetyStop? = null
        docs.get(SafetyStopRecord)?.let { s ->
            if (s.confirmedDay == null) { stop = SafetyStop(s.symptoms, P.SAF_002.emergency_number_default); capAt(Tier.RECOVERY) }
            else if (log.done(s.confirmedDay, today).isEmpty()) RedFlags.tierAfterStop(true)?.let { capAt(it) }
        }
        val ret = returnPlan(u, program).also { d += it.decisions }.value
        ret.tierCap?.let { capAt(it) }
        if (ret.setsFactor <= 0.0) capAt(Tier.RECOVERY)
        if ((program?.lighterSessionsLeft ?: 0) > 0) capAt(Tier.MODIFIED)
        return DayGate(cap, stop, ret, d)
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
    ): GenerationRequest = prepare(u, program, plan, day, tier, minutes, redFlags, illnessSymptoms, awayFromGym).request

    /**
     * GEN-001 step 1 for one planned day: the request, and the load factor each exercise was scaled by. Applied here, from stored
     * state: the SAF-002 stop and the day's caps ([gate]); the return ramp and recalibration (REG-003…005, D-076); the first week after
     * a deload (DEL-004: loads 95–100%, RIR +1); SAF-010's "no strength on consecutive days" (also checked again by the validator).
     */
    suspend fun prepare(
        u: UserState,
        program: ProgramRecord,
        plan: WeekPlan,
        day: PlannedDay,
        tier: Tier,
        minutes: Int = u.profile.sessionMinutes,
        redFlags: Set<String> = emptySet(),
        illnessSymptoms: Set<String> = emptySet(),
        awayFromGym: Boolean = false,
    ): PreparedSession {
        val today = u.today
        val d = ArrayList<Decision>()
        val gate = gate(u, program)
        val ret = gate.returnPlan
        val before = returnInfo(u, program).value.recalibrateBefore
        val stale = if (before == null) emptySet() else staleSince(before, today)
        val resume = resumeAfterDeload(u, program)
        val h = scaled(history(u, day), u, ret, resume, stale)
        val factors = day.slots.map { it.exercise.id }.distinct().associateWith { id ->
            if (id in stale) 1.0 else minOf(ret.loadFactor, if (id in resume.second) resume.first else 1.0) }.filterValues { it < 1.0 - 1e-9 }
        // REG-004 sets: fewer sets per slot while the return ramp runs (never below one).
        var dose = if (ret.setsFactor in 0.0..0.999) day.copy(slots = day.slots.map { it.copy(sets = maxOf(1, Math.floor(it.sets * ret.setsFactor + 1e-9).toInt())) }) else day
        // DEL-004: RIR +1 in the first week after a deload week (re-check finding 9).
        val ws = Days.weekStart(today)
        if (docs.between(WeekSummary, ws - 7, ws - 1).firstOrNull()?.deload == true && dose.slots.isNotEmpty()) {
            val off = P.DEL_004.week1_rir_offset.toDouble()
            dose = dose.copy(slots = dose.slots.map { it.copy(targetRir = minOf(10.0, it.targetRir + off)) })
            d += Decision(com.personalfitnesscoach.engine.core.DecisionKind.LOAD_CHANGE, listOf(com.personalfitnesscoach.engine.registry.RuleIds.DEL_004),
                com.personalfitnesscoach.engine.core.ReasonKey.DELOAD_RESUME, outputs = mapOf("rirOffset" to off, "loadFactor" to resume.first))
        }
        // SAF-010 (type 2 diabetes): resistance work yesterday → none today, whichever planned day is opened (re-check finding 4).
        if (!u.conditions.strengthOnConsecutiveDays && dose.slots.isNotEmpty() && strengthDoneOn(today - 1)) {
            d += Decision(com.personalfitnesscoach.engine.core.DecisionKind.SAFETY, listOf(com.personalfitnesscoach.engine.registry.RuleIds.SAF_010),
                com.personalfitnesscoach.engine.core.ReasonKey.STRENGTH_DAYS_SPACED, inputs = mapOf("strengthYesterday" to true),
                outputs = mapOf("removed" to dose.slots.map { it.exercise.id }))
            dose = dose.copy(slots = emptyList())
        }
        val (painLimits, caution) = painToday(today)
        val earlierToday = log.done(today, today).any { doneSession(it).hiitBlocks > 0 }
        val equipmentToday = if (awayFromGym) u.equipment.homeKit else u.equipment.availableOn(today)
        val req = GenerationRequest(
            day = dose, level = u.profile.level, weeksTraining = program.weeksTraining, minutes = minutes, equipmentToday = equipmentToday, tier = gate.cap(tier),
            age = u.age, screening = u.screening, redFlags = redFlags + (gate.stop?.symptoms ?: emptySet()), illnessSymptoms = illnessSymptoms, inventory = u.equipment.inventory,
            e1rm = h.e1rm, progression = h.progression, calibrationLoads = h.calibrationLoads, calibrationCeilings = h.calibrationCeilings,
            lastWeekLoads = h.lastWeekLoads, bodyweightKg = h.bodyweightKg, crowded = u.preferences.crowdedGym, hiitEarlierToday = earlierToday,
            painCaution = caution, jointLimits = painLimits, regions = regions(today).value + u.injuries.regions, blockedTags = u.injuries.blockedTags,
            excludedModalities = u.preferences.excludedModalities, excludedIds = u.preferences.excludedIds, preferences = u.preferences.exerciseScores,
            inDeload = plan.deload, week = validationContext(u, program, plan, day), conditions = u.conditions, circuitJumps = u.preferences.circuitJumps,
        )
        return PreparedSession(req, factors, d)
    }

    /**
     * DEL-004: the first exposure of each exercise after a deload week starts at 100% when no fatigue signal remains, else 95%.
     * Returns the factor and the exercises it applies to (not yet done since the deload ended).
     */
    suspend fun resumeAfterDeload(u: UserState, program: ProgramRecord): Pair<Double, Set<String>> {
        val ws = Days.weekStart(u.today)
        val prev = docs.between(WeekSummary, ws - 7, ws - 1).firstOrNull() ?: return 1.0 to emptySet()
        if (!prev.deload) return 1.0 to emptySet()
        val factor = com.personalfitnesscoach.engine.calc.Deload.resumeLoadFactor(fatigue(u, program).second.size)
        if (factor >= 1.0) return 1.0 to emptySet()
        return factor to docs.all(ExerciseState).filter { (it.lastDoneDay ?: Int.MIN_VALUE) < ws }.map { it.exerciseId }.toSet()
    }

    /**
     * D-076: exercises with stored state that were not lifted in a finished workout from `before` to `today` — their state predates a
     * layoff of 56 days or more, so they recalibrate. Read from the workout log (what was actually lifted), not from stored state.
     */
    suspend fun staleSince(before: Int, today: Int): Set<String> {
        val doneSince = log.done(before, today).flatMap { w ->
            w.exercises.filter { e -> e.row.status == Status.DONE && e.sets.any { it.kind != com.personalfitnesscoach.data.core.session.SetKind.WARMUP } }
                .map { it.exerciseId } }.toSet()
        return docs.all(ExerciseState).map { it.exerciseId }.filter { it !in doneSince }.toSet()
    }

    /**
     * The history maps scaled for a return (REG-003/004/005) or a post-deload resume (DEL-004). Exercises in `recalibrate` (stale since
     * a layoff of 56 days or more, D-076) lose their e1RM and prescription so calibration runs again, never above the old working load;
     * everything else is scaled by its factor. The scaled load is recorded with the factor ([PreparedSession.loadFactors]), so the next
     * exposure scales the same base again instead of compounding (re-check finding 7).
     */
    fun scaled(h: HistoryMaps, u: UserState, ret: ReturnPlan, resume: Pair<Double, Set<String>>, recalibrate: Set<String> = emptySet()): HistoryMaps {
        var out = h
        if (recalibrate.isNotEmpty()) {
            val ceilings = HashMap(h.calibrationCeilings)
            for (id in recalibrate) {
                h.e1rm[id]?.let { e -> ceilings[id] = minOf(ceilings[id] ?: Double.MAX_VALUE, E1rm.loadFor(e, 8, 2.0)) }
                h.progression[id]?.let { p -> ceilings[id] = minOf(ceilings[id] ?: Double.MAX_VALUE, p.load) }
            }
            out = h.copy(e1rm = h.e1rm - recalibrate, progression = h.progression - recalibrate, calibrationLoads = h.calibrationLoads - recalibrate,
                calibrationCeilings = ceilings)
        }
        fun f(id: String) = if (id in recalibrate) 1.0 else minOf(ret.loadFactor, if (id in resume.second) resume.first else 1.0)
        if (ret.loadFactor >= 1.0 && resume.second.isEmpty()) return out
        fun down(id: String, x: Double): Double {
            val ex = Library[id] ?: return x
            val avail = PlateMath.loadsFor(ex, u.equipment.inventory)
            return avail.filter { it <= x + 1e-9 }.maxOrNull() ?: avail.minOrNull() ?: x
        }
        return out.copy(
            e1rm = out.e1rm.mapValues { (id, v) -> v * f(id) },
            progression = out.progression.mapValues { (id, p) -> if (f(id) >= 1.0) p else p.copy(load = down(id, p.load * f(id))) },
            calibrationLoads = out.calibrationLoads.mapValues { (id, v) -> if (f(id) >= 1.0) v else down(id, v * f(id)) },
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
