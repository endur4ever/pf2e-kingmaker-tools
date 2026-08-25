package at.posselt.pfrpg2e.kingdom.pings

import at.posselt.pfrpg2e.kingdom.sheet.contexts.buildPingsPanelContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The builder receives the ALREADY-filtered unread feed (from [unreadFeed]); its only jobs are
 * faithful mapping and the open/hasUnread flags. Filtering, ordering, and the playerSafe leak
 * guard are pinned in commonTest ([at.posselt.pfrpg2e.kingdom.pings]'s PingsFeedTest) -- these
 * tests pin that the builder does not re-order, drop, or invent rows on top of that.
 */
class PingsPanelContextTest {
    private fun item(id: String, millis: Double, target: String = "turn") = FeedItem(
        id = id,
        occurredAtMillis = millis,
        labelKey = PINGS_KEY_TURN_GAZETTE,
        labelArgs = mapOf("turn" to "4"),
        target = target,
    )

    @Test
    fun mapsEveryRowInOrderWithIdAndTarget() {
        val panel = buildPingsPanelContext(
            listOf(item("b", 200.0, target = "campaign"), item("a", 100.0, target = "roster")),
            open = true,
        )
        assertEquals(2, panel.unreadCount)
        assertEquals(listOf("b", "a"), panel.items.map { it.id })
        assertEquals(listOf("campaign", "roster"), panel.items.map { it.target })
        assertTrue(panel.open)
        assertTrue(panel.hasUnread)
    }

    @Test
    fun labelComesFromTheItemsKey() {
        val panel = buildPingsPanelContext(listOf(item("a", 100.0)), open = false)
        // In the test harness t() falls back to the raw key; live it resolves the template.
        assertTrue(panel.items[0].label.contains("kingdom.pings"))
    }

    @Test
    fun emptyFeedYieldsClosedBadge() {
        val panel = buildPingsPanelContext(emptyList(), open = false)
        assertEquals(0, panel.unreadCount)
        assertEquals(0, panel.items.size)
        assertFalse(panel.hasUnread)
        assertFalse(panel.open)
    }

    @Test
    fun openFlagIsIndependentOfContent() {
        assertTrue(buildPingsPanelContext(emptyList(), open = true).open)
        assertFalse(buildPingsPanelContext(listOf(item("a", 1.0)), open = false).open)
    }
}
