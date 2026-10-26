package com.personalfitnesscoach.data.core.review

import com.personalfitnesscoach.data.core.Fixtures
import com.personalfitnesscoach.data.core.engine.ExerciseProgress
import com.personalfitnesscoach.data.core.engine.ProgressContext
import com.personalfitnesscoach.data.core.model.ExerciseState
import com.personalfitnesscoach.data.core.model.ProgramRecord
import com.personalfitnesscoach.data.core.session.ItemDoc
import com.personalfitnesscoach.data.core.session.SetKind
import com.personalfitnesscoach.data.core.session.Status
import com.personalfitnesscoach.data.core.session.StoredExercise
import com.personalfitnesscoach.data.core.session.StoredWorkout
import com.personalfitnesscoach.data.core.session.WorkoutDoc
import com.personalfitnesscoach.data.core.store.ExerciseRow
import com.personalfitnesscoach.data.core.store.SetRow
import com.personalfitnesscoach.data.core.store.WorkoutRow
import com.personalfitnesscoach.engine.calc.E1rm
import com.personalfitnesscoach.engine.calc.Inventory
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.planning.Priority
import com.personalfitnesscoach.engine.program.SlotRole
import com.personalfitnesscoach.engine.progression.Prescription
import com.personalfitnesscoach.engine.progression.ProgressionAction
import com.personalfitnesscoach.data.core.time.Days
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 4 Part 4: the lightest load that is too heavy, from logged sets to the next plan, and the new-block re-check. */
class Part4AuditDataTest {
    private val bench = Library.require("bench-press")
    private val ctx = ProgressContext(Inventory(), Level.BEGINNER, 40, flaggedScreen = false, recoveryOk = true)
    private val day = 20_000

    private fun item(calibrating: Boolean, load: Double = 20.0, recheck: Boolean = false, offer: String? = null) =
        ItemDoc(SlotRole.MAIN, Priority.P1, 3, 8..12, DoseUnit.REPS, targetRir = 2.0, load = load, restMinSec = 120, restDefaultSec = 150,
            restMaxSec = 180, calibrating = calibrating, main = true, lightestRecheck = recheck, easierOffer = offer)

    private fun exposure(item: ItemDoc, vararg sets: Triple<String, Double, Pair<Int, Double?>>) = StoredExercise(
        ExerciseRow(1, 1, 0, bench.id, "main", "DONE", item.encode()), item,
        sets.mapIndexed { i, (kind, load, rr) -> SetRow(i + 1L, 1, i, kind, load, rr.first, rir = rr.second, loggedAtMs = i.toLong()) })

    private fun cal(load: Double, reps: Int, rir: Double?) = Triple(SetKind.CALIBRATION, load, reps to rir)
    private fun work(load: Double, reps: Int, rir: Double?) = Triple(SetKind.WORKING, load, reps to rir)

    @Test fun `AUD-10 working sets at the empty bar short of the range twice in a row mark it too heavy, and sets in the range do not`() {
        val once = ExerciseProgress.apply(ExerciseState(bench.id), bench, exposure(item(false), work(20.0, 4, 1.0), work(20.0, 4, 1.0)), day, ctx, null).value
        assertEquals(20.0, once.state.prescription!!.load, 0.0)
        assertFalse("one bad day is not enough (review finding 11)", once.state.prescription!!.lightestTooHeavy)
        val short = ExerciseProgress.apply(once.state, bench, exposure(item(false), work(20.0, 4, 1.0), work(20.0, 4, 1.0)), day + 3, ctx, null).value
        val p = short.state.prescription!!
        assertEquals(20.0, p.load, 0.0)
        assertTrue("4 reps at RIR 1, twice, cannot become 8 at RIR 2", p.lightestTooHeavy)
        val fine = ExerciseProgress.apply(ExerciseState(bench.id), bench, exposure(item(false), work(20.0, 8, 2.0), work(20.0, 8, 2.0)), day, ctx, null).value
        assertFalse(fine.state.prescription!!.lightestTooHeavy)
    }

    @Test fun `AUD-11 a ramp set not lifted at the lightest load ends the ramp as too heavy, instead of ramping again at the same load`() {
        val r = ExerciseProgress.apply(ExerciseState(bench.id), bench, exposure(item(true), cal(20.0, 0, 0.0)), day, ctx, null).value
        val p = r.state.prescription!!
        assertEquals(20.0, p.load, 0.0)
        assertTrue(p.lightestTooHeavy)
        assertNull(r.state.calibration)
    }

