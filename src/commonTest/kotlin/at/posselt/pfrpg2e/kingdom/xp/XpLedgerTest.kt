package at.posselt.pfrpg2e.kingdom.xp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Covers the cases §9 of the party XP ledger plan enumerates. */
class XpLedgerTest {
    private fun entry(
        id: String = "e1",
        sourceKind: XpSourceKind = XpSourceKind.SITE_CLEARED,
        sourceRef: String = "hex.3.4",
        proposedAmount: Int = 30,
        grantedAmount: Int? = null,
        status: XpOfferStatus = XpOfferStatus.OFFERED,
    ) = XpLedgerEntry(
        id = id, turn = 1, timestamp = "2026-07-09T12:00:00Z", sourceKind = sourceKind,
        sourceRef = sourceRef, proposedAmount = proposedAmount, grantedAmount = grantedAmount,
        status = status,
    )

    @Test
    fun aRepeatSourceKindAndRefIsRefusedInAnyStatus() {
        // The hex-cleared-twice case: re-populated by the GM and cleared again, the transition
        // reports the same key, and the party must not be paid twice for one site.
        val offered = entry(id = "e1")
        assertNull(proposeEntry(listOf(offered), entry(id = "e2")))
        val confirmed = entry(id = "e3", status = XpOfferStatus.CONFIRMED, grantedAmount = 30)
        assertNull(proposeEntry(listOf(confirmed), entry(id = "e4")))
    }

    @Test
    fun aDismissedOfferMustNotRepropose() {
        // Dismissal is an answer, not a deferral: the GM already declined this beat once, and it
        // must not come back on the next digest.
        val dismissed = entry(id = "e1", status = XpOfferStatus.DISMISSED)
        assertNull(proposeEntry(listOf(dismissed), entry(id = "e2")))
    }

    @Test
    fun theSameRefUnderADifferentKindIsADifferentBeat() {
        // Reconnoitering hex 3.4 and later clearing the site on it are two separate awards.
        val reconnoitered = entry(id = "e1", sourceKind = XpSourceKind.HEX_RECONNOITERED)
        val candidate = entry(id = "e2", sourceKind = XpSourceKind.SITE_CLEARED)
        assertEquals(candidate, proposeEntry(listOf(reconnoitered), candidate))
    }

    @Test
    fun aFirstEntryForAKeyIsProposedUnchanged() {
        val candidate = entry()
        assertEquals(candidate, proposeEntry(emptyList(), candidate))
    }

    @Test
    fun aManualDuplicateIsAlwaysAllowed() {
        // The deliberate override: a GM who genuinely wants a second award for the same ref adds
        // it as MANUAL, which shows up as such in the history.
        val manual = entry(id = "e1", sourceKind = XpSourceKind.MANUAL)
        val again = entry(id = "e2", sourceKind = XpSourceKind.MANUAL)
        assertNotNull(proposeEntry(listOf(manual), again))
    }

    @Test
    fun theCapTrimsTheOldestAnsweredEntryAndStepsOverOffers() {
        val open = entry(id = "open", sourceRef = "pending")
        val answered = (1..3).map {
            entry(id = "a$it", sourceRef = "r$it", status = XpOfferStatus.CONFIRMED, grantedAmount = 10)
        }
        val out = appendEntry(
            listOf(open) + answered,
            entry(id = "a4", sourceRef = "r4", status = XpOfferStatus.DISMISSED),
            cap = 3,
        )
        assertEquals(listOf("open", "a2", "a3", "a4"), out.map { it.id })
    }

    @Test
    fun aLedgerExactlyAtTheCapIsLeftUntouched() {
        val answered = (1..2).map {
            entry(id = "a$it", sourceRef = "r$it", status = XpOfferStatus.CONFIRMED, grantedAmount = 10)
        }
        val out = appendEntry(
            answered,
            entry(id = "a3", sourceRef = "r3", status = XpOfferStatus.CONFIRMED, grantedAmount = 10),
            cap = 3,
        )
        assertEquals(listOf("a1", "a2", "a3"), out.map { it.id })
    }

    @Test
    fun anOfferedEntryIsNeverPrunedEvenWhenOffersAloneExceedTheCap() {
        // An unanswered offer is pending XP the party earned; offers past the cap signal a bug in
        // the offer generator, and pruning must not paper over it by losing awards.
        val offers = (1..10).map { entry(id = "o$it", sourceRef = "r$it") }
        val out = appendEntry(offers, entry(id = "o11", sourceRef = "r11"), cap = 3)
        assertEquals(11, out.size)
        assertTrue(out.all { it.status == XpOfferStatus.OFFERED })
    }

    @Test
    fun theDefaultCapIsFiveHundredAnsweredEntries() {
        val answered = (1..XP_LEDGER_CAP).map {
            entry(id = "a$it", sourceRef = "r$it", status = XpOfferStatus.CONFIRMED, grantedAmount = 1)
        }
        val out = appendEntry(
            answered,
            entry(id = "new", sourceRef = "rNew", status = XpOfferStatus.CONFIRMED, grantedAmount = 1),
        )
        assertEquals(XP_LEDGER_CAP, out.size)
        assertFalse(out.any { it.id == "a1" }, "the oldest answered entry goes first")
        assertTrue(out.any { it.id == "new" })
    }

