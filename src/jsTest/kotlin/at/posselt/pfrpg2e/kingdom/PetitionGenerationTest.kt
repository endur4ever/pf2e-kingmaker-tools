package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.leaders.Leader
import at.posselt.pfrpg2e.kingdom.data.toModel
import at.posselt.pfrpg2e.kingdom.petitions.MAX_NEW_PETITIONS_PER_TURN
import at.posselt.pfrpg2e.kingdom.petitions.PetitionStatus
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The ADAPTER, not the engine: green detector tests plus an adapter feeding wrong values is the
 * shape every dead feature in this repo has had. These pin the wiring — the caps as the adapter
 * actually applies them, the roster gate, and the fairness rotation reaching the last office.
 */
class PetitionGenerationTest {
    private fun npc(id: String) = unsafeJso<dynamic> {
        this.id = id
        name = "Resident $id"
        occupation = "farmer"
    }

    private fun kingdom(
        filled: List<Leader> = Leader.entries.toList(),
        withRoster: Boolean = true,
        petitions: Array<dynamic> = emptyArray(),
    ): KingdomData {
        val leaderSlots = unsafeJso<dynamic> {}
        for (role in Leader.entries) {
            leaderSlots[role.value] = unsafeJso<dynamic> {
                uuid = if (role in filled) "Actor.${role.value}" else null
            }
        }
        return unsafeJso<dynamic> {
            leaders = leaderSlots
            this.petitions = petitions
            settlements = arrayOf(
                unsafeJso<dynamic> {
                    sceneId = "scene-1"
                    populationRoster = unsafeJso<dynamic> {
                        npcs = if (withRoster) arrayOf(npc("n1"), npc("n2")) else emptyArray<dynamic>()
                    }
                }
            )
        }.unsafeCast<KingdomData>()
    }

    /** Always passes the chance roll, and always picks the first weighted slot. */
    private fun alwaysGenerate(k: KingdomData, turn: Int) = generatePetitionsForTurn(
        kingdom = k,
        currentTurn = turn,
        structureNames = emptySet(),
        chanceRoll = { 1 },
        pickRoll = { 0 },
    )

    @Test
    fun atMostTwoPetitionsArriveEvenWithAllEightOfficesFilled() {
        // without this the eight filled roles produce eight petitions a turn and the inbox becomes
        // the chore the caps exist to prevent
        val k = kingdom()
        val created = alwaysGenerate(k, turn = 1)
        assertEquals(MAX_NEW_PETITIONS_PER_TURN, created.size)
        assertEquals(MAX_NEW_PETITIONS_PER_TURN, k.petitions?.size)
    }

    @Test
    fun aRoleSittingOnAnOpenPetitionReceivesNoSecondOne() {
        val k = kingdom(filled = listOf(Leader.RULER))
        val first = alwaysGenerate(k, turn = 1)
        assertEquals(1, first.size)
        val second = alwaysGenerate(k, turn = 2)
        assertEquals(0, second.size, "an office may hold only one undecided audience")
    }

    @Test
    fun anEmptyRosterGeneratesNothingRatherThanAnUnnamedPetitioner() {
        val k = kingdom(withRoster = false)
        assertEquals(0, alwaysGenerate(k, turn = 1).size)
        assertTrue(k.petitions.isNullOrEmpty())
    }

    @Test
    fun failingTheChanceRollGeneratesNothing() {
        val k = kingdom()
        val created = generatePetitionsForTurn(
            kingdom = k,
            currentTurn = 1,
            structureNames = emptySet(),
            chanceRoll = { 100 },
            pickRoll = { 0 },
        )
        assertEquals(0, created.size)
    }

    @Test
    fun theLastOfficeInTheEnumStillReceivesPetitions() {
        // the bias this rotation exists to kill: WARDEN is last in Leader.entries, so a plain
        // take(2) over declaration order would leave it permanently empty
        val roles = mutableSetOf<Leader>()
        for (turn in 1..8) {
            val k = kingdom()
            roles.addAll(alwaysGenerate(k, turn).map { it.targetRole })
        }
        assertTrue(Leader.WARDEN in roles, "warden never received a petition across eight turns")
        assertTrue(roles.size > MAX_NEW_PETITIONS_PER_TURN, "the same offices took every petition")
    }

    @Test
    fun aPetitionIsDueThreeTurnsAfterItArrives() {
        val created = alwaysGenerate(kingdom(), turn = 4).first()
        assertEquals(7, created.dueTurn)
        assertEquals(4, created.createdTurn)
        assertEquals(PetitionStatus.OPEN, created.status)
    }

    @Test
    fun thePetitionerAndSettlementComeFromTheRoster() {
        val created = alwaysGenerate(kingdom(), turn = 1).first()
        assertEquals("n1", created.petitionerId)
        assertEquals("Resident n1", created.petitionerName)
        assertEquals("scene-1", created.settlementId)
    }

    @Test
    fun expiryFreesTheRoleAndReportsOnlyThisTurnsLapses() {
        val k = kingdom(filled = listOf(Leader.RULER))
        alwaysGenerate(k, turn = 1)
        // nothing is due yet
        assertEquals(0, expirePetitionsForTurn(k, currentTurn = 3).size)
        val lapsed = expirePetitionsForTurn(k, currentTurn = 4)
        assertEquals(1, lapsed.size)
        // and it does not lapse -- or re-offer -- a second time
        assertEquals(0, expirePetitionsForTurn(k, currentTurn = 5).size)
        val stored = k.petitions?.mapNotNull { it.toModel() }.orEmpty()
        assertEquals(PetitionStatus.EXPIRED, stored.single().status)
        // with the audience dead, the office is free to receive the next one
        assertEquals(1, alwaysGenerate(k, turn = 5).size)
    }

    @Test
    fun everyGeneratedPetitionNamesATemplateTheCatalogStillCarries() {
        // the catalog is data; a generated petition pointing at a template that is not there would
        // render an empty row in every inbox
        val created = alwaysGenerate(kingdom(), turn = 1)
        assertTrue(created.isNotEmpty())
        created.forEach { assertNotNull(petitionTemplateById(it.templateId), it.templateId) }
    }

    @Test
    fun aVacantOfficeReceivesNoAudiences() {
        val k = kingdom(filled = emptyList())
        assertEquals(0, alwaysGenerate(k, turn = 1).size)
    }
}
