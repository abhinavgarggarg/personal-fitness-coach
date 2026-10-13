@file:OptIn(ExperimentalLayoutApi::class)

package com.personalfitnesscoach.app.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personalfitnesscoach.R
import com.personalfitnesscoach.app.flow.Screen
import com.personalfitnesscoach.app.flow.WorkoutNotice
import com.personalfitnesscoach.app.platform.RestAlerts
import com.personalfitnesscoach.app.ui.Body
import com.personalfitnesscoach.app.ui.ChipShape
import com.personalfitnesscoach.app.ui.ExerciseFigure
import com.personalfitnesscoach.app.ui.Glass
import com.personalfitnesscoach.app.ui.GlassCard
import com.personalfitnesscoach.app.ui.PfcIcons
import com.personalfitnesscoach.app.ui.ProgressRing
import com.personalfitnesscoach.app.ui.StudioCard
import com.personalfitnesscoach.app.ui.TIMES
import com.personalfitnesscoach.app.ui.TonalPill
import com.personalfitnesscoach.app.ui.glass
import com.personalfitnesscoach.app.ui.Heading
import com.personalfitnesscoach.app.ui.InfoCard
import com.personalfitnesscoach.app.ui.LocalActions
import com.personalfitnesscoach.app.ui.LocalBusy
import com.personalfitnesscoach.app.ui.Note
import com.personalfitnesscoach.app.ui.NumberField
import com.personalfitnesscoach.app.ui.PrimaryButton
import com.personalfitnesscoach.app.ui.PrimaryHeight
import com.personalfitnesscoach.app.ui.ScreenColumn
import com.personalfitnesscoach.app.ui.SecondaryButton
import com.personalfitnesscoach.app.ui.SecondaryHeight
import com.personalfitnesscoach.app.ui.SectionTitle
import com.personalfitnesscoach.app.ui.SmallAction
import com.personalfitnesscoach.app.ui.Tone
import com.personalfitnesscoach.app.ui.kg
import com.personalfitnesscoach.app.ui.minutes
import com.personalfitnesscoach.app.ui.text.ReasonTexts
import com.personalfitnesscoach.app.ui.theme.Numerals
import com.personalfitnesscoach.app.ui.theme.NumeralsSmall
import com.personalfitnesscoach.app.ui.theme.grownAtMost
import com.personalfitnesscoach.data.core.player.LiftEntry
import com.personalfitnesscoach.data.core.player.LiftView
import com.personalfitnesscoach.data.core.player.PlayerView
import com.personalfitnesscoach.data.core.player.SetTarget
import com.personalfitnesscoach.data.core.player.Step
import com.personalfitnesscoach.data.core.session.DrillRef
import com.personalfitnesscoach.data.core.session.SetKind
import com.personalfitnesscoach.data.core.session.Stage
import com.personalfitnesscoach.data.core.session.Status
import com.personalfitnesscoach.engine.dose.Effort
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.FormCheck
import com.personalfitnesscoach.engine.safety.AdditionVerdict
import com.personalfitnesscoach.engine.safety.BoneLoadingVariant
import kotlinx.coroutines.delay

/**
 * Workout mode (Phase 2 A1–A8): no tabs, one thing at a time — the current set, the rest timer, or one sheet. "Something hurts",
 * "Warning signs" and "Change time" stay one tap away. The screen stays on while a workout is open.
 */
