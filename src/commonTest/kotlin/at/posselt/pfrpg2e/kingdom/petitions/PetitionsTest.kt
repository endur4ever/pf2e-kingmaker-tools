package at.posselt.pfrpg2e.kingdom.petitions

import at.posselt.pfrpg2e.data.kingdom.leaders.Leader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Covers the cases §8 of the petition-inbox plan enumerates. */
class PetitionsTest {
    private fun petition(
        id: String = "p1",
        targetRole: Leader = Leader.MAGISTER,
        createdTurn: Int = 1,
        dueTurn: Int = createdTurn + PETITION_DUE_AFTER_TURNS,
        status: PetitionStatus = PetitionStatus.OPEN,
        resolvedTurn: Int? = null,
    ) = Petition(
        id = id, petitionerId = "npc1", petitionerName = "Svetlana", settlementId = "s1",
        targetRole = targetRole, templateId = "tax-revolt", createdTurn = createdTurn,
        dueTurn = dueTurn, status = status, resolvedTurn = resolvedTurn,
    )

    @Test
    fun aPetitionDueThisTurnExpiresWhileOneDueNextTurnDoesNot() {
        // Both sides of the deadline: due-this-turn lapses, due-next-turn keeps waiting.
        val out = expirePetitions(
            listOf(petition(id = "due", dueTurn = 5), petition(id = "notYet", dueTurn = 6)),
            currentTurn = 5,
        )
        val expired = out.petitions.first { it.id == "due" }
        assertEquals(PetitionStatus.EXPIRED, expired.status)
        assertEquals(5, expired.resolvedTurn, "resolvedTurn records when the petition lapsed")
        assertEquals(PetitionStatus.OPEN, out.petitions.first { it.id == "notYet" }.status)
        assertEquals(listOf("due"), out.newlyExpired.map { it.id })
    }

    @Test
    fun aLongOverduePetitionStillExpires() {
        // The exact turn a petition lapses can be skipped entirely; the lapse must still land.
        val out = expirePetitions(listOf(petition(dueTurn = 3)), currentTurn = 10)
        assertEquals(PetitionStatus.EXPIRED, out.petitions.single().status)
        assertEquals(10, out.petitions.single().resolvedTurn)
    }

    @Test
    fun anExpiredPetitionIsNotExpiredTwiceAndNotReReported() {
        // Expiry is a GM offer; re-reporting would re-post the overdue card every turn.
        val already = petition(dueTurn = 3, status = PetitionStatus.EXPIRED, resolvedTurn = 4)
        val out = expirePetitions(listOf(already), currentTurn = 9)
        assertTrue(out.newlyExpired.isEmpty(), "an expired petition must not re-offer")
        assertEquals(4, out.petitions.single().resolvedTurn, "the original expiry turn must survive")
    }

    @Test
    fun anAnsweredPetitionPastItsDueTurnNeverExpires() {
        val answered = petition(dueTurn = 3, status = PetitionStatus.ANSWERED, resolvedTurn = 2)
        val out = expirePetitions(listOf(answered), currentTurn = 9)
        assertTrue(out.newlyExpired.isEmpty())
        assertEquals(PetitionStatus.ANSWERED, out.petitions.single().status)
    }

    @Test
    fun aRoleWithAnOpenPetitionGeneratesNone() {
        val roles = rolesEligibleForNewPetitions(
            petitions = listOf(petition(targetRole = Leader.TREASURER)),
            filledRoles = setOf(Leader.TREASURER, Leader.MAGISTER),
        )
        assertEquals(listOf(Leader.MAGISTER), roles)
    }

    @Test
    fun anAnsweredPetitionDoesNotBlockItsRole() {
        // Only OPEN petitions occupy a role's single slot; a decided audience is history.
        val roles = rolesEligibleForNewPetitions(
            petitions = listOf(petition(targetRole = Leader.TREASURER, status = PetitionStatus.ANSWERED)),
            filledRoles = setOf(Leader.TREASURER),
        )
        assertEquals(listOf(Leader.TREASURER), roles)
    }

