package com.personalfitnesscoach.engine.safety

import com.personalfitnesscoach.engine.calc.Deload
import com.personalfitnesscoach.engine.calc.DeloadAction
import com.personalfitnesscoach.engine.calc.Inventory
import com.personalfitnesscoach.engine.calc.PlateMath
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.model.CostClass
import com.personalfitnesscoach.engine.model.Exercise
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.LoadType
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.model.Muscle
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.model.SetLog
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.model.Zone
import com.personalfitnesscoach.engine.planning.Priority
import com.personalfitnesscoach.engine.planning.PlanItem
import com.personalfitnesscoach.engine.planning.SessionPlan
import com.personalfitnesscoach.engine.planning.TimeBudget
import com.personalfitnesscoach.engine.progression.Autoregulation
import com.personalfitnesscoach.engine.progression.Calibration
import com.personalfitnesscoach.engine.progression.CalibrationStep
import com.personalfitnesscoach.engine.progression.ExposureInput
import com.personalfitnesscoach.engine.progression.Progression
import com.personalfitnesscoach.engine.progression.ProgressionAction
import com.personalfitnesscoach.engine.progression.ProgressionCaps
import com.personalfitnesscoach.engine.progression.ReturnToTraining
import com.personalfitnesscoach.engine.progression.Warmup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the defects found by the independent safety review of Phase 3 Part 1
 * (one test per finding), plus the rules the review found unimplemented.
 */
class ReviewFindingsTest {
    private val bar = PlateMath.loads(LoadType.BARBELL, Inventory())
    private val db = PlateMath.loads(LoadType.DUMBBELL, Inventory())
    private val kb = PlateMath.loads(LoadType.KETTLEBELL, Inventory())

    private fun ex(id: String, pattern: Pattern = Pattern.SQUAT, primary: Set<Muscle> = setOf(Muscle.QUADS), joints: Map<Joint, Int> = emptyMap(),
                   impact: Int = 0, cost: CostClass = CostClass.FREE_WEIGHT_COMPOUND, failureSafe: Boolean = false, loadType: LoadType = LoadType.DUMBBELL) =
        Exercise(id, id, pattern, primary, loadType = loadType, costClass = cost, jointStress = joints, impact = impact, failureSafe = failureSafe)

    private val goblet = ex("goblet-squat", joints = mapOf(Joint.KNEE to 2))
    private val row = ex("row", Pattern.HORIZONTAL_PULL, setOf(Muscle.LATS), cost = CostClass.MACHINE_OR_CABLE_COMPOUND)
    private val curl = ex("curl", Pattern.ISOLATION, setOf(Muscle.BICEPS), cost = CostClass.ISOLATION_OR_CORE, failureSafe = true)
    private fun ctx(level: Level = Level.INTERMEDIATE) = ValidationContext(level)

    // 1 — pain limits apply to conditioning too
    @Test fun `TC-SAF-003c knee pain 8 of 10 keeps jump rope and the rower out of the session`() {
        val s = Session(Tier.FULL, emptyList(), listOf(ConditioningBlock(Modality.JUMP_ROPE, Zone.Z2, 15.0, impact = 3)))
        val out = SessionValidator.validate(s, ctx().copy(jointLimits = mapOf(Joint.KNEE to 0))).value.session
        assertTrue(out.conditioning.all { ModalityJoints.stress(it.modality, Joint.KNEE) == 0 && it.impact == 0 })
        assertFalse(SessionValidator.modalityAllowed(Modality.ROWER, ctx().copy(jointLimits = mapOf(Joint.KNEE to 1))))
        assertTrue(SessionValidator.modalityAllowed(Modality.ELLIPTICAL, ctx().copy(jointLimits = mapOf(Joint.KNEE to 1))))
    }

    // 2 — Q5 stays conservative even with clearance confirmed
    @Test fun `TC-SAF-001c told to limit exercise or pregnant stays conservative after clearance`() {
        val a = ScreeningAnswers(false, false, false, false, limitOrPregnancy = true, musculoskeletal = false, longTermMedication = false, regularlyActive = true)
        assertEquals(ScreeningMode.CONSERVATIVE, Screening.evaluate(a, clearanceConfirmed = true).value.mode)
    }

