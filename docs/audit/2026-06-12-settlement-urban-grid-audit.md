# Settlement Details Matrix & Urban Grid Audit Report

**Date:** 2026-06-12
**Branch:** kingmaker.5
**Build status:** GREEN (assemble UP-TO-DATE)

---

## 1. Executive Summary

This audit covers the settlement subsystem in `pf2e-kingmaker-tools`, focusing on:

1. **SettlementDetailsMatrix** — workbook row migration from `Settlements!A46:A145`
2. **Urban Grid** — the `UrbanGrid`/`BlockGrid`/`SettlementEdges` data model vs. the workbook's Urban Grids sheet formulas
3. **Settlement construction** — how `EvaluateStructures` builds `Settlement` objects and the gaps in urban grid wiring

**Key findings:**

| # | Area | Severity | Status |
|---|------|----------|--------|
| 1 | SettlementDetailsMatrix rows complete | OK | Done |
| 2 | SettlementDetailsMatrix bonus matching | OK | Done |
| 3 | `Settlement.occupiedBlocks` ignored by code | **HIGH** | Data model mismatch |
| 4 | Urban grid edge → block terrain resolution missing | **HIGH** | Not implemented |
| 5 | `lotsBorderingWater` not derived from urban grid | **HIGH** | Not implemented |
| 6 | `pavedStreets` / `magicalStreetlamps` / `sewerSystem` not used for terrain | **HIGH** | Not implemented |
| 7 | Workbook `Blocks` / `Lots` / `Level` formulas not mirrored in code | **MEDIUM** | Not implemented |
| 8 | `blockGrid.isLand` conflates all non-LAND as "occupied" | **MEDIUM** | Design issue |
| 9 | `UrbanGrid.occupiedBlocks` can differ from `Settlement.occupiedBlocks` | **MEDIUM** | Naming ambiguity |
| 10 | `highestUniqueBonuses` grouping drops multi-structure same-key bonuses | **LOW** | Design note |

---

## 2. SettlementDetailsMatrix — Workbook Rows

### Status: CORRECT

The file `src/commonMain/kotlin/.../SettlementDetailsMatrix.kt` defines all 95 non-blank rows from workbook `Settlements!A46:A145`.

**Coverage check (representative):**
- Header rows (Agriculture, Arts, Boating, etc.) — present as `isHeader = true`
- Skill-qualified activities — mapped correctly with skill + activity
- General activities (Build Structure, Claim Hex, etc.) — mapped with activityId only
- Multi-activity rows (Repair Reputation, Establish Work Site) — use `activityIds` set
- Army section (Recover Army with 6 sub-types, Recruit Army, Train Army) — present

**Bonus matching logic** (Settlement.matrixBonusFor):
- Skill-only row (header): max of bonuses with matching skill AND no activity
- Skill-qualified activity row: max of skill-only, activity-only, AND exact skill+activity match
- Activity-only/general row: max of bonuses matching any of the row's activityIds (regardless of skill)
- Returns null when no bonus applies

The bonus matching respects the existing `Settlement.highestUniqueBonuses` which deduplicates by `(skill, activity)` group, keeping only the highest-value bonus per group.

---

## 3. Urban Grid — Data Model vs. Workbook

### 3.1 Data Model (code)

**File:** `UrbanGrid.kt`

```
SettlementEdges -> 4x UrbanGridEdge
  - hasWater, hasBridge, hasWoodWall, hasStoneWall

UrbanGrid -> 9x BlockGrid (A-I)
  BlockGrid -> 4x BlockTerrain (topLeft, topRight, bottomLeft, bottomRight)
  BlockTerrain: LAND, UNPAVED, PAVED, WATER, BRIDGE, WOOD_WALL, STONE_WALL

Settlement carries:
  - waterBorders: Int
  - hasBridge: Boolean
  - occupiedBlocks: Int (from data, NOT from UrbanGrid)
  - residentialLots: Int (sum of residential structure lots)
  - lotsBorderingWater: Int
  - edges: SettlementEdges
  - urbanGrid: UrbanGrid
  - pavedStreets: Boolean
  - magicalStreetlamps: Boolean
  - sewerSystem: Boolean
```

### 3.2 Workbook Layout (Urban Grids sheet)

The workbook's Urban Grids sheet has:

