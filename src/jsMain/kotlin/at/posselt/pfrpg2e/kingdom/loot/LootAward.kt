package at.posselt.pfrpg2e.kingdom.loot

import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.RawHexContent
import at.posselt.pfrpg2e.kingdom.data.toModel
import at.posselt.pfrpg2e.kingdom.data.toRaw
import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.setKingdom
import at.posselt.pfrpg2e.utils.buildUuid
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.Game
import com.foundryvtt.core.applications.ux.TextEditor.TextEditor
import com.foundryvtt.core.utils.fromUuid
import com.foundryvtt.core.ui
import js.objects.recordOf
import kotlinx.coroutines.await
import kotlinx.js.JsPlainObject

/**
 * Award-on-clear for hex loot manifests
 * (`docs/plans/2026-07-09-plan-loot-manifests.md` SS5): clearing a hex SURFACES an offer; items
 * move and the ledger grows only when the GM clicks. Items land as REAL items in the party stash
 * (the caravan-delivery path's addToInventory), because that is the only design that makes the
 * ledger -- and therefore the realized-loot pacing metric -- true rather than a guess (SS5.3).
 */

@Suppress("unused")
@JsPlainObject
external interface LootAwardRowContext {
    val link: String
    val detail: String
    val cursed: Boolean
}

@Suppress("unused")
@JsPlainObject
external interface LootAwardCardContext {
    val title: String
    val actorUuid: String
    val contentId: String
    val sourceLabel: String
    val summary: String
    val rows: Array<LootAwardRowContext>
    val awardLabel: String
    val dismissLabel: String
}

@Suppress("unused")
@JsPlainObject
external interface CursedHintRow {
    val name: String
    val uuid: String
    val hasUuid: Boolean
}

@Suppress("unused")
@JsPlainObject
external interface CursedHintContext {
    val title: String
    val actorUuid: String
    val cleanseLabel: String
    val rows: Array<CursedHintRow>
}

/**
 * Whether this content still has treasure to offer. Both halves matter: an emptied manifest is
 * nothing to award, and an awarded one must never re-offer (SS5.1).
 */
fun hasUnawardedManifest(content: RawHexContent): Boolean =
    (content.lootManifest?.isNotEmpty() == true) && content.manifestAwarded != true

suspend fun postLootAwardOffer(game: Game, actor: KingdomActor, content: RawHexContent) {
    if (!hasUnawardedManifest(content)) return
    val gmIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    if (gmIds.isEmpty()) return
    val items = (content.lootManifest ?: emptyArray()).mapNotNull { it.toModel() }
    val totals = manifestTotals(items)
    postChatTemplate(
        templatePath = "chatmessages/loot-award-offer.hbs",
        templateContext = LootAwardCardContext(
            title = t("chatMessages.lootAward.title"),
            actorUuid = actor.uuid,
            contentId = content.id,
            sourceLabel = content.name,
            summary = t(
                "kingdom.hexContent.loot.summary",
                recordOf(
                    "count" to items.size.toString(),
                    "gp" to totals.totalGp.toString(),
                    "cursed" to totals.cursedCount.toString(),
                ),
            ),
            rows = items.map { item ->
                LootAwardRowContext(
                    link = item.itemUuid
                        ?.takeIf { it.isNotBlank() }
                        ?.let { TextEditor.enrichHTML(buildUuid(it, item.name)).await() }
                        ?: item.name,
                    detail = t(
                        "chatMessages.lootAward.rowDetail",
                        recordOf("qty" to item.qty.toString(), "gp" to item.gpValue.toString()),
                    ),
                    cursed = item.cursed,
                )
            }.toTypedArray(),
            awardLabel = t("chatMessages.lootAward.award"),
            dismissLabel = t("chatMessages.lootAward.dismiss"),
        ),
        whisper = gmIds,
    )
}

/**
 * Moves every manifest row into the party stash, appends ONE ledger entry, and stamps the guard.
 *
 * Idempotent by the same flag the offer is gated on, so a re-posted card or a double click cannot
 * double-grant. An item whose uuid no longer resolves is REPORTED and skipped rather than
 * silently dropped -- the GM needs to know a reward went missing -- and the ledger records only
 * what actually moved, because the pacing metric reads it as fact.
 */
