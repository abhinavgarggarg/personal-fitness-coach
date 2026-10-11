package com.personalfitnesscoach.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.personalfitnesscoach.R
import com.personalfitnesscoach.app.flow.OnboardingForm
import com.personalfitnesscoach.app.flow.ProfileSection
import com.personalfitnesscoach.app.flow.Screen
import com.personalfitnesscoach.app.ui.InfoCard
import com.personalfitnesscoach.app.ui.LocalActions
import com.personalfitnesscoach.app.ui.PrimaryButton
import com.personalfitnesscoach.app.ui.ScreenColumn
import com.personalfitnesscoach.app.ui.SecondaryButton
import com.personalfitnesscoach.app.ui.Tone
import com.personalfitnesscoach.app.ui.onboarding.EquipmentStep
import com.personalfitnesscoach.app.ui.onboarding.GoalStep
import com.personalfitnesscoach.app.ui.onboarding.LimitationsStep
import com.personalfitnesscoach.app.ui.onboarding.ScheduleStep
import com.personalfitnesscoach.app.ui.text.Labels

/** Settings → goals, week, gym or injuries (D-086): the onboarding page for that part with the stored answers; the plan follows on save. */
@Composable
fun EditProfileScreen(s: Screen.EditProfile) {
    val a = LocalActions.current
    val edit: ((OnboardingForm) -> OnboardingForm) -> Unit = { change -> a.edit { editProfile(change) } }
    BackHandler { a.run { openSettings() } }
    ScreenColumn(null) {
        when (s.section) {
            ProfileSection.GOALS -> GoalStep(s.form, s.goals, edit)
            ProfileSection.SCHEDULE -> ScheduleStep(s.form, edit)
            ProfileSection.EQUIPMENT -> EquipmentStep(s.form, a.controller, edit)
            ProfileSection.INJURIES -> LimitationsStep(s.form, edit)
        }
        s.problem?.let { InfoCard(stringResource(Labels.onboardingProblem(it)), Tone.ALERT) }
        val ok = when (s.section) {
            ProfileSection.GOALS -> s.form.goals.isNotEmpty()
            ProfileSection.SCHEDULE -> s.form.availableDays.size >= s.form.daysPerWeek
            else -> true
        }
        PrimaryButton(stringResource(R.string.profile_save), { a.run { saveProfile() } }, enabled = ok)
        SecondaryButton(stringResource(R.string.action_cancel), { a.run { openSettings() } })
    }
}
