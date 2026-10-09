package com.personalfitnesscoach.engine.features

import com.personalfitnesscoach.engine.calc.E1rm
import com.personalfitnesscoach.engine.conditioning.Circuits
import com.personalfitnesscoach.engine.conditioning.HiitMenu
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.generation.AwayFromGym
import com.personalfitnesscoach.engine.generation.GenerationRequest
import com.personalfitnesscoach.engine.generation.SessionGenerator
import com.personalfitnesscoach.engine.library.DrillKind
import com.personalfitnesscoach.engine.library.GeneratedLibrary
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.model.Zone
import com.personalfitnesscoach.engine.planning.Mobility
import com.personalfitnesscoach.engine.program.Achievement
import com.personalfitnesscoach.engine.program.AchievementInput
import com.personalfitnesscoach.engine.program.Achievements
import com.personalfitnesscoach.engine.program.Backup
import com.personalfitnesscoach.engine.program.BlockType
import com.personalfitnesscoach.engine.program.Blueprint
import com.personalfitnesscoach.engine.program.DayIntention
import com.personalfitnesscoach.engine.program.FULL_GYM
import com.personalfitnesscoach.engine.program.Goal
import com.personalfitnesscoach.engine.program.Planning
import com.personalfitnesscoach.engine.program.PlanningPrompt
import com.personalfitnesscoach.engine.program.WeekInput
import com.personalfitnesscoach.engine.program.WeekKind
import com.personalfitnesscoach.engine.program.WeekPlanner
import com.personalfitnesscoach.engine.progression.Calibration
import com.personalfitnesscoach.engine.progression.CalibrationStep
import com.personalfitnesscoach.engine.progression.KnownEntry
import com.personalfitnesscoach.engine.progression.KnownLoads
import com.personalfitnesscoach.engine.progression.KnownStart
import com.personalfitnesscoach.engine.safety.ConditionLimits
import com.personalfitnesscoach.engine.safety.Conditions
import com.personalfitnesscoach.engine.safety.HiitProtocol
import com.personalfitnesscoach.engine.safety.ImpactLevel
import com.personalfitnesscoach.engine.safety.UserCondition
import com.personalfitnesscoach.engine.safety.ValidationContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CAL-002, ADH-002 1.1.0, ADH-005, MOB-006, EQ-003 and CON-004 1.1.0. */
class Registry11FeaturesTest {
    private val dumbbells = (1..25).map { it * 2.0 } // 2–50 kg in 2 kg steps
    private val program = Blueprint.plan(listOf(Goal.GENERAL_FITNESS)).value
    private fun weekOf(t: BlockType) = (1..52).first { Blueprint.context(program, it).block.type == t && Blueprint.context(program, it).kind == WeekKind.LOADING }

    // ------------------------------------------------------------------ CAL-002

    @Test fun `TC-CAL-002a a valid recent set or best lift seeds the e1RM and the first load is INT-005 at 90 percent`() {
        val set = KnownEntry("goblet-squat", 30.0, 8, 2.0, daysAgo = 3)
        val r = KnownLoads.start(set, 40, false, 10, 2.0, dumbbells)
        val s = r.value as KnownStart.Estimate
        val est = E1rm.fromSet(30.0, 8, 2.0)!!
        assertEquals(est, s.estimate, 0.01); assertEquals(0, s.reductionPct)
        assertEquals(est * 0.9, s.seedE1rm, 0.01)
        assertEquals(E1rm.prescribe(s.seedE1rm, 10, 2.0, dumbbells).value, s.firstLoad, 1e-9)
        assertTrue(s.firstLoad < 30.0)
        assertTrue(r.decisions.any { it.reason == ReasonKey.KNOWN_LOAD_USED })
        // A best lift counts as 0 reps in reserve.
        val best = KnownLoads.start(KnownEntry("goblet-squat", 40.0, 3, null, bestLift = true), 40, false, 10, 2.0, dumbbells).value as KnownStart.Estimate
        assertEquals(E1rm.fromSet(40.0, 3, 0.0)!!, best.estimate, 0.01)
        // Invalid entries are ignored: reps + RIR above 12, RIR above 3, or no RIR for a recent set.
        assertEquals(KnownStart.Invalid, KnownLoads.start(KnownEntry("x", 20.0, 11, 2.0), 40, false, 10, 2.0, dumbbells).value)
        assertEquals(KnownStart.Invalid, KnownLoads.start(KnownEntry("x", 20.0, 6, 4.0), 40, false, 10, 2.0, dumbbells).value)
        assertEquals(KnownStart.Invalid, KnownLoads.start(KnownEntry("x", 20.0, 6, null), 40, false, 10, 2.0, dumbbells).value)
    }

