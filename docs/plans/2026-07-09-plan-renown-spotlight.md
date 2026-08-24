# Renown & Spotlight — Implementation Plan

> **Status:** Plan only — no implementation yet
> **Date:** 2026-07-09
> **Roadmap item:** New feature (PC Renown / Epithets + per-actor contribution tallies & Spotlight)
> **Depends on:** Faction & Diplomacy Relations Tracker (#1, landed), Player-facing collaborative
> kingdom view + roll-ownership locks (commit `d948f111`), Turn History / Gazette + last-turn recap
> (`postLastTurnRecap`, commit `b080cfe2`), Campaign Analytics dashboard (`TurnAnalytics`), Petition
> Inbox (`docs/plans/2026-07-09-plan-petition-inbox.md`)
> **Branch:** `kingmaker.5`

---

## Executive Summary

Kingdom rolls are already **ownership-locked to characters**: a check is performed *as a leadership
role* (`Leader.RULER … WARDEN`), each role maps to a PF2e actor UUID (`kingdom.leaders.<role>.uuid`),
and non-GM players can only roll the roles they own (`getOwnedLeaderRoles`, `Leaders.kt`). That means
every kingdom check already knows *which PC did it* — but the module throws that identity away the
moment the degree of success is computed. Nothing accrues to the character; nothing remembers that
the Emissary has crit-succeeded eight diplomacy checks in a row.

This plan adds **one credit-attribution substrate** feeding **two features**:

1. **PC Renown & Epithets** — a small, personal, *token-scale* reputation each PC earns with the
   **populace** (kingdom-wide) and **per-faction**. Crossing thresholds awards flavourful
   **epithets** ("The Bridge-Builder", "Restov's Hammer") and a **closed set of two cosmetic perks**
   (a settlement item-purchase *discount tier*; a one-shot *invitation event*), each a GM-confirmed
   offer that never auto-applies.
2. **Per-actor contribution tallies & "Spotlight of the Turn"** — `RawTurnRecord` gains per-actor
   tallies (checks, crits, crit-fails, activities, events) that feed a single light-hearted
   **Spotlight** line in the existing last-turn recap beat (`postLastTurnRecap`) and an optional
   per-player series in the Analytics tab.

Both are driven by exactly one seam: `recordContribution(actorUuid, kind)`. The renown numbers are
kept deliberately **small** so they read as *personal colour*, never as a second faction-standing
economy — see §2.4 (double-count discipline).

---

## 1. Problem Statement + Player/GM Value

**Problem.** The kingdom subsystem is *kingdom-scoped*: RP, unrest, fame, and faction standing all
belong to the realm, not to any character. A player who repeatedly carries the kingdom on their
Emissary's back gets no personal recognition; a GM has no ledger of *who did what* to reward, taunt,
or foreshadow with. The acting-PC identity that roll-ownership already establishes at check time is
discarded immediately after `rollCheck` returns a `DegreeOfSuccess`.

**Value to the table:**

- **Players get personal stakes in kingdom turns.** "Your Emissary just earned *The Silver Tongue*"
  turns a spreadsheet action into a character beat. Renown is *theirs*, unlike shared RP.
- **GM gets a recognition & foreshadowing tool.** Epithet offers and the Spotlight line are prompts:
  reward the star, needle the blunderer, seed an *invitation* quest from a faction that now admires a
  specific PC.
- **Zero new bookkeeping for players.** Renown accrues automatically from checks they already make;
  the Spotlight line is generated, not authored.
- **Advisory, never coercive.** Every mechanical benefit (discount tier, invitation) is a
  GM-confirmed offer. The Spotlight line and epithet flavour are pure colour with no rules weight.

---

## 2. Data Model

### 2.1 The attribution audit — who knows the acting PC *today*

`recordContribution(actorUuid, kind)` can only be called where the acting PC is actually in scope.
Auditing the codebase, the flows split cleanly:

| Flow | Acting-PC known today? | How | Scoped in this plan |
|------|------------------------|-----|---------------------|
| **Kingdom skill check** (`kingdomCheckDialog` → `rollCheck`, `KingdomRoll.kt`) | **YES** | `data.leader: Leader` is in scope at the `rollCheck` call site (`KingdomCheckDialog.kt:481`); resolves to `kingdom.leaders.<role>.uuid` / `parseLeaderActors().resolve(leader)?.uuid`. `rollCheck` returns the `DegreeOfSuccess`, so crit vs. success vs. crit-fail is known there. | **Phase 2 — primary seam** |
| **Activity performed** (`CheckType.PerformActivity`) | **YES** | Same pipeline — an activity is a check with a `RawActivity`; same `leader` in scope. | **Phase 2** |
| **Kingdom event resolved** (`CheckType.HandleEvent`) | **YES** | Same pipeline; the resolving check carries its `leader`. | **Phase 2** |
| **Petition answered** (`RawPetition.targetRole: Leader`, Petition Inbox) | **YES** | A petition is addressed to a role; the answering PC is the owner of `kingdom.leaders[targetRole].uuid`. | **Phase 2** |
| **Battle command** (`ResolveBattle.kt`) | **NO** (not in scope) | `ResolveBattle` resolves army rounds with **no** `leader`/`game.user`/actor UUID in scope (verified: zero such tokens in the file). Armies are conceptually the **General's**, but the General's actor UUID is *not threaded into battle resolution today*. | **OUT OF SCOPE** (see §6.1); optional later seam = resolve `leaders.general.uuid` at battle end |
| **Expedition launch** (`ExpeditionLaunch.kt`) | **NO clean PC** | Expeditions are **companion-scoped** (`companion.actorUuid`) and GM-initiated from the sheet; there is no single "launching PC". Companions already keep their own `careerExpeditions`/`careerTriumphs` on `RawCharacter`. | **OUT OF SCOPE** (companions are not populace-renown PCs; see §6.1) |

**Conclusion that scopes the whole feature:** the only action-time flow that cleanly knows an acting
PC today is the **kingdom check pipeline** (covering checks, crits, activities, and events) plus
**petition answers**. Renown therefore accrues from those four sources. Battle and expedition
attribution are explicitly deferred because the acting-PC identity does not exist at those call sites
without new plumbing — instrumenting them anyway would fabricate attribution and is called out as
out-of-scope rather than hand-waved.

### 2.2 Contribution kinds (pure enum, commonMain)

```kotlin
// src/commonMain/kotlin/at/posselt/pfrpg2e/data/kingdom/RenownEngine.kt
enum class ContributionKind {
    CHECK_SUCCESS,     // any successful kingdom check
    CHECK_CRIT,        // critical success
    CHECK_FAILURE,     // plain failure (no renown, but tallied for the Spotlight)
    CHECK_CRIT_FAIL,   // critical failure — small negative populace renown ("everyone saw that")
    ACTIVITY,          // a RawActivity was performed (independent of degree)
    EVENT_RESOLVED,    // a kingdom event was handled
    PETITION_ANSWERED, // a petition addressed to this PC's role was answered
}
```

### 2.3 New / changed `@JsPlainObject` interfaces (all nullable for migration safety)

#### `RawTurnRecord.kt` — ADDITION only (jsMain: `kingdom/data/RawTurnRecord.kt`)

```kotlin
// existing fields unchanged …
/** Per-actor contribution tallies accrued during this turn. Nullable: legacy records have none,
 *  so the Spotlight simply shows nothing for old turns. */
var contributions: Array<RawTurnContribution>?
```

```kotlin
// src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/data/RawTurnContribution.kt
@JsPlainObject
external interface RawTurnContribution {
    var actorUuid: String
    var actorName: String?   // denormalised for display without a UUID lookup
    var checks: Int          // successful checks this turn
    var crits: Int           // critical successes this turn
    var critFails: Int?      // critical failures (nullable: back-compat)
    var activities: Int      // activities performed this turn
    var events: Int?         // events resolved this turn (nullable: back-compat)
}
```

#### New renown carriers (jsMain: `kingdom/data/RawRenown.kt`)

```kotlin
@JsPlainObject
external interface RawPcRenown {
    var actorUuid: String
    var actorName: String?
    var populace: Int?                            // 0..100, null => 0 (unknown)
    var factionRenown: Array<RawPcFactionRenown>? // personal per-faction renown (see §2.4)
    var epithets: Array<String>?                  // earned epithet ids (see §5 catalog)
    var discountTier: Int?                        // granted perk: 0/1/2 (see §5 perk set)
    var lastOfferedTurn: Int?                     // last turn an epithet/perk offer was emitted (dedup)
}

@JsPlainObject
external interface RawPcFactionRenown {
    var factionName: String   // matches RawGroup.name
    var renown: Int           // personal standing with that faction, token-scale (see §2.4)
}
```

#### `KingdomData.kt` — ADDITIONS only (both nullable, top-level, mirroring `turnHistory`/`groups`)

```kotlin
var renown: Array<RawPcRenown>?                        // cumulative per-PC renown ledger
var currentTurnContributions: Array<RawTurnContribution>? // in-progress tallies for the OPEN turn
```

`currentTurnContributions` is the accumulator for the turn currently in progress; it is flushed into
the new `RawTurnRecord.contributions` at End Turn and then reset (see §3.3). `renown` is the durable
cumulative ledger.

### 2.4 Double-count discipline (the load-bearing design rule)

There are now **two** things that look like "standing with Pitax", and they must never be confused:

| | `RawGroup.standing` (existing) | `RawPcFactionRenown.renown` (new) |
|---|---|---|
| **Owner** | the **KINGDOM** | an individual **PC** |
| **Scale** | ±100, moves in **±5…±10** steps | token, moves in **+1…+3** steps, soft-capped ~±40 |
| **Drives** | war-threat / diplomacy-quest **offers**, caravan raid DC, negotiation | **epithets only** (cosmetic + closed 2-perk set) |
| **Mechanical feedback into kingdom checks?** | yes (existing) | **never** |

A PC's crit-success on a *Send Diplomatic Envoy* toward Pitax raises **both** the kingdom's
`RawGroup.standing` (existing behaviour, unchanged) **and** that PC's personal
`factionRenown["Pitax"]` (new, +2). These are *not* a double-count because the personal number has
**zero mechanical consequence** — it only accumulates toward an epithet. The kingdom standing is the
only number the war/quest/caravan systems ever read. This separation is asserted by tests
(§7.1: `personalRenownNeverFeedsKingdomStanding`).

### 2.5 Persistence — where each thing lives, and why

- **Renown + in-progress tallies → kingdom actor flag (`KingdomData`).** Renown is kingdom-scoped PC
  colour (your reputation *in this realm*); it belongs with the kingdom the PC leads, not the world
  setting (a PC could lead two kingdoms with different renown) and not the camping flag (nothing to
  do with rests).
- **Per-turn tallies → the turn stream (`RawTurnRecord.contributions`).** They are a historical
  snapshot, exactly like every other field on `RawTurnRecord`, and feed Analytics' time series.
- **No world setting, no camping flag.**

### 2.6 Migration

**Migration49** *(placeholder — not free; see caveat)* (the chain currently ends at `Migration61`; `MigrationChainTest`
`registeredVersionsAreContiguous17To48` guards contiguity, so the new class must be registered in
`Migrations.kt` and the assertion bumped to `…17To49`). *Gregory sequences the real number at
implementation time if other migrations land first.*

> ⚠️ **The number in this section is stale and must be re-derived at implementation.** The chain
> ends at `Migration61`, not 48, and 62–67 are already proposed by the downtime-projects,
> scheduled-pressure, petition-inbox, npc-memory, seasonal-economy and loot-manifests plans. Nine
> unimplemented plans currently name `Migration49`, so it is not free for any of them. Take the
> next contiguous number when this actually lands, and update `MigrationChainTest`.


```kotlin
// migrations/migrations/Migration49.kt
class Migration49 : Migration(49) {
    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        if (kingdom.renown == null) kingdom.renown = arrayOf<dynamic>()
        if (kingdom.currentTurnContributions == null) kingdom.currentTurnContributions = arrayOf<dynamic>()
        // RawTurnRecord.contributions is left null on legacy records (Spotlight shows nothing for old turns).
    }
}
```

Non-breaking: every new field is nullable; legacy saves load unchanged and simply show no renown and
no Spotlight until the first instrumented check runs.

---

## 3. Engine Design

### 3.1 Pure core (commonMain, UI-free — mirrors `FactionRelations.kt`)

File: `src/commonMain/kotlin/at/posselt/pfrpg2e/data/kingdom/RenownEngine.kt`, package
`at.posselt.pfrpg2e.data.kingdom`. It operates on **plain value types** (not the JS `Raw*` carriers),
exactly as `FactionRelations` operates on `Int` rather than `RawGroup`. A thin jsMain adapter
(`RenownAdapter.kt`) converts `RawPcRenown ⇆ PcRenown`.

```kotlin
const val MAX_POPULACE_RENOWN = 100
const val MIN_POPULACE_RENOWN = 0
const val FACTION_RENOWN_SOFT_CAP = 40   // personal per-faction renown clamps to ±this

data class PcRenown(
    val actorUuid: String,
    val populace: Int = 0,
    val factionRenown: Map<String, Int> = emptyMap(),
    val epithets: Set<String> = emptySet(),
    val discountTier: Int = 0,
)

data class TurnTally(
    val actorUuid: String,
    val actorName: String? = null,
    val checks: Int = 0,
    val crits: Int = 0,
    val critFails: Int = 0,
    val activities: Int = 0,
    val events: Int = 0,
)
```

#### Source table with AMOUNTS (the single source of truth for accrual)

```kotlin
/** Renown deltas per contribution. Kept small on purpose (see §2.4). `factionName` non-null routes
 *  the faction portion to per-faction renown; the populace portion always applies. */
fun renownDeltaFor(kind: ContributionKind): RenownDelta = when (kind) {
    ContributionKind.CHECK_CRIT       -> RenownDelta(populace = +3, faction = +2)
    ContributionKind.CHECK_SUCCESS    -> RenownDelta(populace = +1, faction = +1)
    ContributionKind.CHECK_FAILURE    -> RenownDelta(populace =  0, faction =  0) // tally only
    ContributionKind.CHECK_CRIT_FAIL  -> RenownDelta(populace = -2, faction = -1)
    ContributionKind.ACTIVITY         -> RenownDelta(populace = +1, faction =  0)
    ContributionKind.EVENT_RESOLVED   -> RenownDelta(populace = +2, faction =  0)
    ContributionKind.PETITION_ANSWERED-> RenownDelta(populace = +2, faction =  0)
}
data class RenownDelta(val populace: Int, val faction: Int)
```

Amounts chosen so a *very* active PC gains ~15–25 populace renown per busy turn's worth of checks —
epithet thresholds (§5) sit at 20/30/40/50, i.e. several turns of sustained contribution, not one.

#### Pure signatures

```kotlin
/** Apply one contribution to a PC's renown, clamped. `factionName` non-null also moves that faction's
 *  personal renown. Returns a NEW PcRenown (immutable). */
fun accrueRenown(current: PcRenown, kind: ContributionKind, factionName: String?): PcRenown

/** Which epithets does this PC now qualify for that they do not already hold? Pure over the
 *  cumulative renown plus optional lifetime role-tally context (see EpithetContext). */
fun evaluateEpithets(renown: PcRenown, ctx: EpithetContext): List<EpithetAward>

/** Perk (if any) that a newly-earned epithet grants, as a CLOSED set. */
fun perkForEpithet(epithetId: String): RenownPerk?   // DiscountTier(n) | Invitation(factionName?) | null

/** Pick the single Spotlight subject for a turn from the turn's tallies (top contributor by a
 *  weighted score; deterministic tie-break by actorUuid). Returns null when nobody contributed. */
fun spotlightOfTheTurn(tallies: List<TurnTally>): SpotlightPick?

data class SpotlightPick(val actorUuid: String, val actorName: String?, val kind: SpotlightKind, val count: Int)
enum class SpotlightKind { BUSIEST, CRIT_STAR, BLUNDERER, DIPLOMAT, EVENT_HERO }  // selects the i18n flavour line

sealed interface RenownPerk {
    data class DiscountTier(val tier: Int) : RenownPerk       // consumed by the item-purchase display
    data class Invitation(val factionName: String?) : RenownPerk // spawns an invitation quest offer
}

data class EpithetAward(val epithetId: String, val perk: RenownPerk?)
data class EpithetContext(  // lifetime role-scoped counts, derived from renown + turn history
    val roleSuccesses: Map<String, Int> = emptyMap(),   // e.g. "treasurer" -> 15
    val critSuccesses: Int = 0,
    val eventsResolved: Int = 0,
    val holdsRulerRole: Boolean = false,
)
```

`spotlightOfTheTurn`'s weighting: `crits*3 + events*2 + activities*1 + checks*1`, with a special-case
`BLUNDERER` pick when a PC's `critFails` dominates — the light-hearted "worst of the turn" line. Being
pure, it is fully unit-tested with fixed inputs (no RNG, no clock).

### 3.2 Impure adapter + the seam (jsMain)

```kotlin
// src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/RecordContribution.kt
/** THE seam. Mutates the kingdom's in-progress tally + cumulative renown for one deed.
 *  Idempotent-per-call (each call is one deed). Never emits offers itself — epithet/perk offers are
 *  batched at End Turn (§3.3) to avoid mid-turn chat spam. */
fun recordContribution(
    kingdom: KingdomData,
    actorUuid: String,
    actorName: String?,
    kind: ContributionKind,
    factionName: String? = null,
) {
    // 1) bump currentTurnContributions[actorUuid].<field> (create row if absent)
    // 2) renown = renown updated via accrueRenown(toPcRenown(row), kind, factionName) -> fromPcRenown
    // Caller is responsible for kingdomActor.setKingdom(kingdom) (batched with its own write).
}
```

Instrumented call sites (all confirmed to have the acting PC in scope, §2.1):

- `KingdomRoll.rollCheck(...)` — add a nullable `leaderActorUuid: String?` param (threaded from the
  `rollCheck` call in `KingdomCheckDialog.kt:481`, where `data.leader` resolves the UUID). After the
  degree is computed, map degree → `ContributionKind` and call `recordContribution`. One call handles
  checks, crits, crit-fails; a second `ACTIVITY`/`EVENT_RESOLVED` call fires when `activity`/`event`
  is non-null. `factionName` is populated when the activity/event targets a `RawGroup` (diplomacy).
- Petition answer handler (Petition Inbox `km-offer-*` / option button) — resolves
  `kingdom.leaders[petition.targetRole].uuid` and calls `recordContribution(..., PETITION_ANSWERED)`.

### 3.3 Tick surface — compose at the End-Turn recap (`TurnTickingEngine` surface)

Respecting the tick split (no third tick): renown accrues *immediately* on each deed (personal colour
can move mid-turn), but everything that **composes** — the Spotlight line and the batched epithet/perk
offers — happens at **End Turn**, on the monthly `TurnTickingEngine` surface, specifically where
`TurnWizardApplication` already builds the record (`appendTurnRecord` / `buildTurnRecord`,
`TurnWizardApplication.kt:525`) and posts the recap (`postLastTurnRecap`, `:709`). Daily
`DailyTickHooks` is **not** touched.

At End Turn, in order:

1. **Flush tallies:** copy `kingdom.currentTurnContributions` into the new
   `RawTurnRecord.contributions` passed to `buildTurnRecord`, then reset
   `kingdom.currentTurnContributions = emptyArray()`.
2. **Evaluate epithets:** for each PC in `kingdom.renown`, `evaluateEpithets(renown, ctx)`; for each
   newly-earned epithet emit **one** GM-confirmed offer card (§5), guarded by `lastOfferedTurn` +
   "epithet not already held" so it fires once.
3. **Compose Spotlight:** `spotlightOfTheTurn(tallies)` → a single localized line added to
   `LastTurnRecap` (new nullable `spotlight: String?` field) and rendered in the recap chat card.
   The Spotlight line is **pure flavour, not an offer** (no mechanical benefit → no confirmation).

`computeLastTurnRecap` (pure, `TurnHistory.kt`) gains a `spotlight: String?` derived from the latest
record's `contributions` via `spotlightOfTheTurn`, so the recap stays unit-testable.

---

## 4. UI Design

### 4.1 Renown card — on the Roster (recommended home)

**Recommendation: the Roster.** PCs already live in the Roster panel (`RosterPanel.kt` +
`applications/kingdom/sections/roster/…`), which is where a player looks for "my character in this
kingdom". A per-PC **Renown card** there is the least-surprising home and needs no new nav entry. (A
duplicate compact strip can later appear in the leader/companion profile, but the Roster is the
canonical surface — declining a separate "Renown" nav tab keeps parity with how Diplomacy reused the
Trade Agreements section rather than adding nav.)

