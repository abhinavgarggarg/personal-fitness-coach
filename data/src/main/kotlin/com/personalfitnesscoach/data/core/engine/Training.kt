package com.personalfitnesscoach.data.core.engine

import com.personalfitnesscoach.data.core.model.ConditionsRecord
import com.personalfitnesscoach.engine.safety.Conditions

import com.personalfitnesscoach.data.core.model.DecisionEntry
import com.personalfitnesscoach.data.core.model.ExerciseState
import com.personalfitnesscoach.data.core.model.KnownNumber
import com.personalfitnesscoach.data.core.model.PainRecord
import com.personalfitnesscoach.data.core.model.ProgramRecord
import com.personalfitnesscoach.data.core.model.ReadinessRecord
import com.personalfitnesscoach.data.core.model.SafetyStopRecord
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
import com.personalfitnesscoach.engine.program.Frequency
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
        // A session ended by a red-flag stop counts towards caps and spacing (it is DONE) but never changes progression (SAF-002).
        if (w.doc.stoppedBySafety) return emptyList()
        val readiness = docs.get(ReadinessRecord, dayKey(w.day))
        val tier = w.row.tier?.let { Tier.valueOf(it) } ?: Tier.FULL
        val ctx = bridge.progressContext(u, recoveryOk = (tier == Tier.FULL || tier == Tier.MODIFIED) && (readiness?.signals?.size ?: 0) < 2)
        val d = ArrayList<Decision>()
        val ladders = LinkedHashMap<String, String>()
        for (e in w.exercises) {
            if (e.row.status != Status.DONE) continue
            val ex = Library[e.exerciseId] ?: continue
            val stored = docs.get(ExerciseState, ex.id) ?: ExerciseState(ex.id)
            // A calibrating exposure of an exercise that had an e1RM is a recalibration after a long layoff (REG-004): start afresh,
            // keeping the count and the "own number used" flag.
            val state = if (e.doc.calibrating && (stored.e1rm != null || stored.prescription != null))
                ExerciseState(ex.id, exposures = stored.exposures, knownApplied = true, lastDoneDay = stored.lastDoneDay) else stored
            val ladderType = ex.assisted || ex.loadType == LoadType.BODYWEIGHT || ex.loadType == LoadType.TIME || ex.loadType == LoadType.DISTANCE
            val recent = if (!ladderType) listOf(e) else log.history(ex.id, w.day - 56, w.day)
                .filter { (day, x) -> day < w.day || (day == w.day && x.row.workoutId <= w.id) }.map { it.second }.takeLast(P.BW_002.consecutive_sessions)
            val r = ExerciseProgress.apply(state, ex, e, w.day, ctx, docs.get(KnownNumber, ex.id), recent.ifEmpty { listOf(e) })
            docs.put(ExerciseState, r.value.state)
            r.value.ladder?.let { (fam, id) -> ladders[fam] = id }
            d += r.decisions
        }
        val hiit = bridge.doneSession(w).hiitBlocks
        docs.get(ProgramRecord)?.let { p ->
            val next = p.copy(hiitDoneEver = p.hiitDoneEver + if (hiit > 0) 1 else 0, ladderRungs = p.ladderRungs + ladders,
                lighterSessionsLeft = maxOf(0, p.lighterSessionsLeft - 1))
            if (next != p) docs.put(ProgramRecord, next)
        }
        return d
    }

    /**
     * Recomputes every exercise's state from all finished workouts, oldest first (ladder rungs from the plan are kept). A DEL-002
     * lighter week in progress is not history to recompute: its remaining sessions are kept (re-check finding 6).
     */
    suspend fun rebuild(u: UserState) {
        docs.deleteAll(ExerciseState)
        val program = docs.get(ProgramRecord) ?: return
        docs.put(ProgramRecord, program.copy(hiitDoneEver = 0))
        for (w in log.done(Int.MIN_VALUE / 2, Int.MAX_VALUE / 2)) record(w, u)
        docs.get(ProgramRecord)?.let { docs.put(ProgramRecord, it.copy(lighterSessionsLeft = program.lighterSessionsLeft, lighterThisWeek = program.lighterThisWeek)) }
    }
}

/** What the daily check-in produced (RDY-001…007, SAF-002, SAF-007, REG, DEL-002). */
data class CheckInOutcome(val record: ReadinessRecord, val safetyStop: SafetyStop?, val decisions: List<Decision>)

/**
 * The daily check-in: readiness tier with the personal baseline and fatigue signals, then every safety limit stored for today —
 * red flags (a stop is kept until the user confirms it resolved), the illness gate, the return rules and a lighter week — and a
 * mid-block DEL-002 "deload now" when the signals call for it. The stored tier is the final, capped tier.
 */
