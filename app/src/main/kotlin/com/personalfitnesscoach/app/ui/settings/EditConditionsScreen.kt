package com.personalfitnesscoach.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.personalfitnesscoach.R
import com.personalfitnesscoach.app.flow.OnboardingForm
import com.personalfitnesscoach.app.flow.Screen
import com.personalfitnesscoach.app.ui.InfoCard
import com.personalfitnesscoach.app.ui.LocalActions
import com.personalfitnesscoach.app.ui.PrimaryButton
import com.personalfitnesscoach.app.ui.ScreenColumn
import com.personalfitnesscoach.app.ui.SecondaryButton
import com.personalfitnesscoach.app.ui.Tone
import com.personalfitnesscoach.app.ui.onboarding.ConditionsStep
import com.personalfitnesscoach.app.ui.text.Labels

/** Settings → health conditions (SAF-010, R5-12): the onboarding questions with the stored answers, changeable at any time. */
@Composable
fun EditConditionsScreen(s: Screen.EditConditions) {
    val a = LocalActions.current
    val edit: ((OnboardingForm) -> OnboardingForm) -> Unit = { change -> a.edit { editConditions(change) } }
    BackHandler { a.run { openSettings() } }
    ScreenColumn(null) {
        ConditionsStep(s.form, edit)
        s.problem?.let { InfoCard(stringResource(Labels.onboardingProblem(it)), Tone.ALERT) }
        PrimaryButton(stringResource(R.string.set_save), { a.run { saveConditions() } }, enabled = s.form.noneOfThese || s.form.conditions.isNotEmpty())
        SecondaryButton(stringResource(R.string.action_cancel), { a.run { openSettings() } })
    }
}
