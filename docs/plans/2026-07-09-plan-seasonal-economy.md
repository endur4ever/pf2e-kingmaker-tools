# Seasonal Economy Layer — Implementation Plan

> **Status:** Plan only — no implementation yet.
> **Date:** 2026-07-09
> **Roadmap item:** New backlog (Seasonal Economy — kingdoms plan around the year).
> **Gate:** **Homebrew rules profile, DEFAULT OFF.** Inert unless the active profile enables it.
> **Depends on:** Calendar-Module Integration (`getSeasonForMonth` / `Season`, landed), Homebrew
> Rules Profile system (`HomebrewRules` / `RuleResolutionHelper`, landed), Caravan economy
> (`CaravanTick.kt`, landed), Faction & Diplomacy Relations Tracker (`RawGroup.standing`, phases 1–3).
> **Branch:** `kingmaker.5`

---

## Executive Summary

Season is already derived deterministically from the Seasons & Stars month
(`getSeasonForMonth(monthZeroIndexed): Season` in `data/regions/Weather.kt`), but today it only
*flavors weather* — it never touches the kingdom's ledger. Real medieval realms live and die by the
year: harvests in autumn, hunger and raids in winter, floods in spring, campaigns in summer. This
feature adds a thin, **profile-gated** seasonal economy layer that makes the calendar month bend four
existing pure-math seams — worksite income, consumption, caravan raid DC, and river-crossing cost —
by **modest, deterministic** amounts, plus one GM-confirmed spring-flood offer.

The whole feature is one pure function — `seasonalModifiers(season, profile): SeasonalEconomyModifiers`
— threaded into four call sites that already exist. Because season is a pure function of the world
date, the preview and the commit see identical modifiers (parity holds for free). Standing seasonal
effects are just math (no chat spam); the one *event*-shaped consequence, a spring flood, is a
GM-confirmed offer through the existing `km-offer-*` chat-button system, never an auto-applied hit.

When the profile flag is off (the default), `seasonalModifiers` returns `NONE`, every multiplier is
`1.0`, every delta is `0`, and the kingdom behaves exactly as RAW does today.

---

## 1. Problem Statement + Player/GM Value

**Problem:** The kingdom economy is season-blind. A turn resolved in the dead of winter yields and
consumes exactly like a turn resolved at harvest. The calendar the group already advances (for
weather and camping) has zero strategic weight at the kingdom scale, so there is no in-fiction reason
to time a war for summer, stockpile food before winter, or fear the spring thaw. `docs/house-rules.md`
("Kingdom Management") explicitly wants *outside pressures* and *tension* that make Leadership choices
matter — a seasonal cycle is exactly that pressure, delivered by the calendar for free.

**Value to the table:**

- **Strategic planning:** Players learn the year. Stockpile Food before winter's consumption bump;
  push caravans in autumn while raid DCs are low; launch the war in summer; brace bridges for spring.
- **The calendar earns its keep:** The date the group already tracks now moves the kingdom, not just
  the weather macro. One coherent world clock instead of two disconnected ones.
- **GM prep, not GM bookkeeping:** The modifiers are automatic and previewed on the Turn tab. The
  only thing that ever asks the GM to decide is the spring-flood *offer* — one click, or dismiss.
- **Opt-in, RAW-safe:** Ships DEFAULT OFF inside the homebrew profile system. Groups who don't want
  it never see it; the RAW numbers are untouched.

---

## 2. Data Model

Season is **derived**, not stored, so the persistent footprint is deliberately tiny: one gate flag
on the homebrew rules, and one nullable idempotency marker so the spring-flood *offer* fires at most
once per spring per world-year. Everything else is recomputed each turn from the calendar date.

### 2.1 Profile-schema addition (the gate) — `HomebrewRules.kt` (commonMain, `@Serializable`)

`HomebrewRules` is a kotlinx `@Serializable` data class of RAW-default fields (not a `@JsPlainObject`);
adding a defaulted field is back-compat safe — older persisted profiles deserialize with the default.

