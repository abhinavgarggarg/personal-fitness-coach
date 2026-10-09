package com.personalfitnesscoach.data.core.backup

import com.personalfitnesscoach.data.core.json.DataFormatException
import com.personalfitnesscoach.data.core.json.Js
import com.personalfitnesscoach.data.core.json.dblOrNull
import com.personalfitnesscoach.data.core.json.int
import com.personalfitnesscoach.data.core.json.intOrNull
import com.personalfitnesscoach.data.core.json.long
import com.personalfitnesscoach.data.core.json.longOrNull
import com.personalfitnesscoach.data.core.json.obj
import com.personalfitnesscoach.data.core.json.objOrNull
import com.personalfitnesscoach.data.core.json.objs
import com.personalfitnesscoach.data.core.json.str
import com.personalfitnesscoach.data.core.json.strOrNull
import com.personalfitnesscoach.data.core.model.SettingsRecord
import com.personalfitnesscoach.data.core.repo.DataSchema
import com.personalfitnesscoach.data.core.repo.Docs
import com.personalfitnesscoach.data.core.session.ItemDoc
import com.personalfitnesscoach.data.core.session.SetKind
import com.personalfitnesscoach.data.core.session.Status
import com.personalfitnesscoach.data.core.session.WorkoutDoc
import com.personalfitnesscoach.data.core.store.ActiveRow
import com.personalfitnesscoach.data.core.store.DocRow
import com.personalfitnesscoach.data.core.store.ExerciseRow
import com.personalfitnesscoach.data.core.store.RowStore
import com.personalfitnesscoach.data.core.store.SetRow
import com.personalfitnesscoach.data.core.store.Snapshot
import com.personalfitnesscoach.data.core.store.WorkoutRow
import com.personalfitnesscoach.data.core.time.AppClock
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.engine.library.GeneratedLibrary
import com.personalfitnesscoach.engine.registry.Registry
import com.personalfitnesscoach.engine.safety.GeneratedConditions
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Why a backup file was refused (Phase 2 section 12: corrupt, truncated, older and newer files). */
enum class BackupProblem {
    NOT_A_BACKUP, NEWER_FORMAT, NEWER_SCHEMA, PASSWORD_NEEDED, WRONG_PASSWORD_OR_DAMAGED, DAMAGED, INVALID_CONTENT,
}

class BackupException(val problem: BackupProblem, message: String, cause: Throwable? = null) : Exception(message, cause)

/** What a backup holds, shown before the user confirms a restore. */
data class BackupSummary(
    val createdAtMs: Long,
    val appVersion: String,
    val schemaVersion: Int,
    val registryVersion: String,
    val encrypted: Boolean,
    val firstDay: Int?,
    val lastDay: Int?,
    val sessions: Int,
    val sets: Int,
    val records: Int,
)

/** A checked backup, ready to restore. */
class RestorePreview internal constructor(val summary: BackupSummary, internal val snapshot: Snapshot)

/**
 * The export file (Phase 2 section 12, decision D-075). Three parts, so the checksum covers exact bytes:
 *
 *     PFCBACKUP 1                  ← magic and format version
 *     {header JSON}                ← versions, counts, date range, SHA-256 of the payload, encryption parameters
 *     payload                      ← the data as JSON, or with a password: Base64 of AES-256-GCM ciphertext
 *
 * With a password the key comes from PBKDF2-HMAC-SHA256 (600,000 iterations, random 16-byte salt), the IV is a random 12 bytes, and
 * the magic and header are the GCM additional data, so neither can be changed without the file failing to open. A forgotten
 * password cannot be recovered.
 */
object BackupFormat {
    const val MAGIC = "PFCBACKUP"
    const val FORMAT_VERSION = 1
    const val PBKDF2_ITERATIONS = 600_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private val rng = SecureRandom()

    fun fileName(day: Int): String = "PersonalFitnessCoach-${Days.date(day)}.pfcbackup"