@Composable
fun WorkoutScreen(s: Screen.Workout) {
    val a = LocalActions.current
    val v = s.view
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    BackHandler { if (s.sheet != null) a.run { closeSheet() } else a.run { openEnd() } }
    // The rest's full length, kept here so the ring doesn't refill after ±30 s or after a sheet was open (review R7-13).
    val restLength = androidx.compose.runtime.saveable.rememberSaveable(saver = RestLength.SAVER) { RestLength() }
    v.restEndsAtMs?.let { restLength.follow(it, v.lifts.sumOf { l -> l.sets.size }) }
    val sheet = s.sheet
    if (sheet != null) {
        WorkoutSheetPanel(v, sheet)
        return
    }
    // The safety controls sit in a bar that never scrolls away (A4/A5 "always one tap away", R5-11).
    Column(Modifier.fillMaxSize()) {
    ScreenColumn(null, Modifier.weight(1f)) {
        TopRow(v)
        if (v.paused) {
            InfoCard(stringResource(R.string.wk_paused), Tone.WARN) { PrimaryButton(stringResource(R.string.wk_resume), { a.run { resume() } }) }
        }
        s.notice?.let { Notice(it, v) }
        v.restEndsAtMs?.let { end -> RestPanel(end, v, restLength.totalMs) }
        when (val step = v.step) {
            is Step.Warmup -> StagePanel(stringResource(R.string.wk_warmup_title), stringResource(R.string.wk_warmup_body, minutes(step.minutes)), step.drills,
                Stage.WARMUP)
            is Step.BoneLoading -> {
                val text = if (step.block.variant == BoneLoadingVariant.HEEL_DROPS) stringResource(R.string.wk_bone_heel_drops, step.block.landings)
                    else stringResource(R.string.wk_bone_hops, step.block.landings)
                StagePanel(stringResource(R.string.wk_bone_title), text, emptyList(), Stage.BONE_LOADING)
            }
            is Step.Lift -> LiftPanel(step.lift, v)
            is Step.Conditioning -> ConditioningPanel(step.index, v)
            is Step.Balance -> StagePanel(stringResource(R.string.wk_balance_title), stringResource(R.string.wk_balance_body, minutes(step.minutes)), step.drills,
                Stage.BALANCE)
            is Step.Cooldown -> StagePanel(stringResource(R.string.wk_cooldown_title), stringResource(R.string.wk_cooldown_body, minutes(step.minutes)) +
                if (step.mobilityMinutes > 0) " " + stringResource(R.string.wk_mobility, minutes(step.mobilityMinutes)) else "", step.drills, Stage.COOLDOWN)
            Step.Done -> {
                InfoCard(stringResource(R.string.wk_all_done), Tone.GOOD)
                PrimaryButton(stringResource(R.string.wk_finish), { a.run { finishWorkout(early = false) } })
            }
        }
        if (v.lifts.isNotEmpty()) ExerciseList(v)
    }
    SafetyRow(v)
    }
}

@Composable
private fun TopRow(v: PlayerView) {
    val a = LocalActions.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Note(stringResource(R.string.wk_progress, Math.round(v.elapsedMinutes).toInt(), Math.round(v.remainingMinutes).toInt()), Modifier.weight(1f))
        if (v.paused) SmallAction(stringResource(R.string.wk_resume), { a.run { resume() } })
        else SmallAction(stringResource(R.string.wk_pause), { a.run { pause() } })
        SmallAction(stringResource(R.string.wk_end), { a.run { openEnd() } })
    }
}

/** "Something hurts" and "Warning signs" are always one tap away (Phase 2 A4, A5); "Change time" re-fits the rest (A6). */
@Composable
private fun SafetyRow(v: PlayerView) {
    val a = LocalActions.current
    // As many per line as keep every word whole: three side by side (icon above the words) when the longest word fits, else two, else
    // one per line; measured on this phone at its text size, so a narrow phone or large text never breaks a word (R7-11, re-check N2).
    val labels = listOf(stringResource(R.string.wk_hurts), stringResource(R.string.wk_red_flag), stringResource(R.string.wk_change_time))
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val longest = remember(labels, style, density) {
        labels.flatMap { it.split(' ') }.maxOf { w -> measurer.measure(w, style).size.width }.let { with(density) { it.toDp() } }
    }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        // Each pill's room for words: its share of the width, less the gaps (8 dp) and its own padding (6 dp a side).
        val perLine = when {
            longest <= (maxWidth - 16.dp) / 3 - 12.dp -> 3
            longest <= (maxWidth - 8.dp) / 2 - 12.dp -> 2
            else -> 1
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = perLine) {
            val current = (v.step as? Step.Lift)?.lift?.rowId
            val stacked = perLine > 1
            TonalPill(labels[0], { a.run { openHurts(current) } }, Modifier.weight(1f), PfcIcons.Hurts, Tone.WARN, stacked = stacked)
            TonalPill(labels[1], { a.run { openRedFlag() } }, Modifier.weight(1f), PfcIcons.Warning, Tone.ALERT, stacked = stacked)
            TonalPill(labels[2], { a.run { openChangeTime() } }, Modifier.weight(1f), PfcIcons.Time, stacked = stacked)
        }
    }
}

