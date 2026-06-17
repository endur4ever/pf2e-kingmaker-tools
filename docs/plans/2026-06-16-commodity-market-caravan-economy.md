# Commodity Market & Caravan Economy

Created: 2026-06-16
Roadmap: feature #4 in `docs/feature-roadmap.md` ("New backlog").
Branch: `kingmaker.5`

## Goal

A kingdom-economy layer: caravans move Commodities/RP between the kingdom's settlements and
trade-partner factions over travel time, exposed to risk en route. Net-new homebrew (the AP has no
caravan system) that makes the existing trade-partner **Groups** and the faction-standing tracker
matter economically.

## Scope (confirmed with Gregory)

- **Map-routed** ETA via the hex route planner.
- Caravans handle **commodity↔RP trade with partners** AND **settlement↔settlement commodity
  transfers**.
- **Per-turn raid check** while in transit.
- Caravans advance on **kingdom turns** (End Turn).

## Key constraints (from exploration)

- Settlements are Foundry **Scenes** (`RawSettlement.sceneId`), not hexes; trade **Groups**
  (`RawGroup`) have no location. Both get a GM-assigned realm-map `hexKey`.
- No hex-adjacency helper exists; `camping/TravelRouteService.kt` + `TravelService.kt` are a
  working but skeletal Dijkstra — they need a real `HexGridProvider`.
- `TurnTickingEngine.tick` already ticks array state (`warThreats`, `armyDeployments`) and returns
  updated arrays — caravans follow this pattern exactly.
- Trade Commodities / Purchase Commodities exist as activities but have no coded RP rate
  (message-only). The caravan rate is defined here (RAW base confirmed via NotebookLM; faction
  standing/alliance as a homebrew multiplier).
- Latest migration = 37 → new = **Migration38**.

## Phases

**Pre-step (loose-end fixes, shipped first):** luxury work-site produces 1 Commodity (was 0) in
`Realm.kt`; leadership "(N per player)" label reads the configured per-leader setting (already
applied).

**Phase 1 — Locations + data model (Migration38).** `hexKey: String?` on `RawSettlement` &
`RawGroup` (+ schema + UI); new `RawCaravan` (`id, kind, originHexKey, destHexKey, originLabel,
destLabel, partnerName?, cargoCommodity?, cargoAmount, cargoRp?, etaTurns, turnsRemaining,
status`); `caravans: Array<RawCaravan>?` on `KingdomData`; Migration38 seeds `[]` / null hexKeys.

**Phase 2 — Route → ETA.** New `kingdom/map/KingmakerHexGridProvider.kt` (adjacency via Foundry
hex-grid API; content from `kingmaker.state.hexes` + `hexContents`). `TravelRouteService` route +
cost → `etaTurns = max(1, ceil(cost / costPerTurn))`. Reuse `TravelService` road/river/terrain cost.

**Phase 3 — Dispatch UI + caravan board.** `CaravanDispatchDialog` (origin/destination/cargo,
preview route/ETA/risk/value, deduct cargo on dispatch); caravan board section on the Turn tab with
recall (returns remaining cargo). Sheet context + `.hbs` + CSS + i18n.

**Phase 4 — Turn tick: risk + delivery.** Pure `kingdom/CaravanTick.kt` (`tickCaravans`) called
from `TurnTickingEngine.tick`; threaded through `runKingdomTurnTick`/`performEndTurn`. Per caravan
each End Turn: per-turn raid flat check (DC modified by partner standing/alliance, atWar, and
claimed-hex route safety) → delay / partial loss / encounter flag; decrement `turnsRemaining`; on
arrival deliver (sellToPartner→RP at standing-scaled rate; buyFromPartner→commodities;
settlementTransfer→commodities capped by storage). Emit `TickChange`s + caravan chat card. Tests in
`CaravanTickTest`.

## Reuse

`TravelRouteService`/`TravelService`; `RawGroup.standing/allianceLevel` + `attitudeFor`;
`TurnTickingEngine` array-tick pattern + `TickChange` + chat; `calculateStorage` /
`RawCurrentCommodities.endTurn(storage)`; migration + `KingdomSheetDataModel` schema patterns.

## Verification

Per phase: `scripts/check_i18n_keys.py`; `./gradlew jsTest -x kotlinStoreYarnLock` (Chrome
headless) incl. new tests; `./gradlew jsBrowserDistribution`. Manual in Foundry: assign hexKeys,
dispatch caravans (partner + settlement), End Turn → ETA decrement + raid chat + delivery; recall
mid-transit.
