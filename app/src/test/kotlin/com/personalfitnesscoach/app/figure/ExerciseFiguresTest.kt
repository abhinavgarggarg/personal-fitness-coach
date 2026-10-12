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

    /** Review R7-01: a hand or foot that reaches for a point gets there in every key position (start, middle, end). */
    @Test
    fun everyReachLandsOnItsTarget() {
        val misses = ArrayList<String>()
        for (e in Library.all) {
            val f = ExerciseFigures.of(e.id) as? SideFigure ?: continue
            for (t in f.keyframes) for ((actual, wanted) in f.reaches(t)) {
                if (actual.dist(wanted) > 3.0) misses += "${e.id} t=$t misses by ${"%.0f".format(actual.dist(wanted))}"
            }
        }
        assertTrue("reaches that miss: $misses", misses.isEmpty())
    }

    /**
     * Re-check: in every key position each elbow and knee is where that position, drawn on its own, puts it — a blend never keeps one
     * end's bend for the other end (the pulldown's elbow pointing up at the end).
     */
    @Test
    fun keyPositionsLookAsDefined() {
        val wrong = ArrayList<String>()
        for (e in Library.all) {
            val f = ExerciseFigures.of(e.id) as? SideFigure ?: continue
            val keys = listOfNotNull(0.0 to f.start, f.mid?.let { 0.5 to it }, 1.0 to f.end)
            for ((t, pose) in keys) {
                val inBlend = f.skeleton(t)
                val alone = f.copy(start = pose, end = pose, mid = null).skeleton(0.0)
                val joints = listOf("near elbow" to (inBlend.nElbow to alone.nElbow), "far elbow" to (inBlend.fElbow to alone.fElbow),
                    "near knee" to (inBlend.nKnee to alone.nKnee), "far knee" to (inBlend.fKnee to alone.fKnee))
                for ((name, pair) in joints) if (pair.first.dist(pair.second) > 3.0) wrong += "${e.id} t=$t $name off by ${"%.0f".format(pair.first.dist(pair.second))}"
            }
        }
        assertTrue("key positions drawn differently in the rep: $wrong", wrong.isEmpty())
    }

    /** Review R7-04: nothing snaps or jumps during a rep: between two close moments every point of the picture moves only a little. */
    @Test
    fun nothingJumpsDuringTheRep() {
        val jumps = ArrayList<String>()
        for (e in Library.all) {
            val f = ExerciseFigures.of(e.id)!!
            var before = points(f.frame(0.0))
            for (i in 1..200) {
                val now = points(f.frame(i / 200.0))
                assertEquals(e.id, before.size, now.size)
                val most = before.indices.maxOf { before[it].dist(now[it]) }
                if (most > 15.0) { jumps += "${e.id} at t=${i / 200.0}: ${"%.0f".format(most)}"; break }
                before = now
            }
        }
        assertTrue("jumps: $jumps", jumps.isEmpty())
    }

    private fun points(f: Frame): List<P> = f.shapes.flatMap { s ->
        when (s) {
            is Shape.Bone -> listOf(s.a, s.b)
            is Shape.Dot -> listOf(s.c)
            is Shape.Poly -> s.points
            is Shape.Box -> s.corners
            is Shape.Stroke -> listOf(s.a, s.b)
        }
    }

    /** Review R7-06: a deadlift's start has the weight on the floor (that is the picture shown standing still). */
    @Test
    fun deadliftsStartWithTheWeightOnTheFloor() {
        for (id in listOf("deadlift", "trap-bar-deadlift", "kb-deadlift")) {
            val f = ExerciseFigures.of(id)!!
            val lowest = f.frame(f.still).shapes.filter { it is Shape.Dot && it.kind == Kind.WEIGHT }.minOf { (it as Shape.Dot).let { d -> d.c.y - d.r } }
            assertTrue("$id: the weight is ${"%.0f".format(lowest)} above the floor", lowest in -4.0..12.0)
        }
    }

    /** Review R7-04: in the overhead triceps extension the hands lower behind the head, never through it or in front of the face. */
    @Test
    fun overheadExtensionStaysBehindTheHead() {
        val f = ExerciseFigures.of("db-overhead-triceps-extension") as SideFigure
        for (i in 0..50) {
            val s = f.skeleton(i / 50.0)
            assertTrue("t=${i / 50.0}", s.nWrist.dist(s.head) > Body.HEAD + 4 && (s.nWrist.x < s.head.x + 4 || s.nWrist.y > s.head.y + Body.HEAD))
        }
    }

    /** Review R7-06: the single-leg Romanian deadlift holds the weight in the hand opposite the standing (near) leg. */
    @Test
    fun singleLegRdlHoldsTheWeightOpposite() {
        for (id in listOf("db-single-leg-rdl", "kb-single-leg-rdl")) {
            val shapes = ExerciseFigures.of(id)!!.frame(1.0).shapes
            assertTrue(id, shapes.any { (it is Shape.Dot || it is Shape.Bone) && it.layer == Layer.FAR && (it as? Shape.Dot)?.kind == Kind.WEIGHT || (it as? Shape.Bone)?.kind == Kind.WEIGHT && it.layer == Layer.FAR })
        }
    }

    @Test
    fun theLoopStartsAtTheStillPosition() {
        for (e in Library.all) {
            val f = ExerciseFigures.of(e.id)!!
            assertEquals(e.id, f.still, f.timing.t(f.loopStart), 1e-9)
        }
        // A deadlift rests on its start (the bar on the floor), a squat on its end (the bottom).
        assertEquals(0.0, ExerciseFigures.of("deadlift")!!.still, 0.0)
        assertEquals(1.0, ExerciseFigures.of("back-squat")!!.still, 0.0)
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
