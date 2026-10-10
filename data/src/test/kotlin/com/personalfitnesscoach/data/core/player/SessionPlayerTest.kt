package com.personalfitnesscoach.data.core.player

import com.personalfitnesscoach.data.core.Fixtures
import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.model.ExerciseState
import com.personalfitnesscoach.data.core.model.PainRecord
import com.personalfitnesscoach.data.core.model.SafetyStopRecord
import com.personalfitnesscoach.data.core.model.StoredCondition
import com.personalfitnesscoach.data.core.session.ActiveState
import com.personalfitnesscoach.data.core.session.SetKind
import com.personalfitnesscoach.data.core.session.Sheet
import com.personalfitnesscoach.data.core.session.Stage
import com.personalfitnesscoach.data.core.session.Status
import com.personalfitnesscoach.data.core.time.FixedClock
import com.personalfitnesscoach.engine.calc.CheckIn
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.FormCheck
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.program.SrpePrompt
import com.personalfitnesscoach.engine.safety.AdditionVerdict
import com.personalfitnesscoach.engine.safety.PainAction
import com.personalfitnesscoach.engine.safety.PainKind
import com.personalfitnesscoach.engine.safety.PainReport
import com.personalfitnesscoach.engine.safety.SessionValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The guided workout over the data layer (Phase 2 FS-4…FS-7, A1–A8; Part 5). */
class SessionPlayerTest {

    /** An intermediate lifter with history (e1RMs), so working sets and INT-007 apply. */
    private suspend fun trained(level: Level = Level.INTERMEDIATE, minutes: Int = 60, conditions: List<StoredCondition> = emptyList()): Triple<PfcData, FixedClock, SessionPlayer> {
        val (d, clock) = Fixtures.data()
        Fixtures.onboard(d, level = level, minutes = minutes, conditions = conditions)
        // Seed every library lift with an estimated max so nothing calibrates (calibration has its own test).
        for (ex in Library.all) d.docs.put(ExerciseState, ExerciseState(ex.id, e1rm = 100.0, e1rmDay = Fixtures.MONDAY - 3, lastDoneDay = Fixtures.MONDAY - 3, exposures = 6))
        return Triple(d, clock, SessionPlayer(d))
    }

    private suspend fun start(d: PfcData, tier: Tier = Tier.FULL, minutes: Int? = null): Long {
        val t = d.planToday()!!
        d.checkIns.record(t.user, CheckIn(3, 3, 3, 3))
        val day = t.next ?: t.week.days.first()
        val g = d.generateSession(t, day, tier, minutes ?: t.user.profile.sessionMinutes)
        return d.startSession(g)
    }

    @Test fun `a session starts with the warm-up, then the lifts in order with ramp-up sets before the first main lift`() = runBlocking {
        val (d, _, p) = trained()
        start(d)
        val v0 = p.view()!!
        assertEquals(Stage.WARMUP, v0.stage)
        assertTrue(v0.step is Step.Warmup)
        assertTrue((v0.step as Step.Warmup).drills.isNotEmpty())
        val v1 = p.completeStage(Stage.WARMUP)
        val lift = (v1.step as Step.Lift).lift
        assertEquals(v1.lifts.first().rowId, lift.rowId)
        if (lift.item.rampSets.isNotEmpty()) {
            assertEquals(SetKind.WARMUP, lift.next!!.kind)
            assertEquals(lift.item.rampSets.first().load, lift.next!!.load)
            // Skipping the ramp goes straight to the working sets.
            val v2 = p.skipRamp(lift.rowId)
            assertEquals(SetKind.WORKING, (v2.step as Step.Lift).lift.next!!.kind)
        }
    }

