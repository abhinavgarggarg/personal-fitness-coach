package com.personalfitnesscoach.data.core.review

import com.personalfitnesscoach.data.core.Fixtures
import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.backup.BackupException
import com.personalfitnesscoach.data.core.backup.BackupFormat
import com.personalfitnesscoach.data.core.backup.BackupProblem
import com.personalfitnesscoach.data.core.json.DataFormatException
import com.personalfitnesscoach.data.core.model.ConditionsRecord
import com.personalfitnesscoach.data.core.model.DecisionEntry
import com.personalfitnesscoach.data.core.model.ExerciseState
import com.personalfitnesscoach.data.core.model.PainRecord
import com.personalfitnesscoach.data.core.model.Profile
import com.personalfitnesscoach.data.core.model.ProgramRecord
import com.personalfitnesscoach.data.core.model.ReadinessRecord
import com.personalfitnesscoach.data.core.model.RecoveryRecord
import com.personalfitnesscoach.data.core.model.SafetyStopRecord
import com.personalfitnesscoach.data.core.model.ScreeningRecord
import com.personalfitnesscoach.data.core.model.StepCounterRecord
import com.personalfitnesscoach.data.core.model.StoredCondition
import com.personalfitnesscoach.data.core.model.WalkRecord
import com.personalfitnesscoach.data.core.model.WeekPlanRecord
import com.personalfitnesscoach.data.core.model.WeekSummary
import com.personalfitnesscoach.data.core.model.dayKey
import com.personalfitnesscoach.data.core.session.ConditioningItem
import com.personalfitnesscoach.data.core.session.ItemDoc
import com.personalfitnesscoach.data.core.session.WorkoutDoc
import com.personalfitnesscoach.data.core.steps.StepLedger
import com.personalfitnesscoach.data.core.store.ExerciseRow
import com.personalfitnesscoach.data.core.store.SetRow
import com.personalfitnesscoach.data.core.store.WorkoutRow
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.data.core.time.FixedClock
import com.personalfitnesscoach.engine.calc.CheckIn
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.model.Zone
import com.personalfitnesscoach.engine.planning.Priority
import com.personalfitnesscoach.engine.program.Blueprint
import com.personalfitnesscoach.engine.program.DayTemplate
import com.personalfitnesscoach.engine.program.Goal
import com.personalfitnesscoach.engine.program.SlotRole
import com.personalfitnesscoach.engine.program.WeekKind
import com.personalfitnesscoach.engine.progression.Prescription
import com.personalfitnesscoach.engine.progression.ProgressionAction
import com.personalfitnesscoach.engine.safety.HiitProtocol
import com.personalfitnesscoach.engine.safety.PainAction
import com.personalfitnesscoach.engine.safety.PainKind
import com.personalfitnesscoach.engine.safety.PainReport
import com.personalfitnesscoach.engine.safety.RedFlags
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.ZoneId

/** Regression tests for the Part 4 independent review (R4-01 … R4-32). */
class Part4ReviewRegressionTest {
    private val monday = Fixtures.MONDAY

    /** A finished workout written straight to storage (history set-up without generating). */
    private suspend fun done(d: PfcData, day: Int, template: DayTemplate = DayTemplate.FB_A, weekday: Int = Days.weekday(day),
                             lifts: List<Triple<String, Double, Int>> = emptyList(), conditioning: List<ConditioningItem> = emptyList(),
                             rpe: Double? = 6.0, minutes: Double? = 50.0): Long {
        val w = d.store.insertWorkout(WorkoutRow(day = day, status = "DONE", template = template.name, tier = "FULL", startedAtMs = Days.startMs(day, ZoneId.of("UTC")) + 3_600_000,
            endedAtMs = Days.startMs(day, ZoneId.of("UTC")) + 7_200_000, sessionRpe = rpe, actualMinutes = minutes, registryVersion = "1.1.1",
            json = WorkoutDoc(weekday, 60.0, 10.0, conditioning = conditioning).encode()))
        for ((i, l) in lifts.withIndex()) {
            val (id, load, reps) = l
            val item = ItemDoc(SlotRole.MAIN, Priority.P1, 3, 8..12, DoseUnit.REPS, targetRir = 2.0, load = load, restMinSec = 90, restDefaultSec = 120, restMaxSec = 180, main = true)
            val e = d.store.insertExercise(ExerciseRow(workoutId = w, position = i, exerciseId = id, slotKey = "main$i", status = "DONE", json = item.encode()))
            repeat(3) { s -> d.store.insertSet(SetRow(workoutExerciseId = e, setIndex = s, kind = "WORKING", loadKg = load, reps = reps, rir = 2.0, loggedAtMs = 1)) }
        }
        return w
    }

