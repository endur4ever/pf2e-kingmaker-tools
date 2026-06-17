package at.posselt.pfrpg2e.kingdom.data

import kotlinx.js.JsPlainObject

/**
 * A caravan shipment representing weapons, equipment, or supplies requested from
 * a faraway location to a destination settlement.
 */
@JsPlainObject
external interface RawCaravanShipment {
    var id: String
    var itemName: String
    var itemQuantity: Int
    var itemLevel: Int
    var itemBulk: String
    var itemPriceGp: Double
    var originHexKey: String
    var destHexKey: String
    var originLabel: String
    var destLabel: String
    var caravanType: String // "light", "medium", "heavy"
    var goldCost: Double
    var etaTurns: Int
    var turnsRemaining: Int
    var path: Array<String>
    var currentHexKey: String
    var status: String // "inTransit" | "delivered" | "lost" | "recalled"
}
