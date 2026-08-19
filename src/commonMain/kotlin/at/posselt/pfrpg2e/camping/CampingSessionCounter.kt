package at.posselt.pfrpg2e.camping

/**
 * The camping session counter: a number that advances exactly once per completed camping session.
 *
 * It exists because the obvious candidate is wrong. `dailyPrepsAtTime` looks like a session marker
 * -- it is stamped at daily preparations -- but two other paths rewrite it with no camping session
 * having happened at all: `persistPassedTime` resets it whenever a single world-time advance covers
 * a day (and `resetTimeTrackingAfterOneDay` defaults to true), and the camping sheet's "reset
 * adventuring time tracker" button rewrites it on demand. Neither clears the camping activity
 * results, so anything keyed on `dailyPrepsAtTime` drifts away from the oncePerSession activity
 * lock that IS cleared at daily preparations.
 *
 * This counter is incremented in the same place those results are cleared, so the two can't drift.
 *
 * See card t_f80e7c4b.
 */
fun nextCampingSessionId(current: Int?): Int = (current ?: 0) + 1

/**
 * Stable string identity for a session counter, for storing against a companion's last attempt.
 * Null when there is no camping data to cap against, which callers treat as "uncapped".
 */
fun campingSessionIdOf(counter: Int?): String? = counter?.toString()
