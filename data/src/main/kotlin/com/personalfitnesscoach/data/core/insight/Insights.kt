package com.personalfitnesscoach.data.core.insight

import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.model.DecisionEntry
import com.personalfitnesscoach.data.core.model.ExerciseState
import com.personalfitnesscoach.data.core.model.PreferencesRecord
import com.personalfitnesscoach.data.core.model.ProgramRecord
import com.personalfitnesscoach.data.core.model.SettingsRecord
import com.personalfitnesscoach.data.core.model.StepStateRecord
import com.personalfitnesscoach.data.core.model.StepsRecord
import com.personalfitnesscoach.data.core.model.WaistRecord
import com.personalfitnesscoach.data.core.model.WeekSummary
import com.personalfitnesscoach.data.core.model.WeightRecord
import com.personalfitnesscoach.engine.model.FormCheck
import com.personalfitnesscoach.data.core.session.NewSet
import com.personalfitnesscoach.data.core.session.SetKind
import com.personalfitnesscoach.data.core.session.Status
import com.personalfitnesscoach.data.core.session.StoredWorkout
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.engine.calc.E1rm
import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.program.Achievement
import com.personalfitnesscoach.engine.program.AchievementInput
import com.personalfitnesscoach.engine.program.Achievements
import com.personalfitnesscoach.engine.program.DayTemplate
import com.personalfitnesscoach.engine.program.StreakState
import com.personalfitnesscoach.engine.program.Streak
import com.personalfitnesscoach.engine.program.WeekRecord
import com.personalfitnesscoach.engine.progress.BodyProgress
import com.personalfitnesscoach.engine.progress.LiftTrend
import com.personalfitnesscoach.engine.progress.WeightEntry
import com.personalfitnesscoach.engine.progress.WeightTrend
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds

// ============================================================================================================ history (H1, H2)
/** How a session ended, as History shows it (Phase 2 states DONE / PARTIAL; a safety stop; a day marked skipped). */
enum class SessionOutcome { DONE, PARTIAL, STOPPED, SKIPPED }

/** One row of H1. */
data class HistoryItem(
    val workoutId: Long,
    val day: Int,
    val template: DayTemplate,
    val tier: Tier?,
    val outcome: SessionOutcome,
    /** Session minutes as stored (pauses and idle gaps removed, D-085); null for a skipped day. */
    val minutes: Double?,
    /** Working and finding-your-weight sets logged. */
    val sets: Int,
    val sessionRpe: Double?,
    val conditioningMinutes: Double,
)

data class SetLine(val setId: Long, val index: Int, val kind: String, val loadKg: Double?, val reps: Int?, val seconds: Int?, val rir: Double?,
                   val form: FormCheck)

data class ExerciseLine(val rowId: Long, val exerciseId: String, val status: String, val sets: List<SetLine>)

/** H2: one session with every set, and what the engine decided for it ("Why?"). */
data class SessionDetail(val item: HistoryItem, val exercises: List<ExerciseLine>, val decisions: List<DecisionEntry>, val editable: Boolean)

// ============================================================================================================ progress (P1, P2)
/** One finished calendar week (P1 weekly view, PH-001). */
data class WeekLine(
    val weekStartDay: Int,
    val planned: Int,
    val completed: Int,
    /** PH-001 equivalent minutes: Z1 and brisk walks + 2 × harder work. */
    val equivalentMinutes: Double,
    val strengthDays: Int,
) {
    /** PH-001: at least 150 equivalent minutes and 2 strength days. */
    val whoFloorMet: Boolean get() = equivalentMinutes >= P.PH_001.target_equivalent_minutes && strengthDays >= P.PH_001.strength_days_min
}

/** A tracked lift on P1: its latest estimated max and the FL-005 trend. */
data class LiftLine(val exerciseId: String, val e1rm: Double?, val trend: LiftTrend?, val points: List<LiftPoint>)

/** One exposure of a lift: the day's best working set and its estimated max (INT-004). */
data class LiftPoint(val day: Int, val e1rm: Double?, val bestLoad: Double, val bestReps: Int)

data class StepDay(val day: Int, val steps: Int)

data class BodyLine(
    val weight: WeightTrend,
    /** Readings of the last 12 weeks, oldest first (P1 chart). */
    val weights: List<WeightEntry>,
    val waistCm: Double?,
    val waistDay: Int?,
    val waistDue: Boolean,
)

