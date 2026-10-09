package com.personalfitnesscoach.data.core

import com.personalfitnesscoach.data.core.model.ConditionsRecord
import com.personalfitnesscoach.data.core.model.EquipmentRecord
import com.personalfitnesscoach.data.core.model.PreferencesRecord
import com.personalfitnesscoach.data.core.model.Profile
import com.personalfitnesscoach.data.core.model.ScreeningRecord
import com.personalfitnesscoach.data.core.model.StoredCondition
import com.personalfitnesscoach.data.core.store.InMemoryRowStore
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.data.core.time.FixedClock
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.program.Goal
import com.personalfitnesscoach.engine.safety.ScreeningAnswers
import java.time.LocalDate
import java.time.ZoneId

/** Shared test set-up: a data layer over an in-memory store with a clock the test moves. */
object Fixtures {
    /** Monday 5 October 2026. */
    val MONDAY: Int = Days.of(LocalDate.of(2026, 10, 5))

    val FULL_GYM: Set<String> = Library.all.flatMap { it.allEquipment }.toSet() + setOf("rower", "stationary_bike", "treadmill")

    val CLEAR = ScreeningAnswers(heartOrBloodPressure = false, metabolicRenalPulmonary = false, symptoms = false, palpitations = false,
        limitOrPregnancy = false, musculoskeletal = false, longTermMedication = false, regularlyActive = true)

    fun data(day: Int = MONDAY, zone: ZoneId = ZoneId.of("UTC")): Pair<PfcData, FixedClock> {
        val clock = FixedClock(0, zone)
        clock.setDay(day)
        return PfcData(InMemoryRowStore(), clock, "test") to clock
    }

    /** Onboarding as the screens will do it (Part 5): profile, screening, conditions, equipment, preferences, then the programme. */
    suspend fun onboard(
        d: PfcData,
        level: Level = Level.INTERMEDIATE,
        birthYear: Int = 1980,
        days: Int = 3,
        minutes: Int = 60,
        priorities: List<Goal> = listOf(Goal.STRENGTH, Goal.MUSCLE, Goal.GENERAL_FITNESS),
        gym: Set<String> = FULL_GYM,
        conditions: List<StoredCondition> = emptyList(),
        screening: ScreeningAnswers = CLEAR,
    ) {
        val today = d.clock.today()
        d.docs.put(Profile, Profile(birthYear = birthYear, level = level, daysPerWeek = days, sessionMinutes = minutes, priorities = priorities, createdDay = today))
        d.docs.put(ScreeningRecord, ScreeningRecord(screening, today))
        d.docs.put(ConditionsRecord, ConditionsRecord(conditions, today))
        d.docs.put(EquipmentRecord, EquipmentRecord(gym))
        d.docs.put(PreferencesRecord, PreferencesRecord())
        d.startProgramIfNeeded()
    }
}
