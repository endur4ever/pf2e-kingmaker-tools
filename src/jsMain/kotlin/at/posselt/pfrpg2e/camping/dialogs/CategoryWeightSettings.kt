package at.posselt.pfrpg2e.camping.dialogs

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.CheckboxInput
import at.posselt.pfrpg2e.app.forms.NumberInput
import at.posselt.pfrpg2e.app.forms.Section
import at.posselt.pfrpg2e.app.forms.SectionsContext
import at.posselt.pfrpg2e.app.forms.Select
import at.posselt.pfrpg2e.app.forms.formContext
import at.posselt.pfrpg2e.app.forms.toOption
import at.posselt.pfrpg2e.camping.CampingActor
import at.posselt.pfrpg2e.camping.CategoryWeights
import at.posselt.pfrpg2e.camping.EncounterCategory
import at.posselt.pfrpg2e.camping.categoryWeightsOrDefault
import at.posselt.pfrpg2e.camping.getCamping
import at.posselt.pfrpg2e.camping.isFilterByHexState
import at.posselt.pfrpg2e.camping.setCamping
import at.posselt.pfrpg2e.camping.toRaw
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.Game
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
external interface CategoryWeightSettings {
    var combat: Int
    var rp: Int
    var rumor: Int
    var merchant: Int
    var disease: Int
    var faction: Int
    var weather: Int
    var lore: Int
    var encounterCategoryProxyTableUuid: String?
    var filterByHexState: Boolean
}

@JsExport
class CategoryWeightSettingsDataModel(
    value: AnyObject,
    options: DocumentConstructionContext?
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            int("combat") { min = 0 }
            int("rp") { min = 0 }
            int("rumor") { min = 0 }
            int("merchant") { min = 0 }
            int("disease") { min = 0 }
            int("faction") { min = 0 }
            int("weather") { min = 0 }
            int("lore") { min = 0 }
            string("encounterCategoryProxyTableUuid", nullable = true)
            boolean("filterByHexState")
        }
    }
}

@JsPlainObject
external interface CategoryWeightSettingsContext : ValidatedHandlebarsContext, SectionsContext

/**
 * GM settings for the random-encounter curator (roadmap #11): the category proxy
 * table, hex-state filtering, and the per-category weights. Writes into the
 * camping flag via [setCamping].
 */
@JsExport
class CategoryWeightSettingsApplication(
    private val game: Game,
    private val campingActor: CampingActor,
) : FormApp<CategoryWeightSettingsContext, CategoryWeightSettings>(
    title = t("camping.encounterCurator"),
    template = "components/forms/application-form.hbs",
    debug = false,
    dataModel = CategoryWeightSettingsDataModel::class.js,
    id = "kmEncounterCurator-${campingActor.uuid}",
    width = 480,
) {
    private var settings: CategoryWeightSettings

    init {
        val camping = campingActor.getCamping()!!
        val w = camping.categoryWeightsOrDefault()
        settings = CategoryWeightSettings(
            combat = w.combat, rp = w.rp, rumor = w.rumor, merchant = w.merchant,
            disease = w.disease, faction = w.faction, weather = w.weather, lore = w.lore,
            encounterCategoryProxyTableUuid = camping.encounterCategoryProxyTableUuid,
            filterByHexState = camping.isFilterByHexState(),
        )
    }

    private fun weightFor(category: EncounterCategory): Int = when (category) {
        EncounterCategory.COMBAT -> settings.combat
        EncounterCategory.RP -> settings.rp
        EncounterCategory.RUMOR -> settings.rumor
        EncounterCategory.MERCHANT -> settings.merchant
        EncounterCategory.DISEASE -> settings.disease
        EncounterCategory.FACTION -> settings.faction
        EncounterCategory.WEATHER -> settings.weather
        EncounterCategory.LORE -> settings.lore
    }

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions
    ): Promise<CategoryWeightSettingsContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        CategoryWeightSettingsContext(
            partId = parent.partId,
            isFormValid = isFormValid,
            sections = formContext(
                Section(
                    legend = t("camping.encounterCuratorRouting"),
                    formRows = listOf(
                        Select(
                            name = "encounterCategoryProxyTableUuid",
                            value = settings.encounterCategoryProxyTableUuid,
                            label = t("camping.encounterCategoryProxyTable"),
                            required = false,
                            options = game.tables.contents.mapNotNull { it.toOption(useUuid = true) },
                            help = t("camping.encounterCategoryProxyTableHelp"),
                            stacked = false,
                        ),
                        CheckboxInput(
                            name = "filterByHexState",
                            label = t("camping.filterByHexState"),
                            value = settings.filterByHexState,
                            help = t("camping.filterByHexStateHelp"),
                            stacked = false,
                        ),
                    )
                ),
                Section(
                    legend = t("camping.encounterCuratorWeights"),
                    formRows = EncounterCategory.allCategories().map { category ->
                        NumberInput(
                            name = category.value,
                            label = t("camping.encounterCategory.${category.value}"),
                            value = weightFor(category),
                            stacked = false,
                        )
                    }
                ),
            )
        )
    }

    override fun onParsedSubmit(value: CategoryWeightSettings): Promise<Void> = buildPromise {
        settings = value
        undefined
    }

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "km-save" -> buildPromise {
                campingActor.getCamping()?.let { camping ->
                    camping.categoryWeights = CategoryWeights(
                        combat = settings.combat, rp = settings.rp, rumor = settings.rumor,
                        merchant = settings.merchant, disease = settings.disease,
                        faction = settings.faction, weather = settings.weather, lore = settings.lore,
                    ).toRaw()
                    camping.encounterCategoryProxyTableUuid = settings.encounterCategoryProxyTableUuid
                    camping.filterByHexState = settings.filterByHexState
                    campingActor.setCamping(camping)
                }
                close()
            }
        }
    }
}