```kotlin
@Serializable
data class HomebrewRules(
    // ... existing fields (useVanceAndKerenshara, ruinThreshold, travelCostRiverNoBridgeAdditional, …)
    val seasonalEconomy: Boolean = false,   // NEW — DEFAULT OFF; the entire feature gate
) {
    companion object {
        fun none() = HomebrewRules()                 // seasonalEconomy = false
        fun gregory() = HomebrewRules(
            // ... existing gregory() preset ...
            seasonalEconomy = true,                  // Gregory's house preset opts in
        )
    }
}
```

Resolution mirrors every other rule in `RuleResolutionHelper.kt`:

```kotlin
fun isSeasonalEconomyEnabled(profile: HomebrewRulesProfile?): Boolean =
    profile?.rules?.seasonalEconomy ?: HomebrewRules.none().seasonalEconomy   // false
```

The active profile is reached exactly as the existing rules are, via
`RuleResolutionHelper.getActiveProfile(registry)` where `registry` is the persisted
`HomebrewProfileRegistry` (`activeProfileId` + `profiles`).

### 2.2 Idempotency marker — `KingdomData.kt` (jsMain, `RawKingdomData` external interface)

The spring-flood offer is an *event*, so it must not re-fire on every End Turn inside the same spring,
nor on re-preview. One nullable marker records the last world-year we offered a flood in:

```kotlin
// RawKingdomData — ADDITION only (nullable for migration safety)
var lastSeasonalFloodYear: Int?   // world calendar year the spring-flood offer last fired; null = never
```

Nullable per the module's Raw* convention (external `@JsPlainObject`, auto `.copy`, nullable for
migration safety). No other persistent field is needed — the yield/consumption/raid modifiers are
pure functions of the date and are never stored.

### 2.3 Pure result type — `SeasonalEconomy.kt` (commonMain, plain `data class`)

Not persisted, so a normal Kotlin `data class` (not a Raw* interface):

```kotlin
data class SeasonalEconomyModifiers(
    val season: Season,
    val farmlandFoodMultiplier: Double,      // autumn harvest ↑ (consumption seam: food term)
    val commodityWorksiteMultiplier: Double, // winter short days ↓ (income seam: lumber/ore/stone)
    val foodConsumptionDelta: Int,           // winter heating/stores ↑ (consumption seam)
    val caravanRaidDcDelta: Int,             // winter bolder raiders ↑ (caravan seam)
    val riverCrossingCostDelta: Int,         // winter freeze ↓ (travel/caravan river surcharge)
    val springFloodOffer: Boolean,           // spring only → GM-confirmed OFFER, never standing math
) {
    companion object {
        /** The identity element: profile off, or an unmodelled season. RAW behavior. */
        fun none(season: Season) = SeasonalEconomyModifiers(
            season = season,
            farmlandFoodMultiplier = 1.0,
            commodityWorksiteMultiplier = 1.0,
            foodConsumptionDelta = 0,
            caravanRaidDcDelta = 0,
            riverCrossingCostDelta = 0,
            springFloodOffer = false,
        )
    }
}
```

### 2.4 Migration — **Migration49** (Gregory sequences the real number at implementation)

The chain currently ends at `Migration48` (`migrations/Migrations.kt`); `MigrationChainTest` asserts
contiguity, so the next contiguous number is proposed as **Migration49**.

```kotlin
// Migration49: initialize the seasonal-economy idempotency marker; bump schema version.
class Migration49 : Migration(version = 49) {
    override fun migrateKingdom(game: Game, kingdomActor: KingdomActor, kingdom: RawKingdomData) {
        if (kingdom.lastSeasonalFloodYear == undefined) {
            kingdom.lastSeasonalFloodYear = null   // never offered yet
        }
    }
}
```

- **Non-breaking.** The added `HomebrewRules.seasonalEconomy` field defaults to `false` under kotlinx
  deserialization; the `RawKingdomData` marker is nullable. Existing saves load unchanged and behave
  exactly as RAW until a profile turns the gate on.

---

## 3. Engine Design

### 3.1 Pure core (commonMain) — `SeasonalEconomy.kt`

Season source is the existing, already-verified `getSeasonForMonth(monthZeroIndexed): Season`
(`data/regions/Weather.kt:95`). The core is a single pure switch; it is **not** modified from RAW
unless the gate is on:

