package at.posselt.pfrpg2e.camping.dialogs

import at.posselt.pfrpg2e.app.awaitablePrompt
import at.posselt.pfrpg2e.app.forms.NumberInput
import at.posselt.pfrpg2e.app.forms.Select
import at.posselt.pfrpg2e.app.forms.formContext
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.pf2e.actor.PF2EActor
import js.objects.recordOf
import kotlinx.js.JsPlainObject

@JsPlainObject
external interface EncounterResolutionFormData {
    val dc: Int
    val rollModifier: Int
    val dcModifier: Int
}

suspend fun showEncounterResolutionDialog(
    watchers: List<PF2EActor>,
    defaultDc: Int
): EncounterResolutionFormData? =
    try {
        awaitablePrompt<EncounterResolutionFormData, EncounterResolutionFormData>(
            title = t("camping.encounterResolutionTitle"),
            templatePath = "components/forms/form.hbs",
            templateContext = recordOf(
                "formRows" to formContext(
                    Select.dc(
                        label = t("camping.stealthDc"),
                        name = "dc",
                        value = defaultDc,
                        help = t(
                            "camping.watchersOnDutyHelp",
                            recordOf("watchers" to watchers.joinToString(", ") { it.name })
                        ),
                        stacked = false
                    ),
                    NumberInput(
                        label = t("camping.rollModifier"),
                        name = "rollModifier",
                        value = 0,
                        stacked = false
                    ),
                    NumberInput(
                        label = t("camping.dcModifier"),
                        name = "dcModifier",
                        value = 0,
                        stacked = false
                    )
                )
            )
        ) { data, _ ->
            data
        }
    } catch (_: Throwable) {
        null
    }