class CheckIns(private val docs: Docs, private val bridge: EngineBridge, private val clock: AppClock) {
    suspend fun record(u: UserState, checkIn: CheckIn, minutesAvailable: Int? = null, redFlags: Set<String> = emptySet(),
                       illnessSymptoms: Set<String> = emptySet(), requestedTier: Tier? = null): CheckInOutcome {
        val today = u.today
        val d = ArrayList<Decision>()
        val program = docs.get(ProgramRecord)
        val baseline = Baseline.of(docs.between(ReadinessRecord, today - P.RDY_002.window_days, today - 1).map { it.rRaw })
        val signals = bridge.fatigue(u, program).second
        val r = Readiness.tier(checkIn, baseline, signals.size).also { d += it.decisions }.value
        // SAF-002: new red flags start a stop that lasts until confirmed.
        var stop = RedFlags.check(redFlags).also { d += it.decisions }.value
        stop?.let { startStop(it, today) }
        val illness = RedFlags.illnessGate(illnessSymptoms).also { d += it.decisions }.value
        val gate = bridge.gate(u, program).also { d += it.decisions }
        if (stop == null) stop = gate.stop
        var tier = gate.cap(r.tier)
        if (illness != null) tier = Tier.min(tier, illness)
        if (stop != null) tier = Tier.RECOVERY
        val locked = stop != null || illness != null || gate.locked || u.flaggedScreen || bridge.painToday(today).first.isNotEmpty()
        val chosen = requestedTier?.let { Readiness.userChoice(tier, it, locked).also { x -> d += x.decisions }.value }?.takeIf { it != tier }
        val rec = ReadinessRecord(today, checkIn, minutesAvailable, r.rRaw, r.r, tier, chosen, signals.map { it.name }.toSet(), redFlags, illnessSymptoms,
            clock.nowMs())
        docs.put(ReadinessRecord, rec)
        // DEL-002 between block ends: enough signals now → the rest of this week is a deload (the block clock waits for it).
        if (program != null && !program.deloadThisWeek) {
            val multi = docs.between(ReadinessRecord, today - 13, today).count { it.signals.size >= 2 }
            val a = Deload.decide(signals.size, multi, atBlockEnd = false, program.weeksSinceLighter, u.age, justFinishedLighterWeek = false)
            if (a.value == DeloadAction.DELOAD_NOW) {
                d += a.decisions
                docs.put(ProgramRecord, program.copy(deloadThisWeek = true, weeksSinceLighter = 0))
            }
        }
        return CheckInOutcome(rec, stop, d)
    }

    /** Keeps a SAF-002 stop until the user confirms it resolved; symptoms reported again while one is open are added to it. */
    suspend fun startStop(stop: SafetyStop, today: Int) {
        val cur = docs.get(SafetyStopRecord)
        val open = cur != null && cur.confirmedDay == null
        docs.put(SafetyStopRecord, SafetyStopRecord(if (open) minOf(cur!!.day, today) else today, if (open) cur!!.symptoms + stop.symptoms else stop.symptoms))
    }

    /** The user confirms the red-flag symptoms have resolved or were reviewed; the next session is LIGHT at most (SAF-002). */
    suspend fun confirmStopResolved(): Boolean {
        val s = docs.get(SafetyStopRecord) ?: return false
        if (s.confirmedDay != null) return false
        docs.put(SafetyStopRecord, s.copy(confirmedDay = clock.today()))
        return true
    }
}

/**
 * The weekly rollover, run when the app opens (Phase 2: no background jobs): closes every finished calendar week — totals, the
 * PER-005 block clock, DEL-002 for the coming week, AER-003 Z1 length, STEP-001 target — then moves the programme to this week.
 */
class ProgramClock(private val docs: Docs, private val log: SessionLog, private val bridge: EngineBridge, private val clock: AppClock) {
    companion object {
        /** Days of decision history kept. */
        const val DECISION_DAYS = 400
    }

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
            val planRec = docs.get(WeekPlanRecord, dayKey(ws))
            // A week nobody opened the app still had sessions planned: the usual number (PER-005 must not advance through it).
            val planned = planRec?.planned ?: Frequency.trainingDays(u.profile.daysPerWeek)
            val t = bridge.totals(ws, we)
            val lastSession = log.done(ws - 365, we).lastOrNull()?.day
            val breakDays = if (lastSession == null) we - prog.startDay + 1 else we - lastSession
            val disruption = if (t.completed == 0 && breakDays >= 14) Blueprint.Disruption.BREAK else Blueprint.Disruption.NONE
            val program = bridge.blueprint(prog)
            val ctx = Blueprint.context(program, prog.clockWeek)
            // What the finished week actually was: the plan's own deload flag, and a lighter week if one ran.
            val wasDeload = planRec?.deload ?: prog.deloadThisWeek
            val wasLighter = wasDeload || prog.lighterThisWeek
            val step = if (prog.insertedDeload(ctx) && wasDeload) Blueprint.ClockStep(prog.clockWeek, Blueprint.ClockAction.PAUSE) // the block waits for an inserted deload
                else Blueprint.advanceClock(program, prog.clockWeek, planned, t.completed, disruption, breakDays).also { d += it.decisions }.value
            docs.put(WeekSummary, WeekSummary(ws, prog.clockWeek, planned, t.completed, t.aerobicMinutes, t.z1Minutes, t.z2PlusMinutes, t.hiitSessions,
                t.hiitWorkMinutes, t.walkingMinutes, t.equivalentMinutes, t.ssu, t.workload, wasDeload, step.action))

