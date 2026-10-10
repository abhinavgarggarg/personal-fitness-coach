package com.personalfitnesscoach.data.core.review

import com.personalfitnesscoach.data.core.Fixtures
import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.backup.BackupException
import com.personalfitnesscoach.data.core.backup.BackupFormat
import com.personalfitnesscoach.data.core.backup.BackupProblem
import com.personalfitnesscoach.data.core.model.ExerciseState
import com.personalfitnesscoach.data.core.model.ProgramRecord
import com.personalfitnesscoach.data.core.model.RecoveryRecord
import com.personalfitnesscoach.data.core.model.SafetyStopRecord
import com.personalfitnesscoach.data.core.model.StepCounterRecord
import com.personalfitnesscoach.data.core.model.WeekPlanRecord
import com.personalfitnesscoach.data.core.model.WeekSummary
import com.personalfitnesscoach.data.core.session.ItemDoc
import com.personalfitnesscoach.data.core.session.NewSet
import com.personalfitnesscoach.data.core.session.SetKind
import com.personalfitnesscoach.data.core.session.Status
import com.personalfitnesscoach.data.core.session.WorkoutDoc
import com.personalfitnesscoach.data.core.store.ExerciseRow
import com.personalfitnesscoach.data.core.store.SetRow
import com.personalfitnesscoach.data.core.store.WorkoutRow
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.data.core.time.FixedClock
import com.personalfitnesscoach.engine.calc.CheckIn
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.planning.Priority
import com.personalfitnesscoach.engine.program.DayTemplate
import com.personalfitnesscoach.engine.program.SlotRole
import com.personalfitnesscoach.engine.progression.Prescription
import com.personalfitnesscoach.engine.progression.ProgressionAction
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

/**
 * Regression tests for the re-check of the Part 4 fixes (findings RC-01 … RC-13, 10 Oct 2026). Each reproduces the scenario the
 * reviewer ran and asserts the corrected behaviour.
 */
class Part4RecheckRegressionTest {
    private val monday = Fixtures.MONDAY
    private val utc = ZoneId.of("UTC")

    private suspend fun done(d: PfcData, day: Int, template: DayTemplate = DayTemplate.FB_A, weekday: Int = Days.weekday(day),
                             lifts: List<Triple<String, Double, Int>> = emptyList()): Long {
        val w = d.store.insertWorkout(WorkoutRow(day = day, status = Status.DONE, template = template.name, tier = "FULL", startedAtMs = Days.startMs(day, utc) + 3_600_000,
            endedAtMs = Days.startMs(day, utc) + 7_200_000, sessionRpe = 6.0, actualMinutes = 50.0, registryVersion = "1.1.1", json = WorkoutDoc(weekday, 60.0, 10.0).encode()))
        for ((i, l) in lifts.withIndex()) {
            val (id, load, reps) = l
            val item = ItemDoc(SlotRole.MAIN, Priority.P1, 3, 8..12, DoseUnit.REPS, targetRir = 2.0, load = load, restMinSec = 90, restDefaultSec = 120, restMaxSec = 180, main = true)
            val e = d.store.insertExercise(ExerciseRow(workoutId = w, position = i, exerciseId = id, slotKey = "main$i", status = Status.DONE, json = item.encode()))
            repeat(3) { s -> d.store.insertSet(SetRow(workoutExerciseId = e, setIndex = s, kind = SetKind.WORKING, loadKg = load, reps = reps, rir = 2.0, loggedAtMs = 1)) }
        }
        return w
    }

    private suspend fun ready(clockDay: Int = monday, birthYear: Int = 1985): Pair<PfcData, FixedClock> {
        val (d, clock) = Fixtures.data(clockDay)
        Fixtures.onboard(d, birthYear = birthYear)
        return d to clock
    }

