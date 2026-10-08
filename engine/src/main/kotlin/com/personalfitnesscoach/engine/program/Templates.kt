package com.personalfitnesscoach.engine.program

import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Muscle
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.program.Blueprint.Exposure
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds

/** SCH-001 session types. */
enum class DayTemplate(val strength: Boolean, val hard: Boolean, val heavyLower: Boolean) {
    FB_A(true, true, true), FB_B(true, true, true), FB_C(true, true, false),
    UPPER_H(true, true, false), LOWER_H(true, true, true), UPPER_M(true, true, false), LOWER_M(true, true, true),
    COND_CORE(false, true, false), COND(false, true, false), EASY_AEROBIC_MOBILITY(false, false, false),
}

enum class SlotRole { POWER, MAIN, SECONDARY, ACCESSORY, CORE, CARRY, ROTATION, CARRY_OR_ROTATION }

/** One slot of a session template: the pattern (or isolation muscle) to fill and how hard. */
data class SlotSpec(
    val key: String,
    val role: SlotRole,
    val pattern: Pattern?,
    val exposure: Exposure = Exposure.MODERATE,
    val muscle: Muscle? = null,
)

/** SCH-001 templates and SCH-002 day assignment. */
object Templates {
    private fun main(k: String, p: Pattern, e: Exposure = Exposure.HEAVY) = SlotSpec(k, SlotRole.MAIN, p, e)
    private fun sec(k: String, p: Pattern) = SlotSpec(k, SlotRole.SECONDARY, p, Exposure.MODERATE)
    private fun acc(k: String, p: Pattern) = SlotSpec(k, SlotRole.ACCESSORY, p, Exposure.MODERATE)
    private fun iso(k: String, m: Muscle) = SlotSpec(k, SlotRole.ACCESSORY, Pattern.ISOLATION, Exposure.MODERATE, m)
    private fun core(k: String, p: Pattern) = SlotSpec(k, SlotRole.CORE, p)
    private fun power(k: String) = SlotSpec(k, SlotRole.POWER, null, Exposure.HEAVY)

    /** SCH-001: the session types for each number of days, in canonical order. */
    fun forDays(days: Int): List<DayTemplate> = when (days) {
        2 -> P.SCH_001._2.map { DayTemplate.valueOf(it) }
        3 -> P.SCH_001._3.map { DayTemplate.valueOf(it) }
        4 -> P.SCH_001._4.map { DayTemplate.valueOf(it) }
        // 5 and 6 days: the first upper/lower pair is the heavy exposure, the second the moderate one (DUP).
        5 -> listOf(DayTemplate.UPPER_H, DayTemplate.LOWER_H, DayTemplate.COND_CORE, DayTemplate.UPPER_M, DayTemplate.LOWER_M)
        6 -> listOf(DayTemplate.UPPER_H, DayTemplate.LOWER_H, DayTemplate.COND, DayTemplate.UPPER_M, DayTemplate.LOWER_M, DayTemplate.EASY_AEROBIC_MOBILITY)
        else -> throw IllegalArgumentException("2–6 training days (FREQ-001)")
    }

