package com.personalfitnesscoach.engine.library

import com.personalfitnesscoach.engine.model.EquipmentClass
import com.personalfitnesscoach.engine.model.Exercise
import com.personalfitnesscoach.engine.model.Pattern

/**
 * Read-only access to the generated exercise library (D-046). Everything is deterministic:
 * lists keep library order and ties are broken by exercise ID.
 */
object Library {
    val VERSION: String get() = GeneratedLibrary.VERSION
    val all: List<Exercise> get() = GeneratedLibrary.exercises
    private val byId: Map<String, Exercise> by lazy { all.associateBy { it.id } }
    private val drillsById: Map<String, Drill> by lazy { GeneratedLibrary.drills.associateBy { it.id } }

    operator fun get(id: String): Exercise? = byId[id]
    fun require(id: String): Exercise = byId[id] ?: throw IllegalArgumentException("unknown exercise $id")
    fun drill(id: String): Drill? = drillsById[id]
    fun text(id: String): ExerciseText? = GeneratedLibraryText.exercises[id] ?: GeneratedLibraryText.drills[id]

    /** Exercises whose equipment is all in `equipment` (bodyweight moves with no equipment always qualify). */
    fun available(equipment: Set<String>): List<Exercise> = all.filter { equipment.containsAll(it.equipment) }

    /** The rungs of a bodyweight or core ladder, easiest first (BW-001, CORE-002). */
    fun ladder(family: String): List<Exercise> = all.filter { it.family == family }.sortedWith(compareBy({ it.rung }, { it.id }))

    fun progressionOf(ex: Exercise): Exercise? = ex.progressionId?.let { byId[it] }
    fun regressionOf(ex: Exercise): Exercise? = ex.regressionId?.let { byId[it] }

    /** Search by name or alias, case-insensitive; exact name/alias matches first. */
    fun search(query: String): List<Exercise> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        fun names(e: Exercise) = listOf(e.name.lowercase()) + e.aliases.map { it.lowercase() }
        val exact = all.filter { e -> names(e).any { it == q } }
        val partial = all.filter { e -> e !in exact && names(e).any { it.contains(q) } }
        return exact + partial.sortedBy { it.id }
    }

    /** Equipment classes with at least one exercise for `p` (main or second pattern). */
    fun classesFor(p: Pattern): Set<EquipmentClass> = all.filter { it.trains(p) }.map { it.equipmentClass }.toSet()
}
