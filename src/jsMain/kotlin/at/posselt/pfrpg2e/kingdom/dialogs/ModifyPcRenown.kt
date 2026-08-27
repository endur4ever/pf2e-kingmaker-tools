package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.CheckboxInput
import at.posselt.pfrpg2e.app.forms.NumberInput
import at.posselt.pfrpg2e.app.forms.Section
import at.posselt.pfrpg2e.app.forms.SectionsContext
import at.posselt.pfrpg2e.app.forms.formContext
import at.posselt.pfrpg2e.data.kingdom.EPITHET_CATALOG
import at.posselt.pfrpg2e.data.kingdom.MAX_POPULACE_RENOWN
import at.posselt.pfrpg2e.data.kingdom.MAX_PURCHASE_ACCESS_TIER
import at.posselt.pfrpg2e.data.kingdom.MIN_POPULACE_RENOWN
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
external interface PcRenownFormData {
    var populace: Int
    var tier: Int
    var epithets: Array<Boolean>
}

@JsExport
class PcRenownDataModel(
    value: AnyObject,
    options: DocumentConstructionContext?
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            int("populace")
            int("tier")
            booleanArray("epithets")
        }
    }
}

@JsPlainObject
external interface PcRenownFormContext : ValidatedHandlebarsContext, SectionsContext

/**
 * The GM's manual override for one PC's renown: set the populace number, the purchase-access tier,
 * and grant or revoke any epithet by hand.
 *
 * Every epithet in the catalog is listed with a checkbox rather than only the held ones, because
 * the dialog exists precisely to give an honour the engine has not offered -- or to take back one
 * granted by mistake. [onSave] receives the COMPLETE desired epithet set, so unchecking is how a
 * revoke happens.
 *
 * Faction renown is deliberately NOT editable here: it is per-faction, unbounded in count, and
 * accrues from named deeds -- a checkbox grid could not represent it, and inventing a text format
 * for it would be a worse tool than leaving the ledger to the engine.
 */
@JsExport
class ModifyPcRenown(
    private val pcName: String,
    private val initialPopulace: Int,
    private val initialTier: Int,
    private val heldEpithets: List<String>,
    private val onSave: (populace: Int, tier: Int, epithets: List<String>) -> Unit,
) : FormApp<PcRenownFormContext, PcRenownFormData>(
    title = t("kingdom.renown.adjust"),
    template = "components/forms/application-form.hbs",
    debug = false,
    dataModel = PcRenownDataModel::class.js,
    width = 480,
    id = "kmPcRenown",
) {
    private val catalogIds: List<String> = EPITHET_CATALOG.map { it.id }

    private var data: PcRenownFormData = PcRenownFormData(
        populace = initialPopulace,
        tier = initialTier,
        epithets = catalogIds.map { it in heldEpithets }.toTypedArray(),
    )

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions
    ): Promise<PcRenownFormContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        PcRenownFormContext(
            partId = parent.partId,
            isFormValid = isFormValid,
            sections = formContext(
                Section(
                    legend = pcName,
                    formRows = listOf(
                        NumberInput(
                            name = "populace",
                            label = t("kingdom.renown.populace"),
                            value = data.populace,
                            stacked = false,
                            help = t("kingdom.renown.populaceHelp"),
                        ),
                        NumberInput(
                            name = "tier",
                            label = t("kingdom.renown.tier"),
                            value = data.tier,
                            stacked = false,
                        ),
                    ) + catalogIds.mapIndexed { index, id ->
                        CheckboxInput(
                            name = "epithets.$index",
                            label = t("kingdom.renown.epithet.$id"),
                            value = data.epithets.getOrElse(index) { false },
                        )
                    }
                )
            )
        )
    }

    override fun onParsedSubmit(value: PcRenownFormData): Promise<Void> = buildPromise {
        data = value
        undefined
    }

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "km-save" -> {
                close()
                onSave(
                    // clamped to the same bounds the engine enforces, so a hand-typed 500 cannot
                    // put a PC somewhere accrueRenown would immediately pull them back from
                    data.populace.coerceIn(MIN_POPULACE_RENOWN, MAX_POPULACE_RENOWN),
                    data.tier.coerceIn(0, MAX_PURCHASE_ACCESS_TIER),
                    catalogIds.filterIndexed { index, _ -> data.epithets.getOrElse(index) { false } },
                )
            }
        }
    }
}
