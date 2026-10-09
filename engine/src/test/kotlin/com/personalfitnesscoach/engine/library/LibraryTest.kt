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
        val allowed = Modality.entries
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
            assertTrue(e.id, e.equipment.none { it in Substitution.CARDIO_MACHINE_EQUIPMENT })
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

    // ---- Library 1.1.0 (Research Update 1.1: SAF-010 tags, D-063 power and "any of" equipment, EQ-003 circuit moves) ----

    @Test fun `library 1_1_0 carries the condition-profile safety tags on the right exercises (SAF-010)`() {
        assertEquals("1.1.0", Library.VERSION)
        val newTags = listOf("breath_hold_max", "isometric_heavy", "loaded_spinal_rotation", "deep_hip_flexion", "head_down", "supine_lying",
            "prone_lying", "high_fall_risk", "contact", "olympic_lift", "uneven_surface_running", "overhead_heavy", "unsupported_single_leg")
        assertTrue(GeneratedLibrary.tags.keys.containsAll(newTags))
        fun tagged(id: String, tag: String) = tag in Library.require(id).limitationTags
        for (e in all) {
            if (e.costClass == CostClass.HEAVY_BILATERAL) assertTrue(e.id, "breath_hold_max" in e.limitationTags)
            if (e.pattern == Pattern.ROTATION) assertTrue(e.id, "loaded_spinal_rotation" in e.limitationTags)
            if (e.pattern == Pattern.VERTICAL_PUSH && "overhead" in e.limitationTags && e.loadType != LoadType.BODYWEIGHT)
                assertTrue(e.id, "overhead_heavy" in e.limitationTags)
            if ("unsupported_single_leg" in e.limitationTags) assertTrue(e.id, "high_fall_risk" in e.limitationTags)
        }
        assertTrue(tagged("bench-press", "supine_lying") && tagged("glute-bridge", "supine_lying") && !tagged("db-incline-press", "supine_lying"))
        assertTrue(tagged("chest-supported-db-row", "prone_lying") && tagged("pike-push-up", "head_down") && tagged("farmer-carry", "isometric_heavy"))
        assertTrue(tagged("db-single-leg-rdl", "high_fall_risk") && tagged("box-jump", "high_fall_risk") && !tagged("goblet-squat", "high_fall_risk"))
        // Drills carry tags too, so condition profiles reach warm-ups and cool-downs.
        assertTrue(GeneratedLibrary.drills.all { d -> d.tags.all { it in GeneratedLibrary.tags } })
        assertTrue("supine_lying" in Library.drill("stretch-hamstring")!!.tags && "head_down" in Library.drill("drill-inchworm")!!.tags)
        // Every position-avoiding drill has an untagged alternative of the same kind.
        for (tag in listOf("supine_lying", "head_down", "spinal_flexion", "deep_hip_flexion"))
            for (k in setOf(DrillKind.STRETCH, DrillKind.MOBILISE, DrillKind.ACTIVATE))
                assertTrue("$tag/$k", GeneratedLibrary.drills.any { it.kind == k && tag !in it.tags })
    }

    @Test fun `any-of equipment - box, step and bench stand in for each other where safe (D-063)`() {
        for (e in all) for (g in e.equipmentAnyOf) {
            assertTrue(e.id, g.size >= 2 && g.all { it in GeneratedLibrary.equipment } && g.none { it in e.equipment })
        }
        for (id in listOf("step-up", "db-step-up", "bodyweight-box-squat", "pull-up-negative", "db-rear-foot-elevated-split-squat", "push-up-incline"))
            assertTrue(id, Library.require(id).usableWith(Library.require(id).equipment + "bench"))
        // A box jump needs a real box: a bench is not something to jump onto.
        assertFalse(Library.require("box-jump").usableWith(setOf("bench")))
        assertTrue(Library.available(setOf("bench")).any { it.id == "step-up" })
    }

    @Test fun `low-impact power options exist for dumbbell-only and bodyweight-only gyms, and fill power slots only (D-063, D-057)`() {
        val dbOnly = setOf("dumbbells")
        assertTrue(Library.available(dbOnly).any { it.powerCapable && it.impact == 0 && it.equipmentClass == EquipmentClass.DUMBBELL })
        assertTrue(Library.available(emptySet()).any { it.powerCapable && it.impact == 0 })
        assertTrue(Library.available(setOf("bench")).any { it.id == "fast-sit-to-stand" })
        for (e in all.filter { it.powerOnly }) {
            assertTrue(e.id, e.powerCapable)
            for (role in listOf(com.personalfitnesscoach.engine.program.SlotRole.MAIN, com.personalfitnesscoach.engine.program.SlotRole.SECONDARY,
                com.personalfitnesscoach.engine.program.SlotRole.ACCESSORY))
                assertFalse(e.id, com.personalfitnesscoach.engine.program.Selector.matches(e,
                    com.personalfitnesscoach.engine.program.SlotSpec("x", role, e.pattern, com.personalfitnesscoach.engine.program.Blueprint.Exposure.MODERATE)))
        }
        // Bodyweight jumps and throws are power-only automatically.
        assertTrue(all.filter { it.powerCapable && it.loadType == LoadType.BODYWEIGHT }.all { it.powerOnly })
        // New balance drills (FL-003 balance minutes, AGE-001 65+ balance days).
        assertTrue(GeneratedLibrary.drills.count { it.kind == DrillKind.BALANCE } >= 8)
    }

    @Test fun `bodyweight circuit has at least six no-jump moves and jumping moves carry the jumping tag (EQ-003)`() {
        val moves = GeneratedLibrary.modalities.first { it.modality == Modality.BODYWEIGHT_CIRCUIT }.moves
        assertTrue(moves.count { !it.jumping } >= 6)
        assertTrue(moves.all { it.jumping == ("jumping" in it.tags) })
        assertTrue(moves.all { m -> m.tags.all { it in GeneratedLibrary.tags } })
        assertEquals(moves.map { it.id }.toSet(), GeneratedLibraryText.circuitMoves.keys)
        assertTrue(GeneratedLibraryText.circuitMoves.values.all { it.setup.isNotBlank() && it.cues.isNotEmpty() })
    }
}