@Composable
private fun Notice(n: WorkoutNotice, v: PlayerView) {
    when (n) {
        is WorkoutNotice.LoadChanged -> InfoCard(stringResource(ReasonTexts.of(n.decision.reason))) {
            val a = LocalActions.current
            com.personalfitnesscoach.app.ui.SmallAction(stringResource(R.string.action_why), { a.run { openWhy(n.decision.reason.name, n.decision.ruleIds) } })
        }
        is WorkoutNotice.Replanned -> InfoCard(stringResource(R.string.wk_changes)) {
            if (n.changes.isEmpty()) Text(stringResource(R.string.wk_no_changes))
            n.changes.forEach { c -> Text(changeText(c, v.conditioning)) }
            // D-071: what can't be cut stays, and the user is told how short the session can be from here (re-check N7).
            n.shortestSafeMinutes?.let { Text(stringResource(R.string.wk_shortest_safe, it)) }
        }
        is WorkoutNotice.Refused -> InfoCard(stringResource(R.string.wk_refused), Tone.WARN)
        is WorkoutNotice.AddSet -> InfoCard(stringResource(if (n.verdict == AdditionVerdict.BLOCKED) R.string.addset_blocked else R.string.addset_ok),
            if (n.verdict == AdditionVerdict.BLOCKED) Tone.WARN else Tone.GOOD)
    }
}

// ============================================================================================================ rest (A2)
/** The rest countdown: ±30 s and skip; when it ends the phone buzzes and the next set is shown. Read out by TalkBack as it changes. */
@Composable
private fun RestPanel(endsAtMs: Long, v: PlayerView, totalMs: Long) {
    val a = LocalActions.current
    val context = LocalContext.current
    // Counted on the phone's elapsed-time clock from the moment the rest is shown (Phase 2 section 11): changing the time of day never
    // changes a rest (R5-19).
    val startLeft = remember(endsAtMs) { endsAtMs - System.currentTimeMillis() }
    val startElapsed = remember(endsAtMs) { android.os.SystemClock.elapsedRealtime() }
    var leftMs by remember(endsAtMs) { mutableLongStateOf(startLeft) }
    LaunchedEffect(endsAtMs) {
        while (true) {
            leftMs = startLeft - (android.os.SystemClock.elapsedRealtime() - startElapsed)
            if (leftMs <= 0) {
                RestAlerts.vibrate(context)
                break
            }
            delay(250)
        }
    }
    val next = (v.step as? Step.Lift)?.lift?.exercise?.name
    RestCountdown(leftMs, totalMs, next, { a.run { adjustRest(-30) } }, { a.run { adjustRest(30) } }, { a.run { skipRest() } })
}

/**
 * How long the current rest is in all: set when a rest starts (a new set was logged), and moved by exactly as much as the end moves
 * when the user adds or takes away 30 s. Plain bookkeeping, not screen state.
 */
private class RestLength(private var setsLogged: Int = -1, private var endsAtMs: Long = 0L, total: Long = 1L) {
    var totalMs = total
        private set

    companion object {
        /** Kept across turning the phone (the screen is rebuilt; re-check N3). */
        val SAVER = androidx.compose.runtime.saveable.Saver<RestLength, LongArray>(
            save = { longArrayOf(it.setsLogged.toLong(), it.endsAtMs, it.totalMs) },
            restore = { RestLength(it[0].toInt(), it[1], it[2]) },
        )
    }

    fun follow(endsAt: Long, sets: Int) {
        if (sets != setsLogged) {
            setsLogged = sets
            endsAtMs = endsAt
            totalMs = (endsAt - System.currentTimeMillis()).coerceAtLeast(1)
        } else if (endsAt != endsAtMs) {
            totalMs = (totalMs + (endsAt - endsAtMs)).coerceAtLeast(1)
            endsAtMs = endsAt
        }
    }
}

/** The rest as a ring that empties as it runs down, the time in big numbers, ±30 s and skip; "Rest over" when it ends. */
@Composable
internal fun RestCountdown(leftMs: Long, totalMs: Long, next: String?, onMinus: () -> Unit, onPlus: () -> Unit, onSkip: () -> Unit) {
    val left = ((leftMs + 999) / 1000).coerceAtLeast(0)
    GlassCard {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (left > 0) {
                Text(stringResource(R.string.wk_rest_title), style = MaterialTheme.typography.titleMedium, color = Glass.muted)
                // The number stays the size of the ring at any text size (it is already huge); everything else grows with the text.
                ProgressRing((leftMs.toFloat() / totalMs.coerceAtLeast(1)).coerceIn(0f, 1f), 216.dp, 14.dp) {
                    // Not a live region: TalkBack would read every second (R5-18); the end of the rest is announced instead.
                    Text(stringResource(R.string.wk_rest_left, (left / 60).toInt(), (left % 60).toInt()),
                        style = Numerals.copy(fontSize = 60.sp, lineHeight = 64.sp).grownAtMost(1f), color = Glass.text, maxLines = 1)
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 3) {
                    TonalPill(stringResource(R.string.wk_rest_minus), onMinus, Modifier.weight(1f))
                    TonalPill(stringResource(R.string.wk_rest_plus), onPlus, Modifier.weight(1f))
                    TonalPill(stringResource(R.string.wk_rest_skip), onSkip, Modifier.weight(1f))
                }
            } else {
                Text(stringResource(R.string.wk_rest_over), style = MaterialTheme.typography.titleLarge, color = Glass.cool,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
            }
            next?.let { Note(stringResource(R.string.wk_next, it)) }
        }
    }
}

