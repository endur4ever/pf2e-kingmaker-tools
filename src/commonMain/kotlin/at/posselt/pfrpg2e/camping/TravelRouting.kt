package at.posselt.pfrpg2e.camping

/**
 * A single, pure travel-routing core: one Dijkstra plus one edge-cost function, abstracted over an
 * opaque hex key so it can be unit-tested against synthetic grids.
 *
 * Today the module computes travel cost three different ways with drifting semantics —
 * `TravelRouteService` (kingdom caravan routing), `TravelService` (camping sheet), and a private
 * Dijkstra inside `CampingSheet`. This is the convergent core those three should all call; the
 * Foundry-specific hex lookups (which hex carries a road/river, the party's speed, the weather)
 * stay in jsMain providers that assemble the [TravelGraph]/[TravelEdgeInputs] handed in here.
 *
 * Behavior-preservation note (card t_baf2ea3f is a refactor): the road/river infrastructure
 * multipliers default to identity (no effect), matching the current stubbed behavior where roads do
 * nothing. The sibling roads-ETA card fills those multipliers in without touching this signature.
 */

/**
 * The cost inputs for stepping across one hex edge. Every field is a multiplier relative to
 * [baseCost] and composes multiplicatively, so a field left at its default `1.0` has no effect.
 */
data class TravelEdgeInputs(
    val baseCost: Double = 1.0,
    /** Difficulty of the destination terrain (e.g. forest/mountain > plains). */
    val terrainMultiplier: Double = 1.0,
    /** Current weather penalty (e.g. storms slow travel). */
    val weatherMultiplier: Double = 1.0,
    /** `< 1.0` makes the edge cheaper (a road); `1.0` = no road. */
    val roadMultiplier: Double = 1.0,
    /** `> 1.0` makes the edge costlier (an unbridged river crossing); `1.0` = none. */
    val riverMultiplier: Double = 1.0,
    /** `> 1.0` for a slow party, `< 1.0` for a fast one; scales every edge equally. */
    val partySpeedFactor: Double = 1.0,
)

/** The single explicit edge-cost function. Non-negative; the multipliers compose multiplicatively. */
fun travelEdgeCost(inputs: TravelEdgeInputs): Double =
    (inputs.baseCost *
        inputs.terrainMultiplier *
        inputs.weatherMultiplier *
        inputs.roadMultiplier *
        inputs.riverMultiplier *
        inputs.partySpeedFactor).coerceAtLeast(0.0)

/** An abstract weighted graph of hexes: the neighbors of a hex and the cost of each edge. */
interface TravelGraph {
    fun neighbors(hex: String): List<String>

    /** Cost to step from [from] to an adjacent [to]; must be finite and `>= 0`. */
    fun edgeCost(from: String, to: String): Double
}

/** A resolved route: the ordered hex keys `start..goal` and the summed edge cost. */
data class TravelPath(
    val hexes: List<String>,
    val totalCost: Double,
)

/**
 * Least-cost path from [start] to [goal] over [graph] via Dijkstra, or `null` if [goal] is
 * unreachable. A `start == goal` request yields the trivial zero-cost single-hex path. Negative edge
 * costs are ignored (skipped), keeping the shortest-path guarantee intact. Deterministic: the
 * frontier is scanned in insertion order so identical grids always yield identical routes.
 *
 * Uses an O(V^2) frontier scan rather than a binary heap — hex neighbourhoods are small and this
 * keeps the core dependency-free in commonMain.
 */
fun shortestTravelPath(graph: TravelGraph, start: String, goal: String): TravelPath? {
    if (start == goal) return TravelPath(listOf(start), 0.0)
    val dist = linkedMapOf(start to 0.0)
    val prev = mutableMapOf<String, String>()
    val visited = mutableSetOf<String>()
    while (true) {
        val current = dist.entries
            .filter { it.key !in visited }
            .minByOrNull { it.value }
            ?.key ?: break
        if (current == goal) break
        visited += current
        val currentDist = dist.getValue(current)
        for (next in graph.neighbors(current)) {
            if (next in visited) continue
            val step = graph.edgeCost(current, next)
            if (step < 0.0) continue
            val candidate = currentDist + step
            if (candidate < (dist[next] ?: Double.POSITIVE_INFINITY)) {
                dist[next] = candidate
                prev[next] = current
            }
        }
    }
    val total = dist[goal] ?: return null
    val path = ArrayDeque<String>()
    var node: String? = goal
    while (node != null) {
        path.addFirst(node)
        node = prev[node]
    }
    return TravelPath(path.toList(), total)
}