```kotlin
package at.posselt.pfrpg2e.kingdom.resources   // alongside Income.kt / Consumption.kt

import at.posselt.pfrpg2e.data.regions.Season
import at.posselt.pfrpg2e.homebrew.HomebrewRulesProfile
import at.posselt.pfrpg2e.homebrew.RuleResolutionHelper

/** The single seam: turn a calendar season + active profile into the turn's economy modifiers.
 *  Gate OFF (or profile null) => NONE (RAW). Deterministic: same (season, profile) => same result. */
fun seasonalModifiers(
    season: Season,
    profile: HomebrewRulesProfile?,
): SeasonalEconomyModifiers {
    if (!RuleResolutionHelper.isSeasonalEconomyEnabled(profile)) {
        return SeasonalEconomyModifiers.none(season)
    }
    return when (season) {
        Season.SPRING -> SeasonalEconomyModifiers.none(season).copy(springFloodOffer = true)
        Season.SUMMER -> SeasonalEconomyModifiers.none(season)                       // war-season flavor only
        Season.FALL   -> SeasonalEconomyModifiers.none(season).copy(farmlandFoodMultiplier = 1.25)
        Season.WINTER -> SeasonalEconomyModifiers.none(season).copy(
            commodityWorksiteMultiplier = 0.9,
            foodConsumptionDelta = 1,
            caravanRaidDcDelta = 2,
            riverCrossingCostDelta = -1,
        )
    }
}
```

Two tiny helpers (also pure, unit-tested for rounding parity):

```kotlin
/** Scale an integer resource total by a seasonal multiplier, rounding deterministically. */
fun applyWorksiteMultiplier(base: Int, multiplier: Double): Int =
    (base * multiplier).roundToInt()   // integer inputs => deterministic; safe for preview/commit parity
```

### 3.2 The concrete seasonal modifier table (real numbers, tuned MODEST)

| Season | Farmland food × | Commodity worksite × (lumber/ore/stone) | Consumption Δ | Caravan raid DC Δ | River-crossing Δ | One-time offer |
|--------|-----------------|------------------------------------------|----------------|--------------------|-------------------|-----------------|
| **Spring** | 1.0 | 1.0 | 0 | 0 | 0 | **Spring Flood** (`km-offer-seasonal-flood`) |
| **Summer** | 1.0 | 1.0 | 0 | 0 | 0 | none (war-season badge only) |
| **Autumn** (`FALL`) | **1.25** | 1.0 | 0 | 0 | 0 | none |
| **Winter** | 1.0 | **0.9** | **+1** | **+2** | **−1** (freeze) | none |

**Rationale, per number (all deliberately small — a nudge, never a cliff):**

- **Autumn farmland food ×1.25 → the consumption seam.** Harvest. On a mid-game realm with ~6–10
  farmland *food* (`realmData.worksites.farmlands.resources`), that is +2 to +3 food, which offsets a
  couple of Consumption — a felt reward for timing, not a game-warping windfall. Applied via
  `applyWorksiteMultiplier` (rounds to Int) so the ledger stays integer and preview == commit.
- **Winter commodity worksite ×0.9 → the income seam.** Frozen ground and short days mean ~10% fewer
  raw materials from lumber camps / mines / quarries. On ~10 lumber that is −1. Luxuries and RP are
  left untouched (traders keep working); only the extraction worksites slow.
- **Winter Consumption +1 → the consumption seam.** Heating, winter stores, harder foraging: one
  extra flat consumer. Small, but it means a realm that didn't stockpile in autumn feels the pinch.
- **Winter caravan raid DC +2 → the caravan seam.** Base raid flat-check DC is 11
  (`CARAVAN_BASE_RAID_DC`); winter raises it to 13 — roughly 10% more raids on the d20 flat check,
  because bandits are bolder when the kingdom's guard is thinned and caravans crawl through snow.
- **Winter river-crossing −1 → travel/caravan interaction.** Rivers freeze; you cross on the ice.
  This reduces the river-no-bridge travel surcharge (`travelCostRiverNoBridgeAdditional`, a homebrew
  setting, `gregory()` = 1) by 1, floored at 0. Only bites when that surcharge is already on, so it
  is a *thematic reward* for winter travel rather than a new penalty.
