package com.personalfitnesscoach.app.figure

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The exercise figures (CR-004, D-061, D-099): an original capsule figure computed from a few joint angles for the start and the end of
 * a rep, so every exercise shares one style, animates smoothly and needs no pictures or downloads. Plain Kotlin (tested on the JVM); the
 * screen only draws the [Shape]s a [Frame] returns.
 *
 * World units: about 285 = standing height; x to the right (the way a side-view figure faces), y up, the floor at y = 0. Angles in
 * degrees, 0 = pointing right, 90 = up. Side view unless the movement happens across the body (then [FrontFigure]).
 */
data class P(val x: Double, val y: Double) {
    operator fun plus(o: P) = P(x + o.x, y + o.y)
    operator fun minus(o: P) = P(x - o.x, y - o.y)
    operator fun times(k: Double) = P(x * k, y * k)
    fun dist(o: P) = hypot(x - o.x, y - o.y)
}

/** Segment lengths, from the Form Guide sample figure. */
object Body {
    const val SHIN = 74.0
    const val THIGH = 73.0
    const val TORSO = 86.0
    const val NECK = 9.0
    const val HEAD = 15.0
    const val UPPER_ARM = 56.0
    const val FOREARM = 46.0
    /** The ankle joint's height above the sole when the foot is flat. */
    const val ANKLE_H = 12.0
    /** Standing hip height. */
    const val HIP_H = ANKLE_H + SHIN + THIGH
}

private fun rad(a: Double) = Math.toRadians(a)
fun at(p: P, angle: Double, len: Double) = P(p.x + cos(rad(angle)) * len, p.y + sin(rad(angle)) * len)
fun rotate(v: P, angle: Double): P { val r = rad(angle); return P(v.x * cos(r) - v.y * sin(r), v.x * sin(r) + v.y * cos(r)) }
internal fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
internal fun lerp(a: P, b: P, t: Double) = P(lerp(a.x, b.x, t), lerp(a.y, b.y, t))

/** Named joints of a frame, for props and form guides that move with the body. */
enum class Joint { HIP, SHOULDER, HEAD, NEAR_KNEE, FAR_KNEE, NEAR_ANKLE, FAR_ANKLE, NEAR_ELBOW, NEAR_WRIST, FAR_WRIST, MID_WRIST }

/**
 * A two-segment limb: by its two segment angles and the foot's (or hand's) turn, or reaching for a point (two-joint IK; [bend] = which
 * side the middle joint goes: +1 counter-clockwise of the line to the target, −1 clockwise). [rel] = the angles are relative to the
 * trunk (an arm that moves with it, like hands holding a bar on the back). A [Reach] target is in world units, or relative to the hip
 * or the shoulder when [from] is set (it then moves with that joint). [end] turns the foot from flat on the floor (positive = toes up).
 */
sealed interface Limb {
    val end: Double
    data class Angles(val a: Double, val b: Double, override val end: Double = 0.0, val rel: Boolean = false) : Limb
    data class Reach(val target: P, val bend: Int, override val end: Double = 0.0, val from: Joint? = null) : Limb
}

/** One position of the body. [hip] is where the hip is when the figure is anchored at the hip; otherwise the anchor places it. */
data class Pose(
    val torso: Double = 90.0,
    val neck: Double = 0.0,
    val near: Limb = Limb.Angles(-90.0, -90.0),
    val far: Limb = near,
    val nearArm: Limb = Limb.Angles(-93.0, -91.0),
    val farArm: Limb = nearArm,
    val hip: P = P(0.0, Body.HIP_H),
)

/** What stays still while the body moves: a foot on the floor, a knee on the floor, or the hip (seated, lying, hanging). */
enum class Anchor { NEAR_ANKLE, NEAR_KNEE, HIP }