    /**
     * Slots per session type. With 2 days both sessions are full body and cover all seven lifting
     * patterns and three core categories; carry and rotation alternate weeks (PAT-001). From 3 days
     * squat, hinge and horizontal push/pull appear at least twice (heavier and lighter exposures).
     */
    fun slots(t: DayTemplate, days: Int, withPower: Boolean, secondPower: Boolean): List<SlotSpec> {
        val p = if (withPower) listOf(power("${t.name}.power")) else emptyList()
        val p2 = if (secondPower) listOf(power("${t.name}.power")) else emptyList()
        return when (t) {
            DayTemplate.FB_A -> if (days == 2) p + listOf(main("A.squat", Pattern.SQUAT), main("A.hpush", Pattern.HORIZONTAL_PUSH), sec("A.hpull", Pattern.HORIZONTAL_PULL),
                sec("A.hinge", Pattern.HINGE), acc("A.vpull", Pattern.VERTICAL_PULL), core("A.antiext", Pattern.ANTI_EXTENSION), SlotSpec("A.carryrot", SlotRole.CARRY_OR_ROTATION, null))
            else p + listOf(main("A.squat", Pattern.SQUAT), main("A.hpush", Pattern.HORIZONTAL_PUSH), sec("A.hpull", Pattern.HORIZONTAL_PULL),
                acc("A.vpull", Pattern.VERTICAL_PULL), core("A.antiext", Pattern.ANTI_EXTENSION), SlotSpec("A.carry", SlotRole.CARRY, Pattern.LOADED_CARRY))
            DayTemplate.FB_B -> if (days == 2) p2 + listOf(main("B.hinge", Pattern.HINGE), main("B.hpull", Pattern.HORIZONTAL_PULL), sec("B.lunge", Pattern.LUNGE),
                sec("B.vpush", Pattern.VERTICAL_PUSH), acc("B.hpush", Pattern.HORIZONTAL_PUSH), core("B.antirot", Pattern.ANTI_ROTATION), core("B.antilat", Pattern.ANTI_LATERAL_FLEXION))
            else p2 + listOf(main("B.hinge", Pattern.HINGE), main("B.hpull", Pattern.HORIZONTAL_PULL), sec("B.vpush", Pattern.VERTICAL_PUSH),
                sec("B.lunge", Pattern.LUNGE), core("B.antirot", Pattern.ANTI_ROTATION))
            DayTemplate.FB_C -> listOf(sec("C.squat", Pattern.SQUAT), sec("C.hinge", Pattern.HINGE), sec("C.hpush", Pattern.HORIZONTAL_PUSH),
                sec("C.vpull", Pattern.VERTICAL_PULL), acc("C.hpull", Pattern.HORIZONTAL_PULL), core("C.antilat", Pattern.ANTI_LATERAL_FLEXION),
                SlotSpec("C.rotation", SlotRole.ROTATION, Pattern.ROTATION))
            DayTemplate.UPPER_H -> listOf(main("UH.hpush", Pattern.HORIZONTAL_PUSH), main("UH.hpull", Pattern.HORIZONTAL_PULL), sec("UH.vpush", Pattern.VERTICAL_PUSH),
                sec("UH.vpull", Pattern.VERTICAL_PULL), iso("UH.side", Muscle.SIDE_DELTS), iso("UH.biceps", Muscle.BICEPS), core("UH.antirot", Pattern.ANTI_ROTATION))
            DayTemplate.LOWER_H -> p + listOf(main("LH.squat", Pattern.SQUAT), sec("LH.hinge", Pattern.HINGE), sec("LH.lunge", Pattern.LUNGE),
                iso("LH.hams", Muscle.HAMSTRINGS), iso("LH.calves", Muscle.CALVES), core("LH.antiext", Pattern.ANTI_EXTENSION), SlotSpec("LH.carry", SlotRole.CARRY, Pattern.LOADED_CARRY))
            DayTemplate.UPPER_M -> listOf(main("UM.vpush", Pattern.VERTICAL_PUSH), main("UM.vpull", Pattern.VERTICAL_PULL), sec("UM.hpush", Pattern.HORIZONTAL_PUSH),
                sec("UM.hpull", Pattern.HORIZONTAL_PULL), iso("UM.triceps", Muscle.TRICEPS), iso("UM.rear", Muscle.REAR_DELTS), core("UM.antilat", Pattern.ANTI_LATERAL_FLEXION))
            DayTemplate.LOWER_M -> p2 + listOf(main("LM.hinge", Pattern.HINGE), sec("LM.squat", Pattern.SQUAT), sec("LM.lunge", Pattern.LUNGE),
                iso("LM.quads", Muscle.QUADS), SlotSpec("LM.rotation", SlotRole.ROTATION, Pattern.ROTATION))
            DayTemplate.COND_CORE -> listOf(core("CC.antirot", Pattern.ANTI_ROTATION), core("CC.antilat", Pattern.ANTI_LATERAL_FLEXION))
            DayTemplate.COND, DayTemplate.EASY_AEROBIC_MOBILITY -> emptyList()
        }
    }

    // ------------------------------------------------------------------ SCH-002 day assignment

    data class Assignment(val days: List<Int>, val order: List<DayTemplate>, val penalty: Double, val spacingOk: Boolean)

    private fun permutations(items: List<DayTemplate>): List<List<DayTemplate>> {
        if (items.size <= 1) return listOf(items)
        val out = LinkedHashSet<List<DayTemplate>>()
        for (i in items.indices) for (rest in permutations(items.filterIndexed { j, _ -> j != i })) out += listOf(items[i]) + rest
        return out.toList()
    }

    private fun subsets(pool: List<Int>, k: Int): List<List<Int>> {
        if (k == 0) return listOf(emptyList())
        if (pool.size < k) return emptyList()
        val first = pool.first()
        return subsets(pool.drop(1), k - 1).map { listOf(first) + it } + subsets(pool.drop(1), k)
    }

    /** Hours-style gap in days between two weekdays going forward, wrapping around the week. */
    private fun gap(a: Int, b: Int) = ((b - a) % 7 + 7) % 7

