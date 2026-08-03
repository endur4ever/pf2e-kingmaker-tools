package at.posselt.pfrpg2e.utils

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression tests for [escapeHtml]: the original DOM-based implementation returned
 * its input unchanged, so chat/roll flavor text was posted unescaped.
 */
class EscapeHtmlTest {

    @Test
    fun escapesMarkupCharacters() {
        assertEquals(
            "&lt;b onload=&quot;x()&quot;&gt;Bold&lt;/b&gt;",
            escapeHtml("<b onload=\"x()\">Bold</b>"),
        )
    }

    @Test
    fun escapesAmpersandFirstSoEntitiesDoNotDoubleEscape() {
        assertEquals("&amp;lt;already&amp;gt;", escapeHtml("&lt;already&gt;"))
        assertEquals("Fish &amp; Chips", escapeHtml("Fish & Chips"))
    }

    @Test
    fun escapesQuotesForAttributeContexts() {
        assertEquals("Amiri&#39;s &quot;Axe&quot;", escapeHtml("Amiri's \"Axe\""))
    }

    @Test
    fun leavesPlainTextUntouched()  {
        assertEquals("The Stag Lord approaches", escapeHtml("The Stag Lord approaches"))
    }
}