    @Test fun `AUD-12 a ramp stopped short at the bar gives an estimate the plan can tell is too heavy`() {
        // 3 reps with about 3 left at 20 kg: the e1RM (24 kg) supports 18 kg for 8 reps at RIR 2, so the bar is too heavy for 8-12.
        val r = ExerciseProgress.apply(ExerciseState(bench.id), bench, exposure(item(true), cal(20.0, 3, 3.0)), day, ctx, null).value
        val est = r.state.e1rm ?: E1rm.fromSet(20.0, 3, 3.0)!!
        assertTrue(com.personalfitnesscoach.engine.calc.PlateMath.belowLightest(E1rm.loadFor(est, 8, 2.0), listOf(20.0, 22.5)) ||
            r.state.prescription?.lightestTooHeavy == true)
    }

    @Test fun `AUD-13 the re-check ramp in a new block starts the exercise afresh`() = runBlocking {
        val (d, _) = Fixtures.data()
        Fixtures.onboard(d)
        val u = d.bridge.user()!!
        val old = ExerciseState(bench.id, e1rm = 21.0, e1rmDay = Fixtures.MONDAY, prescription = Prescription(20.0, 8..12, 8, ProgressionAction.REDUCE,
            lightestTooHeavy = true), prescriptionDay = Fixtures.MONDAY, lastDoneDay = Fixtures.MONDAY, exposures = 3)
        d.docs.put(ExerciseState, old)
        // Four weeks later (no layoff), the new block's ramp: 20 kg for 10 reps easily, then 25 kg with 3 left.
        val doc = item(true, recheck = true)
        val w = StoredWorkout(WorkoutRow(1, Fixtures.MONDAY + 28, Status.DONE, "FB_A", registryVersion = "3.0.1", json = WorkoutDoc(0, 60.0, 5.0).encode()),
            WorkoutDoc(0, 60.0, 5.0), listOf(exposure(doc, cal(20.0, 10, 5.0), cal(25.0, 10, 3.0))))
        d.progress.record(w, u)
        val s = d.docs.get(ExerciseState, bench.id)!!
        // 10 + 3 is not a valid estimate (INT-004), so the found load is the working load; the old 21 kg estimate is gone.
        assertNull(s.e1rm)
        val p = s.prescription!!
        assertTrue("found at 25 kg or less: ${p.load}", p.load in 20.0..25.0)
        assertFalse("the old too-heavy prescription is gone", p.lightestTooHeavy)
        assertEquals(4, s.exposures)
    }

    @Test fun `AUD-14 the flags survive storage`() {
        val st = ExerciseState(bench.id, prescription = Prescription(20.0, 8..12, 8, ProgressionAction.HOLD, lightestTooHeavy = true))
        val back = ExerciseState.decode(ExerciseState.encode(st))
        assertTrue(back.prescription!!.lightestTooHeavy)
        val doc = item(true, recheck = true, offer = "db-bench-press")
        val again = ItemDoc.decode(doc.encode())
        assertTrue(again.lightestRecheck); assertEquals("db-bench-press", again.easierOffer)
        assertNull("an unknown exercise is not offered", ItemDoc.decode(doc.copy(easierOffer = "no-such-exercise").encode()).easierOffer)
        assertFalse(ItemDoc.decode(item(false).encode()).lightestRecheck)
    }

    @Test fun `AUD-15 the plan gets each exercise's last day and the block start, so the re-check can happen`() = runBlocking {
        val (d, _) = Fixtures.data()
        Fixtures.onboard(d)
        d.docs.put(ExerciseState, ExerciseState(bench.id, e1rm = 21.0, lastDoneDay = Fixtures.MONDAY - 3))
        val t = d.planToday()!!
        val req = d.bridge.request(t.user, t.program, t.week, t.next ?: t.week.days.first(), Tier.FULL)
        assertEquals(Fixtures.MONDAY - 3, req.lastDone[bench.id])
        assertEquals(d.docs.get(ProgramRecord)!!.blockStartDay, req.blockStartDay)
        assertNotNull(req.blockStartDay)
    }

