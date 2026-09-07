package at.posselt.pfrpg2e.kingdom.map

import at.posselt.pfrpg2e.data.hex.HexContentVisibility

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

