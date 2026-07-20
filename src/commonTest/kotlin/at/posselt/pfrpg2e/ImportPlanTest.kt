package at.posselt.pfrpg2e

import kotlin.test.Test
import kotlin.test.assertEquals

class ImportPlanTest {
    private val latest = 48

    @Test
    fun bareUnstampedPayloadRequiresConfirmation() {
        assertEquals(ImportPlan.ConfirmThenApply, planImport(stampedVersion = null, latest = latest))
    }

    @Test
    fun newerThanSupportedIsRejected() {
        assertEquals(ImportPlan.RejectNewer, planImport(stampedVersion = 49, latest = latest))
    }

    @Test
    fun currentVersionAppliesAsIs() {
        assertEquals(ImportPlan.Apply, planImport(stampedVersion = 48, latest = latest))
    }

    @Test
    fun olderSupportedVersionMigratesFromThatVersion() {
        assertEquals(ImportPlan.Migrate(fromVersion = 40), planImport(stampedVersion = 40, latest = latest))
        assertEquals(ImportPlan.Migrate(fromVersion = 16), planImport(stampedVersion = 16, latest = latest))
    }

    @Test
    fun tooOldToMigrateSafelyRequiresConfirmation() {
        assertEquals(ImportPlan.ConfirmThenApply, planImport(stampedVersion = 15, latest = latest))
        assertEquals(ImportPlan.ConfirmThenApply, planImport(stampedVersion = 1, latest = latest))
    }
}
