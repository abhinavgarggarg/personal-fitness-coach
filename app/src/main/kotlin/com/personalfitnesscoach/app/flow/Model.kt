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

    /** A8. */
    data class Done(val summary: Summary, val rated: Boolean = false) : Screen

    /** A5: a red flag. Calm guidance and the emergency number; training waits until the user confirms (SAF-002). */
    data class Stop(val stop: SafetyStop) : Screen

    data class Settings(val model: SettingsModel) : Screen
}

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
        val plates = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25).filter { it >= smallestPlateKg - 1e-9 }
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
)

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
    /** FS-5: an unusual entry needs a confirmation before it is saved. */
    data class ConfirmEntry(val rowId: Long, val entry: LiftEntry) : WorkoutSheet
    /** SAF-006 past a cap. */
    data class ConfirmAddSet(val rowId: Long) : WorkoutSheet
    /** End now: finish early or, with nothing logged, discard. */
    data object End : WorkoutSheet
    /** A7: the interval or steady timer for a conditioning block. */
    data class Timer(val index: Int) : WorkoutSheet
}

/** One-line messages after a change (the decision behind it is in the "Why?" log). */
sealed interface WorkoutNotice {
    data class Replanned(val changes: List<Change>) : WorkoutNotice
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
)

/** Backup, restore and erase dialogs (Phase 2 section 12). */
sealed interface SettingsFlow {
    data class Export(val usePassword: Boolean = false, val problem: Boolean = false) : SettingsFlow
    /** The file was written. */
    data object Exported : SettingsFlow
    data class RestoreNeedsPassword(val bytes: ByteArray, val wrong: Boolean = false) : SettingsFlow {
        override fun equals(other: Any?) = other is RestoreNeedsPassword && other.wrong == wrong && other.bytes.contentEquals(bytes)
        override fun hashCode() = bytes.contentHashCode() * 31 + wrong.hashCode()
    }
    data class RestorePreview(val summary: BackupSummary, val token: Int) : SettingsFlow
    data class RestoreRefused(val problem: BackupProblem) : SettingsFlow
    data object Restored : SettingsFlow
    /** Two-step confirmation (Phase 2 section 12). */
    data class Erase(val confirmations: Int) : SettingsFlow
}
