package com.personalfitnesscoach.app.ui.progress

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.personalfitnesscoach.R
import com.personalfitnesscoach.app.flow.BodyEntry
import com.personalfitnesscoach.app.flow.Screen
import com.personalfitnesscoach.app.flow.Tab
import com.personalfitnesscoach.app.ui.Body
import com.personalfitnesscoach.app.ui.CheckRow
import com.personalfitnesscoach.app.ui.InfoCard
import com.personalfitnesscoach.app.ui.LocalActions
import com.personalfitnesscoach.app.ui.LocalBusy
import com.personalfitnesscoach.app.ui.Note
import com.personalfitnesscoach.app.ui.NumberField
import com.personalfitnesscoach.app.ui.PrimaryButton
import com.personalfitnesscoach.app.ui.ScreenColumn
import com.personalfitnesscoach.app.ui.SecondaryButton
import com.personalfitnesscoach.app.ui.SecondaryHeight
import com.personalfitnesscoach.app.ui.SectionTitle
import com.personalfitnesscoach.app.ui.Tone
import com.personalfitnesscoach.app.ui.history.dateText
import com.personalfitnesscoach.app.ui.kg
import com.personalfitnesscoach.app.ui.minutes
import com.personalfitnesscoach.app.ui.text.Labels
import com.personalfitnesscoach.app.ui.workout.exerciseName
import com.personalfitnesscoach.data.core.insight.LiftLine
import com.personalfitnesscoach.data.core.insight.WeekLine
import com.personalfitnesscoach.engine.progress.TrendDirection
import com.personalfitnesscoach.engine.registry.P
import java.text.NumberFormat

