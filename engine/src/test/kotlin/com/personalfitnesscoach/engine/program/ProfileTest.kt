package com.personalfitnesscoach.engine.program

import com.personalfitnesscoach.engine.calc.Inventory
import com.personalfitnesscoach.engine.calc.PlateMath
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.EquipmentClass
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Muscle
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.planning.SubContext
import com.personalfitnesscoach.engine.planning.Substitution
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.safety.Session
import com.personalfitnesscoach.engine.safety.SessionExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun ex(id: String) = Library.require(id)
private fun se(id: String, sets: Int) = SessionExercise(ex(id), sets, 8, 2.0)
private val fullBody = Session(Tier.FULL, listOf(se("back-squat", 3), se("romanian-deadlift", 3), se("bench-press", 3), se("barbell-row", 3), se("overhead-press", 2)))

class FrequencyTest {
    @Test fun `TC-FREQ-001a 2 to 6 days, 3 when unknown`() {
        assertEquals(3, Frequency.trainingDays(null)); assertEquals(2, Frequency.trainingDays(1)); assertEquals(6, Frequency.trainingDays(7))
    }

    @Test fun `TC-FREQ-001b always at least one full rest day`() {
        for (d in 0..10) assertTrue(7 - Frequency.trainingDays(d) >= P.FREQ_001.min_rest_days)
    }

    @Test fun `TC-FREQ-002a each major muscle gets 2 exposures of 2 or more fractional sets`() {
        assertTrue(Frequency.underExposed(listOf(fullBody, fullBody)).isEmpty())
    }

    @Test fun `TC-FREQ-002b one session a week under-exposes every major muscle`() {
        assertEquals(Frequency.MAJOR, Frequency.underExposed(listOf(fullBody)))
        // One set (1.0 fractional set) is below the 2-set exposure threshold.
        assertEquals(0, Frequency.exposures(listOf(Session(Tier.FULL, listOf(se("bench-press", 1))))).getValue(Muscle.CHEST))
    }

    @Test fun `TC-FREQ-003a resistance training on 2 or more days`() {
        assertTrue(Frequency.resistanceDaysOk(listOf(fullBody, fullBody)))
    }

    @Test fun `TC-FREQ-003b conditioning-only days do not count`() {
        assertFalse(Frequency.resistanceDaysOk(listOf(fullBody, Session(Tier.FULL, emptyList()))))
    }

    @Test fun `TC-FREQ-004a strength blocks load the main patterns 2-3 times a week`() {
        assertTrue(Frequency.strengthBlockIssues(listOf(fullBody, fullBody, fullBody)).isEmpty())
    }

    @Test fun `TC-FREQ-004b once or four times a week is flagged`() {
        assertEquals(setOf(Pattern.SQUAT, Pattern.HINGE, Pattern.HORIZONTAL_PUSH, Pattern.HORIZONTAL_PULL), Frequency.strengthBlockIssues(listOf(fullBody)).keys)
        assertEquals(4, Frequency.strengthBlockIssues(List(4) { fullBody }).getValue(Pattern.SQUAT))
    }
}

class ExperienceTest {
    private val all = setOf("squat", "hinge", "press", "row")

    @Test fun `TC-EXP-001a classification by months and comfort with the key lifts`() {
        assertEquals(Level.BEGINNER, Experience.classify(ExperienceAnswers(6, all)).value)
        assertEquals(Level.INTERMEDIATE, Experience.classify(ExperienceAnswers(24, all)).value)
        assertEquals(Level.ADVANCED, Experience.classify(ExperienceAnswers(48, all, structuredProgramming = true, confidentEffortRatings = true)).value)
        assertEquals(Level.INTERMEDIATE, Experience.classify(ExperienceAnswers(48, all)).value)
        assertEquals(Level.BEGINNER, Experience.classify(ExperienceAnswers(48, setOf("squat", "row"))).value)
    }

    @Test fun `TC-EXP-001b calibration may move the level down, never up, in the first 4 weeks`() {
        assertEquals(Level.BEGINNER, Experience.afterCalibration(Level.BEGINNER, Level.INTERMEDIATE, weeksSinceStart = 2))
        assertEquals(Level.BEGINNER, Experience.afterCalibration(Level.INTERMEDIATE, Level.BEGINNER, weeksSinceStart = 2))
        assertEquals(Level.INTERMEDIATE, Experience.afterCalibration(Level.BEGINNER, Level.INTERMEDIATE, weeksSinceStart = 6))
    }

