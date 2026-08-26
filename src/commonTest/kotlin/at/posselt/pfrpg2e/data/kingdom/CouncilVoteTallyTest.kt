package at.posselt.pfrpg2e.data.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the cases section 7.1 of the council-votes plan enumerates, plus the two invariants the
 * feature would be actively harmful without: the engine never picks a winner out of a tie, and an
 * explicit abstain is never confused with not having voted.
 */
class CouncilVoteTallyTest {

    /** One ballot per synthetic user, in the order given, so fixtures read as the vote's shape. */
    private fun ballots(vararg choices: Int): Map<String, Int> =
        choices.withIndex().associate { (i, choice) -> "user-of-index-$i" to choice }

    // --- counting ---

    @Test
    fun ballotsAreCountedUnderTheOptionIndexTheyName() {
        // The plan's fixture: two users pick option 0, one picks option 1, over a 2-option vote.
        val tally = tallyVotes(optionCount = 2, ballots = ballots(0, 0, 1))
        assertEquals(listOf(2, 1), tally.optionCounts)
        assertEquals(3, tally.totalBallots)
        assertEquals(0, tally.abstentions)
    }

    @Test
    fun everyOfferedOptionKeepsItsRowEvenWhenNobodyPicksIt() {
        // "Nobody backed war" is information the table wants; a ballot-derived width would delete
        // it. The output is as wide as the vote, not as wide as the answers.
        val tally = tallyVotes(optionCount = 4, ballots = ballots(2))
        assertEquals(listOf(0, 0, 1, 0), tally.optionCounts)
    }

    @Test
    fun aTallyIsIndependentOfTheOrderBallotsWereCastIn() {
        val castLate = tallyVotes(optionCount = 3, ballots = mapOf("c" to 2, "a" to 0, "b" to 1))
        val castEarly = tallyVotes(optionCount = 3, ballots = mapOf("a" to 0, "b" to 1, "c" to 2))
        assertEquals(castEarly, castLate)
    }

    // --- the tie rule: reported, never resolved ---

    @Test
    fun optionsSharingTheTopCountAreReportedAsATieAndNoWinnerIsPicked() {
        val tally = tallyVotes(optionCount = 3, ballots = ballots(0, 0, 1, 1, 2))
        assertEquals(listOf(2, 2, 1), tally.optionCounts)
        assertTrue(tally.isTie)
        assertEquals(listOf(0, 1), tally.leadingOptions)
        assertEquals(2, tally.leadingCount)
        // The whole point: the GM breaks the tie at the table. Handing back index 0 here would put
        // a decision nobody made into the campaign's permanent record.
        assertNull(tally.decidedOption)
    }

    @Test
    fun aThreeWayTieListsEveryLeaderInAscendingIndexOrder() {
        // Insertion order is deliberately 2, 0, 1 — two clients must highlight the same bars.
        val tally = tallyVotes(optionCount = 3, ballots = mapOf("c" to 2, "a" to 0, "b" to 1))
        assertEquals(listOf(0, 1, 2), tally.leadingOptions)
        assertTrue(tally.isTie)
        assertNull(tally.decidedOption)
    }

    @Test
    fun aClearWinnerIsNotATieAndIsDecided() {
        val tally = tallyVotes(optionCount = 2, ballots = ballots(0, 0, 0, 1))
        assertEquals(listOf(3, 1), tally.optionCounts)
        assertFalse(tally.isTie)
        assertEquals(listOf(0), tally.leadingOptions)
        assertEquals(0, tally.decidedOption)
        assertEquals(3, tally.leadingCount)
    }

    @Test
    fun aWinnerByOneVoteIsStillNotATie() {
        // The boundary next to the tie: 2 vs 1 decides, 2 vs 2 does not (asserted above).
        val tally = tallyVotes(optionCount = 2, ballots = ballots(0, 0, 1))
        assertFalse(tally.isTie)
        assertEquals(0, tally.decidedOption)
    }

