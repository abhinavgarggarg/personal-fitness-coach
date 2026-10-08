package com.personalfitnesscoach.engine.safety

import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.model.CostClass
import com.personalfitnesscoach.engine.model.Exercise
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.LoadType
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.model.Muscle
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.model.Zone
import com.personalfitnesscoach.engine.registry.RuleIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

private val NO = ScreeningAnswers(false, false, false, false, false, false, false, true)

class ScreeningTest {
    @Test fun `TC-SAF-001a symptoms mean clearance and conservative mode - no HIIT, no Z3, RIR 3, no failure`() {
        val r = Screening.evaluate(NO.copy(symptoms = true)).value
        assertEquals(ScreeningMode.CONSERVATIVE, r.mode)
        assertTrue(r.clearanceRecommended)
        assertFalse(r.hiitAllowed); assertFalse(r.zone3Allowed); assertFalse(r.failureAllowed)
        assertEquals(3, r.minRir)
        assertEquals(ScreeningMode.CONSERVATIVE, Screening.evaluate(NO.copy(palpitations = true)).value.mode)
        val v = SessionValidator.validate(Session(Tier.FULL, emptyList(), listOf(ConditioningBlock(Modality.ROWER, Zone.Z4, 4.0, 4.0, hiit = true))),
            ValidationContext(Level.INTERMEDIATE, screening = r.mode)).value.session
        assertTrue(v.conditioning.none { it.hiit || it.zone >= Zone.Z3 })
    }

    @Test fun `TC-SAF-001b disease, limits, musculoskeletal answers, clearance and re-screening`() {
        assertEquals(ScreeningMode.CONSERVATIVE, Screening.evaluate(NO.copy(heartOrBloodPressure = true, regularlyActive = false)).value.mode)
        val active = Screening.evaluate(NO.copy(metabolicRenalPulmonary = true)).value
        assertEquals(ScreeningMode.MODERATE_ONLY, active.mode); assertFalse(active.hiitAllowed); assertFalse(active.failureAllowed)
        val q5 = Screening.evaluate(NO.copy(limitOrPregnancy = true)).value
        assertEquals(ScreeningMode.CONSERVATIVE, q5.mode); assertTrue(q5.clinicianGuidance)
        val msk = Screening.evaluate(NO.copy(musculoskeletal = true)).value
        assertEquals(ScreeningMode.STANDARD, msk.mode); assertTrue(msk.limitationNote)
        assertEquals(ScreeningMode.STANDARD, Screening.evaluate(NO).value.mode)
        assertEquals(ScreeningMode.STANDARD, Screening.evaluate(NO.copy(symptoms = true), clearanceConfirmed = true).value.mode)
        assertTrue(Screening.rescreenDue(12)); assertFalse(Screening.rescreenDue(11)); assertTrue(Screening.rescreenDue(1, newConditionReported = true))
    }
}

class RedFlagTest {
    @Test fun `TC-SAF-002a chest pain ends the session - never a lighter workout`() {
        val r = RedFlags.check(setOf("chest_pain_pressure_tightness"))
        val stop = r.value!!
        assertEquals("112", stop.emergencyNumber)
        assertTrue(stop.resumeRequiresConfirmation)
        assertEquals(ReasonKey.SAFETY_STOP_RED_FLAG, r.decisions.single().reason)
        assertNull("no training until confirmed", RedFlags.tierAfterStop(false))
    }

    @Test fun `TC-SAF-002b first session back is LIGHT and other symptoms do not stop`() {
        assertEquals(Tier.LIGHT, RedFlags.tierAfterStop(true))
        assertNull(RedFlags.check(setOf("muscle_burn")).value)
        assertEquals("911", RedFlags.check(setOf("fainting_or_near_fainting"), "911").value!!.emergencyNumber)
        assertEquals(9, RedFlags.SYMPTOMS.size)
    }