    // ------------------------------------------------------------------------------------------------ RC-01 recalibration ends
    @Test fun `RC-01 after a 70-day layoff each exercise recalibrates once, and HIIT and the load cap come back after three weeks`() = runBlocking {
        val (d, clock) = ready(monday - 120)
        done(d, monday - 70, lifts = listOf(Triple("bench-press", 80.0, 8), Triple("back-squat", 100.0, 8)))
        d.docs.put(ExerciseState, ExerciseState("bench-press", e1rm = 100.0, prescription = Prescription(80.0, 8..12, 8, ProgressionAction.HOLD), lastDoneDay = monday - 70))
        d.docs.put(ExerciseState, ExerciseState("back-squat", e1rm = 130.0, prescription = Prescription(100.0, 8..12, 8, ProgressionAction.HOLD), lastDoneDay = monday - 70))
        clock.setDay(monday)
        val info0 = d.bridge.returnInfo(d.bridge.user()!!, d.docs.get(ProgramRecord)).value
        assertEquals(monday, info0.recalibrateBefore); assertFalse(info0.plan.hiitAllowed); assertTrue(info0.plan.inReturn)
        val t0 = d.planToday()!!
        val r0 = d.bridge.request(t0.user, t0.program, t0.week, t0.next ?: t0.week.days.first(), Tier.FULL)
        assertNull(r0.e1rm["bench-press"]); assertNull(r0.e1rm["back-squat"])
        assertTrue(r0.calibrationCeilings.getValue("bench-press") <= 80.0 + 1e-9)
        // Bench is recalibrated on Monday; the squat is not done yet.
        done(d, monday, lifts = listOf(Triple("bench-press", 70.0, 8)))
        d.docs.put(ExerciseState, ExerciseState("bench-press", e1rm = 92.0, prescription = Prescription(75.0, 8..12, 8, ProgressionAction.HOLD), lastDoneDay = monday))
        for (k in listOf(2, 4, 7, 9, 11, 14, 16, 18)) done(d, monday + k, lifts = listOf(Triple("leg-press", 100.0, 10)))
        clock.setDay(monday + 2)
        val t2 = d.planToday()!!
        val r2 = d.bridge.request(t2.user, t2.program, t2.week, t2.next ?: t2.week.days.first(), Tier.FULL)
        assertEquals("bench is not recalibrated again", 92.0, r2.e1rm.getValue("bench-press"), 1e-9)
        assertNull("the squat has not been done since the return", r2.e1rm["back-squat"])
        // Three weeks back: the return window has ended, HIIT is allowed, and LOAD-005 applies again.
        clock.setDay(monday + 21)
        val t21 = d.planToday()!!
        val p21 = d.bridge.returnPlan(t21.user, t21.program).value
        assertFalse(p21.inReturn); assertTrue(p21.hiitAllowed)
        val vc = d.bridge.validationContext(t21.user, t21.program, t21.week, t21.next)
        assertEquals(t21.week.deload || t21.program.justFinishedLighterWeek, vc.loadCapExempt)
        val r21 = d.bridge.request(t21.user, t21.program, t21.week, t21.next ?: t21.week.days.first(), Tier.FULL)
        assertEquals(92.0, r21.e1rm.getValue("bench-press"), 1e-9)
    }

    @Test fun `RC-01 age 66 and 30 days off recalibrates too, and it ends the same way`() = runBlocking {
        val (d, clock) = ready(monday - 120, birthYear = 1960)
        done(d, monday - 30, lifts = listOf(Triple("bench-press", 60.0, 8)))
        clock.setDay(monday)
        assertEquals(monday, d.bridge.returnInfo(d.bridge.user()!!, d.docs.get(ProgramRecord)).value.recalibrateBefore)
        for (k in listOf(0, 2, 4, 7, 9, 11, 14, 16, 18)) done(d, monday + k, lifts = listOf(Triple("bench-press", 55.0, 8)))
        clock.setDay(monday + 21)
        val p = d.bridge.returnPlan(d.bridge.user()!!, d.docs.get(ProgramRecord)).value
        assertFalse(p.inReturn); assertTrue(p.hiitAllowed)
    }

