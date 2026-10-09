package com.personalfitnesscoach.data.core.engine

import com.personalfitnesscoach.data.core.model.DecisionEntry
import com.personalfitnesscoach.data.core.model.ExerciseState
import com.personalfitnesscoach.data.core.model.KnownNumber
import com.personalfitnesscoach.data.core.model.PainRecord
import com.personalfitnesscoach.data.core.model.ProgramRecord
import com.personalfitnesscoach.data.core.model.ReadinessRecord
import com.personalfitnesscoach.data.core.model.SettingsRecord
import com.personalfitnesscoach.data.core.model.StepStateRecord
import com.personalfitnesscoach.data.core.model.StepsRecord
import com.personalfitnesscoach.data.core.model.WeekPlanRecord
import com.personalfitnesscoach.data.core.model.WeekSummary
import com.personalfitnesscoach.data.core.model.dayKey
import com.personalfitnesscoach.data.core.repo.Docs
import com.personalfitnesscoach.data.core.session.SessionLog
import com.personalfitnesscoach.data.core.session.Status
import com.personalfitnesscoach.data.core.session.StoredWorkout
import com.personalfitnesscoach.data.core.store.RowStore
import com.personalfitnesscoach.data.core.time.AppClock
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.engine.activity.DaySteps
import com.personalfitnesscoach.engine.activity.StepState
import com.personalfitnesscoach.engine.activity.StepTarget
import com.personalfitnesscoach.engine.calc.Baseline
import com.personalfitnesscoach.engine.calc.CheckIn
import com.personalfitnesscoach.engine.calc.Deload
import com.personalfitnesscoach.engine.calc.DeloadAction
import com.personalfitnesscoach.engine.calc.Readiness
import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.LoadType
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.program.Blueprint
import com.personalfitnesscoach.engine.program.WeekKind
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.Registry
import com.personalfitnesscoach.engine.safety.PainKind
import com.personalfitnesscoach.engine.safety.RedFlags
import com.personalfitnesscoach.engine.safety.SafetyStop

/** The decision log (DecisionLog, "Why?"): appends engine decisions with an ever-increasing sequence number. */
class DecisionLog(private val docs: Docs, private val store: RowStore, private val clock: AppClock) {
    private var lastSeq: Long? = null

    private suspend fun nextSeq(): Long {
        val last = lastSeq ?: (store.docs(DecisionEntry.type).maxOfOrNull { it.key.toLongOrNull() ?: 0L } ?: 0L)
        val next = maxOf(last + 1, clock.nowMs() * 1000)
        lastSeq = next
        return next
    }

    suspend fun append(decisions: List<Decision>, day: Int = clock.today(), workoutId: Long? = null) {
        if (decisions.isEmpty()) return
        val now = clock.nowMs()
        for (d in decisions) docs.put(DecisionEntry, DecisionEntry.from(d, nextSeq(), now, day, workoutId, Registry.VERSION))
    }

    suspend fun forDay(day: Int): List<DecisionEntry> = docs.between(DecisionEntry, day, day)
    suspend fun forWorkout(workoutId: Long, day: Int): List<DecisionEntry> = forDay(day).filter { it.workoutId == workoutId }

    /** After a restore or an erase the sequence restarts from what is stored. */
    fun reset() { lastSeq = null }
}

/**
 * History → per-exercise state after each finished workout (FS-5), and a full rebuild from the logged sets (used after a past set
 * is corrected or the equipment changed; the incremental and rebuilt states are the same, which the tests check).
 */
class ProgressRecorder(private val docs: Docs, private val log: SessionLog, private val bridge: EngineBridge) {

    suspend fun record(w: StoredWorkout, u: UserState): List<Decision> {
        check(w.row.status == Status.DONE) { "only finished workouts change training state" }
        val readiness = docs.get(ReadinessRecord, dayKey(w.day))
        val tier = w.row.tier?.let { Tier.valueOf(it) } ?: Tier.FULL
        val ctx = bridge.progressContext(u, recoveryOk = (tier == Tier.FULL || tier == Tier.MODIFIED) && (readiness?.signals?.size ?: 0) < 2)
        val d = ArrayList<Decision>()
        val ladders = LinkedHashMap<String, String>()
        for (e in w.exercises) {
            if (e.row.status != Status.DONE) continue
            val ex = Library[e.exerciseId] ?: continue
            val state = docs.get(ExerciseState, ex.id) ?: ExerciseState(ex.id)
            val ladderType = ex.assisted || ex.loadType == LoadType.BODYWEIGHT || ex.loadType == LoadType.TIME || ex.loadType == LoadType.DISTANCE
            val recent = if (!ladderType) listOf(e) else log.history(ex.id, w.day - 56, w.day)
                .filter { (day, x) -> day < w.day || (day == w.day && x.row.workoutId <= w.id) }.map { it.second }.takeLast(P.BW_002.consecutive_sessions)
            val r = ExerciseProgress.apply(state, ex, e, w.day, ctx, docs.get(KnownNumber, ex.id), recent.ifEmpty { listOf(e) })
            docs.put(ExerciseState, r.value.state)
            r.value.ladder?.let { (fam, id) -> ladders[fam] = id }
            d += r.decisions
        }
        val hiit = bridge.doneSession(w).hiitBlocks
        if (hiit > 0 || ladders.isNotEmpty()) docs.update(ProgramRecord) { p ->
            checkNotNull(p) { "no programme" }.copy(hiitDoneEver = p.hiitDoneEver + if (hiit > 0) 1 else 0, ladderRungs = p.ladderRungs + ladders)
        }
        return d
    }