    @Test fun `TC-SAF-007a fever locks the day to RECOVERY`() {
        assertEquals(Tier.RECOVERY, RedFlags.illnessGate(setOf("fever")).value)
        val v = SessionValidator.validate(Session(Tier.RECOVERY, listOf(SessionExercise(SQUAT, 3, 5, 2.0, main = true))), ValidationContext(Level.BEGINNER)).value
        assertTrue(v.session.exercises.isEmpty())
    }

    @Test fun `TC-SAF-007b no systemic symptom, no lock`() {
        assertNull(RedFlags.illnessGate(setOf("sore_legs")).value)
        assertTrue(RedFlags.illnessGate(setOf("vomiting", "x")).decisions.single().ruleIds.contains(RuleIds.REG_005))
    }
}

class PainGateTest {
    @Test fun `TC-SAF-003a 5 of 10 knee pain stops the exercise and allows no knee stress above 1`() {
        val o = PainGate.assess(PainReport(Joint.KNEE, PainKind.JOINT_OR_TENDON, 5)).value
        assertEquals(PainAction.STOP_EXERCISE, o.action)
        assertEquals(1, o.regionMaxStress)
        val ctx = ValidationContext(Level.INTERMEDIATE, jointLimits = mapOf(Joint.KNEE to o.regionMaxStress!!), library = LIB, equipmentToday = GYM)
        val v = SessionValidator.validate(Session(Tier.FULL, listOf(SessionExercise(SQUAT, 3, 5, 2.0, main = true), SessionExercise(ROW, 3, 10, 2.0))), ctx).value
        assertTrue(v.session.exercises.all { it.exercise.stress(Joint.KNEE) <= 1 })
        assertTrue(v.session.exercises.any { it.exercise.id == "row" })
    }

    @Test fun `TC-SAF-003b low pain continues with caution, severe or descriptive pain stops the region`() {
        val low = PainGate.assess(PainReport(Joint.SHOULDER, PainKind.JOINT_OR_TENDON, 2)).value
        assertEquals(PainAction.CONTINUE_CAUTION, low.action)
        assertEquals(0.8, low.loadFactor!!.start, 1e-9); assertEquals(0.9, low.loadFactor!!.endInclusive, 1e-9)
        assertEquals(PainAction.STOP_EXERCISE, PainGate.assess(PainReport(Joint.SHOULDER, PainKind.JOINT_OR_TENDON, 3, worsening = true)).value.action)
        val severe = PainGate.assess(PainReport(Joint.SHOULDER, PainKind.JOINT_OR_TENDON, 8)).value
        assertEquals(PainAction.STOP_REGION, severe.action); assertEquals(0, severe.regionMaxStress); assertTrue(severe.suggestProfessional)
        assertEquals(PainAction.STOP_REGION, PainGate.assess(PainReport(Joint.WRIST, PainKind.JOINT_OR_TENDON, 2, descriptors = setOf("numbness"))).value.action)
        assertTrue(PainGate.assess(PainReport(Joint.SPINE, PainKind.JOINT_OR_TENDON, 7, affectsWholeBodyMovement = true)).value.endSession)
        assertEquals(PainAction.CONTINUE, PainGate.assess(PainReport(Joint.KNEE, PainKind.MUSCLE_BURN, 6)).value.action)
        assertEquals(PainAction.CONTINUE, PainGate.assess(PainReport(Joint.KNEE, PainKind.DOMS, 6)).value.action)
    }

    @Test fun `TC-SAF-004a pain in 2 sessions keeps the region conservative and suggests a professional`() {
        val c = PainGate.regionConstraint(RegionHistory(Joint.KNEE, painfulSessions = 2, daysSpan = 3, consecutivePainFree = 0)).value!!
        assertEquals(1, c.maxStress); assertEquals(3, c.minRir); assertTrue(c.noFailure && c.noJumping && c.suggestProfessional)
        assertNotNull(PainGate.regionConstraint(RegionHistory(Joint.KNEE, 1, daysSpan = 8, consecutivePainFree = 0)).value)
        assertNull(PainGate.regionConstraint(RegionHistory(Joint.KNEE, 1, daysSpan = 2, consecutivePainFree = 0)).value)
        val ctx = ValidationContext(Level.INTERMEDIATE, regions = listOf(c))
        val light = LEG_EXT.copy(id = "leg-extension-short-range", jointStress = mapOf(Joint.KNEE to 1))
        val v = SessionValidator.validate(Session(Tier.FULL, listOf(SessionExercise(light, 3, 12, 0.0, lastSetToFailure = true),
            SessionExercise(LEG_EXT, 3, 12, 1.0))), ctx).value.session
        val e = v.exercises.single() // full-range leg extension (knee stress 2) removed: no allowed swap in an empty library
        assertEquals("leg-extension-short-range", e.exercise.id)
        assertTrue(e.targetRir >= 3.0 && !e.lastSetToFailure)
    }

