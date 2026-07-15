# Plan: Party XP Ledger with Auto-Offers

## 1. Problem Statement
Currently, kingdom and party XP (leveling) changes are opaque. While `KingdomData` tracks current `xp` and `level`, there is no auditable history of *why* these values changed (e.g., which hex clear or quest completion caused a spike), making audits of party progress difficult. Without an audit trail, it is impossible to verify "where did we get to level 9?" or reconcile manual adjustments with automated events.

## 2. Data Model
The plan introduces a durable ledger of XP transitions.

### Raw External Interfaces
- `RawXpLedgerEntry`:
    - `date: Date` (Turn timestamp)
    - `sourceKind: XpSourceKind` (Enum: `HEX_CLEAR`, `QUEST_COMPLETION`, `EXPEDITION_RESOLVED`, `RP_CONVERSION`, `MANUAL`)
    - `sourceRef: String` (UUID or Name of the source object)
    - `amount: Int` (Proposed XP amount)
    - `status: XpOfferStatus` (`OFFERED`, `CONFIRMED`, `DISMISSED`)
- `XpSourceKind`: Enum for categorized tracking.
- `XpOfferStatus`: Enum to manage the "GM Confirmation" workflow.

### Persistence & Migration
- **Persistence**: Entries will be stored within a new property in `KingdomData` (or an associated actor flag). Since this is part of the kingdom's state, it persists across turns and sessions.
- **Migration**: A `Migration<N>` will initialize the ledger as an empty array for existing kingdoms.

## 3. Engine Design
The engine will use a "Producer-Consumer" pattern with GM confirmation.

### Core Logic (`commonMain`)
- `XpLedgerService`: Handles appending new entries and managing status transitions.
- `XpOfferGenerator`: Pure function to create `OFFERED` entries based on event metadata.
- **Hooks**:
    - `HexContentManager.kt`: Hook into "cleared" transitions to propose `HEX_CLEAR` offers.
    - Quest/Expedition completion flows: Hook into final resolution steps to propose `QUEST_COMPLETION` or `EXPEDITION_RESOLVED` offers.
    - End-turn processing (`TurnTickingEngine`): A post-tick hook that scans for `OFFERED` entries and generates a "Session Digest" chat card.

### Ticking Surfaces
- **Daily/Event Hooks**: Immediate creation of `OFFERED` entries upon event completion.
- **End Turn Tick**: Batch processing of pending offers into the Chat UI.

## rad UI (FoundryVTT)
The ledger must be player-visible as it represents their progression.

### Components
- **Kingdom Sheet Tab**: A new "XP Ledger" tab in `KingdomSheet`.
- **Ledger View**: An interactive list/table showing the history of entries, grouped by status or date.
- **Control Actions**: Inline buttons on each entry for `[Confirm]` (applies XP and marks `CONFIRMED`) and `[Dismiss]` (marks `DISMISSED`).
- **Reconciliation Summary**: A header section in the tab showing: `Total Confirmed XP` vs `Current Kingdom XP`. A warning icon will appear if a discrepancy is detected (indicating manual edits or unrecorded changes).

### i18n
- New namespace: `kingdom.xp_ledger.*` for labels, buttons, and status descriptions.

## 5. Chat/Offer Surfaces
To prevent chat spam, we avoid immediate popups for every single hex clear.

- **The Offer Card**: Uses the `km-offer-*` pattern via `ChatButtons.kt`.
- **End-of-Session Digest**: At the end of a turn (or session), a summarized card is posted to the chat containing a list of all pending offers in a digestible format, with "Confirm All" or individual row buttons.

## 6. Interactions & Scope
### Existing System Integration
- `macros/XP.kt`: Update `updateXP` to log `MANSDUAL` entries in the ledger.
*   `sheet/Xp.kt`: Ensure `gainXp` and `levelUp` can be triggered by ledger confirmation.
- `dialogs/HexContentManager.kt`: Integrate event-driven offer creation.

### Out of Scope
- Recalculating the *amount* of XP (the existing calculation logic remains the source of truth; we only record the result).
- Handling movement/travel costs related to XP.

## 7. Test Plan
- **`commonTest`**: Unit tests for `XpLedgerService` (adding entries, status transitions, calculating totals).
- **`jsTest`**: Integration tests in the Foundry environment to verify that a simulated hex clear correctly generates an `OFFERED` entry in the actor's data.
- **Manual Verification**: A checklist for the GM to confirm the Ledger Tab renders and buttons function as expected within the Kingdom Sheet.

## 8. Phasing
- **Phase 1**: Implementation of `RawXpLedgerEntry` data model, persistence in `KingdomData`, and migration logic.
- **Phase 2**: Development of `XpOfferGenerator` and integration hooks for Hex/Quest transitions.

- **Phase 3**: Implementation of the Kingdom Sheet "XP Ledger" tab and reconciliation view.
- **Phase 4**: Chat automation (End-turn digest) and `km-offer-*` button integration.
