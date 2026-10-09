package com.personalfitnesscoach.engine.program

import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds

/** One calendar week of training, oldest first in a history. */
data class WeekRecord(val planned: Int, val completed: Int, val year: Int = 0)

data class StreakState(
    val current: Int,
    val best: Int,
    /** Weeks this year that counted (with or without a freeze); never goes down. */
    val weeksTrainedThisYear: Int,
    val freezeUsedWeeksAgo: Int?,
)

/** ADH-001 forgiving weekly streak. */
object Streak {
    /** A week counts at ≥ planned − 1 sessions, minimum 2 — or 1 when 2 are planned. */
    fun threshold(planned: Int): Int = if (planned <= 2) 1 else maxOf(planned - 1, 2)

    fun counts(w: WeekRecord): Boolean = w.completed >= threshold(w.planned)

    /**
     * Replays the history. A missed week uses the freeze when none was used in the previous
     * 4 weeks; otherwise the current streak restarts, but best streak and weeks trained this
     * year are kept and shown, so progress never "resets to zero".
     */
    fun compute(history: List<WeekRecord>, thisYear: Int = history.lastOrNull()?.year ?: 0): StreakState {
        var current = 0; var best = 0; var lastFreeze: Int? = null; var trained = 0
        for ((i, w) in history.withIndex()) {
            // A week with nothing planned (holiday, marked break) neither counts nor breaks the streak.
            if (w.planned <= 0) continue
            if (counts(w)) {
                current++; if (w.year == thisYear) trained++
            } else if (lastFreeze == null || i - lastFreeze >= P.ADH_001.freeze_per_weeks) {
                lastFreeze = i // the streak survives this week
            } else current = 0
            best = maxOf(best, current)
        }
        return StreakState(current, best, trained, lastFreeze?.let { history.size - 1 - it })
    }
}

enum class Achievement { FIRST_SESSION, CALIBRATION_DONE, PERSONAL_RECORD, BLOCK_COMPLETED, SESSIONS_10, SESSIONS_25, SESSIONS_50, SESSIONS_100, WHO_FLOOR_WEEK, WELCOME_BACK }

data class AchievementInput(
    val sessionsCompleted: Int,
    val calibrationDone: Boolean,
    val newRecords: Int,
    val blocksCompleted: Int,
    val whoFloorMetThisWeek: Boolean,
    /** ADH-002 1.1.0: this is the first completed session after a missed planned session. */
    val firstAfterMiss: Boolean = false,
)

/** ADH-002 micro-achievements; nothing rewards sheer volume (sets, minutes or days beyond the plan). */
object Achievements {
    fun earned(i: AchievementInput): Set<Achievement> {
        val out = LinkedHashSet<Achievement>()
        if (i.sessionsCompleted >= 1) out += Achievement.FIRST_SESSION
        if (i.calibrationDone) out += Achievement.CALIBRATION_DONE
        if (i.newRecords > 0) out += Achievement.PERSONAL_RECORD
        if (i.blocksCompleted > 0) out += Achievement.BLOCK_COMPLETED
        val milestones = P.ADH_002.session_milestones
        val badges = listOf(Achievement.SESSIONS_10, Achievement.SESSIONS_25, Achievement.SESSIONS_50, Achievement.SESSIONS_100)
        for ((m, a) in milestones.zip(badges)) if (i.sessionsCompleted >= m) out += a
        if (i.whoFloorMetThisWeek) out += Achievement.WHO_FLOOR_WEEK
        if (i.firstAfterMiss && P.ADH_002.welcome_back) out += Achievement.WELCOME_BACK
        return out
    }

    /**
     * ADH-002 1.1.0: after a missed planned session, one neutral nudge on the next planned day ("Ready when you are; today's
     * plan is set") and no more; never penalty, guilt or streak-loss wording (COACH-001).
     */
    fun nudgeToday(missedSinceLastSession: Boolean, nudgesSentSinceMiss: Int, plannedToday: Boolean): EngineResult<Boolean> {
        val send = missedSinceLastSession && plannedToday && nudgesSentSinceMiss < P.ADH_002.nudges_after_miss
        return EngineResult(send, if (!send) emptyList() else listOf(Decision(DecisionKind.WORKLOAD_FLAG, listOf(RuleIds.ADH_002), ReasonKey.WELCOME_BACK,
            inputs = mapOf("nudgesSent" to nudgesSentSinceMiss), outputs = mapOf("nudge" to true))))
    }
}

