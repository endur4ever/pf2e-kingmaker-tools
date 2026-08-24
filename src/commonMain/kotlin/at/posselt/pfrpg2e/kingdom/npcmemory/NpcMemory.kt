package at.posselt.pfrpg2e.kingdom.npcmemory

/**
 * Pure core of the NPC memory ledger (`docs/plans/2026-07-09-plan-npc-memory.md`).
 *
 * Turn history stores per-turn **aggregate state, not discrete events** (§2 of the plan), so every
 * trigger here matches thresholds and deltas between two consecutive turn snapshots — never verbs.
 * There is no "the kingdom razed a forest" anywhere in the data to match; there is only "unrest was
 * 2 and is now 6". Anything that needs a verb stays out of scope until something records one (§2.3).
 */

/**
 * The trigger vocabulary the seventeen starter rules (§4) need — nothing more. Rules live in data
 * files, so an unknown stored trigger maps to null and that rule is skipped rather than crashing
 * the tick.
 */
enum class MemoryTriggerKind(val value: String) {
    UNREST_ROSE("unrestRose"),
    UNREST_CALMED("unrestCalmed"),
    RUIN_WORSENED("ruinWorsened"),
    RUINS_CLEARED("ruinsCleared"),
    SIZE_INCREASED("sizeIncreased"),
    LEVEL_INCREASED("levelIncreased"),
    FAME_AT_MAX("fameAtMax"),
    WAR_PRESSURE_ROSE("warPressureRose"),
    WAR_PRESSURE_RELIEVED("warPressureRelieved"),
    LEAN_YEAR("leanYear"),
    CLOCK_FIRED("clockFired"),
    SHIPMENT_OUTCOME("shipmentOutcome"),
    MILESTONE_EARNED("milestoneEarned");

    companion object {
        fun fromValue(value: String?): MemoryTriggerKind? = entries.find { it.value == value }
    }
}

/**
 * One turn's snapshot, flattened from the turn record plus the per-turn slices of shipment history
 * and milestones (§2.2), so rule evaluation never reaches into live kingdom state and stays pure.
 *
 * [warPressure] is nullable because turn records predate the war-pressure board; an absent value
 * reads as 0, so old history compares against new without a migration.
 */
data class KingdomTurnFacts(
    val turn: Int,
    val unrest: Int,
    val ruinCorruption: Int,
    val ruinCrime: Int,
    val ruinDecay: Int,
    val ruinStrife: Int,
    val size: Int,
    val level: Int,
    val fame: Int,
    val fameMax: Int,
    val warPressure: Int? = null,
    val consumption: Int,
    val resourcePoints: Int,
    val clockEventIds: Set<String> = emptySet(),
    val shipmentOutcomes: List<String> = emptyList(),
    val milestonesEarnedThisTurn: Int = 0,
)

/**
 * The in-memory shape of one JSON rule entry under `data/npc-memory-rules/` (§3.2).
 *
 * [ref] is trigger-dependent: a ruin track name for RUIN_WORSENED, a clock event id for
 * CLOCK_FIRED, an outcome string for SHIPMENT_OUTCOME. [deltas] keys on occupation names; anyone
 * absent from it gets [defaultDelta], so most residents are unmoved by most events — which is the
 * point. [cooldownTurns] exists so a war that raises pressure six straight turns does not write
 * six identical memories (§6).
 */
data class MemoryRule(
    val id: String,
    val trigger: MemoryTriggerKind,
    val threshold: Int = 0,
    val ref: String? = null,
    val deltas: Map<String, Int> = emptyMap(),
    val defaultDelta: Int = 0,
    val cooldownTurns: Int = 0,
)

private fun ruinTrack(facts: KingdomTurnFacts, ref: String?): Int? = when (ref) {
    "corruption" -> facts.ruinCorruption
    "crime" -> facts.ruinCrime
    "decay" -> facts.ruinDecay
    "strife" -> facts.ruinStrife
    else -> null
}

private fun allRuinsZero(facts: KingdomTurnFacts): Boolean =
    facts.ruinCorruption == 0 && facts.ruinCrime == 0 && facts.ruinDecay == 0 && facts.ruinStrife == 0

