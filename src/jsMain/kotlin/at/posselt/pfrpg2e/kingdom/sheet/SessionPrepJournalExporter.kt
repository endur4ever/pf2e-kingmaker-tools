package at.posselt.pfrpg2e.kingdom.sheet

import at.posselt.pfrpg2e.kingdom.SessionPrepView
import at.posselt.pfrpg2e.settings.pfrpg2eKingdomCampingWeather
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.Game
import com.foundryvtt.core.documents.Folder
import com.foundryvtt.core.documents.JournalEntry
import com.foundryvtt.core.documents.JournalEntryPage
import js.objects.recordOf
import kotlinx.coroutines.await

/**
 * Exports a [SessionPrepView] to a Foundry JournalEntry, reusing the same
 * folder/overwrite pattern as [KingdomJournalExporter] (obsidian journal folder
 * setting, "Session Prep" journal name, overwrite-in-place).
 */
object SessionPrepJournalExporter {
    suspend fun export(game: Game, view: SessionPrepView): String {
        val folderName = game.settings.pfrpg2eKingdomCampingWeather.getObsidianJournalFolder()

        // Find or create folder (same pattern as KingdomJournalExporter)
        var folder = game.folders.find {
            it.name == folderName && it.unsafeCast<AnyObject>()["type"] == "JournalEntry"
        }
        if (folder == null) {
            val folderData = recordOf<String, Any>(
                "name" to folderName,
                "type" to "JournalEntry"
            )
            folder = Folder.create(folderData.unsafeCast<AnyObject>()).await()
        }

        val journalName = "Session Prep"

        // Find or create journal entry
        var journal = game.journal.find {
            it.name == journalName && it.folder?.id == folder.id
        }
        if (journal == null) {
            val journalData = recordOf<String, Any>(
                "name" to journalName,
                "folder" to (folder.id ?: "")
            )
            journal = JournalEntry.create(journalData.unsafeCast<AnyObject>()).await()
        } else {
            // Overwrite: clear existing pages
            val pageIds = journal.pages.contents.mapNotNull { it.id }.toTypedArray()
            if (pageIds.isNotEmpty()) {
                journal.deleteEmbeddedDocuments<JournalEntryPage>("JournalEntryPage", pageIds).await()
            }
        }

        // Build HTML recap
        val html = buildSessionPrepHtml(view)

        // Create the page
        val pageData = recordOf<String, Any>(
            "name" to "Session Prep Recap",
            "type" to "text",
            "text" to recordOf<String, Any>(
                "content" to html,
                "format" to 1
            )
        )
        journal.createEmbeddedDocuments<JournalEntryPage>("JournalEntryPage", arrayOf(pageData.unsafeCast<AnyObject>())).await()

        return folderName
    }

