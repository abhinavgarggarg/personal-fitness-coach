package com.personalfitnesscoach.data.room

import androidx.room3.useWriterConnection
import androidx.room3.withWriteTransaction
import com.personalfitnesscoach.data.core.store.ActiveRow
import com.personalfitnesscoach.data.core.store.DocRow
import com.personalfitnesscoach.data.core.store.ExerciseRow
import com.personalfitnesscoach.data.core.store.RowStore
import com.personalfitnesscoach.data.core.store.SetRow
import com.personalfitnesscoach.data.core.store.Snapshot
import com.personalfitnesscoach.data.core.store.WorkoutRow

/** [RowStore] on Room (decision D-073). Every multi-row change runs in one write transaction. */
class RoomRowStore(private val db: PfcDatabase) : RowStore {
    private val dao = db.dao()

    override suspend fun <R> transaction(block: suspend () -> R): R = db.withWriteTransaction { block() }

    // ---------------------------------------------------------------- documents
    override suspend fun doc(type: String, key: String): DocRow? = dao.doc(type, key)?.toRow()
    override suspend fun docs(type: String): List<DocRow> = dao.docs(type).map { it.toRow() }
    override suspend fun docsBetween(type: String, fromDay: Int, toDay: Int): List<DocRow> = dao.docsBetween(type, fromDay, toDay).map { it.toRow() }
    override suspend fun putDoc(row: DocRow) = dao.putDoc(row.toEntity())
    override suspend fun deleteDoc(type: String, key: String) = dao.deleteDoc(type, key)
    override suspend fun deleteDocs(type: String) = dao.deleteDocs(type)

    // ---------------------------------------------------------------- sessions
    override suspend fun insertWorkout(row: WorkoutRow): Long = dao.insertWorkout(row.toEntity())
    override suspend fun updateWorkout(row: WorkoutRow) { check(dao.updateWorkout(row.toEntity()) == 1) { "no workout ${row.id}" } }
    override suspend fun workout(id: Long): WorkoutRow? = dao.workout(id)?.toRow()
    override suspend fun workoutsBetween(fromDay: Int, toDay: Int): List<WorkoutRow> = dao.workoutsBetween(fromDay, toDay).map { it.toRow() }
    override suspend fun deleteWorkout(id: Long) = dao.deleteWorkout(id)

    override suspend fun insertExercise(row: ExerciseRow): Long = dao.insertExercise(row.toEntity())
    override suspend fun updateExercise(row: ExerciseRow) { check(dao.updateExercise(row.toEntity()) == 1) { "no exercise ${row.id}" } }
    override suspend fun exercise(id: Long): ExerciseRow? = dao.exercise(id)?.toRow()
    override suspend fun exercisesOf(workoutId: Long): List<ExerciseRow> = dao.exercisesOf(workoutId).map { it.toRow() }
    override suspend fun deleteExercise(id: Long) = dao.deleteExercise(id)
    override suspend fun exerciseHistory(exerciseId: String, fromDay: Int, toDay: Int): List<ExerciseRow> =
        dao.exerciseHistory(exerciseId, fromDay, toDay).map { it.toRow() }

    override suspend fun insertSet(row: SetRow): Long = dao.insertSet(row.toEntity())
    override suspend fun updateSet(row: SetRow) { check(dao.updateSet(row.toEntity()) == 1) { "no set ${row.id}" } }
    override suspend fun deleteSet(id: Long) = dao.deleteSet(id)
    override suspend fun setsOf(workoutExerciseId: Long): List<SetRow> = dao.setsOf(workoutExerciseId).map { it.toRow() }

    // ---------------------------------------------------------------- active session
    override suspend fun active(): ActiveRow? = dao.active()?.let { ActiveRow(it.workoutId, it.json, it.updatedAtMs) }
    override suspend fun putActive(row: ActiveRow) = dao.putActive(ActiveEntity(1, row.workoutId, row.json, row.updatedAtMs))
    override suspend fun clearActive() = dao.clearActive()

    // ---------------------------------------------------------------- whole store
    override suspend fun snapshot(): Snapshot = db.withWriteTransaction {
        Snapshot(dao.allDocs().map { it.toRow() }, dao.allWorkouts().map { it.toRow() }, dao.allExercises().map { it.toRow() },
            dao.allSets().map { it.toRow() }, active())
    }

    override suspend fun replaceAll(snapshot: Snapshot): Unit = db.withWriteTransaction {
        eraseAllInTransaction()
        snapshot.docs.forEach { dao.putDoc(it.toEntity()) }
        snapshot.workouts.forEach { dao.insertWorkout(it.toEntity()) }
        snapshot.exercises.forEach { dao.insertExercise(it.toEntity()) }
        snapshot.sets.forEach { dao.insertSet(it.toEntity()) }
        snapshot.active?.let { putActive(it) }
    }

    /** Erases every row, then compacts the file so deleted records do not linger in free pages or the write-ahead log. */
    override suspend fun eraseAll() {
        db.withWriteTransaction { eraseAllInTransaction() }
        db.useWriterConnection { c ->
            c.usePrepared("PRAGMA wal_checkpoint(TRUNCATE)") { it.step() }
            c.usePrepared("VACUUM") { it.step() }
        }
    }

    /** Workouts cascade to their exercises, sets and the active session. */
    private suspend fun eraseAllInTransaction() {
        dao.clearActive()
        dao.clearWorkouts()
        dao.clearDocs()
    }
}

private fun DocEntity.toRow() = DocRow(type, key, day, json, updatedAtMs)
private fun DocRow.toEntity() = DocEntity(type, key, day, json, updatedAtMs)
private fun WorkoutEntity.toRow() = WorkoutRow(id, day, status, template, tier, startedAtMs, endedAtMs, sessionRpe, actualMinutes, registryVersion, json)
private fun WorkoutRow.toEntity() = WorkoutEntity(id, day, status, template, tier, startedAtMs, endedAtMs, sessionRpe, actualMinutes, registryVersion, json)
private fun ExerciseEntity.toRow() = ExerciseRow(id, workoutId, position, exerciseId, slotKey, status, json)
private fun ExerciseRow.toEntity() = ExerciseEntity(id, workoutId, position, exerciseId, slotKey, status, json)
private fun SetEntity.toRow() = SetRow(id, workoutExerciseId, setIndex, kind, loadKg, reps, seconds, metres, rir, rpe, formCheck, deviation, loggedAtMs)
private fun SetRow.toEntity() = SetEntity(id, workoutExerciseId, setIndex, kind, loadKg, reps, seconds, metres, rir, rpe, formCheck, deviation, loggedAtMs)
