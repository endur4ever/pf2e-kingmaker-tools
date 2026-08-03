package at.posselt.pfrpg2e.kingdom.data

import kotlinx.js.JsPlainObject

/**
 * Persisted influence of ONE party member toward ONE companion/NPC.
 *
 * The Party Influence panel is a matrix: each companion/NPC ([companionId], the companion's
 * actorUuid or name) is a parent row, and every party member ([uuid], the member's actor UUID) has
 * their own influence value beneath it. Companion roster and party membership are both read live;
 * only these per-pair influence values are stored. Entries whose companion or member no longer
 * exists are harmless — they are ignored when the panel is built.
 */
@JsPlainObject
external interface RawPartyMemberInfluence {
    var companionId: String
    var uuid: String
    var influence: Int
}
