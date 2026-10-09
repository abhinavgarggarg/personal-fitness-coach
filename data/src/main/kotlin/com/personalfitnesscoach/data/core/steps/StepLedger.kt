package com.personalfitnesscoach.data.core.steps

import com.personalfitnesscoach.data.core.model.StepCounterRecord
import com.personalfitnesscoach.data.core.time.Days
import java.time.ZoneId

/**
 * Daily steps from the phone's step counter (D-062). The counter is a running total since the phone last restarted and is read
 * when the app opens (no background work), so each new reading's difference is shared across the days it spans in proportion to
 * the time spent in each day. Opening the app once a day keeps every day exact; a longer gap spreads the steps evenly over it.
 */
object StepLedger {
    /** Faster than any sustained walking or running cadence: a larger difference is a sensor glitch and is capped. */
    const val MAX_STEPS_PER_MINUTE = 250

    /**
     * Same boot: the phone's boot count matches when both readings carry it; otherwise time since boot went up and the counter did not
     * go back. The wall clock is never used for this, so changing the phone's time cannot double-count steps.
     */
    fun sameBoot(prev: StepCounterRecord, now: StepCounterRecord): Boolean {
        if (now.counter < prev.counter || now.elapsedMs < prev.elapsedMs) return false
        val a = prev.bootCount; val b = now.bootCount
        return a == null || b == null || a == b
    }

    /** Steps to add per day for a new reading after `prev` (none for the first reading, which only sets the starting point). */
    fun split(prev: StepCounterRecord?, now: StepCounterRecord, zone: ZoneId): List<Pair<Int, Int>> {
        if (prev == null || now.atMs <= prev.atMs) return emptyList()
        val bootNow = now.atMs - now.elapsedMs
        val sameBoot = sameBoot(prev, now)
        // After a restart the counter starts again from 0 at boot; steps between the last reading and the restart are unknown.
        val fromMs = if (sameBoot) prev.atMs else maxOf(prev.atMs, bootNow)
        val raw = if (sameBoot) now.counter - prev.counter else now.counter
        val minutes = (now.atMs - fromMs) / 60_000.0
        if (raw <= 0 || minutes <= 0.0) return emptyList()
        val delta = minOf(raw, Math.round(minutes * MAX_STEPS_PER_MINUTE)).toInt()
        val firstDay = Days.fromInstant(fromMs, zone)
        val lastDay = Days.fromInstant(now.atMs - 1, zone)
        if (firstDay == lastDay) return listOf(firstDay to delta)
        val span = (now.atMs - fromMs).toDouble()
        val out = ArrayList<Pair<Int, Int>>()
        var given = 0
        for (day in firstDay..lastDay) {
            val start = maxOf(fromMs, Days.startMs(day, zone))
            val end = minOf(now.atMs, Days.startMs(day + 1, zone))
            val share = if (day == lastDay) delta - given else Math.floor(delta * (end - start) / span).toInt()
            given += share
            if (share > 0) out += day to share
        }
        return out
    }
}
