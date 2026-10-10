package com.personalfitnesscoach.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.personalfitnesscoach.R
import com.personalfitnesscoach.app.flow.AppController
import com.personalfitnesscoach.app.flow.Screen
import com.personalfitnesscoach.app.platform.RestAlerts
import com.personalfitnesscoach.app.ui.onboarding.OnboardingScreen
import com.personalfitnesscoach.app.ui.settings.SettingsScreen
import com.personalfitnesscoach.app.ui.today.CheckInScreen
import com.personalfitnesscoach.app.ui.today.PreviewScreen
import com.personalfitnesscoach.app.ui.today.TodayScreen
import com.personalfitnesscoach.app.ui.workout.DoneScreen
import com.personalfitnesscoach.app.ui.workout.ResumeScreen
import com.personalfitnesscoach.app.ui.workout.StopScreen
import com.personalfitnesscoach.app.ui.workout.WorkoutScreen
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Every tap goes to the controller on the app's own scope (not the screen's), so turning the phone or leaving the screen never cancels a
 * half-handled tap. Calls are dispatched one after another in the order they were made, and the controller runs them one at a time, each
 * change in one transaction.
 *
 * [run] is for buttons: a second tap while one is still being handled is dropped (a fast double tap never logs a set twice, NFR-03).
 * [edit] is for typing and ticking answers: every change is kept, in order.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class Actions(scope: CoroutineScope, val controller: AppController, dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
              private val doubleTapMs: Long = DOUBLE_TAP_MS) {
    /** One call after another, in order (a single-thread view of the default pool; tests pass the main thread). */
    private val ordered = CoroutineScope(scope.coroutineContext + dispatcher)
    private val pending = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var lastDoneMs = 0L

    fun run(block: suspend AppController.() -> Unit) {
        // A second tap within a moment of the last one finishing is a double tap on what is now a different button (R5-10).
        if (doubleTapMs > 0 && android.os.SystemClock.uptimeMillis() - lastDoneMs < doubleTapMs) return
        if (!pending.compareAndSet(false, true)) return
        ordered.launch {
            try { controller.block() } finally { lastDoneMs = android.os.SystemClock.uptimeMillis(); pending.set(false) }
        }
    }

    companion object {
        const val DOUBLE_TAP_MS = 350L
    }

    fun edit(block: suspend AppController.() -> Unit) {
        ordered.launch { controller.block() }
    }
}

val LocalActions = staticCompositionLocalOf<Actions> { error("no actions") }

/** The app: one screen at a time, as the controller publishes it (Phase 2 section 5). */
@Composable
fun PfcApp(actions: Actions) {
    val c = actions.controller
    val screen by c.screen.collectAsState()
    val busy by c.busy.collectAsState()
    val error by c.error.collectAsState()
    val snack = remember { SnackbarHostState() }
    val context = LocalContext.current
    // A short, plain message (COACH-001, NFR-16); the technical detail stays out of the screen.
    val errorText = error?.let { stringResource(R.string.error_action) }

    LaunchedEffect(Unit) { if (c.screen.value == Screen.Loading) actions.run { start() } }
    LaunchedEffect(errorText) {
        if (errorText != null) {
            snack.showSnackbar(errorText)
            c.clearError()
        }
    }
    // The end-of-rest alarm follows the workout's rest (A2); any other screen leaves no alarm behind.
    val restEnd = (screen as? Screen.Workout)?.view?.restEndsAtMs
    LaunchedEffect(restEnd) {
        if (restEnd != null && restEnd > System.currentTimeMillis()) RestAlerts.schedule(context, restEnd) else RestAlerts.cancel(context)
    }

    CompositionLocalProvider(LocalBusy provides busy, LocalActions provides actions) {
        Scaffold(snackbarHost = { SnackbarHost(snack) }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
                when (val s = screen) {
                    Screen.Loading -> LoadingScreen()
                    is Screen.Failed -> FailedScreen(s.detail)
                    is Screen.Onboarding -> OnboardingScreen(s)
                    is Screen.Today -> TodayScreen(s.model)
                    is Screen.CheckIn -> CheckInScreen(s)
                    is Screen.Preview -> PreviewScreen(s.model)
                    is Screen.Resume -> ResumeScreen(s.view)
                    is Screen.Workout -> WorkoutScreen(s)
                    is Screen.Done -> DoneScreen(s)
                    is Screen.Stop -> StopScreen(s.stop)
                    is Screen.Settings -> SettingsScreen(s.model)
                    is Screen.PainDay -> com.personalfitnesscoach.app.ui.workout.PainDayScreen(s.outcome)
                    is Screen.EditConditions -> com.personalfitnesscoach.app.ui.settings.EditConditionsScreen(s)
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** Phase 2 ERROR_SAFE: nothing is written; a retry is offered. */
@Composable
private fun FailedScreen(detail: String) {
    val a = LocalActions.current
    ScreenColumn(stringResource(R.string.failed_title)) {
        Body(stringResource(R.string.failed_body))
        Note(stringResource(R.string.failed_detail, detail))
        PrimaryButton(stringResource(R.string.action_retry), { a.run { start() } })
    }
}
