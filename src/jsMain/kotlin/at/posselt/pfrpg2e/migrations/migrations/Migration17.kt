package at.posselt.pfrpg2e.migrations.migrations

import at.posselt.pfrpg2e.camping.CampingData
import at.posselt.pfrpg2e.camping.RawCost
import at.posselt.pfrpg2e.camping.RecipeData
import at.posselt.pfrpg2e.camping.parseLegacyRecipeCost
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.RawModifier
import at.posselt.pfrpg2e.kingdom.data.RawGroup
import com.foundryvtt.core.Game

class Migration17 : Migration(17) {
    override suspend fun migrateKingdom(game: Game, kingdom: KingdomData) {
        kingdom.modifiers = kingdom.modifiers
            .map { RawModifier.copy(it, requiresTranslation = false) }
            .toTypedArray()
        kingdom.groups = kingdom.groups.map {
            RawGroup.copy(it, preventPledgeOfFealty = false)
        }.toTypedArray()
    }

    override suspend fun migrateCamping(game: Game, camping: CampingData) {
        camping.cooking.homebrewMeals = camping.cooking.homebrewMeals
            .map {
                // Shared, tested parse. The regex that used to live here demanded whitespace
                // between number and currency, so "5gp" silently migrated to 0.
                val parsed = parseLegacyRecipeCost(it.cost.unsafeCast<String>())
                RecipeData.copy(
                    it,
                    cost = RawCost(
                        currency = parsed.currency,
                        value = parsed.value,
                    )
                )
            }
            .toTypedArray()
    }
}