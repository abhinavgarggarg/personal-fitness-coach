package com.personalfitnesscoach.engine.safety

import com.personalfitnesscoach.engine.core.Decision
import com.personalfitnesscoach.engine.core.DecisionKind
import com.personalfitnesscoach.engine.core.EngineResult
import com.personalfitnesscoach.engine.core.ReasonKey
import com.personalfitnesscoach.engine.model.Joint
import com.personalfitnesscoach.engine.model.Modality
import com.personalfitnesscoach.engine.model.Zone
import com.personalfitnesscoach.engine.registry.P
import com.personalfitnesscoach.engine.registry.RuleIds

/** SAF-010 control status answer; "not sure" counts as "no". */
enum class ControlStatus { YES, NO, NOT_SURE }

/** SAF-010 doctor's-OK scopes, each unlocking only itself (vigorous = Z2, intervals = Z3 and interval sessions). */
enum class ClearanceScope(val key: String, val zone: Zone) {
    LIGHT_MODERATE("light_moderate", Zone.Z1), VIGOROUS("vigorous", Zone.Z2), INTERVALS("intervals", Zone.Z3);
}

/**
 * One condition the user picked, with everything they told the app about it. Nothing here is a clinical value
 * (SAF-010 never reads or thresholds blood pressure, glucose, ketones, oxygen or ECG values).
 */
data class UserCondition(
    val id: String,
    /** Weeks of training since the condition was added (time-based unlocks). */
    val weeks: Int = 0,
    val controlled: ControlStatus? = null,
    /** The user's statement of what a doctor OK'd (per condition). */
    val clearance: Set<ClearanceScope> = emptySet(),
    /** Sub-flags: heart (heart_failure, pacemaker_icd, recent_breastbone_surgery, aortic_or_connective_tissue_or_cardiomyopathy) and
     *  cancer (in_active_treatment, cancer_has_spread_to_the_bones, lymphoedema). */
    val subFlags: Set<String> = emptySet(),
    val pregnancyWeek: Int? = null,
    val weeksSinceBirth: Int? = null,
    /** The user confirmed the entry's attestation (postpartum "healed; no pelvic floor symptoms"; pregnancy "my provider has OK'd exercise"). */
    val attested: Boolean = false,
    /** Postpartum: the user recorded passing the return-to-impact checks. */
    val impactChecksPassed: Boolean = false,
    /** The user opted in to an impact unlock (osteoarthritis, severe obesity). */
    val impactOptIn: Boolean = false,
    /** Weeks in a row the osteoarthritis pain rule was met. */
    val painRuleMetWeeks: Int = 0,
    /** A `block_if` statement applies (the user should follow their care provider instead of a training plan). */
    val blockIfYes: Boolean = false,
    /** Pregnancy: did vigorous exercise before pregnancy (and the provider OK'd it). */
    val previouslyVigorous: Boolean = false,
    /** Pregnancy: already did impact work before (impact "only if already doing it"). */
    val alreadyDoingImpact: Boolean = false,
    /** Pregnancy: dizzy or unwell lying on the back. */
    val supineUncomfortable: Boolean = false,
    /** Low back pain: "my back is flaring". */
    val flare: Boolean = false,
)

/** SAF-010: resolve each picked entry for today, then merge with "most restrictive wins". */
object Conditions {
    val entries: Map<String, ConditionEntry> by lazy { GeneratedConditions.entries.associateBy { it.id } }

    operator fun get(id: String): ConditionEntry? = entries[id]

    /** The status question decides which high-blood-pressure entry applies ("not sure" counts as "no"). */
    fun hbpEntryId(status: ControlStatus): String = if (status == ControlStatus.YES) "hbp_controlled" else "hbp_not_controlled"

    private val HIIT_ORDER: List<String> = listOf("no", "after_base", "as_tolerated", "yes")
    private val ZONE_MAX = Zone.Z4

    /** One entry resolved for today (an intermediate value; merged into [ConditionLimits]). */
    private data class Resolved(
        val id: String, val maxZone: Zone, val hiit: HiitPermission, val hiitBaseWeeks: Int, val hiitLowImpactOnly: Boolean,
        val hiitModalities: Set<Modality>?, val impact: ImpactLevel, val minRir: Double?, val minRirByTag: Map<String, Double>,
        val failureAllowed: Boolean, val avoidTags: Set<String>, val rangeLimitedTags: Set<String>, val jointLimits: Map<Joint, Int>,
        val warmup: Int, val cooldown: Int, val maxInactive: Int?, val strengthConsecutive: Boolean, val conservative: Boolean,
        val effortByFeel: Boolean, val strengthFirst: Boolean, val balance: Int, val backExt: Int, val fatLossOffered: Boolean,
        val weightFeatures: Boolean, val prompts: List<String>, val stopSigns: List<String>, val clearancePrompt: Boolean, val blocked: Boolean,
    )

