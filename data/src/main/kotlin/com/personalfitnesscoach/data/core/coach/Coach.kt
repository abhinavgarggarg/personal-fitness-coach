package com.personalfitnesscoach.data.core.coach

import com.personalfitnesscoach.data.core.PfcData
import com.personalfitnesscoach.data.core.insight.Insights
import com.personalfitnesscoach.data.core.model.DecisionEntry
import com.personalfitnesscoach.data.core.model.ReadinessRecord
import com.personalfitnesscoach.data.core.model.dayKey
import com.personalfitnesscoach.data.core.time.Days
import com.personalfitnesscoach.engine.generation.SessionGenerator
import com.personalfitnesscoach.engine.library.Library
import com.personalfitnesscoach.engine.model.Muscle
import com.personalfitnesscoach.engine.model.Pattern
import com.personalfitnesscoach.engine.model.Tier
import com.personalfitnesscoach.engine.planning.Substitution
import com.personalfitnesscoach.engine.conditioning.BlockKind
import com.personalfitnesscoach.engine.program.Blueprint
import com.personalfitnesscoach.engine.program.PlannedDay
import com.personalfitnesscoach.engine.program.SlotRole
import com.personalfitnesscoach.engine.progress.LiftTrend
import com.personalfitnesscoach.engine.progress.TrendDirection
import com.personalfitnesscoach.engine.registry.Registry
import com.personalfitnesscoach.engine.registry.RuleIds

/**
 * The questions the Coach tab suggests (master prompt C10; Phase 2 F13 acceptance: each gets a correct, plain answer citing a real
 * decision or rule). [HARDER] is understood but never suggested: the coach explains why it can't raise the level itself (SAF-009).
 */
enum class Question(val suggested: Boolean = true) {
    WHY_THIS_EXERCISE, WHAT_IT_TRAINS, CAN_I_REPLACE, WHY_TODAY_DIFFERENT, WHY_LESS_VOLUME, ONLY_N_MINUTES, TIRED_TODAY,
    IMPROVED_THIS_MONTH, FOCUS_NEXT, WHY_TARGET_CHANGED, WHY_LIGHTER_WEEK, HOW_HARD,
    HARDER(suggested = false);

    companion object {
        val SUGGESTED: List<Question> = entries.filter { it.suggested }
    }
}

/**
 * SAF-009: what the coach does not answer. It refers to a professional (and, for symptoms, to the warning-signs guidance) and adds
 * nothing of its own — no diagnosis, no medicine, supplement or diet advice. Pain and illness get the app's own route (SAF-003/004,
 * the check-in's illness questions).
 */
enum class OutOfScope { URGENT_SYMPTOMS, MEDICATION, MEDICAL, ILLNESS, PAIN, SUPPLEMENTS, DIET }

/** What a typed question was understood as. Nothing in the text is ever executed: it only selects one of the fixed answers. */
data class Match(val question: Question?, val outOfScope: OutOfScope?, val minutes: Int?, val exerciseId: String?, val suggestions: List<Question>)

/** What the coach may do: open a screen or ask the engine, which checks everything (SAF-009 "can only request engine actions"). */
sealed interface CoachAction {
    /** TIME-001…004: fit today's session to this many minutes (the preview shows the engine's result). */
    data class ShorterSession(val minutes: Int) : CoachAction
    /** RDY-007: an easier level is always allowed; the preview offers it. */
    data object EasierLevel : CoachAction
    data object OpenCheckIn : CoachAction
    /** The preview, where the engine offers only the levels today allows. */
    data object OpenPreview : CoachAction
    data object OpenProgress : CoachAction
    data object OpenWarningSigns : CoachAction
    /** The same warning signs, offered quietly under an answer the coach could not place (unknown or non-English text, review R6-01). */
    data object CheckWarningSigns : CoachAction
    /** Ask the same question about another exercise. */
    data class AskAbout(val question: Question, val exerciseId: String) : CoachAction
}

