package at.posselt.pfrpg2e.kingdom.digest

/**
 * Pure core of the "Meanwhile in the Stolen Lands" interlude digest
 * (`docs/plans/2026-07-09-plan-meanwhile-digest.md`, §3.1 scoring and §3.4 selection).
 *
 * Six off-screen feeds already post their own chat cards every turn; the digest exists to replace
 * that fragmented stream with ONE read-aloud beat. That makes volume discipline the governing rule
 * here: a hard beat cap, dedup against the previous turn's recap, and fully deterministic scoring
 * — no clock, no randomness — so the same kingdom state always tells the same story.
 *
 * The types the digest consumes at runtime (TickResult, the Raw flag interfaces, CaravanEvent) are
 * jsMain and cannot be referenced from commonMain, so [DigestEvent] is a pure mirror input: the
 * phase-2 jsMain adapters map each feed's own types into it before calling in.
 */

/**
 * One feed event, mirrored into pure data (the phase-2 jsMain adapters own the mapping).
 *
 * [id] is stable per development — the same caravan arrival reconsidered next turn carries the
 * same id — because dedup against the previous turn's beats keys on id, not on rendered text.
 * [kind] is the event-type tag ("factionDrift", "caravan", ...) and [sourceName] the entity behind
 * the event ("Pitax"); together they key per-source dedup so one caravan cannot produce two beats.
 * [magnitudeNorm] is normalized into 0..1 by the adapter using the per-feed formulas of §3.1, and
 * [relevance] uses the §3.1 tiers (1.0 for kingdom-owned assets down to 0.1 for background
 * flavor). [labelKey] and [labelArgs] reference an i18n template — prose stays template-based,
 * never generated (§6.2).
 */
data class DigestEvent(
    val id: String,
    val kind: String,
    val sourceName: String,
    val magnitudeNorm: Double,
    val relevance: Double,
    val labelKey: String,
    val labelArgs: Map<String, String> = emptyMap(),
)

/**
 * The §3.1 default weights: relevance above magnitude, because what involves the kingdom's own
 * assets outranks what is merely large — a big drift in a distant faction should lose its beat to
 * a raid on the party's caravan.
 */
data class DigestWeights(
    val magnitude: Double = 1.0,
    val relevance: Double = 1.5,
)

/**
 * How much this event deserves one of the digest's few beats.
 *
 * Out-of-range inputs are clamped, never trusted: adapters normalise into 0..1, but they are six
 * separately maintained mappings, and one bad adapter must not let a single runaway event drown
 * the whole digest.
 */
fun interestScore(event: DigestEvent, weights: DigestWeights = DigestWeights()): Double =
    event.magnitudeNorm.coerceIn(0.0, 1.0) * weights.magnitude +
        event.relevance.coerceIn(0.0, 1.0) * weights.relevance

/**
 * The hard beat budget (§1): the interlude is 2-4 beats of read-aloud prose, and past four beats
 * the digest becomes the very noise it was built to replace.
 */
const val MAX_DIGEST_BEATS = 4

/** An event with its score and input position pinned, so sorting cannot recompute or reorder. */
private data class ScoredEvent(val event: DigestEvent, val score: Double, val index: Int)

/**
 * Selects at most [cap] beats from [events], in the order the interlude should tell them.
 *
 * The pipeline runs in this order, and the order is load-bearing:
 * 1. Drop events whose id is in [previousBeatIds] — the dedup against the last-turn recap beat,
 *    so the two chat beats never repeat each other.
 * 2. Dedupe by kind plus sourceName, keeping the higher-scoring event so one caravan cannot
 *    produce two beats; an exact score tie keeps the earlier event, because input order is
 *    chronological.
 * 3. Order by [interestScore] descending, ties resolved by input order — determinism: no
 *    randomness anywhere, so the same state always yields the same digest.
 * 4. Take [cap]. A cap of zero or below returns an empty list (a disabled digest, not an error).
 */
fun selectDigestBeats(
    events: List<DigestEvent>,
    previousBeatIds: Set<String> = emptySet(),
    cap: Int = MAX_DIGEST_BEATS,
    weights: DigestWeights = DigestWeights(),
): List<DigestEvent> {
    if (cap <= 0) return emptyList()
    val scored = events.withIndex()
        .filter { (_, event) -> event.id !in previousBeatIds }
        .map { (index, event) -> ScoredEvent(event, interestScore(event, weights), index) }
    val bestPerSource = mutableMapOf<Pair<String, String>, ScoredEvent>()
    for (candidate in scored) {
        val key = candidate.event.kind to candidate.event.sourceName
        val kept = bestPerSource[key]
        if (kept == null || candidate.score > kept.score) {
            bestPerSource[key] = candidate
        }
    }
    return bestPerSource.values
        .sortedWith(compareByDescending<ScoredEvent> { it.score }.thenBy { it.index })
        .take(cap)
        .map { it.event }
}

/**
 * The §3.1 caravan magnitude: the share of the cargo a raid took, or an arrival delivered.
 *
 * [cargoAmount] is the PRE-tick cargo. The caravan advance returns a new caravan rather than
 * mutating, so the event site still sees the pre-raid figure — and that is the denominator a
 * "how bad was this" score needs: losing 2 of 10 is not losing 2 of 2. The max-with-1 guard makes
 * a zero or corrupt cargo size score 0-ish rather than divide by zero, and the final clamp keeps
 * corrupt numerators inside the 0..1 contract the weighting relies on.
 */
fun caravanMagnitude(
    cargoLost: Int,
    deliveredAmount: Int,
    cargoAmount: Int,
    raided: Boolean,
): Double {
    val moved = if (raided) cargoLost else deliveredAmount
    return (moved.toDouble() / maxOf(cargoAmount, 1)).coerceIn(0.0, 1.0)
}
