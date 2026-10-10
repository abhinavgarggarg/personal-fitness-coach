package com.personalfitnesscoach.data.core.onboarding

import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.model.ConditionsRecord
import com.personalfitnesscoach.data.core.model.EquipmentRecord
import com.personalfitnesscoach.data.core.model.KnownNumber
import com.personalfitnesscoach.data.core.model.PreferencesRecord
import com.personalfitnesscoach.data.core.model.Profile
import com.personalfitnesscoach.data.core.model.ProgramRecord
import com.personalfitnesscoach.data.core.model.ScreeningRecord
import com.personalfitnesscoach.data.core.model.SettingsRecord
import com.personalfitnesscoach.data.core.model.StoredCondition
import com.personalfitnesscoach.data.core.model.WeightRecord
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.engine.calc.Inventory
import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.program.Blueprint
import com.personalfitnesscoach.engine.program.ExperienceAnswers
import com.personalfitnesscoach.engine.program.Experience
import com.personalfitnesscoach.engine.program.FatLoss
import com.personalfitnesscoach.engine.program.Goal
import com.personalfitnesscoach.engine.program.GoalContext
import com.personalfitnesscoach.engine.program.GoalOptions
import com.personalfitnesscoach.engine.program.Program
import com.personalfitnesscoach.engine.progress.ReferenceSex
import com.personalfitnesscoach.engine.safety.Conditions
import com.personalfitnesscoach.engine.safety.Screening
import com.personalfitnesscoach.engine.safety.ScreeningAnswers
import com.personalfitnesscoach.engine.safety.ScreeningResult

/**
 * Onboarding steps (Phase 2 O1–O9, with Research Update 1.1's health conditions, goal and own numbers). Each answer is saved as it is
 * given and the step moves on, so an interrupted onboarding resumes where it stopped (FS-1).
 */
enum class OnboardingStep { WELCOME, SCREENING, ABOUT_YOU, CONDITIONS, GOAL, SCHEDULE, EQUIPMENT, LIMITATIONS, NUMBERS, PLAN, ALERTS }

/** Why an answer was not accepted (the screen shows the matching message). */
enum class OnboardingProblem { UNDER_18, BIRTH_YEAR_IMPLAUSIBLE, NO_DAYS, TOO_FEW_DAYS_AVAILABLE, NO_PRIORITY, UNKNOWN_CONDITION, BLOCKED_GOAL }

class OnboardingException(val problem: OnboardingProblem) : IllegalArgumentException(problem.name)

/**
 * FS-1 onboarding over the data layer. Nothing is planned until [finish]: before that, [PfcData.planToday] returns null and the user's
 * conditions and screening fail closed (conservative) if read.
 */
class Onboarding(private val d: PfcData) {

    /** Where to resume; null once onboarding is finished. A brand-new install (no profile) starts at the welcome screen. */
    suspend fun step(): OnboardingStep? {
        val p = d.docs.get(Profile) ?: return OnboardingStep.WELCOME
        val s = p.onboardingStep ?: return null
        return OnboardingStep.entries.firstOrNull { it.name == s } ?: OnboardingStep.WELCOME
    }

    private suspend fun profile(): Profile = d.docs.get(Profile) ?: Profile(createdDay = d.clock.today(), onboardingStep = OnboardingStep.WELCOME.name)

    private suspend fun moveTo(step: OnboardingStep?, change: (Profile) -> Profile = { it }) {
        val p = change(profile())
        // A finished onboarding is never reopened by a late answer (settings edit those records directly).
        val keep = if (d.docs.get(Profile)?.let { it.onboardingStep == null } == true) null else step?.name
        d.docs.put(Profile, p.copy(onboardingStep = keep))
    }

    /** Goes back to an earlier step (answers already given stay and are shown again). */
    suspend fun back(to: OnboardingStep) = d.store.transaction {
        if (step() == null) return@transaction
        moveTo(to)
    }

    /** O1: the one-time "not a medical device" disclaimer was accepted. */
    suspend fun acceptWelcome() = d.store.transaction { moveTo(OnboardingStep.SCREENING) }

