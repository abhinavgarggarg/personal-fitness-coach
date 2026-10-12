package com.personalfitnesscoach.app.figure

import com.personalfitnesscoach.engine.model.Muscle
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The sculpted figure (CR-008, D-105): the same frames as the capsule figure (same poses, same tests), drawn as shaped and shaded body
 * parts, lit from the upper left with a warm rim on the shadow side, and with the exercise's working muscles glowing. Plain Kotlin, so
 * every outline is tested on the JVM; the screen only fills what [Sculptor.sculpt] returns, in order.
 *
 * World units as in [Figure] (y up, the floor at y = 0).
 */
data class Curve(val c1: P, val c2: P, val end: P)

/** A closed outline of cubic curves. */
data class Outline(val start: P, val curves: List<Curve>) {
    /** Points along the drawn outline ([perCurve] per curve), for tests and the camera. */
    fun sample(perCurve: Int = 6): List<P> {
        val out = ArrayList<P>(curves.size * perCurve)
        var from = start
        for (c in curves) {
            for (i in 1..perCurve) {
                val t = i.toDouble() / perCurve; val u = 1 - t
                out += from * (u * u * u) + c.c1 * (3 * u * u * t) + c.c2 * (3 * u * t * t) + c.end * (t * t * t)
            }
            from = c.end
        }
        return out
    }
}

/** The colour ramp of a part: light skin, the darker far side, or a shoe. */
enum class Tone { SKIN, SKIN_FAR, SHOE, SHOE_FAR }

/** A working muscle's glow: an orange light centred on [c], fading out by [r]; the screen clips it to the part. */
data class Glow(val c: P, val r: Double)

sealed interface Sculpt {
    val layer: Layer

    /**
     * A body part, filled with its [tone] shaded from [lit] (the side facing the light) to [shade]. [from], [to] and [r] give its axis and
     * thickness (for the halo behind it).
     */
    data class Part(
        val part: BodyPart, val outline: Outline, val tone: Tone, val lit: P, val shade: P, val glows: List<Glow>, override val layer: Layer,
        val from: P, val to: P, val r: Double,
    ) : Sculpt

    /** The head or a hand: a lit ball. */
    data class Ball(val part: BodyPart, val c: P, val rx: Double, val ry: Double, override val layer: Layer) : Sculpt

    /** Equipment, form guides and anything else, drawn as before. */
    data class Prop(val shape: Shape) : Sculpt { override val layer: Layer get() = shape.layer }
}

/** One sculpted picture: what to draw, in order, and how strongly the muscles glow (0–1; strongest at the end position). */
data class SculptFrame(val items: List<Sculpt>, val glow: Double)

object Sculptor {
    /** Where the light comes from: the upper left of the card. */
    val LIGHT: P = unit(P(-0.55, 0.83))

    /** Radii of a limb segment (world units): at its start, the belly on each side, at its end, and where along it the belly is. */
    private class Shape5(val ra: Double, val bp: Double, val bn: Double, val rb: Double, val at: Double)

    private fun radii(part: BodyPart, front: Boolean): Shape5 = when (part) {
        BodyPart.THIGH -> Shape5(12.5, 13.0, 13.0, 8.0, 0.32)
        BodyPart.SHIN -> Shape5(8.0, 8.6, 8.6, 5.0, 0.28)
        BodyPart.FOOT -> if (front) Shape5(6.5, 6.5, 6.5, 5.5, 0.5) else Shape5(6.0, 6.0, 5.6, 4.2, 0.4)
        BodyPart.UPPER_ARM -> Shape5(9.4, 9.6, 9.0, 6.0, 0.4)
        BodyPart.FOREARM -> Shape5(6.6, 7.0, 6.6, 4.4, 0.28)
        BodyPart.NECK -> Shape5(6.0, 5.8, 5.8, 5.6, 0.5)
        else -> Shape5(10.0, 10.0, 10.0, 10.0, 0.5)
    }

