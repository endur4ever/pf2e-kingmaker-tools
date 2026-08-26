package at.posselt.pfrpg2e.data.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Where every band in this suite stands unless a test says otherwise. */
private val ORIGIN = HexCube(0, 0, 0)

/**
 * A cube exactly [distance] hexes from [ORIGIN]. Everything is placed on one axis so a fixture
 * reads as the number the scoring formula uses, instead of three coordinates you have to solve.
 */
private fun cubeAt(distance: Int) = HexCube(distance, -distance, 0)

private fun rivalTarget(
    hexKey: String,
    distance: Int,
    kind: String = RIVAL_KIND_UNEXPLORED,
) = RivalTarget(
    hexKey = hexKey,
    cube = cubeAt(distance),
    kind = kind,
    value = rivalTargetValue(kind),
    label = "hex $hexKey",
)

private fun snapshotOf(
    vararg targets: RivalTarget,
    claimed: Map<String, HexCube> = emptyMap(),
) = RivalMapSnapshot(
    targetsByKey = targets.associateBy { it.hexKey },
    claimedKeys = claimed.keys,
    cubeByKey = targets.associate { it.hexKey to it.cube } + claimed,
)

private fun bandAt(
    objectiveKey: String? = null,
    distanceToObjective: Int? = null,
    agenda: List<String> = emptyList(),
    pace: Int = 1,
    aggression: Int = 0,
    aggressionThreshold: Int? = null,
    currentKey: String? = "0000",
    currentCube: HexCube? = ORIGIN,
) = RivalPartyState(
    currentKey = currentKey,
    currentCube = currentCube,
    objectiveKey = objectiveKey,
    distanceToObjective = distanceToObjective,
    agenda = agenda,
    pace = pace,
    aggression = aggression,
    aggressionThreshold = aggressionThreshold,
)

class RivalCharterPartyTest {

    // --- hexDistance ---

    @Test
    fun hexDistanceIsZeroForTheSameHexAndSymmetric() {
        val a = HexCube(3, -2, -1)
        val b = HexCube(-1, 2, -1)
        assertEquals(0, hexDistance(a, a))
        assertEquals(4, hexDistance(a, b))
        assertEquals(hexDistance(a, b), hexDistance(b, a))
    }

    @Test
    fun hexDistanceCountsOneStepPerNeighbour() {
        assertEquals(1, hexDistance(ORIGIN, HexCube(1, 0, -1)))
        assertEquals(2, hexDistance(ORIGIN, HexCube(2, -1, -1)))
        assertEquals(7, hexDistance(ORIGIN, cubeAt(7)))
    }

    // --- chooseObjective: the scalar score, not a lexicographic ordering ---

    @Test
    fun chooseObjectiveFartherLandmarkBeatsNearUnexplored() {
        val near = rivalTarget("1000", distance = 1)
        val landmark = rivalTarget("2000", distance = 3, kind = RIVAL_KIND_LANDMARK)
        val choice = assertNotNull(chooseObjective(ORIGIN, listOf(near, landmark)))
        // 40 - 5*3 = 25 beats 10 - 5*1 = 5. A (distance asc, value desc) ordering picks `near`.
        assertEquals(landmark, choice.target)
        assertEquals(3, choice.distance)
    }

    @Test
    fun chooseObjectiveDistanceStillWinsBeyondTheWeight() {
        val near = rivalTarget("1000", distance = 1)
        val landmark = rivalTarget("2000", distance = 8, kind = RIVAL_KIND_LANDMARK)
        val choice = assertNotNull(chooseObjective(ORIGIN, listOf(near, landmark)))
        // 40 - 5*8 = 0 loses to 10 - 5*1 = 5: distance is priced, not ignored.
        assertEquals(near, choice.target)
        assertEquals(1, choice.distance)
    }

