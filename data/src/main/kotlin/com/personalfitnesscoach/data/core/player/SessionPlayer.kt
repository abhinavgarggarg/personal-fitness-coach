package com.personalfitnesscoach.data.core.player

import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.model.ExerciseState
import com.personalfitnesscoach.data.core.model.PreferencesRecord
import com.personalfitnesscoach.data.core.model.Side
import com.personalfitnesscoach.data.core.session.ActiveState
import com.personalfitnesscoach.data.core.session.BoneRef
import com.personalfitnesscoach.data.core.session.ConditioningItem
import com.personalfitnesscoach.data.core.session.DrillRef
import com.personalfitnesscoach.data.core.session.ItemDoc
import com.personalfitnesscoach.data.core.session.NewSet
import com.personalfitnesscoach.data.core.session.RampRef
import com.personalfitnesscoach.data.core.session.SetKind
import com.personalfitnesscoach.data.core.session.Sheet
import com.personalfitnesscoach.data.core.session.Stage
import com.personalfitnesscoach.data.core.session.Status
import com.personalfitnesscoach.data.core.session.StoredExercise
import com.personalfitnesscoach.data.core.session.StoredWorkout
import com.personalfitnesscoach.data.core.store.SetRow
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.data.core.engine.ExerciseProgress
import com.personalfitnesscoach.engine.calc.PlateMath
import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.dose.Effort
import com.personalfitnesscoach.engine.dose.Rest
import com.personalfitnesscoach.engine.dose.RestWindow
import com.personalfitnesscoach.engine.generation.GenerationRequest
import com.personalfitnesscoach.engine.generation.SessionGenerator
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.Exercise
import com.personalfitnesscoach.engine.model.FormCheck
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.LoadType
import com.personalfitnesscoach.engine.model.SetLog
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.planning.Candidate
import com.personalfitnesscoach.engine.planning.PlanItem
import com.personalfitnesscoach.engine.planning.SessionPlan
import com.personalfitnesscoach.engine.planning.Substitution
import com.personalfitnesscoach.engine.planning.TimeBudget
import com.personalfitnesscoach.engine.planning.TimeModel
import com.personalfitnesscoach.engine.program.PlannedConditioning
import com.personalfitnesscoach.engine.program.PlannedDay
import com.personalfitnesscoach.engine.program.PlannedSlot
import com.personalfitnesscoach.engine.program.SlotRole
import com.personalfitnesscoach.engine.program.SlotSpec
import com.personalfitnesscoach.engine.program.SrpePrompt
import com.personalfitnesscoach.engine.program.WeekPlanner
import com.personalfitnesscoach.engine.progression.Autoregulation
import com.personalfitnesscoach.engine.progression.Calibration
import com.personalfitnesscoach.engine.progression.CalibrationStep
import com.personalfitnesscoach.engine.progression.Progression
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds
import com.personalfitnesscoach.engine.safety.AdditionVerdict
import com.personalfitnesscoach.engine.safety.ConditioningBlock
import com.personalfitnesscoach.engine.safety.PainAction
import com.personalfitnesscoach.engine.safety.PainOutcome
import com.personalfitnesscoach.engine.safety.PainReport
import com.personalfitnesscoach.engine.safety.SafetyStop
import com.personalfitnesscoach.engine.safety.Session
import com.personalfitnesscoach.engine.safety.SessionExercise
import com.personalfitnesscoach.engine.safety.SessionValidator
import com.personalfitnesscoach.engine.safety.UserAdditions
import com.personalfitnesscoach.engine.safety.Violation

/** The next set the player asks for (Phase 2 A1): ramp-up (WU-002), calibration ramp (CAL-001) or working set. */
data class SetTarget(
    val kind: String,
    /** 1-based within its kind. */
    val number: Int,
    /** Planned count of this kind; for a calibration ramp the most it can take (CAL-001: ≤ 5). */
    val of: Int,
    /** kg (assistance kg for assisted moves); null = bodyweight or unloaded. */
    val load: Double?,
    /** Reps, or seconds when [unit] is SECONDS. */
    val reps: IntRange,
    val unit: DoseUnit,
    val perSide: Boolean,
    /** Target reps in reserve for a working set (0 on a last set to failure); null for ramp-up sets. */
    val targetRir: Double?,
    /** PROG-008: ask "form felt solid?" after this set (last working set of a main lift or a new exercise). */
    val askForm: Boolean,
    /** The user added this set (SAF-006). */
    val userAdded: Boolean = false,
)

/** One exercise of the workout as the player shows it. */
data class LiftView(
    val rowId: Long,
    val position: Int,
    val exercise: Exercise,
    val item: ItemDoc,
    val status: String,
    val sets: List<SetRow>,
    /** Null when the exercise is finished (or skipped) for today. */
    val next: SetTarget?,
    /** Working sets from the last finished exposure ("last time", Phase 2 A1). */
    val lastTime: List<SetRow>,
    /** The calibration ramp ended today (found, stopped or capped). */
    val calibrationDone: Boolean,
) {
    val finished: Boolean get() = next == null
    val workingDone: Int get() = sets.count { it.kind == SetKind.WORKING }
    val hasLoggedWork: Boolean get() = sets.any { it.kind != SetKind.WARMUP }
}

/** What the player shows now. */
sealed interface Step {
    data class Warmup(val minutes: Double, val drills: List<DrillRef>) : Step
    data class BoneLoading(val block: BoneRef) : Step
    data class Lift(val lift: LiftView) : Step
    data class Conditioning(val index: Int, val item: ConditioningItem) : Step
    data class Balance(val minutes: Double, val drills: List<DrillRef>) : Step
    data class Cooldown(val minutes: Double, val drills: List<DrillRef>, val mobilityMinutes: Double) : Step
    /** Everything is done or skipped: the summary is next. */
    data object Done : Step
}

data class PlayerView(
    val workout: StoredWorkout,
    val state: ActiveState,
    val stages: List<Stage>,
    val stage: Stage?,
    val step: Step,
    val lifts: List<LiftView>,
    /** Minutes since the start, pauses over 10 minutes removed (LOAD-001). */
    val elapsedMinutes: Double,
    /** TIME-004 estimate of the work still to do (minutes). */
    val remainingMinutes: Double,
    val restEndsAtMs: Long?,
    /** REST rule window for the exercise just logged (REST-007 status: too short, in range, alert, long). */
    val restWindow: RestWindow?,
    val tier: Tier,
    /** INT-006: how effort is asked (beginners answer "how many more could you have done?"). */
    val effortPrompt: Effort.PromptStyle,
    val paused: Boolean,
) {
    val workoutId: Long get() = workout.id
    val conditioning: List<ConditioningItem> get() = workout.doc.conditioning
}

/** What the user typed or tapped for one set. */
data class LiftEntry(
    val load: Double?,
    val reps: Int?,
    val seconds: Int? = null,
    /** Reps in reserve ("how many more could you have done?"); 5.0 = "5+" during calibration. */
    val rir: Double? = null,
    val form: FormCheck = FormCheck.YES,
)

/** The result of logging one set. */
data class LogResult(val view: PlayerView, val decisions: List<Decision>, val calibration: CalibrationStep? = null, val restSec: Int?)

