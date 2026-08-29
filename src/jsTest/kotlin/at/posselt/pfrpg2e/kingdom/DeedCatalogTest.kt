package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.deeds.deedDetectors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shipped milestone catalog against the detector map. commonTest can only check the code's
 * own list; this reads the BUNDLED JSON, so a data file with a typo'd or orphaned detectionId
 * fails here rather than shipping a deed that can never fire.
 */
class DeedCatalogTest {
    private val catalogDetectionIds: List<String>
        get() = kingdomMilestonesForTest().mapNotNull { it.detectionId }

    @Test
    fun everyCatalogDetectionIdHasADetector() {
        val ids = catalogDetectionIds
        assertTrue(ids.isNotEmpty(), "no milestone carries a detectionId")
        ids.forEach { assertTrue(it in deedDetectors, "catalog names '$it' with no detector") }
    }

    @Test
    fun everyDetectorIsReachableFromTheCatalog() {
        assertEquals(deedDetectors.keys, catalogDetectionIds.toSet())
    }

    @Test
    fun detectionIdsAreUniqueAcrossTheCatalog() {
        val ids = catalogDetectionIds
        assertEquals(ids.size, ids.toSet().size, "two milestones share a detectionId: $ids")
    }
}
