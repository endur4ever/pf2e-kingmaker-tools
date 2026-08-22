package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.fromCamelCase
import kotlinx.js.JsPlainObject

/**
 * Persistence bridge for the caravan delivery history.
 *
 * ShipmentHistoryEntry is a commonMain data class and cannot go into a Foundry flag, so entries are
 * stored as plain JS objects and converted at the boundary.
 *
 * See card t_5898d2a0.
 */
@JsPlainObject
external interface RawShipmentHistoryEntry {
    var turn: Int
    var partner: String
    var cargo: String
    var outcome: String
    var rdGained: Int?
}

/** Null when the stored outcome is not one this build understands, rather than throwing. */
fun RawShipmentHistoryEntry.toModel(): ShipmentHistoryEntry? =
    fromCamelCase<ShipmentOutcome>(outcome)?.let {
        ShipmentHistoryEntry(turn = turn, partner = partner, cargo = cargo, outcome = it, rdGained = rdGained)
    }

fun ShipmentHistoryEntry.toRaw(): RawShipmentHistoryEntry =
    RawShipmentHistoryEntry(
        turn = turn,
        partner = partner,
        cargo = cargo,
        outcome = outcome.name.lowercase(),
        rdGained = rdGained,
    )

/** Stored history as models, oldest first. Empty when never written. */
fun KingdomData.shipmentHistoryList(): List<ShipmentHistoryEntry> =
    shipmentHistory?.mapNotNull { it.toModel() } ?: emptyList()

/** Append one resolved shipment. Mutates in place; the caller persists. */
fun KingdomData.appendShipment(entry: ShipmentHistoryEntry) {
    shipmentHistory = appendShipmentHistory(shipmentHistoryList(), entry)
        .map { it.toRaw() }
        .toTypedArray()
}

/** Map a resolved caravan/shipment event onto a history entry. */
fun caravanEventToHistory(event: CaravanEvent, turn: Int): ShipmentHistoryEntry? {
    val outcome = when (event.kind) {
        CaravanEventKind.DELIVERED -> ShipmentOutcome.DELIVERED
        CaravanEventKind.RAIDED -> ShipmentOutcome.RAIDED
        // A LOST caravan never arrives; recording it as RECALLED would misreport a disaster as a
        // GM decision, so it is recorded as raided — the outcome that actually befell the cargo.
        CaravanEventKind.LOST -> ShipmentOutcome.RAIDED
    }
    return ShipmentHistoryEntry(
        turn = turn,
        partner = event.partnerName ?: "",
        cargo = event.summary,
        outcome = outcome,
        rdGained = event.bonusResourceDice.takeIf { it > 0 },
    )
}
