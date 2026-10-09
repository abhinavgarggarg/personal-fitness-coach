package com.personalfitnesscoach.engine.review

import com.personalfitnesscoach.engine.conditioning.Aerobic
import com.personalfitnesscoach.engine.conditioning.ModalitySelection
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.generation.AwayFromGym
import com.personalfitnesscoach.engine.generation.GenerationRequest
import com.personalfitnesscoach.engine.generation.SessionGenerator
import com.personalfitnesscoach.engine.library.GeneratedLibrary
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.LoadType
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.model.Zone
import com.personalfitnesscoach.engine.planning.Mobility
import com.personalfitnesscoach.engine.planning.TimeBudget
import com.personalfitnesscoach.engine.program.Blueprint
import com.personalfitnesscoach.engine.program.BlockType
import com.personalfitnesscoach.engine.program.DayTemplate
import com.personalfitnesscoach.engine.program.Express
import com.personalfitnesscoach.engine.program.FULL_GYM
import com.personalfitnesscoach.engine.program.FatLoss
import com.personalfitnesscoach.engine.program.Goal
import com.personalfitnesscoach.engine.program.Program
import com.personalfitnesscoach.engine.program.WeekInput
import com.personalfitnesscoach.engine.program.WeekKind
import com.personalfitnesscoach.engine.program.WeekPlan
import com.personalfitnesscoach.engine.program.WeekPlanner
import com.personalfitnesscoach.engine.progress.BodyProgress
import com.personalfitnesscoach.engine.progress.StrengthPoint
import com.personalfitnesscoach.engine.progress.TrendDirection
import com.personalfitnesscoach.engine.progression.Calibration
import com.personalfitnesscoach.engine.progression.CalibrationStep
import com.personalfitnesscoach.engine.progression.KnownEntry
import com.personalfitnesscoach.engine.progression.KnownLoads
import com.personalfitnesscoach.engine.progression.KnownStart
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds
import com.personalfitnesscoach.engine.safety.BoneLoading
import com.personalfitnesscoach.engine.safety.BoneLoadingVariant
import com.personalfitnesscoach.engine.safety.ClearanceScope
import com.personalfitnesscoach.engine.safety.ConditionLimits
import com.personalfitnesscoach.engine.safety.ConditioningBlock
import com.personalfitnesscoach.engine.safety.Conditions
import com.personalfitnesscoach.engine.safety.ControlStatus
import com.personalfitnesscoach.engine.safety.HiitPermission
import com.personalfitnesscoach.engine.safety.ImpactLevel
import com.personalfitnesscoach.engine.safety.Session
import com.personalfitnesscoach.engine.safety.SessionExercise
import com.personalfitnesscoach.engine.safety.SessionValidator
import com.personalfitnesscoach.engine.safety.UserCondition
import com.personalfitnesscoach.engine.safety.ValidationContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the independent review of Phase 3 Part 3 (findings R3-01 … R3-15). Each test reproduces the scenario
 * the reviewer ran and asserts the fixed behaviour.
 */
class Part3ReviewRegressionTest {
    private fun resolve(vararg u: UserCondition) = Conditions.resolve(u.toList()).value
    private val general: Program = Blueprint.plan(listOf(Goal.GENERAL_FITNESS)).value
    private val fatLoss: Program = Blueprint.plan(listOf(Goal.FAT_LOSS)).value
    private val gym = FULL_GYM + setOf("treadmill", "stationary_bike", "air_bike", "stair_climber")

    private fun week(program: Program, type: BlockType, kind: WeekKind = WeekKind.LOADING) =
        (1..53).first { Blueprint.context(program, it).block.type == type && Blueprint.context(program, it).kind == kind }

    private fun input(c: ConditionLimits, program: Program = general, days: Int = 3, age: Int = 50, type: BlockType = BlockType.BUILD,
                      available: Set<Int> = (0..6).toSet(), minutes: Int = 60, fl: Boolean = program === fatLoss, kind: WeekKind = WeekKind.LOADING,
                      deload: Boolean = false, equipment: Set<String> = gym) =
        WeekInput(Level.INTERMEDIATE, 40, days, equipment, Blueprint.context(program, week(program, type, kind)), age = age, availableDays = available,
            sessionMinutes = minutes, hiitBaseReady = true, hiitDoneEver = 4, lastWeekHiitWorkMinutes = 20.0, lastWeekAerobicMinutes = 120.0,
            lastWeekEquivalentMinutes = 180.0, priorities = if (fl) listOf(Goal.FAT_LOSS) else listOf(Goal.GENERAL_FITNESS), conditions = c, deload = deload)

