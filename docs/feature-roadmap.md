# Kingmaker Campaign Automation Feature Roadmap

Created: 2026-05-31
Status updated: 2026-07-14
Repo: `/home/grego/code/pf2e-kingmaker-tools`

## Goal

Capture suggested future features for automating more of a Kingmaker campaign, including homebrew support, before any implementation starts.

## Status summary (2026-07-14)

**All 13 features in the original backlog and all 8 new backlog features are now fully implemented** and verified against the codebase and the Hermes kanban board (`~/.hermes/kanban/boards/pf2e-kingmaker-tools`).
Each feature section is annotated with its implementing files. There are no blocked or remaining items left in the backlogs.

Legend: ✅ Implemented · 🟡 Partial · 🚧 In progress · 📝 Plan written · ⛔ Blocked · ⬜ Not started

## Planning rule

No feature in this document should be implemented directly from the roadmap.

Before implementation:
1. Pick exactly one feature.
2. Create a dedicated plan in `docs/plans/`.
3. Include affected files, data models, migrations, UI changes, tests, and manual Foundry verification.
4. Review the plan with Gregory.
5. Only then create implementation tasks or Kanban cards.

## Current foundation observed

The repo already has useful building blocks:

- Camping system: `src/jsMain/kotlin/at/posselt/pfrpg2e/camping/`
- Camping data: `data/camping-activities/`, `data/recipes/`
- Kingdom sheet: `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/sheet/`
- Kingdom quests: `RawQuest.kt`, `sections/quests/page.hbs`
- Turn ticking engine: `TurnTickingEngine.kt`
- Roster/native actor work: `RawCharacter.kt`, `RosterPanel.kt`
- Hex grid sync: `kingdom/map/HexGridSync.kt`
- Events/data packs: `data/events/`, `packs/kingmaker-tools-*`
- House rules doc: `docs/house-rules.md`

## Recommended feature backlog

### 1. Campaign timeline and pressure-clock dashboard

**Status: ✅ Implemented** — `kingdom/sheet/contexts/CampaignClockContext.kt`,
`kingdom/dialogs/CampaignClockDialog.kt`, `kingdom/dialogs/ModifyCampaignClock.kt`,
clock ticking wired through `TurnTickingEngine.kt` + migration.

Purpose: track chapter deadlines, escalating threats, and time pressure so travel/kingdom turns matter.

Examples:
- Stag Lord deadline.
- Troll sightings until Hargulka is handled.
- Season of Bloom daily cult-event pressure.
- Varnhold Vanishing rescue timer.
- Blood for Blood and War of the River Kings army pressure.

Why it fits:
- Builds directly on `TurnTickingEngine.kt`.
- Supports the house-rule goal of making kingdom management feel less like a spreadsheet.
- Gives the GM a visible reason to advance turns and enforce consequences.

Plan requirements:
- New data model for campaign clocks/deadlines.
- Turn tick integration.
- Kingdom sheet dashboard panel.
- Chat/journal output when clocks advance or trigger.
- Tests for clock ticking, expiration, pause/resume, and completed threats.

### 2. Quest/event generator tied to kingdom events

**Status: ✅ Implemented** — `questevent/QuestGeneratorSettings.kt`,
`questevent/GenerateQuestDialog.kt`; quest data models + migration shipped.

Purpose: turn kingdom events into actionable quests, complications, and rewards.

Examples:
- Crop Failure becomes a druid conflict quest.
- Political scandal from an enemy faction appears after Infiltrate.
- Merchant/trainer/refuge rumors become hex hooks.
- Event completion grants XP, RP, commodities, unrest reduction, or structure access.

Why it fits:
- Existing quest model already supports type, target, rewards, and completed flavor text.
- `data/events/` and `event-browser.hbs` already exist.
- Helps automate GM prep while preserving human control.

