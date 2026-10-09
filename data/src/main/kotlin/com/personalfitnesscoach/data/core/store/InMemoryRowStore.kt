package com.personalfitnesscoach.data.core.store

/**
 * [RowStore] in memory, with the same rules as the Room store: foreign keys (an exercise needs its workout, a set its exercise),
 * cascading deletes, unique (type, key) documents, the same orderings, and all-or-nothing transactions. Used by the core tests
 * and by the backup round-trip tests; the Room store is checked against the same contract in the data module's Robolectric tests.
 */
class InMemoryRowStore : RowStore {
    private var docs = LinkedHashMap<Pair<String, String>, DocRow>()
    private var workouts = LinkedHashMap<Long, WorkoutRow>()
    private var exercises = LinkedHashMap<Long, ExerciseRow>()
    private var sets = LinkedHashMap<Long, SetRow>()
    private var activeRow: ActiveRow? = null
    private var nextWorkout = 1L
    private var nextExercise = 1L
    private var nextSet = 1L
    private var depth = 0

    private data class State(val docs: Map<Pair<String, String>, DocRow>, val workouts: Map<Long, WorkoutRow>, val exercises: Map<Long, ExerciseRow>,
                             val sets: Map<Long, SetRow>, val active: ActiveRow?, val ids: Triple<Long, Long, Long>)

    private fun save() = State(LinkedHashMap(docs), LinkedHashMap(workouts), LinkedHashMap(exercises), LinkedHashMap(sets), activeRow,
        Triple(nextWorkout, nextExercise, nextSet))

    private fun load(s: State) {
        docs = LinkedHashMap(s.docs); workouts = LinkedHashMap(s.workouts); exercises = LinkedHashMap(s.exercises); sets = LinkedHashMap(s.sets)
        activeRow = s.active; nextWorkout = s.ids.first; nextExercise = s.ids.second; nextSet = s.ids.third
    }

    override suspend fun <R> transaction(block: suspend () -> R): R {
        if (depth > 0) return block()
        val before = save()
        depth++
        try {
            return block()
        } catch (t: Throwable) {
            load(before)
            throw t
        } finally {
            depth--
        }
    }

    // ---------------------------------------------------------------- documents
    override suspend fun doc(type: String, key: String): DocRow? = docs[type to key]
    override suspend fun docs(type: String): List<DocRow> = docs.values.filter { it.type == type }.sortedWith(compareBy({ it.day ?: Int.MIN_VALUE }, { it.key }))
    override suspend fun docsBetween(type: String, fromDay: Int, toDay: Int): List<DocRow> =
        docs.values.filter { it.type == type && it.day != null && it.day in fromDay..toDay }.sortedWith(compareBy({ it.day }, { it.key }))
    override suspend fun putDoc(row: DocRow) { docs[row.type to row.key] = row }
    override suspend fun deleteDoc(type: String, key: String) { docs.remove(type to key) }
    override suspend fun deleteDocs(type: String) { docs.keys.filter { it.first == type }.forEach { docs.remove(it) } }

    // ---------------------------------------------------------------- sessions
    override suspend fun insertWorkout(row: WorkoutRow): Long {
        val id = if (row.id > 0) row.id else nextWorkout
        require(id !in workouts) { "workout $id exists" }
        workouts[id] = row.copy(id = id)
        nextWorkout = maxOf(nextWorkout, id + 1)
        return id
    }
    override suspend fun updateWorkout(row: WorkoutRow) { require(row.id in workouts) { "no workout ${row.id}" }; workouts[row.id] = row }
    override suspend fun workout(id: Long): WorkoutRow? = workouts[id]
    override suspend fun workoutsBetween(fromDay: Int, toDay: Int): List<WorkoutRow> =
        workouts.values.filter { it.day in fromDay..toDay }.sortedWith(compareBy({ it.day }, { it.id }))
    override suspend fun deleteWorkout(id: Long) {
        workouts.remove(id) ?: return
        val ex = exercises.values.filter { it.workoutId == id }.map { it.id }.toSet()
        ex.forEach { exercises.remove(it) }
        sets.values.filter { it.workoutExerciseId in ex }.map { it.id }.forEach { sets.remove(it) }
        if (activeRow?.workoutId == id) activeRow = null
    }

