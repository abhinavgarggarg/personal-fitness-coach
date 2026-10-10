package com.personalfitnesscoach.data.core.model

import com.personalfitnesscoach.data.core.json.DataFormatException
import com.personalfitnesscoach.data.core.json.Obj
import com.personalfitnesscoach.data.core.json.anyToJson
import com.personalfitnesscoach.data.core.json.bool
import com.personalfitnesscoach.data.core.json.dbl
import com.personalfitnesscoach.data.core.json.dblOrNull
import com.personalfitnesscoach.data.core.json.enum
import com.personalfitnesscoach.data.core.json.enumOrNull
import com.personalfitnesscoach.data.core.json.enums
import com.personalfitnesscoach.data.core.json.int
import com.personalfitnesscoach.data.core.json.intOrNull
import com.personalfitnesscoach.data.core.json.ints
import com.personalfitnesscoach.data.core.json.long
import com.personalfitnesscoach.data.core.json.longOrNull
import com.personalfitnesscoach.data.core.json.obj
import com.personalfitnesscoach.data.core.json.objOrNull
import com.personalfitnesscoach.data.core.json.objs
import com.personalfitnesscoach.data.core.json.parseEnum
import com.personalfitnesscoach.data.core.json.range
import com.personalfitnesscoach.data.core.json.str
import com.personalfitnesscoach.data.core.json.strOrNull
import com.personalfitnesscoach.data.core.json.stringMap
import com.personalfitnesscoach.data.core.json.strings
import com.personalfitnesscoach.data.core.privacy.DataItem
import com.personalfitnesscoach.data.core.privacy.RecordKind
import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.program.Blueprint
import com.personalfitnesscoach.engine.program.DayTemplate
import com.personalfitnesscoach.engine.program.Goal
import com.personalfitnesscoach.engine.progression.Prescription
import com.personalfitnesscoach.engine.progression.ProgressionAction
import kotlinx.serialization.json.JsonObject

// ==================================================================================================== programme
/**
 * The active programme (Program / PeriodizationPhase in Phase 2): the blueprint is rebuilt from `priorities` (deterministic),
 * so only the position on its block clock and the per-block choices are stored.
 */
data class ProgramRecord(
    val priorities: List<Goal>,
    val startDay: Int,
    /** Monday of the week the clock position belongs to; the weekly rollover moves it forward. */
    val weekStartDay: Int,
    /** PER-005 block-clock week (1-based). */
    val clockWeek: Int = 1,
    /** Calendar weeks since the start in which at least one session was completed ("weeks training"). */
    val weeksTraining: Int = 0,
    val blockIndex: Int = 0,
    /** ADH-003: main lift per slot key for the current block; cleared when a block starts. */
    val coreLifts: Map<String, String> = emptyMap(),
    val previousBlockChoices: Map<String, String> = emptyMap(),
    val ladderRungs: Map<String, String> = emptyMap(),
    val hiitDoneEver: Int = 0,
    /** DEL-002 chose a deload for the current deload-or-pivot week. */
    val deloadThisWeek: Boolean = false,
    /** Weeks since the last DEL-002 lighter or deload week. */
    val weeksSinceLighter: Int = 0,
    /** A DEL-002 lighter week has just finished. */
    val justFinishedLighterWeek: Boolean = false,
    /** AER-003: this week's Z1 session length (minutes). */
    val z1SessionMinutes: Double = 15.0,
    val carryOrRotationLastWeek: Set<Pattern> = emptySet(),
    /** PER-005 last clock action (ADVANCE, PAUSE …), for the "why" text. */
    val lastClockAction: Blueprint.ClockAction? = null,
    /** First day of the current block (HIIT protocol progression counts sessions since then, PROG-006). */
    val blockStartDay: Int = startDay,
    /** DEL-002 lighter week: sessions still capped at MODIFIED. */
    val lighterSessionsLeft: Int = 0,
    /** DEL-002 chose a lighter week for the current week. */
    val lighterThisWeek: Boolean = false,
) {
    /** A DEL-002 deload placed inside a block (not at its deload-or-pivot week): the block clock waits for it. */
    fun insertedDeload(ctx: com.personalfitnesscoach.engine.program.WeekContext): Boolean =
        deloadThisWeek && ctx.kind != com.personalfitnesscoach.engine.program.WeekKind.DELOAD_OR_PIVOT

    companion object Codec : DocCodec<ProgramRecord>("program", 1, RecordKind.DERIVED, setOf(DataItem.PRIORITIES, DataItem.DAYS, DataItem.LOGGED_SETS),
        "Your programme and where you are in it") {
        override fun key(v: ProgramRecord) = SINGLE
        override fun write(v: ProgramRecord, o: Obj) = with(o) {
            strings("priorities", v.priorities.map { it.name }, sort = false); put("startDay", v.startDay); put("weekStartDay", v.weekStartDay)
            put("clockWeek", v.clockWeek); put("weeksTraining", v.weeksTraining); put("blockIndex", v.blockIndex)
            stringMap("coreLifts", v.coreLifts); stringMap("previousBlockChoices", v.previousBlockChoices); stringMap("ladderRungs", v.ladderRungs)
            put("hiitDoneEver", v.hiitDoneEver); flag("deloadThisWeek", v.deloadThisWeek); put("weeksSinceLighter", v.weeksSinceLighter)
            flag("justFinishedLighterWeek", v.justFinishedLighterWeek); put("z1SessionMinutes", v.z1SessionMinutes)
            enums("carryOrRotationLastWeek", v.carryOrRotationLastWeek); put("lastClockAction", v.lastClockAction)
            put("blockStartDay", v.blockStartDay); if (v.lighterSessionsLeft != 0) put("lighterSessionsLeft", v.lighterSessionsLeft); flag("lighterThisWeek", v.lighterThisWeek)
        }
        override fun read(o: JsonObject, version: Int) = ProgramRecord(
            o.strings("priorities").map { parseEnum<Goal>(it, "priorities") }, o.int("startDay"), o.int("weekStartDay"), o.int("clockWeek"),
            o.int("weeksTraining"), o.int("blockIndex"), o.stringMap("coreLifts"), o.stringMap("previousBlockChoices"), o.stringMap("ladderRungs"),
            o.int("hiitDoneEver"), o.bool("deloadThisWeek"), o.int("weeksSinceLighter"), o.bool("justFinishedLighterWeek"), o.dbl("z1SessionMinutes"),
            o.enums("carryOrRotationLastWeek"), o.enumOrNull<Blueprint.ClockAction>("lastClockAction"), o.int("blockStartDay"), o.int("lighterSessionsLeft", 0), o.bool("lighterThisWeek"))
    }
}

