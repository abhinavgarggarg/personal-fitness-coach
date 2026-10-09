package com.personalfitnesscoach.data.core.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Stored data that cannot be read (wrong type, missing field, unknown value, newer format). Never silently replaced by defaults. */
class DataFormatException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Hand-written JSON over kotlinx-serialization's element API (decision D-074): no compiler plugin, so the same code builds with
 * the app's Kotlin and with the local toolchain, and every stored field is explicit. Objects keep insertion order, so the same
 * record always encodes to the same text.
 */
object Js {
    fun parse(text: String): JsonObject = try {
        Json.parseToJsonElement(text).jsonObject
    } catch (e: IllegalArgumentException) {
        throw DataFormatException("not a JSON object", e)
    }

    fun parseElement(text: String): JsonElement = try {
        Json.parseToJsonElement(text)
    } catch (e: IllegalArgumentException) {
        throw DataFormatException("not JSON", e)
    }
}

/** Builds a JSON object, leaving out null values so records stay small and optional fields stay optional. */
class Obj {
    private val m = LinkedHashMap<String, JsonElement>()
    fun put(k: String, v: String?) { if (v != null) m[k] = JsonPrimitive(v) }
    fun put(k: String, v: Int?) { if (v != null) m[k] = JsonPrimitive(v) }
    fun put(k: String, v: Long?) { if (v != null) m[k] = JsonPrimitive(v) }
    fun put(k: String, v: Double?) {
        if (v == null) return
        if (v.isNaN() || v.isInfinite()) throw DataFormatException("$k is not a finite number")
        m[k] = JsonPrimitive(v)
    }
    fun put(k: String, v: Boolean?) { if (v != null) m[k] = JsonPrimitive(v) }
    fun put(k: String, v: Enum<*>?) { if (v != null) m[k] = JsonPrimitive(v.name) }
    fun put(k: String, v: JsonElement?) { if (v != null && v !is JsonNull) m[k] = v }
    /** Writes `true` only (absent = false), for flags. */
    fun flag(k: String, v: Boolean) { if (v) m[k] = JsonPrimitive(true) }
    fun strings(k: String, v: Collection<String>, sort: Boolean = true) { if (v.isNotEmpty()) m[k] = JsonArray((if (sort) v.sorted() else v.toList()).map { JsonPrimitive(it) }) }
    fun enums(k: String, v: Collection<Enum<*>>) { if (v.isNotEmpty()) m[k] = JsonArray(v.sortedBy { it.ordinal }.map { JsonPrimitive(it.name) }) }
    fun ints(k: String, v: Collection<Int>, sort: Boolean = true) { if (v.isNotEmpty()) m[k] = JsonArray((if (sort) v.sorted() else v.toList()).map { JsonPrimitive(it) }) }
    fun doubles(k: String, v: List<Double>) { if (v.isNotEmpty()) m[k] = JsonArray(v.map { JsonPrimitive(it) }) }
    fun objs(k: String, v: List<JsonObject>) { if (v.isNotEmpty()) m[k] = JsonArray(v) }
    fun stringMap(k: String, v: Map<String, String>) { if (v.isNotEmpty()) m[k] = JsonObject(v.toSortedMap().mapValues { JsonPrimitive(it.value) }) }
    fun doubleMap(k: String, v: Map<String, Double>) { if (v.isNotEmpty()) m[k] = JsonObject(v.toSortedMap().mapValues { JsonPrimitive(it.value) }) }
    fun intMap(k: String, v: Map<String, Int>) { if (v.isNotEmpty()) m[k] = JsonObject(v.toSortedMap().mapValues { JsonPrimitive(it.value) }) }
    fun build(): JsonObject = JsonObject(m)
}

fun obj(block: Obj.() -> Unit): JsonObject = Obj().apply(block).build()

private fun JsonObject.raw(k: String): JsonElement? = this[k]?.takeIf { it !is JsonNull }
private fun missing(k: String): Nothing = throw DataFormatException("missing field '$k'")
private fun bad(k: String, what: String): Nothing = throw DataFormatException("field '$k' is not $what")

private fun JsonObject.prim(k: String): JsonPrimitive? = raw(k)?.let { it as? JsonPrimitive ?: bad(k, "a value") }

fun JsonObject.str(k: String): String = strOrNull(k) ?: missing(k)
fun JsonObject.strOrNull(k: String): String? = prim(k)?.let { if (it.isString) it.content else bad(k, "text") }
fun JsonObject.int(k: String): Int = intOrNull(k) ?: missing(k)
fun JsonObject.intOrNull(k: String): Int? = prim(k)?.let { if (it.isString) bad(k, "a whole number") else it.intOrNull ?: bad(k, "a whole number") }
fun JsonObject.int(k: String, default: Int): Int = intOrNull(k) ?: default
fun JsonObject.long(k: String): Long = longOrNull(k) ?: missing(k)
fun JsonObject.longOrNull(k: String): Long? = prim(k)?.let { if (it.isString) bad(k, "a whole number") else it.longOrNull ?: bad(k, "a whole number") }
fun JsonObject.dbl(k: String): Double = dblOrNull(k) ?: missing(k)
fun JsonObject.dblOrNull(k: String): Double? = prim(k)?.let { p ->
    if (p.isString) bad(k, "a number") else p.doubleOrNull?.takeIf { !it.isNaN() && !it.isInfinite() } ?: bad(k, "a number")
}
fun JsonObject.dbl(k: String, default: Double): Double = dblOrNull(k) ?: default
fun JsonObject.boolOrNull(k: String): Boolean? = prim(k)?.let { if (it.isString) bad(k, "true or false") else it.booleanOrNull ?: bad(k, "true or false") }
fun JsonObject.bool(k: String, default: Boolean = false): Boolean = boolOrNull(k) ?: default
fun JsonObject.objOrNull(k: String): JsonObject? = raw(k)?.let { it as? JsonObject ?: bad(k, "an object") }
fun JsonObject.obj(k: String): JsonObject = objOrNull(k) ?: missing(k)
fun JsonObject.arr(k: String): JsonArray = raw(k)?.let { it as? JsonArray ?: bad(k, "a list") } ?: JsonArray(emptyList())

