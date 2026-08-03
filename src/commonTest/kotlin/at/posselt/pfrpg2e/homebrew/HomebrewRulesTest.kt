package at.posselt.pfrpg2e.homebrew

import kotlin.test.Test
import kotlin.test.assertEquals

class HomebrewRulesTest {
    @Test
    fun `none returns default values`() {
        val rules = HomebrewRules.none()
        assertEquals(false, rules.useVanceAndKerenshara)
        assertEquals(10, rules.ruinThreshold)
        assertEquals(15, rules.eventDc)
        assertEquals(0, rules.eventDcStep)
        assertEquals(6, rules.leadershipActivityCap)
        assertEquals(8, rules.leadershipActivityCapWithTownhall)
        assertEquals(false, rules.canUpgradeNonCapital)
        assertEquals(false, rules.campingActivityCountByPartySize)
        assertEquals(false, rules.noRandomCombatInClaimedHexes)
        assertEquals(false, rules.capStructureBonusAtKingdomLevel)
        assertEquals(false, rules.capitalCanGrowOneSizeLarger)
        assertEquals(false, rules.cultOfTheBloomEvents)
        assertEquals(0, rules.travelCostRiverNoBridgeAdditional)
        assertEquals(false, rules.pavedStreetsReduceTravelCost)
        assertEquals(0, rules.settlementInfluenceRadius)
    }

    @Test
    fun `gregory returns custom values`() {
        val rules = HomebrewRules.gregory()
        assertEquals(true, rules.useVanceAndKerenshara)
        assertEquals(5, rules.ruinThreshold)
        assertEquals(5, rules.eventDc)
        assertEquals(0, rules.eventDcStep)
        assertEquals(8, rules.leadershipActivityCap)
        assertEquals(12, rules.leadershipActivityCapWithTownhall)
        assertEquals(true, rules.canUpgradeNonCapital)
        assertEquals(true, rules.campingActivityCountByPartySize)
        assertEquals(true, rules.noRandomCombatInClaimedHexes)
        assertEquals(true, rules.capStructureBonusAtKingdomLevel)
        assertEquals(true, rules.capitalCanGrowOneSizeLarger)
        assertEquals(true, rules.cultOfTheBloomEvents)
        assertEquals(1, rules.travelCostRiverNoBridgeAdditional)
        assertEquals(true, rules.pavedStreetsReduceTravelCost)
        assertEquals(1, rules.settlementInfluenceRadius)
    }
}