// ==================================================================================================== week summary
/**
 * One finished calendar week (Week, WorkloadLog and ProgressMetric rolled together): what was planned and done, and the totals the
 * next week's rules start from (AER-003, PROG-007, FL-002, VOL-007, LOAD-005, ADH-001).
 */
data class WeekSummary(
    val weekStartDay: Int,
    val clockWeek: Int,
    val planned: Int,
    val completed: Int,
    val aerobicMinutes: Double = 0.0,
    val z1Minutes: Double = 0.0,
    val z2PlusMinutes: Double = 0.0,
    val hiitSessions: Int = 0,
    val hiitWorkMinutes: Double = 0.0,
    val walkingMinutes: Double = 0.0,
    /** PH-001 equivalent minutes (Z1 + 2 × harder work + brisk walking), the FL-002 starting point. */
    val equivalentMinutes: Double = 0.0,
    val ssu: Double = 0.0,
    /** LOAD-001 weekly load (sum of session RPE × minutes). */
    val workload: Double = 0.0,
    val deload: Boolean = false,
    val clockAction: Blueprint.ClockAction? = null,
) {
    companion object Codec : DocCodec<WeekSummary>("week", 1, RecordKind.DERIVED, setOf(DataItem.LOGGED_SETS, DataItem.SESSION_RPE_DURATION,
        DataItem.DAILY_STEPS_PHONE_SENSOR), "Weekly totals") {
        override fun key(v: WeekSummary) = dayKey(v.weekStartDay)
        override fun day(v: WeekSummary) = v.weekStartDay
        override fun write(v: WeekSummary, o: Obj) = with(o) {
            put("weekStartDay", v.weekStartDay); put("clockWeek", v.clockWeek); put("planned", v.planned); put("completed", v.completed)
            put("aerobicMinutes", v.aerobicMinutes); put("z1Minutes", v.z1Minutes); put("z2PlusMinutes", v.z2PlusMinutes)
            put("hiitSessions", v.hiitSessions); put("hiitWorkMinutes", v.hiitWorkMinutes); put("walkingMinutes", v.walkingMinutes)
            put("equivalentMinutes", v.equivalentMinutes); put("ssu", v.ssu); put("workload", v.workload); flag("deload", v.deload)
            put("clockAction", v.clockAction)
        }
        override fun read(o: JsonObject, version: Int) = WeekSummary(o.int("weekStartDay"), o.int("clockWeek"), o.int("planned"), o.int("completed"),
            o.dbl("aerobicMinutes"), o.dbl("z1Minutes"), o.dbl("z2PlusMinutes"), o.int("hiitSessions"), o.dbl("hiitWorkMinutes"),
            o.dbl("walkingMinutes"), o.dbl("equivalentMinutes"), o.dbl("ssu"), o.dbl("workload"), o.bool("deload"), o.enumOrNull<Blueprint.ClockAction>("clockAction"))
    }
}

