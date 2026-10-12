@file:OptIn(ExperimentalLayoutApi::class)

package com.personalfitnesscoach.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personalfitnesscoach.R
import com.personalfitnesscoach.app.flow.ConservativeReason
import com.personalfitnesscoach.app.flow.DayCell
import com.personalfitnesscoach.app.flow.DayState
import com.personalfitnesscoach.app.flow.SettingsFlow
import com.personalfitnesscoach.app.flow.TodayModel
import com.personalfitnesscoach.app.ui.CardShape
import com.personalfitnesscoach.app.ui.ChipShape
import com.personalfitnesscoach.app.ui.ExerciseFigure
import com.personalfitnesscoach.app.ui.Glass
import com.personalfitnesscoach.app.ui.GlassCard
import com.personalfitnesscoach.app.ui.IconRoundButton
import com.personalfitnesscoach.app.ui.InfoCard
import com.personalfitnesscoach.app.ui.LocalActions
import com.personalfitnesscoach.app.ui.Note
import com.personalfitnesscoach.app.ui.PfcIcons
import com.personalfitnesscoach.app.ui.PillShape
import com.personalfitnesscoach.app.ui.PrimaryButton
import com.personalfitnesscoach.app.ui.ProgressRing
import com.personalfitnesscoach.app.ui.ReasonRow
import com.personalfitnesscoach.app.ui.ScreenColumn
import com.personalfitnesscoach.app.ui.SectionTitle
import com.personalfitnesscoach.app.ui.SmallAction
import com.personalfitnesscoach.app.ui.StatTile
import com.personalfitnesscoach.app.ui.StudioCard
import com.personalfitnesscoach.app.ui.SwitchRow
import com.personalfitnesscoach.app.ui.TileRow
import com.personalfitnesscoach.app.ui.Tone
import com.personalfitnesscoach.app.ui.text.Labels
import com.personalfitnesscoach.app.ui.text.ReasonTexts
import com.personalfitnesscoach.app.ui.theme.NumeralsSmall
import com.personalfitnesscoach.app.ui.theme.grownAtMost
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.engine.program.PlannedDay
import java.time.format.DateTimeFormatter

/**
 * T1 Today in the Glow look (D-099): a greeting with the date, anything that needs attention first, this week's ring and three small
 * numbers, today's workout on a studio card with its first exercise moving, one big Start, the reasons with their "Why?", the week.
 */
@Composable
fun TodayScreen(m: TodayModel) {
    val a = LocalActions.current
    val g = Glass
    ScreenColumn(null) {
        Header(m)
        // SAF-002: a stop that has not been confirmed comes before everything else.
        val stop = m.stopOpen
        if (stop != null) {
            InfoCard(stringResource(R.string.today_stop_title) + "\n" + stringResource(R.string.today_stop_body), Tone.ALERT) {
                PrimaryButton(stringResource(R.string.today_stop_confirm), { a.run { confirmStopResolved() } })
            }
        }
        if (m.conservative) {
            val (text, action) = when (m.conservativeReason) {
                ConservativeReason.CLINICIAN -> R.string.today_conservative_clinician to R.string.today_rescreen_go
                ConservativeReason.CONDITION -> R.string.today_conservative_condition to R.string.today_record_ok
                else -> R.string.today_conservative to R.string.today_record_ok
            }
            InfoCard(stringResource(text), Tone.WARN) {
                SmallAction(stringResource(action), { a.run { if (m.conservativeReason == ConservativeReason.CLINICIAN) openRescreen() else openSettings() } })
            }
        }
        if (m.rescreenDue) InfoCard(stringResource(R.string.today_rescreen), Tone.WARN) {
            SmallAction(stringResource(R.string.today_rescreen_go), { a.run { openRescreen() } })
        }
        if (m.painStop) InfoCard(stringResource(R.string.today_pain_stop), Tone.WARN)
        m.backFlare?.let { on -> SwitchRow(stringResource(R.string.today_back_flare), on, { v -> a.run { setBackFlare(v) } }) }
        if (m.welcomeBack && stop == null) InfoCard(stringResource(R.string.today_welcome_back), Tone.GOOD)

        WeekRing(m)
        Tiles(m)

        val next = m.next
        when {
            stop != null || m.painStop -> Unit
            m.doneToday -> InfoCard(stringResource(R.string.today_done), Tone.GOOD) {
                m.upcoming?.let { n -> Text(stringResource(R.string.done_next, stringResource(Labels.weekday(n.weekday)), stringResource(Labels.template(n.template))), color = g.text) }
            }
            next == null -> InfoCard(stringResource(R.string.today_week_done), Tone.GOOD)
            else -> {
                val name = stringResource(Labels.template(next.template))
                SectionTitle(stringResource(R.string.today_workout))
                if (!m.nextIsToday) Note(stringResource(R.string.today_next_planned, name, stringResource(Labels.weekday(next.weekday))))
                WorkoutCard(name, next, m.plan.user.profile.sessionMinutes)
                PrimaryButton(stringResource(R.string.today_start), { a.run { openCheckIn() } })
            }
        }
        m.reasons.forEach { r ->
            ReasonRow(stringResource(ReasonTexts.of(r.reason)), stringResource(R.string.action_why), { a.run { openWhy(r.reason, r.ruleIds) } })
        }

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

        SectionTitle(stringResource(R.string.today_week_days))
        WeekStrip(m.week)
        if (m.conditionPrompts.isNotEmpty()) {
            SectionTitle(stringResource(R.string.today_condition_tips))
            m.conditionPrompts.distinct().forEach { Note(it) }
        }
    }
}

