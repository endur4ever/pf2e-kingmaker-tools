package at.posselt.pfrpg2e.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import at.posselt.pfrpg2e.kingdom.data.RawResources
import at.posselt.pfrpg2e.kingdom.data.RawFame
import at.posselt.pfrpg2e.kingdom.data.RawConsumption
import at.posselt.pfrpg2e.kingdom.data.RawCurrentCommodities
import at.posselt.pfrpg2e.kingdom.data.RawCommodities
import at.posselt.pfrpg2e.kingdom.data.RawRuin
import at.posselt.pfrpg2e.kingdom.data.RawNotes
import at.posselt.pfrpg2e.kingdom.data.RawLeaders
import at.posselt.pfrpg2e.kingdom.data.RawWorkSites

class ActivityCapCalculatorTest {

    private fun createTestKingdom(
        increaseLeadershipActivities: Boolean = false,
        settlements: List<String> = emptyList(),
        armyCount: Int = 0
    ): KingdomData {
        val kingdom = js("{}").unsafeCast<KingdomData>()
        kingdom.name = "Test Kingdom"
        kingdom.level = 1
        kingdom.xpThreshold = 1000
        kingdom.xp = 0
        kingdom.size = 1
        kingdom.unrest = 0
        kingdom.fame = js("{}").unsafeCast<RawFame>().apply {
            now = 0; next = 0; type = "famous"
        }
        kingdom.resourcePoints = js("{}").unsafeCast<RawResources>().apply {
            now = 0; next = 0
        }
        kingdom.resourceDice = js("{}").unsafeCast<RawResources>().apply {
            now = 0; next = 0
        }
        kingdom.workSites = js("{}").unsafeCast<RawWorkSites>()
        kingdom.consumption = js("{}").unsafeCast<RawConsumption>().apply {
            now = 0; next = 0; armies = 0
        }
        kingdom.commodities = js("{}").unsafeCast<RawCurrentCommodities>().apply {
            now = js("{}").unsafeCast<RawCommodities>().apply { food = 0; lumber = 0; luxuries = 0; ore = 0; stone = 0 }
            next = js("{}").unsafeCast<RawCommodities>().apply { food = 0; lumber = 0; luxuries = 0; ore = 0; stone = 0 }
        }
        kingdom.ruin = js("{}").unsafeCast<RawRuin>()
        kingdom.notes = js("{}").unsafeCast<RawNotes>()
        kingdom.activeSettlement = null
        kingdom.hexContents = emptyArray()
        kingdom.turnsWithoutCultEvent = 0
        kingdom.turnsWithoutEvent = 0
        kingdom.ongoingEvents = emptyArray()

        kingdom.settings = js("{}").unsafeCast<KingdomSettings>().apply {
            rpToXpConversionRate = 1
            rpToXpConversionLimit = 100
            settlementsGenerateRd = false
            ruinThreshold = 5
            increaseScorePicksBy = 1
            realmSceneId = null
            xpPerClaimedHex = 0
            includeCapitalItemModifier = false
            cultOfTheBloomEvents = false
            autoCalculateSettlementLevel = false
            vanceAndKerensharaXP = false
            capitalInvestmentInCapital = false
            reduceDCToBuildLumberStructures = false
            kingdomSkillIncreaseEveryLevel = increaseLeadershipActivities
            this.asDynamic().increaseLeadershipActivities = increaseLeadershipActivities
            kingdomAllStructureItemBonusesStack = false
            kingdomIgnoreSkillRequirements = false
            autoCalculateArmyConsumption = false
            enableLeadershipModifiers = true
            expandMagicUse = false
            kingdomEventRollMode = "none"
            automateResources = "none"
            proficiencyMode = "none"
            kingdomEventsTable = null
            kingdomCultTable = null
            maximumFamePoints = 10
            leaderKingdomSkills = js("{}").unsafeCast<RawLeaderKingdomSkills>()
            leaderSkills = js("{}").unsafeCast<RawLeaderSkills>()
            automateStats = false
            recruitableArmiesFolderId = null
            eventDc = 15
            eventDcStep = 2
            cultEventDc = 15
            cultEventDcStep = 2
            partialStructureConstruction = false
            capStructureBonusAtKingdomLevel = false
            capitalCanGrowOneSizeLarger = false
            enableCouncilMissions = false
        }

        // For simplicity, we'll just mock the settlements and armies as needed in later tests.
        // Since ActivityCapCalculator will iterate through these, we should at least provide the structure.
        kingdom.settlements = emptyArray()
        kingdom.leaders = js("{}").unsafeCast<RawLeaders>()

        return kingdom
    }

    @Test
    fun `Leadership cap is 2 with default settings`() {
        val kingdom = createTestKingdom()
        val result = ActivityCapCalculator.calculate(kingdom)
        val leadership = result.caps.find { it.phase == "leadership" }!!
        assertEquals(2, leadership.maximum)
        assertFalse(leadership.isOverCap)
    }

    @Test
    // @Suppress("UNUSED_PARAMETER")
    fun `Leadership cap is 3 with increaseLeadershipActivities bonus`() {
        val kingdom = createTestKingdom(increaseLeadershipActivities = true)
        val result = ActivityCapCalculator.calculate(kingdom)
        val leadership = result.caps.find { it.phase == "leadership" }!!
        assertEquals(3, leadership.maximum)
    }
}