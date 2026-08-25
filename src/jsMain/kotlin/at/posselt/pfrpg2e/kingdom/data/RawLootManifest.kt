package at.posselt.pfrpg2e.kingdom.data

import at.posselt.pfrpg2e.kingdom.loot.LootManifestItem
import at.posselt.pfrpg2e.kingdom.loot.TreasureLedgerEntry
import kotlinx.js.JsPlainObject

/**
 * Persisted loot-manifest and treasure-ledger shapes
 * (`docs/plans/2026-07-09-plan-loot-manifests.md` SS2.1, SS2.3). Every field nullable where the
 * pure model has a default, so pre-existing worlds load unchanged.
 */
@JsPlainObject
external interface RawLootManifestEntry {
    /** Foundry UUID of the source item. Preferred reference. */
    var itemUuid: String?
    /** Fallback compendium ref when no resolvable UUID exists. */
    var packRef: String?
    /** Display name captured at prep time, so the row renders even if the UUID later breaks. */
    var name: String?
    /** Copies this hex yields. Null => 1. */
    var quantity: Int?
    /** GP per unit; Double keeps sp/cp fidelity. Null => 0. */
    var gpValue: Double?
    var cursed: Boolean?
    var note: String?
}

/**
 * One settled award. Deliberately DENORMALIZED (names and totals are snapshots, never
 * references): the ledger must outlive the hex it came from -- a GM can delete the hex content
 * after clearing it, but the wealth record persists for pacing and audit (SS2.5). Leaner than the
 * plan's sketch by design: per-item snapshot rows exist to serve per-item awards, which SS9 Q3
 * defers -- a nullable items array can join later without a migration.
 */
@JsPlainObject
external interface RawTreasureLedgerEntry {
    var id: String
    var turn: Int
    var sourceHexKey: String
    /** Hex content name at award time, for the ledger table. */
    var sourceName: String?
    var itemNames: Array<String>
    var totalGp: Double
    var cursedCount: Int
}

/** Null-tolerant decode: a row with no usable identity (no name AND no refs) is dropped. */
fun RawLootManifestEntry.toModel(): LootManifestItem? {
    val displayName = name?.takeIf { it.isNotBlank() }
        ?: itemUuid?.takeIf { it.isNotBlank() }
        ?: packRef?.takeIf { it.isNotBlank() }
        ?: return null
    return LootManifestItem(
        itemUuid = itemUuid,
        packRef = packRef,
        name = displayName,
        qty = quantity ?: 1,
        gpValue = gpValue ?: 0.0,
        cursed = cursed == true,
        note = note,
    )
}

fun TreasureLedgerEntry.toRaw(sourceName: String?): RawTreasureLedgerEntry {
    val obj = js("{}").unsafeCast<RawTreasureLedgerEntry>()
    obj.id = id
    obj.turn = turn
    obj.sourceHexKey = sourceHexKey
    obj.sourceName = sourceName
    obj.itemNames = itemNames.toTypedArray()
    obj.totalGp = totalGp
    obj.cursedCount = cursedCount
    return obj
}

fun RawTreasureLedgerEntry.toModel(): TreasureLedgerEntry =
    TreasureLedgerEntry(
        id = id,
        turn = turn,
        sourceHexKey = sourceHexKey,
        itemNames = itemNames.toList(),
        totalGp = totalGp,
        cursedCount = cursedCount,
    )
