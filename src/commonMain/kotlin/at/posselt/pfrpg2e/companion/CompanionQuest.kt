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
}

/** Optional richer reward payload (currently influence is the primary auto-applied reward). */
@JsPlainObject
external interface CompanionQuestRewards {
    var influence: Int?
    var xp: Int?
    var rp: Int?
    var customReward: String?
}
