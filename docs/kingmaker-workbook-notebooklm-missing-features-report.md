# Kingmaker Workbook + NotebookLM Missing Features Report

**Date:** 2026-06-03
**Branch:** `kingmaker.5`
**Scope:** Gap analysis for migrating more of `Copy of Royal Kingdom Sheet for Pathfinder 2E - CURRENT.xlsx` into `/home/grego/code/pf2e-kingmaker-tools`, cross-checked against the `pathfinder 2e kingmaker` NotebookLM material and the current repository.
**Verdict:** PARTIAL MIGRATION. Core data tables and several recent data chunks are present, but workbook settings, turn-tracker automation, V&K rules toggles, warfare automation, and several formula-backed workbook behaviors still need dedicated migration/implementation plans.

---

## Sources Checked

| Source | Result |
|---|---|
| Workbook `Copy of Royal Kingdom Sheet for Pathfinder 2E - CURRENT.xlsx` | 16 sheets inspected, including hidden structure/template sheets |
| Extracted workbook gap report `/tmp/pf2e_kingmaker_gap_report.json` | 10 text/rules rows still lack code/text coverage; 16 of 17 house-rule settings lack settings coverage |
| Extracted workbook tables `/tmp/pf2e_next_workbook_tables.json` | Static tables mostly represented in Kotlin now; workbook settings and formula-backed sheets still need follow-up |
| NotebookLM notebook `pathfinder 2e kingmaker` | Used to identify additional Kingmaker mechanics not yet fully modeled in the repo |
| Repository code/data/docs scan | Checked existing Kotlin data classes, JSON data, templates, tests, and docs to avoid duplicate work |

---

## Executive Summary

The workbook migration is not finished. The repo has already absorbed many static tables and data records, but the migration is currently uneven: simple lookup tables are mostly present, while spreadsheet logic, settings, phase gating, turn tracker rules, and live automation are still partial or absent.

| # | Area | Current Status | Key Finding |
|---|---|---|---|
| 1 | Static workbook tables | MOSTLY COVERED | `Advancement.kt`, `KingdomSize.kt`, `SettlementType.kt`, `MilestoneXp.kt`, `RpToXpConversion.kt`, and `WaterAdjacency.kt` exist |
| 2 | Workbook text/rules rows | GAP | 10 workbook rules rows have no text/code coverage: Running a Kingdom, Activities Listed By Step, Bonus dice, Consumption modifier, Building on Rough Terrain, Warfare Phase, Flat Check DC, Kingdom XP Awards, Awarded XP, Ending The Turn |
| 3 | V&K / house-rule settings | MAJOR GAP | 16 of 17 extracted settings are not represented as real configurable settings; only `XP per claimed hex` was marked covered |
| 4 | Kingdom turn assistant | PARTIAL | `TurnTickingEngine.kt` exists, but strict phase/step gating, resource dice rolling, random-event checks, XP awards, and end-turn workflow are not fully automated |
| 5 | Workbook formula behavior | PARTIAL | The workbook contains formula-heavy `Kingdom Sheet`, `Turn Tracker`, `Urban Grid Template`, and hidden sheets; many formulas require explicit Kotlin/UI equivalents |
| 6 | Settlements and urban grids | PARTIAL | Settlement types, overcrowding unrest, water border fields, and structure evaluation exist; rough terrain building cost, full urban-grid template behavior, and some water-adjacent structure logic remain gaps |
| 7 | Armies and warfare | DATA COVERED / AUTOMATION GAP | Army catalog, tactics, and modifiers are represented as data; a warfare phase/combat resolver, army XP/leveling workflow, morale/rout automation, and battlefield state remain missing |
| 8 | Random events and XP | DATA/PARTIAL | Event data exists, but the workbook/NotebookLM flow for flat check DC, event XP, continuous events, and end-turn awarded XP is not fully surfaced as an automated turn workflow |
| 9 | Leadership model | MOSTLY COVERED WITH VARIANT WARNING | Repo implements the PF2e 8-role model with vacancy penalties; NotebookLM references an 11-role model, likely a variant/source mismatch that needs reconciliation before migration |
| 10 | Campaign automation features | ROADMAP ONLY | Hex content, pressure clocks, quest/event generator, session prep dashboard, and travel planner are documented in roadmap/plans but not complete production features |

---

## Already Migrated or Covered

These should not be duplicated unless a future implementation needs to wire them into automation.

### Static Kingdom Tables

