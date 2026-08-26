# Influence & Research Encounter Runners — Implementation Plan

> **Status:** Plan only — no implementation yet
> **Date:** 2026-07-09
> **Roadmap item:** Generic PF2e subsystem tooling (not in the numbered backlog; see `docs/feature-roadmap.md` "Planning rule")
> **Depends on:** Nothing hard. Reuses the GM-confirmed offer pattern from `ChatButtons.kt` (#1/#12), the `SimpleApp`/`FormApp` dialog stack, and the world-setting store already used for migration backups.
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

## 0. Rules Grounding (PF2e subsystems)

Both runners implement structures published in **GM Core**'s *Subsystems* chapter — the **Influence** and **Research** sections (the same two subsystems, under the same headings, in the pre-remaster *Gamemastery Guide*). Cited **by structure and section name only**: no rules text, table or stat block is reproduced here or shipped by the module (§6.4, §9). The tables below exist so an implementer can tell a *modelling decision* from a *deviation*.

### 0.1 Influence — GM Core, *Subsystems* → **Influence**

| RAW structure | Plan field | What v1 automates |
|---|---|---|
| **Influence stat block** — one NPC entry listing Discovery skills + DCs, Influence skills + DCs, resistances, weaknesses and numbered influence thresholds | `RawInfluenceEncounter` (§2.1) | Nothing — authored by the GM via dialog or JSON import (§9). |
| **Discovery check** — a check to *learn about* the NPC; on a success the GM reveals one influence skill and its DC, or a resistance/weakness | `discoveries: Array<RawSubsystemCheck>` + `revealed` (§2.3) | GM records the check, then flips `revealed` on the row it exposed. The tool never decides *which* row a success reveals. |
| **Influence check** — a skill check against that skill's influence DC feeding one shared pool: critical success +2 IP, success +1, failure 0, critical failure −1 (pool floored at 0) | `PointRule(2, 1, 0, -1)` + `influencePoints` (§3.1) | Fully automated by `applyCheck`. **This is where §3.1's `PointRule` default comes from.** |
| **Influence rounds** — the encounter runs in rounds; each PC takes **one** Influence *or* Discovery action per round | `RawSubsystemParticipant.actedThisRound` (§2.3), surfaced in §4.2 | **Advisory only.** The flag is displayed and cleared by a *New round* button; nothing is blocked. See §10 Q5. |
| **Influence thresholds** — reaching a listed IP total unlocks a benefit from the NPC | `thresholds: Array<RawSubsystemThreshold>` | Crossing is detected (`newlyCrossedThresholds`); the benefit is freeform text delivered as a GM offer (§3.4, §5). |
| **Resistances / weaknesses** — printed as adjustments to the influence **DC** for a named approach | `RawInfluenceTrait.delta` | Deliberate deviation — see §0.3. |

### 0.2 Research — GM Core, *Subsystems* → **Research**

| RAW structure | Plan field | What v1 automates |
|---|---|---|
| **Library / research subject** — the source being researched, with its own level | `libraryName`, `libraryLevel` (§2.2) | Display only. |
| **Research check** — a skill check against the library's DC (derived from the library's level via the standard level-based DC table), each check representing a fixed span of research time | `checks: Array<RawSubsystemCheck>` — skill + an **explicit** DC | Nothing: the GM types the DC they used. The level→DC derivation is **out of scope** (§6.4). |
| **Research Points** — RP accrue per check; a library may define a maximum RP total | `researchPoints`, `maxResearchPoints` (§2.2) | `applyCheck` again. `maxResearchPoints` is a display target, **not** a clamp, in v1. |
| **Research thresholds** — reaching an RP total reveals a piece of information | `thresholds` (shared shape, §2.3) | Same offer path as Influence. |

**On the Research point rule.** Unlike Influence, the Research section does not print one universal degrees-of-success table — published libraries and adventures vary (some award RP only on a success, some subtract on a critical failure, some attach a complication instead). The plan therefore ships the **Influence** rule (+2 / +1 / 0 / −1) as the *default* `PointRule` for both runners and books a per-project override as §10 Q3, rather than asserting a Research RAW default.

### 0.3 Where v1 knowingly deviates

- **Resistances/weaknesses are point deltas, not DC shifts.** RAW prints them as adjustments to the influence *DC*. v1 is manual-entry (§4.2): by the time the GM records an outcome the roll has already happened against whatever DC they applied, so a DC shift has nothing left to modify. `RawInfluenceTrait.delta` therefore adjusts the *points awarded* on a check the GM tags with that trait (`adjustForTraits`, §3.1), which keeps the trait visible and auditable in the check log at the cost of not matching the printed number. **§10 Q6** asks whether to re-cast `delta` as an advisory DC shift shown on the skill row instead.
- **The round economy is tracked, not enforced** (§4.2, §10 Q5).
- **No DC derivation anywhere.** Neither the level-based DC table nor library-level DC bands are implemented; every DC in both runners is a number a GM typed (§6.4).

---

## 1. Problem Statement + Player/GM Value

**Problem.** A Kingmaker GM running an Influence encounter (e.g. a banquet with several court NPCs) or a Research project (e.g. unlocking a cure over several downtime days) tracks everything on scratch paper: which discovery checks have been made, the per-skill DCs, how many influence/research points each PC has contributed, which thresholds have been reached, and what each threshold unlocks. It is bookkeeping-heavy, easy to desync, and invisible to players by design (discovery is hidden per PF2e advice), so nothing is shareable when the GM *does* want to reveal a fact.

