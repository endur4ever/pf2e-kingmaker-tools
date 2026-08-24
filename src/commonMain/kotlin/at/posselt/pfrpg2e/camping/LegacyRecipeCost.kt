package at.posselt.pfrpg2e.camping

/** A homebrew recipe cost recovered from the free-text string that predated the structured field. */
data class LegacyCost(val currency: String, val value: Int)

/**
 * A currency abbreviation standing on its own. Matching by plain substring would read "copper" as
 * "pp" -- platinum -- and multiply the price by a hundred.
 */
private val looseCurrency = Regex("\\b(cp|sp|gp|pp)\\b", RegexOption.IGNORE_CASE)

/** Whitespace between number and currency is optional, and the currency's case is ignored. */
private val costRegex = Regex("^(\\d+)\\s*(cp|sp|gp|pp)$", RegexOption.IGNORE_CASE)

/** Any leading run of digits, for text the strict form cannot match ("25 gp per meal"). */
private val leadingNumber = Regex("^(\\d+)")

/**
 * Parse a pre-migration recipe cost.
 *
 * The original migration required whitespace between the number and the currency
 * (`^(\d+)\s+(cp|sp|gp|pp)$`) and was case-sensitive, so "5gp", "5 GP" and "25 gp per meal" all
 * matched nothing and fell back to a value of 0 -- wiping the price off the recipe rather than
 * failing loudly. Losing the number is the real damage, so this salvages one wherever text contains
 * one and only reports 0 when there is genuinely no number to find.
 *
 * Defaulting an unrecognised currency to gold is the assumption the original fallback already made.
 */
fun parseLegacyRecipeCost(raw: String): LegacyCost {
    val trimmed = raw.trim()
    // Collapse internal whitespace so "12   sp" reaches the strict form.
    val collapsed = trimmed.replace(Regex("\\s+"), " ")
    costRegex.find(collapsed.replace(" ", ""))?.let { match ->
        return LegacyCost(
            currency = match.groupValues[2].lowercase(),
            value = match.groupValues[1].toInt(),
        )
    }
    val value = leadingNumber.find(collapsed)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val currency = looseCurrency.find(collapsed)?.groupValues?.get(1)?.lowercase() ?: "gp"
    return LegacyCost(currency = currency, value = value)
}
