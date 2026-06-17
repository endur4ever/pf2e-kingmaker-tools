package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.kingdom.data.RawCaravan
import at.posselt.pfrpg2e.kingdom.data.RawCaravanShipment
import at.posselt.pfrpg2e.utils.t
import kotlinx.js.JsPlainObject

@Suppress("unused")
@JsPlainObject
external interface CaravanRowContext {
    val id: String
    val summary: String
    val turnsRemaining: Int
    val recallable: Boolean
}

/** Builds the caravan-board rows from the kingdom's in-transit caravans. */
fun Array<RawCaravan>.toCaravanRowContexts(): Array<CaravanRowContext> =
    filter { it.status == "inTransit" }
        .map { caravan ->
            val cargo = caravan.cargoCommodity
                ?.let { "${caravan.cargoAmount} ${t("kingdom.$it")}" }
                ?: "${caravan.cargoRp ?: 0} ${t("kingdom.resourcePoints")}"
            CaravanRowContext(
                id = caravan.id,
                summary = "$cargo: ${caravan.originLabel} → ${caravan.destLabel}",
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
    val turnsRemaining: Int
    val recallable: Boolean
}

/** Builds the shipment-board rows from the kingdom's in-transit shipments. */
fun Array<RawCaravanShipment>.toShipmentRowContexts(): Array<ShipmentRowContext> =
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
                turnsRemaining = shipment.turnsRemaining,
                recallable = shipment.status == "inTransit",
            )
        }
        .toTypedArray()

