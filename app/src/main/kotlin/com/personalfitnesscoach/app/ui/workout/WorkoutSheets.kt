package com.personalfitnesscoach.app.ui.workout

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personalfitnesscoach.R
import com.personalfitnesscoach.app.flow.WorkoutSheet
import com.personalfitnesscoach.app.platform.RestAlerts
import com.personalfitnesscoach.app.ui.Body
import com.personalfitnesscoach.app.ui.CheckRow
import com.personalfitnesscoach.app.ui.Heading
import com.personalfitnesscoach.app.ui.InfoCard
import com.personalfitnesscoach.app.ui.LocalActions
import com.personalfitnesscoach.app.ui.Note
import com.personalfitnesscoach.app.ui.PainFields
import com.personalfitnesscoach.app.ui.PrimaryButton
import com.personalfitnesscoach.app.ui.ScreenColumn
import com.personalfitnesscoach.app.ui.SecondaryButton
import com.personalfitnesscoach.app.ui.SectionTitle
import com.personalfitnesscoach.app.ui.Stepper
import com.personalfitnesscoach.app.ui.Tone
import com.personalfitnesscoach.app.ui.kg
import com.personalfitnesscoach.app.ui.text.Labels
import com.personalfitnesscoach.app.ui.theme.Numerals
import com.personalfitnesscoach.data.core.player.PlayerView
import com.personalfitnesscoach.data.core.session.ConditioningItem
import com.personalfitnesscoach.engine.registry.P
import kotlinx.coroutines.delay

/** One sheet at a time over the workout (Phase 2 ACTIVE.SHEET), shown full screen so every control is large and in reach. */
@Composable
fun WorkoutSheetPanel(v: PlayerView, sheet: WorkoutSheet) {
    val a = LocalActions.current
    ScreenColumn(null) {
        when (sheet) {
            is WorkoutSheet.Replace -> {
                val c = sheet.choice
                Heading(stringResource(if (c.canDoLater) R.string.sheet_busy_title else R.string.sheet_replace_title, c.original.name))
                if (c.options.isEmpty()) Note(stringResource(R.string.sheet_replace_empty))
                c.options.take(5).forEach { o -> SecondaryButton(o.exercise.name, { a.run { swap(c.rowId, o.exercise.id) } }) }
                if (c.canDoLater) PrimaryButton(stringResource(R.string.sheet_do_later), { a.run { doLater(c.rowId) } })
            }
            is WorkoutSheet.Hurts -> {
                Heading(stringResource(R.string.sheet_hurts_title))
                PainFields(sheet.form) { change -> a.edit { editHurts(change) } }
                PrimaryButton(stringResource(R.string.sheet_hurts_check), { a.run { submitHurts() } },
                    enabled = sheet.form.region != null && sheet.form.kind != null)
            }
            is WorkoutSheet.HurtsResult -> {
                Heading(stringResource(R.string.sheet_hurts_title))
                val o = sheet.outcome
                InfoCard(stringResource(Labels.painAction(o.action)), if (o.endSession || o.regionConservative) Tone.WARN else Tone.NEUTRAL) {
                    if (o.suggestProfessional) Text(stringResource(R.string.pain_result_professional))
                    if (o.endSession) Text(stringResource(R.string.pain_result_end))
                }
                if (sheet.changes.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.wk_changes))
                    sheet.changes.forEach { Body(changeText(it, v.conditioning)) }
                }
                val alt = sheet.alternatives
                val region = sheet.region
                if (alt != null && region != null && alt.options.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.pain_alternative_title))
                    alt.options.take(3).forEach { c -> SecondaryButton(c.exercise.name, { a.run { takeAlternative(alt.rowId, c.exercise.id, region) } }) }
                }
            }
            is WorkoutSheet.ChangeTime -> ChangeTimeSheet(sheet.minutes)
            is WorkoutSheet.RedFlag -> RedFlagSheet()
            is WorkoutSheet.ConfirmEntry -> {
                Heading(stringResource(R.string.confirm_entry_title))
                val e = sheet.entry
                val what = listOfNotNull(e.load?.let { stringResource(R.string.unit_kg, kg(it)) }, e.reps?.toString(),
                    e.seconds?.let { stringResource(R.string.unit_sec, it) }).joinToString(" × ")
                Body(stringResource(R.string.confirm_entry_body, what))
                PrimaryButton(stringResource(R.string.confirm_entry_fix), { a.run { closeSheet() } })
                SecondaryButton(stringResource(R.string.confirm_entry_save), { a.run { log(sheet.rowId, e, confirmed = true, expected = sheet.expected) } })
            }
            is WorkoutSheet.ConfirmAddSet -> {
                Heading(stringResource(R.string.addset_confirm_title))
                Body(stringResource(R.string.addset_confirm_body))
                PrimaryButton(stringResource(R.string.action_cancel), { a.run { closeSheet() } })
                SecondaryButton(stringResource(R.string.addset_confirm_go), { a.run { addSet(sheet.rowId, confirmed = true) } })
            }
            WorkoutSheet.End -> {
                Heading(stringResource(R.string.sheet_end_title))
                PrimaryButton(stringResource(R.string.sheet_end_keep), { a.run { closeSheet() } })
                val logged = v.lifts.any { it.hasLoggedWork } || v.state.conditioningDone.isNotEmpty()
                SecondaryButton(stringResource(R.string.sheet_end_finish), { a.run { finishWorkout(early = true) } }, enabled = logged)
                if (!logged) SecondaryButton(stringResource(R.string.sheet_end_discard), { a.run { discardWorkout() } })
            }
            is WorkoutSheet.Timer -> v.conditioning.getOrNull(sheet.index)?.let { IntervalTimer(sheet.index, it) }
        }
        if (sheet !is WorkoutSheet.End && sheet !is WorkoutSheet.ConfirmAddSet && sheet !is WorkoutSheet.ConfirmEntry) {
            SecondaryButton(stringResource(R.string.action_close), { a.run { closeSheet() } })
        }
    }
}

