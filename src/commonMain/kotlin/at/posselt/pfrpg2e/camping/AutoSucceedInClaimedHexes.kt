package at.posselt.pfrpg2e.camping

/**
 * Determines whether a camping activity should auto-succeed when the party is in a claimed hex.
 *
 * This implements the house rule from house-rules.md:
 * "You do not need to make a camp in your own lands and automatically succeed at cooking a meal"
 *
 * The predicate is pure and testable - it only depends on three boolean/string inputs:
 * - [toggleEnabled]: The homebrew setting "Auto-succeed camp preparation & cooking in claimed hexes"
 * - [hexClaimed]: Whether the party's current hex is claimed by the kingdom
 * - [activityId]: The ID of the camping activity being attempted
 *
 * Only affects two specific activities:
 * - [prepareCampsiteId] ("prepare-campsite"): Prepare Campsite
 * - [cookMealId] ("cook-meal"): Cook Meal
 *
 * When auto-succeed applies:
 * - Prepare Campsite skips its roll and applies the plain success result
 * - Cook Meal auto-succeeds (ingredient costs still paid; the house rule waives the ROLL, not the cost)
 * - Degree-dependent extras (crit tables) use the plain-success row
 * - A chat message "auto-success: camping in own lands" is posted (i18n key: "camping.autoSuccessInOwnLands")
 *
 * @return true if the activity should auto-succeed, false to use normal roll logic
 */
fun autoSucceedInClaimedHexes(
    toggleEnabled: Boolean,
    hexClaimed: Boolean,
    activityId: String,
): Boolean {
    if (!toggleEnabled) return false
    if (!hexClaimed) return false
    return when (activityId) {
        prepareCampsiteId, cookMealId -> true
        else -> false
    }
}