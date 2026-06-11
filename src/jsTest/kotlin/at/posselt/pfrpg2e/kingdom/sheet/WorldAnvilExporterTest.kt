package at.posselt.pfrpg2e.kingdom.sheet

import at.posselt.pfrpg2e.kingdom.KingdomData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorldAnvilExporterTest {

    private fun createEmptyKingdomData(): KingdomData = js("""{
        name: "Test Kingdom",
        atWar: false,
        fame: { now: 0, next: 0, type: "famous" },
        level: 1,
        xpThreshold: 100,
        xp: 0,
        size: 10,
        unrest: 0,
        resourcePoints: { now: 0, next: 0 },
        resourceDice: { now: 0, next: 0 },
        workSites: {
            farmlands: { quantity: 0, resources: 0 },
            lumberCamps: { quantity: 0, resources: 0 },
            mines: { quantity: 0, resources: 0 },
            quarries: { quantity: 0, resources: 0 },
            luxurySources: { quantity: 0, resources: 0 }
        },
        consumption: { armies: 0, now: 0, next: 0 },
        supernaturalSolutions: 0,
        creativeSolutions: 0,
        settings: {
            rpToXpConversionRate: 1,
            rpToXpConversionLimit: 0,
            settlementsGenerateRd: false,
            ruinThreshold: 10,
            increaseScorePicksBy: 0,
            xpPerClaimedHex: 0,
            includeCapitalItemModifier: false,
            cultOfTheBloomEvents: false,
            autoCalculateSettlementLevel: false,
            vanceAndKerensharaXP: false,
            capitalInvestmentInCapital: false,
            reduceDCToBuildLumberStructures: false,
            kingdomSkillIncreaseEveryLevel: false,
            kingdomAllStructureItemBonusesStack: false,
            kingdomIgnoreSkillRequirements: false,
            autoCalculateArmyConsumption: false,
            enableLeadershipModifiers: false,
            expandMagicUse: false,
            kingdomEventRollMode: "public",
            automateResources: "OFF",
            proficiencyMode: "normal",
            maximumFamePoints: 0,
            leaderKingdomSkills: { ruler: [], counselor: [], emissary: [], general: [], magister: [], treasurer: [], viceroy: [], warden: [] },
            leaderSkills: { ruler: [], counselor: [], emissary: [], general: [], magister: [], treasurer: [], viceroy: [], warden: [] },
            automateStats: false,
            eventDc: 10,
            eventDcStep: 0,
            cultEventDc: 10,
            cultEventDcStep: 0,
            partialStructureConstruction: false,
            capStructureBonusAtKingdomLevel: false,
            capitalCanGrowOneSizeLarger: false,
            enableCouncilMissions: false
        },
        commodities: {
            now: { food: 0, lumber: 0, luxuries: 0, ore: 0, stone: 0 },
            next: { food: 0, lumber: 0, luxuries: 0, ore: 0, stone: 0 }
        },
        ruin: {
            corruption: { value: 0, penalty: 0, threshold: 10 },
            crime: { value: 0, penalty: 0, threshold: 10 },
            decay: { value: 0, penalty: 0, threshold: 10 },
            strife: { value: 0, penalty: 0, threshold: 10 }
        },
        notes: { public: "", gm: "" },
        homebrewMilestones: [],
        homebrewActivities: [],
        homebrewCharters: [],
        homebrewGovernments: [],
        homebrewHeartlands: [],
        homebrewKingdomEvents: [],
        homebrewFeats: [],
        activityBlacklist: [],
        featBlacklist: [],
        heartlandBlacklist: [],
        charterBlacklist: [],
        governmentBlacklist: [],
        kingdomEventBlacklist: [],
        ongoingEvents: [],
        modifiers: [],
        settlements: [],
        leaders: {
            ruler: { uuid: null, invested: false, type: "pc", vacant: true },
            counselor: { uuid: null, invested: false, type: "pc", vacant: true },
            emissary: { uuid: null, invested: false, type: "pc", vacant: true },
            general: { uuid: null, invested: false, type: "pc", vacant: true },
            magister: { uuid: null, invested: false, type: "pc", vacant: true },
            treasurer: { uuid: null, invested: false, type: "pc", vacant: true },
            viceroy: { uuid: null, invested: false, type: "pc", vacant: true },
            warden: { uuid: null, invested: false, type: "pc", vacant: true }
        },
        charter: { type: null },
        heartland: { type: null },
        government: { type: null },
        abilityBoosts: { culture: false, economy: false, loyalty: false, stability: false },
        features: [],
        bonusFeats: [],
        groups: [],
        skillRanks: { culture: 0, economy: 0, loyalty: 0, stability: 0 },
        abilityScores: { culture: 10, economy: 10, loyalty: 10, stability: 10 },
        initialProficiencies: [],
        milestones: [],
        companions: [],
        structureBlacklist: [],
        campaignClocks: [],
        questTemplates: [],
        campaignQuests: [],
        kingdomEventTemplates: [],
        campaignKingdomEvents: [],
        eventGenerationLogs: [],
        questGeneratorSettings: null
    }""")

    private fun createMinimalKingdomData(
        name: String = "Test Kingdom",
        level: Int = 1,
        xp: Int = 0,
        size: Int = 10,
        unrest: Int = 0,
        food: Int = 0,
        lumber: Int = 0,
        stone: Int = 0,
        ore: Int = 0,
        luxuries: Int = 0,
        consumptionNow: Int = 0,
        ongoingEvents: Array<dynamic> = emptyArray(),
        quests: Array<dynamic>? = null,
        companions: Array<dynamic>? = null,
        armyDeployments: Array<dynamic>? = null,
        settlements: Array<dynamic> = emptyArray()
    ): KingdomData {
        val base = createEmptyKingdomData()
        base.name = name
        base.level = level
        base.xp = xp
        base.size = size
        base.unrest = unrest
        base.commodities.now.food = food
        base.commodities.now.lumber = lumber
        base.commodities.now.stone = stone
        base.commodities.now.ore = ore
        base.commodities.now.luxuries = luxuries
        base.consumption.now = consumptionNow
        base.ongoingEvents = ongoingEvents
        base.quests = quests
        base.companions = companions
        base.armyDeployments = armyDeployments
        base.settlements = settlements
        return base
    }

    // --- generateResourcesTable tests ---

    @Test
    fun testResourcesTableEmpty() {
        val kingdom = createMinimalKingdomData()
        val result = WorldAnvilExporter.generateResourcesTable(kingdom)
        assertTrue(result.contains("[table]"))
        assertTrue(result.contains("[tr][th]Resource[/th][th]Current[/th][/tr]"))
        assertTrue(result.contains("[tr][td]Food[/td][td]0[/td][/tr]"))
        assertTrue(result.contains("[tr][td]Lumber[/td][td]0[/td][/tr]"))
        assertTrue(result.contains("[tr][td]Stone[/td][td]0[/td][/tr]"))
        assertTrue(result.contains("[tr][td]Ore[/td][td]0[/td][/tr]"))
        assertTrue(result.contains("[tr][td]Luxuries[/td][td]0[/td][/tr]"))
        assertTrue(result.contains("[tr][td]Consumption[/td][td]0[/td][/tr]"))
        assertTrue(result.contains("[/table]"))
    }

    @Test
    fun testResourcesTableWithValues() {
        val kingdom = createMinimalKingdomData(
            food = 5,
            lumber = 3,
            stone = 2,
            ore = 1,
            luxuries = 4,
            consumptionNow = 6
        )
        val result = WorldAnvilExporter.generateResourcesTable(kingdom)
        assertTrue(result.contains("[tr][td]Food[/td][td]5[/td][/tr]"))
        assertTrue(result.contains("[tr][td]Lumber[/td][td]3[/td][/tr]"))
        assertTrue(result.contains("[tr][td]Stone[/td][td]2[/td][/tr]"))
        assertTrue(result.contains("[tr][td]Ore[/td][td]1[/td][/tr]"))
        assertTrue(result.contains("[tr][td]Luxuries[/td][td]4[/td][/tr]"))
        assertTrue(result.contains("[tr][td]Consumption[/td][td]6[/td][/tr]"))
    }

    // --- generateEventsList tests ---

    @Test
    fun testEventsListEmpty() {
        val kingdom = createMinimalKingdomData()
        val result = WorldAnvilExporter.generateEventsList(kingdom)
        assertEquals("[i]No active events.[/i]", result)
    }

    @Test
    fun testEventsListWithEvents() {
        val events = arrayOf(
            js("{ id: 'event-001', stage: 0 }"),
            js("{ id: 'event-002', stage: 1 }")
        )
        val kingdom = createMinimalKingdomData(ongoingEvents = events)
        val result = WorldAnvilExporter.generateEventsList(kingdom)
        assertTrue(result.contains("[list]"))
        assertTrue(result.contains("[*]event-001[br]"))
        assertTrue(result.contains("[*]event-002[br]"))
        assertTrue(result.contains("[/list]"))
    }

    // --- generateQuestsList tests ---

    @Test
    fun testQuestsListEmpty() {
        val kingdom = createMinimalKingdomData()
        val result = WorldAnvilExporter.generateQuestsList(kingdom)
        assertEquals("[i]No active quests.[/i]", result)
    }

    @Test
    fun testQuestsListFiltersActiveOnly() {
        val quests = arrayOf(
            js("{ id: 'q1', title: 'Active Quest', status: 'active' }"),
            js("{ id: 'q2', title: 'Completed Quest', status: 'completed' }"),
            js("{ id: 'q3', title: 'Another Active', status: 'active' }")
        )
        val kingdom = createMinimalKingdomData(quests = quests)
        val result = WorldAnvilExporter.generateQuestsList(kingdom)
        assertTrue(result.contains("Active Quest"))
        assertTrue(result.contains("Another Active"))
        assertTrue(!result.contains("Completed Quest"))
    }

    @Test
    fun testQuestsListWithNullQuests() {
        val kingdom = createMinimalKingdomData()  // quests defaults to null
        val result = WorldAnvilExporter.generateQuestsList(kingdom)
        assertEquals("[i]No active quests.[/i]", result)
    }

    // --- generateCompanionsList tests ---

    @Test
    fun testCompanionsListEmpty() {
        val kingdom = createMinimalKingdomData()
        val result = WorldAnvilExporter.generateCompanionsList(kingdom)
        assertEquals("[i]No companions recorded.[/i]", result)
    }

    @Test
    fun testCompanionsListWithCompanions() {
        val companions = arrayOf(
            js("{ name: 'Erin', role: 'companion', plotHook: 'A mysterious traveler' }"),
            js("{ name: 'Kira', role: 'npc', plotHook: '' }")
        )
        val kingdom = createMinimalKingdomData(companions = companions)
        val result = WorldAnvilExporter.generateCompanionsList(kingdom)
        assertTrue(result.contains("[table]"))
        assertTrue(result.contains("Erin"))
        assertTrue(result.contains("companion"))
        assertTrue(result.contains("A mysterious traveler"))
        assertTrue(result.contains("Kira"))
        assertTrue(result.contains("npc"))
        assertTrue(result.contains("[/table]"))
    }

    @Test
    fun testCompanionsListWithNullCompanions() {
        val kingdom = createMinimalKingdomData()
        val result = WorldAnvilExporter.generateCompanionsList(kingdom)
        assertEquals("[i]No companions recorded.[/i]", result)
    }

    // --- generateArmiesInfo tests ---

    @Test
    fun testArmiesInfoEmpty() {
        val kingdom = createMinimalKingdomData()
        val result = WorldAnvilExporter.generateArmiesInfo(kingdom)
        assertEquals("[i]No armies deployed.[/i]", result)
    }

    @Test
    fun testArmiesInfoWithArmies() {
        val armies = arrayOf(
            js("{ id: 'army-1', armyName: 'First Legion', armyType: 'infantry', status: 'deployed', deployedTurn: 5 }"),
            js("{ id: 'army-2', armyName: 'Scout Company', armyType: 'cavalry', status: 'garrisoned', deployedTurn: 3 }")
        )
        val kingdom = createMinimalKingdomData(armyDeployments = armies)
        val result = WorldAnvilExporter.generateArmiesInfo(kingdom)
        assertTrue(result.contains("[table]"))
        assertTrue(result.contains("[tr][th]Army[/th][th]Type[/th][th]Status[/th][/tr]"))
        assertTrue(result.contains("[tr][td]First Legion[/td][td]infantry[/td][td]deployed[/td][/tr]"))
        assertTrue(result.contains("[tr][td]Scout Company[/td][td]cavalry[/td][td]garrisoned[/td][/tr]"))
        assertTrue(result.contains("[/table]"))
    }

    @Test
    fun testArmiesInfoWithNullDeployments() {
        val kingdom = createMinimalKingdomData()
        val result = WorldAnvilExporter.generateArmiesInfo(kingdom)
        assertEquals("[i]No armies deployed.[/i]", result)
    }
}
