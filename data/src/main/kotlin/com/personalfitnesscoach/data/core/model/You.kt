package com.personalfitnesscoach.data.core.model

import com.personalfitnesscoach.data.core.json.DataFormatException
import com.personalfitnesscoach.data.core.json.Obj
import com.personalfitnesscoach.data.core.json.bool
import com.personalfitnesscoach.data.core.json.boolOrNull
import com.personalfitnesscoach.data.core.json.dbl
import com.personalfitnesscoach.data.core.json.doubleMap
import com.personalfitnesscoach.data.core.json.doubles
import com.personalfitnesscoach.data.core.json.enum
import com.personalfitnesscoach.data.core.json.enumOrNull
import com.personalfitnesscoach.data.core.json.enums
import com.personalfitnesscoach.data.core.json.int
import com.personalfitnesscoach.data.core.json.intMap
import com.personalfitnesscoach.data.core.json.intOrNull
import com.personalfitnesscoach.data.core.json.ints
import com.personalfitnesscoach.data.core.json.obj
import com.personalfitnesscoach.data.core.json.objOrNull
import com.personalfitnesscoach.data.core.json.objs
import com.personalfitnesscoach.data.core.json.parseEnum
import com.personalfitnesscoach.data.core.json.str
import com.personalfitnesscoach.data.core.json.strOrNull
import com.personalfitnesscoach.data.core.json.strings
import com.personalfitnesscoach.data.core.privacy.DataItem
import com.personalfitnesscoach.data.core.privacy.RecordKind
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.engine.calc.Inventory
import com.personalfitnesscoach.engine.calc.Stack
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.model.Muscle
import com.personalfitnesscoach.engine.program.ExperienceAnswers
import com.personalfitnesscoach.engine.program.Goal
import com.personalfitnesscoach.engine.progress.ReferenceSex
import com.personalfitnesscoach.engine.safety.ClearanceScope
import com.personalfitnesscoach.engine.safety.ControlStatus
import com.personalfitnesscoach.engine.safety.ScreeningAnswers
import com.personalfitnesscoach.engine.safety.UserCondition
import kotlinx.serialization.json.JsonObject

// ==================================================================================================== profile
/**
 * The user's training profile (Phase 2 entities User + UserProfile). Only birth year is kept for age (DATA-001 "age"); age is
 * the calendar-year difference, which can read one year older before the birthday — the conservative side for age rules.
 */