    @Test
    fun chooseObjectivePricesDistanceExactlyAtTheWeight() {
        val near = rivalTarget("1000", distance = 1)
        val landmarkAtSix = rivalTarget("2000", distance = 6, kind = RIVAL_KIND_LANDMARK)
        val landmarkAtSeven = rivalTarget("2000", distance = 7, kind = RIVAL_KIND_LANDMARK)
        // Five hexes farther than the empty hex, the landmark still wins: 40 - 30 = 10 over 5.
        assertEquals(landmarkAtSix, assertNotNull(chooseObjective(ORIGIN, listOf(near, landmarkAtSix))).target)
        // One hex more and the scores tie at 5 exactly, so the lexicographic key tie-break
        // decides and the lower key ("1000") takes it. This pair pins RIVAL_DISTANCE_WEIGHT: at
        // 4 the landmark would still win at seven, at 6 it would already lose at six.
        assertEquals(near, assertNotNull(chooseObjective(ORIGIN, listOf(near, landmarkAtSeven))).target)
    }

    @Test
    fun chooseObjectiveTieBreaksOnLowestHexKeyRegardlessOfInputOrder() {
        val low = rivalTarget("1000", distance = 2)
        val high = rivalTarget("2000", distance = 2)
        assertEquals(low, assertNotNull(chooseObjective(ORIGIN, listOf(low, high))).target)
        assertEquals(low, assertNotNull(chooseObjective(ORIGIN, listOf(high, low))).target)
    }

    // --- chooseObjective: the GM's script comes first ---

    @Test
    fun chooseObjectiveAgendaHeadWinsOutright() {
        val scripted = rivalTarget("3000", distance = 9)
        val obviousPrize = rivalTarget("1000", distance = 1, kind = RIVAL_KIND_LANDMARK)
        val choice = assertNotNull(chooseObjective(ORIGIN, listOf(obviousPrize, scripted), listOf("3000")))
        // Score would have picked the landmark at 35 over the scripted hex at -35.
        assertEquals(scripted, choice.target)
        assertEquals(9, choice.distance)
    }

    @Test
    fun chooseObjectiveSkipsAgendaKeysThatAreNoLongerCandidates() {
        val scripted = rivalTarget("3000", distance = 9)
        val obviousPrize = rivalTarget("1000", distance = 1, kind = RIVAL_KIND_LANDMARK)
        val choice = assertNotNull(
            chooseObjective(ORIGIN, listOf(obviousPrize, scripted), listOf("9999", "3000")),
        )
        assertEquals(scripted, choice.target)
    }

    @Test
    fun chooseObjectiveFallsBackToScoringWhenNoQueuedKeySurvives() {
        val far = rivalTarget("3000", distance = 9)
        val prize = rivalTarget("1000", distance = 1, kind = RIVAL_KIND_LANDMARK)
        val choice = assertNotNull(chooseObjective(ORIGIN, listOf(prize, far), listOf("9999")))
        assertEquals(prize, choice.target)
    }

    @Test
    fun chooseObjectiveReturnsNullWithoutPositionOrCandidates() {
        val prize = rivalTarget("1000", distance = 1)
        assertNull(chooseObjective(null, listOf(prize)))
        assertNull(chooseObjective(ORIGIN, emptyList()))
        assertNull(chooseObjective(ORIGIN, emptyList(), listOf("1000")))
    }

    // --- advanceRival: the countdown IS the progress ---

    @Test
    fun advanceRivalCountsDownDistanceWithoutMoving() {
        val objective = rivalTarget("2000", distance = 5)
        val move = advanceRival(
            bandAt(objectiveKey = "2000", distanceToObjective = 5, pace = 2),
            snapshotOf(objective),
            paused = false,
            turn = 7,
        )
        assertEquals(3, move.newState.distanceToObjective)
        assertEquals("0000", move.newState.currentKey)
        assertEquals(ORIGIN, move.newState.currentCube)
        assertEquals("2000", move.newState.objectiveKey)
        assertEquals("0000", move.movedFrom)
        assertNull(move.arrivedAt)
        assertFalse(move.newObjective)
        assertEquals(RIVAL_HEADLINE_ADVANCE, move.headlineKind)
    }

