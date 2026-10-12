package com.personalfitnesscoach.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.personalfitnesscoach.app.ui.theme.GlowColors
import com.personalfitnesscoach.app.ui.theme.LocalGlow

/**
 * The "Glow" building blocks (D-099): the screen background, frosted-glass cards, the warm halo behind the one main action, progress
 * rings, and the app's own line icons (no icon library). Everything reads its colours from [LocalGlow], so light and dark both follow.
 */
val Glass: GlowColors @Composable get() = LocalGlow.current

val CardShape = RoundedCornerShape(22.dp)
val ChipShape = RoundedCornerShape(16.dp)
val PillShape = RoundedCornerShape(percent = 50)

/** The screen background: deep navy from top to bottom (light: soft grey-blue). */
@Composable
fun GlowBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val g = Glass
    Box(modifier.background(Brush.verticalGradient(0f to g.bgTop, 0.6f to g.bgMid, 1f to g.bgBottom)), content = content)
}

/** A frosted-glass surface: a light veil with a hairline border. */
fun Modifier.glass(shape: Shape = CardShape, strong: Boolean = false, border: Color? = null): Modifier = composed {
    val g = LocalGlow.current
    this.background(if (strong) g.glassStrong else g.glass, shape).border(1.dp, border ?: g.glassBorder, shape)
}

/**
 * A soft blurred halo in [color] behind the element (drawn once, never animated). Only the main action, the active tab marker and the
 * progress rings glow. Phones without hardware shadow support just draw nothing.
 */
fun Modifier.glow(color: Color, radius: Dp = 18.dp, corner: Dp = 32.dp, alpha: Float = 0.55f): Modifier = drawBehind {
    val paint = Paint().asFrameworkPaint().apply {
        isAntiAlias = true
        this.color = android.graphics.Color.TRANSPARENT
        setShadowLayer(radius.toPx(), 0f, 0f, color.copy(alpha = alpha).toArgb())
    }
    drawIntoCanvas { it.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, corner.toPx(), corner.toPx(), paint) }
}

/** The warm button gradient (left to right). */
@Composable
fun warmBrush(): Brush {
    val g = Glass
    return Brush.horizontalGradient(0f to g.warmStart, 0.55f to g.warm, 1f to g.warmEnd)
}

/**
 * A progress ring with round caps, warm to cool, with a soft halo. [fraction] 0–1; [track] is the empty part. The words inside are the
 * caller's (TalkBack reads the caller's description, never the ring alone).
 */
