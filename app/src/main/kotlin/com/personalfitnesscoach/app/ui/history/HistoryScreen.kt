package com.personalfitnesscoach.app.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.personalfitnesscoach.R
import com.personalfitnesscoach.app.flow.Screen
import com.personalfitnesscoach.app.flow.SetEdit
import com.personalfitnesscoach.app.flow.Tab
import com.personalfitnesscoach.app.ui.Body
import com.personalfitnesscoach.app.ui.InfoCard
import com.personalfitnesscoach.app.ui.LocalActions
import com.personalfitnesscoach.app.ui.LocalBusy
import com.personalfitnesscoach.app.ui.Note
import com.personalfitnesscoach.app.ui.PrimaryButton
import com.personalfitnesscoach.app.ui.ScreenColumn
import com.personalfitnesscoach.app.ui.SecondaryButton
import com.personalfitnesscoach.app.ui.SecondaryHeight
import com.personalfitnesscoach.app.ui.SectionTitle
import com.personalfitnesscoach.app.ui.SmallAction
import com.personalfitnesscoach.app.ui.Stepper
import com.personalfitnesscoach.app.ui.Tone
import com.personalfitnesscoach.app.ui.kg
import com.personalfitnesscoach.app.ui.minutes
import com.personalfitnesscoach.app.ui.text.Labels
import com.personalfitnesscoach.app.ui.text.ReasonTexts
import com.personalfitnesscoach.app.ui.text.Reasons
import com.personalfitnesscoach.app.ui.workout.exerciseName
import com.personalfitnesscoach.data.core.insight.HistoryItem
import com.personalfitnesscoach.data.core.insight.SessionOutcome
import com.personalfitnesscoach.data.core.insight.SetLine
import com.personalfitnesscoach.data.core.session.SetKind
import com.personalfitnesscoach.data.core.time.Days
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val DATE: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

fun dateText(day: Int): String = Days.date(day).format(DATE)

/** H1: finished sessions, newest first; tap one for every set. */
@Composable
fun HistoryScreen(s: Screen.History) {
    val a = LocalActions.current
    BackHandler { a.run { openTab(Tab.TODAY) } }
    ScreenColumn(stringResource(R.string.hist_title)) {
        if (s.items.isEmpty()) Body(stringResource(R.string.hist_empty))
        s.items.forEach { HistoryRow(it) }
        if (s.hasOlder) SmallAction(stringResource(R.string.hist_more), { a.run { moreHistory() } })
    }
}

@Composable
private fun HistoryRow(item: HistoryItem) {
    val a = LocalActions.current
    val busy = LocalBusy.current
    val title = stringResource(R.string.hist_row, dateText(item.day), stringResource(Labels.template(item.template)))
    val detail = if (item.outcome == SessionOutcome.SKIPPED) stringResource(Labels.outcome(item.outcome))
        else stringResource(R.string.hist_row_detail, stringResource(Labels.outcome(item.outcome)), minutes(item.minutes ?: 0.0), item.sets)
    Column(Modifier.fillMaxWidth().heightIn(min = SecondaryHeight)
        .clickable(enabled = !busy && item.outcome != SessionOutcome.SKIPPED, role = Role.Button) { a.run { openSession(item.workoutId) } }
        .padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Note(detail)
    }
    HorizontalDivider()
}

/** H2: every set of one session; a set can be corrected or deleted (FS-5), and the session's decisions open "Why?". */
@Composable
fun SessionDetailScreen(s: Screen.SessionDetail) {
    val a = LocalActions.current
    val d = s.detail
    BackHandler { a.run { if (s.edit != null) cancelSetEdit() else openTab(Tab.HISTORY) } }
    ScreenColumn(stringResource(R.string.hist_row, dateText(d.item.day), stringResource(Labels.template(d.item.template)))) {
        Note(stringResource(R.string.hist_row_detail, stringResource(Labels.outcome(d.item.outcome)), minutes(d.item.minutes ?: 0.0), d.item.sets))
        d.item.sessionRpe?.let { Note(stringResource(R.string.hist_rating, minutes(it))) }
        if (d.item.outcome == SessionOutcome.STOPPED) InfoCard(stringResource(R.string.hist_detail_stopped), Tone.WARN)
        s.edit?.let { EditSet(it, s.problem) }
        if (d.editable && s.edit == null) Note(stringResource(R.string.hist_detail_edit_hint))
        d.exercises.forEach { e ->
            SectionTitle(exerciseName(e.exerciseId))
            if (e.sets.isEmpty()) Note(stringResource(R.string.hist_no_sets))
            e.sets.forEach { set ->
                val line = setText(set)
                val busy = LocalBusy.current
                Text(line, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().heightIn(min = SecondaryHeight)
                    .clickable(enabled = d.editable && s.edit == null && !busy, role = Role.Button) { a.run { startSetEdit(set.setId) } }
                    .padding(vertical = 8.dp))
            }
        }
        val reasons = Reasons.shown(d.decisions.map { it.reason })
        if (reasons.isNotEmpty()) {
            SectionTitle(stringResource(R.string.hist_why_title))
            reasons.forEach { r ->
                val entry = d.decisions.first { it.reason == r }
                Body(stringResource(ReasonTexts.of(r)))
                SmallAction(stringResource(R.string.action_why), { a.run { openWhy(entry.reason, entry.ruleIds) } })
            }
        }
        SecondaryButton(stringResource(R.string.action_back), { a.run { openTab(Tab.HISTORY) } })
    }
}

