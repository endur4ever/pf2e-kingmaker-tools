package at.posselt.pfrpg2e.kingdom.map

import at.posselt.pfrpg2e.camping.routing.TravelProvider
import at.posselt.pfrpg2e.data.hex.HexContent
import at.posselt.pfrpg2e.data.hex.HexContentType
import at.posselt.pfrpg2e.data.hex.HexContentVisibility
import at.posselt.pfrpg2e.data.regions.Terrain
import at.posselt.pfrpg2e.fromCamelCase
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.RawHexContent
import at.posselt.pfrpg2e.kingdom.getKingdomActors
import at.posselt.pfrpg2e.kingdom.getKingdom
import com.foundryvtt.core.game
import com.foundryvtt.kingmaker.KingmakerHex
import at.posselt.pfrpg2e.camping.routing.kingmakerTerrain
import com.foundryvtt.kingmaker.kingmaker
import kotlin.js.unsafeCast
import kotlin.runCatching

/**
 * [TravelProvider] backed by the live Kingmaker region grid. Adjacency uses Foundry's own hex math
 * (`GridHex.getNeighbors()`), filtered to hexes that actually exist in the region so caravans stay
 * on the known map. Content, terrain, and features are read from the kingdom actor and the native
 * pf2e-kingmaker module's state/region models.
 *
 * Foundry-runtime coupled — verified in Foundry, not in unit tests. The pure routing/ETA logic in
 * `CaravanRouting.kt` is tested against a fake provider instead.
 */
class KingmakerHexGridProvider : TravelProvider {

    private val regionKeys: Set<Int> by lazy {
        runCatching { kingmaker.region.hexes.contents.map { it.key }.toSet() }.getOrDefault(emptySet())
    }

    override fun getAdjacentHexKeys(hexKey: String): List<String> {
        val key = hexKey.toIntOrNull() ?: return emptyList()
        val hex = runCatching { kingmaker.region.hexes.find { it.key == key } }.getOrNull() ?: return emptyList()
        return hex.getNeighbors()
            .mapNotNull { neighbor ->
                // getNeighbors() is typed as base GridHex, but at runtime each neighbor is a KingmakerHex
                // that already carries its region `key`. Read it directly. The previous approach recomputed
                // the key via KingmakerHex.getKey(neighbor.offset); that throws at runtime and — being
                // swallowed by runCatching — silently dropped every neighbor, leaving adjacency empty so
                // every caravan/shipment route resolved as "unreachable".
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
            // the hex's own terrain overrides its zone's default; and the ids are Kingmaker's
            // ("mountains", "wetlands", "lake"), which fromCamelCase can never match
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
