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
        val avoidModalities: Set<Modality> = emptySet(),
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
            // Attestations are cumulative (review R3-05): a phase applies only when its own and every earlier attestation is confirmed,
            // so "healed; no pelvic floor symptoms" also gates every later phase. Otherwise the last phase before the first unconfirmed one stays.
            val firstUnconfirmed = (0..idx).firstOrNull { e.phases[it].attest != null && !u.attested }
            if (firstUnconfirmed != null) idx = maxOf(0, firstUnconfirmed - 1)
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
        // Week-based tags (pregnancy: no lying on the back or front from week 20). An unknown week counts as past every threshold (review R3-03).
        val pw = u.pregnancyWeek
        for ((week, tags) in e.avoidTagsFromWeek) if (pw == null || pw >= week) avoid += tags
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
        // Sub-flags (table 1.0.1, review R3-01): their controls come from the table (recent breastbone surgery: nothing that loads the arms,
        // shoulders or chest and no arm-driven cardio machines), their text is shown as a prompt.
        val avoidModalities = LinkedHashSet<Modality>()
        for (f in u.subFlags) e.subFlagEffects[f]?.let { eff -> avoid += eff.avoidTags; eff.avoidModalities.mapNotNullTo(avoidModalities) { Modality.byKey(it) } }
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
        // The doctor's-OK prompt stays while the scope the entry needs is missing ("before vigorous": until vigorous or intervals is confirmed).
        val needsOk = when (clearance) {
            "none" -> false
            "before_vigorous" -> ClearanceScope.VIGOROUS !in u.clearance && ClearanceScope.INTERVALS !in u.clearance
            else -> u.clearance.isEmpty()
        }
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
            blocked = u.blockIfYes && e.blockIf.isNotEmpty(), avoidModalities = avoidModalities,
        )
    }

    /** An ID the table does not know: SAF-001 conservative mode with a doctor's-OK prompt (review R3-07) — limits never silently disappear. */
    private fun unknownResolved(): Resolved = Resolved(
        id = "?", maxZone = Zone.Z1, hiit = HiitPermission.NO, hiitBaseWeeks = 0, hiitLowImpactOnly = false, hiitModalities = null, impact = ImpactLevel.LOW,
        minRir = P.SAF_001.conservative_mode.min_rir.toDouble(), minRirByTag = emptyMap(), failureAllowed = false, avoidTags = emptySet(),
        rangeLimitedTags = emptySet(), jointLimits = emptyMap(), warmup = 0, cooldown = 0, maxInactive = null, strengthConsecutive = true,
        conservative = true, effortByFeel = false, strengthFirst = false, balance = 0, backExt = 0, fatLossOffered = true, weightFeatures = true,
        prompts = emptyList(), stopSigns = emptyList(), clearancePrompt = true, blocked = false)

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
        val unknown = LinkedHashSet<String>()
        for (u0 in picked) {
            // High blood pressure (review R3-08): the status answer decides the entry, whichever ID was stored ("not sure" counts as "no").
            val u = if (u0.id == "hbp_controlled" && u0.controlled != null && u0.controlled != ControlStatus.YES) {
                d += Decision(DecisionKind.SAFETY, listOf(RuleIds.SAF_010), ReasonKey.CONDITION_STATUS_APPLIED,
                    inputs = mapOf("entry" to u0.id, "status" to u0.controlled.name), outputs = mapOf("entry" to "hbp_not_controlled"))
                u0.copy(id = "hbp_not_controlled")
            } else u0
            if (u.id !in entries) {
                unknown += u.id
                d += Decision(DecisionKind.SAFETY, listOf(RuleIds.SAF_010, RuleIds.SAF_001), ReasonKey.CONDITION_UNKNOWN, inputs = mapOf("unknown" to u.id))
                continue
            }
            all[u.id] = u
        }
        for (u in all.values.toList()) {
            val parent = entries.getValue(u.id).parent ?: continue
            if (parent in entries) { if (parent !in all) all[parent] = u.copy(id = parent); continue }
            // A parent group (diabetes add-ons, review R3-04): without any member picked, every member comes along (most restrictive).
            val members = GeneratedConditions.parentGroups[parent] ?: continue
            if (members.none { it in all }) {
                for (m in members) all[m] = u.copy(id = m)
                d += Decision(DecisionKind.SAFETY, listOf(RuleIds.SAF_010), ReasonKey.CONDITION_PARENT_ASSUMED,
                    inputs = mapOf("entry" to u.id, "group" to parent), outputs = mapOf("added" to members))
            }
        }
        val known = all.values.map { resolveEntry(entries.getValue(it.id), it) }
        if (known.isEmpty() && unknown.isEmpty()) return EngineResult(ConditionLimits.NONE, d)
        val r = known + if (unknown.isEmpty()) emptyList() else listOf(unknownResolved())
        val impact = r.minOf { it.impact }
        val joints = HashMap<Joint, Int>()
        for (x in r) for ((j, v) in x.jointLimits) joints[j] = minOf(joints[j] ?: 4, v)
        val byTag = HashMap<String, Double>()
        for (x in r) for ((t, v) in x.minRirByTag) byTag[t] = maxOf(byTag[t] ?: 0.0, v)
        val hiitLists = r.mapNotNull { it.hiitModalities }
        val limits = ConditionLimits(
            entries = known.map { it.id }.toSet(),
            unknown = unknown,
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
            boneLoading = known.any { entries.getValue(it.id).boneLoading } && impact == ImpactLevel.ENCOURAGED,
            balanceSessionsPerWeek = r.maxOf { it.balance },
            backExtensorSessionsPerWeek = r.maxOf { it.backExt },
            fatLossOffered = r.all { it.fatLossOffered },
            weightFeatures = r.all { it.weightFeatures },
            prompts = r.flatMap { it.prompts }.distinct(),
            stopSigns = r.flatMap { it.stopSigns }.distinct(),
            clearancePrompts = known.filter { it.clearancePrompt }.map { it.id }.toSet() + unknown,
            blocked = known.filter { it.blocked }.map { it.id }.toSet(),
            avoidModalities = r.flatMap { it.avoidModalities }.toSet(),
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
