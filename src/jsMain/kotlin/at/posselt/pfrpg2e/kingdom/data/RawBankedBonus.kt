package at.posselt.pfrpg2e.kingdom.data

import kotlinx.js.JsPlainObject

/**
 * A circumstance bonus banked by Request Foreign Aid, spendable on a later failed check.
 *
 * Migration43 has been seeding `bankedBonuses` since it was registered, but the field was never
 * declared on KingdomData — so it wrote into a slot nothing could read through the typed API.
 */
@JsPlainObject
external interface RawBankedBonus {
    var id: String
    var value: Int
    /** Already-localized label naming where the aid came from. */
    var source: String
    var gainedTurn: Int
    /** Turn after which it lapses; null = no expiry. */
    var expiresTurn: Int?
}