**Files:**
- `src/jsMain/resources/applications/kingdom/sections/roster/renown-card.hbs` — one card per PC:
  populace renown bar, earned epithet chips, per-faction renown chips.
- `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/sheet/contexts/RenownContext.kt` — context objects.

**Context objects:**

```kotlin
@JsPlainObject
external interface PcRenownContext {
    val actorUuid: String
    val name: String
    val populace: Int
    val populacePct: Int              // 0-100 for the bar
    val attitudeLabel: String         // localized populace band label
    val epithets: Array<EpithetChipContext>
    val factionRenown: Array<FactionRenownChipContext>
    val discountTier: Int             // 0 = none
    val isGM: Boolean                 // gates the "Adjust Renown" GM control
    val isOwn: Boolean                // this viewer owns this PC (players see their own fully)
}

@JsPlainObject external interface EpithetChipContext { val id: String; val label: String; val hint: String? }
@JsPlainObject external interface FactionRenownChipContext { val factionName: String; val renown: Int; val label: String }
```

**Visibility:** players see their own PC's card fully and other PCs' epithets (public honours) but not
raw internal numbers unless `isGM`; the GM sees everything plus an **Adjust Renown** button opening
`ModifyPcRenown` (set/clear populace or faction renown with a reason; manually grant/revoke an epithet
— all still GM-side, no auto-apply concerns since the GM *is* the authority).