- **Summer = neutral, on purpose.** "Campaign / war season" is delivered as *flavor* (badge + gazette
  line), not a numeric buff, so it can never double-count with the Army & War-Pressure system. The
  economy is unchanged in summer.
- **Spring flood = OFFER, not standing math** (see §5). Floods are one-time disasters that damage
  bridges/lowland worksites; that is a decision, not a silent tax, so it goes through the GM-confirmed
  offer system, never the deterministic multiplier path.

### 3.3 Tick surface — where the modifiers plug in (respect the two-tick split, no third tick)

Season sets the **monthly baseline**, applied once per kingdom turn at the existing
`TurnTickingEngine` (End Turn) economy seams. There is **no new tick** and **no daily seasonal work**.

- The season is resolved once per turn from the world date via the existing runtime accessor
  `Game.getCurrentMonth()` (`utils/Time.kt:27`) → `getSeasonForMonth(game.getCurrentMonth().ordinal)`
  → `seasonalModifiers(season, activeProfile)`.
- The resulting `SeasonalEconomyModifiers` is threaded into the three pure economy seams (§6) that
  already run during the monthly turn: `calculateIncome`, `calculateConsumption`, and the caravan
  `tickCaravans` raid-DC computation.

### 3.4 The compose-with-weather invariant (season = baseline, weather = daily perturbation)

**Invariant, stated explicitly:** the seasonal layer and the daily-weather layer act on
**disjoint quantities** and must never both charge for the same thing.

- **Seasonal layer (monthly, `TurnTickingEngine`):** perturbs the *kingdom ledger* — worksite yields,
  Consumption, caravan raid DC, river surcharge. Applied **once per kingdom turn**.
- **Daily-weather layer (`DailyTickHooks.rollDailyWeather` → `rollWeather`):** perturbs *the day* —
  camping flat checks, weather events, scene FX, travel visibility. It touches the camping/exploration
  layer and **never writes to the kingdom economy**.

So winter's consumption bump and raid-DC bump come **only** from the seasonal (monthly) layer; a
blizzard rolled on a given day adds camping danger but adds **zero** kingdom Consumption or raid DC.
The two layers compose (a cold season *and* a snowy day can both be true) without double-counting,
because they write to different ledgers. Any future temptation to have daily weather also debit the
kingdom economy is explicitly out of scope (§6.1) precisely to preserve this invariant.

### 3.5 Preview/commit parity (deterministic from the calendar date)

**Invariant:** `seasonalModifiers` is a pure function of `(season, profile)`, and `season` is a pure
function of the world calendar date. The Turn wizard's preview and its commit run on the same world
date and the same active profile, therefore they compute **identical** modifiers — the preview shows
exactly the numbers the commit applies. No `Date.now()`, no RNG, no mutable state on the modifier
path. The *only* non-deterministic element in the feature is the spring-flood offer's dice/GM
decision, and that is deliberately kept **off** the numeric preview path: the offer is a separate
GM-confirmed chat card guarded by the `lastSeasonalFloodYear` idempotency marker, so re-previewing or
re-ticking the same spring never changes the previewed ledger and never spams a second flood card.

---

## 4. UI — Turn tab season badge + active-modifiers line (nothing bigger)

A single badge and one modifiers line on the **Turn tab**. No new section, no nav entry, no dialog.
`KingdomSheet.kt` already imports `getCurrentMonth` (line 271), so the season source is in hand.

### 4.1 Context object — `contexts/SeasonalEconomyContext.kt` (jsMain, `@JsPlainObject`)

```kotlin
@JsPlainObject
external interface SeasonalEconomyBadgeContext {
    val enabled: Boolean          // false when the profile gate is off => template renders nothing
    val seasonLabel: String       // localized via existing "season.$value" key (spring/summer/fall/winter)
    val seasonIcon: String        // fa icon class per season (e.g. leaf/snowflake/seedling/sun)
    val modifierLines: Array<String>  // localized active-modifier strings; empty in neutral seasons
}
```

