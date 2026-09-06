package at.posselt.pfrpg2e.camping

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FavoriteMealPinningTest {

    @Test
    fun autoLearnedMealIsNotPinnedOnSaveWithoutChange() {
        // Opening dialog and saving when pick did not change should preserve unpinned state
        assertFalse(
            shouldRowBePinned(
                wasPinned = false,
                previousMeal = "recipe-stew",
                pickedMeal = "recipe-stew",
                explicitlyUnpinned = false,
            )
        )
    }

    @Test
    fun pinnedMealRemainsPinnedOnSaveWithoutChange() {
        // Existing pinned meal remains pinned when not changed
        assertTrue(
            shouldRowBePinned(
                wasPinned = true,
                previousMeal = "recipe-stew",
                pickedMeal = "recipe-stew",
                explicitlyUnpinned = false,
            )
        )
    }

    @Test
    fun explicitlyUnpinnedMealClearsPinWithoutChangingMeal() {
        // Explicitly unpinning clears fixedFavoriteMeal even when the meal remains identical
        assertFalse(
            shouldRowBePinned(
                wasPinned = true,
                previousMeal = "recipe-stew",
                pickedMeal = "recipe-stew",
                explicitlyUnpinned = true,
            )
        )
    }

    @Test
    fun handPickingNewMealPinsIt() {
        // Changing from one meal to another pins the choice
        assertTrue(
            shouldRowBePinned(
                wasPinned = false,
                previousMeal = "recipe-stew",
                pickedMeal = "recipe-roast",
                explicitlyUnpinned = false,
            )
        )
    }

    @Test
    fun handPickingInitialMealPinsIt() {
        // Picking a meal when camper had none pins it
        assertTrue(
            shouldRowBePinned(
                wasPinned = false,
                previousMeal = null,
                pickedMeal = "recipe-roast",
                explicitlyUnpinned = false,
            )
        )
    }

    @Test
    fun clearingFavoriteMealUnpinsIt() {
        // Changing meal to null clears pin
        assertFalse(
            shouldRowBePinned(
                wasPinned = true,
                previousMeal = "recipe-stew",
                pickedMeal = null,
                explicitlyUnpinned = false,
            )
        )
    }

    @Test
    fun noMealUnchangedRemainsUnpinned() {
        assertFalse(
            shouldRowBePinned(
                wasPinned = false,
                previousMeal = null,
                pickedMeal = null,
                explicitlyUnpinned = false,
            )
        )
    }
}
