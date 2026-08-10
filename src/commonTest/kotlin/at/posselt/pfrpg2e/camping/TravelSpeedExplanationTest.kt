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
    fun withNoReadableMemberSpeedsThereIsNoAttribution() {
        val breakdown = explainTravelSpeed(partySpeedFeet = 25, members = emptyList())
        assertTrue(breakdown.slowest.isEmpty())
        assertEquals(25, breakdown.partySpeedFeet)
    }

    @Test
    fun reportsTheKingmakerHexplorationTable() {
        // Speed affects travel ONLY through this table: it sets how many Travel activities the
        // party can spend per day. There is no separate pace multiplier any more.
        assertEquals(1.0, explainTravelSpeed(partySpeedFeet = 25).hexplorationActivitiesPerDay)
        assertEquals(2.0, explainTravelSpeed(partySpeedFeet = 30).hexplorationActivitiesPerDay)
        assertEquals(3.0, explainTravelSpeed(partySpeedFeet = 50).hexplorationActivitiesPerDay)
        assertEquals(4.0, explainTravelSpeed(partySpeedFeet = 60).hexplorationActivitiesPerDay)
        assertEquals(0.5, explainTravelSpeed(partySpeedFeet = 10).hexplorationActivitiesPerDay)
    }

}
