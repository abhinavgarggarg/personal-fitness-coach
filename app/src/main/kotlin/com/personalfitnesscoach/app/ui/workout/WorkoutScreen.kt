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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
        v.restEndsAtMs?.let { end -> RestPanel(end, v) }
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
    HorizontalDivider()
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val current = (v.step as? Step.Lift)?.lift?.rowId
        OutlinedButton(onClick = { a.run { openHurts(current) } }, enabled = !LocalBusy.current, modifier = Modifier.heightIn(min = PrimaryHeight)) {
            Text(stringResource(R.string.wk_hurts))
        }
        OutlinedButton(onClick = { a.run { openRedFlag() } }, enabled = !LocalBusy.current, modifier = Modifier.heightIn(min = PrimaryHeight)) {
            Text(stringResource(R.string.wk_red_flag))
        }
        OutlinedButton(onClick = { a.run { openChangeTime() } }, enabled = !LocalBusy.current, modifier = Modifier.heightIn(min = PrimaryHeight)) {
            Text(stringResource(R.string.wk_change_time))
        }
    }
}

@Composable
private fun Notice(n: WorkoutNotice, v: PlayerView) {
    when (n) {
        is WorkoutNotice.LoadChanged -> InfoCard(stringResource(ReasonTexts.of(n.decision.reason)))
        is WorkoutNotice.Replanned -> InfoCard(stringResource(R.string.wk_changes)) {
            if (n.changes.isEmpty()) Text(stringResource(R.string.wk_no_changes))
            n.changes.forEach { c -> Text(changeText(c, v.conditioning)) }
        }
        is WorkoutNotice.Refused -> InfoCard(stringResource(R.string.wk_refused), Tone.WARN)
        is WorkoutNotice.AddSet -> InfoCard(stringResource(if (n.verdict == AdditionVerdict.BLOCKED) R.string.addset_blocked else R.string.addset_ok),
            if (n.verdict == AdditionVerdict.BLOCKED) Tone.WARN else Tone.GOOD)
    }
}

// ============================================================================================================ rest (A2)
/** The rest countdown: ±30 s and skip; when it ends the phone buzzes and the next set is shown. Read out by TalkBack as it changes. */
@Composable
private fun RestPanel(endsAtMs: Long, v: PlayerView) {
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
    val left = ((leftMs + 999) / 1000).coerceAtLeast(0)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (left > 0) {
                Text(stringResource(R.string.wk_rest_title), style = MaterialTheme.typography.titleMedium)
                // Not a live region: TalkBack would read every second (R5-18); the end of the rest is announced instead.
                Text(stringResource(R.string.wk_rest_left, (left / 60).toInt(), (left % 60).toInt()), style = Numerals)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { a.run { adjustRest(-30) } }, enabled = !LocalBusy.current, modifier = Modifier.heightIn(min = PrimaryHeight)) {
                        Text(stringResource(R.string.wk_rest_minus))
                    }
                    FilledTonalButton(onClick = { a.run { adjustRest(30) } }, enabled = !LocalBusy.current, modifier = Modifier.heightIn(min = PrimaryHeight)) {
                        Text(stringResource(R.string.wk_rest_plus))
                    }
                    FilledTonalButton(onClick = { a.run { skipRest() } }, enabled = !LocalBusy.current, modifier = Modifier.heightIn(min = PrimaryHeight)) {
                        Text(stringResource(R.string.wk_rest_skip))
                    }
                }
            } else {
                Text(stringResource(R.string.wk_rest_over), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
            }
            (v.step as? Step.Lift)?.lift?.let { l -> Note(stringResource(R.string.wk_next, l.exercise.name)) }
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
    if (l.item.reducedRange) Note(stringResource(R.string.wk_reduced_range))
    if (l.item.painReduced) Note(stringResource(R.string.wk_pain_reduced))
    if (l.item.fromKnownNumber) Note(stringResource(R.string.wk_from_own_number))
    com.personalfitnesscoach.engine.library.Library.text(l.exercise.id)?.cues?.take(2)?.forEach { Note(it) }
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
    fun logIt(rir: Double?) { a.run { log(l.rowId, entry(rir), expected = t) } }

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
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(when {
                t.kind == SetKind.WARMUP -> stringResource(R.string.wk_set_ramp, t.number, t.of)
                t.kind == SetKind.CALIBRATION -> stringResource(R.string.wk_set_calibration, t.number)
                t.userAdded -> stringResource(R.string.wk_set_added, t.number - (l.item.sets - l.item.addedSets))
                else -> stringResource(R.string.wk_set_working, t.number, t.of)
            }, style = MaterialTheme.typography.titleMedium)
            val load = t.load?.let { stringResource(R.string.unit_kg, kg(it)) } ?: stringResource(R.string.bodyweight)
            Text(load, style = Numerals)
            Text(doseText(t.reps, t.unit, t.perSide), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            t.targetRir?.let { r ->
                Note(if (r <= 0.0) stringResource(R.string.to_failure) else stringResource(R.string.rir_target, kg(r)))
            }
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, rir) ->
            FilledTonalButton(onClick = { onPick(rir) }, enabled = enabled && !LocalBusy.current, modifier = Modifier.heightIn(min = PrimaryHeight).widthIn(min = PrimaryHeight)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
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