    @Test fun `TC-EXP-002a beginners move up when progress stalls with good adherence, or after 9 months`() {
        fun r(stalled: Int, adh: Double, months: Int) = LevelReview(Level.BEGINNER, months, months, stalled, adh, 1.0)
        assertEquals(Level.INTERMEDIATE, Experience.review(r(2, 0.85, 5)).value.level)
        assertEquals(Level.BEGINNER, Experience.review(r(2, 0.70, 5)).value.level)
        assertEquals(Level.BEGINNER, Experience.review(r(1, 0.90, 5)).value.level)
        assertEquals(Level.INTERMEDIATE, Experience.review(r(0, 0.5, 9)).value.level)
    }

    @Test fun `TC-EXP-002b advanced after 24 months of slow progress, a long break trains one level lower for 4 weeks`() {
        assertEquals(Level.ADVANCED, Experience.review(LevelReview(Level.INTERMEDIATE, 20, 30, 0, 0.9, 0.3)).value.level)
        assertEquals(Level.INTERMEDIATE, Experience.review(LevelReview(Level.INTERMEDIATE, 20, 30, 0, 0.9, 0.8)).value.level)
        val back = Experience.review(LevelReview(Level.ADVANCED, 40, 60, 0, 0.9, 0.1, breakWeeks = 10)).value
        assertEquals(Level.INTERMEDIATE, back.level); assertEquals(4, back.temporaryWeeks)
    }
}

class IndividualTest {
    @Test fun `TC-IND-001a calibration starts scale with bodyweight and never go below the lightest load`() {
        val inv = Inventory()
        val dl = ex("deadlift")
        assertEquals(22.5, Individual.calibrationStart(dl, 80.0, PlateMath.loadsFor(dl, inv))!!, 1e-9) // 0.30 × 80 = 24 → 22.5
        assertEquals(20.0, Individual.calibrationStart(dl, null, PlateMath.loadsFor(dl, inv))!!, 1e-9)
        assertEquals(20.0, Individual.calibrationStart(ex("overhead-press"), 60.0, PlateMath.loadsFor(ex("overhead-press"), inv))!!, 1e-9)
        val db = ex("db-bench-press")
        assertEquals(5.0, Individual.calibrationStart(db, 100.0, PlateMath.loadsFor(db, inv))!!, 1e-9)
    }

    @Test fun `TC-IND-001b prior injuries become limitation tags with 4 conservative weeks`() {
        val early = Individual.injuries(setOf(Joint.SHOULDER), weeksSinceStart = 2).value
        assertEquals(setOf("overhead", "shoulder_extension_load"), early.blockedTags)
        assertEquals(1, early.regions.single().maxStress); assertEquals(3, early.regions.single().minRir)
        val later = Individual.injuries(setOf(Joint.SHOULDER), weeksSinceStart = 4).value
        assertTrue(later.blockedTags.isEmpty() && later.regions.isEmpty())
        assertEquals(setOf(Joint.SHOULDER), later.sensitiveJoints)
        // The blocked tags really remove overhead pressing from swaps.
        val ctx = SubContext(Library.all.flatMap { it.allEquipment }.toSet(), Level.INTERMEDIATE, blockedTags = early.blockedTags)
        assertTrue(Substitution.options(ex("db-shoulder-press"), Library.all, ctx).value.ranked.none { "overhead" in it.exercise.limitationTags })
    }
}

class EquipmentRoleTest {
    private val classes = listOf(EquipmentClass.BARBELL, EquipmentClass.DUMBBELL, EquipmentClass.KETTLEBELL, EquipmentClass.CABLE, EquipmentClass.MACHINE, EquipmentClass.BODYWEIGHT)
    private fun sample(c: EquipmentClass) = Library.all.first { it.equipmentClass == c && it.failureSafe == (c == EquipmentClass.MACHINE || c == EquipmentClass.CABLE) }

    @Test fun `TC-EQ-001a no equipment class is best for every role`() {
        for (c in classes) {
            val e = sample(c)
            val worseSomewhere = EquipmentRole.entries.any { role ->
                classes.any { other -> EquipmentRoles.fit(sample(other), role, true) > EquipmentRoles.fit(e, role, true) }
            }
            assertTrue(c.name, worseSomewhere)
        }
    }

    @Test fun `TC-EQ-001b planned failure only on failure-safe kit, barbells for main lifts once technique is reliable`() {
        assertEquals(0.0, EquipmentRoles.fit(ex("back-squat"), EquipmentRole.PLANNED_FAILURE, true), 1e-9)
        assertEquals(1.0, EquipmentRoles.fit(ex("leg-press"), EquipmentRole.PLANNED_FAILURE, true), 1e-9)
        assertTrue(EquipmentRoles.fit(ex("back-squat"), EquipmentRole.MAIN_LIFT, true) > EquipmentRoles.fit(ex("goblet-squat"), EquipmentRole.MAIN_LIFT, true))
        assertTrue(EquipmentRoles.fit(ex("back-squat"), EquipmentRole.MAIN_LIFT, false) < EquipmentRoles.fit(ex("goblet-squat"), EquipmentRole.MAIN_LIFT, false))
    }