Built in the Turn-tab context assembly (where `TurnWizardApplication` / the Turn-tab context is
composed) from `seasonalModifiers(season, activeProfile)`; `enabled = false` short-circuits rendering.

### 4.2 Template — `sections/turn/seasonal-badge.hbs` (partial, single root element)

Registered by name in `Main.kt loadTemplatePartials` (partials are referenced by name, not path) and
included from the Turn-tab template. Renders a compact pill:

```
[🍂 Autumn]  Harvest — farmland food ×1.25
[❄ Winter]  Consumption +1 · Caravan raid DC +2 · Rivers frozen (river crossing −1)
```

When `enabled` is false, or the season is neutral (summer), the partial renders just the season pill
(or nothing) — never an empty modifiers line.

### 4.3 i18n namespace — nested under `pf2e-kingmaker-tools` in `lang/en.json`

Season names reuse the **existing** `season.*` keys (`Season.i18nKey = "season.$value"`). New keys
live under `kingdom.seasonalEconomy.*` (nested objects, never flat-dotted — i18next nested lookup):

```json
"kingdom": {
  "seasonalEconomy": {
    "badge": { "harvest": "Harvest", "warSeason": "Campaign season" },
    "modifier": {
      "farmlandFood": "Farmland food ×{{multiplier}}",
      "commodityWorksite": "Worksite output ×{{multiplier}}",
      "consumption": "Consumption {{delta}}",
      "caravanRaidDc": "Caravan raid DC {{delta}}",
      "riversFrozen": "Rivers frozen (river crossing {{delta}})"
    },
    "flood": {
      "offerTitle": "Spring Flood",
      "offerBody": "The spring thaw threatens bridges and lowland worksites.",
      "spawnEvent": "Trigger flood event",
      "dismiss": "Hold back the waters"
    }
  }
}
```

Catalogs are wired into `initLocalization()` as usual; run `python3 scripts/check_i18n_keys.py`
before build to guard nesting.

---

## 5. Chat / Offer Surfaces (GM-Confirmed Only)

Only **one** thing in this feature is an offer: the spring flood. Every standing modifier is silent
deterministic math (no chat), so there is no per-turn chat spam.

### 5.1 Spring-flood offer — `km-offer-seasonal-flood` (`ChatButtons.kt`)

Fired during the spring End Turn when `seasonalModifiers(...).springFloodOffer == true` **and** the
`lastSeasonalFloodYear` marker is not already this world-year. The offer card is whispered to GMs
(mirroring `km-offer-war-threat-arrival`); nothing is applied until the GM clicks.

| Button (`data-action`) | Effect | Idempotency |
|------------------------|--------|-------------|
| **Trigger flood event** | Spawn the `spring-flood` kingdom event via `kingdom.getEvent("spring-flood")` + `RawOngoingKingdomEvent(stage = 0, id = "spring-flood")` (exactly the `km-offer-war-threat-arrival` `spawnEvent` pattern), guarded through `getEvent()` so an unresolved id can't create an invisible ongoing entry. | Sets `kingdom.lastSeasonalFloodYear = currentWorldYear`; re-clicks no-op. |
| **Hold back the waters** (dismiss) | No mechanical effect. | Also stamps `lastSeasonalFloodYear` so the card doesn't re-offer this spring. |

Handler shape (follows the established `ChatButton("km-offer-…")` + `game.user.isGM` guard idiom):

```kotlin
ChatButton("km-offer-seasonal-flood") { game, actor, event, button ->
    if (!game.user.isGM) return@ChatButton
    val action = button.dataset["action"] ?: return@ChatButton
    val year = button.dataset["worldYear"]?.toIntOrNull()
    actor.getKingdom()?.let { kingdom ->
        if (kingdom.lastSeasonalFloodYear == year) return@ChatButton   // already handled this spring
        when (action) {
            "spawnEvent" -> {
                val ev = kingdom.getEvent("spring-flood")
                    ?: run { ui.notifications.error(t("kingdom.seasonalEconomy.flood.eventMissing")); return@ChatButton }
                kingdom.ongoingEvents = kingdom.ongoingEvents + RawOngoingKingdomEvent(stage = 0, id = "spring-flood")
            }
            "dismiss" -> { /* flavor only */ }
        }
        kingdom.lastSeasonalFloodYear = year
        actor.setKingdom(kingdom)
    }
}
```

