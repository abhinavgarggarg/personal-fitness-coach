package com.personalfitnesscoach.engine.safety

import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.generation.GenerationRequest
import com.personalfitnesscoach.engine.generation.SessionGenerator
import com.personalfitnesscoach.engine.model.CostClass
import com.personalfitnesscoach.engine.model.Exercise
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.LoadType
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.model.Muscle
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.model.Zone
import com.personalfitnesscoach.engine.program.Blueprint
import com.personalfitnesscoach.engine.program.FULL_GYM
import com.personalfitnesscoach.engine.program.Goal
import com.personalfitnesscoach.engine.program.WeekPlanner
import com.personalfitnesscoach.engine.program.weekInput
import com.personalfitnesscoach.engine.registry.RuleIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Part 8 journey findings on the weekly limits (VOL-007, LOAD-005, D-107): the week's budget is paced across its sessions, a weight
 * still being found counts as it is counted afterwards, conditioning is shortened rather than dropped when that is enough, and a day
 * with nothing left is a recovery day.
 */
class WeekPacingTest {
    private fun ex(id: String, m: Muscle, cost: CostClass = CostClass.MACHINE_OR_CABLE_COMPOUND) =
        Exercise(id, id, Pattern.HORIZONTAL_PULL, setOf(m), loadType = LoadType.STACK, costClass = cost)

    /** Six exercises of 4 sets at RIR 2: 6 × 4 × 0.8 = 19.2 SSU. */
    private val big = Session(Tier.FULL, List(6) { SessionExercise(ex("x$it", Muscle.entries[it]), 4, 10, 2.0) })

    @Test fun `TC-LOAD-005e a session uses only its share of what is left of the week's load budget`() {
        // Cap 1000 AU, 400 used, k = 20: 30 SSU left this week. Half of it today when today is half of what is still planned.
        val c = ValidationContext(Level.INTERMEDIATE, k = 20.0, weeklyLoadCap = 1000.0, weekLoadSoFar = 400.0)
        val half = SessionValidator.sessionSsu(SessionValidator.validate(big, c.copy(weekShare = 0.5)).value.session)
        assertTrue("within today's share ($half)", 400.0 + 20.0 * half <= 700.0 + 1e-9)
        assertTrue("not cut further than needed ($half)", half > 15.0 - 0.8 - 1e-9)
        // The week's last session may use all that is left, as before.
        assertEquals(19.2, SessionValidator.sessionSsu(SessionValidator.validate(big, c).value.session), 1e-9)
    }

    @Test fun `TC-VOL-007c pacing applies to the weekly SSU limit and never loosens a limit`() {
        val c = ValidationContext(Level.INTERMEDIATE, weeklySsuLimit = 40.0, weekSsuSoFar = 10.0, weekShare = 0.25)
        val out = SessionValidator.sessionSsu(SessionValidator.validate(big, c).value.session)
        assertTrue("≤ 10 + 30 × 0.25 ($out)", 10.0 + out <= 17.5 + 1e-9)
        for (share in listOf(-1.0, 0.0, 0.3, 1.0, 2.0)) for (soFar in listOf(0.0, 20.0, 40.0, 55.0)) {
            val p = SessionValidator.paced(40.0, soFar, c.copy(weekShare = share))
            assertTrue("share $share, so far $soFar → $p", p <= 40.0 + 1e-9 && (soFar >= 40.0 || p >= soFar - 1e-9))
        }
        // A week already at its limit leaves nothing, whatever the share.
        assertEquals(40.0, SessionValidator.paced(40.0, 40.0, c.copy(weekShare = 1.0)), 1e-9)
    }

    @Test fun `TC-VOL-007d a weight still being found counts as two sets at the target effort`() {
        val e = SessionExercise(ex("row", Muscle.LATS), 3, 10, 2.0)
        assertEquals(2.4, SessionValidator.sessionSsu(Session(Tier.FULL, listOf(e))), 1e-9)
        assertEquals(1.6, SessionValidator.sessionSsu(Session(Tier.FULL, listOf(e.copy(calibrating = true)))), 1e-9)
        assertEquals(0.8, SessionValidator.sessionSsu(Session(Tier.FULL, listOf(e.copy(sets = 1, calibrating = true)))), 1e-9)
        // Over the weekly limit, it is removed whole in one step (taking one of its sets would change nothing).
        val s = Session(Tier.FULL, listOf(e.copy(calibrating = true)))
        val out = SessionValidator.validate(s, ValidationContext(Level.INTERMEDIATE, weeklySsuLimit = 0.5)).value
        assertTrue(out.session.exercises.isEmpty())
        assertEquals(1, out.corrections)
    }

    @Test fun `TC-LOAD-005f conditioning is shortened just enough, and dropped when that would take more than 40 percent`() {
        // 20 min of Z2 on the rower: 20 × 0.8 × 0.9 = 14.4 SSU.
        val s = Session(Tier.FULL, emptyList(), listOf(ConditioningBlock(Modality.ROWER, Zone.Z2, 20.0)))
        val shorter = SessionValidator.validate(s, ValidationContext(Level.INTERMEDIATE, weeklySsuLimit = 12.0)).value.session.conditioning.single()
        assertEquals(16.5, shorter.workMinutes, 1e-9)
        val gone = SessionValidator.validate(s, ValidationContext(Level.INTERMEDIATE, weeklySsuLimit = 5.0)).value.session
        assertTrue(gone.conditioning.isEmpty())
    }