    @Test
    fun thePerTurnCapOfTwoHoldsWhenAllEightRolesAreEligible() {
        // Without this cap eight filled roles mean eight petitions a turn.
        val roles = rolesEligibleForNewPetitions(emptyList(), Leader.entries.toSet())
        assertEquals(listOf(Leader.RULER, Leader.COUNSELOR), roles)
    }

    @Test
    fun eligibleRolesComeBackInLeaderDeclarationOrder() {
        // Determinism: a Set's iteration order must not decide who petitions the crown.
        val scrambled = setOf(Leader.WARDEN, Leader.RULER, Leader.MAGISTER)
        val roles = rolesEligibleForNewPetitions(emptyList(), scrambled, maxPerTurn = 8)
        assertEquals(listOf(Leader.RULER, Leader.MAGISTER, Leader.WARDEN), roles)
    }

    @Test
    fun anUnfilledRoleIsNeverEligible() {
        // A vacant office has nobody to petition; only filled slots draw audiences.
        val roles = rolesEligibleForNewPetitions(emptyList(), setOf(Leader.GENERAL), maxPerTurn = 8)
        assertEquals(listOf(Leader.GENERAL), roles)
    }

    @Test
    fun anEmptyRosterCastsNobody() {
        // The plan forbids inventing a name: no roster, no petition.
        assertNull(castPetitioner(emptyList(), preferredOccupation = "farmer"))
    }

    @Test
    fun aPetitionerMatchingTheOccupationIsPreferredOverRosterOrder() {
        val roster = listOf(
            PetitionerCandidate("n1", "Kesten", "guard"),
            PetitionerCandidate("n2", "Svetlana", "farmer"),
        )
        assertEquals("n2", castPetitioner(roster, "farmer")?.id)
    }

    @Test
    fun occupationMatchingIgnoresCaseAndPadding() {
        // Occupations are typed by a GM into the roster; " Farmer " must still cast.
        val roster = listOf(
            PetitionerCandidate("n1", "Kesten", "guard"),
            PetitionerCandidate("n2", "Svetlana", " Farmer "),
        )
        assertEquals("n2", castPetitioner(roster, "  farmer")?.id)
    }

    @Test
    fun theFirstRosterEntryIsCastWhenNoOccupationMatches() {
        val roster = listOf(
            PetitionerCandidate("n1", "Kesten", "guard"),
            PetitionerCandidate("n2", "Svetlana", "farmer"),
        )
        assertEquals("n1", castPetitioner(roster, "alchemist")?.id)
    }

    @Test
    fun aTemplateNamingNoOccupationCastsTheFirstRosterEntry() {
        val cast = assertNotNull(castPetitioner(listOf(PetitionerCandidate("n1", "Kesten", "guard")), null))
        assertEquals("n1", cast.id)
    }

    @Test
    fun theCapTrimsAnsweredAndExpiredOnlyOldestFirst() {
        val closed = (1..PETITION_HISTORY_CAP).map {
            petition(
                id = "c$it",
                status = if (it % 2 == 0) PetitionStatus.ANSWERED else PetitionStatus.EXPIRED,
                resolvedTurn = it,
            )
        }
        val withOpen = closed + petition(id = "open")
        val out = appendPetition(withOpen, petition(id = "new", status = PetitionStatus.ANSWERED, resolvedTurn = 200))
        assertEquals(PETITION_HISTORY_CAP, out.count { it.status != PetitionStatus.OPEN })
        assertTrue(out.any { it.id == "open" }, "an open petition must never be pruned")
        assertFalse(out.any { it.id == "c1" }, "the oldest closed petition goes first")
    }

    @Test
    fun anOpenPetitionIsNeverPrunedEvenPastTheCap() {
        // Trimming an unanswered petition silently robs a player of a decision.
        val open = (1..PETITION_HISTORY_CAP + 20).map { petition(id = "o$it") }
        val out = appendPetition(open, petition(id = "new"))
        assertEquals(open.size + 1, out.size)
    }