    @Test fun `TC-SAF-004b after 2 pain-free sessions the region steps up one stress level per week`() {
        fun c(weeks: Int) = PainGate.regionConstraint(RegionHistory(Joint.KNEE, 3, 10, consecutivePainFree = 2, weeksSinceExit = weeks)).value
        assertEquals(1, c(0)!!.maxStress); assertEquals(2, c(1)!!.maxStress); assertEquals(3, c(2)!!.maxStress); assertNull(c(3))
        assertNull(c(0)!!.minRir)
    }
}

class CapsTest {
    @Test fun `TC-SAF-005a hard caps by level`() {
        assertEquals(listOf(12, 16, 20), Level.entries.map { Caps.weeklySetsPerMuscle(it) })
        assertEquals(listOf(6, 8, 10), Level.entries.map { Caps.directSetsPerMuscleSession(it) })
        assertEquals(listOf(20, 25, 30), Level.entries.map { Caps.workingSetsPerSession(it) })
        assertEquals(listOf(100, 140, 180), Level.entries.map { Caps.weeklySsu(it) })
        assertEquals(6, Caps.trainingDaysPerWeek())
        assertEquals(listOf(7.5, 5.0, 5.0), Level.entries.map { Caps.intensityRisePctPerWeek(it) })
    }

    @Test fun `TC-SAF-005b caps cannot be exceeded by the validator's output`() {
        val many = List(8) { SessionExercise(ROW.copy(id = "row$it"), 4, 10, 2.0) }
        val v = SessionValidator.validate(Session(Tier.FULL, many), ValidationContext(Level.BEGINNER)).value.session
        assertTrue(v.workingSets <= 20)
        assertTrue(v.exercises.filter { Muscle.LATS in it.exercise.primary }.sumOf { it.sets } <= 6)
    }

    @Test fun `TC-INT-003a RIR 0 only on FailureSafe exercises, at most 2, FULL tier, not beginners' first 8 weeks`() {
        assertEquals(0, Caps.rir0ExercisesPerSession(Level.BEGINNER, 4, Tier.FULL, false, ScreeningMode.STANDARD))
        assertEquals(2, Caps.rir0ExercisesPerSession(Level.BEGINNER, 9, Tier.FULL, false, ScreeningMode.STANDARD))
        assertEquals(0, Caps.rir0ExercisesPerSession(Level.ADVANCED, 99, Tier.MODIFIED, false, ScreeningMode.STANDARD))
        assertEquals(0, Caps.rir0ExercisesPerSession(Level.ADVANCED, 99, Tier.FULL, true, ScreeningMode.STANDARD))
    }

    @Test fun `TC-INT-003b failure on a barbell squat or a third exercise is removed by the validator`() {
        val s = Session(Tier.FULL, listOf(
            SessionExercise(SQUAT, 3, 5, 1.0, main = true, lastSetToFailure = true),
            SessionExercise(LEG_EXT, 2, 12, 1.0, lastSetToFailure = true),
            SessionExercise(CURL, 2, 12, 1.0, lastSetToFailure = true),
            SessionExercise(PUSHDOWN, 2, 12, 1.0, lastSetToFailure = true),
        ))
        val v = SessionValidator.validate(s, ValidationContext(Level.INTERMEDIATE)).value.session
        assertFalse(v.exercises.first { it.exercise.id == "squat" }.lastSetToFailure)
        assertTrue(v.exercises.count { it.lastSetToFailure } <= 2)
        assertTrue(v.exercises.filter { it.lastSetToFailure }.all { it.exercise.failureSafe })
    }