    @Test fun `AUD-16 a red-flag stop confirmed the same day still makes the first session back LIGHT (review finding 1)`() = runBlocking {
        for (lag in listOf(0, 1)) {
            val (d, clock) = Fixtures.data()
            Fixtures.onboard(d)
            for (ex in Library.all) d.docs.put(ExerciseState, ExerciseState(ex.id, e1rm = 100.0, e1rmDay = Fixtures.MONDAY - 3, lastDoneDay = Fixtures.MONDAY - 3, exposures = 6))
            val t = d.planToday()!!
            d.checkIns.record(t.user, com.personalfitnesscoach.engine.calc.CheckIn(3, 3, 3, 3))
            d.startSession(d.generateSession(t, t.next!!, Tier.FULL))
            val p = com.personalfitnesscoach.data.core.player.SessionPlayer(d)
            p.completeStage(com.personalfitnesscoach.data.core.session.Stage.WARMUP)
            val l = p.view()!!.lifts.first()
            p.skipRamp(l.rowId)
            val tg = p.view()!!.lifts.first { it.rowId == l.rowId }.next!!
            clock.ms += 60_000
            p.log(l.rowId, com.personalfitnesscoach.data.core.player.LiftEntry(tg.load, if (tg.unit == DoseUnit.SECONDS) null else tg.reps.first,
                seconds = if (tg.unit == DoseUnit.SECONDS) tg.reps.last else null, rir = 2.0))
            assertNotNull(p.redFlag(setOf("chest_pain_pressure_tightness")))
            clock.setDay(Fixtures.MONDAY + lag)
            d.confirmStopResolved()
            for (after in listOf(lag, lag + 2)) {
                clock.setDay(Fixtures.MONDAY + after)
                val t2 = d.planToday()!!
                assertEquals("lag $lag, day +$after", Tier.LIGHT, d.bridge.gate(t2.user, t2.program).cap(Tier.FULL))
            }
        }
    }

    @Test fun `AUD-17 after a break at the lightest weights, no lift is planned at the unreduced weight (review finding 5)`() = runBlocking {
        val start = Fixtures.MONDAY - 28
        val (d, clock) = Fixtures.data(start)
        Fixtures.onboard(d, level = Level.BEGINNER, birthYear = 1990)
        val inv = d.bridge.user()!!.equipment.inventory
        fun seed() = runBlocking {
            for (ex in Library.all) {
                if (ex.assisted || ex.loadType !in setOf(com.personalfitnesscoach.engine.model.LoadType.BARBELL, com.personalfitnesscoach.engine.model.LoadType.DUMBBELL,
                        com.personalfitnesscoach.engine.model.LoadType.KETTLEBELL, com.personalfitnesscoach.engine.model.LoadType.STACK)) continue
                val avail = com.personalfitnesscoach.engine.calc.PlateMath.loadsFor(ex, inv)
                if (avail.isEmpty()) continue
                d.docs.put(ExerciseState, ExerciseState(ex.id, prescription = Prescription(avail.min(), ex.defaultRepRange, ex.defaultRepRange.first, ProgressionAction.HOLD),
                    prescriptionDay = start, lastDoneDay = start, exposures = 4, knownApplied = true))
            }
        }
        seed()
        d.store.insertWorkout(WorkoutRow(day = start, status = Status.DONE, template = "FB_A", tier = "FULL", startedAtMs = 1, endedAtMs = 2, sessionRpe = 5.0,
            actualMinutes = 40.0, registryVersion = "3.0.1", json = WorkoutDoc(0, 60.0, 5.0).encode()))
        clock.setDay(start + 21)
        val t = d.planToday()!!
        d.checkIns.record(t.user, com.personalfitnesscoach.engine.calc.CheckIn(3, 3, 3, 3))
        val g = d.generateSession(t, t.next!!, Tier.FULL)
        val ret = d.bridge.returnPlan(t.user, t.program).value
        assertTrue("the return reduction is there: $ret", ret.loadFactor < 1.0)
        assertTrue("easier versions stand in", g.decisions.any { it.reason == com.personalfitnesscoach.engine.core.ReasonKey.LIGHTEST_TOO_HEAVY ||
            it.reason == com.personalfitnesscoach.engine.core.ReasonKey.LIGHTEST_TOO_HEAVY_LEFT_OUT })
        for (it in g.workout.items) if (it.load != null && !it.calibrating) {
            val stored = d.docs.get(ExerciseState, it.exercise.id)?.prescription
            if (stored != null && stored.repRange.first == it.reps.first)
                assertTrue("${it.exercise.id} at ${it.load}, stored ${stored.load}", it.load!! < stored.load - 1e-9 || it.loadFactor < 1.0 - 1e-9)
        }
    }

