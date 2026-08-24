package at.posselt.pfrpg2e.kingdom.deeds

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
)

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
    "road-to-capital" to DeedDetector { it.settlementsRoadedToCapital >= 1 },
    "region-fully-claimed" to DeedDetector { it.regionsFullyClaimed >= 1 },
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
