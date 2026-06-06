package at.posselt.pfrpg2e.homebrew

/**
 * Data class representing the set of homebrew rule overrides.
 * All fields have RAW default values; the `gregory()` companion method provides the house‑rules preset.
 */
data class HomebrewRules(
    val useVanceAndKerenshara: Boolean = false,
    val ruinThreshold: Int = 10,
    val eventDc: Int = 15,
    val eventDcStep: Int = 0,
    val leadershipActivityCap: Int = 6,
    val leadershipActivityCapWithTownhall: Int = 8,
    val canUpgradeNonCapital: Boolean = false,
    val campingActivityCountByPartySize: Boolean = false,
    val noRandomCombatInClaimedHexes: Boolean = false,
    val capStructureBonusAtKingdomLevel: Boolean = false,
    val capitalCanGrowOneSizeLarger: Boolean = false,
    val cultOfTheBloomEvents: Boolean = false,
    val travelCostRiverNoBridgeAdditional: Int = 0,
    val pavedStreetsReduceTravelCost: Boolean = false,
    val settlementInfluenceRadius: Int = 0,
) {
    companion object {
        fun none() = HomebrewRules()
        fun gregory() = HomebrewRules(
            useVanceAndKerenshara = true,
            ruinThreshold = 5,
            eventDc = 5,
            leadershipActivityCap = 8,
            leadershipActivityCapWithTownhall = 12,
            canUpgradeNonCapital = true,
            campingActivityCountByPartySize = true,
            noRandomCombatInClaimedHexes = true,
            capStructureBonusAtKingdomLevel = true,
            capitalCanGrowOneSizeLarger = true,
            cultOfTheBloomEvents = true,
            travelCostRiverNoBridgeAdditional = 1,
            pavedStreetsReduceTravelCost = true,
            settlementInfluenceRadius = 1,
        )
    }
}
