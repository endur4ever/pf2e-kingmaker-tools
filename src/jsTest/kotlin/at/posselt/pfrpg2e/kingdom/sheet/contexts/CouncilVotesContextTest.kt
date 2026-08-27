package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.data.kingdom.ABSTAIN_OPTION
import at.posselt.pfrpg2e.kingdom.castVote
import at.posselt.pfrpg2e.kingdom.data.RawCouncilVote
import at.posselt.pfrpg2e.kingdom.sheet.navigation.MainNavEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CouncilVotesContextTest {
    private fun vote(
        id: String? = "v1",
        options: Array<String> = arrayOf("Aye", "Nay"),
        closedTurn: Int? = null,
        note: String? = null,
    ): RawCouncilVote {
        val obj = js("{}").unsafeCast<RawCouncilVote>()
        obj.id = id
        obj.question = "How do we answer the druids?"
        obj.options = options
        obj.openedTurn = 3
        obj.closedTurn = closedTurn
        obj.outcomeNote = note
        return obj
    }

    private val names = mapOf("u1" to "Alice", "u2" to "Bob", "u3" to "Carol")

    private fun build(
        votes: Array<RawCouncilVote>?,
        isGM: Boolean = true,
        eligibleVoters: Int = 3,
    ) = buildCouncilVotesContext(
        votes = votes,
        isGM = isGM,
        eligibleVoters = eligibleVoters,
        voterNameOf = { names[it] },
    )

    @Test
    fun barsArePercentagesOfBallotsCastAndTheLeaderIsMarked() {
        var v = vote()
        v = castVote(v, "u1", 0)
        v = castVote(v, "u2", 0)
        v = castVote(v, "u3", 1)
        val row = build(arrayOf(v)).votes.single()
        // truncating, not rounding: bar widths only ever shrink by under a percent, and a
        // rounded pair can total 101% across two adjacent bars
        assertEquals(listOf(66, 33), row.options.map { it.pct }, "percent of votes cast, not of the table")
        assertEquals(listOf(true, false), row.options.map { it.isLeading })
        assertEquals(0, row.notVotedCount)
        assertTrue(row.isOpen)
    }

    @Test
    fun anEmptyVoteDrawsNoBarsAtAllRatherThanAFullOne() {
        // divide-by-zero territory: with no ballots every option must read 0%, not 100%
        val row = build(arrayOf(vote())).votes.single()
        assertEquals(listOf(0, 0), row.options.map { it.pct })
        assertFalse(row.options.any { it.isLeading }, "nobody leads a vote nobody answered")
        assertFalse(row.hasBallots)
        assertEquals(3, row.notVotedCount, "all three are outstanding")
    }

    @Test
    fun abstentionsAreShownAsAPositionAndExcludedFromTheBars() {
        var v = vote()
        v = castVote(v, "u1", ABSTAIN_OPTION)
        v = castVote(v, "u2", 0)
        val row = build(arrayOf(v)).votes.single()
        assertEquals(1, row.abstentions)
        assertEquals(listOf(100, 0), row.options.map { it.pct }, "an abstain is not a vote for anything")
        assertEquals(1, row.notVotedCount, "u3 still has not answered at all")
        val alice = row.ballots.first { it.voterName == "Alice" }
        assertTrue(alice.abstained)
        assertEquals("", alice.optionLabel)
    }

    @Test
    fun ballotsResolveNamesAndFallBackToTheRawIdRatherThanVanishing() {
        var v = vote()
        v = castVote(v, "u1", 0)
        v = castVote(v, "ghost-user", 1)   // a user who left the world
        val row = build(arrayOf(v)).votes.single()
        assertEquals(listOf("Alice", "ghost-user"), row.ballots.map { it.voterName })
        assertEquals("Nay", row.ballots.last().optionLabel)
    }

    @Test
    fun aShrunkenTableNeverRendersANegativeOutstandingCount() {
        var v = vote()
        v = castVote(v, "u1", 0)
        v = castVote(v, "u2", 0)
        v = castVote(v, "u3", 1)
        // three ballots on record, one player left since
        assertEquals(0, build(arrayOf(v), eligibleVoters = 2).votes.single().notVotedCount)
    }

    @Test
    fun theOutcomeNoteIsAbsentForPlayersNotMerelyHidden() {
        val v = vote(closedTurn = 7, note = "the druids were bluffing")
        assertEquals("the druids were bluffing", build(arrayOf(v)).votes.single().outcomeNote)
        val asPlayer = build(arrayOf(v), isGM = false).votes.single()
        assertNull(asPlayer.outcomeNote, "gated by absence of data, never by a template conditional")
        assertFalse(asPlayer.isOpen)
        assertEquals(7, asPlayer.closedTurn)
    }

    @Test
    fun aRowWithoutAnIdIsDroppedBecauseEveryControlAddressesItById() {
        val ctx = build(arrayOf(vote(id = null), vote(id = "real")))
        assertEquals(listOf("real"), ctx.votes.map { it.id })
        assertTrue(ctx.hasAny)
        assertFalse(build(null).hasAny)
        assertFalse(build(emptyArray()).hasAny)
    }

    @Test
    fun theNavValueMatchesTheStringTheSectionTemplateHidesItselfWith() {
        // page.hbs opens with {{#if (ne currentNavEntry 'councilVotes')}}hidden{{/if}} -- if the
        // enum's camelCase value ever drifts from that literal the tab renders on EVERY tab at
        // once, which no unit test would otherwise notice
        assertEquals("councilVotes", MainNavEntry.COUNCIL_VOTES.value)
        assertEquals(MainNavEntry.COUNCIL_VOTES, MainNavEntry.fromString("councilVotes"))
    }

    @Test
    fun anOpenTieMarksBothLeadersSoTheTableSeesTheDeadlock() {
        var v = vote()
        v = castVote(v, "u1", 0)
        v = castVote(v, "u2", 1)
        val row = build(arrayOf(v)).votes.single()
        assertTrue(row.isTie)
        assertEquals(listOf(true, true), row.options.map { it.isLeading })
    }
}
