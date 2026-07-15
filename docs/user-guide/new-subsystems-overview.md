# New Subsystems Overview (June/July 202im)

This guide provides an overview of the major subsystems implemented during the June and July 2026 feature waves for the Kingmaker Campaign Automation module.

## Core Systems

### Turn Wizard
A structured, step-by-step interface to guide GMs through a kingdom turn. It includes pre-turn checklists, resource previews (RP, commodities, storage), and activity cap calculations based on your chosen leadership/civic profile.

### Session Prep & Recap
Allows GMs to prepare for upcoming sessions by aggregating open quests, active clocks, and recent events. It also features a "Narrative Generator" that produces a player-facing recap of the previous session's events, which can be exported directly to your campaign journal.

### Factions & Diplomacy
A living diplomacy system where kingdom actions, event outcomes, and conquests influence the standing with various factions (e.g., Pitax, Brevoy houses). This affects trade, quest availability, and potential war threats.

### War-Pressure Board & Battle Resolver
Tracks escalating military threats and army presence using a "War Pressure" mechanic. It includes a full battle resolver for managing encounters between kingdom armies and invaders, automating troop losses, XP gain, and the consequences of victory or defeat.

### Caravan Economy
Automates trade routes and logistics through your hexes. Caravans carry commodities between settlements and groups, subject to travel time, terrain, weather, and potential raids based on your faction relations and regional stability.

## Player & World Systems

### Companion Expeditions & Influence
Manages the relationship between your party and their companions. Track how much influence players have over companions through camping activities, and manage "Companion Expeditions" that occur during downtime.

### Gazette & Event Log
A historical record of all significant kingdom events, troop movements, and political shifts. The system automatically generates chronicle entries (Gazette) that can be posted to the campaign'/player journals.

### Analytics Dashboard
A GM-only, read-only dashboard providing high-level insights into your campaign's trends. Includes line charts for unrest, RP consumption, treasury levels, and population growth, allowing you to spot stagnation or rapid expansion at a glance.

### Calendar & Season Integration
Integrates with the standard Foundry VTT calendar (and custom seasonal modules) to automate time-based triggers, such as seasonal weather changes, harvest times, and annual kingdom tax collections.

### Homebrew Profile System
A flexible system allowing GMs to switch between "Rules As Written" (RAW) and various homebrew rule sets (e.g., Vance & Kerenshara style). This affects everything from taxation rates to the severity of famine or war threats.

### Player-Facing Views
Provides players with permission-filtered, read-only access to essential kingdom information (like territory boundaries, known settlements, and active threats) without leaking sensitive GM-only data like secret war plans or troop counts.

---

*Note: For technical details on implementation and integration, see the [Official Module Integration](./../README.md#official-module-integration) section of the README.*
