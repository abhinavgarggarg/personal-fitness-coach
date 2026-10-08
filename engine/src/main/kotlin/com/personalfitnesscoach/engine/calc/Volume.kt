package com.personalfitnesscoach.engine.calc

import com.personalfitnesscoach.engine.model.Exercise
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Muscle
import com.personalfitnesscoach.engine.registry.P

/** Fractional set counting (VOL-002) and volume landmarks (VOL-003, VOL-005, VOL-008). */
object Volume {
    /** Credit per muscle for `hardSets` sets of an exercise: 1.0 primary, 0.5 secondary. */
    fun credit(ex: Exercise, hardSets: Double): Map<Muscle, Double> {
        val out = HashMap<Muscle, Double>()
        for (m in ex.secondary) out[m] = P.VOL_002.secondary * hardSets
        for (m in ex.primary) out[m] = P.VOL_002.primary * hardSets // primary wins on overlap
        return out
    }

    /** Sum of fractional credit across many (exercise, hard sets) entries. */
    fun weekly(entries: List<Pair<Exercise, Double>>): Map<Muscle, Double> {
        val acc = HashMap<Muscle, Double>()
        for ((ex, sets) in entries) for ((m, c) in credit(ex, sets)) acc[m] = (acc[m] ?: 0.0) + c
        return acc
    }

    /** Direct (primary) sets per muscle in one session, for the per-session cap. */
    fun directPerSession(entries: List<Pair<Exercise, Double>>): Map<Muscle, Double> {
        val acc = HashMap<Muscle, Double>()
        for ((ex, sets) in entries) for (m in ex.primary) acc[m] = (acc[m] ?: 0.0) + sets
        return acc
    }

    fun weeklyCap(level: Level): Int = level.pick(P.VOL_003.cap.beginner, P.VOL_003.cap.intermediate, P.VOL_003.cap.advanced)
    fun blockStart(level: Level): Int = level.pick(P.VOL_003.block_start.beginner, P.VOL_003.block_start.intermediate, P.VOL_003.block_start.advanced)
    fun maintenance(level: Level): Int = level.pick(P.VOL_003.maintenance.beginner, P.VOL_003.maintenance.intermediate, P.VOL_003.maintenance.advanced)
    fun perSessionCap(level: Level): Int = level.pick(P.VOL_005.cap.beginner, P.VOL_005.cap.intermediate, P.VOL_005.cap.advanced)
    fun sessionSetCap(level: Level): Int = level.pick(P.VOL_008.cap.beginner, P.VOL_008.cap.intermediate, P.VOL_008.cap.advanced)

    /** VOL-006: next week's target for one muscle. */
    fun nextWeekTarget(level: Level, current: Int, progressed: Boolean, fatigueActive: Boolean): Int {
        if (!progressed || fatigueActive) return minOf(current, weeklyCap(level))
        val step = level.pick(P.VOL_006.step.beginner, P.VOL_006.step.intermediate, P.VOL_006.step.advanced)
        return minOf(current + step.last(), weeklyCap(level))
    }

    /** VOL-004: accessory sets after a fatigue-driven cut (rounded down, at least 1 per kept exercise). */
    fun cutAccessories(sets: Int): Int =
        maxOf(1, Math.floor(sets * (1.0 - P.VOL_004.accessory_reduction_pct / 100.0)).toInt())
}