    // ------------------------------------------------------------------------------------------------ RC-02 missed session in a ramp
    @Test fun `RC-02 a missed session inside a REG-004 ramp keeps the ramp`() = runBlocking {
        val (d, clock) = ready(monday - 60)
        done(d, monday - 21, lifts = listOf(Triple("bench-press", 80.0, 8)))
        d.docs.put(ExerciseState, ExerciseState("bench-press", e1rm = 100.0, prescription = Prescription(80.0, 8..12, 8, ProgressionAction.HOLD), lastDoneDay = monday - 21))
        clock.setDay(monday)
        done(d, monday, lifts = listOf(Triple("bench-press", 72.0, 8)))
        d.docs.put(WeekPlanRecord, WeekPlanRecord(monday, listOf(0 to DayTemplate.FB_A, 2 to DayTemplate.FB_B, 4 to DayTemplate.FB_C), false))
        clock.setDay(monday + 4) // Wednesday was missed
        val p = d.bridge.returnPlan(d.bridge.user()!!, d.docs.get(ProgramRecord)).value
        assertEquals(0.9, p.loadFactor, 1e-9); assertEquals(0.7, p.setsFactor, 1e-9); assertFalse(p.hiitAllowed)
    }

    // ------------------------------------------------------------------------------------------------ RC-03 red flags anywhere
    @Test fun `RC-03 a red flag given to generate is kept, and one reported mid-session ends the workout without touching progression`() = runBlocking {
        val symptom = RedFlags.SYMPTOMS.first()
        val (d, clock) = ready()
        val t = d.planToday()!!
        val w = d.generate(t, t.next ?: t.week.days.first(), Tier.FULL, redFlags = setOf(symptom)).value
        assertNotNull(w.safetyStop)
        assertNull(d.docs.get(SafetyStopRecord)!!.confirmedDay)
        clock.setDay(monday + 1)
        val c = d.checkIns.record(d.bridge.user()!!, CheckIn(5, 5, 5, 5), requestedTier = Tier.FULL)
        assertNotNull(c.safetyStop); assertEquals(Tier.RECOVERY, c.record.tier)

        val (d2, _) = ready()
        val t2 = d2.planToday()!!
        val day = t2.next ?: t2.week.days.first()
        val w2 = d2.generate(t2, day, Tier.FULL).value
        val id = d2.sessions.start(w2, day.template, day.weekday)
        val first = d2.sessions.load(id)!!.exercises.first()
        val item = w2.items.first()
        d2.sessions.logSet(first.row.id, NewSet(0, if (item.calibrating) SetKind.CALIBRATION else SetKind.WORKING, item.load, item.reps.first, rir = 2.0), "{}")
        assertNull("not a red flag", d2.reportRedFlag(setOf("tired")))
        assertNotNull(d2.reportRedFlag(setOf(symptom)))
        val stopped = d2.sessions.load(id)!!
        assertEquals(Status.DONE, stopped.row.status); assertTrue(stopped.doc.stoppedBySafety)
        assertNull(d2.sessions.active())
        assertNull(d2.docs.get(SafetyStopRecord)!!.confirmedDay)
        d2.progress.rebuild(d2.bridge.user()!!)
        assertNull("a stopped session never changes progression", d2.docs.get(ExerciseState, first.exerciseId))
        assertEquals("its sets still count", 1, d2.bridge.totals(monday, monday).completed)
    }

    // ------------------------------------------------------------------------------------------------ RC-05, RC-08, RC-12, RC-13 restore
    @Test fun `RC-05 restore drops fields this app would not write and resets the open session's screen state`() = runBlocking {
        val (d, _) = ready()
        d.docs.put(RecoveryRecord, RecoveryRecord(monday, illness = true))
        val t = d.planToday()!!
        val day = t.next ?: t.week.days.first()
        d.sessions.start(d.generate(t, day, Tier.FULL).value, day.template, day.weekday, activeState = "{\"text\":\"a private note\"}")
        val snap = d.store.snapshot()
        val tampered = snap.copy(docs = snap.docs.map { if (it.type == "recovery") it.copy(json = it.json.dropLast(1) + ",\"note\":\"saw Dr Smith\"}") else it })
        d.restore(d.backup.inspect(BackupFormat.encode(tampered, 1, "old"))) { _, _ -> }
        assertFalse(d.store.snapshot().docs.first { it.type == "recovery" }.json.contains("note"))
        assertEquals("{}", d.store.active()!!.json)
        val again = String(d.backup.export().second, Charsets.UTF_8)
        assertFalse(again.contains("Dr Smith") || again.contains("a private note"))
    }

