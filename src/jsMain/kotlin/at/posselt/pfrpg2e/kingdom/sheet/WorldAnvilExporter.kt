package at.posselt.pfrpg2e.kingdom.sheet

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.data.getChosenFeatures
import at.posselt.pfrpg2e.kingdom.vacancies
import at.posselt.pfrpg2e.kingdom.getExplodedFeatures
import at.posselt.pfrpg2e.kingdom.getRealmData
import at.posselt.pfrpg2e.kingdom.parseLeaderActors
import at.posselt.pfrpg2e.data.kingdom.calculateControlDC
import at.posselt.pfrpg2e.kingdom.modifiers.penalties.calculateUnrestPenalty
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import kotlinx.browser.document
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.dom.url.URL
import kotlinx.html.a
import kotlinx.html.dom.create

/**
 * Exports kingdom data to World Anvil-compatible BBCode/HTML files for manual import.
 * Generates a ZIP-like structure of text files that can be imported into World Anvil.
 */
object WorldAnvilExporter {
    /**
     * Exports kingdom data to World Anvil format and triggers download.
     * @param game The Foundry VTT game instance
     * @param actor The kingdom actor
     * @param kingdom The kingdom data to export
     * @return A status message indicating success or failure
     */
    suspend fun export(game: Game, actor: KingdomActor, kingdom: KingdomData): String {
        try {
            // Generate BBCode content for World Anvil import
            val bbcodeContent = generateWorldAnvilBbcode(game, actor, kingdom)
            
            // Trigger download of the BBCode file
            triggerDownload(bbcodeContent, "kingdom-worldanvil-export.txt")
            
            return "Successfully exported kingdom data for World Anvil import!"
        } catch (e: Throwable) {
            return "Export failed: ${e.message}"
        }
    }

    /**
     * Generates World Anvil-compatible BBCode content from kingdom data.
     */
    private suspend fun generateWorldAnvilBbcode(game: Game, actor: KingdomActor, kingdom: KingdomData): String {
        val sb = StringBuilder()
        
        sb.append("[h1]Kingdom Export for World Anvil[/h1]")
        sb.append("[hr]")
        sb.append("[b]Kingdom:[/b] ${kingdom.name}[br]")
        sb.append("[b]Level:[/b] ${kingdom.level}[br]")
        sb.append("[b]Current Turn:[/b] ${kingdom.currentTurn ?: "Not tracked"}[br][br]")
        
        // Overview section
        sb.append("[h2]Overview[/h2]")
        sb.append(generateOverviewTable(kingdom, game, actor))
        sb.append("[br]")
        
        // Leaders section
        sb.append("[h2]Council & Leaders[/h2]")
        sb.append(generateLeadersTable(kingdom))
        sb.append("[br]")
        
        // Settlements section
        sb.append("[h2]Settlements[/h2]")
        sb.append(generateSettlementsTable(kingdom, game))
        sb.append("[br]")
        
        // Events section
        sb.append("[h2]Active Events[/h2]")
        sb.append(generateEventsList(kingdom))
        sb.append("[br]")
        
        // Quests section
        sb.append("[h2]Active Quests[/h2]")
        sb.append(generateQuestsList(kingdom))
        sb.append("[br]")
        
        // Companions/NPCs section (if available)
        sb.append("[h2]Companions & NPCs[/h2]")
        sb.append(generateCompanionsList(kingdom))
        sb.append("[br]")
        
        // Armies section
        sb.append("[h2]Armies[/h2]")
        sb.append(generateArmiesInfo(kingdom))
        sb.append("[br]")
        
        // Resources section
        sb.append("[h2]Resources[/h2]")
        sb.append(generateResourcesTable(kingdom))
        
        return sb.toString()
    }

