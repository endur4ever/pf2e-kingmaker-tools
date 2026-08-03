# Roadmap #10 / #11 / #12 / #13 — Session Prep Dashboard, Encounter Curator, War Pressure Board & Pacing Alerts

Implements four roadmap items end-to-end on `kingmaker.5`, plus a localization guard and two latent save-wipe bug fixes. Each feature was built in verified green phases; `jsTest` went **1198 → 1265, 0 failures** throughout, and a full `assemble` stays green.

### #10 — Session Prep & Recap Dashboard (kingdom)
A new GM-friendly **Session Prep** kingdom tab: a read-only pre-session snapshot aggregating open quests, active campaign clocks, unresolved events, hex hooks, and companion moments.
- **Player-safe**: GM-only sections (clocks, events, hidden hex content) are withheld from non-GMs; companion moments collapse to player-visible ones.
- Pure tested view (`SessionPrepView`) + thin context, mirroring the pacing-alerts section pattern.
- Disabled "Generate Narrative" button left as the seam for a future prose layer; journal export deferred (both ticketed).

## Features

### #11 — Random Encounter & Rumor Curator (camping)
A GM-facing system that routes random encounters through weighted categories and a preview/accept flow, and turns rumors into kingdom quests.
- **Category routing**: 8 encounter categories (combat, rp, rumor, merchant, disease, faction, weather, lore) with configurable relative weights and a weighted picker; combat encounters can be suppressed in claimed + cleared hexes.
- **GM encounter preview dialog** + curated rolling path (rolls without auto-posting; the GM accepts to post to chat).
- **Encounter curator settings dialog** (proxy roll table + weights + hex filter).
- **Per-region per-category roll tables**: a per-region sub-dialog (launched from a row button in the region matrix) configures all 8 category tables plus the cleared-hex toggle, without bloating the matrix to 17 columns.
- **Rumor → quest conversion**: creates a `CampaignQuest`, badges it as encounter-generated, and merges it into the quests board.

### #12 — Army & War Pressure Board (kingdom)
A new **Army & Pressure** kingdom tab tracking enemy threats, army deployments, and an aggregate war-pressure meter that feeds unrest/consumption.
- **Data + logic**: `RawWarThreat` / `RawArmyDeployment` / `RawWarPressure` models; pure `recalculateWarPressure` (+5/active threat, −2/deployed army, clamped 0–100, threshold flags) and `tickWarThreat` (ETA countdown → escalation when it reaches 0).
- **Turn-tick integration**: threats tick and pressure recalculates each End Turn.
- **War-threat CRUD** (add/edit/delete) + an enable toggle.
- **Deploy/recall army CRUD**: a `DeployArmy` dialog picks any `PF2EArmy` actor (shown with its type) and optionally assigns it to an active threat; recall removes the deployment. Both recompute pressure, so deployed armies actually relieve it.

### #13 — Balance & Pacing Alerts (kingdom)
An advisory system that watches the campaign for drift off its intended pressure curve. **Purely advisory — never mutates game state.** Four alert types, each with fire-once semantics so chat isn't spammed:
| Type | Signal | Generated at |
|------|--------|--------------|
| Stagnation | unrest unchanged for N turns | End Turn |
| Turn gap | event drought (`turnsWithoutEvent`) | event check |
| Level mismatch | kingdom level vs party average level | End Turn |
| Loot imbalance | settlement item access far above party level | End Turn |
- **Pacing tab**: severity-styled alert cards, per-severity counts, GM dismiss + clear-all.
- **Chat messages** on fire (shared poster across generation sites).
- **Tunable thresholds** in the kingdom settings dialog (gap, level tolerance, loot toggle).

## Supporting changes
- **Settlement population roster persistence**: NPC add/edit/delete in the settlement population tab writes through to the kingdom actor immediately; the seeded starter roster is copied into editable data once at dialog open (no reseed after deletions).
- **Camping watch overhaul**: every watcher on the ambushed watch slot rolls Perception vs the ambusher's Stealth DC (best result alerts the party); all other camp characters are exposed in their sleep; the chat card lists each watcher's roll.
- **Curator polish**: encounter category is picked from the curator weight sliders (proxy table only as zero-weight fallback); lenient whole-token category matching for proxy rows; curated rumors always flagged as quest hooks.
- **Hex map fixes**: isolated road hexes draw a visible stub; hex overlays are click-through; hex drawings resync on canvasReady; functional Hex Map Enabled toggle.
- **Real kingdom turn counter** (`KingdomData.currentTurn`): incremented once per End Turn and threaded into war-threat `triggeredTurn`, every pacing alert, the turn-gap check, and army `deployedTurn` (these previously defaulted to `0`).
- **Localization guard** (`scripts/check_i18n_keys.py`): fails on flat-dotted keys or unresolved `localizeKM` / `t("…")` references — i18next resolves keys by nested path, so flat keys render raw. Also added `AGENTS.md` documenting the convention for non-Anthropic models.

## Bug fixes (latent data loss)
Both fixed as part of building the new settings UIs:
- **Kingdom settings save wiped non-schema fields** — `KingdomSettingsApplication.onParsedSubmit` replaced settings wholesale with the schema-parsed form, silently nuking any field absent from the DataModel schema (the #12 army-board flags, the #13 pacing thresholds) on every save. Now merges via `mergeObject`.
- **Region config save wiped `suppressEncountersOnClearedHex`** + the non-matrix category tables for the same reason. Now preserved across matrix saves.

## Architecture notes
- Two-layer pattern throughout: pure, unit-tested `*View` / `*Track` logic (no Foundry deps) + thin `*Context` builders that localize via `t()`. Keeps the testable core decoupled from ApplicationV2/DataModel glue.
- New persisted fields are nullable with defensive `?: default` reads — **no data migration required**.

## Test plan
- [x] `./gradlew assemble` green (JDK 25 toolchain + JDK 17)
- [x] `./gradlew jsTest` green — **1265 tests, 0 failures** (7 new suites: `SessionPrepViewTest`, `PacingAlertsTest`, `PacingAlertViewTest`, `ArmyWarPressureTest`, `ArmyPressureViewTest`, `EncounterCuratorDataModelsTest`, `EncounterCuratorPersistenceTest`)
- [x] `python3 scripts/check_i18n_keys.py` — all keys resolve, no flat-dotted keys
- [x] **Live Foundry render check** (Foundry 14, build 343): kingdom sheet nav shows both new tabs (**Army & Pressure**, **Pacing**); the Pacing panel renders with correct localized content + empty state and **no raw i18n keys**; **0 console/page errors** attributable to this work.
- [ ] Manual GM playtest: run several End Turns and confirm pacing advisories fire once at thresholds; deploy/recall an army and confirm the pressure meter moves; configure per-region category tables and confirm a curated encounter routes through them.

## Notes
- `lang/de.json` is intentionally **not** committed (regenerated by `createDummyTranslations`).
- Remaining follow-ups are minor: per-region encounter tables share the level-mismatch range (no dedicated threshold); pacing thresholds use sensible code defaults until configured.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
