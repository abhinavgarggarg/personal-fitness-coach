package com.personalfitnesscoach.app.flow

import com.personalfitnesscoach.data.core.GeneratedSession
import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.TodayPlan
import com.personalfitnesscoach.data.core.backup.BackupException
import com.personalfitnesscoach.data.core.backup.BackupFormat
import com.personalfitnesscoach.data.core.backup.BackupProblem
import com.personalfitnesscoach.data.core.backup.BackupSink
import com.personalfitnesscoach.data.core.backup.RestorePreview
import com.personalfitnesscoach.data.core.model.ConditionsRecord
import com.personalfitnesscoach.data.core.model.EquipmentRecord
import com.personalfitnesscoach.data.core.model.KnownNumber
import com.personalfitnesscoach.data.core.model.PreferencesRecord
import com.personalfitnesscoach.data.core.model.Profile
import com.personalfitnesscoach.data.core.model.ReadinessRecord
import com.personalfitnesscoach.data.core.model.SafetyStopRecord
import com.personalfitnesscoach.data.core.model.ScreeningRecord
import com.personalfitnesscoach.data.core.model.SettingsRecord
import com.personalfitnesscoach.data.core.model.StepStateRecord
import com.personalfitnesscoach.data.core.model.StepsRecord
import com.personalfitnesscoach.data.core.model.StoredCondition
import com.personalfitnesscoach.data.core.model.WeekSummary
import com.personalfitnesscoach.data.core.model.WeightRecord
import com.personalfitnesscoach.data.core.model.dayKey
import com.personalfitnesscoach.data.core.onboarding.Onboarding
import com.personalfitnesscoach.data.core.onboarding.OnboardingException
import com.personalfitnesscoach.data.core.onboarding.OnboardingStep
import com.personalfitnesscoach.data.core.player.LiftEntry
import com.personalfitnesscoach.data.core.player.SessionPlayer
import com.personalfitnesscoach.data.core.player.Step
import com.personalfitnesscoach.data.core.session.SetKind
import com.personalfitnesscoach.data.core.session.Sheet
import com.personalfitnesscoach.data.core.session.Stage
import com.personalfitnesscoach.data.core.session.Status
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.engine.calc.CheckIn
import com.personalfitnesscoach.engine.calc.Readiness
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.library.GeneratedLibrary
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.EquipmentClass
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.program.ExperienceAnswers
import com.personalfitnesscoach.engine.program.Streak
import com.personalfitnesscoach.engine.program.WeekRecord
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.Registry
import com.personalfitnesscoach.engine.safety.Conditions
import com.personalfitnesscoach.engine.safety.ControlStatus
import com.personalfitnesscoach.engine.safety.GeneratedConditions
import com.personalfitnesscoach.engine.safety.PainReport
import com.personalfitnesscoach.engine.safety.ScreeningAnswers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A step-counter reading (D-078), as the platform reports it. */
data class StepReadingData(val atMs: Long, val elapsedMs: Long, val counter: Long, val bootCount: Int?)

/** What only the phone can do (faked in tests). */
interface Platform {
    val stepCounterAvailable: Boolean
    /** Null without a sensor or without the "physical activity" permission. */
    suspend fun readSteps(): StepReadingData?
    /** After an erase or when the workout ends: no alarm or notification is left behind. */
    fun cancelAlerts()
    /** False when the phone blocks this app's notifications (the rest timer can't buzz while locked). */
    val alertsAllowed: Boolean get() = true
}

/**
 * Phase 2 "Orchestrator": the screens' only door to the data layer and the engine. Every tap is one call; each call runs alone
 * (a second tap waits, so a double tap can't log a set twice) and publishes the next [Screen]. No Android types, so the whole flow is
 * tested on the JVM.
 */
class AppController(private val open: () -> PfcData, private val platform: Platform, val appVersion: String) {
    private val _screen = MutableStateFlow<Screen>(Screen.Loading)
    val screen: StateFlow<Screen> = _screen
    private val _busy = MutableStateFlow(false)
    /** True while a tap is being handled: the screens disable their buttons, so a double tap never logs a set twice (NFR-03). */
    val busy: StateFlow<Boolean> = _busy
    private val _error = MutableStateFlow<String?>(null)
    /** An action that failed unexpectedly: shown as a short message; the screen stays where it was and nothing half-done is kept. */
    val error: StateFlow<String?> = _error
    private val lock = Mutex()
    private var d: PfcData? = null
    private lateinit var player: SessionPlayer
    private lateinit var onboarding: Onboarding
    private var form = OnboardingForm()
    private var screeningResult: com.personalfitnesscoach.engine.safety.ScreeningResult? = null
    private var restorePreview: RestorePreview? = null
    private var restoreToken = 0

    private val data: PfcData get() = d ?: open().also { d = it; player = SessionPlayer(it); onboarding = Onboarding(it) }

