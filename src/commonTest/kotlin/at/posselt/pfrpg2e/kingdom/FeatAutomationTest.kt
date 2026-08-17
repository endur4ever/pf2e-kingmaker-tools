package at.posselt.pfrpg2e.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatAutomationTest {
    // ── Pull Together ───────────────────────────────────────────────────────────────────────────
    @Test
    fun pullTogetherDcRisesByFivePerUse() {
        assertEquals(16, pullTogetherDcAfterUse(11))
        assertEquals(21, pullTogetherDcAfterUse(16))
    }

    @Test
    fun pullTogetherDcDecaysByOnePerUnusedTurnDownToBase() {
        assertEquals(20, pullTogetherDcAfterTurn(currentDc = 21, usedThisTurn = false))
        assertEquals(11, pullTogetherDcAfterTurn(currentDc = 11, usedThisTurn = false))  // floored at base
        assertEquals(21, pullTogetherDcAfterTurn(currentDc = 21, usedThisTurn = true))   // held when used
    }

    @Test
    fun pullTogetherOnlyOnCritFailOncePerTurn() {
        assertTrue(canUsePullTogether(isCriticalFailure = true, alreadyUsedThisTurn = false))
        assertFalse(canUsePullTogether(isCriticalFailure = false, alreadyUsedThisTurn = false))  // not a crit fail
        assertFalse(canUsePullTogether(isCriticalFailure = true, alreadyUsedThisTurn = true))    // already used
    }

    // ── Liquidate Resources ─────────────────────────────────────────────────────────────────────
    @Test
    fun liquidateAvailableWhenExpenseWouldZeroRp() {
        assertTrue(canLiquidateResources(currentRp = 3, expense = 3, alreadyUsedThisTurn = false))   // -> 0
        assertTrue(canLiquidateResources(currentRp = 2, expense = 5, alreadyUsedThisTurn = false))   // -> below 0
    }

    @Test
    fun liquidateUnavailableWhenExpenseSurvivable() {
        assertFalse(canLiquidateResources(currentRp = 10, expense = 3, alreadyUsedThisTurn = false)) // -> 7, fine
    }

    @Test
    fun liquidateOncePerTurnAndNeedsARealExpense() {
        assertFalse(canLiquidateResources(currentRp = 3, expense = 3, alreadyUsedThisTurn = true))
        assertFalse(canLiquidateResources(currentRp = 0, expense = 0, alreadyUsedThisTurn = false))  // no expense
    }

    @Test
    fun liquidateLeavesOneRpAndCostsFourRdNextTurn() {
        assertEquals(1, liquidatedRp())
        assertEquals(4, LIQUIDATE_RESOURCES_NEXT_TURN_RD_PENALTY)
    }

    @Test
    fun qualityOfLifeBoostsOnlyTheFirstLuxuryGainOfTheTurn() {
        assertEquals(1, qualityOfLifeLuxuryBonus(gained = 3, bonusPerTurn = 1, alreadyUsedThisTurn = false))
        assertEquals(0, qualityOfLifeLuxuryBonus(gained = 3, bonusPerTurn = 1, alreadyUsedThisTurn = true))
    }

    @Test
    fun aGainOfZeroLuxuriesIsNotAGain() {
        // The feat says "the first time you GAIN Luxury Commodities" -- collecting none must not
        // burn the turn's single use, and must not conjure a luxury out of nothing.
        assertEquals(0, qualityOfLifeLuxuryBonus(gained = 0, bonusPerTurn = 1, alreadyUsedThisTurn = false))
        assertEquals(0, qualityOfLifeLuxuryBonus(gained = -2, bonusPerTurn = 1, alreadyUsedThisTurn = false))
    }

    @Test
    fun aKingdomWithoutTheFeatGetsNothing() {
        assertEquals(0, qualityOfLifeLuxuryBonus(gained = 5, bonusPerTurn = 0, alreadyUsedThisTurn = false))
    }

    @Test
    fun envyIgnoresOnlyTheFirstIncreaseOfTheTurn() {
        // The old implementation was a bare `kingdom.level >= 20` check, which ignored EVERY Upkeep
        // unrest increase every turn -- far more generous than "the first time in a Kingdom turn".
        assertTrue(envyIgnoresIncrease(gained = 3, alreadyUsedThisTurn = false))
        assertFalse(envyIgnoresIncrease(gained = 3, alreadyUsedThisTurn = true))
    }

    @Test
    fun anIncreaseOfZeroDoesNotBurnTheFreeIgnore() {
        // A turn with no unrest gain must leave the ignore available for a later, real increase.
        assertFalse(envyIgnoresIncrease(gained = 0, alreadyUsedThisTurn = false))
        assertFalse(envyIgnoresIncrease(gained = -2, alreadyUsedThisTurn = false))
    }
}
