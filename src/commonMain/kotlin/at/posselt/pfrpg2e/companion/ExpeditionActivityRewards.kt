package at.posselt.pfrpg2e.companion

import at.posselt.pfrpg2e.data.actor.Skill
import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess

/**
 * Activity-specific reward layer, applied ON TOP of the base per-degree resolution
 * ([at.posselt.pfrpg2e.camping.ExpeditionResolverEngine.resolve]). Turns activity choice into
 * the core decision of the expedition: a hunt brings back food, a craft mission lumber/ore, a
 * treasure hunt swings loot variance, training multiplies XP, rest heals the wounded, scouting
 * yields intel.
 *
 * All amounts are DELIBERATELY SMALL — expeditions supplement, never replace, kingdom income
 * (see [lootTierToResourcePoints] for the philosophy). Pure and unit-tested; the impure resolution
 * wrapper accrues these onto the expedition record and the reward-apply site spends them.
 */
data class ExpeditionActivityReward(
    /** Commodity grants keyed by type ("food"|"lumber"|"ore"|"stone"|"luxuries"). */
    val commodities: Map<String, Int> = emptyMap(),
    /** Multiplier on the base XP award (train activity). */
    val xpMultiplier: Double = 1.0,
    /** Days to shave off injured companions' recovery (rest activity). */
    val injuryDaysReduction: Int = 0,
    /** i18n key for a GM intel note (scout activity), or null. */
    val intelNoteKey: String? = null,
    /** Overrides the engine's loot tier when non-null (treasure-hunt softens a plain failure). */
    val lootTierOverride: String? = null,
    /** Extra resource points on top of the loot tier (treasure-hunt jackpot on a crit). */
    val bonusLootRp: Int = 0,
)

/** Per-tier commodity/recovery unit: routine 0, standard 1, perilous 2. */
internal fun expeditionTierUnit(tier: String): Int = when (tier) {
    "perilous" -> 2
    "standard" -> 1
    else -> 0 // routine
}

/**
 * Map an activity's declared skill names (from the activity catalog) to [Skill] enum values,
 * dropping names that aren't real kingdom skills ("perception", "hunting", "any"). Case-insensitive.
 * Order is preserved so the resolution wrapper rolls them in catalog order.
 */
fun expeditionActivitySkills(skillNames: List<String>): List<Skill> =
    skillNames.mapNotNull { name ->
        val v = name.lowercase()
        Skill.entries.firstOrNull { it.value == v }
    }

/**
 * Activity-specific extras for [activityId] at [tier]/[degree]. Non-catalog or unknown activities
 * return the neutral default (no extras). Success-gated grants apply on SUCCESS and CRITICAL_SUCCESS.
 */
fun expeditionActivityReward(
    activityId: String,
    tier: String,
    degree: DegreeOfSuccess,
): ExpeditionActivityReward {
    val unit = expeditionTierUnit(tier)
    val isCritSuccess = degree == DegreeOfSuccess.CRITICAL_SUCCESS
    val isSuccess = isCritSuccess || degree == DegreeOfSuccess.SUCCESS
    val isPlainFailure = degree == DegreeOfSuccess.FAILURE

    return when (activityId) {
        "hunt" -> if (isSuccess && unit > 0) {
            ExpeditionActivityReward(commodities = mapOf("food" to unit))
        } else ExpeditionActivityReward()

        "craft" -> if (isSuccess && unit > 0) {
            ExpeditionActivityReward(commodities = mapOf("lumber" to unit, "ore" to unit))
        } else ExpeditionActivityReward()

        "treasure-hunt" -> ExpeditionActivityReward(
            // A crit hits the jackpot: major loot + a bonus RP. A plain failure still turns up
            // something (minor) rather than nothing — treasure hunting rarely comes back empty.
            bonusLootRp = if (isCritSuccess) 1 else 0,
            lootTierOverride = if (isPlainFailure) "minor" else null,
        )

        "train" -> if (isSuccess) {
            ExpeditionActivityReward(
                xpMultiplier = when (tier) {
                    "perilous" -> 1.5
                    "standard" -> 1.25
                    else -> 1.1 // routine
                },
            )
        } else ExpeditionActivityReward()

        "rest" -> if (isSuccess && unit > 0) {
            ExpeditionActivityReward(injuryDaysReduction = unit)
        } else ExpeditionActivityReward()

        "scout" -> if (isSuccess) {
            ExpeditionActivityReward(
                intelNoteKey = when (tier) {
                    "perilous" -> "kingdom.expeditions.intel.perilous"
                    "standard" -> "kingdom.expeditions.intel.standard"
                    else -> "kingdom.expeditions.intel.routine"
                },
            )
        } else ExpeditionActivityReward()

        else -> ExpeditionActivityReward()
    }
}
