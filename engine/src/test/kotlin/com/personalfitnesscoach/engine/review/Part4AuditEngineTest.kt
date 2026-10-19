package com.personalfitnesscoach.engine.review

import com.personalfitnesscoach.engine.calc.E1rm
import com.personalfitnesscoach.engine.calc.PlateMath
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.generation.GenerationRequest
import com.personalfitnesscoach.engine.generation.SessionGenerator
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.program.BlockType
import com.personalfitnesscoach.engine.program.Blueprint
import com.personalfitnesscoach.engine.program.FULL_GYM
import com.personalfitnesscoach.engine.program.Goal
import com.personalfitnesscoach.engine.program.PlannedDay
import com.personalfitnesscoach.engine.program.WeekKind
import com.personalfitnesscoach.engine.program.WeekPlanner
import com.personalfitnesscoach.engine.program.weekInput
import com.personalfitnesscoach.engine.progression.Prescription
import com.personalfitnesscoach.engine.progression.ProgressionAction
import com.personalfitnesscoach.engine.safety.ValidationContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4 Part 4 (safety and evidence audit): a lightest load that is too heavy for the rep range, its new-block re-check, and the
 * PROG-008 easier-version offer.
 */
class Part4AuditEngineTest {
    private val program = Blueprint.plan(listOf(Goal.GENERAL_FITNESS)).value
    private fun weekOf(type: BlockType): Int = (1..52).first { w ->
        val c = Blueprint.context(program, w)
        c.block.type == type && c.kind == WeekKind.LOADING
    }
    private val vctx = ValidationContext(Level.BEGINNER, hiitBaseReady = true)

    /** A planned day with the barbell bench press in it, and that slot. */
    private fun benchDay(type: BlockType = BlockType.BUILD): PlannedDay {
        val day = WeekPlanner.plan(weekInput(4, weekOf(type), program = program), program).value.days
            .first { d -> d.slots.any { it.main && it.exercise.pattern == Library.require("bench-press").pattern } }
        val bench = Library.require("bench-press")
        val i = day.slots.indexOfFirst { it.main && it.exercise.pattern == bench.pattern }
        return day.copy(slots = day.slots.mapIndexed { k, s -> if (k == i) s.copy(exercise = bench, unit = bench.unit, perSide = bench.unilateral) else s }
            .filter { it.exercise.id != "db-bench-press" || it === day.slots[i] })
    }

    private fun req(day: PlannedDay, gym: Set<String> = FULL_GYM, e1rm: Map<String, Double> = emptyMap(), prog: Map<String, Prescription> = emptyMap(),
                    lastDone: Map<String, Int> = emptyMap(), blockStart: Int? = null) =
        GenerationRequest(day, Level.BEGINNER, 4, 90, gym, Tier.FULL, 35, e1rm = e1rm, progression = prog, week = vctx, lastDone = lastDone, blockStartDay = blockStart)

    /** An e1RM for which the 20 kg bar is ~10% too heavy for the bottom of the bench slot's range at its target effort. */
    private fun weakE1rm(day: PlannedDay): Double {
        val s = day.slots.first { it.exercise.id == "bench-press" }
        return 20.0 * (1.0 + (s.reps.first + maxOf(1.0, s.targetRir)) / 30.0) * 0.9
    }

    @Test fun `AUD-01 an empty bar too heavy for the bottom of the rep range is never prescribed - an easier version is used`() {
        val day = benchDay()
        val slot = day.slots.first { it.exercise.id == "bench-press" }
        val r = SessionGenerator.generate(req(day, e1rm = mapOf("bench-press" to weakE1rm(day))))
        assertTrue("bench at the bar is not planned", r.value.items.none { it.exercise.id == "bench-press" })
        val instead = r.value.items.first { it.slotKey == slot.spec.key }
        assertEquals("the regression first", "db-bench-press", instead.exercise.id)
        assertTrue(r.decisions.any { it.reason == ReasonKey.LIGHTEST_TOO_HEAVY })
        assertTrue(r.decisions.none { it.reason == ReasonKey.LIGHTEST_TOO_HEAVY_LEFT_OUT })
    }

    @Test fun `AUD-02 when the bar does allow the bottom of the range, nothing changes`() {
        val day = benchDay()
        val s = day.slots.first { it.exercise.id == "bench-press" }
        // e1RM at which 20 kg is exactly what the bottom of the range supports at the target effort.
        val ok = 20.0 * (1.0 + (s.reps.first + maxOf(1.0, s.targetRir)) / 30.0)
        val r = SessionGenerator.generate(req(day, e1rm = mapOf("bench-press" to ok)))
        val bench = r.value.items.first { it.exercise.id == "bench-press" }
        assertEquals(20.0, bench.load!!, 0.0)
        assertTrue(r.decisions.none { it.reason == ReasonKey.LIGHTEST_TOO_HEAVY })
        assertFalse(PlateMath.belowLightest(E1rm.loadFor(ok, s.reps.first, maxOf(1.0, s.targetRir)), listOf(20.0, 22.5)))
    }

