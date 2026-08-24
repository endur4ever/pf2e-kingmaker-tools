# Settlement Life-Event Generator — Implementation Plan

> **Status:** Plan only — no implementation yet
> **Date:** 2026-07-09
> **Roadmap item:** Follow-on to the shipped *Living settlement population* feature
> (see `docs/feature-roadmap.md` → "Completed beyond the original backlog")
> **Depends on:** Living settlement population (`PopulationDialogs.kt`, `RawNpcEntry`,
> `NpcNameGenerator`), Structure evaluation (`EvaluateStructures.kt`), Season derivation
> (`Weather.kt`), Quest generator (#2, `AddQuest`/`RawQuest`), Faction-standing GM-offer
> pattern (`ChatButtons.kt` `km-offer-*`), Turn history gazette (`TurnHistory.kt`)
> **Branch:** `kingmaker.5`

---

## Executive Summary

The *Living settlement population* feature shipped names, occupations, and roster CRUD:
every settlement now holds a `RawPopulationRoster` of named `RawNpcEntry` residents
(Svetlana Morozov, *Innkeeper*; Aldarn Vex, *Rat Catcher*; …). But those NPCs are inert
data — they sit in a list and never *do* anything. The settlement reads as a spreadsheet of
names, not an inhabited place.

This feature adds a **per-settlement life-event generator**. Once per kingdom turn (monthly,
at End Turn), each settlement rolls — with a chance scaled by its population and biased by
its **structures** and the current **season** — for a small "slice of life" event:

- A **festival** at the Theater (season-weighted: harvest fairs in Fall, midwinter feasts).
- A **feud** between two named roster NPCs (cast from the actual roster).
- **Crime** near the Thieves' Guild, blamed on a named ne'er-do-well.
- A **birth**, a **market day**, a **pilgrimage arrival**, a **tavern brawl**.

Each event produces **two things**:

1. **A gazette line** (always) — flavor for the Recent-Turns recap and the session-prep
   journal export. "*Midwinter feast at the Silver Stag; Svetlana Morozov crowned Frost Queen.*"
2. **A tiny mechanical hook** (optional, **GM-confirmed only**) — from a **closed set**:
   ±1 unrest, +1 RP, spawn a quest, spawn a rumor. Never auto-applied; surfaced as an
   **apply button** on a digest offer card, mirroring the `km-offer-*` pattern already used
   for war threats, diplomacy quests, and expedition rewards.

The result: settlements feel alive at near-zero GM cost. The GM clicks *Apply* on the two or
three hooks they like and ignores the rest; the flavor lines write themselves into the
gazette either way.

**Architecture fit:** All event selection, weighting, and casting is **pure**
(`commonMain` + `commonTest`), seeded deterministically from `(currentTurn, settlement id)`
so the Turn-Wizard **preview equals the commit** exactly — the same parity guarantee every
other End-Turn effect already honors. Event content is **data-driven JSON** under
`data/settlement-life-events/`, combined by the existing `CombineJsonFiles` task and validated
against a new JSON-schema, exactly like `data/events/`. The monthly cadence rides the existing
End-Turn path; `DailyTickHooks` is untouched (no third tick).

---

## 1. Problem Statement + Player/GM Value

**Problem.** The population roster is a static directory. There is no reason for a GM to open
the population tab twice, no fiction generated from it, and no reward for the players having
built a Theater or a Thieves' Guild beyond the flat kingdom-skill bonus. `docs/house-rules.md`
("A word on Settlements") explicitly complains that RAW gives "very few reasons to really build
settlements" and that structures outside the capital "seem largely unimportant"; the same doc
begs GMs to "add flavor, tangible benefits, lore and RP" so kingdom management "won't feel like
managing an Excel spreadsheet." Named residents that never act are exactly the missing flavor.

**Value to the GM.**
- **Prep reduction.** A festival, a feud, a market day appears every turn, pre-populated with
  the settlement's own residents. The GM narrates it or clicks *Apply* for a mechanical nudge —
  no invention required.
- **Structures finally matter.** A Theater unlocks festivals; a Thieves' Guild unlocks crime
  events; a Marketplace unlocks market days. Building a structure changes what the town *does*,
  not just its bonus number — directly serving the house-rule goal.
- **Gazette fuel.** Every turn yields 1–3 flavor lines that flow into the existing
  `formatTurnGazette` recap and the session-prep journal export — a living chronicle.

**Value to the players.**
- The kingdom feels inhabited. "Remember Aldarn the Rat Catcher? He got caught fencing goods
  near the Guild this month." Recurring named NPCs give the settlement continuity.
- Occasional player-facing hooks (a rumor, a quest) grow organically out of town life instead
  of dropping from the GM's clipboard.

**Non-goal.** This is *not* a simulation of the economy or a second event system competing with
kingdom events. It is a flavor layer with an opt-in, GM-gated mechanical trickle.

---

## 2. Data Model

### 2.1 Event-table JSON schema (data-driven, mirrors `data/events/`)

Content lives in **`data/settlement-life-events/*.json`** — one file per template. The existing
`CombineJsonFiles` Gradle task (`build.gradle.kts:29`, `sourceDirectory = data/`) already walks
every immediate sub-directory of `data/` and concatenates its `*.json` files into
`build/generated/data/<dirname>.json`. So this directory is combined into
`build/generated/data/settlement-life-events.json` with **zero build-glue changes**, and is
imported the same way `events.json` is:

```kotlin
// SettlementLifeEvents.kt (jsMain)
@JsModule("./settlement-life-events.json")
private external val rawSettlementLifeEvents: Array<RawSettlementLifeEvent>
```

A new JSON-schema at `src/commonMain/resources/schemas/settlement-life-event.json` plus a
`validateSettlementLifeEvents` task wired into `check` (mirroring `validateKingdomEvents`,
`build.gradle.kts:139`) validates every template at build time.

**One template file** (`data/settlement-life-events/midwinter-feast.json`):

```json
{
  "id": "midwinter-feast",
  "name": "settlementLife.event.midwinterFeast.name",
  "gazette": "settlementLife.event.midwinterFeast.gazette",
  "category": "festival",
  "baseWeight": 10,
  "minSettlementLevel": 1,
  "requiresStructures": [],
  "structureWeights": [
    { "anyOf": ["theater", "tavern", "marketplace"], "multiplier": 2.5 }
  ],
  "seasonWeights": { "spring": 0.5, "summer": 1.0, "fall": 1.5, "winter": 2.0 },
  "cast": [
    { "slot": "host", "preferOccupation": ["Tavern Keeper", "Innkeeper", "Brewer"] },
    { "slot": "guest", "distinctFrom": ["host"] }
  ],
  "hook": { "kind": "rp-delta", "magnitude": 1 },
  "cooldownTurns": 3
}
```

Field semantics:

| Field | Type | Meaning |
|-------|------|---------|
| `id` | string | Stable template id (kebab-case). |
| `name` / `gazette` | string | i18n keys (nested under `settlementLife.*`); `gazette` interpolates cast + settlement. |
| `category` | string | `"festival" \| "feud" \| "crime" \| "birth" \| "market" \| "civic"` — display grouping only. |
| `baseWeight` | number | Relative selection weight before multipliers. |
| `minSettlementLevel` | number? | Eligibility floor against `Settlement.level`. |
| `requiresStructures` | string[] | Base structure ids (see §6) that **must** be present, else ineligible. Empty = always eligible. |
| `structureWeights` | array | `{ anyOf: string[], multiplier: number }` — multiply weight when the settlement has any listed structure. |
| `seasonWeights` | object | Per-season multiplier (`spring`/`summer`/`fall`/`winter`); missing season = `1.0`. |
| `cast` | array | Ordered slot specs the caster fills from the roster (see §3.3). |
| `hook` | object | The single mechanical hook from the **closed set** (§5.2). `{ kind, magnitude?, questTemplate? }`. |
| `cooldownTurns` | number? | Turns before this template can fire again *in the same settlement* (0 / null = no cooldown). |

Weights are **relative**; the engine normalizes over the eligible set. `requiresStructures`,
`minSettlementLevel`, and per-template cooldown act as hard filters *before* weighting.

### 2.2 `Raw*` interfaces (jsMain, all `@JsPlainObject`, nullable for migration safety)

The template catalog is external content; the *persisted history* is new state on the settlement.

```kotlin
// SettlementLifeEvents.kt — the loaded catalog (matches the JSON schema)
@JsPlainObject
external interface RawSettlementLifeEvent {
    var id: String
    var name: String
    var gazette: String
    var category: String
    var baseWeight: Int
    var minSettlementLevel: Int?
    var requiresStructures: Array<String>?
    var structureWeights: Array<RawStructureWeight>?
    var seasonWeights: RawSeasonWeights?
    var cast: Array<RawCastSlot>?
    var hook: RawLifeEventHook?
    var cooldownTurns: Int?
}

@JsPlainObject external interface RawStructureWeight { var anyOf: Array<String>; var multiplier: Double }
@JsPlainObject external interface RawSeasonWeights   { var spring: Double?; var summer: Double?; var fall: Double?; var winter: Double? }
@JsPlainObject external interface RawCastSlot        { var slot: String; var preferOccupation: Array<String>?; var distinctFrom: Array<String>? }
@JsPlainObject external interface RawLifeEventHook   { var kind: String; var magnitude: Int?; var questTemplate: String? }
```

**History — added to `RawSettlement`** (`kingdom/structures/RawSettlement.kt`, ADD one field,
keep all existing fields; nullable for back-compat):

```kotlin
// Existing: sceneId, lots, level, type, layoutType, secondaryTerritory,
//           manualSettlementLevel, waterBorders, populationRoster, terrain, hexKey
var lifeEventHistory: Array<RawSettlementLifeEventRecord>?   // NEW — null on legacy data
```

```kotlin
@JsPlainObject
external interface RawSettlementLifeEventRecord {
    var recordId: String          // unique: "life-${sceneId}-${turn}-${templateId}"
    var templateId: String        // the RawSettlementLifeEvent.id that fired
    var turn: Int                 // kingdom turn it occurred on
    var castNpcIds: Array<String> // roster RawNpcEntry.ids cast into slots (see NPC-memory seam §6.3)
    var castNames: Array<String>  // resolved display names (incl. on-demand generated ones)
    var hookKind: String          // closed-set kind, echoed for the offer button
    var hookMagnitude: Int?       // echoed for the offer button
    var hookApplied: Boolean?     // GM clicked Apply (or Dismiss) — idempotency guard, like RawWarThreat.offerConsumed
}
```

### 2.3 Persistence — recommendation + justification

**Store `lifeEventHistory` on `RawSettlement`, which persists on the *kingdom* flag.**

`KingdomData.settlements: Array<RawSettlement>` (`KingdomData.kt:242`) is the durable home for
per-settlement mutable metadata; `populationRoster`, `terrain`, and `hexKey` already live there
and round-trip through `actor.setKingdom(kingdom)`. The `sceneId` merely *links* to the Foundry
scene for structure/block geometry (read-only during evaluation) — settlement *state* is on the
kingdom flag, not a scene flag. Putting history here means:

- **Same save path as the roster it references.** `castNpcIds` point at
  `populationRoster.npcs[].id`, which live in the same object graph — no cross-document join.
- **GM-writable through the normal End-Turn commit.** End Turn is already GM-only and already
  calls `actor.setKingdom(kingdom)`; no separate scene-flag write (which memory notes are
  GM-only and error for players) is introduced.
- **No new top-level `KingdomData` field.** History is naturally settlement-scoped.

A per-turn digest is *not* stored separately; the offer buttons resolve their event by
`(sceneId, recordId)` straight out of `lifeEventHistory`, and the `hookApplied` flag on the
record is the idempotency guard (same idea as `RawWarThreat.offerConsumed`).

### 2.4 Migration

**Migration49** *(placeholder — not free; see caveat)* (chain currently ends at `Migration61`; `MigrationChainTest` asserts
contiguity — Gregory assigns the real next number). Backfills `lifeEventHistory = []` on every
settlement, mirroring `Migration31` (which backfilled `populationRoster`):

> ⚠️ **The number in this section is stale and must be re-derived at implementation.** The chain
> ends at `Migration61`, not 48, and 62–67 are already proposed by the downtime-projects,
> scheduled-pressure, petition-inbox, npc-memory, seasonal-economy and loot-manifests plans. Nine
> unimplemented plans currently name `Migration49`, so it is not free for any of them. Take the
> next contiguous number when this actually lands, and update `MigrationChainTest`.


```kotlin
class Migration49 : Migration(49) {
    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        val settlements = kingdom.settlements ?: return
        for (i in 0 until (settlements.length as Int)) {
            val settlement = settlements[i]
            if (settlement.lifeEventHistory == null) settlement.lifeEventHistory = js("[]")
        }
    }
}
```

Register in `migrations/Migrations.kt` `listOf(...)`. Non-breaking: `null` history is treated
as empty by all readers, so pre-migration saves load unchanged.

---

## 3. Engine Design (pure `commonMain`)

### 3.1 Location & purity

`src/commonMain/kotlin/at/posselt/pfrpg2e/kingdom/SettlementLifeEngine.kt` — pure, no Foundry /
`Game` / `Date` / `Math.random`. Reuses the project's blessed pure RNG **`SeededRng`**
(`data.kingdom.settlements.SeededRng`, the same 32-bit LCG the name generator uses) and the
existing `NpcNameGenerator` for empty-roster fill. Unit-tested in
`src/commonTest/.../SettlementLifeEngineTest.kt`.

### 3.2 Core types & signatures

```kotlin
package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.regions.Season
import at.posselt.pfrpg2e.data.kingdom.settlements.NpcEntry
import at.posselt.pfrpg2e.data.kingdom.settlements.SeededRng

/** Pure, UI-free snapshot of one settlement handed to the engine. */
data class SettlementLifeInput(
    val settlementId: String,          // RawSettlement.sceneId — stable per settlement
    val name: String,
    val level: Int,                    // Settlement.level (drives volume + minLevel filter)
    val populationNumber: Int,         // Settlement.size.populationNumber (drives per-turn chance)
    val structureBaseIds: Set<String>, // constructedStructures.map { it.id.removeSuffix("-vk") }
    val roster: List<NpcEntry>,        // current populationRoster.npcs
    val recentTemplateIds: Map<String, Int>, // templateId -> most-recent turn it fired (for cooldown)
)

/** A concrete life event produced for one settlement this turn. */
data class SettlementLifeEvent(
    val templateId: String,
    val gazetteKey: String,
    val gazetteData: Map<String, String>, // interpolation: settlement + cast slot -> name
    val cast: List<CastMember>,
    val hook: LifeEventHook,              // closed set; may be LifeEventHook.None
)

data class CastMember(val slot: String, val npcId: String?, val name: String) // npcId null => generated on demand

/** CLOSED mechanical-hook vocabulary — nothing open-ended. */
sealed interface LifeEventHook {
    data object None : LifeEventHook
    data class UnrestDelta(val delta: Int) : LifeEventHook   // constrained to -1..+1 at parse time
    data class RpDelta(val delta: Int) : LifeEventHook       // constrained to 0..+1
    data class QuestSpawn(val questTemplateId: String?) : LifeEventHook
    data class RumorSpawn(val questTemplateId: String?) : LifeEventHook
}
```

**Main entry (deterministic; identical output for the same inputs → preview == commit):**

```kotlin
object SettlementLifeEngine {

    /** Per-kingdom hard cap on life events surfaced in one turn (chat + digest volume control). */
    const val DEFAULT_PER_TURN_CAP = 3

    /**
     * Roll life events for every settlement for ONE kingdom turn.
     * Settlements are processed in a STABLE order (sorted by settlementId) so the
     * global cap truncation is deterministic. Returns at most [perTurnCap] events.
     */
    fun rollTurn(
        settlements: List<SettlementLifeInput>,
        catalog: List<RawSettlementLifeEvent>,
        season: Season?,                 // null => all season multipliers treated as 1.0
        currentTurn: Int,
        kingdomSeed: Int,                // stable per world (e.g. kingdom actor id hash)
        perTurnCap: Int = DEFAULT_PER_TURN_CAP,
    ): List<SettlementLifeEvent>

    /** Roll 0 or 1 event for a SINGLE settlement (one roll per settlement per turn max). */
    fun rollSettlement(
        input: SettlementLifeInput,
        catalog: List<RawSettlementLifeEvent>,
        season: Season?,
        currentTurn: Int,
        kingdomSeed: Int,
    ): SettlementLifeEvent?

    /** Probability (0.0..1.0) that a settlement produces an event this turn, scaled by population. */
    fun eventChance(populationNumber: Int, level: Int): Double

    /** Effective selection weight for one template in one settlement + season, after all multipliers. */
    fun weightFor(
        template: RawSettlementLifeEvent,
        input: SettlementLifeInput,
        season: Season?,
        currentTurn: Int,
    ): Double

    /** Filter the catalog to templates eligible for this settlement (structures, minLevel, cooldown). */
    fun eligibleTemplates(
        input: SettlementLifeInput,
        catalog: List<RawSettlementLifeEvent>,
        currentTurn: Int,
    ): List<RawSettlementLifeEvent>
}
```

### 3.3 Casting — how roster NPCs fill template slots (`castRoster`)

```kotlin
/**
 * Fill a template's cast slots with distinct roster NPCs, deterministically.
 * - preferOccupation: soft filter — restrict the candidate pool to NPCs whose occupation
 *   matches; if none match, fall back to the full pool (never fail to cast).
 * - distinctFrom: exclude NPCs already assigned to the named prior slots.
 * - Empty / too-small roster: generate on-demand names via NpcNameGenerator (see below),
 *   with npcId = null so they are NOT persisted into the roster (never resurrects deleted NPCs).
 */
fun castRoster(
    slots: List<RawCastSlot>,
    roster: List<NpcEntry>,
    rng: SeededRng,
    nameGen: NpcNameGenerator,
): List<CastMember>
```

Casting policy (matches the shipped population philosophy — never auto-mutate the roster):

1. Build a candidate pool. If `preferOccupation` is set, prefer roster NPCs whose `occupation`
   is in the list; if the filtered pool is empty, use the whole roster (soft preference).
2. Remove NPCs assigned to any slot named in `distinctFrom`.
3. Draw one via `rng.nextInt(pool.size)`; record its `id` + `name`.
4. **Empty-roster / underfull fallback:** when the pool is exhausted, call
   `nameGen.generate().fullName` for an **ephemeral** cast member (`npcId = null`). These are
   *not* written back into `populationRoster` — matching `growPopulation()`'s rule that the
   generator never resurrects user-deleted NPCs. Their names are still recorded in
   `castNames` for the gazette and the NPC-memory seam (§6.3).

### 3.4 Determinism / seeding (preview parity)

Every random draw for a settlement in a turn comes from **one** `SeededRng` seeded purely from
stable inputs:

```kotlin
// Per-settlement stream seed — pure, no clock/randomness:
val seed: Long = mix(kingdomSeed, currentTurn, input.settlementId)
val rng = SeededRng(seed)          // gate roll, template pick, and casting all draw from this
val nameGen = NpcNameGenerator(seed xor 0x5DEECE66DL) // separate stream for fallback names
```

Because the seed depends only on `(kingdomSeed, currentTurn, settlementId)` — never on wall
time or `Math.random` — the **Turn-Wizard preview and the End-Turn commit produce byte-identical
events**, exactly like the rest of `TurnTickingEngine`. The global cap truncation is deterministic
too: settlements are sorted by `settlementId`, each rolls 0/1, fired events are collected in that
order, and the list is truncated to `perTurnCap`.

`eventChance` scales gently with population so hamlets are quiet and cities bustle, e.g.

```kotlin
fun eventChance(populationNumber: Int, level: Int): Double =
    (0.15 + 0.05 * level + populationNumber / 20000.0).coerceIn(0.15, 0.85)
```

(A village of ~400 at level 1 → ~0.22; a metropolis of ~25000 at level 15 → capped 0.85.)
Concrete constants are tuned in Phase 2 with the distribution tests.

### 3.5 Tick surface (monthly End Turn — no third tick)

The engine is invoked from the **existing monthly End-Turn path**, alongside the
`TurnTickingEngine.tick()` call, in **`KingdomUpkeep.performEndTurn`** (jsMain) — the one place
that already has `kingdom.getAllSettlements(game)`, the current turn, and the calendar month:

```kotlin
// In performEndTurn, after the pure tick, on the monthly cadence (End Turn only):
val settlementInputs = settlements.allSettlements.map { it.toLifeInput() }
val season = currentMonthZeroIndexed?.let { getSeasonForMonth(it) }   // Weather.kt; null => neutral
val lifeEvents = SettlementLifeEngine.rollTurn(
    settlements = settlementInputs,
    catalog = translateSettlementLifeEvents(),
    season = season,
    currentTurn = kingdom.turnsPassed,   // or the same turn counter tickQuests uses
    kingdomSeed = actor.id.hashCode(),
)
// 1) append each to the acting settlement's lifeEventHistory (records the cast — always)
// 2) post ONE digest offer card (§5) with per-event apply buttons
// 3) feed gazette lines into formatTurnGazette (§4 / §6)
```

`TurnTickingEngine.tick()` itself is **not** given settlement data — it stays the settlement-free
pure economic tick it is today. The life roll is a sibling pure call on the same monthly path.
This respects the tick split: **monthly End Turn only; `DailyTickHooks` / `DailyTickEngine`
untouched.** One roll per settlement per turn (max), one digest card per turn, capped globally.

---

## 4. UI

### 4.1 Digest offer card (primary surface)

There is **no new nav entry** and **no forced dialog**. The turn's life events are delivered as
**one whispered-to-GM digest chat card** posted during End Turn (template
`src/jsMain/resources/chatmessages/settlement-life-digest.hbs`), grouped by settlement, each row
carrying its own apply/dismiss buttons. This avoids chat spam (the global cap keeps it to ≤3 rows)
and matches the end-turn faction-moves digest shape.

Context object (thin `@JsPlainObject`, built in `performEndTurn`):

```kotlin
@JsPlainObject
external interface SettlementLifeDigestContext {
    val actorUuid: String
    val turn: Int
    val rows: Array<SettlementLifeRowContext>
}

@JsPlainObject
external interface SettlementLifeRowContext {
    val settlementId: String
    val settlementName: String
    val recordId: String
    val gazetteLine: String        // already localized + cast-interpolated
    val category: String
    val hookKind: String           // "none" | "unrest-delta" | "rp-delta" | "quest-spawn" | "rumor-spawn"
    val hookLabel: String?         // localized button label, e.g. "+1 RP"; null when hookKind == "none"
    val applied: Boolean           // hide/disable the button when already actioned
}
```

Template sketch (single root element; buttons wired by data-attributes, never a bare
ChatButton-on-sheet — see memory "sheet-buttons-vs-chatbuttons"):

```hbs
<div class="km-settlement-life-digest">
  <h3>{{localizeKM "settlementLife.digest.title"}}</h3>
  {{#each rows}}
  <div class="km-life-row">
    <span class="km-life-flavor">{{this.gazetteLine}}</span>
    {{#unless this.applied}}{{#if this.hookLabel}}
    <button type="button" class="km-offer-life-event"
            data-settlement-id="{{this.settlementId}}"
            data-record-id="{{this.recordId}}"
            data-hook-kind="{{this.hookKind}}"
            data-kingdom-actor-uuid="{{../actorUuid}}">
      {{this.hookLabel}}
    </button>
    <button type="button" class="km-offer-life-event"
            data-settlement-id="{{this.settlementId}}"
            data-record-id="{{this.recordId}}" data-hook-kind="dismiss"
            data-kingdom-actor-uuid="{{../actorUuid}}">
      {{localizeKM "settlementLife.digest.dismiss"}}
    </button>
    {{/if}}{{/unless}}
  </div>
  {{/each}}
</div>
```

### 4.2 Optional settlement history view (read-only)

A small, GM-facing **"Town Life"** panel inside the existing settlement inspector
(`InspectSettlement.kt` → a new `SettlementNav.LIFE` tab, template
`applications/kingdom/settlement-life.hbs`) lists the most recent `lifeEventHistory` records
(newest first, windowed to ~20): turn, localized gazette line, cast names, and whether the hook
was applied. Pure read of `RawSettlement.lifeEventHistory`; no new state. This is the durable
"what has this town lived through" log and the visible seed of the future NPC-memory feature.

### 4.3 i18n namespace

All keys nested under `pf2e-kingmaker-tools` → **`settlementLife.*`** in `lang/en.json`
(nested objects, never flat-dotted — see memory "i18n-i18next-nesting"; guard with
`scripts/check_i18n_keys.py`). Catalog wired into `initLocalization()` via
`translateSettlementLifeEvents()` (mirrors `translateKingdomEvents()`).

```json
"settlementLife": {
  "digest": { "title": "Town Life This Month", "dismiss": "Dismiss" },
  "tab": { "title": "Town Life" },
  "hook": { "unrestUp": "+1 Unrest", "unrestDown": "-1 Unrest", "rp": "+1 RP",
            "quest": "Create Quest", "rumor": "Spread Rumor" },
  "event": {
    "midwinterFeast": {
      "name": "Midwinter Feast",
      "gazette": "{{settlement}} throws a midwinter feast; {{host}} hosts, {{guest}} is guest of honor."
    },
    "guildTheft": {
      "name": "Guild Theft",
      "gazette": "Goods vanish near the Thieves' Guild in {{settlement}}; {{culprit}} is blamed."
    }
  }
}
```

---

## 5. Chat / Offer Surfaces (GM-Confirmed Only)

### 5.1 The single offer handler

One new `ChatButton` registered in `ChatButtons.kt`, **`km-offer-life-event`**, handles every
row via its `data-hook-kind`. It follows the established handler contract verbatim: GM gate →
`actor.getKingdom()` → locate the record by `(settlementId, recordId)` → idempotency guard on
`hookApplied` → apply the closed hook → `actor.setKingdom(kingdom)` → `postChatMessage(...)`.

```kotlin
ChatButton("km-offer-life-event") { game, actor, event, button ->
    if (!game.user.isGM) return@ChatButton
    val settlementId = button.dataset["settlementId"] ?: return@ChatButton
    val recordId = button.dataset["recordId"] ?: return@ChatButton
    val hookKind = button.dataset["hookKind"] ?: return@ChatButton
    actor.getKingdom()?.let { kingdom ->
        val settlement = kingdom.settlements.find { it.sceneId == settlementId } ?: return@ChatButton
        val record = settlement.lifeEventHistory?.find { it.recordId == recordId } ?: return@ChatButton
        if (record.hookApplied == true) return@ChatButton            // idempotency, like offerConsumed
        when (hookKind) {
            "dismiss"      -> { /* just mark applied */ }
            "unrest-delta" -> applyUnrestDelta(kingdom, record.hookMagnitude ?: 0)   // clamped like every unrest write
            "rp-delta"     -> applyRpDelta(kingdom, record.hookMagnitude ?: 0)
            "quest-spawn"  -> { openAddQuestPrefilled(actor, settlement, record); /* returns; marks applied on save */ }
            "rumor-spawn"  -> spawnRumorQuest(kingdom, settlement, record)           // hidden RawQuest, type "other"
            else -> return@ChatButton
        }
        record.hookApplied = true
        actor.setKingdom(kingdom)
        postChatMessage(t("settlementLife.applied", recordOf("name" to settlement /* name */)))
    }
}
```

- **`quest-spawn`** reuses `AddQuest(prefillTitle=…, prefillGiver=<cast NPC or settlement>)` and
  appends to `kingdom.quests`, exactly like `km-offer-diplomacy-quest` (`ChatButtons.kt:270`).
- **`rumor-spawn`** creates a `RawQuest` with `hidden = true`, `type = "other"`, `category =
  "side"` — a GM-only rumor that can later be promoted; **no new subsystem** (deliberately reuses
  the quest surface rather than touching the Encounter/Rumor Curator, #11).

### 5.2 Closed mechanical-hook vocabulary

The hook set is **closed** — the JSON `hook.kind` enum and the `LifeEventHook` sealed interface
are the only shapes that exist. Anything outside this set is a schema-validation failure.

| `hook.kind` | Effect on Apply | Constraint (enforced at parse) | Surface |
|-------------|-----------------|--------------------------------|---------|
| `none` | (flavor only) | — | No button; gazette line only |
| `unrest-delta` | ±1 unrest, clamped | `magnitude ∈ {-1, +1}` | `[+1 Unrest]` / `[-1 Unrest]` |
| `rp-delta` | +1 RP (next), clamped | `magnitude ∈ {0, +1}` | `[+1 RP]` |
| `quest-spawn` | Opens `AddQuest` prefilled | `questTemplate?` seeds title/giver | `[Create Quest]` |
| `rumor-spawn` | Hidden rumor `RawQuest` | `questTemplate?` seeds text | `[Spread Rumor]` |

**Every mechanical change is a GM-confirmed offer.** The gazette line is always emitted (flavor);
the hook never auto-applies. `none`-kind events are pure flavor with no button.

---

## 6. Interactions with Existing Systems

| System | File(s) | Interaction |
|--------|---------|-------------|
| **Population roster** | `structures/RawSettlement.kt`, `dialogs/PopulationDialogs.kt`, `Settlement.kt` (`generateInitialPopulation`, `npcOccupations`) | Source of cast members. `castRoster` reads `populationRoster.npcs`; `preferOccupation` matches `RawNpcEntry.occupation`. History is stored beside the roster on `RawSettlement`. **Never mutates the roster** (ephemeral fallback names, `npcId = null`). |
| **Structure evaluation** | `modifiers/evaluation/EvaluateStructures.kt`, `data/.../Settlement.kt` | `SettlementLifeInput.structureBaseIds = constructedStructures.map { it.id.removeSuffix("-vk") }` — the exact idiom `Settlement.trainers`/`craftingAccess` already use. Drives `requiresStructures` + `structureWeights` (e.g. `theater`, `thieves-guild`, `marketplace`, `tavern-*`, `shrine`). |
| **Season derivation** | `data/regions/Weather.kt` (`getSeasonForMonth`, `Season`) | `seasonWeights` bias. Month comes from the kingdom's calendar (Seasons & Stars via `SimpleCalendar`/`CalendarLogger`, already wired at End Turn); `null` month → neutral (all 1.0). |
| **Name generator** | `data/.../NpcNameGenerator.kt`, `SeededRng` | Empty/underfull roster fallback; also the engine's shared pure RNG. |
| **Quest generator (#2)** | `data/RawQuest.kt`, `dialogs/AddQuest.kt` | `quest-spawn` / `rumor-spawn` hooks open/append quests — same path as `km-offer-diplomacy-quest`. |
| **Turn history / gazette** | `kingdom/TurnHistory.kt` (`formatTurnGazette`) | Every life event contributes one **public** gazette line (flavor, no weights/hooks leaked) to the Recent-Turns recap + session-prep journal export. |
| **End-Turn path** | `sheet/KingdomUpkeep.kt` (`performEndTurn`), `TurnTickingEngine.kt`, `dialogs/TurnWizardApplication.kt` | Life roll invoked here (monthly). Preview (`TurnWizard`) and commit run the same pure `rollTurn` → identical events. |
| **Offer pattern** | `kingdom/ChatButtons.kt` | New `km-offer-life-event` handler beside the existing `km-offer-*` family. |
| **Daily tick** | `DailyTickHooks.kt`, `DailyTickEngine.kt` | **No interaction** — life events are monthly only. |
| **Migrations** | `migrations/Migrations.kt`, `migrations/migrations/Migration31.kt` (pattern) | `Migration49` backfills `lifeEventHistory`. |

### 6.1 Structure-id note

Template `requiresStructures` / `structureWeights` use **base structure ids** (the `-vk` suffix
stripped, as in `EvaluateStructures`). The authoritative id set is whatever exists under
`data/structures/`; the JSON-schema does **not** enum-constrain structure ids (they are open
strings) so homebrew structures work, but the `validateSettlementLifeEvents` task plus a
`SettlementLifeCatalogTest` sanity-check the shipped templates reference real ids.

### 6.2 Explicit OUT-OF-SCOPE

- **No new economy.** Hooks are the closed ±1 unrest / +1 RP / quest / rumor set — no commodity
  flows, no population growth, no structure damage.
- **No auto-applied effects.** Every mechanical change is a GM-confirmed offer.
- **No per-NPC stat blocks / Foundry actors.** Cast members are roster rows (names), not actors.
- **No daily/weekly cadence.** Monthly End Turn only.
- **No cross-settlement or faction interplay** (that is the Faction Agenda plan's job).
- **No automatic roster mutation** (births do not add residents; deaths do not remove them —
  those remain explicit user actions in `PopulationDialogs`).

### 6.3 NPC-memory seam (note, do not build)

`RawSettlementLifeEventRecord.castNpcIds` deliberately records *which roster NPCs* were cast into
*which events on which turns*. This is the persistence seam for a **future NPC-memory feature**
("Svetlana has hosted 3 feasts and been feuding with Aldarn for two years"): that feature would
read `lifeEventHistory` grouped by `castNpcIds` to build per-NPC timelines and bias future casting
toward recurring characters. **This plan only writes the records; it does not read them back for
casting bias.** Keeping the field now avoids a second migration later.

---

## 7. Test Plan

### 7.1 `commonTest` — pure logic (`SettlementLifeEngineTest.kt`)

| Test | Asserts |
|------|---------|
| `eligibleTemplates_filtersByRequiredStructure` | A `requiresStructures:["theater"]` template is excluded when the settlement lacks a theater, included when present. |
| `eligibleTemplates_respectsMinLevel` | `minSettlementLevel` gates by `Settlement.level`. |
| `eligibleTemplates_respectsCooldown` | A template that fired last turn with `cooldownTurns:3` is excluded until turn+3. |
| `weightFor_appliesStructureAndSeasonMultipliers` | Theater present + Fall on a harvest-fair template multiplies base weight by the product of the two multipliers. |
| `weightFor_neutralSeasonWhenNull` | `season == null` treats all season multipliers as 1.0. |
| `rollSettlement_weightedSelectionDistribution` | 10 000 seeded rolls match relative weights ±2%. |
| `rollSettlement_atMostOneEvent` | Never returns >1 event for a single settlement. |
| `eventChance_scalesWithPopulation` | Hamlet chance < city chance; both within `[0.15, 0.85]`. |
| `rollTurn_deterministicSameSeed` | Same `(kingdomSeed, turn, settlements)` → identical event list (preview==commit). |
| `rollTurn_respectsGlobalCap` | 8 settlements all firing → exactly `perTurnCap` events, truncation deterministic (sorted by id). |
| `castRoster_castsDistinctNpcs` | `distinctFrom` slots never draw the same NPC twice. |
| `castRoster_softOccupationPreference` | Prefers matching occupation; falls back to full pool when none match. |
| `castRoster_emptyRosterGeneratesEphemeralNames` | Empty roster → cast members with `npcId == null` and non-blank generated names; roster unchanged. |
| `parse_rejectsOutOfRangeHookMagnitude` | `unrest-delta` with magnitude 5 fails the closed-set constraint. |

### 7.2 `jsTest` — Foundry-integrated

| Test | Asserts |
|------|---------|
| `performEndTurn_appendsLifeEventHistory` | After an End Turn, fired events are appended to the acting `RawSettlement.lifeEventHistory` and persist through `setKingdom`. |
| `previewCommitParity` | Turn-Wizard preview life events == committed life events (byte-identical). |
| `digestCard_rendersRowsWithButtons` | The digest template renders one row per event with the correct `data-hook-kind`. |
| `offerHandler_appliesHookOnce` | Clicking `km-offer-life-event` applies the hook and flips `hookApplied`; a second click is a no-op (idempotent). |
| `offerHandler_questSpawnOpensAddQuest` | `quest-spawn` opens `AddQuest` prefilled; save appends to `kingdom.quests`. |
| `offerHandler_playerBlocked` | Non-GM click returns early (no state change). |
| `migration49_backfillsHistory` | Legacy settlement (no `lifeEventHistory`) migrates to `[]`; existing roster untouched. |
| `catalog_referencesRealStructureIds` | Every shipped template's `requiresStructures` id exists under `data/structures/`. |

### 7.3 Manual Foundry verification checklist

1. Open a settlement with a seeded roster; confirm the new **Town Life** tab shows an empty history.
2. Build a **Theater**; End Turn a few times → festival events appear more often than before.
3. Build a **Thieves' Guild** → crime events referencing a named resident start appearing.
4. End Turn → **one digest card** whispered to the GM lists ≤3 rows, each with an apply button.
5. Click **+1 RP** on a festival row → RP increases by 1, button disappears, chat confirms.
6. Click a **Create Quest** row → `AddQuest` opens prefilled with the settlement/cast; save → appears in Quests.
7. Reload the world → `lifeEventHistory`, applied flags, and any spawned quests persist.
8. Open the **Recent Turns** recap → life-event gazette lines are present and player-visible.
9. Delete every roster NPC, End Turn → events still fire with freshly *generated* names; the
   deleted NPCs are **not** resurrected into the roster.
10. Preview an End Turn in the Turn Wizard, then commit → the life events shown in preview are the
    same ones committed (parity).
11. Advance the calendar into Fall/Winter → season-flavored events (harvest fair, midwinter feast)
    become more frequent.

Build/verify per project convention: `python3 scripts/check_i18n_keys.py`, then
`JAVA_HOME=<jdk25> ./gradlew assemble jsTest -x kotlinStoreYarnLock` (Chrome-headless karma
override).

---

## 8. Phasing (Independently Committable)

Each phase is one kanban card, ~1–2 days, green `assemble jsTest` at the end.

| Phase | Title | Deliverable | Key files |
|-------|-------|-------------|-----------|
| **1** | **Data model + schema + migration** | `RawSettlementLifeEvent` + sub-interfaces, `RawSettlementLifeEventRecord`, `RawSettlement.lifeEventHistory`, JSON-schema `settlement-life-event.json`, `validateSettlementLifeEvents` wired into `check`, `Migration49`, an initial `data/settlement-life-events/*.json` set (~10 templates: festival, feud, guild-theft, birth, market-day, pilgrimage, tavern-brawl, civic-dispute, good-harvest, plague-scare). | `RawSettlement.kt`, `SettlementLifeEvents.kt`, `schemas/settlement-life-event.json`, `build.gradle.kts`, `Migration49.kt`, `Migrations.kt`, `data/settlement-life-events/` |
| **2** | **Pure engine + tests** | `SettlementLifeEngine.kt` (eligibility, weighting, `eventChance`, `rollSettlement`, `rollTurn` with cap, `castRoster`), `LifeEventHook` closed set + parse constraints, deterministic seeding; full `commonTest` suite. | `SettlementLifeEngine.kt`, `SettlementLifeEngineTest.kt` |
| **3** | **End-Turn integration + gazette** | `toLifeInput()` adapter, invoke `rollTurn` in `performEndTurn` (monthly), append to `lifeEventHistory`, feed lines into `formatTurnGazette`; preview parity in `TurnWizardApplication`; `jsTest` integration + parity + migration tests. | `KingdomUpkeep.kt`, `TurnHistory.kt`, `TurnWizardApplication.kt`, `SettlementLifeEvents.kt` (`translate…`) |
| **4** | **Digest offer card + handler + i18n** | `settlement-life-digest.hbs`, `SettlementLifeDigestContext`, `km-offer-life-event` handler (all 5 hook kinds, idempotency, GM gate), `settlementLife.*` i18n; digest/offer `jsTest`. | `ChatButtons.kt`, `chatmessages/settlement-life-digest.hbs`, context file, `lang/en.json`, `initLocalization` |
| **5** | **Settlement history view + QA** | `SettlementNav.LIFE` tab, `settlement-life.hbs`, read-only history context; full manual checklist; template↔structure-id sanity test. | `InspectSettlement.kt`, `applications/kingdom/settlement-life.hbs`, `SettlementLifeCatalogTest.kt` |

Phases 1 and 2 can run in parallel (data vs. pure logic). Phase 3 depends on 1+2; Phase 4 on 3;
Phase 5 on 4. Phases 1–3 deliver a working flavor-only feature (gazette lines, no buttons);
Phase 4 adds the mechanical offers; Phase 5 adds the durable history view.

---

## 9. Open Questions for Gregory

1. **Per-turn global cap** — default `3` life events surfaced kingdom-wide per turn. Higher for
   busier chronicles, lower to keep chat quiet?
2. **`eventChance` curve** — the proposed `0.15 + 0.05·level + pop/20000` (village ≈ 0.22,
   metropolis ≈ 0.85). Flatter (every town roughly equal) or steeper (big cities dominate)?
3. **Rumor hook target** — plan maps `rumor-spawn` to a hidden `RawQuest` (reuses the quest
   surface). Prefer that, or route rumors into the Encounter/Rumor Curator (#11) instead?
4. **History retention** — keep full `lifeEventHistory` forever (feeds the NPC-memory seam) or
   window it to the last N turns to bound flag size? Plan keeps all; the view windows to ~20.
5. **Should births ever offer to add a resident?** Plan keeps roster mutation manual (out of
   scope). A future `birth` template could offer an "Add to roster" button if wanted.
6. **Digest visibility** — whisper the digest to GMs only (plan), or post it publicly so players
   see town life directly (the gazette recap already exposes the lines either way)?

---

## 10. Appendix: Two Concrete Template Files

`data/settlement-life-events/guild-theft.json`:

```json
{
  "id": "guild-theft",
  "name": "settlementLife.event.guildTheft.name",
  "gazette": "settlementLife.event.guildTheft.gazette",
  "category": "crime",
  "baseWeight": 8,
  "minSettlementLevel": 1,
  "requiresStructures": ["thieves-guild"],
  "structureWeights": [{ "anyOf": ["marketplace", "luxury-store"], "multiplier": 1.5 }],
  "seasonWeights": { "winter": 1.3 },
  "cast": [
    { "slot": "culprit", "preferOccupation": ["Rat Catcher", "Courier", "Tinker"] },
    { "slot": "victim", "preferOccupation": ["Merchant", "Jeweler"], "distinctFrom": ["culprit"] }
  ],
  "hook": { "kind": "unrest-delta", "magnitude": 1 },
  "cooldownTurns": 2
}
```

`data/settlement-life-events/market-day.json`:

```json
{
  "id": "market-day",
  "name": "settlementLife.event.marketDay.name",
  "gazette": "settlementLife.event.marketDay.gazette",
  "category": "market",
  "baseWeight": 12,
  "requiresStructures": [],
  "structureWeights": [{ "anyOf": ["marketplace", "general-store", "stockyard"], "multiplier": 2.0 }],
  "seasonWeights": { "spring": 1.2, "summer": 1.4, "fall": 1.4, "winter": 0.6 },
  "cast": [{ "slot": "trader", "preferOccupation": ["Merchant", "Teamster", "Farmer"] }],
  "hook": { "kind": "rp-delta", "magnitude": 1 },
  "cooldownTurns": 1
}
```

> `structureWeights[].anyOf` and `requiresStructures` use base structure ids (post `-vk` strip);
> the shipped set is sanity-checked against `data/structures/` in `SettlementLifeCatalogTest`.

---

**End of Plan.** Ready for review. On approval, implementation cards follow the phasing table.