- **`spring-flood` kingdom event** lives in `data/events/` (a data asset, **out of this doc's write
  scope** — listed here as a Phase-4 deliverable). If the group has no such event authored, the
  offer degrades gracefully: the button reports the missing event and stamps the marker.
- The card is posted from the spring End-Turn flow (where the other `km-offer-*` end-turn cards are
  emitted), carrying `data-action`, `data-world-year`, and `data-kingdom-actor-uuid`.

---

## 6. Interactions with Existing Systems (the four concrete seams)

All four seams were read and **CONFIRMED** to exist at the signatures below.

| # | Seam (file:line) | Verified signature today | Change |
|---|------------------|--------------------------|--------|
| 1 | **`calculateIncome`** — `src/commonMain/.../kingdom/resources/Income.kt:28` | `calculateIncome(realmData, resourceDice, increaseGainedLuxuries): Income` | Add `seasonal: SeasonalEconomyModifiers = SeasonalEconomyModifiers.none(...)`; wrap the lumber/ore/stone worksite `.income` with `applyWorksiteMultiplier(_, seasonal.commodityWorksiteMultiplier)`. Callers `collectResources` + `calculateProjectedResources` (`sheet/CalculateIncome.kt`) pass the turn's modifiers. |
| 2 | **`calculateConsumption`** — `src/commonMain/.../kingdom/resources/Consumption.kt:23` | `calculateConsumption(settlements, realmData, armyConsumption, now, expressionContext, modifiers): Consumption` | Add `seasonal` param; scale the `food` term (`= applyWorksiteMultiplier(farmlands.resources, seasonal.farmlandFoodMultiplier)`) and add `seasonal.foodConsumptionDelta` into `consumers`. |
| 3 | **`caravanRaidDc`** — `src/jsMain/.../kingdom/CaravanTick.kt:82` (pure fn; unit-tested via `CaravanTickTest.kt`) | `caravanRaidDc(baseDc, partnerStanding, atWar, claimedFraction): Int` | Add `seasonalDcDelta: Int = 0`; add it before the final `coerceIn(5, 40)`. `performEndTurn`'s caravan tick passes `seasonal.caravanRaidDcDelta`. |
| 4 | **Season source** — `getSeasonForMonth` `src/commonMain/.../data/regions/Weather.kt:95` + `Game.getCurrentMonth()` `utils/Time.kt:27` | `getSeasonForMonth(monthZeroIndexed: Int): Season` | **Reused, not modified** — imported by `SeasonalEconomy.kt` as the season source. |

Plus the river-crossing interaction: `riverCrossingCostDelta` composes with the existing
`travelCostRiverNoBridgeAdditional` game setting
(`settings/Pfrpg2eKingdomCampingWeatherSettings.kt:426`), read where the river-no-bridge travel
surcharge is applied (caravan routing / hexploration travel cost). Winter subtracts 1, floored at 0:
`max(0, getTravelCostRiverNoBridgeAdditional() + seasonal.riverCrossingCostDelta)`.

Profile plumbing: `HomebrewRules.kt` (new field) + `RuleResolutionHelper.kt`
(`isSeasonalEconomyEnabled`) + `HomebrewProfileRegistry` (unchanged; reached via `getActiveProfile`).

### 6.1 Explicit OUT-OF-SCOPE

- **No third tick.** No daily seasonal ledger work; the monthly `TurnTickingEngine` is the only
  seasonal surface. `DailyTickHooks` is untouched.
- **No daily-weather → kingdom-economy coupling.** Weather stays camping/exploration-only; it must
  never debit Consumption or raid DC (preserves the §3.4 compose invariant).
- **No new worksite types, no seasonal structure bonuses**, no changes to storage caps.
- **No seasonal army/war-pressure mechanics.** Summer is flavor only; the War-Pressure board is
  unchanged.