    private suspend fun ready(clockDay: Int = monday, conditions: List<StoredCondition> = emptyList(), priorities: List<Goal> = listOf(Goal.STRENGTH, Goal.MUSCLE),
                              days: Int = 3, birthYear: Int = 1985): Pair<PfcData, FixedClock> {
        val (d, clock) = Fixtures.data(clockDay)
        Fixtures.onboard(d, days = days, priorities = priorities, conditions = conditions, birthYear = birthYear)
        return d to clock
    }

    // ------------------------------------------------------------------------------------------------ R4-01 return to training
    @Test fun `R4-01 three weeks off scale loads and sets and pause HIIT (REG-004)`() = runBlocking {
        val (d, clock) = ready(monday - 70)
        done(d, monday - 21, lifts = listOf(Triple("bench-press", 80.0, 8)))
        d.docs.put(ExerciseState, ExerciseState("bench-press", e1rm = 100.0, prescription = Prescription(80.0, 8..12, 8, ProgressionAction.HOLD)))
        clock.setDay(monday)
        val u = d.bridge.user()!!
        val plan = d.bridge.returnPlan(u, d.docs.get(ProgramRecord)).value
        assertEquals(0.9, plan.loadFactor, 1e-9); assertEquals(0.7, plan.setsFactor, 1e-9); assertFalse(plan.hiitAllowed)
        val t = d.planToday()!!
        val day = t.next ?: t.week.days.first()
        val req = d.bridge.request(t.user, t.program, t.week, day, Tier.FULL)
        assertTrue("prescription scaled: ${req.progression["bench-press"]}", req.progression.getValue("bench-press").load <= 72.0 + 1e-9)
        assertEquals(90.0, req.e1rm.getValue("bench-press"), 1e-9)
        assertTrue(req.day.slots.zip(day.slots).all { (a, b) -> a.sets <= b.sets })
        assertFalse(req.week.hiitBaseReady)
    }

    @Test fun `R4-01 ten weeks off recalibrate, never above the old working load`() = runBlocking {
        val (d, clock) = ready(monday - 120)
        done(d, monday - 70, lifts = listOf(Triple("bench-press", 80.0, 8)))
        d.docs.put(ExerciseState, ExerciseState("bench-press", e1rm = 100.0, prescription = Prescription(80.0, 8..12, 8, ProgressionAction.HOLD)))
        clock.setDay(monday)
        val t = d.planToday()!!
        val req = d.bridge.request(t.user, t.program, t.week, t.next ?: t.week.days.first(), Tier.FULL)
        assertNull(req.e1rm["bench-press"]); assertNull(req.progression["bench-press"])
        assertTrue(req.calibrationCeilings.getValue("bench-press") <= 80.0)
    }

    @Test fun `R4-01 the day after an illness is rest or light (REG-005)`() = runBlocking {
        val (d, clock) = ready()
        d.docs.put(RecoveryRecord, RecoveryRecord(monday, illness = true))
        clock.setDay(monday + 1, hour = 9)
        val g = d.bridge.gate(d.bridge.user()!!, d.docs.get(ProgramRecord))
        assertEquals(Tier.RECOVERY, g.tierCap) // less than 24 h symptom-free
        clock.setDay(monday + 2, hour = 9)
        assertEquals(Tier.LIGHT, d.bridge.gate(d.bridge.user()!!, d.docs.get(ProgramRecord)).tierCap)
    }

