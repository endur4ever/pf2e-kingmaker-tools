package at.posselt.pfrpg2e.kingdom.resources

import at.posselt.pfrpg2e.data.kingdom.RealmData
import at.posselt.pfrpg2e.kingdom.seasonaleconomy.SeasonalEconomyModifiers
import at.posselt.pfrpg2e.kingdom.seasonaleconomy.applyWorksiteMultiplier
import at.posselt.pfrpg2e.data.kingdom.ResourceDieSize
import at.posselt.pfrpg2e.data.kingdom.findKingdomSize
import at.posselt.pfrpg2e.data.kingdom.structures.CommodityStorage

data class Income(
    val stone: Int,
    val ore: Int,
    val lumber: Int,
    val resourceDice: Int,
    val resourceDiceSize: ResourceDieSize,
    val resourcePoints: Int,
    val luxuries: Int,
) {
    val resourcePointsFormula = "$resourceDice${resourceDiceSize.value}"

    fun limitBy(storage: CommodityStorage): Income =
        copy(
            ore = storage.limitOre(ore),
            lumber = storage.limitLumber(lumber),
            stone = storage.limitStone(stone),
            luxuries = storage.limitLuxuries(luxuries),
        )
}

fun calculateIncome(
    realmData: RealmData,
    resourceDice: Int,
    increaseGainedLuxuries: Int,
    seasonal: SeasonalEconomyModifiers = SeasonalEconomyModifiers.none(),
): Income {
    val worksites = realmData.worksites
    val size = findKingdomSize(realmData.size)
    val luxuries = worksites.luxurySources.income
    // Only the EXTRACTION worksites slow in winter (§3.2): traders keep working, so luxuries and
    // resource dice are untouched. The multiplier is neutral unless the profile gate is open.
    return Income(
        stone = applyWorksiteMultiplier(worksites.quarries.income, seasonal.commodityWorksiteMultiplier),
        ore = applyWorksiteMultiplier(worksites.mines.income, seasonal.commodityWorksiteMultiplier),
        lumber = applyWorksiteMultiplier(worksites.lumberCamps.income, seasonal.commodityWorksiteMultiplier),
        luxuries = if(luxuries > 0) luxuries + increaseGainedLuxuries else luxuries,
        resourceDice = resourceDice,
        resourceDiceSize = size.resourceDieSize,
        resourcePoints = 0,
    )
}