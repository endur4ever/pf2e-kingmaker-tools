package at.posselt.pfrpg2e.kingdom.sheet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ResourceButtonLabelTest {

    @Test
    fun `resolveResourceButtonLabelKey - plain integer uses resource key with count`() {
        val result = resolveResourceButtonLabelKey("3", Resource.RESOURCE_DICE)
        assertEquals("resourceButton.resource.resourceDice", result.i18nKey)
        assertEquals("count", result.interpolationVar)
        assertEquals("3", result.interpolationValue)
    }

    @Test
    fun `resolveResourceButtonLabelKey - dice expression rd uses resourceExpression key with expression`() {
        val result = resolveResourceButtonLabelKey("1d4rd", Resource.RESOURCE_DICE)
        assertEquals("resourceButton.resourceExpression.resourceDice", result.i18nKey)
        assertEquals("expression", result.interpolationVar)
        assertEquals("1d4", result.interpolationValue) // rd stripped for dice check but full value used
    }

    @Test
    fun `resolveResourceButtonLabelKey - dice expression with modifier uses resourceExpression key`() {
        val result = resolveResourceButtonLabelKey("1d6+2", Resource.RESOURCE_POINTS)
        assertEquals("resourceButton.resourceExpression.resourcePoints", result.i18nKey)
        assertEquals("expression", result.interpolationVar)
        assertEquals("1d6+2", result.interpolationValue)
    }

    @Test
    fun `resolveResourceButtonLabelKey - dice expression with minus modifier uses resourceExpression key`() {
        val result = resolveResourceButtonLabelKey("2d4-1", Resource.FOOD)
        assertEquals("resourceButton.resourceExpression.food", result.i18nKey)
        assertEquals("expression", result.interpolationVar)
        assertEquals("2d4-1", result.interpolationValue)
    }

    @Test
    fun `resolveResourceButtonLabelKey - lose mode with integer uses resource key with count`() {
        val result = resolveResourceButtonLabelKey("5", Resource.CRIME)
        assertEquals("resourceButton.resource.crime", result.i18nKey)
        assertEquals("count", result.interpolationVar)
        assertEquals("5", result.interpolationValue)
    }

    @Test
    fun `resolveResourceButtonLabelKey - lose mode with dice expression uses resourceExpression key`() {
        val result = resolveResourceButtonLabelKey("1d4rd", Resource.RESOURCE_DICE)
        // Resource Dice lose also uses resourceExpression
        assertEquals("resourceButton.resourceExpression.resourceDice", result.i18nKey)
        assertEquals("expression", result.interpolationVar)
        assertEquals("1d4", result.interpolationValue)
    }

    @Test
    fun `resolveResourceButtonLabelKey - event uses resource key with eventName handled by caller`() {
        val result = resolveResourceButtonLabelKey("someEvent", Resource.EVENT)
        assertEquals("resourceButton.resource.event", result.i18nKey)
        // Event uses count but caller overrides with eventName
        assertEquals("count", result.interpolationVar)
        assertEquals("someEvent", result.interpolationValue)
    }

    @Test
    fun `resolveResourceButtonLabelKey - rolledResourceDice with dice expression uses resourceExpression key`() {
        val result = resolveResourceButtonLabelKey("1d4+1", Resource.ROLLED_RESOURCE_DICE)
        assertEquals("resourceButton.resourceExpression.rolledResourceDice", result.i18nKey)
        assertEquals("expression", result.interpolationVar)
        assertEquals("1d4+1", result.interpolationValue)
    }

    @Test
    fun `resolveResourceButtonLabelKey - integer rd (e.g., 2rd) uses resource key with a NUMERIC count`() {
        val result = resolveResourceButtonLabelKey("2rd", Resource.RESOURCE_DICE)
        // "2rd" stripped of "rd" -> "2" doesn't match the dice pattern, so it uses the count key.
        assertEquals("resourceButton.resource.resourceDice", result.i18nKey)
        assertEquals("count", result.interpolationVar)
        // The count slot is an ICU plural: `{count, plural, =1 {1 Resource Dice} other {# Resource Dice}}`.
        // Passing the raw "2rd" put a non-numeric into it — the same mangled label this function
        // exists to prevent, just for the integer form rather than the dice form.
        assertEquals("2", result.interpolationValue)
    }

    @Test
    fun `resolveResourceButtonLabelKey - nothing routed to the count slot is ever non-numeric`() {
        // The count key pluralizes on {count}; anything that is not a plain integer must have been
        // routed to the expression key instead.
        val values = listOf("3", "2rd", "1d4", "1d4rd", "1d4+1", "0", "12rd")
        for (resource in listOf(Resource.RESOURCE_DICE, Resource.RESOURCE_POINTS, Resource.UNREST)) {
            for (value in values) {
                val result = resolveResourceButtonLabelKey(value, resource)
                if (result.interpolationVar == "count") {
                    assertNotNull(
                        result.interpolationValue.toIntOrNull(),
                        "count slot got a non-numeric \"${result.interpolationValue}\" for $value/$resource",
                    )
                }
            }
        }
    }
}