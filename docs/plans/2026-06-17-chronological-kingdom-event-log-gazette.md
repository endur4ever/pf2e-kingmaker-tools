# Implementation Plan - Chronological Kingdom Event Log & Gazette

Implement the "Chronological Kingdom Event Log & Gazette" feature (task `t_239a880e`), which aggregates all major activities, expansions, caravan updates, and clock events completed during a kingdom turn, saving them as a structured chronological summary in the turn history. This allows GMs to view them on the Session Prep tab and automatically export them to a player-facing journal.

## User Review Required

> [!NOTE]
> The summary is constructed automatically at the end of each turn in `performEndTurn` before clearing the performed activities flag. It aggregates:
> 1. All kingdom activities performed during the turn (e.g. "Celebrate Holiday (x2)", "Build Structure").
> 2. Kingdom expansions (claimed hex counts by diffing the current size with the previous turn's size).
> 3. Active caravan deliveries, raids, or losses.
> 4. Campaign clock events that ticked or expired during the turn.
> 
> The aggregated summary is saved directly to the existing `notes` field in `RawTurnRecord`. Because the `Session Prep` tab and `SessionPrepJournalExporter` already display and export the `notes` field, this integration automatically enriches the Session Prep history log and the exported recap journal with a detailed chronological gazette without changing the underlying database schemas or data models.

## Proposed Changes

### Turn Resolution

#### [MODIFY] [TurnWizardApplication.kt](file:///home/grego/code/pf2e-kingmaker-tools/src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/dialogs/TurnWizardApplication.kt)
- In `performEndTurn`, before clearing the performed activities at line 159, retrieve the performed activities map using `actor.getPerformedActivities()`.
- Diff `realm.size` against the last turn's size in `kingdom.turnHistory?.lastOrNull()?.size` to calculate the number of claimed hexes.
- Collect caravan and shipment events during their ticks.
- Format all collected events into a single concise summary string (e.g., `Activities: Celebrate Holiday (x2), Build Structure | Expansion: Claimed 1 hex(es) | Caravans: 2 Ore delivered to Sootscale`).
- Pass the generated summary string to the `notes` argument of `buildTurnRecord`.

---

## Verification Plan

### Automated Tests
- Create a unit test `TurnHistoryGazetteTest.kt` or add to `TurnHistoryTest.kt` verifying that the turn gazette summary string is correctly constructed under various turn events (activities performed, size diff, caravan events).
- Run `./gradlew jsTest -x kotlinStoreYarnLock` to verify compilation and all tests pass.

### Manual Verification
1. Open the Turn Wizard, perform some kingdom activities, and complete the turn.
2. Open the Session Prep tab and verify that the "Recent Turns" section shows the detailed activity and event summary for the completed turn.
3. Click the "Export Session Recap" button on the Session Prep tab and verify that the created Foundry Journal contains the turn-by-turn gazette summary.