// ============================================================================================================ stages
@Composable
private fun StagePanel(title: String, body: String, drills: List<DrillRef>, stage: Stage) {
    val a = LocalActions.current
    Heading(title)
    Body(body)
    drills.forEach { d ->
        val drill = com.personalfitnesscoach.engine.library.Library.drill(d.id)
        val name = drill?.name ?: exerciseName(d.id)
        Note(if (drill?.unit == DoseUnit.SECONDS) stringResource(R.string.wk_drill_secs, name, d.sets, d.amount)
            else stringResource(R.string.wk_drill_reps, name, d.sets, d.amount))
    }
    PrimaryButton(stringResource(R.string.wk_stage_next), { a.run { completeStage(stage) } })
}

@Composable
private fun ConditioningPanel(index: Int, v: PlayerView) {
    val a = LocalActions.current
    val c = v.conditioning[index]
    Heading(modalityName(c.modality))
    Body(conditioningLine(c))
    PrimaryButton(stringResource(R.string.wk_cond_timer), { a.run { openTimer(index) } })
    SecondaryButton(stringResource(R.string.wk_cond_done), { a.run { logConditioning(index, c.workMinutes) } })
    SmallAction(stringResource(R.string.wk_cond_skip), { a.run { logConditioning(index, 0.0) } })
}

