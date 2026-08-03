package at.posselt.pfrpg2e.kingdom.sheet

import at.posselt.pfrpg2e.Config
import at.posselt.pfrpg2e.app.forms.formContext
import at.posselt.pfrpg2e.app.jsonFilePicker
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.RawCharacter
import at.posselt.pfrpg2e.kingdom.data.RawQuest
import at.posselt.pfrpg2e.kingdom.data.RawQuestRewards
import at.posselt.pfrpg2e.kingdom.data.getChosenFeatures
import at.posselt.pfrpg2e.kingdom.vacancies
import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.setKingdom
import at.posselt.pfrpg2e.kingdom.parseLeaderActors
import at.posselt.pfrpg2e.kingdom.getExplodedFeatures
import at.posselt.pfrpg2e.kingdom.getRealmData
import at.posselt.pfrpg2e.data.kingdom.calculateControlDC
import at.posselt.pfrpg2e.data.kingdom.leaders.Leader
import at.posselt.pfrpg2e.kingdom.modifiers.penalties.calculateUnrestPenalty
import at.posselt.pfrpg2e.settings.pfrpg2eKingdomCampingWeather
import at.posselt.pfrpg2e.utils.fromUuidTypeSafe
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.Game
import com.foundryvtt.core.ui
import com.foundryvtt.core.documents.Folder
import com.foundryvtt.core.documents.JournalEntry
import com.foundryvtt.core.documents.JournalEntryPage
import js.objects.recordOf
import kotlinx.coroutines.await

object KingdomJournalExporter {
    suspend fun export(game: Game, actor: KingdomActor, kingdom: KingdomData): String {
        val folderName = game.settings.pfrpg2eKingdomCampingWeather.getObsidianJournalFolder()
        val overwrite = game.settings.pfrpg2eKingdomCampingWeather.getObsidianOverwrite()

        // Find or create folder
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

        // Determine journal entry name
        val journalName = if (overwrite) {
            "Kingdom Summary"
        } else {
            "Kingdom Summary - Level ${kingdom.level} (${js("new Date().toLocaleDateString()")})"
        }

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

        // Build HTML Report Content
        val htmlReport = generateHtmlReport(game, actor, kingdom)

        // Create the page
        val pageData = recordOf<String, Any>(
            "name" to "Kingdom Overview",
            "type" to "text",
            "text" to recordOf<String, Any>(
                "content" to htmlReport,
                "format" to 1
            )
        )
        journal.createEmbeddedDocuments<JournalEntryPage>("JournalEntryPage", arrayOf(pageData.unsafeCast<AnyObject>())).await()

        return folderName
    }