    // 3 — 6+ minutes of Z3 counts as HIIT even without the flag
    @Test fun `TC-HIIT-001c 20 minutes of Z3 work counts as HIIT and is capped`() {
        val s = Session(Tier.MODIFIED, emptyList(), listOf(ConditioningBlock(Modality.ROWER, Zone.Z3, 20.0)))
        val c = ctx().copy(hiitThisWeekSoFar = 2, hiitAllowedThisWeek = 2, hoursSinceLastHiit = 10.0, hoursToNextHeavyLower = 12.0)
        assertTrue(s.conditioning.single().countsAsHiit)
        val out = SessionValidator.validate(s, c).value.session
        assertEquals(0, out.hiitBlocks)
        assertTrue(out.conditioning.all { it.zone <= Zone.Z2 })
    }

    // 4 — tier and deload set cuts, MODIFIED RIR shift, LIGHT and deload conditioning, deload loads on every exercise
    @Test fun `TC-RDY-004a LIGHT keeps half the sets and at most 20 minutes of Z1`() {
        val s = Session(Tier.LIGHT, List(5) { SessionExercise(row.copy(id = "row$it", primary = setOf(Muscle.entries[it])), 5, 10, 3.0) },
            listOf(ConditioningBlock(Modality.ROWER, Zone.Z2, 45.0)))
        val out = SessionValidator.validate(s, ctx().copy(fullTierWorkingSets = 25)).value.session
        assertTrue(out.workingSets <= 12)
        assertTrue(out.conditioning.all { it.zone == Zone.Z1 } && out.conditioning.sumOf { it.totalMinutes } <= 20.0)
    }

    @Test fun `TC-RDY-004b MODIFIED adds one RIR to the FULL plan and caps main loads at 95 percent`() {
        val s = Session(Tier.MODIFIED, listOf(SessionExercise(goblet, 3, 8, 2.0, loadFactor = 1.0, main = true, fullTierRir = 2.0)))
        val e = SessionValidator.validate(s, ctx()).value.session.exercises.single()
        assertTrue(e.targetRir >= 3.0)
        assertTrue(e.loadFactor <= 0.95)
    }

    @Test fun `TC-DEL-003c deload caps every exercise at 90 percent, halves sets and allows Z1 only`() {
        val s = Session(Tier.FULL, listOf(SessionExercise(curl, 4, 12, 3.0, loadFactor = 1.0), SessionExercise(row, 4, 10, 3.0, loadFactor = 1.0)),
            listOf(ConditioningBlock(Modality.ROWER, Zone.Z2, 30.0)))
        val out = SessionValidator.validate(s, ctx().copy(inDeload = true, fullTierWorkingSets = 8)).value.session
        assertTrue(out.exercises.all { it.loadFactor <= 0.90 && it.targetRir >= 3.0 })
        assertTrue(out.workingSets <= 4)
        assertTrue(out.conditioning.all { it.zone == Zone.Z1 })
    }

    // 5 — in-session increases respect the day's load cap
    @Test fun `TC-INT-007c an easy LIGHT day never climbs above 85 percent of the normal load`() {
        val cap = 100.0 * SessionValidator.maxLoadFactor(main = true, tier = Tier.LIGHT, inDeload = false)
        var load = 85.0
        repeat(5) { load = Autoregulation.adjust(85.0, load, SetLog(load, 8, 5.0), 8, 6, 3.0, bar, tierMaxLoad = cap).value }
        assertTrue(load <= 85.0)
    }

    // 6 — LOAD-005 applies before the personal factor k is learned
    @Test fun `TC-LOAD-005c before k is learned, planned SSU may rise at most 20 percent week over week`() {
        val s = Session(Tier.FULL, List(6) { SessionExercise(row.copy(id = "r$it", primary = setOf(Muscle.entries[it])), 4, 10, 2.0) })
        val c = ctx().copy(lastWeekSsu = 40.0, weekSsuSoFar = 40.0)
        val out = SessionValidator.validate(s, c).value.session
        assertTrue(c.weekSsuSoFar + SessionValidator.sessionSsu(out) <= 48.0 + 1e-9)
    }

