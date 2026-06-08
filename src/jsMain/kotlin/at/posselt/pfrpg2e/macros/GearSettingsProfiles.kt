package at.posselt.pfrpg2e.macros

import at.posselt.pfrpg2e.app.jsonFilePicker
import at.posselt.pfrpg2e.gearsettings.GearSettingsProfileManager
import at.posselt.pfrpg2e.gearsettings.GearSettingsProfileManagerApplication
import at.posselt.pfrpg2e.gearsettings.GearSettingsProfileRegistryImport
import at.posselt.pfrpg2e.utils.downloadJson
import at.posselt.pfrpg2e.utils.launch
import com.foundryvtt.core.Game
import com.foundryvtt.core.ui

/** Open the CRUD dialog for importing, exporting, activating, and deleting gear-settings profiles. */
suspend fun manageGearSettingsProfilesMacro() {
    GearSettingsProfileManagerApplication().launch()
}

/** Import a gear-settings profile JSON file and activate it immediately. */
suspend fun importGearSettingsProfileMacro(game: Game) {
    val json = jsonFilePicker(
        title = "Import Gear Settings Profile",
        label = "Gear settings profile JSON",
    )
    when (val result = GearSettingsProfileManager(game).importProfile(json, activate = true)) {
        is GearSettingsProfileRegistryImport.Invalid -> ui.notifications.error(result.message)
        is GearSettingsProfileRegistryImport.Valid -> ui.notifications.info("Imported and activated '${result.importedProfile.name}'.")
    }
}

/** Export the currently-active gear-settings profile as JSON. */
suspend fun exportActiveGearSettingsProfileMacro(game: Game) {
    val manager = GearSettingsProfileManager(game)
    val registry = manager.loadRegistry()
    val activeProfileId = registry.activeProfileId
    if (activeProfileId == null) {
        ui.notifications.error("No active gear settings profile is selected.")
        return
    }

    val json = manager.exportProfile(activeProfileId)
    if (json == null) {
        ui.notifications.error("Gear settings profile '$activeProfileId' was not found.")
        return
    }

    downloadJson(JSON.parse(json), "gear-settings-profile-$activeProfileId.json")
}
