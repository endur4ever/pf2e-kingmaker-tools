package at.posselt.pfrpg2e.macros

import at.posselt.pfrpg2e.app.forms.Select
import at.posselt.pfrpg2e.app.forms.SelectOption
import at.posselt.pfrpg2e.app.forms.formContext
import at.posselt.pfrpg2e.app.prompt
import at.posselt.pfrpg2e.migrations.migrationBackupSlots
import at.posselt.pfrpg2e.migrations.restoreMigrationBackup
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import com.foundryvtt.core.ui
import js.objects.recordOf
import kotlinx.js.JsPlainObject

@JsPlainObject
external interface RestoreBackupChoice {
    val slot: String
}

/**
 * Choose and restore a pre-migration backup slot.
 *
 * Two slots are kept (latest + previous) so a second migration run cannot destroy the only good
 * copy; both are offered here with their schema version and how many actors they cover.
 */
suspend fun restoreMigrationBackupMacro(game: Game) {
    if (!game.user.isGM) return
    val slots = game.migrationBackupSlots()
    if (slots.isEmpty()) {
        ui.notifications.warn(t("migrations.restore.noBackups"))
        return
    }
    val options = slots.map { slot ->
        SelectOption(
            label = t("migrations.restore.slotLabel", recordOf(
                "slot" to t("migrations.restore.slot.${slot.key}"),
                "version" to slot.version,
                "kingdoms" to slot.kingdomActorIds.size,
                "camping" to slot.campingActorIds.size,
            )),
            value = slot.key,
        )
    }
    prompt<RestoreBackupChoice, Unit>(
        title = t("migrations.restore.title"),
        templatePath = "components/forms/form.hbs",
        templateContext = recordOf(
            "formRows" to formContext(
                Select(
                    name = "slot",
                    label = t("migrations.restore.chooseSlot"),
                    help = t("migrations.restore.chooseSlotHelp"),
                    options = options,
                    value = slots.first().key,
                ),
            ),
        ),
    ) { choice ->
        // Re-read the slots rather than closing over them: a migration could have rotated the
        // slots while the chooser was open, and restoring a stale snapshot would be silent.
        game.migrationBackupSlots()
            .find { it.key == choice.slot }
            ?.let { game.restoreMigrationBackup(it) }
            ?: ui.notifications.error(t("migrations.restore.slotGone"))
    }
}
