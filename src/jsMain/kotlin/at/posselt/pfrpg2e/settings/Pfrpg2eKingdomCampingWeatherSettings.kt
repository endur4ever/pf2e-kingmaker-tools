package at.posselt.pfrpg2e.settings

import at.posselt.pfrpg2e.Config
import at.posselt.pfrpg2e.data.ValueEnum
import at.posselt.pfrpg2e.data.checks.RollMode
import at.posselt.pfrpg2e.data.kingdom.KingdomSizeType
import at.posselt.pfrpg2e.fromCamelCase
import at.posselt.pfrpg2e.gearsettings.GearSettingsProfileManagerApplication
import at.posselt.pfrpg2e.gearsettings.gearSettingsProfileRegistryKey
import at.posselt.pfrpg2e.localization.Translatable
import at.posselt.pfrpg2e.toCamelCase
import at.posselt.pfrpg2e.utils.newInstance
import at.posselt.pfrpg2e.utils.t
import at.posselt.pfrpg2e.utils.toMutableRecord
import com.foundryvtt.core.abstract.DataModel
import com.foundryvtt.core.applications.api.ApplicationV2
import com.foundryvtt.core.game
import com.foundryvtt.core.helpers.ClientSettings
import com.foundryvtt.core.helpers.SettingsData
import com.foundryvtt.core.helpers.SettingsMenuData
//import js.core.JsNumber
import js.objects.ReadonlyRecord
import js.objects.toReadonlyRecord
import kotlinx.coroutines.await
import kotlin.enums.enumEntries

enum class SettingsScope {
    CLIENT,
    WORLD;

    companion object {
        fun fromString(value: String) = fromCamelCase<KingdomSizeType>(value)
    }

    val value: String
        get() = toCamelCase()
}

inline fun <reified T : DataModel> ClientSettings.registerDataModel(
    key: String,
    name: String,
    hint: String? = undefined,
    requiresReload: Boolean = false,
) {
    register(
        Config.moduleId,
        key,
        SettingsData(
            name = name,
            hint = hint,
            config = false,
            requiresReload = requiresReload,
            default = T::class.js.newInstance(emptyArray()).toObject(),
            type = T::class.js,
            scope = "world"
        )
    )
}

fun ClientSettings.registerInt(
    key: String,
    name: String,
    hint: String? = undefined,
    default: Int = 0,
    hidden: Boolean = false,
    requiresReload: Boolean = false,
    choices: ReadonlyRecord<String, Int>? = undefined,
) {
    register(
        Config.moduleId,
        key,
        SettingsData(
            name = name,
            hint = hint,
            config = !hidden,
            default = default,
            requiresReload = requiresReload,
            type = at.posselt.pfrpg2e.utils.JsNumber::class.js,
            scope = "world",
            choices = choices,
        )
    )
}

inline fun <reified T : Any> ClientSettings.registerScalar(
    key: String,
    name: String,
    hint: String? = undefined,
    default: T? = undefined,
    hidden: Boolean = false,
    requiresReload: Boolean = false,
    choices: ReadonlyRecord<String, T>? = undefined,
    scope: SettingsScope = SettingsScope.WORLD,
) {
    register(
        Config.moduleId,
        key,
        SettingsData(
            name = name,
            hint = hint,
            config = !hidden,
            default = default,
            requiresReload = requiresReload,
            type = T::class.js,
            scope = scope.value,
            choices = choices,
        )
    )
}

inline fun <reified T> ClientSettings.registerEnum(
    key: String,
    name: String? = null,
    hint: String? = undefined,
    default: T? = undefined,
    hidden: Boolean = false,
    requiresReload: Boolean = false,
    scope: SettingsScope = SettingsScope.WORLD,
) where T : Enum<T>, T : Translatable, T: ValueEnum {
    register(
        Config.moduleId,
        key,
        SettingsData(
            name = name ?: t("enums." + T::class.simpleName?.lowercase()),
            hint = hint,
            config = !hidden,
            default = default?.value,
            requiresReload = requiresReload,
            type = String::class.js,
            scope = scope.value,
            choices = enumEntries<T>().map {
                it.value to t(it.i18nKey)
            }.toReadonlyRecord(),
        )
    )
}

