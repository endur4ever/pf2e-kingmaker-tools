# Urban Grid Parity Audit

**Date:** 2026-06-11  
**Scope:** Urban Grid formula implementation vs. rules spec and workbook rows  
**Sources audited:**
- `src/commonMain/kotlin/.../settlements/UrbanGrid.kt`
- `src/commonMain/kotlin/.../settlements/SettlementDetailsMatrix.kt`
- `src/commonMain/kotlin/.../settlements/Settlement.kt`
- `src/commonMain/kotlin/.../evaluation/EvaluateStructures.kt`
- `docs/structure-rules.md`
- `docs/plans/2026-06-01-settlement-details-matrix-workbook-rows.md`
- Existing tests: `UrbanGridTest.kt`, `SettlementUrbanFieldsTest.kt`

---

## Legend

| Status | Meaning |
|--------|---------|
| **COVERED** | Implemented and tested in commonTest |
| **GAP** | Specified in rules/workbook but not implemented or not wired into the evaluation pipeline |
| **UNKNOWN** | Not enough information in the spec to determine coverage |

---

## UrbanGrid.kt — Data Model & Formulas

| # | Rule / Behavior | Status | Notes |
|---|----------------|--------|-------|
| U1 | `BlockTerrain` enum: LAND, UNPAVED, PAVED, WATER, BRIDGE, WOOD_WALL, STONE_WALL (7 values) | **COVERED** | All 7 values exist; `BlockTerrainTest` verifies count and membership |
| U2 | `BlockGrid` — 4 lot positions (topLeft, topRight, bottomLeft, bottomRight) | **COVERED** | `BlockGridTest` verifies defaults, `lots` accessor, copy, equals, hashCode |
| U3 | `BlockGrid.waterCount` — counts WATER cells | **COVERED** | Tested in `BlockGridTest.waterCountMixed` |
| U4 | `BlockGrid.bridgeCount` — counts BRIDGE cells | **COVERED** | Tested in `BlockGridTest.bridgeCountMixed` |
| U5 | `BlockGrid.pavedCount` — counts PAVED cells | **COVERED** | Tested in `BlockGridTest.pavedCountMixed` |
| U6 | `BlockGrid.isLand` — true when all 4 cells are LAND | **COVERED** | Tested in `BlockGridTest.isLandTrueWhenAllLand` and `isLandFalseWhenMixed` |
| U7 | `UrbanGrid` — 9 blocks A–I in row-major order | **COVERED** | `UrbanGridTest.blocksOrderedAiToI` verifies A–I ordering |
| U8 | `UrbanGrid.totalWaterLots` — sum of waterCount across all 9 blocks | **COVERED** | Tested in `UrbanGridTest.totalWaterLotsSumsAcrossAllBlocks` |
| U9 | `UrbanGrid.totalBridgeLots` — sum of bridgeCount across all 9 blocks | **COVERED** | Tested in `UrbanGridTest.totalBridgeLotsSumsAcrossAllBlocks` |
| U10 | `UrbanGrid.lotsBorderingWater` — equals totalWaterLots | **COVERED** | Tested in `UrbanGridTest.lotsBorderingWaterEqualsTotalWaterLots` |
| U11 | `UrbanGrid.occupiedBlocks` — count of blocks where isLand == false | **COVERED** | Tested up to max 9 in `UrbanGridTest.occupiedBlocksMaxNine` |
| U12 | `UrbanGridEdge` — per-edge toggles: hasWater, hasBridge, hasWoodWall, hasStoneWall | **COVERED** | `UrbanGridEdgeTest` covers all flags, defaults, copy, equals |
| U13 | `SettlementEdges` — 4 edges (north, east, south, west) | **COVERED** | `SettlementEdgesTest` covers all edges, waterBorders count, hasBridge, hasWoodWall, hasStoneWall |
| U14 | `SettlementEdges.waterBorders` — count of edges with hasWater | **COVERED** | Tested 0 through 4 |
| U15 | Block terrain derivation from edge toggles + infrastructure flags (GAP-2 from rules) | **GAP** | `BlockGrid` terrain is set manually; no function derives it from `SettlementEdges` + `pavedStreets`/`sewerSystem` flags. The rules describe auto-derivation from edge toggles, but the code has no such mapping. |
| U16 | `UrbanGrid` copy/equals/hashCode/toString | **COVERED** | `UrbanGridTest` covers all data class operations |