Plan requirements:
- Extend `RawQuest` with source event, urgency, due date, visibility, and consequence fields.
- Add event-to-quest action in event browser.
- Add quest completion consequence application.
- Add GM-only/generated quest distinction.
- Tests for reward application and status transitions.

### 3. Hex content and discovery manager

**Status: ✅ Implemented** — `kingdom/dialogs/HexContentManager.kt`;
hex content enums/helpers, data models + Migration 27, and `explored`/`cleared`
hex states synced through `kingdom/map/HexGridSync.kt`.

Purpose: track what each hex contains, what players know, what is hidden, and what changes after claiming or clearing it.

Examples:
- Landmarks, refuges, worksites, resources, ruins, merchants, trainers, enemy armies.
- Hidden vs discovered vs cleared states.
- Claimed hexes suppress random combat encounters.
- Roads/bridges/settlements change travel cost.
- GM-only notes and player-facing discovered text.

Why it fits:
- Kingmaker is exploration-heavy.
- House rules already call for more rewarding hex content.
- Existing hex grid sync can become the visual layer for discovery state.

Plan requirements:
- Hex content schema with GM/player visibility split.
- Scene drawing or tile metadata sync.
- Quest/event hooks from hex discoveries.
- Random encounter filtering by claimed/cleared state.
- Tests for visibility, discovery transitions, and travel modifiers.

### 4. Travel and route planner

**Status: ✅ Implemented** — `camping/TravelRouteService.kt`, `camping/TravelModels.kt`.

Purpose: calculate travel time, route costs, and arrival estimates from current party state and map conditions.

Examples:
- Horses speed up travel.
- Rivers without bridges increase travel cost.
- Roads, bridges, settlements, terrain, weather, and forced march affect ETA.
- Party speed uses slowest relevant traveler.
- Travel plan can reserve camp stops and watches.

Why it fits:
- Camping data already tracks travel/hexploration seconds, travel mode, forced march, minimum speed, and hex size.
- Roster/actor integration can provide party speed and availability.

Plan requirements:
- Route-cost function independent of Foundry UI.
- UI for selected start/end hexes and route preview.
- Integration with camp/travel timers.
- Tests for roads, rivers, terrain, mounts, forced march, and weather modifiers.

### 5. Kingdom turn assistant

**Status: ✅ Implemented** — `kingdom/dialogs/TurnWizardApplication.kt`,
`kingdom/sheet/contexts/TurnWizardContext.kt`, `kingdom/ActivityCapCalculator.kt`;
preview/commit tick parity verified.

Purpose: guide the GM and players through a kingdom turn with fewer missed steps.

Examples:
- Pre-turn checklist.
- Current RP/commodities/storage/consumption preview.
- Leadership/civic/region activity caps from RAW or homebrew profile.
- Suggested pressure events when unrest/ruin is too low.
- End-turn diff summary from `TurnTickingEngine`.

Why it fits:
- The turn ticking engine already produces change records.
- House rules emphasize adding pressure and limiting action count to reduce analysis paralysis.

Plan requirements:
- Turn wizard state model.
- Activity cap rules by mode/profile.
- End-turn preview before applying changes.
- Chat/journal summary after turn completion.
- Tests for turn diff, limits, and homebrew toggles.

### 6. Settlement benefit and access tracker

**Status: ✅ Implemented** — `kingdom/dialogs/InspectSettlement.kt`
exposes `trainers`, `craftingAccess`, and `availableItemLevels` with a full
structure → trainable-class mapping.

Purpose: make structures matter to PCs beyond kingdom bonuses.

Examples:
- Trainers unlocked by buildings.
- Crafting access unlocked by structures.
- Item purchase levels by settlement type and structures.
- Special artisans offering limited magic items.
- Settlement upgrades using the homebrew Improve Settlement activity.

Why it fits:
- `docs/house-rules.md` already lists trainer/crafting structure mappings.
- Existing settlement matrix tracks item levels and settlement stats.

