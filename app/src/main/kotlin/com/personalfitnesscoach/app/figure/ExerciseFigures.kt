package com.personalfitnesscoach.app.figure

import com.personalfitnesscoach.app.figure.Joint.FAR_WRIST
import com.personalfitnesscoach.app.figure.Joint.HIP
import com.personalfitnesscoach.app.figure.Joint.MID_WRIST
import com.personalfitnesscoach.app.figure.Joint.NEAR_ANKLE
import com.personalfitnesscoach.app.figure.Joint.NEAR_KNEE
import com.personalfitnesscoach.app.figure.Joint.NEAR_WRIST
import com.personalfitnesscoach.app.figure.Joint.SHOULDER

/**
 * One figure for every exercise in the library (CR-004). Exercises that move the same way share a template; the equipment and the side
 * that works change. [of] returns null only for an exercise added later without a figure; the screen then shows none, and a test
 * fails until one is added.
 */
object ExerciseFigures {

    fun of(exerciseId: String): Figure? = all[exerciseId]

    val ids: Set<String> get() = all.keys

    // ------------------------------------------------------------------------------------------------ building blocks
    private fun L(a: Double, b: Double, end: Double = 0.0) = Limb.Angles(a, b, end)
    /** An arm turned with the trunk (hands on a bar on the back or at the chest). */
    private fun R(a: Double, b: Double) = Limb.Angles(a, b, rel = true)
    /** A hand or foot reaching a fixed point. */
    private fun W(x: Double, y: Double, bend: Int, end: Double = 0.0) = Limb.Reach(P(x, y), bend, end)
    /** A hand reaching a point that moves with the shoulder. */
    private fun S(dx: Double, dy: Double, bend: Int) = Limb.Reach(P(dx, dy), bend, from = SHOULDER)

    private val STAND = L(-90.0, -90.0)
    private val HANG = L(-93.0, -91.0)
    private val FORWARD = L(-4.0, -2.0)
    /** Hands on the hips (bodyweight lunges and step-ups). */
    private val ON_HIPS = R(-120.0, -30.0)
    /** Arms folded on the chest. */
    private val FOLDED = R(-55.0, 160.0)

    /** How the weight is held in a squat or a lunge. */
    private enum class Hold { BACK_BAR, FRONT_BAR, GOBLET_DB, GOBLET_KB, RACK_KB, SIDES_DB, SIDES_KB, ARMS_FORWARD, HANDS_ON_HIPS, CABLE_GOBLET, CHEST_FOLDED }

    private fun holdArms(h: Hold, bottom: Boolean): Limb = when (h) {
        Hold.BACK_BAR -> R(-93.5, 102.5)
        Hold.FRONT_BAR -> R(-14.0, 150.0)
        Hold.GOBLET_DB, Hold.GOBLET_KB, Hold.CABLE_GOBLET -> R(-58.0, 104.0)
        Hold.RACK_KB -> R(-48.0, 112.0)
        Hold.SIDES_DB, Hold.SIDES_KB -> HANG
        Hold.ARMS_FORWARD -> if (bottom) FORWARD else HANG
        Hold.HANDS_ON_HIPS -> ON_HIPS
        Hold.CHEST_FOLDED -> FOLDED
    }

    private fun holdProps(h: Hold, cableTo: P = P(110.0, 14.0)): List<Prop> = when (h) {
        Hold.BACK_BAR -> listOf(Prop.Plate(SHOULDER, -10.0, -2.0, r = 28.0, withTorso = true))
        Hold.FRONT_BAR -> listOf(Prop.Plate(SHOULDER, 16.0, 2.0, r = 28.0, withTorso = true))
        Hold.GOBLET_DB -> listOf(Prop.Dumbbell(NEAR_WRIST, 4.0, 4.0, DbView.UPRIGHT))
        Hold.GOBLET_KB -> listOf(Prop.Kettlebell(NEAR_WRIST, 3.0, -10.0))
        Hold.RACK_KB -> listOf(Prop.Kettlebell(NEAR_WRIST, 8.0, -2.0))
        Hold.SIDES_DB -> listOf(Prop.Dumbbell(NEAR_WRIST))
        Hold.SIDES_KB -> listOf(Prop.Kettlebell(NEAR_WRIST))
        Hold.CABLE_GOBLET -> listOf(Prop.Line(MID_WRIST, cableTo), Prop.Pad(NEAR_WRIST, 90.0, 22.0, 8.0))
        else -> emptyList()
    }

    // ------------------------------------------------------------------------------------------------ squat
    private fun squat(
        h: Hold, depthTorso: Double = 56.0, extra: List<Prop> = emptyList(), timing: Timing = Timing.NORMAL,
        thigh: Double = -6.0, shin: Double = -121.0,
    ) = SideFigure(
        start = Pose(torso = 86.0, near = STAND, nearArm = holdArms(h, false)),
        end = Pose(torso = depthTorso, near = L(thigh, shin), nearArm = holdArms(h, true)),
        props = holdProps(h) + extra, timing = timing,
    )

    private fun legPress() = SideFigure(
        anchor = Anchor.HIP,
        start = Pose(torso = 125.0, hip = P(0.0, 70.0), near = W(42.0, 138.0, 1, 112.0), nearArm = L(-80.0, -40.0)),
        end = Pose(torso = 125.0, hip = P(0.0, 70.0), near = W(100.0, 172.0, 1, 112.0), nearArm = L(-80.0, -40.0)),
        props = listOf(
            Prop.Rail(P(40.0, 30.0), P(230.0, 150.0)),
            Prop.Block(-4.0, 57.0, 110.0, 16.0, angle = 125.0),
            Prop.Block(-40.0, 30.0, 52.0, 24.0),
            Prop.Pad(NEAR_ANKLE, 122.0, 74.0, 10.0, dx = 11.0, dy = 7.0),
        ),
    )

    private fun hackSquat() = SideFigure(
        start = Pose(torso = 104.0, near = L(-72.0, -76.0), nearArm = R(-110.0, 95.0)),
        end = Pose(torso = 100.0, near = L(4.0, -112.0), nearArm = R(-110.0, 95.0)),
        anchorAt = P(0.0, 52.0),
        props = listOf(
            Prop.Block(-40.0, 0.0, 120.0, 40.0, angle = 0.0),
            Prop.Rail(P(-150.0, 40.0), P(-40.0, 330.0)),
            Prop.Pad(SHOULDER, 14.0, 24.0, 12.0, dx = 2.0, dy = 10.0),
        ),
    )

    private fun boxSquat() = SideFigure(
        start = Pose(torso = 86.0, near = STAND, nearArm = HANG),
        end = Pose(torso = 60.0, near = L(-2.0, -112.0), nearArm = FORWARD),
        props = listOf(Prop.Block(-96.0, 0.0, 64.0, 62.0)),
    )

    private fun sitToStand() = SideFigure(
        start = Pose(torso = 70.0, near = L(0.0, -96.0), nearArm = FOLDED),
        end = Pose(torso = 88.0, near = STAND, nearArm = FOLDED),
        props = listOf(Prop.Block(-118.0, 0.0, 72.0, 70.0), Prop.Rail(P(-118.0, 70.0), P(-128.0, 170.0))),
        timing = Timing.FAST,
        still = 0.0,
    )

    private fun boxJump() = SideFigure(
        anchor = Anchor.HIP,
        start = Pose(torso = 50.0, hip = P(-30.0, 110.0), near = W(0.0, 12.0, 1), nearArm = L(-130.0, -125.0)),
        mid = Pose(torso = 60.0, hip = P(40.0, 230.0), near = W(55.0, 120.0, 1, 10.0), nearArm = L(60.0, 70.0)),
        end = Pose(torso = 62.0, hip = P(60.0, 170.0), near = W(100.0, 82.0, 1), nearArm = FORWARD),
        props = listOf(Prop.Block(65.0, 0.0, 110.0, 70.0)),
        timing = Timing.FAST,
    )

    // ------------------------------------------------------------------------------------------------ hinge
    private fun deadlift(plate: Boolean = true, trap: Boolean = false, kb: Boolean = false, db: Boolean = false) = SideFigure(
        start = Pose(torso = if (trap) 34.0 else 26.0, near = L(if (trap) -10.0 else -14.0, if (trap) -112.0 else -104.0), nearArm = HANG),
        end = Pose(torso = 90.0, near = STAND, nearArm = HANG),
        props = when {
            kb -> listOf(Prop.Kettlebell(NEAR_WRIST, 2.0, -12.0))
            db -> listOf(Prop.Dumbbell(NEAR_WRIST))
            plate -> listOf(Prop.Plate(NEAR_WRIST, r = 30.0))
            else -> emptyList()
        },
        guide = Guide.Through(HIP, SHOULDER),
        still = 0.0,
    )

    private fun rdl(hold: Prop?) = SideFigure(
        start = Pose(torso = 88.0, near = L(-88.0, -92.0), nearArm = HANG),
        end = Pose(torso = 18.0, near = L(-76.0, -98.0), nearArm = HANG),
        props = listOfNotNull(hold),
        guide = Guide.Through(HIP, SHOULDER),
    )

