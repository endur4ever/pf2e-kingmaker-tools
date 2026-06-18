package at.posselt.pfrpg2e.kingdom.sheet

import at.posselt.pfrpg2e.data.kingdom.RealmData
import at.posselt.pfrpg2e.data.kingdom.settlements.*
import at.posselt.pfrpg2e.data.kingdom.structures.AvailableItemBonuses
import at.posselt.pfrpg2e.data.kingdom.structures.CommodityStorage
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.RawFeat
import at.posselt.pfrpg2e.kingdom.data.ChosenFeat
import at.posselt.pfrpg2e.kingdom.modifiers.Modifier
import at.posselt.pfrpg2e.kingdom.modifiers.expressions.ExpressionContext
import at.posselt.pfrpg2e.data.kingdom.KingdomSkillRanks
import at.posselt.pfrpg2e.data.kingdom.leaders.Vacancies
import kotlin.test.Test
import kotlin.test.assertEquals

class CalculateIncomeTest {

    private fun createMockKingdomData(
        level: Int = 1,
        resourceDiceNow: Int = 0,
        bonusResourceDice: Int = 0,
        settlementsGenerateRd: Boolean = false
    ): KingdomData {
        return js("""{
            level: level,
            resourceDice: { now: resourceDiceNow },
            bonusResourceDice: bonusResourceDice,
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

        val income = calculateProjectedResources(
            kingdomData = kingdomData,
            realmData = realmData,
            chosenFeats = chosenFeats,
            settlements = emptyList(),
            expressionContext = expressionContext,
            modifiers = modifiers
        )

        // Resource dice = 4 + level 10 = 14
        assertEquals(14, income.resourceDice)
        // Lumber from worksite = 3
        assertEquals(3, income.lumber)
        // Ore from worksite = 3
        assertEquals(3, income.ore)
        // Stone from worksite = 3
        assertEquals(3, income.stone)
        // Luxuries = worksite 2 + chosen feat increase 2 = 4
        assertEquals(4, income.luxuries)
    }
}