- **No automatic bridge/worksite destruction.** Spring flood is a GM-confirmed offer, never an
  auto-applied hit.
- **No behavior change when the profile gate is off** — RAW numbers are byte-for-byte preserved.
- **Authoring the `spring-flood` event content** (balance, stages) is a data task; this plan only
  wires the offer to spawn it.

---

## 7. Test Plan

### 7.1 commonTest — pure logic (`SeasonalEconomyTest.kt`)

| Test | Assertion |
|------|-----------|
| `modifierTable_spring` | `springFloodOffer == true`, all multipliers 1.0, all deltas 0. |
| `modifierTable_summer` | `none(SUMMER)` exactly — neutral, no offer. |
| `modifierTable_autumn` | `farmlandFoodMultiplier == 1.25`, everything else neutral. |
| `modifierTable_winter` | `commodityWorksiteMultiplier == 0.9`, `foodConsumptionDelta == 1`, `caravanRaidDcDelta == 2`, `riverCrossingCostDelta == -1`. |
| `gateOff_returnsNone_everySeason` | With `profile == null` or `seasonalEconomy=false`, every season returns `none(season)`. |
| `determinism_sameInputsSameResult` | `seasonalModifiers(s, p) == seasonalModifiers(s, p)` for all seasons (value equality). |
| `applyWorksiteMultiplier_roundingIsDeterministic` | `(base, 1.25)` and `(base, 0.9)` round per spec (e.g. 8→10, 10→9); integer in → integer out. |
| `incomeApplied_winterReducesCommodities` | `calculateIncome` with winter modifiers yields floor-ish reduced lumber/ore/stone; luxuries/RP unchanged. |
| `consumptionApplied_autumnRaisesFood` | `calculateConsumption` with autumn modifiers increases the `food` term (lower `total`). |
| `consumptionApplied_winterAddsConsumer` | winter `foodConsumptionDelta` raises `consumers` by exactly 1. |
| `caravanRaidDc_winterAddsTwo` | `caravanRaidDc(11, 0, false, 0.0, seasonalDcDelta = 2) == 13`; still clamped to `[5, 40]`. |
| `riverFreeze_floorsAtZero` | `max(0, surcharge + (-1))` never negative. |

### 7.2 jsTest (Foundry-integrated)

| Test | Assertion |
|------|-----------|
| `badgeContext_disabledWhenGateOff` | `SeasonalEconomyBadgeContext.enabled == false`, no modifier lines, when profile off. |
| `badgeContext_winterLinesLocalized` | Winter builds the four expected localized modifier lines. |
| `previewCommitParity_sameSeasonSameNumbers` | Turn-wizard preview and commit on the same world date produce identical income/consumption/raid-DC deltas. |
| `floodOffer_firesOncePerSpring` | Two consecutive spring End Turns emit the flood card once; second is suppressed by `lastSeasonalFloodYear`. |
| `floodOffer_stampsMarkerOnDismiss` | Dismiss also stamps the marker (no re-offer this spring). |
| `gateOff_noBadgeNoOfferRawNumbers` | Profile off → no badge, no flood card, and income/consumption equal the RAW baseline. |
| `migration49_initsMarkerNull` | Migration49 sets `lastSeasonalFloodYear = null` on a pre-existing kingdom and bumps schema version. |

### 7.3 Manual Foundry verification checklist

1. Activate Seasons & Stars; confirm the Turn tab shows the correct season pill for the current date
   (no modifiers line when the profile is off).
2. Enable a homebrew profile with `seasonalEconomy = true`. Set the date to **autumn** → Turn tab
   shows "Harvest — farmland food ×1.25"; preview Consumption drops vs. RAW.
3. Set the date to **winter** → badge lists Consumption +1, Caravan raid DC +2, Rivers frozen;
   preview commodity income drops ~10%; End Turn caravan raids use DC 13.
4. Preview an End Turn, then commit — every seasonal number in the commit equals the preview.
5. Set the date to **spring**, End Turn → a GM-whispered **Spring Flood** offer appears. Click
   *Trigger flood event* → the `spring-flood` kingdom event spawns (or a clean "event missing"
   notice). End Turn again in the same spring → **no** second flood card.
