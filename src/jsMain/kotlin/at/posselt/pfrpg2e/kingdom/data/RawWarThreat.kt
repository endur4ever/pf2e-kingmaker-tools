package at.posselt.pfrpg2e.kingdom.data

import kotlinx.js.JsPlainObject

/**
 * An enemy army / invasion force threatening the kingdom (roadmap #12).
 * escalationLevel/maxEscalation is the threat clock; eta is turns-until-arrival.
 * When escalation hits max, the consequence fires unless [pauseOnExpiry] (Decision 3 soft-pause).
 */
@JsPlainObject
external interface RawWarThreat {
    var id: String
    var name: String
    var description: String
    var enemyFaction: String?

    var escalationLevel: Int
    var maxEscalation: Int
    var eta: Int?

    var targetSettlementSceneId: String?
    var targetHexLocation: String?
    var linkedQuestId: String?
    var linkedEventId: String?

    var pauseOnExpiry: Boolean

    var status: String
    var triggeredTurn: Int?
    /** Whether the GM offer card for this threat's arrival has been consumed (idempotency guard). */
    var offerConsumed: Boolean?

    /** Player-board visibility (house rule). Nullable for migration safety; null/true = visible. */
    var visibleToPlayers: Boolean?

    /**
     * The name of the [RawGroup] this threat belongs to, when it is a faction's war rather than a
     * wandering menace. Null = unlinked, which is the whole point of keeping [enemyFaction] free
     * text alongside it: a goblin horde has an enemy but no diplomatic relationship to mend.
     *
     * Matched on name because [RawGroup] has no id — the same key the caravan and expedition
     * subsystems already use for their faction links. A rename orphans the link, so every resolver
     * tells the GM rather than silently doing nothing.
     */
    var enemyFactionName: String?
}