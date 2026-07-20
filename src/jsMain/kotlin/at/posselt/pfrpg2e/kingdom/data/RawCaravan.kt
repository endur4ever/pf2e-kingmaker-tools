package at.posselt.pfrpg2e.kingdom.data

import kotlinx.js.JsPlainObject

/**
 * A caravan in transit between two realm-map hexes, part of the commodity market / caravan economy.
 * Caravans advance one step per kingdom turn (End Turn); see `kingdom/CaravanTick.kt`.
 *
 * - [kind] `sellToPartner` — carries Commodities to a trade-partner Group, delivers RP on arrival.
 *   `buyFromPartner` — carries RP to a partner, delivers Commodities on arrival.
 *   (The `settlementTransfer` kind was retired: inter-settlement transfers fight the single global
 *   commodity pool — rejected in the 2026-07-09 gap analysis. Migration44 remaps stray values.)
 * - [cargoCommodity]/[cargoAmount] describe a Commodity cargo (food|lumber|stone|ore|luxuries);
 *   [cargoRp] is used instead for an RP cargo (`buyFromPartner`).
 * - [etaTurns] is the full route length in turns; [turnsRemaining] counts down to 0 (arrival).
 */
@JsPlainObject
external interface RawCaravan {
    var id: String
    var kind: String
    var originHexKey: String
    var destHexKey: String
    var originLabel: String
    var destLabel: String
    var partnerName: String?
    var cargoCommodity: String?
    var cargoAmount: Int
    var cargoRp: Int?
    var etaTurns: Int
    var turnsRemaining: Int
    var status: String // inTransit | delivered | lost | recalled
}
