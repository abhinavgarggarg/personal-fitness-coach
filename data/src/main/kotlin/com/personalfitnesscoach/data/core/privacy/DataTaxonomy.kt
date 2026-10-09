package com.personalfitnesscoach.data.core.privacy

/**
 * DATA-001 1.1.0, the data taxonomy, as code. Every item the app may keep is listed with its class; the "never" items exist only
 * so tests can prove nothing stored maps to them. `key` is the registry's name for the item (P.DATA_001 lists).
 */
enum class DataItem(val key: String, val itemClass: ItemClass) {
    AGE("age", ItemClass.REQUIRED),
    EXPERIENCE("experience", ItemClass.REQUIRED),
    DAYS("days", ItemClass.REQUIRED),
    SESSION_LENGTH("session_length", ItemClass.REQUIRED),
    EQUIPMENT_INVENTORY("equipment_inventory", ItemClass.REQUIRED),
    SCREENING("screening", ItemClass.REQUIRED),
    PRIORITIES("priorities", ItemClass.REQUIRED),
    LOGGED_SETS("logged_sets", ItemClass.REQUIRED),
    SESSION_RPE_DURATION("session_rpe_duration", ItemClass.REQUIRED),
    READINESS("readiness", ItemClass.REQUIRED),
    PAIN_REPORTS("pain_reports", ItemClass.REQUIRED),

    SLEEP_HOURS("sleep_hours", ItemClass.OPTIONAL),
    BODYWEIGHT("bodyweight", ItemClass.OPTIONAL),
    BODY_MEASUREMENTS("body_measurements", ItemClass.OPTIONAL),
    HEART_RATE("heart_rate", ItemClass.OPTIONAL),
    SEX("sex", ItemClass.OPTIONAL),
    EXERCISE_PREFERENCES("exercise_preferences", ItemClass.OPTIONAL),

    // Added by Research Update 1.1 (CR-001, CR-002, CR-005): optional.
    DAILY_STEPS_PHONE_SENSOR("daily_steps_phone_sensor", ItemClass.OPTIONAL_ADDED),
    HEALTH_CONDITIONS_AND_CLEARANCE("health_conditions_and_clearance", ItemClass.OPTIONAL_ADDED),
    KNOWN_LOADS_AND_RECORDS("known_loads_and_records", ItemClass.OPTIONAL_ADDED),

    LOCATION("location", ItemClass.NEVER),
    CONTACTS("contacts", ItemClass.NEVER),
    PHOTOS_CAMERA("photos_camera", ItemClass.NEVER),
    MICROPHONE("microphone", ItemClass.NEVER),
    ADVERTISING_ID("advertising_id", ItemClass.NEVER),
    SOCIAL_PROFILES("social_profiles", ItemClass.NEVER),
    THIRD_PARTY_STEPS_V1("third_party_steps_v1", ItemClass.NEVER),
    CALORIE_INTAKE("calorie_intake", ItemClass.NEVER),
    VANITY_METRICS("vanity_metrics", ItemClass.NEVER);

    val allowed: Boolean get() = itemClass != ItemClass.NEVER
}

enum class ItemClass { REQUIRED, OPTIONAL, OPTIONAL_ADDED, NEVER }

/**
 * What a stored record is, for DATA-001:
 *  - COLLECTED: the user's own answers or logs (the items in `items`);
 *  - DERIVED: computed by the engine only from collected items (e1RM, programme position, the decision log);
 *  - APP: app settings that say nothing about the user (units, theme, last export date).
 */
enum class RecordKind { COLLECTED, DERIVED, APP }

/** One stored record type with the taxonomy items it holds or is derived from. */
data class StoredType(val type: String, val kind: RecordKind, val items: Set<DataItem>, val what: String)
