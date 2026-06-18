package at.posselt.pfrpg2e.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import at.posselt.pfrpg2e.kingdom.data.RawResources
import at.posselt.pfrpg2e.kingdom.data.RawFame
import at.posselt.pfrpg2e.kingdom.data.RawConsumption
import at.posselt.pfrpg2e.kingdom.data.RawCurrentCommodities
import at.posselt.pfrpg2e.kingdom.data.RawCommodities
import at.posselt.pfrpg2e.kingdom.data.RawRuin
import at.posselt.pfrpg2e.kingdom.data.RawNotes
import at.posselt.pfrpg2e.kingdom.data.RawLeaders
import at.posselt.pfrpg2e.kingdom.data.RawLeaderValues
import at.posselt.pfrpg2e.kingdom.data.RawWorkSites

class ActivityCapCalculatorTest {

    private fun pcLeader(uuid: String?, vacant: Boolean = false) =
        RawLeaderValues(uuid = uuid, invested = false, type = "pc", vacant = vacant)

    private fun npcLeader(uuid: String?, vacant: Boolean = false) =
        RawLeaderValues(uuid = uuid, invested = false, type = "regularNpc", vacant = vacant)

    private fun vacantSlot() =
        RawLeaderValues(uuid = null, invested = false, type = "pc", vacant = true)

    /** Builds the 8 leadership roles, padding any unspecified slots with vacant PC roles. */
    private fun leadersOf(vararg roles: RawLeaderValues): RawLeaders {
        val padded = (roles.toList() + List(8) { vacantSlot() }).take(8)
        return RawLeaders(
            ruler = padded[0], counselor = padded[1], emissary = padded[2], general = padded[3],
            magister = padded[4], treasurer = padded[5], viceroy = padded[6], warden = padded[7],
        )
    }

    private fun createTestKingdom(
        increaseLeadershipActivities: Boolean = false,
        settlements: List<String> = emptyList(),
        armyCount: Int = 0,
        pcLeaders: Int = 4,
        leaders: RawLeaders? = null,
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
        kingdom.leaders = leaders
            ?: leadersOf(*Array(pcLeaders) { pcLeader("Actor.$it") })

        return kingdom
    }

    @Test
    fun `Leadership cap is PC leaders times 2 by default`() {
        // 4 PC leaders, no Town Hall: 4 x 2 = 8 (RAW)
        val kingdom = createTestKingdom(pcLeaders = 4)
        val leadership = ActivityCapCalculator.calculate(kingdom).caps.find { it.phase == "leadership" }!!
        assertEquals(8, leadership.maximum)
        assertFalse(leadership.isOverCap)
    }

    @Test
    fun `Leadership cap is PC leaders times 3 with a Town Hall structure`() {
        // 8 PC leaders, with Town Hall: 8 x 3 = 24 (RAW)
        val kingdom = createTestKingdom(pcLeaders = 8, increaseLeadershipActivities = true)
        val leadership = ActivityCapCalculator.calculate(kingdom).caps.find { it.phase == "leadership" }!!
        assertEquals(24, leadership.maximum)
    }

    @Test
    fun `Party of 8 PCs gets 16 leadership activities without a Town Hall`() {
        val kingdom = createTestKingdom(pcLeaders = 8)
        val leadership = ActivityCapCalculator.calculate(kingdom).caps.find { it.phase == "leadership" }!!
        assertEquals(16, leadership.maximum)
    }

    @Test
    fun `Vacant and NPC leaders do not count toward the cap`() {
        val kingdom = createTestKingdom(
            leaders = leadersOf(
                pcLeader("Actor.1"),                 // counts
                pcLeader("Actor.2", vacant = true),  // vacant -> ignored
                npcLeader("Actor.3"),                // NPC -> ignored
            ),
        )
        val leadership = ActivityCapCalculator.calculate(kingdom).caps.find { it.phase == "leadership" }!!
        assertEquals(2, leadership.maximum, "only the one active PC leader counts (1 x 2)")
    }

    @Test
    fun `Each filled seat counts, even when one PC holds two roles`() {
        val kingdom = createTestKingdom(
            leaders = leadersOf(pcLeader("Actor.1"), pcLeader("Actor.1")),
        )
        val leadership = ActivityCapCalculator.calculate(kingdom).caps.find { it.phase == "leadership" }!!
        assertEquals(4, leadership.maximum, "two filled PC seats are two leaders (2 x 2), even if the same actor")
    }

    @Test
    fun `A full council of eight PC seats gets 16 (8 x 2)`() {
        // The realistic Kingmaker case: 8 leadership roles filled, even by fewer distinct players.
        val kingdom = createTestKingdom(
            leaders = leadersOf(
                pcLeader("Actor.1"), pcLeader("Actor.1"),
                pcLeader("Actor.2"), pcLeader("Actor.2"),
                pcLeader("Actor.3"), pcLeader("Actor.3"),
                pcLeader("Actor.4"), pcLeader("Actor.4"),
            ),
        )
        val leadership = ActivityCapCalculator.calculate(kingdom).caps.find { it.phase == "leadership" }!!
        assertEquals(16, leadership.maximum, "8 filled PC seats x 2 = 16, regardless of how many distinct actors")
    }

    @Test
    fun `Configured per-leader allotment overrides the RAW default`() {
        val leadership = ActivityCapCalculator.calculate(
            createTestKingdom(pcLeaders = 3),
            leadershipCap = 1,
            leadershipCapWithTownhall = 2,
        ).caps.find { it.phase == "leadership" }!!
        assertEquals(3, leadership.maximum, "3 PC leaders x 1 per leader")
    }

    @Test
    fun `Performed phase counts flow into current and remaining`() {
        val kingdom = createTestKingdom(pcLeaders = 4) // cap 8
        val result = ActivityCapCalculator.calculate(kingdom, mapOf("leadership" to 3))
        val leadership = result.caps.find { it.phase == "leadership" }!!
        assertEquals(3, leadership.current)
        assertEquals(8, leadership.maximum)
        assertFalse(leadership.isOverCap)
    }

    @Test
    fun `Exceeding the leadership cap is flagged as over cap`() {
        val kingdom = createTestKingdom(pcLeaders = 2) // cap 4
        val result = ActivityCapCalculator.calculate(kingdom, mapOf("leadership" to 5))
        val leadership = result.caps.find { it.phase == "leadership" }!!
        assertEquals(5, leadership.current)
        assertTrue(leadership.isOverCap)
    }
}