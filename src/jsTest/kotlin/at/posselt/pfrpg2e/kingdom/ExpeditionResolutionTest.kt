package at.posselt.pfrpg2e.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals

class ExpeditionResolutionTest {

    @Test
    fun `influenceBandBonus maps discovery bands to a +0, +1, +2 circumstance bonus`() {
        // unknown -> +0; introduced/established -> +1; trusted/bonded -> +2.
        // This is the bonus the linked-actor roll subtracts from the DC and the
        // unlinked d20Resolve adds to the roll — both make a higher band easier.
        assertEquals(0, influenceBandBonus("unknown"))
        assertEquals(1, influenceBandBonus("introduced"))
        assertEquals(1, influenceBandBonus("established"))
        assertEquals(2, influenceBandBonus("trusted"))
        assertEquals(2, influenceBandBonus("bonded"))
    }

    @Test
    fun `influenceBandBonus defaults to +0 for an unrecognized status`() {
        assertEquals(0, influenceBandBonus("nonsense"))
    }
}