/** The facts an answer is built from — all read from storage, the plan and the library; the screen turns them into words. */
sealed interface Fact {
    data class ExerciseRole(val exerciseId: String, val role: SlotRole?, val main: Boolean, val pattern: Pattern, val coreLift: Boolean,
                            val favourite: Boolean) : Fact
    data class Trains(val exerciseId: String, val pattern: Pattern, val primary: List<Muscle>, val secondary: List<Muscle>) : Fact
    data class Replacements(val exerciseId: String, val options: List<String>) : Fact
    data class TodayLevel(val tier: Tier?, val checkedIn: Boolean) : Fact
    /** The decisions behind the answer, newest first (each has a plain line and its rules in "Why?"). */
    data class Decisions(val entries: List<DecisionEntry>) : Fact
    data class Minutes(val minutes: Int, val usual: Int) : Fact
    data class Improved(val liftsUp: List<LiftTrend>, val liftsFlat: Int, val sessionsThisMonth: Int, val sessionsLastMonth: Int,
                        val whoWeeks: Int, val streakWeeks: Int) : Fact
    data class Focus(val block: BlockKind?, val weeksLeftInBlock: Int?, val next: PlannedDay?, val lastWeekMinutes: Double?, val stepTarget: Int?) : Fact
    data class Effort(val targetRir: Double?, val beginnerPrompt: Boolean) : Fact
    data class LighterWeek(val deloadThisWeek: Boolean, val lighterSessionsLeft: Int) : Fact
    /** No data yet for this answer (e.g. no finished week): the answer says what will appear and when. */
    data object NotYet : Fact
}

data class CoachAnswer(
    val question: Question?,
    val outOfScope: OutOfScope?,
    val facts: List<Fact>,
    /** Rule IDs the answer rests on (title and confidence one tap deeper in "Why?"). */
    val rules: List<String>,
    val actions: List<CoachAction>,
    /** For an unknown question: the 3 closest suggested questions (Phase 2 FS-9). */
    val suggestions: List<Question> = emptyList(),
)

/** "Why?" for one decision: its plain line (by reason key), and each rule with its title and confidence label. */
data class Why(val reason: String, val rules: List<RuleRef>)
data class RuleRef(val id: String, val title: String?, val confidence: String?) {
    /** The registry's confidence label as a level the screen words plainly (review R6-16). */
    val evidence: Evidence? get() = Evidence.of(confidence)
}

/** The registry's five confidence labels (Phase 1 evidence scale). */
enum class Evidence {
    HIGH, MODERATE, LIMITED, EXPERT, PRODUCT;

    companion object {
        fun of(label: String?): Evidence? = when {
            label == null -> null
            label.startsWith("High") -> HIGH
            label.startsWith("Moderate") -> MODERATE
            label.startsWith("Limited") -> LIMITED
            label.startsWith("Expert") -> EXPERT
            label.startsWith("Product") -> PRODUCT
            else -> null
        }
    }
}

/**
 * The Tier 1 coach (Phase 2 section 9, FS-9; SAF-009, COACH-001): offline and deterministic. It explains decisions the engine already
 * made — from the decision log, the plan and the rule registry — and can only ask the engine for a change (a shorter session, an easier
 * level), which the engine checks like any other. It never writes sets, reps or loads; words come from fixed, reviewed templates.
 */
class Coach(private val d: PfcData) {
    private val insights = Insights(d)

    // -------------------------------------------------------------------------------------------- understanding a question
    fun match(text: String): Match = Companion.match(text)

