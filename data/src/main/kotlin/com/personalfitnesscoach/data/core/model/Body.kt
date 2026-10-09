package com.personalfitnesscoach.data.core.model

import com.personalfitnesscoach.data.core.json.Obj
import com.personalfitnesscoach.data.core.json.bool
import com.personalfitnesscoach.data.core.json.dbl
import com.personalfitnesscoach.data.core.json.dblOrNull
import com.personalfitnesscoach.data.core.json.doubles
import com.personalfitnesscoach.data.core.json.enum
import com.personalfitnesscoach.data.core.json.enumOrNull
import com.personalfitnesscoach.data.core.json.int
import com.personalfitnesscoach.data.core.json.intOrNull
import com.personalfitnesscoach.data.core.json.long
import com.personalfitnesscoach.data.core.json.longOrNull
import com.personalfitnesscoach.data.core.json.obj
import com.personalfitnesscoach.data.core.json.strOrNull
import com.personalfitnesscoach.data.core.json.strings
import com.personalfitnesscoach.data.core.privacy.DataItem
import com.personalfitnesscoach.data.core.privacy.RecordKind
import com.personalfitnesscoach.engine.calc.CheckIn
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.safety.PainAction
import com.personalfitnesscoach.engine.safety.PainKind
import com.personalfitnesscoach.engine.safety.PainReport
import kotlinx.serialization.json.JsonObject

// ==================================================================================================== readiness
/** The daily check-in (ReadinessLog), its scores, the tier and today's safety answers (red flags, illness symptoms). */
data class ReadinessRecord(
    val day: Int,
    val checkIn: CheckIn,
    val minutesAvailable: Int? = null,
    val rRaw: Double,
    val rFinal: Double,
    val engineTier: Tier,
    /** RDY-006: the tier the user chose (within what safety allows); null = the engine's tier. */
    val chosenTier: Tier? = null,
    val signals: Set<String> = emptySet(),
    val redFlags: Set<String> = emptySet(),
    val illnessSymptoms: Set<String> = emptySet(),
    val atMs: Long,
) {
    val tier: Tier get() = chosenTier ?: engineTier

    companion object Codec : DocCodec<ReadinessRecord>("readiness", 1, RecordKind.COLLECTED, setOf(DataItem.READINESS, DataItem.SLEEP_HOURS),
        "Your daily check-ins") {
        override fun key(v: ReadinessRecord) = dayKey(v.day)
        override fun day(v: ReadinessRecord) = v.day
        override fun write(v: ReadinessRecord, o: Obj) = with(o) {
            put("day", v.day)
            put("checkIn", obj { put("sleep", v.checkIn.sleep); put("energy", v.checkIn.energy); put("soreness", v.checkIn.soreness)
                put("stress", v.checkIn.stress); put("sleepHours", v.checkIn.sleepHours) })
            put("minutesAvailable", v.minutesAvailable); put("rRaw", v.rRaw); put("rFinal", v.rFinal); put("engineTier", v.engineTier)
            put("chosenTier", v.chosenTier); strings("signals", v.signals); strings("redFlags", v.redFlags); strings("illnessSymptoms", v.illnessSymptoms)
            put("atMs", v.atMs)
        }
        override fun read(o: JsonObject, version: Int): ReadinessRecord {
            val c = o.obj("checkIn")
            return ReadinessRecord(o.int("day"), CheckIn(c.int("sleep"), c.int("energy"), c.int("soreness"), c.int("stress"), c.dblOrNull("sleepHours")),
                o.intOrNull("minutesAvailable"), o.dbl("rRaw"), o.dbl("rFinal"), o.enum("engineTier"), o.enumOrNull<Tier>("chosenTier"),
                o.strings("signals").toSet(), o.strings("redFlags").toSet(), o.strings("illnessSymptoms").toSet(), o.long("atMs"))
        }
    }
}

// ==================================================================================================== pain
enum class Side { LEFT, RIGHT, BOTH, MIDDLE }