/** P1, everything at once (A9: one screen, no dashboard of menus). */
data class ProgressOverview(
    val lifts: List<LiftLine>,
    val weeks: List<WeekLine>,
    val streak: StreakState,
    val achievements: Set<Achievement>,
    val sessionsCompleted: Int,
    val whoWeeks: Int,
    /** Null when weight features are off (FL-001) — nothing about weight or waist is shown. */
    val body: BodyLine?,
    val steps: List<StepDay>,
    val stepTarget: Int?,
)

/** P2: one exercise over time, with its next prescription and the user's like or skip. */
data class ExerciseHistory(
    val exerciseId: String,
    val points: List<LiftPoint>,
    /** The best working set ever (highest estimated max, or the heaviest load × reps when the lift has no estimate). */
    val best: LiftPoint?,
    val state: ExerciseState?,
    val favourite: Boolean,
    val excluded: Boolean,
)

/**
 * History and progress from what is stored (Phase 2 H1/H2, P1/P2; FL-005, PH-001, ADH-001/002, STEP-001). Reads only, except the
 * entries the user makes here: a corrected or deleted past set (rebuilt and logged, FS-5), weight and waist, likes and skips.
 */
class Insights(private val d: PfcData) {

    // -------------------------------------------------------------------------------------------- history
    suspend fun history(fromDay: Int, toDay: Int): List<HistoryItem> = d.store.transaction {
        d.sessions.between(fromDay, toDay).filter { it.row.status == Status.DONE || it.row.status == Status.SKIPPED }
            .sortedWith(compareByDescending<StoredWorkout> { it.day }.thenByDescending { it.id }).map { item(it) }
    }

    private fun item(w: StoredWorkout): HistoryItem {
        val outcome = when {
            w.row.status == Status.SKIPPED -> SessionOutcome.SKIPPED
            w.doc.stoppedBySafety -> SessionOutcome.STOPPED
            w.doc.endedEarly -> SessionOutcome.PARTIAL
            else -> SessionOutcome.DONE
        }
        return HistoryItem(w.id, w.day, w.template, w.row.tier?.let { runCatching { Tier.valueOf(it) }.getOrNull() }, outcome,
            if (outcome == SessionOutcome.SKIPPED) null else w.row.actualMinutes,
            w.exercises.sumOf { e -> e.sets.count { it.kind != SetKind.WARMUP } }, w.row.sessionRpe,
            w.doc.conditioning.sumOf { it.doneWorkMinutes ?: 0.0 })
    }

    suspend fun session(workoutId: Long): SessionDetail? = d.store.transaction {
        val w = d.sessions.load(workoutId) ?: return@transaction null
        if (w.row.status != Status.DONE && w.row.status != Status.SKIPPED) return@transaction null
        val ex = w.exercises.sortedBy { it.row.position }.map { e ->
            ExerciseLine(e.row.id, e.exerciseId, e.row.status, e.sets.sortedBy { it.setIndex }.map { s ->
                SetLine(s.id, s.setIndex, s.kind, s.loadKg, s.reps, s.seconds, s.rir, FormCheck.valueOf(s.formCheck))
            })
        }
        SessionDetail(item(w), ex, d.decisions.forWorkout(w.id, w.day), editable = w.row.status == Status.DONE)
    }

    /**
     * FS-5: a set of a finished workout is corrected; every exercise's state is rebuilt from the logged sets and the change is logged
     * ("Why?"). The same ranges as logging apply (NewSet), and a set only belongs to its own exercise.
     */
    suspend fun correctSet(setId: Long, loadKg: Double?, reps: Int?, seconds: Int?, rir: Double?) = d.store.transaction {
        val (w, e, s) = locate(setId)
        val set = NewSet(s.setIndex, s.kind, loadKg, reps, seconds, s.metres, rir, s.rpe, FormCheck.valueOf(s.formCheck), "ADJUSTED")
        d.correctPastSet(setId, e.row.id, set)
        d.decisions.append(listOf(Decision(DecisionKind.LOAD_CHANGE, listOf(RuleIds.PROG_001, RuleIds.INT_004), ReasonKey.HISTORY_EDITED,
            inputs = mapOf("exercise" to e.exerciseId, "before" to listOf(s.loadKg, s.reps, s.seconds, s.rir)),
            outputs = mapOf("after" to listOf(loadKg, reps, seconds, rir)))), w.day, w.id)
    }