| Workbook / Rules Area | Current Repo Representation | Status |
|---|---|---|
| Kingdom advancement levels and features | `src/commonMain/kotlin/at/posselt/pfrpg2e/data/kingdom/Advancement.kt` | COVERED |
| Kingdom size bands, resource die, control DC modifier, commodity capacity | `KingdomSize.kt` | COVERED |
| Settlement type data | `SettlementType.kt` | COVERED |
| Milestone XP awards | `MilestoneXp.kt` plus milestone JSON data | COVERED |
| RP-to-XP conversion, random-event XP, claimed-hex XP | `RpToXpConversion.kt` | COVERED AS DATA |
| Structure water adjacency lookup | `WaterAdjacency.kt` | COVERED AS DATA |
| Charter/government/heartland data models | `Charter.kt`, `Government.kt`, `Heartland.kt` | COVERED AS DATA/UI MANAGEMENT |
| Anarchy penalty | `AnarchyPenalty.kt`, used by modifier creation | COVERED |
| Vacancy penalties | `Leader.kt`, `VacancyPenalties.kt` | COVERED FOR PF2e 8-ROLE MODEL |
| Overcrowding unrest | `GainedUnrest.kt`, `Unrest.kt` count overcrowded settlements | COVERED |
| Quell Unrest / Lumber Camp / Mine settlement matrix entries | `SettlementDetailsMatrix.kt` | COVERED |

### Recent Data Migration Items

| Item | Current Status |
|---|---|
| Basic army catalog | Present in Kotlin data |
| Army tactics | Present in Kotlin data |
| Specialized army modifiers | Present in Kotlin data |
| Companion structures | JSON files present under `data/structures/` |
| Naval Support | JSON file present under `data/kingdom-activities/` |
| Cleanse Item | JSON file present under `data/kingdom-activities/` |
| V&K structure JSON records | Many present in `data/structures/` |
| Camping activities and recipes | Present under `data/camping-activities/` and `data/recipes/` |

---

## Confirmed Missing Workbook Rows / Rules Text

The extracted gap report still lists these workbook rows as lacking text/code coverage. These should become either docs entries, data records, UI help, or explicit automation, depending on the row.

| Workbook Row | Title | Recommended Treatment |
|---|---|---|
| 6 | Running a Kingdom | Add turn assistant reference/help text and state-machine entry point |
| 10 | Activities Listed By Step | Add machine-readable phase/step grouping for leadership, region, civic, army, event, and upkeep steps |
| 46 | Bonus dice | Model where bonus dice are granted, spent, capped, and reset |
| 59 | Consumption modifier | Split formula-backed consumption modifiers into explicit model/evaluation rules |
| 292 | Building on Rough Terrain | Add structure-building cost/DC modifier logic and UI warning |
| 431 | Warfare Phase | Add warfare phase workflow or explicit placeholder in turn assistant |
| 506 | Flat Check DC | Add random-event flat-check DC setting/logic |
| 515 | Kingdom XP Awards | Add turn-end XP award calculation and log output |
| 526 | Awarded XP | Track/display XP awarded during the turn, not just final XP totals |
| 534 | Ending The Turn | Make end-turn checklist/summary explicit in the turn assistant |

Priority: rows 6, 10, 506, 515, 526, and 534 should be handled together with the Kingdom Turn Assistant. Rows 46 and 59 belong with resource/consumption automation. Row 292 belongs with settlement/structure construction. Row 431 belongs with the army/warfare feature.

---

## Major Gap: V&K and Workbook Settings

The workbook has 17 extracted settings. Only `XP per claimed hex` was marked covered by the gap extractor. The remaining settings are not represented as real configurable app settings/profile rules.

| Setting | Current Gap |
|---|---|
| Untrained Skill Bonus | No configurable formula/profile setting for untrained kingdom skill math |
| RPes To XP Conversion Rate | `RpToXpConversion.kt` exists as data, but the workbook's user-editable setting is not exposed as a setting |
| RPes To XP Conversion Maximum | No configurable maximum setting surfaced |
| XP Multiplier | No profile-level XP multiplier setting surfaced |
| XP To Level | No configurable XP-to-level threshold setting surfaced |
| Minimal Capital Influence | No setting/profile rule for minimum capital influence |
| V&K charter extra skills | Not wired into kingdom creation/settings |
| V&K heartland extra skills | Not wired into kingdom creation/settings |
| V&K extra ability boost | Not wired into kingdom creation/level-up |
| V&K skill increase every level | Not wired into advancement/level-up workflow |
| V&K alternative activities | Data exists for some activities, but rule toggle does not swap/enable behavior as a profile |
| V&K extra activities | Some extra activity data exists, but settings/profile behavior is incomplete |
| V&K Practical Magic nerf | JSON/data may exist, but the setting toggle is not modeled |
| V&K Feat status bonuses fixed | No rule toggle controlling bonus stacking behavior |
| V&K additional structure item bonuses | Structure data exists, but profile/setting behavior and full item-bonus audit are incomplete |
| V&K alternative Milestone XP rewards | Milestone data exists, but alternative reward profile is not exposed |

