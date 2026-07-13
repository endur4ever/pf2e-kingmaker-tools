package at.posselt.pfrpg2e.camping.routing

import at.posselt.pfrpg2e.data.hex.HexContent
import at.posselt.pfrpg2e.data.hex.HexContentType
import at.posselt.pfrpg2e.data.hex.HexContentVisibility
import at.posselt.pfrpg2e.data.regions.Terrain
import at.posselt.pfrpg2e.fromCamelCase
import at.posselt.pfrpg2e.kingdom.data.RawHexContent
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.getKingdomActors
import at.posselt.pfrpg2e.kingdom.getKingdom
import com.foundryvtt.core.game
import com.foundryvtt.kingmaker.KingmakerHex
import com.foundryvtt.kingmaker.kingmaker
import kotlin.js.unsafeCast
import kotlin.runCatching

/**
 * Foundry-backed [TravelProvider] that reads hex topology and content from the live
 * Kingmaker region grid and kingdom state. Used in production (JS target).
 */
class FoundryTravelProvider : TravelProvider {

    override fun getAdjacentHexKeys(hexKey: String): List<String> {
        val key = hexKey.toIntOrNull() ?: return emptyList()
        val hex = runCatching { kingmaker.region.hexes.find { it.key == key } }.getOrNull() ?: return emptyList()
        val regionKeys = kingmaker.region.hexes.contents.map { it.key }.toSet()

        return hex.getNeighbors()
            .mapNotNull { neighbor ->
                runCatching { neighbor.unsafeCast<KingmakerHex>().key }.getOrNull()
            }
            .filter { it in regionKeys }
            .map { it.toString() }
    }

    override fun getContentForHex(hexKey: String): List<HexContent> {
        return runCatching {
            // Get kingdom hex contents (RawHexContent) from the current kingdom actor
            val kingdomActor = game.getKingdomActors().firstOrNull() ?: return@runCatching emptyList()
            val kingdom = kingdomActor.getKingdom() ?: return@runCatching emptyList()

            val hexContents: Array<RawHexContent>? = kingdom.hexContents
            if (hexContents == null) return@runCatching emptyList()

            hexContents
                .filter { it.hexKey == hexKey }
                .map { raw ->
                    HexContent(
                        id = raw.id,
                        hexKey = raw.hexKey,
                        type = fromCamelCase<HexContentType>(raw.type) ?: HexContentType.CUSTOM,
                        name = raw.name,
                        visibility = fromCamelCase<HexContentVisibility>(raw.visibility) ?: HexContentVisibility.DISCOVERED,
                        gmNotes = raw.gmNotes,
                        playerText = raw.playerText,
                        suppressesEncounters = raw.suppressesEncounters,
                        travelModifier = raw.travelModifier,
                        linkedQuestId = raw.linkedQuestId,
                        linkedUuid = raw.linkedUuid,
                        linkedWarThreatId = raw.linkedWarThreatId,
                        icon = raw.icon
                    )
                }
        }.getOrDefault(emptyList())
    }

    override fun getTerrainForHex(hexKey: String): Terrain? {
        return runCatching {
            val hexObj = kingmaker.region.hexes.find { it.key.toString() == hexKey }
            val terrainName = hexObj?.zone?.terrain
            terrainName?.let { fromCamelCase<Terrain>(it) }
        }.getOrNull()
    }

    override fun getFeaturesForHex(hexKey: String): List<String> {
        return runCatching {
            val hexState = kingmaker.state.hexes[hexKey]
            hexState?.features?.mapNotNull { it.type } ?: emptyList()
        }.getOrDefault(emptyList())
    }
}