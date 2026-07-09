package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.data.hex.HexContent

/**
 * Result of the encounter hex-state filter decision.
 */
enum class EncounterFilterDecision {
    /** Encounter proceeds normally. */
    ALLOW,
    /** Combat encounters are suppressed; non-combat categories proceed. */
    SUPPRESS_COMBAT,
    /** All encounters are suppressed (hex content override). */
    SUPPRESS_ALL
}

/**
 * Pure decision function for the camping encounter hex-state filter.
 *
 * This function implements the "Suppress Combat encounters in claimed+cleared hexes"
 * setting ([CampingData.filterByHexState]) combined with the per-hex content override
 * ([HexContent.suppressesEncounters]).
 *
 * Decision logic:
 * 1. If the rolled category is not COMBAT, always ALLOW (only combat is filterable).
 * 2. If [hexContentSuppresses] is true, SUPPRESS_ALL (content override wins, ignores filter toggle).
 * 3. If [filterEnabled] is false, ALLOW (toggle off = no filtering).
 * 4. If [hexClaimed] AND [hexCleared] are both true, SUPPRESS_COMBAT.
 * 5. Otherwise ALLOW.
 *
 * The homebrew setting [noRandomCombatInClaimedHexes] is checked separately by the caller
 * and takes precedence (see [RuleResolutionHelper.isRandomCombatSuppressedInClaimedHexes]).
 *
 * @param filterEnabled [CampingData.filterByHexState] — GM toggle "Suppress Combat in Cleared Hexes"
 * @param hexClaimed True if the party's current hex is claimed by the kingdom (from kingmaker.state.hexes)
 * @param hexCleared True if the party's current hex is cleared by the kingdom (from kingmaker.state.hexes)
 * @param hexContentSuppresses [HexContent.suppressesEncounters] for the current hex, or null if no content
 * @param rolledCategory The category that was rolled (from proxy table or weight picker)
 * @return Whether to allow, suppress combat only, or suppress all encounters
 */
fun decideEncounterFilter(
    filterEnabled: Boolean,
    hexClaimed: Boolean,
    hexCleared: Boolean,
    hexContentSuppresses: Boolean?,
    rolledCategory: EncounterCategory,
): EncounterFilterDecision {
    // Non-combat categories are never suppressed by this filter
    if (rolledCategory != EncounterCategory.COMBAT) {
        return EncounterFilterDecision.ALLOW
    }

    // Per-hex content override: suppresses regardless of the filter toggle
    if (hexContentSuppresses == true) {
        return EncounterFilterDecision.SUPPRESS_ALL
    }

    // Filter disabled: no suppression
    if (!filterEnabled) {
        return EncounterFilterDecision.ALLOW
    }

    // Filter enabled + claimed AND cleared hex -> suppress combat
    if (hexClaimed && hexCleared) {
        return EncounterFilterDecision.SUPPRESS_COMBAT
    }

    return EncounterFilterDecision.ALLOW
}