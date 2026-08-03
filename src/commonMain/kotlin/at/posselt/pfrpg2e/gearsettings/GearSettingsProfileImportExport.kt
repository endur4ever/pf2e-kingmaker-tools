package at.posselt.pfrpg2e.gearsettings

import at.posselt.pfrpg2e.slugify
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val profileJson = Json {
    ignoreUnknownKeys = false
    encodeDefaults = true
    prettyPrint = true
}

private val permissiveProfileJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Pure import/export support for gear-settings profiles.
 *
 * The Foundry UI/file-picker layer can delegate to these functions so import,
 * export, migration, collision handling, and first-run seeding remain testable in
 * commonMain.
 */
object GearSettingsProfileImportExport {
    const val currentSchemaVersion = 1

    fun exportProfile(profile: GearSettingsProfile): String =
        profileJson.encodeToString(profile)

    fun parseAndValidateProfile(text: String): GearSettingsProfileImport =
        when (val validation = validateProfileJson(text)) {
            is GearSettingsProfileImport.Invalid -> validation
            is GearSettingsProfileImport.Valid -> validation
        }

    fun importProfile(
        registry: GearSettingsProfileRegistry,
        text: String,
        activate: Boolean = true,
        idFactory: (GearSettingsProfile, GearSettingsProfileRegistry) -> String = ::defaultImportedProfileId,
    ): GearSettingsProfileRegistryImport {
        return when (val parsed = parseAndValidateProfile(text)) {
            is GearSettingsProfileImport.Invalid -> GearSettingsProfileRegistryImport.Invalid(parsed.message)
            is GearSettingsProfileImport.Valid -> {
                val importedProfile = parsed.profile.copy(
                    id = idFactory(parsed.profile, registry),
                    isBuiltin = false,
                )
                val nextProfiles = registry.profiles.filter { it.id != importedProfile.id } + importedProfile
                val nextRegistry = registry.copy(
                    schemaVersion = currentSchemaVersion,
                    activeProfileId = if (activate) importedProfile.id else registry.activeProfileId,
                    profiles = nextProfiles,
                ).withBuiltinProfiles()
                GearSettingsProfileRegistryImport.Valid(nextRegistry, importedProfile)
            }
        }
    }

    fun seedRegistryFromCurrentSettings(
        registry: GearSettingsProfileRegistry,
        currentSettings: GearSettings,
        createdAt: String,
        activate: Boolean = true,
    ): GearSettingsProfileRegistry {
        val withBuiltins = registry.withBuiltinProfiles()
        if (withBuiltins.profiles.any { !it.isBuiltin }) return withBuiltins
        val detected = detectPreset(currentSettings)
        val imported = detected ?: GearSettingsProfile(
            id = "default-current-settings",
            name = "Default (Current Settings)",
            description = "Auto-imported from existing module settings on first run.",
            author = null,
            isBuiltin = false,
            createdAt = createdAt,
            updatedAt = createdAt,
            settings = currentSettings,
        )
        val profile = if (detected == null) imported else imported.copy(isBuiltin = false)
        return withBuiltins.copy(
            activeProfileId = if (activate) profile.id else withBuiltins.activeProfileId,
            profiles = withBuiltins.profiles.filter { it.id != profile.id } + profile,
        )
    }

