# Plan: chapter deadline scheduler — date-driven recurring pressure events with escalation

Card: `t_56e7bae2`.

## 1. Problem statement

The house rules run chapter pressure on the **calendar**, not on kingdom turns
(`docs/house-rules.md` lines 43–45):

> * 3 months to deal with the Stag Lord, otherwise Brevoy might claim the Rostland Hinterlands …
> * Use weekly **Troll Sightings** events until Hargulka is dealt with …
> * Similarly, roll one cult event per day … during the **Season of Bloom**

Campaign clocks tick once per kingdom turn, so none of that fits them: a weekly event and a
three-month deadline both land between turns. Today the GM tracks all three by hand, and the failure
mode is silent — nobody notices the deadline that quietly passed four sessions ago.

These three are the concrete use cases, and §6 ships them as authoring presets. A plan for this
feature that does not produce them has not done the job.

## 2. Work in day numbers, not dates

Foundry world time is an **`Int` of seconds** (`GameTime.worldTimeSeconds`, `utils/Time.kt:67`), and
`DailyTickHooks.kt:88` already reduces it to day boundaries:

```kotlin
private fun daysCrossed(worldTime: Int, deltaInSeconds: Int): Int {
	if (deltaInSeconds <= 0) return 0
	val previous = worldTime - deltaInSeconds
	return worldTime.floorDiv(DAY_SECONDS) - previous.floorDiv(DAY_SECONDS)
}
```

So the engine's unit is the **world day number** — `worldTimeSeconds.floorDiv(DAY_SECONDS)` — and its
question is "which schedules fall in `(fromDay, toDay]`". An earlier draft stored
`triggerDate: String // ISO-8601 or Foundry date string`, which is three problems in one line: it is
not a decision, Foundry has no date string to store, and parsing dates inside the engine is exactly
the impurity the card forbids ("no `Date.now` in engines — world time in, due-list out; testable").

Note the `deltaInSeconds <= 0` guard: a GM winding the clock backwards to correct a mistake already
yields zero days, so nothing fires. The scheduler inherits that for free and must not undo it.

Day numbers also make the multi-day jump correct for free. The clock does not advance a day at a
time; a GM who advances a week must get **every** weekly Troll Sighting in that week, not one.

Calendar dates appear only at the edges: the authoring dialog converts a GM-picked date to a day
number once, and `logToCalendar` (`CalendarLogger.kt:60`) writes the note.

## 3. Data model

```kotlin
// jsMain: kingdom/data/RawScheduledPressure.kt
@JsPlainObject
external interface RawScheduledPressure {
    /** Stable id -- the offer card's button needs to name one schedule. */
    var id: String
    /** GM-facing label: "Troll Sightings", "Stag Lord deadline". */
    var name: String
    var description: String?

    /** World day number of the first firing. */
    var startDay: Int
    /** none | daily | weekly | monthly -- a STRING, see below. */
    var recurrence: String
    /** Stop recurring after this day, inclusive. Null = until resolved or disabled. */
    var endDay: Int?
    /** Last day this schedule fired, so a re-tick over the same span cannot double-fire. */
    var lastFiredDay: Int?

    /** spawnEvent | spawnEncounter | advanceClock | postBeat */
    var payloadKind: String
    /** Typed payload slots. Exactly one is meaningful per payloadKind. */
    var payloadEventId: String?
    var payloadEncounterId: String?
    var payloadClockId: String?
    var payloadBeatText: String?

    /** Increments on each firing; escalating payloads read it. */
    var escalationCount: Int
    /** none | questCompleted | threatResolved */
    var resolveConditionKind: String?
    var resolveConditionRef: String?

    var active: Boolean
}
```

**Strings, not enums; typed slots, not a JSON blob.** These are `@JsPlainObject` interfaces
serialized into Foundry flags. An earlier draft declared `payloadType: PayloadType` (a Kotlin enum,
which does not survive serialization — `RawWarThreat.status` is a `String` for this reason) plus
`payloadData: String // JSON/String data`, a stringly-typed blob that no guard can validate and no
migration can safely rewrite. It also had **no `id` and no `name`**, so a schedule could neither be
named in the UI nor targeted by an offer button.

`lastFiredDay` is what makes firing idempotent. Enums live in `commonMain` and convert at the
boundary with `fromCamelCase`; an unrecognised `recurrence` or `payloadKind` **skips that schedule
rather than throwing**, so one bad row cannot take down the day's whole tick.

**Storage:** `var scheduledPressures: Array<RawScheduledPressure>?` on `KingdomData`, beside
`campaignClocks` (line 303), `quests` (210) and `warThreats` (319).

**Migration:** `Migration63` seeds `emptyArray()`. 62 is claimed by the downtime-projects plan
(`2026-07-09-plan-downtime-projects.md`), so this takes the next number; both must be registered
in `Migrations.kt` and included in `MigrationChainTest`'s hardcoded range.

## 4. Engine

