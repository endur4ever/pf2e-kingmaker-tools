package at.posselt.pfrpg2e.kingdom.data

import at.posselt.pfrpg2e.data.kingdom.HexCube
import at.posselt.pfrpg2e.data.kingdom.RivalPartyState
import at.posselt.pfrpg2e.data.kingdom.isRivalBandActive
import at.posselt.pfrpg2e.data.kingdom.rivalEffectiveLevel
import at.posselt.pfrpg2e.data.kingdom.rivalTargetValue
import kotlinx.js.JsPlainObject

/**
 * Persisted shape of one Rival Charter Party
 * (`docs/plans/2026-07-09-plan-rival-charter-party.md` SS2.1, SS2.4, SS2.6, SS5.2).
 *
 * A competing adventuring band exploring the map off-screen: the adversarial mirror of
 * [RawCompanionExpedition]. It moves on the MONTHLY kingdom tick (not the daily world clock), it
 * belongs to a RIVAL faction (not the PCs), and it competes over the SAME hex map the players
 * explore. Unlike an abstract rival-realm scoreboard row it has a real map POSITION and an
 * OBJECTIVE it walks toward.
 *
 * Honesty contract, restated here because this is the layer that could break it: movement is
 * deterministic (a cube-distance countdown; position only changes on arrival), there is no
 * simulated combat, no inventory, no pathfinding. Reaching a target is a GM-confirmed OFFER plus a
 * gazette line, never a silent `kingmaker.state` write, and a lifecycle change is a GM click, never
 * something the tick writes.
 *
 * Every field except [id] and [name] is nullable for migration safety, matching the plan's
 * declared shape: Migration68 seeds only the empty array, so any field this build adds later reads
 * as absent on an old row rather than as a wrong value. [id] and [name] cannot be absent because a
 * row only ever comes into existence through the add dialog, which supplies both.
 *
 * STRINGS at this boundary, never enums: [status] and [objectiveKind] are documented literal sets
 * whose authority is the commonMain core (`RIVAL_STATUS_*` / `RIVAL_KIND_*` in
 * `data/kingdom/RivalCharterParty.kt`), which deliberately declares them as constants rather than
 * enums. The "an unrecognised stored value drops the row from evaluation, never throws" contract is
 * therefore honoured by three whitelist readers rather than by `Enum.fromValue`: [toModel] returns
 * null for a status this build does not recognise, [objectiveKindOrNull] returns null for a kind it
 * does not recognise, and the core's `rivalTargetValue` floors an unknown kind at 0.
 */
@JsPlainObject
external interface RawRivalCharterParty {
    /** Stable id (UUID) so UI edits and offers target the right band. */
    var id: String

    /** Soft foreign key to RawGroup.name -- the faction that chartered this band (e.g. "Pitax").
     * The same by-name link the caravan and war-threat systems use, and just as forgiving: a
     * renamed or deleted group leaves the band alive with an "unlinked" hint. Nullable because an
     * unaffiliated band ("freebooters") is legal. */
    var factionRef: String?

    /** The band's own name for UI and gazette ("The Pitax Chartists"). */
    var name: String

    // --- Lifecycle (plan SS2.4) ---------------------------------------------------------------

    /** active | defected | retired | joined. null/absent reads as active (migration safety).
     * Only active and defected bands tick. Never written by the tick -- a lifecycle change is a
     * GM click. */
    var status: String?

    // --- Flavor (never mechanically simulated) ------------------------------------------------

    /** Free-text roster blurb ("Ganderel and four sellswords"). Display only. */
    var members: String?

    /** Level CURVE relative to the party, not an absolute level: a confrontation's encounter
     * budget is `rivalEffectiveLevel(partyLevel, levelOffset)`, clamped to 1..20. An absolute
     * level would go stale the moment the PCs level up. null/absent means an even match. */
    var levelOffset: Int?

    // --- Map position and objective (the whole point) -----------------------------------------

    /** Current hex key, a string key into `kingmaker.state.hexes`. null means off-map or not yet
     * placed. */
    var currentHexKey: String?

    /** GM-authored objective queue: ordered hex keys the band works through BEFORE automatic
     * scoring takes over. null or empty means fully automatic. Consumed head-first; a head key the
     * players took first is dropped silently with no arrival offer. */
    var agenda: Array<String>?

    /** The hex the band is currently walking toward. null means idle, recompute next tick. */
    var objectiveHexKey: String?

    /** Why this hex is valuable: unexplored | unclearedLair | contestedClaim | landmark. camelCase
     * deliberately, matching the core's `RIVAL_KIND_*` constants -- a hyphen would forever
     * foreclose promoting this field to a real enum. Drives the headline pool and the
     * got-there-first offer copy. */
    var objectiveKind: String?

    /** Hexes still to cover to reach [objectiveHexKey]. Set to the cube distance when the
     * objective is chosen and decremented by [pace] every turn. THIS countdown is the band's
     * progress, because [currentHexKey] only changes on arrival. */
    var distanceToObjective: Int?

    // --- GM movement dials (nullable, so absent means the documented default) ------------------

    /** Hexes advanced toward the objective per kingdom turn. null/absent means 1. */
    var pace: Int?

    /** GM kill-switch: true means the band does not move this turn, and does not accrue
     * proximity aggression from the sidelines either. */
    var pauseMovement: Boolean?

    // --- Aggression and confrontation escalation ----------------------------------------------

    /** Escalation clock, rising when the band contests the players: arriving at a target the PCs
     * wanted, or prowling near claimed territory. */
    var aggression: Int?

    /** When [aggression] reaches this, a confrontation OFFER fires. null disables confrontation
     * entirely rather than defaulting to a number the GM never chose. */
    var aggressionThreshold: Int?

    /** Idempotency: true once a confrontation offer has been posted for the current aggression
     * peak; reset when the aggression is spent. Read as `== true`, so null is "not yet". */
    var confrontationOffered: Boolean?

    // --- Scoreboard and idempotency guards ----------------------------------------------------

    /** How many targets this band has beaten the players to -- the "they got there first" tally.
     * Kept on retired and joined rows, because it is the scoreboard's memory of races the players
     * lost. */
    var arrivals: Int?

    /** Hex key of the last target the band reached, so the got-there-first offer fires once. */
    var lastArrivalHexKey: String?

    /** Turn the last arrival offer fired, for idempotency across preview/commit and reloads. */
    var lastArrivalTurn: Int?

    /** Guards the co-location encounter offer, one per band per turn: the band cannot leave its
     * hex mid-turn, so a turn stamp is the whole key. Read as `== turn`, so null is "not yet". */
    var lastEncounterOfferTurn: Int?

    /** Guards the rumor offer: one rumor per objective, so re-selecting the same objective after a
     * failed approach does not re-plant it. */
    var rumoredObjectiveHexKey: String?

    // --- Presentation -------------------------------------------------------------------------

    /** Player-board visibility (house rule). Nullable for migration safety; null and true both
     * mean visible. */
    var visibleToPlayers: Boolean?
}

