package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import js.objects.recordOf
import kotlinx.js.JsPlainObject
import at.posselt.pfrpg2e.utils.asSequence
import js.array.component2

/**
 * Rules from Kingmaker Companion Guide p.113:
 * - A PC learns a favorite meal after either 1 critical success OR 2 successes with that meal.
 * - Changing a favorite requires 2 critical successes on the new meal.
 * - NPC fixed favorite meals are never auto-changed.
 * - The favorite meal outcome is only applied once the actor qualifies.
 */

const val SUCCESSES_TO_LEARN = 2
const val CRITS_TO_CHANGE = 2

/**
 * Result of evaluating favorite meal progression after a cooking outcome.
 */
@JsPlainObject
external interface FavoriteMealEligibility {
    val actorUuid: String
    val recipeId: String
    val recipeName: String
    val isNewFavorite: Boolean
    val isChange: Boolean
}

/**
 * Increment the progression counters for a given actor + recipe based on the
 * degree of success, then persist the updated [CampingData].
 *
 * Returns a [FavoriteMealEligibility] if the actor now qualifies for a new
 * (or changed) favorite meal, or null if no threshold was crossed.
 */
fun CampingData.recordCookingResult(
    actorUuid: String,
    recipeId: String,
    recipeName: String,
    degree: DegreeOfSuccess,
    actorIsPc: Boolean,
    actorType: String?,
): FavoriteMealEligibility? {
    // Only track progression for PCs; NPCs with fixed favorites never auto-change
    if (!actorIsPc) return null

    if (degree != DegreeOfSuccess.SUCCESS && degree != DegreeOfSuccess.CRITICAL_SUCCESS) {
        return null
    }

    val progress = cooking.favoriteMealProgress ?: recordOf()
    val actorProgress = progress[actorUuid] ?: recordOf()
    val key = recipeId
    val current = actorProgress[key]
    val newSuccessCount = (current?.successCount ?: 0) +
        if (degree == DegreeOfSuccess.SUCCESS) 1 else 0
    val newCritCount = (current?.criticalSuccessCount ?: 0) +
        if (degree == DegreeOfSuccess.CRITICAL_SUCCESS) 1 else 0

    val updated = FavoriteMealProgression(
        successCount = newSuccessCount,
        criticalSuccessCount = newCritCount,
    )
    actorProgress[key] = updated
    progress[actorUuid] = actorProgress
    cooking.favoriteMealProgress = progress

    // actorMeals is keyed by actor.id at every writer (CampingSheet assigns actorMeals[actorId]),
    // so a uuid lookup always missed and no character ever learned a favourite meal. Match on the
    // record's own actorUuid field instead, which is correct whichever key the row is filed under.
    val actorMeal = cooking.actorMeals.asSequence().map { it.component2() }.find { meal -> meal.actorUuid == actorUuid } ?: return null
    val currentFavorite = actorMeal.favoriteMeal

    // NPC fixed favorite: never auto-change
    if (actorMeal.fixedFavoriteMeal == true) return null

    // Determine if actor qualifies
    val qualifiesAsNewFavorite = newCritCount >= 1 ||
        newSuccessCount >= SUCCESSES_TO_LEARN

    if (currentFavorite == null) {
        // No favorite yet — learn one
        if (qualifiesAsNewFavorite) {
            actorMeal.favoriteMeal = recipeId
            return FavoriteMealEligibility(
                actorUuid = actorUuid,
                recipeId = recipeId,
                recipeName = recipeName,
                isNewFavorite = true,
                isChange = false,
            )
        }
    } else if (currentFavorite != recipeId) {
        // Has a different favorite — need 2 crits to change
        if (newCritCount >= CRITS_TO_CHANGE) {
            actorMeal.favoriteMeal = recipeId
            return FavoriteMealEligibility(
                actorUuid = actorUuid,
                recipeId = recipeId,
                recipeName = recipeName,
                isNewFavorite = false,
                isChange = true,
            )
        }
    }
    // Already has this recipe as favorite, or hasn't crossed threshold yet
    return null
}

/**
 * Check whether the favorite meal outcome should be applied for a given
 * actor + recipe. The favorite outcome is only applied when the actor has
 * already qualified (i.e. the recipe is their current favorite).
 */
fun CampingData.shouldApplyFavoriteOutcome(
    actorUuid: String,
    recipeId: String,
): Boolean {
    val actorMeal = cooking.actorMeals.asSequence().map { it.component2() }.find { meal -> meal.actorUuid == actorUuid } ?: return false
    return actorMeal.favoriteMeal == recipeId
}