    @Test
    fun advanceRivalRepeatedTurnsReachZeroThenArrive() {
        val objective = rivalTarget("2000", distance = 5)
        val snapshot = snapshotOf(objective)
        val first = advanceRival(
            bandAt(objectiveKey = "2000", distanceToObjective = 5, pace = 2),
            snapshot,
            paused = false,
            turn = 1,
        )
        assertEquals(3, first.newState.distanceToObjective)
        assertNull(first.arrivedAt)
        val second = advanceRival(first.newState, snapshot, paused = false, turn = 2)
        assertEquals(1, second.newState.distanceToObjective)
        assertNull(second.arrivedAt)
        val third = advanceRival(second.newState, snapshot, paused = false, turn = 3)
        assertEquals(objective, third.arrivedAt)
        assertEquals("2000", third.newState.currentKey)
        assertNull(third.newState.objectiveKey)
        assertNull(third.newState.distanceToObjective)
    }

    @Test
    fun advanceRivalArrivesWhenDistanceReachesZero() {
        val objective = rivalTarget("2000", distance = 2, kind = RIVAL_KIND_LANDMARK)
        val move = advanceRival(
            bandAt(objectiveKey = "2000", distanceToObjective = 2, pace = 2),
            snapshotOf(objective),
            paused = false,
            turn = 4,
        )
        assertEquals(objective, move.arrivedAt)
        assertEquals("2000", move.newState.currentKey)
        assertEquals(objective.cube, move.newState.currentCube)
        assertEquals("0000", move.movedFrom)
        assertNull(move.newState.objectiveKey)
        assertNull(move.newState.distanceToObjective)
        assertEquals(RIVAL_HEADLINE_ARRIVE_LANDMARK, move.headlineKind)
    }

    @Test
    fun advanceRivalNeverReArrivesOnTheHexItStandsOn() {
        // The rival never writes the map, so the prize it just took is STILL a candidate next
        // turn. Without the standing-hex guard it would re-target at distance 0 and "arrive"
        // again every single turn, inflating the tally and re-posting the offer forever.
        val objective = rivalTarget("2000", distance = 2)
        val snapshot = snapshotOf(objective)
        val arrival = advanceRival(
            bandAt(objectiveKey = "2000", distanceToObjective = 2, pace = 2),
            snapshot,
            paused = false,
            turn = 4,
        )
        assertEquals(objective, arrival.arrivedAt)
        val next = advanceRival(arrival.newState, snapshot, paused = false, turn = 5)
        assertNull(next.arrivedAt)
        assertNull(next.newState.objectiveKey)
        assertEquals("2000", next.newState.currentKey)
        assertEquals(RIVAL_HEADLINE_IDLE, next.headlineKind)
        assertEquals(arrival.newState.aggression, next.newState.aggression)
    }

    @Test
    fun advanceRivalReTargetsWhenPlayersTakeTheObjectiveFirst() {
        val replacement = rivalTarget("3000", distance = 4)
        val move = advanceRival(
            bandAt(objectiveKey = "2000", distanceToObjective = 3),
            snapshotOf(replacement),
            paused = false,
            turn = 9,
        )
        assertNull(move.arrivedAt)
        assertTrue(move.newObjective)
        assertEquals("3000", move.newState.objectiveKey)
        // Choosing consumes the turn: the countdown starts at the full distance, undecremented.
        assertEquals(4, move.newState.distanceToObjective)
        assertEquals(RIVAL_HEADLINE_ADVANCE, move.headlineKind)
    }

    @Test
    fun advanceRivalRechoosesWhenTheCountdownWasLost() {
        val objective = rivalTarget("2000", distance = 4)
        val move = advanceRival(
            bandAt(objectiveKey = "2000", distanceToObjective = null),
            snapshotOf(objective),
            paused = false,
            turn = 1,
        )
        // A half-written record must re-initialise, never read as "distance 0, therefore arrived".
        assertNull(move.arrivedAt)
        assertTrue(move.newObjective)
        assertEquals(4, move.newState.distanceToObjective)
    }

