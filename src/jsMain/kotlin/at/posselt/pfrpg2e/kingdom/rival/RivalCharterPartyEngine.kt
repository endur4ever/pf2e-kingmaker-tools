package at.posselt.pfrpg2e.kingdom.rival

import at.posselt.pfrpg2e.companion.formatHexKeyLabel
import at.posselt.pfrpg2e.data.hex.HexContentType
import at.posselt.pfrpg2e.data.kingdom.HexCube
import at.posselt.pfrpg2e.data.kingdom.RIVAL_HEADLINE_IDLE
import at.posselt.pfrpg2e.data.kingdom.RIVAL_KIND_CONTESTED_CLAIM
import at.posselt.pfrpg2e.data.kingdom.RIVAL_KIND_LANDMARK
import at.posselt.pfrpg2e.data.kingdom.RIVAL_KIND_UNCLEARED_LAIR
import at.posselt.pfrpg2e.data.kingdom.RIVAL_KIND_UNEXPLORED
import at.posselt.pfrpg2e.data.kingdom.RivalMapSnapshot
import at.posselt.pfrpg2e.data.kingdom.RivalTarget
import at.posselt.pfrpg2e.data.kingdom.advanceRival
import at.posselt.pfrpg2e.data.kingdom.headlineKey
import at.posselt.pfrpg2e.data.kingdom.headlinePoolSize
import at.posselt.pfrpg2e.data.kingdom.headlineTemplateIndex
import at.posselt.pfrpg2e.data.kingdom.rivalTargetValue
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.RawHexContent
import at.posselt.pfrpg2e.kingdom.data.RawRivalCharterParty
import at.posselt.pfrpg2e.kingdom.data.isActive
import at.posselt.pfrpg2e.kingdom.data.toModel
import at.posselt.pfrpg2e.kingdom.data.toRaw
import com.foundryvtt.kingmaker.HexState
import com.foundryvtt.kingmaker.kingmaker
import com.foundryvtt.kingmaker.isExplored
import at.posselt.pfrpg2e.utils.toMap

/**
 * jsMain engine for the Rival Charter Party (plan section 3.4 / 3.5): the one impure map read,
 * taken once per tick, and the per-band adapter around the pure `advanceRival`.
 *
 * Everything the pure core decides is deterministic over (state, snapshot, paused, turn); this
 * layer only supplies the snapshot, stamps the idempotency fields, and turns moves into headline
 * keys and offer rows. It never writes the map: arriving somewhere is a GM-confirmed offer.
 */

/** One region hex as the classifier sees it -- plain data so the classifier is testable. */
data class RegionHexInfo(val key: String, val name: String?, val cube: HexCube)

/**
 * The classification rules of plan section 3.1, over plain data.
 *
 * A hex matching more than one rule takes the highest-value kind. Claimed hexes are never prizes,
 * so the players win a race simply by claiming the ground: the band re-targets and no
 * "got there first" offer can fire for it.
 */
fun classifyRivalTargets(
    regionHexes: List<RegionHexInfo>,
    stateByKey: Map<String, HexState>,
    hexContents: List<RawHexContent>,
): RivalMapSnapshot {
    val landmarkKeys = hexContents.filter {
        it.type == HexContentType.LANDMARK.value || it.type == HexContentType.REFUGE.value
    }.map { it.hexKey }.toSet()
    val lairKeys = hexContents.filter {
        it.type == HexContentType.RUIN.value || it.type == HexContentType.ENEMY_ARMY.value
    }.map { it.hexKey }.toSet()
    val cubeByKey = regionHexes.associate { it.key to it.cube }
    val claimedKeys = stateByKey.filter { (_, s) -> s.claimed == true }.keys
    val targets = linkedMapOf<String, RivalTarget>()
    for (hex in regionHexes) {
        val state = stateByKey[hex.key]
        if (state?.claimed == true) continue
        val kind = when {
            hex.key in landmarkKeys -> RIVAL_KIND_LANDMARK
            hex.key in lairKeys && state?.cleared != true -> RIVAL_KIND_UNCLEARED_LAIR
            state?.isExplored() == true -> RIVAL_KIND_CONTESTED_CLAIM
            else -> RIVAL_KIND_UNEXPLORED
        }
        val coord = formatHexKeyLabel(hex.key) ?: hex.key
        val label = hex.name?.takeIf { it.isNotBlank() && it != coord }?.let { "$it ($coord)" } ?: coord
        targets[hex.key] = RivalTarget(hexKey = hex.key, cube = hex.cube, kind = kind, value = rivalTargetValue(kind), label = label)
    }
    return RivalMapSnapshot(targetsByKey = targets, claimedKeys = claimedKeys, cubeByKey = cubeByKey)
}

/**
 * The single impure read (plan section 3.1). NULL -- not an empty snapshot -- when the map cannot
 * be read: pf2e-kingmaker disabled for a session, or a region with no hexes. The tick copies every
 * band through untouched on null. An EMPTY snapshot is not the same thing: the core would see zero
 * candidates, drop the band's whole scripted agenda and clear its countdown, and the GM's script
 * would be gone for good the next time the module was enabled.
 */
