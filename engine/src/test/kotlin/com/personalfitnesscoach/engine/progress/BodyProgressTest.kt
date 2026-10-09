package com.personalfitnesscoach.engine.progress

import com.personalfitnesscoach.engine.core.ReasonKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyProgressTest {
    /** Daily weights for `days` days ending on day 100, falling `perWeek` kg a week from `start`. */
    private fun series(start: Double, perWeek: Double, days: Int = 35) =
        (0 until days).map { k -> WeightEntry(100 - (days - 1) + k, start - perWeek * k / 7.0) }

    @Test fun `TC-FL-005a weight shows a 7-day mean and only the 4-week trend is judged`() {
        val e = series(90.0, 0.5)
        val t = BodyProgress.weightTrend(e, 100).value
        val last7 = e.filter { it.day in 94..100 }.map { it.kg }.average()
        assertEquals(last7, t.mean7!!, 0.01)
        assertEquals(-0.5, t.kgPerWeek!!, 0.01)
        assertFalse(t.rateCheckIn)
        // Less than 4 weeks of data: no trend yet; no readings in the last 7 days: no mean.
        assertNull(BodyProgress.weightTrend(series(90.0, 0.5, days = 20), 100).value.kgPerWeek)
        assertNull(BodyProgress.mean7(listOf(WeightEntry(80, 90.0)), 100))
        // Weekly weigh-ins work too (one reading a week).
        val weekly = (0..4).map { WeightEntry(100 - 7 * it, 88.0 + 0.4 * it) }
        assertEquals(-0.4, BodyProgress.weightTrend(weekly, 100).value.kgPerWeek!!, 1e-9)
    }

    @Test fun `TC-FL-005b a fast fall for 3 weeks gives a neutral check-in, and waist is averaged and due every 2-4 weeks`() {
        val fast = BodyProgress.weightTrend(series(95.0, 1.3), 100)
        assertTrue(fast.value.rateCheckIn)
        assertTrue(fast.decisions.any { it.reason == ReasonKey.WEIGHT_RATE_CHECK_IN })
        // Only 2 fast weeks (the week before was flat): no check-in.
        val twoWeeks = (0 until 35).map { k -> WeightEntry(66 + k, if (k < 21) 95.0 else 95.0 - 1.3 * (k - 20) / 7.0) }
        assertFalse(BodyProgress.weightTrend(twoWeeks, 100).value.rateCheckIn)
        // Exactly 1 kg a week is not "more than 1 kg".
        assertFalse(BodyProgress.weightTrend((0..3).map { WeightEntry(100 - 7 * it, 90.0 + it) }, 100).value.rateCheckIn)
        assertTrue(BodyProgress.weightTrend((0..3).map { WeightEntry(100 - 7 * it, 90.0 + 1.1 * it) }, 100).value.rateCheckIn)
        // Waist: three readings averaged; zero readings are ignored.
        assertEquals(92.3, BodyProgress.waist(WaistEntry(100, listOf(92.0, 92.5, 92.4))).value!!, 1e-9)
        assertEquals(92.0, BodyProgress.waist(WaistEntry(100, listOf(92.0, 0.0))).value!!, 1e-9)
        assertNull(BodyProgress.waist(WaistEntry(100, emptyList())).value)
        assertTrue(BodyProgress.waistDue(null, 100)); assertFalse(BodyProgress.waistDue(90, 100)); assertTrue(BodyProgress.waistDue(86, 100))
        assertFalse(BodyProgress.waistOverdue(75, 100)); assertTrue(BodyProgress.waistOverdue(70, 100))
        assertEquals(90, BodyProgress.referenceLineCm(ReferenceSex.MAN)); assertEquals(80, BodyProgress.referenceLineCm(ReferenceSex.WOMAN))
        assertNull(BodyProgress.referenceLineCm(null))
    }
}
