package at.posselt.pfrpg2e.gearsettings

import at.posselt.pfrpg2e.Config
import at.posselt.pfrpg2e.app.CrudApplication
import at.posselt.pfrpg2e.app.CrudColumn
import at.posselt.pfrpg2e.app.CrudData
import at.posselt.pfrpg2e.app.CrudItem
import at.posselt.pfrpg2e.app.forms.CheckboxInput
import at.posselt.pfrpg2e.app.jsonFilePicker
import at.posselt.pfrpg2e.settings.Advancement
import at.posselt.pfrpg2e.settings.pfrpg2eKingdomCampingWeather
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.downloadJson
import com.foundryvtt.core.Game
import com.foundryvtt.core.game
import com.foundryvtt.core.ui
import js.core.Void
import kotlinx.coroutines.await
import kotlin.js.Date
import kotlin.js.Promise

const val gearSettingsProfileRegistryKey = "gearSettings.profileRegistry"

/**
 * Foundry-facing persistence and activation layer for gear-settings profiles.
 *
 * Pure JSON import/export validation lives in commonMain; this class is limited
 * to game.settings I/O and applying a profile to the legacy setting keys the
 * rest of the module already reads.
 */
class GearSettingsProfileManager(
    private val game: Game,
) {
    fun loadRegistry(): GearSettingsProfileRegistry {
        val saved = game.settings.get<String?>(Config.moduleId, gearSettingsProfileRegistryKey)
        val decoded = saved?.takeIf { it.isNotBlank() }?.let(GearSettingsProfileRegistry::fromJson)
            ?: GearSettingsProfileRegistry()
        return decoded.withBuiltinProfiles()
    }

    suspend fun saveRegistry(registry: GearSettingsProfileRegistry) {
        game.settings.set(Config.moduleId, gearSettingsProfileRegistryKey, registry.withBuiltinProfiles().toJson()).await()
    }

    fun currentSettings(): GearSettings {
        val settings = game.settings.pfrpg2eKingdomCampingWeather
        return GearSettings(
            kingdom = KingdomSettingsGroup(
                advancement = settings.getAdvancement().value,
                ruinThreshold = settings.getRuinThreshold(),
                eventDc = settings.getEventDc(),
                eventDcStep = settings.getEventDcStep(),
                leadershipActivityCap = settings.getLeadershipActivityCap(),
                leadershipActivityCapWithTownhall = settings.getLeadershipActivityCapWithTownhall(),
                canUpgradeNonCapital = settings.getCanUpgradeNonCapital(),
                capitalCanGrowOneSizeLarger = settings.getCapitalCanGrowOneSizeLarger(),
                noRandomCombatInClaimedHexes = settings.getNoRandomCombatInClaimedHexes(),
                capStructureBonusAtKingdomLevel = settings.getCapStructureBonusAtKingdomLevel(),
                settlementInfluenceRadius = settings.getSettlementInfluenceRadius(),
                cultOfTheBloomEvents = settings.getCultOfTheBloomEvents(),
            ),
            camping = CampingSettingsGroup(
                campingActivityCountByPartySize = settings.getCampingActivityCountByPartySize(),
                enableSheltered = settings.getEnableSheltered(),
                enableWeather = settings.getEnableWeather(),
            ),
            hex = HexSettingsGroup(
                travelCostRiverNoBridgeAdditional = settings.getTravelCostRiverNoBridgeAdditional(),
                pavedStreetsReduceTravelCost = settings.getPavedStreetsReduceTravelCost(),
                hexMapEnabled = settings.getHexMapEnabled(),
            ),
            army = ArmySettingsGroup(
                enableCombatTracks = settings.getEnableCombatTracks(),
            ),
            ui = UiSettingsGroup(
                hideBuiltinKingdomSheet = settings.getHideBuiltinKingdomSheet(),
                enablePartyActorIcons = settings.getEnablePartyActorIcons(),
                enableWeatherSoundFx = settings.getEnableWeatherSoundFx(),
                enableTokenMapping = settings.getEnableTokenMapping(),
                disableFirstRunMessage = settings.getDisableFirstRunMessage(),
            ),
        )
    }

    suspend fun applySettings(settings: GearSettings) {
        val foundrySettings = game.settings.pfrpg2eKingdomCampingWeather
        foundrySettings.setAdvancement(Advancement.fromString(settings.kingdom.advancement) ?: Advancement.XP)
        foundrySettings.setRuinThreshold(settings.kingdom.ruinThreshold)
        foundrySettings.setEventDc(settings.kingdom.eventDc)
        foundrySettings.setEventDcStep(settings.kingdom.eventDcStep)
        foundrySettings.setLeadershipActivityCap(settings.kingdom.leadershipActivityCap)
        foundrySettings.setLeadershipActivityCapWithTownhall(settings.kingdom.leadershipActivityCapWithTownhall)
        foundrySettings.setCanUpgradeNonCapital(settings.kingdom.canUpgradeNonCapital)
        foundrySettings.setCapitalCanGrowOneSizeLarger(settings.kingdom.capitalCanGrowOneSizeLarger)
        foundrySettings.setNoRandomCombatInClaimedHexes(settings.kingdom.noRandomCombatInClaimedHexes)
        foundrySettings.setCapStructureBonusAtKingdomLevel(settings.kingdom.capStructureBonusAtKingdomLevel)
        foundrySettings.setSettlementInfluenceRadius(settings.kingdom.settlementInfluenceRadius)
        foundrySettings.setCultOfTheBloomEvents(settings.kingdom.cultOfTheBloomEvents)
        foundrySettings.setCampingActivityCountByPartySize(settings.camping.campingActivityCountByPartySize)
        foundrySettings.setEnableSheltered(settings.camping.enableSheltered)
        foundrySettings.setEnableWeather(settings.camping.enableWeather)
        foundrySettings.setTravelCostRiverNoBridgeAdditional(settings.hex.travelCostRiverNoBridgeAdditional)
        foundrySettings.setPavedStreetsReduceTravelCost(settings.hex.pavedStreetsReduceTravelCost)
        foundrySettings.setHexMapEnabled(settings.hex.hexMapEnabled)
        foundrySettings.setEnableCombatTracks(settings.army.enableCombatTracks)
        foundrySettings.setHideBuiltinKingdomSheet(settings.ui.hideBuiltinKingdomSheet)
        foundrySettings.setEnablePartyActorIcons(settings.ui.enablePartyActorIcons)
        foundrySettings.setEnableWeatherSoundFx(settings.ui.enableWeatherSoundFx)
        foundrySettings.setEnableTokenMapping(settings.ui.enableTokenMapping)
        foundrySettings.setDisableFirstRunMessage(settings.ui.disableFirstRunMessage)
    }

    suspend fun createProfileFromCurrentSettings(name: String = "Current Gear Settings"): GearSettingsProfileRegistry {
        val now = Date().toISOString()
        val registry = loadRegistry()
        val profile = GearSettingsProfile(
            id = uniqueId(name, registry),
            name = name,
            description = "Captured from the current world gear settings.",
            isBuiltin = false,
            createdAt = now,
            updatedAt = now,
            settings = currentSettings(),
        )
        val next = registry.copy(
            activeProfileId = profile.id,
            profiles = registry.profiles + profile,
        )
        saveRegistry(next)
        return next
    }

    suspend fun seedRegistryFromCurrentSettings(createdAt: String): GearSettingsProfileRegistry {
        val seeded = GearSettingsProfileImportExport.seedRegistryFromCurrentSettings(
            registry = loadRegistry(),
            currentSettings = currentSettings(),
            createdAt = createdAt,
        )
        saveRegistry(seeded)
        return seeded
    }

    suspend fun activateProfile(id: String): Boolean {
        val registry = loadRegistry()
        val profile = registry.profiles.find { it.id == id } ?: return false
        applySettings(profile.settings)
        saveRegistry(registry.copy(activeProfileId = id))
        return true
    }

    suspend fun deleteProfile(id: String): Boolean {
        val registry = loadRegistry()
        val profile = registry.profiles.find { it.id == id } ?: return false
        if (profile.isBuiltin) return false
        saveRegistry(
            registry.copy(
                activeProfileId = if (registry.activeProfileId == id) null else registry.activeProfileId,
                profiles = registry.profiles.filterNot { it.id == id },
            )
        )
        return true
    }

    suspend fun importProfile(text: String, activate: Boolean = true): GearSettingsProfileRegistryImport {
        return when (val imported = GearSettingsProfileImportExport.importProfile(loadRegistry(), text, activate)) {
            is GearSettingsProfileRegistryImport.Invalid -> imported
            is GearSettingsProfileRegistryImport.Valid -> {
                saveRegistry(imported.registry)
                if (activate) applySettings(imported.importedProfile.settings)
                imported
            }
        }
    }

    fun exportProfile(id: String): String? =
        loadRegistry().profiles.find { it.id == id }?.let(GearSettingsProfileImportExport::exportProfile)

    private fun GearSettingsProfileRegistry.withBuiltinProfiles(): GearSettingsProfileRegistry {
        val builtins = listOf(GearSettingsProfile.raw(), GearSettingsProfile.vanceKerenshara())
        val custom = profiles.filterNot { profile -> builtins.any { it.id == profile.id } }
        return copy(
            schemaVersion = GearSettingsProfileImportExport.currentSchemaVersion,
            profiles = builtins + custom,
        )
    }

    private fun uniqueId(name: String, registry: GearSettingsProfileRegistry): String {
        val base = name.lowercase()
            .replace(Regex("[^a-z0-9_-]+"), "-")
            .trim('-')
            .ifBlank { "gear-settings-profile" }
        val existing = registry.profiles.map { it.id }.toSet()
        if (base !in existing) return base
        for (index in 1..10_000) {
            val candidate = "$base-$index"
            if (candidate !in existing) return candidate
        }
        return "gear-settings-profile-${registry.profiles.size + 1}"
    }
}

