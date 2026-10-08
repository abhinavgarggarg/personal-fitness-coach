package com.personalfitnesscoach.engine.calc

import com.personalfitnesscoach.engine.model.Exercise
import com.personalfitnesscoach.engine.model.LoadType
import com.personalfitnesscoach.engine.registry.P

/** A machine weight stack: lowest load, highest load and step. */
data class Stack(val minKg: Double, val maxKg: Double, val stepKg: Double)

/**
 * The user's real equipment (EquipmentItem increments). Plate counts are totals,
 * so 2 × 1.25 kg means one pair.
 */
data class Inventory(
    val barKg: Double = 20.0,
    val plates: Map<Double, Int> = mapOf(25.0 to 4, 20.0 to 2, 15.0 to 2, 10.0 to 2, 5.0 to 2, 2.5 to 2, 1.25 to 2),
    val dumbbells: List<Double> = (1..20).map { it * 2.5 },
    val kettlebells: List<Double> = listOf(8.0, 12.0, 16.0, 20.0, 24.0, 28.0, 32.0),
    val stack: Stack = Stack(5.0, 100.0, 5.0),
    /** Other bars by library bar ID; the standard barbell uses [barKg]. */
    val bars: Map<String, Double> = mapOf("ez_bar" to 10.0, "trap_bar" to 25.0),
    /** Machine- or cable-specific stacks by equipment ID; anything not listed uses [stack]. */
    val stacks: Map<String, Stack> = emptyMap(),
)

/** Rounding to loads that really exist in the gym (PROG-003). */
object PlateMath {
    /** Every achievable load for a load type, sorted ascending. */
    fun loads(type: LoadType, inv: Inventory): List<Double> = when (type) {
        LoadType.BARBELL -> barbellLoads(inv)
        LoadType.DUMBBELL -> inv.dumbbells.sorted().distinct()
        LoadType.KETTLEBELL -> inv.kettlebells.sorted().distinct()
        LoadType.STACK -> generateSequence(inv.stack.minKg) { it + inv.stack.stepKg }
            .takeWhile { it <= inv.stack.maxKg + 1e-9 }.map { round(it) }.toList()
        LoadType.BODYWEIGHT, LoadType.TIME, LoadType.DISTANCE -> emptyList()
    }

    /**
     * Every achievable load for one exercise: the right bar (EZ, trap bar), one loaded end for a
     * landmine (plates only, at least one plate), or that machine's own stack.
     */
    fun loadsFor(ex: Exercise, inv: Inventory): List<Double> = when (ex.loadType) {
        LoadType.BARBELL -> when (val bar = ex.bar ?: "barbell") {
            "barbell" -> barbellLoads(inv)
            "landmine" -> plateSums(inv, perPair = false).filter { it > 0L }.map { round(it / 20.0) }.sorted()
            else -> barbellLoads(inv, inv.bars[bar] ?: inv.barKg)
        }
        LoadType.STACK -> {
            val s = ex.equipment.sorted().firstNotNullOfOrNull { inv.stacks[it] } ?: inv.stack
            loads(LoadType.STACK, inv.copy(stack = s))
        }
        else -> loads(ex.loadType, inv)
    }

    private fun barbellLoads(inv: Inventory, barKg: Double = inv.barKg): List<Double> =
        plateSums(inv, perPair = true).map { round(barKg + 2.0 * it / 20.0) }.sorted()

    /** Achievable per-side plate totals (pairs) or one-end totals (single plates), in 0.05 kg units. */
    private fun plateSums(inv: Inventory, perPair: Boolean): Set<Long> {
        var sides = setOf(0L)
        for ((plate, count) in inv.plates) {
            val n = if (perPair) count / 2 else count
            val unit = Math.round(plate * 20)
            val next = HashSet<Long>()
            for (s in sides) for (k in 0..n) next.add(s + k * unit)
            sides = next
        }
        return sides
    }

    /**
     * Closest available load among those no more than 2% above the target; when none is
     * at or below that limit, the lightest available load.
     */
    fun choose(target: Double, available: List<Double>): Double {
        if (available.isEmpty()) return round(target)
        val limit = target * (1.0 + P.PROG_003.over_target_tolerance_pct / 100.0)
        val eligible = available.filter { it <= limit + 1e-9 }
        if (eligible.isEmpty()) return available.minOrNull()!!
        return eligible.minByOrNull { Math.abs(it - target) }!!
    }

    /** Smallest available load strictly above `current`, or null at the top of the range. */
    fun nextAbove(current: Double, available: List<Double>): Double? =
        available.filter { it > current + 1e-9 }.minOrNull()

    /** Smallest available load at or above `min`. */
    fun firstAtLeast(min: Double, available: List<Double>): Double? =
        available.filter { it >= min - 1e-9 }.minOrNull()

    fun round(x: Double): Double = Math.round(x * 100.0) / 100.0
}
