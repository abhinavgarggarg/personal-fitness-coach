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

enum class Achievement { FIRST_SESSION, CALIBRATION_DONE, PERSONAL_RECORD, BLOCK_COMPLETED, SESSIONS_10, SESSIONS_25, SESSIONS_50, SESSIONS_100, WHO_FLOOR_WEEK }

data class AchievementInput(
    val sessionsCompleted: Int,
    val calibrationDone: Boolean,
    val newRecords: Int,
    val blocksCompleted: Int,
    val whoFloorMetThisWeek: Boolean,
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
        return out
    }
}

/** ADH-004: the express session length and the habit expectation used in messages. */
object Express {
    val minutes: IntRange get() = P.ADH_004.express_minutes[0]..P.ADH_004.express_minutes[1]
    val habitMedianDays: Int get() = P.ADH_004.habit_median_days
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