Recommended implementation direction: do not scatter these into ad-hoc booleans. Create or extend a rules-profile/settings model that can answer questions like `useVanceKerensharaActivities`, `rpToXpRate`, `xpToLevel`, `skillIncreaseMode`, `featBonusStackingMode`, and `structureItemBonusMode`. This aligns with `docs/feature-roadmap.md` item 9, Homebrew rules profile system.

---

## NotebookLM-Derived Kingmaker Mechanics Still Needing Coverage

The NotebookLM Kingmaker material highlighted several mechanics that are either absent, only partially modeled, or present only as low-level data without turn workflow.

### 1. Kingdom Creation Workflow

Current repo state:
- Charter/government/heartland data classes and JS management dialogs exist.
- Initial kingdom creation is not a dedicated guided workflow.
- V&K creation toggles are not wired.

Missing work:
- Creation wizard that applies charter, government, heartland, initial proficiencies, favored land, settlement construction, trained skills, bonus feats, and optional V&K extras.
- Validation that permanent ability boosts/flaws and skill proficiencies are applied exactly once.
- Explicit handling for V&K charter/heartland extra skills and extra ability boost settings.

Recommended next plan: `docs/plans/kingdom-creation-wizard-and-vk-settings.md`.

### 2. Strict Kingdom Turn State Machine

Current repo state:
- `TurnTickingEngine.kt` exists and handles several end-turn state transitions.
- `docs/plans/2026-06-03-kingdom-turn-assistant.md` already plans a turn assistant.

Missing work:
- Phase/step state machine for Upkeep, Commerce, Leadership, Region, Civic, Army/Warfare, Event, and End Turn.
- Activity availability by phase/step.
- Activity cap enforcement by RAW/profile/V&K settings.
- Resource dice roll flow: resource dice count, die size from kingdom size, bonus dice, RP result, preview and apply.
- Consumption payment flow: Food first / RP fallback / Unrest on shortfall, including modifiers.
- Anarchy handling as a turn-state restriction, not only a modifier penalty.
- End-turn diff summary that includes XP awards, event checks, consumption, resources, commodities, fame/infamy, unrest, and modifiers.

Recommended next plan: continue from `docs/plans/2026-06-03-kingdom-turn-assistant.md` and add the workbook rows listed above.

### 3. Random Events and Event XP

Current repo state:
- Event JSON/data exists.
- RP-to-XP/random-event XP static table exists.
- There is no fully guided event step in the turn flow.

Missing work:
- Flat check DC setting/logic from workbook row 506.
- Event level calculation.
- Random event XP award flow.
- Continuous event persistence and recurring modifiers until resolved.
- Event-to-quest hooks, already suggested by `docs/feature-roadmap.md`.
- Turn tracker display for awarded XP, event result, and unresolved event state.

Recommended next plan: fold the basic flat-check/event-XP flow into the turn assistant, then create a separate plan for the event-to-quest generator.

### 4. Fame / Infamy

Current repo state:
- Fame state is present in `RawFame` and `TurnTickingEngineTest.kt` verifies `next -> now` and preserves `type`.

Missing work:
- Confirm and automate the NotebookLM/upkeep rule that fame or infamy increases by +1 per turn during Upkeep.
- Expose the Fame vs Infamy choice/profile cleanly in the UI.
- Include fame/infamy change in end-turn preview and chat/journal summary.

Recommended next plan: include this as a small subtask in Kingdom Turn Assistant rather than a separate feature.

### 5. Settlements, Construction, and Urban Grid Template

Current repo state:
- Settlement type data exists.
- Settlement matrix and structure evaluation exist.
- Overcrowding unrest appears represented by `Unrest.kt` counting `isOvercrowded` settlements.
- Water borders exist in structure evaluation context.

