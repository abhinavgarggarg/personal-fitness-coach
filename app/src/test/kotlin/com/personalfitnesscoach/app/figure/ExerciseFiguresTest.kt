package com.personalfitnesscoach.app.figure

import com.personalfitnesscoach.engine.library.Library
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** CR-004: every exercise in the library has a moving figure, and every figure stays on the floor and inside its picture. */
class ExerciseFiguresTest {

    private val steps = (0..20).map { it / 20.0 }

    @Test
    fun everyLibraryExerciseHasAFigure() {
        val missing = Library.all.map { it.id }.filter { ExerciseFigures.of(it) == null }
        assertTrue("exercises without a figure: $missing", missing.isEmpty())
        val unknown = ExerciseFigures.ids - Library.all.map { it.id }.toSet()
        assertTrue("figures for exercises not in the library: $unknown", unknown.isEmpty())
    }

    @Test
    fun everyFrameIsFiniteAndHasABody() {
        for (e in Library.all) {
            val f = ExerciseFigures.of(e.id)!!
            for (t in steps) {
                val frame = f.frame(t)
                assertTrue("${e.id} t=$t draws a body", frame.shapes.count { it is Shape.Bone && it.kind == Kind.BODY } >= 10)
                val b = frame.extent()
                assertTrue("${e.id} t=$t has finite numbers", listOf(b.minX, b.minY, b.maxX, b.maxY).all { it.isFinite() })
            }
        }
    }

    /** A body part may touch the floor line (a few units of a rounded end), never sink through it. */
    @Test
    fun nobodyGoesThroughTheFloor() {
        val sinking = ArrayList<String>()
        for (e in Library.all) {
            val f = ExerciseFigures.of(e.id)!!
            for (t in steps) {
                val lowest = f.frame(t).shapes.minOf { s ->
                    when (s) {
                        is Shape.Bone -> minOf(s.a.y, s.b.y) - s.width / 2
                        is Shape.Dot -> if (s.kind == Kind.HOLE) Double.MAX_VALUE else s.c.y - s.r
                        is Shape.Poly -> s.points.minOf { it.y }
                        is Shape.Box -> s.corners.minOf { it.y }
                        is Shape.Stroke -> minOf(s.a.y, s.b.y)
                    }
                }
                if (lowest < -5.0) { sinking += "${e.id} at t=$t: ${"%.1f".format(lowest)}"; break }
            }
        }
        assertTrue("below the floor: $sinking", sinking.isEmpty())
    }

    @Test
    fun theCameraHoldsAPictureThatFitsACard() {
        for (e in Library.all) {
            val c = ExerciseFigures.of(e.id)!!.camera()
            assertTrue("${e.id} has a size", c.width > 50 && c.height > 50)
            val aspect = c.width / c.height
            assertTrue("${e.id} is not too wide or tall: ${"%.2f".format(aspect)}", aspect in 0.2..4.5)
        }
    }

    @Test
    fun limbsKeepTheirLength() {
        // Side figures: thigh, shin, upper arm and forearm never stretch (the reach solver bends or points a limb, never grows it).
        for (e in Library.all) {
            val f = ExerciseFigures.of(e.id) as? SideFigure ?: continue
            for (t in steps) {
                val s = f.skeleton(t)
                assertEquals("${e.id} thigh", Body.THIGH, s.hip.dist(s.nKnee), 0.05)
                assertEquals("${e.id} shin", Body.SHIN, s.nKnee.dist(s.nAnkle), 0.05)
                assertEquals("${e.id} upper arm", Body.UPPER_ARM, s.shoulder.dist(s.nElbow), 0.05)
                assertEquals("${e.id} forearm", Body.FOREARM, s.nElbow.dist(s.nWrist), 0.05)
            }
        }
    }

    @Test
    fun theTimingHoldsAndReturns() {
        for (timing in listOf(Timing.NORMAL, Timing.HOLD, Timing.STEP, Timing.FAST)) {
            assertEquals(0.0, timing.t(0.0), 1e-9)
            assertEquals(timing.t(0.25), timing.t(1.25), 1e-9)
            assertTrue((0..100).all { timing.t(it / 100.0) in 0.0..1.0 })
            assertTrue((0..100).any { timing.t(it / 100.0) > 0.99 })
            // The loop starts at the end position (the first picture shows the end of the rep).
            assertEquals(1.0, timing.t(timing.peak), 1e-9)
        }
    }
}
