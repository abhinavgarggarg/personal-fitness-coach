package com.personalfitnesscoach.engine.program

import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.Num
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Level
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds

/** FL-003 / FL-002 / STEP-001 age bands. Users under 30 who pick the fat-loss goal use the 30–39 band. */
enum class AgeBand {
    AGE_30_39, AGE_40_49, AGE_50_59, AGE_60_64, AGE_65_PLUS;

    companion object {
        fun of(age: Int?): AgeBand = when {
            age == null || age < 40 -> AGE_30_39
            age < 50 -> AGE_40_49
            age < 60 -> AGE_50_59
            age < 65 -> AGE_60_64
            else -> AGE_65_PLUS
        }
    }
}

/** What a user can pick at onboarding and what is chosen for them (FL-001). */
data class GoalContext(
    val age: Int?,
    val pregnant: Boolean = false,
    val cancerActiveTreatment: Boolean = false,
    /** Weeks since giving birth while the postpartum entry applies (SAF-010), else null. */
    val postpartumWeeks: Int? = null,
    /** The user turned weight features off: no weight or waist tracking, expectations or rate check-in. */
    val weightFeaturesOff: Boolean = false,
)

data class GoalOptions(val offered: List<Goal>, val default: Goal, val weightFeatures: Boolean)

/** FL-001's realistic expectation, shown only with the fat-loss goal and weight features on. */
data class Expectation(val weightKg: IntRange, val waistCm: IntRange, val aerobicMinutes: IntRange)

/** The weekly mix for the fat-loss goal at this age (FL-003), after the HIIT base and offer rules. */
data class FatLossMix(
    val band: AgeBand,
    val strengthDays: Int,
    /** Steady Z1 minutes a week, walking included (STEP-002). */
    val z1Minutes: IntRange,
    /** Tempo (Z2) minutes planned this week (0 = none). */
    val z2Minutes: Int,
    val z2Max: Int,
    /** HIIT sessions planned this week before the HIIT-001/AGE-001 caps. */
    val hiit: Int,
    val hiitMax: Int,
    val hiitBaseWeeks: Int,
    /** 60+: intervals are offered once the base is there, not planned; true when the offer is open but not taken. */
    val hiitOffered: Boolean,
    val lowImpactOnly: Boolean,
    /** Conditioning modalities allowed for intervals when [lowImpactOnly] (FL-003 low-impact list). */
    val intervalModalities: Set<Modality>?,
    val sprints: Boolean,
    /** Jumping power work and impact cardio (≤ 1 a week, CON-004) — under 50, without obesity or lower-limb pain. */
    val impactAllowed: Boolean,
    val powerSlot: Boolean,
    val balanceMinutesWeek: Int,
    /** Days a week with strength plus balance at 65+ (AGE-001 balance days). */
    val balanceDays: Int,
)

/** This week's activity target (FL-002) in PH-001 equivalent minutes. */
data class ActivityTarget(val range: IntRange, val thisWeek: Double, val growthPct: Int)

/** Brisk walks to close the week's gap (STEP-002 bouts of ≥ 10 minutes). */
data class WalkPlan(val minutesPerWeek: Double, val walks: Int, val minutesEach: Int)

/** The week's aerobic accounting for the fat-loss goal (FL-002, FL-003, PH-001, STEP-002). */
data class ActivityPlan(
    val target: ActivityTarget,
    val gymZ1Minutes: Double,
    val z2Minutes: Double,
    val hiitWorkMinutes: Double,
    val walk: WalkPlan,
    /** Equivalent minutes planned: Z1 + walking + 2 × (Z2 + Z3/Z4 work). */
    val plannedEquivalent: Double,
    val hiitOffered: Boolean,
    /** Balance sessions to do at home because there are fewer training days than balance days (65+). */
    val homeBalanceSessions: Int,
)

/** FL-001 to FL-004 (the goal, the weekly target, the age-banded mix; the year is in [Blueprint]). */
object FatLoss {
    private val LOWER_LIMB = setOf(Joint.HIP, Joint.KNEE, Joint.ANKLE)

    // ------------------------------------------------------------------ FL-001