    suspend fun deleteSet(setId: Long) = d.store.transaction {
        val (w, e, s) = locate(setId)
        d.deletePastSet(setId)
        d.decisions.append(listOf(Decision(DecisionKind.LOAD_CHANGE, listOf(RuleIds.PROG_001, RuleIds.INT_004), ReasonKey.HISTORY_EDITED,
            inputs = mapOf("exercise" to e.exerciseId, "before" to listOf(s.loadKg, s.reps, s.seconds, s.rir)), outputs = mapOf("deleted" to true))),
            w.day, w.id)
    }

    private suspend fun locate(setId: Long): Triple<StoredWorkout, com.personalfitnesscoach.data.core.session.StoredExercise, com.personalfitnesscoach.data.core.store.SetRow> {
        val all = d.sessions.done(Int.MIN_VALUE / 2, Int.MAX_VALUE / 2)
        for (w in all) for (e in w.exercises) for (s in e.sets) if (s.id == setId) return Triple(w, e, s)
        throw IllegalArgumentException("set $setId is not part of a finished workout")
    }

    // -------------------------------------------------------------------------------------------- progress
    suspend fun overview(today: Int = d.clock.today()): ProgressOverview = d.store.transaction {
        val all = d.sessions.done(Int.MIN_VALUE / 2, today)
        val points = liftPoints(all)
        val states = d.docs.all(ExerciseState).associateBy { it.exerciseId }
        val program = d.docs.get(ProgramRecord)
        val core = program?.coreLifts?.values?.toSet().orEmpty()
        val trend = d.bridge.body(today).strength.lifts.associateBy { it.exerciseId }
        // Tracked lifts: those with an estimated max or a trend, the block's main lifts first, then the most recent.
        val ids = (states.values.filter { it.e1rm != null }.map { it.exerciseId } + trend.keys).distinct()
        val lifts = ids.map { id -> LiftLine(id, states[id]?.e1rm, trend[id], points[id].orEmpty().filter { it.day >= today - 7 * 12 }) }
            .sortedWith(compareByDescending<LiftLine> { it.exerciseId in core }.thenByDescending { l -> l.points.maxOfOrNull { it.day } ?: Int.MIN_VALUE })
        val summaries = d.docs.all(WeekSummary).sortedBy { it.weekStartDay }
        val weeks = summaries.map { s -> WeekLine(s.weekStartDay, s.planned, s.completed, s.equivalentMinutes, strengthDays(all, s.weekStartDay)) }
        val streak = Streak.compute(summaries.map { WeekRecord(it.planned, it.completed, Days.date(it.weekStartDay).year) }, Days.date(today).year)
        val records = points.values.sumOf { records(it) }
        val achievements = Achievements.earned(AchievementInput(
            sessionsCompleted = all.size,
            calibrationDone = (program?.blockIndex ?: 0) > 0,
            newRecords = records,
            blocksCompleted = program?.blockIndex ?: 0,
            whoFloorMetThisWeek = weeks.lastOrNull()?.whoFloorMet == true,
            firstAfterMiss = false,
        ))
        val settings = d.docs.get(SettingsRecord) ?: SettingsRecord()
        val body = if (settings.weightFeaturesOff) null else {
            val b = d.bridge.body(today)
            BodyLine(b.weight, d.docs.between(WeightRecord, today - 7 * 12, today).sortedBy { it.day }.map { WeightEntry(it.day, it.kg) },
                b.waistCm, b.waistDay, b.waistDue)
        }
        val steps = if (settings.stepTracking || d.docs.between(StepsRecord, today - 6, today).isNotEmpty())
            d.docs.between(StepsRecord, today - 6, today).sortedBy { it.day }.map { StepDay(it.day, it.steps) } else emptyList()
        ProgressOverview(lifts.take(12), weeks.takeLast(8), streak, achievements, all.size, weeks.count { it.whoFloorMet }, body, steps,
            d.docs.get(StepStateRecord)?.target)
    }

