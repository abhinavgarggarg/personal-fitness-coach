package com.personalfitnesscoach.engine.planning

import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.Num
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.dose.Order
import com.personalfitnesscoach.engine.dose.PairClass
import com.personalfitnesscoach.engine.model.Muscle
import com.personalfitnesscoach.engine.progression.Warmup
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds

/** TIME-001 priority tiers. P0 (warm-up and cool-down) lives on [SessionPlan] itself. */
enum class Priority { P1, P2, P3, P4, P5 }

/** Interval or steady conditioning: `rounds` × (work + rest). Steady work is 1 round with no rest. */
data class Conditioning(val workSec: Int, val restSec: Int, val rounds: Int)

data class PlanItem(
    val id: String,
    val priority: Priority,
    val sets: Int = 0,
    val reps: Int = 0,
    val restSec: Int = 0,
    val minRestSec: Int = 0,
    val setupSec: Int = 20,
    val tempoSecPerRep: Double = P.TIME_004.tempo_s_per_rep,
    val station: String = "",
    val primaryMuscles: Set<Muscle> = emptySet(),
    val conditioning: Conditioning? = null,
    /** Allowed to be paired into a non-competing superset (P3–P5 only; ORD-003 forbids heavy main lifts at RIR ≤ 2). */
    val supersetEligible: Boolean = true,
    val pairedWith: String? = null,
    /** ORD-003 pairing class; ANY keeps the muscle-overlap check only. */
    val pairClass: PairClass = PairClass.ANY,
) {
    val isConditioning get() = conditioning != null
}

data class SessionPlan(
    val warmupMin: Double,
    val cooldownMin: Double,
    val items: List<PlanItem>,
    val z1Min: Double = 0.0,
    val coreMobilityMin: Double = 0.0,
    val skillPowerMin: Double = 0.0,
)

data class FitResult(
    val plan: SessionPlan,
    val minutes: Double,
    val fits: Boolean,
    val expressOffered: Boolean,
    /** Items removed whose weekly coverage must move to another day. */
    val dropped: List<String>,
)

/** TIME-004 time model. */
object TimeModel {
    private val stationChangeSec = (P.TIME_004.station_change_s[0] + P.TIME_004.station_change_s[1]) / 2.0

    fun setSeconds(item: PlanItem): Double = item.reps * item.tempoSecPerRep + P.TIME_004.per_set_overhead_s

    /** Seconds for one item on its own (no station change). */
    fun itemSeconds(item: PlanItem): Double {
        val c = item.conditioning
        if (c != null) return item.setupSec + c.rounds * (c.workSec + c.restSec).toDouble()
        if (item.sets <= 0) return 0.0
        return item.setupSec + item.sets * setSeconds(item) + (item.sets - 1) * item.restSec.toDouble()
    }

    /** A non-competing superset: alternate A and B with the REST-004 rest after each set. */
    fun pairSeconds(a: PlanItem, b: PlanItem): Double {
        val rounds = a.sets + b.sets
        return a.setupSec + b.setupSec + a.sets * setSeconds(a) + b.sets * setSeconds(b) + (rounds - 1) * P.REST_004.default_s.toDouble()
    }

    /** Planned session minutes including warm-up, cool-down, station changes, 5% buffer and personal factor. */
    fun minutes(plan: SessionPlan, personalFactor: Double = 1.0): Double {
        val f = Num.clamp(personalFactor, P.TIME_004.personal_factor_bounds[0], P.TIME_004.personal_factor_bounds[1])
        var sec = 0.0
        var lastStation: String? = null
        val done = HashSet<String>()
        val byId = plan.items.associateBy { it.id }
        for (item in plan.items) {
            if (item.id in done) continue
            val partner = item.pairedWith?.let { byId[it] }
            sec += if (partner != null) pairSeconds(item, partner) else itemSeconds(item)
            done += item.id
            if (partner != null) done += partner.id
            if (lastStation != null && item.station != lastStation) sec += stationChangeSec
            lastStation = item.station
        }
        sec += (plan.warmupMin + plan.cooldownMin + plan.z1Min + plan.coreMobilityMin + plan.skillPowerMin) * 60.0
        return sec * (1.0 + P.TIME_004.buffer_pct / 100.0) * f / 60.0
    }

