package com.personalfitnesscoach.engine.progression

import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds

/**
 * How a session after a break is scaled. Factors apply to the pre-break working loads and
 * planned sets; `tierCap` is the most demanding tier allowed (null = no cap).
 */
data class ReturnPlan(
    val loadFactor: Double = 1.0,
    val setsFactor: Double = 1.0,
    val tierCap: Tier? = null,
    val hiitAllowed: Boolean = true,
    val recalibrate: Boolean = false,
    /** True while a return ramp is still running. */
    val inReturn: Boolean = false,
)

/** Missed sessions, layoffs and illness (REG-001 to REG-005). */
object ReturnToTraining {
    /** REG-001: three failed exposures on one lift within 3 weeks → variation or −10% and rebuild. */
    fun failedTargets(failureWeeks: List<Int>, currentWeek: Int): EngineResult<Boolean> {
        val recent = failureWeeks.count { currentWeek - it in 0 until P.REG_001.window_weeks }
        val review = recent >= P.REG_001.failures
        return EngineResult(review, if (!review) emptyList() else listOf(Decision(DecisionKind.LOAD_CHANGE,
            listOf(RuleIds.REG_001, RuleIds.DEL_001), ReasonKey.FAILED_TARGETS_REVIEW,
            inputs = mapOf("failuresInWindow" to recent), outputs = mapOf("reducePct" to P.REG_001.reduce_pct))))
    }

    /**
     * Plan for the session that follows a break.
     * @param daysOff days since the last completed session
     * @param consecutiveMissed planned sessions missed in a row
     * @param weeksSinceReturn 0 for the first week back, 1 for the second, …
     * @param sessionsSinceReturn sessions completed since coming back (REG-003 applies to the first only)
     */
    fun afterBreak(
        daysOff: Int,
        consecutiveMissed: Int,
        weeksSinceReturn: Int = 0,
        sessionsSinceReturn: Int = 0,
        age: Int? = null,
        flaggedScreen: Boolean = false,
    ): EngineResult<ReturnPlan> {
        val inputs = mapOf("daysOff" to daysOff, "missed" to consecutiveMissed, "week" to weeksSinceReturn, "age" to age, "flagged" to flaggedScreen)
        if (daysOff >= bandStart(0)) {
            var band = bandIndex(daysOff)
            if ((age != null && age >= 60) || flaggedScreen) band = minOf(band + P.REG_004.older_or_flagged_shift, BANDS.size - 1)
            val rules = listOf(RuleIds.REG_004) + if (age != null && age >= 60) listOf(RuleIds.AGE_001) else emptyList()
            val plan = layoffPlan(band, weeksSinceReturn)
            val reason = if (plan.recalibrate) ReasonKey.RECALIBRATE else ReasonKey.RETURN_RAMP
            return EngineResult(plan, listOf(Decision(DecisionKind.RETURN_TO_TRAINING, rules, reason, inputs,
                mapOf("band" to band, "load" to plan.loadFactor, "sets" to plan.setsFactor, "hiit" to plan.hiitAllowed))))
        }
        val reg3Days = P.REG_003.days
        if (daysOff >= reg3Days[0] || consecutiveMissed >= 3) {
            if (sessionsSinceReturn > 0) return EngineResult(ReturnPlan())
            val plan = ReturnPlan(loadFactor = P.REG_003.loads_pct / 100.0, tierCap = Tier.valueOf(P.REG_003.tier), inReturn = true)
            return EngineResult(plan, listOf(Decision(DecisionKind.RETURN_TO_TRAINING, listOf(RuleIds.REG_003), ReasonKey.RETURN_MODIFIED, inputs,
                mapOf("load" to plan.loadFactor, "tier" to plan.tierCap))))
        }
        if (consecutiveMissed >= 1) {
            // REG-002: the plan shifts; never two sessions at once.
            return EngineResult(ReturnPlan(), listOf(Decision(DecisionKind.RETURN_TO_TRAINING, listOf(RuleIds.REG_002), ReasonKey.MISSED_SHIFTED, inputs)))
        }
        return EngineResult(ReturnPlan())
    }

