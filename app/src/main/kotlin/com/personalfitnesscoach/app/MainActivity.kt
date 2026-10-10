package com.personalfitnesscoach.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.personalfitnesscoach.app.flow.Screen
import com.personalfitnesscoach.app.platform.RestAlerts
import com.personalfitnesscoach.app.ui.PfcApp
import com.personalfitnesscoach.app.ui.theme.PfcTheme

/** The one screen of the app (Phase 2 section 5): Compose draws everything; the controller decides what is shown. */
class MainActivity : ComponentActivity() {
    private val app: PfcApplication get() = application as PfcApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PfcTheme { PfcApp(app.actions) } }
    }

    override fun onStart() {
        super.onStart()
        RestAlerts.appVisible = true
        RestAlerts.hideCountdown(this)
        // Back in the foreground: finished weeks roll over and steps are read (D-078). The first start is handled by the screen.
        if (app.controller.screen.value != Screen.Loading) app.actions.run { onForeground() }
    }

    override fun onStop() {
        RestAlerts.appVisible = false
        // Hidden during a rest: the countdown shows in a notification until the alarm buzzes.
        val s = app.controller.screen.value
        val end = (s as? Screen.Workout)?.view?.restEndsAtMs
        if (end != null && end > System.currentTimeMillis()) RestAlerts.showCountdown(this, end)
        super.onStop()
    }
}