    /**
     * Generates the overview table in BBCode format.
     */
    private fun generateOverviewTable(kingdom: KingdomData, game: Game, actor: KingdomActor): String {
        val sb = StringBuilder()
        sb.append("[table]")
        sb.append("[tr][th]Attribute[/th][th]Value[/th][/tr]")
        
        val chosenFeatures = kingdom.getChosenFeatures(kingdom.getExplodedFeatures())
        val realmData = game.getRealmData(actor, kingdom)
        val vacancies = kingdom.vacancies(
            choices = chosenFeatures,
            bonusFeats = kingdom.bonusFeats,
            government = kingdom.government,
        )
        val controlDC = calculateControlDC(kingdom.level, realmData, vacancies.ruler)
        val unrestPenalty = calculateUnrestPenalty(kingdom.unrest)
        
        sb.append("[tr][td]Level[/td][td]${kingdom.level}[/td][/tr]")
        sb.append("[tr][td]XP[/td][td]${kingdom.xp} / ${kingdom.xpThreshold}[/td][/tr]")
        sb.append("[tr][td]Size[/td][td]${realmData.size} (${t(realmData.sizeInfo.type)})[/td][/tr]")
        sb.append("[tr][td]Control DC[/td][td]$controlDC[/td][/tr]")
        sb.append("[tr][td]Unrest[/td][td]${kingdom.unrest} (Penalty: $unrestPenalty)[/td][/tr]")
        sb.append("[tr][td]Resource Points[/td][td]${kingdom.resourcePoints.now}[/td][/tr]")
        sb.append("[tr][td]Resource Dice[/td][td]${kingdom.resourceDice.now}[/td][/tr]")
        sb.append("[/table]")
        
        return sb.toString()
    }

    /**
     * Generates the leaders table in BBCode format.
     */
    private suspend fun generateLeadersTable(kingdom: KingdomData): String {
        val sb = StringBuilder()
        sb.append("[table]")
        sb.append("[tr][th]Role[/th][th]Leader[/th][th]Status[/th][/tr]")

        val chosenFeatures = kingdom.getChosenFeatures(kingdom.getExplodedFeatures())
        val vacancies = kingdom.vacancies(
            choices = chosenFeatures,
            bonusFeats = kingdom.bonusFeats,
            government = kingdom.government,
        )
        val leaderActors = kingdom.parseLeaderActors()
        
        val roles = listOf(
            Pair("Ruler", leaderActors.ruler),
            Pair("Counselor", leaderActors.counselor),
            Pair("Emissary", leaderActors.emissary),
            Pair("General", leaderActors.general),
            Pair("Magister", leaderActors.magister),
            Pair("Treasurer", leaderActors.treasurer),
            Pair("Viceroy", leaderActors.viceroy),
            Pair("Warden", leaderActors.warden)
        )
        
        for ((role, actor) in roles) {
            val leaderName = actor?.name ?: "Vacant"
            val isVacant = when (role) {
                "Ruler" -> vacancies.ruler
                "Counselor" -> vacancies.counselor
                "Emissary" -> vacancies.emissary
                "General" -> vacancies.general
                "Magister" -> vacancies.magister
                "Treasurer" -> vacancies.treasurer
                "Viceroy" -> vacancies.viceroy
                "Warden" -> vacancies.warden
                else -> false
            }
            val status = if (isVacant) "Vacant" else "Active"
            sb.append("[tr][td]$role[/td][td]$leaderName[/td][td]$status[/td][/tr]")
        }
        
        sb.append("[/table]")
        return sb.toString()
    }

    /**
     * Generates the settlements table in BBCode format.
     */
    private fun generateSettlementsTable(kingdom: KingdomData, game: Game): String {
        if (kingdom.settlements.isEmpty()) {
            return "[i]No settlements established yet.[/i]"
        }
        
        val sb = StringBuilder()
        sb.append("[table]")
        sb.append("[tr][th]Name[/th][th]Type[/th][th]Level[/th][th]Lots[/th][/tr]")
        
        for (settlement in kingdom.settlements) {
            val sceneName = game.scenes.get(settlement.sceneId)?.name ?: "Unknown Scene"
            sb.append("[tr][td]$sceneName[/td][td]${settlement.type}[/td][td]${settlement.level}[/td][td]${settlement.lots}[/td][/tr]")
        }
        
        sb.append("[/table]")
        return sb.toString()
    }

