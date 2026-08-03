package at.posselt.pfrpg2e.camping.routing

import at.posselt.pfrpg2e.data.hex.HexContent
import at.posselt.pfrpg2e.data.regions.Terrain

/**
 * Provider interface for travel routing. Pure Kotlin — no Foundry dependencies.
 * Implementations in jsMain connect to Foundry's hex grid; test implementations use fakes.
 */
interface TravelProvider {

    /**
     * Returns adjacent hex keys for the given hex.
     * Used by Dijkstra to traverse the graph.
     */
    fun getAdjacentHexKeys(hexKey: String): List<String>

    /**
     * Returns hex content for the given hex key.
     * Used for travelModifier from HexContent.
     */
    fun getContentForHex(hexKey: String): List<HexContent>

    /**
     * Returns the terrain type for the given hex key.
     * Used for terrainModifiers lookup.
     */
    fun getTerrainForHex(hexKey: String): Terrain?

    /**
     * Returns feature strings for the given hex key (e.g., "road", "river", "bridge").
     * Used for infrastructureModifiers lookup.
     */
    fun getFeaturesForHex(hexKey: String): List<String>
}