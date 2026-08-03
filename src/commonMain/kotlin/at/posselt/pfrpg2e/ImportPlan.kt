package at.posselt.pfrpg2e

/**
 * The oldest per-actor schema version the migration chain can safely start from. Below this the
 * world-level migration path refuses (see Migrations.kt), so imported data stamped older is treated
 * conservatively rather than force-migrated.
 */
const val OLDEST_SUPPORTED_SCHEMA_VERSION = 16

/** What to do with a kingdom/camping JSON import, decided purely from its stamped schema version. */
sealed interface ImportPlan {
    /** Run the migration chain on the data starting after [fromVersion], then apply. */
    data class Migrate(val fromVersion: Int) : ImportPlan

    /** Data is already at the current version — apply as-is. */
    data object Apply : ImportPlan

    /**
     * No/too-old version stamp — re-running the full chain could double-transform or reset settings,
     * so warn the GM and import as-is on confirmation. (The chain contains unconditional-set and
     * array→record migrations that are not safe to re-run on already-current data.)
     */
    data object ConfirmThenApply : ImportPlan

    /** Stamped newer than this module supports — refuse (would install fields we can't understand). */
    data object RejectNewer : ImportPlan
}

/**
 * Decide how to import actor data given its [stampedVersion] (null when the payload carries no
 * envelope), the module's [latest] supported schema version, and the [oldestSupported] version the
 * chain can start from.
 */
fun planImport(
    stampedVersion: Int?,
    latest: Int,
    oldestSupported: Int = OLDEST_SUPPORTED_SCHEMA_VERSION,
): ImportPlan = when {
    stampedVersion == null -> ImportPlan.ConfirmThenApply
    stampedVersion > latest -> ImportPlan.RejectNewer
    stampedVersion < oldestSupported -> ImportPlan.ConfirmThenApply
    stampedVersion == latest -> ImportPlan.Apply
    else -> ImportPlan.Migrate(stampedVersion)
}