fun buildRivalMapSnapshot(kingdom: KingdomData): RivalMapSnapshot? = runCatching {
    val region = kingmaker.region.hexes.contents.map { hex ->
        RegionHexInfo(key = hex.key.toString(), name = hex.name, cube = HexCube(hex.cube.q, hex.cube.r, hex.cube.s))
    }
    if (region.isEmpty()) return@runCatching null
    val state = kingmaker.state.hexes.toMap()
    classifyRivalTargets(region, state, (kingdom.hexContents ?: emptyArray()).toList())
}.getOrNull()

/** One band's turn, ready for the gazette and the offer digest. */
data class RivalPartyMove(
    val bandId: String,
    val bandName: String,
    val factionRef: String?,
    val headlineKey: String,
    val headlineData: Map<String, String>,
    /** Non-null: the band beat the players to this prize this turn (offer: reached target). */
    val arrivedAt: RivalTarget?,
    /** True only on the turn aggression crosses the threshold (offer: confrontation). */
    val confrontation: Boolean,
    /** Non-null: the band chose this NEW objective this turn and no rumor was planted for it yet. */
    val rumorTarget: RivalTarget?,
    /** The band's hex after the move, for the co-location check. */
    val currentHexKey: String?,
    val levelOffset: Int?,
    /** false = a band the GM hid: its headline stays out of the PLAYER gazette. */
    val visibleToPlayers: Boolean = true,
)

/**
 * Advance every band one kingdom turn (plan section 3.4).
 *
 * Inactive bands are copied through untouched and produce no move. Idle and paused bands keep
 * their record but emit no headline: the gazette is for movement. The idempotency stamps live
 * HERE, deterministically, so a re-run of the same turn (undo and redo) proposes nothing twice:
 * an arrival already stamped with this turn, a rumor already stamped for this objective, and a
 * confrontation already offered for this aggression peak are all skipped.
 */
fun advanceAllRivalParties(
    parties: Array<RawRivalCharterParty>,
    snapshot: RivalMapSnapshot,
    turn: Int,
): Pair<Array<RawRivalCharterParty>, List<RivalPartyMove>> {
    val moves = mutableListOf<RivalPartyMove>()
    val next = parties.map { band ->
        if (!band.isActive()) return@map band
        val state = band.toModel(snapshot.cubeByKey) ?: return@map band
        // only the GM's explicit pause is a kill-switch. A pace-0 band still stands on the
        // kingdom's doorstep and still provokes; it just never moves, so it gets no headline
        val paused = band.pauseMovement == true
        // prizes this band has already reached leave ITS pool; the map keeps them for everyone else
        val visited = (band.visitedHexKeys ?: emptyArray()).toSet()
        val ownSnapshot = if (visited.isEmpty()) snapshot else snapshot.copy(targetsByKey = snapshot.targetsByKey.filterKeys { it !in visited })
        val move = advanceRival(state, ownSnapshot, paused, turn)
        val objectiveTarget = move.newState.objectiveKey?.let { ownSnapshot.targetsByKey[it] }
        val raw = move.newState.toRaw(
            band,
            objectiveKind = objectiveTarget?.kind ?: band.objectiveKind?.takeIf { move.newState.objectiveKey == band.objectiveHexKey },
        )
        val arrived = move.arrivedAt
        val freshArrival = arrived != null && band.lastArrivalTurn != turn
        if (arrived != null && freshArrival) {
            raw.arrivals = (band.arrivals ?: 0) + 1
            raw.lastArrivalHexKey = arrived.hexKey
            raw.lastArrivalTurn = turn
            raw.visitedHexKeys = (visited + arrived.hexKey).toTypedArray()
        }
        val freshConfrontation = move.confrontation && band.confrontationOffered != true
        if (freshConfrontation) raw.confrontationOffered = true
        val rumorTarget = objectiveTarget?.takeIf { move.newObjective && band.rumoredObjectiveHexKey != it.hexKey }
        if (rumorTarget != null) raw.rumoredObjectiveHexKey = rumorTarget.hexKey

        // a band that cannot move must not announce progress: pace 0 keeps its objective and the
        // core still reports ADVANCE, but "3 turns from the Temple" every month is a lie. Its
        // OFFERS still post -- a parked band on the doorstep provokes -- with no gazette line.
        val announce = move.headlineKind != RIVAL_HEADLINE_IDLE && (band.pace ?: 1) > 0
        if (announce || freshArrival || freshConfrontation || rumorTarget != null) {
            val place = arrived?.label ?: objectiveTarget?.label ?: ""
            val pace = (band.pace ?: 1).coerceAtLeast(1)
            val turns = move.newState.distanceToObjective?.let { (it + pace - 1) / pace } ?: 0
            val index = headlineTemplateIndex(turn, band.id, move.headlineKind, headlinePoolSize(move.headlineKind))
            moves += RivalPartyMove(
                bandId = band.id,
                bandName = band.name,
                factionRef = band.factionRef,
                // empty = nothing to print; the gazette skips it and the digest ignores it
                headlineKey = if (announce) headlineKey(move.headlineKind, index) else "",
                headlineData = mapOf(
                    "band" to band.name,
                    "place" to place,
                    "faction" to (band.factionRef ?: band.name),
                    "turns" to turns.toString(),
                ),
                arrivedAt = if (freshArrival) arrived else null,
                confrontation = freshConfrontation,
                rumorTarget = rumorTarget,
                currentHexKey = move.newState.currentKey,
                levelOffset = band.levelOffset,
                visibleToPlayers = band.visibleToPlayers != false,
            )
        }
        raw
    }.toTypedArray()
    return next to moves
}