fun ClientSettings.createMenu(
    key: String,
    name: String,
    label: String,
    hint: String? = undefined,
    icon: String? = undefined,
    restricted: Boolean = false,
    app: JsClass<out ApplicationV2>
) {
    registerMenu(
        Config.moduleId,
        key,
        SettingsMenuData(
            name = name,
            label = label,
            hint = hint,
            icon = icon,
            type = app,
            restricted = restricted,
        )
    )
}

fun ClientSettings.getInt(key: String): Int =
    get(Config.moduleId, key)

suspend fun ClientSettings.setInt(key: String, value: Int) {
    set(Config.moduleId, key, value).await()
}

fun ClientSettings.getString(key: String): String =
    get(Config.moduleId, key)

suspend fun ClientSettings.setString(key: String, value: String) {
    set(Config.moduleId, key, value).await()
}

fun ClientSettings.getNullableString(key: String): String? =
    get(Config.moduleId, key)

suspend fun ClientSettings.setNullableString(key: String, value: String?) {
    set(Config.moduleId, key, value).await()
}


fun ClientSettings.getBoolean(key: String): Boolean =
    get(Config.moduleId, key)

suspend fun ClientSettings.setBoolean(key: String, value: Boolean) {
    set(Config.moduleId, key, value).await()
}

fun <T : Any> ClientSettings.getObject(key: String): T =
    get(Config.moduleId, key)

suspend fun ClientSettings.setObject(key: String, value: Any) {
    set(Config.moduleId, key, value).await()
}

val ClientSettings.pfrpg2eKingdomCampingWeather: Pfrpg2eKingdomCampingWeatherSettings
    get() = Pfrpg2eKingdomCampingWeatherSettings

@Suppress("unused", "ClassName")
object Pfrpg2eKingdomCampingWeatherSettings {
    fun resolveKingdomBackground(activeSettlementType: String): String? =
        if (activeSettlementType == "none" || activeSettlementType.isBlank()) null
        else "${game.settings.getString("artDirectory").trimEnd('/')}/kingdom/backgrounds/$activeSettlementType.webp"

    fun resolveCampingBackground(terrain: String, isDay: Boolean): String {
        val timeOfDay = if (isDay) "day" else "night"
        return "${game.settings.getString("artDirectory").trimEnd('/')}/camping/backgrounds/$terrain-$timeOfDay.webp"
    }

    suspend fun setKingdomActiveLeader(value: String?) =
        game.settings.setNullableString("kingdomActiveLeader", value)

    fun getKingdomActiveLeader(): String? =
        game.settings.getNullableString("kingdomActiveLeader")

    suspend fun setEnableAfterCombatDialog(value: Boolean) =
        game.settings.setBoolean("enableAfterCombatDialog", value)

    fun getEnableAfterCombatDialog(): Boolean =
        game.settings.getBoolean("enableAfterCombatDialog")

    suspend fun setAdvancement(value: Advancement) =
        game.settings.setString("advancement", value.value)

    fun getAdvancement(): Advancement =
        Advancement.fromString(game.settings.getString("advancement")) ?: Advancement.XP

    suspend fun setEnablePartyActorIcons(value: Boolean) =
        game.settings.setBoolean("enablePartyActorIcons", value)

    fun getEnablePartyActorIcons(): Boolean =
        game.settings.getBoolean("enablePartyActorIcons")

    suspend fun setEnableTokenMapping(value: Boolean) =
        game.settings.setBoolean("enableTokenMapping", value)

    fun getEnableTokenMapping(): Boolean =
        game.settings.getBoolean("enableTokenMapping")

    suspend fun setLatestMigrationBackup(value: String) =
        game.settings.setString("latestMigrationBackup", value)

    fun getLatestMigrationBackup(): String =
        game.settings.getString("latestMigrationBackup")

    suspend fun setCampaignMapSceneIds(value: String) =
        game.settings.setString("campaignMapSceneIds", value)

    fun getCampaignMapSceneIds(): String =
        game.settings.getString("campaignMapSceneIds")

    suspend fun setSchemaVersion(value: Int) =
        game.settings.setInt("schemaVersion", value)

    fun getSchemaVersion(): Int =
        game.settings.getInt("schemaVersion")

    suspend fun setWeatherHazardRange(value: Int) =
        game.settings.setInt("weatherHazardRange", value)

    fun getWeatherHazardRange(): Int =
        game.settings.getInt("weatherHazardRange")

    suspend fun setWeatherRollMode(value: RollMode) =
        game.settings.setString("weatherRollMode", value.toCamelCase())

