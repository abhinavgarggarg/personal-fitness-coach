package com.personalfitnesscoach.data.core.session

import com.personalfitnesscoach.data.core.Fixtures
import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.engine.generation.Workout
import com.personalfitnesscoach.engine.model.FormCheck
import com.personalfitnesscoach.engine.model.Tier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Phase 2 data rule 4: a logged set and the resume point are written together; sessions resume, finish, discard and correct cleanly. */
class SessionLogTest {
    private suspend fun ready(withHistory: Boolean = false): Triple<PfcData, Workout, com.personalfitnesscoach.engine.program.DayTemplate> {
        val (d, _) = Fixtures.data()
        Fixtures.onboard(d)
        if (withHistory) for (e in com.personalfitnesscoach.engine.library.Library.all.filter { it.trackE1rm })
            d.docs.put(com.personalfitnesscoach.data.core.model.ExerciseState, com.personalfitnesscoach.data.core.model.ExerciseState(e.id, e1rm = 60.0, e1rmDay = Fixtures.MONDAY - 3))
        val t = d.planToday()!!
        val day = t.next ?: t.week.days.first()
        return Triple(d, d.generate(t, day, Tier.FULL).value, day.template)
    }

    private suspend fun fails(what: String, block: suspend () -> Unit) {
        try { block() } catch (e: Throwable) { return }
        fail("expected failure: $what")
    }

    @Test fun `start saves the approved workout and makes it the active session`() = runBlocking {
        val (d, w, template) = ready()
        val id = d.sessions.start(w, template, activeState = "{\"exercise\":0}")
        val (stored, active) = d.sessions.active()!!
        assertEquals(id, stored.id)
        assertEquals("{\"exercise\":0}", active.json)
        assertEquals(Status.IN_PROGRESS, stored.row.status)
        assertEquals(w.items.map { it.exercise.id }, stored.exercises.map { it.exerciseId })
        assertEquals(w.items.map { it.sets }, stored.exercises.map { it.doc.sets })
        assertEquals(w.items.map { it.load }, stored.exercises.map { it.doc.load })
        assertEquals(w.conditioning.size, stored.doc.conditioning.size)
        fails("a second workout while one is open") { d.sessions.start(w, template) }
    }

    @Test fun `a logged set and the resume point land together, or neither does`() = runBlocking {
        val (d, w, template) = ready()
        val id = d.sessions.start(w, template)
        val ex = d.sessions.load(id)!!.exercises.first()
        d.sessions.logSet(ex.row.id, NewSet(0, SetKind.WORKING, 40.0, 8, rir = 2.0), "{\"set\":1}")
        assertEquals("{\"set\":1}", d.sessions.active()!!.second.json)
        assertEquals(Status.IN_PROGRESS, d.sessions.load(id)!!.exercises.first().row.status)
        // A set for a missing exercise fails and the resume point does not move.
        fails("foreign key") { d.sessions.logSet(424242, NewSet(1, SetKind.WORKING, 40.0, 8), "{\"set\":2}") }
        assertEquals("{\"set\":1}", d.sessions.active()!!.second.json)
        assertEquals(1, d.sessions.load(id)!!.exercises.first().sets.size)
        // Out-of-range values never reach storage.
        fails("RIR 11") { NewSet(1, SetKind.WORKING, 40.0, 8, rir = 11.0) }
        fails("unknown kind") { NewSet(1, "DROP", 40.0, 8) }
    }

    @Test fun `finish marks done and skipped exercises and clears the resume point`() = runBlocking {
        val (d, w, template) = ready()
        val id = d.sessions.start(w, template)
        val exs = d.sessions.load(id)!!.exercises
        d.sessions.logSet(exs[0].row.id, NewSet(0, SetKind.WORKING, 40.0, 8, rir = 2.0, formCheck = FormCheck.NO), "{}")
        if (exs.size > 2) d.sessions.skipExercise(exs[2].row.id)
        val done = d.finishWorkout(id, 7.0, 52.0, w.conditioning.map { it.workMinutes / 2 })
        assertEquals(Status.DONE, done.row.status)
        assertEquals(7.0, done.row.sessionRpe!!, 0.0)
        assertEquals(Status.DONE, done.exercises[0].row.status)
        assertTrue(done.exercises.drop(1).all { it.row.status == Status.SKIPPED })
        assertEquals(w.conditioning.map { it.workMinutes / 2 }, done.doc.conditioning.map { it.doneWorkMinutes })
        assertNull(d.sessions.active())
        assertEquals("NO", done.exercises[0].sets.single().formCheck)
        fails("finishing twice") { d.sessions.finish(id, 7.0, 52.0) }
        d.sessions.rate(id, 8.0)
        assertEquals(8.0, d.sessions.load(id)!!.row.sessionRpe!!, 0.0)
    }

    @Test fun `discard removes an abandoned workout and its sets`() = runBlocking {
        val (d, w, template) = ready()
        val id = d.sessions.start(w, template)
        val ex = d.sessions.load(id)!!.exercises.first()
        d.sessions.logSet(ex.row.id, NewSet(0, SetKind.WORKING, 40.0, 8), "{}")
        d.sessions.discard(id)
        assertNull(d.sessions.load(id)); assertNull(d.sessions.active())
        assertTrue(d.store.snapshot().sets.isEmpty())
    }

    @Test fun `a swap keeps the old exercise for history and only before sets are logged`() = runBlocking {
        val (d, w, template) = ready()
        val id = d.sessions.start(w, template)
        val ex = d.sessions.load(id)!!.exercises.first()
        d.sessions.swap(ex.row.id, "goblet-squat", ex.doc.copy(load = 12.0))
        val swapped = d.sessions.load(id)!!.exercises.first()
        assertEquals("goblet-squat", swapped.exerciseId)
        assertEquals(ex.exerciseId, swapped.doc.swappedFrom)
        d.sessions.logSet(swapped.row.id, NewSet(0, SetKind.WORKING, 12.0, 10), "{}")
        fails("swap after logging") { d.sessions.swap(swapped.row.id, "leg-press", ex.doc) }
    }

    @Test fun `correcting a past set rebuilds the training state from the logged sets`() = runBlocking {
        val (d, w, template) = ready(withHistory = true)
        val id = d.sessions.start(w, template)
        val ex = d.sessions.load(id)!!.exercises.first { it.doc.load != null && !it.doc.calibrating }
        val setId = d.sessions.logSet(ex.row.id, NewSet(0, SetKind.WORKING, ex.doc.load, ex.doc.reps.last, rir = 4.0), "{}")
        d.finishWorkout(id, 6.0, 50.0)
        val before = d.docs.get(com.personalfitnesscoach.data.core.model.ExerciseState, ex.exerciseId)
        assertNotNull(before)
        d.correctPastSet(setId, ex.row.id, NewSet(0, SetKind.WORKING, ex.doc.load, ex.doc.reps.first, rir = 0.0))
        val after = d.docs.get(com.personalfitnesscoach.data.core.model.ExerciseState, ex.exerciseId)!!
        assertTrue("a harder result changes the next prescription: $before → $after", after.prescription != before!!.prescription)
    }
}
