package com.personalfitnesscoach.engine.generation

import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.program.Blueprint
import com.personalfitnesscoach.engine.program.FULL_GYM
import com.personalfitnesscoach.engine.program.Goal
import com.personalfitnesscoach.engine.program.WeekPlanner
import com.personalfitnesscoach.engine.program.weekInput
import com.personalfitnesscoach.engine.safety.ValidationContext
import org.junit.Assert.assertTrue
import org.junit.Test

/** NFR-02 (Phase 2): a workout, safety validator included, is generated in under 500 ms. Measured on the test machine. */
class GenerationSpeedTest {
    @Test fun `NFR-02 a session is generated and validated in well under 500 ms`() {
        val program = Blueprint.plan(listOf(Goal.STRENGTH, Goal.MUSCLE, Goal.CARDIO)).value
        val requests = (2..6).flatMap { days -> listOf(3, 9, 16, 30, 44).flatMap { week ->
            WeekPlanner.plan(weekInput(days, week, program = program), program).value.days } }
            .flatMap { d -> Tier.entries.filter { it != Tier.RECOVERY }.map { tier ->
                GenerationRequest(d, Level.INTERMEDIATE, 40, 60, FULL_GYM, tier, age = 45,
                    e1rm = mapOf("back-squat" to 120.0, "bench-press" to 90.0, "romanian-deadlift" to 130.0),
                    week = ValidationContext(Level.INTERMEDIATE, hiitBaseReady = true)) } }
        requests.take(20).forEach { SessionGenerator.generate(it) }   // warm up the JIT, as a running app is
        val ms = requests.map { r ->
            val start = System.nanoTime()
            SessionGenerator.generate(r)
            (System.nanoTime() - start) / 1e6
        }.sorted()
        val median = ms[ms.size / 2]
        val p95 = ms[(ms.size * 95) / 100]
        println("METRIC workout generation (validator included), %d sessions: median %.1f ms, 95th percentile %.1f ms, slowest %.1f ms"
            .format(ms.size, median, p95, ms.last()))
        assertTrue("95th percentile %.1f ms".format(p95), p95 < 500.0)
    }
}