    @Test fun `TC-CAL-002b old entries are reduced or only cap the calibration ramp, one band longer at 60 plus or with a flagged screen`() {
        fun start(days: Int, age: Int = 40, flagged: Boolean = false) = KnownLoads.start(KnownEntry("goblet-squat", 30.0, 8, 2.0, daysAgo = days), age, flagged, 10, 2.0, dumbbells).value
        assertEquals(10, (start(20) as KnownStart.Estimate).reductionPct)
        assertEquals(20, (start(40) as KnownStart.Estimate).reductionPct)
        assertTrue(start(60) is KnownStart.Ceiling)
        assertEquals(10, (start(5, age = 62) as KnownStart.Estimate).reductionPct)
        assertTrue(start(30, flagged = true) is KnownStart.Ceiling)
        assertEquals(listOf(0, 1, 2, 3), listOf(0, 14, 28, 56).map { KnownLoads.band(it, 40, false) })
        // A ceiling stops the CAL-001 ramp: never above it.
        val ceiling = (start(60) as KnownStart.Ceiling).maxLoad
        val step = Calibration.next(ceiling, 10, 5.0, 1, dumbbells, ceiling = ceiling).value
        assertTrue(step is CalibrationStep.Found && step.workingLoad <= ceiling)
        val below = Calibration.next(ceiling - 4.0, 10, 5.0, 1, dumbbells, ceiling = ceiling).value
        assertTrue(below is CalibrationStep.Continue && below.nextLoad <= ceiling + 1e-9)
    }

    // ------------------------------------------------------------------ ADH-002 1.1.0, ADH-005

    @Test fun `TC-ADH-002c welcome back after a miss, and only one neutral nudge`() {
        val base = AchievementInput(5, true, 0, 0, false)
        assertFalse(Achievement.WELCOME_BACK in Achievements.earned(base))
        assertTrue(Achievement.WELCOME_BACK in Achievements.earned(base.copy(firstAfterMiss = true)))
        assertTrue(Achievements.nudgeToday(missedSinceLastSession = true, nudgesSentSinceMiss = 0, plannedToday = true).value)
        assertFalse(Achievements.nudgeToday(true, 1, true).value)
        assertFalse(Achievements.nudgeToday(true, 0, plannedToday = false).value)
        assertFalse(Achievements.nudgeToday(false, 0, true).value)
    }

    @Test fun `TC-ADH-005a the planning prompt keeps last week's times on the same weekdays`() {
        assertEquals(30, Planning.maxSeconds)
        assertEquals(listOf(Backup.EXPRESS_SESSION, Backup.NEXT_FREE_DAY), Planning.backups)
        val last = PlanningPrompt(listOf(DayIntention(0, 7 * 60, "after dropping the kids"), DayIntention(2, 18 * 60)), Backup.EXPRESS_SESSION)
        val p = Planning.suggest(listOf(4, 0, 2), last)
        assertEquals(listOf(0, 2, 4), p.value.days.map { it.weekday })
        assertEquals(7 * 60, p.value.days[0].minuteOfDay); assertEquals("after dropping the kids", p.value.days[0].afterCue)
        assertNull(p.value.days[2].minuteOfDay)
        assertEquals(Backup.EXPRESS_SESSION, p.value.backup)
        assertTrue(p.decisions.single().reason == ReasonKey.PLANNING_PROMPT)
        assertTrue(Planning.suggest(listOf(1, 3), null).value.days.all { it.minuteOfDay == null })
    }

