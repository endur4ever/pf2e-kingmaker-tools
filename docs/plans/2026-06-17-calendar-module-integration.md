# Calendar-Module Integration (Seasons & Stars / Simple Calendar)

Created: 2026-06-17
Roadmap: candidate backlog #2 in `docs/plans/`
Branch: `kingmaker.5`

## Goal

Integrate the module with the active in-world calendar system—specifically **Seasons & Stars** (which supports the `SimpleCalendar` API namespace via its compatibility bridge)—to surface campaign actions (kingdom turns, weather events, camping results, clock deadlines) as calendar notes/events, and to auto-derive weather seasons from the actual Golarion calendar date.

## Scope

1. **Auto-Derive Season from Calendar:**
   - Retrieve the current month from `SimpleCalendar.api.getCurrentDate()`.
   - Map Golarion calendar months (Abadius to Kuthona) to seasons (Spring, Summer, Fall, Winter).
   - Use the derived season for daily weather generation flat checks, temperature adjustments, and precipitation tables.
   - Fall back to the manual setting season when the calendar module is not active or present.

2. **Surface Kingdom End-Turn Summary on Calendar:**
   - On executing `performEndTurn`, post a summary note to the calendar on the current date showing kingdom level, unrest, RP shifts, consumption paid, and random events rolled.

3. **Surface Camping & Weather Results on Calendar:**
   - When a daily tick rolls weather (or a manual weather roll is performed), write a note to the calendar indicating the weather type, precipitation, and temperature.
   - When completing a camping session, log a calendar note listing the camping activities attempted/succeeded and travel milestones.

4. **Surface Campaign Clock Deadlines on Calendar:**
   - When campaign clocks are created or modified, calculate the target deadline date based on current date + clock remaining ticks (where 1 tick = 1 month for kingdom scale, or 1 tick = 1 day for daily scale).
   - Create a calendar event/note on that future target date representing the threat deadline.

5. **Advance Kingdom Turn on Calendar Month Change:**
   - Add an opt-in GM setting `enableCalendarMonthEndTurn` which automatically triggers/prompts the GM to perform a kingdom End Turn when the calendar month changes.

---

## Proposed Changes

### 1. External APIs Declarations & Detections

#### [NEW] [SimpleCalendar.kt](file:///home/grego/code/pf2e-kingmaker-tools/src/jsMain/kotlin/com/foundryvtt/core/helpers/SimpleCalendar.kt)
Define external declarations for the `SimpleCalendar.api` surface:
```kotlin
package com.foundryvtt.core.helpers

import kotlin.js.Promise

external interface SimpleCalendarDate {
    val year: Int
    val month: Int // 0-indexed (0 = Abadius)
    val day: Int // 0-indexed
    val hour: Int
    val minute: Int
    val second: Int
}

external interface SimpleCalendarNote {
    val title: String
    val content: String
    val startDate: SimpleCalendarDate
    val endDate: SimpleCalendarDate
    val allDay: Boolean
    val repeats: Int
    val categories: Array<String>
    val calendarId: String
}

external interface SimpleCalendarApi {
    fun getCurrentDate(): SimpleCalendarDate
    fun addNote(
        title: String,
        content: String,
        startDate: SimpleCalendarDate,
        endDate: SimpleCalendarDate,
        allDay: Boolean,
        repeats: Int,
        categories: Array<String>,
        calendarId: String = definedExternally
    ): Promise<Any?>
}

external class SimpleCalendar {
    companion object {
        val api: SimpleCalendarApi?
    }
}
```

---

### 2. Season Derivation

#### [MODIFY] [Time.kt](file:///home/grego/code/pf2e-kingmaker-tools/src/commonMain/kotlin/at/posselt/pfrpg2e/utils/Time.kt)
Create a function/enum mapping to resolve Golarion months to seasons:
```kotlin
enum class GolarionSeason {
    SPRING, SUMMER, FALL, WINTER
}

fun getSeasonForMonth(monthZeroIndexed: Int): GolarionSeason {
    return when (monthZeroIndexed) {
        2, 3, 4 -> GolarionSeason.SPRING      // Pharast (2), Gozran (3), Desnus (4)
        5, 6, 7 -> GolarionSeason.SUMMER      // Sarenith (5), Erastus (6), Arodus (7)
        8, 9, 10 -> GolarionSeason.FALL       // Rova (8), Lamashtan (9), Neth (10)
        else -> GolarionSeason.WINTER          // Kuthona (11), Abadius (0), Calistril (1)
    }
}
```

#### [MODIFY] [Weather.kt](file:///home/grego/code/pf2e-kingmaker-tools/src/jsMain/kotlin/at/posselt/pfrpg2e/weather/Weather.kt)
- In `rollWeather`, check if `window.SimpleCalendar.api` is available.
- If available, retrieve the current date and derive the season from the month using `getSeasonForMonth`.
- Otherwise, fall back to the existing manual season selection setting.

---

### 3. Posting Calendar Notes

#### [NEW] [CalendarLogger.kt](file:///home/grego/code/pf2e-kingmaker-tools/src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/CalendarLogger.kt)
Implement a utility to log messages to the calendar:
```kotlin
package at.posselt.pfrpg2e.kingdom

import com.foundryvtt.core.helpers.SimpleCalendar
import com.foundryvtt.core.helpers.SimpleCalendarDate
import js.objects.jso
import kotlinx.coroutines.await

suspend fun logToCalendar(title: String, content: String, date: SimpleCalendarDate? = null) {
    val api = SimpleCalendar.api ?: return
    val targetDate = date ?: api.getCurrentDate()
    try {
        api.addNote(
            title = title,
            content = content,
            startDate = targetDate,
            endDate = targetDate,
            allDay = true,
            repeats = 0,
            categories = arrayOf("Kingmaker")
        ).await()
    } catch (e: Exception) {
        console.error("Failed to add calendar note: ", e)
    }
}
```

#### [MODIFY] [TurnTickingEngine.kt](file:///home/grego/code/pf2e-kingmaker-tools/src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/TurnTickingEngine.kt)
- After finishing `performEndTurn`, post a note:
  - Title: `"Kingdom Turn ${turnRecord.turnNumber} Complete"`
  - Content: Details about Unrest changes, RP gains, consumption outcome, and events.

#### [MODIFY] [DailyTickHooks.kt](file:///home/grego/code/pf2e-kingmaker-tools/src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/DailyTickHooks.kt)
- When weather rolls or companion token steps are processed, invoke `logToCalendar` to log the weather forecast and travel progress.

---

### 4. Settings and Date Hooks

#### [MODIFY] [KingdomData.kt](file:///home/grego/code/pf2e-kingmaker-tools/src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/KingdomData.kt)
Add `enableCalendarMonthEndTurn: Boolean?` settings schema.

#### [MODIFY] [KingdomSettings.kt](file:///home/grego/code/pf2e-kingmaker-tools/src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/dialogs/KingdomSettings.kt)
Add settings checkboxes for `"enableCalendarMonthEndTurn"`.

---

## Verification Plan

### Automated Tests
- Create unit tests in `CalendarIntegrationTest.kt` verifying:
  - Golarion month-to-season mapping correctness for all 12 months.
  - Derived season fallback logic.
  - Correct clock-to-target-date calculation logic.

### Manual Verification
1. Activate the **Seasons & Stars** module.
2. Advance calendar date and verify the weather season automatically updates to match Golarion dates.
3. Perform a Kingdom End Turn and open the calendar. Verify a colored indicator and a note exist on the target day detailing the turn summary.
4. Set a campaign clock threat (e.g. 2 months remaining) and verify an event is created on the calendar 2 months in the future.
