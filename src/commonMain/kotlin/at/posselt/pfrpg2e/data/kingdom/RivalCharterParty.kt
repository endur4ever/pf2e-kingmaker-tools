package at.posselt.pfrpg2e.data.kingdom

import at.posselt.pfrpg2e.companion.hexCubeDistance

/**
 * Pure core of the Rival Charter Party: a competing adventuring band that walks the hex map
 * off-screen on the monthly kingdom tick (`docs/plans/2026-07-09-plan-rival-charter-party.md`,
 * phase 2 of section 8).
 *
 * Be honest about what this is: a scoreboard with teeth, not a simulated party. No pathfinding,
 * no terrain, no inventory, no combat resolver, and — the load-bearing part — no randomness
 * anywhere. [advanceRival] is a pure function of `(state, snapshot, paused, turn)`, which is a
 * stronger parity guarantee than a seeded RNG: the Turn Wizard preview, the forecast and the End
 * Turn commit all call it over the same tuple, so they cannot disagree about where the band is.
 * The single impure read of the live map lives in the jsMain snapshot builder, which hands this
 * core a plain-data [RivalMapSnapshot] once per tick.
 *
 * Equally load-bearing: nothing here writes anything, not even conceptually. Reaching a hex first
 * yields a [RivalMove] carrying an `arrivedAt` target; the jsMain layer turns that into a
 * GM-confirmed offer plus a gazette line. The rival never claims, explores or clears a hex, and
 * never changes its own lifecycle status — those are GM clicks, by design (plan sections 2.4
 * and 6.2).
 */

// --- Target kinds ------------------------------------------------------------------------

/**
 * Why a hex is worth walking toward. camelCase deliberately, matching the persisted
 * `objectiveKind` literal set: the codebase resolves enum constants from camelCase, so a hyphen
 * here would forever foreclose promoting the field to a real enum. Declared as constants rather
 * than left as loose literals so this core and the jsMain classifier that produces them cannot
 * drift by a typo — a mistyped kind is not a compile error, it is a band that silently scores
 * every prize at zero.
 */
const val RIVAL_KIND_UNEXPLORED = "unexplored"
const val RIVAL_KIND_UNCLEARED_LAIR = "unclearedLair"
const val RIVAL_KIND_CONTESTED_CLAIM = "contestedClaim"
const val RIVAL_KIND_LANDMARK = "landmark"

// --- Tunables (plan section 3.6) -----------------------------------------------------------
//
// Constants, not a JSON catalog: `data/` holds only per-item catalog directories that the build
// combines into arrays, which is the wrong shape for a key/value dial sheet — and keeping the
// dials here is what lets the whole core be unit-tested with zero build wiring. If per-world
// tuning is ever wanted the right home is a kingdom setting, with these as its defaults.

/** Prize weight per kind: a landmark is worth chasing past several closer, emptier hexes. */
const val RIVAL_VALUE_LANDMARK = 40
const val RIVAL_VALUE_UNCLEARED_LAIR = 30
const val RIVAL_VALUE_CONTESTED_CLAIM = 20
const val RIVAL_VALUE_UNEXPLORED = 10

/**
 * Hexes of distance that cost one point of target value in [chooseObjective]'s score. At the
 * weights above a landmark stays the better prize until it is more than 6 hexes farther than a
 * plain unexplored hex, and a lair until more than 4 — the dial that decides whether the band
 * reads as chasing interesting prizes or as hoovering up whatever is nearest. Gregory's number
 * to tune (plan open question 1); everything else about the ordering is settled.
 */
const val RIVAL_DISTANCE_WEIGHT = 5

/**
 * Aggression gained by arriving somewhere. A contested claim costs more because it is ground the
 * players had already explored and were plainly going to take, which is the arrival most likely
 * to end in a confrontation.
 */
const val RIVAL_ARRIVAL_AGGRESSION_CONTESTED = 2
const val RIVAL_ARRIVAL_AGGRESSION_OTHER = 1

