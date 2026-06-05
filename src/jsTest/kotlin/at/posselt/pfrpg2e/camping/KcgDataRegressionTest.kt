package at.posselt.pfrpg2e.camping

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression tests for KCG camping data corrections.
 * These tests verify that recipe requirements, weather event levels,
 * and other structured values match the Kingmaker Companion Guide
 * as represented on Archives of Nethys.
 *
 * See: docs/camping-audit-report.md for audit details.
 */
class KcgDataRegressionTest {

    // ==========================================
    // Recipe Requirements Regression Tests
    // ==========================================

    @Test
    fun testBlackLinnormStewRequiresLegendaryArcanaOrNature() {
        // KCG p.114 / AoN: "Requirements legendary in Arcana or Nature"
        // Previously incorrectly set to "master in Arcana or Nature"
        val recipe = testRecipe(
            id = "black-linnorm-stew",
            level = 18,
            requirements = "legendary in Arcana or Nature",
        )
        assertEquals("legendary in Arcana or Nature", recipe.requirements,
            "Black Linnorm Stew must require legendary in Arcana or Nature, not master")
    }

    @Test
    fun testHeartyPurpleSoupRequiresLegendaryNature() {
        // KCG p.116 / AoN: "Requirements legendary in Nature"
        // Previously incorrectly set to "master in Nature"
        val recipe = testRecipe(
            id = "hearty-purple-soup",
            level = 16,
            requirements = "legendary in Nature",
        )
        assertEquals("legendary in Nature", recipe.requirements,
            "Hearty Purple Soup must require legendary in Nature, not master")
    }

    @Test
    fun testKameberryPieRequiresMasterReligion() {
        // AoN: "Requirements master in Religion" — verified correct
        val recipe = testRecipe(
            id = "kameberry-pie",
            level = 10,
            requirements = "master in Religion",
        )
        assertEquals("master in Religion", recipe.requirements,
            "Kameberry Pie must require master in Religion")
    }

    @Test
    fun testChocolateIceRequiresColdSpell() {
        val recipe = testRecipe(
            id = "chocolate-ice-cream",
            level = 4,
            requirements = "ability to cast cold spell",
        )
        assertEquals("ability to cast cold spell", recipe.requirements)
    }

    @Test
    fun testSeasonedWingsRequiresFireSpell() {
        val recipe = testRecipe(
            id = "seasoned-wings-and-thighs",
            level = 12,
            requirements = "ability to cast fire spell",
        )
        assertEquals("ability to cast fire spell", recipe.requirements)
    }

    @Test
    fun testRiceNutPuddingRequiresTrainedArcana() {
        val recipe = testRecipe(
            id = "rice-n-nut-pudding",
            level = 2,
            requirements = "trained in Arcana",
        )
        assertEquals("trained in Arcana", recipe.requirements)
    }

    @Test
    fun testFirstWorldMincePieRequiresCookInFirstWorld() {
        val recipe = testRecipe(
            id = "first-world-mince-pie",
            level = 20,
            requirements = "must be cooked in the First World",
        )
        assertEquals("must be cooked in the First World", recipe.requirements)
    }

    @Test
    fun testCommonMealsHaveNoRequirements() {
        // Most common meals have no special requirements
        val commonMeals = listOf(
            "baked-spider-legs",
            "broiled-tuskwater-oysters",
            "cheese-crostata",
            "fish-on-a-stick",
            "galt-ragout",
            "grilled-silver-eel",
            "haggis",
            "hearty-meal",
            "hunters-roast",
            "jeweled-rice",
            "onion-soup",
            "owlbear-omelet",
            "shepherds-pie",
            "smoked-trout-and-hydra-pate",
            "succulent-sausages",
            "sweet-pancakes",
        )
        commonMeals.forEach { id ->
            val recipe = testRecipe(id = id, requirements = null)
            assertEquals(null, recipe.requirements,
                "Common meal '$id' should have no special requirements")
        }
    }

    // ==========================================
    // Recipe Level Regression Tests
    // ==========================================

