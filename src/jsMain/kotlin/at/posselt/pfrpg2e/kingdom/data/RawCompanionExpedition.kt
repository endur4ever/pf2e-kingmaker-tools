package at.posselt.pfrpg2e.kingdom.data

import kotlin.js.JsExport

/**
 * Represents an in-flight companion expedition — a mission where one or more
 * companions are sent away from the kingdom to undertake a task.
 *
 * Expeditions progress on the daily world-clock tick. When complete, they
 * enter `awaitingResolution` status and require a GM to confirm the outcome
 * via a chat offer card.
 */
@JsExport
external interface RawCompanionExpedition {
    /** Unique identifier for this expedition instance. */
    var id: String
    /** Activity template ID this expedition was created from (e.g., "scout", "hunt"). */
    var activityId: String
    /** Display name (copied from activity at creation time). */
    var title: String
    /** Companion IDs participating in this expedition (actorUuid ?: name). */
    var companionIds: Array<String>
    /** Current status of the expedition. */
    var status: String
    /** Days remaining until completion. */
    var daysRemaining: Int
    /** Total duration in days. */
    var totalDays: Int
    /** DC of the expedition check (resolved at creation). */
    var dc: Int
    /** Difficulty tier (routine | standard | perilous). */
    var tier: String
    /** Degree of success outcome (null until resolved). */
    var outcomeDegree: String?
    /** XP accrued by participants (populated on resolution). */
    var accruedXp: Int
    /** Influence delta accrued (populated on resolution). */
    var accruedInfluenceDelta: Int
    /** Injury condition slugs from critical failure (offered, not applied). */
    var accruedInjuries: Array<String>
    /** Loot tier earned (none | minor | moderate | major). */
    var lootTier: String?
    /** Faction standing delta (0 unless diplomacy). */
    var factionStandingDelta: Int
    /** Personal quest spawned by this expedition (if any). */
    var spawnedQuestId: String?
    /** The specific personal quest this expedition is pursuing (for "personal-quest" activities). */
    var targetQuestId: String?
    /** GM notes for narrative integration. */
    var gmNotes: String
    /** Whether this expedition is visible to players (read-only board). */
    var visibleToPlayers: Boolean
    /** Whether the reward has been applied (GM-confirmed). */
    var rewardApplied: Boolean
    /** ISO timestamp of creation. */
    var createdAt: String?
}

/**
 * Creates a [RawCompanionExpedition] with sensible defaults.
 */
fun createRawCompanionExpedition(
    id: String,
    activityId: String,
    title: String,
    companionIds: Array<String>,
    totalDays: Int,
    dc: Int,
    tier: String,
    visibleToPlayers: Boolean = false,
    createdAt: String? = null,
    targetQuestId: String? = null,
): RawCompanionExpedition {
    val obj = js("{ }").unsafeCast<RawCompanionExpedition>()
    obj.id = id
    obj.activityId = activityId
    obj.title = title
    obj.companionIds = companionIds
    obj.status = "inProgress"
    obj.daysRemaining = totalDays
    obj.totalDays = totalDays
    obj.dc = dc
    obj.tier = tier
    obj.outcomeDegree = null
    obj.accruedXp = 0
    obj.accruedInfluenceDelta = 0
    obj.accruedInjuries = emptyArray()
    obj.lootTier = null
    obj.factionStandingDelta = 0
    obj.spawnedQuestId = null
    obj.targetQuestId = targetQuestId
    obj.gmNotes = ""
    obj.visibleToPlayers = visibleToPlayers
    obj.rewardApplied = false
    obj.createdAt = createdAt
    return obj
}