Missing work:
- Building on rough terrain rule from workbook row 292.
- Full Urban Grid Template formula parity: block identifiers, lot IDs, water borders, walls, edifice handling, resident capacity, and structure lookup behavior.
- Water-adjacent structure logic audit. `WaterAdjacency.kt` exists, but prior docs note that water-adjacent Mills lowering consumption still needs wiring.
- GM-facing construction warnings when terrain/water/lot constraints are not met.

Recommended next plan: `docs/plans/urban-grid-template-and-construction-rules.md`.

### 6. Structures and Settlement Benefits

Current repo state:
- Many structure JSON files are present, including V&K variants and companion structures.
- `docs/structure-rules.md` documents structure rule fields.
- Settlement benefit/access tracker exists only as roadmap item 6.

Missing work:
- Audit all workbook and V&K structure item bonuses against JSON and evaluation logic.
- Implement the V&K additional structure item bonuses setting/profile behavior.
- Surface PC-facing benefits: trainers, crafting access, purchasable item levels, special artisans, and capital/non-capital differences.
- Verify bridge/water border behavior and consumption reduction behavior with tests.

Recommended next plan: `docs/plans/settlement-benefit-and-vk-structure-bonuses.md`.

### 7. Armies and Warfare

Current repo state:
- Basic armies, tactics, and specialized modifiers are represented as data.
- Army-related package/templates exist.
- Roadmap already calls for an army board/combat resolver.

Missing work:
- Workbook row 431 Warfare Phase as a real workflow step.
- Army XP and leveling workflow.
- Morale checks, rout threshold, routed/retreat/recovery state.
- Mired, pinned, weary, damaged, destroyed, scouting, maneuver, melee/ranged strike automation.
- Battlefield terrain state and tactic conditional application.
- Enemy army pressure clocks and ETA, matching roadmap item 12.

Recommended next plan: either extend `docs/army-war-pressure-data-models.md` into implementation tasks or create `docs/plans/army-warfare-phase-resolver.md`.

### 8. Leadership Model Reconciliation

Current repo state:
- Code implements the PF2e-style 8-role model: Ruler, Counselor, Emissary, General, Magister, Treasurer, Viceroy, Warden.
- Vacancy penalties and leader bonuses are implemented around those 8 roles.

NotebookLM warning:
- NotebookLM surfaced an 11-role list: Ruler, Counselor, General, Grand Diplomat, High Priest, Magister, Marshal, Spymaster, Treasurer, Viceroy, Warden.

Recommendation:
- Do not blindly migrate the 11-role list into code. First identify whether that material is from PF1e Kingmaker, a variant, or V&K/homebrew notes.
- If Gregory wants the 11-role variant, implement it as a rules-profile option with a migration plan, not by replacing the current PF2e role model.

### 9. History, Turn Tracker, and Workbook State

Current repo state:
- Kingdom sheet and turn ticking state exist.
- Workbook has formula-heavy `History` and `Turn Tracker` sheets with state not fully reflected in the app.

Missing work:
- Turn history log with per-turn resource dice, RP, consumption, events, XP awards, fame/infamy, unrest, and notes.
- Current-turn scratch fields such as awarded XP, event result, treasury tapped, and action use.
- Human-readable turn recap output for chat/journal.
- Import path for existing workbook historical rows if needed.

Recommended next plan: pair this with the Kingdom Turn Assistant because turn history is the durable output of that workflow.

---

## Workbook Sheets Requiring More Migration / Reconciliation

| Workbook Sheet | Status | Remaining Migration Work |
|---|---|---|
| README | Mostly documentation/changelog; prior docs say no new structured game data, but it contains logic notes that became code gaps | Keep as audit source for behavior gaps such as water-adjacent Mills and feat logic |
| Kingdom Sheet | Partially represented by current sheet model and UI | Formula parity audit, display-derived fields, consumption split, treasury tapped, party-level reference, trade agreement count |
| Turn Tracker | Major remaining gap | Phase/step workflow, activity tracking, random events, awarded XP, end-turn summary |
| History | Major remaining gap | Durable turn history / log import / recap display |
| Settlements | Partially represented | Rough terrain, construction constraints, water/walls/urban-grid formula parity |
| Urban Grids | Partially represented | Template/grid calculations and visual sync for more than claimed state |
| Armies | Data mostly represented | Warfare phase and army board/combat resolver |
| Creation | Partially represented | Guided kingdom creation wizard and V&K creation settings |
| Advancement | Covered as static data | Wire profile variants for V&K advancement/skill increases if desired |
| Tables | Mostly covered as static data | Expose user-configurable workbook settings/profile knobs |
| Army Template | Data covered but behavior incomplete | Combat resolver, tactic conditional logic, army leveling/recovery |
| Urban Grid Template | Partially covered | Formula parity and construction validation |
| Hidden structure/settings sheets | Partially covered | V&K item bonuses/settings audit and rule-profile behavior |