    suspend fun exercise(exerciseId: String, today: Int = d.clock.today()): ExerciseHistory = d.store.transaction {
        require(Library[exerciseId] != null) { "unknown exercise $exerciseId" }
        val points = liftPoints(d.sessions.done(Int.MIN_VALUE / 2, today))[exerciseId].orEmpty()
        val best = points.maxWithOrNull(compareBy<LiftPoint> { it.e1rm ?: 0.0 }.thenBy { it.bestLoad * (1.0 + it.bestReps / 30.0) })
        val prefs = d.docs.get(PreferencesRecord) ?: PreferencesRecord()
        ExerciseHistory(exerciseId, points, best, d.docs.get(ExerciseState, exerciseId), exerciseId in prefs.favourites, exerciseId in prefs.excludedIds)
    }

    /** One point per exposure: the day's best working set (load × reps) and its estimate; safety-stopped work is not progress (D-081). */
    private fun liftPoints(done: List<StoredWorkout>): Map<String, List<LiftPoint>> {
        val out = LinkedHashMap<String, MutableList<LiftPoint>>()
        for (w in done.sortedWith(compareBy({ it.day }, { it.id }))) {
            if (w.doc.stoppedBySafety) continue
            for (e in w.exercises) {
                val logs = e.workingLogs().filter { it.load > 0 }
                val top = logs.maxByOrNull { it.load * (1.0 + it.reps / 30.0) } ?: continue
                out.getOrPut(e.exerciseId) { ArrayList() } += LiftPoint(w.day, E1rm.sessionBest(logs), top.load, top.reps)
            }
        }
        return out
    }

    /** ADH-002 personal records: exposures that beat every earlier one (the first exposure is the starting point, not a record). */
    private fun records(points: List<LiftPoint>): Int {
        var best = Double.NaN
        var n = 0
        for (p in points) {
            val v = p.e1rm ?: (p.bestLoad * (1.0 + p.bestReps / 30.0))
            if (!best.isNaN() && v > best + 1e-9) n++
            best = if (best.isNaN()) v else maxOf(best, v)
        }
        return n
    }

    private suspend fun strengthDays(done: List<StoredWorkout>, weekStart: Int): Int =
        (weekStart..weekStart + 6).count { day -> done.any { w -> w.day == day && w.exercises.any { e -> e.sets.any { it.kind != SetKind.WARMUP } } } }

    // -------------------------------------------------------------------------------------------- entries
    /** FL-005 bodyweight, once a day (a second entry the same day replaces the first). Refused when weight features are off. */
    suspend fun logWeight(kg: Double, day: Int = d.clock.today()) = d.store.transaction {
        check((d.docs.get(SettingsRecord) ?: SettingsRecord()).weightFeaturesOff.not()) { "weight features are off" }
        d.docs.put(WeightRecord, WeightRecord(day, Math.round(kg * 10) / 10.0))
    }

    /** FL-005 waist: up to three readings (cm), averaged. */
    suspend fun logWaist(readingsCm: List<Double>, day: Int = d.clock.today()) = d.store.transaction {
        check((d.docs.get(SettingsRecord) ?: SettingsRecord()).weightFeaturesOff.not()) { "weight features are off" }
        d.docs.put(WaistRecord, WaistRecord(day, readingsCm.map { Math.round(it * 10) / 10.0 }))
    }

    /** SUB-002 favourites: kept when accessories rotate (ADH-003). */
    suspend fun setFavourite(exerciseId: String, on: Boolean) = d.store.transaction {
        require(Library[exerciseId] != null) { "unknown exercise $exerciseId" }
        d.docs.update(PreferencesRecord) { p -> (p ?: PreferencesRecord()).let { it.copy(favourites = if (on) it.favourites + exerciseId else it.favourites - exerciseId) } }
    }

    /**
     * "Don't suggest this": never planned or offered as a swap again (SUB-001 filter). Safety and pattern coverage still come first —
     * the planner picks another exercise for the same movement.
     */
    suspend fun setExcluded(exerciseId: String, on: Boolean) = d.store.transaction {
        require(Library[exerciseId] != null) { "unknown exercise $exerciseId" }
        d.docs.update(PreferencesRecord) { p -> (p ?: PreferencesRecord()).let {
            it.copy(excludedIds = if (on) it.excludedIds + exerciseId else it.excludedIds - exerciseId, favourites = if (on) it.favourites - exerciseId else it.favourites)
        } }
    }

    suspend fun excluded(): List<String> = d.store.transaction { (d.docs.get(PreferencesRecord) ?: PreferencesRecord()).excludedIds.sorted() }
}
