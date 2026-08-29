package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.camping.routing.FoundryTravelProvider
import at.posselt.pfrpg2e.data.kingdom.extractRegionData
import at.posselt.pfrpg2e.kingdom.deeds.DeedCatalogEntry
import at.posselt.pfrpg2e.kingdom.deeds.DeedHistoryPoint
import at.posselt.pfrpg2e.kingdom.data.RawGroup
import at.posselt.pfrpg2e.data.kingdom.Relations
import at.posselt.pfrpg2e.data.kingdom.settlements.SettlementSizeType
import at.posselt.pfrpg2e.kingdom.deeds.DeedInputs
import at.posselt.pfrpg2e.kingdom.deeds.undetectedDeeds
import at.posselt.pfrpg2e.data.kingdom.getRoadHexKeys
import at.posselt.pfrpg2e.data.kingdom.milestoneOfferAnswered
import at.posselt.pfrpg2e.data.kingdom.regionFullyClaimed
import at.posselt.pfrpg2e.data.kingdom.roadConnectedToCapital
import com.foundryvtt.kingmaker.kingmaker
import at.posselt.pfrpg2e.utils.postChatTemplate
import js.objects.recordOf
import com.foundryvtt.core.Game
import js.objects.Object

/**
 * Assemble [DeedInputs] and return the deeds that fired this turn but have not been answered
 * (deeds-chronicle plan 4.2). Detection is read-only and deterministic, so the Turn Wizard preview
 * and the End Turn commit agree; NOTHING is posted or awarded here -- the caller renders one
 * digest and the GM's click is what applies XP.
 *
 * Hex topology (road features, claimed state, adjacency) comes from the live Kingmaker module
 * through the same seams the travel-cost path uses; the connectivity and claim logic itself is the
 * pure, unit-tested [roadConnectedToCapital] / [regionFullyClaimed].
 */
fun detectFiredDeeds(
    kingdom: KingdomData,
    currentTurn: Int,
    /** Realm size from getRealmData: kingdom.size is the MANUAL-mode field and stays 1 otherwise. */
    realmSize: Int,
    /** Parsed settlement size bands. RawSettlement.level is written once as 1 and never updated;
     *  the live size is derived from occupied blocks, so only the caller can supply this. */
    settlementSizes: List<SettlementSizeType>,
    /** Victories counted BEFORE the tick, which rewrites every finished battle to "archived". */
    armiesWon: Int,
): List<String> {
    // Level-triggered detectors re-fire forever on standing state, so an offer is suppressed once
    // ANSWERED either way -- awarded (completed) or refused (offerDismissed) -- never merely once
    // posted. Suppressing on "completed" alone re-posts the identical card to a GM who declined.
    val answeredIds = kingdom.milestones
        .filter { milestoneOfferAnswered(it.completed, it.offerDismissed) }
        .map { it.id }
        .toSet()
    // A milestone the GM switched off is not part of this campaign, so it must not be offered.
    // Absent choices count as ON: a kingdom predating a catalog entry has no row for it yet.
    val disabledIds = kingdom.milestones.filter { !it.enabled }.map { it.id }.toSet()
    val catalog = kingdom.getMilestones()
        .filter { it.detectionId != null && it.id !in disabledIds }
        .map { DeedCatalogEntry(id = it.id, detectionId = it.detectionId) }
    if (catalog.isEmpty() || catalog.all { it.id in answeredIds }) return emptyList()

    val regionData = runCatching { extractRegionData(kingdom) }.getOrNull()
    val unansweredDetectionIds = catalog.filter { it.id !in answeredIds }.mapNotNull { it.detectionId }.toSet()

    // The BFS and the region sweep are the only expensive probes here, so each stays gated on a
    // detector that could still fire: an answered deed's topology walk is money for nothing.
    val settlementsRoadedToCapital = if (regionData != null &&
        unansweredDetectionIds.any { it == "road-to-capital" || it == "all-settlements-roaded" }
    ) {
        val capital = regionData.capitalHexKey
        if (capital == null) 0 else {
            val roadSet = runCatching { getRoadHexKeys(kingmaker.state.hexes) }.getOrNull() ?: emptySet()
            val provider = FoundryTravelProvider()
            val neighbors: (String) -> Set<String> = { provider.getAdjacentHexKeys(it).toSet() }
            val settlementHexes = Object.values(regionData.settlementHexKeys).unsafeCast<Array<String>>()
            // all-settlements-roaded needs the COUNT, so this can no longer short-circuit at the
            // first hit the way the road-to-capital-only version did. The capital counts as
            // connected to itself -- excluding it made "every settlement is linked" compare a
            // capital-less numerator against a capital-ful total, so it could never be equal.
            settlementHexes.count { hex ->
                hex == capital || roadConnectedToCapital(hex, capital, roadSet, neighbors)
            }
        }
    } else 0

    val regionsFullyClaimed = if (regionData != null &&
        unansweredDetectionIds.any { it == "region-fully-claimed" || it == "two-regions-claimed" }
    ) {
        val regionSets = Object.values(regionData.regionHexKeys).unsafeCast<Array<Set<String>>>()
        regionSets.count { regionFullyClaimed(it, kingmaker.state.hexes) }
    } else 0

    val history = (kingdom.turnHistory ?: emptyArray()).map {
        DeedHistoryPoint(
            turn = it.turn,
            unrest = it.unrest,
            ruinTotal = (it.ruinCorruption ?: 0) + (it.ruinCrime ?: 0) +
                (it.ruinDecay ?: 0) + (it.ruinStrife ?: 0),
        )
    }
    return undetectedDeeds(
        catalog = catalog,
        answeredIds = answeredIds,
        inputs = DeedInputs(
            turn = currentTurn,
            level = kingdom.level,
            size = realmSize,
            unrest = kingdom.unrest,
            fame = kingdom.fame.now,
            fameMax = kingdom.settings.maximumFamePoints,
            ruinTotal = kingdom.ruin.corruption.value + kingdom.ruin.crime.value +
                kingdom.ruin.decay.value + kingdom.ruin.strife.value,
            regionsFullyClaimed = regionsFullyClaimed,
            settlementsRoadedToCapital = settlementsRoadedToCapital,
            settlementSizes = settlementSizes,
            armiesWon = armiesWon,
            consecutiveSafeCaravanTurns = consecutiveSafeCaravanTurns(kingdom, currentTurn),
            // the persisted value is the enum's camelCase form ("tradeAgreement"); the
            // kebab-case spelling in RawGroup's comment is prose, not data
            tradeAgreements = (kingdom.groups.unsafeCast<Array<RawGroup>?>() ?: emptyArray())
                .count { it.relations == Relations.TRADE_AGREEMENT.value },
            history = history,
        ),
    )
}