    private fun generateAll(plan: WeekPlan, c: ConditionLimits, age: Int = 50, minutes: Int = 60, equipment: Set<String> = gym) =
        plan.days.filter { it.slots.isNotEmpty() || it.conditioning.isNotEmpty() }.map { day ->
            SessionGenerator.generate(GenerationRequest(day, Level.INTERMEDIATE, 40, minutes, equipment, Tier.FULL, age = age,
                week = ValidationContext(Level.INTERMEDIATE, weeksTraining = 40, hiitBaseReady = true), conditions = c))
        }

    // ---------------------------------------------------------------- R3-01 breastbone sub-flag

    @Test fun `R3-01 recent breastbone surgery rules out everything that loads the arms, shoulders or chest and arm-driven machines`() {
        val c = resolve(UserCondition("heart", clearance = setOf(ClearanceScope.VIGOROUS), subFlags = setOf("recent_breastbone_surgery")))
        assertTrue(c.avoidTags.containsAll(setOf("upper_body_loaded", "overhead", "overhead_heavy", "breath_hold_max", "isometric_heavy")))
        assertTrue(c.avoidModalities.containsAll(setOf(Modality.ROWER, Modality.SKIERG, Modality.AIR_BIKE, Modality.SLED, Modality.BATTLE_ROPES)))
        // The library carries the tag on every exercise that loads the arms (the generator enforces it); lower-body machines stay free.
        for (id in listOf("bench-press", "close-grip-bench-press", "barbell-row", "db-bench-press", "landmine-push-press", "goblet-squat",
                "front-plank", "pallof-standing", "farmer-carry")) Library.all.firstOrNull { it.id == id }?.let { assertTrue(id, "upper_body_loaded" in it.limitationTags) }
        for (id in listOf("leg-press", "leg-extension", "leg-curl", "glute-bridge", "air-squat")) assertFalse(id, "upper_body_loaded" in Library.require(id).limitationTags)
        for (type in listOf(BlockType.STRENGTH, BlockType.BUILD, BlockType.CONDITIONING)) {
            val plan = WeekPlanner.plan(input(c, type = type, days = 4), general).value
            val slots = plan.days.flatMap { it.slots }
            assertTrue(slots.map { it.exercise.id }.toString(), slots.none { s -> s.exercise.limitationTags.any { it in c.avoidTags } })
            assertTrue(plan.days.flatMap { it.conditioning }.none { it.modality in c.avoidModalities })
            for (w in generateAll(plan, c)) {
                assertTrue(w.value.items.none { "upper_body_loaded" in it.exercise.limitationTags || "overhead" in it.exercise.limitationTags })
                assertTrue(w.value.validated.conditioning.none { it.modality in c.avoidModalities })
            }
        }
        // The final gate catches a bench press or a rower that slipped through.
        val ctx = ValidationContext(Level.INTERMEDIATE, weeksTraining = 40, conditions = c, library = Library.all.filter { !it.userAddOnly })
        val bad = Session(Tier.FULL, listOf(SessionExercise(Library.require("bench-press"), 3, 8, 3.0, main = true)), listOf(ConditioningBlock(Modality.ROWER, Zone.Z1, 10.0)))
        val v = SessionValidator.violations(bad, ctx)
        assertTrue(v.toString(), v.any { it.code == "EXERCISE_NOT_ALLOWED" } && v.any { it.ruleId == RuleIds.SAF_010 && it.code == "MODALITY" })
    }

    // ---------------------------------------------------------------- R3-02 warm-up extras

