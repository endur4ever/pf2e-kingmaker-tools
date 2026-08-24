package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.camping.routing.FoundryTravelProvider
import at.posselt.pfrpg2e.data.kingdom.extractRegionData
import at.posselt.pfrpg2e.kingdom.deeds.DeedCatalogEntry
import at.posselt.pfrpg2e.kingdom.deeds.DeedInputs
import at.posselt.pfrpg2e.kingdom.deeds.undetectedDeeds
import at.posselt.pfrpg2e.data.kingdom.getRoadHexKeys
import at.posselt.pfrpg2e.data.kingdom.milestoneOfferAnswered
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

    // Both detectors are LEVEL-triggered on standing world state: the road stays built and the
    // claimed region stays claimed, so every End Turn re-detects the same thing forever. An offer is
    // therefore suppressed once it has been ANSWERED either way -- awarded (completed) or refused
    // (offerDismissed) -- not merely once it has been posted. Suppressing on "completed" alone let a
    // GM who declined the house rule get the identical card again every turn for the rest of the
    // campaign, with no way to stop it.
    val answeredIds = kingdom.milestones
        .filter { milestoneOfferAnswered(it.completed, it.offerDismissed) }
        .map { it.id }
        .toSet()
    if (MILESTONE_ROAD_TO_CAPITAL in answeredIds && MILESTONE_REGION_CLAIMED in answeredIds) return

    val regionData = runCatching { extractRegionData(kingdom) }.getOrNull() ?: return

    // The expensive topology probes stay gated on answeredIds exactly as before -- an answered
    // milestone's BFS is money for nothing -- so the probes feed counts into the pure detection
    // core rather than deciding anything themselves. undetectedDeeds re-applies the answered
    // filter, which is harmless: a gated probe reports 0 and its detector cannot fire anyway.
    val settlementsRoadedToCapital = if (MILESTONE_ROAD_TO_CAPITAL !in answeredIds) {
        val capital = regionData.capitalHexKey
        if (capital != null) {
            val roadSet = runCatching { getRoadHexKeys(kingmaker.state.hexes) }.getOrNull() ?: emptySet()
            val provider = FoundryTravelProvider()
            val neighbors: (String) -> Set<String> = { provider.getAdjacentHexKeys(it).toSet() }
            val settlementHexes = Object.values(regionData.settlementHexKeys).unsafeCast<Array<String>>()
            // any {} not count {}: the detector needs >= 1, and the original short-circuited at
            // the first connected settlement -- counting them all would run one BFS per settlement
            // for an answer that cannot change past the first hit.
            val connected = settlementHexes.any { hex ->
                hex != capital && roadConnectedToCapital(hex, capital, roadSet, neighbors)
            }
            if (connected) 1 else 0
        } else {
            0
        }
    } else {
        0
    }

    val regionsFullyClaimed = if (MILESTONE_REGION_CLAIMED !in answeredIds) {
        val regionSets = Object.values(regionData.regionHexKeys).unsafeCast<Array<Set<String>>>()
        if (regionSets.any { regionFullyClaimed(it, kingmaker.state.hexes) }) 1 else 0
    } else {
        0
    }

    val fired = undetectedDeeds(
        catalog = listOf(
            DeedCatalogEntry(MILESTONE_ROAD_TO_CAPITAL, "road-to-capital"),
            DeedCatalogEntry(MILESTONE_REGION_CLAIMED, "region-fully-claimed"),
        ),
        answeredIds = answeredIds,
        inputs = DeedInputs(
            settlementsRoadedToCapital = settlementsRoadedToCapital,
            regionsFullyClaimed = regionsFullyClaimed,
        ),
    )

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
