package com.personalfitnesscoach.data.core.engine

import com.personalfitnesscoach.data.core.model.CalibrationState
import com.personalfitnesscoach.data.core.model.ExerciseState
import com.personalfitnesscoach.data.core.model.KnownNumber
import com.personalfitnesscoach.data.core.session.StoredExercise
import com.personalfitnesscoach.engine.calc.E1rm
import com.personalfitnesscoach.engine.calc.Inventory
import com.personalfitnesscoach.engine.calc.PlateMath
import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.Exercise
import com.personalfitnesscoach.engine.model.FormCheck
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.LoadType
import com.personalfitnesscoach.engine.progression.BwAction
import com.personalfitnesscoach.engine.progression.BwSession
import com.personalfitnesscoach.engine.progression.BwSet
import com.personalfitnesscoach.engine.progression.Bodyweight
import com.personalfitnesscoach.engine.progression.Calibration
import com.personalfitnesscoach.engine.progression.CalibrationStep
import com.personalfitnesscoach.engine.progression.ExposureInput
import com.personalfitnesscoach.engine.progression.KnownEntry
import com.personalfitnesscoach.engine.progression.KnownLoads
import com.personalfitnesscoach.engine.progression.KnownStart
import com.personalfitnesscoach.engine.progression.Prescription
import com.personalfitnesscoach.engine.progression.Progression
import com.personalfitnesscoach.engine.progression.ProgressionAction
import com.personalfitnesscoach.engine.registry.P

/** What the progression rules need to know about the user while one exposure is applied. */
data class ProgressContext(
    val inventory: Inventory,
    val level: Level,
    val age: Int?,
    /** SAF-001 screening not standard (REG-004 shift for old known numbers). */
    val flaggedScreen: Boolean,
    /** PROG-001: tier FULL or MODIFIED and fewer than 2 fatigue signals on the day. */
    val recoveryOk: Boolean,
    /** CAL-001 coarse steps on free weights for this user (D-116); off unless set. */
    val coarseFreeWeights: Boolean = false,
)

/** The result of one exposure: the exercise's new state and, for ladders, the rung to use next time. */
data class ExposureOutcome(val state: ExerciseState, val ladder: Pair<String, String>? = null)

/**
 * History → engine (FS-5): turns one logged exposure into the exercise's next state, using only engine rules. Applied after every
 * finished workout and, from scratch, by [ProgressRecorder.rebuild]; both give the same result (tested).
 *
 *  - Calibration sets (CAL-001) are replayed in order through [Calibration.next]: a found load with a valid estimate seeds the
 *    e1RM; without one, the found load (or the "too hard" load −5%) is the next exposure's working load (D-115). A ramp still easy at
 *    its 5-set limit goes on next session, over sessions 1–4; then progression takes over from the best load the ramp allowed.
 *  - Working sets update the e1RM (INT-004, smoothed, +5% cap) and, for a full-load exposure, the next prescription
 *    (PROG-001…008; the generator uses it for the same rep range, D-055).
 *  - Bodyweight, assisted and core exercises follow their ladders (BW-001/002, CORE-002, PROG-005).
 *  - The user's own number (CAL-002) seeds the first e1RM, or caps the calibration ramp when it is old.
 */
object ExerciseProgress {
    private val LOADED = setOf(LoadType.BARBELL, LoadType.DUMBBELL, LoadType.KETTLEBELL, LoadType.STACK)

    fun available(ex: Exercise, inv: Inventory): List<Double> = PlateMath.loadsFor(ex, inv)

    /** CAL-002 for one slot on `day` (the age of the number is measured from when the exercise was last done). */
    fun known(k: KnownNumber, ex: Exercise, day: Int, ctx: ProgressContext, targetReps: Int, targetRir: Double): EngineResult<KnownStart> =
        KnownLoads.start(KnownEntry(k.exerciseId, k.loadKg, k.reps, k.rir, k.bestLift, maxOf(0, day - k.lastDoneDay)), ctx.age, ctx.flaggedScreen,
            targetReps, targetRir, available(ex, ctx.inventory))

    /** Middle of the rep range: the reps a calibration ramp set asks for. */
    fun calibrationReps(reps: IntRange): Int = (reps.first + reps.last) / 2

    fun apply(state: ExerciseState, ex: Exercise, done: StoredExercise, day: Int, ctx: ProgressContext, known: KnownNumber?,
              recent: List<StoredExercise> = listOf(done)): EngineResult<ExposureOutcome> {
        val r = exposure(state, ex, done, day, ctx, known, recent)
        return EngineResult(r.value, r.decisions.map { it.forExercise(ex.id) })
    }

