package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.kingdom.xp.XpLedgerEntry
import at.posselt.pfrpg2e.kingdom.xp.XpOfferStatus
import at.posselt.pfrpg2e.kingdom.xp.XpSourceKind
import at.posselt.pfrpg2e.kingdom.xp.confirmedTotal
import at.posselt.pfrpg2e.kingdom.xp.reconcile
import at.posselt.pfrpg2e.kingdom.xp.totalsByKind
import at.posselt.pfrpg2e.utils.t
import js.objects.recordOf
import kotlinx.js.JsPlainObject

@Suppress("unused")
@JsPlainObject
external interface XpLedgerRowContext {
    val id: String
    val turnLabel: String
    val sourceLabel: String
    val sourceRef: String
    val amount: Int
    val statusLabel: String
    val isOffered: Boolean
    val note: String?
}

@Suppress("unused")
@JsPlainObject
external interface XpLedgerKindTotalContext {
    val label: String
    val total: Int
}

@Suppress("unused")
@JsPlainObject
external interface XpLedgerContext {
    val rows: Array<XpLedgerRowContext>
    val hasRows: Boolean
    val confirmedTotal: Int
    val byKind: Array<XpLedgerKindTotalContext>
    /** What a PC actually holds, and the gap. Drift is reported, never corrected. */
    val actualLifetimeXp: Int
    val drift: Int
    val hasDrift: Boolean
    val isGM: Boolean
}

/** Literal keys: a composed "xpLedger.source.$value" is invisible to the i18n scan. */
fun localizeXpSource(kind: XpSourceKind): String = when (kind) {
    XpSourceKind.HEX_RECONNOITERED -> t("kingdom.xpLedger.source.hexReconnoitered")
    XpSourceKind.SITE_CLEARED -> t("kingdom.xpLedger.source.siteCleared")
    XpSourceKind.QUEST_COMPLETED -> t("kingdom.xpLedger.source.questCompleted")
    XpSourceKind.EXPEDITION_RESOLVED -> t("kingdom.xpLedger.source.expeditionResolved")
    XpSourceKind.RP_ENCOUNTER -> t("kingdom.xpLedger.source.rpEncounter")
    XpSourceKind.MANUAL -> t("kingdom.xpLedger.source.manual")
}

private fun localizeStatus(status: XpOfferStatus): String = when (status) {
    XpOfferStatus.OFFERED -> t("kingdom.xpLedger.status.offered")
    XpOfferStatus.CONFIRMED -> t("kingdom.xpLedger.status.confirmed")
    XpOfferStatus.DISMISSED -> t("kingdom.xpLedger.status.dismissed")
}

/**
 * The ledger as the Party tab renders it, newest first.
 *
 * A row shows what was GRANTED once answered and what was proposed while it waits, so a confirmed
 * row never advertises a number the party did not receive.
 */
fun buildXpLedgerContext(
    entries: List<XpLedgerEntry>,
    actualLifetimeXp: Int,
    isGM: Boolean,
): XpLedgerContext {
    val reconciliation = reconcile(entries, actualLifetimeXp)
    return XpLedgerContext(
        rows = entries.sortedWith(compareByDescending<XpLedgerEntry> { it.turn }.thenByDescending { it.timestamp })
            .map { entry ->
                XpLedgerRowContext(
                    id = entry.id,
                    turnLabel = t("kingdom.xpLedger.turnLabel", recordOf("turn" to entry.turn)),
                    sourceLabel = localizeXpSource(entry.sourceKind),
                    sourceRef = entry.sourceRef,
                    amount = entry.grantedAmount ?: entry.proposedAmount,
                    statusLabel = localizeStatus(entry.status),
                    isOffered = entry.status == XpOfferStatus.OFFERED,
                    note = entry.note,
                )
            }.toTypedArray(),
        hasRows = entries.isNotEmpty(),
        confirmedTotal = confirmedTotal(entries),
        byKind = totalsByKind(entries)
            .entries
            .sortedByDescending { it.value }
            .map { XpLedgerKindTotalContext(label = localizeXpSource(it.key), total = it.value) }
            .toTypedArray(),
        actualLifetimeXp = reconciliation.actualLifetimeXp,
        drift = reconciliation.drift,
        // drift is EXPECTED -- combat XP and hand edits never pass through the ledger -- so this
        // reports a number and never offers to correct it
        hasDrift = reconciliation.drift != 0,
        isGM = isGM,
    )
}
