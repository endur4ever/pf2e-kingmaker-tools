package at.posselt.pfrpg2e.camping

/**
 * The windows of a night in which a random-encounter check may land.
 *
 * Pure and enum-free so the arithmetic can be tested: the jsMain caller maps its rest-roll setting
 * to an [intervalSeconds] and draws one moment inside each returned window.
 *
 * Two defects live in this arithmetic when it is written inline, and both shipped:
 *
 * 1. A window narrower than three seconds has no interior moment to draw. `Random.nextInt(from,
 *    until)` THROWS when `from >= until`, and a night can legitimately be short or zero -- watches
 *    skipped, nothing to prepare -- so the roll threw out of the rest with no visible error and no
 *    rest happened. Such windows are omitted here rather than returned.
 * 2. Dividing the night by the interval with integer division drops the final partial window. On
 *    any night that is not an exact multiple of the interval the tail went unchecked, and since
 *    the last watch slot covers that tail, it was almost never the slot that caught anything.
 *    The count is rounded UP and the last window is clamped to the end of the night.
 */
fun encounterCheckWindows(watchDurationSeconds: Int, intervalSeconds: Int?): List<IntRange> {
    if (watchDurationSeconds <= 2) return emptyList()
    if (intervalSeconds == null || intervalSeconds <= 0) {
        return listOf(1..(watchDurationSeconds - 2))
    }
    val windows = (watchDurationSeconds + intervalSeconds - 1) / intervalSeconds
    return (0 until windows).mapNotNull { index ->
        val begin = index * intervalSeconds
        val end = (begin + intervalSeconds).coerceAtMost(watchDurationSeconds)
        if (end - begin > 2) (begin + 1)..(end - 2) else null
    }
}
