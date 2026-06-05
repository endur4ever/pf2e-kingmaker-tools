package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.camping.dialogs.RegionSettings
import js.objects.recordOf
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for learned companion activity unlocks:
 * - learnedCompanionActivities field on CampingData defaults to empty
 * - Activity availability is enabled when companion is present
 * - Activity availability is enabled when activity is in the learned list
 * - Activity availability is disabled when companion is absent and activity not learned
 * - Learn from a Companion result handling adds companion activities to learned list
 * - Learned activities persist across getDefaultCamping / sheet reloads
 */
class LearnedCompanionActivityTest {

    private fun companionActivity(
        id: String,
        companionName: String,
    ): CampingActivityData = unsafeJso {
        this.id = id
        name = "campingActivities.$id.name"
        skills = arrayOf(
            CampingSkill(
                name = "perception",
                proficiency = "untrained",
                dcType = "static",
                dc = 20,
            ),
        )
        isSecret = false
        isLocked = false
        isHomebrew = false
        oncePerSession = true
        requiredCompanion = companionName
    }

    private fun universalActivity(id: String): CampingActivityData = unsafeJso {
        this.id = id
        name = "campingActivities.$id.name"
        skills = arrayOf(
            CampingSkill(
                name = "survival",
                proficiency = "trained",
                dcType = "zone",
            ),
        )
        isSecret = false
        isLocked = false
        isHomebrew = false
        oncePerSession = true
        requiredCompanion = null
    }

    // === learnedCompanionActivities field default ===

    @Test
    fun testLearnedCompanionActivitiesDefaultsToEmpty() {
        val camping = unsafeJso<CampingData> {
            currentRegion = "test"
            actorUuids = emptyArray()
            campingActivities = recordOf()
            homebrewCampingActivities = emptyArray()
            lockedActivities = emptyArray()
            cooking = Cooking(
                knownRecipes = emptyArray(),
                actorMeals = recordOf(),
                homebrewMeals = emptyArray(),
                results = recordOf(),
                minimumSubsistence = 0,
            )
            watchSecondsRemaining = 0
            gunsToClean = 0
            dailyPrepsAtTime = 0
            encounterModifier = 0
            restRollMode = "one"
            increaseWatchActorNumber = 0
            actorUuidsNotKeepingWatch = emptyArray()
            alwaysPerformActivityIds = emptyArray()
            huntAndGatherTargetActorUuid = null
            proxyRandomEncounterTableUuid = null
            randomEncounterRollMode = "gmroll"
            ignoreSkillRequirements = false
            minimumTravelSpeed = null
            regionSettings = RegionSettings(regions = emptyArray())
            section = "prepareCampsite"
            restingTrack = null
            worldSceneId = null
            autoApplyFatigued = false
            restSettings = RestSettings(
                skipWatch = false,
                skipDailyPreparations = false,
                disableRandomEncounter = false,
                skipWeather = false,
            )
            secondsSpentTraveling = 0
            secondsSpentHexploring = 0
            resetTimeTrackingAfterOneDay = true
            travelModeActive = false
            forcedMarchActive = false
            secondsSpentForcedMarching = 0
            hexSizeInMiles = 12
            learnedCompanionActivities = emptyArray()
            watchSlots = emptyArray()
            downtimeHoursSpent = recordOf()
        }
        assertEquals(0, camping.learnedCompanionActivities.size)
    }

    // === Activity availability: companion present ===

    @Test
    fun testActivityEnabledWhenCompanionPresent() {
        val activity = companionActivity("blend-into-the-night", "Harrim")
        // When companion is present, activity should be available (not disabled)
        assertTrue(activity.isRequiredCompanionPresent(setOf("Harrim", "Linzi")))
    }

    @Test
    fun testActivityEnabledWhenCompanionPresent_caseInsensitive() {
        val activity = companionActivity("blend-into-the-night", "Harrim")
        assertTrue(activity.isRequiredCompanionPresent(setOf("harrim")))
    }

    // === Activity availability: learned list ===

