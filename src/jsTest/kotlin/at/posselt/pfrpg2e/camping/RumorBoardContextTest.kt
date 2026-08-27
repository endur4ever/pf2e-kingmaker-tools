package at.posselt.pfrpg2e.camping

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RumorBoardContextTest {
    private fun rumor(
        id: String = "r1",
        state: RumorState = RumorState.FRESH,
        veracity: RumorVeracity? = RumorVeracity.DISTORTED,
        born: Int? = 10,
    ) = Rumor(text = "Troll sightings", id = id, state = state, veracity = veracity, bornDay = born)

    @Test
    fun playersSeeTheTextAndTheStateButNeverTheGmFields() {
        val row = buildRumorBoardContext(listOf(rumor()), currentDay = 15, isGM = false).rows.single()
        assertEquals("Troll sightings", row.text)
        assertNull(row.veracityValue, "veracity would turn hearsay into a solved puzzle")
        assertNull(row.veracityLabel)
        assertNull(row.ageDays, "a visible countdown turns a rumor into a quest timer")
        assertFalse(row.canConvert)
    }

    @Test
    fun theGmSeesVeracityAndAge() {
        val row = buildRumorBoardContext(listOf(rumor()), currentDay = 15, isGM = true).rows.single()
        assertEquals("distorted", row.veracityValue)
        assertEquals(5, row.ageDays)
        assertTrue(row.canConvert)
    }

    @Test
    fun aConvertedRumorOffersNoSecondConversion() {
        val row = buildRumorBoardContext(
            listOf(rumor(state = RumorState.CONVERTED)), currentDay = 15, isGM = true,
        ).rows.single()
        assertFalse(row.canConvert)
        assertTrue(row.isConverted)
    }

    @Test
    fun anIdlessRowIsShownButCarriesNoControls() {
        val row = buildRumorBoardContext(listOf(rumor(id = "")), currentDay = 15, isGM = true).rows.single()
        assertEquals("Troll sightings", row.text, "the text is the value; it is not hidden")
        assertFalse(row.canConvert, "every control addresses its rumor by id")
    }

    @Test
    fun aFutureBornDayClampsToZeroRatherThanShowingNegativeAge() {
        // a rewound world clock can put bornDay after today
        val row = buildRumorBoardContext(listOf(rumor(born = 20)), currentDay = 15, isGM = true).rows.single()
        assertEquals(0, row.ageDays)
    }
}
