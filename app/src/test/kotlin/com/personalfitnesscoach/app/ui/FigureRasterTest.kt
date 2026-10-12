package com.personalfitnesscoach.app.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.personalfitnesscoach.app.figure.ExerciseFigures
import com.personalfitnesscoach.app.figure.FrontFigure
import com.personalfitnesscoach.app.figure.Sculptor
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.Muscle
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * CR-008 (D-105): the sculpted figure drawn into a picture on the processor, as the app does: it draws a body, the working muscles
 * show in the warm colour (and not without them), and a picture the size of the workout card is quick to draw.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FigureRasterTest {
    private val w = 900; private val h = 690   // the workout card's figure on a 2.75× phone

    private fun picture(id: String, t: Double, muscles: Set<Muscle>, bitmap: ImageBitmap = ImageBitmap(w, h)): ImageBitmap {
        val fig = ExerciseFigures.of(id)!!
        val camera = Sculptor.camera(fig)
        bitmap.asAndroidBitmap().eraseColor(0)
        CanvasDrawScope().draw(Density(2.75f), LayoutDirection.Ltr, Canvas(bitmap), Size(w.toFloat(), h.toFloat())) {
            drawSculpt(Sculptor.sculpt(fig.frame(t), fig is FrontFigure, t, muscles), camera, false)
        }
        return bitmap
    }

    private fun count(b: ImageBitmap, test: (r: Int, g: Int, bl: Int, a: Int) -> Boolean): Int {
        val px = IntArray(w * h); b.asAndroidBitmap().getPixels(px, 0, w, 0, 0, w, h)
        return px.count { c -> test((c shr 16) and 255, (c shr 8) and 255, c and 255, (c ushr 24) and 255) }
    }

    /** Clearly orange: red well above blue, solidly drawn. */
    private fun warm(r: Int, g: Int, b: Int, a: Int) = a > 200 && r > 200 && r - b > 90 && g in 80..200

    @Test
    fun theFigureIsDrawnWithItsWorkingMusclesLit() {
        for (id in listOf("back-squat", "db-lateral-raise", "bench-press")) {
            val primary = Library[id]!!.primary
            val lit = picture(id, 1.0, primary)
            val body = count(lit) { r, g, b, a -> a > 200 && r > 200 && g > 200 && b > 200 }
            assertTrue("$id: a light body is drawn ($body pixels)", body > 5_000)
            val glowing = count(lit) { r, g, b, a -> warm(r, g, b, a) }
            assertTrue("$id: the working muscles glow ($glowing warm pixels)", glowing > 2_000)
            val plain = count(picture(id, 1.0, emptySet())) { r, g, b, a -> warm(r, g, b, a) }
            assertTrue("$id: without muscles, far less warm colour ($plain vs $glowing)", plain * 2 < glowing)
        }
    }

    @Test
    fun aPictureIsQuickToDraw() {
        val b = ImageBitmap(w, h)
        val primary = Library["back-squat"]!!.primary
        repeat(10) { picture("back-squat", it / 10.0, primary, b) }   // warm up
        val n = 60
        val start = System.nanoTime()
        for (i in 0 until n) picture("back-squat", i.toDouble() / n, primary, b)
        val ms = (System.nanoTime() - start) / 1e6 / n
        println("METRIC sculpted figure, 900 x 690 picture: %.1f ms each".format(ms))
        // A generous bound for a shared test machine: the point is to catch a drawing that became many times slower.
        assertTrue("a picture took %.1f ms".format(ms), ms < 40)
    }
}
