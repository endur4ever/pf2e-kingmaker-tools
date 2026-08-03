package at.posselt.pfrpg2e.homebrew

import at.posselt.pfrpg2e.slugify
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Versioned envelope for shared homebrew rules profiles. Mirrors the gear-settings export format so
 * profiles can be shared/backed up and re-imported with a schema-version check.
 */
@Serializable
data class HomebrewProfileExport(
    val schemaVersion: Int,
    val profiles: List<HomebrewRulesProfile>,
)

/** Result of validating a raw import payload (before it is merged into a registry). */
sealed interface HomebrewProfileImport {
    data class Valid(val profiles: List<HomebrewRulesProfile>) : HomebrewProfileImport
    data class Invalid(val message: String) : HomebrewProfileImport
}

/** Result of merging an import into a registry. */
sealed interface HomebrewRegistryImport {
    data class Valid(
        val registry: HomebrewProfileRegistry,
        val imported: List<HomebrewRulesProfile>,
    ) : HomebrewRegistryImport

    data class Invalid(val message: String) : HomebrewRegistryImport
}

/**
 * Pure import/export support for homebrew rules profiles (the gear-settings pipeline mirrored onto
 * homebrew). The Foundry UI/file-picker layer delegates here so serialize, validate, migrate and
 * collision handling stay testable in commonMain.
 *
 * Unknown fields are dropped on import (forward-compat) and missing optional fields fall back to
 * their defaults; a missing REQUIRED field or a newer schema version is rejected with a message.
 */
object HomebrewProfileImportExport {
    const val currentSchemaVersion = 1

    private val exportJson = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    // ignoreUnknownKeys => unknown fields warn+drop rather than fail the whole import.
    private val importJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Export a single profile as a versioned envelope. */
    fun exportProfile(profile: HomebrewRulesProfile): String =
        exportJson.encodeToString(
            HomebrewProfileExport.serializer(),
            HomebrewProfileExport(currentSchemaVersion, listOf(profile)),
        )

    /** Export every profile in one envelope (backup / share-all). */
    fun exportAll(profiles: List<HomebrewRulesProfile>): String =
        exportJson.encodeToString(
            HomebrewProfileExport.serializer(),
            HomebrewProfileExport(currentSchemaVersion, profiles),
        )

    /** Parse + validate a raw payload without touching any registry. */
    fun parseImport(text: String): HomebrewProfileImport {
        val envelope = runCatching {
            importJson.decodeFromString(HomebrewProfileExport.serializer(), text)
        }.getOrNull() ?: return HomebrewProfileImport.Invalid(
            "Invalid homebrew profile file: not valid JSON, missing required fields, or wrong shape.",
        )
        if (envelope.schemaVersion > currentSchemaVersion) {
            return HomebrewProfileImport.Invalid(
                "This file was exported from a newer module version (v${envelope.schemaVersion}) than you have installed.",
            )
        }
        if (envelope.profiles.isEmpty()) {
            return HomebrewProfileImport.Invalid("Invalid homebrew profile file: it contains no profiles.")
        }
        return HomebrewProfileImport.Valid(envelope.profiles)
    }

    /**
     * Validate [text] and merge its profiles into [registry] with fresh, collision-free ids. When
     * [activate] is set, the last imported profile becomes active. Never mutates built-in state
     * beyond adding the imported profiles.
     */
    fun importInto(
        registry: HomebrewProfileRegistry,
        text: String,
        activate: Boolean = true,
        idFactory: (HomebrewRulesProfile, HomebrewProfileRegistry) -> String = ::uniqueProfileId,
    ): HomebrewRegistryImport = when (val parsed = parseImport(text)) {
        is HomebrewProfileImport.Invalid -> HomebrewRegistryImport.Invalid(parsed.message)
        is HomebrewProfileImport.Valid -> {
            var working = registry
            val imported = mutableListOf<HomebrewRulesProfile>()
            parsed.profiles.forEach { profile ->
                val id = idFactory(profile, working)
                val fresh = profile.copy(id = id, isActive = false)
                working = working.copy(profiles = working.profiles.filter { it.id != id } + fresh)
                imported.add(fresh)
            }
            val activeId = if (activate) imported.lastOrNull()?.id else working.activeProfileId
            working = working.copy(
                version = currentSchemaVersion,
                activeProfileId = activeId,
                profiles = working.profiles.map { it.copy(isActive = it.id == activeId) },
            )
            HomebrewRegistryImport.Valid(working, imported)
        }
    }

    /** Deterministic collision-free id from the profile name (slugified), suffixed if taken. */
    fun uniqueProfileId(profile: HomebrewRulesProfile, registry: HomebrewProfileRegistry): String {
        val base = profile.name.slugify().ifBlank { "homebrew-profile" }
        val existing = registry.profiles.map { it.id }.toSet()
        if (base !in existing) return base
        for (index in 1..10_000) {
            val candidate = "$base-$index"
            if (candidate !in existing) return candidate
        }
        return "$base-${registry.profiles.size + 1}"
    }
}
