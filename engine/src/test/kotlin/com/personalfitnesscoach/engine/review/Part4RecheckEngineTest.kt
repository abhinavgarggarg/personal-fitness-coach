package com.personalfitnesscoach.engine.review

import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.progression.ReturnToTraining
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.safety.ClearanceScope
import com.personalfitnesscoach.engine.safety.Conditions
import com.personalfitnesscoach.engine.safety.Session
import com.personalfitnesscoach.engine.safety.SessionExercise
import com.personalfitnesscoach.engine.safety.SessionValidator
import com.personalfitnesscoach.engine.safety.UserCondition
import com.personalfitnesscoach.engine.safety.ValidationContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Engine side of the Part 4 re-check (RC-01, RC-04). */
class Part4RecheckEngineTest {
    @Test fun `RC-01 a layoff of 56 days or more recalibrates, and HIIT and the return window end after the HIIT-003 base`() {
        for (week in 0 until P.HIIT_003.base_weeks) {
            val p = ReturnToTraining.afterBreak(70, 0, weeksSinceReturn = week).value
            assertTrue(p.recalibrate); assertFalse(p.hiitAllowed); assertTrue(p.inReturn)
        }
        val after = ReturnToTraining.afterBreak(70, 0, weeksSinceReturn = P.HIIT_003.base_weeks).value
        assertTrue(after.recalibrate); assertTrue(after.hiitAllowed); assertFalse(after.inReturn)
        assertEquals(1.0, after.loadFactor, 1e-9); assertEquals(1.0, after.setsFactor, 1e-9)
    }

    @Test fun `RC-04 the final check removes resistance work the day after strength with type 2 diabetes`() {
        val t2d = Conditions.resolve(listOf(UserCondition("t2d", clearance = setOf(ClearanceScope.VIGOROUS)))).value
        assertFalse(t2d.strengthOnConsecutiveDays)
        val s = Session(Tier.FULL, listOf(SessionExercise(Library.require("leg-press"), 3, 10, 2.0), SessionExercise(Library.require("seated-cable-row"), 3, 10, 2.0)))
        val yes = ValidationContext(Level.INTERMEDIATE, weeksTraining = 20, conditions = t2d, strengthYesterday = true)
        assertTrue(SessionValidator.violations(s, yes).any { it.code == "STRENGTH_CONSECUTIVE" })
        assertTrue(SessionValidator.validate(s, yes).value.session.exercises.isEmpty())
        // Not yesterday, or no such condition: nothing is removed.
        assertFalse(SessionValidator.violations(s, yes.copy(strengthYesterday = false)).any { it.code == "STRENGTH_CONSECUTIVE" })
        assertFalse(SessionValidator.violations(s, ValidationContext(Level.INTERMEDIATE, weeksTraining = 20, strengthYesterday = true)).any { it.code == "STRENGTH_CONSECUTIVE" })
    }
}