    private fun singleLegRdl(hold: Prop?) = SideFigure(
        start = Pose(torso = 88.0, near = L(-88.0, -92.0), far = L(-98.0, -104.0, -20.0), nearArm = HANG, farArm = HANG),
        end = Pose(torso = 8.0, near = L(-78.0, -98.0), far = L(-172.0, -172.0, -80.0), nearArm = HANG, farArm = L(-92.0, -92.0)),
        props = listOfNotNull(hold),
        guide = Guide.Through(Joint.FAR_ANKLE, SHOULDER, 1.05),
    )

    private fun hipThrust(load: List<Prop>, machine: Boolean = false) = SideFigure(
        anchor = Anchor.HIP,
        start = Pose(torso = 158.0, neck = -10.0, hip = P(-6.0, 55.0), near = W(64.0, 12.0, 1), nearArm = L(-30.0, -10.0)),
        end = Pose(torso = 182.0, neck = -38.0, hip = P(0.0, 90.0), near = W(64.0, 12.0, 1), nearArm = L(-40.0, -15.0)),
        props = listOf(Prop.Block(-160.0, 0.0, 74.0, 72.0)) + load +
            if (machine) listOf(Prop.Block(-60.0, 0.0, 50.0, 30.0)) else emptyList(),
        guide = Guide.Through(SHOULDER, NEAR_KNEE, 1.0),
    )

    private fun swing(hold: Prop) = SideFigure(
        start = Pose(torso = 32.0, near = L(-72.0, -100.0), nearArm = L(-118.0, -122.0)),
        end = Pose(torso = 92.0, near = STAND, nearArm = L(-4.0, -2.0)),
        props = listOf(hold),
        guide = Guide.Through(HIP, SHOULDER),
        timing = Timing.FAST,
    )

    private fun pullThrough() = SideFigure(
        start = Pose(torso = 26.0, near = L(-76.0, -100.0), nearArm = L(-104.0, -112.0)),
        end = Pose(torso = 90.0, near = STAND, nearArm = L(-80.0, -70.0)),
        props = listOf(Prop.Line(MID_WRIST, P(-150.0, 18.0)), Prop.Rail(P(-160.0, 0.0), P(-160.0, 300.0))),
        guide = Guide.Through(HIP, SHOULDER),
        still = 0.0,
    )

    private fun backExtension() = SideFigure(
        anchor = Anchor.HIP,
        start = Pose(torso = -50.0, hip = P(0.0, 130.0), near = L(214.0, 214.0, 60.0), nearArm = FOLDED),
        end = Pose(torso = 34.0, hip = P(0.0, 130.0), near = L(214.0, 214.0, 60.0), nearArm = FOLDED),
        props = listOf(
            Prop.Block(-20.0, 104.0, 120.0, 18.0, angle = 214.0),
            Prop.Rail(P(-10.0, 0.0), P(-30.0, 100.0)),
            Prop.Pad(NEAR_ANKLE, 34.0, 26.0, 12.0, dx = 6.0, dy = 10.0),
        ),
        guide = Guide.Through(NEAR_ANKLE, SHOULDER, 1.05),
    )

    /**
     * Slider hamstring curl: shoulders on the floor, hips held up the whole time (the library's listed mistake is letting them drop,
     * review R7-06); the heels slide out to nearly straight legs and back in under the knees.
     */
    private fun sliderCurl() = SideFigure(
        anchor = Anchor.HIP,
        start = Pose(torso = 189.5, neck = -10.0, hip = P(-6.0, 36.0), near = W(126.0, 14.0, 1, 60.0), nearArm = W(10.0, 9.0, -1)),
        end = Pose(torso = 215.0, neck = -20.0, hip = P(-19.6, 71.3), near = W(60.0, 14.0, 1, 50.0), nearArm = W(10.0, 9.0, -1)),
        props = listOf(Prop.Pad(NEAR_ANKLE, 0.0, 26.0, 6.0, dx = -2.0, dy = -12.0)),
        guide = Guide.Through(SHOULDER, HIP, 1.0),
    )

    private fun gluteBridge(singleLeg: Boolean = false) = SideFigure(
        anchor = Anchor.HIP,
        start = Pose(
            torso = 180.0, neck = -10.0, hip = P(-4.0, 22.0), near = W(60.0, 14.0, 1),
            far = if (singleLeg) L(28.0, 28.0, 50.0) else W(60.0, 14.0, 1),
            nearArm = W(10.0, 9.0, -1),
        ),
        end = Pose(
            torso = 215.0, neck = -20.0, hip = P(-19.6, 71.3), near = W(60.0, 14.0, 1),
            far = if (singleLeg) L(35.0, 35.0, 50.0) else W(60.0, 14.0, 1),
            nearArm = W(10.0, 9.0, -1),
        ),
        guide = if (singleLeg) null else Guide.Through(SHOULDER, NEAR_KNEE, 1.0),
    )

    private fun scoopToss() = SideFigure(
        start = Pose(torso = 30.0, near = L(-60.0, -110.0), nearArm = L(-100.0, -105.0)),
        end = Pose(torso = 98.0, near = L(-90.0, -92.0), nearArm = L(80.0, 86.0)),
        props = listOf(Prop.Ball(MID_WRIST, 6.0, 0.0)),
        timing = Timing.FAST,
    )

    // ------------------------------------------------------------------------------------------------ lunge
    private fun splitSquat(h: Hold, rear: Double = 25.0, rearX: Double = -130.0, rearFoot: Double = -50.0, extra: List<Prop> = emptyList()) =
        SideFigure(
            start = Pose(torso = 88.0, near = L(-62.0, -78.0), far = W(rearX, rear, 1, rearFoot), nearArm = holdArms(h, false)),
            end = Pose(torso = 86.0, near = L(-5.0, -100.0), far = W(rearX, rear, 1, rearFoot), nearArm = holdArms(h, true)),
            props = holdProps(h, P(140.0, 14.0)) + extra,
            guide = Guide.Through(HIP, SHOULDER, 1.0),
        )

    private fun reverseLunge(h: Hold) = SideFigure(
        start = Pose(torso = 89.0, near = STAND, far = W(-4.0, 13.0, 1), nearArm = holdArms(h, false)),
        mid = Pose(torso = 88.0, near = L(-40.0, -96.0), far = W(-70.0, 46.0, 1, -20.0), nearArm = holdArms(h, false)),
        end = Pose(torso = 86.0, near = L(-5.0, -100.0), far = W(-130.0, 25.0, 1, -50.0), nearArm = holdArms(h, true)),
        props = holdProps(h),
    )

    private fun walkingLunge() = SideFigure(
        anchor = Anchor.HIP,
        start = Pose(torso = 89.0, hip = P(0.0, Body.HIP_H), near = W(4.0, 12.0, 1), far = W(-4.0, 13.0, 1), nearArm = HANG, farArm = HANG),
        mid = Pose(torso = 88.0, hip = P(30.0, 138.0), near = W(70.0, 44.0, 1, 10.0), far = W(-4.0, 18.0, 1, -20.0), nearArm = HANG, farArm = HANG),
        end = Pose(torso = 86.0, hip = P(60.0, 92.0), near = W(130.0, 12.0, 1), far = W(-4.0, 25.0, 1, -50.0), nearArm = HANG, farArm = HANG),
        props = listOf(Prop.Dumbbell(NEAR_WRIST)),
    )

    private fun stepUp(h: Hold) = SideFigure(
        start = Pose(torso = 70.0, near = L(-18.0, -104.0), far = W(-60.0, 20.0, 1, -30.0), nearArm = holdArms(h, false)),
        end = Pose(torso = 88.0, near = STAND, far = L(-8.0, -96.0, -10.0), nearArm = holdArms(h, false)),
        anchorAt = P(0.0, 72.0),
        props = listOf(Prop.Block(-36.0, 0.0, 110.0, 60.0)) + holdProps(h),
    )

    private fun lateralLunge() = FrontFigure(
        start = FrontPose(hip = P(0.0, 146.0), rightLeg = FLimb(target = P(-72.0, 14.0), bend = -1), leftLeg = FLimb(target = P(72.0, 14.0)),
            rightArm = FLimb(target = P(-2.0, 196.0), bend = 1, sa = 0.7, sb = 0.6), leftArm = FLimb(target = P(2.0, 196.0), bend = -1, sa = 0.7, sb = 0.6)),
        end = FrontPose(hip = P(-36.0, 112.0), lean = 94.0, rightLeg = FLimb(target = P(-72.0, 14.0), bend = -1), leftLeg = FLimb(target = P(72.0, 14.0)),
            rightArm = FLimb(target = P(-40.0, 162.0), bend = 1, sa = 0.7, sb = 0.6), leftArm = FLimb(target = P(-36.0, 162.0), bend = -1, sa = 0.7, sb = 0.6)),
    )