    @Test fun `AUD-03 a prescription flagged too heavy at the lightest load uses the easier version, a heavier one is not affected`() {
        val day = benchDay()
        val s = day.slots.first { it.exercise.id == "bench-press" }
        val flagged = Prescription(20.0, s.reps, s.reps.first, ProgressionAction.REDUCE, lightestTooHeavy = true)
        val r = SessionGenerator.generate(req(day, prog = mapOf("bench-press" to flagged)))
        assertTrue(r.value.items.none { it.exercise.id == "bench-press" })
        assertTrue(r.decisions.any { it.reason == ReasonKey.LIGHTEST_TOO_HEAVY })
        // A stale flag on a load above the lightest (the inventory changed, say) is not a reason to swap.
        val heavier = flagged.copy(load = 30.0)
        val r2 = SessionGenerator.generate(req(day, prog = mapOf("bench-press" to heavier)))
        assertEquals(30.0, r2.value.items.first { it.exercise.id == "bench-press" }.load!!, 0.0)
    }

    @Test fun `AUD-04 in a new block one weight-finding ramp at the lightest load checks again, and within the block the easier version stays`() {
        val day = benchDay()
        val weak = mapOf("bench-press" to weakE1rm(day))
        val again = SessionGenerator.generate(req(day, e1rm = weak, lastDone = mapOf("bench-press" to 90), blockStart = 100))
        val bench = again.value.items.first { it.exercise.id == "bench-press" }
        assertTrue(bench.calibrating); assertTrue(bench.lightestRecheck); assertEquals(20.0, bench.load!!, 0.0)
        assertTrue(again.decisions.any { it.reason == ReasonKey.LIGHTEST_RECHECK })
        val within = SessionGenerator.generate(req(day, e1rm = weak, lastDone = mapOf("bench-press" to 101), blockStart = 100))
        assertTrue(within.value.items.none { it.exercise.id == "bench-press" })
        assertTrue(within.value.items.none { it.lightestRecheck })
    }

    @Test fun `AUD-05 with no easier loaded version in the gym the slot gets an exercise it can be done with, never the bar`() {
        val day = benchDay()
        val barOnly = setOf("barbell", "bench", "rack")
        val r = SessionGenerator.generate(req(day, gym = barOnly, e1rm = mapOf("bench-press" to weakE1rm(day))))
        assertTrue(r.value.items.none { it.exercise.id == "bench-press" })
        for (it in r.value.items) if (it.load != null && !it.calibrating && it.exercise.id in setOf("close-grip-bench-press", "incline-bench-press", "overhead-press"))
            assertTrue("${it.exercise.id} ${it.load}", it.load!! >= 20.0)
        val reasons = r.decisions.map { it.reason }
        assertTrue(ReasonKey.LIGHTEST_TOO_HEAVY in reasons || ReasonKey.LIGHTEST_TOO_HEAVY_LEFT_OUT in reasons)
    }

    @Test fun `AUD-06 PROG-008 after two form-not-solid answers the easier version is offered and the plan keeps the lift`() {
        val day = benchDay()
        val s = day.slots.first { it.exercise.id == "bench-press" }
        val p = Prescription(40.0, s.reps, s.reps.first, ProgressionAction.HOLD, offerRegression = true)
        val bench = SessionGenerator.generate(req(day, prog = mapOf("bench-press" to p))).value.items.first { it.exercise.id == "bench-press" }
        assertEquals(40.0, bench.load!!, 0.0)
        assertEquals("db-bench-press", bench.easierOffer)
        val none = SessionGenerator.generate(req(day, prog = mapOf("bench-press" to p.copy(offerRegression = false)))).value.items.first { it.exercise.id == "bench-press" }
        assertNull(none.easierOffer)
        // Not offered when the easier version can't be done here today.
        val noDb = SessionGenerator.generate(req(day, gym = FULL_GYM - "dumbbells", prog = mapOf("bench-press" to p))).value.items.firstOrNull { it.exercise.id == "bench-press" }
        assertNotNull(noDb); assertNull(noDb!!.easierOffer)
    }

    @Test fun `AUD-07 RDY-004 - a left-out exercise says so, instead of claiming an easier version`() {
        val plan = WeekPlanner.plan(weekInput(4, weekOf(BlockType.BUILD), program = program), program).value
        val day = plan.days.first { d -> d.slots.any { it.exercise.id == "back-squat" } }
        // Only a rack and bar: no goblet squat; LIGHT day caps main lifts at 85% and the bar is the lightest load.
        val r = SessionGenerator.generate(GenerationRequest(day, Level.INTERMEDIATE, 40, 90, setOf("barbell", "rack"), Tier.LIGHT, 35,
            e1rm = mapOf("back-squat" to 26.0), week = vctx))
        if (r.value.items.none { it.exercise.id == "back-squat" || it.exercise.id == "goblet-squat" })
            assertTrue(r.decisions.any { it.reason == ReasonKey.LOAD_CANNOT_REDUCE_LEFT_OUT })
        assertTrue(r.decisions.none { it.reason == ReasonKey.LOAD_CANNOT_REDUCE && it.outputs["regression"] == null })
    }
}