/** The plan for the current week as it was last made (weekdays and templates), so the block clock knows what was planned. */
data class WeekPlanRecord(val weekStartDay: Int, val days: List<Pair<Int, DayTemplate>>, val deload: Boolean, val walkDays: List<Int> = emptyList(),
                          /** AER-003: the Z1 session length planned this week (the longest Z1 block on a strength day), if any. */
                          val z1SessionMinutes: Double? = null) {
    val planned: Int get() = days.size

    companion object Codec : DocCodec<WeekPlanRecord>("week_plan", 1, RecordKind.DERIVED, setOf(DataItem.DAYS, DataItem.PRIORITIES), "This week's plan") {
        override fun key(v: WeekPlanRecord) = dayKey(v.weekStartDay)
        override fun day(v: WeekPlanRecord) = v.weekStartDay
        override fun write(v: WeekPlanRecord, o: Obj) = with(o) {
            put("weekStartDay", v.weekStartDay)
            objs("days", v.days.map { (wd, t) -> obj { put("weekday", wd); put("template", t) } })
            flag("deload", v.deload); ints("walkDays", v.walkDays); put("z1SessionMinutes", v.z1SessionMinutes)
        }
        override fun read(o: JsonObject, version: Int) = WeekPlanRecord(o.int("weekStartDay"),
            o.objs("days").map { it.int("weekday") to it.enum<DayTemplate>("template") }, o.bool("deload"), o.ints("walkDays"), o.dblOrNull("z1SessionMinutes"))
    }
}

// ==================================================================================================== per-exercise state
/** CAL-001 over sessions 1–4: where the next session's ramp starts, and how many calibration sessions have run. */
data class CalibrationState(val nextLoad: Double, val sessions: Int, val ceiling: Double? = null)

/**
 * What the progression rules carry from one exposure of an exercise to the next (PerformanceMetric plus the FS-5 prescription):
 * the e1RM (INT-004), the next-exposure prescription (PROG-001…008, D-055), calibration in progress (CAL-001), and the PROG-004 /
 * PROG-008 streaks. Derived only from logged sets and known numbers; recomputable from them.
 */
data class ExerciseState(
    val exerciseId: String,
    val e1rm: Double? = null,
    val e1rmDay: Int? = null,
    val prescription: Prescription? = null,
    val prescriptionDay: Int? = null,
    val calibration: CalibrationState? = null,
    val formNoStreak: Int = 0,
    val previousWasReduction: Boolean = false,
    val loadBeforeReductions: Double? = null,
    val lastDoneDay: Int? = null,
    val exposures: Int = 0,
    /** CAL-002: the user's own number has been turned into this state (it is not applied again). */
    val knownApplied: Boolean = false,
) {
    // Plausible values only (a restored file cannot bring in a 5000 kg reference that would lift every cap; re-check finding 8).
    init {
        require(e1rm == null || e1rm > 0.0 && e1rm <= 1000.0) { "e1RM out of range" }
        require(prescription == null || prescription.load in 0.0..1000.0) { "prescribed load out of range" }
        require(calibration == null || calibration.nextLoad in 0.0..1000.0 && (calibration.ceiling ?: 0.0) in 0.0..1000.0 && calibration.sessions in 0..20) {
            "calibration out of range" }
        require(loadBeforeReductions == null || loadBeforeReductions in 0.0..1000.0) { "load out of range" }
        require(exposures >= 0 && formNoStreak >= 0) { "counts out of range" }
    }

    companion object Codec : DocCodec<ExerciseState>("exercise_state", 1, RecordKind.DERIVED, setOf(DataItem.LOGGED_SETS, DataItem.KNOWN_LOADS_AND_RECORDS),
        "Your current level on each exercise") {
        override fun key(v: ExerciseState) = v.exerciseId
        override fun write(v: ExerciseState, o: Obj) = with(o) {
            put("exerciseId", v.exerciseId); put("e1rm", v.e1rm); put("e1rmDay", v.e1rmDay)
            v.prescription?.let { p -> put("prescription", obj {
                put("load", p.load); ints("repRange", listOf(p.repRange.first, p.repRange.last), sort = false); put("repTarget", p.repTarget)
                put("action", p.action); flag("offerRegression", p.offerRegression) }) }
            put("prescriptionDay", v.prescriptionDay)
            v.calibration?.let { c -> put("calibration", obj { put("nextLoad", c.nextLoad); put("sessions", c.sessions); put("ceiling", c.ceiling) }) }
            if (v.formNoStreak != 0) put("formNoStreak", v.formNoStreak); flag("previousWasReduction", v.previousWasReduction)
            put("loadBeforeReductions", v.loadBeforeReductions); put("lastDoneDay", v.lastDoneDay); put("exposures", v.exposures)
            flag("knownApplied", v.knownApplied)
        }
        override fun read(o: JsonObject, version: Int) = ExerciseState(
            o.str("exerciseId"), o.dblOrNull("e1rm"), o.intOrNull("e1rmDay"),
            o.objOrNull("prescription")?.let { p -> Prescription(p.dbl("load"), p.range("repRange"), p.int("repTarget"), p.enum<ProgressionAction>("action"),
                p.bool("offerRegression")) },
            o.intOrNull("prescriptionDay"), o.objOrNull("calibration")?.let { CalibrationState(it.dbl("nextLoad"), it.int("sessions"), it.dblOrNull("ceiling")) },
            o.int("formNoStreak", 0), o.bool("previousWasReduction"), o.dblOrNull("loadBeforeReductions"), o.intOrNull("lastDoneDay"),
            o.int("exposures"), o.bool("knownApplied"))
    }
}