Plan requirements:
- Data schema for structure-granted PC benefits.
- Settlement view showing unlocked trainers/crafting/item access.
- Optional homebrew toggle for non-capital settlement upgrades.
- Tests for access calculation and display context.

### 7. Companion relationship and personal quest manager

**Status: ✅ Implemented** — `kingdom/sheet/contexts/CompanionProfileContext.kt`,
`CompanionQuestContext.kt`, `PartyInfluenceContext.kt`, `data/RawPartyMemberInfluence.kt`.

Purpose: manage companion influence, camp availability, learning activities, and personal quest hooks.

Examples:
- Companion influence/discovery status.
- One influence/discover attempt per camp session.
- Companion-specific camp activities greyed out when absent.
- Personal quest triggers and rewards.
- Missing custom quests for companions can be filled with homebrew entries.

Why it fits:
- Current TODO already includes companion-learning chart integration.
- Native actor roster work gives companions a durable home.

Plan requirements:
- Companion profile schema linked to actor UUID.
- Camp availability state.
- Activity gating integration.
- Quest trigger model.
- Tests for absent/present NPC activity gating and per-session limits.

### 8. Camping encounter resolver

**Status: ✅ Implemented** — `camping/EncounterResolverEngine.kt`,
`camping/EncounterPreviewDialog.kt`.

Purpose: automate the watch encounter flow without removing GM control.

Examples:
- One encounter per watch.
- Watcher rolls Perception vs ambusher Stealth DC.
- Result determines enemy start distance, sleeping/prone/unconscious state, reactions, and wake-up checks.
- Armor comfort trait handling.
- Chat card summary for the GM.

Why it fits:
- `ConfirmWatchApplication.kt`, `RandomEncounters.kt`, and camping watch settings already exist.
- House rules define a clear encounter resolution table.

Plan requirements:
- Encounter resolution model independent of UI.
- Watcher/ambusher input dialog.
- Optional token condition automation.
- Chat output with editable GM decisions.
- Tests for degree-of-success outcomes.

### 9. Homebrew rules profile system

**Status: ✅ Implemented** — `kingdom/dialogs/HomebrewProfileManager.kt`;
homebrew data classes, Foundry settings registration, sheet Homebrew tab, and i18n.

Purpose: let the GM switch between RAW, Vance & Kerenshara-style, and Gregory/custom rules without code edits.

Examples:
- Kingdom XP adjustments.
- Ruin threshold 5 instead of 10.
- Leadership activity count caps.
- No random combat in claimed hexes.
- Settlement upgrade rules.
- Camping activity count equals PC count.

Why it fits:
- The module already has a lot of settings and data-driven content.
- Homebrew rules are a core part of the campaign direction.

Plan requirements:
- Versioned JSON profile schema.
- Settings UI for active profile.
- Import/export profile action.
- Rule resolution helper used by camping/kingdom/hex systems.
- Tests for RAW vs homebrew profile behavior.

### 10. Session prep and recap dashboard

**Status: ✅ Implemented** — `kingdom/SessionPrepView.kt`,
`kingdom/sheet/contexts/SessionPrepContext.kt`,
`kingdom/SessionPrepNarrativeGenerator.kt`; narrative prose layer and
journal recap export both shipped.

Purpose: produce a GM-facing plan before play and a player-facing recap after play.

Examples:
- Open quests.
- Active clocks/deadlines.
- Unresolved kingdom events.
- Nearby hex hooks.
- Companion moments due.
- Suggested next session outline.
- End-session recap to journal.

Why it fits:
- The user wants to be present with a plan before new features and campaign sessions.
- Existing quests, notes, events, roster, and hex data can feed one dashboard.

Plan requirements:
- Read-only aggregation context first.
- GM-only dashboard UI.
- Journal export action.
- Optional player-safe recap filter.
- Tests for visibility filtering and generated summary data.

### 11. Random encounter and rumor curator

