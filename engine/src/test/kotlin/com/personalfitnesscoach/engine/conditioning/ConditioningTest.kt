package com.personalfitnesscoach.engine.conditioning

import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.dose.Order
import com.personalfitnesscoach.engine.dose.OrderSlot
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.model.Zone
import com.personalfitnesscoach.engine.safety.HiitProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AerobicTest {
    @Test fun `TC-AER-001a zones by CR10 and talk test, with WHO intensity`() {
        assertEquals(Zone.Z1, Zones.fromCr10(3)); assertEquals(Zone.Z1, Zones.fromCr10(4))
        assertEquals(Zone.Z2, Zones.fromCr10(5)); assertEquals(Zone.Z3, Zones.fromCr10(7)); assertEquals(Zone.Z3, Zones.fromCr10(9))
        assertEquals(Zone.Z4, Zones.fromCr10(10)); assertNull(Zones.fromCr10(2))
        assertEquals(Zone.Z1, Zones.fromTalkTest(TalkTest.FULL_SENTENCES)); assertEquals(Zone.Z3, Zones.fromTalkTest(TalkTest.FEW_WORDS))
        assertEquals(WhoIntensity.MODERATE, Zones.who(Zone.Z1)); assertEquals(WhoIntensity.VIGOROUS, Zones.who(Zone.Z2))
    }

    @Test fun `TC-AER-001b optional heart-rate reserve targets`() {
        assertEquals(112..137, Zones.heartRateRange(Zone.Z1, restingHr = 60, maxHr = 190))
        assertEquals(138..163, Zones.heartRateRange(Zone.Z2, 60, 190))
        assertNull(Zones.heartRateRange(Zone.Z4, 60, 190))
        assertNull(Zones.heartRateRange(Zone.Z1, 60, 50))
    }

    @Test fun `TC-AER-002a about 75-80 percent of aerobic minutes in Z1`() {
        assertTrue(Aerobic.distributionOk(listOf(AerobicBlock(Zone.Z1, 80.0, 1), AerobicBlock(Zone.Z3, 20.0, 3))))
        assertFalse(Aerobic.distributionOk(listOf(AerobicBlock(Zone.Z1, 70.0, 1), AerobicBlock(Zone.Z2, 30.0, 3))))
    }

    @Test fun `TC-AER-002b hard minutes allowed alongside Z1, all-Z1 weeks are fine`() {
        assertEquals(30.0, Aerobic.maxHardMinutes(90.0), 1e-9)
        assertTrue(Aerobic.distributionOk(listOf(AerobicBlock(Zone.Z1, 40.0, 1))))
        assertTrue(Aerobic.distributionOk(emptyList()))
    }

    @Test fun `TC-AER-003a duration first, 2-5 minutes a session toward 30-40 minutes of Z1`() {
        assertEquals(23.0, Aerobic.nextZ1Minutes(20.0, lastWeekMin = 60.0, plannedThisWeekMin = 40.0, sessionsLeft = 1).value, 1e-9)
        assertEquals(40.0, Aerobic.nextZ1Minutes(40.0, 120.0, 0.0, 3).value, 1e-9)
        assertEquals(Aerobic.Stage.BUILD_Z1, Aerobic.stage(25.0, 0, true))
    }

    @Test fun `TC-AER-003b weekly aerobic minutes rise at most 15 percent, then tempo, then intervals`() {
        // Last week 60 min → this week ≤ 69; 48 already planned in the other sessions, one session left → 21 (+1 only).
        assertEquals(21.0, Aerobic.nextZ1Minutes(20.0, 60.0, 48.0, 1).value, 1e-9)
        // Three sessions sharing a 45-min week: ≤ 51.75 in total, so 17.25 each, not 18 (review finding 8).
        assertEquals(17.2, Aerobic.nextZ1Minutes(15.0, 45.0, 0.0, 3).value, 1e-9)
        assertEquals(Aerobic.Stage.ADD_TEMPO, Aerobic.stage(30.0, 0, true))
        assertEquals(Aerobic.Stage.ADD_TEMPO, Aerobic.stage(35.0, 3, hiitBaseReady = false))
        assertEquals(Aerobic.Stage.ADD_INTERVALS, Aerobic.stage(35.0, 2, true))
    }

    @Test fun `TC-PH-001a equivalent minutes count vigorous work twice`() {
        val c = Aerobic.whoCheck(listOf(AerobicBlock(Zone.Z1, 90.0, 1), AerobicBlock(Zone.Z3, 30.0, 3)), strengthDays = 2).value
        assertEquals(150.0, c.equivalentMinutes, 1e-9); assertTrue(c.meetsAerobic && c.meetsStrength)
    }

    @Test fun `TC-PH-001b a week below the floor says so and suggests walking`() {
        val r = Aerobic.whoCheck(listOf(AerobicBlock(Zone.Z1, 60.0, 1), AerobicBlock(Zone.Z2, 20.0, 3)), strengthDays = 1)
        assertEquals(100.0, r.value.equivalentMinutes, 1e-9)
        assertEquals(50.0, r.value.suggestedWalkingMinutes, 1e-9)
        assertFalse(r.value.meetsStrength)
        assertEquals(ReasonKey.WHO_FLOOR_SHORT, r.decisions.single().reason)
        assertTrue(Aerobic.whoCheck(listOf(AerobicBlock(Zone.Z1, 60.0, 1), AerobicBlock(Zone.Z2, 20.0, 3)), 2, walkingMinutes = 50.0).value.meetsAerobic)
    }

    @Test fun `TC-FREQ-005a aerobic work on 3 or more days when training 3 or more days`() {
        val three = listOf(AerobicBlock(Zone.Z1, 10.0, 1), AerobicBlock(Zone.Z1, 15.0, 3), AerobicBlock(Zone.Z3, 12.0, 5))
        assertTrue(Aerobic.frequencyOk(three, trainingDays = 3))
        assertFalse(Aerobic.frequencyOk(three.take(2), trainingDays = 3))
    }

    @Test fun `TC-FREQ-005b short post-strength blocks count from 10 minutes, 2-day plans are exempt`() {
        val short = listOf(AerobicBlock(Zone.Z1, 8.0, 1), AerobicBlock(Zone.Z1, 15.0, 3), AerobicBlock(Zone.Z1, 15.0, 5))
        assertFalse(Aerobic.frequencyOk(short, 4))
        assertTrue(Aerobic.frequencyOk(short.take(1), trainingDays = 2))
    }
}