    fun getWeatherRollMode(): RollMode =
        fromCamelCase<RollMode>(game.settings.getString("weatherRollMode"))
            ?: throw IllegalStateException("Null value set for setting 'weatherRollMode'")

    suspend fun setEnableWeatherSoundFx(value: Boolean) =
        game.settings.setBoolean("enableWeatherSoundFx", value)

    fun getEnableWeatherSoundFx(): Boolean =
        game.settings.getBoolean("enableWeatherSoundFx")

    suspend fun setEnableCombatTracks(value: Boolean) =
        game.settings.setBoolean("enableCombatTracks", value)

    fun getEnableCombatTracks(): Boolean =
        game.settings.getBoolean("enableCombatTracks")

    suspend fun setDisableFirstRunMessage(value: Boolean) =
        game.settings.setBoolean("disableFirstRunMessage", value)

    fun getDisableFirstRunMessage(): Boolean =
        game.settings.getBoolean("disableFirstRunMessage")

    suspend fun setEnableSheltered(value: Boolean) =
        game.settings.setBoolean("enableSheltered", value)

    fun getEnableSheltered(): Boolean =
        game.settings.getBoolean("enableSheltered")

    suspend fun setEnableWeather(value: Boolean) =
        game.settings.setBoolean("enableWeather", value)

    fun getEnableWeather(): Boolean =
        game.settings.getBoolean("enableWeather")

    fun getHideBuiltinKingdomSheet(): Boolean =
        game.settings.getBoolean("hideBuiltinKingdomSheet")

    suspend fun setHideBuiltinKingdomSheet(value: Boolean) =
        game.settings.setBoolean("hideBuiltinKingdomSheet", value)

    suspend fun setClimateSettings(settings: ClimateSettings) =
        game.settings.setObject("climate", settings)

    fun getClimateSettings(): ClimateSettings =
        game.settings.getObject("climate")

    suspend fun setCurrentWeatherFx(value: String) =
        game.settings.setString("currentWeatherFx", value)

    fun getCurrentWeatherFx(): String =
        game.settings.getString("currentWeatherFx")

    suspend fun setObsidianVaultName(value: String) =
        game.settings.setString("obsidianVaultName", value)

    fun getObsidianVaultName(): String =
        game.settings.getNullableString("obsidianVaultName") ?: ""

    suspend fun setObsidianJournalFolder(value: String) =
        game.settings.setString("obsidianJournalFolder", value)

    fun getObsidianJournalFolder(): String =
        game.settings.getNullableString("obsidianJournalFolder") ?: "Kingdom Export"

    suspend fun setObsidianOverwrite(value: Boolean) =
        game.settings.setBoolean("obsidianOverwrite", value)

    fun getObsidianOverwrite(): Boolean =
        try { game.settings.getBoolean("obsidianOverwrite") } catch (_: Throwable) { true }


    suspend fun setCurrentWeatherType(value: String) =
        game.settings.setString("currentWeatherType", value)

    fun getCurrentWeatherType(): String =
        game.settings.getString("currentWeatherType")

    suspend fun setGearSettingsProfileRegistry(value: String) =
        game.settings.setString(gearSettingsProfileRegistryKey, value)

    fun getGearSettingsProfileRegistry(): String =
        game.settings.getString(gearSettingsProfileRegistryKey)

    suspend fun setRuinThreshold(value: Int) =
        game.settings.setInt("ruinThreshold", value)

    fun getRuinThreshold(): Int =
        game.settings.getInt("ruinThreshold")

    suspend fun setEventDc(value: Int) =
        game.settings.setInt("eventDc", value)

    fun getEventDc(): Int =
        game.settings.getInt("eventDc")

    suspend fun setEventDcStep(value: Int) =
        game.settings.setInt("eventDcStep", value)

    fun getEventDcStep(): Int =
        game.settings.getInt("eventDcStep")

    suspend fun setLeadershipActivityCap(value: Int) =
        game.settings.setInt("leadershipActivityCap", value)

    fun getLeadershipActivityCap(): Int =
        game.settings.getInt("leadershipActivityCap")

    suspend fun setLeadershipActivityCapWithTownhall(value: Int) =
        game.settings.setInt("leadershipActivityCapWithTownhall", value)

    fun getLeadershipActivityCapWithTownhall(): Int =
        game.settings.getInt("leadershipActivityCapWithTownhall")