/** ADH-005 if-then backups. */
enum class Backup { EXPRESS_SESSION, NEXT_FREE_DAY }

/** When the user plans to train on a day: a time and an optional "after what" cue. */
data class DayIntention(val weekday: Int, val minuteOfDay: Int? = null, val afterCue: String? = null)

/** ADH-005: the optional planning prompt (≤ 30 s, stored on the phone only, never nags). */
data class PlanningPrompt(val days: List<DayIntention>, val backup: Backup? = null)

object Planning {
    val maxSeconds: Int get() = P.ADH_005.max_seconds
    val backups: List<Backup> get() = P.ADH_005.backup_options.map { Backup.valueOf(it.uppercase()) }

    /**
     * Suggest this week's intentions: the planned training days, keeping last week's time and cue for the same weekday
     * (same weekdays by default); the backup carries over.
     */
    fun suggest(trainingDays: List<Int>, last: PlanningPrompt?): EngineResult<PlanningPrompt> {
        val byDay = last?.days?.associateBy { it.weekday }.orEmpty()
        val p = PlanningPrompt(trainingDays.sorted().map { d -> byDay[d]?.copy(weekday = d) ?: DayIntention(d) }, last?.backup)
        return EngineResult(p, listOf(Decision(DecisionKind.VOLUME_CHANGE, listOf(RuleIds.ADH_005), ReasonKey.PLANNING_PROMPT,
            inputs = mapOf("days" to trainingDays.sorted(), "hadLastWeek" to (last != null)), outputs = mapOf("kept" to p.days.count { it.minuteOfDay != null }))))
    }

    /** What the backup means today: short on time → the express session (ADH-004); missed day → the next free day (REG-002). */
    fun backupFor(prompt: PlanningPrompt, shortOnTime: Boolean, missedPlannedDay: Boolean): Backup? = when {
        prompt.backup == Backup.EXPRESS_SESSION && shortOnTime -> Backup.EXPRESS_SESSION
        prompt.backup == Backup.NEXT_FREE_DAY && missedPlannedDay -> Backup.NEXT_FREE_DAY
        else -> null
    }
}

/**
 * ADH-004 1.0.1: how habit messages talk about time — a range, never a fixed day count (review R3-14). "About two months for many
 * people" comes from the median (66 days); the spread runs from a few weeks to most of a year (Singh 2024: 4–335 days), exercise often
 * takes longer than simpler habits, and a missed day doesn't undo it (Lally 2010, Gardner 2012).
 */
data class HabitExpectation(val aboutMonths: Int, val varies: Boolean, val rangeText: String, val exerciseOftenLonger: Boolean, val missedDayUndoes: Boolean)

/** ADH-004: the express session length and the habit expectation used in messages. */
object Express {
    val minutes: IntRange get() = P.ADH_004.express_minutes[0]..P.ADH_004.express_minutes[1]
    val habit: HabitExpectation get() = HabitExpectation(aboutMonths = Math.round(P.ADH_004.habit_median_days / 30.0).toInt(), varies = true,
        rangeText = "from a few weeks to most of a year", exerciseOftenLonger = true, missedDayUndoes = false)
}

/** LOAD-002: when to ask for session RPE. */
object SrpePrompt {
    enum class Ask { NOW, WAIT, LATE_PROMPT, NEVER }

    /**
     * @param minutesSinceLastHardEffort time since the last hard set or interval
     * @param askedBefore the summary prompt was already shown and skipped
     * @param hoursSinceSession time since the session ended
     */
    fun decide(minutesSinceLastHardEffort: Double, askedBefore: Boolean, hoursSinceSession: Double): EngineResult<Ask> {
        val a = when {
            hoursSinceSession > P.LOAD_002.late_prompt_hours -> Ask.NEVER
            askedBefore -> Ask.LATE_PROMPT
            minutesSinceLastHardEffort >= P.LOAD_002.min_minutes_after -> Ask.NOW
            else -> Ask.WAIT
        }
        return EngineResult(a, listOf(Decision(DecisionKind.WORKLOAD_FLAG, listOf(RuleIds.LOAD_002), ReasonKey.SRPE_PROMPT,
            inputs = mapOf("minutes" to minutesSinceLastHardEffort, "askedBefore" to askedBefore, "hours" to hoursSinceSession), outputs = mapOf("ask" to a.name))))
    }
}