### 4.2 Analytics — optional per-player series

`AnalyticsContext` gains an optional per-actor contribution series built by extending
`TurnAnalytics.extractSeries` to read `RawTurnRecord.contributions` (e.g. metric
`"contribution:<actorUuid>:crits"`), rendered by the existing `sections/analytics/metric-chart.hbs`.
Zero new chart code — reuses `mapSeriesToCoordinates`/`summarizeSeries`.

### 4.3 i18n namespace

All keys nested under `kingdom.renown.*` in `lang/en.json` (nested objects, never flat-dotted; new
catalog is already loaded via `initLocalization()` since it lives in the one `en.json`):

```json
"kingdom": {
  "renown": {
    "title": "Renown",
    "populace": "Populace",
    "band": { "unknown": "Unknown", "known": "Known", "renowned": "Renowned", "celebrated": "Celebrated", "legendary": "Legendary" },
    "epithet": {
      "bridgeBuilder": { "label": "The Bridge-Builder", "hint": "Forged ties across the realm." },
      "restovsHammer": { "label": "Restov's Hammer", "hint": "A martial faction's champion." }
      /* … one entry per epithet in §5 … */
    },
    "perk": { "discountTier": "Favoured Customer (tier {{tier}})", "invitation": "Invitation" },
    "spotlight": {
      "busiest": "🌟 Spotlight: {{name}} never stopped working ({{count}} actions).",
      "critStar": "🌟 Spotlight: {{name}} was on fire — {{count}} critical successes!",
      "blunderer": "😬 Spotlight: {{name}} had… a turn ({{count}} critical fumbles).",
      "diplomat": "🌟 Spotlight: {{name}} charmed the realm this turn.",
      "eventHero": "🌟 Spotlight: {{name}} steadied the kingdom through {{count}} crises."
    },
    "offer": {
      "epithet": "{{name}} has earned the epithet {{epithet}}.",
      "discount": "{{name}}'s renown could earn them a favoured-customer discount.",
      "invitation": "{{faction}} wishes to fête {{name}} — spawn an invitation?"
    },
    "dialog": { "adjust": "Adjust Renown", "reason": "Reason", "grantEpithet": "Grant Epithet", "revokeEpithet": "Revoke Epithet" }
  }
}
```

