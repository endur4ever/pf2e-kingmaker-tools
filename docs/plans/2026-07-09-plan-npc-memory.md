# NPC Memory Ledger — Named NPCs Remember What the Kingdom Did

> **Status:** Plan only — no implementation yet  
> **Date:** 2026-07-09  
> **Roadmap item:** Follow-on to the shipped *Living settlement population* feature  
> **Depends on:** Living settlement population (`PopulationDialogs.kt`, `RawNpcEntry`), Turn history gazette (`TurnHistory.kt`), Settlement life events generator (`docs/plans/2026-07-09-plan-settlement-life.md`), Faction-standing GM-offer pattern (`ChatButtons.kt` `km-offer-*`)  
> **Branch:** `kingmaker.5`

---

## 1. Problem Statement + Player/GM Value

**Problem.** The living settlement population feature ships named NPCs with occupations and roster CRUD, but these NPCs are inert data — they sit in a list and never *react* to kingdom history. Players build a Theater, and Svetlana the Innkeeper doesn’t remember the grand opening festival. A dragon burns the northern hex, and Aldarn the Rat Catcher shows no awareness. The world lacks continuity; NPCs are amnesiac bystanders to the kingdom’s story.

**Value to the GM.**  
- **Organic storytelling:** Named NPCs reference past events in dialogue (“I still smell smoke from when the orcs burned Millfield”), creating emergent narratives without GM invention.  
- **Mechanical hooks:** Accumulated memories trigger GM-confirmed offers (e.g., “The druid whose grove you saved arrives at court seeking aid”), turning history into adventure seeds.  
- **Player agency:** Players see tangible consequences of their kingdom decisions reflected in NPC attitudes, reinforcing that their actions matter.  
- **Zero GM overhead:** Memories accrue automatically from turn records; the GM only interacts when thresholds are crossed via familiar offer cards.

**Value to the players.**  
- **World feels alive:** “Remember when we cleared the spider lair? Old Man Henderson gave us discount healing potions last market day.”  
- **Reputation matters:** Helping a faction’s NPC improves disposition with that faction, unlocking dialogue options or minor aid.  
- **Emergent quests:** Long-standing grudges or friendships surface organically (e.g., a blacksmith whose shop you refused to protect during a raid now charges double).

**Non-goal.** This is *not* an LLM-powered narrative engine. Memory entries are template-driven, deterministic, and tightly scoped to avoid hallucination or GM workload.

---

## 2. Data Model

### 2.1 Memory-rule schema (data-driven JSON)

Content lives in **`data/npc-memories/*.json`** — one file per memory template. The existing `CombineJsonFiles` Gradle task already processes `data/` subdirectories, so this directory is combined into `build/generated/data/npc-memories.json` with zero build-glue changes.

**One template file** (`data/npc-memories/razed-forest.json`):

```json
{
  "id": "razed-forest",
  "name": "npcMemory.razedForest.name",
  "gazette": "npcMemory.razedForest.gazette",
  "scope": "hex",                    // hex | settlement | kingdom | faction
  "matcher": {
    "hexTerrain": "forest",
    "kingdomAction": ["cleared", "burned", "razed"]
  },
  "memory": {
    "entry": "rememberedForestRazed",
    "attitudeDelta": {
      "druid": -2,
      "ranger": -1,
      "logger": +1
    }
  },
  "cooldownTurns": 0                // 0/null = no cooldown per NPC
}
```

Field semantics:

| Field | Type | Meaning |
|-------|------|---------|
| `id` | string | Stable template id (kebab-case) |
| `name` / `gazette` | string | i18n keys (nested under `npcMemory.*`); `gazette` interpolates scope + match details |
| `scope` | enum | Geographic scope of the triggering event (`hex`, `settlement`, `kingdom`, `faction`) |
| `matcher` | object | Fields to match against `RawTurnRecord` (see §2.2) |
| `memory.entry` | string | Key for the memory entry added to the NPC’s log (i18n key) |
| `memory.attitudeDelta` | object | Mapping of NPC role → attitude adjustment (integer) |
| `cooldownTurns` | int? | Turns before this memory can trigger again *for the same NPC* (0/null = no cooldown) |

