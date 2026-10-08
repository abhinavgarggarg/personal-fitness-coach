package com.personalfitnesscoach.engine.dose

import com.personalfitnesscoach.engine.library.ConditioningUnit
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.CostClass
import com.personalfitnesscoach.engine.model.DoseUnit
import com.personalfitnesscoach.engine.model.Exercise
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.LoadType
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.model.Muscle
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.planning.PlanItem
import com.personalfitnesscoach.engine.planning.Priority
import com.personalfitnesscoach.engine.planning.SessionPlan
import com.personalfitnesscoach.engine.planning.TimeBudget
import com.personalfitnesscoach.engine.planning.TimeModel
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun ex(id: String) = Library.require(id)

class RepsTest {
    @Test fun `TC-REP-001a strength range on loaded compounds is 3-6 reps at 80-88 percent`() {
        val s = Reps.forSlot(ex("back-squat"), DoseGoal.STRENGTH, Level.INTERMEDIATE).value
        assertEquals(3..6, s.range); assertEquals(RuleIds.REP_001, s.ruleId)
        assertEquals(80.0, s.pct1rm!!.start, 1e-9); assertEquals(88.0, s.pct1rm!!.endInclusive, 1e-9)
    }

    @Test fun `TC-REP-001b 1-3 reps only for advanced lifters, at RPE 8 or easier`() {
        val adv = Reps.forSlot(ex("deadlift"), DoseGoal.STRENGTH, Level.ADVANCED, advancedLowReps = true).value
        assertEquals(1..3, adv.range); assertEquals(2.0, adv.minRir!!, 1e-9)
        assertEquals(3..6, Reps.forSlot(ex("deadlift"), DoseGoal.STRENGTH, Level.INTERMEDIATE, advancedLowReps = true).value.range)
        // A strength goal on an accessory falls back to the hypertrophy range rather than heavy singles on a curl.
        assertEquals(RuleIds.REP_002, Reps.forSlot(ex("db-curl"), DoseGoal.STRENGTH, Level.ADVANCED).value.ruleId)
    }

    @Test fun `TC-REP-002a hypertrophy compounds 6-12, clipped to the exercise's own range`() {
        assertEquals(6..12, Reps.forSlot(ex("bench-press"), DoseGoal.HYPERTROPHY, Level.INTERMEDIATE).value.range)
        assertEquals(8..12, Reps.forSlot(ex("db-bench-press"), DoseGoal.HYPERTROPHY, Level.INTERMEDIATE).value.range)
    }

    @Test fun `TC-REP-002b hypertrophy isolation 8-20`() {
        assertEquals(12..20, Reps.forSlot(ex("db-lateral-raise"), DoseGoal.HYPERTROPHY, Level.BEGINNER).value.range)
        assertEquals(10..20, Reps.forSlot(ex("leg-extension"), DoseGoal.HYPERTROPHY, Level.BEGINNER).value.range)
        assertTrue(Reps.forSlot(ex("cable-fly"), DoseGoal.HYPERTROPHY, Level.BEGINNER).value.range.let { it.first >= 8 && it.last <= 20 })
    }

    @Test fun `TC-REP-003a endurance is 15-30 reps below 60 percent`() {
        val s = Reps.forSlot(ex("db-lateral-raise"), DoseGoal.ENDURANCE, Level.INTERMEDIATE).value
        assertEquals(15..25, s.range); assertEquals(60.0, s.pct1rm!!.endInclusive, 1e-9); assertEquals(RuleIds.REP_003, s.ruleId)
    }

    @Test fun `TC-REP-003b endurance holds are 30-60 s`() {
        val wallSit = Exercise("wall-sit", "Wall sit", Pattern.SQUAT, setOf(Muscle.QUADS), loadType = LoadType.TIME,
            costClass = CostClass.ISOLATION_OR_CORE, unit = DoseUnit.SECONDS, defaultRepRange = 20..40)
        val s = Reps.forSlot(wallSit, DoseGoal.ENDURANCE, Level.BEGINNER).value
        assertEquals(30..60, s.range); assertEquals(DoseUnit.SECONDS, s.unit)
    }

