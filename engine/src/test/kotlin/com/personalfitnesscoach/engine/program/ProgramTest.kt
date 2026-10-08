package com.personalfitnesscoach.engine.program

import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Muscle
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.safety.Caps
import com.personalfitnesscoach.engine.safety.HiitContext
import com.personalfitnesscoach.engine.model.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A fully equipped gym: every library item plus the allowed conditioning machines. */
val FULL_GYM: Set<String> = Library.all.flatMap { it.equipment }.toSet() + setOf("rower", "skierg", "elliptical", "sled", "battle_ropes", "jump_rope", "medicine_ball")

fun weekInput(days: Int, week: Int, level: Level = Level.INTERMEDIATE, minutes: Int = 60, program: Program = Blueprint.plan(listOf(Goal.GENERAL_FITNESS)).value,
              gym: Set<String> = FULL_GYM, baseReady: Boolean = true) =
    WeekInput(level, 40, days, gym, Blueprint.context(program, week), age = 35, sessionMinutes = minutes, hiitBaseReady = baseReady, hiitDoneEver = 4)

class BlueprintTest {
    private val default = Blueprint.plan(emptyList()).value

    @Test fun `TC-PER-001a beginners progress session to session on one range, others alternate heavy and moderate`() {
        assertFalse(Blueprint.usesDup(Level.BEGINNER)); assertTrue(Blueprint.usesDup(Level.INTERMEDIATE))
        assertEquals(Blueprint.mainReps(BlockType.STRENGTH, Level.BEGINNER, Blueprint.Exposure.HEAVY), Blueprint.mainReps(BlockType.STRENGTH, Level.BEGINNER, Blueprint.Exposure.MODERATE))
        assertNotEquals(Blueprint.mainReps(BlockType.STRENGTH, Level.ADVANCED, Blueprint.Exposure.HEAVY), Blueprint.mainReps(BlockType.STRENGTH, Level.ADVANCED, Blueprint.Exposure.MODERATE))
        assertEquals(3..4, Blueprint.mainReps(BlockType.STRENGTH, Level.INTERMEDIATE, Blueprint.Exposure.HEAVY))
    }

    @Test fun `TC-PER-001b a planned strength week gives each main pattern a heavier and a lighter exposure`() {
        val plan = WeekPlanner.plan(weekInput(3, 16, program = default), default).value
        assertEquals(BlockType.STRENGTH, plan.blockType)
        val squats = plan.days.flatMap { it.slots }.filter { it.exercise.pattern == Pattern.SQUAT && it.exercise.trackE1rm }
        assertTrue(squats.size >= 2)
        assertTrue(squats.map { it.reps }.toSet().size >= 2) // heavy and moderate ranges differ
        assertTrue(squats.any { it.reps.last <= 6 })
    }

    @Test fun `TC-PER-002a blocks run 5 loading weeks by default, each followed by a deload-or-pivot week`() {
        val loading = default.blocks.filter { it.type != BlockType.CALIBRATE && it.type != BlockType.REVIEW }
        assertTrue(loading.all { it.loadingWeeks == P.PER_002.default_weeks })
        assertEquals(listOf(8, 14, 20, 26, 32, 38, 44), Blueprint.deloadWeeks(default))
    }

    @Test fun `TC-PER-002b priority-adjusted blocks stay within 4-6 weeks`() {
        for (top in Goal.entries) for (low in Goal.entries) if (top != low) {
            val p = Blueprint.plan(listOf(top, low)).value
            assertTrue(p.blocks.filter { it.type != BlockType.CALIBRATE && it.type != BlockType.REVIEW }.all { it.loadingWeeks in 4..6 })
            assertTrue("$top/$low ${p.totalWeeks}", p.totalWeeks <= 52)
        }
    }

    @Test fun `TC-PER-003a the year runs calibrate, foundation, build, strength, conditioning, build II, strength II, power, consolidation, review`() {
        assertEquals(P.PER_003.sequence, default.blocks.map { it.type.name.lowercase().replace("power_athleticism", "power_athleticism") })
        assertEquals(2, default.blocks.first().loadingWeeks)
        assertEquals(50, default.blocks.last().startWeek); assertEquals(2, default.flexWeeks); assertEquals(52, default.totalWeeks)
    }

