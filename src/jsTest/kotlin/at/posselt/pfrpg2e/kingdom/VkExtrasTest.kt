package at.posselt.pfrpg2e.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import at.posselt.pfrpg2e.kingdom.data.*
import at.posselt.pfrpg2e.kingdom.sheet.createKingdomDefaults

class VkExtrasTest {

    private fun createTestKingdom(
        charterExtra: Boolean? = null,
        heartlandExtra: Boolean? = null,
        extraBoost: Boolean? = null,
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
            increaseScorePicksBy = 0
            realmSceneId = null
            xpPerClaimedHex = 0
            includeCapitalItemModifier = false
            cultOfTheBloomEvents = false
            autoCalculateSettlementLevel = false
            vanceAndKerensharaXP = false
            capitalInvestmentInCapital = false
            reduceDCToBuildLumberStructures = false
            kingdomSkillIncreaseEveryLevel = false
            kingdomAllStructureItemBonusesStack = false
            kingdomIgnoreSkillRequirements = false
            autoCalculateArmyConsumption = false
            enableLeadershipModifiers = false
            expandMagicUse = false
            kingdomEventRollMode = "none"
            automateResources = "none"
            proficiencyMode = "none"
            kingdomEventsTable = null
            kingdomCultTable = null
            maximumFamePoints = 3
            leaderKingdomSkills = js("{}").unsafeCast<RawLeaderKingdomSkills>()
            leaderSkills = js("{}").unsafeCast<RawLeaderSkills>()
            automateStats = false
            recruitableArmiesFolderId = null
            eventDc = 10
            eventDcStep = 0
            cultEventDc = 10
            cultEventDcStep = 0
            partialStructureConstruction = false
            capStructureBonusAtKingdomLevel = false
            capitalCanGrowOneSizeLarger = false
            enableCouncilMissions = false
            autoGainFamePerTurn = false
            vkCharterExtraSkills = charterExtra
            vkHeartlandExtraSkills = heartlandExtra
            vkExtraAbilityBoost = extraBoost
        }
        return kingdom
    }

    // --- Default values ---

    @Test
    fun defaultSettingsHaveAllVkExtrasDisabled() {
        val defaults = createKingdomDefaults("Test")
        assertFalse(
            defaults.settings.vkCharterExtraSkills == true,
            "vkCharterExtraSkills should default to false"
        )
        assertFalse(
            defaults.settings.vkHeartlandExtraSkills == true,
            "vkHeartlandExtraSkills should default to false"
        )
        assertFalse(
            defaults.settings.vkExtraAbilityBoost == true,
            "vkExtraAbilityBoost should default to false"
        )
    }

    @Test
    fun vkExtrasAcceptNullValues() {
        val kingdom = createTestKingdom()
        assertNull(kingdom.settings.vkCharterExtraSkills, "vkCharterExtraSkills should accept null")
        assertNull(kingdom.settings.vkHeartlandExtraSkills, "vkHeartlandExtraSkills should accept null")
        assertNull(kingdom.settings.vkExtraAbilityBoost, "vkExtraAbilityBoost should accept null")
    }

    // --- Individual toggle tests ---

    @Test
    fun vkCharterExtraSkillsCanBeEnabledIndependently() {
        val kingdom = createTestKingdom(charterExtra = true)
        assertTrue(kingdom.settings.vkCharterExtraSkills == true)
        assertFalse(kingdom.settings.vkHeartlandExtraSkills == true)
        assertFalse(kingdom.settings.vkExtraAbilityBoost == true)
    }

    @Test
    fun vkHeartlandExtraSkillsCanBeEnabledIndependently() {
        val kingdom = createTestKingdom(heartlandExtra = true)
        assertFalse(kingdom.settings.vkCharterExtraSkills == true)
        assertTrue(kingdom.settings.vkHeartlandExtraSkills == true)
        assertFalse(kingdom.settings.vkExtraAbilityBoost == true)
    }

    @Test
    fun vkExtraAbilityBoostCanBeEnabledIndependently() {
        val kingdom = createTestKingdom(extraBoost = true)
        assertFalse(kingdom.settings.vkCharterExtraSkills == true)
        assertFalse(kingdom.settings.vkHeartlandExtraSkills == true)
        assertTrue(kingdom.settings.vkExtraAbilityBoost == true)
    }

    // --- Combination tests ---

    @Test
    fun allThreeVkExtrasCanBeEnabledTogether() {
        val kingdom = createTestKingdom(
            charterExtra = true,
            heartlandExtra = true,
            extraBoost = true,
        )
        assertTrue(kingdom.settings.vkCharterExtraSkills == true)
        assertTrue(kingdom.settings.vkHeartlandExtraSkills == true)
        assertTrue(kingdom.settings.vkExtraAbilityBoost == true)
    }

    @Test
    fun charterAndHeartlandExtrasCanBeEnabledWithoutAbilityBoost() {
        val kingdom = createTestKingdom(
            charterExtra = true,
            heartlandExtra = true,
            extraBoost = false,
        )
        assertTrue(kingdom.settings.vkCharterExtraSkills == true)
        assertTrue(kingdom.settings.vkHeartlandExtraSkills == true)
        assertFalse(kingdom.settings.vkExtraAbilityBoost == true)
    }

    @Test
    fun abilityBoostCanBeEnabledWithoutSkillExtras() {
        val kingdom = createTestKingdom(
            charterExtra = false,
            heartlandExtra = false,
            extraBoost = true,
        )
        assertFalse(kingdom.settings.vkCharterExtraSkills == true)
        assertFalse(kingdom.settings.vkHeartlandExtraSkills == true)
        assertTrue(kingdom.settings.vkExtraAbilityBoost == true)
    }

    @Test
    fun allThreeVkExtrasCanBeExplicitlyDisabled() {
        val kingdom = createTestKingdom(
            charterExtra = false,
            heartlandExtra = false,
            extraBoost = false,
        )
        assertFalse(kingdom.settings.vkCharterExtraSkills == true)
        assertFalse(kingdom.settings.vkHeartlandExtraSkills == true)
        assertFalse(kingdom.settings.vkExtraAbilityBoost == true)
    }

    // --- Skill slot derivation logic (exercises production vkExtraSkillSlots / vkInitialSkillSlots) ---

    @Test
    fun noVkExtrasMeans4BaseSkillSlots() {
        val kingdom = createTestKingdom()
        assertEquals(0, vkExtraSkillSlots(kingdom.settings), "No V&K extras means 0 extra slots")
        assertEquals(4, vkInitialSkillSlots(kingdom.settings), "Base game should have 4 skill slots")
    }

    @Test
    fun vkCharterExtraSkillsAddsOneExtraSkillSlot() {
        val kingdom = createTestKingdom(charterExtra = true)
        assertEquals(1, vkExtraSkillSlots(kingdom.settings))
        assertEquals(5, vkInitialSkillSlots(kingdom.settings), "Charter extra should give 5 skill slots")
    }

    @Test
    fun vkHeartlandExtraSkillsAddsOneExtraSkillSlot() {
        val kingdom = createTestKingdom(heartlandExtra = true)
        assertEquals(1, vkExtraSkillSlots(kingdom.settings))
        assertEquals(5, vkInitialSkillSlots(kingdom.settings), "Heartland extra should give 5 skill slots")
    }

    @Test
    fun bothSkillExtrasGive6TotalSkillSlots() {
        val kingdom = createTestKingdom(
            charterExtra = true,
            heartlandExtra = true,
        )
        assertEquals(2, vkExtraSkillSlots(kingdom.settings))
        assertEquals(6, vkInitialSkillSlots(kingdom.settings), "Both extras should give 6 skill slots")
    }

    // --- Ability boost derivation logic (exercises production vkExtraAbilityBoosts) ---

    @Test
    fun noVkExtraAbilityBoostMeans2BaseAbilityBoosts() {
        val kingdom = createTestKingdom()
        assertEquals(0, vkExtraAbilityBoosts(kingdom.settings), "No V&K extra means 0 extra boosts")
        assertEquals(
            2,
            BASE_ABILITY_BOOSTS + vkExtraAbilityBoosts(kingdom.settings),
            "Base game should have 2 ability boosts",
        )
    }

    @Test
    fun vkExtraAbilityBoostAddsOneExtraAbilityBoost() {
        val kingdom = createTestKingdom(extraBoost = true)
        assertEquals(1, vkExtraAbilityBoosts(kingdom.settings))
        assertEquals(
            3,
            BASE_ABILITY_BOOSTS + vkExtraAbilityBoosts(kingdom.settings),
            "V&K extra should give 3 ability boosts",
        )
    }

    // --- Interaction with vanceAndKerensharaXP ---

    @Test
    fun vkExtrasWorkIndependentlyOfVanceAndKerensharaXPSetting() {
        val kingdom = createTestKingdom(
            charterExtra = true,
            heartlandExtra = true,
            extraBoost = true,
        )
        assertFalse(kingdom.settings.vanceAndKerensharaXP == true)
        assertTrue(kingdom.settings.vkCharterExtraSkills == true)
        assertTrue(kingdom.settings.vkHeartlandExtraSkills == true)
        assertTrue(kingdom.settings.vkExtraAbilityBoost == true)
    }
}