data class Profile(
    val birthYear: Int? = null,
    val level: Level = Level.BEGINNER,
    val experience: ExperienceAnswers? = null,
    val daysPerWeek: Int? = null,
    val availableDays: Set<Int> = (0..6).toSet(),
    val preferredDays: Set<Int> = emptySet(),
    val sessionMinutes: Int = 60,
    val priorities: List<Goal> = emptyList(),
    val focusMuscles: Set<Muscle> = emptySet(),
    /** IND-001 prior injuries (screening Q6 follow-up) with the day each was added: conservative for 4 weeks from then, then "sensitive". */
    val priorInjuries: Map<Joint, Int> = emptyMap(),
    /** Optional (DATA-001 "sex"): only for the waist reference line (FL-005); the training algorithm never reads it (IND-001). */
    val referenceSex: ReferenceSex? = null,
    /** Optional typed-in heart rates (DATA-001 "heart_rate") for HR zones instead of the talk test (AER-001). */
    val restingHr: Int? = null,
    val maxHr: Int? = null,
    /** Onboarding progress, so an interrupted onboarding resumes where it stopped; null when finished. */
    val onboardingStep: String? = null,
    val createdDay: Int,
) {
    init {
        require(availableDays.all { it in 0..6 } && preferredDays.all { it in 0..6 }) { "weekdays are 0–6" }
        require(sessionMinutes in 10..240) { "session length is 10–240 minutes" }
        require(birthYear == null || birthYear in 1900..2100) { "birth year out of range" }
    }

    fun age(today: Int): Int? = birthYear?.let { Days.year(today) - it }

    companion object Codec : DocCodec<Profile>("profile", 1, RecordKind.COLLECTED,
        setOf(DataItem.AGE, DataItem.EXPERIENCE, DataItem.DAYS, DataItem.SESSION_LENGTH, DataItem.PRIORITIES, DataItem.SCREENING, DataItem.SEX,
            DataItem.HEART_RATE), "Your training profile") {
        override fun key(v: Profile) = SINGLE
        override fun write(v: Profile, o: Obj) = with(o) {
            put("birthYear", v.birthYear); put("level", v.level)
            v.experience?.let { e -> put("experience", obj {
                put("monthsConsistent", e.monthsConsistent); strings("comfortableWith", e.comfortableWith)
                flag("structuredProgramming", e.structuredProgramming); flag("confidentEffortRatings", e.confidentEffortRatings) }) }
            put("daysPerWeek", v.daysPerWeek); ints("availableDays", v.availableDays); ints("preferredDays", v.preferredDays)
            put("sessionMinutes", v.sessionMinutes)
            strings("priorities", v.priorities.map { it.name }, sort = false)
            enums("focusMuscles", v.focusMuscles); intMap("priorInjuries", v.priorInjuries.mapKeys { it.key.name })
            put("referenceSex", v.referenceSex); put("restingHr", v.restingHr); put("maxHr", v.maxHr)
            put("onboardingStep", v.onboardingStep); put("createdDay", v.createdDay)
        }
        override fun read(o: JsonObject, version: Int) = Profile(
            birthYear = o.intOrNull("birthYear"), level = o.enum("level"),
            experience = o.objOrNull("experience")?.let { e -> ExperienceAnswers(e.int("monthsConsistent"), e.strings("comfortableWith").toSet(),
                e.bool("structuredProgramming"), e.bool("confidentEffortRatings")) },
            daysPerWeek = o.intOrNull("daysPerWeek"), availableDays = o.ints("availableDays").toSet(), preferredDays = o.ints("preferredDays").toSet(),
            sessionMinutes = o.int("sessionMinutes"), priorities = o.strings("priorities").map { parseEnum<Goal>(it, "priorities") },
            focusMuscles = o.enums("focusMuscles"), priorInjuries = o.intMap("priorInjuries").mapKeys { parseEnum<Joint>(it.key, "priorInjuries") }, referenceSex = o.enumOrNull<ReferenceSex>("referenceSex"),
            restingHr = o.intOrNull("restingHr"), maxHr = o.intOrNull("maxHr"), onboardingStep = o.strOrNull("onboardingStep"),
            createdDay = o.int("createdDay"),
        )
    }
}

// ==================================================================================================== screening
/** SAF-001 answers (ScreeningResult in Phase 2). The outcome is computed by the engine from the answers each time. */
data class ScreeningRecord(val answers: ScreeningAnswers, val takenDay: Int, val clearanceConfirmedDay: Int? = null) {
    companion object Codec : DocCodec<ScreeningRecord>("screening", 1, RecordKind.COLLECTED, setOf(DataItem.SCREENING), "Your screening answers") {
        override fun key(v: ScreeningRecord) = SINGLE
        override fun write(v: ScreeningRecord, o: Obj) = with(o) {
            val a = v.answers
            put("answers", obj {
                put("heartOrBloodPressure", a.heartOrBloodPressure); put("metabolicRenalPulmonary", a.metabolicRenalPulmonary)
                put("symptoms", a.symptoms); put("palpitations", a.palpitations); put("limitOrPregnancy", a.limitOrPregnancy)
                put("musculoskeletal", a.musculoskeletal); put("longTermMedication", a.longTermMedication); put("regularlyActive", a.regularlyActive)
            })
            put("takenDay", v.takenDay); put("clearanceConfirmedDay", v.clearanceConfirmedDay)
        }
        override fun read(o: JsonObject, version: Int): ScreeningRecord {
            val a = o.obj("answers")
            fun req(k: String) = a.boolOrNull(k) ?: throw DataFormatException("missing screening answer '$k'")
            return ScreeningRecord(ScreeningAnswers(req("heartOrBloodPressure"), req("metabolicRenalPulmonary"), req("symptoms"), req("palpitations"),
                req("limitOrPregnancy"), req("musculoskeletal"), req("longTermMedication"), req("regularlyActive")),
                o.int("takenDay"), o.intOrNull("clearanceConfirmedDay"))
        }
    }
}

