package at.posselt.pfrpg2e.camping

import js.objects.Object
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The partial-update paths the camping flag is written through.
 *
 * These need no Foundry document: buildCampingUpdate returns a plain record of path -> value, so
 * the thing that actually goes over the wire is directly assertable. That matters because every
 * defect this file pins was invisible to the rest of the suite:
 *
 *  - deleteEntry once wrote its marker to the LITERAL path "propertyPath.<key>" (the `$` was
 *    missing), so every camping deletion -- removing a camper, Reset Activities, deleting a
 *    homebrew recipe, clearing a meal -- silently removed nothing, because a merge update just
 *    ignores a key that is not there and the caller saw a successful write.
 *  - two handlers used to save the WHOLE camping flag after a run of awaits, reverting whatever
 *    another client had changed meanwhile. The fix was to write only the fields the code path
 *    mutates, and "only" is exactly what a key-set assertion can hold them to.
 */
class CampingUpdateBuilderTest {
    @BeforeTest
    fun installForcedDeletionGlobal() {
        // Foundry supplies `_del` at runtime; the builder writes it as the delete marker.
        js("if (typeof globalThis._del === 'undefined') { globalThis._del = { __forcedDeletion: true } }")
    }

    private fun keysOf(record: Any?): List<String> = Object.keys(record.unsafeCast<js.objects.Record<String, Any?>>()).toList()

    @Test
    fun deleteEntryWritesAnInterpolatedPathNotALiteralOne() {
        val update = buildCampingUpdate { cooking.actorMeals.deleteEntry("actor-1") }
        val keys = keysOf(update)
        assertEquals(listOf("cooking.actorMeals.actor-1"), keys)
        assertTrue(
            keys.none { it.contains("propertyPath") },
            "the marker must not land on a literal path -- that shipped once and made every camping deletion a no-op",
        )
    }

    @Test
    fun deletingSeveralEntriesWritesOnePathEach() {
        val update = buildCampingUpdate { campingActivities.deleteEntries(setOf("a", "b")) }
        assertEquals(setOf("campingActivities.a", "campingActivities.b"), keysOf(update).toSet())
    }

    @Test
    fun theFieldsAddedForTheNarrowedWritesActuallyEmitPaths() {
        // watchSlots, learnedCompanionActivities and learnedCompanionActivitiesByActor had no
        // builder entries at all until the writes that needed them were narrowed
        val update = buildCampingUpdate {
            watchSlots.set(arrayOf(arrayOf("uuid-1")))
            learnedCompanionActivities.set(arrayOf("hunt"))
            learnedCompanionActivitiesByActor.set(null)
        }
        assertEquals(
            setOf("watchSlots", "learnedCompanionActivities", "learnedCompanionActivitiesByActor"),
            keysOf(update).toSet(),
        )
    }

    @Test
    fun theMealHandlersWriteTouchesProgressAndMealsAndNothingElse() {
        // ApplyMealEffectsHandler: narrowing to actorMeals ALONE would silently drop every
        // favourite-meal progression count, because recordCookingResult writes both
        val update = buildCampingUpdate {
            cooking.favoriteMealProgress.set(null)
            cooking.actorMeals.set(js("({})").unsafeCast<js.objects.Record<String, ActorMeal>>())
        }
        val keys = keysOf(update).toSet()
        assertEquals(setOf("cooking.favoriteMealProgress", "cooking.actorMeals"), keys)
        assertTrue(keys.none { it == "" || !it.startsWith("cooking.") }, "must not write the whole flag")
    }

    @Test
    fun aNestedSetDoesNotWriteItsParent() {
        // the whole point of a narrow write: touching one nested field must not send its parent,
        // or the update clobbers every sibling the way setCamping did
        val update = buildCampingUpdate { cooking.rationsPaidForDay.set(12) }
        assertEquals(listOf("cooking.rationsPaidForDay"), keysOf(update))
        assertTrue(keysOf(update).none { it == "cooking" })
    }

    @Test
    fun deletingAPropertyClearsPathsBeneathItFirst() {
        val update = buildCampingUpdate {
            cooking.actorMeals.deleteEntry("actor-1")
            cooking.actorMeals.delete()
        }
        assertEquals(listOf("cooking.actorMeals"), keysOf(update))
        assertContains(keysOf(update), "cooking.actorMeals")
    }
}
