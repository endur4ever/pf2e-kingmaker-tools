package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.HexCube
import at.posselt.pfrpg2e.data.kingdom.MAX_RIVAL_CHARTER_PARTIES
import at.posselt.pfrpg2e.data.kingdom.RIVAL_KIND_CONTESTED_CLAIM
import at.posselt.pfrpg2e.data.kingdom.RIVAL_KIND_LANDMARK
import at.posselt.pfrpg2e.data.kingdom.RIVAL_KIND_UNCLEARED_LAIR
import at.posselt.pfrpg2e.data.kingdom.RIVAL_KIND_UNEXPLORED
import at.posselt.pfrpg2e.data.kingdom.RIVAL_STATUS_RETIRED
import at.posselt.pfrpg2e.kingdom.data.RawHexContent
import at.posselt.pfrpg2e.kingdom.data.RawRivalCharterParty
import at.posselt.pfrpg2e.kingdom.rival.RegionHexInfo
import at.posselt.pfrpg2e.kingdom.rival.advanceAllRivalParties
import at.posselt.pfrpg2e.kingdom.rival.classifyRivalTargets
import at.posselt.pfrpg2e.kingdom.sheet.contexts.buildRivalCharterContext
import com.foundryvtt.kingmaker.HexState
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The ADAPTER around the rival core: the classifier's precedence, the stamps, and the blanking. */
class RivalCharterEngineTest {
    private fun hex(key: String, q: Int, name: String? = null) = RegionHexInfo(key, name, HexCube(q, -q, 0))
    private fun state(claimed: Boolean? = null, explored: Boolean? = null, cleared: Boolean? = null) =
        unsafeJso<dynamic> { this.claimed = claimed; this.explored = explored; this.cleared = cleared }.unsafeCast<HexState>()
    private fun content(hexKey: String, type: String) =
        unsafeJso<dynamic> { id = "c-$hexKey"; this.hexKey = hexKey; this.type = type; name = "x"; visibility = "discovered"; gmNotes = ""; playerText = "" }.unsafeCast<RawHexContent>()
    private fun band(
        id: String = "b1", currentHexKey: String? = "1001", status: String? = null,
        threshold: Int? = null, aggression: Int? = null, pace: Int? = null,
    ) = unsafeJso<dynamic> {
        this.id = id; name = "The Chartists"; this.status = status; this.currentHexKey = currentHexKey
        this.aggressionThreshold = threshold; this.aggression = aggression; this.pace = pace
    }.unsafeCast<RawRivalCharterParty>()

    private val region = listOf(hex("1001", 0), hex("1002", 1, "Temple"), hex("1003", 2), hex("1004", 3))

    @Test
    fun theClassifierTakesTheHighestValueKindAndNeverAClaimedHex() {
        val snapshot = classifyRivalTargets(
            regionHexes = region,
            stateByKey = mapOf("1002" to state(explored = true), "1003" to state(explored = true, cleared = false), "1004" to state(claimed = true)),
            hexContents = listOf(content("1002", "landmark"), content("1003", "ruin")),
        )
        assertEquals(RIVAL_KIND_LANDMARK, snapshot.targetsByKey["1002"]?.kind, "landmark outranks contested")
        assertEquals(RIVAL_KIND_UNCLEARED_LAIR, snapshot.targetsByKey["1003"]?.kind, "uncleared ruin outranks contested")
        assertEquals(RIVAL_KIND_UNEXPLORED, snapshot.targetsByKey["1001"]?.kind)
        assertNull(snapshot.targetsByKey["1004"], "a claimed hex is never a prize")
        assertTrue("1004" in snapshot.claimedKeys)
        assertEquals("Temple (${snapshot.targetsByKey["1002"]!!.label.substringAfter('(')}", snapshot.targetsByKey["1002"]!!.label)
    }

    @Test
    fun aClearedLairIsNoLongerALairAndExploredGroundIsContested() {
        val snapshot = classifyRivalTargets(region, mapOf("1003" to state(explored = true, cleared = true)), listOf(content("1003", "ruin")))
        assertEquals(RIVAL_KIND_CONTESTED_CLAIM, snapshot.targetsByKey["1003"]?.kind)
    }

    private fun snapshotFor() = classifyRivalTargets(region, mapOf("1002" to state(explored = true)), listOf(content("1002", "landmark")))

