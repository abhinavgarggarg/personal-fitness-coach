package com.personalfitnesscoach.engine.dose

import com.personalfitnesscoach.engine.model.Exercise
import com.personalfitnesscoach.engine.model.Muscle
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.registry.P

/** ORD-001 session steps, in their default order. */
enum class OrderSlot { WARMUP, POWER, PRIMARY_COMPOUND, SECONDARY_COMPOUND, ACCESSORIES, CORE, CONDITIONING, COOLDOWN }

/** What an exercise can be paired with in a superset (ORD-003). ANY = unknown (muscle check only), NONE = never paired. */
enum class PairClass { ANY, PUSH, PULL, LOWER, CORE, NONE }

/** ORD-001 to ORD-003 (and CON-001: strength before conditioning unless conditioning has priority today). */
object Order {
    /** ORD-001 sequence; on a conditioning-priority day conditioning moves to step 3 (ORD-002, CON-001 exception). */
    fun sequence(conditioningPriority: Boolean): List<OrderSlot> {
        val base = P.ORD_001.sequence.map { OrderSlot.valueOf(it.uppercase()) }
        if (!conditioningPriority) return base
        val rest = base - OrderSlot.CONDITIONING
        val i = rest.indexOf(OrderSlot.PRIMARY_COMPOUND)
        return rest.subList(0, i) + OrderSlot.CONDITIONING + rest.subList(i, rest.size)
    }

    /**
     * Stable sort into ORD-001 order. A weak-point accessory may move ahead of the secondary
     * compounds (ORD-002); ties keep their input order.
     */
    fun <T> sort(items: List<T>, slotOf: (T) -> OrderSlot, conditioningPriority: Boolean = false, weakPoint: (T) -> Boolean = { false }): List<T> {
        val seq = sequence(conditioningPriority)
        fun rank(t: T): Double {
            val s = slotOf(t)
            val r = seq.indexOf(s).toDouble()
            return if (s == OrderSlot.ACCESSORIES && weakPoint(t)) seq.indexOf(OrderSlot.SECONDARY_COMPOUND) - 0.5 else r
        }
        return items.withIndex().sortedWith(compareBy({ rank(it.value) }, { it.index })).map { it.value }
    }

    /** ORD-002: lower-body strength volume on a conditioning-priority day (×0.7). */
    val conditioningPriorityLowerVolumeFactor: Double
        get() = 1.0 - P.ORD_002.conditioning_priority_lower_volume_reduction_pct / 100.0

    private val PUSH_MUSCLES = setOf(Muscle.CHEST, Muscle.TRICEPS, Muscle.FRONT_DELTS, Muscle.SIDE_DELTS)
    private val PULL_MUSCLES = setOf(Muscle.LATS, Muscle.UPPER_BACK, Muscle.REAR_DELTS, Muscle.BICEPS, Muscle.FOREARMS)
    private val LOWER_MUSCLES = setOf(Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.CALVES, Muscle.ADDUCTORS)

    fun pairClass(ex: Exercise): PairClass = when {
        ex.pattern == Pattern.LOADED_CARRY -> PairClass.NONE
        ex.pattern.coreCategory || ex.pattern == Pattern.ROTATION || ex.primary == setOf(Muscle.CORE) -> PairClass.CORE
        ex.pattern.isPush -> PairClass.PUSH
        ex.pattern.isPull -> PairClass.PULL
        ex.pattern == Pattern.SQUAT || ex.pattern == Pattern.HINGE || ex.pattern == Pattern.LUNGE -> PairClass.LOWER
        ex.primary.any { it in LOWER_MUSCLES } -> PairClass.LOWER
        ex.primary.any { it in PUSH_MUSCLES } -> PairClass.PUSH
        ex.primary.any { it in PULL_MUSCLES } -> PairClass.PULL
        else -> PairClass.NONE
    }

    /** ORD-003 allowed pairs: push+pull, upper+lower, compound+core. */
    fun classesCompatible(a: PairClass, b: PairClass): Boolean {
        if (a == PairClass.NONE || b == PairClass.NONE) return false
        if (a == PairClass.ANY || b == PairClass.ANY) return true
        val s = setOf(a, b)
        return s == setOf(PairClass.PUSH, PairClass.PULL) ||
            (PairClass.LOWER in s && (PairClass.PUSH in s || PairClass.PULL in s)) ||
            (PairClass.CORE in s && s.size == 2)
    }

    /** Stations you can carry your kit to, so a pair never needs two fixed stations. */
    val PORTABLE_STATIONS = setOf("floor", "dumbbells", "kettlebells", "bands", "medicine_ball", "mat", "sliders", "ab_wheel", "jump_rope")

    fun portable(station: String): Boolean = station.isEmpty() || station in PORTABLE_STATIONS

    /** ORD-003 / EQ-002: one station or a portable partner; in a crowded gym never two fixed stations. */
    fun stationsCompatible(a: String, b: String, crowded: Boolean): Boolean =
        !crowded || a == b || portable(a) || portable(b)

    /** ORD-003: heavy main lifts at RIR ≤ 2 are never supersetted. */
    fun supersetEligible(main: Boolean, targetRir: Double): Boolean =
        !(main && targetRir <= P.ORD_003.forbid_main_lift_rir_lte + 1e-9)
}
