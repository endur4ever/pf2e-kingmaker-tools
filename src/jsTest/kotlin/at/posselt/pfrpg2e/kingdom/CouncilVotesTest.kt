package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.ABSTAIN_OPTION
import at.posselt.pfrpg2e.kingdom.data.RawCouncilVote
import at.posselt.pfrpg2e.kingdom.data.ballots
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CouncilVotesTest {
    private fun vote(
        id: String = "v1",
        options: Array<String> = arrayOf("Aye", "Nay"),
        closedTurn: Int? = null,
    ): RawCouncilVote {
        val obj = js("{}").unsafeCast<RawCouncilVote>()
        obj.id = id
        obj.question = "How do we answer the druids?"
        obj.options = options
        obj.closedTurn = closedTurn
        return obj
    }

    @Test
    fun castingRecordsOverwritesAndAbstains() {
        var v = castVote(vote(), "alice", 0)
        v = castVote(v, "bob", 1)
        assertEquals(mapOf("alice" to 0, "bob" to 1), v.ballots())
        // changing your vote while open is an overwrite, not a second entry
        v = castVote(v, "alice", 1)
        assertEquals(mapOf("alice" to 1, "bob" to 1), v.ballots())
        v = castVote(v, "alice", ABSTAIN_OPTION)
        assertEquals(ABSTAIN_OPTION, v.ballots()["alice"], "an explicit abstain is a recorded position")
    }

    @Test
    fun aMalformedIndexAndAClosedVoteBothBounceOff() {
        val open = vote()
        assertEquals(emptyMap(), castVote(open, "mallory", 7).ballots(), "out-of-range index is rejected, not clamped")
        assertEquals(emptyMap(), castVote(open, "mallory", -2).ballots(), "only -1 is the abstain sentinel")
        val closed = vote(closedTurn = 4)
        assertEquals(emptyMap(), castVote(closed, "alice", 0).ballots(), "a closed vote cannot gain ballots")
    }

    @Test
    fun closeIsIdempotentAndReopenActuallyClears() {
        val closed = closeVote(vote(), turn = 6, outcomeNote = "the council spoke")
        assertEquals(6, closed.closedTurn)
        assertEquals("the council spoke", closed.outcomeNote)
        // a stale card's second Close must not move the recorded turn
        assertEquals(6, closeVote(closed, turn = 9, outcomeNote = null).closedTurn)
        val reopened = reopenVote(closed)
        assertNull(reopened.closedTurn, "reopen clears the load-bearing null")
        assertEquals(9, closeVote(reopened, turn = 9, outcomeNote = null).closedTurn)
    }

    @Test
    fun linkingDeduplicatesAndUnlinkingIsANoOpWhenAbsent() {
        var v = linkConsequence(vote(), 12)
        v = linkConsequence(v, 12)
        v = linkConsequence(v, 15)
        assertEquals(listOf(12, 15), v.linkedRecordRefs?.toList())
        v = unlinkConsequence(v, 12)
        assertEquals(listOf(15), v.linkedRecordRefs?.toList())
        assertEquals(listOf(15), unlinkConsequence(v, 99).linkedRecordRefs?.toList())
    }

    @Test
    fun attributionTheBallotKeyIsTheSenderIdArgumentNeverThePayload() {
        // the adversarial shape: a clicking client could put anyone's userId in its payload, but
        // the handler passes only action.senderId here -- assert the ballot lands under it
        val out = applyCastToCouncilVotes(arrayOf(vote()), "v1", senderId = "socket-sender", optionIdx = 0)
        assertEquals(mapOf("socket-sender" to 0), out[0].ballots())
        // and only the addressed vote changes
        val two = applyCastToCouncilVotes(arrayOf(vote(), vote(id = "v2")), "v2", "s", 1)
        assertEquals(emptyMap(), two[0].ballots())
        assertEquals(mapOf("s" to 1), two[1].ballots())
    }

    @Test
    fun tallyBridgesTheRawShapeIntoTheCommonMath() {
        var v = vote(options = arrayOf("A", "B", "C"))
        v = castVote(v, "u1", 0)
        v = castVote(v, "u2", 0)
        v = castVote(v, "u3", 2)
        v = castVote(v, "u4", ABSTAIN_OPTION)
        val tally = tallyVote(v)
        assertEquals(listOf(2, 0, 1), tally.optionCounts)
        assertEquals(1, tally.abstentions)
        assertEquals(0, tally.decidedOption)
        assertTrue(!tally.isTie)
    }

    @Test
    fun theCastHandlerIsPlayerOriginableByDesign() {
        val handler = at.posselt.pfrpg2e.actions.handlers.CastCouncilVoteHandler()
        assertEquals("castCouncilVote", handler.action)
        assertEquals(at.posselt.pfrpg2e.actions.handlers.OriginatorPolicy.ANY, handler.originatorPolicy)
    }
}