    /** Runs one tap. `showBusy` is false for typing and ticking answers (no flicker); buttons disable while a real action runs. */
    private suspend fun act(showBusy: Boolean = true, block: suspend () -> Screen?) = lock.withLock {
        if (showBusy) _busy.value = true
        try {
            val next = try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                // Every change runs in one transaction, so a failure leaves nothing half-written. Before the data layer opens it is ERROR_SAFE.
                if (d == null) Screen.Failed(e.javaClass.simpleName + ": " + (e.message ?: ""))
                else { _error.value = e.javaClass.simpleName; null }
            }
            if (next != null) _screen.value = next
        } finally {
            _busy.value = false
        }
    }

    fun clearError() { _error.value = null }

    private fun current(): Screen = _screen.value

    // ======================================================================================================== launch
    /** At launch: onboarding, the resume prompt for an unfinished workout (R1), or Today. */
    suspend fun start() = act {
        val dd = data
        dd.onAppOpen()
        syncSteps()
        home()
    }

    /** The app came back to the foreground: finished weeks roll over, steps are read (D-078), Today refreshes. */
    suspend fun onForeground() = act {
        val dd = d ?: return@act null
        dd.onAppOpen()
        syncSteps()
        if (current() is Screen.Today) today() else null
    }

    private suspend fun syncSteps() {
        val dd = data
        if (dd.docs.get(SettingsRecord)?.stepTracking != true) return
        platform.readSteps()?.let { dd.recordStepReading(it.atMs, it.elapsedMs, it.counter, it.bootCount) }
    }

    private suspend fun home(): Screen {
        val step = onboarding.step()
        if (step != null) {
            form = loadForm()
            return onboardingScreen(step)
        }
        player.view()?.let { return Screen.Resume(it) }
        return today()
    }

    // ======================================================================================================== onboarding
    private suspend fun onboardingScreen(step: OnboardingStep, problem: com.personalfitnesscoach.data.core.onboarding.OnboardingProblem? = null): Screen {
        val goals = if (step == OnboardingStep.GOAL) onboarding.goalOptions(form.weightFeaturesOff).value else null
        if (step == OnboardingStep.GOAL && form.goals.isEmpty() && goals != null) form = form.copy(goals = listOf(goals.default))
        val plan = if (step == OnboardingStep.PLAN) onboarding.planPreview().value else null
        return Screen.Onboarding(step, form, problem, screeningResult, goals, plan)
    }

    /** Answers given earlier (shown again when going back, or after the app was closed mid-onboarding). */
    private suspend fun loadForm(): OnboardingForm {
        val dd = data
        val p = dd.docs.get(Profile)
        val s = dd.docs.get(ScreeningRecord)
        val c = dd.docs.get(ConditionsRecord)
        val e = dd.docs.get(EquipmentRecord)
        val pref = dd.docs.get(PreferencesRecord)
        val today = dd.clock.today()
        var f = OnboardingForm()
        s?.let { r -> f = f.copy(screening = ScreeningQuestion.entries.associateWith { q -> answer(r.answers, q) }) }
        p?.let { f = f.copy(birthYear = it.birthYear, months = it.experience?.let { x -> ExperienceBand.entries.lastOrNull { b -> x.monthsConsistent >= b.months } },
            comfortable = it.experience?.comfortableWith ?: emptySet(), structured = it.experience?.structuredProgramming ?: false,
            confidentEffort = it.experience?.confidentEffortRatings ?: false, referenceSex = it.referenceSex, goals = it.priorities,
            daysPerWeek = it.daysPerWeek ?: 3, availableDays = it.availableDays, preferredDays = it.preferredDays.ifEmpty { setOf(0, 2, 4) },
            sessionMinutes = it.sessionMinutes, joints = it.priorInjuries.keys) }
        dd.docs.all(WeightRecord).maxByOrNull { it.day }?.let { f = f.copy(bodyweightKg = it.kg) }
        c?.let { r -> f = f.copy(noneOfThese = r.items.isEmpty(), conditions = r.items.associate { sc -> formKey(sc.id) to ConditionAnswers(
            status = if (sc.id.startsWith("hbp_")) (if (sc.id == "hbp_controlled") ControlStatus.YES else ControlStatus.NO) else sc.controlled,
            clearance = sc.clearance, subFlags = sc.subFlags, pregnancyWeek = sc.pregnancyWeek?.let { w -> w + maxOf(0, today - (sc.pregnancyWeekDay ?: sc.addedDay)) / 7 },
            weeksSinceBirth = sc.birthDay?.let { b -> maxOf(0, today - b) / 7 }, attested = sc.attested, blockIfYes = sc.blockIfYes,
            previouslyVigorous = sc.previouslyVigorous, alreadyDoingImpact = sc.alreadyDoingImpact, supineUncomfortable = sc.supineUncomfortable,
            impactChecksPassed = sc.impactChecksPassed, flare = sc.flare, impactOptIn = sc.impactOptIn) }) }
        e?.let { f = f.copy(gym = it.gym, increments = Increments(barKg = it.inventory.barKg, smallestPlateKg = it.inventory.plates.keys.minOrNull() ?: 1.25,
            dumbbellMinKg = it.inventory.dumbbells.minOrNull() ?: 2.5, dumbbellMaxKg = it.inventory.dumbbells.maxOrNull() ?: 50.0,
            dumbbellStepKg = it.inventory.dumbbells.sorted().zipWithNext { a, b -> b - a }.minOrNull() ?: 2.5, stackStepKg = it.inventory.stack.stepKg,
            stackMaxKg = it.inventory.stack.maxKg)) }
        pref?.let { f = f.copy(excludedModalities = it.excludedModalities) }
        dd.docs.get(SettingsRecord)?.let { f = f.copy(weightFeaturesOff = it.weightFeaturesOff) }
        val known = dd.docs.all(KnownNumber)
        if (known.isNotEmpty()) f = f.copy(numbers = known.associate { k -> k.exerciseId to NumberEntry(k.loadKg, k.reps, k.rir, k.bestLift, maxOf(0, today - k.lastDoneDay)) })
        return f
    }

    private fun answer(a: ScreeningAnswers, q: ScreeningQuestion): Boolean = when (q) {
        ScreeningQuestion.HEART_OR_BLOOD_PRESSURE -> a.heartOrBloodPressure
        ScreeningQuestion.METABOLIC_RENAL_PULMONARY -> a.metabolicRenalPulmonary
        ScreeningQuestion.SYMPTOMS -> a.symptoms
        ScreeningQuestion.PALPITATIONS -> a.palpitations
        ScreeningQuestion.LIMIT_OR_PREGNANCY -> a.limitOrPregnancy
        ScreeningQuestion.MUSCULOSKELETAL -> a.musculoskeletal
        ScreeningQuestion.LONG_TERM_MEDICATION -> a.longTermMedication
        ScreeningQuestion.REGULARLY_ACTIVE -> a.regularlyActive
    }

    /** The picker shows one "high blood pressure" item; its status answer picks the table entry (SAF-010). */
    private fun formKey(id: String) = if (id.startsWith("hbp_")) HBP else id

    /** Changes the answers on the current step (no saving until the step is submitted). */
    suspend fun editOnboarding(change: (OnboardingForm) -> OnboardingForm) = act(showBusy = false) {
        val s = current() as? Screen.Onboarding ?: return@act null
        form = change(form)
        val goals = if (s.step == OnboardingStep.GOAL) onboarding.goalOptions(form.weightFeaturesOff).value else s.goals
        s.copy(form = form, problem = null, goals = goals)
    }

    /** Saves the current step's answers and moves on (FS-1); a refused answer stays on the step with its problem shown. */
    suspend fun submitOnboarding() = act {
        val s = current() as? Screen.Onboarding ?: return@act null
        val o = onboarding
        val f = form
        val dd = data
        try {
            when (s.step) {
                OnboardingStep.WELCOME -> o.acceptWelcome()
                OnboardingStep.SCREENING -> {
                    if (f.screening.size < ScreeningQuestion.entries.size) return@act s.copy(problem = null).also { }
                    val a = f.screening
                    screeningResult = o.screening(ScreeningAnswers(a.getValue(ScreeningQuestion.HEART_OR_BLOOD_PRESSURE), a.getValue(ScreeningQuestion.METABOLIC_RENAL_PULMONARY),
                        a.getValue(ScreeningQuestion.SYMPTOMS), a.getValue(ScreeningQuestion.PALPITATIONS), a.getValue(ScreeningQuestion.LIMIT_OR_PREGNANCY),
                        a.getValue(ScreeningQuestion.MUSCULOSKELETAL), a.getValue(ScreeningQuestion.LONG_TERM_MEDICATION), a.getValue(ScreeningQuestion.REGULARLY_ACTIVE)))
                }
                OnboardingStep.ABOUT_YOU -> {
                    val year = f.birthYear ?: return@act s
                    val band = f.months ?: return@act s
                    o.aboutYou(year, ExperienceAnswers(band.months, f.comfortable, f.structured, f.confidentEffort), f.referenceSex, f.bodyweightKg)
                }
                OnboardingStep.CONDITIONS -> {
                    if (!f.noneOfThese && f.conditions.isEmpty()) return@act s
                    o.conditions(if (f.noneOfThese) emptyList() else storedConditions(f.conditions, dd.clock.today()))
                }
                OnboardingStep.GOAL -> o.goal(f.goals, f.weightFeaturesOff)
                OnboardingStep.SCHEDULE -> o.schedule(f.daysPerWeek, f.availableDays, f.preferredDays, f.sessionMinutes)
                OnboardingStep.EQUIPMENT -> o.equipment(f.gym, f.increments.inventory(), excludedModalities = f.excludedModalities)
                OnboardingStep.LIMITATIONS -> o.limitations(f.joints)
                OnboardingStep.NUMBERS -> {
                    val today = dd.clock.today()
                    o.numbers(f.numbers.mapNotNull { (id, n) ->
                        val load = n.loadKg ?: return@mapNotNull null
                        val reps = n.reps ?: return@mapNotNull null
                        if (load <= 0 || load >= 1000 || reps !in 1..100) null
                        else KnownNumber(id, load, reps, if (n.bestLift) 0.0 else n.rir, n.bestLift, today - n.daysAgo.coerceIn(0, 3650), today)
                    })
                }
                OnboardingStep.PLAN -> o.planSeen()
                OnboardingStep.ALERTS -> {
                    o.finish()
                    dd.docs.update(SettingsRecord) { (it ?: SettingsRecord()).copy(alertsAsked = true) }
                    return@act today()
                }
            }
        } catch (e: OnboardingException) {
            return@act s.copy(problem = e.problem)
        }
        onboardingScreen(o.step() ?: return@act today())
    }

    suspend fun backOnboarding() = act {
        val s = current() as? Screen.Onboarding ?: return@act null
        val prev = OnboardingStep.entries.getOrNull(s.step.ordinal - 1) ?: return@act null
        onboarding.back(prev)
        onboardingScreen(prev)
    }

    private fun storedConditions(m: Map<String, ConditionAnswers>, today: Int): List<StoredCondition> = m.map { (key, a) ->
        val id = if (key == HBP) Conditions.hbpEntryId(a.status ?: ControlStatus.NOT_SURE) else key
        StoredCondition(id, today, controlled = if (key == HBP) null else a.status, clearance = a.clearance, clearanceDay = if (a.clearance.isEmpty()) null else today,
            subFlags = a.subFlags, pregnancyWeek = a.pregnancyWeek, pregnancyWeekDay = a.pregnancyWeek?.let { today },
            birthDay = a.weeksSinceBirth?.let { today - it * 7 }, attested = a.attested, impactChecksPassed = a.impactChecksPassed,
            blockIfYes = a.blockIfYes || a.attestNotOk, previouslyVigorous = a.previouslyVigorous, alreadyDoingImpact = a.alreadyDoingImpact,
            supineUncomfortable = a.supineUncomfortable, flare = a.flare, impactOptIn = a.impactOptIn)
    }.distinctBy { it.id }

    /** The equipment ticked by a preset (the list stays editable). */
    fun preset(p: GymPreset): Set<String> {
        val all = GeneratedLibrary.equipment.keys
        val cardio = GeneratedLibrary.equipment.filterValues { it.equipmentClass == EquipmentClass.CONDITIONING }.keys
        return when (p) {
            GymPreset.FULL_GYM -> all.toSet()
            GymPreset.DUMBBELLS_AND_MACHINES -> all.filter { GeneratedLibrary.equipment[it]!!.equipmentClass != EquipmentClass.BARBELL && it != "rack" }.toSet()
            GymPreset.HOME_DUMBBELLS -> setOf("dumbbells", "bench", "mat", "bands")
            GymPreset.BODYWEIGHT_ONLY -> setOf("mat")
        }.let { if (p == GymPreset.HOME_DUMBBELLS || p == GymPreset.BODYWEIGHT_ONLY) it else it + cardio }
    }

    /** CAL-002 candidates: tracked lifts that work with the ticked equipment, the common ones first. */
    fun numberCandidates(gym: Set<String>): List<String> = COMMON_LIFTS.filter { id -> Library[id]?.usableWith(gym) == true }

    // ======================================================================================================== today
    private suspend fun today(): Screen {
        val dd = data
        val t = dd.planToday() ?: return onboardingScreen(onboarding.step() ?: OnboardingStep.WELCOME)
        return Screen.Today(todayModel(t))
    }

    suspend fun refreshToday() = act { today() }

    private suspend fun todayModel(t: TodayPlan): TodayModel {
        val dd = data
        val today = t.user.today
        val ws = Days.weekStart(today)
        val week = dd.sessions.between(ws, ws + 6)
        val cells = (0..6).map { wd ->
            val day = ws + wd
            val planned = t.week.days.firstOrNull { it.weekday == wd }
            val mine = week.filter { it.doc.weekday == wd }
            val state = when {
                mine.any { it.row.status == Status.DONE } -> DayState.DONE
                mine.any { it.row.status == Status.SKIPPED } -> DayState.SKIPPED
                planned == null -> DayState.REST
                day < today -> DayState.MISSED
                else -> DayState.PLANNED
            }
            DayCell(wd, day, planned?.template, state, day == today)
        }
        val doneAll = dd.sessions.done(today - 400, today)
        val last = doneAll.lastOrNull()
        val missed = last?.let { dd.bridge.missedPlannedSince(it.day, today) } ?: 0
        val next = t.next
        val nextIsToday = next != null && next.weekday == Days.weekday(today)
        val settings = dd.docs.get(SettingsRecord) ?: SettingsRecord()
        val now = dd.clock.nowMs()
        val rate = doneAll.lastOrNull { w -> w.row.sessionRpe == null && !w.doc.stoppedBySafety && w.row.endedAtMs != null &&
            now - w.row.endedAtMs!! >= P.LOAD_002.min_minutes_after * 60_000L && now - w.row.endedAtMs!! <= P.LOAD_002.late_prompt_hours * 3_600_000L }?.id
        val streak = Streak.compute(dd.docs.all(WeekSummary).sortedBy { it.weekStartDay }.map { WeekRecord(it.planned, it.completed, Days.year(it.weekStartDay)) },
            Days.year(today))
        val steps = if (settings.stepTracking) dd.docs.get(StepsRecord, dayKey(today))?.steps ?: 0 else null
        val target = if (settings.stepTracking) dd.docs.get(StepStateRecord)?.target else null
        val reasons = dd.decisions.forDay(today).filter { it.reason in TODAY_REASONS }.distinctBy { it.reason }
        val painStop = dd.docs.between(com.personalfitnesscoach.data.core.model.PainRecord, today, today)
            .any { it.resolvedDay == null && com.personalfitnesscoach.engine.safety.PainGate.assess(it.report).value.endSession }
        val scr = dd.docs.get(ScreeningRecord)
        val reason = when {
            !t.user.flaggedScreen -> null
            t.user.conditions.conservative && t.user.screening == com.personalfitnesscoach.engine.safety.ScreeningMode.STANDARD -> ConservativeReason.CONDITION
            scr != null && com.personalfitnesscoach.engine.safety.Screening.evaluate(scr.answers, scr.clearanceConfirmedDay != null).value.clinicianGuidance -> ConservativeReason.CLINICIAN
            else -> ConservativeReason.SCREENING_DOCTOR
        }
        val rescreen = scr != null && com.personalfitnesscoach.engine.safety.Screening.rescreenDue(
            java.time.Period.between(Days.date(scr.takenDay), Days.date(today)).toTotalMonths().toInt())
        val flare = dd.docs.get(ConditionsRecord)?.items?.firstOrNull { it.id == "low_back_pain" }?.flare
        return TodayModel(t, next, nextIsToday, cells, dd.docs.get(ReadinessRecord, dayKey(today)),
            dd.docs.get(SafetyStopRecord)?.takeIf { it.confirmedDay == null }?.symptoms, missed >= 1 && (next != null),
            dd.backup.reminderDue(dd.completedWorkouts()), rate, streak, steps, target, t.user.flaggedScreen, t.user.conditions.prompts,
            week.any { it.day == today && it.row.status == Status.DONE }, reasons, painStop, reason, rescreen, flare, upcoming(t))
    }

    /** SAF-010 low back pain flare mode, switched from Today (R5-12). */
    suspend fun setBackFlare(on: Boolean) = act {
        val dd = data
        dd.docs.update(ConditionsRecord) { r ->
            val rec = checkNotNull(r) { "no conditions" }
            rec.copy(items = rec.items.map { if (it.id == "low_back_pain") it.copy(flare = on) else it })
        }
        today()
    }

    /** SAF-002: the user confirms the symptoms resolved or were reviewed by a doctor; the next session is LIGHT at most. */
    suspend fun confirmStopResolved() = act {
        data.confirmStopResolved()
        today()
    }

    suspend fun dismissBackupReminder() = act {
        val dd = data
        dd.backup.dismissReminder(dd.completedWorkouts())
        today()
    }

    /** LOAD-002 late prompt from Today. */
    suspend fun rateLater(workoutId: Long, rpe: Int) = act {
        data.sessions.rate(workoutId, rpe.coerceIn(0, 10).toDouble())
        today()
    }

    // ======================================================================================================== check-in and preview
    /** T2: opens the check-in for the next planned session (done today, whichever weekday it was planned for, SCH-002). */
    suspend fun openCheckIn() = act {
        val dd = data
        val t = dd.planToday() ?: return@act null
        val next = t.next ?: return@act null
        if (todayModel(t).painStop) return@act null
        val prev = dd.docs.get(ReadinessRecord, dayKey(t.user.today))
        Screen.CheckIn(CheckInForm(sleep = prev?.checkIn?.sleep ?: 3, energy = prev?.checkIn?.energy ?: 3, soreness = prev?.checkIn?.soreness ?: 3,
            stress = prev?.checkIn?.stress ?: 3, sleepHours = prev?.checkIn?.sleepHours, minutes = prev?.minutesAvailable ?: t.user.profile.sessionMinutes), next)
    }

    suspend fun editCheckIn(change: (CheckInForm) -> CheckInForm) = act(showBusy = false) {
        val s = current() as? Screen.CheckIn ?: return@act null
        s.copy(form = change(s.form))
    }

    /**
     * Saves the check-in: pain goes through the pain gate (SAF-003), missing equipment is off for today, then the readiness tier with every
     * stored limit (RDY, SAF-002, SAF-007, REG, DEL-002). A red flag stops here (A5); otherwise the session is generated for preview.
     */
    suspend fun submitCheckIn() = act {
        val s = current() as? Screen.CheckIn ?: return@act null
        val dd = data
        val f = s.form
        // SAF-003: the pain gate's outcome decides the day (R5-01): ending the session means no session today.
        val pain = f.pain?.let { p -> if (p.region != null && p.kind != null) dd.reportPain(PainReport(p.region, p.kind, p.rating, p.worsening, p.descriptors, p.wholeBody)) else null }
        dd.equipmentMissingToday(f.missingEquipment)
        val t = dd.planToday() ?: return@act today()
        val out = dd.checkIns.record(t.user, CheckIn(f.sleep, f.energy, f.soreness, f.stress, f.sleepHours), f.minutes, f.redFlags, f.illness)
        dd.decisions.append(out.decisions)
        out.safetyStop?.let { return@act Screen.Stop(it) }
        if (pain?.value?.endSession == true) return@act Screen.PainDay(pain.value)
        preview(s.next, out.record.tier, f.minutes, express = false, away = f.awayFromGym, extra = pain?.decisions ?: emptyList())
    }

    private suspend fun preview(day: com.personalfitnesscoach.engine.program.PlannedDay, tier: Tier, minutes: Int, express: Boolean, away: Boolean,
                                extra: List<com.personalfitnesscoach.engine.core.Decision> = emptyList()): Screen {
        val dd = data
        val t = dd.planToday() ?: return today()
        val planned = t.week.days.firstOrNull { it.weekday == day.weekday && it.template == day.template } ?: day
        val g = dd.generateSession(t, planned, tier, minutes, express = express, awayFromGym = away)
        g.workout.safetyStop?.let { return Screen.Stop(it) }
        val rec = dd.docs.get(ReadinessRecord, dayKey(t.user.today))
        val engineTier = rec?.engineTier ?: tier
        // RDY-007 with the same lock as the check-in, the day's safety, return and lighter-week caps included (R5-14).
        val gate = dd.bridge.gate(t.user, t.program)
        val locked = t.user.flaggedScreen || gate.locked || (rec?.illnessSymptoms?.isNotEmpty() == true) || dd.bridge.painToday(t.user.today).first.isNotEmpty()
        val options = Tier.entries.filter { it != g.workout.tier && Readiness.userChoice(engineTier, it, locked).value == it && gate.cap(it) == it }
        return Screen.Preview(PreviewModel(g, g.workout.tier, options, extra + g.decisions, minutes))
    }

    /** RDY-007: an easier tier always; one step harder with a warning when nothing locks it. The check-in is saved again with the choice. */
    suspend fun chooseTier(tier: Tier) = act {
        val s = current() as? Screen.Preview ?: return@act null
        val dd = data
        val t = dd.planToday() ?: return@act today()
        val rec = dd.docs.get(ReadinessRecord, dayKey(t.user.today)) ?: return@act null
        val out = dd.checkIns.record(t.user, rec.checkIn, rec.minutesAvailable, rec.redFlags, rec.illnessSymptoms, requestedTier = tier)
        dd.decisions.append(out.decisions)
        out.safetyStop?.let { return@act Screen.Stop(it) }
        preview(s.model.session.day, out.record.tier, s.model.minutes, s.model.session.express, s.model.session.awayFromGym)
    }

    /** "I only have N minutes" before starting (FS-7; under 20 → the express session, ADH-004). */
    suspend fun previewMinutes(minutes: Int) = act {
        val s = current() as? Screen.Preview ?: return@act null
        val m = minutes.coerceIn(10, 180)
        preview(s.model.session.day, s.model.tier, m, express = m < P.TIME_002.express_below_minutes, away = s.model.session.awayFromGym)
    }

    suspend fun previewExpress(on: Boolean) = act {
        val s = current() as? Screen.Preview ?: return@act null
        preview(s.model.session.day, s.model.tier, s.model.minutes, express = on, away = s.model.session.awayFromGym)
    }

    /** EQ-003: one tap — today's equipment is the saved home kit (bodyweight only by default). */
    suspend fun previewAway(on: Boolean) = act {
        val s = current() as? Screen.Preview ?: return@act null
        preview(s.model.session.day, s.model.tier, s.model.minutes, express = s.model.session.express, away = on)
    }

    /** Starts the previewed session (only the validated workout is saved, SAF-008). */
    suspend fun startWorkout() = act {
        val s = current() as? Screen.Preview ?: return@act null
        val g: GeneratedSession = s.model.session
        if (g.workout.items.isEmpty() && g.workout.conditioning.isEmpty()) return@act today()
        data.startSession(g)
        Screen.Workout(player.view()!!)
    }

    suspend fun closeToToday() = act { today() }

    // ======================================================================================================== resume (R1)
    suspend fun resumeWorkout() = act { player.view()?.let { Screen.Workout(it, sheetFor(it)) } ?: today() }

    /** The interrupted workout ends here: kept as done so far (PARTIAL), or discarded when nothing was logged. */
    suspend fun endInterrupted() = act {
        if (player.discard()) { platform.cancelAlerts(); return@act today() }
        val sum = player.finish(null, endedEarly = true)
        platform.cancelAlerts()
        done(sum)
    }

    /** A8 with the next planned session. */
    private suspend fun done(sum: com.personalfitnesscoach.data.core.player.Summary): Screen = Screen.Done(sum, next = data.planToday()?.let { upcoming(it) })

    /** The next session to show: today's offer, or the next planned day later this week (after today's session is done). */
    private fun upcoming(t: TodayPlan): com.personalfitnesscoach.engine.program.PlannedDay? =
        t.next ?: t.week.days.filter { it.weekday > Days.weekday(t.user.today) }.minByOrNull { it.weekday }

    private fun sheetFor(v: com.personalfitnesscoach.data.core.player.PlayerView): WorkoutSheet? = when (v.state.sheet) {
        Sheet.CHANGE_TIME -> WorkoutSheet.ChangeTime(Math.round(v.remainingMinutes).toInt())
        Sheet.RED_FLAG -> WorkoutSheet.RedFlag()
        Sheet.SOMETHING_HURTS -> WorkoutSheet.Hurts(v.state.currentRowId)
        else -> null
    }

    // ======================================================================================================== workout
    private suspend fun workout(showBusy: Boolean = true, update: suspend (com.personalfitnesscoach.data.core.player.PlayerView) -> Screen) = act(showBusy) {
        val s = current()
        val v = (s as? Screen.Workout)?.view ?: player.view() ?: return@act today()
        update(v)
    }

    private fun shown(v: com.personalfitnesscoach.data.core.player.PlayerView, notice: WorkoutNotice? = null, sheet: WorkoutSheet? = null): Screen =
        if (v.step is Step.Done && sheet == null) Screen.Workout(v, null, notice) else Screen.Workout(v, sheet, notice)

    suspend fun completeStage(stage: Stage) = workout { shown(player.completeStage(stage)) }
    suspend fun select(rowId: Long) = workout { shown(player.select(rowId)) }
    suspend fun skipRamp(rowId: Long) = workout { shown(player.skipRamp(rowId)) }

    /**
     * Logs a set (one tap for "done as planned"). FS-5: an unusual entry asks for a confirmation first. The next set's load may change
     * (INT-007, CAL-001) — the decision is shown as a one-line notice.
     */
    suspend fun log(rowId: Long, entry: LiftEntry, confirmed: Boolean = false, expected: com.personalfitnesscoach.data.core.player.SetTarget? = null) = workout { v ->
        val t = v.lifts.firstOrNull { it.rowId == rowId }?.next ?: return@workout shown(v)
        // A tap for a set that was already logged (a late double tap) changes nothing (R5-10).
        if (expected != null && (t.kind != expected.kind || t.number != expected.number)) return@workout shown(v)
        if (!confirmed && SessionPlayer.unusual(entry, t)) return@workout Screen.Workout(v, WorkoutSheet.ConfirmEntry(rowId, entry, expected))
        val r = player.log(rowId, entry, expected)
        if (r.ignored) return@workout shown(r.view)
        val change = r.decisions.firstOrNull { it.kind == DecisionKind.LOAD_CHANGE || it.kind == DecisionKind.CALIBRATION }
        shown(r.view, change?.let { WorkoutNotice.LoadChanged(it) })
    }

    suspend fun adjustRest(deltaSec: Int) = workout { shown(player.adjustRest(deltaSec)) }
    suspend fun skipRest() = workout { shown(player.skipRest()) }
    suspend fun startTimerRest(sec: Int) = workout { shown(player.startRest(sec)) }
    suspend fun pause() = workout { shown(player.pause()) }
    suspend fun resume() = workout { shown(player.resume()) }
    suspend fun logConditioning(index: Int, minutes: Double) = workout { shown(player.logConditioning(index, minutes)) }
    suspend fun skipExercise(rowId: Long) = workout { shown(player.skipExercise(rowId)) }
    suspend fun doLater(rowId: Long) = workout { shown(player.doLater(rowId)) }
    suspend fun openTimer(index: Int) = workout { v -> Screen.Workout(v, WorkoutSheet.Timer(index)) }

    suspend fun closeSheet() = workout { v ->
        val nv = if (v.state.sheet != null) player.openSheet(null) else v
        Screen.Workout(nv)
    }

    /** A3: Replace, or "occupied" with "do it later" (EQ-002). */
    suspend fun openReplace(rowId: Long, occupied: Boolean) = workout { v ->
        val nv = player.openSheet(Sheet.REPLACE)
        Screen.Workout(nv, WorkoutSheet.Replace(player.swapOptions(rowId, occupied)))
    }

    suspend fun swap(rowId: Long, exerciseId: String) = workout { v ->
        val r = player.swap(rowId, exerciseId)
        val nv = player.openSheet(null)
        shown(nv, if (r.refused) WorkoutNotice.Refused(r.decisions) else WorkoutNotice.Replanned(r.changes))
    }

    /** A4: "Something hurts" is always one tap away. */
    suspend fun openHurts(rowId: Long?) = workout { v -> Screen.Workout(player.openSheet(Sheet.SOMETHING_HURTS), WorkoutSheet.Hurts(rowId)) }

    suspend fun editHurts(change: (PainForm) -> PainForm) = workout(showBusy = false) { v ->
        val s = (current() as? Screen.Workout)?.sheet as? WorkoutSheet.Hurts ?: return@workout shown(v)
        Screen.Workout(v, s.copy(form = change(s.form)))
    }

    suspend fun submitHurts() = workout { v ->
        val s = (current() as? Screen.Workout)?.sheet as? WorkoutSheet.Hurts ?: return@workout shown(v)
        val f = s.form
        val region = f.region ?: return@workout Screen.Workout(v, s)
        val kind = f.kind ?: return@workout Screen.Workout(v, s)
        val r = player.reportPain(s.rowId, PainReport(region, kind, f.rating, f.worsening, f.descriptors, f.wholeBody))
        if (r.endSession) { platform.cancelAlerts(); return@workout done(player.lastSummary!!) }
        val nv = player.openSheet(null)
        val alt = if (r.alternatives.isNotEmpty() && s.rowId != null) com.personalfitnesscoach.data.core.player.SwapChoice(s.rowId, Library.require(
            v.workout.exercises.first { it.row.id == s.rowId }.exerciseId), r.alternatives, false, emptyList()) else null
        Screen.Workout(nv, WorkoutSheet.HurtsResult(r.outcome, r.changes, alt, region))
    }

    /** After a pain stop: a pain-free alternative instead (SAF-003 substitute with joint stress ≤ 1). */
    suspend fun takeAlternative(stoppedRowId: Long, exerciseId: String, region: Joint) = workout { v ->
        val r = player.addAlternative(stoppedRowId, exerciseId, region, P.SAF_003.substitute_max_joint_stress)
        shown(r.view, if (r.refused) WorkoutNotice.Refused(r.decisions) else WorkoutNotice.Replanned(r.changes))
    }

    suspend fun openChangeTime() = workout { v -> Screen.Workout(player.openSheet(Sheet.CHANGE_TIME),
        WorkoutSheet.ChangeTime(maxOf(5, Math.round(v.remainingMinutes).toInt()))) }

    suspend fun changeTime(minutes: Int) = workout { v ->
        val r = player.changeTime(minutes.coerceIn(1, 600))
        shown(r.view, WorkoutNotice.Replanned(r.changes))
    }

    suspend fun openRedFlag() = workout { v -> Screen.Workout(player.openSheet(Sheet.RED_FLAG), WorkoutSheet.RedFlag()) }

    /** SAF-002: any red flag ends the workout at once (A5). */
    suspend fun submitRedFlag(symptoms: Set<String>) = act {
        if (symptoms.isEmpty()) return@act null
        val stop = player.redFlag(symptoms) ?: return@act null
        platform.cancelAlerts()
        Screen.Stop(stop)
    }

    /** SAF-006: one more set; past a cap the user confirms, at the ceiling or on a painful region it is refused. */
    suspend fun addSet(rowId: Long, confirmed: Boolean = false) = workout { v ->
        val r = player.addSet(rowId, confirmed)
        when (r.verdict) {
            com.personalfitnesscoach.engine.safety.AdditionVerdict.WARN_CONFIRM -> Screen.Workout(r.view, WorkoutSheet.ConfirmAddSet(rowId))
            else -> shown(r.view, WorkoutNotice.AddSet(r.verdict))
        }
    }

    suspend fun openEnd() = workout { v -> Screen.Workout(v, WorkoutSheet.End) }

    /** Ends the workout: finished, or early (PARTIAL); the summary follows (A8). */
    suspend fun finishWorkout(early: Boolean = false) = act {
        val sum = player.finish(null, endedEarly = early)
        platform.cancelAlerts()
        done(sum)
    }

    /** Nothing logged yet: the workout is dropped without a trace. */
    suspend fun discardWorkout() = act {
        if (!player.discard()) return@act null
        platform.cancelAlerts()
        today()
    }

    /** A8 session rating (CR10 0–10, LOAD-001). */
    suspend fun rateSummary(rpe: Int) = act {
        val s = current() as? Screen.Done ?: return@act null
        data.sessions.rate(s.summary.workout.id, rpe.coerceIn(0, 10).toDouble())
        s.copy(rated = true)
    }

    // ======================================================================================================== settings
    private suspend fun settingsModel(flow: SettingsFlow? = null): SettingsModel {
        val dd = data
        val s = dd.docs.get(SettingsRecord) ?: SettingsRecord()
        val scr = dd.docs.get(ScreeningRecord)
        val needs = scr != null && scr.clearanceConfirmedDay == null &&
            com.personalfitnesscoach.engine.safety.Screening.evaluate(scr.answers).value.clearanceRecommended
        val clearances = dd.docs.get(ConditionsRecord)?.items.orEmpty().mapNotNull { c ->
            val e = Conditions[c.id] ?: return@mapNotNull null
            val rule = if (e.clearanceAlwaysIf.any { it in c.subFlags }) "always" else e.clearance
            if (rule == "none") null else ConditionClearance(c.id, e.name, rule, c.clearance)
        }
        val guidance = scr != null && com.personalfitnesscoach.engine.safety.Screening.evaluate(scr.answers, scr.clearanceConfirmedDay != null).value.clinicianGuidance
        return SettingsModel(s.stepTracking, platform.stepCounterAvailable, s.units, s.lastExportDay, flow, appVersion, Registry.VERSION, Library.VERSION,
            GeneratedConditions.VERSION, needs, scr?.clearanceConfirmedDay, clearances, guidance, !platform.alertsAllowed)
    }

    /** SAF-001 re-screen from Settings (12-monthly, or when health changes; the only way out of question 5's guidance). */
    suspend fun openRescreen() = act { Screen.Settings(settingsModel(SettingsFlow.Rescreen())) }

    suspend fun answerRescreen(q: ScreeningQuestion, yes: Boolean) = act(showBusy = false) {
        val s = current() as? Screen.Settings ?: return@act null
        val f = s.model.flow as? SettingsFlow.Rescreen ?: return@act null
        s.copy(model = s.model.copy(flow = f.copy(answers = f.answers + (q to yes), result = null)))
    }

    /** Saves the new answers (the doctor's-OK confirmation starts again from them) and shows the outcome. */
    suspend fun submitRescreen() = act {
        val s = current() as? Screen.Settings ?: return@act null
        val f = s.model.flow as? SettingsFlow.Rescreen ?: return@act null
        if (f.answers.size < ScreeningQuestion.entries.size) return@act null
        val a = f.answers
        val r = onboarding.screening(ScreeningAnswers(a.getValue(ScreeningQuestion.HEART_OR_BLOOD_PRESSURE), a.getValue(ScreeningQuestion.METABOLIC_RENAL_PULMONARY),
            a.getValue(ScreeningQuestion.SYMPTOMS), a.getValue(ScreeningQuestion.PALPITATIONS), a.getValue(ScreeningQuestion.LIMIT_OR_PREGNANCY),
            a.getValue(ScreeningQuestion.MUSCULOSKELETAL), a.getValue(ScreeningQuestion.LONG_TERM_MEDICATION), a.getValue(ScreeningQuestion.REGULARLY_ACTIVE)))
        Screen.Settings(settingsModel(SettingsFlow.Rescreen(a, r)))
    }

    /** Settings → health conditions: the onboarding questions with the stored answers (R5-12). */
    suspend fun openConditionsEditor() = act {
        form = loadForm()
        Screen.EditConditions(form)
    }

    suspend fun editConditions(change: (OnboardingForm) -> OnboardingForm) = act(showBusy = false) {
        val s = current() as? Screen.EditConditions ?: return@act null
        form = change(form)
        s.copy(form = form, problem = null)
    }

    /**
     * Saves changed condition answers. A condition kept from before keeps its start day and pain-rule weeks (its time-based unlocks), and
     * its doctor's-OK date unless the scopes changed; a new one starts today.
     */
    suspend fun saveConditions() = act {
        val s = current() as? Screen.EditConditions ?: return@act null
        val dd = data
        val f = form
        if (!f.noneOfThese && f.conditions.isEmpty()) return@act s.copy(problem = com.personalfitnesscoach.data.core.onboarding.OnboardingProblem.UNKNOWN_CONDITION)
        val today = dd.clock.today()
        val old = dd.docs.get(ConditionsRecord)?.items.orEmpty().associateBy { it.id }
        val fresh = if (f.noneOfThese) emptyList() else storedConditions(f.conditions, today)
        val merged = fresh.map { n ->
            val o = old[n.id] ?: return@map n
            // Unchanged dates are kept exactly (the form shows whole weeks, so re-saving never shifts a week or a birth date).
            val oWeek = o.pregnancyWeek
            val sameWeek = oWeek != null && n.pregnancyWeek == oWeek + maxOf(0, today - (o.pregnancyWeekDay ?: o.addedDay)) / 7
            val oBirth = o.birthDay
            val nBirth = n.birthDay
            val sameBirth = oBirth != null && nBirth != null && (today - oBirth) / 7 == (today - nBirth) / 7
            n.copy(addedDay = o.addedDay, painRuleMetWeeks = o.painRuleMetWeeks,
                clearanceDay = if (n.clearance == o.clearance) o.clearanceDay else n.clearanceDay,
                pregnancyWeek = if (sameWeek) oWeek else n.pregnancyWeek,
                pregnancyWeekDay = if (sameWeek) o.pregnancyWeekDay else n.pregnancyWeekDay,
                birthDay = if (sameBirth) oBirth else nBirth)
        }
        try { onboarding.conditions(merged) } catch (e: OnboardingException) { return@act s.copy(problem = e.problem) }
        Screen.Settings(settingsModel())
    }

    /** SAF-001: the user confirms a doctor said they can exercise; conservative or moderate-only mode from the screening ends. */
    suspend fun confirmScreeningClearance() = act {
        val dd = data
        val today = dd.clock.today()
        dd.docs.update(ScreeningRecord) { r -> checkNotNull(r) { "no screening" }.copy(clearanceConfirmedDay = today) }
        Screen.Settings(settingsModel())
    }

    /** SAF-010: the doctor's-OK scopes confirmed for one condition (each unlocks only itself); recorded with today's date. */
    suspend fun setConditionClearance(id: String, scopes: Set<com.personalfitnesscoach.engine.safety.ClearanceScope>) = act {
        val dd = data
        val today = dd.clock.today()
        dd.docs.update(ConditionsRecord) { r ->
            val rec = checkNotNull(r) { "no conditions" }
            rec.copy(items = rec.items.map { c -> if (c.id != id) c else c.copy(clearance = scopes, clearanceDay = if (scopes.isEmpty()) null else today) })
        }
        Screen.Settings(settingsModel())
    }

    suspend fun openSettings() = act { Screen.Settings(settingsModel()) }

    /** D-062 / D-078: the screen asks for the permission first; turning it off forgets the counter reading. */
    suspend fun setStepTracking(on: Boolean) = act {
        data.setStepTracking(on)
        if (on) syncSteps()
        Screen.Settings(settingsModel())
    }

    suspend fun settingsFlow(flow: SettingsFlow?) = act { Screen.Settings(settingsModel(flow)) }

    fun exportFileName(): String = BackupFormat.fileName(data.clock.today())

    /** The backup file's bytes (optional password, D-075). The screen writes them where the user chose, then calls [exported]. */
    suspend fun exportBytes(password: CharArray?): ByteArray = lock.withLock { data.backup.export(password?.takeIf { it.isNotEmpty() }).second }

    suspend fun exported() = act {
        val dd = data
        dd.backup.exported(dd.completedWorkouts())
        Screen.Settings(settingsModel(SettingsFlow.Exported))
    }

    /** Checks a picked backup file before anything is changed (Phase 2 section 12). */
    suspend fun inspectBackup(bytes: ByteArray, password: CharArray? = null) = act {
        try {
            val p = data.backup.inspect(bytes, password?.takeIf { it.isNotEmpty() })
            restorePreview = p
            Screen.Settings(settingsModel(SettingsFlow.RestorePreview(p.summary, ++restoreToken)))
        } catch (e: BackupException) {
            when (e.problem) {
                BackupProblem.PASSWORD_NEEDED -> Screen.Settings(settingsModel(SettingsFlow.RestoreNeedsPassword(bytes)))
                BackupProblem.WRONG_PASSWORD_OR_DAMAGED -> if (password != null) Screen.Settings(settingsModel(SettingsFlow.RestoreNeedsPassword(bytes, wrong = true)))
                    else Screen.Settings(settingsModel(SettingsFlow.RestoreRefused(e.problem)))
                else -> Screen.Settings(settingsModel(SettingsFlow.RestoreRefused(e.problem)))
            }
        }
    }

    /** Restores the checked file: the current data is saved first through `safetyCopy` (optionally with a password), then replaced. */
    suspend fun restore(token: Int, safetyPassword: CharArray?, safetyCopy: BackupSink) = act {
        val p = restorePreview?.takeIf { token == restoreToken } ?: return@act null
        platform.cancelAlerts()
        data.restore(p, safetyPassword?.takeIf { it.isNotEmpty() }, safetyCopy)
        restorePreview = null
        Screen.Settings(settingsModel(SettingsFlow.Restored))
    }

    /** "Erase all my data", after the second confirmation: everything goes and the app is back at the welcome screen. */
    suspend fun eraseAll() = act {
        platform.cancelAlerts()
        data.eraseAll()
        form = OnboardingForm()
        screeningResult = null
        onboardingScreen(OnboardingStep.WELCOME)
    }

    suspend fun closeSettings() = act { home() }

    companion object {
        const val HBP = "hbp"
        /** Decisions that explain today's tier and limits (the one-line "why" on Today); routine ones (validator passed …) are left out. */
        val TODAY_REASONS: Set<String> = setOf(
            "TIER_CAPPED_BY_SLEEP", "TIER_CAPPED_BY_ITEM", "TIER_FLOOR_RAW_LOW", "TIER_STEPPED_DOWN_FATIGUE", "TIER_CHOSEN_BY_USER", "TIER_CHOICE_REFUSED",
            "SAFETY_STOP_RED_FLAG", "ILLNESS_REST", "ILLNESS_RETURN", "ILLNESS_MILD_CHOICE", "ILLNESS_REST_DAY", "RETURN_MODIFIED", "RETURN_RAMP", "RECALIBRATE",
            "MISSED_SHIFTED", "DELOAD_NOW", "LIGHTER_WEEK", "DELOAD_BLOCK_END", "DELOAD_RESUME", "PIVOT_WEEK", "REGION_CONSERVATIVE", "PAIN_CONTINUE_CAUTION",
            "PAIN_STOP_EXERCISE", "PAIN_STOP_REGION", "PAIN_SEE_PROFESSIONAL", "SCREEN_CONSERVATIVE", "SCREEN_MODERATE_ONLY", "CONDITION_CLEARANCE_NEEDED",
            "CONDITION_BLOCKED", "STRENGTH_DAYS_SPACED", "WORKLOAD_SPIKE_FLAG", "MONOTONY_FLAG", "ACCESSORIES_CUT_FATIGUE")
        /** Common tracked lifts offered for CAL-002 own numbers. */
        val COMMON_LIFTS = listOf("back-squat", "deadlift", "bench-press", "overhead-press", "barbell-row", "trap-bar-deadlift", "goblet-squat",
            "db-bench-press", "db-shoulder-press", "db-one-arm-row", "leg-press", "lat-pulldown", "seated-cable-row", "romanian-deadlift")
    }
}