    @Test fun `R3-02 a condition's extra warm-up and cool-down survive time pressure, the express session and the final gate`() {
        val c = resolve(UserCondition("heart", clearance = setOf(ClearanceScope.VIGOROUS)))
        assertEquals(10, c.extraWarmupMin); assertEquals(10, c.extraCooldownMin)
        val floor = TimeBudget.warmupFloor(50, 10.0)
        assertEquals(17.0, floor, 1e-9) // 5 + 2 (50+) + 10
        for (minutes in listOf(45, 60)) {
            val plan = WeekPlanner.plan(input(c, minutes = minutes), general).value
            for (r in generateAll(plan, c, minutes = minutes)) {
                val w = r.value
                if (w.items.isEmpty() && w.conditioning.isEmpty()) continue
                assertTrue("$minutes min: warm-up ${w.warmupMinutes}", w.warmupMinutes >= floor - 1e-9)
                assertTrue(w.validated.cooldownMinutes!! >= P.TIME_001.cooldown_min_minutes + 10.0 - 1e-9)
            }
            // The express version keeps the extras; when it then needs more than 30 minutes the app says so and offers a walk.
            val day = plan.days.first { it.slots.isNotEmpty() }
            val ex = SessionGenerator.express(GenerationRequest(day, Level.INTERMEDIATE, 40, minutes, gym, Tier.FULL, age = 50,
                week = ValidationContext(Level.INTERMEDIATE, weeksTraining = 40), conditions = c))
            assertTrue(ex.value.warmupMinutes >= floor - 1e-9)
            if (ex.value.plannedMinutes > Express.minutes.last) assertTrue(ex.decisions.any { it.reason == ReasonKey.EXPRESS_TOO_SHORT })
        }
        // The validator raises a short warm-up back to the floor.
        val ctx = ValidationContext(Level.INTERMEDIATE, weeksTraining = 40, age = 50, conditions = c)
        val s = Session(Tier.FULL, listOf(SessionExercise(Library.require("leg-press"), 2, 10, 3.0)), warmupMinutes = 7.0, cooldownMinutes = 2.0)
        assertTrue(SessionValidator.violations(s, ctx).map { it.code }.containsAll(listOf("WARMUP_SHORT", "COOLDOWN_SHORT")))
        val fixed = SessionValidator.validate(s, ctx).value.session
        assertEquals(17.0, fixed.warmupMinutes!!, 1e-9); assertEquals(12.0, fixed.cooldownMinutes!!, 1e-9)
    }

    // ---------------------------------------------------------------- R3-03 pregnancy week unknown

    @Test fun `R3-03 pregnancy with no week entered avoids lying on the back or front`() {
        val c = resolve(UserCondition("pregnancy", clearance = setOf(ClearanceScope.LIGHT_MODERATE), attested = true))
        assertTrue(c.avoidTags.containsAll(setOf("supine_lying", "prone_lying")))
        val early = resolve(UserCondition("pregnancy", clearance = setOf(ClearanceScope.LIGHT_MODERATE), attested = true, pregnancyWeek = 12))
        assertFalse("supine_lying" in early.avoidTags)
        val ctx = ValidationContext(Level.INTERMEDIATE, weeksTraining = 40, conditions = c, library = Library.all.filter { !it.userAddOnly })
        val s = Session(Tier.FULL, listOf(SessionExercise(Library.require("db-bench-press"), 2, 10, 3.0)))
        assertTrue(SessionValidator.violations(s, ctx).any { it.code == "EXERCISE_NOT_ALLOWED" })
    }

    // ---------------------------------------------------------------- R3-04 diabetes add-on without a type

    @Test fun `R3-04 a diabetes add-on picked without type 1 or 2 brings both base entries`() {
        val r = Conditions.resolve(listOf(UserCondition("diabetes_feet", weeks = 20)))
        val c = r.value
        assertTrue(c.entries.containsAll(setOf("diabetes_feet", "t1d", "t2d")))
        assertEquals(Zone.Z1, c.maxZone); assertEquals(HiitPermission.NO, c.hiit) // before_vigorous without a doctor's OK
        assertTrue("t2d" in c.clearancePrompts)
        assertEquals(2, c.maxConsecutiveInactiveDays); assertFalse(c.strengthOnConsecutiveDays)
        assertTrue(r.decisions.any { it.reason == ReasonKey.CONDITION_PARENT_ASSUMED })
        // With the type given, only that entry comes along.
        val typed = resolve(UserCondition("diabetes_feet"), UserCondition("t2d"))
        assertTrue("t2d" in typed.entries && "t1d" !in typed.entries)
    }

