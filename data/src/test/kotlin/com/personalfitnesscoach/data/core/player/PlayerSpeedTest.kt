package com.personalfitnesscoach.data.core.player

import com.personalfitnesscoach.data.core.Fixtures
import com.personalfitnesscoach.data.core.model.ExerciseState
import com.personalfitnesscoach.data.core.session.SetKind
import com.personalfitnesscoach.data.core.session.Stage
import com.personalfitnesscoach.engine.calc.CheckIn
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.Tier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2 acceptance timings measured through the data layer on the test machine (the engine work and the storage logic; the phone's
 * own database adds its write time, checked in UAT): F6 / NFR-03 a logged set is saved in under 100 ms; F8 replacement options come
 * back in under 300 ms.
 */
class PlayerSpeedTest {
    private fun stats(ms: List<Double>): Triple<Double, Double, Double> {
        val s = ms.sorted()
        return Triple(s[s.size / 2], s[minOf(s.size - 1, (s.size * 95) / 100)], s.last())
    }

    @Test fun `F6 and F8 logging a set and finding replacements are quick`() = runBlocking {
        val (d, _) = Fixtures.data()
        Fixtures.onboard(d)
        for (ex in Library.all) d.docs.put(ExerciseState, ExerciseState(ex.id, e1rm = 100.0, e1rmDay = Fixtures.MONDAY - 3, lastDoneDay = Fixtures.MONDAY - 3, exposures = 6))
        val t = d.planToday()!!
        d.checkIns.record(t.user, CheckIn(3, 3, 3, 3))
        d.startSession(d.generateSession(t, t.next ?: t.week.days.first(), Tier.FULL, 60))
        val p = SessionPlayer(d)
        p.completeStage(Stage.WARMUP)
        val swaps = ArrayList<Double>()
        val logs = ArrayList<Double>()
        var guard = 0
        while (guard++ < 200) {
            val step = p.view()!!.step as? Step.Lift ?: break
            val lift = step.lift
            if (lift.next?.kind == SetKind.WARMUP) { p.skipRamp(lift.rowId); continue }
            if (lift.workingDone == 0) {
                val s0 = System.nanoTime()
                p.swapOptions(lift.rowId)
                swaps += (System.nanoTime() - s0) / 1e6
            }
            val n = lift.next!!
            val entry = if (n.unit == DoseUnit.SECONDS) LiftEntry(n.load, null, seconds = n.reps.last, rir = n.targetRir ?: 2.0)
                else LiftEntry(n.load, n.reps.last, rir = n.targetRir ?: 2.0)
            val s1 = System.nanoTime()
            p.log(lift.rowId, entry)
            logs += (System.nanoTime() - s1) / 1e6
        }
        assertTrue("sets were logged (${logs.size})", logs.size >= 8)
        // The first call of each kind warms the JIT, as on a phone after the first set.
        val (lm, l95, lmax) = stats(logs.drop(1))
        val (sm, s95, smax) = stats(swaps.drop(1).ifEmpty { swaps })
        println("METRIC set logging, %d sets: median %.1f ms, 95th percentile %.1f ms, slowest %.1f ms".format(logs.size - 1, lm, l95, lmax))
        println("METRIC replacement options, %d exercises: median %.1f ms, 95th percentile %.1f ms, slowest %.1f ms".format(swaps.size, sm, s95, smax))
        assertTrue("set logging median %.1f ms".format(lm), lm < 100.0)
        assertTrue("replacement options 95th percentile %.1f ms".format(s95), s95 < 300.0)
    }
}