/** Radius, in hexes, at which the band counts as prowling the kingdom's claimed territory. */
const val RIVAL_PROXIMITY_HEXES = 2

/** Aggression gained per turn spent inside [RIVAL_PROXIMITY_HEXES] of a claimed hex. */
const val RIVAL_PROXIMITY_AGGRESSION = 1

/**
 * How many bands may be in the field at once (plan section 2.5). Two, for two independent
 * reasons: each band adds a full scoring pass to the heaviest click in the module, and each
 * moving band claims a public gazette headline plus up to two offers per turn. At three the End
 * Turn recap stops being the players' turn and becomes a rival newsletter, which is the opposite
 * of the occasional sting this feature exists to deliver. Enforced on the add path only — an
 * edit of an existing band is always allowed, so a world that somehow holds more can still be
 * fixed rather than frozen.
 */
const val MAX_RIVAL_CHARTER_PARTIES = 2

/**
 * The prize weight for a target [kind], so the kind-to-value table lives in exactly one place
 * instead of being retyped by the jsMain classifier that builds [RivalTarget]s.
 *
 * An unrecognised kind scores 0 rather than the lowest real weight: a garbled or
 * future-versioned stored kind must never be able to outrank a real landmark, but it should
 * still be reachable when it is the only candidate left, so this floors the score instead of
 * dropping the target.
 */
fun rivalTargetValue(kind: String): Int = when (kind) {
    RIVAL_KIND_LANDMARK -> RIVAL_VALUE_LANDMARK
    RIVAL_KIND_UNCLEARED_LAIR -> RIVAL_VALUE_UNCLEARED_LAIR
    RIVAL_KIND_CONTESTED_CLAIM -> RIVAL_VALUE_CONTESTED_CLAIM
    RIVAL_KIND_UNEXPLORED -> RIVAL_VALUE_UNEXPLORED
    else -> 0
}

// --- Lifecycle (plan section 2.4) ----------------------------------------------------------

/**
 * The four lifecycle literals a band's stored `status` may hold. A bare string set rather than an
 * enum, matching the sibling off-screen-party record whose `status`/`tier`/`lootTier` are all
 * documented literal sets — the persisted shape is a nullable JS string, and an enum here would
 * only add a conversion that must never throw anyway.
 */
const val RIVAL_STATUS_ACTIVE = "active"
const val RIVAL_STATUS_DEFECTED = "defected"
const val RIVAL_STATUS_RETIRED = "retired"
const val RIVAL_STATUS_JOINED = "joined"

/**
 * Whether a band still ticks.
 *
 * Whitelist, not a blacklist: `null` (never migrated) and `"active"` are the same thing, a
 * defected band keeps walking for its new patron, and anything else — retired, joined, or a
 * value this version has never heard of — stands still. An unrecognised status must cost that
 * one band its turn, never take down the tick, so this cannot throw and has no `else` that
 * guesses "probably active".
 */
fun isRivalBandActive(status: String?): Boolean =
    status == null || status == RIVAL_STATUS_ACTIVE || status == RIVAL_STATUS_DEFECTED

/**
 * Bands counting against [MAX_RIVAL_CHARTER_PARTIES].
 *
 * Retired and joined rows are archive, not competitors: their `arrivals` tally is the
 * scoreboard's memory of races the players lost, so they stay on the board greyed out rather
 * than being deleted — and they must not consume a slot the GM wants for a live rival.
 */
fun activeRivalBandCount(statuses: List<String?>): Int = statuses.count(::isRivalBandActive)

/**
 * The encounter budget for confronting a band: the party's level shifted by the band's curve,
 * clamped to the legal PF2e level range.
 *
 * The record stores an OFFSET, never an absolute level, because a band's fighting weight only
 * means anything relative to the party — an absolute level goes stale the moment the PCs level
 * up, and a GM would have to re-edit every band every few sessions to keep "slightly harder than
 * even" true. A null offset is an even match.
 */
