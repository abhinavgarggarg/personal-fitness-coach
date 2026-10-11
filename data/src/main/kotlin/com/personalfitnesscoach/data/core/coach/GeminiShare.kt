package com.personalfitnesscoach.data.core.coach

import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.model.ReadinessRecord
import com.personalfitnesscoach.data.core.model.dayKey
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.program.DayTemplate

/**
 * What "Ask Gemini" hands to the phone's Gemini app through Android's share sheet (D-045): today's plan, the engine's reasons and the
 * user's question — nothing else. No name, age or birth date, no health answers or conditions, no weight, waist or pain details
 * (Phase 2 section 15, "what the model sees"); only whether gentle mode is on. The user sees the full text before sharing, the answer
 * appears in Gemini under their own account, and it never changes the plan (the engine decides; the app has no internet permission).
 */
data class ShareContent(
    val template: DayTemplate?,
    val minutes: Int?,
    val tier: Tier?,
    /** Library exercise IDs in today's plan, in order (names are shown, never loads or personal records). */
    val exercises: List<String>,
    /** Reason keys of today's non-routine decisions (rendered as their plain lines). */
    val reasons: List<String>,
    val gentleMode: Boolean,
    val question: String,
)

class GeminiShare(private val d: PfcData) {
    /** The package of Google's Gemini app (the share goes to it when installed, otherwise to the share sheet). */
    companion object {
        const val GEMINI_PACKAGE = "com.google.android.apps.bard"
        const val MAX_QUESTION = 500

        /**
         * Questions that are not passed on (SAF-009, review R6-06): symptoms, medicines, medical questions, pain and illness go to a
         * professional (or the app's own pain and illness checks), not to a general chatbot. Diet and supplement questions may be asked.
         */
        val NOT_SHARED: Set<OutOfScope> = setOf(OutOfScope.URGENT_SYMPTOMS, OutOfScope.MEDICATION, OutOfScope.MEDICAL, OutOfScope.PAIN, OutOfScope.ILLNESS)

        fun blocked(question: String): OutOfScope? = Coach.outOfScope(question)?.takeIf { it in NOT_SHARED }
    }

    suspend fun content(question: String): ShareContent = d.store.transaction {
        val today = d.clock.today()
        val t = d.planToday()
        // A day the pain gate or a warning sign stopped has no session to describe (and its reason is not shared).
        val stopped = d.trainingStopped(today)
        val day = if (stopped) null else t?.next
        val ci = d.docs.get(ReadinessRecord, dayKey(today))
        val reasons = d.decisions.forDay(today).sortedBy { it.seq }.map { it.reason }.filter { it in SHAREABLE_REASONS }
        ShareContent(
            template = day?.template,
            // The minutes chosen at today's check-in, else the usual length.
            minutes = day?.let { ci?.minutesAvailable ?: t?.user?.profile?.sessionMinutes },
            tier = if (stopped) null else ci?.tier,
            exercises = day?.slots?.map { it.exercise.id }?.distinct().orEmpty(),
            reasons = if (stopped) emptyList() else reasons.distinct().take(6),
            gentleMode = t?.user?.flaggedScreen == true,
            // Checked here too, not only on the screen: a question that is not shared is left out.
            question = question.trim().take(MAX_QUESTION).takeIf { blocked(it) == null } ?: "",
        )
    }
}

/**
 * The only reasons that may be shared (D-045, minimal data): readiness level, time, deloads, returns after a break, progression and
 * session shape. Anything that could reveal a health condition, a symptom, pain, illness, screening answers or body measurements is
 * left out — an allow-list, so a reason added later is not shared until it is reviewed. Its effect still shows in the plan itself.
 */
val SHAREABLE_REASONS: Set<String> = setOf(
    "TIER_CAPPED_BY_SLEEP", "TIER_CAPPED_BY_ITEM", "TIER_FLOOR_RAW_LOW", "TIER_STEPPED_DOWN_FATIGUE", "TIER_CHOSEN_BY_USER",
    "LOAD_UP_TARGET_MET", "LOAD_HOLD_EFFORT_HIGH", "LOAD_HOLD_TECHNIQUE", "LOAD_REDUCE_EFFORT_HIGH", "LOAD_REDUCE_REPEATED", "REPS_UP", "RANGE_EXTENDED",
    "LOAD_JUMP_REPS_RESET", "LOAD_HOLD_RECOVERY", "LOAD_HOLD_TOP_OF_RANGE", "LOAD_CAPPED_WEEKLY",
    "ACCESSORIES_CUT_FATIGUE", "WORKLOAD_SPIKE_FLAG", "MONOTONY_FLAG", "PLAN_CAPPED_BY_LOAD",
    "DELOAD_NOW", "LIGHTER_WEEK", "DELOAD_BLOCK_END", "PIVOT_WEEK", "LIGHTER_WEEK_OFFER", "DELOAD_RESUME",
    "MISSED_SHIFTED", "RETURN_MODIFIED", "RETURN_RAMP", "RECALIBRATE",
    "WARMUP_COMPRESSED", "TIME_COMPRESSED", "TIME_EXTENDED", "TIME_EXPRESS_OFFERED", "TIME_NOT_FITTABLE", "EXPRESS_SESSION",
    "CALIBRATION_DONE", "BLOCK_CLOCK_PAUSED", "BLOCK_RESTARTED", "WEEK_DAYS_REDUCED", "WELCOME_BACK", "AWAY_FROM_GYM",
)
