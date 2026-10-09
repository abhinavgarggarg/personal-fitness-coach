package com.personalfitnesscoach.data.core

import com.personalfitnesscoach.data.core.backup.BackupService
import com.personalfitnesscoach.data.core.backup.BackupSink
import com.personalfitnesscoach.data.core.backup.RestorePreview
import com.personalfitnesscoach.data.core.engine.CheckIns
import com.personalfitnesscoach.data.core.engine.DecisionLog
import com.personalfitnesscoach.data.core.engine.EngineBridge
import com.personalfitnesscoach.data.core.engine.ProgramClock
import com.personalfitnesscoach.data.core.engine.ProgressRecorder
import com.personalfitnesscoach.data.core.engine.UserState
import com.personalfitnesscoach.data.core.model.ProgramRecord
import com.personalfitnesscoach.data.core.model.ReadinessRecord
import com.personalfitnesscoach.data.core.model.SettingsRecord
import com.personalfitnesscoach.data.core.model.StepCounterRecord
import com.personalfitnesscoach.data.core.model.StepsRecord
import com.personalfitnesscoach.data.core.model.dayKey
import com.personalfitnesscoach.data.core.repo.Docs
import com.personalfitnesscoach.data.core.session.NewSet
import com.personalfitnesscoach.data.core.session.SessionLog
import com.personalfitnesscoach.data.core.session.Status
import com.personalfitnesscoach.data.core.session.StoredWorkout
import com.personalfitnesscoach.data.core.steps.StepLedger
import com.personalfitnesscoach.data.core.store.RowStore
import com.personalfitnesscoach.data.core.time.AppClock
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.data.core.time.SystemClock
import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.generation.SessionGenerator
import com.personalfitnesscoach.engine.generation.Workout
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.program.PlannedDay
import com.personalfitnesscoach.engine.program.WeekPlan
import com.personalfitnesscoach.engine.program.WeekPlanner

/** Today as the app shows it: the user, the programme position, this week's plan and the next planned session. */
data class TodayPlan(val user: UserState, val program: ProgramRecord, val week: WeekPlan, val next: PlannedDay?, val decisions: List<Decision>)

/**
 * The data layer's front door (Phase 3 Part 4). One instance per app, over one [RowStore] (Room on the phone, in memory in tests).
 * Every operation that changes more than one record runs in one transaction.
 */
class PfcData(val store: RowStore, val clock: AppClock = SystemClock(), appVersion: String) {
    val docs = Docs(store, clock)
    val sessions = SessionLog(store, clock)
    val bridge = EngineBridge(docs, sessions, clock)
    val decisions = DecisionLog(docs, store, clock)
    val progress = ProgressRecorder(docs, sessions, bridge)
    val checkIns = CheckIns(docs, bridge, clock)
    val programClock = ProgramClock(docs, sessions, bridge, clock)
    val backup = BackupService(store, docs, clock, appVersion)

    /** Run when the app opens or returns to the foreground: closes finished weeks (no background jobs, Phase 2 section 11). */
    suspend fun onAppOpen(): ProgramRecord? = store.transaction {
        val r = programClock.rollover()
        decisions.append(r.decisions)
        r.value
    }

    /** Starts the programme on the first day after onboarding (idempotent: an existing programme is kept). */
    suspend fun startProgramIfNeeded(): ProgramRecord = store.transaction { docs.get(ProgramRecord) ?: programClock.start() }

    /**
     * Plans today: rolls finished weeks over, plans this week from current data, and picks the next planned session (SCH-002,
     * SAF-010 "no strength on consecutive days"). Null before onboarding is complete.
     */
    suspend fun planToday(): TodayPlan? = store.transaction {
        onAppOpen()
        val u = bridge.user() ?: return@transaction null
        if (u.profile.onboardingStep != null) return@transaction null // onboarding not finished: nothing is planned yet
        val program = docs.get(ProgramRecord) ?: return@transaction null
        val plan = bridge.planWeek(u, program)
        val program2 = docs.get(ProgramRecord)!!
        val today = u.today
        val week = sessions.between(Days.weekStart(today), today)
        val doneWeekdays = week.filter { it.row.status == Status.DONE || it.row.status == Status.SKIPPED }.map { it.doc.weekday }.toSet()
        // Yesterday may be last week (Sunday before Monday): SAF-010 "no strength on consecutive days" looks across the week boundary.
        val strengthYesterday = sessions.done(today - 1, today - 1).any { it.template.strength }
        val doneToday = week.any { it.day == today && it.row.status == Status.DONE }
        val next = if (doneToday) null else WeekPlanner.nextSession(plan.value, doneWeekdays, strengthYesterday, u.conditions)
        TodayPlan(u, program2, plan.value, next, u.decisions + plan.decisions)
    }