/** Equipment and lines drawn with the figure. A prop at a [Joint] moves with the body. */
sealed interface Prop {
    /** A barbell end with its plate (the bar is seen end-on). [withTorso] turns the offset with the trunk (a bar on the back). */
    data class Plate(val at: Joint, val dx: Double = 0.0, val dy: Double = 0.0, val r: Double = 25.0, val withTorso: Boolean = false) : Prop
    /** A dumbbell: seen end-on (a disc), or side-on across the forearm (a hammer grip) or upright (held at the chest). */
    data class Dumbbell(val at: Joint, val dx: Double = 0.0, val dy: Double = 0.0, val view: DbView = DbView.END_ON) : Prop
    /** A kettlebell hanging from the hand, or ([along]) carried on in the forearm's direction (a swing). */
    data class Kettlebell(val at: Joint, val dx: Double = 0.0, val dy: Double = -12.0, val along: Boolean = false) : Prop
    data class Ball(val at: Joint, val dx: Double = 0.0, val dy: Double = 0.0, val r: Double = 17.0) : Prop
    /** A cable (with its pulley) or a band from a joint to a fixed point. */
    data class Line(val from: Joint, val to: P, val band: Boolean = false) : Prop
    /** A fixed box: bench, plyo box, chair, machine seat or pad. [angle] tilts it about its bottom-left corner. */
    data class Block(val x: Double, val y: Double, val w: Double, val h: Double, val angle: Double = 0.0) : Prop
    /** A fixed bar seen end-on (pull-up bar, dip handle, rack bar). */
    data class Bar(val at: P) : Prop
    /** A fixed straight line (machine frame, cable tower, backrest). */
    data class Rail(val a: P, val b: P) : Prop
    /** A landmine: the bar from its floor pivot to the hands, with the plate just before them (or just past them, [beyond]). */
    data class Landmine(val pivot: P, val to: Joint, val beyond: Boolean = false) : Prop
    /** A pad, handle, platform or slider: a short thick bar through a joint (it moves with it). [angle] in world degrees. */
    data class Pad(val at: Joint, val angle: Double, val len: Double, val width: Double = 10.0, val dx: Double = 0.0, val dy: Double = 0.0) : Prop
    /** A weight vest on the trunk. */
    data object Vest : Prop
    /** A wheel or a slider under a joint (ab wheel, sliders). */
    data class Wheel(val at: Joint, val r: Double = 13.0, val dx: Double = 0.0, val dy: Double = 0.0) : Prop
}

enum class DbView { END_ON, ACROSS, UPRIGHT }

/** The main form point, drawn as a dashed line in the cool colour: a straight back, a straight body, a vertical shin. */
sealed interface Guide {
    data class Through(val a: Joint, val b: Joint, val extend: Double = 1.2) : Guide
    data class Vertical(val at: Joint) : Guide
}

/** How a rep is timed (fractions of one loop): hold, move to the end, hold, come back. */
data class Timing(val holdStart: Double = 0.14, val toEnd: Double = 0.36, val holdEnd: Double = 0.12, val back: Double = 0.38, val seconds: Double = 4.0) {
    /** 0 = start position, 1 = end position, for a point [u] (0–1, wraps) in the loop. */
    fun t(u: Double): Double {
        fun ease(x: Double) = if (x < 0.5) 2 * x * x else 1 - (-2 * x + 2) * (-2 * x + 2) / 2
        var x = u - kotlin.math.floor(u)
        if (x < holdStart) return 0.0
        x -= holdStart
        if (x < toEnd) return ease(x / toEnd)
        x -= toEnd
        if (x < holdEnd) return 1.0
        x -= holdEnd
        if (x < back) return 1 - ease(x / back)
        return 0.0
    }

    /** The middle of the hold at the end position: the loop starts here, so the first picture (and a test's) shows the end of the rep. */
    val peak: Double get() = holdStart + toEnd + holdEnd / 2

    companion object {
        val NORMAL = Timing()
        /** A hold (plank, side plank): a slow, small breathing movement. */
        val HOLD = Timing(0.0, 0.5, 0.0, 0.5, 5.0)
        /** Walking, crawling and carrying: even steps. */
        val STEP = Timing(0.05, 0.42, 0.06, 0.47, 1.8)
        /** Fast and powerful (jumps, swings, throws): a quick move, then a slower return. */
        val FAST = Timing(0.22, 0.2, 0.16, 0.42, 2.6)
    }
}

/** Something to draw. [layer] orders the drawing: far limbs behind the body, the near arm in front. */
sealed interface Shape {
    val layer: Layer
    /** A rounded segment of the body (or a handle). */
    data class Bone(val a: P, val b: P, val width: Double, override val layer: Layer, val kind: Kind = Kind.BODY) : Shape
    data class Dot(val c: P, val r: Double, override val layer: Layer, val kind: Kind = Kind.BODY) : Shape
    data class Poly(val points: List<P>, override val layer: Layer, val kind: Kind = Kind.WEIGHT) : Shape
    data class Stroke(val a: P, val b: P, val kind: Kind, override val layer: Layer = Layer.PROP_BACK) : Shape
    data class Box(val corners: List<P>, override val layer: Layer = Layer.PROP_BACK) : Shape
}

enum class Layer { PROP_BACK, FAR, BODY, NEAR, PROP_FRONT, GUIDE }
enum class Kind { BODY, WEIGHT, HOLE, CABLE, BAND, RAIL, GUIDE, PULLEY }

