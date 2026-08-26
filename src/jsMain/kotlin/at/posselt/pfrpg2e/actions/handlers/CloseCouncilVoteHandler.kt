package at.posselt.pfrpg2e.actions.handlers

import at.posselt.pfrpg2e.actions.ActionDispatcher
import at.posselt.pfrpg2e.actions.ActionMessage
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.closeVote
import at.posselt.pfrpg2e.kingdom.councilVoteMutex
import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.postCouncilVoteResult
import at.posselt.pfrpg2e.kingdom.reopenVote
import at.posselt.pfrpg2e.kingdom.setKingdom
import at.posselt.pfrpg2e.utils.fromUuidTypeSafe
import kotlinx.coroutines.sync.withLock
import kotlinx.js.JsPlainObject

@JsPlainObject
external interface CouncilVoteLifecycleData {
    val actorUuid: String
    val voteId: String
}

/**
 * Closing rides the same dispatcher funnel as casting, on purpose: a GM clicking Close on their
 * own client while a player's ballot is mid-flight on the FIRST-GM client is a read-modify-write
 * race on the whole kingdom flag -- the close could resurrect a pre-ballot copy, or the ballot
 * could reopen a just-closed vote whose "frozen" result card already posted. Same executor plus
 * [councilVoteMutex] makes close-vs-cast strictly ordered. originatorPolicy stays the
 * deny-by-default GM_ONLY: only a GM may close, and the clicking button also bails on !isGM.
 */
class CloseCouncilVoteHandler : ActionHandler("closeCouncilVote") {
    override suspend fun execute(action: ActionMessage, dispatcher: ActionDispatcher) {
        val data = action.data.unsafeCast<CouncilVoteLifecycleData>()
        councilVoteMutex.withLock {
            val actor = fromUuidTypeSafe<KingdomActor>(data.actorUuid) ?: return
            val kingdom = actor.getKingdom() ?: return
            val vote = kingdom.councilVotes?.firstOrNull { it.id == data.voteId } ?: return
            if (vote.closedTurn != null) return  // idempotent: a stale card cannot move the recorded turn
            val closed = closeVote(vote, kingdom.currentTurn ?: 0, outcomeNote = null)
            kingdom.councilVotes = kingdom.councilVotes
                ?.map { if (it.id == data.voteId) closed else it }
                ?.toTypedArray()
            actor.setKingdom(kingdom)
            postCouncilVoteResult(closed)
        }
    }
}

/** Reopen, same funnel and lock as [CloseCouncilVoteHandler] for the same race. */
class ReopenCouncilVoteHandler : ActionHandler("reopenCouncilVote") {
    override suspend fun execute(action: ActionMessage, dispatcher: ActionDispatcher) {
        val data = action.data.unsafeCast<CouncilVoteLifecycleData>()
        councilVoteMutex.withLock {
            val actor = fromUuidTypeSafe<KingdomActor>(data.actorUuid) ?: return
            val kingdom = actor.getKingdom() ?: return
            val vote = kingdom.councilVotes?.firstOrNull { it.id == data.voteId } ?: return
            if (vote.closedTurn == null) return
            kingdom.councilVotes = kingdom.councilVotes
                ?.map { if (it.id == data.voteId) reopenVote(it) else it }
                ?.toTypedArray()
            actor.setKingdom(kingdom)
        }
    }
}
