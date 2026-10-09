package com.personalfitnesscoach.data.core.steps

import com.personalfitnesscoach.data.core.Fixtures
import com.personalfitnesscoach.data.core.model.StepsRecord
import com.personalfitnesscoach.data.core.model.StepCounterRecord
import com.personalfitnesscoach.data.core.model.dayKey
import com.personalfitnesscoach.data.core.time.Days
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** D-062: the phone's step counter is a running total since restart, read when the app opens; days get their share of each difference. */
class StepLedgerTest {
    private val utc = ZoneId.of("UTC")
    private val day = Fixtures.MONDAY
    private fun at(d: Int, hour: Double) = Days.startMs(d, utc) + (hour * 3_600_000).toLong()
    /** A reading at day/hour on a phone booted at `bootMs`. */
    private fun reading(d: Int, hour: Double, counter: Long, bootMs: Long = at(day - 10, 0.0)) = at(d, hour).let { StepCounterRecord(it, it - bootMs, counter) }

    @Test fun `the first reading only sets the starting point`() {
        assertTrue(StepLedger.split(null, reading(day, 8.0, 5000), utc).isEmpty())
    }

    @Test fun `a difference within one day goes to that day`() {
        assertEquals(listOf(day to 3000), StepLedger.split(reading(day, 8.0, 5000), reading(day, 20.0, 8000), utc))
    }

    @Test fun `a difference across midnight is shared by time`() {
        // 22:00 → 04:00 next day: 2 h before midnight, 4 h after.
        val r = StepLedger.split(reading(day, 22.0, 1000), reading(day + 1, 4.0, 1600), utc)
        assertEquals(listOf(day to 200, day + 1 to 400), r)
        assertEquals(600, r.sumOf { it.second })
    }

    @Test fun `after a restart the counter starts from zero at boot`() {
        val boot = at(day, 12.0)
        val r = StepLedger.split(reading(day, 8.0, 50_000), reading(day, 18.0, 2_000, bootMs = boot), utc)
        assertEquals(listOf(day to 2000), r) // steps between 08:00 and the 12:00 restart are unknown
    }

    @Test fun `a glitch is capped at a running cadence`() {
        val r = StepLedger.split(reading(day, 8.0, 0), reading(day, 8.5, 1_000_000), utc)
        assertEquals(listOf(day to 30 * StepLedger.MAX_STEPS_PER_MINUTE), r)
    }

    @Test fun `time zones decide the day`() {
        val ist = ZoneId.of("Asia/Kolkata") // 23:30 UTC on Monday is 05:00 Tuesday in India
        val prev = StepCounterRecord(at(day, 23.0), 1_000_000, 100)
        val now = StepCounterRecord(at(day, 23.5), 1_000_000 + 1_800_000, 400)
        assertEquals(listOf(day + 1 to 300), StepLedger.split(prev, now, ist))
    }

    @Test fun `readings add to daily totals only while tracking is on`() = runBlocking {
        val (d, clock) = Fixtures.data()
        d.recordStepReading(at(day, 8.0), 1000, 100)
        assertNull(d.docs.get(StepCounterRecord))
        d.setStepTracking(true)
        d.recordStepReading(at(day, 8.0), 1000, 100)
        d.recordStepReading(at(day, 12.0), 1000 + 4 * 3_600_000L, 4100)
        d.recordStepReading(at(day, 20.0), 1000 + 12 * 3_600_000L, 9100)
        assertEquals(9000, d.docs.get(StepsRecord, dayKey(day))!!.steps)
        d.setStepTracking(false)
        assertNull("turning tracking off forgets the reading", d.docs.get(StepCounterRecord))
        d.recordStepReading(at(day, 21.0), 1000 + 13 * 3_600_000L, 9900)
        assertEquals(9000, d.docs.get(StepsRecord, dayKey(day))!!.steps)
        clock.advanceDays(0)
    }
}