- **Rows 35-38:** Edge toggles per direction (N/E/S/W) for Water, Bridge, Wall (Wood), Wall (Stone)
- **Rows 14-28:** Block terrain grid — a visual 3x3 of 3x3 mini-grids (9 blocks × 4 lots each)
- **R10:** Settlement-level infrastructure toggles (Magical Streetlamps, Paved Streets, Sewer System)
- **R6-R13:** Settlement stats derived from blocks/infrastructure
  - `K7` = Settlement size lookup by name
  - `K10` = Blocks count formula: `MAX(COUNTIF(UNIQUE(QUERY(W:Z, "SELECT W, Z WHERE Z <> ''")), $B7), 1)`
  - `L10` = Lots consumed: `SUMIF(W:W, $B7, AC:AC)` (sum of lot values from structure list)
  - `M10` = Level: `MAX(MIN($K10, 20), 1)`
  - `K12` = Consumption (complex VLOOKUP minus structure-based reduction)
  - Overcrowded check: `=IF($K10-SUMIF($W:$W,$B7,$AD:AD)<1,"No","Yes (diff Residents)")`

### 3.3 Workbook Block Terrain Derivation (key formula)

The workbook derives each of the 36 lot positions (9 blocks × 4 lots) from the edge toggles AND infrastructure:

**Corner block formula** (e.g., Block A — uses North + West edges):
```
=IF($Q35,IF(Q36,"Bridge","Water"),"Land")  -- top-left lot (water/bridge from North or West edge)
=IF($Q36,"Bridge",IF($Q38,"Stone",IF($Q37,"Wood",IF($Q35,"Water","Land"))))  -- top-right
```

The formula IF order is: Water → Bridge → Stone Wall → Wood Wall → Water → Land

**Edge priority order** (from innermost to outermost IF):
1. Water (`hasWater`)
2. Bridge (`hasBridge`)
3. Stone Wall (`hasStoneWall`)
4. Wood Wall (`hasWoodWall`)
5. Fallback: Land

**Center + infrastructure:** The formula also checks `$I11` (Paved Streets) to set lots to Paved or Unpaved.

### 3.4 Critical Gap: Edge Resolution Not Implemented in Code

**The `UrbanGrid.kt` data model defines the *output* structure (what terrain each lot has) but there is NO function that derives the terrain from `SettlementEdges` + infrastructure toggles.**

The workbook derives 36 lot terrain values from 4 edge toggles + 3 infrastructure flags. The Kotlin code has no equivalent `resolveTerrain(edges, pavedStreets, magicalStreetlamps, sewerSystem) -> UrbanGrid` function.

**Impact:** The `Settlement.urbanGrid` field is stored and passed through unchanged from input data. If the input data doesn't already have resolved terrain, the urban grid is just a default (all LAND).

---

## 4. Settlement Construction — What Gets Computed vs. What Comes from Data

### 4.1 `EvaluateStructures.kt` — Settlement construction