    @Test fun `TC-PER-003b weeks map to blocks and are never skipped`() {
        assertEquals(WeekKind.CALIBRATION, Blueprint.context(default, 1).kind)
        assertEquals(BlockType.FOUNDATION, Blueprint.context(default, 3).block.type)
        val w8 = Blueprint.context(default, 8); assertEquals(WeekKind.DELOAD_OR_PIVOT, w8.kind); assertEquals(BlockType.FOUNDATION, w8.block.type)
        assertEquals(BlockType.BUILD, Blueprint.context(default, 9).block.type); assertEquals(1, Blueprint.context(default, 9).weekInBlock)
        assertEquals(WeekKind.REVIEW, Blueprint.context(default, 50).kind)
        assertEquals(WeekKind.FLEX, Blueprint.context(default, 51).kind)
        val seen = (1..52).map { Blueprint.context(default, it).block.type }.distinct()
        assertEquals(default.blocks.map { it.type }, seen)
    }

    @Test fun `TC-PER-004a block-end check-ins are non-maximal, the 2,000 m row is opt-in from intermediate`() {
        assertFalse(Blueprint.CheckIn.ROW_2000M_OPT_IN in Blueprint.checkIns(Level.BEGINNER))
        assertTrue(Blueprint.CheckIn.ROW_2000M_OPT_IN in Blueprint.checkIns(Level.ADVANCED))
        assertEquals(10, Blueprint.aerobicCheckMinutes); assertEquals(5, Blueprint.aerobicCheckCr10)
    }

    @Test fun `TC-PER-004b no check-in is a maximal test`() {
        assertTrue(Blueprint.CheckIn.entries.none { it.name.contains("MAX") || it.name.contains("1RM_TEST") })
        // The only all-out effort is the 2,000 m row, and it is opt-in.
        assertTrue(Blueprint.CheckIn.entries.filter { it.name.contains("2000M") }.all { it.name.endsWith("OPT_IN") })
        assertTrue(Blueprint.CheckIn.E1RM_TRENDS in Blueprint.checkIns(Level.BEGINNER))
    }

    @Test fun `TC-PER-005a a week with under half the sessions pauses the block clock`() {
        assertEquals(Blueprint.ClockStep(10, Blueprint.ClockAction.PAUSE), Blueprint.advanceClock(default, 10, 3, 1).value)
        assertEquals(Blueprint.ClockStep(11, Blueprint.ClockAction.ADVANCE), Blueprint.advanceClock(default, 10, 3, 2).value)
        assertEquals(ReasonKey.BLOCK_CLOCK_PAUSED, Blueprint.advanceClock(default, 10, 4, 1).decisions.single().reason)
    }

    @Test fun `TC-PER-005b travel, holidays and breaks`() {
        assertEquals(Blueprint.ClockAction.SUBSTITUTION_MODE, Blueprint.advanceClock(default, 10, 3, 2, Blueprint.Disruption.TRAVEL).value.action)
        assertEquals(Blueprint.ClockAction.HOLIDAY_WEEK, Blueprint.advanceClock(default, 10, 3, 0, Blueprint.Disruption.HOLIDAY).value.action)
        assertEquals(Blueprint.ClockAction.RETURN_TO_TRAINING, Blueprint.advanceClock(default, 10, 3, 0, Blueprint.Disruption.BREAK, breakDays = 15).value.action)
        val restart = Blueprint.advanceClock(default, 11, 3, 0, Blueprint.Disruption.BREAK, breakDays = 30).value
        assertEquals(Blueprint.ClockAction.RESTART_BLOCK, restart.action); assertEquals(9, restart.nextClockWeek)
    }

    @Test fun `TC-PER-006a the top priority's blocks gain a week and the lowest priority's lose one`() {
        val p = Blueprint.plan(listOf(Goal.STRENGTH, Goal.MUSCLE, Goal.CARDIO)).value
        assertEquals(6, p.blocks.first { it.type == BlockType.STRENGTH }.loadingWeeks)
        assertEquals(6, p.blocks.first { it.type == BlockType.STRENGTH_2 }.loadingWeeks)
        assertEquals(4, p.blocks.first { it.type == BlockType.CONDITIONING }.loadingWeeks)
        assertEquals(1, p.flexWeeks); assertEquals(52, p.totalWeeks)
    }