    /**
     * Generates the events list in BBCode format.
     */
    internal fun generateEventsList(kingdom: KingdomData): String {
        val ongoingEvents = kingdom.ongoingEvents
        if (ongoingEvents.isEmpty()) {
            return "[i]No active events.[/i]"
        }

        val sb = StringBuilder()
        sb.append("[list]")
        for (event in ongoingEvents) {
            val title = event.id
            sb.append("[*]$title[br]")
        }
        sb.append("[/list]")
        return sb.toString()
    }

    /**
     * Generates the quests list in BBCode format.
     */
    internal fun generateQuestsList(kingdom: KingdomData): String {
        val activeQuests = (kingdom.quests ?: emptyArray()).filter { it.status == "active" }
        if (activeQuests.isEmpty()) {
            return "[i]No active quests.[/i]"
        }
        
        val sb = StringBuilder()
        sb.append("[list]")
        for (quest in activeQuests) {
            sb.append("[*][b]${quest.title}[/b] (Giver: ${quest.giver})[br]")
            sb.append("${quest.description}[/*][br]")
        }
        sb.append("[/list]")
        return sb.toString()
    }

    /**
     * Generates the companions list in BBCode format.
     */
    internal fun generateCompanionsList(kingdom: KingdomData): String {
        val companions = kingdom.companions ?: emptyArray()
        if (companions.isEmpty()) {
            return "[i]No companions recorded.[/i]"
        }
        
        val sb = StringBuilder()
        sb.append("[table]")
        sb.append("[tr][th]Name[/th][th]Role[/th][th]Description[/th][/tr]")
        
        for (companion in companions) {
            val name = companion.name
            val role = companion.role
            val description = companion.plotHook ?: ""
            sb.append("[tr][td]$name[/td][td]$role[/td][td]$description[/td][/tr]")
        }
        
        sb.append("[/table]")
        return sb.toString()
    }

    /**
     * Generates army information in BBCode format.
     */
    internal fun generateArmiesInfo(kingdom: KingdomData): String {
        val armyDeployments = kingdom.armyDeployments ?: emptyArray()
        if (armyDeployments.isEmpty()) {
            return "[i]No armies deployed.[/i]"
        }

        val sb = StringBuilder()
        sb.append("[table]")
        sb.append("[tr][th]Army[/th][th]Type[/th][th]Status[/th][/tr]")

        for (deployment in armyDeployments) {
            val armyName = deployment.armyName
            val armyType = deployment.armyType
            val status = deployment.status
            sb.append("[tr][td]$armyName[/td][td]$armyType[/td][td]$status[/td][/tr]")
        }

        sb.append("[/table]")
        return sb.toString()
    }

    /**
     * Generates the resources table in BBCode format.
     */
    internal fun generateResourcesTable(kingdom: KingdomData): String {
        val sb = StringBuilder()
        sb.append("[table]")
        sb.append("[tr][th]Resource[/th][th]Current[/th][/tr]")

        val resources = listOf(
            Pair("Food", kingdom.commodities.now.food),
            Pair("Lumber", kingdom.commodities.now.lumber),
            Pair("Stone", kingdom.commodities.now.stone),
            Pair("Ore", kingdom.commodities.now.ore),
            Pair("Luxuries", kingdom.commodities.now.luxuries)
        )

        for ((resource, current) in resources) {
            sb.append("[tr][td]$resource[/td][td]$current[/td][/tr]")
        }

        sb.append("[tr][td]Consumption[/td][td]${kingdom.consumption.now}[/td][/tr]")
        sb.append("[/table]")
        return sb.toString()
    }

    /**
     * Triggers a file download with the given content and filename.
     * Uses the Kotlin/JS Blob API for proper browser compatibility.
     */
    private fun triggerDownload(content: String, filename: String) {
        val blob = Blob(arrayOf(content), BlobPropertyBag(type = "text/plain"))
        val url = URL.createObjectURL(blob)
        val link = document.create.a {
            href = url
            downLoad = filename
        }
        link.click()
        URL.revokeObjectURL(url)
    }
}