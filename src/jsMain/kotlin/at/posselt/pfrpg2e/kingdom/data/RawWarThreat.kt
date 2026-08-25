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

    /**
     * Set once a peace treaty or tribute has concluded the war this threat belonged to.
     *
     * Peace is a per-faction, once-per-war event, but offer cards are per-battle and stay clickable
     * in chat scrollback forever. Without this, every historical victory card against a faction
     * stayed armed: a GM could scroll back and collect the tribute RP again, or sign peace AND
     * demand tribute from the same card and land below the floor the treaty just promised.
     *
     * Settled threats drop out of peace eligibility entirely, so a LATER war with the same faction
     * -- new threats, unsettled -- becomes offerable again on its own merits.
     */
    var peaceSettled: Boolean?

    /** True = this threat physically migrates one step toward its target each turn
     * (docs/plans/2026-07-09-plan-map-dynamism.md SS2.2). Null/false = static, today's behavior. */
    var wanders: Boolean?

    /** Hex key where the threat's radius currently sits. Null => treat as at targetHexLocation.
     * Advances ONLY on a GM-accepted migration offer, never automatically. */
    var currentHexLocation: String?

    /** Per-turn idempotency guard: the turn whose migration offer was already resolved (advance
     * OR hold), so a held threat re-offers NEXT turn rather than immediately. */
    var migrationConsumedTurn: Int?
}