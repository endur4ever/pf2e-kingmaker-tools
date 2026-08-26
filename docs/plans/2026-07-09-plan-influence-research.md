# Influence & Research Encounter Runners — Implementation Plan

> **Status:** Plan only — no implementation yet
> **Date:** 2026-07-09
> **Roadmap item:** Generic PF2e subsystem tooling (not in the numbered backlog; see `docs/feature-roadmap.md` "Planning rule")
> **Depends on:** Nothing hard. Reuses the GM-confirmed offer pattern from `ChatButtons.kt` (#1/#12), the `FormApp`/`CrudApplication` dialog stack, and the world-setting store already used for migration backups.
> **Branch:** `kingmaker.5`

---

## Executive Summary

`docs/house-rules.md` lists the subsystems Kingmaker leans on (lines 13–22): **Influence**, **Research**, Kingdom Building, Warfare, Camping, Weather. The module already automates the last four. **Influence** (banquets, court NPCs, the RAW PF2e Influence subsystem) and **Research** (Season of Bloom cult research, Vordakai's library, and the house-rule advice on line 130 to *"use the research subsystem to let them figure out"* the source of a kingdom event) are still **entirely paper-tracked**.

This feature ships **two small encounter runners**:

1. An **Influence tracker** — an NPC with discovery entries (skill + DC to learn about them), a list of influence skills + DCs, influence-point thresholds with freeform unlocked effects, resistances/weaknesses that shift point gains, per-PC round actions, and a running influence-point pool.
2. A **Research tracker** — a research library/project with a level, an accumulating Research Point (RP) pool, RP thresholds with freeform effects, and the skill checks that earn RP.

Both are **generic subsystem tooling**. The module ships **empty** runners plus a documented **JSON import format** so a GM can enter published encounter stats *once* and reuse them. **No copyrighted encounter content is shipped.**

Two design commitments frame everything below:

- **This is per-encounter social/exploration combat, not a relationship track.** It is deliberately *separate* from the adjacent companion-influence system (`PartyInfluenceContext.kt` / `RawPartyMemberInfluence.kt`), which is long-term, campaign-wide relationship state on a fixed 0–12 scale. See §6.1 for the explicit delineation.
- **Every mechanical benefit is a GM-confirmed offer.** Crossing a threshold never auto-applies an effect; it whispers the GM an offer card (`km-offer-*`) exactly like the war-threat / diplomacy-quest offers already in `ChatButtons.kt`.

---

## 1. Problem Statement + Player/GM Value

**Problem.** A Kingmaker GM running an Influence encounter (e.g. a banquet with several court NPCs) or a Research project (e.g. unlocking a cure over several downtime days) tracks everything on scratch paper: which discovery checks have been made, the per-skill DCs, how many influence/research points each PC has contributed, which thresholds have been reached, and what each threshold unlocks. It is bookkeeping-heavy, easy to desync, and invisible to players by design (discovery is hidden per PF2e advice), so nothing is shareable when the GM *does* want to reveal a fact.

**Value to the GM:**

- **One dashboard per encounter.** Add the participating PCs, record each check with a single click, watch the point pool and per-PC contributions update, and see which thresholds are now in reach.
- **Reusable stat blocks.** Enter a court NPC's discovery/influence/threshold table once via JSON import (or the add/edit dialog), and reuse it every time that NPC appears — no re-keying.
- **Never-silent consequences.** When influence/research crosses a threshold, the GM gets an offer card and decides whether to grant the unlocked benefit (post the effect text, or convert it into a quest/modifier via existing dialogs). Nothing fires behind the GM's back.

**Value to players:**

- **Selective reveal.** Discovery is GM-only by default (per PF2e guidance), but a per-row **reveal toggle** lets the GM surface a learned fact, an influence skill the party has discovered, or a reached threshold — so the shared table state matches table knowledge.
- **Legible progress.** Once revealed, players see a running influence/RP bar instead of trusting the GM's memory.

**Why it belongs in *this* module (vs a generic PF2e subsystem module):** §6.3 — a single concrete integration in v1: a **"Run this event as a Research project"** button on the kingdom-event browser that seeds a Research tracker from a `RawOngoingKingdomEvent`, directly implementing the house-rule on `docs/house-rules.md` line 130.

---

## 2. Data Model

All persisted shapes are `@JsPlainObject external interface` types in `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/data/` (JS-only; they get auto-generated constructors + `.copy`, exactly like `RawWarThreat` / `RawQuest`). **Every field that is not structurally required is nullable for migration safety.**

> **Architecture note.** `@JsPlainObject` types cannot live in `commonMain`. The **pure engine** (§3) therefore operates on plain Kotlin value types (enums, `data class`, `Int`), and a thin jsMain adapter maps `Raw*` ⇆ pure. This mirrors the existing split: pure `FactionRelations.kt` lives in `src/commonMain/kotlin/at/posselt/pfrpg2e/data/kingdom/` and works on `Int`/`FactionAttitude`, while `RawGroup` (jsMain) holds the persisted shape.

### 2.1 New external interfaces — Influence

```kotlin
// RawInfluenceEncounter.kt  (src/jsMain/.../kingdom/data/)

@JsPlainObject
external interface RawInfluenceEncounter {
    var id: String
    var name: String              // encounter/scene label, e.g. "Restov Banquet"
    var npcName: String           // the influenced NPC
    var description: String
    var level: Int?               // optional NPC/encounter level (display only in v1)

    /** Running shared influence-point pool (PF2e: a single pool the party fills). */
    var influencePoints: Int
    var status: String            // "active" | "resolved"

    /** Discovery checks: skill + DC to LEARN about the NPC (hidden until revealed). */
    var discoveries: Array<RawSubsystemCheck>?
    /** Influence skills the party can use to gain points, each with its DC. */
    var influenceSkills: Array<RawSubsystemCheck>?
    /** Influence-point thresholds and the freeform benefit each unlocks. */
    var thresholds: Array<RawSubsystemThreshold>?
    /** Traits that REDUCE influence gains when matched by a check (e.g. "flattery": -1). */
    var resistances: Array<RawInfluenceTrait>?
    /** Traits that INCREASE influence gains when matched (e.g. "gossip": +1). */
    var weaknesses: Array<RawInfluenceTrait>?

    /** Per-PC running contribution + round-action tracking. */
    var participants: Array<RawSubsystemParticipant>?
    /** Append-only log of recorded checks (audit trail + undo source). */
    var checkLog: Array<RawSubsystemCheckEntry>?

    /** Player-board visibility for the whole encounter (default GM-only). null/false => GM-only. */
    var visibleToPlayers: Boolean?
    var createdAt: Double?
    var updatedAt: Double?
}

@JsPlainObject
external interface RawInfluenceTrait {
    var label: String   // e.g. "flattery", "appeals to greed"
    var delta: Int      // resistance => negative, weakness => positive; applied to a matched check's base gain
    var note: String?
}
```

### 2.2 New external interfaces — Research

```kotlin
// RawResearchProject.kt  (src/jsMain/.../kingdom/data/)

@JsPlainObject
external interface RawResearchProject {
    var id: String
    var name: String              // "Vordakai's Library", "Cure for the Bloom"
    var description: String
    var libraryName: String?      // the research source, if any
    var libraryLevel: Int?        // research library level (display + optional DC hinting)

    /** Accumulating Research Points. */
    var researchPoints: Int
    /** Optional completion target (null => open-ended, GM eyeballs thresholds). */
    var maxResearchPoints: Int?
    var status: String            // "active" | "resolved"

    /** Skill checks available to earn RP (skill + DC). */
    var checks: Array<RawSubsystemCheck>?
    /** RP thresholds and the freeform benefit each unlocks. */
    var thresholds: Array<RawSubsystemThreshold>?
    var checkLog: Array<RawSubsystemCheckEntry>?

    /** Set when this project was seeded from a kingdom event (§6.3 integration hook). */
    var sourceEventId: String?

    var visibleToPlayers: Boolean?
    var createdAt: Double?
    var updatedAt: Double?
}
```

### 2.3 Shared external interfaces

```kotlin
// RawSubsystem.kt  (src/jsMain/.../kingdom/data/)  — shared by both runners

@JsPlainObject
external interface RawSubsystemCheck {
    var skill: String   // lore/skill slug or freeform label, e.g. "diplomacy", "society"
    var dc: Int
    var revealed: Boolean?   // discovery/influence-skill row shown to players (default GM-only)
    var note: String?
}

@JsPlainObject
external interface RawSubsystemThreshold {
    var points: Int          // point value at which this unlocks
    var effect: String       // FREEFORM text — no automation in v1 (§3.4)
    /** Idempotency guard for the GM offer card, mirroring RawWarThreat.offerConsumed. */
    var offerConsumed: Boolean?
    var revealedToPlayers: Boolean?
}

@JsPlainObject
external interface RawSubsystemParticipant {
    var uuid: String         // PC actor UUID
    var name: String
    var points: Int          // this PC's running contribution to the pool
    var actedThisRound: Boolean?  // per-round action bookkeeping (one influence action / PC / round)
}

@JsPlainObject
external interface RawSubsystemCheckEntry {
    var timestamp: Double
    var participantUuid: String?
    var skill: String
    var outcome: String      // "criticalSuccess" | "success" | "failure" | "criticalFailure"
    var pointsDelta: Int     // signed points actually applied (post-trait, post-clamp)
    var note: String?
}
```

### 2.4 Persistence — where this lives, and why

**Recommendation: a world-level game setting** (a JSON string registered on `Pfrpg2eKingdomCampingWeatherSettings`, alongside the existing `latestMigrationBackup` / `schemaVersion` string settings), holding:

```kotlin
@JsPlainObject
external interface RawSubsystemStore {
    var influenceEncounters: Array<RawInfluenceEncounter>?
    var researchProjects: Array<RawResearchProject>?
}
```

**Why world-level and not the kingdom actor flag:**

| Consideration | World setting (recommended) | Kingdom actor flag (`KingdomData`) |
|---|---|---|
| **Usable without a kingdom** | ✅ `docs/house-rules.md` (lines 13–33) explicitly says a GM can *skip Kingdom Building but keep the flavor/events*; Influence & Research are run in campaigns that have **no active kingdom actor**. A world setting is reachable regardless. | ❌ Unreachable in a Kingdom-less campaign — the entire store hangs off an actor that may not exist. |
| **Outlives scenes** | ✅ Single blob, client-global. | ✅ Also fine. |
| **Reuses migration chain** | ⚠️ Not via `migrateKingdom`; use `migrateOther(game)` (§2.5). | ✅ Reuses `migrateKingdom` exactly like Migration48. |
| **Reuses sheet + chat plumbing** | ⚠️ Not a kingdom-sheet tab; needs a standalone app (§4) and an actor-independent chat binder (§5.3). | ✅ Free kingdom-sheet tab + `findKingdomActor` chat routing. |

**Decision:** the "usable without a kingdom" argument is decisive for a *generic subsystem* tool, so the store is **world-level**. The cost is two concrete extra pieces of work — a standalone launcher app (§4) and one change to the chat-button binder so offer cards fire without a kingdom actor (§5.3) — both small and called out in the phasing. The kingdom-flag alternative is documented above and in §11 (Open Questions) as the fallback if Gregory would rather trade Kingdom-less support for the free plumbing.

> **Not the camping flag.** `CampingData` is per-camping-session and gc'd with rests; a research project spanning weeks of downtime would be lost.

### 2.5 Migration

Propose **`Migration49`** *(placeholder — not free; see caveat)* (`internal val migrations = listOf(...)` in `src/jsMain/kotlin/at/posselt/pfrpg2e/migrations/Migrations.kt` currently ends at `Migration61()`, and `MigrationChainTest` asserts contiguity).

> ⚠️ **The number in this section is a placeholder and must be re-derived at implementation.**
> The chain now ends at **`Migration65`**. Since these plans were written, four of the reserved
> numbers have LANDED: 62 = downtime-projects, 63 = scheduled-pressure-engine,
> 64 = map-dynamism, 65 = loot-manifests. `Migration49` was never free (it sits inside the
> long-registered 17..61 range) and several unimplemented plans still name it. The next free
> number is **66**. Take the next contiguous number when this actually lands, and extend
> `MigrationChainTest`'s hardcoded range.


> Gregory sequences the real number at implementation time; if other cards land first, bump accordingly and keep the `listOf` contiguous.

Because the store is a **new** world setting with a registered default of `{"influenceEncounters":[],"researchProjects":[]}`, no per-actor backfill is required. `Migration49` therefore overrides **`migrateOther(game)`** (not `migrateKingdom`) to defensively initialize the setting when the key is absent, and exists mainly to keep the schema version monotonic so downgrades are detected:

```kotlin
class Migration49 : Migration(49) {
    override suspend fun migrateOther(game: Game) {
        val settings = game.settings.pfrpg2eKingdomCampingWeather
        if (settings.getSubsystemStoreRaw().isBlank()) {
            settings.setSubsystemStore(RawSubsystemStore(
                influenceEncounters = emptyArray(),
                researchProjects = emptyArray(),
            ))
        }
    }
}
```

> **If Gregory picks the kingdom-flag alternative instead:** `Migration49` overrides `migrateKingdom` and adds nullable `influenceEncounters`/`researchProjects` arrays to `KingdomData`, mirroring `Migration48`'s null-guard style exactly.

---

## 3. Engine Design

### 3.1 Pure core (commonMain)

File: `src/commonMain/kotlin/at/posselt/pfrpg2e/data/kingdom/subsystems/SubsystemEngine.kt`
(sibling of the existing pure `FactionRelations.kt`). Pure, UI-free, unit-tested from `commonTest`. Operates on value types only — the jsMain adapter maps `Raw*` ⇆ these.

```kotlin
package at.posselt.pfrpg2e.data.kingdom.subsystems

enum class SubsystemOutcome { CRITICAL_SUCCESS, SUCCESS, FAILURE, CRITICAL_FAILURE }

/** Points per degree of success. PF2e Influence default: +2 / +1 / 0 / -1. */
data class PointRule(
    val criticalSuccess: Int = 2,
    val success: Int = 1,
    val failure: Int = 0,
    val criticalFailure: Int = -1,
)

/** A resistance (delta<0) or weakness (delta>0) keyed by a trait label. */
data class SubsystemTrait(val label: String, val delta: Int)
```

#### Core signatures (all pure)

```kotlin
/** Base points for an outcome, before trait adjustments. */
fun pointsForOutcome(outcome: SubsystemOutcome, rule: PointRule): Int

/**
 * Apply matched resistances/weaknesses to a base gain. `matched` are the traits whose
 * label the recorded check declared (GM-selected in the record dialog). Resistances can
 * reduce a gain to a floor of 0 for that check; weaknesses add on top.
 */
fun adjustForTraits(basePoints: Int, matched: List<SubsystemTrait>): Int

/**
 * New pool value after one check: clamp(previous + adjusted, min = 0). Returns the new
 * total AND the signed delta actually applied (for the check-log entry).
 */
data class PointApplication(val newTotal: Int, val appliedDelta: Int)
fun applyCheck(
    previous: Int,
    outcome: SubsystemOutcome,
    rule: PointRule,
    matched: List<SubsystemTrait>,
): PointApplication

/**
 * Which threshold point-values are newly crossed moving previous -> next, so an offer
 * fires exactly once per threshold (mirrors FactionRelations.shouldOfferWarThreat's
 * "only on the crossing tick" contract).
 */
fun newlyCrossedThresholds(previous: Int, next: Int, thresholds: List<Int>): List<Int>

/** Per-PC contribution update: returns the participant list with `uuid`'s points += delta. */
fun applyParticipantDelta(
    participants: List<ParticipantPoints>,
    uuid: String,
    delta: Int,
): List<ParticipantPoints>

data class ParticipantPoints(val uuid: String, val points: Int)
```

Research reuses the same primitives (`applyCheck`, `newlyCrossedThresholds`); RP is just another point pool with its own `PointRule` (RAW research often uses +2 crit / +1 success / 0 fail / −1 crit-fail, identical to the default, so no separate function is needed — only a distinct `PointRule` instance if the GM overrides it).

### 3.2 Determinism / seeding

**None needed.** These runners record the *outcome* of checks the GM/players have already resolved at the table (manual entry, §4). The engine performs no random selection, so there is no RNG, no seed, and no preview/commit parity concern.

### 3.3 Tick surface

**Neither tick fires this feature.** Per the required split:

- `TurnTickingEngine.tick()` (monthly, End Turn — `src/jsMain/.../kingdom/TurnTickingEngine.kt:167`) is the kingdom economy tick. Influence/Research points are **not** kingdom-turn resources; they advance from PC check actions inside an encounter, on whatever cadence the table plays at.
- `DailyTickHooks` (daily, world clock — `src/jsMain/.../kingdom/DailyTickHooks.kt`) advances weather and companion travel. A Research project *may* have a real-world deadline the GM tracks, but v1 does **not** auto-decrement anything on the daily clock (see OUT-OF-SCOPE, §6.4).

The runners are **encounter-scoped**: state changes only when the GM records a check or clicks an offer button. This is stated explicitly so no future card wires a third tick.

### 3.4 Threshold-effect authoring

**Freeform text, v1. No automation of effects.** `RawSubsystemThreshold.effect` is a plain string the GM writes ("The duke reveals the traitor's name"; "You may now cast the ritual at +2"). Reaching a threshold emits an **offer** (§5); the "Grant" button posts that text to chat and/or opens an existing prefilled dialog (`AddQuest` / `AddModifier`) if the GM wants a durable reward. The engine never parses or applies the effect string.

---

## 4. UI

Because the store is world-level (§2.4), the runner is **not** a kingdom-sheet nav entry. `MainNavEntry` (`src/jsMain/.../kingdom/sheet/navigation/MainNavEntry.kt`) is per-kingdom-actor; adding an entry there would tie the tool to a kingdom actor, defeating §2.4. Instead:

### 4.1 Standalone "Subsystem Trackers" application

A single `CrudApplication`-derived app (base: `src/jsMain/.../app/CrudApplication.kt`, the same base as `ArmyBrowser` / `StructureBrowser`) with two panes: **Influence** and **Research**. It reads/writes the world setting directly.

**Launch surfaces (v1):**
- A world macro / an exposed module-API method (e.g. `game.pf2eKingmakerTools.openSubsystemTrackers()`), so it works in Kingdom-less campaigns.
- A convenience button on the kingdom-sheet **Turn** tab (`sections/turn/…`) that just *opens* the standalone app — it does **not** store anything on the kingdom actor. (No scene-controls hook exists in the repo today, so a macro/API + sheet button is the pragmatic entry.)

**Add/edit dialogs** use `FormApp` exactly like `AddWarThreat.kt` / `AddQuest.kt`:
- `AddInfluenceEncounter` (`FormApp`) — name, NPC, level, and repeating rows for discoveries / influence skills / thresholds / resistances / weaknesses.
- `AddResearchProject` (`FormApp`) — name, library, level, target RP, and repeating rows for checks / thresholds.

### 4.2 Runner dashboard (per encounter/project)

Recorded via **manual-entry buttons (recommended v1)**, not auto-roll:

- A **participant picker** (add PCs from the party actor by UUID; reuses the `PartyMemberRef` idiom from `PartyInfluenceContext.kt`).
- Per check: pick **skill** (from the encounter's `influenceSkills`/`checks`), pick **PC**, optionally tick matched **traits**, then click a degree-of-success button: **[Crit Success] [Success] [Failure] [Crit Failure]**. The handler calls the pure engine (`applyCheck`), appends a `RawSubsystemCheckEntry`, updates the pool + per-PC contribution, and — if a threshold is newly crossed — emits the offer card (§5).
- A **reveal toggle** on each discovery/skill/threshold row (`revealed` / `revealedToPlayers`) and an encounter-level `visibleToPlayers` toggle.

> **Auto-roll is a deliberate v2 deferral.** The check pipeline (`KingdomCheckDialog.kt` / `KingdomRoll.kt`) could roll a PC's skill against the row DC and infer the degree of success automatically. v1 stays **manual-first** so the tool is useful immediately and never fights the GM's at-table adjudication.

### 4.3 Templates, contexts, i18n

**Templates** (`src/jsMain/resources/applications/subsystems/`):
- `tracker.hbs` — the two-pane CRUD shell (single root element).
- `influence-encounter.hbs` — one encounter's dashboard (pool bar, participants, check log, threshold list).
- `research-project.hbs` — one project's dashboard (RP bar, checks, threshold list).

Register partials by **name** in `Main.kt loadTemplatePartials` and reference by name (per the "Handlebars partials by name" convention).

**Context objects** (`src/jsMain/.../kingdom/sheet/contexts/SubsystemContext.kt`, thin `@JsPlainObject`, pure builders unit-testable from commonTest à la `buildPartyInfluenceContext`):

```kotlin
@JsPlainObject
external interface InfluenceEncounterContext {
    val id: String
    val name: String
    val npcName: String
    val influencePoints: Int
    val poolPercent: Int          // for the progress bar (relative to top threshold)
    val participants: Array<SubsystemParticipantContext>
    val discoveries: Array<SubsystemCheckContext>   // filtered by isGM/revealed
    val influenceSkills: Array<SubsystemCheckContext>
    val thresholds: Array<SubsystemThresholdContext>
    val isGM: Boolean
}
// + ResearchProjectContext, SubsystemParticipantContext, SubsystemCheckContext, SubsystemThresholdContext
```

**i18n namespace:** nested objects under the `"pf2e-kingmaker-tools"` root in `lang/en.json` (i18next nested-path lookup — flat-dotted keys render raw). New keys under **`subsystems.influence.*`** and **`subsystems.research.*`** (labels, degree-of-success button text, dialog legends, offer-card text). No new `translate*()` helper is needed in `initLocalization()` — these are static UI strings resolved through `t()` / the `localizeKM` Handlebars helper, unlike the data-driven `translate*` catalogs (events, activities, …).

---

## 5. Chat / Offer Surfaces (GM-Confirmed Only)

Every mechanical benefit is a whispered **GM-confirmed offer**. Two new `ChatButton("km-offer-…")` handlers are added to the `buttons` list in `src/jsMain/.../kingdom/ChatButtons.kt`, following the existing `km-offer-diplomacy-quest` / `km-offer-war-threat` shape.

### 5.1 Offer cards

| Trigger | Offer card (whispered to GMs) | Buttons | Handler |
|---|---|---|---|
| Influence pool crosses a threshold (via `newlyCrossedThresholds`) | `chatmessages/influence-threshold-offer.hbs` | **[Grant]** · **[Convert to Quest]** · **[Dismiss]** | `ChatButton("km-offer-influence-threshold")` |
| Research RP crosses a threshold | `chatmessages/research-threshold-offer.hbs` | **[Grant]** · **[Convert to Quest]** · **[Dismiss]** | `ChatButton("km-offer-research-threshold")` |

**Button semantics (no effect automation, §3.4):**
- **Grant** — posts the threshold's freeform `effect` text publicly and sets `offerConsumed = true` on that threshold (idempotency guard, mirroring `RawWarThreat.offerConsumed`). Clicking a consumed threshold is a no-op.
- **Convert to Quest** *(optional)* — opens `AddQuest` prefilled with the effect text (reusing the exact pattern `km-offer-diplomacy-quest` uses today), so an unlocked benefit becomes a durable quest/reward. `AddModifier` is an analogous alternative if the benefit is a kingdom modifier.
- **Dismiss** — sets `offerConsumed = true` without posting, so the card can't re-fire.

`data-*` payload on each button: `data-store="influence|research"`, `data-entry-id` (encounter/project id), `data-threshold-points`. The button reads/writes the **world setting**, not a kingdom actor.

### 5.2 Where offers are emitted

Offers are emitted at **check-record time** in the runner (§4.2), not on any tick — the record handler compares `previous` vs `next` pool via `newlyCrossedThresholds` and posts one card per newly crossed threshold. Resistances/weaknesses are already folded into the delta before the comparison, so a threshold can only be offered when the *applied* points actually reach it.

### 5.3 Required plumbing change (world-level offers without a kingdom actor)

`bindChatButtons(game)` currently routes every button through `parent.findKingdomActor(game)?.let { … }`, so a button whose card has no owning kingdom actor **never fires**. Because these two offers are world-scoped, Phase 4 adds a small **actor-independent binder** (or generalizes `bindChatButtons` to bind a second list whose callbacks take only `(game, event, button)`), so the influence/research offer buttons work even in a Kingdom-less campaign. This is the concrete cost of the world-level store called out in §2.4; the kingdom-flag alternative would not need it.

---

## 6. Interactions with Existing Systems + Out-of-Scope

### 6.1 Delineation from companion influence (MANDATORY)

| | **Companion influence** (existing) | **This feature** (new) |
|---|---|---|
| Purpose | Long-term relationship state | Per-**encounter** social/exploration combat |
| Scale | Fixed 0–12 (`MAX_COMPANION_INFLUENCE`) | Open-ended point pool with GM-authored thresholds |
| Data | `RawPartyMemberInfluence` (companionId × memberUuid) | `RawInfluenceEncounter` (NPC + discoveries + skills + thresholds + traits) |
| Store | Kingdom flag (`KingdomData.partyInfluence`) | World setting (§2.4) |
| Cadence | Once per camp session | Once per round, during an encounter |
| Files | `PartyInfluenceContext.kt`, `data/RawPartyMemberInfluence.kt` | new `RawInfluenceEncounter.kt`, `SubsystemContext.kt` |

They **share UI idioms only** (progress bars, a per-PC matrix, the `PartyMemberRef` party-actor read). They must **not** share data types or storage. The plan reuses `buildPartyInfluenceContext`'s *style*, not its state.

### 6.2 Systems reused (concrete paths)

| System | File(s) | Interaction |
|---|---|---|
| GM-confirmed offer pattern | `kingdom/ChatButtons.kt` | Add 2 handlers + 1 actor-independent binder (§5). |
| Dialog stack | `app/FormApp.kt`, `app/CrudApplication.kt`, `kingdom/dialogs/AddWarThreat.kt` (shape reference) | New `AddInfluenceEncounter` / `AddResearchProject` + the CRUD tracker app. |
| Quest/modifier conversion | `kingdom/dialogs/AddQuest.kt`, `kingdom/dialogs/AddModifier.kt` | "Convert to Quest" offer button reuses the prefill pattern. |
| World-setting store | `settings/Pfrpg2eKingdomCampingWeatherSettings.kt` | Register + get/set the `RawSubsystemStore` JSON (same pattern as `latestMigrationBackup`). |
| Migration chain | `migrations/Migrations.kt`, `migrations/migrations/Migration.kt` | Register `Migration49` (via `migrateOther`); `MigrationChainTest` guards contiguity. |
| Localization | `utils/Localization.kt`, `lang/en.json` | Nested `subsystems.*` keys; no new `translate*()` helper. |
| Party-actor read | `kingdom/sheet/contexts/PartyInfluenceContext.kt` (`PartyMemberRef`) | Participant picker reads live party membership. |

### 6.3 The one kingdom-event integration hook (v1)

`docs/house-rules.md` line 130: *"Prepare some Kingdom Events as quests … Use the research subsystem to let them figure out that the source was a druid …"*.

v1 ships exactly **one** module-specific integration: a **"Run as Research project"** button on the kingdom-event browser / ongoing-event card (`kingdom/dialogs/KingdomEventManagement.kt` + the event-browser template). Clicking it seeds a `RawResearchProject` with `name`/`description` from the `RawOngoingKingdomEvent` and `sourceEventId` set, then opens the Research runner. This is the concrete reason the feature belongs in *this* module rather than a generic PF2e subsystem module.

### 6.4 Explicit OUT-OF-SCOPE

- **No auto-roll.** Manual degree-of-success entry only in v1 (auto-roll via `KingdomCheckDialog`/`KingdomRoll` is v2).
- **No effect automation.** Thresholds are freeform text; nothing is applied mechanically.
- **No tick integration.** Neither monthly nor daily; no auto-deadline on research.
- **No shipped copyrighted content.** Empty runners + a documented JSON schema only.
- **No companion-influence merge.** The two systems stay separate data + storage (§6.1).
- **No per-PC "victory point" scoring / initiative order** — the runner records points, not turn order.
- **No cross-world sync** — the setting is per-world.

---

## 7. Test Plan

### 7.1 commonTest — pure logic (JVM-less, `kotlin.test`)

**File:** `src/commonTest/kotlin/at/posselt/pfrpg2e/data/kingdom/subsystems/SubsystemEngineTest.kt`

| Test | Description |
|---|---|
| `pointsForOutcome_defaultRule` | +2 / +1 / 0 / −1 for the four degrees. |
| `adjustForTraits_resistanceFloorsAtZeroForThatCheck` | A −2 resistance on a +1 success yields 0, not −1. |
| `adjustForTraits_weaknessAddsOnTop` | A +1 weakness on a +2 crit yields +3. |
| `applyCheck_poolNeverBelowZero` | Crit-fail at pool 0 stays 0; returns `appliedDelta = 0`. |
| `applyCheck_returnsSignedAppliedDelta` | Delta recorded equals the post-trait, post-clamp change. |
| `newlyCrossedThresholds_firesOncePerThreshold` | 3→7 with thresholds [5,10] returns [5]; 7→7 returns []. |
| `newlyCrossedThresholds_multipleInOneJump` | 0→11 with [5,10] returns [5,10]. |
| `applyParticipantDelta_accumulatesPerPc` | Two checks by the same PC sum; other PCs untouched. |
| `research_reusesApplyCheck` | RP pool advances identically under a research `PointRule`. |

### 7.2 jsTest — Foundry-integrated (`src/jsTest/kotlin/…`)

**Files:** `SubsystemStoreTest.kt`, `SubsystemContextTest.kt`, `Migration49Test.kt`.

| Test | Description |
|---|---|
| `store_roundTripsThroughWorldSetting` | `RawSubsystemStore` serialize → setting → deserialize is lossless. |
| `context_filtersHiddenRowsForPlayers` | `isGM=false` hides unrevealed discoveries/thresholds; `isGM=true` shows all. |
| `context_poolPercentRelativeToTopThreshold` | Bar percent maths against the highest threshold. |
| `recordCheck_emitsOfferOnlyOnCrossing` | Recording a success that crosses a threshold posts exactly one offer; a non-crossing success posts none. |
| `offer_grantMarksThresholdConsumed` | Grant sets `offerConsumed`; a second click is a no-op. |
| `offer_firesWithoutKingdomActor` | The actor-independent binder (§5.3) invokes the handler in a world with no `KingdomActor`. |
| `Migration49_initializesEmptyStore` | `migrateOther` writes the default blob when the key is absent; leaves an existing store untouched. |
| `Migration49Test_chainContiguous` | (Covered by existing `MigrationChainTest`) — 17…49 contiguous. |

Run per repo convention: `python3 scripts/check_i18n_keys.py`, then `JAVA_HOME=<jdk25> ./gradlew assemble jsTest -x kotlinStoreYarnLock` with Chrome headless (`useChromeHeadless` + `CHROME_BIN`).

### 7.3 Manual Foundry verification checklist

1. Open the Subsystem Trackers app via macro/API (with **no** kingdom actor present) — it opens and shows two empty panes.
2. **Add Influence Encounter** "Restov Banquet" (NPC "Duke Sellemius", 1 discovery DC 18, 2 influence skills, thresholds at 5/10, one resistance "flattery" −1, one weakness "history" +1). Save → appears in the Influence pane.
3. Open it, add two party PCs as participants.
4. Record a **Success** on the "history" (weakness) skill for PC A → pool goes to +2, PC A shows 2, a check-log row appears.
5. Record checks until the pool crosses **5** → a whispered offer card appears. Click **Grant** → effect text posts publicly; threshold shows consumed; re-clicking does nothing.
6. Toggle **reveal** on the discovery row and set the encounter **visibleToPlayers** → log in as a player: the revealed row + pool bar are visible, unrevealed rows are not.
7. **Add Research Project** and repeat the RP-threshold → offer → Grant flow; click **Convert to Quest** on one threshold → `AddQuest` opens prefilled → Save → quest appears in the kingdom Quests tab.
8. From the kingdom-event browser, click **Run as Research project** on an ongoing event → a Research project is seeded with the event's name/description and `sourceEventId`.
9. **Reload the world** → all encounters, projects, points, participants, logs, consumed/reveal flags persist.
10. Import a JSON stat block (§Appendix) via the app's **Import** action → the encounter/project is created with all rows.

---

## 8. Phasing (independently committable, one worker card each)

| Phase | Title | Deliverable | Key files |
|---|---|---|---|
| **1** | **Data model + world store + Migration49** | `Raw*` interfaces, `RawSubsystemStore`, world-setting register + get/set, JSON import schema doc, `Migration49` (`migrateOther` initializer), registration in `Migrations.kt`. | `data/RawInfluenceEncounter.kt`, `data/RawResearchProject.kt`, `data/RawSubsystem.kt`, `settings/Pfrpg2eKingdomCampingWeatherSettings.kt`, `migrations/migrations/Migration49.kt`, `migrations/Migrations.kt` |
| **2** | **Pure engine (commonMain) + tests** | `SubsystemEngine.kt` (all pure signatures, `PointRule`, trait math, threshold-crossing) + `SubsystemEngineTest.kt`. | `data/kingdom/subsystems/SubsystemEngine.kt`, `commonTest/.../SubsystemEngineTest.kt` |
| **3** | **Runner UI — CRUD app, dialogs, contexts, i18n** | Standalone `SubsystemTrackers` `CrudApplication` + two panes, `AddInfluenceEncounter`/`AddResearchProject` `FormApp`s, manual-entry degree-of-success buttons, reveal toggles, `SubsystemContext.kt` + pure builders, templates, `subsystems.*` i18n, macro/API launcher + Turn-tab convenience button. | `app/…` new app, `kingdom/dialogs/AddInfluenceEncounter.kt`, `AddResearchProject.kt`, `kingdom/sheet/contexts/SubsystemContext.kt`, `resources/applications/subsystems/*.hbs`, `lang/en.json`, `Main.kt` (partials + launcher) |
| **4** | **Offer cards + event hook + import + QA** | 2 `km-offer-*` handlers, actor-independent chat binder (§5.3), offer templates, "Run as Research" event-browser hook, JSON import action, `SubsystemContextTest`/`SubsystemStoreTest`/`Migration49Test` (jsTest) + manual checklist. | `kingdom/ChatButtons.kt`, `resources/chatmessages/influence-threshold-offer.hbs`, `research-threshold-offer.hbs`, `kingdom/dialogs/KingdomEventManagement.kt` (hook), `jsTest/.../*` |

**Dependencies:** Phase 2 is independent of Phase 1 (pure vs data) and they can run in parallel. Phase 3 depends on 1+2. Phase 4 depends on 3.

---

## 9. Appendix — JSON import format (no copyrighted content shipped)

The module ships an **empty** store; GMs enter published stats once and reuse. The import action accepts either shape:

```json
{
  "influenceEncounters": [
    {
      "name": "Example Banquet",
      "npcName": "Example Court NPC",
      "level": 4,
      "discoveries": [ { "skill": "society", "dc": 18 }, { "skill": "diplomacy", "dc": 20 } ],
      "influenceSkills": [ { "skill": "diplomacy", "dc": 22 }, { "skill": "deception", "dc": 24 } ],
      "thresholds": [
        { "points": 4, "effect": "The NPC shares a rumor." },
        { "points": 8, "effect": "The NPC offers a favor." }
      ],
      "resistances": [ { "label": "flattery", "delta": -1 } ],
      "weaknesses": [ { "label": "appeals to reason", "delta": 1 } ]
    }
  ],
  "researchProjects": [
    {
      "name": "Example Library",
      "libraryName": "Example Collection",
      "libraryLevel": 5,
      "maxResearchPoints": 15,
      "checks": [ { "skill": "arcana", "dc": 21 }, { "skill": "occultism", "dc": 21 } ],
      "thresholds": [
        { "points": 5, "effect": "First clue uncovered." },
        { "points": 15, "effect": "Full answer revealed." }
      ]
    }
  ]
}
```

Values above are **placeholder examples**, not published stat blocks. Ids/timestamps are assigned on import.

---

## 10. Open Questions for Gregory

1. **World setting vs kingdom flag** (§2.4). Plan recommends world-level for Kingdom-less usability, at the cost of a standalone app + one chat-binder change. Accept, or prefer the kingdom-flag store for free sheet/chat plumbing?
2. **Launcher surface.** Macro + module API + Turn-tab button (plan) — or is a dedicated scene-control tool worth adding (no such pattern exists in the repo yet)?
3. **Point rule defaults.** Ship the PF2e default (+2/+1/0/−1) as fixed, or expose an editable `PointRule` per encounter/project in v1?
4. **"Convert to Quest" button.** Include in v1 (plan) or defer, keeping only Grant/Dismiss?
5. **Per-PC round gating.** Track `actedThisRound` as advisory (plan) or enforce one influence action per PC per round?

---

**End of Plan.** Ready for review. On approval, create the four phase cards per §8.
