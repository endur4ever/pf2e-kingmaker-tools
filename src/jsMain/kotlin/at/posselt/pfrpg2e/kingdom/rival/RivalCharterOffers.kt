package at.posselt.pfrpg2e.kingdom.rival

import at.posselt.pfrpg2e.camping.getCamping
import at.posselt.pfrpg2e.camping.getCampingActors
import at.posselt.pfrpg2e.camping.getPartyCurrentHexKey
import at.posselt.pfrpg2e.data.hex.HexContentType
import at.posselt.pfrpg2e.data.hex.HexContentVisibility
import at.posselt.pfrpg2e.data.kingdom.RIVAL_STATUS_ACTIVE
import at.posselt.pfrpg2e.data.kingdom.RIVAL_STATUS_DEFECTED
import at.posselt.pfrpg2e.data.kingdom.RIVAL_STATUS_JOINED
import at.posselt.pfrpg2e.data.kingdom.RIVAL_STATUS_RETIRED
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.RawHexContent
import at.posselt.pfrpg2e.kingdom.data.RawRivalCharterParty
import at.posselt.pfrpg2e.kingdom.data.isActive
import at.posselt.pfrpg2e.kingdom.mapdynamism.hexDisplayLabel
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import io.github.uuidjs.uuid.v4
import js.objects.recordOf

/**
 * The rival charter party's offer surfaces (plan section 5): ONE whispered digest per turn
 * carrying every band that crossed a threshold, each row with its own GM-confirmed buttons.
 * Nothing here writes the map; the handlers in ChatButtons.kt do, one click at a time.
 */

/**
 * Bands standing in the party's hex this turn (plan section 5.4), stamped so each band offers the
 * encounter once per turn. Called BEFORE the single persist because it writes the stamp.
 */
fun rivalCoLocationBands(game: Game, kingdom: KingdomData, turn: Int): List<RawRivalCharterParty> {
    val campingActor = game.getCampingActors().firstOrNull() ?: return emptyList()
    val partyHex = runCatching { getPartyCurrentHexKey(game, campingActor, campingActor.getCamping()) }
        .getOrNull() ?: return emptyList()
    return (kingdom.rivalCharterParties ?: emptyArray()).filter { band ->
        band.isActive() && band.currentHexKey == partyHex && band.lastEncounterOfferTurn != turn
    }.onEach { it.lastEncounterOfferTurn = turn }
}

/** Upsert the discovery-on-arrival row (plan section 5.5 stage 3); idempotent by hex and name. */
fun applyRivalDiscovery(kingdom: KingdomData, band: RawRivalCharterParty, hexKey: String, turn: Int, pendingEncounter: Boolean = false) {
    val name = t("kingdom.rivalCharter.discovery.name", recordOf("band" to band.name))
    val existing = (kingdom.hexContents ?: emptyArray()).find { it.hexKey == hexKey && it.name == name }
    if (existing != null) {
        existing.pendingEncounter = pendingEncounter || existing.pendingEncounter == true
        return
    }
    kingdom.hexContents = (kingdom.hexContents ?: emptyArray()) + RawHexContent(
        id = v4(),
        hexKey = hexKey,
        type = HexContentType.CUSTOM.value,
        name = name,
        visibility = HexContentVisibility.DISCOVERED.value,
        gmNotes = "${band.name} -- turn $turn (${band.objectiveKind ?: "arrival"})",
        playerText = t("kingdom.rivalCharter.discovery.playerText"),
        pendingEncounter = pendingEncounter,
    )
}

/** The co-location note (plan section 5.4), with or without a queued encounter. */
fun applyRivalColocation(kingdom: KingdomData, band: RawRivalCharterParty, hexKey: String, turn: Int, queueEncounter: Boolean) {
    val name = t("kingdom.rivalCharter.colocation.note", recordOf("band" to band.name))
    val existing = (kingdom.hexContents ?: emptyArray()).find { it.hexKey == hexKey && it.name == name }
    if (existing != null) {
        if (queueEncounter) existing.pendingEncounter = true
        return
    }
    kingdom.hexContents = (kingdom.hexContents ?: emptyArray()) + RawHexContent(
        id = v4(),
        hexKey = hexKey,
        type = HexContentType.CUSTOM.value,
        name = name,
        visibility = HexContentVisibility.DISCOVERED.value,
        gmNotes = "${band.name} -- co-located turn $turn",
        playerText = "",
        pendingEncounter = queueEncounter,
    )
}

/** Lifecycle literal for a digest button, or null for a value this build does not recognise. */
fun rivalLifecycleStatus(choice: String?): String? = when (choice) {
    "retire" -> RIVAL_STATUS_RETIRED
    "defect" -> RIVAL_STATUS_DEFECTED
    "join" -> RIVAL_STATUS_JOINED
    "keep" -> RIVAL_STATUS_ACTIVE
    else -> null
}

suspend fun postRivalCharterDigest(
    game: Game,
    actorUuid: String,
    turn: Int,
    moves: List<RivalPartyMove>,
    coLocated: List<RawRivalCharterParty>,
    lifecycleBands: List<RawRivalCharterParty>,
) {
    val arrivals = moves.filter { it.arrivedAt != null }
    val confrontations = moves.filter { it.confrontation }
    val rumors = moves.filter { it.rumorTarget != null }
    if (arrivals.isEmpty() && confrontations.isEmpty() && rumors.isEmpty() && coLocated.isEmpty() && lifecycleBands.isEmpty()) return
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    // an EMPTY whisper array posts publicly, not to nobody
    if (gmUserIds.isEmpty()) return
    postChatTemplate(
        templatePath = "chatmessages/rival-charter-digest.hbs",
        templateContext = recordOf<String, Any?>(
            "actorUuid" to actorUuid,
            "turn" to turn,
            "arrivals" to arrivals.map { m ->
                recordOf<String, Any?>(
                    "bandId" to m.bandId, "band" to m.bandName, "hexKey" to m.arrivedAt!!.hexKey,
                    "title" to t("kingdom.rivalCharter.arrivalOffer.title", recordOf("band" to m.bandName, "place" to m.arrivedAt.label)),
                )
            }.toTypedArray(),
            "confrontations" to confrontations.map { m ->
                recordOf<String, Any?>(
                    "bandId" to m.bandId, "band" to m.bandName, "hexKey" to (m.currentHexKey ?: ""),
                    "title" to t("kingdom.rivalCharter.confrontationOffer.title", recordOf("band" to m.bandName)),
                )
            }.toTypedArray(),
            "rumors" to rumors.map { m ->
                recordOf<String, Any?>(
                    "bandId" to m.bandId, "band" to m.bandName, "hexKey" to m.rumorTarget!!.hexKey,
                    "line" to t("kingdom.rivalCharter.rumor.sighted", recordOf("faction" to (m.factionRef ?: m.bandName), "place" to m.rumorTarget.label)),
                )
            }.toTypedArray(),
            "encounters" to coLocated.map { band ->
                recordOf<String, Any?>(
                    "bandId" to band.id, "band" to band.name, "hexKey" to (band.currentHexKey ?: ""),
                    "line" to t("kingdom.rivalCharter.colocation.note", recordOf("band" to band.name)) + " " + hexDisplayLabel(band.currentHexKey ?: ""),
                )
            }.toTypedArray(),
            "lifecycle" to lifecycleBands.map { band ->
                recordOf<String, Any?>(
                    "bandId" to band.id, "band" to band.name,
                    "title" to t("kingdom.rivalCharter.lifecycle.title", recordOf("band" to band.name)),
                )
            }.toTypedArray(),
        ),
        whisper = gmUserIds,
    )
}
