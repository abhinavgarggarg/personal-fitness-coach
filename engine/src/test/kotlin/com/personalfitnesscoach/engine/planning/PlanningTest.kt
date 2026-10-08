package com.personalfitnesscoach.engine.planning

import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.model.CostClass
import com.personalfitnesscoach.engine.model.Exercise
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.LoadType
import com.personalfitnesscoach.engine.model.Muscle
import com.personalfitnesscoach.engine.model.Objective
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.registry.P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** The Phase 1 section 32 example: a 60-minute lower-body-led session. */
object Example60 {
    val squat = PlanItem("squat", Priority.P1, sets = 4, reps = 5, restSec = 180, minRestSec = 120, setupSec = 45, station = "rack", primaryMuscles = setOf(Muscle.QUADS, Muscle.GLUTES))
    val bench = PlanItem("db-bench", Priority.P3, sets = 3, reps = 10, restSec = 120, minRestSec = 90, setupSec = 30, station = "bench", primaryMuscles = setOf(Muscle.CHEST))
    val row = PlanItem("cable-row", Priority.P4, sets = 3, reps = 12, restSec = 75, minRestSec = 60, setupSec = 20, station = "cable", primaryMuscles = setOf(Muscle.LATS, Muscle.UPPER_BACK))
    val rdl = PlanItem("rdl", Priority.P3, sets = 3, reps = 10, restSec = 120, minRestSec = 90, setupSec = 30, station = "rack", primaryMuscles = setOf(Muscle.HAMSTRINGS, Muscle.GLUTES))
    val raise = PlanItem("lateral-raise", Priority.P4, sets = 3, reps = 15, restSec = 60, minRestSec = 60, setupSec = 15, tempoSecPerRep = 3.0, station = "dumbbells", primaryMuscles = setOf(Muscle.SIDE_DELTS))
    val pallof = PlanItem("pallof", Priority.P5, sets = 2, reps = 20, restSec = 45, minRestSec = 30, setupSec = 15, tempoSecPerRep = 2.0, station = "cable", primaryMuscles = setOf(Muscle.CORE))
    val rower = PlanItem("rower-30-30", Priority.P2, station = "rower", setupSec = 30, conditioning = Conditioning(30, 30, 6))
    val plan = SessionPlan(warmupMin = 10.0, cooldownMin = 2.0, items = listOf(squat, bench, row, rdl, raise, pallof, rower))
}

class TimeBudgetTest {
    @Test fun `TC-TIME-004a set time is reps x tempo + 10 s, plus setup and rests`() {
        assertEquals(27.5, TimeModel.setSeconds(Example60.squat), 1e-9)
        assertEquals(45 + 4 * 27.5 + 3 * 180.0, TimeModel.itemSeconds(Example60.squat), 1e-9)
        assertEquals(30 + 6 * 60.0, TimeModel.itemSeconds(Example60.rower), 1e-9)
    }

    @Test fun `TC-TIME-004b 5 percent buffer and a personal factor bounded 0-8 to 1-3`() {
        val bare = SessionPlan(0.0, 0.0, listOf(Example60.squat))
        assertEquals(TimeModel.itemSeconds(Example60.squat) * 1.05 / 60.0, TimeModel.minutes(bare), 1e-9)
        assertEquals(TimeModel.minutes(bare) * 1.3, TimeModel.minutes(bare, personalFactor = 2.0), 1e-9)
        assertEquals(1.3, TimeModel.updatePersonalFactor(1.25, 60.0, 120.0), 1e-9)
        assertEquals(0.8, TimeModel.updatePersonalFactor(0.82, 60.0, 20.0), 1e-9)
    }

    @Test fun `TC-TIME-001a the Phase 1 plan models at about 67 minutes under TIME-004 and is left alone when time allows`() {
        // Phase 1 listed this plan as 60 min with illustrative per-item minutes; the TIME-004 model adds
        // station changes and the 5% buffer (decision D-034). Real durations tune it via the personal factor.
        val m = TimeModel.minutes(Example60.plan)
        assertEquals(67.4, m, 0.05)
        val r = TimeBudget.fit(Example60.plan, 70.0)
        assertTrue(r.value.fits)
        assertEquals(Example60.plan, r.value.plan)
        assertTrue(r.decisions.isEmpty())
    }

    @Test fun `TC-TIME-001b cool-down is at least 2 minutes and the warm-up survives extreme compression`() {
        val r = TimeBudget.fit(Example60.plan.copy(cooldownMin = 0.0), 12.0).value
        assertTrue(r.plan.cooldownMin >= 2.0)
        assertTrue(r.plan.warmupMin >= 5.0)
        assertTrue("P1 is never dropped", r.plan.items.any { it.id == "squat" && it.sets >= 2 })
    }

