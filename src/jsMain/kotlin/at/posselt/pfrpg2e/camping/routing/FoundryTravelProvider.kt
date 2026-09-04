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
            // the HEX's own terrain first: a hex may override its zone's default, and pricing the
            // zone's terrain misprices every hex that does
            val terrainName = hexObj?.terrain?.id?.takeIf { it.isNotBlank() } ?: hexObj?.zone?.terrain
            terrainName?.let { kingmakerTerrain(it) }
        }.getOrNull()
    }

    override fun getFeaturesForHex(hexKey: String): List<String> {
        return runCatching {
            val hexState = kingmaker.state.hexes[hexKey]
            hexState?.features?.mapNotNull { it.type } ?: emptyList()
        }.getOrDefault(emptyList())
    }
}

/**
 * Map a pf2e-kingmaker TERRAIN id onto this module's [Terrain].
 *
 * NOT fromCamelCase: the served TERRAIN table (verified 2026-09-04) uses plains, forest, hills,
 * mountains, wetlands, swamp and lake, while the enum spells MOUNTAIN, has no WETLANDS and no LAKE.
 * "mountains" therefore resolved to null and every mountain hex priced as OPEN terrain -- one
 * Travel activity to cross a mountain range. An unrecognised id still returns null, which the cost
 * model reads as open ground, but the seven ids the module actually ships are now covered.
 */
internal fun kingmakerTerrain(id: String): Terrain? = when (id.trim().lowercase()) {
    "plains" -> Terrain.PLAINS
    "forest" -> Terrain.FOREST
    "hills" -> Terrain.HILLS
    "mountains", "mountain" -> Terrain.MOUNTAIN
    "swamp", "wetlands" -> Terrain.SWAMP
    "lake", "aquatic", "water" -> Terrain.AQUATIC
    "desert" -> Terrain.DESERT
    "urban" -> Terrain.URBAN
    "dungeon" -> Terrain.DUNGEON
    else -> null
}