---

## 5. Chat / Offer Surfaces (GM-Confirmed Only)

Every renown consequence that grants a **mechanical** benefit is a GM-confirmed offer, whispered to
GMs, using the established `km-offer-*` `ChatButton` pattern in
`kingdom/ChatButtons.kt` (each handler is `{ game, actor, event, button -> … }`, guarded by
`if (!game.user.isGM) return@ChatButton`, reading `button.dataset[...]`, mutating via
`actor.getKingdom()` / `actor.setKingdom(...)` — mirroring `km-offer-expedition-reward` and
`km-offer-companion-levelup`). The Spotlight line is **not** an offer (no mechanical benefit).

| Trigger (at End Turn) | Offer `ChatButton` id | Buttons | Handler behaviour |
|-----------------------|-----------------------|---------|-------------------|
| PC crosses an epithet threshold | `km-offer-renown-epithet` | [Grant Epithet] [Dismiss] | Add `epithetId` to that PC's `RawPcRenown.epithets`; post a public flavour line. Pure honour, no rules effect. |
| Epithet whose perk is a discount tier | `km-offer-renown-perk-discount` | [Grant Discount Tier] [Dismiss] | Set `RawPcRenown.discountTier = tier`. **Consumed by the item-purchase display** (§6). |
| Epithet whose perk is an invitation | `km-offer-renown-invitation` | [Create Invitation] [Dismiss] | Open `AddQuest` prefilled (invitation event; faction as giver when known) — reuses the existing quest pipeline. |