    @Test fun `TC-TIME-002a Phase 1 example - compressed to 30 minutes in TIME-002 order`() {
        val r = TimeBudget.fit(Example60.plan, 30.0)
        val f = r.value
        assertTrue("fits (${f.minutes} min)", f.fits && f.minutes <= 30.0)
        assertFalse(f.expressOffered)
        val byId = f.plan.items.associateBy { it.id }
        assertEquals(5.0, f.plan.warmupMin, 1e-9)                               // warm-up to its floor
        assertEquals(2.0, f.plan.cooldownMin, 1e-9)
        assertEquals(3, byId.getValue("squat").sets)                            // P1 kept: 3 × 5 (as in Phase 1)
        assertTrue(byId.getValue("squat").restSec >= P.REST_001.min_s)
        assertEquals(2, byId.getValue("db-bench").sets)                         // P3 −1 set
        assertEquals(2, byId.getValue("rdl").sets)
        assertEquals(4, byId.getValue("rower-30-30").conditioning!!.rounds)     // P2: 6 → 4 rounds (≤ 40% cut)
        assertEquals(listOf("pallof", "lateral-raise", "cable-row"), f.dropped) // P5 first, then P4 lowest first
        assertEquals(ReasonKey.TIME_COMPRESSED, r.decisions.first().reason)
    }

    @Test fun `TC-TIME-002c supersets pair only non-competing P3 to P5 strength items`() {
        val chestFly = PlanItem("fly", Priority.P4, sets = 3, reps = 12, restSec = 75, minRestSec = 60, station = "cable", primaryMuscles = setOf(Muscle.CHEST))
        val paired = TimeBudget.pairSupersets(listOf(Example60.squat, Example60.bench, chestFly, Example60.row, Example60.rower))
        val byId = paired.associateBy { it.id }
        assertNull(byId.getValue("squat").pairedWith)                     // P1 never paired
        assertEquals("cable-row", byId.getValue("db-bench").pairedWith)  // chest + back: non-competing
        assertNull(byId.getValue("fly").pairedWith)                       // no chest + chest pairs
        assertEquals(listOf("squat", "db-bench", "cable-row", "fly", "rower-30-30"), paired.map { it.id })
    }

    @Test fun `TC-TIME-002b under 20 minutes an express session is offered, and invariants hold for random plans`() {
        assertTrue(TimeBudget.fit(Example60.plan, 15.0).value.expressOffered)
        val rnd = Random(42)
        repeat(400) {
            val items = (0 until 3 + rnd.nextInt(6)).map { i ->
                val pr = Priority.entries[rnd.nextInt(5)]
                if (pr == Priority.P2) PlanItem("c$i", pr, station = "s${rnd.nextInt(3)}", conditioning = Conditioning(30 + rnd.nextInt(60), rnd.nextInt(60), 2 + rnd.nextInt(8)))
                else PlanItem("e$i", pr, sets = 1 + rnd.nextInt(5), reps = 5 + rnd.nextInt(11), restSec = 60 + rnd.nextInt(180), minRestSec = 45,
                    station = "s${rnd.nextInt(4)}", primaryMuscles = setOf(Muscle.entries[rnd.nextInt(Muscle.entries.size)]))
            }
            val plan = SessionPlan(8.0 + rnd.nextInt(5), rnd.nextInt(4).toDouble(), items)
            val budget = 15.0 + rnd.nextInt(60)
            val f = TimeBudget.fit(plan, budget).value
            assertTrue(f.plan.warmupMin >= 5.0 && f.plan.cooldownMin >= 2.0)
            assertTrue(f.fits == (f.minutes <= budget + 0.05))
            for (orig in items.filter { it.priority == Priority.P1 }) {
                val now = f.plan.items.single { it.id == orig.id }
                assertTrue(now.sets >= minOf(2, orig.sets) && now.restSec >= now.minRestSec)
            }
            for (orig in items.filter { it.priority == Priority.P2 }) {
                val now = f.plan.items.single { it.id == orig.id }.conditioning!!
                val was = orig.conditioning!!
                assertTrue(now.rounds * now.workSec >= was.rounds * was.workSec * 0.6 - 1e-9)
            }
            // A P3 item is only cut after every P4/P5 item is gone.
            val p3cut = items.filter { it.priority == Priority.P3 }.any { o -> f.plan.items.single { it.id == o.id }.sets < o.sets }
            if (p3cut) assertTrue(f.plan.items.none { it.priority >= Priority.P4 })
            assertTrue(f.dropped.all { id -> items.single { it.id == id }.priority >= Priority.P4 })
        }
    }

