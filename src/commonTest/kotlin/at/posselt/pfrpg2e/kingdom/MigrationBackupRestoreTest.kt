package at.posselt.pfrpg2e.kingdom

import kotlin.test.assertNull
import kotlin.test.Test
import kotlin.test.assertEquals

class MigrationBackupRestoreTest {
    @Test
    fun rotatingShiftsLatestIntoPreviousAndDropsTheOldPrevious() {
        val slots = BackupSlots(latest = "v2", previous = "v1")
        val rotated = rotateBackupSlots(slots, "v3")
        assertEquals("v3", rotated.latest)
        assertEquals("v2", rotated.previous)  // old previous "v1" discarded
    }

    @Test
    fun firstRotationLeavesPreviousEmpty() {
        val rotated = rotateBackupSlots(BackupSlots(latest = null, previous = null), "v1")
        assertEquals("v1", rotated.latest)
        assertEquals(null, rotated.previous)
    }

    @Test
    fun reconcileSplitsActorsThreeWays() {
        val r = reconcileBackupActors(
            backupActorIds = listOf("a", "b", "c"),
            worldActorIds = listOf("b", "c", "d"),
        )
        assertEquals(listOf("b", "c"), r.restorable)
        assertEquals(listOf("a"), r.missingFromWorld)      // in backup, gone from world
        assertEquals(listOf("d"), r.extraInWorld)          // in world, not in backup
    }

    @Test
    fun reconcileHandlesDisjointAndIdenticalSets() {
        val disjoint = reconcileBackupActors(listOf("a"), listOf("b"))
        assertEquals(emptyList(), disjoint.restorable)
        assertEquals(listOf("a"), disjoint.missingFromWorld)
        assertEquals(listOf("b"), disjoint.extraInWorld)

        val identical = reconcileBackupActors(listOf("a", "b"), listOf("a", "b"))
        assertEquals(listOf("a", "b"), identical.restorable)
        assertEquals(emptyList(), identical.missingFromWorld)
        assertEquals(emptyList(), identical.extraInWorld)
    }

    @Test
    fun anUnwrittenSlotIsNotABackup() {
        // The world setting defaults to "{}", so this is the state of every world that has never
        // run a migration. Offering it as a restore point would hand the GM an empty rollback.
        assertNull(backupSlotContent("{}"))
        assertNull(backupSlotContent("  {}  "))
        assertNull(backupSlotContent(""))
        assertNull(backupSlotContent(null))
    }

    @Test
    fun aRealBackupSurvivesTheEmptyCheck() {
        assertEquals("""{"version":42}""", backupSlotContent("""{"version":42}"""))
    }
}
