package com.personalfitnesscoach.engine.planning

import com.personalfitnesscoach.engine.library.DrillKind
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.library.Region
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Muscle
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.safety.PainAction
import com.personalfitnesscoach.engine.safety.PainGate
import com.personalfitnesscoach.engine.safety.PainKind
import com.personalfitnesscoach.engine.safety.PainReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MobilityTest {
    private val gym = setOf("bands", "pullup_bar", "bench", "mat")

    @Test fun `TC-MOB-001a 3-5 minutes of dynamic drills for the session's patterns`() {
        val d = Mobility.warmupDrills(setOf(Pattern.SQUAT, Pattern.HORIZONTAL_PUSH), gym, jointLimits = emptyMap(), avoidTags = emptySet()).value
        val sec = d.sumOf { it.seconds }
        assertTrue("$sec s", sec in 180..300)
        assertTrue(d.any { Pattern.SQUAT in it.drill.prepares }); assertTrue(d.any { Pattern.HORIZONTAL_PUSH in it.drill.prepares })
    }

    @Test fun `TC-MOB-001b warm-up drills are dynamic only, 65+ adds a balance drill`() {
        val d = Mobility.warmupDrills(setOf(Pattern.HINGE), gym, jointLimits = emptyMap(), avoidTags = emptySet()).value
        assertTrue(d.all { it.drill.kind == DrillKind.MOBILISE || it.drill.kind == DrillKind.ACTIVATE })
        val older = Mobility.warmupDrills(setOf(Pattern.HINGE), gym, age = 67, jointLimits = emptyMap(), avoidTags = emptySet()).value
        assertTrue(older.any { it.drill.kind == DrillKind.BALANCE })
        // ORD-002: a restricted joint's mobility moves into the warm-up, first.
        val shoulder = Mobility.warmupDrills(setOf(Pattern.SQUAT), gym, restricted = setOf(Joint.SHOULDER), jointLimits = emptyMap(), avoidTags = emptySet()).value
        assertTrue(Region.SHOULDERS in shoulder.first().drill.regions)
    }

    @Test fun `TC-MOB-002a pre-workout static holds are capped at 30 s`() {
        assertEquals(30, Mobility.preWorkoutStaticSeconds(45)); assertEquals(20, Mobility.preWorkoutStaticSeconds(20))
    }

    @Test fun `TC-MOB-002b cool-down stretches the muscles trained, then 1-2 minutes of slow breathing`() {
        val c = Mobility.cooldown(setOf(Muscle.QUADS, Muscle.CHEST), minutes = 4.0, jointLimits = emptyMap(), avoidTags = emptySet()).value
        assertEquals(DrillKind.BREATHING, c.last().drill.kind)
        assertTrue(c.last().amount in 60..120)
        val stretches = c.dropLast(1)
        assertTrue(stretches.isNotEmpty() && stretches.all { it.drill.kind == DrillKind.STRETCH && it.amount in 30..60 })
        assertTrue(c.sumOf { it.seconds } <= 4 * 60)
        assertTrue(stretches.any { Region.QUADS in it.drill.regions || Region.HIP_FLEXORS in it.drill.regions })
    }

    @Test fun `TC-MOB-003a an optional drill for the next exercise during rests`() {
        val d = Mobility.betweenSets(Library.require("back-squat"), Library.require("bench-press"), gym, emptyMap(), emptySet())
        assertNotNull(d)
        assertTrue(Pattern.HORIZONTAL_PUSH in d!!.prepares)
        assertTrue(d.regions.none { it in setOf(Region.QUADS, Region.GLUTES, Region.HIPS, Region.ADDUCTORS, Region.SPINE) })
    }

    @Test fun `TC-MOB-003b never one that works the muscles of the current exercise`() {
        assertNull(Mobility.betweenSets(Library.require("bench-press"), Library.require("overhead-press"), gym, emptyMap(), emptySet()))
    }

    @Test fun `TC-MOB-004a lifts run through full range by default`() {
        assertEquals(RangeOfMotion.FULL, Mobility.rangeFor(painCaution = false))
    }

    @Test fun `TC-MOB-004b only the pain gate's continue-with-caution shortens the range`() {
        val o = PainGate.assess(PainReport(Joint.KNEE, PainKind.JOINT_OR_TENDON, 2)).value
        assertEquals(PainAction.CONTINUE_CAUTION, o.action)
        assertEquals(RangeOfMotion.REDUCED, Mobility.rangeFor(painCaution = o.action == PainAction.CONTINUE_CAUTION))
    }

    @Test fun `TC-MOB-005a off-day routine of 10-20 minutes, 2 x 30-60 s per position`() {
        val r = Mobility.offDayRoutine(15, gym, emptyMap(), emptySet()).value
        assertTrue(r.sumOf { it.seconds } <= 15 * 60)
        assertTrue(r.all { it.sets == 2 })
        assertTrue(r.filter { it.drill.unit == DoseUnit.SECONDS }.all { it.amount in 30..60 })
        for (region in listOf(Region.HIPS, Region.UPPER_BACK, Region.SHOULDERS, Region.ANKLES))
            assertTrue(region.name, r.any { region in it.drill.regions })
    }

    @Test fun `TC-MOB-005b the routine never exceeds 20 minutes and is offered 2-3 times a week`() {
        assertTrue(Mobility.offDayRoutine(40, gym, emptyMap(), emptySet()).value.sumOf { it.seconds } <= 20 * 60)
        assertEquals(2..3, Mobility.offDayPerWeek)
    }
}
