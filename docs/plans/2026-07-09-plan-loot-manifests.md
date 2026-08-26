# Hex Loot Manifests & Treasure Ledger — Implementation Plan

> **Status:** Plan only — no implementation yet
> **Date:** 2026-07-09
> **Roadmap item:** New backlog, sibling of #13 (Balance & pacing alerts). Turns the pacing loot
> metric from an *estimate* into *measured* data.
> **Depends on:** Hex content system (`HexContentManager`, `RawHexContent`), Balance & Pacing
> Alerts (#13, `PacingAlerts.kt` — landed), Cleanse Item house rule (`CleanseItem.kt` — landed).
> **Sibling card:** `gap0709-cleanse-item` (cursed-item consumer — cross-linked in §5/§6).
> **Branch:** `kingmaker.5`

---

## Executive Summary

The house rules put loot discipline front and centre: *"Loot balance in the early levels … is way
above the recommendations in the CRB"* and *"Players can not buy items above their level in Restov …
you are going to imbalance fights for around half of the AP"* (`docs/house-rules.md:36,56`). Roadmap
#13 shipped a **loot-imbalance pacing alert** to police this — but its input is a **proxy**. Today
`TurnWizardApplication` feeds the alert `offendingSettlement.itemPurchaseLevel` (the highest level a
*settlement market* can sell) versus the party's target level. It never looks at the treasure the GM
actually handed out. Meanwhile the GM's real loot prep lives in freeform `gmNotes` on each hex, and
distribution after a clear is entirely manual: read the note, drag compendium items into the party
sheet, remember which were cursed, hope the pacing math noticed.

This feature closes that loop. Each **hex content entry** gains an optional **loot manifest** — a
list of `{ item UUID / pack ref, quantity, gp value, cursed flag, note }`. When the GM **clears** the
hex, the module posts a **GM-confirmed award offer** (never auto-applied). Clicking *Award to party*
moves the real items into the party stash (the `PF2EParty` inventory, exactly like caravan shipment
delivery), stamps a **treasure ledger** entry `{ turn, source hex, items, total gp, cursed count }`,
and — because the ledger now holds realized gp — **feeds the pacing loot metric measured data**
alongside the existing shop-access proxy. Cursed items land in the stash flagged, cross-linked to the
Cleanse Item ritual so players have a path to remove the curse.

The result: the GM preps loot where the encounter lives, distributes it with one click, and the
balance dashboard finally reflects what the party is *actually* carrying.

---

## 1. Problem Statement + Player / GM Value

**Problem.** Three gaps compound:

1. **Loot prep is unstructured.** Treasure lives in `RawHexContent.gmNotes` free text. Nothing is
   machine-readable, so nothing downstream can act on it.
2. **Distribution is manual and lossy.** After a clear the GM hand-drags items into the party sheet.
   Cursed items are tracked only in the GM's head. There is no record of *what was given, when, from
   where*.
3. **The pacing alert is guessing.** `evaluateLootImbalance` (§6) is fed *settlement purchase access*,
   not handed-out treasure. A party swimming in dropped magic items but shopping in a low-level town
   registers as perfectly balanced; the alert cannot see the actual wealth curve.

**Value to the GM.**
- Prep treasure *in the hex*, next to the encounter and player-facing text, with drag-drop from any
  compendium — no separate spreadsheet.
- One-click, GM-confirmed distribution on clear. Items land in the party stash; nothing is applied
  behind the GM's back.
- A durable **treasure ledger** — a wealth chronicle the GM can audit ("what did chapter 2 actually
  hand out?").
- Pacing alerts that watch *real* wealth-by-level, catching over-loot the settlement proxy misses.

**Value to the players.**
- Awarded items appear in the party inventory as real, usable equipment (correct quantity, price,
  level), not a chat line to transcribe.
- Cursed items arrive flagged with a pointer to the Cleanse Item ritual — a clear, fair path to
  remove downsides instead of a GM "gotcha".

---

## 2. Data Model

Architecture note: all persisted shapes are external `@JsPlainObject` interfaces with `var` fields
and auto-generated `.copy`, and **every new field is nullable** so pre-existing worlds load
unchanged (migration safety). This mirrors `RawHexContent` and `RawFactionStandingEntry`.

### 2.1 New interface — `RawLootManifestEntry`

Lives beside its owner in `src/jsMain/kotlin/.../kingdom/data/RawHexContent.kt` (or a new
`RawLootManifest.kt` in the same package):

```kotlin
@JsPlainObject
external interface RawLootManifestEntry {
    /** Foundry UUID of the source item (compendium or world). Preferred reference. */
    var itemUuid: String?
    /** Fallback compendium pack ref ("pack.collection.id") when no resolvable UUID exists. */
    var packRef: String?
    /** Display name captured at prep time, so the row renders even if the UUID later breaks. */
    var name: String?
    /** How many of this item the hex yields. Null => 1. */
    var quantity: Int?
    /** GP value per unit. Double preserves sp/cp fidelity (PF2e prices are not always whole gp). */
    var gpValue: Double?
    /** Marks this as a cursed item (drives the Cleanse Item cross-link + ledger cursed count). */
    var cursed: Boolean?
    /** Optional GM annotation ("hidden under the altar", "attuned to the lich"). */
    var note: String?
}
```

### 2.2 `RawHexContent` — ADDITIONS only (keep all existing fields)

```kotlin
// Existing: id, hexKey, type, name, visibility, gmNotes, playerText, suppressesEncounters,
//   pendingEncounter, travelModifier, linkedQuestId(s), linkedUuid(s), linkedWarThreatId, icon

// NEW — nullable for back-compat
var lootManifest: Array<RawLootManifestEntry>?   // the treasure prepped for this hex
var manifestAwarded: Boolean?                    // true once awarded — idempotency guard
var manifestAwardedTurn: Int?                    // kingdom turn the award fired (audit)
```

The `manifestAwarded` flag mirrors the existing `offerConsumed` / `pendingEncounter` idempotency
pattern (`ChatButtons.kt:210,236`) so a re-posted or double-clicked offer cannot double-grant.

### 2.3 New interfaces — the treasure ledger

```kotlin
// A snapshot of one item as it was awarded (denormalized — survives later item/hex deletion).
@JsPlainObject
external interface RawLootLedgerItem {
    var uuid: String?
    var name: String
    var quantity: Int
    var gpValue: Double        // per-unit gp at award time
    var cursed: Boolean
    var createdItemId: String? // id of the embedded item created on the party actor (undo hook)
}

// One award event = everything moved to the party from clearing one hex.
@JsPlainObject
external interface RawTreasureLedgerEntry {
    var id: String                        // "loot-<hexKey>-<Date.now()>"
    var turn: Int                         // kingdom turn at award
    var awardedAtWorldTime: Double?       // game.time.worldTime at award (chronology)
    var sourceHexKey: String
    var sourceContentId: String
    var sourceName: String                // hex content name, captured for the ledger table
    var items: Array<RawLootLedgerItem>
    var totalGp: Double                   // sum(quantity * gpValue) across items
    var cursedCount: Int
}
```

### 2.4 `KingdomData` — ADDITIONS only

`hexContents`, `pacingAlerts`, `quests`, `expeditionChronicle` already live here as nullable arrays
(`KingdomData.kt:221,279,203,304`). The ledger joins them:

```kotlin
var treasureLedger: Array<RawTreasureLedgerEntry>?   // append-only wealth chronicle (capped, §3.3)
var pacingLastRealizedLootImbalance: String?         // fire-once state for the NEW realized track (§6)
```

### 2.5 Persistence — where each thing lives, and why

| State | Location | Justification |
|-------|----------|---------------|
| **Manifest** (`lootManifest`) | Nested on `RawHexContent` | The manifest *describes* one hex's treasure. It belongs with the content it annotates — same lifecycle as `linkedUuids`/`gmNotes`, edited in the same `HexContentManager` dialog, saved through the same `actor.setKingdom(kingdom)` audit path. Co-location keeps prep and encounter together. |
| **Ledger** (`treasureLedger`) | **Kingdom flag** on `KingdomData` (top-level array) | The ledger must **outlive** the hex it came from — a GM can delete a hex content entry after clearing it, but the wealth record must persist for the pacing engine and audit. It is also **campaign-scoped**: the pacing consumer (`TurnWizardApplication`, End Turn) reads across *all* awards, not one hex. Storing it on `RawHexContent` would scope it wrong and lose it on deletion. Kingdom flag is the correct home — sibling to `pacingAlerts`. |

Not a camping flag, not a world setting: this is kingdom-scoped campaign state, consistent with every
other kingdom subsystem.

### 2.6 Migration — **implemented as `Migration65`**

**Implemented 2026-08-25 as `Migration65`** (`migrations/migrations/Migration65.kt`; `MigrationChainTest` asserts 17..65). It seeds only the ledger — the hex guard-flag writes this section originally sketched were null-to-null no-ops and were dropped. None of the six is implemented yet, so
whichever lands first should re-check the chain rather than trust these reservations.

```kotlin
class Migration65 : Migration(65) {
    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        // Ledger: initialise to empty so append is always safe.
        if (kingdom.treasureLedger == null) kingdom.treasureLedger = emptyArray<dynamic>()
        // New realized-loot pacing fire-once state.
        if (kingdom.pacingLastRealizedLootImbalance == null) kingdom.pacingLastRealizedLootImbalance = null
        // Hex contents: leave lootManifest absent (null = "no treasure prepped"); seed the guard flags.
        val contents = kingdom.hexContents
        if (contents != null) {
            for (i in 0 until (contents.length as Int)) {
                val c = contents[i]
                if (c.manifestAwarded == null) c.manifestAwarded = null
                if (c.manifestAwardedTurn == null) c.manifestAwardedTurn = null
            }
        }
    }
}
```

Register in the `Migrations.kt` `migrations` list. **Non-breaking**: absent `lootManifest` = the hex has
no treasure; an empty `treasureLedger` = nothing awarded yet. Follows the `Migration48` field-seed
shape exactly.

---

## 3. Engine Design (pure `commonMain` core)

All wealth/ledger math is **pure, Foundry-free** — same discipline as `PacingAlerts.kt` and
`CleanseItem.kt` — so it lives in `commonMain` and is fully unit-testable in `commonTest`. Foundry
item resolution (UUID → item object, party-stash write) is the only impure part and stays in
`jsMain` (§5).

New file: `src/commonMain/kotlin/at/posselt/pfrpg2e/kingdom/LootLedger.kt`

### 3.1 Totals & aggregation

```kotlin
data class LootTotals(
    val totalGp: Double,
    val itemCount: Int,
    val cursedCount: Int,
    val gpByTurn: Map<Int, Double>,   // for a per-chapter/turn wealth curve
)

/** Fold the ledger into headline totals. Pure. */
fun ledgerTotals(entries: List<RawTreasureLedgerEntry>): LootTotals
```

### 3.2 Manifest → ledger assembly (pure)

The impure layer resolves each UUID into a concrete `ResolvedLootItem`; the pure core assembles the
persisted entry so the shape and the totals are testable without Foundry:

```kotlin
data class ResolvedLootItem(
    val uuid: String?,
    val name: String,
    val quantity: Int,
    val gpValue: Double,
    val cursed: Boolean,
    val createdItemId: String?,   // filled in after the stash write
)

/** Build the ledger entry for a cleared hex from already-resolved items. Pure. */
fun assembleLedgerEntry(
    id: String,
    turn: Int,
    worldTime: Double?,
    sourceHexKey: String,
    sourceContentId: String,
    sourceName: String,
    items: List<ResolvedLootItem>,
): RawTreasureLedgerEntry

/** Convenience readers over a raw manifest (used by the editor summary + offer card). */
fun manifestGpTotal(manifest: Array<RawLootManifestEntry>?): Double
fun manifestItemCount(manifest: Array<RawLootManifestEntry>?): Int
fun manifestCursedCount(manifest: Array<RawLootManifestEntry>?): Int
```

### 3.3 Cap (bounded log)

The ledger is append-only; cap it like other bounded histories so a long campaign can't bloat the
flag:

```kotlin
const val MAX_TREASURE_LEDGER_ENTRIES = 250

/** Keep the newest [max] entries (drop oldest). Pure, returns a new array. */
fun capLedger(
    entries: Array<RawTreasureLedgerEntry>,
    max: Int = MAX_TREASURE_LEDGER_ENTRIES,
): Array<RawTreasureLedgerEntry>
```

### 3.4 Pacing input (the bridge to §6)

Convert realized gp into a level the existing loot-imbalance comparator understands. PF2e's
Treasure-by-Level guidance gives a cumulative "party wealth by level" curve; we seed it as a tunable
constant table and **invert** it — "how much gp implies what level?" — so realized wealth maps onto
the same *itemAccessLevel vs partyLevel* axis the settlement proxy already uses.

```kotlin
/** Cumulative party-wealth-by-level table (party of 4), seeded from PF2e treasure guidance.
 *  Index = level (1..20); value = expected cumulative gp. Tunable; pinned in tests. */
internal val PARTY_WEALTH_BY_LEVEL: List<Double> = listOf(/* L1..L20 cumulative gp */)

/** Invert the wealth table: the highest level whose expected wealth <= [totalGp]. Pure. */
fun wealthLevelForGp(totalGp: Double): Int

data class RealizedLootInput(
    val impliedWealthLevel: Int,   // wealthLevelForGp(cumulative awarded gp)
    val partyLevel: Int,
    val turn: Int,
)

/** Assemble the input the pacing evaluator consumes from the ledger. Pure. */
fun pacingLootInput(
    ledger: List<RawTreasureLedgerEntry>,
    partyLevel: Int,
    turn: Int,
): RealizedLootInput
```

`impliedWealthLevel` then flows straight into the loot-imbalance comparator (§6): if the party's
handed-out wealth implies a level well above their actual level, the alert fires — measured, not
guessed.

### 3.5 Tick surface (respect the split — **no third tick**)

- **`TurnTickingEngine` (monthly, End Turn)** — unchanged for *awarding*. The pacing block that
  already runs here (`TurnWizardApplication.kt:439-491`) gains **one** extra tracked call that reads
  `treasureLedger` (§6). Same cadence as the existing loot metric; no new scheduling.
- **`DailyTickHooks` (daily world clock)** — **not used**. No daily behaviour.
- **Award-on-clear is a GM button, not a tick.** Clearing a hex posts a GM-whispered *offer*
  (§5). Items move and the ledger grows **only** when the GM clicks *Award to party*. Nothing about
  loot is applied automatically by either tick. The pacing metric merely *reads* the ledger at its
  existing monthly cadence.

---

## 4. UI

### 4.1 `HexContentManager` — manifest editor

The manager already accepts drag-dropped Foundry documents onto `.km-hex-drop-zone`, rendering
removable chips read back from the DOM on Save (`HexContentManager.kt:143-224,348-405`). The loot
manifest reuses that exact pattern with a dedicated zone so item drops don't mix with reference-link
drops.

**New editor block** (rendered only when adding/editing an entry):
- A drop zone `.km-hex-loot-zone` — dropping a compendium/world **item** adds a manifest row.
- Each row `.km-hex-loot-row` carries: the enriched item link (name), a `quantity` number input, a
  `gpValue` number input (pre-filled from `item.system.price` on drop, GM-editable), a `cursed`
  checkbox (pre-checked if the item has the `cursed` trait), a `note` text input, and a remove `×`.
- A running summary line: *"3 items · 420 gp · 1 cursed"* via `manifestGpTotal` / `manifestCursedCount`.

**Drop handler** (mirrors `addDroppedDoc`, `HexContentManager.kt:189-202`): resolve the dropped
`uuid` via `fromUuid(...).await()`, read `name`, `system.price` → gp, and the `cursed` trait, then
append a row. **Collection on Save** (mirrors `collectLinkedUuids`, `:215-224`): walk
`.km-hex-loot-row` nodes, read each field from the DOM into a `RawLootManifestEntry`, and persist the
array on the entry inside `saveContent` — the manifest is a variable-length array, so it is collected
from the DOM exactly like the link chips, **not** through the scalar `HexContentManagerModel`
`defineSchema()` (which stays scalar-only). Editing an already-awarded manifest is allowed but shows
a "already awarded" badge and does not re-trigger an award.

**Context additions** (`@JsPlainObject`, extend `HexContentEntryContext` + `HexContentManagerContext`):

```kotlin
// on HexContentEntryContext (list row)
val lootItemCount: Int
val lootGp: Double
val lootCursedCount: Int
val manifestAwarded: Boolean
val hasManifest: Boolean

// on the edit form context
val lootRows: Array<HexLootRowContext>   // { uuid, link, quantity, gpValue, cursed, note }
```

### 4.2 Treasure Ledger — GM-only section

A read-only **Treasure Ledger** table. Placement: the **Session Prep / Analytics** area, where the
gazette and pacing alerts already surface (roadmap #10/#13), rather than a new nav entry — keeping the
loot picture next to the pacing picture.

- Columns: *Turn · Source Hex · Items (links) · Total gp · Cursed*, newest first.
- A footer with lifetime totals from `ledgerTotals` and a small gp-by-turn sparkline hook.
- **Visibility is GM-only, enforced at context build.** Following the NotesContext lesson
  (`NotesContext.kt:17-27` — GM-only fields blanked for players *when the context is built*, never in
  the template), the ledger context is populated only when `isGM`; for players it is built empty:

```kotlin
suspend fun buildTreasureLedgerContext(kingdom: KingdomData, isGM: Boolean): TreasureLedgerContext {
    if (!isGM) return TreasureLedgerContext(entries = emptyArray(), totalGp = 0.0, visible = false)
    // ... enrich item links, format rows ...
}
```

No manifest or ledger data reaches a player's rendered sheet at all — the array is empty before the
template runs, so there is nothing to leak.

### 4.3 i18n namespace

Nested under the module root `pf2e-kingmaker-tools` in `lang/en.json` (never flat-dotted — i18next
nested lookup; catalogs wired through `initLocalization()`):

```json
"kingdom": {
  "lootManifest": {
    "title": "Loot Manifest",
    "dropHint": "Drag items here to add treasure",
    "quantity": "Qty", "gpValue": "GP", "cursed": "Cursed", "note": "Note",
    "summary": "{{items}} items · {{gp}} gp · {{cursed}} cursed",
    "awardedBadge": "Awarded (turn {{turn}})"
  },
  "treasureLedger": {
    "title": "Treasure Ledger",
    "colTurn": "Turn", "colHex": "Source Hex", "colItems": "Items",
    "colGp": "Total GP", "colCursed": "Cursed",
    "lifetimeTotal": "Lifetime: {{gp}} gp across {{items}} items",
    "empty": "No treasure awarded yet."
  }
}
```

Offer-card / chat keys live under `chatMessages.lootAward.*` (§5).

---

## 5. Chat / Offer Surfaces (GM-confirmed only)

Every mechanical grant is a **GM-confirmed offer**, never auto-applied — the module's firm rule
(`ChatButtons.kt` `km-offer-*`). Award handlers register as `ChatButton` entries bound to the `#chat`
sidebar in `ChatButtons.kt`, each GM-gated with `if (!game.user.isGM) return@ChatButton` and made
idempotent via `content.manifestAwarded`.

### 5.1 Trigger — clearing a hex

`HexContentManager._onClickAction` already handles the `"clear"` action
(`HexContentManager.kt:285-292` → `updateVisibility(..., DiscoveryEvent.CLEAR)`, which sets
visibility to `HexContentVisibility.CLEARED`). Extend that branch: **after** the state change, if the
entry has a non-empty `lootManifest` and `manifestAwarded != true`, post the award offer card
(whispered to GMs) via `postChatTemplate("chatmessages/loot-award-offer.hbs", …)`. Clearing does
**not** move items — it only surfaces the offer. The manager list row also shows an inline *"Award
loot"* button when `hasManifest && !manifestAwarded`, routing to the same handler, so the GM can award
without re-clearing.

### 5.2 Offer card & buttons

Card `src/jsMain/resources/chatmessages/loot-award-offer.hbs`: the source hex, the item links, per-row
qty/gp/cursed, and a button group.

| Button (`data-action`) | Behaviour | Handler |
|------------------------|-----------|---------|
| `km-offer-loot-award` / `award-all` | Resolve every manifest item, move all into the party stash, append **one** ledger entry, set `manifestAwarded=true` + `manifestAwardedTurn`. Idempotent (no-op if already awarded). | `ChatButton("km-offer-loot-award")` |
| `km-offer-loot-award` / `award-item` (`data-index`) | Award a single row (partial distribution); ledger entry accrues per awarded item; manifest marked awarded once all rows are done. | same |
| `km-offer-loot-award` / `dismiss` | Set `manifestAwarded=true` with **no** grant (GM handled loot manually) — stops re-offers. | same |

### 5.3 Where items land — party stash vs chat-only links (**decision**)

**Decision: move real items into the party stash.** `KingdomActor` is a `typealias` for `PF2EParty`
(`Kingdom.kt:12`), and the caravan-delivery path already writes to that inventory:
`actor.addToInventory(itemData)` after building an item object (`TurnWizardApplication.kt:389-409`),
with the created item ids recorded into the End-Turn snapshot for exact undo (`:415-419`). The award
handler reuses that: `fromUuid(uuid).await()?.toObject()` → set `system.quantity` → `addToInventory`
(`PF2EActor.addToInventory`, `PF2EActor.kt:64`), capturing each `createdItemId` into the
`RawLootLedgerItem` for a future undo hook.

**Rejected: chat-only links.** A chat card of enriched item links (no inventory write) is less code,
but it (a) leaves the party inventory a manual copy job — the exact friction this feature removes —
and (b) gives the pacing engine **nothing measurable**, since "gp the party holds" would still be a
guess. Real items in the stash are the only design that makes the ledger *true*. (For GMs who prefer
manual distribution, the *dismiss* button records the intent without granting.)

### 5.4 Cursed items → Cleanse Item cross-link

When an awarded row is `cursed`, the item still moves to the stash (players own the problem) and the
ledger entry's `cursedCount` increments. The award chat confirmation appends a cursed line per item
with a pointer to the **Cleanse Item** ritual (`CleanseItem.kt` `cleanseItemPlan` — the kingdom-scale
Magic counteract that removes an item's curse, `docs/house-rules.md:279`). This is the consumer of the
`cursed` flag and closes the loop with sibling card **`gap0709-cleanse-item`**: manifests are where
cursed items *enter* play; Cleanse Item is where they *leave* cursed state. i18n:
`chatMessages.lootAward.cursedHint` → *"{{name}} is cursed — see the Cleanse Item ritual."*

> **Upgrade available since this plan was written.** When drafted, `cleanseItemPlan` was pure logic
> with **no callers** — there was no ritual a GM could actually run, so a text pointer was the most
> this could offer. The activity now has a real dialog (`dialogs/CleanseItemDialog.kt`,
> `openCleanseItemDialog(kingdomLevel, settlements, onPrepared)`), reached from the Leadership
> activity flow, which computes DC, counteract level, luxury cost and the qualifying settlements from
> the item's level and gates on shrine/temple/cathedral.
>
> So the cursed line should be a **button**, not a sentence: `km-offer-loot-cleanse`, carrying the
> awarded item's uuid, opening that dialog for it.
>
> **That seam now exists.** `openCleanseItemDialog` takes an optional
> `preselected: PF2EItem? = null`; passing a resolved item fills the drop zone and derives DC,
> counteract level, luxury cost and the qualifying settlements from its level immediately. Null
> keeps the empty drop zone, which is the activity-menu path. So `km-offer-loot-cleanse` resolves the
> awarded item's uuid and hands it straight over — the GM never re-finds an item the module just
> gave them.

---

## 6. Interactions With Existing Systems (+ Out of Scope)

### 6.1 PacingAlerts — the exact change (verified against source)

**Current loot math (finding).** `PacingAlerts.kt:45-70`:

```kotlin
fun evaluateLootImbalance(itemAccessLevel: Int, partyLevel: Int, range: Int, turn, relatedEntityId): RawPacingAlert? {
    val diff = itemAccessLevel - partyLevel
    if (diff <= range) return null
    val severity = if (diff > range * 2) CRITICAL else WARNING
    return alert(PacingAlertType.LOOT_IMBALANCE, severity, turn, relatedEntityId)
}
fun trackLootImbalance(...) = trackSeverityChange(evaluateLootImbalance(...), previousSeverity)  // fire-once
```

The **only caller** is `TurnWizardApplication.kt:471-486`, inside the End-Turn pacing block. It picks
`offendingSettlement = settlements.allSettlements.maxByOrNull { it.itemPurchaseLevel }` and passes
`itemAccessLevel = offendingSettlement.itemPurchaseLevel` vs `partyLevel = targetLevel` (chapter
target, else avg party level), persisting fire-once state in `kingdom.pacingLastLootImbalance`, gated
by `pacingLootImbalanceEnabled()`, tolerance `pacingLootImbalanceRange()`. **So the metric measures a
settlement's *sellable* item level — a shop-access proxy — and never reads awarded treasure.**

**Change: AUGMENT, do not replace.** Add a *second, independent* realized-loot signal fed by the
ledger; keep the settlement-purchase-access signal intact.

- *Why keep the proxy:* the settlement metric answers a distinct, house-rule-explicit question —
  *"can players buy items above their level?"* (`house-rules.md:56`). Realized loot answers a different
  one — *"has the party been handed too much treasure for its level?"* Both are real pacing risks;
  dropping the proxy would blind the "shopping above level" warning the house rules single out.
- *Why a new fire-once field:* the tracker's fire-once state is a single scalar
  (`pacingLastLootImbalance`). Two tracks sharing it would fight over severity. So add
  `pacingLastRealizedLootImbalance` (§2.4) and a new `PacingAlertType.REALIZED_LOOT_IMBALANCE` enum
  value (with its own i18n key), so the two alerts coexist and each fires once on its own crossing.

Concrete edits:

1. **`PacingAlerts.kt`** — extract the shared `diff → severity` logic into a private
   `severityForDiff(diff, range)` helper, then add sibling evaluators reusing it:
   ```kotlin
   fun evaluateRealizedLootImbalance(input: RealizedLootInput, range: Int): RawPacingAlert? {
       val diff = input.impliedWealthLevel - input.partyLevel
       val severity = severityForDiff(diff, range) ?: return null
       return alert(PacingAlertType.REALIZED_LOOT_IMBALANCE, severity, input.turn, relatedEntityId = "treasure-ledger")
   }
   fun trackRealizedLootImbalance(input, range, previousSeverity): PacingStateTrack =
       trackSeverityChange(evaluateRealizedLootImbalance(input, range), previousSeverity)
   ```
   (`evaluateLootImbalance` keeps its exact current signature/behaviour — the settlement path is
   untouched.)
2. **`LootLedger.kt`** — `pacingLootInput(ledger, partyLevel, turn)` (§3.4) builds the `RealizedLootInput`.
3. **`TurnWizardApplication.kt`** — immediately after the existing settlement loot block
   (`:472-486`), add, still inside `if (targetLevel != null)` and gated by the same
   `pacingLootImbalanceEnabled()`:
   ```kotlin
   val realizedInput = pacingLootInput(kingdom.treasureLedger?.toList().orEmpty(), targetLevel, currentTurn)
   val realizedTrack = trackRealizedLootImbalance(
       realizedInput, kingdom.settings.pacingLootImbalanceRange(), kingdom.pacingLastRealizedLootImbalance)
   kingdom.pacingLastRealizedLootImbalance = realizedTrack.severity
   realizedTrack.alert?.let { firedPacingAlerts.add(it) }
   ```
   Both alerts land in the same `firedPacingAlerts` list, posted through the existing chat path
   (`:489-491`). No new tick, no new cadence.
4. **`RawPacingAlert.kt` / `PacingAlertType`** — add the `REALIZED_LOOT_IMBALANCE` case + i18n key.

### 6.2 Other systems

| System | File(s) | Interaction |
|--------|---------|-------------|
| **Hex content / clear transition** | `HexContentManager.kt` | Manifest edited here; `"clear"` action posts the award offer (§5.1). `RawHexContent` extended (§2.2). |
| **Party inventory** | `TurnWizardApplication.kt:389-409`, `PF2EActor.kt:64` | Award reuses `addToInventory` on the `PF2EParty` (`KingdomActor`); records `createdItemId` for undo. |
| **Cleanse Item house rule** | `CleanseItem.kt`, sibling `gap0709-cleanse-item` | Consumes the `cursed` flag; award chat cross-links the ritual (§5.4). |
| **Turn history / gazette** | `TurnHistory.kt` / `formatTurnGazette` | *Optional* one public line per award turn ("The party recovered treasure from hex 12.4"), gp/cursed omitted. Player-safe. |
| **Migrations** | `Migrations.kt` | Register `Migration65` (§2.6). |
| **Daily tick** | `DailyTickHooks.kt` | **No interaction** — award is a button; pacing reads monthly. |

### 6.3 Explicit OUT OF SCOPE

- **No removal of the settlement-purchase-access metric** — it stays as a parallel signal (§6.1).
- **No automatic gp appraisal beyond `item.system.price`** — the drop pre-fills gp; the GM edits it.
  No market/haggle modelling.
- **No automatic Cleanse execution** — awarding a cursed item only *flags and links*; removing the
  curse remains the separate Cleanse Item activity.
- **No per-player loot split / assignment** — items go to the shared party stash; who carries what is
  the table's call.
- **No sell / consume / loss tracking** — the ledger records *awards*, not later disposal. It is a
  wealth-*in* chronicle.
- **No retroactive backfill** of treasure for hexes cleared before this feature shipped.
- **Bulk CSV / region seeding is deferred to Phase 5** (§8), not part of the core feature.

---

## 7. Test Plan

### 7.1 `commonTest` — pure logic (`LootLedgerTest.kt`)

| Test | Assertion |
|------|-----------|
| `ledgerTotals sums gp, items, cursed` | totals across mixed entries; `gpByTurn` groups correctly. |
| `ledgerTotals empty` | zeros, no exception. |
| `manifestGpTotal multiplies qty × gpValue` | 3 × 15.0 + 1 × 40.5 = 85.5. |
| `manifestCursedCount counts only cursed rows` | mixed manifest → correct count. |
| `wealthLevelForGp boundaries` | at each table breakpoint and just below/above; gp below L1 → 1; gp above L20 → 20. |
| `pacingLootInput derives implied level from cumulative gp` | ledger summing to a level-N wealth → `impliedWealthLevel == N`. |
| `capLedger keeps newest N` | 260 entries, `MAX=250` → 250 newest kept, oldest dropped, order preserved. |
| `assembleLedgerEntry totals + cursedCount` | resolved items → correct `totalGp`, `cursedCount`, snapshot fidelity. |

### 7.2 `commonTest` — pacing (`PacingAlertsTest.kt` additions)

| Test | Assertion |
|------|-----------|
| `evaluateRealizedLootImbalance null within range` | implied − party ≤ range → null. |
| `evaluateRealizedLootImbalance warning / critical` | diff > range → WARNING; diff > range×2 → CRITICAL; type == `REALIZED_LOOT_IMBALANCE`. |
| `trackRealizedLootImbalance fire-once` | fires on first crossing, silent while persisting, re-fires after clearing. |
| `settlement + realized tracks independent` | both can fire the same turn without clobbering each other's severity state. |

### 7.3 `jsTest` — integration (`LootManifestTest.kt`)

| Test | Assertion |
|------|-----------|
| `manifest round-trips on RawHexContent` | save/load fidelity through `hexContents`. |
| `award-all moves items + appends one ledger entry` | party inventory gains items (correct qty/level/price); ledger grows by one; `manifestAwarded=true`. |
| `award idempotent` | second click / re-posted card → no double-grant, no duplicate ledger entry. |
| `dismiss records no grant` | inventory unchanged; `manifestAwarded=true`; no ledger entry. |
| `cursed award increments cursedCount + emits hint` | ledger `cursedCount` right; chat contains the Cleanse cross-link key. |
| `clear posts offer only when manifest present & unawarded` | no manifest → no card; already awarded → no card. |
| `treasure ledger context is GM-only` | `isGM=false` → empty context (no rows, no gp) before template. |
| `End Turn feeds realized loot into pacing` | ledger above tolerance → `REALIZED_LOOT_IMBALANCE` in `firedPacingAlerts`. |
| `Migration65` | pre-67 kingdom → `treasureLedger==[]`, guard flags seeded, existing data intact. |

### 7.4 Manual Foundry checklist

1. Edit a hex content entry → drag two compendium items into the loot zone; gp pre-fills from price;
   check `cursed` on one; set quantities; Save. Reopen → manifest persists with the summary line.
2. List row shows "2 items · N gp · 1 cursed" and an *Award loot* button.
3. Click **Clear** on the hex → a GM-whispered award offer card appears; players see nothing.
4. Click **Award to party** → both items appear in the party (The Party) inventory with correct
   quantity/level/price; chat confirms; the cursed item's line links Cleanse Item.
5. Reopen the manager → entry shows the "Awarded (turn N)" badge; clicking Award again does nothing.
6. Open **Session Prep → Treasure Ledger** (as GM) → the award is listed with turn, hex, items, gp,
   cursed; lifetime total correct. Log in as a player → the ledger section is empty/hidden.
7. Award enough treasure to exceed tolerance, then **End Turn** → a realized-loot pacing alert fires
   in chat; the settlement-purchase alert still fires independently when a high-level market exists.
8. Reload the world → manifests, awarded flags, and ledger persist.

Build/verify per repo convention: `python3 scripts/check_i18n_keys.py`, then
`JAVA_HOME=<jdk25> ./gradlew assemble jsTest -x kotlinStoreYarnLock` (Chrome headless).

---

## 8. Phasing (independently committable)

| Phase | Title | Deliverable | Key files |
|-------|-------|-------------|-----------|
| **1** | **Data model + migration + pure engine** | `RawLootManifestEntry`, `RawLootLedgerItem`, `RawTreasureLedgerEntry`; `RawHexContent`/`KingdomData` field additions; `Migration65` (registered); `LootLedger.kt` (`ledgerTotals`, `wealthLevelForGp`, `pacingLootInput`, `assembleLedgerEntry`, `capLedger`) with full `commonTest`. | `RawHexContent.kt`, `RawLootManifest.kt`, `KingdomData.kt`, `Migration65.kt`, `Migrations.kt`, `LootLedger.kt`, `LootLedgerTest.kt` |
| **2** | **Pacing augmentation** | `severityForDiff` refactor + `evaluateRealizedLootImbalance`/`trackRealizedLootImbalance`; `REALIZED_LOOT_IMBALANCE` type; wire the realized track into End Turn beside the settlement track; `pacingLastRealizedLootImbalance` state. Tests. | `PacingAlerts.kt`, `RawPacingAlert.kt`, `TurnWizardApplication.kt`, `PacingAlertsTest.kt`, `lang/en.json` |
| **3** | **Manifest editor UX** | Loot drop zone + rows in `HexContentManager` (drop → resolve name/price/cursed; collect from DOM on Save); list-row summary; context/i18n. | `HexContentManager.kt`, `hex-content-manager.hbs`, `lang/en.json` |
| **4** | **Award offers + ledger view + QA** | `km-offer-loot-award` handler (party-stash write, per-item/all/dismiss, idempotent, cursed cross-link); `loot-award-offer.hbs`; clear-transition trigger + inline Award button; GM-only Treasure Ledger section + `TreasureLedgerContext`; `jsTest` + manual checklist. | `ChatButtons.kt`, `HexContentManager.kt`, `chatmessages/loot-award-offer.hbs`, `TreasureLedgerContext.kt`, ledger `.hbs`, `LootManifestTest.kt` |
| **5** *(deferred)* | **Bulk region seeding** | CSV / quick-add to seed a whole region's hex manifests at once (paste `hexKey,itemUuid,qty,gp,cursed` rows). Out of scope for the core feature; scoped as a follow-up. | new import dialog, `LootLedger.kt` parse helpers |

Phases 1 and 2 are backend-only and land first (1 → 2). Phase 3 (editor) depends on 1's data model.
Phase 4 (offers/ledger) depends on 1 + 3. Phase 5 is optional polish after the core ships.

---

## 9. Open Questions for Gregory

1. **Wealth table source:** seed `PARTY_WEALTH_BY_LEVEL` from the PF2e GMG party-treasure-by-level
   curve (party of 4), or make it a homebrew-profile-tunable constant from day one?
2. **gp fidelity:** `gpValue: Double` (keeps sp/cp) vs `Int` gp (simpler, matches the shipment path's
   `itemPriceGp`). Plan assumes `Double`.
3. **Partial awards:** ship per-item `award-item` in Phase 4, or start with `award-all`/`dismiss` only
   and add per-item later?
4. **Undo:** wire awarded `createdItemId`s into the End-Turn snapshot so *Undo End Turn* also removes
   awarded items, or keep award undo out of the turn-snapshot (award is a standalone GM action)?
5. **Gazette line:** emit a public "treasure recovered" line per award turn, or keep the ledger
   entirely GM-side?

---

**End of Plan.** Ready for review. On approval, implementation cards follow the phasing table above.
