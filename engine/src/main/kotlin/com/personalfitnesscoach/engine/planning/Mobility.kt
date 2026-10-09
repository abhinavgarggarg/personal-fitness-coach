package com.personalfitnesscoach.engine.planning

import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.library.Drill
import com.personalfitnesscoach.engine.library.DrillKind
import com.personalfitnesscoach.engine.library.GeneratedLibrary
import com.personalfitnesscoach.engine.library.Region
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.Exercise
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Muscle
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds

/** A drill with its prescribed dose: `sets` × `amount` (reps or seconds, per side when the drill is). */
data class DrillDose(val drill: Drill, val sets: Int, val amount: Int) {
    val seconds: Int get() = sets * ((if (drill.unit == DoseUnit.SECONDS) amount else amount * 3) * (if (drill.perSide) 2 else 1) + 10)
}

/** MOB-004: lifts run through full range unless the pain gate allows only a shorter range today. */
enum class RangeOfMotion { FULL, REDUCED }

/** MOB-001 to MOB-005. All selections are deterministic (ties by drill ID). */
object Mobility {
    private val drills: List<Drill> get() = GeneratedLibrary.drills

    fun regionsOf(m: Muscle): Set<Region> = when (m) {
        Muscle.CHEST -> setOf(Region.CHEST); Muscle.LATS -> setOf(Region.LATS); Muscle.UPPER_BACK -> setOf(Region.UPPER_BACK)
        Muscle.FRONT_DELTS, Muscle.SIDE_DELTS, Muscle.REAR_DELTS -> setOf(Region.SHOULDERS)
        Muscle.QUADS -> setOf(Region.QUADS, Region.HIP_FLEXORS); Muscle.HAMSTRINGS -> setOf(Region.HAMSTRINGS)
        Muscle.GLUTES -> setOf(Region.GLUTES, Region.HIPS); Muscle.CALVES -> setOf(Region.CALVES); Muscle.ADDUCTORS -> setOf(Region.ADDUCTORS)
        Muscle.CORE -> setOf(Region.SPINE); Muscle.BICEPS, Muscle.TRICEPS, Muscle.FOREARMS -> emptySet()
    }

    fun regionsOf(j: Joint): Set<Region> = when (j) {
        Joint.SHOULDER -> setOf(Region.SHOULDERS); Joint.ELBOW -> emptySet(); Joint.WRIST -> setOf(Region.WRISTS)
        Joint.SPINE -> setOf(Region.SPINE, Region.UPPER_BACK); Joint.HIP -> setOf(Region.HIPS); Joint.KNEE -> setOf(Region.KNEES, Region.QUADS)
        Joint.ANKLE -> setOf(Region.ANKLES)
    }

    private fun usable(d: Drill, equipment: Set<String>, limits: Map<Joint, Int>, avoidTags: Set<String>) =
        (equipment.containsAll(d.equipment) || d.equipment.all { it == "mat" }) && limits.all { (j, lim) -> d.stress(j) <= lim } &&
            d.tags.none { it in avoidTags }