    /** Penalty of one candidate week; spacingOk = no hard SCH-002 violation. */
    fun score(days: List<Int>, order: List<DayTemplate>, canonical: List<DayTemplate>, preferred: Set<Int>, level: Level): Pair<Double, Boolean> {
        var pen = 0.0
        var ok = true
        pen += days.count { it !in preferred } * 10.0
        // Heavy lower-body sessions ≥ 48 h apart, also across the weekend.
        val heavy = days.zip(order).filter { it.second.heavyLower }.map { it.first }
        for (i in heavy.indices) for (j in heavy.indices) if (i != j && gap(heavy[i], heavy[j]) in 1 until P.SCH_002.heavy_lower_min_h / 24) { pen += 1000.0; ok = false }
        // Advanced lifters: heavy squat and heavy hinge days ≥ 72 h apart preferred.
        if (level == Level.ADVANCED && heavy.size >= 2) {
            val minGap = heavy.flatMap { a -> heavy.filter { it != a }.map { gap(a, it) } }.minOrNull() ?: 7
            if (minGap < P.SCH_002.advanced_pref_h / 24) pen += 5.0
        }
        // No more than 3 consecutive hard days (wrapping).
        val hardDays = days.zip(order).filter { it.second.hard }.map { it.first }.toSet()
        var run = 0; var maxRun = 0
        for (d in 0 until 14) { if ((d % 7) in hardDays) { run++; maxRun = maxOf(maxRun, run) } else run = 0 }
        if (hardDays.size == 7) maxRun = 7
        if (maxRun > P.SCH_002.max_consecutive_hard_days) { pen += 1000.0 * (maxRun - P.SCH_002.max_consecutive_hard_days); ok = false }
        // Prefer alternating hard and easier days, and even spacing.
        for (d in hardDays) if (((d + 1) % 7) in hardDays) pen += 1.0
        val sorted = days.sorted()
        val ideal = 7.0 / days.size
        for (i in sorted.indices) { val g = gap(sorted[i], sorted[(i + 1) % sorted.size]).let { if (it == 0) 7 else it }; pen += 0.5 * (g - ideal) * (g - ideal) }
        // Stay close to the canonical session order.
        pen += order.indices.count { order[it] != canonical[it] } * 3.0
        return pen to ok
    }

    /** Longest run of hard days in a row, wrapping round the week (SCH-002). */
    fun maxHardRun(days: List<Int>, order: List<DayTemplate>): Int {
        val hard = days.zip(order).filter { it.second.hard }.map { it.first }.toSet()
        if (hard.size >= 7) return 7
        var run = 0; var max = 0
        for (d in 0 until 14) { if ((d % 7) in hard) { run++; max = maxOf(max, run) } else run = 0 }
        return max
    }

    /**
     * SCH-002: indices (into `order`) of heavy lower-body days that come less than 48 h after another
     * heavy lower-body day. The planner turns them into moderate lower-body days.
     */
    fun heavyConflicts(days: List<Int>, order: List<DayTemplate>): Set<Int> {
        val out = LinkedHashSet<Int>()
        val heavy = days.indices.filter { order[it].heavyLower }.sortedBy { days[it] }
        for (a in heavy) for (b in heavy) if (a != b && b !in out && a !in out) {
            if (gap(days[a], days[b]) in 1 until P.SCH_002.heavy_lower_min_h / 24) out += b
        }
        return out
    }

    /**
     * SCH-002: choose the weekdays (0 = Monday) and the session order. `available` days are hard
     * limits; `preferred` days are favoured. 2–4-day templates keep their order; 5–6-day
     * templates may be reordered so no more than 3 hard days run back to back.
     */
    fun assign(templates: List<DayTemplate>, available: Set<Int>, preferred: Set<Int>, level: Level): EngineResult<Assignment> {
        val pool = available.filter { it in 0..6 }.sorted().ifEmpty { (0..6).toList() }
        val k = minOf(templates.size, pool.size)
        val canonical = templates.take(k)
        val orders = if (templates.size >= 5) permutations(canonical) else listOf(canonical)
        var best: Assignment? = null
        for (days in subsets(pool, k)) for (order in orders) {
            val (pen, ok) = score(days, order, canonical, preferred, level)
            if (best == null || pen < best.penalty - 1e-9) best = Assignment(days, order, pen, ok)
        }
        val a = best!!
        return EngineResult(a, listOf(Decision(DecisionKind.VOLUME_CHANGE, listOf(RuleIds.SCH_001, RuleIds.SCH_002),
            if (a.spacingOk) ReasonKey.WEEK_PLANNED else ReasonKey.DAY_SPACING_ADJUSTED,
            inputs = mapOf("templates" to templates.map { it.name }, "available" to pool, "preferred" to preferred.sorted()),
            outputs = mapOf("days" to a.days, "order" to a.order.map { it.name }, "spacingOk" to a.spacingOk))))
    }
}
