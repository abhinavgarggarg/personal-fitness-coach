package com.personalfitnesscoach.data.core.model

import com.personalfitnesscoach.data.core.json.DataFormatException
import com.personalfitnesscoach.data.core.json.Js
import com.personalfitnesscoach.data.core.repo.DataSchema
import com.personalfitnesscoach.data.core.session.ConditioningItem
import com.personalfitnesscoach.data.core.session.ItemDoc
import com.personalfitnesscoach.data.core.session.WorkoutDoc
import com.personalfitnesscoach.engine.calc.CheckIn
import com.personalfitnesscoach.engine.calc.Inventory
import com.personalfitnesscoach.engine.calc.Stack
import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.model.Muscle
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.model.Zone
import com.personalfitnesscoach.engine.planning.Priority
import com.personalfitnesscoach.engine.program.Blueprint
import com.personalfitnesscoach.engine.program.DayTemplate
import com.personalfitnesscoach.engine.program.ExperienceAnswers
import com.personalfitnesscoach.engine.program.Goal
import com.personalfitnesscoach.engine.program.SlotRole
import com.personalfitnesscoach.engine.progress.ReferenceSex
import com.personalfitnesscoach.engine.progression.Prescription
import com.personalfitnesscoach.engine.progression.ProgressionAction
import com.personalfitnesscoach.engine.safety.ClearanceScope
import com.personalfitnesscoach.engine.safety.ControlStatus
import com.personalfitnesscoach.engine.safety.HiitProtocol
import com.personalfitnesscoach.engine.safety.PainAction
import com.personalfitnesscoach.engine.safety.PainKind
import com.personalfitnesscoach.engine.safety.PainReport
import com.personalfitnesscoach.engine.safety.ScreeningAnswers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Every stored record type round-trips exactly, and unreadable data is refused rather than replaced by defaults. */
class CodecTest {
    /** One full sample per document type (every optional field set), plus a minimal one where defaults matter. */
    val samples: List<Pair<DocCodec<*>, Any>> = listOf(
        Profile to Profile(birthYear = 1979, level = Level.ADVANCED, experience = ExperienceAnswers(30, setOf("squat", "hinge"), true, false), daysPerWeek = 4,
            availableDays = setOf(0, 1, 3, 5), preferredDays = setOf(1, 3), sessionMinutes = 75, priorities = listOf(Goal.FAT_LOSS, Goal.STRENGTH),
            focusMuscles = setOf(Muscle.GLUTES), priorInjuries = setOf(Joint.KNEE, Joint.SHOULDER), referenceSex = ReferenceSex.WOMAN, restingHr = 58, maxHr = 178,
            onboardingStep = "equipment", createdDay = 20000),
        Profile to Profile(createdDay = 1),
        ScreeningRecord to ScreeningRecord(ScreeningAnswers(true, false, false, false, true, false, true, false), 20001, 20005),
        ConditionsRecord to ConditionsRecord(listOf(
            StoredCondition("hbp_controlled", 20000, ControlStatus.YES, setOf(ClearanceScope.VIGOROUS), 20002, emptySet()),
            StoredCondition("pregnancy", 20003, pregnancyWeek = 14, pregnancyWeekDay = 20003, attested = true, previouslyVigorous = true, supineUncomfortable = true),
            StoredCondition("heart", 20004, subFlags = setOf("pacemaker_icd"), painRuleMetWeeks = 2, blockIfYes = true, flare = true, impactOptIn = true,
                impactChecksPassed = true, alreadyDoingImpact = true, birthDay = 19990)), 20004),
        EquipmentRecord to EquipmentRecord(setOf("barbell", "dumbbells", "cable"), Inventory(barKg = 15.0, plates = mapOf(20.0 to 4, 1.25 to 2),
            dumbbells = listOf(2.0, 4.0, 22.5), kettlebells = listOf(12.0), stack = Stack(2.5, 90.0, 2.5), bars = mapOf("ez_bar" to 8.0),
            stacks = mapOf("leg_press" to Stack(10.0, 200.0, 10.0))), mapOf("cable" to 20010), setOf("dumbbells")),
        PreferencesRecord to PreferencesRecord(mapOf("goblet_squat" to 0.8), setOf("barbell_back_squat"), setOf("db_row"), mapOf(Modality.ROWER to 0.9),
            setOf(Modality.TREADMILL_RUN), hiitOptIn = true, circuitJumps = true, crowdedGym = true),
        SettingsRecord to SettingsRecord(Units.LB, Theme.DARK, true, 20000, 16, 12),
        ProgramRecord to ProgramRecord(listOf(Goal.MUSCLE), 20000, 20002, 5, 4, 2, mapOf("main_squat" to "goblet_squat"), mapOf("a" to "b"), mapOf("push_up" to "knee_push_up"),
            3, true, 2, true, 22.5, setOf(Pattern.LOADED_CARRY), Blueprint.ClockAction.PAUSE),
        WeekSummary to WeekSummary(20002, 4, 3, 2, 40.0, 30.0, 10.0, 1, 4.0, 60.0, 110.0, 120.5, 900.0, true, Blueprint.ClockAction.ADVANCE),
        WeekPlanRecord to WeekPlanRecord(20002, listOf(0 to DayTemplate.FB_A, 2 to DayTemplate.FB_B), true, listOf(4, 6)),
        ExerciseState to ExerciseState("goblet_squat", 61.3, 20010, Prescription(22.5, 8..12, 8, ProgressionAction.LOAD_UP, true), 20010,
            CalibrationState(20.0, 2, 30.0), 1, true, 25.0, 20010, 7, true),
        ExerciseState to ExerciseState("plank"),
        KnownNumber to KnownNumber("barbell_bench_press", 60.0, 8, 2.0, false, 19990, 20000),
        KnownNumber to KnownNumber("barbell_deadlift", 120.0, 3, null, true, 20000, 20000),
        DecisionEntry to DecisionEntry.from(Decision(DecisionKind.LOAD_CHANGE, listOf("PROG-001", "PROG-002"), ReasonKey.LOAD_FROM_PROGRESSION,
            mapOf("exercise" to "x", "list" to listOf(1, 2), "range" to 8..12, "none" to null), mapOf("load" to 22.5, "tier" to Tier.FULL)), 7L, 1234L, 20000, 5L, "1.1.1"),
        ReadinessRecord to ReadinessRecord(20000, CheckIn(3, 4, 2, 5, 6.5), 45, 55.0, 52.5, Tier.MODIFIED, Tier.LIGHT, setOf("F3_READINESS"), setOf("chest_pain"),
            setOf("fever"), 99L),
        PainRecord to PainRecord(5000L, 20000, 9L, "goblet_squat", PainReport(Joint.KNEE, PainKind.JOINT_OR_TENDON, 4, true, setOf("sharp"), true), Side.LEFT,
            PainAction.STOP_REGION, 20003),
        RecoveryRecord to RecoveryRecord(20000, illness = true, runDown = true, symptoms = setOf("fever"), note = "flu"),
        WeightRecord to WeightRecord(20000, 82.4),
        WaistRecord to WaistRecord(20000, listOf(90.5, 90.0, 91.0)),
        StepsRecord to StepsRecord(20000, 8432),
        StepStateRecord to StepStateRecord(20002, 6000, 6500, 1),
        StepCounterRecord to StepCounterRecord(123456789L, 98765L, 4242L),
        WalkRecord to WalkRecord(20000, 7 * 60 + 30, 25, brisk = false),
    )

