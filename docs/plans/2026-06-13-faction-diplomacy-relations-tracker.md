# Faction & Diplomacy Relations Tracker Implementation Plan

> **Status:** In progress — phases 1–3 landed on `kingmaker.5`.
> **Date:** 2026-06-13
> **Roadmap item:** New backlog #1 (Faction & diplomacy relations tracker)
>
> **Implementation progress (2026-06-13):**
> - ✅ **Phase 1** — `FactionAttitude` enum + pure `FactionRelations` standing math
>   (commonMain) with `FactionRelationsTest`; `RawFactionStandingEntry` + nullable
>   `standing`/`standingLog`/`allianceLevel` on `RawGroup`. Commit `7d90f6c7`.
> - ✅ **Phase 2** — attitude/standing shown read-only in the existing Trade Agreements
>   section via `GroupContext` + i18n. Commit `4afde69c`.
> - ✅ **Phase 3** — `ModifyFactionStanding` dialog: adjust a faction's standing by a signed
>   delta with a reason; applies via `applyStandingDelta` and appends a change-log entry.
> - ⬜ **Phase 4 (deferred)** — turn-tick standing drift, threshold hooks (war-threat / quest
>   offers via `shouldOfferWarThreat` / `shouldOfferDiplomacyQuest`), treaty UI, and the
>   `faction-standing-change` chat card. Note: implementation integrates into the existing
>   Trade Agreements section rather than a standalone Diplomacy section/nav (less duplication).
> **Builds on:** `kingdom/data/RawGroup.kt` (existing static trade-partner model), the
> Army & War Pressure board (#12), and the Quest/Event generator (#2).

---

## Executive Summary

The module already models other powers as **Groups** (`RawGroup`): a flat list with a
`name`, a `negotiationDC`, an `atWar` flag, a `preventPledgeOfFealty` flag, and a
`relations` string (`none` / `diplomatic-relations` / `trade-agreement`). That is enough
to resolve the RAW *Establish Trade Agreement* / *Pledge of Fealty* activities, but it is
inert: a Group has no standing that moves over time, no history, no link to quests, wars,
or events, and no GM-facing place to see "where do we stand with Pitax right now?"

This feature promotes Groups into a **living diplomacy layer**. Each faction gains an
**attitude track** (a numeric standing with named bands), a **change log** of what moved
it, optional **treaty state**, and **hooks** so that kingdom activities, kingdom events,
and war-pressure threats can shift standing — and so that crossing a threshold can spawn a
quest (via #2) or a war threat (via #12).

**Key capabilities:**

- **Attitude track per faction:** an integer standing mapped to PF2e-style attitude bands
  (Hostile → Unfriendly → Indifferent → Friendly → Helpful), shown on the Kingdom Sheet.
- **Standing change log:** an append-only list of `(turn, delta, reason)` entries so the GM
  can see *why* a faction feels the way it does.
- **Treaty / relations state:** retains and extends the existing `relations` field; adds
  optional alliance/non-aggression/tribute flags.
- **Drivers:** kingdom activities (e.g. *Send Diplomatic Envoy*, *Infiltrate*), kingdom
  events, and war-pressure ticks can apply a documented standing delta.
- **Threshold hooks:** dropping to Hostile can auto-create a `RawWarThreat` (#12); a rumor
  or offer at Friendly+ can generate a quest (#2). All hooks are GM-confirmed, never silent.
- **Backward compatible:** existing `RawGroup` data keeps working; new fields are nullable
  and default to a neutral standing.

**Relationship to architecture:** Pure standing math lives in a UI-free `FactionRelations.kt`
(mirroring `PacingAlerts.kt` / `ArmyWarPressure.kt` / `VkExtras.kt`). The Kingdom Sheet renders
a Diplomacy section from a thin `@JsPlainObject` context. Standing drift is applied during the
existing `TurnTickingEngine` tick so it participates in preview/commit parity like every other
turn effect.

---

## Affected Files

### New Kotlin Files

| File | Purpose |
|------|----------|
| `src/jsMain/kotlin/.../kingdom/data/RawFactionStanding.kt` | `@JsPlainObject` standing + change-log entry interfaces |
| `src/jsMain/kotlin/.../kingdom/FactionRelations.kt` | **Pure** standing math: band derivation, clamped delta application, hook predicates (unit-tested) |
| `src/jsMain/kotlin/.../kingdom/sheet/contexts/DiplomacyContext.kt` | Thin UI context for the Diplomacy section |
| `src/jsMain/kotlin/.../kingdom/dialogs/ModifyFactionStanding.kt` | GM dialog to adjust standing / treaty state with a reason |

### New Handlebars Templates

| File | Purpose |
|------|----------|
| `src/jsMain/resources/applications/kingdom/sections/diplomacy/page.hbs` | Diplomacy section: one card per faction (single root element) |
| `src/jsMain/resources/applications/kingdom/sections/diplomacy/faction-card.hbs` | Reusable faction card: name, attitude band, treaty chips, recent log |
| `src/jsMain/resources/chatmessages/faction-standing-change.hbs` | Chat card when a threshold is crossed |

### Modified Kotlin Files

| File | Changes |
|------|---------|
| `kingdom/data/RawGroup.kt` | Add nullable `standing: Int?`, `standingLog: Array<RawFactionStandingEntry>?`, optional treaty flags — keep existing fields |
| `kingdom/KingdomData.kt` | No new top-level field needed (Groups already live on the kingdom); confirm Groups are reachable for the tick |
| `kingdom/TurnTickingEngine.kt` | During tick, apply any queued standing deltas + evaluate threshold hooks; include changes in `TickResult` so preview/commit match |
| `kingdom/sheet/KingdomSheet.kt` | Register the Diplomacy section + nav entry; wire the ModifyFactionStanding action; build `DiplomacyContext` |
| `kingdom/sheet/navigation/MainNavEntry.kt` | Add the Diplomacy nav entry |
| `lang/en.json` | Nested `kingdom.diplomacy.*` keys (attitude band labels, treaty labels, log reasons, dialog text) — **nested objects, never flat-dotted** (AGENTS.md) |
| `migrations/migrations/MigrationNN.kt` | Initialize `standing`/`standingLog` for existing Groups |

### Modified Handlebars

| File | Changes |
|------|---------|
| `src/jsMain/resources/applications/kingdom/kingdom-sheet.hbs` | Include `{{> kingdom-diplomacy}}` |

---

## Data Models

```kotlin
// RawFactionStanding.kt
@JsPlainObject
external interface RawFactionStandingEntry {
    var turn: Int          // kingdom turn the change happened
    var delta: Int         // signed standing change
    var reason: String     // i18n key or raw text, e.g. "diplomacy.reason.envoy"
}
```

`RawGroup` gains (all nullable for back-compat):

```kotlin
var standing: Int?                              // -100..100, 0 = Indifferent; null => neutral
var standingLog: Array<RawFactionStandingEntry>?
var allianceLevel: String?                      // null | "non-aggression" | "alliance" | "tribute"
```

`FactionRelations.kt` (pure, mirrors the `VkExtras.kt` style):

```kotlin
enum class FactionAttitude { HOSTILE, UNFRIENDLY, INDIFFERENT, FRIENDLY, HELPFUL }

/** Maps a clamped standing integer to a PF2e-style attitude band. */
fun attitudeFor(standing: Int?): FactionAttitude

/** Clamp + apply a delta, returning the new standing (no side effects). */
fun applyStandingDelta(current: Int?, delta: Int): Int

/** True when a war threat should be offered (e.g. fell to HOSTILE this tick). */
fun shouldOfferWarThreat(before: Int?, after: Int): Boolean

/** True when a diplomacy quest hook should be offered (e.g. reached FRIENDLY+). */
fun shouldOfferDiplomacyQuest(before: Int?, after: Int): Boolean
```

Band thresholds (tunable, documented in KDoc): `<= -50` Hostile, `-49..-15` Unfriendly,
`-14..14` Indifferent, `15..49` Friendly, `>= 50` Helpful.

---

## Migration Plan

**Non-breaking.** New `RawGroup`/`KingdomData` fields are nullable. A new `MigrationNN`:
1. For each existing Group, leave `standing` null (treated as Indifferent by `attitudeFor`)
   or backfill from `relations` (`trade-agreement` → +20, `diplomatic-relations` → +10,
   `none`/`atWar` → 0 / negative) — **decision for Gregory**, see open questions.
2. Initialize `standingLog` to `emptyArray()`.
Existing saves load unchanged; the Diplomacy section renders Indifferent until the GM or an
activity moves a faction.

---

## Testing Strategy

**jsTest — pure logic (`FactionRelationsTest.kt`, ~14 tests)**
- `attitudeFor` band boundaries (each threshold edge, plus `null`).
- `applyStandingDelta` clamps at ±100 and is associative for sequential deltas.
- `shouldOfferWarThreat` / `shouldOfferDiplomacyQuest` fire only on the crossing tick, not
  while already past the threshold (no repeat spam).

**jsTest — data + context (~6 tests)**
- `RawFactionStanding` round-trip fidelity.
- `DiplomacyContext` builds correct band label/colour and orders the log newest-first.

**jsTest — engine integration (~5 tests)**
- A tick with a queued envoy delta updates standing and appends a log entry.
- Preview tick and commit tick produce identical standing (parity, like TurnWizard).
- Crossing into Hostile yields a war-threat offer in `TickResult` exactly once.

Build/verify per AGENTS.md: `python3 scripts/check_i18n_keys.py` then
`JAVA_HOME=<jdk25> ./gradlew assemble jsTest -x kotlinStoreYarnLock -Dorg.gradle.java.installations.paths=<jdk17>`
(Chrome headless + throwaway `karma.config.d` override).

---

## Manual Verification Checklist

1. Open Kingdom Sheet → a **Diplomacy** section/tab appears listing existing Groups.
2. Each faction card shows name, attitude band (colour-coded), treaty chips, and recent log.
3. Empty state reads "No factions tracked yet."
4. *Modify Standing* dialog: apply +10 with reason "Diplomatic envoy" → band updates, log gains an entry.
5. Run a kingdom turn with a queued delta → standing drifts; preview total equals committed total.
6. Drive a faction down to Hostile → a war-threat **offer** chat card appears (GM-confirmed, not auto-applied).
7. Raise a faction to Friendly → a diplomacy quest **offer** appears and can be sent to the Quest generator (#2).
8. Reload the world → standings and logs persist; pre-existing Groups still resolve trade activities.
9. All UI text resolves via i18n (no raw keys, no hardcoded English).

---

## Open Questions for Gregory

- Backfill standing from the existing `relations` field on migration, or start everyone at Indifferent?
- Should war-threat / quest hooks be **offers** (GM confirms) or auto-applied? (Plan assumes offers.)
- Which kingdom activities should carry a built-in standing delta, and how large?
- Do we want per-faction GM-only notes vs player-visible standing (visibility split like hex content)?
