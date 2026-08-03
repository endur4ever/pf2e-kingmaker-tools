package at.posselt.pfrpg2e.kingdom

/**
 * Pure logic for the pre-migration backup restore feature. `createBackups` already serializes all
 * kingdom+camping data into a single world setting before each migration, but it is overwritten every
 * run and never read back. This adds the two deterministic pieces: rotating the single slot into two
 * (so a re-run can't destroy the only good copy) and reconciling a backup's actors against the world
 * on restore.
 *
 * Building the restore dialog, writing flags back to actors, and resetting the world schemaVersion
 * are the deferred jsMain wiring.
 *
 * See card t_4bb250d1.
 */

/** The two retained backup slots (opaque serialized payloads); null when a slot is empty. */
data class BackupSlots(
    val latest: String?,
    val previous: String?,
)

/**
 * Rotate before writing a new backup: the current latest becomes previous, and [newBackup] becomes
 * latest. The old previous is discarded. This guarantees a failed/garbage re-run can only clobber one
 * slot, never both.
 */
fun rotateBackupSlots(slots: BackupSlots, newBackup: String): BackupSlots =
    BackupSlots(latest = newBackup, previous = slots.latest)

/** How a backup's actors line up with the current world, so restore can report the gaps honestly. */
data class BackupReconciliation(
    /** In both the backup and the world — these get their flags restored. */
    val restorable: List<String>,
    /** In the backup but absent from the world — cannot be restored (report to the GM). */
    val missingFromWorld: List<String>,
    /** In the world but not in the backup — left untouched by the restore (report to the GM). */
    val extraInWorld: List<String>,
)

/**
 * Reconcile [backupActorIds] against [worldActorIds] by id. All three lists follow the order of their
 * source list and contain no duplicates.
 */
fun reconcileBackupActors(
    backupActorIds: List<String>,
    worldActorIds: List<String>,
): BackupReconciliation {
    val backup = backupActorIds.distinct()
    val world = worldActorIds.distinct().toSet()
    val backupSet = backup.toSet()
    return BackupReconciliation(
        restorable = backup.filter { it in world },
        missingFromWorld = backup.filter { it !in world },
        extraInWorld = worldActorIds.distinct().filter { it !in backupSet },
    )
}