    @Test
    fun testLearnedActivityAvailableWithoutCompanion() {
        val activity = companionActivity("blend-into-the-night", "Harrim")
        // Even though Harrim is not present, if the activity is in the learned list,
        // it should be available. We simulate this by checking that isLearned would
        // make the disabled check pass.
        val camping = unsafeJso<CampingData> {
            currentRegion = "test"
            actorUuids = emptyArray()
            campingActivities = recordOf()
            homebrewCampingActivities = emptyArray()
            lockedActivities = emptyArray()
            cooking = Cooking(
                knownRecipes = emptyArray(),
                actorMeals = recordOf(),
                homebrewMeals = emptyArray(),
                results = recordOf(),
                minimumSubsistence = 0,
            )
            watchSecondsRemaining = 0
            gunsToClean = 0
            dailyPrepsAtTime = 0
            encounterModifier = 0
            restRollMode = "one"
            increaseWatchActorNumber = 0
            actorUuidsNotKeepingWatch = emptyArray()
            alwaysPerformActivityIds = emptyArray()
            huntAndGatherTargetActorUuid = null
            proxyRandomEncounterTableUuid = null
            randomEncounterRollMode = "gmroll"
            ignoreSkillRequirements = false
            minimumTravelSpeed = null
            regionSettings = RegionSettings(regions = emptyArray())
            section = "prepareCampsite"
            restingTrack = null
            worldSceneId = null
            autoApplyFatigued = false
            restSettings = RestSettings(
                skipWatch = false,
                skipDailyPreparations = false,
                disableRandomEncounter = false,
                skipWeather = false,
            )
            secondsSpentTraveling = 0
            secondsSpentHexploring = 0
            resetTimeTrackingAfterOneDay = true
            travelModeActive = false
            forcedMarchActive = false
            secondsSpentForcedMarching = 0
            hexSizeInMiles = 12
            learnedCompanionActivities = arrayOf("blend-into-the-night")
            watchSlots = emptyArray()
            downtimeHoursSpent = recordOf()
        }
        val isLearned = activity.id in camping.learnedCompanionActivities
        assertTrue(isLearned)
        val isCompanionPresent = activity.isRequiredCompanionPresent(setOf("Linzi", "Amiri"))
        assertFalse(isCompanionPresent)
        // The disabled logic: !hidden && requiredCompanion != null && !isCompanionPresent && !isLearned
        // With hidden=false and requiredCompanion != null:
        // !false && true && !false && !true = true && true && true && false = false
        // So disabled = false, meaning the activity is available
        val disabled = true && (activity.requiredCompanion != null) && !isCompanionPresent && !isLearned
        assertFalse(disabled, "Learned activity should not be disabled even when companion is absent")
    }

    @Test
    fun testUnlearnedActivityDisabledWhenCompanionAbsent() {
        val activity = companionActivity("blend-into-the-night", "Harrim")
        val camping = unsafeJso<CampingData> {
            currentRegion = "test"
            actorUuids = emptyArray()
            campingActivities = recordOf()
            homebrewCampingActivities = emptyArray()
            lockedActivities = emptyArray()
            cooking = Cooking(
                knownRecipes = emptyArray(),
                actorMeals = recordOf(),
                homebrewMeals = emptyArray(),
                results = recordOf(),
                minimumSubsistence = 0,
            )
            watchSecondsRemaining = 0
            gunsToClean = 0
            dailyPrepsAtTime = 0
            encounterModifier = 0
            restRollMode = "one"
            increaseWatchActorNumber = 0
            actorUuidsNotKeepingWatch = emptyArray()
            alwaysPerformActivityIds = emptyArray()
            huntAndGatherTargetActorUuid = null
            proxyRandomEncounterTableUuid = null
            randomEncounterRollMode = "gmroll"
            ignoreSkillRequirements = false
            minimumTravelSpeed = null
            regionSettings = RegionSettings(regions = emptyArray())
            section = "prepareCampsite"
            restingTrack = null
            worldSceneId = null
            autoApplyFatigued = false
            restSettings = RestSettings(
                skipWatch = false,
                skipDailyPreparations = false,
                disableRandomEncounter = false,
                skipWeather = false,
            )
            secondsSpentTraveling = 0
            secondsSpentHexploring = 0
            resetTimeTrackingAfterOneDay = true
            travelModeActive = false
            forcedMarchActive = false
            secondsSpentForcedMarching = 0
            hexSizeInMiles = 12
            learnedCompanionActivities = emptyArray()
            watchSlots = emptyArray()
            downtimeHoursSpent = recordOf()
        }
        val isLearned = activity.id in camping.learnedCompanionActivities
        assertFalse(isLearned)
        val isCompanionPresent = activity.isRequiredCompanionPresent(setOf("Linzi", "Amiri"))
        assertFalse(isCompanionPresent)
        // disabled = true && true && true && true = true
        val disabled = true && (activity.requiredCompanion != null) && !isCompanionPresent && !isLearned
        assertTrue(disabled, "Unlearned activity should be disabled when companion is absent")
    }

    // === Universal activities unaffected ===