    @Test fun `RC-08 restore refuses numbers logging would refuse`() = runBlocking {
        val (d, _) = ready()
        done(d, monday - 1, lifts = listOf(Triple("bench-press", 60.0, 8)))
        val snap = d.store.snapshot()
        fun refused(s: com.personalfitnesscoach.data.core.store.Snapshot) = try {
            d.backup.inspect(BackupFormat.encode(s, 1, "x")); fail("expected refusal")
        } catch (e: BackupException) { assertEquals(BackupProblem.INVALID_CONTENT, e.problem) }
        refused(snap.copy(sets = snap.sets.map { it.copy(loadKg = 5000.0) }))
        refused(snap.copy(sets = snap.sets.map { it.copy(reps = -4) }))
        refused(snap.copy(sets = snap.sets.map { it.copy(rir = -3.0) }))
        val state = ExerciseState.encode(ExerciseState("bench-press", e1rm = 100.0))
        refused(snap.copy(docs = snap.docs + com.personalfitnesscoach.data.core.store.DocRow("exercise_state", "bench-press", null,
            state.replace("100.0", "5000.0"), 1)))
    }

    @Test fun `RC-12 the old phone's step reading is never restored, RC-13 a damaged encryption header is a damaged file`() = runBlocking {
        val (d, _) = ready()
        d.docs.put(StepCounterRecord, StepCounterRecord(1_000, 1_000, 4000, bootCount = 3))
        val bytes = d.backup.export(iterations = 10_000).second
        d.restore(d.backup.inspect(bytes)) { _, _ -> }
        assertNull(d.docs.get(StepCounterRecord))
        val enc = String(d.backup.export("pw".toCharArray(), iterations = 10_000).second, Charsets.UTF_8)
        for (bad in listOf(enc.replace(Regex("\"iv\":\"[^\"]*\""), "\"iv\":\"\""), enc.replace(Regex("\"salt\":\"[^\"]*\""), "\"salt\":\"%%%\""))) {
            try { d.backup.inspect(bad.toByteArray(Charsets.UTF_8), "pw".toCharArray()); fail("expected refusal") }
            catch (e: BackupException) { assertEquals(BackupProblem.DAMAGED, e.problem) }
        }
    }

    // ------------------------------------------------------------------------------------------------ RC-06 lighter week and edits
    @Test fun `RC-06 editing history keeps a DEL-002 lighter week`() = runBlocking {
        val (d, _) = ready()
        val w = done(d, monday - 3, lifts = listOf(Triple("bench-press", 60.0, 8)))
        d.docs.put(ProgramRecord, d.docs.get(ProgramRecord)!!.copy(lighterSessionsLeft = 3, lighterThisWeek = true))
        d.deletePastSet(d.sessions.load(w)!!.exercises.single().sets.first().id)
        assertEquals(3, d.docs.get(ProgramRecord)!!.lighterSessionsLeft)
        assertEquals(Tier.MODIFIED, d.checkIns.record(d.bridge.user()!!, CheckIn(5, 5, 5, 5), requestedTier = Tier.FULL).record.tier)
    }