    @Test fun `TC-ADH-005b the if-then backup maps to the express session or the next free day, and never nags`() {
        val express = PlanningPrompt(listOf(DayIntention(0)), Backup.EXPRESS_SESSION)
        assertEquals(Backup.EXPRESS_SESSION, Planning.backupFor(express, shortOnTime = true, missedPlannedDay = false))
        assertNull(Planning.backupFor(express, shortOnTime = false, missedPlannedDay = true))
        val next = PlanningPrompt(listOf(DayIntention(0)), Backup.NEXT_FREE_DAY)
        assertEquals(Backup.NEXT_FREE_DAY, Planning.backupFor(next, false, true))
        assertNull(Planning.backupFor(PlanningPrompt(emptyList()), true, true))
    }

    // ------------------------------------------------------------------ MOB-006

    @Test fun `TC-MOB-006a a calm mobility session is offered on rest, LIGHT and RECOVERY days and on stressed days only`() {
        assertTrue(Mobility.calmSessionOffered(restDay = true, tier = null, stressItem = null))
        assertTrue(Mobility.calmSessionOffered(false, Tier.LIGHT, 4)); assertTrue(Mobility.calmSessionOffered(false, Tier.RECOVERY, 4))
        assertTrue(Mobility.calmSessionOffered(false, Tier.FULL, 2))
        assertFalse(Mobility.calmSessionOffered(false, Tier.FULL, 4)); assertFalse(Mobility.calmSessionOffered(false, Tier.MODIFIED, 3))
        val s = Mobility.calmSession(20).value
        assertFalse(s.countsAsZ1); assertFalse(s.countsAsStrengthDay)
        assertTrue(s.minutes in 15.0..30.0)
        assertEquals(DrillKind.BREATHING, s.drills.last().drill.kind)
        assertTrue(s.drills.last().amount in 180..300)
        assertTrue(s.balanceMinutes > 0)
        assertTrue(s.drills.filter { it.drill.kind == DrillKind.STRETCH }.all { it.amount in 30..60 })
    }

    @Test fun `TC-MOB-006b condition tags and joint limits apply to the calm session, and its length stays within 15 to 30 minutes`() {
        val preg = Conditions.resolve(listOf(UserCondition("pregnancy", pregnancyWeek = 24))).value
        val s = Mobility.calmSession(25, avoidTags = preg.avoidTags).value
        assertTrue(s.drills.none { d -> d.drill.tags.any { it in preg.avoidTags } })
        val limited = Mobility.calmSession(15, jointLimits = mapOf(com.personalfitnesscoach.engine.model.Joint.WRIST to 0)).value
        assertTrue(limited.drills.all { it.drill.stress(com.personalfitnesscoach.engine.model.Joint.WRIST) == 0 })
        assertTrue(Mobility.calmSession(5).value.minutes <= 15.0 + 1e-9)
        assertTrue(Mobility.calmSession(60).value.minutes <= 30.0 + 1e-9)
    }

    // ------------------------------------------------------------------ EQ-003

    private fun gymDay() = WeekPlanner.plan(WeekInput(Level.BEGINNER, 20, 3, FULL_GYM, Blueprint.context(program, weekOf(BlockType.BUILD)), age = 45,
        hiitBaseReady = true, hiitDoneEver = 2, lastWeekHiitWorkMinutes = 10.0), program).value.days.first { it.slots.isNotEmpty() && it.conditioning.isNotEmpty() }

    @Test fun `TC-EQ-003a away from the gym, strength goes bodyweight and cardio becomes a no-jump circuit`() {
        val day = gymDay()
        val r = AwayFromGym.generate(GenerationRequest(day, Level.BEGINNER, 20, 45, FULL_GYM, Tier.FULL, age = 45, week = ValidationContext(Level.BEGINNER, hiitBaseReady = true)))
        val w = r.value
        assertTrue(AwayFromGym.defaultKit.isEmpty())
        assertTrue(w.items.isNotEmpty() && w.items.all { it.exercise.usableWith(emptySet()) })
        assertTrue(w.conditioning.isNotEmpty() && w.conditioning.all { it.modality == Modality.BODYWEIGHT_CIRCUIT })
        val circuits = w.circuits.filterNotNull()
        assertTrue(circuits.isNotEmpty())
        assertTrue(circuits.all { c -> c.moves.none { it.jumping } && c.moves.size >= 3 })
        assertTrue(w.warmupMinutes >= 8.0)
        assertTrue(r.decisions.any { it.reason == ReasonKey.AWAY_FROM_GYM })
    }

