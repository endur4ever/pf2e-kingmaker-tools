# Plan: NPC memory ledger — named NPCs remember what the kingdom did

Card: `t_0169a788`. Parent: `t_7a8a72b4` (`2026-07-09-plan-settlement-life.md`).

## 1. Problem statement

Every settlement carries a `RawPopulationRoster` of named residents with occupations, and none of
them ever notices anything. The kingdom floods, wars, prospers and expands; Svetlana the Innkeeper is
the same inert row she was forty turns ago.

Turn history already records what happened. This plan lets a **small, GM-chosen** set of roster NPCs
accumulate memories from that history, shifting a personal attitude that at thresholds emits a
GM-confirmed offer — the guildmaster who has watched three caravans raided arrives at court.

## 2. What is actually matchable today

The card asks this first, and it decides the whole feature: **`RawTurnRecord` stores per-turn
aggregate state, not discrete events.** Its complete field list is:

`turn`, `timestamp`, `fame`, `resourcePoints`, `consumption`, `unrest`, `xpAwarded`, `clockEvents`,
`warPressure`, `pressurePerTurn`, `notes`, `playerNotes`, `level`, `size`, `ruinCorruption`,
`ruinCrime`, `ruinDecay`, `ruinStrife`.

### 2.1 Matchable from `RawTurnRecord`

Because the record is state, rules match **thresholds and deltas between consecutive records**, not
verbs.

| Signal | Basis |
| --- | --- |
| unrest crossed up / fell to 0 | `unrest` vs previous record |
| any ruin track worsened / all cleared | `ruinCorruption` / `ruinCrime` / `ruinDecay` / `ruinStrife` |
| the realm grew | `size` increased |
| the kingdom advanced | `level` increased |
| renown peaked | `fame` at its maximum |
| war pressure rose / was relieved | `warPressure` vs previous |
| lean year | `consumption` exceeded `resourcePoints` |
| a campaign clock fired | `clockEvents` contains an id |

`notes` and `playerNotes` are **free GM prose and are deliberately not matchable**. Pattern-matching
narrative text would fire on a turn of point-of-view and is exactly the LLM-adjacent guessing the
card puts out of scope.

### 2.2 Matchable from other per-turn kingdom state

Turn records are not the only dated history. These carry a turn number and are equally usable:

| Signal | Source |
| --- | --- |
| a caravan was raided / delivered / recalled, and with whom | `kingdom.shipmentHistory` — `RawShipmentHistoryEntry { turn, partner, cargo, outcome, rdGained }` |
| a milestone was earned | `kingdom.milestones` — `MilestoneChoice.completed` |
| a war threat arrived | `kingdom.warThreats` — `status`, `triggeredTurn` |
| a quest was completed | `kingdom.quests` — `RawQuest.status` |

This is what makes "the caravan raids survived" from the card's concept genuinely available.

### 2.3 NOT recorded anywhere — would need new capture first

An earlier draft's headline template was `razed-forest`, matching:

```json
"matcher": { "hexTerrain": "forest", "kingdomAction": ["cleared", "burned", "razed"] }
```

**`hexTerrain`, `kingdomAction` and `farmOutput` do not exist** — not on `RawTurnRecord`, not
anywhere — yet they sat in a table captioned "subset of `RawTurnRecord` fields that are matchable
TODAY". The flagship example could never have fired.

Per-hex terrain and what was done to a hex live in the Kingmaker module's own state, which turn
history never captures. So these remain unavailable until something records them:

- which hex was cleared, razed or worksited, and its terrain
- festivals or specific structures being raised
- battles won "nearby" an NPC

A druid whose grove was logged is a good scene and is **out of scope until hex-event capture exists**,
which is its own card. Saying so is better than shipping a rule that silently never fires.

Its attitude deltas were keyed `"druid"`, `"ranger"`, `"logger"` as well. The occupation vocabulary
is `npcOccupations` (`Settlement.kt:34`), 45 values, capitalised: **`Druid` and `Logger` are not
among them.** `Ranger` and `Woodcutter` are.

## 3. Data model

### 3.1 Memories hang off the roster NPC

The parent plan already fixes the shape: it casts by `RawNpcEntry.id` and names this feature's seam
explicitly (`castNpcIds: Array<String> // roster RawNpcEntry.ids`). So memories belong on
`RawNpcEntry`, in `RawPopulationRoster` on `RawSettlement`.