    // ------------------------------------------------------------------------------------------------ R4-02 red-flag stop
    @Test fun `R4-02 a red-flag stop is kept until confirmed, then the first session is light`() = runBlocking {
        val (d, clock) = ready()
        val symptom = RedFlags.SYMPTOMS.first()
        val c1 = d.checkIns.record(d.bridge.user()!!, CheckIn(4, 4, 4, 4), redFlags = setOf(symptom))
        assertNotNull(c1.safetyStop); assertEquals(Tier.RECOVERY, c1.record.tier)
        val t = d.planToday()!!
        val w = d.generate(t, t.next ?: t.week.days.first(), Tier.FULL).value
        assertNotNull("generate honours today's stop", w.safetyStop)
        clock.setDay(monday + 1)
        val c2 = d.checkIns.record(d.bridge.user()!!, CheckIn(4, 4, 4, 4))
        assertNotNull("still stopped without confirmation", c2.safetyStop); assertEquals(Tier.RECOVERY, c2.record.tier)
        assertTrue(d.confirmStopResolved())
        val c3 = d.checkIns.record(d.bridge.user()!!, CheckIn(5, 5, 5, 5), requestedTier = Tier.FULL)
        assertNull(c3.safetyStop); assertEquals(Tier.LIGHT, c3.record.tier)
        done(d, monday + 1)
        clock.setDay(monday + 2)
        assertNull(d.bridge.gate(d.bridge.user()!!, d.docs.get(ProgramRecord)).tierCap)
        assertNotNull(d.docs.get(SafetyStopRecord)!!.confirmedDay)
    }

    // ------------------------------------------------------------------------------------------------ R4-03 / R4-06 deload
    @Test fun `R4-03 a deload chosen inside a block is planned and the clock waits for it`() = runBlocking {
        val (d, clock) = ready()
        var week = 0
        while (true) { // train every week until the calibration block is over
            clock.setDay(monday + 7 * week)
            d.planToday()
            if (d.bridge.context(d.docs.get(ProgramRecord)!!).kind == WeekKind.LOADING) break
            for (k in listOf(0, 2, 4)) done(d, monday + 7 * week + k)
            week++
            check(week < 8)
        }
        val ws = monday + 7 * week
        val p = d.docs.get(ProgramRecord)!!
        d.docs.put(ProgramRecord, p.copy(deloadThisWeek = true))
        val t = d.planToday()!!
        assertTrue("deload planned", t.week.deload)
        done(d, ws); done(d, ws + 2); done(d, ws + 4)
        clock.setDay(ws + 7)
        d.onAppOpen()
        val after = d.docs.get(ProgramRecord)!!
        assertEquals("the block clock did not advance through the inserted deload", p.clockWeek, after.clockWeek)
        assertTrue(d.docs.get(WeekSummary, dayKey(ws))!!.deload)
        assertTrue("R4-06: the finished week is the one just finished", after.justFinishedLighterWeek)
    }

    // ------------------------------------------------------------------------------------------------ R4-04 T2D across the week boundary
    @Test fun `R4-04 type 2 diabetes - no strength on Monday after Sunday strength, whichever planned day is opened`() = runBlocking {
        val (d, clock) = ready(monday - 7, conditions = listOf(StoredCondition("t2d", monday - 7)), priorities = listOf(Goal.FAT_LOSS), days = 4)
        done(d, monday - 1, DayTemplate.FB_A, weekday = 6, lifts = listOf(Triple("goblet-squat", 20.0, 10)))
        clock.setDay(monday)
        val t = d.planToday()!!
        val next = t.next
        assertTrue("Monday after Sunday strength: ${next?.template}", next == null || next.slots.isEmpty())
        // Re-check finding 4: opening a strength day directly is no way round it — no resistance exercise, and the final check agrees.
        for (day in t.week.days.filter { it.slots.isNotEmpty() }) {
            val w = d.generate(t, day, Tier.FULL).value
            assertTrue("${day.template}: ${w.items.map { it.exercise.id }}", w.items.none { it.sets > 0 })
            assertTrue(w.validated.exercises.isEmpty())
        }
        // Without a lift yesterday, the same day keeps its strength work.
        val (d2, clock2) = ready(monday - 7, conditions = listOf(StoredCondition("t2d", monday - 7)), priorities = listOf(Goal.FAT_LOSS), days = 4)
        done(d2, monday - 1, DayTemplate.COND, weekday = 6, conditioning = listOf(ConditioningItem(Modality.ROWER, Zone.Z1, 20.0, doneWorkMinutes = 20.0)))
        clock2.setDay(monday)
        val t2 = d2.planToday()!!
        val strengthDay = t2.week.days.first { it.slots.isNotEmpty() }
        assertTrue(d2.generate(t2, strengthDay, Tier.FULL).value.items.any { it.sets > 0 })
    }

    // ------------------------------------------------------------------------------------------------ R4-05 lighter week
    @Test fun `R4-05 a lighter week caps three sessions at MODIFIED`() = runBlocking {
        val (d, _) = ready()
        d.docs.put(ProgramRecord, d.docs.get(ProgramRecord)!!.copy(lighterSessionsLeft = 3, lighterThisWeek = true))
        val c = d.checkIns.record(d.bridge.user()!!, CheckIn(5, 5, 5, 5), requestedTier = Tier.FULL)
        assertEquals(Tier.MODIFIED, c.record.tier)
    }