    /** FL-001: which goals are offered, the default goal and whether weight features are on. */
    fun options(c: GoalContext): EngineResult<GoalOptions> {
        val blocked = c.pregnant || c.cancerActiveTreatment
        val earlyPostpartum = c.postpartumWeeks != null && c.postpartumWeeks < P.FL_001.not_default_with_postpartum_until_week
        val offered = Goal.entries.filter { it != Goal.FAT_LOSS || (!blocked && !earlyPostpartum) }
        val weight = !c.weightFeaturesOff && !blocked
        val default = if (c.age != null && c.age >= P.FL_001.default_for_age_gte && Goal.FAT_LOSS in offered && weight && c.postpartumWeeks == null)
            Goal.FAT_LOSS else Goal.GENERAL_FITNESS
        val o = GoalOptions(offered, default, weight)
        return EngineResult(o, listOf(Decision(DecisionKind.VOLUME_CHANGE, listOf(RuleIds.FL_001), ReasonKey.GOAL_DEFAULT_CHOSEN,
            inputs = mapOf("age" to c.age, "pregnant" to c.pregnant, "cancerActiveTreatment" to c.cancerActiveTreatment,
                "postpartumWeeks" to c.postpartumWeeks, "weightFeaturesOff" to c.weightFeaturesOff),
            outputs = mapOf("default" to default.name, "fatLossOffered" to (Goal.FAT_LOSS in offered), "weightFeatures" to weight))))
    }

    /** FL-001: the expectation shown with the fat-loss goal (never a promise of faster loss); null when weight features are off. */
    fun expectation(weightFeatures: Boolean): Expectation? = if (!weightFeatures) null else Expectation(
        P.FL_001.expected_weight_change_kg[0]..P.FL_001.expected_weight_change_kg[1],
        P.FL_001.expected_waist_change_cm[0]..P.FL_001.expected_waist_change_cm[1],
        P.FL_001.aerobic_minutes_range[0]..P.FL_001.aerobic_minutes_range[1])

    // ------------------------------------------------------------------ FL-002

    fun targetRange(age: Int?): IntRange {
        val r = when (AgeBand.of(age)) {
            AgeBand.AGE_60_64 -> P.FL_002.equivalent_minutes.age_60_64
            AgeBand.AGE_65_PLUS -> P.FL_002.equivalent_minutes.age_65_plus
            else -> P.FL_002.equivalent_minutes.age_30_59
        }
        return maxOf(r[0], P.FL_002.floor)..r[1]
    }

    /**
     * FL-002: this week's equivalent-minute target. It grows from last week's minutes by at most 15% (12% at 65+, AER-003 and
     * AGE-001) toward the band's lower bound and holds there; the band range is shown. With no history yet the first week plans
     * the 150-minute floor, mostly walking (D-068).
     */
    fun weeklyTarget(age: Int?, lastWeekEquivalent: Double): EngineResult<ActivityTarget> {
        val range = targetRange(age)
        val pct = if (AgeBand.of(age) == AgeBand.AGE_65_PLUS) P.FL_002.growth_max_pct_week_65_plus else P.FL_002.growth_max_pct_week
        val week = if (lastWeekEquivalent <= 0.0) P.FL_002.floor.toDouble()
            else minOf(range.first.toDouble(), lastWeekEquivalent * (1 + pct / 100.0))
        val t = ActivityTarget(range, Num.round1(minOf(week, range.last.toDouble())), pct)
        return EngineResult(t, listOf(Decision(DecisionKind.VOLUME_CHANGE, listOf(RuleIds.FL_002, RuleIds.AER_003), ReasonKey.ACTIVITY_TARGET_SET,
            inputs = mapOf("age" to age, "lastWeek" to lastWeekEquivalent), outputs = mapOf("range" to range.toString(), "thisWeek" to t.thisWeek))))
    }

    // ------------------------------------------------------------------ FL-003

    /** FL-003: strength days for the fat-loss goal — 3 by default from 3 training days, never below 2. */
    fun strengthDays(trainingDays: Int): Int =
        if (trainingDays >= P.FL_003.strength_days.default_needs_training_days_gte) minOf(trainingDays, P.FL_003.strength_days.default)
        else maxOf(P.FL_003.strength_days.min, minOf(trainingDays, P.FL_003.strength_days.min))

    private data class Band(val z1: List<Int>, val z2: List<Int>, val hiitDefault: Int, val hiitMax: Int, val baseWeeks: Int,
                            val offerAfter: Int?, val lowImpactOnly: Boolean, val sprints: Boolean, val power: Boolean, val balance: Int, val multi: Int)

    private fun band(b: AgeBand): Band = when (b) {
        AgeBand.AGE_30_39 -> with(P.FL_003.bands.age_30_39) { Band(z1_minutes, z2_minutes, hiit_default, hiit_max, hiit_base_weeks, null, low_impact_only, true, power_slot, balance_minutes_week, 0) }
        AgeBand.AGE_40_49 -> with(P.FL_003.bands.age_40_49) { Band(z1_minutes, z2_minutes, hiit_default, hiit_max, hiit_base_weeks, null, low_impact_only, true, power_slot, balance_minutes_week, 0) }
        AgeBand.AGE_50_59 -> with(P.FL_003.bands.age_50_59) { Band(z1_minutes, z2_minutes, hiit_default, hiit_max, hiit_base_weeks, null, low_impact_only, sprint_intervals, power_slot, balance_minutes_week, 0) }
        AgeBand.AGE_60_64 -> with(P.FL_003.bands.age_60_64) { Band(z1_minutes, z2_minutes, hiit_default, hiit_max, hiit_offer_after_base_weeks, hiit_offer_after_base_weeks, low_impact_only, sprint_intervals, power_slot, balance_minutes_week, 0) }
        AgeBand.AGE_65_PLUS -> with(P.FL_003.bands.age_65_plus) { Band(z1_minutes, z2_minutes, hiit_default, hiit_max, hiit_offer_after_base_weeks, hiit_offer_after_base_weeks, low_impact_only, sprint_intervals, power_slot, balance_minutes_week, multicomponent_days) }
    }

