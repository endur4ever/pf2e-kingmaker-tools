package at.posselt.pfrpg2e.gearsettings

/**
 * Resolution helper that all camping, kingdom, hex, and army systems call
 * instead of reading settings directly. Mirrors the homebrew
 * [at.posselt.pfrpg2e.homebrew.RuleResolutionHelper] pattern.
 *
 * When a gear-settings profile is active, its values take precedence over
 * RAW defaults. If no profile is active (null), RAW defaults are returned.
 */
object GearSettingsResolutionHelper {

    // ── Kingdom ────────────────────────────────────────────────────────

    fun getAdvancement(profile: GearSettingsProfile?): String =
        profile?.settings?.kingdom?.advancement ?: "xp"

    fun getRuinThreshold(profile: GearSettingsProfile?): Int =
        profile?.settings?.kingdom?.ruinThreshold ?: KingdomSettingsGroup().ruinThreshold

    fun getEventDc(profile: GearSettingsProfile?): Int =
        profile?.settings?.kingdom?.eventDc ?: KingdomSettingsGroup().eventDc

    fun getEventDcStep(profile: GearSettingsProfile?): Int =
        profile?.settings?.kingdom?.eventDcStep ?: KingdomSettingsGroup().eventDcStep

    fun getLeadershipActivityCap(
        profile: GearSettingsProfile?,
        hasTownhallOrHigher: Boolean,
    ): Int {
        val kingdom = profile?.settings?.kingdom ?: return if (hasTownhallOrHigher) 8 else 6
        return if (hasTownhallOrHigher)
            kingdom.leadershipActivityCapWithTownhall
        else
            kingdom.leadershipActivityCap
    }

    fun canUpgradeNonCapitalSettlement(profile: GearSettingsProfile?): Boolean =
        profile?.settings?.kingdom?.canUpgradeNonCapital ?: false

    fun canCapitalGrowOneSizeLarger(profile: GearSettingsProfile?): Boolean =
        profile?.settings?.kingdom?.capitalCanGrowOneSizeLarger ?: false

    fun isRandomCombatSuppressedInClaimedHexes(profile: GearSettingsProfile?): Boolean =
        profile?.settings?.kingdom?.noRandomCombatInClaimedHexes ?: false

    fun isStructureBonusCappedAtKingdomLevel(profile: GearSettingsProfile?): Boolean =
        profile?.settings?.kingdom?.capStructureBonusAtKingdomLevel ?: false

    fun getSettlementInfluenceRadius(profile: GearSettingsProfile?): Int =
        profile?.settings?.kingdom?.settlementInfluenceRadius ?: 0

    fun getCultOfTheBloomEvents(profile: GearSettingsProfile?): Boolean =
        profile?.settings?.kingdom?.cultOfTheBloomEvents ?: false

    // ── Camping ────────────────────────────────────────────────────────

    fun getCampingActivityCount(profile: GearSettingsProfile?, partySize: Int): Int {
        val camping = profile?.settings?.camping ?: return 4
        return if (camping.campingActivityCountByPartySize) partySize else 4
    }

    fun isShelteredEnabled(profile: GearSettingsProfile?): Boolean =
        profile?.settings?.camping?.enableSheltered ?: false

    fun isWeatherEnabled(profile: GearSettingsProfile?): Boolean =
        profile?.settings?.camping?.enableWeather ?: true

    // ── Hex ────────────────────────────────────────────────────────────

    fun getTravelCostRiverNoBridgeAdditional(profile: GearSettingsProfile?): Int =
        profile?.settings?.hex?.travelCostRiverNoBridgeAdditional ?: 0

    fun doPavedStreetsReduceTravelCost(profile: GearSettingsProfile?): Boolean =
        profile?.settings?.hex?.pavedStreetsReduceTravelCost ?: false

    fun isHexMapEnabled(profile: GearSettingsProfile?): Boolean =
        profile?.settings?.hex?.hexMapEnabled ?: true

    // ── Army ───────────────────────────────────────────────────────────

    fun isCombatTracksEnabled(profile: GearSettingsProfile?): Boolean =
        profile?.settings?.army?.enableCombatTracks ?: true

    // ── UI ─────────────────────────────────────────────────────────────

    fun shouldHideBuiltinKingdomSheet(profile: GearSettingsProfile?): Boolean =
        profile?.settings?.ui?.hideBuiltinKingdomSheet ?: false

    fun isPartyActorIconsEnabled(profile: GearSettingsProfile?): Boolean =
        profile?.settings?.ui?.enablePartyActorIcons ?: true

    fun isWeatherSoundFxEnabled(profile: GearSettingsProfile?): Boolean =
        profile?.settings?.ui?.enableWeatherSoundFx ?: true

    fun isTokenMappingEnabled(profile: GearSettingsProfile?): Boolean =
        profile?.settings?.ui?.enableTokenMapping ?: true

    fun isFirstRunMessageDisabled(profile: GearSettingsProfile?): Boolean =
        profile?.settings?.ui?.disableFirstRunMessage ?: false

    // ── Profile resolution ─────────────────────────────────────────────

    /**
     * Returns the active profile from the registry, or null if none is active
     * (which means RAW defaults apply).
     */
    fun getActiveProfile(registry: GearSettingsProfileRegistry?): GearSettingsProfile? {
        if (registry == null) return null
        return registry.profiles.find { it.id == registry.activeProfileId }
    }
}
