package at.posselt.pfrpg2e.kingdom.loot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WealthLevelTest {
    @Test
    fun theCumulativeCurveIsDerivedFromThePerLevelTableAndOnlyRises() {
        assertEquals(TREASURE_PER_LEVEL_GP.size, PARTY_WEALTH_BY_LEVEL.size)
        assertEquals(TREASURE_PER_LEVEL_GP[0], PARTY_WEALTH_BY_LEVEL[0])
        assertEquals(
            TREASURE_PER_LEVEL_GP[0] + TREASURE_PER_LEVEL_GP[1],
            PARTY_WEALTH_BY_LEVEL[1],
            1e-9,
        )
        assertEquals(
            TREASURE_PER_LEVEL_GP.sum(),
            PARTY_WEALTH_BY_LEVEL.last(),
            1e-9,
        )
        assertTrue(PARTY_WEALTH_BY_LEVEL.zipWithNext().all { (a, b) -> b > a }, "strictly increasing")
    }

    @Test
    fun anEmptyHoardIsStillLevelOneNotLevelZero() {
        assertEquals(1, wealthLevelForGp(0.0))
        assertEquals(1, wealthLevelForGp(-500.0), "corrupt negative totals cannot go below the floor")
        assertEquals(1, wealthLevelForGp(PARTY_WEALTH_BY_LEVEL[0] - 0.01))
    }

    @Test
    fun aThresholdIsReachedExactlyAtItsValue() {
        assertEquals(1, wealthLevelForGp(PARTY_WEALTH_BY_LEVEL[0]), "level-1 wealth means level 1")
        assertEquals(2, wealthLevelForGp(PARTY_WEALTH_BY_LEVEL[1]))
        assertEquals(2, wealthLevelForGp(PARTY_WEALTH_BY_LEVEL[2] - 0.01), "just short stays put")
        assertEquals(3, wealthLevelForGp(PARTY_WEALTH_BY_LEVEL[2]))
    }

    @Test
    fun theCurveCapsRatherThanExtrapolatingForever() {
        val max = PARTY_WEALTH_BY_LEVEL.size
        assertEquals(max, wealthLevelForGp(PARTY_WEALTH_BY_LEVEL.last()))
        assertEquals(max, wealthLevelForGp(PARTY_WEALTH_BY_LEVEL.last() * 1000))
    }

    @Test
    fun pacingInputSumsTheWholeLedgerNotOneTurn() {
        val ledger = listOf(
            TreasureLedgerEntry("a", turn = 1, sourceHexKey = "1", itemNames = listOf("x"), totalGp = 300.0, cursedCount = 0),
            TreasureLedgerEntry("b", turn = 9, sourceHexKey = "2", itemNames = listOf("y"), totalGp = 200.0, cursedCount = 0),
        )
        val input = pacingLootInput(ledger, partyLevel = 3, turn = 9)
        assertEquals(wealthLevelForGp(500.0), input.impliedWealthLevel)
        assertEquals(3, input.partyLevel)
        assertEquals(9, input.turn)
        assertEquals(1, pacingLootInput(emptyList(), 3, 9).impliedWealthLevel, "an empty ledger is level 1")
    }
}