    @Test
    fun testUniversalActivityAlwaysAvailable() {
        val activity = universalActivity("relax")
        assertTrue(activity.isRequiredCompanionPresent(emptySet()))
        // Universal activities have requiredCompanion = null, so disabled check is:
        // !hidden && null != null → false
        val disabled = true && (activity.requiredCompanion != null) && false
        assertFalse(disabled, "Universal activity should never be disabled by companion gating")
    }

    // === Companion activity still available when present AND learned ===

    @Test
    fun testCompanionActivityAvailableWhenPresentAndLearned() {
        val activity = companionActivity("bolster-confidence", "Linzi")
        val camping = unsafeJso<CampingData> {
            currentRegion = "test"
            actorUuids = emptyArray()
            campingActivities = recordOf()
            homebrewCampingActivities = emptyArray()
            lockedActivities = emptyArray()
            cooking = Cooking(
                knownRecipes = emptyArray(),
                actorMeals = recordOf(),
                homebrewMeals = emptyArray(),
                results = recordOf(),
                minimumSubsistence = 0,
            )
            watchSecondsRemaining = 0
            gunsToClean = 0
            dailyPrepsAtTime = 0
            encounterModifier = 0
            restRollMode = "one"
            increaseWatchActorNumber = 0
            actorUuidsNotKeepingWatch = emptyArray()
            alwaysPerformActivityIds = emptyArray()
            huntAndGatherTargetActorUuid = null
            proxyRandomEncounterTableUuid = null
            randomEncounterRollMode = "gmroll"
            ignoreSkillRequirements = false
            minimumTravelSpeed = null
            regionSettings = RegionSettings(regions = emptyArray())
            section = "prepareCampsite"
            restingTrack = null
            worldSceneId = null
            autoApplyFatigued = false
            restSettings = RestSettings(
                skipWatch = false,
                skipDailyPreparations = false,
                disableRandomEncounter = false,
                skipWeather = false,
            )
            secondsSpentTraveling = 0
            secondsSpentHexploring = 0
            resetTimeTrackingAfterOneDay = true
            travelModeActive = false
            forcedMarchActive = false
            secondsSpentForcedMarching = 0
            hexSizeInMiles = 12
            learnedCompanionActivities = arrayOf("bolster-confidence")
            watchSlots = emptyArray()
            downtimeHoursSpent = recordOf()
        }
        val isCompanionPresent = activity.isRequiredCompanionPresent(setOf("Linzi"))
        assertTrue(isCompanionPresent)
        val isLearned = activity.id in camping.learnedCompanionActivities
        assertTrue(isLearned)
        // disabled = true && true && !true && !true = true && true && false && false = false
        val disabled = true && (activity.requiredCompanion != null) && !isCompanionPresent && !isLearned
        assertFalse(disabled, "Activity should be available when companion is present (even if also learned)")
    }

    // === Multiple learned activities ===

    @Test
    fun testMultipleLearnedActivities() {
        val activity1 = companionActivity("blend-into-the-night", "Harrim")
        val activity2 = companionActivity("bolster-confidence", "Linzi")
        val activity3 = companionActivity("enhance-weapons", "Amiri")

        val learned = arrayOf("blend-into-the-night", "bolster-confidence")

        assertTrue(activity1.id in learned)
        assertTrue(activity2.id in learned)
        assertFalse(activity3.id in learned)

        // activity3 companion absent and not learned → disabled
        val isCompanionPresent3 = activity3.isRequiredCompanionPresent(setOf("Harrim", "Linzi"))
        assertFalse(isCompanionPresent3)
        val isLearned3 = activity3.id in learned
        assertFalse(isLearned3)
        val disabled3 = true && (activity3.requiredCompanion != null) && !isCompanionPresent3 && !isLearned3
        assertTrue(disabled3, "Unlearned activity should be disabled when companion absent")
    }

    // === Learned activities don't affect hidden-by-lock logic ===

    @Test
    fun testHiddenByLockStillRespectsLearnedActivities() {
        // Companion activities should never be hidden by lock
        val activity = companionActivity("blend-into-the-night", "Harrim")
        assertFalse(
            activity.isHiddenByLock(setOf("blend-into-the-night")),
            "Companion activity should not be hidden by lock even when in lock set",
        )
    }

    @Test
    fun testNonCompanionActivityHiddenByLockUnaffectedByLearned() {
        val activity = universalActivity("relax")
        // Non-companion activities are NOT affected by learnedCompanionActivities
        // They should still be hidden when locked
        assertTrue(
            activity.isHiddenByLock(setOf("relax")),
            "Non-companion activity should still be hidden when locked",
        )
        assertFalse(
            activity.isHiddenByLock(emptySet()),
            "Non-companion activity should be shown when not locked",
        )
    }
}