    @Test fun `TC-REP-004a power is 3-5 reps at 30-70 percent with fewer than 24 total reps`() {
        val s = Reps.forSlot(ex("kb-swing"), DoseGoal.POWER, Level.INTERMEDIATE).value
        assertEquals(3..5, s.range); assertEquals(23, s.maxTotalReps)
        assertEquals(30.0, s.pct1rm!!.start, 1e-9); assertEquals(70.0, s.pct1rm!!.endInclusive, 1e-9)
        assertEquals(4, Reps.maxPowerSets(5)); assertEquals(7, Reps.maxPowerSets(3))
        assertTrue(Reps.maxPowerSets(5) * 5 < 24)
    }

    @Test fun `TC-REP-004b power dosing only on power-capable exercises and stops when speed drops`() {
        assertNotEquals(RuleIds.REP_004, Reps.forSlot(ex("back-squat"), DoseGoal.POWER, Level.ADVANCED).value.ruleId)
        assertTrue(Reps.stopPowerSet(speedDropped = true))
        assertFalse(Reps.stopPowerSet(speedDropped = false))
    }

    @Test fun `TC-REP-005a core holds 10-45 s`() {
        val s = Reps.forSlot(ex("front-plank"), DoseGoal.HYPERTROPHY, Level.BEGINNER).value
        assertEquals(20..45, s.range); assertEquals(DoseUnit.SECONDS, s.unit); assertEquals(RuleIds.REP_005, s.ruleId)
        assertTrue(s.range.first >= P.REP_005.hold_seconds[0] && s.range.last <= P.REP_005.hold_seconds[1])
    }

    @Test fun `TC-REP-005b core reps 6-12 per side`() {
        val s = Reps.forSlot(ex("pallof-standing"), DoseGoal.STRENGTH, Level.ADVANCED).value
        assertEquals(8..12, s.range); assertTrue(s.perSide); assertEquals(RuleIds.REP_005, s.ruleId)
    }

    @Test fun `TC-REP-006a carries are dosed in metres`() {
        val s = Reps.forSlot(ex("farmer-carry"), DoseGoal.HYPERTROPHY, Level.BEGINNER).value
        assertEquals(DoseUnit.METRES, s.unit); assertEquals(20..40, s.range); assertEquals(RuleIds.REP_006, s.ruleId)
    }

    @Test fun `TC-REP-006b every allowed modality has registry units`() {
        val allowed = P.REP_006.units.map { ConditioningUnit.valueOf(it.uppercase()) }.toSet()
        for (m in Modality.entries.filter { !it.excluded }) {
            val u = Reps.conditioningUnits(m)
            assertTrue(m.name, u.isNotEmpty() && allowed.containsAll(u))
        }
        assertEquals(listOf(ConditioningUnit.METRES), Reps.conditioningUnits(Modality.SLED))
        assertTrue(Reps.conditioningUnits(Modality.TREADMILL_RUN).isEmpty())
    }
}

class RestTest {
    @Test fun `TC-REST-001a heavy compounds rest 120-300 s, 180 by default`() {
        val w = Rest.forSets(ex("back-squat"), reps = 5)
        assertEquals(RestWindow(120, 180, 300, RuleIds.REST_001), w)
    }

    @Test fun `TC-REST-001b heavy means 6 reps or fewer, or 80 percent or more`() {
        assertEquals(RuleIds.REST_001, Rest.forSets(ex("bench-press"), reps = 8, pct1rm = 82.0).ruleId)
        assertEquals(RuleIds.REST_002, Rest.forSets(ex("bench-press"), reps = 8, pct1rm = 70.0).ruleId)
        assertEquals(RuleIds.REST_001, Rest.forSets(ex("bench-press"), reps = 6).ruleId)
    }

    @Test fun `TC-REST-002a moderate compounds rest 90-150 s`() {
        assertEquals(RestWindow(90, 120, 150, RuleIds.REST_002), Rest.forSets(ex("db-bench-press"), reps = 10))
    }

