package at.posselt.pfrpg2e.companion

import kotlin.js.JsExport

/**
 * Represents a companion expedition — a mission where a companion is sent away
 * from the kingdom to undertake a task, gaining XP and influence on return.
 *
 * Expeditions are defined by their difficulty (DC), duration (in days),
 * and potential rewards (XP, influence, and optional loot/items).
 */
@JsExport
data class CompanionExpedition(
    /** Unique identifier for this expedition instance. */
    val id: String,
    /** Display name of the expedition. */
    val name: String,
    /** Flavor text describing what the companion will be doing. */
    val description: String,
    /** The DC (Difficulty Class) of the expedition check. */
    val dc: Int,
    /** How many in-game days the expedition takes. */
    val durationDays: Int,
    /** XP the companion gains upon successful completion. */
    val xpReward: Int,
    /** Influence the companion gains upon successful completion. */
    val influenceReward: Int,
    /** Optional loot item granted on success. */
    val loot: String? = null,
    /** Whether this expedition is currently available (not on cooldown). */
    val available: Boolean = true,
    /** Required companion level to attempt this expedition (null = no minimum). */
    val minimumLevel: Int? = null,
    /** Tags for filtering/category (e.g., "combat", "exploration", "diplomacy"). */
    val tags: Array<String> = emptyArray(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CompanionExpedition) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}