/** SUB-002 swap options for one exercise, and EQ-002 "do it later" when other exercises are still to come. */
data class SwapChoice(val rowId: Long, val original: Exercise, val options: List<Candidate>, val canDoLater: Boolean, val decisions: List<Decision>)

/** What changed in the session (shown as a list: "what moved where", FS-6/FS-7). */
sealed interface Change {
    data class Skipped(val rowId: Long, val exerciseId: String) : Change
    data class SetsReduced(val rowId: Long, val exerciseId: String, val from: Int, val to: Int) : Change
    data class Swapped(val rowId: Long, val from: String, val to: String) : Change
    data class LoadLowered(val rowId: Long, val exerciseId: String, val from: Double, val to: Double) : Change
    data class ConditioningShortened(val index: Int, val fromMinutes: Double, val toMinutes: Double) : Change
    data class ConditioningDropped(val index: Int) : Change
    data class Added(val rowId: Long, val exerciseId: String) : Change
}

/** SAF-003 in a session: the gate's outcome, what changed, and pain-free alternatives for the stopped exercise. */
data class PainResult(val outcome: PainOutcome, val changes: List<Change>, val alternatives: List<Candidate>, val endSession: Boolean,
                      val decisions: List<Decision>, val view: PlayerView?)

data class ReplanResult(val changes: List<Change>, val decisions: List<Decision>, val view: PlayerView, val refused: Boolean = false)

data class AddSetResult(val verdict: AdditionVerdict, val decisions: List<Decision>, val view: PlayerView)

/** A new personal best (INT-004 estimated max up), shown on the summary (ADH-002). */
data class Record(val exerciseId: String, val before: Double?, val after: Double)

/** The session summary (Phase 2 A8). */
data class Summary(
    val workout: StoredWorkout,
    val records: List<Record>,
    /** What the engine decided during and after the session ("What changed"). */
    val decisions: List<com.personalfitnesscoach.data.core.model.DecisionEntry>,
    /** LOAD-002: ask the session rating now, or later (10+ minutes after the last hard effort). */
    val ask: SrpePrompt.Ask,
    val minutes: Double,
    val workingSets: Int,
)

/**
 * The guided workout (Phase 2 FS-4 to FS-7, A1–A8): what to do next, set logging with in-session autoregulation (INT-007) and the
 * calibration ramp (CAL-001), rest, swaps (SUB-001/002, EQ-002), "something hurts" (SAF-003), red flags (SAF-002), changing the time
 * left (TIME-001…004), added sets (SAF-006) and the summary (LOAD-001/002). Every change is saved at once with the resume point, and
 * every re-plan is re-validated (SAF-008) before it is shown.
 */
class SessionPlayer(private val d: PfcData) {

    // ------------------------------------------------------------------------------------------------ reading
    suspend fun view(): PlayerView? = d.store.transaction {
        val (w, a) = d.sessions.active() ?: return@transaction null
        build(w, ActiveState.decode(a.json))
    }

    private suspend fun build(w: StoredWorkout, s0: ActiveState): PlayerView {
        val now = d.clock.nowMs()
        val doc = w.doc
        val lifts = w.exercises.sortedBy { it.row.position }.mapNotNull { e -> liftView(w, e, s0) }
        val stages = stagesOf(w, lifts)
        // A reset resume point (a restore resets it) never sends the user back: the logged work shows how far they got.
        val inferred = when {
            lifts.any { it.hasLoggedWork } -> Stage.LIFTS
            s0.conditioningDone.isNotEmpty() -> if (doc.conditioningFirst) Stage.CONDITIONING_FIRST else Stage.CONDITIONING
            else -> Stage.WARMUP
        }
        val s = if (s0.stage.ordinal < inferred.ordinal && inferred in stages) s0.copy(stage = inferred) else s0
        val stage = stages.firstOrNull { it.ordinal >= s.stage.ordinal && !complete(it, s, lifts, doc.conditioning) }
        val step = when (stage) {
            null -> Step.Done
            Stage.WARMUP -> Step.Warmup(doc.warmupMinutes, doc.warmupDrills)
            Stage.BONE_LOADING -> Step.BoneLoading(doc.boneLoading!!)
            Stage.CONDITIONING_FIRST, Stage.CONDITIONING -> {
                val i = doc.conditioning.indices.first { it !in s.conditioningDone }
                Step.Conditioning(i, doc.conditioning[i])
            }
            Stage.LIFTS -> Step.Lift(lifts.firstOrNull { it.rowId == s.currentRowId && !it.finished } ?: lifts.first { !it.finished })
            Stage.BALANCE -> Step.Balance(doc.balanceMinutes, doc.balanceDrills)
            Stage.COOLDOWN -> Step.Cooldown(doc.cooldownMinutes ?: 0.0, doc.cooldownDrills, doc.mobilityMinutes)
            Stage.END -> Step.Done
        }
        val start = w.row.startedAtMs ?: now
        val pausedNow = s.pausedAtMs?.let { p -> (now - p).takeIf { it > PAUSE_EXCLUDED_MS } ?: 0L } ?: 0L
        val elapsed = ((now - start - s.pausedMs - pausedNow) / 60_000.0).coerceIn(0.0, 600.0)
        val u = d.bridge.user()
        val program = d.docs.get(com.personalfitnesscoach.data.core.model.ProgramRecord)
        val prompt = Effort.promptStyle(u?.profile?.level ?: com.personalfitnesscoach.engine.model.Level.BEGINNER, program?.weeksTraining ?: 0)
        val restWin = s.currentRowId?.let { id -> lifts.firstOrNull { it.rowId == id } }?.item?.let { RestWindow(it.restMinSec, it.restDefaultSec, it.restMaxSec, "") }
        return PlayerView(w, s, stages, stage, step, lifts, elapsed, remainingMinutes(w, lifts, s, stage), s.restEndsAtMs()?.takeIf { it > now },
            restWin, Tier.valueOf(w.row.tier ?: Tier.FULL.name), prompt, s.pausedAtMs != null)
    }

    private fun stagesOf(w: StoredWorkout, lifts: List<LiftView>): List<Stage> {
        val doc = w.doc
        val out = ArrayList<Stage>()
        out += Stage.WARMUP
        if (doc.boneLoading != null) out += Stage.BONE_LOADING
        if (doc.conditioning.isNotEmpty() && doc.conditioningFirst) out += Stage.CONDITIONING_FIRST
        if (lifts.isNotEmpty()) out += Stage.LIFTS
        if (doc.conditioning.isNotEmpty() && !doc.conditioningFirst) out += Stage.CONDITIONING
        if (doc.balanceDrills.isNotEmpty()) out += Stage.BALANCE
        out += Stage.COOLDOWN
        return out
    }

    private fun complete(stage: Stage, s: ActiveState, lifts: List<LiftView>, cond: List<ConditioningItem>): Boolean = when (stage) {
        Stage.LIFTS -> lifts.all { it.finished }
        Stage.CONDITIONING_FIRST, Stage.CONDITIONING -> cond.indices.all { it in s.conditioningDone }
        else -> s.stage.ordinal > stage.ordinal
    }

