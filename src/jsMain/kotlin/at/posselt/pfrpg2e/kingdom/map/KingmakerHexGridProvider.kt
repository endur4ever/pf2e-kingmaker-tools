package at.posselt.pfrpg2e.kingdom.map

import at.posselt.pfrpg2e.camping.HexGridProvider
import at.posselt.pfrpg2e.data.hex.HexContent
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

    // Distance-only routing for v1: no per-hex content modifiers yet.
    override fun getContentForHex(hexKey: String): List<HexContent> = emptyList()
}