    private fun hiitOf(key: String): HiitPermission = HiitPermission.of(key)

    private fun minZone(a: Zone, b: Zone?): Zone = if (b == null || a <= b) a else b

    private fun resolveEntry(e: ConditionEntry, u: UserCondition): Resolved {
        // Phase (postpartum): the current phase; a phase with an attestation needs it, otherwise the previous phase stays.
        var maxZone: Zone = e.maxZone ?: ZONE_MAX
        var hiitKey = e.hiit
        var minRir = e.minRir
        var impactKey = e.impact
        var impactUnlocked = false
        if (e.phases.isNotEmpty()) {
            val w = u.weeksSinceBirth ?: 0
            var idx = e.phases.indexOfFirst { w >= it.fromWeek && w < it.toWeek }.let { if (it < 0) e.phases.lastIndex else it }
            while (idx > 0 && e.phases[idx].attest != null && !u.attested) idx--
            val ph = e.phases[idx]
            maxZone = ph.maxZone
            minRir = ph.minRir
            impactUnlocked = ph.impact == "progressive" && u.impactChecksPassed
            impactKey = if (ph.impact == "progressive") (if (impactUnlocked) "low_volume" else "none") else ph.impact
            hiitKey = if (ph.hiit == "after_impact_unlocked") (if (impactUnlocked) "after_base" else "no") else ph.hiit
        }
        // Control status (asthma): "no" or "not sure" applies the not-controlled limits.
        val notControlled = e.askControlStatus && u.controlled != ControlStatus.YES
        val prompts = ArrayList(e.prompts)
        if (notControlled) {
            maxZone = minZone(maxZone, e.ifNotControlledMaxZone)
            e.ifNotControlledHiit?.let { hiitKey = it }
            e.ifNotControlledPrompt?.let { prompts += it }
        }
        // Time-based zone unlock (severe obesity: Z1 for the first 8 weeks).
        if (e.maxZoneAfterWeeks != null && e.maxZoneAfterWeeksZone != null && u.weeks >= e.maxZoneAfterWeeks) maxZone = e.maxZoneAfterWeeksZone
        // Impact: unlocks, "none at start", "only if already doing it", "none if" sub-flags.
        if (e.impactUnlockAfterWeeks != null && u.impactOptIn && u.weeks >= e.impactUnlockAfterWeeks &&
            (!e.impactUnlockNeedsPainRule || u.painRuleMetWeeks >= e.impactUnlockAfterWeeks)) impactUnlocked = true
        var impact = when (impactKey) {
            "none_at_start" -> if (impactUnlocked) ImpactLevel.LOW else ImpactLevel.NONE
            "only_if_already_doing_it" -> if (u.alreadyDoingImpact) ImpactLevel.ONLY_IF_ALREADY else ImpactLevel.LOW
            else -> ImpactLevel.of(impactKey)
        }
        // Osteoarthritis: impact unlock lifts "low" to low-volume impact (CON-004 still allows ≤ 1 a week).
        if (impactUnlocked && e.impactKeyIsLow()) impact = ImpactLevel.LOW_VOLUME
        if (e.impactNoneIf.any { it in u.subFlags }) impact = ImpactLevel.NONE
        // Tags.
        val avoid = LinkedHashSet(e.avoidTags)
        if (!impactUnlocked) { avoid += e.avoidTagsAtStart; avoid += e.avoidTagsEarly }
        val pw = u.pregnancyWeek
        if (pw != null) for ((week, tags) in e.avoidTagsFromWeek) if (pw >= week) avoid += tags
        if (e.avoidSupineAnyTimeIf != null && u.supineUncomfortable) avoid += "supine_lying"
        // Joint limits and their unlocks.
        val joints = e.jointLimits.toMutableMap()
        val unlockWeeks = e.jointLimitUnlockAfterWeeks
        if (unlockWeeks != null && (if (e.jointLimitUnlockNeedsPainRule) u.painRuleMetWeeks >= unlockWeeks else u.weeks >= unlockWeeks)) joints.putAll(e.jointLimitUnlock)
        // Low back pain flare mode.
        if (u.flare) {
            for ((j, v) in e.flareJointLimits) joints[j] = minOf(joints[j] ?: 4, v)
            avoid += e.flareAvoidTags
        }
        // Heart sub-flags: recent breastbone surgery = no loaded overhead work and no heavy pushing or pulling.
        if ("recent_breastbone_surgery" in u.subFlags && "recent_breastbone_surgery" in e.subFlags) avoid += setOf("overhead_heavy", "breath_hold_max", "isometric_heavy")
        for (f in u.subFlags) e.subFlags[f]?.let { prompts += it }
        e.environment.forEach { prompts += it }
        e.positionsNote?.let { prompts += it }

        // Clearance (doctor's OK of a scope).
        val clearance = if (e.clearanceAlwaysIf.any { it in u.subFlags }) "always" else e.clearance
        val scopeZone = u.clearance.maxOfOrNull { it.zone }
        var hiit = hiitOf(hiitKey)
        var conservative = false
        var failure = e.failureAllowed
        when (clearance) {
            "before_vigorous" -> if (ClearanceScope.VIGOROUS !in u.clearance && ClearanceScope.INTERVALS !in u.clearance) {
                maxZone = minZone(maxZone, Zone.Z1); hiit = HiitPermission.NO; minRir = maxOf(minRir ?: 0, P.SAF_001.conservative_mode.min_rir)
            } else {
                maxZone = minZone(maxZone, scopeZone)
                if (e.hiitNeedsScope == "intervals" && ClearanceScope.INTERVALS !in u.clearance) hiit = HiitPermission.NO
            }
            "always" -> if (u.clearance.isEmpty()) {
                // SAF-001 conservative mode until the user confirms a doctor's OK.
                conservative = true
                maxZone = minZone(maxZone, Zone.Z1); hiit = HiitPermission.NO; minRir = maxOf(minRir ?: 0, P.SAF_001.conservative_mode.min_rir)
                failure = false; if (impact > ImpactLevel.LOW) impact = ImpactLevel.LOW
            } else {
                // After it, the entry's limits apply up to the confirmed scope.
                var entryZone = maxZone
                e.maxZoneAfterClearance?.let { entryZone = if (it > entryZone) it else entryZone }
                if (u.previouslyVigorous) e.maxZoneIfPreviouslyVigorousAndOk?.let { entryZone = if (it > entryZone) it else entryZone }
                val top = u.clearance.maxByOrNull { it.zone }!!
                e.afterClearance.firstOrNull { it.scope == top.key }?.let { ac ->
                    ac.maxZone?.let { entryZone = it }
                    ac.minRir?.let { minRir = it }
                    ac.hiit?.let { hiit = hiitOf(it) }
                }
                maxZone = minZone(entryZone, scopeZone)
                if (ClearanceScope.INTERVALS !in u.clearance) hiit = HiitPermission.NO
            }
        }
        if (maxZone < Zone.Z3) hiit = HiitPermission.NO
        val cancerTreatment = e.id == "cancer" && "in_active_treatment" in u.subFlags
        val pregnant = e.id == "pregnancy"
        val needsOk = clearance != "none" && u.clearance.isEmpty()
        return Resolved(
            id = e.id, maxZone = maxZone, hiit = hiit, hiitBaseWeeks = e.hiitBaseWeeks, hiitLowImpactOnly = e.hiitLowImpactOnly,
            hiitModalities = e.hiitModalities?.mapNotNull { Modality.byKey(it) }?.toSet(), impact = impact,
            minRir = if (e.minRirTags == null) minRir?.toDouble() else null,
            minRirByTag = if (e.minRirTags != null && minRir != null) e.minRirTags.associateWith { minRir!!.toDouble() } else emptyMap(),
            failureAllowed = failure, avoidTags = avoid, rangeLimitedTags = e.rangeLimitedTags, jointLimits = joints,
            warmup = e.extraWarmupMin, cooldown = e.extraCooldownMin, maxInactive = e.maxConsecutiveInactiveDays,
            strengthConsecutive = e.strengthOnConsecutiveDays ?: true, conservative = conservative, effortByFeel = e.effortBy != null,
            strengthFirst = e.defaultOrder == "strength_then_cardio", balance = e.balancePerWeek, backExt = e.backExtensorPerWeek,
            fatLossOffered = !pregnant && !cancerTreatment && !(e.id == "postpartum" && (u.weeksSinceBirth ?: 0) < P.FL_001.not_default_with_postpartum_until_week),
            weightFeatures = !pregnant && !cancerTreatment, prompts = prompts, stopSigns = e.stopSigns,
            clearancePrompt = needsOk && (e.clearancePromptNow || clearance == "always" || clearance == "before_vigorous" || clearance == "suggest"),
            blocked = u.blockIfYes && e.blockIf.isNotEmpty(),
        )
    }

