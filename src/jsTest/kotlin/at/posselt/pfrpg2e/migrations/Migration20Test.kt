package at.posselt.pfrpg2e.migrations

import at.posselt.pfrpg2e.camping.CampingData
import at.posselt.pfrpg2e.migrations.migrations.Migration20
import com.foundryvtt.core.Game
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Migration20 is the most structural migration in the chain: it transforms the flat
 * `campingActivities` / `cooking.results` ARRAYS into keyed RECORDS and seeds the forced-march
 * fields. actorMeals is left empty here so the transform runs without a `fromUuid` lookup.
 */
class Migration20Test {
    private val game = unsafeJso<Game>()

    @Test
    fun convertsActivityAndCookingArraysToRecordsAndSeedsForcedMarch() = runTest {
        val camping = unsafeJso<dynamic> {
            campingActivities = arrayOf(unsafeJso<dynamic> {
                activityId = "camp"
                actorUuid = "Actor.a"
                result = "success"
                selectedSkill = "survival"
            })
            cooking = unsafeJso<dynamic> {
                results = arrayOf(unsafeJso<dynamic> {
                    recipeId = "stew"
                    result = "criticalSuccess"
                    skill = "cooking"
                })
                actorMeals = arrayOf<dynamic>()
            }
        }.unsafeCast<CampingData>()

        Migration20().migrateCamping(game, camping)

        val d = camping.asDynamic()
        // Array -> record keyed by activityId / recipeId.
        assertEquals("success", d.campingActivities["camp"].result.unsafeCast<String>())
        assertEquals("survival", d.campingActivities["camp"].selectedSkill.unsafeCast<String>())
        assertEquals("criticalSuccess", d.cooking.results["stew"].result.unsafeCast<String>())
        assertEquals("cooking", d.cooking.results["stew"].skill.unsafeCast<String>())
        // Forced-march fields seeded.
        assertEquals(false, d.forcedMarchActive.unsafeCast<Boolean>())
        assertEquals(0, d.secondsSpentForcedMarching.unsafeCast<Int>())
        assertEquals(12, d.hexSizeInMiles.unsafeCast<Int>())
    }
}
