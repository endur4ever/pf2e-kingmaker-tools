package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.armies.BattleStatus
import at.posselt.pfrpg2e.data.kingdom.MAX_FACTION_STANDING
import at.posselt.pfrpg2e.data.kingdom.MIN_FACTION_STANDING

/**
 * Pure war-resolution logic: how a battle result shifts the enemy faction's standing, when a war is
 * over (peace becomes offerable), and what each peace choice does.
 *
 * See card t_fe8399aa.
 */

/**
 * The reason labels this subsystem stamps onto [at.posselt.pfrpg2e.kingdom.data.RawFactionStandingEntry].
 *
 * They are i18n keys, matching how every other standing source records itself
 * (`kingdom.factionStanding.expedition`, `…allianceChange`, `…annexation`). Kept as constants rather
 * than hand-written string literals at each call site precisely because there are now four of them
 * across three files, and a typo in a literal is invisible to the i18n guard.
 */
object WarStandingReason {
    const val VICTORY = "kingdom.factionStanding.warVictory"
    const val DEFEAT = "kingdom.factionStanding.warDefeat"
    const val PEACE = "kingdom.factionStanding.peaceTreaty"
    const val TRIBUTE = "kingdom.factionStanding.tribute"
}

/**
 * How far a single battle moves the enemy faction's standing.
 *
 * Magnitudes are calibrated against this codebase's existing standing economy, not invented: the
 * faction scale runs [-100, 100] in ~30-point attitude bands, and a diplomacy expedition already
 * moves ±4 on a success and ±8 on a critical (see ExpeditionResolverEngine). A pitched battle is
 * worth about a successful expedition, so the default is 4. A smaller number would make an entire
 * war statistically invisible on the standing tracker, which is the thing this card exists to fix.
 *
 * ACTIVE and RETREAT produce no movement: an unfinished battle has no result yet, and a withdrawal
 * is explicitly a survivable outcome elsewhere in the army lifecycle rather than a loss.
 */
fun warStandingDelta(
    result: BattleStatus,
    victoryBonus: Int = 4,
    defeatPenalty: Int = 4,
): Int = when (result) {
    BattleStatus.VICTORY -> victoryBonus
    BattleStatus.DEFEAT -> -defeatPenalty
    BattleStatus.ACTIVE, BattleStatus.RETREAT -> 0
}

/**
 * How a war threat ended, for peace-eligibility purposes.
 *
 * Deliberately three-valued rather than a `defeated` boolean, because the persisted status domain
 * has four members (ACTIVE / DEFEATED / EVADED / EXPIRED) and collapsing it to a boolean is wrong
 * in both directions: mapping only DEFEATED to true wedges a war permanently un-endable once any
 * threat is evaded or expires, and mapping "not ACTIVE" to true congratulates the kingdom on a war
 * whose invasion actually landed.
 */
enum class ThreatOutcome {
    /** Still live — the war is not over. */
    ACTIVE,

    /** Beaten in battle. */
    DEFEATED,

    /** Evaded or expired: off the board, but not a victory. */
    ENDED_OTHERWISE,
}

/** Minimal view of a war threat for peace-eligibility (mapped from RawWarThreat in jsMain). */
data class ThreatState(
    val enemyFactionName: String?,
    val outcome: ThreatOutcome,
)

/**
 * Peace with [factionName] is offerable when that faction has at least one linked threat, none of
 * them is still ACTIVE, and at least one was actually DEFEATED.
 *
 * The "at least one DEFEATED" clause is what stops the module offering peace terms over a war the
 * kingdom lost: a threat that reached max escalation and expired put an invasion on the map, and
 * clearing the board that way is not a victory to dictate terms from.
 *
 * A blank faction name is never eligible — unlinked threats would otherwise all fuse into a single
 * pseudo-faction and offer peace with nobody.
 */
fun peaceEligible(threats: List<ThreatState>, factionName: String): Boolean {
    if (factionName.isBlank()) return false
    val linked = threats.filter { it.enemyFactionName == factionName }
    return linked.isNotEmpty() &&
        linked.none { it.outcome == ThreatOutcome.ACTIVE } &&
        linked.any { it.outcome == ThreatOutcome.DEFEATED }
}

/** What the GM can do once a war is won. */
enum class PeaceChoice { SIGN_PEACE, DEMAND_TRIBUTE, IGNORE }

/** The mechanical effect of a peace choice. */
data class PeaceOutcome(
    val standingDelta: Int,
    val rpGain: Int,
    /**
     * Clears `RawGroup.atWar` for this faction only — NOT the kingdom-wide `KingdomData.atWar`,
     * which carries a +1 unrest per turn and must stay set while any other faction is still at war.
     * Derive that one with [kingdomRemainsAtWar].
     */
    val clearsFactionAtWar: Boolean,
)

/**
 * Resolve a [PeaceChoice].
 *
 * [currentStanding] MUST be read at the moment the GM confirms, not when the offer card is posted.
 * These cards sit in chat until someone clicks them, and another battle can move standing in the
 * meantime; a delta computed from a stale snapshot would land SIGN_PEACE below its own floor.
 *
 * - SIGN_PEACE ends the war and raises standing to [standingFloor], never lowering it.
 * - DEMAND_TRIBUTE ends the war for a one-time [tributeRp] gain at the cost of [tributePenalty]
 *   standing. It deliberately does NOT set `allianceLevel = "tribute"`: that treaty tier is a
 *   separate GM-driven mechanic that already pays recurring RP each turn, and setting it here would
 *   double-pay on the turn the indemnity lands.
 * - IGNORE does nothing and leaves the war running.
 */
fun peaceOutcome(
    choice: PeaceChoice,
    currentStanding: Int,
    standingFloor: Int,
    tributeRp: Int,
    tributePenalty: Int = 8,
): PeaceOutcome = when (choice) {
    PeaceChoice.SIGN_PEACE ->
        PeaceOutcome(
            // Clamp the floor before differencing: an out-of-range floor would otherwise produce a
            // delta the standing clamp silently swallows while standingLog recorded the raw number,
            // leaving a log that cannot be replayed to the stored value.
            standingDelta = (standingFloor.coerceIn(MIN_FACTION_STANDING, MAX_FACTION_STANDING) - currentStanding)
                .coerceAtLeast(0),
            rpGain = 0,
            clearsFactionAtWar = true,
        )

    PeaceChoice.DEMAND_TRIBUTE ->
        PeaceOutcome(
            standingDelta = -tributePenalty,
            rpGain = tributeRp.coerceAtLeast(0),
            clearsFactionAtWar = true,
        )

    PeaceChoice.IGNORE ->
        PeaceOutcome(standingDelta = 0, rpGain = 0, clearsFactionAtWar = false)
}

/**
 * Whether the kingdom-wide war flag should stay set after one faction makes peace.
 *
 * [otherFactionsAtWar] is every OTHER faction's `atWar` flag. The kingdom-level flag drives a
 * standing +1 unrest per turn and the `@atWar` modifier expression, so it must only clear when the
 * last war ends — signing peace with one of two enemies does not stop the war.
 */
fun kingdomRemainsAtWar(otherFactionsAtWar: List<Boolean>): Boolean = otherFactionsAtWar.any { it }
