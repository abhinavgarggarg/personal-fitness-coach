package com.personalfitnesscoach.data.room

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** Typed documents: one row per record, unique by (type, key); `day` indexes date-range queries (decision D-073). */
@Entity(tableName = "doc", primaryKeys = ["type", "key"], indices = [Index(value = ["type", "day"])])
data class DocEntity(
    val type: String,
    val key: String,
    val day: Int?,
    val json: String,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long,
)

@Entity(tableName = "workout", indices = [Index(value = ["day"]), Index(value = ["status"])])
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val day: Int,
    val status: String,
    val template: String,
    val tier: String?,
    @ColumnInfo(name = "started_at_ms") val startedAtMs: Long?,
    @ColumnInfo(name = "ended_at_ms") val endedAtMs: Long?,
    @ColumnInfo(name = "session_rpe") val sessionRpe: Double?,
    @ColumnInfo(name = "actual_minutes") val actualMinutes: Double?,
    @ColumnInfo(name = "registry_version") val registryVersion: String,
    val json: String,
)

@Entity(
    tableName = "workout_exercise",
    foreignKeys = [ForeignKey(entity = WorkoutEntity::class, parentColumns = ["id"], childColumns = ["workout_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["workout_id", "position"]), Index(value = ["exercise_id"])],
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(name = "workout_id") val workoutId: Long,
    val position: Int,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "slot_key") val slotKey: String,
    val status: String,
    val json: String,
)

@Entity(
    tableName = "logged_set",
    foreignKeys = [ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["workout_exercise_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["workout_exercise_id", "set_index"])],
)
data class SetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(name = "workout_exercise_id") val workoutExerciseId: Long,
    @ColumnInfo(name = "set_index") val setIndex: Int,
    val kind: String,
    @ColumnInfo(name = "load_kg") val loadKg: Double?,
    val reps: Int?,
    val seconds: Int?,
    val metres: Double?,
    val rir: Double?,
    val rpe: Double?,
    @ColumnInfo(name = "form_check") val formCheck: String,
    val deviation: String,
    @ColumnInfo(name = "logged_at_ms") val loggedAtMs: Long,
)

/** The single active session (id is always 1), so the app can resume exactly where you stopped. */
@Entity(
    tableName = "active_session",
    foreignKeys = [ForeignKey(entity = WorkoutEntity::class, parentColumns = ["id"], childColumns = ["workout_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["workout_id"])],
)
data class ActiveEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "workout_id") val workoutId: Long,
    val json: String,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long,
)