    /** Updates the personal factor from a real duration, bounded 0.8–1.3. Smoothed like e1RM (α 0.4). */
    fun updatePersonalFactor(current: Double, plannedMin: Double, actualMin: Double): Double {
        if (plannedMin <= 0.0) return current
        val observed = current * actualMin / plannedMin
        return Num.clamp(current + 0.4 * (observed - current), P.TIME_004.personal_factor_bounds[0], P.TIME_004.personal_factor_bounds[1])
    }
}

/**
 * Fits a session into the minutes available (TIME-001 to TIME-003). Compression follows the
 * TIME-002 order and stops as soon as the session fits. The warm-up is never removed, P1 never
 * drops below 2 working sets, and rests never go below their REST minimum.
 */
object TimeBudget {
    /** The warm-up floor: WU-003's 5 minutes, WU-004's age extra and any SAF-010 extra. */
    fun warmupFloor(age: Int?, extraWarmupMin: Double = 0.0): Double = Warmup.floorMinutes(age) + extraWarmupMin

    /**
     * @param extraWarmupMin / extraCooldownMin SAF-010 extras (the highest of the picked conditions): part of the floor, so time
     *   pressure never cuts them (review R3-02)
     */
    fun fit(plan: SessionPlan, minutes: Double, age: Int? = null, personalFactor: Double = 1.0, crowded: Boolean = false,
            extraWarmupMin: Double = 0.0, extraCooldownMin: Double = 0.0): EngineResult<FitResult> {
        val express = minutes < P.TIME_002.express_below_minutes
        // P0 is never below its floor: cool-down ≥ 2 min (TIME-001), warm-up ≥ 5 min (+ age extra, WU-003/004), each + the SAF-010 extra.
        val warmupFloor = warmupFloor(age, extraWarmupMin)
        var cur = plan.copy(
            cooldownMin = maxOf(plan.cooldownMin, P.TIME_001.cooldown_min_minutes.toDouble() + extraCooldownMin),
            warmupMin = maxOf(plan.warmupMin, warmupFloor),
        )
        val dropped = ArrayList<String>()
        val steps = ArrayList<String>()
        fun t() = TimeModel.minutes(cur, personalFactor)
        fun fits() = t() <= minutes + 1e-9
        fun items(f: (PlanItem) -> PlanItem) { cur = cur.copy(items = cur.items.map(f)) }

        fun done(): EngineResult<FitResult> {
            val ok = fits()
            val d = ArrayList<Decision>()
            if (steps.isNotEmpty()) d += Decision(DecisionKind.TIME_FIT, listOf(RuleIds.TIME_001, RuleIds.TIME_002, RuleIds.TIME_004),
                ReasonKey.TIME_COMPRESSED, inputs = mapOf("available" to minutes, "planned" to Num.round1(TimeModel.minutes(plan, personalFactor))),
                outputs = mapOf("minutes" to Num.round1(t()), "steps" to steps.toList(), "dropped" to dropped.toList()))
            if (express) d += Decision(DecisionKind.TIME_FIT, listOf(RuleIds.TIME_002), ReasonKey.TIME_EXPRESS_OFFERED, inputs = mapOf("available" to minutes))
            if (!ok) d += Decision(DecisionKind.TIME_FIT, listOf(RuleIds.TIME_002), ReasonKey.TIME_NOT_FITTABLE, outputs = mapOf("minutes" to Num.round1(t())))
            return EngineResult(FitResult(cur, Num.round1(t()), ok, express, dropped.toList()), d)
        }
        if (fits()) return done()

        // 1) P4/P5 rests to their minimum.
        items { if (it.priority >= Priority.P4 && !it.isConditioning) it.copy(restSec = it.minRestSec) else it }
        steps += "rest_P4_P5_to_min"; if (fits()) return done()

        // 2) Pair eligible P3–P5 items into non-competing supersets.
        cur = cur.copy(items = pairSupersets(cur.items, crowded))
        steps += "supersets"; if (fits()) return done()

        // 3) P5 to 1 set, then drop P5 items (last first).
        items { if (it.priority == Priority.P5 && !it.isConditioning && it.sets > 1) it.copy(sets = 1) else it }
        steps += "P5_reduce"; if (fits()) return done()
        while (cur.items.any { it.priority == Priority.P5 }) {
            cur.dropLastOf(Priority.P5).let { cur = it.first; dropped += it.second; steps += "P5_drop:${it.second}" }
            if (fits()) return done()
        }
        // 4) P4 to 2 sets; then, lowest priority first (last in the list), each P4 item to 1 set and then dropped.
        items { if (it.priority == Priority.P4 && !it.isConditioning && it.sets > 2) it.copy(sets = 2) else it }
        steps += "P4_to_2"; if (fits()) return done()
        while (cur.items.any { it.priority == Priority.P4 }) {
            val victim = cur.items.last { it.priority == Priority.P4 }
            if (!victim.isConditioning && victim.sets > 1) {
                items { if (it.id == victim.id) it.copy(sets = 1) else it }
                steps += "P4_to_1:${victim.id}"; if (fits()) return done()
            }
            cur.dropLastOf(Priority.P4).let { cur = it.first; dropped += it.second; steps += "P4_drop:${it.second}" }
            if (fits()) return done()
        }
        // 5) Warm-up to its floor (never removed; a condition's extra minutes stay).
        cur = cur.copy(warmupMin = minOf(cur.warmupMin, warmupFloor))
        steps += "warmup_to_floor"; if (fits()) return done()

        // 6) P3 −1 set each (at least 1).
        items { if (it.priority == Priority.P3 && !it.isConditioning && it.sets > 1) it.copy(sets = it.sets - 1) else it }
        steps += "P3_minus_one_set"; if (fits()) return done()

        // 7) P2 conditioning shortened by up to 40%, one round (or 10% of steady time) at a time.
        val original = cur.items.filter { it.priority == Priority.P2 && it.conditioning != null }.associate { it.id to it.conditioning!! }
        var changed = true
        while (changed && !fits()) {
            changed = false
            items { item ->
                val c = item.conditioning
                val o = original[item.id]
                if (item.priority != Priority.P2 || c == null || o == null || changed) item
                else if (o.rounds > 1) {
                    val minRounds = Math.ceil(o.rounds * (1 - P.TIME_002.p2_max_cut_pct / 100.0) - 1e-9).toInt()
                    if (c.rounds > minRounds) { changed = true; item.copy(conditioning = c.copy(rounds = c.rounds - 1)) } else item
                } else {
                    // ≤ 40% shorter (TIME-002), and a steady block of ≥ 10 min keeps 10 min so it still counts (FREQ-005).
                    val freqFloor = if (o.workSec >= P.FREQ_005.min_block_minutes * 60) P.FREQ_005.min_block_minutes * 60 else 0
                    val minWork = maxOf(freqFloor, Math.ceil(o.workSec * (1 - P.TIME_002.p2_max_cut_pct / 100.0) - 1e-9).toInt())
                    val next = maxOf(minWork, c.workSec - Math.ceil(o.workSec * 0.1).toInt())
                    if (next < c.workSec) { changed = true; item.copy(conditioning = c.copy(workSec = next)) } else item
                }
            }
        }
        steps += "P2_shorten"; if (fits()) return done()

        // 8) REST-007: P3 then P1 rests shrink toward (never below) their minimum, 30 s at a time;
        //    then P1 drops toward 2 working sets (decision D-034).
        for (pr in listOf(Priority.P3, Priority.P1)) {
            while (!fits() && cur.items.any { it.priority == pr && !it.isConditioning && it.pairedWith == null && it.restSec > it.minRestSec }) {
                items { if (it.priority == pr && !it.isConditioning && it.pairedWith == null) it.copy(restSec = maxOf(it.minRestSec, it.restSec - 30)) else it }
            }
            steps += "${pr}_rest_toward_min"; if (fits()) return done()
        }
        while (!fits() && cur.items.any { it.priority == Priority.P1 && !it.isConditioning && it.sets > 2 }) {
            var once = false
            items { if (!once && it.priority == Priority.P1 && !it.isConditioning && it.sets > 2) { once = true; it.copy(sets = it.sets - 1) } else it }
        }
        steps += "P1_to_two_sets"
        return done()
    }

