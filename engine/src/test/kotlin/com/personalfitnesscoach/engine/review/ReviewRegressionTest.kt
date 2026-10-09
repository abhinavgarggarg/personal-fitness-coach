package com.personalfitnesscoach.engine.review

import com.personalfitnesscoach.engine.calc.Volume
import com.personalfitnesscoach.engine.conditioning.Aerobic
import com.personalfitnesscoach.engine.conditioning.BlockKind
import com.personalfitnesscoach.engine.conditioning.HiitMenu
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.generation.GenerationRequest
import com.personalfitnesscoach.engine.generation.SessionGenerator
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.model.Muscle
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.model.Zone
import com.personalfitnesscoach.engine.planning.Mobility
import com.personalfitnesscoach.engine.program.BlockType
import com.personalfitnesscoach.engine.program.Blueprint
import com.personalfitnesscoach.engine.program.FULL_GYM
import com.personalfitnesscoach.engine.program.Goal
import com.personalfitnesscoach.engine.program.PlannedConditioning
import com.personalfitnesscoach.engine.program.SelectionContext
import com.personalfitnesscoach.engine.program.Selector
import com.personalfitnesscoach.engine.program.SlotRole
import com.personalfitnesscoach.engine.program.SlotSpec
import com.personalfitnesscoach.engine.program.Streak
import com.personalfitnesscoach.engine.program.Templates
import com.personalfitnesscoach.engine.program.WeekKind
import com.personalfitnesscoach.engine.program.WeekPlanner
import com.personalfitnesscoach.engine.program.WeekRecord
import com.personalfitnesscoach.engine.program.weekInput
import com.personalfitnesscoach.engine.progression.Bodyweight
import com.personalfitnesscoach.engine.progression.BwSession
import com.personalfitnesscoach.engine.progression.BwSet
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.safety.HiitProtocol
import com.personalfitnesscoach.engine.safety.RegionConstraint
import com.personalfitnesscoach.engine.safety.ScreeningMode
import com.personalfitnesscoach.engine.safety.ValidationContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the independent Part 2 review (findings R01–R20, docs/phase3/part2_report.md).
 * Each test reproduces the reviewer's failing case and asserts the corrected behaviour.
 */
class ReviewRegressionTest {
    private val program = Blueprint.plan(listOf(Goal.GENERAL_FITNESS)).value
    private fun weekOf(type: BlockType, last: Boolean = false): Int = (1..52).first { w ->
        val c = Blueprint.context(program, w)
        c.block.type == type && c.kind == WeekKind.LOADING && (!last || c.weekInBlock == c.block.loadingWeeks)
    }
    private fun vctx(level: Level = Level.INTERMEDIATE, impactSoFar: Int = 0) =
        ValidationContext(level, hiitBaseReady = true, impactSessionsThisWeekSoFar = impactSoFar)

    @Test fun `R01 SAF-007 an illness day is a rest day with no conditioning, warm-up or cool-down`() {
        val day = WeekPlanner.plan(weekInput(4, weekOf(BlockType.BUILD), program = program), program).value.days.first()
        for (sym in listOf("fever", "chest_symptoms")) {
            val r = SessionGenerator.generate(GenerationRequest(day, Level.INTERMEDIATE, 40, 60, FULL_GYM, Tier.FULL, 35, illnessSymptoms = setOf(sym), week = vctx()))
            val w = r.value
            assertEquals(Tier.RECOVERY, w.tier)
            assertTrue(w.items.isEmpty() && w.conditioning.isEmpty() && w.validated.conditioning.isEmpty())
            assertTrue(w.warmupDrills.isEmpty() && w.cooldown.isEmpty())
            assertEquals(0.0, w.plannedMinutes, 1e-9)
            assertTrue(r.decisions.any { it.reason == ReasonKey.ILLNESS_REST_DAY })
        }
    }

