package com.personalfitnesscoach.data.core.backup

import com.personalfitnesscoach.data.core.Fixtures
import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.model.SettingsRecord
import com.personalfitnesscoach.data.core.model.WeightRecord
import com.personalfitnesscoach.data.core.store.DocRow
import com.personalfitnesscoach.data.core.store.ExerciseRow
import com.personalfitnesscoach.data.core.store.SetRow
import com.personalfitnesscoach.data.core.store.WorkoutRow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Phase 2 section 12: export → erase → restore reproduces identical data; corrupt, truncated, older and newer files are handled. */
class BackupTest {
    /** Fast key derivation for tests; the real default (600,000) is exercised once in [password protects the file]. */
    private val fast = 10_000

    private suspend fun populated(): PfcData {
        val (d, clock) = Fixtures.data()
        Fixtures.onboard(d)
        for (i in 0 until 10) { d.docs.put(WeightRecord, WeightRecord(clock.today() - i, 80.0 + i * 0.1)) }
        val w = d.store.insertWorkout(WorkoutRow(day = clock.today() - 2, status = "DONE", template = "FB_A", tier = "FULL", startedAtMs = 1, endedAtMs = 2,
            sessionRpe = 6.0, actualMinutes = 55.0, registryVersion = "1.1.1", json = "{\"v\":1,\"weekday\":0,\"plannedMinutes\":60.0,\"warmupMinutes\":10.0}"))
        val e = d.store.insertExercise(ExerciseRow(workoutId = w, position = 0, exerciseId = "goblet_squat", slotKey = "main", status = "DONE",
            json = "{\"v\":1,\"role\":\"MAIN\",\"priority\":\"P1\",\"sets\":3,\"reps\":[8,12],\"unit\":\"REPS\",\"targetRir\":2.0,\"loadFactor\":1.0,\"restMinSec\":90,\"restDefaultSec\":120,\"restMaxSec\":180}"))
        for (i in 0 until 3) d.store.insertSet(SetRow(workoutExerciseId = e, setIndex = i, kind = "WORKING", loadKg = 24.0, reps = 10, rir = 2.0, loggedAtMs = 10L + i))
        return d
    }

    private fun problem(expected: BackupProblem, block: () -> Unit) {
        try { block() } catch (e: BackupException) { assertEquals(e.message, expected, e.problem); return }
        fail("expected $expected")
    }

    @Test fun `export, erase and restore reproduce identical data`() = runBlocking {
        val d = populated()
        val before = d.store.snapshot()
        val (name, bytes) = d.backup.export(iterations = fast)
        assertTrue(name.matches(Regex("PersonalFitnessCoach-\\d{4}-\\d{2}-\\d{2}\\.pfcbackup")))
        assertTrue(String(bytes, Charsets.UTF_8).startsWith("PFCBACKUP 1\n{"))
        d.eraseAll()
        assertTrue(d.store.snapshot().isEmpty)
        val preview = d.backup.inspect(bytes)
        assertEquals(1, preview.summary.sessions); assertEquals(3, preview.summary.sets); assertFalse(preview.summary.encrypted)
        assertEquals(Fixtures.MONDAY - 9, preview.summary.firstDay); assertEquals(Fixtures.MONDAY, preview.summary.lastDay)
        var safety: ByteArray? = null
        d.restore(preview) { _, b -> safety = b }
        assertNull("nothing to save when the app is empty", safety)
        assertEquals(before, d.store.snapshot())
        // The restored data works: a second export is byte-identical apart from the creation time.
        val again = d.backup.export(iterations = fast).second
        assertEquals(String(bytes).substringAfter("\"payloadBytes\""), String(again).substringAfter("\"payloadBytes\""))
    }

    @Test fun `restore saves the current data first, then replaces it (no merge)`() = runBlocking {
        val d = populated()
        val backup = d.backup.export(iterations = fast).second
        d.docs.put(WeightRecord, WeightRecord(Fixtures.MONDAY + 1, 79.0)) // newer data that the backup lacks
        val current = d.store.snapshot()
        var savedName = ""; var saved: ByteArray? = null
        d.restore(d.backup.inspect(backup)) { n, b -> savedName = n; saved = b }
        assertTrue(savedName.startsWith("before-restore-"))
        assertNull("replaced, not merged", d.docs.get(WeightRecord, "%07d".format(Fixtures.MONDAY + 1)))
        // The safety copy restores the newer data.
        d.restore(d.backup.inspect(saved!!)) { _, _ -> }
        assertEquals(current, d.store.snapshot())
    }

    @Test fun `a failing safety copy stops the restore before anything changes`() = runBlocking {
        val d = populated()
        val backup = d.backup.export(iterations = fast).second
        d.docs.put(WeightRecord, WeightRecord(Fixtures.MONDAY + 1, 79.0))
        val current = d.store.snapshot()
        try {
            d.restore(d.backup.inspect(backup)) { _, _ -> throw java.io.IOException("disk full") }
            fail("expected the restore to stop")
        } catch (e: java.io.IOException) { /* expected */ }
        assertEquals(current, d.store.snapshot())
    }