    // Quest/clock/hex names and details are world data and may contain markup
    // characters; escape them so they can't inject into the journal page HTML.
    private fun esc(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun buildSessionPrepHtml(view: SessionPrepView): String {
        val sb = StringBuilder()
        sb.append("<h1>Session Prep Recap</h1>\n")

        // Open Quests
        sb.append("<h2>Open Quests</h2>\n")
        if (view.openQuests.isEmpty()) {
            sb.append("<p><em>No open quests.</em></p>\n")
        } else {
            sb.append("<ul>\n")
            for (q in view.openQuests) {
                sb.append("  <li><strong>${esc(q.name)}</strong>")
                if (q.detail.isNotBlank()) sb.append(" <span style=\"opacity:0.7\">(${esc(q.detail)})</span>")
                sb.append("</li>\n")
            }
            sb.append("</ul>\n")
        }

        // Active Clocks (GM only)
        if (view.isGM) {
            sb.append("<h2>Active Campaign Clocks</h2>\n")
            if (view.activeClocks.isEmpty()) {
                sb.append("<p><em>No active campaign clocks.</em></p>\n")
            } else {
                sb.append("<ul>\n")
                for (c in view.activeClocks) {
                    sb.append("  <li><strong>${esc(c.name)}</strong>")
                    if (c.turnsRemaining != null) sb.append(" — <em>${c.turnsRemaining} turns remaining</em>")
                    sb.append("</li>\n")
                }
                sb.append("</ul>\n")
            }

            // Unresolved Events (GM only)
            sb.append("<h2>Unresolved Kingdom Events</h2>\n")
            if (view.unresolvedEvents.isEmpty()) {
                sb.append("<p><em>No unresolved events.</em></p>\n")
            } else {
                sb.append("<ul>\n")
                for (e in view.unresolvedEvents) {
                    sb.append("  <li><strong>${esc(e.name)}</strong></li>\n")
                }
                sb.append("</ul>\n")
            }
        }

        // Hex Hooks
        sb.append("<h2>Hex Content Hooks</h2>\n")
        if (view.hexHooks.isEmpty()) {
            sb.append("<p><em>No hex content hooks.</em></p>\n")
        } else {
            sb.append("<ul>\n")
            for (h in view.hexHooks) {
                sb.append("  <li><strong>${esc(h.name)}</strong>")
                if (h.detail.isNotBlank()) sb.append(" <span style=\"opacity:0.7\">(${esc(h.detail)})</span>")
                sb.append("</li>\n")
            }
            sb.append("</ul>\n")
        }

        // Companion Moments
        sb.append("<h2>Companion Moments</h2>\n")
        if (view.companionMoments.isEmpty()) {
            sb.append("<p><em>No active companion moments.</em></p>\n")
        } else {
            sb.append("<ul>\n")
            for (m in view.companionMoments) {
                sb.append("  <li><strong>${esc(m.name)}</strong>")
                if (m.turnsRemaining != null) sb.append(" — <em>${m.turnsRemaining} turns remaining</em>")
                if (m.detail.isNotBlank()) sb.append(" <span style=\"opacity:0.7\">(${esc(m.detail)})</span>")
                sb.append("</li>\n")
            }
            sb.append("</ul>\n")
        }

        // Companion Expeditions
        sb.append("<h2>Companion Expeditions</h2>\n")
        if (view.companionExpeditions.isEmpty()) {
            sb.append("<p><em>No active companion expeditions.</em></p>\n")
        } else {
            sb.append("<ul>\n")
            for (e in view.companionExpeditions) {
                sb.append("  <li><strong>${esc(e.name)}</strong>")
                if (e.turnsRemaining != null) sb.append(" — <em>${e.turnsRemaining} days remaining</em>")
                if (e.detail.isNotBlank()) sb.append(" <span style=\"opacity:0.7\">(${esc(e.detail)})</span>")
                sb.append("</li>\n")
            }
            sb.append("</ul>\n")
        }

        // Recent Turns (GM only)
        if (view.isGM && view.recentTurns.isNotEmpty()) {
            sb.append("<h2>Recent Turns</h2>\n")
            sb.append("<ul>\n")
            for (t in view.recentTurns) {
                sb.append("  <li><strong>Turn ${t.turn}</strong>")
                sb.append(" — Fame: ${t.fame}, RP: ${t.resourcePoints}, Consumption: ${t.consumption}, Unrest: ${t.unrest}")
                if (t.warPressure != null) sb.append(", War Pressure: ${t.warPressure}")
                if (t.xpAwarded != null) sb.append(", XP: ${t.xpAwarded}")
                if (!t.clockEvents.isNullOrEmpty()) {
                    sb.append("<br/><em>Clock events: ${t.clockEvents.joinToString(", ") { esc(it) }}</em>")
                }
                if (!t.notes.isNullOrBlank()) sb.append("<br/><em>${esc(t.notes)}</em>")
                sb.append("</li>\n")
            }
            sb.append("</ul>\n")
        }

        return sb.toString()
    }
}
