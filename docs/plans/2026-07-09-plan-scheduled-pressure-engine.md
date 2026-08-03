# Scheduled Pressure Engine Implementation Plan

> **Status:** In progress — Phase 1 planning.
> **Date:** 2026-07-09
> **Roadmap item:** New feature: Kingdom Deadline Scheduler (Scheduled Pressure)

> **Implementation progress (2026-07-09):**
> - ⬜ **Phase 1** — Data model (`RawScheduledPressure`) and pure recurrence engine logic.
> - ⬜ **Phase 2** — Integration with `DailyTickHooks` and basic event triggers (chat message).
Dt
> - ⬜ **Phase 3** — GM Authoring UI: Dialog for creating/editing pressures + "Deadlines" section on Kingdom Sheet.
> - ⬜ **Phase 4** — Advanced Payloads: Integration with existing systems (e.g., spawning encounter tokens, advancing other kingdom clocks).

---

## Executive Summary

The **Scheduled Pressure Engine** provides a robust way for GMs to schedule time-sensitive kingdom events that do not follow the standard monthly turn cycle. Instead of manual tracking in GM notes, this engine allows the definition of "pressures"—events that are mathematically bound to the Foundry world clock (daily or monthly boundaries).

**Problem Statement:**
Currently, while some elements like companion travel and expeditions use a daily tick mechanism, there is no generic way to schedule arbitrary future events. A GM cannot easily say "An army arrives in 14 days" or "A plague starts on the 1st of next month" such that the system automatically alerts them or triggers an event when the time comes.

**Key capabilities:**
- **Triggerable Pressures (`RawScheduledPressure`):** A data structure defining a trigger (date/recurance rule), a payload type, and an escalation counter.
- **Recurrence Engine:** Pure Kotlin logic that evaluates if a scheduled event should fire based on world time/day boundary crossings.
- **GM-Confirmed Offers:** When a deadline is reached, the engine produces a `TickResult` containing an **offer** (e.g., "Spawn encounter" or "Post narrative beat") which appears in chat for the GM to confirm via the UI.
- **Payload Vocabulary:** A closed set of actionable payloads: `spawn-event`, `spawn-encounter`, `advance-clock`, `post-narrative-beat`.
- **Visual Tracking:** A new "Deadlines" section on the Kingdom Sheet showing upcoming pressures, countdowns, and past resolutions.

---

## Affected Files

### New Kotlin Files

| File | Purpose |
|------|----------|
| `src/jsMain/kotlin/.../kingdom/data/RawScheduledPressure.kt` | `@JsPlainObject` definition for the pressure event (recurrence rule, payload, escalation). |
| `src/jsMain/kotlin/.../kingdom/scheduler/PressureEngine.kt` | **Pure** logic: evaluates recurrence rules and calculates "next trigger" dates. |
| `src/jsMain/kotlin/.../kingdom/sheet/contexts/DeadlineContext.kt` | UI context for rendering the upcoming deadlines list on the sheet. |
| `src/jsMain/kotlin/.../kingdom/dialogs/ScheduleEventDialog.DT` | GM dialog to create/edit pressures (date, payload type, description). |

### New Handlebars Templates

| File | Purpose |
|------|----------|
| `src/jsMain/resources/applications/kingdom/sections/deadlines/page.hbs` | The Deadlines section on the Kingdom Sheet. |
| `src/jsMain/resources/chatmessages/scheduled-pressure-trigger.hbs` | The chat card shown when a deadline is reached (the "Offer" to GM). |

### Modified Kotlin Files

| File | Changes |
|------|---------|
| `src/jsMain/kotlin/.../kingdom/DailyTickHooks.kt` | Hook into the world clock update to call the engine's tick function for any active pressures. |
| `src/jsMain/kotlin/.../kingdom/sheet/KingdomSheet.kt` | Register the Deadlines section and navigation entry; build `DeadlineContext`. |
| `lang/en.json` | Add all i18n keys for deadlines, payloads, and dialog text (nested objects). |

---

## Data Models

### `RawScheduledPressure` (`@JsPlainObject`)
The core data structure stored on the Kingdom object:
```kotlin
@JsPlainObject
external interface RawScheduledPressure {
    var triggerDate: String      // ISO-8601 or Foundry date string (e.g., "2026-07-15")
    var recurrenceRule: String?  // Optional: "daily", "weekly", "monthly"
    var payloadType: PayloadType // Enum: SPAWN_EVENT, SPAWN_ENCOUNTER, ADVANCE_CLOCK, POST_BEAT
    var payloadData: String      // JSON/String data for the payload (e.g., event ID or text)
    var escalationCounter: Int   // How many times it has "hit" before final resolution?
    var isResolved: Boolean      // Flag to clear from active tracking
}

enum class PayloadType { SPAWN_EVENT, SPAWN_EN_COUNTER, ADVANCE_CLOCK, POST_BEAT }
```

---

## Migration Plan

**Non-breaking.** The new `scheduledPressures` field on the Kingdom object will be nullable. 
1. A migration script/class will initialize the array as `emptyArray()` for existing kingdoms.
2. Existing systems (travel, expeditions) remain unaffected as they use their own specific logic in `DailyTickHooks`.

---

## Testing Strategy

**jsTest — pure logic (`PressureEngineTest.kt`)**
- Recurrence math: checking if "daily" rule triggers on day boundary crossings.
- Date parsing/comparison against Foundry world time.
- Payload evaluation (ensuring the correct payload type is returned).

**jsTest — integration (~5 tests)**
- `DailyTickHooks` triggering a mock pressure event when time advances.
- Verification that an "offer" chat card is generated in the `TickResult`.

---

## Manual Verification Checklist

1. **Setup:** Add a new scheduled pressure via the (new) dialog for "3 days from now".
2. **Tracking:** Verify it appears in the Kingdom Sheet "Deadlines" section with a countdown.
3. **Triggering:** Advance the Foundry world clock by 4 days.
4. **Observation:** A chat message/offer appears in the GM chat, asking to execute the payload.
5. **Resolution:** Confirm the offer; verify the pressure is marked as resolved and removed from active tracking.
