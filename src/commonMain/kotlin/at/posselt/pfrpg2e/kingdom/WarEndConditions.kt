package at.posselt.pfrpg2e.kingdom

/**
 * Pure war-resolution logic: how a battle result shifts the enemy faction's standing, when a war is
 * over (peace becomes offerable), and what each peace choice does. Today winning a battle marks the
 * threat defeated and recomputes pressure but never touches faction standing, never clears `atWar`,
 * and never offers peace; threats also link to a faction only by free text.
 *
 * This is the deterministic core; adding `enemyFactionId` to RawWarThreat + its Migration, the
 * faction dropdown in AddWarThreat, and the GM-confirmed victory/defeat/peace offer cards (reusing
 * the existing ModifyFactionStanding + standingLog plumbing) is the deferred jsMain wiring.
 *
 * See card t_fe8399aa.
 */

/** The outcome of a battle against a faction-linked war threat. */
enum class BattleResult { VICTORY, DEFEAT }

/**
 * The standing delta a faction-linked battle produces: beating their army improves your standing with
 * them (they respect strength / you can dictate terms), losing worsens it. Both magnitudes are
 * caller-configured and applied through the existing standing plumbing.
 */
fun warStandingDelta(
    result: BattleResult,
    victoryBonus: Int = 1,
    defeatPenalty: Int = 1,
): Int = when (result) {
    BattleResult.VICTORY -> victoryBonus
    BattleResult.DEFEAT -> -defeatPenalty
}

/** Minimal view of a war threat for peace-eligibility (mapped from RawWarThreat in jsMain). */
data class ThreatState(
    val enemyFactionId: String?,
    val defeated: Boolean,
)

/**
 * Peace with [factionId] is offerable exactly when it has at least one linked threat and every one of
 * them is defeated — i.e. the LAST active threat for that faction just fell. A faction with no linked
 * threats, or with any still-active threat, is not eligible.
 */
fun peaceEligible(threats: List<ThreatState>, factionId: String): Boolean {
    val linked = threats.filter { it.enemyFactionId == factionId }
    return linked.isNotEmpty() && linked.all { it.defeated }
}

/** What the GM can do once a war is won. */
enum class PeaceChoice { SIGN_PEACE, DEMAND_TRIBUTE, IGNORE }

/** The mechanical effect of a peace choice. */
data class PeaceOutcome(
    val standingDelta: Int,
    val rpGain: Int,
    val clearsAtWar: Boolean,
)

/**
 * Resolve a [PeaceChoice]:
 * - SIGN_PEACE ends the war and bumps standing up to [standingFloor] (no bump if already at/above it),
 *   no RP.
 * - DEMAND_TRIBUTE ends the war for a one-time [tributeRp] gain but costs [tributePenalty] standing.
 * - IGNORE does nothing and leaves the war flag set.
 */
fun peaceOutcome(
    choice: PeaceChoice,
    currentStanding: Int,
    standingFloor: Int,
    tributeRp: Int,
    tributePenalty: Int = 2,
): PeaceOutcome = when (choice) {
    PeaceChoice.SIGN_PEACE ->
        PeaceOutcome(
            standingDelta = (standingFloor - currentStanding).coerceAtLeast(0),
            rpGain = 0,
            clearsAtWar = true,
        )
    PeaceChoice.DEMAND_TRIBUTE ->
        PeaceOutcome(
            standingDelta = -tributePenalty,
            rpGain = tributeRp.coerceAtLeast(0),
            clearsAtWar = true,
        )
    PeaceChoice.IGNORE ->
        PeaceOutcome(standingDelta = 0, rpGain = 0, clearsAtWar = false)
}