    @Test fun `TC-TIME-003a extra time adds accessory sets first, then Z1 minutes`() {
        val r = TimeBudget.extend(Example60.plan, 80.0, sessionSetCap = 25)
        val row = r.value.items.single { it.id == "cable-row" }
        assertTrue(row.sets > 3)
        assertTrue(r.value.z1Min >= 0.0)
        assertTrue(TimeModel.minutes(r.value) <= 80.0)
        assertEquals(ReasonKey.TIME_EXTENDED, r.decisions.single().reason)
    }

    @Test fun `TC-TIME-003b extension never breaks the session set cap`() {
        for (cap in listOf(18, 20, 25)) {
            val r = TimeBudget.extend(Example60.plan, 120.0, sessionSetCap = cap).value
            assertTrue(r.items.filter { !it.isConditioning }.sumOf { it.sets } <= maxOf(cap, 18))
        }
    }
}

private fun ex(
    id: String, pattern: Pattern, primary: Set<Muscle>, secondary: Set<Muscle>, equipment: Set<String>, loadType: LoadType,
    difficulty: Int = 2, fatigue: Int = 2, joints: Map<Joint, Int> = mapOf(Joint.SPINE to 1, Joint.SHOULDER to 1, Joint.ELBOW to 1),
    skill: Int = 2, tags: Set<String> = emptySet(),
) = Exercise(id, id, pattern, primary, secondary, equipment, loadType, CostClass.MACHINE_OR_CABLE_COMPOUND, difficulty = difficulty, skill = skill,
    jointStress = joints, fatigueSystemic = fatigue, objectives = setOf(Objective.HYPERTROPHY, Objective.STRENGTH), limitationTags = tags)

private val BACK = setOf(Muscle.LATS, Muscle.UPPER_BACK)
private val CABLE_ROW = ex("cable-row", Pattern.HORIZONTAL_PULL, BACK, setOf(Muscle.BICEPS, Muscle.REAR_DELTS), setOf("cable_row"), LoadType.STACK)
private val MACHINE_ROW = ex("machine-row", Pattern.HORIZONTAL_PULL, BACK, setOf(Muscle.BICEPS), setOf("machine_row"), LoadType.STACK)
private val CS_DB_ROW = ex("chest-supported-db-row", Pattern.HORIZONTAL_PULL, BACK, setOf(Muscle.BICEPS), setOf("dumbbells", "bench"), LoadType.DUMBBELL)
private val INVERTED_ROW = ex("inverted-row", Pattern.HORIZONTAL_PULL, BACK, setOf(Muscle.BICEPS), setOf("rack"), LoadType.BODYWEIGHT, difficulty = 3,
    joints = mapOf(Joint.SPINE to 1, Joint.SHOULDER to 2, Joint.ELBOW to 1))
private val BB_ROW = ex("barbell-row", Pattern.HORIZONTAL_PULL, BACK, setOf(Muscle.BICEPS), setOf("barbell"), LoadType.BARBELL, difficulty = 3, fatigue = 4,
    joints = mapOf(Joint.SPINE to 3, Joint.SHOULDER to 1, Joint.ELBOW to 1))
private val PULLDOWN = ex("lat-pulldown", Pattern.VERTICAL_PULL, setOf(Muscle.LATS), setOf(Muscle.BICEPS, Muscle.UPPER_BACK), setOf("lat_pulldown"), LoadType.STACK)
private val GOBLET = ex("goblet-squat", Pattern.SQUAT, setOf(Muscle.QUADS), setOf(Muscle.GLUTES), setOf("dumbbells"), LoadType.DUMBBELL)
private val BIKE = ex("bike-intervals", Pattern.ISOLATION, setOf(Muscle.QUADS), emptySet(), setOf("stationary_bike"), LoadType.TIME)
private val LIBRARY = listOf(CABLE_ROW, MACHINE_ROW, CS_DB_ROW, INVERTED_ROW, BB_ROW, PULLDOWN, GOBLET, BIKE)
private val GYM = setOf("cable_row", "machine_row", "dumbbells", "bench", "rack", "barbell", "lat_pulldown", "stationary_bike")

class SubstitutionTest {
    private val ctx = SubContext(GYM, Level.INTERMEDIATE)