fun rivalEffectiveLevel(partyLevel: Int, levelOffset: Int?): Int =
    (partyLevel + (levelOffset ?: 0)).coerceIn(1, 20)

// --- Map + band state ----------------------------------------------------------------------

/** Cube coordinates of one hex, `q + r + s == 0`, as the native region model exposes them. */
data class HexCube(val q: Int, val r: Int, val s: Int)

/**
 * Cube distance between two hexes, delegating to the expedition travel math so the rival and the
 * players' own off-screen parties can never measure the same map differently.
 */
fun hexDistance(a: HexCube, b: HexCube): Int = hexCubeDistance(a.q, a.r, a.s, b.q, b.r, b.s)

/**
 * One candidate prize the band could walk toward.
 *
 * [value] is carried on the target rather than recomputed from [kind] on every comparison so the
 * jsMain classifier stays the single authority on what a hex is worth (it is the layer that can
 * see the map); [rivalTargetValue] is the table it should build them from. [label] is resolved at
 * snapshot time because headlines are written from plain data — this core can no more read a hex
 * name than it can read a die roll.
 */
data class RivalTarget(
    val hexKey: String,
    val cube: HexCube,
    val kind: String,
    val value: Int,
    val label: String,
)

/**
 * Everything the movement core needs to know about the map this turn, built once per tick.
 *
 * [targetsByKey] holds only prizes still available: a hex the players explored, claimed or
 * cleared is simply absent, which is exactly how the players win a race — the band re-targets and
 * no "they got there first" offer ever fires. [cubeByKey] covers the whole map, not just targets,
 * because proximity aggression measures against claimed hexes, which are by definition not
 * prizes.
 */
data class RivalMapSnapshot(
    val targetsByKey: Map<String, RivalTarget> = emptyMap(),
    val claimedKeys: Set<String> = emptySet(),
    val cubeByKey: Map<String, HexCube> = emptyMap(),
)

/**
 * Plain movement state for ONE band, extracted from the persisted record by the jsMain adapter.
 *
 * [distanceToObjective] is the band's progress, not [currentKey]: position changes only on
 * arrival (see [advanceRival]). [agenda] is the GM's scripted objective queue, consumed
 * head-first; empty means fully automatic. [aggressionThreshold] null disables confrontation
 * entirely rather than defaulting to some number the GM never chose.
 */
data class RivalPartyState(
    val currentKey: String?,
    val currentCube: HexCube?,
    val objectiveKey: String? = null,
    val distanceToObjective: Int? = null,
    val agenda: List<String> = emptyList(),
    val pace: Int = 1,
    val aggression: Int = 0,
    val aggressionThreshold: Int? = null,
)

/** The band's chosen target plus the countdown [advanceRival] should start from. */
data class ObjectiveChoice(val target: RivalTarget, val distance: Int)

/**
 * Picks the objective: the GM's script first, then the best score.
 *
 * While [agenda] is non-empty its head key wins outright, provided that key is still among
 * [candidates]; a head the players already took is dropped and the next key tried in the same
 * call, so a scripted band never stalls on a prize that no longer exists. With no usable queued
 * key, the winner is the argmax of a SINGLE SCALAR SCORE:
 *
 *     score = target.value - RIVAL_DISTANCE_WEIGHT * hexDistance(currentCube, target.cube)
 *
 * ties broken by the lowest [RivalTarget.hexKey] lexicographically. Deliberately NOT a
 * lexicographic `(distance asc, value desc, key asc)` ordering: with distance ranked first,
 * `value` could only ever fire on an exact distance tie, so the per-kind weights would be inert
 * and a landmark one hex farther than an empty hex could never win at any weighting. The scalar
 * prices distance instead of ranking it.
 *
 * [candidates] is whatever the caller considers reachable prizes — [advanceRival] excludes the
 * hex the band is standing on before calling, which is why no filtering happens here.
 *
 * Fully deterministic: a stable argmax with an explicit tie-break, no RNG, no iteration-order
 * dependence. Returns null when the band has no position or nothing is left to chase (idle).
 */
