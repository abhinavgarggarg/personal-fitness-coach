package com.personalfitnesscoach.app.flow

import com.personalfitnesscoach.data.core.GeneratedSession
import com.personalfitnesscoach.data.core.TodayPlan
import com.personalfitnesscoach.data.core.backup.BackupProblem
import com.personalfitnesscoach.data.core.backup.BackupSummary
import com.personalfitnesscoach.data.core.model.DecisionEntry
import com.personalfitnesscoach.data.core.model.ReadinessRecord
import com.personalfitnesscoach.data.core.model.Units
import com.personalfitnesscoach.data.core.onboarding.OnboardingProblem
import com.personalfitnesscoach.data.core.onboarding.OnboardingStep
import com.personalfitnesscoach.data.core.player.Change
import com.personalfitnesscoach.data.core.player.LiftEntry
import com.personalfitnesscoach.data.core.player.PlayerView
import com.personalfitnesscoach.data.core.player.Summary
import com.personalfitnesscoach.data.core.player.SwapChoice
import com.personalfitnesscoach.engine.calc.Inventory
import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.program.DayTemplate
import com.personalfitnesscoach.engine.program.Goal
import com.personalfitnesscoach.engine.program.GoalOptions
import com.personalfitnesscoach.engine.program.PlannedDay
import com.personalfitnesscoach.engine.program.Program
import com.personalfitnesscoach.engine.program.StreakState
import com.personalfitnesscoach.engine.progress.ReferenceSex
import com.personalfitnesscoach.engine.safety.AdditionVerdict
import com.personalfitnesscoach.engine.safety.ClearanceScope
import com.personalfitnesscoach.engine.safety.ControlStatus
import com.personalfitnesscoach.engine.safety.PainKind
import com.personalfitnesscoach.engine.safety.PainOutcome
import com.personalfitnesscoach.engine.safety.SafetyStop
import com.personalfitnesscoach.engine.safety.ScreeningResult

/**
 * Everything the screens show, as plain data (no Android, no text: the screens turn keys into string resources, NFR-16). One
 * [Screen] at a time; the Android layer renders it and calls [AppController] for every tap.
 */
sealed interface Screen {
    data object Loading : Screen

    /** Phase 2 ERROR_SAFE: the database could not open; nothing is written, a retry is offered. */
    data class Failed(val detail: String) : Screen

    data class Onboarding(val step: OnboardingStep, val form: OnboardingForm, val problem: OnboardingProblem? = null,
                          val screening: ScreeningResult? = null, val goals: GoalOptions? = null, val plan: Program? = null) : Screen

    data class Today(val model: TodayModel) : Screen

    /** T2: the readiness check (RDY-001…007) with pain, minutes, red flags, illness and missing equipment. */
    data class CheckIn(val form: CheckInForm, val next: PlannedDay) : Screen

    /** T3: the generated session before it starts, with the tier and why. */
    data class Preview(val model: PreviewModel) : Screen

    /** R1: an unfinished workout was found at launch. */
    data class Resume(val view: PlayerView) : Screen

    /** Workout mode (A1–A7): no tabs; one sheet at a time. */
    data class Workout(val view: PlayerView, val sheet: WorkoutSheet? = null, val notice: WorkoutNotice? = null) : Screen

    /** A8, with the next planned session (J1 step 7). */
    data class Done(val summary: Summary, val rated: Boolean = false, val next: PlannedDay? = null) : Screen

    /** SAF-003 at the check-in: the pain reported ends training for today (no session is generated). */
    data class PainDay(val outcome: PainOutcome) : Screen

    /** Settings → health conditions: the same questions as onboarding, answers changeable at any time (SAF-010, R5-12). */
    data class EditConditions(val form: OnboardingForm, val problem: OnboardingProblem? = null) : Screen

    /** A5: a red flag. Calm guidance and the emergency number; training waits until the user confirms (SAF-002). */
    data class Stop(val stop: SafetyStop) : Screen

    data class Settings(val model: SettingsModel) : Screen

    // ---------------------------------------------------------------------------------------------------- Part 6 tabs
    /** H1: finished sessions, newest first. */
    /** `hasOlder`: sessions exist before the weeks shown ("Show older sessions" is offered; review R6-10). */
    data class History(val items: List<com.personalfitnesscoach.data.core.insight.HistoryItem>, val weeks: Int, val hasOlder: Boolean = false) : Screen