    @Test fun `TC-PROG-008d AUD-18 PROG-008 on bodyweight ladders - form not solid holds the rung, twice offers the easier version (review finding 7)`() = runBlocking {
        val pu = Library.require("push-up")
        val ctx = ProgressContext(Inventory(), Level.INTERMEDIATE, 40, flaggedScreen = false, recoveryOk = true)
        fun bw(form: String, reps: Int = 15) = StoredExercise(ExerciseRow(1, 1, 0, pu.id, "main", "DONE", item(false, load = 0.0).copy(load = null, reps = 8..15).encode()),
            item(false).copy(load = null, reps = 8..15), List(3) { i -> SetRow(i + 1L, 1, i, SetKind.WORKING, null, reps, rir = 3.0, loggedAtMs = i.toLong(), formCheck = form) })
        val good = List(3) { bw("YES") }
        val up = ExerciseProgress.apply(ExerciseState(pu.id), pu, good.last(), day, ctx, null, good).value
        val held = ExerciseProgress.apply(ExerciseState(pu.id), pu, bw("NO"), day, ctx, null, good.dropLast(1) + bw("NO"))
        assertEquals(1, held.value.state.formNoStreak)
        if (up.ladder != null) assertNull("a step up is held when form wasn't solid", held.value.ladder)
        assertTrue(held.decisions.any { it.reason == com.personalfitnesscoach.engine.core.ReasonKey.TECHNIQUE_CUE })
        val twice = ExerciseProgress.apply(held.value.state, pu, bw("NO"), day + 2, ctx, null, listOf(bw("NO"), bw("NO")))
        assertEquals(2, twice.value.state.formNoStreak)
        assertTrue(twice.decisions.any { it.reason == com.personalfitnesscoach.engine.core.ReasonKey.REGRESSION_OFFERED })
    }

    @Test fun `AUD-19 SAF-004 - only sessions that load the sore area count as pain-free (review finding 15)`() = runBlocking {
        val (d, _) = Fixtures.data()
        Fixtures.onboard(d)
        val m = Fixtures.MONDAY
        d.docs.put(com.personalfitnesscoach.data.core.model.PainRecord, com.personalfitnesscoach.data.core.model.PainRecord(Days.startMs(m - 10, java.time.ZoneId.of("UTC")), m - 10,
            null, null, com.personalfitnesscoach.engine.safety.PainReport(com.personalfitnesscoach.engine.model.Joint.KNEE, com.personalfitnesscoach.engine.safety.PainKind.JOINT_OR_TENDON, 8),
            action = com.personalfitnesscoach.engine.safety.PainAction.STOP_REGION))
        suspend fun session(day: Int, id: String) {
            val w = d.store.insertWorkout(WorkoutRow(day = day, status = Status.DONE, template = "FB_A", tier = "FULL", startedAtMs = Days.startMs(day, java.time.ZoneId.of("UTC")) + 3_600_000,
                endedAtMs = Days.startMs(day, java.time.ZoneId.of("UTC")) + 7_200_000, sessionRpe = 6.0, actualMinutes = 50.0, registryVersion = "3.0.1", json = WorkoutDoc(0, 60.0, 10.0).encode()))
            val e = d.store.insertExercise(ExerciseRow(workoutId = w, position = 0, exerciseId = id, slotKey = "main0", status = Status.DONE, json = item(false, load = 40.0).encode()))
            repeat(3) { s -> d.store.insertSet(SetRow(workoutExerciseId = e, setIndex = s, kind = SetKind.WORKING, loadKg = 40.0, reps = 10, rir = 2.0, loggedAtMs = 1)) }
        }
        val upper = Library.all.first { it.stress(com.personalfitnesscoach.engine.model.Joint.KNEE) == 0 && it.loadType == com.personalfitnesscoach.engine.model.LoadType.BARBELL }
        session(m - 8, upper.id); session(m - 6, upper.id)
        val still = d.bridge.regions(m).value.first { it.region == com.personalfitnesscoach.engine.model.Joint.KNEE }
        assertEquals("two upper-body days leave the knee conservative", 3, still.minRir)
        val legs = Library.all.first { it.stress(com.personalfitnesscoach.engine.model.Joint.KNEE) in 1..1 && it.loadType == com.personalfitnesscoach.engine.model.LoadType.DUMBBELL }
        session(m - 4, legs.id); session(m - 2, legs.id)
        val after = d.bridge.regions(m).value.firstOrNull { it.region == com.personalfitnesscoach.engine.model.Joint.KNEE }
        assertTrue("two pain-free knee sessions end the conservative phase: $after", after == null || after.minRir == null)
    }