fun chooseObjective(
    currentCube: HexCube?,
    candidates: Collection<RivalTarget>,
    agenda: List<String> = emptyList(),
): ObjectiveChoice? {
    if (currentCube == null || candidates.isEmpty()) return null
    for (key in agenda) {
        val scripted = candidates.firstOrNull { it.hexKey == key } ?: continue
        return ObjectiveChoice(scripted, hexDistance(currentCube, scripted.cube))
    }
    var best: RivalTarget? = null
    var bestScore = 0
    var bestDistance = 0
    for (target in candidates) {
        val distance = hexDistance(currentCube, target.cube)
        val score = target.value - RIVAL_DISTANCE_WEIGHT * distance
        val incumbent = best
        if (incumbent == null || score > bestScore || (score == bestScore && target.hexKey < incumbent.hexKey)) {
            best = target
            bestScore = score
            bestDistance = distance
        }
    }
    return best?.let { ObjectiveChoice(it, bestDistance) }
}

// --- Headlines -----------------------------------------------------------------------------

/**
 * The headline pools a [RivalMove] can key into. The arrive variants are derived from the kind of
 * the target reached, so the gazette line for losing a landmark never reads like losing an empty
 * hex.
 */
const val RIVAL_HEADLINE_ADVANCE = "advance"
const val RIVAL_HEADLINE_IDLE = "idle"
const val RIVAL_HEADLINE_ARRIVE_UNEXPLORED = "arriveUnexplored"
const val RIVAL_HEADLINE_ARRIVE_LAIR = "arriveLair"
const val RIVAL_HEADLINE_ARRIVE_CONTESTED = "arriveContested"
const val RIVAL_HEADLINE_ARRIVE_LANDMARK = "arriveLandmark"

/**
 * An unknown kind degrades to the blandest arrival line rather than throwing: a stored kind this
 * version does not recognise should cost the table a little flavor, not the End Turn.
 */
private fun arrivalHeadlineKind(kind: String): String = when (kind) {
    RIVAL_KIND_LANDMARK -> RIVAL_HEADLINE_ARRIVE_LANDMARK
    RIVAL_KIND_UNCLEARED_LAIR -> RIVAL_HEADLINE_ARRIVE_LAIR
    RIVAL_KIND_CONTESTED_CLAIM -> RIVAL_HEADLINE_ARRIVE_CONTESTED
    else -> RIVAL_HEADLINE_ARRIVE_UNEXPLORED
}

/**
 * Which template of a headline pool a move uses — deterministic, so a preview and the commit that
 * follows it read the same sentence.
 *
 * A stable hash of `(turn, bandId, kind)` modulo [poolSize], normalised into `[0, poolSize)`
 * because the accumulation overflows into negative Ints on any realistic band id and a raw
 * remainder would then index backwards off the pool. A non-positive [poolSize] yields 0, so a
 * kind whose pool has not been written yet degrades to the first template instead of throwing
 * inside a tick.
 *
 * NAME OVERLOAD: the Rival Realms scoreboard declares a same-named function in this same package
 * (`RivalRealms.kt`) whose third parameter is a `RivalStat` enum and which hashes differently.
 * The parameter type is the only thing that tells the two apart, so a caller passing a bare
 * string always lands here — pass this feature's headline-kind constants, never a stat.
 */
fun headlineTemplateIndex(turn: Int, bandId: String, kind: String, poolSize: Int): Int {
    if (poolSize <= 0) return 0
    var h = 17
    for (c in "$turn|$bandId|$kind") h = h * 31 + c.code
    return ((h % poolSize) + poolSize) % poolSize
}

/**
 * How many templates each headline kind owns.
 *
 * Literal, not derived from the translation catalogs: the i18n guard cannot see a runtime-composed
 * key, so pool sizes and key strings live together here and are asserted in commonTest. Adding a
 * fourth advance line to the catalogs without widening both functions is then a failing test
 * rather than a GM noticing the same sentence twice.
 */
