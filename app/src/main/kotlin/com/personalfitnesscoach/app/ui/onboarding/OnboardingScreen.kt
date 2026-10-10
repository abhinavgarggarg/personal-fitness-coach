@file:OptIn(ExperimentalMaterial3Api::class)

package com.personalfitnesscoach.app.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.personalfitnesscoach.R
import com.personalfitnesscoach.app.flow.AppController
import com.personalfitnesscoach.app.flow.ConditionAnswers
import com.personalfitnesscoach.app.flow.ExperienceBand
import com.personalfitnesscoach.app.flow.GymPreset
import com.personalfitnesscoach.app.flow.NumberEntry
import com.personalfitnesscoach.app.flow.OnboardingForm
import com.personalfitnesscoach.app.flow.Screen
import com.personalfitnesscoach.app.flow.ScreeningQuestion
import com.personalfitnesscoach.app.ui.AnswerRow
import com.personalfitnesscoach.app.ui.Body
import com.personalfitnesscoach.app.ui.CheckRow
import com.personalfitnesscoach.app.ui.ChipGroup
import com.personalfitnesscoach.app.ui.Heading
import com.personalfitnesscoach.app.ui.InfoCard
import com.personalfitnesscoach.app.ui.LocalActions
import com.personalfitnesscoach.app.ui.Note
import com.personalfitnesscoach.app.ui.NumberField
import com.personalfitnesscoach.app.ui.PrimaryButton
import com.personalfitnesscoach.app.ui.RadioRow
import com.personalfitnesscoach.app.ui.ScreenColumn
import com.personalfitnesscoach.app.ui.SecondaryButton
import com.personalfitnesscoach.app.ui.SectionTitle
import com.personalfitnesscoach.app.ui.Stepper
import com.personalfitnesscoach.app.ui.SwitchRow
import com.personalfitnesscoach.app.ui.Tone
import com.personalfitnesscoach.app.ui.text.Labels
import com.personalfitnesscoach.data.core.onboarding.OnboardingStep
import com.personalfitnesscoach.engine.library.GeneratedLibrary
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.EquipmentClass
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.progress.ReferenceSex
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.safety.ClearanceScope
import com.personalfitnesscoach.engine.safety.ConditionEntry
import com.personalfitnesscoach.engine.safety.ControlStatus
import com.personalfitnesscoach.engine.safety.GeneratedConditions
import com.personalfitnesscoach.engine.safety.ScreeningMode
import com.personalfitnesscoach.engine.safety.ScreeningResult

/** FS-1 onboarding (Phase 2 O1–O9 with Research Update 1.1's conditions, goal and own numbers). Each step is saved when submitted. */
@Composable
fun OnboardingScreen(s: Screen.Onboarding) {
    val a = LocalActions.current
    val total = OnboardingStep.entries.size
    val edit: ((OnboardingForm) -> OnboardingForm) -> Unit = { change -> a.edit { editOnboarding(change) } }
    BackHandler(enabled = s.step != OnboardingStep.WELCOME) { a.run { backOnboarding() } }
    ScreenColumn(null) {
        if (s.step != OnboardingStep.WELCOME) Note(stringResource(R.string.onb_step, s.step.ordinal + 1, total))
        when (s.step) {
            OnboardingStep.WELCOME -> Welcome()
            OnboardingStep.SCREENING -> ScreeningStep(s.form, edit)
            OnboardingStep.ABOUT_YOU -> AboutYou(s.form, s.screening, edit)
            OnboardingStep.CONDITIONS -> ConditionsStep(s.form, edit)
            OnboardingStep.GOAL -> GoalStep(s, edit)
            OnboardingStep.SCHEDULE -> ScheduleStep(s.form, edit)
            OnboardingStep.EQUIPMENT -> EquipmentStep(s.form, a.controller, edit)
            OnboardingStep.LIMITATIONS -> LimitationsStep(s.form, edit)
            OnboardingStep.NUMBERS -> NumbersStep(s.form, a.controller, edit)
            OnboardingStep.PLAN -> PlanStep(s)
            OnboardingStep.ALERTS -> AlertsStep()
        }
        s.problem?.let { InfoCard(stringResource(Labels.onboardingProblem(it)), Tone.ALERT) }
        if (s.step != OnboardingStep.WELCOME && s.step != OnboardingStep.ALERTS) {
            PrimaryButton(stringResource(R.string.action_continue), { a.run { submitOnboarding() } }, enabled = canContinue(s))
            SecondaryButton(stringResource(R.string.action_back), { a.run { backOnboarding() } })
        }
    }
}