// ==================================================================================================== health conditions
/**
 * One picked health condition (SAF-010, D-058) with the user's answers. Time-based facts are stored as the date they were true
 * on, so they never go stale: `pregnancyWeek` was the week on `pregnancyWeekDay`; `birthDay` is the date of birth (postpartum).
 */
data class StoredCondition(
    val id: String,
    val addedDay: Int,
    val controlled: ControlStatus? = null,
    val clearance: Set<ClearanceScope> = emptySet(),
    val clearanceDay: Int? = null,
    val subFlags: Set<String> = emptySet(),
    val pregnancyWeek: Int? = null,
    val pregnancyWeekDay: Int? = null,
    val birthDay: Int? = null,
    val attested: Boolean = false,
    val impactChecksPassed: Boolean = false,
    val impactOptIn: Boolean = false,
    val painRuleMetWeeks: Int = 0,
    val blockIfYes: Boolean = false,
    val previouslyVigorous: Boolean = false,
    val alreadyDoingImpact: Boolean = false,
    val supineUncomfortable: Boolean = false,
    val flare: Boolean = false,
) {
    /**
     * The engine's view on `today`. `trainingWeeks` is weeks with at least one completed session since the condition was added
     * ("weeks of training", the table's time-based unlocks): calendar time alone never unlocks anything.
     */
    fun toUserCondition(today: Int, trainingWeeks: Int): UserCondition = UserCondition(
        id = id, weeks = trainingWeeks, controlled = controlled, clearance = clearance, subFlags = subFlags,
        // Weeks are counted up from the day the week was given (the added day when none was stored), rounded up: limits that
        // start at a given week start no later than they should.
        pregnancyWeek = pregnancyWeek?.let { w -> w + (maxOf(0, today - (pregnancyWeekDay ?: addedDay)) + 6) / 7 },
        weeksSinceBirth = birthDay?.let { maxOf(0, today - it) / 7 },
        attested = attested, impactChecksPassed = impactChecksPassed, impactOptIn = impactOptIn, painRuleMetWeeks = painRuleMetWeeks,
        blockIfYes = blockIfYes, previouslyVigorous = previouslyVigorous, alreadyDoingImpact = alreadyDoingImpact,
        supineUncomfortable = supineUncomfortable, flare = flare,
    )

    internal fun write(o: Obj) = with(o) {
        put("id", id); put("addedDay", addedDay); put("controlled", controlled); enums("clearance", clearance); put("clearanceDay", clearanceDay)
        strings("subFlags", subFlags); put("pregnancyWeek", pregnancyWeek); put("pregnancyWeekDay", pregnancyWeekDay); put("birthDay", birthDay)
        flag("attested", attested); flag("impactChecksPassed", impactChecksPassed); flag("impactOptIn", impactOptIn)
        if (painRuleMetWeeks != 0) put("painRuleMetWeeks", painRuleMetWeeks)
        flag("blockIfYes", blockIfYes); flag("previouslyVigorous", previouslyVigorous); flag("alreadyDoingImpact", alreadyDoingImpact)
        flag("supineUncomfortable", supineUncomfortable); flag("flare", flare)
    }

    internal companion object {
        fun read(o: JsonObject) = StoredCondition(
            id = o.str("id"), addedDay = o.int("addedDay"), controlled = o.enumOrNull<ControlStatus>("controlled"), clearance = o.enums("clearance"),
            clearanceDay = o.intOrNull("clearanceDay"), subFlags = o.strings("subFlags").toSet(), pregnancyWeek = o.intOrNull("pregnancyWeek"),
            pregnancyWeekDay = o.intOrNull("pregnancyWeekDay"), birthDay = o.intOrNull("birthDay"), attested = o.bool("attested"),
            impactChecksPassed = o.bool("impactChecksPassed"), impactOptIn = o.bool("impactOptIn"), painRuleMetWeeks = o.int("painRuleMetWeeks", 0),
            blockIfYes = o.bool("blockIfYes"), previouslyVigorous = o.bool("previouslyVigorous"), alreadyDoingImpact = o.bool("alreadyDoingImpact"),
            supineUncomfortable = o.bool("supineUncomfortable"), flare = o.bool("flare"),
        )
    }
}

