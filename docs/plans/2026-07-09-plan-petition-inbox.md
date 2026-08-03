# Plan: Petition Inbox — NPC audiences addressed to each leadership role

## 1. Problem Statement & Value Proposition
In a Kingmaker campaign, players often experience "dead turns" where kingdom management feels purely mechanical (updating resources). To increase player engagement and provide roleplay hooks between sessions, the **Petition Inbox** feature introduces structured, persona-driven interactions.

By targeting specific PCs assigned to leadership roles (e.g., the Magister receiving an arcane dispute), we transform the Kingdom Sheet from a passive dashboard into an active source of player-centric narratives. This drives players to check the sheet not just for resource counts, but for "unread" political and social developments that impact their characters directly.

## /2. Data Model
The implementation will introduce a new `RawPetition` data structure persisted within the kingdom state.

### RawPetition Interface
```kotlin
data class RawPetition(
    val id: String,                 // Unique identifier (UUID)
    val petitionerRef: String,      // Reference to an actor/NPC from PopulationDialogs
    val targetRole: Leader,         // The leadership role this is addressed to (e.g., MAGISTER)
    val templateKey: String,        // i18n key for the petition text (e.g., "petition.tax_revolt.text")
    val options: Array<PetitionOption>, // 2-3 response options
    val dueTurn: Int,               // The turn number by which a decision must be made
    val status: PetitionStatus = PetitionStatus.OPEN // OPEN | ANSWERED | EXPIRED
)

data class PetitionOption(
    val label: String,              // Button text (e.g., "Grant the request")
    val consequences: Array<PetitionConsequence> // Consequences to apply if chosen
)

data class PetitionConsequence(
    val type: ConsequenceType,      // UNREST | STANDING_DELTA | QUEST_SPAWN | RP_COST
    val value: String               // Context-dependent (e.g., "-1" for unrest, "quest_id_xyz")
)

enum class PetitionStatus { OPEN, ANSWERED, EXMSPIRED }
```

### Migration & Persistence
- **Persistence**: Stored in the `KingdomData` object as a list of `RawPetition`.
- **Migration**: No immediate migration required for existing kingdoms since this is a new feature. However, any future addition of petitions to legacy saves must include an empty list initialization.

## 3. Engine Design
The logic will be integrated into the existing `TurnTickingEngine` to ensure turn-based deadlines and consequences are processed during the standard monthly tick.

### Generation Model (End Turn)
At the end of every kingdom turn, a generation service will:
1. **Identify Targets**: Iterate through active leaders in `Leaders.kt`. For each leader with an owned actor, assess potential petitions.
2. **Select Template**: Consult a JSON-based template catalog keyed by `Leader` role and current settlement/event state (e.s., if "War" is active, higher weight for "Militia Request").
3. **Populate Petitioner**: Use `PopulationDialogs` to select a named NPC from the population roster based on suitable casting.
4. **Set Deadline**: Assign a `dueTurn` relative to the current turn (e.g., `currentTurn + 3`).

### Ticking & Expiration (TurnTickingEngine)
During the `tick()` operation:
1. **Deadline Check**: For every `OPEN` petition, check if `dueTurn <= currentTurn`.
2. **Expiration Logic**: If expired, transition status to `EXPIRED`. Trigger an "auto-consequence" offer (e.g., a default negative consequence like increased Unrest) via the `km-offer-*` pattern.
3. **Consequence Application**: When a player/GM selects an option, apply the associated `PetitionConsequence` using existing appliers (Unrest, Faction Standing).

## 4. UI / UX Implementation
The goal is to create a "Personal Inbox" feel for each leader.

### Components
- **Kingdom Sheet Tab**: A new "Inbox" section in the Kingdom Sheet.
- **Per-Role Filtering**: The view will automatically filter petitions based on the current user's ownership of leadership roles (using `Leaders.kt`).
- **Unread Badges**: Implementation of a client-side "seen/unseen" tracking system using Foundry's `flags`. A red badge appears on the Kingdom Sheet navigation if unread petitions exist for the user's role.
- **Template Files**: Use `.hbs` files for rendering the petition body and response buttons.

### i18n Keys
Namespace: `pf2e-kingmaker-tools.petition`
- `inbox.title`: "Petition Inbox"
- `inbox.empty`: "No new petitions for your office."
- `status.open`: "Open"
- `status.expired`: "Expired"

## 5. Chat & Offer Surfaces
Responses must follow the established **GM-Confirmed Offer** pattern to maintain agency and prevent accidental consequences.

### Response Workflow
1. **Player Action**: The leader (player) clicks an option button in the Inbox UI.
2. **GM Prompt**: A `ChatButton` appears in the GM's chat log: "The [Magister] has responded to the [Tax Revolt]: [Option A]? [Option B]?"
3. **Confirmation**: Once the GM confirms (or a pre-configured auto-confirm rule applies), the consequences are applied, and the petition is marked `ANSWERED`.

## 6. Interactions & Scope
### Integrated Systems
- `PopulationDialogs`: For petitioner selection/casting.
- `TurnTickingEngine`: For deadline management and expiration triggers.
- `FactionRelations`: For applying standing deltas via consequences.
- `questevent/`: For spawning new quests as a consequence of petitions.

### Out-of-Scope
- **NPC Autonomy**: NPCs do not "act" on their own; they are purely data entries generated by the engine.
- **Complex AI Dialogue**: The text is templated and structured, not procedurally generated via LLM at runtime (to ensure stability and performance).

## 7. Test Plan
### Unit Tests (`commonTest`)
- Verify `RawPetition` creation and lifecycle transitions (OPEN $\rightarrow$ EXPIRED).
- Ensure `TurnTickingEngine` correctly triggers expiration on the precise turn.
- Validate that `PetitionConsequence` application updates Unrest/Standing correctly.

### Integration Tests (`jsTest`)
- Test the Inbox UI rendering with mock data in a headless browser environment (Karma/Chrome).
- Verify "Unread" badge visibility toggling when petitions are read.

### Manual Foundry Verification
- Checklist: Ensure all role-based filters work; verify that the GM can confirm offers via chat buttons.

## 8. Implementation Phasing
1. **Phase 1: Data & Core Engine**: Define `RawPetition` models, migration/initialization logic, and the core generation service (template selection + petitioner casting).
2. **Phase 2: Ticking & Expiration**: Integrate deadline tracking into `TurnTickingEngine` and implement the expiration consequence trigger.
3. **Phase 3: UI & Badging**: Build the Inbox section in the Kingdom Sheet, including role-based filtering and i18n integration. Implement the "unseen" badge logic.
4. **Phase 4: Offer System**: Implement the `km-offer-*` pattern for response confirmation and the final consequence application workflow.
