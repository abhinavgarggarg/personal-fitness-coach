package com.personalfitnesscoach.engine.program

import com.personalfitnesscoach.engine.calc.Volume
import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.model.EquipmentClass
import com.personalfitnesscoach.engine.model.Exercise
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.LoadType
import com.personalfitnesscoach.engine.model.Muscle
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds
import com.personalfitnesscoach.engine.safety.RegionConstraint
import com.personalfitnesscoach.engine.safety.Session

/** FREQ-001 to FREQ-004. */
object Frequency {
    /** FREQ-001: 2–6 training days with at least one full rest day; 3 when unknown (ASSUMPTION). */
    fun trainingDays(requested: Int?): Int =
        (requested ?: P.FREQ_001.default_days).coerceIn(P.FREQ_001.min_days, minOf(P.FREQ_001.max_days, 7 - P.FREQ_001.min_rest_days))

    /** Major muscles that must be trained at least twice a week (FREQ-002). */
    val MAJOR = setOf(Muscle.CHEST, Muscle.LATS, Muscle.UPPER_BACK, Muscle.FRONT_DELTS, Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES)

    /** FREQ-002: exposures per major muscle; an exposure is ≥ 2 fractional hard sets in one session. */
    fun exposures(week: List<Session>): Map<Muscle, Int> {
        val out = MAJOR.associateWith { 0 }.toMutableMap()
        for (s in week) {
            val credit = Volume.weekly(s.exercises.map { it.exercise to it.sets.toDouble() })
            for (m in MAJOR) if ((credit[m] ?: 0.0) >= P.FREQ_002.exposure_min_fractional_sets - 1e-9) out[m] = out.getValue(m) + 1
        }
        return out
    }

    fun underExposed(week: List<Session>): Set<Muscle> =
        exposures(week).filterValues { it < P.FREQ_002.min_exposures_per_week }.keys

    /** FREQ-003: resistance training on at least 2 days every week. */
    fun resistanceDaysOk(week: List<Session>): Boolean = week.count { it.exercises.isNotEmpty() } >= P.FREQ_003.min_days

    private val MAIN = listOf(Pattern.SQUAT, Pattern.HINGE, Pattern.HORIZONTAL_PUSH, Pattern.HORIZONTAL_PULL)

    /** FREQ-004: in strength blocks each main pattern is loaded on 2–3 days. Returns patterns outside that band. */
    fun strengthBlockIssues(week: List<Session>): Map<Pattern, Int> =
        MAIN.associateWith { p -> week.count { s -> s.exercises.any { it.exercise.pattern == p } } }
            .filterValues { it < P.FREQ_004.min || it > P.FREQ_004.max }
}

/** EXP-001 onboarding answers. */
data class ExperienceAnswers(
    val monthsConsistent: Int,
    /** Of the key lifts squat, hinge, press, row: those the user is comfortable with. */
    val comfortableWith: Set<String>,
    val structuredProgramming: Boolean = false,
    val confidentEffortRatings: Boolean = false,
)

/** EXP-002 review inputs. */
data class LevelReview(
    val level: Level,
    val monthsAtLevel: Int,
    val monthsTraining: Int,
    /** Main lifts that have not progressed session to session for ≥ 3 weeks despite FULL readiness. */
    val stalledMainLifts: Int,
    val adherence: Double,
    /** Mean weekly e1RM change (%) over the last 12 weeks. */
    val e1rmPctPerWeek12: Double,
    /** Length of the break just ended, in weeks (0 = none). */
    val breakWeeks: Int = 0,
)

data class LevelChange(val level: Level, val temporaryWeeks: Int = 0)

/** EXP-001, EXP-002. */
object Experience {
    fun classify(a: ExperienceAnswers): EngineResult<Level> {
        val keyLifts = P.EXP_001.key_lifts.toSet()
        val comfortable = a.comfortableWith.intersect(keyLifts).size
        val level = when {
            a.monthsConsistent < P.EXP_001.beginner_months_lt || keyLifts.size - comfortable >= 2 -> Level.BEGINNER
            a.monthsConsistent > P.EXP_001.advanced_months_gt && comfortable == keyLifts.size && a.structuredProgramming && a.confidentEffortRatings -> Level.ADVANCED
            else -> Level.INTERMEDIATE
        }
        return EngineResult(level, listOf(Decision(DecisionKind.CALIBRATION, listOf(RuleIds.EXP_001), ReasonKey.LEVEL_CLASSIFIED,
            inputs = mapOf("months" to a.monthsConsistent, "comfortable" to comfortable), outputs = mapOf("level" to level.name))))
    }

