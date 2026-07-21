package at.posselt.pfrpg2e.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals

class CampaignBackupBundleTest {
    @Test
    fun olderBundleNeedsMigration() {
        assertEquals(BackupCompatibility.NEEDS_MIGRATION, backupCompatibility(bundleSchemaVersion = 3, currentSchemaVersion = 5))
    }

    @Test
    fun sameVersionIsCompatible() {
        assertEquals(BackupCompatibility.COMPATIBLE, backupCompatibility(5, 5))
    }

    @Test
    fun newerBundleIsRefused() {
        assertEquals(BackupCompatibility.TOO_NEW, backupCompatibility(bundleSchemaVersion = 7, currentSchemaVersion = 5))
    }

    @Test
    fun restoreAppliesKnownKeysAndSkipsUnknownSorted() {
        val bundle = mapOf(
            "weatherEnabled" to "true",
            "obsoleteToggle" to "1",
            "campingResabled" to "false",
            "anotherGoneKey" to "x",
        )
        val registered = setOf("weatherEnabled", "campingResabled")
        val r = filterRestorableSettings(bundle, registered)
        assertEquals(mapOf("weatherEnabled" to "true", "campingResabled" to "false"), r.applied)
        assertEquals(listOf("anotherGoneKey", "obsoleteToggle"), r.skipped)  // sorted
    }

    @Test
    fun manifestExcludesOfficialModuleAndSceneData() {
        assertEquals(listOf("kingmakerHexState", "sceneDrawings"), BACKUP_EXCLUDED_SECTIONS)
    }
}
