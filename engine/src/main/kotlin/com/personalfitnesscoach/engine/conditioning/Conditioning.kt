package com.personalfitnesscoach.engine.conditioning

import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.Num
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.library.GeneratedLibrary
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.model.Zone
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds
import com.personalfitnesscoach.engine.safety.HiitProtocol
import com.personalfitnesscoach.engine.safety.ModalityJoints

/** AER-001 talk test answers. */
enum class TalkTest { FULL_SENTENCES, NOT_COMFORTABLE, FEW_WORDS, NO_TALKING }

enum class WhoIntensity { MODERATE, VIGOROUS }

/** AER-001 aerobic zones by talk test, CR10 and optional heart-rate reserve. */
object Zones {
    private fun cr10(z: Zone): List<Int> = when (z) {
        Zone.Z1 -> P.AER_001.Z1.cr10; Zone.Z2 -> P.AER_001.Z2.cr10; Zone.Z3 -> P.AER_001.Z3.cr10; Zone.Z4 -> P.AER_001.Z4.cr10
    }

    fun cr10Range(z: Zone): IntRange = cr10(z)[0]..cr10(z)[1]

    /** CR10 below 3 is warm-up effort, not a training zone (null). */
    fun fromCr10(x: Int): Zone? = Zone.entries.firstOrNull { x in cr10Range(it) }

    fun fromTalkTest(t: TalkTest): Zone = when (t) {
        TalkTest.FULL_SENTENCES -> Zone.Z1; TalkTest.NOT_COMFORTABLE -> Zone.Z2
        TalkTest.FEW_WORDS -> Zone.Z3; TalkTest.NO_TALKING -> Zone.Z4
    }

    fun who(z: Zone): WhoIntensity = if (z == Zone.Z1) WhoIntensity.MODERATE else WhoIntensity.VIGOROUS

    /** Optional %HRR target in beats per minute (Karvonen); null for Z4, which is effort-only. */
    fun heartRateRange(z: Zone, restingHr: Int, maxHr: Int): IntRange? {
        val pct = when (z) {
            Zone.Z1 -> P.AER_001.Z1.hrr_pct; Zone.Z2 -> P.AER_001.Z2.hrr_pct; Zone.Z3 -> P.AER_001.Z3.hrr_pct; Zone.Z4 -> return null
        }
        if (maxHr <= restingHr) return null
        fun bpm(p: Int) = Math.round(restingHr + p / 100.0 * (maxHr - restingHr)).toInt()
        return bpm(pct[0])..bpm(pct[1])
    }
}

/** One planned aerobic block for weekly accounting. */
data class AerobicBlock(val zone: Zone, val workMinutes: Double, val day: Int)

/** AER-002 distribution, AER-003 progression, PH-001 WHO floor, CON-006 strength-block dose. */
object Aerobic {
    /** AER-002: share of aerobic minutes in Z1 must be at least 75% (more Z1 is always allowed). */
    fun distributionOk(blocks: List<AerobicBlock>): Boolean {
        val total = blocks.sumOf { it.workMinutes }
        if (total <= 0.0) return true
        return blocks.filter { it.zone == Zone.Z1 }.sumOf { it.workMinutes } / total >= P.AER_002.z1_share[0] - 1e-9
    }

    /** AER-002: most hard (Z2+) minutes allowed alongside `z1Minutes` of Z1. */
    fun maxHardMinutes(z1Minutes: Double): Double = z1Minutes * (1 - P.AER_002.z1_share[0]) / P.AER_002.z1_share[0]

    enum class Stage { BUILD_Z1, ADD_TEMPO, ADD_INTERVALS }

    /**
     * AER-003 / PROG-006 steady progression: add 2–5 min per session (+3 by default) without
     * exceeding +15% of last week's aerobic minutes, until 30–40 min of Z1; then tempo, then intervals.
     */
    /**
     * @param plannedThisWeekMin aerobic minutes already planned in the week's *other* sessions
     * @param sessionsLeft sessions still to plan this week, including this one; they share what is left of the weekly cap
     */
    fun nextZ1Minutes(currentSessionMin: Double, lastWeekMin: Double, plannedThisWeekMin: Double, sessionsLeft: Int): EngineResult<Double> {
        val target = P.AER_003.z1_target_minutes[1].toDouble()
        val step = 3.0.coerceIn(P.AER_003.per_session_minutes[0].toDouble(), P.AER_003.per_session_minutes[1].toDouble())
        val weekCap = weeklyCap(lastWeekMin)
        val allowed = if (sessionsLeft <= 0) 0.0 else (weekCap - plannedThisWeekMin) / sessionsLeft
        val next = maxOf(0.0, minOf(currentSessionMin + step, target, allowed))
        // Rounded down to 0.1 min so the weekly cap is never exceeded by rounding.
        return EngineResult(Math.floor(next * 10 + 1e-9) / 10.0, listOf(Decision(DecisionKind.VOLUME_CHANGE, listOf(RuleIds.AER_003, RuleIds.PROG_006),
            ReasonKey.AEROBIC_DURATION_UP, inputs = mapOf("current" to currentSessionMin, "lastWeek" to lastWeekMin), outputs = mapOf("next" to next))))
    }

