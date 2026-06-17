package at.posselt.pfrpg2e.kingdom.map

import at.posselt.pfrpg2e.camping.HexGridProvider
import at.posselt.pfrpg2e.data.hex.HexContent
import com.foundryvtt.kingmaker.HexOffsetCoordinate
import com.foundryvtt.kingmaker.KingmakerHex
import com.foundryvtt.kingmaker.kingmaker

/**
 * [HexGridProvider] backed by the live Kingmaker region grid. Adjacency uses Foundry's own hex math
 * (`GridHex.getNeighbors()`), filtered to hexes that actually exist in the region so caravans stay
 * on the known map. Content lookup is empty for now (distance-only ETA); road/river/terrain cost
 * modifiers are a later refinement.
 *
 * Foundry-runtime coupled — verified in Foundry, not in unit tests. The pure routing/ETA logic in
 * `CaravanRouting.kt` is tested against a fake provider instead.
 */
class KingmakerHexGridProvider : HexGridProvider {
    private val regionKeys: Set<Int> by lazy {
        runCatching { kingmaker.region.hexes.contents.map { it.key }.toSet() }.getOrDefault(emptySet())
    }

    override fun getAdjacentHexKeys(hexKey: String): List<String> {
        val key = hexKey.toIntOrNull() ?: return emptyList()
        val hex = runCatching { kingmaker.region.hexes.find { it.key == key } }.getOrNull() ?: return emptyList()
        return hex.getNeighbors()
            .mapNotNull { neighbor ->
                runCatching { KingmakerHex.getKey(neighbor.offset.unsafeCast<HexOffsetCoordinate>()) }.getOrNull()
            }
            .filter { it in regionKeys }
            .map { it.toString() }
    }

    // Distance-only routing for v1: no per-hex content modifiers yet.
    override fun getContentForHex(hexKey: String): List<HexContent> = emptyList()
}