    @Test
    fun advanceRivalPausedChangesNothing() {
        val objective = rivalTarget("2000", distance = 3)
        // The claimed hex sits right under the band, so a pause guard applied after the
        // aggression step would tick this band to its threshold from the sidelines.
        val snapshot = snapshotOf(objective, claimed = mapOf("0000" to ORIGIN))
        val state = bandAt(
            objectiveKey = "2000",
            distanceToObjective = 3,
            aggression = 4,
            aggressionThreshold = 5,
        )
        val move = advanceRival(state, snapshot, paused = true, turn = 2)
        assertEquals(state, move.newState)
        assertEquals(4, move.newState.aggression)
        assertFalse(move.confrontation)
        assertFalse(move.newObjective)
        assertNull(move.arrivedAt)
        assertEquals(RIVAL_HEADLINE_IDLE, move.headlineKind)
    }

    // --- advanceRival: aggression is arithmetic ---

    @Test
    fun advanceRivalAggressionRisesOnlyWithinTheProximityRadius() {
        val objective = rivalTarget("2000", distance = 6)
        val onTheEdge = snapshotOf(objective, claimed = mapOf("5000" to cubeAt(RIVAL_PROXIMITY_HEXES)))
        val justOutside = snapshotOf(objective, claimed = mapOf("5000" to cubeAt(RIVAL_PROXIMITY_HEXES + 1)))
        val inside = advanceRival(
            bandAt(objectiveKey = "2000", distanceToObjective = 6),
            onTheEdge,
            paused = false,
            turn = 1,
        )
        val outside = advanceRival(
            bandAt(objectiveKey = "2000", distanceToObjective = 6),
            justOutside,
            paused = false,
            turn = 1,
        )
        assertEquals(RIVAL_PROXIMITY_AGGRESSION, inside.newState.aggression)
        assertEquals(0, outside.newState.aggression)
        assertEquals(5, inside.newState.distanceToObjective)
    }

    @Test
    fun advanceRivalContestedArrivalCostsMoreAggressionThanAnyOther() {
        val contested = rivalTarget("2000", distance = 1, kind = RIVAL_KIND_CONTESTED_CLAIM)
        val landmark = rivalTarget("2000", distance = 1, kind = RIVAL_KIND_LANDMARK)
        val contestedMove = advanceRival(
            bandAt(objectiveKey = "2000", distanceToObjective = 1),
            snapshotOf(contested),
            paused = false,
            turn = 1,
        )
        val landmarkMove = advanceRival(
            bandAt(objectiveKey = "2000", distanceToObjective = 1),
            snapshotOf(landmark),
            paused = false,
            turn = 1,
        )
        assertEquals(RIVAL_ARRIVAL_AGGRESSION_CONTESTED, contestedMove.newState.aggression)
        assertEquals(RIVAL_ARRIVAL_AGGRESSION_OTHER, landmarkMove.newState.aggression)
        assertTrue(contestedMove.newState.aggression > landmarkMove.newState.aggression)
        assertEquals(RIVAL_HEADLINE_ARRIVE_CONTESTED, contestedMove.headlineKind)
    }

    @Test
    fun advanceRivalConfrontationFiresOnlyOnTheCrossingTurn() {
        // No targets at all: an idle band prowling claimed ground still escalates.
        val snapshot = snapshotOf(claimed = mapOf("5000" to cubeAt(1)))
        val crossing = advanceRival(
            bandAt(aggression = 4, aggressionThreshold = 5),
            snapshot,
            paused = false,
            turn = 1,
        )
        assertEquals(5, crossing.newState.aggression)
        assertTrue(crossing.confrontation)

        val past = advanceRival(crossing.newState, snapshot, paused = false, turn = 2)
        assertEquals(6, past.newState.aggression)
        assertFalse(past.confrontation)

        val disabled = advanceRival(
            bandAt(aggression = 4, aggressionThreshold = null),
            snapshot,
            paused = false,
            turn = 1,
        )
        assertEquals(5, disabled.newState.aggression)
        assertFalse(disabled.confrontation)
    }