    // ---------------------------------------------------------------- R3-05 postpartum attestation

    @Test fun `R3-05 after birth the healing confirmation gates every later phase`() {
        for (w in listOf(8, 13, 30)) {
            val c = resolve(UserCondition("postpartum", weeksSinceBirth = w))
            assertEquals("week $w", Zone.Z1, c.maxZone)
            assertEquals("week $w", 4.0, c.minRir!!, 1e-9)
            assertEquals(ImpactLevel.NONE, c.impact)
        }
        val ok = resolve(UserCondition("postpartum", weeksSinceBirth = 13, attested = true))
        assertEquals(Zone.Z3, ok.maxZone); assertEquals(2.0, ok.minRir!!, 1e-9)
        val mid = resolve(UserCondition("postpartum", weeksSinceBirth = 8, attested = true))
        assertEquals(Zone.Z1, mid.maxZone); assertEquals(3.0, mid.minRir!!, 1e-9)
    }

    // ---------------------------------------------------------------- R3-06 type 2 diabetes scheduling

    @Test fun `R3-06 type 2 diabetes never gets strength on consecutive days, deload weeks keep the walks, and moved sessions respect it`() {
        val t2d = resolve(UserCondition("t2d", clearance = setOf(ClearanceScope.VIGOROUS)))
        fun strengthDays(p: WeekPlan) = p.days.filter { it.template.strength }.map { it.weekday }.toSet()
        fun adjacent(s: Set<Int>) = s.size >= 2 && s.any { ((it + 1) % 7) in s }
        for (program in listOf(general, fatLoss)) {
            val r = WeekPlanner.plan(input(t2d, program, days = 3, available = setOf(0, 1, 2)), program)
            val s = strengthDays(r.value)
            assertFalse("$s", adjacent(s))
            assertEquals(setOf(0, 2), s)
            assertTrue(r.decisions.any { it.reason == ReasonKey.STRENGTH_DAYS_SPACED })
        }
        // Only two consecutive days available: one strength day, the other cardio, and the decision says why.
        val two = WeekPlanner.plan(input(t2d, general, days = 2, available = setOf(5, 6)), general)
        assertEquals(1, strengthDays(two.value).size)
        // Sunday and Monday touch across the weekend.
        assertFalse(adjacent(strengthDays(WeekPlanner.plan(input(t2d, general, days = 3, available = setOf(6, 0, 3)), general).value)))
        // Deload week: rest days still get walks so no more than 2 days in a row are inactive.
        val deloadWeek = (1..53).first { Blueprint.context(general, it).kind == WeekKind.DELOAD_OR_PIVOT }
        val di = input(t2d, general, days = 2, available = setOf(0, 3)).copy(week = Blueprint.context(general, deloadWeek), deload = true)
        val dp = WeekPlanner.plan(di, general).value
        assertTrue(dp.deload)
        val active = (dp.days.filter { it.slots.isNotEmpty() || it.conditioning.isNotEmpty() }.map { it.weekday } + dp.walkDays).toSet()
        var run = 0; var worst = 0
        for (k in 0 until 14) { val d = k % 7; run = if (d in active) 0 else run + 1; worst = maxOf(worst, run) }
        assertTrue("active $active", worst <= 2)
        // SCH-003: the day after a strength session gets a session without strength work, or nothing.
        val plan = WeekPlanner.plan(input(t2d, fatLoss, days = 4), fatLoss).value
        val next = WeekPlanner.nextSession(plan, emptySet(), strengthYesterday = true, conditions = t2d)
        assertTrue(next == null || next.slots.isEmpty())
        assertNotNull(WeekPlanner.nextSession(plan, emptySet()))
    }

    // ---------------------------------------------------------------- R3-07 unknown IDs, R3-08 HBP status