    @Test fun `TC-HIIT-001a at most 2 HIIT sessions, a 3rd only when every condition holds`() {
        val base = HiitContext(Level.INTERMEDIATE, Tier.FULL, 0, false, conditioningBlock = true)
        assertEquals(3, Caps.hiitPerWeek(base).value)
        assertEquals(2, Caps.hiitPerWeek(base.copy(fatigueSignals = 1)).value)
        assertEquals(2, Caps.hiitPerWeek(base.copy(level = Level.BEGINNER)).value)
        assertEquals(2, Caps.hiitPerWeek(base.copy(age = 55)).value)
        assertEquals(0, Caps.hiitPerWeek(base.copy(screening = ScreeningMode.MODERATE_ONLY)).value)
        assertEquals(0, Caps.hiitPerWeek(base.copy(inDeload = true)).value)
    }

    @Test fun `TC-HIIT-001b what counts as HIIT, and the weekly count is enforced`() {
        assertTrue(Caps.countsAsHiit(6.0)); assertFalse(Caps.countsAsHiit(5.0))
        assertTrue(Caps.countsAsHiit(0.0, circuitCr10 = 7, circuitMinutes = 8.0)); assertFalse(Caps.countsAsHiit(0.0, 7, 7.0))
        val s = Session(Tier.FULL, emptyList(), listOf(ConditioningBlock(Modality.ROWER, Zone.Z4, 4.0, 4.0, hiit = true)))
        val v = SessionValidator.validate(s, ValidationContext(Level.INTERMEDIATE, hiitThisWeekSoFar = 2, hiitAllowedThisWeek = 2)).value.session
        assertEquals(0, v.hiitBlocks)
    }

    @Test fun `TC-HIIT-004a HIIT less than 24 h after the last HIIT is converted to steady work`() {
        val s = Session(Tier.FULL, emptyList(), listOf(ConditioningBlock(Modality.SKIERG, Zone.Z4, 6.0, 6.0, hiit = true)))
        val v = SessionValidator.validate(s, ValidationContext(Level.INTERMEDIATE, hoursSinceLastHiit = 20.0)).value.session
        assertEquals(Zone.Z2, v.conditioning.single().zone); assertFalse(v.conditioning.single().hiit)
    }

    @Test fun `TC-HIIT-004b 24 h or more is allowed`() {
        val s = Session(Tier.FULL, emptyList(), listOf(ConditioningBlock(Modality.SKIERG, Zone.Z4, 6.0, 6.0, hiit = true)))
        assertEquals(1, SessionValidator.validate(s, ValidationContext(Level.INTERMEDIATE, hoursSinceLastHiit = 24.0, hiitBaseReady = true)).value.session.hiitBlocks)
    }

    @Test fun `TC-CON-003a no HIIT within 24 h before a heavy lower-body session`() {
        val s = Session(Tier.FULL, emptyList(), listOf(ConditioningBlock(Modality.ROWER, Zone.Z4, 4.0, 4.0, hiit = true)))
        assertEquals(0, SessionValidator.validate(s, ValidationContext(Level.INTERMEDIATE, hoursToNextHeavyLower = 18.0)).value.session.hiitBlocks)
    }

    @Test fun `TC-CON-003b HIIT the day after heavy legs is fine`() {
        val s = Session(Tier.FULL, emptyList(), listOf(ConditioningBlock(Modality.ROWER, Zone.Z4, 4.0, 4.0, hiit = true)))
        assertEquals(1, SessionValidator.validate(s, ValidationContext(Level.INTERMEDIATE, hoursToNextHeavyLower = 48.0, hiitBaseReady = true)).value.session.hiitBlocks)
    }