---

## Recommended Implementation Order

Do not implement all of this in one task. The workbook mixes data migration, settings, UI workflow, and campaign automation. Split into small reviewed plans.

### Phase 1 — Settings/Profile Foundation

Create a plan for rule profiles and workbook/V&K settings.

Acceptance targets:
- Represent all 17 extracted settings, including defaults and descriptions.
- Keep RAW and Gregory/V&K behavior separate.
- Add tests proving profile values change calculations only where intended.
- Do not hard-code V&K behavior into base RAW calculations.

Suggested plan file:
- `docs/plans/workbook-vk-settings-profile.md`

### Phase 2 — Kingdom Turn Assistant Completion

Extend the existing turn assistant plan with the missing workbook rows.

Acceptance targets:
- Explicit turn step state machine.
- Activity availability and caps by step/profile.
- Resource dice, bonus dice, consumption, random event flat check, awarded XP, end-turn summary.
- Chat/journal output and persistent history entry.

Existing plan to update/extend:
- `docs/plans/2026-06-03-kingdom-turn-assistant.md`

### Phase 3 — Turn Tracker and History Migration

Implement durable turn logs and optional workbook-history import.

Acceptance targets:
- Turn record model.
- Import/export or manual entry for historical workbook turn rows.
- Sheet UI section for history.
- Tests for turn record creation and display context.

Suggested plan file:
- `docs/plans/turn-tracker-history-migration.md`

### Phase 4 — Settlement / Urban Grid Formula Parity

Handle rough terrain, water adjacency, walls, lot/block formulas, and construction warnings.

Acceptance targets:
- Rough-terrain construction modifier logic.
- Water-adjacent Mills consumption rule verified or intentionally rejected.
- Urban grid template formula parity tests using extracted workbook examples.
- Foundry UI warnings for invalid construction placement.

Suggested plan file:
- `docs/plans/urban-grid-template-and-construction-rules.md`

### Phase 5 — Warfare Phase / Army Resolver

Turn army data into a usable warfare workflow.

Acceptance targets:
- Warfare phase in turn state machine.
- Army XP/leveling, morale/rout, conditions, recovery, tactics, battlefield terrain.
- Enemy army pressure clocks and ETA integration if desired.
- Tests for core battle actions and state transitions.

Suggested plan file:
- `docs/plans/army-warfare-phase-resolver.md`

### Phase 6 — Kingdom Creation Wizard

Make creation data usable by a guided workflow.

Acceptance targets:
- Apply charter/government/heartland choices once.
- Initial skills, boosts/flaws, favored land, settlement construction.
- V&K creation toggles.
- Migration-safe default for existing kingdoms.

Suggested plan file:
- `docs/plans/kingdom-creation-wizard-and-vk-settings.md`

---

## Human Foundry Checks Needed Later

These cannot be fully validated by static code inspection once implemented:

- [ ] Turn assistant renders each phase/step in a live kingdom sheet.
- [ ] Activity buttons are enabled/disabled correctly by phase and profile.
- [ ] End-turn preview matches applied changes.
- [ ] Random event flat check creates the expected event/XP/history output.
- [ ] Rough terrain and water-adjacent construction warnings display in settlement UI.
- [ ] Urban grid visual state matches workbook examples.
- [ ] Army warfare phase appears only when relevant and can resolve morale/rout/recovery.
- [ ] V&K settings can be switched without corrupting existing RAW kingdoms.

---

## Bottom Line

The migration is not just missing more JSON. The remaining workbook material is mostly behavior:

1. configurable workbook/V&K settings,
2. turn step/state-machine logic,
3. turn tracker/history output,
4. settlement/urban-grid formula parity,
5. random event and XP automation,
6. warfare/army workflow,
7. guided kingdom creation.

The highest-value next chunk is the settings/profile foundation plus the Kingdom Turn Assistant extension, because those two areas unlock most of the remaining workbook rows and NotebookLM-derived Kingmaker mechanics without duplicating already migrated static tables.