/**
 * Whether [rule] matches the transition from [prev] to [curr].
 *
 * Triggers fire on the transition, never the steady state: turn records are aggregates, and a
 * level test would re-fire on every turn the state merely persisted. FAME_AT_MAX is the sharpest
 * case — it fires once on reaching the cap, not for every turn spent sitting at it. LEAN_YEAR and
 * MILESTONE_EARNED read quantities that are already per-turn, where cooldown, not edge detection,
 * is what stops the repetition.
 *
 * A rule this engine cannot interpret returns false rather than throwing — an UNREST_ROSE
 * threshold below 1 (it would be satisfied by every flat turn), a RUIN_WORSENED ref naming no
 * known track, a missing ref on CLOCK_FIRED or SHIPMENT_OUTCOME. Rules are data files, and one
 * bad rule must not take down the whole tick.
 */
fun ruleFires(rule: MemoryRule, prev: KingdomTurnFacts, curr: KingdomTurnFacts): Boolean =
    when (rule.trigger) {
        MemoryTriggerKind.UNREST_ROSE ->
            rule.threshold >= 1 && curr.unrest - prev.unrest >= rule.threshold
        MemoryTriggerKind.UNREST_CALMED ->
            curr.unrest == 0 && prev.unrest >= rule.threshold
        MemoryTriggerKind.RUIN_WORSENED -> {
            val before = ruinTrack(prev, rule.ref)
            val after = ruinTrack(curr, rule.ref)
            before != null && after != null && after > before
        }
        MemoryTriggerKind.RUINS_CLEARED -> allRuinsZero(curr) && !allRuinsZero(prev)
        MemoryTriggerKind.SIZE_INCREASED -> curr.size > prev.size
        MemoryTriggerKind.LEVEL_INCREASED -> curr.level > prev.level
        MemoryTriggerKind.FAME_AT_MAX ->
            curr.fameMax > 0 && curr.fame >= curr.fameMax && prev.fame < prev.fameMax
        MemoryTriggerKind.WAR_PRESSURE_ROSE ->
            (curr.warPressure ?: 0) > (prev.warPressure ?: 0)
        MemoryTriggerKind.WAR_PRESSURE_RELIEVED ->
            (curr.warPressure ?: 0) == 0 && (prev.warPressure ?: 0) > 0
        MemoryTriggerKind.LEAN_YEAR -> curr.consumption > curr.resourcePoints
        MemoryTriggerKind.CLOCK_FIRED ->
            rule.ref != null && rule.ref in curr.clockEventIds
        MemoryTriggerKind.SHIPMENT_OUTCOME ->
            rule.ref != null && curr.shipmentOutcomes.contains(rule.ref)
        MemoryTriggerKind.MILESTONE_EARNED -> curr.milestonesEarnedThisTurn > 0
    }

/**
 * The attitude shift [rule] deals to an NPC of [occupation].
 *
 * Roster occupations are typed by a GM, so matching ignores case and padding rather than treating
 * "merchant " as a stranger to "Merchant". An occupation absent from the rule's deltas gets
 * [MemoryRule.defaultDelta]: most residents are unmoved by most events, which is the point (§3.2).
 */
fun deltaFor(rule: MemoryRule, occupation: String): Int {
    val wanted = occupation.trim()
    return rule.deltas.entries.firstOrNull { it.key.trim().equals(wanted, ignoreCase = true) }?.value
        ?: rule.defaultDelta
}

/**
 * Whether enough turns have passed since [lastFiredTurn] for a rule with [cooldownTurns] to fire
 * again.
 *
 * Cooldown is per rule **per NPC** (§6): the caller keeps a lastFired map for each NPC and passes
 * that NPC's entry here, so one NPC's cooldown never suppresses another's memory of the same
 * event. A rule that has never fired ([lastFiredTurn] null) is always allowed.
 */
fun cooldownAllows(lastFiredTurn: Int?, currentTurn: Int, cooldownTurns: Int): Boolean =
    lastFiredTurn == null || currentTurn - lastFiredTurn > cooldownTurns

/** One rule matching one turn for one NPC, with [delta] already resolved through their occupation. */
data class MemoryFiring(val rule: MemoryRule, val delta: Int)

/**
 * Every rule that fires for one NPC on the transition from [prev] to [curr].
 *
 * [lastFiredByRuleId] is **that NPC's** map (§6): cooldown is per rule per NPC, and passing a
 * shared map is exactly how one NPC's fresh memory would wrongly silence another's, so the map is
 * a parameter to make the per-NPC scoping the caller's visible obligation. Each rule fires at
 * most once per turn, even if it appears in [rules] twice.
 */
