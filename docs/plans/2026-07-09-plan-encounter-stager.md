# Encounter Stager — Implementation Plan

> **Status:** Plan only — no implementation yet
> **Date:** 2026-07-09
> **Roadmap item:** New backlog (Encounter Stager, built on the Random Encounter & Rumor Curator, roadmap #11)
> **Depends on:** Random Encounter & Rumor Curator (#11, landed — `EncounterPreviewDialog`, `EncounterResolverEngine`, `rollCuratedEncounter`), War-Threat pending-encounter queue (`km-offer-war-threat-arrival`, commit `d68c1fad`), Session Prep pending-encounter rows (commit `f3185fed`), Combat Track manager (`combat/CombatTracks.kt`)
> **Branch:** `kingmaker.5`

---

## Executive Summary

The Random Encounter & Rumor Curator already does the *decision* work: it picks an
encounter category, rolls the region's curated table, and — for the party's watch — the
`EncounterResolverEngine` computes a **start distance** (15 / 60 / 120 ft) and **awareness
state** (ambusher Hidden vs. Revealed; sleepers unconscious/prone) from the watcher's
Perception roll. The GM previews all of this in `EncounterPreviewDialog` and clicks *Accept*.

Then the automation stops and the GM does the fiddly part by hand: open the bestiary /
compendium, drag the right creatures onto the scene, apply *Elite* / *Weak* to hit the XP
budget, place tokens roughly at the computed distance, add them to the tracker, roll
initiative, and start the combat music track. That is 2–4 minutes of clicking, mid-session,
every random encounter.

**This feature adds a "Stage Encounter" button** — on `EncounterPreviewDialog` and on the
Session Prep *pending-encounter* rows — that performs the mechanical build: spawn the
encounter's creatures as tokens, place them in a ring at the engine's start distance around
the party token, create/activate the combat encounter, add the spawned tokens as combatants,
and roll initiative. The existing combat-track hook plays the music for free. Everything is
GM-side, previewable before it fires, and undoable (delete the spawned tokens + combat).

**The load-bearing audit finding** (see §2) is that curated encounters carry **no creature
references today** — the curator reads only the drawn table result's `text` string. Making
staging possible is therefore *primarily a data problem*: we must attach a **creature
manifest** (actor UUIDs + counts) to an encounter before we can spawn anything.

---

## 1. Problem Statement + Player/GM Value

**Problem:** The curator computes *where* and *how aware* the enemies are, but the GM still
hand-assembles the fight. Between the *Accept* click and initiative there is a manual
compendium-drag / template-apply / token-place / start-combat sequence that breaks table
flow and is easy to get wrong (wrong count, forgot Elite, tokens placed at the wrong range so
the "120 ft, no surprise" ruling silently becomes a 20-ft knife fight).

**Value to the table:**

- **GM prep/latency reduction:** one click turns an accepted encounter into a ready-to-run,
  correctly-ranged combat with initiative rolled and music playing. The 2–4 minute manual
  build collapses to seconds.
- **Faithful to the curator's ruling:** tokens are actually placed at the distance the
  `EncounterResolverEngine` computed, so the ambush/awareness fiction the module already
  narrates matches the board state. This is the module's own house rule (`docs/house-rules.md`
  → Random Encounters: "the watcher performs a Perception check against the ambusher's
  Stealth DC") made mechanical.
- **Fewer mistakes:** creature count and (in v2) XP-budget adjustment come from data, not GM
  memory under time pressure.
- **Player-invisible:** this is a GM convenience; players just see the fight appear correctly.
  Nothing is auto-applied to *player* characters — the stager only spawns *enemy* tokens.

---

## 2. Data Model

### 2.1 Audit — what an encounter carries for creature references TODAY

This determines the whole design, so it is stated precisely.

| Layer | File | What it carries | Creature ref? |
|-------|------|-----------------|---------------|
| Curated roll | `camping/RandomEncounters.kt` `rollCuratedEncounter()` | Draws region table; reads **`draw.results.get(0)?.text?.trim()`** → a plain `String` | **No** — only text |
| Preview | `camping/EncounterPreviewDialog.kt` | `resultText: String`, `category`, `regionName`, optional `Rumor` (text) | **No** |
| Resolver | `camping/EncounterResolverEngine.kt` | distance + conditions + ambusher state from the watch roll | **No** |
| Persisted value models | `camping/EncounterCuratorData.kt` | `RawCategoryWeights`, `RawMerchantStock` (item names/prices + merchant `uuid`), `RawRumor` (text + quest template ids) | **No creature refs** |
| Pending encounter | `kingdom/data/RawHexContent.kt` | `pendingEncounter: Boolean?`, `linkedWarThreatId`, `linkedUuids: Array<String>?` ("referenced Foundry documents (journals/actors/scenes/items)") | **Loose, unstructured** — a hex *may* link actor UUIDs, but there is no "N of creature X" model |

**Crucial nuance:** Foundry's `TableResult` (`com/foundryvtt/core/documents/TableResult.kt`)
*does* expose `type`, `documentCollection`, and `documentId`. A roll-table result whose
`type` is `"document"` or `"pack"` therefore points at a real actor. **The curator code path
ignores these fields entirely and reads only `.text`.** So the reference sometimes physically
exists on the drawn result but is discarded before it reaches the preview.

**Conclusion — what must be ADDED:** an explicit, spawnable **creature manifest**. Two
ingestion sources, in priority order:

1. **From the table result** — when the drawn `TableResult.type` is `document`/`pack`, read
   `documentCollection` + `documentId` (or resolve to a full UUID) into the manifest
   automatically. This is zero-authoring for tables that already point at bestiary actors.
2. **GM-authored / editable** — a manifest the GM curates in the Stage dialog (add actor by
   drag-drop UUID, set count, optional per-entry adjustment). For text-only results (most of
   the shipped tables), this is the only source; the *Stage* button is disabled until the GM
   supplies at least one creature.

### 2.2 New external interfaces (all `@JsPlainObject`, nullable for migration safety)

`RawEncounterManifest.kt` — lives in `src/jsMain/kotlin/.../camping/` (co-located with the
curator value models it extends):

```kotlin
@JsPlainObject
external interface RawEncounterCreature {
    var uuid: String            // full document UUID (world Actor or Compendium.<pack>.Actor.<id>)
    var count: Int              // how many tokens to spawn (>= 1)
    var adjustment: String?     // null | "elite" | "weak"  (v2 — recorded now, applied later)
    var displayName: String?    // cached label for the dialog list (resolved lazily otherwise)
}

@JsPlainObject
external interface RawEncounterManifest {
    var creatures: Array<RawEncounterCreature>
    var startDistanceFt: Int?   // override; null => use EncounterResolverEngine's distance
    var xpBudgetNote: String?   // optional GM note (e.g. "moderate for L5x4")
}
```

### 2.3 Persistence

Staging is action-shaped, not state-shaped, so the persistence footprint is deliberately small
and follows the roadmap-#11 "nullable + guarded reads, no data-touching migration" doctrine
(`EncounterCuratorData.kt` header comment).

| Path | Where the manifest lives | Persistence |
|------|--------------------------|-------------|
| **Ephemeral preview** (`EncounterPreviewDialog` right after a roll) | Held in the dialog instance in memory; auto-seeded from the table result's document ref | **None** — nothing new persisted |
| **Pending-encounter hex** (Session Prep row, queued via `km-offer-war-threat-arrival`) | New nullable field on `RawHexContent`: `var encounterManifest: RawEncounterManifest?` | **Kingdom actor flag** (`RawHexContent` already lives under `KingdomData.hexContents`) |

No camping flag and no world setting are introduced. The pending-encounter manifest is the
only durable new schema.

### 2.4 Migration

`RawHexContent.encounterManifest` is nullable; a null manifest simply means "no creatures
curated yet — Stage button disabled". Existing saves load unchanged, exactly like the
`pendingEncounter`/`linkedUuids` fields added earlier. Reads go through a guarded accessor
(`RawHexContent.encounterManifestOrNull()`), matching `EncounterCuratorData.kt`'s `*OrDefault`
pattern.

- **v1 core (spawn from ephemeral preview only): no migration needed** — nothing persists.
- **Pending-encounter manifest (Phase 3): `Migration49`** — no-op backfill that leaves
  `encounterManifest` null for existing hexes. Registered for chain contiguity only.

> The migration chain currently ends at **`Migration61`** (`migrations/Migrations.kt`,
> `internal val migrations = listOf(...)`; `MigrationChainTest` asserts contiguity). This plan
> proposes **`Migration49`**; **Gregory sequences the real number at implementation time** in
> case other in-flight work claims 49 first.

> ⚠️ **The number in this section is stale and must be re-derived at implementation.** The chain
> ends at `Migration61`, not 48, and 62–67 are already proposed by the downtime-projects,
> scheduled-pressure, petition-inbox, npc-memory, seasonal-economy and loot-manifests plans. Nine
> unimplemented plans currently name `Migration49`, so it is not free for any of them. Take the
> next contiguous number when this actually lands, and update `MigrationChainTest`.


---

## 3. Engine Design

### 3.1 Pure core (commonMain) — placement + budget math

The geometry and XP math are pure and belong in `commonMain` with `commonTest` coverage
(mirroring `EncounterHexFilter.kt` / `CategoryWeights.kt`, which already live in
`src/commonMain/kotlin/.../camping/`). Foundry's `com.pixijs.Point` is a JS type, so the pure
layer uses its own value point and the jsMain wiring converts.

File: `src/commonMain/kotlin/at/posselt/pfrpg2e/camping/EncounterStaging.kt`

```kotlin
package at.posselt.pfrpg2e.camping

/** UI-free 2D point in scene pixel space (converted to com.pixijs.Point in jsMain). */
data class StagePoint(val x: Double, val y: Double)

/**
 * Places `count` tokens evenly around a circle of radius `radiusPx` centred on `center`,
 * starting at `startAngleRad` (default: away from party facing / straight up). Deterministic:
 * no randomness, so preview and commit agree. Returns top-left token origins already offset
 * by half a grid cell so tokens are centred on the ring, not hung off it.
 */
fun ringPlacement(
    center: StagePoint,
    radiusPx: Double,
    count: Int,
    gridSizePx: Double,
    startAngleRad: Double = -kotlin.math.PI / 2,
): List<StagePoint>

/** Feet (engine distance) -> scene pixels: distanceFt / gridDistanceFt * gridSizePx. */
fun feetToPixels(distanceFt: Double, gridDistanceFt: Double, gridSizePx: Double): Double

/**
 * Total spawn count across the manifest (sum of per-creature counts). Used to size the ring
 * and to gate the Stage button (0 => disabled).
 */
fun manifestSpawnCount(creatures: List<StageCreature>): Int

/** Pure mirror of RawEncounterCreature for commonMain math/tests. */
data class StageCreature(val uuid: String, val count: Int, val adjustment: String?)

/**
 * v2 helper (recorded now, wired later): given a party (levels) and a base creature level,
 * report the XP a single creature contributes and whether elite/weak would move it toward a
 * target budget. Pure PF2e encounter-budget arithmetic (Core Rulebook table). Returns a note
 * string only in v1 — no actor mutation.
 */
fun creatureXpContribution(partySize: Int, partyLevel: Int, creatureLevel: Int): Int
```

`ringPlacement` is the heart of the placement model: the party token's centre is the ring
centre, `radiusPx` is the engine's start distance converted through the scene's
`grid.distance` (feet per square) and `grid.size` (px per square), and the N enemies are laid
out at equal angles. Collision and wall awareness are **out of scope for v1** (see §6.1) — the
ring is geometric only; the GM nudges tokens if one lands in a wall.

### 3.2 Impure wiring (jsMain) — spawn, combat, initiative

File: `src/jsMain/kotlin/at/posselt/pfrpg2e/camping/EncounterStager.kt`

```kotlin
/** Result of staging: what got created, so undo can reverse exactly this. */
data class StageOutcome(
    val spawnedTokenIds: List<String>,
    val combatId: String?,
)

/**
 * Full stage pipeline (GM-only). Pure geometry from EncounterStaging; everything else is
 * Foundry document I/O. Returns null (and notifies) if it cannot run (no active scene, no
 * party token, empty manifest).
 */
suspend fun stageEncounter(
    game: Game,
    partyActor: CampingActor,          // = PF2EParty; provides the ring-centre token
    manifest: RawEncounterManifest,
    startDistanceFt: Int,              // from EncounterResolverEngine (or manifest override)
    hidden: Boolean,                   // ambusher Hidden => spawn tokens hidden from players
): StageOutcome?

/** Reverse a StageOutcome: delete spawned tokens, then end/delete the combat if we made it. */
suspend fun undoStage(game: Game, scene: Scene, outcome: StageOutcome)
```

**Grounded Foundry API calls the wiring makes (verified against the external bindings):**

1. **Party centre** — read the party token's `x`/`y` off `scene.tokens.contents.find { it.actorId == partyActor.id }` and add half a grid cell, exactly as `getPartyCurrentHexKey()` does today (`camping/CampingUtils.kt`), producing the ring centre.
2. **Resolve creatures** — `fromUuidTypeSafe<PF2EActor>(uuid)` per manifest entry (util already used across `RandomEncounters.kt`).
3. **Build token data** — from each actor's `prototypeToken` (`Actor.prototypeToken`, `com/foundryvtt/core/documents/Actor.kt`), stamped with the `ringPlacement` `x`/`y`, `disposition = -1` (hostile), and `hidden = hidden`.
4. **Spawn** — `scene.createEmbeddedDocuments<TokenDocument>("Token", data)` (same embedded-create pattern as `structures/Scenes.kt` → `"Tile"` and `utils/Drawing.kt` → `"Drawing"`). Collect `_id`s for undo.
5. **Create + activate combat** — reuse `game.combats.active` if present, else create a `Combat`; `combat.activate()`.
6. **Add combatants** — `TokenDocument.createCombatants(spawnedTokens, CreateCombatantOptions(combat = combat))` (static on `TokenDocument`).
7. **Roll initiative + start** — `combat.rollNPC()` (or `rollAll()`), then `combat.startCombat()` (all on `Combat`, `com/foundryvtt/core/documents/Combat.kt`).
8. **Combat track** — **no explicit call.** Starting combat drives `round` 0→1, which the existing `registerCombatTrackHooks` (`combat/CombatTracks.kt`, `onPreUpdateCombat`) catches to `startCombatTrack(...)` when `getEnableCombatTracks()` is on. Staging inherits the music for free and must not double-drive it.

### 3.3 Tick surface — NONE

Staging is a **GM button action**, not a tick. It hooks **neither** `TurnTickingEngine`
(monthly End Turn) **nor** `DailyTickHooks` (daily world clock). It runs synchronously in
response to a click, mutates scene/combat documents directly, and returns. There is no
preview/commit parity concern because it is not part of the turn pipeline. Explicitly: **do
not add a third tick.**

---

## 4. UI

### 4.1 Stage button on `EncounterPreviewDialog`

`camping/EncounterPreviewDialog.kt` already routes clicks through `_onClickAction` on
`data-action` (`km-accept` / `km-reroll` / `km-reject` / `km-convert-quest`). Add:

- **New action `km-stage`** in `_onClickAction` → opens the **Stage dialog** (§4.3) seeded
  with the auto-detected manifest, the engine's `startDistanceFt`, and `hidden`.
- **New context fields** on `EncounterPreviewContext`:

```kotlin
val canStage: Boolean          // true when manifest has >= 1 creature AND category == COMBAT
val stageLabel: String         // t("camping.encounterStage")
val startDistanceFt: Int       // surfaced from EncounterResolverEngine for the GM
val creatureCount: Int         // sum of manifest counts (0 => button disabled)
```

The preview dialog gains the auto-seeded manifest as a constructor param (built by
`rollCuratedEncounter` from the drawn `TableResult` document ref when present, else empty).

**Template:** `src/jsMain/resources/applications/camping/encounter-preview.hbs` — add a
`{{#if canStage}}` button next to *Accept*; when `creatureCount == 0` render it disabled with
a tooltip ("add creatures to stage").

### 4.2 Stage button on Session Prep pending-encounter rows

Pending encounters render via `buildPendingEncounters()` (`kingdom/SessionPrepView.kt`,
filtering `hexContents` on `pendingEncounter == true`) into
`SessionPrepContext.pendingEncounters` and out through
`applications/kingdom/sections/session-prep/page.hbs`. Add:

- A per-row **`data-action="km-stage-pending"`** button carrying `data-hex-id` (wired through
  `KingdomSheet._onClickAction`, the same place other session-prep actions like
  `export-session-prep-to-journal` are handled).
- Enable only when that hex's `encounterManifest` is non-null and non-empty; otherwise the
  button links to the Stage dialog in *edit* mode so the GM can curate creatures first.
- New `SessionPrepEntry` fields: `canStage: Boolean`, `hexId: String` (already has `id`).

### 4.3 New Stage dialog (`ModifyEncounterStage`)

File: `src/jsMain/kotlin/at/posselt/pfrpg2e/camping/dialogs/ModifyEncounterStage.kt`
(a `FormApp`, modelled on the curator's `RegionConfig` / `CategoryWeightSettings` dialogs).

Lets the GM, before committing the spawn:

- see the **manifest list** — each creature row: name, count stepper, adjustment
  select (None / Elite / Weak — recorded; applied in v2), remove button;
- **add a creature** by dropping an actor (drag-drop UUID) or picking from a compendium;
- see and override **start distance** (defaults to the engine value) and the **XP-budget note**;
- toggle **spawn hidden** (defaults to the resolver's ambusher `Hidden` state);
- **Stage** (fires `stageEncounter`) or **Cancel**.

Context object:

```kotlin
@JsPlainObject
external interface EncounterStageContext : ValidatedHandlebarsContext {
    val creatures: Array<StageCreatureRowContext>
    val startDistanceFt: Int
    val spawnHidden: Boolean
    val xpBudgetNote: String?
    val totalCount: Int
    val partyLevel: Int
    val stageLabel: String
    val cancelLabel: String
}

@JsPlainObject
external interface StageCreatureRowContext {
    val uuid: String
    val name: String
    val count: Int
    val adjustment: String?   // "elite" | "weak" | null
    val xpNote: String?       // v1: informational per-row XP contribution
}
```

**Template:** `src/jsMain/resources/applications/camping/encounter-stage.hbs` (single root
element per the ApplicationV2 rule; register in `Main.kt` `loadTemplatePartials` if used as a
partial).

### 4.4 i18n namespace

All keys nested under the existing `camping.*` root object in `lang/en.json` (never
flat-dotted — `scripts/check_i18n_keys.py`), wired through the existing `initLocalization()`
(`Main.kt`). New keys:

```jsonc
"camping": {
  "encounterStage": "Stage Encounter",
  "encounterStageTitle": "Stage Encounter",
  "encounterStageAddCreature": "Add Creature",
  "encounterStageCount": "Count",
  "encounterStageAdjustment": "Adjustment",
  "encounterStageAdjustmentNone": "None",
  "encounterStageAdjustmentElite": "Elite",
  "encounterStageAdjustmentWeak": "Weak",
  "encounterStageStartDistance": "Start Distance (ft)",
  "encounterStageSpawnHidden": "Spawn Hidden",
  "encounterStageXpNote": "XP Budget",
  "encounterStageNoCreatures": "Add at least one creature to stage.",
  "encounterStaged": "Staged {{count}} creature(s) at {{distance}} ft.",
  "encounterStageNoParty": "No party token found on the active scene.",
  "encounterStageNoScene": "No active scene to stage on.",
  "encounterStageUndo": "Removed staged tokens and combat."
}
```

---

## 5. Chat / Offer Surfaces

**The Stage action itself is a GM button, not a GM-confirmed offer.** It is a direct GM
convenience (like the existing `km-view-settlement` sheet button), triggered from a dialog the
GM already has open, so it needs no `km-offer-*` chat card and no accept/decline round-trip.

- **No new `km-offer-*` cards** are introduced. The stager grants no *mechanical benefit to
  player characters* — it only spawns enemy tokens and a combat — so it falls outside the
  GM-confirmed-offer rule (which governs benefits applied to the kingdom / PCs).
- **Optional confirmation chat line (public):** after a successful stage, post a brief GM-only
  whisper (reuse `postChatMessage(..., whisper = gmIds)` as `whisperEncounterSuppressed` does)
  summarising "Staged 3 × Gnoll at 60 ft (hidden)". Purely informational; no buttons.
- **Undo** is a button *inside* the Stage flow (or a follow-up whisper button
  `data-action="km-stage-undo"` carrying the `StageOutcome` ids), not an offer. It calls
  `undoStage`.

The *upstream* offer already exists and is untouched: `km-offer-war-threat-arrival`
(`ChatButtons.kt`) is what *queues* a pending encounter on a hex (`pendingEncounter = true`).
The stager consumes that queue; it does not add to the offer surface.

---

## 6. Interactions With Existing Systems

| System | File(s) | Interaction |
|--------|---------|-------------|
| **Encounter Curator** | `camping/RandomEncounters.kt`, `camping/EncounterPreviewDialog.kt` | `rollCuratedEncounter` seeds the manifest from the drawn `TableResult` document ref (new: read `documentCollection`/`documentId`, not just `.text`); preview gains the Stage button |
| **Encounter Resolver** | `camping/EncounterResolverEngine.kt` | Supplies `startDistanceFt` (15/60/120) and ambusher `Hidden` → `spawnHidden`; unchanged, just consumed |
| **Roll tables** | `com/foundryvtt/core/documents/TableResult.kt` | `type`/`documentCollection`/`documentId` read to auto-detect actors (currently ignored) |
| **Session Prep** | `kingdom/SessionPrepView.kt`, `kingdom/sheet/contexts/SessionPrepContext.kt`, `sections/session-prep/page.hbs` | Pending-encounter rows gain a Stage button; `SessionPrepEntry` gains `canStage`/`hexId` |
| **Pending-encounter queue** | `kingdom/data/RawHexContent.kt`, `kingdom/ChatButtons.kt` (`km-offer-war-threat-arrival`) | New nullable `encounterManifest` on the hex; the arrival offer keeps queueing, the stager consumes |
| **Combat tracks** | `combat/CombatTracks.kt` | Starting the combat trips the existing `onPreUpdateCombat` round-0→1 hook → music plays; stager must NOT call `startCombatTrack` itself |
| **Token / combat docs** | `Scene`, `TokenDocument`, `Combat`, `CombatEncounters` externals | `createEmbeddedDocuments("Token")`, `TokenDocument.createCombatants`, `Combat.startCombat/rollNPC` |
| **Party token** | `camping/CampingUtils.kt` (`getPartyCurrentHexKey`) | Same token-centre read reused for the ring centre |
| **Turn / daily ticks** | `TurnTickingEngine.kt`, `DailyTickHooks.kt` | **No interaction** — staging is not a tick |

### 6.1 Explicit OUT-OF-SCOPE (v1)

- **Collision / wall / occupied-cell awareness** — ring placement is geometric; tokens may
  land on walls or overlap. GM nudges. No pathfinding, no `canvas` collision tests.
- **Auto Elite/Weak XP budgeting** — see §7 finding. The adjustment field is *recorded* and
  the per-creature XP contribution is *shown*, but no template is auto-applied in v1. Deferred
  to v2.
- **Player-character effects** — no auto-applying `unconscious`/`prone` to sleeping PCs (the
  resolver *narrates* those; applying them stays manual). Stager only spawns enemies.
- **Non-combat categories** — RP / merchant / rumor / weather / lore encounters have nothing
  to spawn; Stage is COMBAT-only.
- **Text-only tables** — a drawn result with no document ref and no GM-authored manifest
  cannot be staged; the button is disabled with a tooltip.
- **Difficulty auto-selection** — the stager does not *choose* creatures to hit a budget; it
  spawns what the manifest says.

---

## 7. Finding — Does PF2e Expose Elite/Weak Programmatically?

**Finding (verified against the bindings):** This module has **no elite/weak binding today** —
a grep for `elite` / `weak` / `applyAdjustment` / `adjustment` across `src/jsMain/kotlin`
returns nothing relevant (only an unrelated degree-of-success "adjustment"). The bound
`PF2ENpc` external (`com/foundryvtt/pf2e/actor/PF2ENpc.kt`) exposes only
`system.attributes.hp`; there is no adjustment method surfaced.

The **PF2e system itself does** support NPC elite/weak adjustment (the system's `NPCPF2e`
class exposes `applyAdjustment("elite" | "weak" | null)`, and the adjustment is data-driven via
`flags.pf2e`). But: (a) it is **not bound** in this module, so it needs new external-binding
work; and (b) applying it cleanly means adjusting the **spawned token's actor delta** (an
unlinked token), *not* the shared compendium source actor — otherwise every future spawn of
that bestiary entry inherits the template. That per-token, delta-scoped application is
non-trivial.

**Recommendation:** **Defer auto elite/weak to v2.** v1 spawns creatures as authored and
*surfaces the XP-budget arithmetic* (per-creature XP contribution + a running total vs. the
party's moderate/severe thresholds) so the GM applies Elite/Weak by hand on the PF2e sheet in
the rare case the manifest doesn't already fit. The `adjustment` field is persisted from day
one so v2 can honour it without a data migration.

---

## 8. Test Plan

### 8.1 commonTest (pure logic, JVM-less — `kotlin.test`)

**File:** `src/commonTest/kotlin/at/posselt/pfrpg2e/camping/EncounterStagingTest.kt`

| Test | Description |
|------|-------------|
| `ringPlacement_singleCreature_placesAtStartAngle` | count=1 → one point at radius, angle -π/2 (above centre) |
| `ringPlacement_spreadsEvenly` | count=4 → 4 points at 90° apart, all at `radiusPx` from centre (±ε) |
| `ringPlacement_centresOnGridCell` | returned origins are offset by −½ grid so token *centres* sit on the ring |
| `feetToPixels_convertsByGridScale` | 60 ft on a 5-ft/100-px grid → 1200 px; 120 ft → 2400 px |
| `feetToPixels_respectsNonFiveFootGrid` | 60 ft on a 10-ft/100-px grid → 600 px |
| `manifestSpawnCount_sumsCounts` | `[{count:2},{count:3}]` → 5; empty → 0 |
| `creatureXpContribution_matchesPf2eTable` | level-relative XP (−4..+4) matches the CRB budget table |
| `ringPlacement_zeroCount_returnsEmpty` | count=0 → empty list (button-gate parity) |

### 8.2 jsTest (Foundry-integrated — Chrome headless)

**File:** `src/jsTest/kotlin/at/posselt/pfrpg2e/camping/EncounterStagerTest.kt`

| Test | Description |
|------|-------------|
| `stageEncounter_spawnsTokensAtRingPositions` | mock scene + party token + 3-creature manifest → 3 `TokenDocument`s created at expected `x`/`y` |
| `stageEncounter_hiddenFlagPropagates` | `hidden=true` → spawned tokens have `hidden == true`, `disposition == -1` |
| `stageEncounter_createsCombatAndRollsInitiative` | combat created/active, spawned tokens added as combatants, initiative rolled |
| `stageEncounter_noPartyToken_returnsNullAndNotifies` | missing party token → null + error notification, no tokens created |
| `stageEncounter_emptyManifest_disabledPath` | empty manifest → null, nothing spawned |
| `stageEncounter_doesNotDoubleDriveCombatTrack` | stager makes no direct `startCombatTrack` call (music left to the existing hook) |
| `undoStage_deletesTokensAndCombat` | `undoStage(outcome)` removes exactly the spawned token ids and ends the combat it created |
| `preview_manifestAutoSeededFromDocumentResult` | a `TableResult` with `type="pack"` seeds one manifest creature; a `text` result seeds none |

### 8.3 Manual Foundry verification checklist

1. Load a hex-grid scene with a party token; configure a region whose category table has a
   COMBAT result pointing at a bestiary actor (document/pack result).
2. Trigger a curated encounter (watch roll) → `EncounterPreviewDialog` shows category + result
   → the **Stage Encounter** button is enabled and shows the creature count.
3. Click **Stage** → dialog opens pre-filled with the detected creature + the resolver's start
   distance; toggle count to 3.
4. Confirm **Stage** → 3 enemy tokens appear in a ring at ~60 ft around the party, combat
   tracker opens with initiative rolled, combat music starts.
5. Repeat on a **critical failure** watch result → tokens spawn at ~15 ft and **hidden**.
6. Undo → the 3 tokens and the combat are removed; music stops (existing delete-combat hook).
7. Text-only table result → Stage button disabled with tooltip; open dialog, drag an actor in,
   count 2 → Stage now works.
8. Session Prep → queue an encounter via a war-threat arrival offer (`km-offer-war-threat-arrival`
   → "Queue encounter"); the pending row shows a Stage button; curate creatures, Stage from
   there.
9. Reload world → pending-encounter manifest persists on the hex.
10. Non-combat category (RP/rumor) → no Stage button.
11. Combat-tracks setting OFF → staging still spawns + rolls initiative, just no music.

Build/verify per repo convention: `python3 scripts/check_i18n_keys.py`, then
`JAVA_HOME=<jdk25> ./gradlew assemble jsTest -x kotlinStoreYarnLock` (Chrome headless).

---

## 9. Phasing (independently committable)

| Phase | Title | Deliverable | Key files |
|-------|-------|-------------|-----------|
| **1** | **Pure placement + budget core** | `EncounterStaging.kt` (`StagePoint`, `ringPlacement`, `feetToPixels`, `manifestSpawnCount`, `creatureXpContribution`) + `EncounterStagingTest.kt`. No Foundry deps. | `commonMain/.../camping/EncounterStaging.kt`, `commonTest/.../camping/EncounterStagingTest.kt` |
| **2** | **Stager wiring + preview button** | `RawEncounterManifest.kt`, `EncounterStager.kt` (`stageEncounter`/`undoStage`), auto-seed manifest from `TableResult` doc ref in `rollCuratedEncounter`, Stage button + context on `EncounterPreviewDialog` + `encounter-preview.hbs`, `ModifyEncounterStage` dialog + `encounter-stage.hbs`, i18n. | `camping/RawEncounterManifest.kt`, `camping/EncounterStager.kt`, `camping/RandomEncounters.kt`, `camping/EncounterPreviewDialog.kt`, `camping/dialogs/ModifyEncounterStage.kt`, `applications/camping/*.hbs`, `lang/en.json`, `jsTest/.../EncounterStagerTest.kt` |
| **3** | **Pending-encounter (Session Prep) staging** | `RawHexContent.encounterManifest` + guarded accessor + `Migration49`; Stage button on Session Prep pending rows; `SessionPrepEntry`/context fields; wire `KingdomSheet._onClickAction`. | `kingdom/data/RawHexContent.kt`, `migrations/migrations/Migration49.kt`, `migrations/Migrations.kt`, `kingdom/SessionPrepView.kt`, `kingdom/sheet/contexts/SessionPrepContext.kt`, `kingdom/sheet/KingdomSheet.kt`, `sections/session-prep/page.hbs` |
| **4 (v2, optional)** | **Auto Elite/Weak budgeting** | New PF2e `applyAdjustment` binding; apply `adjustment` to spawned token actor deltas; budget auto-fit toward moderate/severe threshold. | `com/foundryvtt/pf2e/actor/PF2ENpc.kt` (binding), `camping/EncounterStager.kt`, `camping/EncounterStaging.kt` |

Phase 1 is standalone. Phase 2 depends on 1. Phase 3 depends on 2. Phase 4 is deferred and
depends on 2–3. **v1 = Phases 1–3** (spawn + place + initiative + music, from both the preview
and the pending queue).

---

## 10. Open Questions for Gregory

1. **Manifest auto-seed scope:** auto-read `documentCollection`/`documentId` off every drawn
   `TableResult`, or only when `type in ("document","pack")`? (Plan: only doc/pack results.)
2. **Ring facing:** start the ring straight up (`-π/2`), or oriented away from the party's
   movement/facing? Facing needs the token's rotation; up-is-fine for v1.
3. **Reuse vs. new combat:** if a combat is already active, add spawned tokens to it, or always
   create a fresh one? (Plan: reuse `game.combats.active` if present.)
4. **Hidden default:** tie `spawnHidden` to the resolver's ambusher `Hidden` state (plan), or
   always spawn visible and let the GM reveal?
5. **Manifest persistence for the ephemeral preview:** keep it purely in-memory (plan), or also
   let the GM "save this manifest to the region table" for reuse next time it's drawn?
6. **Elite/Weak in v1:** confirm deferral to v2 (§7) — v1 shows XP numbers but applies nothing.

---

**End of Plan.** Ready for review. Upon approval, implementation cards will be created per the
phasing table above.