    // ------------------------------------------------------------------------------------------------ R4-07 / R4-08 counting
    @Test fun `R4-07 impact work counts as an impact session, R4-08 HIIT counts when minutes were not logged`() = runBlocking {
        val (d, _) = ready()
        done(d, monday, conditioning = listOf(ConditioningItem(Modality.JUMP_ROPE, Zone.Z2, 10.0, impact = 1, doneWorkMinutes = 10.0)))
        done(d, monday, conditioning = listOf(ConditioningItem(Modality.ROWER, Zone.Z3, 8.0, 8.0, hiit = true, protocol = HiitProtocol.SHORT, doneWorkMinutes = null)))
        val t = d.bridge.totals(monday, monday)
        assertEquals(1, t.impactSessions)
        assertEquals(1, t.hiitSessions)
        assertEquals(8.0, t.hiitWorkMinutes, 1e-9)
        // Logged minutes never count above the plan.
        done(d, monday + 1, conditioning = listOf(ConditioningItem(Modality.ROWER, Zone.Z1, 20.0, doneWorkMinutes = 45.0)))
        assertEquals(20.0, d.bridge.totals(monday + 1, monday + 1).z1Minutes, 1e-9)
    }

    // ------------------------------------------------------------------------------------------------ R4-09 missed week
    @Test fun `R4-09 a missed week does not switch the week-over-week caps off`() = runBlocking {
        val (d, clock) = ready()
        done(d, monday, conditioning = listOf(ConditioningItem(Modality.ROWER, Zone.Z1, 20.0, doneWorkMinutes = 20.0)))
        clock.setDay(monday + 14)
        d.onAppOpen()
        val last = d.bridge.lastActiveWeek(monday + 14)
        assertEquals(monday, last!!.weekStartDay)
        val u = d.bridge.user()!!
        val input = d.bridge.weekInput(u, d.docs.get(ProgramRecord)!!)
        assertEquals(20.0, input.lastWeekAerobicMinutes, 1e-9)
    }

    // ------------------------------------------------------------------------------------------------ R4-10 heavy lower next week
    @Test fun `R4-10 Sunday counts Monday's heavy lower day`() = runBlocking {
        val (d, clock) = ready()
        clock.setDay(monday + 6)
        val t = d.planToday()!!
        val days = d.bridge.daysToNextHeavyLower(t.week, null, monday + 6)
        assertNotNull(days)
        assertTrue("$days", days!! >= 1)
    }

    // ------------------------------------------------------------------------------------------------ R4-11 condition weeks
    @Test fun `R4-11 the current week never counts towards a condition's weeks of training`() = runBlocking {
        val (d, clock) = ready(conditions = listOf(StoredCondition("obesity_severe", monday)))
        done(d, monday)
        val w0 = d.bridge.conditions(monday).value
        clock.setDay(monday + 7)
        done(d, monday + 7)
        val w1 = d.bridge.conditions(monday + 7).value
        assertEquals(w0.maxZone, d.bridge.conditions(monday + 1).value.maxZone)
        assertNotNull(w1)
        // One finished week only after the week has ended.
        val u = com.personalfitnesscoach.data.core.model.ConditionsRecord.decode(com.personalfitnesscoach.data.core.model.ConditionsRecord.encode(d.docs.get(ConditionsRecord)!!))
        assertEquals(0, u.items.single().toUserCondition(monday + 6, 0).weeks)
    }

    // ------------------------------------------------------------------------------------------------ R4-13 injuries added later
    @Test fun `R4-13 an injury added in week 10 is conservative for its own 4 weeks`() = runBlocking {
        val (d, clock) = ready(monday - 70)
        clock.setDay(monday)
        d.docs.put(Profile, d.docs.get(Profile)!!.copy(priorInjuries = mapOf(Joint.KNEE to monday)))
        val u = d.bridge.user()!!
        assertTrue(u.injuries.regions.any { it.region == Joint.KNEE })
        clock.setDay(monday + 28)
        assertTrue(d.bridge.user()!!.injuries.regions.isEmpty())
    }

