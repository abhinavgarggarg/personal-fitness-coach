package com.personalfitnesscoach.app.ui.text

import com.personalfitnesscoach.R
import com.personalfitnesscoach.app.flow.ExperienceBand
import com.personalfitnesscoach.app.flow.GymPreset
import com.personalfitnesscoach.app.flow.ScreeningQuestion
import com.personalfitnesscoach.data.core.backup.BackupProblem
import com.personalfitnesscoach.data.core.onboarding.OnboardingProblem
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.model.Zone
import com.personalfitnesscoach.engine.conditioning.BlockKind
import com.personalfitnesscoach.engine.program.DayTemplate
import com.personalfitnesscoach.engine.program.Goal
import com.personalfitnesscoach.engine.safety.ClearanceScope
import com.personalfitnesscoach.engine.safety.PainAction
import com.personalfitnesscoach.engine.safety.PainKind

/**
 * Engine and flow values → string resources (NFR-16: the engine and the flow hold keys, the screens hold words). Lists of IDs that come
 * from the registry (red flags, illness symptoms, pain descriptors) map by name; an ID without a string falls back to a readable form of
 * the ID, so a registry update never crashes a screen (tools/check_strings.py reports the gap).
 */
object Labels {
    fun tier(t: Tier): Int = when (t) {
        Tier.FULL -> R.string.tier_full
        Tier.MODIFIED -> R.string.tier_modified
        Tier.LIGHT -> R.string.tier_light
        Tier.RECOVERY -> R.string.tier_recovery
    }

    fun tierDescription(t: Tier): Int = when (t) {
        Tier.FULL -> R.string.tier_desc_full
        Tier.MODIFIED -> R.string.tier_desc_modified
        Tier.LIGHT -> R.string.tier_desc_light
        Tier.RECOVERY -> R.string.tier_desc_recovery
    }

    fun template(t: DayTemplate): Int = when (t) {
        DayTemplate.FB_A -> R.string.template_fb_a
        DayTemplate.FB_B -> R.string.template_fb_b
        DayTemplate.FB_C -> R.string.template_fb_c
        DayTemplate.UPPER_H -> R.string.template_upper_h
        DayTemplate.LOWER_H -> R.string.template_lower_h
        DayTemplate.UPPER_M -> R.string.template_upper_m
        DayTemplate.LOWER_M -> R.string.template_lower_m
        DayTemplate.COND_CORE -> R.string.template_cond_core
        DayTemplate.COND -> R.string.template_cond
        DayTemplate.EASY_AEROBIC_MOBILITY -> R.string.template_easy_aerobic_mobility
    }

    fun goal(g: Goal): Int = when (g) {
        Goal.FAT_LOSS -> R.string.goal_fat_loss
        Goal.STRENGTH -> R.string.goal_strength
        Goal.MUSCLE -> R.string.goal_muscle
        Goal.CARDIO -> R.string.goal_cardio
        Goal.POWER -> R.string.goal_power
        Goal.MOBILITY -> R.string.goal_mobility
        Goal.BODY_COMPOSITION -> R.string.goal_body_composition
        Goal.GENERAL_FITNESS -> R.string.goal_general_fitness
    }

    fun joint(j: Joint): Int = when (j) {
        Joint.SHOULDER -> R.string.joint_shoulder
        Joint.ELBOW -> R.string.joint_elbow
        Joint.WRIST -> R.string.joint_wrist
        Joint.SPINE -> R.string.joint_spine
        Joint.HIP -> R.string.joint_hip
        Joint.KNEE -> R.string.joint_knee
        Joint.ANKLE -> R.string.joint_ankle
    }

    fun painKind(k: PainKind): Int = when (k) {
        PainKind.MUSCLE_BURN -> R.string.pk_muscle_burn
        PainKind.DOMS -> R.string.pk_doms
        PainKind.JOINT_OR_TENDON -> R.string.pk_joint_or_tendon
    }

    fun painAction(a: PainAction): Int = when (a) {
        PainAction.CONTINUE -> R.string.pain_result_continue
        PainAction.CONTINUE_CAUTION -> R.string.pain_result_caution
        PainAction.STOP_EXERCISE -> R.string.pain_result_stop_exercise
        PainAction.STOP_REGION -> R.string.pain_result_stop_region
    }