fun headlinePoolSize(headlineKind: String): Int = when (headlineKind) {
    RIVAL_HEADLINE_ADVANCE -> 3
    else -> 1
}

/**
 * Exhaustive kind-and-index to literal i18n key.
 *
 * No key is ever built by string concatenation — the catalog parity check scans for literals, and
 * a composed key is invisible to it, which is how a missing translation ships as a raw key in the
 * gazette. Out-of-range indices fall to the last template of the pool, and an unrecognised kind to
 * the idle line, so the gazette always has something printable.
 */
fun headlineKey(headlineKind: String, index: Int): String = when (headlineKind) {
    RIVAL_HEADLINE_ADVANCE -> when (index) {
        0 -> "kingdom.rivalCharter.headline.advance1"
        1 -> "kingdom.rivalCharter.headline.advance2"
        else -> "kingdom.rivalCharter.headline.advance3"
    }

    RIVAL_HEADLINE_ARRIVE_UNEXPLORED -> "kingdom.rivalCharter.headline.arriveUnexplored1"
    RIVAL_HEADLINE_ARRIVE_LAIR -> "kingdom.rivalCharter.headline.arriveLair1"
    RIVAL_HEADLINE_ARRIVE_CONTESTED -> "kingdom.rivalCharter.headline.arriveContested1"
    RIVAL_HEADLINE_ARRIVE_LANDMARK -> "kingdom.rivalCharter.headline.arriveLandmark1"
    else -> "kingdom.rivalCharter.headline.idle1"
}

// --- One turn ------------------------------------------------------------------------------

/**
 * What one turn's advance produced.
 *
 * [newState] is the band's state to persist; [arrivedAt] non-null means the band beat the players
 * to a prize this turn, which the adapter turns into a GM-confirmed offer — never a map write.
 * [movedFrom] is where the band stood when the turn began, which is what the advance headline
 * names ("last seen at X"). [newObjective] flags that a target was chosen this turn, the trigger
 * for the optional rumor offer. [confrontation] is true only on the turn aggression CROSSES the
 * threshold, so the escalation card is offered once rather than every turn past the line.
 */
data class RivalMove(
    val newState: RivalPartyState,
    val arrivedAt: RivalTarget?,
    val movedFrom: String?,
    val headlineKind: String,
    val newObjective: Boolean,
    val confrontation: Boolean,
)

/**
 * Advances ONE band one kingdom turn against [snapshot]. Deterministic; no RNG, no clock.
 *
 * The order of operations, and why each step is where it is:
 *
 *  1. `paused` returns immediately with the state untouched and an idle move. The GM kill-switch
 *     has to precede aggression too, or a paused band would keep escalating from the sidelines.
 *  2. Prizes are filtered to exclude the hex the band is STANDING on. This guard is load-bearing:
 *     the rival never writes the map, so an arrived-at hex stays a valid target forever, and
 *     without the filter the band would re-target it at distance 0 and "arrive" again every
 *     single turn — an endless arrivals tally and an endless offer.
 *  3. Dead heads are dropped from the [RivalPartyState.agenda]: a scripted key the players took
 *     first is skipped silently, with no arrival offer, and the next key tried the same turn.
 *  4. With no live objective (or a half-written record whose countdown was lost) a fresh one is
 *     chosen and the countdown initialised. Choosing consumes the turn; the countdown starts
 *     dropping the turn after, which is what makes the ETA the players read hold.
 *  5. Otherwise the countdown drops by `pace` and the POSITION DOES NOT CHANGE. The countdown is
 *     the progress; recomputing distance from an unchanged position would return the same number
 *     forever and the band would never arrive. A non-positive pace is a stand-still, never a
 *     retreat.
 *  6. Reaching zero snaps position to the objective, clears BOTH objective and countdown so a
 *     fresh one is chosen next turn, dequeues the agenda head when that is what was reached, and
 *     bumps aggression by the arrival weight for the kind.
 *  7. Prowling within [RIVAL_PROXIMITY_HEXES] of claimed territory adds aggression whether or not
 *     the band moved — sitting on the kingdom's doorstep is the provocation, not the walking.
 *
 * [turn] is not read today. It stays in the signature because the determinism contract is stated
 * over `(state, snapshot, paused, turn)` — the same tuple the adapter stamps arrival offers with
 * for idempotency — and because a turn-dependent rule must not be able to change this signature
 * later without every caller noticing.
 *
 * Inactive bands never reach here; the adapter filters them with [isRivalBandActive] first.
 */