    fun validateProfileJson(text: String): GearSettingsProfileImport {
        val root = try {
            profileJson.parseToJsonElement(text)
        } catch (_: SerializationException) {
            return GearSettingsProfileImport.Invalid("Invalid profile: not valid JSON")
        } catch (_: IllegalArgumentException) {
            return GearSettingsProfileImport.Invalid("Invalid profile: not valid JSON")
        }
        if (root !is JsonObject) {
            return GearSettingsProfileImport.Invalid("Invalid profile: not valid JSON object")
        }

        for (required in requiredTopLevelKeys) {
            if (!root.containsKey(required)) {
                return GearSettingsProfileImport.Invalid("Invalid profile: missing '$required'")
            }
        }

        val schemaVersion = root.intAt("schemaVersion")
            ?: return GearSettingsProfileImport.Invalid("Invalid profile: 'schemaVersion' must be an integer")
        if (schemaVersion > currentSchemaVersion) {
            return GearSettingsProfileImport.Invalid("Invalid profile: version $schemaVersion newer than module-supported $currentSchemaVersion")
        }
        if (schemaVersion < currentSchemaVersion) {
            return GearSettingsProfileImport.Invalid("Invalid profile: version $schemaVersion cannot be migrated by this module version")
        }

        val unknownTopLevel = root.keys.firstOrNull { it !in allowedTopLevelKeys }
        if (unknownTopLevel != null) {
            return GearSettingsProfileImport.Invalid("Invalid profile: unknown setting key '$unknownTopLevel'")
        }

        val id = root.stringAt("id")
            ?: return GearSettingsProfileImport.Invalid("Invalid profile: 'id' must be a string")
        if (!profileIdRegex.matches(id)) {
            return GearSettingsProfileImport.Invalid("Invalid profile: 'id' must match ${profileIdRegex.pattern}")
        }

        val name = root.stringAt("name")
            ?: return GearSettingsProfileImport.Invalid("Invalid profile: 'name' must be a string")
        if (name.isBlank()) {
            return GearSettingsProfileImport.Invalid("Invalid profile: 'name' must not be blank")
        }

        val settings = root["settings"] as? JsonObject
            ?: return GearSettingsProfileImport.Invalid("Invalid profile: 'settings' must be an object")
        validateSettingsKeys(settings)?.let { return GearSettingsProfileImport.Invalid(it) }
        validateSettingsRanges(settings)?.let { return GearSettingsProfileImport.Invalid(it) }

        val profile = try {
            permissiveProfileJson.decodeFromString(GearSettingsProfile.serializer(), text)
        } catch (_: SerializationException) {
            return GearSettingsProfileImport.Invalid("Invalid profile: not valid JSON")
        } catch (_: IllegalArgumentException) {
            return GearSettingsProfileImport.Invalid("Invalid profile: not valid JSON")
        }

        validateSemantic(profile)?.let { return GearSettingsProfileImport.Invalid(it) }
        return GearSettingsProfileImport.Valid(profile)
    }

    private fun validateSettingsKeys(settings: JsonObject): String? {
        val unknownGroup = settings.keys.firstOrNull { it !in allowedSettingsGroups }
        if (unknownGroup != null) return "Invalid profile: unknown setting key '$unknownGroup'"
        settings.forEach { (groupName, value) ->
            val group = value as? JsonObject
                ?: return "Invalid profile: '$groupName' must be an object"
            val allowedKeys = allowedKeysByGroup.getValue(groupName)
            val unknown = group.keys.firstOrNull { it !in allowedKeys }
            if (unknown != null) return "Invalid profile: unknown setting key '$groupName.$unknown'"
        }
        return null
    }

    private fun validateSettingsRanges(settings: JsonObject): String? {
        val kingdom = settings["kingdom"] as? JsonObject
        val camping = settings["camping"] as? JsonObject
        val hex = settings["hex"] as? JsonObject
        val army = settings["army"] as? JsonObject
        val ui = settings["ui"] as? JsonObject

        kingdom?.stringAt("advancement")?.let {
            if (it !in setOf("xp", "milestone")) return "Invalid profile: 'kingdom.advancement' value $it is not one of [xp, milestone]"
        }
        kingdom?.rangeError("kingdom.ruinThreshold", "ruinThreshold", 1, 20)?.let { return it }
        kingdom?.rangeError("kingdom.eventDc", "eventDc", 1, 30)?.let { return it }
        kingdom?.rangeError("kingdom.eventDcStep", "eventDcStep", 0, 10)?.let { return it }
        kingdom?.rangeError("kingdom.leadershipActivityCap", "leadershipActivityCap", 1, 20)?.let { return it }
        kingdom?.rangeError("kingdom.leadershipActivityCapWithTownhall", "leadershipActivityCapWithTownhall", 1, 20)?.let { return it }
        kingdom?.rangeError("kingdom.settlementInfluenceRadius", "settlementInfluenceRadius", 0, 5)?.let { return it }
        hex?.rangeError("hex.travelCostRiverNoBridgeAdditional", "travelCostRiverNoBridgeAdditional", 0, 5)?.let { return it }

        listOfNotNull(kingdom, camping, hex, army, ui).forEach { group ->
            group.forEach { (key, value) ->
                if (key !in integerKeys && value !is JsonPrimitive) return "Invalid profile: '$key' has invalid value"
            }
        }
        return null
    }

    private fun validateSemantic(profile: GearSettingsProfile): String? {
        val kingdom = profile.settings.kingdom
        if (kingdom.leadershipActivityCapWithTownhall < kingdom.leadershipActivityCap) {
            return "Invalid profile: 'kingdom.leadershipActivityCapWithTownhall' value ${kingdom.leadershipActivityCapWithTownhall} out of range [${kingdom.leadershipActivityCap}, 20]"
        }
        if (profile.id in reservedBuiltinIds && !profile.isBuiltin) {
            return "Invalid profile: custom profile cannot use reserved id '${profile.id}'"
        }
        return null
    }

