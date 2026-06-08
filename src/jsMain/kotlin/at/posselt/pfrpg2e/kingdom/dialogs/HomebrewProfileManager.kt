package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.CrudApplication
import at.posselt.pfrpg2e.app.CrudData
import at.posselt.pfrpg2e.homebrew.HomebrewProfileRegistry
import at.posselt.pfrpg2e.homebrew.HomebrewRulesProfile
import at.posselt.pfrpg2e.homebrew.HomebrewRules
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
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
        val saved = game.settings.get<String?>("pfrpg2eKingdom", "homebrew.profileRegistry")
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
        game.settings.set("pfrpg2eKingdom", "homebrew.profileRegistry", workingRegistry.toJson())
        render()
        undefined
    }

    override fun addEntry(): Promise<Void> = buildPromise {
        // Create a new profile with a temporary id, to be replaced on submit
        val now = Date().toISOString()
        val newProfile = HomebrewRulesProfile(
            id = "new-profile-${Date.now().toLong()}",
            name = t("kingdom.newProfile"),
            version = 1,
            isActive = false,
            createdAt = now,
            updatedAt = now,
            description = null,
            rules = HomebrewRules() // Uses default constructor which provides sensible defaults
        )
        // Add the new profile to the registry
        workingRegistry = workingRegistry.copy(
            profiles = workingRegistry.profiles + newProfile
        )
        // Save the registry to game settings
        game.settings.set("pfrpg2eKingdom", "homebrew.profileRegistry", workingRegistry.toJson())
        render()
        undefined
    }

    override fun editEntry(id: String) = buildPromise {
        // Find the profile to edit
        val profileOption = workingRegistry.profiles.find { it.id == id }
        if (profileOption != null) {
            // For now, we just render again - in a full implementation we'd open a dialog to edit the profile
            render()
        }
        // Save the registry to game settings (even if we didn't change anything, it's okay)
        game.settings.set("pfrpg2eKingdom", "homebrew.profileRegistry", workingRegistry.toJson())
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
        game.settings.set("pfrpg2eKingdom", "homebrew.profileRegistry", workingRegistry.toJson())
        render()
        undefined
    }

    // We don't override _onClickAction because we don't have custom actions in the CrudApplication template.
    // The save happens automatically on every change via saving to game settings.
}