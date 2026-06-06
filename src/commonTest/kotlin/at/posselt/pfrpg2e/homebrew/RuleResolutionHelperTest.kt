package at.posselt.pfrpg2e.homebrew

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class RuleResolutionHelperTest {
    @Test
    fun `defaults when profile is null`() {
        // Ruin threshold
        assertEquals(10, RuleResolutionHelper.getRuinThreshold(null))
        // Event DC
        assertEquals(15, RuleResolutionHelper.getEventDc(null))
        // Event DC step
        assertEquals(0, RuleResolutionHelper.getEventDcStep(null))
        // Leadership activity cap without townhall
        assertEquals(6, RuleResolutionHelper.getLeadershipActivityCap(null, false))
        // Leadership activity cap with townhall
        assertEquals(8, RuleResolutionHelper.getLeadershipActivityCap(null, true))
        // Camping activity count defaults to 4 regardless of party size
        assertEquals(4, RuleResolutionHelper.getCampingActivityCount(null, 5))
        // Random combat suppression
        assertFalse(RuleResolutionHelper.isRandomCombatSuppressedInClaimedHexes(null))
        // Structure bonus cap
        assertFalse(RuleResolutionHelper.isStructureBonusCappedAtKingdomLevel(null))
        // Upgrade non-capital settlement
        assertFalse(RuleResolutionHelper.canUpgradeNonCapitalSettlement(null))
        // Capital grow one size larger
        assertFalse(RuleResolutionHelper.canCapitalGrowOneSizeLarger(null))
        // Vance and Kerenshara XP
        assertFalse(RuleResolutionHelper.useVanceAndKerensharaXp(null))
        // Cult of the Bloom events
        assertFalse(RuleResolutionHelper.getCultOfTheBloomEvents(null))
        // Travel cost river no bridge additional
        assertEquals(0, RuleResolutionHelper.getTravelCostRiverNoBridgeAdditional(null))
        // Paved streets reduce travel cost
        assertFalse(RuleResolutionHelper.doPavedStreetsReduceTravelCost(null))
        // Settlement influence radius
        assertEquals(0, RuleResolutionHelper.getSettlementInfluenceRadius(null))
        // Active profile handling
        assertEquals(null, RuleResolutionHelper.getActiveProfile(null))
    }

    @Test
    fun `gregory profile values`() {
        val gregoryProfile = HomebrewRulesProfile(
            id = "greg1",
            name = "Gregory",
            version = 1,
            isActive = true,
            createdAt = "2024-01-01",
            updatedAt = "2024-01-01",
            description = null,
            rules = HomebrewRules.gregory()
        )
        // Ruin threshold
        assertEquals(5, RuleResolutionHelper.getRuinThreshold(gregoryProfile))
        // Event DC
        assertEquals(5, RuleResolutionHelper.getEventDc(gregoryProfile))
        // Event DC step (unchanged from default)
        assertEquals(0, RuleResolutionHelper.getEventDcStep(gregoryProfile))
        // Leadership activity cap without townhall
        assertEquals(8, RuleResolutionHelper.getLeadershipActivityCap(gregoryProfile, false))
        // Leadership activity cap with townhall
        assertEquals(12, RuleResolutionHelper.getLeadershipActivityCap(gregoryProfile, true))
        // Camping activity count uses party size when flag is true
        assertEquals(7, RuleResolutionHelper.getCampingActivityCount(gregoryProfile, 7))
        // Random combat suppression
        assertTrue(RuleResolutionHelper.isRandomCombatSuppressedInClaimedHexes(gregoryProfile))
        // Structure bonus cap
        assertTrue(RuleResolutionHelper.isStructureBonusCappedAtKingdomLevel(gregoryProfile))
        // Upgrade non-capital settlement
        assertTrue(RuleResolutionHelper.canUpgradeNonCapitalSettlement(gregoryProfile))
        // Capital grow one size larger
        assertTrue(RuleResolutionHelper.canCapitalGrowOneSizeLarger(gregoryProfile))
        // Vance and Kerenshara XP
        assertTrue(RuleResolutionHelper.useVanceAndKerensharaXp(gregoryProfile))
        // Cult of the Bloom events
        assertTrue(RuleResolutionHelper.getCultOfTheBloomEvents(gregoryProfile))
        // Travel cost river no bridge additional
        assertEquals(1, RuleResolutionHelper.getTravelCostRiverNoBridgeAdditional(gregoryProfile))
        // Paved streets reduce travel cost
        assertTrue(RuleResolutionHelper.doPavedStreetsReduceTravelCost(gregoryProfile))
        // Settlement influence radius
        assertEquals(1, RuleResolutionHelper.getSettlementInfluenceRadius(gregoryProfile))
    }

    @Test
    fun `active profile resolution`() {
        val profileA = HomebrewRulesProfile(
            id = "a",
            name = "A",
            version = 1,
            isActive = false,
            createdAt = "2024-01-01",
            updatedAt = "2024-01-01",
            description = null,
            rules = HomebrewRules.none()
        )
        val profileB = HomebrewRulesProfile(
            id = "b",
            name = "B",
            version = 1,
            isActive = false,
            createdAt = "2024-01-01",
            updatedAt = "2024-01-01",
            description = null,
            rules = HomebrewRules.gregory()
        )
        val registry = HomebrewProfileRegistry(
            version = 1,
            activeProfileId = "b",
            profiles = listOf(profileA, profileB)
        )
        val active = RuleResolutionHelper.getActiveProfile(registry)
        assertEquals(profileB, active)
        // When activeProfileId is null, should return null
        val registryNoActive = HomebrewProfileRegistry(
            version = 1,
            activeProfileId = null,
            profiles = listOf(profileA, profileB)
        )
        assertEquals(null, RuleResolutionHelper.getActiveProfile(registryNoActive))
    }
}
