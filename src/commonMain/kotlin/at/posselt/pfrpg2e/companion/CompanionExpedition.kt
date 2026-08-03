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

/**
 * Result of resolving a companion expedition attempt.
 */
@JsExport
data class ExpeditionResult(
    /** The expedition that was resolved. */
    val expedition: CompanionExpedition,
    /** The companion's check result (raw roll). */
    val roll: Int,
    /** The degree of success/failure. */
    val degreeOfSuccess: String,
    /** XP actually gained (may be 0 on failure). */
    val xpEarned: Int,
    /** Influence actually gained (may be 0 on failure). */
    val influenceEarned: Int,
    /** Whether loot was obtained. */
    val lootObtained: Boolean,
    /** Human-readable summary of the outcome. */
    val summary: String,
)

/**
 * Resolve an expedition attempt for a companion.
 *
 * The companion rolls a check against the expedition DC. The degree of success
 * determines how much XP and influence they earn.
 *
 * - Critical Success: full rewards + bonus XP
 * - Success: full rewards
 * - Failure: half XP, no influence
 * - Critical Failure: no rewards, possible setback
 */
fun resolveExpedition(
    expedition: CompanionExpedition,
    roll: Int,
    degreeOfSuccess: String,
): ExpeditionResult {
    val xpEarned = when (degreeOfSuccess) {
        "critical_success" -> expedition.xpReward + 2
        "success" -> expedition.xpReward
        "failure" -> expedition.xpReward / 2
        else -> 0 // critical_failure
    }

    val influenceEarned = when (degreeOfSuccess) {
        "critical_success" -> expedition.influenceReward + 1
        "success" -> expedition.influenceReward
        else -> 0
    }

    val lootObtained = degreeOfSuccess == "critical_success" && expedition.loot != null

    val summary = when (degreeOfSuccess) {
        "critical_success" -> "Critical success! The companion excelled at ${expedition.name}, earning bonus rewards."
        "success" -> "Success! The companion completed ${expedition.name} as expected."
        "failure" -> "Failure. The companion struggled with ${expedition.name} but learned from the experience."
        else -> "Critical failure! The companion failed ${expedition.name} and returned empty-handed."
    }

    return ExpeditionResult(
        expedition = expedition,
        roll = roll,
        degreeOfSuccess = degreeOfSuccess,
        xpEarned = xpEarned,
        influenceEarned = influenceEarned,
        lootObtained = lootObtained,
        summary = summary,
    )
}