    /** H2: one session with every set; a set can be corrected or deleted (FS-5, rebuilt and logged). */
    data class SessionDetail(val detail: com.personalfitnesscoach.data.core.insight.SessionDetail, val edit: SetEdit? = null,
                             val problem: Boolean = false) : Screen

    /** P1: lifts, weeks, streak, achievements, body and steps; `entry` is the open weight or waist entry. */
    data class Progress(val overview: com.personalfitnesscoach.data.core.insight.ProgressOverview, val entry: BodyEntry? = null,
                        val problem: Boolean = false) : Screen

    /** P2: one exercise over time, with like and "don't suggest". */
    data class ExerciseDetail(val history: com.personalfitnesscoach.data.core.insight.ExerciseHistory) : Screen

    /** C1: suggested questions, a typed question, the answer, "Why?" and Ask Gemini. */
    data class Coach(val model: CoachModel) : Screen

    /** Settings → goals, week, gym or injuries: the onboarding page for that part, with the stored answers (D-086). */
    data class EditProfile(val section: ProfileSection, val form: OnboardingForm, val problem: OnboardingProblem? = null,
                           val goals: GoalOptions? = null) : Screen
}

/** The four tabs (Phase 2 section 5); workout mode and onboarding have none. */
enum class Tab { TODAY, HISTORY, PROGRESS, COACH }

/** The tab a screen belongs to (null = no tab bar). */
/** The rest still running behind this screen: the workout's own, or the workout's under a "Why?" opened from it (the alarm stays set). */
fun Screen.restEndsAtMs(): Long? = when (this) {
    is Screen.Workout -> view.restEndsAtMs
    is Screen.Coach -> (model.back as? Screen.Workout)?.view?.restEndsAtMs
    else -> null
}

fun Screen.tab(): Tab? = when (this) {
    is Screen.Today -> Tab.TODAY
    is Screen.History, is Screen.SessionDetail -> Tab.HISTORY
    is Screen.Progress, is Screen.ExerciseDetail -> Tab.PROGRESS
    // Opened over a workout (a "Why?" during the session): workout mode has no tabs (Phase 2 section 5; review R6-05).
    is Screen.Coach -> if (model.back is Screen.Workout || model.back is Screen.Resume) null else Tab.COACH
    else -> null
}

/** A past set being corrected (steppers, not typing; NewSet ranges apply). */
data class SetEdit(val setId: Long, val exerciseId: String, val kind: String, val loadKg: Double?, val reps: Int?, val seconds: Int?, val rir: Double?,
                   /** Delete asks once more before the set is removed (it can't be undone; review R6-12). */
                   val confirmDelete: Boolean = false)

/** FL-005 entries from Progress. */
sealed interface BodyEntry {
    /** Null until a number is typed (no made-up starting weight; review R6-14). */
    data class Weight(val kg: Double?) : BodyEntry
    /** Up to three readings, averaged. */
    data class Waist(val readings: List<Double>) : BodyEntry
}

enum class ProfileSection { GOALS, SCHEDULE, EQUIPMENT, INJURIES }

data class CoachModel(
    val text: String = "",
    val answer: com.personalfitnesscoach.data.core.coach.CoachAnswer? = null,
    /** The exercise the answer is about (the coach was opened from it, or the user picked another). */
    val exerciseId: String? = null,
    /** "Why?" for one decision, opened from Today, the preview, the summary or the answer. */
    val why: com.personalfitnesscoach.data.core.coach.Why? = null,
    /** D-045: the exact text shared to Gemini, shown before sharing. */
    val share: com.personalfitnesscoach.data.core.coach.ShareContent? = null,
    /** SAF-002 from the coach: the warning-sign list, picked so far. */
    val warning: Set<String>? = null,
    /** Where the coach was opened from, to go back there (a screen without a tab, e.g. the preview). */
    val back: Screen? = null,
)