    // ------------------------------------------------------------------------------------------------ R4-14 SAF-004
    @Test fun `R4-14 a severe report tapped as muscle pain still makes the region conservative`() = runBlocking {
        val (d, _) = ready()
        for (i in 0 until 2) d.docs.put(PainRecord, PainRecord(1000L + i, monday - i, null, null, PainReport(Joint.KNEE, PainKind.MUSCLE_BURN, 3, descriptors = setOf("sharp")),
            action = PainAction.STOP_REGION))
        assertTrue(d.bridge.regions(monday).value.any { it.region == Joint.KNEE })
    }

    // ------------------------------------------------------------------------------------------------ R4-15 conditions not answered
    @Test fun `R4-15 conditions not answered yet means conservative, and planning waits for onboarding`() = runBlocking {
        val (d, _) = ready()
        d.docs.delete(ConditionsRecord)
        assertTrue(d.bridge.conditions(monday).value.conservative)
        d.docs.put(Profile, d.docs.get(Profile)!!.copy(onboardingStep = "conditions"))
        assertNull(d.planToday())
    }

    // ------------------------------------------------------------------------------------------------ R4-16 unopened week
    @Test fun `R4-16 a week the app was never opened pauses the block clock`() = runBlocking {
        val (d, clock) = ready()
        val before = d.docs.get(ProgramRecord)!!.clockWeek
        clock.setDay(monday + 7)
        d.onAppOpen()
        assertEquals(before, d.docs.get(ProgramRecord)!!.clockWeek)
        assertEquals(Blueprint.ClockAction.PAUSE, d.docs.get(WeekSummary, dayKey(monday))!!.clockAction)
    }

    // ------------------------------------------------------------------------------------------------ R4-17 / R4-32 fatigue inputs
    @Test fun `R4-17 workload ratios need real history, R4-32 F3 uses raw scores on both sides`() = runBlocking {
        val (d, clock) = ready()
        done(d, monday, rpe = 7.0, minutes = 60.0)
        clock.setDay(monday + 7)
        val (inputs, _) = d.bridge.fatigue(d.bridge.user()!!, d.docs.get(ProgramRecord))
        assertNull("no EWMA ratio before 28 days of history", inputs.ewmaRatio)
        d.docs.put(ReadinessRecord, ReadinessRecord(monday + 7, CheckIn(3, 3, 3, 3), null, 40.0, 70.0, Tier.FULL, atMs = 1))
        val (i2, _) = d.bridge.fatigue(d.bridge.user()!!, d.docs.get(ProgramRecord))
        assertEquals(40.0, i2.readinessMean7d!!, 1e-9)
    }

    // ------------------------------------------------------------------------------------------------ R4-19 / R4-26 backup
    @Test fun `R4-19 a restored workout with an unknown template is refused, R4-26 encrypted files hide their summary`() = runBlocking {
        val (d, _) = ready()
        done(d, monday)
        val snap = d.store.snapshot()
        try {
            d.backup.inspect(BackupFormat.encode(snap.copy(workouts = snap.workouts.map { it.copy(template = "FUTURE_DAY") }), 1, "test"))
            fail("expected refusal")
        } catch (e: BackupException) { assertEquals(BackupProblem.INVALID_CONTENT, e.problem) }
        val enc = String(d.backup.export("pw".toCharArray(), iterations = 10_000).second, Charsets.UTF_8).lines()[1]
        assertFalse(enc.contains("sha256") || enc.contains("sessions") || enc.contains("firstDay"))
        // The automatic copy before a restore can carry the same password.
        var copy: ByteArray? = null
        d.restore(d.backup.inspect(d.backup.export(iterations = 10_000).second), "pw".toCharArray()) { _, b -> copy = b }
        assertTrue(String(copy!!, Charsets.UTF_8).lines()[1].contains("\"encryption\""))
    }

    // ------------------------------------------------------------------------------------------------ R4-20 HIIT progression
    @Test fun `R4-20 interval progression counts sessions since the block started`() = runBlocking {
        val (d, clock) = ready()
        val hiit = ConditioningItem(Modality.ROWER, Zone.Z3, 6.0, 6.0, hiit = true, protocol = HiitProtocol.SHORT, doneWorkMinutes = 6.0)
        done(d, monday, conditioning = listOf(hiit)); done(d, monday + 3, conditioning = listOf(hiit))
        clock.setDay(monday + 7)
        d.onAppOpen()
        val p = d.docs.get(ProgramRecord)!!
        val input = d.bridge.weekInput(d.bridge.user()!!, p)
        assertEquals(if (p.blockStartDay <= monday) 2 else 0, input.hiitExposures)
    }