// ============================================================================================================ lifts (A1)
@Composable
private fun LiftPanel(l: LiftView, v: PlayerView) {
    val a = LocalActions.current
    Heading(l.exercise.name)
    l.item.swappedFrom?.let { Note(stringResource(R.string.wk_swapped_from, exerciseName(it))) }
    // TIME-002 superset (D-109): the partner's sets alternate with this one's.
    com.personalfitnesscoach.data.core.player.SessionPlayer.supersetPartner(l, v.lifts)?.let { p ->
        Note(stringResource(R.string.wk_superset, p.exercise.name, com.personalfitnesscoach.engine.registry.P.REST_004.default_s))
    }
    if (l.item.reducedRange) Note(stringResource(R.string.wk_reduced_range))
    if (l.item.painReduced) Note(stringResource(R.string.wk_pain_reduced))
    if (l.item.fromKnownNumber) Note(stringResource(R.string.wk_from_own_number))
    // CR-004: the exercise moving on a studio card, its main form point marked; the first cue sits on the card, the second below.
    val cues = com.personalfitnesscoach.engine.library.Library.text(l.exercise.id)?.cues?.take(2).orEmpty()
    StudioCard(Modifier.fillMaxWidth().heightIn(min = 196.dp)) {
        ExerciseFigure(l.exercise.id, stringResource(R.string.figure_desc, l.exercise.name), Modifier.matchParentSize())
        cues.firstOrNull()?.let { cue ->
            Text(cue, style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color(0xFFC6D3E2),
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                    .background(androidx.compose.ui.graphics.Color(0x990A101C), ChipShape).padding(horizontal = 10.dp, vertical = 4.dp))
        }
    }
    cues.drop(1).forEach { Note(it) }
    val t = l.next
    if (t == null) {
        Note(stringResource(R.string.wk_ex_done))
        SecondaryButton(stringResource(R.string.wk_add_set), { a.run { addSet(l.rowId) } })
        return
    }
    TargetCard(t, l)
    if (l.lastTime.isNotEmpty()) {
        Note(stringResource(R.string.wk_last_time, l.lastTime.map { setText(it) }.joinToString(", ")))
    }
    // The set is logged with one tap: "done as planned", or the effort answer (which also logs the set as planned).
    var adjusting by remember(l.rowId, t.kind, t.number) { mutableStateOf(false) }
    var form by remember(l.rowId, t.kind, t.number) { mutableStateOf(FormCheck.YES) }
    var load by remember(l.rowId, t.kind, t.number) { mutableStateOf(t.load) }
    var amount by remember(l.rowId, t.kind, t.number) { mutableStateOf(t.reps.last) }
    fun entry(rir: Double?): LiftEntry = if (t.unit == DoseUnit.SECONDS) LiftEntry(load, null, amount, rir, form) else LiftEntry(load, amount, null, rir, form)
    // A loaded set needs its weight: an emptied weight field never saves a set as bodyweight (R5-22).
    val canLog = t.load == null || load != null
    // A short tick confirms the set (D-099 "touch"); it follows the phone's own touch-feedback setting.
    val view = LocalView.current
    fun logIt(rir: Double?) {
        view.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM
            else android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        a.run { log(l.rowId, entry(rir), expected = t) }
    }

    if (t.askForm) {
        Text(stringResource(R.string.wk_form_q), style = MaterialTheme.typography.bodyLarge)
        com.personalfitnesscoach.app.ui.ChipGroup(listOf(FormCheck.YES, FormCheck.UNSURE, FormCheck.NO), { it == form }, {
            stringResource(when (it) { FormCheck.YES -> R.string.form_yes; FormCheck.UNSURE -> R.string.form_unsure; else -> R.string.form_no })
        }, { form = it })
    }
    if (adjusting) {
        if (t.load != null) NumberField(stringResource(R.string.wk_weight), load, { x -> load = x?.takeIf { it in 0.0..1000.0 } })
        NumberField(stringResource(if (t.unit == DoseUnit.SECONDS) R.string.wk_seconds else R.string.wk_reps), amount.toDouble(),
            { x -> amount = (x?.toInt() ?: 0).coerceIn(0, 1000) }, decimals = false)
        if (!canLog) Note(stringResource(R.string.wk_enter_weight))
    }
    when (t.kind) {
        SetKind.WARMUP -> {
            PrimaryButton(stringResource(R.string.wk_done_as_planned), { logIt(null) }, enabled = canLog)
            SmallAction(stringResource(R.string.wk_skip_ramp), { a.run { skipRamp(l.rowId) } })
        }
        SetKind.CALIBRATION -> {
            Note(stringResource(R.string.wk_calibration_hint))
            EffortButtons(v.effortPrompt, includeFivePlus = true, enabled = canLog) { rir -> logIt(rir) }
        }
        else -> {
            if (!adjusting) PrimaryButton(stringResource(R.string.wk_done_as_planned), { logIt(t.targetRir) }, enabled = canLog)
            EffortButtons(v.effortPrompt, includeFivePlus = false, enabled = canLog) { rir -> logIt(rir) }
        }
    }
    if (!adjusting) SmallAction(stringResource(R.string.wk_adjust), { adjusting = true })
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // A swap is only for an exercise not started yet (R5-15); once sets are logged, "skip" ends it at the sets done.
        if (!l.hasLoggedWork) {
            SmallAction(stringResource(R.string.wk_replace), { a.run { openReplace(l.rowId, occupied = false) } })
            SmallAction(stringResource(R.string.wk_busy), { a.run { openReplace(l.rowId, occupied = true) } })
        }
        SmallAction(stringResource(R.string.wk_skip_exercise), { a.run { skipExercise(l.rowId) } })
        if (t.kind == SetKind.WORKING && l.workingDone > 0) SmallAction(stringResource(R.string.wk_add_set), { a.run { addSet(l.rowId) } })
    }
}

/** The target of the next set in large numerals (Phase 2 A1). */
@Composable
private fun TargetCard(t: SetTarget, l: LiftView) {
    val g = Glass
    GlassCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(when {
                t.kind == SetKind.WARMUP -> stringResource(R.string.wk_set_ramp, t.number, t.of)
                t.kind == SetKind.CALIBRATION -> stringResource(R.string.wk_set_calibration, t.number)
                t.userAdded -> stringResource(R.string.wk_set_added, t.number - (l.item.sets - l.item.addedSets))
                else -> stringResource(R.string.wk_set_working, t.number, t.of)
            }, style = MaterialTheme.typography.titleMedium, color = g.text, modifier = Modifier.weight(1f))
            // The sets of this exercise as dots: done (cool), this one (warm), still to come (glass). The words above say the same.
            if (t.kind == SetKind.WORKING && !t.userAdded && t.of in 2..8) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 1..t.of) Box(Modifier.size(10.dp).background(when {
                    i < t.number -> g.cool
                    i == t.number -> g.warm
                    else -> g.glassBorder
                }, androidx.compose.foundation.shape.CircleShape))
            }
        }
        // "60 kg × 8 reps" in big numbers (Phase 2 A1); the parts wrap onto a new line at large text sizes.
        TargetNumbers(t.load, doseText(t.reps, t.unit, t.perSide))
        t.targetRir?.let { r ->
            Note(if (r <= 0.0) stringResource(R.string.to_failure) else stringResource(R.string.rir_target, kg(r)))
        }
    }
}