    @Test fun `TC-SUB-001a unavailable, excluded, too-skilled, painful and MOD-001 options are filtered out`() {
        assertFalse(Substitution.passesFilters(BIKE, CABLE_ROW, ctx))                                        // MOD-001
        assertFalse(Substitution.passesFilters(MACHINE_ROW, CABLE_ROW, ctx.copy(equipmentToday = GYM - "machine_row")))
        assertFalse(Substitution.passesFilters(MACHINE_ROW, CABLE_ROW, ctx.copy(excludedIds = setOf("machine-row"))))
        assertFalse(Substitution.passesFilters(BB_ROW, CABLE_ROW, ctx.copy(jointLimits = mapOf(Joint.SPINE to 2))))
        assertFalse(Substitution.passesFilters(MACHINE_ROW.copy(skill = 4), CABLE_ROW, ctx.copy(level = Level.BEGINNER)))
        assertTrue(Substitution.passesFilters(MACHINE_ROW.copy(skill = 3), CABLE_ROW, ctx.copy(level = Level.INTERMEDIATE)))
        assertFalse(Substitution.passesFilters(CABLE_ROW, CABLE_ROW, ctx))
    }

    @Test fun `TC-SUB-001b limitation tags block candidates`() {
        val tagged = MACHINE_ROW.copy(limitationTags = setOf("shoulder_overhead_pain"))
        assertFalse(Substitution.passesFilters(tagged, CABLE_ROW, ctx.copy(blockedTags = setOf("shoulder_overhead_pain"))))
        assertTrue(Substitution.passesFilters(tagged, CABLE_ROW, ctx))
    }

    @Test fun `TC-SUB-002a Phase 1 component values give 0-955 and 0-760`() {
        assertEquals(0.955, Substitution.score(Fits(1.0, 1.0, 1.0, 0.7, 1.0, 1.0, 1.0, 0.5)), 1e-9)
        assertEquals(0.760, Substitution.score(Fits(0.5, 1.0, 0.7, 0.7, 1.0, 1.0, 1.0, 0.5)), 1e-9)
        assertEquals(1.0, P.SUB_002.weights.run { pattern + objective + muscle + equipment + joint + difficulty + fatigue + preference }, 1e-9)
    }

    @Test fun `TC-SUB-002b occupied cable row ranks like the Phase 1 example and never auto-picks another pattern`() {
        val r = Substitution.options(CABLE_ROW, LIBRARY, ctx, top = 5)
        assertEquals(listOf("machine-row", "chest-supported-db-row", "inverted-row", "barbell-row", "lat-pulldown"), r.value.ranked.map { it.exercise.id })
        assertEquals(0.955, r.value.ranked[0].score, 1e-9)
        assertEquals("machine-row", r.value.autoPick!!.exercise.id)
        val onlySquat = Substitution.options(CABLE_ROW, listOf(GOBLET), ctx)
        assertNull(onlySquat.value.autoPick)
        assertEquals(ReasonKey.SWAP_OFFERED_ONLY, onlySquat.decisions.single().reason)
        assertEquals(ReasonKey.SWAP_NONE_AVAILABLE, Substitution.options(CABLE_ROW, listOf(BIKE), ctx).decisions.single().reason)
    }

    @Test fun `TC-SUB-003a accept and reject move preference by 0-05`() {
        assertEquals(0.55, Substitution.updatePreference(0.5, true), 1e-9)
        assertEquals(0.45, Substitution.updatePreference(0.5, false), 1e-9)
    }

    @Test fun `TC-SUB-003b preference stays within 0 to 1 and never beats pattern`() {
        assertEquals(1.0, Substitution.updatePreference(1.0, true), 1e-9)
        assertEquals(0.0, Substitution.updatePreference(0.0, false), 1e-9)
        val c = ctx.copy(preferences = mapOf("lat-pulldown" to 1.0, "barbell-row" to 0.0))
        val ids = Substitution.options(CABLE_ROW, LIBRARY, c, top = 5).value.ranked.map { it.exercise.id }
        assertTrue(ids.indexOf("barbell-row") < ids.indexOf("lat-pulldown"))
    }

    @Test fun `TC-SUB-001c property - every offered candidate passes the hard filters`() {
        val rnd = Random(99)
        repeat(500) {
            val equipment = GYM.filter { rnd.nextBoolean() }.toSet()
            val limits = Joint.entries.filter { rnd.nextInt(4) == 0 }.associateWith { rnd.nextInt(4) }
            val c = SubContext(equipment, Level.entries[rnd.nextInt(3)], jointLimits = limits)
            val orig = LIBRARY[rnd.nextInt(LIBRARY.size)]
            val r = Substitution.options(orig, LIBRARY, c).value
            for (cand in r.ranked) {
                assertTrue(Substitution.passesFilters(cand.exercise, orig, c))
                assertTrue(cand.score in 0.0..1.0)
                assertFalse(cand.exercise.equipment.any { it in Substitution.MOD001_EQUIPMENT })
            }
            r.autoPick?.let { assertTrue(it.fits.pattern >= 0.5) }
        }
    }
}