    // 7 — calibration never overshoots on coarse equipment
    @Test fun `TC-CAL-001c coarse dumbbell and kettlebell steps end calibration instead of overshooting`() {
        val d = Calibration.next(10.0, 10, 4.0, 2, db)
        assertEquals(CalibrationStep.Found(10.0, null), d.value)
        assertEquals(ReasonKey.CALIBRATION_CAPPED, d.decisions.single().reason)
        assertTrue(Calibration.next(8.0, 10, 5.0, 2, kb).value is CalibrationStep.Found)
        assertEquals(CalibrationStep.Continue(25.0), Calibration.next(20.0, 8, 5.0, 1, bar).value) // Phase 1 example unchanged
    }

    // 8 — severe descriptors stop the region whatever the pain type
    @Test fun `TC-SAF-003d soreness after a fall with swelling stops the region`() {
        val o = PainGate.assess(PainReport(Joint.KNEE, PainKind.DOMS, 6, descriptors = setOf("after_fall_or_impact", "swelling"))).value
        assertEquals(PainAction.STOP_REGION, o.action)
        assertTrue(o.suggestProfessional)
    }

    // 9 — still 2 signals after a lighter week → deload
    @Test fun `TC-DEL-002c two signals after a lighter week escalate to a deload`() {
        assertEquals(DeloadAction.DELOAD_NOW, Deload.decide(2, 4, false, 0, justFinishedLighterWeek = true).value)
        assertEquals(DeloadAction.LIGHTER_WEEK, Deload.decide(2, 4, false, 0, justFinishedLighterWeek = false).value)
    }

    // 10 — user additions beyond the per-session direct-set cap need confirmation
    @Test fun `TC-SAF-006c a 9th chest set in one session asks for confirmation`() {
        assertEquals(AdditionVerdict.WARN_CONFIRM, UserAdditions.addSets(Level.INTERMEDIATE, 12.0, 18, false, false, muscleSessionDirectAfter = 9.0).value)
        assertEquals(AdditionVerdict.OK, UserAdditions.addSets(Level.INTERMEDIATE, 12.0, 18, false, true, muscleSessionDirectAfter = 9.0).value)
    }

    // 11 — illness of 7+ days also uses the layoff ramp
    @Test fun `TC-REG-005c a 30-day illness returns at the 28 to 55 day ramp`() {
        val p = ReturnToTraining.afterIllness(48, false, sessionsSinceReturn = 1, illnessDays = 30).value
        assertEquals(Tier.MODIFIED, p.tierCap)
        assertEquals(0.8, p.loadFactor, 1e-9)
        assertEquals(0.6, p.setsFactor, 1e-9)
        assertFalse(p.hiitAllowed)
    }

    // Smaller items
    @Test fun `TC-PROG-007c a light-dumbbell jump that breaks the weekly intensity cap is held and a swap offered`() {
        val curlEx = ex("db-curl", Pattern.ISOLATION, setOf(Muscle.BICEPS), cost = CostClass.ISOLATION_OR_CORE).copy(defaultRepRange = 8..12)
        val r = Progression.next(ExposureInput(curlEx, 8..15, 2.0, 10.0, List(3) { SetLog(10.0, 15, 2.0) }, db, true))
        assertEquals(ProgressionAction.HOLD, r.value.action)
        assertEquals(10.0, r.value.load, 1e-9)
        assertTrue(r.decisions.any { it.reason == ReasonKey.SWAP_OFFERED_ONLY })
    }

    @Test fun `TC-AGE-001a at 65 the weekly intensity cap is 0-8 times the usual`() {
        assertEquals(4.0, ProgressionCaps.intensityPctPerWeek(Level.INTERMEDIATE, 66), 1e-9)
        assertEquals(6.0, ProgressionCaps.intensityPctPerWeek(Level.BEGINNER, 70), 1e-9)
        assertEquals(5.0, ProgressionCaps.intensityPctPerWeek(Level.INTERMEDIATE, 64), 1e-9)
    }

    @Test fun `TC-AGE-001b age 50 lowers the HIIT ceiling and deload trigger, 65 adds a balance drill`() {
        assertEquals(2, Caps.hiitPerWeek(HiitContext(Level.ADVANCED, Tier.FULL, 0, false, true, age = 52)).value)
        assertEquals(DeloadAction.DELOAD_NOW, Deload.decide(2, 0, false, 0, age = 50, justFinishedLighterWeek = false).value)
        assertTrue(Warmup.balanceDrillRequired(65)); assertFalse(Warmup.balanceDrillRequired(64))
    }

