package at.posselt.pfrpg2e.migrations.migrations

import com.foundryvtt.core.Game

/**
 * Migration 44 — remove dead settlementTransfer caravan kind.
 *
 * The 'settlementTransfer' kind was documented in RawCaravan but never implemented in the
 * dispatch UI (only sellToPartner/buyFromPartner exist) and delivery merged into the single
 * global commodity pool. Per-settlement stockpiles were evaluated and REJECTED in the
 * 2026-07-09 gap analysis (fights the single-pool design).
 *
 * This migration defensively maps any stray persisted 'settlementTransfer' caravans to
 * 'sellToPartner' so they don't crash on load or tick.
 */
class Migration44 : Migration(44) {
    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        val caravans = kingdom.caravans
        if (caravans != null) {
            for (i in 0 until (caravans.length as Int)) {
                val caravan = caravans[i]
                if (caravan.kind == "settlementTransfer") {
                    // Map to sellToPartner as the closest valid kind
                    caravan.kind = "sellToPartner"
                }
            }
        }
    }
}