    private fun exposure(state: ExerciseState, ex: Exercise, done: StoredExercise, day: Int, ctx: ProgressContext, known: KnownNumber?,
                         recent: List<StoredExercise>): EngineResult<ExposureOutcome> {
        require(state.exerciseId == ex.id && done.exerciseId == ex.id) { "exercise mismatch" }
        val d = ArrayList<Decision>()
        val item = done.doc
        var s = state.copy(lastDoneDay = day, exposures = state.exposures + 1)

        if (ex.loadType !in LOADED || ex.assisted) return ladder(s, ex, recent, ctx, d)
        val avail = available(ex, ctx.inventory)
        if (avail.isEmpty()) return EngineResult(ExposureOutcome(s), d)

        // CAL-002: the number is used once, on the first exposure without an e1RM; an old number caps calibration while it runs.
        var seed: Double? = null
        var ceiling: Double? = state.calibration?.ceiling
        if (!state.knownApplied && state.e1rm == null && known != null) {
            val k = known(known, ex, day, ctx, calibrationReps(item.reps), item.targetRir).also { d += it.decisions }.value
            when (k) {
                is KnownStart.Estimate -> seed = k.seedE1rm
                is KnownStart.Ceiling -> ceiling = k.maxLoad
                KnownStart.Invalid -> Unit
            }
            s = s.copy(knownApplied = true)
        }

        // CAL-001 ramp, replayed in the order logged — only for an exposure the generator planned as calibration.
        val ramp = if (!item.calibrating) emptyList() else done.calibration.filter { it.loadKg != null && it.reps != null }.sortedWith(compareBy({ it.setIndex }, { it.id }))
        if (ramp.isNotEmpty()) {
            val target = calibrationReps(item.reps)
            val sessions = (state.calibration?.sessions ?: 0) + 1
            var last: CalibrationStep? = null
            var lastRated: Double? = null
            var prevRated: Double? = null
            var missed = false
            var lastRir = 0.0
            var lastReps = target
            for ((i, set) in ramp.withIndex()) {
                val rir = set.rir ?: set.rpe?.let { 10.0 - it } ?: break // an unrated set ends the replay: next time starts from the last rated load
                prevRated = lastRated
                lastRated = set.loadKg
                lastRir = rir
                missed = set.reps!! < 1
                // The reps done, not the reps asked for: a set short of its target is what the estimate and the working load go by.
                lastReps = minOf(target, set.reps!!)
                val step = Calibration.next(set.loadKg!!, lastReps, rir, i + 1, avail, ceiling, Calibration.coarseStepsAllowed(ex.loadType, ctx.coarseFreeWeights))
                d += step.decisions
                last = step.value
                if (last !is CalibrationStep.Continue) break
            }
            // An unfinished ramp restarts from the last load the user rated (every rated set before it was easy), else from where it began.
            val restart = lastRated ?: ramp.first().loadKg!!
            // A ramp still easy at its set limit goes on next session; CAL-001 runs over sessions 1–4, and after the 4th progression takes
            // over from the best load the ramp allowed.
            fun next(load: Double) = if (sessions < P.CAL_001.sessions[1]) s.copy(calibration = CalibrationState(load, sessions, ceiling))
                else found(s, load, item, day)
            s = when (val l = last) {
                is CalibrationStep.Found -> when {
                    l.startE1rm != null -> s.copy(e1rm = l.startE1rm, e1rmDay = day, calibration = null)
                    // The 5-set limit reached with the load still easy (RIR 4+): not found yet.
                    l.stillEasy -> next(l.workingLoad)
                    // RIR 3 without a valid estimate (INT-004), or no safe heavier step (D-038): this is the working load.
                    else -> found(s, l.workingLoad, item, day)
                }
                // A set not lifted at all (0 reps) says nothing about what can be lifted: the last load lifted before it is the working
                // load, and with none the ramp goes on next session from 5% lower (review of D-115).
                is CalibrationStep.Stop -> if (missed) prevRated?.let { found(s, it, item, day) } ?: next(l.nextSessionLoad) else {
                    // RIR ≤ 2: "stop; next session uses this load −5%" (CAL-001 step 2) — and no more than the stopping set supports. Its
                    // e1RM, where valid, is the starting estimate ("from the final calibration set", step 4), and the working load is the
                    // lower of the two, so a set short of its reps (6 of 10) never leads to working sets near failure (review of D-115).
                    val e = E1rm.fromSet(lastRated!!, lastReps, lastRir)
                    val supported = e?.let { est -> E1rm.loadFor(est, item.reps.first, item.targetRir).let { x -> avail.filter { it <= x + 1e-9 }.maxOrNull() ?: avail.min() } }
                    found(s, minOf(l.nextSessionLoad, supported ?: l.nextSessionLoad), item, day, e)
                }
                is CalibrationStep.Continue, null -> next(restart)
            }
        } else if (s.e1rm == null && item.calibrating && done.working.isEmpty()) {
            return EngineResult(ExposureOutcome(s), d)
        }

        // Working sets: e1RM and the next-exposure prescription.
        val work = done.workingLogs().filter { it.load > 0.0 }
        if (work.isNotEmpty() && item.unit == DoseUnit.REPS) {
            if (ex.trackE1rm) {
                val session = E1rm.sessionBest(work)
                val u = E1rm.update(s.e1rm ?: seed, session)
                d += u.decisions
                u.value?.let { s = s.copy(e1rm = it, e1rmDay = day, calibration = null) }
            }
            if (item.loadFactor >= 1.0 - 1e-9) {
                val load = work.groupingBy { it.load }.eachCount().entries.sortedWith(compareBy({ -it.value }, { it.key })).first().key
                val formNo = if (work.any { it.formOk == FormCheck.NO }) s.formNoStreak + 1 else 0
                val next = Progression.next(ExposureInput(ex, item.reps, item.targetRir, load, work, avail, ctx.recoveryOk, s.previousWasReduction,
                    s.loadBeforeReductions, formNo, ctx.level, ctx.age))
                d += next.decisions
                val reduced = next.value.action == ProgressionAction.REDUCE || next.value.action == ProgressionAction.REDUCE_AND_REVIEW
                s = s.copy(prescription = next.value, prescriptionDay = day, formNoStreak = formNo, previousWasReduction = reduced,
                    loadBeforeReductions = if (!reduced) null else if (s.previousWasReduction) s.loadBeforeReductions ?: load else load,
                    calibration = null)
            }
        }
        return EngineResult(ExposureOutcome(s), d)
    }