    /** Which muscles light up which limb segment, and where along it (0 = its start). */
    private fun limbGlows(part: BodyPart, muscles: Set<Muscle>, front: Boolean): List<Double> {
        val belly = mutableListOf<Double>()
        fun any(vararg m: Muscle) = m.any { it in muscles }
        when (part) {
            BodyPart.THIGH -> if (any(Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.ADDUCTORS) || (front && Muscle.GLUTES in muscles)) belly += 0.42
            BodyPart.SHIN -> if (Muscle.CALVES in muscles) belly += 0.38
            BodyPart.UPPER_ARM -> {
                if (any(Muscle.FRONT_DELTS, Muscle.SIDE_DELTS, Muscle.REAR_DELTS)) belly += 0.08
                if (any(Muscle.BICEPS, Muscle.TRICEPS)) belly += 0.5
            }
            BodyPart.FOREARM -> if (Muscle.FOREARMS in muscles) belly += 0.38
            else -> Unit
        }
        return belly
    }

    /**
     * The sculpted picture of [figure] at [t] (0 = start position, 1 = end position), with [muscles] (the exercise's primary muscles)
     * glowing.
     */
    fun sculpt(figure: Figure, t: Double, muscles: Set<Muscle>): SculptFrame = sculpt(figure.frame(t), figure is FrontFigure, t, muscles)

    fun sculpt(frame: Frame, front: Boolean, t: Double, muscles: Set<Muscle>): SculptFrame {
        val keyed = ArrayList<Triple<Int, Double, Sculpt>>()   // layer, rank within the layer, item
        val bones = frame.shapes.filterIsInstance<Shape.Bone>()
        fun bone(p: BodyPart) = bones.first { it.part == p }
        frame.shapes.forEachIndexed { i, s ->
            val part = when (s) { is Shape.Bone -> s.part; is Shape.Dot -> s.part; else -> null }
            val order = i * 1e-3
            if (part == null) { keyed += Triple(s.layer.ordinal, -1.0 + order, Sculpt.Prop(s)); return@forEachIndexed }
            val rank = rank(part, front) + order
            when {
                s is Shape.Dot -> keyed += Triple(s.layer.ordinal, rank, Sculpt.Ball(part, s.c, s.r * 0.98, s.r * 1.08, s.layer))
                part == BodyPart.HIPS || part == BodyPart.SHOULDERS -> Unit   // part of the front trunk's outline
                part == BodyPart.TRUNK -> {
                    val b = s as Shape.Bone
                    keyed += Triple(s.layer.ordinal, rank, if (front) frontTrunk(b, bone(BodyPart.HIPS), bone(BodyPart.SHOULDERS), muscles) else sideTrunk(b, muscles))
                }
                else -> {
                    val b = s as Shape.Bone
                    keyed += Triple(s.layer.ordinal, rank, limb(b, part, front, muscles))
                    if (part == BodyPart.FOREARM) {
                        val d = unit(b.b - b.a)
                        keyed += Triple(s.layer.ordinal, rank + 1e-4, Sculpt.Ball(BodyPart.HAND, b.b + d * 4.0, 5.6, 5.6, s.layer))
                    }
                }
            }
        }
        val items = keyed.sortedWith(compareBy({ it.first }, { it.second })).map { it.third }
        return SculptFrame(items, 0.55 + 0.45 * t.coerceIn(0.0, 1.0))
    }

    /** Drawing order inside a layer: in a side view the trunk goes before the near leg; in a front view the legs go under the trunk. */
    private fun rank(p: BodyPart, front: Boolean): Double = if (front) when (p) {
        BodyPart.THIGH -> 0.0; BodyPart.SHIN -> 1.0; BodyPart.FOOT -> 2.0; BodyPart.NECK -> 3.0; BodyPart.TRUNK -> 4.0; BodyPart.HEAD -> 5.0
        BodyPart.UPPER_ARM -> 6.0; BodyPart.FOREARM -> 7.0; else -> 8.0
    } else when (p) {
        BodyPart.NECK -> 0.0; BodyPart.TRUNK -> 1.0; BodyPart.HEAD -> 2.0; BodyPart.THIGH -> 3.0; BodyPart.SHIN -> 4.0; BodyPart.FOOT -> 5.0
        BodyPart.UPPER_ARM -> 6.0; BodyPart.FOREARM -> 7.0; else -> 8.0
    }

