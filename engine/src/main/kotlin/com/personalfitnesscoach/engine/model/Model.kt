package com.personalfitnesscoach.engine.model

/** Experience level (EXP-001); most registry parameters are keyed by it. */
enum class Level {
    BEGINNER, INTERMEDIATE, ADVANCED;

    fun <T> pick(beginner: T, intermediate: T, advanced: T): T = when (this) {
        BEGINNER -> beginner
        INTERMEDIATE -> intermediate
        ADVANCED -> advanced
    }
}

/** Session tiers, ordered from least to most demanding (RDY-003). */
enum class Tier { RECOVERY, LIGHT, MODIFIED, FULL;
    fun stepDown(): Tier = if (this == RECOVERY) RECOVERY else entries[ordinal - 1]
    fun stepUp(): Tier = if (this == FULL) FULL else entries[ordinal + 1]
    companion object {
        fun min(a: Tier, b: Tier): Tier = if (a.ordinal <= b.ordinal) a else b
    }
}

/** The 12 movement patterns (Phase 1 section 17). */
enum class Pattern(val coreCategory: Boolean = false) {
    SQUAT, HINGE, LUNGE, HORIZONTAL_PUSH, HORIZONTAL_PULL, VERTICAL_PUSH, VERTICAL_PULL,
    LOADED_CARRY, ROTATION, ANTI_EXTENSION(true), ANTI_ROTATION(true), ANTI_LATERAL_FLEXION(true),
    /** Isolation work that does not belong to a main pattern (curls, raises, calf raises). */
    ISOLATION;

    val isPush get() = this == HORIZONTAL_PUSH || this == VERTICAL_PUSH
    val isPull get() = this == HORIZONTAL_PULL || this == VERTICAL_PULL
    val isKneeDominant get() = this == SQUAT || this == LUNGE
    val isHipDominant get() = this == HINGE

    /** Adjacent patterns score 0.5 in substitution (SUB-002). */
    fun isAdjacentTo(other: Pattern): Boolean = setOf(this, other) in ADJACENT

    private companion object {
        val ADJACENT = setOf(
            setOf(SQUAT, LUNGE), setOf(HINGE, LUNGE),
            setOf(HORIZONTAL_PULL, VERTICAL_PULL), setOf(HORIZONTAL_PUSH, VERTICAL_PUSH),
            setOf(ANTI_EXTENSION, ANTI_ROTATION), setOf(ANTI_ROTATION, ANTI_LATERAL_FLEXION),
        )
    }
}

enum class Muscle {
    CHEST, LATS, UPPER_BACK, FRONT_DELTS, SIDE_DELTS, REAR_DELTS, BICEPS, TRICEPS,
    QUADS, HAMSTRINGS, GLUTES, CALVES, ADDUCTORS, CORE, FOREARMS,
}

enum class Joint { SHOULDER, ELBOW, WRIST, SPINE, HIP, KNEE, ANKLE }

/** Systemic cost class C in the SSU equation (VOL-007). */
enum class CostClass { ISOLATION_OR_CORE, MACHINE_OR_CABLE_COMPOUND, FREE_WEIGHT_COMPOUND, HEAVY_BILATERAL }

enum class LoadType { BARBELL, DUMBBELL, STACK, KETTLEBELL, BODYWEIGHT, TIME, DISTANCE }

enum class Objective { STRENGTH, HYPERTROPHY, ENDURANCE, POWER, CONDITIONING, CORE, MOBILITY, GPP }

/** Exercise metadata — the subset of the Phase 1 schema (section 29) the engine reasons with. */
data class Exercise(
    val id: String,
    val name: String,
    val pattern: Pattern,
    val primary: Set<Muscle>,
    val secondary: Set<Muscle> = emptySet(),
    val equipment: Set<String> = emptySet(),
    val loadType: LoadType,
    val costClass: CostClass,
    val difficulty: Int = 2,
    val skill: Int = 2,
    val jointStress: Map<Joint, Int> = emptyMap(),
    val fatigueSystemic: Int = 3,
    val impact: Int = 0,
    val objectives: Set<Objective> = setOf(Objective.HYPERTROPHY),
    val defaultRepRange: IntRange = 8..12,
    val maxExtendedReps: Int = 15,
    val failureSafe: Boolean = false,
    val trackE1rm: Boolean = false,
    val unilateral: Boolean = false,
    val tempoSecPerRep: Double = 3.5,
    val setupSec: Int = 20,
    val station: Station = Station.FLOOR,
    val limitationTags: Set<String> = emptySet(),
) {
    fun stress(j: Joint): Int = jointStress[j] ?: 0
}

enum class Station { SINGLE_STATION, MULTI_STATION, FLOOR }

/** Conditioning modalities (MOD-001, MOD-002). Excluded ones exist only so they can be rejected. */
enum class Modality(val excluded: Boolean = false, val pendingConfirmation: Boolean = false) {
    ROWER, SKIERG, ELLIPTICAL, SLED, BATTLE_ROPES, KETTLEBELL, CARRIES, MEDBALL, BODYWEIGHT_CIRCUIT, JUMP_ROPE,
    TREADMILL_RUN(excluded = true), STATIONARY_BIKE(excluded = true), STAIR_MACHINE(excluded = true),
    AIR_BIKE(excluded = true, pendingConfirmation = true), TREADMILL_WALK(excluded = true, pendingConfirmation = true),
}

/** Aerobic intensity zones (AER-001). */
enum class Zone { Z1, Z2, Z3, Z4 }

/** A logged set (LoggedSet entity). `rir` is null when the user did not rate effort. */
data class SetLog(
    val load: Double,
    val reps: Int,
    val rir: Double? = null,
    val warmup: Boolean = false,
    val formOk: FormCheck = FormCheck.YES,
)

enum class FormCheck { YES, UNSURE, NO }