/** One computed picture. The floor is the line y = 0; the screen draws it. */
data class Frame(val shapes: List<Shape>) {
    /** The box around everything drawn (bones and dots with their thickness). */
    fun extent(): Bounds {
        var minX = Double.MAX_VALUE; var minY = 0.0; var maxX = -Double.MAX_VALUE; var maxY = 0.0
        fun add(p: P, pad: Double) { minX = min(minX, p.x - pad); maxX = max(maxX, p.x + pad); minY = min(minY, p.y - pad); maxY = max(maxY, p.y + pad) }
        for (s in shapes) when (s) {
            is Shape.Bone -> { add(s.a, s.width / 2); add(s.b, s.width / 2) }
            is Shape.Dot -> add(s.c, s.r)
            is Shape.Poly -> s.points.forEach { add(it, 0.0) }
            is Shape.Stroke -> { add(s.a, 1.0); add(s.b, 1.0) }
            is Shape.Box -> s.corners.forEach { add(it, 0.0) }
        }
        return Bounds(minX, minY, maxX, maxY)
    }
}

data class Bounds(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
    val width get() = maxX - minX
    val height get() = maxY - minY
    fun union(o: Bounds) = Bounds(min(minX, o.minX), min(minY, o.minY), max(maxX, o.maxX), max(maxY, o.maxY))
}

/** A figure: anything that draws itself at a point t of the movement (0 = start position, 1 = end position). */
sealed interface Figure {
    val timing: Timing
    fun frame(t: Double): Frame

    /**
     * The position shown standing still (animations off) and where the loop starts: 1 = the end of the rep (most exercises), 0 = the
     * start, when the start tells more (a deadlift's start is the bar on the floor; its end is just standing).
     */
    val still: Double get() = 1.0

    /** Where in the loop (0–1) to begin, so the first picture is the [still] position. */
    val loopStart: Double get() = if (still < 0.5) timing.holdStart / 2 else timing.peak

    /** The box that holds every frame: the camera never moves during the animation. */
    fun camera(): Bounds = (0..12).map { frame(it / 12.0).extent() }.reduce { a, b -> a.union(b) }
}

/** The joints of one side-view frame. */
data class Skeleton(
    val hip: P, val shoulder: P, val neckTop: P, val head: P,
    val nKnee: P, val nAnkle: P, val nFoot: Double,
    val fKnee: P, val fAnkle: P, val fFoot: Double,
    val nElbow: P, val nWrist: P, val fElbow: P, val fWrist: P,
    val torso: Double,
) {
    fun joint(j: Joint): P = when (j) {
        Joint.HIP -> hip
        Joint.SHOULDER -> shoulder
        Joint.HEAD -> head
        Joint.NEAR_KNEE -> nKnee
        Joint.FAR_KNEE -> fKnee
        Joint.NEAR_ANKLE -> nAnkle
        Joint.FAR_ANKLE -> fAnkle
        Joint.NEAR_ELBOW -> nElbow
        Joint.NEAR_WRIST -> nWrist
        Joint.FAR_WRIST -> fWrist
        Joint.MID_WRIST -> lerp(nWrist, fWrist, 0.5)
    }
}

/** Two-joint reach from [s] towards [t] with segment lengths [a] and [b]; out of reach, the limb points straight at it. */
fun reach(s: P, t: P, a: Double, b: Double, bend: Int): Pair<P, P> {
    var dx = t.x - s.x
    var dy = t.y - s.y
    var d = hypot(dx, dy)
    val maxD = a + b - 0.01
    val minD = abs(a - b) + 0.01
    if (d > maxD) { dx *= maxD / d; dy *= maxD / d; d = maxD }
    if (d < minD) { if (d < 1e-6) { dx = 0.0; dy = -1.0; d = 1.0 }; dx *= minD / d; dy *= minD / d; d = minD }
    val phi = atan2(dy, dx)
    val alpha = acos(((a * a + d * d - b * b) / (2 * a * d)).coerceIn(-1.0, 1.0))
    val mid = P(s.x + cos(phi + bend * alpha) * a, s.y + sin(phi + bend * alpha) * a)
    return mid to P(s.x + dx, s.y + dy)
}

/** The foot as a capsule from heel to toes, around the ankle, turned [angle] degrees from flat (positive = toes up). */
internal fun foot(ankle: P, angle: Double): Pair<P, P> = (ankle + rotate(P(-4.0, -7.0), angle)) to (ankle + rotate(P(24.0, -7.5), angle))