    private fun limb(b: Shape.Bone, part: BodyPart, front: Boolean, muscles: Set<Muscle>): Sculpt.Part {
        val r = radii(part, front)
        val a = b.a; val e = b.b
        // A segment seen end-on (a bent-over front view hides the neck) still gets a direction, so it draws as a small rounded shape.
        val len = (e - a).len()
        val d = if (len > 1e-6) unit(e - a) else P(0.0, 1.0); val n = perp(d)
        val m = a + d * (len * r.at)
        val q = a + d * (len * (r.at + 1) / 2)
        val pts = listOf(
            a + n * r.ra, m + n * r.bp, q + n * ((r.bp + r.rb) / 2 * 1.02), e + n * r.rb,
            e + unit(d + n) * r.rb, e + d * r.rb, e + unit(d - n) * r.rb,
            e - n * r.rb, q - n * ((r.bn + r.rb) / 2 * 1.02), m - n * r.bn, a - n * r.ra,
            a + unit(n * -1.0 - d) * r.ra, a - d * r.ra, a + unit(n - d) * r.ra,
        )
        val far = b.layer == Layer.FAR
        val tone = if (part == BodyPart.FOOT) (if (far) Tone.SHOE_FAR else Tone.SHOE) else (if (far) Tone.SKIN_FAR else Tone.SKIN)
        val (lit, shade) = across(a + d * (len / 2), d, max(r.ra, r.bp))
        val glows = limbGlows(part, muscles, front).map { u -> Glow(a + d * (len * u), len * (if (u < 0.2) 0.6 else 0.75)) }
        return Sculpt.Part(part, smooth(pts), tone, lit, shade, glows, b.layer, a, e, max(r.ra, r.bp))
    }

    /** Side-view trunk: chest in front, shoulder blades and glutes behind, a narrower waist. The spine line runs near the back. */
    private fun sideTrunk(b: Shape.Bone, muscles: Set<Muscle>): Sculpt.Part {
        val hip = b.a; val d = unit(b.b - hip); val len = (b.b - hip).len()
        val front = P(d.y, -d.x)   // the way the figure faces
        fun at(u: Double, off: Double) = hip + d * (len * u) + front * off
        val pts = listOf(
            at(-0.10, 10.0), at(-0.02, 18.0), at(0.22, 18.5), at(0.45, 19.0), at(0.66, 23.0), at(0.84, 24.0), at(0.98, 16.0),
            at(1.10, 5.0), at(1.08, -7.0), at(0.98, -14.0), at(0.80, -16.0), at(0.55, -13.0), at(0.30, -13.5), at(0.08, -17.5),
            at(-0.12, -14.5), at(-0.19, -3.0),
        )
        val glows = ArrayList<Glow>()
        fun glow(m: Muscle, u: Double, off: Double) { if (m in muscles) glows += Glow(at(u, off), len * 0.45) }
        glow(Muscle.CHEST, 0.80, 14.0)
        glow(Muscle.LATS, 0.62, -10.0)
        if (Muscle.UPPER_BACK in muscles || Muscle.REAR_DELTS in muscles) glows += Glow(at(0.86, -11.0), len * 0.45)
        glow(Muscle.CORE, 0.42, 11.0)
        glow(Muscle.GLUTES, 0.0, -12.0)
        val (lit, shade) = across(hip + d * (len / 2), d, 22.0)
        return Sculpt.Part(BodyPart.TRUNK, smooth(pts), Tone.SKIN, lit, shade, glows, b.layer, b.a, b.b, 18.0)
    }

