package at.posselt.pfrpg2e.camping

import kotlin.test.Test
import kotlin.test.assertEquals

class EncounterHexFilterTest {

    @Test
    fun `non-combat category allowed by the claimed-cleared filter`() {
        val categories = listOf(
            EncounterCategory.RP,
            EncounterCategory.RUMOR,
            EncounterCategory.MERCHANT,
            EncounterCategory.DISEASE,
            EncounterCategory.FACTION,
            EncounterCategory.WEATHER,
            EncounterCategory.LORE,
        )
        categories.forEach { cat ->
            // Filter on, claimed+cleared, no content override
            assertEquals(
                EncounterFilterDecision.ALLOW,
                decideEncounterFilter(true, true, true, false, cat)
            )
            // Filter off
            assertEquals(
                EncounterFilterDecision.ALLOW,
                decideEncounterFilter(false, true, true, false, cat)
            )
        }
    }

    @Test
    fun `content override suppresses every category not just combat`() {
        // The per-hex "suppresses encounters" override means NO encounters here — it must beat
        // the non-combat short-circuit (the original ordering made SUPPRESS_ALL unreachable for
        // non-combat rolls, so an encounter-free hex still fired rumors/merchants).
        EncounterCategory.entries.forEach { cat ->
            assertEquals(
                EncounterFilterDecision.SUPPRESS_ALL,
                decideEncounterFilter(true, true, true, true, cat),
                "override must suppress $cat",
            )
            // ...and it ignores the filter toggle entirely
            assertEquals(
                EncounterFilterDecision.SUPPRESS_ALL,
                decideEncounterFilter(false, false, false, true, cat),
                "override must suppress $cat with the toggle off",
            )
        }
    }

    @Test
    fun `combat allowed when filter disabled`() {
        assertEquals(
            EncounterFilterDecision.ALLOW,
            decideEncounterFilter(false, true, true, false, EncounterCategory.COMBAT)
        )
        assertEquals(
            EncounterFilterDecision.ALLOW,
            decideEncounterFilter(false, true, false, false, EncounterCategory.COMBAT)
        )
        assertEquals(
            EncounterFilterDecision.ALLOW,
            decideEncounterFilter(false, false, true, false, EncounterCategory.COMBAT)
        )
        assertEquals(
            EncounterFilterDecision.ALLOW,
            decideEncounterFilter(false, false, false, false, EncounterCategory.COMBAT)
        )
    }

    @Test
    fun `combat allowed when hex not claimed`() {
        assertEquals(
            EncounterFilterDecision.ALLOW,
            decideEncounterFilter(true, false, true, false, EncounterCategory.COMBAT)
        )
        assertEquals(
            EncounterFilterDecision.ALLOW,
            decideEncounterFilter(true, false, false, false, EncounterCategory.COMBAT)
        )
    }

    @Test
    fun `combat allowed when hex not cleared`() {
        assertEquals(
            EncounterFilterDecision.ALLOW,
            decideEncounterFilter(true, true, false, false, EncounterCategory.COMBAT)
        )
    }

    @Test
    fun `combat suppressed when filter enabled and hex claimed and cleared`() {
        assertEquals(
            EncounterFilterDecision.SUPPRESS_COMBAT,
            decideEncounterFilter(true, true, true, false, EncounterCategory.COMBAT)
        )
    }

    @Test
    fun `combat suppressed all when content override true regardless of filter`() {
        // Filter on, claimed+cleared, content override
        assertEquals(
            EncounterFilterDecision.SUPPRESS_ALL,
            decideEncounterFilter(true, true, true, true, EncounterCategory.COMBAT)
        )
        // Filter off, content override
        assertEquals(
            EncounterFilterDecision.SUPPRESS_ALL,
            decideEncounterFilter(false, true, true, true, EncounterCategory.COMBAT)
        )
        // Filter on, not claimed, content override
        assertEquals(
            EncounterFilterDecision.SUPPRESS_ALL,
            decideEncounterFilter(true, false, false, true, EncounterCategory.COMBAT)
        )
    }

    @Test
    fun `content override false does not suppress`() {
        // When content override is false (not true), it doesn't suppress - the filter logic applies
        assertEquals(
            EncounterFilterDecision.SUPPRESS_COMBAT,
            decideEncounterFilter(true, true, true, false, EncounterCategory.COMBAT)
        )
        assertEquals(
            EncounterFilterDecision.ALLOW,
            decideEncounterFilter(false, true, true, false, EncounterCategory.COMBAT)
        )
    }

    @Test
    fun `content override null treated as no override`() {
        assertEquals(
            EncounterFilterDecision.SUPPRESS_COMBAT,
            decideEncounterFilter(true, true, true, null, EncounterCategory.COMBAT)
        )
        assertEquals(
            EncounterFilterDecision.ALLOW,
            decideEncounterFilter(true, false, true, null, EncounterCategory.COMBAT)
        )
        assertEquals(
            EncounterFilterDecision.ALLOW,
            decideEncounterFilter(false, true, true, null, EncounterCategory.COMBAT)
        )
    }
}