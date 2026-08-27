package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import at.posselt.pfrpg2e.data.kingdom.ContributionKind
import at.posselt.pfrpg2e.data.kingdom.DeedCategory
import at.posselt.pfrpg2e.data.kingdom.MAX_POPULACE_RENOWN
import at.posselt.pfrpg2e.data.kingdom.leaders.Leader
import at.posselt.pfrpg2e.kingdom.data.toModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordContributionTest {
    private fun kingdom(): KingdomData {
        val k = js("{}").unsafeCast<KingdomData>()
        k.renown = emptyArray()
        k.currentTurnContributions = emptyArray()
        k.currentTurnDeeds = emptyArray()
        return k
    }

    private fun populaceOf(k: KingdomData, uuid: String): Int? =
        k.renown?.firstOrNull { it.actorUuid == uuid }?.populace

    private fun tallyOf(k: KingdomData, uuid: String) =
        k.currentTurnContributions?.firstOrNull { it.actorUuid == uuid }?.toModel()

    private fun record(
        k: KingdomData,
        deedId: String,
        uuid: String = "pc-1",
        kind: ContributionKind = ContributionKind.CHECK_SUCCESS,
        faction: String? = null,
        name: String? = "Alice",
    ) = recordContribution(
        kingdom = k, deedId = deedId, actorUuid = uuid, actorName = name,
        kind = kind, leader = Leader.RULER, category = DeedCategory.DIPLOMATIC, factionName = faction,
    )

    @Test
    fun aSuccessCreditsPopulaceAndCountsAsOneCheck() {
        val k = kingdom()
        record(k, "d1")
        assertEquals(1, populaceOf(k, "pc-1"))
        assertEquals(1, tallyOf(k, "pc-1")?.checks)
        assertEquals(0, tallyOf(k, "pc-1")?.crits)
        assertEquals(1, k.currentTurnDeeds?.size)
    }

    @Test
    fun aCritCountsAsBothACritAndACheck() {
        val k = kingdom()
        record(k, "d1", kind = ContributionKind.CHECK_CRIT)
        val tally = tallyOf(k, "pc-1")
        assertEquals(1, tally?.crits)
        assertEquals(1, tally?.checks, "a crit is still a check the PC made")
        assertEquals(3, populaceOf(k, "pc-1"))
    }

    @Test
    fun aReRollReplacesTheDeedRatherThanCreditingItTwice() {
        val k = kingdom()
        record(k, "d1", kind = ContributionKind.CHECK_FAILURE)
        assertEquals(0, populaceOf(k, "pc-1"))
        assertEquals(1, tallyOf(k, "pc-1")?.checks)

        // same deedId: the re-roll landed a crit
        record(k, "d1", kind = ContributionKind.CHECK_CRIT)
        assertEquals(3, populaceOf(k, "pc-1"), "credited once, at the FINAL degree")
        val tally = tallyOf(k, "pc-1")
        assertEquals(1, tally?.checks, "still one check, not two")
        assertEquals(1, tally?.crits)
        assertEquals(1, k.currentTurnDeeds?.size, "one deed row, replaced in place")
    }

    @Test
    fun aReRollDownwardGivesBackExactlyWhatWasCredited() {
        val k = kingdom()
        record(k, "d1", kind = ContributionKind.CHECK_CRIT)
        record(k, "d1", kind = ContributionKind.CHECK_CRIT_FAIL)
        assertEquals(0, populaceOf(k, "pc-1"), "3 credited, 3 taken back, then -2 clamped at the floor")
        val tally = tallyOf(k, "pc-1")
        assertEquals(0, tally?.crits, "the crit that was re-rolled away stops counting")
        assertEquals(1, tally?.critFails)
        assertEquals(1, tally?.checks)
    }

    @Test
    fun aReRollAtTheCeilingRefundsOnlyWhatActuallyLanded() {
        // the load-bearing case for storing APPLIED deltas: at 99, a crit worth +3 lands only +1
        val k = kingdom()
        repeat(99) { index -> record(k, "warmup-$index") }
        assertEquals(99, populaceOf(k, "pc-1"))
        record(k, "d-final", kind = ContributionKind.CHECK_CRIT)
        assertEquals(MAX_POPULACE_RENOWN, populaceOf(k, "pc-1"), "clamped at the ceiling")
        record(k, "d-final", kind = ContributionKind.CHECK_FAILURE)
        assertEquals(99, populaceOf(k, "pc-1"),
            "only the +1 that actually landed is taken back -- refunding 3 would mint renown")
    }

    @Test
    fun aReRollCannotMoveCreditBetweenPlayers() {
        val k = kingdom()
        record(k, "d1", uuid = "pc-1", kind = ContributionKind.CHECK_CRIT)
        // the same deed id arriving for a different PC: pc-1's credit must be undone, not pc-2's
        record(k, "d1", uuid = "pc-2", name = "Bob", kind = ContributionKind.CHECK_SUCCESS)
        assertEquals(0, populaceOf(k, "pc-1"), "the original credit is reversed on its own ledger")
        assertEquals(1, populaceOf(k, "pc-2"))
        assertEquals(0, tallyOf(k, "pc-1")?.checks)
        assertEquals(1, tallyOf(k, "pc-2")?.checks)
    }

    @Test
    fun factionRenownTracksTheNamedGroupAndReversesWithIt() {
        val k = kingdom()
        record(k, "d1", kind = ContributionKind.CHECK_CRIT, faction = "Pitax")
        val row = k.renown?.first()?.factionRenown?.firstOrNull { it.factionName == "Pitax" }
        assertEquals(2, row?.renown)
        record(k, "d1", kind = ContributionKind.CHECK_FAILURE, faction = "Pitax")
        val after = k.renown?.first()?.factionRenown?.firstOrNull { it.factionName == "Pitax" }
        assertTrue(after == null || after.renown == 0, "the faction credit came back too")
    }

    @Test
    fun separateDeedsAccumulateRatherThanReplacing() {
        val k = kingdom()
        record(k, "d1")
        record(k, "d2")
        record(k, "d3")
        assertEquals(3, populaceOf(k, "pc-1"))
        assertEquals(3, tallyOf(k, "pc-1")?.checks)
        assertEquals(3, k.currentTurnDeeds?.size)
    }

    @Test
    fun aBlankIdentityRecordsNothingAtAll() {
        val k = kingdom()
        record(k, "d1", uuid = "")
        record(k, "", uuid = "pc-1")
        assertEquals(0, k.renown?.size)
        assertEquals(0, k.currentTurnDeeds?.size)
    }

    @Test
    fun anIncomingNameRefreshesButNeverErasesTheStoredOne() {
        val k = kingdom()
        record(k, "d1", name = "Alice")
        record(k, "d2", name = null)
        assertEquals("Alice", k.renown?.first()?.actorName, "a nameless call must not blank the ledger")
        record(k, "d3", name = "Alice the Bold")
        assertEquals("Alice the Bold", k.renown?.first()?.actorName)
    }

    @Test
    fun epithetsAndTierSurviveAReRollBecauseTheyAreGrantedNotDerived() {
        val k = kingdom()
        record(k, "d1", kind = ContributionKind.CHECK_CRIT)
        k.renown?.first()?.epithets = arrayOf("bridgeBuilder")
        k.renown?.first()?.purchaseAccessTier = 2
        record(k, "d1", kind = ContributionKind.CHECK_CRIT_FAIL)
        assertEquals(listOf("bridgeBuilder"), k.renown?.first()?.epithets?.toList(),
            "no re-roll may take back a GM-confirmed honour")
        assertEquals(2, k.renown?.first()?.purchaseAccessTier)
    }

    @Test
    fun aDeedThatOutlivedItsTallyRowCannotDriveTheCounterNegative() {
        // End Turn clears currentTurnContributions; a deed row that survives that reset (an
        // interrupted flush, a hand-edited world) would decrement a tally that was never written.
        // The counters are floored so the Spotlight cannot end up reporting "-1 checks".
        val k = kingdom()
        record(k, "d1", kind = ContributionKind.CHECK_CRIT)
        k.currentTurnContributions = emptyArray()   // the reset, without the deeds being cleared
        record(k, "d1", kind = ContributionKind.CHECK_SUCCESS)
        val tally = tallyOf(k, "pc-1")
        assertEquals(1, tally?.checks, "the new credit lands")
        assertEquals(0, tally?.crits, "and the stale decrement floors at zero rather than going negative")
        assertTrue((tally?.crits ?: -1) >= 0)
        assertTrue((tally?.checks ?: -1) >= 0)
    }

    @Test
    fun degreesMapOntoTheKindsTheLedgerCredits() {
        assertEquals(ContributionKind.CHECK_CRIT, contributionKindFor(DegreeOfSuccess.CRITICAL_SUCCESS))
        assertEquals(ContributionKind.CHECK_SUCCESS, contributionKindFor(DegreeOfSuccess.SUCCESS))
        assertEquals(ContributionKind.CHECK_FAILURE, contributionKindFor(DegreeOfSuccess.FAILURE))
        assertEquals(ContributionKind.CHECK_CRIT_FAIL, contributionKindFor(DegreeOfSuccess.CRITICAL_FAILURE))
    }

    @Test
    fun anUnknownPriorDeedDoesNotStrandTheNewCredit() {
        // a deed row from an older build with an unreadable kind: the revert cannot be computed,
        // but the new credit must still land rather than being swallowed
        val k = kingdom()
        record(k, "d1")
        k.currentTurnDeeds?.first()?.kind = "not-a-kind-this-build-knows"
        record(k, "d1", kind = ContributionKind.CHECK_CRIT)
        assertEquals(1, k.currentTurnDeeds?.size)
        assertTrue((populaceOf(k, "pc-1") ?: 0) > 0)
    }

    @Test
    fun aFirstDeedOnAnEmptyKingdomSeedsTheLedgerRatherThanThrowing() {
        val k = js("{}").unsafeCast<KingdomData>()   // all three arrays absent, as on an old world
        recordContribution(
            kingdom = k, deedId = "d1", actorUuid = "pc-1", actorName = "Alice",
            kind = ContributionKind.CHECK_SUCCESS, leader = Leader.RULER,
        )
        assertEquals(1, k.renown?.size)
        assertEquals(1, k.currentTurnDeeds?.size)
        assertEquals(1, k.currentTurnContributions?.size)
        assertNull(k.renown?.first()?.factionRenown?.firstOrNull())
    }
}

