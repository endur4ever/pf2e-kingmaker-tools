package at.posselt.pfrpg2e.data.kingdom

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MilestoneOfferStateTest {
    @Test
    fun anUnansweredMilestoneIsStillOfferable() {
        assertFalse(milestoneOfferAnswered(completed = false, offerDismissed = false))
    }

    @Test
    fun awardingAnswersIt() {
        assertTrue(milestoneOfferAnswered(completed = true, offerDismissed = false))
    }

    @Test
    fun dismissingAlsoAnswersIt() {
        // The whole point of the field: declining is an answer. Without this, detection re-posts
        // the identical offer on every subsequent End Turn forever, because the road stays built.
        assertTrue(milestoneOfferAnswered(completed = false, offerDismissed = true))
    }

    @Test
    fun aKingdomSavedBeforeTheFieldExistedIsTreatedAsNotYetAsked() {
        // Absent must mean "not refused" -- the opposite default would silently swallow the offer
        // for every pre-existing campaign, and the milestone would never be awardable.
        assertFalse(milestoneOfferAnswered(completed = false, offerDismissed = null))
        assertTrue(milestoneOfferAnswered(completed = true, offerDismissed = null))
    }
}
