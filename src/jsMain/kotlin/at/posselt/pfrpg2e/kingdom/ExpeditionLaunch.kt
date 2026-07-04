package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.companion.CompanionPersonalQuest
import at.posselt.pfrpg2e.companion.expeditionLaunchCost
import at.posselt.pfrpg2e.kingdom.data.RawCharacter
import at.posselt.pfrpg2e.kingdom.data.RawCompanionExpedition
import at.posselt.pfrpg2e.kingdom.sheet.contexts.MAX_CONCURRENT_EXPEDITIONS
import at.posselt.pfrpg2e.kingdom.sheet.contexts.activeExpeditionCount

/**
 * Shared expedition launch validation + mutation.
 *
 * Encapsulates the logic for launching an expedition so that ALL launch paths
 * (KingdomSheet, ChatButtons Approve, ChatButtons SendElsewhere, CompanionProfileDialog)
 * enforce the same concurrency cap and RP cost deduction.
 *
 * Lives in jsMain (not commonMain) because it mutates the jsMain kingdom flag types
 * ([KingdomData], [RawCharacter], [RawCompanionExpedition]).
 *
 * @param kingdom The kingdom data to mutate (deep-cloned by caller, caller persists).
 * @param expedition The expedition to launch.
 * @param companions The kingdom's companion array (statuses flipped on a copy set back onto [kingdom]).
 * @return true if the expedition was launched, false if blocked by the concurrency cap.
 */
fun launchExpedition(
    kingdom: KingdomData,
    expedition: RawCompanionExpedition,
    companions: Array<RawCharacter>,
): Boolean {
    // 1. Concurrency cap check
    if (activeExpeditionCount(kingdom.companionExpeditions ?: emptyArray()) >= MAX_CONCURRENT_EXPEDITIONS) {
        return false
    }

    // 2. RP cost deduction (tier-scaled, coerced to >= 0)
    val launchCost = expeditionLaunchCost(expedition.tier)
    if (launchCost > 0) {
        kingdom.resourcePoints.now = (kingdom.resourcePoints.now - launchCost).coerceAtLeast(0)
    }

    // 3. Add expedition to kingdom
    kingdom.companionExpeditions = (kingdom.companionExpeditions ?: emptyArray()) + expedition

    // 4. Flip participant expeditionStatus to "onExpedition"
    val updatedComps = companions.copyOf()
    expedition.companionIds.forEach { cid ->
        updatedComps.find { (it.actorUuid ?: it.name) == cid }?.expeditionStatus = "onExpedition"
    }
    kingdom.companions = updatedComps

    // Note: logExpeditionLaunched is intentionally NOT called here because it is suspend
    // and callers persist the kingdom first; call it after setKingdom.
    return true
}

/**
 * Proposal data for an autonomous companion volunteer.
 */
data class AutonomousProposal(
    /** The companion volunteering. */
    val companion: RawCharacter,
    /** The suggested activity ID (e.g., "personal-quest", "scout", "hunt"). */
    val activityId: String,
    /** The target quest ID if activityId == "personal-quest", otherwise null. */
    val targetQuestId: String?,
    /** The expedition tier to suggest ("routine", "standard", "perilous"). */
    val tier: String,
    /** Estimated total days for the expedition (includes travel). */
    val totalDays: Int,
    /** The RP cost for the suggested tier. */
    val rpCost: Int,
)

/**
 * Compute a concrete proposal for the top-ranked autonomous volunteer.
 *
 * Logic:
 * - If the volunteer has an active personal quest: propose "personal-quest" with that questId.
 * - Otherwise: propose "scout" (safe default).
 * - Tier defaults to "standard".
 * - Duration is the tier base; travel is added by the dialog when a destination is picked.
 *
 * @param volunteer The top-ranked volunteer from selectAutonomousCompanions.
 * @param personalQuests All personal quests on the kingdom.
 * @return A concrete proposal for the dialog prefill and card pitch.
 */
fun computeAutonomousProposal(
    volunteer: RawCharacter,
    personalQuests: Array<CompanionPersonalQuest>,
): AutonomousProposal {
    // Check if volunteer has an active personal quest
    val activeQuest = personalQuests
        .filter { it.status == "active" }
        .firstOrNull { it.companionId == (volunteer.actorUuid ?: volunteer.name) }

    return if (activeQuest != null) {
        AutonomousProposal(
            companion = volunteer,
            activityId = "personal-quest",
            targetQuestId = activeQuest.id,
            tier = "standard",
            totalDays = 3, // standard tier base; travel added in dialog
            rpCost = expeditionLaunchCost("standard"),
        )
    } else {
        AutonomousProposal(
            companion = volunteer,
            activityId = "scout",
            targetQuestId = null,
            tier = "standard",
            totalDays = 3, // standard tier base; travel added in dialog
            rpCost = expeditionLaunchCost("standard"),
        )
    }
}