    // ------------------------------------------------------------------------------------------------ horizontal push
    /** Lying on a flat bench (head to the left) or tilted ([incline] degrees); hands press from the chest to above the shoulders. */
    private fun benchPress(incline: Double = 0.0, load: (Boolean) -> List<Prop>, floor: Boolean = false, oneArm: Boolean = false): SideFigure {
        val torso = 180.0 - incline
        val hipY = if (floor) 16.0 else 60.0
        val sh = at(P(0.0, hipY), torso, Body.TORSO)
        // The bar over the shoulders at the top; at the bottom the forearms stand upright and the bar touches the chest.
        val top = L(92.0 - incline * 0.2, 92.0 - incline * 0.2)
        val bottom = if (floor) L(-4.0, 92.0) else L(-20.0 - incline * 0.6, 114.0 - incline * 0.06)
        val bench = when {
            floor -> emptyList()
            incline == 0.0 -> listOf(Prop.Block(-120.0, 0.0, 150.0, 45.0))
            else -> listOf(Prop.Block(-30.0, 0.0, 60.0, 45.0), Prop.Block(-8.0, 30.0, 120.0, 16.0, angle = torso))
        }
        val rest = L(-6.0, -2.0)
        return SideFigure(
            anchor = Anchor.HIP,
            start = Pose(torso = torso, neck = -4.0, hip = P(0.0, hipY), near = W(if (floor) 64.0 else 70.0, 14.0, 1), nearArm = top,
                farArm = if (oneArm) rest else top),
            end = Pose(torso = torso, neck = -4.0, hip = P(0.0, hipY), near = W(if (floor) 64.0 else 70.0, 14.0, 1), nearArm = bottom,
                farArm = if (oneArm) rest else bottom),
            props = bench + load(true),
        )
    }

    private fun pushUp(
        handsY: Double = 8.0, feetY: Double = 29.0, feetX: Double = -150.0, topAngle: Double? = null, bottomAngle: Double? = null,
        extra: List<Prop> = emptyList(), handsX: Double? = null,
    ): SideFigure {
        // A straight body from the toes; the hands under the shoulders at the top.
        val reach = Body.THIGH + Body.SHIN + Body.TORSO
        val top = topAngle ?: Math.toDegrees(kotlin.math.asin(((handsY + 98.0) - feetY) / reach))
        val bottom = bottomAngle ?: Math.toDegrees(kotlin.math.asin(((handsY + 38.0) - feetY) / reach))
        val hx = handsX ?: (feetX + reach * (kotlin.math.cos(Math.toRadians(top)) + kotlin.math.cos(Math.toRadians(bottom))) / 2)
        fun body(a: Double) = L(180.0 + a, 180.0 + a, -62.0 + a)
        return SideFigure(
            start = Pose(torso = top, near = body(top), nearArm = W(hx, handsY, -1)),
            end = Pose(torso = bottom, near = body(bottom), nearArm = W(hx, handsY, -1)),
            anchorAt = P(feetX, feetY),
            props = extra,
            guide = Guide.Through(NEAR_ANKLE, SHOULDER, 1.0),
        )
    }

    private fun dip(assisted: Boolean = false, weighted: Boolean = false) = SideFigure(
        anchor = Anchor.HIP,
        start = Pose(torso = 82.0, hip = P(-17.0, 205.0), near = L(-96.0, if (assisted) 186.0 else -150.0), nearArm = W(0.0, 195.0, -1)),
        end = Pose(torso = 66.0, hip = P(-50.0, 148.0), near = L(-100.0, if (assisted) 186.0 else -154.0), nearArm = W(0.0, 195.0, -1)),
        props = listOf(Prop.Bar(P(0.0, 195.0)), Prop.Rail(P(8.0, 190.0), P(8.0, 0.0))) +
            (if (assisted) listOf(Prop.Pad(NEAR_KNEE, 0.0, 70.0, 12.0, dx = -30.0, dy = -12.0), Prop.Rail(P(-60.0, 0.0), P(-60.0, 80.0))) else emptyList()) +
            (if (weighted) listOf(Prop.Plate(HIP, 12.0, -46.0, r = 20.0)) else emptyList()),
    )

    private fun cablePress() = SideFigure(
        start = Pose(torso = 80.0, near = L(-62.0, -82.0), far = W(-90.0, 24.0, 1, -40.0), nearArm = S(20.0, -10.0, -1)),
        end = Pose(torso = 78.0, near = L(-62.0, -82.0), far = W(-90.0, 24.0, 1, -40.0), nearArm = S(100.0, 4.0, -1)),
        props = listOf(Prop.Line(NEAR_WRIST, P(-120.0, 215.0)), Prop.Rail(P(-130.0, 0.0), P(-130.0, 300.0))),
    )

    // ------------------------------------------------------------------------------------------------ seated machines
    /** Seated upright, hips at the seat: thighs level, feet on the floor. */
    private fun seated(torso: Double, arm: Limb, legs: Limb = L(0.0, -90.0)) = Pose(torso = torso, hip = P(0.0, 86.0), near = legs, nearArm = arm)

    private val SEAT = listOf(Prop.Block(-44.0, 46.0, 84.0, 24.0), Prop.Rail(P(-6.0, 46.0), P(-6.0, 0.0)))
    private fun backrest(angle: Double = 98.0) = Prop.Block(-34.0, 70.0, 120.0, 14.0, angle = angle)

    private fun machinePress(up: Boolean) = SideFigure(
        anchor = Anchor.HIP,
        start = seated(96.0, if (up) S(20.0, 10.0, -1) else S(22.0, -16.0, -1)),
        end = seated(96.0, if (up) S(10.0, 100.0, -1) else S(98.0, -16.0, -1)),
        props = SEAT + backrest() + listOf(Prop.Pad(NEAR_WRIST, 90.0, 26.0, 9.0), Prop.Rail(P(150.0, 0.0), P(150.0, if (up) 340.0 else 240.0))),
    )

    private fun seatedPress(hold: List<Prop>, bar: Boolean = false) = SideFigure(
        anchor = Anchor.HIP,
        start = seated(94.0, S(14.0, 8.0, -1)),
        end = seated(94.0, S(6.0, 100.0, -1)),
        props = SEAT + backrest(94.0) + hold + if (bar) listOf(Prop.Rail(P(10.0, 0.0), P(10.0, 350.0))) else emptyList(),
    )

    // ------------------------------------------------------------------------------------------------ vertical push
    private fun overheadPress(hold: List<Prop>, oneArm: Boolean = false, dip: Boolean = false) = SideFigure(
        start = Pose(torso = 92.0, near = if (dip) L(-74.0, -106.0) else STAND, nearArm = S(16.0, 4.0, -1), farArm = if (oneArm) ON_HIPS else S(16.0, 4.0, -1)),
        end = Pose(torso = 90.0, near = STAND, nearArm = S(4.0, 100.0, -1), farArm = if (oneArm) ON_HIPS else S(4.0, 100.0, -1)),
        props = hold,
        timing = if (dip) Timing.FAST else Timing.NORMAL,
    )

    private fun landminePress(push: Boolean) = SideFigure(
        start = Pose(torso = 84.0, near = if (push) L(-60.0, -104.0) else L(-66.0, -84.0), far = W(-80.0, 26.0, 1, -40.0), nearArm = S(18.0, -4.0, -1), farArm = ON_HIPS),
        end = Pose(torso = 80.0, near = L(-66.0, -84.0), far = W(-80.0, 26.0, 1, -40.0), nearArm = S(72.0, 70.0, -1), farArm = ON_HIPS),
        props = listOf(Prop.Landmine(P(250.0, 4.0), NEAR_WRIST)),
        timing = if (push) Timing.FAST else Timing.NORMAL,
    )

    /** Half-kneeling: the near (front) foot flat, the far knee on the floor. */
    private fun halfKneel(torso: Double, arm: Limb, farArm: Limb = ON_HIPS) =
        Pose(torso = torso, near = L(0.0, -90.0), far = L(-90.0, 180.0, 180.0), nearArm = arm, farArm = farArm)

    private fun halfKneelingPress() = SideFigure(
        start = halfKneel(90.0, S(14.0, 6.0, -1)),
        end = halfKneel(90.0, S(4.0, 100.0, -1)),
        anchorAt = P(60.0, 12.0),
        props = listOf(Prop.Dumbbell(NEAR_WRIST)),
    )

    private fun pikePushUp(elevated: Boolean) = if (elevated) SideFigure(
        start = Pose(torso = -84.0, near = L(226.0, 226.0, -40.0), nearArm = W(74.0, 8.0, -1)),
        end = Pose(torso = -78.0, near = L(214.0, 214.0, -40.0), nearArm = W(74.0, 8.0, -1)),
        anchorAt = P(-40.0, 86.0),
        props = listOf(Prop.Block(-90.0, 0.0, 80.0, 60.0)),
    ) else SideFigure(
        start = Pose(torso = -44.0, near = L(-118.0, -118.0, -20.0), nearArm = W(70.0, 8.0, -1)),
        end = Pose(torso = -62.0, near = L(-112.0, -112.0, -20.0), nearArm = W(70.0, 8.0, -1)),
        anchorAt = P(-80.0, 21.0),
    )

    // ------------------------------------------------------------------------------------------------ horizontal pull
    private fun bentRow(hold: List<Prop>, torso: Double = 24.0, landmine: Boolean = false) = SideFigure(
        start = Pose(torso = torso, near = L(-70.0, -100.0), nearArm = S(0.0, -100.0, 1)),
        end = Pose(torso = torso, near = L(-70.0, -100.0), nearArm = S(-34.0, -46.0, 1)),
        props = hold + if (landmine) listOf(Prop.Landmine(P(-260.0, 4.0), NEAR_WRIST, beyond = true)) else emptyList(),
        guide = Guide.Through(HIP, SHOULDER),
    )

