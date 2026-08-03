# Implementation Plan: Settlement Benefit & Access Tracker

## Overview
This plan outlines the implementation of a system to make settlements and their structures more meaningful for players (PCs). Instead of just providing kingdom bonuses, certain buildings will now unlock specific player-facing benefits like character retraining, specialized crafting, and upgraded item purchase availability.

**Feature ID:** #6 in `feature-roadmap.md`
**Status:** Implemented on branch `kingmaker.6` (commit `002184f2`). Ported onto `kingmaker.5` on 2026-06-06 alongside a `structureBlacklist` crash fix (`Migration34`) so it builds and loads there. **Manual Foundry verification still pending — see §8.**

## 1. Affected Files
### Kotlin Source (Data Models & Logic)
- `src/commonMain/kotlin/at/posselt/pfrpg2e/data/kingdom/structures/Structure.kt`: To add capability for structures to define PC benefits (trainers, crafting).
- `src/commonMain/kotlin/at/posselt/pfrpg2e/data/kingdom/settlements/SettlementType.kt`: To ensure settlement types correctly drive the item purchase level logic.
- `src/commonMain/kotlin/at/posselt/pfrpg2e/data/kingdom/settlements/Settlement.kt`: To implement the aggregation logic that scans a settlement's structures for active benefits.
- `src/commonMain/kotlin/at/posselt/pfrpg2e/data/kingdom/structures/AvailableItems.kt`: (If necessary) to link item availability to structural presence.

### Templates (UI)
- `src/jsMain/resources/applications/kingdom/sections/settlements/*.hbs`: To add the "Settlement Benefits" or "Unlocked Access" UI component, listing available trainers and crafting types.

### Data Files
- `data/kingdom/...` (Relevant JSON files): To update structure definitions with new benefit metadata if using externalized data loading.

## rag_ref: docs/house-rules.md

## 2. Data Models
### New/Modified Entities
- **Structure Benefit**: A new concept or property within `Structure` that defines what a building grants to PCs.
    - `type`: Enum (e.g., `TRAINER`, `CRAFTER`, `MERCHANT`)
    - `identifier`: String (e.g., `"Investigator"`, `"Metallic Items"`, `"Magic Item Level 5"`)
- **Settlement Benefit Aggregator**: A logic layer that iterates through all structures in a settlement and collects unique benefits into a set for the UI to render.

## 3. Migrations
- **Schema Update**: If adding properties to `Structure` class, ensure any JSON/data-loading mechanism is updated to handle the new fields without breaking existing structure definitions.
- **Data Backfill**: No data backfill required as benefits are derived from existing structures.

## 4. UI/Template Changes
- **Settlement View Component**: A new section titled "Unlocked Access" or "Settlement Benefits".
    - **Trainers Sub-section**: List of classes available for retraining at this settlement (e.g., "Library: Investigator, Thaumaturge").
    - **Crafting Sub-section**: List of crafting types enabled here (e.g., "Smithy: Metallic items").
    - **Shopping Sub-section**: A clear indicator of the current item purchase level based on the settlement type (Town/City/Metropolis).

## 5. Tests
### Unit Tests
- `StructureBenefitTest`: Verify that a structure with `TRAINER` benefit correctly registers its class list.
- `SettlementBenefitAggregationTest`: Verify that if a Settlement has both a 'Library' and an 'Alchemy Lab', the aggregated benefits include all associated classes/crafting types.
- `ItemPurchaseLevelTest`: Ensure item purchase level matches the rules for the current settlement type (Town=3, City=9, Metropolis=15).

### Integration Tests
- Test that adding a structure to a Settlement in the data model correctly triggers an update to the UI component in the simulated Kingdom Sheet.

## 6. Manual Foundry Verification Checklist
- [ ] **Scenario: Basic Town**
    - Create a 'Town' level settlement with no structures.
    - Verify "Unlocked Access" is empty or shows only base town items.
- [ ] **Scenario: Trainer Unlocking**
    - Add a 'Library' structure to a settlement.
    - Verify that "Investigator, Thaumaturge, Psychic" appear in the trainer list on the UI.
- [ ] **Scenario: Crafting Access**
    - Add an 'Alchemy Laboratory' structure to a settlement.
    - Verify that "Alchemical items" appears under the crafting section.
- [ ] **Scenario: Item Purchase Level**
    - Upgrade a settlement from 'Town' to 'City'.
    - Verify the item purchase level limit has increased (e.g., from 3 to 9) in the UI display.