class RenownOfferTest {
    private fun renownRow(
        uuid: String = "pc-1",
        populace: Int = 0,
        epithets: Array<String>? = null,
        lastOffered: Int? = null,
    ): at.posselt.pfrpg2e.kingdom.data.RawPcRenown {
        val obj = js("{}").unsafeCast<at.posselt.pfrpg2e.kingdom.data.RawPcRenown>()
        obj.actorUuid = uuid
        obj.actorName = "Alice"
        obj.populace = populace
        obj.epithets = epithets
        obj.lastOfferedTurn = lastOffered
        return obj
    }

    @Test
    fun anEarnedEpithetIsOfferedOnce() {
        // populace >= 50 earns peoplesChampion per the authored catalog
        val rows = arrayOf(renownRow(populace = 55))
        val offers = pendingEpithetOffers(rows, rulerUuid = null, turn = 5)
        assertEquals(1, offers.size)
        assertEquals("peoplesChampion", offers.single().award.epithetId)
    }

    @Test
    fun anEpithetAlreadyHeldIsNeverReOffered() {
        val rows = arrayOf(renownRow(populace = 55, epithets = arrayOf("peoplesChampion")))
        assertTrue(pendingEpithetOffers(rows, rulerUuid = null, turn = 5).isEmpty())
    }