    suspend fun setCanUpgradeNonCapital(value: Boolean) =
        game.settings.setBoolean("canUpgradeNonCapital", value)

    fun getCanUpgradeNonCapital(): Boolean =
        game.settings.getBoolean("canUpgradeNonCapital")

    suspend fun setCapitalCanGrowOneSizeLarger(value: Boolean) =
        game.settings.setBoolean("capitalCanGrowOneSizeLarger", value)

    fun getCapitalCanGrowOneSizeLarger(): Boolean =
        game.settings.getBoolean("capitalCanGrowOneSizeLarger")

    suspend fun setNoRandomCombatInClaimedHexes(value: Boolean) =
        game.settings.setBoolean("noRandomCombatInClaimedHexes", value)

    fun getNoRandomCombatInClaimedHexes(): Boolean =
        game.settings.getBoolean("noRandomCombatInClaimedHexes")

    suspend fun setCapStructureBonusAtKingdomLevel(value: Boolean) =
        game.settings.setBoolean("capStructureBonusAtKingdomLevel", value)

    fun getCapStructureBonusAtKingdomLevel(): Boolean =
        game.settings.getBoolean("capStructureBonusAtKingdomLevel")

    suspend fun setSettlementInfluenceRadius(value: Int) =
        game.settings.setInt("settlementInfluenceRadius", value)

    fun getSettlementInfluenceRadius(): Int =
        game.settings.getInt("settlementInfluenceRadius")

    suspend fun setCultOfTheBloomEvents(value: Boolean) =
        game.settings.setBoolean("cultOfTheBloomEvents", value)

    fun getCultOfTheBloomEvents(): Boolean =
        game.settings.getBoolean("cultOfTheBloomEvents")

    suspend fun setCampingActivityCountByPartySize(value: Boolean) =
        game.settings.setBoolean("campingActivityCountByPartySize", value)

    fun getCampingActivityCountByPartySize(): Boolean =
        game.settings.getBoolean("campingActivityCountByPartySize")

    suspend fun setTravelCostRiverNoBridgeAdditional(value: Int) =
        game.settings.setInt("travelCostRiverNoBridgeAdditional", value)

    fun getTravelCostRiverNoBridgeAdditional(): Int =
        game.settings.getInt("travelCostRiverNoBridgeAdditional")

    suspend fun setPavedStreetsReduceTravelCost(value: Boolean) =
        game.settings.setBoolean("pavedStreetsReduceTravelCost", value)

    fun getPavedStreetsReduceTravelCost(): Boolean =
        game.settings.getBoolean("pavedStreetsReduceTravelCost")

    suspend fun setHexMapEnabled(value: Boolean) =
        game.settings.setBoolean("hexMapEnabled", value)

    fun getHexMapEnabled(): Boolean =
        game.settings.getBoolean("hexMapEnabled")

    private object nonUserVisibleSettings {
        val booleans = mapOf(
            "enableSheltered" to false,
        )
        val strings = mapOf(
            "currentWeatherFx" to "none",
            "currentWeatherType" to "sunny",
            "latestMigrationBackup" to "{}"
        )
    }


