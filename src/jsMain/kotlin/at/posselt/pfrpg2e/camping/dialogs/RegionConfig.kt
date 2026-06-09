package at.posselt.pfrpg2e.camping.dialogs

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.Button
import at.posselt.pfrpg2e.app.forms.DataAttribute
import at.posselt.pfrpg2e.app.forms.FormElementContext
import at.posselt.pfrpg2e.app.forms.Select
import at.posselt.pfrpg2e.app.forms.TextInput
import at.posselt.pfrpg2e.app.forms.toOption
import at.posselt.pfrpg2e.camping.CampingActor
import at.posselt.pfrpg2e.camping.EncounterCategory
import at.posselt.pfrpg2e.camping.getCamping
import at.posselt.pfrpg2e.camping.setCamping
import at.posselt.pfrpg2e.data.regions.Terrain
import at.posselt.pfrpg2e.fromCamelCase
import at.posselt.pfrpg2e.toCamelCase
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.fromUuidTypeSafe
import at.posselt.pfrpg2e.utils.t
import at.posselt.pfrpg2e.utils.typeSafeUpdate
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.abstract.DataModel
import com.foundryvtt.core.abstract.DocumentConstructionContext
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.data.dsl.buildSchema
import com.foundryvtt.core.documents.Playlist
import com.foundryvtt.core.documents.PlaylistSound
import com.foundryvtt.core.game
import kotlinx.coroutines.await
import kotlinx.js.JsPlainObject
import js.objects.Record
import js.objects.recordOf
import org.w3c.dom.HTMLElement
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.collections.plus
import kotlin.js.Promise

@JsPlainObject
external interface Track {
    var playlistUuid: String
    var trackUuid: String?
}

@JsPlainObject
external interface RegionSetting {
    var name: String
    var zoneDc: Int
    var encounterDc: Int
    var level: Int
    var terrain: String
    var rollTableUuid: String?
    var combatTrack: Track?

    /**
     * Roadmap #11: per-category encounter roll table UUIDs keyed by
     * [at.posselt.pfrpg2e.camping.EncounterCategory.value]. Nullable for
     * backwards compatibility; read via `categoryRollTableUuidMap()`.
     */
    var categoryRollTableUuids: Record<String, String?>?

    /** Roadmap #11: suppress encounters in claimed+cleared hexes for this region. */
    var suppressEncountersOnClearedHex: Boolean?
}

@JsPlainObject
external interface RegionSettings {
    var regions: Array<RegionSetting>
}

@JsPlainObject
external interface TableHead {
    var label: String
    var classes: Array<String>?
}

@Suppress("unused")
@JsPlainObject
external interface RegionSettingsContext : ValidatedHandlebarsContext {
    var heading: Array<TableHead>
    var formRows: Array<Array<FormElementContext>>
    var allowDelete: Boolean
}

@JsExport
class RegionSettingsDataModel(
    value: AnyObject,
    options: DocumentConstructionContext?
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            array("regions") {
                schema {
                    string("name")
                    int("zoneDc")
                    int("encounterDc")
                    int("level")
                    string("rollTableUuid", nullable = true)
                    schema("combatTrack") {
                        string("playlistUuid", nullable = true)
                        string("trackUuid", nullable = true)
                    }
                    schema("categoryRollTableUuids") {
                        string("combat", nullable = true)
                        string("rumor", nullable = true)
                        string("merchant", nullable = true)
                    }
                    string("terrain")
                }
            }
        }
    }
}


/**
 * Merge a region's per-category roll tables: the matrix renders combat/rumor/merchant,
 * so those come from [matrix]; the remaining five are preserved from the prior [old]
 * settings (they're only editable via the per-region encounter-tables sub-dialog).
 */
private fun mergeRegionCategoryTables(
    old: Record<String, String?>?,
    matrix: Record<String, String?>?,
): Record<String, String?> {
    val matrixCategories = setOf(
        EncounterCategory.COMBAT.value,
        EncounterCategory.RUMOR.value,
        EncounterCategory.MERCHANT.value,
    )
    val pairs = EncounterCategory.entries.map { category ->
        val source = if (category.value in matrixCategories) matrix else old
        category.value to source?.get(category.value)
    }.toTypedArray()
    return recordOf(*pairs)
}

private fun allCategoryTablesNull(tables: Record<String, String?>): Boolean =
    EncounterCategory.entries.all { tables[it.value] == null }