**Status: ✅ Implemented** — `camping/EncounterCuratorData.kt`,
`camping/EncounterPreviewDialog.kt`, `camping/dialogs/CategoryWeightSettings.kt`,
`camping/dialogs/RegionEncounterTables.kt`, `camping/dialogs/RegionConfig.kt`.

Purpose: improve random encounters so they support campaign pacing instead of just adding combat.

Examples:
- 50/50 combat vs RP encounter weighting.
- Region/level/hex-state filters.
- Rumors that reveal locations or foreshadow threats.
- Merchants with limited special stock.
- Disease, weather, faction, or lore encounters.

Why it fits:
- House rules recommend fewer random combat encounters and more RP/lore encounters.
- Existing rolltable support and random encounter code can be extended.

Plan requirements:
- Encounter category schema.
- GM preview before applying.
- Claimed/cleared hex filtering.
- Rumor-to-quest/hex hook support.
- Tests for weighting and filters.

### 12. Army and war pressure board

**Status: ✅ Implemented** — `kingdom/ArmyWarPressure.kt`, `kingdom/ArmyPressureView.kt`,
`kingdom/data/RawWarThreat.kt`, `kingdom/dialogs/AddWarThreat.kt`,
`kingdom/dialogs/DeployArmy.kt`, `kingdom/dialogs/ResolveBattle.kt`; full 4-phase
warfare resolver (battle models, pure battle engine, army XP/leveling/recovery,
Resolve Battle dialog + chat log) shipped.

Purpose: track army threats, invasions, and chapter war pressure in one place.

Examples:
- Enemy army status and ETA.
- Threat clocks that increase unrest or consume commodities.
- Army attacks until a chapter objective is resolved.
- Links between warfare, quests, and kingdom events.

Why it fits:
- Repo has `kingdom/armies/` and army browser templates.
- House rules call for more army pressure in multiple chapters.

Plan requirements:
- War threat data model.
- Integration with campaign clocks.
- Army browser/dashboard additions.
- Turn tick consequences.
- Tests for ETA/clocks/consequence application.

### 13. Balance and pacing alerts

**Status: ✅ Implemented** — `kingdom/PacingAlerts.kt`, `kingdom/PacingAlertView.kt`,
`kingdom/PacingAlertChat.kt`, `kingdom/sheet/contexts/PacingAlertContext.kt`;
stagnation tracking wired via `pacingAlertMinUnrestDelta`.

Purpose: warn the GM when the campaign is drifting away from the intended pressure curve.

Examples:
- Kingdom level too low/high for chapter.
- Too much or too little unrest/ruin.
- Too much loot/item access for party level.
- Too few claimed hexes/worksites for kingdom level.
- Too many turns without a pressure event.

Why it fits:
- House rules explicitly discuss XP curve, loot imbalance, unrest/ruin tension, and worksites.
- This is advisory only, so it helps without forcing automation.

Plan requirements:
- Pacing metrics model.
- Configurable thresholds by chapter/profile.
- Dashboard alert panel.
- Tests for threshold calculations.

## Completed beyond the original backlog

Work that shipped in addition to the 13 features above (from the kanban board and code):

- ✅ **Gear settings profile system** — versioned schema, import/export, settings UI;
  V&K activity/structure toggles wired into the gear-settings pipeline.
- ✅ **Workbook house-rule structures & activities continuation** — migrated workbook
  data (Advancement, Milestone XP, RP→XP, water-adjacency tables) into Kotlin data
  classes with tests; settlement urban-grid parity audit + fixes.
- ✅ **Living settlement population** — population data model + migration, NPC name
  generator (River-Kingdoms/Brevoy name tables), starter-roster generation from the
  population number, settlement-sheet population roster CRUD (`PopulationDialogs.kt`).
- ✅ **No-roll camping downtime fix** — no-roll activities (e.g. Enhance Weapons) now
  charge 2h downtime at commit; sheet shows hours spent and disables activities at 8h.
  (Resolves the long-standing `docs/todo.md` bug.)