    private suspend fun liftView(w: StoredWorkout, e: StoredExercise, s: ActiveState): LiftView? {
        val ex = Library[e.exerciseId] ?: return null
        val last = d.sessions.history(ex.id, w.day - 365, w.day).lastOrNull { (day, x) -> x.row.workoutId != w.id && day <= w.day }?.second?.working ?: emptyList()
        val calDone = e.row.id in s.calibrationDone
        val next = if (e.row.status == Status.SKIPPED || e.row.status == Status.DONE) null else target(ex, e, s, calDone, last.isEmpty())
        return LiftView(e.row.id, e.row.position, ex, e.doc, e.row.status, e.sets, next, last, calDone)
    }

    /** The next set of an exercise, or null when it is finished for today. */
    private fun target(ex: Exercise, e: StoredExercise, s: ActiveState, calDone: Boolean, firstExposure: Boolean): SetTarget? {
        val item = e.doc
        val warm = e.sets.count { it.kind == SetKind.WARMUP }
        val cal = e.sets.count { it.kind == SetKind.CALIBRATION }
        val work = e.sets.count { it.kind == SetKind.WORKING }
        // WU-002 ramp-up sets come first, until the first calibration or working set is logged (or the user skips them).
        if (item.rampSets.isNotEmpty() && cal == 0 && work == 0 && warm < item.rampSets.size && e.row.id !in s.rampSkipped) {
            val r: RampRef = item.rampSets[warm]
            return SetTarget(SetKind.WARMUP, warm + 1, item.rampSets.size, r.load, r.reps, DoseUnit.REPS, item.perSide, null, askForm = false)
        }
        val load = s.nextLoads[e.row.id] ?: item.load
        if (calibrates(ex, item)) {
            if (calDone || cal >= P.CAL_001.max_ramp_sets) return null
            val reps = ExerciseProgress.calibrationReps(item.reps)
            return SetTarget(SetKind.CALIBRATION, cal + 1, P.CAL_001.max_ramp_sets, load, reps..reps, item.unit, item.perSide, null, askForm = false)
        }
        if (work >= item.sets) return null
        val lastSet = work + 1 == item.sets
        val rir = if (lastSet && item.lastSetToFailure) 0.0 else item.targetRir
        val askForm = lastSet && item.unit == DoseUnit.REPS && (item.main || firstExposure)
        return SetTarget(SetKind.WORKING, work + 1, item.sets, load, item.reps, item.unit, item.perSide, rir, askForm,
            userAdded = work + 1 > item.sets - item.addedSets)
    }

    /** CAL-001 runs on a loaded exercise the generator planned as calibration (bodyweight ladders and assisted moves do not calibrate). */
    private fun calibrates(ex: Exercise, item: ItemDoc): Boolean =
        item.calibrating && item.load != null && !ex.assisted && ex.loadType in LOADED && item.unit == DoseUnit.REPS

    /** TIME-004 estimate of what is left: the remaining plan through the same time model the generator uses. */
    private fun remainingMinutes(w: StoredWorkout, lifts: List<LiftView>, s: ActiveState, stage: Stage?): Double =
        if (stage == null) 0.0 else Math.round(TimeModel.minutes(remaining(w, lifts, s, stage).plan) * 10) / 10.0

    /** The work still to do as a TIME-004 plan; slot `i` of [Remaining.slots] is plan item "i:<exercise>", conditioning j is "c<j>:<modality>". */
    private data class Remaining(val plan: SessionPlan, val lifts: List<LiftView>, val slots: List<PlannedSlot>, val conditioning: List<IndexedValue<ConditioningItem>>)

    private fun remaining(w: StoredWorkout, lifts: List<LiftView>, s: ActiveState, stage: Stage): Remaining {
        val doc = w.doc
        val open = lifts.filter { !it.finished }
        val slots = open.map { l ->
            val left = if (calibrates(l.exercise, l.item)) maxOf(1, 3 - l.sets.count { it.kind == SetKind.CALIBRATION }) else maxOf(1, l.item.sets - l.workingDone)
            PlannedSlot(SlotSpec("r${l.rowId}", l.item.role, l.exercise.pattern), l.exercise, left, l.item.reps, l.item.unit, l.item.perSide, l.item.targetRir,
                l.item.priority, RestWindow(l.item.restMinSec, l.item.restDefaultSec, l.item.restMaxSec, ""), l.item.main)
        }
        val cond = doc.conditioning.withIndex().filter { it.index !in s.conditioningDone && it.value.workMinutes > 0 }
        val day = PlannedDay(doc.weekday, w.template, slots, cond.map { (_, k) -> PlannedConditioning(k.modality, k.zone, k.workMinutes, k.restMinutes,
            k.interval?.let { i -> com.personalfitnesscoach.engine.conditioning.Interval(k.protocol, i.reps, i.workSec, i.restSec, i.cr10) }, k.hiit, k.impact) },
            0.0, w.template.heavyLower)
        val warmup = if (stage == Stage.WARMUP) doc.warmupMinutes else 0.0
        val bone = if (doc.boneLoading != null && stage.ordinal <= Stage.BONE_LOADING.ordinal) doc.boneLoading.minutes else 0.0
        val balance = if (stage.ordinal <= Stage.BALANCE.ordinal) doc.balanceMinutes else 0.0
        val cool = if (stage.ordinal <= Stage.COOLDOWN.ordinal) (doc.cooldownMinutes ?: 0.0) else 0.0
        val mob = if (stage.ordinal <= Stage.COOLDOWN.ordinal) doc.mobilityMinutes else 0.0
        return Remaining(SessionPlan(warmup, cool, WeekPlanner.planItems(day), coreMobilityMin = mob + balance + bone), open, slots, cond)
    }

    // ------------------------------------------------------------------------------------------------ moving through the session
    private suspend fun save(w: StoredWorkout, s: ActiveState): PlayerView {
        d.sessions.saveState(w.id, s.encode())
        return build(d.sessions.load(w.id)!!, s)
    }

    private suspend fun active(): Pair<StoredWorkout, ActiveState> {
        val (w, a) = checkNotNull(d.sessions.active()) { "no workout in progress" }
        return w to ActiveState.decode(a.json)
    }

    /** Marks a stage done (warm-up, bone-loading block, balance, cool-down) and moves on. */
    suspend fun completeStage(stage: Stage): PlayerView = d.store.transaction {
        val (w, s) = active()
        val v = build(w, s)
        val idx = v.stages.indexOf(stage)
        require(idx >= 0) { "stage $stage is not in this workout" }
        val next = v.stages.getOrNull(idx + 1)
        save(w, s.copy(stage = next ?: Stage.END, sheet = null))
    }

    /** Shows another exercise (any order is allowed; EQ-002 reorder). */
    suspend fun select(rowId: Long): PlayerView = d.store.transaction {
        val (w, s) = active()
        require(w.exercises.any { it.row.id == rowId }) { "not in this workout" }
        save(w, s.copy(currentRowId = rowId, stage = maxStage(s.stage, Stage.LIFTS, w)))
    }

    private fun maxStage(a: Stage, b: Stage, w: StoredWorkout): Stage = if (a.ordinal >= b.ordinal) a else b

    /** Skips the remaining WU-002 ramp-up sets of an exercise. */
    suspend fun skipRamp(rowId: Long): PlayerView = d.store.transaction {
        val (w, s) = active()
        save(w, s.copy(rampSkipped = s.rampSkipped + rowId))
    }