    @Test fun `TC-TIME-001c a warm-up planned below the floor is raised to 5 minutes`() {
        val plan = SessionPlan(0.0, 0.0, listOf(PlanItem("a", Priority.P1, sets = 3, reps = 5, restSec = 120, minRestSec = 120)))
        val f = TimeBudget.fit(plan, 60.0).value
        assertEquals(5.0, f.plan.warmupMin, 1e-9)
        assertEquals(2.0, f.plan.cooldownMin, 1e-9)
    }

    @Test fun `TC-TIME-003c extension respects the per-muscle session cap`() {
        val plan = SessionPlan(8.0, 2.0, listOf(
            PlanItem("press", Priority.P3, sets = 4, reps = 8, restSec = 90, minRestSec = 90, primaryMuscles = setOf(Muscle.CHEST)),
            PlanItem("fly", Priority.P4, sets = 2, reps = 12, restSec = 60, minRestSec = 60, primaryMuscles = setOf(Muscle.CHEST))))
        val out = TimeBudget.extend(plan, 120.0, sessionSetCap = 25, directCapPerMuscle = 8).value
        assertTrue(out.items.sumOf { it.sets } <= 8)
    }

    @Test fun `TC-SAF-005c seven training days are flagged`() {
        val day = Session(Tier.FULL, listOf(SessionExercise(row, 3, 10, 2.0)))
        assertTrue(WeekChecks.issues(List(7) { day }, 6).value.any { it.ruleId == "SAF-005" })
        assertTrue(WeekChecks.issues(List(6) { day }, 6).value.none { it.ruleId == "SAF-005" })
    }

    // Rules the review found unimplemented
    @Test fun `TC-HIIT-003a no HIIT before screening, calibration and 3 weeks of base`() {
        val s = Session(Tier.FULL, emptyList(), listOf(ConditioningBlock(Modality.ROWER, Zone.Z3, 5.0, 5.0, protocol = HiitProtocol.SHORT)))
        assertEquals(0, SessionValidator.validate(s, ctx().copy(hiitBaseReady = false)).value.session.hiitBlocks)
    }

    @Test fun `TC-HIIT-003b HIIT is allowed once the base is in place`() {
        val s = Session(Tier.FULL, emptyList(), listOf(ConditioningBlock(Modality.ROWER, Zone.Z3, 5.0, 5.0, protocol = HiitProtocol.SHORT)))
        assertEquals(1, SessionValidator.validate(s, ctx().copy(hiitBaseReady = true)).value.session.hiitBlocks)
        assertEquals(0, SessionValidator.validate(s, ctx()).value.session.hiitBlocks) // fail-closed default
    }

    @Test fun `TC-HIIT-005a interval work is capped by protocol`() {
        val s = Session(Tier.FULL, emptyList(), listOf(ConditioningBlock(Modality.SKIERG, Zone.Z3, 25.0, 10.0, protocol = HiitProtocol.LONG)))
        assertTrue(SessionValidator.validate(s, ctx().copy(hiitBaseReady = true)).value.session.conditioning.single().let { it.protocol == HiitProtocol.LONG && it.workMinutes <= 20.0 })
        val short = Session(Tier.FULL, emptyList(), listOf(ConditioningBlock(Modality.ROWER, Zone.Z3, 12.0, 12.0, protocol = HiitProtocol.SHORT)))
        assertTrue(SessionValidator.validate(short, ctx().copy(hiitBaseReady = true)).value.session.conditioning.single().let { it.protocol == HiitProtocol.SHORT && it.workMinutes <= 10.0 })
    }

    @Test fun `TC-HIIT-005b at most 35 minutes of conditioning after strength`() {
        val s = Session(Tier.FULL, listOf(SessionExercise(row, 3, 10, 2.0)), listOf(ConditioningBlock(Modality.ROWER, Zone.Z1, 30.0), ConditioningBlock(Modality.ELLIPTICAL, Zone.Z1, 20.0)))
        assertTrue(SessionValidator.validate(s, ctx()).value.session.conditioning.sumOf { it.totalMinutes } <= 35.0 + 1e-9)
    }

    @Test fun `TC-HIIT-002a sprints are for advanced lifters, once a week`() {
        val s = Session(Tier.FULL, emptyList(), listOf(ConditioningBlock(Modality.ROWER, Zone.Z4, 2.0, 12.0, protocol = HiitProtocol.SPRINT)))
        assertTrue(SessionValidator.validate(s, ctx(Level.INTERMEDIATE)).value.session.conditioning.none { it.protocol == HiitProtocol.SPRINT })
        assertTrue(SessionValidator.validate(s, ctx(Level.ADVANCED).copy(sprintsThisWeekSoFar = 1)).value.session.conditioning.none { it.protocol == HiitProtocol.SPRINT })
    }