    companion object {
        /** The minutes in the suggested question "I only have 40 minutes" (q_only_n_minutes). */
        const val SUGGESTED_MINUTES = 40

        /** Lower-case words only; anything else in the text is ignored (prompt injection is just more words, SAF-009). */
        private fun norm(t: String) = (" " + t.lowercase().replace('’', '\'').replace(Regex("[^a-z0-9' ]+"), " ") + " ")
            .replace("'s ", " ").replace("'", "").replace(Regex("\\s+"), " ").trim().let { " $it " }

        private fun words(t: String): List<String> = t.trim().split(' ').filter { it.isNotEmpty() }

        /**
         * Out of scope (SAF-009), checked before any question and in this order, so a symptom always wins. A key is a word, a word stem
         * ending in `*` ("faint*" also finds "fainted") or a phrase of them. Urgent and pain words also match one typing slip of the same
         * length ("pian", "chets"; a swapped pair of letters, or one wrong letter in a longer word) — never a shorter or longer word, so
         * "train" is not "strain" (review R6-01/02).
         */
        private val OUT: List<Pair<OutOfScope, List<String>>> = listOf(
            OutOfScope.URGENT_SYMPTOMS to listOf("faint*", "pass* out", "black* out", "blackout*", "collaps*", "dizz*", "lighthead*", "light headed",
                "palpitat*", "breathless*", "short of breath", "shortness of breath", "out of breath", "cant breathe", "cannot breathe", "can not breathe",
                "trouble breathing", "difficulty breathing", "hard to breathe", "struggling to breathe", "cant catch my breath", "heart attack",
                "a stroke", "having a stroke", "had a stroke", "stroke symptoms", "numb", "numbness", "one side of my face", "one side of my body",
                "weak on one side", "weakness on one side", "numb on one side", "one sided weakness", "one sided numbness", "face droop*",
                "drooping face", "slurr*", "cant speak", "cannot speak",
                "trouble speaking", "severe headache", "sudden headache", "worst headache", "severe pain", "sudden pain", "irregular heart*",
                "heart racing", "racing heart", "heart pounding", "pounding heart", "heart flutter*", "skipped beat*", "skipping beat*", "angina"),
            OutOfScope.MEDICATION to listOf("medication*", "medicine*", "meds", "pill", "pills", "tablets", "insulin", "metformin", "ibuprofen",
                "aspirin", "paracetamol", "acetaminophen", "painkiller*", "pain killer*", "steroid*", "drug", "drugs", "prescri*", "beta blocker*",
                "blocker*", "statin*", "antidepress*", "inhaler*", "blood thinner*", "warfarin", "dose", "dosage", "side effect*", "antibiotic*",
                "birth control", "hrt", "injection*", "ozempic", "semaglutide", "wegovy"),
            OutOfScope.MEDICAL to listOf("diagnos*", "pregnan*", "postpartum", "post partum", "gave birth", "had a baby", "after birth", "surger*",
                "surgical", "operation", "doctor*", "gp", "physio*", "physician", "specialist", "cardiolog*", "hospital*", "diabet*", "hypertens*",
                "blood pressure", "high bp", "asthma*", "arthrit*", "osteopor*", "osteopen*", "heart condition", "heart disease", "heart problem*",
                "cancer", "chemo*", "epilep*", "seizure*", "copd", "kidney*", "thyroid*", "cholesterol", "blood sugar", "sugar level*", "fractur*",
                "torn", "tore", "broken bone*", "broken arm", "broken leg", "broken wrist", "broken toe", "broken finger", "broken rib*", "broke my",
                "disease*", "hernia*", "herniated", "slipped disc", "bulging disc", "disc bulge", "scan", "mri", "x ray", "xray", "is it serious",
                "what is wrong", "whats wrong", "tendin*", "sciatica", "concussion*", "stitches", "health condition", "medical*", "cardiac",
                "pins and needles", "condition"),
            OutOfScope.ILLNESS to listOf("sick", "illness", "feel ill", "feeling ill", "im ill", "am ill", "been ill", "flu", "fever*", "covid*",
                "a cold", "head cold", "infection*", "sore throat", "virus", "cough*", "runny nose", "temperature", "vomit*", "nause*", "diarrh*",
                "stomach bug"),
            OutOfScope.PAIN to listOf("hurt*", "pain*", "ache*", "aching", "achy", "sore*", "injur*", "pulled", "strain*", "sprain*", "tweak*",
                "twinge*", "pinched", "pinching", "swollen", "swelling", "cramp*", "popped", "popping", "gave out", "gives way", "buckl*", "tender", "stiff*",
                "bruis*", "headache*", "backache*"),
            OutOfScope.SUPPLEMENTS to listOf("supplement*", "protein powder", "protein shake*", "creatine", "pre workout", "preworkout", "vitamin*",
                "multivitamin*", "bcaa*", "whey", "fat burner*", "caffeine pill*", "collagen", "fish oil", "omega*", "magnesium", "zinc",
                "ashwagandha", "test booster*", "electrolyte*"),
            OutOfScope.DIET to listOf("diet*", "calori*", "eat", "eating", "ate", "food*", "meal*", "fasting", "keto", "carb*", "macro*", "protein",
                "nutrition*", "nutrient*", "deficit", "surplus", "snack*", "breakfast", "lunch", "dinner", "alcohol", "beer", "wine", "drink*",
                "hydrat*", "sugar"),
        )

        /** Words typed with one slip still count for these (same length only). "stroke" is left out: "strike" and "strobe" are too close. */
        private val TYPO_TOPICS = setOf(OutOfScope.URGENT_SYMPTOMS, OutOfScope.PAIN)

        /** Chest or heart with a word of pain or pressure is a warning sign, however it is put ("my chest is tight", "pain in my chest"). */
        private val CHEST = listOf("chest", "heart")
        private val CHEST_FEELING = listOf("pain*", "hurt*", "tight*", "pressure", "ache*", "aching", "squeez*", "heavy", "heaviness", "sore*",
            "burn*", "discomfort", "weird", "funny", "strange", "racing", "pounding", "flutter*", "irregular", "fast")
        /** Exercise names and plain workout words with "chest" in them are not the chest itself ("my shoulder hurts on chest press"). */
        private val CHEST_EXERCISE = Regex(" chest (press|fly|flye|flies|supported|day|exercise|exercises|workout|muscles|muscle|stretch|dip|dips) ")

        /** One pair of neighbouring letters swapped ("pian"), or — when `sub` — one wrong letter in a word of 6 letters or more ("palpatations"). */
        private fun near(word: String, key: String, sub: Boolean): Boolean {
            if (word == key) return true
            if (word.length != key.length || key.length < 4) return false
            val diff = word.indices.filter { word[it] != key[it] }
            if (diff.size == 2 && diff[1] == diff[0] + 1 && word[diff[0]] == key[diff[1]] && word[diff[1]] == key[diff[0]]) return true
            return sub && diff.size == 1 && key.length >= 6
        }

        private fun part(word: String, key: String, typos: Boolean, sub: Boolean): Boolean {
            val stem = key.endsWith('*')
            val k = key.removeSuffix("*")
            return if (stem) word.startsWith(k) || (typos && word.length >= k.length && near(word.take(k.length), k, sub))
            else word == k || (typos && k != "stroke" && near(word, k, sub))
        }

        /** Whether the words contain the key (one word, or a phrase of consecutive words). */
        private fun has(ws: List<String>, key: String, typos: Boolean, sub: Boolean = false): Boolean {
            val parts = key.split(' ')
            if (parts.size > ws.size) return false
            return (0..ws.size - parts.size).any { i -> parts.indices.all { j -> part(ws[i + j], parts[j], typos, sub) } }
        }

        /** The out-of-scope topic of a text, if any (SAF-009); urgent symptoms first. */
        fun outOfScope(text: String): OutOfScope? {
            val t = norm(text)
            val ws = words(t)
            val chestWords = words(CHEST_EXERCISE.replace(t, " "))
            if (CHEST.any { c -> has(chestWords, c, typos = true) } && CHEST_FEELING.any { f -> has(chestWords, f, typos = true) }) return OutOfScope.URGENT_SYMPTOMS
            return OUT.firstOrNull { (topic, keys) -> keys.any { k -> has(ws, k, topic in TYPO_TOPICS, sub = topic == OutOfScope.URGENT_SYMPTOMS) } }?.first
        }

        /** Phrase → weight per question. Phrases win over single words. */
        private val KEYS: Map<Question, List<Pair<String, Int>>> = mapOf(
            Question.WHY_THIS_EXERCISE to listOf("why am i doing" to 4, "why this exercise" to 4, "why do i do" to 3, "why is this exercise" to 4,
                "why this" to 2, "point of this" to 3, "why do i have to" to 3, "why exercise" to 2),
            Question.WHAT_IT_TRAINS to listOf("what does this exercise train" to 5, "what does it train" to 5, "what does this train" to 5, "what does" to 2, "train" to 1,
                "which muscles" to 4, "what muscles" to 4, "muscle" to 2, "muscles" to 2, "work on" to 1, "target" to 1, "good for" to 2),
            Question.CAN_I_REPLACE to listOf("replace" to 4, "swap" to 4, "instead" to 2, "substitute" to 4, "alternative" to 3, "another exercise" to 3,
                "don't like" to 2, "dont like" to 2, "busy" to 2, "occupied" to 3, "taken" to 1, "change the exercise" to 3, "different exercise" to 3),
            Question.WHY_TODAY_DIFFERENT to listOf("today different" to 5, "different today" to 5, "why is today" to 4, "today's workout" to 2,
                "todays workout" to 2, "today" to 1, "changed today" to 4, "why easier" to 3, "why lighter today" to 4, "why modified" to 4, "why light" to 3),
            Question.WHY_LESS_VOLUME to listOf("volume" to 4, "fewer sets" to 5, "less sets" to 5, "less work" to 3, "reduced" to 2, "cut my" to 2,
                "fewer exercises" to 4, "why so few" to 4, "reduce my volume" to 5),
            Question.ONLY_N_MINUTES to listOf("minutes" to 3, "minute" to 3, "mins" to 3, "min" to 2, "only have" to 3, "short on time" to 5,
                "no time" to 4, "quick" to 2, "hurry" to 3, "in a rush" to 4, "less time" to 4, "short session" to 4),
            Question.TIRED_TODAY to listOf("tired" to 5, "exhausted" to 5, "sleepy" to 4, "no energy" to 5, "low energy" to 5, "fatigued" to 5,
                "slept badly" to 5, "didn't sleep" to 5, "didnt sleep" to 5, "bad sleep" to 5, "drained" to 4, "worn out" to 4, "stressed" to 3),
            Question.IMPROVED_THIS_MONTH to listOf("improve" to 3, "improved" to 4, "progress" to 4, "stronger" to 4, "this month" to 3,
                "how am i doing" to 5, "results" to 3, "getting better" to 4, "better" to 1),
            Question.FOCUS_NEXT to listOf("focus" to 4, "focus on next" to 5, "what next" to 4, "what's next" to 4, "whats next" to 4,
                "next" to 1, "priority" to 3, "should i work on" to 4, "what should i" to 2),
            Question.WHY_TARGET_CHANGED to listOf("target changed" to 5, "target" to 2, "weight changed" to 4, "heavier" to 2, "why did my" to 2,
                "why is the weight" to 4, "kg" to 1, "load" to 2, "reps changed" to 4, "why more weight" to 4, "why less weight" to 4, "lower weight" to 3),
            Question.WHY_LIGHTER_WEEK to listOf("deload" to 5, "lighter week" to 5, "easy week" to 4, "recovery week" to 4, "easier week" to 4,
                "week lighter" to 4, "week easier" to 4, "rest week" to 4),
            Question.HOW_HARD to listOf("how hard" to 5, "how heavy" to 4, "rir" to 5, "reps in reserve" to 5, "effort" to 3, "to failure" to 3,
                "failure" to 2, "reps left" to 4, "rpe" to 4, "how many more" to 3, "should it feel" to 4),
            Question.HARDER to listOf("harder" to 5, "make it harder" to 6, "more sets" to 4, "heavier today" to 4, "max out" to 5, "push me" to 4,
                "go heavy" to 4, "more weight" to 3, "ignore" to 2, "skip the warm up" to 4, "skip warm up" to 4, "extra sets" to 4),
        )

        fun match(text: String): Match {
            val t = norm(text)
            val minutes = Regex(" (\\d{1,3}) ?(?:minutes|minute|mins|min) ").find(t)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex(" only have (\\d{1,3}) ").find(t)?.groupValues?.get(1)?.toIntOrNull()
            val exercise = exerciseIn(t)
            outOfScope(text)?.let { return Match(null, it, minutes, exercise, emptyList()) }
            val scores = KEYS.mapValues { (_, ks) -> ks.sumOf { (k, w) -> if (t.contains(norm(k))) w else 0 } }.toMutableMap()
            if (minutes != null) scores[Question.ONLY_N_MINUTES] = (scores[Question.ONLY_N_MINUTES] ?: 0) + 4
            if (exercise != null) for (q in listOf(Question.WHY_THIS_EXERCISE, Question.WHAT_IT_TRAINS, Question.CAN_I_REPLACE)) scores[q] = (scores[q] ?: 0) + 1
            val ranked = scores.entries.filter { it.value > 0 }.sortedWith(compareByDescending<Map.Entry<Question, Int>> { it.value }.thenBy { it.key.ordinal })
            val top = ranked.firstOrNull()
            return if (top != null && top.value >= 3) Match(top.key, null, minutes, exercise, emptyList())
            else Match(null, null, minutes, exercise, closest(ranked.map { it.key }))
        }

        /** Phase 2 FS-9: an unknown question gets the 3 closest suggested questions (the most common ones when nothing is close). */
        private fun closest(ranked: List<Question>): List<Question> =
            (ranked.filter { it.suggested } + listOf(Question.WHY_TODAY_DIFFERENT, Question.IMPROVED_THIS_MONTH, Question.ONLY_N_MINUTES)).distinct().take(3)

        /** An exercise named in the question (the longest library name found). */
        private fun exerciseIn(t: String): String? = Library.all.filter { e -> t.contains(" " + norm(e.name).trim() + " ") }.maxByOrNull { it.name.length }?.id
    }

