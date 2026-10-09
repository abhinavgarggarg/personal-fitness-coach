package com.personalfitnesscoach.data.core.store

/**
 * The storage contract every implementation follows: Room on the phone ([com.personalfitnesscoach.data.room.RoomRowStore]) and
 * [InMemoryRowStore] in tests. Decision D-073:
 *  - **Sessions are relational** (workout → workout_exercise → logged_set, foreign keys with cascade), because logging is the
 *    highest-volume, most query-heavy data and must be atomic (Phase 2 data rule 4).
 *  - **Everything else is a typed document** ([DocRow]): one row per record, keyed by (type, key), with an optional training day
 *    for date-range queries and the record itself as versioned JSON (Phase 2 data rule 6). Typed repositories in
 *    [com.personalfitnesscoach.data.core.repo] own the codecs, so the Room layer stays thin and the same code is tested here.
 *
 * Days are local training dates as epoch days; instants are UTC milliseconds (Phase 2 data rule 5).
 */
interface RowStore {
    /** Runs `block` atomically: all of its writes land, or none do. Nested calls join the outer transaction. */
    suspend fun <R> transaction(block: suspend () -> R): R

    // ---------------------------------------------------------------- documents
    suspend fun doc(type: String, key: String): DocRow?
    suspend fun docs(type: String): List<DocRow>
    /** Documents of a type whose day is in `fromDay..toDay` (inclusive), ordered by day then key. */
    suspend fun docsBetween(type: String, fromDay: Int, toDay: Int): List<DocRow>
    suspend fun putDoc(row: DocRow)
    suspend fun deleteDoc(type: String, key: String)
    suspend fun deleteDocs(type: String)

    // ---------------------------------------------------------------- sessions
    /** Inserts a workout; `row.id` 0 means "assign one". Returns the id. */
    suspend fun insertWorkout(row: WorkoutRow): Long
    suspend fun updateWorkout(row: WorkoutRow)
    suspend fun workout(id: Long): WorkoutRow?
    /** Workouts with a day in `fromDay..toDay`, ordered by day then id. */
    suspend fun workoutsBetween(fromDay: Int, toDay: Int): List<WorkoutRow>
    /** Deletes the workout and, by cascade, its exercises and sets. */
    suspend fun deleteWorkout(id: Long)

    suspend fun insertExercise(row: ExerciseRow): Long
    suspend fun updateExercise(row: ExerciseRow)
    suspend fun exercise(id: Long): ExerciseRow?
    /** The workout's exercises ordered by position. */
    suspend fun exercisesOf(workoutId: Long): List<ExerciseRow>
    suspend fun deleteExercise(id: Long)
    /** Logged exercises of one exercise ID from workouts with a day in `fromDay..toDay`, oldest first. */
    suspend fun exerciseHistory(exerciseId: String, fromDay: Int, toDay: Int): List<ExerciseRow>

    suspend fun insertSet(row: SetRow): Long
    suspend fun updateSet(row: SetRow)
    suspend fun deleteSet(id: Long)
    /** Sets of one workout exercise ordered by set index then id. */
    suspend fun setsOf(workoutExerciseId: Long): List<SetRow>

    // ---------------------------------------------------------------- the one active session (powers resume)
    suspend fun active(): ActiveRow?
    suspend fun putActive(row: ActiveRow)
    suspend fun clearActive()

    // ---------------------------------------------------------------- whole store
    /** Every row, for export (backup format v1). */
    suspend fun snapshot(): Snapshot
    /** Replaces everything with `snapshot` atomically (ids kept, so references stay valid). */
    suspend fun replaceAll(snapshot: Snapshot)
    /** "Erase all my data": every row in every table. */
    suspend fun eraseAll()
}

/** A typed document. `day` is set for records that belong to a training date (readiness, weight, steps …), else null. */
data class DocRow(val type: String, val key: String, val day: Int?, val json: String, val updatedAtMs: Long)

data class WorkoutRow(
    val id: Long = 0,
    val day: Int,
    /** PLANNED, IN_PROGRESS, DONE, SKIPPED or MOVED. */
    val status: String,
    val template: String,
    val tier: String? = null,
    val startedAtMs: Long? = null,
    val endedAtMs: Long? = null,
    /** CR10 session rating (LOAD-001) and active minutes, once done. */
    val sessionRpe: Double? = null,
    val actualMinutes: Double? = null,
    val registryVersion: String,
    /** Everything else about the workout (planned day, validated conditioning, warm-up …) as versioned JSON. */
    val json: String,
)

data class ExerciseRow(
    val id: Long = 0,
    val workoutId: Long,
    val position: Int,
    val exerciseId: String,
    val slotKey: String,
    /** PLANNED, DONE, SKIPPED or SWAPPED. */
    val status: String,
    /** The dose (sets, reps, RIR, load, rest, role …) as versioned JSON. */
    val json: String,
)

data class SetRow(
    val id: Long = 0,
    val workoutExerciseId: Long,
    val setIndex: Int,
    /** WARMUP, CALIBRATION or WORKING. */
    val kind: String,
    val loadKg: Double? = null,
    val reps: Int? = null,
    val seconds: Int? = null,
    val metres: Double? = null,
    val rir: Double? = null,
    val rpe: Double? = null,
    /** YES, UNSURE or NO ("form felt solid?"). */
    val formCheck: String = "YES",
    /** AS_PLANNED, ADJUSTED or USER_ADDED (SAF-006 counts additions). */
    val deviation: String = "AS_PLANNED",
    val loggedAtMs: Long,
)

data class ActiveRow(val workoutId: Long, val json: String, val updatedAtMs: Long)

/** Every row of the store, in a stable order. */
data class Snapshot(
    val docs: List<DocRow>,
    val workouts: List<WorkoutRow>,
    val exercises: List<ExerciseRow>,
    val sets: List<SetRow>,
    val active: ActiveRow?,
) {
    val isEmpty: Boolean get() = docs.isEmpty() && workouts.isEmpty() && exercises.isEmpty() && sets.isEmpty() && active == null
}
