package com.personalfitnesscoach.app.ui.today

import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.personalfitnesscoach.R
import com.personalfitnesscoach.app.flow.PreviewModel
import com.personalfitnesscoach.app.ui.Body
import com.personalfitnesscoach.app.ui.ChipGroup
import com.personalfitnesscoach.app.ui.InfoCard
import com.personalfitnesscoach.app.ui.LocalActions
import com.personalfitnesscoach.app.ui.Note
import com.personalfitnesscoach.app.ui.PrimaryButton
import com.personalfitnesscoach.app.ui.ScreenColumn
import com.personalfitnesscoach.app.ui.SecondaryButton
import com.personalfitnesscoach.app.ui.SectionTitle
import com.personalfitnesscoach.app.ui.Stepper
import com.personalfitnesscoach.app.ui.SwitchRow
import com.personalfitnesscoach.app.ui.Tone
import com.personalfitnesscoach.app.ui.kg
import com.personalfitnesscoach.app.ui.minutes
import com.personalfitnesscoach.app.ui.text.Labels
import com.personalfitnesscoach.app.ui.text.ReasonTexts
import com.personalfitnesscoach.app.ui.text.Reasons
import com.personalfitnesscoach.app.ui.workout.conditioningLine
import com.personalfitnesscoach.app.ui.workout.doseText
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.registry.P

/** T3: the validated session before it starts (SAF-008: nothing else is ever shown), with the level, time and why. */
@Composable
fun PreviewScreen(m: PreviewModel) {
    val a = LocalActions.current
    val w = m.session.workout
    BackHandler { a.run { closeToToday() } }
    ScreenColumn(stringResource(R.string.preview_title)) {
        Text(stringResource(Labels.template(m.session.day.template)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        InfoCard(stringResource(R.string.preview_level, stringResource(Labels.tier(m.tier))) + "\n" + stringResource(Labels.tierDescription(m.tier)),
            if (m.tier == Tier.FULL) Tone.GOOD else Tone.WARN)
        if (m.canChoose.isNotEmpty()) {
            SectionTitle(stringResource(R.string.preview_choose_level))
            ChipGroup(m.canChoose, { false }, { stringResource(Labels.tier(it)) }, { t -> a.run { chooseTier(t) } })
            if (m.canChoose.any { it.ordinal > m.tier.ordinal }) Note(stringResource(R.string.preview_harder_warning))
        }
        Stepper(stringResource(R.string.preview_time, m.minutes), { a.run { previewMinutes(m.minutes - 5) } }, { a.run { previewMinutes(m.minutes + 5) } },
            minusEnabled = m.minutes > 10, plusEnabled = m.minutes < 180)
        if (m.minutes < 45 || m.session.express) {
            SwitchRow(stringResource(R.string.preview_express), m.session.express, { on -> a.run { previewExpress(on) } })
        }
        SwitchRow(stringResource(R.string.preview_away), m.session.awayFromGym, { on -> a.run { previewAway(on) } })

        val nothing = w.items.isEmpty() && w.conditioning.isEmpty()
        when {
            w.followCareProvider.isNotEmpty() -> InfoCard(stringResource(R.string.preview_follow_provider), Tone.ALERT)
            nothing || m.tier == Tier.RECOVERY -> InfoCard(stringResource(R.string.preview_rest_day) + "\n" + stringResource(R.string.tier_desc_recovery))
            else -> {
                Note(stringResource(R.string.today_about_minutes, Math.round(w.plannedMinutes).toInt()))
                Body(stringResource(R.string.preview_warmup, minutes(w.warmupMinutes)))
                w.boneLoading?.let { Body(stringResource(R.string.preview_bone, minutes(it.minutes))) }
                if (w.conditioningFirst) w.conditioning.forEachIndexed { i, c -> Body(conditioningLine(c.modality, c.zone, c.workMinutes, w.intervals.getOrNull(i), w.circuits.getOrNull(i))) }
                w.items.forEach { it ->
                    val dose = doseText(it.reps, it.unit, it.perSide)
                    val what = if (it.calibrating && it.load != null) stringResource(R.string.preview_finding_weight)
                        else it.load?.let { l -> stringResource(R.string.at_load, dose, kg(l)) } ?: dose
                    Text(it.exercise.name, style = MaterialTheme.typography.titleMedium)
                    Note(stringResource(R.string.preview_sets_line, it.sets, what))
                }
                if (!w.conditioningFirst) w.conditioning.forEachIndexed { i, c -> Body(conditioningLine(c.modality, c.zone, c.workMinutes, w.intervals.getOrNull(i), w.circuits.getOrNull(i))) }
                if (w.balanceDrills.isNotEmpty()) Body(stringResource(R.string.preview_balance, minutes(w.balanceDrills.sumOf { d -> d.seconds } / 60.0)))
                Body(stringResource(R.string.preview_cooldown, minutes(w.cooldown.sumOf { d -> d.seconds } / 60.0)))
                if (w.effortByFeel) Note(stringResource(R.string.preview_effort_by_feel))
                PrimaryButton(stringResource(R.string.preview_start), { a.run { startWorkout() } })
            }
        }
        if (w.conditionPrompts.isNotEmpty() || w.stopSigns.isNotEmpty()) {
            SectionTitle(stringResource(R.string.preview_conditions))
            w.conditionPrompts.distinct().forEach { Note(it) }
            if (w.stopSigns.isNotEmpty()) {
                Text(stringResource(R.string.preview_stop_signs), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                w.stopSigns.distinct().forEach { Note(it) }
            }
        }
        val why = Reasons.shown(m.reasons.map { it.reason.name })
        if (why.isNotEmpty()) {
            SectionTitle(stringResource(R.string.preview_why))
            why.forEach { Note(stringResource(ReasonTexts.of(it))) }
        }
        SecondaryButton(stringResource(R.string.preview_back_today), { a.run { closeToToday() } })
        if (m.minutes < P.TIME_002.express_below_minutes) Note(stringResource(R.string.reason_time_express_offered))
    }
}