/** A side-view figure. Far limbs are drawn a little up and back and darker, so left and right read apart. */
data class SideFigure(
    val start: Pose,
    val end: Pose,
    val anchor: Anchor = Anchor.NEAR_ANKLE,
    /** Where the anchor joint sits; for a flat foot, the ankle is [Body.ANKLE_H] above the floor. */
    val anchorAt: P = P(0.0, Body.ANKLE_H),
    val props: List<Prop> = emptyList(),
    val guide: Guide? = null,
    override val timing: Timing = Timing.NORMAL,
    /** An optional middle position (a foot lifted in a step, the top of a jump): start → mid → end. */
    val mid: Pose? = null,
    override val still: Double = 1.0,
) : Figure {

    /**
     * Blends two positions of one limb. Two angle positions blend their angles; two reaches measured from the same place with the same
     * bend blend their targets (so a hand that moves with the shoulder keeps doing so). Anything else — a fixed point and a point
     * measured from the shoulder, a reach and an angle position, different bends — blends where the hand or foot actually is, in the
     * room, so the limb moves smoothly instead of snapping or reaching for a wrong point (review R7-01, R7-04).
     */
    private fun mix(a: Limb, b: Limb, t: Double, pa: Pose, pb: Pose, arm: Boolean): Limb = when {
        a is Limb.Angles && b is Limb.Angles -> Limb.Angles(lerp(a.a, b.a, t), lerp(a.b, b.b, t), lerp(a.end, b.end, t), a.rel)
        a is Limb.Reach && b is Limb.Reach && a.from == b.from && a.bend == b.bend ->
            Limb.Reach(lerp(a.target, b.target, t), a.bend, lerp(a.end, b.end, t), a.from)
        else -> Limb.Reach(lerp(endPoint(a, pa, arm), endPoint(b, pb, arm), t), (a as? Limb.Reach)?.bend ?: (b as Limb.Reach).bend,
            lerp(a.end, b.end, t), null)
    }

    /** The hip and the shoulder of a pose (from its anchor), before any limb is placed. */
    private fun base(p: Pose): Pair<P, P> {
        val hip = when (anchor) {
            Anchor.HIP -> p.hip
            Anchor.NEAR_ANKLE, Anchor.NEAR_KNEE -> {
                val l = p.near as? Limb.Angles ?: error("an ankle- or knee-anchored figure needs its near leg by angles")
                val knee = at(P(0.0, 0.0), l.a, Body.THIGH)
                val joint = if (anchor == Anchor.NEAR_KNEE) knee else at(knee, l.b, Body.SHIN)
                anchorAt - joint
            }
        }
        return hip to at(hip, p.torso, Body.TORSO)
    }

    private fun target(l: Limb.Reach, hip: P, shoulder: P) = when (l.from) {
        Joint.HIP -> hip + l.target
        Joint.SHOULDER -> shoulder + l.target
        else -> l.target
    }

    /** Where a limb's hand or foot is in a pose (for a reach: the point it reaches for). */
    private fun endPoint(l: Limb, p: Pose, arm: Boolean): P {
        val (hip, shoulder) = base(p)
        return when (l) {
            is Limb.Reach -> target(l, hip, shoulder)
            is Limb.Angles -> if (arm) {
                val off = if (l.rel) p.torso - 90.0 else 0.0
                at(at(shoulder, l.a + off, Body.UPPER_ARM), l.b + off, Body.FOREARM)
            } else at(at(hip, l.a, Body.THIGH), l.b, Body.SHIN)
        }
    }

    fun pose(t: Double): Pose {
        if (mid != null) return if (t < 0.5) between(start, mid, t * 2) else between(mid, end, t * 2 - 1)
        return between(start, end, t)
    }

    private fun between(a: Pose, b: Pose, t: Double) = Pose(
        lerp(a.torso, b.torso, t), lerp(a.neck, b.neck, t),
        mix(a.near, b.near, t, a, b, arm = false), mix(a.far, b.far, t, a, b, arm = false),
        mix(a.nearArm, b.nearArm, t, a, b, arm = true), mix(a.farArm, b.farArm, t, a, b, arm = true),
        lerp(a.hip, b.hip, t),
    )

    fun skeleton(t: Double): Skeleton {
        val p = pose(t)
        val (hip, shoulder) = base(p)
        val headDir = p.torso + p.neck
        val neckTop = at(shoulder, headDir, Body.NECK)
        val head = at(neckTop, headDir, Body.HEAD)
        fun leg(l: Limb): Pair<P, P> = when (l) {
            is Limb.Angles -> at(hip, l.a, Body.THIGH).let { k -> k to at(k, l.b, Body.SHIN) }
            is Limb.Reach -> reach(hip, target(l, hip, shoulder), Body.THIGH, Body.SHIN, l.bend)
        }
        fun arm(l: Limb): Pair<P, P> = when (l) {
            is Limb.Angles -> {
                val off = if (l.rel) p.torso - 90.0 else 0.0
                at(shoulder, l.a + off, Body.UPPER_ARM).let { e -> e to at(e, l.b + off, Body.FOREARM) }
            }
            is Limb.Reach -> reach(shoulder, target(l, hip, shoulder), Body.UPPER_ARM, Body.FOREARM, l.bend)
        }
        val (nk, na) = leg(p.near)
        val (fk, fa) = leg(p.far)
        val (ne, nw) = arm(p.nearArm)
        val (fe, fw) = arm(p.farArm)
        return Skeleton(hip, shoulder, neckTop, head, nk, na, p.near.end, fk, fa, p.far.end, ne, nw, fe, fw, p.torso)
    }

    /** For tests: each hand and foot that reaches for a point, as (where it is, where it was meant to be), at a point t. */
    fun reaches(t: Double): List<Pair<P, P>> {
        val p = pose(t)
        val s = skeleton(t)
        val (hip, shoulder) = base(p)
        return listOf(p.near to s.nAnkle, p.far to s.fAnkle, p.nearArm to s.nWrist, p.farArm to s.fWrist)
            .mapNotNull { (l, actual) -> (l as? Limb.Reach)?.let { actual to target(it, hip, shoulder) } }
    }

    /** The keyframes' points: 0, the middle position (when there is one) and 1. */
    val keyframes: List<Double> get() = if (mid != null) listOf(0.0, 0.5, 1.0) else listOf(0.0, 1.0)

    override fun frame(t: Double): Frame {
        val s = skeleton(t)
        val out = ArrayList<Shape>()
        val back = FAR_SHIFT
        for (pr in props) addProp(pr, s, out, behind = true)
        // The far side, a little behind.
        out += Shape.Bone(s.hip + back, s.fKnee + back, 20.0, Layer.FAR)
        out += Shape.Bone(s.fKnee + back, s.fAnkle + back, 15.0, Layer.FAR)
        foot(s.fAnkle + back, s.fFoot).let { (h, toe) -> out += Shape.Bone(h, toe, 10.0, Layer.FAR) }
        out += Shape.Bone(s.shoulder + back, s.fElbow + back, 12.0, Layer.FAR)
        out += Shape.Bone(s.fElbow + back, s.fWrist + back, 10.0, Layer.FAR)
        // The body and the near leg.
        out += Shape.Bone(s.hip, s.shoulder, 30.0, Layer.BODY)
        out += Shape.Bone(s.shoulder, s.neckTop, 11.0, Layer.BODY)
        out += Shape.Dot(s.head, Body.HEAD, Layer.BODY)
        out += Shape.Bone(s.hip, s.nKnee, 22.0, Layer.BODY)
        out += Shape.Bone(s.nKnee, s.nAnkle, 17.0, Layer.BODY)
        foot(s.nAnkle, s.nFoot).let { (h, toe) -> out += Shape.Bone(h, toe, 11.0, Layer.BODY) }
        // The near arm in front.
        out += Shape.Bone(s.shoulder, s.nElbow, 13.0, Layer.NEAR)
        out += Shape.Bone(s.nElbow, s.nWrist, 11.0, Layer.NEAR)
        for (pr in props) addProp(pr, s, out, behind = false)
        when (val g = guide) {
            is Guide.Through -> {
                val a = s.joint(g.a); val b = s.joint(g.b)
                val pad = (b - a) * ((g.extend - 1) / 2)
                out += Shape.Stroke(a - pad, b + pad, Kind.GUIDE, Layer.GUIDE)
            }
            is Guide.Vertical -> { val p = s.joint(g.at); out += Shape.Stroke(P(p.x, p.y + 30), P(p.x, 2.0), Kind.GUIDE, Layer.GUIDE) }
            null -> Unit
        }
        return Frame(out)
    }

    private fun elbowOf(j: Joint, s: Skeleton) = if (j == Joint.FAR_WRIST) s.fElbow else s.nElbow

    private fun forearmAngle(j: Joint, s: Skeleton): Double {
        val d = s.joint(j) - elbowOf(j, s)
        return Math.toDegrees(atan2(d.y, d.x))
    }

    private fun place(at: Joint, dx: Double, dy: Double, s: Skeleton, withTorso: Boolean = false): P =
        s.joint(at) + if (withTorso) rotate(P(dx, dy), s.torso - 90.0) else P(dx, dy)

    private fun addProp(pr: Prop, s: Skeleton, out: MutableList<Shape>, behind: Boolean) {
        when (pr) {
            is Prop.Block -> if (behind) out += Shape.Box(block(pr))
            is Prop.Rail -> if (behind) out += Shape.Stroke(pr.a, pr.b, Kind.RAIL)
            is Prop.Bar -> if (behind) out += Shape.Dot(pr.at, 6.0, Layer.PROP_BACK, Kind.RAIL)
            is Prop.Line -> if (behind) {
                out += Shape.Stroke(s.joint(pr.from), pr.to, if (pr.band) Kind.BAND else Kind.CABLE)
                out += Shape.Dot(pr.to, if (pr.band) 5.0 else 8.0, Layer.PROP_BACK, Kind.PULLEY)
            }
            is Prop.Landmine -> if (behind) {
                val hand = s.joint(pr.to)
                val dir = hand - pr.pivot
                val unit = dir * (1 / hypot(dir.x, dir.y).coerceAtLeast(1.0))
                val plate = hand + unit * (if (pr.beyond) 30.0 else -30.0)
                out += Shape.Stroke(pr.pivot, hand + unit * 12.0, Kind.RAIL)
                out += Shape.Dot(plate, 22.0, Layer.PROP_BACK, Kind.WEIGHT)
                out += Shape.Dot(plate, 5.0, Layer.PROP_BACK, Kind.HOLE)
            }
            is Prop.Pad -> if (!behind) {
                val c = place(pr.at, pr.dx, pr.dy, s)
                val h = at(P(0.0, 0.0), pr.angle, pr.len / 2)
                out += Shape.Bone(c - h, c + h, pr.width, Layer.PROP_FRONT, Kind.WEIGHT)
            }
            is Prop.Vest -> if (!behind) out += Shape.Bone(lerp(s.hip, s.shoulder, 0.35), lerp(s.hip, s.shoulder, 0.9), 26.0, Layer.PROP_FRONT, Kind.WEIGHT)
            is Prop.Plate -> if (behind) {
                val c = place(pr.at, pr.dx, pr.dy, s, pr.withTorso)
                out += Shape.Dot(c, pr.r, Layer.PROP_BACK, Kind.WEIGHT)
                out += Shape.Dot(c, pr.r * 0.24, Layer.PROP_BACK, Kind.HOLE)
            }
            is Prop.Dumbbell -> if (behind == (pr.at == Joint.FAR_WRIST)) {
                val layer = if (behind) Layer.FAR else Layer.PROP_FRONT
                val c = place(pr.at, pr.dx, pr.dy, s) + if (behind) FAR_SHIFT else P(0.0, 0.0)
                when (pr.view) {
                    DbView.END_ON -> {
                        out += Shape.Dot(c, 11.0, layer, Kind.WEIGHT)
                        out += Shape.Dot(c, 3.5, layer, Kind.HOLE)
                    }
                    DbView.ACROSS, DbView.UPRIGHT -> {
                        val axis = if (pr.view == DbView.UPRIGHT) 90.0 else forearmAngle(pr.at, s) + 90.0
                        val e1 = at(c, axis, 15.0); val e2 = at(c, axis + 180, 15.0)
                        out += Shape.Bone(e1, e2, 6.0, layer, Kind.WEIGHT)
                        out += Shape.Bone(at(e1, axis, -2.0), at(e1, axis, 6.0), 18.0, layer, Kind.WEIGHT)
                        out += Shape.Bone(at(e2, axis, 2.0), at(e2, axis, -6.0), 18.0, layer, Kind.WEIGHT)
                    }
                }
            }
            is Prop.Kettlebell -> if (behind == (pr.at == Joint.FAR_WRIST)) {
                val layer = if (behind) Layer.FAR else Layer.PROP_FRONT
                val shift = if (behind) FAR_SHIFT else P(0.0, 0.0)
                val w = s.joint(pr.at)
                val c = if (pr.along) w + (w - elbowOf(pr.at, s)).let { it * (16.0 / hypot(it.x, it.y).coerceAtLeast(1.0)) } else place(pr.at, pr.dx, pr.dy, s)
                out += Shape.Bone(w + shift, c + shift, 5.0, layer, Kind.WEIGHT)
                out += Shape.Dot(c + shift + (c - w) * (6.0 / hypot((c - w).x, (c - w).y).coerceAtLeast(1.0)), 14.0, layer, Kind.WEIGHT)
            }
            is Prop.Ball -> if (!behind) out += Shape.Dot(place(pr.at, pr.dx, pr.dy, s), pr.r, Layer.PROP_FRONT, Kind.WEIGHT)
            is Prop.Wheel -> if (!behind) {
                val c = place(pr.at, pr.dx, pr.dy, s)
                out += Shape.Dot(c, pr.r, Layer.PROP_FRONT, Kind.WEIGHT)
                out += Shape.Dot(c, pr.r * 0.3, Layer.PROP_FRONT, Kind.HOLE)
            }
        }
    }
}