    @Test fun `TC-MOD-001a excluded modalities are never prescribed`() {
        for (m in Modality.entries.filter { it.excluded }) {
            val s = Session(Tier.FULL, emptyList(), listOf(ConditioningBlock(m, Zone.Z2, 20.0)))
            assertTrue(SessionValidator.validate(s, ValidationContext(Level.INTERMEDIATE)).value.session.conditioning.none { it.modality.excluded })
        }
    }

    @Test fun `TC-MOD-001b treadmill running, bikes and stair machines are excluded, air bike and walking pending`() {
        assertTrue(Modality.TREADMILL_RUN.excluded && Modality.STATIONARY_BIKE.excluded && Modality.STAIR_MACHINE.excluded)
        assertTrue(Modality.AIR_BIKE.pendingConfirmation && Modality.TREADMILL_WALK.pendingConfirmation)
        assertFalse(Modality.ROWER.excluded || Modality.SKIERG.excluded || Modality.ELLIPTICAL.excluded)
        assertFalse(SessionValidator.exerciseAllowed(BIKE_EX, ValidationContext(Level.ADVANCED)))
    }
}

class UserAdditionTest {
    @Test fun `TC-SAF-006a beyond a cap asks for confirmation of that addition`() {
        assertEquals(AdditionVerdict.OK, UserAdditions.addSets(Level.INTERMEDIATE, 15.0, 20, false, false, muscleSessionDirectAfter = 0.0).value)
        assertEquals(AdditionVerdict.WARN_CONFIRM, UserAdditions.addSets(Level.INTERMEDIATE, 17.0, 20, false, false, muscleSessionDirectAfter = 0.0).value)
        assertEquals(AdditionVerdict.OK, UserAdditions.addSets(Level.INTERMEDIATE, 17.0, 20, false, true, muscleSessionDirectAfter = 0.0).value)
        assertEquals(AdditionVerdict.WARN_CONFIRM, UserAdditions.addHiit(3, 2, ScreeningMode.STANDARD, false).value)
    }

    @Test fun `TC-SAF-006b absolute ceilings and painful regions block even with confirmation`() {
        assertEquals(AdditionVerdict.BLOCKED, UserAdditions.addSets(Level.INTERMEDIATE, 24.5, 20, false, true, muscleSessionDirectAfter = 0.0).value) // > 1.5 × 16
        assertEquals(AdditionVerdict.BLOCKED, UserAdditions.addSets(Level.ADVANCED, 10.0, 41, false, true, muscleSessionDirectAfter = 0.0).value)
        assertEquals(AdditionVerdict.BLOCKED, UserAdditions.addSets(Level.ADVANCED, 10.0, 10, true, true, muscleSessionDirectAfter = 0.0).value)
        assertEquals(AdditionVerdict.BLOCKED, UserAdditions.addHiit(5, 2, ScreeningMode.STANDARD, true).value)
        assertEquals(AdditionVerdict.BLOCKED, UserAdditions.addHiit(1, 2, ScreeningMode.MODERATE_ONLY, true).value)
    }
}

class ValidatorTest {
    @Test fun `TC-SAF-008a a session breaking many rules is corrected and every correction is logged`() {
        val s = Session(Tier.MODIFIED, listOf(
            SessionExercise(SQUAT, 6, 5, 0.0, loadFactor = 1.0, main = true, lastSetToFailure = true),
            SessionExercise(BIKE_EX, 3, 1, 2.0),
            SessionExercise(LEG_EXT, 6, 12, 1.0),
        ), listOf(ConditioningBlock(Modality.STAIR_MACHINE, Zone.Z2, 15.0), ConditioningBlock(Modality.ROWER, Zone.Z4, 4.0, 4.0, hiit = true)))
        val r = SessionValidator.validate(s, ValidationContext(Level.BEGINNER, weeksTraining = 3))
        val out = r.value.session
        assertTrue(SessionValidator.violations(out, ValidationContext(Level.BEGINNER, weeksTraining = 3)).isEmpty())
        assertFalse(r.value.fallbackUsed)
        assertTrue(out.exercises.none { it.exercise.id == "bike" })
        assertTrue(out.conditioning.none { it.modality.excluded || it.hiit })
        val squat = out.exercises.first { it.exercise.id == "squat" }
        assertTrue(squat.loadFactor <= 0.95 && squat.targetRir >= 1.0 && !squat.lastSetToFailure)
        assertTrue(r.decisions.size >= 5)
        assertEquals(ReasonKey.VALIDATOR_PASSED, r.decisions.last().reason)
    }

