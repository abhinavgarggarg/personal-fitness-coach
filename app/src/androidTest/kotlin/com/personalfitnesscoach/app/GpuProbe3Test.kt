package com.personalfitnesscoach.app

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.personalfitnesscoach.app.figure.ExerciseFigures
import com.personalfitnesscoach.app.figure.FrontFigure
import com.personalfitnesscoach.app.figure.Sculptor
import com.personalfitnesscoach.app.ui.ExerciseFigure
import com.personalfitnesscoach.app.ui.drawSculpt
import com.personalfitnesscoach.engine.library.Library
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/** Temporary probe, part 3: is it the full drawing, or the moving figure? */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GpuProbe3Test {
    @get:Rule val compose = createComposeRule()

    @Test fun r1_animatorScaleIsZero() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("animator duration scale", 0f, Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f), 0f)
    }
    @Test fun r2_fullDrawingStill() {
        val fig = ExerciseFigures.of("back-squat")!!
        val cam = Sculptor.camera(fig)
        val frame = Sculptor.sculpt(fig.frame(fig.still), fig is FrontFigure, fig.still, Library["back-squat"]!!.primary)
        compose.setContent { Canvas(Modifier.size(320.dp)) { drawSculpt(frame, cam, false) } }
        compose.waitForIdle(); Thread.sleep(1500); compose.waitForIdle()
    }
    @Test fun r3_fullDrawingTwelveFrames() {
        val fig = ExerciseFigures.of("back-squat")!!
        val cam = Sculptor.camera(fig)
        for (i in 0..12) {
            val t = i / 12.0
            val frame = Sculptor.sculpt(fig.frame(t), false, t, Library["back-squat"]!!.primary)
            compose.setContent { Canvas(Modifier.size(320.dp)) { drawSculpt(frame, cam, false) } }
            compose.waitForIdle()
        }
        Thread.sleep(1000)
    }
    @Test fun r4_exerciseFigure() {
        compose.setContent { ExerciseFigure("back-squat", "probe", Modifier.size(320.dp)) }
        compose.waitForIdle(); Thread.sleep(3000); compose.waitForIdle()
    }
}
