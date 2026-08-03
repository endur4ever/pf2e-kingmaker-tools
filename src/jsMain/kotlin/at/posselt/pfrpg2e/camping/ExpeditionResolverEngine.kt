package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.math.roundToInt

/**
 * Pure expedition resolution engine.
 *
 * Given a base influence, tier, and degree of success, computes the actual
 * awards (XP, influence, loot, injuries, faction standing, notes).
 *
 * XP is a flat per-degree base × tier multiplier (rounded). The per-degree base
 * is owned by this pure layer so it is unit-tested; failure and critical failure
 * still award a non-zero trickle (anti-death-spiral). See design doc section 4.
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

    /** Flat per-degree base XP (before tier multiplier). */
    private const val XP_CRITICAL_SUCCESS = 120
    private const val XP_SUCCESS = 80
    private const val XP_FAILURE = 30
    private const val XP_CRITICAL_FAILURE = 10

    /**
     * Flat per-degree base faction-standing delta for "diplomacy" expeditions (before tier
     * multiplier). The standing scale is ±100 (see [at.posselt.pfrpg2e.data.kingdom.applyStandingDelta])
     * with attitude bands: Hostile ≤ −50, Unfriendly −49..−15, Indifferent −14..14, Friendly 15..49,
     * Helpful ≥ 50 (roughly 29–35 points each). On standard tier a success is +4, so crossing from
     * Indifferent (0) into Friendly (15) takes ~4 expeditions; reaching Helpful (50) is deliberately
     * a long grind (~13). A crit (+8) is twice as fast; a crit failure (−4) sours by the same step.
     * Keeps the owner's intended ratio (crit = 2× success, crit-fail = −success).
     */
    private const val STANDING_CRITICAL_SUCCESS = 8
    private const val STANDING_SUCCESS = 4
    private const val STANDING_FAILURE = 0
    private const val STANDING_CRITICAL_FAILURE = -4

    /**
     * Result of resolving an expedition.
     *
     * @property xpAwarded XP actually awarded (per-degree base × tier multiplier).
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
     * @param baseInfluence The base influence gain (applied only on success degrees).
     * @param tier The difficulty tier (routine | standard | perilous).
     * @param degree The degree of success from the check.
     * @return An [ExpeditionResolutionResult] with all awards.
     */
    fun resolve(
        baseInfluence: Int,
        tier: String,
        degree: DegreeOfSuccess,
    ): ExpeditionResolutionResult {
        val multiplier = tierMultiplier(tier)

        return when (degree) {
            DegreeOfSuccess.CRITICAL_SUCCESS -> ExpeditionResolutionResult(
                xpAwarded = (XP_CRITICAL_SUCCESS * multiplier).roundToInt(),
                influenceDelta = baseInfluence,
                lootTier = "major",
                injuryConditions = emptyArray(),
                factionStandingDelta = 0,
                gmNotes = "",
            )
            DegreeOfSuccess.SUCCESS -> ExpeditionResolutionResult(
                xpAwarded = (XP_SUCCESS * multiplier).roundToInt(),
                influenceDelta = baseInfluence,
                lootTier = "moderate",
                injuryConditions = emptyArray(),
                factionStandingDelta = 0,
                gmNotes = "",
            )
            DegreeOfSuccess.FAILURE -> ExpeditionResolutionResult(
                xpAwarded = (XP_FAILURE * multiplier).roundToInt(),
                influenceDelta = 0,
                lootTier = "none",
                injuryConditions = emptyArray(),
                factionStandingDelta = 0,
                gmNotes = "lost time, returned whole",
            )
            DegreeOfSuccess.CRITICAL_FAILURE -> ExpeditionResolutionResult(
                xpAwarded = (XP_CRITICAL_FAILURE * multiplier).roundToInt(),
                influenceDelta = 0,
                lootTier = "none",
                injuryConditions = arrayOf("fatigued", "wounded"),
                factionStandingDelta = 0,
                gmNotes = "Seeds a narrative hook for the GM",
            )
        }
    }

    /**
     * Faction-standing delta a "diplomacy" expedition credits to its target faction,
     * scaled by the same tier multiplier as XP and rounded. Positive on success,
     * negative on critical failure, zero on plain failure. Non-diplomacy expeditions
     * never call this (their standing delta stays 0). Pure and unit-tested.
     */
    fun diplomacyStandingDelta(tier: String, degree: DegreeOfSuccess): Int {
        val base = when (degree) {
            DegreeOfSuccess.CRITICAL_SUCCESS -> STANDING_CRITICAL_SUCCESS
            DegreeOfSuccess.SUCCESS -> STANDING_SUCCESS
            DegreeOfSuccess.FAILURE -> STANDING_FAILURE
            DegreeOfSuccess.CRITICAL_FAILURE -> STANDING_CRITICAL_FAILURE
        }
        return (base * tierMultiplier(tier)).roundToInt()
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