suspend fun awardLootManifest(game: Game, actor: KingdomActor, contentId: String): Boolean {
    val kingdom: KingdomData = actor.getKingdom() ?: return false
    val content = kingdom.hexContents?.find { it.id == contentId } ?: return false
    if (content.manifestAwarded == true) {
        ui.notifications.warn(t("chatMessages.lootAward.alreadyAwarded", recordOf("name" to content.name)))
        return false
    }
    val items = (content.lootManifest ?: emptyArray()).mapNotNull { it.toModel() }
    if (items.isEmpty()) return false

    val granted = mutableListOf<LootManifestItem>()
    val missing = mutableListOf<String>()
    val cursedGranted = mutableListOf<LootManifestItem>()
    for (item in items) {
        val uuid = item.itemUuid?.takeIf { it.isNotBlank() }
        if (uuid == null) {
            // A manually-typed row has no document to move; it still counts as treasure awarded.
            granted.add(item)
            if (item.cursed) cursedGranted.add(item)
            continue
        }
        val moved = runCatching {
            val doc = fromUuid(uuid).await() ?: return@runCatching false
            val data = doc.asDynamic().toObject()
            data.system.quantity = maxOf(item.qty, 1)
            actor.addToInventory(data.unsafeCast<AnyObject>()).await()
            true
        }.getOrDefault(false)
        if (moved) {
            granted.add(item)
            if (item.cursed) cursedGranted.add(item)
        } else {
            missing.add(item.name)
        }
    }
    if (missing.isNotEmpty()) {
        ui.notifications.warn(
            t("chatMessages.lootAward.itemsMissing", recordOf("names" to missing.joinToString(", ")))
        )
    }

    val turn = kingdom.currentTurn ?: 0
    val entry = buildLedgerEntry(
        id = "loot-${content.hexKey}-${kotlin.js.Date().getTime().toLong()}",
        turn = turn,
        sourceHexKey = content.hexKey,
        items = granted,
    )
    val existingNames = (kingdom.treasureLedger ?: emptyArray()).associate { it.id to it.sourceName }
    kingdom.treasureLedger = appendLedgerEntry(
        (kingdom.treasureLedger ?: emptyArray()).map { it.toModel() },
        entry,
    ).map { model ->
        model.toRaw(sourceName = if (model.id == entry.id) content.name else existingNames[model.id])
    }.toTypedArray()
    content.manifestAwarded = true
    content.manifestAwardedTurn = turn
    actor.setKingdom(kingdom)

    val totals = manifestTotals(granted)
    ui.notifications.info(
        t(
            "chatMessages.lootAward.awarded",
            recordOf("count" to granted.size.toString(), "gp" to totals.totalGp.toString()),
        )
    )
    // Cursed items still move -- players own the problem -- and each gets a pointer to the ritual
    // that removes the curse (SS5.4), as a BUTTON: the dialog takes the item directly, so the GM
    // never re-finds an item the module just handed them.
    if (cursedGranted.isNotEmpty()) {
        postChatTemplate(
            templatePath = "chatmessages/loot-cursed-hint.hbs",
            templateContext = CursedHintContext(
                title = t("chatMessages.lootAward.cursedTitle"),
                actorUuid = actor.uuid,
                cleanseLabel = t("chatMessages.lootAward.cleanse"),
                rows = cursedGranted.map { item ->
                    CursedHintRow(
                        name = item.name,
                        uuid = item.itemUuid.orEmpty(),
                        hasUuid = !item.itemUuid.isNullOrBlank(),
                    )
                }.toTypedArray(),
            ),
            whisper = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray(),
        )
    }
    return true
}

/** Records the GM's intent to handle loot manually: stops re-offers, grants nothing, no ledger. */
suspend fun dismissLootManifest(actor: KingdomActor, contentId: String): Boolean {
    val kingdom = actor.getKingdom() ?: return false
    val content = kingdom.hexContents?.find { it.id == contentId } ?: return false
    if (content.manifestAwarded == true) return false
    content.manifestAwarded = true
    content.manifestAwardedTurn = kingdom.currentTurn ?: 0
    actor.setKingdom(kingdom)
    return true
}
