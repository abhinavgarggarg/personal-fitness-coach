package com.personalfitnesscoach.data.core.session

import com.personalfitnesscoach.data.core.json.Js
import com.personalfitnesscoach.data.core.json.bool
import com.personalfitnesscoach.data.core.json.checkVersion
import com.personalfitnesscoach.data.core.json.dbl
import com.personalfitnesscoach.data.core.json.dblOrNull
import com.personalfitnesscoach.data.core.json.enum
import com.personalfitnesscoach.data.core.json.enumOrNull
import com.personalfitnesscoach.data.core.json.int
import com.personalfitnesscoach.data.core.json.obj
import com.personalfitnesscoach.data.core.json.objs
import com.personalfitnesscoach.data.core.json.range
import com.personalfitnesscoach.data.core.json.strOrNull
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.model.Zone
import com.personalfitnesscoach.engine.planning.Priority
import com.personalfitnesscoach.engine.program.DayTemplate
import com.personalfitnesscoach.engine.program.SlotRole
import com.personalfitnesscoach.engine.safety.ConditioningBlock
import com.personalfitnesscoach.engine.safety.HiitProtocol
import kotlinx.serialization.json.JsonObject

/** One conditioning block of a workout as validated (SAF-008), plus the minutes actually done once finished. */
data class ConditioningItem(
    val modality: Modality,
    val zone: Zone,
    val workMinutes: Double,
    val restMinutes: Double = 0.0,
    val hiit: Boolean = false,
    val impact: Int = 0,
    val protocol: HiitProtocol? = null,
    /** Work minutes done; null until the workout is finished (then 0 means skipped). */
    val doneWorkMinutes: Double? = null,
) {
    fun block(): ConditioningBlock = ConditioningBlock(modality, zone, workMinutes, restMinutes, hiit, impact, protocol)
    /**
     * The block as it counts once the workout is finished: work minutes as logged, never more than planned; as planned when the
     * minutes were not recorded (null), so caps and spacing never miss it; nothing when logged as 0 (skipped).
     */
    fun countedBlock(): ConditioningBlock? {
        val m = doneWorkMinutes ?: workMinutes
        return if (m <= 0.0) null else block().copy(workMinutes = minOf(m, workMinutes))
    }

    companion object {
        fun of(b: ConditioningBlock) = ConditioningItem(b.modality, b.zone, b.workMinutes, b.restMinutes, b.hiit, b.impact, b.protocol)
    }
}

/** Everything about a workout besides its exercises and sets (WorkoutRow.json, decision D-073). */
data class WorkoutDoc(
    val weekday: Int,
    val plannedMinutes: Double,
    val warmupMinutes: Double,
    val cooldownMinutes: Double? = null,
    val conditioning: List<ConditioningItem> = emptyList(),
    val conditioningFirst: Boolean = false,
    val mobilityMinutes: Double = 0.0,
    val balanceMinutes: Double = 0.0,
    val fullTierWorkingSets: Int = 0,
    val inDeload: Boolean = false,
    val express: Boolean = false,
    val awayFromGym: Boolean = false,
    /** Planned session effort for fatigue signal F2 (DEL-001): 10 − the mean target RIR of the working sets (INT-001); null without lifts. */
    val plannedSessionRpe: Double? = null,
) {
    fun encode(): String = obj {
        put("v", VERSION); put("weekday", weekday); put("plannedMinutes", plannedMinutes); put("warmupMinutes", warmupMinutes)
        put("cooldownMinutes", cooldownMinutes)
        objs("conditioning", conditioning.map { c -> obj {
            put("modality", c.modality); put("zone", c.zone); put("workMinutes", c.workMinutes); put("restMinutes", c.restMinutes)
            flag("hiit", c.hiit); if (c.impact != 0) put("impact", c.impact); put("protocol", c.protocol); put("doneWorkMinutes", c.doneWorkMinutes)
        } })
        flag("conditioningFirst", conditioningFirst); put("mobilityMinutes", mobilityMinutes); put("balanceMinutes", balanceMinutes)
        put("fullTierWorkingSets", fullTierWorkingSets); flag("inDeload", inDeload); flag("express", express); flag("awayFromGym", awayFromGym)
        put("plannedSessionRpe", plannedSessionRpe)
    }.toString()

    companion object {
        const val VERSION = 1
        fun decode(text: String): WorkoutDoc {
            val o = Js.parse(text)
            o.checkVersion("workout", VERSION)
            return WorkoutDoc(o.int("weekday"), o.dbl("plannedMinutes"), o.dbl("warmupMinutes"), o.dblOrNull("cooldownMinutes"),
                o.objs("conditioning").map { c -> ConditioningItem(c.enum("modality"), c.enum("zone"), c.dbl("workMinutes"), c.dbl("restMinutes", 0.0),
                    c.bool("hiit"), c.int("impact", 0), c.enumOrNull<HiitProtocol>("protocol"), c.dblOrNull("doneWorkMinutes")) },
                o.bool("conditioningFirst"), o.dbl("mobilityMinutes", 0.0), o.dbl("balanceMinutes", 0.0), o.int("fullTierWorkingSets", 0),
                o.bool("inDeload"), o.bool("express"), o.bool("awayFromGym"), o.dblOrNull("plannedSessionRpe"))
        }
    }
}