    private fun benchRow() = SideFigure(
        start = Pose(torso = 8.0, near = L(-82.0, -94.0), far = L(-92.0, 180.0, 180.0), nearArm = S(0.0, -100.0, 1), farArm = L(-90.0, -90.0)),
        end = Pose(torso = 8.0, near = L(-82.0, -94.0), far = L(-92.0, 180.0, 180.0), nearArm = S(-36.0, -40.0, 1), farArm = L(-90.0, -90.0)),
        props = listOf(Prop.Block(-120.0, 0.0, 190.0, 74.0), Prop.Dumbbell(NEAR_WRIST)),
        guide = Guide.Through(HIP, SHOULDER),
    )

    private fun kbRow() = SideFigure(
        start = Pose(torso = 30.0, near = L(-100.0, -100.0, 0.0), far = L(-44.0, -96.0), nearArm = S(0.0, -100.0, 1), farArm = W(42.0, 112.0, 1)),
        end = Pose(torso = 30.0, near = L(-100.0, -100.0, 0.0), far = L(-44.0, -96.0), nearArm = S(-34.0, -42.0, 1), farArm = W(42.0, 112.0, 1)),
        anchorAt = P(-40.0, 12.0),
        props = listOf(Prop.Kettlebell(NEAR_WRIST)),
        guide = Guide.Through(HIP, SHOULDER),
    )

    private fun chestSupportedRow() = SideFigure(
        anchor = Anchor.HIP,
        start = Pose(torso = 40.0, hip = P(0.0, 100.0), near = W(-118.0, 27.0, 1, -40.0), nearArm = S(0.0, -100.0, 1)),
        end = Pose(torso = 40.0, hip = P(0.0, 100.0), near = W(-118.0, 27.0, 1, -40.0), nearArm = S(-38.0, -36.0, 1)),
        props = listOf(Prop.Block(4.6, 63.4, 116.0, 16.0, angle = 40.0), Prop.Rail(P(30.0, 80.0), P(30.0, 0.0)), Prop.Dumbbell(NEAR_WRIST)),
    )

    private fun seatedCableRow(machine: Boolean = false) = SideFigure(
        anchor = Anchor.HIP,
        start = Pose(torso = if (machine) 84.0 else 72.0, hip = P(0.0, if (machine) 86.0 else 50.0),
            near = if (machine) L(0.0, -90.0) else W(128.0, 44.0, 1, 70.0), nearArm = W(if (machine) 108.0 else 112.0, if (machine) 152.0 else 104.0, -1)),
        end = Pose(torso = if (machine) 96.0 else 92.0, hip = P(0.0, if (machine) 86.0 else 50.0),
            near = if (machine) L(0.0, -90.0) else W(128.0, 44.0, 1, 70.0), nearArm = S(26.0, -48.0, -1)),
        props = (if (machine) SEAT + listOf(Prop.Block(70.0, 150.0, 20.0, 60.0), Prop.Rail(P(150.0, 0.0), P(150.0, 250.0)))
        else listOf(Prop.Block(-50.0, 0.0, 90.0, 35.0), Prop.Rail(P(140.0, 10.0), P(150.0, 90.0)), Prop.Line(NEAR_WRIST, P(165.0, 60.0)))) +
            Prop.Pad(NEAR_WRIST, 90.0, 22.0, 8.0),
    )

    private fun standingRow(band: Boolean, oneArm: Boolean) = SideFigure(
        start = Pose(torso = 82.0, near = L(-70.0, -84.0), far = W(-70.0, 22.0, 1, -40.0), nearArm = S(96.0, -30.0, -1), farArm = if (oneArm) HANG else S(96.0, -30.0, -1)),
        end = Pose(torso = 86.0, near = L(-70.0, -84.0), far = W(-70.0, 22.0, 1, -40.0), nearArm = S(22.0, -50.0, -1), farArm = if (oneArm) HANG else S(22.0, -50.0, -1)),
        props = listOf(Prop.Line(NEAR_WRIST, P(220.0, 190.0), band = band)) + if (band) emptyList() else listOf(Prop.Rail(P(230.0, 0.0), P(230.0, 300.0))),
    )

    private fun facePull() = SideFigure(
        start = Pose(torso = 86.0, near = L(-70.0, -84.0), far = W(-70.0, 22.0, 1, -40.0), nearArm = S(96.0, 24.0, 1)),
        end = Pose(torso = 88.0, near = L(-70.0, -84.0), far = W(-70.0, 22.0, 1, -40.0), nearArm = S(8.0, 28.0, 1)),
        props = listOf(Prop.Line(NEAR_WRIST, P(220.0, 262.0)), Prop.Rail(P(230.0, 0.0), P(230.0, 320.0))),
    )

    private fun invertedRow() = pullBody(barX = 70.0, barY = 140.0)

    private fun pullBody(barX: Double, barY: Double): SideFigure {
        val reach = Body.THIGH + Body.SHIN + Body.TORSO
        val feet = P(-150.0, 14.0)
        val top = Math.toDegrees(kotlin.math.asin((barY - 26.0 - feet.y) / reach))
        val bottom = Math.toDegrees(kotlin.math.asin((barY - 98.0 - feet.y) / reach))
        fun body(a: Double) = L(180.0 + a, 180.0 + a, 90.0)
        return SideFigure(
            start = Pose(torso = bottom, near = body(bottom), nearArm = W(barX, barY, 1)),
            end = Pose(torso = top, near = body(top), nearArm = W(barX, barY, 1)),
            anchorAt = feet,
            props = listOf(Prop.Bar(P(barX, barY)), Prop.Rail(P(barX + 10.0, barY), P(barX + 10.0, 0.0))),
            guide = Guide.Through(NEAR_ANKLE, SHOULDER, 1.0),
        )
    }

    // ------------------------------------------------------------------------------------------------ vertical pull
    private fun pulldown(band: Boolean = false) = SideFigure(
        anchor = Anchor.HIP,
        start = seated(100.0, W(8.0, 272.0, -1)),
        end = seated(104.0, S(26.0, 6.0, -1)),
        props = (if (band) listOf(Prop.Block(-44.0, 0.0, 84.0, 70.0)) else SEAT + listOf(Prop.Pad(NEAR_KNEE, 0.0, 30.0, 12.0, dy = 18.0), Prop.Rail(P(70.0, 0.0), P(70.0, 330.0)))) +
            listOf(Prop.Line(NEAR_WRIST, P(20.0, 330.0), band = band), Prop.Pad(NEAR_WRIST, 0.0, 34.0, 7.0)),
    )

    private fun halfKneelingPulldown() = SideFigure(
        start = halfKneel(86.0, S(70.0, 72.0, -1)),
        end = halfKneel(90.0, S(18.0, -10.0, -1)),
        anchorAt = P(60.0, 12.0),
        props = listOf(Prop.Line(NEAR_WRIST, P(160.0, 320.0)), Prop.Rail(P(175.0, 0.0), P(175.0, 330.0))),
    )

    private fun pullUp(extra: List<Prop> = emptyList(), negative: Boolean = false, knees: Boolean = false): SideFigure {
        val bottom = Pose(torso = 96.0, hip = P(-14.0, 178.0), near = L(-96.0, if (knees) 186.0 else -140.0), nearArm = W(0.0, 362.0, -1))
        val top = Pose(torso = 96.0, hip = P(-12.0, 252.0), near = L(-94.0, if (knees) 186.0 else -140.0), nearArm = W(0.0, 362.0, -1))
        return SideFigure(
            anchor = Anchor.HIP,
            start = if (negative) top else bottom,
            end = if (negative) bottom else top,
            props = listOf(Prop.Bar(P(0.0, 362.0)), Prop.Rail(P(-60.0, 362.0), P(60.0, 362.0))) + extra,
            timing = if (negative) Timing(0.12, 0.6, 0.06, 0.22, 5.0) else Timing.NORMAL,
        )
    }

    // ------------------------------------------------------------------------------------------------ carries
    private fun carry(near: List<Prop>, farArm: Limb = HANG, nearArm: Limb = HANG) = SideFigure(
        anchor = Anchor.HIP,
        start = Pose(torso = 90.0, hip = P(0.0, 157.0), near = L(-74.0, -92.0), far = L(-106.0, -116.0, -22.0), nearArm = nearArm, farArm = farArm),
        mid = Pose(torso = 90.0, hip = P(0.0, 160.0), near = L(-89.0, -91.0), far = L(-91.0, -93.0), nearArm = nearArm, farArm = farArm),
        end = Pose(torso = 90.0, hip = P(0.0, 157.0), near = L(-106.0, -116.0, -22.0), far = L(-74.0, -92.0), nearArm = nearArm, farArm = farArm),
        props = near,
        timing = Timing.STEP,
    )

    // ------------------------------------------------------------------------------------------------ rotation (chops in side view)
    private fun chop(hold: List<Prop>, kneel: Boolean, lift: Boolean = false, cable: P? = null): SideFigure {
        val high = S(-30.0, 90.0, -1)
        val low = S(76.0, -66.0, -1)
        val fromP = if (lift) S(-40.0, -90.0, -1) else high
        val toP = if (lift) S(66.0, 74.0, -1) else low
        fun pose(arm: Limb, torso: Double) = if (kneel) halfKneel(torso, arm, arm) else Pose(torso = torso, near = L(-80.0, -96.0), far = L(-104.0, -88.0), nearArm = arm, farArm = arm)
        return SideFigure(
            start = pose(fromP, if (lift) 70.0 else 94.0),
            end = pose(toP, if (lift) 94.0 else 70.0),
            anchorAt = if (kneel) P(60.0, 12.0) else P(0.0, 12.0),
            props = hold + (cable?.let { listOf(Prop.Line(MID_WRIST, it)) } ?: emptyList()),
        )
    }