class GearSettingsProfileManagerApplication : CrudApplication(
    title = "Manage Gear Settings Profiles",
    id = "kmManageGearSettingsProfiles",
    debug = true,
) {
    private val manager = GearSettingsProfileManager(game)

    override fun addEntry(): Promise<Void> = buildPromise {
        val json = jsonFilePicker(
            title = "Import Gear Settings Profile",
            label = "Gear settings profile JSON",
        )
        when (val result = manager.importProfile(json, activate = true)) {
            is GearSettingsProfileRegistryImport.Invalid -> ui.notifications.error(result.message)
            is GearSettingsProfileRegistryImport.Valid -> ui.notifications.info("Imported and activated '${result.importedProfile.name}'.")
        }
        render()
        undefined
    }

    override fun deleteEntry(id: String): Promise<Void> = buildPromise {
        if (!manager.deleteProfile(id)) {
            ui.notifications.error("Built-in gear settings profiles cannot be deleted.")
        }
        render()
        undefined
    }

    override fun editEntry(id: String): Promise<Void> = buildPromise {
        val json = manager.exportProfile(id)
        if (json == null) {
            ui.notifications.error("Gear settings profile '$id' was not found.")
        } else {
            downloadJson(JSON.parse(json), "gear-settings-profile-$id.json")
        }
        undefined
    }

    override fun getItems(): Promise<Array<CrudItem>> = buildPromise {
        val registry = manager.loadRegistry()
        registry.profiles.map { profile ->
            CrudItem(
                id = profile.id,
                name = profile.name,
                nameIsHtml = false,
                additionalColumns = arrayOf(
                    CrudColumn(value = if (profile.isBuiltin) "Built-in" else "Custom", escapeHtml = false),
                    CrudColumn(value = profile.createdAt.take(10), escapeHtml = false),
                    CrudColumn(value = profile.updatedAt.take(10), escapeHtml = false),
                    CrudColumn(value = profile.description ?: "", escapeHtml = false),
                ),
                enable = CheckboxInput(
                    value = profile.id == registry.activeProfileId,
                    label = "Active",
                    hideLabel = true,
                    name = "activeProfileId.${profile.id}",
                ).toContext(),
                canBeEdited = true,
                canBeDeleted = !profile.isBuiltin,
            )
        }.toTypedArray()
    }

    override fun getHeadings(): Promise<Array<String>> = buildPromise {
        arrayOf("Type", "Created", "Updated", "Description")
    }

    override fun onParsedSubmit(value: CrudData): Promise<Void> = buildPromise {
        val selectedId = value.enabledIds.firstOrNull()
        if (selectedId != null && !manager.activateProfile(selectedId)) {
            ui.notifications.error("Gear settings profile '$selectedId' was not found.")
        }
        render()
        undefined
    }
}
