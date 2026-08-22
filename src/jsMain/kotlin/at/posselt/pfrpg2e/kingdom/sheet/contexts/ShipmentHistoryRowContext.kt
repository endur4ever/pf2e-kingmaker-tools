package at.posselt.pfrpg2e.kingdom.sheet.contexts

import kotlinx.js.JsPlainObject

/** One rendered row of the caravan delivery history. */
@JsPlainObject
external interface ShipmentHistoryRowContext {
    val turn: Int
    val partner: String
    val cargo: String
    /** Raw outcome value, for the per-outcome badge colour. */
    val outcome: String
    /** Localized outcome label. */
    val outcomeLabel: String
    /** "+3 RD" when a sale earned dice, else empty. */
    val rd: String
}
