package at.posselt.pfrpg2e.data.kingdom

/**
 * Pure tally core of the Council Votes contested-call ledger
 * (`docs/plans/2026-07-09-plan-council-votes.md`, sections 3.1 and 3.2).
 *
 * Council votes are ADVISORY: nothing in the kingdom engine is gated by a result, so this file
 * counts and reports and never decides. The rule that carries the whole feature is the tie rule —
 * when two or more options share the top count, the GM breaks the tie at the table. The engine
 * must therefore SAY "tie" rather than quietly hand the win to the lowest index, which is why
 * [VoteTally] carries a list of leaders instead of a winner field and why [VoteTally.decidedOption]
 * is nullable. A silent auto-pick would be worse than no tally at all: it would put a decision
 * nobody made into the campaign's permanent record.
 *
 * This lives in commonMain, beside [applyStandingDelta], because it takes primitives only. The
 * persisted vote is a jsMain `@JsPlainObject` (`RawCouncilVote`) that commonMain cannot see, so the
 * jsMain adapter unpacks `options.size` and the ballot record and calls [tallyVotes]. That is the
 * same split the plan draws: primitives here, interop carriers there.
 *
 * Deterministic by construction — no clock, no randomness, no I/O — so the live tally the sheet
 * renders while a vote is open and the frozen tally the result card publishes on close are the
 * same numbers arrived at the same way.
 */

/**
 * Ballot value for an EXPLICIT abstain, as opposed to simply not having voted yet.
 *
 * "Chose to abstain" and "was away from keyboard" are different facts about a council member, and
 * recording who did what is the entire point of the feature, so the two are never collapsed: an
 * abstainer stores this sentinel, a non-voter has no entry in the ballot map at all
 * (see [notVotedCount]). It is a negative index rather than a separate flag because the ballot map
 * is `userId -> Int` and has to JSON round-trip through the actor flag; -1 can never collide with a
 * real option index, which are always 0-based.
 */
const val ABSTAIN_OPTION = -1

/**
 * Immutable result of counting one vote's ballots.
 *
 * Everything here is derived from the ballots alone: the tally holds no identity, no turn and no
 * outcome note, so the same struct serves the live sheet tally, the frozen result card and the
 * Recent Turns recap without any of them being able to disagree about the arithmetic.
 *
 * @param optionCounts votes per option index, always exactly as wide as the option list so an
 *   option nobody picked still renders its zero bar instead of vanishing from the chart.
 * @param abstentions ballots cast as [ABSTAIN_OPTION]. Surfaced separately rather than folded into
 *   [optionCounts], because an abstain is a recorded choice but not a vote FOR anything.
 * @param totalBallots [optionCounts] plus [abstentions] — every ballot that counted. Ballots with
 *   an unusable option index are excluded here too, so the denominator of the "3 of 4" recap line
 *   can never exceed the ballots the tally actually understood.
 * @param leadingOptions every option index sharing the highest count, in ascending index order.
 *   Empty when no option has a single vote. Ascending rather than insertion order so two clients
 *   rendering the same vote highlight the same bars.
 * @param isTie true exactly when [leadingOptions] holds more than one index. The GM breaks it; the
 *   engine only reports it.
 */
data class VoteTally(
    val optionCounts: List<Int>,
    val abstentions: Int,
    val totalBallots: Int,
    val leadingOptions: List<Int>,
    val isTie: Boolean,
) {
    /**
     * The single winning option index, or null when there is nothing to declare.
     *
     * Null covers BOTH failure-to-decide cases on purpose — a tie and a vote where no option drew a
     * single ballot — because a caller that wants to print "the council chose X" must be forced to
     * handle them. Deliberately not "the first leader": picking index 0 out of a tie is exactly the
     * silent decision the GM is supposed to make out loud.
     */
    val decidedOption: Int?
        get() = leadingOptions.singleOrNull()

    /**
     * The count the leaders share — the first number of the "3 to 1" recap line.
     *
     * Reads through [leadingOptions] rather than taking a max of [optionCounts] again so it cannot
     * drift from what the leaders actually scored, and is zero when nobody voted.
     */
    val leadingCount: Int
        get() = leadingOptions.firstOrNull()?.let { optionCounts[it] } ?: 0
}

