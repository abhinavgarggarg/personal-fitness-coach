package com.personalfitnesscoach.engine.safety

import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds

/** Answers to the eight onboarding questions (Phase 1 section "Pre-participation screening"). */
data class ScreeningAnswers(
    /** Q1 heart condition or uncontrolled blood pressure. */
    val heartOrBloodPressure: Boolean,
    /** Q2 diabetes, kidney or lung disease. */
    val metabolicRenalPulmonary: Boolean,
    /** Q3 chest pain, fainting, dizziness or unusual breathlessness in the last 12 months. */
    val symptoms: Boolean,
    /** Q4 palpitations or a known heart-rhythm problem. */
    val palpitations: Boolean,
    /** Q5 told to limit exercise, or pregnant / recently postpartum. */
    val limitOrPregnancy: Boolean,
    /** Q6 bone, joint or soft-tissue problem. */
    val musculoskeletal: Boolean,
    /** Q7 medicine for a long-term condition. */
    val longTermMedication: Boolean,
    /** Q8 exercised 3+ days a week for 30+ minutes for the last 3 months. */
    val regularlyActive: Boolean,
)

enum class ScreeningMode { STANDARD, MODERATE_ONLY, CONSERVATIVE }

data class ScreeningResult(
    val mode: ScreeningMode,
    val clearanceRecommended: Boolean,
    val clinicianGuidance: Boolean,
    /** Q6/Q7: limitation tags to be collected and a coach note shown. */
    val limitationNote: Boolean,
) {
    val hiitAllowed get() = mode == ScreeningMode.STANDARD
    val zone3Allowed get() = mode == ScreeningMode.STANDARD
    /** Minimum RIR on every working set, or null when not restricted. */
    val minRir: Int? get() = if (mode == ScreeningMode.CONSERVATIVE) P.SAF_001.conservative_mode.min_rir else null
    /** Training to failure is vigorous work: only in standard mode (decision D-039). */
    val failureAllowed get() = mode == ScreeningMode.STANDARD
}

/** SAF-001 pre-participation screening (ACSM 2015 logic, original wording). */
object Screening {
    fun evaluate(a: ScreeningAnswers, clearanceConfirmed: Boolean = false): EngineResult<ScreeningResult> {
        val disease = a.heartOrBloodPressure || a.metabolicRenalPulmonary
        val symptoms = a.symptoms || a.palpitations
        var mode = ScreeningMode.STANDARD
        var clearance = false
        var clinician = false
        if (symptoms || (disease && !a.regularlyActive)) { mode = ScreeningMode.CONSERVATIVE; clearance = true }
        else if (disease) { mode = ScreeningMode.MODERATE_ONLY; clearance = true }
        // The user confirms medical clearance → the Q1–Q4 restrictions lift; the record stays.
        if (clearanceConfirmed) mode = ScreeningMode.STANDARD
        // Q5 (told to limit exercise, pregnancy, postpartum): clinician guidance and conservative mode stay
        // until a re-screen answers Q5 "no" — clearance alone does not lift it (pregnancy programming is outside V1).
        if (a.limitOrPregnancy) { mode = ScreeningMode.CONSERVATIVE; clinician = true }
        val result = ScreeningResult(mode, clearance, clinician, a.musculoskeletal || a.longTermMedication)
        val reason = when (mode) {
            ScreeningMode.STANDARD -> ReasonKey.SCREEN_STANDARD
            ScreeningMode.MODERATE_ONLY -> ReasonKey.SCREEN_MODERATE_ONLY
            ScreeningMode.CONSERVATIVE -> ReasonKey.SCREEN_CONSERVATIVE
        }
        return EngineResult(result, listOf(Decision(DecisionKind.SCREENING, listOf(RuleIds.SAF_001), reason,
            inputs = mapOf("answers" to a, "clearanceConfirmed" to clearanceConfirmed),
            outputs = mapOf("mode" to mode, "clearance" to clearance, "clinician" to clinician, "limitationNote" to result.limitationNote))))
    }

    fun rescreenDue(monthsSinceLastScreen: Int, newConditionReported: Boolean = false): Boolean =
        newConditionReported || monthsSinceLastScreen >= P.SAF_001.rescreen_months
}

/** A SAFETY STOP (SAF-002): the session ends and is never turned into a lighter workout. */
data class SafetyStop(
    val symptoms: Set<String>,
    val emergencyNumber: String,
    /** Training resumes only after the user confirms resolution or a medical review. */
    val resumeRequiresConfirmation: Boolean = true,
    val firstSessionAfter: Tier = Tier.valueOf(P.SAF_002.first_session_after),
)

/** Red flags (SAF-002) and the illness gate (SAF-007). */
object RedFlags {
    val SYMPTOMS: Set<String> = P.SAF_002.symptoms.toSet()
    val SYSTEMIC: Set<String> = P.SAF_007.systemic_symptoms.toSet()

    fun check(reported: Set<String>, emergencyNumber: String = P.SAF_002.emergency_number_default): EngineResult<SafetyStop?> {
        val hits = reported intersect SYMPTOMS
        if (hits.isEmpty()) return EngineResult(null)
        return EngineResult(SafetyStop(hits, emergencyNumber), listOf(Decision(DecisionKind.SAFETY, listOf(RuleIds.SAF_002),
            ReasonKey.SAFETY_STOP_RED_FLAG, inputs = mapOf("symptoms" to hits.sorted()), outputs = mapOf("emergency" to emergencyNumber))))
    }

    /** After a stop: null = no training yet; otherwise the most demanding tier allowed for the first session back. */
    fun tierAfterStop(confirmedResolvedOrReviewed: Boolean): Tier? =
        if (confirmedResolvedOrReviewed) Tier.valueOf(P.SAF_002.first_session_after) else null

    /** SAF-007: systemic symptoms lock the day to RECOVERY (rest). Returns the tier cap, or null if none. */
    fun illnessGate(reported: Set<String>): EngineResult<Tier?> {
        val hits = reported intersect SYSTEMIC
        if (hits.isEmpty()) return EngineResult(null)
        val tier = Tier.valueOf(P.SAF_007.tier)
        return EngineResult(tier, listOf(Decision(DecisionKind.SAFETY, listOf(RuleIds.SAF_007, RuleIds.REG_005), ReasonKey.ILLNESS_REST,
            inputs = mapOf("symptoms" to hits.sorted()), outputs = mapOf("tier" to tier))))
    }
}