    @Test fun `TC-REST-002b a compound at 12 reps is still moderate, not an accessory`() {
        assertEquals(RuleIds.REST_002, Rest.forSets(ex("leg-press"), reps = 12).ruleId)
    }

    @Test fun `TC-REST-003a isolation rests 60-90 s`() {
        assertEquals(RestWindow(60, 75, 90, RuleIds.REST_003), Rest.forSets(ex("db-curl"), reps = 12))
    }

    @Test fun `TC-REST-003b low-cost bodyweight work is dosed like an accessory`() {
        assertEquals(RuleIds.REST_003, Rest.forSets(ex("push-up"), reps = 12).ruleId)
    }

    @Test fun `TC-REST-004a supersets rest 45-75 s between partners`() {
        assertEquals(RestWindow(45, 60, 75, RuleIds.REST_004), Rest.superset)
    }

    @Test fun `TC-REST-004b the time model uses the superset rest between paired sets`() {
        val a = PlanItem("a", Priority.P3, sets = 2, reps = 10, setupSec = 0)
        val b = PlanItem("b", Priority.P4, sets = 2, reps = 10, setupSec = 0)
        assertEquals(4 * 45.0 + 3 * P.REST_004.default_s, TimeModel.pairSeconds(a, b), 1e-9)
    }

    @Test fun `TC-REST-005a circuits rest 15-45 s`() {
        assertEquals(RestWindow(15, 30, 45, RuleIds.REST_005), Rest.circuit)
    }

    @Test fun `TC-REST-005b circuit timer statuses`() {
        assertEquals(RestStatus.TOO_SHORT, Rest.status(10, Rest.circuit))
        assertEquals(RestStatus.IN_RANGE, Rest.status(20, Rest.circuit))
        assertEquals(RestStatus.ALERT, Rest.status(30, Rest.circuit))
    }

    @Test fun `TC-REST-006a core rests 30-60 s`() {
        assertEquals(RestWindow(30, 45, 60, RuleIds.REST_006), Rest.forSets(ex("pallof-standing"), reps = 10))
    }

    @Test fun `TC-REST-006b planks, chops and carries-for-core use the core rest`() {
        assertEquals(RuleIds.REST_006, Rest.forSets(ex("side-plank"), reps = 30).ruleId)
        assertEquals(RuleIds.REST_006, Rest.forSets(ex("cable-chop-standing"), reps = 10).ruleId)
    }

    @Test fun `TC-REST-007a start anywhere in the window, alert at the default`() {
        val w = Rest.heavy
        assertEquals(RestStatus.TOO_SHORT, Rest.status(100, w))
        assertEquals(RestStatus.IN_RANGE, Rest.status(150, w))
        assertEquals(RestStatus.ALERT, Rest.status(180, w))
        assertEquals(RestStatus.LONG, Rest.status(301, w))
    }

    @Test fun `TC-REST-007b rests compress P5 first and heavy lifts last, never below minimum`() {
        assertEquals(listOf(Priority.P5, Priority.P4, Priority.P3, Priority.P1), Rest.compressionOrder)
        val main = PlanItem("squat", Priority.P1, sets = 4, reps = 5, restSec = 180, minRestSec = 120, setupSec = 45, station = "rack")
        val acc = PlanItem("curl", Priority.P4, sets = 3, reps = 12, restSec = 75, minRestSec = 60, setupSec = 10, station = "dumbbells")
        val fit = TimeBudget.fit(SessionPlan(5.0, 2.0, listOf(main, acc)), minutes = 15.0).value
        for (i in fit.plan.items) assertTrue(i.id, i.restSec >= i.minRestSec)
        assertTrue(fit.plan.items.first { it.id == "squat" }.restSec >= P.REST_001.min_s)
    }
}