**Value to the GM:**

- **One dashboard per encounter.** Add the participating PCs, record each check with a single click, watch the point pool and per-PC contributions update, and see which thresholds are now in reach.
- **Reusable stat blocks.** Enter a court NPC's discovery/influence/threshold table once via JSON import (or the add/edit dialog), and reuse it every time that NPC appears — no re-keying.
- **Never-silent consequences.** When influence/research crosses a threshold, the GM gets an offer card and decides whether to grant the unlocked benefit (post the effect text, or convert it into a quest/modifier via existing dialogs). Nothing fires behind the GM's back.

**Value to players:**

- **Selective reveal.** Discovery is hidden from the player UI by default (per PF2e guidance; see the §2.4 caveat — hidden is not secret), but a per-row **reveal toggle** lets the GM surface a learned fact, an influence skill the party has discovered, or a reached threshold — so the shared table state matches table knowledge.
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

> **Unparseable `outcome` strings — LOG AND SKIP, never throw.** `outcome` is a free `String` on the
> wire but a `SubsystemOutcome` in the engine (§3.1), and §9's importer accepts user-supplied JSON, so
> the string can be anything. The jsMain adapter maps it with `SubsystemOutcome.fromString(entry.outcome)`
> and, on `null`, **drops that check-log row, `console.warn`s the offending value and the entry's
> `timestamp`, and keeps loading the rest of the store**. It must not throw: one typo in an imported
> file would otherwise take the whole tracker app down. Pool totals are *not* recomputed from the log —
> `influencePoints` / `researchPoints` are the stored truth, so a skipped row costs an audit line, not
> a score.

### 2.4 Persistence — where this lives, and why

**Recommendation: a world-level game setting** — a JSON string added to the `nonUserVisibleSettings.strings` map in `settings/Pfrpg2eKingdomCampingWeatherSettings.kt:458-467` with default `"{}"`, exactly like `latestMigrationBackup` (line 461) and `homebrewProfileRegistry` (line 466). That whole map is registered by `registerSimple(game.settings, nonUserVisibleSettings.strings, hidden = true)` (line 472), which delegates to `registerScalar` (line 746 → line 85) and so lands at `SettingsScope.WORLD` (the default on line 93) with `config = false`. Add `getSubsystemStore()` / `setSubsystemStore()` accessors modelled on `getLatestMigrationBackup()` / `setLatestMigrationBackup()` (lines 241-245).

*(Not `schemaVersion` — that one is `registerInt`, line 513, not a string setting.)*

The stored blob is:

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

**Decision:** the "usable without a kingdom" argument is decisive for a *generic subsystem* tool, so the store is **world-level**. The cost is two concrete extra pieces of work — a standalone launcher app (§4) and one change to the chat-button binder so offer cards fire without a kingdom actor (§5.3) — both small and called out in the phasing. The kingdom-flag alternative is documented above and in §10 Q1 (Open Questions) as the fallback if Gregory would rather trade Kingdom-less support for the free plumbing.

> **Caveat — world-scope means replicated, not secret.** `registerScalar` defaults to `SettingsScope.WORLD` (`Pfrpg2eKingdomCampingWeatherSettings.kt:93`) and Foundry pushes every world setting to every connected client, so `game.settings.get(...)` hands a player the raw store blob including unrevealed discovery rows. Hiding is therefore done at **context-build time** (§4.3) — unrevealed rows are never put into a player's render context — but a determined player can still read the blob from the browser console. This is exactly how existing GM-authored kingdom-flag data already behaves (players are OWNERs of the party actor) and is **accepted**. If true secrecy is ever required, the store has to move to a GM-owned `JournalEntry`; that is not v1.

> **Not the camping flag.** `CampingData` persists perfectly well — it is a durable actor flag carrying `regionSettings`, `restSettings`, `hexSizeInMiles` and `travelMoveToken` (`camping/CampingData.kt:149/154/161/169`, the last one explicitly *"Persisted so the choice survives the sheet re-rendering"*). It is the wrong **home**, not a lossy one: it hangs off a camping actor that is optional and party-scoped, it mixes that durable config with per-session state the GM's *Reset Activities* control wipes (`CampingSheet.kt:388` → `resetActivities()` at `CampingSheet.kt:878`), and social/exploration encounters have nothing to do with camping.

### 2.5 Migration

Propose **`Migration66`** *(placeholder — re-derive at implementation; see caveat)* (`internal val migrations = listOf(...)` in `src/jsMain/kotlin/at/posselt/pfrpg2e/migrations/Migrations.kt` currently ends at `Migration65()` on line 155, and `MigrationChainTest` asserts contiguity).

> ⚠️ **The number in this section is a placeholder and must be re-derived at implementation.**
> The chain now ends at **`Migration65`**. Since these plans were written, four of the reserved
> numbers have LANDED: 62 = downtime-projects, 63 = scheduled-pressure-engine,
> 64 = map-dynamism, 65 = loot-manifests. `Migration49` was never free (it sits inside the
> long-registered 17..61 range) and several unimplemented plans still name it. The next free
> number is **66**. Take the next contiguous number when this actually lands, and extend
> `MigrationChainTest`'s hardcoded range.


> Gregory sequences the real number at implementation time; if other cards land first, bump accordingly and keep the `listOf` contiguous.

