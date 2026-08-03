# Plan: Personal Holdings & Titles — give each PC a private stake in the kingdom

> **Status:** Plan only — no implementation yet
> **Date:** 2026-07-09
> **Roadmap item:** New backlog / player-engagement lever (sibling to Petition Inbox)
> **Parent:** [`2026-07-09-plan-petition-inbox.md`](2026-07-09-plan-petition-inbox.md) — shares the "personal surface" framing; titles integrate with petition addressing.
> **Depends on:** Faction & Diplomacy Relations Tracker (#1), Army & War Pressure Board (#12), Turn History gazette, `TurnTickingEngine` (monthly End Turn tick), Player-facing collaborative kingdom view (per-user ownership gating).
> **Sibling card:** `gap0709-siege-damage` — the siege/settlement-damage work that this plan's damage model hooks into (see §6).
> **Branch:** `kingmaker.5`

---

## Executive Summary

Kingdom management in this module is a shared, abstract ledger: one RP pool, one commodity
pool, one unrest track that the whole table nudges together. Nothing on the sheet is *mine*.
A player who is the Warden has no personal reason to care whether the Narlmarches stays
claimed, and a player who is the Treasurer feels the kingdom's gold as a spreadsheet number,
not as their own purse.

**Personal Holdings** give each PC a *private stake*: a **title** (Baron of the Tuskwater, the
Silver Stag's Patron) and one or two **holdings** — a capital manor, a hunting lodge bound to a
claimed Narlmarches hex, a share in the Silver Stag tavern bound to a settlement structure.
Each holding yields a **small personal per-turn income** (gold / luxuries / favors) at End Turn,
and — critically — can be **damaged** when kingdom events, war threats, or raids touch its hex or
settlement. Kingdom outcomes now land on each player's **own** ledger.

Every mechanical benefit stays a **GM-confirmed offer** (`km-offer-…`): income is *offered* at
End Turn, damage is *offered*, repair is *offered*. Nothing writes to a PC's sheet silently.
This is deliberately **flavor + stakes, not an economy sim** — hard-capped at ~2 holdings per PC,
with modest numbers well below treasure-by-level.

---

## 1. Problem Statement + Player/GM Value

**Problem.** The house-rules doc is blunt about the failure mode this feature targets. From
`docs/house-rules.md` (Kingdom Management):

> "This might be the most labor-intensive system in this AP. If you run it as written, it will
> be terrible at low levels. […] My players often ran out of Leadership activities that they
> could perform, even with faster leveling. **One player left the campaign early because of
> kingdom management.**"

and:

> "**Kingdom Building**: If you don't put in work to add flavor, tangible benefits, lore and RP
> plus ramp up the pressure using Unrest and Ruin in certain parts […] **this will feel like
> managing an Excel spreadsheet.**"

The doc's own prescription is "Tie their kingdom and buildings to XP, loot and access to
feats/items" and to make the world push back on the players' choices. Today the module gives
the kingdom *collective* tangible benefits (structures, item access) but nothing that a single
player owns and can lose. There is no lever that makes *this hex, my hex* worth defending.

**Value to the table.**

- **Player buy-in (the single strongest lever).** A holding is a small, legible thing a player
  owns. "The raid is heading for the Tuskwater — that's *my* manor" converts an abstract war
  threat into a personal stake. This is the disengagement antidote the house-rules doc asks for.
- **Consequences with a face.** When a war threat expires on a settlement or a kingdom event
  sacks a hex, the fallout is no longer just +1 Unrest — a specific player's income stream drops
  to half until they pay to rebuild. Kingdom neglect has a personal cost.
- **Reward for claiming/holding land.** Modest per-turn income gives players a reason to claim,
  road, and defend specific hexes — reinforcing the exploration loop the AP already leans on.
- **GM prep reduction.** The GM grants a title + holding once (a dialog); thereafter income,
  damage, and repair surface as offer cards the GM clicks. No per-turn bookkeeping.
- **Roleplay hooks.** Titles feed the Petition Inbox (§ "Title mechanics"): NPCs address the
  "Baron of the Narlmarches," not just "the Warden."

---

## 2. Data Model

### 2.1 `RawPersonalHolding` (jsMain, `@JsPlainObject`, nullable for migration safety)

New file: `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/data/RawPersonalHolding.kt`
(package `at.posselt.pfrpg2e.kingdom.data`, next to `RawGroup.kt` / `RawWarThreat.kt`).

```kotlin
package at.posselt.pfrpg2e.kingdom.data

import kotlinx.js.JsPlainObject

/**
 * A title + personal holding granted to one PC. Kingdom-scoped, GM-granted.
 * All fields non-null where the grant dialog always supplies them; genuinely
 * optional fields are nullable so older saves and partial grants load cleanly.
 */
@JsPlainObject
external interface RawPersonalHolding {
    var id: String                     // stable UUID

    // --- Ownership (see §2.3 for why actorUuid is authoritative) ---
    var actorUuid: String?             // the PC actor this holding belongs to (authoritative owner)
    var ownerUserId: String?           // denormalised Foundry user id cache (fast per-user filter)
    var ownerLabel: String?            // display fallback ("Valeria") when the actor can't resolve

    // --- Identity / flavor ---
    var title: String?                 // cosmetic title, e.g. "Baron of the Tuskwater" (null = untitled)
    var name: String                   // holding name, e.g. "Silverstead Manor"
    var kind: String                   // "manor" | "lodge" | "tavern-stake" | "farmstead" | "workshop" | "other"

    // --- Location binding (exactly one is meaningful; both nullable) ---
    var boundHexKey: String?           // realm-map hex key (matches RawSettlement.hexKey / kingmaker.state.hexes)
    var structureSceneId: String?      // settlement sceneId (matches RawSettlement.sceneId) for structure-bound holdings
    var structureRef: String?          // optional structure id/name within that settlement ("Tavern")

    // --- Economy ---
    var incomeTier: Int                // 1 = Modest, 2 = Comfortable, 3 = Lavish (see §3.2)
    var condition: String              // "sound" | "damaged" | "destroyed"

    // --- Bookkeeping / ledger ---
    var grantedTurn: Int?              // kingdom turn the holding was granted
    var lastIncomeTurn: Int?           // last turn income was ACCRUED (idempotency for the tick)
    var lifetimeIncomeGold: Int?       // durable ledger: total gp offered over the holding's life
    var lastEventLabel: String?        // last thing that happened ("Raided by Tiger Lords") for the card hint
    var visibleToPlayers: Boolean?     // house-rule visibility; null/true = the owner can see it
}
```

Design notes:

- `kind` and `condition` are **string discriminators**, mirroring the existing `RawGroup.relations`
  / `RawGroup.allianceLevel` / `RawWarThreat.status` convention — parsed into commonMain enums
  (§3) at the boundary, never switched on raw strings in logic.
- **Location is a soft union.** A hex-bound holding sets `boundHexKey`; a structure-bound holding
  sets `structureSceneId` (+ optional `structureRef`). Validation in the grant dialog requires
  exactly one; the engine treats "neither set" as an un-sited holding that still yields income but
  can never be damaged by location hooks.
- No `Record<String,…>` maps here (unlike faction agendas) — a holding is a flat record.

### 2.2 Persistence — `KingdomData.personalHoldings` (top-level array)

Add one nullable top-level field to `KingdomData` (`src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/KingdomData.kt`),
alongside the existing `groups: Array<RawGroup>`, `companions: Array<RawCharacter>?`,
`companionExpeditions`, `campaignClocks`, etc.:

```kotlin
// KingdomData (additions only — keep all existing fields)
var personalHoldings: Array<RawPersonalHolding>?   // null on legacy saves; Migration49 seeds []
```

**Why a top-level kingdom array (recommended) vs alternatives:**

| Option | Verdict | Reason |
|--------|---------|--------|
| **`KingdomData.personalHoldings` (top-level array, keyed by `actorUuid`/`ownerUserId`)** | ✅ **Recommended** | Kingdom-scoped state, reachable by `TurnTickingEngine` exactly like `groups`; one place the GM sees every holding; travels with the kingdom flag; participates in preview/commit parity. |
| Nest under each `RawCharacter` (companion roster) | ❌ | `companions: Array<RawCharacter>` is the **companion** roster (Ekundayo, Amiri…), not the PCs. Holdings belong to player characters, who have no `RawCharacter` entry here. |
| Store on the PF2e PC actor via Foundry `flags` | ❌ | Scatters state across actor documents the kingdom tick can't cheaply enumerate; breaks the pure/previewable tick (would need per-actor reads); GM can't see all holdings in one view; players could self-edit. |
| Reuse `RawGroup` | ❌ | Groups are *factions*, semantically wrong; would pollute the diplomacy list. |

Ownership is stored as `actorUuid` (authoritative) plus an `ownerUserId` cache — see §2.3.

### 2.3 Ownership model (grounded in `Leaders.kt`)

`getOwnedLeaderRoles` (`src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/Leaders.kt`) already resolves
"does the current user own this?" by taking a leader's linked actor UUID, calling
`fromUuidOfTypes(uuid, PF2ECharacter, PF2ENpc)`, and checking `actor.isOwner`. Personal Holdings
reuse that exact pattern:

- `actorUuid` is the **authoritative** owner key. The "My Holdings" card (§4) shows a holding when
  its `actorUuid` resolves to an actor with `isOwner == true` (GM sees all).
- `ownerUserId` is a **denormalised convenience cache** for a fast first-pass filter and for
  holdings whose actor can't currently resolve; it is never the sole source of truth.

### 2.4 Migration — propose `Migration49` (Gregory sequences the real number)

The chain currently ends at `Migration48` (`src/jsMain/kotlin/at/posselt/pfrpg2e/migrations/migrations/Migration48.kt`);
`MigrationChainTest` asserts contiguity with `assertEquals((17..48).toList(), migrations.map { it.version })`.

New file `Migration49.kt`, following the `Migration48` template (idempotent, `dynamic`):

```kotlin
package at.posselt.pfrpg2e.migrations.migrations

import com.foundryvtt.core.Game

/**
 * Migration 49 — personal holdings & titles.
 * Seeds an empty personalHoldings array on kingdoms that predate the feature.
 */
class Migration49 : Migration(49) {
    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        if (kingdom.personalHoldings == null) kingdom.personalHoldings = arrayOf<Any?>()
    }
}
```

Wiring (three edits, matching how 48 was added):
1. `import …migrations.Migration49` in `Migrations.kt`.
2. Add `Migration49()` to the `internal val migrations = listOf(…)`.
3. Update `MigrationChainTest` assertion to `(17..49).toList()`.

Non-breaking: `personalHoldings` is nullable; a null array is treated as "no holdings."

---

## 3. Engine Design

Following the house rule **pure logic → commonMain + commonTest, impure → jsMain + jsTest**.
`RawPersonalHolding` is a `@JsPlainObject` (JS-only), so the split is:

- **commonMain** holds all pure math over *plain Kotlin value types* (enums + data classes),
  fully unit-tested with zero JS/Foundry deps.
- **jsMain** holds thin adapters that read a `RawPersonalHolding`, call the pure core, and return
  a copied `Raw` object. (This is a deliberate correction of the faction-agenda plan, which put
  `@JsPlainObject`-referencing functions in commonMain where they cannot compile.)

### 3.1 Pure core — `src/commonMain/kotlin/at/posselt/pfrpg2e/data/kingdom/PersonalHoldings.kt`

```kotlin
package at.posselt.pfrpg2e.data.kingdom

enum class HoldingTier(val goldPerLevel: Int, val luxuriesPerTurn: Int, val favorsPerTurn: Int) {
    MODEST(2, 0, 0),
    COMFORTABLE(5, 0, 1),
    LAVISH(10, 1, 2);
    companion object { fun fromValue(v: Int) = entries.getOrElse(v - 1) { MODEST } }
}

enum class HoldingCondition { SOUND, DAMAGED, DESTROYED }

enum class DamageSeverity { MINOR, MAJOR }   // MINOR: one step; MAJOR: straight to destroyed

/** A turn's worth of personal income. Plain value type; mapped to offers/commodities in jsMain. */
data class HoldingIncome(val gold: Int, val luxuries: Int, val favors: Int) {
    val isEmpty get() = gold == 0 && luxuries == 0 && favors == 0
    operator fun plus(o: HoldingIncome) = HoldingIncome(gold + o.gold, luxuries + o.luxuries, favors + o.favors)
    companion object { val ZERO = HoldingIncome(0, 0, 0) }
}

const val MAX_HOLDINGS_PER_PC = 2
private const val LEVEL_CAP = 20

/** Base income for a SOUND holding at [tier], scaled by [level] (PC or kingdom level, capped at 20). */
fun holdingIncome(tier: HoldingTier, level: Int): HoldingIncome {
    val lvl = level.coerceIn(1, LEVEL_CAP)
    return HoldingIncome(
        gold = tier.goldPerLevel * lvl,
        luxuries = tier.luxuriesPerTurn,
        favors = tier.favorsPerTurn,
    )
}

/** Condition multiplier applied to base income: sound ×1, damaged ×½ (floored), destroyed ×0. */
fun conditionAdjustedIncome(tier: HoldingTier, level: Int, condition: HoldingCondition): HoldingIncome =
    when (condition) {
        HoldingCondition.SOUND     -> holdingIncome(tier, level)
        HoldingCondition.DAMAGED   -> holdingIncome(tier, level).let { HoldingIncome(it.gold / 2, it.luxuries / 2, it.favors) }
        HoldingCondition.DESTROYED -> HoldingIncome.ZERO
    }

/** Pure condition transition. Never throws; DESTROYED is terminal until repaired. */
fun nextConditionAfterDamage(current: HoldingCondition, severity: DamageSeverity): HoldingCondition =
    when {
        current == HoldingCondition.DESTROYED -> HoldingCondition.DESTROYED
        severity == DamageSeverity.MAJOR      -> HoldingCondition.DESTROYED
        current == HoldingCondition.SOUND     -> HoldingCondition.DAMAGED
        else                                  -> HoldingCondition.DESTROYED   // DAMAGED + MINOR -> DESTROYED
    }

/** Cost (in gp) to restore a holding one step toward SOUND. Roughly 3× / 6× monthly income. */
fun repairCost(tier: HoldingTier, condition: HoldingCondition): Int = when (condition) {
    HoldingCondition.SOUND     -> 0
    HoldingCondition.DAMAGED   -> when (tier) { HoldingTier.MODEST -> 25;  HoldingTier.COMFORTABLE -> 75;  HoldingTier.LAVISH -> 150 }
    HoldingCondition.DESTROYED -> when (tier) { HoldingTier.MODEST -> 100; HoldingTier.COMFORTABLE -> 300; HoldingTier.LAVISH -> 600 }
}

/** One step toward SOUND (DESTROYED -> DAMAGED -> SOUND), so a rebuild is two paid repairs. */
fun repairedCondition(current: HoldingCondition): HoldingCondition = when (current) {
    HoldingCondition.DESTROYED -> HoldingCondition.DAMAGED
    HoldingCondition.DAMAGED   -> HoldingCondition.SOUND
    HoldingCondition.SOUND     -> HoldingCondition.SOUND
}
```

### 3.2 Income table (concrete)

Income is **per kingdom turn** (monthly, at End Turn). `level` = the owning PC's level (fallback:
kingdom level). Numbers are deliberately modest — a Lavish holding at level 10 pays 100 gp/turn
(~1,200 gp/year of downtime), far below party treasure-by-level, and a PC is capped at 2 holdings.

| Tier | `incomeTier` | Name | Examples | gp/turn | Luxuries | Favors |
|------|--------------|------|----------|---------|----------|--------|
| MODEST | 1 | Modest | farmstead, market stall, a lodge | `2 × level` | 0 | 0 |
| COMFORTABLE | 2 | Comfortable | a manor, a mill, a workshop | `5 × level` | 0 | 1 |
| LAVISH | 3 | Lavish | a tavern stake, a noble estate | `10 × level` | 1 | 2 |

Sample gp/turn (SOUND):

| Tier | L1 | L5 | L10 | L15 | L20 |
|------|----|----|-----|-----|-----|
| Modest | 2 | 10 | 20 | 30 | 40 |
| Comfortable | 5 | 25 | 50 | 75 | 100 |
| Lavish | 10 | 50 | 100 | 150 | 200 |

- **Damaged** → gold and luxuries halved (floored), favors kept (a damaged tavern still trades gossip).
- **Destroyed** → zero until rebuilt.
- **Favors** are a soft social currency (narrative; can feed Petition Inbox response options later).
  They are *not* a kingdom resource — no storage, no consumption. Kept as flavor.
- **Luxuries** map to the kingdom's existing Luxuries commodity **only when the GM applies the
  offer** (§5); accrual itself writes nothing.

### 3.3 jsMain adapters — `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/PersonalHoldingsJs.kt`

```kotlin
// Read a RawPersonalHolding's tier/condition into commonMain enums.
fun RawPersonalHolding.tierEnum(): HoldingTier = HoldingTier.fromValue(incomeTier)
fun RawPersonalHolding.conditionEnum(): HoldingCondition =
    when (condition) { "damaged" -> HoldingCondition.DAMAGED; "destroyed" -> HoldingCondition.DESTROYED; else -> HoldingCondition.SOUND }

/** Pure-cored, returns a COPIED Raw with condition advanced + lastEventLabel set (no mutation of input). */
fun applyHoldingDamage(holding: RawPersonalHolding, severity: DamageSeverity, label: String): RawPersonalHolding {
    val next = nextConditionAfterDamage(holding.conditionEnum(), severity)
    return holding.copy(
        condition = next.name.lowercase(),
        lastEventLabel = label,
    )
}

/** Per-turn income accrual for one holding, respecting idempotency via lastIncomeTurn. */
fun accrueIncome(holding: RawPersonalHolding, ownerLevel: Int, currentTurn: Int): HoldingIncome {
    if (holding.lastIncomeTurn == currentTurn) return HoldingIncome.ZERO   // already accrued this turn
    return conditionAdjustedIncome(holding.tierEnum(), ownerLevel, holding.conditionEnum())
}
```

`RawPersonalHolding.copy(...)` is the auto-generated `@JsPlainObject` copy — the immutable-update
pattern used everywhere in this codebase (`RawFame`, `RawResources`, etc.).

### 3.4 `TurnTickingEngine` surface

`TurnTickingEngine.tick()` (`src/jsMain/kotlin/.../kingdom/TurnTickingEngine.kt`) is the monthly
End Turn tick and is **preview/commit-safe** (deterministic, Foundry-free). Personal income accrues
**here** — never in `DailyTickHooks` (daily world clock stays untouched).

Add to `tick(...)`:

```kotlin
// new params (defaulted, so existing call sites and tests keep compiling)
personalHoldings: Array<RawPersonalHolding> = emptyArray(),
holdingOwnerLevels: Map<String, Int> = emptyMap(),   // actorUuid -> level, resolved impurely before tick
```

Add to `TickResult` (mirroring how `groups` / `warThreatOffers` were added):

```kotlin
val updatedPersonalHoldings: Array<RawPersonalHolding> = emptyArray(),  // lastIncomeTurn / lifetimeIncomeGold advanced
val holdingIncomeOffers: Array<HoldingIncomeLine> = emptyArray(),        // per-PC accrued income for the offer card
```

where `HoldingIncomeLine` is a small pure jsMain/commonMain data class
`(ownerUserId, ownerLabel, holdingId, holdingName, gold, luxuries, favors)`. The tick only
**computes and records** accrual (advances `lastIncomeTurn`, bumps `lifetimeIncomeGold`); it posts
nothing. The impure `performEndTurn` (`TurnWizardApplication.kt`) reads `holdingIncomeOffers` and
posts the GM-confirmed income offer (§5), exactly like it posts war-threat / caravan cards today.

Damage is **not** applied inside the tick's income pass — it originates from war-threat expiry,
event resolution, and raids, each of which already runs at End Turn or on GM action (§6), and each
emits its own damage **offer** rather than mutating a holding directly.

---

## 4. UI Design

### 4.1 "My Holdings" — per-user card on the Kingdom Sheet

- New section under an existing tab (no new nav entry needed — reuse the sheet's holdings/personal
  area, or attach to the Turn/Overview tab): `src/jsMain/resources/applications/kingdom/sections/holdings/page.hbs`
  and a reusable `holding-card.hbs`.
- **Per-user filtering.** A player sees only holdings they own (resolved via the `Leaders.kt`
  `actor.isOwner` pattern, §2.3); the **GM sees all**, grouped by owner, with grant/manage controls.
  This mirrors the shipped player-facing collaborative view.
- Each card shows: title (if any) + holding name, `kind` icon, location (hex label or settlement +
  structure), **condition chip** (sound/damaged/destroyed, colour-coded like the diplomacy attitude
  chips), this turn's projected income, and `lastEventLabel` as a one-line hint ("Raided by the
  Tiger Lords last turn").
- **Single root element** per template (ApplicationV2 requirement noted in project memory).

### 4.2 GM grant dialog — `GrantHolding.kt`

New file `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/dialogs/GrantHolding.kt` (a Foundry
`FormApplication`-style dialog, matching `ModifyFactionStanding` / `AddWarThreat`). Fields:

- Owner: a select of PC actors (the players' assigned leader actors; free-text `ownerLabel` fallback).
- Title (free text, optional), holding name, `kind` (select).
- Location: radio between **hex** (a `hexKey` select populated from `kingmaker.region.hexes`, same
  source `HexContentManager` uses) and **settlement structure** (`sceneId` select from
  `kingdom.settlements` + optional structure ref).
- `incomeTier` (Modest / Comfortable / Lavish).
- **Guardrail:** the dialog refuses to grant a 3rd holding to a PC already at `MAX_HOLDINGS_PER_PC`
  (2), surfacing an i18n error — enforcing the "flavor, not economy sim" discipline in the UI.

GM-only management actions on each card (all `if (!game.user.isGM) return`): edit, change tier,
force condition (sound/damage/destroy for narrative reasons), transfer owner, revoke.

### 4.3 Context objects — `PersonalHoldingsContext.kt`

`src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/sheet/contexts/PersonalHoldingsContext.kt`:

```kotlin
@JsPlainObject
external interface PersonalHoldingsSectionContext {
    val holdings: Array<HoldingCardContext>
    val isGM: Boolean
    val canGrant: Boolean            // isGM
    val emptyLabel: String
}

@JsPlainObject
external interface HoldingCardContext {
    val id: String
    val title: String?
    val name: String
    val kindLabel: String            // localized
    val ownerLabel: String
    val locationLabel: String        // hex label or "Silver Stag, Tuskwater"
    val conditionValue: String       // "sound" | "damaged" | "destroyed"
    val conditionLabel: String       // localized
    val incomeLabel: String          // "50 gp, 1 luxury, 2 favors / turn"
    val lastEventLabel: String?
    val isOwner: Boolean
    val isGM: Boolean
}
```

### 4.4 i18n namespace

Nested under `pf2e-kingmaker-tools` in `lang/en.json` (i18next nested paths — **never** flat-dotted;
wired through `initLocalization()`). All keys under `kingdom.personalHoldings.*`:

```json
"kingdom": {
  "personalHoldings": {
    "title": "My Holdings",
    "empty": "You hold no titles or lands yet.",
    "grant": "Grant Holding",
    "kind": { "manor": "Manor", "lodge": "Hunting Lodge", "tavern-stake": "Tavern Stake",
              "farmstead": "Farmstead", "workshop": "Workshop", "other": "Holding" },
    "tier": { "1": "Modest", "2": "Comfortable", "3": "Lavish" },
    "condition": { "sound": "Sound", "damaged": "Damaged", "destroyed": "Destroyed" },
    "income": "{{gold}} gp, {{luxuries}} luxuries, {{favors}} favors / turn",
    "maxReached": "This character already holds the maximum of {{max}} holdings.",
    "offer": {
      "incomeTitle": "Holdings Income",
      "incomeBody": "{{owner}} accrued {{gold}} gp from {{name}} this turn.",
      "award": "Award Income",
      "damageTitle": "Holding Damaged",
      "damageBody": "{{name}} ({{owner}}) was {{severity}} by {{cause}}.",
      "applyDamage": "Apply Damage", "waive": "Waive",
      "repairTitle": "Repair Holding",
      "repairBody": "Restore {{name}} for {{cost}} gp?", "repair": "Pay & Repair"
    }
  }
}
```

`scripts/check_i18n_keys.py` must pass (nested-object guard).

---

## 5. Chat / Offer Surfaces (GM-Confirmed Only)

Every mechanical benefit is a **GM-confirmed offer** posted via `postChatTemplate(templatePath =
"chatmessages/holding-*.hbs", templateContext = …)` (the exact pattern `postQuestDeadlineOffer` uses)
with `data-*` attributes handled by new `ChatButton("km-offer-holding-…")` handlers in
`src/jsMain/kotlin/.../kingdom/ChatButtons.kt`. Each handler starts `if (!game.user.isGM) return@ChatButton`
and is idempotent.

### 5.1 Offer catalog

| Trigger | Template | ChatButton id(s) | Handler behavior |
|---------|----------|------------------|------------------|
| **Income accrued** at End Turn (`TickResult.holdingIncomeOffers` non-empty) | `chatmessages/holding-income-offer.hbs` | `km-offer-holding-income` / dismiss | GM clicks **Award Income** → posts a public award line and (optionally, GM-gated) writes coins to the linked PC actor via `actor.inventory.addCoins({ gp })`; marks the turn's line consumed. **Default is the chat award; no silent PC-inventory write.** |
| **Holding damaged** by war threat / event / raid (§6) | `chatmessages/holding-damage-offer.hbs` | `km-offer-holding-damage` / `km-waive-holding-damage` | **Apply Damage** → `applyHoldingDamage(holding, severity, cause)`, `actor.setKingdom(kingdom)`, post confirmation. **Waive** → no state change, records nothing. |
| **Repair** offered when a damaged/destroyed holding exists (from the sheet card or auto-offered next turn) | `chatmessages/holding-repair-offer.hbs` | `km-offer-holding-repair` | **Pay & Repair** → deducts `repairCost(tier, condition)` (from kingdom treasury RP-equivalent or PC gold, GM's choice in the card), sets `repairedCondition(current)`, saves. |

### 5.2 Where personal gold income lands — **RECOMMENDATION**

**Recommend an OFFER the GM applies, not a silent PC-inventory write.** Rationale:

- The module's ironclad rule is that anything granting a mechanical benefit is a GM-confirmed offer
  (`km-offer-*`). A silent write to a player's coin purse would be the first exception — and the one
  most likely to cause "where did this gold come from?" confusion mid-session.
- The **durable ledger** lives on the holding (`lifetimeIncomeGold`, `lastIncomeTurn`) and is advanced
  by the pure tick, so the *record* of income is authoritative and previewable. The *handoff* of gp is
  the GM's click.
- On **Award Income**, the handler may — GM-gated and opt-in — call `actor.inventory.addCoins({ gp: n })`
  on the resolved PC actor (the same `typeSafeUpdate`/actor-write capability `km-offer-companion-levelup`
  uses to bump a PF2e actor). But the **default and safe path is a public chat award** the GM reads out,
  keeping the module's zero-silent-write invariant intact.

### 5.3 Digest discipline

One **income digest card per End Turn** listing every PC's accrued line (not one card per holding),
mirroring the faction-moves digest — avoids chat spam when several PCs have holdings.

---

## 6. Interactions With Existing Systems

Concrete files and the exact damage hook points.

| System | File(s) | Interaction |
|--------|---------|-------------|
| **Turn tick / income** | `kingdom/TurnTickingEngine.kt`, `kingdom/dialogs/TurnWizardApplication.kt` (`performEndTurn`) | Income accrues in `tick()`; `performEndTurn` posts the income digest offer. Preview/commit parity preserved (accrual is deterministic; offers post only on commit). |
| **War & War Pressure (damage hook #1)** | `kingdom/ArmyWarPressure.kt`, `kingdom/data/RawWarThreat.kt` | When a threat's escalation hits max and fires (unless `pauseOnExpiry`), match `RawWarThreat.targetHexLocation` against each holding's `boundHexKey`, and `targetSettlementSceneId` against each holding's `structureSceneId`. Matching holdings → emit a **`km-offer-holding-damage`** (severity MAJOR for a triggered siege, MINOR for a raid tick). |
| **Siege / settlement damage (damage hook #2)** | sibling card **`gap0709-siege-damage`** | That work introduces settlement/structure damage on siege resolution; this plan subscribes to it: a siege that damages a settlement offers damage to holdings whose `structureSceneId` is that settlement. Cross-linked so the two land coherently. |
| **Kingdom events (damage hook #3)** | `ChatButtons.kt` (`km-resolve-event`, `km-set-structure-hp`), `data/events/` | Events that "sack," "burn," or "raid" a hex/settlement (already resolved via GM offer cards) gain an optional follow-on holding-damage offer for holdings at that location. Reuses the structure-HP mental model. |
| **Hex claim state (damage hook #4)** | `kingmaker.state.hexes[hexKey].claimed` (read exactly as `camping/CampingUtils.kt` does) | If a hex-bound holding's `boundHexKey` becomes **unclaimed** (lost territory), offer MINOR damage — losing the land degrades the holding. Read-only check; never writes hex state. |
| **Structures / settlements** | `commonMain/.../modifiers/evaluation/EvaluateStructures.kt`, `kingdom/structures/RawSettlement.kt` | Structure-bound holdings reference a settlement `sceneId` (+ optional `structureRef`). `EvaluateStructures` is **not modified** — holdings read settlement identity for display/binding only; they do not add kingdom-wide structure bonuses. |
| **Titles ↔ leadership roles** | `commonMain/.../leaders/Leader.kt`, `kingdom/Leaders.kt` | `title` is **cosmetic** and independent of the `Leader` role; a PC can hold a title without a role and vice-versa. Ownership resolution reuses `getOwnedLeaderRoles`' `actor.isOwner` mechanism. |
| **Petition Inbox (parent)** | [`2026-07-09-plan-petition-inbox.md`](2026-07-09-plan-petition-inbox.md) | Petitions today address a `targetRole: Leader`. This plan lets a petition **also** be addressed to a titled holder: the petition greeting can interpolate `holding.title` ("To the Baron of the Tuskwater…"), and holdings become a natural petition subject ("your tenants at Silverstead petition…"). Integration is a one-field bridge (surface `title` to the petition template); the systems ship independently. |
| **Turn History gazette** | `kingdom/TurnHistory.kt` | Optional: a holding damaged/destroyed emits one public gazette line ("Silverstead Manor was raided"). Low priority; can defer to a follow-up. |
| **Daily tick** | `camping/DailyTickHooks.kt` | **No interaction.** Personal income is monthly (End Turn) only. |

### 6.1 Explicit OUT-OF-SCOPE

- **No holding-vs-holding economy, no markets, no upkeep costs.** Income is one-way flavor; there is
  no supply chain, no rent modeling, no depreciation beyond damage. This is the "not an economy sim"
  line, held deliberately.
- **No new kingdom-wide bonuses.** A holding never grants item bonuses, skill bonuses, or structure
  effects — `EvaluateStructures` is untouched.
- **No auto-grant.** Milestone/deed-triggered granting (§8, Phase 5) is an optional later phase; the
  MVP is GM-dialog grant only.
- **No token/tile placement** for holdings on the map (they are bound *logically* to a hex/settlement).
- **No silent PC-inventory writes** (see §5.2).
- **No cap above 2 holdings/PC**, and no per-settlement stockpile modeling.

---

## 7. Test Plan

### 7.1 commonTest (pure, JVM/JS-agnostic) — `src/commonTest/kotlin/at/posselt/pfrpg2e/data/kingdom/PersonalHoldingsTest.kt`

| Test | Asserts |
|------|---------|
| `holdingIncome_scalesByTierAndLevel` | Modest/Comfortable/Lavish at L1/L5/L10/L20 match §3.2 table exactly. |
| `holdingIncome_capsLevelAt20` | Level 25 yields the same as level 20. |
| `holdingIncome_floorsAtLevel1` | Level 0 / negative treated as 1. |
| `conditionAdjustedIncome_damagedHalvesGoldAndLuxuries` | Damaged Lavish@L10 = 50 gp, 0 luxuries (1/2 floored), 2 favors. |
| `conditionAdjustedIncome_destroyedIsZero` | Destroyed → `HoldingIncome.ZERO`. |
| `nextConditionAfterDamage_soundMinorToDamaged` | SOUND + MINOR → DAMAGED. |
| `nextConditionAfterDamage_damagedMinorToDestroyed` | DAMAGED + MINOR → DESTROYED. |
| `nextConditionAfterDamage_majorToDestroyed` | Any + MAJOR → DESTROYED. |
| `nextConditionAfterDamage_destroyedIsTerminal` | DESTROYED + any → DESTROYED. |
| `repairCost_matchesTable` | Damaged/Destroyed costs per tier match §3.1. |
| `repairedCondition_stepsOneLevel` | DESTROYED→DAMAGED→SOUND (rebuild = two repairs). |
| `holdingIncome_plus_accumulates` | `HoldingIncome` addition sums fields. |

### 7.2 jsTest (Foundry-integrated) — `src/jsTest/kotlin/at/posselt/pfrpg2e/kingdom/PersonalHoldingsJsTest.kt`

| Test | Asserts |
|------|---------|
| `applyHoldingDamage_returnsCopyWithoutMutating` | Input `RawPersonalHolding` unchanged; result has advanced condition + `lastEventLabel`. |
| `accrueIncome_idempotentWithinTurn` | Second `accrueIncome` for the same `currentTurn` returns ZERO. |
| `tick_accruesIncomeAndAdvancesLedger` | `TurnTickingEngine.tick()` with 2 holdings populates `holdingIncomeOffers` and bumps `lastIncomeTurn`/`lifetimeIncomeGold`. |
| `tick_previewCommitParity` | Preview and commit ticks produce identical `updatedPersonalHoldings` + `holdingIncomeOffers`. |
| `tick_destroyedHoldingYieldsNoIncome` | Destroyed holding contributes no offer line. |
| `context_filtersByOwnerForPlayers` | `PersonalHoldingsSectionContext` shows only owned holdings for a non-GM; GM sees all. |
| `grant_rejectsThirdHolding` | Grant path enforces `MAX_HOLDINGS_PER_PC`. |
| `migration49_seedsEmptyArray` | Kingdom without `personalHoldings` gets `[]`; existing array untouched. |
| `migrationChain_contiguous` | Updated `(17..49)` assertion passes. |

### 7.3 Manual Foundry verification checklist

1. Open Kingdom Sheet as GM → **Grant Holding** → grant "Silverstead Manor" (Comfortable, hex-bound) to a PC actor with title "Baron of the Tuskwater."
2. Grant a 2nd holding to the same PC (structure-bound, Lavish); attempt a 3rd → blocked with the max-reached message.
3. Log in as that PC's player → **My Holdings** shows exactly their 2 holdings, income projected; other PCs' holdings hidden.
4. **End Turn** → an income digest offer card appears (GM-whispered). Click **Award Income** → public award posts; `lifetimeIncomeGold` increases; re-clicking does nothing (idempotent).
5. Create a war threat targeting the holding's hex; escalate to trigger → a **Holding Damaged** offer appears. Click **Apply Damage** → card condition flips to Damaged; income halves on the sheet.
6. Click **Waive** on a second damage offer → no change.
7. From the card, **Repair** the damaged holding → cost deducted, condition returns to Sound.
8. Destroy a holding (major damage) → income 0; repair twice (Destroyed→Damaged→Sound).
9. Unclaim the bound hex (`kingmaker.state`) → next End Turn offers MINOR damage.
10. Reload the world → holdings, conditions, ledger, and titles persist.
11. Confirm all text resolves via i18n (no raw keys); `scripts/check_i18n_keys.py` clean.

Build/verify per AGENTS.md: `python3 scripts/check_i18n_keys.py` then
`JAVA_HOME=<jdk25> ./gradlew assemble jsTest -x kotlinStoreYarnLock` (Chrome headless).

---

## 8. Phasing (independently committable)

| Phase | Title | Deliverable | Key files |
|-------|-------|-------------|-----------|
| **1** | **Pure core + data + migration** | `HoldingTier`/`HoldingCondition`/`DamageSeverity`/`HoldingIncome` + all pure functions in commonMain with full `PersonalHoldingsTest`; `RawPersonalHolding`; `KingdomData.personalHoldings`; `Migration49` + chain-test bump. | `commonMain/.../data/kingdom/PersonalHoldings.kt`, `PersonalHoldingsTest.kt`, `kingdom/data/RawPersonalHolding.kt`, `KingdomData.kt`, `migrations/migrations/Migration49.kt`, `Migrations.kt`, `MigrationChainTest.kt` |
| **2** | **Tick integration + income offer** | jsMain adapters (`PersonalHoldingsJs.kt`); `tick()` accrual + `TickResult` fields; `performEndTurn` posts the income digest offer; `km-offer-holding-income` handler + template; jsTest for accrual/parity/idempotency. | `PersonalHoldingsJs.kt`, `TurnTickingEngine.kt`, `TurnWizardApplication.kt`, `ChatButtons.kt`, `chatmessages/holding-income-offer.hbs`, `PersonalHoldingsJsTest.kt` |
| **3** | **UI — My Holdings card + GM grant dialog** | Per-user section + card, `PersonalHoldingsContext`, `GrantHolding` dialog with the 2-holding guardrail, GM management actions, i18n. | `sections/holdings/{page,holding-card}.hbs`, `contexts/PersonalHoldingsContext.kt`, `dialogs/GrantHolding.kt`, `KingdomSheet.kt`, `lang/en.json` |
| **4** | **Damage + repair offers + hooks** | `km-offer-holding-damage` / `km-waive-holding-damage` / `km-offer-holding-repair` handlers + templates; wire damage hooks #1 (war-threat expiry), #3 (event resolution), #4 (hex unclaim); repair flow; jsTest. | `ChatButtons.kt`, `ArmyWarPressure.kt`/`TurnWizardApplication.kt` (hook emission), `chatmessages/holding-damage-offer.hbs`, `chatmessages/holding-repair-offer.hbs` |
| **5** | **(Optional) Titles→petition bridge + deed grants** | Surface `title` to Petition Inbox templates; optional milestone/deed-triggered grant offer; siege-damage subscription (coordinate with `gap0709-siege-damage`); gazette lines. | petition templates, `TurnHistory.kt`, milestone hooks |

Phase 1 is standalone (pure + persistence). Phase 2 depends on 1. Phase 3 depends on 1. Phase 4
depends on 2+3. Phase 5 is optional polish and depends on the Petition Inbox + siege-damage siblings.

---

## 9. Open Questions for Gregory

1. **Income handoff:** default to chat-award only (recommended), or enable the opt-in
   `actor.inventory.addCoins` write behind a setting from day one?
2. **Repair funding:** pay repair from the **kingdom treasury** (RP/commodities) or the **PC's own
   gold**? (Plan offers both on the card; which is the default?)
3. **Level basis:** scale income by the **owning PC's level** (recommended, personal) or the
   **kingdom level** (simpler, uniform)?
4. **Favors:** keep purely narrative, or later let favors feed Petition Inbox response options as a
   spendable social currency?
5. **Grant source:** GM-dialog only for MVP (recommended), or also auto-offer holdings on milestones
   (claim-a-region, found-a-settlement) in Phase 5?
6. **Visibility:** should other players see each other's holdings (transparency) or only their own +
   GM (privacy)? Plan defaults to owner + GM via `visibleToPlayers`.

---

**End of Plan.** Ready for review. On approval, implementation cards follow the Phase 1–5 table above.
