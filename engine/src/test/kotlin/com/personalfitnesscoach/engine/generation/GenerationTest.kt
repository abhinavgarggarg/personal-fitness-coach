package com.personalfitnesscoach.engine.generation

import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.model.Zone
import com.personalfitnesscoach.engine.program.Blueprint
import com.personalfitnesscoach.engine.program.FULL_GYM
import com.personalfitnesscoach.engine.program.Goal
import com.personalfitnesscoach.engine.program.PlannedDay
import com.personalfitnesscoach.engine.program.SlotRole
import com.personalfitnesscoach.engine.program.WeekPlanner
import com.personalfitnesscoach.engine.program.weekInput
import com.personalfitnesscoach.engine.registry.RuleIds
import com.personalfitnesscoach.engine.safety.SessionValidator
import com.personalfitnesscoach.engine.safety.ValidationContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationTest {
    private val program = Blueprint.plan(listOf(Goal.STRENGTH, Goal.CARDIO)).value
    private fun day(days: Int = 4, week: Int = 16, pick: (PlannedDay) -> Boolean = { it.heavyLower }): PlannedDay =
        WeekPlanner.plan(weekInput(days, week, program = program), program).value.days.first(pick)
    private fun req(d: PlannedDay, tier: Tier = Tier.FULL, minutes: Int = 60) =
        GenerationRequest(d, Level.INTERMEDIATE, 40, minutes, FULL_GYM, tier, age = 35, e1rm = mapOf("back-squat" to 120.0, "bench-press" to 90.0, "romanian-deadlift" to 130.0),
            week = ValidationContext(Level.INTERMEDIATE, hiitBaseReady = true))

    @Test fun `TC-GEN-001a the pipeline is deterministic and runs all ten steps`() {
        val r1 = SessionGenerator.generate(req(day()))
        val r2 = SessionGenerator.generate(req(day()))
        assertEquals(r1.value, r2.value)
        val done = r1.decisions.last { it.reason == ReasonKey.SESSION_GENERATED }
        assertEquals(RuleIds.GEN_001, done.ruleIds.single())
        assertEquals(10, (done.outputs["steps"] as List<*>).size)
        // Loads come from e1RM (INT-005) rounded to real plates, with ramp-up sets for the first main lift.
        val squat = r1.value.items.first { it.exercise.id == "back-squat" }
        assertNotNull(squat.load); assertFalse(squat.calibrating)
        assertTrue(squat.warmupSets.isNotEmpty())
        assertTrue(r1.value.items.first().role == SlotRole.POWER || r1.value.items.first().main)
    }

    @Test fun `TC-GEN-001b every generated session passes the safety validator, whatever the day brings`() {
        val days = (2..6).flatMap { n -> WeekPlanner.plan(weekInput(n, 16, program = program), program).value.days }
        for (d in days) for (tier in Tier.entries) for (minutes in listOf(20, 45, 75)) {
            val r = req(d, tier, minutes).copy(equipmentToday = FULL_GYM - setOf("barbell", "cable"), painCaution = emptySet())
            val w = SessionGenerator.generate(r).value
            val ctx = r.week.copy(level = r.level, weeksTraining = r.weeksTraining, equipmentToday = r.equipmentToday,
                fullTierWorkingSets = d.slots.sumOf { it.sets })
            assertTrue("${d.template} $tier $minutes: ${SessionValidator.violations(w.validated, ctx)}", SessionValidator.violations(w.validated, ctx).isEmpty())
            assertTrue(w.items.none { "barbell" in it.exercise.equipment || "cable" in it.exercise.equipment })
            if (tier == Tier.RECOVERY) assertTrue(w.items.isEmpty())
        }
    }

    @Test fun `TC-GEN-001c the next-exposure prescription sets the load for its own rep range, the e1RM for a new one`() {
        val d = day()
        val slot = d.slots.first { it.exercise.id == "back-squat" }
        val same = com.personalfitnesscoach.engine.progression.Prescription(77.5, slot.reps, slot.reps.first, com.personalfitnesscoach.engine.progression.ProgressionAction.LOAD_JUMP)
        val r1 = SessionGenerator.generate(req(d).copy(progression = mapOf("back-squat" to same)))
        assertEquals(77.5, r1.value.items.first { it.exercise.id == "back-squat" }.load!!, 1e-9)
        assertTrue(r1.decisions.any { it.reason == ReasonKey.LOAD_FROM_PROGRESSION })
        val other = same.copy(repRange = (slot.reps.first + 3)..(slot.reps.last + 3))
        val r2 = SessionGenerator.generate(req(d).copy(progression = mapOf("back-squat" to other)))
        val sq = r2.value.items.first { it.exercise.id == "back-squat" }
        assertTrue(sq.load != 77.5 && sq.reps == slot.reps)
        assertTrue(r2.decisions.any { it.reason == ReasonKey.LOAD_FROM_E1RM })
    }

    @Test fun `missing equipment swaps to the same pattern`() {
        val d = day()
        val w = SessionGenerator.generate(req(d).copy(equipmentToday = FULL_GYM - "barbell")).value
        val planned = d.slots.filter { it.main }.map { it.exercise.pattern }.toSet()
        assertTrue(planned.all { p -> w.items.any { it.exercise.pattern == p } })
    }

    @Test fun `red flags stop the session and illness locks the day to rest`() {
        val stop = SessionGenerator.generate(req(day()).copy(redFlags = setOf("chest_pain_pressure_tightness"))).value
        assertNotNull(stop.safetyStop); assertTrue(stop.items.isEmpty())
        val ill = SessionGenerator.generate(req(day()).copy(illnessSymptoms = setOf("fever"))).value
        assertEquals(Tier.RECOVERY, ill.tier); assertTrue(ill.items.isEmpty())
        assertTrue(ill.conditioning.all { it.zone == Zone.Z1 })
    }

    @Test fun `MODIFIED and LIGHT days follow RDY-004`() {
        val d = day()
        val full = SessionGenerator.generate(req(d)).value
        val mod = SessionGenerator.generate(req(d, Tier.MODIFIED)).value
        val light = SessionGenerator.generate(req(d, Tier.LIGHT)).value
        assertTrue(mod.items.sumOf { it.sets } <= Math.floor(d.slots.sumOf { it.sets } * 0.7 + 1e-9))
        assertTrue(light.items.sumOf { it.sets } <= Math.floor(d.slots.sumOf { it.sets } * 0.5 + 1e-9))
        val fullSquat = full.items.first { it.exercise.id == "back-squat" }
        val modSquat = mod.items.first { it.exercise.id == "back-squat" }
        assertEquals(fullSquat.targetRir + 1.0, modSquat.targetRir, 1e-9)
        assertTrue(modSquat.load!! <= fullSquat.load!! * 0.95 + 1e-9)
        assertTrue(light.items.all { it.targetRir >= 3.0 })
        assertTrue(light.conditioning.none { it.countsAsHiit })
    }

    @Test fun `power work is dropped after HIIT earlier the same day (CON-005)`() {
        val d = WeekPlanner.plan(weekInput(4, 16, program = program), program).value.days.first { day -> day.slots.any { it.power } }
        val w = SessionGenerator.generate(req(d).copy(hiitEarlierToday = true))
        assertTrue(w.value.items.none { it.role == SlotRole.POWER })
        assertTrue(w.decisions.any { it.reason == ReasonKey.POWER_DROPPED_AFTER_HIIT })
    }

    @Test fun `TC-ADH-004b the express session fits 20-30 minutes and keeps the main lift`() {
        val d = day()
        val w = SessionGenerator.express(req(d)).value
        assertTrue("${w.plannedMinutes}", w.plannedMinutes <= 30.0 + 1e-9)
        val p1 = d.slots.first { it.priority == com.personalfitnesscoach.engine.planning.Priority.P1 }.exercise.id
        assertTrue(w.items.any { it.exercise.id == p1 && it.sets >= 2 })
    }

    @Test fun `generated sessions follow ORD-001 order`() {
        val w = SessionGenerator.generate(req(day(4, 40) { it.slots.any { s -> s.power } })).value
        val roles = w.items.map { it.role }
        if (SlotRole.POWER in roles) assertEquals(SlotRole.POWER, roles.first())
        val firstCore = roles.indexOfFirst { it == SlotRole.CORE || it == SlotRole.CARRY || it == SlotRole.ROTATION }
        val lastLift = roles.indexOfLast { it == SlotRole.MAIN || it == SlotRole.SECONDARY }
        if (firstCore >= 0) assertTrue(firstCore > lastLift)
        assertTrue(w.items.none { it.exercise.pattern == Pattern.ISOLATION && it.main })
    }
}
