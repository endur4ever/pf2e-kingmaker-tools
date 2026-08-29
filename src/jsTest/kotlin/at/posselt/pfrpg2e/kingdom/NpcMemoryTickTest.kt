package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.npcmemory.AttitudeBand
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NpcMemoryTickTest {
    private fun record(turn: Int, unrest: Int = 0, warPressure: Int? = null) = unsafeJso<dynamic> {
        this.turn = turn
        this.timestamp = ""
        this.fame = 0
        this.resourcePoints = 10
        this.consumption = 0
        this.unrest = unrest
        this.warPressure = warPressure
        this.level = 1
        this.size = 5
        this.ruinCorruption = 0
        this.ruinCrime = 0
        this.ruinDecay = 0
        this.ruinStrife = 0
    }

    private fun npc(id: String, occupation: String, tracked: Boolean, score: Int? = null, log: Array<dynamic> = emptyArray()) =
        unsafeJso<dynamic> {
            this.id = id
            this.name = id
            this.occupation = occupation
            this.memoryTracked = tracked
            this.memoryLog = log
            this.attitudeScore = score
        }

    private fun kingdomWith(npcs: Array<dynamic>, records: Array<dynamic>): KingdomData {
        val k = unsafeJso<dynamic> {}
        k.turnHistory = records
        k.settlements = arrayOf(unsafeJso<dynamic> {
            sceneId = "scene1"
            populationRoster = unsafeJso<dynamic> { this.npcs = npcs }
        })
        return k.unsafeCast<KingdomData>()
    }

    @Test
    fun trackedNpcRemembersAnUnrestSpikeAndUntrackedStaysUntouched() {
        val merchant = npc("m", "Merchant", tracked = true)
        val bystander = npc("b", "Merchant", tracked = false)
        val k = kingdomWith(arrayOf(merchant, bystander), arrayOf(record(1, unrest = 1), record(2, unrest = 5)))
        val outcome = evaluateNpcMemoriesForTurn(k, 2, npcMemoryRules(), fameMax = 3)
        assertTrue(outcome.memoriesWritten >= 1)
        assertEquals(-2, merchant.attitudeScore.unsafeCast<Int>())
        assertEquals("unrest-spike", merchant.memoryLog.unsafeCast<Array<dynamic>>()[0].ruleId)
        assertEquals(null, bystander.attitudeScore)
        assertEquals(0, bystander.memoryLog.unsafeCast<Array<dynamic>>().size)
    }

    @Test
    fun steadyStateWritesNothingAndDecaysTowardZero() {
        val merchant = npc("m", "Merchant", tracked = true, score = -4)
        val k = kingdomWith(arrayOf(merchant), arrayOf(record(1, unrest = 5), record(2, unrest = 5)))
        val outcome = evaluateNpcMemoriesForTurn(k, 2, npcMemoryRules(), fameMax = 3)
        assertEquals(0, outcome.memoriesWritten)
        assertEquals(-3, merchant.attitudeScore.unsafeCast<Int>())
    }

    @Test
    fun cooldownDerivedFromTheLogSuppressesARepeat() {
        val log = arrayOf(unsafeJso<dynamic> { ruleId = "war-looms"; turn = 2; delta = 1 })
        val soldier = npc("s", "Soldier", tracked = true, score = 1, log = log)
        // war-looms cooldownTurns=3: fired turn 2, so turn 3 is suppressed -> decay instead
        val k = kingdomWith(arrayOf(soldier), arrayOf(record(2, warPressure = 5), record(3, warPressure = 9)))
        evaluateNpcMemoriesForTurn(k, 3, npcMemoryRules(), fameMax = 3)
        assertEquals(1, soldier.memoryLog.unsafeCast<Array<dynamic>>().size)
        assertEquals(0, soldier.attitudeScore.unsafeCast<Int>())
    }

    @Test
    fun bandCrossingIsEdgeTriggeredAndReportsTheNewBand() {
        val merchant = npc("m", "Merchant", tracked = true, score = -9)
        val k = kingdomWith(arrayOf(merchant), arrayOf(record(1, unrest = 0), record(2, unrest = 6)))
        val outcome = evaluateNpcMemoriesForTurn(k, 2, npcMemoryRules(), fameMax = 3)
        // -9 + (-2) = -11: crossed into UNFRIENDLY
        assertEquals(1, outcome.crossings.size)
        assertEquals(AttitudeBand.UNFRIENDLY, outcome.crossings.single().band)
        assertEquals("scene1", outcome.crossings.single().settlementSceneId)
        // same state next turn: no rule fires, decay -10, still UNFRIENDLY -> no crossing
        val k2 = kingdomWith(arrayOf(merchant), arrayOf(record(2, unrest = 6), record(3, unrest = 6)))
        val second = evaluateNpcMemoriesForTurn(k2, 3, npcMemoryRules(), fameMax = 3)
        assertEquals(0, second.crossings.size)
    }

    @Test
    fun ruleCatalogLoadsAllSeventeen() {
        assertEquals(17, npcMemoryRules().size)
    }

    @Test
    fun firstTurnWithNoPredecessorOnlyDecays() {
        val merchant = npc("m", "Merchant", tracked = true, score = 3)
        val k = kingdomWith(arrayOf(merchant), arrayOf(record(1, unrest = 9)))
        val outcome = evaluateNpcMemoriesForTurn(k, 1, npcMemoryRules(), fameMax = 3)
        assertEquals(0, outcome.memoriesWritten)
        assertEquals(2, merchant.attitudeScore.unsafeCast<Int>())
    }
}
