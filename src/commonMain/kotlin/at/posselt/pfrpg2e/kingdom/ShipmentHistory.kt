package at.posselt.pfrpg2e.kingdom

/**
 * Pure caravan/shipment history: append a resolved shipment and keep the list capped to the most
 * recent entries, plus the outcome tallies the collapsible history board shows as badges. Today
 * nothing records delivered/raided shipments after they resolve; this is the deterministic core the
 * CaravanTick resolution + the history section build on (the persisted `shipmentHistory` field,
 * Migration, structured rows, and GM-gating are the deferred jsMain wiring).
 *
 * See card t_5898d2a0.
 */

/** How a dispatched shipment ended. */
enum class ShipmentOutcome {
    DELIVERED,
    RAIDED,
    RECALLED,
}

/** One resolved shipment recorded to history. */
data class ShipmentHistoryEntry(
    val turn: Int,
    val partner: String,
    val cargo: String,
    val outcome: ShipmentOutcome,
    /** Resource Dice gained on delivery, when the shipment was a sale; null otherwise. */
    val rdGained: Int? = null,
)

/** Default number of resolved shipments retained before the oldest are pruned. */
const val SHIPMENT_HISTORY_CAP = 50

/**
 * Append [entry] (the newest resolution) to [history] and keep only the most recent [cap] entries,
 * dropping the oldest from the front. Order is preserved oldest -> newest. A non-positive [cap]
 * yields an empty history.
 */
fun appendShipmentHistory(
    history: List<ShipmentHistoryEntry>,
    entry: ShipmentHistoryEntry,
    cap: Int = SHIPMENT_HISTORY_CAP,
): List<ShipmentHistoryEntry> {
    if (cap <= 0) return emptyList()
    val appended = history + entry
    return if (appended.size <= cap) appended else appended.takeLast(cap)
}

/** Per-outcome counts for the history board badges; every outcome is present (0 when none). */
fun shipmentOutcomeCounts(history: List<ShipmentHistoryEntry>): Map<ShipmentOutcome, Int> =
    ShipmentOutcome.entries.associateWith { outcome -> history.count { it.outcome == outcome } }
