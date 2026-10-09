package com.personalfitnesscoach.engine.safety

import com.personalfitnesscoach.engine.model.Joint

/**
 * CON-004 1.1.0 / SAF-010 osteoporosis: the short bone-loading block (≤ 5 min, ≥ 50 low-level landings). Small hops are the
 * default; heel drops (rising onto the toes and dropping onto the heels) are the gentler option. Review R3-11: the block honours
 * pain limits, blocked tags (jumping) and no-jumping regions like any other exercise; with neither variant allowed there is no block.
 */
enum class BoneLoadingVariant(val joints: Map<Joint, Int>, val jumping: Boolean) {
    SMALL_HOPS(mapOf(Joint.ANKLE to 2, Joint.KNEE to 2, Joint.HIP to 1), true),
    HEEL_DROPS(mapOf(Joint.ANKLE to 1, Joint.KNEE to 1), false),
}

object BoneLoading {
    private val LOWER = setOf(Joint.HIP, Joint.KNEE, Joint.ANKLE, Joint.SPINE)

    fun allowed(v: BoneLoadingVariant, jointLimits: Map<Joint, Int>, blockedTags: Set<String>, regions: List<RegionConstraint>,
                painCaution: Set<Joint> = emptySet()): Boolean {
        if (v.jumping && ("jumping" in blockedTags || regions.any { it.noJumping && it.region in LOWER })) return false
        if (v.joints.any { (j, s) -> s > (jointLimits[j] ?: 4) }) return false
        if (regions.any { r -> r.maxStress != null && (v.joints[r.region] ?: 0) > r.maxStress }) return false
        // Pain caution today (SAF-003 continue-with-caution) on a loaded joint: no landings today.
        if (painCaution.any { it in v.joints }) return false
        return true
    }

    /** The first allowed variant (small hops, then heel drops), or null. */
    fun choose(jointLimits: Map<Joint, Int>, blockedTags: Set<String>, regions: List<RegionConstraint>, painCaution: Set<Joint> = emptySet()): BoneLoadingVariant? =
        BoneLoadingVariant.entries.firstOrNull { allowed(it, jointLimits, blockedTags, regions, painCaution) }
}