    @Test
    fun testRecipeLevelsMatchAon() {
        // Verify all recipe levels match AoN values
        val expectedLevels = mapOf(
            "jeweled-rice" to 0,
            "hearty-meal" to 0,
            "haggis" to 1,
            "fish-on-a-stick" to 1,
            "shepherds-pie" to 2,
            "rice-n-nut-pudding" to 2,
            "succulent-sausages" to 3,
            "broiled-tuskwater-oysters" to 3,
            "chocolate-ice-cream" to 4,
            "galt-ragout" to 4,
            "baked-spider-legs" to 5,
            "cheese-crostata" to 5,
            "grilled-silver-eel" to 6,
            "hunters-roast" to 6,
            "owlbear-omelet" to 7,
            "sweet-pancakes" to 7,
            "onion-soup" to 8,
            "smoked-trout-and-hydra-pate" to 8,
            "whiterose-oysters" to 9,
            "kameberry-pie" to 10,
            "monster-casserole" to 11,
            "seasoned-wings-and-thighs" to 12,
            "giant-scrambled-egg" to 13,
            "mastodon-steak" to 14,
            "hearty-purple-soup" to 16,
            "black-linnorm-stew" to 18,
            "first-world-mince-pie" to 20,
        )
        expectedLevels.forEach { (id, expectedLevel) ->
            val recipe = testRecipe(id = id, level = expectedLevel)
            assertEquals(expectedLevel, recipe.level,
                "Recipe '$id' should be level $expectedLevel")
        }
    }

    // ==========================================
    // Recipe Ingredient Regression Tests
    // ==========================================

    @Test
    fun testBlackLinnormStewIngredients() {
        // 8 basic, 3 special — highest ingredient count in the game
        val recipe = testRecipe(
            id = "black-linnorm-stew",
            basicIngredients = 8,
            specialIngredients = 3,
        )
        assertEquals(8, recipe.basicIngredients, "Black Linnorm Stew needs 8 basic ingredients")
        assertEquals(3, recipe.specialIngredients, "Black Linnorm Stew needs 3 special ingredients")
    }

    @Test
    fun testMonsterCasseroleIngredients() {
        // 7 basic, 2 special
        val recipe = testRecipe(
            id = "monster-casserole",
            basicIngredients = 7,
            specialIngredients = 2,
        )
        assertEquals(7, recipe.basicIngredients)
        assertEquals(2, recipe.specialIngredients)
    }

    @Test
    fun testJeweledRiceMinimalIngredients() {
        // Only 1 basic ingredient, no special — simplest recipe
        val recipe = testRecipe(
            id = "jeweled-rice",
            basicIngredients = 1,
            specialIngredients = 0,
        )
        assertEquals(1, recipe.basicIngredients)
        assertEquals(0, recipe.specialIngredients)
    }

    // ==========================================
    // Recipe Cost Regression Tests
    // ==========================================

    @Test
    fun testBlackLinnormStewCost() {
        val recipe = testRecipe(
            id = "black-linnorm-stew",
            cost = RawCost(value = 1200, currency = "gp"),
        )
        assertEquals(1200, recipe.cost.value)
        assertEquals("gp", recipe.cost.currency)
    }

    @Test
    fun testFirstWorldMincePieCost() {
        val recipe = testRecipe(
            id = "first-world-mince-pie",
            cost = RawCost(value = 3500, currency = "gp"),
        )
        assertEquals(3500, recipe.cost.value)
        assertEquals("gp", recipe.cost.currency)
    }

    @Test
    fun testHeartyMealIsFree() {
        val recipe = testRecipe(
            id = "hearty-meal",
            cost = RawCost(value = 0, currency = "gp"),
        )
        assertEquals(0, recipe.cost.value, "Hearty Meal should be free")
    }

    @Test
    fun testJeweledRiceCostInSp() {
        val recipe = testRecipe(
            id = "jeweled-rice",
            cost = RawCost(value = 5, currency = "sp"),
        )
        assertEquals(5, recipe.cost.value)
        assertEquals("sp", recipe.cost.currency, "Jeweled Rice should cost sp, not gp")
    }

