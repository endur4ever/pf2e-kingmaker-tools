package at.posselt.pfrpg2e.companion

/**
 * Companion influence is grounded in the PF2e Influence subsystem, which the Kingmaker
 * Companion Guide uses to befriend companions. Influence is measured in Influence Points
 * against small thresholds — NOT a 0-100 bar.
 *
 * Companion Guide thresholds: 1 / 2 / 4 / 6 / 8 / 12. The maximum threshold (12) is unique to
 * Nok-Nok; most primary companions top out at 8, Octavia at 6. We cap stored influence at 12.
 */
const val MAX_COMPANION_INFLUENCE: Int = 12

const val DISCOVERY_UNKNOWN = "unknown"
const val DISCOVERY_INTRODUCED = "introduced"
const val DISCOVERY_ESTABLISHED = "established"
const val DISCOVERY_TRUSTED = "trusted"
const val DISCOVERY_BONDED = "bonded"

/** Ordered discovery/relationship stages, from not-yet-engaged to fully bonded. */
val companionDiscoveryStages: List<String> = listOf(
    DISCOVERY_UNKNOWN,
    DISCOVERY_INTRODUCED,
    DISCOVERY_ESTABLISHED,
    DISCOVERY_TRUSTED,
    DISCOVERY_BONDED,
)

/** Influence-point threshold each discovery stage corresponds to in the subsystem. */
val companionDiscoveryThresholds: Map<String, Int> = mapOf(
    DISCOVERY_UNKNOWN to 0,
    DISCOVERY_INTRODUCED to 2,
    DISCOVERY_ESTABLISHED to 4,
    DISCOVERY_TRUSTED to 6,
    DISCOVERY_BONDED to 8,
)

/** Clamp an influence value into the valid [0, MAX_COMPANION_INFLUENCE] range. */
fun clampInfluence(value: Int): Int = value.coerceIn(0, MAX_COMPANION_INFLUENCE)

/** Percentage (0-100) used to fill the roster influence bar. */
fun influenceBarPercent(value: Int): Int =
    (clampInfluence(value) * 100) / MAX_COMPANION_INFLUENCE

/** Localization-key-safe discovery status; falls back to "unknown" for null/unrecognized values. */
fun normalizeDiscoveryStatus(status: String?): String =
    status?.takeIf { it in companionDiscoveryStages } ?: DISCOVERY_UNKNOWN