Because the store is a **new** world setting whose registered default is the empty JSON object
`"{}"` (§2.4 — matching `latestMigrationBackup`, not a pre-shaped payload), no per-actor backfill is
required; the accessors treat an absent or `"{}"` value as "no encounters, no projects". `Migration66` therefore overrides **`migrateOther(game)`** (`migrations/migrations/Migration.kt:17`, an `open suspend fun` — not `migrateKingdom`) to defensively initialize the setting when the key is absent, and exists mainly to keep the schema version monotonic so downgrades are detected:

```kotlin
class Migration66 : Migration(66) {
    override suspend fun migrateOther(game: Game) {
        // getSubsystemStore/setSubsystemStore are the String accessors added in §2.4,
        // modelled on getLatestMigrationBackup/setLatestMigrationBackup.
        val settings = game.settings.pfrpg2eKingdomCampingWeather   // Pfrpg2eKingdomCampingWeatherSettings.kt:197
        val raw = settings.getSubsystemStore()
        if (raw.isBlank() || raw == "{}") {
            settings.setSubsystemStore(
                JSON.stringify(
                    RawSubsystemStore(
                        influenceEncounters = emptyArray(),
                        researchProjects = emptyArray(),
                    )
                )
            )
        }
    }
}
```

> **If Gregory picks the kingdom-flag alternative instead:** `Migration66` overrides `migrateKingdom` and adds nullable `influenceEncounters`/`researchProjects` arrays to `KingdomData`, mirroring `Migration48`'s null-guard style exactly.

---

## 3. Engine Design

### 3.1 Pure core (commonMain)

File: `src/commonMain/kotlin/at/posselt/pfrpg2e/data/kingdom/subsystems/SubsystemEngine.kt`
(sibling of the existing pure `FactionRelations.kt`). Pure, UI-free, unit-tested from `commonTest`. Operates on value types only — the jsMain adapter maps `Raw*` ⇆ these.

