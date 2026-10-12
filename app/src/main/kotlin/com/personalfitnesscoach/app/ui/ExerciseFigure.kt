package com.personalfitnesscoach.app.ui

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.personalfitnesscoach.app.figure.Bounds
import com.personalfitnesscoach.app.figure.ExerciseFigures
import com.personalfitnesscoach.app.figure.Figure
import com.personalfitnesscoach.app.figure.FrontFigure
import com.personalfitnesscoach.app.figure.Kind
import com.personalfitnesscoach.app.figure.Layer
import com.personalfitnesscoach.app.figure.Outline
import com.personalfitnesscoach.app.figure.P
import com.personalfitnesscoach.app.figure.Sculpt
import com.personalfitnesscoach.app.figure.SculptFrame
import com.personalfitnesscoach.app.figure.Sculptor
import com.personalfitnesscoach.app.figure.Shape
import com.personalfitnesscoach.app.figure.Tone
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.Muscle

/**
 * The "studio" colours of the exercise pictures (D-099, D-105): a lit, sculpted figure on a dark card in both themes, like a photo
 * studio. Light comes from the upper left; the shadow side catches a warm rim. The far side of the body is darker so left and right read
 * apart; working muscles glow in the warm colour; the form guide is the cool colour.
 */
private object Studio {
    val top = Color(0xFF24344D)
    val mid = Color(0xFF141E30)
    val bottom = Color(0xFF0D1422)
    val skin = arrayOf(0f to Color(0xFFFFFFFF), 0.3f to Color(0xFFE7ECF4), 0.64f to Color(0xFFAAB8CD), 0.88f to Color(0xFF72849F),
        0.96f to Color(0xFF9C6A55), 1f to Color(0xFFE9814F))
    val skinFar = arrayOf(0f to Color(0xFF7C8CA6), 0.5f to Color(0xFF56657F), 0.88f to Color(0xFF3A475E), 1f to Color(0xFF5E4538))
    val shoe = arrayOf(0f to Color(0xFF5C7092), 0.75f to Color(0xFF2C3A55), 1f to Color(0xFFC2643A))
    val shoeFar = arrayOf(0f to Color(0xFF3A4A66), 1f to Color(0xFF222D40))
    val ball = arrayOf(0f to Color(0xFFFFFFFF), 0.4f to Color(0xFFE2E8F1), 0.72f to Color(0xFF8495AF), 0.9f to Color(0xFF9C6A55), 1f to Color(0xFFE9814F))
    val ballFar = arrayOf(0f to Color(0xFF7C8CA6), 0.7f to Color(0xFF4A586F), 1f to Color(0xFF5E4538))
    val metal = arrayOf(0f to Color(0xFF9DB0CC), 0.55f to Color(0xFF61769A), 0.9f to Color(0xFF3D4F6E), 1f to Color(0xFF8FA3C2))
    val metalFar = arrayOf(0f to Color(0xFF4C5F80), 0.75f to Color(0xFF33435E), 1f to Color(0xFF26334A))
    val edge = Color(0x73080C16)
    val far = Color(0xFF55657D)
    val bar = Color(0xFFC9D3E2)
    val weight = Color(0xFF6E83A6)
    val farWeight = Color(0xFF3F5170)
    val hole = Color(0xFF141E30)
    val cable = Color(0xFF8A9BB0)
    val rail = Color(0xFF3A4B66)
    val boxTop = Color(0xFF34496B)
    val boxMid = Color(0xFF26364F)
    val boxBottom = Color(0xFF1A2538)
    val boxEdge = Color(0xFF3D5375)
    val floor = Color(0x1AFFFFFF)
    val warm = Color(0xFFFF6B2C)
    val guide = Color(0xFF2DE2D0)
}

/** True when the phone's "remove animations" setting is on: the figure then stands still at its still position (NFR-08). */
@Composable
private fun animationsOff(): Boolean {
    val context = LocalContext.current
    if (LocalInspectionMode.current) return true
    return remember { runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false) }
}

/** A dark studio card (rounded, radial light) for an exercise picture and the words over it. */
@Composable
fun StudioCard(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.clip(CardShape)
            .background(Brush.radialGradient(0f to Studio.top, 0.55f to Studio.mid, 1f to Studio.bottom))
            .border(1.dp, Color(0x1AFFFFFF), CardShape),
        content = content,
    )
}

/**
 * The exercise's moving figure: one rep (start → end → start) in a slow loop, or the still position when animations are off, with the
 * exercise's primary muscles glowing (CR-008). TalkBack reads [description]; nothing here is tappable. Draws nothing for an exercise
 * without a figure.
 */
@Composable
fun ExerciseFigure(exerciseId: String, description: String, modifier: Modifier = Modifier, alignEnd: Boolean = false) {
    val figure = remember(exerciseId) { ExerciseFigures.of(exerciseId) } ?: return
    val muscles = remember(exerciseId) { Library[exerciseId]?.primary ?: emptySet() }
    ExerciseFigure(figure, description, modifier, alignEnd, muscles)
}