    fun zone(z: Zone): Int = when (z) {
        Zone.Z1 -> R.string.zone_z1
        Zone.Z2 -> R.string.zone_z2
        Zone.Z3 -> R.string.zone_z3
        Zone.Z4 -> R.string.zone_z4
    }

    fun clearance(c: ClearanceScope): Int = when (c) {
        ClearanceScope.LIGHT_MODERATE -> R.string.clearance_light_moderate
        ClearanceScope.VIGOROUS -> R.string.clearance_vigorous
        ClearanceScope.INTERVALS -> R.string.clearance_intervals
    }

    fun block(k: BlockKind): Int = when (k) {
        BlockKind.CALIBRATE -> R.string.block_calibrate
        BlockKind.FOUNDATION -> R.string.block_foundation
        BlockKind.BUILD -> R.string.block_build
        BlockKind.STRENGTH -> R.string.block_strength
        BlockKind.CONDITIONING -> R.string.block_conditioning
        BlockKind.POWER -> R.string.block_power
        BlockKind.CONSOLIDATION -> R.string.block_consolidation
        BlockKind.REVIEW -> R.string.block_review
        BlockKind.DELOAD -> R.string.block_deload
    }

    fun screening(q: ScreeningQuestion): Int = when (q) {
        ScreeningQuestion.HEART_OR_BLOOD_PRESSURE -> R.string.screen_q1
        ScreeningQuestion.METABOLIC_RENAL_PULMONARY -> R.string.screen_q2
        ScreeningQuestion.SYMPTOMS -> R.string.screen_q3
        ScreeningQuestion.PALPITATIONS -> R.string.screen_q4
        ScreeningQuestion.LIMIT_OR_PREGNANCY -> R.string.screen_q5
        ScreeningQuestion.MUSCULOSKELETAL -> R.string.screen_q6
        ScreeningQuestion.LONG_TERM_MEDICATION -> R.string.screen_q7
        ScreeningQuestion.REGULARLY_ACTIVE -> R.string.screen_q8
    }

    fun experience(b: ExperienceBand): Int = when (b) {
        ExperienceBand.UNDER_6 -> R.string.exp_under_6
        ExperienceBand.SIX_TO_12 -> R.string.exp_6_12
        ExperienceBand.ONE_TO_3_YEARS -> R.string.exp_1_3
        ExperienceBand.OVER_3_YEARS -> R.string.exp_over_3
    }

    fun preset(p: GymPreset): Int = when (p) {
        GymPreset.FULL_GYM -> R.string.preset_full_gym
        GymPreset.DUMBBELLS_AND_MACHINES -> R.string.preset_dumbbells_and_machines
        GymPreset.HOME_DUMBBELLS -> R.string.preset_home_dumbbells
        GymPreset.BODYWEIGHT_ONLY -> R.string.preset_bodyweight_only
    }

    /** EXP-001 key lifts, as the onboarding question names them. */
    fun keyLift(id: String): Int? = when (id) {
        "squat" -> R.string.lift_squat
        "hinge" -> R.string.lift_hinge
        "press" -> R.string.lift_press
        "row" -> R.string.lift_row
        else -> null
    }

    fun onboardingProblem(p: OnboardingProblem): Int = when (p) {
        OnboardingProblem.UNDER_18 -> R.string.problem_under_18
        OnboardingProblem.BIRTH_YEAR_IMPLAUSIBLE -> R.string.problem_birth_year
        OnboardingProblem.NO_DAYS -> R.string.problem_no_days
        OnboardingProblem.TOO_FEW_DAYS_AVAILABLE -> R.string.problem_too_few_days
        OnboardingProblem.NO_PRIORITY -> R.string.problem_no_priority
        OnboardingProblem.UNKNOWN_CONDITION -> R.string.onb_cond_pick_one
        OnboardingProblem.BLOCKED_GOAL -> R.string.problem_blocked_goal
    }

    fun backupProblem(p: BackupProblem): Int = when (p) {
        BackupProblem.NOT_A_BACKUP -> R.string.restore_refused_not_a_backup
        BackupProblem.NEWER_FORMAT, BackupProblem.NEWER_SCHEMA -> R.string.restore_refused_newer
        BackupProblem.PASSWORD_NEEDED, BackupProblem.WRONG_PASSWORD_OR_DAMAGED -> R.string.restore_password_wrong
        BackupProblem.DAMAGED -> R.string.restore_refused_damaged
        BackupProblem.INVALID_CONTENT -> R.string.restore_refused_invalid
    }