    /** EXP-001: in the first 4 weeks calibration may move the level down, never up. */
    fun afterCalibration(current: Level, suggested: Level, weeksSinceStart: Int): Level =
        if (weeksSinceStart < P.EXP_001.lock_up_weeks && suggested.ordinal > current.ordinal) current else suggested

    /** EXP-002: promotion by performance, not calendar; a break of ≥ 8 weeks trains one level lower for 4 weeks. */
    fun review(r: LevelReview): EngineResult<LevelChange> {
        val p = P.EXP_002
        val change = when {
            r.breakWeeks >= p.break_weeks && r.level != Level.BEGINNER ->
                LevelChange(Level.entries[r.level.ordinal - 1], p.demotion_weeks)
            r.level == Level.BEGINNER && ((r.stalledMainLifts >= 2 && r.adherence >= p.adherence_min) || r.monthsAtLevel >= p.beginner_max_months) ->
                LevelChange(Level.INTERMEDIATE)
            r.level == Level.INTERMEDIATE && r.monthsTraining >= p.advanced_min_months && r.e1rmPctPerWeek12 < p.progress_pct_week_lt && r.adherence >= p.adherence_min ->
                LevelChange(Level.ADVANCED)
            else -> LevelChange(r.level)
        }
        val d = if (change.level == r.level) emptyList() else listOf(Decision(DecisionKind.CALIBRATION, listOf(RuleIds.EXP_002),
            if (change.level.ordinal > r.level.ordinal) ReasonKey.LEVEL_PROMOTED else ReasonKey.LEVEL_TEMPORARILY_LOWER,
            inputs = mapOf("level" to r.level.name), outputs = mapOf("level" to change.level.name, "weeks" to change.temporaryWeeks)))
        return EngineResult(change, d)
    }
}

/**
 * IND-001: one algorithm for everyone (no sex input exists); calibration starts scale with
 * bodyweight when given; prior injuries become limitation tags with that region conservative
 * for 4 weeks. Start fractions are Expert Practice (D-048) and deliberately low: CAL-001 ramps
 * up quickly from an easy first set.
 */
object Individual {
    private val LOWER = setOf(Pattern.SQUAT, Pattern.HINGE, Pattern.LUNGE)

    fun startFraction(ex: Exercise): Double = when (ex.loadType) {
        LoadType.BARBELL -> when (ex.pattern) {
            Pattern.SQUAT -> 0.25; Pattern.HINGE -> 0.30; Pattern.LUNGE -> 0.15
            Pattern.HORIZONTAL_PUSH, Pattern.HORIZONTAL_PULL -> 0.20; Pattern.VERTICAL_PUSH -> 0.12
            else -> 0.10
        }
        LoadType.DUMBBELL, LoadType.KETTLEBELL -> when {
            ex.pattern in LOWER -> 0.08; ex.pattern == Pattern.ISOLATION -> 0.03; else -> 0.05
        }
        LoadType.STACK -> when {
            ex.pattern in LOWER -> 0.30; ex.pattern == Pattern.ISOLATION -> 0.10; else -> 0.20
        }
        else -> 0.0
    }

    /** Calibration start: the bodyweight fraction rounded down to real loads, never below the lightest load. */
    fun calibrationStart(ex: Exercise, bodyweightKg: Double?, available: List<Double>): Double? {
        val lightest = available.minOrNull() ?: return null
        if (bodyweightKg == null || bodyweightKg <= 0.0 || ex.assisted) return lightest
        val target = bodyweightKg * startFraction(ex)
        return available.filter { it <= target + 1e-9 }.maxOrNull()?.let { maxOf(it, lightest) } ?: lightest
    }