            val nextCtx = Blueprint.context(program, step.nextClockWeek)
            val newBlock = nextCtx.blockIndex != prog.blockIndex
            // DEL-002 for the coming week: at a block's deload-or-pivot week, or now when the signals call for it.
            val signals = bridge.fatigue(u.copy(today = we + 1), prog).second.size
            val deloadAction = Deload.decide(signals, recentMultiSignalDays(we), atBlockEnd = nextCtx.kind == WeekKind.DELOAD_OR_PIVOT,
                prog.weeksSinceLighter, u.age, justFinishedLighterWeek = wasLighter).also { d += it.decisions }.value
            val deload = deloadAction == DeloadAction.DELOAD_NOW || (deloadAction == DeloadAction.DELOAD_AT_BLOCK_END && nextCtx.kind == WeekKind.DELOAD_OR_PIVOT)
            val lighter = !deload && deloadAction == DeloadAction.LIGHTER_WEEK
            prog = prog.copy(
                weekStartDay = ws + 7, clockWeek = step.nextClockWeek, weeksTraining = prog.weeksTraining + if (t.completed > 0) 1 else 0,
                blockIndex = nextCtx.blockIndex, coreLifts = if (newBlock) emptyMap() else prog.coreLifts,
                previousBlockChoices = if (newBlock) prog.coreLifts else prog.previousBlockChoices, blockStartDay = if (newBlock) ws + 7 else prog.blockStartDay,
                deloadThisWeek = deload, lighterSessionsLeft = if (lighter) P.DEL_002.lighter_week_sessions else 0, lighterThisWeek = lighter,
                weeksSinceLighter = if (deload || lighter) 0 else prog.weeksSinceLighter + 1,
                justFinishedLighterWeek = wasLighter,
                // AER-003: the next Z1 length grows from the one planned in a week that was trained.
                z1SessionMinutes = if (t.completed > 0) planRec?.z1SessionMinutes ?: prog.z1SessionMinutes else prog.z1SessionMinutes,
                carryOrRotationLastWeek = t.carryOrRotation, lastClockAction = step.action,
            )
            stepWeek(u.copy(today = we + 1), ws, wasDeload)?.let { d += it }
            painRuleWeek(ws, we, t.completed)
        }
        docs.put(ProgramRecord, prog)
        // The decision log keeps about a year ("Why?" for recent changes); older entries are removed.
        for (old in docs.between(DecisionEntry, Int.MIN_VALUE / 2, today - DECISION_DAYS)) docs.delete(DecisionEntry, DecisionEntry.key(old))
        return EngineResult(prog, d)
    }

    /**
     * SAF-010 pain rule (knee and hip arthritis): a trained week meets it when no pain report for the condition's joints was above the
     * table's limit during the week or the next morning; it counts towards the unlocks that need it, and a week that breaks it starts the
     * count again (review R5-12). A week without training leaves the count as it was.
     */
    private suspend fun painRuleWeek(ws: Int, we: Int, completed: Int) {
        val rec = docs.get(ConditionsRecord) ?: return
        var changed = false
        val items = rec.items.map { c ->
            val e = Conditions[c.id] ?: return@map c
            val max = e.painRuleDuringMax ?: return@map c
            if (!e.impactUnlockNeedsPainRule && !e.jointLimitUnlockNeedsPainRule) return@map c
            val joints = e.jointLimits.keys + e.jointLimitUnlock.keys
            val broken = docs.between(PainRecord, ws, we + 1).any { it.report.region in joints && it.report.rating > max }
            val n = when { broken -> 0; completed > 0 -> c.painRuleMetWeeks + 1; else -> c.painRuleMetWeeks }
            if (n != c.painRuleMetWeeks) { changed = true; c.copy(painRuleMetWeeks = n) } else c
        }
        if (changed) docs.put(ConditionsRecord, rec.copy(items = items))
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