    /**
     * MOB-001: 3–5 minutes of dynamic drills for the session's patterns, inside the warm-up.
     * Restricted-joint drills go first (ORD-002); a balance drill is added at 65+ (WU-004).
     */
    fun warmupDrills(patterns: Set<Pattern>, equipment: Set<String>, restricted: Set<Joint> = emptySet(), age: Int? = null,
                     jointLimits: Map<Joint, Int>, avoidTags: Set<String>): EngineResult<List<DrillDose>> {
        val maxSec = P.MOB_001.minutes[1] * 60
        val minSec = P.MOB_001.minutes[0] * 60
        // Pain limits apply to drills too (SAF-003): a drill never loads a joint beyond today's limit.
        val pool = drills.filter { (it.kind == DrillKind.MOBILISE || it.kind == DrillKind.ACTIVATE) && usable(it, equipment, jointLimits, avoidTags) }
        val chosen = ArrayList<Drill>()
        // Restricted joints first.
        val restrictedRegions = restricted.flatMap { regionsOf(it) }.toSet()
        pool.filter { d -> d.regions.any { it in restrictedRegions } }
            .sortedWith(compareBy<Drill>({ d -> restricted.sumOf { d.stress(it) } }, { it.id })).take(1).forEach { chosen += it }
        // Then greedy cover of the session's patterns: most uncovered patterns first, ties by ID.
        val uncovered = patterns.toMutableSet()
        uncovered.removeAll(chosen.flatMap { it.prepares }.toSet())
        while (uncovered.isNotEmpty()) {
            val best = pool.filter { it !in chosen }.maxWithOrNull(compareBy<Drill> { d -> d.prepares.count { it in uncovered } }.thenByDescending { it.id }) ?: break
            if (best.prepares.none { it in uncovered }) break
            if (chosen.sumOf { it.seconds } + best.seconds > maxSec) break
            chosen += best; uncovered.removeAll(best.prepares)
        }
        // Fill to the 3-minute minimum with drills that prepare any of today's patterns.
        for (d in pool.filter { it !in chosen && it.prepares.any { p -> p in patterns } }.sortedBy { it.id }) {
            if (chosen.sumOf { it.seconds } >= minSec) break
            if (chosen.sumOf { it.seconds } + d.seconds <= maxSec) chosen += d
        }
        val out = chosen.map { DrillDose(it, 1, it.amount) }.toMutableList()
        if (age != null && age >= 65 && P.WU_004.age_65_balance_drill) {
            drills.filter { it.kind == DrillKind.BALANCE && usable(it, equipment, jointLimits, avoidTags) }.minByOrNull { it.id }?.let { out += DrillDose(it, 1, it.amount) }
        }
        return EngineResult(out, listOf(Decision(DecisionKind.WARMUP, listOf(RuleIds.MOB_001) + if (restricted.isNotEmpty()) listOf(RuleIds.ORD_002) else emptyList(),
            ReasonKey.MOBILITY_PLANNED, inputs = mapOf("patterns" to patterns.map { it.name }.sorted()), outputs = mapOf("drills" to out.map { it.drill.id }))))
    }

    /** MOB-002: optional static holds before training are capped at 30 s and followed by dynamic work. */
    fun preWorkoutStaticSeconds(requested: Int): Int = minOf(requested, P.MOB_002.pre_max_seconds_per_muscle)

    /**
     * MOB-002 cool-down: 30–60 s stretches for the muscles trained, then 1–2 minutes of slow
     * breathing. `minutes` is the cool-down budget (≥ 2, TIME-001).
     */
    fun cooldown(trained: Set<Muscle>, minutes: Double, equipment: Set<String> = emptySet(), jointLimits: Map<Joint, Int>,
                 avoidTags: Set<String>): EngineResult<List<DrillDose>> {
        val breathing = drills.first { it.kind == DrillKind.BREATHING }
        val minBreath = P.MOB_002.breathing_minutes[0] * 60
        val maxBreath = P.MOB_002.breathing_minutes[1] * 60
        val budget = (minutes * 60).toInt()
        val holdSec = P.MOB_002.post_seconds_per_muscle[0]
        val regions = trained.flatMap { regionsOf(it) }.toSet()
        val out = ArrayList<DrillDose>()
        var used = 0
        // Stretches first, keeping room for at least 1 minute of breathing (+10 s change-over).
        for (d in drills.filter { it.kind == DrillKind.STRETCH && usable(it, equipment, jointLimits, avoidTags) && it.regions.any { r -> r in regions } }
            .sortedWith(compareByDescending<Drill> { d -> d.regions.count { it in regions } }.thenBy { it.id })) {
            val dose = DrillDose(d, 1, holdSec)
            if (used + dose.seconds > budget - minBreath - 10) continue
            out += dose; used += dose.seconds
        }
        out += DrillDose(breathing, 1, (budget - used - 10).coerceIn(minBreath, maxBreath))
        return EngineResult(out, listOf(Decision(DecisionKind.WARMUP, listOf(RuleIds.MOB_002), ReasonKey.MOBILITY_PLANNED,
            outputs = mapOf("cooldown" to out.map { it.drill.id }))))
    }

    /**
     * MOB-003: an optional drill for the *next* exercise during rests, only when it does not
     * work the muscles of the current exercise. Null when nothing qualifies.
     */
    fun betweenSets(current: Exercise, next: Exercise, equipment: Set<String>, jointLimits: Map<Joint, Int>, avoidTags: Set<String>): Drill? {
        val busy = (current.primary + current.secondary).flatMap { regionsOf(it) }.toSet()
        return drills.filter { it.kind == DrillKind.MOBILISE && usable(it, equipment, jointLimits, avoidTags) && next.pattern in it.prepares && it.regions.none { r -> r in busy } }
            .minByOrNull { it.id }
    }