class ConcurrentTest {
    @Test fun `TC-CON-001a strength comes before conditioning`() {
        val seq = Order.sequence(conditioningPriority = false)
        assertTrue(seq.indexOf(OrderSlot.PRIMARY_COMPOUND) < seq.indexOf(OrderSlot.CONDITIONING))
        assertTrue(seq.indexOf(OrderSlot.ACCESSORIES) < seq.indexOf(OrderSlot.CONDITIONING))
    }

    @Test fun `TC-CON-001b except on conditioning-priority days`() {
        val seq = Order.sequence(conditioningPriority = true)
        assertTrue(seq.indexOf(OrderSlot.CONDITIONING) < seq.indexOf(OrderSlot.PRIMARY_COMPOUND))
    }

    @Test fun `TC-CON-002a two hard sessions on one day need 6 hours or they merge strength-first`() {
        assertEquals(Concurrent.SameDay.MERGE_STRENGTH_FIRST, Concurrent.sameDay(5.0))
        assertEquals(Concurrent.SameDay.SEPARATE_OK, Concurrent.sameDay(6.0))
    }

    @Test fun `TC-CON-002b hard aerobic means HIIT or 30 minutes of Z2 and above`() {
        assertFalse(Concurrent.hardAerobic(false, 29.0)); assertTrue(Concurrent.hardAerobic(false, 30.0)); assertTrue(Concurrent.hardAerobic(true, 0.0))
    }

    @Test fun `TC-CON-005a power never follows HIIT the same day`() {
        assertFalse(Concurrent.powerAllowed(hiitEarlierToday = true, atSessionStart = true))
        assertTrue(Concurrent.powerAllowed(hiitEarlierToday = false, atSessionStart = true))
    }

    @Test fun `TC-CON-005b power sits at the start of the session`() {
        assertFalse(Concurrent.powerAllowed(false, atSessionStart = false))
        assertEquals(OrderSlot.POWER, Order.sequence(false)[1])
        assertEquals(OrderSlot.POWER, Order.sequence(true)[1])
    }

    @Test fun `TC-CON-006a strength blocks keep aerobic work mostly Z1 and at most 150 minutes`() {
        assertTrue(Aerobic.strengthBlockOk(listOf(AerobicBlock(Zone.Z1, 120.0, 1), AerobicBlock(Zone.Z3, 30.0, 3))))
        assertFalse(Aerobic.strengthBlockOk(listOf(AerobicBlock(Zone.Z1, 140.0, 1), AerobicBlock(Zone.Z3, 20.0, 3))))
    }

    @Test fun `TC-CON-006b too much hard work fails even under 150 minutes`() {
        assertFalse(Aerobic.strengthBlockOk(listOf(AerobicBlock(Zone.Z1, 100.0, 1), AerobicBlock(Zone.Z2, 50.0, 3))))
    }
}

class ModalityTest {
    @Test fun `TC-MOD-002a selection score follows the registry weights`() {
        val ctx = ModalityContext(setOf("rower"), ConditioningPurpose.STEADY, legPriority = 1.0, jointSensitivity = 1.0)
        // 0.35·5/5 + 0.25·(1−2/5)·1 + 0.20·(1−1/5)·1 + 0.10·1 + 0.10·0.5
        assertEquals(0.81, ModalitySelection.score(Modality.ROWER, ctx), 1e-9)
        // Variety: used in the last two sessions loses the variety term.
        assertEquals(0.71, ModalitySelection.score(Modality.ROWER, ctx.copy(recentModalities = listOf(Modality.ROWER))), 1e-9)
    }