    /** Logs one set (FS-5, NFR-03: saved at once with the resume point) and applies INT-007 or the CAL-001 ramp step for the next set. */
    suspend fun log(rowId: Long, entry: LiftEntry): LogResult = d.store.transaction {
        val (w, s) = active()
        val v = build(w, s)
        val lift = v.lifts.firstOrNull { it.rowId == rowId } ?: error("not in this workout")
        val t = lift.next ?: error("${lift.exercise.id} is finished for today")
        val item = lift.item
        val u = checkNotNull(d.bridge.user()) { "no profile" }
        val avail = PlateMath.loadsFor(lift.exercise, u.equipment.inventory)
        val dec = ArrayList<Decision>()
        var next = s
        var calStep: CalibrationStep? = null
        val deviation = when {
            t.userAdded -> "USER_ADDED"
            entry.load != t.load || (entry.reps != null && entry.reps !in t.reps) -> "ADJUSTED"
            else -> "AS_PLANNED"
        }
        val newSet = NewSet(lift.sets.size, t.kind, entry.load, entry.reps, entry.seconds, rir = entry.rir, formCheck = entry.form, deviation = deviation)
        when (t.kind) {
            SetKind.CALIBRATION -> {
                val load = requireNotNull(entry.load) { "a calibration set needs a load" }
                val rir = requireNotNull(entry.rir) { "a calibration set needs the effort answer" }
                val step = Calibration.next(load, t.reps.first, rir, lift.sets.count { it.kind == SetKind.CALIBRATION } + 1, avail, item.calibrationCeiling,
                    Calibration.coarseStepsAllowed(lift.exercise.loadType))
                dec += step.decisions
                calStep = step.value
                next = when (val st = step.value) {
                    is CalibrationStep.Continue -> next.copy(nextLoads = next.nextLoads + (rowId to st.nextLoad))
                    else -> next.copy(calibrationDone = next.calibrationDone + rowId)
                }
            }
            SetKind.WORKING -> {
                val planned = item.load
                val rir = entry.rir
                if (planned != null && entry.load != null && entry.reps != null && rir != null && item.unit == DoseUnit.REPS && lift.exercise.loadType in LOADED &&
                    !lift.exercise.assisted && avail.isNotEmpty()) {
                    val normal = planned / maxOf(item.loadFactor, 1e-9)
                    val tierMax = Autoregulation.tierMaxLoad(normal, item.main, v.tier, w.doc.inDeload)
                    val adj = Autoregulation.adjust(planned, entry.load, SetLog(entry.load, entry.reps, rir, warmup = false, formOk = entry.form),
                        item.reps.last, item.reps.first, item.targetRir, avail, minOf(tierMax, planned * (1 + P.INT_007.net_limit_pct / 100.0)))
                    dec += adj.decisions
                    next = next.copy(nextLoads = next.nextLoads + (rowId to adj.value))
                }
            }
        }
        val now = d.clock.nowMs()
        val rest = when (t.kind) {
            SetKind.WARMUP -> minOf(RAMP_REST_SEC, item.restMinSec)
            SetKind.CALIBRATION -> item.restMinSec
            else -> item.restDefaultSec
        }
        next = next.copy(currentRowId = rowId, stage = maxStage(next.stage, Stage.LIFTS, w), restStartedAtMs = now, restSec = rest,
            lastHardEffortAtMs = if (t.kind == SetKind.WARMUP) next.lastHardEffortAtMs else now, sheet = null)
        d.sessions.logSet(rowId, newSet, next.encode())
        d.decisions.append(dec, w.day, w.id)
        val view = build(d.sessions.load(w.id)!!, next)
        LogResult(view, dec, calStep, rest)
    }

    /** Rest timer controls (A2): ±30 s or skip. */
    suspend fun adjustRest(deltaSec: Int): PlayerView = d.store.transaction {
        val (w, s) = active()
        val started = s.restStartedAtMs ?: return@transaction build(w, s)
        val sec = ((s.restSec ?: 0) + deltaSec).coerceIn(0, 3600)
        save(w, s.copy(restStartedAtMs = started, restSec = sec))
    }

    suspend fun skipRest(): PlayerView = d.store.transaction {
        val (w, s) = active()
        save(w, s.copy(restStartedAtMs = null, restSec = null))
    }

    /** Starts a timed rest (between intervals or drills, outside set logging). */
    suspend fun startRest(sec: Int): PlayerView = d.store.transaction {
        val (w, s) = active()
        save(w, s.copy(restStartedAtMs = d.clock.nowMs(), restSec = sec.coerceIn(0, 3600)))
    }

    suspend fun pause(): PlayerView = d.store.transaction {
        val (w, s) = active()
        if (s.pausedAtMs != null) build(w, s) else save(w, s.copy(pausedAtMs = d.clock.nowMs()))
    }

    suspend fun resume(): PlayerView = d.store.transaction {
        val (w, s) = active()
        val p = s.pausedAtMs ?: return@transaction build(w, s)
        val seg = d.clock.nowMs() - p
        // LOAD-001: pauses over 10 minutes are removed from the session's minutes.
        save(w, s.copy(pausedAtMs = null, pausedMs = s.pausedMs + if (seg > PAUSE_EXCLUDED_MS) seg else 0L))
    }

    /** Opens or closes a sheet (A3–A6), so a resumed session reopens it. */
    suspend fun openSheet(sheet: Sheet?): PlayerView = d.store.transaction {
        val (w, s) = active()
        save(w, s.copy(sheet = sheet))
    }

    /** Logs a conditioning block (minutes of work done; 0 = skipped) and moves on. Interval and Z3+ work counts as a hard effort. */
    suspend fun logConditioning(index: Int, workMinutesDone: Double): PlayerView = d.store.transaction {
        val (w, s) = active()
        val c = w.doc.conditioning.getOrNull(index) ?: error("no conditioning block $index")
        val m = workMinutesDone.coerceIn(0.0, c.workMinutes.coerceAtLeast(0.0) * 2 + 60)
        val hard = m > 0 && c.block().countsAsHiit
        save(w, s.copy(conditioningDone = s.conditioningDone + (index to m), lastHardEffortAtMs = if (hard) d.clock.nowMs() else s.lastHardEffortAtMs,
            restStartedAtMs = null, restSec = null))
    }

    suspend fun skipExercise(rowId: Long): PlayerView = d.store.transaction {
        val (w, s) = active()
        d.sessions.skipExercise(rowId)
        save(w, s.copy(currentRowId = null, sheet = null))
    }

    /** EQ-002 "do it later": the exercise moves to the end of the list. */
    suspend fun doLater(rowId: Long): PlayerView = d.store.transaction {
        val (w, s) = active()
        d.sessions.moveToEnd(rowId)
        save(w, s.copy(currentRowId = null, sheet = null))
    }

