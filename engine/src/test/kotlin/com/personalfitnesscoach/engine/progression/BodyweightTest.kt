package com.personalfitnesscoach.engine.progression

import com.personalfitnesscoach.engine.library.Library
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyweightTest {
    private fun ex(id: String) = Library.require(id)
    private fun s(vararg reps: Int, rir: Double? = 1.0, assist: Double? = null) = BwSession(reps.map { BwSet(it, rir) }, assist)
    private fun next(id: String, vararg h: BwSession, step: Double = 5.0): BwDecision {
        val e = ex(id)
        return Bodyweight.next(e, h.toList(), Library.progressionOf(e), Library.regressionOf(e), step).value
    }

    @Test fun `TC-BW-001a reps come first`() {
        val d = next("push-up", s(10, 9, 8))
        assertEquals(BwAction.REPS_UP, d.action); assertEquals("push-up", d.exerciseId); assertEquals(9, d.targetReps)
    }

    @Test fun `TC-BW-001b regressions run the levers backwards`() {
        val d = next("push-up", s(4, 5, 7), s(5, 4, 6))
        assertEquals(BwAction.STEP_DOWN, d.action); assertEquals("push-up-incline", d.exerciseId)
        // One bad session is not enough.
        assertNotEquals(BwAction.STEP_DOWN, next("push-up", s(9, 9, 9), s(4, 5, 7)).action)
    }

    @Test fun `TC-BW-002a every set at the top of the range at RIR 1 or more for two sessions moves up a rung`() {
        val d = next("push-up", s(15, 15, 15), s(15, 15, 15))
        assertEquals(BwAction.STEP_UP, d.action); assertEquals("push-up-feet-elevated", d.exerciseId)
        assertEquals(Library.require("push-up-feet-elevated").defaultRepRange.first, d.targetReps)
        assertNotEquals(BwAction.STEP_UP, next("push-up", s(15, 15, 15)).action) // one session only
        assertNotEquals(BwAction.STEP_UP, next("push-up", s(15, 15, 15), s(15, 15, 15, rir = 0.0)).action) // RIR 0
        assertEquals(20, Bodyweight.topOfRange(ex("air-squat"))); assertEquals(12, Bodyweight.topOfRange(ex("pull-up")))
    }

    @Test fun `TC-BW-002b assisted pull-ups lose one assistance step after 3 x 8 at RIR 2`() {
        val d = next("pull-up-assisted", s(8, 8, 8, rir = 2.0, assist = 30.0))
        assertEquals(BwAction.LESS_ASSISTANCE, d.action); assertEquals(25.0, d.assistanceKg!!, 1e-9)
        val last = next("pull-up-assisted", s(8, 8, 9, rir = 2.0, assist = 5.0))
        assertEquals(BwAction.STEP_UP, last.action); assertEquals("pull-up-negative", last.exerciseId)
        assertEquals(30.0, next("pull-up-assisted", s(8, 8, 8, rir = 1.0, assist = 30.0)).assistanceKg!!, 1e-9)
    }

    @Test fun `TC-PROG-005a bodyweight progression adds load at the top of a ladder`() {
        val d = next("push-up-weighted", s(15, 15, 15), s(15, 15, 15))
        assertEquals(BwAction.ADD_LOAD, d.action); assertEquals("push-up-weighted", d.exerciseId)
    }

    @Test fun `TC-PROG-005b unrated sets never step up`() {
        assertNotEquals(BwAction.STEP_UP, next("push-up", s(15, 15, 15, rir = null), s(15, 15, 15, rir = null)).action)
        assertEquals(BwAction.HOLD, Bodyweight.next(ex("push-up"), emptyList(), null, null).value.action)
    }

    @Test fun `TC-CORE-002a three solid 30-45 s holds for two sessions move one rung up`() {
        val d = next("front-plank", s(30, 35, 30, rir = 2.0), s(32, 32, 30, rir = 2.0))
        assertEquals(BwAction.STEP_UP, d.action); assertEquals("long-lever-plank", d.exerciseId)
        val pallof = next("pallof-half-kneeling", s(10, 10, 11, rir = 2.0), s(10, 12, 10, rir = 3.0))
        assertEquals("pallof-standing", pallof.exerciseId)
    }

    @Test fun `TC-CORE-002b otherwise lengthen the hold, never past 45 s`() {
        val d = next("front-plank", s(30, 30, 30, rir = 2.0))
        assertEquals(BwAction.REPS_UP, d.action); assertEquals(35, d.targetReps)
        assertEquals(45, next("front-plank", s(44, 44, 44, rir = 1.0)).targetReps)
        assertTrue(Bodyweight.isCoreLadder(ex("dead-bug")))
    }
}