    @Test
    fun anUnansweredVoteIsNotATieBetweenItsOptions() {
        // All-zero is a question nobody has answered, not a contested one — offering the GM a
        // tie-break here would be asking them to decide a vote that never happened.
        val tally = tallyVotes(optionCount = 3, ballots = emptyMap())
        assertEquals(listOf(0, 0, 0), tally.optionCounts)
        assertFalse(tally.isTie)
        assertEquals(emptyList<Int>(), tally.leadingOptions)
        assertNull(tally.decidedOption)
        assertEquals(0, tally.leadingCount)
        assertEquals(0, tally.totalBallots)
    }

    // --- abstentions ---

    @Test
    fun theAbstainSentinelIsMinusOne() {
        // Pinned because the ballot map is persisted: changing it would silently reclassify every
        // stored abstain as an unusable index and drop it from the record.
        assertEquals(-1, ABSTAIN_OPTION)
    }

    @Test
    fun abstainsCountAsBallotsCastButBackNoOption() {
        // -1 written literally, so a change to the sentinel is caught here and not papered over by
        // the fixture following the constant.
        val tally = tallyVotes(optionCount = 2, ballots = ballots(0, -1, -1, 1))
        assertEquals(listOf(1, 1), tally.optionCounts)
        assertEquals(2, tally.abstentions)
        assertEquals(4, tally.totalBallots)
    }

    @Test
    fun aVoteWhereEveryoneAbstainedHasNoLeaderAndIsNotATie() {
        val tally = tallyVotes(optionCount = 2, ballots = ballots(ABSTAIN_OPTION, ABSTAIN_OPTION))
        assertEquals(listOf(0, 0), tally.optionCounts)
        assertEquals(2, tally.abstentions)
        assertEquals(2, tally.totalBallots)
        assertEquals(emptyList<Int>(), tally.leadingOptions)
        assertFalse(tally.isTie)
        assertNull(tally.decidedOption)
    }

    // --- unusable ballots are dropped, never counted ---

    @Test
    fun theLastOptionCountsButAnIndexOnePastItIsDropped() {
        // Asymmetric on purpose: index 2 is the last valid option of a 3-option vote and must
        // count; index 3 is one past the end and must vanish from counts AND from totalBallots.
        val lastOption = tallyVotes(optionCount = 3, ballots = ballots(2))
        assertEquals(listOf(0, 0, 1), lastOption.optionCounts)
        assertEquals(1, lastOption.totalBallots)

        val pastTheEnd = tallyVotes(optionCount = 3, ballots = ballots(3))
        assertEquals(listOf(0, 0, 0), pastTheEnd.optionCounts)
        assertEquals(0, pastTheEnd.totalBallots)
        assertEquals(0, pastTheEnd.abstentions)
    }

    @Test
    fun aNegativeIndexThatIsNotTheAbstainSentinelIsDropped() {
        // A GM who shortened the option list, or a malformed socket payload — either way the row
        // is dropped rather than crashing the render or inventing a phantom option.
        val tally = tallyVotes(optionCount = 2, ballots = ballots(-2, 0))
        assertEquals(listOf(1, 0), tally.optionCounts)
        assertEquals(0, tally.abstentions)
        assertEquals(1, tally.totalBallots)
    }

    @Test
    fun aVoteWithNoOptionsTalliesEmptyInsteadOfThrowing() {
        // The tally runs on every sheet render, so corrupt data must not take the sheet down.
        val none = tallyVotes(optionCount = 0, ballots = ballots(0, 1))
        assertEquals(emptyList<Int>(), none.optionCounts)
        assertEquals(0, none.totalBallots)

        val negative = tallyVotes(optionCount = -3, ballots = ballots(0, ABSTAIN_OPTION))
        assertEquals(emptyList<Int>(), negative.optionCounts)
        // The abstain still counted: it never needed an option row to be a recorded ballot.
        assertEquals(1, negative.abstentions)
        assertEquals(1, negative.totalBallots)
    }

