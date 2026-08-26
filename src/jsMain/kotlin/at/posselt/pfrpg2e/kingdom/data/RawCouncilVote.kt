package at.posselt.pfrpg2e.kingdom.data

import at.posselt.pfrpg2e.data.kingdom.COUNCIL_VOTE_CAP
import at.posselt.pfrpg2e.data.kingdom.VoteTally
import at.posselt.pfrpg2e.data.kingdom.appendCapped
import at.posselt.pfrpg2e.data.kingdom.tallyVotes
import at.posselt.pfrpg2e.utils.toMap
import at.posselt.pfrpg2e.utils.toMutableRecord
import js.objects.Record
import kotlinx.js.JsPlainObject

/**
 * Persisted shape of one council vote — the contested-call ledger
 * (`docs/plans/2026-07-09-plan-council-votes.md` section 2.1).
 *
 * ADVISORY ONLY. Nothing in the kingdom engine reads a result, so this record never gates a tick,
 * an activity or a build. It exists so the campaign remembers who argued for what, and so the GM
 * can later staple a consequence onto the decision that caused it.
 *
 * Lives top-level on `KingdomData.councilVotes`, NOT nested in a `RawTurnRecord`, because of a
 * cadence mismatch the plan calls out: turn records are only appended at End Turn, while a vote is
 * opened ad hoc mid-turn and accumulates ballots live. There would be nowhere in `turnHistory` for
 * an in-progress vote to sit. `RawTurnRecord.closedVoteIds` stamps the back-reference at close.
 *
 * Every field is nullable, deliberately and without exception. Nullability is the module's
 * migration contract: a build that adds a field, a hand-edited world, or a vote written by a newer
 * build must all load into an older reader without a crash, and the only way to guarantee that is
 * for absence to be a legal value everywhere. The reader supplies the meaning of absence instead —
 * see [toModel], which DROPS an unusable row rather than throwing, so one corrupt vote cannot take
 * down a sheet render or a turn tick.
 *
 * Primitives only, so the whole object JSON round-trips through the actor flag untouched: strings,
 * ints, string arrays, and one `js.objects.Record` ballot map (the same interop carrier
 * `RawActivity.skills` uses). No enums and no Kotlin `Map` at this boundary — a `Map` does not
 * survive the flag round trip, and an enum would throw on a value this build does not know.
 */
@JsPlainObject
external interface RawCouncilVote {
    /** Stable id (uuid v4), referenced by `RawTurnRecord.closedVoteIds` and by the chat card. */
    var id: String?

    /** Free-text question the GM typed, e.g. "How do we answer the druids?". */
    var question: String?

    /** Two to six option labels (free text). The INDEX into this array is the ballot value. */
    var options: Array<String>?

    /**
     * Per-user ballot: userId to chosen option index. `ABSTAIN_OPTION` (-1) is an explicit abstain;
     * a user simply absent from the map has not voted yet, which is a different fact and is never
     * collapsed into the first. Overwriting an entry is how "change your vote while it is open"
     * works. Read it back with [ballots]; write it with [toRawBallots].
     */
    var votes: Record<String, Int>?

    /** Kingdom turn the vote was opened on. */
    var openedTurn: Int?

    /** Kingdom turn the vote was closed on. Null means STILL OPEN — the load-bearing null here. */
    var closedTurn: Int?

    /**
     * GM free-text note attached at close: the decision taken, the tie-break rationale. This is
     * where "GM broke the tie in favour of Appease" is recorded, and the one field that can carry
     * spoilers, so the context builder gates it with `takeIf { isGM }` rather than a template `if`.
     */
    var outcomeNote: String?

    /**
     * Turn numbers of LATER turn records the GM manually attached as fallout of this vote. No
     * causality is ever inferred — the GM picks them, and can unpick them. Absent until linked.
     */
    var linkedRecordRefs: Array<Int>?

    /**
     * Reserved for a future anonymous mode. Named voting is the entire point of the feature, so
     * null and false both mean named; the flag exists only so the shape is forward-compatible
     * without a second migration. The anonymous rendering path is out of scope.
     */
    var anonymous: Boolean?
}

/**
 * The stored ballots as the plain [Map] the commonMain tally math speaks.
 *
 * An absent record reads as no ballots rather than an error: a vote that has just been opened
 * legitimately has none, and that is indistinguishable at the flag boundary from a field a future
 * build forgot to write.
 */
fun RawCouncilVote.ballots(): Map<String, Int> =
    votes?.toMap() ?: emptyMap()

/**
 * Converts this persisted row into the commonMain model — [VoteTally], the only portable type the
 * vote has. There is deliberately no commonMain twin of the record itself: `@JsPlainObject` is a
 * JS-only interop feature that commonMain cannot see, so the portable half of the feature is the
 * arithmetic (`tallyVotes`) and this adapter unpacks the row into the primitives it takes.
 *
 * Returns NULL for a row that cannot be evaluated — no id, or no options to vote for. Dropping the
 * row is the house rule for unrecognised stored data: the tally is recomputed on every sheet
 * render, so a single malformed vote must degrade to "not shown" and never to a thrown render.
 * Ballots naming an option index that no longer exists are dropped one level down, by `tallyVotes`
 * itself, which is why shortening a vote's option list after ballots exist is survivable.
 */
fun RawCouncilVote.toModel(): VoteTally? {
    if (id.isNullOrBlank()) return null
    val labels = options ?: return null
    if (labels.isEmpty()) return null
    return tallyVotes(labels.size, ballots())
}

/**
 * The toRaw half of the boundary conversion: the model side's ballot [Map] back into the interop
 * [Record] the flag can store. Kept next to [ballots] so the two directions cannot drift, and used
 * by every writer of [RawCouncilVote.votes] so no call site hand-rolls the record. Named for the
 * ballots rather than a bare `toRaw` because the receiver is a plain `Map<String, Int>`, and a
 * `toRaw` on a type that general would be a redeclaration hazard for any other persistence layer
 * that happens to carry a string-to-int map.
 */
fun Map<String, Int>.toRawBallots(): Record<String, Int> =
    map { it.key to it.value }.toMutableRecord()

/**
 * Appends [vote], dropping the OLDEST once the kingdom would exceed [cap].
 *
 * A thin array-to-list delegate onto commonMain's `appendCapped`, exactly as `appendStandingEntry`
 * delegates to `pruneStandingLog`: the cap rule is written and tested once, in commonMain, where it
 * is portable, and this wrapper exists only because the persisted carrier is a JS array of a type
 * commonMain cannot name. Every write to `KingdomData.councilVotes` goes through here so the cap
 * cannot be forgotten at one of its call sites.
 *
 * A null [votes] is treated as empty, so a kingdom that predates the flag appends correctly even if
 * Migration66 has not run on it yet.
 */
fun appendCouncilVote(
    votes: Array<RawCouncilVote>?,
    vote: RawCouncilVote,
    cap: Int = COUNCIL_VOTE_CAP,
): Array<RawCouncilVote> =
    appendCapped(votes?.toList(), vote, cap).toTypedArray()
