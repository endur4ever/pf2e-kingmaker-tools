package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.NumberInput
import at.posselt.pfrpg2e.app.forms.Section
import at.posselt.pfrpg2e.app.forms.SectionsContext
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

/** A realm hex a caravan can depart from (a claimed hex, optionally a settlement). */
data class CaravanHexOption(val hexKey: String, val label: String)

/** A trade-partner faction with a map location a caravan can be sent to. */
data class CaravanPartnerOption(val name: String, val hexKey: String, val label: String)

/** What the dispatch dialog hands back; the sheet computes the route/ETA and persists the caravan. */
data class CaravanDispatchRequest(
    val kind: String,
    val originHexKey: String,
    val partnerName: String,
    val partnerHexKey: String,
    val commodity: String,
    val amount: Int,
)

@JsPlainObject
external interface CaravanDispatchFormData {
    var kind: String
    var originHexKey: String
    var partnerName: String
    var commodity: String
    var amount: Int
}

@JsExport
class CaravanDispatchDataModel(
    value: AnyObject,
    options: DocumentConstructionContext?
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            string("kind")
            string("originHexKey")
            string("partnerName")
            string("commodity")
            int("amount")
        }
    }
}

@JsPlainObject
external interface CaravanDispatchFormContext : ValidatedHandlebarsContext, SectionsContext

/**
 * Dispatch a caravan that sells Commodities to a trade partner (delivering RP on arrival; see
 * `CaravanTick`). Origin is one of the kingdom's claimed [hexes]; destination is a [partners] entry
 * that has a map location set. The sheet computes the route/ETA, deducts the cargo, and stores the
 * caravan. (Buying from partners and inter-settlement transfers are planned follow-ups.)
 */
class CaravanDispatchDialog(
    private val hexes: List<CaravanHexOption>,
    private val partners: List<CaravanPartnerOption>,
    private val commodities: List<String>,
    private val onSave: (CaravanDispatchRequest) -> Unit,
) : FormApp<CaravanDispatchFormContext, CaravanDispatchFormData>(
    title = t("kingdom.caravans.dispatchTitle"),
    template = "components/forms/application-form.hbs",
    dataModel = CaravanDispatchDataModel::class.js,
    width = 480,
    id = "kmCaravanDispatch",
) {
    private var data: CaravanDispatchFormData = CaravanDispatchFormData(
        kind = "sellToPartner",
        originHexKey = hexes.firstOrNull()?.hexKey ?: "",
        partnerName = partners.firstOrNull()?.name ?: "",
        commodity = commodities.firstOrNull() ?: "food",
        amount = 1,
    )

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions
    ): Promise<CaravanDispatchFormContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        CaravanDispatchFormContext(
            partId = parent.partId,
            isFormValid = isFormValid,
            sections = formContext(
                Section(
                    legend = t("kingdom.caravans.dispatchTitle"),
                    formRows = listOf(
                        Select(
                            name = "kind",
                            label = t("kingdom.caravans.kind"),
                            value = data.kind,
                            options = listOf(
                                SelectOption(t("kingdom.caravans.kindSell"), "sellToPartner"),
                                SelectOption(t("kingdom.caravans.kindBuy"), "buyFromPartner"),
                            ),
                            stacked = false,
                            help = t("kingdom.caravans.kindHelp"),
                        ),
                        Select(
                            name = "originHexKey",
                            label = t("kingdom.caravans.origin"),
                            value = data.originHexKey,
                            options = hexes.map { SelectOption(it.label, it.hexKey) },
                            stacked = false,
                        ),
                        Select(
                            name = "partnerName",
                            label = t("kingdom.caravans.partner"),
                            value = data.partnerName,
                            options = partners.map { SelectOption(it.label, it.name) },
                            stacked = false,
                        ),
                        Select(
                            name = "commodity",
                            label = t("kingdom.caravans.commodity"),
                            value = data.commodity,
                            options = commodities.map { SelectOption(t("kingdom.$it"), it) },
                            stacked = false,
                        ),
                        NumberInput(
                            name = "amount",
                            label = t("kingdom.caravans.amount"),
                            value = data.amount,
                            stacked = false,
                        ),
                    )
                )
            )
        )
    }

    override fun onParsedSubmit(value: CaravanDispatchFormData): Promise<Void> = buildPromise {
        data = value
        undefined
    }

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "km-save" -> {
                val partner = partners.find { it.name == data.partnerName } ?: return
                if (data.amount <= 0) return
                close()
                onSave(
                    CaravanDispatchRequest(
                        kind = if (data.kind == "buyFromPartner") "buyFromPartner" else "sellToPartner",
                        originHexKey = data.originHexKey,
                        partnerName = partner.name,
                        partnerHexKey = partner.hexKey,
                        commodity = data.commodity,
                        amount = data.amount,
                    )
                )
            }
        }
    }
}
