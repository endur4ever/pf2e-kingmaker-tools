package at.posselt.pfrpg2e.gearsettings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A named configuration of all toggleable module settings — kingdom, camping,
 * hex, army, and UI — that can be switched with a single profile selection.
 *
 * Three built-in profiles exist: RAW (rules as written), Vance & Kerenshara
 * (community house rules), and Gregory's custom profile. GMs can create,
 * import, export, and activate custom profiles.
 */
@Serializable
data class GearSettingsProfile(
    val schemaVersion: Int = 1,
    val id: String,
    val name: String,
    val description: String? = null,
    val author: String? = null,
    val isBuiltin: Boolean = false,
    val createdAt: String,
    val updatedAt: String,
    val settings: GearSettings = GearSettings(),
) {
    companion object {
        private val EPOCH = "2026-06-01T00:00:00Z"

        fun raw() = GearSettingsProfile(
            id = "raw",
            name = "RAW (Rules as Written)",
            description = "Unmodified Pathfinder 2e Kingmaker rules.",
            isBuiltin = true,
            createdAt = EPOCH,
            updatedAt = EPOCH,
            settings = GearSettings(),
        )

        fun vanceKerenshara() = GearSettingsProfile(
            id = "vance-kerenshara",
            name = "Vance & Kerenshara",
            description = "Vance & Kerenshara community house rules for kingdom advancement and structure balance.",
            author = "Vance & Kerenshara",
            isBuiltin = true,
            createdAt = EPOCH,
            updatedAt = EPOCH,
            settings = GearSettings(),
        )

        fun gregory() = GearSettingsProfile(
            id = "gregory",
            name = "Gregory's Gear Settings",
            description = "Gregory's custom gear settings profile.",
            author = "Gregory",
            isBuiltin = false,
            createdAt = EPOCH,
            updatedAt = EPOCH,
            settings = GearSettings(
                kingdom = KingdomSettingsGroup(
                    ruinThreshold = 5,
                    eventDc = 5,
                    leadershipActivityCap = 8,
                    leadershipActivityCapWithTownhall = 12,
                    canUpgradeNonCapital = true,
                    capitalCanGrowOneSizeLarger = true,
                    noRandomCombatInClaimedHexes = true,
                    capStructureBonusAtKingdomLevel = true,
                    settlementInfluenceRadius = 1,
                    cultOfTheBloomEvents = true,
                ),
                camping = CampingSettingsGroup(
                    campingActivityCountByPartySize = true,
                    enableWeather = false,
                ),
                hex = HexSettingsGroup(
                    travelCostRiverNoBridgeAdditional = 1,
                    pavedStreetsReduceTravelCost = true,
                ),
                ui = UiSettingsGroup(
                    hideBuiltinKingdomSheet = true,
                    disableFirstRunMessage = true,
                ),
            ),
        )
    }
}

@Serializable
data class GearSettings(
    val kingdom: KingdomSettingsGroup = KingdomSettingsGroup(),
    val camping: CampingSettingsGroup = CampingSettingsGroup(),
    val hex: HexSettingsGroup = HexSettingsGroup(),
    val army: ArmySettingsGroup = ArmySettingsGroup(),
    val ui: UiSettingsGroup = UiSettingsGroup(),
)

@Serializable
data class KingdomSettingsGroup(
    val advancement: String = "xp",
    val ruinThreshold: Int = 10,
    val eventDc: Int = 15,
    val eventDcStep: Int = 0,
    val leadershipActivityCap: Int = 6,
    val leadershipActivityCapWithTownhall: Int = 8,
    val canUpgradeNonCapital: Boolean = false,
    val capitalCanGrowOneSizeLarger: Boolean = false,
    val noRandomCombatInClaimedHexes: Boolean = false,
    val capStructureBonusAtKingdomLevel: Boolean = false,
    val settlementInfluenceRadius: Int = 0,
    val cultOfTheBloomEvents: Boolean = false,
)

@Serializable
data class CampingSettingsGroup(
    val campingActivityCountByPartySize: Boolean = false,
    val enableSheltered: Boolean = false,
    val enableWeather: Boolean = true,
)

@Serializable
data class HexSettingsGroup(
    val travelCostRiverNoBridgeAdditional: Int = 0,
    val pavedStreetsReduceTravelCost: Boolean = false,
    val hexMapEnabled: Boolean = true,
)

@Serializable
data class ArmySettingsGroup(
    val enableCombatTracks: Boolean = true,
)

@Serializable
data class UiSettingsGroup(
    val hideBuiltinKingdomSheet: Boolean = false,
    val enablePartyActorIcons: Boolean = true,
    val enableWeatherSoundFx: Boolean = true,
    val enableTokenMapping: Boolean = true,
    val disableFirstRunMessage: Boolean = false,
)

/**
 * Registry holding all gear-settings profiles and the active profile id.
 * Stored in a Foundry game setting as a JSON string.
 */
@Serializable
data class GearSettingsProfileRegistry(
    val schemaVersion: Int = 1,
    val activeProfileId: String? = null,
    val profiles: List<GearSettingsProfile> = listOf(
        GearSettingsProfile.raw(),
        GearSettingsProfile.vanceKerenshara(),
    ),
) {
    fun toJson(): String = codec.encodeToString(serializer(), this)

    companion object {
        private val codec = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun fromJson(text: String): GearSettingsProfileRegistry? =
            runCatching { codec.decodeFromString(serializer(), text) }.getOrNull()
    }
}