    /** AER-003 / PROG-007: this week's aerobic minutes may be at most +15% on last week's (no limit before there is a week to compare). */
    fun weeklyCap(lastWeekMin: Double): Double =
        if (lastWeekMin > 0) lastWeekMin * (1 + P.AER_003.weekly_increase_pct_max / 100.0) else Double.MAX_VALUE

    /** AER-003: duration first; tempo once Z1 sessions reach 30 min; intervals once tempo is established and HIIT-003 allows. */
    fun stage(z1SessionMin: Double, tempoWeeks: Int, hiitBaseReady: Boolean): Stage = when {
        z1SessionMin < P.AER_003.z1_target_minutes[0] -> Stage.BUILD_Z1
        tempoWeeks < 2 || !hiitBaseReady -> Stage.ADD_TEMPO
        else -> Stage.ADD_INTERVALS
    }

    /** PH-001 equivalent minutes: Z1 + 2 × (Z2 + Z3/Z4 work) + logged walking. */
    fun whoEquivalentMinutes(blocks: List<AerobicBlock>, walkingMinutes: Double = 0.0): Double =
        blocks.sumOf { if (it.zone == Zone.Z1) it.workMinutes else P.PH_001.vigorous_multiplier * it.workMinutes } + walkingMinutes

    data class WhoCheck(val equivalentMinutes: Double, val meetsAerobic: Boolean, val meetsStrength: Boolean, val suggestedWalkingMinutes: Double)

    /** PH-001: does the planned week meet the WHO floor? If not, how much walking would close the gap. */
    fun whoCheck(blocks: List<AerobicBlock>, strengthDays: Int, walkingMinutes: Double = 0.0): EngineResult<WhoCheck> {
        val eq = whoEquivalentMinutes(blocks, walkingMinutes)
        val gap = maxOf(0.0, P.PH_001.target_equivalent_minutes - eq)
        val c = WhoCheck(Num.round1(eq), gap == 0.0, strengthDays >= P.PH_001.strength_days_min, Num.round1(gap))
        val d = if (c.meetsAerobic && c.meetsStrength) emptyList() else listOf(Decision(DecisionKind.VOLUME_CHANGE, listOf(RuleIds.PH_001),
            ReasonKey.WHO_FLOOR_SHORT, outputs = mapOf("equivalent" to c.equivalentMinutes, "walking" to c.suggestedWalkingMinutes, "strengthDays" to strengthDays)))
        return EngineResult(c, d)
    }

    /** CON-006: in strength-emphasis blocks aerobic work is mostly Z1 and ≤ 150 prescribed minutes. */
    fun strengthBlockOk(blocks: List<AerobicBlock>): Boolean =
        blocks.sumOf { it.workMinutes } <= P.CON_006.max_prescribed_minutes + 1e-9 && distributionOk(blocks)

    /** FREQ-005: aerobic on ≥ 3 days when training ≥ 3 days; a block counts from 10 minutes. */
    fun frequencyOk(blocks: List<AerobicBlock>, trainingDays: Int): Boolean {
        if (trainingDays < P.FREQ_005.applies_when_training_days_gte) return true
        val days = blocks.groupBy { it.day }.count { (_, b) -> b.sumOf { it.workMinutes } >= P.FREQ_005.min_block_minutes - 1e-9 }
        return days >= P.FREQ_005.min_days
    }
}

/** What a conditioning slot is for (MOD-002 stimulus fit). */
enum class ConditioningPurpose { STEADY, INTERVALS }

/** Today's context for choosing a modality. */
data class ModalityContext(
    val equipment: Set<String>,
    val purpose: ConditioningPurpose,
    /** 0–1: how much today's or tomorrow's leg training matters (heavy legs soon = 1). */
    val legPriority: Double = 0.5,
    /** 0–1: how sensitive the joints are today (pain, limitations, age). */
    val jointSensitivity: Double = 0.5,
    val recentModalities: List<Modality> = emptyList(),
    val preferences: Map<Modality, Double> = emptyMap(),
    val jointLimits: Map<Joint, Int> = emptyMap(),
    /** CON-004: impact work is ≤ 1/week and never the day before heavy legs. */
    val impactAllowed: Boolean = true,
    /** MOD-001 2.0.0: modalities this user doesn't use. */
    val excluded: Set<Modality> = emptySet(),
    /** Only these modalities (FL-003 low-impact intervals, SAF-010 interval lists); null = any. */
    val allowed: Set<Modality>? = null,
)