**Perk vocabulary is a CLOSED set of two** (`RenownPerk`): `DiscountTier(tier)` and
`Invitation(factionName?)`. No other perk types exist; renown can never grant kingdom bonuses, XP,
RP, or check modifiers. This bounds the feature and is what keeps renown "colour, not economy".

**Dedup:** offers carry the target turn + PC; `lastOfferedTurn` on `RawPcRenown` plus
"epithet not already held" / "discountTier not already ≥ tier" guards ensure each offer fires once,
matching the crossing-once discipline of `shouldOfferWarThreat`.

---

## 6. Interactions with Existing Systems

| System | File(s) | Interaction |
|--------|---------|-------------|
| **Roll ownership / leaders** | `KingdomRoll.kt`, `KingdomCheckDialog.kt`, `Leaders.kt`, `data/RawLeaders.kt`, `data/kingdom/leaders/LeaderActors.kt` | The seam. `rollCheck` gains `leaderActorUuid: String?`; `data.leader` → `kingdom.leaders.<role>.uuid` resolves the acting PC. |
| **Turn history / recap** | `TurnHistory.kt`, `data/RawTurnRecord.kt`, `dialogs/TurnWizardApplication.kt` (`buildTurnRecord` @ :525, `postLastTurnRecap` @ :709) | `RawTurnRecord.contributions` flushed here; `computeLastTurnRecap` gains `spotlight`; recap chat card renders the Spotlight line. |
| **Petition Inbox** | `docs/plans/2026-07-09-plan-petition-inbox.md` (RawPetition.targetRole) | Answering a petition credits the owning leader PC via `PETITION_ANSWERED`. |
| **Faction relations** | `data/kingdom/FactionRelations.kt`, `data/RawGroup.kt` | Per-faction *personal* renown is a **separate** carrier (`RawPcFactionRenown`); it never touches `RawGroup.standing` (§2.4). |
| **Settlement benefits** | `dialogs/InspectSettlement.kt` (`itemPurchaseLevel`, `availableItemLevels` price display) | The `discountTier` perk is **consumed** here: when a PC with a discount tier is the shopper, apply a small price reduction in the item-purchase display (e.g. tier1 −5%, tier2 −10%). Read-only consumer of the perk. |
| **Analytics** | `TurnAnalytics.kt`, `sections/analytics/metric-chart.hbs` | Optional per-actor contribution series reuses `extractSeries`/`mapSeriesToCoordinates`. |
| **Quest generator** | `dialogs/AddQuest.kt` | The `Invitation` perk offer opens `AddQuest` prefilled. |
| **Migrations** | `migrations/Migrations.kt`, `migrations/migrations/Migration49.kt`, `MigrationChainTest` | Register Migration49; bump contiguity assertion to `17..49`. |
| **Daily tick** | `DailyTickHooks.kt` | **No interaction** — renown composes on the monthly turn surface only. |