    @Test fun `TC-HIIT-002b an advanced lifter's first sprint of the week is kept`() {
        val s = Session(Tier.FULL, emptyList(), listOf(ConditioningBlock(Modality.ROWER, Zone.Z4, 2.0, 12.0, protocol = HiitProtocol.SPRINT)))
        assertEquals(HiitProtocol.SPRINT, SessionValidator.validate(s, ctx(Level.ADVANCED).copy(hiitBaseReady = true)).value.session.conditioning.single().protocol)
    }

    @Test fun `TC-CON-004a impact work at most once a week`() {
        val s = Session(Tier.FULL, emptyList(), listOf(ConditioningBlock(Modality.JUMP_ROPE, Zone.Z2, 10.0, impact = 2)))
        assertTrue(SessionValidator.validate(s, ctx().copy(impactSessionsThisWeekSoFar = 1)).value.session.conditioning.all { it.impact == 0 })
        assertEquals(2, SessionValidator.validate(s, ctx()).value.session.conditioning.single().impact)
    }

    @Test fun `TC-CON-004b no jumping the day before heavy legs`() {
        val jumps = ex("box-jump", impact = 2, joints = mapOf(Joint.KNEE to 2, Joint.ANKLE to 2))
        val out = SessionValidator.validate(Session(Tier.FULL, listOf(SessionExercise(jumps, 3, 5, 3.0), SessionExercise(row, 3, 10, 2.0))),
            ctx().copy(heavyLegsNextDay = true)).value.session
        assertTrue(out.exercises.none { it.exercise.impact > 0 })
        assertTrue(out.exercises.any { it.exercise.id == "row" })
    }

    // Second review pass
    @Test fun `TC-AGE-001c at 70 a 5 percent squat step is too big - the range extends first`() {
        val squat = ex("back-squat", loadType = LoadType.BARBELL).copy(defaultRepRange = 5..5, maxExtendedReps = 8)
        val r = Progression.next(ExposureInput(squat, 5..5, 2.0, 50.0, List(3) { SetLog(50.0, 5, 2.0) }, bar, true, age = 70))
        assertEquals(ProgressionAction.RANGE_EXTENDED, r.value.action)
        assertEquals(50.0, r.value.load, 1e-9)
    }

    @Test fun `TC-HIIT-001d two 5-minute Z3 blocks add up to a HIIT session`() {
        val s = Session(Tier.FULL, emptyList(), listOf(ConditioningBlock(Modality.ROWER, Zone.Z3, 5.0), ConditioningBlock(Modality.SKIERG, Zone.Z3, 5.0)))
        assertEquals(1, s.hiitBlocks)
        val c = ctx().copy(hiitBaseReady = true, hiitThisWeekSoFar = 2, hiitAllowedThisWeek = 2, hoursSinceLastHiit = 10.0)
        val out = SessionValidator.validate(s, c).value.session
        assertEquals(0, out.hiitBlocks)
        assertTrue(SessionValidator.violations(out, c).isEmpty())
    }

    @Test fun `TC-SAF-005d a recovery day with easy Z1 is a rest day, not a seventh training day`() {
        val day = Session(Tier.FULL, listOf(SessionExercise(row, 3, 10, 2.0)))
        val rest = Session(Tier.RECOVERY, emptyList(), listOf(ConditioningBlock(Modality.ROWER, Zone.Z1, 20.0)))
        assertTrue(WeekChecks.issues(List(6) { day } + rest, 6).value.none { it.ruleId == "SAF-005" })
    }

    @Test fun `TC-LOAD-005d deload and return weeks follow their own ramp`() {
        val s = Session(Tier.FULL, List(4) { SessionExercise(row.copy(id = "r$it", primary = setOf(Muscle.entries[it])), 4, 10, 2.0) })
        val c = ctx().copy(lastWeekSsu = 10.0, weekSsuSoFar = 10.0, loadCapExempt = true)
        assertEquals(16, SessionValidator.validate(s, c).value.session.workingSets)
    }
}