    @Test fun `R02 CON-004 converted conditioning keeps its impact, so the validator still sees jump rope`() {
        val plan = WeekPlanner.plan(weekInput(4, weekOf(BlockType.BUILD), program = program), program).value
        val day = plan.days.first { it.template.strength && !it.heavyLower }.copy(conditioning = listOf(PlannedConditioning(Modality.JUMP_ROPE, Zone.Z1, 15.0, impact = 1)))
        val spine = RegionConstraint(Joint.SPINE, 1, 3, noFailure = true, noJumping = true, suggestProfessional = false)
        for (t in listOf(Tier.FULL, Tier.MODIFIED, Tier.LIGHT)) {
            val w = SessionGenerator.generate(GenerationRequest(day, Level.INTERMEDIATE, 40, 90, FULL_GYM, t, 35, regions = listOf(spine), week = vctx(impactSoFar = 1))).value
            assertTrue("$t ${w.validated.conditioning}", w.validated.conditioning.none { it.modality == Modality.JUMP_ROPE || it.impact > 0 })
        }
    }

    @Test fun `R03 CON-004 box jumps count as impact, so a week has at most one impact session`() {
        val gym = setOf("dumbbells", "bench", "incline_bench", "pullup_bar", "bands", "mat", "plyo_box", "jump_rope", "rack", "barbell")
        for (n in 2..6) {
            val plan = WeekPlanner.plan(weekInput(n, weekOf(BlockType.POWER_ATHLETICISM), program = program, gym = gym)
                .copy(modalityPreferences = mapOf(Modality.JUMP_ROPE to 1.0)), program).value
            val impactDays = plan.days.filter { d -> d.slots.any { it.exercise.impact > 0 } || d.conditioning.any { it.impact > 0 } }
            assertTrue("$n days: ${impactDays.map { it.weekday }}", impactDays.size <= P.CON_004.impact_sessions_per_week_max)
        }
    }

    @Test fun `R04 SCH-002 adjacent available days never give back-to-back heavy lower days or more than 3 hard days running`() {
        for ((n, avail) in listOf(2 to setOf(0, 1), 3 to setOf(0, 1, 2), 4 to setOf(0, 1, 2, 3), 5 to setOf(0, 1, 2, 3, 4))) {
            val r = WeekPlanner.plan(weekInput(n, weekOf(BlockType.BUILD), program = program).copy(availableDays = avail), program)
            val heavy = r.value.days.filter { it.heavyLower }.map { it.weekday }.sorted()
            assertTrue("$avail heavy lower on $heavy", heavy.zipWithNext().all { (a, b) -> (b - a) * 24 >= P.SCH_002.heavy_lower_min_h })
            assertTrue(Templates.maxHardRun(r.value.days.map { it.weekday }, r.value.days.map { it.template }) <= P.SCH_002.max_consecutive_hard_days)
        }
    }

    @Test fun `R05 SAF-003 and SAF-004 limitation tags and joint stress keep blocked movements out`() {
        for (id in listOf("air-squat", "tempo-squat", "bodyweight-reverse-lunge", "lateral-lunge")) assertTrue(id, "deep_knee_flexion" in Library.require(id).limitationTags)
        for (id in listOf("front-squat", "kb-double-front-squat", "overhead-press")) assertTrue(id, "spinal_loading" in Library.require(id).limitationTags)
        for (e in Library.all.filter { it.pattern == Pattern.VERTICAL_PULL }) assertTrue(e.id, "overhead" in e.limitationTags)
        for (e in Library.all.filter { it.pattern == Pattern.LOADED_CARRY }) assertTrue(e.id, listOf(Joint.KNEE, Joint.HIP, Joint.ANKLE).all { e.stress(it) >= 1 })

        val knee = SelectionContext(Level.INTERMEDIATE, 40, FULL_GYM, blockedTags = setOf("deep_knee_flexion"), jointLimits = mapOf(Joint.KNEE to 1))
        for (spec in listOf(SlotSpec("x.squat", SlotRole.MAIN, Pattern.SQUAT), SlotSpec("x.lunge", SlotRole.SECONDARY, Pattern.LUNGE)))
            Selector.select(spec, knee).value?.let { assertFalse(it.id, "deep_knee_flexion" in it.limitationTags) }
        val overhead = SelectionContext(Level.INTERMEDIATE, 40, FULL_GYM, blockedTags = setOf("overhead"))
        Selector.select(SlotSpec("v", SlotRole.MAIN, Pattern.VERTICAL_PULL), overhead).value?.let { assertFalse(it.id, "overhead" in it.limitationTags) }
        val spine = SelectionContext(Level.ADVANCED, 40, FULL_GYM, blockedTags = setOf("spinal_loading", "spinal_flexion"))
        assertTrue(Library.all.filter { Selector.matches(it, SlotSpec("s", SlotRole.MAIN, Pattern.SQUAT)) && Selector.allowed(it, spine) }.none { it.id == "front-squat" })
        assertFalse(Selector.select(SlotSpec("o", SlotRole.MAIN, Pattern.VERTICAL_PUSH), spine).value?.id == "overhead-press")
        val legs0 = SelectionContext(Level.INTERMEDIATE, 40, FULL_GYM, jointLimits = mapOf(Joint.KNEE to 0, Joint.ANKLE to 0, Joint.HIP to 0))
        assertEquals(null, Selector.select(SlotSpec("c", SlotRole.CARRY, Pattern.LOADED_CARRY), legs0).value)
    }

