package at.posselt.pfrpg2e.kingdom.mapdynamism

import at.posselt.pfrpg2e.camping.routing.FoundryTravelProvider
import at.posselt.pfrpg2e.companion.formatHexKeyLabel
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.RawRewildTracker
import at.posselt.pfrpg2e.kingdom.data.WarThreatStatus
import at.posselt.pfrpg2e.kingdom.setKingdom
import at.posselt.pfrpg2e.utils.asSequence
import at.posselt.pfrpg2e.utils.postChatTemplate
import js.array.component1
import js.array.component2
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import com.foundryvtt.kingmaker.kingmaker
import js.objects.recordOf
import kotlinx.js.JsPlainObject

/**
 * End Turn adapter for map dynamism
 * (`docs/plans/2026-07-09-plan-map-dynamism.md` SS3.3, SS4.1, SS5): reconciles the re-wild
 * side-table (the ONE write that needs no offer -- it is bookkeeping, not a map change) and posts
 * a single GM-whispered Map Changes card whose every row is an offer. Nothing on the shared map
 * moves until a GM clicks.
 */

/** Adjacency over the live Kingmaker region; null when the module is absent (offers just skip). */
fun kingmakerNeighbors(): ((String) -> Set<String>)? =
    runCatching {
        val provider = FoundryTravelProvider()
        // touch the region once so an absent module fails HERE, not inside the BFS
        kingmaker.region.hexes
        val closure: (String) -> Set<String> = { provider.getAdjacentHexKeys(it).toSet() }
        closure
    }.getOrNull()

/** Live cleared-but-unclaimed hex keys; empty when the module is absent. */
fun clearedUnclaimedHexes(): Set<String> =
    runCatching {
        kingmaker.state.hexes.asSequence()
            .filter { (_, hex) -> hex.cleared == true && hex.claimed != true }
            .map { (key, _) -> key }
            .toSet()
    }.getOrDefault(emptySet())

@Suppress("unused")
@JsPlainObject
external interface MapMigrationRowContext {
    val threatId: String
    val name: String
    val fromLabel: String
    val toLabel: String
}

@Suppress("unused")
@JsPlainObject
external interface MapRewildRowContext {
    val hexKey: String
    val hexLabel: String
    val ageLabel: String
}

@Suppress("unused")
@JsPlainObject
external interface MapChangesContext {
    val title: String
    val actorUuid: String
    val migrations: Array<MapMigrationRowContext>
    val rewilds: Array<MapRewildRowContext>
    val advanceLabel: String
    val holdLabel: String
    val rewildLabel: String
    val keepLabel: String
}

fun hexDisplayLabel(hexKey: String): String = formatHexKeyLabel(hexKey) ?: hexKey

/**
 * Wandering-threat proposals for this turn. Active threats only -- an arrived threat is the
 * war-threat escalation flow's business (SS5.3) -- and the pure core skips anything already
 * resolved this turn or already at target.
 */
fun migrationProposals(
    kingdom: KingdomData,
    currentTurn: Int,
    neighbors: (String) -> Set<String>,
): List<ThreatMigrationProposal> {
    if (kingdom.settings.threatMigrationEnabled != true) return emptyList()
    val wandering = (kingdom.warThreats ?: emptyArray())
        .filter { it.status == WarThreatStatus.ACTIVE.value && it.wanders == true }
        .mapNotNull { threat ->
            val target = threat.targetHexLocation ?: return@mapNotNull null
            WanderingThreat(
                threatId = threat.id,
                currentHexKey = threat.currentHexLocation ?: target,
                targetHexKey = target,
                migrationConsumedTurn = threat.migrationConsumedTurn,
            )
        }
    return proposeThreatSteps(wandering, currentTurn, neighbors)
}

/**
 * Reconcile the re-wild side-table against the live map and return this turn's offer candidates.
 * Surviving trackers keep their ORIGINAL clearedSinceTurn and their offerConsumed flag (the pure
 * core carries neither raw field, so the merge happens here, keyed by hex); candidates exclude
 * consumed rows so a "keep cleared" stays quiet for the rest of its cycle.
 */
