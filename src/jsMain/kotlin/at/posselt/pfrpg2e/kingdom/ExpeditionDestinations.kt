package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.camping.routing.TravelProvider
import at.posselt.pfrpg2e.kingdom.map.KingmakerHexGridProvider
import at.posselt.pfrpg2e.companion.expeditionTravelDays
import at.posselt.pfrpg2e.companion.formatHexKeyLabel
import at.posselt.pfrpg2e.companion.hexCubeDistance
import at.posselt.pfrpg2e.utils.asSequence
import com.foundryvtt.core.game
import com.foundryvtt.kingmaker.kingmaker
import com.foundryvtt.kingmaker.isExplored
import js.array.component1
import js.array.component2
import kotlinx.js.JsPlainObject
import kotlin.math.ceil

/**
 * Destination choices for the expedition launch form, plus the origin hexes the
 * travel-time math measures from. Built once at each launch call site (the dialog
 * itself has no kingdom access) and resolved against the native pf2e-kingmaker
 * region model, so every access is wrapped defensively — the module may be absent.
 */

@JsPlainObject
external interface ExpeditionDestinationOption {
    val key: String
    val label: String
}

class ExpeditionDestinationOptions(
    val settlements: Array<ExpeditionDestinationOption>,
    val hexes: Array<ExpeditionDestinationOption>,
    val hubs: Array<ExpeditionDestinationOption>,
    /** Settlement hexes: travel time is measured from the NEAREST of these. */
    val originHexKeys: Array<String>,
    /** Explored-but-unclaimed hexes — the frontier the party already knows. */
    val explored: Array<ExpeditionDestinationOption> = emptyArray(),
    /** Every remaining map hex — expeditions (especially scouting) go where nobody has. */
    val uncharted: Array<ExpeditionDestinationOption> = emptyArray(),
) {
    fun isEmpty(): Boolean =
        settlements.isEmpty() && hexes.isEmpty() && hubs.isEmpty() && explored.isEmpty() && uncharted.isEmpty()

    fun labelFor(key: String): String? =
        (settlements.asSequence() + hexes.asSequence() + hubs.asSequence() +
            explored.asSequence() + uncharted.asSequence())
            .find { it.key == key }?.label

    companion object {
        fun empty() = ExpeditionDestinationOptions(emptyArray(), emptyArray(), emptyArray(), emptyArray())
    }
}

/**
 * Assemble the grouped destination dropdown: named settlements (scene name), faction
 * trade hubs (RawGroup.hexKey — the caravan-partner precedent), and remaining claimed
 * hexes labeled by native hex name when the region knows one, else "i.j" coordinates.
 */