class EffortTest {
    @Test fun `TC-INT-002a beginners in their first 8 weeks aim for RIR 3 on compounds and 2 on isolation`() {
        assertEquals(3.0, Effort.targetRir(Level.BEGINNER, true, weeksTraining = 3, blockWeek = 3, blockLoadingWeeks = 5).value, 1e-9)
        assertEquals(2.0, Effort.targetRir(Level.BEGINNER, false, weeksTraining = 3, blockWeek = 3, blockLoadingWeeks = 5).value, 1e-9)
        // The last loading week never drops below the floor, which is the fixed beginner value.
        assertEquals(3.0, Effort.targetRir(Level.BEGINNER, true, 3, blockWeek = 5, blockLoadingWeeks = 5).value, 1e-9)
        // After week 8 beginners use the intermediate ranges (D-047).
        assertEquals(1.0..3.0, Effort.range(Level.BEGINNER, true, weeksTraining = 10))
    }

    @Test fun `TC-INT-002b week 1 adds one RIR, the last loading week removes one, deload and LIGHT stay at 3 or more`() {
        assertEquals(3.0, Effort.targetRir(Level.INTERMEDIATE, true, 40, blockWeek = 1, blockLoadingWeeks = 5).value, 1e-9)
        assertEquals(2.0, Effort.targetRir(Level.INTERMEDIATE, true, 40, blockWeek = 3, blockLoadingWeeks = 5).value, 1e-9)
        assertEquals(1.0, Effort.targetRir(Level.INTERMEDIATE, true, 40, blockWeek = 5, blockLoadingWeeks = 5).value, 1e-9)
        assertEquals(1.0, Effort.targetRir(Level.ADVANCED, true, 40, blockWeek = 5, blockLoadingWeeks = 5).value, 1e-9)
        assertEquals(0.0, Effort.targetRir(Level.INTERMEDIATE, false, 40, blockWeek = 5, blockLoadingWeeks = 5).value, 1e-9)
        assertEquals(3.0, Effort.targetRir(Level.ADVANCED, false, 40, blockWeek = 3, blockLoadingWeeks = 5, lightOrDeload = true).value, 1e-9)
    }

    @Test fun `TC-INT-006a beginners answer how many more reps they could have done for 4 weeks`() {
        for (w in 0 until 4) assertEquals(Effort.PromptStyle.RIR_QUESTION, Effort.promptStyle(Level.BEGINNER, w))
        assertEquals(listOf("0", "1", "2", "3", "4+"), Effort.rirOptions)
    }

    @Test fun `TC-INT-006b RPE words arrive gradually and 4+ still counts as a hard set`() {
        assertEquals(Effort.PromptStyle.RIR_WITH_RPE, Effort.promptStyle(Level.BEGINNER, 5))
        assertEquals(Effort.PromptStyle.RPE, Effort.promptStyle(Level.BEGINNER, 9))
        assertEquals(Effort.PromptStyle.RPE, Effort.promptStyle(Level.INTERMEDIATE, 0))
        assertEquals(4.0, Effort.rirFromAnswer("4+")!!, 1e-9)
        assertTrue(Effort.rirFromAnswer("4+")!! <= P.VOL_001.max_rir)
        assertNull(Effort.rirFromAnswer("lots"))
    }
}

class OrderTest {
    private data class It(val id: String, val slot: OrderSlot, val weak: Boolean = false)

    @Test fun `TC-ORD-001a sessions run warm-up, power, primary, secondary, accessories, core, conditioning, cool-down`() {
        assertEquals(listOf(OrderSlot.WARMUP, OrderSlot.POWER, OrderSlot.PRIMARY_COMPOUND, OrderSlot.SECONDARY_COMPOUND,
            OrderSlot.ACCESSORIES, OrderSlot.CORE, OrderSlot.CONDITIONING, OrderSlot.COOLDOWN), Order.sequence(false))
        val shuffled = listOf(It("row", OrderSlot.CONDITIONING), It("plank", OrderSlot.CORE), It("squat", OrderSlot.PRIMARY_COMPOUND),
            It("jump", OrderSlot.POWER), It("curl", OrderSlot.ACCESSORIES), It("rdl", OrderSlot.SECONDARY_COMPOUND))
        assertEquals(listOf("jump", "squat", "rdl", "curl", "plank", "row"), Order.sort(shuffled, { it.slot }).map { it.id })
    }