```kotlin
package at.posselt.pfrpg2e.data.kingdom.subsystems

import at.posselt.pfrpg2e.data.ValueEnum
import at.posselt.pfrpg2e.fromCamelCase
import at.posselt.pfrpg2e.toCamelCase

/**
 * The four degrees of success. Persisted as the camelCase `value` in
 * `RawSubsystemCheckEntry.outcome`; `fromString` returns null for anything else, which the
 * jsMain adapter logs and skips (§2.3). Mirrors `data/kingdom/FactionAttitude.kt:20-27`.
 */
enum class SubsystemOutcome : ValueEnum {
    CRITICAL_SUCCESS,
    SUCCESS,
    FAILURE,
    CRITICAL_FAILURE;

    companion object {
        fun fromString(value: String) = fromCamelCase<SubsystemOutcome>(value)
    }

    override val value: String
        get() = toCamelCase()
}

/** Points per degree of success. PF2e Influence default: +2 / +1 / 0 / -1 (§0.1). */
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

Research reuses the same primitives (`applyCheck`, `newlyCrossedThresholds`); RP is just another point pool with its own `PointRule` instance. The default it starts from is the **Influence** rule above, because the Research subsystem does not print one universal degrees-of-success table (§0.2) — so no separate function is needed, only a distinct `PointRule` if §10 Q3 lands as "editable per project".

### 3.2 Determinism / seeding

**None needed.** These runners record the *outcome* of checks the GM/players have already resolved at the table (manual entry, §4). The engine performs no random selection, so there is no RNG, no seed, and no preview/commit parity concern.

### 3.3 Tick surface

**Neither tick fires this feature.** Per the required split:

- `TurnTickingEngine.tick()` (monthly, End Turn — `src/jsMain/.../kingdom/TurnTickingEngine.kt:170`) is the kingdom economy tick. Influence/Research points are **not** kingdom-turn resources; they advance from PC check actions inside an encounter, on whatever cadence the table plays at.
- `DailyTickHooks` (daily, world clock — `src/jsMain/.../kingdom/DailyTickHooks.kt`) advances weather and companion travel. A Research project *may* have a real-world deadline the GM tracks, but v1 does **not** auto-decrement anything on the daily clock (see OUT-OF-SCOPE, §6.4).

The runners are **encounter-scoped**: state changes only when the GM records a check or clicks an offer button. This is stated explicitly so no future card wires a third tick.

### 3.4 Threshold-effect authoring

**Freeform text, v1. No automation of effects.** `RawSubsystemThreshold.effect` is a plain string the GM writes ("The duke reveals the traitor's name"; "You may now cast the ritual at +2"). Reaching a threshold emits an **offer** (§5); the "Grant" button posts that text to chat and/or opens an existing prefilled dialog (`AddQuest` / `AddModifier`) if the GM wants a durable reward. The engine never parses or applies the effect string.

---

## 4. UI

Because the store is world-level (§2.4), the runner is **not** a kingdom-sheet nav entry. `MainNavEntry` (`src/jsMain/.../kingdom/sheet/navigation/MainNavEntry.kt`) is per-kingdom-actor; adding an entry there would tie the tool to a kingdom actor, defeating §2.4. Instead:

### 4.1 Standalone "Subsystem Trackers" application

A single standalone `SimpleApp<SubsystemTrackersContext>` (base: `src/jsMain/kotlin/at/posselt/pfrpg2e/app/forms/SimpleApp.kt:16`; shape reference `kingdom/dialogs/ArmyBrowser.kt:70`) with two panes: **Influence** and **Research**. It reads/writes the world setting directly.

```kotlin
private class SubsystemTrackers(
    private val game: Game,
) : SimpleApp<SubsystemTrackersContext>(
    title = t("subsystems.trackersTitle"),
    template = "applications/subsystems/tracker.hbs",
    classes = setOf("km-scroll-application"),
    id = "kmSubsystemTrackers",
) {
    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "add-influence", "add-research", "edit", "delete",
            "record-check", "toggle-reveal", "import" -> { /* … */ }
        }
    }
}
```

> **Why not `CrudApplication` — §4.1 and §4.3 cannot both stand on it.** `CrudApplication` (`app/CrudApplication.kt:69-82`) passes `template = "components/forms/crud-form.hbs"` (line 76) up into `FormApp<CrudTemplateContext, CrudData>` (line 74). `template` is a **by-value constructor parameter** of `FormApp` (`app/FormApp.kt:40`), consumed exactly once at `FormApp.kt:87` as `template = resolveTemplatePath(template)` — it is not an open member, so no subclass can substitute `tracker.hbs`. The render context is pinned to `CrudTemplateContext` for the same reason, and §4.3's `InfluenceEncounterContext` is not one. Since the three custom templates are the point of §4.3, the base is `SimpleApp`.
>
> The action vocabulary is **not** an obstacle either way: `_onClickAction` is `protected open` on `com/foundryvtt/core/applications/api/ApplicationV2.kt:80`, `CrudApplication` merely overrides it at line 83, and `ArmyBrowser` — a `SimpleApp` — overrides it at `ArmyBrowser.kt:81`.
>
> If a flat-list CRUD shell is ever preferred over the custom dashboards, the real `CrudApplication` exemplars are `kingdom/dialogs/ActivityManagement.kt:20` and `kingdom/dialogs/DeadlinesDialog.kt:26`. **Not** `ArmyBrowser` (a `SimpleApp`, `ArmyBrowser.kt:75`) and **not** `StructureBrowser` (a plain `FormApp<StructureBrowserContext, StructureBrowserData>`, `StructureBrowser.kt:221`) — neither derives from `CrudApplication`.

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
- **Round bookkeeping (advisory).** RAW gives each PC one Influence *or* Discovery action per influence round (§0.1). Recording a check sets that participant's `actedThisRound`; the row then renders greyed with an "acted" marker, and a **[New round]** button clears the flag for everyone. **Nothing is blocked** — the GM can record a second check for the same PC and the tool will take it. Whether to harden this into enforcement is §10 Q5.

> **Auto-roll is a deliberate v2 deferral.** The check pipeline (`KingdomCheckDialog.kt` / `KingdomRoll.kt`) could roll a PC's skill against the row DC and infer the degree of success automatically. v1 stays **manual-first** so the tool is useful immediately and never fights the GM's at-table adjudication.

### 4.3 Templates, contexts, i18n

**Templates** (`src/jsMain/resources/applications/subsystems/`):
- `tracker.hbs` — the two-pane shell. This is the app's own `template` (rendered as `SimpleApp`'s single `"div"` part, `app/forms/SimpleApp.kt:41-47`), so it needs a **single root element** and is *not* registered as a partial.
- `influence-encounter.hbs` — one encounter's dashboard (pool bar, participants, check log, threshold list).
- `research-project.hbs` — one project's dashboard (RP bar, checks, threshold list).

The latter two are **registered partials**, included from `tracker.hbs`. Register them by **name** in `Main.kt loadTemplatePartials` (`Main.kt:134`, alongside `"kingdom-events" to "applications/kingdom/events.hbs"` on line 137) and reference them by name, per the "Handlebars partials by name" convention:

```kotlin
"subsystem-influence-encounter" to "applications/subsystems/influence-encounter.hbs",
"subsystem-research-project"    to "applications/subsystems/research-project.hbs",
```

> **Partial scope rule for every GM/reveal gate (CI-enforced).** Both partials render GM-only and reveal-gated rows *inside* `{{#each}}` loops (participants, thresholds, check-log rows, discovery rows) — precisely the shape that has already shipped dead GM buttons in this repo.
> - **Never a bare `{{#if isGM}}` inside `{{#each}}`.** The `each` frame shadows it and a bare lookup does not climb out of a partial. `scripts/check_hbs_scope.py` **cannot** catch this form (it only flags `../` chains deeper than the file's own block depth), so it has to be caught in review.
> - **Never a top-level `{{#if ../isGM}}` in a partial.** Per that script's docstring, with Handlebars 4.7.9 a partial gets no frame above its own context — `[bare:N parent:N root:Y]` — and passing an explicit context does not help.
> - **Do** use `{{#if @root.isGM}}` / `{{#unless @root.isGM}}hidden{{/unless}}`, or pass the flag in explicitly at the include site and read it one `{{#each}}` deep, the way `sections/turn/page.hbs:336` does (`{{> kingdom-events ongoingEvents=ongoingEvents isGM=isGM}}`, read back as `{{#unless ../isGM}}hidden{{/unless}}` at `events.hbs:34`).
>
> Note that the gate is cosmetic either way — authorization is the **context-level** filtering below, and the raw store is world-replicated regardless (§2.4 caveat).

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
// + SubsystemTrackersContext (the §4.1 app's own two-pane context: influence + research
//   summary rows, isGM), ResearchProjectContext, SubsystemParticipantContext,
//   SubsystemCheckContext, SubsystemThresholdContext
```

**i18n namespace:** nested objects under the `"pf2e-kingmaker-tools"` root of **every** file in `lang/` — all 8 of `de.json`, `en.json`, `fr.json`, `it.json`, `pl.json`, `pt-BR.json`, `ru.json`, `zh-Hans.json` (i18next nested-path lookup — flat-dotted keys render raw). New keys under **`subsystems.influence.*`**, **`subsystems.research.*`** and **`subsystems.outcome.*`** (labels, degree-of-success button text, dialog legends, offer-card text). No new `translate*()` helper is needed in `initLocalization()` — these are static UI strings resolved through `t()` / the `localizeKM` Handlebars helper, unlike the data-driven `translate*` catalogs (events, activities, …).

> **en-only is a CI failure, not a TODO.** `scripts/check_i18n_keys.py` check 5 (`--parity`) fails on any key present in `en.json` and missing from the other seven, and `.github/workflows/test.yml:27` runs it as `--all`. Adding a key to `en.json` alone turns the build red. Translate, or carry the English string across all eight — but land all eight in the same commit.

**Localize outcome and status labels through a literal `when`, not an interpolated key.** The context builder maps the persisted discriminators to literal keys:

```kotlin
fun outcomeLabel(outcome: SubsystemOutcome?): String = when (outcome) {
    SubsystemOutcome.CRITICAL_SUCCESS -> t("subsystems.outcome.criticalSuccess")
    SubsystemOutcome.SUCCESS          -> t("subsystems.outcome.success")
    SubsystemOutcome.FAILURE          -> t("subsystems.outcome.failure")
    SubsystemOutcome.CRITICAL_FAILURE -> t("subsystems.outcome.criticalFailure")
    null                              -> t("subsystems.outcome.unknown")
}
```

`t("subsystems.outcome.$outcome")` would work at runtime but is invisible to `check_i18n_keys.py` check 2 (which scans literal `t("…")` / `{{localizeKM "…"}}` refs), so a typo'd or missing key would ship with every guard green. Same treatment for `status` (`subsystems.status.active` / `subsystems.status.resolved`). This follows the note at `kingdom/pressure/PressureDigest.kt:68`.

---

## 5. Chat / Offer Surfaces (GM-Confirmed Only)

Every mechanical benefit is a whispered **GM-confirmed offer**. Two new `km-offer-…` handlers are added to `src/jsMain/.../kingdom/ChatButtons.kt`, following the existing `km-offer-diplomacy-quest` (line 1011) / `km-offer-war-threat` shape — but registered in the **new actor-independent world-button list introduced in §5.3**, *not* the existing `buttons` list (line 99). That list's entries take a non-null `KingdomActor` (`ChatButtons.kt:84-87`) and every one of them is gated behind `parent.findKingdomActor(game)` in `bindChatButtons` (`ChatButtons.kt:1433`, warning `t("kingdom.chatButtonNoKingdom")` at line 1437), so a world-scoped offer put there would silently never fire in the Kingdom-less campaigns §2.4 exists to support.

### 5.1 Offer cards

| Trigger | Offer card (whispered to GMs) | Buttons | Handler |
|---|---|---|---|
| Influence pool crosses a threshold (via `newlyCrossedThresholds`) | `chatmessages/influence-threshold-offer.hbs` | **[Grant]** · **[Convert to Quest]**¹ · **[Dismiss]** | `WorldChatButton("km-offer-influence-threshold")` (§5.3) |
| Research RP crosses a threshold | `chatmessages/research-threshold-offer.hbs` | **[Grant]** · **[Convert to Quest]**¹ · **[Dismiss]** | `WorldChatButton("km-offer-research-threshold")` (§5.3) |

¹ Conditional — see the **Convert to Quest** bullet below.

**Button semantics (no effect automation, §3.4):**
- **Grant** — posts the threshold's freeform `effect` text publicly and sets `offerConsumed = true` on that threshold (idempotency guard, mirroring `RawWarThreat.offerConsumed`). Clicking a consumed threshold is a no-op.
- **Convert to Quest** *(conditional)* — opens `AddQuest` (`kingdom/dialogs/AddQuest.kt:106`, `prefillTitle` / `prefillGiver` / `settlements`) prefilled with the effect text, reusing the exact pattern `km-offer-diplomacy-quest` uses today (`ChatButtons.kt:1011-1027`). `AddModifier` is an analogous alternative if the benefit is a kingdom modifier.
  **A quest has nowhere to go without a kingdom.** That existing handler persists via `actor.getKingdom()?.let { kingdom -> kingdom.quests = … }` — a null-safe **no-op** when there is no kingdom, i.e. a Save button that silently discards the quest. So this button is rendered **only when `game.getKingdomActors().isNotEmpty()`** (`kingdom/KingdomData.kt:745`, which already filters to actors whose `getKingdom() != null`); in a Kingdom-less world the offer card ships **[Grant] · [Dismiss]** only. Because card HTML is authored once and read by every client, the handler re-checks: it resolves the target itself with `game.getKingdomActors().firstOrNull()` and, if that is null, aborts with `ui.notifications.warn(t("kingdom.chatButtonNoKingdom"))` instead of opening the dialog. With more than one kingdom actor in the world it prompts for which one — the same disambiguation the launcher already needs.
  *(Note the world-button list of §5.3 deliberately has no `findKingdomActor` gate; that DOM-scoped helper — `kingdom/ContextMenus.kt:19`, an `Element` extension reading `[data-kingdom-actor-uuid]` — has nothing to read on a world-scoped card. Resolving from `game` is the right call here.)*
- **Dismiss** — sets `offerConsumed = true` without posting, so the card can't re-fire.

`data-*` payload on each button: `data-store="influence|research"`, `data-entry-id` (encounter/project id), `data-threshold-points`. The button reads/writes the **world setting**, not a kingdom actor.

### 5.2 Where offers are emitted

Offers are emitted at **check-record time** in the runner (§4.2), not on any tick — the record handler compares `previous` vs `next` pool via `newlyCrossedThresholds` and posts one card per newly crossed threshold. Resistances/weaknesses are already folded into the delta before the comparison, so a threshold can only be offered when the *applied* points actually reach it.

### 5.3 Required plumbing change (world-level offers without a kingdom actor)

`bindChatButtons(game)` (`ChatButtons.kt:1427`) routes every button through `parent.findKingdomActor(game)`, warning and returning when it is null (lines 1433-1437), so a button whose card has no owning kingdom actor **never fires**. Because these two offers are world-scoped, Phase 4 adds a second, actor-independent list bound in the same hook:

```kotlin
private data class WorldChatButton(
    val buttonClass: String,
    val callback: suspend (game: Game, event: Event, button: HTMLElement) -> Unit,
)

private val worldButtons = listOf(
    WorldChatButton("km-offer-influence-threshold") { game, event, button -> /* … */ },
    WorldChatButton("km-offer-research-threshold") { game, event, button -> /* … */ },
)
```

bound by a second `worldButtons.forEach { … }` loop inside the existing `TypedHooks.onRenderChatLog` block in `bindChatButtons` (`ChatButtons.kt:1429`) — **no `findKingdomActor` gate**, same `bindChatClick(".${data.buttonClass}")` + `buildPromise { … }.catch { e -> console.error(…); ui.notifications.error(t("kingdom.chatButtonFailed")) }` wrapper as the existing loop (lines 1430-1447), so a throw inside a world handler is still surfaced rather than becoming an unhandled rejection. The existing `ChatButton` type and `buttons` list are left exactly as they are. This is the concrete cost of the world-level store called out in §2.4; the kingdom-flag alternative would not need it.

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
| GM-confirmed offer pattern | `kingdom/ChatButtons.kt` | Add 2 `WorldChatButton` handlers + the actor-independent `worldButtons` loop in `bindChatButtons` (§5.3). |
| Dialog stack | `app/forms/SimpleApp.kt` (tracker shell; shape reference `kingdom/dialogs/ArmyBrowser.kt:70`), `app/FormApp.kt`, `kingdom/dialogs/AddWarThreat.kt` (add/edit shape reference) | Standalone `SubsystemTrackers` `SimpleApp` (§4.1) + new `AddInfluenceEncounter` / `AddResearchProject` `FormApp`s. **Not** `CrudApplication` — see §4.1. |
| Quest/modifier conversion | `kingdom/dialogs/AddQuest.kt`, `kingdom/dialogs/AddModifier.kt` | "Convert to Quest" offer button reuses the prefill pattern. |
| World-setting store | `settings/Pfrpg2eKingdomCampingWeatherSettings.kt` | Register + get/set the `RawSubsystemStore` JSON (same pattern as `latestMigrationBackup`). |
| Migration chain | `migrations/Migrations.kt`, `migrations/migrations/Migration.kt` | Register `Migration66` (via `migrateOther`) at the end of `migrations` (currently `Migration65()`, `Migrations.kt:155`); `MigrationChainTest` guards contiguity. |
| Localization | `utils/Localization.kt`, `lang/*.json` (all 8: `de`, `en`, `fr`, `it`, `pl`, `pt-BR`, `ru`, `zh-Hans`) | Nested `subsystems.*` keys in **every** file — `check_i18n_keys.py --all` parity is a CI gate (`.github/workflows/test.yml:27`). No new `translate*()` helper. |
| Party-actor read | `kingdom/sheet/contexts/PartyInfluenceContext.kt` (`PartyMemberRef`) | Participant picker reads live party membership. |

### 6.3 The one kingdom-event integration hook (v1)

`docs/house-rules.md` line 130: *"Prepare some Kingdom Events as quests … Use the research subsystem to let them figure out that the source was a druid …"*.

v1 ships exactly **one** module-specific integration: a **"Run as Research project"** button on the **ongoing-event card on the kingdom sheet's Turn tab**. Clicking it seeds a `RawResearchProject` and opens the Research runner. This is the concrete reason the feature belongs in *this* module rather than a generic PF2e subsystem module.

**The seam (three files):**

| File | Change |
|---|---|
| `src/jsMain/resources/applications/kingdom/events.hbs` | Add a `<button data-action="run-as-research" data-index="{{@index}}" {{#unless ../isGM}}hidden{{/unless}} type="button">` inside the existing `{{#each ongoingEvents}}` block (line 2), next to the `handle-event` / `delete-event` buttons (lines 28-39). `data-index` + `../isGM` match what those buttons already do (`events.hbs:33`, `:34`) — the partial is included with `isGM` passed explicitly at `sections/turn/page.hbs:336`, so `../` is in scope one `each` deep here. |
| `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/sheet/contexts/OngoingEventContext.kt` | Expose whatever the button needs on `OngoingEventContext` (line 23), built by `suspend fun List<OngoingEvent>.toContext(openedDetails, isGM, settlements)` (line 46). Note `OngoingEventContext.id` is the composite `"${event.id}-$index"`, which is why the button keys off `@index`, not `id`. |
| `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/sheet/KingdomSheet.kt` | Handle `"run-as-research"` in `_onClickAction` (line 544), resolving the event exactly like `"handle-event"` does (line 2072): `kingdom.getOngoingEvents().getOrNull(target.dataset["index"]!!.toInt())`. |

**Seeding `name` / `description` — not from `RawOngoingKingdomEvent`.** That interface (`kingdom/KingdomEvent.kt:86`) carries **only** `stage`, `id`, `settlementSceneId`, `secretLocation`, `becameContinuous`, `hireAdventurersFailures`, `hireAdventurersBlocked`. There is no `name` and no `description` on it; both live on the **catalog** entry (`RawKingdomEvent`, `KingdomEvent.kt:33-35`).

The resolution already happens for free in the accessor the handler uses anyway: `KingdomData.getOngoingEvents()` (`KingdomEvent.kt:117`) joins each ongoing entry to the catalog and returns `OngoingEvent(event: KingdomEvent, …)` (`KingdomEvent.kt:106`), where the parsed `KingdomEvent` (`src/commonMain/.../data/events/KingdomEvent.kt:6-9`) has `id`, `name` and `description`:

```kotlin
val ongoing = kingdom.getOngoingEvents().getOrNull(index) ?: return@buildPromise
RawResearchProject(
    name = ongoing.event.name,
    description = ongoing.event.description,
    sourceEventId = ongoing.event.id,
    // …
)
```

No second lookup is needed. (`kingdom.getEvent(id)` — `KingdomEvent.kt:183`, over `getEvents()` at `:173` — is the right call only when all you hold is an id, e.g. re-opening a project by its `sourceEventId`.)

**These values are already localized, and must be.** `getEvents()` returns homebrew events plus `translatedKingdomEvents`, the catalog after `translateKingdomEvents()` (`KingdomEvent.kt:162`, wired from `initLocalization()` at `utils/Localization.kt:148`) has mapped `name` through `t(it.name)` and the descriptions through `translate()`. The values in the shipped `events.json` are i18n **keys**, so seed from the resolved `OngoingEvent` above — never from the raw catalog JSON — or the project name renders as a raw key.

*(Not `kingdom/dialogs/KingdomEventManagement.kt`: that is a `CrudApplication` over `kingdom.homebrewKingdomEvents` — a homebrew **definition** manager with no ongoing-event card. Not `applications/kingdom/event-browser.hbs` either: that is `AddEvent.kt:118`'s pick-an-event browser.)*

### 6.4 Explicit OUT-OF-SCOPE

- **No auto-roll.** Manual degree-of-success entry only in v1 (auto-roll via `KingdomCheckDialog`/`KingdomRoll` is v2).
- **No effect automation.** Thresholds are freeform text; nothing is applied mechanically.
- **No tick integration.** Neither monthly nor daily; no auto-deadline on research.
- **No shipped copyrighted content.** Empty runners + a documented JSON schema only.
- **No companion-influence merge.** The two systems stay separate data + storage (§6.1).
- **No per-PC "victory point" scoring / initiative order** — the runner records points, not turn order.
- **No DC derivation.** Neither the level-based DC table nor library-level DC bands (§0.2) are implemented; every DC is typed by the GM.
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
| `outcomeFromString_roundTrips` | `SubsystemOutcome.fromString(o.value) == o` for all four entries (`criticalSuccess`, `success`, `failure`, `criticalFailure`). |
| `outcomeFromString_unknownReturnsNull` | `fromString("nonsense")` / `fromString("")` return **null** rather than throwing — the contract §2.3's log-and-skip adapter relies on. |

### 7.2 jsTest — Foundry-integrated (`src/jsTest/kotlin/…`)

**Files:** `SubsystemStoreTest.kt`, `SubsystemContextTest.kt`, `Migration66Test.kt`.

| Test | Description |
|---|---|
| `store_roundTripsThroughWorldSetting` | `RawSubsystemStore` serialize → setting → deserialize is lossless. |
| `context_filtersHiddenRowsForPlayers` | `isGM=false` hides unrevealed discoveries/thresholds; `isGM=true` shows all. |
| `context_poolPercentRelativeToTopThreshold` | Bar percent maths against the highest threshold. |
| `recordCheck_emitsOfferOnlyOnCrossing` | Recording a success that crosses a threshold posts exactly one offer; a non-crossing success posts none. |
| `offer_grantMarksThresholdConsumed` | Grant sets `offerConsumed`; a second click is a no-op. |
| `offer_firesWithoutKingdomActor` | The actor-independent binder (§5.3) invokes the handler in a world with no `KingdomActor`. |
| `Migration66_initializesEmptyStore` | `migrateOther` writes the default blob when the key is absent; leaves an existing store untouched. |
| `Migration66Test_chainContiguous` | (Covered by existing `MigrationChainTest`.) `MigrationChainTest.kt:24` currently asserts `(17..65)`; extend it to `(17..66)` when this lands. Its method name, `registeredVersionsAreContiguous17To61` (line 23), is *already* stale against the 65 in the assertion — rename it while you are in there. |

Run the same gates CI runs (`.github/workflows/test.yml`), not a subset — the bare `check_i18n_keys.py` skips the cross-language parity check that would fail the build:

```
python3 scripts/check_i18n_keys.py --all     # test.yml:27 — checks 1-5, incl. lang/*.json parity
python3 scripts/check_hbs_scope.py           # test.yml:36 — Handlebars parent-scope guard (§4.3)
JAVA_HOME=<jdk25> ./gradlew assemble jsTest -x kotlinStoreYarnLock   # Chrome headless: useChromeHeadless + CHROME_BIN
```

### 7.3 Manual Foundry verification checklist

**Part A — in a world with NO kingdom actor** (this is what §2.4's world-level store buys, so it is verified first):

1. Open the Subsystem Trackers app via macro/API — it opens and shows two empty panes.
2. **Add Influence Encounter** "Restov Banquet" (NPC "Duke Sellemius", 1 discovery DC 18, 2 influence skills, thresholds at 5/10, one resistance "flattery" −1, one weakness "history" +1). Save → appears in the Influence pane.
3. Open it, add two party PCs as participants.
4. Record a **Success** on the "history" (weakness) skill for PC A → pool goes to +2, PC A shows 2, a check-log row appears.
5. Record checks until the pool crosses **5** → a whispered offer card appears **even with no kingdom actor in the world** (§5.3's `worldButtons` binder). The card shows **[Grant] · [Dismiss]** and **no [Convert to Quest]** button (§5.1). Click **Grant** → effect text posts publicly; threshold shows consumed; re-clicking does nothing.
6. Toggle **reveal** on the discovery row and set the encounter **visibleToPlayers** → log in as a player: the revealed row + pool bar are visible, unrevealed rows are not.
7. Import a JSON stat block (§9) via the app's **Import** action → the encounter/project is created with all rows. Re-import a file with one bad `"outcome"` string in a `checkLog` → the app still opens, that row is dropped, a `console.warn` names the bad value (§2.3).
8. **Reload the world** → all encounters, projects, points, participants, logs, consumed/reveal flags persist.

**Part B — in a world that DOES have a kingdom actor** (the offer/quest and event-hook paths):

9. Open the tracker, **Add Research Project**, and repeat the RP-threshold → offer → Grant flow. This card **does** show **[Convert to Quest]**; click it → `AddQuest` opens prefilled → Save → the quest appears in the kingdom **Quests** tab.
10. On the kingdom sheet's **Turn** tab, on an ongoing-event card, click **Run as Research project** → a Research project is seeded with that event's **localized** name/description and `sourceEventId` (§6.3), and the Research runner opens.
11. Log in as a player and confirm the **Run as Research project** button is not rendered (`../isGM` gate, §6.3), then confirm the Turn tab still renders normally for them.

---

## 8. Phasing (independently committable, one worker card each)

| Phase | Title | Deliverable | Key files |
|---|---|---|---|
| **1** | **Data model + world store + Migration66** | `Raw*` interfaces, `RawSubsystemStore`, world-setting register + get/set, JSON import schema doc, `Migration66` (`migrateOther` initializer), registration in `Migrations.kt`, `MigrationChainTest` range bumped to `(17..66)`. | `data/RawInfluenceEncounter.kt`, `data/RawResearchProject.kt`, `data/RawSubsystem.kt`, `settings/Pfrpg2eKingdomCampingWeatherSettings.kt`, `migrations/migrations/Migration66.kt`, `migrations/Migrations.kt`, `jsTest/.../MigrationChainTest.kt` |
| **2** | **Pure engine (commonMain) + tests** | `SubsystemEngine.kt` (all pure signatures, `PointRule`, trait math, threshold-crossing) + `SubsystemEngineTest.kt`. | `data/kingdom/subsystems/SubsystemEngine.kt`, `commonTest/.../SubsystemEngineTest.kt` |
| **3** | **Runner UI — tracker app, dialogs, contexts, i18n** | Standalone `SubsystemTrackers` `SimpleApp` + two panes (§4.1), `AddInfluenceEncounter`/`AddResearchProject` `FormApp`s, manual-entry degree-of-success buttons, reveal toggles, `SubsystemContext.kt` + pure builders, templates (`@root`/hash-arg gates per §4.3), `subsystems.*` i18n **in all 8 `lang/` files**, macro/API launcher + Turn-tab convenience button. | `kingdom/dialogs/SubsystemTrackers.kt`, `kingdom/dialogs/AddInfluenceEncounter.kt`, `AddResearchProject.kt`, `kingdom/sheet/contexts/SubsystemContext.kt`, `resources/applications/subsystems/*.hbs`, `lang/*.json` (de, en, fr, it, pl, pt-BR, ru, zh-Hans), `Main.kt` (partials + launcher) |
| **4** | **Offer cards + event hook + import + QA** | 2 `WorldChatButton` `km-offer-*` handlers + the actor-independent `worldButtons` loop (§5.3), offer templates, "Run as Research project" hook on the ongoing-event card (§6.3), JSON import action, `SubsystemContextTest`/`SubsystemStoreTest`/`Migration66Test` (jsTest) + manual checklist. | `kingdom/ChatButtons.kt`, `resources/chatmessages/influence-threshold-offer.hbs`, `research-threshold-offer.hbs`, `resources/applications/kingdom/events.hbs`, `kingdom/sheet/contexts/OngoingEventContext.kt`, `kingdom/sheet/KingdomSheet.kt`, `jsTest/.../*` |

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
5. **Per-PC round gating.** Track `actedThisRound` as advisory (plan) or enforce one influence action per PC per round? (§0.1 — RAW gives each PC one action per influence round.)
6. **Resistances/weaknesses: point delta or DC shift?** RAW prints them as adjustments to the influence **DC**; the plan models them as adjustments to the *points awarded*, because manual entry happens after the roll (§0.3). Keep the point delta (plan), or re-cast `RawInfluenceTrait.delta` as an advisory DC shift displayed next to the skill row — which would delete `adjustForTraits` and its three tests from §3.1/§7.1?

---

**End of Plan.** Ready for review. On approval, create the four phase cards per §8.