    private fun ConditionEntry.impactKeyIsLow(): Boolean = impact == "low"

    /**
     * SAF-010: resolve every picked condition (phases, control status, unlocks, the doctor's-OK scope) and merge them, each
     * control taking its most restrictive value. A picked child entry brings its parent along (severe obesity → obesity).
     * Unknown IDs are ignored and logged.
     */
    fun resolve(picked: List<UserCondition>): EngineResult<ConditionLimits> {
        if (picked.isEmpty()) return EngineResult(ConditionLimits.NONE)
        val d = ArrayList<Decision>()
        val all = LinkedHashMap<String, UserCondition>()
        for (u in picked) {
            if (u.id !in entries) { d += Decision(DecisionKind.SAFETY, listOf(RuleIds.SAF_010), ReasonKey.CONDITION_BLOCKED, inputs = mapOf("unknown" to u.id)); continue }
            all[u.id] = u
        }
        for (u in all.values.toList()) {
            val parent = entries.getValue(u.id).parent
            if (parent != null && parent in entries && parent !in all) all[parent] = u.copy(id = parent)
        }
        val r = all.values.map { resolveEntry(entries.getValue(it.id), it) }
        if (r.isEmpty()) return EngineResult(ConditionLimits.NONE, d)
        val impact = r.minOf { it.impact }
        val joints = HashMap<Joint, Int>()
        for (x in r) for ((j, v) in x.jointLimits) joints[j] = minOf(joints[j] ?: 4, v)
        val byTag = HashMap<String, Double>()
        for (x in r) for ((t, v) in x.minRirByTag) byTag[t] = maxOf(byTag[t] ?: 0.0, v)
        val hiitLists = r.mapNotNull { it.hiitModalities }
        val limits = ConditionLimits(
            entries = r.map { it.id }.toSet(),
            maxZone = r.minOf { it.maxZone },
            hiit = r.minOf { it.hiit },
            hiitBaseWeeks = r.maxOf { it.hiitBaseWeeks },
            hiitLowImpactOnly = r.any { it.hiitLowImpactOnly },
            hiitModalities = if (hiitLists.isEmpty()) null else hiitLists.reduce { a, b -> a intersect b },
            impact = impact,
            minRir = r.mapNotNull { it.minRir }.maxOrNull(),
            minRirByTag = byTag,
            failureAllowed = r.all { it.failureAllowed },
            avoidTags = r.flatMap { it.avoidTags }.toSet(),
            rangeLimitedTags = r.flatMap { it.rangeLimitedTags }.toSet(),
            jointLimits = joints,
            extraWarmupMin = r.maxOf { it.warmup },
            extraCooldownMin = r.maxOf { it.cooldown },
            maxConsecutiveInactiveDays = r.mapNotNull { it.maxInactive }.minOrNull(),
            strengthOnConsecutiveDays = r.all { it.strengthConsecutive },
            conservative = r.any { it.conservative },
            effortByFeel = r.any { it.effortByFeel },
            strengthBeforeCardio = r.any { it.strengthFirst },
            obesity = "obesity" in all || "obesity_severe" in all,
            // Bone loading only while impact stays "encouraged" (another entry such as osteoarthritis limits it; CON-004 1.1.0).
            boneLoading = r.any { entries.getValue(it.id).boneLoading } && impact == ImpactLevel.ENCOURAGED,
            balanceSessionsPerWeek = r.maxOf { it.balance },
            backExtensorSessionsPerWeek = r.maxOf { it.backExt },
            fatLossOffered = r.all { it.fatLossOffered },
            weightFeatures = r.all { it.weightFeatures },
            prompts = r.flatMap { it.prompts }.distinct(),
            stopSigns = r.flatMap { it.stopSigns }.distinct(),
            clearancePrompts = r.filter { it.clearancePrompt }.map { it.id }.toSet(),
            blocked = r.filter { it.blocked }.map { it.id }.toSet(),
        )
        d += Decision(DecisionKind.SAFETY, listOf(RuleIds.SAF_010), ReasonKey.CONDITIONS_MERGED,
            inputs = mapOf("picked" to picked.map { it.id }, "table" to GeneratedConditions.VERSION),
            outputs = mapOf("entries" to limits.entries.sorted(), "maxZone" to limits.maxZone.name, "hiit" to limits.hiit.name, "impact" to limits.impact.name,
                "minRir" to limits.minRir, "failureAllowed" to limits.failureAllowed, "avoidTags" to limits.avoidTags.sorted(),
                "jointLimits" to limits.jointLimits.mapKeys { it.key.name }, "conservative" to limits.conservative))
        if (limits.clearancePrompts.isNotEmpty()) d += Decision(DecisionKind.SAFETY, listOf(RuleIds.SAF_010, RuleIds.SAF_001),
            ReasonKey.CONDITION_CLEARANCE_NEEDED, outputs = mapOf("entries" to limits.clearancePrompts.sorted()))
        if (limits.blocked.isNotEmpty()) d += Decision(DecisionKind.SAFETY, listOf(RuleIds.SAF_010), ReasonKey.CONDITION_BLOCKED,
            outputs = mapOf("entries" to limits.blocked.sorted()))
        return EngineResult(limits, d)
    }
}
