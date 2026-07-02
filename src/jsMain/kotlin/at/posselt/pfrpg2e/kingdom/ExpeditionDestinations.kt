package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.companion.expeditionTravelDays
import at.posselt.pfrpg2e.companion.formatHexKeyLabel
import at.posselt.pfrpg2e.companion.hexCubeDistance
import at.posselt.pfrpg2e.utils.asSequence
import com.foundryvtt.core.game
import com.foundryvtt.kingmaker.kingmaker
import js.array.component1
import js.array.component2
import kotlinx.js.JsPlainObject

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
) {
    fun isEmpty(): Boolean = settlements.isEmpty() && hexes.isEmpty() && hubs.isEmpty()

    fun labelFor(key: String): String? =
        (settlements.asSequence() + hexes.asSequence() + hubs.asSequence())
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
    val hexOptions = runCatching {
        kingmaker.state.hexes.asSequence()
            .filter { (_, hex) -> hex.claimed == true }
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

    return ExpeditionDestinationOptions(
        settlements = settlementOptions,
        hexes = hexOptions,
        hubs = hubOptions,
        originHexKeys = settlementOptions.map { it.key }.toTypedArray(),
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

/** One-way travel days from the nearest origin to [destinationHexKey]; 0 when unresolvable. */
fun expeditionTravelDaysTo(originHexKeys: Array<String>, destinationHexKey: String?): Int {
    if (destinationHexKey == null) return 0
    val distance = expeditionHexDistance(originHexKeys, destinationHexKey) ?: return 0
    return expeditionTravelDays(distance)
}
