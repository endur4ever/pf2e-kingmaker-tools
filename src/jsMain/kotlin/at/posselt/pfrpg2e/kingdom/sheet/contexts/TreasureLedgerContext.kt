package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.toModel
import at.posselt.pfrpg2e.kingdom.loot.TREASURE_LEDGER_CAP
import at.posselt.pfrpg2e.utils.t
import js.objects.recordOf
import kotlinx.js.JsPlainObject

/**
 * Read-only Treasure Ledger rows (`docs/plans/2026-07-09-plan-loot-manifests.md` SS4.2).
 *
 * GM-ONLY BY DATA -- null for players -- because the ledger names hexes whose treasure the party
 * may not have found yet. Newest first: the GM's question is almost always "what did they just
 * get?", and the cap trims the oldest, so the interesting end is the one that stays.
 */
@Suppress("unused")
@JsPlainObject
external interface TreasureLedgerRowContext {
    val id: String
    val turnLabel: String
    val source: String
    val items: String
    val gpLabel: String
    val cursedCount: Int
    val hasCursed: Boolean
}

@Suppress("unused")
@JsPlainObject
external interface TreasureLedgerContext {
    val rows: Array<TreasureLedgerRowContext>
    val totalLabel: String
    val hasRows: Boolean
    val atCap: Boolean
}

fun buildTreasureLedgerContext(isGM: Boolean, kingdom: KingdomData): TreasureLedgerContext? {
    if (!isGM) return null
    val entries = (kingdom.treasureLedger ?: emptyArray()).map { it.toModel() }
    val raws = (kingdom.treasureLedger ?: emptyArray()).associateBy { it.id }
    return TreasureLedgerContext(
        rows = entries.reversed().map { entry ->
            TreasureLedgerRowContext(
                id = entry.id,
                turnLabel = t("kingdom.treasureLedger.turn", recordOf("turn" to entry.turn.toString())),
                source = raws[entry.id]?.sourceName?.takeIf { it.isNotBlank() } ?: entry.sourceHexKey,
                items = entry.itemNames.joinToString(", "),
                gpLabel = t("kingdom.treasureLedger.gp", recordOf("gp" to entry.totalGp.toString())),
                cursedCount = entry.cursedCount,
                hasCursed = entry.cursedCount > 0,
            )
        }.toTypedArray(),
        totalLabel = t(
            "kingdom.treasureLedger.total",
            recordOf("gp" to entries.sumOf { it.totalGp }.toString()),
        ),
        hasRows = entries.isNotEmpty(),
        atCap = entries.size >= TREASURE_LEDGER_CAP,
    )
}