    /** Front-view trunk: shoulders wider than the waist, the hips a little wider again; it follows a shrug or a lean. */
    private fun frontTrunk(b: Shape.Bone, hips: Shape.Bone, shoulders: Shape.Bone, muscles: Set<Muscle>): Sculpt.Part {
        val pel = b.a; val d = unit(b.b - pel); val n = perp(d); val len = (b.b - pel).len()
        val rHip = hips.a; val lHip = hips.b; val rSh = shoulders.a; val lSh = shoulders.b
        val side = if ((lSh - rSh).dot(n) > 0) 1.0 else -1.0
        fun at(u: Double, off: Double) = pel + d * (len * u) + n * off
        val pts = listOf(
            rHip - n * (side * 6) - d * 4.0, at(0.30, -side * 15), at(0.62, -side * 18), rSh - n * (side * 4) - d * 6.0,
            rSh + d * 6.0, at(1.02, -side * 9), at(1.08, 0.0), at(1.02, side * 9), lSh + d * 6.0,
            lSh + n * (side * 4) - d * 6.0, at(0.62, side * 18), at(0.30, side * 15), lHip + n * (side * 6) - d * 4.0,
            at(-0.16, side * 6), at(-0.18, 0.0), at(-0.16, -side * 6),
        )
        val glows = ArrayList<Glow>()
        val r = len * 0.42
        if (Muscle.CHEST in muscles) { glows += Glow(at(0.82, 8.0), r); glows += Glow(at(0.82, -8.0), r) }
        if (Muscle.LATS in muscles) { glows += Glow(at(0.6, 14.0), r); glows += Glow(at(0.6, -14.0), r) }
        if (Muscle.UPPER_BACK in muscles) { glows += Glow(at(1.0, 7.0), r); glows += Glow(at(1.0, -7.0), r) }
        if (Muscle.CORE in muscles) glows += Glow(at(0.4, 0.0), r)
        if (Muscle.SIDE_DELTS in muscles || Muscle.FRONT_DELTS in muscles || Muscle.REAR_DELTS in muscles) { glows += Glow(rSh, r); glows += Glow(lSh, r) }
        val (lit, shade) = across(pel + d * (len / 2), d, 22.0)
        return Sculpt.Part(BodyPart.TRUNK, smooth(pts), Tone.SKIN, lit, shade, glows, b.layer, b.a, b.b, 18.0)
    }

    /** The two ends of a part's shading: across the part, the lit side towards [LIGHT]. */
    private fun across(mid: P, along: P, r: Double): Pair<P, P> {
        var n = perp(along); if (n.dot(LIGHT) < 0) n = n * -1.0
        return (mid + n * r) to (mid - n * r)
    }

    /** A smooth closed outline through [pts] (Catmull–Rom). */
    internal fun smooth(pts: List<P>): Outline {
        val k = pts.size
        val curves = (0 until k).map { i ->
            val p0 = pts[(i - 1 + k) % k]; val p1 = pts[i]; val p2 = pts[(i + 1) % k]; val p3 = pts[(i + 2) % k]
            Curve(p1 + (p2 - p0) * (1.0 / 6), p2 - (p3 - p1) * (1.0 / 6), p2)
        }
        return Outline(pts[0], curves)
    }

    /** The box that holds every sculpted frame, props included: the camera for the sculpted figure. */
    fun camera(figure: Figure): Bounds = (0..12).map { i ->
        val f = sculpt(figure, i / 12.0, emptySet())
        var minX = Double.MAX_VALUE; var minY = 0.0; var maxX = -Double.MAX_VALUE; var maxY = 0.0
        fun add(p: P, pad: Double) { minX = min(minX, p.x - pad); maxX = max(maxX, p.x + pad); minY = min(minY, p.y - pad); maxY = max(maxY, p.y + pad) }
        for (s in f.items) when (s) {
            is Sculpt.Part -> s.outline.sample(4).forEach { add(it, 0.0) }
            is Sculpt.Ball -> { add(s.c, max(s.rx, s.ry)) }
            is Sculpt.Prop -> Frame(listOf(s.shape)).extent().let { add(P(it.minX, it.minY), 0.0); add(P(it.maxX, it.maxY), 0.0) }
        }
        Bounds(minX, minY, maxX, maxY)
    }.reduce { a, b -> a.union(b) }
}

private fun P.len() = hypot(x, y)
private fun P.dot(o: P) = x * o.x + y * o.y
private fun perp(d: P) = P(-d.y, d.x)
private fun unit(v: P): P { val l = hypot(v.x, v.y).coerceAtLeast(1e-9); return P(v.x / l, v.y / l) }
