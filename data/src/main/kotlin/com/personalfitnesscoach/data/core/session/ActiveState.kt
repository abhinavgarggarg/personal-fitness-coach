package com.personalfitnesscoach.data.core.session

import com.personalfitnesscoach.data.core.json.DataFormatException
import com.personalfitnesscoach.data.core.json.Js
import com.personalfitnesscoach.data.core.json.enum
import com.personalfitnesscoach.data.core.json.longOrNull
import com.personalfitnesscoach.data.core.json.obj
import com.personalfitnesscoach.data.core.json.objOrNull
import com.personalfitnesscoach.data.core.json.intOrNull
import com.personalfitnesscoach.data.core.json.strOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** The parts of a workout, in the order they are done (Phase 2 FS-4: warm-up → … → cool-down). */
enum class Stage { WARMUP, BONE_LOADING, CONDITIONING_FIRST, LIFTS, CONDITIONING, BALANCE, COOLDOWN,
    /** Every part is done: the summary is next. */
    END }

/** A sheet open over the workout (Phase 2 state ACTIVE.SHEET), so a resumed session reopens it. */
enum class Sheet { REPLACE, SOMETHING_HURTS, CHANGE_TIME, RED_FLAG }

/**
 * The resume point of the active session (Phase 2 ActiveSessionState, saved on every change, FS-4). Everything the player needs that is
 * not already a logged set or a stored dose: where the user is, the in-session load changes (INT-007, CAL-001 ramp steps), the rest
 * timer and pauses. Durable choices (a pain-gate load reduction, a re-fit, a swap) are written to the exercise's dose instead, so a
 * reset of this state (a restore resets it) never undoes a safety change.
 *
 * Times are wall-clock milliseconds (they survive a restart); the running app counts down on the elapsed-time clock (Phase 2 section 11).
 */
data class ActiveState(
    val stage: Stage = Stage.WARMUP,
    /** The workout exercise (row ID) being shown in the lifts stage; null = the first one not finished. */
    val currentRowId: Long? = null,
    /** Load for the next set per workout exercise, after INT-007 or a CAL-001 ramp step (kg). */
    val nextLoads: Map<Long, Double> = emptyMap(),
    /** CAL-001: the ramp finished (found, stopped or capped) for these workout exercises today. */
    val calibrationDone: Set<Long> = emptySet(),
    /** WU-002 ramp-up sets the user skipped, per workout exercise. */
    val rampSkipped: Set<Long> = emptySet(),
    /** Rest: when it started and how long it is (seconds); null when not resting. */
    val restStartedAtMs: Long? = null,
    val restSec: Int? = null,
    val sheet: Sheet? = null,
    val pausedAtMs: Long? = null,
    /** Total paused time, removed from the session's minutes (LOAD-001: pauses over 10 minutes don't count). */
    val pausedMs: Long = 0,
    /** When the last hard set or interval finished (LOAD-002: the session rating is asked 10+ minutes after it). */
    val lastHardEffortAtMs: Long? = null,
    /** Conditioning block index → work minutes done (logged when the block ends; 0 = skipped or dropped by a re-plan). */
    val conditioningDone: Map<Int, Double> = emptyMap(),
    /** The last tap that changed the workout: a gap with no tap longer than the stage allows is not session time (LOAD-001, review R5-05). */
    val lastActionAtMs: Long? = null,
) {
    init {
        require(nextLoads.values.all { it in 0.0..1000.0 }) { "load out of range" }
        require(restSec == null || restSec in 0..3600) { "rest out of range" }
        require(pausedMs >= 0) { "pause out of range" }
        require(conditioningDone.values.all { it in 0.0..600.0 }) { "conditioning minutes out of range" }
    }

    fun resting(nowMs: Long): Boolean = restStartedAtMs != null && restSec != null && nowMs < restStartedAtMs + restSec * 1000L

    fun restEndsAtMs(): Long? = if (restStartedAtMs != null && restSec != null) restStartedAtMs + restSec * 1000L else null

    fun encode(): String = obj {
        put("v", VERSION); put("stage", stage); put("currentRowId", currentRowId)
        if (nextLoads.isNotEmpty()) put("nextLoads", JsonObject(nextLoads.toSortedMap().map { (k, v) -> k.toString() to JsonPrimitive(v) }.toMap()))
        if (calibrationDone.isNotEmpty()) put("calibrationDone", kotlinx.serialization.json.JsonArray(calibrationDone.sorted().map { JsonPrimitive(it) }))
        if (rampSkipped.isNotEmpty()) put("rampSkipped", kotlinx.serialization.json.JsonArray(rampSkipped.sorted().map { JsonPrimitive(it) }))
        put("restStartedAtMs", restStartedAtMs); put("restSec", restSec); put("sheet", sheet); put("pausedAtMs", pausedAtMs)
        if (pausedMs != 0L) put("pausedMs", pausedMs); put("lastHardEffortAtMs", lastHardEffortAtMs)
        if (conditioningDone.isNotEmpty()) put("conditioningDone", JsonObject(conditioningDone.toSortedMap().map { (k, v) -> k.toString() to JsonPrimitive(v) }.toMap()))
        put("lastActionAtMs", lastActionAtMs)
    }.toString()

    companion object {
        const val VERSION = 1

        private fun longs(o: JsonObject, k: String): Set<Long> =
            (o[k] as? kotlinx.serialization.json.JsonArray)?.map { (it as? JsonPrimitive)?.content?.toLongOrNull() ?: throw DataFormatException("bad id") }?.toSet() ?: emptySet()

        /**
         * Reads the saved state. An empty object (a new session, or one reset by a restore) is the start; anything unreadable also starts
         * over rather than blocking the workout — the logged sets and doses are the durable truth.
         */
        fun decode(text: String): ActiveState {
            val o = try { Js.parse(text) } catch (e: DataFormatException) { return ActiveState() }
            if (o.isEmpty() || o.intOrNull("v") == null) return ActiveState()
            return try {
                ActiveState(
                    stage = o.enum("stage", Stage.WARMUP),
                    currentRowId = o.longOrNull("currentRowId"),
                    nextLoads = (o.objOrNull("nextLoads") ?: JsonObject(emptyMap())).entries.associate { (k, v) ->
                        (k.toLongOrNull() ?: throw DataFormatException("bad key")) to ((v as? JsonPrimitive)?.doubleOrNull ?: throw DataFormatException("bad load")) },
                    calibrationDone = longs(o, "calibrationDone"),
                    rampSkipped = longs(o, "rampSkipped"),
                    restStartedAtMs = o.longOrNull("restStartedAtMs"), restSec = o.intOrNull("restSec"),
                    sheet = o.strOrNull("sheet")?.let { n -> Sheet.entries.firstOrNull { it.name == n } },
                    pausedAtMs = o.longOrNull("pausedAtMs"), pausedMs = o.longOrNull("pausedMs") ?: 0L,
                    lastHardEffortAtMs = o.longOrNull("lastHardEffortAtMs"),
                    conditioningDone = (o.objOrNull("conditioningDone") ?: JsonObject(emptyMap())).entries.associate { (k, v) ->
                        (k.toIntOrNull() ?: throw DataFormatException("bad key")) to ((v as? JsonPrimitive)?.doubleOrNull ?: throw DataFormatException("bad minutes")) },
                    lastActionAtMs = o.longOrNull("lastActionAtMs"),
                )
            } catch (e: DataFormatException) {
                ActiveState()
            } catch (e: IllegalArgumentException) {
                ActiveState()
            }
        }
    }
}
