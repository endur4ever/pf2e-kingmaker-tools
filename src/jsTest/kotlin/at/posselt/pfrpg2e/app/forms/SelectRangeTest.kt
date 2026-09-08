package at.posselt.pfrpg2e.app.forms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelectRangeTest {
    private fun values(select: Select) = select.options.map { it.value }

    @Test
    fun rangeCoversFromTo() {
        assertEquals(listOf("0", "1", "2"), values(Select.range(label = "l", name = "n", from = 0, to = 2, value = 1)))
    }

    @Test
    fun rangeStretchesToIncludeAValueAboveIt() {
        // Commodities held above their storage cap. A select with no matching option renders as
        // its first option and submits that, so the stock silently became zero on the next save.
        val select = Select.range(label = "l", name = "n", from = 0, to = 2, value = 5)
        assertTrue("5" in values(select), "expected the held value to be selectable, got ${values(select)}")
        assertEquals("5", select.value)
    }

    @Test
    fun rangeStretchesToIncludeAValueBelowIt() {
        val select = Select.range(label = "l", name = "n", from = 3, to = 5, value = 1)
        assertTrue("1" in values(select), "expected the held value to be selectable, got ${values(select)}")
    }
}