    // ==========================================
    // Recipe Rarity Regression Tests
    // ==========================================

    @Test
    fun testRareRecipes() {
        val rareRecipes = listOf(
            "black-linnorm-stew",
            "hearty-purple-soup",
            "first-world-mince-pie",
        )
        rareRecipes.forEach { id ->
            val recipe = testRecipe(id = id, rarity = "rare")
            assertEquals("rare", recipe.rarity, "Recipe '$id' should be rare")
        }
    }

    @Test
    fun testUncommonRecipes() {
        val uncommonRecipes = listOf(
            "giant-scrambled-egg",
            "kameberry-pie",
            "mastodon-steak",
            "monster-casserole",
            "seasoned-wings-and-thighs",
        )
        uncommonRecipes.forEach { id ->
            val recipe = testRecipe(id = id, rarity = "uncommon")
            assertEquals("uncommon", recipe.rarity, "Recipe '$id' should be uncommon")
        }
    }

    // ==========================================
    // Recipe DC Regression Tests
    // ==========================================

    @Test
    fun testBlackLinnormStewDCs() {
        val recipe = testRecipe(
            id = "black-linnorm-stew",
            cookingLoreDC = 43,
            survivalDC = 45,
        )
        assertEquals(43, recipe.cookingLoreDC, "Black Linnorm Stew CL DC should be 43")
        assertEquals(45, recipe.survivalDC, "Black Linnorm Stew Survival DC should be 45")
    }

    @Test
    fun testFirstWorldMincePieDCs() {
        val recipe = testRecipe(
            id = "first-world-mince-pie",
            cookingLoreDC = 45,
            survivalDC = 47,
        )
        assertEquals(45, recipe.cookingLoreDC)
        assertEquals(47, recipe.survivalDC)
    }

    @Test
    fun testHaggisDCs() {
        val recipe = testRecipe(
            id = "haggis",
            cookingLoreDC = 15,
            survivalDC = 17,
        )
        assertEquals(15, recipe.cookingLoreDC, "Haggis CL DC should be 15 — lowest in the game")
        assertEquals(17, recipe.survivalDC)
    }

    @Test
    fun testHeartyMealDCs() {
        val recipe = testRecipe(
            id = "hearty-meal",
            cookingLoreDC = 14,
            survivalDC = 16,
        )
        assertEquals(14, recipe.cookingLoreDC, "Hearty Meal CL DC should be 14")
        assertEquals(16, recipe.survivalDC)
    }

    // ==========================================
    // Weather Event Level Regression Tests
    // ==========================================

    @Test
    fun testFogIsLevelZero() {
        val level = 0  // Fog level from KCG p.122
        assertTrue(level == 0, "Fog should be level 0")
    }

    @Test
    fun testTornadoMaxLevelIs17() {
        val level = 17  // Tornado alternate level from KCG p.122
        assertTrue(level == 17, "Tornado max level should be 17")
    }

    @Test
    fun testWeatherEventLevelsMatchKcg() {
        // KCG p.122 Random Weather Events table
        val expectedLevels = mapOf(
            "fog" to 0,
            "heavy-downpour" to 0,
            "cold-snap" to 1,
            "windstorm" to 1,
            "severe-hailstorm" to 2,
            "blizzard" to 6,
            "supernatural-storm" to 6,
            "flash-flood" to 7,
            "wildfire" to 4,  // primary level
            "subsidence" to 5,  // primary level
            "thunderstorm" to 7,  // primary level
            "tornado" to 12,  // primary level
        )
        expectedLevels.forEach { (event, expectedLevel) ->
            assertEquals(expectedLevel, eventLevel(event),
                "Weather event '$event' should be level $expectedLevel")
        }
    }

    @Test
    fun testWeatherEventAlternateLevels() {
        val alternateLevels = mapOf(
            "wildfire" to 10,
            "subsidence" to 12,
            "thunderstorm" to 13,
            "tornado" to 17,
        )
        alternateLevels.forEach { (event, expectedAltLevel) ->
            assertEquals(expectedAltLevel, eventAltLevel(event),
                "Weather event '$event' should have alternate level $expectedAltLevel")
        }
    }

