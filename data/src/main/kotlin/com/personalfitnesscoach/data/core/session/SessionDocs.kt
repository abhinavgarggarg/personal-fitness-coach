package com.personalfitnesscoach.data.core.session

import com.personalfitnesscoach.data.core.json.Js
import com.personalfitnesscoach.data.core.json.Obj
import com.personalfitnesscoach.data.core.json.bool
import com.personalfitnesscoach.data.core.json.checkVersion
import com.personalfitnesscoach.data.core.json.dbl
import com.personalfitnesscoach.data.core.json.dblOrNull
import com.personalfitnesscoach.data.core.json.enum
import com.personalfitnesscoach.data.core.json.enumOrNull
import com.personalfitnesscoach.data.core.json.int
import com.personalfitnesscoach.data.core.json.obj
import com.personalfitnesscoach.data.core.json.objOrNull
import com.personalfitnesscoach.data.core.json.objs
import com.personalfitnesscoach.data.core.json.range
import com.personalfitnesscoach.data.core.json.str
import com.personalfitnesscoach.data.core.json.strOrNull
import com.personalfitnesscoach.data.core.json.strings
import com.personalfitnesscoach.engine.library.GeneratedLibrary
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.model.Zone
import com.personalfitnesscoach.engine.planning.Priority
import com.personalfitnesscoach.engine.program.DayTemplate
import com.personalfitnesscoach.engine.program.SlotRole
import com.personalfitnesscoach.engine.safety.BoneLoadingVariant
import com.personalfitnesscoach.engine.safety.ConditioningBlock
import com.personalfitnesscoach.engine.safety.HiitProtocol
import kotlinx.serialization.json.JsonObject

/**
 * A drill as the session prescribes it (warm-up, cool-down, balance): the drill's ID in the bundled library (D-077), the sets and the
 * reps or seconds per set. Stored so a session can be resumed, and shown again, exactly as it was validated.
 */
data class DrillRef(val id: String, val sets: Int, val amount: Int) {
    init {
        require(Library.drill(id) != null) { "unknown drill $id" }
        require(sets in 1..20 && amount in 1..3600) { "drill dose out of range" }
    }

    internal fun write(o: Obj) = with(o) { put("id", id); put("sets", sets); put("amount", amount) }

    companion object {
        fun of(d: com.personalfitnesscoach.engine.planning.DrillDose) = DrillRef(d.drill.id, d.sets, d.amount)
        internal fun read(o: JsonObject) = DrillRef(o.str("id"), o.int("sets"), o.int("amount"))
        internal fun list(o: JsonObject, k: String) = o.objs(k).map { read(it) }
    }
}

/** A WU-002 ramp-up set before a main lift: load and reps. */
data class RampRef(val load: Double, val reps: IntRange) {
    init { require(load in 0.0..1000.0 && reps.first in 1..50 && reps.last in reps.first..50) { "ramp set out of range" } }
}

/** HIIT-002 interval structure of a conditioning block: repeats × (work + rest) at a CR10 range. */
data class IntervalRef(val reps: Int, val workSec: Int, val restSec: Int, val cr10: IntRange) {
    init { require(reps in 1..100 && workSec in 1..3600 && restSec in 0..3600 && cr10.first in 0..10 && cr10.last in cr10.first..10) { "interval out of range" } }

    companion object {
        fun of(i: com.personalfitnesscoach.engine.conditioning.Interval) = IntervalRef(i.reps, i.workSec, i.restSec, i.cr10)
    }
}

/** EQ-003 bodyweight circuit: the moves (library IDs), work and rest per station and rounds. */
data class CircuitRef(val moves: List<String>, val workSec: Int, val restSec: Int, val rounds: Int, val cr10: IntRange) {
    init {
        require(moves.isNotEmpty() && moves.all { CIRCUIT_MOVES.containsKey(it) }) { "unknown circuit move" }
        require(workSec in 1..3600 && restSec in 0..3600 && rounds in 1..50 && cr10.first in 0..10 && cr10.last in cr10.first..10) { "circuit out of range" }
    }

    companion object {
        /** Every circuit move in the bundled library, by ID. */
        val CIRCUIT_MOVES: Map<String, com.personalfitnesscoach.engine.library.CircuitMove> by lazy {
            GeneratedLibrary.modalities.flatMap { it.moves }.associateBy { it.id } }
        fun of(c: com.personalfitnesscoach.engine.conditioning.CircuitPlan) = CircuitRef(c.moves.map { it.id }, c.workSec, c.restSec, c.rounds, c.cr10)
    }
}

