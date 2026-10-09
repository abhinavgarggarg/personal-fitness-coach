package com.personalfitnesscoach.data.core.store

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The storage contract (decision D-073), run against every [RowStore]: [InMemoryRowStoreTest] here and RoomRowStoreTest (Robolectric,
 * the real SQLite schema) in the data module — so the in-memory store the core tests use behaves like the database on the phone.
 */
abstract class RowStoreContract {
    abstract fun newStore(): RowStore

    private fun withStore(block: suspend (RowStore) -> Unit) = runBlocking { block(newStore()) }

    private suspend fun assertFails(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            return
        }
        fail("expected a failure: $what")
    }

    private fun workout(day: Int, status: String = "DONE") = WorkoutRow(day = day, status = status, template = "FB_A", registryVersion = "1.1.1", json = "{\"v\":1}")
    private fun exercise(workoutId: Long, pos: Int, id: String = "ex$pos") = ExerciseRow(workoutId = workoutId, position = pos, exerciseId = id, slotKey = "s$pos",
        status = "DONE", json = "{\"v\":1}")
    private fun set(exId: Long, idx: Int, load: Double = 50.0) = SetRow(workoutExerciseId = exId, setIndex = idx, kind = "WORKING", loadKg = load, reps = 8, rir = 2.0,
        loggedAtMs = 1000L + idx)

    @Test fun `documents are unique by type and key, upserted, ordered and range-queried`() = withStore { s ->
        s.putDoc(DocRow("weight", "0000010", 10, "{\"v\":1,\"a\":1}", 1))
        s.putDoc(DocRow("weight", "0000005", 5, "{\"v\":1,\"a\":2}", 2))
        s.putDoc(DocRow("weight", "0000020", 20, "{\"v\":1,\"a\":3}", 3))
        s.putDoc(DocRow("profile", "current", null, "{\"v\":1}", 4))
        s.putDoc(DocRow("weight", "0000010", 10, "{\"v\":1,\"a\":9}", 5)) // upsert
        assertEquals("{\"v\":1,\"a\":9}", s.doc("weight", "0000010")!!.json)
        assertEquals(listOf(5, 10, 20), s.docs("weight").map { it.day })
        assertEquals(listOf(10, 20), s.docsBetween("weight", 6, 20).map { it.day })
        assertEquals(emptyList<DocRow>(), s.docsBetween("weight", 21, 30))
        assertNull(s.doc("weight", "nope"))
        s.deleteDoc("weight", "0000005")
        assertEquals(2, s.docs("weight").size)
        s.deleteDocs("weight")
        assertTrue(s.docs("weight").isEmpty())
        assertNotNull(s.doc("profile", "current"))
    }

    @Test fun `documents without a day come first, then by day and key`() = withStore { s ->
        s.putDoc(DocRow("t", "b", 3, "{}", 1)); s.putDoc(DocRow("t", "a", null, "{}", 1)); s.putDoc(DocRow("t", "c", 3, "{}", 1)); s.putDoc(DocRow("t", "d", 1, "{}", 1))
        assertEquals(listOf("a", "d", "b", "c"), s.docs("t").map { it.key })
    }

    @Test fun `workouts get increasing ids, update and range queries`() = withStore { s ->
        val a = s.insertWorkout(workout(100))
        val b = s.insertWorkout(workout(98))
        val c = s.insertWorkout(workout(100))
        assertTrue(a in 1 until b && b < c)
        assertEquals(listOf(b, a, c), s.workoutsBetween(90, 110).map { it.id })
        s.updateWorkout(s.workout(a)!!.copy(status = "SKIPPED", sessionRpe = 6.5, actualMinutes = 42.0))
        assertEquals("SKIPPED", s.workout(a)!!.status)
        assertEquals(6.5, s.workout(a)!!.sessionRpe!!, 0.0)
        assertFails("update of a missing workout") { s.updateWorkout(workout(1).copy(id = 999)) }
        assertFails("duplicate id") { s.insertWorkout(workout(1).copy(id = a)) }
    }

    @Test fun `foreign keys are enforced`() = withStore { s ->
        assertFails("exercise without workout") { s.insertExercise(exercise(42, 0)) }
        val w = s.insertWorkout(workout(1))
        val e = s.insertExercise(exercise(w, 0))
        assertFails("set without exercise") { s.insertSet(set(999, 0)) }
        assertFails("active without workout") { s.putActive(ActiveRow(999, "{}", 1)) }
        s.insertSet(set(e, 0))
        assertEquals(1, s.setsOf(e).size)
    }

    @Test fun `deleting a workout cascades to its exercises, sets and the active session`() = withStore { s ->
        val w = s.insertWorkout(workout(1, "IN_PROGRESS"))
        val e1 = s.insertExercise(exercise(w, 0)); val e2 = s.insertExercise(exercise(w, 1))
        s.insertSet(set(e1, 0)); s.insertSet(set(e2, 0)); s.insertSet(set(e2, 1))
        s.putActive(ActiveRow(w, "{\"step\":3}", 5))
        val other = s.insertWorkout(workout(2)); val oe = s.insertExercise(exercise(other, 0)); s.insertSet(set(oe, 0))
        s.deleteExercise(e1)
        assertTrue(s.setsOf(e1).isEmpty())
        s.deleteWorkout(w)
        assertNull(s.workout(w)); assertTrue(s.exercisesOf(w).isEmpty()); assertTrue(s.setsOf(e2).isEmpty()); assertNull(s.active())
        assertEquals(1, s.setsOf(oe).size)
    }

    @Test fun `exercises and sets keep their order and history joins the workout day`() = withStore { s ->
        val w1 = s.insertWorkout(workout(10)); val w2 = s.insertWorkout(workout(5)); val w3 = s.insertWorkout(workout(20))
        s.insertExercise(exercise(w1, 1, "squat")); s.insertExercise(exercise(w1, 0, "row"))
        s.insertExercise(exercise(w2, 0, "squat")); s.insertExercise(exercise(w3, 2, "squat"))
        assertEquals(listOf("row", "squat"), s.exercisesOf(w1).map { it.exerciseId })
        assertEquals(listOf(w2, w1, w3), s.exerciseHistory("squat", 0, 30).map { it.workoutId })
        assertEquals(listOf(w1), s.exerciseHistory("squat", 6, 19).map { it.workoutId })
        val e = s.exercisesOf(w1).first().id
        s.insertSet(set(e, 2)); s.insertSet(set(e, 0)); s.insertSet(set(e, 1))
        assertEquals(listOf(0, 1, 2), s.setsOf(e).map { it.setIndex })
        val first = s.setsOf(e).first()
        s.updateSet(first.copy(reps = 5, rir = 1.0))
        assertEquals(5, s.setsOf(e).first().reps)
        s.deleteSet(first.id)
        assertEquals(listOf(1, 2), s.setsOf(e).map { it.setIndex })
    }

    @Test fun `the active session is a single row`() = withStore { s ->
        val w = s.insertWorkout(workout(1, "IN_PROGRESS"))
        assertNull(s.active())
        s.putActive(ActiveRow(w, "{\"a\":1}", 1)); s.putActive(ActiveRow(w, "{\"a\":2}", 2))
        assertEquals("{\"a\":2}", s.active()!!.json)
        s.clearActive()
        assertNull(s.active())
    }

    @Test fun `a transaction is all or nothing, nested calls join it`() = withStore { s ->
        val before = s.insertWorkout(workout(1))
        try {
            s.transaction {
                s.putDoc(DocRow("weight", "x", 1, "{}", 1))
                val w = s.insertWorkout(workout(2))
                s.transaction { s.insertExercise(exercise(w, 0)) }
                s.insertSet(set(424242, 0)) // foreign key failure
            }
            fail("expected the transaction to fail")
        } catch (e: Throwable) {
            // expected
        }
        assertNull(s.doc("weight", "x"))
        assertEquals(listOf(before), s.workoutsBetween(0, 10).map { it.id })
        val ok = s.transaction { s.insertWorkout(workout(3)) }
        assertTrue(ok > before)
        assertEquals("v", s.transaction { s.transaction { "v" } })
    }

    @Test fun `snapshot and replaceAll keep every row and id, erase empties and never reuses ids`() = withStore { s ->
        val w = s.insertWorkout(workout(7, "IN_PROGRESS"))
        val e = s.insertExercise(exercise(w, 0)); s.insertSet(set(e, 0, 62.5)); s.insertSet(set(e, 1))
        s.putActive(ActiveRow(w, "{\"x\":1}", 9))
        s.putDoc(DocRow("profile", "current", null, "{\"v\":1}", 3)); s.putDoc(DocRow("weight", "0000007", 7, "{\"v\":1}", 4))
        val snap = s.snapshot()
        assertEquals(1, snap.workouts.size); assertEquals(2, snap.sets.size); assertNotNull(snap.active)
        s.eraseAll()
        assertTrue(s.snapshot().isEmpty)
        val fresh = s.insertWorkout(workout(1))
        assertTrue("ids are not reused after erase", fresh > w)
        s.replaceAll(snap)
        assertEquals(snap, s.snapshot())
        val next = s.insertWorkout(workout(8))
        assertTrue(next > w)
    }

    @Test fun `replaceAll is atomic`() = withStore { s ->
        s.putDoc(DocRow("profile", "current", null, "{\"v\":1}", 3))
        val bad = Snapshot(emptyList(), emptyList(), listOf(ExerciseRow(1, 77, 0, "x", "s", "DONE", "{}")), emptyList(), null)
        try { s.replaceAll(bad); fail("expected failure") } catch (e: Throwable) { /* expected */ }
        assertNotNull("the old data is still there", s.doc("profile", "current"))
    }
}

class InMemoryRowStoreTest : RowStoreContract() {
    override fun newStore(): RowStore = InMemoryRowStore()
}