    @Test fun `R3-07 an unknown condition ID puts the user in conservative mode instead of dropping limits`() {
        val r = Conditions.resolve(listOf(UserCondition("heart_condition")))
        val c = r.value
        assertTrue(c.any && c.conservative)
        assertEquals(setOf("heart_condition"), c.unknown)
        assertEquals(Zone.Z1, c.maxZone); assertEquals(HiitPermission.NO, c.hiit); assertFalse(c.failureAllowed)
        assertEquals(P.SAF_001.conservative_mode.min_rir.toDouble(), c.minRir!!, 1e-9)
        assertTrue("heart_condition" in c.clearancePrompts)
        assertTrue(r.decisions.any { it.reason == ReasonKey.CONDITION_UNKNOWN })
        val mixed = resolve(UserCondition("heart_condition"), UserCondition("asthma", controlled = ControlStatus.YES))
        assertTrue(mixed.conservative && mixed.maxZone == Zone.Z1 && "asthma" in mixed.entries)
    }

    @Test fun `R3-08 high blood pressure follows the status answer whichever entry was stored`() {
        for (status in listOf(ControlStatus.NO, ControlStatus.NOT_SURE)) {
            val r = Conditions.resolve(listOf(UserCondition("hbp_controlled", controlled = status)))
            assertEquals(setOf("hbp_not_controlled"), r.value.entries)
            assertEquals(Zone.Z1, r.value.maxZone)
            assertTrue(r.decisions.any { it.reason == ReasonKey.CONDITION_STATUS_APPLIED })
        }
        assertEquals(setOf("hbp_controlled"), resolve(UserCondition("hbp_controlled", controlled = ControlStatus.YES)).entries)
        assertEquals(setOf("hbp_controlled"), resolve(UserCondition("hbp_controlled")).entries)
    }

    // ---------------------------------------------------------------- R3-09 FL-003 impact gate, R3-10 home kit

    @Test fun `R3-09 the fat-loss impact gate reaches the session and the final gate - no jumps from 50 or with obesity`() {
        for ((age, c) in listOf(55 to ConditionLimits.NONE, 35 to resolve(UserCondition("obesity")))) {
            val plan = WeekPlanner.plan(input(c, fatLoss, days = 4, age = age), fatLoss).value
            assertTrue(plan.days.all { !it.impactAllowed })
            for (day in plan.days.filter { it.slots.isNotEmpty() || it.conditioning.isNotEmpty() }) {
                val w = AwayFromGym.generate(GenerationRequest(day, Level.INTERMEDIATE, 40, 45, gym, Tier.FULL, age = age,
                    week = ValidationContext(Level.INTERMEDIATE, weeksTraining = 40, hiitBaseReady = true), conditions = c, circuitJumps = true)).value
                assertTrue("age $age ${day.weekday}", w.circuits.filterNotNull().none { cp -> cp.moves.any { it.jumping } })
                assertTrue(w.validated.conditioning.none { it.impact > 0 })
            }
        }
        // The final gate rejects impact work on a day the plan marked impact-free.
        val ctx = ValidationContext(Level.INTERMEDIATE, weeksTraining = 40, planImpactAllowed = false)
        val s = Session(Tier.FULL, emptyList(), listOf(ConditioningBlock(Modality.JUMP_ROPE, Zone.Z1, 10.0, impact = 1)))
        assertTrue(SessionValidator.violations(s, ctx).any { it.code == "IMPACT_NOT_ALLOWED" })
        // Where jumps are allowed, a block is flagged as impact only when a jumping move was chosen.
        val young = WeekPlanner.plan(input(ConditionLimits.NONE, fatLoss, days = 4, age = 35), fatLoss).value
        for (day in young.days.filter { it.conditioning.isNotEmpty() }) {
            val w = AwayFromGym.generate(GenerationRequest(day, Level.INTERMEDIATE, 40, 45, gym, Tier.FULL, age = 35,
                week = ValidationContext(Level.INTERMEDIATE, weeksTraining = 40, hiitBaseReady = true), circuitJumps = true)).value
            w.validated.conditioning.forEachIndexed { j, b ->
                val cp = w.circuits.getOrNull(j) ?: return@forEachIndexed
                assertEquals(b.impact > 0, cp.moves.any { it.jumping })
            }
        }
    }