    @Suppress("UNCHECKED_CAST")
    private fun <T> roundTrip(c: DocCodec<T>, v: Any) {
        val text = c.encode(v as T)
        val back = c.decode(text)
        assertEquals("${c.type} round trip", v, back)
        assertEquals("${c.type} encodes deterministically", text, c.encode(back))
        assertEquals(1, Js.parse(text)["v"].toString().toInt())
    }

    @Test fun `every document type round-trips exactly`() {
        for ((c, v) in samples) roundTrip(c, v)
        val covered = samples.map { it.first.type }.toSet()
        assertEquals("a sample for every stored type", DataSchema.documents.map { it.type }.toSet(), covered)
    }

    @Test fun `session documents round-trip`() {
        val w = WorkoutDoc(2, 58.5, 11.0, 5.0, listOf(ConditioningItem(Modality.ROWER, Zone.Z3, 8.0, 8.0, true, 1, HiitProtocol.SHORT, 6.0),
            ConditioningItem(Modality.TREADMILL_WALK, Zone.Z1, 15.0)), true, 6.0, 4.0, 18, true, true, true, 7.5)
        assertEquals(w, WorkoutDoc.decode(w.encode()))
        val i = ItemDoc(SlotRole.MAIN, Priority.P1, 3, 6..8, DoseUnit.REPS, true, 2.0, true, 62.5, 0.9, 120, 150, 180, true, true, "barbell_back_squat", true)
        assertEquals(i, ItemDoc.decode(i.encode()))
        val minimal = ItemDoc(SlotRole.CORE, Priority.P4, 2, 30..45, DoseUnit.SECONDS, targetRir = 3.0, restMinSec = 30, restDefaultSec = 45, restMaxSec = 60)
        assertEquals(minimal, ItemDoc.decode(minimal.encode()))
    }

