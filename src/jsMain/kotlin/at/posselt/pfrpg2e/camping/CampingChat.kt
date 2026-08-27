package at.posselt.pfrpg2e.camping

import com.foundryvtt.core.ui
import at.posselt.pfrpg2e.actions.handlers.RumorOfferData
import at.posselt.pfrpg2e.actions.ActionDispatcher
import at.posselt.pfrpg2e.actions.ActionMessage
import at.posselt.pfrpg2e.actions.handlers.ApplyMealEffects
import at.posselt.pfrpg2e.actions.handlers.ApplyStarvation
import at.posselt.pfrpg2e.actions.handlers.GainProvisions
import at.posselt.pfrpg2e.actions.handlers.LearnSpecialRecipeData
import at.posselt.pfrpg2e.data.checks.RollMode
import at.posselt.pfrpg2e.utils.bindChatClick
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.fromUuidTypeSafe
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.Game
import js.objects.recordOf
import kotlinx.coroutines.await
import org.w3c.dom.get

suspend fun postPassTimeMessage(message: String, hours: Int) {
    postChatTemplate(
        templatePath = "chatmessages/pass-time.hbs",
        templateContext = recordOf(
            "message" to message,
            "seconds" to hours * 3600,
            "label" to t("camping.hours", recordOf("count" to hours))
        ),
        rollMode = RollMode.GMROLL,
    )
}

fun bindCampingChatEventListeners(game: Game, dispatcher: ActionDispatcher) {
    bindChatClick(".km-add-recipe") { _, el, _ ->
        val actorUuid = el.dataset["actorUuid"]
        val id = el.dataset["id"]
        val degree = el.dataset["degree"]
        val campingActorUuid = el.dataset["campingActorUuid"]
        if (actorUuid != null && degree != null && id != null && campingActorUuid != null) {
            buildPromise {
                dispatcher.dispatch(
                    ActionMessage(
                        action = "learnSpecialRecipe",
                        data = LearnSpecialRecipeData(
                            actorUuid = actorUuid,
                            id = id,
                            degree = degree,
                            campingActorUuid = campingActorUuid,
                        ).unsafeCast<AnyObject>()
                    )
                )
            }
        }
    }
    // rumor offer buttons (plan section 6.1): camping-side, dispatched through the
    // deny-by-default ActionDispatcher -- GM_ONLY originator by default, and the cards are
    // GM-whispered anyway. Identity is pinned on the card; the handlers act on what the GM read.
    for (spec in listOf(
        ".km-offer-rumor-beat" to "postRumorBeat",
        ".km-offer-rumor-quest" to "convertRumorQuest",
        ".km-offer-rumor-hex" to "convertRumorHex",
    )) {
        val (selector, actionName) = spec
        bindChatClick(selector) { _, el, _ ->
            val campingActorUuid = el.dataset["campingActorUuid"]
            val rumorId = el.dataset["rumorId"]
            if (campingActorUuid != null && rumorId != null) {
                buildPromise {
                    dispatcher.dispatch(
                        ActionMessage(
                            action = actionName,
                            data = RumorOfferData(
                                campingActorUuid = campingActorUuid,
                                rumorId = rumorId,
                                beatKey = el.dataset["beatKey"],
                                hexKey = el.dataset["hexKey"],
                                regionPin = el.dataset["regionPin"],
                            ).unsafeCast<AnyObject>(),
                        )
                    )
                }
            }
        }
    }
    bindChatClick(".km-offer-rumor-quiet") { _, el, _ ->
        // posting already stamped beatOfferedDay, so Quiet is a pure acknowledgement -- a silent
        // button reads as broken, nothing more is needed
        ui.notifications.info(t("camping.rumors.expiredOffer.faded"))
    }
    bindChatClick(".km-offer-rumor-dismiss") { _, el, _ ->
        ui.notifications.info(t("camping.rumors.convertOffer.dismissed"))
    }

    bindChatClick(".km-pass-time") { _, el, _ ->
        el.dataset["seconds"]?.toInt()?.let {
            buildPromise {
                game.time.advance(it).await()
            }
        }
    }
    bindChatClick(".km-random-encounter") { _, el, _ ->
        el.dataset["campingActorUuid"]?.let {
            buildPromise {
                fromUuidTypeSafe<CampingActor>(it)?.let { actor ->
                    buildPromise {
                        rollRandomEncounter(game, actor, true)
                    }
                }
            }
        }
    }
    bindChatClick(".gain-provisions") { _, el, _ ->
        buildPromise {
            val actorUuid = el.dataset["actorUuid"]
            val quantity = el.dataset["quantity"]?.toInt() ?: 0
            if (quantity > 0 && actorUuid != null) {
                dispatcher.dispatch(
                    ActionMessage(
                        action = "gainProvisions",
                        data = GainProvisions(
                            quantity = quantity,
                            actorUuid = actorUuid,
                        ).unsafeCast<AnyObject>()
                    )
                )
            }
        }
    }
    bindChatClick(".km-offer-starvation") { _, el, _ ->
        // The handler is GM-only by policy; this guard makes a player's click a silent no-op
        // instead of a rejected socket message.
        if (!game.user.isGM) return@bindChatClick
        buildPromise {
            val actorUuid = el.dataset["actorUuid"]
            val condition = el.dataset["condition"]
            if (actorUuid != null && condition != null) {
                dispatcher.dispatch(
                    ActionMessage(
                        action = "applyStarvation",
                        data = ApplyStarvation(
                            actorUuid = actorUuid,
                            condition = condition,
                            messageKey = el.dataset["messageKey"],
                        ).unsafeCast<AnyObject>()
                    )
                )
            }
        }
    }
    bindChatClick(".km-add-food") { _, el, _ ->
        el.dataset["campingActorUuid"]?.let {
            val action = ActionMessage(
                action = "addHuntAndGatherResult",
                data = HuntAndGatherData(
                    actorUuid = el.dataset["actorUuid"] as String,
                    basicIngredients = el.dataset["basicIngredients"]?.toInt() ?: 0,
                    specialIngredients = el.dataset["specialIngredients"]?.toInt() ?: 0,
                    campingActorUuid = it,
                ).unsafeCast<AnyObject>()
            )
            buildPromise {
                dispatcher.dispatch(action)
            }
        }
    }
    bindChatClick(".km-apply-meal-effect") { _, el, _ ->
        val degree = el.dataset["degree"]
        val id = el.dataset["recipe"]
        val campingActorUuid = el.dataset["campingActorUuid"]
        if (degree != null && id != null && campingActorUuid != null) {
            buildPromise {
                dispatcher.dispatch(
                    ActionMessage(
                        action = "applyMealEffects",
                        data = ApplyMealEffects(
                            degree = degree,
                            recipeId = id,
                            campingActorUuid = campingActorUuid,
                        ).unsafeCast<AnyObject>()
                    )
                )
            }
        }
    }
}