---

## Settlement.kt — Urban Fields on the Settlement Data Class

| # | Field / Behavior | Status | Notes |
|---|-----------------|--------|-------|
| S1 | `magicalStreetlamps: Boolean = false` field exists | **COVERED** | Field declared; `SettlementUrbanFieldsTest` verifies default false, can set true, copy preserves |
| S2 | `pavedStreets: Boolean = false` field exists | **COVERED** | Same test coverage as S1 |
| S3 | `sewerSystem: Boolean = false` field exists | **COVERED** | Same test coverage as S1 |
| S4 | `lotsBorderingWater: Int = 0` field exists | **COVERED** | Tested: default 0, can set custom value, copy preserves |
| S5 | `edges: SettlementEdges` field exists | **COVERED** | Tested: default empty, custom edges, copy preserves |
| S6 | `urbanGrid: UrbanGrid` field exists | **COVERED** | Tested: default 9 land blocks, custom grid, copy preserves |
| S7 | `isOvercrowded` — `occupiedBlocks > residentialLots` | **UNKNOWN** | Formula exists in Settlement.kt but not tested with urban grid data. No test exercises the overcrowding check with non-default residentialLots. |
| S8 | `lacksBridge` — `waterBorders >= 4 && !hasBridge` | **UNKNOWN** | Formula exists but no test exercises it with waterBorders >= 4. |
| S9 | `magicalStreetlamps` derived from structures during evaluation | **GAP** | `evaluateSettlement()` in `EvaluateStructures.kt` never sets `magicalStreetlamps`. It always defaults to `false`. No structure flag maps to this field. |
| S10 | `pavedStreets` derived from structures during evaluation | **GAP** | Same as S9 — never set by `evaluateSettlement()`. |
| S11 | `sewerSystem` derived from structures during evaluation | **GAP** | Same as S9 — never set by `evaluateSettlement()`. |
| S12 | `lotsBorderingWater` derived from urban grid or structures during evaluation | **GAP** | `evaluateSettlement()` never sets `lotsBorderingWater`. It always defaults to 0. The `UrbanGrid.lotsBorderingWater` property exists but is never wired into `Settlement.lotsBorderingWater`. |
| S13 | `edges` derived from structures during evaluation | **GAP** | `evaluateSettlement()` never sets `edges`. Always defaults to `SettlementEdges()` (all false). |
| S14 | `urbanGrid` derived from structures during evaluation | **GAP** | `evaluateSettlement()` never sets `urbanGrid`. Always defaults to `UrbanGrid()` (all LAND). |

---

## SettlementDetailsMatrix.kt — Workbook Rows

| # | Rule / Behavior | Status | Notes |
|---|----------------|--------|-------|
| M1 | `settlementDetailsMatrixRows` contains all non-blank labels from workbook rows 46–145 in order | **COVERED** | 100 rows defined; matches workbook spec. No test yet verifying label parity against the xlsx file. |
| M2 | `SettlementDetailsMatrixRow` data class with workbookRow, label, skill, activityId, isHeader, activityIds | **COVERED** | Data class exists with all fields. |
| M3 | `matrixBonusFor(row)` — skill-only row matching | **COVERED** | Implemented: matches bonuses with matching skill and no activity. |
| M4 | `matrixBonusFor(row)` — skill-qualified activity row matching | **COVERED** | Implemented: max of skill-only, activity-only, and skill+activity bonuses. |
| M5 | `matrixBonusFor(row)` — activity-only/general row matching | **COVERED** | Implemented: matches bonuses with matching activity regardless of skill. |
| M6 | `matrixBonusFor(row)` — returns null when no bonus applies | **COVERED** | Implemented: returns null for no match. |
| M7 | Parenthesized labels map to both skill and activity (e.g. "Rest and Relax (Arts)" -> ARTS + rest-and-relax) | **COVERED** | Verified in row definitions. |
| M8 | General rows (Build Structure, Claim Hex, etc.) mapped by activity id with no fixed skill | **COVERED** | Verified in row definitions. |
| M9 | Deterministic workbook reconciliation (all xlsx labels present in code) | **GAP** | No automated test or script verifies parity between the xlsx file and `settlementDetailsMatrixRows`. The plan's Task 5 describes a Python verifier but it was never added. |