    // --- "has not voted" vs "abstained" ---

    @Test
    fun notVotedCountIsTheTableMinusTheBallotsCast() {
        val tally = tallyVotes(optionCount = 2, ballots = ballots(0, 1, 1))
        assertEquals(2, notVotedCount(eligibleVoters = 5, tally = tally))
    }

    @Test
    fun anAbstainerHasAnsweredAndIsNotCountedAsNotVoting() {
        // The distinction the feature exists for: "chose to abstain" is not "away from keyboard".
        val tally = tallyVotes(optionCount = 2, ballots = ballots(0, ABSTAIN_OPTION))
        assertEquals(2, notVotedCount(eligibleVoters = 4, tally = tally))
    }

    @Test
    fun notVotedCountIsZeroWhenTheWholeTableHasAnswered() {
        val tally = tallyVotes(optionCount = 2, ballots = ballots(0, 1, ABSTAIN_OPTION))
        assertEquals(0, notVotedCount(eligibleVoters = 3, tally = tally))
    }

    @Test
    fun notVotedCountFloorsAtZeroWhenAVoterHasLeftTheTable() {
        // A user who cast a ballot and then left still has a ballot on record, which can push the
        // ballot count above the current head count. Rendering "-1 have not voted" is not an option.
        val tally = tallyVotes(optionCount = 2, ballots = ballots(0, 1, 1))
        assertEquals(0, notVotedCount(eligibleVoters = 2, tally = tally))
    }

    // --- retention cap ---

    @Test
    fun theCouncilVoteCapIsFifty() {
        assertEquals(50, COUNCIL_VOTE_CAP)
    }

    @Test
    fun appendingBelowTheCapDropsNothing() {
        val kept = appendCapped(listOf(1, 2, 3), 4)
        assertEquals(listOf(1, 2, 3, 4), kept)
    }

    @Test
    fun anAppendThatLandsExactlyOnTheCapStillDropsNothing() {
        // One below the boundary and on it: filling the last slot must keep the oldest vote.
        val kept = appendCapped((1..COUNCIL_VOTE_CAP - 1).toList(), 999)
        assertEquals(COUNCIL_VOTE_CAP, kept.size)
        assertEquals(1, kept.first())
        assertEquals(999, kept.last())
    }

    @Test
    fun anAppendPastTheCapDropsTheOldestAndKeepsOrder() {
        val kept = appendCapped((1..COUNCIL_VOTE_CAP).toList(), 999)
        assertEquals(COUNCIL_VOTE_CAP, kept.size)
        assertEquals(2, kept.first())
        assertEquals(999, kept.last())
    }

    @Test
    fun appendingToANullListTreatsItAsEmpty() {
        // A kingdom that predates the flag must be able to record its first vote without a
        // migration having run first.
        assertEquals(listOf("first"), appendCapped<String>(null, "first"))
    }

    @Test
    fun aNonPositiveCapKeepsNothingRatherThanThrowing() {
        assertEquals(emptyList<Int>(), appendCapped(listOf(1, 2), 3, cap = 0))
        assertEquals(emptyList<Int>(), appendCapped(listOf(1, 2), 3, cap = -5))
    }

    @Test
    fun appendingDoesNotMutateTheListItWasGiven() {
        val existing = (1..COUNCIL_VOTE_CAP).toList()
        val appended = appendCapped(existing, 999)
        assertEquals(999, appended.last())
        // The caller's list is the persisted array's mirror; trimming it in place would evict a
        // vote from the kingdom flag before the caller ever decided to write the new list back.
        assertEquals(COUNCIL_VOTE_CAP, existing.size)
        assertEquals(1, existing.first())
        assertEquals(COUNCIL_VOTE_CAP, existing.last())
    }
}