    /** Monday = 0 (Days.weekday). */
    fun weekday(wd: Int): Int = when (wd) {
        0 -> R.string.day_mon
        1 -> R.string.day_tue
        2 -> R.string.day_wed
        3 -> R.string.day_thu
        4 -> R.string.day_fri
        5 -> R.string.day_sat
        else -> R.string.day_sun
    }

    fun conditionGroup(g: String): Int = when (g) {
        "heart_and_circulation" -> R.string.cond_group_heart_and_circulation
        "metabolic" -> R.string.cond_group_metabolic
        "breathing" -> R.string.cond_group_breathing
        "joints" -> R.string.cond_group_joints
        "bones" -> R.string.cond_group_bones
        "pregnancy" -> R.string.cond_group_pregnancy
        "body_weight" -> R.string.cond_group_body_weight
        else -> R.string.cond_group_other
    }

    /** SAF-002 red flags (registry IDs). */
    val RED_FLAGS: Map<String, Int> = mapOf(
        "chest_pain_pressure_tightness" to R.string.rf_chest_pain_pressure_tightness,
        "fainting_or_near_fainting" to R.string.rf_fainting_or_near_fainting,
        "dizziness_lightheadedness" to R.string.rf_dizziness_lightheadedness,
        "unusual_breathlessness" to R.string.rf_unusual_breathlessness,
        "palpitations_irregular_heartbeat" to R.string.rf_palpitations_irregular_heartbeat,
        "sudden_severe_pain" to R.string.rf_sudden_severe_pain,
        "sudden_severe_headache" to R.string.rf_sudden_severe_headache,
        "one_sided_weakness_numbness" to R.string.rf_one_sided_weakness_numbness,
        "confusion_or_speech_trouble" to R.string.rf_confusion_or_speech_trouble,
    )

    /** SAF-007 systemic symptoms. */
    val ILLNESS: Map<String, Int> = mapOf(
        "fever" to R.string.ill_fever,
        "chills" to R.string.ill_chills,
        "body_aches" to R.string.ill_body_aches,
        "chest_symptoms" to R.string.ill_chest_symptoms,
        "vomiting" to R.string.ill_vomiting,
        "diarrhoea" to R.string.ill_diarrhoea,
    )

    /** SAF-003 descriptors that stop the region. */
    val PAIN_DESCRIPTORS: Map<String, Int> = mapOf(
        "sharp" to R.string.pd_sharp,
        "stabbing" to R.string.pd_stabbing,
        "swelling" to R.string.pd_swelling,
        "instability" to R.string.pd_instability,
        "locking" to R.string.pd_locking,
        "numbness" to R.string.pd_numbness,
        "tingling" to R.string.pd_tingling,
        "after_fall_or_impact" to R.string.pd_after_fall_or_impact,
    )

    /** SAF-010 sub-flags and clearance-always answers (snake_case keys from the condition table). */
    val SUB_FLAGS: Map<String, Int> = mapOf(
        "heart_failure" to R.string.subflag_heart_failure,
        "pacemaker_icd" to R.string.subflag_pacemaker_icd,
        "recent_breastbone_surgery" to R.string.subflag_recent_breastbone_surgery,
        "aortic_or_connective_tissue_or_cardiomyopathy" to R.string.subflag_aortic_or_connective_tissue_or_cardiomyopathy,
        "in_active_treatment" to R.string.subflag_in_active_treatment,
        "cancer_has_spread_to_the_bones" to R.string.subflag_cancer_has_spread_to_the_bones,
        "lymphoedema" to R.string.subflag_lymphoedema,
        "a_past_foot_ulcer_or_charcot_foot" to R.string.subflag_a_past_foot_ulcer_or_charcot_foot,
    )