    @Test fun `TC-VOL-007g sets cut before conditioning that then had to go are given back when they fit`() {
        // Two accessories of 2 sets at RIR 2 (4 × 0.8 = 3.2 SSU) and 20 min of Z2 on the rower (14.4 SSU), against 6 SSU left.
        val s = Session(Tier.FULL, listOf(SessionExercise(ex("row", Muscle.LATS), 2, 10, 2.0), SessionExercise(ex("fly", Muscle.CHEST), 2, 12, 2.0)),
            listOf(ConditioningBlock(Modality.ROWER, Zone.Z2, 20.0)))
        val r = SessionValidator.validate(s, ValidationContext(Level.INTERMEDIATE, weeklySsuLimit = 6.0))
        val out = r.value.session
        // The conditioning would lose more than 40%, so it goes; the accessory sets cut before it all fit again (not an empty day).
        assertTrue(out.conditioning.isEmpty())
        assertEquals(listOf("row" to 2, "fly" to 2), out.exercises.map { it.exercise.id to it.sets })
        assertTrue(SessionValidator.sessionSsu(out) <= 6.0 + 1e-9)
        assertEquals(4, r.decisions.last().outputs["setsGivenBack"])
        // Only what fits: with 2 SSU left, two sets come back, the first exercise first.
        val tight = SessionValidator.validate(s, ValidationContext(Level.INTERMEDIATE, weeklySsuLimit = 2.0)).value.session
        assertEquals(listOf("row" to 2), tight.exercises.map { it.exercise.id to it.sets })
        // Never more than planned, and nothing given back when nothing was cut for the week.
        val open = SessionValidator.validate(s, ValidationContext(Level.INTERMEDIATE, weeklySsuLimit = 40.0))
        assertEquals(s.exercises, open.value.session.exercises)
        assertTrue(open.decisions.last().outputs["setsGivenBack"] == null)
    }

    @Test fun `TC-LOAD-005i an interval block is shortened by whole repeats, so what is shown is what was approved`() {
        // Rower Z4, 4 x 240 s work with 120 s rest: 16 min of work.
        val block = ConditioningBlock(Modality.ROWER, Zone.Z4, 16.0, 8.0, hiit = true, protocol = HiitProtocol.LONG, repSec = 240)
        val full = SessionValidator.sessionSsu(Session(Tier.FULL, emptyList(), listOf(block)))
        var shortened = 0
        for (limit in listOf(full - 1.0, full - 4.0, full * 0.62)) {
            val out = SessionValidator.validate(Session(Tier.FULL, emptyList(), listOf(block)),
                ValidationContext(Level.ADVANCED, weeklySsuLimit = limit, hiitBaseReady = true, hiitAllowedThisWeek = 2)).value.session
            val b = out.conditioning.singleOrNull() ?: continue
            val reps = b.workMinutes * 60 / 240
            assertEquals("limit $limit: ${b.workMinutes} min is whole repeats", Math.round(reps).toDouble(), reps, 1e-9)
            assertTrue("limit $limit: within it", SessionValidator.sessionSsu(out) <= limit + 1e-9)
            if (b.workMinutes < 16.0) shortened++
        }
        assertTrue("an interval block was shortened ($shortened)", shortened >= 1)
    }

    @Test fun `TC-LOAD-005g a day with no weekly budget left is a recovery day that says why`() {
        val program = Blueprint.plan(listOf(Goal.STRENGTH, Goal.CARDIO)).value
        val day = WeekPlanner.plan(weekInput(4, 16, program = program), program).value.days.first { it.heavyLower }
        val req = GenerationRequest(day, Level.INTERMEDIATE, 40, 60, FULL_GYM, Tier.FULL, age = 35,
            e1rm = mapOf("back-squat" to 120.0, "bench-press" to 90.0, "romanian-deadlift" to 130.0),
            week = ValidationContext(Level.INTERMEDIATE, hiitBaseReady = true, weeklySsuLimit = 60.0, weekSsuSoFar = 60.0))
        val r = SessionGenerator.generate(req)
        assertTrue(r.value.items.isEmpty() && r.value.conditioning.isEmpty())
        assertEquals(Tier.RECOVERY, r.value.tier)
        val why = r.decisions.single { it.reason == ReasonKey.PLAN_CAPPED_BY_LOAD }
        assertEquals(listOf(RuleIds.VOL_007), why.ruleIds)
        assertEquals(true, why.outputs["recoveryDay"])
        // A recovery day has no bone-loading block either (osteoporosis; review R8-03).
        val ost = Conditions.resolve(listOf(UserCondition("osteoporosis"))).value
        val bone = SessionGenerator.generate(req.copy(conditions = ost)).value
        assertTrue(bone.items.isEmpty() && bone.boneLoading == null && bone.validated.boneLoading == null)
        // With room left, the same day trains normally and says nothing about the limit.
        val open = SessionGenerator.generate(req.copy(week = req.week.copy(weekSsuSoFar = 0.0)))
        assertTrue(open.value.items.isNotEmpty())
        assertTrue(open.decisions.none { it.reason == ReasonKey.PLAN_CAPPED_BY_LOAD })
    }
}