    @Test fun `TC-ORD-001b items in the same step keep their order`() {
        val items = listOf(It("b", OrderSlot.ACCESSORIES), It("a", OrderSlot.ACCESSORIES), It("c", OrderSlot.PRIMARY_COMPOUND))
        assertEquals(listOf("c", "b", "a"), Order.sort(items, { it.slot }).map { it.id })
    }

    @Test fun `TC-ORD-002a conditioning-priority days put conditioning third and cut lower-body volume 30 percent`() {
        val seq = Order.sequence(true)
        assertEquals(OrderSlot.CONDITIONING, seq[2])
        assertEquals(0.7, Order.conditioningPriorityLowerVolumeFactor, 1e-9)
    }

    @Test fun `TC-ORD-002b a weak point may precede secondary compounds`() {
        val items = listOf(It("rdl", OrderSlot.SECONDARY_COMPOUND), It("calf", OrderSlot.ACCESSORIES, weak = true), It("squat", OrderSlot.PRIMARY_COMPOUND))
        assertEquals(listOf("squat", "calf", "rdl"), Order.sort(items, { it.slot }, weakPoint = { it.weak }).map { it.id })
    }

    @Test fun `TC-ORD-003a only push+pull, upper+lower and compound+core pairs, never heavy main lifts at RIR 2 or less`() {
        assertTrue(Order.classesCompatible(PairClass.PUSH, PairClass.PULL))
        assertTrue(Order.classesCompatible(PairClass.LOWER, PairClass.PUSH))
        assertTrue(Order.classesCompatible(PairClass.CORE, PairClass.LOWER))
        assertFalse(Order.classesCompatible(PairClass.PUSH, PairClass.PUSH))
        assertFalse(Order.classesCompatible(PairClass.CORE, PairClass.CORE))
        assertFalse(Order.classesCompatible(PairClass.NONE, PairClass.PULL))
        assertEquals(PairClass.NONE, Order.pairClass(ex("farmer-carry")))
        assertEquals(PairClass.PULL, Order.pairClass(ex("db-curl")))
        assertEquals(PairClass.PUSH, Order.pairClass(ex("cable-pushdown")))
        assertFalse(Order.supersetEligible(main = true, targetRir = 2.0))
        assertTrue(Order.supersetEligible(main = true, targetRir = 3.0))
        assertTrue(Order.supersetEligible(main = false, targetRir = 0.0))
    }

    @Test fun `TC-ORD-003b a crowded gym never pairs two fixed stations`() {
        val cable = PlanItem("pushdown", Priority.P4, sets = 3, reps = 12, station = "cable", primaryMuscles = setOf(Muscle.TRICEPS), pairClass = PairClass.PUSH)
        val machine = PlanItem("leg-curl", Priority.P4, sets = 3, reps = 12, station = "leg_curl", primaryMuscles = setOf(Muscle.HAMSTRINGS), pairClass = PairClass.LOWER)
        val db = PlanItem("db-row", Priority.P4, sets = 3, reps = 12, station = "dumbbells", primaryMuscles = setOf(Muscle.LATS), pairClass = PairClass.PULL)
        assertTrue(TimeBudget.pairSupersets(listOf(cable, machine), crowded = true).none { it.pairedWith != null })
        assertTrue(TimeBudget.pairSupersets(listOf(cable, machine), crowded = false).all { it.pairedWith != null })
        val paired = TimeBudget.pairSupersets(listOf(db, cable), crowded = true)
        assertTrue(paired.all { it.pairedWith != null })
        assertTrue(paired.all { it.station == "cable" }) // the pair runs at the fixed station
        // Two pushes never pair even when the muscles differ.
        val press = PlanItem("press", Priority.P3, sets = 3, reps = 10, station = "dumbbells", primaryMuscles = setOf(Muscle.FRONT_DELTS), pairClass = PairClass.PUSH)
        assertTrue(TimeBudget.pairSupersets(listOf(press, cable)).none { it.pairedWith != null })
    }
}