- ✅ **Turn history** — durable per-turn `TurnRecord` (`kingdom/TurnHistory.kt`) +
  "Recent Turns" recap section.
- ✅ **End-turn XP awards** — RP→XP conversion + opt-in automatic Fame gain.
- ✅ **Bonus resource dice** — grant → roll → reset model.
- ✅ **Water-adjacency structure rules** — Mill consumption wired into evaluation.
- ✅ **Rough terrain construction costs** (opt-in) and **Anarchy activity gating** (opt-in).
- ✅ **Obsidian integration** (feasibility → implementation decision).
- ✅ **World Anvil integration** — `kingdom/sheet/WorldAnvilExporter.kt`.
- ✅ **Hermes companion profiles** — in-character soul profiles for the Kingmaker companions.
- ✅ **Camping sheet render performance** — addressed the ~20s context-prep cost.
- ✅ **Kingdom creation: V&K extras** — charter/heartland extra trained skills and an extra
  ability boost during creation, each gated by an independent setting (default off, so RAW
  behavior is preserved). Derivation extracted to a pure, unit-tested
  `kingdom/VkExtras.kt` (`vkInitialSkillSlots` / `vkExtraAbilityBoosts`, with `VkExtrasTest`
  now calling the real functions); `assemble jsTest` green. This was the last blocked board item.

## Blocked / in-progress