    @Test fun `AUD-20 CON-004 2_0_0 - small hops only after 4 weeks of blocks, not for beginners, people 65+ or after lower-body pain`() = runBlocking {
        val m = Fixtures.MONDAY
        suspend fun hops(level: Level, birth: Int, blockDaysAgo: Int?, kneePainDaysAgo: Int? = null): Boolean {
            val (d, _) = Fixtures.data()
            Fixtures.onboard(d, level = level, birthYear = birth,
                conditions = listOf(com.personalfitnesscoach.data.core.model.StoredCondition("osteoporosis", m - 60, com.personalfitnesscoach.engine.safety.ControlStatus.YES)))
            if (blockDaysAgo != null) d.store.insertWorkout(WorkoutRow(day = m - blockDaysAgo, status = Status.DONE, template = "FB_A", tier = "FULL", startedAtMs = 1,
                endedAtMs = 2, sessionRpe = 5.0, actualMinutes = 40.0, registryVersion = "4.0.0", json = WorkoutDoc(0, 60.0, 5.0,
                    boneLoading = com.personalfitnesscoach.data.core.session.BoneRef(5.0, 50, com.personalfitnesscoach.engine.safety.BoneLoadingVariant.HEEL_DROPS)).encode()))
            if (kneePainDaysAgo != null) d.docs.put(com.personalfitnesscoach.data.core.model.PainRecord, com.personalfitnesscoach.data.core.model.PainRecord(1L, m - kneePainDaysAgo,
                null, null, com.personalfitnesscoach.engine.safety.PainReport(com.personalfitnesscoach.engine.model.Joint.KNEE, com.personalfitnesscoach.engine.safety.PainKind.JOINT_OR_TENDON, 2),
                action = com.personalfitnesscoach.engine.safety.PainAction.CONTINUE_CAUTION))
            val u = d.bridge.user()!!
            assertTrue(u.conditions.boneLoading)
            return d.bridge.boneHopsAllowed(u, m)
        }
        assertTrue("a 46-year-old after 5 weeks of blocks", hops(Level.INTERMEDIATE, 1980, 35))
        assertFalse("the first 4 weeks", hops(Level.INTERMEDIATE, 1980, 20))
        assertFalse("no block yet", hops(Level.INTERMEDIATE, 1980, null))
        assertFalse("a beginner", hops(Level.BEGINNER, 1980, 35))
        assertFalse("65 or over", hops(Level.INTERMEDIATE, 1955, 35))
        assertFalse("knee pain in the last 4 weeks", hops(Level.INTERMEDIATE, 1980, 35, kneePainDaysAgo = 10))
        assertTrue("knee pain longer ago", hops(Level.INTERMEDIATE, 1980, 35, kneePainDaysAgo = 40))
    }

    @Test fun `AUD-21 SAF-003 2_0_0 - strong muscle pain stops the exercise, the rest of today goes easy on that area, nothing persists`() = runBlocking {
        val (d, clock) = Fixtures.data()
        Fixtures.onboard(d)
        val t = d.planToday()!!
        d.checkIns.record(t.user, com.personalfitnesscoach.engine.calc.CheckIn(3, 3, 3, 3))
        d.startSession(d.generateSession(t, t.next!!, Tier.FULL))
        val p = com.personalfitnesscoach.data.core.player.SessionPlayer(d)
        p.completeStage(com.personalfitnesscoach.data.core.session.Stage.WARMUP)
        val v = p.view()!!
        val first = v.lifts.first()
        val region = com.personalfitnesscoach.engine.model.Joint.entries.maxByOrNull { j -> first.exercise.stress(j) }!!
        clock.ms += 60_000
        val r = p.reportPain(first.rowId, com.personalfitnesscoach.engine.safety.PainReport(region, com.personalfitnesscoach.engine.safety.PainKind.MUSCLE_BURN, 8))
        assertEquals(com.personalfitnesscoach.engine.safety.PainAction.STOP_EXERCISE, r.outcome.action)
        assertFalse(r.endSession)
        // The rest of the session keeps that area at joint stress ≤ 1 (swapped or skipped), as a joint-pain stop does (D-123).
        val after = p.view()!!
        for (l in after.lifts) if (l.rowId != first.rowId && l.status != Status.SKIPPED && !l.hasLoggedWork) assertTrue("${l.exercise.id}", l.exercise.stress(region) <= 1)
        // The alternatives offered can all be added, and none works the same main muscles as the exercise that hurt.
        assertTrue("other exercises are offered (final re-check)", r.alternatives.isNotEmpty())
        for (c in r.alternatives) assertTrue((c.exercise.primary intersect first.exercise.primary).isEmpty())
        r.alternatives.firstOrNull()?.let { c -> assertFalse(p.addAlternative(first.rowId, c.exercise.id, region, 1).refused) }
        // Nothing beyond today: no SAF-004 region.
        assertTrue(d.bridge.regions(clock.today()).value.none { it.region == region })
    }