    @Test fun `logging a set saves it at once with the resume point, starts the rest and survives a restart`() = runBlocking {
        val (d, clock, p) = trained()
        start(d)
        p.completeStage(Stage.WARMUP)
        val lift = (p.view()!!.step as Step.Lift).lift
        p.skipRamp(lift.rowId)
        val t = (p.view()!!.step as Step.Lift).lift.next!!
        val r = p.log(lift.rowId, LiftEntry(t.load, t.reps.last, rir = t.targetRir))
        assertEquals(lift.item.restDefaultSec, r.restSec)
        assertEquals(clock.nowMs() + lift.item.restDefaultSec * 1000L, r.view.restEndsAtMs)
        // A new data layer over the same store (process death) resumes at the same place, set count and rest.
        val again = SessionPlayer(PfcData(d.store, clock, "test")).view()!!
        val l2 = again.lifts.first { it.rowId == lift.rowId }
        assertEquals(1, l2.workingDone)
        assertEquals(r.view.restEndsAtMs, again.restEndsAtMs)
        assertEquals(2, l2.next!!.number)
        // ±30 s and skip.
        assertEquals(r.view.restEndsAtMs!! + 30_000, p.adjustRest(30).restEndsAtMs)
        assertNull(p.skipRest().restEndsAtMs)
    }

    @Test fun `INT-007 an easy set raises the next load one step, a hard one lowers it, never beyond 10 percent`() = runBlocking {
        val (d, _, p) = trained()
        start(d)
        p.completeStage(Stage.WARMUP)
        val lift = p.view()!!.lifts.first { it.item.load != null && it.item.load!! >= 40.0 && it.item.unit == com.personalfitnesscoach.engine.model.DoseUnit.REPS }
        p.skipRamp(lift.rowId)
        val planned = lift.item.load!!
        var t = p.view()!!.lifts.first { it.rowId == lift.rowId }.next!!
        val easy = p.log(lift.rowId, LiftEntry(t.load, t.reps.last, rir = lift.item.targetRir + 2.5))
        val up = easy.view.lifts.first { it.rowId == lift.rowId }.next!!.load!!
        assertTrue("easy → heavier ($planned → $up)", up > planned)
        assertTrue(up <= planned * 1.10 + 1e-9)
        assertTrue(easy.decisions.any { it.reason == ReasonKey.INSESSION_LOAD_UP })
        t = easy.view.lifts.first { it.rowId == lift.rowId }.next!!
        val hard = p.log(lift.rowId, LiftEntry(t.load, t.reps.first - 1, rir = 0.0))
        val down = hard.view.lifts.first { it.rowId == lift.rowId }.next?.load
        if (down != null) assertTrue("hard → lighter ($up → $down)", down < up)
    }

    @Test fun `CAL-001 a new lifter ramps by the effort answer and the exercise ends when the working load is found`() = runBlocking {
        val (d, _) = Fixtures.data()
        Fixtures.onboard(d, level = Level.BEGINNER)
        val p = SessionPlayer(d)
        start(d)
        p.completeStage(Stage.WARMUP)
        // A barbell or machine lift (free-weight dumbbell steps can be too coarse for the CAL-001 band, D-070).
        val cal = p.view()!!.lifts.first { it.next?.kind == SetKind.CALIBRATION && it.exercise.loadType in setOf(com.personalfitnesscoach.engine.model.LoadType.BARBELL,
            com.personalfitnesscoach.engine.model.LoadType.STACK) }
        val t1 = cal.next!!
        val r1 = p.log(cal.rowId, LiftEntry(t1.load, t1.reps.first, rir = 5.0))
        val t2 = r1.view.lifts.first { it.rowId == cal.rowId }.next!!
        assertEquals(SetKind.CALIBRATION, t2.kind)
        assertTrue("ramp step up ${t1.load} → ${t2.load}", t2.load!! > t1.load!!)
        val r2 = p.log(cal.rowId, LiftEntry(t2.load, t2.reps.first, rir = 3.0))
        assertTrue(r2.calibration is com.personalfitnesscoach.engine.progression.CalibrationStep.Found)
        assertTrue(r2.view.lifts.first { it.rowId == cal.rowId }.finished)
    }

    @Test fun `pauses over 10 minutes do not count towards the session minutes, shorter ones do`() = runBlocking {
        val (d, clock, p) = trained()
        start(d)
        clock.ms += 20 * 60_000L
        p.pause(); clock.ms += 5 * 60_000L; p.resume()
        p.pause(); clock.ms += 15 * 60_000L; p.resume()
        assertEquals(40.0 - 15.0, p.view()!!.elapsedMinutes, 0.01)
    }

