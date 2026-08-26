package at.posselt.pfrpg2e.actions.handlers

import at.posselt.pfrpg2e.actions.ActionDispatcher
import at.posselt.pfrpg2e.actions.ActionMessage
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.applyCastToCouncilVotes
import at.posselt.pfrpg2e.kingdom.councilVoteMutex
import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.setKingdom
import at.posselt.pfrpg2e.utils.fromUuidTypeSafe
import kotlinx.coroutines.sync.withLock
import kotlinx.js.JsPlainObject

@JsPlainObject
external interface CastCouncilVoteData {
    val actorUuid: String
    val voteId: String
    val optionIdx: Int
}

/**
 * Records one user's council ballot. `originatorPolicy = ANY` is the point of the handler:
 * players cast ballots, and every player-origin cast rides the socket to the FIRST-GM client,
 * which executes them one at a time against freshly-read state. That serialisation is why this
 * exists -- three players clicking inside the same second must not read-modify-write the whole
 * kingdom flag in parallel, where the last write silently wins.
 *
 * It is a serialisation point, NOT a security boundary: players are OWNERs of the party actor
 * and could write the flag directly. The ballot key is `action.senderId` -- stamped by the
 * dispatcher from the socket sender, surviving the hop -- never a user id from the payload, so
 * the attribution is as honest as the platform allows (senderId is client-supplied and
 * technically forgeable; acceptable for an advisory accountability tool).
 */
class CastCouncilVoteHandler : ActionHandler(
    "castCouncilVote",
    originatorPolicy = OriginatorPolicy.ANY,
) {
    override suspend fun execute(action: ActionMessage, dispatcher: ActionDispatcher) {
        val data = action.data.unsafeCast<CastCouncilVoteData>()
        val senderId = action.senderId ?: return
        councilVoteMutex.withLock {
            val actor = fromUuidTypeSafe<KingdomActor>(data.actorUuid) ?: return
            val kingdom = actor.getKingdom() ?: return
            kingdom.councilVotes = applyCastToCouncilVotes(
                votes = kingdom.councilVotes,
                voteId = data.voteId,
                senderId = senderId,
                optionIdx = data.optionIdx,
            )
            actor.setKingdom(kingdom)
        }
    }
}