    @Test fun `R3-10 at home only bodyweight conditioning is planned and every validator swap checks the equipment`() {
        val c = resolve(UserCondition("obesity_severe", weeks = 12))
        val plan = WeekPlanner.plan(input(c, general, days = 4, age = 45, type = BlockType.CONDITIONING), general).value
        for (day in plan.days.filter { it.conditioning.isNotEmpty() }) {
            val w = AwayFromGym.generate(GenerationRequest(day, Level.INTERMEDIATE, 40, 60, gym, Tier.FULL, age = 45,
                week = ValidationContext(Level.INTERMEDIATE, weeksTraining = 40, hiitBaseReady = true), conditions = c)).value
            assertTrue(w.validated.conditioning.map { it.modality }.toString(), w.validated.conditioning.all { ModalitySelection.available(it.modality, emptySet()) })
        }
        val ctx = ValidationContext(Level.INTERMEDIATE, weeksTraining = 40, equipmentToday = emptySet())
        val s = Session(Tier.FULL, emptyList(), listOf(ConditioningBlock(Modality.ROWER, Zone.Z1, 15.0)))
        assertTrue(SessionValidator.violations(s, ctx).any { it.code == "MODALITY" && it.ruleId == RuleIds.SUB_001 })
        val fixed = SessionValidator.validate(s, ctx).value.session
        assertTrue(fixed.conditioning.all { ModalitySelection.available(it.modality, emptySet()) })
    }

    // ---------------------------------------------------------------- R3-11 bone loading and pain limits

    @Test fun `R3-11 the bone-loading block honours pain limits, blocked jumping and no-jumping regions`() {
        val ost = resolve(UserCondition("osteoporosis"))
        assertTrue(ost.boneLoading)
        assertEquals(BoneLoadingVariant.SMALL_HOPS, BoneLoading.choose(emptyMap(), emptySet(), emptyList()))
        assertEquals(BoneLoadingVariant.HEEL_DROPS, BoneLoading.choose(emptyMap(), setOf("jumping"), emptyList()))
        assertNull(BoneLoading.choose(mapOf(Joint.KNEE to 0, Joint.ANKLE to 0), setOf("jumping"), emptyList()))
        val day = WeekPlanner.plan(input(ost, general, days = 3, age = 60), general).value.days.first { it.slots.isNotEmpty() }
        fun gen(limits: Map<Joint, Int>, tags: Set<String>) = SessionGenerator.generate(GenerationRequest(day, Level.INTERMEDIATE, 40, 60, gym, Tier.FULL, age = 60,
            jointLimits = limits, blockedTags = tags, week = ValidationContext(Level.INTERMEDIATE, weeksTraining = 40), conditions = ost)).value
        assertNull(gen(mapOf(Joint.KNEE to 0, Joint.ANKLE to 0), setOf("jumping")).boneLoading)
        assertEquals(BoneLoadingVariant.HEEL_DROPS, gen(emptyMap(), setOf("jumping")).boneLoading?.variant)
        assertEquals(BoneLoadingVariant.SMALL_HOPS, gen(emptyMap(), emptySet()).boneLoading?.variant)
        // The final gate sees the block.
        val ctx = ValidationContext(Level.INTERMEDIATE, weeksTraining = 40, blockedTags = setOf("jumping"), conditions = ost)
        val s = Session(Tier.FULL, listOf(SessionExercise(Library.require("leg-press"), 2, 10, 3.0)), boneLoading = BoneLoadingVariant.SMALL_HOPS)
        assertTrue(SessionValidator.violations(s, ctx).any { it.code == "BONE_LOADING_NOT_ALLOWED" })
        assertEquals(BoneLoadingVariant.HEEL_DROPS, SessionValidator.validate(s, ctx).value.session.boneLoading)
    }

    // ---------------------------------------------------------------- R3-12 activity floor, R3-13 calibration and strength trend

    @Test fun `R3-12 the weekly activity target never drops below 150 minutes`() {
        for (last in listOf(10.0, 50.0, 100.0, 130.0)) for (age in listOf(35, 45, 62, 70))
            assertTrue("$age $last", FatLoss.weeklyTarget(age, last).value.thisWeek >= 150.0 - 1e-9)
    }