```kotlin
// jsMain: kingdom/structures/RawSettlement.kt -- additive, nullable
external interface RawNpcEntry {
    var id: String
    var name: String
    var occupation: String
    var notes: String?

    /** GM opted this resident into memory tracking. Null/false = untracked. */
    var memoryTracked: Boolean?
    /** Oldest first. Null on legacy data. */
    var memoryLog: Array<RawNpcMemoryEntry>?
    /** Cached sum of deltas; null = never computed. */
    var attitudeScore: Int?
}

@JsPlainObject
external interface RawNpcMemoryEntry {
    var ruleId: String
    var turn: Int
    var delta: Int
    /** Interpolation values for the entry's i18n key, e.g. the partner's name. */
    var subject: String?
}
```

An earlier draft put `memoryLog` and `attitudeScore` on **`RawCharacter`** and justified it with
"`RawCharacter` … is already persisted … via `PopulationDialogs.kt` for roster NPCs". That is not
true: `PopulationDialogs.kt:50` constructs `RawNpcEntry`. `RawCharacter` is the companion type —
name, `actorUuid`, travel coordinates — and it **has no `id` field at all**, so a memory written
there could not be attributed to a resident. It would also have put this plan and its parent on
different NPC types, which the card exists to prevent.

**Migration.** Nullable additive fields on an interface nested inside `RawSettlement`. Every nullable
array on `KingdomData` is seeded by convention (`quests`/23, `companionExpeditions`/40,
`caravans`/44, `warThreats`/46, `shipmentHistory`/61), so this seeds too — but it walks
settlements → roster → npcs rather than a top-level field. **`Migration65`**; 62, 63 and 64 are
claimed by the downtime, scheduler and petition plans respectively.

### 3.2 Rule schema

`data/npc-memory-rules/*.json`, one file per rule, combined by the existing `CombineJsonFiles`
Gradle task, validated by a new `schemas/npc-memory-rule.json`.

**Build glue this needs (it is not automatic).** `CombineJsonFiles` *is* automatic — it walks
`data/` one level deep and emits `<dirname>.json` per subdirectory (`buildSrc/.../CombineJsonFiles.kt`),
so the new directory is bundled with no change. Validation is **not**: each validator is an
explicitly registered task, e.g.

```kotlin
tasks.register<JsonSchemaValidator>("validateMilestones") {
    outputs.upToDateWhen { true }
    schema = layout.projectDirectory.file("src/commonMain/resources/schemas/milestone.json")
    files = layout.projectDirectory.dir("data/milestones")
}
```

and is then named in the `check` task's `dependsOn` list (`build.gradle.kts:121-134`). So this
schema needs its own `tasks.register<JsonSchemaValidator>` plus a line in that list, or it is written
and never run.


```json
{
  "id": "caravan-raided",
  "source": "shipmentHistory",
  "match": { "outcome": "raided" },
  "entryKey": "npcMemory.caravanRaided.entry",
  "deltas": { "Merchant": -2, "Teamster": -2, "Guard": -1, "Soldier": 1 },
  "defaultDelta": 0,
  "cooldownTurns": 1
}
```

`source` is a **closed set** naming §2.1/§2.2 origins: `turnRecord`, `shipmentHistory`, `milestones`,
`warThreats`, `quests`. `match` keys are validated against that source's real fields by the schema, so
a rule cannot reference a field that does not exist — the failure this plan shipped.

`deltas` key on **`npcOccupations` values verbatim**, capitalisation included. An occupation absent
from `deltas` gets `defaultDelta`, so most residents are unmoved by most events, which is the point.

## 4. Starter rules

Seventeen, all using only §2.1 and §2.2 sources.