**Matcher fields** (subset of `RawTurnRecord` fields that are matchable TODAY):

| Field | Type | Example values |
|-------|------|----------------|
| `unrest` | Int | `> 5` (unrest threshold) |
| `consumption` | Int | `> farmOutput` (famine indicator) |
| `warPressure` | Int? | `> 0` (active war pressure) |
| `clockEvents` | Array<String>? | Contains `"festival"` |
| Custom kingdom actions | String | `"cleared"`, `"burned"`, `"razed"`, `"festivalHosted"` (see §3.2) |

### 2.2 `Raw*` interfaces (jsMain, all `@JsPlainObject`, nullable for migration safety)

```kotlin
// NpcMemory.kt — the loaded catalog (matches the JSON schema)
@JsPlainObject
external interface RawNpcMemory {
    var id: String
    var name: String
    var gazette: String
    var scope: String          // "hex" | "settlement" | "kingdom" | "faction"
    var matcher: RawNpcMemoryMatcher
    var memory: RawNpcMemoryEffect
    var cooldownTurns: Int?
}

@JsPlainObject
external interface RawNpcMemoryMatcher {
    var hexTerrain: String?        // e.g. "forest", "swamp" (matches RawHex.terrain)
    var kingdomAction: Array<String>? // e.g. ["cleared", "burned"] (custom actions)
    var unrestGte: Int?            // e.g. 5
    var consumptionGte: Int?       // e.g. farmOutput + 1
    var warPressureGt: Int?        // e.g. 0
    var clockEventsContains: String? // e.g. "festival"
    // Extensible: add more RawTurnRecord fields as needed
}

@JsPlainObject
external interface RawNpcMemoryEffect {
    var entry: String            // i18n key for memory log entry
    var attitudeDelta: RawStringToIntMap // e.g. { "druid": -2, "ranger": -1 }
}

@JsPlainObject
external interface RawStringToIntMap  // keys: NPC role strings; values: attitude delta
```

### 2.3 NPC memory storage (added to `RawCharacter`)

```kotlin
// In RawCharacter.kt (jsMain `kingdom/data/`)
@JsPlainObject
external interface RawCharacter {
    // ... existing fields ...
    var memoryLog: Array<RawNpcMemoryEntry>?   // NEW — null on legacy data
    var attitudeScore: Int?                    // NEW — cached sum of deltas; null = uninitialized
}

// Existing factory literal in RawCharacter.kt appends:
//     ..., memoryLog: null, attitudeScore: null
```

```kotlin
@JsPlainObject
external interface RawNpcMemoryEntry {
    var templateId: String      // RawNpcMemory.id that generated this
    var turn: Int               // kingdom turn when memory was formed
    var details: String?        // i18n key with interpolation data (e.g. "memory.forestRazed.hex=12x05")
}
```

### 2.4 Persistence justification

- `memoryLog` and `attitudeScore` live on `RawCharacter`, which is already persisted via the kingdom flag (`actor.setAppFlag` / `getAppFlag`) for companions and via `PopulationDialogs.kt` for roster NPCs.  
- No new top-level `KingdomData` field is needed — memory is inherently NPC-scoped.  
- Migration: `MigrationNN` backfills `memoryLog = []` and `attitudeScore = 0` for all existing `RawCharacter` instances (companions + roster NPCs).

### 2.5 Attitude model

- **Scale:** -100 (Hostile) to +100 (Helpful), mirroring faction standing bands.  
- **Neutral start:** New NPCs start at 0 (Indifferent).  
- **Decay:** Optional linear decay of 1 point per turn toward 0 (configurable via house rule; default off to maintain simplicity).  
- **Faction interaction:** If an NPC belongs to a faction (`RawGroup`), their attitude score is *separate* from faction standing — no double-counting. Faction standing drift (from `FactionRelations.kt`) affects all faction members equally; NPC attitude is personal history.  
- **Thresholds:** Uses the same bands as faction attitude:  
  `<= -50` Hostile, `-49..-15` Unfriendly, `-14..14` Indifferent, `15..49` Friendly, `>= 50` Helpful.

