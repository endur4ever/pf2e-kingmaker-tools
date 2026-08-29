package at.posselt.pfrpg2e.kingdom.deeds

import at.posselt.pfrpg2e.data.kingdom.settlements.SettlementSizeType

/**
 * Pure core of the Deeds Chronicle (`docs/plans/2026-07-09-plan-deeds-chronicle.md`, phase 1).
 *
 * Detectors are LEVEL-triggered: they answer "is this true now", never "did this just become
 * true". A road stays built and a claimed region stays claimed, so a detector re-fires every turn
 * for the rest of the campaign — which is intended and must stay intended. Idempotence is the
 * caller's job via [undetectedDeeds], because only the caller knows whether the GM has already
 * ANSWERED the offer (awarded or dismissed; `milestoneOfferAnswered`). Suppressing on "completed"
 * alone re-posts the identical card every turn to a GM who declined — the bug `offerDismissed`
 * exists to prevent.
 *
 * Detectors are free of side effects and randomness: End Turn evaluates them twice, once for the
 * Turn Wizard preview and once on commit, and the two must agree.
 */

/**
 * Everything a detector may read, assembled in jsMain from live state.
 *
 * Phase 1 carries the scalar fields; the per-turn history and settlement snapshots the plan's
 * catalog rules need arrive with the catalog in phase 2 — adding them now would be surface with no
 * reader, which is how fields drift.
 */
data class DeedInputs(
    val turn: Int = 0,
    val level: Int = 0,
    val size: Int = 0,
    val unrest: Int = 0,
    val fame: Int = 0,
    val claimedHexCount: Int = 0,
    val roadHexCount: Int = 0,
    val regionsFullyClaimed: Int = 0,
    val settlementsRoadedToCapital: Int = 0,
    val armiesWon: Int = 0,
    val consecutiveSafeCaravanTurns: Int = 0,
    val tradeAgreements: Int = 0,
    // Phase 2 additions, each read by at least one catalog detector below.
    /** One entry per settlement, so the size detectors read a real distribution, not a count. */
    val settlementSizes: List<SettlementSizeType> = emptyList(),
    /** The kingdom's fame cap; 0 means "unknown", and the fame-max detector stays quiet. */
    val fameMax: Int = 0,
    /** All four ruin tracks summed: the ruin-free deed cares that every track is clear. */
    val ruinTotal: Int = 0,
    /** Past turns, oldest first. Deeds that need "having once been worse" read this, not live state. */
    val history: List<DeedHistoryPoint> = emptyList(),
)

/** The slice of a past turn the historical detectors need. */
data class DeedHistoryPoint(
    val turn: Int,
    val unrest: Int,
    val ruinTotal: Int,
)

/**
 * Ten consecutive turns in [history] with unrest never rising from one to the next.
 *
 * Reads the RECORDED history rather than a running counter: a counter would have to be stored,
 * migrated and kept honest across undo, and history already holds the answer. Ten entries are the
 * minimum that can contain nine transitions, so shorter histories are false rather than vacuously
 * true -- the trap in "all pairs satisfy the predicate" over an empty window.
 */
fun hasTenQuietTurns(history: List<DeedHistoryPoint>, window: Int = 10): Boolean {
    if (history.size < window) return false
    val ordered = history.sortedBy { it.turn }
    return (0..ordered.size - window).any { start ->
        val slice = ordered.subList(start, start + window)
        slice.zipWithNext().all { (a, b) -> b.unrest <= a.unrest }
    }
}

/** See the file KDoc: level-triggered on standing state, pure, and never self-idempotent. */
fun interface DeedDetector {
    fun firesFor(inputs: DeedInputs): Boolean
}

/**
 * detectionId -> detector: the single place a deed's catalog entry is wired to code.
 *
 * Phase 1 holds the two detectors retrofitted from the shipped `MilestoneOffers.kt` pair; the
 * remaining catalog lands in phase 2. An id absent from this map is a catalog newer than the
 * build and is SKIPPED by [undetectedDeeds], never thrown — one unrecognised entry must not take
 * down every other deed on End Turn.
 */
val deedDetectors: Map<String, DeedDetector> = mapOf(
    // settlements
    "first-settlement" to DeedDetector { it.settlementSizes.isNotEmpty() },
    "first-town" to DeedDetector { inputs -> inputs.settlementSizes.any { it >= SettlementSizeType.TOWN } },
    "first-city" to DeedDetector { inputs -> inputs.settlementSizes.any { it >= SettlementSizeType.CITY } },
    "first-metropolis" to DeedDetector { inputs -> inputs.settlementSizes.any { it >= SettlementSizeType.METROPOLIS } },
    // roads and regions
    "road-to-capital" to DeedDetector { it.settlementsRoadedToCapital >= 1 },
    "all-settlements-roaded" to DeedDetector {
        // three is the plan's floor: "every settlement is linked" is no achievement at one
        it.settlementSizes.size >= 3 && it.settlementsRoadedToCapital == it.settlementSizes.size
    },
    "region-fully-claimed" to DeedDetector { it.regionsFullyClaimed >= 1 },
    "two-regions-claimed" to DeedDetector { it.regionsFullyClaimed >= 2 },
    // scale
    "size-25" to DeedDetector { it.size >= 25 },
    "size-50" to DeedDetector { it.size >= 50 },
    "size-100" to DeedDetector { it.size >= 100 },
    "level-10" to DeedDetector { it.level >= 10 },
    "level-20" to DeedDetector { it.level >= 20 },
    "fame-max" to DeedDetector { it.fameMax > 0 && it.fame >= it.fameMax },
    // recovery: each needs evidence in history that things were once worse, so a kingdom that
    // never suffered cannot claim the deed for never having suffered
    "ruin-free" to DeedDetector { inputs ->
        inputs.ruinTotal == 0 && inputs.history.any { it.ruinTotal > 0 }
    },
    "unrest-zero-after-crisis" to DeedDetector { inputs ->
        inputs.unrest == 0 && inputs.history.any { it.unrest >= 10 }
    },
    "ten-quiet-turns" to DeedDetector { hasTenQuietTurns(it.history) },
    // commerce and war
    "safe-trade-route" to DeedDetector { it.consecutiveSafeCaravanTurns >= 5 },
    "first-battle-won" to DeedDetector { it.armiesWon >= 1 },
    "three-trade-agreements" to DeedDetector { it.tradeAgreements >= 3 },
    "survived-fifty-turns" to DeedDetector { it.turn >= 50 },
)

/** A catalog row as the detector layer sees it: a milestone id plus its optional detection wiring. */
data class DeedCatalogEntry(
    val id: String,
    val detectionId: String? = null,
)

/**
 * The deeds to offer this turn: catalog entries that carry a KNOWN detectionId, whose detector
 * fires for [inputs], and whose offer the GM has not already answered either way. Catalog order is
 * preserved so the digest renders deterministically.
 */
fun undetectedDeeds(
    catalog: List<DeedCatalogEntry>,
    answeredIds: Set<String>,
    inputs: DeedInputs,
): List<String> =
    catalog
        .filter { it.id !in answeredIds }
        .filter { entry -> entry.detectionId?.let { deedDetectors[it]?.firesFor(inputs) } == true }
        .map { it.id }