| id | source | fires when | who moves |
| --- | --- | --- | --- |
| `unrest-spike` | turnRecord | `unrest` rose ≥ 3 in one turn | Guard +1, Soldier +1, Merchant −2, Innkeeper −1 |
| `unrest-calmed` | turnRecord | `unrest` reached 0 from ≥ 5 | Merchant +2, Innkeeper +2, Priest +1 |
| `decay-worsens` | turnRecord | `ruinDecay` increased | Carpenter −2, Mason −2, Cobbler −1 |
| `crime-worsens` | turnRecord | `ruinCrime` increased | Merchant −2, Jeweler −2, Guard −1 |
| `corruption-worsens` | turnRecord | `ruinCorruption` increased | Scribe −2, Priest −2 |
| `strife-worsens` | turnRecord | `ruinStrife` increased | Priest −2, Healer −1 |
| `ruins-cleared` | turnRecord | all four ruins 0, having been nonzero | Priest +2, Healer +2, Mason +1 |
| `realm-expanded` | turnRecord | `size` increased | Farmer +1, Shepherd +1, Ranger +1, Miner +1 |
| `kingdom-advanced` | turnRecord | `level` increased | Scribe +2, Merchant +1 |
| `renown-peaked` | turnRecord | `fame` at maximum | Innkeeper +2, Tavern Keeper +2, Painter +1 |
| `war-looms` | turnRecord | `warPressure` increased | Soldier +1, Fletcher +1, Farmer −2, Shepherd −2 |
| `war-relieved` | turnRecord | `warPressure` reached 0 | Farmer +2, Shepherd +2, Soldier −1 |
| `lean-year` | turnRecord | `consumption` exceeded `resourcePoints` | Farmer −2, Miller −2, Baker −2, Butcher −1 |
| `clock-fired` | turnRecord | `clockEvents` contains the rule's id | Scribe +1 |
| `caravan-raided` | shipmentHistory | `outcome == raided` | Merchant −2, Teamster −2, Guard −1, Soldier +1 |
| `caravan-delivered` | shipmentHistory | `outcome == delivered` | Merchant +1, Teamster +1, Brewer +1 |
| `milestone-earned` | milestones | a milestone completed this turn | Priest +1, Scribe +1, Painter +1 |

Every occupation named is in `npcOccupations`, verbatim.

## 5. Attitude model

**Scale −50…+50**, clamped, starting at 0. Bands: `≤ −25` hostile, `−24…−10` unfriendly,
`−9…9` indifferent, `10…24` friendly, `≥ 25` helpful.

**Decay:** 1 point toward 0 per turn, only for NPCs with no memory formed that turn. Without it a
single bad decade fixes an NPC's opinion permanently; with it, a grudge fades unless renewed.

**No double-counting with faction standing.** Faction standing moves for *the faction*, and
`FactionRelations` drift already applies it to every member. An NPC's attitude is **personal history
only**: no rule reads or writes `RawGroup.standing`, and no faction drift touches `attitudeScore`.
Where both exist, the UI shows them as two separate lines rather than a sum — they answer different
questions ("does Pitax like us" vs "does this guildmaster").

## 6. Tracking, caps and retention

All three were absent from the earlier draft; all three are mandated.

**Flagging.** A **Track memory** toggle on the roster entry in `PopulationDialogs`, writing
`memoryTracked`. Untracked NPCs are skipped entirely — no log, no attitude, no cost.

**Cap: `MAX_TRACKED_NPCS = 10` across the whole kingdom.** The toggle refuses beyond it and says
which NPCs are tracked. Ten is a cast; forty is a spreadsheet, and every tracked NPC is evaluated
against every rule every turn.

**Retention: `MEMORY_LOG_CAP = 30` entries per NPC**, oldest trimmed first. `attitudeScore` is a
**running total and is NOT recomputed from the log**, so trimming an old memory does not silently
revise an NPC's opinion. Untracking an NPC keeps the log — retracking resumes a history rather than
starting a stranger.

**Cooldown.** `cooldownTurns` per rule per NPC, so a war that raises pressure for six straight turns
does not write six identical memories.

## 7. Offers

Crossing a band boundary — not every delta — posts one GM-whispered card,
`chatmessages/npc-attitude-shift.hbs`: the NPC, their settlement, the new band, and the memories that
moved them.

Buttons `km-offer-npc-encounter`, `km-offer-npc-quest`, `km-offer-npc-note`: spawn an encounter,
spawn a quest through the existing generator, or write a scene note and dismiss. A fourth path is
just closing the card. Handlers begin `if (!game.user.isGM) return`.

Band crossings are **edge-triggered**: an NPC sitting at hostile does not re-offer every turn.

## 8. UI

**GM-only, on the roster entry.** Players learn an NPC's feelings by playing, not by reading a score.

| Piece | Path |
| --- | --- |
| Log panel | `applications/kingdom/npc-memory-log.hbs` (inside the existing NPC edit dialog) |
| Context | `kingdom/sheet/contexts/NpcMemoryContext.kt` |
| Offer card | `chatmessages/npc-attitude-shift.hbs` |
| i18n | `pf2e-kingmaker-tools.kingdom.npcMemory.*` |

