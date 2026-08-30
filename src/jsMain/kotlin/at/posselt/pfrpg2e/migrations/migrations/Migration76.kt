package at.posselt.pfrpg2e.migrations.migrations

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.RawPetition
import com.foundryvtt.core.Game

/**
 * Petition inbox seed (petition-inbox plan section 2; the plan's placeholder "Migration72"
 * re-derives to 76).
 *
 * Every nullable array on KingdomData is seeded by convention -- quests/23,
 * companionExpeditions/40, caravans/44, warThreats/46, shipmentHistory/61 -- because the code
 * that fills them APPENDS, and an append against undefined throws. Seed only when absent, so a
 * re-run keeps every petition already in an inbox.
 */
class Migration76 : Migration(76) {
    override suspend fun migrateKingdom(game: Game, kingdom: KingdomData) {
        if (kingdom.petitions == null) {
            kingdom.petitions = emptyArray<RawPetition>()
        }
    }
}