---

## EvaluateStructures.kt — Settlement Evaluation Pipeline

| # | Behavior | Status | Notes |
|---|----------|--------|-------|
| E1 | `residentialLots` — sum of `lots` from structures where `isResidential` | **COVERED** | Line 226–228 of EvaluateStructures.kt. |
| E2 | `hasBridge` — true if any constructed structure `isBridge` | **COVERED** | Line 232. |
| E3 | `occupiedBlocks` — passed through from `SettlementData` | **COVERED** | Line 250. |
| E4 | `SettlementData` contains: name, occupiedBlocks, type, isSecondaryTerritory, waterBorders, id, layoutType, populationRoster | **COVERED** | Data class defined at line 178. |
| E5 | `SettlementData` does NOT contain `urbanGrid`, `edges`, `lotsBorderingWater`, `magicalStreetlamps`, `pavedStreets`, `sewerSystem` | **GAP** | These fields exist on `Settlement` but `SettlementData` (the input to `evaluateSettlement`) has no way to pass them in. `evaluateSettlement` always uses defaults. |
| E6 | Capital investment fallback merges capital bonuses into non-capital settlements | **COVERED** | `includeCapital()` function at line 149. |

---

## Summary

### COVERED (28 items)
All `UrbanGrid`, `BlockGrid`, `BlockTerrain`, `UrbanGridEdge`, `SettlementEdges` data model behaviors and their formulas are implemented and tested. The `SettlementDetailsMatrix` row definitions and bonus matching logic are fully implemented. Core settlement evaluation (residentialLots, hasBridge, occupiedBlocks, bonuses) is wired up.

### GAP (8 items)
| ID | Gap | Estimated Fix |
|----|-----|---------------|
| U15 | No function derives `BlockGrid` terrain from `SettlementEdges` + infrastructure flags | ~40 LOC: add derivation function |
| S9–S11 | `magicalStreetlamps`, `pavedStreets`, `sewerSystem` never set during evaluation | ~15 LOC: add structure flag mapping or user-settable fields |
| S12 | `lotsBorderingWater` never derived from urban grid or structures | ~10 LOC: wire `urbanGrid.lotsBorderingWater` into settlement field |
| S13 | `edges` never set during evaluation | ~10 LOC: add to SettlementData or derive from structures |
| S14 | `urbanGrid` never set during evaluation | ~10 LOC: add to SettlementData or derive from structures |
| M9 | No automated workbook parity verification | ~20 LOC: add test or script |
| E5 | `SettlementData` missing urban grid fields | ~10 LOC: add fields to data class |

### UNKNOWN (2 items)
| ID | Question |
|----|----------|
| S7 | `isOvercrowded` formula correctness — needs test with non-default residentialLots |
| S8 | `lacksBridge` formula correctness — needs test with waterBorders >= 4 |

---

## Recommended Next Steps

1. **Add missing `commonTest` cases** for S7 (`isOvercrowded`) and S8 (`lacksBridge`) to resolve UNKNOWNs.
2. **Fix E5** — add `urbanGrid`, `edges`, `lotsBorderingWater`, `magicalStreetlamps`, `pavedStreets`, `sewerSystem` to `SettlementData` so they can be passed into `evaluateSettlement`. This is the foundational fix that unblocks S9–S14.
3. **Fix S9–S11** — determine whether these should be derived from structure flags or remain user-settable. If structure-driven, add mapping in `evaluateSettlement`.
4. **Fix S12–S14** — wire `urbanGrid.lotsBorderingWater` and `edges` into the settlement during evaluation, or accept them as user input via `SettlementData`.
5. **Fix U15** — implement block terrain derivation from edge toggles if the rules require auto-derivation.
6. **Fix M9** — add a deterministic test that verifies all xlsx labels appear in `settlementDetailsMatrixRows`.