/**
 * The pace an absent [RawRivalCharterParty.pace] means, matching [RivalPartyState]'s own default
 * (plan SS2.1). File-private on purpose: the dial belongs to the record's documented defaults, and
 * a second public copy of the number is exactly how a UI default and a tick default drift apart.
 */
private const val DEFAULT_PACE = 1

/** An absent [RawRivalCharterParty.aggression] is a band that has not provoked anyone yet. */
private const val DEFAULT_AGGRESSION = 0

/**
 * Whether this band still ticks, delegating the whitelist to the commonMain core so the board, the
 * cap check and the tick cannot disagree about what "active" means.
 */
fun RawRivalCharterParty.isActive(): Boolean = isRivalBandActive(status)

/**
 * The stored [RawRivalCharterParty.objectiveKind] if this build recognises it, else null.
 *
 * The string-boundary reader: a garbled or future-versioned kind yields null instead of throwing,
 * so the caller falls back to neutral copy. Recognition is "the core prices it above zero", which
 * keeps the kind table in exactly one place -- adding a fifth kind to the core makes it readable
 * here with no edit.
 */
fun RawRivalCharterParty.objectiveKindOrNull(): String? =
    objectiveKind?.takeIf { rivalTargetValue(it) > 0 }

/**
 * The encounter budget for confronting this band, given the party's current level.
 *
 * A convenience over the core's `rivalEffectiveLevel` so no caller has to remember that the stored
 * number is an offset rather than a level.
 */
fun RawRivalCharterParty.effectiveLevel(partyLevel: Int): Int =
    rivalEffectiveLevel(partyLevel, levelOffset)

/**
 * Extracts the movement state the pure core ticks, or null when this band must not tick at all.
 *
 * Null, not a throw and not a default-shaped state, for an unrecognised or non-ticking
 * [RawRivalCharterParty.status]: retired, joined and any value this build has never heard of all
 * drop the row from evaluation, which costs that one band its turn instead of taking down the
 * whole tick. The adapter copies a dropped row through unchanged -- no headline, no offer, no
 * aggression drift.
 *
 * [cubeByKey] is this turn's map snapshot lookup. A [RawRivalCharterParty.currentHexKey] that is
 * absent from it yields a null cube, which the core reads as "no position": the band chooses no
 * objective and stands still rather than measuring distance from a hex that is not on the map.
 */
fun RawRivalCharterParty.toModel(cubeByKey: Map<String, HexCube> = emptyMap()): RivalPartyState? {
    if (!isRivalBandActive(status)) return null
    return RivalPartyState(
        currentKey = currentHexKey,
        currentCube = currentHexKey?.let { cubeByKey[it] },
        objectiveKey = objectiveHexKey,
        distanceToObjective = distanceToObjective,
        agenda = agenda?.toList() ?: emptyList(),
        pace = pace ?: DEFAULT_PACE,
        aggression = aggression ?: DEFAULT_AGGRESSION,
        aggressionThreshold = aggressionThreshold,
    )
}

/**
 * Writes a ticked [RivalPartyState] back onto the record it came from, leaving every other field
 * untouched.
 *
 * Only the fields the core owns are written: identity, flavor, the GM dials, the offer guards and
 * the [RawRivalCharterParty.arrivals] tally stay exactly as [band] holds them, because those are
 * the adapter's and the GM's to change, not the movement math's.
 *
 * [objectiveKind] must be supplied whenever the band chose a NEW objective this turn -- the state
 * carries only the objective's key, since the core cannot see the map that says what a hex is
 * worth. The default keeps the stored kind only while the objective key is both non-null and
 * unchanged, so an idle band and a re-targeted band can never keep a kind that describes some
 * other hex.
 */
fun RivalPartyState.toRaw(
    band: RawRivalCharterParty,
    objectiveKind: String? = band.objectiveKind
        ?.takeIf { this.objectiveKey != null && this.objectiveKey == band.objectiveHexKey },
): RawRivalCharterParty =
    RawRivalCharterParty.copy(
        band,
        currentHexKey = currentKey,
        objectiveHexKey = objectiveKey,
        objectiveKind = objectiveKind,
        distanceToObjective = distanceToObjective,
        agenda = agenda.toTypedArray(),
        aggression = aggression,
    )
