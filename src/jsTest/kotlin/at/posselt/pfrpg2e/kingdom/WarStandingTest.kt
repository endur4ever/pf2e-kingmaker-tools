package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawGroup
import at.posselt.pfrpg2e.kingdom.data.RawWarThreat
import at.posselt.pfrpg2e.kingdom.data.WarThreatStatus
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WarStandingTest {
    private fun group(name: String, standing: Int? = null, atWar: Boolean = false) = RawGroup(
        name = name,
        negotiationDC = 15,
        atWar = atWar,
        preventPledgeOfFealty = false,
        relations = "none",
        standing = standing,
        standingLog = null,
        allianceLevel = null,
        hexKey = null,
    )

    private fun threat(faction: String?, status: WarThreatStatus) = RawWarThreat(
        id = "t", name = "n", description = "", enemyFaction = null,
        escalationLevel = 0, maxEscalation = 3, eta = null,
        targetSettlementSceneId = null, targetHexLocation = null, linkedQuestId = null,
        linkedEventId = null, pauseOnExpiry = false, status = status.value,
        triggeredTurn = null, enemyFactionName = faction,
    )

    private fun kingdom(
        groups: Array<RawGroup>,
        threats: Array<RawWarThreat> = emptyArray(),
        turn: Int = 7,
    ): KingdomData = unsafeJso<dynamic> {
        this.groups = groups
        this.warThreats = threats
        this.currentTurn = turn
    }.unsafeCast<KingdomData>()

    @Test
    fun applyingAWarStandingDeltaLogsItAgainstTheRightFaction() {
        val k = kingdom(arrayOf(group("Pitax", standing = -10), group("Mivon", standing = 5)))

        assertTrue(k.applyWarStanding("Pitax", -4, WarStandingReason.DEFEAT))

        assertEquals(-14, k.groups[0].standing)
        assertEquals(1, k.groups[0].standingLog?.size)
        assertEquals(-4, k.groups[0].standingLog?.get(0)?.delta)
        assertEquals(7, k.groups[0].standingLog?.get(0)?.turn)
        assertEquals("kingdom.factionStanding.warDefeat", k.groups[0].standingLog?.get(0)?.reason)
        // The other faction is untouched.
        assertEquals(5, k.groups[1].standing)
        assertEquals(null, k.groups[1].standingLog)
    }

    @Test
    fun aRenamedOrDeletedFactionReportsFailureRatherThanSwallowingTheChange() {
        val k = kingdom(arrayOf(group("Pitax")))

        assertFalse(k.applyWarStanding("Pitax the Great", 4, WarStandingReason.VICTORY))

        assertEquals(null, k.groups[0].standingLog)
    }

    @Test
    fun standingIsClampedToTheFactionScale() {
        val k = kingdom(arrayOf(group("Pitax", standing = 98)))

        k.applyWarStanding("Pitax", 50, WarStandingReason.PEACE)

        assertEquals(100, k.groups[0].standing)
        // The log still records what was requested, so the entry explains the clamp.
        assertEquals(50, k.groups[0].standingLog?.get(0)?.delta)
    }

    @Test
    fun everyThreatStatusMapsToTheOutcomeThePeaceRuleExpects() {
        assertEquals(ThreatOutcome.ACTIVE, threat("Pitax", WarThreatStatus.ACTIVE).threatOutcome())
        assertEquals(ThreatOutcome.DEFEATED, threat("Pitax", WarThreatStatus.DEFEATED).threatOutcome())
        assertEquals(ThreatOutcome.ENDED_OTHERWISE, threat("Pitax", WarThreatStatus.EVADED).threatOutcome())
        assertEquals(ThreatOutcome.ENDED_OTHERWISE, threat("Pitax", WarThreatStatus.EXPIRED).threatOutcome())
    }

    @Test
    fun peaceBecomesOfferableOnlyWhenTheFactionsLastThreatFalls() {
        val stillFighting = kingdom(
            arrayOf(group("Pitax", atWar = true)),
            arrayOf(
                threat("Pitax", WarThreatStatus.DEFEATED),
                threat("Pitax", WarThreatStatus.ACTIVE),
            ),
        )
        assertFalse(peaceEligible(stillFighting.threatStates(), "Pitax"))

        val won = kingdom(
            arrayOf(group("Pitax", atWar = true)),
            arrayOf(
                threat("Pitax", WarThreatStatus.DEFEATED),
                threat("Pitax", WarThreatStatus.EVADED),
            ),
        )
        assertTrue(peaceEligible(won.threatStates(), "Pitax"))
    }

    @Test
    fun makingPeaceWithOneOfTwoEnemiesLeavesTheKingdomAtWar() {
        val k = kingdom(arrayOf(group("Pitax", atWar = true), group("Mivon", atWar = true)))

        assertTrue(k.kingdomStillAtWarWithout("Pitax"))
    }

    @Test
    fun makingPeaceWithTheLastEnemyEndsTheKingdomsWar() {
        val k = kingdom(arrayOf(group("Pitax", atWar = true), group("Mivon", atWar = false)))

        assertFalse(k.kingdomStillAtWarWithout("Pitax"))
    }
}
