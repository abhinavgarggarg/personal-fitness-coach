package com.personalfitnesscoach.data.room

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import androidx.room3.Upsert

@Dao
interface PfcDao {
    // ---------------------------------------------------------------- documents
    @Query("SELECT * FROM doc WHERE type = :type AND `key` = :key")
    suspend fun doc(type: String, key: String): DocEntity?

    @Query("SELECT * FROM doc WHERE type = :type ORDER BY CASE WHEN day IS NULL THEN 0 ELSE 1 END, day, `key`")
    suspend fun docs(type: String): List<DocEntity>

    @Query("SELECT * FROM doc WHERE type = :type AND day BETWEEN :fromDay AND :toDay ORDER BY day, `key`")
    suspend fun docsBetween(type: String, fromDay: Int, toDay: Int): List<DocEntity>

    @Upsert
    suspend fun putDoc(row: DocEntity)

    @Query("DELETE FROM doc WHERE type = :type AND `key` = :key")
    suspend fun deleteDoc(type: String, key: String)

    @Query("DELETE FROM doc WHERE type = :type")
    suspend fun deleteDocs(type: String)

    // ---------------------------------------------------------------- workouts
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorkout(row: WorkoutEntity): Long

    @Update
    suspend fun updateWorkout(row: WorkoutEntity): Int

    @Query("SELECT * FROM workout WHERE id = :id")
    suspend fun workout(id: Long): WorkoutEntity?

    @Query("SELECT * FROM workout WHERE day BETWEEN :fromDay AND :toDay ORDER BY day, id")
    suspend fun workoutsBetween(fromDay: Int, toDay: Int): List<WorkoutEntity>

    @Query("DELETE FROM workout WHERE id = :id")
    suspend fun deleteWorkout(id: Long)

    // ---------------------------------------------------------------- workout exercises
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExercise(row: ExerciseEntity): Long

    @Update
    suspend fun updateExercise(row: ExerciseEntity): Int

    @Query("SELECT * FROM workout_exercise WHERE id = :id")
    suspend fun exercise(id: Long): ExerciseEntity?

    @Query("SELECT * FROM workout_exercise WHERE workout_id = :workoutId ORDER BY position, id")
    suspend fun exercisesOf(workoutId: Long): List<ExerciseEntity>

    @Query("DELETE FROM workout_exercise WHERE id = :id")
    suspend fun deleteExercise(id: Long)

    @Query(
        "SELECT e.* FROM workout_exercise e JOIN workout w ON w.id = e.workout_id " +
            "WHERE e.exercise_id = :exerciseId AND w.day BETWEEN :fromDay AND :toDay ORDER BY w.day, w.id, e.position"
    )
    suspend fun exerciseHistory(exerciseId: String, fromDay: Int, toDay: Int): List<ExerciseEntity>

    // ---------------------------------------------------------------- logged sets
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSet(row: SetEntity): Long

    @Update
    suspend fun updateSet(row: SetEntity): Int

    @Query("DELETE FROM logged_set WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("SELECT * FROM logged_set WHERE workout_exercise_id = :workoutExerciseId ORDER BY set_index, id")
    suspend fun setsOf(workoutExerciseId: Long): List<SetEntity>

    // ---------------------------------------------------------------- active session
    @Query("SELECT * FROM active_session WHERE id = 1")
    suspend fun active(): ActiveEntity?

    @Upsert
    suspend fun putActive(row: ActiveEntity)

    @Query("DELETE FROM active_session")
    suspend fun clearActive()

    // ---------------------------------------------------------------- whole store
    @Query("SELECT * FROM doc ORDER BY type, `key`")
    suspend fun allDocs(): List<DocEntity>

    @Query("SELECT * FROM workout ORDER BY id")
    suspend fun allWorkouts(): List<WorkoutEntity>

    @Query("SELECT * FROM workout_exercise ORDER BY id")
    suspend fun allExercises(): List<ExerciseEntity>

    @Query("SELECT * FROM logged_set ORDER BY id")
    suspend fun allSets(): List<SetEntity>

    @Query("DELETE FROM doc")
    suspend fun clearDocs()

    @Query("DELETE FROM workout")
    suspend fun clearWorkouts()
}