// ==================================================================================================== own numbers
/**
 * A number the user entered for one exercise (CAL-002, D-059): a recent set (load × reps, reps in reserve) or a best lift, and
 * when they last did the exercise, so its age is always measured from that date.
 */
data class KnownNumber(
    val exerciseId: String,
    val loadKg: Double,
    val reps: Int,
    val rir: Double? = null,
    val bestLift: Boolean = false,
    val lastDoneDay: Int,
    val enteredDay: Int,
) {
    init {
        require(loadKg > 0 && loadKg < 1000) { "load out of range" }
        require(reps in 1..100) { "reps out of range" }
        require(rir == null || rir in 0.0..10.0) { "reps in reserve out of range" }
        require(lastDoneDay <= enteredDay) { "last done after it was entered" }
    }

    companion object Codec : DocCodec<KnownNumber>("known", 1, RecordKind.COLLECTED, setOf(DataItem.KNOWN_LOADS_AND_RECORDS), "Starting weights and records you entered") {
        override fun key(v: KnownNumber) = v.exerciseId
        override fun write(v: KnownNumber, o: Obj) = with(o) {
            put("exerciseId", v.exerciseId); put("loadKg", v.loadKg); put("reps", v.reps); put("rir", v.rir); flag("bestLift", v.bestLift)
            put("lastDoneDay", v.lastDoneDay); put("enteredDay", v.enteredDay)
        }
        override fun read(o: JsonObject, version: Int) = KnownNumber(o.str("exerciseId"), o.dbl("loadKg"), o.int("reps"), o.dblOrNull("rir"),
            o.bool("bestLift"), o.int("lastDoneDay"), o.int("enteredDay"))
    }
}

// ==================================================================================================== decision log
/** One engine decision (DecisionLog): powers "Why?". Inputs and outputs are kept as the engine gave them. */
data class DecisionEntry(
    val seq: Long,
    val atMs: Long,
    val day: Int,
    val workoutId: Long? = null,
    val kind: String,
    val ruleIds: List<String>,
    val reason: String,
    val inputs: JsonObject = JsonObject(emptyMap()),
    val outputs: JsonObject = JsonObject(emptyMap()),
    val registryVersion: String,
) {
    companion object Codec : DocCodec<DecisionEntry>("decision", 1, RecordKind.DERIVED, setOf(DataItem.LOGGED_SETS, DataItem.READINESS),
        "Why the coach changed something") {
        override fun key(v: DecisionEntry) = v.seq.toString().padStart(19, '0')
        override fun day(v: DecisionEntry) = v.day
        override fun write(v: DecisionEntry, o: Obj) = with(o) {
            put("seq", v.seq); put("atMs", v.atMs); put("day", v.day); put("workoutId", v.workoutId); put("kind", v.kind)
            strings("ruleIds", v.ruleIds, sort = false); put("reason", v.reason)
            if (v.inputs.isNotEmpty()) put("inputs", v.inputs); if (v.outputs.isNotEmpty()) put("outputs", v.outputs)
            put("registryVersion", v.registryVersion)
        }
        override fun read(o: JsonObject, version: Int) = DecisionEntry(o.long("seq"), o.long("atMs"), o.int("day"), o.longOrNull("workoutId"),
            o.str("kind"), o.strings("ruleIds"), o.str("reason"), o.objOrNull("inputs") ?: JsonObject(emptyMap()),
            o.objOrNull("outputs") ?: JsonObject(emptyMap()), o.str("registryVersion"))

        fun from(d: Decision, seq: Long, atMs: Long, day: Int, workoutId: Long?, registryVersion: String) = DecisionEntry(seq, atMs, day, workoutId,
            d.kind.name, d.ruleIds, d.reason.name, anyToJson(d.inputs) as JsonObject, anyToJson(d.outputs) as JsonObject, registryVersion)
    }
}

internal fun requireData(ok: Boolean, msg: () -> String) { if (!ok) throw DataFormatException(msg()) }