/** How far the far limbs are drawn up and back, so left and right read apart. */
private val FAR_SHIFT = P(-5.0, 3.0)

internal fun rect(x: Double, y: Double, w: Double, h: Double) = listOf(P(x, y), P(x + w, y), P(x + w, y + h), P(x, y + h))

internal fun block(b: Prop.Block): List<P> {
    val o = P(b.x, b.y)
    return listOf(P(0.0, 0.0), P(b.w, 0.0), P(b.w, b.h), P(0.0, b.h)).map { o + rotate(it, b.angle) }
}

// ================================================================================================================ front view
/**
 * A front-view limb, for the figure's right side (on the viewer's left) or left side: by its two segment angles, or reaching for a
 * point ([target], world units, two-joint IK). [sa] and [sb] shorten a segment that points towards the viewer (a fly seen from the
 * front, a thigh when seated).
 */
data class FLimb(val a: Double = -90.0, val b: Double = -90.0, val sa: Double = 1.0, val sb: Double = sa, val target: P? = null, val bend: Int = 1)

data class FrontPose(
    val hip: P = P(0.0, Body.HIP_H),
    /** The trunk's direction from the pelvis to the chest (90 = upright; a side bend or a side plank tilts it). */
    val lean: Double = 90.0,
    val rightLeg: FLimb = FLimb(-93.0, -91.0),
    val leftLeg: FLimb = FLimb(-87.0, -89.0),
    val rightArm: FLimb = FLimb(-100.0, -95.0),
    val leftArm: FLimb = FLimb(-80.0, -85.0),
    /** Shoulders raised (a shrug), in world units. */
    val shrug: Double = 0.0,
    /** The trunk's apparent length (1 = upright; less when bent over towards the viewer). */
    val torsoScale: Double = 1.0,
)

