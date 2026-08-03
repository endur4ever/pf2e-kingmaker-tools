package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.CrudApplication
import at.posselt.pfrpg2e.app.CrudData
import at.posselt.pfrpg2e.app.jsonFilePicker
import at.posselt.pfrpg2e.homebrew.HomebrewProfileImportExport
import at.posselt.pfrpg2e.homebrew.HomebrewProfileRegistry
import at.posselt.pfrpg2e.homebrew.HomebrewRegistryImport
import at.posselt.pfrpg2e.homebrew.HomebrewRulesProfile
import at.posselt.pfrpg2e.homebrew.HomebrewRules
import at.posselt.pfrpg2e.settings.pfrpg2eKingdomCampingWeather
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.downloadJson
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import com.foundryvtt.core.ui
import js.objects.recordOf
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.abstract.DataModel
import com.foundryvtt.core.abstract.DocumentConstructionContext
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.data.dsl.buildSchema
import com.foundryvtt.core.utils.deepClone
import js.core.Void
import kotlinx.coroutines.await
import kotlinx.js.JsPlainObject
import org.w3c.dom.HTMLElement
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Date
import kotlin.js.Promise

@JsExport
class HomebrewProfileManagerDataModel(
    value: AnyObject,
    options: DocumentConstructionContext?
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            stringArray("enabledIds")
        }
    }
}

/**
 * Manages the list of homebrew profiles and the active profile.
 * Uses CrudApplication to display profiles and allow adding/editing/deleting.
 * The active profile is selected via the enable checkbox (only one can be active).
 */
class HomebrewProfileManagerApplication(
    private val game: Game,
) : CrudApplication(
    title = t("kingdom.manageProfiles"),
    id = "kmManageProfiles",
    debug = true,
) {
    private var workingRegistry: HomebrewProfileRegistry = HomebrewProfileRegistry()

    init {
        // The registry lives in the registered module setting (see Pfrpg2eKingdomCampingWeatherSettings);
        // reading an unregistered namespace/key throws in Foundry, so never game.settings.get raw here.
        val saved = runCatching { game.settings.pfrpg2eKingdomCampingWeather.getHomebrewProfileRegistry() }.getOrNull()
        if (saved != null) {
            workingRegistry = HomebrewProfileRegistry.fromJson(saved) ?: HomebrewProfileRegistry()
        }
    }

    override fun deleteEntry(id: String) = buildPromise {
        // Remove the profile with the given id
        workingRegistry = workingRegistry.copy(
            profiles = workingRegistry.profiles.filter { it.id != id },
            activeProfileId = if (workingRegistry.activeProfileId == id) null else workingRegistry.activeProfileId
        )
        // Save the registry to game settings
        game.settings.pfrpg2eKingdomCampingWeather.setHomebrewProfileRegistry(workingRegistry.toJson())
        render()
        undefined
    }

    // "Add" imports a profile from a JSON file (mirrors the gear-settings manager). Profiles are
    // authored by importing a shared/backed-up file rather than a blank stub the old edit couldn't fill.
    override fun addEntry(): Promise<Void> = buildPromise {
        val json = jsonFilePicker(
            title = t("kingdom.importHomebrewProfile"),
            label = t("kingdom.homebrewProfileJson"),
        )
        when (val result = HomebrewProfileImportExport.importInto(workingRegistry, json, activate = true)) {
            is HomebrewRegistryImport.Invalid -> ui.notifications.error(result.message)
            is HomebrewRegistryImport.Valid -> {
                workingRegistry = result.registry
                game.settings.pfrpg2eKingdomCampingWeather.setHomebrewProfileRegistry(workingRegistry.toJson())
                ui.notifications.info(
                    t("kingdom.homebrewImported", recordOf("name" to (result.imported.firstOrNull()?.name ?: ""))),
                )
            }
        }
        render()
        undefined
    }

    // "Edit" exports the profile as a downloadable versioned JSON file (mirrors the gear manager).
    override fun editEntry(id: String) = buildPromise {
        val profile = workingRegistry.profiles.find { it.id == id }
        if (profile == null) {
            ui.notifications.error(t("kingdom.homebrewProfileNotFound"))
        } else {
            downloadJson(
                JSON.parse(HomebrewProfileImportExport.exportProfile(profile)),
                "homebrew-profile-$id.json",
            )
        }
        undefined
    }

    override fun getItems(): Promise<Array<at.posselt.pfrpg2e.app.CrudItem>> = buildPromise {
        val items = workingRegistry.profiles.map { profile ->
            val isActive = profile.id == workingRegistry.activeProfileId
            at.posselt.pfrpg2e.app.CrudItem(
                id = profile.id,
                name = profile.name,
                nameIsHtml = false,
                additionalColumns = arrayOf(
                    at.posselt.pfrpg2e.app.CrudColumn(
                        value = profile.version.toString(),
                        escapeHtml = false
                    ),
                    at.posselt.pfrpg2e.app.CrudColumn(
                        value = profile.createdAt.take(10), // Show just date part
                        escapeHtml = false
                    ),
                    at.posselt.pfrpg2e.app.CrudColumn(
                        value = profile.updatedAt.take(10),
                        escapeHtml = false
                    ),
                    at.posselt.pfrpg2e.app.CrudColumn(
                        value = profile.description ?: "",
                        escapeHtml = false
                    )
                ),
                enable = at.posselt.pfrpg2e.app.forms.CheckboxInput(
                    value = isActive,
                    label = t("kingdom.active"),
                    hideLabel = true,
                    name = "activeProfileId.${profile.id}"
                ).toContext(),
                canBeEdited = true,
                canBeDeleted = true
            )
        }
        items.toTypedArray()
    }

    override fun getHeadings(): Promise<Array<String>> = buildPromise {
        arrayOf(
            t("kingdom.version"),
            t("kingdom.created"),
            t("kingdom.updated"),
            t("kingdom.description")
        )
    }

    override fun onParsedSubmit(value: CrudData): Promise<Void> = buildPromise {
        // The enabledIds array should contain at most one id: the active profile.
        val newActiveId = value.enabledIds.firstOrNull()
        // Update the working registry's active profile id
        workingRegistry = workingRegistry.copy(
            activeProfileId = newActiveId
        )
        // Also update the isActive flag in each profile to match
        val profilesWithActiveFlag = workingRegistry.profiles.map { profile ->
            profile.copy(
                isActive = profile.id == newActiveId
            )
        }
        workingRegistry = workingRegistry.copy(
            profiles = profilesWithActiveFlag
        )
        // Save the registry to game settings
        game.settings.pfrpg2eKingdomCampingWeather.setHomebrewProfileRegistry(workingRegistry.toJson())
        render()
        undefined
    }

    // We don't override _onClickAction because we don't have custom actions in the CrudApplication template.
    // The save happens automatically on every change via saving to game settings.
}