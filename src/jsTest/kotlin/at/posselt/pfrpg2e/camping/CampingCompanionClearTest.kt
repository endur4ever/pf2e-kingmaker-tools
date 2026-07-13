package at.posselt.pfrpg2e.camping

import js.objects.recordOf
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CampingCompanionClearTest {

    @Test
    fun testClearDepartingCompanion() {
        val companionUuid = "uuid-amiri"

        val activity1 = unsafeJso<CampingActivity> {
            actorUuid = companionUuid
        }
        val activity2 = unsafeJso<CampingActivity> {
            actorUuid = "uuid-valerie"
        }

        val mealAmiri = unsafeJso<ActorMeal> {
            actorUuid = companionUuid
            chosenMeal = "haggis"
        }
        val mealValerie = unsafeJso<ActorMeal> {
            actorUuid = "uuid-valerie"
            chosenMeal = "hearty-meal"
        }

        val camping = unsafeJso<CampingData> {
            campingActivities = recordOf(
                "cook-meal" to activity1,
                "prepare-campsite" to activity2
            )
            watchSlots = arrayOf(
                arrayOf(companionUuid, "uuid-valerie"),
                arrayOf("uuid-valerie")
            )
            cooking = unsafeJso {
                actorMeals = recordOf(
                    companionUuid to mealAmiri,
                    "uuid-valerie" to mealValerie
                )
            }
        }

        val result = clearDepartingCompanionFromCamp(camping, companionUuid)

        // Verify result
        assertEquals(listOf("cook-meal"), result.clearedActivities)
        assertEquals(1, result.clearedWatchSlotsCount)
        assertTrue(result.clearedMealChoice)

        // Verify camping data was mutated correctly
        assertNull(camping.campingActivities["cook-meal"]?.actorUuid)
        assertEquals("uuid-valerie", camping.campingActivities["prepare-campsite"]?.actorUuid)

        assertEquals(2, camping.watchSlots.size)
        assertEquals(1, camping.watchSlots[0].size)
        assertEquals("uuid-valerie", camping.watchSlots[0][0])
        assertEquals(1, camping.watchSlots[1].size)
        assertEquals("uuid-valerie", camping.watchSlots[1][0])

        assertNull(camping.cooking.actorMeals[companionUuid])
        assertNotNull(camping.cooking.actorMeals["uuid-valerie"])
    }

    private fun assertNotNull(actual: Any?) {
        assertTrue(actual != null)
    }
}