    /** A readable form of an ID with no string (never shown for the IDs above). */
    fun pattern(p: com.personalfitnesscoach.engine.model.Pattern): Int = when (p) {
        com.personalfitnesscoach.engine.model.Pattern.SQUAT -> R.string.pat_squat
        com.personalfitnesscoach.engine.model.Pattern.HINGE -> R.string.pat_hinge
        com.personalfitnesscoach.engine.model.Pattern.LUNGE -> R.string.pat_lunge
        com.personalfitnesscoach.engine.model.Pattern.HORIZONTAL_PUSH -> R.string.pat_horizontal_push
        com.personalfitnesscoach.engine.model.Pattern.HORIZONTAL_PULL -> R.string.pat_horizontal_pull
        com.personalfitnesscoach.engine.model.Pattern.VERTICAL_PUSH -> R.string.pat_vertical_push
        com.personalfitnesscoach.engine.model.Pattern.VERTICAL_PULL -> R.string.pat_vertical_pull
        com.personalfitnesscoach.engine.model.Pattern.LOADED_CARRY -> R.string.pat_loaded_carry
        com.personalfitnesscoach.engine.model.Pattern.ROTATION -> R.string.pat_rotation
        com.personalfitnesscoach.engine.model.Pattern.ANTI_EXTENSION -> R.string.pat_anti_extension
        com.personalfitnesscoach.engine.model.Pattern.ANTI_ROTATION -> R.string.pat_anti_rotation
        com.personalfitnesscoach.engine.model.Pattern.ANTI_LATERAL_FLEXION -> R.string.pat_anti_lateral_flexion
        com.personalfitnesscoach.engine.model.Pattern.ISOLATION -> R.string.pat_isolation
    }

    fun muscle(m: com.personalfitnesscoach.engine.model.Muscle): Int = when (m) {
        com.personalfitnesscoach.engine.model.Muscle.CHEST -> R.string.mus_chest
        com.personalfitnesscoach.engine.model.Muscle.LATS -> R.string.mus_lats
        com.personalfitnesscoach.engine.model.Muscle.UPPER_BACK -> R.string.mus_upper_back
        com.personalfitnesscoach.engine.model.Muscle.FRONT_DELTS -> R.string.mus_front_delts
        com.personalfitnesscoach.engine.model.Muscle.SIDE_DELTS -> R.string.mus_side_delts
        com.personalfitnesscoach.engine.model.Muscle.REAR_DELTS -> R.string.mus_rear_delts
        com.personalfitnesscoach.engine.model.Muscle.BICEPS -> R.string.mus_biceps
        com.personalfitnesscoach.engine.model.Muscle.TRICEPS -> R.string.mus_triceps
        com.personalfitnesscoach.engine.model.Muscle.QUADS -> R.string.mus_quads
        com.personalfitnesscoach.engine.model.Muscle.HAMSTRINGS -> R.string.mus_hamstrings
        com.personalfitnesscoach.engine.model.Muscle.GLUTES -> R.string.mus_glutes
        com.personalfitnesscoach.engine.model.Muscle.CALVES -> R.string.mus_calves
        com.personalfitnesscoach.engine.model.Muscle.ADDUCTORS -> R.string.mus_adductors
        com.personalfitnesscoach.engine.model.Muscle.CORE -> R.string.mus_core
        com.personalfitnesscoach.engine.model.Muscle.FOREARMS -> R.string.mus_forearms
    }

    fun achievement(a: com.personalfitnesscoach.engine.program.Achievement): Int = when (a) {
        com.personalfitnesscoach.engine.program.Achievement.FIRST_SESSION -> R.string.ach_first_session
        com.personalfitnesscoach.engine.program.Achievement.CALIBRATION_DONE -> R.string.ach_calibration_done
        com.personalfitnesscoach.engine.program.Achievement.PERSONAL_RECORD -> R.string.ach_personal_record
        com.personalfitnesscoach.engine.program.Achievement.BLOCK_COMPLETED -> R.string.ach_block_completed
        com.personalfitnesscoach.engine.program.Achievement.SESSIONS_10 -> R.string.ach_sessions_10
        com.personalfitnesscoach.engine.program.Achievement.SESSIONS_25 -> R.string.ach_sessions_25
        com.personalfitnesscoach.engine.program.Achievement.SESSIONS_50 -> R.string.ach_sessions_50
        com.personalfitnesscoach.engine.program.Achievement.SESSIONS_100 -> R.string.ach_sessions_100
        com.personalfitnesscoach.engine.program.Achievement.WHO_FLOOR_WEEK -> R.string.ach_who_floor_week
        com.personalfitnesscoach.engine.program.Achievement.WELCOME_BACK -> R.string.ach_welcome_back
    }