// ============================================================================================================ onboarding
/** Answers being given on the current onboarding step (saved when the step is submitted). */
data class OnboardingForm(
    val screening: Map<ScreeningQuestion, Boolean> = emptyMap(),
    val birthYear: Int? = null,
    val months: ExperienceBand? = null,
    val comfortable: Set<String> = emptySet(),
    val structured: Boolean = false,
    val confidentEffort: Boolean = false,
    val referenceSex: ReferenceSex? = null,
    val bodyweightKg: Double? = null,
    val conditions: Map<String, ConditionAnswers> = emptyMap(),
    val noneOfThese: Boolean = false,
    val goals: List<Goal> = emptyList(),
    val weightFeaturesOff: Boolean = false,
    val daysPerWeek: Int = 3,
    val availableDays: Set<Int> = setOf(0, 1, 2, 3, 4, 5, 6),
    val preferredDays: Set<Int> = setOf(0, 2, 4),
    val sessionMinutes: Int = 60,
    val gym: Set<String> = emptySet(),
    val gymPreset: GymPreset? = null,
    val increments: Increments = Increments(),
    val excludedModalities: Set<Modality> = emptySet(),
    val joints: Set<Joint> = emptySet(),
    val numbers: Map<String, NumberEntry> = emptyMap(),
)

/** SAF-001 questions in the order shown (original wording in the string resources). */
enum class ScreeningQuestion { HEART_OR_BLOOD_PRESSURE, METABOLIC_RENAL_PULMONARY, SYMPTOMS, PALPITATIONS, LIMIT_OR_PREGNANCY, MUSCULOSKELETAL,
    LONG_TERM_MEDICATION, REGULARLY_ACTIVE }

/** EXP-001 months of consistent training, as the screen offers them. */
enum class ExperienceBand(val months: Int) { UNDER_6(0), SIX_TO_12(9), ONE_TO_3_YEARS(24), OVER_3_YEARS(48) }

/** The answers for one picked condition (SAF-010 asks; every field maps to the stored condition). */
data class ConditionAnswers(
    val status: ControlStatus? = null,
    val clearance: Set<ClearanceScope> = emptySet(),
    val subFlags: Set<String> = emptySet(),
    val pregnancyWeek: Int? = null,
    val weeksSinceBirth: Int? = null,
    val attested: Boolean = false,
    val blockIfYes: Boolean = false,
    val previouslyVigorous: Boolean = false,
    val alreadyDoingImpact: Boolean = false,
    val supineUncomfortable: Boolean = false,
    val impactChecksPassed: Boolean = false,
    val flare: Boolean = false,
    /** The user wants impact work added once the table allows it (knee/hip arthritis, severe obesity). */
    val impactOptIn: Boolean = false,
    /** Pregnancy `attest_if`: one of the listed conditions applies and the provider has not OK'd exercise → follow the provider (stored as block). */
    val attestNotOk: Boolean = false,
)

enum class GymPreset { FULL_GYM, DUMBBELLS_AND_MACHINES, HOME_DUMBBELLS, BODYWEIGHT_ONLY }

/** PROG-003 real increments, as asked on O5 (kg). */
data class Increments(
    val barKg: Double = 20.0,
    val smallestPlateKg: Double = 1.25,
    val dumbbellMinKg: Double = 2.5,
    val dumbbellMaxKg: Double = 50.0,
    val dumbbellStepKg: Double = 2.5,
    val stackStepKg: Double = 5.0,
    val stackMaxKg: Double = 100.0,
) {
    fun inventory(): Inventory {
        // The standard plates down to the smallest the gym has, and that smallest plate itself even when it is an unusual size (R5-24).
        val plates = (listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25).filter { it > smallestPlateKg + 1e-9 } + smallestPlateKg).distinct().sortedDescending()
            .associateWith { if (it == 25.0) 4 else 2 }
        val dbs = generateSequence(dumbbellMinKg) { it + dumbbellStepKg }.takeWhile { it <= dumbbellMaxKg + 1e-9 }.map { Math.round(it * 100) / 100.0 }.toList()
        return Inventory(barKg = barKg, plates = plates, dumbbells = dbs, stack = com.personalfitnesscoach.engine.calc.Stack(stackStepKg, stackMaxKg, stackStepKg))
    }
}

/** CAL-002 own number for one exercise. */
data class NumberEntry(val loadKg: Double? = null, val reps: Int? = null, val rir: Double? = 2.0, val bestLift: Boolean = false, val daysAgo: Int = 7)

