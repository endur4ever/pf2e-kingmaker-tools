package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.regions.getSeasonForMonth
import at.posselt.pfrpg2e.homebrew.HomebrewProfileRegistry
import at.posselt.pfrpg2e.homebrew.RuleResolutionHelper
import at.posselt.pfrpg2e.kingdom.seasonaleconomy.SeasonalEconomyModifiers
import at.posselt.pfrpg2e.settings.pfrpg2eKingdomCampingWeather
import at.posselt.pfrpg2e.kingdom.seasonaleconomy.seasonalModifiers
import at.posselt.pfrpg2e.utils.getCurrentMonth
import com.foundryvtt.core.Game

/**
 * The turn's seasonal economy modifiers, resolved once per surrounding operation
 * (seasonal-economy plan §3.3): season from the world date via the existing month derivation,
 * gate from the active homebrew profile. Deterministic for a given date, so the Turn Wizard
 * preview and the End-Turn commit read the same values.
 *
 * Fails NEUTRAL: a missing registry, an unparseable profile, or a calendar that cannot report a
 * month all resolve to [SeasonalEconomyModifiers.none] — a broken integration must never tax the
 * kingdom.
 */
fun Game.currentSeasonalModifiers(): SeasonalEconomyModifiers = runCatching {
    val registry = runCatching { settings.pfrpg2eKingdomCampingWeather.getHomebrewProfileRegistry() }
        .getOrNull()
        ?.let { HomebrewProfileRegistry.fromJson(it) }
    val profile = RuleResolutionHelper.getActiveProfile(registry)
    val enabled = RuleResolutionHelper.isSeasonalEconomyEnabled(profile)
    seasonalModifiers(getSeasonForMonth(getCurrentMonth().ordinal), enabled)
}.getOrDefault(SeasonalEconomyModifiers.none())