    @Test
    fun anInactiveBandIsCopiedThroughAndMovesNothing() {
        val (next, moves) = advanceAllRivalParties(arrayOf(band(status = RIVAL_STATUS_RETIRED)), snapshotFor(), turn = 1)
        assertEquals(1, next.size); assertNull(next[0].objectiveHexKey); assertTrue(moves.isEmpty())
    }

    @Test
    fun choosingAnObjectivePersistsItsKindAndOffersTheRumorOnce() {
        val (after1, moves1) = advanceAllRivalParties(arrayOf(band()), snapshotFor(), turn = 1)
        assertEquals("1002", after1[0].objectiveHexKey, "the landmark wins at these distances")
        assertEquals(RIVAL_KIND_LANDMARK, after1[0].objectiveKind)
        assertNotNull(moves1.single().rumorTarget, "a NEW objective proposes the rumor")
        assertEquals("1002", after1[0].rumoredObjectiveHexKey)
        // the countdown turn: same objective, no second rumor
        val (_, moves2) = advanceAllRivalParties(after1, snapshotFor(), turn = 2)
        assertNull(moves2.single().rumorTarget)
    }

    @Test
    fun arrivalIsStampedOnceAndTheBandNeverReArrivesWhereItStands() {
        // turn 1 chooses the landmark one hex away; turn 2 arrives. The band then walks ON to the
        // next prize -- it never re-arrives at the hex it stands on, and re-running the arrival
        // turn (undo + redo) cannot stamp a second arrival for it.
        var parties = arrayOf(band(pace = 1))
        parties = advanceAllRivalParties(parties, snapshotFor(), 1).first
        val (afterArrival, arrivalMoves) = advanceAllRivalParties(parties, snapshotFor(), 2)
        assertEquals(1, arrivalMoves.count { it.arrivedAt != null })
        assertEquals(1, afterArrival[0].arrivals)
        assertEquals("1002", afterArrival[0].lastArrivalHexKey)
        assertEquals("1002", afterArrival[0].currentHexKey)
        assertEquals(2, afterArrival[0].lastArrivalTurn)
        val (again, movesAgain) = advanceAllRivalParties(afterArrival, snapshotFor(), 2)
        assertTrue(movesAgain.none { it.arrivedAt != null }, "a re-run of the arrival turn re-offered")
        assertEquals(1, again[0].arrivals)
        // and the walk continues to the next prize, never back to where it stands
        var onward = afterArrival
        for (turn in 3..4) onward = advanceAllRivalParties(onward, snapshotFor(), turn).first
        assertEquals(2, onward[0].arrivals)
        assertTrue(onward[0].lastArrivalHexKey != "1002")
    }

    @Test
    fun confrontationIsOfferedOnTheCrossingTurnOnly() {
        // standing next to claimed ground raises aggression by 1 a turn; threshold 2 crosses on turn 2
        val snapshot = classifyRivalTargets(region, mapOf("1002" to state(claimed = true)), emptyList())
        var parties = arrayOf(band(currentHexKey = "1001", threshold = 2, aggression = 0, pace = 0))
        val offered = mutableListOf<Int>()
        for (turn in 1..4) {
            val (next, moves) = advanceAllRivalParties(parties, snapshot, turn)
            if (moves.any { it.confrontation }) offered += turn
            parties = next
        }
        assertEquals(listOf(2), offered)
        assertEquals(true, parties[0].confrontationOffered)
    }

    @Test
    fun theContextBlanksGmNumbersForPlayersAndReportsTheCap() {
        val parties = arrayOf(band(id = "a", aggression = 3, threshold = 5), band(id = "b"), band(id = "c", status = RIVAL_STATUS_RETIRED))
        val player = buildRivalCharterContext(parties, emptyArray(), isGM = false) { it }
        assertTrue(player.rows.all { it.aggression == null && it.aggressionMax == null }, "players never see aggression")
        assertTrue(player.atCap, "two active bands is the cap")
        assertTrue(player.rows.first { it.id == "c" }.inactive)
        val gm = buildRivalCharterContext(parties, emptyArray(), isGM = true) { it }
        assertEquals(3, gm.rows.first { it.id == "a" }.aggression)
        assertEquals(5, gm.rows.first { it.id == "a" }.aggressionMax)
        assertEquals(MAX_RIVAL_CHARTER_PARTIES, 2)
        assertFalse(buildRivalCharterContext(arrayOf(band(id = "a")), emptyArray(), isGM = true) { it }.atCap)
    }
}