    /**
     * TIME-003: with more time, add (in order) P4 sets toward weekly targets, Z1 minutes,
     * core/mobility, then skill or power — always within the session set cap.
     */
    fun extend(
        plan: SessionPlan,
        minutes: Double,
        sessionSetCap: Int,
        /** VOL-005 direct sets per muscle per session (Volume.perSessionCap); null = not checked here. */
        directCapPerMuscle: Int? = null,
        maxSetsPerAccessory: Int = 4,
        z1MaxMin: Double = 20.0,
        coreMobilityMaxMin: Double = 10.0,
        skillPowerMaxMin: Double = 10.0,
        personalFactor: Double = 1.0,
    ): EngineResult<SessionPlan> {
        var cur = plan
        val added = ArrayList<String>()
        fun fits(p: SessionPlan) = TimeModel.minutes(p, personalFactor) <= minutes + 1e-9
        fun totalSets(p: SessionPlan) = p.items.filter { !it.isConditioning }.sumOf { it.sets }
        // P4 sets, round-robin.
        var progress = true
        while (progress) {
            progress = false
            for (item in cur.items.filter { it.priority == Priority.P4 && !it.isConditioning }) {
                if (item.sets >= maxSetsPerAccessory || totalSets(cur) >= sessionSetCap) continue
                if (directCapPerMuscle != null && item.primaryMuscles.any { m ->
                        cur.items.filter { !it.isConditioning && m in it.primaryMuscles }.sumOf { it.sets } + 1 > directCapPerMuscle }) continue
                val next = cur.copy(items = cur.items.map { if (it.id == item.id) it.copy(sets = it.sets + 1) else it })
                if (fits(next)) { cur = next; progress = true; added += "P4:${item.id}" }
            }
        }
        fun addMinutes(get: (SessionPlan) -> Double, set: (SessionPlan, Double) -> SessionPlan, max: Double, label: String) {
            while (get(cur) + 5.0 <= max + 1e-9) {
                val next = set(cur, get(cur) + 5.0)
                if (!fits(next)) break
                cur = next; added += label
            }
        }
        addMinutes({ it.z1Min }, { p, v -> p.copy(z1Min = v) }, z1MaxMin, "z1")
        addMinutes({ it.coreMobilityMin }, { p, v -> p.copy(coreMobilityMin = v) }, coreMobilityMaxMin, "core_mobility")
        addMinutes({ it.skillPowerMin }, { p, v -> p.copy(skillPowerMin = v) }, skillPowerMaxMin, "skill_power")
        val d = if (added.isEmpty()) emptyList() else listOf(Decision(DecisionKind.TIME_FIT, listOf(RuleIds.TIME_003, RuleIds.VOL_008),
            ReasonKey.TIME_EXTENDED, inputs = mapOf("available" to minutes), outputs = mapOf("added" to added.toList())))
        return EngineResult(cur, d)
    }

