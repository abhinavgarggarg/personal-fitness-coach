package com.personalfitnesscoach.app.ui

import android.graphics.BlurMaskFilter
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
import com.personalfitnesscoach.app.figure.Frame
import com.personalfitnesscoach.app.figure.Kind
import com.personalfitnesscoach.app.figure.Layer
import com.personalfitnesscoach.app.figure.P
import com.personalfitnesscoach.app.figure.Shape

/**
 * The "studio" colours of the exercise pictures (D-099, CR-004): a light figure on a dark card in both themes, like a photo studio.
 * The far side of the body is darker so left and right read apart; the form guide is the cool colour.
 */
private object Studio {
    val top = Color(0xFF24344D)
    val mid = Color(0xFF141E30)
    val bottom = Color(0xFF0D1422)
    val body = Color(0xFFE9EEF5)
    val far = Color(0xFF55657D)
    val weight = Color(0xFF5A6E8E)
    val farWeight = Color(0xFF3F5170)
    val hole = Color(0xFF141E30)
    val cable = Color(0xFF8A9BB0)
    val rail = Color(0xFF3A4B66)
    val box = Color(0xFF22324A)
    val boxEdge = Color(0xFF33455F)
    val floor = Color(0x24FFFFFF)
    val halo = Color(0xFFFF6B2C)
    val guide = Color(0xFF2DE2D0)
}

/** True when the phone's "remove animations" setting is on: the figure then stands still at the end of the rep (NFR-08). */
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
 * The exercise's moving figure: one rep (start → end → start) in a slow loop, or the end position standing still when animations are
 * off. TalkBack reads [description]; nothing here is tappable. Draws nothing for an exercise without a figure.
 */
@Composable
fun ExerciseFigure(exerciseId: String, description: String, modifier: Modifier = Modifier, alignEnd: Boolean = false) {
    val figure = remember(exerciseId) { ExerciseFigures.of(exerciseId) } ?: return
    ExerciseFigure(figure, description, modifier, alignEnd)
}

@Composable
fun ExerciseFigure(figure: Figure, description: String, modifier: Modifier = Modifier, alignEnd: Boolean = false) {
    val still = animationsOff()
    val camera = remember(figure) { figure.camera() }
    val u = if (still) null else {
        val loop = rememberInfiniteTransition(label = "figure")
        loop.animateFloat(0f, 1f, infiniteRepeatable(tween((figure.timing.seconds * 1000).toInt(), easing = LinearEasing)), label = "rep")
    }
    Canvas(modifier.semantics { contentDescription = description; role = Role.Image }) {
        val t = u?.value?.let { figure.timing.t(it.toDouble() + figure.timing.peak) } ?: 1.0
        drawFigure(figure.frame(t), camera, alignEnd)
    }
}

private fun DrawScope.drawFigure(frame: Frame, camera: Bounds, alignEnd: Boolean) {
    // The camera box fits inside the padding, centred (or against the right edge), and never moves during the loop.
    val pad = size.minDimension * 0.08f
    val scale = minOf((size.width - 2 * pad) / camera.width.toFloat(), (size.height - 2 * pad) / camera.height.toFloat())
    val ox = if (alignEnd) size.width - pad - camera.maxX.toFloat() * scale
    else pad + (size.width - 2 * pad - camera.width.toFloat() * scale) / 2 - camera.minX.toFloat() * scale
    val floorY = pad + (size.height - 2 * pad - camera.height.toFloat() * scale) / 2 + camera.maxY.toFloat() * scale
    fun o(p: P) = Offset(ox + p.x.toFloat() * scale, floorY - p.y.toFloat() * scale)
    val shapes = frame.shapes.sortedBy { it.layer.ordinal }

    // The floor line, then a soft warm halo behind the body (drawn once per frame, blurred).
    drawLine(Studio.floor, Offset(0f, floorY), Offset(size.width, floorY), strokeWidth = 2.dp.toPx())
    drawIntoCanvas { c ->
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            color = Studio.halo.copy(alpha = 0.42f).toArgb()
            maskFilter = BlurMaskFilter(9.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
        }
        for (s in shapes) if (s is Shape.Bone && s.kind == Kind.BODY && (s.layer == Layer.BODY || s.layer == Layer.NEAR)) {
            paint.strokeWidth = s.width.toFloat() * scale + 8.dp.toPx()
            val a = o(s.a); val b = o(s.b)
            c.nativeCanvas.drawLine(a.x, a.y, b.x, b.y, paint)
        }
    }
    for (s in shapes) {
        when (s) {
            is Shape.Bone -> drawLine(colour(s.kind, s.layer), o(s.a), o(s.b), strokeWidth = s.width.toFloat() * scale, cap = StrokeCap.Round)
            is Shape.Dot -> drawCircle(if (s.kind == Kind.RAIL) Studio.far else colour(s.kind, s.layer), s.r.toFloat() * scale, o(s.c))
            is Shape.Poly -> drawPath(path(s.points.map(::o)), colour(s.kind, s.layer))
            is Shape.Box -> {
                val p = path(s.corners.map(::o))
                drawPath(p, Studio.box)
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
}

private fun colour(k: Kind, l: Layer): Color = when {
    k == Kind.WEIGHT && l == Layer.FAR -> Studio.farWeight
    k == Kind.WEIGHT -> Studio.weight
    k == Kind.HOLE -> Studio.hole
    k == Kind.PULLEY -> Studio.far
    l == Layer.FAR -> Studio.far
    else -> Studio.body
}

private fun path(points: List<Offset>) = Path().apply {
    points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
    close()
}
