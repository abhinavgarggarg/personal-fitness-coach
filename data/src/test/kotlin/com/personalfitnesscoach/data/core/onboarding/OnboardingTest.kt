package com.personalfitnesscoach.data.core.onboarding

import com.personalfitnesscoach.data.core.Fixtures
import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.model.KnownNumber
import com.personalfitnesscoach.data.core.model.Profile
import com.personalfitnesscoach.data.core.model.SettingsRecord
import com.personalfitnesscoach.data.core.model.StoredCondition
import com.personalfitnesscoach.data.core.model.WeightRecord
import com.personalfitnesscoach.engine.calc.Inventory
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.program.ExperienceAnswers
import com.personalfitnesscoach.engine.program.Goal
import com.personalfitnesscoach.engine.safety.ScreeningMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** FS-1 onboarding (Phase 2 O1–O9 with Research Update 1.1). */
class OnboardingTest {
    private val experienced = ExperienceAnswers(30, setOf("squat", "hinge", "press", "row"))

    private suspend fun through(o: Onboarding, d: PfcData, birthYear: Int = 1985, conditions: List<StoredCondition> = emptyList()) {
        o.acceptWelcome()
        o.screening(Fixtures.CLEAR)
        o.aboutYou(birthYear, experienced, bodyweightKg = 82.0)
        o.conditions(conditions)
        o.goal(listOf(o.goalOptions().value.default))
        o.schedule(3, setOf(0, 2, 4, 5), setOf(0, 2, 4), 60)
        o.equipment(Fixtures.FULL_GYM, Inventory())
        o.limitations(setOf(Joint.SHOULDER))
        o.numbers(listOf(KnownNumber("goblet-squat", 20.0, 10, 2.0, false, d.clock.today() - 3, d.clock.today())))
        o.planSeen()
    }

    @Test fun `each answer is saved as it is given, so an interrupted onboarding resumes at the same step`() = runBlocking {
        val (d, clock) = Fixtures.data()
        val o = Onboarding(d)
        assertEquals(OnboardingStep.WELCOME, o.step())
        o.acceptWelcome()
        val r = o.screening(Fixtures.CLEAR)
        assertEquals(ScreeningMode.STANDARD, r.mode)
        // "Process death": a new data layer over the same store.
        val o2 = Onboarding(PfcData(d.store, clock, "test"))
        assertEquals(OnboardingStep.ABOUT_YOU, o2.step())
        assertNull("nothing is planned before onboarding ends", d.planToday())
    }

    @Test fun `the whole flow ends with a programme, today's plan and the answers stored`() = runBlocking {
        val (d, _) = Fixtures.data()
        val o = Onboarding(d)
        through(o, d)
        assertEquals(OnboardingStep.ALERTS, o.step())
        o.finish()
        assertNull(o.step())
        val p = d.docs.get(Profile)!!
        assertEquals(Level.INTERMEDIATE, p.level)
        assertEquals(listOf(Goal.FAT_LOSS), p.priorities) // FL-001: adults 30+ default to fat loss
        assertEquals(setOf(Joint.SHOULDER), p.priorInjuries.keys)
        assertEquals(82.0, d.docs.all(WeightRecord).single().kg, 1e-9)
        assertNotNull(d.planToday())
    }

    @Test fun `the app is for adults - under 18 and impossible birth years are refused`() = runBlocking {
        val (d, _) = Fixtures.data()
        val o = Onboarding(d)
        o.acceptWelcome(); o.screening(Fixtures.CLEAR)
        for ((year, problem) in listOf(2012 to OnboardingProblem.UNDER_18, 1890 to OnboardingProblem.BIRTH_YEAR_IMPLAUSIBLE, 2030 to OnboardingProblem.BIRTH_YEAR_IMPLAUSIBLE)) {
            try { o.aboutYou(year, experienced); fail("$year accepted") } catch (e: OnboardingException) { assertEquals(problem, e.problem) }
        }
        assertEquals(OnboardingStep.ABOUT_YOU, o.step())
    }

    @Test fun `FL-001 fat loss is not offered in pregnancy and weight features are switched off`() = runBlocking {
        val (d, _) = Fixtures.data()
        val o = Onboarding(d)
        o.acceptWelcome(); o.screening(Fixtures.CLEAR); o.aboutYou(1990, experienced)
        o.conditions(listOf(StoredCondition("pregnancy", d.clock.today(), pregnancyWeek = 14, pregnancyWeekDay = d.clock.today())))
        val opts = o.goalOptions().value
        assertFalse(Goal.FAT_LOSS in opts.offered)
        assertEquals(Goal.GENERAL_FITNESS, opts.default)
        try { o.goal(listOf(Goal.FAT_LOSS)); fail("fat loss accepted") } catch (e: OnboardingException) { assertEquals(OnboardingProblem.BLOCKED_GOAL, e.problem) }
        o.goal(listOf(Goal.GENERAL_FITNESS))
        assertTrue(d.docs.get(SettingsRecord)!!.weightFeaturesOff)
    }

    @Test fun `unknown conditions, empty schedules and missing goals are refused`() = runBlocking {
        val (d, _) = Fixtures.data()
        val o = Onboarding(d)
        o.acceptWelcome(); o.screening(Fixtures.CLEAR); o.aboutYou(1980, experienced)
        try { o.conditions(listOf(StoredCondition("nope", 0))); fail() } catch (e: OnboardingException) { assertEquals(OnboardingProblem.UNKNOWN_CONDITION, e.problem) }
        o.conditions(emptyList())
        try { o.goal(emptyList()); fail() } catch (e: OnboardingException) { assertEquals(OnboardingProblem.NO_PRIORITY, e.problem) }
        o.goal(listOf(Goal.STRENGTH))
        try { o.schedule(4, setOf(0, 1), emptySet(), 60); fail() } catch (e: OnboardingException) { assertEquals(OnboardingProblem.TOO_FEW_DAYS_AVAILABLE, e.problem) }
    }

    @Test fun `going back keeps the answers and finishing twice changes nothing`() = runBlocking {
        val (d, _) = Fixtures.data()
        val o = Onboarding(d)
        through(o, d)
        o.back(OnboardingStep.SCHEDULE)
        assertEquals(OnboardingStep.SCHEDULE, o.step())
        assertEquals(3, d.docs.get(Profile)!!.daysPerWeek)
        val p1 = o.finish()
        val p2 = o.finish()
        assertEquals(p1, p2)
        o.back(OnboardingStep.WELCOME) // a finished onboarding is never reopened
        assertNull(o.step())
    }
}
