package com.personalfitnesscoach.data.core.privacy

import com.personalfitnesscoach.data.core.Fixtures
import com.personalfitnesscoach.data.core.json.Obj
import com.personalfitnesscoach.data.core.model.CodecTest
import com.personalfitnesscoach.data.core.model.DocCodec
import com.personalfitnesscoach.data.core.repo.DataSchema
import com.personalfitnesscoach.engine.registry.P
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** DATA-001 1.1.0: 11 required items, 9 optional (6 + 3 added by Research Update 1.1), the "never" list never collected; all on-device. */
class DataTaxonomyTest {

    /** TC-DATA-001a: the taxonomy in code is the registry's, and every stored record maps only to allowed items. */
    @Test fun `TC-DATA-001a every stored record maps to required or optional DATA-001 items`() {
        assertEquals(P.DATA_001.required.toSet(), DataItem.entries.filter { it.itemClass == ItemClass.REQUIRED }.map { it.key }.toSet())
        assertEquals(P.DATA_001.optional.toSet(), DataItem.entries.filter { it.itemClass == ItemClass.OPTIONAL }.map { it.key }.toSet())
        assertEquals(P.DATA_001.optional_add.toSet(), DataItem.entries.filter { it.itemClass == ItemClass.OPTIONAL_ADDED }.map { it.key }.toSet())
        assertEquals(P.DATA_001.never.toSet(), DataItem.entries.filter { it.itemClass == ItemClass.NEVER }.map { it.key }.toSet())
        assertEquals(11, P.DATA_001.required.size)
        assertEquals(9, P.DATA_001.optional.size + P.DATA_001.optional_add.size)

        for (t in DataSchema.allTypes) {
            assertTrue("${t.type} names what it holds", t.what.isNotBlank())
            assertTrue("${t.type} holds only allowed items: ${t.items}", t.items.all { it.allowed })
            when (t.kind) {
                RecordKind.COLLECTED -> assertTrue("${t.type} collects something", t.items.isNotEmpty())
                RecordKind.DERIVED -> assertTrue("${t.type} is derived from collected items", t.items.isNotEmpty())
                RecordKind.APP -> assertTrue("${t.type} is an app setting with no personal data", t.items.isEmpty())
            }
        }
        // Every required item is actually stored somewhere (the app can work), and nothing is stored that no item covers.
        val stored = DataSchema.allTypes.filter { it.kind == RecordKind.COLLECTED }.flatMap { it.items }.toSet()
        val required = DataItem.entries.filter { it.itemClass == ItemClass.REQUIRED }.toSet()
        assertTrue("required items without storage: ${required - stored}", stored.containsAll(required))
    }

    /** Words in a field name that would mean a "never" item is being stored (location, contacts, media, ad IDs, social, calories, lifetime totals). */
    private val forbidden = setOf("lat", "latitude", "lon", "lng", "longitude", "gps", "location", "geo", "address", "contact", "contacts", "phone", "email",
        "photo", "photos", "image", "camera", "video", "microphone", "mic", "audio", "advertising", "gaid", "adid", "social", "facebook", "instagram",
        "twitter", "calorie", "calories", "kcal", "food", "meal", "lifetime", "tonnage", "fit", "health", "connect", "fitbit")

    /** "loadKg" → [load, kg]; "step_counter" → [step, counter]. */
    private fun words(field: String): List<String> =
        field.split('_').flatMap { it.split(Regex("(?<=[a-z0-9])(?=[A-Z])")) }.map { it.lowercase() }.filter { it.isNotEmpty() }

    private fun keys(e: JsonElement, out: MutableSet<String>) {
        when (e) {
            is JsonObject -> e.forEach { (k, v) -> out += k; keys(v, out) }
            is JsonArray -> e.forEach { keys(it, out) }
            else -> Unit
        }
    }

    /** TC-DATA-001b: no stored record maps to a "never" item, no stored field names one, and nothing outside the schema can be written. */
    @Test fun `TC-DATA-001b listed exclusions are never collected and only schema types can be stored`() {
        for (t in DataSchema.allTypes) assertTrue("${t.type} maps to a never item", t.items.none { it.itemClass == ItemClass.NEVER })
        // Every field of every record type, taken from fully populated samples.
        val fields = HashSet<String>()
        for ((c, v) in CodecTest().samples) {
            @Suppress("UNCHECKED_CAST")
            keys((c as DocCodec<Any>).toJson(v), fields)
        }
        val hits = fields.filter { f -> words(f).any { it in forbidden } }
        assertTrue("sample covers the fields", fields.size > 100)
        assertTrue("fields naming excluded data: $hits", hits.isEmpty())
        // Steps come only from this phone's sensor: the record has no source field for other apps.
        assertTrue(DataSchema.codec("steps")!!.items == setOf(DataItem.DAILY_STEPS_PHONE_SENSOR))

        // A record type that is not in the schema cannot be written.
        val rogue = object : DocCodec<String>("location", 1, RecordKind.COLLECTED, setOf(DataItem.LOCATION), "where you are") {
            override fun key(v: String) = "x"
            override fun write(v: String, o: Obj) { o.put("place", v) }
            override fun read(o: JsonObject, version: Int) = ""
        }
        runBlocking {
            val (d, _) = Fixtures.data()
            try {
                d.docs.put(rogue, "home")
                fail("a type outside the schema was stored")
            } catch (e: IllegalArgumentException) {
                // expected
            }
            assertTrue(d.store.snapshot().isEmpty)
        }
    }
}
