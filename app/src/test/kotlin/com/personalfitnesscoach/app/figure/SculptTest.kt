package com.personalfitnesscoach.app.figure

import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.Muscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** CR-008 (D-105): the sculpted figure draws every body part of every frame, in the right order, on the floor, with its muscles lit. */
class SculptTest {

    private val steps = (0..12).map { it / 12.0 }
    private fun figure(id: String) = ExerciseFigures.of(id)!!

    private fun parts(f: SculptFrame) = f.items.filterIsInstance<Sculpt.Part>()

    @Test
    fun everyBodyPartIsSculpted() {
        for (e in Library.all) {
            val fig = figure(e.id)
            for (t in steps) {
                val s = Sculptor.sculpt(fig, t, e.primary)
                val names = parts(s).map { it.part }.sorted()
                val balls = s.items.filterIsInstance<Sculpt.Ball>().map { it.part }
                if (fig is FrontFigure) {
                    // Two of each limb segment, the trunk and the neck; the hips and shoulders lines become the trunk's outline.
                    val expected = (listOf(BodyPart.THIGH, BodyPart.SHIN, BodyPart.FOOT, BodyPart.UPPER_ARM, BodyPart.FOREARM).flatMap { listOf(it, it) } +
                        BodyPart.TRUNK + BodyPart.NECK).sorted()
                    assertEquals("${e.id} t=$t", expected, names)
                } else {
                    val expected = (listOf(BodyPart.THIGH, BodyPart.SHIN, BodyPart.FOOT, BodyPart.UPPER_ARM, BodyPart.FOREARM).flatMap { listOf(it, it) } +
                        BodyPart.TRUNK + BodyPart.NECK).sorted()
                    assertEquals("${e.id} t=$t", expected, names)
                    assertEquals("${e.id} far side", 5, parts(s).count { it.layer == Layer.FAR })
                }
                assertEquals("${e.id} t=$t: a head and two hands", listOf(BodyPart.HEAD, BodyPart.HAND, BodyPart.HAND).sorted(), balls.sorted())
            }
        }
    }

    @Test
    fun everyOutlineIsFiniteClosedAndHasArea() {
        for (e in Library.all) {
            val fig = figure(e.id)
            for (t in steps) for (p in parts(Sculptor.sculpt(fig, t, e.primary))) {
                val pts = p.outline.sample(4)
                assertTrue("${e.id} ${p.part} finite", pts.all { it.x.isFinite() && it.y.isFinite() })
                assertEquals("${e.id} ${p.part} closes where it starts", 0.0, (p.outline.curves.last().end - p.outline.start).let { abs(it.x) + abs(it.y) }, 1e-9)
                // Shoelace area of the drawn outline: a real shape, not a sliver.
                val area = abs(pts.indices.sumOf { i -> val a = pts[i]; val b = pts[(i + 1) % pts.size]; a.x * b.y - b.x * a.y } / 2)
                assertTrue("${e.id} ${p.part} t=$t has area ($area)", area > 60.0)
            }
        }
    }

    /** As for the capsule figure: a rounded part may touch the floor line, never sink through it. */
    @Test
    fun nothingSculptedGoesThroughTheFloor() {
        val sinking = ArrayList<String>()
        for (e in Library.all) {
            val fig = figure(e.id)
            for (t in steps) {
                val s = Sculptor.sculpt(fig, t, e.primary)
                val lowest = s.items.minOf { i ->
                    when (i) {
                        is Sculpt.Part -> i.outline.sample(8).minOf { it.y }
                        is Sculpt.Ball -> i.c.y - i.ry
                        is Sculpt.Prop -> Double.MAX_VALUE   // checked by ExerciseFiguresTest
                    }
                }
                if (lowest < -5.0) { sinking += "${e.id} at t=$t: ${"%.1f".format(lowest)}"; break }
            }
        }
        assertTrue("below the floor: $sinking", sinking.isEmpty())
    }