    /**
     * GEN-001 for a planned day. Today's check-in decides: its tier caps the one asked for and its red flags and illness answers go to
     * the safety gates; the stored safety state (stop, return, lighter week) caps it again in [EngineBridge.request]. The validated
     * workout is what [SessionLog.start] saves.
     */
    suspend fun generate(t: TodayPlan, day: PlannedDay, tier: Tier, minutes: Int = t.user.profile.sessionMinutes,
                         redFlags: Set<String> = emptySet(), illnessSymptoms: Set<String> = emptySet()): EngineResult<Workout> {
        val checkIn = docs.get(ReadinessRecord, dayKey(t.user.today))
        val capped = checkIn?.let { Tier.min(tier, it.tier) } ?: tier
        val req = bridge.request(t.user, t.program, t.week, day, capped, minutes, redFlags + (checkIn?.redFlags ?: emptySet()),
            illnessSymptoms + (checkIn?.illnessSymptoms ?: emptySet()))
        val w = SessionGenerator.generate(req)
        decisions.append(w.decisions)
        return w
    }

    /** Finishes the active workout and turns its sets into the next prescriptions, in one transaction. */
    suspend fun finishWorkout(workoutId: Long, sessionRpe: Double?, actualMinutes: Double?, conditioningDoneMinutes: List<Double>? = null): StoredWorkout =
        store.transaction {
            val w = sessions.finish(workoutId, sessionRpe, actualMinutes, conditioningDoneMinutes)
            val u = checkNotNull(bridge.user(w.day)) { "no profile" }
            decisions.append(progress.record(w, u), w.day, w.id)
            w
        }

    /** Corrects a set of a finished workout from history; every exercise's state is rebuilt from the logged sets. */
    suspend fun correctPastSet(setId: Long, workoutExerciseId: Long, set: NewSet) = store.transaction {
        sessions.correctSet(setId, workoutExerciseId, set)
        bridge.user()?.let { progress.rebuild(it) }
    }

    /** Deletes a set of a finished workout from history and rebuilds every exercise's state. */
    suspend fun deletePastSet(setId: Long) = store.transaction {
        sessions.deleteSet(setId)
        bridge.user()?.let { progress.rebuild(it) }
    }

    /** The user confirms red-flag symptoms resolved or were reviewed (SAF-002); training resumes with a LIGHT session. */
    suspend fun confirmStopResolved(): Boolean = store.transaction { checkIns.confirmStopResolved() }

    /** A new step-counter reading (only kept while step tracking is on, D-062). */
    suspend fun recordStepReading(atMs: Long, elapsedMs: Long, counter: Long, bootCount: Int? = null) = store.transaction {
        if (docs.get(SettingsRecord)?.stepTracking != true) return@transaction
        val now = StepCounterRecord(atMs, elapsedMs, counter, bootCount)
        for ((day, steps) in StepLedger.split(docs.get(StepCounterRecord), now, clock.zone())) {
            val cur = docs.get(StepsRecord, dayKey(day))?.steps ?: 0
            docs.put(StepsRecord, StepsRecord(day, minOf(200_000, cur + steps)))
        }
        docs.put(StepCounterRecord, now)
    }

    /** Turns step tracking on or off. Off also forgets the counter reading, so turning it on again starts fresh. */
    suspend fun setStepTracking(on: Boolean) = store.transaction {
        docs.update(SettingsRecord) { (it ?: SettingsRecord()).copy(stepTracking = on) }
        if (!on) docs.delete(StepCounterRecord)
    }

    suspend fun completedWorkouts(): Int = sessions.between(Int.MIN_VALUE / 2, Int.MAX_VALUE / 2).count { it.row.status == Status.DONE }

    /** Restores a checked backup (current data saved first). The step-counter reading belongs to the old phone and is dropped. */
    suspend fun restore(preview: RestorePreview, safetyPassword: CharArray? = null, safetyCopy: BackupSink) {
        backup.restore(preview, safetyPassword, safetyCopy)
        docs.delete(StepCounterRecord)
        decisions.reset()
    }

    suspend fun eraseAll() {
        backup.eraseAll()
        decisions.reset()
    }
}