    @Test fun `TC-PER-006b the top priority's focus muscles start the block 2 sets higher`() {
        val dose = Blueprint.dose(BlockType.FOUNDATION, Level.INTERMEDIATE)
        val i = weekInput(4, 3).copy(priorities = listOf(Goal.MUSCLE, Goal.CARDIO), focusMuscles = setOf(Muscle.CHEST))
        assertEquals(WeekPlanner.target(Muscle.LATS, i, dose, 1, false) + 2, WeekPlanner.target(Muscle.CHEST, i, dose, 1, false), 1e-9)
        val notTop = i.copy(priorities = listOf(Goal.CARDIO, Goal.MUSCLE))
        assertEquals(WeekPlanner.target(Muscle.LATS, notTop, dose, 1, false), WeekPlanner.target(Muscle.CHEST, notTop, dose, 1, false), 1e-9)
    }
}

class SchedulingTest {
    private val program = Blueprint.plan(listOf(Goal.GENERAL_FITNESS)).value

    @Test fun `TC-SCH-001a templates by days available`() {
        assertEquals(listOf(DayTemplate.FB_A, DayTemplate.FB_B), Templates.forDays(2))
        assertEquals(listOf(DayTemplate.FB_A, DayTemplate.FB_B, DayTemplate.FB_C), Templates.forDays(3))
        assertEquals(listOf(DayTemplate.UPPER_H, DayTemplate.LOWER_H, DayTemplate.UPPER_M, DayTemplate.LOWER_M), Templates.forDays(4))
        assertEquals(5, Templates.forDays(5).size); assertTrue(DayTemplate.COND_CORE in Templates.forDays(5))
        assertTrue(DayTemplate.EASY_AEROBIC_MOBILITY in Templates.forDays(6))
    }

    @Test fun `TC-SCH-001b every template meets weekly pattern coverage in a 60-minute plan`() {
        for (days in 2..6) for (week in listOf(4, 10, 16, 22)) {
            val plan = WeekPlanner.plan(weekInput(days, week, program = program), program).value
            assertTrue("$days days week $week: ${plan.issues}", plan.issues.isEmpty())
            assertEquals(days, plan.days.size)
        }
    }

    @Test fun `TC-SCH-002a heavy lower-body days are at least 48 hours apart`() {
        for (days in 2..6) for (pref in listOf(setOf(0, 1, 2, 3, 4), setOf(0, 1, 5, 6), emptySet())) {
            val plan = WeekPlanner.plan(weekInput(days, 10, program = program).copy(preferredDays = pref), program).value
            val heavy = plan.days.filter { it.heavyLower }.map { it.weekday }
            for (a in heavy) for (b in heavy) if (a != b) assertTrue("$days $pref $heavy", ((b - a + 7) % 7) >= 2)
        }
    }

    @Test fun `TC-SCH-002b never more than 3 hard days in a row, and advanced lifters get 72 hours where possible`() {
        for (days in 5..6) {
            val plan = WeekPlanner.plan(weekInput(days, 10, program = program), program).value
            val hard = plan.days.filter { it.template.hard }.map { it.weekday }.toSet()
            var run = 0; var max = 0
            for (d in 0 until 14) { if ((d % 7) in hard) { run++; max = maxOf(max, run) } else run = 0 }
            assertTrue("$days days: $hard", max <= P.SCH_002.max_consecutive_hard_days)
        }
        val adv = Templates.assign(Templates.forDays(4), (0..6).toSet(), emptySet(), Level.ADVANCED).value
        val heavy = adv.days.zip(adv.order).filter { it.second.heavyLower }.map { it.first }
        assertTrue(((heavy[1] - heavy[0] + 7) % 7) >= 3 && ((heavy[0] - heavy[1] + 7) % 7) >= 3)
    }

    @Test fun `TC-SCH-003a training on an unplanned day runs the next session in sequence`() {
        val plan = WeekPlanner.plan(weekInput(3, 10, program = program), program).value
        val first = plan.days.first()
        assertEquals(first.template, WeekPlanner.nextSession(plan, emptySet())!!.template)
        assertEquals(plan.days[1].template, WeekPlanner.nextSession(plan, setOf(first.weekday))!!.template)
    }

    @Test fun `TC-SCH-003b only 2 days this week re-plans by priority, keeping coverage and the HIIT cap`() {
        val r = WeekPlanner.replanReduced(weekInput(4, 10, program = program), daysLeft = 2, availableDays = setOf(3, 5), program = program)
        assertEquals(listOf(3, 5), r.value.days.map { it.weekday })
        assertTrue(r.value.issues.isEmpty())
        val hiit = r.value.days.count { d -> d.conditioning.any { it.hiit } }
        assertTrue(hiit <= Caps.hiitPerWeek(HiitContext(Level.INTERMEDIATE, Tier.FULL, 0, false, false)).value)
        assertTrue(r.value.days.all { d -> d.slots.firstOrNull()?.let { it.main || it.power } ?: false })
        assertTrue(r.decisions.any { it.reason == ReasonKey.WEEK_DAYS_REDUCED })
    }
}

