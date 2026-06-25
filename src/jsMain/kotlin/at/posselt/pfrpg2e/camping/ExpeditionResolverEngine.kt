package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Pure expedition resolution engine.
 *
 * Given a base XP, base influence, tier, and degree of success, computes
 * the actual awards (XP, influence, loot, injuries, faction standing, notes).
 *
 * This engine contains NO Foundry/DOM/roll/chat/persistence dependencies and
 * is fully unit-testable.
 *
 * Tier multiplier (applied to XP):
 *   - routine: ×0.75
 *   - standard: ×1.0
 *   - perilous: ×1.5
 *
 * Influence is returned as raw intent — clamping happens at the apply site
 * via `clampInfluence()`.
 */
@JsExport
@JsName("ExpeditionResolverEngine")
object ExpeditionResolverEngine {

    /**
     * Result of resolving an expedition.
     *
     * @property xpAwarded XP actually awarded (after tier multiplier).
     * @property influenceDelta Influence change (raw, not clamped).
     * @property lootTier Loot tier earned.
     * @property injuryConditions Injury condition slugs (offered, not applied).
     * @property factionStandingDelta Faction standing change (0 unless diplomacy).
     * @property gmNotes Narrative notes for the GM.
     */
    data class ExpeditionResolutionResult(
        val xpAwarded: Int,
        val influenceDelta: Int,
        val lootTier: String,
        val injuryConditions: Array<String>,
        val factionStandingDelta: Int,
        val gmNotes: String,
    )

    /**
     * Resolve an expedition outcome.
     *
     * @param baseXp The base XP before tier multiplier.
     * @param baseInfluence The base influence gain (before degree modifier).
     * @param tier The difficulty tier (routine | standard | perilous).
     * @param degree The degree of success from the check.
     * @return An [ExpeditionResolutionResult] with all awards.
     */
    fun resolve(
        baseXp: Int,
        baseInfluence: Int,
        tier: String,
        degree: DegreeOfSuccess,
    ): ExpeditionResolutionResult {
        val multiplier = tierMultiplier(tier)

        return when (degree) {
            DegreeOfSuccess.CRITICAL_SUCCESS -> ExpeditionResolutionResult(
                xpAwarded = (baseXp * multiplier).toInt(),
                influenceDelta = baseInfluence,
                lootTier = "major",
                injuryConditions = emptyArray(),
                factionStandingDelta = 0,
                gmNotes = "",
            )
            DegreeOfSuccess.SUCCESS -> ExpeditionResolutionResult(
                xpAwarded = (baseXp * multiplier).toInt(),
                influenceDelta = baseInfluence,
                lootTier = "moderate",
                injuryConditions = emptyArray(),
                factionStandingDelta = 0,
                gmNotes = "",
            )
            DegreeOfSuccess.FAILURE -> ExpeditionResolutionResult(
                xpAwarded = (baseXp * multiplier).toInt(),
                influenceDelta = 0,
                lootTier = "none",
                injuryConditions = emptyArray(),
                factionStandingDelta = 0,
                gmNotes = "lost time, returned whole",
            )
            DegreeOfSuccess.CRITICAL_FAILURE -> ExpeditionResolutionResult(
                xpAwarded = (baseXp * multiplier).toInt(),
                influenceDelta = 0,
                lootTier = "none",
                injuryConditions = arrayOf("fatigued", "wounded"),
                factionStandingDelta = 0,
                gmNotes = "Seeds a narrative hook for the GM",
            )
        }
    }

    /**
     * Get the XP multiplier for a given tier.
     */
    fun tierMultiplier(tier: String): Double = when (tier) {
        "routine" -> 0.75
        "standard" -> 1.0
        "perilous" -> 1.5
        else -> 1.0
    }
}