6. Set the date to **summer** → neutral badge (war-season flavor), no economic change vs. RAW.
7. Turn the profile gate **off** → badge and modifiers vanish; numbers return to RAW; no flood offer.
8. Reload the world → `lastSeasonalFloodYear` persists; no duplicate flood offer for the same spring.
9. Confirm daily weather (blizzard) rolled between turns adds camping danger but adds **zero** kingdom
   Consumption or raid DC (compose invariant holds).

---

## 8. Phasing (independently-committable)

| Phase | Title | Deliverable | Key files |
|-------|-------|-------------|-----------|
| **1** | **Pure core + profile gate** | `SeasonalEconomy.kt` (`SeasonalEconomyModifiers`, `seasonalModifiers`, `applyWorksiteMultiplier`), `HomebrewRules.seasonalEconomy` field + `gregory()` opt-in, `RuleResolutionHelper.isSeasonalEconomyEnabled`, full commonTest. **Inert** — nothing wired yet. | `kingdom/resources/SeasonalEconomy.kt`, `homebrew/HomebrewRules.kt`, `homebrew/RuleResolutionHelper.kt`, `SeasonalEconomyTest.kt` |
| **2** | **Economy seam wiring + migration** | Thread `seasonal` into `calculateIncome`, `calculateConsumption`, `caravanRaidDc` and their callers (`sheet/CalculateIncome.kt`, `performEndTurn` caravan tick); resolve season once per turn via `getCurrentMonth`; river-freeze composes with `travelCostRiverNoBridgeAdditional`; `RawKingdomData.lastSeasonalFloodYear` + **Migration49**. | `Income.kt`, `Consumption.kt`, `CaravanTick.kt`, `sheet/CalculateIncome.kt`, `TurnTickingEngine.kt`, `KingdomData.kt`, `migrations/migrations/Migration49.kt` |
| **3** | **Turn-tab badge UI** | `SeasonalEconomyBadgeContext`, badge partial + Turn-tab include, partial registration, `kingdom.seasonalEconomy.*` i18n (reusing `season.*`). | `sheet/contexts/SeasonalEconomyContext.kt`, `sections/turn/seasonal-badge.hbs`, `Main.kt` (partial registration), `KingdomSheet.kt`, `lang/en.json` |
| **4** | **Spring-flood offer + QA** | `km-offer-seasonal-flood` handler (spawn-event + dismiss, idempotent via marker), flood offer chat template, spring End-Turn emission, `data/events/spring-flood` event asset, jsTest + manual checklist. | `ChatButtons.kt`, `chatmessages/seasonal-flood-offer.hbs`, `TurnTickingEngine.kt`/`TurnWizardApplication.kt` (emission), `data/events/…`, `SeasonalEconomyTest.kt` (jsTest) |

**Total: 4 phases.** Phase 1 is standalone (pure + gate, no behavior change). Phase 2 depends on 1.
Phase 3 depends on 1. Phase 4 depends on 2 (marker) and reuses Phase-1 core.

---

## 9. Open Questions for Gregory

1. **Autumn scope:** boost only farmland *food* (this plan) or also give a small luxuries bump
   (autumn wine/fur harvest)? Kept to food to stay modest.
2. **Winter commodity penalty:** ×0.9 on lumber/ore/stone as planned, or leave income untouched and
   express winter purely through Consumption + raid DC? ×0.9 gives the income seam a real hook.
3. **Spring flood severity:** spawn a `spring-flood` kingdom *event* (this plan, consistent with
   war-threat-arrival) vs. a direct one-time hit (e.g. −1 lowland worksite this turn)? Event route
   keeps balance in data, not code.
4. **Gate granularity:** one master `seasonalEconomy` flag (this plan) vs. per-effect toggles
   (harvest / winter / flood independently)? One flag is simplest; per-effect can come later as
   additional `HomebrewRules` fields without a migration.
5. **Summer:** keep economically neutral (flavor only), or add a small war-pressure/army-cost flavor
   hook? Left neutral to avoid double-counting with the War-Pressure system.

---

**End of Plan.** Ready for review. Upon approval, implementation cards will be created per the
phasing table above.
