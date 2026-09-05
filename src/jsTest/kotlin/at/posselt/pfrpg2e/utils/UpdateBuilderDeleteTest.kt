package at.posselt.pfrpg2e.utils

import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The delete marker has to land on the record's real path. It was written to the literal string
 * "propertyPath.<key>" — a missing `$` — so every camping record deletion was a silent no-op:
 * removing a camper left their activity assignments, resetting activities reset nothing, and
 * deleting a homebrew activity left a row pointing at an id that no longer resolves.
 */
class UpdateBuilderDeleteTest {
    private fun keysAfterDelete(path: String, key: String): List<String> {
        val updates = js("{}").unsafeCast<js.objects.Record<String, Any?>>()
        val builder = RecordPropertyUpdateBuilder<Any?>("flags.pf2e-kingmaker-tools", updates, path)
        builder.deleteEntry(key)
        return js("Object").keys(updates).unsafeCast<Array<String>>().toList()
    }

    @Test
    fun theDeleteMarkerLandsOnTheInterpolatedPath() {
        val keys = keysAfterDelete("campingActivities", "cook-meal")
        assertEquals(listOf("flags.pf2e-kingmaker-tools.campingActivities.cook-meal"), keys)
    }

    @Test
    fun theLiteralPropertyPathStringIsNeverUsedAsAKey() {
        val keys = keysAfterDelete("cooking.actorMeals", "abc123")
        assertTrue(keys.none { it.contains("propertyPath") }, "wrote a literal propertyPath key: $keys")
    }

    @Test
    fun deletingSeveralEntriesMarksEachOne() {
        val updates = js("{}").unsafeCast<js.objects.Record<String, Any?>>()
        RecordPropertyUpdateBuilder<Any?>("flags.km", updates, "campingActivities")
            .deleteEntries(setOf("a", "b"))
        val keys = js("Object").keys(updates).unsafeCast<Array<String>>().toList()
        assertEquals(setOf("flags.km.campingActivities.a", "flags.km.campingActivities.b"), keys.toSet())
    }
}
