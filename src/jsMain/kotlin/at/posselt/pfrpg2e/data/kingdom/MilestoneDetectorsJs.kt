package at.posselt.pfrpg2e.data.kingdom

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.getAllSettlements
import at.posselt.pfrpg2e.data.kingdom.settlements.SettlementType
import com.foundryvtt.kingmaker.KingmakerHex
import com.foundryvtt.kingmaker.kingmaker
import js.objects.recordOf

/**
 * Extracts region data from the live Kingmaker module for use in pure milestone detection.
 * Returns a RegionData object with:
 * - regionHexKeys: map of regionId -> set of hex keys in that region
 * - capitalHexKey: the hex key of the capital settlement (if found)
 * - settlementHexKeys: map of settlementId -> hexKey for all settlements
 */
fun extractRegionData(kingdom: KingdomData): RegionData {
    val regionHexKeys = mutableMapOf<String, MutableSet<String>>()
    var capitalHexKey: String? = null
    val settlementHexKeys = mutableMapOf<String, String>()

    // Iterate over all hexes in the Kingmaker region
    for (hex in kingmaker.region.hexes.contents) {
        val hexKey = hex.key.toString()
        val zoneId = hex.zone?.id
        if (zoneId != null && zoneId.isNotBlank()) {
            val set = regionHexKeys.getOrPut(zoneId) { mutableSetOf() }
            set.add(hexKey)
        }
    }

    // Get settlements from kingdom data to find capital and settlement hex keys
    // Note: We can't call getAllSettlements here because it requires a Game instance
    // Instead, we extract from kingdom.settlements directly
    for (rawSettlement in kingdom.settlements) {
        val hexKey = rawSettlement.hexKey
        if (hexKey != null && hexKey.isNotBlank()) {
            settlementHexKeys[rawSettlement.sceneId] = hexKey
            if (rawSettlement.type == SettlementType.CAPITAL.value) {
                capitalHexKey = hexKey
            }
        }
    }

    // Convert to the external interface format
    val regionMap = recordOf<String, Any>()
    for ((regionId, keys) in regionHexKeys) {
        regionMap[regionId] = setOf(*keys.toTypedArray()).asDynamic()
    }

    val settlementMap = recordOf<String, String>()
    for ((settlementId, hexKey) in settlementHexKeys) {
        settlementMap[settlementId] = hexKey
    }

    val result = js("{}")
    result.regionHexKeys = regionMap
    result.capitalHexKey = capitalHexKey?.toString() ?: null
    result.settlementHexKeys = settlementMap
    return result.unsafeCast<RegionData>()
}