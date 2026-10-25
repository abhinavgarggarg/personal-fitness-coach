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

    @Test fun `AUD-05 the easier version follows the regression chain, never a harder same-pattern lift (review finding 2)`() {
        val day = benchDay()
        val slot = day.slots.first { it.exercise.id == "bench-press" }
        // A barbell-only gym: no dumbbells for the dumbbell bench, so the next step down, the push-up; never the close-grip bench.
        val barOnly = setOf("barbell", "bench", "rack")
        val r = SessionGenerator.generate(req(day, gym = barOnly, e1rm = mapOf("bench-press" to weakE1rm(day))))
        val instead = r.value.items.firstOrNull { it.slotKey == slot.spec.key }
        assertNotNull(instead)
        assertEquals("push-up", instead!!.exercise.id)
        assertTrue(r.value.items.none { it.exercise.id in setOf("bench-press", "close-grip-bench-press", "incline-bench-press") && it.slotKey == slot.spec.key })
        // With dumbbells, the dumbbell bench, its weight-finding starting at the lightest pair (not from body weight).
        val withDb = SessionGenerator.generate(req(day, e1rm = mapOf("bench-press" to weakE1rm(day))).copy(bodyweightKg = 85.0)).value.items.first { it.slotKey == slot.spec.key }
        assertEquals("db-bench-press", withDb.exercise.id)
        assertTrue(withDb.calibrating)
        assertEquals(2.5, withDb.load!!, 0.0)
    }

    @Test fun `AUD-05b an easier version never starts heavier than the load found too heavy, and never a harder lift (review finding 2)`() {
        val plan = WeekPlanner.plan(weekInput(4, weekOf(BlockType.BUILD), program = program), program).value
        val day0 = plan.days.first { d -> d.slots.any { it.main && it.exercise.pattern == Library.require("deadlift").pattern } }
        val i = day0.slots.indexOfFirst { it.main && it.exercise.pattern == Library.require("deadlift").pattern }
        val dl = Library.require("deadlift")
        val day = day0.copy(slots = day0.slots.mapIndexed { k, s -> if (k == i) s.copy(exercise = dl, unit = dl.unit, perSide = dl.unilateral) else s }
            .filter { it.exercise.id !in setOf("trap-bar-deadlift", "kb-deadlift") || it === day0.slots[i] })
        val s = day.slots[i]
        val weak = 20.0 * (1.0 + (s.reps.first + maxOf(1.0, s.targetRir)) / 30.0) * 0.9
        val r = SessionGenerator.generate(req(day, e1rm = mapOf("deadlift" to weak)))
        val instead = r.value.items.first { it.slotKey == s.spec.key }
        // The trap bar (25 kg) is heavier than the 20 kg bar found too heavy: skipped for the kettlebell deadlift.
        assertEquals("kb-deadlift", instead.exercise.id)
        assertTrue(instead.load!! <= 20.0)
        assertTrue(instead.exercise.difficulty < dl.difficulty)
    }

    @Test fun `AUD-08 bodyweight and timed work stays on lighter days and in deload weeks (review finding 4)`() {
        val base = benchDay()
        val pu = Library.require("push-up")
        val i = base.slots.indexOfFirst { it.exercise.id == "bench-press" }
        val day = base.copy(slots = base.slots.mapIndexed { k, s -> if (k == i) s.copy(exercise = pu, unit = pu.unit, perSide = pu.unilateral, reps = pu.defaultRepRange) else s }
            .filterIndexed { k, s -> k == i || s.exercise.id != "push-up" })
        for ((tier, deload) in listOf(Tier.MODIFIED to false, Tier.LIGHT to false, Tier.FULL to true)) {
            val r = SessionGenerator.generate(req(day).copy(tier = tier, inDeload = deload))
            val item = r.value.items.firstOrNull { it.exercise.id == "push-up" }
            assertNotNull("push-up kept on $tier deload=$deload", item)
            assertNull(item!!.load)
            assertTrue(r.decisions.none { it.reason == ReasonKey.LOAD_CANNOT_REDUCE_LEFT_OUT && it.inputs["exercise"] == "push-up" })
            for (x in r.value.validated.exercises) assertTrue(x.loadFactor <= com.personalfitnesscoach.engine.safety.SessionValidator.maxLoadFactor(x.main, tier, deload) + 1e-9)
        }
    }

    @Test fun `AUD-09 a hard set at the lightest weight says there is nothing lighter, instead of promising a lighter set (review finding 6)`() {
        val r = com.personalfitnesscoach.engine.progression.Autoregulation.adjust(20.0, 20.0, com.personalfitnesscoach.engine.model.SetLog(20.0, 3, 0.0), 10, 8, 2.0,
            listOf(20.0, 22.5, 25.0), 100.0)
        assertEquals(20.0, r.value, 0.0)
        assertEquals(ReasonKey.INSESSION_AT_LIGHTEST, r.decisions.single().reason)
        val heavier = com.personalfitnesscoach.engine.progression.Autoregulation.adjust(40.0, 40.0, com.personalfitnesscoach.engine.model.SetLog(40.0, 3, 0.0), 10, 8, 2.0,
            listOf(20.0, 37.5, 40.0), 100.0)
        assertEquals(ReasonKey.INSESSION_LOAD_DOWN, heavier.decisions.single().reason)
    }

    @Test fun `AUD-10e PROG-008 says an easier version is offered only when there is one (review finding 7)`() {
        fun twiceNo(id: String) = com.personalfitnesscoach.engine.progression.Progression.next(com.personalfitnesscoach.engine.progression.ExposureInput(
            Library.require(id), 8..12, 2.0, 40.0, List(3) { com.personalfitnesscoach.engine.model.SetLog(40.0, 10, 2.0, formOk = com.personalfitnesscoach.engine.model.FormCheck.NO) },
            listOf(35.0, 40.0, 45.0), recoveryOk = true, consecutiveFormNo = 2))
        val leg = twiceNo("leg-press")
        assertFalse(leg.value.offerRegression)
        assertTrue(leg.decisions.any { it.reason == ReasonKey.FORM_HOLD_NO_EASIER })
        assertTrue(leg.decisions.none { it.reason == ReasonKey.REGRESSION_OFFERED })
        val bench = twiceNo("bench-press")
        assertTrue(bench.value.offerRegression)
        assertTrue(bench.decisions.any { it.reason == ReasonKey.REGRESSION_OFFERED })
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

    @Test fun `TC-SAF-003e very strong muscle pain stops that exercise only (SAF-003 2_0_0)`() {
        fun a(kind: com.personalfitnesscoach.engine.safety.PainKind, rating: Int) =
            com.personalfitnesscoach.engine.safety.PainGate.assess(com.personalfitnesscoach.engine.safety.PainReport(com.personalfitnesscoach.engine.model.Joint.KNEE, kind, rating)).value
        val burn7 = a(com.personalfitnesscoach.engine.safety.PainKind.MUSCLE_BURN, 7)
        assertEquals(com.personalfitnesscoach.engine.safety.PainAction.STOP_EXERCISE, burn7.action)
        assertFalse(burn7.regionConservative); assertNull(burn7.regionMaxStress); assertFalse(burn7.endSession)
        assertEquals(com.personalfitnesscoach.engine.safety.PainAction.STOP_EXERCISE, a(com.personalfitnesscoach.engine.safety.PainKind.DOMS, 9).action)
        assertEquals(com.personalfitnesscoach.engine.safety.PainAction.CONTINUE, a(com.personalfitnesscoach.engine.safety.PainKind.MUSCLE_BURN, 6).action)
        // Joint pain is unchanged: 7 stops the region.
        assertEquals(com.personalfitnesscoach.engine.safety.PainAction.STOP_REGION, a(com.personalfitnesscoach.engine.safety.PainKind.JOINT_OR_TENDON, 7).action)
    }

    @Test fun `AUD-22 condition table 1_0_2 - bone spread follows the care team, pregnancy the stricter guideline, a past foot ulcer needs a doctor's OK`() {
        val C = com.personalfitnesscoach.engine.safety.Conditions
        fun U(id: String, subFlags: Set<String> = emptySet()) = com.personalfitnesscoach.engine.safety.UserCondition(id, subFlags = subFlags)
        val bone = C.resolve(listOf(U("cancer", subFlags = setOf("cancer_has_spread_to_the_bones")))).value
        assertTrue("cancer spread to the bones: no training plan", "cancer" in bone.blocked)
        assertTrue(bone.prompts.any { it.contains("supervised", ignoreCase = true) })
        assertTrue(C.resolve(listOf(U("cancer"))).value.blocked.isEmpty())
        assertTrue(C.resolve(listOf(U("cancer"))).value.stopSigns.none { it.contains("SAF-002") })
        val preg = com.personalfitnesscoach.engine.safety.GeneratedConditions.entries.first { it.id == "pregnancy" }
        assertTrue(preg.blockIf.single().contains("after 26 weeks"))
        assertTrue(preg.blockIf.single().contains("twins at risk of premature labour"))
        assertTrue(preg.blockIf.single().contains("severe anaemia"))
        assertTrue(preg.stopSigns.any { it.contains("calf", ignoreCase = true) } && preg.stopSigns.any { it == "Headache" })
        // The evidence re-check: the stricter of the two guidelines everywhere.
        assertTrue(preg.blockIf.single().contains("restrictive lung disease") && preg.blockIf.single().contains("second or third trimester"))
        assertTrue(preg.attestIf.single().contains("; anaemia;") && preg.attestIf.single().contains("another significant medical condition"))
        assertFalse("thyroid only in the block list", preg.attestIf.single().contains("thyroid"))
        assertTrue(preg.stopSigns.contains("Chest pain") && preg.stopSigns.contains("Fluid leakage"))
        assertTrue(preg.environment.any { it.startsWith("Avoid scuba diving") })
        val feet = C.resolve(listOf(U("t2d"), U("diabetes_feet", subFlags = setOf("a_past_foot_ulcer_or_charcot_foot")))).value
        val plain = C.resolve(listOf(U("t2d"), U("diabetes_feet"))).value
        assertTrue("a past ulcer needs the doctor's OK first: ${feet.conservative}", feet.conservative && !plain.conservative || feet.clearancePrompts.size > plain.clearancePrompts.size)
    }
}