    // ------------------------------------------------------------------------------------------------ re-plans (all re-validated, SAF-008)
    /** The GEN-001 request the session was made from, rebuilt from the same stored state (deterministic). */
    private suspend fun request(w: StoredWorkout): Pair<GenerationRequest, PlannedDay>? {
        val t = d.planToday() ?: return null
        val day = t.week.days.firstOrNull { it.weekday == w.doc.weekday && it.template == w.template } ?: PlannedDay(w.doc.weekday, w.template,
            emptyList(), emptyList(), 0.0, w.template.heavyLower)
        val tier = Tier.valueOf(w.row.tier ?: Tier.FULL.name)
        val p = d.bridge.prepare(t.user, t.program, t.week, day, tier, maxOf(10, Math.round(w.doc.plannedMinutes).toInt()), awayFromGym = w.doc.awayFromGym)
        return p.request to day
    }

    /** The session as it stands, for the validator: exercises not skipped (user-added sets aside, SAF-006 counts them separately). */
    private fun live(w: StoredWorkout, override: Map<Long, Pair<String, ItemDoc>?> = emptyMap(), conditioning: List<ConditioningItem> = w.doc.conditioning): Session {
        val ex = w.exercises.mapNotNull { e ->
            val o = if (override.containsKey(e.row.id)) override[e.row.id] else e.exerciseId to e.doc
            if (o == null || (e.row.status == Status.SKIPPED && !override.containsKey(e.row.id))) return@mapNotNull null
            val (id, doc) = o
            val x = Library[id] ?: return@mapNotNull null
            val sets = maxOf(doc.sets - doc.addedSets, if (e.exerciseId == id) e.working.size.coerceAtMost(doc.sets - doc.addedSets) else 0)
            if (sets <= 0) null else SessionExercise(x, sets, doc.reps.last, doc.targetRir, doc.loadFactor, doc.main, doc.lastSetToFailure)
        }
        return Session(Tier.valueOf(w.row.tier ?: Tier.FULL.name), ex, conditioning.map { it.block() }, w.doc.warmupMinutes, w.doc.cooldownMinutes,
            w.doc.boneLoading?.variant)
    }

    private fun violations(w: StoredWorkout, req: GenerationRequest, s: Session): List<Violation> {
        val base = SessionGenerator.validationContext(req, w.doc.fullTierWorkingSets)
        // The exercises' MODIFIED +1 RIR shift is checked against the FULL-day RIR of their slots.
        val withRir = s.copy(exercises = s.exercises.map { e ->
            val slot = w.exercises.firstOrNull { it.exerciseId == e.exercise.id }?.row?.slotKey
            if (slot == null) e else e.copy(fullTierRir = SessionGenerator.fullTierRir(slot, e.targetRir, req)) })
        return SessionValidator.violations(withRir, base)
    }

    /** SUB-001/002 swap options (A3), or EQ-002 "occupied" options with "do it later" when exercises are still to come. */
    suspend fun swapOptions(rowId: Long, occupied: Boolean = false, extraJointLimits: Map<Joint, Int> = emptyMap()): SwapChoice = d.store.transaction {
        val (w, _) = active()
        val e = w.exercises.first { it.row.id == rowId }
        val original = Library.require(e.exerciseId)
        val (req0, _) = checkNotNull(request(w)) { "no plan" }
        val req = if (extraJointLimits.isEmpty()) req0 else req0.copy(jointLimits = (req0.jointLimits.keys + extraJointLimits.keys)
            .associateWith { minOf(req0.jointLimits[it] ?: 4, extraJointLimits[it] ?: 4) })
        val sub = SessionGenerator.subContext(req)
        val taken = w.exercises.filter { it.row.status != Status.SKIPPED }.map { it.exerciseId }.toSet()
        val power = e.doc.role == SlotRole.POWER
        val pool = Library.all.filter { !it.userAddOnly && it.id !in taken && (if (power) it.powerCapable else !it.powerOnly) && unitFits(it, e.doc) }
        val stillToCome = w.exercises.count { it.row.id != rowId && it.row.status != Status.SKIPPED && it.row.status != Status.DONE &&
            it.sets.none { s -> s.kind != SetKind.WARMUP } }
        if (occupied) {
            val r = Substitution.occupied(original, pool, sub, stillToCome)
            SwapChoice(rowId, original, r.value.swaps.ranked, r.value.canDoLater, r.decisions)
        } else {
            val r = Substitution.options(original, pool, sub)
            SwapChoice(rowId, original, r.value.ranked, false, r.decisions)
        }
    }

    private fun unitFits(c: Exercise, item: ItemDoc): Boolean = c.unit == item.unit || item.unit == DoseUnit.REPS && c.unit == DoseUnit.REPS

    /**
     * Swaps an exercise with no logged work for `newId`: the new dose comes from the generator for the same slot (load from history,
     * tier and deload caps, ramp-up sets), and the whole session is re-validated before the swap is saved (SAF-008). SUB-003: the
     * accepted swap raises the new exercise's preference a little.
     */
    suspend fun swap(rowId: Long, newId: String): ReplanResult = d.store.transaction {
        val (w, s) = active()
        val e = w.exercises.first { it.row.id == rowId }
        check(e.sets.none { it.kind != SetKind.WARMUP }) { "sets already logged for ${e.exerciseId}" }
        val doc = itemFor(w, e, Library.require(newId))
        if (doc == null) return@transaction ReplanResult(emptyList(), listOf(swapRefused(e.exerciseId, newId)), build(w, s), refused = true)
        val (req, _) = request(w)!!
        val bad = violations(w, req, live(w, mapOf(rowId to (newId to doc))))
        if (bad.isNotEmpty()) return@transaction ReplanResult(emptyList(), listOf(swapRefused(e.exerciseId, newId, bad)), build(w, s), refused = true)
        d.sessions.swap(rowId, newId, doc)
        d.docs.update(PreferencesRecord) { p -> (p ?: PreferencesRecord()).let { it.copy(exerciseScores = it.exerciseScores +
            (newId to Substitution.updatePreference(it.exerciseScores[newId] ?: 0.5, accepted = true))) } }
        val dec = listOf(Decision(DecisionKind.SUBSTITUTION, listOf(RuleIds.SUB_001, RuleIds.SUB_002, RuleIds.SAF_008), ReasonKey.SWAP_CHOSEN,
            inputs = mapOf("from" to e.exerciseId), outputs = mapOf("to" to newId, "sets" to doc.sets, "load" to doc.load)))
        d.decisions.append(dec, w.day, w.id)
        ReplanResult(listOf(Change.Swapped(rowId, e.exerciseId, newId)), dec, save(w, s.copy(currentRowId = rowId, nextLoads = s.nextLoads - rowId,
            rampSkipped = s.rampSkipped - rowId, sheet = null)))
    }

    private fun swapRefused(from: String, to: String, v: List<Violation> = emptyList()) = Decision(DecisionKind.SUBSTITUTION, listOf(RuleIds.SUB_001, RuleIds.SAF_008),
        ReasonKey.SWAP_NONE_AVAILABLE, inputs = mapOf("from" to from, "to" to to), outputs = mapOf("violations" to v.map { it.code }))