    @Test fun `R06 INT-003 nothing you could drop on yourself is planned to failure`() {
        assertFalse(Library.require("db-overhead-triceps-extension").failureSafe)
        for (id in listOf("step-up", "db-dead-bug")) assertFalse(id, Library.require(id).failureSafe)
        val free = setOf("barbell", "dumbbells", "kettlebells", "ez_bar")
        assertTrue(Library.all.filter { it.failureSafe && "overhead" in it.limitationTags && it.equipment.any { e -> e in free } }.map { it.id }.isEmpty())
        val gym = setOf("dumbbells", "bench", "incline_bench", "pullup_bar", "bands", "mat")
        val plan = WeekPlanner.plan(weekInput(5, weekOf(BlockType.BUILD_2, last = true), program = program, gym = gym), program).value
        for (d in plan.days) {
            val w = SessionGenerator.generate(GenerationRequest(d, Level.INTERMEDIATE, 40, 90, gym, Tier.FULL, 35, week = vctx())).value
            assertTrue(w.validated.exercises.filter { it.lastSetToFailure }.all { it.exercise.failureSafe })
        }
    }

    @Test fun `R07 GEN-001 muscle maps iterate in a fixed order`() {
        val entries = listOf(Library.require("machine-hip-thrust") to 5.0, Library.require("leg-extension") to 5.0, Library.require("goblet-squat") to 4.0)
        for (m in listOf(Volume.weekly(entries), Volume.directPerSession(entries), Volume.credit(Library.require("back-squat"), 3.0))) {
            assertTrue(m is java.util.EnumMap<*, *>)
            assertEquals(m.keys.sortedBy { (it as Muscle).ordinal }, m.keys.toList())
        }
    }

    @Test fun `R08 AER-003 next Z1 sessions keep the week within +15 percent`() {
        val next = Aerobic.nextZ1Minutes(15.0, 45.0, 0.0, 3).value
        assertTrue("$next", 3 * next <= Aerobic.weeklyCap(45.0) + 1e-9)
        val i = weekInput(3, weekOf(BlockType.FOUNDATION), minutes = 120, program = program, baseReady = false)
            .copy(z1SessionMinutes = 15.0, lastWeekAerobicMinutes = 45.0)
        val plan = WeekPlanner.plan(i, program).value
        val z1 = plan.aerobic.filter { it.zone == Zone.Z1 }.sumOf { it.workMinutes }
        assertTrue("$z1", z1 <= Aerobic.weeklyCap(45.0) + 1e-9)
    }

