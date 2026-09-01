package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.leaders.Leader
import at.posselt.pfrpg2e.kingdom.data.RawPetition
import at.posselt.pfrpg2e.kingdom.data.toModel
import at.posselt.pfrpg2e.kingdom.data.toRaw
import at.posselt.pfrpg2e.kingdom.petitions.Petition
import at.posselt.pfrpg2e.kingdom.petitions.PetitionStatus
import at.posselt.pfrpg2e.kingdom.sheet.contexts.petitionInboxContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
    fun theBundleResolvesAndCarriesTheStarterCatalog() {
        // the @JsModule import is only satisfied because data/petitions/ exists and
        // combineJsonFiles emits a bundle for it. Forty is the plan's starter catalog, five per
        // office -- a drop here means template files stopped being bundled, which would empty
        // every inbox while the rest of the suite stayed green.
        assertEquals(40, petitionTemplates().size)
    }

    @Test
    fun everyOfficeHasTemplatesToDrawFrom() {
        // an office with no templates silently never generates, however eligible it is
        Leader.entries.forEach { role ->
            assertTrue(
                petitionTemplatesForRole(role.value).isNotEmpty(),
                "no petition templates for ${role.value}",
            )
        }
        assertNotNull(petitionTemplateById("succession-question"))
    }

    @Test
    fun everyTemplateOffersAtLeastTwoRealChoices() {
        // a single-option petition is not a decision, and a zero-weight one can never be drawn
        petitionTemplates().forEach { template ->
            val options = template.options ?: emptyArray()
            assertTrue(options.size >= 2, "${template.id} offers ${options.size} option(s)")
            assertTrue(template.weight > 0, "${template.id} has weight ${template.weight}")
            options.forEach { option ->
                assertTrue(
                    (option.consequences ?: emptyArray()).isNotEmpty(),
                    "${template.id}/${option.id} does nothing",
                )
            }
        }
    }

    @Test
    fun everyConsequenceUsesTheClosedVocabulary() {
        // section 4 is a CLOSED set: a kind outside it is a new feature, not a new template, and
        // would apply nothing at all while looking like a priced choice on the card
        val allowed = setOf("unrest", "rp", "standing", "quest", "rumor")
        petitionTemplates().forEach { template ->
            (template.options ?: emptyArray()).forEach { option ->
                (option.consequences ?: emptyArray()).forEach { consequence ->
                    assertTrue(
                        consequence.kind in allowed,
                        "${template.id}/${option.id}: unknown kind ${consequence.kind}",
                    )
                    if (consequence.kind in setOf("unrest", "rp", "standing")) {
                        assertNotNull(
                            consequence.amount,
                            "${template.id}/${option.id}: ${consequence.kind} carries no amount",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun everyTemplateNamesARoleThatExists() {
        // targetRole is a Leader.value string; a typo drops the template from every draw silently
        petitionTemplates().forEach { template ->
            assertNotNull(
                Leader.fromString(template.targetRole),
                "${template.id} targets unknown role ${template.targetRole}",
            )
        }
    }

    @Test
    fun theOverdueSentinelIsNeverRenderedAsAChosenOption() {
        // applyPetitionOverdue parks a sentinel in chosenOptionId; composing a label from it would
        // print the raw i18n key and claim the office "chose" something nobody ever answered
        val expired = Petition(
            id = "p1",
            petitionerId = "n1",
            petitionerName = "Someone",
            targetRole = Leader.RULER,
            templateId = "succession-question",
            createdTurn = 1,
            dueTurn = 4,
            status = PetitionStatus.EXPIRED,
            chosenOptionId = OVERDUE_APPLIED,
        )
        val row = petitionInboxContext(
            petitions = listOf(expired),
            ownedRoles = setOf(Leader.RULER),
            seenIds = emptySet(),
            currentTurn = 5,
        ).rows.single()
        assertNull(row.chosenLabel)
    }

    @Test
    fun theInboxShowsOnlyTheRolesAUserOwns() {
        fun petition(id: String, role: Leader) = Petition(
            id = id,
            petitionerId = "n1",
            petitionerName = "Someone",
            targetRole = role,
            templateId = if (role == Leader.RULER) "succession-question" else "tax-revolt",
            createdTurn = 1,
            dueTurn = 4,
        )
        val all = listOf(petition("a", Leader.RULER), petition("b", Leader.TREASURER))
        val mine = petitionInboxContext(all, setOf(Leader.TREASURER), emptySet(), currentTurn = 2)
        assertEquals(listOf("b"), mine.rows.map { it.id })
        assertEquals(1, mine.unreadCount)
        // a user owning no office gets no inbox and no badge, however full the realm's is
        val none = petitionInboxContext(all, emptySet(), emptySet(), currentTurn = 2)
        assertEquals(0, none.rows.size)
        assertEquals(0, none.unreadCount)
    }

    @Test
    fun eachOptionCarriesItsOwnPetitionId() {
        // petitions.hbs is a registered partial with no parent frame: without this denormalisation
        // every option button in the list would name the first petition
        val p = Petition(
            id = "p-42",
            petitionerId = "n1",
            petitionerName = "Someone",
            targetRole = Leader.RULER,
            templateId = "succession-question",
            createdTurn = 1,
            dueTurn = 4,
        )
        val row = petitionInboxContext(listOf(p), setOf(Leader.RULER), emptySet(), 2).rows.single()
        assertTrue(row.options.isNotEmpty())
        row.options.forEach { assertEquals("p-42", it.petitionId) }
    }
}