/** Everything picked in the condition picker (CR-001). An empty list means "none of these". */
data class ConditionsRecord(val items: List<StoredCondition>, val answeredDay: Int) {
    init { require(items.map { it.id }.toSet().size == items.size) { "a condition can be picked once" } }

    companion object Codec : DocCodec<ConditionsRecord>("conditions", 1, RecordKind.COLLECTED, setOf(DataItem.HEALTH_CONDITIONS_AND_CLEARANCE),
        "Health conditions you picked and what a doctor OK'd") {
        override fun key(v: ConditionsRecord) = SINGLE
        override fun write(v: ConditionsRecord, o: Obj) = with(o) {
            objs("items", v.items.map { c -> obj { c.write(this) } }); put("answeredDay", v.answeredDay)
        }
        override fun read(o: JsonObject, version: Int) = ConditionsRecord(o.objs("items").map { StoredCondition.read(it) }, o.int("answeredDay"))
    }
}

// ==================================================================================================== equipment
/**
 * The equipment inventory (EquipmentItem): what the gym has, the real increments, items unavailable until a date, and the
 * away-from-gym kit (EQ-003).
 */
data class EquipmentRecord(
    val gym: Set<String>,
    val inventory: Inventory = Inventory(),
    /** Equipment ID → first day it is available again. */
    val unavailableUntil: Map<String, Int> = emptyMap(),
    val homeKit: Set<String> = emptySet(),
) {
    /** What can be used on `day` (empty = bodyweight only, D-072). */
    fun availableOn(day: Int): Set<String> = gym.filterTo(HashSet()) { (unavailableUntil[it] ?: Int.MIN_VALUE) <= day }

    companion object Codec : DocCodec<EquipmentRecord>("equipment", 1, RecordKind.COLLECTED, setOf(DataItem.EQUIPMENT_INVENTORY), "Your equipment") {
        override fun key(v: EquipmentRecord) = SINGLE
        private fun stack(s: Stack) = obj { put("minKg", s.minKg); put("maxKg", s.maxKg); put("stepKg", s.stepKg) }
        private fun stack(o: JsonObject) = Stack(o.dbl("minKg"), o.dbl("maxKg"), o.dbl("stepKg"))
        override fun write(v: EquipmentRecord, o: Obj) = with(o) {
            strings("gym", v.gym)
            val i = v.inventory
            put("inventory", obj {
                put("barKg", i.barKg)
                put("plates", JsonObject(i.plates.toSortedMap(compareByDescending { it }).map { (kg, n) -> kg.toString() to kotlinx.serialization.json.JsonPrimitive(n) }.toMap()))
                doubles("dumbbells", i.dumbbells.sorted()); doubles("kettlebells", i.kettlebells.sorted())
                put("stack", stack(i.stack))
                doubleMap("bars", i.bars)
                if (i.stacks.isNotEmpty()) put("stacks", JsonObject(i.stacks.toSortedMap().mapValues { stack(it.value) }))
            })
            intMap("unavailableUntil", v.unavailableUntil); strings("homeKit", v.homeKit)
        }
        override fun read(o: JsonObject, version: Int): EquipmentRecord {
            val i = o.obj("inventory")
            val plates = i.intMap("plates").mapKeys { (k, _) -> k.toDoubleOrNull() ?: throw DataFormatException("plate weight '$k' is not a number") }
            return EquipmentRecord(o.strings("gym").toSet(), Inventory(barKg = i.dbl("barKg"), plates = plates, dumbbells = i.doubles("dumbbells"),
                kettlebells = i.doubles("kettlebells"), stack = stack(i.obj("stack")), bars = i.doubleMap("bars"),
                stacks = (i.objOrNull("stacks") ?: JsonObject(emptyMap())).mapValues { (k, s) ->
                    stack(s as? JsonObject ?: throw DataFormatException("stack '$k' is not an object")) }),
                o.intMap("unavailableUntil"), o.strings("homeKit").toSet())
        }
    }
}

