package at.posselt.pfrpg2e.camping.dialogs

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.FormElementContext
import at.posselt.pfrpg2e.app.forms.HiddenInput
import at.posselt.pfrpg2e.app.forms.Select
import at.posselt.pfrpg2e.app.forms.SelectOption
import at.posselt.pfrpg2e.camping.CampingActor
import at.posselt.pfrpg2e.camping.canBeFavoriteMeal
import at.posselt.pfrpg2e.camping.currentWorldDay
import at.posselt.pfrpg2e.camping.getActorsInCamp
import at.posselt.pfrpg2e.camping.getAllRecipes
import at.posselt.pfrpg2e.camping.getCamping
import at.posselt.pfrpg2e.camping.setCamping
import at.posselt.pfrpg2e.camping.shouldRowBePinned
import at.posselt.pfrpg2e.utils.asSequence
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.Game
import com.foundryvtt.core.abstract.DataModel
import com.foundryvtt.core.abstract.DocumentConstructionContext
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.data.dsl.buildSchema
import js.array.component1
import js.array.component2
import js.core.Void
import kotlinx.coroutines.await
import kotlinx.js.JsPlainObject
import org.w3c.dom.HTMLElement
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise

@JsExport
class FavoriteMealDataModel(
    value: AnyObject,
    options: DocumentConstructionContext?
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            array("meals") {
                schema {
                    string("actorUuid")
                    string("favoriteMeal", nullable = true)
                }
            }
        }
    }
}

@JsPlainObject
external interface FavoriteMealChoice {
    val actorUuid: String
    val favoriteMeal: String?
}

@JsPlainObject
external interface FavoriteMealRowContext {
    val actorUuid: String
    val name: String
    val image: String?
    val isPinned: Boolean
    val rationsPaidForTonight: Boolean
    val select: FormElementContext
    val hiddenActorUuid: FormElementContext
}

@JsPlainObject
external interface FavoriteMealContext : ValidatedHandlebarsContext {
    val formRows: Array<FormElementContext>
    val camperRows: Array<FavoriteMealRowContext>
}

@JsPlainObject
external interface FavoriteMealSubmitData {
    val meals: Array<FavoriteMealChoice>
}


@JsExport
class FavoriteMealsApplication(
    private val game: Game,
    private val actor: CampingActor,
) : FormApp<FavoriteMealContext, FavoriteMealSubmitData>(
    title = t("camping.favoriteMeals"),
    template = "applications/camping/favorite-meals.hbs",
    debug = true,
    dataModel = FavoriteMealDataModel::class.js,
    id = "kmFavoriteMeals-${actor.uuid}",
    width = 480,
) {
    private val unpinnedActorUuids = mutableSetOf<String>()

    private var meals: List<FavoriteMealChoice> = actor.getCamping()
        ?.cooking
        ?.actorMeals
        ?.asSequence()
        ?.map { (id, actorMeal) ->
            FavoriteMealChoice(
                actorUuid = actorMeal.actorUuid,
                favoriteMeal = actorMeal.favoriteMeal
            )
        }?.toList() ?: emptyList()

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (val action = target.dataset["action"]) {
            "unpin-meal" -> {
                val actorUuid = target.dataset["actorUuid"] ?: return
                buildPromise {
                    unpinnedActorUuids.add(actorUuid)
                    actor.getCamping()?.let { camping ->
                        camping.cooking.actorMeals.asSequence()
                            .map { it.component2() }
                            .find { it.actorUuid == actorUuid }
                            ?.let { meal ->
                                meal.fixedFavoriteMeal = false
                                actor.setCamping(camping)
                            }
                    }
                    render(force = true)
                }
            }

            "km-save" -> {
                buildPromise {
                    actor.getCamping()?.let { camping ->
                        val allowedActorUuids = camping.getActorsInCamp()
                            .filter { game.user.isGM || it.isOwner }
                            .map { it.uuid }
                            .toSet()
                        val mealsByActorUuid = meals.associateBy { it.actorUuid }
                        camping.cooking.actorMeals.asSequence()
                            .filter { it.component2().actorUuid in allowedActorUuids }
                            .forEach { (_, meal) ->
                                val picked = mealsByActorUuid[meal.actorUuid]?.favoriteMeal
                                val wasPinned = meal.fixedFavoriteMeal == true
                                val explicitlyUnpinned = meal.actorUuid in unpinnedActorUuids
                                val shouldPin = shouldRowBePinned(
                                    wasPinned = wasPinned,
                                    previousMeal = meal.favoriteMeal,
                                    pickedMeal = picked,
                                    explicitlyUnpinned = explicitlyUnpinned,
                                )
                                meal.favoriteMeal = picked
                                meal.fixedFavoriteMeal = shouldPin
                            }
                        actor.setCamping(camping)
                    }
                    close()
                }
            }

            else -> console.log(action)
        }
    }

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions
    ): Promise<FavoriteMealContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        val camping = actor.getCamping()
        val actors = camping
            ?.getActorsInCamp()
            ?.filter { game.user.isGM || it.isOwner }
            ?.associateBy { it.uuid } ?: emptyMap()
        val mealChoices = camping
            ?.getAllRecipes()
            ?.filter { it.canBeFavoriteMeal() }
            ?.sortedBy { it.name }
            ?.map { SelectOption(label = it.name, value = it.id) }
            ?: emptyList()

        val actorMealsByUuid = camping?.cooking?.actorMeals?.asSequence()
            ?.map { it.component2() }
            ?.associateBy { it.actorUuid } ?: emptyMap()

        val activeMeals = meals.filter { it.actorUuid in actors }
        val formRows = mutableListOf<FormElementContext>()
        val camperRows = mutableListOf<FavoriteMealRowContext>()

        activeMeals.forEachIndexed { index, meal ->
            val camper = actors[meal.actorUuid]
            val name = camper?.name ?: ""
            val image = camper?.img
            val existingMeal = actorMealsByUuid[meal.actorUuid]
            val wasPinned = existingMeal?.fixedFavoriteMeal == true
            val isExplicitlyUnpinned = meal.actorUuid in unpinnedActorUuids
            val isPinned = shouldRowBePinned(
                wasPinned = wasPinned,
                previousMeal = existingMeal?.favoriteMeal,
                pickedMeal = meal.favoriteMeal,
                explicitlyUnpinned = isExplicitlyUnpinned,
            )
            val rationsPaidForTonight = camping?.cooking?.rationsPaidForDay == currentWorldDay(game)

            val hiddenActorUuid = HiddenInput(
                name = "meals.$index.actorUuid",
                value = meal.actorUuid,
            ).toContext()

            val select = Select(
                label = name,
                hideLabel = true,
                name = "meals.$index.favoriteMeal",
                value = meal.favoriteMeal,
                options = mealChoices,
                stacked = false,
                required = false,
            ).toContext()

            formRows.add(hiddenActorUuid)
            formRows.add(select)

            camperRows.add(
                FavoriteMealRowContext(
                    actorUuid = meal.actorUuid,
                    name = name,
                    image = image,
                    isPinned = isPinned,
                    rationsPaidForTonight = rationsPaidForTonight,
                    select = select,
                    hiddenActorUuid = hiddenActorUuid,
                )
            )
        }

        FavoriteMealContext(
            partId = parent.partId,
            isFormValid = isFormValid,
            formRows = formRows.toTypedArray(),
            camperRows = camperRows.toTypedArray(),
        )
    }

    override fun onParsedSubmit(value: FavoriteMealSubmitData): Promise<Void> = buildPromise {
        meals = value.meals.toList()
        undefined
    }

}