    private suspend fun generateHtmlReport(game: Game, actor: KingdomActor, kingdom: KingdomData): String {
        val allFeatures = kingdom.getExplodedFeatures()
        val chosenFeatures = kingdom.getChosenFeatures(allFeatures)
        val vacancies = kingdom.vacancies(
            choices = chosenFeatures,
            bonusFeats = kingdom.bonusFeats,
            government = kingdom.government,
        )
        val realm = game.getRealmData(actor, kingdom)
        val controlDc = calculateControlDC(kingdom.level, realm, vacancies.ruler)
        val unrestPenalty = calculateUnrestPenalty(kingdom.unrest)
        val leaderActors = kingdom.parseLeaderActors()

        val sb = StringBuilder()
        sb.append("<h1>Kingdom Summary: ${kingdom.name}</h1>\n")
        
        sb.append("<h2>Overview</h2>\n")
        sb.append("<table>\n")
        sb.append("  <tr><td><strong>Level</strong></td><td>${kingdom.level}</td></tr>\n")
        sb.append("  <tr><td><strong>XP</strong></td><td>${kingdom.xp} / ${kingdom.xpThreshold}</td></tr>\n")
        sb.append("  <tr><td><strong>Size</strong></td><td>${realm.size} (${t(realm.sizeInfo.type)})</td></tr>\n")
        sb.append("  <tr><td><strong>Control DC</strong></td><td>$controlDc</td></tr>\n")
        sb.append("  <tr><td><strong>Unrest</strong></td><td>${kingdom.unrest} (Penalty: $unrestPenalty)</td></tr>\n")
        sb.append("  <tr><td><strong>Resource Points</strong></td><td>${kingdom.resourcePoints.now}</td></tr>\n")
        sb.append("  <tr><td><strong>Resource Dice</strong></td><td>${kingdom.resourceDice.now}</td></tr>\n")
        sb.append("</table>\n\n")

        sb.append("<h2>Council / Leaders</h2>\n")
        sb.append("<table>\n")
        sb.append("  <thead>\n")
        sb.append("    <tr><th>Role</th><th>Leader Actor</th><th>Vacancy Status</th></tr>\n")
        sb.append("  </thead>\n")
        sb.append("  <tbody>\n")
        sb.append("    <tr><td>Ruler</td><td>${leaderActors.ruler?.name ?: "Vacant"}</td><td>${if (vacancies.ruler) "Vacant" else "Active"}</td></tr>\n")
        sb.append("    <tr><td>Counselor</td><td>${leaderActors.counselor?.name ?: "Vacant"}</td><td>${if (vacancies.counselor) "Vacant" else "Active"}</td></tr>\n")
        sb.append("    <tr><td>Emissary</td><td>${leaderActors.emissary?.name ?: "Vacant"}</td><td>${if (vacancies.emissary) "Vacant" else "Active"}</td></tr>\n")
        sb.append("    <tr><td>General</td><td>${leaderActors.general?.name ?: "Vacant"}</td><td>${if (vacancies.general) "Vacant" else "Active"}</td></tr>\n")
        sb.append("    <tr><td>Magister</td><td>${leaderActors.magister?.name ?: "Vacant"}</td><td>${if (vacancies.magister) "Vacant" else "Active"}</td></tr>\n")
        sb.append("    <tr><td>Treasurer</td><td>${leaderActors.treasurer?.name ?: "Vacant"}</td><td>${if (vacancies.treasurer) "Vacant" else "Active"}</td></tr>\n")
        sb.append("    <tr><td>Viceroy</td><td>${leaderActors.viceroy?.name ?: "Vacant"}</td><td>${if (vacancies.viceroy) "Vacant" else "Active"}</td></tr>\n")
        sb.append("    <tr><td>Warden</td><td>${leaderActors.warden?.name ?: "Vacant"}</td><td>${if (vacancies.warden) "Vacant" else "Active"}</td></tr>\n")
        sb.append("  </tbody>\n")
        sb.append("</table>\n\n")

        sb.append("<h2>Settlements</h2>\n")
        if (kingdom.settlements.isEmpty()) {
            sb.append("<p>No settlements established yet.</p>\n")
        } else {
            sb.append("<table>\n")
            sb.append("  <thead>\n")
            sb.append("    <tr><th>Name</th><th>Type</th><th>Level</th><th>Lots</th></tr>\n")
            sb.append("  </thead>\n")
            sb.append("  <tbody>\n")
            for (s in kingdom.settlements) {
                val sceneName = game.scenes.get(s.sceneId)?.name ?: "Unknown Scene"
                sb.append("    <tr><td>${sceneName}</td><td>${s.type}</td><td>${s.level}</td><td>${s.lots}</td></tr>\n")
            }
            sb.append("  </tbody>\n")
            sb.append("</table>\n\n")
        }

        sb.append("<h2>Active Quests</h2>\n")
        val activeQuests = (kingdom.quests ?: emptyArray()).filter { it.status == "active" }
        if (activeQuests.isEmpty()) {
            sb.append("<p>No active quests.</p>\n")
        } else {
            sb.append("<ul>\n")
            for (q in activeQuests) {
                sb.append("  <li><strong>${q.title}</strong> (Giver: ${q.giver}) - ${q.description}</li>\n")
            }
            sb.append("</ul>\n")
        }

        return sb.toString()
    }
}

