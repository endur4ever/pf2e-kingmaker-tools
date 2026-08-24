package at.posselt.pfrpg2e.kingdom.seasonaleconomy

import at.posselt.pfrpg2e.data.regions.Season
import kotlin.math.roundToInt

/**
 * Pure core of the seasonal economy layer (`docs/plans/2026-07-09-plan-seasonal-economy.md`,
 * phase 1). The modifiers are a deterministic function of the season and the profile gate — no
 * clock, no randomness — which is what makes the End-Turn preview and commit agree (§3.5).
 *
 * The numbers are the plan's §3.2 table, deliberately small: a nudge, never a cliff. The whole
 * layer is profile-gated and DEFAULT OFF; [seasonalModifiers] returns neutral whenever the gate is
 * closed, so a kingdom that never opts in is arithmetically untouched.
 */
data class SeasonalEconomyModifiers(
    /** Autumn harvest: farmland food yield multiplier (§3.2, ×1.25 in FALL). */
    val farmlandFoodMultiplier: Double = 1.0,
    /** Winter extraction slowdown for lumber/ore/stone worksites (×0.9); luxuries and RP untouched. */
    val commodityWorksiteMultiplier: Double = 1.0,
    /** Winter heating and stores: one extra flat consumer. */
    val foodConsumptionDelta: Int = 0,
    /** Winter raids: added before the final clamp in caravanRaidDc, AFTER the road discount (§6). */
    val caravanRaidDcDelta: Int = 0,
    /** Frozen rivers: applied to the river-no-bridge surcharge, floored at 0 by the consumer (§6). */
    val riverCrossingCostDelta: Int = 0,
) {
    companion object {
        /** The identity: what every season gets when the profile gate is closed. */
        fun none(): SeasonalEconomyModifiers = SeasonalEconomyModifiers()
    }
}

/**
 * The §3.2 table. Spring and summer are neutral ON PURPOSE: spring's flood is a one-time OFFER
 * through the event system, never a standing multiplier, and summer's "war season" is flavour so
 * it can never double-count with the Army & War-Pressure system.
 */
fun seasonalModifiers(season: Season, enabled: Boolean): SeasonalEconomyModifiers {
    if (!enabled) return SeasonalEconomyModifiers.none()
    return when (season) {
        Season.SPRING, Season.SUMMER -> SeasonalEconomyModifiers.none()
        Season.FALL -> SeasonalEconomyModifiers(farmlandFoodMultiplier = 1.25)
        Season.WINTER -> SeasonalEconomyModifiers(
            commodityWorksiteMultiplier = 0.9,
            foodConsumptionDelta = 1,
            caravanRaidDcDelta = 2,
            riverCrossingCostDelta = -1,
        )
    }
}

/**
 * Applies a yield multiplier and returns to the integer ledger.
 *
 * roundToInt, not truncation, so the autumn bonus on a small farm is not silently eaten (6 food at
 * ×1.25 is 7.5 and must round to 8, not floor to 7 — the plan's "+2 to +3 on a mid-game realm"
 * arithmetic depends on it). Floored at zero: no multiplier can make a worksite produce negative
 * goods, however corrupt the input.
 */
fun applyWorksiteMultiplier(amount: Int, multiplier: Double): Int =
    (amount * multiplier).roundToInt().coerceAtLeast(0)
