package at.posselt.pfrpg2e.camping

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TravelSpeedExplanationTest {
    @Test
    fun namesTheMemberHoldingThePartyBack() {
        // PF2e sets the party's travel Speed to the minimum across members, so the explanation
        // must point at whoever is actually at that Speed — not at everyone.
        val breakdown = explainTravelSpeed(
            partySpeedFeet = 20,
            members = listOf(
                MemberSpeed("Valerie", 20),
                MemberSpeed("Linzi", 25),
                MemberSpeed("Amiri", 35),
            ),
        )
        assertEquals(listOf("Valerie"), breakdown.slowest.map { it.name })
    }

    @Test
    fun namesEveryMemberOnATie() {
        val breakdown = explainTravelSpeed(
            partySpeedFeet = 25,
            members = listOf(
                MemberSpeed("Valerie", 25),
                MemberSpeed("Linzi", 25),
                MemberSpeed("Amiri", 40),
            ),
        )
        assertEquals(listOf("Valerie", "Linzi"), breakdown.slowest.map { it.name })
    }

    @Test
    fun paceIsSpeedOverBaseline() {
        val breakdown = explainTravelSpeed(partySpeedFeet = 30)
        assertEquals(TRAVEL_SPEED_BASELINE_FEET, breakdown.baselineFeet)
        assertEquals(30.0 / TRAVEL_SPEED_BASELINE_FEET, breakdown.multiplier)
        assertTrue(breakdown.multiplier > 1.0, "a faster-than-baseline party travels in less time")
    }

    @Test
    fun aSlowPartyGetsAMultiplierBelowOne() {
        val breakdown = explainTravelSpeed(partySpeedFeet = 15)
        assertTrue(breakdown.multiplier < 1.0)
    }

    @Test
    fun withNoReadableMemberSpeedsThereIsNoAttribution() {
        val breakdown = explainTravelSpeed(partySpeedFeet = 25, members = emptyList())
        assertTrue(breakdown.slowest.isEmpty())
        assertEquals(25, breakdown.partySpeedFeet)
    }

    @Test
    fun reportsTheKingmakerHexplorationTableSeparately() {
        // The hexploration budget does NOT use the pace multiplier — it uses the Speed table, so
        // the tooltip quotes both and they must not be conflated.
        assertEquals(1.0, explainTravelSpeed(partySpeedFeet = 25).hexplorationActivitiesPerDay)
        assertEquals(2.0, explainTravelSpeed(partySpeedFeet = 30).hexplorationActivitiesPerDay)
        assertEquals(3.0, explainTravelSpeed(partySpeedFeet = 50).hexplorationActivitiesPerDay)
        assertEquals(4.0, explainTravelSpeed(partySpeedFeet = 60).hexplorationActivitiesPerDay)
        assertEquals(0.5, explainTravelSpeed(partySpeedFeet = 10).hexplorationActivitiesPerDay)
    }

    @Test
    fun aZeroBaselineCannotDivideByZero() {
        val breakdown = explainTravelSpeed(partySpeedFeet = 25, baselineFeet = 0)
        assertEquals(TRAVEL_SPEED_BASELINE_FEET, breakdown.baselineFeet)
    }
}
