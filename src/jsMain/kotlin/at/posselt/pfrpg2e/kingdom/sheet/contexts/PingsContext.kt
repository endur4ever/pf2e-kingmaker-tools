package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.kingdom.pings.FeedItem
import at.posselt.pfrpg2e.utils.t
import js.objects.recordOf
import kotlinx.js.JsPlainObject

/**
 * Render context for the bell badge + unread panel
 * (`docs/plans/2026-07-09-plan-player-pings.md`, phase 3).
 *
 * Unlike the forecast this is for EVERY user — it is the players' feature — and it only ever
 * receives the already-filtered unread feed, whose playerSafe stage ran first in the pure
 * pipeline. The badge count and the panel rows come from the same list, so they cannot disagree.
 */
@Suppress("unused")
@JsPlainObject
external interface PingsItemContext {
    val id: String
    val label: String
    /** MainNavEntry value for the row's change-nav jump. */
    val target: String
}

@Suppress("unused")
@JsPlainObject
external interface PingsPanelContext {
    val unreadCount: Int
    val items: Array<PingsItemContext>
    val open: Boolean
    val hasUnread: Boolean
}

fun buildPingsPanelContext(unread: List<FeedItem>, open: Boolean): PingsPanelContext =
    PingsPanelContext(
        unreadCount = unread.size,
        items = unread.map { item ->
            PingsItemContext(
                id = item.id,
                label = t(item.labelKey, recordOf(*item.labelArgs.map { (k, v) -> k to v }.toTypedArray())),
                target = item.target,
            )
        }.toTypedArray(),
        open = open,
        hasUnread = unread.isNotEmpty(),
    )