    @Test fun `TC-MOD-002b hard filters remove excluded, unavailable, painful and impact modalities`() {
        val eq = setOf("rower", "skierg", "jump_rope", "treadmill", "spin_bike")
        val excluded = setOf(Modality.TREADMILL_RUN, Modality.TREADMILL_WALK, Modality.STATIONARY_BIKE)
        val ranked = ModalitySelection.rank(ModalityContext(eq, ConditioningPurpose.STEADY, impactAllowed = false, excluded = excluded)).value.map { it.modality }
        assertTrue(ranked.none { it in excluded })
        assertFalse(Modality.JUMP_ROPE in ranked)
        assertEquals(Modality.ROWER, ranked.first())
        val knee = ModalitySelection.rank(ModalityContext(eq, ConditioningPurpose.STEADY, jointLimits = mapOf(Joint.KNEE to 1))).value.map { it.modality }
        assertFalse(Modality.ROWER in knee); assertTrue(Modality.SKIERG in knee)
        assertTrue(ModalitySelection.isImpact(Modality.JUMP_ROPE)); assertFalse(ModalitySelection.isImpact(Modality.ROWER))
    }
}

class HiitMenuTest {
    @Test fun `TC-HIIT-002c protocol by block and purpose, first ever is short at 1 to 2`() {
        assertEquals(HiitProtocol.SHORT, HiitMenu.choose(BlockKind.CONDITIONING, Level.ADVANCED, hiitDoneEver = 0, sprintsThisWeek = 0, sessionIndex = 0).value)
        // Blueprint: Build "1 (long)", Conditioning "long + short", Consolidation medium + short, Power short (or one sprint for advanced).
        assertEquals(HiitProtocol.LONG, HiitMenu.choose(BlockKind.BUILD, Level.INTERMEDIATE, 5, 0, 0).value)
        assertEquals(HiitProtocol.LONG, HiitMenu.choose(BlockKind.CONDITIONING, Level.INTERMEDIATE, 5, 0, 0).value)
        assertEquals(HiitProtocol.SHORT, HiitMenu.choose(BlockKind.CONDITIONING, Level.INTERMEDIATE, 5, 0, 1).value)
        assertEquals(HiitProtocol.MEDIUM, HiitMenu.choose(BlockKind.CONSOLIDATION, Level.INTERMEDIATE, 5, 0, 0).value)
        assertEquals(HiitProtocol.SPRINT, HiitMenu.choose(BlockKind.POWER, Level.ADVANCED, 5, 0, 0).value)
        assertEquals(HiitProtocol.SHORT, HiitMenu.choose(BlockKind.POWER, Level.ADVANCED, 5, sprintsThisWeek = 1, sessionIndex = 1).value)
        assertEquals(HiitProtocol.SHORT, HiitMenu.choose(BlockKind.POWER, Level.INTERMEDIATE, 5, 0, 0).value)
        assertEquals(HiitProtocol.SHORT, HiitMenu.choose(BlockKind.STRENGTH, Level.INTERMEDIATE, 5, 0, 0).value)
        val first = HiitMenu.start(HiitProtocol.SHORT, Level.BEGINNER, firstEver = true)
        assertEquals(10, first.reps); assertEquals(15, first.workSec); assertEquals(30, first.restSec); assertEquals(7..8, first.cr10)
        assertTrue(first.workMinutes <= HiitMenu.workCapMinutes(HiitProtocol.SHORT))
        val sprint = HiitMenu.start(HiitProtocol.SPRINT, Level.ADVANCED, false)
        assertTrue(sprint.restSec >= 6 * sprint.workSec)
    }

    @Test fun `TC-PROG-006a intervals progress by a repeat, then longer work, then shorter rest`() {
        var i = HiitMenu.start(HiitProtocol.LONG, Level.INTERMEDIATE, false)
        assertEquals(Interval(HiitProtocol.LONG, 3, 180, 120, 7..8), i)
        i = HiitMenu.progress(i); assertEquals(4, i.reps)
        i = HiitMenu.progress(HiitMenu.progress(i)); assertEquals(5, i.reps); assertEquals(195, i.workSec)
        i = Interval(HiitProtocol.MEDIUM, 10, 120, 90, 8..8)
        assertEquals(75, HiitMenu.progress(i).restSec)
        val top = Interval(HiitProtocol.MEDIUM, 10, 120, 60, 8..8)
        assertEquals(top, HiitMenu.progress(top)) // next lever is pace, at the same effort
    }

    @Test fun `TC-PROG-006b steady minutes first, then pace, sleds and carries distance, then load`() {
        assertEquals(23.0, Aerobic.nextZ1Minutes(20.0, 0.0, 0.0, 2).value, 1e-9)
        assertEquals(505.0, CarryProgression.nextPace(500.0), 1e-9)
        assertEquals(40 to 32.0, CarryProgression.next(30, 40, 32.0))
        val (d, load) = CarryProgression.next(40, 40, 32.0)
        assertEquals(20, d); assertEquals(33.6, load, 1e-9)
    }
}