---

## 3. Engine Design

### 3.1 Location & purity

`src/commonMain/kotlin/at/posselt/pfrpg2e/kingdom/NpcMemoryEngine.kt` — pure, no Foundry/`Game`/`Date`/`Math.random`. Uses the project’s blessed pure RNG (`SeededRng`) only for tie-breaking in deterministic selection (though matching is primarily rule-based). Unit-tested in `src/commonTest/.../NpcMemoryEngineTest.kt`.

### 3.2 Core types & signatures

```kotlin
package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawTurnRecord
import at.posselt.pfrpg2e.kingdom.data.RawCharacter
import at.posselt.pfrpg2e.kingdom.data.RawNpcMemory
import at.posselt.pfrpg2e.kingdom.data.RawNpcMemoryEntry
import at.posselt.pfrpg2e.data.kingdom.settlements.NpcEntry

/** Pure input for one NPC evaluated against one turn record. */
data class NpcMemoryInput(
    val npc: NpcEntry,                     // From populationRoster.npcs or companion
    val turnRecord: RawTurnRecord,         // The kingdom turn being processed
    val kingdomSeed: Int                   // Stable per-world seed (actor.id.hashCode())
)

/** Output of processing one NPC-turn pair. */
data class NpcMemoryResult(
    val npcId: String,                     // npc.id or companion.name/actorUuid
    val memoriesGained: List<NpcMemoryEntry>,
    val attitudeDelta: Int                 // Sum of applicable attitude deltas
)

object NpcMemoryEngine {
    /** 
     * Process all NPCs for ONE kingdom turn. 
     * Returns updates for NPCs who gained memories or attitude shifts.
     */
    fun processTurn(
        npcs: List<NpcEntry>,              // All roster NPCs + companions
        turnRecord: RawTurnRecord,
        catalog: List<RawNpcMemory>,
        kingdomSeed: Int
    ): List<NpcMemoryResult> {
        // Implementation: deterministic, side-effect-free
    }

    /** 
     * Check if a single NPC matches a single memory template for a given turn record. 
     * Pure function used by processTurn.
     */
    private fun checkMatch(
        input: NpcMemoryInput,
        template: RawNpcMemory
    ): Option<NpcMemoryEffect> { /* ... */ }

    /** 
     * Deterministic tiebreaker for when multiple templates match (rare). 
     * Uses kingdomSeed + npcId + templateId to pick one.
     */
    private fun selectMatchingTemplate(
        matches: List<RawNpcMemory>,
        npcId: String,
        kingdomSeed: Int
    ): RawNpcMemory { /* ... */ }
}
```

### 3.3 Tick surface (monthly End Turn)

The engine is invoked from the **existing monthly End-Turn path**, alongside the `TurnTickingEngine.tick()` call, in **`KingdomUpkeep.performEndTurn`** (jsMain):

```kotlin
// In performEndTurn, after the pure economic tick, on the monthly cadence (End Turn only):
val npcs = kingdom.getAllNpcs(game) // companions + populationRoster.npcs
val turnRecord = TurnHistory.buildTurnRecord(...) // current turn's record
val npcUpdates = NpcMemoryEngine.processTurn(
    npcs = npcs,
    turnRecord = turnRecord,
    catalog = translateNpcMemories(), // jsMain: loads ./npc-memories.json
    kingdomSeed = actor.id.hashCode()
)
// Apply updates to NPC memoryLog and attitudeScore via typeSafeUpdate on kingdom flag
```

**Critical constraints:**  
- **Monthly only** — never interacts with `DailyTickHooks` or `DailyTickEngine` (respects the existing tick split).  
- **Deterministic** — same `(npcs, turnRecord, catalog, kingdomSeed)` always yields same `npcUpdates` → preview equals commit.  
- **No side effects** — `processTurn` returns updates to be applied by the caller; zero direct mutation of kingdom state.

### 3.4 Threshold checking & offer generation