### 6.1 Explicit OUT-OF-SCOPE

- **Battle-command attribution.** `ResolveBattle.kt` has no acting-PC in scope today (§2.1). A future
  seam could credit `leaders.general.uuid` at battle end, but this plan does **not** instrument battle
  resolution — no `BATTLE` contribution kind ships.
- **Expedition-launch attribution.** Expeditions are companion-scoped and GM-initiated; there is no
  single launching PC. Companions keep their own `careerExpeditions`/`careerTriumphs` on
  `RawCharacter`; those are **not** populace renown and are not merged here.
- **Renown affecting kingdom checks.** No feedback loop: renown never modifies a DC, roll, or
  resource. Perks are the closed 2-element set only.
- **PC-vs-PC competition mechanics**, leaderboards, or renown decay races. Renown is sticky by design
  (an *optional* GM toggle for slow monthly fade-toward-zero is noted as a future switch, default off —
  epithets are earned, not rented).
- **Cross-kingdom persistence.** Each kingdom actor tracks its own renown ledger.
- **Retroactive backfill** of renown from historical `turnHistory` (legacy turns have no
  `contributions`); renown starts accruing from first instrumented check post-upgrade.

---

## 7. Test Plan

### 7.1 commonTest (pure, JVM-less) — `RenownEngineTest.kt`

