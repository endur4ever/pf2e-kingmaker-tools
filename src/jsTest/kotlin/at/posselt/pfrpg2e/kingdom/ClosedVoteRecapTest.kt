package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawCouncilVote
import at.posselt.pfrpg2e.kingdom.data.RawTurnRecord
import at.posselt.pfrpg2e.kingdom.dialogs.linkableTurnsFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClosedVoteRecapTest {
    private fun record(turn: Int, closedVoteIds: Array<String>? = null, notes: String? = null): RawTurnRecord {
        val obj = js("{}").unsafeCast<RawTurnRecord>()
        obj.turn = turn
        obj.timestamp = "2026-08-26T00:00:00Z"
        obj.fame = 0
        obj.resourcePoints = 0
        obj.consumption = 0
        obj.unrest = 0
        obj.closedVoteIds = closedVoteIds
        obj.notes = notes
        obj.playerNotes = null
        return obj
    }

    private fun vote(
        id: String,
        options: Array<String> = arrayOf("Aye", "Nay"),
        closedTurn: Int? = null,
        links: Array<Int>? = null,
    ): RawCouncilVote {
        val obj = js("{}").unsafeCast<RawCouncilVote>()
        obj.id = id
        obj.question = "Answer the druids?"
        obj.options = options
        obj.openedTurn = 4
        obj.closedTurn = closedTurn
        obj.linkedRecordRefs = links
        return obj
    }

    /** Ballots first, THEN close -- castVote refuses to record on a closed vote, by design. */
    private fun closedAt(vote: RawCouncilVote, turn: Int): RawCouncilVote =
        RawCouncilVote.copy(vote, closedTurn = turn)

    @Test
    fun aClosedVoteBecomesARecapLineOnItsTurnForBothGmAndPlayers() {
        var v = vote("v1")
        v = castVote(v, "u1", 0)
        v = castVote(v, "u2", 0)
        v = castVote(v, "u3", 1)
        v = closedAt(v, 5)
        val history = arrayOf(record(5, closedVoteIds = arrayOf("v1")))
        val gm = buildSessionPrepView(
            quests = null, clocks = emptyArray(), events = null, hexContents = null,
            companionQuests = null, isGM = true, turnHistory = history, councilVotes = arrayOf(v),
        )
        val line = gm.recentTurns.single().closedVotes.single()
        assertEquals("Answer the druids?", line.question)
        assertEquals("Aye", line.winnerLabel)
        assertEquals(2, line.winnerCount)
        assertEquals(3, line.totalBallots)

        val players = buildSessionPrepView(
            quests = null, clocks = emptyArray(), events = null, hexContents = null,
            companionQuests = null, isGM = false, turnHistory = history, councilVotes = arrayOf(v),
        )
        assertEquals("Aye", players.recentTurns.single().closedVotes.single().winnerLabel,
            "closed votes are shared memory -- the player slice carries them too")
    }

    @Test
    fun aTieNamesNoWinnerRatherThanTheFirstOption() {
        var v = vote("v1")
        v = castVote(v, "u1", 0)
        v = castVote(v, "u2", 1)
        v = closedAt(v, 5)
        val view = buildSessionPrepView(
            quests = null, clocks = emptyArray(), events = null, hexContents = null,
            companionQuests = null, isGM = true,
            turnHistory = arrayOf(record(5, closedVoteIds = arrayOf("v1"))),
            councilVotes = arrayOf(v),
        )
        val line = view.recentTurns.single().closedVotes.single()
        assertTrue(line.isTie)
        assertNull(line.winnerLabel, "picking a winner out of a tie is the GM's call, not the recap's")
    }

    @Test
    fun anEvictedVoteIsSkippedNotRenderedBlank() {
        // the vote cap dropped the oldest vote, but the turn record still names its id
        val view = buildSessionPrepView(
            quests = null, clocks = emptyArray(), events = null, hexContents = null,
            companionQuests = null, isGM = true,
            turnHistory = arrayOf(record(5, closedVoteIds = arrayOf("gone", "v1"))),
            councilVotes = arrayOf(closedAt(vote("v1"), 5)),
        )
        assertEquals(1, view.recentTurns.single().closedVotes.size)
    }

    @Test
    fun turnsWithNoClosedVotesCarryAnEmptyListNotANull() {
        val view = buildSessionPrepView(
            quests = null, clocks = emptyArray(), events = null, hexContents = null,
            companionQuests = null, isGM = true,
            turnHistory = arrayOf(record(5), record(6, closedVoteIds = emptyArray())),
            councilVotes = arrayOf(closedAt(vote("v1"), 5)),
        )
        assertTrue(view.recentTurns.all { it.closedVotes.isEmpty() })
    }

    @Test
    fun aVoteClosedDuringPlayLandsOnTheTurnRecordThatReportsIt() {
        // THE regression: kingdom.currentTurn is the COMPLETED-turn count, so during play of
        // turn N it reads N-1, while the End Turn record is labelled N. Stamping the raw value
        // made `closedTurn == record.turn` unmatchable and silently emptied every recap. The
        // close handler stamps currentTurn + 1; this pins the two scales together.
        val completedTurns = 4              // kingdom.currentTurn while turn 5 is being played
        val stampedByCloseHandler = completedTurns + 1
        val recordTurnAtEndTurn = completedTurns + 1   // TurnWizardApplication's snapshotTurn
        assertEquals(recordTurnAtEndTurn, stampedByCloseHandler,
            "a vote closed during turn N must be reported by turn N's record")

        // and the End Turn filter that uses it selects exactly that vote
        val closed = closedAt(vote("v1"), stampedByCloseHandler)
        val stillOpen = vote("v2", closedTurn = null)
        val selected = arrayOf(closed, stillOpen)
            .filter { it.closedTurn == recordTurnAtEndTurn }
            .mapNotNull { it.id }
        assertEquals(listOf("v1"), selected)
    }

    @Test
    fun aReopenedVoteDropsOutOfTheFrozenRecordRatherThanShowingALiveTally() {
        var v = closedAt(vote("v1"), 5)
        v = reopenVote(v)
        val view = buildSessionPrepView(
            quests = null, clocks = emptyArray(), events = null, hexContents = null,
            companionQuests = null, isGM = true,
            turnHistory = arrayOf(record(5, closedVoteIds = arrayOf("v1"))),
            councilVotes = arrayOf(v),
        )
        assertTrue(view.recentTurns.single().closedVotes.isEmpty(),
            "its numbers move again, so it is no longer a record of a decision")
    }

    @Test
    fun aVoteNobodyAnsweredNamesNoWinnerAndIsNotATie() {
        val v = closedAt(vote("v1"), 5)   // closed with zero ballots
        val view = buildSessionPrepView(
            quests = null, clocks = emptyArray(), events = null, hexContents = null,
            companionQuests = null, isGM = true,
            turnHistory = arrayOf(record(5, closedVoteIds = arrayOf("v1"))),
            councilVotes = arrayOf(v),
        )
        val line = view.recentTurns.single().closedVotes.single()
        assertNull(line.winnerLabel, "nothing to name")
        assertTrue(!line.isTie, "an all-zero board is not a tie -- nobody contested anything")
        assertEquals(0, line.totalBallots)
    }

    @Test
    fun linkableTurnsAreStrictlyLaterThanTheDecisionAndNewestFirst() {
        val history = arrayOf(record(3), record(5), record(6, notes = "the druids retaliated"), record(7))
        val turns = linkableTurnsFor(history, closedTurn = 5, alreadyLinked = arrayOf(7))
        assertEquals(listOf(7, 6), turns.map { it.turn }, "a consequence follows the decision, newest first")
        assertTrue(turns.first { it.turn == 7 }.linked, "an existing link comes back pre-checked")
        assertTrue(turns.first { it.turn == 6 }.label.contains("the druids retaliated"),
            "the label carries what the turn left behind so turns are distinguishable")
    }

    @Test
    fun anUnclosedVoteCanStillOfferEveryTurnRatherThanNone() {
        // closedTurn null (still open) must not filter everything away and strand the picker
        val turns = linkableTurnsFor(arrayOf(record(3), record(4)), closedTurn = null, alreadyLinked = null)
        assertEquals(listOf(4, 3), turns.map { it.turn })
    }
}