    // -------------------------------------------------------------------------------------------- answering
    /**
     * Answers a matched question. `exerciseId` is the exercise in view (the workout's current lift, or one the user picked); without it the
     * next session's first main lift is used and the other exercises are offered.
     */
    suspend fun answer(m: Match, exerciseId: String? = m.exerciseId): CoachAnswer = d.store.transaction {
        m.outOfScope?.let { return@transaction outOfScope(it) }
        // Not understood (or not English): the closest questions, and the warning signs within reach in case it was about feeling unwell.
        val q = m.question ?: return@transaction CoachAnswer(null, null, emptyList(), emptyList(), listOf(CoachAction.CheckWarningSigns), m.suggestions)
        answer(q, exerciseId, m.minutes)
    }

    suspend fun answer(q: Question, exerciseId: String? = null, minutes: Int? = null): CoachAnswer = d.store.transaction {
        val today = d.clock.today()
        val t = d.planToday()
        val day: PlannedDay? = t?.next ?: t?.week?.days?.firstOrNull { it.weekday > Days.weekday(today) }
        val inView = exerciseId?.takeIf { Library[it] != null } ?: day?.slots?.firstOrNull { it.main }?.exercise?.id ?: day?.slots?.firstOrNull()?.exercise?.id
        val others = day?.slots?.map { it.exercise.id }?.distinct().orEmpty().filter { it != inView }.take(5)
        val recent = d.docs.between(com.personalfitnesscoach.data.core.model.DecisionEntry, today - 13, today).sortedByDescending { it.seq }
        fun decisions(vararg prefixes: String, exercise: String? = null) = recent.filter { e -> prefixes.any { e.reason.startsWith(it) } &&
            (exercise == null || e.inputs.toString().contains("\"$exercise\"") || e.outputs.toString().contains("\"$exercise\"")) }.distinctBy { it.reason }.take(5)
        when (q) {
            Question.WHY_THIS_EXERCISE -> {
                val ex = inView?.let { Library[it] } ?: return@transaction notYet(q)
                val slot = day?.slots?.firstOrNull { it.exercise.id == ex.id }
                val prefs = d.docs.get(com.personalfitnesscoach.data.core.model.PreferencesRecord)
                val core = t?.program?.coreLifts?.values?.contains(ex.id) == true
                CoachAnswer(q, null, listOf(Fact.ExerciseRole(ex.id, slot?.spec?.role, slot?.main == true, ex.pattern, core, prefs?.favourites?.contains(ex.id) == true),
                    Fact.Decisions(decisions("SLOT_", "CORE_LIFT", "ACCESSORY_", "SWAP", exercise = ex.id))),
                    listOf("ADH-003", "PAT-001", "SUB-002").filter { Registry.titles.containsKey(it) },
                    others.map { CoachAction.AskAbout(q, it) })
            }
            Question.WHAT_IT_TRAINS -> {
                val ex = inView?.let { Library[it] } ?: return@transaction notYet(q)
                CoachAnswer(q, null, listOf(Fact.Trains(ex.id, ex.pattern, ex.primary.toList(), ex.secondary.toList())), listOf("PAT-001", "VOL-001")
                    .filter { Registry.titles.containsKey(it) }, others.map { CoachAction.AskAbout(q, it) })
            }
            Question.CAN_I_REPLACE -> {
                val ex = inView?.let { Library[it] } ?: return@transaction notYet(q)
                val options = if (t != null && day != null) runCatching {
                    val req = d.bridge.request(t.user, t.program, t.week, day, Tier.FULL)
                    val pool = Library.all.filter { !it.userAddOnly && it.id != ex.id && (if (ex.powerOnly) it.powerCapable else !it.powerOnly) && it.unit == ex.unit }
                    Substitution.options(ex, pool, SessionGenerator.subContext(req)).value.ranked.map { it.exercise.id }
                }.getOrDefault(emptyList()) else emptyList()
                CoachAnswer(q, null, listOf(Fact.Replacements(ex.id, options.take(3))), listOf(RuleIds.SUB_001, RuleIds.SUB_002, "EQ-002"),
                    others.map { CoachAction.AskAbout(q, it) })
            }
            Question.WHY_TODAY_DIFFERENT -> {
                val ci = t?.let { d.docs.get(ReadinessRecord, dayKey(today)) }
                val entries = d.decisions.forDay(today).sortedByDescending { it.seq }.filter { it.reason !in ROUTINE }.distinctBy { it.reason }.take(6)
                CoachAnswer(q, null, listOf(Fact.TodayLevel(ci?.tier, ci != null), Fact.Decisions(entries)),
                    listOf("RDY-001", "RDY-005", "DEL-002", "REG-002").filter { Registry.titles.containsKey(it) },
                    if (ci == null && t?.next != null) listOf(CoachAction.OpenCheckIn) else emptyList())
            }
            Question.WHY_LESS_VOLUME -> {
                val entries = decisions("TIER_", "DELOAD", "LIGHTER", "TIME_", "RETURN", "RECALIBRATE", "ILLNESS", "VOLUME", "VALIDATOR_", "CONDITION_", "FATIGUE",
                    "WORKLOAD")
                CoachAnswer(q, null, if (entries.isEmpty()) listOf(Fact.NotYet) else listOf(Fact.Decisions(entries)),
                    listOf("VOL-001", "DEL-001", "RDY-004", "TIME-002", "SAF-008").filter { Registry.titles.containsKey(it) }, emptyList())
            }
            Question.ONLY_N_MINUTES -> {
                val usual = t?.user?.profile?.sessionMinutes ?: 60
                // The suggested question says 40 minutes; a typed one gives its own number (review R6-07).
                val n = (minutes ?: SUGGESTED_MINUTES).coerceIn(10, 180)
                // SAF-009 "shorter session": only offered when it is shorter than usual; longer is not something the coach asks for.
                CoachAnswer(q, null, listOf(Fact.Minutes(n, usual)), listOf("TIME-001", "TIME-002", "TIME-004"),
                    if (t?.next != null && n < usual) listOf(CoachAction.ShorterSession(n)) else emptyList())
            }
            Question.TIRED_TODAY -> {
                val ci = d.docs.get(ReadinessRecord, dayKey(today))
                CoachAnswer(q, null, listOf(Fact.TodayLevel(ci?.tier, ci != null)), listOf("RDY-001", "RDY-005", "RDY-007"),
                    when {
                        t?.next == null -> emptyList()
                        ci == null -> listOf(CoachAction.OpenCheckIn)
                        else -> listOf(CoachAction.EasierLevel)
                    })
            }
            Question.IMPROVED_THIS_MONTH -> {
                val o = insights.overview(today)
                val monthStart = Days.of(Days.date(today).withDayOfMonth(1))
                val lastStart = Days.of(Days.date(monthStart).minusMonths(1))
                val done = d.sessions.done(lastStart, today)
                val up = o.lifts.mapNotNull { it.trend }.filter { it.direction == TrendDirection.UP }.sortedByDescending { it.changePct }
                val fact = Fact.Improved(up.take(3), o.lifts.mapNotNull { it.trend }.count { it.direction == TrendDirection.FLAT },
                    done.count { it.day >= monthStart }, done.count { it.day < monthStart }, o.whoWeeks, o.streak.current)
                CoachAnswer(q, null, if (o.sessionsCompleted == 0) listOf(Fact.NotYet) else listOf(fact), listOf("FL-005", "INT-004", "PH-001", "ADH-001"),
                    listOf(CoachAction.OpenProgress))
            }
            Question.FOCUS_NEXT -> {
                val p = t?.program
                val ctx = p?.let { d.bridge.context(it) }
                val prog = p?.let { d.bridge.blueprint(it) }
                val block = ctx?.block
                // Loading weeks left in this block, this week included (the easier week after it is not counted).
                val weeksLeft = ctx?.let { c -> (c.block.endWeek - c.programWeek + 1).coerceAtLeast(0) }
                val last = d.docs.all(com.personalfitnesscoach.data.core.model.WeekSummary).maxByOrNull { it.weekStartDay }
                CoachAnswer(q, null, listOf(Fact.Focus(block?.type?.kind, weeksLeft, t?.next ?: day, last?.equivalentMinutes,
                    d.docs.get(com.personalfitnesscoach.data.core.model.StepStateRecord)?.target)), listOf("PER-001", "PER-005", "PH-001", "STEP-001")
                    .filter { Registry.titles.containsKey(it) }, emptyList())
            }
            Question.WHY_TARGET_CHANGED -> {
                val entries = decisions("LOAD_", "PROG", "INSESSION_", "CALIBRATION", "E1RM", "FAILED_TARGETS", "DELOAD_RESUME", "RETURN", "HISTORY_EDITED",
                    exercise = exerciseId)
                CoachAnswer(q, null, if (entries.isEmpty()) listOf(Fact.NotYet) else listOf(Fact.Decisions(entries)),
                    listOf("PROG-001", "PROG-007", "INT-004", "INT-007", "CAL-001").filter { Registry.titles.containsKey(it) }, emptyList())
            }
            Question.WHY_LIGHTER_WEEK -> {
                val p = t?.program
                val entries = decisions("DELOAD", "LIGHTER", "PIVOT", "FATIGUE")
                CoachAnswer(q, null, listOf(Fact.LighterWeek(p?.deloadThisWeek == true, p?.lighterSessionsLeft ?: 0), Fact.Decisions(entries)),
                    listOf("DEL-001", "DEL-002", "DEL-004").filter { Registry.titles.containsKey(it) }, emptyList())
            }
            Question.HOW_HARD -> {
                val slot = day?.slots?.firstOrNull { it.exercise.id == inView } ?: day?.slots?.firstOrNull { it.main }
                val weeks = t?.program?.weeksTraining ?: 0
                val prompt = com.personalfitnesscoach.engine.dose.Effort.promptStyle(t?.user?.profile?.level ?: com.personalfitnesscoach.engine.model.Level.BEGINNER, weeks)
                CoachAnswer(q, null, listOf(Fact.Effort(slot?.targetRir, prompt == com.personalfitnesscoach.engine.dose.Effort.PromptStyle.RIR_QUESTION)),
                    listOf("INT-001", "INT-006", "INT-002").filter { Registry.titles.containsKey(it) }, emptyList())
            }
            Question.HARDER -> {
                // SAF-009 adversarial case: the coach can't raise the level; the engine offers one step harder only when nothing locks it (RDY-007).
                val ci = d.docs.get(ReadinessRecord, dayKey(today))
                CoachAnswer(q, null, listOf(Fact.TodayLevel(ci?.tier, ci != null)), listOf("SAF-009", "RDY-007", "SAF-003", "SAF-008"),
                    if (t?.next != null) listOf(if (ci == null) CoachAction.OpenCheckIn else CoachAction.OpenPreview) else emptyList())
            }
        }
    }

