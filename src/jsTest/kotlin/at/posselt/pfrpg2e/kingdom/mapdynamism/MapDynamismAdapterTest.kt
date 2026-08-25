package at.posselt.pfrpg2e.kingdom.mapdynamism

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.RawRewildTracker
import at.posselt.pfrpg2e.kingdom.data.RawWarThreat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapDynamismAdapterTest {
    // a straight-line region: 1 - 2 - 3 - 4
    private val line: (String) -> Set<String> = { hex ->
        when (hex) {
            "1" -> setOf("2")
            "2" -> setOf("1", "3")
            "3" -> setOf("2", "4")
            "4" -> setOf("3")
            else -> emptySet()
        }
    }

    private fun threat(
        id: String,
        wanders: Boolean? = true,
        current: String? = "1",
        target: String? = "4",
        status: String = "active",
        consumedTurn: Int? = null,
    ) = RawWarThreat(
        id = id, name = "Threat $id", description = "", enemyFaction = null,
        escalationLevel = 1, maxEscalation = 4, eta = null,
        targetSettlementSceneId = null, targetHexLocation = target,
        linkedQuestId = null, linkedEventId = null, pauseOnExpiry = false,
        status = status, triggeredTurn = null,
    ).also {
        it.wanders = wanders
        it.currentHexLocation = current
        it.migrationConsumedTurn = consumedTurn
    }

    private fun kingdom(
        enabled: Boolean?,
        vararg threats: RawWarThreat,
    ): KingdomData {
        val k = js("{}").unsafeCast<KingdomData>()
        k.asDynamic().settings = js("{}")
        k.settings.threatMigrationEnabled = enabled
        k.warThreats = arrayOf(*threats)
        return k
    }

    @Test
    fun theMasterDialGatesEveryProposal() {
        val off = kingdom(enabled = false, threat("a"))
        assertTrue(migrationProposals(off, 5, line).isEmpty())
        val nullDial = kingdom(enabled = null, threat("a"))
        assertTrue(migrationProposals(nullDial, 5, line).isEmpty(), "null = off: migration is opt-in")
    }

    @Test
    fun onlyActiveWanderingThreatsWithATargetPropose() {
        val k = kingdom(
            enabled = true,
            threat("moves"),
            threat("static", wanders = false),
            threat("legacy", wanders = null), // un-migrated row: null must read as static
            threat("resolvedThreat", status = "resolved"),
            threat("noTarget", target = null),
            threat("doneThisTurn", consumedTurn = 5),
        )
        val proposals = migrationProposals(k, currentTurn = 5, neighbors = line)
        assertEquals(listOf("moves"), proposals.map { it.threatId })
        assertEquals("2", proposals[0].toHex, "one step along the line")
    }

    @Test
    fun nullPositionMeansAtTargetSoNothingProposes() {
        val k = kingdom(enabled = true, threat("home", current = null))
        assertTrue(migrationProposals(k, 5, line).isEmpty())
    }

    private fun rewildKingdom(delay: Int?, vararg trackers: RawRewildTracker): KingdomData {
        val k = js("{}").unsafeCast<KingdomData>()
        k.asDynamic().settings = js("{}")
        k.settings.rewildDelayTurns = delay
        k.rewildTrackers = arrayOf(*trackers)
        return k
    }

    private fun tracker(hexKey: String, since: Int, consumed: Boolean? = null): RawRewildTracker {
        val obj = js("{}").unsafeCast<RawRewildTracker>()
        obj.hexKey = hexKey
        obj.clearedSinceTurn = since
        obj.offerConsumed = consumed
        return obj
    }

    @Test
    fun reconcilePersistsTimersAndOffersOnlyRipeUnconsumedHexes() {
        val k = rewildKingdom(
            delay = 3,
            tracker("old", since = 1),
            tracker("quiet", since = 1, consumed = true),
            tracker("gone", since = 1),
        )
        val candidates = reconcileRewild(k, liveClearedUnclaimed = setOf("old", "quiet", "fresh"), currentTurn = 5)
        assertEquals(listOf("old"), candidates, "ripe + unconsumed only")
        val byHex = k.rewildTrackers!!.associateBy { it.hexKey }
        assertEquals(1, byHex["old"]?.clearedSinceTurn, "surviving timer keeps its ORIGINAL start")
        assertEquals(true, byHex["quiet"]?.offerConsumed, "consumed flag survives the reconcile")
        assertEquals(5, byHex["fresh"]?.clearedSinceTurn, "new hex starts its timer now")
        assertNull(byHex["gone"], "a hex that left cleared-unclaimed drops its tracker")
    }

    @Test
    fun zeroDelayNeverOffers() {
        val k = rewildKingdom(delay = 0, tracker("old", since = 1))
        assertTrue(reconcileRewild(k, setOf("old"), currentTurn = 50).isEmpty())
    }

    @Test
    fun absentDelayUsesTheSixTurnDefault() {
        val k = rewildKingdom(delay = null, tracker("h", since = 1))
        assertTrue(reconcileRewild(k, setOf("h"), currentTurn = 6).isEmpty(), "age 5 < 6")
        val k2 = rewildKingdom(delay = null, tracker("h", since = 1))
        assertEquals(listOf("h"), reconcileRewild(k2, setOf("h"), currentTurn = 7), "age 6 is ripe")
    }
}