/** A6: how much time is left; the rest of the session is re-fitted (TIME-001…004) and what moved is listed. */
@Composable
private fun ChangeTimeSheet(start: Int) {
    val a = LocalActions.current
    var m by remember(start) { mutableIntStateOf(start) }
    Heading(stringResource(R.string.sheet_time_title))
    Stepper(stringResource(R.string.unit_min, m), { m = (m - 5).coerceAtLeast(5) }, { m = (m + 5).coerceAtMost(180) }, minusEnabled = m > 5, plusEnabled = m < 180)
    PrimaryButton(stringResource(R.string.sheet_time_apply), { a.run { changeTime(m) } })
}

/** A5 entry: any ticked warning sign ends the workout at once (SAF-002). */
@Composable
private fun RedFlagSheet() {
    val a = LocalActions.current
    var picked by remember { mutableStateOf(emptySet<String>()) }
    Heading(stringResource(R.string.sheet_red_title))
    Body(stringResource(R.string.sheet_red_intro))
    P.SAF_002.symptoms.forEach { id ->
        val label = Labels.RED_FLAGS[id]?.let { stringResource(it) } ?: Labels.fallback(id)
        CheckRow(label, id in picked, { on -> picked = if (on) picked + id else picked - id })
    }
    PrimaryButton(stringResource(R.string.sheet_red_stop), { a.run { submitRedFlag(picked) } }, enabled = picked.isNotEmpty())
}

/** One phase of a conditioning timer. */
private data class Phase(val work: Boolean, val seconds: Int, val round: Int, val rounds: Int, val label: String?)

/**
 * A7: the interval or steady timer — work and rest phases with a beep and a buzz at each change, and the round count. When the block is
 * finished (or ended early) the work minutes actually done are logged.
 */