fun JsonObject.strings(k: String): List<String> = arr(k).map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: bad(k, "a list of text") }
fun JsonObject.ints(k: String): List<Int> = arr(k).map { (it as? JsonPrimitive)?.takeIf { p -> !p.isString }?.intOrNull ?: bad(k, "a list of whole numbers") }
fun JsonObject.doubles(k: String): List<Double> = arr(k).map { (it as? JsonPrimitive)?.takeIf { p -> !p.isString }?.doubleOrNull ?: bad(k, "a list of numbers") }
fun JsonObject.objs(k: String): List<JsonObject> = arr(k).map { it as? JsonObject ?: bad(k, "a list of objects") }
fun JsonObject.stringMap(k: String): Map<String, String> {
    val o = objOrNull(k) ?: return emptyMap()
    return o.mapValues { (key, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.content ?: bad("$k.$key", "text") }
}
fun JsonObject.doubleMap(k: String): Map<String, Double> {
    val o = objOrNull(k) ?: return emptyMap()
    return o.mapValues { (key, v) -> (v as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull ?: bad("$k.$key", "a number") }
}
fun JsonObject.intMap(k: String): Map<String, Int> {
    val o = objOrNull(k) ?: return emptyMap()
    return o.mapValues { (key, v) -> (v as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull ?: bad("$k.$key", "a whole number") }
}

inline fun <reified E : Enum<E>> JsonObject.enumOrNull(k: String): E? = strOrNull(k)?.let { parseEnum<E>(it, k) }
inline fun <reified E : Enum<E>> JsonObject.enum(k: String): E = enumOrNull<E>(k) ?: throw DataFormatException("missing field '$k'")
inline fun <reified E : Enum<E>> JsonObject.enum(k: String, default: E): E = enumOrNull<E>(k) ?: default
inline fun <reified E : Enum<E>> JsonObject.enums(k: String): Set<E> = strings(k).map { parseEnum<E>(it, k) }.toSet()
inline fun <reified E : Enum<E>> parseEnum(name: String, field: String): E =
    enumValues<E>().firstOrNull { it.name == name } ?: throw DataFormatException("field '$field' has an unknown value '$name'")

/** Everything JSON can hold from an engine Decision's inputs/outputs (numbers, text, flags, lists, maps); anything else as text. */
fun anyToJson(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is JsonElement -> v
    is String -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is Int -> JsonPrimitive(v)
    is Long -> JsonPrimitive(v)
    is Double -> if (v.isNaN() || v.isInfinite()) JsonPrimitive(v.toString()) else JsonPrimitive(v)
    is Float -> anyToJson(v.toDouble())
    is Number -> JsonPrimitive(v.toDouble())
    is Enum<*> -> JsonPrimitive(v.name)
    is Map<*, *> -> JsonObject(v.entries.associate { (k, x) -> k.toString() to anyToJson(x) })
    is Iterable<*> -> JsonArray(v.map { anyToJson(it) })
    is Array<*> -> JsonArray(v.map { anyToJson(it) })
    is IntRange -> JsonArray(listOf(JsonPrimitive(v.first), JsonPrimitive(v.last)))
    else -> JsonPrimitive(v.toString())
}

/** Reads `[a, b]` as a range. */
fun JsonObject.range(k: String): IntRange {
    val l = ints(k)
    if (l.size != 2) bad(k, "a range [from, to]")
    return l[0]..l[1]
}

fun JsonArray.isNotEmptyArray(): Boolean = isNotEmpty()

/** The `v` field every stored record carries; a newer version than this app knows is refused, never guessed at. */
fun JsonObject.checkVersion(type: String, current: Int): Int {
    val v = int("v")
    if (v < 1 || v > current) throw DataFormatException("$type record version $v is not supported (this app reads 1–$current)")
    return v
}

/** `jsonObject` / `jsonArray` / `jsonPrimitive` re-exported for codec files. */
val JsonElement.asObj: JsonObject get() = try { jsonObject } catch (e: IllegalArgumentException) { throw DataFormatException("expected an object", e) }
val JsonElement.asArr: JsonArray get() = try { jsonArray } catch (e: IllegalArgumentException) { throw DataFormatException("expected a list", e) }
val JsonElement.asPrim: JsonPrimitive get() = try { jsonPrimitive } catch (e: IllegalArgumentException) { throw DataFormatException("expected a value", e) }