    fun register() {
        registerSimple(game.settings, nonUserVisibleSettings.strings, hidden = true)
        registerSimple(game.settings, nonUserVisibleSettings.booleans, hidden = true)
        game.settings.registerScalar<String>(
            key = "artDirectory",
            name = t("settings.artDirectory"),
            hint = t("settings.artDirectoryHelp"),
            default = "modules/${Config.moduleId}/art",
        )
        game.settings.registerScalar(
            key = "enableAfterCombatDialog",
            name = t("settings.enableAfterCombatDialog"),
            hint = t("settings.enableAfterCombatDialogHelp"),
            default = true,
            requiresReload = false,
        )
        game.settings.registerEnum<Advancement>(
            key = "advancement",
            hint = t("settings.advancementHelp"),
            default = Advancement.XP,
        )
        game.settings.registerScalar(
            key = "hideBuiltinKingdomSheet",
            name = t("settings.hideBuiltinKingdomSheet"),
            hint = t("settings.hideBuiltinKingdomSheetHelp"),
            default = false,
            requiresReload = true,
        )
        game.settings.registerScalar(
            key = "disableFirstRunMessage",
            name = t("settings.disableFirstRunMessage"),
            hint = t("settings.disableFirstRunMessageHelp"),
            default = false,
            requiresReload = true,
        )
        game.settings.registerScalar<String>(
            key = "kingdomActiveLeader",
            name = t("settings.kingdomActiveLeader"),
            default = null,
            hidden = true,
            scope = SettingsScope.CLIENT,
        )
        game.settings.registerInt(
            key = "schemaVersion",
            name = t("settings.schemaVersion"),
            hidden = false,
            hint = t("settings.schemaVersionHelp")
        )
        game.settings.registerScalar<String>(
            key = "campaignMapSceneIds",
            name = t("settings.campaignMapSceneIds"),
            hint = t("settings.campaignMapSceneIdsHelp"),
            default = "AJ1k5II28u72JOmz",
        )
        game.settings.registerDataModel<ClimateConfigurationDataModel>(
            key = "climate",
            name = t("settings.climateSettings"),
        )
        game.settings.createMenu(
            key = "climateMenu",
            label = t("settings.climateButton"),
            name = t("settings.climate"),
            restricted = true,
            app = ClimateConfiguration::class.js,
        )
        game.settings.registerScalar(
            name = t("settings.enableWeather"),
            key = "enableWeather",
            default = true,
        )
        game.settings.registerScalar(
            name = t("settings.enablePartyActorIcons"),
            key = "enablePartyActorIcons",
            hint = t("settings.enablePartyActorIconsHelp"),
            default = true,
            requiresReload = true,
        )
        game.settings.registerScalar<Boolean>(
            key = "enableWeatherSoundFx",
            name = t("settings.enableWeatherSoundFx"),
            hint = t("settings.enableWeatherSoundFxHelp"),
            default = true,
        )
        game.settings.registerScalar<String>(
            key = "weatherRollMode",
            name = t("settings.weatherRollMode"),
            choices = RollMode.entries.asSequence()
                .map { it.toCamelCase() to t(it) }
                .toMutableRecord(),
            default = "gmroll"
        )
        game.settings.registerInt(
            key = "weatherHazardRange",
            name = t("settings.weatherHazardRange"),
            default = 4,
            hint = t("settings.weatherHazardRangeHelp")
        )
        game.settings.registerScalar<Boolean>(
            key = "enableCombatTracks",
            name = t("settings.enableCombatTracks"),
            hint = t("settings.enableCombatTracksHelp"),
            default = true,
        )
        game.settings.registerScalar<Boolean>(
            key = "enableTokenMapping",
            name = t("settings.enableTokenMapping"),
            hint = t("settings.enableTokenMappingHelp"),
            default = true,
            requiresReload = true,
        )
        game.settings.registerScalar<String>(
            key = gearSettingsProfileRegistryKey,
            name = "Gear Settings Profile Registry",
            default = "",
            hidden = true,
        )
        game.settings.createMenu(
            key = "gearSettings.profiles",
            label = "Manage Profiles",
            name = "Gear Settings Profiles",
            hint = "Import, export, and activate gear settings profiles.",
            restricted = true,
            app = GearSettingsProfileManagerApplication::class.js,
        )
        game.settings.registerInt(
            key = "ruinThreshold",
            name = "Ruin Threshold",
            default = 10,
        )
        game.settings.registerInt(
            key = "eventDc",
            name = "Event DC",
            default = 15,
        )
        game.settings.registerInt(
            key = "eventDcStep",
            name = "Event DC Step",
            default = 0,
        )
        game.settings.registerInt(
            key = "leadershipActivityCap",
            name = "Leadership Activities per PC Leader",
            default = 2,
        )
        game.settings.registerInt(
            key = "leadershipActivityCapWithTownhall",
            name = "Leadership Activities per PC Leader (Town Hall/Castle/Palace)",
            default = 3,
        )
        game.settings.registerScalar<Boolean>(
            key = "canUpgradeNonCapital",
            name = "Can Upgrade Non-Capital Settlements",
            default = false,
        )
        game.settings.registerScalar<Boolean>(
            key = "capitalCanGrowOneSizeLarger",
            name = "Capital Can Grow One Size Larger",
            default = false,
        )
        game.settings.registerScalar<Boolean>(
            key = "noRandomCombatInClaimedHexes",
            name = "No Random Combat In Claimed Hexes",
            default = false,
        )
        game.settings.registerScalar<Boolean>(
            key = "capStructureBonusAtKingdomLevel",
            name = "Cap Structure Bonus At Kingdom Level",
            default = false,
        )
        game.settings.registerInt(
            key = "settlementInfluenceRadius",
            name = "Settlement Influence Radius",
            default = 0,
        )
        game.settings.registerScalar<Boolean>(
            key = "cultOfTheBloomEvents",
            name = "Cult of the Bloom Events",
            default = false,
        )
        game.settings.registerScalar<Boolean>(
            key = "campingActivityCountByPartySize",
            name = "Camping Activity Count By Party Size",
            default = false,
        )
        game.settings.registerInt(
            key = "travelCostRiverNoBridgeAdditional",
            name = "River Travel Cost Without Bridge",
            default = 0,
        )
        game.settings.registerScalar<Boolean>(
            key = "pavedStreetsReduceTravelCost",
            name = "Paved Streets Reduce Travel Cost",
            default = false,
        )
        game.settings.registerScalar<Boolean>(
            key = "hexMapEnabled",
            name = "Hex Map Enabled",
            hint = "Paints kingdom claimed/explored/cleared tints, roads and markers onto the Kingmaker hex map. Turning it off removes all of these overlays from the scene.",
            default = true,
        )
        game.settings.registerScalar<String>(
            key = "obsidianVaultName",
            name = t("settings.obsidianVaultName"),
            hint = t("settings.obsidianVaultNameHelp"),
            default = "",
        )
        game.settings.registerScalar<String>(
            key = "obsidianJournalFolder",
            name = t("settings.obsidianJournalFolder"),
            hint = t("settings.obsidianJournalFolderHelp"),
            default = "Kingdom Export",
        )
        game.settings.registerScalar<Boolean>(
            key = "obsidianOverwrite",
            name = t("settings.obsidianOverwrite"),
            hint = t("settings.obsidianOverwriteHelp"),
            default = true,
        )
        game.settings.registerScalar<Boolean>(
            key = "enableCompanionLeveling",
            name = t("settings.enableCompanionLeveling"),
            hint = t("settings.enableCompanionLevelingHelp"),
            default = true,
        )
        game.settings.registerScalar<Boolean>(
            key = "companionAutonomyEnabled",
            name = t("settings.companionAutonomyEnabled"),
            hint = t("settings.companionAutonomyEnabledHelp"),
            default = false,
        )
        game.settings.registerScalar<String>(
            key = "dismissedCalendarWarnings",
            name = "Dismissed Calendar Warnings",
            default = "",
            hidden = true,
        )
        game.settings.registerScalar<Boolean>(
            key = "enableForagingModifiers",
            name = t("settings.enableForagingModifiers"),
            hint = t("settings.enableForagingModifiersHelp"),
            default = true,
        )
    }