    /** MOB-004: full range by default; only a pain-gate "continue with caution" may shorten it. */
    fun rangeFor(painCaution: Boolean): RangeOfMotion = if (painCaution) RangeOfMotion.REDUCED else RangeOfMotion.FULL

    /**
     * MOB-005: optional off-day routine of 10–20 minutes covering hips, upper back, shoulders and
     * ankles, 2 × 30–60 s per position.
     */
    fun offDayRoutine(minutes: Int, equipment: Set<String> = emptySet(), jointLimits: Map<Joint, Int>, avoidTags: Set<String>): EngineResult<List<DrillDose>> {
        val m = minutes.coerceIn(P.MOB_005.minutes[0], P.MOB_005.minutes[1])
        val focus = listOf(Region.HIPS, Region.UPPER_BACK, Region.SHOULDERS, Region.ANKLES)
        val pool = drills.filter { (it.kind == DrillKind.MOBILISE || it.kind == DrillKind.STRETCH) && usable(it, equipment, jointLimits, avoidTags) }
        val ordered = focus.flatMap { r -> pool.filter { r in it.regions }.sortedBy { it.id } }.distinct() +
            pool.filter { d -> focus.none { it in d.regions } }.sortedBy { it.id }
        val out = ArrayList<DrillDose>()
        var used = 0
        for (d in ordered) {
            val amount = if (d.unit == DoseUnit.SECONDS) 30.coerceAtLeast(d.amount).coerceAtMost(60) else d.amount
            val dose = DrillDose(d, 2, amount)
            if (used + dose.seconds > m * 60) continue
            out += dose; used += dose.seconds
        }
        return EngineResult(out, listOf(Decision(DecisionKind.WARMUP, listOf(RuleIds.MOB_005), ReasonKey.MOBILITY_PLANNED,
            inputs = mapOf("minutes" to m), outputs = mapOf("drills" to out.map { it.drill.id }))))
    }

    /** MOB-005: suggested off-day routines per week. */
    val offDayPerWeek: IntRange get() = P.MOB_005.per_week[0]..P.MOB_005.per_week[1]

    /** MOB-006: one calm mobility session — what it holds, and the minutes that count as balance work (FL-003). */
    data class CalmSession(val drills: List<DrillDose>, val minutes: Double, val balanceMinutes: Double) {
        /** MOB-006: never Z1 minutes (PH-001, FL-002) and never a strength day. */
        val countsAsZ1: Boolean get() = P.MOB_006.counts_as_z1
        val countsAsStrengthDay: Boolean get() = P.MOB_006.counts_as_strength_day
    }

    /**
     * MOB-006: offered (never imposed) on rest days, LIGHT and RECOVERY days, and when the check-in stress item is 1–2 — never with
     * illness symptoms (SAF-007), a red flag (SAF-002) or a SAF-010 "follow your care provider" entry (review R3-14).
     */
    fun calmSessionOffered(restDay: Boolean, tier: com.personalfitnesscoach.engine.model.Tier?, stressItem: Int?, illness: Boolean, redFlag: Boolean,
                           followCareProvider: Boolean = false): Boolean =
        !illness && !redFlag && !followCareProvider && (restDay || tier == com.personalfitnesscoach.engine.model.Tier.LIGHT ||
            tier == com.personalfitnesscoach.engine.model.Tier.RECOVERY || (stressItem != null && stressItem <= 2))