    @Test
    fun advanceRivalIsDeterministicForTheSameInput() {
        val snapshot = snapshotOf(
            rivalTarget("1000", distance = 3, kind = RIVAL_KIND_LANDMARK),
            rivalTarget("2000", distance = 3, kind = RIVAL_KIND_LANDMARK),
            rivalTarget("3000", distance = 1),
            claimed = mapOf("5000" to cubeAt(2)),
        )
        val state = bandAt(aggression = 1, aggressionThreshold = 3, pace = 2)
        assertEquals(
            advanceRival(state, snapshot, paused = false, turn = 11),
            advanceRival(state, snapshot, paused = false, turn = 11),
        )
    }

    // --- advanceRival: the agenda queue ---

    @Test
    fun advanceRivalDropsAgendaKeysThePlayersTookFirst() {
        val live = rivalTarget("3000", distance = 4)
        val move = advanceRival(
            bandAt(agenda = listOf("9999", "3000")),
            snapshotOf(live),
            paused = false,
            turn = 1,
        )
        assertEquals(listOf("3000"), move.newState.agenda)
        assertEquals("3000", move.newState.objectiveKey)
        assertTrue(move.newObjective)
        // A scripted target the players took is a silent skip, never a "they got there first".
        assertNull(move.arrivedAt)
    }

    @Test
    fun advanceRivalDequeuesTheAgendaHeadOnArrival() {
        val scripted = rivalTarget("3000", distance = 1)
        val move = advanceRival(
            bandAt(objectiveKey = "3000", distanceToObjective = 1, agenda = listOf("3000", "4000")),
            snapshotOf(scripted),
            paused = false,
            turn = 1,
        )
        assertEquals(scripted, move.arrivedAt)
        assertEquals(listOf("4000"), move.newState.agenda)
    }

    @Test
    fun advanceRivalKeepsTheAgendaWhenArrivingSomewhereElse() {
        val scored = rivalTarget("3000", distance = 1)
        val queued = rivalTarget("4000", distance = 5)
        val move = advanceRival(
            bandAt(objectiveKey = "3000", distanceToObjective = 1, agenda = listOf("4000")),
            snapshotOf(scored, queued),
            paused = false,
            turn = 1,
        )
        assertEquals(scored, move.arrivedAt)
        assertEquals(listOf("4000"), move.newState.agenda)
    }

    // --- advanceRival: idling honestly ---

    @Test
    fun advanceRivalIdlesWithNothingLeftToChase() {
        val move = advanceRival(
            bandAt(objectiveKey = "2000", distanceToObjective = 3),
            RivalMapSnapshot(),
            paused = false,
            turn = 1,
        )
        assertNull(move.arrivedAt)
        assertNull(move.newState.objectiveKey)
        assertNull(move.newState.distanceToObjective)
        assertFalse(move.newObjective)
        assertEquals(RIVAL_HEADLINE_IDLE, move.headlineKind)
    }

    @Test
    fun advanceRivalIdlesWithoutAPosition() {
        val move = advanceRival(
            bandAt(currentKey = null, currentCube = null),
            snapshotOf(rivalTarget("1000", distance = 1, kind = RIVAL_KIND_LANDMARK)),
            paused = false,
            turn = 1,
        )
        assertNull(move.newState.objectiveKey)
        assertNull(move.arrivedAt)
        assertFalse(move.newObjective)
        assertEquals(RIVAL_HEADLINE_IDLE, move.headlineKind)
    }

    @Test
    fun advanceRivalHeadlineKindFollowsTheKindOfTheTargetReached() {
        val expected = mapOf(
            RIVAL_KIND_UNEXPLORED to RIVAL_HEADLINE_ARRIVE_UNEXPLORED,
            RIVAL_KIND_UNCLEARED_LAIR to RIVAL_HEADLINE_ARRIVE_LAIR,
            RIVAL_KIND_CONTESTED_CLAIM to RIVAL_HEADLINE_ARRIVE_CONTESTED,
            RIVAL_KIND_LANDMARK to RIVAL_HEADLINE_ARRIVE_LANDMARK,
        )
        for ((kind, headline) in expected) {
            val move = advanceRival(
                bandAt(objectiveKey = "2000", distanceToObjective = 1),
                snapshotOf(rivalTarget("2000", distance = 1, kind = kind)),
                paused = false,
                turn = 1,
            )
            assertEquals(headline, move.headlineKind, "arriving at a $kind")
        }
        // A kind this version has never heard of costs flavor, not the tick.
        val garbled = advanceRival(
            bandAt(objectiveKey = "2000", distanceToObjective = 1),
            snapshotOf(rivalTarget("2000", distance = 1, kind = "kindFromAFutureVersion")),
            paused = false,
            turn = 1,
        )
        assertEquals(RIVAL_HEADLINE_ARRIVE_UNEXPLORED, garbled.headlineKind)
        assertEquals(RIVAL_ARRIVAL_AGGRESSION_OTHER, garbled.newState.aggression)
    }

