package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.CheckboxInput
import at.posselt.pfrpg2e.app.forms.NumberInput
import at.posselt.pfrpg2e.app.forms.Section
import at.posselt.pfrpg2e.app.forms.SectionsContext
import at.posselt.pfrpg2e.app.forms.TextInput
import at.posselt.pfrpg2e.app.forms.formContext
import at.posselt.pfrpg2e.kingdom.data.RawWarThreat
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
external interface WarThreatFormData {
    var name: String
    var description: String
    var enemyFaction: String?
    var maxEscalation: Int
    var eta: Int?
    var targetHexLocation: String?
    var pauseOnExpiry: Boolean
}

@JsExport
class WarThreatDataModel(
    value: AnyObject,
    options: DocumentConstructionContext?
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            string("name")
            string("description")
            string("enemyFaction", nullable = true)
            int("maxEscalation") { min = 1 }
            int("eta", nullable = true)
            string("targetHexLocation", nullable = true)
            boolean("pauseOnExpiry")
        }
    }
}

@JsPlainObject
external interface WarThreatFormContext : ValidatedHandlebarsContext, SectionsContext

/**
 * Create/edit a [RawWarThreat] (roadmap #12). [existing] non-null = edit mode;
 * onSave receives the assembled threat (id/status/escalation preserved on edit,
 * fresh on create).
 */
@JsExport
class AddWarThreat(
    private val existing: RawWarThreat? = null,
    private val onSave: (RawWarThreat) -> Unit,
) : FormApp<WarThreatFormContext, WarThreatFormData>(
    title = if (existing == null) t("armyPressure.addThreat") else t("armyPressure.editThreat"),
    template = "components/forms/application-form.hbs",
    debug = false,
    dataModel = WarThreatDataModel::class.js,
    width = 480,
    id = "kmWarThreat",
) {
    private var data: WarThreatFormData = WarThreatFormData(
        name = existing?.name ?: "",
        description = existing?.description ?: "",
        enemyFaction = existing?.enemyFaction,
        maxEscalation = existing?.maxEscalation ?: 3,
        eta = existing?.eta,
        targetHexLocation = existing?.targetHexLocation,
        pauseOnExpiry = existing?.pauseOnExpiry ?: false,
    )

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions
    ): Promise<WarThreatFormContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        WarThreatFormContext(
            partId = parent.partId,
            isFormValid = isFormValid,
            sections = formContext(
                Section(
                    legend = t("armyPressure.threats"),
                    formRows = listOf(
                        TextInput(name = "name", label = t("armyPressure.threatName"), value = data.name, stacked = false),
                        TextInput(name = "description", label = t("armyPressure.threatDescription"), value = data.description, required = false, stacked = false),
                        TextInput(name = "enemyFaction", label = t("armyPressure.enemyFaction"), value = data.enemyFaction ?: "", required = false, stacked = false),
                        NumberInput(name = "maxEscalation", label = t("armyPressure.maxEscalation"), value = data.maxEscalation, stacked = false),
                        NumberInput(name = "eta", label = t("armyPressure.eta"), value = data.eta ?: 0, stacked = false, help = t("armyPressure.etaHelp")),
                        TextInput(name = "targetHexLocation", label = t("armyPressure.target"), value = data.targetHexLocation ?: "", required = false, stacked = false),
                        CheckboxInput(name = "pauseOnExpiry", label = t("armyPressure.pauseOnExpiry"), value = data.pauseOnExpiry, help = t("armyPressure.pauseOnExpiryHelp"), stacked = false),
                    )
                )
            )
        )
    }

    override fun onParsedSubmit(value: WarThreatFormData): Promise<Void> = buildPromise {
        data = value
        undefined
    }

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "km-save" -> {
                val etaValue = data.eta?.let { if (it <= 0) null else it }
                val threat = RawWarThreat(
                    id = existing?.id ?: "threat-${kotlin.js.Date().getTime().toLong()}",
                    name = data.name.ifBlank { t("armyPressure.threats") },
                    description = data.description,
                    enemyFaction = data.enemyFaction?.ifBlank { null },
                    escalationLevel = existing?.escalationLevel ?: 0,
                    maxEscalation = data.maxEscalation.coerceAtLeast(1),
                    eta = etaValue,
                    targetSettlementSceneId = existing?.targetSettlementSceneId,
                    targetHexLocation = data.targetHexLocation?.ifBlank { null },
                    linkedQuestId = existing?.linkedQuestId,
                    linkedEventId = existing?.linkedEventId,
                    pauseOnExpiry = data.pauseOnExpiry,
                    status = existing?.status ?: "active",
                    triggeredTurn = existing?.triggeredTurn,
                )
                close()
                onSave(threat)
            }
        }
    }
}