    private fun refused(what: String, block: () -> Unit) {
        try { block() } catch (e: DataFormatException) { return }
        fail("expected DataFormatException: $what")
    }

    @Test fun `unreadable records are refused, never defaulted`() {
        refused("newer record version") { WeightRecord.decode("{\"v\":2,\"day\":1,\"kg\":80.0}") }
        refused("no version") { WeightRecord.decode("{\"day\":1,\"kg\":80.0}") }
        refused("missing field") { WeightRecord.decode("{\"v\":1,\"day\":1}") }
        refused("wrong type") { WeightRecord.decode("{\"v\":1,\"day\":\"monday\",\"kg\":80.0}") }
        refused("fraction for a whole number") { WeightRecord.decode("{\"v\":1,\"day\":1.5,\"kg\":80.0}") }
        refused("out of range value") { WeightRecord.decode("{\"v\":1,\"day\":1,\"kg\":5000.0}") }
        refused("unknown enum value") { Profile.decode("{\"v\":1,\"level\":\"GODLIKE\",\"sessionMinutes\":60,\"createdDay\":1}") }
        refused("engine validation (rating 1–5)") {
            ReadinessRecord.decode(ReadinessRecord.encode(samples.first { it.first === ReadinessRecord }.second as ReadinessRecord).replace("\"sleep\":3", "\"sleep\":9"))
        }
        refused("not JSON") { WeightRecord.decode("not json") }
        refused("screening answer missing") { ScreeningRecord.decode("{\"v\":1,\"answers\":{\"symptoms\":false},\"takenDay\":1}") }
        refused("workout doc newer") { WorkoutDoc.decode("{\"v\":9,\"weekday\":1,\"plannedMinutes\":1.0,\"warmupMinutes\":1.0}") }
    }

    @Test fun `optional fields left out stay small`() {
        val text = ExerciseState.encode(ExerciseState("plank"))
        assertEquals("{\"v\":1,\"exerciseId\":\"plank\",\"exposures\":0}", text)
        assertTrue(Profile.encode(Profile(createdDay = 1)).length < 200)
    }

    @Test fun `time-based condition facts are computed for the day asked`() {
        val c = StoredCondition("pregnancy", 100, pregnancyWeek = 12, pregnancyWeekDay = 100)
        assertEquals(12, c.toUserCondition(100, 0).pregnancyWeek)
        assertEquals(14, c.toUserCondition(114, 2).pregnancyWeek)
        assertEquals(2, c.toUserCondition(114, 2).weeks)
        val pp = StoredCondition("postpartum", 100, birthDay = 90)
        assertEquals(0, pp.toUserCondition(95, 0).weeksSinceBirth)
        assertEquals(6, pp.toUserCondition(132, 0).weeksSinceBirth)
    }
}