@Composable
private fun setText(set: SetLine): String {
    val load = set.loadKg
    val reps = set.reps
    val secs = set.seconds
    val value = when {
        load != null && load > 0 && reps != null -> stringResource(R.string.hist_set_value_load, kg(load), reps)
        secs != null -> stringResource(R.string.hist_set_value_secs, secs)
        reps != null -> stringResource(R.string.hist_set_value_reps, reps)
        else -> "-"
    } + (set.rir?.let { stringResource(R.string.hist_set_rir, minutes(it)) } ?: "")
    return when (set.kind) {
        SetKind.WARMUP -> stringResource(R.string.hist_set_warmup, set.index + 1, value)
        SetKind.CALIBRATION -> stringResource(R.string.hist_set_calibration, set.index + 1, value)
        else -> stringResource(R.string.hist_set_line, set.index + 1, value)
    }
}

/** Correcting one set: steppers only (master prompt A9: no keyboard); the ranges of logging apply on save. */
@Composable
private fun EditSet(e: SetEdit, problem: Boolean) {
    val a = LocalActions.current
    InfoCard(stringResource(R.string.edit_title)) {
        Text(exerciseName(e.exerciseId), style = MaterialTheme.typography.titleMedium)
        e.loadKg?.let { load ->
            Stepper(stringResource(R.string.edit_weight, kg(load)), { a.edit { changeSetEdit { it.copy(loadKg = ((it.loadKg ?: 0.0) - 1.25).coerceAtLeast(0.0)) } } },
                { a.edit { changeSetEdit { it.copy(loadKg = ((it.loadKg ?: 0.0) + 1.25).coerceAtMost(com.personalfitnesscoach.data.core.session.SetLimits.MAX_LOAD_KG)) } } },
                minusEnabled = load > 0.0, plusEnabled = load < com.personalfitnesscoach.data.core.session.SetLimits.MAX_LOAD_KG)
        }
        e.reps?.let { reps ->
            Stepper(stringResource(R.string.edit_reps, reps), { a.edit { changeSetEdit { it.copy(reps = ((it.reps ?: 0) - 1).coerceAtLeast(0)) } } },
                { a.edit { changeSetEdit { it.copy(reps = ((it.reps ?: 0) + 1).coerceAtMost(com.personalfitnesscoach.data.core.session.SetLimits.MAX_REPS)) } } },
                minusEnabled = reps > 0, plusEnabled = reps < com.personalfitnesscoach.data.core.session.SetLimits.MAX_REPS)
        }
        e.seconds?.let { secs ->
            Stepper(stringResource(R.string.edit_secs, secs), { a.edit { changeSetEdit { it.copy(seconds = ((it.seconds ?: 0) - 5).coerceAtLeast(0)) } } },
                { a.edit { changeSetEdit { it.copy(seconds = (it.seconds ?: 0) + 5) } } }, minusEnabled = secs > 0)
        }
        if (e.kind != SetKind.WARMUP) {
            val rir = e.rir ?: 2.0
            Stepper(stringResource(R.string.edit_rir, minutes(rir)), { a.edit { changeSetEdit { it.copy(rir = ((it.rir ?: 2.0) - 1.0).coerceAtLeast(0.0)) } } },
                { a.edit { changeSetEdit { it.copy(rir = ((it.rir ?: 2.0) + 1.0).coerceAtMost(10.0)) } } }, minusEnabled = rir > 0.0, plusEnabled = rir < 10.0)
        }
        if (problem) Body(stringResource(R.string.edit_problem))
        if (e.confirmDelete) {
            // Asked once more: a deleted set can't be brought back (review R6-12).
            Body(stringResource(R.string.edit_delete_confirm))
            PrimaryButton(stringResource(R.string.action_delete), { a.run { deleteEditedSet() } })
            SecondaryButton(stringResource(R.string.edit_delete_keep), { a.edit { changeSetEdit { it.copy(confirmDelete = false) } } })
        } else {
            PrimaryButton(stringResource(R.string.action_save), { a.run { saveSetEdit() } })
            Note(stringResource(R.string.edit_delete_note))
            SecondaryButton(stringResource(R.string.action_delete), { a.edit { askDeleteSet() } })
            SecondaryButton(stringResource(R.string.action_cancel), { a.run { cancelSetEdit() } })
        }
    }
}
