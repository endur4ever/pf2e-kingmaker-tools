package at.posselt.pfrpg2e.data.kingdom

import com.foundryvtt.kingmaker.HexState
import kotlinx.js.JsPlainObject
import js.objects.ReadonlyRecord
import js.objects.Record

const val ROAD_FEATURE_TYPE = "road"

/**
 * Pure detection logic for the "Connect a settlement to your capital via roads" milestone.
 * Returns true if there exists a path of road-bearing hexes connecting the settlement hex
 * to the capital hex (including the endpoints).
 *
 * [settlementHex] - the hex key of the settlement to check
 * [capitalHex] - the hex key of the capital
 * [roadHexSet] - precomputed set of hex keys that have a road feature
 * [neighborsProvider] - function that returns neighboring hex keys for a given hex key
 */
fun roadConnectedToCapital(
    settlementHex: String,
    capitalHex: String,
    roadHexSet: Set<String>,
    neighborsProvider: (String) -> Set<String>,
): Boolean {
    if (settlementHex == capitalHex) return true

    // Both endpoints must have roads for a valid connection
    if (settlementHex !in roadHexSet || capitalHex !in roadHexSet) return false

    // BFS from settlement hex over road-bearing hexes
    val visited = mutableSetOf<String>()
    val queue = mutableListOf(settlementHex)
    visited.add(settlementHex)

    while (queue.isNotEmpty()) {
        val current = queue.removeAt(0)
        if (current == capitalHex) return true

        val neighbors = neighborsProvider(current)
        for (neighbor in neighbors) {
            if (neighbor in visited) continue
            if (neighbor !in roadHexSet) continue
            visited.add(neighbor)
            queue.add(neighbor)
        }
    }

    return false
}

/**
 * Pure detection logic for the "Claim all hexes in a region" milestone.
 * Returns true if every hex in the given region is claimed by the kingdom.
 *
 * [regionHexKeys] - precomputed set of hex keys belonging to the region
 * [hexStates] - the native Kingmaker hex state map (kingmaker.state.hexes)
 */
fun regionFullyClaimed(
    regionHexKeys: Set<String>,
    hexStates: ReadonlyRecord<String, HexState>,
): Boolean {
    if (regionHexKeys.isEmpty()) return false

    // Check if all hexes in the region are claimed
    return regionHexKeys.all { hexKey ->
        val state = hexStates[hexKey]
        state?.claimed == true
    }
}

/**
 * Helper to get all hexes that have a road feature (pure, testable).
 */
fun getRoadHexKeys(hexStates: ReadonlyRecord<String, HexState>): Set<String> {
    val roadHexes = mutableSetOf<String>()
    for (entry in js("Object.entries(hexStates)").unsafeCast<Array<Array<Any>>>()) {
        val key = entry[0] as String
        val state = entry[1] as HexState
        val hasRoad = state.features?.any { it.type == ROAD_FEATURE_TYPE } == true
        if (hasRoad) roadHexes.add(key)
    }
    return roadHexes
}

/**
 * Data structure to hold region information for pure detection.
 * Passed from JS side where kingmaker.region.hexes is available.
 */
@JsPlainObject
external interface RegionData {
    val regionHexKeys: Record<String, Set<String>>
    val capitalHexKey: String?
    val settlementHexKeys: Record<String, String>
}

/**
 * Extracts region data from the live Kingmaker module (JS side only).
 * This is called from JS main where kingmaker.region is available,
 * and the result is passed to the pure detection functions.
 */
external fun extractRegionData(): RegionData