package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.companion.CompanionPersonalQuest
import at.posselt.pfrpg2e.companion.MAX_COMPANION_INFLUENCE
import at.posselt.pfrpg2e.companion.clampInfluence
import at.posselt.pfrpg2e.companion.companionDiscoveryStages
import at.posselt.pfrpg2e.companion.influenceBarPercent
import at.posselt.pfrpg2e.companion.normalizeDiscoveryStatus
import at.posselt.pfrpg2e.kingdom.data.RawCharacter
import kotlinx.js.JsPlainObject

@JsPlainObject
external interface DiscoveryStageOption {
    val value: String
    val label: String
    val selected: Boolean
}

@JsPlainObject
external interface PersonalQuestSummaryContext {
    val id: String
    val title: String
    val description: String
    val status: String
    val statusLabel: String
    val turnsRemaining: Int?
    val hasDeadline: Boolean
    val visibleToPlayers: Boolean
    val influenceReward: Int
    val isActive: Boolean
    val questHook: String?
    val isGM: Boolean
}

@JsPlainObject
external interface CompanionProfileContext : HandlebarsRenderContext {
    val companionName: String
    val companionUuid: String?
    val img: String?
    val roleLabel: String
    val influence: Int
    val influencePercent: Int
    val maxInfluence: Int
    val campAvailable: Boolean
    val discoveryStatus: String
    val discoveryStatusLabel: String
    val discoveryStages: Array<DiscoveryStageOption>
    val plotHook: String?
    val personalQuests: Array<PersonalQuestSummaryContext>
    val activeQuestCount: Int
    val isGM: Boolean
}

private fun questSummary(
    quest: CompanionPersonalQuest,
    isGM: Boolean,
    localize: (String) -> String,
): PersonalQuestSummaryContext =
    PersonalQuestSummaryContext(
        id = quest.id,
        title = quest.title,
        description = quest.description,
        status = quest.status,
        statusLabel = localize("kingdom.companion.quest.status.${quest.status}"),
        turnsRemaining = quest.turnsRemaining,
        hasDeadline = quest.turnsRemaining != null,
        visibleToPlayers = quest.visibleToPlayers,
        influenceReward = quest.influenceReward,
        isActive = quest.status == "active",
        // Quest hooks are GM-facing narrative triggers; never expose to players.
        questHook = if (isGM) quest.questHook else null,
        isGM = isGM,
    )

/**
 * Builds the companion profile context. Pure (no Foundry i18n dependency) so it can be unit-tested;
 * the dialog passes [localize] = the real localizer, tests use the identity default.
 *
 * When [isGM] is false (read-only player view, Decision 5) only player-visible quests are shown and
 * quest hooks are stripped.
 */
fun buildCompanionProfileContext(
    partId: String,
    companion: RawCharacter,
    quests: List<CompanionPersonalQuest>,
    isGM: Boolean,
    localize: (String) -> String = { it },
): CompanionProfileContext {
    val influence = clampInfluence(companion.influence)
    val status = normalizeDiscoveryStatus(companion.discoveryStatus)
    val visibleQuests = if (isGM) quests else quests.filter { it.visibleToPlayers }
    return CompanionProfileContext(
        partId = partId,
        companionName = companion.name,
        companionUuid = companion.actorUuid,
        img = companion.img,
        roleLabel = if (companion.role == "npc") "NPC" else "Companion",
        influence = influence,
        influencePercent = influenceBarPercent(influence),
        maxInfluence = MAX_COMPANION_INFLUENCE,
        campAvailable = companion.campAvailable,
        discoveryStatus = status,
        discoveryStatusLabel = localize("kingdom.companion.discovery.$status"),
        discoveryStages = companionDiscoveryStages.map { stage ->
            DiscoveryStageOption(
                value = stage,
                label = localize("kingdom.companion.discovery.$stage"),
                selected = stage == status,
            )
        }.toTypedArray(),
        plotHook = companion.plotHook,
        personalQuests = visibleQuests.map { quest -> questSummary(quest, isGM, localize) }.toTypedArray(),
        activeQuestCount = visibleQuests.count { it.status == "active" },
        isGM = isGM,
    )
}
