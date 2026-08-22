package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.kingdom.data.RawCaravan
import at.posselt.pfrpg2e.kingdom.data.RawCaravanShipment
import at.posselt.pfrpg2e.utils.t
import kotlinx.js.JsPlainObject

@Suppress("unused")
@JsPlainObject
external interface CaravanRowContext {
    val id: String
    /** Whole route as one line, kept for the row's hover title. */
    val summary: String
    /** Who is on the far end: the trade partner when there is one, else the destination. */
    val partner: String
    val cargo: String
    /** Null when the route cannot be resolved right now, rather than showing a misleading number. */
    val raidDc: Int?
    val etaTurns: Int
    val turnsRemaining: Int
    val recallable: Boolean
}

/**
 * Builds the caravan-board rows from the kingdom's in-transit caravans.
 *
 * [raidDcOf] is injected because resolving a raid DC needs the live hex grid, which this file must
 * stay clear of. It returns null when the route cannot be walked, and the row then omits the DC.
 */
fun Array<RawCaravan>.toCaravanRowContexts(
    raidDcOf: (RawCaravan) -> Int? = { null },
): Array<CaravanRowContext> =
    filter { it.status == "inTransit" }
        .map { caravan ->
            val cargo = caravan.cargoCommodity
                ?.let { "${caravan.cargoAmount} ${t("kingdom.$it")}" }
                ?: "${caravan.cargoRp ?: 0} ${t("kingdom.resourcePoints")}"
            CaravanRowContext(
                id = caravan.id,
                summary = "$cargo: ${caravan.originLabel} → ${caravan.destLabel}",
                partner = caravan.partnerName ?: caravan.destLabel,
                cargo = cargo,
                raidDc = raidDcOf(caravan),
                etaTurns = caravan.etaTurns,
                turnsRemaining = caravan.turnsRemaining,
                recallable = caravan.status == "inTransit",
            )
        }
        .toTypedArray()

@Suppress("unused")
@JsPlainObject
external interface ShipmentRowContext {
    val id: String
    val summary: String
    val partner: String
    val cargo: String
    val raidDc: Int?
    val etaTurns: Int
    val turnsRemaining: Int
    val recallable: Boolean
}

/** Builds the shipment-board rows from the kingdom's in-transit shipments. */
fun Array<RawCaravanShipment>.toShipmentRowContexts(
    raidDcOf: (RawCaravanShipment) -> Int? = { null },
): Array<ShipmentRowContext> =
    filter { it.status == "inTransit" }
        .map { shipment ->
            val typeLabel = when (shipment.caravanType) {
                "light" -> t("kingdom.caravans.typeLight")
                "heavy" -> t("kingdom.caravans.typeHeavy")
                else -> t("kingdom.caravans.typeMedium")
            }
            val cargo = "${shipment.itemQuantity}x ${shipment.itemName} (Lvl ${shipment.itemLevel}) [$typeLabel]"
            ShipmentRowContext(
                id = shipment.id,
                summary = "$cargo: ${shipment.originLabel} → ${shipment.destLabel}",
                // An item shipment has no diplomatic partner, only a destination settlement.
                partner = shipment.destLabel,
                cargo = cargo,
                raidDc = raidDcOf(shipment),
                etaTurns = shipment.etaTurns,
                turnsRemaining = shipment.turnsRemaining,
                recallable = shipment.status == "inTransit",
            )
        }
        .toTypedArray()