    /** O2 SAF-001: the eight answers; the outcome (standard, moderate-only or conservative, clearance advice) is shown next. */
    suspend fun screening(a: ScreeningAnswers): ScreeningResult = d.store.transaction {
        val today = d.clock.today()
        d.docs.put(ScreeningRecord, ScreeningRecord(a, today))
        moveTo(OnboardingStep.ABOUT_YOU)
        Screening.evaluate(a).value
    }

    /**
     * O3: birth year (adults only — the rules come from adult evidence), experience (EXP-001), and optionally the waist reference sex
     * (FL-005 only; the training algorithm never reads it, IND-001) and bodyweight (IND-001 calibration start, FL-005 trend).
     */
    suspend fun aboutYou(birthYear: Int, experience: ExperienceAnswers, referenceSex: ReferenceSex? = null, bodyweightKg: Double? = null): EngineResult<Level> =
        d.store.transaction {
            val today = d.clock.today()
            val year = Days.year(today)
            if (birthYear < year - 100 || birthYear > year) throw OnboardingException(OnboardingProblem.BIRTH_YEAR_IMPLAUSIBLE)
            if (year - birthYear < ADULT_AGE) throw OnboardingException(OnboardingProblem.UNDER_18)
            require(bodyweightKg == null || bodyweightKg in 25.0..400.0) { "bodyweight out of range" }
            require(experience.monthsConsistent in 0..1200) { "months out of range" }
            val level = Experience.classify(experience)
            moveTo(OnboardingStep.CONDITIONS) { it.copy(birthYear = birthYear, experience = experience, level = level.value, referenceSex = referenceSex) }
            if (bodyweightKg != null) d.docs.put(WeightRecord, WeightRecord(today, bodyweightKg))
            d.decisions.append(level.decisions)
            level
        }

    /** CR-001 / SAF-010: the picked conditions with their answers ("none of these" = an empty list). Unknown IDs are refused. */
    suspend fun conditions(items: List<StoredCondition>) = d.store.transaction {
        if (items.any { Conditions[it.id] == null }) throw OnboardingException(OnboardingProblem.UNKNOWN_CONDITION)
        d.docs.put(ConditionsRecord, ConditionsRecord(items, d.clock.today()))
        moveTo(OnboardingStep.GOAL)
    }

    /** FL-001: goals offered and the default for this person (fat loss for 30+, not offered in pregnancy or active cancer treatment). */
    suspend fun goalOptions(weightFeaturesOff: Boolean = false): EngineResult<GoalOptions> = d.store.transaction {
        val today = d.clock.today()
        val age = d.docs.get(Profile)?.age(today)
        val items = d.docs.get(ConditionsRecord)?.items ?: emptyList()
        val postpartum = items.firstOrNull { it.id == "postpartum" }?.birthDay?.let { maxOf(0, today - it) / 7 }
        FatLoss.options(GoalContext(age, pregnant = items.any { it.id == "pregnancy" },
            cancerActiveTreatment = items.any { it.id == "cancer" && "in_active_treatment" in it.subFlags }, postpartumWeeks = postpartum,
            weightFeaturesOff = weightFeaturesOff))
    }

    /** O6: the top goals in order (PER-006) and the FL-001 weight-features switch. A goal not offered (FL-001) is refused. */
    suspend fun goal(priorities: List<Goal>, weightFeaturesOff: Boolean = false, focus: Set<com.personalfitnesscoach.engine.model.Muscle> = emptySet()) =
        d.store.transaction {
            if (priorities.isEmpty()) throw OnboardingException(OnboardingProblem.NO_PRIORITY)
            val offered = goalOptions(weightFeaturesOff).value
            if (priorities.any { it !in offered.offered }) throw OnboardingException(OnboardingProblem.BLOCKED_GOAL)
            d.docs.update(SettingsRecord) { (it ?: SettingsRecord()).copy(weightFeaturesOff = weightFeaturesOff || !offered.weightFeatures) }
            moveTo(OnboardingStep.SCHEDULE) { it.copy(priorities = priorities.distinct().take(3), focusMuscles = focus) }
        }

