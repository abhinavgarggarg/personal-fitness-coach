package com.personalfitnesscoach.engine.library

import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.EquipmentClass
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.model.Pattern

data class EquipmentInfo(val id: String, val name: String, val equipmentClass: EquipmentClass)

/** User-facing instructions. All text is original (D7). */
data class ExerciseText(val setup: String, val cues: List<String>, val mistakes: List<String>, val safety: String?)

enum class DrillKind { ACTIVATE, MOBILISE, BALANCE, STRETCH, BREATHING }

/** Body regions a drill or stretch targets (MOB-001 matches them to the session's patterns). */
enum class Region { HIPS, ANKLES, KNEES, SPINE, UPPER_BACK, SHOULDERS, WRISTS, CHEST, LATS, QUADS, HAMSTRINGS, GLUTES, CALVES, ADDUCTORS, HIP_FLEXORS, WHOLE_BODY }

/** A warm-up, balance, cool-down or breathing drill. `amount` is reps or seconds, per side when `perSide`. */
data class Drill(
    val id: String,
    val name: String,
    val kind: DrillKind,
    val regions: Set<Region>,
    val prepares: Set<Pattern>,
    val unit: DoseUnit,
    val amount: Int,
    val perSide: Boolean,
    val equipment: Set<String>,
) {
    /** Seconds the drill takes, including a short change-over. */
    val seconds: Int
        get() = (if (unit == DoseUnit.SECONDS) amount else amount * 3) * (if (perSide) 2 else 1) + 10
}

enum class ConditioningUnit { MINUTES, METRES, WATTS, CALORIES, REPS }

/** Equipment and units for a conditioning modality (REP-006); `altEquipment` items can replace the main one. */
data class ModalityInfo(
    val modality: Modality,
    val name: String,
    val equipment: Set<String>,
    val altEquipment: Set<String>,
    val units: List<ConditioningUnit>,
)
