package at.posselt.pfrpg2e.companion

/**
 * House rule: each companion may make only ONE Influence or Discover attempt per camping session.
 * The "session" is identified by a durable marker the camping system already advances each session
 * (the daily-preparations world-time stamp) — reused verbatim so this cap and the camping oncePer
 * session reset can never drift.
 *
 * Returns true when the attempt is allowed: either there is no active camping session to cap against
 * ([currentSessionId] is null — e.g. the interaction happens outside camping), or the companion's
 * last attempt was recorded in a *different* session than the current one. Recording an attempt is
 * simply storing [currentSessionId] as the companion's last-attempt id; the next session (a new
 * marker) re-enables the attempt automatically.
 */
fun canAttemptCompanionInteraction(
    lastAttemptSessionId: String?,
    currentSessionId: String?,
): Boolean =
    currentSessionId == null || lastAttemptSessionId != currentSessionId