    @Test
    fun aPcAlreadyOfferedThisTurnIsSkipped() {
        // the GM has a card sitting in chat undecided; a second End Turn must not stack another
        val rows = arrayOf(renownRow(populace = 55, lastOffered = 5))
        assertTrue(pendingEpithetOffers(rows, rulerUuid = null, turn = 5).isEmpty())
        assertEquals(1, pendingEpithetOffers(rows, rulerUuid = null, turn = 6).size)
    }

    @Test
    fun theRulerOnlyEpithetNeedsTheRulerRole() {
        val rows = arrayOf(renownRow(populace = 80))
        val asRuler = pendingEpithetOffers(rows, rulerUuid = "pc-1", turn = 1).map { it.award.epithetId }
        val notRuler = pendingEpithetOffers(rows, rulerUuid = "someone-else", turn = 1).map { it.award.epithetId }
        assertTrue("theKingmaker" in asRuler)
        assertTrue("theKingmaker" !in notRuler, "the crown is the condition, not the renown alone")
    }

    @Test
    fun aRowWithoutAnActorIsSkippedRatherThanOfferedToNobody() {
        val row = renownRow(populace = 55)
        row.actorUuid = null
        assertTrue(pendingEpithetOffers(arrayOf(row), rulerUuid = null, turn = 1).isEmpty())
    }
}