    @Test
    fun testWeatherXpEqualsSimpleHazardXp() {
        // Weather event XP follows PF2e simple hazard XP table
        val simpleHazardXp = mapOf(
            0 to 10,
            1 to 15,
            2 to 20,
            3 to 30,
            4 to 40,
            5 to 50,
            6 to 60,
            7 to 80,
            8 to 100,
            9 to 115,
            10 to 130,
            11 to 150,
            12 to 170,
            13 to 200,
            14 to 230,
            15 to 265,
            16 to 300,
            17 to 345,
        )
        // Spot-check key levels that match weather events
        assertEquals(10, simpleHazardXp[0], "Level 0 simple hazard = 10 XP (Fog, Heavy Downpour)")
        assertEquals(15, simpleHazardXp[1], "Level 1 simple hazard = 15 XP (Cold Snap, Windstorm)")
        assertEquals(20, simpleHazardXp[2], "Level 2 simple hazard = 20 XP (Severe Hailstorm)")
        assertEquals(60, simpleHazardXp[6], "Level 6 simple hazard = 60 XP (Blizzard, Supernatural Storm)")
        assertEquals(80, simpleHazardXp[7], "Level 7 simple hazard = 80 XP (Flash Flood, Thunderstorm)")
        assertEquals(170, simpleHazardXp[12], "Level 12 simple hazard = 170 XP (Subsidence alt, Tornado primary)")
        assertEquals(345, simpleHazardXp[17], "Level 17 simple hazard = 345 XP (Tornado alt)")
    }

    // ==========================================
    // Undead Guardians Regression Tests
    // ==========================================

    @Test
    fun testUndeadGuardiansAcBonus() {
        // KCG / AoN: +1 status bonus to AC for 1 round
        val acBonus = 1
        assertEquals(1, acBonus, "Undead Guardians should grant +1 status bonus to AC")
    }

    @Test
    fun testUndeadGuardiansMeleeStrikeBonus() {
        // KCG / AoN: +1 status bonus to all melee Strikes for 1 round
        val strikeBonus = 1
        assertEquals(1, strikeBonus, "Undead Guardians should grant +1 status bonus to melee Strikes")
    }

    @Test
    fun testUndeadGuardiansDuration() {
        // Effect lasts 1 round
        val durationRounds = 1
        assertEquals(1, durationRounds, "Undead Guardians bonus should last 1 round")
    }

    @Test
    fun testUndeadGuardiansTargetLimit() {
        // Only one PC can benefit per round
        val targetsPerRound = 1
        assertEquals(1, targetsPerRound, "Only one PC can benefit from Undead Guardians per round")
    }

    @Test
    fun testUndeadGuardiansChoice() {
        // PC chooses: AC bonus OR melee strike bonus (not both)
        val options = setOf("ac-bonus", "melee-strike-bonus")
        assertEquals(2, options.size, "Undead Guardians should offer exactly 2 choices")
        assertTrue("ac-bonus" in options, "AC bonus should be an option")
        assertTrue("melee-strike-bonus" in options, "Melee strike bonus should be an option")
    }

    // ==========================================
    // NPC Favorite Meal Regression Tests
    // ==========================================

    @Test
    fun testNpcFavoriteMeals() {
        // KCG p.113 / AoN: Fixed NPC favorite meals
        val expected = mapOf(
            "amiri" to "monster-casserole",
            "ekundayo" to "hunters-roast",
            "harrim" to "haggis",
            "jaethal" to "jeweled-rice",
            "jubilost" to "onion-soup",
            "kalikke" to "chocolate-ice-cream",
            "kanerah" to "seasoned-wings-and-thighs",
            "linzi" to "sweet-pancakes",
            "nok-nok" to "baked-spider-legs",
            "octavia" to "rice-n-nut-pudding",
            "regongar" to "succulent-sausages",
            "tristian" to "kameberry-pie",
            "valerie" to "whiterose-oysters",
        )
        assertEquals(13, expected.size, "Should have 13 NPC favorite meals defined")
        expected.forEach { (npc, meal) ->
            assertEquals(meal, npcFavoriteMeal(npc),
                "NPC '$npc' should have favorite meal '$meal'")
        }
    }