    /** The generator's dose for `ex` in `e`'s slot today (a one-slot day), or null when the generator would not use it. */
    private suspend fun itemFor(w: StoredWorkout, e: StoredExercise, ex: Exercise): ItemDoc? {
        val (req, day) = request(w) ?: return null
        val orig = day.slots.firstOrNull { it.spec.key == e.row.slotKey }
        val base = orig ?: PlannedSlot(SlotSpec(e.row.slotKey, e.doc.role, ex.pattern), ex, e.doc.sets, e.doc.reps, e.doc.unit, e.doc.perSide,
            e.doc.targetRir, e.doc.priority, Rest.forSets(ex, e.doc.reps.last), e.doc.main)
        val slot = base.copy(exercise = ex, rest = Rest.forSets(ex, base.reps.last), unit = ex.unit, perSide = ex.unilateral,
            reps = if (ex.unit == base.unit) base.reps else ex.defaultRepRange)
        val one = req.copy(day = day.copy(slots = listOf(slot), conditioning = emptyList(), mobilityMinutes = 0.0, balanceMinutes = 0.0), minutes = 240)
        val g = SessionGenerator.generate(one).value
        val it = g.items.firstOrNull { x -> x.slotKey == e.row.slotKey && x.exercise.id == ex.id } ?: return null
        val sets = minOf(it.sets, maxOf(1, e.doc.sets - e.doc.addedSets))
        return ItemDoc(it.role, it.priority, sets, it.reps, it.unit, it.perSide, it.targetRir, it.lastSetToFailure, it.load, it.loadFactor,
            it.rest.minSec, it.rest.defaultSec, it.rest.maxSec, it.calibrating, it.main, rampSets = it.warmupSets.map { r -> RampRef(r.load, r.reps) },
            reducedRange = it.range == com.personalfitnesscoach.engine.planning.RangeOfMotion.REDUCED,
            calibrationCeiling = if (it.calibrating) req.calibrationCeilings[ex.id] else null)
    }

    /**
     * SAF-003 "Something hurts" (A4). The report is kept; then, for the session:
     *  - continue with caution: this exercise's load comes down 10–20% (a shorter range is suggested too);
     *  - stop the exercise: it ends, and the rest of the session keeps the joint at stress ≤ 1 (swapped where possible, else skipped);
     *    pain-free alternatives for the stopped exercise are offered;
     *  - stop the region: nothing more loads that joint today; if whole-body movement is affected the session ends.
     */
    suspend fun reportPain(rowId: Long?, report: PainReport, side: Side? = null): PainResult = d.store.transaction {
        val (w, s) = active()
        val e = rowId?.let { id -> w.exercises.first { it.row.id == id } }
        val gate = d.reportPain(report, side, w.id, e?.exerciseId)
        val o = gate.value
        val changes = ArrayList<Change>()
        val dec = ArrayList<Decision>(gate.decisions)
        var state = s
        val u = checkNotNull(d.bridge.user())
        when (o.action) {
            PainAction.CONTINUE -> Unit
            PainAction.CONTINUE_CAUTION -> if (e != null && e.doc.load != null && e.row.status != Status.SKIPPED) {
                val ex = Library.require(e.exerciseId)
                val avail = PlateMath.loadsFor(ex, u.equipment.inventory)
                val cur = s.nextLoads[e.row.id] ?: e.doc.load
                val f = o.loadFactor ?: (0.8..0.9)
                val lowered = if (ex.assisted) cur else (avail.filter { it <= cur * f.endInclusive + 1e-9 }.maxOrNull() ?: Progression.lowerLoad(cur * f.start, cur, avail))
                if (lowered < cur - 1e-9 || ex.assisted) {
                    d.sessions.updateItem(e.row.id, e.doc.copy(load = if (ex.assisted) e.doc.load else minOf(e.doc.load, lowered), painReduced = true, reducedRange = true))
                    state = state.copy(nextLoads = state.nextLoads + (e.row.id to lowered))
                    if (!ex.assisted) changes += Change.LoadLowered(e.row.id, e.exerciseId, cur, lowered)
                } else {
                    d.sessions.updateItem(e.row.id, e.doc.copy(reducedRange = true, painReduced = true))
                }
            }
            PainAction.STOP_EXERCISE, PainAction.STOP_REGION -> {
                if (e != null && e.row.status != Status.SKIPPED && e.row.status != Status.DONE) {
                    // An exercise with logged work ends as done (its sets stay); one without any is skipped.
                    if (e.sets.none { it.kind != SetKind.WARMUP }) d.sessions.skipExercise(e.row.id)
                    else d.sessions.updateItem(e.row.id, e.doc.copy(sets = maxOf(1, e.working.size), addedSets = minOf(e.doc.addedSets, maxOf(1, e.working.size))))
                    changes += Change.Skipped(e.row.id, e.exerciseId)
                }
                val limit = o.regionMaxStress ?: 0
                val joint = report.region
                val rest = d.sessions.load(w.id)!!.exercises.filter { x -> x.row.id != e?.row?.id && x.row.status != Status.SKIPPED && x.row.status != Status.DONE &&
                    (Library[x.exerciseId]?.stress(joint) ?: 0) > limit }
                for (x in rest) {
                    val logged = x.sets.any { it.kind != SetKind.WARMUP }
                    if (!logged && o.action == PainAction.STOP_EXERCISE) {
                        // Keep the slot with a swap that keeps the joint at stress ≤ 1, when one passes the checks.
                        val opts = swapOptionsFor(w, x, mapOf(joint to limit))
                        val pick = opts.firstOrNull { it.fits.pattern >= P.SUB_002.min_pattern_for_auto }?.exercise
                        val doc = pick?.let { itemFor(w, x, it) }
                        if (pick != null && doc != null) {
                            val (req, _) = request(w)!!
                            val bad = violations(w, req.copy(jointLimits = req.jointLimits + (joint to minOf(req.jointLimits[joint] ?: 4, limit))),
                                live(d.sessions.load(w.id)!!, mapOf(x.row.id to (pick.id to doc))))
                            if (bad.isEmpty()) {
                                d.sessions.swap(x.row.id, pick.id, doc)
                                changes += Change.Swapped(x.row.id, x.exerciseId, pick.id)
                                continue
                            }
                        }
                    }
                    if (logged) d.sessions.updateItem(x.row.id, x.doc.copy(sets = maxOf(1, x.working.size), addedSets = minOf(x.doc.addedSets, maxOf(1, x.working.size))))
                    else d.sessions.skipExercise(x.row.id)
                    changes += Change.Skipped(x.row.id, x.exerciseId)
                }
            }
        }
        val alternatives = if (o.action == PainAction.STOP_EXERCISE && e != null)
            swapOptionsFor(w, e, mapOf(report.region to (o.regionMaxStress ?: P.SAF_003.substitute_max_joint_stress))) else emptyList()
        d.decisions.append(dec.drop(gate.decisions.size), w.day, w.id)
        if (o.endSession) {
            // SAF-003: whole-body movement affected → the session ends (PARTIAL); what was logged stays.
            val sum = finishInternal(w.id, null, endedEarly = true)
            return@transaction PainResult(o, changes, emptyList(), true, dec, null).also { lastSummary = sum }
        }
        PainResult(o, changes, alternatives, false, dec, save(d.sessions.load(w.id)!!, state.copy(sheet = null, currentRowId = null)))
    }

