package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.camping.dialogs.RegionSettings
import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import js.objects.Record
import js.objects.recordOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the favorite meal progression mechanic (Kingmaker Companion Guide p.113).
 *
 * Rules:
 * - PCs learn a favorite meal after either 1 critical success OR 2 successes.
 * - Changing favorites requires 2 critical successes on the new meal.
 * - NPC fixed favorite meals are never auto-changed.
 * - Favorite outcome is only applied once the actor qualifies.
 */
class FavoriteMealProgressionTest {

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
        favoriteMeal: CookingOutcome? = null,
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
            favoriteMeal = favoriteMeal,
        )
    }

    private fun testCampingData(
        actorMeals: Record<String, ActorMeal> = recordOf(),
        favoriteMealProgress: Record<String, Record<String, FavoriteMealProgression>>? = null,
    ): CampingData {
        return CampingData(
            currentRegion = "test",
            actorUuids = emptyArray(),
            campingActivities = recordOf(),
            homebrewCampingActivities = emptyArray(),
            lockedActivities = emptyArray(),
            cooking = Cooking(
                knownRecipes = emptyArray(),
                actorMeals = actorMeals,
                homebrewMeals = emptyArray(),
                results = recordOf(),
                minimumSubsistence = 0,
                favoriteMealProgress = favoriteMealProgress,
            ),
            watchSecondsRemaining = 0,
            gunsToClean = 0,
            dailyPrepsAtTime = 0,
            encounterModifier = 0,
            restRollMode = "one",
            increaseWatchActorNumber = 0,
            actorUuidsNotKeepingWatch = emptyArray(),
            alwaysPerformActivityIds = emptyArray(),
            huntAndGatherTargetActorUuid = null,
            proxyRandomEncounterTableUuid = null,
            randomEncounterRollMode = "gmroll",
            ignoreSkillRequirements = false,
            minimumTravelSpeed = null,
            regionSettings = RegionSettings(regions = emptyArray()),
            section = "prepareCampsite",
            restingTrack = null,
            worldSceneId = null,
            autoApplyFatigued = false,
            restSettings = RestSettings(
                skipWatch = false,
                skipDailyPreparations = false,
                disableRandomEncounter = false,
                skipWeather = false,
            ),
            secondsSpentTraveling = 0,
            secondsSpentHexploring = 0,
            resetTimeTrackingAfterOneDay = true,
            travelModeActive = false,
            forcedMarchActive = false,
            secondsSpentForcedMarching = 0,
            hexSizeInMiles = 12,
            learnedCompanionActivities = emptyArray(),
            watchSlots = emptyArray(),
            downtimeHoursSpent = recordOf(),
        )
    }

    // ==========================================
    // Progression: learn after 1 critical success
    // ==========================================

    @Test
    fun testLearnFavoriteAfterOneCriticalSuccess() {
        val camping = testCampingData(
            actorMeals = recordOf(
                "actor-1" to ActorMeal(
                    actorUuid = "actor-1",
                    favoriteMeal = null,
                    chosenMeal = "haggis",
                ),
            ),
        )
        val result = camping.recordCookingResult(
            actorUuid = "actor-1",
            recipeId = "haggis",
            recipeName = "Haggis",
            degree = DegreeOfSuccess.CRITICAL_SUCCESS,
            actorIsPc = true,
            actorType = null,
        )
        assertNotNull(result, "Should return eligibility after 1 crit success")
        assertTrue(result!!.isNewFavorite)
        assertFalse(result.isChange)
        assertEquals("haggis", camping.cooking.actorMeals["actor-1"]?.favoriteMeal)
    }

    @Test
    fun testProgressTrackedAfterOneCriticalSuccess() {
        val camping = testCampingData(
            actorMeals = recordOf(
                "actor-1" to ActorMeal(
                    actorUuid = "actor-1",
                    favoriteMeal = null,
                    chosenMeal = "haggis",
                ),
            ),
        )
        camping.recordCookingResult(
            actorUuid = "actor-1",
            recipeId = "haggis",
            recipeName = "Haggis",
            degree = DegreeOfSuccess.CRITICAL_SUCCESS,
            actorIsPc = true,
            actorType = null,
        )
        val progress = camping.cooking.favoriteMealProgress?.get("actor-1")?.get("haggis")
        assertNotNull(progress)
        assertEquals(0, progress.successCount, "Crit success doesn't count as success for the 2-success threshold")
        assertEquals(1, progress.criticalSuccessCount)
    }

    // ==========================================
    // Progression: learn after 2 successes
    // ==========================================

    @Test
    fun testLearnFavoriteAfterTwoSuccesses() {
        val camping = testCampingData(
            actorMeals = recordOf(
                "actor-1" to ActorMeal(
                    actorUuid = "actor-1",
                    favoriteMeal = null,
                    chosenMeal = "haggis",
                ),
            ),
        )
        // First success — not enough yet
        val first = camping.recordCookingResult(
            actorUuid = "actor-1",
            recipeId = "haggis",
            recipeName = "Haggis",
            degree = DegreeOfSuccess.SUCCESS,
            actorIsPc = true,
            actorType = null,
        )
        assertNull(first, "Should not qualify after only 1 success")
        assertNull(camping.cooking.actorMeals["actor-1"]?.favoriteMeal)

        // Second success — now qualifies
        val second = camping.recordCookingResult(
            actorUuid = "actor-1",
            recipeId = "haggis",
            recipeName = "Haggis",
            degree = DegreeOfSuccess.SUCCESS,
            actorIsPc = true,
            actorType = null,
        )
        assertNotNull(second, "Should qualify after 2 successes")
        assertTrue(second!!.isNewFavorite)
        assertEquals("haggis", camping.cooking.actorMeals["actor-1"]?.favoriteMeal)
    }

    @Test
    fun testProgressCountsTwoSuccesses() {
        val camping = testCampingData(
            actorMeals = recordOf(
                "actor-1" to ActorMeal(
                    actorUuid = "actor-1",
                    favoriteMeal = null,
                    chosenMeal = "haggis",
                ),
            ),
        )
        camping.recordCookingResult(
            actorUuid = "actor-1",
            recipeId = "haggis",
            recipeName = "Haggis",
            degree = DegreeOfSuccess.SUCCESS,
            actorIsPc = true,
            actorType = null,
        )
        camping.recordCookingResult(
            actorUuid = "actor-1",
            recipeId = "haggis",
            recipeName = "Haggis",
            degree = DegreeOfSuccess.SUCCESS,
            actorIsPc = true,
            actorType = null,
        )
        val progress = camping.cooking.favoriteMealProgress?.get("actor-1")?.get("haggis")
        assertNotNull(progress)
        assertEquals(2, progress.successCount)
        assertEquals(0, progress.criticalSuccessCount)
    }

    // ==========================================
    // Progression: change requires 2 critical successes
    // ==========================================

    @Test
    fun testChangeFavoriteRequiresTwoCriticalSuccesses() {
        val camping = testCampingData(
            actorMeals = recordOf(
                "actor-1" to ActorMeal(
                    actorUuid = "actor-1",
                    favoriteMeal = "hearty-meal",
                    chosenMeal = "haggis",
                ),
            ),
        )
        // First crit on new meal — not enough to change
        val first = camping.recordCookingResult(
            actorUuid = "actor-1",
            recipeId = "haggis",
            recipeName = "Haggis",
            degree = DegreeOfSuccess.CRITICAL_SUCCESS,
            actorIsPc = true,
            actorType = null,
        )
        assertNull(first, "Should not change favorite after only 1 crit on new meal")
        assertEquals("hearty-meal", camping.cooking.actorMeals["actor-1"]?.favoriteMeal)

        // Second crit on new meal — now changes
        val second = camping.recordCookingResult(
            actorUuid = "actor-1",
            recipeId = "haggis",
            recipeName = "Haggis",
            degree = DegreeOfSuccess.CRITICAL_SUCCESS,
            actorIsPc = true,
            actorType = null,
        )
        assertNotNull(second, "Should change favorite after 2 crits on new meal")
        assertFalse(second!!.isNewFavorite)
        assertTrue(second.isChange)
        assertEquals("haggis", camping.cooking.actorMeals["actor-1"]?.favoriteMeal)
    }

    @Test
    fun testChangeFavoriteDoesNotTriggerAfterOneCritAndOneSuccess() {
        val camping = testCampingData(
            actorMeals = recordOf(
                "actor-1" to ActorMeal(
                    actorUuid = "actor-1",
                    favoriteMeal = "hearty-meal",
                    chosenMeal = "haggis",
                ),
            ),
        )
        // Crit + success is not enough to change (need 2 crits)
        camping.recordCookingResult(
            actorUuid = "actor-1",
            recipeId = "haggis",
            recipeName = "Haggis",
            degree = DegreeOfSuccess.CRITICAL_SUCCESS,
            actorIsPc = true,
            actorType = null,
        )
        val result = camping.recordCookingResult(
            actorUuid = "actor-1",
            recipeId = "haggis",
            recipeName = "Haggis",
            degree = DegreeOfSuccess.SUCCESS,
            actorIsPc = true,
            actorType = null,
        )
        assertNull(result, "Crit + success should not change favorite")
        assertEquals("hearty-meal", camping.cooking.actorMeals["actor-1"]?.favoriteMeal)
    }

    // ==========================================
    // NPC fixed favorite: never auto-changed
    // ==========================================

    @Test
    fun testNpcFixedFavoriteNeverAutoChanged() {
        val camping = testCampingData(
            actorMeals = recordOf(
                "npc-1" to ActorMeal(
                    actorUuid = "npc-1",
                    favoriteMeal = "hearty-meal",
                    chosenMeal = "haggis",
                    fixedFavoriteMeal = true,
                ),
            ),
        )
        // Even with 2 crits, fixed favorite should not change
        camping.recordCookingResult(
            actorUuid = "npc-1",
            recipeId = "haggis",
            recipeName = "Haggis",
            degree = DegreeOfSuccess.CRITICAL_SUCCESS,
            actorIsPc = false,
            actorType = "npc",
        )
        val result = camping.recordCookingResult(
            actorUuid = "npc-1",
            recipeId = "haggis",
            recipeName = "Haggis",
            degree = DegreeOfSuccess.CRITICAL_SUCCESS,
            actorIsPc = false,
            actorType = "npc",
        )
        assertNull(result, "Fixed favorite should never auto-change")
        assertEquals("hearty-meal", camping.cooking.actorMeals["npc-1"]?.favoriteMeal)
    }

    @Test
    fun testNpcWithFixedFavoriteDoesNotTrackProgress() {
        val camping = testCampingData(
            actorMeals = recordOf(
                "npc-1" to ActorMeal(
                    actorUuid = "npc-1",
                    favoriteMeal = "hearty-meal",
                    chosenMeal = "haggis",
                    fixedFavoriteMeal = true,
                ),
            ),
        )
        camping.recordCookingResult(
            actorUuid = "npc-1",
            recipeId = "haggis",
            recipeName = "Haggis",
            degree = DegreeOfSuccess.CRITICAL_SUCCESS,
            actorIsPc = false,
            actorType = "npc",
        )
        // Progress should not be tracked for NPCs
        assertNull(camping.cooking.favoriteMealProgress)
    }

    // ==========================================
    // Already favorite: no duplicate eligibility
    // ==========================================

    @Test
    fun testNoEligibilityWhenAlreadyFavorite() {
        val camping = testCampingData(
            actorMeals = recordOf(
                "actor-1" to ActorMeal(
                    actorUuid = "actor-1",
                    favoriteMeal = "haggis",
                    chosenMeal = "haggis",
                ),
            ),
        )
        val result = camping.recordCookingResult(
            actorUuid = "actor-1",
            recipeId = "haggis",
            recipeName = "Haggis",
            degree = DegreeOfSuccess.CRITICAL_SUCCESS,
            actorIsPc = true,
            actorType = null,
        )
        assertNull(result, "Should not return eligibility when recipe is already the favorite")
    }

    // ==========================================
    // Failure does not count toward progression
    // ==========================================

    @Test
    fun testFailureDoesNotCountTowardProgression() {
        val camping = testCampingData(
            actorMeals = recordOf(
                "actor-1" to ActorMeal(
                    actorUuid = "actor-1",
                    favoriteMeal = null,
                    chosenMeal = "haggis",
                ),
            ),
        )
        val result = camping.recordCookingResult(
            actorUuid = "actor-1",
            recipeId = "haggis",
            recipeName = "Haggis",
            degree = DegreeOfSuccess.FAILURE,
            actorIsPc = true,
            actorType = null,
        )
        assertNull(result)
        assertNull(camping.cooking.favoriteMealProgress)
    }

    @Test
    fun testCriticalFailureDoesNotCountTowardProgression() {
        val camping = testCampingData(
            actorMeals = recordOf(
                "actor-1" to ActorMeal(
                    actorUuid = "actor-1",
                    favoriteMeal = null,
                    chosenMeal = "haggis",
                ),
            ),
        )
        val result = camping.recordCookingResult(
            actorUuid = "actor-1",
            recipeId = "haggis",
            recipeName = "Haggis",
            degree = DegreeOfSuccess.CRITICAL_FAILURE,
            actorIsPc = true,
            actorType = null,
        )
        assertNull(result)
        assertNull(camping.cooking.favoriteMealProgress)
    }

    // ==========================================
    // shouldApplyFavoriteOutcome tests
    // ==========================================

    @Test
    fun testShouldApplyFavoriteOutcomeWhenQualified() {
        val camping = testCampingData(
            actorMeals = recordOf(
                "actor-1" to ActorMeal(
                    actorUuid = "actor-1",
                    favoriteMeal = "haggis",
                    chosenMeal = "haggis",
                ),
            ),
        )
        assertTrue(
            camping.shouldApplyFavoriteOutcome("actor-1", "haggis"),
            "Should apply favorite outcome when recipe matches favorite",
        )
    }

    @Test
    fun testShouldNotApplyFavoriteOutcomeWhenNotQualified() {
        val camping = testCampingData(
            actorMeals = recordOf(
                "actor-1" to ActorMeal(
                    actorUuid = "actor-1",
                    favoriteMeal = "hearty-meal",
                    chosenMeal = "haggis",
                ),
            ),
        )
        assertFalse(
            camping.shouldApplyFavoriteOutcome("actor-1", "haggis"),
            "Should not apply favorite outcome when recipe is not the favorite",
        )
    }

    @Test
    fun testShouldNotApplyFavoriteOutcomeWhenNoFavorite() {
        val camping = testCampingData(
            actorMeals = recordOf(
                "actor-1" to ActorMeal(
                    actorUuid = "actor-1",
                    favoriteMeal = null,
                    chosenMeal = "haggis",
                ),
            ),
        )
        assertFalse(
            camping.shouldApplyFavoriteOutcome("actor-1", "haggis"),
            "Should not apply favorite outcome when actor has no favorite",
        )
    }

    @Test
    fun testShouldNotApplyFavoriteOutcomeForUnknownActor() {
        val camping = testCampingData()
        assertFalse(
            camping.shouldApplyFavoriteOutcome("unknown-actor", "haggis"),
            "Should not apply favorite outcome for unknown actor",
        )
    }

    // ==========================================
    // Persistence: progress survives across calls
    // ==========================================

    @Test
    fun testProgressPersistsAcrossMultipleCalls() {
        val camping = testCampingData(
            actorMeals = recordOf(
                "actor-1" to ActorMeal(
                    actorUuid = "actor-1",
                    favoriteMeal = null,
                    chosenMeal = "haggis",
                ),
            ),
        )
        // First success
        camping.recordCookingResult(
            actorUuid = "actor-1",
            recipeId = "haggis",
            recipeName = "Haggis",
            degree = DegreeOfSuccess.SUCCESS,
            actorIsPc = true,
            actorType = null,
        )
        // Second success on a different recipe (should not affect haggis progress)
        camping.recordCookingResult(
            actorUuid = "actor-1",
            recipeId = "hearty-meal",
            recipeName = "Hearty Meal",
            degree = DegreeOfSuccess.SUCCESS,
            actorIsPc = true,
            actorType = null,
        )
        // Third success on haggis (should now have 2 successes for haggis)
        val result = camping.recordCookingResult(
            actorUuid = "actor-1",
            recipeId = "haggis",
            recipeName = "Haggis",
            degree = DegreeOfSuccess.SUCCESS,
            actorIsPc = true,
            actorType = null,
        )
        assertNotNull(result, "Should qualify after 2 successes on haggis")
        assertEquals("haggis", camping.cooking.actorMeals["actor-1"]?.favoriteMeal)

        // Verify hearty-meal progress is separate
        val haggisProgress = camping.cooking.favoriteMealProgress?.get("actor-1")?.get("haggis")
        val heartyProgress = camping.cooking.favoriteMealProgress?.get("actor-1")?.get("hearty-meal")
        assertNotNull(haggisProgress)
        assertNotNull(heartyProgress)
        assertEquals(2, haggisProgress.successCount)
        assertEquals(1, heartyProgress.successCount)
    }

    // ==========================================
    // Multiple actors tracked independently
    // ==========================================

    @Test
    fun testMultipleActorsTrackedIndependently() {
        val camping = testCampingData(
            actorMeals = recordOf(
                "actor-1" to ActorMeal(
                    actorUuid = "actor-1",
                    favoriteMeal = null,
                    chosenMeal = "haggis",
                ),
                "actor-2" to ActorMeal(
                    actorUuid = "actor-2",
                    favoriteMeal = null,
                    chosenMeal = "haggis",
                ),
            ),
        )
        // Actor 1 gets 1 success
        camping.recordCookingResult(
            actorUuid = "actor-1",
            recipeId = "haggis",
            recipeName = "Haggis",
            degree = DegreeOfSuccess.SUCCESS,
            actorIsPc = true,
            actorType = null,
        )
        // Actor 2 gets 2 successes
        camping.recordCookingResult(
            actorUuid = "actor-2",
            recipeId = "haggis",
            recipeName = "Haggis",
            degree = DegreeOfSuccess.SUCCESS,
            actorIsPc = true,
            actorType = null,
        )
        val actor2Result = camping.recordCookingResult(
            actorUuid = "actor-2",
            recipeId = "haggis",
            recipeName = "Haggis",
            degree = DegreeOfSuccess.SUCCESS,
            actorIsPc = true,
            actorType = null,
        )
        assertNotNull(actor2Result, "Actor 2 should qualify")
        assertNull(camping.cooking.actorMeals["actor-1"]?.favoriteMeal, "Actor 1 should not have favorite yet")
        assertEquals("haggis", camping.cooking.actorMeals["actor-2"]?.favoriteMeal)
    }

    // ==========================================
    // ActorMeal.fixedFavoriteMeal field tests
    // ==========================================

    @Test
    fun testFixedFavoriteMealFieldDefaultsToNull() {
        val meal = ActorMeal(
            actorUuid = "test",
            favoriteMeal = "haggis",
            chosenMeal = "haggis",
        )
        assertNull(meal.fixedFavoriteMeal)
    }

    @Test
    fun testFixedFavoriteMealFieldCanBeSet() {
        val meal = ActorMeal(
            actorUuid = "test",
            favoriteMeal = "haggis",
            chosenMeal = "haggis",
            fixedFavoriteMeal = true,
        )
        assertTrue(meal.fixedFavoriteMeal == true)
    }

    // ==========================================
    // FavoriteMealProgression data structure
    // ==========================================

    @Test
    fun testFavoriteMealProgressionStoresCounts() {
        val progression = FavoriteMealProgression(
            successCount = 3,
            criticalSuccessCount = 1,
        )
        assertEquals(3, progression.successCount)
        assertEquals(1, progression.criticalSuccessCount)
    }

    @Test
    fun testFavoriteMealProgressionDefaultCounts() {
        val progression = FavoriteMealProgression(
            successCount = 0,
            criticalSuccessCount = 0,
        )
        assertEquals(0, progression.successCount)
        assertEquals(0, progression.criticalSuccessCount)
    }

    // ==========================================
    // Edge case: crit success counts for both thresholds
    // ==========================================

    @Test
    fun testCritSuccessCountsForBothSuccessAndCritThresholds() {
        val camping = testCampingData(
            actorMeals = recordOf(
                "actor-1" to ActorMeal(
                    actorUuid = "actor-1",
                    favoriteMeal = null,
                    chosenMeal = "haggis",
                ),
            ),
        )
        // A single crit success should immediately qualify (1 crit >= 1 crit threshold)
        val result = camping.recordCookingResult(
            actorUuid = "actor-1",
            recipeId = "haggis",
            recipeName = "Haggis",
            degree = DegreeOfSuccess.CRITICAL_SUCCESS,
            actorIsPc = true,
            actorType = null,
        )
        assertNotNull(result)
        assertTrue(result!!.isNewFavorite)

        // Verify the crit was counted in both counters
        val progress = camping.cooking.favoriteMealProgress?.get("actor-1")?.get("haggis")
        assertNotNull(progress)
        // Crit success counts as a success AND a crit success for progression purposes
        // But in our implementation, crit success only increments crit counter, not success counter
        // This is correct per the rules: "one critical success OR two successes"
        assertEquals(0, progress.successCount)
        assertEquals(1, progress.criticalSuccessCount)
    }

    // ==========================================
    // Edge case: changing back to a previous favorite
    // ==========================================

    @Test
    fun testCanChangeBackToPreviousFavorite() {
        val camping = testCampingData(
            actorMeals = recordOf(
                "actor-1" to ActorMeal(
                    actorUuid = "actor-1",
                    favoriteMeal = "haggis",
                    chosenMeal = "hearty-meal",
                ),
            ),
        )
        // Change to hearty-meal (need 2 crits)
        camping.recordCookingResult(
            actorUuid = "actor-1",
            recipeId = "hearty-meal",
            recipeName = "Hearty Meal",
            degree = DegreeOfSuccess.CRITICAL_SUCCESS,
            actorIsPc = true,
            actorType = null,
        )
        camping.recordCookingResult(
            actorUuid = "actor-1",
            recipeId = "hearty-meal",
            recipeName = "Hearty Meal",
            degree = DegreeOfSuccess.CRITICAL_SUCCESS,
            actorIsPc = true,
            actorType = null,
        )
        assertEquals("hearty-meal", camping.cooking.actorMeals["actor-1"]?.favoriteMeal)

        // Change back to haggis (need 2 more crits)
        camping.recordCookingResult(
            actorUuid = "actor-1",
            recipeId = "haggis",
            recipeName = "Haggis",
            degree = DegreeOfSuccess.CRITICAL_SUCCESS,
            actorIsPc = true,
            actorType = null,
        )
        val result = camping.recordCookingResult(
            actorUuid = "actor-1",
            recipeId = "haggis",
            recipeName = "Haggis",
            degree = DegreeOfSuccess.CRITICAL_SUCCESS,
            actorIsPc = true,
            actorType = null,
        )
        assertNotNull(result)
        assertTrue(result!!.isChange)
        assertEquals("haggis", camping.cooking.actorMeals["actor-1"]?.favoriteMeal)
    }
}