data class ModalityScore(val modality: Modality, val score: Double)

/** MOD-002 modality matrix and selection score; MOD-001 exclusions are never candidates. */
object ModalitySelection {
    private fun row(m: Modality): List<Int>? = when (m) {
        Modality.ROWER -> P.MOD_002.matrix.rower; Modality.SKIERG -> P.MOD_002.matrix.skierg
        Modality.ELLIPTICAL -> P.MOD_002.matrix.elliptical; Modality.SLED -> P.MOD_002.matrix.sled
        Modality.BATTLE_ROPES -> P.MOD_002.matrix.battle_ropes; Modality.KETTLEBELL -> P.MOD_002.matrix.kettlebell
        Modality.CARRIES -> P.MOD_002.matrix.carries; Modality.MEDBALL -> P.MOD_002.matrix.medball
        Modality.BODYWEIGHT_CIRCUIT -> P.MOD_002.matrix.bodyweight_circuit; Modality.JUMP_ROPE -> P.MOD_002.matrix.jump_rope
        Modality.TREADMILL_WALK -> P.MOD_002.matrix.treadmill_walking; Modality.STATIONARY_BIKE -> P.MOD_002.matrix.stationary_bike
        Modality.AIR_BIKE -> P.MOD_002.matrix.air_fan_bike; Modality.STAIR_MACHINE -> P.MOD_002.matrix.stair_machine
        Modality.TREADMILL_RUN -> P.MOD_002.matrix.treadmill_running
    }

    private fun col(name: String) = P.MOD_002.matrix_columns.indexOf(name)

    fun matrix(m: Modality, column: String): Int? = row(m)?.get(col(column))

    /** Impact modalities (impact ≥ 3) count toward the CON-004 weekly impact limit. */
    fun isImpact(m: Modality): Boolean = (matrix(m, "impact") ?: 0) >= 3

    fun available(m: Modality, equipment: Set<String>): Boolean {
        val info = GeneratedLibrary.modalities.firstOrNull { it.modality == m } ?: return false
        return equipment.containsAll(info.equipment) || info.altEquipment.any { it in equipment }
    }

    fun score(m: Modality, ctx: ModalityContext): Double {
        val r = row(m) ?: return 0.0
        val w = P.MOD_002.weights
        val stimulus = r[col(if (ctx.purpose == ConditioningPurpose.STEADY) "steady_state" else "intervals")] / 5.0
        val interference = (1 - r[col("interference")] / 5.0) * ctx.legPriority
        val impact = (1 - r[col("impact")] / 5.0) * ctx.jointSensitivity
        val variety = if (m in ctx.recentModalities.takeLast(2)) 0.0 else 1.0
        val pref = ctx.preferences[m] ?: 0.5
        return Math.round((w.stimulus_fit * stimulus + w.interference * interference + w.impact * impact + w.variety * variety + w.preference * pref) * 1000.0) / 1000.0
    }

    /** Ranked candidates after hard filters (excluded, equipment, joint limits, impact limit); ties by name. */
    fun rank(ctx: ModalityContext): EngineResult<List<ModalityScore>> {
        val ranked = Modality.entries.asSequence()
            .filter { it !in ctx.excluded && row(it) != null && available(it, ctx.equipment) && (ctx.allowed == null || it in ctx.allowed) }
            .filter { m -> ctx.jointLimits.all { (j, lim) -> ModalityJoints.stress(m, j) <= lim } }
            .filter { ctx.impactAllowed || !isImpact(it) }
            .map { ModalityScore(it, score(it, ctx)) }
            .sortedWith(compareByDescending<ModalityScore> { it.score }.thenBy { it.modality.name })
            .toList()
        return EngineResult(ranked, listOf(Decision(DecisionKind.SUBSTITUTION, listOf(RuleIds.MOD_001, RuleIds.MOD_002), ReasonKey.MODALITY_CHOSEN,
            inputs = mapOf("purpose" to ctx.purpose.name), outputs = mapOf("ranked" to ranked.map { "${it.modality}:${it.score}" }))))
    }
}