    /** The far limbs go behind the body, the near arm in front of it, props keep their layers, and the form guide comes last. */
    @Test
    fun theDrawingOrderKeepsTheLayers() {
        for (e in Library.all) {
            val s = Sculptor.sculpt(figure(e.id), 0.5, e.primary)
            val layers = s.items.map { it.layer.ordinal }
            assertEquals("${e.id} layers in order", layers.sorted(), layers)
            val i = s.items
            val trunk = i.indexOfFirst { it is Sculpt.Part && it.part == BodyPart.TRUNK }
            val head = i.indexOfFirst { it is Sculpt.Ball && it.part == BodyPart.HEAD }
            assertTrue("${e.id} head over the trunk", head > trunk)
            if (figure(e.id) !is FrontFigure) {
                val nearThigh = i.indexOfFirst { it is Sculpt.Part && it.part == BodyPart.THIGH && it.layer == Layer.BODY }
                assertTrue("${e.id} near leg over the trunk", nearThigh > trunk)
            } else {
                assertTrue("${e.id} legs under the trunk", i.indexOfFirst { it is Sculpt.Part && it.part == BodyPart.THIGH } < trunk)
            }
            i.forEachIndexed { k, item ->
                if (item is Sculpt.Part && item.part == BodyPart.FOREARM) {
                    val next = i[k + 1]
                    assertTrue("${e.id}: a hand right after each forearm", next is Sculpt.Ball && next.part == BodyPart.HAND && next.layer == item.layer)
                }
            }
        }
    }

    /** Every exercise's working muscles light up somewhere on the body, in a side view and in a front view. */
    @Test
    fun everyExerciseShowsItsWorkingMuscles() {
        for (e in Library.all) {
            val glows = parts(Sculptor.sculpt(figure(e.id), 1.0, e.primary)).sumOf { it.glows.size }
            assertTrue("${e.id} (${e.primary}) glows", glows > 0)
        }
        // Every muscle the library uses lights something in both views.
        val side = figure("back-squat").frame(0.5); val front = figure("db-lateral-raise").frame(0.5)
        for (m in Muscle.values()) for ((name, frame, isFront) in listOf(Triple("side", side, false), Triple("front", front, true))) {
            val n = parts(Sculptor.sculpt(frame, isFront, 0.5, setOf(m))).sumOf { it.glows.size }
            assertTrue("$m lights something in a $name view", n > 0)
        }
        val none = parts(Sculptor.sculpt(figure("back-squat"), 0.5, emptySet())).sumOf { it.glows.size }
        assertEquals("no muscles, no glow", 0, none)
    }

    @Test
    fun theGlowIsStrongestAtTheEndPosition() {
        val f = figure("back-squat")
        assertEquals(0.55, Sculptor.sculpt(f, 0.0, emptySet()).glow, 1e-9)
        assertEquals(1.0, Sculptor.sculpt(f, 1.0, emptySet()).glow, 1e-9)
    }

    /** Light from the upper left: every part's lit end is on the light side of its shaded end. */
    @Test
    fun everyPartIsLitFromTheSameSide() {
        for (e in Library.all) for (p in parts(Sculptor.sculpt(figure(e.id), 0.5, e.primary))) {
            val d = p.lit - p.shade
            assertTrue("${e.id} ${p.part}", d.x * Sculptor.LIGHT.x + d.y * Sculptor.LIGHT.y >= 0)
        }
    }

    /** The sculpted camera makes a picture that fits a card, and holds the body between the frames it was measured on too. */
    @Test
    fun theSculptedCameraFitsACard() {
        val between = (0 until 24).map { (it + 0.5) / 24 }
        for (e in Library.all) {
            val fig = figure(e.id)
            val c = Sculptor.camera(fig)
            val aspect = c.width / c.height
            assertTrue("${e.id} aspect $aspect", aspect in 0.2..4.5)
            for (t in between) for (p in parts(Sculptor.sculpt(fig, t, emptySet()))) for (q in p.outline.sample(4)) {
                // A few units' slack: the camera is measured on 13 frames and a 8% margin surrounds it on the card.
                assertTrue("${e.id} t=$t ${p.part} inside the camera", q.x >= c.minX - 4 && q.x <= c.maxX + 4 && q.y <= c.maxY + 4)
            }
        }
    }

    /** The trunk faces the way the figure faces: chest forward when standing, up when lying on the back, down in a push-up. */
    @Test
    fun theChestFacesTheRightWay() {
        fun chestSide(id: String, t: Double): P {
            val s = Sculptor.sculpt(figure(id), t, setOf(Muscle.CHEST))
            val trunk = parts(s).first { it.part == BodyPart.TRUNK }
            val mid = (trunk.from + trunk.to) * 0.5
            return trunk.glows.first().c - mid
        }
        assertTrue("standing (overhead press) chest to the right", chestSide("overhead-press", 0.0).x > 0)
        assertTrue("bench press chest up", chestSide("bench-press", 0.0).y > 0)
        assertTrue("push-up chest down", chestSide("push-up", 0.0).y < 0)
    }
}