@Composable
fun ProgressRing(fraction: Float, size: Dp, stroke: Dp = 14.dp, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val g = Glass
    Box(modifier.size(size), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val d = this.size.minDimension - w
            val tl = Offset((this.size.width - d) / 2, (this.size.height - d) / 2)
            drawArc(g.glassBorder, 0f, 360f, false, tl, Size(d, d), style = Stroke(w))
            val sweep = 360f * fraction.coerceIn(0f, 1f)
            if (sweep > 0f) {
                val brush = Brush.sweepGradient(listOf(g.warm, g.cool, g.warm), center)
                // The halo first, then the arc.
                drawIntoCanvas { c ->
                    val p = Paint().asFrameworkPaint().apply {
                        isAntiAlias = true; style = android.graphics.Paint.Style.STROKE; strokeWidth = w; strokeCap = android.graphics.Paint.Cap.ROUND
                        color = android.graphics.Color.TRANSPARENT
                        setShadowLayer(w, 0f, 0f, g.warm.copy(alpha = if (g.dark) 0.5f else 0.25f).toArgb())
                    }
                    c.nativeCanvas.drawArc(tl.x, tl.y, tl.x + d, tl.y + d, -90f, sweep, false, p)
                }
                drawArc(brush, -90f, sweep, false, tl, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

// ================================================================================================================ icons
/** The app's own line icons (24 × 24, 2 dp strokes, round ends); [androidx.compose.material3.Icon] tints them. */
object PfcIcons {
    /** [paths] are SVG path strings or ready path nodes (circles). */
    private fun icon(name: String, vararg paths: Any, width: Float = 2f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                @Suppress("UNCHECKED_CAST")
                val nodes = if (d is String) addPathNodes(d) else d as List<PathNode>
                addPath(nodes, fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            }
        }.build()

    private fun circle(cx: Float, cy: Float, r: Float): List<PathNode> = PathBuilder().apply {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = false, dx1 = 2 * r, dy1 = 0f)
        arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = false, dx1 = -2 * r, dy1 = 0f)
    }.nodes

    val Home = icon("home", "M3.5 10.5L12 3.5l8.5 7V20a.5.5 0 0 1-.5.5h-5v-6h-6v6H4a.5.5 0 0 1-.5-.5z")
    val History = icon("history", "M3.5 12a8.5 8.5 0 1 0 2.6-6.1", "M3.5 4v4h4", "M12 7.5V12l3 2")
    val Progress = icon("progress", "M5 20V12M10 20V6M15 20v-9M20 20V9")
    val Coach = icon("coach", "M20.5 12a8.5 8.5 0 0 1-12.4 7.5L3.5 20.5l1-4.4A8.5 8.5 0 1 1 20.5 12z")
    val Settings = icon("settings", "M4 6h9M18 6h2M4 12h3M12 12h8M4 18h11", circle(15.5f, 6f, 2.2f), circle(9.5f, 12f, 2.2f), circle(17.5f, 18f, 2.2f))
    val Hurts = icon("hurts", "M12 21s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 8a4.3 4.3 0 0 1 7.5 2.8C19.5 16.4 12 21 12 21z", "M12 11v4M10 13h4")
    val Warning = icon("warning", "M12 3.5L21.5 20h-19z", "M12 10v4.5M12 17.2v.3")
    val Time = icon("time", circle(12f, 13f, 8f), "M12 9v4l2.5 1.5M9.5 2.5h5")
    val Swap = icon("swap", "M7 4L3.5 7.5 7 11M3.5 7.5h14M17 13l3.5 3.5L17 20M20.5 16.5h-14")
    val Check = icon("check", "M5 12l5 5L20 7", width = 3f)
    val Play = ImageVector.Builder("play", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(addPathNodes("M8 5.5v13l11-6.5z"), fill = SolidColor(Color.Black))
    }.build()
    val Back = icon("back", "M15 5l-7 7 7 7", width = 2.4f)
    val Steps = icon("steps", "M8 3c1.7 0 2.5 2 2.5 4.5S9.5 12 8 12 5.5 10 5.5 7.5 6.3 3 8 3z", "M6 15h4v1.5a2 2 0 0 1-4 0z",
        "M16 8c1.7 0 2.5 2 2.5 4.5S17.5 17 16 17s-2.5-2-2.5-4.5S14.3 8 16 8z", "M14 20h4v.5a2 2 0 0 1-4 0z")
    val Clock = icon("clock", circle(12f, 12f, 8.5f), "M12 7.5V12l3 2")
    val Flame = icon("flame", "M12 21c-3.9 0-6.5-2.6-6.5-6 0-3.6 3-5.6 3.8-9 2.2 1.4 3.4 3.4 3.5 5.2 1-.8 1.6-2 1.8-3.2 2 1.6 3.9 4.2 3.9 7 0 3.4-2.6 6-6.5 6z")
    val Trophy = icon("trophy", "M8 4h8v5a4 4 0 0 1-8 0z", "M8 6H4.5a3 3 0 0 0 3.5 4M16 6h3.5a3 3 0 0 1-3.5 4M12 13v4M8.5 20.5h7M10 17h4")
    val Spark = icon("spark", "M12 3v4M12 17v4M3 12h4M17 12h4M6.3 6.3l2.5 2.5M15.2 15.2l2.5 2.5M6.3 17.7l2.5-2.5M15.2 8.8l2.5-2.5")
    val Share = icon("share", "M4 12v7.5h16V12M12 3.5v11M7.5 8L12 3.5 16.5 8")
    val Up = icon("up", "M12 19V5M6 11l6-6 6 6", width = 2.8f)
}