    private fun landmineRotation() = FrontFigure(
        start = FrontPose(lean = 95.0, rightLeg = FLimb(target = P(-34.0, 12.0), bend = -1), leftLeg = FLimb(target = P(34.0, 12.0)),
            rightArm = FLimb(target = P(-74.0, 236.0), bend = -1, sa = 0.7, sb = 0.7), leftArm = FLimb(target = P(-66.0, 232.0), bend = -1, sa = 0.7, sb = 0.7)),
        end = FrontPose(lean = 85.0, rightLeg = FLimb(target = P(-34.0, 12.0), bend = -1), leftLeg = FLimb(target = P(34.0, 12.0)),
            rightArm = FLimb(target = P(66.0, 232.0), sa = 0.7, sb = 0.7), leftArm = FLimb(target = P(74.0, 236.0), sa = 0.7, sb = 0.7)),
        props = listOf(FrontProp.Landmine(P(0.0, 2.0))),
    )

    private fun rotationalThrow() = FrontFigure(
        start = FrontPose(lean = 96.0, hip = P(0.0, 150.0), rightLeg = FLimb(target = P(-40.0, 12.0), bend = -1), leftLeg = FLimb(target = P(40.0, 12.0)),
            rightArm = FLimb(target = P(-62.0, 168.0), bend = -1), leftArm = FLimb(target = P(-40.0, 166.0), bend = -1)),
        end = FrontPose(lean = 84.0, hip = P(0.0, 154.0), rightLeg = FLimb(target = P(-40.0, 12.0), bend = -1), leftLeg = FLimb(target = P(40.0, 12.0)),
            rightArm = FLimb(target = P(70.0, 226.0)), leftArm = FLimb(target = P(92.0, 228.0))),
        props = listOf(FrontProp.HandsWeight(ball = true)),
        timing = Timing.FAST,
    )

    // ------------------------------------------------------------------------------------------------ core
    private fun deadBug(hold: Boolean) = SideFigure(
        anchor = Anchor.HIP,
        start = Pose(torso = 180.0, neck = -6.0, hip = P(0.0, 18.0), near = L(90.0, 0.0, 90.0), far = L(90.0, 0.0, 90.0), nearArm = L(88.0, 88.0), farArm = L(92.0, 92.0)),
        end = Pose(torso = 180.0, neck = -6.0, hip = P(0.0, 18.0), near = L(90.0, 0.0, 90.0), far = L(8.0, 6.0, 40.0),
            nearArm = if (hold) L(88.0, 88.0) else L(160.0, 166.0), farArm = L(92.0, 92.0)),
        props = if (hold) listOf(Prop.Dumbbell(MID_WRIST, view = DbView.ACROSS)) else emptyList(),
    )

    private fun plank(long: Boolean = false) = SideFigure(
        start = Pose(torso = if (long) 6.4 else 8.4, near = L(if (long) 186.4 else 188.4, if (long) 186.4 else 188.4, -52.0), nearArm = L(if (long) -62.0 else -90.0, 0.0)),
        end = Pose(torso = if (long) 7.2 else 9.2, near = L(if (long) 187.2 else 189.2, if (long) 187.2 else 189.2, -52.0), nearArm = L(if (long) -62.0 else -90.0, 0.0)),
        anchorAt = P(-150.0, 29.0),
        guide = Guide.Through(NEAR_ANKLE, SHOULDER, 1.0),
        timing = Timing.HOLD,
    )

    private fun bodySaw() = SideFigure(
        anchor = Anchor.HIP,
        start = Pose(torso = 10.0, hip = P(0.0, 52.0), near = L(190.0, 190.0, -52.0), nearArm = L(-104.0, 0.0)),
        end = Pose(torso = 8.0, hip = P(-40.0, 50.0), near = L(188.0, 188.0, -52.0), nearArm = L(-62.0, 0.0)),
        props = listOf(Prop.Pad(NEAR_ANKLE, 0.0, 24.0, 6.0, dx = 4.0, dy = -20.0)),
        guide = Guide.Through(NEAR_ANKLE, SHOULDER, 1.0),
    )

    private fun rollout(standing: Boolean = false, bar: Boolean = false): SideFigure {
        val r = if (bar) 28.0 else 15.0
        return if (standing) SideFigure(
            start = Pose(torso = -78.0, near = L(-96.0, -92.0, -20.0), nearArm = W(64.0, r, 1)),
            end = Pose(torso = 12.0, near = L(-170.0, -170.0, -50.0), nearArm = W(316.0, r, 1)),
            anchorAt = P(0.0, 26.0),
            props = listOf(Prop.Wheel(NEAR_WRIST, r)),
            guide = Guide.Through(NEAR_ANKLE, SHOULDER, 1.0),
        ) else SideFigure(
            anchor = Anchor.NEAR_KNEE,
            anchorAt = P(0.0, 10.0),
            start = Pose(torso = if (bar) 33.0 else 22.0, near = L(-92.0, 180.0, 180.0), nearArm = W(if (bar) 74.0 else 82.0, r, 1)),
            end = Pose(torso = 10.0, near = L(-150.0, 180.0, 180.0), nearArm = W(240.0, r, 1)),
            props = listOf(if (bar) Prop.Plate(NEAR_WRIST, r = r) else Prop.Wheel(NEAR_WRIST, r)),
            guide = Guide.Through(NEAR_KNEE, SHOULDER, 1.0),
        )
    }

    private fun bear(crawl: Boolean): SideFigure {
        val hold = Pose(torso = 6.0, hip = P(0.0, 96.0), near = W(-70.0, 28.0, 1, -50.0), far = W(-70.0, 28.0, 1, -50.0), nearArm = W(92.0, 8.0, 1), farArm = W(92.0, 8.0, 1))
        return if (!crawl) SideFigure(
            anchor = Anchor.HIP, start = hold, end = hold.copy(hip = P(0.0, 99.0), torso = 5.0), timing = Timing.HOLD,
            guide = Guide.Through(HIP, SHOULDER),
        ) else SideFigure(
            anchor = Anchor.HIP,
            start = hold.copy(far = W(-90.0, 28.0, 1, -50.0), nearArm = W(72.0, 8.0, 1)),
            mid = hold.copy(hip = P(8.0, 98.0), far = W(-70.0, 46.0, 1, -40.0), nearArm = W(92.0, 30.0, 1)),
            end = hold.copy(hip = P(14.0, 96.0), far = W(-50.0, 28.0, 1, -50.0), nearArm = W(112.0, 8.0, 1)),
            timing = Timing.STEP,
        )
    }

    private fun braceBreathing() = SideFigure(
        anchor = Anchor.HIP,
        start = Pose(torso = 180.0, neck = -6.0, hip = P(0.0, 18.0), near = W(64.0, 14.0, 1), nearArm = L(-6.0, 34.0)),
        end = Pose(torso = 180.0, neck = -6.0, hip = P(0.0, 18.0), near = W(64.0, 14.0, 1), nearArm = L(-4.0, 40.0)),
        timing = Timing.HOLD,
    )

    private fun birdDog() = SideFigure(
        anchor = Anchor.NEAR_KNEE,
        anchorAt = P(0.0, 10.0),
        start = Pose(torso = 2.0, near = L(-90.0, 180.0, 180.0), far = L(-90.0, 180.0, 180.0), nearArm = W(90.0, 8.0, -1), farArm = W(90.0, 8.0, -1)),
        end = Pose(torso = 2.0, near = L(-90.0, 180.0, 180.0), far = L(-180.0, 180.0, 90.0), nearArm = L(4.0, 4.0), farArm = W(90.0, 8.0, -1)),
        guide = Guide.Through(HIP, SHOULDER, 1.3),
    )

    private fun highPlank(tap: Boolean, row: Boolean): SideFigure {
        // A straight body from the toes, at the angle that puts the shoulders straight-armed over the hands (on dumbbells: higher).
        val handsY = if (row) 20.0 else 8.0
        val a = Math.toDegrees(kotlin.math.asin((handsY + 99.0 - 24.0) / (Body.THIGH + Body.SHIN + Body.TORSO)))
        val body = L(180.0 + a, 180.0 + a, -62.0 + a)
        val hands = W(-150.0 + (Body.THIGH + Body.SHIN + Body.TORSO) * kotlin.math.cos(Math.toRadians(a)) - 3.0, handsY, -1)
        return SideFigure(
            // The rowing arm keeps one bend from start to end (it is straight at the start, so the bend only shows as it rows).
            start = Pose(torso = a, near = body, nearArm = if (row) hands.copy(bend = 1) else hands, farArm = hands),
            end = Pose(torso = a, near = body, nearArm = if (tap) S(-6.0, -14.0, -1) else S(-30.0, -44.0, 1), farArm = hands),
            anchorAt = P(-150.0, 24.0),
            props = if (row) listOf(Prop.Dumbbell(NEAR_WRIST, dy = -8.0), Prop.Dumbbell(FAR_WRIST, dy = -8.0)) else emptyList(),
            guide = Guide.Through(NEAR_ANKLE, SHOULDER, 1.0),
        )
    }

