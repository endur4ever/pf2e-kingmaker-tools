package at.posselt.pfrpg2e.kingdom.data

import kotlinx.js.JsPlainObject

/**
 * A title + personal holding granted to one PC. Kingdom-scoped, GM-granted (plan section 2).
 *
 * Every field is nullable at this boundary except the three the grant dialog always supplies:
 * a row from an older build, a hand-edited world, or a half-written update must load as a
 * degraded holding, never take the sheet down. Strings not enums; the enums live in commonMain
 * and convert at the edge, dropping unrecognised values at the call site.
 */
@JsPlainObject
external interface RawPersonalHolding {
    /** Stable id (uuid v4); every card button and offer addresses the holding by it. */
    var id: String

    // ownership: actorUuid is authoritative; the user id is a denormalised cache for the
    // per-user card filter; the label is the display fallback when neither resolves
    var actorUuid: String?
    var ownerUserId: String?
    var ownerLabel: String?

    /** Cosmetic title, e.g. "Baron of the Tuskwater". Null = untitled. */
    var title: String?
    var name: String
    /** manor | lodge | tavern-stake | farmstead | workshop | other. */
    var kind: String

    // location binding: exactly one is meaningful; both nullable
    var boundHexKey: String?
    var structureSceneId: String?
    var structureRef: String?

    /** 1 = Modest, 2 = Comfortable, 3 = Lavish. */
    var incomeTier: Int
    /** sound | damaged | destroyed. */
    var condition: String

    var grantedTurn: Int?
    /** Last turn the TICK accrued a line -- idempotency stamp, moves whether or not the GM clicks. */
    var lastIncomeTurn: Int?
    /** Last turn the GM actually clicked Award Income -- the handler's own idempotency. */
    var incomeAwardedTurn: Int?
    /** Durable ledger: gp ACTUALLY awarded. Bumped by the award handler only, never the tick. */
    var lifetimeIncomeGold: Int?
    /** Last thing that happened to it ("Raided by the Tiger Lords"), for the card hint. */
    var lastEventLabel: String?
    /** Last observed claim state of [boundHexKey] -- edge detection for the unclaim damage hook. */
    var lastKnownClaimed: Boolean?
    /** House-rule visibility; null/true = the owner can see it. */
    var visibleToPlayers: Boolean?
}
