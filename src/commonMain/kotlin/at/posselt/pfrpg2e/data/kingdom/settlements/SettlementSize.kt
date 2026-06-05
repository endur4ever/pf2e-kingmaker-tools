package at.posselt.pfrpg2e.data.kingdom.settlements

data class SettlementSize(
    val type: SettlementSizeType,
    val maximumBlocks: String,
    val requiredKingdomLevel: Int,
    val population: String,
    val consumption: Int,
    val maxItemBonus: Int,
    val influence: Int,
    val levelFrom: Int,
    val levelTo: Int? = null,
) {
    /**
     * A representative numeric population derived from the [population] string.
     *
     * Parses ranges like "401-2000" (returns the midpoint) and open-ended
     * strings like "<401" or "25001+" (returns the boundary value).
     * This is used to determine how many named NPCs to seed.
     */
    val populationNumber: Int
        get() {
            val s = population.replace(",", "").trim()
            // range "401-2000" or "2001-25000"
            val rangeMatch = Regex("""(\d+)\s*[-–]\s*(\d+)""").find(s)
            if (rangeMatch != null) {
                val (lo, hi) = rangeMatch.destructured
                return (lo.toInt() + hi.toInt()) / 2
            }
            // "<401" → use the upper bound
            val lessMatch = Regex("""<\s*(\d+)""").find(s)
            if (lessMatch != null) {
                return lessMatch.groupValues[1].toInt()
            }
            // "25001+" → use the lower bound
            val plusMatch = Regex("""(\d+)\+""").find(s)
            if (plusMatch != null) {
                return plusMatch.groupValues[1].toInt()
            }
            // fallback: grab the first number we see
            val anyNum = Regex("""(\d+)""").find(s)
            if (anyNum != null) {
                return anyNum.groupValues[1].toInt()
            }
            return 200 // sensible default
        }
}

val settlementSizeData = listOf(
    SettlementSize(
        type = SettlementSizeType.VILLAGE,
        consumption = 1,
        influence = 0,
        maximumBlocks = "1",
        requiredKingdomLevel = 1,
        levelFrom = 1,
        levelTo = 1,
        maxItemBonus = 1,
        population = "<401",
    ), SettlementSize(
        type = SettlementSizeType.TOWN,
        consumption = 2,
        influence = 1,
        requiredKingdomLevel = 3,
        maximumBlocks = "4",
        levelFrom = 2,
        levelTo = 4,
        maxItemBonus = 1,
        population = "401-2000",
    ), SettlementSize(
        type = SettlementSizeType.CITY,
        consumption = 4,
        influence = 2,
        requiredKingdomLevel = 9,
        maximumBlocks = "9",
        levelFrom = 5,
        levelTo = 9,
        maxItemBonus = 2,
        population = "2001–25000",
    ), SettlementSize(
        type = SettlementSizeType.METROPOLIS,
        consumption = 6,
        influence = 3,
        maximumBlocks = "10+",
        requiredKingdomLevel = 15,
        levelFrom = 10,
        maxItemBonus = 3,
        population = "25001+",
    )
)

fun findSettlementSize(level: Int) =
    settlementSizeData.find {
        it.levelFrom <= level
                && (it.levelTo?.let { to -> to >= level } != false)
    }
        ?: settlementSizeData.first()

fun findSettlementMaxItemBonusLevel(kingdomLevel: Int) =
    if (kingdomLevel <= 4) {
        1
    } else if (kingdomLevel <= 9) {
        2
    } else {
        3
    }