    fun encode(snapshot: Snapshot, createdAtMs: Long, appVersion: String, password: CharArray? = null, iterations: Int = PBKDF2_ITERATIONS): ByteArray {
        val payload = payloadJson(snapshot).toString().toByteArray(Charsets.UTF_8)
        val days = (snapshot.workouts.map { it.day } + snapshot.docs.mapNotNull { it.day })
        var enc: JsonObject? = null
        var salt = ByteArray(0); var iv = ByteArray(0)
        if (password != null) {
            require(password.isNotEmpty()) { "empty password" }
            salt = ByteArray(SALT_BYTES).also { rng.nextBytes(it) }
            iv = ByteArray(IV_BYTES).also { rng.nextBytes(it) }
            val b64 = Base64.getEncoder()
            enc = obj { put("cipher", "AES-256-GCM"); put("kdf", "PBKDF2-HMAC-SHA256"); put("iterations", iterations); put("salt", b64.encodeToString(salt))
                put("iv", b64.encodeToString(iv)) }
        }
        val header = obj {
            put("format", "pfcbackup"); put("formatVersion", FORMAT_VERSION); put("app", appVersion); put("schemaVersion", DataSchema.SCHEMA_VERSION)
            put("registryVersion", Registry.VERSION); put("libraryVersion", GeneratedLibrary.VERSION); put("conditionsVersion", GeneratedConditions.VERSION)
            put("createdAtMs", createdAtMs); put("firstDay", days.minOrNull()); put("lastDay", days.maxOrNull())
            put("sessions", snapshot.workouts.count { it.status == Status.DONE }); put("sets", snapshot.sets.size)
            put("records", snapshot.docs.size + snapshot.workouts.size + snapshot.exercises.size + snapshot.sets.size)
            put("payloadBytes", payload.size); put("sha256", sha256(payload)); put("encryption", enc)
        }.toString()
        val head = "$MAGIC $FORMAT_VERSION\n$header\n"
        val body = if (password == null) payload else {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
            c.updateAAD(head.toByteArray(Charsets.UTF_8))
            Base64.getEncoder().encode(c.doFinal(payload))
        }
        return head.toByteArray(Charsets.UTF_8) + body
    }

    /** Checks and decodes a file; throws [BackupException] with the reason when it cannot be restored. */
    fun decode(bytes: ByteArray, password: CharArray? = null): RestorePreview {
        val nl1 = bytes.indexOf('\n'.code.toByte())
        if (nl1 <= 0 || nl1 > 64) throw BackupException(BackupProblem.NOT_A_BACKUP, "not a Personal Fitness Coach backup")
        val magic = String(bytes, 0, nl1, Charsets.UTF_8).split(' ')
        if (magic.size != 2 || magic[0] != MAGIC) throw BackupException(BackupProblem.NOT_A_BACKUP, "not a Personal Fitness Coach backup")
        val fv = magic[1].toIntOrNull() ?: throw BackupException(BackupProblem.NOT_A_BACKUP, "unknown backup format")
        if (fv > FORMAT_VERSION) throw BackupException(BackupProblem.NEWER_FORMAT, "made by a newer version of the app; update the app to restore it")
        if (fv < 1) throw BackupException(BackupProblem.NOT_A_BACKUP, "unknown backup format")
        var nl2 = -1
        for (i in nl1 + 1 until bytes.size) if (bytes[i] == '\n'.code.toByte()) { nl2 = i; break }
        if (nl2 < 0) throw BackupException(BackupProblem.DAMAGED, "the file is incomplete")
        val header = try { Js.parse(String(bytes, nl1 + 1, nl2 - nl1 - 1, Charsets.UTF_8)) } catch (e: DataFormatException) {
            throw BackupException(BackupProblem.DAMAGED, "the file header is damaged", e) }
        val head = bytes.copyOfRange(0, nl2 + 1)
        val body = bytes.copyOfRange(nl2 + 1, bytes.size)
        try {
            if (header.str("format") != "pfcbackup") throw BackupException(BackupProblem.NOT_A_BACKUP, "not a Personal Fitness Coach backup")
            val schema = header.int("schemaVersion")
            if (schema > DataSchema.SCHEMA_VERSION) throw BackupException(BackupProblem.NEWER_SCHEMA, "made by a newer version of the app; update the app to restore it")
            val enc = header.objOrNull("encryption")
            val payload = if (enc == null) body else {
                if (password == null || password.isEmpty()) throw BackupException(BackupProblem.PASSWORD_NEEDED, "this backup has a password")
                val d = Base64.getDecoder()
                val cipherBytes = try { d.decode(body) } catch (e: IllegalArgumentException) {
                    throw BackupException(BackupProblem.DAMAGED, "the file is damaged", e) }
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, key(password, d.decode(enc.str("salt")), enc.int("iterations")), GCMParameterSpec(TAG_BITS, d.decode(enc.str("iv"))))
                c.updateAAD(head)
                try { c.doFinal(cipherBytes) } catch (e: AEADBadTagException) {
                    throw BackupException(BackupProblem.WRONG_PASSWORD_OR_DAMAGED, "wrong password, or the file is damaged", e) }
            }
            if (payload.size != header.int("payloadBytes") || sha256(payload) != header.str("sha256"))
                throw BackupException(BackupProblem.DAMAGED, "the file is damaged or incomplete (checksum does not match)")
            val snapshot = migrate(readPayload(Js.parse(String(payload, Charsets.UTF_8))), schema)
            validate(snapshot)
            val days = snapshot.workouts.map { it.day } + snapshot.docs.mapNotNull { it.day }
            return RestorePreview(BackupSummary(header.long("createdAtMs"), header.str("app"), schema, header.str("registryVersion"), enc != null,
                days.minOrNull(), days.maxOrNull(), snapshot.workouts.count { it.status == Status.DONE }, snapshot.sets.size,
                snapshot.docs.size + snapshot.workouts.size + snapshot.exercises.size + snapshot.sets.size), snapshot)
        } catch (e: DataFormatException) {
            throw BackupException(BackupProblem.INVALID_CONTENT, "the backup's content cannot be read: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw BackupException(BackupProblem.INVALID_CONTENT, "the backup's content cannot be read: ${e.message}", e)
        }
    }