    fun outcome(o: com.personalfitnesscoach.data.core.insight.SessionOutcome): Int = when (o) {
        com.personalfitnesscoach.data.core.insight.SessionOutcome.DONE -> R.string.hist_outcome_done
        com.personalfitnesscoach.data.core.insight.SessionOutcome.PARTIAL -> R.string.hist_outcome_partial
        com.personalfitnesscoach.data.core.insight.SessionOutcome.STOPPED -> R.string.hist_outcome_stopped
        com.personalfitnesscoach.data.core.insight.SessionOutcome.SKIPPED -> R.string.hist_outcome_skipped
    }

    fun question(q: com.personalfitnesscoach.data.core.coach.Question): Int = when (q) {
        com.personalfitnesscoach.data.core.coach.Question.WHY_THIS_EXERCISE -> R.string.q_why_this_exercise
        com.personalfitnesscoach.data.core.coach.Question.WHAT_IT_TRAINS -> R.string.q_what_it_trains
        com.personalfitnesscoach.data.core.coach.Question.CAN_I_REPLACE -> R.string.q_can_i_replace
        com.personalfitnesscoach.data.core.coach.Question.WHY_TODAY_DIFFERENT -> R.string.q_why_today_different
        com.personalfitnesscoach.data.core.coach.Question.WHY_LESS_VOLUME -> R.string.q_why_less_volume
        com.personalfitnesscoach.data.core.coach.Question.ONLY_N_MINUTES -> R.string.q_only_n_minutes
        com.personalfitnesscoach.data.core.coach.Question.TIRED_TODAY -> R.string.q_tired_today
        com.personalfitnesscoach.data.core.coach.Question.IMPROVED_THIS_MONTH -> R.string.q_improved_this_month
        com.personalfitnesscoach.data.core.coach.Question.FOCUS_NEXT -> R.string.q_focus_next
        com.personalfitnesscoach.data.core.coach.Question.WHY_TARGET_CHANGED -> R.string.q_why_target_changed
        com.personalfitnesscoach.data.core.coach.Question.WHY_LIGHTER_WEEK -> R.string.q_why_lighter_week
        com.personalfitnesscoach.data.core.coach.Question.HOW_HARD -> R.string.q_how_hard
        com.personalfitnesscoach.data.core.coach.Question.HARDER -> R.string.q_harder
    }

    fun outOfScope(o: com.personalfitnesscoach.data.core.coach.OutOfScope): Int = when (o) {
        com.personalfitnesscoach.data.core.coach.OutOfScope.URGENT_SYMPTOMS -> R.string.oos_urgent
        com.personalfitnesscoach.data.core.coach.OutOfScope.MEDICATION -> R.string.oos_medication
        com.personalfitnesscoach.data.core.coach.OutOfScope.SUPPLEMENTS -> R.string.oos_supplements
        com.personalfitnesscoach.data.core.coach.OutOfScope.DIET -> R.string.oos_diet
        com.personalfitnesscoach.data.core.coach.OutOfScope.MEDICAL -> R.string.oos_medical
        com.personalfitnesscoach.data.core.coach.OutOfScope.PAIN -> R.string.oos_pain
        com.personalfitnesscoach.data.core.coach.OutOfScope.ILLNESS -> R.string.oos_illness
    }

    fun theme(t: com.personalfitnesscoach.data.core.model.Theme): Int = when (t) {
        com.personalfitnesscoach.data.core.model.Theme.DARK -> R.string.theme_dark
        com.personalfitnesscoach.data.core.model.Theme.LIGHT -> R.string.theme_light
        com.personalfitnesscoach.data.core.model.Theme.SYSTEM -> R.string.theme_system
    }

    fun section(s: com.personalfitnesscoach.app.flow.ProfileSection): Int = when (s) {
        com.personalfitnesscoach.app.flow.ProfileSection.GOALS -> R.string.set_goals
        com.personalfitnesscoach.app.flow.ProfileSection.SCHEDULE -> R.string.set_schedule
        com.personalfitnesscoach.app.flow.ProfileSection.EQUIPMENT -> R.string.set_equipment
        com.personalfitnesscoach.app.flow.ProfileSection.INJURIES -> R.string.set_injuries
    }

    fun fallback(id: String): String = id.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
