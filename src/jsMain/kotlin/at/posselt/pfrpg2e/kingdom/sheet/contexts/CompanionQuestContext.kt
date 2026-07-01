package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.companion.CompanionPersonalQuest
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.RawCharacter
import kotlinx.js.JsPlainObject

/**
 * True if the companion at [index] still has a personal quest in the non-terminal "active" status.
 * Used to BLOCK companion deletion until quests are resolved (Decision 4).
 */
fun KingdomData.companionHasActivePersonalQuests(index: Int): Boolean {
    val companion = companions?.getOrNull(index) ?: return false
    val key = companion.actorUuid ?: companion.name
    val ids = companion.personalQuestIds.toSet()
    return (companionPersonalQuests ?: emptyArray()).any {
        (it.id in ids || it.companionId == key) && it.status == "active"
    }
}

/** A personal-quest row rendered in the kingdom quests page "Personal Quests" subsection. */
@JsPlainObject
external interface CompanionQuestRowContext {
    val id: String
    val title: String
    val companionName: String
    val companionIndex: Int
    val status: String
    val statusLabel: String
    val turnsRemaining: Int?
    val hasDeadline: Boolean
    val visibleToPlayers: Boolean
    val influenceReward: Int
    val isActive: Boolean
    val isGM: Boolean
}

/**
 * Maps companion personal quests to quests-page rows, resolving each quest to its companion (by
 * personalQuestIds membership, else companionId == actorUuid/name). Pure / unit-testable. When
 * [isGM] is false only player-visible quests are returned (Decision 2/5).
 */
fun buildCompanionQuestRows(
    quests: Array<CompanionPersonalQuest>,
    companions: Array<RawCharacter>,
    isGM: Boolean,
    localize: (String) -> String = { it },
): Array<CompanionQuestRowContext> {
    val visible = if (isGM) quests.toList() else quests.filter { it.visibleToPlayers }
    return visible.map { quest ->
        val index = companions.indexOfFirst { c ->
            quest.id in c.personalQuestIds || (c.actorUuid ?: c.name) == quest.companionId
        }
        val companion = companions.getOrNull(index)
        CompanionQuestRowContext(
            id = quest.id,
            title = quest.title,
            companionName = companion?.name ?: quest.companionId,
            companionIndex = index,
            status = quest.status,
            statusLabel = localize("kingdom.companion.quest.status.${quest.status}"),
            turnsRemaining = quest.turnsRemaining,
            hasDeadline = quest.turnsRemaining != null,
            visibleToPlayers = quest.visibleToPlayers,
            influenceReward = quest.influenceReward,
            isActive = quest.status == "active",
            isGM = isGM,
        )
    }.toTypedArray()
}
