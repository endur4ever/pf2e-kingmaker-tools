package at.posselt.pfrpg2e.kingdom.sheet

import at.posselt.pfrpg2e.kingdom.sheet.Resource
import kotlin.text.Regex

/**
 * Determines which i18n key variant and interpolation value to use for a resource button label.
 *
 * - Plain integers (e.g., "3") use the `resource` key with `count` = integer (pluralized).
 * - Dice expressions ending in "rd" (e.g., "1d4rd") use the `resourceExpression` key with
 *   `expression` = the full expression including "rd" (verbatim, no pluralization).
 * - Other dice expressions (e.g., "1d4+3") use the `resourceExpression` key with
 *   `expression` = the full expression (verbatim, no pluralization).
 * - Events use the `resource` key with `eventName` (handled separately by caller).
 *
 * Returns a [ResourceButtonLabelKey] containing the full i18n key and interpolation details.
 */
fun resolveResourceButtonLabelKey(value: String, resource: Resource): ResourceButtonLabelKey {
    val isEvent = resource == Resource.EVENT

    // For Resource Dice, the "rd" suffix indicates a rolled resource die (e.g., "1d4rd" -> roll 1d4).
    // Strip "rd" before checking for dice expression pattern.
    val valueForDiceCheck = if (resource == Resource.RESOURCE_DICE) {
        value.removeSuffix("rd")
    } else {
        value
    }

    val isDiceExpression = diceExpressionRegex.matches(valueForDiceCheck)

    val (keySuffix, interpolationKey) = when {
        isEvent -> "resource" to "count" // Caller handles eventName separately
        isDiceExpression -> "resourceExpression" to "expression"
        else -> "resource" to "count"
    }

    val i18nKey = if (isEvent) {
        "resourceButton.resource.${Resource.EVENT.value}"
    } else if (keySuffix == "resourceExpression") {
        resource.i18nKeyExpression
    } else {
        resource.i18nKey
    }

    val interpolationValue = when {
        isDiceExpression -> valueForDiceCheck
        else -> value
    }

    return ResourceButtonLabelKey(
        i18nKey = i18nKey,
        interpolationVar = interpolationKey,
        interpolationValue = interpolationValue
    )
}

private val diceExpressionRegex = Regex("^\\d+d\\d+([+-]\\d+)?$")

data class ResourceButtonLabelKey(
    val i18nKey: String,
    val interpolationVar: String,
    val interpolationValue: String
)