package at.posselt.pfrpg2e.kingdom.map

import at.posselt.pfrpg2e.data.hex.HexContent
import at.posselt.pfrpg2e.data.hex.HexContentVisibility
import at.posselt.pfrpg2e.data.hex.HexContentType

enum class DiscoveryEvent {
    DISCOVER,
    CLEAR,
    RESET;
}

fun nextVisibility(current: HexContentVisibility, event: DiscoveryEvent): HexContentVisibility =
    when (event) {
        DiscoveryEvent.DISCOVER -> when (current) {
            HexContentVisibility.HIDDEN -> HexContentVisibility.DISCOVERED
            else -> current // already discovered or cleared — idempotent
        }
        DiscoveryEvent.CLEAR -> when (current) {
            HexContentVisibility.HIDDEN,
            HexContentVisibility.DISCOVERED -> HexContentVisibility.CLEARED
            else -> current // already cleared — idempotent
        }
        DiscoveryEvent.RESET -> HexContentVisibility.HIDDEN
    }


fun aggregateTravelModifiers(featureTypes: List<String?>, contents: List<HexContent>): Int {
    var total = 0
    // Native road feature reduces travel cost
    if (featureTypes.any { it == ROAD_FEATURE_TYPE }) {
        total -= 1
    }
    // Content-specific travel modifiers
    contents.forEach { content ->
        content.travelModifier?.let { total += it }
    }
    return total
}

/**
 * Priority ordering for the single composite marker per hex.
 * Higher-priority content types win when a hex has multiple contents.
 * Only non-hidden content is eligible for display.
 */
private val CONTENT_TYPE_PRIORITY = listOf(
    HexContentType.ENEMY_ARMY,
    HexContentType.RUIN,
    HexContentType.MERCHANT,
    HexContentType.TRAINER,
    HexContentType.REFUGE,
    HexContentType.WORKSITE,
    HexContentType.RESOURCE,
    HexContentType.LANDMARK,
    HexContentType.CUSTOM,
)

data class MarkerSpec(
    val icon: String,
    val label: String,
    val tint: String,
    val priority: Int,
)

fun contentMarkerFor(
    claimed: Boolean,
    cleared: Boolean,
    contents: List<HexContent>,
    visibilityFilter: (HexContent) -> Boolean = { true },
): MarkerSpec? {
    val visibleContents = contents.filter(visibilityFilter)
    if (visibleContents.isEmpty()) return null

    // Pick the highest-priority content for the composite marker (lower index = higher priority)
    val best = visibleContents.minByOrNull { content ->
        val idx = CONTENT_TYPE_PRIORITY.indexOf(content.type)
        if (idx >= 0) idx else CONTENT_TYPE_PRIORITY.size
    } ?: return null

    val contentType = best.type
    val priority = CONTENT_TYPE_PRIORITY.indexOf(contentType).let { if (it >= 0) it else CONTENT_TYPE_PRIORITY.size }

    val icon = when (contentType) {
        HexContentType.LANDMARK -> "fa-solid fa-monument"
        HexContentType.REFUGE -> "fa-solid fa-house-chimney"
        HexContentType.WORKSITE -> "fa-solid fa-hammer"
        HexContentType.RESOURCE -> "fa-solid fa-gem"
        HexContentType.RUIN -> "fa-solid fa-ruin"
        HexContentType.MERCHANT -> "fa-solid fa-store"
        HexContentType.TRAINER -> "fa-solid fa-graduation-cap"
        HexContentType.ENEMY_ARMY -> "fa-solid fa-skull-crossbones"
        HexContentType.CUSTOM -> (best.icon ?: "fa-solid fa-circle-question")
    }

    val tint = when (contentType) {
        HexContentType.ENEMY_ARMY -> "#cc0000"
        HexContentType.RUIN -> "#8b4513"
        HexContentType.MERCHANT -> "#ffd700"
        HexContentType.TRAINER -> "#4169e1"
        HexContentType.REFUGE -> "#228b22"
        HexContentType.WORKSITE -> "#808080"
        HexContentType.RESOURCE -> "#9932cc"
        HexContentType.LANDMARK -> "#1e90ff"
        HexContentType.CUSTOM -> "#aaaaaa"
    }

    return MarkerSpec(
        icon = icon,
        label = best.name,
        tint = tint,
        priority = priority,
    )
}