/** Required answers before Continue (the controller and the data layer check them again). */
private fun canContinue(s: Screen.Onboarding): Boolean = when (s.step) {
    OnboardingStep.SCREENING -> s.form.screening.size == ScreeningQuestion.entries.size
    OnboardingStep.ABOUT_YOU -> s.form.birthYear != null && s.form.months != null
    OnboardingStep.CONDITIONS -> s.form.noneOfThese || s.form.conditions.isNotEmpty()
    OnboardingStep.GOAL -> s.form.goals.isNotEmpty()
    else -> true
}

// ================================================================================================ O1
@Composable
private fun ColumnScope.Welcome() {
    val a = LocalActions.current
    Note(stringResource(R.string.app_name))
    Heading(stringResource(R.string.onb_welcome_title))
    Body(stringResource(R.string.onb_welcome_body1))
    InfoCard(stringResource(R.string.onb_welcome_body2), Tone.WARN)
    Body(stringResource(R.string.onb_welcome_body3))
    PrimaryButton(stringResource(R.string.onb_welcome_accept), { a.run { submitOnboarding() } })
}

// ================================================================================================ O2 SAF-001
@Composable
private fun ScreeningStep(f: OnboardingForm, edit: ((OnboardingForm) -> OnboardingForm) -> Unit) {
    Heading(stringResource(R.string.onb_screen_title))
    Body(stringResource(R.string.onb_screen_intro))
    ScreeningQuestion.entries.forEach { q ->
        AnswerRow(stringResource(Labels.screening(q)), f.screening[q], { yes -> edit { it.copy(screening = it.screening + (q to yes)) } })
    }
    if (f.screening.size < ScreeningQuestion.entries.size) Note(stringResource(R.string.screen_unanswered))
}

// ================================================================================================ O3
@Composable
private fun AboutYou(f: OnboardingForm, screening: ScreeningResult?, edit: ((OnboardingForm) -> OnboardingForm) -> Unit) {
    screening?.let { ScreeningOutcome(it) }
    Heading(stringResource(R.string.onb_about_title))
    NumberField(stringResource(R.string.onb_birth_year), f.birthYear?.toDouble(), { v -> edit { it.copy(birthYear = v?.toInt()) } }, decimals = false)
    SectionTitle(stringResource(R.string.onb_experience_q))
    ExperienceBand.entries.forEach { b ->
        RadioRow(stringResource(Labels.experience(b)), f.months == b, { edit { it.copy(months = b) } })
    }
    SectionTitle(stringResource(R.string.onb_comfortable_q))
    P.EXP_001.key_lifts.forEach { id ->
        val label = Labels.keyLift(id)?.let { stringResource(it) } ?: Labels.fallback(id)
        CheckRow(label, id in f.comfortable, { on -> edit { it.copy(comfortable = if (on) it.comfortable + id else it.comfortable - id) } })
    }
    CheckRow(stringResource(R.string.onb_structured), f.structured, { on -> edit { it.copy(structured = on) } })
    CheckRow(stringResource(R.string.onb_confident_effort), f.confidentEffort, { on -> edit { it.copy(confidentEffort = on) } })
    SectionTitle(stringResource(R.string.onb_optional))
    NumberField(stringResource(R.string.onb_bodyweight), f.bodyweightKg, { v -> edit { it.copy(bodyweightKg = v?.takeIf { x -> x in 25.0..400.0 }) } })
    Note(stringResource(R.string.onb_bodyweight_why))
    Text(stringResource(R.string.onb_reference_sex_q), style = MaterialTheme.typography.titleMedium)
    Note(stringResource(R.string.onb_reference_sex_why))
    RadioRow(stringResource(R.string.sex_man), f.referenceSex == ReferenceSex.MAN, { edit { it.copy(referenceSex = ReferenceSex.MAN) } })
    RadioRow(stringResource(R.string.sex_woman), f.referenceSex == ReferenceSex.WOMAN, { edit { it.copy(referenceSex = ReferenceSex.WOMAN) } })
    RadioRow(stringResource(R.string.sex_skip), f.referenceSex == null, { edit { it.copy(referenceSex = null) } })
}

