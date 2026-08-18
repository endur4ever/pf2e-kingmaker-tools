package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.settlements.SettlementSizeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImproveSettlementTest {
    @Test
    fun tierProgressionStopsAtMetropolis() {
        assertEquals(SettlementSizeType.TOWN, nextSettlementTier(SettlementSizeType.VILLAGE))
        assertEquals(SettlementSizeType.CITY, nextSettlementTier(SettlementSizeType.TOWN))
        assertEquals(SettlementSizeType.METROPOLIS, nextSettlementTier(SettlementSizeType.CITY))
        assertNull(nextSettlementTier(SettlementSizeType.METROPOLIS))
    }

    @Test
    fun costsMatchTheHouseRulesTable() {
        assertEquals(ImproveSettlementCost(5, 5, 5, 1, 10), improveSettlementCost(SettlementSizeType.TOWN))
        assertEquals(ImproveSettlementCost(10, 10, 10, 3, 25), improveSettlementCost(SettlementSizeType.CITY))
        assertEquals(ImproveSettlementCost(20, 20, 20, 5, 50), improveSettlementCost(SettlementSizeType.METROPOLIS))
        assertNull(improveSettlementCost(SettlementSizeType.VILLAGE))
    }

    @Test
    fun purchaseLevelsMatchTheDoc() {
        assertEquals(3, improveSettlementPurchaseLevel(SettlementSizeType.TOWN))
        assertEquals(9, improveSettlementPurchaseLevel(SettlementSizeType.CITY))
        assertEquals(15, improveSettlementPurchaseLevel(SettlementSizeType.METROPOLIS))
    }

    @Test
    fun capitalCannotBeImproved() {
        assertFalse(
            canImproveSettlement(
                currentTier = SettlementSizeType.VILLAGE, isCapital = true, kingdomLevel = 20,
                availableStone = 99, availableOre = 99, availableLumber = 99, availableLuxuries = 99, availableRp = 99,
            ),
        )
    }

    @Test
    fun metropolisCannotBeImprovedFurther() {
        assertFalse(
            canImproveSettlement(
                currentTier = SettlementSizeType.METROPOLIS, isCapital = false, kingdomLevel = 20,
                availableStone = 99, availableOre = 99, availableLumber = 99, availableLuxuries = 99, availableRp = 99,
            ),
        )
    }

    @Test
    fun affordableUpgradeIsAllowedExactlyAtCost() {
        // Village -> Town costs 5/5/5/1/10 exactly.
        assertTrue(
            canImproveSettlement(
                currentTier = SettlementSizeType.VILLAGE, isCapital = false, kingdomLevel = 20,
                availableStone = 5, availableOre = 5, availableLumber = 5, availableLuxuries = 1, availableRp = 10,
            ),
        )
    }

    @Test
    fun insufficientAnyResourceBlocksTheUpgrade() {
        // One RP short of the Village->Town cost.
        assertFalse(
            canImproveSettlement(
                currentTier = SettlementSizeType.VILLAGE, isCapital = false, kingdomLevel = 20,
                availableStone = 5, availableOre = 5, availableLumber = 5, availableLuxuries = 1, availableRp = 9,
            ),
        )
        // One luxury short.
        assertFalse(
            canImproveSettlement(
                currentTier = SettlementSizeType.VILLAGE, isCapital = false, kingdomLevel = 20,
                availableStone = 5, availableOre = 5, availableLumber = 5, availableLuxuries = 0, availableRp = 10,
            ),
        )
    }

    @Test
    fun aKingdomTooLowLevelCannotUpgradeEvenWithFullCoffers() {
        // "(kingdom level restrictions still apply)" -- previously deferred to "the existing
        // settlement-size logic", which never re-checked it on this path, so a level-1 kingdom
        // could buy a Town.
        assertFalse(
            canImproveSettlement(
                currentTier = SettlementSizeType.VILLAGE, isCapital = false, kingdomLevel = 2,
                availableStone = 99, availableOre = 99, availableLumber = 99, availableLuxuries = 99, availableRp = 99,
            ),
        )
    }

    @Test
    fun theLevelThresholdIsInclusive() {
        // Town requires exactly kingdom level 3.
        assertTrue(
            canImproveSettlement(
                currentTier = SettlementSizeType.VILLAGE, isCapital = false, kingdomLevel = 3,
                availableStone = 99, availableOre = 99, availableLumber = 99, availableLuxuries = 99, availableRp = 99,
            ),
        )
    }

    @Test
    fun eachTierUsesTheSharedSizeTablesLevelRequirement() {
        // Read from settlementSizeData rather than duplicated here, so the house rule and the
        // vanilla size table cannot drift into disagreeing about when a tier is legal.
        assertEquals(3, requiredKingdomLevelFor(SettlementSizeType.TOWN))
        assertEquals(9, requiredKingdomLevelFor(SettlementSizeType.CITY))
        assertEquals(15, requiredKingdomLevelFor(SettlementSizeType.METROPOLIS))
    }

    @Test
    fun higherTiersGateOnTheirOwnLevel() {
        // Level 9 allows City but not Metropolis.
        assertTrue(
            canImproveSettlement(
                currentTier = SettlementSizeType.TOWN, isCapital = false, kingdomLevel = 9,
                availableStone = 99, availableOre = 99, availableLumber = 99, availableLuxuries = 99, availableRp = 99,
            ),
        )
        assertFalse(
            canImproveSettlement(
                currentTier = SettlementSizeType.CITY, isCapital = false, kingdomLevel = 9,
                availableStone = 99, availableOre = 99, availableLumber = 99, availableLuxuries = 99, availableRp = 99,
            ),
        )
    }
}
