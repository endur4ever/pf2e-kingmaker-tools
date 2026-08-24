package at.posselt.pfrpg2e.kingdom.pings

import kotlin.test.Test
import kotlin.test.assertEquals

/** Covers the §7.1 feed and readiness cases of the player-pings plan. */
class PingsFeedTest {
    private fun item(
        id: String = "i1",
        occurredAtMillis: Double = 1_000.0,
        playerSafe: Boolean = true,
    ) = FeedItem(
        id = id, occurredAtMillis = occurredAtMillis, labelKey = "kingdom.playerPings.feed.item",
        target = "sheet-tab:turn", playerSafe = playerSafe,
    )

    @Test
    fun aGmOnlyItemNeverAppearsEvenWhenUnseenAndUndismissed() {
        // The leak case: an unsafe row newer than the cursor and not dismissed -- every other
        // filter would keep it, so only the playerSafe guard stands between it and a player.
        val unsafe = item(id = "secret", occurredAtMillis = 5_000.0, playerSafe = false)
        val safe = item(id = "public", occurredAtMillis = 4_000.0)
        val out = unreadFeed(listOf(unsafe, safe), SeenCursor(lastSeenAtMillis = 1_000.0))
        assertEquals(listOf("public"), out.map { it.id })
    }

    @Test
    fun aDismissedItemIsExcludedEvenThoughItIsNewerThanTheCursor() {
        val out = unreadFeed(
            listOf(item(id = "snoozed", occurredAtMillis = 5_000.0), item(id = "kept", occurredAtMillis = 4_000.0)),
            SeenCursor(lastSeenAtMillis = 1_000.0, dismissedIds = setOf("snoozed")),
        )
        assertEquals(listOf("kept"), out.map { it.id })
    }

    @Test
    fun anItemStampedExactlyAtTheCursorIsSeenAndOneMillisecondAfterIsUnread() {
        // Turn-granularity sources stamp a whole turn's items with one shared instant; marking
        // the sheet seen at that instant must clear the whole turn, not leave its tail unread.
        val atCursor = item(id = "at", occurredAtMillis = 2_000.0)
        val after = item(id = "after", occurredAtMillis = 2_001.0)
        val out = unreadFeed(listOf(atCursor, after), SeenCursor(lastSeenAtMillis = 2_000.0))
        assertEquals(listOf("after"), out.map { it.id })
    }

    @Test
    fun aNullCursorReturnsTheFullPlayerSafeBacklog() {
        // A first-time reader has no flag yet: they get everything safe, not an empty feed --
        // and the leak guard still applies before the null-cursor keep-everything rule.
        val out = unreadFeed(
            listOf(
                item(id = "a", occurredAtMillis = 1.0),
                item(id = "b", occurredAtMillis = 2.0),
                item(id = "gm", occurredAtMillis = 3.0, playerSafe = false),
            ),
            SeenCursor(),
        )
        assertEquals(listOf("b", "a"), out.map { it.id })
    }

    @Test
    fun theFeedIsNewestFirstWithInputOrderTieStability() {
        // tieA and tieB share one turn's timestamp; input order is source-report order and must
        // survive the sort, so the exact list is asserted, not just the ids present.
        val old = item(id = "old", occurredAtMillis = 1_000.0)
        val tieA = item(id = "tieA", occurredAtMillis = 3_000.0)
        val newest = item(id = "newest", occurredAtMillis = 4_000.0)
        val tieB = item(id = "tieB", occurredAtMillis = 3_000.0)
        val out = unreadFeed(listOf(old, tieA, newest, tieB), SeenCursor())
        assertEquals(listOf(newest, tieA, tieB, old), out)
    }

    @Test
    fun unreadCountEqualsTheFeedSizeOnAMixedFixture() {
        // Delegation is the contract: the badge and the panel must never disagree, so the count
        // is checked against the derived feed itself on a fixture exercising every filter.
        val items = listOf(
            item(id = "unsafe", occurredAtMillis = 9_000.0, playerSafe = false),
            item(id = "dismissed", occurredAtMillis = 8_000.0),
            item(id = "seen", occurredAtMillis = 2_000.0),
            item(id = "fresh1", occurredAtMillis = 7_000.0),
            item(id = "fresh2", occurredAtMillis = 6_000.0),
        )
        val cursor = SeenCursor(lastSeenAtMillis = 2_000.0, dismissedIds = setOf("dismissed"))
        assertEquals(unreadFeed(items, cursor).size, unreadCount(items, cursor))
        assertEquals(2, unreadCount(items, cursor))
    }

    @Test
    fun aFutureTurnsReadinessDoesNotCountEither() {
        // Both sides of the turn boundary: a record for turn 13 says nothing about turn 12. Only
        // the stale side was pinned before, so a `turn >= currentTurn` mutant survived the suite.
        val strip = readinessStrip(
            listOf(TurnReadiness("u1", turn = 13, ready = true)),
            currentTurn = 12,
            userIds = listOf("u1"),
        )
        assertEquals(false, strip["u1"])
    }

    @Test
    fun readinessIsTrueOnlyForTheCurrentTurn() {
        // A player ready for turn 11 said nothing about turn 12: the same record flips to false
        // the moment the turn advances, with no cleanup step in between.
        val records = listOf(TurnReadiness(userId = "u1", turn = 11, ready = true))
        assertEquals(mapOf("u1" to true), readinessStrip(records, currentTurn = 11, userIds = listOf("u1")))
        assertEquals(mapOf("u1" to false), readinessStrip(records, currentTurn = 12, userIds = listOf("u1")))
    }

    @Test
    fun anAbsentUserIsFalseButStillPresentInTheMap() {
        // The strip renders "waiting on ..." from the map; a user with no record must show as
        // waiting, not vanish from the roster.
        val out = readinessStrip(emptyList(), currentTurn = 5, userIds = listOf("ghost"))
        assertEquals(mapOf("ghost" to false), out)
    }

    @Test
    fun aLaterUnReadyOverridesAnEarlierReadyAndViceVersa() {
        // List order is write order: the last thing the player said about this turn stands.
        val readyThenNot = listOf(
            TurnReadiness(userId = "u1", turn = 7, ready = true),
            TurnReadiness(userId = "u1", turn = 7, ready = false),
        )
        assertEquals(mapOf("u1" to false), readinessStrip(readyThenNot, currentTurn = 7, userIds = listOf("u1")))
        val reReady = readyThenNot + TurnReadiness(userId = "u1", turn = 7, ready = true)
        assertEquals(mapOf("u1" to true), readinessStrip(reReady, currentTurn = 7, userIds = listOf("u1")))
    }

    @Test
    fun everyRequestedUserIdHasAnEntry() {
        val records = listOf(
            TurnReadiness(userId = "a", turn = 3, ready = true),
            TurnReadiness(userId = "b", turn = 2, ready = true),
        )
        val out = readinessStrip(records, currentTurn = 3, userIds = listOf("a", "b", "c"))
        assertEquals(setOf("a", "b", "c"), out.keys)
        assertEquals(mapOf("a" to true, "b" to false, "c" to false), out)
    }
}