    override suspend fun insertExercise(row: ExerciseRow): Long {
        require(row.workoutId in workouts) { "foreign key: no workout ${row.workoutId}" }
        val id = if (row.id > 0) row.id else nextExercise
        require(id !in exercises) { "exercise $id exists" }
        exercises[id] = row.copy(id = id)
        nextExercise = maxOf(nextExercise, id + 1)
        return id
    }
    override suspend fun updateExercise(row: ExerciseRow) {
        require(row.id in exercises) { "no exercise ${row.id}" }
        require(row.workoutId in workouts) { "foreign key: no workout ${row.workoutId}" }
        exercises[row.id] = row
    }
    override suspend fun exercise(id: Long): ExerciseRow? = exercises[id]
    override suspend fun exercisesOf(workoutId: Long): List<ExerciseRow> =
        exercises.values.filter { it.workoutId == workoutId }.sortedWith(compareBy({ it.position }, { it.id }))
    override suspend fun deleteExercise(id: Long) {
        exercises.remove(id) ?: return
        sets.values.filter { it.workoutExerciseId == id }.map { it.id }.forEach { sets.remove(it) }
    }
    override suspend fun exerciseHistory(exerciseId: String, fromDay: Int, toDay: Int): List<ExerciseRow> =
        exercises.values.filter { it.exerciseId == exerciseId && (workouts[it.workoutId]?.day ?: Int.MIN_VALUE) in fromDay..toDay }
            .sortedWith(compareBy({ workouts.getValue(it.workoutId).day }, { it.workoutId }, { it.position }))

    override suspend fun insertSet(row: SetRow): Long {
        require(row.workoutExerciseId in exercises) { "foreign key: no workout exercise ${row.workoutExerciseId}" }
        val id = if (row.id > 0) row.id else nextSet
        require(id !in sets) { "set $id exists" }
        sets[id] = row.copy(id = id)
        nextSet = maxOf(nextSet, id + 1)
        return id
    }
    override suspend fun updateSet(row: SetRow) {
        require(row.id in sets) { "no set ${row.id}" }
        require(row.workoutExerciseId in exercises) { "foreign key: no workout exercise ${row.workoutExerciseId}" }
        sets[row.id] = row
    }
    override suspend fun deleteSet(id: Long) { sets.remove(id) }
    override suspend fun setsOf(workoutExerciseId: Long): List<SetRow> =
        sets.values.filter { it.workoutExerciseId == workoutExerciseId }.sortedWith(compareBy({ it.setIndex }, { it.id }))

    // ---------------------------------------------------------------- active session
    override suspend fun active(): ActiveRow? = activeRow
    override suspend fun putActive(row: ActiveRow) { require(row.workoutId in workouts) { "foreign key: no workout ${row.workoutId}" }; activeRow = row }
    override suspend fun clearActive() { activeRow = null }

    // ---------------------------------------------------------------- whole store
    override suspend fun snapshot(): Snapshot = Snapshot(
        docs.values.sortedWith(compareBy({ it.type }, { it.key })),
        workouts.values.sortedBy { it.id }, exercises.values.sortedBy { it.id }, sets.values.sortedBy { it.id }, activeRow)

    override suspend fun replaceAll(snapshot: Snapshot): Unit = transaction {
        eraseAll()
        snapshot.docs.forEach { putDoc(it) }
        snapshot.workouts.forEach { insertWorkout(it) }
        snapshot.exercises.forEach { insertExercise(it) }
        snapshot.sets.forEach { insertSet(it) }
        snapshot.active?.let { putActive(it) }
    }

    /** Like SQLite AUTOINCREMENT, ids are never reused, even after everything is erased. */
    override suspend fun eraseAll() {
        docs.clear(); workouts.clear(); exercises.clear(); sets.clear(); activeRow = null
    }
}