/** "Good morning," and the date, with the settings button on the right. */
@Composable
private fun Header(m: TodayModel) {
    val a = LocalActions.current
    val greeting = stringResource(when (m.hour) {
        in 5..11 -> R.string.greet_morning
        in 12..17 -> R.string.greet_afternoon
        else -> R.string.greet_evening
    })
    // The date in the language of the app's words (English in V1), not the phone's, so it matches the greeting (review R7-14).
    val pattern = stringResource(R.string.today_date_pattern)
    val language = stringResource(R.string.date_language)
    val date = remember(m.today, pattern, language) {
        Days.date(m.today).format(DateTimeFormatter.ofPattern(pattern, java.util.Locale.forLanguageTag(language)))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
            Text(greeting, style = MaterialTheme.typography.titleSmall, color = Glass.muted)
            Text(date, style = MaterialTheme.typography.headlineSmall, color = Glass.text)
        }
        IconRoundButton(PfcIcons.Settings, stringResource(R.string.action_settings), { a.run { openSettings() } })
    }
}

/** This week: sessions done out of the sessions planned, as a ring, with the words beside it (and read as one sentence). */
@Composable
private fun WeekRing(m: TodayModel) {
    val planned = m.week.count { it.template != null || it.state == DayState.DONE }
    val done = m.week.count { it.state == DayState.DONE }
    if (planned == 0) return
    val left = (planned - done).coerceAtLeast(0)
    val desc = stringResource(R.string.today_ring_desc, done, planned)
    val line = if (left == 0) stringResource(R.string.today_week_all_done) else stringResource(R.string.today_week_left, done, left)
    val spoken = stringResource(R.string.today_ring_spoken, desc, line)
    GlassCard(Modifier.clearAndSetSemantics { contentDescription = spoken }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ProgressRing(done.toFloat() / planned, 112.dp, 12.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Inside the ring the words grow only a little with the text size (they must fit its centre; review R7-05).
                    Text(stringResource(R.string.today_ring_value, done, planned), style = NumeralsSmall.grownAtMost(1f), color = Glass.text, maxLines = 1)
                    Text(stringResource(R.string.today_ring_unit), style = MaterialTheme.typography.labelMedium.grownAtMost(1.2f), color = Glass.muted,
                        maxLines = 1)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.today_week), style = MaterialTheme.typography.titleLarge, color = Glass.text)
                Text(line, style = MaterialTheme.typography.bodyMedium, color = Glass.muted)
            }
        }
    }
}

private class TileData(val icon: ImageVector, val tint: Color, val value: String, val label: String, val description: String, val sub: String? = null)

