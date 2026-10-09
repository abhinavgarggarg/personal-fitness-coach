package com.personalfitnesscoach.data.core.model

import com.personalfitnesscoach.data.core.json.DataFormatException
import com.personalfitnesscoach.data.core.json.Js
import com.personalfitnesscoach.data.core.json.Obj
import com.personalfitnesscoach.data.core.json.checkVersion
import com.personalfitnesscoach.data.core.json.obj
import com.personalfitnesscoach.data.core.privacy.DataItem
import com.personalfitnesscoach.data.core.privacy.RecordKind
import com.personalfitnesscoach.data.core.privacy.StoredType
import kotlinx.serialization.json.JsonObject

/**
 * How one record type is stored as a document (decision D-073): its type name, record version, key and training day, the
 * DATA-001 items it holds, and the JSON encoding. Reading a record of a newer version than this app knows fails with
 * [DataFormatException] (restore refuses such files); older versions are upgraded in [read] (none exist yet: every record is v1).
 */
abstract class DocCodec<T>(val type: String, val version: Int, val kind: RecordKind, val items: Set<DataItem>, val what: String) {
    abstract fun key(v: T): String
    open fun day(v: T): Int? = null
    protected abstract fun write(v: T, o: Obj)
    protected abstract fun read(o: JsonObject, version: Int): T

    fun toJson(v: T): JsonObject = obj { put("v", version); write(v, this) }
    fun encode(v: T): String = toJson(v).toString()
    fun decode(text: String): T = fromJson(Js.parse(text))
    fun fromJson(o: JsonObject): T {
        val ver = o.checkVersion(type, version)
        return try {
            read(o, ver)
        } catch (e: DataFormatException) {
            throw DataFormatException("$type: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            // Engine types validate themselves (e.g. ratings 1–5); a stored value outside the range is unreadable data.
            throw DataFormatException("$type: ${e.message}", e)
        }
    }

    val stored: StoredType get() = StoredType(type, kind, items, what)
}

/** Records with exactly one instance (profile, settings …) use this key. */
const val SINGLE = "current"

/** Day-keyed documents use a zero-padded key so key order is date order. */
fun dayKey(day: Int): String = day.toString().padStart(7, '0')
