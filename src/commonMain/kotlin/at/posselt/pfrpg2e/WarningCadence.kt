package at.posselt.pfrpg2e

/**
 * How often a GM warning may repeat.
 *
 * [ONCE_PER_WORLD] suits a *configuration* fact the GM only needs telling once — a missing module.
 * [ONCE_PER_SESSION] suits a *recurring operational failure*: the world clock silently refusing to
 * advance is not a one-off notice, it happens again on every rest, and a warning that fires once
 * ever leaves the GM with a permanently broken clock and no further signal after the first message
 * scrolls away.
 */
enum class WarningCadence { ONCE_PER_WORLD, ONCE_PER_SESSION }

/**
 * Whether [key] should be posted now.
 *
 * [worldDismissed] persists across reloads; [sessionShown] lives only for this client's session, so
 * a session-scoped warning returns after a reload but never twice in one sitting.
 */
fun shouldPostWarning(
    key: String,
    cadence: WarningCadence,
    worldDismissed: Set<String>,
    sessionShown: Set<String>,
): Boolean = when (cadence) {
    WarningCadence.ONCE_PER_WORLD -> key !in worldDismissed
    WarningCadence.ONCE_PER_SESSION -> key !in sessionShown
}
