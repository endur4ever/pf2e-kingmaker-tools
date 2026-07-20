package at.posselt.pfrpg2e.kingdom

/**
 * Suggested fallout from losing a war battle (all GM-confirmed offers, never auto-applied). Without
 * these, losing every army to an invasion is mechanically identical to never fighting.
 */
data class DefeatConsequences(
    /** Unrest the kingdom gains from the defeat. */
    val unrestGain: Int,
    /** War-pressure spike (the enemy presses its advantage). */
    val pressureJump: Int,
    /** How much the threat's escalation rises (0 when already at max). */
    val escalationBump: Int,
    /** Whether the defeat should offer to spawn the war-threat-arrival event now (invasion lands). */
    val spawnArrivalEvent: Boolean,
)

/**
 * Pure defeat-consequence calculator. Numbers are deliberately modest and scale with how far the
 * threat has escalated and what the defeat cost:
 *
 * - unrestGain = 1 (the loss itself) + up to 3 for armies lost + 2 if a settlement was the target.
 * - pressureJump = 2 + up to 3 scaled by the threat's escalation fraction (a near-max threat that
 *   beats you surges hardest).
 * - escalationBump = 1, unless the threat is already at its maximum escalation (then 0).
 * - spawnArrivalEvent = a settlement-targeting threat that is already at max escalation has arrived.
 *
 * @param threatEscalation current escalation (clamped to 0..[maxEscalation])
 * @param maxEscalation the threat's escalation ceiling (min 1)
 * @param armiesLost armies the kingdom lost in the battle
 * @param settlementTargeted whether the threat targets a settlement
 */
fun calculateDefeatConsequences(
    threatEscalation: Int,
    maxEscalation: Int,
    armiesLost: Int,
    settlementTargeted: Boolean,
): DefeatConsequences {
    val max = maxEscalation.coerceAtLeast(1)
    val escalation = threatEscalation.coerceIn(0, max)
    val unrestGain = 1 + armiesLost.coerceIn(0, 3) + if (settlementTargeted) 2 else 0
    val pressureJump = 2 + (escalation * 3 / max)
    val escalationBump = if (escalation < max) 1 else 0
    val spawnArrivalEvent = settlementTargeted && escalation >= max
    return DefeatConsequences(
        unrestGain = unrestGain,
        pressureJump = pressureJump,
        escalationBump = escalationBump,
        spawnArrivalEvent = spawnArrivalEvent,
    )
}