    @Test fun `SUB-002 swap options exclude what is already in the session, and a swap re-validates and keeps the slot`() = runBlocking {
        val (d, _, p) = trained()
        start(d)
        p.completeStage(Stage.WARMUP)
        val v = p.view()!!
        val lift = v.lifts[1]
        val choice = p.swapOptions(lift.rowId)
        assertTrue(choice.options.isNotEmpty())
        val inSession = v.lifts.map { it.exercise.id }.toSet()
        assertTrue(choice.options.none { it.exercise.id in inSession })
        val pick = choice.options.first().exercise.id
        val r = p.swap(lift.rowId, pick)
        assertFalse(r.refused)
        val after = r.view.lifts.first { it.rowId == lift.rowId }
        assertEquals(pick, after.exercise.id)
        assertEquals(lift.exercise.id, after.item.swappedFrom)
        // The whole session still passes the final safety check.
        val w = d.sessions.load(v.workoutId)!!
        val t = d.planToday()!!
        val ctx = d.bridge.validationContext(t.user, t.program, t.week, null)
        assertTrue(SessionValidator.violations(d.bridge.doneSession(w).copy(exercises = w.exercises.filter { it.row.status != Status.SKIPPED }.map { e ->
            com.personalfitnesscoach.engine.safety.SessionExercise(Library.require(e.exerciseId), e.doc.sets, e.doc.reps.last, e.doc.targetRir, e.doc.loadFactor, e.doc.main) }),
            ctx.copy(fullTierWorkingSets = w.doc.fullTierWorkingSets)).none { it.code == "TAG_BLOCKED" || it.code == "JOINT_LIMIT" })
    }

    @Test fun `EQ-002 occupied offers do-it-later, which moves the exercise to the end`() = runBlocking {
        val (d, _, p) = trained()
        start(d)
        p.completeStage(Stage.WARMUP)
        val first = p.view()!!.lifts.first()
        val occ = p.swapOptions(first.rowId, occupied = true)
        assertTrue(occ.canDoLater)
        val v = p.doLater(first.rowId)
        assertEquals(first.rowId, v.lifts.last().rowId)
        assertTrue((v.step as Step.Lift).lift.rowId != first.rowId)
    }

    @Test fun `SAF-003 mild joint pain lowers this exercise's load 10 to 20 percent and keeps the report`() = runBlocking {
        val (d, _, p) = trained()
        start(d)
        p.completeStage(Stage.WARMUP)
        val lift = p.view()!!.lifts.first { it.item.load != null && it.item.load!! >= 40.0 }
        p.skipRamp(lift.rowId)
        val joint = lift.exercise.jointStress.maxByOrNull { it.value }!!.key
        val r = p.reportPain(lift.rowId, PainReport(joint, PainKind.JOINT_OR_TENDON, 2))
        assertEquals(PainAction.CONTINUE_CAUTION, r.outcome.action)
        val lowered = r.changes.filterIsInstance<Change.LoadLowered>().single()
        assertTrue(lowered.to <= lowered.from * 0.9 + 1e-9 && lowered.to >= lowered.from * 0.7)
        assertEquals(lowered.to, r.view!!.lifts.first { it.rowId == lift.rowId }.next!!.load!!, 1e-9)
        assertTrue(r.view!!.lifts.first { it.rowId == lift.rowId }.item.painReduced)
        assertEquals(1, d.docs.all(PainRecord).size)
    }

    @Test fun `SAF-003 a 5 out of 10 stops the exercise and the rest of the session keeps that joint at stress 1 or less`() = runBlocking {
        val (d, _, p) = trained()
        start(d)
        p.completeStage(Stage.WARMUP)
        val v = p.view()!!
        val lift = v.lifts.first { (it.exercise.jointStress[Joint.KNEE] ?: 0) >= 2 }.let { it }
        val r = p.reportPain(lift.rowId, PainReport(Joint.KNEE, PainKind.JOINT_OR_TENDON, 5))
        assertEquals(PainAction.STOP_EXERCISE, r.outcome.action)
        val after = r.view!!
        assertTrue(after.lifts.first { it.rowId == lift.rowId }.finished)
        for (l in after.lifts.filter { !it.finished }) assertTrue("${l.exercise.id} loads the knee", l.exercise.stress(Joint.KNEE) <= 1)
        assertTrue(r.alternatives.all { it.exercise.stress(Joint.KNEE) <= 1 })
    }

