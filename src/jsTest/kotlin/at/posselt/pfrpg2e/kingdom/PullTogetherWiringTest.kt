package at.posselt.pfrpg2e.kingdom

import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PullTogetherWiringTest {
    private fun kingdom(dc: Int? = null, used: Boolean? = null): KingdomData =
        unsafeJso<dynamic> {
            pullTogetherCurrentDC = dc
            pullTogetherUsedThisTurn = used
        }.unsafeCast<KingdomData>()

    @Test
    fun anUntouchedKingdomStartsAtTheFeatsPrintedDc() {
        // Migration45 seeds 11, but a kingdom that predates it (or a fresh one) reads null, and the
        // feat's own text is the fallback rather than 0 -- a DC 0 flat check never fails.
        assertEquals(PULL_TOGETHER_BASE_DC, kingdom().pullTogetherDc())
        assertEquals(11, PULL_TOGETHER_BASE_DC)
    }

    @Test
    fun aStoredDcWins() {
        assertEquals(16, kingdom(dc = 16).pullTogetherDc())
    }

    @Test
    fun theOfferIsOncePerTurn() {
        assertTrue(canUsePullTogether(isCriticalFailure = true, alreadyUsedThisTurn = false))
        assertFalse(canUsePullTogether(isCriticalFailure = true, alreadyUsedThisTurn = true))
    }

    @Test
    fun theOfferOnlyAppliesToACriticalFailure() {
        // The feat is explicit: "when you roll a critical failure". A plain failure gets nothing.
        assertFalse(canUsePullTogether(isCriticalFailure = false, alreadyUsedThisTurn = false))
    }

    @Test
    fun usingItRaisesTheDcByFiveWhetherOrNotTheFlatCheckLanded() {
        // "The DC of this flat check increases by 5 each time you subsequently use it" -- use, not
        // success. A failed flat check still spends the attempt.
        assertEquals(16, pullTogetherDcAfterUse(11))
        assertEquals(21, pullTogetherDcAfterUse(16))
    }

    @Test
    fun anUnusedTurnDecaysTheDcTowardsElevenButNeverBelow() {
        assertEquals(15, pullTogetherDcAfterTurn(16, usedThisTurn = false))
        assertEquals(11, pullTogetherDcAfterTurn(11, usedThisTurn = false))
        // A turn it WAS used does not also decay -- that would refund half the cost immediately.
        assertEquals(16, pullTogetherDcAfterTurn(16, usedThisTurn = true))
    }

    @Test
    fun aFullEscalateThenDecayCycleReturnsToBase() {
        var dc = PULL_TOGETHER_BASE_DC
        dc = pullTogetherDcAfterUse(dc)
        assertEquals(16, dc)
        repeat(5) { dc = pullTogetherDcAfterTurn(dc, usedThisTurn = false) }
        assertEquals(11, dc)
        dc = pullTogetherDcAfterTurn(dc, usedThisTurn = false)
        assertEquals(11, dc)
    }

    @Test
    fun bothPrintingsOfTheFeatCount() {
        assertTrue("pull-together" in PULL_TOGETHER_FEATS)
        assertTrue("pull-together-vk" in PULL_TOGETHER_FEATS)
    }
}