- None. The previously-blocked **Kingdom creation: V&K extras** item was finished on
  `kingmaker.5` (pure `VkExtras.kt` + tests, `assemble jsTest` green) — see
  [Completed beyond the original backlog](#completed-beyond-the-original-backlog).

## New backlog (not yet started)

Candidate features that are **not** covered by the work above. Per the planning rule,
write a `docs/plans/` doc before implementing any of these.

1. ✅ **Faction & diplomacy relations tracker.** Extended the static trade-partner
   `kingdom/data/RawGroup.kt` into a living diplomacy system: per-faction attitude/standing
   (Sootscale, Pitax, Brevoy houses, the fey, Varnhold), shifts driven by kingdom
   activities/events, treaty/trade-agreement state, and faction-driven quests/threats.
   **Plan:** [`docs/plans/2026-06-13-faction-diplomacy-relations-tracker.md`](plans/2026-06-13-faction-diplomacy-relations-tracker.md).
   **Progress:** Implemented phases 1–4 on `kingmaker.5` (pure standing logic + tests, attitude
   display, adjust-standing dialog, turn-tick drift, threshold GM-confirmed offers for war threats/diplomacy quests).
 2. ✅ **Calendar-module integration (Seasons & Stars).** Implemented on
    `kingmaker.5` — `com/foundryvtt/core/helpers/SimpleCalendar.kt` (bindings),
    `kingdom/CalendarLogger.kt` (logger), integrated in `Climate.kt`, `DailyTickHooks.kt`,
    `Resting.kt`, and `TurnWizardApplication.kt` w/ settings UI in `KingdomSettings.kt` and `KingdomData.kt`.
    Surfaces weather, kingdom turns, camping sessions, and companion arrivals on the in-world
    Seasons & Stars calendar; auto-derives season from month; prompts GMs in chat when the month advances.
3. ✅ **Campaign analytics / trends dashboard.** GM-only read-only **Analytics** tab
   (`MainNavEntry.ANALYTICS`) charts the recorded `RawTurnRecord` series — unrest, RP,
   consumption, fame, XP, war pressure, kingdom level/size, and all four ruin tracks — as
   inline-SVG line charts with min/max/mean/current/delta summaries and a 10/25/all window
   selector. The level chart overlays the pacing-alert tolerance band. Pure math lives in
   `TurnAnalytics.kt` (`extractSeries`/`summarizeSeries`/`mapSeriesToCoordinates`,
   unit-tested in `TurnAnalyticsTest.kt`); the sheet builds an `AnalyticsContext` rendered by
   `sections/analytics/{page,metric-chart}.hbs`. Zero new deps, no game-state mutation.
   **Plan:** [`docs/plans/2026-06-13-campaign-analytics-trends-dashboard.md`](plans/2026-06-13-campaign-analytics-trends-dashboard.md).
4. ✅ **Commodity market & trade-route/caravan economy.** Caravans carry Commodities to
   trade-partner Groups over map-routed travel time (reuses the camping route planner via
   `CaravanRouting`/`KingmakerHexGridProvider`), face a per-turn raid flat check (DC by
   standing/at-war/route safety) in `CaravanTick`, and deliver bonus Resource Dice on arrival
   (standing/alliance-scaled). Dispatch dialog + Turn-tab board + recall; partners get a map
   `hexKey`. Wired into End Turn (`performEndTurn`). **Plan:**
   [`docs/plans/2026-06-16-commodity-market-caravan-economy.md`](plans/2026-06-16-commodity-market-caravan-economy.md).
   Both selling (Commodities→Resource Dice) and buying (RP→Commodities, RAW Purchase Commodities
   pricing with standing/treaty discounts) are supported. Follow-ups: per-settlement stockpiles
   (the kingdom currently uses a single Commodity pool, so settlement transfers are
   logistics-risk only) and a real claimed-hex route-safety modifier for the raid DC.
5. ✅ **Player-facing collaborative kingdom view.** Permission-filtered read-only sheet access for players, character-owned active leader selection gating, and roll/assurance button checks. Shipped in `KingdomSheet.kt`, `KingdomCheckDialog.kt`, `check.hbs`, and `Leaders.kt`.
6. ✅ **Chronological Kingdom Event Log & Gazette.** `formatTurnGazette` in `kingdom/TurnHistory.kt` + journal export from Session Prep (`kingdom/SessionPrepView.kt` → `SessionPrepNarrativeGenerator.kt`).
7. ✅ **Hex-based Resource Worksite & Yield Calculator.** `calculateProjectedResources` in `kingdom/sheet/CalculateIncome.kt` + tests in `CalculateIncomeTest.kt`.
8. ✅ **Vassal State, Settlement Annexation, & Tribute Tracking.** Automate diplomacy-based or conquest-based integration of adjacent territories, calculating monthly tribute, unrest penalties, and structural changes on annexation. Shipped in `kingdom/TurnTickingEngine.kt` (vassal tribute RP accrual), `kingdom/sheet/KingdomSheet.kt` (`annex-group` action with hex claim, unrest increase, and standing log), `trade-agreements/page.hbs`, `kingdom/TurnHistory.kt`, and `kingdom/dialogs/TurnWizardApplication.kt`.
9. ✅ **Caravan Route Safety Overlays & Threat Indicators.** `syncCaravanRoutes` in `kingdom/map/HexGridSync.kt` + `CaravanRouteSafetyTest.kt`.

## Decisions resolved by implementation
## Decisions resolved by implementation

The original "Open decisions for Gregory" have effectively been answered by shipped code;
recorded here for history:

- Homebrew support shipped as a general **multi-profile** system (`HomebrewProfileManager.kt`),
  plus a separate **gear settings** profile system.
- Generated quests carry a GM-only/visibility distinction.
- Campaign clocks tick through the turn engine with chat/journal output.
- Hex content/discovery syncs to Foundry **scene drawings** (`explored`/`cleared` states).
- Session prep produces both **structured aggregation** and a **narrative prose** layer,
  with journal recap export.
- **Leadership roles** — PF2e 8-role model (Ruler, Counselor, Emissary, General, Magister, Treasurer, Viceroy, Warden) is intentional; the PF1e 11-role list (Spymaster, Grand Diplomat, High Priest, Marshal) is rejected for this module and would only return as a homebrew-profile variant. Cross-referenced in `docs/kingmaker-ap-gap-analysis-2026-06-13.md` (section C) and `docs/kingmaker-workbook-notebooklm-missing-features-report.md` (item 9).