The function builds a `Settlement` from `data` (the settlement's input configuration) plus resolved structures:

**Computed from structures:**
- `residentialLots` = sum of `lots` from structures where `isResidential == true`
- `hasBridge` = any structure has `isBridge`
- `consumptionReduction` = sum (with stacking rules)
- `bonuses` = combined skill/activity bonuses
- `storage`, `notes`, `allowCapitalInvestment`, `availableItems`
- `preventItemLevelPenalty`, `maximumCivicRdLimit`, `settlementActions`

**Passed through from data (NOT computed from urban grid):**
- `occupiedBlocks` = `data.occupiedBlocks` (raw input)
- `waterBorders` = `data.waterBorders` (raw input)
- `lotsBorderingWater` = `data.lotsBorderingWater` (raw input)
- `edges` = `data.edges` (raw input)
- `urbanGrid` = `data.urbanGrid` (raw input — default all-LAND if not set)

### 4.2 Workbook vs Code: `occupiedBlocks` mismatch

**Workbook:** Blocks = count of unique non-empty structure names in columns W:Z for this settlement. This is essentially "how many of the 9 grid blocks have at least one structure."

**Code:** `Settlement.occupiedBlocks` comes from `data.occupiedBlocks` — a manually entered integer. The `UrbanGrid.occupiedBlocks` property (which counts non-LAND blocks) is computed but **never used** to set `Settlement.occupiedBlocks`.

**The `UrbanGrid.occupiedBlocks` getter:**
```kotlin
val occupiedBlocks: Int
    get() = blocks.count { !it.isLand }
```
This counts blocks where ANY lot is not LAND. But `isLand` requires ALL 4 lots to be LAND. This means a block with 1 WATER lot and 3 LAND lots counts as "occupied" — which is semantically wrong for "occupied by structures."

### 4.3 Workbook vs Code: `lotsBorderingWater`

**Workbook:** Counts lots with WATER terrain across all blocks.

**Code:** `Settlement.lotsBorderingWater` comes from `data.lotsBorderingWater` — a raw input. The `UrbanGrid.totalWaterLots` property exists but is never wired to `Settlement.lotsBorderingWater`.

### 4.4 Workbook vs Code: `level`

**Workbook:** `=MAX(MIN(Blocks, 20), 1)` — capped at 20, minimum 1.

**Code:** `Settlement.level = max(1, occupiedBlocks)` — uses the raw `occupiedBlocks` from data, not the urban grid's block count. No cap at 20.

---

## 5. Detailed Findings

### FINDING 1 [HIGH]: `Settlement.occupiedBlocks` not derived from urban grid

**Location:** `EvaluateStructures.kt:258`, `Settlement.kt:124`

**Problem:** `Settlement.occupiedBlocks` is set from `data.occupiedBlocks` (user input). The `UrbanGrid.occupiedBlocks` property exists but is never used. The workbook computes blocks from structure placement.

**Expected:** Either:
- (a) Derive `occupiedBlocks` from the urban grid (count blocks with structures), OR
- (b) Derive it from `blocks` list (count blocks with `isOccupied` in the `Block` data class)

**Note:** The `Block` data class already has `isOccupied = occupiedLots > 0`, and `Settlement.blocks` is populated. So `blocks.count { it.isOccupied }` would be a code-side derivation that doesn't depend on the urban grid.

### FINDING 2 [HIGH]: No edge → terrain resolution function

**Location:** `UrbanGrid.kt` — missing function

**Problem:** There is no function that takes `SettlementEdges` + infrastructure flags and produces an `UrbanGrid` with resolved terrain per lot. The workbook has 36 formulas (one per lot) that do this.

**Expected:** A function like:
```kotlin
fun resolveUrbanGrid(
    edges: SettlementEdges,
    pavedStreets: Boolean,
    magicalStreetlamps: Boolean,
    sewerSystem: Boolean,
): UrbanGrid
```

The resolution logic per lot follows the workbook's IF chain:
1. If edge has water → WATER
2. If edge has bridge → BRIDGE
3. If edge has stone wall → STONE_WALL
4. If edge has wood wall → WOOD_WALL
5. If pavedStreets → PAVED
6. Otherwise → LAND (or UNPAVED if not paved)

Corner blocks use two edges (e.g., Block A = North + West), edge blocks use one edge, center block (E) uses no edges.

### FINDING 3 [HIGH]: `lotsBorderingWater` not derived from urban grid

**Location:** `EvaluateStructures.kt:273`, `Settlement.kt:136`

**Problem:** `lotsBorderingWater` is a raw input. The `UrbanGrid.totalWaterLots` property exists but is never wired.

**Expected:** When urban grid is resolved, set `lotsBorderingWater = urbanGrid.totalWaterLots`.

### FINDING 4 [HIGH]: Infrastructure flags not used for terrain resolution

**Location:** `UrbanGrid.kt`, `Settlement.kt:133-135`

**Problem:** `pavedStreets`, `magicalStreetlamps`, `sewerSystem` are stored on `Settlement` but never used to derive terrain. The workbook uses Paved Streets to set non-edge, non-water lots to PAVED instead of UNPAVED.

**Expected:** These flags should feed into the terrain resolution function.

### FINDING 5 [MEDIUM]: `BlockGrid.isLand` conflates terrain with occupancy

**Location:** `UrbanGrid.kt:74-76`

**Problem:** `isLand = lots.all { it == BlockTerrain.LAND }`. This means a block with water, bridge, or wall terrain is considered "not land" and thus "occupied." But water/wall/block aren't structures — they're terrain features. The `UrbanGrid.occupiedBlocks` uses `!isLand` which overcounts.

**Expected:** Either rename to `isDefaultTerrain` or add a separate `hasStructure` flag. The workbook's block count is based on structure placement, not terrain.

### FINDING 6 [MEDIUM]: `UrbanGrid.occupiedBlocks` vs `Settlement.occupiedBlocks` naming

**Location:** `UrbanGrid.kt:106-108`, `Settlement.kt:124`

**Problem:** Both classes have `occupiedBlocks` with different semantics:
- `UrbanGrid.occupiedBlocks` = count of blocks where `!isLand` (terrain-based)
- `Settlement.occupiedBlocks` = raw input integer (structure-based)

**Expected:** Rename `UrbanGrid.occupiedBlocks` to something like `nonLandBlocks` or `developedBlocks` to avoid confusion.

### FINDING 7 [MEDIUM]: Workbook `Blocks` formula uses structure names, not terrain

**Location:** Workbook Urban Grids R10

**Problem:** The workbook's block count formula is:
```
=MAX(COUNTIF(UNIQUE(QUERY(W:Z, "SELECT W, Z WHERE Z <> ''")), $B7), 1)
```
This counts unique structure-to-block assignments, not terrain changes. The code's `UrbanGrid.occupiedBlocks` counts terrain changes. These are fundamentally different.

**Expected:** The code should derive `occupiedBlocks` from the `blocks` list (which tracks actual structure placement), not from terrain.

### FINDING 8 [LOW]: `highestUniqueBonuses` drops same-key bonuses from different structures

**Location:** `Settlement.kt:149-157`

**Problem:** `highestUniqueBonuses` groups by `(skill, activity)` and keeps only the max value. If two structures both give "+2 Agriculture" (skill=AGRICULTURE, activity=null), only one is kept. This is correct for display (avoid double-counting) but means the displayed bonus doesn't show the total from all structures.

**Note:** This is likely intentional — the "unique" in the name suggests deduplication is by design. The `GroupedStructureBonus.structureNames` field preserves the set of contributing structures.

---

## 6. Recommendations

### Priority 1 — Wire existing urban grid data to settlement stats

1. **Derive `occupiedBlocks` from `blocks` list:** Replace `data.occupiedBlocks` with `blocks.count { it.isOccupied }` in `EvaluateStructures.kt:258`. This uses the already-computed `Block.isOccupied` which checks `occupiedLots > 0`.

2. **Derive `lotsBorderingWater` from urban grid:** After terrain resolution, set `lotsBorderingWater = urbanGrid.totalWaterLots`.

3. **Derive `waterBorders` from edges:** `edges.waterBorders` already computes this. Use it instead of `data.waterBorders`.

### Priority 2 — Implement terrain resolution

4. **Add `resolveUrbanGrid` function:** Implement the edge → terrain derivation matching the workbook's IF chain logic. Handle:
   - Corner blocks (2 edges): A (N+W), C (N+E), G (S+W), I (S+E)
   - Edge blocks (1 edge): B (N), D (W), F (E), H (S)
   - Center block (E): no edges, uses infrastructure only
   - Infrastructure: Paved Streets → PAVED, else UNPAVED for non-special lots

5. **Wire infrastructure flags:** Feed `pavedStreets`, `magicalStreetlamps`, `sewerSystem` into terrain resolution.

### Priority 3 — Clean up naming and semantics

6. **Rename `UrbanGrid.occupiedBlocks`** to `developedBlocks` or `nonDefaultBlocks` to distinguish from `Settlement.occupiedBlocks`.

7. **Rename `BlockGrid.isLand`** to `isAllLand` or `isDefaultTerrain` for clarity.

8. **Cap `Settlement.level` at 20** to match workbook: `level = max(1, min(20, occupiedBlocks))`.

---

## 7. Test Coverage Gaps

| Area | Existing tests | Missing |
|------|---------------|---------|
| SettlementDetailsMatrix rows | `SettlementDetailsMatrixTest` — row count, labels, bonus matching | None identified |
| UrbanGrid data model | `UrbanGridTest` — terrain counts, block properties | Edge resolution, terrain derivation |
| Settlement construction | `SettlementUrbanFieldsTest` — field derivation from structures | `occupiedBlocks` from blocks list |
| Urban grid parity | `UrbanGridParityAuditTest` — workbook vs code comparison | Full terrain resolution parity |

---

## 8. File Reference

| File | Role |
|------|------|
| `src/commonMain/.../settlements/UrbanGrid.kt` | UrbanGrid, BlockGrid, BlockTerrain, SettlementEdges |
| `src/commonMain/.../settlements/Settlement.kt` | Settlement data class, highestUniqueBonuses |
| `src/commonMain/.../settlements/SettlementDetailsMatrix.kt` | Workbook row definitions, bonus matching |
| `src/commonMain/.../settlements/SettlementSize.kt` | Size-based stats (population, consumption, etc.) |
| `src/commonMain/.../settlements/SettlementType.kt` | CAPITAL/NORMAL enum |
| `src/commonMain/.../settlements/SettlementLayoutType.kt` | Settlement layout types |
| `src/commonMain/.../structures/Structure.kt` | Structure data class (lots, isResidential, etc.) |
| `src/commonMain/.../structures/StructureTrait.kt` | Edifice/Wall/Lot traits |
| `src/commonMain/.../structures/StructureBonus.kt` | Skill/activity bonus mapping |
| `src/commonMain/.../modifiers/evaluation/EvaluateStructures.kt` | Settlement construction from structures |
| `src/commonMain/.../modifiers/bonuses/StructureBonuses.kt` | Modifier creation from bonuses |
| `src/commonTest/.../settlements/UrbanGridTest.kt` | UrbanGrid data model tests |
| `src/commonTest/.../settlements/UrbanGridParityAuditTest.kt` | Workbook parity tests |
| `src/commonTest/.../settlements/SettlementUrbanFieldsTest.kt` | Settlement field derivation tests |
| `src/commonTest/.../settlements/SettlementDetailsMatrixTest.kt` | Matrix row + bonus matching tests |
| `docs/structure-rules.md` | Structure rules documentation |
| `docs/plans/2026-06-01-settlement-details-matrix-workbook-rows.md` | Implementation plan |
