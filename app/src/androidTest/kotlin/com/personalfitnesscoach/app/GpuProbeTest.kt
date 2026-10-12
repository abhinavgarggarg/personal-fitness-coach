package com.personalfitnesscoach.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.personalfitnesscoach.app.ui.ExerciseFigure
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/** Temporary probe: which drawing operation takes down the API 34/36 emulators. Tests run in name order; the last one to pass names the op before the crash. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GpuProbeTest {
    @get:Rule val compose = createComposeRule()

    private fun show(block: DrawScope.() -> Unit) = show { Canvas(Modifier.size(320.dp)) { block() } }
    private fun show(content: @Composable () -> Unit) {
        compose.setContent(content)
        compose.waitForIdle(); Thread.sleep(800); compose.waitForIdle()
    }
    private fun DrawScope.blob() = Path().apply { moveTo(40f, 200f); cubicTo(60f, 40f, 240f, 40f, 280f, 200f); cubicTo(240f, 330f, 80f, 330f, 40f, 200f); close() }
    private val stops = arrayOf(0f to Color.White, 0.3f to Color(0xFFE7ECF4), 0.64f to Color(0xFFAAB8CD), 0.88f to Color(0xFF72849F), 1f to Color(0xFFE9814F))

    @Test fun t01_pathLinearGradient() = show { drawPath(blob(), Brush.linearGradient(*stops, start = Offset(60f, 60f), end = Offset(260f, 300f))) }
    @Test fun t02_pathStroke() = show { drawPath(blob(), Color(0x73080C16), style = Stroke(0.8.dp.toPx())) }
    @Test fun t03_pathRadialAlpha() = show {
        drawPath(blob(), Brush.radialGradient(0f to Color(0xCCFF6B2C), 0.55f to Color(0x73FF6B2C), 1f to Color(0x00FF6B2C), center = Offset(160f, 180f), radius = 120f), alpha = 0.8f)
    }
    @Test fun t04_ovalRadial() = show { drawOval(Brush.radialGradient(*stops, center = Offset(150f, 150f), radius = 90f), Offset(100f, 100f), Size(110f, 120f)) }
    @Test fun t05_circleRadial() = show { drawCircle(Brush.radialGradient(*stops, center = Offset(150f, 150f), radius = 90f), 60f, Offset(160f, 160f)) }
    @Test fun t06_scaledShadow() = show {
        scale(1f, 0.12f, Offset(160f, 300f)) { drawCircle(Brush.radialGradient(0f to Color(0x80000000), 1f to Color.Transparent, center = Offset(160f, 300f), radius = 120f), 120f, Offset(160f, 300f)) }
    }
    @Test fun t07_verticalGradientBox() = show {
        val p = Path().apply { moveTo(20f, 220f); lineTo(300f, 220f); lineTo(300f, 300f); lineTo(20f, 300f); close() }
        drawPath(p, Brush.verticalGradient(0f to Color(0xFF34496B), 0.25f to Color(0xFF26364F), 1f to Color(0xFF1A2538), startY = 220f, endY = 300f))
    }
    @Test fun t08_wideHaloLines() = show { repeat(14) { drawLine(Color(0x12FF6B2C), Offset(40f + it * 10, 60f), Offset(60f + it * 10, 280f), strokeWidth = 90f, cap = StrokeCap.Round) } }
    @Test fun t09_figureSide() = show { ExerciseFigure("back-squat", "probe", Modifier.size(320.dp)) }
    @Test fun t10_figureFront() = show { ExerciseFigure("db-lateral-raise", "probe", Modifier.size(320.dp)) }
}
