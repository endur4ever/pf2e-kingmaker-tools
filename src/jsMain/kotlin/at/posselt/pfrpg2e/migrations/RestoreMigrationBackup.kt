package at.posselt.pfrpg2e.migrations

import at.posselt.pfrpg2e.app.confirm
import at.posselt.pfrpg2e.camping.CampingData
import at.posselt.pfrpg2e.camping.getCampingActors
import at.posselt.pfrpg2e.camping.setCamping
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.backupSlotContent
import at.posselt.pfrpg2e.kingdom.getKingdomActors
import at.posselt.pfrpg2e.kingdom.reconcileBackupActors
import at.posselt.pfrpg2e.kingdom.setKingdom
import at.posselt.pfrpg2e.settings.pfrpg2eKingdomCampingWeather
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import com.foundryvtt.core.ui
import js.objects.Object
import js.objects.recordOf

/**
 * Restore a pre-migration backup slot.
 *
 * createBackups has been writing these before every migration run since long before anything could
 * read them back — getLatestMigrationBackup had no callers at all, so a failed migration left
 * hand-editing actor flags as the only recovery. This is the read side.
 *
 * Restoring also RESETS the world schema version to the backup's own version, so the migration
 * chain re-runs cleanly on next load rather than leaving restored old-shape data stranded above a
 * version marker that claims it is already current.
 *
 * See card t_4bb250d1.
 */

/** One restorable slot, summarised for the chooser. */
data class BackupSlotSummary(
    val key: String,
    val version: Int,
    val kingdomActorIds: List<String>,
    val campingActorIds: List<String>,
    val raw: String,
)

private fun parseSlot(key: String, raw: String?): BackupSlotSummary? {
    val content = backupSlotContent(raw) ?: return null
    val parsed = runCatching { JSON.parse<Any?>(content).asDynamic() }.getOrNull() ?: return null
    val version = (parsed.version as? Int) ?: return null
    fun ids(section: dynamic): List<String> =
        runCatching { Object.keys(section.unsafeCast<Any>()).toList() }.getOrDefault(emptyList())
    return BackupSlotSummary(
        key = key,
        version = version,
        kingdomActorIds = if (parsed.kingdoms != undefined) ids(parsed.kingdoms) else emptyList(),
        campingActorIds = if (parsed.camping != undefined) ids(parsed.camping) else emptyList(),
        raw = content,
    )
}

/** Both slots, newest first, skipping any that was never written. */
fun Game.migrationBackupSlots(): List<BackupSlotSummary> {
    val s = settings.pfrpg2eKingdomCampingWeather
    return listOfNotNull(
        parseSlot("latest", s.getLatestMigrationBackup()),
        parseSlot("previous", s.getPreviousMigrationBackup()),
    )
}

/**
 * GM-only. Restores [slot] onto the matching actors after an explicit destructive confirm that
 * names exactly which actors are affected and which cannot be restored.
 */
suspend fun Game.restoreMigrationBackup(slot: BackupSlotSummary) {
    if (!user.isGM) return
    val parsed = runCatching { JSON.parse<Any?>(slot.raw).asDynamic() }.getOrNull() ?: return

    val kingdomActors = getKingdomActors().associateBy { it.id ?: "" }
    val campingActors = getCampingActors().associateBy { it.id ?: "" }
    val kingdomPlan = reconcileBackupActors(slot.kingdomActorIds, kingdomActors.keys.toList())
    val campingPlan = reconcileBackupActors(slot.campingActorIds, campingActors.keys.toList())

    val message = buildString {
        append(t("migrations.restore.confirmIntro", recordOf("version" to slot.version)))
        append("<hr>")
        append(t("migrations.restore.confirmRestoring", recordOf(
            "kingdoms" to kingdomPlan.restorable.size,
            "camping" to campingPlan.restorable.size,
        )))
        val missing = kingdomPlan.missingFromWorld + campingPlan.missingFromWorld
        if (missing.isNotEmpty()) {
            append("<br>").append(
                t("migrations.restore.confirmMissing", recordOf("count" to missing.size)),
            )
        }
        val extra = kingdomPlan.extraInWorld + campingPlan.extraInWorld
        if (extra.isNotEmpty()) {
            append("<br>").append(
                t("migrations.restore.confirmUntouched", recordOf("count" to extra.size)),
            )
        }
        append("<hr>").append(t("migrations.restore.confirmSchemaReset", recordOf("version" to slot.version)))
        append("<br><strong>").append(t("migrations.restore.confirmDestructive")).append("</strong>")
        append("<br>").append(t("migrations.restore.inWorldWarning"))
    }
    if (!confirm(message)) return

    kingdomPlan.restorable.forEach { id ->
        val data = parsed.kingdoms?.get(id)
        if (data != null && data != undefined) {
            kingdomActors[id]?.setKingdom(data.unsafeCast<KingdomData>())
        }
    }
    campingPlan.restorable.forEach { id ->
        val data = parsed.camping?.get(id)
        if (data != null && data != undefined) {
            campingActors[id]?.setCamping(data.unsafeCast<CampingData>())
        }
    }
    // Reset the version marker LAST: if a setKingdom above throws, the world still claims the
    // current version and no half-restored data is left looking un-migrated.
    settings.pfrpg2eKingdomCampingWeather.setSchemaVersion(slot.version)
    ui.notifications.info(
        t("migrations.restore.restored", recordOf(
            "kingdoms" to kingdomPlan.restorable.size,
            "camping" to campingPlan.restorable.size,
            "version" to slot.version,
        )),
    )
}