/** Front-view equipment. */
sealed interface FrontProp {
    data class Dumbbells(val right: Boolean = true, val left: Boolean = true) : FrontProp
    data class Kettlebell(val right: Boolean) : FrontProp
    data object BandBetweenHands : FrontProp
    /** One weight held in both hands (a goblet dumbbell, a medicine ball). */
    data class HandsWeight(val ball: Boolean) : FrontProp
    data class Cable(val rightHand: Boolean, val to: P) : FrontProp
    data class Seat(val x: Double, val y: Double, val w: Double, val h: Double) : FrontProp
    data class Rail(val a: P, val b: P) : FrontProp
    /** A landmine seen from the front: the bar from the floor pivot (in front of the feet) up to the hands. */
    data class Landmine(val pivot: P) : FrontProp
}

data class FrontFigure(
    val start: FrontPose,
    val end: FrontPose,
    val props: List<FrontProp> = emptyList(),
    override val timing: Timing = Timing.NORMAL,
) : Figure {
    private fun mixL(a: FLimb, b: FLimb, t: Double) = FLimb(lerp(a.a, b.a, t), lerp(a.b, b.b, t), lerp(a.sa, b.sa, t), lerp(a.sb, b.sb, t),
        if (a.target != null && b.target != null) lerp(a.target, b.target, t) else a.target ?: b.target, a.bend)

    override fun frame(t: Double): Frame {
        val p = FrontPose(lerp(start.hip, end.hip, t), lerp(start.lean, end.lean, t), mixL(start.rightLeg, end.rightLeg, t),
            mixL(start.leftLeg, end.leftLeg, t), mixL(start.rightArm, end.rightArm, t), mixL(start.leftArm, end.leftArm, t),
            lerp(start.shrug, end.shrug, t), lerp(start.torsoScale, end.torsoScale, t))
        val out = ArrayList<Shape>()
        val pelvis = p.hip
        val chest = at(pelvis, p.lean, Body.TORSO * p.torsoScale)
        fun side(c: P, half: Double, sign: Int) = c + rotate(P(sign * half, 0.0), p.lean - 90.0)
        val rHip = side(pelvis, 12.0, -1); val lHip = side(pelvis, 12.0, 1)
        val up = rotate(P(0.0, p.shrug), p.lean - 90.0)
        val rSh = side(chest, 23.0, -1) + up; val lSh = side(chest, 23.0, 1) + up
        // Bent over towards the viewer, the head comes down in front of the shoulders.
        val headUp = (Body.NECK + 3 + Body.HEAD) * (1.6 * p.torsoScale - 0.6)
        val head = at(chest, p.lean, headUp)
        val neckTop = at(chest, p.lean, (headUp - Body.HEAD).coerceAtLeast(0.0))
        fun limb(base: P, l: FLimb, a: Double, b: Double): Pair<P, P> {
            l.target?.let { return reach(base, it, a * l.sa, b * l.sb, l.bend) }
            val m = at(base, l.a, a * l.sa)
            return m to at(m, l.b, b * l.sb)
        }
        val (rk, ra) = limb(rHip, p.rightLeg, Body.THIGH, Body.SHIN)
        val (lk, la) = limb(lHip, p.leftLeg, Body.THIGH, Body.SHIN)
        val (re, rw) = limb(rSh, p.rightArm, Body.UPPER_ARM, Body.FOREARM)
        val (le, lw) = limb(lSh, p.leftArm, Body.UPPER_ARM, Body.FOREARM)
        for (pr in props) when (pr) {
            is FrontProp.Seat -> out += Shape.Box(rect(pr.x, pr.y, pr.w, pr.h))
            is FrontProp.Rail -> out += Shape.Stroke(pr.a, pr.b, Kind.RAIL)
            is FrontProp.Cable -> {
                out += Shape.Stroke(if (pr.rightHand) rw else lw, pr.to, Kind.CABLE)
                out += Shape.Dot(pr.to, 8.0, Layer.PROP_BACK, Kind.PULLEY)
            }
            is FrontProp.BandBetweenHands -> out += Shape.Stroke(rw, lw, Kind.BAND, Layer.PROP_FRONT)
            is FrontProp.Landmine -> {
                val c = lerp(rw, lw, 0.5)
                out += Shape.Stroke(pr.pivot, c, Kind.RAIL)
                out += Shape.Dot(lerp(c, pr.pivot, 0.18), 20.0, Layer.PROP_BACK, Kind.WEIGHT)
            }
            else -> Unit
        }
        out += Shape.Bone(pelvis, chest, 36.0, Layer.BODY)
        out += Shape.Bone(rHip, lHip, 24.0, Layer.BODY)
        out += Shape.Bone(rSh, lSh, 20.0, Layer.BODY)
        out += Shape.Bone(chest, neckTop, 12.0, Layer.BODY)
        out += Shape.Dot(head, Body.HEAD, Layer.BODY)
        out += Shape.Bone(rHip, rk, 20.0, Layer.BODY); out += Shape.Bone(rk, ra, 16.0, Layer.BODY)
        out += Shape.Bone(lHip, lk, 20.0, Layer.BODY); out += Shape.Bone(lk, la, 16.0, Layer.BODY)
        // Feet seen from the front: short capsules turned a little out, along the shin's line.
        fun frontFoot(knee: P, ankle: P, sign: Int) {
            val dir = ankle - knee
            val len = hypot(dir.x, dir.y).coerceAtLeast(1.0)
            val d = dir * (1 / len)
            out += Shape.Bone(ankle, ankle + d * 9.0 + P(sign * 5.0, 0.0), 13.0, Layer.BODY)
        }
        frontFoot(rk, ra, -1); frontFoot(lk, la, 1)
        out += Shape.Bone(rSh, re, 13.0, Layer.NEAR); out += Shape.Bone(re, rw, 11.0, Layer.NEAR)
        out += Shape.Bone(lSh, le, 13.0, Layer.NEAR); out += Shape.Bone(le, lw, 11.0, Layer.NEAR)
        for (pr in props) when (pr) {
            is FrontProp.Dumbbells -> {
                if (pr.right) dumbbellFront(rw, out)
                if (pr.left) dumbbellFront(lw, out)
            }
            is FrontProp.Kettlebell -> {
                val w = if (pr.right) rw else lw
                out += Shape.Bone(w, w + P(0.0, -12.0), 5.0, Layer.PROP_FRONT)
                out += Shape.Dot(w + P(0.0, -18.0), 14.0, Layer.PROP_FRONT, Kind.WEIGHT)
            }
            is FrontProp.HandsWeight -> {
                val c = lerp(rw, lw, 0.5)
                if (pr.ball) out += Shape.Dot(c, 17.0, Layer.PROP_FRONT, Kind.WEIGHT)
                else out += Shape.Poly(rect(c.x - 9, c.y - 20, 18.0, 40.0), Layer.PROP_FRONT)
            }
            else -> Unit
        }
        return Frame(out)
    }

    /** A dumbbell seen from the front: a short handle with a weight at each end. */
    private fun dumbbellFront(w: P, out: MutableList<Shape>) {
        out += Shape.Poly(rect(w.x - 15, w.y - 7, 30.0, 14.0), Layer.PROP_FRONT)
        out += Shape.Poly(rect(w.x - 21, w.y - 11, 9.0, 22.0), Layer.PROP_FRONT)
        out += Shape.Poly(rect(w.x + 12, w.y - 11, 9.0, 22.0), Layer.PROP_FRONT)
    }
}