    @Test fun `R3-13 machine stacks climb past coarse steps in calibration, free weights do not overshoot, and the strength trend works without an e1RM`() {
        val stack = (1..20).map { it * 5.0 }
        val step = Calibration.next(5.0, 10, 5.0, 1, stack, coarseStepsAllowed = Calibration.coarseStepsAllowed(LoadType.STACK)).value
        assertEquals(CalibrationStep.Continue(10.0), step)
        assertTrue(Calibration.next(5.0, 10, 5.0, 1, stack).value is CalibrationStep.Found) // free-weight default unchanged
        assertTrue(Calibration.next(5.0, 10, 4.0, 1, stack, coarseStepsAllowed = true).value is CalibrationStep.Found) // RIR 4: band kept
        assertFalse(Calibration.coarseStepsAllowed(LoadType.DUMBBELL))
        val pts = listOf(StrengthPoint(1, "leg-press", 40.0, 12), StrengthPoint(3, "leg-press", 42.5, 12),
            StrengthPoint(29, "leg-press", 50.0, 12), StrengthPoint(31, "leg-press", 52.5, 10),
            StrengthPoint(2, "chest-press-machine", 30.0, 10), StrengthPoint(30, "chest-press-machine", 30.0, 10))
        val t = BodyProgress.strengthTrend(pts, 31).value
        assertEquals(TrendDirection.UP, t.lifts.first { it.exerciseId == "leg-press" }.direction)
        assertEquals(TrendDirection.FLAT, t.lifts.first { it.exerciseId == "chest-press-machine" }.direction)
        assertEquals(1, t.improved)
    }

    // ---------------------------------------------------------------- R3-14 smaller findings

    @Test fun `R3-14 smaller findings - CAL-002 band, ceiling, 65 plus cap, 40-49 preference, habit range, clearance prompt, defaults, calm session, tags`() {
        // CAL-002: a fresh entry keeps its value at 62; from 14 days the 60+ shift applies.
        val dbs = (1..25).map { it * 2.0 }
        fun start(days: Int, age: Int) = KnownLoads.start(KnownEntry("goblet-squat", 30.0, 8, 2.0, daysAgo = days), age, false, 10, 2.0, dbs).value
        assertEquals(0, (start(5, 62) as KnownStart.Estimate).reductionPct)
        assertEquals(20, (start(20, 62) as KnownStart.Estimate).reductionPct)
        // KnownStart.Ceiling caps the calibration start in the generator.
        val day = WeekPlanner.plan(input(ConditionLimits.NONE, general, days = 3), general).value.days.first { it.slots.isNotEmpty() }
        val first = day.slots.first { it.exercise.loadType == LoadType.DUMBBELL || it.exercise.loadType == LoadType.BARBELL || it.exercise.loadType == LoadType.STACK }
        val w = SessionGenerator.generate(GenerationRequest(day, Level.INTERMEDIATE, 40, 75, gym, Tier.FULL, age = 40, bodyweightKg = 120.0,
            calibrationCeilings = mapOf(first.exercise.id to 1.0), week = ValidationContext(Level.INTERMEDIATE, weeksTraining = 40))).value
        w.items.firstOrNull { it.exercise.id == first.exercise.id && it.calibrating }?.let { item ->
            val lightest = com.personalfitnesscoach.engine.calc.PlateMath.loadsFor(item.exercise, com.personalfitnesscoach.engine.calc.Inventory()).minOrNull()
            assertEquals(lightest, item.load)
        }
        // AER-003 weekly cap: +12% from 65.
        assertEquals(115.0, Aerobic.weeklyCap(100.0, 40), 1e-9); assertEquals(112.0, Aerobic.weeklyCap(100.0, 70), 1e-9)
        // FL-003 40–49: low-impact machines preferred.
        assertTrue(FatLoss.mix(45, Level.INTERMEDIATE, 4, 20, false).value.lowImpactPreferred)
        assertFalse(FatLoss.mix(35, Level.INTERMEDIATE, 4, 20, false).value.lowImpactPreferred)
        // ADH-004: a range, never a fixed day count.
        assertTrue(Express.habit.varies && Express.habit.exerciseOftenLonger && !Express.habit.missedDayUndoes)
        // "Before vigorous": the doctor's-OK prompt stays after a light-moderate OK, until vigorous is confirmed.
        assertTrue("t2d" in resolve(UserCondition("t2d", clearance = setOf(ClearanceScope.LIGHT_MODERATE))).clearancePrompts)
        assertFalse("t2d" in resolve(UserCondition("t2d", clearance = setOf(ClearanceScope.VIGOROUS))).clearancePrompts)
        // Fail-closed default: an unknown training history meets no base period.
        assertEquals(0, ValidationContext(Level.INTERMEDIATE).weeksTraining)
        // MOB-006 is never offered with illness, a red flag or a "follow your care provider" entry.
        assertFalse(Mobility.calmSessionOffered(true, null, null, illness = true, redFlag = false))
        assertFalse(Mobility.calmSessionOffered(true, null, null, illness = false, redFlag = true))
        assertFalse(Mobility.calmSessionOffered(true, null, null, illness = false, redFlag = false, followCareProvider = true))
        // Library tags: hand-hovering single-leg balance drills are unsupported single-leg work; child pose rounds the spine.
        val drills = GeneratedLibrary.drills.associateBy { it.id }
        for (id in listOf("balance-single-leg-stand", "balance-clock-reach")) assertTrue(drills.getValue(id).tags.containsAll(setOf("unsupported_single_leg", "high_fall_risk")))
        assertTrue("spinal_flexion" in drills.getValue("stretch-child-pose").tags)
        val stroke = resolve(UserCondition("stroke", clearance = setOf(ClearanceScope.LIGHT_MODERATE)))
        assertTrue(Mobility.balanceDrills(10.0, jointLimits = emptyMap(), avoidTags = stroke.avoidTags).value.none { "unsupported_single_leg" in it.drill.tags })
        // Severe obesity: "uncomfortable lying on the back" is honoured.
        assertTrue("supine_lying" in resolve(UserCondition("obesity_severe", supineUncomfortable = true)).avoidTags)
        assertFalse("supine_lying" in resolve(UserCondition("obesity_severe")).avoidTags)
    }

