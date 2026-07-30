package at.posselt.pfrpg2e.camping

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EncounterPreviewRestoreTest {
    @Test
    fun aPersistedCategoryAndResultAreRestorable() {
        assertEquals(EncounterCategory.RUMOR, restorableEncounterPreview("rumor", "A stranger whispers of a hidden vault."))
        assertEquals(EncounterCategory.COMBAT, restorableEncounterPreview("combat", "2 bandits"))
    }

    @Test
    fun missingOrBlankResultIsNotRestorable() {
        assertNull(restorableEncounterPreview("combat", null))
        assertNull(restorableEncounterPreview("combat", ""))
        assertNull(restorableEncounterPreview("combat", "   "))
    }

    @Test
    fun missingOrUnknownCategoryIsNotRestorable() {
        assertNull(restorableEncounterPreview(null, "2 bandits"))
        assertNull(restorableEncounterPreview("not-a-category", "2 bandits"))
    }

    @Test
    fun lenientCategoryMatchingStillResolves() {
        // fromString's whole-token leniency applies to restored values too
        assertEquals(EncounterCategory.COMBAT, restorableEncounterPreview("Combat Encounter", "2 bandits"))
    }
}
