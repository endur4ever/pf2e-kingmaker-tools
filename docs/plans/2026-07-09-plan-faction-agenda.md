# Faction Agenda Engine — Implementation Plan

> **Status:** Plan only — no implementation yet
> **Date:** 2026-07-09
> **Roadmap item:** New backlog #8 (Faction Agenda Engine)
> **Depends on:** Faction & Diplomacy Relations Tracker (#1, phases 1–3 landed), Army & War Pressure Board (#12), Quest/Event Generator (#2), Companion Expeditions (daily tick pattern), Turn History gazette
> **Branch:** `kingmaker.5`

---

## Executive Summary

Today every faction (`RawGroup`) is a static trade partner with a single `standing` number that only ever drifts toward the PCs. Factions never *act on each other* — no wars between Pitax and Brevoy, no fey courts sabotaging merchants, no troll lords building armies while the PCs look the other way.

This feature gives each faction an **agenda**: a goal with a progress clock and a small weighted move table (expand, sabotage rival, court ally, raise army, court the PCs). A pure deterministic engine advances every agenda once per **kingdom turn** (monthly, at End Turn). Every externally-visible move resolves as a **GM-confirmed offer** (`km-offer-*` pattern); accepted moves emit gazette lines and can spawn quests/war threats through existing generators.

This is the keystone living-world feature: rival realms, faction-vs-faction fiction, and the "Meanwhile" digest all compound on it.

---

## 1. Problem Statement + Player/GM Value

**Problem:** Kingmaker is a sandbox AP about *competing powers* in the Stolen Lands. The module currently models those powers as inert trade counters. The GM must manually invent every inter-faction conflict, army buildup, or diplomatic maneuver — or the world feels static.

**Value to the table:**

- **GM prep reduction:** Factions generate their own plot hooks. "Pitax is sabotaging Mivon's trade routes" appears as an offer card; the GM just clicks *Accept* and a quest/war threat spawns.
- **Emergent narrative:** Faction-vs-faction fiction arises from deterministic rules, not GM fiat. Players see the world moving around them ("Meanwhile, the Sootscale Kobolds expanded their warrens…").
- **Strategic depth:** PCs can *influence* agendas (send envoys, sabotage, ally) and see the ripples on the faction board.
- **Gazette fuel:** Every turn produces 1–3 gazette lines automatically — the "Recent Turns" recap and session-prep journal export become living chronicles.

---

## 2. Data Model

### 2.1 New External Interfaces (all `@JsPlainObject`, nullable for migration safety)

#### `RawFactionAgenda.kt` — lives in `src/jsMain/kotlin/.../kingdom/data/`

```kotlin
@JsPlainObject
external interface RawFactionAgenda {
    /** Stable goal identifier (e.g. "expand-territory", "destroy-rival", "court-pcs"). */
    var goalId: String

    /** Human-readable goal title for UI/gazette (i18n key or free text). */
    var goalTitle: String

    /** Progress clock: 0..segments. When >= segments the goal completes and a new one is drawn. */
    var progress: Int
    var segments: Int           // 4 / 6 / 8 segments per goal tier

    /** Which weighted move table this faction uses (see §3.2). */
    var archetype: String       // "aggressive" | "mercantile" | "fey" | "political" | "monster"

    /** Per-turn cooldown map: moveId -> turns remaining before that move can fire again. */
    var moveCooldowns: Record<String, Int>

    /** Last turn this agenda was advanced (for idempotency / catch-up). */
    var lastAdvancedTurn: Int?

    /** Optional: the specific rival faction this agenda is targeting (by RawGroup.name). */
    var targetFaction: String?
}
```

#### `RawGroup.kt` — ADDITIONS only (keep all existing fields)

```kotlin
// Existing: name, negotiationDC, atWar, preventPledgeOfFealty, relations, standing, standingLog, allianceLevel, hexKey

// NEW — nullable for back-compat with pre-existing groups
var agenda: RawFactionAgenda?
```

#### `RawFactionAgendaMove.kt` — the move catalog (static data, loaded from JSON)

```kotlin
@JsPlainObject
external interface RawFactionAgendaMove {
    var id: String                 // "expand", "sabotage-rival", "court-ally", "raise-army", "court-pcs"
    var label: String              // i18n key: "kingdom.factionAgenda.move.expand"
    var weight: Int                // relative weight in the archetype's table
    var cooldownTurns: Int         // 0 = no cooldown
    var validTargets: String       // "self" | "rival" | "ally" | "pcs" | "any-faction"
    var effect: String             // "clock" | "standing-delta" | "war-threat" | "quest" | "army"
    var effectMagnitude: Int       // e.g. clock segments, standing delta, army strength
    var requires: String?          // optional precondition i18n key (e.g. "not-at-war", "has-hex")
}
```

### 2.2 Persistence Location

- **Kingdom flag** (`KingdomData`): `groups: Array<RawGroup>` already exists — `agenda` nests on each group. No new top-level field needed.
- **No camping flag, no world setting.** This is kingdom-scoped state.

### 2.3 Migration

**MigrationNN** (next available number after current highest):

```kotlin
// For each existing RawGroup:
if (group.agenda == null) {
    val archetype = pickArchetypeForGroup(group) // deterministic by name hash
    group.agenda = RawFactionAgenda(
        goalId = initialGoalFor(archetype),
        goalTitle = i18nKeyForGoal(initialGoalFor(archetype)),
        progress = 0,
        segments = 6,
        archetype = archetype,
        moveCooldowns = js("{}").unsafeCast<Record<String, Int>>(),
        lastAdvancedTurn = null,
        targetFaction = null
    )
}
```

- `standing`/`standingLog`/`allianceLevel` already nullable → no further migration needed for diplomacy tracker.
- Migration is **non-breaking**: `agenda` null = faction has no agenda yet (treated as idle).

---

## 3. Engine Design

### 3.1 Pure Core (commonMain)

File: `src/commonMain/kotlin/.../kingdom/FactionAgendaEngine.kt`

```kotlin
package at.posselt.pfrpg2e.data.kingdom

/** Pure agenda advance for ONE faction for ONE kingdom turn.
 *  Returns the updated agenda + a list of emitted Moves (for offer cards).
 */
data class AgendaAdvanceResult(
    val agenda: RawFactionAgenda,
    val moves: List<FactionMove>,
    val newGoal: RawFactionAgenda?   // non-null when progress >= segments (goal completed)
)

/** One concrete move the faction took this turn. */
data class FactionMove(
    val moveId: String,
    val label: String,
    val targetFaction: String?,      // null for self-targeted moves
    val effect: MoveEffect,
    val gazetteLine: String,         // i18n key with interpolated data
    val offerCard: OfferCard?        // null if purely internal (e.g. clock tick)
)

sealed interface MoveEffect {
    data class ClockSegments(val segments: Int) : MoveEffect
    data class StandingDelta(val delta: Int) : MoveEffect
    data class WarThreat(val threat: RawWarThreat) : MoveEffect
    data class QuestHook(val questSeed: RawQuestSeed) : MoveEffect
    data class ArmyRaised(val army: RawArmyDeployment) : MoveEffect
    data class CourtPCs(val standingDelta: Int) : MoveEffect
}

sealed interface OfferCard {
    data class WarThreat(val faction: String, val threat: RawWarThreat) : OfferCard
    data class DiplomacyQuest(val faction: String, val questSeed: RawQuestSeed) : OfferCard
    data class FactionAction(val faction: String, val moveId: String, val description: String) : OfferCard
}
```

#### Core signatures (all pure, no Foundry deps)

```kotlin
// Main entry: advance all factions' agendas for one kingdom turn
fun advanceAllAgendas(
    groups: Array<RawGroup>,
    currentTurn: Int,
    moveCatalog: Map<String, RawFactionAgendaMove>,   // keyed by moveId
    archetypeTables: Map<String, List<String>>,       // archetype -> ordered moveIds
    rng: (Int) -> Int                                 // deterministic RNG: seed -> int
): Pair<Array<RawGroup>, List<FactionMove>>

// Advance a single group's agenda
fun advanceAgenda(
    group: RawGroup,
    allGroups: Array<RawGroup>,
    currentTurn: Int,
    moveCatalog: Map<String, RawFactionAgendaMove>,
    archetypeTables: Map<String, List<String>>,
    rng: (Int) -> Int
): AgendaAdvanceResult

// Pick a move from the archetype's weighted table, respecting cooldowns & valid targets
fun pickMove(
    agenda: RawFactionAgenda,
    allGroups: Array<RawGroup>,
    moveCatalog: Map<String, RawFactionAgendaMove>,
    archetypeTables: Map<String, List<String>>,
    rng: (Int) -> Int
): FactionMove?

// Apply a move's effect, return the updated agenda + any spawned offer cards
fun applyMove(
    move: FactionMove,
    agenda: RawFactionAgenda,
    group: RawGroup,
    allGroups: Array<RawGroup>,
    currentTurn: Int
): AgendaAdvanceResult

// When progress >= segments: complete goal, draw new goal, reset progress
fun completeGoal(
    agenda: RawFactionAgenda,
    group: RawGroup,
    allGroups: Array<RawGroup>,
    currentTurn: Int
): RawFactionAgenda
```

### 3.2 Determinism / Seeding Policy

**Constraint:** `TurnTickingEngine.tick()` is **preview-safe** — it must produce identical output for preview and commit. No `Date`, no `Math.random()`, no `kotlin.random.Random`.

**Decision:** The agenda engine runs **inside `TurnTickingEngine.tick()`** (monthly, End Turn). It receives a deterministic RNG function derived from the kingdom's persistent `turnSeed` (stored on `KingdomData` or derived from `currentTurn` + `kingdom.id`).

```kotlin
// In TurnTickingEngine.kt, before calling advanceAllAgendas:
val turnSeed = (kingdom.id.hashCode() * 31 + currentTurn).toInt()
val rng: (Int) -> Int = { salt -> turnSeed + salt * 1664525 + 1013904223 } // LCG, pure
```

- The RNG is **pure** (function `Int -> Int`), seeded per turn.
- All randomness (move selection, target picking) routes through this RNG.
- **Preview parity is guaranteed** because the same `currentTurn` + `kingdom.id` yields the same sequence.

> If future work needs day-scale faction ticks, a second RNG stream keyed by `worldTime` would live in `DailyTickEngine` — but **this feature only ticks monthly**.

### 3.3 Tick Surface

- **Monthly (End Turn)** → `TurnTickingEngine.tick()` calls `advanceAllAgendas()`.
- **Returns** `FactionAgendaChanges` added to `TickResult`:
  - `updatedGroups: Array<RawGroup>` (with advanced agendas + standing deltas)
  - `factionMoves: List<FactionMove>` (for offer cards + gazette)
  - `newWarThreatOffers: Int`, `newDiplomacyQuestOffers: Int` (counters for end-turn chat)
- **DailyTickHooks** — **NOT USED**. Faction agendas are kingdom-turn scale only.

---

## 4. Move Catalog per Archetype (Concrete Tables)

Weights are **relative**; the engine normalizes to 100%.

| Archetype | Move | Weight | Cooldown | Target | Effect | Gazette i18n key |
|-----------|------|--------|----------|--------|--------|------------------|
| **aggressive** (Pitax, Tiger Lords, Hargulka) | expand | 30 | 2 | self | ClockSegments(+1) | `kingdom.factionAgenda.gazette.expand` |
| | sabotage-rival | 25 | 3 | rival | StandingDelta(-10 to target) | `kingdom.factionAgenda.gazette.sabotage` |
| | raise-army | 20 | 4 | self | ArmyRaised(strength=level) | `kingdom.factionAgenda.gazette.raiseArmy` |
| | court-ally | 15 | 3 | ally | StandingDelta(+5 to target) | `kingdom.factionAgenda.gazette.courtAlly` |
| | court-pcs | 10 | 0 | pcs | CourtPCs(+5 standing) | `kingdom.factionAgenda.gazette.courtPCs` |
| **mercantile** (Mivon, Brevoy Houses, Varnhold) | expand | 20 | 2 | self | ClockSegments(+1) | ... |
| | court-ally | 30 | 2 | ally | StandingDelta(+8) | ... |
| | sabotage-rival | 15 | 3 | rival | StandingDelta(-5) | ... |
| | court-pcs | 25 | 1 | pcs | CourtPCs(+8) | ... |
| | raise-army | 10 | 5 | self | ArmyRaised(strength=level-1) | ... |
| **fey** (Narlmarches, First World courts) | court-ally | 25 | 2 | ally | StandingDelta(+10) | ... |
| | sabotage-rival | 20 | 3 | rival | StandingDelta(-8) | ... |
| | expand | 15 | 3 | self | ClockSegments(+1) | ... |
| | court-pcs | 25 | 0 | pcs | CourtPCs(+10) | ... |
| | raise-army | 15 | 4 | self | ArmyRaised(strength=level, fey=true) | ... |
| **political** (Brevoy, Restov, Noble Houses) | court-ally | 35 | 2 | ally | StandingDelta(+10) | ... |
| | court-pcs | 25 | 1 | pcs | CourtPCs(+10) | ... |
| | sabotage-rival | 20 | 3 | rival | StandingDelta(-8) | ... |
| | expand | 15 | 3 | self | ClockSegments(+1) | ... |
| | raise-army | 5 | 6 | self | ArmyRaised(strength=level-2) | ... |
| **monster** (Trolls, Bandits, Stag Lord remnants) | expand | 35 | 2 | self | ClockSegments(+1) | ... |
| | raise-army | 25 | 3 | self | ArmyRaised(strength=level) | ... |
| | sabotage-rival | 20 | 2 | rival | StandingDelta(-10) | ... |
| | court-ally | 10 | 4 | ally | StandingDelta(+5) | ... |
| | court-pcs | 10 | 0 | pcs | CourtPCs(+3) | ... |

**Archetype assignment:** Deterministic by `group.name` hash → one of the five. Stored in `agenda.archetype` so it persists and can be overridden by GM via dialog.

**Target selection:**
- `rival` = faction with lowest standing (most negative) among NPC groups (excluding `atWar=true`).
- `ally` = faction with highest standing (most positive) among NPC groups.
- `self` = the acting faction.
- `pcs` = the player kingdom (standing tracked on the PC's `RawGroup` equivalent).

**Cooldowns:** Decremented each turn; a move with `cooldownTurns > 0` cannot be picked until it reaches 0.

---

## 5. UI Design

### 5.1 Kingdom Sheet — Factions Section (extends existing Trade Agreements section)

**No new nav entry.** The Faction & Diplomacy Relations Tracker (#1) already added a Diplomacy section. We add an **Agenda** sub-tab/pane inside that section.

**Files:**
- `src/jsMain/resources/applications/kingdom/sections/diplomacy/agenda-tab.hbs` — new partial
- `src/jsMain/resources/applications/kingdom/sections/diplomacy/faction-agenda-card.hbs` — card for one faction's agenda
- `src/jsMain/kotlin/.../kingdom/sheet/contexts/DiplomacyContext.kt` — extend with `agendaContext: FactionAgendaContext`

**Context object:**

```kotlin
@JsPlainObject
external interface FactionAgendaCardContext {
    val groupName: String
    val attitude: FactionAttitude
    val standing: Int
    val agenda: AgendaDisplayContext?  // null if no agenda yet
    val isGM: Boolean
}

@JsPlainObject
external interface AgendaDisplayContext {
    val goalTitle: String
    val progress: Int
    val segments: Int
    val progressPct: Int          // 0-100
    val archetype: String
    val archetypeLabel: String    // localized
    val lastMove: String?         // last gazette line (for hint)
    val cooldowns: Record<String, Int>
}
```

**GM actions in the card:**
- "Set Goal" dialog (pick goalId, segments)
- "Adjust Progress" (±1 segment)
- "Pick Archetype" (override deterministic assignment)
- "Clear Agenda" (set to null → faction goes idle)

### 5.2 i18n Namespace

All keys under `kingdom.factionAgenda.*`:

```json
"kingdom": {
  "factionAgenda": {
    "title": "Faction Agendas",
    "archetype": { "aggressive": "Aggressive", "mercantile": "Mercantile", "fey": "Fey", "political": "Political", "monster": "Monster" },
    "move": { "expand": "Expand Territory", "sabotage-rival": "Sabotage Rival", "court-ally": "Court Ally", "raise-army": "Raise Army", "court-pcs": "Court the PCs" },
    "gazette": {
      "expand": "{{faction}} expands their influence ({{progress}}/{{segments}}).",
      "sabotage": "{{faction}} sabotages {{target}}.",
      "courtAlly": "{{faction}} courts {{target}}.",
      "raiseArmy": "{{faction}} raises an army.",
      "courtPCs": "{{faction}} sends envoys to the PCs."
    },
    "dialog": { "setGoal": "Set Agenda Goal", "adjustProgress": "Adjust Progress", "pickArchetype": "Choose Archetype", "clear": "Clear Agenda" }
  }
}
```

---

## 6. Chat / Offer Surfaces (GM-Confirmed Only)

Every externally-visible move produces **exactly one offer card** (whispered to GMs). The end-turn chat card (`end-turn.hbs`) gets a new section: **"Faction Moves"**.

### 6.1 Offer Card Types

| Move Effect | Offer Card | Buttons | Handler (ChatButtons.kt) |
|-------------|------------|---------|--------------------------|
| `WarThreat` | `km-offer-faction-war-threat` | [Spawn Threat] [Dismiss] | `ChatButton("km-offer-faction-war-threat")` → opens `AddWarThreat` prefilled |
| `QuestHook` | `km-offer-faction-diplomacy-quest` | [Create Quest] [Dismiss] | `ChatButton("km-offer-faction-diplomacy-quest")` → opens `AddQuest` prefilled |
| `ArmyRaised` | `km-offer-faction-army` | [Deploy Army] [Dismiss] | `ChatButton("km-offer-faction-army")` → opens `DeployArmy` prefilled |
| `StandingDelta` (NPC↔NPC) | `km-offer-faction-standing-shift` | [Confirm] [Dismiss] | `ChatButton("km-offer-faction-standing-shift")` → applies delta + log entry |
| `CourtPCs` | `km-offer-faction-court-pcs` | [Accept Envoy] [Decline] | `ChatButton("km-offer-faction-court-pcs")` → applies standing delta to PC faction |

### 6.2 Multiple Factions in One Turn

**Design: ONE digest card per turn** (not per-faction cards). The end-turn chat template renders a "Faction Moves" section with a list; each list item has its own button group. This avoids chat spam when 5+ factions move.

```hbs
{{#if factionMoves.length}}
<hr>
<h3>{{localizeKM "chatMessages.endTurn.factionMoves"}}</h3>
<ul>
  {{#each factionMoves}}
  <li>
    {{localizeKM this.gazetteKey data=this.gazetteData}}
    {{#if this.offerButton}}
    <button type="button" class="{{this.offerButton.class}}" data-{{this.offerButton.dataKey}}="{{this.offerButton.dataValue}}" data-kingdom-actor-uuid="{{../actorUuid}}">
      {{localizeKM this.offerButton.labelKey}}
    </button>
    {{/if}}
  </li>
  {{/each}}
</ul>
{{/if}}
```

### 6.3 Interaction with Existing War-Threat Offers

**Double-fire guard:** `FactionMove` carries `offerCard: OfferCard?`. The engine only emits an offer card when:

1. The move's effect **crosses a threshold** (e.g., standing drops into Hostile → war threat; rises into Friendly+ → diplomacy quest).
2. The target faction does **not already have a pending offer** of the same kind (checked via `RawWarThreat.offerConsumed` and a new `RawQuest.offerConsumed` flag).

**Code rule in `applyMove`:**

```kotlin
// War threat only if target faction attitude crosses into HOSTILE this tick
val beforeAttitude = attitudeFor(targetGroup.standing)
val afterAttitude = attitudeFor(applyStandingDelta(targetGroup.standing, move.effect.magnitude))
if (beforeAttitude != FactionAttitude.HOSTILE && afterAttitude == FactionAttitude.HOSTILE) {
    // Check existing war threats for this faction pair
    val existing = warThreats.find { it.enemyFaction == targetGroup.name && it.offerConsumed != true }
    if (existing == null) emit WarThreat offer
}
```

Same pattern for diplomacy quests (crosses into FRIENDLY+).

---

## 7. Gazette & Player Visibility

### 7.1 Gazette Line (GM + Players)

Every accepted move emits **one gazette line** via `formatTurnGazette`. Added to `TurnHistory.formatTurnGazette`:

```kotlin
// In formatTurnGazette, new factionMoves parameter:
val factionGazette = factionMoves.map { move ->
    localize("kingdom.turnGazette.factionMove", mapOf(
        "faction" to move.actorFaction,
        "action" to localize(move.gazetteKey),
        "target" to move.targetFaction ?: ""
    ))
}.joinToString(" | ")
```

**Player-facing:** The gazette line is **public** (appears in Recent Turns, session-prep journal export). It says *what happened* but not *why* (no internal weights, cooldowns, or GM-only context).

### 7.2 Faction Board Hints (GM-only)

In the Diplomacy section's faction card, show a one-line hint:
> "Last turn: *Expanded territory (3/6)* — *Sabotaged Mivon*"

Controlled by `isGM` in `FactionAgendaCardContext`.

---

## 8. Interactions with Existing Systems

| System | File(s) | Interaction |
|--------|---------|-------------|
| **Faction Relations Tracker** | `FactionRelations.kt`, `RawGroup.kt` | Standing deltas from `sabotage-rival` / `court-ally` / `court-pcs` route through `applyStandingDelta` → threshold hooks (`shouldOfferWarThreat`, `shouldOfferDiplomacyQuest`) fire naturally. |
| **Army & War Pressure** | `RawWarThreat.kt`, `TurnTickingEngine.kt` | `raise-army` move → `ArmyRaised` offer → GM clicks → `AddWarThreat` / `DeployArmy` creates `RawWarThreat` + `RawArmyDeployment`; war pressure recalculated next tick. |
| **Quest/Event Generator** | `questevent/`, `AddQuest.kt` | `court-ally` / `court-pcs` / `sabotage-rival` can emit `QuestHook` → GM clicks → `AddQuest` prefilled with faction as giver. |
| **Turn History Gazette** | `TurnHistory.kt` | `formatTurnGazette` gains `factionMoves: List<FactionMove>` param; renders public lines. |
| **Companion Expeditions** | `ExpeditionResolution.kt` | Diplomacy expeditions already write standing deltas; they now *also* tick the target faction's agenda progress (reciprocal pressure). |
| **Daily Tick** | `DailyTickHooks.kt` | **No interaction** — faction agendas are monthly only. |
| **Homebrew Profiles** | `HomebrewProfileManager.kt` | Archetype weights can be overridden per profile (future enhancement, out of scope). |

### 8.1 Explicit OUT-OF-SCOPE

- **No tactical map movement** for faction armies (army tokens stay in `DeployArmy`/`ResolveBattle`).
- **No faction-vs-faction battle resolution** (armies only fight PCs or sit as pressure).
- **No dynamic hex ownership change** (hex claiming stays a PC activity).
- **No faction internal politics** (succession, civil war) — single agenda per faction.
- **No PC faction agenda** (PCs have kingdom activities; they don't need an auto-agenda).
- **No cross-kingdom persistence** — each kingdom actor tracks its own faction agendas.

---

## 9. Test Plan

### 9.1 commonTest (pure logic, JVM-less)

**File:** `src/commonTest/kotlin/.../data/kingdom/FactionAgendaEngineTest.kt`

| Test | Description |
|------|-------------|
| `pickMove_respectsWeights` | Run 10000 picks; verify distribution matches weight ratios ±2%. |
| `pickMove_respectsCooldown` | Move with cooldown=2 cannot be picked 2 turns in a row. |
| `pickMove_rivalTargetSelectsLowestStanding` | With 3 NPC factions, sabotage-rival picks the most hostile. |
| `advanceAgenda_progressIncrements` | `expand` move adds 1 segment; at >= segments, `newGoal` non-null. |
| `applyMove_standingDeltaRoutesThroughApplyStandingDelta` | Standing changes clamp at ±100 and append log entry. |
| `applyMove_warThreatOnlyOnCrossing` | Hostile→Hostile = no offer; Unfriendly→Hostile = offer. |
| `applyMove_diplomacyQuestOnlyOnCrossing` | Indifferent→Friendly = offer; Friendly→Helpful = no offer. |
| `advanceAllAgendas_deterministicSameSeed` | Same turn + same kingdom id → identical move sequence. |
| `completeGoal_drawsNewGoalFromArchetype` | New goalId comes from archetype's goal pool. |

### 9.2 jsTest (Foundry-integrated)

**File:** `src/jsTest/kotlin/.../kingdom/FactionAgendaEngineTest.kt`

| Test | Description |
|------|-------------|
| `tick_integration_endTurnAdvancesAgendas_` | Full `TurnTickingEngine.tick()` with 3 factions produces `factionMoves` in `TickResult`. |
| `previewCommitParity` | Preview tick and commit tick produce identical `factionMoves` and `updatedGroups`. |
| `offerCardsEmittedCorrectly` | Crossing Hostile/Friendly thresholds increments `warThreatOffers`/`diplomacyQuestOffers`. |
| `gazetteIncludesFactionMoves` | `formatTurnGazette` output contains faction move lines. |
| `doubleFireGuard` | Two consecutive turns crossing same threshold only fires offer once. |

### 9.3 Manual Foundry Verification Checklist

1. Start a kingdom with 4 pre-seeded groups (Pitax, Mivon, Brevoy, Narlmarches).
2. Open Kingdom Sheet → Diplomacy section → Agenda sub-tab. All 4 show agendas with progress 0/6.
3. Click **End Turn** → chat card shows "Faction Moves" section with 2–4 buttons.
4. Click a **war threat** button → `AddWarThreat` dialog opens prefilled → Save → threat appears on Army board.
5. Click a **diplomacy quest** button → `AddQuest` dialog opens prefilled → Save → quest appears in Quests.
6. Click a **standing shift** button → standing updates, log entry appears, faction card updates.
7. Run 3 more turns → verify cooldowns prevent repeat moves, progress clocks advance, new goals draw.
8. Check **Recent Turns** recap → gazette lines for faction moves are present and player-visible.
9. Reload world → agendas, progress, cooldowns persist.
10. Toggle a faction's archetype via GM dialog → subsequent turns use new weight table.

---

## 10. Phasing (2–5 Independently Committable Phases)

Each phase = one kanban worker card, sized for ~1–2 days.

| Phase | Title | Deliverable | Key Files |
|-------|-------|-------------|-----------|
| **1** | **Data Model + Migration** | `RawFactionAgenda`, `RawFactionAgendaMove`, `RawGroup.agenda` field, `MigrationNN`, move catalog JSON (`data/faction-agenda-moves.json`), archetype assignment util. | `RawFactionAgenda.kt`, `RawFactionAgendaMove.kt`, `RawGroup.kt`, `MigrationNN.kt`, `data/faction-agenda-moves.json`, `Defaults.kt` |
| **2** | **Pure Engine (commonMain)** | `FactionAgendaEngine.kt` with all pure signatures, deterministic RNG, unit tests (`FactionAgendaEngineTest.kt`). | `FactionAgendaEngine.kt`, `FactionAgendaEngineTest.kt` |
| **3** | **TurnTickingEngine Integration** | Wire `advanceAllAgendas` into `TurnTickingEngine.tick()`, extend `TickResult` with `factionMoves`, `updatedGroups`, offer counters; update `performEndTurn` to post offer cards + gazette. | `TurnTickingEngine.kt`, `TurnWizardApplication.kt`, `TurnHistory.kt`, `ChatButtons.kt` (new handlers), `end-turn.hbs`, `war-threat-arrival-offer.hbs` (reuse pattern) |
| **4** | **UI — Agenda Tab & GM Dialogs** | Diplomacy section Agenda sub-tab, faction agenda cards, GM dialogs (Set Goal, Adjust Progress, Pick Archetype, Clear), i18n keys. | `DiplomacyContext.kt`, `agenda-tab.hbs`, `faction-agenda-card.hbs`, `ModifyFactionAgenda.kt` (new dialog), `lang/en.json` |
| **5** | **Offer Card Handlers + QA** | Implement 5 `ChatButton` handlers (`km-offer-faction-war-threat`, `km-offer-faction-diplomacy-quest`, `km-offer-faction-army`, `km-offer-faction-standing-shift`, `km-offer-faction-court-pcs`), double-fire guards, full jsTest + manual checklist. | `ChatButtons.kt`, `expedition-result.hbs` (pattern), new offer templates `chatmessages/faction-*.hbs`, `FactionAgendaEngineTest.kt` (jsTest), manual verify |

**Total: 5 phases.** Phase 1–2 can run in parallel (data vs. pure logic). Phase 3 depends on 2. Phase 4 depends on 1. Phase 5 depends on 3.

---

## 11. Open Questions for Gregory

1. **Archetype assignment:** Hash-based deterministic (this plan) vs. GM-assigned at creation? Hash is zero-config; GM override via dialog is still possible.
2. **Goal pools per archetype:** Should we ship a fixed list of 3–5 goals per archetype (e.g., aggressive: "Conquer Neighbor", "Build Army", "Raid Trade Routes") or keep it open-ended with a single generic "Expand" goal? Fixed list gives better gazette flavor.
3. **PC faction standing target:** `court-pcs` moves standing on the *PC kingdom's* implicit group. Do we need a `RawGroup` for the PCs, or reuse the existing `standing` on the kingdom actor? (Currently PCs don't have a `RawGroup` — standing is tracked on each NPC group only.)
4. **Gazette verbosity:** One line per move (this plan) or aggregate per faction ("Pitax: expanded, raised army")? Single line per move is more chronological; aggregate is cleaner.
5. **Cooldown persistence:** `moveCooldowns` on `RawFactionAgenda` survives save/load. Good? Yes — prevents move spam across sessions.

---

## 12. Appendix: Move Catalog JSON (data/faction-agenda-moves.json)

```json
{
  "moves": [
    { "id": "expand", "label": "kingdom.factionAgenda.move.expand", "weight": 1, "cooldownTurns": 2, "validTargets": "self", "effect": "clock", "effectMagnitude": 1, "requires": null },
    { "id": "sabotage-rival", "label": "kingdom.factionAgenda.move.sabotageRival", "weight": 1, "cooldownTurns": 3, "validTargets": "rival", "effect": "standing-delta", "effectMagnitude": -10, "requires": "not-at-war" },
    { "id": "court-ally", "label": "kingdom.factionAgenda.move.courtAlly", "weight": 1, "cooldownTurns": 2, "validTargets": "ally", "effect": "standing-delta", "effectMagnitude": 8, "requires": null },
    { "id": "raise-army", "label": "kingdom.factionAgenda.move.raiseArmy", "weight": 1, "cooldownTurns": 4, "validTargets": "self", "effect": "war-threat", "effectMagnitude": 0, "requires": "has-hex" },
    { "id": "court-pcs", "label": "kingdom.factionAgenda.move.courtPCs", "weight": 1, "cooldownTurns": 0, "validTargets": "pcs", "effect": "standing-delta", "effectMagnitude": 5, "requires": null }
  ],
  "archetypes": {
    "aggressive": { "expand": 30, "sabotage-rival": 25, "raise-army": 20, "court-ally": 15, "court-pcs": 10 },
    "mercantile": { "expand": 20, "sabotage-rival": 15, "court-ally": 30, "court-pcs": 25, "raise-army": 10 },
    "fey": { "expand": 15, "sabotage-rival": 20, "court-ally": 25, "court-pcs": 25, "raise-army": 15 },
    "political": { "expand": 15, "sabotage-rival": 20, "court-ally": 35, "court-pcs": 25, "raise-army": 5 },
    "monster": { "expand": 35, "sabotage-rival": 20, "court-ally": 10, "court-pcs": 10, "raise-army": 25 }
  },
  "goals": {
    "aggressive": ["conquer-neighbor", "build-army", "raid-trade-routes"],
    "mercantile": ["secure-trade-route", "monopoly-resource", "ally-major-power"],
    "fey": ["claim-grove", "bind-mortal", "veil-territory"],
    "political": ["marriage-alliance", "treaty-network", "court-favor"],
    "monster": ["expand-lair", "gather-horde", "sack-settlement"]
  }
}
```

> Weights in `archetypes` are relative; the engine normalizes. `effectMagnitude` in the move entry is the **base**; archetype can override via a multiplier (e.g., aggressive `raise-army` magnitude ×1.5) — implemented in `applyMove`.

---

**End of Plan.** Ready for review. Upon approval, implementation cards will be created per the phasing table above.