class CoreAndAdherencePlanTest {
    private val program = Blueprint.plan(listOf(Goal.GENERAL_FITNESS)).value

    @Test fun `TC-CORE-001b planned weeks never include crunch-type moves by default`() {
        for (days in 2..6) {
            val plan = WeekPlanner.plan(weekInput(days, 10, program = program), program).value
            assertTrue(plan.days.flatMap { it.slots }.none { it.exercise.userAddOnly || "spinal_flexion" in it.exercise.limitationTags })
        }
    }

    @Test fun `TC-CORE-003a 6-12 core sets a week across 2-4 sessions`() {
        for (days in 2..6) {
            val plan = WeekPlanner.plan(weekInput(days, 10, program = program), program).value
            val coreSlots = plan.days.flatMap { d -> d.slots.filter(WeekPlanner::isCoreWork) }
            val coreDays = plan.days.count { d -> d.slots.any(WeekPlanner::isCoreWork) }
            assertTrue("$days days: ${coreSlots.sumOf { it.sets }}", coreSlots.sumOf { it.sets } in P.CORE_003.weekly_sets[0]..P.CORE_003.weekly_sets[1])
            assertTrue("$days days: $coreDays", coreDays in P.CORE_003.sessions[0]..maxOf(P.CORE_003.sessions[1], days))
        }
    }

    @Test fun `TC-CORE-003b at most 4 core sets in one session`() {
        for (days in 2..6) {
            val plan = WeekPlanner.plan(weekInput(days, 10, program = program).copy(sessionMinutes = 90), program).value
            for (d in plan.days) assertTrue(d.slots.filter(WeekPlanner::isCoreWork).sumOf { it.sets } <= P.CORE_003.max_sets_per_session)
        }
    }

    @Test fun `TC-ADH-003a main lifts stay fixed for the block`() {
        val week1 = WeekPlanner.plan(weekInput(4, 9, program = program), program).value
        val week2 = WeekPlanner.plan(weekInput(4, 10, program = program).copy(coreLifts = week1.coreLifts, preferences = mapOf(week1.coreLifts.values.first() to 0.0)), program)
        assertEquals(week1.coreLifts, week2.value.coreLifts)
        assertTrue(week2.decisions.any { it.reason == ReasonKey.CORE_LIFT_KEPT })
    }

    @Test fun `TC-ADH-003b accessories rotate between blocks, favourites stay`() {
        val build = WeekPlanner.plan(weekInput(4, 9, program = program), program).value
        val acc = build.days.flatMap { it.slots }.filter { it.spec.role == SlotRole.ACCESSORY || it.spec.role == SlotRole.SECONDARY }
        val prev = acc.associate { it.spec.key to it.exercise.id }
        val next = WeekPlanner.plan(weekInput(4, 15, program = program).copy(previousBlockChoices = prev), program)
        val nextAcc = next.value.days.flatMap { it.slots }.filter { it.spec.key in prev }.associate { it.spec.key to it.exercise.id }
        assertTrue(nextAcc.any { (k, v) -> prev[k] != v })
        assertTrue(next.decisions.any { it.reason == ReasonKey.ACCESSORY_ROTATED })
        val fav = prev.values.first()
        val kept = WeekPlanner.plan(weekInput(4, 15, program = program).copy(previousBlockChoices = prev, favourites = setOf(fav)), program).value
        assertTrue(kept.days.flatMap { it.slots }.any { it.exercise.id == fav })
    }

    @Test fun `deload weeks halve the sets and drop HIIT`() {
        val dw = Blueprint.deloadWeeks(program).first()
        val normal = WeekPlanner.plan(weekInput(4, dw - 1, program = program), program).value
        val deload = WeekPlanner.plan(weekInput(4, dw, program = program).copy(deload = true), program).value
        assertTrue(deload.deload)
        for ((n, dl) in normal.days.zip(deload.days)) {
            assertEquals(n.weekday, dl.weekday)
            assertTrue("${n.template}", dl.workingSets <= maxOf(1, n.workingSets / 2))
        }
        assertTrue(deload.days.none { d -> d.conditioning.any { it.hiit } })
        assertTrue(deload.days.flatMap { it.slots }.all { it.targetRir >= 3.0 })
        assertNotNull(deload.days.firstOrNull { it.mobilityMinutes > 0 })
    }
}