    // ---------------------------------------------------------------- R3-15 property check of the new paths

    @Test fun `R3-15 random condition sets - warm-ups at the floor, no supine work in pregnancy, breastbone and impact limits in every generated session`() {
        val rnd = java.util.Random((System.getenv("PFC_SEED") ?: "20261009").toLong())
        val ids = Conditions.entries.keys.sorted()
        repeat((System.getenv("PFC_CASES") ?: "60").toInt()) { n ->
            val picked = ids.shuffled(kotlin.random.Random(rnd.nextLong())).take(1 + rnd.nextInt(3)).map { id ->
                UserCondition(id, weeks = rnd.nextInt(20), clearance = if (rnd.nextBoolean()) setOf(ClearanceScope.VIGOROUS) else emptySet(),
                    subFlags = if (id == "heart" && rnd.nextBoolean()) setOf("recent_breastbone_surgery") else emptySet(),
                    pregnancyWeek = if (rnd.nextBoolean()) null else rnd.nextInt(40), weeksSinceBirth = if (rnd.nextBoolean()) null else rnd.nextInt(52))
            }
            val c = Conditions.resolve(picked).value
            if (c.blocked.isNotEmpty()) return@repeat
            val age = 30 + rnd.nextInt(45)
            val program = if (c.fatLossOffered && rnd.nextBoolean()) fatLoss else general
            val i = input(c, program, days = 2 + rnd.nextInt(4), age = age, minutes = listOf(30, 45, 60)[rnd.nextInt(3)])
            val plan = WeekPlanner.plan(i, program).value
            for (r in generateAll(plan, c, age = age, minutes = i.sessionMinutes)) {
                val w = r.value
                val tag = "case $n ${picked.map { it.id }} age $age"
                if (w.items.isNotEmpty() || w.conditioning.isNotEmpty())
                    assertTrue("$tag warm-up ${w.warmupMinutes}", w.warmupMinutes >= TimeBudget.warmupFloor(age, c.extraWarmupMin.toDouble()) - 1e-9)
                assertTrue(tag, w.items.none { s -> s.exercise.limitationTags.any { it in c.avoidTags } })
                assertTrue(tag, w.validated.conditioning.none { it.modality in c.avoidModalities })
                if (!c.impact.allowsImpact || !plan.days.first().impactAllowed) assertTrue(tag, w.validated.conditioning.none { it.impact > 0 })
                assertTrue(tag, w.validated.conditioning.all { it.zone <= c.maxZone })
            }
        }
    }
}