@Suppress("UNUSED_PARAMETER")
fun advanceRival(
    state: RivalPartyState,
    snapshot: RivalMapSnapshot,
    paused: Boolean,
    turn: Int,
): RivalMove {
    if (paused) {
        return RivalMove(
            newState = state,
            arrivedAt = null,
            movedFrom = state.currentKey,
            headlineKind = RIVAL_HEADLINE_IDLE,
            newObjective = false,
            confrontation = false,
        )
    }
    val candidates = snapshot.targetsByKey.values.filter { it.hexKey != state.currentKey }
    val candidateKeys = candidates.mapTo(mutableSetOf()) { it.hexKey }
    val agenda = state.agenda.dropWhile { it !in candidateKeys }

    // An objective is usable only when the key is still a live prize AND a countdown exists: a
    // record carrying one without the other is corrupt (a partial write, a hand-edited flag) and
    // is re-chosen from scratch rather than counted down from an unknown number.
    val liveObjective = state.objectiveKey
        ?.takeIf { state.distanceToObjective != null }
        ?.let { key -> candidates.firstOrNull { it.hexKey == key } }

    var currentKey = state.currentKey
    var currentCube = state.currentCube
    var objectiveKey: String? = null
    var distance: Int? = null
    var agendaAfter = agenda
    var arrivedAt: RivalTarget? = null
    var newObjective = false
    var aggression = state.aggression

    if (liveObjective == null) {
        val choice = chooseObjective(state.currentCube, candidates, agenda)
        if (choice != null) {
            objectiveKey = choice.target.hexKey
            distance = choice.distance
            newObjective = true
        }
    } else {
        val remaining = (state.distanceToObjective ?: 0) - state.pace.coerceAtLeast(0)
        if (remaining <= 0) {
            arrivedAt = liveObjective
            currentKey = liveObjective.hexKey
            currentCube = liveObjective.cube
            if (agenda.firstOrNull() == liveObjective.hexKey) {
                agendaAfter = agenda.drop(1)
            }
            aggression += if (liveObjective.kind == RIVAL_KIND_CONTESTED_CLAIM) {
                RIVAL_ARRIVAL_AGGRESSION_CONTESTED
            } else {
                RIVAL_ARRIVAL_AGGRESSION_OTHER
            }
        } else {
            objectiveKey = liveObjective.hexKey
            distance = remaining
        }
    }

    val position = currentCube
    if (position != null && snapshot.claimedKeys.any { key ->
            val claimed = snapshot.cubeByKey[key]
            claimed != null && hexDistance(position, claimed) <= RIVAL_PROXIMITY_HEXES
        }
    ) {
        aggression += RIVAL_PROXIMITY_AGGRESSION
    }

    val threshold = state.aggressionThreshold
    return RivalMove(
        newState = state.copy(
            currentKey = currentKey,
            currentCube = currentCube,
            objectiveKey = objectiveKey,
            distanceToObjective = distance,
            agenda = agendaAfter,
            aggression = aggression,
        ),
        arrivedAt = arrivedAt,
        movedFrom = state.currentKey,
        headlineKind = when {
            arrivedAt != null -> arrivalHeadlineKind(arrivedAt.kind)
            objectiveKey != null -> RIVAL_HEADLINE_ADVANCE
            else -> RIVAL_HEADLINE_IDLE
        },
        newObjective = newObjective,
        confrontation = threshold != null && state.aggression < threshold && aggression >= threshold,
    )
}