`src/commonMain/kotlin/at/posselt/pfrpg2e/kingdom/pressure/PressureSchedule.kt` — pure, `commonMain`
so it is testable without Foundry. (An earlier draft put the "pure" engine in `jsMain`.)

```kotlin
enum class Recurrence(val value: String) { NONE("none"), DAILY("daily"), WEEKLY("weekly"), MONTHLY("monthly") }
enum class PayloadKind(val value: String) { SPAWN_EVENT("spawnEvent"), SPAWN_ENCOUNTER("spawnEncounter"),
    ADVANCE_CLOCK("advanceClock"), POST_BEAT("postBeat") }

data class ScheduledPressure(/* model mirror */)
data class PressureFiring(val schedule: ScheduledPressure, val day: Int, val escalation: Int)

/**
 * Every firing in (fromDay, toDay]. A week-long jump yields every weekly occurrence inside it, in
 * chronological order, so advancing time in one step is indistinguishable from seven daily steps.
 *
 * Inactive schedules, those resolved by [isResolved], those past endDay, and those whose day was
 * already covered by lastFiredDay are all skipped. Pure: no clock, no randomness.
 *
 * MONTHLY is 30 days. The in-world calendar's months are not uniform, and a scheduler that needed
 * real month lengths would need the calendar module -- an impurity for a rule the house use cases
 * never ask for. A GM wanting a true month-end uses a one-shot.
 */
fun dueFirings(
    schedules: List<ScheduledPressure>,
    fromDay: Int,
    toDay: Int,
    isResolved: (ScheduledPressure) -> Boolean,
): List<PressureFiring>
```

**Tick surface:** `DailyTickHooks`, beside `tickCompanionExpeditions`. Not `TurnTickingEngine` — the
entire point is that these fire between turns — and no third tick. Campaign clocks are unchanged and
compose rather than compete: `advanceClock` is one payload kind, so a schedule can drive a per-turn
clock from the calendar without replacing it.

## 5. Resolution links: polled, not hooked

The card asks which hooks exist to observe "quest completed" or "threat defeated". **None do** — there
is no completion hook for either. So resolution is evaluated at tick time against stored state,
which is also the more robust choice: a quest completed while the module was disabled still resolves
on the next tick.

| `resolveConditionKind` | Resolved when |
| --- | --- |
| `questCompleted` | `kingdom.quests` has `id == ref` and `status == "completed"` (`RawQuest.status` is `"active" \| "completed"`) |
| `threatResolved` | `kingdom.warThreats` has `id == ref` whose `status` is terminal, **or** `peaceSettled == true` |

A resolved schedule **suspends** — `active` stays as it is and firing stops — rather than deleting,
so the Deadlines section can still show "Troll Sightings — ended when Hargulka fell on day 212".

A `resolveConditionRef` that matches nothing is reported to the GM once rather than silently
suspending forever: the same posture `RawWarThreat.enemyFactionName` documents for a renamed faction.

## 6. Authoring presets — the three house rules

The dialog (`ScheduleEventDialog.kt`; an earlier draft named the file `ScheduleEventDialog.DT`) opens
with three presets that fill every field, because these are the cases the feature exists for:

| Preset | Fields |
| --- | --- |
| **Troll Sightings** | `recurrence = weekly`, `payloadKind = spawnEvent`, `resolveConditionKind = threatResolved` linked to the Hargulka threat, escalation on |
| **Season of Bloom cult events** | `recurrence = daily`, `payloadKind = spawnEvent`, `endDay` = the season's last day |
| **Stag Lord deadline** | `recurrence = none`, `startDay` = today + 90, `payloadKind = postBeat` with the Brevoy consequence, `resolveConditionKind = questCompleted` |

**Escalation** increments per firing and is passed to the payload as `escalationCount`; for
`spawnEvent` it is offered to the GM as a suggested DC/severity step, never applied automatically.

## 7. Firing UX

Multiple schedules can fall on one day, and a week-long jump can produce many firings at once. So the
tick posts **one GM-whispered digest**, `chatmessages/pressure-digest.hbs`, grouped by day, one row
per firing with its payload and a confirm button, plus **Confirm all** / **Dismiss all**.

Buttons: `km-offer-pressure-fire`, `km-offer-pressure-dismiss`, `km-offer-pressure-fire-all`,
`km-offer-pressure-dismiss-all`. Confirming applies the payload and stamps `lastFiredDay`;
dismissing stamps `lastFiredDay` **too**, so a declined firing does not re-post next tick.

Every handler begins `if (!game.user.isGM) return` — players are OWNERs of the party actor, so a
template conditional is layout, not authorization.

Confirmed firings also write a calendar note via `logToCalendar(title, content, date)`
(`CalendarLogger.kt:60`), which is what the card means by scheduled entries rendering as calendar
notes. Notes are written on confirm only, so a dismissed firing leaves no trace in the calendar.

## 8. UI

A **Deadlines** section on the existing `MainNavEntry.CAMPAIGN` tab, beside campaign clocks — the
pressure siblings these compose with — rather than a new top-level tab.