/** One exercise's prescription in a workout (ExerciseRow.json): the dose the validator approved. */
data class ItemDoc(
    val role: SlotRole,
    val priority: Priority,
    val sets: Int,
    val reps: IntRange,
    val unit: DoseUnit,
    val perSide: Boolean = false,
    val targetRir: Double,
    val lastSetToFailure: Boolean = false,
    /** kg (assistance kg for assisted moves); null for bodyweight and unloaded holds. */
    val load: Double? = null,
    val loadFactor: Double = 1.0,
    val restMinSec: Int,
    val restDefaultSec: Int,
    val restMaxSec: Int,
    val calibrating: Boolean = false,
    val main: Boolean = false,
    /** SUB: the exercise this one replaced in the session, if swapped. */
    val swappedFrom: String? = null,
    /** CAL-002: the load came from the user's own number on this first session. */
    val fromKnownNumber: Boolean = false,
) {
    fun encode(): String = obj {
        put("v", VERSION); put("role", role); put("priority", priority); put("sets", sets); ints("reps", listOf(reps.first, reps.last), sort = false)
        put("unit", unit); flag("perSide", perSide); put("targetRir", targetRir); flag("lastSetToFailure", lastSetToFailure); put("load", load)
        put("loadFactor", loadFactor); put("restMinSec", restMinSec); put("restDefaultSec", restDefaultSec); put("restMaxSec", restMaxSec)
        flag("calibrating", calibrating); flag("main", main); put("swappedFrom", swappedFrom); flag("fromKnownNumber", fromKnownNumber)
    }.toString()

    companion object {
        const val VERSION = 1
        fun decode(text: String): ItemDoc {
            val o: JsonObject = Js.parse(text)
            o.checkVersion("workout_exercise", VERSION)
            return ItemDoc(o.enum("role"), o.enum("priority"), o.int("sets"), o.range("reps"), o.enum("unit"), o.bool("perSide"), o.dbl("targetRir"),
                o.bool("lastSetToFailure"), o.dblOrNull("load"), o.dbl("loadFactor"), o.int("restMinSec"), o.int("restDefaultSec"), o.int("restMaxSec"),
                o.bool("calibrating"), o.bool("main"), o.strOrNull("swappedFrom"), o.bool("fromKnownNumber"))
        }
    }
}

/** Workout and exercise status values stored in the relational columns. */
object Status {
    const val PLANNED = "PLANNED"
    const val IN_PROGRESS = "IN_PROGRESS"
    const val DONE = "DONE"
    const val SKIPPED = "SKIPPED"
    const val MOVED = "MOVED"
    const val SWAPPED = "SWAPPED"
}

/** Set kinds (LoggedSet.kind). */
object SetKind {
    const val WARMUP = "WARMUP"
    const val CALIBRATION = "CALIBRATION"
    const val WORKING = "WORKING"
    val all = setOf(WARMUP, CALIBRATION, WORKING)
}
