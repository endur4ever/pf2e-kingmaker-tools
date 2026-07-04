package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.companion.CompanionPersonalQuest
import at.posselt.pfrpg2e.companion.MAX_COMPANION_INFLUENCE
import at.posselt.pfrpg2e.companion.clampInfluence
import at.posselt.pfrpg2e.companion.companionDiscoveryStages
import at.posselt.pfrpg2e.companion.influenceBarPercent
import at.posselt.pfrpg2e.companion.normalizeDiscoveryStatus
import at.posselt.pfrpg2e.kingdom.data.RawCharacter
import at.posselt.pfrpg2e.kingdom.data.RawCompanionExpedition
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
    val isCompleted: Boolean
    val questHook: String?
    val isGM: Boolean
}

@JsPlainObject
external interface CompanionExpeditionSummaryContext {
    val id: String
    val title: String
    val status: String
    val statusLabel: String
    val daysRemaining: Int
    val totalDays: Int
    val progressPercent: Int
    val dc: Int
    val tier: String
    val tierLabel: String
    val isInProgress: Boolean
    val isAwaitingResolution: Boolean
    val isResolved: Boolean
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
    val level: Int
    val xp: Int
    val xpPercent: Int
    val expeditionStatus: String
    val expeditionStatusLabel: String
    val injuryDaysRemaining: Int?
    val currentExpeditions: Array<CompanionExpeditionSummaryContext>
    val pastExpeditions: Array<CompanionExpeditionSummaryContext>
    val canSendOnExpedition: Boolean
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
        isCompleted = quest.status == "completed",
        // Quest hooks are GM-facing narrative triggers; never expose to players.
        questHook = if (isGM) quest.questHook else null,
        isGM = isGM,
    )

/**
 * XP needed to reach the next level from [level]. Uses the simplified companion leveling curve.
 */
fun xpForLevel(level: Int): Int = level * 1000

/**
 * Builds the companion profile context. Pure (no Foundry i18n dependency) so it can be unit-tested;
 * the dialog passes [localize] = the real localizer, tests use the identity default.
 *
 * When [isGM] is false (read-only player view, Decision 5) only player-visible quests are shown and
 * quest hooks are stripped.
 *
 * [expeditions] are the raw expedition records; the companion's `actorUuid ?: name` is used to
 * filter those that involve this companion.
 */
fun buildCompanionProfileContext(
    partId: String,
    companion: RawCharacter,
    quests: List<CompanionPersonalQuest>,
    isGM: Boolean,
    expeditions: List<RawCompanionExpedition> = emptyList(),
    localize: (String) -> String = { it },
): CompanionProfileContext {
    val influence = clampInfluence(companion.influence)
    val status = normalizeDiscoveryStatus(companion.discoveryStatus)
    val visibleQuests = if (isGM) quests else quests.filter { it.visibleToPlayers }
    val companionKey = companion.actorUuid ?: companion.name
    val myExpeditions = expeditions.filter { exp -> companionKey in exp.companionIds }
    val currentExpeditions = myExpeditions.filter { it.status != "resolved" && it.status != "cancelled" }
    val pastExpeditions = myExpeditions.filter { it.status == "resolved" || it.status == "cancelled" }
    val level = companion.level
    val xp = companion.xp
    val xpPercent = if (level > 0) ((xp * 100) / xpForLevel(level)).coerceIn(0, 100) else 0
    val expeditionStatus = companion.expeditionStatus
    val canSendOnExpedition = isGM && expeditionStatus == "available" && companion.injuryDaysRemaining == null

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
        // Plot hooks are GM-authored narrative triggers; never expose to players.
        plotHook = if (isGM) companion.plotHook else null,
        personalQuests = visibleQuests.map { quest -> questSummary(quest, isGM, localize) }.toTypedArray(),
        activeQuestCount = visibleQuests.count { it.status == "active" },
        isGM = isGM,
        level = level,
        xp = xp,
        xpPercent = xpPercent,
        expeditionStatus = expeditionStatus,
        expeditionStatusLabel = localize("kingdom.companion.expeditionStatus.$expeditionStatus"),
        injuryDaysRemaining = companion.injuryDaysRemaining,
        currentExpeditions = currentExpeditions.map { exp ->
            val progress = if (exp.totalDays > 0) ((exp.totalDays - exp.daysRemaining) * 100 / exp.totalDays).coerceIn(0, 100) else 0
            val tierLabel = when (exp.tier) {
                "routine" -> localize("kingdom.expedition.tier.routine")
                "standard" -> localize("kingdom.expedition.tier.standard")
                "perilous" -> localize("kingdom.expedition.tier.perilous")
                else -> exp.tier
            }
            val statusLabel = when (exp.status) {
                "inProgress" -> localize("kingdom.expedition.status.inProgress")
                "awaitingResolution" -> localize("kingdom.expedition.status.awaitingResolution")
                "resolved" -> localize("kingdom.expedition.status.resolved")
                "cancelled" -> localize("kingdom.expedition.status.cancelled")
                else -> exp.status
            }
            CompanionExpeditionSummaryContext(
                id = exp.id,
                title = exp.title,
                status = exp.status,
                statusLabel = statusLabel,
                daysRemaining = exp.daysRemaining,
                totalDays = exp.totalDays,
                progressPercent = progress,
                // GM-only fields are blanked for the player-facing read-only profile (no info leak).
                dc = if (isGM) exp.dc else 0,
                tier = exp.tier,
                tierLabel = tierLabel,
                isInProgress = exp.status == "inProgress",
                isAwaitingResolution = exp.status == "awaitingResolution",
                isResolved = exp.status == "resolved",
            )
        }.toTypedArray(),
        pastExpeditions = pastExpeditions.map { exp ->
            val progress = if (exp.totalDays > 0) ((exp.totalDays - exp.daysRemaining) * 100 / exp.totalDays).coerceIn(0, 100) else 0
            val tierLabel = when (exp.tier) {
                "routine" -> localize("kingdom.expedition.tier.routine")
                "standard" -> localize("kingdom.expedition.tier.standard")
                "perilous" -> localize("kingdom.expedition.tier.perilous")
                else -> exp.tier
            }
            val statusLabel = when (exp.status) {
                "inProgress" -> localize("kingdom.expedition.status.inProgress")
                "awaitingResolution" -> localize("kingdom.expedition.status.awaitingResolution")
                "resolved" -> localize("kingdom.expedition.status.resolved")
                "cancelled" -> localize("kingdom.expedition.status.cancelled")
                else -> exp.status
            }
            CompanionExpeditionSummaryContext(
                id = exp.id,
                title = exp.title,
                status = exp.status,
                statusLabel = statusLabel,
                daysRemaining = exp.daysRemaining,
                totalDays = exp.totalDays,
                progressPercent = progress,
                // GM-only fields are blanked for the player-facing read-only profile (no info leak).
                dc = if (isGM) exp.dc else 0,
                tier = exp.tier,
                tierLabel = tierLabel,
                isInProgress = exp.status == "inProgress",
                isAwaitingResolution = exp.status == "awaitingResolution",
                isResolved = exp.status == "resolved",
            )
        }.toTypedArray(),
        canSendOnExpedition = canSendOnExpedition,
    )
}