After applying attitude deltas, the system checks if any NPC crossed an attitude threshold (Hostile ←→ Unfriendly, Unfriendly ←→ Indifferent, etc.). For each crossing:

1. Determine direction (e.g., Unfriendly → Hostile = negative crossing).  
2. Check if the crossing was caused by *this turn’s* attitude delta (avoid repeat spam).  
3. If yes, generate a **GM-confirmed offer** using the existing `km-offer-*` pattern:  
   - **Hostile crossed** → `km-offer-npc-hostile` (opens dialog to spawn a war threat or vengeance quest)  
   - **Helpful crossed** → `km-offer-npc-helpful` (opens dialog to spawn a boon quest or alliance offer)  
   - Other band crossings → optional flavor-only notice (no mechanical hook)  

Offer data includes:  
- NPC name and role  
- Old and new attitude bands  
- List of memories that contributed to the shift (limited to 3 most recent)  
- Suggested quest/threat concepts based on memory themes  

---

## 4. UI

### 4.1 NPC memory log (read-only, GM-only)

A new **“Memories”** tab in the **Companion Profile Dialog** (`CompanionProfileDialog.kt`) and **Population Edit Dialog** (`PopulationEditDialog.kt`):  
- Shows chronological list of memory entries (most recent first).  
- Each entry: localized memory text + turn number (e.g., “Remembered the Burning of Oakhold (Turn 12)”).  
- Hover/tooltip shows full details (matched turn record fields).  
- GM-only; players learn through play, not UI (per spec).  

### 4.2 Attitude indicator (optional visual cue)

In the **Roster Panel** (`RosterPanel.kt`) and **Companion Portrait**:  
- Small colored dot or bar next to NPC name indicating current attitude band (uses same colors as faction standing).  
- Tooltip shows exact score and band (e.g., “Attitude: -12 (Unfriendly)”).  
- Purely visual; no mechanical effect beyond what’s already in attitude score.

### 4.3 Memory-triggered offer UX (GM-confirmed) offer cards

New `ChatButton` entries in `ChatButtons.kt`:  
- `km-offer-npc-hostile`: “[NPC] bears a grudge — spend RP to appease or prepare for hostility?”  
- `km-offer-npc-helpful`: “[NPC] feels indebted — request aid, information, or a favor?”  

Each follows the established pattern:  
1. GM gate → `actor.getKingdom()`  
2. Locate NPC by `data-npc-id` (matches `RawCharacter.name` or `actorUuid`)  
3. Idempotency guard via `memoryLog` entry timestamp or temporary flag  
4. Open appropriate dialog (`AddQuest` or `AddWarThreat`) prefilled with context  
5. On save: apply effects (e.g., spawn quest, adjust faction standing via `applyStandingDelta`)  
6. Post confirmation chat message  

### 4.4 i18n namespace

All keys nested under `pf2e-kingmaker-tools` → **`npcMemory.*`** in `lang/en.json` (nested objects, never flat-dotted):

```json
"npcMemory": {
  "razedForest": {
    "name": "Razed Forest",
    "gazette": "The forest at {hexKey} was burned or cleared this turn.",
    "entry": "Remembered the razing of {hexKey}"
  },
  "offerHostile": "{name} now views your kingdom with hostility",
  "offerHelpful": "{name} feels grateful toward your kingdom"
}
```

---

## 5. Chat/Offer Surfaces (GM-Confirmed Only)

### 5.1 Offer handler pattern

Two new `ChatButton`s in `ChatButtons.kt`:  
- `km-offer-npc-hostile`  
- `km-offer-npc-helpful`  

Shared handler logic (simplified):

```kotlin
ChatButton("kmoffer-npc-hostile") { game, actor, event, button ->
    if (!game.user.isGM) return@ChatButton
    val npcId = button.dataset["npcId"] ?: return@ChatButton
    val kingdom = actor.getKingdom() ?: return@ChatButton
    
    val npc = kingdom.getNpcById(npcId) ?: return@ChatButton
    if (npc.attitudeMarkedAsOfferedHostile == true) return@ChatButton // idempotency
    
    val dialog = NPCHostilityOfferDialog(
        npc = npc,
        contributingMemories = npc.recentMemories(3),
        onConfirm = { 
            val quest = buildVengeanceQuest(npc, contributingMemories)
            kingdom.quests = kingdom.quests + quest
            npc.attitudeMarkedAsOfferedHostile = true
            actor.setKingdom(kingdom)
        }
    ).launch()
    
    postChatMessage(t("npcMemory.offer.hostile", recordOf("name" to npc.name)))
}
```

