package com.personalfitnesscoach.data.core.session

import com.personalfitnesscoach.data.core.store.ActiveRow
import com.personalfitnesscoach.data.core.store.ExerciseRow
import com.personalfitnesscoach.data.core.store.RowStore
import com.personalfitnesscoach.data.core.store.SetRow
import com.personalfitnesscoach.data.core.store.WorkoutRow
import com.personalfitnesscoach.data.core.time.AppClock
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.engine.generation.Workout
import com.personalfitnesscoach.engine.model.FormCheck
import com.personalfitnesscoach.engine.model.SetLog
import com.personalfitnesscoach.engine.program.DayTemplate
import com.personalfitnesscoach.engine.registry.Registry

/** A workout read back with its document, exercises and sets. */
data class StoredWorkout(val row: WorkoutRow, val doc: WorkoutDoc, val exercises: List<StoredExercise>) {
    val id: Long get() = row.id
    val day: Int get() = row.day
    val template: DayTemplate get() = DayTemplate.valueOf(row.template)
}

data class StoredExercise(val row: ExerciseRow, val doc: ItemDoc, val sets: List<SetRow>) {
    val exerciseId: String get() = row.exerciseId
    val working: List<SetRow> get() = sets.filter { it.kind == SetKind.WORKING }
    val calibration: List<SetRow> get() = sets.filter { it.kind == SetKind.CALIBRATION }

    /** Working sets as the engine's [SetLog] (reps-based sets with a load or bodyweight). */
    fun workingLogs(): List<SetLog> = working.filter { it.reps != null }.map { s ->
        SetLog(s.loadKg ?: 0.0, s.reps!!, s.rir ?: s.rpe?.let { 10.0 - it }, warmup = false, formOk = FormCheck.valueOf(s.formCheck))
    }
}

/** A set the user just logged (the set id and time are assigned on write). */
data class NewSet(
    val setIndex: Int,
    val kind: String,
    val loadKg: Double? = null,
    val reps: Int? = null,
    val seconds: Int? = null,
    val metres: Double? = null,
    val rir: Double? = null,
    val rpe: Double? = null,
    val formCheck: FormCheck = FormCheck.YES,
    val deviation: String = "AS_PLANNED",
) {
    init {
        require(kind in SetKind.all) { "unknown set kind $kind" }
        require(setIndex >= 0) { "set index" }
        require(loadKg == null || loadKg in 0.0..1000.0) { "load out of range" }
        require(reps == null || reps in 0..1000) { "reps out of range" }
        require(seconds == null || seconds in 0..36_000) { "seconds out of range" }
        require(metres == null || metres in 0.0..100_000.0) { "metres out of range" }
        require(rir == null || rir in 0.0..10.0) { "RIR is 0–10" }
        require(rpe == null || rpe in 0.0..10.0) { "RPE is 0–10" }
        require(deviation in setOf("AS_PLANNED", "ADJUSTED", "USER_ADDED")) { "unknown deviation $deviation" }
    }
}

/**
 * Sessions on disk (Phase 2 entities Workout, WorkoutExercise, LoggedSet, ActiveSessionState). Every change that touches more than
 * one row is one transaction (Phase 2 data rule 4): a logged set and the resume point are written together, so a crash can never
 * leave a set without the state that points past it, or the reverse.
 */
class SessionLog(private val store: RowStore, private val clock: AppClock) {