    @Test fun `SAF-003 sharp pain that affects whole-body movement ends the session, keeping what was logged`() = runBlocking {
        val (d, _, p) = trained()
        val id = start(d)
        p.completeStage(Stage.WARMUP)
        val lift = (p.view()!!.step as Step.Lift).lift
        p.skipRamp(lift.rowId)
        val t = p.view()!!.lifts.first { it.rowId == lift.rowId }.next!!
        p.log(lift.rowId, LiftEntry(t.load, t.reps.last, rir = 2.0))
        val r = p.reportPain(lift.rowId, PainReport(Joint.SPINE, PainKind.JOINT_OR_TENDON, 6, descriptors = setOf("sharp"), affectsWholeBodyMovement = true))
        assertTrue(r.endSession)
        assertNull(d.sessions.active())
        val w = d.sessions.load(id)!!
        assertEquals(Status.DONE, w.row.status)
        assertTrue(w.doc.endedEarly)
        assertEquals(1, w.exercises.sumOf { it.working.size })
        assertNotNull(p.lastSummary)
    }

    @Test fun `SAF-002 a red flag during the session ends it at once and the stop is kept`() = runBlocking {
        val (d, _, p) = trained()
        val id = start(d)
        p.completeStage(Stage.WARMUP)
        assertNotNull(p.redFlag(setOf("chest_pain_pressure_tightness")))
        assertNull(d.sessions.active())
        assertTrue(d.sessions.load(id)!!.doc.stoppedBySafety)
        assertNull(d.docs.get(SafetyStopRecord)!!.confirmedDay)
    }

    @Test fun `FS-7 changing the time left trims the lowest priorities first and keeps the cool-down`() = runBlocking {
        val (d, _, p) = trained(minutes = 75)
        start(d)
        val before = p.completeStage(Stage.WARMUP)
        val setsBefore = before.lifts.sumOf { it.item.sets }
        val r = p.changeTime(15)
        assertTrue(r.changes.isNotEmpty())
        val after = r.view
        val active = after.lifts.filter { it.status != Status.SKIPPED }
        assertTrue(active.sumOf { it.item.sets } < setsBefore)
        // P1 work is the last to go.
        val p1 = before.lifts.filter { it.item.priority == com.personalfitnesscoach.engine.planning.Priority.P1 }.map { it.rowId }.toSet()
        val skipped = after.lifts.filter { it.status == Status.SKIPPED }.map { it.rowId }.toSet()
        if (skipped.isNotEmpty() && skipped.size < before.lifts.size) assertTrue(p1.none { it in skipped } || skipped.size == before.lifts.size)
        assertTrue("remaining ${after.remainingMinutes}", after.remainingMinutes <= 15.0 + 0.05)
        assertTrue((after.workout.doc.cooldownMinutes ?: 0.0) > 0.0)
    }

    @Test fun `SAF-006 an added set is fine within the caps, needs a confirmation past one, and is blocked on a painful region`() = runBlocking {
        val (d, _, p) = trained()
        start(d)
        p.completeStage(Stage.WARMUP)
        val lift = p.view()!!.lifts.first()
        val ok = p.addSet(lift.rowId)
        assertEquals(AdditionVerdict.OK, ok.verdict)
        assertEquals(lift.item.sets + 1, ok.view.lifts.first { it.rowId == lift.rowId }.item.sets)
        // Keep adding: the session cap needs a confirmation, and the ceiling blocks.
        var v = ok.verdict
        var n = 0
        while (v == AdditionVerdict.OK && n++ < 40) v = p.addSet(lift.rowId).verdict
        assertEquals(AdditionVerdict.WARN_CONFIRM, v)
        assertEquals(AdditionVerdict.OK, p.addSet(lift.rowId, confirmed = true).verdict)
        // A joint with stop-level pain today: additions there are blocked.
        val other = p.view()!!.lifts.first { it.rowId != lift.rowId && it.exercise.jointStress.values.any { s -> s >= 2 } }
        val joint = other.exercise.jointStress.maxByOrNull { it.value }!!.key
        d.reportPain(PainReport(joint, PainKind.JOINT_OR_TENDON, 5))
        assertEquals(AdditionVerdict.BLOCKED, p.addSet(other.rowId).verdict)
    }