    /** Limitation tags implied by a prior injury to a joint. */
    fun tagsFor(j: Joint): Set<String> = when (j) {
        Joint.SHOULDER -> setOf("overhead", "shoulder_extension_load")
        Joint.KNEE -> setOf("deep_knee_flexion", "jumping")
        Joint.SPINE -> setOf("spinal_loading", "spinal_flexion")
        Joint.WRIST -> setOf("wrist_extension_load")
        Joint.ANKLE -> setOf("jumping")
        Joint.HIP -> setOf("deep_knee_flexion")
        Joint.ELBOW -> emptySet()
    }

    data class InjuryPlan(val blockedTags: Set<String>, val regions: List<RegionConstraint>, val sensitiveJoints: Set<Joint>)

    /** Conservative mode (SAF-004 limits) for the first 4 weeks; afterwards the joint stays "sensitive" for swaps. */
    fun injuries(prior: Set<Joint>, weeksSinceStart: Int): EngineResult<InjuryPlan> {
        val conservative = weeksSinceStart < P.IND_001.injury_conservative_weeks
        val plan = if (conservative) InjuryPlan(prior.flatMap { tagsFor(it) }.toSet(),
            prior.sortedBy { it.ordinal }.map { RegionConstraint(it, P.SAF_004.region_max_joint_stress, P.SAF_004.region_min_rir, noFailure = true, noJumping = true, suggestProfessional = false) },
            prior)
        else InjuryPlan(emptySet(), emptyList(), prior)
        val d = if (prior.isEmpty()) emptyList() else listOf(Decision(DecisionKind.SAFETY, listOf(RuleIds.IND_001, RuleIds.SAF_004),
            if (conservative) ReasonKey.REGION_CONSERVATIVE else ReasonKey.INJURY_SENSITIVE, inputs = mapOf("joints" to prior.map { it.name }.sorted(), "weeks" to weeksSinceStart)))
        return EngineResult(plan, d)
    }
}

/** Roles from the EQ-001 equipment matrix: no class is best at everything. */
enum class EquipmentRole { MAIN_LIFT, ACCESSORY, PLANNED_FAILURE, CROWDED_ACCESSORY }

/** EQ-001 role fit (0–1) used as the preference input when choosing exercises; Expert Practice (D-048). */
object EquipmentRoles {
    fun fit(ex: Exercise, role: EquipmentRole, techniqueReliable: Boolean): Double {
        val c = ex.equipmentClass
        return when (role) {
            EquipmentRole.MAIN_LIFT -> if (techniqueReliable) when (c) {
                EquipmentClass.BARBELL -> 1.0; EquipmentClass.DUMBBELL, EquipmentClass.MACHINE -> 0.8
                EquipmentClass.KETTLEBELL -> 0.7; EquipmentClass.CABLE, EquipmentClass.BODYWEIGHT -> 0.6; else -> 0.4
            } else when (c) {
                EquipmentClass.DUMBBELL, EquipmentClass.MACHINE -> 1.0; EquipmentClass.KETTLEBELL -> 0.8
                EquipmentClass.CABLE, EquipmentClass.BODYWEIGHT -> 0.7; EquipmentClass.BARBELL -> 0.5; else -> 0.4
            }
            EquipmentRole.ACCESSORY -> when (c) {
                EquipmentClass.DUMBBELL, EquipmentClass.CABLE, EquipmentClass.MACHINE -> 1.0
                EquipmentClass.BODYWEIGHT, EquipmentClass.KETTLEBELL -> 0.8; EquipmentClass.BAND -> 0.6; else -> 0.5
            }
            EquipmentRole.PLANNED_FAILURE -> if (!ex.failureSafe) 0.0 else when (c) {
                EquipmentClass.MACHINE, EquipmentClass.CABLE -> 1.0; else -> 0.8
            }
            EquipmentRole.CROWDED_ACCESSORY -> when (c) {
                EquipmentClass.DUMBBELL -> 1.0; EquipmentClass.BODYWEIGHT, EquipmentClass.KETTLEBELL -> 0.9
                EquipmentClass.BAND -> 0.8; EquipmentClass.MACHINE -> 0.5; else -> 0.4
            }
        }
    }

    /** EQ-002: a session (or superset) never needs more than 2 stations at once. */
    val maxSimultaneousStations: Int get() = P.EQ_002.max_simultaneous_stations
}