- [ ] **Scenario: Homebrew Toggle (Future/Optional)**
    - If implemented, verify that turning off "Non-capital upgrades" hides benefits for all settlements except the capital.

## 7. Design Decisions Reference
- Follows decisions from `docs/house-rules.md` regarding Trainer and Crafting structure mappings.
- Integrates with existing `SettlementType` hierarchy (Town, City, Metropolis).

## 8. Remaining Work — Detailed Testing TODO

> Code is implemented; what's left is **verification**. Do this when you next have Foundry up. The Hermes task `t_1c143642` carries a condensed copy of these steps.

### 8.1 Branch / build context (read first)
- The feature originated on **`kingmaker.6`** (commit `002184f2`). It was **ported onto `kingmaker.5`** on 2026-06-06 because that's the branch loaded in Foundry. The same `Inspect Settlement` dialog had a latent **`structureBlacklist` undefined crash** (it read `kingdom.structureBlacklist.toSet()` on data that lacked the field); fixed by making the field nullable + guarding both read sites (`InspectSettlement.kt`, `StructureBrowser.kt`) and adding **`Migration34`** to backfill `structureBlacklist = []` on existing kingdoms.
- If testing on a fresh `kingmaker.6` checkout instead, apply that same crash fix first or `Inspect Settlement` will throw.

### 8.2 Build commands
```bash
export JAVA_HOME=/home/grego/.local/jdks/jdk-25.0.3+9
export CHROME_BIN=$(command -v google-chrome || command -v chromium)
./gradlew assemble -x kotlinStoreYarnLock          # builds dist/main.js to load in Foundry
# Unit test (lives in commonTest on kingmaker.6; NOT yet ported to kingmaker.5):
./gradlew jsBrowserTest --tests '*SettlementBenefitAccessTracker*' -x kotlinStoreYarnLock
```

### 8.3 Where the UI is
Kingdom sheet → open a settlement → **Inspect Settlement** → new **"Unlocked Access"** section. It is a **table** with three rows (Item Purchase Level / Trainers / Crafting Access) — **not a dropdown**. Empty rows render "None".

### 8.4 Critical gotcha
Trainers and crafting are derived from `parsed.constructedStructures`. The structure must be **fully constructed** in that settlement — a structure still *under construction*, or a tile just placed on the scene that hasn't synced into the kingdom's structure list, will **not** appear.

### 8.5 Verification checklist
- [ ] **Item purchase level by size:** Village **1**, Town **3**, City **9**, Metropolis **15**.
- [ ] **Upgrade reflows level:** upgrade Town → City, confirm purchase level flips 3 → 9 and downstream available-item levels update.
- [ ] **Library** → Trainers row reads **"Library: Investigator, Thaumaturge, Psychic"**; Crafting row gains **Tomes**.
- [ ] **Garrison** → Trainers add **fighter, barbarian, champion, monk**.
- [ ] **Alchemy Laboratory** → Crafting **alchemical**; Trainers **alchemist, gunslinger, inventor**.
- [ ] **Smithy** or **Foundry** → Crafting **metallic** (multiple metal structures collapse to one row, names joined by " / ").
- [ ] **Dedup:** two structures granting the same benefit produce no duplicate entries.
- [ ] **`-vk` variants** (Vance & Kerenshara) unlock the same benefits as their base structure (id matched after stripping the `-vk` suffix).
- [ ] **No crash:** Inspect Settlement and Structure Browser open cleanly even on a kingdom that predates `structureBlacklist` (regression guard for the 8.1 fix).

### 8.6 Full structure → benefit reference
**Trainers:** shrine→cleric/oracle · library→investigator/thaumaturge/psychic · alchemy-laboratory→alchemist/gunslinger/inventor · tavern-*→bard · arcanists-tower→wizard/witch/sorcerer/magus · garrison→fighter/barbarian/champion/monk · sacred-grove→druid/kineticist/summoner/ranger · thieves-guild→rogue · pier→swashbuckler

**Crafting:** smithy|foundry→metallic · stonemason→runes · tannery→leather · arcanists-tower→scrollsWandsStaves · luxury-store→amuletsRings · library→tomes · alchemy-laboratory→alchemical · lumberyard→wooden · specialized-artisan→other

### 8.7 Follow-ups (optional)
- [ ] Port `SettlementBenefitAccessTrackerTest` (commonTest) onto `kingmaker.5` — its `Settlement(...)` constructor args may need updating for the diverged data model.
- [ ] Decide whether the "Non-capital upgrades" homebrew toggle should hide benefits for non-capital settlements (plan §6 scenario, not yet implemented).