    private fun pallof(kneel: Boolean, walk: Boolean = false, band: Boolean = false): FrontFigure {
        val hipY = if (kneel) 90.0 else Body.HIP_H
        val chest = hipY + 64.0
        fun legs(dx: Double) = if (kneel) Pair(FLimb(-92.0, -90.0, 1.0, 0.08), FLimb(-40.0, -90.0, 0.2, 0.74))
        else Pair(FLimb(target = P(dx - 26.0, 12.0), bend = -1), FLimb(target = P(dx + 26.0, 12.0)))
        fun pose(dx: Double, out: Boolean): FrontPose {
            val (r, l) = legs(dx)
            val hand = P(dx, chest - 8.0)
            return FrontPose(hip = P(dx, hipY - if (kneel) 0.0 else 3.0), rightLeg = r, leftLeg = l,
                rightArm = FLimb(target = hand, bend = -1, sa = if (out) 0.55 else 1.0), leftArm = FLimb(target = hand, sa = if (out) 0.55 else 1.0))
        }
        return FrontFigure(
            start = pose(0.0, false),
            end = pose(if (walk) 40.0 else 0.0, !walk),
            props = listOf(FrontProp.Cable(rightHand = true, to = P(-170.0, chest - 8.0))) +
                (if (band) emptyList() else listOf(FrontProp.Rail(P(-180.0, 0.0), P(-180.0, 300.0)))),
        )
    }

    /** Lying on the side, seen from the front: a straight line from the feet (or knees) to the head, on one forearm. */
    private fun sidePlank(kneeling: Boolean = false, reach: Boolean = false): FrontFigure {
        fun pose(lift: Double, reachIn: Boolean): FrontPose {
            val lean = (if (kneeling) 22.3 else 15.4) + lift
            val hip = P(0.0, (if (kneeling) 50.7 else 61.4) + lift * 1.5)
            val legs = if (kneeling) FLimb(180.0 + lean, 180.0 + lean, 1.0, 0.05) else FLimb(180.0 + lean, 180.0 + lean)
            return FrontPose(hip = hip, lean = lean, rightLeg = legs, leftLeg = legs,
                leftArm = FLimb(-90.0, 0.0, 1.0, 0.35),
                rightArm = if (reachIn) FLimb(230.0, 210.0) else FLimb(lean + 80.0, lean + 80.0))
        }
        return FrontFigure(
            start = pose(0.0, false),
            end = pose(if (reach) 0.0 else 1.0, reach),
            timing = if (reach) Timing.NORMAL else Timing.HOLD,
        )
    }

    // ------------------------------------------------------------------------------------------------ isolation
    private fun curl(hold: List<Prop>, cable: P? = null) = SideFigure(
        start = Pose(torso = 90.0, nearArm = L(-94.0, -92.0), farArm = L(-94.0, -92.0)),
        end = Pose(torso = 92.0, nearArm = L(-100.0, 62.0), farArm = L(-100.0, 62.0)),
        props = hold + (cable?.let { listOf(Prop.Line(NEAR_WRIST, it)) } ?: emptyList()),
    )

    private fun pushdown() = SideFigure(
        start = Pose(torso = 84.0, nearArm = L(-96.0, 66.0)),
        end = Pose(torso = 84.0, nearArm = L(-96.0, -88.0)),
        props = listOf(Prop.Line(NEAR_WRIST, P(70.0, 330.0)), Prop.Rail(P(84.0, 0.0), P(84.0, 340.0)), Prop.Pad(NEAR_WRIST, 0.0, 22.0, 7.0)),
    )

    private fun overheadExtension() = SideFigure(
        anchor = Anchor.HIP,
        // The forearm lowers behind the head and comes back up (−104° → −262°, never in front of the face; review R7-04).
        start = seated(94.0, L(104.0, -104.0)),
        end = seated(94.0, L(104.0, -262.0)),
        props = SEAT + backrest(94.0) + Prop.Dumbbell(NEAR_WRIST, view = DbView.UPRIGHT, dy = 6.0),
    )

    private fun skullCrusher() = SideFigure(
        anchor = Anchor.HIP,
        start = Pose(torso = 180.0, neck = -4.0, hip = P(0.0, 60.0), near = W(70.0, 14.0, 1), nearArm = L(98.0, 96.0)),
        end = Pose(torso = 180.0, neck = -4.0, hip = P(0.0, 60.0), near = W(70.0, 14.0, 1), nearArm = L(104.0, 210.0)),
        props = listOf(Prop.Block(-120.0, 0.0, 150.0, 45.0), Prop.Plate(NEAR_WRIST, r = 20.0)),
    )

    private val STANDING_FRONT = FrontPose()
    /** Seated, seen from the front: the thighs point at the viewer (short), the shins hang to the floor. */
    private val SEATED_FRONT = FrontPose(hip = P(0.0, 96.0), rightLeg = FLimb(-100.0, -92.0, 0.12, 1.0), leftLeg = FLimb(-80.0, -88.0, 0.12, 1.0))

    private fun lateralRaise(dumbbells: Boolean) = FrontFigure(
        start = FrontPose(rightArm = FLimb(-98.0, -94.0), leftArm = FLimb(-82.0, -86.0)),
        end = FrontPose(rightArm = FLimb(-182.0, -188.0), leftArm = FLimb(2.0, 8.0)),
        props = if (dumbbells) listOf(FrontProp.Dumbbells()) else emptyList(),
    )

    private fun cableLateralRaise() = FrontFigure(
        start = FrontPose(leftArm = FLimb(-96.0, -100.0), rightArm = FLimb(-80.0, -60.0)),
        end = FrontPose(leftArm = FLimb(4.0, 8.0), rightArm = FLimb(-80.0, -60.0)),
        props = listOf(FrontProp.Cable(rightHand = false, to = P(-110.0, 16.0)), FrontProp.Rail(P(-120.0, 0.0), P(-120.0, 300.0))),
    )

    private fun rearFly(bentOver: Boolean, machine: Boolean = false, band: Boolean = false): FrontFigure {
        val base = if (bentOver) FrontPose(hip = P(0.0, 158.0), torsoScale = 0.42, rightLeg = FLimb(-94.0, -90.0), leftLeg = FLimb(-86.0, -90.0))
        else if (machine) SEATED_FRONT
        else STANDING_FRONT
        // Arms pointing at the viewer look short; spreading out to the sides they grow to full length.
        val armsIn = if (bentOver) Pair(FLimb(-96.0, -92.0), FLimb(-84.0, -88.0)) else Pair(FLimb(182.0, 182.0, 0.12), FLimb(-2.0, -2.0, 0.12))
        val armsOut = if (bentOver) Pair(FLimb(-182.0, -184.0), FLimb(2.0, 4.0)) else Pair(FLimb(182.0, 182.0), FLimb(-2.0, -2.0))
        return FrontFigure(
            start = base.copy(rightArm = armsIn.first, leftArm = armsIn.second),
            end = base.copy(rightArm = armsOut.first, leftArm = armsOut.second),
            props = when {
                bentOver -> listOf(FrontProp.Dumbbells())
                machine -> listOf(FrontProp.Seat(-50.0, 50.0, 100.0, 30.0), FrontProp.Rail(P(0.0, 50.0), P(0.0, 0.0)))
                band -> listOf(FrontProp.BandBetweenHands)
                else -> emptyList()
            },
        )
    }

    private fun fly(machine: Boolean) = FrontFigure(
        start = (if (machine) SEATED_FRONT else STANDING_FRONT)
            .copy(rightArm = if (machine) FLimb(180.0, 90.0) else FLimb(168.0, 164.0), leftArm = if (machine) FLimb(0.0, 90.0) else FLimb(12.0, 16.0)),
        end = (if (machine) SEATED_FRONT else STANDING_FRONT)
            .copy(rightArm = if (machine) FLimb(180.0, 90.0, 0.14, 1.0) else FLimb(196.0, 200.0, 0.16), leftArm = if (machine) FLimb(0.0, 90.0, 0.14, 1.0) else FLimb(-16.0, -20.0, 0.16)),
        props = if (machine) listOf(FrontProp.Seat(-50.0, 50.0, 100.0, 30.0), FrontProp.Rail(P(0.0, 50.0), P(0.0, 0.0)))
        else listOf(FrontProp.Cable(true, P(-170.0, 290.0)), FrontProp.Cable(false, P(170.0, 290.0)), FrontProp.Rail(P(-180.0, 0.0), P(-180.0, 300.0)), FrontProp.Rail(P(180.0, 0.0), P(180.0, 300.0))),
    )

    private fun legMachine(extension: Boolean) = SideFigure(
        anchor = Anchor.HIP,
        start = seated(98.0, L(-80.0, -40.0), L(0.0, if (extension) -100.0 else -2.0, if (extension) 0.0 else 70.0)),
        end = seated(98.0, L(-80.0, -40.0), L(0.0, if (extension) -2.0 else -104.0, if (extension) 70.0 else 0.0)),
        props = SEAT + backrest(98.0) + Prop.Pad(NEAR_ANKLE, 0.0, 0.0, 20.0, dx = if (extension) 12.0 else -6.0, dy = if (extension) 6.0 else 14.0) +
            if (extension) emptyList() else listOf(Prop.Pad(NEAR_KNEE, 0.0, 30.0, 12.0, dx = -24.0, dy = 18.0)),
    )

