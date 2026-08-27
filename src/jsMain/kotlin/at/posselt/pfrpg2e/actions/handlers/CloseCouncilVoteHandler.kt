package at.posselt.pfrpg2e.actions.handlers

import at.posselt.pfrpg2e.actions.ActionDispatcher
import at.posselt.pfrpg2e.actions.ActionMessage
import at.posselt.pfrpg2e.kingdom.data.RawCouncilVote
import at.posselt.pfrpg2e.kingdom.data.appendCouncilVote
import at.posselt.pfrpg2e.kingdom.data.toRawBallots
import at.posselt.pfrpg2e.kingdom.postCouncilVoteBallot
import com.foundryvtt.core.Game
import io.github.uuidjs.uuid.v4
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

@JsPlainObject
external interface OpenCouncilVoteData {
    val actorUuid: String
    val question: String
    val options: Array<String>
}

@JsPlainObject
external interface SetCouncilVoteLinksData {
    val actorUuid: String
    val voteId: String
    val turns: Array<Int>
}

@JsPlainObject
external interface SetCouncilVoteNoteData {
    val actorUuid: String
    val voteId: String
    val note: String
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
            // +1, matching ExpeditionResolution's chronicle stamp and for the same reason:
            // kingdom.currentTurn is the COMPLETED-turn count, so during play of turn N it reads
            // N-1, while the End Turn record that will report this vote is labelled N. Stamping
            // the raw value made `closedTurn == record.turn` unmatchable, which silently emptied
            // every recap, every export line and every consequence link.
            val closed = closeVote(vote, (kingdom.currentTurn ?: 0) + 1, outcomeNote = null)
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

/**
 * Opens a vote and posts its cards. Dispatched rather than written on the clicking GM's client so
 * that EVERY council mutation lands on the same executor under [councilVoteMutex]: a sheet-side
 * read-modify-write of the whole kingdom flag would otherwise race an in-flight ballot and drop
 * it (the flag is written wholesale, so last write wins).
 */
class OpenCouncilVoteHandler(private val game: Game) : ActionHandler("openCouncilVote") {
    override suspend fun execute(action: ActionMessage, dispatcher: ActionDispatcher) {
        val data = action.data.unsafeCast<OpenCouncilVoteData>()
        val vote = councilVoteMutex.withLock {
            val actor = fromUuidTypeSafe<KingdomActor>(data.actorUuid) ?: return
            val kingdom = actor.getKingdom() ?: return
            val vote = RawCouncilVote(
                id = v4(),
                question = data.question,
                options = data.options,
                votes = emptyMap<String, Int>().toRawBallots(),
                openedTurn = (kingdom.currentTurn ?: 0) + 1,  // record-label scale, as above
                closedTurn = null,
                outcomeNote = null,
                linkedRecordRefs = emptyArray(),
                anonymous = false,
            )
            kingdom.councilVotes = appendCouncilVote(kingdom.councilVotes, vote)
            actor.setKingdom(kingdom)
            vote
        }
        // posted outside the lock: chat rendering does not touch the flag, and holding a lock
        // across it would stall a concurrent ballot for no reason
        postCouncilVoteBallot(game, data.actorUuid, vote)
    }
}

/** Deletes a vote. Same executor + lock as every other council write. */
class DeleteCouncilVoteHandler : ActionHandler("deleteCouncilVote") {
    override suspend fun execute(action: ActionMessage, dispatcher: ActionDispatcher) {
        val data = action.data.unsafeCast<CouncilVoteLifecycleData>()
        councilVoteMutex.withLock {
            val actor = fromUuidTypeSafe<KingdomActor>(data.actorUuid) ?: return
            val kingdom = actor.getKingdom() ?: return
            kingdom.councilVotes = kingdom.councilVotes
                ?.filter { it.id != data.voteId }
                ?.toTypedArray()
            actor.setKingdom(kingdom)
        }
    }
}

/**
 * Records the GM's outcome note -- the "the council tied, so I decided" line. Without a writer
 * the note field, its player-gate in the context builder and its template block are all dead, so
 * this is what makes the tie-break story real.
 */
class SetCouncilVoteNoteHandler : ActionHandler("setCouncilVoteNote") {
    override suspend fun execute(action: ActionMessage, dispatcher: ActionDispatcher) {
        val data = action.data.unsafeCast<SetCouncilVoteNoteData>()
        councilVoteMutex.withLock {
            val actor = fromUuidTypeSafe<KingdomActor>(data.actorUuid) ?: return
            val kingdom = actor.getKingdom() ?: return
            kingdom.councilVotes = kingdom.councilVotes?.map { vote ->
                if (vote.id == data.voteId) {
                    RawCouncilVote.copy(vote, outcomeNote = data.note.ifBlank { null })
                } else {
                    vote
                }
            }?.toTypedArray()
            actor.setKingdom(kingdom)
        }
    }
}

/**
 * Replaces a vote's consequence links with the picker's complete desired set.
 *
 * Set-semantics rather than an add/remove pair: the picker is checkboxes, so what it knows is the
 * final state, and diffing here would mean re-deriving what the GM unchecked. Sorted so the chip
 * row does not reorder itself between renders.
 */
class SetCouncilVoteLinksHandler : ActionHandler("setCouncilVoteLinks") {
    override suspend fun execute(action: ActionMessage, dispatcher: ActionDispatcher) {
        val data = action.data.unsafeCast<SetCouncilVoteLinksData>()
        councilVoteMutex.withLock {
            val actor = fromUuidTypeSafe<KingdomActor>(data.actorUuid) ?: return
            val kingdom = actor.getKingdom() ?: return
            kingdom.councilVotes = kingdom.councilVotes?.map { vote ->
                if (vote.id == data.voteId) {
                    RawCouncilVote.copy(vote, linkedRecordRefs = data.turns.distinct().sorted().toTypedArray())
                } else {
                    vote
                }
            }?.toTypedArray()
            actor.setKingdom(kingdom)
        }
    }
}
