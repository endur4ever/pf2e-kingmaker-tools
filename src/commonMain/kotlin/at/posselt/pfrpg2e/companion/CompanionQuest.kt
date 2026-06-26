package at.posselt.pfrpg2e.companion

import kotlinx.js.JsPlainObject

/**
 * A personal quest tied to a single companion (roadmap #7). Stored in
 * [at.posselt.pfrpg2e.kingdom.KingdomData.companionPersonalQuests], separate from kingdom quests.
 * Defaults to GM-only visibility (Decision 2); GM reveals to players with one click.
 */
@JsPlainObject
external interface CompanionPersonalQuest {
    var id: String
    var title: String
    var description: String
    /** RawCharacter.actorUuid when linked, otherwise the companion's name. */
    var companionId: String
    /** "active" | "completed" | "failed" | "abandoned" */
    var status: String
    /** GM-facing narrative hook / trigger condition. */
    var questHook: String?
    /** null = no deadline. */
    var turnsRemaining: Int?
    /** If true, players can see this quest on the kingdom quests page and companion profile. */
    var visibleToPlayers: Boolean
    /** Influence points granted to the companion on completion (auto-applied, Decision 3). */
    var influenceReward: Int
    /** XP granted to the companion on completion (auto-applied when companion leveling is enabled). */
    var rewards: CompanionQuestRewards?
}

/** Optional richer reward payload (currently influence is the primary auto-applied reward). */
@JsPlainObject
external interface CompanionQuestRewards {
    var influence: Int?
    var xp: Int?
    var rp: Int?
    var customReward: String?
}

/**
 * Select which personal quest an expedition's completion reward applies to.
 *
 * Prefers the explicitly targeted quest ([targetQuestId]) when it is still active, so a
 * companion with multiple active quests gets the reward on the RIGHT one. Falls back to the
 * companion's first active quest for legacy expeditions created before target linkage existed
 * (targetQuestId == null). Returns null when no active quest matches.
 */
fun selectRewardQuest(
    quests: List<CompanionPersonalQuest>,
    companionId: String,
    targetQuestId: String?,
): CompanionPersonalQuest? {
    if (targetQuestId != null) {
        val targeted = quests.find { it.id == targetQuestId && it.status == "active" }
        if (targeted != null) return targeted
    }
    return quests.find { it.companionId == companionId && it.status == "active" }
}
