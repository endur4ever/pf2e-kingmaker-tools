package at.posselt.pfrpg2e.camping

import com.foundryvtt.core.documents.TableResult
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EncounterManifestTest {
    private fun result(type: String, collection: String = "", documentId: String = "", text: String = "") =
        unsafeJso<dynamic> {
            this.type = type
            this.documentCollection = collection
            this.documentId = documentId
            this.text = text
        }.unsafeCast<TableResult>()

    @Test
    fun packResultSeedsOneCompendiumCreature() {
        val manifest = manifestFromTableResult(
            result("pack", "pf2e.pathfinder-bestiary", "abc123", "Gnoll Sergeant")
        )
        val creature = manifest?.creatures?.single()
        assertEquals("Compendium.pf2e.pathfinder-bestiary.Actor.abc123", creature?.uuid)
        assertEquals(1, creature?.count)
        assertEquals("Gnoll Sergeant", creature?.displayName)
        assertNull(creature?.adjustment)
    }

    @Test
    fun worldDocumentResultSeedsAWorldActor() {
        val manifest = manifestFromTableResult(result("document", "Actor", "xyz789", "Bandit"))
        assertEquals("Actor.xyz789", manifest?.creatures?.single()?.uuid)
    }

    @Test
    fun textOnlyResultSeedsNothing() {
        // most shipped tables are prose; the Stage button stays disabled until the GM curates
        assertNull(manifestFromTableResult(result("text", text = "A pack of wolves circles")))
        // a doc-typed result missing its ids is not a usable reference either
        assertNull(manifestFromTableResult(result("pack", "", "abc")))
        assertNull(manifestFromTableResult(result("document", "Actor", "")))
    }

    @Test
    fun spawnCountReadsThroughTheNullableManifest() {
        assertEquals(0, (null as RawEncounterManifest?).spawnCount())
        assertEquals(0, RawEncounterManifest(creatures = null).spawnCount())
        val manifest = RawEncounterManifest(
            creatures = arrayOf(
                RawEncounterCreature(uuid = "a", count = 2),
                RawEncounterCreature(uuid = "b", count = 3),
            ),
        )
        assertEquals(5, manifest.spawnCount())
        assertEquals(2, manifest.toStageCreatures().size)
    }
}
