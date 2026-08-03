package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.CheckboxInput
import at.posselt.pfrpg2e.app.forms.FormElementContext
import at.posselt.pfrpg2e.app.forms.NumberInput
import at.posselt.pfrpg2e.app.forms.TextArea
import at.posselt.pfrpg2e.app.forms.TextInput
import at.posselt.pfrpg2e.app.forms.formContext
import at.posselt.pfrpg2e.campaign.CampaignClock
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.launch
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.abstract.DataModel
import com.foundryvtt.core.abstract.DocumentConstructionContext
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.data.dsl.buildSchema
import com.foundryvtt.core.utils.deepClone
import io.github.uuidjs.uuid.v4
import js.core.Void
import kotlinx.coroutines.await
import kotlinx.js.JsPlainObject
import org.w3c.dom.HTMLElement
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise

@JsPlainObject
external interface ModifyCampaignClockContext : ValidatedHandlebarsContext {
    val formRows: Array<FormElementContext>
}

@JsPlainObject
external interface ModifyCampaignClockData {
    val label: String
    val maxTurns: Int
    val turnsRemaining: Int
    val description: String
    val pauseOnExpiry: Boolean
    val expiryConsequenceUnrest: Int
    val expiryMessage: String
}

@JsExport
class ModifyCampaignClockDataModel(
    value: AnyObject,
    options: DocumentConstructionContext?
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            string("label")
            int("maxTurns")
            int("turnsRemaining")
            string("description")
            boolean("pauseOnExpiry")
            int("expiryConsequenceUnrest")
            string("expiryMessage")
        }
    }
}

class ModifyCampaignClock(
    data: CampaignClock? = null,
    private val afterSubmit: suspend (data: CampaignClock) -> Unit,
) : FormApp<ModifyCampaignClockContext, ModifyCampaignClockData>(
    title = if (data == null) t("kingdom.addCampaignClock") else t("kingdom.editCampaignClock"),
    template = "components/forms/application-form.hbs",
    debug = true,
    dataModel = ModifyCampaignClockDataModel::class.js,
    id = "kmModifyCampaignClock",
    width = 500,
) {
    private val edit: Boolean = data != null
    private var current: CampaignClock = data?.let {
        CampaignClock(
            id = it.id,
            label = it.label,
            maxTurns = it.maxTurns,
            turnsRemaining = it.turnsRemaining,
            description = it.description,
            pauseOnExpiry = it.pauseOnExpiry,
            expired = it.expired,
            active = it.active,
            expiryConsequenceUnrest = it.expiryConsequenceUnrest,
            expiryMessage = it.expiryMessage,
        )
    } ?: CampaignClock(
        id = v4(),
        label = "",
        maxTurns = 6,
        turnsRemaining = 6,
        description = "",
        pauseOnExpiry = false,
        expired = false,
        active = true,
        expiryConsequenceUnrest = 0,
        expiryMessage = "",
    )

    init {
        isFormValid = data != null
    }

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "km-save" -> save()
        }
    }

    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions,
    ): Promise<ModifyCampaignClockContext> =
        buildPromise {
            val parent = super._preparePartContext(partId, context, options).await()
            val formData = ModifyCampaignClockData(
                label = current.label,
                maxTurns = current.maxTurns,
                turnsRemaining = current.turnsRemaining,
                description = current.description,
                pauseOnExpiry = current.pauseOnExpiry,
                expiryConsequenceUnrest = current.expiryConsequenceUnrest,
                expiryMessage = current.expiryMessage,
            )
            ModifyCampaignClockContext(
                partId = parent.partId,
                formRows = formContext(
                    TextInput(
                        name = "label",
                        label = t("applications.name"),
                        value = formData.label,
                        required = true,
                        hideLabel = false,
                    ),
                    NumberInput(
                        name = "maxTurns",
                        label = t("kingdom.maxTurns"),
                        value = formData.maxTurns,
                        required = true,
                        hideLabel = false,
                    ),
                    NumberInput(
                        name = "turnsRemaining",
                        label = t("kingdom.turnsRemaining"),
                        value = formData.turnsRemaining,
                        required = true,
                        hideLabel = false,
                    ),
                    TextArea(
                        name = "description",
                        label = t("applications.description"),
                        value = formData.description,
                        required = false,
                        hideLabel = false,
                    ),
                    CheckboxInput(
                        name = "pauseOnExpiry",
                        label = t("kingdom.pauseOnExpiry"),
                        value = formData.pauseOnExpiry,
                        required = false,
                        hideLabel = false,
                    ),
                    NumberInput(
                        name = "expiryConsequenceUnrest",
                        label = t("kingdom.expiryConsequenceUnrest"),
                        value = formData.expiryConsequenceUnrest,
                        required = true,
                        hideLabel = false,
                    ),
                    TextInput(
                        name = "expiryMessage",
                        label = t("kingdom.expiryMessage"),
                        value = formData.expiryMessage,
                        required = false,
                        hideLabel = false,
                    ),
                ),
                isFormValid = isFormValid,
            )
        }

    fun save(): Promise<Void> = buildPromise {
        if (isValid()) {
            close().await()
            afterSubmit(current)
        }
        undefined
    }

    override fun onParsedSubmit(value: ModifyCampaignClockData): Promise<Void> = buildPromise {
        current = CampaignClock(
            id = current.id,
            label = value.label,
            maxTurns = value.maxTurns,
            turnsRemaining = value.turnsRemaining,
            description = value.description,
            pauseOnExpiry = value.pauseOnExpiry,
            expired = current.expired,
            active = current.active,
            expiryConsequenceUnrest = value.expiryConsequenceUnrest,
            expiryMessage = value.expiryMessage,
        )
        undefined
    }
}