    private suspend fun swapOptionsFor(w: StoredWorkout, e: StoredExercise, limits: Map<Joint, Int>): List<Candidate> {
        val (req0, _) = request(w) ?: return emptyList()
        val req = req0.copy(jointLimits = (req0.jointLimits.keys + limits.keys).associateWith { minOf(req0.jointLimits[it] ?: 4, limits[it] ?: 4) })
        val taken = w.exercises.filter { it.row.status != Status.SKIPPED }.map { it.exerciseId }.toSet()
        val power = e.doc.role == SlotRole.POWER
        val pool = Library.all.filter { !it.userAddOnly && it.id !in taken && (if (power) it.powerCapable else !it.powerOnly) && unitFits(it, e.doc) }
        return Substitution.options(Library.require(e.exerciseId), pool, SessionGenerator.subContext(req)).value.ranked
    }

    /**
     * After STOP_EXERCISE: does a pain-free alternative instead, as a new exercise in the same slot (the stopped one keeps its sets).
     * Re-validated with the region's joint limit before it is added.
     */
    suspend fun addAlternative(stoppedRowId: Long, newId: String, region: Joint, maxStress: Int): ReplanResult = d.store.transaction {
        val (w, s) = active()
        val e = w.exercises.first { it.row.id == stoppedRowId }
        val ex = Library.require(newId)
        require(ex.stress(region) <= maxStress) { "$newId loads ${region.name} too much" }
        val doc = itemFor(w, e, ex) ?: return@transaction ReplanResult(emptyList(), listOf(swapRefused(e.exerciseId, newId)), build(w, s), refused = true)
        val remaining = maxOf(1, e.doc.sets - e.working.size)
        val add = doc.copy(sets = minOf(doc.sets, remaining), rampSets = emptyList(), swappedFrom = e.exerciseId)
        val (req, _) = request(w)!!
        val reqLimited = req.copy(jointLimits = req.jointLimits + (region to minOf(req.jointLimits[region] ?: 4, maxStress)))
        val trial = live(w).let { it.copy(exercises = it.exercises.filter { x -> x.exercise.id != e.exerciseId || e.working.isNotEmpty() } +
            SessionExercise(ex, add.sets, add.reps.last, add.targetRir, add.loadFactor, add.main, add.lastSetToFailure)) }
        val bad = violations(w, reqLimited, trial)
        if (bad.isNotEmpty()) return@transaction ReplanResult(emptyList(), listOf(swapRefused(e.exerciseId, newId, bad)), build(w, s), refused = true)
        val id = d.sessions.addExercise(w.id, e.row.position + 1, newId, e.row.slotKey, add)
        val dec = listOf(Decision(DecisionKind.SUBSTITUTION, listOf(RuleIds.SAF_003, RuleIds.SUB_001, RuleIds.SAF_008), ReasonKey.SWAP_CHOSEN,
            inputs = mapOf("from" to e.exerciseId, "pain" to region.name), outputs = mapOf("to" to newId, "sets" to add.sets)))
        d.decisions.append(dec, w.day, w.id)
        ReplanResult(listOf(Change.Added(id, newId)), dec, save(d.sessions.load(w.id)!!, s.copy(currentRowId = id, sheet = null)))
    }

    /**
     * FS-7 / A6 "I only have N minutes left": the remaining work is re-fitted with TIME-001…004 (P0 warm-up and cool-down floors kept,
     * lowest priorities trimmed first); what moves is returned as a list. Shortening only — more time is offered before the session.
     */
    suspend fun changeTime(minutesLeft: Int): ReplanResult = d.store.transaction {
        val (w, s) = active()
        val v = build(w, s)
        val stage = v.stage ?: return@transaction ReplanResult(emptyList(), emptyList(), v)
        val u = checkNotNull(d.bridge.user())
        val (req, _) = request(w)!!
        val c = req.conditions
        val rem = remaining(w, v.lifts, s, stage)
        // The warm-up floor is already paid once the warm-up is done: the fit gets that much more, so only the remaining work competes.
        val floor = TimeBudget.warmupFloor(u.age, c.extraWarmupMin.toDouble())
        val budget = minutesLeft + if (rem.plan.warmupMin == 0.0) floor else 0.0
        val fit = TimeBudget.fit(rem.plan, budget, u.age, 1.0, u.preferences.crowdedGym, c.extraWarmupMin.toDouble(), c.extraCooldownMin.toDouble())
        var plan = fit.value.plan
        val extra = ArrayList<String>()
        // A hard limit mid-session: below TIME-002's floors, work goes in reverse TIME-001 priority (P5…P3, then P2 conditioning, then P1
        // down to one set); the cool-down (P0) always stays.
        fun fits() = TimeModel.minutes(plan) <= budget + 1e-9
        while (!fits()) {
            val victim = plan.items.filter { it.priority >= com.personalfitnesscoach.engine.planning.Priority.P3 }.maxWithOrNull(compareBy({ it.priority }, { plan.items.indexOf(it) }))
                ?: plan.items.lastOrNull { it.isConditioning }
            if (victim != null) { plan = plan.copy(items = plan.items.filter { it.id != victim.id && it.pairedWith != victim.id }); extra += victim.id; continue }
            val p1 = plan.items.lastOrNull { !it.isConditioning && it.sets > 1 } ?: break
            plan = plan.copy(items = plan.items.map { if (it.id == p1.id) it.copy(sets = it.sets - 1) else it })
        }
        val byId = plan.items.associateBy { it.id }
        val changes = ArrayList<Change>()
        rem.lifts.forEachIndexed { i, l ->
            val p: PlanItem? = byId["$i:${l.exercise.id}"]
            val planned = rem.slots[i].sets
            if (p == null) {
                if (l.hasLoggedWork) {
                    val to = maxOf(1, l.workingDone)
                    d.sessions.updateItem(l.rowId, l.item.copy(sets = to, addedSets = minOf(l.item.addedSets, to)))
                    if (calibrates(l.exercise, l.item)) Unit else changes += Change.SetsReduced(l.rowId, l.exercise.id, l.item.sets, to)
                } else { d.sessions.skipExercise(l.rowId); changes += Change.Skipped(l.rowId, l.exercise.id) }
            } else if (p.sets < planned && !calibrates(l.exercise, l.item)) {
                val to = l.workingDone + p.sets
                d.sessions.updateItem(l.rowId, l.item.copy(sets = to, addedSets = minOf(l.item.addedSets, to)))
                changes += Change.SetsReduced(l.rowId, l.exercise.id, l.item.sets, to)
            }
        }
        var cond = w.doc.conditioning
        var state = s
        rem.conditioning.forEachIndexed { j, (idx, k) ->
            val p = byId["c$j:${k.modality}"]
            if (p == null) {
                cond = cond.mapIndexed { i, x -> if (i == idx) x.copy(workMinutes = 0.0, restMinutes = 0.0) else x }
                // A dropped block is logged as not done (0), so it never counts.
                state = state.copy(conditioningDone = state.conditioningDone + (idx to 0.0))
                changes += Change.ConditioningDropped(idx)
            } else p.conditioning?.let { q ->
                val work = if (q.rounds > 1 || k.interval != null) q.rounds * q.workSec / 60.0 else q.workSec / 60.0
                if (work < k.workMinutes - 1e-6) {
                    val rest = if (q.rounds > 1 || k.interval != null) q.rounds * q.restSec / 60.0 else 0.0
                    cond = cond.mapIndexed { i, x -> if (i == idx) x.copy(workMinutes = work, restMinutes = rest,
                        interval = x.interval?.let { iv -> iv.copy(reps = maxOf(1, q.rounds)) }) else x }
                    changes += Change.ConditioningShortened(idx, k.workMinutes, work)
                }
            }
        }
        if (cond != w.doc.conditioning) d.sessions.updateDoc(w.id, w.doc.copy(conditioning = cond))
        val dec = fit.decisions + Decision(DecisionKind.TIME_FIT, listOf(RuleIds.TIME_001, RuleIds.TIME_002, RuleIds.TIME_004, RuleIds.SAF_008),
            ReasonKey.TIME_COMPRESSED, inputs = mapOf("minutesLeft" to minutesLeft), outputs = mapOf("changes" to changes.size, "belowFloors" to extra))
        d.decisions.append(dec, w.day, w.id)
        ReplanResult(changes, dec, save(d.sessions.load(w.id)!!, state.copy(sheet = null)))
    }

