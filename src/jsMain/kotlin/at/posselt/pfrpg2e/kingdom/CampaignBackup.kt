package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.Config
import at.posselt.pfrpg2e.camping.getCamping
import at.posselt.pfrpg2e.camping.getCampingActors
import at.posselt.pfrpg2e.migrations.currentSchemaVersion
import at.posselt.pfrpg2e.utils.downloadJson
import com.foundryvtt.core.Game
import kotlin.js.Date

/**
 * Whole-campaign backup: one JSON file holding every piece of module state that is portable.
 *
 * Uses the same versioned-envelope discipline as the per-actor import path, so a bundle restored
 * into a newer install can be recognised as needing migration (see [backupCompatibility]) rather
 * than written blindly.
 *
 * [BACKUP_EXCLUDED_SECTIONS] is written INTO the file rather than merely documented. A backup that
 * quietly omits the official module's hex claims and the scene drawings would be actively dangerous
 * — a GM would restore it, see settlements and kingdom data return, and assume the map came back
 * too. Naming the gaps in the artefact means the omission travels with it.
 *
 * See card t_7123132a.
 */

/** Assemble the campaign bundle. Read-only: touches no state, so it is safe to call for a preview. */
suspend fun Game.assembleCampaignBackup(): dynamic {
    val bundle = js("{}")
    bundle.schemaVersion = currentSchemaVersion()
    bundle.moduleVersion = (modules.get(Config.moduleId)?.asDynamic()?.version as? String) ?: ""
    bundle.exportedAt = Date().toISOString()
    bundle.excludedSections = BACKUP_EXCLUDED_SECTIONS.toTypedArray()

    val sections = js("{}")

    sections.kingdomActors = getKingdomActors().mapNotNull { actor ->
        actor.getKingdom()?.let { kingdom ->
            val entry = js("{}")
            entry.uuid = actor.uuid
            entry.name = actor.name
            entry.data = kingdom
            entry
        }
    }.toTypedArray()

    sections.campingActors = getCampingActors().mapNotNull { actor ->
        actor.getCamping()?.let { camping ->
            val entry = js("{}")
            entry.uuid = actor.uuid
            entry.name = actor.name
            entry.data = camping
            entry
        }
    }.toTypedArray()

    // Settings are collected defensively: this walks Foundry's own registry rather than a
    // hand-maintained key list, so a setting added later is backed up without anyone remembering
    // to update this. A failure here yields an empty settings section rather than losing the
    // actor data, which is the part a GM cannot reconstruct by hand.
    sections.settings = runCatching { collectModuleSettings() }.getOrElse { js("{}") }

    bundle.sections = sections
    return bundle
}

/** Every setting this module registers, as key -> value, read straight from Foundry's registry. */
private fun Game.collectModuleSettings(): dynamic {
    val out = js("{}")
    val registry = settings.asDynamic().settings
    val prefix = "${Config.moduleId}."
    val keys = js("Array").from(registry.keys()).unsafeCast<Array<String>>()
    keys.filter { it.startsWith(prefix) }.forEach { fullKey ->
        val key = fullKey.removePrefix(prefix)
        runCatching { settings.asDynamic().get(Config.moduleId, key) }
            .onSuccess { value -> out[key] = value }
    }
    return out
}

/** GM-only: assemble the bundle and hand the GM a dated JSON file. */
suspend fun Game.exportCampaignBackup() {
    if (!user.isGM) return
    val bundle = assembleCampaignBackup()
    val stamp = Date().toISOString().substringBefore('T')
    downloadJson(bundle, "pf2e-kingmaker-campaign-backup-$stamp.json")
}
