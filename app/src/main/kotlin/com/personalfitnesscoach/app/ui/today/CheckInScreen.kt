package com.personalfitnesscoach.app.ui.today

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.personalfitnesscoach.R
import com.personalfitnesscoach.app.flow.CheckInForm
import com.personalfitnesscoach.app.flow.PainForm
import com.personalfitnesscoach.app.flow.Screen
import com.personalfitnesscoach.app.ui.CheckRow
import com.personalfitnesscoach.app.ui.ChipGroup
import com.personalfitnesscoach.app.ui.DotRow
import com.personalfitnesscoach.app.ui.LocalActions
import com.personalfitnesscoach.app.ui.Note
import com.personalfitnesscoach.app.ui.NumberField
import com.personalfitnesscoach.app.ui.PainFields
import com.personalfitnesscoach.app.ui.PrimaryButton
import com.personalfitnesscoach.app.ui.RadioRow
import com.personalfitnesscoach.app.ui.ScreenColumn
import com.personalfitnesscoach.app.ui.SecondaryButton
import com.personalfitnesscoach.app.ui.SectionTitle
import com.personalfitnesscoach.app.ui.Stepper
import com.personalfitnesscoach.app.ui.text.Labels
import com.personalfitnesscoach.engine.library.GeneratedLibrary
import com.personalfitnesscoach.engine.registry.P

/** T2 check-in: four ratings, optional sleep hours, time, pain, red flags, illness, missing equipment (RDY, SAF-002/003/007, FS-8). */
@Composable
fun CheckInScreen(s: Screen.CheckIn) {
    val a = LocalActions.current
    val f = s.form
    val edit: ((CheckInForm) -> CheckInForm) -> Unit = { change -> a.edit { editCheckIn(change) } }
    BackHandler { a.run { closeToToday() } }
    ScreenColumn(stringResource(R.string.checkin_title)) {
        Note(stringResource(R.string.checkin_normal))
        DotRow(stringResource(R.string.checkin_sleep), stringResource(R.string.checkin_sleep_low), stringResource(R.string.checkin_sleep_high), f.sleep,
            { v -> edit { it.copy(sleep = v) } })
        DotRow(stringResource(R.string.checkin_energy), stringResource(R.string.checkin_energy_low), stringResource(R.string.checkin_energy_high), f.energy,
            { v -> edit { it.copy(energy = v) } })
        DotRow(stringResource(R.string.checkin_soreness), stringResource(R.string.checkin_soreness_low), stringResource(R.string.checkin_soreness_high),
            f.soreness, { v -> edit { it.copy(soreness = v) } })
        DotRow(stringResource(R.string.checkin_stress), stringResource(R.string.checkin_stress_low), stringResource(R.string.checkin_stress_high), f.stress,
            { v -> edit { it.copy(stress = v) } })
        NumberField(stringResource(R.string.checkin_sleep_hours), f.sleepHours, { v -> edit { it.copy(sleepHours = v?.takeIf { h -> h in 0.0..24.0 }) } })
        Stepper(stringResource(R.string.checkin_minutes, f.minutes), { edit { it.copy(minutes = (it.minutes - 5).coerceAtLeast(10)) } },
            { edit { it.copy(minutes = (it.minutes + 5).coerceAtMost(180)) } }, minusEnabled = f.minutes > 10, plusEnabled = f.minutes < 180)

        SectionTitle(stringResource(R.string.checkin_pain_q))
        RadioRow(stringResource(R.string.checkin_pain_none), f.pain == null, { edit { it.copy(pain = null) } })
        RadioRow(stringResource(R.string.checkin_pain_yes), f.pain != null, { edit { it.copy(pain = it.pain ?: PainForm()) } })
        f.pain?.let { p -> PainFields(p) { change -> edit { it.copy(pain = change(it.pain ?: PainForm())) } } }

        SectionTitle(stringResource(R.string.checkin_red_title))
        Note(stringResource(R.string.checkin_red_intro))
        P.SAF_002.symptoms.forEach { id ->
            val label = Labels.RED_FLAGS[id]?.let { stringResource(it) } ?: Labels.fallback(id)
            CheckRow(label, id in f.redFlags, { on -> edit { it.copy(redFlags = if (on) it.redFlags + id else it.redFlags - id) } })
        }
        SectionTitle(stringResource(R.string.checkin_ill_title))
        P.SAF_007.systemic_symptoms.forEach { id ->
            val label = Labels.ILLNESS[id]?.let { stringResource(it) } ?: Labels.fallback(id)
            CheckRow(label, id in f.illness, { on -> edit { it.copy(illness = if (on) it.illness + id else it.illness - id) } })
        }

        // FS-8: equipment today's plan would use; anything ticked is left out for today only.
        val needed = (s.next.slots.flatMap { it.exercise.equipment } +
            s.next.conditioning.flatMap { c -> GeneratedLibrary.modalities.firstOrNull { it.modality == c.modality }?.equipment ?: emptySet() })
            .distinct().filter { it in GeneratedLibrary.equipment }
        if (needed.isNotEmpty()) {
            SectionTitle(stringResource(R.string.checkin_missing_title))
            ChipGroup(needed, { it in f.missingEquipment }, { GeneratedLibrary.equipment[it]?.name ?: it },
                { id -> edit { it.copy(missingEquipment = if (id in it.missingEquipment) it.missingEquipment - id else it.missingEquipment + id) } })
        }
        CheckRow(stringResource(R.string.checkin_away), f.awayFromGym, { on -> edit { it.copy(awayFromGym = on) } })

        val painIncomplete = f.pain != null && (f.pain.region == null || f.pain.kind == null)
        PrimaryButton(stringResource(R.string.checkin_submit), { a.run { submitCheckIn() } }, enabled = !painIncomplete)
        SecondaryButton(stringResource(R.string.action_back), { a.run { closeToToday() } })
    }
}