    @Test
    fun unreadCountsOnlyOpenPetitionsInOwnedRolesMinusSeenIds() {
        // Deliberately asymmetric: 2 unseen vs 1 seen. A symmetric fixture (1 vs 1) cannot tell
        // "minus seen" apart from "only seen", and an EXPIRED row in an owned role pins that only
        // OPEN petitions count -- both mutants an adversarial review found surviving here.
        val petitions = listOf(
            petition(id = "unseen1", targetRole = Leader.MAGISTER),
            petition(id = "unseen2", targetRole = Leader.MAGISTER),
            petition(id = "seen", targetRole = Leader.MAGISTER),
            petition(id = "othersRole", targetRole = Leader.WARDEN),
            petition(id = "decided", targetRole = Leader.MAGISTER, status = PetitionStatus.ANSWERED),
            petition(id = "lapsed", targetRole = Leader.MAGISTER, status = PetitionStatus.EXPIRED),
        )
        assertEquals(2, unreadPetitionCount(petitions, setOf(Leader.MAGISTER), seenIds = setOf("seen")))
    }

    @Test
    fun unreadIsZeroForAUserOwningNoRoles() {
        // A user owning no roles gets no badge, however full the inbox is.
        val petitions = listOf(petition(id = "p1"), petition(id = "p2", targetRole = Leader.RULER))
        assertEquals(0, unreadPetitionCount(petitions, emptySet(), emptySet()))
    }

    @Test
    fun unknownStoredValuesMapToNullRatherThanThrowing() {
        // An unrecognised stored status drops that petition rather than emptying every inbox.
        assertNull(PetitionStatus.fromValue("pending"))
        assertNull(PetitionStatus.fromValue(null))
        assertEquals(PetitionStatus.ANSWERED, PetitionStatus.fromValue("answered"))
    }

    @Test
    fun weightedPickIsProportionalAndSkipsNonPositiveWeights() {
        val choices = listOf(
            PetitionTemplateChoice("a", 1),
            PetitionTemplateChoice("never", 0),
            PetitionTemplateChoice("b", 2),
        )
        // roll 0 lands in a's single slot; 1 and 2 in b's two -- the zero-weight entry occupies
        // no slot at all rather than a zero-width one that a boundary roll could still hit
        assertEquals("a", weightedPickPetition(choices, 0)?.id)
        assertEquals("b", weightedPickPetition(choices, 1)?.id)
        assertEquals("b", weightedPickPetition(choices, 2)?.id)
    }

    @Test
    fun anOutOfRangeRollPicksNothingRatherThanTheFirstTemplate() {
        // a bad draw must not silently bias the catalog toward whatever sorts first
        val choices = listOf(PetitionTemplateChoice("a", 1), PetitionTemplateChoice("b", 1))
        assertNull(weightedPickPetition(choices, -1))
        assertNull(weightedPickPetition(choices, 2))
        assertNull(weightedPickPetition(emptyList(), 0))
    }

    @Test
    fun everyOfficeReachesTheFrontOfTheQueueAcrossTurns() {
        // THE fairness property: rolesEligibleForNewPetitions returns declaration order, and the
        // per-turn cap of 2 would otherwise hand every petition to RULER and COUNSELOR while
        // WARDEN -- last in the enum -- never received one at all.
        val all = Leader.entries.toList()
        val firstPlaces = (0 until all.size).map { turn ->
            rotatePetitionCandidates(all, turn).first()
        }.toSet()
        assertEquals(all.toSet(), firstPlaces)
    }

    @Test
    fun theRotationIsStableWithinATurn() {
        // same turn, same order: a Turn Wizard preview and the End Turn commit must agree
        val all = Leader.entries.toList()
        assertEquals(rotatePetitionCandidates(all, 3), rotatePetitionCandidates(all, 3))
        // and it is a rotation, not a shuffle -- no role is lost or duplicated
        assertEquals(all.toSet(), rotatePetitionCandidates(all, 5).toSet())
        assertEquals(all.size, rotatePetitionCandidates(all, 5).size)
    }

    @Test
    fun rotationSurvivesADegenerateCandidateList() {
        assertEquals(emptyList(), rotatePetitionCandidates(emptyList(), 3))
        assertEquals(listOf(Leader.WARDEN), rotatePetitionCandidates(listOf(Leader.WARDEN), 3))
        // a negative turn must not throw or index backwards off the list
        assertEquals(2, rotatePetitionCandidates(listOf(Leader.RULER, Leader.WARDEN), -1).size)
    }
}
