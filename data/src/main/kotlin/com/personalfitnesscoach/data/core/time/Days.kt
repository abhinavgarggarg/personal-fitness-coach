package com.personalfitnesscoach.data.core.time

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Training dates (Phase 2 data rule 5): a day is the local calendar date as an epoch day; an instant is UTC milliseconds.
 * A session belongs to the date it was started on in the phone's time zone at that moment, so travel never moves it.
 */
object Days {
    fun of(date: LocalDate): Int = date.toEpochDay().toInt()
    fun date(day: Int): LocalDate = LocalDate.ofEpochDay(day.toLong())
    fun fromInstant(ms: Long, zone: ZoneId): Int = of(Instant.ofEpochMilli(ms).atZone(zone).toLocalDate())

    /** 0 = Monday … 6 = Sunday, the engine's weekday numbering. */
    fun weekday(day: Int): Int = date(day).dayOfWeek.value - 1

    /** The Monday of the week that contains `day`. */
    fun weekStart(day: Int): Int = day - weekday(day)

    fun year(day: Int): Int = date(day).year

    /** Milliseconds at local midnight starting `day`. */
    fun startMs(day: Int, zone: ZoneId): Long = date(day).atStartOfDay(zone).toInstant().toEpochMilli()

    val MONDAY: DayOfWeek = DayOfWeek.MONDAY
}

/** The app's clock: everything that needs "now" or "today" asks this, so tests can fix time. */
interface AppClock {
    fun nowMs(): Long
    fun zone(): ZoneId
    fun today(): Int = Days.fromInstant(nowMs(), zone())
}

class SystemClock : AppClock {
    override fun nowMs(): Long = System.currentTimeMillis()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

/** A clock tests move by hand. */
class FixedClock(var ms: Long, private val tz: ZoneId = ZoneId.of("UTC")) : AppClock {
    override fun nowMs(): Long = ms
    override fun zone(): ZoneId = tz
    fun setDay(day: Int, hour: Int = 9) { ms = Days.startMs(day, tz) + hour * 3_600_000L }
    fun advanceDays(n: Int) { ms += n * 86_400_000L }
}