/** MOD-001 2.0.0: which cardio machines a user doesn't use. Nothing is excluded globally. */
object ModalityExclusions {
    /** Every machine a user can switch on or off. */
    val selectable: List<Modality> = P.MOD_001.user_selectable.mapNotNull { Modality.byRegistryKey(it) }

    /** Defaults for a new user: treadmill running is off for the fat-loss goal (impact); everything else is on. */
    fun defaults(fatLossGoal: Boolean): Set<Modality> =
        if (fatLossGoal) P.MOD_001.default_excluded_by_goal.fat_loss.mapNotNull { Modality.byRegistryKey(it) }.toSet() else emptySet()
}

/** An interval prescription: `reps` × (`workSec` at CR10 `cr10`, then `restSec` easy). */
data class Interval(val protocol: HiitProtocol?, val reps: Int, val workSec: Int, val restSec: Int, val cr10: IntRange) {
    val workMinutes: Double get() = reps * workSec / 60.0
    val totalMinutes: Double get() = reps * (workSec + restSec) / 60.0
}

/** Why the week needs intervals (HIIT-002: chosen by block and purpose, not popularity). */
enum class BlockKind { CALIBRATE, FOUNDATION, BUILD, STRENGTH, CONDITIONING, POWER, CONSOLIDATION, REVIEW, DELOAD }

/** HIIT-002 menu, the HIIT-003 first protocol and PROG-006 interval progression. */
object HiitMenu {
    data class Band(val reps: IntRange, val work: IntRange, val rest: IntRange, val cr10: IntRange)

    fun band(p: HiitProtocol?): Band {
        fun r(l: List<Int>) = l[0]..l[1]
        return when (p) {
            HiitProtocol.LONG -> with(P.HIIT_002.long) { Band(r(reps), r(work_s), r(rest_s), r(cr10)) }
            HiitProtocol.MEDIUM -> with(P.HIIT_002.medium) { Band(r(reps), r(work_s), r(rest_s), r(cr10)) }
            HiitProtocol.SHORT -> with(P.HIIT_002.short) { Band(r(reps), r(work_s), r(rest_s), r(cr10)) }
            HiitProtocol.SPRINT -> with(P.HIIT_002.sprint) { Band(r(reps), r(work_s), r(rest_s), r(cr10)) }
            null -> with(P.HIIT_002.tempo_not_hiit) { Band(r(reps), r(work_s), r(rest_s), r(cr10)) }
        }
    }

    /**
     * Protocol by block and purpose, following the Phase 1 blueprint (D-050): Foundation and
     * Strength blocks use SHORT; Build blocks LONG; the Conditioning block LONG then SHORT; the
     * Power block SHORT, or one SPRINT a week for advanced lifters; Consolidation MEDIUM then
     * SHORT. The very first HIIT is always SHORT at 1:2 (HIIT-003). `sessionIndex` is 0 for the
     * week's first HIIT session, 1 for the second.
     */
    fun choose(block: BlockKind, level: Level, hiitDoneEver: Int, sprintsThisWeek: Int, sessionIndex: Int, sprintsAllowed: Boolean = true): EngineResult<HiitProtocol> {
        val p = when {
            hiitDoneEver == 0 -> HiitProtocol.SHORT
            block == BlockKind.BUILD -> HiitProtocol.LONG
            block == BlockKind.CONDITIONING -> if (sessionIndex == 0) HiitProtocol.LONG else HiitProtocol.SHORT
            // FL-003: no sprint intervals from 50 or with low-impact-only intervals; SAF-010 zone caps below Z4 rule them out too.
            block == BlockKind.POWER && level == Level.ADVANCED && sprintsAllowed && sprintsThisWeek < P.HIIT_002.sprint.per_week_max -> HiitProtocol.SPRINT
            block == BlockKind.CONSOLIDATION -> if (sessionIndex == 0) HiitProtocol.MEDIUM else HiitProtocol.SHORT
            else -> HiitProtocol.SHORT
        }
        return EngineResult(p, listOf(Decision(DecisionKind.LOAD_PRESCRIPTION, listOf(RuleIds.HIIT_002, RuleIds.HIIT_003), ReasonKey.HIIT_PROTOCOL_CHOSEN,
            inputs = mapOf("block" to block.name, "level" to level.name, "hiitDoneEver" to hiitDoneEver, "sessionIndex" to sessionIndex), outputs = mapOf("protocol" to p.name))))
    }