| Piece | Path |
| --- | --- |
| Template | `applications/kingdom/sections/campaign/deadlines.hbs` |
| Context | `kingdom/sheet/contexts/DeadlineContext.kt` |
| Dialog | `kingdom/dialogs/ScheduleEventDialog.kt` + `applications/kingdom/schedule-event.hbs` |
| Styles | `.km-deadline*` in `applications/kingdom/kingdom-sheet.css` |
| i18n | `pf2e-kingmaker-tools.kingdom.deadlines.*` |

Rows show name, next firing as a countdown in days, recurrence, and resolve-link status. GM-only:
these leak unarrived pressure by construction, so `DeadlineContext` is populated only when
`game.user.isGM`, not merely hidden in the template.

`deadlines.hbs` is a registered partial with no parent frame — inside `{{#each}}` use `@root`, never
`../`. Payload and recurrence labels must be **literal** i18n keys mapped from the enum in a `when`;
`t("deadlines.payload.$kind")` is invisible to `check_i18n_keys.py` and ships as a raw key with every
guard green.

## 9. Boundary with the seasonal-economy plan

`2026-07-09-plan-seasonal-economy.md` is also date-driven, and the card asks for the boundary to be
named. **Keep them independent.**

- This engine fires **discrete, GM-confirmed, one-off-or-recurring beats** the GM authored.
- Seasonal economy applies **continuous, automatic modifiers** derived from the date.

A shared "world-date rules" abstraction would have to unify an authored offer with a derived
modifier, which have opposite confirmation semantics. The shared piece is the **day number** from §2,
and that is the whole boundary: both read `worldTimeSeconds.floorDiv(DAY_SECONDS)`, and neither
imports the other.

## 10. Interactions and out of scope

**Reads:** `kingdom.quests`, `kingdom.warThreats`, `kingdom.campaignClocks`, world time.
**Writes:** `kingdom.scheduledPressures`; payloads write only through the systems that own them
(event queue, encounter queue, clock advance), all on confirm.

**Out of scope:** rewriting campaign clocks to be date-driven; real calendar month lengths;
auto-applying any payload; player-visible deadlines; schedules that reschedule themselves
conditionally; importing the AP's canonical dates.

## 11. Test plan

**commonTest** (`PressureScheduleTest`)
- A weekly schedule over a **7-day jump** yields exactly one firing; over a **21-day jump**, three,
  in ascending day order — the multi-day case.
- A daily schedule over a 7-day jump yields seven.
- `fromDay` exclusive, `toDay` inclusive: a schedule due exactly on `fromDay` does not re-fire.
- `lastFiredDay` suppresses a re-tick over an already-covered span.
- `endDay` stops recurrence on the day after it, inclusive on the day itself.
- A schedule whose `isResolved` returns true yields nothing, and is suspended rather than removed.
- `escalationCount` increments once per firing, so three firings in one jump escalate 1, 2, 3.
- An unknown `recurrence` or `payloadKind` skips that schedule and still returns the others.
- A one-shot (`recurrence = none`) fires once and never again.

**jsTest** — Raw↔model round trip preserving every nullable payload slot; resolve-condition polling
against fixture quests and threats, including a `ref` that matches nothing; digest context for a
multi-firing day; `DeadlineContext` null for a non-GM.

**Mutation-check every new test**: make `fromDay` inclusive, ignore `lastFiredDay`, return only the
first firing of a jump, drop the `endDay` bound — and confirm the mutation *compiled* before
believing a "survived" result.

**Manual Foundry checklist**
1. Create the Troll Sightings preset; advance **one week in a single step** → one firing, not seven,
   and not zero.
2. Create the cult-event preset; advance one week → seven rows in one digest, grouped by day.
3. Dismiss a firing → it does not re-post on the next advance.
4. Confirm a firing → the payload applies and a calendar note appears; dismiss another → no note.
5. Complete the linked quest → the Stag Lord deadline suspends and stops firing, and still shows in
   Deadlines with its end recorded.
6. Advance time backwards (a GM correcting the clock) → nothing fires.
7. Log in as a player → no Deadlines section, no digest.

## 12. Phasing

**Phase 1 — pure engine.** `PressureSchedule.kt`, the enums, `dueFirings`, full commonTest suite.
Nothing wired.

**Phase 2 — data and tick.** `RawScheduledPressure`, `kingdom.scheduledPressures`, the migration plus
`MigrationChainTest`, `DailyTickHooks` registration, resolve-condition polling. Firings post a plain
chat line; no UI yet.

**Phase 3 — digest and offers.** `pressure-digest.hbs`, the four buttons, `logToCalendar` on confirm,
i18n across all eight locales (one short and CI goes red; only `check_i18n_keys.py --all` catches it).

**Phase 4 — authoring dialog and Deadlines section.** `ScheduleEventDialog` with the three presets,
the Campaign-tab section, context and CSS.

Phases 1 and 2 ship nothing user-visible, which is what makes 3 and 4 safe.
