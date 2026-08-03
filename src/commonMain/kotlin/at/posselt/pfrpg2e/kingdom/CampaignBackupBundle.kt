package at.posselt.pfrpg2e.kingdom

/**
 * Pure logic for the whole-campaign backup/restore bundle: the versioned-envelope compatibility
 * check and the settings-restore filter that skips keys this module version no longer registers.
 * Assembling the actor/settings/profile JSON and driving the download/restore dialogs is the
 * deferred jsMain wiring; this is the deterministic, testable heart (the card's "pure assemble/split
 * round-trip" + "settings restore covers registered keys only, unknown keys warn + skip").
 *
 * See card t_7123132a.
 */

/** Sections deliberately excluded from a v1 bundle, surfaced in the manifest so restores aren't
 * oversold (official-module hex state and heavyweight scene drawings are not portable here). */
val BACKUP_EXCLUDED_SECTIONS: List<String> = listOf("kingmakerHexState", "sceneDrawings")

/** How a bundle's schema version relates to the version currently installed. */
enum class BackupCompatibility {
    /** Same schema — restore directly. */
    COMPATIBLE,

    /** Older bundle — run it through the migration-aware import path first. */
    NEEDS_MIGRATION,

    /** Newer bundle than this module understands — refuse rather than corrupt. */
    TOO_NEW,
}

/** Compare a bundle's [bundleSchemaVersion] against the [currentSchemaVersion] installed. */
fun backupCompatibility(bundleSchemaVersion: Int, currentSchemaVersion: Int): BackupCompatibility =
    when {
        bundleSchemaVersion < currentSchemaVersion -> BackupCompatibility.NEEDS_MIGRATION
        bundleSchemaVersion > currentSchemaVersion -> BackupCompatibility.TOO_NEW
        else -> BackupCompatibility.COMPATIBLE
    }

/** The outcome of filtering a bundle's settings against the keys this module still registers. */
data class SettingsRestore(
    val applied: Map<String, String>,
    val skipped: List<String>,
)

/**
 * Restore only the settings whose keys are still [registeredKeys]; unknown keys are collected into
 * [SettingsRestore.skipped] (the caller warns) rather than written blindly. Skipped keys are
 * returned sorted for a stable warning message.
 */
fun filterRestorableSettings(
    bundleSettings: Map<String, String>,
    registeredKeys: Set<String>,
): SettingsRestore {
    val applied = bundleSettings.filterKeys { it in registeredKeys }
    val skipped = bundleSettings.keys.filter { it !in registeredKeys }.sorted()
    return SettingsRestore(applied = applied, skipped = skipped)
}
