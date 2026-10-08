package com.personalfitnesscoach.engine.safety

import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds

enum class PainKind { MUSCLE_BURN, DOMS, JOINT_OR_TENDON }

/** One "Something hurts" report. Region is the joint area on the body map. */
data class PainReport(
    val region: Joint,
    val kind: PainKind,
    val rating: Int,
    val worsening: Boolean = false,
    val descriptors: Set<String> = emptySet(),
    val affectsWholeBodyMovement: Boolean = false,
) {
    init { require(rating in 0..10) { "pain rating is 0–10" } }
}

enum class PainAction { CONTINUE, CONTINUE_CAUTION, STOP_EXERCISE, STOP_REGION }

data class PainOutcome(
    val action: PainAction,
    /** Load multiplier range for "continue with caution" (0.8–0.9), else null. */
    val loadFactor: ClosedFloatingPointRange<Double>? = null,
    /** Highest joint stress allowed on the region for substitutes / the rest of the session. 0 = no loading. */
    val regionMaxStress: Int? = null,
    val regionConservative: Boolean = false,
    val endSession: Boolean = false,
    val suggestProfessional: Boolean = false,
)

/** Persistent-pain state of one region (SAF-004). */
data class RegionHistory(
    val region: Joint,
    /** Sessions in which this region was reported painful (joint/tendon, rating ≥ 1). */
    val painfulSessions: Int,
    /** Days between the first and the latest pain report for this region. */
    val daysSpan: Int,
    /** Pain-free sessions in a row since the last report. */
    val consecutivePainFree: Int,
    /** Whole weeks since the region left conservative mode (after the pain-free sessions). */
    val weeksSinceExit: Int = 0,
    /** The region was put into conservative mode by SAF-003 at some point. */
    val conservativeFlag: Boolean = false,
)

data class RegionConstraint(
    val region: Joint,
    val maxStress: Int?,
    val minRir: Int?,
    val noFailure: Boolean,
    val noJumping: Boolean,
    val suggestProfessional: Boolean,
)

/** Pain is a separate gate from readiness (SAF-003, SAF-004). */
object PainGate {
    fun assess(r: PainReport): EngineResult<PainOutcome> {
        val severeDescriptor = r.descriptors.any { it in P.SAF_003.stop_region_descriptors }
        val outcome = when {
            // Sharp pain, swelling, instability, locking, numbness/tingling or pain after a fall stop the
            // region whatever the user tapped as the pain type (SAF-003).
            severeDescriptor -> PainOutcome(PainAction.STOP_REGION, regionMaxStress = 0, regionConservative = true,
                endSession = r.affectsWholeBodyMovement, suggestProfessional = true)
            r.kind != PainKind.JOINT_OR_TENDON -> PainOutcome(PainAction.CONTINUE)
            r.rating >= P.SAF_003.stop_region_min_rating ->
                PainOutcome(PainAction.STOP_REGION, regionMaxStress = 0, regionConservative = true,
                    endSession = r.affectsWholeBodyMovement, suggestProfessional = true)
            r.rating >= P.SAF_003.stop_exercise_min_rating || r.worsening ->
                PainOutcome(PainAction.STOP_EXERCISE, regionMaxStress = P.SAF_003.substitute_max_joint_stress, regionConservative = true)
            r.rating == 0 -> PainOutcome(PainAction.CONTINUE)
            else -> {
                val (lo, hi) = P.SAF_003.continue_load_reduction_pct.let { it[0] to it[1] }
                PainOutcome(PainAction.CONTINUE_CAUTION, loadFactor = (1.0 - hi / 100.0)..(1.0 - lo / 100.0))
            }
        }
        val reason = when (outcome.action) {
            PainAction.CONTINUE -> null
            PainAction.CONTINUE_CAUTION -> ReasonKey.PAIN_CONTINUE_CAUTION
            PainAction.STOP_EXERCISE -> ReasonKey.PAIN_STOP_EXERCISE
            PainAction.STOP_REGION -> ReasonKey.PAIN_STOP_REGION
        }
        val d = if (reason == null) emptyList() else listOf(Decision(DecisionKind.SAFETY, listOf(RuleIds.SAF_003), reason,
            inputs = mapOf("region" to r.region, "kind" to r.kind, "rating" to r.rating, "worsening" to r.worsening, "descriptors" to r.descriptors.sorted()),
            outputs = mapOf("action" to outcome.action, "maxStress" to outcome.regionMaxStress, "endSession" to outcome.endSession)))
        return EngineResult(outcome, d)
    }

    /**
     * SAF-004: a region painful in ≥2 sessions or for >7 days stays conservative (joint stress ≤1, RIR ≥3,
     * no failure, no jumping) until 2 pain-free sessions in a row, then steps up one stress level per week.
     * Returns null when the region needs no constraint.
     */
    fun regionConstraint(h: RegionHistory): EngineResult<RegionConstraint?> {
        val persistent = h.painfulSessions >= P.SAF_004.sessions_threshold || h.daysSpan > P.SAF_004.days_threshold
        if (!persistent && !h.conservativeFlag) return EngineResult(null)
        val exited = h.consecutivePainFree >= P.SAF_004.pain_free_sessions_to_exit
        val base = P.SAF_004.region_max_joint_stress
        val c = if (!exited) {
            RegionConstraint(h.region, base, P.SAF_004.region_min_rir, noFailure = true, noJumping = true, suggestProfessional = persistent)
        } else {
            val stress = base + P.SAF_004.stress_step_up_per_week * h.weeksSinceExit
            if (stress >= 4) return EngineResult(null)
            RegionConstraint(h.region, stress, null, noFailure = false, noJumping = stress < 2, suggestProfessional = false)
        }
        val reason = if (c.suggestProfessional) ReasonKey.PAIN_SEE_PROFESSIONAL else ReasonKey.REGION_CONSERVATIVE
        return EngineResult(c, listOf(Decision(DecisionKind.SAFETY, listOf(RuleIds.SAF_004), reason,
            inputs = mapOf("region" to h.region, "sessions" to h.painfulSessions, "days" to h.daysSpan, "painFree" to h.consecutivePainFree, "weeksSinceExit" to h.weeksSinceExit),
            outputs = mapOf("maxStress" to c.maxStress, "minRir" to c.minRir))))
    }
}
