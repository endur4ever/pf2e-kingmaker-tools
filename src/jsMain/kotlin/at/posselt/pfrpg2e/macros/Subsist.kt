package at.posselt.pfrpg2e.macros

import at.posselt.pfrpg2e.app.forms.CheckboxInput
import at.posselt.pfrpg2e.app.forms.Select
import at.posselt.pfrpg2e.app.forms.SelectOption
import at.posselt.pfrpg2e.app.forms.formContext
import at.posselt.pfrpg2e.app.prompt
import at.posselt.pfrpg2e.camping.getCamping
import at.posselt.pfrpg2e.camping.getCampingActors
import at.posselt.pfrpg2e.camping.postSubsistProvisionsOffer
import at.posselt.pfrpg2e.camping.rollSubsist
import at.posselt.pfrpg2e.camping.subsistDefaults
import at.posselt.pfrpg2e.takeIfInstance
import at.posselt.pfrpg2e.utils.asSequence
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import com.foundryvtt.core.documents.Actor
import com.foundryvtt.core.ui
import com.foundryvtt.pf2e.actor.PF2ECharacter
import js.array.component1
import js.array.component2
import js.objects.recordOf
import kotlinx.js.JsPlainObject

@JsPlainObject
external interface SubsistData {
    val skill: String
    val dc: Int
    val subsistPenalty: Boolean
}

suspend fun subsistMacro(game: Game, actor: Actor?) {
    val chosenActor = actor?.takeIfInstance<PF2ECharacter>()
    if (chosenActor == null) {
        ui.notifications.error(t("macros.subsist.selectCharacter"))
        return
    }
    val campingActor = game.getCampingActors()
        .find { chosenActor.uuid in it.getCamping()?.actorUuids.orEmpty() }
    val camping = campingActor?.getCamping()
    val skills = chosenActor.skills.asSequence()
        .map { SelectOption(label = it.component2().label, value = it.component1()) }
        .toList()
    val defaults = subsistDefaults(camping)
    val defaultDc = defaults.dc
    val defaultSkill = defaults.skill
    prompt<SubsistData, Unit>(
        title = t("macros.subsist.title"),
        templatePath = "components/forms/form.hbs",
        templateContext = recordOf(
            "formRows" to formContext(
                Select(
                    name = "skill",
                    label = t("enums.skill"),
                    options = skills,
                    value = defaultSkill,
                ),
                Select.dc(
                    name = "dc",
                    value = defaultDc,
                ),
                CheckboxInput(
                    name = "subsistPenalty",
                    label = t("macros.subsist.subsistAfterExploration"),
                    help =  t("macros.subsist.subsistAfterExplorationHelp"),
                    value = true,
                )
            )
        )
    ) {
        val result = rollSubsist(
            game = game,
            actor = chosenActor,
            skill = it.skill,
            dc = it.dc,
            subsistPenalty = it.subsistPenalty,
        )
        if (result != null) {
            postSubsistProvisionsOffer(chosenActor, result.provisions)
        }
    }
}
