package at.posselt.pfrpg2e.kingdom.sheet

import at.posselt.pfrpg2e.data.kingdom.RealmData
import at.posselt.pfrpg2e.data.kingdom.settlements.*
import at.posselt.pfrpg2e.data.kingdom.structures.AvailableItemBonuses
import at.posselt.pfrpg2e.data.kingdom.structures.CommodityStorage
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.RawFeat
import at.posselt.pfrpg2e.kingdom.data.ChosenFeat
import at.posselt.pfrpg2e.kingdom.data.RawCommodities
import at.posselt.pfrpg2e.kingdom.data.RawCurrentCommodities
import at.posselt.pfrpg2e.kingdom.data.endTurn
import at.posselt.pfrpg2e.kingdom.modifiers.Modifier
import at.posselt.pfrpg2e.kingdom.modifiers.expressions.ExpressionContext
import at.posselt.pfrpg2e.kingdom.resources.calculateStorage
import at.posselt.pfrpg2e.data.kingdom.KingdomSkillRanks
import at.posselt.pfrpg2e.data.kingdom.leaders.Vacancies
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalculateIncomeTest {

    private fun createMockKingdomData(
        level: Int = 1,
        resourceDiceNow: Int = 0,
        bonusResourceDice: Int = 0,
        settlementsGenerateRd: Boolean = false,
        foodNow: Int = 0,
        lumberNow: Int = 0,
        luxuriesNow: Int = 0,
        oreNow: Int = 0,
        stoneNow: Int = 0,
    ): KingdomData {
        return js("""{
            level: level,
            resourceDice: { now: resourceDiceNow },
            bonusResourceDice: bonusResourceDice,
            commodities: {
                now: {
                    food: foodNow,
                    lumber: lumberNow,
                    luxuries: luxuriesNow,
                    ore: oreNow,
                    stone: stoneNow
                },
                next: { food: 0, lumber: 0, luxuries: 0, ore: 0, stone: 0 }
            },
            settings: {
                settlementsGenerateRd: settlementsGenerateRd
            }
        }""").unsafeCast<KingdomData>()
    }

    private fun createMockSettlement(sizeType: SettlementSizeType, maximumCivicRdLimit: Int): Settlement {
        val size = settlementSizeData.first { it.type == sizeType }
        return Settlement(
            id = "mock-id",
            name = "Mock Settlement",
            type = SettlementType.SETTLEMENT,
            waterBorders = 0,
            isSecondaryTerritory = false,
            settlementEventBonus = 0,
            leaderLeadershipActivityBonus = 0,
            bonuses = emptySet(),
            allowCapitalInvestment = false,
            notes = emptySet(),
            storage = CommodityStorage(),
            increaseLeadershipActivities = false,
            consumptionReduction = 0,
            availableItems = AvailableItemBonuses(),
            size = size,
            unlockActivities = emptySet(),
            residentialLots = 0,
            hasBridge = false,
            occupiedBlocks = 0,
            preventItemLevelPenalty = false,
            delayedStructures = emptyList(),
            constructedStructures = emptyList(),
            structuresUnderConstruction = emptyList(),
            maximumCivicRdLimit = maximumCivicRdLimit,
            settlementActions = 0,
            blocks = emptyList(),
            layoutType = SettlementLayoutType.RIGID
        )
    }

    private fun createMockFeat(id: String, resourceDice: Int? = null, increaseGainedLuxuriesOncePerTurnBy: Int? = null): RawFeat {
        return js("""{
            id: id,
            name: "Mock Feat",
            level: 1,
            text: "Mock text",
            resourceDice: resourceDice,
            increaseGainedLuxuriesOncePerTurnBy: increaseGainedLuxuriesOncePerTurnBy
        }""").unsafeCast<RawFeat>()
    }

    @Test
    fun testGetResourceDiceAmount() {
        val kingdomData = createMockKingdomData(
            level = 5,
            resourceDiceNow = 2,
            bonusResourceDice = 1,
            settlementsGenerateRd = true
        )

        val chosenFeats = listOf(
            ChosenFeat(takenAtLevel = 1, feat = createMockFeat("f1", resourceDice = 3)),
            ChosenFeat(takenAtLevel = 2, feat = createMockFeat("f2", resourceDice = null))
        )

        val settlements = listOf(
            createMockSettlement(SettlementSizeType.TOWN, maximumCivicRdLimit = 2), // min(2, 1) = 1
            createMockSettlement(SettlementSizeType.CITY, maximumCivicRdLimit = 1), // max(1, min(1, 2)) = 1
            createMockSettlement(SettlementSizeType.VILLAGE, maximumCivicRdLimit = 5) // 0
        )

        // Base 4 + level 5 + feats 3 + resourceDice.now 2 + bonusResourceDice 1 + settlements (1 + 1 + 0) = 17
        val expectedDice = 4 + 5 + 3 + 2 + 1 + 2
        val actualDice = kingdomData.getResourceDiceAmount(chosenFeats, settlements, kingdomData.level)
        assertEquals(expectedDice, actualDice)
    }

    @Test
    fun testCalculateProjectedResources() {
        val kingdomData = createMockKingdomData(
            level = 10,
            resourceDiceNow = 0,
            bonusResourceDice = 0,
            settlementsGenerateRd = false
        )

        val realmData = RealmData(
            size = 15,
            worksites = RealmData.WorkSites(
                farmlands = RealmData.WorkSite(quantity = 0, resources = 0),
                lumberCamps = RealmData.WorkSite(quantity = 2, resources = 1), // income = 3
                mines = RealmData.WorkSite(quantity = 1, resources = 2), // income = 3
                quarries = RealmData.WorkSite(quantity = 3, resources = 0), // income = 3
                luxurySources = RealmData.WorkSite(quantity = 1, resources = 1) // income = 2
            )
        )

        val chosenFeats = listOf(
            ChosenFeat(takenAtLevel = 1, feat = createMockFeat("f1", increaseGainedLuxuriesOncePerTurnBy = 2))
        )

        val expressionContext = ExpressionContext(
            usedSkill = null,
            ranks = KingdomSkillRanks(),
            leader = null,
            activity = null,
            phase = null,
            level = 10,
            unrest = 0,
            rollOptions = emptySet(),
            vacancies = Vacancies(),
            structure = null,
            anarchyAt = 20,
            atWar = false,
            eventTraits = emptySet(),
            settlementEvents = emptySet(),
            eventLeader = null,
            event = null,
            structures = emptySet(),
            waterBorders = 0
        )

        // No modifier additions
        val modifiers = emptyList<Modifier>()

        val projected = calculateProjectedResources(
            kingdomData = kingdomData,
            realmData = realmData,
            chosenFeats = chosenFeats,
            settlements = emptyList(),
            expressionContext = expressionContext,
            modifiers = modifiers
        )

        // Resource dice = 4 + level 10 = 14
        assertEquals(14, projected.income.resourceDice)
        // Lumber from worksite = 3
        assertEquals(3, projected.income.lumber)
        // Ore from worksite = 3
        assertEquals(3, projected.income.ore)
        // Stone from worksite = 3
        assertEquals(3, projected.income.stone)
        // Luxuries = worksite 2 + chosen feat increase 2 = 4
        assertEquals(4, projected.income.luxuries)
        assertFalse(projected.oreCappedByStorage)
    }

    @Test
    fun testCalculateProjectedResourcesCapsGainByRemainingStorage() {
        val kingdomData = createMockKingdomData(
            level = 1,
            oreNow = 3,
        )
        val realmData = RealmData(
            // A territory stores four commodities. With three ore already stored, only one of the
            // three projected ore can be retained at end turn.
            size = 1,
            worksites = RealmData.WorkSites(
                farmlands = RealmData.WorkSite(quantity = 0, resources = 0),
                lumberCamps = RealmData.WorkSite(quantity = 0, resources = 0),
                mines = RealmData.WorkSite(quantity = 1, resources = 2),
                quarries = RealmData.WorkSite(quantity = 0, resources = 0),
                luxurySources = RealmData.WorkSite(quantity = 0, resources = 0),
            ),
        )
        val expressionContext = ExpressionContext(
            usedSkill = null,
            ranks = KingdomSkillRanks(),
            leader = null,
            activity = null,
            phase = null,
            level = 1,
            unrest = 0,
            rollOptions = emptySet(),
            vacancies = Vacancies(),
            structure = null,
            anarchyAt = 20,
            atWar = false,
            eventTraits = emptySet(),
            settlementEvents = emptySet(),
            eventLeader = null,
            event = null,
            structures = emptySet(),
            waterBorders = 0,
        )

        val projected = calculateProjectedResources(
            kingdomData = kingdomData,
            realmData = realmData,
            chosenFeats = emptyList(),
            settlements = emptyList(),
            expressionContext = expressionContext,
            modifiers = emptyList(),
        )

        assertEquals(1, projected.income.ore)
        assertTrue(projected.oreCappedByStorage)
        assertFalse(projected.lumberCappedByStorage)

        val rawEndTurn = RawCurrentCommodities(
            now = RawCommodities(food = 0, lumber = 0, luxuries = 0, ore = 3, stone = 0),
            next = RawCommodities(food = 0, lumber = 0, luxuries = 0, ore = 3, stone = 0),
        ).endTurn(calculateStorage(realmData, emptyList()))
        val automatedEndTurn = RawCurrentCommodities(
            now = RawCommodities(food = 0, lumber = 0, luxuries = 0, ore = 3, stone = 0),
            next = RawCommodities(food = 0, lumber = 0, luxuries = 0, ore = projected.income.ore, stone = 0),
        ).endTurn(calculateStorage(realmData, emptyList()))
        assertEquals(rawEndTurn.now.ore, 3 + projected.income.ore)
        assertEquals(rawEndTurn.now.ore, automatedEndTurn.now.ore)
    }

    @Test
    fun testGetResourceDiceAmountExcludesCurrentWhenProjecting() {
        // A kingdom currently holding 1 resource die (now = 1).
        val kingdomData = createMockKingdomData(level = 1, resourceDiceNow = 1)

        // This-turn total includes the dice you currently hold: 4 + 1 + now 1 = 6
        assertEquals(
            6,
            kingdomData.getResourceDiceAmount(emptyList(), emptyList(), kingdomData.level)
        )
        // A next-turn projection must NOT fold in the current dice (they get spent this turn): 4 + 1 = 5
        assertEquals(
            5,
            kingdomData.getResourceDiceAmount(emptyList(), emptyList(), kingdomData.level, includeCurrent = false)
        )
    }

    @Test
    fun testCalculateProjectedResourcesExcludesCurrentResourceDice() {
        // Regression: a level-1 kingdom holding 1 resource die used to project 6 (4 + level + now).
        // The current dice are consumed each turn, so the projection should read 5, not 6.
        val kingdomData = createMockKingdomData(level = 1, resourceDiceNow = 1)

        val realmData = RealmData(
            size = 1,
            worksites = RealmData.WorkSites(
                farmlands = RealmData.WorkSite(quantity = 0, resources = 0),
                lumberCamps = RealmData.WorkSite(quantity = 0, resources = 0),
                mines = RealmData.WorkSite(quantity = 0, resources = 0),
                quarries = RealmData.WorkSite(quantity = 0, resources = 0),
                luxurySources = RealmData.WorkSite(quantity = 0, resources = 0)
            )
        )

        val expressionContext = ExpressionContext(
            usedSkill = null,
            ranks = KingdomSkillRanks(),
            leader = null,
            activity = null,
            phase = null,
            level = 1,
            unrest = 0,
            rollOptions = emptySet(),
            vacancies = Vacancies(),
            structure = null,
            anarchyAt = 20,
            atWar = false,
            eventTraits = emptySet(),
            settlementEvents = emptySet(),
            eventLeader = null,
            event = null,
            structures = emptySet(),
            waterBorders = 0
        )

        val projected = calculateProjectedResources(
            kingdomData = kingdomData,
            realmData = realmData,
            chosenFeats = emptyList(),
            settlements = emptyList(),
            expressionContext = expressionContext,
            modifiers = emptyList()
        )

        // 4 base + 1 level, current resourceDice.now (1) excluded
        assertEquals(5, projected.income.resourceDice)
    }
}