/** CON-004 / SAF-010 osteoporosis bone-loading block: minutes, landings and the variant chosen for today's limits. */
data class BoneRef(val minutes: Double, val landings: Int, val variant: BoneLoadingVariant) {
    init { require(minutes in 0.0..30.0 && landings in 0..500) { "bone-loading block out of range" } }
}

private fun Obj.range(k: String, r: IntRange) = ints(k, listOf(r.first, r.last), sort = false)

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
    /** HIIT-002 interval structure, or null for steady work. */
    val interval: IntervalRef? = null,
    /** EQ-003 circuit moves for a bodyweight-circuit block. */
    val circuit: CircuitRef? = null,
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
        fun of(b: ConditioningBlock, interval: IntervalRef? = null, circuit: CircuitRef? = null) =
            ConditioningItem(b.modality, b.zone, b.workMinutes, b.restMinutes, b.hiit, b.impact, b.protocol, interval = interval, circuit = circuit)
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
    /** SAF-002: the session was ended by a red-flag stop. Its work counts towards caps and spacing, never towards progression. */
    val stoppedBySafety: Boolean = false,
    /** WU-001 general warm-up drills, the cool-down drills and the FL-003 / AGE-001 balance drills as validated. */
    val warmupDrills: List<DrillRef> = emptyList(),
    val cooldownDrills: List<DrillRef> = emptyList(),
    val balanceDrills: List<DrillRef> = emptyList(),
    /** CON-004 / SAF-010 bone-loading block. */
    val boneLoading: BoneRef? = null,
    /** SAF-010: go by feel (talk test, CR10), not heart-rate numbers. */
    val effortByFeel: Boolean = false,
    /** The session was ended early by the user, by time or by the pain gate (Phase 2 state PARTIAL). */
    val endedEarly: Boolean = false,
) {
    init {
        require(weekday in 0..6) { "weekday is 0–6" }
        require(plannedMinutes in 0.0..600.0 && warmupMinutes in 0.0..120.0 && (cooldownMinutes ?: 0.0) in 0.0..120.0) { "minutes out of range" }
        require(conditioning.all { it.workMinutes in 0.0..600.0 && it.restMinutes in 0.0..600.0 && (it.doneWorkMinutes ?: 0.0) in 0.0..600.0 && it.impact in 0..1 }) {
            "conditioning out of range" }
        require(plannedSessionRpe == null || plannedSessionRpe in 0.0..10.0) { "planned effort out of range" }
    }

    fun encode(): String = obj {
        put("v", VERSION); put("weekday", weekday); put("plannedMinutes", plannedMinutes); put("warmupMinutes", warmupMinutes)
        put("cooldownMinutes", cooldownMinutes)
        objs("conditioning", conditioning.map { c -> obj {
            put("modality", c.modality); put("zone", c.zone); put("workMinutes", c.workMinutes); put("restMinutes", c.restMinutes)
            flag("hiit", c.hiit); if (c.impact != 0) put("impact", c.impact); put("protocol", c.protocol); put("doneWorkMinutes", c.doneWorkMinutes)
            c.interval?.let { i -> put("interval", obj { put("reps", i.reps); put("workSec", i.workSec); put("restSec", i.restSec); range("cr10", i.cr10) }) }
            c.circuit?.let { k -> put("circuit", obj { strings("moves", k.moves, sort = false); put("workSec", k.workSec); put("restSec", k.restSec)
                put("rounds", k.rounds); range("cr10", k.cr10) }) }
        } })
        flag("conditioningFirst", conditioningFirst); put("mobilityMinutes", mobilityMinutes); put("balanceMinutes", balanceMinutes)
        put("fullTierWorkingSets", fullTierWorkingSets); flag("inDeload", inDeload); flag("express", express); flag("awayFromGym", awayFromGym)
        put("plannedSessionRpe", plannedSessionRpe); flag("stoppedBySafety", stoppedBySafety)
        objs("warmupDrills", warmupDrills.map { d -> obj { d.write(this) } }); objs("cooldownDrills", cooldownDrills.map { d -> obj { d.write(this) } })
        objs("balanceDrills", balanceDrills.map { d -> obj { d.write(this) } })
        boneLoading?.let { b -> put("boneLoading", obj { put("minutes", b.minutes); put("landings", b.landings); put("variant", b.variant) }) }
        flag("effortByFeel", effortByFeel); flag("endedEarly", endedEarly)
    }.toString()

    companion object {
        const val VERSION = 1
        fun decode(text: String): WorkoutDoc {
            val o = Js.parse(text)
            o.checkVersion("workout", VERSION)
            return WorkoutDoc(o.int("weekday"), o.dbl("plannedMinutes"), o.dbl("warmupMinutes"), o.dblOrNull("cooldownMinutes"),
                o.objs("conditioning").map { c -> ConditioningItem(c.enum("modality"), c.enum("zone"), c.dbl("workMinutes"), c.dbl("restMinutes", 0.0),
                    c.bool("hiit"), c.int("impact", 0), c.enumOrNull<HiitProtocol>("protocol"), c.dblOrNull("doneWorkMinutes"),
                    c.objOrNull("interval")?.let { i -> IntervalRef(i.int("reps"), i.int("workSec"), i.int("restSec"), i.range("cr10")) },
                    c.objOrNull("circuit")?.let { k -> CircuitRef(k.strings("moves"), k.int("workSec"), k.int("restSec"), k.int("rounds"), k.range("cr10")) }) },
                o.bool("conditioningFirst"), o.dbl("mobilityMinutes", 0.0), o.dbl("balanceMinutes", 0.0), o.int("fullTierWorkingSets", 0),
                o.bool("inDeload"), o.bool("express"), o.bool("awayFromGym"), o.dblOrNull("plannedSessionRpe"), o.bool("stoppedBySafety"),
                DrillRef.list(o, "warmupDrills"), DrillRef.list(o, "cooldownDrills"), DrillRef.list(o, "balanceDrills"),
                o.objOrNull("boneLoading")?.let { b -> BoneRef(b.dbl("minutes"), b.int("landings"), b.enum("variant")) },
                o.bool("effortByFeel"), o.bool("endedEarly"))
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
    /** WU-002 ramp-up sets before this lift. */
    val rampSets: List<RampRef> = emptyList(),
    /** MOB-004: a shorter range today (pain caution or a SAF-010 range-limited tag). */
    val reducedRange: Boolean = false,
    /** CAL-001 / CAL-002: the ramp never goes above this load (an old known number). */
    val calibrationCeiling: Double? = null,
    /** SAF-003 continue-with-caution: the load was lowered in this session after a pain report. */
    val painReduced: Boolean = false,
    /** SAF-006: sets the user added beyond the prescription (confirmed past a cap, counted). */
    val addedSets: Int = 0,
) {
    init {
        require(sets in 0..50 && !reps.isEmpty() && reps.first >= 0 && reps.last <= 1000) { "dose out of range" }
        require(calibrationCeiling == null || calibrationCeiling in 0.0..1000.0) { "calibration ceiling out of range" }
        require(addedSets in 0..sets) { "added sets out of range" }
        require(targetRir in 0.0..10.0) { "target RIR out of range" }
        require(load == null || load in 0.0..1000.0) { "load out of range" }
        require(loadFactor in 0.0..1.0 + 1e-9) { "load factor out of range" }
        require(restMinSec in 0..3600 && restDefaultSec in 0..3600 && restMaxSec in 0..3600) { "rest out of range" }
    }

    fun encode(): String = obj {
        put("v", VERSION); put("role", role); put("priority", priority); put("sets", sets); ints("reps", listOf(reps.first, reps.last), sort = false)
        put("unit", unit); flag("perSide", perSide); put("targetRir", targetRir); flag("lastSetToFailure", lastSetToFailure); put("load", load)
        put("loadFactor", loadFactor); put("restMinSec", restMinSec); put("restDefaultSec", restDefaultSec); put("restMaxSec", restMaxSec)
        flag("calibrating", calibrating); flag("main", main); put("swappedFrom", swappedFrom); flag("fromKnownNumber", fromKnownNumber)
        objs("rampSets", rampSets.map { r -> obj { put("load", r.load); range("reps", r.reps) } }); flag("reducedRange", reducedRange)
        put("calibrationCeiling", calibrationCeiling); flag("painReduced", painReduced); if (addedSets != 0) put("addedSets", addedSets)
    }.toString()

    companion object {
        const val VERSION = 1
        fun decode(text: String): ItemDoc {
            val o: JsonObject = Js.parse(text)
            o.checkVersion("workout_exercise", VERSION)
            return ItemDoc(o.enum("role"), o.enum("priority"), o.int("sets"), o.range("reps"), o.enum("unit"), o.bool("perSide"), o.dbl("targetRir"),
                o.bool("lastSetToFailure"), o.dblOrNull("load"), o.dbl("loadFactor"), o.int("restMinSec"), o.int("restDefaultSec"), o.int("restMaxSec"),
                o.bool("calibrating"), o.bool("main"), o.strOrNull("swappedFrom"), o.bool("fromKnownNumber"),
                o.objs("rampSets").map { r -> RampRef(r.dbl("load"), r.range("reps")) }, o.bool("reducedRange"), o.dblOrNull("calibrationCeiling"),
                o.bool("painReduced"), o.int("addedSets", 0))
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
