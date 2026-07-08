package at.posselt.pfrpg2e.kingdom

/**
 * Pure projection logic for the Army & War Pressure board "advanced" mode.
 * Computes turns-until-thresholds and per-threat arrival forecasts from
 * current pressure, pressurePerTurn, and threat ETAs. No Foundry dependencies.
 */

data class ThreatArrival(
    val threatId: String,
    val threatName: String,
    /** Turns until this threat arrives (escalates to max). Null = never/stable. */
    val turnsUntilArrival: Int?,
)

data class WarPressureProjection(
    /** Turns until pressure reaches unrest threshold. Null if never reaches (pressurePerTurn <= 0 or already at threshold). */
    val turnsUntilUnrestThreshold: Int?,
    /** Turns until pressure reaches ruin threshold. Null if never reaches. */
    val turnsUntilRuinThreshold: Int?,
    /** Per-threat arrival forecasts for active threats. */
    val threatArrivals: List<ThreatArrival>,
)

/**
 * Projects war pressure forward based on current pressure, pressure per turn,
 * and threat ETAs.
 *
 * @param currentPressure Current war pressure (0-100)
 * @param pressurePerTurn Net pressure change per turn (can be negative, zero, or positive)
 * @param unrestThreshold Pressure level at which unrest modifier activates
 * @param ruinThreshold Pressure level at which ruin/consumption modifier activates
 * @param threats Active threats with their ETAs and escalation levels
 * @param currentTurn Current kingdom turn number (for triggeredTurn calculations)
 * @return Projection with turns-until-thresholds and per-threat arrivals
 */
fun projectWarPressure(
    currentPressure: Int,
    pressurePerTurn: Int,
    unrestThreshold: Int,
    ruinThreshold: Int,
    threats: List<WarThreatSnapshot>,
    currentTurn: Int,
): WarPressureProjection {
    // Turns until unrest threshold
    val turnsUntilUnrest = when {
        currentPressure >= unrestThreshold -> 0
        pressurePerTurn <= 0 -> null
        else -> {
            val remaining = unrestThreshold - currentPressure
            (remaining + pressurePerTurn - 1) / pressurePerTurn // ceiling division
        }
    }

    // Turns until ruin threshold
    val turnsUntilRuin = when {
        currentPressure >= ruinThreshold -> 0
        pressurePerTurn <= 0 -> null
        else -> {
            val remaining = ruinThreshold - currentPressure
            (remaining + pressurePerTurn - 1) / pressurePerTurn // ceiling division
        }
    }

    // Per-threat arrival forecasts
    val threatArrivals = threats
        .filter { it.status == "active" }
        .map { threat ->
            val eta = threat.eta
            val turnsUntilArrival = when {
                eta != null -> {
                    // If ETA is known and > 0, that's the direct count
                    if (eta > 0) eta
                    else 0 // ETA <= 0 means arriving now/already arrived
                }
                else -> {
                    // ETA unknown: compute from escalation progress
                    // Threat escalates each turn once ETA reaches 0 or is unknown
                    // It reaches maxEscalation in (maxEscalation - escalationLevel) turns
                    val remainingEscalations = threat.maxEscalation - threat.escalationLevel
                    if (remainingEscalations > 0) remainingEscalations
                    else 0 // Already at max escalation
                }
            }
            ThreatArrival(
                threatId = threat.id,
                threatName = threat.name,
                turnsUntilArrival = if (turnsUntilArrival > 0) turnsUntilArrival else null,
            )
        }

    return WarPressureProjection(
        turnsUntilUnrestThreshold = turnsUntilUnrest,
        turnsUntilRuinThreshold = turnsUntilRuin,
        threatArrivals = threatArrivals,
    )
}