    @Test fun `TC-EQ-002a peak hours prefer dumbbells and never more than 2 stations at once`() {
        assertEquals(2, EquipmentRoles.maxSimultaneousStations)
        assertTrue(EquipmentRoles.fit(ex("db-curl"), EquipmentRole.CROWDED_ACCESSORY, true) > EquipmentRoles.fit(ex("cable-curl"), EquipmentRole.CROWDED_ACCESSORY, true))
    }

    @Test fun `TC-EQ-002b Occupied offers a swap or doing it later`() {
        val ctx = SubContext(setOf("cable", "cable_row", "dumbbells", "bench", "incline_bench", "row_machine"), Level.INTERMEDIATE)
        val r = Substitution.occupied(ex("seated-cable-row"), Library.all, ctx, exercisesStillToCome = 2).value
        assertTrue(r.canDoLater)
        assertEquals(Pattern.HORIZONTAL_PULL, r.swaps.autoPick!!.exercise.pattern)
        assertFalse(Substitution.occupied(ex("seated-cable-row"), Library.all, ctx, exercisesStillToCome = 0).value.canDoLater)
    }
}

class AdherenceTest {
    @Test fun `TC-ADH-001a a week counts at planned minus one sessions, minimum 2, or 1 when planning 2`() {
        assertEquals(1, Streak.threshold(2)); assertEquals(2, Streak.threshold(3)); assertEquals(3, Streak.threshold(4)); assertEquals(5, Streak.threshold(6))
        val h = listOf(WeekRecord(3, 3), WeekRecord(3, 2), WeekRecord(3, 1), WeekRecord(3, 3), WeekRecord(3, 0))
        val s = Streak.compute(h)
        assertEquals(0, s.current); assertEquals(3, s.best); assertEquals(3, s.weeksTrainedThisYear)
    }

    @Test fun `TC-ADH-001b one freeze per 4 weeks and totals never go backwards`() {
        val h = listOf(WeekRecord(3, 3), WeekRecord(3, 0), WeekRecord(3, 3), WeekRecord(3, 3), WeekRecord(3, 3), WeekRecord(3, 3), WeekRecord(3, 1))
        val s = Streak.compute(h)
        assertEquals(5, s.current) // both misses were frozen (5 weeks apart)
        var trained = 0
        for (i in 1..h.size) { val t = Streak.compute(h.take(i)).weeksTrainedThisYear; assertTrue(t >= trained); trained = t }
    }

    @Test fun `TC-ADH-002a milestones at 10, 25, 50 and 100 sessions`() {
        val a = Achievements.earned(AchievementInput(25, true, 1, 0, false))
        assertTrue(Achievement.SESSIONS_10 in a && Achievement.SESSIONS_25 in a && Achievement.SESSIONS_50 !in a)
        assertTrue(Achievement.FIRST_SESSION in a && Achievement.CALIBRATION_DONE in a && Achievement.PERSONAL_RECORD in a)
    }

    @Test fun `TC-ADH-002b nothing rewards extreme volume`() {
        assertTrue(Achievement.entries.none { n -> listOf("SET", "VOLUME", "MINUTE", "STREAK_DAYS").any { it in n.name } })
        assertTrue(Achievements.earned(AchievementInput(0, false, 0, 0, false)).isEmpty())
    }

    @Test fun `TC-ADH-004a a 20-30 minute express option and habit messaging around 66 days`() {
        assertEquals(20..30, Express.minutes); assertEquals(66, Express.habitMedianDays)
    }

    @Test fun `TC-LOAD-002a session RPE is asked at least 10 minutes after the last hard effort`() {
        assertEquals(SrpePrompt.Ask.WAIT, SrpePrompt.decide(5.0, false, 0.1).value)
        assertEquals(SrpePrompt.Ask.NOW, SrpePrompt.decide(12.0, false, 0.2).value)
    }

    @Test fun `TC-LOAD-002b one late prompt within 24 hours if skipped`() {
        assertEquals(SrpePrompt.Ask.LATE_PROMPT, SrpePrompt.decide(60.0, askedBefore = true, hoursSinceSession = 3.0).value)
        assertEquals(SrpePrompt.Ask.NEVER, SrpePrompt.decide(60.0, true, 25.0).value)
    }
}