    @Test fun `R09 HIIT-002 and PROG-007 protocols by block, weekly work budget and the beginner 1 to 2 ratio`() {
        assertEquals(HiitProtocol.LONG, HiitMenu.choose(BlockKind.BUILD, Level.INTERMEDIATE, 5, 0, 0).value)
        assertEquals(HiitProtocol.LONG, HiitMenu.choose(BlockKind.CONDITIONING, Level.INTERMEDIATE, 5, 0, 0).value)
        assertEquals(HiitProtocol.SHORT, HiitMenu.choose(BlockKind.CONDITIONING, Level.INTERMEDIATE, 5, 0, 1).value)
        assertEquals(HiitProtocol.SHORT, HiitMenu.choose(BlockKind.BUILD, Level.INTERMEDIATE, 0, 0, 0).value)
        val w = weekOf(BlockType.CONDITIONING)
        for (last in listOf(0.0, 4.0, 10.0, 16.0)) for (exp in listOf(0, 2, 6)) {
            val plan = WeekPlanner.plan(weekInput(4, w, program = program).copy(hiitExposures = exp, lastWeekHiitWorkMinutes = last), program).value
            val work = plan.days.flatMap { it.conditioning }.filter { it.hiit }.sumOf { it.workMinutes }
            assertTrue("last $last exposures $exp → $work", work <= HiitMenu.weeklyWorkBudget(last) + 1e-9)
        }
        var iv = HiitMenu.start(HiitProtocol.SHORT, Level.BEGINNER, firstEver = true)
        repeat(20) { iv = HiitMenu.progress(iv, Level.BEGINNER); assertTrue("$iv", iv.restSec >= 2 * iv.workSec) }
        var sp = HiitMenu.start(HiitProtocol.SPRINT, Level.ADVANCED, firstEver = false)
        repeat(20) { sp = HiitMenu.progress(sp, Level.ADVANCED); assertTrue("$sp", sp.restSec >= 6 * sp.workSec) }
    }

    @Test fun `R10 ADH-004 the express session fits 30 minutes`() {
        for (n in listOf(3, 5)) for (bt in listOf(BlockType.FOUNDATION, BlockType.STRENGTH, BlockType.CONDITIONING)) for (lvl in Level.entries) {
            val plan = WeekPlanner.plan(weekInput(n, weekOf(bt), level = lvl, minutes = 90, program = program), program).value
            for (d in plan.days) {
                val e = SessionGenerator.express(GenerationRequest(d, lvl, 40, 90, FULL_GYM, Tier.FULL, 55, week = vctx(lvl))).value
                assertTrue("$n $bt $lvl ${d.template}: ${e.plannedMinutes}", e.plannedMinutes <= 30.0 + 1e-6)
                assertTrue(e.conditioning.isEmpty() && e.items.size <= 3)
            }
        }
    }

    @Test fun `R11 SAF-001 moderate-only screening plans no Z2 tempo, Z3 or HIIT`() {
        for (t in listOf(BlockType.BUILD, BlockType.CONDITIONING)) {
            val plan = WeekPlanner.plan(weekInput(6, weekOf(t), program = program).copy(screening = ScreeningMode.MODERATE_ONLY), program).value
            val blocks = plan.days.flatMap { it.conditioning }
            assertTrue("$t $blocks", blocks.all { it.zone == Zone.Z1 && !it.hiit })
            for (d in plan.days) {
                val w = SessionGenerator.generate(GenerationRequest(d, Level.INTERMEDIATE, 40, 90, FULL_GYM, Tier.FULL, 35, ScreeningMode.MODERATE_ONLY, week = vctx())).value
                assertTrue(w.validated.conditioning.all { it.zone == Zone.Z1 })
            }
        }
    }

    @Test fun `R12 SAF-003 warm-up and cool-down drills respect today's joint limits`() {
        val drills = Mobility.warmupDrills(setOf(Pattern.SQUAT, Pattern.HORIZONTAL_PUSH, Pattern.VERTICAL_PULL), FULL_GYM,
            restricted = setOf(Joint.WRIST, Joint.SHOULDER), jointLimits = mapOf(Joint.WRIST to 0, Joint.SHOULDER to 0), avoidTags = emptySet()).value
        assertTrue(drills.isNotEmpty())
        assertTrue(drills.map { it.drill.id }.toString(), drills.all { it.drill.stress(Joint.WRIST) == 0 && it.drill.stress(Joint.SHOULDER) == 0 })
        val plan = WeekPlanner.plan(weekInput(4, weekOf(BlockType.BUILD), program = program), program).value
        val day = plan.days.first { it.template.name.startsWith("UPPER") }
        val w = SessionGenerator.generate(GenerationRequest(day, Level.INTERMEDIATE, 40, 60, FULL_GYM, Tier.FULL, 35, jointLimits = mapOf(Joint.WRIST to 0), week = vctx())).value
        assertTrue((w.warmupDrills + w.cooldown).all { it.drill.stress(Joint.WRIST) == 0 })
    }

