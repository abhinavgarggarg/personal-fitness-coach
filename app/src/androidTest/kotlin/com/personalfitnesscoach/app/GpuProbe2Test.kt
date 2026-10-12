package com.personalfitnesscoach.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.personalfitnesscoach.app.figure.ExerciseFigures
import com.personalfitnesscoach.app.figure.Outline
import com.personalfitnesscoach.app.figure.P
import com.personalfitnesscoach.app.figure.Sculpt
import com.personalfitnesscoach.app.figure.Sculptor
import com.personalfitnesscoach.app.figure.BodyPart
import com.personalfitnesscoach.engine.library.Library
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/** Temporary probe, part 2: which part of the sculpted back squat stops the emulator. Name order; each draws one kind of thing. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GpuProbe2Test {
    @get:Rule val compose = createComposeRule()
    private val fig = ExerciseFigures.of("back-squat")!!
    private val frame = Sculptor.sculpt(fig, fig.still, Library["back-squat"]!!.primary)
    private val cam = Sculptor.camera(fig)

    private fun draw(block: DrawScope.(o: (P) -> Offset, scale: Float, path: (Outline) -> Path) -> Unit) {
        compose.setContent {
            Canvas(Modifier.size(320.dp)) {
                val pad = size.minDimension * 0.08f
                val scale = minOf((size.width - 2 * pad) / cam.width.toFloat(), (size.height - 2 * pad) / cam.height.toFloat())
                val ox = pad + (size.width - 2 * pad - cam.width.toFloat() * scale) / 2 - cam.minX.toFloat() * scale
                val floorY = pad + (size.height - 2 * pad - cam.height.toFloat() * scale) / 2 + cam.maxY.toFloat() * scale
                val o = { p: P -> Offset(ox + p.x.toFloat() * scale, floorY - p.y.toFloat() * scale) }
                val path = { outline: Outline ->
                    Path().apply {
                        o(outline.start).let { moveTo(it.x, it.y) }
                        for (c in outline.curves) { val a = o(c.c1); val b = o(c.c2); val e = o(c.end); cubicTo(a.x, a.y, b.x, b.y, e.x, e.y) }
                        close()
                    }
                }
                block(o, scale, path)
            }
        }
        compose.waitForIdle(); Thread.sleep(800); compose.waitForIdle()
    }
    private val parts get() = frame.items.filterIsInstance<Sculpt.Part>()

    @Test fun p1_balls() = draw { o, scale, _ ->
        for (b in frame.items.filterIsInstance<Sculpt.Ball>()) {
            val c = o(b.c); val rx = (b.rx * scale).toFloat(); val ry = (b.ry * scale).toFloat()
            drawOval(Brush.radialGradient(0f to Color.White, 1f to Color.Gray, center = c, radius = rx * 1.45f), Offset(c.x - rx, c.y - ry), Size(2 * rx, 2 * ry))
        }
    }
    @Test fun p2_halo() = draw { o, scale, _ ->
        for (s in parts) drawLine(Color(0x12FF6B2C), o(s.from), o(s.to), strokeWidth = (2 * s.r * scale).toFloat() + 16.dp.toPx(), cap = StrokeCap.Round)
    }
    @Test fun p3_trunkSolid() = draw { _, _, path -> drawPath(path(parts.first { it.part == BodyPart.TRUNK }.outline), Color.White) }
    @Test fun p4_limbsSolid() = draw { _, _, path -> for (s in parts) if (s.part != BodyPart.TRUNK) drawPath(path(s.outline), Color.White) }
    @Test fun p5_partsGradient() = draw { o, _, path ->
        for (s in parts) drawPath(path(s.outline), Brush.linearGradient(0f to Color.White, 1f to Color.Gray, start = o(s.lit), end = o(s.shade)))
    }
    @Test fun p6_partsEdges() = draw { _, _, path -> for (s in parts) drawPath(path(s.outline), Color.Black, style = Stroke(0.8.dp.toPx())) }
    @Test fun p7_glows() = draw { o, scale, path ->
        for (s in parts) for (g in s.glows) drawPath(path(s.outline), Brush.radialGradient(0f to Color(0xCCFF6B2C), 1f to Color(0x00FF6B2C),
            center = o(g.c), radius = (g.r * scale).toFloat()), alpha = 0.9f)
    }
}
