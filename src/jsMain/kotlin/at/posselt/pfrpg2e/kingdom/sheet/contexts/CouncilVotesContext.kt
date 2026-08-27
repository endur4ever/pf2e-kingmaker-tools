package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.data.kingdom.ABSTAIN_OPTION
import at.posselt.pfrpg2e.data.kingdom.notVotedCount
import at.posselt.pfrpg2e.kingdom.data.RawCouncilVote
import at.posselt.pfrpg2e.kingdom.data.ballots
import at.posselt.pfrpg2e.kingdom.tallyVote
import kotlinx.js.JsPlainObject

@Suppress("unused")
@JsPlainObject
external interface VoteOptionRowContext {
    val label: String
    val count: Int
    val pct: Int
    val isLeading: Boolean
}

@Suppress("unused")
@JsPlainObject
external interface VoteBallotRowContext {
    val voterName: String
    val optionLabel: String
    val abstained: Boolean
}

@Suppress("unused")
@JsPlainObject
external interface CouncilVoteRowContext {
    val id: String
    val question: String
    val isOpen: Boolean
    val openedTurn: Int
    val closedTurn: Int?
    val options: Array<VoteOptionRowContext>
    val ballots: Array<VoteBallotRowContext>
    val abstentions: Int
    val notVotedCount: Int
    val isTie: Boolean
    val hasBallots: Boolean
    /**
     * The GM's free-text outcome note, NULL for players. It can hold spoilers, so the gate is the
     * absence of the data rather than a template conditional: players are OWNERs of the party
     * actor, and a {{#if isGM}} is layout, never authorization. (Plan open question 5 asks
     * whether to gate at all; gated is the direction that cannot leak, and un-gating later is a
     * one-line change here.)
     */
    val outcomeNote: String?
    val linkedTurns: Array<Int>
    val hasLinks: Boolean
}

@Suppress("unused")
@JsPlainObject
external interface CouncilVotesContext {
    val isGM: Boolean
    val votes: Array<CouncilVoteRowContext>
    val hasAny: Boolean
}

/**
 * Builds the Votes section rows.
 *
 * [voterNameOf] and [eligibleVoters] are injected rather than read from `game` so the whole
 * builder is testable without a Foundry registry -- the sheet passes
 * `game.users.get(id)?.name` and the non-GM head count.
 *
 * A row whose id or question is missing (a half-written flag from a failed update) is DROPPED
 * rather than rendered blank: every GM control on the row addresses it by id, so a row without
 * one would draw buttons that can never resolve.
 */
fun buildCouncilVotesContext(
    votes: Array<RawCouncilVote>?,
    isGM: Boolean,
    eligibleVoters: Int,
    voterNameOf: (String) -> String?,
): CouncilVotesContext {
    val rows = (votes ?: emptyArray()).mapNotNull { vote ->
        val id = vote.id ?: return@mapNotNull null
        val labels = vote.options ?: emptyArray()
        val tally = tallyVote(vote)
        // percentages are of the ballots CAST, not of the table: a bar that shrinks when an
        // absent player is counted would read as losing support it never had
        val castTotal = tally.optionCounts.sum()
        CouncilVoteRowContext(
            id = id,
            question = vote.question ?: "",
            isOpen = vote.closedTurn == null,
            openedTurn = vote.openedTurn ?: 0,
            closedTurn = vote.closedTurn,
            options = labels.mapIndexed { index, label ->
                val count = tally.optionCounts.getOrNull(index) ?: 0
                VoteOptionRowContext(
                    label = label,
                    count = count,
                    pct = if (castTotal > 0) count * 100 / castTotal else 0,
                    // leadingOptions, not decidedOption: while a vote is open a tie is a real
                    // state worth showing on both bars. No count > 0 guard is needed -- tallyVotes
                    // returns an EMPTY leaders list for an all-zero board on purpose ("a vote
                    // nobody has answered yet" is not a tie), so an unanswered vote marks nothing.
                    isLeading = index in tally.leadingOptions,
                )
            }.toTypedArray(),
            ballots = vote.ballots().entries
                .sortedBy { voterNameOf(it.key) ?: it.key }
                .map { (userId, choice) ->
                    VoteBallotRowContext(
                        voterName = voterNameOf(userId) ?: userId,
                        optionLabel = labels.getOrNull(choice) ?: "",
                        abstained = choice == ABSTAIN_OPTION,
                    )
                }.toTypedArray(),
            abstentions = tally.abstentions,
            notVotedCount = notVotedCount(eligibleVoters, tally),
            isTie = tally.isTie,
            hasBallots = tally.totalBallots > 0,
            outcomeNote = vote.outcomeNote?.takeIf { isGM },
            linkedTurns = vote.linkedRecordRefs ?: emptyArray(),
            hasLinks = (vote.linkedRecordRefs?.size ?: 0) > 0,
        )
    }
    return CouncilVotesContext(
        isGM = isGM,
        votes = rows.toTypedArray(),
        hasAny = rows.isNotEmpty(),
    )
}

/** Form payload for the outcome-note prompt. */
@JsPlainObject
external interface CouncilNoteData {
    val note: String
}

/** Context for the generic single-row form template the note prompt reuses. */
@JsPlainObject
external interface CouncilNoteContext {
    val formRows: Array<at.posselt.pfrpg2e.app.forms.FormElementContext>
}
