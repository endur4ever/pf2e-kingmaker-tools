package at.posselt.pfrpg2e.kingdom.data

import kotlinx.js.JsPlainObject

/**
 * A settlement benefit granted by a completed quest, persisted on the kingdom.
 *
 * Migration42 has been seeding `accessGrants` to an empty array since it was registered, but the
 * field was never declared on KingdomData — so the migration wrote into a slot nothing could read
 * through the typed API. This is that declaration.
 */
@JsPlainObject
external interface RawAccessGrant {
    /** "trainer" | "crafting" | "itemLevel". */
    var benefitType: String

    /** Trainer class id or crafting-material id, for trainer/crafting grants. */
    var value: String?

    /** Item purchase level granted, for an "itemLevel" grant. */
    var amount: Int?

    /** Quest this grant came from — the handle a reopened quest revokes it by. */
    var sourceQuestId: String

    /** Settlement scene id, or null for kingdom-wide. */
    var settlementId: String?
}