    @Test fun `R13 BW-001 a ladder rung on another pattern does not empty the slot`() {
        val bw = setOf("mat", "pullup_bar")
        val ctx = SelectionContext(Level.INTERMEDIATE, 40, bw, ladderRungs = mapOf("squat" to "bodyweight-split-squat"))
        val squat = Selector.select(SlotSpec("A.squat", SlotRole.MAIN, Pattern.SQUAT), ctx).value
        assertNotNull(squat); assertEquals(Pattern.SQUAT, squat!!.pattern)
        val lunge = Selector.select(SlotSpec("B.lunge", SlotRole.SECONDARY, Pattern.LUNGE), ctx.copy(ladderRungs = mapOf("squat" to "tempo-squat"))).value
        assertNotNull(lunge)
    }

    @Test fun `R14 RDY-004 the reported load factor is the real one, or a lighter variation is used`() {
        val plan = WeekPlanner.plan(weekInput(4, weekOf(BlockType.BUILD), program = program), program).value
        val day = plan.days.first { d -> d.slots.any { it.exercise.id == "back-squat" } }
        val req = GenerationRequest(day, Level.INTERMEDIATE, 40, 90, FULL_GYM, Tier.LIGHT, 35, e1rm = mapOf("back-squat" to 26.0), week = vctx())
        val full = SessionGenerator.generate(req.copy(tier = Tier.FULL)).value.items.first { it.exercise.id == "back-squat" }
        val light = SessionGenerator.generate(req)
        val sq = light.value.items.firstOrNull { it.exercise.id == "back-squat" }
        if (sq != null) {
            assertTrue(sq.loadFactor <= 0.85 + 1e-9)
            assertEquals(sq.load!! / full.load!!, sq.loadFactor, 0.01)
        } else assertTrue(light.decisions.any { it.reason == ReasonKey.LOAD_CANNOT_REDUCE })
        for (it in light.value.validated.exercises) assertTrue(it.loadFactor <= (if (it.main) 0.85 else 1.0) + 1e-9)
    }

    @Test fun `R15 CORE-002 and BW-002 targets stay inside each exercise's own range`() {
        val carry = Library.require("suitcase-carry")
        val t = Bodyweight.next(carry, List(2) { BwSession(List(3) { BwSet(25, 1.0) }) }, null, null).value.targetReps
        assertTrue("$t", t >= 25 && t <= carry.maxExtendedReps)
        val crawl = Library.require("bear-crawl")
        val c = Bodyweight.next(crawl, List(2) { BwSession(List(3) { BwSet(15, 1.0) }) }, null, null).value.targetReps
        assertTrue("$c", c >= 15 && c <= crawl.maxExtendedReps)
        val neg = Library.require("pull-up-negative")
        assertTrue(Bodyweight.topOfRange(neg) <= neg.maxExtendedReps)
        assertEquals(neg.defaultRepRange.last, Bodyweight.topOfRange(neg))
    }

    @Test fun `R16 SCH-002 more days requested than available plans the templates for the available days`() {
        val plan = WeekPlanner.plan(weekInput(4, weekOf(BlockType.STRENGTH), program = program).copy(availableDays = setOf(0, 2, 4)), program).value
        assertEquals(3, plan.days.size)
        assertEquals(Templates.forDays(3).toSet(), plan.days.map { it.template }.toSet())
        assertTrue(plan.days.flatMap { it.slots }.any { it.main && it.exercise.pattern == Pattern.HINGE })
    }

