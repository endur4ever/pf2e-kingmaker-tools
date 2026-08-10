package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.data.regions.Terrain

/**
 * Hexploration terrain difficulty, which is what the Travel activity is priced in.
 *
 * RAW (Hexploration, Archives of Nethys): "In open terrain, using 1 Travel activity allows you to
 * move from one hex to an adjacent hex. Traversing a hex with difficult terrain requires 2 Travel
 * activities, and hexes of greater difficult terrain require 3 Travel activities to traverse."
 */
enum class TerrainDifficulty(val travelActivities: Int) {
    OPEN(1),
    DIFFICULT(2),
    GREATER_DIFFICULT(3),
}

/**
 * The Travel activity cost of the module's house-rule ceiling. `docs/house-rules.md`:
 * "Increase the Travel activity cost by 1 degree (up to a maximum of 3)".
 */
const val MAX_TRAVEL_ACTIVITIES = 3

/**
 * Terrain difficulty per the hexploration rules: plains are open, forest/hills/desert are
 * difficult, swamp and mountains are greater difficult. Urban and dungeon hexes are treated as
 * open, and aquatic as difficult — RAW makes water open when travelling DOWNriver and difficult
 * or greater difficult upriver, and the module has no travel direction to distinguish them.
 */
fun terrainDifficulty(terrain: Terrain?): TerrainDifficulty = when (terrain) {
    Terrain.PLAINS, Terrain.URBAN, Terrain.DUNGEON, null -> TerrainDifficulty.OPEN
    Terrain.FOREST, Terrain.HILLS, Terrain.DESERT, Terrain.AQUATIC -> TerrainDifficulty.DIFFICULT
    Terrain.MOUNTAIN, Terrain.SWAMP -> TerrainDifficulty.GREATER_DIFFICULT
}

/**
 * Travel activities needed to ENTER one hex, in the range 1..[MAX_TRAVEL_ACTIVITIES].
 *
 * - Base cost is the destination terrain's difficulty (1 / 2 / 3).
 * - A road makes it "one step better than the surrounding terrain" (RAW), i.e. one activity less,
 *   never below open.
 * - [riverExtraDegrees] is added when the hex has a river and no bridge. RAW says nothing about
 *   CROSSING water — only about travelling along it — so this is the module's optional house rule,
 *   configured by the `travelCostRiverNoBridgeAdditional` setting and 0 (i.e. RAW) by default.
 * - [pavedSettlement] applies `docs/house-rules.md`: "Hexes containing a settlement reduce their
 *   Travel cost to 1 if you've constructed Paved Streets". It overrides everything else, since a
 *   paved settlement is passable regardless of the terrain it was built on. Gated by the
 *   `pavedStreetsReduceTravelCost` setting, off by default.
 * - [extraDegrees] carries per-hex content modifiers (a hazard, a landmark) in the same units.
 *
 * The house-rule ceiling of 3 applies to the total, so a swamp with a river penalty costs 3 rather
 * than 4 — nothing capped it before.
 */
fun travelActivityCost(
    difficulty: TerrainDifficulty,
    hasRoad: Boolean = false,
    riverExtraDegrees: Int = 0,
    extraDegrees: Int = 0,
    pavedSettlement: Boolean = false,
): Int {
    if (pavedSettlement) return TerrainDifficulty.OPEN.travelActivities
    val afterRoad = if (hasRoad) difficulty.travelActivities - 1 else difficulty.travelActivities
    val total = afterRoad + extraDegrees + riverExtraDegrees
    return total.coerceIn(TerrainDifficulty.OPEN.travelActivities, MAX_TRAVEL_ACTIVITIES)
}