    /** Saves an approved workout (only [Workout.validated]'s content is ever shown, SAF-008) and makes it the active session. */
    suspend fun start(
        workout: Workout,
        template: DayTemplate,
        /** The planned day's weekday (the session may be done on another day, SCH-002/003). */
        weekday: Int,
        express: Boolean = false,
        awayFromGym: Boolean = false,
        inDeload: Boolean = false,
        activeState: String = "{}",
        /** CAL-002 ceilings per exercise ID from the generation request (the ramp never goes above them). */
        calibrationCeilings: Map<String, Double> = emptyMap(),
    ): Long = store.transaction {
        check(store.active() == null) { "another workout is in progress" }
        require(workout.safetyStop == null && workout.followCareProvider.isEmpty()) { "a safety stop has no workout to start" }
        val now = clock.nowMs()
        val day = clock.today()
        val lifts = workout.items.filter { it.sets > 0 }
        val plannedRpe = if (lifts.isEmpty()) null else 10.0 - lifts.sumOf { it.targetRir * it.sets } / lifts.sumOf { it.sets }
        val conditioning = workout.conditioning.mapIndexed { j, b ->
            ConditioningItem.of(b, workout.intervals.getOrNull(j)?.let { IntervalRef.of(it) }, workout.circuits.getOrNull(j)?.let { CircuitRef.of(it) }) }
        val doc = WorkoutDoc(weekday, workout.plannedMinutes, workout.warmupMinutes, workout.validated.cooldownMinutes ?: (workout.cooldown.sumOf { it.seconds } / 60.0), conditioning,
            workout.conditioningFirst, workout.mobilityMinutes, workout.balanceDrills.sumOf { it.seconds } / 60.0, workout.fullTierWorkingSets, inDeload,
            express, awayFromGym, plannedRpe, warmupDrills = workout.warmupDrills.map { DrillRef.of(it) }, cooldownDrills = workout.cooldown.map { DrillRef.of(it) },
            balanceDrills = workout.balanceDrills.map { DrillRef.of(it) },
            boneLoading = workout.boneLoading?.let { BoneRef(it.minutes, it.landings, it.variant) }, effortByFeel = workout.effortByFeel)
        val id = store.insertWorkout(WorkoutRow(day = day, status = Status.IN_PROGRESS, template = template.name, tier = workout.tier.name,
            startedAtMs = now, registryVersion = Registry.VERSION, json = doc.encode()))
        workout.items.forEachIndexed { i, it ->
            val item = ItemDoc(it.role, it.priority, it.sets, it.reps, it.unit, it.perSide, it.targetRir, it.lastSetToFailure, it.load, it.loadFactor,
                it.rest.minSec, it.rest.defaultSec, it.rest.maxSec, it.calibrating, it.main,
                rampSets = it.warmupSets.map { r -> RampRef(r.load, r.reps) },
                reducedRange = it.range == com.personalfitnesscoach.engine.planning.RangeOfMotion.REDUCED,
                calibrationCeiling = if (it.calibrating) calibrationCeilings[it.exercise.id] else null)
            store.insertExercise(ExerciseRow(workoutId = id, position = i, exerciseId = it.exercise.id, slotKey = it.slotKey, status = Status.PLANNED,
                json = item.encode()))
        }
        store.putActive(ActiveRow(id, activeState, now))
        id
    }

    /** Logs one set and moves the resume point, atomically. */
    suspend fun logSet(workoutExerciseId: Long, set: NewSet, activeState: String): Long = store.transaction {
        val ex = store.exercise(workoutExerciseId) ?: error("no workout exercise $workoutExerciseId")
        val w = store.workout(ex.workoutId) ?: error("no workout ${ex.workoutId}")
        check(w.status == Status.IN_PROGRESS) { "workout ${w.id} is not in progress" }
        val now = clock.nowMs()
        val id = store.insertSet(SetRow(workoutExerciseId = workoutExerciseId, setIndex = set.setIndex, kind = set.kind, loadKg = set.loadKg,
            reps = set.reps, seconds = set.seconds, metres = set.metres, rir = set.rir, rpe = set.rpe, formCheck = set.formCheck.name,
            deviation = set.deviation, loggedAtMs = now))
        if (ex.status == Status.PLANNED) store.updateExercise(ex.copy(status = Status.IN_PROGRESS))
        store.putActive(ActiveRow(w.id, activeState, now))
        id
    }

    /** Corrects a logged set (load, reps, effort …) — allowed while the workout is open and afterwards from history. */
    suspend fun correctSet(setId: Long, workoutExerciseId: Long, set: NewSet) = store.transaction {
        val old = store.setsOf(workoutExerciseId).firstOrNull { it.id == setId } ?: error("no set $setId")
        store.updateSet(old.copy(setIndex = set.setIndex, kind = set.kind, loadKg = set.loadKg, reps = set.reps, seconds = set.seconds,
            metres = set.metres, rir = set.rir, rpe = set.rpe, formCheck = set.formCheck.name, deviation = set.deviation))
    }

    suspend fun deleteSet(setId: Long) = store.deleteSet(setId)

    /** Saves the resume point alone (rest timer started, sheet opened …). */
    suspend fun saveState(workoutId: Long, activeState: String) = store.transaction {
        val a = store.active()
        check(a != null && a.workoutId == workoutId) { "workout $workoutId is not the active session" }
        store.putActive(ActiveRow(workoutId, activeState, clock.nowMs()))
    }

    /** Swaps a planned exercise for another (SUB-001); the new dose replaces the old, the old ID is kept for history. */
    suspend fun swap(workoutExerciseId: Long, newExerciseId: String, newDose: ItemDoc) = store.transaction {
        val ex = store.exercise(workoutExerciseId) ?: error("no workout exercise $workoutExerciseId")
        check(store.setsOf(workoutExerciseId).none { it.kind != SetKind.WARMUP }) { "sets already logged for ${ex.exerciseId}" }
        store.updateExercise(ex.copy(exerciseId = newExerciseId, status = Status.PLANNED, json = newDose.copy(swappedFrom = ex.exerciseId).encode()))
    }