    /** Property: whatever goes in, what comes out has no violations, and nothing throws. */
    @Test fun `TC-SAF-008b property - 3000 random sessions are always validated or replaced by a valid fallback`() {
        val rnd = Random(20261007)
        var fallbacks = 0
        repeat(3000) {
            val pool = List(12) { randomExercise(rnd, it) }
            val s = Session(Tier.entries[rnd.nextInt(4)],
                List(rnd.nextInt(9)) { i ->
                    val e = pool[rnd.nextInt(pool.size)]
                    SessionExercise(e.copy(id = e.id + "-$i"), rnd.nextInt(7), 3 + rnd.nextInt(15), rnd.nextInt(5).toDouble(),
                        loadFactor = 0.6 + rnd.nextDouble() * 0.6, main = rnd.nextInt(3) == 0, lastSetToFailure = rnd.nextInt(4) == 0)
                },
                List(rnd.nextInt(3)) { ConditioningBlock(Modality.entries[rnd.nextInt(Modality.entries.size)], Zone.entries[rnd.nextInt(4)],
                    1.0 + rnd.nextInt(30), rnd.nextInt(10).toDouble(), hiit = rnd.nextBoolean(), impact = rnd.nextInt(3)) })
            val level = Level.entries[rnd.nextInt(3)]
            val c = ValidationContext(level, weeksTraining = rnd.nextInt(30), screening = ScreeningMode.entries[rnd.nextInt(3)],
                jointLimits = Joint.entries.filter { rnd.nextInt(5) == 0 }.associateWith { rnd.nextInt(3) },
                regions = if (rnd.nextInt(4) == 0) listOf(RegionConstraint(Joint.entries[rnd.nextInt(7)], 1, 3, true, true, true)) else emptyList(),
                blockedTags = if (rnd.nextBoolean()) setOf("tagA") else emptySet(),
                weekSetsSoFar = Muscle.entries.associateWith { rnd.nextInt(22).toDouble() },
                weekSsuSoFar = rnd.nextInt(200).toDouble(),
                hiitThisWeekSoFar = rnd.nextInt(4), hiitAllowedThisWeek = 2 + rnd.nextInt(2),
                hoursSinceLastHiit = if (rnd.nextBoolean()) rnd.nextInt(72).toDouble() else null,
                hoursToNextHeavyLower = if (rnd.nextBoolean()) rnd.nextInt(72).toDouble() else null,
                weekLoadSoFar = rnd.nextInt(2000).toDouble(), weeklyLoadCap = if (rnd.nextBoolean()) 1500.0 + rnd.nextInt(1500) else null,
                k = if (rnd.nextBoolean()) 10.0 + rnd.nextInt(20) else null, inDeload = rnd.nextInt(6) == 0,
                equipmentToday = GYM, library = pool)
            val r = SessionValidator.validate(s, c)
            assertTrue("violations remain: ${SessionValidator.violations(r.value.session, c)}", SessionValidator.violations(r.value.session, c).isEmpty())
            assertTrue(r.value.session.tier.ordinal <= s.tier.ordinal)
            if (r.value.fallbackUsed) fallbacks++
        }
        assertTrue("fallback should be rare, was $fallbacks", fallbacks < 300)
    }
}

class WeekChecksTest {
    private fun week(vararg ex: Pair<Exercise, Int>) = listOf(Session(Tier.FULL, ex.map { SessionExercise(it.first, it.second, 10, 2.0) }))

