package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.camping.routing.FoundryTravelProvider
import at.posselt.pfrpg2e.data.kingdom.extractRegionData
import at.posselt.pfrpg2e.data.kingdom.getRoadHexKeys
import at.posselt.pfrpg2e.data.kingdom.regionFullyClaimed
import at.posselt.pfrpg2e.data.kingdom.roadConnectedToCapital
import at.posselt.pfrpg2e.utils.postChatTemplate
import com.foundryvtt.core.Game
import com.foundryvtt.kingmaker.kingmaker
import js.objects.Object
import js.objects.recordOf

/** Ids of the two auto-detectable house-rule milestones (see the data/milestones directory). */
const val MILESTONE_ROAD_TO_CAPITAL = "connect-settlement-to-capital-via-roads"
const val MILESTONE_REGION_CLAIMED = "claim-all-hexes-in-a-region"

/**
 * At End Turn, detect the two auto-detectable house-rule milestones and post a GM-confirmed award
 * OFFER for each that fired and is not yet completed. Detection is read-only (identical in the Turn
 * Wizard preview); the XP is only ever applied by the GM clicking the km-offer-milestone button.
 * Already-completed milestones are skipped, so an awarded milestone is never re-offered.
 *
 * Hex topology (adjacency for the road BFS, road features, claimed state) is read from the live
 * Kingmaker module via the same seams the travel-cost path uses; the connectivity/claim logic itself
 * is the pure, unit-tested [roadConnectedToCapital] / [regionFullyClaimed].
 */
suspend fun detectAndOfferMilestones(game: Game, actor: KingdomActor, kingdom: KingdomData) {
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    if (gmUserIds.isEmpty()) return

    val completedIds = kingdom.milestones.filter { it.completed }.map { it.id }.toSet()
    if (MILESTONE_ROAD_TO_CAPITAL in completedIds && MILESTONE_REGION_CLAIMED in completedIds) return

    val regionData = runCatching { extractRegionData(kingdom) }.getOrNull() ?: return
    val fired = mutableListOf<String>()

    if (MILESTONE_ROAD_TO_CAPITAL !in completedIds) {
        val capital = regionData.capitalHexKey
        if (capital != null) {
            val roadSet = runCatching { getRoadHexKeys(kingmaker.state.hexes) }.getOrNull() ?: emptySet()
            val provider = FoundryTravelProvider()
            val neighbors: (String) -> Set<String> = { provider.getAdjacentHexKeys(it).toSet() }
            val settlementHexes = Object.values(regionData.settlementHexKeys).unsafeCast<Array<String>>()
            val connected = settlementHexes.any { hex ->
                hex != capital && roadConnectedToCapital(hex, capital, roadSet, neighbors)
            }
            if (connected) fired.add(MILESTONE_ROAD_TO_CAPITAL)
        }
    }

    if (MILESTONE_REGION_CLAIMED !in completedIds) {
        val regionSets = Object.values(regionData.regionHexKeys).unsafeCast<Array<Set<String>>>()
        val anyFull = regionSets.any { regionFullyClaimed(it, kingmaker.state.hexes) }
        if (anyFull) fired.add(MILESTONE_REGION_CLAIMED)
    }

    for (id in fired) {
        val milestone = kingdom.getMilestones().find { it.id == id } ?: continue
        postChatTemplate(
            templatePath = "chatmessages/milestone-offer.hbs",
            templateContext = recordOf<String, Any?>(
                "milestoneId" to id,
                "milestoneName" to milestone.name,
                "milestoneXp" to milestone.xp,
                "actorUuid" to actor.uuid,
            ),
            whisper = gmUserIds,
        )
    }
}