    // --- Lifecycle, cap, level curve ---

    @Test
    fun isRivalBandActiveCoversEveryLifecycleState() {
        assertTrue(isRivalBandActive(null))
        assertTrue(isRivalBandActive(RIVAL_STATUS_ACTIVE))
        assertTrue(isRivalBandActive(RIVAL_STATUS_DEFECTED))
        assertFalse(isRivalBandActive(RIVAL_STATUS_RETIRED))
        assertFalse(isRivalBandActive(RIVAL_STATUS_JOINED))
        // Unrecognised values stand still rather than being assumed active.
        assertFalse(isRivalBandActive("Active"))
        assertFalse(isRivalBandActive(""))
        assertFalse(isRivalBandActive("disbandedByTheKing"))
    }

    @Test
    fun activeRivalBandCountIgnoresArchivedRows() {
        assertEquals(0, activeRivalBandCount(emptyList()))
        assertEquals(
            3,
            activeRivalBandCount(
                listOf(
                    null,
                    RIVAL_STATUS_ACTIVE,
                    RIVAL_STATUS_DEFECTED,
                    RIVAL_STATUS_RETIRED,
                    RIVAL_STATUS_JOINED,
                    "disbandedByTheKing",
                ),
            ),
        )
        assertEquals(
            2,
            activeRivalBandCount(
                listOf(RIVAL_STATUS_ACTIVE, RIVAL_STATUS_ACTIVE, RIVAL_STATUS_RETIRED, RIVAL_STATUS_RETIRED),
            ),
        )
    }

    @Test
    fun maxRivalCharterPartiesIsTwo() {
        assertEquals(2, MAX_RIVAL_CHARTER_PARTIES)
    }

    @Test
    fun rivalEffectiveLevelClampsToTheLegalRange() {
        assertEquals(20, rivalEffectiveLevel(19, 4))
        assertEquals(1, rivalEffectiveLevel(2, -4))
        assertEquals(5, rivalEffectiveLevel(5, null))
        assertEquals(19, rivalEffectiveLevel(20, -1))
        assertEquals(20, rivalEffectiveLevel(20, 0))
        assertEquals(1, rivalEffectiveLevel(1, 0))
        assertEquals(4, rivalEffectiveLevel(1, 3))
    }

    @Test
    fun rivalTargetValueRanksTheFourKinds() {
        assertEquals(RIVAL_VALUE_LANDMARK, rivalTargetValue(RIVAL_KIND_LANDMARK))
        assertEquals(RIVAL_VALUE_UNCLEARED_LAIR, rivalTargetValue(RIVAL_KIND_UNCLEARED_LAIR))
        assertEquals(RIVAL_VALUE_CONTESTED_CLAIM, rivalTargetValue(RIVAL_KIND_CONTESTED_CLAIM))
        assertEquals(RIVAL_VALUE_UNEXPLORED, rivalTargetValue(RIVAL_KIND_UNEXPLORED))
        // An unknown kind is worth nothing, so it can never outrank a real prize.
        assertEquals(0, rivalTargetValue("kindFromAFutureVersion"))
        val ranked = listOf(
            RIVAL_KIND_UNEXPLORED,
            RIVAL_KIND_CONTESTED_CLAIM,
            RIVAL_KIND_UNCLEARED_LAIR,
            RIVAL_KIND_LANDMARK,
        ).map(::rivalTargetValue)
        assertEquals(ranked.sorted(), ranked, "weights must rank unexplored < contested < lair < landmark")
        assertEquals(ranked.size, ranked.distinct().size, "each kind needs its own weight")
    }