    private fun notYet(q: Question) = CoachAnswer(q, null, listOf(Fact.NotYet), emptyList(), emptyList())

    private fun outOfScope(o: OutOfScope): CoachAnswer = when (o) {
        OutOfScope.URGENT_SYMPTOMS -> CoachAnswer(null, o, emptyList(), listOf(RuleIds.SAF_002, RuleIds.SAF_009), listOf(CoachAction.OpenWarningSigns))
        OutOfScope.PAIN -> CoachAnswer(null, o, emptyList(), listOf(RuleIds.SAF_003, RuleIds.SAF_004, RuleIds.SAF_009), listOf(CoachAction.OpenCheckIn))
        // SAF-007: the check-in asks about illness and rests or lightens the day; the coach says nothing about the illness itself.
        OutOfScope.ILLNESS -> CoachAnswer(null, o, emptyList(), listOf(RuleIds.SAF_007, RuleIds.SAF_009), listOf(CoachAction.OpenCheckIn))
        else -> CoachAnswer(null, o, emptyList(), listOf(RuleIds.SAF_009), emptyList())
    }

    /** "Why?" for one decision (stored or just made): its rules with title and confidence (Phase 2 section 9). */
    fun why(reason: String, ruleIds: List<String>): Why = Why(reason, ruleIds.distinct().map { RuleRef(it, Registry.titles[it], Registry.confidence[it]) })