/** SAF-001's outcome, shown once after the questions. */
@Composable
private fun ScreeningOutcome(r: ScreeningResult) {
    val (text, tone) = when {
        r.clinicianGuidance -> R.string.screen_result_clinician to Tone.ALERT
        r.mode == ScreeningMode.CONSERVATIVE -> R.string.screen_result_conservative to Tone.ALERT
        r.mode == ScreeningMode.MODERATE_ONLY -> R.string.screen_result_moderate to Tone.WARN
        else -> R.string.screen_result_standard to Tone.GOOD
    }
    InfoCard(stringResource(text), tone) {
        if (r.limitationNote) Text(stringResource(R.string.screen_result_note), style = MaterialTheme.typography.bodyMedium)
    }
}

// ================================================================================================ conditions (SAF-010, CR-001)
private val HBP_IDS = setOf("hbp_controlled", "hbp_not_controlled")

/** The conditions page; also used by Settings → health conditions (R5-12). */
@Composable
internal fun ConditionsStep(f: OnboardingForm, edit: ((OnboardingForm) -> OnboardingForm) -> Unit) {
    Heading(stringResource(R.string.onb_cond_title))
    Body(stringResource(R.string.onb_cond_intro))
    CheckRow(stringResource(R.string.onb_cond_none), f.noneOfThese, { on -> edit { it.copy(noneOfThese = on, conditions = if (on) emptyMap() else it.conditions) } })
    val groups = GeneratedConditions.entries.filter { it.id !in HBP_IDS }.groupBy { it.group }
    val order = listOf("heart_and_circulation") + groups.keys.filter { it != "heart_and_circulation" }
    for (g in order.distinct()) {
        SectionTitle(stringResource(Labels.conditionGroup(g)))
        if (g == "heart_and_circulation") ConditionItem(AppController.HBP, stringResource(R.string.cond_hbp), null, f, edit)
        groups[g].orEmpty().forEach { e -> ConditionItem(e.id, e.name, e, f, edit) }
    }
}