    /** FL-003 low-impact interval modalities, as [Modality] values. */
    val LOW_IMPACT_MODALITIES: Set<Modality> = P.FL_003.low_impact_modalities.mapNotNull { Modality.byKey(it) }.toSet()

    /**
     * FL-003 mix for this week.
     * @param trainingDays days the week will train (after FREQ-001 and the available days)
     * @param regularWeeks weeks of regular training so far — the interval base (HIIT-003 is checked separately)
     * @param conditioningBlock this week follows a conditioning block (FL-004): one more interval session up to the band's
     *   maximum, only under HIIT-001's conditions (intermediate or advanced; the HIIT-001/AGE-001 cap is applied by the planner)
     * @param hiitOptIn 60+: the user accepted the interval offer
     * @param obesity the user reported living with obesity (SAF-010): impact off and low-impact intervals only
     */
    fun mix(age: Int?, level: Level, trainingDays: Int, regularWeeks: Int, conditioningBlock: Boolean, hiitOptIn: Boolean = false,
            obesity: Boolean = false, lowerLimbPain: Set<Joint> = emptySet()): EngineResult<FatLossMix> {
        val ab = AgeBand.of(age)
        val b = band(ab)
        val baseOk = regularWeeks >= b.baseWeeks
        // 60+: offered once the base is there (65+ also needs to have been active before: not a beginner), planned only when accepted.
        val offerOpen = b.offerAfter != null && baseOk && (ab != AgeBand.AGE_65_PLUS || level != Level.BEGINNER)
        val wanted = when {
            b.offerAfter != null -> if (offerOpen && hiitOptIn) 1 else 0
            !baseOk -> 0
            else -> b.hiitDefault
        }
        val extra = if (wanted > 0 && conditioningBlock && level != Level.BEGINNER) 1 else 0
        val hiit = minOf(b.hiitMax, wanted + extra)
        val lowImpactOnly = b.lowImpactOnly || obesity || (age != null && age >= 60)
        val impact = !lowImpactOnly && lowerLimbPain.none { it in LOWER_LIMB }
        val z2 = b.z2[0]
        val m = FatLossMix(ab, strengthDays(trainingDays), b.z1[0]..b.z1[1], z2, b.z2[1], hiit, b.hiitMax, b.baseWeeks,
            offerOpen && !hiitOptIn, lowImpactOnly, if (lowImpactOnly) LOW_IMPACT_MODALITIES else null, b.sprints && !lowImpactOnly,
            impact, b.power, b.balance, if (b.multi > 0) b.multi else if (b.balance > 0) Math.ceil(b.balance / 10.0).toInt() else 0)
        val rules = listOf(RuleIds.FL_003) + if (ab == AgeBand.AGE_65_PLUS) listOf(RuleIds.AGE_001) else emptyList()
        return EngineResult(m, listOf(Decision(DecisionKind.VOLUME_CHANGE, rules, ReasonKey.FAT_LOSS_MIX,
            inputs = mapOf("age" to age, "level" to level.name, "days" to trainingDays, "regularWeeks" to regularWeeks, "conditioningBlock" to conditioningBlock,
                "hiitOptIn" to hiitOptIn, "obesity" to obesity),
            outputs = mapOf("band" to ab.name, "strengthDays" to m.strengthDays, "hiit" to hiit, "z2" to z2, "lowImpactOnly" to lowImpactOnly,
                "impact" to impact, "balanceMinutes" to m.balanceMinutesWeek, "hiitOffered" to m.hiitOffered))))
    }

    /** STEP-002 walks for `minutes` a week: daily-ish bouts of 10–45 minutes, rounded up to 5 minutes. */
    fun walkPlan(minutes: Double): WalkPlan {
        if (minutes <= 0.0) return WalkPlan(0.0, 0, 0)
        val minBout = P.STEP_002.bout_min_minutes
        val walks = Math.ceil(minutes / 20.0).toInt().coerceIn(1, 7)
        val each = (Math.ceil(minutes / walks / 5.0).toInt() * 5).coerceIn(minBout, 45)
        return WalkPlan(Num.round1(minutes), walks, each)
    }
}
