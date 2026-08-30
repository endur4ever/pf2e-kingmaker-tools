package at.posselt.pfrpg2e.camping

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure placement and encounter-budget math for the Encounter Stager
 * (`docs/plans/2026-07-09-plan-encounter-stager.md` phase 1).
 *
 * Everything here is deterministic and Foundry-free: the jsMain wiring converts [StagePoint] to
 * `com.pixijs.Point` and does the document I/O. Staging is a GM button, never a tick, so there is
 * no preview/commit parity concern -- but determinism still matters, because a GM who cancels and
 * re-opens the dialog must see the same ring.
 */

/** UI-free 2D point in scene pixel space. */
data class StagePoint(val x: Double, val y: Double)

/** Feet (engine distance) to scene pixels through the scene's own grid scale. */
fun feetToPixels(distanceFt: Double, gridDistanceFt: Double, gridSizePx: Double): Double {
    if (gridDistanceFt <= 0.0) return 0.0
    return distanceFt / gridDistanceFt * gridSizePx
}

/**
 * [count] token origins evenly spaced around a circle of [radiusPx] centred on [center].
 *
 * Returns TOP-LEFT origins, not centres: Foundry places a token by its top-left corner, so each
 * point is shifted by half a grid cell to put the token's middle on the ring rather than hanging
 * it off the edge. [startAngleRad] defaults to straight up, which is the plan's v1 answer to
 * ring facing -- orienting away from party facing needs the token's rotation and buys little.
 *
 * count <= 0 returns empty, matching the Stage button's disabled state, so the caller cannot
 * spawn "nothing" as a single token at the centre.
 */
fun ringPlacement(
    center: StagePoint,
    radiusPx: Double,
    count: Int,
    gridSizePx: Double,
    startAngleRad: Double = -PI / 2,
): List<StagePoint> {
    if (count <= 0) return emptyList()
    val halfCell = gridSizePx / 2.0
    val step = 2.0 * PI / count
    return (0 until count).map { index ->
        val angle = startAngleRad + step * index
        StagePoint(
            x = center.x + radiusPx * cos(angle) - halfCell,
            y = center.y + radiusPx * sin(angle) - halfCell,
        )
    }
}

/** Pure mirror of RawEncounterCreature for commonMain math and tests. */
data class StageCreature(
    val uuid: String,
    val count: Int,
    val adjustment: String? = null,
)

/** Total tokens the manifest spawns; 0 gates the Stage button. Negative counts never subtract. */
fun manifestSpawnCount(creatures: List<StageCreature>): Int =
    creatures.sumOf { it.count.coerceAtLeast(0) }

/**
 * XP one creature contributes, by its level RELATIVE to the party's (PF2e CRB encounter budget).
 *
 * Deliberately NOT taking party size, though the plan's sketch signature did: in PF2e a
 * creature's XP award depends only on the level difference. Party size adjusts the BUDGET the
 * encounter is measured against -- that is [encounterBudget]'s job -- and folding the two
 * together is how an encounter-builder starts quietly lying about difficulty.
 *
 * A creature more than 4 levels below the party contributes 0 (the table's floor); more than 4
 * above is off the table entirely and reports the +4 value rather than pretending to know.
 */
fun creatureXpContribution(partyLevel: Int, creatureLevel: Int): Int =
    when ((creatureLevel - partyLevel).coerceAtMost(4)) {
        -4 -> 10
        -3 -> 15
        -2 -> 20
        -1 -> 30
        0 -> 40
        1 -> 60
        2 -> 80
        3 -> 120
        4 -> 160
        else -> 0
    }

/** The five PF2e threat bands an encounter's total XP is read against. */
enum class EncounterThreat(val value: String) {
    TRIVIAL("trivial"),
    LOW("low"),
    MODERATE("moderate"),
    SEVERE("severe"),
    EXTREME("extreme");

    companion object {
        fun fromValue(value: String?): EncounterThreat? = entries.find { it.value == value }
    }
}

/**
 * The XP budget for [partySize] characters at [threat] (CRB: the four-PC budget plus the
 * per-additional-character adjustment, applied in both directions).
 */
fun encounterBudget(partySize: Int, threat: EncounterThreat): Int {
    val (base, perCharacter) = when (threat) {
        EncounterThreat.TRIVIAL -> 40 to 10
        EncounterThreat.LOW -> 60 to 15
        EncounterThreat.MODERATE -> 80 to 20
        EncounterThreat.SEVERE -> 120 to 30
        EncounterThreat.EXTREME -> 160 to 40
    }
    return base + (partySize - 4) * perCharacter
}

/**
 * The threat band [totalXp] lands in for a party of [partySize]: the highest band whose budget
 * it reaches. Below the trivial budget it is still TRIVIAL -- an encounter can be beneath every
 * threshold, but it cannot be less than trivial.
 */
fun threatForXp(partySize: Int, totalXp: Int): EncounterThreat =
    EncounterThreat.entries.lastOrNull { totalXp >= encounterBudget(partySize, it) }
        ?: EncounterThreat.TRIVIAL

/** Total XP of a manifest whose creature levels the caller has resolved. */
fun manifestXpTotal(partyLevel: Int, creatureLevels: List<Pair<Int, Int>>): Int =
    creatureLevels.sumOf { (level, count) ->
        creatureXpContribution(partyLevel, level) * count.coerceAtLeast(0)
    }
