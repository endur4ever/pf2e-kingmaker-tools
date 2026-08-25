package at.posselt.pfrpg2e.homebrew

object RuleResolutionHelper {
    fun getRuinThreshold(profile: HomebrewRulesProfile?): Int =
        profile?.rules?.ruinThreshold ?: HomebrewRules.none().ruinThreshold

    fun getEventDc(profile: HomebrewRulesProfile?): Int =
        profile?.rules?.eventDc ?: HomebrewRules.none().eventDc

    fun getEventDcStep(profile: HomebrewRulesProfile?): Int =
        profile?.rules?.eventDcStep ?: HomebrewRules.none().eventDcStep

    fun getLeadershipActivityCap(profile: HomebrewRulesProfile?, hasTownhallOrHigher: Boolean): Int {
        val rules = profile?.rules ?: return if (hasTownhallOrHigher) 8 else 6
        return if (hasTownhallOrHigher) rules.leadershipActivityCapWithTownhall else rules.leadershipActivityCap
    }

    fun getCampingActivityCount(profile: HomebrewRulesProfile?, partySize: Int): Int {
        val rules = profile?.rules ?: return 4 // RAW default: 4
        return if (rules.campingActivityCountByPartySize) partySize else 4
    }

    fun isRandomCombatSuppressedInClaimedHexes(profile: HomebrewRulesProfile?): Boolean =
        profile?.rules?.noRandomCombatInClaimedHexes ?: false

    fun isStructureBonusCappedAtKingdomLevel(profile: HomebrewRulesProfile?): Boolean =
        profile?.rules?.capStructureBonusAtKingdomLevel ?: false

    fun canUpgradeNonCapitalSettlement(profile: HomebrewRulesProfile?): Boolean =
        profile?.rules?.canUpgradeNonCapital ?: false

    fun canCapitalGrowOneSizeLarger(profile: HomebrewRulesProfile?): Boolean =
        profile?.rules?.capitalCanGrowOneSizeLarger ?: false

    fun useVanceAndKerensharaXp(profile: HomebrewRulesProfile?): Boolean =
        profile?.rules?.useVanceAndKerenshara ?: false

    fun getCultOfTheBloomEvents(profile: HomebrewRulesProfile?): Boolean =
        profile?.rules?.cultOfTheBloomEvents ?: false

    fun isSeasonalEconomyEnabled(profile: HomebrewRulesProfile?): Boolean =
        profile?.rules?.seasonalEconomyEnabled ?: false

    fun getTravelCostRiverNoBridgeAdditional(profile: HomebrewRulesProfile?): Int =
        profile?.rules?.travelCostRiverNoBridgeAdditional ?: 0

    fun doPavedStreetsReduceTravelCost(profile: HomebrewRulesProfile?): Boolean =
        profile?.rules?.pavedStreetsReduceTravelCost ?: false

    fun getSettlementInfluenceRadius(profile: HomebrewRulesProfile?): Int =
        profile?.rules?.settlementInfluenceRadius ?: 0

    fun getActiveProfile(registry: HomebrewProfileRegistry?): HomebrewRulesProfile? {
        if (registry == null) return null
        return registry.profiles.find { it.id == registry.activeProfileId }
    }
}