    /**
     * Rising onto the ball of the foot (the toes stay where they are: the hip rises by what the turned foot lifts the ankle). On a step,
     * the ball of the foot is on its edge and the heel hangs off behind, dropping below it at the start (review R7-06).
     */
    private fun calfRaise(machine: Boolean): SideFigure {
        val step = if (machine) 0.0 else 50.0
        val down = if (machine) 0.0 else 14.0
        // The ankle sits so the ball of the foot (the underside of the toe end, see foot()) rests on the floor or the step.
        fun ankle(turn: Double): Double {
            val r = Math.toRadians(turn)
            return step + 1.0 - (24.0 * kotlin.math.sin(r) - 7.5 * kotlin.math.cos(r) - 5.5)
        }
        fun pose(turn: Double) = Pose(torso = 90.0, hip = P(0.0, ankle(turn) + Body.SHIN + Body.THIGH), near = L(-90.0, -90.0, turn),
            far = if (machine) L(-90.0, -90.0, turn) else L(-90.0, -150.0, -10.0),
            nearArm = if (machine) R(80.0, 100.0) else HANG, farArm = if (machine) R(80.0, 100.0) else L(-60.0, -10.0))
        return SideFigure(
            anchor = Anchor.HIP,
            start = pose(down),
            end = pose(-26.0),
            props = if (machine) listOf(Prop.Pad(SHOULDER, 0.0, 36.0, 12.0, dy = 14.0), Prop.Rail(P(-50.0, 0.0), P(-50.0, 300.0)))
            else listOf(Prop.Block(12.0, 0.0, 80.0, step), Prop.Dumbbell(NEAR_WRIST), Prop.Rail(P(100.0, 0.0), P(100.0, 160.0))),
        )
    }

    private fun adductor() = FrontFigure(
        start = FrontPose(hip = P(0.0, 96.0), rightLeg = FLimb(-160.0, -96.0, 0.55, 0.86), leftLeg = FLimb(-20.0, -84.0, 0.55, 0.86)),
        end = FrontPose(hip = P(0.0, 96.0), rightLeg = FLimb(-110.0, -92.0, 0.2, 0.86), leftLeg = FLimb(-70.0, -88.0, 0.2, 0.86)),
        props = listOf(FrontProp.Seat(-50.0, 50.0, 100.0, 30.0), FrontProp.Rail(P(0.0, 50.0), P(0.0, 0.0))),
    )

    private fun straightArmPulldown() = SideFigure(
        start = Pose(torso = 74.0, near = L(-84.0, -94.0), nearArm = L(54.0, 54.0)),
        end = Pose(torso = 74.0, near = L(-84.0, -94.0), nearArm = L(-80.0, -80.0)),
        props = listOf(Prop.Line(NEAR_WRIST, P(150.0, 330.0)), Prop.Rail(P(164.0, 0.0), P(164.0, 340.0)), Prop.Pad(NEAR_WRIST, 0.0, 30.0, 7.0)),
    )

    private fun shrug() = FrontFigure(
        start = FrontPose(shrug = 0.0),
        end = FrontPose(shrug = 14.0, rightArm = FLimb(-100.0, -95.0), leftArm = FLimb(-80.0, -85.0)),
        props = listOf(FrontProp.Dumbbells()),
    )

    private fun crunch() = SideFigure(
        anchor = Anchor.HIP,
        start = Pose(torso = 180.0, neck = -6.0, hip = P(0.0, 18.0), near = W(64.0, 14.0, 1), nearArm = FOLDED),
        end = Pose(torso = 150.0, neck = -24.0, hip = P(0.0, 18.0), near = W(64.0, 14.0, 1), nearArm = FOLDED),
    )

    private fun cableCrunch() = SideFigure(
        anchor = Anchor.NEAR_KNEE,
        anchorAt = P(0.0, 10.0),
        start = Pose(torso = 84.0, near = L(-100.0, 180.0, 180.0), nearArm = R(-30.0, 140.0)),
        end = Pose(torso = -14.0, neck = -10.0, near = L(-120.0, 180.0, 180.0), nearArm = R(-30.0, 140.0)),
        props = listOf(Prop.Line(NEAR_WRIST, P(60.0, 330.0)), Prop.Rail(P(74.0, 0.0), P(74.0, 340.0))),
    )