    /**
     * MOB-006 calm mobility session of 15–30 minutes: easy movement to start, slow mobility for hips, upper back, shoulders and
     * ankles, standing balance holds, 30–60 s stretch holds, and 3–5 minutes of easy slow breathing to finish (no breath holds).
     * Condition tags, joint limits and pain limits apply; balance holds count toward FL-003 balance minutes.
     */
    fun calmSession(minutes: Int, equipment: Set<String> = emptySet(), jointLimits: Map<Joint, Int>, avoidTags: Set<String>): EngineResult<CalmSession> {
        val m = minutes.coerceIn(P.MOB_006.minutes[0], P.MOB_006.minutes[1])
        val budget = m * 60
        val breath = drills.first { it.kind == DrillKind.BREATHING }
        val breathSec = (P.MOB_006.breathing_finish_minutes[0] + (if (m >= 25) 1 else 0)) * 60
        val hold = P.MOB_006.hold_seconds[0]
        val focus = listOf(Region.HIPS, Region.UPPER_BACK, Region.SHOULDERS, Region.ANKLES)
        val out = ArrayList<DrillDose>()
        fun used() = out.sumOf { it.seconds }
        fun add(dd: DrillDose): Boolean { if (used() + dd.seconds + breathSec + 10 > budget) return false; out += dd; return true }
        // Easy movement first (a walking or swinging drill), then flowing mobility for the four focus regions.
        drills.filter { it.kind == DrillKind.MOBILISE && usable(it, equipment, jointLimits, avoidTags) && it.unit == DoseUnit.REPS &&
            (Region.WHOLE_BODY in it.regions || it.id.contains("walk") || it.id.contains("swing")) }.minByOrNull { it.id }?.let { add(DrillDose(it, 1, it.amount)) }
        for (r in focus) drills.filter { it.kind == DrillKind.MOBILISE && r in it.regions && usable(it, equipment, jointLimits, avoidTags) && out.none { o -> o.drill == it } }
            .minByOrNull { it.id }?.let { add(DrillDose(it, 2, it.amount)) }
        // Standing balance holds.
        val balance = drills.filter { it.kind == DrillKind.BALANCE && usable(it, equipment, jointLimits, avoidTags) }.sortedBy { it.id }
        for (bd in balance.take(2)) add(DrillDose(bd, 2, if (bd.unit == DoseUnit.SECONDS) maxOf(bd.amount, hold) else bd.amount))
        // Stretch holds of 30–60 s.
        for (s in drills.filter { it.kind == DrillKind.STRETCH && usable(it, equipment, jointLimits, avoidTags) }.sortedBy { it.id }) add(DrillDose(s, 1, hold))
        out += DrillDose(breath, 1, maxOf(breathSec, budget - used() - 10).coerceAtMost(P.MOB_006.breathing_finish_minutes[1] * 60))
        val balanceMin = out.filter { it.drill.kind == DrillKind.BALANCE }.sumOf { it.seconds } / 60.0
        val s = CalmSession(out, Math.round(out.sumOf { it.seconds } / 6.0) / 10.0, Math.round(balanceMin * 10) / 10.0)
        return EngineResult(s, listOf(Decision(DecisionKind.WARMUP, listOf(RuleIds.MOB_006), ReasonKey.CALM_MOBILITY_SESSION,
            inputs = mapOf("minutes" to m), outputs = mapOf("drills" to out.map { it.drill.id }, "balanceMinutes" to s.balanceMinutes))))
    }

    /**
     * FL-003 / AGE-001 balance work: supported balance drills filling `minutes`, 2 sets each, a third set round the
     * list if time is left. `offset` rotates the start (the weekday) so the week's sessions vary.
     */
    fun balanceDrills(minutes: Double, equipment: Set<String> = emptySet(), jointLimits: Map<Joint, Int>, avoidTags: Set<String>,
                      offset: Int = 0): EngineResult<List<DrillDose>> {
        val budget = Math.round(minutes * 60).toInt()
        val pool = drills.filter { it.kind == DrillKind.BALANCE && usable(it, equipment, jointLimits, avoidTags) }.sortedBy { it.id }
        if (budget <= 0 || pool.isEmpty()) return EngineResult(emptyList())
        val k = Math.floorMod(offset, pool.size)
        val ordered = pool.drop(k) + pool.take(k)
        val out = ArrayList<DrillDose>()
        for (d in ordered) {
            val dose = DrillDose(d, 2, d.amount)
            if (out.sumOf { it.seconds } + dose.seconds <= budget) out += dose
        }
        var grew = true
        while (grew) {
            grew = false
            for ((idx, dd) in out.withIndex()) {
                val more = dd.copy(sets = dd.sets + 1)
                if (dd.sets < 3 && out.sumOf { it.seconds } - dd.seconds + more.seconds <= budget) { out[idx] = more; grew = true }
            }
        }
        return EngineResult(out, listOf(Decision(DecisionKind.WARMUP, listOf(RuleIds.FL_003, RuleIds.AGE_001), ReasonKey.BALANCE_PLANNED,
            inputs = mapOf("minutes" to minutes), outputs = mapOf("drills" to out.map { "${it.drill.id}x${it.sets}" }))))
    }
}