    fun why(entry: DecisionEntry): Why = why(entry.reason, entry.ruleIds)

    suspend fun decision(seq: Long): DecisionEntry? = d.store.transaction {
        d.docs.get(com.personalfitnesscoach.data.core.model.DecisionEntry, seq.toString().padStart(19, '0'))
    }
}

/** Routine decisions every session has; "why is today different" leaves them out (the same list the screens use). */
private val ROUTINE = setOf(
    "READINESS_SCORED", "LOAD_FROM_E1RM", "LOAD_ROUNDED", "LOAD_FROM_PROGRESSION", "LOAD_DISCRETE_STEP", "VALIDATOR_PASSED", "REPS_FROM_GOAL",
    "RIR_TARGET", "MODALITY_CHOSEN", "HIIT_PROTOCOL_CHOSEN", "MOBILITY_PLANNED", "PROGRAM_PLANNED", "WEEK_PLANNED", "SESSION_GENERATED",
    "SLOT_FILLED", "CORE_LIFT_KEPT", "ACCESSORY_ROTATED", "VOLUME_ALLOCATED", "CONDITIONING_PLANNED", "NEXT_IN_SEQUENCE", "WARMUP_BUILT",
    "CONDITIONS_MERGED", "CONDITION_LIMIT_APPLIED", "CONDITION_STATUS_APPLIED", "LEVEL_CLASSIFIED", "ACTIVITY_TARGET_SET", "FAT_LOSS_MIX",
    "WALKS_PLANNED", "BALANCE_PLANNED", "SCREEN_STANDARD", "STEP_BASELINE_PENDING", "STEP_TARGET_SET", "STEP_TARGET_UP", "STEP_TARGET_HELD",
    "STEP_TARGET_DOWN", "BRISK_WALKS_COUNTED", "BW_HOLD", "SRPE_PROMPT", "WEIGHT_TREND", "WAIST_AVERAGED", "AEROBIC_DURATION_UP",
    "GOAL_DEFAULT_CHOSEN", "BONE_LOADING_VARIANT", "SWAP_OR_LATER", "ADDITION_OK", "STRENGTH_TREND",
)