    // ------------------------------------------------------------------------------------------------ the library
    private val all: Map<String, Figure> by lazy {
        val db = listOf(Prop.Dumbbell(NEAR_WRIST))
        val kb = listOf(Prop.Kettlebell(NEAR_WRIST))
        val barbell = listOf(Prop.Plate(NEAR_WRIST, r = 28.0))
        val ez = listOf(Prop.Plate(NEAR_WRIST, r = 20.0))
        val smith = listOf(Prop.Rail(P(10.0, 0.0), P(10.0, 285.0)))
        mapOf(
            // Squat
            "back-squat" to squat(Hold.BACK_BAR),
            "front-squat" to squat(Hold.FRONT_BAR, depthTorso = 70.0, shin = -124.0),
            "goblet-squat" to squat(Hold.GOBLET_DB, depthTorso = 68.0),
            "kb-goblet-squat" to squat(Hold.GOBLET_KB, depthTorso = 68.0),
            "kb-double-front-squat" to squat(Hold.RACK_KB, depthTorso = 68.0),
            "leg-press" to legPress(),
            "hack-squat" to hackSquat(),
            "smith-squat" to squat(Hold.BACK_BAR, depthTorso = 62.0, extra = smith),
            "cable-goblet-squat" to squat(Hold.CABLE_GOBLET, depthTorso = 68.0, extra = listOf(Prop.Rail(P(120.0, 0.0), P(120.0, 300.0)))),
            "bodyweight-box-squat" to boxSquat(),
            "air-squat" to squat(Hold.ARMS_FORWARD, depthTorso = 62.0),
            "tempo-squat" to squat(Hold.ARMS_FORWARD, depthTorso = 62.0, timing = Timing(0.1, 0.5, 0.1, 0.3, 6.0)),
            "box-jump" to boxJump(),
            "db-speed-squat" to squat(Hold.SIDES_DB, depthTorso = 66.0, thigh = -20.0, shin = -116.0, timing = Timing.FAST),
            "speed-squat" to squat(Hold.ARMS_FORWARD, depthTorso = 64.0, thigh = -18.0, shin = -116.0, timing = Timing.FAST),
            "fast-sit-to-stand" to sitToStand(),
            // Hinge
            "deadlift" to deadlift(),
            "trap-bar-deadlift" to deadlift(trap = true),
            "romanian-deadlift" to rdl(Prop.Plate(NEAR_WRIST, r = 28.0)),
            "barbell-hip-thrust" to hipThrust(listOf(Prop.Plate(HIP, 4.0, 22.0, r = 30.0))),
            "db-romanian-deadlift" to rdl(Prop.Dumbbell(NEAR_WRIST)),
            "db-single-leg-rdl" to singleLegRdl(Prop.Dumbbell(FAR_WRIST)),
            "db-hip-thrust" to hipThrust(listOf(Prop.Dumbbell(HIP, 4.0, 22.0))),
            "kb-swing" to swing(Prop.Kettlebell(NEAR_WRIST, along = true)),
            "kb-deadlift" to deadlift(kb = true),
            "kb-single-leg-rdl" to singleLegRdl(Prop.Kettlebell(FAR_WRIST)),
            "cable-pull-through" to pullThrough(),
            "machine-hip-thrust" to hipThrust(listOf(Prop.Pad(HIP, 0.0, 50.0, 14.0, dy = 22.0)), machine = true),
            "back-extension" to backExtension(),
            "glute-bridge" to gluteBridge(),
            "single-leg-glute-bridge" to gluteBridge(singleLeg = true),
            "bodyweight-single-leg-rdl" to singleLegRdl(null),
            "med-ball-scoop-toss" to scoopToss(),
            "db-swing" to swing(Prop.Dumbbell(NEAR_WRIST, view = DbView.ACROSS)),
            // Lunge
            "barbell-split-squat" to splitSquat(Hold.BACK_BAR),
            "db-split-squat" to splitSquat(Hold.SIDES_DB),
            "db-rear-foot-elevated-split-squat" to splitSquat(Hold.SIDES_DB, rear = 66.0, rearX = -124.0, rearFoot = -100.0, extra = listOf(Prop.Block(-170.0, 0.0, 70.0, 48.0))),
            "db-reverse-lunge" to reverseLunge(Hold.SIDES_DB),
            "db-walking-lunge" to walkingLunge(),
            "db-step-up" to stepUp(Hold.SIDES_DB),
            "kb-reverse-lunge" to reverseLunge(Hold.SIDES_KB),
            "cable-split-squat" to splitSquat(Hold.CABLE_GOBLET, extra = listOf(Prop.Rail(P(150.0, 0.0), P(150.0, 300.0)))),
            "smith-split-squat" to splitSquat(Hold.BACK_BAR, extra = listOf(Prop.Rail(P(-50.0, 0.0), P(-50.0, 340.0)))),
            "bodyweight-split-squat" to splitSquat(Hold.HANDS_ON_HIPS),
            "bodyweight-rear-foot-elevated-split-squat" to splitSquat(Hold.HANDS_ON_HIPS, rear = 66.0, rearX = -124.0, rearFoot = -100.0, extra = listOf(Prop.Block(-170.0, 0.0, 70.0, 48.0))),
            "bodyweight-reverse-lunge" to reverseLunge(Hold.HANDS_ON_HIPS),
            "step-up" to stepUp(Hold.HANDS_ON_HIPS),
            "lateral-lunge" to lateralLunge(),
            // Horizontal push
            "bench-press" to benchPress(load = { barbell + listOf(Prop.Rail(P(-140.0, 0.0), P(-140.0, 180.0))) }),
            "incline-bench-press" to benchPress(incline = 32.0, load = { barbell }),
            "close-grip-bench-press" to benchPress(load = { barbell + listOf(Prop.Rail(P(-140.0, 0.0), P(-140.0, 180.0))) }),
            "db-bench-press" to benchPress(load = { db }),
            "db-incline-press" to benchPress(incline = 32.0, load = { db }),
            "db-floor-press" to benchPress(load = { db }, floor = true),
            "kb-floor-press" to benchPress(load = { kb }, floor = true, oneArm = true),
            "cable-chest-press" to cablePress(),
            "chest-press-machine" to machinePress(up = false),
            "push-up-incline" to pushUp(handsY = 62.0, extra = listOf(Prop.Block(40.0, 0.0, 90.0, 54.0))),
            "push-up" to pushUp(),
            "push-up-feet-elevated" to pushUp(feetY = 70.0, extra = listOf(Prop.Block(-200.0, 0.0, 80.0, 52.0))),
            "push-up-deficit" to pushUp(handsY = 14.0, bottomAngle = 3.0, extra = listOf(Prop.Dumbbell(NEAR_WRIST))),
            "push-up-weighted" to pushUp(extra = listOf(Prop.Vest)),
            "dip-assisted" to dip(assisted = true),
            "dip" to dip(),
            "dip-weighted" to dip(weighted = true),
            // Vertical push
            "overhead-press" to overheadPress(barbell),
            "landmine-press" to landminePress(push = false),
            "landmine-push-press" to landminePress(push = true),
            "db-shoulder-press" to seatedPress(db),
            "db-half-kneeling-press" to halfKneelingPress(),
            "kb-press" to overheadPress(kb, oneArm = true),
            "shoulder-press-machine" to machinePress(up = true),
            "smith-overhead-press" to seatedPress(barbell, bar = true),
            "pike-push-up" to pikePushUp(elevated = false),
            "pike-push-up-elevated" to pikePushUp(elevated = true),
            "db-push-press" to overheadPress(db, dip = true),
            // Horizontal pull
            "barbell-row" to bentRow(barbell),
            "landmine-row" to bentRow(emptyList(), torso = 30.0, landmine = true),
            "db-one-arm-row" to benchRow(),
            "chest-supported-db-row" to chestSupportedRow(),
            "kb-row" to kbRow(),
            "seated-cable-row" to seatedCableRow(),
            "single-arm-cable-row" to standingRow(band = false, oneArm = true),
            "face-pull" to facePull(),
            "machine-row" to seatedCableRow(machine = true),
            "inverted-row" to invertedRow(),
            "band-row" to standingRow(band = true, oneArm = false),
            // Vertical pull
            "lat-pulldown" to pulldown(),
            "half-kneeling-cable-pulldown" to halfKneelingPulldown(),
            "band-pulldown" to pulldown(band = true),
            "pull-up-assisted" to pullUp(listOf(Prop.Pad(NEAR_KNEE, 0.0, 60.0, 12.0, dx = -30.0, dy = -12.0), Prop.Rail(P(-60.0, 0.0), P(-60.0, 150.0))), knees = true),
            "pull-up-band-assisted" to pullUp(listOf(Prop.Line(NEAR_KNEE, P(0.0, 362.0), band = true)), knees = true),
            "pull-up-negative" to pullUp(negative = true),
            "pull-up" to pullUp(),
            "pull-up-weighted" to pullUp(listOf(Prop.Vest)),
            // Carries
            "farmer-carry" to carry(listOf(Prop.Dumbbell(NEAR_WRIST), Prop.Dumbbell(FAR_WRIST))),
            "trap-bar-carry" to carry(listOf(Prop.Plate(NEAR_WRIST, r = 28.0))),
            "suitcase-carry" to carry(listOf(Prop.Dumbbell(NEAR_WRIST)), farArm = L(-80.0, -70.0)),
            "goblet-carry" to carry(listOf(Prop.Dumbbell(NEAR_WRIST, 4.0, 4.0, DbView.UPRIGHT)), nearArm = R(-58.0, 104.0), farArm = R(-58.0, 104.0)),
            "kb-farmer-carry" to carry(listOf(Prop.Kettlebell(NEAR_WRIST), Prop.Kettlebell(FAR_WRIST))),
            "kb-suitcase-carry" to carry(listOf(Prop.Kettlebell(NEAR_WRIST)), farArm = L(-80.0, -70.0)),
            "kb-offset-overhead-carry" to carry(listOf(Prop.Kettlebell(NEAR_WRIST, 0.0, -14.0)), nearArm = L(92.0, 92.0), farArm = L(-80.0, -70.0)),
            // Rotation
            "landmine-rotation" to landmineRotation(),
            "db-woodchop" to chop(listOf(Prop.Dumbbell(MID_WRIST, view = DbView.ACROSS)), kneel = false),
            "kb-chop" to chop(listOf(Prop.Kettlebell(MID_WRIST)), kneel = false),
            "cable-chop-half-kneeling" to chop(emptyList(), kneel = true, cable = P(-120.0, 300.0)),
            "cable-lift-half-kneeling" to chop(emptyList(), kneel = true, lift = true, cable = P(-120.0, 20.0)),
            "cable-chop-standing" to chop(emptyList(), kneel = false, cable = P(-120.0, 320.0)),
            "med-ball-rotational-throw" to rotationalThrow(),
            "band-chop" to chop(listOf(Prop.Line(MID_WRIST, P(-120.0, 320.0), band = true)), kneel = false),
            // Anti-extension
            "dead-bug" to deadBug(hold = false),
            "db-dead-bug" to deadBug(hold = true),
            "front-plank" to plank(),
            "long-lever-plank" to plank(long = true),
            "body-saw" to bodySaw(),
            "ab-wheel-kneeling" to rollout(),
            "ab-wheel-standing" to rollout(standing = true),
            "barbell-rollout" to rollout(bar = true),
            "bear-hold" to bear(crawl = false),
            "bear-crawl" to bear(crawl = true),
            "lateral-bear-crawl" to bear(crawl = true),
            "brace-breathing" to braceBreathing(),
            // Anti-rotation
            "pallof-half-kneeling" to pallof(kneel = true),
            "pallof-standing" to pallof(kneel = false),
            "pallof-walkout" to pallof(kneel = false, walk = true),
            "band-pallof-press" to pallof(kneel = false, band = true),
            "bird-dog" to birdDog(),
            "plank-shoulder-tap" to highPlank(tap = true, row = false),
            "renegade-row" to highPlank(tap = false, row = true),
            // Anti-lateral flexion
            "side-plank-kneeling" to sidePlank(kneeling = true),
            "side-plank" to sidePlank(),
            "side-plank-reach" to sidePlank(reach = true),
            // Isolation
            "db-curl" to curl(db + Prop.Dumbbell(FAR_WRIST)),
            "hammer-curl" to curl(listOf(Prop.Dumbbell(NEAR_WRIST, view = DbView.ACROSS))),
            "ez-bar-curl" to curl(ez),
            "cable-curl" to curl(listOf(Prop.Pad(NEAR_WRIST, 0.0, 22.0, 7.0)), cable = P(90.0, 16.0)),
            "band-curl" to curl(listOf(Prop.Line(NEAR_WRIST, P(14.0, 4.0), band = true))),
            "cable-pushdown" to pushdown(),
            "db-overhead-triceps-extension" to overheadExtension(),
            "ez-skull-crusher" to skullCrusher(),
            "db-lateral-raise" to lateralRaise(dumbbells = true),
            "cable-lateral-raise" to cableLateralRaise(),
            "db-rear-delt-fly" to rearFly(bentOver = true),
            "reverse-pec-deck" to rearFly(bentOver = false, machine = true),
            "band-pull-apart" to rearFly(bentOver = false, band = true),
            "cable-fly" to fly(machine = false),
            "pec-deck" to fly(machine = true),
            "leg-extension" to legMachine(extension = true),
            "leg-curl" to legMachine(extension = false),
            "slider-hamstring-curl" to sliderCurl(),
            "calf-raise-machine" to calfRaise(machine = true),
            "db-single-leg-calf-raise" to calfRaise(machine = false),
            "hip-adductor-machine" to adductor(),
            "straight-arm-pulldown" to straightArmPulldown(),
            "db-shrug" to shrug(),
            "crunch" to crunch(),
            "cable-crunch" to cableCrunch(),
        )
    }
}
