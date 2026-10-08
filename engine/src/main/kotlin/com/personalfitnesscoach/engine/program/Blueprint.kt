package com.personalfitnesscoach.engine.program

import com.personalfitnesscoach.engine.calc.Volume
import com.personalfitnesscoach.engine.conditioning.BlockKind
import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds

/** The user's training priorities (onboarding ranks them; first = #1). */
enum class Goal { STRENGTH, MUSCLE, CARDIO, POWER, MOBILITY, BODY_COMPOSITION, GENERAL_FITNESS }

/** PER-003 block sequence. */
enum class BlockType(val kind: BlockKind, val goal: Goal?) {
    CALIBRATE(BlockKind.CALIBRATE, null),
    FOUNDATION(BlockKind.FOUNDATION, Goal.GENERAL_FITNESS),
    BUILD(BlockKind.BUILD, Goal.MUSCLE),
    STRENGTH(BlockKind.STRENGTH, Goal.STRENGTH),
    CONDITIONING(BlockKind.CONDITIONING, Goal.CARDIO),
    BUILD_2(BlockKind.BUILD, Goal.MUSCLE),
    STRENGTH_2(BlockKind.STRENGTH, Goal.STRENGTH),
    POWER_ATHLETICISM(BlockKind.POWER, Goal.POWER),
    CONSOLIDATION(BlockKind.CONSOLIDATION, Goal.GENERAL_FITNESS),
    REVIEW(BlockKind.REVIEW, null);

    val isStrengthEmphasis: Boolean get() = this == STRENGTH || this == STRENGTH_2
}

/** What a calendar week of the program is. DELOAD_OR_PIVOT is decided at run time by DEL-002. */
enum class WeekKind { CALIBRATION, LOADING, DELOAD_OR_PIVOT, REVIEW, FLEX }

data class Block(val type: BlockType, val startWeek: Int, val loadingWeeks: Int, val followedByDeloadOrPivot: Boolean) {
    val endWeek: Int get() = startWeek + loadingWeeks - 1
}

data class Program(val blocks: List<Block>, val flexWeeks: Int, val priorities: List<Goal>) {
    val totalWeeks: Int get() = blocks.sumOf { it.loadingWeeks + if (it.followedByDeloadOrPivot) 1 else 0 } + flexWeeks
}

/** Where a program week sits: block, week within the block and what kind of week it is. */
data class WeekContext(val programWeek: Int, val block: Block, val weekInBlock: Int, val kind: WeekKind, val blockIndex: Int)

/**
 * Main-lift and accessory dose for a block, from the approved Phase 1 blueprint (section 25).
 * Encoded here rather than in the registry because the registry holds rules, not the table;
 * see decision D-050.
 */
data class BlockDose(
    val heavyReps: IntRange,
    val moderateReps: IntRange,
    val mainRir: ClosedFloatingPointRange<Double>,
    val accessoryReps: IntRange,
    val accessoryRir: ClosedFloatingPointRange<Double>,
    /** Weekly sets per muscle: start and top of the block's band (VOL-003 landmarks). */
    val setsStart: Int,
    val setsTop: Int,
    val hiitPerWeek: Int,
    val tempoPerWeek: Int,
    val power: Boolean,
    /** CONDITIONING block: main lifts keep 2 heavy sets twice a week. */
    val mainSetsOverride: Int? = null,
)

/** PER-001 to PER-006. */
object Blueprint {
    private val DEFAULT_SEQUENCE = listOf(BlockType.FOUNDATION, BlockType.BUILD, BlockType.STRENGTH, BlockType.CONDITIONING,
        BlockType.BUILD_2, BlockType.STRENGTH_2, BlockType.POWER_ATHLETICISM, BlockType.CONSOLIDATION)

    /**
     * PER-003 with PER-002 lengths and PER-006 priority weighting: calibrate 2 weeks, eight
     * 5-week blocks each followed by a deload-or-pivot week (weeks 8, 14 … 44 by default),
     * consolidation, then review plus 2 flex weeks. The #1 priority's blocks gain a week and
     * the lowest priority's lose one (never below 4); the flex weeks absorb the difference.
     */
    fun plan(priorities: List<Goal>): EngineResult<Program> {
        val top = priorities.firstOrNull()
        val low = priorities.lastOrNull()?.takeIf { priorities.size > 1 && it != top }
        val blocks = ArrayList<Block>()
        var week = 1
        blocks += Block(BlockType.CALIBRATE, week, P.PER_003.calibrate_weeks, followedByDeloadOrPivot = false)
        week += P.PER_003.calibrate_weeks
        for (t in DEFAULT_SEQUENCE) {
            var len = P.PER_002.default_weeks
            if (t.goal != null && t.goal == top) len += P.PER_006.top_priority_weeks
            if (t.goal != null && t.goal == low) len += P.PER_006.low_priority_weeks
            len = len.coerceIn(maxOf(P.PER_002.min_weeks, P.PER_006.min_block_weeks), P.PER_002.max_weeks)
            val deload = t != BlockType.CONSOLIDATION
            blocks += Block(t, week, len, deload)
            week += len + if (deload) 1 else 0
        }
        blocks += Block(BlockType.REVIEW, week, 1, followedByDeloadOrPivot = false)
        val used = week
        val flex = maxOf(0, 52 - used)
        val program = Program(blocks, flex, priorities)
        return EngineResult(program, listOf(Decision(DecisionKind.VOLUME_CHANGE, listOf(RuleIds.PER_002, RuleIds.PER_003, RuleIds.PER_006),
            ReasonKey.PROGRAM_PLANNED, inputs = mapOf("priorities" to priorities.map { it.name }),
            outputs = mapOf("blocks" to blocks.map { "${it.type}:${it.startWeek}+${it.loadingWeeks}" }, "flex" to flex))))
    }