@Composable
private fun IntervalTimer(index: Int, c: ConditioningItem) {
    val a = LocalActions.current
    val context = LocalContext.current
    val phases = remember(c) { phasesOf(c) }
    // Kept across turning the phone (R5-20); a restarted app starts the block's timer again (the minutes are logged when it ends).
    var i by rememberSaveable(index) { mutableIntStateOf(0) }
    var running by rememberSaveable(index) { mutableStateOf(false) }
    var leftMs by rememberSaveable(index) { mutableLongStateOf(phases.firstOrNull()?.seconds?.times(1000L) ?: 0L) }
    var workDoneMs by rememberSaveable(index) { mutableLongStateOf(0L) }
    val tone = remember { try { ToneGenerator(AudioManager.STREAM_ALARM, 80) } catch (e: RuntimeException) { null } }
    DisposableEffect(Unit) { onDispose { tone?.release() } }

    LaunchedEffect(running) {
        var last = System.currentTimeMillis()
        while (running && i < phases.size) {
            delay(200)
            val now = System.currentTimeMillis()
            val dt = now - last
            last = now
            if (phases[i].work) workDoneMs += dt
            leftMs -= dt
            if (leftMs <= 0) {
                tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
                RestAlerts.vibrate(context)
                if (i + 1 < phases.size) { i += 1; leftMs = phases[i].seconds * 1000L } else { running = false; i = phases.size }
            }
        }
    }
    Heading(modalityName(c.modality))
    Body(conditioningLine(c))
    val p = phases.getOrNull(i)
    if (p == null) {
        InfoCard(stringResource(R.string.timer_done), Tone.GOOD)
    } else {
        Text(stringResource(if (p.work) R.string.timer_work else R.string.timer_rest), style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        p.label?.let { Body(it) }
        val left = ((leftMs + 999) / 1000).coerceAtLeast(0)
        Text(stringResource(R.string.wk_rest_left, (left / 60).toInt(), (left % 60).toInt()), style = Numerals)
        if (p.rounds > 1) Note(stringResource(R.string.timer_round, p.round, p.rounds))
        if (c.interval == null && c.circuit == null) Note(stringResource(R.string.timer_steady, stringResource(Labels.zone(c.zone))))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (running) SecondaryButton(stringResource(R.string.timer_pause), { running = false }, Modifier.weight(1f))
            else PrimaryButton(stringResource(R.string.timer_start), { running = true }, Modifier.weight(1f))
        }
    }
    Note(stringResource(R.string.wk_cond_minutes_done, Math.round(workDoneMs / 60000.0).toInt()))
    PrimaryButton(stringResource(R.string.timer_finish), {
        running = false
        val done = if (i >= phases.size) c.workMinutes else workDoneMs / 60000.0
        a.run { logConditioning(index, Math.round(done * 10) / 10.0) }
    })
}

private fun phasesOf(c: ConditioningItem): List<Phase> {
    val iv = c.interval
    val k = c.circuit
    return when {
        k != null -> (1..k.rounds).flatMap { r ->
            k.moves.flatMap { m ->
                val name = com.personalfitnesscoach.data.core.session.CircuitRef.CIRCUIT_MOVES[m]?.name
                listOf(Phase(true, k.workSec, r, k.rounds, name)) + if (k.restSec > 0) listOf(Phase(false, k.restSec, r, k.rounds, null)) else emptyList()
            }
        }
        iv != null -> (1..iv.reps).flatMap { r ->
            listOf(Phase(true, iv.workSec, r, iv.reps, null)) + if (iv.restSec > 0 && r < iv.reps) listOf(Phase(false, iv.restSec, r, iv.reps, null)) else emptyList()
        }
        else -> listOf(Phase(true, Math.round(c.workMinutes * 60).toInt().coerceAtLeast(1), 1, 1, null))
    }
}