/** P1: strength, weeks, streak and milestones, body and steps — one screen, no menus (master prompt A9). */
@Composable
fun ProgressScreen(s: Screen.Progress) {
    val a = LocalActions.current
    val o = s.overview
    BackHandler { a.run { if (s.entry != null) closeBodyEntry() else openTab(Tab.TODAY) } }
    ScreenColumn(stringResource(R.string.prog_title)) {
        if (o.sessionsCompleted == 0 && o.body?.weights.isNullOrEmpty()) Body(stringResource(R.string.prog_empty))

        // ---------------------------------------------------------------- strength (FL-005, INT-004)
        if (o.lifts.isNotEmpty()) {
            SectionTitle(stringResource(R.string.prog_strength))
            Note(stringResource(R.string.prog_strength_intro))
            o.lifts.forEach { LiftRow(it) }
        }

        // ---------------------------------------------------------------- weeks (PH-001, ADH-001, ADH-002)
        if (o.weeks.isNotEmpty() || o.sessionsCompleted > 0) {
            SectionTitle(stringResource(R.string.prog_weeks))
            Note(stringResource(R.string.prog_weeks_intro))
            if (o.weeks.isNotEmpty()) WeekBars(o.weeks)
            o.weeks.reversed().forEach { w ->
                Body(stringResource(R.string.prog_week_line, dateText(w.weekStartDay), w.completed, w.planned, minutes(w.equivalentMinutes)) +
                    if (w.whoFloorMet) " · " + stringResource(R.string.prog_week_met) else "")
            }
            // ADH-001: the run of weeks only once there is one, and the weeks trained this year, which never go down.
            if (o.streak.best > 0) Body(stringResource(R.string.prog_streak, o.streak.current, o.streak.best))
            if (o.streak.weeksTrainedThisYear > 0) Body(stringResource(R.string.prog_weeks_year, o.streak.weeksTrainedThisYear))
            Body(stringResource(R.string.prog_sessions, o.sessionsCompleted))
            Body(stringResource(R.string.prog_who_weeks, o.whoWeeks))
        }
        if (o.achievements.isNotEmpty()) {
            SectionTitle(stringResource(R.string.prog_achievements))
            o.achievements.forEach { Body("• " + stringResource(Labels.achievement(it))) }
        }

        // ---------------------------------------------------------------- body (FL-005; hidden when weight features are off, FL-001)
        o.body?.let { b ->
            SectionTitle(stringResource(R.string.prog_body))
            Note(stringResource(R.string.prog_body_intro))
            Body(b.weight.mean7?.let { stringResource(R.string.prog_weight_mean, kg(it)) } ?: stringResource(R.string.prog_weight_none))
            b.weight.kgPerWeek?.let { Body(stringResource(R.string.prog_weight_trend, signed(it))) }
            if (b.weights.size >= 2) Sparkline(b.weights.map { it.kg }, stringResource(R.string.prog_chart_desc, stringResource(R.string.prog_body),
                b.weights.joinToString(", ") { kg(it.kg) }))
            if (b.weight.rateCheckIn) InfoCard(stringResource(R.string.prog_weight_check_in), Tone.WARN)
            val waist = b.waistCm
            val waistDay = b.waistDay
            Body(if (waist != null && waistDay != null) stringResource(R.string.prog_waist, kg(waist), dateText(waistDay)) else stringResource(R.string.prog_waist_none))
            if (b.waistDue) Note(stringResource(R.string.prog_waist_due))
            when (val e = s.entry) {
                null -> {
                    SecondaryButton(stringResource(R.string.prog_add_weight), { a.run { openBodyEntry(waist = false) } })
                    SecondaryButton(stringResource(R.string.prog_add_waist), { a.run { openBodyEntry(waist = true) } })
                }
                is BodyEntry.Weight -> InfoCard(stringResource(R.string.prog_add_weight)) {
                    // Once a day at most, so typing is fine here; the last weight is filled in.
                    NumberField(stringResource(R.string.prog_weight_entry), e.kg, { v -> a.edit { changeBodyEntry(BodyEntry.Weight(v)) } })
                    if (s.problem) Body(stringResource(R.string.prog_entry_problem))
                    PrimaryButton(stringResource(R.string.action_save), { a.run { saveBodyEntry() } }, enabled = e.kg != null)
                    SecondaryButton(stringResource(R.string.action_cancel), { a.run { closeBodyEntry() } })
                }
                is BodyEntry.Waist -> InfoCard(stringResource(R.string.prog_add_waist)) {
                    Note(stringResource(R.string.prog_waist_howto))
                    (0 until 3).forEach { i ->
                        NumberField(stringResource(R.string.prog_waist_reading, i + 1), e.readings.getOrNull(i), { v ->
                            val list = (0 until 3).map { j -> if (j == i) v else e.readings.getOrNull(j) }.filterNotNull()
                            a.edit { changeBodyEntry(BodyEntry.Waist(list)) }
                        })
                    }
                    if (s.problem) Body(stringResource(R.string.prog_entry_problem))
                    PrimaryButton(stringResource(R.string.action_save), { a.run { saveBodyEntry() } }, enabled = e.readings.isNotEmpty())
                    SecondaryButton(stringResource(R.string.action_cancel), { a.run { closeBodyEntry() } })
                }
            }
        }

        // ---------------------------------------------------------------- steps (STEP-001)
        if (o.steps.isNotEmpty()) {
            SectionTitle(stringResource(R.string.prog_steps))
            o.stepTarget?.let { Note(stringResource(R.string.prog_steps_target, count(it))) }
            Sparkline(o.steps.map { it.steps.toDouble() }, stringResource(R.string.prog_chart_desc, stringResource(R.string.prog_steps),
                o.steps.joinToString(", ") { count(it.steps) }), target = o.stepTarget?.toDouble())
            o.steps.reversed().forEach { Body(stringResource(R.string.prog_steps_day, dateText(it.day), count(it.steps))) }
        }
    }
}

@Composable
private fun LiftRow(l: LiftLine) {
    val a = LocalActions.current
    val busy = LocalBusy.current
    val name = exerciseName(l.exerciseId)
    val head = l.e1rm?.let { stringResource(R.string.prog_lift_line, name, kg(it)) } ?: stringResource(R.string.prog_lift_no_max, name)
    val trend = l.trend?.let { t -> when (t.direction) {
        TrendDirection.UP -> stringResource(R.string.prog_trend_up, pct(t.changePct))
        TrendDirection.FLAT -> stringResource(R.string.prog_trend_flat)
        TrendDirection.DOWN -> stringResource(R.string.prog_trend_down, pct(-t.changePct))
    } }
    Column(Modifier.fillMaxWidth().heightIn(min = SecondaryHeight).clickable(enabled = !busy, role = Role.Button) { a.run { openExercise(l.exerciseId) } }
        .padding(vertical = 6.dp)) {
        Text(head, style = MaterialTheme.typography.titleMedium)
        trend?.let { Note(it) }
        val values = l.points.mapNotNull { it.e1rm ?: (it.bestLoad * (1.0 + it.bestReps / 30.0)) }
        if (values.size >= 2) Sparkline(values, stringResource(R.string.prog_chart_desc, name, values.joinToString(", ") { kg(it) }))
    }
    HorizontalDivider()
}

