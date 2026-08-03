package at.posselt.pfrpg2e.camping

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecipeDataTest {

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

    @Test
    fun testRecipeHasLevel() {
        val recipe = testRecipe(level = 5)
        assertEquals(5, recipe.level)
    }

    @Test
    fun testRecipeHasRarity() {
        val common = testRecipe(rarity = "common")
        assertEquals("common", common.rarity)
        val uncommon = testRecipe(rarity = "uncommon")
        assertEquals("uncommon", uncommon.rarity)
        val rare = testRecipe(rarity = "rare")
        assertEquals("rare", rare.rarity)
        val unique = testRecipe(rarity = "unique")
        assertEquals("unique", unique.rarity)
    }

    @Test
    fun testRecipeHasCost() {
        val recipe = testRecipe(cost = RawCost(value = 10, currency = "gp"))
        assertEquals(10, recipe.cost.value)
        assertEquals("gp", recipe.cost.currency)
    }

    @Test
    fun testRecipeCostCurrencyValues() {
        val sp = testRecipe(cost = RawCost(value = 5, currency = "sp"))
        assertEquals("sp", sp.cost.currency)
        assertEquals(5, sp.cost.value)
    }

    @Test
    fun testSpecialMealFlag() {
        val special = testRecipe(isSpecialMeal = true)
        assertTrue(special.isSpecialMeal == true)
        val normal = testRecipe(isSpecialMeal = false)
        assertFalse(normal.isSpecialMeal == true)
    }

    @Test
    fun testHomebrewFlag() {
        val homebrew = testRecipe(isHomebrew = true)
        assertTrue(homebrew.isHomebrew == true)
        val official = testRecipe(isHomebrew = false)
        assertFalse(official.isHomebrew == true)
    }

    @Test
    fun testRecipeCostAccessibilityFields() {
        val recipe = testRecipe(
            level = 3,
            rarity = "uncommon",
            cost = RawCost(value = 8, currency = "gp")
        )
        assertEquals(3, recipe.level)
        assertEquals("uncommon", recipe.rarity)
        assertEquals(8, recipe.cost.value)
        assertEquals("gp", recipe.cost.currency)
    }

    @Test
    fun testAllRarityLevelsHaveDistinctValues() {
        val rarities = listOf("common", "uncommon", "rare", "unique")
        val recipes = rarities.mapIndexed { index, rarity ->
            testRecipe(
                id = "recipe-$rarity",
                rarity = rarity,
                level = index + 1
            )
        }
        recipes.forEachIndexed { index, recipe ->
            assertEquals(rarities[index], recipe.rarity)
            assertEquals(index + 1, recipe.level)
        }
    }

    @Test
    fun testRecipeWithDefaultSpecialIngredients() {
        val recipe = testRecipe(specialIngredients = 3)
        assertEquals(3, recipe.specialIngredients)
    }

    @Test
    fun testRecipeWithOptionalFieldsNull() {
        val recipe = testRecipe()
        assertEquals(null, recipe.icon)
    }

    @Test
    fun testRecipeRequirementsNullByDefault() {
        val recipe = testRecipe()
        assertEquals(null, recipe.requirements)
    }

    @Test
    fun testRecipeRequirementsWithValue() {
        val recipe = testRecipe(requirements = "master in Religion")
        assertEquals("master in Religion", recipe.requirements)
    }

    @Test
    fun testRecipeRequirementsWithEmptyString() {
        val recipe = testRecipe(requirements = "")
        assertEquals("", recipe.requirements)
    }

    // ==========================================
    // Action/reaction meal effect tests
    // ==========================================

    @Test
    fun testMealEffectWithGrantsFreeAction() {
        val freeAction = GrantedAction(
            type = "freeAction",
            name = "Dash",
            description = "Gain +5 Speed for 1 minute",
            durationSeconds = 60,
        )
        val effect = MealEffect(
            uuid = "test-uuid",
            grantsFreeAction = freeAction,
        )
        assertEquals("freeAction", effect.grantsFreeAction?.type)
        assertEquals("Dash", effect.grantsFreeAction?.name)
        assertEquals("Gain +5 Speed for 1 minute", effect.grantsFreeAction?.description)
        assertEquals(60, effect.grantsFreeAction?.durationSeconds)
    }

    @Test
    fun testMealEffectWithGrantsReaction() {
        val reaction = GrantedAction(
            type = "reaction",
            name = "Ignite Magic",
            description = "When an enemy critically fails a spell, deal 2d6 fire damage",
            durationSeconds = null,
        )
        val effect = MealEffect(
            uuid = "test-uuid",
            grantsReaction = reaction,
        )
        assertEquals("reaction", effect.grantsReaction?.type)
        assertEquals("Ignite Magic", effect.grantsReaction?.name)
        assertEquals("When an enemy critically fails a spell, deal 2d6 fire damage", effect.grantsReaction?.description)
        assertEquals(null, effect.grantsReaction?.durationSeconds)
    }

    @Test
    fun testMealEffectWithBothFreeActionAndReaction() {
        val freeAction = GrantedAction(
            type = "freeAction",
            name = "Quick Step",
            description = "+5 Speed",
            durationSeconds = 60,
        )
        val reaction = GrantedAction(
            type = "reaction",
            name = "Careful Casting",
            description = "DC 15 flat check to avoid spell disruption",
            durationSeconds = null,
        )
        val effect = MealEffect(
            uuid = "test-uuid",
            grantsFreeAction = freeAction,
            grantsReaction = reaction,
        )
        assertEquals("Quick Step", effect.grantsFreeAction?.name)
        assertEquals("Careful Casting", effect.grantsReaction?.name)
    }

    @Test
    fun testMealEffectWithoutActions() {
        val effect = MealEffect(uuid = "test-uuid")
        assertEquals(null, effect.grantsFreeAction)
        assertEquals(null, effect.grantsReaction)
    }

    @Test
    fun testMealEffectActionWithNullDuration() {
        val freeAction = GrantedAction(
            type = "freeAction",
            name = "Sprint",
            description = "Move quickly",
            durationSeconds = null,
        )
        val effect = MealEffect(
            uuid = "test-uuid",
            grantsFreeAction = freeAction,
        )
        assertEquals(null, effect.grantsFreeAction?.durationSeconds)
    }

    @Test
    fun testActionTypeEnumValues() {
        assertEquals("freeAction", ActionType.FREE_ACTION.value)
        assertEquals("singleAction", ActionType.SINGLE_ACTION.value)
        assertEquals("reaction", ActionType.REACTION.value)
    }

    @Test
    fun testActionTypeI18nKeys() {
        assertEquals("actionType.freeAction", ActionType.FREE_ACTION.i18nKey)
        assertEquals("actionType.singleAction", ActionType.SINGLE_ACTION.i18nKey)
        assertEquals("actionType.reaction", ActionType.REACTION.i18nKey)
    }

    @Test
    fun testCookingOutcomeWithActionEffects() {
        val freeAction = GrantedAction(
            type = "freeAction",
            name = "recipes.grilled-silver-eel.freeAction.name",
            description = "recipes.grilled-silver-eel.freeAction.description",
            durationSeconds = 60,
        )
        val effect = MealEffect(
            uuid = "Compendium.test.Item.abc123",
            grantsFreeAction = freeAction,
        )
        val outcome = CookingOutcome(
            effects = arrayOf(effect),
            chooseRandomly = null,
            message = null,
        )
        assertEquals(1, outcome.effects?.size)
        assertEquals("freeAction", outcome.effects?.get(0)?.grantsFreeAction?.type)
        assertEquals(60, outcome.effects?.get(0)?.grantsFreeAction?.durationSeconds)
    }

    @Test
    fun testCookingOutcomeWithReactionEffects() {
        val reaction = GrantedAction(
            type = "reaction",
            name = "recipes.succulent-sausages.reaction.name",
            description = "recipes.succulent-sausages.reaction.description",
        )
        val effect = MealEffect(
            uuid = "Compendium.test.Item.def456",
            grantsReaction = reaction,
        )
        val outcome = CookingOutcome(
            effects = arrayOf(effect),
            chooseRandomly = null,
            message = null,
        )
        assertEquals(1, outcome.effects?.size)
        assertEquals("reaction", outcome.effects?.get(0)?.grantsReaction?.type)
        assertEquals("recipes.succulent-sausages.reaction.name", outcome.effects?.get(0)?.grantsReaction?.name)
    }

    @Test
    fun testCookingOutcomeWithMultipleEffectsIncludingActions() {
        val freeAction = GrantedAction(
            type = "freeAction",
            name = "Boost Speed",
            description = "+5 Speed",
            durationSeconds = 60,
        )
        val reaction = GrantedAction(
            type = "reaction",
            name = "Counterspell",
            description = "React to enemy spells",
        )
        val effectWithFreeAction = MealEffect(
            uuid = "Compendium.test.Item.aaa",
            grantsFreeAction = freeAction,
        )
        val effectWithReaction = MealEffect(
            uuid = "Compendium.test.Item.bbb",
            grantsReaction = reaction,
        )
        val plainEffect = MealEffect(uuid = "Compendium.test.Item.ccc")
        val outcome = CookingOutcome(
            effects = arrayOf(effectWithFreeAction, effectWithReaction, plainEffect),
            chooseRandomly = null,
            message = null,
        )
        assertEquals(3, outcome.effects?.size)
        assertEquals("Boost Speed", outcome.effects?.get(0)?.grantsFreeAction?.name)
        assertEquals("Counterspell", outcome.effects?.get(1)?.grantsReaction?.name)
        assertEquals(null, outcome.effects?.get(2)?.grantsFreeAction)
        assertEquals(null, outcome.effects?.get(2)?.grantsReaction)
    }

    @Test
    fun testSeasonedWingsAndThighsReactionInOutcome() {
        // Seasoned Wings and Thighs grants a reaction: Ignite Magic (2d6 fire on ally's spell crit fail)
        val reaction = GrantedAction(
            type = "reaction",
            name = "recipes.seasoned-wings-and-thighs.reaction.name",
            description = "recipes.seasoned-wings-and-thighs.reaction.description",
        )
        val effect = MealEffect(
            uuid = "Compendium.test.Item.seasoned",
            grantsReaction = reaction,
        )
        val outcome = CookingOutcome(
            effects = arrayOf(effect),
            chooseRandomly = null,
            message = null,
        )
        assertEquals(1, outcome.effects?.size)
        assertEquals("reaction", outcome.effects?.get(0)?.grantsReaction?.type)
        assertEquals(
            "recipes.seasoned-wings-and-thighs.reaction.name",
            outcome.effects?.get(0)?.grantsReaction?.name,
        )
        assertEquals(
            "recipes.seasoned-wings-and-thighs.reaction.description",
            outcome.effects?.get(0)?.grantsReaction?.description,
        )
        assertEquals(null, outcome.effects?.get(0)?.grantsReaction?.durationSeconds)
        assertEquals(null, outcome.effects?.get(0)?.grantsFreeAction)
    }

    @Test
    fun testGrilledSilverEelFreeActionInOutcome() {
        // Grilled Silver Eel grants a free action: +5 Speed for 1 minute
        val freeAction = GrantedAction(
            type = "freeAction",
            name = "recipes.grilled-silver-eel.freeAction.name",
            description = "recipes.grilled-silver-eel.freeAction.description",
            durationSeconds = 60,
        )
        val effect = MealEffect(
            uuid = "Compendium.test.Item.eel",
            grantsFreeAction = freeAction,
        )
        val outcome = CookingOutcome(
            effects = arrayOf(effect),
            chooseRandomly = null,
            message = null,
        )
        assertEquals(1, outcome.effects?.size)
        assertEquals("freeAction", outcome.effects?.get(0)?.grantsFreeAction?.type)
        assertEquals(60, outcome.effects?.get(0)?.grantsFreeAction?.durationSeconds)
        assertEquals(null, outcome.effects?.get(0)?.grantsReaction)
    }
}