@Composable
private fun ConditionItem(key: String, name: String, e: ConditionEntry?, f: OnboardingForm, edit: ((OnboardingForm) -> OnboardingForm) -> Unit) {
    val picked = f.conditions[key]
    CheckRow(name, picked != null, { on ->
        edit { it.copy(noneOfThese = false, conditions = if (on) it.conditions + (key to ConditionAnswers()) else it.conditions - key) }
    })
    if (picked == null) return
    val change: ((ConditionAnswers) -> ConditionAnswers) -> Unit = { c -> edit { it.copy(conditions = it.conditions + (key to c(it.conditions[key] ?: ConditionAnswers()))) } }
    Card(Modifier.fillMaxWidth().padding(start = 12.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { ConditionFollowUps(key, e, picked, change) }
    }
}

/** The questions the table asks for one picked condition; each answer maps to a stored field the engine reads (SAF-010). */
@Composable
private fun ColumnScope.ConditionFollowUps(key: String, e: ConditionEntry?, c: ConditionAnswers, change: ((ConditionAnswers) -> ConditionAnswers) -> Unit) {
    // The rule as the engine applies it: some answers (cancer in treatment, spread to the bones, lymphoedema) make the doctor's OK required (R5-16).
    val clearance = when {
        key == AppController.HBP -> if (c.status == ControlStatus.YES) "none" else "before_vigorous"
        e == null -> "always"
        e.clearanceAlwaysIf.any { it in c.subFlags } -> "always"
        else -> e.clearance
    }
    if (key == AppController.HBP || e?.askControlStatus == true) {
        Text(stringResource(R.string.onb_cond_status_q), style = MaterialTheme.typography.bodyLarge)
        RadioRow(stringResource(R.string.action_yes), c.status == ControlStatus.YES, { change { it.copy(status = ControlStatus.YES) } })
        RadioRow(stringResource(R.string.action_no), c.status == ControlStatus.NO, { change { it.copy(status = ControlStatus.NO) } })
        RadioRow(stringResource(R.string.action_not_sure), c.status == ControlStatus.NOT_SURE || c.status == null, { change { it.copy(status = ControlStatus.NOT_SURE) } })
        Note(stringResource(R.string.onb_cond_status_note))
    }
    if (e != null) {
        val flags = (e.subFlags.keys + e.clearanceAlwaysIf + e.impactNoneIf).distinct()
        if (flags.isNotEmpty()) {
            Text(stringResource(R.string.onb_cond_subflags), style = MaterialTheme.typography.bodyLarge)
            flags.forEach { k ->
                val label = Labels.SUB_FLAGS[k]?.let { stringResource(it) } ?: Labels.fallback(k)
                CheckRow(label, k in c.subFlags, { on -> change { it.copy(subFlags = if (on) it.subFlags + k else it.subFlags - k) } })
                e.subFlags[k]?.let { meaning -> if (k in c.subFlags) Note(meaning.replaceFirstChar { ch -> ch.uppercase() }) }
            }
        }
        if (e.blockIf.isNotEmpty()) {
            e.blockIf.forEach { Note(it) }
            CheckRow(stringResource(R.string.onb_cond_block_yes), c.blockIfYes, { on -> change { it.copy(blockIfYes = on) } })
        }
        if (e.attestIf.isNotEmpty()) {
            // Three plain answers (R5-17): none applies; one applies and the provider OK'd exercise; one applies without that OK, which
            // means following the provider instead of a training plan.
            e.attestIf.forEach { Note(it) }
            RadioRow(stringResource(R.string.onb_cond_attest_none), !c.attested && !c.attestNotOk, { change { it.copy(attested = false, attestNotOk = false) } })
            RadioRow(stringResource(R.string.onb_cond_attest_ok), c.attested, { change { it.copy(attested = true, attestNotOk = false) } })
            RadioRow(stringResource(R.string.onb_cond_attest_not_ok), c.attestNotOk, { change { it.copy(attested = false, attestNotOk = true) } })
        }
        if (e.impactUnlockOptIn) {
            CheckRow(stringResource(R.string.onb_cond_impact_opt_in), c.impactOptIn, { on -> change { it.copy(impactOptIn = on) } })
        }
        if (e.id == "pregnancy") {
            NumberField(stringResource(R.string.onb_cond_pregnancy_week), c.pregnancyWeek?.toDouble(),
                { v -> change { it.copy(pregnancyWeek = v?.toInt()?.takeIf { w -> w in 1..45 }) } }, decimals = false)
        }
        e.avoidSupineAnyTimeIf?.let { t ->
            CheckRow(t.replaceFirstChar { ch -> ch.uppercase() }, c.supineUncomfortable, { on -> change { it.copy(supineUncomfortable = on) } })
        }
        if (e.maxZoneIfPreviouslyVigorousAndOk != null) {
            CheckRow(stringResource(R.string.onb_cond_prev_vigorous), c.previouslyVigorous, { on -> change { it.copy(previouslyVigorous = on) } })
        }
        if (e.impact == "only_if_already_doing_it") {
            CheckRow(stringResource(R.string.onb_cond_already_impact), c.alreadyDoingImpact, { on -> change { it.copy(alreadyDoingImpact = on) } })
        }
        if (e.phases.isNotEmpty()) {
            NumberField(stringResource(R.string.onb_cond_weeks_since_birth), c.weeksSinceBirth?.toDouble(),
                { v -> change { it.copy(weeksSinceBirth = v?.toInt()?.takeIf { w -> w in 0..52 }) } }, decimals = false)
            if (e.attestIf.isEmpty()) {
                CheckRow(stringResource(R.string.onb_cond_postpartum_healed), c.attested, { on -> change { it.copy(attested = on) } })
            }
        }
        if (e.impactChecks.isNotEmpty()) {
            Text(stringResource(R.string.onb_cond_impact_checks), style = MaterialTheme.typography.bodyLarge)
            e.impactChecks.forEach { Note(it) }
            CheckRow(stringResource(R.string.action_yes), c.impactChecksPassed, { on -> change { it.copy(impactChecksPassed = on) } })
        }
        if (e.flareJointLimits.isNotEmpty() || e.flareAvoidTags.isNotEmpty()) {
            CheckRow(stringResource(R.string.onb_cond_flare), c.flare, { on -> change { it.copy(flare = on) } })
        }
    }
    Text(stringResource(when (clearance) {
        "none" -> R.string.onb_cond_clearance_none
        "suggest" -> R.string.onb_cond_clearance_suggest
        "before_vigorous" -> R.string.onb_cond_clearance_before_vigorous
        else -> R.string.onb_cond_clearance_always
    }), style = MaterialTheme.typography.bodyMedium)
    if (clearance != "none") {
        Text(stringResource(R.string.onb_cond_clearance_q), style = MaterialTheme.typography.bodyLarge)
        ClearanceScope.entries.forEach { sc ->
            CheckRow(stringResource(Labels.clearance(sc)), sc in c.clearance, { on -> change { it.copy(clearance = if (on) it.clearance + sc else it.clearance - sc) } })
        }
    }
}

// ================================================================================================ O6 goal (FL-001)
@Composable
private fun GoalStep(s: Screen.Onboarding, edit: ((OnboardingForm) -> OnboardingForm) -> Unit) {
    val f = s.form
    val options = s.goals
    Heading(stringResource(R.string.onb_goal_title))
    Body(stringResource(R.string.onb_goal_intro))
    val offered = options?.offered ?: emptyList()
    offered.forEach { g ->
        val rank = f.goals.indexOf(g)
        val label = stringResource(Labels.goal(g)) + (if (rank >= 0) " · " + stringResource(R.string.onb_goal_rank, rank + 1) else "") +
            (if (options?.default == g) " · " + stringResource(R.string.onb_goal_suggested) else "")
        CheckRow(label, rank >= 0, { on ->
            edit { it.copy(goals = if (on) (it.goals + g).distinct().take(3) else it.goals - g) }
        })
    }
    if (options?.weightFeatures != false) {
        SwitchRow(stringResource(R.string.onb_weight_off), f.weightFeaturesOff, { on -> edit { it.copy(weightFeaturesOff = on) } })
    }
    if (com.personalfitnesscoach.engine.program.Goal.FAT_LOSS in f.goals && !f.weightFeaturesOff && options?.weightFeatures != false) {
        val w = P.FL_001.expected_weight_change_kg.map { kotlin.math.abs(it) }.sorted()
        val waist = P.FL_001.expected_waist_change_cm.map { kotlin.math.abs(it) }.sorted()
        val mins = P.FL_001.aerobic_minutes_range
        InfoCard(stringResource(R.string.onb_goal_expectation, w[0], w[1], waist[0], waist[1], mins[0], mins[1]))
    }
}

// ================================================================================================ O4 schedule
@Composable
private fun ScheduleStep(f: OnboardingForm, edit: ((OnboardingForm) -> OnboardingForm) -> Unit) {
    Heading(stringResource(R.string.onb_sched_title))
    Stepper(stringResource(R.string.onb_days_per_week, f.daysPerWeek), { edit { it.copy(daysPerWeek = (it.daysPerWeek - 1).coerceAtLeast(1)) } },
        { edit { it.copy(daysPerWeek = (it.daysPerWeek + 1).coerceAtMost(6)) } }, minusEnabled = f.daysPerWeek > 1, plusEnabled = f.daysPerWeek < 6)
    SectionTitle(stringResource(R.string.onb_available_days))
    WeekdayChips(f.availableDays) { d -> edit { it.copy(availableDays = it.availableDays.toggle(d), preferredDays = it.preferredDays - (if (d in it.availableDays) setOf(d) else emptySet())) } }
    SectionTitle(stringResource(R.string.onb_preferred_days))
    WeekdayChips(f.preferredDays, enabledDays = f.availableDays) { d -> edit { it.copy(preferredDays = it.preferredDays.toggle(d)) } }
    Text(stringResource(R.string.onb_session_length, f.sessionMinutes), style = MaterialTheme.typography.titleMedium)
    Slider(value = f.sessionMinutes.toFloat(), onValueChange = { v -> edit { it.copy(sessionMinutes = (Math.round(v / 5f) * 5).coerceIn(20, 120)) } },
        valueRange = 20f..120f, steps = 19)
}

private fun Set<Int>.toggle(d: Int): Set<Int> = if (d in this) this - d else this + d

@Composable
private fun WeekdayChips(selected: Set<Int>, enabledDays: Set<Int> = (0..6).toSet(), onToggle: (Int) -> Unit) {
    ChipGroup((0..6).filter { it in enabledDays }, { it in selected }, { stringResource(Labels.weekday(it)) }, onToggle)
}

// ================================================================================================ O5 equipment (PROG-003, MOD-001)
@Composable
private fun EquipmentStep(f: OnboardingForm, c: AppController, edit: ((OnboardingForm) -> OnboardingForm) -> Unit) {
    Heading(stringResource(R.string.onb_equip_title))
    Body(stringResource(R.string.onb_equip_intro))
    ChipGroup(GymPreset.entries, { it == f.gymPreset }, { stringResource(Labels.preset(it)) }, { p -> edit { it.copy(gymPreset = p, gym = c.preset(p)) } })
    SectionTitle(stringResource(R.string.onb_equip_items))
    GeneratedLibrary.equipment.values.sortedWith(compareBy<com.personalfitnesscoach.engine.library.EquipmentInfo>({ it.equipmentClass.ordinal }, { it.name })).forEach { item ->
        CheckRow(item.name, item.id in f.gym, { on -> edit { it.copy(gym = if (on) it.gym + item.id else it.gym - item.id, gymPreset = null) } })
    }
    SectionTitle(stringResource(R.string.onb_increments))
    val inc = f.increments
    val classes = f.gym.mapNotNull { GeneratedLibrary.equipment[it]?.equipmentClass }.toSet()
    if ("barbell" in f.gym) {
        NumberField(stringResource(R.string.onb_bar_kg), inc.barKg, { v -> v?.takeIf { it in 5.0..30.0 }?.let { x -> edit { it.copy(increments = it.increments.copy(barKg = x)) } } })
        NumberField(stringResource(R.string.onb_smallest_plate), inc.smallestPlateKg,
            { v -> v?.takeIf { it in 0.25..10.0 }?.let { x -> edit { it.copy(increments = it.increments.copy(smallestPlateKg = x)) } } })
    }
    if ("dumbbells" in f.gym) {
        NumberField(stringResource(R.string.onb_db_min), inc.dumbbellMinKg, { v -> v?.takeIf { it in 0.5..50.0 }?.let { x -> edit { it.copy(increments = it.increments.copy(dumbbellMinKg = x)) } } })
        NumberField(stringResource(R.string.onb_db_max), inc.dumbbellMaxKg, { v -> v?.takeIf { it in 1.0..100.0 }?.let { x -> edit { it.copy(increments = it.increments.copy(dumbbellMaxKg = x)) } } })
        NumberField(stringResource(R.string.onb_db_step), inc.dumbbellStepKg, { v -> v?.takeIf { it in 0.5..10.0 }?.let { x -> edit { it.copy(increments = it.increments.copy(dumbbellStepKg = x)) } } })
    }
    if (EquipmentClass.MACHINE in classes || EquipmentClass.CABLE in classes) {
        NumberField(stringResource(R.string.onb_stack_step), inc.stackStepKg, { v -> v?.takeIf { it in 0.5..20.0 }?.let { x -> edit { it.copy(increments = it.increments.copy(stackStepKg = x)) } } })
        NumberField(stringResource(R.string.onb_stack_max), inc.stackMaxKg, { v -> v?.takeIf { it in 10.0..400.0 }?.let { x -> edit { it.copy(increments = it.increments.copy(stackMaxKg = x)) } } })
    }
    SectionTitle(stringResource(R.string.onb_exclude_title))
    Note(stringResource(R.string.onb_exclude_intro))
    Modality.entries.filter { it.userSelectable }.forEach { m ->
        val name = GeneratedLibrary.modalities.firstOrNull { it.modality == m }?.name ?: Labels.fallback(m.name.lowercase())
        CheckRow(name, m in f.excludedModalities, { on -> edit { it.copy(excludedModalities = if (on) it.excludedModalities + m else it.excludedModalities - m) } })
    }
}

// ================================================================================================ O7 limitations (IND-001)
@Composable
private fun LimitationsStep(f: OnboardingForm, edit: ((OnboardingForm) -> OnboardingForm) -> Unit) {
    Heading(stringResource(R.string.onb_limits_title))
    Body(stringResource(R.string.onb_limits_intro))
    Joint.entries.forEach { j ->
        CheckRow(stringResource(Labels.joint(j)), j in f.joints, { on -> edit { it.copy(joints = if (on) it.joints + j else it.joints - j) } })
    }
}

// ================================================================================================ own numbers (CAL-002)
@Composable
private fun NumbersStep(f: OnboardingForm, c: AppController, edit: ((OnboardingForm) -> OnboardingForm) -> Unit) {
    Heading(stringResource(R.string.onb_numbers_title))
    Body(stringResource(R.string.onb_numbers_intro))
    var open by remember { mutableStateOf(f.numbers.keys) }
    c.numberCandidates(f.gym).forEach { id ->
        val ex = Library[id] ?: return@forEach
        val n = f.numbers[id]
        CheckRow(ex.name, id in open, { on ->
            open = if (on) open + id else open - id
            if (!on) edit { it.copy(numbers = it.numbers - id) }
        })
        if (id in open) {
            val set: ((NumberEntry) -> NumberEntry) -> Unit = { ch -> edit { it.copy(numbers = it.numbers + (id to ch(it.numbers[id] ?: NumberEntry()))) } }
            Card(Modifier.fillMaxWidth().padding(start = 12.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(stringResource(R.string.onb_numbers_load), n?.loadKg, { v -> set { it.copy(loadKg = v) } }, Modifier.weight(1f))
                        NumberField(stringResource(R.string.onb_numbers_reps), n?.reps?.toDouble(), { v -> set { it.copy(reps = v?.toInt()) } }, Modifier.weight(1f), decimals = false)
                    }
                    CheckRow(stringResource(R.string.onb_numbers_best), n?.bestLift == true, { on -> set { it.copy(bestLift = on) } })
                    NumberField(stringResource(R.string.onb_numbers_days_ago), (n?.daysAgo ?: 7).toDouble(), { v -> set { it.copy(daysAgo = (v?.toInt() ?: 0).coerceIn(0, 3650)) } }, decimals = false)
                }
            }
        }
    }
}

