package com.personalfitnesscoach.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.personalfitnesscoach.app.flow.tab
import com.personalfitnesscoach.app.flow.restEndsAtMs
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
 * [fromSystem] is for what the phone hands back — a file picker's or permission dialog's result, the return to the foreground: never
 * dropped and never blocking a tap (re-check N4: the picker's result arrived while the foreground refresh was running and was lost).
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class Actions(scope: CoroutineScope, val controller: AppController, dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
              private val doubleTapMs: Long = DOUBLE_TAP_MS) {
    /** One call after another, in order (a single-thread view of the default pool; tests pass the main thread). */
    private val ordered = CoroutineScope(scope.coroutineContext + dispatcher)
    private val pending = java.util.concurrent.atomic.AtomicBoolean(false)
    /** When the last tap finished (uptime); far in the past at first, so the first tap is never taken for a double tap. */
    @Volatile private var lastDoneMs = Long.MIN_VALUE / 2

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

    /** A result from the system or the app's lifecycle: always run, in order, outside the double-tap guard. */
    fun fromSystem(block: suspend AppController.() -> Unit) {
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
    // The end-of-rest alarm follows the workout's rest (A2), also under a "Why?" opened from it; any other screen leaves no alarm behind.
    val restEnd = screen.restEndsAtMs()
    LaunchedEffect(restEnd) {
        if (restEnd != null && restEnd > System.currentTimeMillis()) RestAlerts.schedule(context, restEnd) else RestAlerts.cancel(context)
    }

    CompositionLocalProvider(LocalBusy provides busy, LocalActions provides actions) {
        // D-099: every screen sits on the Glow background; the tab bar floats over it.
        GlowBackground(Modifier.fillMaxSize()) {
        Scaffold(snackbarHost = { SnackbarHost(snack) }, bottomBar = { screen.tab()?.let { TabBar(it) } },
            containerColor = androidx.compose.ui.graphics.Color.Transparent, contentColor = Glass.text) { padding ->
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
                    is Screen.History -> com.personalfitnesscoach.app.ui.history.HistoryScreen(s)
                    is Screen.SessionDetail -> com.personalfitnesscoach.app.ui.history.SessionDetailScreen(s)
                    is Screen.Progress -> com.personalfitnesscoach.app.ui.progress.ProgressScreen(s)
                    is Screen.ExerciseDetail -> com.personalfitnesscoach.app.ui.progress.ExerciseDetailScreen(s)
                    is Screen.Coach -> com.personalfitnesscoach.app.ui.coach.CoachScreen(s.model)
                    is Screen.EditProfile -> com.personalfitnesscoach.app.ui.settings.EditProfileScreen(s)
                }
            }
        }
        }
    }
}

/**
 * The four tabs (Phase 2 section 5): Today, History, Progress, Coach, as icons on a floating glass bar (D-099). Each icon is a 56 dp
 * target with its spoken name; the current one is marked (warm, with a glowing bar) and read as selected by TalkBack.
 */
@Composable
private fun TabBar(current: com.personalfitnesscoach.app.flow.Tab) {
    val a = LocalActions.current
    val busy = LocalBusy.current
    val g = Glass
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 4.dp)
            .background(g.bar, PillShape).border(1.dp, g.glassBorder, PillShape).heightIn(min = 68.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.personalfitnesscoach.app.flow.Tab.entries.forEach { tab ->
            val (label, icon) = when (tab) {
                com.personalfitnesscoach.app.flow.Tab.TODAY -> stringResource(R.string.tab_today) to PfcIcons.Home
                com.personalfitnesscoach.app.flow.Tab.HISTORY -> stringResource(R.string.tab_history) to PfcIcons.History
                com.personalfitnesscoach.app.flow.Tab.PROGRESS -> stringResource(R.string.tab_progress) to PfcIcons.Progress
                com.personalfitnesscoach.app.flow.Tab.COACH -> stringResource(R.string.tab_coach) to PfcIcons.Coach
            }
            val selected = tab == current
            Column(
                Modifier.weight(1f).heightIn(min = PrimaryHeight)
                    .clickable(enabled = !busy, role = androidx.compose.ui.semantics.Role.Tab) { if (!selected) a.run { openTab(tab) } }
                    .semantics { this.selected = selected; contentDescription = label },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                androidx.compose.material3.Icon(icon, contentDescription = null, tint = if (selected) g.warm else g.muted, modifier = Modifier.size(26.dp))
                if (selected) Box(Modifier.padding(top = 6.dp).size(width = 18.dp, height = 3.dp).glow(g.warm, radius = 8.dp, corner = 2.dp)
                    .background(g.warm, PillShape))
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