    /** Older payload versions are upgraded here, one step at a time; there is only version 1 so far. */
    private fun migrate(s: Snapshot, fromSchema: Int): Snapshot {
        if (fromSchema < 1) throw DataFormatException("schema version $fromSchema is not supported")
        return s
    }

    /** Every record must be readable and every reference valid before anything is replaced. */
    internal fun validate(s: Snapshot) {
        val seen = HashSet<Pair<String, String>>()
        for (d in s.docs) {
            if (!seen.add(d.type to d.key)) throw DataFormatException("record ${d.type}/${d.key} appears twice")
            @Suppress("UNCHECKED_CAST")
            val c = (DataSchema.codec(d.type) ?: throw DataFormatException("unknown record type '${d.type}'")) as com.personalfitnesscoach.data.core.model.DocCodec<Any?>
            val v = c.decode(d.json)
            if (c.key(v) != d.key || c.day(v) != d.day) throw DataFormatException("record ${d.type}/${d.key} does not match its content")
        }
        val workouts = s.workouts.associateBy { it.id }
        if (workouts.size != s.workouts.size || s.workouts.any { it.id <= 0 }) throw DataFormatException("workout ids are not unique")
        val statuses = setOf(Status.PLANNED, Status.IN_PROGRESS, Status.DONE, Status.SKIPPED, Status.MOVED)
        for (w in s.workouts) {
            if (w.status !in statuses) throw DataFormatException("workout ${w.id} has an unknown status")
            WorkoutDoc.decode(w.json)
        }
        val exercises = s.exercises.associateBy { it.id }
        if (exercises.size != s.exercises.size || s.exercises.any { it.id <= 0 }) throw DataFormatException("exercise ids are not unique")
        val exStatuses = setOf(Status.PLANNED, Status.IN_PROGRESS, Status.DONE, Status.SKIPPED, Status.SWAPPED)
        for (e in s.exercises) {
            if (e.workoutId !in workouts) throw DataFormatException("exercise ${e.id} belongs to a missing workout")
            if (e.status !in exStatuses) throw DataFormatException("exercise ${e.id} has an unknown status")
            ItemDoc.decode(e.json)
        }
        if (s.sets.map { it.id }.toSet().size != s.sets.size || s.sets.any { it.id <= 0 }) throw DataFormatException("set ids are not unique")
        for (x in s.sets) {
            if (x.workoutExerciseId !in exercises) throw DataFormatException("set ${x.id} belongs to a missing exercise")
            if (x.kind !in SetKind.all) throw DataFormatException("set ${x.id} has an unknown kind")
        }
        s.active?.let { if (it.workoutId !in workouts) throw DataFormatException("the open workout is missing") }
    }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        require(iterations in 10_000..10_000_000) { "iterations out of range" }
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun sha256(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    // ------------------------------------------------------------------------------------------------ payload JSON
    private fun payloadJson(s: Snapshot): JsonObject = obj {
        put("docs", JsonArray(s.docs.map { d -> obj { put("type", d.type); put("key", d.key); put("day", d.day); put("json", d.json); put("updatedAtMs", d.updatedAtMs) } }))
        put("workouts", JsonArray(s.workouts.map { w -> obj {
            put("id", w.id); put("day", w.day); put("status", w.status); put("template", w.template); put("tier", w.tier); put("startedAtMs", w.startedAtMs)
            put("endedAtMs", w.endedAtMs); put("sessionRpe", w.sessionRpe); put("actualMinutes", w.actualMinutes); put("registryVersion", w.registryVersion)
            put("json", w.json) } }))
        put("exercises", JsonArray(s.exercises.map { e -> obj {
            put("id", e.id); put("workoutId", e.workoutId); put("position", e.position); put("exerciseId", e.exerciseId); put("slotKey", e.slotKey)
            put("status", e.status); put("json", e.json) } }))
        put("sets", JsonArray(s.sets.map { x -> obj {
            put("id", x.id); put("workoutExerciseId", x.workoutExerciseId); put("setIndex", x.setIndex); put("kind", x.kind); put("loadKg", x.loadKg)
            put("reps", x.reps); put("seconds", x.seconds); put("metres", x.metres); put("rir", x.rir); put("rpe", x.rpe); put("formCheck", x.formCheck)
            put("deviation", x.deviation); put("loggedAtMs", x.loggedAtMs) } }))
        s.active?.let { a -> put("active", obj { put("workoutId", a.workoutId); put("json", a.json); put("updatedAtMs", a.updatedAtMs) }) }
    }

    private fun readPayload(o: JsonObject): Snapshot = Snapshot(
        docs = o.objs("docs").map { DocRow(it.str("type"), it.str("key"), it.intOrNull("day"), it.str("json"), it.long("updatedAtMs")) },
        workouts = o.objs("workouts").map { WorkoutRow(it.long("id"), it.int("day"), it.str("status"), it.str("template"), it.strOrNull("tier"),
            it.longOrNull("startedAtMs"), it.longOrNull("endedAtMs"), it.dblOrNull("sessionRpe"), it.dblOrNull("actualMinutes"), it.str("registryVersion"),
            it.str("json")) },
        exercises = o.objs("exercises").map { ExerciseRow(it.long("id"), it.long("workoutId"), it.int("position"), it.str("exerciseId"), it.str("slotKey"),
            it.str("status"), it.str("json")) },
        sets = o.objs("sets").map { SetRow(it.long("id"), it.long("workoutExerciseId"), it.int("setIndex"), it.str("kind"), it.dblOrNull("loadKg"),
            it.intOrNull("reps"), it.intOrNull("seconds"), it.dblOrNull("metres"), it.dblOrNull("rir"), it.dblOrNull("rpe"), it.str("formCheck"),
            it.str("deviation"), it.long("loggedAtMs")) },
        active = o.objOrNull("active")?.let { ActiveRow(it.long("workoutId"), it.str("json"), it.long("updatedAtMs")) },
    )

}

/** A place to save a file the user will keep (the app's file picker). Must have written every byte when it returns. */
fun interface BackupSink {
    suspend fun save(fileName: String, bytes: ByteArray)
}

/**
 * Export, restore and erase (Phase 2 section 12). Restore never merges: it checks the file first, always saves the current data
 * through `safetyCopy` before replacing anything (when there is any), then replaces everything in one transaction.
 */
class BackupService(private val store: RowStore, private val docs: Docs, private val clock: AppClock, private val appVersion: String) {