object ObsidianImporter {
    suspend fun importMarkdown(actor: KingdomActor, mdText: String, game: Game): String {
        val lines = mdText.split("\n").map { it.trim() }
        var inFrontmatter = false
        val frontmatterLines = mutableListOf<String>()
        val bodyLines = mutableListOf<String>()
        var hasFrontmatter = false
        var parsedSecondDelimiter = false
        
        if (lines.firstOrNull() == "---") {
            inFrontmatter = true
            hasFrontmatter = true
            for (i in 1 until lines.size) {
                val line = lines[i]
                if (line == "---") {
                    inFrontmatter = false
                    parsedSecondDelimiter = true
                    bodyLines.addAll(lines.subList(i + 1, lines.size))
                    break
                }
                frontmatterLines.add(line)
            }
        }
        
        if (!hasFrontmatter || !parsedSecondDelimiter) {
            bodyLines.addAll(lines)
        }
        
        val frontmatter = mutableMapOf<String, String>()
        for (line in frontmatterLines) {
            if (line.isEmpty() || line.startsWith("#")) continue
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim().lowercase()
                val value = parts[1].trim().removeSurrounding("\"").removeSurrounding("'")
                frontmatter[key] = value
            }
        }
        
        val type = frontmatter["type"]?.lowercase()
            ?: return "Missing 'type' property in YAML frontmatter (e.g. 'type: notes', 'type: companions', 'type: leaders', 'type: quest')"