    suspend fun setEnableCompanionLeveling(value: Boolean) =
        game.settings.setBoolean("enableCompanionLeveling", value)

    fun getEnableCompanionLeveling(): Boolean =
        game.settings.getBoolean("enableCompanionLeveling")

    suspend fun setCompanionAutonomyEnabled(value: Boolean) =
        game.settings.setBoolean("companionAutonomyEnabled", value)

    fun getCompanionAutonomyEnabled(): Boolean =
        game.settings.getBoolean("companionAutonomyEnabled")

    /**
     * Comma-separated set of one-time calendar-integration warning keys that have already been
     * shown to the GM (e.g. the missing-compat-bridge notice). Persisted so each warning fires
     * exactly once per world. Hidden, world-scoped.
     */
    suspend fun setDismissedCalendarWarnings(value: String) =
        game.settings.setString("dismissedCalendarWarnings", value)

    fun getDismissedCalendarWarnings(): String =
        game.settings.getNullableString("dismissedCalendarWarnings") ?: ""

    suspend fun setEnableForagingModifiers(value: Boolean) =
        game.settings.setBoolean("enableForagingModifiers", value)

    fun getEnableForagingModifiers(): Boolean =
        try { game.settings.getBoolean("enableForagingModifiers") } catch (_: Throwable) { true }
}


private inline fun <reified T : Any> registerSimple(
    settings: ClientSettings,
    values: Map<String, T>,
    hidden: Boolean,
) {
    values.forEach { (key, value) ->
        settings.registerScalar<T>(
            key = key,
            default = value,
            name = key,
            hidden = hidden,
        )
    }
}

