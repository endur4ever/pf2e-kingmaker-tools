package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.MilestoneChoice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MilestoneSubmitMergeTest {
    private fun choice(
        id: String,
        completed: Boolean = false,
        dismissed: Boolean? = null,
        awardedOnTurn: Int? = null,
    ) = MilestoneChoice(id = id, completed = completed, enabled = true).also {
        it.offerDismissed = dismissed
        it.awardedOnTurn = awardedOnTurn
    }

    @Test
    fun submitKeepsDismissalAndAwardTurn() {
        val existing = arrayOf(
            choice("refused", dismissed = true),
            choice("earned", completed = true, awardedOnTurn = 7),
        )
        // the sheet renders id/completed/enabled only, so both extra fields come back missing
        val submitted = arrayOf(choice("refused"), choice("earned", completed = true))
        val merged = mergeSubmittedMilestones(submitted, existing).associateBy { it.id }
        assertEquals(true, merged.getValue("refused").offerDismissed)
        assertEquals(7, merged.getValue("earned").awardedOnTurn)
    }

    @Test
    fun theSubmittedValuesStillWinForRenderedFields() {
        val existing = arrayOf(choice("m", completed = false, dismissed = true, awardedOnTurn = 2))
        val submitted = arrayOf(choice("m", completed = true))
        val merged = mergeSubmittedMilestones(submitted, existing).single()
        assertEquals(true, merged.completed)
        assertEquals(true, merged.offerDismissed)
        assertEquals(2, merged.awardedOnTurn)
    }

    @Test
    fun anIdWithNoPredecessorKeepsItsBlankState() {
        val merged = mergeSubmittedMilestones(arrayOf(choice("new")), arrayOf(choice("other", dismissed = true)))
        assertNull(merged.single().offerDismissed)
        assertNull(merged.single().awardedOnTurn)
    }
}