    /**
     * CAL-001 step 4 (D-115): the working load is found, so calibration ends and the next exposure confirms it with working sets; normal
     * progression follows from them. (Before, a load found without a valid e1RM was ramped to again for up to four sessions, so the
     * exercise had no working sets for weeks — Phase 4 scenario S19.)
     */
    private fun found(s: ExerciseState, load: Double, item: com.personalfitnesscoach.data.core.session.ItemDoc, day: Int, e1rm: Double? = null): ExerciseState =
        s.copy(calibration = null, prescription = Prescription(load, item.reps, item.reps.first, ProgressionAction.HOLD), prescriptionDay = day,
            e1rm = e1rm ?: s.e1rm, e1rmDay = if (e1rm != null) day else s.e1rmDay)

    /** Bodyweight, assisted and core: the ladder decides the next rung, reps or assistance (BW-001, BW-002, CORE-002). */
    private fun ladder(s0: ExerciseState, ex: Exercise, recent: List<StoredExercise>, ctx: ProgressContext, d: MutableList<Decision>): EngineResult<ExposureOutcome> {
        val history = recent.map { e ->
            BwSession(e.working.mapNotNull { set -> (set.reps ?: set.seconds)?.let { BwSet(it, set.rir ?: set.rpe?.let { r -> 10.0 - r }) } },
                if (ex.assisted) e.working.mapNotNull { it.loadKg }.minOrNull() else null)
        }.filter { it.sets.isNotEmpty() }
        if (history.isEmpty()) return EngineResult(ExposureOutcome(s0), d)
        val step = if (ex.assisted) ex.allEquipment.sorted().firstNotNullOfOrNull { ctx.inventory.stacks[it] }?.stepKg ?: ctx.inventory.stack.stepKg else 5.0
        val r = Bodyweight.next(ex, history, Library.progressionOf(ex), Library.regressionOf(ex), step)
        d += r.decisions
        val dec = r.value
        var s = s0
        val assistance = dec.assistanceKg
        if (ex.assisted && assistance != null) {
            s = s.copy(prescription = Prescription(assistance, ex.defaultRepRange, dec.targetReps,
                if (dec.action == BwAction.LESS_ASSISTANCE) ProgressionAction.LOAD_UP else ProgressionAction.HOLD), prescriptionDay = s.lastDoneDay)
        }
        val fam = ex.family
        val ladder = if (fam != null && dec.exerciseId != ex.id && (dec.action == BwAction.STEP_UP || dec.action == BwAction.STEP_DOWN)) fam to dec.exerciseId else null
        return EngineResult(ExposureOutcome(s, ladder), d)
    }
}

/**
 * The exercise a decision is about, kept in its inputs, so "Why did my target change?" finds the decisions of one exercise
 * (the engine's progression results don't name it; review R6-08).
 */
fun Decision.forExercise(exerciseId: String): Decision = if (inputs.containsKey("exercise")) this else copy(inputs = inputs + ("exercise" to exerciseId))