    /** Recomputes every exercise's state from all finished workouts, oldest first (ladder rungs from the plan are kept). */
    suspend fun rebuild(u: UserState) {
        docs.deleteAll(ExerciseState)
        val program = docs.get(ProgramRecord) ?: return
        docs.put(ProgramRecord, program.copy(hiitDoneEver = 0))
        for (w in log.done(Int.MIN_VALUE / 2, Int.MAX_VALUE / 2)) record(w, u)
    }
}

/** What the daily check-in produced (RDY-001…007, SAF-002, SAF-007). */
data class CheckInOutcome(val record: ReadinessRecord, val safetyStop: SafetyStop?, val decisions: List<Decision>)

/** The daily check-in: readiness tier with the personal baseline and fatigue signals, red flags and the illness gate. */
class CheckIns(private val docs: Docs, private val bridge: EngineBridge, private val clock: AppClock) {
    suspend fun record(u: UserState, checkIn: CheckIn, minutesAvailable: Int? = null, redFlags: Set<String> = emptySet(),
                       illnessSymptoms: Set<String> = emptySet(), requestedTier: Tier? = null): CheckInOutcome {
        val today = u.today
        val d = ArrayList<Decision>()
        val baseline = Baseline.of(docs.between(ReadinessRecord, today - P.RDY_002.window_days, today - 1).map { it.rRaw })
        val signals = bridge.fatigue(u, docs.get(ProgramRecord)).second
        val r = Readiness.tier(checkIn, baseline, signals.size).also { d += it.decisions }.value
        val stop = RedFlags.check(redFlags).also { d += it.decisions }.value
        val illness = RedFlags.illnessGate(illnessSymptoms).also { d += it.decisions }.value
        var tier = r.tier
        if (illness != null && illness.ordinal < tier.ordinal) tier = illness
        val locked = stop != null || illness != null || u.flaggedScreen || bridge.painToday(today).first.isNotEmpty()
        val chosen = requestedTier?.let { Readiness.userChoice(tier, it, locked).also { x -> d += x.decisions }.value }?.takeIf { it != tier }
        val rec = ReadinessRecord(today, checkIn, minutesAvailable, r.rRaw, r.r, tier, chosen, signals.map { it.name }.toSet(), redFlags, illnessSymptoms,
            clock.nowMs())
        docs.put(ReadinessRecord, rec)
        return CheckInOutcome(rec, stop, d)
    }
}

/**
 * The weekly rollover, run when the app opens (Phase 2: no background jobs): closes every finished calendar week — totals, the
 * PER-005 block clock, DEL-002 for the coming week, AER-003 Z1 length, STEP-001 target — then moves the programme to this week.
 */
class ProgramClock(private val docs: Docs, private val log: SessionLog, private val bridge: EngineBridge, private val clock: AppClock) {

    /** Starts the programme from the profile's priorities (once; a new start keeps history but resets the clock). */
    suspend fun start(today: Int = clock.today()): ProgramRecord {
        val p = checkNotNull(bridge.user(today)) { "no profile" }.profile
        val rec = ProgramRecord(p.priorities, startDay = today, weekStartDay = Days.weekStart(today))
        docs.put(ProgramRecord, rec)
        return rec
    }

