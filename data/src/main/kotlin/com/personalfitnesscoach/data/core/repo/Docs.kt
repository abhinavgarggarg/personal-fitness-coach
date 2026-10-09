package com.personalfitnesscoach.data.core.repo

import com.personalfitnesscoach.data.core.model.ConditionsRecord
import com.personalfitnesscoach.data.core.model.DecisionEntry
import com.personalfitnesscoach.data.core.model.DocCodec
import com.personalfitnesscoach.data.core.model.EquipmentRecord
import com.personalfitnesscoach.data.core.model.ExerciseState
import com.personalfitnesscoach.data.core.model.KnownNumber
import com.personalfitnesscoach.data.core.model.PainRecord
import com.personalfitnesscoach.data.core.model.PreferencesRecord
import com.personalfitnesscoach.data.core.model.Profile
import com.personalfitnesscoach.data.core.model.ProgramRecord
import com.personalfitnesscoach.data.core.model.ReadinessRecord
import com.personalfitnesscoach.data.core.model.RecoveryRecord
import com.personalfitnesscoach.data.core.model.SINGLE
import com.personalfitnesscoach.data.core.model.ScreeningRecord
import com.personalfitnesscoach.data.core.model.SettingsRecord
import com.personalfitnesscoach.data.core.model.StepCounterRecord
import com.personalfitnesscoach.data.core.model.StepStateRecord
import com.personalfitnesscoach.data.core.model.StepsRecord
import com.personalfitnesscoach.data.core.model.WaistRecord
import com.personalfitnesscoach.data.core.model.WalkRecord
import com.personalfitnesscoach.data.core.model.WeekPlanRecord
import com.personalfitnesscoach.data.core.model.WeekSummary
import com.personalfitnesscoach.data.core.model.WeightRecord
import com.personalfitnesscoach.data.core.privacy.DataItem
import com.personalfitnesscoach.data.core.privacy.RecordKind
import com.personalfitnesscoach.data.core.privacy.StoredType
import com.personalfitnesscoach.data.core.store.DocRow
import com.personalfitnesscoach.data.core.store.RowStore
import com.personalfitnesscoach.data.core.time.AppClock

/**
 * Every kind of record the app stores (DATA-001): the document types and the four session tables. Nothing else can be written —
 * [Docs.put] refuses a type not listed here, and the backup carries only these.
 */
object DataSchema {
    /** Database schema version (Room) and backup payload version; they move together. */
    const val SCHEMA_VERSION = 1

    val documents: List<DocCodec<*>> = listOf(
        Profile, ScreeningRecord, ConditionsRecord, EquipmentRecord, PreferencesRecord, SettingsRecord,
        ProgramRecord, WeekSummary, WeekPlanRecord, ExerciseState, KnownNumber, DecisionEntry,
        ReadinessRecord, PainRecord, RecoveryRecord, WeightRecord, WaistRecord, StepsRecord, StepStateRecord, StepCounterRecord, WalkRecord,
    )

    val sessionTables: List<StoredType> = listOf(
        StoredType("workout", RecordKind.COLLECTED, setOf(DataItem.SESSION_RPE_DURATION, DataItem.DAYS, DataItem.SESSION_LENGTH),
            "Each workout: date, status, session rating and minutes, the plan it followed"),
        StoredType("workout_exercise", RecordKind.DERIVED, setOf(DataItem.LOGGED_SETS, DataItem.EXERCISE_PREFERENCES), "The exercises of each workout and their dose"),
        StoredType("logged_set", RecordKind.COLLECTED, setOf(DataItem.LOGGED_SETS), "Every set you logged: load, reps, effort, form check"),
        StoredType("active_session", RecordKind.APP, emptySet(), "Where you are in an unfinished workout, so it can resume"),
    )

    private val byType: Map<String, DocCodec<*>> = documents.associateBy { it.type }

    init {
        require(byType.size == documents.size) { "duplicate document type" }
    }

    fun codec(type: String): DocCodec<*>? = byType[type]

    val allTypes: List<StoredType> get() = documents.map { it.stored } + sessionTables
}

/** Typed access to the documents of a [RowStore]. Every write stamps the time; every read decodes and validates. */
class Docs(private val store: RowStore, private val clock: AppClock) {
    private fun check(c: DocCodec<*>) = require(DataSchema.codec(c.type) === c) { "unknown record type ${c.type}" }

    suspend fun <T> get(c: DocCodec<T>, key: String = SINGLE): T? = store.doc(c.type, key)?.let { c.decode(it.json) }
    suspend fun <T> all(c: DocCodec<T>): List<T> = store.docs(c.type).map { c.decode(it.json) }
    suspend fun <T> between(c: DocCodec<T>, fromDay: Int, toDay: Int): List<T> = store.docsBetween(c.type, fromDay, toDay).map { c.decode(it.json) }

    suspend fun <T> put(c: DocCodec<T>, v: T) {
        check(c)
        store.putDoc(DocRow(c.type, c.key(v), c.day(v), c.encode(v), clock.nowMs()))
    }

    suspend fun <T> delete(c: DocCodec<T>, key: String = SINGLE) { check(c); store.deleteDoc(c.type, key) }
    suspend fun <T> deleteAll(c: DocCodec<T>) { check(c); store.deleteDocs(c.type) }

    /** Reads, changes and writes one record atomically. `change` gets null when there is none yet. */
    suspend fun <T> update(c: DocCodec<T>, key: String = SINGLE, change: (T?) -> T): T = store.transaction {
        val next = change(get(c, key))
        require(c.key(next) == key) { "update changed the key" }
        put(c, next)
        next
    }
}
