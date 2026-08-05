package at.posselt.pfrpg2e.kingdom.sheet

import at.posselt.pfrpg2e.kingdom.sheet.Resource
import kotlin.text.Regex

/**
 * Determines which i18n key variant and interpolation value to use for a resource button label.
 *
 * The trailing "rd" in authored values like `@gain2rdResourceDice` is internal syntax and is
 * stripped from whatever gets interpolated — it is never shown to a player.
 *
 * - Plain integers (e.g., "3", or "2rd" -> "2") use the `resource` key with `count` = the integer,
 *   which the ICU string pluralizes.
 * - Dice expressions (e.g., "1d4rd" -> "1d4", or "1d4+3") use the `resourceExpression` key with
 *   `expression` = the expression, rendered verbatim with no pluralization.
 * - Events use the `resource` key with `eventName` (handled separately by caller).
 *
 * Returns a [ResourceButtonLabelKey] containing the full i18n key and interpolation details.
 */
fun resolveResourceButtonLabelKey(value: String, resource: Resource): ResourceButtonLabelKey {
    val isEvent = resource == Resource.EVENT

    // The "rd" suffix marks a value denominated in Resource Dice and is NOT specific to the
    // resourceDice resource — `@gain2rdResourcePoints` is valid authoring syntax, and
    // ResourceButton.evaluateValueExpression likewise strips it for every resource. Conditioning
    // the strip on RESOURCE_DICE left "2rd" reaching the ICU plural count slot for every other
    // resource, which is the mangled label this function exists to prevent.
    val valueForDiceCheck = value.removeSuffix("rd")

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

    // Always the rd-stripped value. "rd" is internal authoring syntax (@gain2rdResourceDice), never
    // something to show a player, and the count slot is an ICU plural — feeding it "2rd" put a
    // non-numeric into `{count, plural, =1 {…} other {# …}}`, which is the same mangled-label bug
    // this function exists to fix, just for the integer form instead of the dice form.
    // valueForDiceCheck is identical to value for every non-resource-dice resource.
    val interpolationValue = valueForDiceCheck

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