    @Test fun `R17 FREQ-005 steady aerobic blocks keep their 10-minute floor in short sessions`() {
        for (m in listOf(30, 45, 60)) for (n in 3..5) {
            val r = WeekPlanner.plan(weekInput(n, weekOf(BlockType.STRENGTH), minutes = m, program = program), program)
            // Steady blocks only: HIIT work (Z3) is counted in minutes of work, not as a FREQ-005 block.
            val ok = r.value.aerobic.filter { it.zone != Zone.Z3 }.all { it.workMinutes >= P.FREQ_005.min_block_minutes - 1e-9 }
            assertTrue("$n d $m min ${r.value.aerobic}", ok || r.decisions.any { it.reason == ReasonKey.AEROBIC_CHECK_FAILED })
        }
    }

    @Test fun `R18 a validator swap keeps the slot's role sensible and main lifts keep their ramp`() {
        val gym = setOf("dumbbells", "bench", "incline_bench", "pullup_bar", "bands", "mat", "plyo_box", "rack", "barbell", "leg_press", "hack_squat")
        val plan = WeekPlanner.plan(weekInput(4, weekOf(BlockType.POWER_ATHLETICISM), program = program, gym = gym).copy(excludedIds = com.personalfitnesscoach.engine.generation.LOW_IMPACT_POWER), program).value
        val day = plan.days.first { d -> d.slots.any { it.exercise.id == "box-jump" } }
        val req = GenerationRequest(day, Level.INTERMEDIATE, 40, 90, gym, Tier.FULL, 35,
            e1rm = mapOf("back-squat" to 140.0, "front-squat" to 110.0, "leg-press" to 250.0, "hack-squat" to 180.0, "goblet-squat" to 40.0), week = vctx(impactSoFar = 1))
        val out = SessionGenerator.generate(req).value
        assertTrue(out.items.none { it.exercise.id == "box-jump" })
        assertTrue(out.items.filter { it.role == SlotRole.POWER }.all { it.exercise.powerCapable && it.exercise.impact == 0 })
        assertEquals(out.items.map { it.exercise.id }, out.validated.exercises.map { it.exercise.id })
        val firstMain = out.items.firstOrNull { it.main && it.load != null && !it.calibrating }
        if (firstMain != null) assertTrue(firstMain.warmupSets.isNotEmpty())
    }

    @Test fun `R19 LIGHT days add RDY-004 mobility and HIIT intervals reach the output`() {
        val plan = WeekPlanner.plan(weekInput(4, weekOf(BlockType.CONDITIONING), program = program), program).value
        val d = plan.days.first { it.template.strength }
        val light = SessionGenerator.generate(GenerationRequest(d, Level.INTERMEDIATE, 40, 60, FULL_GYM, Tier.LIGHT, 35, week = vctx())).value
        assertTrue(light.mobilityMinutes >= P.RDY_004.LIGHT.mobility_minutes[0] - 1e-9)
        val hiitDay = plan.days.first { day -> day.conditioning.any { it.hiit } }
        val w = SessionGenerator.generate(GenerationRequest(hiitDay, Level.INTERMEDIATE, 40, 90, FULL_GYM, Tier.FULL, 35, week = vctx())).value
        assertEquals(w.validated.conditioning.size, w.intervals.size)
        w.validated.conditioning.forEachIndexed { k, b -> assertEquals(b.hiit, w.intervals[k] != null) }
    }

    @Test fun `R20 PER-005 a long break in a deload-or-pivot week starts the next block, and a week with nothing planned keeps the streak`() {
        val pivot = (1..52).first { Blueprint.context(program, it).kind == WeekKind.DELOAD_OR_PIVOT }
        val ctx = Blueprint.context(program, pivot)
        val step = Blueprint.advanceClock(program, pivot, 4, 0, Blueprint.Disruption.BREAK, breakDays = 60).value
        assertEquals(Blueprint.ClockAction.RESTART_BLOCK, step.action)
        assertEquals(program.blocks[ctx.blockIndex + 1].startWeek, step.nextClockWeek)
        val streak = Streak.compute(listOf(WeekRecord(3, 3), WeekRecord(3, 3), WeekRecord(0, 0), WeekRecord(3, 3)))
        assertEquals(3, streak.current)
        assertEquals(null, streak.freezeUsedWeeksAgo)
    }
}