    /** SAF-006: one more set than prescribed — OK, warn-and-confirm past a cap, blocked at the absolute ceiling or on a painful region. */
    suspend fun addSet(rowId: Long, confirmed: Boolean = false): AddSetResult = d.store.transaction {
        val (w, s) = active()
        val e = w.exercises.first { it.row.id == rowId }
        val ex = Library.require(e.exerciseId)
        val u = checkNotNull(d.bridge.user())
        val t = d.planToday()
        val week = t?.let { d.bridge.validationContext(u, it.program, it.week, null) }
        val today = live(w)
        val sessionAfter = today.workingSets + w.exercises.sumOf { it.doc.addedSets } + 1
        val touched = ex.primary
        val weeklyAfter = touched.maxOfOrNull { m -> (week?.weekSetsSoFar?.get(m) ?: 0.0) +
            w.exercises.filter { x -> x.row.status != Status.SKIPPED && Library[x.exerciseId]?.primary?.contains(m) == true }.sumOf { it.doc.sets.toDouble() } + 1 } ?: 1.0
        val directAfter = touched.maxOfOrNull { m -> w.exercises.filter { x -> x.row.status != Status.SKIPPED && Library[x.exerciseId]?.primary?.contains(m) == true }
            .sumOf { it.doc.sets.toDouble() } + 1 } ?: 1.0
        val (limits, _) = d.bridge.painToday(u.today)
        val painful = ex.jointStress.any { (j, st) -> st > 0 && (limits[j] ?: 4) < st } || d.bridge.regions(u.today).value.any { r -> ex.stress(r.region) > (r.maxStress ?: 4) }
        val r = UserAdditions.addSets(u.profile.level, weeklyAfter, sessionAfter, painful, confirmed, directAfter)
        d.decisions.append(r.decisions, w.day, w.id)
        if (r.value == AdditionVerdict.OK) {
            d.sessions.updateItem(rowId, e.doc.copy(sets = e.doc.sets + 1, addedSets = e.doc.addedSets + 1))
            if (e.row.status == Status.DONE) Unit
        }
        AddSetResult(r.value, r.decisions, save(d.sessions.load(w.id)!!, s.copy(currentRowId = rowId)))
    }

    // ------------------------------------------------------------------------------------------------ ending
    /** SAF-002 during a session (A5): the workout ends at once and the stop is kept until confirmed. */
    suspend fun redFlag(symptoms: Set<String>): SafetyStop? = d.reportRedFlag(symptoms)

    /** A summary produced by an automatic end (pain gate), picked up by the screen. */
    var lastSummary: Summary? = null
        private set

    /**
     * Finishes the workout (A8): conditioning minutes as logged (blocks never reached count as not done), the session rating if given
     * now (LOAD-001), minutes without long pauses; then the next prescriptions are written and the summary is built.
     */
    suspend fun finish(sessionRpe: Double?, endedEarly: Boolean = false): Summary = d.store.transaction {
        val (w, _) = active()
        finishInternal(w.id, sessionRpe, endedEarly)
    }

    private suspend fun finishInternal(workoutId: Long, sessionRpe: Double?, endedEarly: Boolean): Summary {
        val (w, s) = active()
        check(w.id == workoutId)
        val before = d.docs.all(ExerciseState).associate { it.exerciseId to it.e1rm }
        val now = d.clock.nowMs()
        val v = build(w, s)
        // Pauses over 10 minutes don't count (LOAD-001); an open pause ends now.
        val openPause = s.pausedAtMs?.let { p -> (now - p).takeIf { it > PAUSE_EXCLUDED_MS } ?: 0L } ?: 0L
        val minutes = (((now - (w.row.startedAtMs ?: now)) - s.pausedMs - openPause) / 60_000.0).coerceIn(0.0, 600.0)
        val done = w.doc.conditioning.indices.map { i -> s.conditioningDone[i] ?: if (v.step == Step.Done) null else 0.0 }
        val finished = d.finishWorkout(w.id, sessionRpe, Math.round(minutes * 10) / 10.0, done, endedEarly)
        val after = d.docs.all(ExerciseState)
        val records = after.mapNotNull { st -> val a = st.e1rm ?: return@mapNotNull null; val b = before[st.exerciseId]
            if (b != null && a > b + 1e-9 && finished.exercises.any { it.exerciseId == st.exerciseId && it.working.isNotEmpty() }) Record(st.exerciseId, b, a) else null }
        val hardAgo = s.lastHardEffortAtMs?.let { (now - it) / 60_000.0 } ?: Double.MAX_VALUE
        val ask = SrpePrompt.decide(hardAgo, askedBefore = false, hoursSinceSession = 0.0)
        d.decisions.append(ask.decisions, w.day, w.id)
        val dec = d.decisions.forWorkout(w.id, w.day)
        return Summary(finished, records, dec, if (sessionRpe != null) SrpePrompt.Ask.NEVER else ask.value, minutes,
            finished.exercises.sumOf { it.working.size })
    }

    /** Abandons the workout without keeping it (nothing counts). Only before any work is logged; otherwise end it early. */
    suspend fun discard(): Boolean = d.store.transaction {
        val (w, _) = active()
        if (w.exercises.any { e -> e.sets.any { it.kind != SetKind.WARMUP } }) return@transaction false
        d.sessions.discard(w.id)
        true
    }

    companion object {
        val LOADED = setOf(LoadType.BARBELL, LoadType.DUMBBELL, LoadType.KETTLEBELL, LoadType.STACK)
        /** Rest after a WU-002 ramp-up set: short, never longer than the exercise's minimum rest. */
        const val RAMP_REST_SEC = 60
        /** LOAD-001: pauses over 10 minutes are removed from session minutes. */
        val PAUSE_EXCLUDED_MS: Long = P.LOAD_001.pause_exclusion_min * 60_000L

        /** FS-5 "absurd entries (e.g. 500 kg, 99 reps) → confirm prompt": far from the target or beyond what anyone lifts. */
        fun unusual(entry: LiftEntry, t: SetTarget): Boolean {
            val l = entry.load
            val r = entry.reps
            return (l != null && (l > 300.0 || (t.load != null && t.load > 0 && l > t.load * 1.5 + 5.0))) ||
                (r != null && (r > 50 || r > t.reps.last * 2 + 2)) || (entry.seconds != null && entry.seconds > 600)
        }
    }
}