    suspend fun export(password: CharArray? = null, iterations: Int = BackupFormat.PBKDF2_ITERATIONS): Pair<String, ByteArray> {
        val snapshot = store.snapshot()
        val bytes = BackupFormat.encode(snapshot, clock.nowMs(), appVersion, password, iterations)
        return BackupFormat.fileName(clock.today()) to bytes
    }

    /** Records a successful export (for the reminder card). Call after the file was saved. */
    suspend fun exported(completedWorkouts: Int) {
        docs.update(SettingsRecord) { (it ?: SettingsRecord()).copy(lastExportDay = clock.today(), completedAtLastExport = completedWorkouts) }
    }

    fun inspect(bytes: ByteArray, password: CharArray? = null): RestorePreview = BackupFormat.decode(bytes, password)

    suspend fun restore(preview: RestorePreview, safetyCopy: BackupSink) {
        val current = store.snapshot()
        if (!current.isEmpty) {
            val bytes = BackupFormat.encode(current, clock.nowMs(), appVersion)
            safetyCopy.save("before-restore-" + BackupFormat.fileName(clock.today()), bytes)
        }
        store.replaceAll(preview.snapshot)
    }

    /** "Erase all my data": every record in every table (settings included). Alarms and notifications are cleared by the app. */
    suspend fun eraseAll() = store.eraseAll()

    /**
     * The reminder card (Phase 2 section 12): after every 8 completed workouts, if the last export is over 30 days old (or never),
     * until dismissed; a dismissed card returns after 8 more workouts.
     */
    suspend fun reminderDue(completedWorkouts: Int): Boolean {
        val s = docs.get(SettingsRecord) ?: SettingsRecord()
        val since = completedWorkouts - s.completedAtLastExport
        val old = s.lastExportDay == null || clock.today() - s.lastExportDay > 30
        val dismissed = s.reminderDismissedAt?.let { completedWorkouts - it < 8 } ?: false
        return since >= 8 && old && !dismissed
    }

    suspend fun dismissReminder(completedWorkouts: Int) {
        docs.update(SettingsRecord) { (it ?: SettingsRecord()).copy(reminderDismissedAt = completedWorkouts) }
    }
}