        return when (type) {
            "notes", "kingdom-notes" -> {
                val target = frontmatter["target"]?.lowercase() ?: "public"
                val htmlContent = markdownToHtml(bodyLines)
                val kingdom = actor.getKingdom() ?: return "No kingdom data found on actor"
                if (target == "gm") {
                    kingdom.notes.gm = htmlContent
                } else {
                    kingdom.notes.public = htmlContent
                }
                actor.setKingdom(kingdom)
                "Kingdom notes updated ($target)"
            }
            "companions", "roster", "settlement-population" -> {
                val newCompanions = mutableListOf<RawCharacter>()
                for (line in bodyLines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                        val content = trimmed.substring(2).trim()
                        val parts = content.split(":", limit = 2)
                        if (parts.isNotEmpty()) {
                            var rawName = parts[0].trim()
                            val desc = if (parts.size == 2) parts[1].trim() else ""
                            
                            var role = "npc"
                            if (rawName.contains("(") && rawName.contains(")")) {
                                val start = rawName.indexOf("(")
                                val end = rawName.indexOf(")")
                                val roleText = rawName.substring(start + 1, end).trim().lowercase()
                                if (roleText == "companion" || roleText == "pc") {
                                    role = "companion"
                                }
                                rawName = rawName.substring(0, start).trim()
                            }
                            
                            val companion = js("{ name: rawName, speed: 0, traveling: false, active: true, role: role, plotHook: desc }").unsafeCast<RawCharacter>()
                            newCompanions.add(companion)
                        }
                    }
                }
                val kingdom = actor.getKingdom() ?: return "No kingdom data found on actor"
                val existing = kingdom.companions ?: emptyArray()
                val updated = (existing.toList() + newCompanions).distinctBy { it.name }.toTypedArray()
                kingdom.companions = updated
                actor.setKingdom(kingdom)
                "Imported ${newCompanions.size} companions to Roster"
            }
            "leaders", "council" -> {
                val assignments = mutableMapOf<String, String>()
                for (line in bodyLines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                        val content = trimmed.substring(2).trim()
                        val parts = content.split(":", limit = 2)
                        if (parts.size == 2) {
                            val role = parts[0].trim().lowercase()
                            val actorName = parts[1].trim()
                            assignments[role] = actorName
                        }
                    }
                }
                val kingdom = actor.getKingdom() ?: return "No kingdom data found on actor"
                var count = 0
                for ((roleName, actorName) in assignments) {
                    val role = Leader.fromString(roleName) ?: continue
                    val foundActor = game.actors.find { it.name.lowercase() == actorName.lowercase() }
                    if (foundActor != null) {
                        val leaderValues = when (role) {
                            Leader.RULER -> kingdom.leaders.ruler
                            Leader.COUNSELOR -> kingdom.leaders.counselor
                            Leader.EMISSARY -> kingdom.leaders.emissary
                            Leader.GENERAL -> kingdom.leaders.general
                            Leader.MAGISTER -> kingdom.leaders.magister
                            Leader.TREASURER -> kingdom.leaders.treasurer
                            Leader.VICEROY -> kingdom.leaders.viceroy
                            Leader.WARDEN -> kingdom.leaders.warden
                        }
                        leaderValues.uuid = foundActor.uuid
                        leaderValues.vacant = false
                        count++
                    }
                }
                actor.setKingdom(kingdom)
                "Assigned $count leaders on the council"
            }
            "quest" -> {
                val title = frontmatter["title"] ?: "New Quest"
                val giver = frontmatter["giver"] ?: "Unknown Giver"
                val target = frontmatter["target"]
                val status = frontmatter["status"] ?: "active"
                val qType = frontmatter["type"] ?: "other"
                val description = markdownToHtml(bodyLines)
                
                val rp = frontmatter["rp"]?.toIntOrNull()
                val xp = frontmatter["xp"]?.toIntOrNull()
                val unrest = frontmatter["unrest"]?.toIntOrNull()
                val food = frontmatter["food"]?.toIntOrNull()
                val lumber = frontmatter["lumber"]?.toIntOrNull()
                val stone = frontmatter["stone"]?.toIntOrNull()
                val ore = frontmatter["ore"]?.toIntOrNull()
                val luxuries = frontmatter["luxuries"]?.toIntOrNull()
                
                val rewards = js("{}").unsafeCast<RawQuestRewards>()
                rewards.rp = rp
                rewards.xp = xp
                rewards.unrest = unrest
                rewards.food = food
                rewards.lumber = lumber
                rewards.stone = stone
                rewards.ore = ore
                rewards.luxuries = luxuries
                
                val id = "obsidian-" + js("Date.now().toString(36)") + "-" + js("Math.random().toString(36).substring(2, 6)")
                val quest = js("{}").unsafeCast<RawQuest>()
                quest.id = id
                quest.title = title
                quest.description = description
                quest.giver = giver
                quest.status = status
                quest.type = qType
                quest.target = target
                quest.rewards = rewards
                quest.flavorTextCompleted = ""
                
                val kingdom = actor.getKingdom() ?: return "No kingdom data found on actor"
                val existing = kingdom.quests ?: emptyArray()
                kingdom.quests = existing + quest
                actor.setKingdom(kingdom)
                "Imported Quest: $title"
            }
            else -> "Unsupported import type '$type'"
        }
    }

    fun markdownToHtml(lines: List<String>): String {
        val sb = StringBuilder()
        var inList = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                if (inList) {
                    sb.append("</ul>\n")
                    inList = false
                }
                sb.append("<p></p>\n")
                continue
            }
            
            if (trimmed.startsWith("#")) {
                if (inList) {
                    sb.append("</ul>\n")
                    inList = false
                }
                val level = trimmed.takeWhile { it == '#' }.length
                val content = trimmed.dropWhile { it == '#' || it == ' ' }
                sb.append("<h$level>$content</h$level>\n")
                continue
            }
            
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                if (!inList) {
                    sb.append("<ul>\n")
                    inList = true
                }
                val content = trimmed.substring(2)
                sb.append("<li>${parseInlineMarkdown(content)}</li>\n")
                continue
            }
            
            if (inList) {
                sb.append("</ul>\n")
                inList = false
            }
            sb.append("<p>${parseInlineMarkdown(trimmed)}</p>\n")
        }
        if (inList) {
            sb.append("</ul>\n")
        }
        return sb.toString()
    }
    
    private fun parseInlineMarkdown(text: String): String {
        var result = text
        val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
        result = boldRegex.replace(result) { "<strong>${it.groupValues[1]}</strong>" }
        val italicRegex = Regex("\\*(.*?)\\*")
        result = italicRegex.replace(result) { "<em>${it.groupValues[1]}</em>" }
        return result
    }
}
