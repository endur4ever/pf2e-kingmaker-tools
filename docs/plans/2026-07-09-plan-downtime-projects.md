# Plan: PC Downtime Project Ledger on World Clock (Craft / Retrain / Earn Income / Rituals)

**Status:** Draft  
**Date:** 2026-07-15  
**Assignee:** builder  
**Repository:** `/home/grego/code/pf2e-kingmaker-tools`

## 1. Problem Statement & Value

In the Kingmaker campaign, players spend months in downtime performing activities like crafting items, retraining feats, or conducting rituals. Currently, tracking these multi-day, settlement-gated processes is a manual GM task (often relegated to notebooks), which leads to "notebook fatigue" and potential rule inconsistencies regarding structure prerequisites.

**Value Proposition:**
By introducing an automated Project Ledger, we transform downtime from a static period into a dynamic, tracked gameplay loop. This system automates the lifecycle of player projects using the proven "daily expedition" model. It ensures players cannot perform activities without required structures (e.g., no retraining without a Library) and provides automated, GM-confirmed rewards (XP, loot, feats) upon project completion via chat offers, reducing the GM's administrative burden.

## / 2. Data Model & Migration

All new interfaces will be nullable or additive to ensure zero-downtime, non-destructive migration for existing kingdom and camping sessions.

### 2.1 Project Shape (`RawPcDowntimeProject`)
The core interface will mirror the `RawCompanionExpedition` structure:

| Field | Type | Purpose |
|-------|------|---------|
| `id` | String | Unique identifier for this project instance. |
| `pcActorUuid` | String | The actor participating in the project. |
| `kind` | Enum (`craft`, `retrain`, `earn_income`, `ritual`) | The type of downtime activity. |
| `targetRef` | String | Identifier for target (e.g., Item ID, Feat Name). |
| `settlementId` | String? | The settlement where the project is hosted. |
| `daysTotal` | Int | Total duration required for completion. |
| `daysRemaining` | Int | Days remaining on the world clock. |
| `dailyCost` | Int? | Optional gold or resource cost per day. |
| `status` | Enum (`inProgress`, `paused`, `completed`) | Current state of the project. |
| `accruedRewards` | Map<String, Int> | Quantifiable rewards (e.g., `"xp": 10`). |

### 2.2 Storage & Migration
- **Storage:** Persisted within `KingdomData` or a dedicated `DowntimeRegistry`.
- **Migration Strategy:** A new migration (`MigrationX`) will add the project array to the kingdom JSON, initializing it as an empty array for legacy data.

## 3. Engine Design: The Daily Tick Lifecycle

The engine implements a pure Kotlin core integrated into the existing daily tick infrastructure.

### 3.1 Ticking Surface
Integration with **`DailyTickHooks.kt`**. Every time the world clock advances one day:
1. Iterate through all active projects in `inProgress` status.
2. Decrement `daysRemaining`.
3. **Pause Logic:** Check if the prerequisite structure/settlement still exists (via `InspectSettlement` logic). If the settlement or required building is destroyed, transition status to `paused`.

### 3.2 Completion & Rewards
When `daysRemaining == 0`:
1. Transition status to `completed`.
2. Trigger a **GM-CONFIRMED OFFER** (`km-offer-downtime-completion`) using the `ChatButtons.kt` pattern.
3. The offer will present: "PC [Name] has completed [Project Kind]: [Target]. Rewards: [List]."
4. Buttons: `[Apply Reward]` (applies XP/Loot to player/kingdom) and `[Cleanup]` (removes project from registry).

## 4. UI & Player Surface

### 4.1 Kingdom/Camping Sheet Integration
- **Downtime Tab:** A new section in the Kingdom Sheet displaying all active, paused, and recently completed projects.
- **Creation Dialog:** A unified dialog for launching projects: Select PC $\to$ Select Kind $\to$ Enter Target $\to$ Select Settlement (with real-time validation of structure availability).

### 4.2 Chat & GM Offer Surfaces
Every consequence must be a GM-confirmed offer to maintain narrative control.
- **Offer Pattern:** `km-offer-downtime-completion`.
- **Visuals:** A formatted chat card with the project summary and interactive buttons for reward resolution.

## 5. Interactions & Scope

### 5.1 Integration Points
- **`kingdom/DailyTickHooks.kt`**: Primary engine hook for daily decrementing and status transitions.
- **`dialogs/InspectSettlement.kt`**: Used during project creation to validate structure presence (e.g., "Does this town have a Smithy?").
- **`kingdom/KingdomData.kt`**: Data persistence layer.

### 5.2 Out of Scope
- **Adjudication:** The system does not adjudicate the *rules* of crafting or feats; it only tracks the *time and prerequisites*. The GM still determines if a craft succeeds.
- **Economic Simulation:** We provide the tracker, but do not implement the full `Earn Income` mathematical model.

## 6. Test Plan

### 6.1 Unit Tests (`commonTest`)
- **`DowntimeEngineTest`**: Verify `daysRemaining` decrementing and status transition to `completed`.
- **`PauseLogicTest`**: Ensure projects transition to `paused` if the prerequisite settlement/structure is missing.

### 6.2 Integration Tests (`jsTest`)
- **`ProjectCreationFlowTest`**: End-to-end: Create project $\to$ Advance clock $\to$ Verify completion offer in chat.

### 6.3 Manual Verification (FoundryVTT)
- [ ] Confirm projects appear on the Kingdom Sheet tab.
- [ ] Verify that destroying a settlement mid-project pauses the project.
- [ ] Check that clicking `[Apply Reward]` correctly updates player/kingdom data.

## 7. Implementation Phases

1. **Phase 1: Data Model & Migration.** Implement `RawPcDowntimeProject` interface and migration logic.
2. **Phase 2: Core Engine.** Implement the daily tick decrementing logic and pause-on-destruction mechanics in `DailyTickHooks`.
3. **Phase 3: UI & Creation.** Develop the Kingdom Sheet downtime tab and project creation dialog (with prerequisite validation).
4. **Phase 4: Completion Offers.** Implement the Chat/Offer system (`km-offer`) and reward application logic.