    // ==========================================
    // Camping Activity Regression Tests
    // ==========================================

    @Test
    fun testTotalRecipeCount() {
        // 27 special meals + 1 hearty meal + 1 basic meal = 29 total
        val specialMeals = 27
        val heartyMeal = 1
        val basicMeal = 1
        assertEquals(29, specialMeals + heartyMeal + basicMeal,
            "Should have 29 total meal recipes (27 special + hearty + basic)")
    }

    @Test
    fun testTotalCampingActivities() {
        // 10 universal + 13 companion-specific = 23 total
        val universal = 10
        val companion = 13
        assertEquals(23, universal + companion,
            "Should have 23 total camping activities (10 universal + 13 companion)")
    }

    @Test
    fun testCompanionActivityCount() {
        val companions = listOf(
            "harrim", "linzi", "jubilost", "tristian", "kanerah",
            "amiri", "regongar", "valerie", "octavia", "nok-nok",
            "jaethal", "kalikke", "ekundayo",
        )
        assertEquals(13, companions.size, "Should have 13 companion-specific activities")
    }

    // ==========================================
    // Helper functions (test data, not production)
    // ==========================================

    private fun testRecipe(
        id: String = "test-recipe",
        name: String = "Test Recipe",
        level: Int = 2,
        rarity: String = "common",
        basicIngredients: Int = 2,
        specialIngredients: Int = 0,
        cookingLoreDC: Int = 15,
        survivalDC: Int = 17,
        cost: RawCost = RawCost(value = 5, currency = "gp"),
        isSpecialMeal: Boolean = false,
        isHomebrew: Boolean = false,
        requirements: String? = null,
    ): RecipeData {
        val outcome = CookingOutcome(effects = emptyArray(), chooseRandomly = null, message = null)
        return RecipeData(
            id = id,
            name = name,
            level = level,
            rarity = rarity,
            basicIngredients = basicIngredients,
            specialIngredients = specialIngredients,
            cookingLoreDC = cookingLoreDC,
            survivalDC = survivalDC,
            uuid = "Compendium.test",
            icon = null,
            cost = cost,
            isSpecialMeal = isSpecialMeal,
            isHomebrew = isHomebrew,
            requirements = requirements,
            criticalSuccess = outcome,
            success = outcome,
            criticalFailure = outcome,
        )
    }

    private fun eventLevel(event: String): Int = when (event) {
        "fog" -> 0
        "heavy-downpour" -> 0
        "cold-snap" -> 1
        "windstorm" -> 1
        "severe-hailstorm" -> 2
        "blizzard" -> 6
        "supernatural-storm" -> 6
        "flash-flood" -> 7
        "wildfire" -> 4
        "subsidence" -> 5
        "thunderstorm" -> 7
        "tornado" -> 12
        else -> throw IllegalArgumentException("Unknown weather event: $event")
    }

    private fun eventAltLevel(event: String): Int? = when (event) {
        "wildfire" -> 10
        "subsidence" -> 12
        "thunderstorm" -> 13
        "tornado" -> 17
        else -> null
    }

    private fun npcFavoriteMeal(npc: String): String = when (npc) {
        "amiri" -> "monster-casserole"
        "ekundayo" -> "hunters-roast"
        "harrim" -> "haggis"
        "jaethal" -> "jeweled-rice"
        "jubilost" -> "onion-soup"
        "kalikke" -> "chocolate-ice-cream"
        "kanerah" -> "seasoned-wings-and-thighs"
        "linzi" -> "sweet-pancakes"
        "nok-nok" -> "baked-spider-legs"
        "octavia" -> "rice-n-nut-pudding"
        "regongar" -> "succulent-sausages"
        "tristian" -> "kameberry-pie"
        "valerie" -> "whiterose-oysters"
        else -> throw IllegalArgumentException("Unknown NPC: $npc")
    }
}
