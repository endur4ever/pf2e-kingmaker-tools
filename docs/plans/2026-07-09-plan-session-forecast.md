# Plan: Next-Session Forecast — Dry-Run the Coming Days and Turn in Session Prep

## 1. Problem Statement
The Game Master (GM) often prepares for a session by mentally simulating upcoming events: "Which caravan arrives tomorrow?", "Is the war threat arriving next week?", "Will we have enough food for the next turn?". Currently, this requires manual checking of multiple disparate lists or relying on memory.

This feature provides an automated "Next Session Forecast" within the **Session Prep** dashboard. By leveraging the existing pure-Kotlin simulation engines (`DailyTickEngine` and `TurnTickingEngine`), the system can perform a non-mutating look-ahead to predict exactly which beats (arrivals, completions, expirations) will occur in the next $N$ days plus one kingdom turn.

## 2. Data Model
Since this feature is transient and computed on demand, no new persistent database or LevelDB changes are required. We define a lightweight, read-only structure for the UI:

### `ForecastEvent` (Transient)
*   **`id`**: Unique identifier for the event instance.
*   **`date/turn`**: The in-world date or turn number when the event occurs.
*   **`type`**: Enum (`Arrival`, `Completion`, `Expiration`, `Threat`, `ResourceAlert`).
*   **`message`**: Human-readable description (e.g., "Caravan 'Golden Grain' arrives").
*   **`subsystemLink`**: A reference to a specific tab/view in the Kingdom Sheet (e.g., `caravan-tab`, `campaign-clocks`).

### `ForecastResult` (Transient)
*   A list of `ForecastEvent` objects grouped by their occurrence date or turn.

## 3. Engine Design
We will implement a new service: `KingdomSimulationService`. This service will be purely functional and side-effect free.

### Simulation Logic
1.  **Input**: A snapshot of the current kingdom state (`KingdomState`) and an integer `daysAhead`.
2.  **Phase 1 (Daily Loop)**:
    *   For $i = 1$ to `daysAhead`:
        *   Iteratively call `DailyTickEngine.tickTravelEta` for all active companions/caravans.
        *   Iteratively call `DailyTickEngine.tickExpedition` for all ongoing expeditions.
        *   Iteratively call `DailyTickEngine.tickPersonalQuest` for all active personal quests.
        *   Capture any `arrived`, `completed`, or `failed` flags as `ForecastEvent` entries.
3.  **Phase 2 (End Turn)**:
    *   Using the state reached after $N$ days, perform a single call to `TurnTickingEngine.tick(...)`.
    *   Capture events from the `TickResult`: `clockEvents`, `newlyTriggeredThreats`, and `questDeadlineReached`.
4.  **Output**: A `ForecastResult` containing all captured beats.

### Complexity/Cost
The complexity is $O(N \times M)$ where $N$ is days ahead and $M$ is the number of active entities (companions, caravans, etc.). Given typical session horizons ($N < 14$), this is extremely efficient.

## 4. UI Integration
The forecast will be integrated into the existing `SessionPrepView`.

*   **New Section**: "Next Session Forecast" added to the dashboard.
*   **Rendering**: A list of events, grouped by in-world date/turn.
*   **Interactivity**: Each event row acts as a hyperlink (via `subsystemLink`) that directs the GM to the relevant section of the Kingdom Sheet (e.g., clicking an "Expedition Completion" link jumps the user to the Expedition tab).
*   **i18n**: All event descriptions and types will use the `pf2e-kingmaker-tools` i18next namespace.

## 5. Chat/Offer Surfaces
For high-impact predicted events (e.g., a new war threat or a major resource shortage), the system can generate "Pre-emptive Offer" cards (`km-offer-*`). These appear in the GM's chat log, allowing them to see a warning *before* the event actually triggers on the turn, facilitating better session preparation.

## 6. Interactions & Out-of-Scope
### Interactions
*   **`DailyTickEngine`**: For companion/caravan/quest progression.
*   **`TurnTickingEngine`**: For end-turn consequences (clocks, threats).
*   **`CampaignClockManager`**: For predicting clock expirations.

### Out-of-Scope
*   **Multi-turn forecasting**: Simulating multiple kingdom turns is explicitly out of scope to avoid exponential complexity and "hallucinated" long-term uncertainty.
*   **Persistent Predictions**: Forecasts are never saved to the database; they must be regenerated when the GM opens Session Prep.
*   **Player Visibility**: This view is strictly for the GM.

## 7. Test Plan
*   **`commonTest` (Unit Tests)**:
    *   Verify that a caravan with 2 days ETA correctly shows an `Arrival` event in a 3-day forecast but not in a 1-day forecast.
    *   Verify that `TurnTickingEngine`'s end-turn threats are captured in the forecast.
    *   Ensure no mutations occur to the original `KingdomState`.
*   **`jsTest` (UI/Integration Tests)**:
    *   Mock a `SessionPrepContext` with various upcoming events and verify that the Handlebars template renders the correct number of rows.
*   **Manual Verification**:
    *   In Foundry, set up a caravan arriving in 3 days. Open Session Prep. Confirm the event appears under "Next Session Forecast".

## 8. Phasing
1.  **Phase 1: Simulation Engine**: Implement `KingdomSimulationService` as a pure Kotlin module with comprehensive unit tests.
2.  **Phase 2: Context & UI Integration**: Update `SessionPrepContext` and `SessionPrepView` to call the simulator and render the new section.
3.  **Phase 3: Interactivity & i18n**: Implement subsystem deep-linking and finalize localization strings.
