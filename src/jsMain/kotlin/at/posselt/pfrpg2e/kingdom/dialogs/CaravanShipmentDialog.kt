package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.CheckboxInput
import at.posselt.pfrpg2e.app.forms.NumberInput
import at.posselt.pfrpg2e.app.forms.Section
import at.posselt.pfrpg2e.app.forms.SectionsContext
import at.posselt.pfrpg2e.app.forms.Select
import at.posselt.pfrpg2e.app.forms.SelectOption
import at.posselt.pfrpg2e.app.forms.TextInput
import at.posselt.pfrpg2e.app.forms.formContext
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.abstract.DataModel
import com.foundryvtt.core.abstract.DocumentConstructionContext
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.data.dsl.buildSchema
import com.foundryvtt.core.ui
import js.core.Void
import kotlinx.coroutines.await
import kotlinx.js.JsPlainObject
import org.w3c.dom.HTMLElement
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise

@JsPlainObject
external interface CaravanShipmentFormData {
    var itemName: String
    var itemQuantity: Int
    var itemLevel: Int
    var itemBulk: String
    var itemPriceGp: Double
    var originHexKey: String
    var destHexKey: String
    var caravanType: String
    var isPurchase: Boolean
}

@JsExport
class CaravanShipmentDataModel(
    value: AnyObject,
    options: DocumentConstructionContext?
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            string("itemName")
            int("itemQuantity")
            int("itemLevel")
            string("itemBulk")
            double("itemPriceGp")
            string("originHexKey")
            string("destHexKey")
            string("caravanType")
            boolean("isPurchase")
        }
    }
}

@JsPlainObject
external interface CaravanShipmentFormContext : ValidatedHandlebarsContext, SectionsContext

class CaravanShipmentDialog(
    private val settlements: List<CaravanHexOption>,
    private val onSave: (CaravanShipmentFormData) -> Unit,
) : FormApp<CaravanShipmentFormContext, CaravanShipmentFormData>(
    title = t("kingdom.caravans.shipmentTitle"),
    template = "components/forms/application-form.hbs",
    dataModel = CaravanShipmentDataModel::class.js,
    width = 480,
    id = "kmCaravanShipment",
) {
    private var data: CaravanShipmentFormData = CaravanShipmentFormData(
        itemName = "",
        itemQuantity = 1,
        itemLevel = 1,
        itemBulk = "1",
        itemPriceGp = 0.0,
        originHexKey = settlements.firstOrNull()?.hexKey ?: "",
        destHexKey = settlements.getOrNull(1)?.hexKey ?: settlements.firstOrNull()?.hexKey ?: "",
        caravanType = "medium",
        isPurchase = true,
    )

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions
    ): Promise<CaravanShipmentFormContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        CaravanShipmentFormContext(
            partId = parent.partId,
            isFormValid = isFormValid,
            sections = formContext(
                Section(
                    legend = t("kingdom.caravans.shipmentTitle"),
                    formRows = listOf(
                        TextInput(
                            name = "itemName",
                            label = t("kingdom.caravans.itemName"),
                            value = data.itemName,
                            stacked = false,
                            required = true,
                        ),
                        NumberInput(
                            name = "itemQuantity",
                            label = t("kingdom.caravans.itemQuantity"),
                            value = data.itemQuantity,
                            stacked = false,
                        ),
                        NumberInput(
                            name = "itemLevel",
                            label = t("kingdom.caravans.itemLevel"),
                            value = data.itemLevel,
                            stacked = false,
                        ),
                        TextInput(
                            name = "itemBulk",
                            label = t("kingdom.caravans.itemBulk"),
                            value = data.itemBulk,
                            stacked = false,
                        ),
                        TextInput(
                            name = "itemPriceGp",
                            label = t("kingdom.caravans.itemPriceGp"),
                            value = data.itemPriceGp.toString(),
                            stacked = false,
                        ),
                        CheckboxInput(
                            name = "isPurchase",
                            label = t("kingdom.caravans.isPurchase"),
                            value = data.isPurchase,
                            stacked = false,
                        ),
                        Select(
                            name = "originHexKey",
                            label = t("kingdom.caravans.origin"),
                            value = data.originHexKey,
                            options = settlements.map { SelectOption(it.label, it.hexKey) },
                            stacked = false,
                        ),
                        Select(
                            name = "destHexKey",
                            label = t("kingdom.caravans.destination"),
                            value = data.destHexKey,
                            options = settlements.map { SelectOption(it.label, it.hexKey) },
                            stacked = false,
                        ),
                        Select(
                            name = "caravanType",
                            label = t("kingdom.caravans.caravanType"),
                            value = data.caravanType,
                            options = listOf(
                                SelectOption(t("kingdom.caravans.typeLight"), "light"),
                                SelectOption(t("kingdom.caravans.typeMedium"), "medium"),
                                SelectOption(t("kingdom.caravans.typeHeavy"), "heavy"),
                            ),
                            stacked = false,
                        ),
                    )
                )
            )
        )
    }

    override fun onParsedSubmit(value: CaravanShipmentFormData): Promise<Void> = buildPromise {
        data = value
        undefined
    }

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "km-save" -> {
                if (data.itemName.isBlank()) return
                if (data.itemQuantity <= 0) return
                if (data.originHexKey == data.destHexKey) {
                    ui.notifications.warn(t("kingdom.caravans.sameOriginDest"))
                    return
                }
                close()
                onSave(data)
            }
        }
    }
}