fun evaluateMemoryRules(
    rules: List<MemoryRule>,
    prev: KingdomTurnFacts,
    curr: KingdomTurnFacts,
    lastFiredByRuleId: Map<String, Int>,
    occupation: String,
): List<MemoryFiring> {
    val firings = mutableListOf<MemoryFiring>()
    val firedIds = mutableSetOf<String>()
    for (rule in rules) {
        if (rule.id in firedIds) continue
        if (!ruleFires(rule, prev, curr)) continue
        if (!cooldownAllows(lastFiredByRuleId[rule.id], curr.turn, rule.cooldownTurns)) continue
        firedIds.add(rule.id)
        firings.add(MemoryFiring(rule, deltaFor(rule, occupation)))
    }
    return firings
}

const val MIN_NPC_ATTITUDE = -50
const val MAX_NPC_ATTITUDE = 50

/**
 * Attitude lives on a hard −50…+50 scale (§5) so that however many memories accumulate, one
 * spectacular decade cannot push an opinion somewhere decay would take fifty years to walk back
 * from.
 */
fun clampAttitude(score: Int): Int = score.coerceIn(MIN_NPC_ATTITUDE, MAX_NPC_ATTITUDE)

/** The five bands of §5, stored by [value]; an unknown stored band maps to null, not a crash. */
enum class AttitudeBand(val value: String) {
    HOSTILE("hostile"),
    UNFRIENDLY("unfriendly"),
    INDIFFERENT("indifferent"),
    FRIENDLY("friendly"),
    HELPFUL("helpful");

    companion object {
        fun fromValue(value: String?): AttitudeBand? = entries.find { it.value == value }
    }
}

/**
 * The §5 band for a score. Band edges are what offers key off, so they are pinned here once
 * rather than re-derived in sheet and chat code that could drift apart.
 */
fun attitudeBand(score: Int): AttitudeBand = when {
    score <= -25 -> AttitudeBand.HOSTILE
    score <= -10 -> AttitudeBand.UNFRIENDLY
    score <= 9 -> AttitudeBand.INDIFFERENT
    score <= 24 -> AttitudeBand.FRIENDLY
    else -> AttitudeBand.HELPFUL
}

/**
 * One turn of fading, applied only when no memory formed this turn.
 *
 * Without decay a single bad decade fixes an NPC's opinion permanently; with it a grudge fades
 * unless renewed (§5). Decay never crosses 0 — it wears an opinion down to indifference, never
 * swings it to the other side — and 0 stays 0.
 */
fun decayAttitude(score: Int, formedMemoryThisTurn: Boolean): Int = when {
    formedMemoryThisTurn -> score
    score > 0 -> score - 1
    score < 0 -> score + 1
    else -> 0
}

/**
 * The band [newScore] entered, or null when it stayed inside [previousScore]'s band.
 *
 * Offers are edge-triggered on band **crossings**, not on scores (§7): an NPC sitting at hostile
 * must not re-offer every turn, so movement within a band — however large — reports nothing.
 */
fun crossedBand(previousScore: Int, newScore: Int): AttitudeBand? {
    val next = attitudeBand(newScore)
    return if (next == attitudeBand(previousScore)) null else next
}

const val MAX_TRACKED_NPCS = 10

/**
 * Whether the GM may opt one more NPC into memory tracking.
 *
 * Ten is a cast; forty is a spreadsheet (§6). Every tracked NPC is evaluated against every rule
 * every turn, so the cap bounds the tick's cost as much as the GM's attention.
 */
fun canTrackAnother(trackedCount: Int): Boolean = trackedCount < MAX_TRACKED_NPCS

const val MEMORY_LOG_CAP = 30

/**
 * One remembered event. [subject] carries interpolation values for the entry's i18n key — the
 * caravan partner's name, say. Entries are template text only, never generated prose (§9).
 */
data class NpcMemoryEntry(
    val ruleId: String,
    val turn: Int,
    val delta: Int,
    val subject: String? = null,
)

/**
 * Appends [entry], trimming oldest first past [cap].
 *
 * Returns the log ONLY: attitude is a running total that is deliberately never recomputed from
 * the log (§6), so trimming an old memory cannot silently revise an NPC's opinion. Handing an
 * attitude back from here would tempt a caller into exactly that recomputation.
 */
fun appendMemoryEntry(
    log: List<NpcMemoryEntry>,
    entry: NpcMemoryEntry,
    cap: Int = MEMORY_LOG_CAP,
): List<NpcMemoryEntry> {
    val all = log + entry
    return if (all.size <= cap) all else all.takeLast(cap)
}