// ============================================================================================================ today
data class TodayModel(
    val plan: TodayPlan,
    /** The next planned session (SCH-002); null when this week's sessions are done. */
    val next: PlannedDay?,
    val nextIsToday: Boolean,
    val week: List<DayCell>,
    val checkIn: ReadinessRecord?,
    /** SAF-002 stop not yet confirmed. */
    val stopOpen: Set<String>?,
    /** ADH-002: a planned session was missed since the last one — a neutral "welcome back" (never guilt). */
    val welcomeBack: Boolean,
    val backupReminder: Boolean,
    /** LOAD-002 late prompt: a finished session waiting for its rating. */
    val rateWorkoutId: Long?,
    val streak: StreakState,
    val stepsToday: Int?,
    val stepTarget: Int?,
    /** SAF-001 / SAF-010 conservative mode while a doctor's OK is outstanding. */
    val conservative: Boolean,
    /** SAF-010 prompts for today's conditions (from the table). */
    val conditionPrompts: List<String>,
    val doneToday: Boolean,
    /** Today's readiness, safety, return and deload decisions (the one-line "why"). */
    val reasons: List<DecisionEntry>,
    /** SAF-003: a pain reported today ends training for the day. */
    val painStop: Boolean = false,
    /** Why gentle (conservative) mode is on, so the card says what ends it. */
    val conservativeReason: ConservativeReason? = null,
    /** SAF-001: the health questions are due again (12 months). */
    val rescreenDue: Boolean = false,
    /** SAF-010 low back pain: the "my back is flaring" switch (null when the condition isn't picked). */
    val backFlare: Boolean? = null,
    /** The next planned session, also after today's is done (shown as "Next: …"). */
    val upcoming: PlannedDay? = null,
    /** The hour (0–23) on the app's clock, for the greeting (D-099); today's day number for the date line. */
    val hour: Int = 12,
    val today: Int = 0,
)

enum class ConservativeReason {
    /** The screening recommends a doctor's OK (confirmed in Settings). */
    SCREENING_DOCTOR,
    /** Screening question 5: follow the clinician's guidance; answering the questions again ends it. */
    CLINICIAN,
    /** A condition's table entry needs a doctor's OK (Settings, per condition). */
    CONDITION,
}

data class DayCell(val weekday: Int, val day: Int, val template: DayTemplate?, val state: DayState, val today: Boolean)

enum class DayState { PLANNED, DONE, SKIPPED, REST, MISSED }

/** T2. Ratings 1–5 (3 = normal). Pain, red flags and illness are separate gates (SAF-002/003/007). */
data class CheckInForm(
    val sleep: Int = 3,
    val energy: Int = 3,
    val soreness: Int = 3,
    val stress: Int = 3,
    val sleepHours: Double? = null,
    val minutes: Int,
    val pain: PainForm? = null,
    val redFlags: Set<String> = emptySet(),
    val illness: Set<String> = emptySet(),
    val missingEquipment: Set<String> = emptySet(),
    val awayFromGym: Boolean = false,
)

/** A4 / T2 pain report. */
data class PainForm(val region: Joint? = null, val kind: PainKind? = null, val rating: Int = 3, val worsening: Boolean = false,
                    val descriptors: Set<String> = emptySet(), val wholeBody: Boolean = false)

data class PreviewModel(
    val session: GeneratedSession,
    val tier: Tier,
    /** RDY-007: tiers the user may pick instead (easier always; one step harder, with a warning, when nothing locks it). */
    val canChoose: List<Tier>,
    val reasons: List<Decision>,
    val minutes: Int,
)

// ============================================================================================================ workout
sealed interface WorkoutSheet {
    /** A3 Replace (or "occupied" with "do it later"). */
    data class Replace(val choice: SwapChoice) : WorkoutSheet
    data class Hurts(val rowId: Long?, val form: PainForm = PainForm()) : WorkoutSheet
    data class HurtsResult(val outcome: PainOutcome, val changes: List<Change>, val alternatives: SwapChoice?, val region: Joint? = null) : WorkoutSheet
    data class ChangeTime(val minutes: Int) : WorkoutSheet
    data class RedFlag(val picked: Set<String> = emptySet()) : WorkoutSheet
    /** FS-5: an unusual entry needs a confirmation before it is saved (for the set it was typed for). */
    data class ConfirmEntry(val rowId: Long, val entry: LiftEntry, val expected: com.personalfitnesscoach.data.core.player.SetTarget? = null) : WorkoutSheet
    /** SAF-006 past a cap. */
    data class ConfirmAddSet(val rowId: Long) : WorkoutSheet
    /** End now: finish early or, with nothing logged, discard. */
    data object End : WorkoutSheet
    /** A7: the interval or steady timer for a conditioning block. */
    data class Timer(val index: Int) : WorkoutSheet
    /** The whole session in order, with what is done and what is next (PO feedback, UAT 26 Oct 2026). */
    data object Plan : WorkoutSheet
}

