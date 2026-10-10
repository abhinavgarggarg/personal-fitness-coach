@file:OptIn(ExperimentalLayoutApi::class)

package com.personalfitnesscoach.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personalfitnesscoach.R
import com.personalfitnesscoach.app.flow.DayCell
import com.personalfitnesscoach.app.flow.DayState
import com.personalfitnesscoach.app.flow.SettingsFlow
import com.personalfitnesscoach.app.flow.TodayModel
import com.personalfitnesscoach.app.ui.Body
import com.personalfitnesscoach.app.ui.Heading
import com.personalfitnesscoach.app.ui.InfoCard
import com.personalfitnesscoach.app.ui.LocalActions
import com.personalfitnesscoach.app.ui.Note
import com.personalfitnesscoach.app.ui.PrimaryButton
import com.personalfitnesscoach.app.ui.ScreenColumn
import com.personalfitnesscoach.app.ui.SecondaryButton
import com.personalfitnesscoach.app.ui.SectionTitle
import com.personalfitnesscoach.app.ui.SmallAction
import com.personalfitnesscoach.app.ui.Tone
import com.personalfitnesscoach.app.ui.text.Labels
import com.personalfitnesscoach.app.ui.text.ReasonTexts

/** T1 Today: the next session with one big Start button, the week strip, and anything that needs the user's attention first. */
@Composable
fun TodayScreen(m: TodayModel) {
    val a = LocalActions.current
    ScreenColumn(stringResource(R.string.today_title)) {
        // SAF-002: a stop that has not been confirmed comes before everything else.
        val stop = m.stopOpen
        if (stop != null) {
            InfoCard(stringResource(R.string.today_stop_title) + "\n" + stringResource(R.string.today_stop_body), Tone.ALERT) {
                PrimaryButton(stringResource(R.string.today_stop_confirm), { a.run { confirmStopResolved() } })
            }
        }
        if (m.conservative) InfoCard(stringResource(R.string.today_conservative), Tone.WARN)
        if (m.welcomeBack && stop == null) InfoCard(stringResource(R.string.today_welcome_back), Tone.GOOD)

        val next = m.next
        when {
            stop != null -> Unit
            m.doneToday -> InfoCard(stringResource(R.string.today_done), Tone.GOOD)
            next == null -> InfoCard(stringResource(R.string.today_week_done), Tone.GOOD)
            else -> {
                val name = stringResource(Labels.template(next.template))
                Text(
                    if (m.nextIsToday) stringResource(R.string.today_next_today, name)
                    else stringResource(R.string.today_next_planned, name, stringResource(Labels.weekday(next.weekday))),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                )
                Note(stringResource(R.string.today_about_minutes, m.plan.user.profile.sessionMinutes))
                PrimaryButton(stringResource(R.string.today_start), { a.run { openCheckIn() } })
            }
        }
        m.reasons.forEach { r -> Note(stringResource(ReasonTexts.of(r.reason))) }

        // LOAD-002: a finished session still waiting for its rating.
        m.rateWorkoutId?.let { id ->
            InfoCard(stringResource(R.string.today_rate_title)) {
                RpePicker { rpe -> a.run { rateLater(id, rpe) } }
            }
        }
        if (m.backupReminder) {
            InfoCard(stringResource(R.string.today_backup_reminder)) {
                PrimaryButton(stringResource(R.string.today_backup_now), { a.run { settingsFlow(SettingsFlow.Export()) } })
                SmallAction(stringResource(R.string.action_dismiss), { a.run { dismissBackupReminder() } })
            }
        }

        SectionTitle(stringResource(R.string.today_week))
        WeekStrip(m.week)
        Note(stringResource(R.string.today_streak, m.streak.current, m.streak.best))
        m.stepsToday?.let { steps ->
            val target = m.stepTarget
            Body(if (target != null) stringResource(R.string.today_steps_target, steps, target) else stringResource(R.string.today_steps, steps))
        }
        if (m.conditionPrompts.isNotEmpty()) {
            SectionTitle(stringResource(R.string.today_condition_tips))
            m.conditionPrompts.distinct().forEach { Note(it) }
        }
        SecondaryButton(stringResource(R.string.action_settings), { a.run { openSettings() } })
    }
}

/** The week: each day reads as "Mon, Lower body, heavy, done" to TalkBack; the words, not the colours, carry the state (NFR-08). */
@Composable
private fun WeekStrip(days: List<DayCell>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEach { d ->
            val day = stringResource(Labels.weekday(d.weekday))
            val what = d.template?.let { stringResource(Labels.template(it)) } ?: stringResource(R.string.day_state_rest)
            val state = stringResource(when (d.state) {
                DayState.DONE -> R.string.day_state_done
                DayState.SKIPPED -> R.string.day_state_skipped
                DayState.REST -> R.string.day_state_rest
                DayState.MISSED -> R.string.day_state_missed
                DayState.PLANNED -> R.string.day_state_planned
            })
            val desc = stringResource(R.string.day_cell_desc, day, what, state) + if (d.today) ", " + stringResource(R.string.today_marker) else ""
            val cs = MaterialTheme.colorScheme
            val bg = when (d.state) {
                DayState.DONE -> cs.primaryContainer
                DayState.PLANNED -> if (d.today) cs.secondaryContainer else cs.surfaceVariant
                else -> cs.surface
            }
            Surface(color = bg, shape = MaterialTheme.shapes.small, tonalElevation = if (d.today) 2.dp else 0.dp,
                modifier = Modifier.widthIn(min = 44.dp).heightIn(min = 56.dp).clearAndSetSemantics { contentDescription = desc }) {
                Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(day, style = MaterialTheme.typography.labelLarge, fontWeight = if (d.today) FontWeight.Bold else FontWeight.Normal)
                    Text(when (d.state) {
                        DayState.DONE -> "✓"
                        DayState.PLANNED -> "•"
                        DayState.MISSED, DayState.SKIPPED -> "–"
                        DayState.REST -> " "
                    }, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** CR10 0–10 session rating (LOAD-001), one tap. */
@Composable
fun RpePicker(onPick: (Int) -> Unit) {
    Note(stringResource(R.string.rpe_scale))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0..10) {
            val desc = stringResource(R.string.rpe_value, i)
            androidx.compose.material3.OutlinedButton(onClick = { onPick(i) }, enabled = !com.personalfitnesscoach.app.ui.LocalBusy.current,
                modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).semantics { contentDescription = desc }) {
                Text(i.toString())
            }
        }
    }
}
