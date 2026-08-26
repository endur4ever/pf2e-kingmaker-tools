package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.ABSTAIN_OPTION
import at.posselt.pfrpg2e.data.kingdom.VoteTally
import at.posselt.pfrpg2e.data.kingdom.tallyVotes
import at.posselt.pfrpg2e.kingdom.data.RawCouncilVote
import at.posselt.pfrpg2e.kingdom.data.ballots
import at.posselt.pfrpg2e.kingdom.data.toRawBallots

/**
 * Pure transforms over [RawCouncilVote] -- new copies out, no Foundry I/O, unit-testable. They
 * live in jsMain rather than commonMain only because they touch the @JsPlainObject type; the
 * tally MATH stays in commonMain (CouncilVoteTally.kt) where primitives suffice.
 */

/**
 * Serialises every council-vote mutation on the executing client. Funnelling player casts to the
 * first-GM client narrows the whole-flag read-modify-write race but does not remove it: the
 * dispatcher runs each incoming socket message as its own coroutine, and a handler suspends
 * between its read and its write (uuid resolution, the actor-update round trip). Two same-second
 * ballots could still interleave there and the second write would silently drop the first. Every
 * council handler takes this lock around read-to-write.
 */
val councilVoteMutex = kotlinx.coroutines.sync.Mutex()

/** Adapter: unpack a raw vote into the commonMain math. */
fun tallyVote(vote: RawCouncilVote): VoteTally =
    tallyVotes(vote.options?.size ?: 0, vote.ballots())

/**
 * Record or overwrite one user's ballot. Total: a closed vote and an option index outside
 * `0..options.size-1` (other than [ABSTAIN_OPTION]) both return the vote unchanged, so a
 * malformed or malicious socket payload cannot corrupt the map or resurrect a closed vote.
 */
fun castVote(vote: RawCouncilVote, userId: String, optionIdx: Int): RawCouncilVote {
    if (vote.closedTurn != null) return vote
    val optionCount = vote.options?.size ?: 0
    if (optionIdx != ABSTAIN_OPTION && optionIdx !in 0 until optionCount) return vote
    val updated = vote.ballots().toMutableMap()
    updated[userId] = optionIdx
    return RawCouncilVote.copy(vote, votes = updated.toRawBallots())
}

/** Freeze the vote at [turn] with the GM's outcome note. Idempotent: closing a closed vote is a
 *  no-op, so a stale card's Close click cannot move the recorded turn. */
fun closeVote(vote: RawCouncilVote, turn: Int, outcomeNote: String?): RawCouncilVote =
    if (vote.closedTurn != null) {
        vote
    } else if (outcomeNote != null) {
        RawCouncilVote.copy(vote, closedTurn = turn, outcomeNote = outcomeNote)
    } else {
        RawCouncilVote.copy(vote, closedTurn = turn)
    }

/** GM reopen: ballots may change again. Assigns the field directly on a fresh copy because the
 *  generated static copy treats null-vs-undefined ambiguously -- clearing must be explicit. */
fun reopenVote(vote: RawCouncilVote): RawCouncilVote {
    val reopened = RawCouncilVote.copy(vote)
    reopened.closedTurn = null
    return reopened
}

/** GM manual link: append [turn] to linkedRecordRefs, deduplicated. No inference anywhere. */
fun linkConsequence(vote: RawCouncilVote, turn: Int): RawCouncilVote {
    val existing = vote.linkedRecordRefs ?: emptyArray()
    if (turn in existing) return vote
    return RawCouncilVote.copy(vote, linkedRecordRefs = existing + turn)
}

/** GM manual unlink: remove [turn]; no-op when absent. */
fun unlinkConsequence(vote: RawCouncilVote, turn: Int): RawCouncilVote {
    val existing = vote.linkedRecordRefs ?: return vote
    if (turn !in existing) return vote
    return RawCouncilVote.copy(vote, linkedRecordRefs = existing.filter { it != turn }.toTypedArray())
}

/**
 * The cast handler's core, extracted so attribution is testable without Foundry: the ballot key
 * is the [senderId] ARGUMENT -- which the caller takes from the socket-authenticated
 * `action.senderId` -- never any user id a clicking client baked into the payload.
 */
fun applyCastToCouncilVotes(
    votes: Array<RawCouncilVote>?,
    voteId: String,
    senderId: String,
    optionIdx: Int,
): Array<RawCouncilVote> =
    (votes ?: emptyArray())
        .map { if (it.id == voteId) castVote(it, senderId, optionIdx) else it }
        .toTypedArray()
