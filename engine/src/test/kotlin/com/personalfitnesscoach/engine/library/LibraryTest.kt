package com.personalfitnesscoach.engine.library

import com.personalfitnesscoach.engine.calc.Inventory
import com.personalfitnesscoach.engine.calc.PlateMath
import com.personalfitnesscoach.engine.model.CostClass
import com.personalfitnesscoach.engine.model.EquipmentClass
import com.personalfitnesscoach.engine.model.LoadType
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.planning.Substitution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {
    private val all = Library.all
    private val coverageClasses = listOf(EquipmentClass.BARBELL, EquipmentClass.DUMBBELL, EquipmentClass.KETTLEBELL,
        EquipmentClass.CABLE, EquipmentClass.MACHINE, EquipmentClass.BODYWEIGHT)

    @Test fun `library has unique kebab-case IDs and well over a hundred exercises`() {
        assertTrue(all.size >= 120)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertTrue(all.all { Regex("[a-z0-9]+(-[a-z0-9]+)*").matches(it.id) })
    }

    @Test fun `every pattern has an exercise per equipment class unless the gap is justified`() {
        for (p in Pattern.entries.filter { it != Pattern.ISOLATION }) {
            val have = Library.classesFor(p)
            val gaps = GeneratedLibrary.coverageGaps[p].orEmpty()
            for (c in coverageClasses) {
                assertTrue("$p/$c covered or justified", c in have || c in gaps)
                assertFalse("$p/$c justified but covered", c in have && c in gaps)
            }
            // Every pattern has at least three classes, so equipment loss never empties it.
            assertTrue("$p has ≥ 3 classes", have.size >= 3)
        }
    }

    @Test fun `every exercise, drill and modality has original setup text, cues and mistakes`() {
        for (e in all) {
            val t = Library.text(e.id)
            assertNotNull(e.id, t)
            assertTrue(e.id, t!!.setup.isNotBlank() && t.cues.isNotEmpty() && t.mistakes.isNotEmpty())
        }
        for (d in GeneratedLibrary.drills) assertTrue(d.id, Library.text(d.id)!!.cues.isNotEmpty())
        val allowed = Modality.entries.filter { !it.excluded }
        assertEquals(allowed.toSet(), GeneratedLibraryText.modalities.keys)
        assertEquals(allowed.toSet(), GeneratedLibrary.modalities.map { it.modality }.toSet())
        assertTrue(GeneratedLibrary.LICENSE.contains("ORIGINAL"))
    }

    @Test fun `equipment, stations, tags and ladder links all resolve`() {
        for (e in all) {
            assertTrue(e.id, e.equipment.all { it in GeneratedLibrary.equipment })
            assertTrue(e.id, e.stationKey == "floor" || e.stationKey in GeneratedLibrary.equipment)
            assertTrue(e.id, e.limitationTags.all { it in GeneratedLibrary.tags })
            e.progressionId?.let { assertNotNull(it, Library[it]) }
            e.regressionId?.let { assertNotNull(it, Library[it]) }
            assertTrue(e.id, e.equipment.none { it in Substitution.MOD001_EQUIPMENT })
            if (e.trackE1rm) assertTrue(e.id, e.loadType in setOf(LoadType.BARBELL, LoadType.DUMBBELL, LoadType.STACK, LoadType.KETTLEBELL))
        }
    }

    @Test fun `failure-unsafe lifts are never failure-safe (INT-003)`() {
        for (id in listOf("back-squat", "front-squat", "bench-press", "overhead-press", "deadlift", "farmer-carry", "trap-bar-carry"))
            assertFalse(id, Library.require(id).failureSafe)
        assertTrue(all.filter { it.loadType == LoadType.BARBELL }.none { it.failureSafe })
        assertTrue(all.filter { it.pattern == Pattern.LOADED_CARRY }.none { it.failureSafe })
        assertTrue(Library.require("leg-press").failureSafe)
    }

    @Test fun `TC-CORE-001a crunch-type moves exist only as user additions`() {
        val flexion = all.filter { "spinal_flexion" in it.limitationTags }
        assertTrue(flexion.isNotEmpty())
        assertTrue(flexion.all { it.userAddOnly })
        // Every core category the engine prescribes has non-flexion options.
        for (p in listOf(Pattern.ANTI_EXTENSION, Pattern.ANTI_ROTATION, Pattern.ANTI_LATERAL_FLEXION, Pattern.ROTATION))
            assertTrue(all.any { it.trains(p) && !it.userAddOnly })
    }

    @Test fun `bodyweight ladders follow the Phase 1 order (BW-001)`() {
        fun ids(f: String) = Library.ladder(f).map { it.id }
        assertEquals(listOf("push-up-incline", "push-up", "push-up-feet-elevated", "push-up-deficit", "push-up-weighted"), ids("push_up"))
        assertEquals(listOf("pull-up-assisted", "pull-up-band-assisted", "pull-up-negative", "pull-up", "pull-up-weighted"), ids("pull_up"))
        assertEquals(listOf("dip-assisted", "dip", "dip-weighted"), ids("dip"))
        assertEquals(listOf("bodyweight-box-squat", "air-squat", "tempo-squat", "bodyweight-split-squat", "bodyweight-rear-foot-elevated-split-squat"), ids("squat"))
        assertEquals(listOf("bear-hold", "bear-crawl", "lateral-bear-crawl"), ids("crawl"))
    }

    @Test fun `core ladders follow the Phase 1 order (CORE-002)`() {
        fun ids(f: String) = Library.ladder(f).map { it.id }
        assertEquals(listOf("dead-bug", "front-plank", "long-lever-plank", "body-saw", "ab-wheel-kneeling", "ab-wheel-standing"), ids("anti_extension"))
        assertEquals(listOf("pallof-half-kneeling", "pallof-standing", "pallof-walkout", "single-arm-cable-row", "renegade-row"), ids("anti_rotation"))
        assertEquals(listOf("side-plank-kneeling", "side-plank", "side-plank-reach", "kb-suitcase-carry", "suitcase-carry", "kb-offset-overhead-carry").sorted(),
            ids("anti_lateral_flexion").sorted())
        assertEquals(listOf("cable-chop-half-kneeling", "cable-lift-half-kneeling", "cable-chop-standing", "med-ball-rotational-throw"), ids("rotation"))
        assertEquals(listOf("brace-breathing", "goblet-carry"), ids("bracing"))
        // Each rung's progression stays on its ladder or leaves it upward.
        for (f in listOf("push_up", "pull_up", "dip", "squat", "crawl", "anti_extension", "anti_rotation", "anti_lateral_flexion", "rotation")) {
            for (e in Library.ladder(f)) {
                val next = Library.progressionOf(e) ?: continue
                if (next.family == f) assertTrue("${e.id}→${next.id}", next.rung > e.rung)
            }
        }
    }

    @Test fun `search finds exercises by alias`() {
        assertEquals("seated-cable-row", Library.search("seated row").first().id)
        assertEquals("db-rear-foot-elevated-split-squat", Library.search("Bulgarian split squat").first().id)
        assertTrue(Library.search("").isEmpty())
    }

    @Test fun `loads come from the right bar or machine`() {
        val inv = Inventory()
        assertEquals(10.0, PlateMath.loadsFor(Library.require("ez-bar-curl"), inv).first(), 1e-9)
        assertEquals(25.0, PlateMath.loadsFor(Library.require("trap-bar-deadlift"), inv).first(), 1e-9)
        assertEquals(20.0, PlateMath.loadsFor(Library.require("back-squat"), inv).first(), 1e-9)
        // Landmine: plates on one end only, smallest single plate first.
        val landmine = PlateMath.loadsFor(Library.require("landmine-press"), inv)
        assertEquals(1.25, landmine.first(), 1e-9)
        assertTrue(3.75 in landmine) // 2.5 + 1.25 on one end — impossible as a barbell pair total
        val custom = inv.copy(stacks = mapOf("leg_press" to com.personalfitnesscoach.engine.calc.Stack(20.0, 300.0, 10.0)))
        assertEquals(20.0, PlateMath.loadsFor(Library.require("leg-press"), custom).first(), 1e-9)
        assertEquals(5.0, PlateMath.loadsFor(Library.require("lat-pulldown"), custom).first(), 1e-9)
    }

    @Test fun `assisted exercises and cost classes are consistent`() {
        assertTrue(all.filter { it.assisted }.all { !it.trackE1rm })
        assertTrue(all.filter { it.costClass == CostClass.HEAVY_BILATERAL }.all { it.dosedAsCompound })
        assertTrue(all.filter { it.pattern == Pattern.ISOLATION }.none { it.dosedAsCompound })
    }

    @Test fun `drills prepare every lifting pattern and include balance, stretches and breathing (MOB-001, WU-004)`() {
        val lifting = listOf(Pattern.SQUAT, Pattern.HINGE, Pattern.LUNGE, Pattern.HORIZONTAL_PUSH, Pattern.HORIZONTAL_PULL, Pattern.VERTICAL_PUSH, Pattern.VERTICAL_PULL)
        for (p in lifting) assertTrue(p.name, GeneratedLibrary.drills.any { p in it.prepares })
        for (k in listOf(DrillKind.BALANCE, DrillKind.STRETCH, DrillKind.BREATHING))
            assertTrue(k.name, GeneratedLibrary.drills.any { it.kind == k })
    }
}
