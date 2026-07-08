package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.PacingAlertSeverity
import at.posselt.pfrpg2e.kingdom.data.PacingAlertType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PacingAlertsTest {
    private fun defaultSettings(): KingdomSettings = js("({})").unsafeCast<KingdomSettings>()

    @Test
    fun levelMismatchWithinRangeProducesNoAlert() {
        assertNull(evaluateLevelMismatch(kingdomLevel = 5, targetLevel = 6, range = 2, turn = 1))
    }

    @Test
    fun levelMismatchWarningVsCritical() {
        val warn = evaluateLevelMismatch(kingdomLevel = 9, targetLevel = 5, range = 2, turn = 3)
        assertNotNull(warn)
        assertEquals(PacingAlertType.LEVEL_MISMATCH.value, warn.type)
        assertEquals(PacingAlertSeverity.WARNING.value, warn.severity)   // diff 4 == range*2

        val crit = evaluateLevelMismatch(kingdomLevel = 12, targetLevel = 5, range = 2, turn = 3)
        assertNotNull(crit)
        assertEquals(PacingAlertSeverity.CRITICAL.value, crit.severity)  // diff 7 > range*2
    }

    @Test
    fun chapterTargetLevelDefaultsToTrackingThePartyAndIgnoresZero() {
        // null / 0 mean "track the party's average level" — only a positive value pins
        // the advisories to a fixed campaign chapter level.
        assertNull(defaultSettings().pacingChapterTargetLevel())
        val zeroed = defaultSettings().apply { pacingAlertChapterTargetLevel = 0 }
        assertNull(zeroed.pacingChapterTargetLevel())
        val pinned = defaultSettings().apply { pacingAlertChapterTargetLevel = 7 }
        assertEquals(7, pinned.pacingChapterTargetLevel())
    }

    @Test
    fun lootImbalanceRangeFallsBackToLevelMismatchRange() {
        // No dedicated threshold set: reuse the level-mismatch tolerance (old behavior).
        val fallback = defaultSettings().apply { pacingAlertLevelMismatchRange = 3 }
        assertEquals(3, fallback.pacingLootImbalanceRange())
        // Dedicated threshold wins when present.
        val dedicated = defaultSettings().apply {
            pacingAlertLevelMismatchRange = 3
            pacingAlertLootImbalanceRange = 5
        }
        assertEquals(5, dedicated.pacingLootImbalanceRange())
        // Neither set: both fall back to the built-in default of 2.
        assertEquals(2, defaultSettings().pacingLootImbalanceRange())
    }

    @Test
    fun stagnationThresholds() {
        assertNull(evaluateStagnation(turnsSinceUnrestChange = 5, maxTurnGap = 10, turn = 1))
        assertEquals(PacingAlertSeverity.WARNING.value, evaluateStagnation(10, 10, 1)!!.severity)
        assertEquals(PacingAlertSeverity.CRITICAL.value, evaluateStagnation(20, 10, 1)!!.severity)
    }

    @Test
    fun turnGapThreshold() {
        assertNull(evaluateTurnGap(turnsSinceLastEvent = 9, maxTurnGap = 10, turn = 1))
        assertNotNull(evaluateTurnGap(10, 10, 1))
    }

    @Test
    fun aggregateProducesAllTriggeredAlerts() {
        val metrics = PacingMetrics(
            kingdomLevel = 12, targetLevel = 5,
            turnsSinceUnrestChange = 12, turnsSinceLastEvent = 12, turn = 4,
        )
        // defaults: range 2, gap 10 -> level mismatch + stagnation + turn gap
        assertEquals(3, evaluatePacingAlerts(metrics, defaultSettings()).size)
    }

    @Test
    fun healthyCampaignProducesNoAlerts() {
        val metrics = PacingMetrics(
            kingdomLevel = 5, targetLevel = 5,
            turnsSinceUnrestChange = 2, turnsSinceLastEvent = 2, turn = 1,
        )
        assertEquals(0, evaluatePacingAlerts(metrics, defaultSettings()).size)
    }

    @Test
    fun stagnationTrackerResetsWhenUnrestMoves() {
        val track = trackUnrestStagnation(
            previousUnrest = 4, currentUnrest = 6, previousCount = 9, maxTurnGap = 10, minDelta = 1, turn = 1,
        )
        assertEquals(0, track.turnsSinceUnrestChange)
        assertNull(track.alert)
    }

    @Test
    fun stagnationTrackerStartsFreshWhenNoPreviousUnrest() {
        val track = trackUnrestStagnation(
            previousUnrest = null, currentUnrest = 5, previousCount = null, maxTurnGap = 10, minDelta = 1, turn = 1,
        )
        assertEquals(0, track.turnsSinceUnrestChange)
        assertNull(track.alert)
    }

    @Test
    fun stagnationTrackerIncrementsWhileUnrestUnchanged() {
        val track = trackUnrestStagnation(
            previousUnrest = 5, currentUnrest = 5, previousCount = 3, maxTurnGap = 10, minDelta = 1, turn = 1,
        )
        assertEquals(4, track.turnsSinceUnrestChange)
        assertNull(track.alert)   // below threshold, no alert
    }

    @Test
    fun stagnationTrackerFiresOnceAtWarningThreshold() {
        // count crosses from 9 -> 10 (== maxTurnGap) -> warning fires
        val crossing = trackUnrestStagnation(
            previousUnrest = 5, currentUnrest = 5, previousCount = 9, maxTurnGap = 10, minDelta = 1, turn = 1,
        )
        assertEquals(10, crossing.turnsSinceUnrestChange)
        assertNotNull(crossing.alert)
        assertEquals(PacingAlertSeverity.WARNING.value, crossing.alert.severity)

        // next turn 10 -> 11: still stagnant but NO repeat alert (fire once)
        val afterCrossing = trackUnrestStagnation(
            previousUnrest = 5, currentUnrest = 5, previousCount = 10, maxTurnGap = 10, minDelta = 1, turn = 1,
        )
        assertEquals(11, afterCrossing.turnsSinceUnrestChange)
        assertNull(afterCrossing.alert)
    }

    @Test
    fun stagnationTrackerEscalatesToCriticalAtDoubleThreshold() {
        val track = trackUnrestStagnation(
            previousUnrest = 5, currentUnrest = 5, previousCount = 19, maxTurnGap = 10, minDelta = 1, turn = 1,
        )
        assertEquals(20, track.turnsSinceUnrestChange)
        assertNotNull(track.alert)
        assertEquals(PacingAlertSeverity.CRITICAL.value, track.alert.severity)
    }

    @Test
    fun stagnationTrackerResetsWhenUnrestChangesByAtLeastMinDelta() {
        // minDelta = 3, unrest changes by 5 (>= 3) -> not stagnant
        val track = trackUnrestStagnation(
            previousUnrest = 4, currentUnrest = 9, previousCount = 7, maxTurnGap = 10, minDelta = 3, turn = 1,
        )
        assertEquals(0, track.turnsSinceUnrestChange)
        assertNull(track.alert)
    }

    @Test
    fun stagnationTrackerStagnatesWhenUnrestChangesByLessThanMinDelta() {
        // minDelta = 5, unrest changes by 3 (< 5) -> stagnant
        val track = trackUnrestStagnation(
            previousUnrest = 4, currentUnrest = 7, previousCount = 2, maxTurnGap = 10, minDelta = 5, turn = 1,
        )
        assertEquals(3, track.turnsSinceUnrestChange)
        assertNull(track.alert)
    }

    @Test
    fun stagnationTrackerMinDeltaOneRequiresExactEquality() {
        // minDelta = 1: delta < 1 means delta == 0, so only exact equality is stagnant
        val stagnant = trackUnrestStagnation(
            previousUnrest = 5, currentUnrest = 5, previousCount = 0, maxTurnGap = 3, minDelta = 1, turn = 1,
        )
        assertEquals(1, stagnant.turnsSinceUnrestChange)

        val moved = trackUnrestStagnation(
            previousUnrest = 5, currentUnrest = 6, previousCount = 0, maxTurnGap = 3, minDelta = 1, turn = 1,
        )
        assertEquals(0, moved.turnsSinceUnrestChange)
    }

    @Test
    fun levelMismatchTrackerFiresWhenMismatchAppears() {
        val track = trackLevelMismatch(
            kingdomLevel = 9, partyLevel = 5, range = 2, previousSeverity = null, turn = 9,
        )
        assertNotNull(track.alert)
        assertEquals(PacingAlertSeverity.WARNING.value, track.alert.severity)
        assertEquals(PacingAlertSeverity.WARNING.value, track.severity)
    }

    @Test
    fun levelMismatchTrackerFiresOnEscalation() {
        val track = trackLevelMismatch(
            kingdomLevel = 12, partyLevel = 5, range = 2,
            previousSeverity = PacingAlertSeverity.WARNING.value, turn = 12,
        )
        assertNotNull(track.alert)
        assertEquals(PacingAlertSeverity.CRITICAL.value, track.alert.severity)
    }

    @Test
    fun levelMismatchTrackerSilentWhenSeverityUnchanged() {
        val track = trackLevelMismatch(
            kingdomLevel = 9, partyLevel = 5, range = 2,
            previousSeverity = PacingAlertSeverity.WARNING.value, turn = 9,
        )
        assertNull(track.alert)                                   // no re-warn
        assertEquals(PacingAlertSeverity.WARNING.value, track.severity)  // state preserved
    }

    @Test
    fun levelMismatchTrackerClearsWhenBackInRange() {
        val track = trackLevelMismatch(
            kingdomLevel = 6, partyLevel = 5, range = 2,
            previousSeverity = PacingAlertSeverity.WARNING.value, turn = 6,
        )
        assertNull(track.alert)
        assertNull(track.severity)   // cleared so a later recurrence fires again
    }

    @Test
    fun lootImbalanceOnlyFlagsAccessAboveLevel() {
        // item access at or below party level + range: fine
        assertNull(evaluateLootImbalance(itemAccessLevel = 6, partyLevel = 5, range = 2, turn = 1))
        // access far below level is NOT flagged (one-directional)
        assertNull(evaluateLootImbalance(itemAccessLevel = 1, partyLevel = 10, range = 2, turn = 1))
        // access well above level: warning, then critical past 2x
        assertEquals(
            PacingAlertSeverity.WARNING.value,
            evaluateLootImbalance(itemAccessLevel = 9, partyLevel = 5, range = 2, turn = 1)!!.severity,
        )
        assertEquals(
            PacingAlertSeverity.CRITICAL.value,
            evaluateLootImbalance(itemAccessLevel = 12, partyLevel = 5, range = 2, turn = 1)!!.severity,
        )
    }

    @Test
    fun lootImbalanceTrackerFiresOncePerSeverityChange() {
        val appear = trackLootImbalance(
            itemAccessLevel = 9, partyLevel = 5, range = 2, previousSeverity = null, turn = 1,
        )
        assertNotNull(appear.alert)
        assertEquals(PacingAlertType.LOOT_IMBALANCE.value, appear.alert.type)

        val unchanged = trackLootImbalance(
            itemAccessLevel = 9, partyLevel = 5, range = 2,
            previousSeverity = PacingAlertSeverity.WARNING.value, turn = 1,
        )
        assertNull(unchanged.alert)
        assertEquals(PacingAlertSeverity.WARNING.value, unchanged.severity)

        val cleared = trackLootImbalance(
            itemAccessLevel = 6, partyLevel = 5, range = 2,
            previousSeverity = PacingAlertSeverity.WARNING.value, turn = 1,
        )
        assertNull(cleared.alert)
        assertNull(cleared.severity)
    }

    @Test
    fun lootImbalancePropagatesRelatedEntityId() {
        val settlementId = "settlement-123"
        val alert = evaluateLootImbalance(
            itemAccessLevel = 9, partyLevel = 5, range = 2, turn = 1,
            relatedEntityId = settlementId,
        )
        assertNotNull(alert)
        assertEquals(settlementId, alert.relatedEntityId)

        // Track function should also propagate it
        val track = trackLootImbalance(
            itemAccessLevel = 9, partyLevel = 5, range = 2,
            previousSeverity = null, turn = 1,
            relatedEntityId = settlementId,
        )
        assertNotNull(track.alert)
        assertEquals(settlementId, track.alert.relatedEntityId)
    }

    @Test
    fun turnGapTrackerFiresOnlyAtExactCrossings() {
        // below threshold: silent
        assertNull(trackTurnGap(turnsSinceLastEvent = 9, maxTurnGap = 10, turn = 1))
        // exactly at the gap: fire once
        val first = trackTurnGap(turnsSinceLastEvent = 10, maxTurnGap = 10, turn = 1)
        assertNotNull(first)
        assertEquals(PacingAlertType.TURN_GAP.value, first.type)
        // one past the gap: silent (no re-warn every check)
        assertNull(trackTurnGap(turnsSinceLastEvent = 11, maxTurnGap = 10, turn = 1))
        // double the gap: reminder fires again
        assertNotNull(trackTurnGap(turnsSinceLastEvent = 20, maxTurnGap = 10, turn = 1))
        assertNull(trackTurnGap(turnsSinceLastEvent = 21, maxTurnGap = 10, turn = 1))
    }
}