    @Test fun `finishing gives a summary, records the minutes and asks the rating now or later (LOAD-002)`() = runBlocking {
        val (d, clock, p) = trained()
        val id = start(d)
        p.completeStage(Stage.WARMUP)
        for (l in p.view()!!.lifts) {
            p.skipRamp(l.rowId)
            var t = p.view()!!.lifts.first { it.rowId == l.rowId }.next
            while (t != null) {
                clock.ms += 90_000
                p.log(l.rowId, LiftEntry(t.load, if (t.unit == com.personalfitnesscoach.engine.model.DoseUnit.SECONDS) null else t.reps.last,
                    seconds = if (t.unit == com.personalfitnesscoach.engine.model.DoseUnit.SECONDS) t.reps.last else null, rir = t.targetRir ?: 3.0,
                    form = FormCheck.YES))
                t = p.view()!!.lifts.first { it.rowId == l.rowId }.next
            }
        }
        val v = p.view()!!
        for ((i, c) in v.conditioning.withIndex()) p.logConditioning(i, c.workMinutes)
        var step = p.view()!!.step
        while (step !is Step.Done) {
            val st = p.view()!!.stage!!
            p.completeStage(st)
            step = p.view()!!.step
        }
        val s = p.finish(sessionRpe = null)
        assertEquals(Status.DONE, d.sessions.load(id)!!.row.status)
        assertTrue(s.minutes > 0)
        assertTrue(s.workingSets > 0)
        // The last set was just now: the rating waits 10+ minutes (LOAD-002).
        assertEquals(SrpePrompt.Ask.WAIT, s.ask)
        assertNull(d.sessions.active())
    }

    @Test fun `the resume point survives a reset - logged work shows where the user got to`() = runBlocking {
        val (d, _, p) = trained()
        start(d)
        p.completeStage(Stage.WARMUP)
        val lift = (p.view()!!.step as Step.Lift).lift
        p.skipRamp(lift.rowId)
        val t = p.view()!!.lifts.first { it.rowId == lift.rowId }.next!!
        p.log(lift.rowId, LiftEntry(t.load, t.reps.last, rir = 2.0))
        val (w, _) = d.sessions.active()!!
        d.sessions.saveState(w.id, "{}")
        val v = p.view()!!
        assertEquals(Stage.LIFTS, v.stage)
        assertEquals(1, v.lifts.first { it.rowId == lift.rowId }.workingDone)
    }

    @Test fun `the resume point round-trips and an unreadable one starts over instead of blocking the workout`() {
        val s = ActiveState(Stage.LIFTS, 7L, mapOf(7L to 62.5), setOf(3L), setOf(9L), 1000L, 120, Sheet.REPLACE, null, 700_000L, 900L, mapOf(0 to 12.5))
        assertEquals(s, ActiveState.decode(s.encode()))
        assertEquals(ActiveState(), ActiveState.decode("not json"))
        assertEquals(ActiveState(), ActiveState.decode("{\"v\":1,\"stage\":\"NOPE\"}"))
        assertEquals(ActiveState(), ActiveState.decode("{}"))
    }

    @Test fun `FS-5 absurd entries need a confirmation`() {
        val t = SetTarget(SetKind.WORKING, 1, 3, 60.0, 8..10, com.personalfitnesscoach.engine.model.DoseUnit.REPS, false, 2.0, false)
        assertTrue(SessionPlayer.unusual(LiftEntry(500.0, 8), t))
        assertTrue(SessionPlayer.unusual(LiftEntry(60.0, 99), t))
        assertFalse(SessionPlayer.unusual(LiftEntry(65.0, 10), t))
    }
}