    @Test fun `password protects the file, with the real key derivation`() = runBlocking {
        val d = populated()
        val before = d.store.snapshot()
        val (_, bytes) = d.backup.export("correct horse".toCharArray())
        val text = String(bytes, Charsets.UTF_8)
        assertTrue(text.contains("\"iterations\":600000"))
        assertFalse("no readable data in an encrypted file", text.contains("goblet_squat") || text.contains("weight"))
        problem(BackupProblem.PASSWORD_NEEDED) { d.backup.inspect(bytes) }
        problem(BackupProblem.WRONG_PASSWORD_OR_DAMAGED) { d.backup.inspect(bytes, "wrong".toCharArray()) }
        val preview = d.backup.inspect(bytes, "correct horse".toCharArray())
        assertTrue(preview.summary.encrypted)
        d.eraseAll()
        d.restore(preview) { _, _ -> }
        assertEquals(before, d.store.snapshot())
    }

    @Test fun `the header of an encrypted file cannot be changed`() = runBlocking {
        val d = populated()
        val bytes = d.backup.export("pw".toCharArray(), iterations = fast).second
        val tampered = String(bytes, Charsets.ISO_8859_1).replaceFirst("\"app\":\"test\"", "\"app\":\"evil\"").toByteArray(Charsets.ISO_8859_1)
        problem(BackupProblem.WRONG_PASSWORD_OR_DAMAGED) { d.backup.inspect(tampered, "pw".toCharArray()) }
    }

    @Test fun `corrupt, truncated, foreign and newer files are refused with a reason`() = runBlocking {
        val d = populated()
        val bytes = d.backup.export(iterations = fast).second
        val text = String(bytes, Charsets.UTF_8)
        problem(BackupProblem.NOT_A_BACKUP) { d.backup.inspect("hello world\n{}\n".toByteArray()) }
        problem(BackupProblem.NOT_A_BACKUP) { d.backup.inspect(ByteArray(0)) }
        problem(BackupProblem.DAMAGED) { d.backup.inspect(bytes.copyOf(bytes.size - 40)) }
        problem(BackupProblem.DAMAGED) { d.backup.inspect(bytes.copyOf(20)) }
        val flipped = bytes.copyOf().also { it[it.size - 10] = (it[it.size - 10] + 1).toByte() }
        problem(BackupProblem.DAMAGED) { d.backup.inspect(flipped) }
        problem(BackupProblem.NEWER_FORMAT) { d.backup.inspect(text.replaceFirst("PFCBACKUP 1", "PFCBACKUP 2").toByteArray()) }
        problem(BackupProblem.NEWER_SCHEMA) { d.backup.inspect(text.replaceFirst("\"schemaVersion\":1", "\"schemaVersion\":7").toByteArray()) }
        val encrypted = d.backup.export("pw".toCharArray(), iterations = fast).second
        val broken = encrypted.copyOf().also { val i = it.size - 5; it[i] = (if (it[i] == 'A'.code.toByte()) 'B' else 'A').code.toByte() }
        problem(BackupProblem.WRONG_PASSWORD_OR_DAMAGED) { d.backup.inspect(broken, "pw".toCharArray()) }
    }

    @Test fun `content that would break the database is refused before anything is replaced`() = runBlocking {
        val d = populated()
        val snap = d.store.snapshot()
        fun file(s: com.personalfitnesscoach.data.core.store.Snapshot) = BackupFormat.encode(s, 1, "test")
        problem(BackupProblem.INVALID_CONTENT) { d.backup.inspect(file(snap.copy(docs = snap.docs + DocRow("location", "x", null, "{\"v\":1}", 1)))) }
        problem(BackupProblem.INVALID_CONTENT) { d.backup.inspect(file(snap.copy(sets = snap.sets + snap.sets.first().copy(id = 999, workoutExerciseId = 4242)))) }
        problem(BackupProblem.INVALID_CONTENT) { d.backup.inspect(file(snap.copy(exercises = snap.exercises.map { it.copy(workoutId = 77) }))) }
        problem(BackupProblem.INVALID_CONTENT) { d.backup.inspect(file(snap.copy(docs = snap.docs.map { if (it.type == "weight") it.copy(json = "{\"v\":1,\"day\":1}") else it }))) }
        problem(BackupProblem.INVALID_CONTENT) { d.backup.inspect(file(snap.copy(docs = snap.docs.map { if (it.type == "weight") it.copy(json = it.json.replace("\"v\":1", "\"v\":5")) else it }))) }
        problem(BackupProblem.INVALID_CONTENT) { d.backup.inspect(file(snap.copy(docs = snap.docs.map { if (it.type == "weight") it.copy(key = "9999999") else it }))) }
        problem(BackupProblem.INVALID_CONTENT) { d.backup.inspect(file(snap.copy(workouts = snap.workouts.map { it.copy(status = "WHATEVER") }))) }
        problem(BackupProblem.INVALID_CONTENT) { d.backup.inspect(file(snap.copy(sets = snap.sets.map { it.copy(kind = "MYSTERY") }))) }
        assertEquals(snap, d.store.snapshot())
    }

    @Test fun `the reminder card appears after 8 workouts when the last export is over 30 days old`() = runBlocking {
        val (d, clock) = Fixtures.data()
        assertFalse(d.backup.reminderDue(7))
        assertTrue(d.backup.reminderDue(8))
        d.backup.exported(8)
        assertFalse(d.backup.reminderDue(20))
        clock.advanceDays(31)
        assertTrue(d.backup.reminderDue(16))
        d.backup.dismissReminder(16)
        assertFalse(d.backup.reminderDue(23))
        assertTrue(d.backup.reminderDue(24))
        assertNotNull(d.docs.get(SettingsRecord))
    }

    @Test fun `erase all removes every record`() = runBlocking {
        val d = populated()
        d.eraseAll()
        assertTrue(d.store.snapshot().isEmpty)
        assertNull(d.bridge.user())
        assertArrayEquals(ByteArray(0), ByteArray(0))
    }
}