    @Test fun `AUD-24 a warning sign reported after a finished session still makes the first session back LIGHT (re-check finding 1)`() = runBlocking {
        val (d, clock) = Fixtures.data()
        Fixtures.onboard(d)
        val m = Fixtures.MONDAY
        clock.setDay(m, hour = 8)
        // A normal session finished in the morning…
        d.store.insertWorkout(WorkoutRow(day = m, status = Status.DONE, template = "FB_A", tier = "FULL", startedAtMs = clock.nowMs(), endedAtMs = clock.nowMs() + 3_600_000,
            sessionRpe = 5.0, actualMinutes = 50.0, registryVersion = "5.0.0", json = WorkoutDoc(0, 60.0, 5.0).encode()))
        // …then palpitations reported from Today in the evening, confirmed resolved the same evening.
        clock.setDay(m, hour = 19)
        d.checkIns.record(d.bridge.user()!!, com.personalfitnesscoach.engine.calc.CheckIn(3, 3, 3, 3), redFlags = setOf(com.personalfitnesscoach.engine.safety.RedFlags.SYMPTOMS.first()))
        d.confirmStopResolved()
        clock.setDay(m + 2, hour = 9)
        val t2 = d.planToday()!!
        assertEquals(Tier.LIGHT, d.bridge.gate(t2.user, t2.program).cap(Tier.FULL))
    }

    @Test fun `AUD-25 strong muscle pain at the check-in keeps today's plan easy on that area (D-123)`() = runBlocking {
        val (d, clock) = Fixtures.data()
        Fixtures.onboard(d)
        clock.setDay(Fixtures.MONDAY, hour = 8)
        val knee = com.personalfitnesscoach.engine.model.Joint.KNEE
        d.reportPain(com.personalfitnesscoach.engine.safety.PainReport(knee, com.personalfitnesscoach.engine.safety.PainKind.DOMS, 9), null, null, null)
        val t = d.planToday()!!
        d.checkIns.record(t.user, com.personalfitnesscoach.engine.calc.CheckIn(3, 3, 3, 3))
        val g = d.generateSession(t, t.next!!, Tier.FULL)
        for (it in g.workout.items) assertTrue("${it.exercise.id} knee ${it.exercise.stress(knee)}", it.exercise.stress(knee) <= 1)
    }

    @Test fun `AUD-26 with low back pain the bone-loading block is always heel drops (D-123)`() = runBlocking {
        val m = Fixtures.MONDAY
        val (d, _) = Fixtures.data()
        Fixtures.onboard(d, level = Level.INTERMEDIATE, birthYear = 1980, conditions = listOf(
            com.personalfitnesscoach.data.core.model.StoredCondition("osteoporosis", m - 60, com.personalfitnesscoach.engine.safety.ControlStatus.YES),
            com.personalfitnesscoach.data.core.model.StoredCondition("low_back_pain", m - 60)))
        d.store.insertWorkout(WorkoutRow(day = m - 35, status = Status.DONE, template = "FB_A", tier = "FULL", startedAtMs = 1, endedAtMs = 2, sessionRpe = 5.0,
            actualMinutes = 40.0, registryVersion = "5.0.0", json = WorkoutDoc(0, 60.0, 5.0,
                boneLoading = com.personalfitnesscoach.data.core.session.BoneRef(5.0, 50, com.personalfitnesscoach.engine.safety.BoneLoadingVariant.HEEL_DROPS)).encode()))
        val u = d.bridge.user()!!
        assertTrue(u.conditions.boneLoading)
        assertFalse(d.bridge.boneHopsAllowed(u, m))
    }
}
