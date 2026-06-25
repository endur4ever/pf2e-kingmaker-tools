package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawCharacter
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Pure companion autonomy selection logic.
 *
 * Given the list of companions on the kingdom actor, determines which companions
 * are eligible to volunteer for an expedition and ranks them by willingness.
 *
 * Selection priority (higher score = more willing):
 * 1. Has active personal quest (+100)
 * 2. Discovery status >= "established" (+50)
 * 3. Personality affinity via influence (0-12, added directly as bonus)
 *
 * Eligibility requirements:
 * - role == "companion"
 * - active == true
 * - campAvailable == true
 * - expeditionStatus == "available"
 * - not traveling
 * - not injured (injuryDaysRemaining == null)
 */
@JsExport
@JsName("CompanionAutonomy")
object CompanionAutonomy {

    private const val PERSONAL_QUEST_BONUS = 100
    private const val DISCOVERY_ESTABLISHED_BONUS = 50

    /**
     * Discovery status ranks — used to determine if discovery >= "established".
     */
    private val discoveryRanks = mapOf(
        "unknown" to 0,
        "introduced" to 1,
        "established" to 2,
        "trusted" to 3,
        "bonded" to 4,
    )

    /**
     * Select and rank eligible companions for an autonomous expedition offer.
     *
     * @param companions All companions from the kingdom actor.
     * @return Sorted list of willing companions (highest priority first). May be empty.
     */
    fun selectAutonomousCompanions(companions: List<RawCharacter>): List<RawCharacter> {
        return companions
            .filter { isEligible(it) }
            .sortedByDescending { computeWillingnessScore(it) }
    }

    /**
     * Check whether a companion is eligible to volunteer for an expedition.
     */
    fun isEligible(companion: RawCharacter): Boolean {
        return companion.role == "companion" &&
                companion.active &&
                companion.campAvailable &&
                companion.expeditionStatus == "available" &&
                !companion.traveling &&
                companion.injuryDaysRemaining == null
    }

    /**
     * Compute the willingness score for a companion.
     * Higher score = more willing to volunteer.
     */
    fun computeWillingnessScore(companion: RawCharacter): Int {
        var score = 0

        // Priority 1: Active personal quest
        if (companion.personalQuestIds.isNotEmpty()) {
            score += PERSONAL_QUEST_BONUS
        }

        // Priority 2: Discovery >= established
        val discoveryRank = discoveryRanks[companion.discoveryStatus] ?: 0
        if (discoveryRank >= (discoveryRanks["established"] ?: 2)) {
            score += DISCOVERY_ESTABLISHED_BONUS
        }

        // Priority 3: Influence as willingness (0-12)
        score += companion.influence

        return score
    }
}
