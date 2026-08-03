package at.posselt.pfrpg2e

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalendarIntegrationTest {
    @Test
    fun simpleCalendarPresentIsAlwaysSupported() {
        assertEquals(
            CalendarNoteSupport.SUPPORTED,
            evaluateCalendarNoteSupport(seasonsStarsActive = false, simpleCalendarPresent = true),
        )
        // Bridge present alongside S&S still counts as supported.
        assertEquals(
            CalendarNoteSupport.SUPPORTED,
            evaluateCalendarNoteSupport(seasonsStarsActive = true, simpleCalendarPresent = true),
        )
    }

    @Test
    fun seasonsStarsWithoutBridgeIsMissingBridge() {
        assertEquals(
            CalendarNoteSupport.MISSING_BRIDGE,
            evaluateCalendarNoteSupport(seasonsStarsActive = true, simpleCalendarPresent = false),
        )
    }

    @Test
    fun noCalendarAtAllIsUnavailable() {
        assertEquals(
            CalendarNoteSupport.UNAVAILABLE,
            evaluateCalendarNoteSupport(seasonsStarsActive = false, simpleCalendarPresent = false),
        )
    }

    @Test
    fun warnsForMissingBridgeOnlyOnce() {
        assertTrue(
            shouldWarnAboutMissingCalendarBridge(CalendarNoteSupport.MISSING_BRIDGE, alreadyWarned = false),
        )
        assertFalse(
            shouldWarnAboutMissingCalendarBridge(CalendarNoteSupport.MISSING_BRIDGE, alreadyWarned = true),
        )
    }

    @Test
    fun neverWarnsWhenSupportedOrUnavailable() {
        // Bridge present -> no warning, regardless of prior state.
        assertFalse(shouldWarnAboutMissingCalendarBridge(CalendarNoteSupport.SUPPORTED, alreadyWarned = false))
        // No calendar module -> nothing to install, so no warning.
        assertFalse(shouldWarnAboutMissingCalendarBridge(CalendarNoteSupport.UNAVAILABLE, alreadyWarned = false))
    }
}