/** Turns counting back from [currentTurn] whose shipments all arrived; stops at the first loss. */
private fun consecutiveSafeCaravanTurns(kingdom: KingdomData, currentTurn: Int): Int {
    val rows = kingdom.shipmentHistory ?: return 0
    if (rows.isEmpty()) return 0
    var streak = 0
    var turn = currentTurn
    while (turn > 0) {
        val forTurn = rows.filter { it.turn == turn }
        // a turn with no shipment neither breaks the streak nor extends it: the route is idle,
        // not unsafe, and counting idle turns would award the deed for having no trade at all
        if (forTurn.isNotEmpty()) {
            if (forTurn.any { it.outcome != "delivered" }) break
            streak += 1
        }
        turn -= 1
    }
    return streak
}

/**
 * ONE whispered digest per turn listing every deed that fired (plan 7): turn 1 of a kingdom
 * adopted mid-campaign can fire a dozen true, retroactive deeds at once, and a dozen separate
 * cards is how a GM learns to ignore the feature. Each row awards individually; one button
 * dismisses the lot.
 */
suspend fun postDeedsDigest(game: Game, actor: KingdomActor, kingdom: KingdomData, firedIds: List<String>) {
    if (firedIds.isEmpty()) return
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    if (gmUserIds.isEmpty()) return
    val catalog = kingdom.getMilestones().associateBy { it.id }
    val rows = firedIds.mapNotNull { id ->
        catalog[id]?.let { m ->
            recordOf<String, Any?>("milestoneId" to id, "milestoneName" to m.name, "milestoneXp" to m.xp)
        }
    }.toTypedArray()
    if (rows.isEmpty()) return
    postChatTemplate(
        templatePath = "chatmessages/deeds-digest.hbs",
        templateContext = recordOf<String, Any?>(
            "actorUuid" to actor.uuid,
            "deeds" to rows,
            "allIds" to firedIds.joinToString(","),
        ),
        whisper = gmUserIds,
    )
}
