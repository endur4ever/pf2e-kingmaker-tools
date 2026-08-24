package at.posselt.pfrpg2e.camping

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Migration 17 rewrote homebrew recipe costs from a free-text string into a structured cost. Its
 * regex demanded whitespace between the number and the currency, so anything a GM typed without one
 * matched nothing and fell back to zero -- silently wiping the price off the recipe.
 */
class LegacyRecipeCostTest {
    @Test
    fun theCanonicalSpacedFormParses() {
        assertEquals(LegacyCost("gp", 5), parseLegacyRecipeCost("5 gp"))
    }

    @Test
    fun aCostTypedWithoutASpaceKeepsItsValue() {
        // The original regex required \s+ here, so this became 0 gp.
        assertEquals(LegacyCost("gp", 5), parseLegacyRecipeCost("5gp"))
    }

    @Test
    fun currencyCaseIsIgnored() {
        assertEquals(LegacyCost("gp", 5), parseLegacyRecipeCost("5 GP"))
        assertEquals(LegacyCost("sp", 12), parseLegacyRecipeCost("12Sp"))
    }

    @Test
    fun surroundingAndInternalWhitespaceIsTolerated() {
        assertEquals(LegacyCost("sp", 12), parseLegacyRecipeCost("  12   sp  "))
    }

    @Test
    fun everyCurrencyIsRecognised() {
        listOf("cp", "sp", "gp", "pp").forEach {
            assertEquals(LegacyCost(it, 3), parseLegacyRecipeCost("3 $it"), "failed for $it")
        }
    }

    @Test
    fun aBareNumberKeepsItsValueAndDefaultsToGold() {
        // Losing the number is the damage; assuming gold is the same assumption the old fallback
        // already made, so this strictly improves on it.
        assertEquals(LegacyCost("gp", 7), parseLegacyRecipeCost("7"))
    }

    @Test
    fun anUnknownCurrencyStillKeepsTheNumber() {
        assertEquals(LegacyCost("gp", 5), parseLegacyRecipeCost("5 zorkmids"))
    }

    @Test
    fun textWithNoNumberIsZero() {
        assertEquals(LegacyCost("gp", 0), parseLegacyRecipeCost("free"))
        assertEquals(LegacyCost("gp", 0), parseLegacyRecipeCost(""))
    }

    @Test
    fun aLeadingNumberIsSalvagedFromMessyText() {
        assertEquals(LegacyCost("gp", 25), parseLegacyRecipeCost("25 gp per meal"))
    }
}

/** The salvage path must not read a currency out of the middle of an ordinary word. */
class LegacyRecipeCostSalvageTest {
    @Test
    fun copperIsNotReadAsPlatinum() {
        // "copper" contains the substring "pp"; a naive contains-check made this 5 platinum,
        // a hundredfold price error.
        assertEquals(LegacyCost("gp", 5), parseLegacyRecipeCost("5 copper pieces"))
    }

    @Test
    fun aStandaloneAbbreviationInMessyTextIsStillFound() {
        assertEquals(LegacyCost("sp", 8), parseLegacyRecipeCost("8 sp per portion"))
    }

    @Test
    fun aCurrencyFoundBySalvageIsAlsoLowercased() {
        // The strict path lowercases; the salvage path must agree, or the same recipe stores
        // "SP" or "sp" depending on how the GM typed the rest of the line.
        assertEquals(LegacyCost("sp", 8), parseLegacyRecipeCost("8 SP per portion"))
    }

    @Test
    fun anAbbreviationInsideAWordIsIgnored() {
        assertEquals(LegacyCost("gp", 2), parseLegacyRecipeCost("2 supper"))
    }
}