fun reconcileRewild(
    kingdom: KingdomData,
    liveClearedUnclaimed: Set<String>,
    currentTurn: Int,
): List<String> {
    val existingRaw = kingdom.rewildTrackers ?: emptyArray()
    val consumedByHex = existingRaw.associate { it.hexKey to it.offerConsumed }
    val models = existingRaw.map { RewildTracker(hexKey = it.hexKey, clearedSinceTurn = it.clearedSinceTurn) }
    val updated = updateRewildTrackers(models, liveClearedUnclaimed, currentTurn)
    kingdom.rewildTrackers = updated.map { tracker ->
        val obj = js("{}").unsafeCast<RawRewildTracker>()
        obj.hexKey = tracker.hexKey
        obj.clearedSinceTurn = tracker.clearedSinceTurn
        obj.offerConsumed = consumedByHex[tracker.hexKey]
        obj
    }.toTypedArray()
    val delay = kingdom.settings.rewildDelayTurns ?: 6
    return rewildCandidates(updated, currentTurn, delay)
        .filter { consumedByHex[it] != true }
}

/**
 * The whole End Turn surface: reconcile (persisted), propose (offers only), post ONE card when
 * anything is on it. Wrapped so an absent Kingmaker module or a broken region graph degrades to
 * a missing card, never an aborted End Turn.
 */
suspend fun postMapDynamismOffers(
    game: Game,
    actor: KingdomActor,
    kingdom: KingdomData,
    currentTurn: Int,
) {
    runCatching {
        // No setKingdom here. This runs AFTER End Turn's persist and after the offer cards are
        // live, so a second wholesale write of the in-hand kingdom would ship a snapshot that
        // predates any click on them -- silently reverting a granted epithet, a rival war-offer
        // bump, a council ballot, anything. reconcileRewild's mutation is persisted by
        // performEndTurn instead, which calls it BEFORE its own write.
        val candidates = reconcileRewild(kingdom, clearedUnclaimedHexes(), currentTurn)
        val neighbors = kingmakerNeighbors()
        val proposals = if (neighbors != null) {
            migrationProposals(kingdom, currentTurn, neighbors)
        } else {
            emptyList()
        }
        if (proposals.isEmpty() && candidates.isEmpty()) return
        val threatsById = (kingdom.warThreats ?: emptyArray()).associateBy { it.id }
        val trackersByHex = (kingdom.rewildTrackers ?: emptyArray()).associateBy { it.hexKey }
        val gmIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
        if (gmIds.isEmpty()) return
        postChatTemplate(
            templatePath = "chatmessages/map-changes-offer.hbs",
            templateContext = MapChangesContext(
                title = t("kingdom.mapDynamism.title"),
                actorUuid = actor.uuid,
                migrations = proposals.map { proposal ->
                    MapMigrationRowContext(
                        threatId = proposal.threatId,
                        name = threatsById[proposal.threatId]?.name ?: proposal.threatId,
                        fromLabel = hexDisplayLabel(proposal.fromHex),
                        toLabel = hexDisplayLabel(proposal.toHex),
                    )
                }.toTypedArray(),
                rewilds = candidates.map { hexKey ->
                    val age = trackersByHex[hexKey]
                        ?.let { currentTurn - it.clearedSinceTurn }
                        ?: 0
                    MapRewildRowContext(
                        hexKey = hexKey,
                        hexLabel = hexDisplayLabel(hexKey),
                        ageLabel = t("kingdom.mapDynamism.age", recordOf("turns" to age.toString())),
                    )
                }.toTypedArray(),
                advanceLabel = t("kingdom.mapDynamism.advance"),
                holdLabel = t("kingdom.mapDynamism.hold"),
                rewildLabel = t("kingdom.mapDynamism.rewild"),
                keepLabel = t("kingdom.mapDynamism.keep"),
            ),
            whisper = gmIds,
        )
    }.onFailure { console.error("map dynamism offers failed", it) }
}