/**
 * "60 kg × 8 reps" in big numbers (Phase 2 A1). The weight and "× reps" are two groups: when they don't fit on one line, "× 8 reps"
 * moves down as a whole; the numbers stop growing at their own size, so "102.5" never breaks (review R7-03, D-103).
 */
@Composable
internal fun TargetNumbers(load: Double?, dose: String) {
    val g = Glass
    FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.alignByBaseline(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (load != null) {
                Text(kg(load), style = Numerals.grownAtMost(1f), color = g.text, maxLines = 1, modifier = Modifier.alignByBaseline())
                Text(stringResource(R.string.unit_kg_alone), style = MaterialTheme.typography.titleMedium, color = g.muted, modifier = Modifier.alignByBaseline())
            } else {
                Text(stringResource(R.string.bodyweight), style = NumeralsSmall.grownAtMost(1.4f), color = g.text, modifier = Modifier.alignByBaseline())
            }
        }
        Row(Modifier.alignByBaseline(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(TIMES, style = NumeralsSmall.grownAtMost(1.4f), color = g.muted, modifier = Modifier.alignByBaseline())
            Text(dose, style = NumeralsSmall.grownAtMost(1.4f), color = g.text, modifier = Modifier.alignByBaseline())
        }
    }
}

/** INT-006: "how many more could you have done?" 0 / 1 / 2 / 3 / 4+ (and 5+ while finding a weight, CAL-001). One tap logs the set. */
@Composable
private fun EffortButtons(style: Effort.PromptStyle, includeFivePlus: Boolean, enabled: Boolean = true, onPick: (Double) -> Unit) {
    Text(stringResource(if (style == Effort.PromptStyle.RIR_QUESTION) R.string.wk_effort_q else R.string.wk_effort_q_rpe),
        style = MaterialTheme.typography.bodyLarge)
    val five = stringResource(R.string.wk_effort_5plus)
    val options: List<Pair<String, Double>> =
        if (includeFivePlus) listOf("0" to 0.0, "1" to 1.0, "2" to 2.0, "3" to 3.0, "4" to 4.0, five to 5.0)
        else Effort.rirOptions.map { it to (Effort.rirFromAnswer(it) ?: 4.0) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { (label, rir) ->
            val on = enabled && !LocalBusy.current
            Box(Modifier.heightIn(min = PrimaryHeight).widthIn(min = PrimaryHeight).clip(ChipShape).glass(ChipShape, strong = true)
                .clickable(enabled = on, role = androidx.compose.ui.semantics.Role.Button) { onPick(rir) }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.titleLarge, color = if (on) Glass.text else Glass.muted)
            }
        }
    }
}

/** All exercises of the workout; any can be done next (EQ-002 reorder). */
@Composable
private fun ExerciseList(v: PlayerView) {
    val a = LocalActions.current
    SectionTitle(stringResource(R.string.wk_exercises))
    val current = (v.step as? Step.Lift)?.lift?.rowId
    // Exercises can be chosen in any order once the lifts have started (never skipping the warm-up, R5-08).
    val choosing = v.stage == Stage.LIFTS
    GlassCard(padding = 12.dp) {
    v.lifts.forEach { l ->
        val state = when {
            l.status == Status.SKIPPED -> stringResource(R.string.wk_ex_skipped)
            l.finished -> stringResource(R.string.wk_ex_done)
            else -> stringResource(R.string.wk_ex_sets_done, l.workingDone, l.item.sets)
        }
        val label = stringResource(R.string.list_item, l.exercise.name, state)
        if (l.rowId == current || l.finished || !choosing) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (l.rowId == current) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.heightIn(min = SecondaryHeight).padding(vertical = 12.dp))
            // SAF-006: one more set of a finished exercise (R5-23).
            if (l.finished && l.status != Status.SKIPPED && l.workingDone > 0 && v.state.stage.ordinal <= Stage.LIFTS.ordinal) SmallAction(stringResource(R.string.wk_add_set), { a.run { addSet(l.rowId) } })
        } else {
            SmallAction(label, { a.run { select(l.rowId) } })
        }
    }
    }
}
