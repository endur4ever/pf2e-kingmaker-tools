package at.posselt.pfrpg2e.actions.handlers

import at.posselt.pfrpg2e.actions.ActionDispatcher
import at.posselt.pfrpg2e.actions.ActionMessage
import at.posselt.pfrpg2e.camping.CampingActor
import at.posselt.pfrpg2e.camping.FavoriteMealEligibility
import at.posselt.pfrpg2e.camping.MealChoice
import at.posselt.pfrpg2e.camping.applyConsumptionMealEffects
import at.posselt.pfrpg2e.camping.findCookingChoices
import at.posselt.pfrpg2e.camping.getActorsCarryingFood
import at.posselt.pfrpg2e.camping.getActorsInCamp
import at.posselt.pfrpg2e.camping.getAllRecipes
import at.posselt.pfrpg2e.camping.getCamping
import at.posselt.pfrpg2e.camping.getCompendiumFoodItems
import at.posselt.pfrpg2e.camping.recordCookingResult
import at.posselt.pfrpg2e.camping.reduceFoodBy
import at.posselt.pfrpg2e.camping.removeMealEffects
import at.posselt.pfrpg2e.camping.setCamping
import at.posselt.pfrpg2e.camping.shouldApplyFavoriteOutcome
import at.posselt.pfrpg2e.camping.sum
import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import at.posselt.pfrpg2e.fromCamelCase
import at.posselt.pfrpg2e.utils.fromUuidTypeSafe
import at.posselt.pfrpg2e.utils.postChatMessage
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import com.foundryvtt.pf2e.actor.PF2ECharacter
import js.objects.recordOf
import kotlinx.js.JsPlainObject

@JsPlainObject
external interface ApplyMealEffects {
    val recipeId: String
    val degree: String
    val campingActorUuid: String
}

class ApplyMealEffectsHandler(val game: Game) : ActionHandler("applyMealEffects", originatorPolicy = OriginatorPolicy.ANY) {
    override suspend fun execute(action: ActionMessage, dispatcher: ActionDispatcher) {
        val data = action.data.unsafeCast<ApplyMealEffects>()
        val campingActor = fromUuidTypeSafe<CampingActor>(data.campingActorUuid)
        val camping = campingActor?.getCamping() ?: return
        val recipesById = camping.getAllRecipes().associateBy { it.id }
        val degree = fromCamelCase<DegreeOfSuccess>(data.degree) ?: return
        val recipe = recipesById[data.recipeId] ?: return

        // reduce meal cost
        val charactersInCampByUuid = camping.getActorsInCamp()
            .filterIsInstance<PF2ECharacter>()
            .associateBy { it.uuid }
        val party = campingActor
        val parsed = camping.findCookingChoices(
            charactersInCampByUuid = charactersInCampByUuid,
            recipesById = recipesById
        )
        val mealChoices = parsed.meals
            .filterIsInstance<MealChoice.ParsedMeal>()
        val chosenMeals = mealChoices
            .filter { it.id == data.recipeId }
        val totalCost = chosenMeals.map { it.cookingCost }.sum()
        reduceFoodBy(
            actors = camping.getActorsCarryingFood(party),
            foodItems = getCompendiumFoodItems(),
            foodAmount = totalCost,
        )

        // and apply effects if not failure
        val outcome = when (degree) {
            DegreeOfSuccess.CRITICAL_FAILURE -> recipe.criticalFailure
            DegreeOfSuccess.SUCCESS -> recipe.success
            DegreeOfSuccess.CRITICAL_SUCCESS -> recipe.criticalSuccess
            else -> null
        } ?: return
        val actors = chosenMeals.map { it.actor }
        val actorUuids = actors.map { it.uuid }.toSet()

        // if the meal is a special meal, remove all other effects granted by special meals
        if (recipe.isSpecialMeal != false) {
            removeMealEffects(
                recipes = camping.getAllRecipes().filter { it.isSpecialMeal != false }.toList(),
                actors = actors,
                onlyRemoveAfterRest = false,
                removeWhenPreparingCampsite = false,
            )
        }

        applyConsumptionMealEffects(
            actors = actors,
            outcome = outcome,
        )

        // Record progression and determine favorite meal eligibility
        val eligibilities = mutableListOf<FavoriteMealEligibility>()
        for (mealChoice in chosenMeals) {
            val actor = mealChoice.actor
            val isPc = actor is PF2ECharacter
            val eligibility = camping.recordCookingResult(
                actorUuid = actor.uuid,
                recipeId = data.recipeId,
                recipeName = recipe.name,
                degree = degree,
                actorIsPc = isPc,
                actorType = null,
            )
            if (eligibility != null) {
                eligibilities.add(eligibility)
            }
        }

        // Apply favorite meal outcome only for actors who have qualified
        recipe.favoriteMeal?.let { favoriteOutcome ->
            val favoriteMealActors = chosenMeals
                .filter { meal ->
                    camping.shouldApplyFavoriteOutcome(
                        actorUuid = meal.actor.uuid,
                        recipeId = data.recipeId,
                    )
                }
                .map { it.actor }
            if (favoriteMealActors.isNotEmpty()) {
                applyConsumptionMealEffects(
                    actors = favoriteMealActors,
                    outcome = favoriteOutcome,
                )
            }
        }

        // Persist updated camping data (progression + favorite changes)
        campingActor.setCamping(camping)

        // Surface eligible favorite changes in chat
        for (eligibility in eligibilities) {
            val actorName = actors.find { it.uuid == eligibility.actorUuid }?.name ?: ""
            val i18nKey = if (eligibility.isNewFavorite) {
                "camping.favoriteMealLearned"
            } else {
                "camping.favoriteMealChanged"
            }
            val message = t(
                i18nKey,
                recordOf(
                    "actorName" to actorName,
                    "recipeName" to eligibility.recipeName,
                ),
            )
            postChatMessage(message, isHtml = true)
        }
    }
}