// ================================================================================================ O8 plan
@Composable
private fun PlanStep(s: Screen.Onboarding) {
    Heading(stringResource(R.string.onb_plan_title))
    Body(stringResource(R.string.onb_plan_intro))
    val plan = s.plan ?: return
    plan.blocks.forEach { b ->
        val line = stringResource(R.string.onb_plan_block, stringResource(Labels.block(b.type.kind)), b.loadingWeeks) +
            if (b.followedByDeloadOrPivot) ", " + stringResource(R.string.onb_plan_then_easy) else ""
        Body(line)
    }
    Note(stringResource(R.string.onb_plan_total, plan.totalWeeks))
}

// ================================================================================================ O9 alerts
@Composable
private fun AlertsStep() {
    val a = LocalActions.current
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> a.run { submitOnboarding() } }
    Heading(stringResource(R.string.onb_alerts_title))
    Body(stringResource(R.string.onb_alerts_body))
    PrimaryButton(stringResource(R.string.onb_alerts_allow), {
        val needs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needs) ask.launch(Manifest.permission.POST_NOTIFICATIONS) else a.run { submitOnboarding() }
    })
    SecondaryButton(stringResource(R.string.onb_alerts_skip), { a.run { submitOnboarding() } })
    SecondaryButton(stringResource(R.string.action_back), { a.run { backOnboarding() } })
}
