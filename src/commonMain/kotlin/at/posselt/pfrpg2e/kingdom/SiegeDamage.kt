package at.posselt.pfrpg2e.kingdom

/**
 * Pure severity math for a siege. When a settlement-targeting war threat arrives undefeated it can
 * sack the town, razing some of its built structures. Everything here is deterministic input ->
 * output so it can be unit-tested and previewed on the GM offer card before anything is applied; the
 * concrete structure selection, the `isDestroyed` flagging in the settlement evaluation, the
 * migration, and the GM-confirmed `km-offer-*` card itself live in jsMain (deferred).
 *
 * See card t_0d733507: "Sack: destroy N structures (N from a pure severity function of threat
 * escalation)", reduced by defensive structures.
 */

/**
 * Kingmaker structure ids that harden a settlement against a sack (base ids; the "-vk" Vance &
 * Kerenshara homebrew variants collapse onto these). Each pair of them shaves one structure off the
 * destruction count. A garrisoned ARMY is not counted here — that mitigation lives in
 * [siegeDamageWithGarrison], which the sibling garrison-effects card (t_35bb2e42) wired in; this
 * function stays purely about the settlement's built defences.
 */
val DEFENSIVE_STRUCTURE_IDS: Set<String> = setOf(
    "wall-wooden",
    "wall-stone",
    "watchtower",
    "watchtower-stone",
    "garrison",
    "barracks",
    "keep",
    "castle",
)

/** Count a settlement's defensive structures, treating the "-vk" homebrew variants as their base id. */
fun countDefensiveStructures(structureIds: List<String>): Int =
    structureIds.count { it.removeSuffix("-vk") in DEFENSIVE_STRUCTURE_IDS }

/** The outcome of a sack: how many structures fall and how much unrest the atrocity stirs up. */
data class SiegeDamage(
    val structuresDestroyed: Int,
    val unrestGain: Int,
)

/** The most structures a single sack can raze, before defensive mitigation. */
const val MAX_SIEGE_STRUCTURES_DESTROYED = 3

/**
 * Compute how many structures a sack destroys and the unrest it generates.
 *
 * - [threatEscalation] is clamped to `0..maxEscalation`; [maxEscalation] is treated as at least 1.
 * - Base destruction scales with how far the threat escalated: `escalation * 3 / maxEscalation`, so
 *   a fully-escalated threat razes [MAX_SIEGE_STRUCTURES_DESTROYED]. Any positive escalation razes at
 *   least one structure; a non-escalated (0) threat razes none.
 * - Every two defensive structures ([countDefensiveStructures]) prevent one destruction; the result
 *   never drops below 0.
 * - Unrest is 1 for the assault itself plus 1 per structure lost (capped), so even a bloodless
 *   repulse (0 destroyed) still yields a single point of unrest for the attack.
 */
fun calculateSiegeDamage(
    threatEscalation: Int,
    maxEscalation: Int,
    defensiveStructureCount: Int,
): SiegeDamage {
    val max = maxEscalation.coerceAtLeast(1)
    val escalation = threatEscalation.coerceIn(0, max)
    val base = if (escalation <= 0) {
        0
    } else {
        (escalation * MAX_SIEGE_STRUCTURES_DESTROYED / max)
            .coerceIn(1, MAX_SIEGE_STRUCTURES_DESTROYED)
    }
    val mitigation = defensiveStructureCount.coerceAtLeast(0) / 2
    val destroyed = (base - mitigation).coerceAtLeast(0)
    val unrest = 1 + destroyed.coerceAtMost(MAX_SIEGE_STRUCTURES_DESTROYED)
    return SiegeDamage(structuresDestroyed = destroyed, unrestGain = unrest)
}