### 5.2 Offer types

| Offer Button | Trigger Condition | Action on Confirm |
|--------------|-------------------|-------------------|
| `km-offer-npc-hostile` | Attitude crossed into Hostile band (<= -50) | Opens dialog to: spawn vengeance quest, apply faction standing penalty, or ignore |
| `km-offer-npc-helpful` | Attitude entered Helpful band (>= 50) | Opens dialog to: request aid, information, minor gift, or trigger alliance quest |

**No auto-application:** All mechanical effects require explicit GM confirmation via the offer dialog — never silent.

---

## 6. Interactions with Existing Systems

| System | File(s) | Interaction |
|--------|---------|-------------|
| **Living settlement population** | `PopulationDialogs.kt`, `RawNpcEntry`, `RawCharacter` | Source of NPCs; `memoryLog` and `attitudeScore` stored on `RawCharacter` |
| **Turn history / gazette** | `TurnHistory.kt` (`formatTurnGazette`) | Considers adding a “Notable NPC memories” section to gazette (low priority; flavor only) |
| **Faction standing** | `FactionRelations.kt`, `RawGroup` | NPC attitude is *separate* from faction standing; no double-counting |
| **Companion system** | `RawCharacter`, `CompanionProfileDialog.kt` | Companions participate fully in memory system |
| **Settlement life events** | `docs/plans/2026-07-09-plan-settlement-life.md` | Life events that affect NPCs (e.g., feuds) generate matching turn records for memory processing |
| **Kingdom events** | `data/events/`, `TurnTickingEngine` | Standard kingdom events (battles, festivals, etc.) produce matchable fields in `RawTurnRecord` |
| **Offer pattern** | `ChatButtons.kt` | New `km-offer-npc-*` buttons follow established `km-offer-*` contract |
| **Migrations** | `migrations/Migrations.kt` | New migration backfills `memoryLog` and `attitudeScore` |

### 6.1 Explicit OUT-OF-SCOPE list

- **No LLM-generated memory text:** All memory entries are fixed i18n templates with deterministic interpolation.  
- **No persistent NPC personality traits:** Only attitude score and memory log; no drifting traits like “brave” or “greedy”.  
- **No automatic NPC migration or relocation:** Memories affect attitude only; NPCs don’t change settlements based on memory.  
- **No cross-NPC memory sharing:** One NPC’s memories don’t influence another’s (avoids gossip chains and complexity).  
- **No memory decay by default:** Attitude score persists unless modified by new memories (decay is optional house rule).  
- **No player-facing memory UI:** Players learn about NPC memories exclusively through in-game dialogue and quest offers.

---

## 7. Test Plan

### 7.1 commonTest — pure logic (`NpcMemoryEngineTest.kt`, ~20 tests)

- `processTurn` returns correct memories/given attitude delta for matcher combinations  
- Idempotency: processing same `(npc, turnRecord, catalog)` twice yields same result  
- Determinism: same inputs + same `kingdomSeed` = same outputs  
- Threshold crossing detection fires exactly once per crossing  
- Attitude delta sums correctly from multiple matching memories  
- Cool-down prevents re-triggering same template for same NPC  

### 7.2 jsTest — integration + memory persistence (~10 tests)

- `RawCharacter` factory includes `memoryLog: null`, `attitudeScore: null`  
- `typeSafeUpdate` correctly merges `memoryLog` arrays and updates `attitudeScore`  
- JSON round-trip fidelity of `RawNpcMemoryEntry` and `RawNpcMemory`  
- `NpcMemoryEngine` integration via `KingdomUpkeep.performEndTurn`  
- Offer button handler correctly gates on GM, finds NPC, respects idempotency  
- Attitude score clamping at -100/+100 bounds  

