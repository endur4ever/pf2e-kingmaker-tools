package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawCharacter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompanionAutonomyTest {

    @Test
    fun `isEligible checks active, available, role and injury`() {
        val eligible = RawCharacter("Amiri").apply {
            role = "companion"
            active = true
            campAvailable = true
            expeditionStatus = "available"
            traveling = false
            injuryDaysRemaining = null
        }
        assertTrue(CompanionAutonomy.isEligible(eligible))

        val inactive = RawCharacter("Amiri").apply {
            role = "companion"
            active = false
            campAvailable = true
            expeditionStatus = "available"
            traveling = false
            injuryDaysRemaining = null
        }
        assertFalse(CompanionAutonomy.isEligible(inactive))

        val injured = RawCharacter("Amiri").apply {
            role = "companion"
            active = true
            campAvailable = true
            expeditionStatus = "available"
            traveling = false
            injuryDaysRemaining = 3
        }
        assertFalse(CompanionAutonomy.isEligible(injured))

        val npc = RawCharacter("Amiri").apply {
            role = "npc"
            active = true
            campAvailable = true
            expeditionStatus = "available"
            traveling = false
            injuryDaysRemaining = null
        }
        assertFalse(CompanionAutonomy.isEligible(npc))
    }

    @Test
    fun `computeWillingnessScore handles quests, discovery status and influence`() {
        val base = RawCharacter("Amiri").apply {
            personalQuestIds = emptyArray()
            discoveryStatus = "unknown"
            influence = 4
        }
        // Base score = 4 (influence)
        assertEquals(4, CompanionAutonomy.computeWillingnessScore(base))

        val withQuest = RawCharacter("Amiri").apply {
            personalQuestIds = arrayOf("q1")
            discoveryStatus = "unknown"
            influence = 4
        }
        // Quest (+100) + influence (4) = 104
        assertEquals(104, CompanionAutonomy.computeWillingnessScore(withQuest))

        val established = RawCharacter("Amiri").apply {
            personalQuestIds = emptyArray()
            discoveryStatus = "established"
            influence = 4
        }
        // Established (+50) + influence (4) = 54
        assertEquals(54, CompanionAutonomy.computeWillingnessScore(established))

        val establishedWithQuest = RawCharacter("Amiri").apply {
            personalQuestIds = arrayOf("q1")
            discoveryStatus = "established"
            influence = 4
        }
        // Quest (+100) + Established (+50) + influence (4) = 154
        assertEquals(154, CompanionAutonomy.computeWillingnessScore(establishedWithQuest))
    }

    @Test
    fun `selectAutonomousCompanions filters and sorts correctly`() {
        val eligibleLow = RawCharacter("Amiri").apply {
            role = "companion"
            active = true
            campAvailable = true
            expeditionStatus = "available"
            traveling = false
            injuryDaysRemaining = null
            influence = 2
            discoveryStatus = "introduced"
        }
        val eligibleHigh = RawCharacter("Lini").apply {
            role = "companion"
            active = true
            campAvailable = true
            expeditionStatus = "available"
            traveling = false
            injuryDaysRemaining = null
            influence = 8
            discoveryStatus = "established" // established (+50)
        }
        val ineligible = RawCharacter("Valeros").apply {
            role = "companion"
            active = true
            campAvailable = true
            expeditionStatus = "available"
            traveling = true // Ineligible!
        }

        val result = CompanionAutonomy.selectAutonomousCompanions(listOf(eligibleLow, eligibleHigh, ineligible))
        assertEquals(2, result.size)
        assertEquals("Lini", result[0].name)
        assertEquals("Amiri", result[1].name)
    }
}