    // --- Headlines ---

    @Test
    fun headlineTemplateIndexIsDeterministicAndStaysInThePool() {
        val pool = headlinePoolSize(RIVAL_HEADLINE_ADVANCE)
        val seen = mutableSetOf<Int>()
        var sawNegativeRawHash = false
        for (turn in 0 until 200) {
            val index = headlineTemplateIndex(turn, "band-a", RIVAL_HEADLINE_ADVANCE, pool)
            assertTrue(index in 0 until pool, "index $index out of pool for turn $turn")
            assertEquals(index, headlineTemplateIndex(turn, "band-a", RIVAL_HEADLINE_ADVANCE, pool))
            seen += index
            // Re-derived only to prove the fixture reaches the overflowed-to-negative hashes the
            // normalisation exists for; the assertions above are what is under test.
            var raw = 17
            for (c in "$turn|band-a|$RIVAL_HEADLINE_ADVANCE") raw = raw * 31 + c.code
            if (raw < 0) sawNegativeRawHash = true
        }
        assertTrue(sawNegativeRawHash, "fixture never produced a negative intermediate hash")
        assertTrue(seen.size > 1, "a fixed index would make every advance headline read the same")
    }

    @Test
    fun headlineTemplateIndexDegradesForAnEmptyPool() {
        assertEquals(0, headlineTemplateIndex(3, "band-a", RIVAL_HEADLINE_ADVANCE, 0))
        assertEquals(0, headlineTemplateIndex(3, "band-a", RIVAL_HEADLINE_ADVANCE, -2))
        assertEquals(0, headlineTemplateIndex(3, "band-a", RIVAL_HEADLINE_IDLE, 1))
    }

    @Test
    fun headlineKeyCoversEveryTemplateInEveryPool() {
        assertEquals(3, headlinePoolSize(RIVAL_HEADLINE_ADVANCE))
        val advance = (0 until headlinePoolSize(RIVAL_HEADLINE_ADVANCE))
            .map { headlineKey(RIVAL_HEADLINE_ADVANCE, it) }
        assertEquals(
            listOf(
                "kingdom.rivalCharter.headline.advance1",
                "kingdom.rivalCharter.headline.advance2",
                "kingdom.rivalCharter.headline.advance3",
            ),
            advance,
        )
        val singles = mapOf(
            RIVAL_HEADLINE_IDLE to "kingdom.rivalCharter.headline.idle1",
            RIVAL_HEADLINE_ARRIVE_UNEXPLORED to "kingdom.rivalCharter.headline.arriveUnexplored1",
            RIVAL_HEADLINE_ARRIVE_LAIR to "kingdom.rivalCharter.headline.arriveLair1",
            RIVAL_HEADLINE_ARRIVE_CONTESTED to "kingdom.rivalCharter.headline.arriveContested1",
            RIVAL_HEADLINE_ARRIVE_LANDMARK to "kingdom.rivalCharter.headline.arriveLandmark1",
        )
        for ((kind, key) in singles) {
            assertEquals(1, headlinePoolSize(kind), "pool size for $kind")
            assertEquals(key, headlineKey(kind, 0), "key for $kind")
        }
        val allKeys = advance + singles.values
        assertEquals(allKeys.size, allKeys.toSet().size, "every template needs its own literal key")
    }

    @Test
    fun headlineKeyFallsBackForUnknownKindsAndIndices() {
        assertEquals("kingdom.rivalCharter.headline.advance3", headlineKey(RIVAL_HEADLINE_ADVANCE, 7))
        assertEquals("kingdom.rivalCharter.headline.arriveLair1", headlineKey(RIVAL_HEADLINE_ARRIVE_LAIR, 4))
        assertEquals("kingdom.rivalCharter.headline.idle1", headlineKey("kindFromAFutureVersion", 0))
        assertEquals(1, headlinePoolSize("kindFromAFutureVersion"))
    }
}
