package at.posselt.pfrpg2e.camping.dialogs

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.CheckboxInput
import at.posselt.pfrpg2e.app.forms.Section
import at.posselt.pfrpg2e.app.forms.SectionsContext
import at.posselt.pfrpg2e.app.forms.Select
import at.posselt.pfrpg2e.app.forms.SelectOption
import at.posselt.pfrpg2e.app.forms.formContext
import at.posselt.pfrpg2e.camping.EncounterCategory
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.launch
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.abstract.DataModel
import com.foundryvtt.core.abstract.DocumentConstructionContext
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.data.dsl.buildSchema
import js.core.Void
import js.objects.Record
import js.objects.recordOf
import kotlinx.coroutines.await
import kotlinx.js.JsPlainObject
import org.w3c.dom.HTMLElement
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise

/**
 * Roadmap #11: per-region sub-dialog editing all eight per-category encounter
 * roll tables plus the cleared-hex suppression toggle. Kept out of the region
 * matrix (which would balloon to 17 columns); launched from a per-row button.
 * Edits in memory and reports back via [onSave]; the parent [RegionConfig]
 * persists on its own save.
 */
@JsPlainObject
external interface RegionEncounterTablesData {
    var combat: String?
    var rp: String?
    var rumor: String?
    var merchant: String?
    var disease: String?
    var faction: String?
    var weather: String?
    var lore: String?
    var suppressEncountersOnClearedHex: Boolean
}

@JsExport
class RegionEncounterTablesDataModel(
    value: AnyObject,
    options: DocumentConstructionContext?
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            string("combat", nullable = true)
            string("rp", nullable = true)
            string("rumor", nullable = true)
            string("merchant", nullable = true)
            string("disease", nullable = true)
            string("faction", nullable = true)
            string("weather", nullable = true)
            string("lore", nullable = true)
            boolean("suppressEncountersOnClearedHex")
        }
    }
}

@JsPlainObject
external interface RegionEncounterTablesContext : ValidatedHandlebarsContext, SectionsContext

private class RegionEncounterTables(
    private val regionName: String,
    private val tables: Record<String, String?>?,
    private val suppress: Boolean,
    private val rollTableOptions: List<SelectOption>,
    private val onSave: (tables: Record<String, String?>, suppress: Boolean) -> Unit,
) : FormApp<RegionEncounterTablesContext, RegionEncounterTablesData>(
    title = t("camping.regionEncounterTables"),
    template = "components/forms/application-form.hbs",
    debug = false,
    dataModel = RegionEncounterTablesDataModel::class.js,
    width = 480,
    id = "kmRegionEncounterTables",
) {
    private fun tableFor(category: EncounterCategory): String? =
        tables?.get(category.value)

    private var data: RegionEncounterTablesData = RegionEncounterTablesData(
        combat = tableFor(EncounterCategory.COMBAT),
        rp = tableFor(EncounterCategory.RP),
        rumor = tableFor(EncounterCategory.RUMOR),
        merchant = tableFor(EncounterCategory.MERCHANT),
        disease = tableFor(EncounterCategory.DISEASE),
        faction = tableFor(EncounterCategory.FACTION),
        weather = tableFor(EncounterCategory.WEATHER),
        lore = tableFor(EncounterCategory.LORE),
        suppressEncountersOnClearedHex = suppress,
    )

    private fun valueFor(category: EncounterCategory): String? = when (category) {
        EncounterCategory.COMBAT -> data.combat
        EncounterCategory.RP -> data.rp
        EncounterCategory.RUMOR -> data.rumor
        EncounterCategory.MERCHANT -> data.merchant
        EncounterCategory.DISEASE -> data.disease
        EncounterCategory.FACTION -> data.faction
        EncounterCategory.WEATHER -> data.weather
        EncounterCategory.LORE -> data.lore
    }

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions
    ): Promise<RegionEncounterTablesContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        RegionEncounterTablesContext(
            partId = parent.partId,
            isFormValid = isFormValid,
            sections = formContext(
                Section(
                    legend = regionName,
                    formRows = EncounterCategory.entries.map { category ->
                        Select(
                            name = category.value,
                            label = t("camping.encounterCategoryTable.${category.value}"),
                            value = valueFor(category),
                            options = rollTableOptions,
                            required = false,
                            stacked = false,
                        )
                    } + listOf(
                        CheckboxInput(
                            name = "suppressEncountersOnClearedHex",
                            label = t("camping.regionSuppressClearedHex"),
                            value = data.suppressEncountersOnClearedHex,
                            help = t("camping.regionSuppressClearedHexHelp"),
                        ),
                    )
                )
            )
        )
    }

    override fun onParsedSubmit(value: RegionEncounterTablesData): Promise<Void> = buildPromise {
        data = value
        undefined
    }

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "km-save" -> {
                val map = recordOf<String, String?>(
                    EncounterCategory.COMBAT.value to data.combat?.ifBlank { null },
                    EncounterCategory.RP.value to data.rp?.ifBlank { null },
                    EncounterCategory.RUMOR.value to data.rumor?.ifBlank { null },
                    EncounterCategory.MERCHANT.value to data.merchant?.ifBlank { null },
                    EncounterCategory.DISEASE.value to data.disease?.ifBlank { null },
                    EncounterCategory.FACTION.value to data.faction?.ifBlank { null },
                    EncounterCategory.WEATHER.value to data.weather?.ifBlank { null },
                    EncounterCategory.LORE.value to data.lore?.ifBlank { null },
                )
                close()
                onSave(map, data.suppressEncountersOnClearedHex)
            }
        }
    }
}

fun configureRegionEncounterTables(
    regionName: String,
    tables: Record<String, String?>?,
    suppress: Boolean,
    rollTableOptions: List<SelectOption>,
    onSave: (tables: Record<String, String?>, suppress: Boolean) -> Unit,
) {
    RegionEncounterTables(regionName, tables, suppress, rollTableOptions, onSave).launch()
}
