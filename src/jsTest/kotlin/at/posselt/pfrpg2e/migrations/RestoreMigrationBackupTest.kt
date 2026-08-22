package at.posselt.pfrpg2e.migrations

import at.posselt.pfrpg2e.kingdom.reconcileBackupActors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The reconciliation drives what the destructive confirm PROMISES the GM, so the three buckets have
 * to be exactly right: restore these, cannot restore those, leave these alone.
 */
class RestoreMigrationBackupTest {
    @Test
    fun anActorInBothIsRestorable() {
        val r = reconcileBackupActors(listOf("a", "b"), listOf("a", "b"))
        assertEquals(listOf("a", "b"), r.restorable)
        assertTrue(r.missingFromWorld.isEmpty())
        assertTrue(r.extraInWorld.isEmpty())
    }

    @Test
    fun anActorDeletedSinceTheBackupCannotBeRestored() {
        // The GM must be told rather than silently getting fewer actors back than they expect.
        val r = reconcileBackupActors(listOf("a", "gone"), listOf("a"))
        assertEquals(listOf("a"), r.restorable)
        assertEquals(listOf("gone"), r.missingFromWorld)
    }

    @Test
    fun anActorCreatedSinceTheBackupIsLeftUntouched() {
        // Restoring must not delete work done after the backup was taken; it is reported, not wiped.
        val r = reconcileBackupActors(listOf("a"), listOf("a", "new"))
        assertEquals(listOf("a"), r.restorable)
        assertEquals(listOf("new"), r.extraInWorld)
    }

    @Test
    fun anEmptyBackupRestoresNothingAndClaimsNothing() {
        val r = reconcileBackupActors(emptyList(), listOf("a", "b"))
        assertTrue(r.restorable.isEmpty())
        assertTrue(r.missingFromWorld.isEmpty())
        assertEquals(listOf("a", "b"), r.extraInWorld)
    }

    @Test
    fun everyActorLandsInExactlyOneBucket() {
        val backup = listOf("both", "onlyBackup")
        val world = listOf("both", "onlyWorld")
        val r = reconcileBackupActors(backup, world)
        val all = r.restorable + r.missingFromWorld + r.extraInWorld
        assertEquals(all.size, all.toSet().size, "no actor may appear in two buckets")
        assertEquals(setOf("both", "onlyBackup", "onlyWorld"), all.toSet())
    }
}