    /** Replaces an exercise's dose during the session (a re-fit to the time left, a pain-gate load reduction, an added set). */
    suspend fun updateItem(workoutExerciseId: Long, doc: ItemDoc) = store.transaction {
        val ex = store.exercise(workoutExerciseId) ?: error("no workout exercise $workoutExerciseId")
        val w = store.workout(ex.workoutId) ?: error("no workout ${ex.workoutId}")
        check(w.status == Status.IN_PROGRESS) { "workout ${w.id} is not in progress" }
        store.updateExercise(ex.copy(json = doc.encode()))
    }

    /** Replaces the workout's document during the session (conditioning re-fitted to the time left). */
    suspend fun updateDoc(workoutId: Long, doc: WorkoutDoc) = store.transaction {
        val w = store.workout(workoutId) ?: error("no workout $workoutId")
        check(w.status == Status.IN_PROGRESS) { "workout $workoutId is not in progress" }
        store.updateWorkout(w.copy(json = doc.encode()))
    }

    /** Inserts an exercise at `position` (later ones move down): a pain-free alternative after SAF-003 stops one. */
    suspend fun addExercise(workoutId: Long, position: Int, exerciseId: String, slotKey: String, doc: ItemDoc): Long = store.transaction {
        val w = store.workout(workoutId) ?: error("no workout $workoutId")
        check(w.status == Status.IN_PROGRESS) { "workout $workoutId is not in progress" }
        val all = store.exercisesOf(workoutId)
        val at = position.coerceIn(0, all.size)
        all.filter { it.position >= at }.sortedByDescending { it.position }.forEach { store.updateExercise(it.copy(position = it.position + 1)) }
        store.insertExercise(ExerciseRow(workoutId = workoutId, position = at, exerciseId = exerciseId, slotKey = slotKey, status = Status.PLANNED, json = doc.encode()))
    }

    /**
     * Moves an exercise to the end of the workout's remaining exercises ("do it later" when the equipment is occupied, EQ-002, SUB-002).
     * Positions are renumbered in order, so the list stays dense.
     */
    suspend fun moveToEnd(workoutExerciseId: Long) = store.transaction {
        val ex = store.exercise(workoutExerciseId) ?: error("no workout exercise $workoutExerciseId")
        val all = store.exercisesOf(ex.workoutId)
        val order = all.filter { it.id != ex.id } + ex
        order.forEachIndexed { i, r -> if (r.position != i) store.updateExercise((if (r.id == ex.id) ex else r).copy(position = i)) }
    }

    suspend fun skipExercise(workoutExerciseId: Long) = store.transaction {
        val ex = store.exercise(workoutExerciseId) ?: error("no workout exercise $workoutExerciseId")
        store.updateExercise(ex.copy(status = Status.SKIPPED))
    }

    /**
     * Ends the workout: the session rating (LOAD-001, asked 10–30 minutes after the session, SrpePrompt) and minutes are stored
     * with the conditioning actually done; the resume point is cleared. Exercises with no logged set are marked skipped.
     */
    suspend fun finish(workoutId: Long, sessionRpe: Double?, actualMinutes: Double?, conditioningDoneMinutes: List<Double?>? = null,
                       endedEarly: Boolean = false): StoredWorkout = store.transaction {
        require(sessionRpe == null || sessionRpe in 0.0..10.0) { "session rating is 0–10" }
        require(actualMinutes == null || actualMinutes in 0.0..600.0) { "minutes out of range" }
        val w = store.workout(workoutId) ?: error("no workout $workoutId")
        check(w.status == Status.IN_PROGRESS) { "workout $workoutId is not in progress" }
        val doc = WorkoutDoc.decode(w.json)
        // Not recorded = null per block (counted as planned for caps and spacing); 0 = skipped.
        val done: List<Double?> = conditioningDoneMinutes ?: doc.conditioning.map { null }
        require(done.size == doc.conditioning.size && done.all { it == null || it in 0.0..600.0 }) { "one done-minutes value per conditioning block" }
        val newDoc = doc.copy(conditioning = doc.conditioning.zip(done) { c, m -> c.copy(doneWorkMinutes = m) }, endedEarly = endedEarly || doc.endedEarly)
        for (ex in store.exercisesOf(workoutId)) {
            val logged = store.setsOf(ex.id).any { it.kind != SetKind.WARMUP }
            val status = when {
                ex.status == Status.SKIPPED -> Status.SKIPPED
                logged -> Status.DONE
                else -> Status.SKIPPED
            }
            if (status != ex.status) store.updateExercise(ex.copy(status = status))
        }
        store.updateWorkout(w.copy(status = Status.DONE, endedAtMs = clock.nowMs(), sessionRpe = sessionRpe, actualMinutes = actualMinutes, json = newDoc.encode()))
        if (store.active()?.workoutId == workoutId) store.clearActive()
        load(workoutId)!!
    }