/** One-line messages after a change (the decision behind it is in the "Why?" log). */
sealed interface WorkoutNotice {
    /** `shortestSafeMinutes`: the time asked for is below what can't be cut; the shortest this session can be from here (D-071, N7). */
    data class Replanned(val changes: List<Change>, val shortestSafeMinutes: Int? = null) : WorkoutNotice
    data class Refused(val decisions: List<Decision>) : WorkoutNotice
    data class AddSet(val verdict: AdditionVerdict) : WorkoutNotice
    data class LoadChanged(val decision: Decision) : WorkoutNotice
}

// ============================================================================================================ settings
data class SettingsModel(
    val stepTracking: Boolean,
    val stepCounterAvailable: Boolean,
    val units: Units,
    val lastExportDay: Int?,
    val flow: SettingsFlow? = null,
    val appVersion: String,
    val registryVersion: String,
    val libraryVersion: String,
    val conditionsVersion: String,
    /** SAF-001: the screening recommends a doctor's OK and none was confirmed yet. */
    val screeningClearanceNeeded: Boolean = false,
    /** The day a doctor's OK for the screening was confirmed. */
    val screeningClearanceDay: Int? = null,
    /** SAF-010: picked conditions whose table entry asks for (or suggests) a doctor's OK, with the scopes confirmed so far. */
    val clearances: List<ConditionClearance> = emptyList(),
    /** SAF-001 question 5: the clinician's guidance applies; answering the questions again is the way out. */
    val screeningGuidance: Boolean = false,
    /** Android 13+: notifications are off, so the rest timer can't buzz while the phone is locked. */
    val alertsOff: Boolean = false,
    val theme: com.personalfitnesscoach.data.core.model.Theme = com.personalfitnesscoach.data.core.model.Theme.DARK,
    /** SUB-002: exercises the user asked never to be suggested (undo here). */
    val excludedExercises: List<String> = emptyList(),
    /** FL-001: weight and waist tracking is off (switchable here when the goals allow it). */
    val weightFeaturesOff: Boolean = false,
    val weightFeaturesAllowed: Boolean = true,
)

/** One condition's doctor's-OK scopes (each scope unlocks only itself, SAF-010). `rule` is the table's clearance kind. */
data class ConditionClearance(val id: String, val name: String, val rule: String, val confirmed: Set<ClearanceScope>)

/** Backup, restore and erase dialogs (Phase 2 section 12). */
sealed interface SettingsFlow {
    data class Export(val usePassword: Boolean = false, val problem: Boolean = false) : SettingsFlow
    /** The file was written. */
    data object Exported : SettingsFlow
    /** FL-001 after a condition change: goals no longer offered were dropped and/or weight features switched off (re-check N2). */
    data class GoalsChanged(val removed: List<com.personalfitnesscoach.engine.program.Goal>, val now: List<com.personalfitnesscoach.engine.program.Goal>,
                            val weightFeaturesOff: Boolean) : SettingsFlow
    data class RestoreNeedsPassword(val bytes: ByteArray, val wrong: Boolean = false) : SettingsFlow {
        override fun equals(other: Any?) = other is RestoreNeedsPassword && other.wrong == wrong && other.bytes.contentEquals(bytes)
        override fun hashCode() = bytes.contentHashCode() * 31 + wrong.hashCode()
    }
    data class RestorePreview(val summary: BackupSummary, val token: Int) : SettingsFlow
    data class RestoreRefused(val problem: BackupProblem) : SettingsFlow
    data object Restored : SettingsFlow
    /** Two-step confirmation (Phase 2 section 12). */
    data class Erase(val confirmations: Int) : SettingsFlow
    /** SAF-001 re-screen: the eight questions again; `result` once saved. */
    data class Rescreen(val answers: Map<ScreeningQuestion, Boolean> = emptyMap(), val result: ScreeningResult? = null) : SettingsFlow
    /** A profile section was saved (goals, week, gym or injuries). */
    data class ProfileSaved(val section: ProfileSection) : SettingsFlow
}