@Composable
fun ExerciseFigure(figure: Figure, description: String, modifier: Modifier = Modifier, alignEnd: Boolean = false, muscles: Set<Muscle> = emptySet()) {
    val still = animationsOff()
    val camera = remember(figure) { Sculptor.camera(figure) }
    val front = figure is FrontFigure
    val u = if (still) null else {
        val loop = rememberInfiniteTransition(label = "figure")
        loop.animateFloat(0f, 1f, infiniteRepeatable(tween((figure.timing.seconds * 1000).toInt(), easing = LinearEasing)), label = "rep")
    }
    Canvas(modifier.semantics { contentDescription = description; role = Role.Image }) {
        val t = u?.value?.let { figure.timing.t(it.toDouble() + figure.loopStart) } ?: figure.still
        drawSculpt(Sculptor.sculpt(figure.frame(t), front, t, muscles), camera, alignEnd)
    }
}

private fun DrawScope.drawSculpt(frame: SculptFrame, camera: Bounds, alignEnd: Boolean) {
    // The camera box fits inside the padding, centred (or against the right edge), and never moves during the loop.
    val pad = size.minDimension * 0.08f
    val scale = minOf((size.width - 2 * pad) / camera.width.toFloat(), (size.height - 2 * pad) / camera.height.toFloat())
    val ox = if (alignEnd) size.width - pad - camera.maxX.toFloat() * scale
    else pad + (size.width - 2 * pad - camera.width.toFloat() * scale) / 2 - camera.minX.toFloat() * scale
    val floorY = pad + (size.height - 2 * pad - camera.height.toFloat() * scale) / 2 + camera.maxY.toFloat() * scale
    fun o(p: P) = Offset(ox + p.x.toFloat() * scale, floorY - p.y.toFloat() * scale)
    fun path(outline: Outline) = Path().apply {
        o(outline.start).let { moveTo(it.x, it.y) }
        for (c in outline.curves) { val a = o(c.c1); val b = o(c.c2); val e = o(c.end); cubicTo(a.x, a.y, b.x, b.y, e.x, e.y) }
        close()
    }

    // The floor only under the figure and its equipment (not under words beside it on a card), with a soft contact shadow where the
    // body or the equipment meets it.
    val floorFrom = (ox + camera.minX.toFloat() * scale - 14.dp.toPx()).coerceAtLeast(0f)
    val floorTo = (ox + camera.maxX.toFloat() * scale + 14.dp.toPx()).coerceAtMost(size.width)
    drawLine(Studio.floor, Offset(floorFrom, floorY), Offset(floorTo, floorY), strokeWidth = 1.5.dp.toPx())
    val low = frame.items.flatMap { s ->
        when (s) {
            is Sculpt.Part -> s.outline.sample(3).filter { it.y < 10 }
            is Sculpt.Ball -> if (s.c.y - s.ry < 10) listOf(s.c) else emptyList()
            is Sculpt.Prop -> when (val sh = s.shape) {
                is Shape.Box -> sh.corners.filter { it.y < 10 }
                is Shape.Dot -> if (sh.c.y - sh.r < 10) listOf(sh.c) else emptyList()
                else -> emptyList()
            }
        }
    }
    if (low.isNotEmpty()) {
        val x0 = o(P(low.minOf { it.x }, 0.0)).x; val x1 = o(P(low.maxOf { it.x }, 0.0)).x
        val rx = (x1 - x0) / 2 + 18f * scale; val ry = 6f * scale + 3.dp.toPx(); val cx = (x0 + x1) / 2
        drawOval(Brush.radialGradient(0f to Color(0x80000000), 1f to Color.Transparent, Offset(cx, floorY), rx),
            Offset(cx - rx, floorY - ry), Size(2 * rx, 2 * ry))
    }

    // A soft warm halo behind the body: wide, faint strokes along each near body part (no blur filter, so it looks the same on every
    // phone and costs little per frame; review R7-12).
    for ((extra, alpha) in HALO) for (s in frame.items) if (s is Sculpt.Part && s.layer != Layer.FAR) {
        drawLine(Studio.warm.copy(alpha = alpha), o(s.from), o(s.to), strokeWidth = (2 * s.r * scale).toFloat() + extra.toPx(), cap = StrokeCap.Round)
    }

    val edge = Stroke(0.8.dp.toPx())
    for (s in frame.items) when (s) {
        is Sculpt.Part -> {
            val p = path(s.outline)
            val stops = when (s.tone) { Tone.SKIN -> Studio.skin; Tone.SKIN_FAR -> Studio.skinFar; Tone.SHOE -> Studio.shoe; Tone.SHOE_FAR -> Studio.shoeFar }
            drawPath(p, Brush.linearGradient(*stops, start = o(s.lit), end = o(s.shade)))
            val strength = (frame.glow * if (s.layer == Layer.FAR) 0.6 else 1.0).toFloat()
            for (g in s.glows) drawPath(p, Brush.radialGradient(0f to Studio.warm.copy(alpha = 0.8f), 0.55f to Studio.warm.copy(alpha = 0.45f),
                1f to Studio.warm.copy(alpha = 0f), center = o(g.c), radius = (g.r * scale).toFloat().coerceAtLeast(1f)), alpha = strength)
            drawPath(p, Studio.edge, style = edge)
        }
        is Sculpt.Ball -> {
            val c = o(s.c); val rx = (s.rx * scale).toFloat(); val ry = (s.ry * scale).toFloat()
            val lit = Offset(c.x + Sculptor.LIGHT.x.toFloat() * rx * 0.45f, c.y - Sculptor.LIGHT.y.toFloat() * ry * 0.45f)
            val far = s.layer == Layer.FAR
            drawOval(Brush.radialGradient(*(if (far) Studio.ballFar else Studio.ball), center = lit, radius = maxOf(rx, ry) * 1.45f),
                Offset(c.x - rx, c.y - ry), Size(2 * rx, 2 * ry))
            drawOval(Studio.edge, Offset(c.x - rx, c.y - ry), Size(2 * rx, 2 * ry), style = edge)
        }
        is Sculpt.Prop -> drawProp(s.shape, scale, ::o)
    }
}

