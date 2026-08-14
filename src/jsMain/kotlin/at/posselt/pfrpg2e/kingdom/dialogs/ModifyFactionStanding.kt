package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.NumberInput
import at.posselt.pfrpg2e.app.forms.Section
import at.posselt.pfrpg2e.app.forms.SectionsContext
import at.posselt.pfrpg2e.app.forms.TextInput
import at.posselt.pfrpg2e.app.forms.Select
import at.posselt.pfrpg2e.app.forms.SelectOption
import at.posselt.pfrpg2e.app.forms.formContext
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.abstract.DataModel
import com.foundryvtt.core.abstract.DocumentConstructionContext
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.data.dsl.buildSchema
import js.core.Void
import kotlinx.coroutines.await
import kotlinx.js.JsPlainObject
import org.w3c.dom.HTMLElement
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise

@JsPlainObject
external interface FactionStandingFormData {
    var delta: Int
    var reason: String
    var allianceLevel: String
}

@JsExport
class FactionStandingDataModel(
    value: AnyObject,
    options: DocumentConstructionContext?
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            // The field's own help text says "e.g. 10 or -15", but without allowNegative the schema
        // clamped a negative delta to 0, and the caller's `delta != 0` guard then skipped the whole
        // update: no standing change, no log entry, no error. Adjusting downward did nothing at all.
        int("delta", allowNegative = true)
            string("reason")
            string("allianceLevel")
        }
    }
}

@JsPlainObject
external interface FactionStandingFormContext : ValidatedHandlebarsContext, SectionsContext

/**
 * Adjust one faction/group's diplomatic standing and treaty level.
 * [onSave] receives the raw delta, reason, and allianceLevel; the caller applies it,
 * keeping standing math in the pure layer.
 */
@JsExport
class ModifyFactionStanding(
    private val factionName: String,
    private val initialAllianceLevel: String?,
    private val onSave: (delta: Int, reason: String, allianceLevel: String?) -> Unit,
) : FormApp<FactionStandingFormContext, FactionStandingFormData>(
    title = t("kingdom.adjustStanding"),
    template = "components/forms/application-form.hbs",
    debug = false,
    dataModel = FactionStandingDataModel::class.js,
    width = 480,
    id = "kmFactionStanding",
) {
    private var data: FactionStandingFormData = FactionStandingFormData(
        delta = 0,
        reason = "",
        allianceLevel = initialAllianceLevel ?: "none",
    )

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions
    ): Promise<FactionStandingFormContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        FactionStandingFormContext(
            partId = parent.partId,
            isFormValid = isFormValid,
            sections = formContext(
                Section(
                    legend = factionName,
                    formRows = listOf(
                        NumberInput(
                            name = "delta",
                            label = t("kingdom.standingDelta"),
                            value = data.delta,
                            stacked = false,
                            help = t("kingdom.standingDeltaHelp"),
                        ),
                        TextInput(
                            name = "reason",
                            label = t("kingdom.standingReason"),
                            value = data.reason,
                            stacked = false,
                        ),
                        Select(
                            label = t("kingdom.treaty"),
                            name = "allianceLevel",
                            value = data.allianceLevel,
                            options = listOf(
                                SelectOption(t("kingdom.allianceLevel.none"), "none"),
                                SelectOption(t("kingdom.allianceLevel.non-aggression"), "non-aggression"),
                                SelectOption(t("kingdom.allianceLevel.alliance"), "alliance"),
                                SelectOption(t("kingdom.allianceLevel.tribute"), "tribute"),
                            ),
                            required = false,
                            stacked = false,
                        ),
                    )
                )
            )
        )
    }

    override fun onParsedSubmit(value: FactionStandingFormData): Promise<Void> = buildPromise {
        data = value
        undefined
    }

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "km-save" -> {
                close()
                val level = data.allianceLevel.ifBlank { "none" }
                val finalAllianceLevel = if (level == "none") null else level
                onSave(data.delta, data.reason, finalAllianceLevel)
            }
        }
    }
}