    /** O4: days a week (FREQ), the weekdays that are possible and preferred, and the usual session length. */
    suspend fun schedule(daysPerWeek: Int, availableDays: Set<Int>, preferredDays: Set<Int>, sessionMinutes: Int) = d.store.transaction {
        if (daysPerWeek !in 1..6) throw OnboardingException(OnboardingProblem.NO_DAYS)
        if (availableDays.size < daysPerWeek) throw OnboardingException(OnboardingProblem.TOO_FEW_DAYS_AVAILABLE)
        require(sessionMinutes in 20..120) { "session length is 20–120 minutes" }
        moveTo(OnboardingStep.EQUIPMENT) { it.copy(daysPerWeek = daysPerWeek, availableDays = availableDays, preferredDays = preferredDays intersect availableDays,
            sessionMinutes = sessionMinutes) }
    }

    /** O5: the gym's equipment with its real increments (PROG-003), the home kit (EQ-003) and machines this person doesn't use (MOD-001). */
    suspend fun equipment(gym: Set<String>, inventory: Inventory, homeKit: Set<String> = emptySet(), excludedModalities: Set<Modality> = emptySet()) =
        d.store.transaction {
            require(gym.all { it in com.personalfitnesscoach.engine.library.GeneratedLibrary.equipment }) { "unknown equipment" }
            d.docs.put(EquipmentRecord, (d.docs.get(EquipmentRecord) ?: EquipmentRecord(emptySet())).copy(gym = gym, inventory = inventory, homeKit = homeKit))
            d.docs.update(PreferencesRecord) { (it ?: PreferencesRecord()).copy(excludedModalities = excludedModalities) }
            moveTo(OnboardingStep.LIMITATIONS)
        }

    /** O7 IND-001: joints with a past injury or that hurt with some movements (conservative for 4 weeks from today, then "sensitive"). */
    suspend fun limitations(joints: Set<Joint>) = d.store.transaction {
        val today = d.clock.today()
        moveTo(OnboardingStep.NUMBERS) { p -> p.copy(priorInjuries = joints.associateWith { j -> p.priorInjuries[j] ?: today }) }
    }

    /** CAL-002 (optional): starting numbers per exercise. Invalid entries are kept out by the engine when they are used. */
    suspend fun numbers(entries: List<KnownNumber>) = d.store.transaction {
        require(entries.all { Library[it.exerciseId] != null }) { "unknown exercise" }
        for (k in d.docs.all(KnownNumber)) if (entries.none { it.exerciseId == k.exerciseId }) d.docs.delete(KnownNumber, k.exerciseId)
        entries.forEach { d.docs.put(KnownNumber, it) }
        moveTo(OnboardingStep.PLAN)
    }

    /** O8: the 12-month plan for the chosen goals (block strip) — shown before starting. */
    suspend fun planPreview(): EngineResult<Program> = Blueprint.plan(d.docs.get(Profile)?.priorities?.ifEmpty { null } ?: listOf(Goal.GENERAL_FITNESS))

    /** O8 → O9: the plan was seen. */
    suspend fun planSeen() = d.store.transaction { moveTo(OnboardingStep.ALERTS) }

    /**
     * O9 done: onboarding ends and the programme starts today (the calibration weeks first, CAL-001). Missing optional records get their
     * defaults so nothing fails closed by accident; screening and conditions must have been answered.
     */
    suspend fun finish(): ProgramRecord = d.store.transaction {
        check(d.docs.get(ScreeningRecord) != null) { "screening not answered" }
        check(d.docs.get(ConditionsRecord) != null) { "conditions not answered" }
        if (d.docs.get(PreferencesRecord) == null) d.docs.put(PreferencesRecord, PreferencesRecord())
        if (d.docs.get(EquipmentRecord) == null) d.docs.put(EquipmentRecord, EquipmentRecord(emptySet()))
        if (d.docs.get(SettingsRecord) == null) d.docs.put(SettingsRecord, SettingsRecord())
        val p = profile()
        d.docs.put(Profile, p.copy(onboardingStep = null, priorities = p.priorities.ifEmpty { listOf(Goal.GENERAL_FITNESS) },
            daysPerWeek = p.daysPerWeek ?: 3))
        d.startProgramIfNeeded()
    }

    companion object {
        const val ADULT_AGE = 18
    }
}
