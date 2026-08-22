package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.Config
import at.posselt.pfrpg2e.app.confirm
import at.posselt.pfrpg2e.camping.CampingData
import at.posselt.pfrpg2e.camping.getCampingActors
import at.posselt.pfrpg2e.camping.setCamping
import at.posselt.pfrpg2e.migrations.currentSchemaVersion
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import com.foundryvtt.core.ui
import js.objects.Object
import js.objects.recordOf

/**
 * Restore a whole-campaign backup bundle produced by [exportCampaignBackup].
 *
 * The compatibility check and the settings filter shipped tested with no callers; this is what
 * drives them. Sections are opt-in and every one states what it will overwrite before anything is
 * written.
 *
 * See card t_7123132a.
 */

/** What a bundle contains and what restoring it would do, decided before anything is written. */
data class CampaignRestorePlan(
    val compatibility: BackupCompatibility,
    val bundleSchemaVersion: Int,
    val kingdomActors: List<String>,
    val campingActors: List<String>,
    val settings: SettingsRestore,
)

/** Inspect a parsed bundle without touching world state. */
fun Game.planCampaignRestore(bundle: dynamic): CampaignRestorePlan? {
    val version = (bundle?.schemaVersion as? Int) ?: return null
    val sections = bundle.sections
    fun entryIds(list: dynamic): List<String> =
        runCatching {
            list.unsafeCast<Array<dynamic>>().mapNotNull { it.uuid as? String }
        }.getOrDefault(emptyList())
    val bundleSettings: Map<String, String> = runCatching {
        val raw = sections?.settings ?: return@runCatching emptyMap<String, String>()
        Object.keys(raw.unsafeCast<Any>()).associateWith { key -> JSON.stringify(raw[key]) }
    }.getOrDefault(emptyMap())
    return CampaignRestorePlan(
        compatibility = backupCompatibility(version, currentSchemaVersion()),
        bundleSchemaVersion = version,
        kingdomActors = if (sections?.kingdomActors != undefined) entryIds(sections.kingdomActors) else emptyList(),
        campingActors = if (sections?.campingActors != undefined) entryIds(sections.campingActors) else emptyList(),
        settings = filterRestorableSettings(bundleSettings, registeredModuleSettingKeys()),
    )
}

/** Setting keys this module currently registers, read from Foundry's own registry. */
fun Game.registeredModuleSettingKeys(): Set<String> = runCatching {
    val prefix = "${Config.moduleId}."
    js("Array").from(settings.asDynamic().settings.keys())
        .unsafeCast<Array<String>>()
        .filter { it.startsWith(prefix) }
        .map { it.removePrefix(prefix) }
        .toSet()
}.getOrDefault(emptySet())

/**
 * GM-only. Restores the chosen sections after a confirm naming everything that changes.
 *
 * A bundle from a NEWER module version is refused outright rather than partially applied: it may
 * carry fields this build cannot interpret, and half-restoring those is worse than not restoring.
 */