| Test | Asserts |
|------|---------|
| `accrueRenown_critAddsPopulaceAndFaction` | CHECK_CRIT with a faction → +3 populace, +2 that faction. |
| `accrueRenown_successAmounts` | CHECK_SUCCESS → +1/+1; ACTIVITY → +1/0; EVENT_RESOLVED → +2/0. |
| `accrueRenown_critFailIsNegativeButFloored` | CHECK_CRIT_FAIL → −2 populace, clamped ≥ 0. |
| `accrueRenown_clampsPopulaceAndFactionSoftCap` | populace ∈ [0,100]; faction ∈ [−40,40]. |
| `accrueRenown_isImmutable` | input `PcRenown` unchanged; new instance returned. |
| `evaluateEpithets_firesOnceOnCrossing` | crossing a threshold awards; re-eval at/above it does **not** re-award. |
| `evaluateEpithets_negativeNemesisEpithet` | faction renown ≤ −25 with Pitax → "Scourge of Pitax". |
| `perkForEpithet_isClosedSet` | only `DiscountTier`/`Invitation`/null ever returned. |
| `spotlight_picksTopContributorWeighted` | crits outweigh checks; deterministic tie-break by uuid. |
| `spotlight_blundererWhenCritFailsDominate` | a crit-fail-heavy turn selects `BLUNDERER`. |
| `spotlight_nullWhenNobodyContributed` | empty tallies → null. |
| `personalRenownNeverFeedsKingdomStanding` | the engine exposes no path from `PcRenown` to `RawGroup.standing` (compile-level + doc test). |

### 7.2 jsTest (Foundry-integrated)

| Test | Asserts |
|------|---------|
| `recordContribution_bumpsTallyAndRenown` | one CHECK_CRIT updates `currentTurnContributions` and `renown`. |
| `recordContribution_createsRowForNewActor` | first deed for a PC creates its row. |
| `endTurn_flushesTalliesIntoRecordAndResets` | `RawTurnRecord.contributions` populated; `currentTurnContributions` reset to empty. |
| `computeLastTurnRecap_includesSpotlight` | recap `spotlight` non-null when a contributor exists. |
| `endTurn_emitsEpithetOfferOnce` | crossing a threshold whispers one `km-offer-renown-epithet`; next turn without a new crossing emits none. |
| `discountTierConsumedInInspectSettlement` | a PC with `discountTier=2` shopping sees the reduced item-purchase price. |
| `migration49_backfillsRenownAndTallies` | run through the chain → `renown`/`currentTurnContributions` defined; contiguity `17..49` holds. |
| `analyticsPerPlayerSeriesExtracts` | `extractSeries("contribution:<uuid>:crits")` returns the per-turn series. |

### 7.3 Manual Foundry verification checklist

1. Fresh kingdom, 3 PCs assigned to leader roles (Ruler/Emissary/Treasurer).
2. As the Emissary's owner, roll a diplomacy check toward Pitax → crit-succeed. Reopen sheet →
   Roster → Emissary's Renown card shows populace +3 and a Pitax faction chip +2.
3. Confirm `RawGroup.standing` for Pitax also moved (kingdom standing) but by its own larger step —
   the two numbers differ (double-count check).
4. Perform several activities + resolve an event across a couple of turns to push the Emissary's
   populace renown past 30.
5. Click **End Turn** → recap chat card shows the **Spotlight of the Turn** line; a
   `km-offer-renown-epithet` whisper appears for the crossed epithet.
6. Click **Grant Epithet** → epithet chip appears on the card; public flavour line posts.
7. Trigger a discount-tier epithet → **Grant Discount Tier** → open a settlement's purchase view as
   that PC → price is reduced.
8. Trigger an invitation epithet → **Create Invitation** → `AddQuest` opens prefilled → save → quest
   appears in Quests.
9. As a non-GM player, confirm you see your own PC's full card and others' epithet chips, but no GM
   adjust control; the Spotlight line is visible to all.
10. Open Analytics → per-player crit series renders for a PC with recorded turns.
11. Reload the world → renown, epithets, discount tier, and `contributions` persist.
12. Load a pre-Migration49 save → no crash; renown starts empty and accrues from the next check.

---

## 8. Phasing (Independently Committable)

Each phase compiles and tests green on its own; each is one kanban card.