@JsExport
@JsName("RegionConfig")
class RegionConfig(
    private val actor: CampingActor,
) : FormApp<RegionSettingsContext, RegionSettings>(
    title = t("camping.regions"),
    width = 1200,
    template = "applications/settings/configure-regions.hbs",
    dataModel = RegionSettingsDataModel::class.js,
    debug = true,
    id = "kmRegions-${actor.uuid}"
) {
    private var currentSettings = actor.getCamping()!!.regionSettings

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (val action = target.dataset["action"]) {
            "km-save" -> {
                buildPromise {
                    actor.getCamping()?.let { camping ->
                        camping.regionSettings = currentSettings
                        if (!camping.regionSettings.regions.any { it.name == camping.currentRegion }) {
                            camping.currentRegion = currentSettings.regions.first().name
                        }
                        actor.setCamping(camping)
                    }
                    close()
                }
            }

            "add" -> {
                addDefaultRegion()
                render()
            }

            "delete" -> {
                target.dataset["index"]?.toInt()?.let {
                    currentSettings.regions = currentSettings.regions
                        .filterIndexed { index, _ -> index != it }
                        .toTypedArray()
                    render()
                }
            }

            "configure-region-tables" -> {
                target.dataset["index"]?.toInt()?.let { index ->
                    currentSettings.regions.getOrNull(index)?.let { region ->
                        val rollTableOptions = game.tables.contents
                            .mapNotNull { it.toOption(useUuid = true) }
                            .sortedBy { it.label }
                        configureRegionEncounterTables(
                            regionName = region.name,
                            tables = region.categoryRollTableUuids,
                            suppress = region.suppressEncountersOnClearedHex == true,
                            rollTableOptions = rollTableOptions,
                        ) { tables, suppress ->
                            region.categoryRollTableUuids = tables
                            region.suppressEncountersOnClearedHex = suppress
                            render()
                        }
                    }
                }
            }

            else -> console.log(action)
        }
    }

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions
    ): Promise<RegionSettingsContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        val playlistOptions = game.playlists.contents
            .mapNotNull { it.toOption(useUuid = true) }
            .sortedBy { it.label }
        val rollTableOptions = game.tables.contents
            .mapNotNull { it.toOption(useUuid = true) }
            .sortedBy { it.label }
        RegionSettingsContext(
            partId = parent.partId,
            isFormValid = isFormValid,
            heading = arrayOf(
                TableHead(t("applications.name")),
                TableHead(t("applications.level"), arrayOf("number-select-heading")),
                TableHead(t("camping.terrain")),
                TableHead(t("camping.zoneDc"), arrayOf("number-select-heading")),
                TableHead(t("camping.encounterDc"), arrayOf("number-select-heading")),
                TableHead(t("camping.rollTable")),
                TableHead(t("camping.encounterCategoryTable.combat"), arrayOf("category-table-heading")),
                TableHead(t("camping.encounterCategoryTable.rumor"), arrayOf("category-table-heading")),
                TableHead(t("camping.encounterCategoryTable.merchant"), arrayOf("category-table-heading")),
                TableHead(t("camping.combatPlaylist")),
                TableHead(t("camping.combatTrack")),
                TableHead(t("camping.regionEncounterTables"), arrayOf("small-heading")),
                TableHead(t("applications.delete"), arrayOf("small-heading"))
            ),
            allowDelete = currentSettings.regions.size > 1,
            formRows = currentSettings.regions.mapIndexed { index, row ->
                val trackOptions = row.combatTrack?.playlistUuid?.let { uuid ->
                    game.playlists.find { it.uuid == uuid }?.sounds?.contents?.mapNotNull { it.toOption(useUuid = true) }
                        ?: emptyList()
                } ?: emptyList()
                arrayOf(
                    TextInput(
                        name = "regions.$index.name",
                        label = t("applications.name"),
                        value = row.name,
                        hideLabel = true
                    ).toContext(),
                    Select.level(
                        name = "regions.$index.level",
                        value = row.level,
                        hideLabel = true
                    ).toContext(),
                    Select.fromEnum<Terrain>(
                        name = "regions.$index.terrain",
                        value = fromCamelCase<Terrain>(row.terrain),
                        hideLabel = true
                    ).toContext(),
                    Select.dc(
                        name = "regions.$index.zoneDc",
                        label = t("camping.zoneDc"),
                        value = row.zoneDc,
                        hideLabel = true
                    ).toContext(),
                    Select.flatCheck(
                        name = "regions.$index.encounterDc",
                        label = t("camping.encounterDc"),
                        value = row.encounterDc,
                        hideLabel = true,
                    ).toContext(),
                    Select(
                        name = "regions.$index.rollTableUuid",
                        label = t("camping.rollTable"),
                        value = row.rollTableUuid,
                        required = false,
                        hideLabel = true,
                        options = rollTableOptions,
                    ).toContext(),
                    Select(
                        name = "regions.$index.categoryRollTableUuids.combat",
                        label = t("camping.encounterCategoryTable.combat"),
                        value = row.categoryRollTableUuids?.get("combat"),
                        required = false,
                        hideLabel = true,
                        options = rollTableOptions,
                    ).toContext(),
                    Select(
                        name = "regions.$index.categoryRollTableUuids.rumor",
                        label = t("camping.encounterCategoryTable.rumor"),
                        value = row.categoryRollTableUuids?.get("rumor"),
                        required = false,
                        hideLabel = true,
                        options = rollTableOptions,
                    ).toContext(),
                    Select(
                        name = "regions.$index.categoryRollTableUuids.merchant",
                        label = t("camping.encounterCategoryTable.merchant"),
                        value = row.categoryRollTableUuids?.get("merchant"),
                        required = false,
                        hideLabel = true,
                        options = rollTableOptions,
                    ).toContext(),
                    Select(
                        name = "regions.$index.combatTrack.playlistUuid",
                        label = t("camping.combatPlaylist"),
                        value = row.combatTrack?.playlistUuid,
                        required = false,
                        hideLabel = true,
                        options = playlistOptions
                    ).toContext(),
                    Select(
                        name = "regions.$index.combatTrack.trackUuid",
                        label = t("camping.combatTrack"),
                        value = row.combatTrack?.trackUuid,
                        required = false,
                        hideLabel = true,
                        options = trackOptions
                    ).toContext(),
                    Button(
                        value = "configure-region-tables",
                        label = "",
                        icon = "fa-solid fa-dice-d20",
                        data = listOf(DataAttribute(key = "index", value = index.toString())),
                    ).toContext(),
                )
            }.toTypedArray()
        )
    }

    override fun onParsedSubmit(value: RegionSettings) = buildPromise {
        // unfortunately there is no way to make an object optional if all of its properties are null
        value.regions.forEachIndexed { index, region ->
            val old = currentSettings.regions.getOrNull(index)
            if (region.combatTrack?.playlistUuid == null) {
                region.combatTrack = null
            }
            // The matrix only renders combat/rumor/merchant; the other five category
            // tables and the cleared-hex toggle are edited via the per-region sub-dialog.
            // Preserve them from the prior settings so saving the matrix doesn't wipe them.
            region.categoryRollTableUuids =
                mergeRegionCategoryTables(old?.categoryRollTableUuids, region.categoryRollTableUuids)
            region.suppressEncountersOnClearedHex = old?.suppressEncountersOnClearedHex
            if (region.categoryRollTableUuids?.let { allCategoryTablesNull(it) } == true) {
                region.categoryRollTableUuids = null
            }
        }
        currentSettings = value
        if (currentSettings.regions.isEmpty()) {
            addDefaultRegion()
        }
        null
    }

    private fun addDefaultRegion() {
        currentSettings.regions = currentSettings.regions + RegionSetting(
            name = t("camping.newRegion"),
            zoneDc = 15,
            encounterDc = 12,
            level = 1,
            rollTableUuid = null,
            combatTrack = null,
            terrain = Terrain.PLAINS.toCamelCase(),
        )
    }

}

suspend fun Track.play() {
    val track = trackUuid
    if (track != null) {
        fromUuidTypeSafe<PlaylistSound>(track)
            ?.typeSafeUpdate { playing = true }
    } else {
        fromUuidTypeSafe<Playlist>(playlistUuid)
            ?.playAll()
    }
}

suspend fun Track.stop() {
    val track = trackUuid
    if (track != null) {
        fromUuidTypeSafe<PlaylistSound>(track)
            ?.typeSafeUpdate { playing = false }
    } else {
        fromUuidTypeSafe<Playlist>(playlistUuid)
            ?.stopAll()
    }
}