    // ------------------------------------------------------------------------------------------------ R4-21 walks
    @Test fun `R4-21 short and overlapping walks are not counted twice`() = runBlocking {
        val (d, _) = ready()
        d.docs.put(WalkRecord, WalkRecord(monday, 8 * 60, 30)); d.docs.put(WalkRecord, WalkRecord(monday, 8 * 60 + 10, 30)); d.docs.put(WalkRecord, WalkRecord(monday, 12 * 60, 5))
        assertEquals(40.0, d.bridge.totals(monday, monday).walkingMinutes, 1e-9)
    }

    // ------------------------------------------------------------------------------------------------ R4-22 Z1 length
    @Test fun `R4-22 the next Z1 length comes from the plan, not the longest block done`() = runBlocking {
        val (d, clock) = ready()
        d.docs.put(WeekPlanRecord, WeekPlanRecord(monday, listOf(0 to DayTemplate.FB_A), false, z1SessionMinutes = 17.0))
        done(d, monday, conditioning = listOf(ConditioningItem(Modality.ROWER, Zone.Z1, 35.0, doneWorkMinutes = 35.0)))
        clock.setDay(monday + 7)
        d.onAppOpen()
        assertEquals(17.0, d.docs.get(ProgramRecord)!!.z1SessionMinutes, 1e-9)
    }

    // ------------------------------------------------------------------------------------------------ R4-23 delete from history
    @Test fun `R4-23 deleting a past set rebuilds the exercise state`() = runBlocking {
        val (d, _) = ready()
        val w = done(d, monday, lifts = listOf(Triple("bench-press", 60.0, 8)))
        d.progress.record(d.sessions.load(w)!!, d.bridge.user()!!)
        val before = d.docs.get(ExerciseState, "bench-press")!!
        for (s in d.sessions.load(w)!!.exercises.single().sets) d.deletePastSet(s.id)
        val after = d.docs.get(ExerciseState, "bench-press")
        assertTrue("state rebuilt without the deleted sets: $before → $after", after == null || after != before)
    }

    // ------------------------------------------------------------------------------------------------ R4-27 screening answers
    @Test fun `R4-27 a null screening answer is refused`() {
        try {
            ScreeningRecord.decode("""{"v":1,"answers":{"heartOrBloodPressure":null,"metabolicRenalPulmonary":false,"symptoms":false,"palpitations":false,""" +
                """"limitOrPregnancy":false,"musculoskeletal":false,"longTermMedication":false,"regularlyActive":true},"takenDay":1}""")
            fail("expected refusal")
        } catch (e: DataFormatException) { /* expected */ }
    }

    // ------------------------------------------------------------------------------------------------ R4-28 step counter restarts
    @Test fun `R4-28 restarts come from the boot count, not the wall clock`() {
        val utc = ZoneId.of("UTC")
        val prev = StepCounterRecord(Days.startMs(monday, utc) + 8 * 3_600_000L, 5_000_000, 4000, bootCount = 7)
        // The user moved the clock 2 hours forward: same boot, only the new steps count.
        val moved = StepCounterRecord(prev.atMs + 3 * 3_600_000L, prev.elapsedMs + 3_600_000L, 4500, bootCount = 7)
        assertEquals(500, StepLedger.split(prev, moved, utc).sumOf { it.second })
        // A restart: the boot count changed, the counter starts again.
        val rebooted = StepCounterRecord(prev.atMs + 3_600_000L, 600_000, 300, bootCount = 8)
        assertEquals(300, StepLedger.split(prev, rebooted, utc).sumOf { it.second })
    }

    // ------------------------------------------------------------------------------------------------ R4-30 decision log
    @Test fun `R4-30 decisions older than about a year are pruned`() = runBlocking {
        val (d, clock) = ready(monday - 500)
        d.docs.put(DecisionEntry, DecisionEntry(1, 1, monday - 450, null, "SAFETY", listOf("SAF-001"), "X", registryVersion = "1.1.1"))
        d.docs.put(DecisionEntry, DecisionEntry(2, 2, monday - 10, null, "SAFETY", listOf("SAF-001"), "X", registryVersion = "1.1.1"))
        clock.setDay(monday)
        d.onAppOpen()
        assertEquals(listOf(2L), d.docs.all(DecisionEntry).map { it.seq }.filter { it < 10 })
    }
}