    @Test fun `TC-PAT-001a a balanced 3-day week has no coverage issues`() {
        val w = listOf(
            Session(Tier.FULL, listOf(SQUAT, HINGE, HPUSH, HPULL, VPUSH, CARRY, ANTI_EXT).map { SessionExercise(it, 3, 8, 2.0) }),
            Session(Tier.FULL, listOf(SQUAT, HINGE, HPUSH, HPULL, VPULL, LUNGE, ANTI_ROT, ANTI_LAT, ROTATION).map { SessionExercise(it, 3, 8, 2.0) }),
        )
        assertTrue(WeekChecks.issues(w, 3).value.none { it.ruleId == RuleIds.PAT_001 })
    }

    @Test fun `TC-PAT-001b missing patterns are reported`() {
        val codes = WeekChecks.issues(week(SQUAT to 3), 3).value.filter { it.ruleId == RuleIds.PAT_001 }.map { it.code + ":" + it.detail }
        assertTrue("MISSING_PATTERN:HINGE" in codes && "MISSING_CARRY:" in codes && "MAIN_PATTERN_ONCE:SQUAT" in codes)
        val twoDay = WeekChecks.issues(week(SQUAT to 3), 2, carryOrRotationLastWeek = setOf(Pattern.LOADED_CARRY)).value.map { it.code }
        assertTrue("MISSING_ROTATION" in twoDay && "MISSING_CARRY" !in twoDay)
    }

    @Test fun `TC-PAT-002a pull to push between 1-0 and 1-5 passes`() {
        assertTrue(WeekChecks.issues(week(HPULL to 6, HPUSH to 5), 3).value.none { it.ruleId == RuleIds.PAT_002 })
    }

    @Test fun `TC-PAT-002b more push than pull is flagged`() {
        assertTrue(WeekChecks.issues(week(HPULL to 4, HPUSH to 6), 3).value.any { it.ruleId == RuleIds.PAT_002 })
    }

    @Test fun `TC-PAT-003a knee to hip between 0-67 and 1-5 passes`() {
        assertTrue(WeekChecks.issues(week(SQUAT to 6, HINGE to 6), 3).value.none { it.ruleId == RuleIds.PAT_003 })
    }

    @Test fun `TC-PAT-003b knee-dominant overload is flagged`() {
        assertTrue(WeekChecks.issues(week(SQUAT to 10, HINGE to 3), 3).value.any { it.ruleId == RuleIds.PAT_003 })
    }

    @Test fun `TC-PAT-004a one unilateral lower-body exercise is enough for 3 days`() {
        assertTrue(WeekChecks.issues(week(LUNGE to 3), 3).value.none { it.ruleId == RuleIds.PAT_004 })
    }

    @Test fun `TC-PAT-004b four or more days need two`() {
        assertTrue(WeekChecks.issues(week(LUNGE to 3), 4).value.any { it.ruleId == RuleIds.PAT_004 })
    }
}

// ---------------------------------------------------------------------- fixtures

private val GYM = setOf("barbell", "rack", "dumbbells", "bench", "cable", "machine", "rower", "skierg")

private fun e(id: String, pattern: Pattern, primary: Set<Muscle>, equipment: Set<String>, cost: CostClass = CostClass.MACHINE_OR_CABLE_COMPOUND,
              joints: Map<Joint, Int> = emptyMap(), failureSafe: Boolean = false, unilateral: Boolean = false, impact: Int = 0) =
    Exercise(id, id, pattern, primary, emptySet(), equipment, LoadType.STACK, cost, jointStress = joints, failureSafe = failureSafe, unilateral = unilateral, impact = impact)

