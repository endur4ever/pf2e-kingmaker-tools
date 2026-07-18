package at.posselt.pfrpg2e

/**
 * Support level for writing dated calendar notes (expedition launch/return, weather, end-turn
 * summaries, camp-rest notes). Our note writer talks to the `SimpleCalendar` global, which is
 * provided either by the Simple Calendar module directly or by the *Simple Calendar Compatibility
 * Bridge* when the calendar is Seasons & Stars. Seasons & Stars on its own does **not** expose that
 * global, so notes silently no-op — the state this enum lets us detect and warn about.
 */
enum class CalendarNoteSupport {
    /** `SimpleCalendar` global is present (Simple Calendar itself, or the S&S compat bridge). */
    SUPPORTED,

    /** Seasons & Stars is active but the compat bridge is missing — notes silently no-op. */
    MISSING_BRIDGE,

    /** No calendar integration at all — notes are simply unavailable, nothing to warn about. */
    UNAVAILABLE,
}

/**
 * Classify the calendar-note integration from two feature-detected facts:
 * whether Seasons & Stars is active, and whether the `SimpleCalendar` global resolved.
 *
 * A present `SimpleCalendar` global always means notes work (Simple Calendar or the bridge), so it
 * wins regardless of S&S. Only when the global is absent *and* S&S is driving the calendar do we
 * have the silent-no-op misconfiguration worth flagging.
 */
fun evaluateCalendarNoteSupport(
    seasonsStarsActive: Boolean,
    simpleCalendarPresent: Boolean,
): CalendarNoteSupport = when {
    simpleCalendarPresent -> CalendarNoteSupport.SUPPORTED
    seasonsStarsActive -> CalendarNoteSupport.MISSING_BRIDGE
    else -> CalendarNoteSupport.UNAVAILABLE
}

/**
 * Whether to show the GM the one-time "install the compat bridge" warning. True only for the
 * [CalendarNoteSupport.MISSING_BRIDGE] state and only if the warning has not already been shown,
 * so the notice appears exactly once per world.
 */
fun shouldWarnAboutMissingCalendarBridge(
    support: CalendarNoteSupport,
    alreadyWarned: Boolean,
): Boolean = support == CalendarNoteSupport.MISSING_BRIDGE && !alreadyWarned