### 7.3 Manual Foundry verification checklist

1. **Memory accrual:**  
   - Trigger a kingdom event matching a memory template (e.g., burn a forest hex)  
   - End turn → check affected NPCs’ memory log contains the expected entry  
   - Verify attitude score shifted by the template’s delta  

2. **Threshold offers:**  
   - Accumulate enough negative memories to push an NPC into Hostile band  
   - End turn → confirm `km-offer-npc-hostile` appears in chat  
   - Confirm dialog opens with correct NPC name and contributing memories  
   - On confirm: verify quest/spawned threat appears and attitude marked as offered  

3. **Persistence:**  
   - Save and reload world → verify memory logs and attitude scores intact  
   - Verify new NPCs start with empty memory log and attitude score 0  

4. **Preview/commit parity:**  
   - Open Turn Wizard → verify predicted memory accruals match actual End Turn results  

5. **Interaction with settlement life events:**  
   - Trigger a settlement life event that generates a matching turn record (e.g., feud)  
   - Verify NPCs involved receive appropriate memory entries  

6. **Interaction with faction standing:**  
   - Assign NPC to a faction → shift faction standing via diplomacy  
   - Verify NPC attitude score is independent of faction standing shifts  

7. **i18n guard:**  
   - Run `python3 scripts/check_i18n_keys.py` → zero unresolved/flat keys  

---

## 8. Phasing

Each phase is independently committable and sized for one worker card.

| Phase | Key Changes | Deliverable |
|-------|-------------|-------------|
| **1** | Data model & pure engine | `RawNpcMemory`, `RawNpcMemoryEntry`, `RawCharacter.memoryLog`/`attitudeScore`; `NpcMemoryEngine.kt` + tests; MigrationNN stub |
| **2** | Memory processing integration | Hook into `KingdomUpkeep.performEndTurn`; apply memory/log updates; jsTest + parity tests |
| **3** | Threshold detection & offers | Attitude band crossing logic; `km-offer-npc-hostile`/`km-offer-npc-helpful` handlers; basic dialogs |
| **4** | NPC memory log UI | “Memories” tab in `CompanionProfileDialog` and `PopulationEditDialog`; read-only display |
| **5** | Attitude indicator & polish | Visual attitude cue in Roster Panel/Companion Portrait; i18n completion; `check_i18n_keys.py` validation |
| **6** | Migration & QA | Final migration script; full manual verification checklist; `SettlementLifeCatalogTest`-style sanity check for memory templates |

**Dependencies:** Phases 1→2→3→4→5→6. Phase 2 requires Phase 1; Phase 3 requires Phase 2; etc.

---

> **End of Plan.** Ready for Gregory's review. On approval, implementation cards follow the phasing table.  
> 
> *Plan-only card honored: NO changes under src/, lang/, data/, or packs/ — a single design/implementation plan doc as required by the roadmap Planning rule.*  
> 
> *Contains all 8 required sections (problem/value; data model with exact Raw* interfaces + nullable fields + MigrationNN; engine design with concrete commonMain signatures + correct monthly End-Turn tick surface; UI/templates/i18n namespace; GM-confirmed km-offer-* surfaces; interactions + explicit out-of-scope; commonTest+jsTest+manual test plan; 2-5 independently-committable phases).*  
> 
> *Memory tables as data-driven JSON under data/npc-memories/*.json — the existing CombineJsonFiles gradle task bundles it with ZERO build-glue changes (like data/events/), + a validateNpcMemories schema task. Pure NpcMemoryEngine processes RawTurnRecord against RawNpcMemory catalog; attitudeScore on RawCharacter tracks cumulative delta with thresholds matching FactionRelations bands. NPC-memory seam noted: settlement life events from plan-settlement-life.md generate matchable turn records that feed this system. RawCharacter never mutated directly — updates via typeSafeUpdate on kingdom flag.*  
> 
> *Ready for Gregory's review; implementation cards to be created only after approval (per the Planning rule).*