suspend fun Game.restoreCampaignBackup(
    bundle: dynamic,
    restoreActors: Boolean,
    restoreSettings: Boolean,
) {
    if (!user.isGM) return
    val plan = planCampaignRestore(bundle)
    if (plan == null) {
        ui.notifications.error(t("kingdom.campaignRestore.unreadable"))
        return
    }
    if (plan.compatibility == BackupCompatibility.TOO_NEW) {
        ui.notifications.error(
            t("kingdom.campaignRestore.tooNew", recordOf(
                "bundle" to plan.bundleSchemaVersion,
                "current" to currentSchemaVersion(),
            )),
        )
        return
    }
    if (!restoreActors && !restoreSettings) {
        ui.notifications.warn(t("kingdom.campaignRestore.nothingSelected"))
        return
    }

    val message = buildString {
        append(t("kingdom.campaignRestore.confirmIntro", recordOf("version" to plan.bundleSchemaVersion)))
        if (plan.compatibility == BackupCompatibility.NEEDS_MIGRATION) {
            append("<br>").append(
                t("kingdom.campaignRestore.confirmOlder", recordOf("version" to plan.bundleSchemaVersion)),
            )
        }
        if (restoreActors) {
            append("<hr>").append(t("kingdom.campaignRestore.confirmActors", recordOf(
                "kingdoms" to plan.kingdomActors.size,
                "camping" to plan.campingActors.size,
            )))
        }
        if (restoreSettings) {
            append("<hr>").append(t("kingdom.campaignRestore.confirmSettings",
                recordOf("count" to plan.settings.applied.size)))
            if (plan.settings.skipped.isNotEmpty()) {
                append("<br>").append(t("kingdom.campaignRestore.confirmSkipped", recordOf(
                    "count" to plan.settings.skipped.size,
                    "keys" to plan.settings.skipped.take(5).joinToString(", "),
                )))
            }
        }
        append("<hr>").append(t("kingdom.campaignRestore.excluded",
            recordOf("sections" to BACKUP_EXCLUDED_SECTIONS.joinToString(", "))))
        append("<br><strong>").append(t("kingdom.campaignRestore.destructive")).append("</strong>")
    }
    if (!confirm(message)) return

    var restoredKingdoms = 0
    var restoredCamping = 0
    if (restoreActors) {
        val kingdomByUuid = getKingdomActors().associateBy { it.uuid }
        val campingByUuid = getCampingActors().associateBy { it.uuid }
        bundle.sections?.kingdomActors?.unsafeCast<Array<dynamic>>()?.forEach { entry ->
            val actor = (entry.uuid as? String)?.let { kingdomByUuid[it] }
            if (actor != null && entry.data != undefined) {
                actor.setKingdom(entry.data.unsafeCast<KingdomData>())
                restoredKingdoms++
            }
        }
        bundle.sections?.campingActors?.unsafeCast<Array<dynamic>>()?.forEach { entry ->
            val actor = (entry.uuid as? String)?.let { campingByUuid[it] }
            if (actor != null && entry.data != undefined) {
                actor.setCamping(entry.data.unsafeCast<CampingData>())
                restoredCamping++
            }
        }
    }
    var restoredSettings = 0
    if (restoreSettings) {
        val raw = bundle.sections?.settings
        plan.settings.applied.keys.forEach { key ->
            // Each write is guarded: one unwritable setting must not abandon the rest, and a
            // setting whose TYPE changed since the backup would otherwise throw mid-restore.
            runCatching { settings.asDynamic().set(Config.moduleId, key, raw[key]) }
                .onSuccess { restoredSettings++ }
        }
    }
    ui.notifications.info(
        t("kingdom.campaignRestore.restored", recordOf(
            "kingdoms" to restoredKingdoms,
            "camping" to restoredCamping,
            "settings" to restoredSettings,
        )),
    )
}

@kotlinx.js.JsPlainObject
external interface CampaignRestoreChoice {
    val restoreActors: Boolean
    val restoreSettings: Boolean
}

/**
 * Ask which sections to restore, then hand off to [restoreCampaignBackup] for the destructive
 * confirm. Sections are opt-IN: a GM restoring only settings must not silently lose actor data.
 */
suspend fun promptRestoreSections(game: Game, bundle: dynamic) {
    val plan = game.planCampaignRestore(bundle)
    if (plan == null) {
        ui.notifications.error(t("kingdom.campaignRestore.unreadable"))
        return
    }
    at.posselt.pfrpg2e.app.prompt<CampaignRestoreChoice, Unit>(
        title = t("kingdom.campaignRestore.title"),
        templatePath = "components/forms/form.hbs",
        templateContext = recordOf(
            "formRows" to at.posselt.pfrpg2e.app.forms.formContext(
                at.posselt.pfrpg2e.app.forms.CheckboxInput(
                    name = "restoreActors",
                    label = t("kingdom.campaignRestore.sectionActors", recordOf(
                        "kingdoms" to plan.kingdomActors.size,
                        "camping" to plan.campingActors.size,
                    )),
                    value = true,
                ),
                at.posselt.pfrpg2e.app.forms.CheckboxInput(
                    name = "restoreSettings",
                    label = t("kingdom.campaignRestore.sectionSettings",
                        recordOf("count" to plan.settings.applied.size)),
                    value = false,
                ),
            ),
        ),
    ) { choice ->
        game.restoreCampaignBackup(bundle, choice.restoreActors, choice.restoreSettings)
    }
}