/** Equipment and guides: metal with a highlight, padded boxes, cables, bands and the dashed form guide. */
private fun DrawScope.drawProp(s: Shape, scale: Float, o: (P) -> Offset) {
    when (s) {
        is Shape.Bone -> {
            val a = o(s.a); val b = o(s.b); val w = s.width.toFloat() * scale
            val col = when { s.kind != Kind.WEIGHT -> Studio.bar; s.layer == Layer.FAR -> Studio.farWeight; else -> Studio.weight }
            drawLine(col, a, b, strokeWidth = w, cap = StrokeCap.Round)
            if (s.kind == Kind.WEIGHT && s.layer != Layer.FAR && w > 4.dp.toPx()) {
                // A highlight along the lit side.
                val dx = b.x - a.x; val dy = b.y - a.y; val l = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
                var nx = -dy / l; var ny = dx / l
                if (nx * Sculptor.LIGHT.x.toFloat() - ny * Sculptor.LIGHT.y.toFloat() < 0) { nx = -nx; ny = -ny }
                val off = Offset(nx * w * 0.25f, ny * w * 0.25f)
                drawLine(Color(0x47FFFFFF), a + off, b + off, strokeWidth = w * 0.22f, cap = StrokeCap.Round)
            }
        }
        is Shape.Dot -> {
            val c = o(s.c); val r = s.r.toFloat() * scale
            when (s.kind) {
                Kind.WEIGHT -> {
                    val far = s.layer == Layer.FAR
                    val lit = Offset(c.x + Sculptor.LIGHT.x.toFloat() * r * 0.4f, c.y - Sculptor.LIGHT.y.toFloat() * r * 0.4f)
                    drawCircle(Brush.radialGradient(*(if (far) Studio.metalFar else Studio.metal), center = lit, radius = r * 1.4f), r, c)
                    if (!far && r > 8.dp.toPx()) drawCircle(Color(0x59BECDE4), r, c, style = Stroke(1.dp.toPx()))
                }
                Kind.HOLE -> drawCircle(Studio.hole, r, c)
                Kind.RAIL, Kind.PULLEY -> drawCircle(Studio.far, r, c)
                else -> drawCircle(Studio.bar, r, c)
            }
        }
        is Shape.Poly -> drawPath(Path().apply {
            s.points.forEachIndexed { i, p -> val q = o(p); if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) }
            close()
        }, if (s.layer == Layer.FAR) Studio.farWeight else Studio.weight)
        is Shape.Box -> {
            val cs = s.corners.map(o)
            val p = Path().apply { cs.forEachIndexed { i, q -> if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) }; close() }
            val top = cs.minOf { it.y }; val bottom = cs.maxOf { it.y }
            drawPath(p, Brush.verticalGradient(0f to Studio.boxTop, 0.25f to Studio.boxMid, 1f to Studio.boxBottom, startY = top, endY = bottom))
            drawPath(p, Studio.boxEdge, style = Stroke(1.dp.toPx()))
        }
        is Shape.Stroke -> when (s.kind) {
            Kind.CABLE -> drawLine(Studio.cable, o(s.a), o(s.b), strokeWidth = 1.5.dp.toPx())
            Kind.BAND -> drawLine(Studio.guide, o(s.a), o(s.b), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            Kind.RAIL -> drawLine(Studio.rail, o(s.a), o(s.b), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
            Kind.GUIDE -> drawLine(Studio.guide, o(s.a), o(s.b), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())))
            else -> drawLine(Studio.cable, o(s.a), o(s.b), strokeWidth = 1.dp.toPx())
        }
    }
}

/** The halo's rings: how wide, and how strong. */
private val HALO = listOf(16.dp to 0.035f, 10.dp to 0.05f, 5.dp to 0.06f)
