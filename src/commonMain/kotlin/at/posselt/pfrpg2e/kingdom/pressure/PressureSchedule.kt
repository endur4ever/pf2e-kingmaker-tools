package at.posselt.pfrpg2e.kingdom.pressure

/**
 * Pure core of the chapter deadline scheduler (`docs/plans/2026-07-09-plan-scheduled-pressure-engine.md`).
 *
 * The unit is the **world day number** — `worldTimeSeconds.floorDiv(DAY_SECONDS)` — never a parsed
 * date. Foundry world time is an Int of seconds and `DailyTickHooks` already reduces it to day
 * boundaries, so working in days keeps this free of clocks and makes a multi-day jump correct by
 * construction: advancing a week must yield every weekly firing inside it, not one.
 */

enum class Recurrence(val value: String) {
    NONE("none"), DAILY("daily"), WEEKLY("weekly"), MONTHLY("monthly");

    companion object {
        fun fromValue(value: String?): Recurrence? = entries.find { it.value == value }
    }
}

enum class PayloadKind(val value: String) {
    SPAWN_EVENT("spawnEvent"), SPAWN_ENCOUNTER("spawnEncounter"),
    ADVANCE_CLOCK("advanceClock"), POST_BEAT("postBeat");

    companion object {
        fun fromValue(value: String?): PayloadKind? = entries.find { it.value == value }
    }
}

/** A month is 30 days here: the in-world calendar's months are not uniform, and needing real
 *  month lengths would drag the calendar module into a pure function for a rule the house use
 *  cases never ask for. A GM wanting a true month-end uses a one-shot. */
const val DAYS_PER_MONTH = 30
const val DAYS_PER_WEEK = 7

data class ScheduledPressure(
    val id: String,
    val name: String,
    val startDay: Int,
    val recurrence: Recurrence,
    val endDay: Int? = null,
    val lastFiredDay: Int? = null,
    val payloadKind: PayloadKind,
    val escalationCount: Int = 0,
    val active: Boolean = true,
)

/** One firing of one schedule on one day. [escalation] counts this firing, 1-based. */
data class PressureFiring(
    val schedule: ScheduledPressure,
    val day: Int,
    val escalation: Int,
)

private fun Recurrence.strideDays(): Int? = when (this) {
    Recurrence.NONE -> null
    Recurrence.DAILY -> 1
    Recurrence.WEEKLY -> DAYS_PER_WEEK
    Recurrence.MONTHLY -> DAYS_PER_MONTH
}

/**
 * Every firing in `(fromDay, toDay]`, chronological.
 *
 * Half-open at the start so re-ticking the same span cannot double-fire: a schedule due exactly on
 * [fromDay] already fired when that day was crossed.
 *
 * Skipped entirely: inactive schedules, ones [isResolved] reports as concluded, days past [endDay],
 * and days at or before `lastFiredDay`. A schedule with no stride fires once, on [startDay].
 *
 * Pure: no clock, no randomness. Advancing seven days in one step yields exactly what seven
 * single-day steps would.
 */
fun dueFirings(
    schedules: List<ScheduledPressure>,
    fromDay: Int,
    toDay: Int,
    isResolved: (ScheduledPressure) -> Boolean = { false },
): List<PressureFiring> {
    if (toDay <= fromDay) return emptyList()
    val out = mutableListOf<PressureFiring>()
    for (schedule in schedules) {
        if (!schedule.active || isResolved(schedule)) continue
        val floor = maxOf(fromDay, schedule.lastFiredDay ?: Int.MIN_VALUE)
        val ceiling = minOf(toDay, schedule.endDay ?: Int.MAX_VALUE)
        val stride = schedule.recurrence.strideDays()
        var escalation = schedule.escalationCount
        if (stride == null) {
            if (schedule.startDay in (floor + 1)..ceiling) {
                out.add(PressureFiring(schedule, schedule.startDay, ++escalation))
            }
            continue
        }
        // First occurrence strictly after `floor`, walking the stride from startDay.
        var day = schedule.startDay
        if (day <= floor) {
            val steps = (floor - day) / stride + 1
            day += steps * stride
        }
        while (day <= ceiling) {
            out.add(PressureFiring(schedule, day, ++escalation))
            day += stride
        }
    }
    return out.sortedBy { it.day }
}
