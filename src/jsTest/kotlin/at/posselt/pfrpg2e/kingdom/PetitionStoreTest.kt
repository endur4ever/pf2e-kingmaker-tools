package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.leaders.Leader
import at.posselt.pfrpg2e.kingdom.data.RawPetition
import at.posselt.pfrpg2e.kingdom.data.toModel
import at.posselt.pfrpg2e.kingdom.data.toRaw
import at.posselt.pfrpg2e.kingdom.petitions.Petition
import at.posselt.pfrpg2e.kingdom.petitions.PetitionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PetitionStoreTest {
    private fun petition() = Petition(
        id = "p1",
        petitionerId = "npc-1",
        petitionerName = "Svetlana Morozov",
        settlementId = "scene-1",
        targetRole = Leader.RULER,
        templateId = "succession-question",
        createdTurn = 4,
        dueTurn = 7,
        status = PetitionStatus.OPEN,
        chosenOptionId = null,
        resolvedTurn = null,
    )

    @Test
    fun rawRoundTripPreservesEveryField() {
        val original = petition().copy(
            status = PetitionStatus.ANSWERED,
            chosenOptionId = "concede",
            resolvedTurn = 6,
        )
        assertEquals(original, original.toRaw().toModel())
    }

    @Test
    fun anUnknownRoleOrStatusDropsThatPetitionOnly() {
        // one bad row must not empty an inbox
        val raw = petition().toRaw()
        raw.targetRole = "spymaster"
        assertNull(raw.toModel())
        val raw2 = petition().toRaw()
        raw2.status = "escalated"
        assertNull(raw2.toModel())
    }

    @Test
    fun thePetitionerNameSurvivesADeletedRosterEntry() {
        // the name is captured at creation precisely so this still renders
        val raw = petition().toRaw()
        assertEquals("Svetlana Morozov", raw.petitionerName)
        assertEquals("Svetlana Morozov", raw.toModel()?.petitionerName)
    }

    @Test
    fun theBundleResolvesEvenWithNoTemplates() {
        // the @JsModule import is only satisfied because data/petitions/ exists and
        // combineJsonFiles emits "[]" for it. If this ever throws instead of returning an empty
        // array, the directory (and its load-bearing .gitkeep) has gone missing.
        assertEquals(0, petitionTemplates().size)
    }

    @Test
    fun theCatalogShipsEmptyUntilTheTemplatesLand() {
        // the forty starter templates need option ids and labels that are Gregory's to write;
        // everything around them is here, so they drop in as pure data
        assertEquals(0, petitionTemplates().size)
        assertNull(petitionTemplateById("succession-question"))
        assertTrue(petitionTemplatesForRole(Leader.RULER.value).isEmpty())
    }
}