/** One "Something hurts" report (PainReport) and what the pain gate decided (SAF-003). */
data class PainRecord(
    val atMs: Long,
    val day: Int,
    val workoutId: Long? = null,
    val exerciseId: String? = null,
    val report: PainReport,
    val side: Side? = null,
    val action: PainAction,
    val resolvedDay: Int? = null,
) {
    companion object Codec : DocCodec<PainRecord>("pain", 1, RecordKind.COLLECTED, setOf(DataItem.PAIN_REPORTS), "Pain you reported") {
        override fun key(v: PainRecord) = v.atMs.toString().padStart(15, '0')
        override fun day(v: PainRecord) = v.day
        override fun write(v: PainRecord, o: Obj) = with(o) {
            put("atMs", v.atMs); put("day", v.day); put("workoutId", v.workoutId); put("exerciseId", v.exerciseId)
            put("region", v.report.region); put("kind", v.report.kind); put("rating", v.report.rating); flag("worsening", v.report.worsening)
            strings("descriptors", v.report.descriptors); flag("wholeBody", v.report.affectsWholeBodyMovement)
            put("side", v.side); put("action", v.action); put("resolvedDay", v.resolvedDay)
        }
        override fun read(o: JsonObject, version: Int) = PainRecord(o.long("atMs"), o.int("day"), o.longOrNull("workoutId"), o.strOrNull("exerciseId"),
            PainReport(o.enum<Joint>("region"), o.enum<PainKind>("kind"), o.int("rating"), o.bool("worsening"), o.strings("descriptors").toSet(),
                o.bool("wholeBody")), o.enumOrNull<Side>("side"), o.enum("action"), o.intOrNull("resolvedDay"))
    }
}

// ==================================================================================================== recovery
/** Illness and "I feel run down" (RecoveryLog): SAF-007 and fatigue signal F6. */
data class RecoveryRecord(val day: Int, val illness: Boolean = false, val runDown: Boolean = false, val symptoms: Set<String> = emptySet()) {
    companion object Codec : DocCodec<RecoveryRecord>("recovery", 1, RecordKind.COLLECTED, setOf(DataItem.READINESS), "Illness and feeling run down") {
        override fun key(v: RecoveryRecord) = dayKey(v.day)
        override fun day(v: RecoveryRecord) = v.day
        override fun write(v: RecoveryRecord, o: Obj) = with(o) {
            put("day", v.day); flag("illness", v.illness); flag("runDown", v.runDown); strings("symptoms", v.symptoms)
        }
        override fun read(o: JsonObject, version: Int) = RecoveryRecord(o.int("day"), o.bool("illness"), o.bool("runDown"), o.strings("symptoms").toSet())
    }
}

// ==================================================================================================== body
/** Morning bodyweight (FL-004). */
data class WeightRecord(val day: Int, val kg: Double) {
    init { require(kg in 20.0..400.0) { "bodyweight out of range" } }

    companion object Codec : DocCodec<WeightRecord>("weight", 1, RecordKind.COLLECTED, setOf(DataItem.BODYWEIGHT), "Your bodyweight") {
        override fun key(v: WeightRecord) = dayKey(v.day)
        override fun day(v: WeightRecord) = v.day
        override fun write(v: WeightRecord, o: Obj) = with(o) { put("day", v.day); put("kg", v.kg) }
        override fun read(o: JsonObject, version: Int) = WeightRecord(o.int("day"), o.dbl("kg"))
    }
}

/** Waist readings at the navel (FL-005): up to three readings, averaged (BodyProgress.waist). */
data class WaistRecord(val day: Int, val readingsCm: List<Double>) {
    init { require(readingsCm.isNotEmpty() && readingsCm.size <= 3 && readingsCm.all { it in 30.0..250.0 }) { "1–3 waist readings, 30–250 cm" } }

    companion object Codec : DocCodec<WaistRecord>("waist", 1, RecordKind.COLLECTED, setOf(DataItem.BODY_MEASUREMENTS), "Your waist measurements") {
        override fun key(v: WaistRecord) = dayKey(v.day)
        override fun day(v: WaistRecord) = v.day
        override fun write(v: WaistRecord, o: Obj) = with(o) { put("day", v.day); doubles("readingsCm", v.readingsCm) }
        override fun read(o: JsonObject, version: Int) = WaistRecord(o.int("day"), o.doubles("readingsCm"))
    }
}

// ==================================================================================================== steps
/** Steps on one day from this phone's built-in step counter (STEP-001, D-062). Steps from other apps are never stored (DATA-001). */
data class StepsRecord(val day: Int, val steps: Int) {
    init { require(steps in 0..200_000) { "steps out of range" } }