    /** Starting prescription: bottom of the band; beginners and first-ever HIIT use a 1:2 work:rest ratio. */
    fun start(p: HiitProtocol?, level: Level, firstEver: Boolean): Interval {
        val b = band(p)
        val work = b.work.first
        var rest = b.rest.first
        if (p == HiitProtocol.SHORT && (level == Level.BEGINNER || firstEver)) rest = Num.clampInt(work * 2, b.rest.first, b.rest.last)
        if (p == HiitProtocol.SPRINT) rest = maxOf(rest, work * 6)
        val cr10 = if (firstEver) 7..8 else b.cr10
        return Interval(p, b.reps.first, work, rest, cr10)
    }

    /**
     * PROG-006: +1 repeat → longer work → shorter rest; at the top of the band, hold (pace +1–2% is the
     * next lever). Sprints keep rest ≥ 6 × work; beginners on SHORT intervals keep rest ≥ 2 × work (HIIT-002).
     */
    fun progress(cur: Interval, level: Level = Level.INTERMEDIATE): Interval {
        val b = band(cur.protocol)
        val ratio = when {
            cur.protocol == HiitProtocol.SPRINT -> 6
            cur.protocol == HiitProtocol.SHORT && level == Level.BEGINNER -> 2
            else -> 0
        }
        fun minRest(work: Int) = maxOf(b.rest.first, work * ratio)
        return when {
            cur.reps < b.reps.last -> cur.copy(reps = cur.reps + 1)
            cur.workSec < b.work.last && minRest(minOf(b.work.last, cur.workSec + 15)) <= b.rest.last -> {
                val w = minOf(b.work.last, cur.workSec + 15)
                cur.copy(workSec = w, restSec = maxOf(cur.restSec, minRest(w)))
            }
            cur.restSec > minRest(cur.workSec) -> cur.copy(restSec = maxOf(minRest(cur.workSec), cur.restSec - 15))
            else -> cur
        }
    }

    /** The smallest valid HIIT session: the HIIT-003 first protocol (SHORT at 1:2), in minutes of work. */
    fun firstSessionWorkMinutes(): Double = start(HiitProtocol.SHORT, Level.BEGINNER, firstEver = true).workMinutes

    /**
     * PROG-007: this week's HIIT work may be at most +2 min on last week's; the first HIIT-003 session is
     * always allowed once the base is ready, even when that is a little more than 2 minutes (D-053).
     */
    fun weeklyWorkBudget(lastWeekWorkMin: Double): Double =
        maxOf(lastWeekWorkMin + P.PROG_007.hiit_work_min_week, firstSessionWorkMinutes())

    /** HIIT-005: a session's HIIT work cap in minutes for the protocol. */
    fun workCapMinutes(p: HiitProtocol): Double = when (p) {
        HiitProtocol.LONG, HiitProtocol.MEDIUM -> P.HIIT_005.long_work_min_max.toDouble()
        HiitProtocol.SHORT -> P.HIIT_005.short_work_min_max.toDouble()
        HiitProtocol.SPRINT -> P.HIIT_005.sprint_work_min_max
    }
}

/** PROG-006 for sleds and carries: distance first, then +5–10% load. */
object CarryProgression {
    fun next(distanceM: Int, maxDistanceM: Int, loadKg: Double, stepM: Int = 10): Pair<Int, Double> =
        if (distanceM + stepM <= maxDistanceM) (distanceM + stepM) to loadKg
        else (maxDistanceM - 2 * stepM).coerceAtLeast(stepM) to Num.round2(loadKg * (1 + P.PROG_006.sled_load_pct[0] / 100.0))

    /** PROG-006: once steady minutes reach target, progress pace or distance by 1–2% at the same CR10. */
    fun nextPace(currentPace: Double): Double = Num.round2(currentPace * (1 + P.PROG_006.pace_pct[0] / 100.0))
}

/** CON-002 and CON-005: same-day rules for strength, hard aerobic work and power. */
object Concurrent {
    /** A session counts as hard aerobic work when it is HIIT or ≥ 30 min of Z2+. */
    fun hardAerobic(hiit: Boolean, z2PlusMinutes: Double): Boolean = hiit || z2PlusMinutes >= P.CON_002.hard_aerobic_min_minutes

    enum class SameDay { SEPARATE_OK, MERGE_STRENGTH_FIRST }

    /** CON-002: two hard sessions on one day need ≥ 6 h between them, otherwise they merge strength-first. */
    fun sameDay(hoursApart: Double): SameDay = if (hoursApart >= P.CON_002.min_hours) SameDay.SEPARATE_OK else SameDay.MERGE_STRENGTH_FIRST

    /** CON-005: power work only at the start of a session and never after HIIT earlier the same day. */
    fun powerAllowed(hiitEarlierToday: Boolean, atSessionStart: Boolean): Boolean = !hiitEarlierToday && atSessionStart
}