    suspend fun rollover(today: Int = clock.today()): EngineResult<ProgramRecord?> {
        var prog = docs.get(ProgramRecord) ?: return EngineResult(null)
        val u = bridge.user(today) ?: return EngineResult(prog)
        val thisWeek = Days.weekStart(today)
        val d = ArrayList<Decision>()
        var guard = 0
        while (prog.weekStartDay < thisWeek && guard++ < 520) {
            val ws = prog.weekStartDay
            val we = ws + 6
            val planned = docs.get(WeekPlanRecord, dayKey(ws))?.planned ?: 0
            val t = bridge.totals(ws, we)
            val lastSession = log.done(ws - 365, we).lastOrNull()?.day
            val breakDays = if (lastSession == null) we - prog.startDay + 1 else we - lastSession
            val disruption = if (t.completed == 0 && breakDays >= 14) Blueprint.Disruption.BREAK else Blueprint.Disruption.NONE
            val program = bridge.blueprint(prog)
            val step = Blueprint.advanceClock(program, prog.clockWeek, planned, t.completed, disruption, breakDays).also { d += it.decisions }.value
            docs.put(WeekSummary, WeekSummary(ws, prog.clockWeek, planned, t.completed, t.aerobicMinutes, t.z1Minutes, t.z2PlusMinutes, t.hiitSessions,
                t.hiitWorkMinutes, t.walkingMinutes, t.equivalentMinutes, t.ssu, t.workload, prog.deloadThisWeek, step.action))

            val nextCtx = Blueprint.context(program, step.nextClockWeek)
            val newBlock = nextCtx.blockIndex != prog.blockIndex
            // DEL-002 for the coming week: at a block's deload-or-pivot week, or now when the signals call for it.
            val signals = bridge.fatigue(u.copy(today = we + 1), prog).second.size
            val deloadAction = Deload.decide(signals, recentMultiSignalDays(we), atBlockEnd = nextCtx.kind == WeekKind.DELOAD_OR_PIVOT,
                prog.weeksSinceLighter, u.age, prog.justFinishedLighterWeek).also { d += it.decisions }.value
            val deload = deloadAction == DeloadAction.DELOAD_NOW || (deloadAction == DeloadAction.DELOAD_AT_BLOCK_END && nextCtx.kind == WeekKind.DELOAD_OR_PIVOT)
            val lighter = deload || deloadAction == DeloadAction.LIGHTER_WEEK
            val finishedWasDeload = prog.deloadThisWeek
            prog = prog.copy(
                weekStartDay = ws + 7, clockWeek = step.nextClockWeek, weeksTraining = prog.weeksTraining + if (t.completed > 0) 1 else 0,
                blockIndex = nextCtx.blockIndex, coreLifts = if (newBlock) emptyMap() else prog.coreLifts,
                previousBlockChoices = if (newBlock) prog.coreLifts else prog.previousBlockChoices,
                deloadThisWeek = deload, weeksSinceLighter = if (lighter) 0 else prog.weeksSinceLighter + 1,
                justFinishedLighterWeek = prog.deloadThisWeek, z1SessionMinutes = if (t.longestZ1Block > 0) t.longestZ1Block else prog.z1SessionMinutes,
                carryOrRotationLastWeek = t.carryOrRotation, lastClockAction = step.action,
            )
            stepWeek(u.copy(today = we + 1), ws, finishedWasDeload)?.let { d += it }
        }
        docs.put(ProgramRecord, prog)
        return EngineResult(prog, d)
    }

    /** Days in the last 14 whose check-in had 2 or more fatigue signals (DEL-002 "sessions with ≥ 2 signals"). */
    private suspend fun recentMultiSignalDays(weekEnd: Int): Int = docs.between(ReadinessRecord, weekEnd - 13, weekEnd).count { it.signals.size >= 2 }

    /** STEP-001: starts the step target once a baseline exists, then moves it once a week (steps tracked only when switched on). */
    private suspend fun stepWeek(u: UserState, weekStart: Int, deloadWeek: Boolean): List<Decision>? {
        if (docs.get(SettingsRecord)?.stepTracking != true) return null
        val cur = docs.get(StepStateRecord)
        if (cur != null && cur.weekStartDay > weekStart) return null
        val days = docs.between(StepsRecord, weekStart - 28, weekStart + 6).map { DaySteps(it.day, it.steps) }
        val state = StepState(cur?.baseline, cur?.target, cur?.lowWeeks ?: 0)
        val lowerLimbPain = docs.between(PainRecord, weekStart, weekStart + 6).any {
            it.report.kind == PainKind.JOINT_OR_TENDON && it.report.region in setOf(Joint.HIP, Joint.KNEE, Joint.ANKLE) }
        val r = if (state.target == null) StepTarget.start(state, days, u.age)
            else StepTarget.nextWeek(state, days.filter { it.day in weekStart..weekStart + 6 }, u.age, deloadWeek, lowerLimbPain)
        docs.put(StepStateRecord, StepStateRecord(weekStart + 7, r.value.baseline, r.value.target, r.value.lowWeeks))
        return r.decisions
    }
}