    companion object Codec : DocCodec<StepsRecord>("steps", 1, RecordKind.COLLECTED, setOf(DataItem.DAILY_STEPS_PHONE_SENSOR), "Daily steps from this phone") {
        override fun key(v: StepsRecord) = dayKey(v.day)
        override fun day(v: StepsRecord) = v.day
        override fun write(v: StepsRecord, o: Obj) = with(o) { put("day", v.day); put("steps", v.steps) }
        override fun read(o: JsonObject, version: Int) = StepsRecord(o.int("day"), o.int("steps"))
    }
}

/** The STEP-001 baseline and weekly target, as of the week starting `weekStartDay`. */
data class StepStateRecord(val weekStartDay: Int, val baseline: Int? = null, val target: Int? = null, val lowWeeks: Int = 0) {
    companion object Codec : DocCodec<StepStateRecord>("step_state", 1, RecordKind.DERIVED, setOf(DataItem.DAILY_STEPS_PHONE_SENSOR), "Your step target") {
        override fun key(v: StepStateRecord) = SINGLE
        override fun write(v: StepStateRecord, o: Obj) = with(o) {
            put("weekStartDay", v.weekStartDay); put("baseline", v.baseline); put("target", v.target); put("lowWeeks", v.lowWeeks)
        }
        override fun read(o: JsonObject, version: Int) = StepStateRecord(o.int("weekStartDay"), o.intOrNull("baseline"), o.intOrNull("target"), o.int("lowWeeks"))
    }
}

/**
 * The last reading of the phone's step counter (it counts from the last reboot). The next reading's difference is shared across
 * the days it spans ([com.personalfitnesscoach.data.core.steps.StepLedger]).
 */
data class StepCounterRecord(val atMs: Long, val elapsedMs: Long, val counter: Long, /** The phone's boot count (Settings.Global.BOOT_COUNT), when known. */ val bootCount: Int? = null) {
    companion object Codec : DocCodec<StepCounterRecord>("step_counter", 1, RecordKind.DERIVED, setOf(DataItem.DAILY_STEPS_PHONE_SENSOR), "Step counter reading") {
        override fun key(v: StepCounterRecord) = SINGLE
        override fun write(v: StepCounterRecord, o: Obj) = with(o) { put("atMs", v.atMs); put("elapsedMs", v.elapsedMs); put("counter", v.counter); put("bootCount", v.bootCount) }
        override fun read(o: JsonObject, version: Int) = StepCounterRecord(o.long("atMs"), o.long("elapsedMs"), o.long("counter"), o.intOrNull("bootCount"))
    }
}

// ==================================================================================================== safety stop
/**
 * A SAF-002 red-flag stop. Training stays stopped until the user confirms the symptoms have resolved or were reviewed by a
 * clinician; the first session back is then LIGHT at most (RedFlags.tierAfterStop).
 */
data class SafetyStopRecord(val day: Int, val symptoms: Set<String>, val confirmedDay: Int? = null) {
    companion object Codec : DocCodec<SafetyStopRecord>("safety_stop", 1, RecordKind.COLLECTED, setOf(DataItem.READINESS, DataItem.SCREENING),
        "A red-flag stop and when it was cleared") {
        override fun key(v: SafetyStopRecord) = SINGLE
        override fun write(v: SafetyStopRecord, o: Obj) = with(o) { put("day", v.day); strings("symptoms", v.symptoms); put("confirmedDay", v.confirmedDay) }
        override fun read(o: JsonObject, version: Int) = SafetyStopRecord(o.int("day"), o.strings("symptoms").toSet(), o.intOrNull("confirmedDay"))
    }
}

/** A logged walk (PH-001 walking minutes; STEP-002 brisk bouts). */
data class WalkRecord(val day: Int, val startMinute: Int, val minutes: Int, val brisk: Boolean = true) {
    init { require(startMinute in 0 until 1440 && minutes in 1..600) { "walk out of range" } }

    companion object Codec : DocCodec<WalkRecord>("walk", 1, RecordKind.COLLECTED, setOf(DataItem.SESSION_RPE_DURATION, DataItem.DAILY_STEPS_PHONE_SENSOR),
        "Walks") {
        override fun key(v: WalkRecord) = dayKey(v.day) + "-" + v.startMinute.toString().padStart(4, '0')
        override fun day(v: WalkRecord) = v.day
        override fun write(v: WalkRecord, o: Obj) = with(o) { put("day", v.day); put("startMinute", v.startMinute); put("minutes", v.minutes); put("brisk", v.brisk) }
        override fun read(o: JsonObject, version: Int) = WalkRecord(o.int("day"), o.int("startMinute"), o.int("minutes"), o.bool("brisk", true))
    }
}