    /**
     * REG-005 return after illness. Training waits until symptom-free for 24 h without fever
     * medicine; then LIGHT → MODIFIED → normal, HIIT after 2 normal sessions. A mild head cold
     * without fever lets the user choose LIGHT or MODIFIED.
     */
    fun afterIllness(
        symptomFreeHours: Int,
        usingFeverMedicine: Boolean,
        sessionsSinceReturn: Int,
        mildHeadColdOnly: Boolean = false,
        /** Days without training because of the illness; ≥ 7 also applies REG-003/REG-004 by days off. */
        illnessDays: Int = 0,
        weeksSinceReturn: Int = 0,
        age: Int? = null,
        flaggedScreen: Boolean = false,
    ): EngineResult<ReturnPlan> {
        val base = illnessOnly(symptomFreeHours, usingFeverMedicine, sessionsSinceReturn, mildHeadColdOnly)
        if (illnessDays < 7 || base.value.setsFactor == 0.0) return base
        val layoff = afterBreak(illnessDays, 0, weeksSinceReturn, sessionsSinceReturn, age, flaggedScreen)
        val a = base.value
        val b = layoff.value
        val tier = when {
            a.tierCap == null -> b.tierCap
            b.tierCap == null -> a.tierCap
            else -> Tier.min(a.tierCap, b.tierCap)
        }
        val merged = ReturnPlan(
            loadFactor = minOf(a.loadFactor, b.loadFactor), setsFactor = minOf(a.setsFactor, b.setsFactor), tierCap = tier,
            hiitAllowed = a.hiitAllowed && b.hiitAllowed, recalibrate = b.recalibrate, inReturn = a.inReturn || b.inReturn,
        )
        return EngineResult(merged, base.decisions + layoff.decisions)
    }

    private fun illnessOnly(
        symptomFreeHours: Int,
        usingFeverMedicine: Boolean,
        sessionsSinceReturn: Int,
        mildHeadColdOnly: Boolean,
    ): EngineResult<ReturnPlan> {
        val inputs = mapOf("symptomFreeHours" to symptomFreeHours, "feverMeds" to usingFeverMedicine, "sessions" to sessionsSinceReturn, "mildCold" to mildHeadColdOnly)
        fun d(reason: ReasonKey, out: Map<String, Any?>) = listOf(Decision(DecisionKind.RETURN_TO_TRAINING, listOf(RuleIds.REG_005), reason, inputs, out))
        if (mildHeadColdOnly && !usingFeverMedicine) {
            val plan = ReturnPlan(tierCap = Tier.MODIFIED, hiitAllowed = false, inReturn = true)
            return EngineResult(plan, d(ReasonKey.ILLNESS_MILD_CHOICE, mapOf("tierCap" to Tier.MODIFIED)))
        }
        if (usingFeverMedicine || symptomFreeHours < P.REG_005.symptom_free_hours[0]) {
            val plan = ReturnPlan(tierCap = Tier.RECOVERY, hiitAllowed = false, inReturn = true, setsFactor = 0.0)
            return EngineResult(plan, d(ReasonKey.ILLNESS_REST, mapOf("train" to false)))
        }
        val normalDone = maxOf(0, sessionsSinceReturn - 2)
        val tier = when (sessionsSinceReturn) {
            0 -> Tier.LIGHT
            1 -> Tier.MODIFIED
            else -> null
        }
        val hiit = normalDone >= P.REG_005.hiit_after_normal_sessions
        val plan = ReturnPlan(tierCap = tier, hiitAllowed = hiit, inReturn = tier != null || !hiit)
        return EngineResult(plan, d(ReasonKey.ILLNESS_RETURN, mapOf("tierCap" to tier, "hiit" to hiit)))
    }

    // ---- REG-004 bands, read from the registry ------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    private val BANDS: List<Map<String, Any?>> = P.REG_004.bands.map { it as Map<String, Any?> }

    private fun num(x: Any?): Double = (x as Number).toDouble()

    @Suppress("UNCHECKED_CAST")
    private fun bandStart(i: Int): Int = ((BANDS[i]["days"] as List<Any?>)[0] as Number).toInt()

    private fun bandIndex(daysOff: Int): Int = BANDS.indices.last { daysOff >= bandStart(it) }

    @Suppress("UNCHECKED_CAST")
    private fun layoffPlan(band: Int, week: Int): ReturnPlan {
        val b = BANDS[band]
        if (b["action"] == "recalibrate_and_restart_block") {
            return ReturnPlan(loadFactor = 1.0, setsFactor = 1.0, hiitAllowed = false, recalibrate = true, inReturn = true)
        }
        val hiitPause = (b["hiit_pause_weeks"] as Number).toInt()
        val hiit = week >= hiitPause
        val weeks = b["weeks"] as List<Map<String, Any?>>?
        if (weeks != null) {
            if (week >= weeks.size) return ReturnPlan(hiitAllowed = hiit)
            val w = weeks[week]
            return ReturnPlan(num(w["load"]) / 100.0, num(w["sets"]) / 100.0, hiitAllowed = hiit, inReturn = true)
        }
        val w1 = b["week1"] as Map<String, Any?>
        val step = b["weekly_step"] as Map<String, Any?>
        val load = minOf(100.0, num(w1["load"]) + week * num(step["load"]))
        val sets = minOf(100.0, num(w1["sets"]) + week * num(step["sets"]))
        return ReturnPlan(load / 100.0, sets / 100.0, hiitAllowed = hiit, inReturn = load < 100.0 || sets < 100.0 || !hiit)
    }
}