fun buildExpeditionDestinationOptions(kingdom: KingdomData): ExpeditionDestinationOptions {
    val settlementOptions = kingdom.settlements
        .filter { !it.hexKey.isNullOrBlank() }
        .mapNotNull { raw ->
            game.scenes.get(raw.sceneId)?.name
                ?.takeIf { it.isNotBlank() }
                ?.let { name -> ExpeditionDestinationOption(key = raw.hexKey!!, label = name) }
        }
        .toTypedArray()

    val hubOptions = kingdom.groups
        .filter { !it.hexKey.isNullOrBlank() }
        .map { ExpeditionDestinationOption(key = it.hexKey!!, label = it.name) }
        .toTypedArray()

    val taken = (settlementOptions.map { it.key } + hubOptions.map { it.key }).toSet()

    fun stateHexes(predicate: (com.foundryvtt.kingmaker.HexState) -> Boolean) = runCatching {
        kingmaker.state.hexes.asSequence()
            .filter { (_, hex) -> predicate(hex) }
            .mapNotNull { (key, _) ->
                if (key in taken) return@mapNotNull null
                val intKey = key.toIntOrNull() ?: return@mapNotNull null
                val coord = formatHexKeyLabel(key) ?: return@mapNotNull null
                val name = runCatching {
                    kingmaker.region.hexes.find { it.key == intKey }?.name
                }.getOrNull()
                val label = if (!name.isNullOrBlank() && name != coord) "$name ($coord)" else coord
                ExpeditionDestinationOption(key = key, label = label)
            }
            .sortedBy { it.label }
            .toList()
    }.getOrDefault(emptyList()).toTypedArray()

    val hexOptions = stateHexes { it.claimed == true }
    val exploredOptions = stateHexes { it.claimed != true && it.isExplored() }

    // Everything else on the map: expeditions — scouting above all — go where nobody has.
    // The kingmaker state record is sparse (only hexes with any state), so the full map
    // comes from the region model; exclude anything already listed above.
    val listed = taken + hexOptions.map { it.key } + exploredOptions.map { it.key }
    val unchartedOptions = runCatching {
        kingmaker.region.hexes.contents
            .mapNotNull { hex ->
                val key = hex.key.toString()
                if (key in listed) return@mapNotNull null
                val coord = formatHexKeyLabel(key) ?: return@mapNotNull null
                val name = hex.name
                val label = if (name.isNotBlank() && name != coord) "$name ($coord)" else coord
                ExpeditionDestinationOption(key = key, label = label)
            }
            .sortedBy { it.label }
            .toList()
    }.getOrDefault(emptyList()).toTypedArray()

    return ExpeditionDestinationOptions(
        settlements = settlementOptions,
        hexes = hexOptions,
        hubs = hubOptions,
        originHexKeys = settlementOptions.map { it.key }.toTypedArray(),
        explored = exploredOptions,
        uncharted = unchartedOptions,
    )
}

/**
 * Minimum hex distance from any origin to [destinationHexKey], resolved through the
 * native region grid's cube coordinates. Null when the region/keys can't resolve —
 * callers treat that as zero travel (tier-flat duration, the pre-destination behavior).
 */
fun expeditionHexDistance(originHexKeys: Array<String>, destinationHexKey: String): Int? = runCatching {
    val destKey = destinationHexKey.toIntOrNull() ?: return@runCatching null
    val hexes = kingmaker.region.hexes
    val dest = hexes.find { it.key == destKey }?.cube ?: return@runCatching null
    originHexKeys
        .mapNotNull { origin -> origin.toIntOrNull()?.let { ok -> hexes.find { it.key == ok }?.cube } }
        .minOfOrNull { c -> hexCubeDistance(c.q, c.r, c.s, dest.q, dest.r, dest.s) }
}.getOrNull()

/**
 * Weighted cost of the cheapest ACTUAL route from any origin to [destinationHexKey], or null when
 * no route resolves (no region data, unreachable destination, unparseable keys).
 *
 * Routed through the same [KingmakerHexGridProvider] and cost model kingdom caravans use, so the
 * roads a kingdom spends RP building shorten companion expeditions too, and trackless swamp costs
 * what it should. Straight-line distance cannot see any of that.
 */
fun expeditionRouteCost(
    originHexKeys: Array<String>,
    destinationHexKey: String,
    provider: TravelProvider = KingmakerHexGridProvider(),
): Double? {
    return originHexKeys
        .mapNotNull { origin -> computeCaravanRoute(provider, origin, destinationHexKey)?.totalCost }
        .filter { it.isFinite() }
        .minOrNull()
}

/**
 * One-way travel days from the nearest origin to [destinationHexKey]; 0 when unresolvable.
 *
 * Prefers the routed cost so terrain, roads, rivers and bridges all move the estimate. Falls back
 * to straight-line hex distance when no route resolves, which keeps the previous behaviour for
 * worlds without region data rather than silently reporting a free trip.
 */
fun expeditionTravelDaysTo(
    originHexKeys: Array<String>,
    destinationHexKey: String?,
    provider: TravelProvider = KingmakerHexGridProvider(),
): Int {
    if (destinationHexKey == null) return 0
    val routed = expeditionRouteCost(originHexKeys, destinationHexKey, provider)
    if (routed != null) return expeditionTravelDays(ceil(routed).toInt())
    val distance = expeditionHexDistance(originHexKeys, destinationHexKey) ?: return 0
    return expeditionTravelDays(distance)
}
