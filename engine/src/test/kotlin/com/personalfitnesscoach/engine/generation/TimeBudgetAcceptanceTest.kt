package com.personalfitnesscoach.engine.generation

import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.program.Blueprint
import com.personalfitnesscoach.engine.program.FULL_GYM
import com.personalfitnesscoach.engine.program.Goal
import com.personalfitnesscoach.engine.program.PlannedDay
import com.personalfitnesscoach.engine.program.WeekPlanner
import com.personalfitnesscoach.engine.program.weekInput
import com.personalfitnesscoach.engine.safety.ValidationContext
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F10 acceptance (Phase 2 section 19) over the planned strength days of 2 to 5 days a week: for 30, 40, 60, 75 and 90 minutes the
 * session never runs more than 10% over the budget; a shortened session uses at least 90% of it; the warm-up and the primary lift
 * stay; what was cut is listed. When even the most shortened session is more than 10% over 30 minutes, the engine says so
 * (TIME_NOT_FITTABLE) and the express session fits instead (the app then switches to it). A day that needs less time than given ends
 * early: extra time is not filled (TIME-003 extension is not used yet; open issue for Phase 4).
 */
class TimeBudgetAcceptanceTest {
    private val program = Blueprint.plan(listOf(Goal.STRENGTH, Goal.MUSCLE, Goal.GENERAL_FITNESS)).value
    private val days = (2..5).flatMap { n -> listOf(3, 16, 30).flatMap { week -> WeekPlanner.plan(weekInput(n, week, program = program), program).value.days } }
        .filter { it.template.strength }

    private fun request(d: PlannedDay, minutes: Int) =
        GenerationRequest(d, Level.INTERMEDIATE, 40, minutes, FULL_GYM, Tier.FULL, age = 40,
            e1rm = mapOf("back-squat" to 120.0, "bench-press" to 90.0, "romanian-deadlift" to 130.0),
            week = ValidationContext(Level.INTERMEDIATE, hiitBaseReady = true))

    @Test fun `F10 each time budget is met within 10 percent, with the warm-up and the primary lift kept`() {
        var shortened = 0
        var express = 0
        var early = 0
        for (d in days) for (budget in listOf(30, 40, 60, 75, 90)) {
            val g = SessionGenerator.generate(request(d, budget))
            val w = g.value
            val where = "${d.template} ${d.slots.size} slots, $budget min → ${w.plannedMinutes} min"
            val compressed = g.decisions.firstOrNull { it.reason == ReasonKey.TIME_COMPRESSED }
            val notFittable = g.decisions.any { it.reason == ReasonKey.TIME_NOT_FITTABLE }
            assertTrue("$where: the warm-up is kept", w.warmupMinutes > 0.0)
            d.slots.firstOrNull { it.main }?.exercise?.pattern?.let { main ->
                assertTrue("$where: the primary lift's pattern is kept", w.items.any { it.exercise.pattern == main })
            }
            if (notFittable) {
                assertTrue("$where: only a 30-minute budget is out of reach", budget <= 30)
                val e = SessionGenerator.express(request(d, budget)).value
                assertTrue("$where: the express session fits instead (${e.plannedMinutes} min)", e.plannedMinutes <= budget * 1.10 && e.items.isNotEmpty())
                express++
                continue
            }
            assertTrue("$where: never more than 10% over", w.plannedMinutes <= budget * 1.10 + 1e-9)
            if (compressed != null) {
                shortened++
                assertTrue("$where: a shortened session uses at least 90% of the time", w.plannedMinutes >= budget * 0.90 - 1e-9)
                assertTrue("$where: what was cut is listed", (compressed.outputs["steps"] as List<*>).isNotEmpty())
            } else if (w.plannedMinutes < budget * 0.90) early++
        }
        println("METRIC F10 time budgets over ${days.size} days x 5 budgets: $shortened shortened within 10%, $express at 30 min " +
            "switched to the express session, $early ended early (needing less than the time given)")
    }
}