/** Weekly active minutes as bars with the 150-minute goal as a dashed line (PH-001). */
@Composable
private fun WeekBars(weeks: List<WeekLine>) {
    val bar = MaterialTheme.colorScheme.primary
    val met = MaterialTheme.colorScheme.tertiary
    val line = MaterialTheme.colorScheme.onSurfaceVariant
    val goal = P.PH_001.target_equivalent_minutes.toDouble()
    val top = maxOf(goal * 1.2, weeks.maxOf { it.equivalentMinutes })
    val desc = stringResource(R.string.prog_chart_desc, stringResource(R.string.prog_weeks), weeks.joinToString(", ") { minutes(it.equivalentMinutes) })
    Canvas(Modifier.fillMaxWidth().height(96.dp).semantics { contentDescription = desc }) {
        val n = weeks.size
        val slot = size.width / n
        weeks.forEachIndexed { i, w ->
            val h = (w.equivalentMinutes / top * size.height).toFloat()
            drawRect(if (w.whoFloorMet) met else bar, topLeft = Offset(i * slot + slot * 0.2f, size.height - h),
                size = androidx.compose.ui.geometry.Size(slot * 0.6f, h))
        }
        val y = (size.height - goal / top * size.height).toFloat()
        drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
    }
}

/** A small line chart; values carry the meaning in the description too (TalkBack). */
@Composable
internal fun Sparkline(values: List<Double>, description: String, target: Double? = null) {
    val c = MaterialTheme.colorScheme.primary
    val t = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.fillMaxWidth().height(56.dp).padding(vertical = 4.dp).semantics { contentDescription = description }) {
        val all = values + listOfNotNull(target)
        val lo = all.min()
        val hi = all.max().let { if (it - lo < 1e-9) lo + 1.0 else it }
        fun y(v: Double) = (size.height - (v - lo) / (hi - lo) * size.height).toFloat()
        val step = if (values.size > 1) size.width / (values.size - 1) else 0f
        target?.let { drawLine(t, Offset(0f, y(it)), Offset(size.width, y(it)), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))) }
        for (i in 1 until values.size) drawLine(c, Offset((i - 1) * step, y(values[i - 1])), Offset(i * step, y(values[i])), strokeWidth = 5f)
        values.forEachIndexed { i, v -> drawCircle(c, radius = 6f, center = Offset(i * step, y(v))) }
    }
}

private fun pct(x: Double): String = String.format(java.util.Locale.getDefault(), "%.0f", x)
private fun signed(x: Double): String = (if (x > 0) "+" else "") + kg(x)
private fun count(n: Int): String = NumberFormat.getIntegerInstance().format(n)

/** P2: one exercise over time; like it, or ask never to see it again (SUB-002). */
@Composable
fun ExerciseDetailScreen(s: Screen.ExerciseDetail) {
    val a = LocalActions.current
    val h = s.history
    val name = exerciseName(h.exerciseId)
    BackHandler { a.run { openTab(Tab.PROGRESS) } }
    ScreenColumn(name) {
        if (h.points.isEmpty()) Body(stringResource(R.string.ex_none))
        else {
            h.state?.e1rm?.let { Body(stringResource(R.string.ex_max, kg(it))) }
            h.best?.let { Body(stringResource(R.string.ex_title_best, kg(it.bestLoad), it.bestReps, dateText(it.day))) }
            Note(stringResource(R.string.ex_sessions, h.points.size))
            val values = h.points.map { it.e1rm ?: (it.bestLoad * (1.0 + it.bestReps / 30.0)) }
            if (values.size >= 2) Sparkline(values, stringResource(R.string.prog_chart_desc, name, values.joinToString(", ") { kg(it) }))
        }
        h.state?.prescription?.let { p -> Body(stringResource(R.string.ex_next, kg(p.load), p.repTarget)) }
        CheckRow(stringResource(R.string.ex_favourite), h.favourite, { on -> a.run { setFavourite(h.exerciseId, on) } }, enabled = !h.excluded)
        CheckRow(stringResource(R.string.ex_exclude), h.excluded, { on -> a.run { setExcluded(h.exerciseId, on) } })
        Note(stringResource(R.string.ex_exclude_note))
        SecondaryButton(stringResource(R.string.ex_ask), { a.run { openCoach(h.exerciseId) } })
        SecondaryButton(stringResource(R.string.action_back), { a.run { openTab(Tab.PROGRESS) } })
    }
}