/**
 * Counts [ballots] (userId to chosen option index) against a vote offering [optionCount] options.
 *
 * [optionCount] fixes the output width instead of the ballots doing it, so the tally is a complete
 * picture of the vote — a shut-out option reports a real zero, which is information the table wants
 * ("nobody backed war") and which a ballot-derived width would silently delete.
 *
 * Three classes of ballot are handled distinctly, and the distinction is the point:
 * an in-range index votes; [ABSTAIN_OPTION] is a recorded abstain that counts as participation but
 * backs nothing; anything else — a negative other than the sentinel, or an index past the last
 * option — is unusable and is dropped from every number here. That last case is not hypothetical:
 * the ballot key is a client-supplied socket payload, and a GM may also shorten a vote's option
 * list after ballots exist. Dropping the row keeps a malformed or stale ballot from crashing a
 * render or inventing a phantom option, the same discipline the enum `fromValue` helpers follow.
 *
 * A non-positive [optionCount] yields an empty tally rather than throwing, because the tally is
 * computed on every sheet render and a corrupt vote must not take the sheet down with it.
 */
fun tallyVotes(optionCount: Int, ballots: Map<String, Int>): VoteTally {
    val width = maxOf(optionCount, 0)
    val counts = MutableList(width) { 0 }
    var abstentions = 0
    for (choice in ballots.values) {
        when {
            choice == ABSTAIN_OPTION -> abstentions++
            choice in 0 until width -> counts[choice] = counts[choice] + 1
            // Unusable index: dropped, and deliberately not counted as a ballot either.
        }
    }
    val highest = counts.maxOrNull() ?: 0
    // An all-zero board is not a tie between the options — it is a vote nobody has answered yet,
    // and reporting "tied" there would put a GM tie-break decision in front of the table for a
    // question that was never actually contested.
    val leadingOptions = if (highest <= 0) emptyList<Int>() else counts.indices.filter { counts[it] == highest }
    return VoteTally(
        optionCounts = counts.toList(),
        abstentions = abstentions,
        totalBallots = counts.sum() + abstentions,
        leadingOptions = leadingOptions,
        isTie = leadingOptions.size > 1,
    )
}

/**
 * How many of [eligibleVoters] have not answered at all — the "N has not voted" line.
 *
 * Kept out of [VoteTally] because eligibility is not a property of the ballots: only the caller
 * knows how many users are at the table, and that number changes while a vote is open. Floored at
 * zero so a shrinking table cannot render a negative count: a user who cast a ballot and then left
 * the session still has a ballot on record, which can legitimately push [VoteTally.totalBallots]
 * above the current head count.
 */
fun notVotedCount(eligibleVoters: Int, tally: VoteTally): Int =
    maxOf(eligibleVoters - tally.totalBallots, 0)

/**
 * How many council votes a kingdom retains.
 *
 * A named constant beside the append helper rather than a literal default at each call site, for
 * the reason `TURN_HISTORY_CAP` gives verbatim. Votes are heavier than turn records — each carries
 * a per-user ballot map — and are opened far more rarely, so the cap sits well below the turn
 * history's hundred. Provisional in the same sense as any storage bound here: the plan's open
 * question 2 asks Gregory whether 50 should instead become a world setting.
 */
const val COUNCIL_VOTE_CAP = 50

/**
 * Appends [item] to [existing], dropping the OLDEST entries once the list would exceed [cap].
 *
 * Oldest-first is safe for votes specifically because a vote is inert: it gates nothing, so
 * evicting an ancient one costs only the deep past of the chronicle and can never strand a pending
 * decision the way pruning an unanswered offer would. Ordering is load-bearing in the other
 * direction too — the list stays oldest-first so the sheet's "most recent vote last" reading and
 * the eviction end are the same end.
 *
 * A null [existing] is treated as empty, so a legacy kingdom that predates the flag appends
 * without a migration having run first. A non-positive [cap] keeps nothing rather than throwing.
 *
 * Generic because commonMain cannot name the persisted `RawCouncilVote`: the jsMain
 * `appendCouncilVote(votes, vote, cap)` wrapper is a thin array-to-list delegate onto this, so the
 * cap rule is written and tested once.
 */
fun <T> appendCapped(existing: List<T>?, item: T, cap: Int = COUNCIL_VOTE_CAP): List<T> {
    val appended = existing.orEmpty() + item
    return if (appended.size <= cap) appended else appended.takeLast(cap.coerceAtLeast(0))
}
