package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.app.forms.CheckboxInput
import at.posselt.pfrpg2e.app.forms.formContext
import at.posselt.pfrpg2e.app.prompt
import at.posselt.pfrpg2e.camping.getCampingActors
import at.posselt.pfrpg2e.kingdom.sheet.openOrCreateKingdomSheet
import at.posselt.pfrpg2e.settings.ClimateSettings
import at.posselt.pfrpg2e.settings.Pfrpg2eKingdomCampingWeatherSettings
import at.posselt.pfrpg2e.settings.getDefaultMonths
import at.posselt.pfrpg2e.utils.bindChatClick
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.t
import at.posselt.pfrpg2e.actions.ActionDispatcher
import com.foundryvtt.core.Game
import com.foundryvtt.core.helpers.TypedHooks
import com.foundryvtt.core.helpers.onRenderChatLog
import com.foundryvtt.core.ui
import js.objects.recordOf
import kotlinx.js.JsPlainObject
import org.w3c.dom.get

/**
 * The one-click fixes offered beside a failing row of the setup health check.
 *
 * These deliberately do not go through [bindChatButtons]: every button there resolves a
 * KingdomActor first and warns when it cannot, which is precisely the state the "create kingdom"
 * fix exists to resolve. A setup fix has to run when the world is not set up yet.
 */

@Suppress("unused")
@JsPlainObject
external interface PickMapScenesChoice {
    val sceneIds: Array<String>?
}

private suspend fun createKingdom(game: Game, dispatcher: ActionDispatcher) {
    // The kingdom lives on the party actor, so without one there is nothing to attach it to.
    val party = game.getCampingActors().firstOrNull()
    if (party == null) {
        ui.notifications.error(t("setupWizard.error.noPartyActor"))
        return
    }
    openOrCreateKingdomSheet(game, dispatcher, party)
}

private suspend fun restoreClimate() {
    Pfrpg2eKingdomCampingWeatherSettings.setClimateSettings(ClimateSettings(months = getDefaultMonths()))
    ui.notifications.info(t("setupWizard.done.climate"))
}

private suspend fun pickMapScenes(game: Game) {
    val scenes = game.scenes.contents.mapNotNull { scene -> scene.id?.let { it to scene.name } }
    if (scenes.isEmpty()) {
        ui.notifications.error(t("setupWizard.error.noScenes"))
        return
    }
    val selected = Pfrpg2eKingdomCampingWeatherSettings.getCampaignMapSceneIds()
        .split(',')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()
    prompt<PickMapScenesChoice, Unit>(
        title = t("setupWizard.action.pick-map-scenes"),
        templatePath = "components/forms/form.hbs",
        templateContext = recordOf(
            "formRows" to formContext(
                *scenes.map { (id, name) ->
                    // The id is the value that gets stored, so it has to be the field name.
                    CheckboxInput(name = id, label = name, value = id in selected)
                }.toTypedArray(),
            ),
        ),
    ) { choice ->
        // Reading the checkboxes back by scene id keeps this independent of the form's field order.
        val chosen = scenes.map { it.first }.filter { choice.asDynamic()[it] == true }
        Pfrpg2eKingdomCampingWeatherSettings.setCampaignMapSceneIds(chosen.joinToString(","))
        ui.notifications.info(t("setupWizard.done.mapScenes", recordOf("count" to chosen.size)))
    }
}

/**
 * Binds the health-check card's fix buttons. Every handler re-checks GM: the card is whispered to
 * GMs, but a whisper is delivery, not authorization, and these all write world-level state.
 */
fun bindSetupHealthCheckButtons(game: Game, dispatcher: ActionDispatcher) {
    TypedHooks.onRenderChatLog { _, _, _ ->
        bindChatClick(".km-setup-action") { _, target, _ ->
            buildPromise {
                if (!game.user.isGM) {
                    ui.notifications.error(t("setupWizard.error.gmOnly"))
                } else {
                    when (val action = target.dataset["setupAction"]) {
                        "create-kingdom" -> createKingdom(game, dispatcher)
                        "restore-climate" -> restoreClimate()
                        "pick-map-scenes" -> pickMapScenes(game)
                        else -> console.error("unknown setup action '$action'")
                    }
                }
            }.catch { e ->
                console.error("setup health check button failed", e)
                ui.notifications.error(t("kingdom.chatButtonFailed"))
                null
            }
        }
    }
}
