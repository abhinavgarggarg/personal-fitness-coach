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

/** Equipment class used for library coverage and the equipment matrix (EQ-001). */
enum class EquipmentClass { BARBELL, DUMBBELL, KETTLEBELL, CABLE, MACHINE, BODYWEIGHT, BAND, OTHER, CONDITIONING }

/** What one "rep" of an exercise is: a repetition, a second of holding, or a metre carried/crawled. */
enum class DoseUnit { REPS, SECONDS, METRES }

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
    val aliases: List<String> = emptyList(),
    /** Optional second pattern (suitcase carry also trains anti-lateral flexion); counts for PAT-001 coverage only. */
    val secondaryPattern: Pattern? = null,
    val equipmentClass: EquipmentClass = EquipmentClass.OTHER,
    /** Where the exercise is done (equipment ID or "floor"); a change costs TIME-004 station time. */
    val stationKey: String = "floor",
    /** Bodyweight or core ladder (BW-001, CORE-002) and the rung on it, 1 = easiest. */
    val family: String? = null,
    val rung: Int = 0,
    val progressionId: String? = null,
    val regressionId: String? = null,
    val unit: DoseUnit = DoseUnit.REPS,
    /** Usable for intent-based power sets (REP-004). */
    val powerCapable: Boolean = false,
    /** Machine or band assistance: more load means easier (BW-002). */
    val assisted: Boolean = false,
    /** Which bar a BARBELL load uses: barbell, ez_bar, trap_bar or landmine (one loaded end). */
    val bar: String? = null,
    /** Never prescribed by default; only when the user adds it (CORE-001 crunches). */
    val userAddOnly: Boolean = false,
    /** "Any of" equipment (D-063): one item from each group is enough (a step-up works on a box or a bench). */
    val equipmentAnyOf: List<Set<String>> = emptyList(),
    /** Fills power slots only, never a lifting slot (D-057: jumps, throws and speed drills). */
    val powerOnly: Boolean = false,
) {
    fun stress(j: Joint): Int = jointStress[j] ?: 0

    /** True when `available` has every required item and one item from each "any of" group. */
    fun usableWith(available: Set<String>): Boolean =
        available.containsAll(equipment) && equipmentAnyOf.all { g -> g.any { it in available } }

    /** Every item the exercise could use: required plus all "any of" options. */
    val allEquipment: Set<String> get() = equipment + equipmentAnyOf.flatten()

    /** True when the exercise trains `p` as its main or second pattern. */
    fun trains(p: Pattern): Boolean = pattern == p || secondaryPattern == p

    /**
     * Dosed as a compound (REP-002, REST-002, INT-002): a lifting pattern with a compound cost class.
     * Low-cost bodyweight moves (push-ups, inverted rows) are dosed like accessories.
     */
    val dosedAsCompound: Boolean
        get() = pattern != Pattern.ISOLATION && !pattern.coreCategory && pattern != Pattern.ROTATION &&
            costClass != CostClass.ISOLATION_OR_CORE
}

enum class Station { SINGLE_STATION, MULTI_STATION, FLOOR }

/** Conditioning modalities (MOD-001, MOD-002). Excluded ones exist only so they can be rejected. */
/**
 * Conditioning modalities. Machines with a [registryKey] are per-person choices (MOD-001 2.0.0): each user keeps a
 * list of modalities they don't use; nothing is excluded globally.
 */
enum class Modality(val registryKey: String? = null) {
    ROWER, SKIERG, ELLIPTICAL, SLED, BATTLE_ROPES, KETTLEBELL, CARRIES, MEDBALL, BODYWEIGHT_CIRCUIT, JUMP_ROPE,
    TREADMILL_RUN("treadmill_running"), STATIONARY_BIKE("stationary_bike"), STAIR_MACHINE("stair_machine"),
    AIR_BIKE("air_fan_bike"), TREADMILL_WALK("treadmill_walking");

    val userSelectable: Boolean get() = registryKey != null

    companion object {
        fun byRegistryKey(key: String): Modality? = entries.firstOrNull { it.registryKey == key }
    }
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