    @Test
    fun confirmedTotalCountsGrantedNotProposed() {
        // The confirm row's amount field is editable; an edited-down confirm must report what was
        // actually granted, never the proposed figure.
        val edited = entry(status = XpOfferStatus.CONFIRMED, proposedAmount = 80, grantedAmount = 30)
        assertEquals(30, confirmedTotal(listOf(edited)))
    }

    @Test
    fun confirmedTotalTreatsANullGrantAsZeroRatherThanBorrowingTheProposal() {
        val odd = entry(status = XpOfferStatus.CONFIRMED, proposedAmount = 80, grantedAmount = null)
        assertEquals(0, confirmedTotal(listOf(odd)))
    }

    @Test
    fun confirmedTotalIgnoresOfferedAndDismissedEntries() {
        val entries = listOf(
            entry(id = "c", sourceRef = "r1", status = XpOfferStatus.CONFIRMED, grantedAmount = 30),
            entry(id = "o", sourceRef = "r2", status = XpOfferStatus.OFFERED, proposedAmount = 100),
            entry(id = "d", sourceRef = "r3", status = XpOfferStatus.DISMISSED, grantedAmount = 50),
        )
        assertEquals(30, confirmedTotal(entries))
    }

    @Test
    fun totalsByKindSumsConfirmedGrantsPerKind() {
        val entries = listOf(
            entry(id = "1", sourceRef = "r1", status = XpOfferStatus.CONFIRMED, grantedAmount = 30),
            entry(id = "2", sourceRef = "r2", status = XpOfferStatus.CONFIRMED, grantedAmount = 80),
            entry(
                id = "3", sourceKind = XpSourceKind.QUEST_COMPLETED, sourceRef = "q1",
                status = XpOfferStatus.CONFIRMED, grantedAmount = 30,
            ),
        )
        assertEquals(
            mapOf(XpSourceKind.SITE_CLEARED to 110, XpSourceKind.QUEST_COMPLETED to 30),
            totalsByKind(entries),
        )
    }

    @Test
    fun totalsByKindOmitsKindsWithNothingConfirmed() {
        // Dismissed and offered entries granted nothing; their kinds must be absent, not zero.
        val entries = listOf(
            entry(id = "o", sourceKind = XpSourceKind.HEX_RECONNOITERED, sourceRef = "h1"),
            entry(
                id = "d", sourceKind = XpSourceKind.QUEST_COMPLETED, sourceRef = "q1",
                status = XpOfferStatus.DISMISSED, grantedAmount = 30,
            ),
            entry(id = "c", sourceRef = "s1", status = XpOfferStatus.CONFIRMED, grantedAmount = 10),
        )
        val totals = totalsByKind(entries)
        assertEquals(mapOf(XpSourceKind.SITE_CLEARED to 10), totals)
        assertNull(totals[XpSourceKind.HEX_RECONNOITERED])
        assertNull(totals[XpSourceKind.QUEST_COMPLETED])
    }

    @Test
    fun reconcileReportsPositiveDriftWhenThePartyHoldsMore() {
        // Combat XP and hand edits never pass through the ledger, so a party holding more than
        // the ledger granted is the expected steady state — information, not an error.
        val entries = listOf(entry(status = XpOfferStatus.CONFIRMED, grantedAmount = 100))
        val reconciliation = reconcile(entries, actualLifetimeXp = 350)
        assertEquals(100, reconciliation.ledgerTotal)
        assertEquals(350, reconciliation.actualLifetimeXp)
        assertEquals(250, reconciliation.drift)
    }

    @Test
    fun reconcileReportsZeroDriftOnAnExactMatch() {
        val entries = listOf(entry(status = XpOfferStatus.CONFIRMED, grantedAmount = 100))
        assertEquals(0, reconcile(entries, actualLifetimeXp = 100).drift)
    }

    @Test
    fun reconcileReportsNegativeDriftWhenTheLedgerExceedsActual() {
        // A hand edit downward is just as visible; the report stays a number, never a write-back.
        val entries = listOf(entry(status = XpOfferStatus.CONFIRMED, grantedAmount = 100))
        assertEquals(-40, reconcile(entries, actualLifetimeXp = 60).drift)
    }

    @Test
    fun reconcileMutatesNothing() {
        val entries = listOf(
            entry(id = "c", sourceRef = "r1", status = XpOfferStatus.CONFIRMED, grantedAmount = 100),
            entry(id = "o", sourceRef = "r2"),
        )
        val before = entries.map { it.copy() }
        reconcile(entries, actualLifetimeXp = 999)
        assertEquals(before, entries)
    }

    @Test
    fun unknownStoredValuesMapToNullRatherThanThrowing() {
        assertNull(XpSourceKind.fromValue("combat"))
        assertNull(XpSourceKind.fromValue(null))
        assertNull(XpOfferStatus.fromValue("pending"))
        assertEquals(XpSourceKind.SITE_CLEARED, XpSourceKind.fromValue("siteCleared"))
        assertEquals(XpOfferStatus.DISMISSED, XpOfferStatus.fromValue("dismissed"))
    }
}