    /** The context of a block-clock week (1-based). Weeks past the review are flex weeks. */
    fun context(program: Program, clockWeek: Int): WeekContext {
        for ((i, b) in program.blocks.withIndex()) {
            if (clockWeek in b.startWeek..b.endWeek) {
                val kind = when (b.type) { BlockType.CALIBRATE -> WeekKind.CALIBRATION; BlockType.REVIEW -> WeekKind.REVIEW; else -> WeekKind.LOADING }
                return WeekContext(clockWeek, b, clockWeek - b.startWeek + 1, kind, i)
            }
            if (b.followedByDeloadOrPivot && clockWeek == b.endWeek + 1) return WeekContext(clockWeek, b, b.loadingWeeks + 1, WeekKind.DELOAD_OR_PIVOT, i)
        }
        val last = program.blocks.last()
        return WeekContext(clockWeek, last, clockWeek - last.startWeek + 1, WeekKind.FLEX, program.blocks.lastIndex)
    }

    /** Default deload-or-pivot weeks of a plan (8, 14, 20, 26, 32, 38, 44 without priority changes). */
    fun deloadWeeks(program: Program): List<Int> = program.blocks.filter { it.followedByDeloadOrPivot }.map { it.endWeek + 1 }

    /** PER-006: the #1 priority's focus muscles start the block at block-start + 2 sets. */
    fun prioritySetBonus(isTopPriorityFocus: Boolean): Int = if (isTopPriorityFocus) P.PER_006.top_priority_extra_sets else 0

    /** Block doses from the Phase 1 blueprint (section 25), with VOL-003 landmarks for the level. */
    fun dose(type: BlockType, level: Level): BlockDose {
        val start = Volume.blockStart(level)
        val maint = Volume.maintenance(level)
        val work = level.pick(P.VOL_003.working_range.beginner, P.VOL_003.working_range.intermediate, P.VOL_003.working_range.advanced)
        val mid = (work[0] + work[1]) / 2
        fun r(a: Double, b: Double) = a..b
        return when (type) {
            BlockType.CALIBRATE -> BlockDose(8..12, 8..12, r(3.0, 4.0), 10..15, r(3.0, 3.0), maxOf(maint, start - 2), maxOf(maint, start - 2), 0, 0, false)
            BlockType.FOUNDATION -> BlockDose(8..10, 10..12, r(2.0, 3.0), 10..15, r(2.0, 2.0), start, start + 3, 0, 0, true)
            BlockType.BUILD -> BlockDose(6..8, 8..10, r(1.0, 3.0), 8..15, r(1.0, 2.0), work[0], work[1], 1, 1, true)
            BlockType.STRENGTH -> BlockDose(3..4, 5..6, r(1.0, 3.0), 8..15, r(1.0, 2.0), work[0], mid, 1, 0, true)
            BlockType.CONDITIONING -> BlockDose(4..6, 4..6, r(1.0, 3.0), 10..15, r(2.0, 3.0), maint, start, 2, 1, true, mainSetsOverride = 2)
            BlockType.BUILD_2 -> BlockDose(6..8, 8..10, r(1.0, 2.0), 8..20, r(0.0, 2.0), mid, work[1], 1, 1, true)
            BlockType.STRENGTH_2 -> BlockDose(3..4, 5..6, r(1.0, 2.0), 8..12, r(1.0, 2.0), work[0], mid, 1, 0, true)
            BlockType.POWER_ATHLETICISM -> BlockDose(4..6, 4..6, r(1.0, 3.0), 8..12, r(1.0, 2.0), start, mid, 2, 0, true)
            BlockType.CONSOLIDATION -> BlockDose(4..6, 8..12, r(1.0, 3.0), 8..15, r(1.0, 2.0), work[0], work[1], 2, 1, true)
            BlockType.REVIEW -> BlockDose(6..10, 6..10, r(3.0, 4.0), 10..15, r(3.0, 3.0), maint, maint, 0, 0, false)
        }
    }

    enum class Exposure { HEAVY, MODERATE }

    /**
     * PER-001: beginners use one rep range for every exposure (session-to-session progression);
     * intermediate and advanced lifters alternate heavier and lighter exposures (DUP).
     */
    fun mainReps(type: BlockType, level: Level, exposure: Exposure): IntRange {
        val d = dose(type, level)
        if (level == Level.BEGINNER) return minOf(d.heavyReps.first, d.moderateReps.first)..maxOf(d.heavyReps.last, d.moderateReps.last)
        return if (exposure == Exposure.HEAVY) d.heavyReps else d.moderateReps
    }