`NpcMemoryContext` is populated only when `game.user.isGM` — not merely hidden in the template, since
players are OWNERs of the party actor. Entry text uses **literal** i18n keys mapped from `ruleId` in
a `when`; `t("npcMemory.$ruleId.entry")` is invisible to `check_i18n_keys.py` and would ship as a raw
key with every guard green. Because rules are data-driven, extend that guard with a check that every
id in `data/npc-memory-rules/` has its entry key in all eight locales.

## 9. Interactions and out of scope

**Reads:** `RawTurnRecord` history, `kingdom.shipmentHistory`, `milestones`, `warThreats`, `quests`,
the population roster.
**Writes:** `RawNpcEntry.memoryTracked` / `memoryLog` / `attitudeScore`, and on confirm the encounter,
quest or note the GM chose.

**Shared with the parent plan:** both address residents by `RawNpcEntry.id` and neither mutates the
roster's identity fields. Settlement life casts NPCs into scene slots; this records what they
remember. They may reference the same NPC and must not fight over the row.

**Out of scope:** LLM prose of any kind — entries are template text only; matching `notes`;
per-hex or terrain-based memories until hex-event capture exists (§2.3); player-visible attitude;
NPC-initiated action without a GM offer; memories for companions (`RawCharacter`) — a different type
with a different purpose.

## 10. Test plan

**commonTest** (`NpcMemoryEngineTest`)
- Delta rules fire on the transition and not on the steady state: unrest 2→6 fires `unrest-spike`,
  6→6 does not.
- `ruins-cleared` requires all four at 0 **and** a nonzero predecessor.
- An occupation absent from `deltas` receives `defaultDelta`, not the first entry.
- `cooldownTurns` suppresses a repeat for the same NPC while allowing a different NPC's.
- Attitude clamps at ±50 and never exceeds it however many memories accumulate.
- Decay moves toward 0 only on a turn with no new memory, and never past 0.
- Band crossing is edge-triggered: one offer on entering hostile, none while remaining there.
- `MEMORY_LOG_CAP` trims oldest first and leaves `attitudeScore` unchanged — the trim must not
  revise an opinion.
- `MAX_TRACKED_NPCS` refuses the eleventh.
- Untracked NPCs produce nothing at all.
- A rule naming an unknown `source` is skipped, and the other rules still evaluate.

**jsTest** — Raw↔model round trip preserving all three nullable fields; `Migration65` walking
settlements→roster→npcs and idempotent on a second run; rule JSON parsed against the schema;
`NpcMemoryContext` null for a non-GM.

**Mutation-check every new test**: make the delta comparison `>=`, ignore cooldown, recompute
attitude from the trimmed log, drop the tracked check — and confirm the mutation *compiled* before
believing a "survived" result.

**Manual Foundry checklist**
1. Track three residents; try to track an eleventh → refused with the current list named.
2. End Turn with unrest rising 3 → only tracked NPCs of the named occupations gain the memory.
3. End Turn again with unrest flat → no new memory; attitude decays 1 toward 0.
4. Raid a caravan → the merchant and teamster remember it; the soldier moves the other way.
5. Push an NPC across into hostile → one offer card; next turn, still hostile, no second card.
6. Confirm the quest option → a quest spawns; dismiss on another → nothing changes.
7. Untrack then retrack an NPC → the log is intact.
8. Log in as a player → no memory panel, no attitude anywhere.

## 11. Phasing

**Phase 1 — pure core.** `NpcMemory.kt` in `commonMain`: rule evaluation over a state-delta input,
attitude accumulation, clamping, decay, cooldown, band crossing, caps. Full commonTest suite. Nothing
wired.

**Phase 2 — data and schema.** `schemas/npc-memory-rule.json`, the seventeen rules, the
`check_i18n_keys.py` catalog check, and the `RawNpcEntry` fields with `Migration65`.

**Phase 3 — tick.** End Turn evaluation over tracked NPCs, reading §2.2 sources alongside turn
records; memories written, no UI.

**Phase 4 — flagging, log panel and offers.** The Track toggle with its cap, the GM-only log,
`npc-attitude-shift.hbs` and its three buttons, i18n across all eight locales.

Phases 1–3 are invisible to players, which is what makes phase 4 safe.