private val SQUAT = e("squat", Pattern.SQUAT, setOf(Muscle.QUADS, Muscle.GLUTES), setOf("barbell", "rack"), CostClass.HEAVY_BILATERAL, mapOf(Joint.KNEE to 3, Joint.SPINE to 3, Joint.HIP to 2))
private val LEG_EXT = e("leg-extension", Pattern.ISOLATION, setOf(Muscle.QUADS), setOf("machine"), CostClass.ISOLATION_OR_CORE, mapOf(Joint.KNEE to 2), failureSafe = true)
private val LEG_PRESS_LIGHT = e("box-step", Pattern.LUNGE, setOf(Muscle.QUADS), setOf("bench"), joints = mapOf(Joint.KNEE to 1))
private val ROW = e("row", Pattern.HORIZONTAL_PULL, setOf(Muscle.LATS, Muscle.UPPER_BACK), setOf("cable"), joints = mapOf(Joint.ELBOW to 1, Joint.SHOULDER to 1))
private val CURL = e("curl", Pattern.ISOLATION, setOf(Muscle.BICEPS), setOf("dumbbells"), CostClass.ISOLATION_OR_CORE, failureSafe = true)
private val PUSHDOWN = e("pushdown", Pattern.ISOLATION, setOf(Muscle.TRICEPS), setOf("cable"), CostClass.ISOLATION_OR_CORE, failureSafe = true)
private val BIKE_EX = e("bike", Pattern.ISOLATION, setOf(Muscle.QUADS), setOf("stationary_bike"))
private val HINGE = e("rdl", Pattern.HINGE, setOf(Muscle.HAMSTRINGS, Muscle.GLUTES), setOf("barbell"), CostClass.FREE_WEIGHT_COMPOUND)
private val HPUSH = e("bench-press", Pattern.HORIZONTAL_PUSH, setOf(Muscle.CHEST), setOf("barbell", "bench"))
private val HPULL = ROW
private val VPUSH = e("ohp", Pattern.VERTICAL_PUSH, setOf(Muscle.FRONT_DELTS), setOf("dumbbells"))
private val VPULL = e("pulldown", Pattern.VERTICAL_PULL, setOf(Muscle.LATS), setOf("cable"))
private val LUNGE = e("split-squat", Pattern.LUNGE, setOf(Muscle.QUADS, Muscle.GLUTES), setOf("dumbbells"), unilateral = true)
private val CARRY = e("farmer-carry", Pattern.LOADED_CARRY, setOf(Muscle.FOREARMS), setOf("dumbbells"))
private val ANTI_EXT = e("dead-bug", Pattern.ANTI_EXTENSION, setOf(Muscle.CORE), emptySet(), CostClass.ISOLATION_OR_CORE)
private val ANTI_ROT = e("pallof", Pattern.ANTI_ROTATION, setOf(Muscle.CORE), setOf("cable"), CostClass.ISOLATION_OR_CORE)
private val ANTI_LAT = e("side-plank", Pattern.ANTI_LATERAL_FLEXION, setOf(Muscle.CORE), emptySet(), CostClass.ISOLATION_OR_CORE)
private val ROTATION = e("cable-chop", Pattern.ROTATION, setOf(Muscle.CORE), setOf("cable"), CostClass.ISOLATION_OR_CORE)
private val LIB = listOf(SQUAT, LEG_EXT, LEG_PRESS_LIGHT, ROW, CURL, PUSHDOWN, BIKE_EX, HINGE, HPUSH, VPUSH, VPULL, LUNGE)

private fun randomExercise(rnd: Random, i: Int): Exercise {
    val pattern = Pattern.entries[rnd.nextInt(Pattern.entries.size)]
    val muscles = Muscle.entries.shuffled(rnd).take(1 + rnd.nextInt(2)).toSet()
    val equipment = (GYM + "stationary_bike").shuffled(rnd).take(rnd.nextInt(3)).toSet()
    return Exercise("x$i", "x$i", pattern, muscles, Muscle.entries.shuffled(rnd).take(rnd.nextInt(2)).toSet(), equipment,
        LoadType.entries[rnd.nextInt(LoadType.entries.size)], CostClass.entries[rnd.nextInt(4)],
        difficulty = 1 + rnd.nextInt(5), skill = 1 + rnd.nextInt(4),
        jointStress = Joint.entries.filter { rnd.nextBoolean() }.associateWith { rnd.nextInt(5) },
        fatigueSystemic = 1 + rnd.nextInt(5), impact = if (rnd.nextInt(4) == 0) 1 + rnd.nextInt(2) else 0,
        failureSafe = rnd.nextBoolean(), limitationTags = if (rnd.nextInt(5) == 0) setOf("tagA") else emptySet())
}