    private fun SessionPlan.dropLastOf(p: Priority): Pair<SessionPlan, String> {
        val victim = items.last { it.priority == p }
        val remaining = items.filter { it.id != victim.id }.map { if (it.pairedWith == victim.id) it.copy(pairedWith = null) else it }
        return copy(items = remaining) to victim.id
    }

    /**
     * Greedy non-competing pairing of P3–P5 strength items (ORD-003, EQ-002): no shared primary
     * muscle, an allowed pair (push+pull, upper+lower, compound+core) and, in a crowded gym,
     * one station or a portable partner. The pair runs at the fixed station of the two.
     */
    fun pairSupersets(items: List<PlanItem>, crowded: Boolean = false): List<PlanItem> {
        val eligible = items.filter { it.priority >= Priority.P3 && !it.isConditioning && it.supersetEligible && it.pairedWith == null }
        val pairs = HashMap<String, String>()
        for (a in eligible) {
            if (a.id in pairs) continue
            val b = eligible.firstOrNull {
                it.id != a.id && it.id !in pairs && (it.primaryMuscles intersect a.primaryMuscles).isEmpty() &&
                    Order.classesCompatible(a.pairClass, it.pairClass) && Order.stationsCompatible(a.station, it.station, crowded)
            } ?: continue
            pairs[a.id] = b.id; pairs[b.id] = a.id
        }
        if (pairs.isEmpty()) return items
        // Move each second partner directly after its first partner so the pair runs together.
        val out = ArrayList<PlanItem>()
        val placed = HashSet<String>()
        val byId = items.associateBy { it.id }
        for (item in items) {
            if (item.id in placed) continue
            val partnerId = pairs[item.id]
            if (partnerId == null) { out += item; placed += item.id; continue }
            val partner = byId.getValue(partnerId)
            val station = if (Order.portable(item.station)) partner.station else item.station
            out += item.copy(pairedWith = partnerId, station = station)
            placed += item.id
            if (partnerId !in placed) {
                out += partner.copy(pairedWith = item.id, station = station)
                placed += partnerId
            }
        }
        return out
    }
}
