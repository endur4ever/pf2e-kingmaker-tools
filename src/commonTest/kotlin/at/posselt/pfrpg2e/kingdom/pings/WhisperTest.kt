package at.posselt.pfrpg2e.kingdom.pings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WhisperTest {
    private fun expLine(n: Int) = WhisperLine(
        kind = PingLineKind.EXPEDITION_RETURNING,
        labelKey = "kingdom.pings.card.expeditionReturning",
        labelArgs = mapOf("title" to "exp$n"),
        jumpKind = "sheet-tab",
        jumpValue = "expeditions",
    )

    @Test
    fun emptyReturnsNullNeverAnEmptyCard() {
        assertNull(composeWhisper(WhisperInputs(turn = 5, ownedRoles = emptySet())))
        // a spectator gets no card even when the kingdom has slots left
        assertNull(
            composeWhisper(
                WhisperInputs(turn = 5, ownedRoles = emptySet(), leadershipSlotsRemaining = 3)
            )
        )
    }

    @Test
    fun multiRoleFansOutOneLeaderCheckLinePerOwnedRole() {
        val card = composeWhisper(
            WhisperInputs(
                turn = 7,
                ownedRoles = setOf("ruler", "warden"),
                pendingCheckRoles = listOf("ruler", "warden", "magister"),
            )
        )!!
        val checks = card.lines.filter { it.kind == PingLineKind.LEADER_CHECK_PENDING }
        // magister pending but NOT owned by this user -> not their line
        assertEquals(listOf("ruler", "warden"), checks.map { it.labelArgs["role"] })
    }

    @Test
    fun slotsLineNeedsAnOwnedRoleAndAPositiveRemainder() {
        val owned = WhisperInputs(turn = 3, ownedRoles = setOf("ruler"), leadershipSlotsRemaining = 2)
        assertEquals(
            1,
            composeWhisper(owned)!!.lines.count { it.kind == PingLineKind.LEADERSHIP_SLOTS },
        )
        assertNull(
            composeWhisper(owned.copy(leadershipSlotsRemaining = 0)),
            "zero remaining is noise, not a nudge",
        )
        assertNull(composeWhisper(owned.copy(leadershipSlotsRemaining = null)))
    }

    @Test
    fun spectatorStillGetsExpeditionLines() {
        val card = composeWhisper(
            WhisperInputs(turn = 3, ownedRoles = emptySet(), expeditionLines = listOf(expLine(1)))
        )!!
        assertEquals(1, card.lines.size)
        assertEquals(PingLineKind.EXPEDITION_RETURNING, card.lines[0].kind)
    }

    @Test
    fun capTruncatesTheTailKeepingPersonalLinesFirst() {
        val card = composeWhisper(
            WhisperInputs(
                turn = 2,
                ownedRoles = setOf("ruler"),
                pendingCheckRoles = listOf("ruler"),
                leadershipSlotsRemaining = 1,
                expeditionLines = (1..10).map { expLine(it) },
            )
        )!!
        assertEquals(WHISPER_LINE_CAP, card.lines.size)
        assertEquals(PingLineKind.LEADER_CHECK_PENDING, card.lines[0].kind)
        assertEquals(PingLineKind.LEADERSHIP_SLOTS, card.lines[1].kind)
        // the tail that survives is the head of the expedition fan-out, in order
        assertEquals("exp1", card.lines[2].labelArgs["title"])
        assertEquals("exp6", card.lines.last().labelArgs["title"])
    }

    @Test
    fun readyFlagIsAStrictTurnMatch() {
        val base = WhisperInputs(turn = 4, ownedRoles = setOf("ruler"), leadershipSlotsRemaining = 1)
        assertTrue(composeWhisper(base.copy(readyForTurn = 4))!!.alreadyReady)
        assertFalse(composeWhisper(base.copy(readyForTurn = 3))!!.alreadyReady, "stale readiness never carries forward")
        assertFalse(composeWhisper(base.copy(readyForTurn = null))!!.alreadyReady)
        assertFalse(composeWhisper(base.copy(readyForTurn = 5))!!.alreadyReady, "future turn readiness is meaningless")
    }

    @Test
    fun cardCarriesTheTurnItWasComposedFor() {
        assertEquals(
            9,
            composeWhisper(
                WhisperInputs(turn = 9, ownedRoles = setOf("ruler"), leadershipSlotsRemaining = 1)
            )!!.turn,
        )
    }
}