    @Test fun `TC-EQ-003b beginners work 1 to 2, jumps only where impact is allowed, and a hard circuit of 8 minutes counts as HIIT`() {
        val iv = HiitMenu.start(HiitProtocol.SHORT, Level.BEGINNER, firstEver = false)
        val c = Circuits.build(Zone.Z3, iv.workMinutes, iv, Level.BEGINNER, jumping = false).value!!
        assertTrue(c.restSec >= 2 * c.workSec)
        assertTrue(c.moves.none { it.jumping })
        assertTrue(Circuits.allowedMoves(jumping = true).any { it.jumping })
        assertTrue(Circuits.allowedMoves(jumping = true, avoidTags = setOf("jumping")).none { it.jumping })
        // Jumping moves only when asked for and impact is allowed by the conditions.
        val day = gymDay()
        fun gen(c: ConditionLimits) = AwayFromGym.generate(GenerationRequest(day, Level.BEGINNER, 20, 45, FULL_GYM, Tier.FULL, age = 45,
            week = ValidationContext(Level.BEGINNER, hiitBaseReady = true), conditions = c, circuitJumps = true)).value
        assertTrue(gen(ConditionLimits(impact = ImpactLevel.LOW)).circuits.filterNotNull().all { cp -> cp.moves.none { it.jumping } })
        // HIIT-001: CR10 ≥ 7 for ≥ 8 minutes.
        val hard = Circuits.build(Zone.Z3, 8.0, com.personalfitnesscoach.engine.conditioning.Interval(HiitProtocol.SHORT, 16, 30, 0, 7..8), Level.INTERMEDIATE, false).value!!
        assertTrue(hard.countsAsHiit)
        val easy = Circuits.build(Zone.Z1, 20.0, null, Level.INTERMEDIATE, false).value!!
        assertFalse(easy.countsAsHiit)
        assertEquals(easy.moves.size * easy.rounds, easy.stations)
        assertTrue(GeneratedLibrary.modalities.first { it.modality == Modality.BODYWEIGHT_CIRCUIT }.moves.count { !it.jumping } >= 6)
    }

    // ------------------------------------------------------------------ CON-004 1.1.0

    @Test fun `TC-CON-004c the osteoporosis bone-loading block is added on training days and is not an impact session`() {
        val ost = Conditions.resolve(listOf(UserCondition("osteoporosis"))).value
        val day = gymDay()
        val w = SessionGenerator.generate(GenerationRequest(day, Level.BEGINNER, 20, 60, FULL_GYM, Tier.FULL, age = 60,
            week = ValidationContext(Level.BEGINNER, hiitBaseReady = true, impactSessionsThisWeekSoFar = 1), conditions = ost))
        val b = w.value.boneLoading
        assertNotNull(b)
        assertTrue(b!!.minutes <= 5.0 && b.landings >= 50)
        // It is not counted as impact: the validator still sees no impact conditioning and passes.
        assertTrue(w.value.validated.conditioning.all { it.impact == 0 })
        assertTrue(w.decisions.any { it.reason == ReasonKey.BONE_LOADING_BLOCK })
        // Not on a RECOVERY day, and not when osteoarthritis limits impact.
        assertNull(SessionGenerator.generate(GenerationRequest(day, Level.BEGINNER, 20, 60, FULL_GYM, Tier.RECOVERY, conditions = ost)).value.boneLoading)
        val both = Conditions.resolve(listOf(UserCondition("osteoporosis"), UserCondition("oa_knee"))).value
        assertNull(SessionGenerator.generate(GenerationRequest(day, Level.BEGINNER, 20, 60, FULL_GYM, Tier.FULL, conditions = both)).value.boneLoading)
    }
}