// ==================================================================================================== preferences
/** Likes, dislikes and choices (UserPreference; SUB-002/003 learning, MOD-001 2.0.0 machines, FL-003 opt-ins). */
data class PreferencesRecord(
    val exerciseScores: Map<String, Double> = emptyMap(),
    val excludedIds: Set<String> = emptySet(),
    val favourites: Set<String> = emptySet(),
    val modalityScores: Map<Modality, Double> = emptyMap(),
    val excludedModalities: Set<Modality> = emptySet(),
    val hiitOptIn: Boolean = false,
    val circuitJumps: Boolean = false,
    val crowdedGym: Boolean = false,
) {
    companion object Codec : DocCodec<PreferencesRecord>("preferences", 1, RecordKind.COLLECTED, setOf(DataItem.EXERCISE_PREFERENCES),
        "Exercises and machines you like or skip") {
        override fun key(v: PreferencesRecord) = SINGLE
        override fun write(v: PreferencesRecord, o: Obj) = with(o) {
            doubleMap("exerciseScores", v.exerciseScores); strings("excludedIds", v.excludedIds); strings("favourites", v.favourites)
            doubleMap("modalityScores", v.modalityScores.mapKeys { it.key.name }); enums("excludedModalities", v.excludedModalities)
            flag("hiitOptIn", v.hiitOptIn); flag("circuitJumps", v.circuitJumps); flag("crowdedGym", v.crowdedGym)
        }
        override fun read(o: JsonObject, version: Int) = PreferencesRecord(
            o.doubleMap("exerciseScores"), o.strings("excludedIds").toSet(), o.strings("favourites").toSet(),
            o.doubleMap("modalityScores").mapKeys { parseEnum<Modality>(it.key, "modalityScores") }, o.enums("excludedModalities"),
            o.bool("hiitOptIn"), o.bool("circuitJumps"), o.bool("crowdedGym"))
    }
}

// ==================================================================================================== settings
enum class Units { KG, LB }
enum class Theme { SYSTEM, LIGHT, DARK }

/** App settings (no personal data) plus the backup reminder state (Phase 2 section 12). */
data class SettingsRecord(
    val units: Units = Units.KG,
    val theme: Theme = Theme.SYSTEM,
    /** D-062: step tracking is off until the user turns it on (and grants activity recognition). */
    val stepTracking: Boolean = false,
    val lastExportDay: Int? = null,
    val completedAtLastExport: Int = 0,
    /** Completed-workout count when the reminder card was last dismissed (it comes back after 8 more). */
    val reminderDismissedAt: Int? = null,
) {
    companion object Codec : DocCodec<SettingsRecord>("settings", 1, RecordKind.APP, emptySet(), "App settings") {
        override fun key(v: SettingsRecord) = SINGLE
        override fun write(v: SettingsRecord, o: Obj) = with(o) {
            put("units", v.units); put("theme", v.theme); flag("stepTracking", v.stepTracking); put("lastExportDay", v.lastExportDay)
            if (v.completedAtLastExport != 0) put("completedAtLastExport", v.completedAtLastExport); put("reminderDismissedAt", v.reminderDismissedAt)
        }
        override fun read(o: JsonObject, version: Int) = SettingsRecord(o.enum("units"), o.enum("theme"), o.bool("stepTracking"),
            o.intOrNull("lastExportDay"), o.int("completedAtLastExport", 0), o.intOrNull("reminderDismissedAt"))
    }
}