    // ------------------------------------------------------------------------------------------------ RC-07 no compounding
    @Test fun `RC-07 return-ramp loads come from the pre-break base each week, never from the reduced ones`() = runBlocking {
        val (d, clock) = ready(monday - 60)
        done(d, monday - 21, lifts = listOf(Triple("bench-press", 80.0, 8)))
        val trueE1rm = 100.0
        for (e in Library.all.filter { it.trackE1rm }) d.docs.put(ExerciseState, ExerciseState(e.id, e1rm = trueE1rm, e1rmDay = monday - 21, lastDoneDay = monday - 21, exposures = 5))
        val loads = HashMap<String, MutableList<Pair<Int, Double>>>()
        for (k in listOf(0, 2, 4, 7, 9, 11, 14, 16)) {
            clock.setDay(monday + k, hour = 9)
            val t = d.planToday()!!
            val day = t.next ?: continue
            val w = d.generate(t, day, Tier.FULL).value
            val id = d.sessions.start(w, day.template, day.weekday)
            for ((i, e) in d.sessions.load(id)!!.exercises.withIndex()) {
                val item = w.items[i]
                val load = item.load ?: continue
                if (item.calibrating || !item.exercise.trackE1rm || item.unit != DoseUnit.REPS || load <= 0.0) continue
                loads.getOrPut(item.exercise.id) { ArrayList() } += k / 7 to load
                // An honest lifter: reps in reserve as the true strength allows (Epley), reps at the bottom of the range.
                val possible = 30.0 * (trueE1rm / load - 1.0)
                val rir = (possible - item.reps.first).coerceIn(0.0, 10.0).let { Math.floor(it) }
                repeat(item.sets) { s -> d.sessions.logSet(e.row.id, NewSet(s, SetKind.WORKING, load, item.reps.first, rir = rir), "{}") }
                assertTrue("a ramp exposure is recorded as reduced", k >= 14 || ItemDoc.decode(d.sessions.load(id)!!.exercises[i].row.json).loadFactor < 1.0)
            }
            d.finishWorkout(id, 6.0, w.plannedMinutes, w.conditioning.map { it.workMinutes })
        }
        val tracked = loads.filterValues { v -> v.map { it.first }.toSet().containsAll(listOf(0, 1, 2)) }
        assertTrue("some lift was seen in all three weeks: $loads", tracked.isNotEmpty())
        for ((id, v) in tracked) {
            val byWeek = v.groupBy({ it.first }, { it.second }).mapValues { it.value.max() }
            assertTrue("$id $byWeek", byWeek.getValue(0) <= byWeek.getValue(1) + 1e-9 && byWeek.getValue(1) <= byWeek.getValue(2) + 1e-9)
            assertTrue("$id $byWeek: back to the full base after the ramp", byWeek.getValue(2) > byWeek.getValue(0))
        }
    }

    // ------------------------------------------------------------------------------------------------ RC-09 DEL-004
    @Test fun `RC-09 the week after a deload adds one rep in reserve`() = runBlocking {
        val (d, _) = ready()
        d.docs.put(WeekSummary, WeekSummary(monday - 7, 5, 3, 3, deload = true))
        val t = d.planToday()!!
        val day = t.week.days.first { it.slots.isNotEmpty() }
        val p = d.bridge.prepare(t.user, t.program, t.week, day, Tier.FULL)
        assertEquals(day.slots.map { it.targetRir + 1.0 }, p.request.day.slots.map { it.targetRir })
        assertTrue(p.decisions.any { it.reason == ReasonKey.DELOAD_RESUME })
        val (d2, _) = ready()
        val t2 = d2.planToday()!!
        val day2 = t2.week.days.first { it.slots.isNotEmpty() }
        assertEquals(day2.slots.map { it.targetRir }, d2.bridge.prepare(t2.user, t2.program, t2.week, day2, Tier.FULL).request.day.slots.map { it.targetRir })
    }

    // ------------------------------------------------------------------------------------------------ RC-11 VOL-007 k
    @Test fun `RC-11 the personal k is the median weekly ratio`() = runBlocking {
        val (d, clock) = ready(monday - 35)
        clock.setDay(monday)
        val weeks = listOf(1000.0, 1000.0, 1000.0, 4000.0).mapIndexed { i, wl -> WeekSummary(monday - 28 + 7 * i, 1, 3, 3, ssu = 100.0, workload = wl) }
        assertEquals(10.0, d.bridge.learnedK(weeks)!!, 1e-9)
        assertNull(d.bridge.learnedK(weeks.take(3)))
    }

    // ------------------------------------------------------------------------------------------------ weak tests made strict
    @Test fun `deleting every set of the only exposure leaves no e1RM or prescription from it`() = runBlocking {
        val (d, _) = ready()
        val w = done(d, monday, lifts = listOf(Triple("bench-press", 60.0, 8)))
        d.progress.record(d.sessions.load(w)!!, d.bridge.user()!!)
        assertNotNull(d.docs.get(ExerciseState, "bench-press")!!.prescription)
        for (s in d.sessions.load(w)!!.exercises.single().sets) d.deletePastSet(s.id)
        val after = d.docs.get(ExerciseState, "bench-press")
        assertTrue("$after", after == null || (after.e1rm == null && after.prescription == null))
    }
}