    /**
     * SAF-002: a red flag ends the workout at once. Sets logged so far stay — they count towards caps, spacing and stress — and the
     * workout is marked [WorkoutDoc.stoppedBySafety] so progression ignores it. Conditioning not yet logged counts as planned.
     */
    suspend fun stopForSafety(workoutId: Long): StoredWorkout = store.transaction {
        val w = store.workout(workoutId) ?: error("no workout $workoutId")
        check(w.status == Status.IN_PROGRESS) { "workout $workoutId is not in progress" }
        for (ex in store.exercisesOf(workoutId)) {
            val logged = store.setsOf(ex.id).any { it.kind != SetKind.WARMUP }
            val status = if (ex.status != Status.SKIPPED && logged) Status.DONE else Status.SKIPPED
            if (status != ex.status) store.updateExercise(ex.copy(status = status))
        }
        val now = clock.nowMs()
        val minutes = w.startedAtMs?.let { ((now - it) / 60_000.0).coerceIn(0.0, 600.0) }
        store.updateWorkout(w.copy(status = Status.DONE, endedAtMs = now, actualMinutes = minutes, json = WorkoutDoc.decode(w.json).copy(stoppedBySafety = true).encode()))
        if (store.active()?.workoutId == workoutId) store.clearActive()
        load(workoutId)!!
    }

    /** Adds the session rating later (it is asked 10–30 minutes after the session). */
    suspend fun rate(workoutId: Long, sessionRpe: Double, actualMinutes: Double? = null) = store.transaction {
        require(sessionRpe in 0.0..10.0) { "session rating is 0–10" }
        val w = store.workout(workoutId) ?: error("no workout $workoutId")
        check(w.status == Status.DONE) { "only a finished workout is rated" }
        store.updateWorkout(w.copy(sessionRpe = sessionRpe, actualMinutes = actualMinutes ?: w.actualMinutes))
    }

    /** Abandons the active workout without results (no sets are kept as training history). */
    suspend fun discard(workoutId: Long) = store.transaction {
        if (store.active()?.workoutId == workoutId) store.clearActive()
        store.deleteWorkout(workoutId)
    }

    /** Records a planned day as skipped (adherence, ADH-001). */
    suspend fun markSkipped(day: Int, template: DayTemplate, weekday: Int): Long = store.insertWorkout(WorkoutRow(day = day, status = Status.SKIPPED,
        template = template.name, registryVersion = Registry.VERSION, json = WorkoutDoc(weekday, 0.0, 0.0).encode()))

    suspend fun active(): Pair<StoredWorkout, ActiveRow>? = store.active()?.let { a -> load(a.workoutId)?.let { it to a } }

    suspend fun load(workoutId: Long): StoredWorkout? {
        val w = store.workout(workoutId) ?: return null
        return StoredWorkout(w, WorkoutDoc.decode(w.json), store.exercisesOf(workoutId).map { ex ->
            StoredExercise(ex, ItemDoc.decode(ex.json), store.setsOf(ex.id)) })
    }

    /** Workouts with a training day in `fromDay..toDay`, oldest first. */
    suspend fun between(fromDay: Int, toDay: Int): List<StoredWorkout> = store.workoutsBetween(fromDay, toDay).mapNotNull { load(it.id) }

    suspend fun done(fromDay: Int, toDay: Int): List<StoredWorkout> = between(fromDay, toDay).filter { it.row.status == Status.DONE }

    /** Logged exposures of one exercise in finished workouts, oldest first (history → e1RM, strength trend). */
    suspend fun history(exerciseId: String, fromDay: Int, toDay: Int): List<Pair<Int, StoredExercise>> =
        store.exerciseHistory(exerciseId, fromDay, toDay).mapNotNull { ex ->
            val w = store.workout(ex.workoutId) ?: return@mapNotNull null
            if (w.status != Status.DONE || ex.status == Status.SKIPPED) return@mapNotNull null
            w.day to StoredExercise(ex, ItemDoc.decode(ex.json), store.setsOf(ex.id))
        }
}