    private fun JsonObject.rangeError(label: String, key: String, min: Int, max: Int): String? {
        val value = this[key] ?: return null
        val intValue = value.jsonPrimitive.intOrNull
            ?: return "Invalid profile: '$label' must be an integer"
        return if (intValue in min..max) null
        else "Invalid profile: '$label' value $intValue out of range [$min, $max]"
    }

    private fun JsonObject.stringAt(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.intAt(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun GearSettingsProfileRegistry.withBuiltinProfiles(): GearSettingsProfileRegistry {
        val byId = profiles.associateBy { it.id }
        val builtins = listOf(GearSettingsProfile.raw(), GearSettingsProfile.vanceKerenshara())
        val custom = profiles.filterNot { it.id in builtins.map(GearSettingsProfile::id) }
        return copy(
            schemaVersion = currentSchemaVersion,
            profiles = builtins.map { byId[it.id] ?: it } + custom,
        )
    }

    private fun detectPreset(settings: GearSettings): GearSettingsProfile? =
        when (settings) {
            GearSettingsProfile.raw().settings -> GearSettingsProfile.raw().copy(isBuiltin = false)
            GearSettingsProfile.vanceKerenshara().settings -> GearSettingsProfile.vanceKerenshara().copy(isBuiltin = false)
            GearSettingsProfile.gregory().settings -> GearSettingsProfile.gregory()
            else -> null
        }

    private fun defaultImportedProfileId(
        profile: GearSettingsProfile,
        registry: GearSettingsProfileRegistry,
    ): String {
        val base = (profile.name.ifBlank { profile.id }).slugify().ifBlank { "imported-profile" }
        val existing = registry.profiles.map { it.id }.toSet()
        if (base !in existing && base !in reservedBuiltinIds) return base
        for (index in 1..10_000) {
            val candidate = "$base-imported-$index"
            if (candidate !in existing && candidate !in reservedBuiltinIds) return candidate
        }
        return "imported-profile-${registry.profiles.size + 1}"
    }

    private val requiredTopLevelKeys = setOf(
        "schemaVersion",
        "id",
        "name",
        "isBuiltin",
        "createdAt",
        "updatedAt",
        "settings",
    )

    private val allowedTopLevelKeys = requiredTopLevelKeys + setOf("description", "author")
    private val reservedBuiltinIds = setOf("raw", "vance-kerenshara")
    private val profileIdRegex = Regex("^[a-z0-9_-]{1,64}$")
    private val allowedSettingsGroups = setOf("kingdom", "camping", "hex", "army", "ui")
    private val allowedKeysByGroup = mapOf(
        "kingdom" to setOf(
            "advancement",
            "ruinThreshold",
            "eventDc",
            "eventDcStep",
            "leadershipActivityCap",
            "leadershipActivityCapWithTownhall",
            "canUpgradeNonCapital",
            "capitalCanGrowOneSizeLarger",
            "noRandomCombatInClaimedHexes",
            "capStructureBonusAtKingdomLevel",
            "settlementInfluenceRadius",
            "cultOfTheBloomEvents",
        ),
        "camping" to setOf("campingActivityCountByPartySize", "enableSheltered", "enableWeather"),
        "hex" to setOf("travelCostRiverNoBridgeAdditional", "pavedStreetsReduceTravelCost", "hexMapEnabled"),
        "army" to setOf("enableCombatTracks"),
        "ui" to setOf(
            "hideBuiltinKingdomSheet",
            "enablePartyActorIcons",
            "enableWeatherSoundFx",
            "enableTokenMapping",
            "disableFirstRunMessage",
        ),
    )
    private val integerKeys = setOf(
        "ruinThreshold",
        "eventDc",
        "eventDcStep",
        "leadershipActivityCap",
        "leadershipActivityCapWithTownhall",
        "settlementInfluenceRadius",
        "travelCostRiverNoBridgeAdditional",
    )
}

sealed class GearSettingsProfileImport {
    data class Valid(val profile: GearSettingsProfile) : GearSettingsProfileImport()
    data class Invalid(val message: String) : GearSettingsProfileImport()
}

sealed class GearSettingsProfileRegistryImport {
    data class Valid(
        val registry: GearSettingsProfileRegistry,
        val importedProfile: GearSettingsProfile,
    ) : GearSettingsProfileRegistryImport()

    data class Invalid(val message: String) : GearSettingsProfileRegistryImport()
}