/** Steps (when counted) and the run of weeks (once there is one: never "0 weeks" for someone just starting, ADH-001); the minutes are on the workout card. */
@Composable
private fun Tiles(m: TodayModel) {
    val g = Glass
    val tiles = ArrayList<TileData>()
    m.stepsToday?.let { steps ->
        val target = m.stepTarget
        val desc = if (target != null) stringResource(R.string.today_steps_target, steps, target) else stringResource(R.string.today_steps, steps)
        // The target stays on screen, not only in the spoken description (review R7-09).
        tiles += TileData(PfcIcons.Steps, g.cool, count(steps), stringResource(R.string.tile_steps), desc,
            target?.let { stringResource(R.string.tile_steps_goal, count(it)) })
    }
    if (m.streak.best > 0) {
        tiles += TileData(PfcIcons.Flame, g.warm, m.streak.current.toString(), stringResource(R.string.tile_streak),
            stringResource(R.string.today_streak, m.streak.current, m.streak.best), stringResource(R.string.tile_best, m.streak.best))
    }
    if (tiles.isEmpty()) return
    TileRow(tiles.size) {
        tiles.forEach { t -> StatTile(t.icon, t.tint, t.value, t.label, t.description, Modifier.weight(1f), t.sub) }
    }
}

/** Today's workout on a studio card: its first exercise moving on the right, the name and the size of the session on the left. */
@Composable
private fun WorkoutCard(name: String, day: PlannedDay, minutes: Int) {
    val first = day.slots.firstOrNull()?.exercise
    // The figure has the right part of the card and the words the left, on a dark veil, so a wide figure (a bench press) never sits
    // under the session's name (review R7-07).
    StudioCard(Modifier.fillMaxWidth().heightIn(min = 200.dp)) {
        if (first != null) ExerciseFigure(first.id, stringResource(R.string.figure_desc, first.name),
            Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.6f).height(200.dp), alignEnd = true)
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(0.62f)
            .background(Brush.horizontalGradient(listOf(Color(0xE60D1422), Color(0x000D1422))), CardShape)
            .padding(18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.titleLarge, color = Color(0xFFF4F7FB))
            Text(stringResource(R.string.today_card_line, minutes, day.slots.size), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC6D3E2))
        }
    }
}

/** The week: each day reads as "Mon, Lower body, done" to TalkBack; the words and the marks, not the colours, carry the state (NFR-08). */
@Composable
private fun WeekStrip(days: List<DayCell>) {
    val g = Glass
    // Seven across; at large text sizes the days wrap onto a second line instead of being cut off (review R7-05).
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
        maxItemsInEachRow = 7) {
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
            val edge = when {
                d.today -> g.warm
                d.state == DayState.DONE -> g.cool.copy(alpha = 0.5f)
                else -> g.glassBorder
            }
            Column(
                Modifier.weight(1f).widthIn(min = 44.dp).heightIn(min = 64.dp)
                    .background(if (d.state == DayState.DONE) g.cool.copy(alpha = 0.10f) else g.glass, ChipShape)
                    .border(if (d.today) 1.5.dp else 1.dp, edge, ChipShape)
                    .clearAndSetSemantics { contentDescription = desc }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(day, style = MaterialTheme.typography.labelMedium, color = if (d.today) g.text else g.muted,
                    fontWeight = if (d.today) FontWeight.ExtraBold else FontWeight.SemiBold, softWrap = false)
                when (d.state) {
                    DayState.DONE -> androidx.compose.material3.Icon(PfcIcons.Check, null, tint = g.cool, modifier = Modifier.size(16.dp))
                    DayState.PLANNED -> Box(Modifier.size(8.dp).background(if (d.today) g.warm else g.muted, CircleShape))
                    DayState.MISSED, DayState.SKIPPED -> Box(Modifier.size(width = 10.dp, height = 2.dp).background(g.muted, PillShape))
                    DayState.REST -> Box(Modifier.size(8.dp))
                }
            }
        }
    }
}

private fun count(n: Int): String = java.text.NumberFormat.getIntegerInstance(java.util.Locale.UK).format(n)

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