| Phase | Title | Deliverable | Key files |
|-------|-------|-------------|-----------|
| **1** | **Data model + pure engine + migration** | `RawTurnContribution`, `RawRenown`, `RawTurnRecord.contributions`, `KingdomData.renown`/`currentTurnContributions`, `Migration49` (+contiguity bump), pure `RenownEngine.kt` (accrue/epithets/perk/spotlight) with `RenownEngineTest`. | `data/RawTurnContribution.kt`, `data/RawRenown.kt`, `data/RawTurnRecord.kt`, `KingdomData.kt`, `migrations/migrations/Migration49.kt`, `migrations/Migrations.kt`, `data/kingdom/RenownEngine.kt`, `commonTest/RenownEngineTest.kt` |
| **2** | **Attribution seam** | `recordContribution` + jsMain adapter; instrument `rollCheck` (`leaderActorUuid` param) covering checks/crits/activities/events; instrument petition answers; jsTest for tally + renown accrual. | `RecordContribution.kt`, `RenownAdapter.kt`, `dialogs/KingdomRoll.kt`, `dialogs/KingdomCheckDialog.kt`, petition handler, `jsTest/RecordContributionTest.kt` |
| **3** | **Recap + Spotlight + offers** | End-Turn flush + reset; `LastTurnRecap.spotlight` + recap card; epithet/perk `km-offer-*` handlers (3); optional Analytics per-player series; i18n `kingdom.renown.*`. | `dialogs/TurnWizardApplication.kt`, `TurnHistory.kt`, `ChatButtons.kt`, `TurnAnalytics.kt`, recap chat template, `lang/en.json` |
| **4** | **Roster UI + GM dialog + discount consumption** | Renown card on Roster, `RenownContext`, `ModifyPcRenown` GM dialog, discount-tier consumption in `InspectSettlement`, full manual QA. | `sheet/contexts/RenownContext.kt`, `sections/roster/renown-card.hbs`, `dialogs/ModifyPcRenown.kt`, `dialogs/InspectSettlement.kt`, `dialogs/RosterPanel.kt` |

**Total: 4 phases.** Phase 1 is standalone. Phase 2 depends on 1. Phase 3 depends on 1–2. Phase 4
depends on 1 (and reads renown produced by 2–3, but its UI degrades gracefully to empty cards).

---

## 9. Open Questions for Gregory

1. **Discount tier magnitudes** — −5% / −10% (this plan), or express as an item-purchase-level nudge
   instead of a percentage? The percentage keeps it purely cosmetic-economic and off the level ladder.
2. **Optional monthly renown fade** — ship the default-off "slow decay toward zero" toggle now, or
   leave renown fully sticky? (Plan: sticky; toggle is a noted future switch.)
3. **Spotlight tone** — the `BLUNDERER` "worst of the turn" line is playful; keep it, or make it
   opt-in so tables that dislike naming-and-shaming can disable it?
4. **Epithet catalog ownership** — ship the §Appendix catalog as data-driven JSON (GM-editable) or as
   a fixed Kotlin table? JSON matches the faction-agenda move-catalog precedent.
5. **Battle credit later** — worth a follow-up card to thread `leaders.general.uuid` into
   `ResolveBattle` so martial epithets (e.g. "Restov's Hammer") can accrue from real battles rather
   than only from martial *checks*?

---

## Appendix: Epithet Catalog (12 authored)

Ids are stable; labels/hints are i18n keys under `kingdom.renown.epithet.*`. "Perk" uses the closed
set from §5.

| # | Epithet id | Label | Condition | Perk |
|---|-----------|-------|-----------|------|
| 1 | `bridgeBuilder` | The Bridge-Builder | populace ≥ 30 **and** ≥ 5 diplomacy/trade activities credited | `Invitation(null)` |
| 2 | `restovsHammer` | Restov's Hammer | per-faction renown with a martial faction (Restov/Swordlords) ≥ 25 | — |
| 3 | `theUntiring` | The Untiring | ≥ 20 activities credited (any) | `DiscountTier(1)` |
| 4 | `coinCounter` | Coin-Counter | ≥ 15 Treasurer-role successes | `DiscountTier(2)` |
| 5 | `theIronhand` | The Ironhand | populace ≥ 40 **and** ≥ 3 crit-fails on record (feared, not loved) | — |
| 6 | `feyFriend` | Fey-Friend | per-faction renown with a fey group (Narlmarches) ≥ 25 | `Invitation("Narlmarches")` |
| 7 | `theUnshaken` | The Unshaken | ≥ 5 kingdom events resolved | — |
| 8 | `wardenOfTheMarches` | Warden of the Marches | ≥ 10 Warden-role successes | — |
| 9 | `theSilverTongue` | The Silver Tongue | ≥ 8 crit-successes on Emissary/diplomacy checks | `Invitation(null)` |
| 10 | `peoplesChampion` | People's Champion | populace ≥ 50 | `DiscountTier(2)` |
| 11 | `theKingmaker` | The Kingmaker | populace ≥ 75 **and** holds the Ruler role | — |
| 12 | `scourgeOfPitax` | Scourge of Pitax | per-faction renown with Pitax ≤ −25 (nemesis / negative-standing) | — |

---

**End of Plan.** Ready for review. On approval, implementation cards follow the §8 phasing table.
