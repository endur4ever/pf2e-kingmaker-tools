package at.posselt.pfrpg2e.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefeatConsequencesTest {
    @Test
    fun minorDefeatIsModest() {
        val c = calculateDefeatConsequences(threatEscalation = 0, maxEscalation = 4, armiesLost = 0, settlementTargeted = false)
        assertEquals(1, c.unrestGain)          // just the loss
        assertEquals(2, c.pressureJump)        // base, escalation 0
        assertEquals(1, c.escalationBump)      // escalates
        assertFalse(c.spawnArrivalEvent)
    }

    @Test
    fun armiesLostAndSettlementTargetRaiseUnrest() {
        val c = calculateDefeatConsequences(threatEscalation = 2, maxEscalation = 4, armiesLost = 5, settlementTargeted = true)
        assertEquals(1 + 3 + 2, c.unrestGain)  // armies capped at +3, +2 for settlement
    }

    @Test
    fun pressureJumpScalesWithEscalationFraction() {
        val low = calculateDefeatConsequences(1, 4, 0, false).pressureJump
        val high = calculateDefeatConsequences(4, 4, 0, false).pressureJump
        assertTrue(high > low)
        assertEquals(2 + 3, high)              // full escalation -> 2 + 3
    }

    @Test
    fun maxEscalationDoesNotBumpFurther() {
        val c = calculateDefeatConsequences(threatEscalation = 4, maxEscalation = 4, armiesLost = 1, settlementTargeted = false)
        assertEquals(0, c.escalationBump)
    }

    @Test
    fun settlementThreatAtMaxEscalationArrives() {
        assertTrue(calculateDefeatConsequences(4, 4, 0, settlementTargeted = true).spawnArrivalEvent)
        // Not yet at max -> no arrival.
        assertFalse(calculateDefeatConsequences(3, 4, 0, settlementTargeted = true).spawnArrivalEvent)
        // At max but not settlement-targeting -> no arrival.
        assertFalse(calculateDefeatConsequences(4, 4, 0, settlementTargeted = false).spawnArrivalEvent)
    }

    @Test
    fun handlesDegenerateMaxEscalation() {
        // maxEscalation 0 is floored to 1; escalation clamped.
        val c = calculateDefeatConsequences(threatEscalation = 9, maxEscalation = 0, armiesLost = 0, settlementTargeted = false)
        assertEquals(0, c.escalationBump)      // clamped escalation == max(1)
        assertEquals(5, c.pressureJump)        // 2 + (1*3/1)
    }
}

/**
 * The card's contents. Each offer is applied individually by its own button, so this decides
 * exactly which buttons a defeat card shows.
 */
class DefeatOffersTest {
    private val full = DefeatConsequences(
        unrestGain = 3,
        pressureJump = 4,
        escalationBump = 1,
        spawnArrivalEvent = true,
    )

    @Test
    fun offersEveryNonZeroConsequenceInDisplayOrder() {
        val offers = defeatOffers(full)
        assertEquals(
            listOf(
                DEFEAT_OFFER_UNREST,
                DEFEAT_OFFER_PRESSURE,
                DEFEAT_OFFER_ESCALATION,
                DEFEAT_OFFER_ARRIVAL,
            ),
            offers.map { it.key },
        )
        assertEquals(listOf(3, 4, 1, 0), offers.map { it.amount })
    }

    @Test
    fun omitsZeroValuedConsequencesSoNoButtonIsANoOp() {
        // A threat already at max escalation bumps by 0 and, when it targets no settlement,
        // offers no arrival either.
        val maxed = full.copy(escalationBump = 0, spawnArrivalEvent = false)
        assertEquals(listOf(DEFEAT_OFFER_UNREST, DEFEAT_OFFER_PRESSURE), defeatOffers(maxed).map { it.key })
    }

    @Test
    fun alreadyAppliedConsequencesAreNotReOffered() {
        val remaining = defeatOffers(full, alreadyApplied = setOf(DEFEAT_OFFER_UNREST, DEFEAT_OFFER_ARRIVAL))
        assertEquals(listOf(DEFEAT_OFFER_PRESSURE, DEFEAT_OFFER_ESCALATION), remaining.map { it.key })
    }

    @Test
    fun applyingEverythingLeavesAnEmptyCard() {
        val all = defeatOffers(full).map { it.key }.toSet()
        assertTrue(defeatOffers(full, alreadyApplied = all).isEmpty())
    }

    @Test
    fun offerAmountsMatchTheCalculatorExactly() {
        // The button label and the applied delta both come from DefeatOffer.amount, so a drift
        // here would apply something other than what the GM was shown.
        val c = calculateDefeatConsequences(
            threatEscalation = 2,
            maxEscalation = 4,
            armiesLost = 2,
            settlementTargeted = true,
        )
        val byKey = defeatOffers(c).associate { it.key to it.amount }
        assertEquals(c.unrestGain, byKey[DEFEAT_OFFER_UNREST])
        assertEquals(c.pressureJump, byKey[DEFEAT_OFFER_PRESSURE])
        assertEquals(c.escalationBump, byKey[DEFEAT_OFFER_ESCALATION])
    }
}