    /** PER-001: does this level alternate heavy and moderate exposures within the week? */
    fun usesDup(level: Level): Boolean = level != Level.BEGINNER

    /** HIIT sessions planned this week (blueprint), before the HIIT-001/003 checks in the planner. */
    fun hiitPerWeek(type: BlockType, level: Level, weekInBlock: Int, daysPerWeek: Int): Int {
        val d = dose(type, level)
        return when {
            type == BlockType.FOUNDATION -> if (weekInBlock >= 3) 1 else 0 // "0 → 1 (short 1:2)"
            (type == BlockType.POWER_ATHLETICISM || type == BlockType.CONSOLIDATION) && (level == Level.BEGINNER || daysPerWeek < 4) -> 1
            else -> d.hiitPerWeek
        }
    }

    // ------------------------------------------------------------ PER-004 and PER-005

    enum class CheckIn { E1RM_TRENDS, AEROBIC_10_MIN_CR10_5, BODYWEIGHT_REPS_RIR1, MEASUREMENTS_OPTIONAL, ROW_2000M_OPT_IN }

    /** PER-004: block-end check-ins, all non-maximal; the 2,000 m row is opt-in for intermediate and advanced. */
    fun checkIns(level: Level): List<CheckIn> =
        listOf(CheckIn.E1RM_TRENDS, CheckIn.AEROBIC_10_MIN_CR10_5, CheckIn.BODYWEIGHT_REPS_RIR1, CheckIn.MEASUREMENTS_OPTIONAL) +
            if (level == Level.BEGINNER) emptyList() else listOf(CheckIn.ROW_2000M_OPT_IN)

    val aerobicCheckMinutes: Int get() = P.PER_004.aerobic_check_minutes
    val aerobicCheckCr10: Int get() = P.PER_004.aerobic_check_cr10

    enum class Disruption { NONE, TRAVEL, HOLIDAY, BREAK }

    enum class ClockAction { ADVANCE, PAUSE, SUBSTITUTION_MODE, HOLIDAY_WEEK, RETURN_TO_TRAINING, RESTART_BLOCK }

    data class ClockStep(val nextClockWeek: Int, val action: ClockAction)

    /**
     * PER-005 block clock: a week with < 50% of planned sessions does not count; travel keeps
     * the clock running in substitution mode; a marked holiday becomes a deload, pivot or
     * maintenance week; breaks of ≥ 14 days follow REG-004 and ≥ 4 weeks restart the block.
     */
    fun advanceClock(program: Program, clockWeek: Int, plannedSessions: Int, completedSessions: Int, disruption: Disruption = Disruption.NONE, breakDays: Int = 0): EngineResult<ClockStep> {
        val ctx = context(program, clockWeek)
        val step = when {
            // A finished block is not restarted: a break during its deload-or-pivot week restarts the next block; flex and review weeks stay put.
            disruption == Disruption.BREAK && breakDays >= P.PER_005.restart_block_after_weeks * 7 -> ClockStep(when (ctx.kind) {
                WeekKind.DELOAD_OR_PIVOT -> program.blocks.getOrNull(ctx.blockIndex + 1)?.startWeek ?: clockWeek
                WeekKind.FLEX, WeekKind.REVIEW -> clockWeek
                else -> ctx.block.startWeek
            }, ClockAction.RESTART_BLOCK)
            disruption == Disruption.BREAK && breakDays >= 14 -> ClockStep(clockWeek, ClockAction.RETURN_TO_TRAINING)
            disruption == Disruption.HOLIDAY -> ClockStep(clockWeek, ClockAction.HOLIDAY_WEEK)
            disruption == Disruption.TRAVEL && completedSessions.toDouble() >= plannedSessions * P.PER_005.pause_below_session_share ->
                ClockStep(clockWeek + 1, ClockAction.SUBSTITUTION_MODE)
            plannedSessions > 0 && completedSessions.toDouble() < plannedSessions * P.PER_005.pause_below_session_share ->
                ClockStep(clockWeek, ClockAction.PAUSE)
            else -> ClockStep(clockWeek + 1, ClockAction.ADVANCE)
        }
        val reason = when (step.action) {
            ClockAction.PAUSE -> ReasonKey.BLOCK_CLOCK_PAUSED; ClockAction.RESTART_BLOCK -> ReasonKey.BLOCK_RESTARTED
            ClockAction.RETURN_TO_TRAINING -> ReasonKey.RETURN_RAMP; else -> ReasonKey.WEEK_PLANNED
        }
        return EngineResult(step, listOf(Decision(DecisionKind.RETURN_TO_TRAINING, listOf(RuleIds.PER_005), reason,
            inputs = mapOf("clockWeek" to clockWeek, "planned" to plannedSessions, "completed" to completedSessions, "disruption" to disruption.name, "breakDays" to breakDays),
            outputs = mapOf("next" to step.nextClockWeek, "action" to step.action.name))))
    }
}
