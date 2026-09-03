# New Subsystems Overview (June–August 2026)

This guide provides an overview of the major subsystems implemented during the June, July and August 2026 feature waves for the Kingmaker Campaign Automation module.

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

## August 2026 Wave

Every mechanical effect below is a **GM-confirmed offer**: the module detects, proposes and
whispers, and nothing is applied to the kingdom or the party until a GM clicks. Where a feature is
deliberately incomplete, this guide says so rather than implying it works.

### Influence & Research Trackers
Runs the PF2e Influence and Research subsystems for named NPCs and long projects. Track influence
points against thresholds, record checks by degree of success, and let discoveries, skills and
threshold effects stay hidden until you reveal them — players only ever *receive* what has been
revealed, so nothing leaks through the sheet. Crossing a threshold whispers a Grant / Convert to
Quest / Dismiss card. Encounters and projects can be pasted in as JSON.

Deliberately manual: you enter each check's degree of success; auto-rolling is not implemented.
Elite/Weak adjustments are recorded on a creature but not applied.

### Faction Agendas
NPC factions pursue goals of their own. Each faction gets a progress clock and an archetype
(aggressive, mercantile, fey, political, monster) that weights which move it takes: expanding,
sabotaging a rival, courting an ally, raising an army, or sending envoys to the party. One
whispered digest per turn lists what everyone did; standing shifts, war threats and quests are all
offers. The public gazette records *what* happened, never the weights behind it.

### NPC Memory Ledger
Named residents remember what the kingdom did to them. Opt an NPC in with **Track memory** (capped
at ten kingdom-wide), and each turn compares the last two turn records — unrest spiking, ruin
clearing, a caravan raided, war pressure lifting — against seventeen rules that move attitude by
occupation. Grudges decay toward indifference unless renewed. Crossing an attitude band whispers a
card offering an encounter, a quest, or a scene note.

GM-only: players see no attitude score and no memory log anywhere.

### Deeds Chronicle
Auto-detects twenty-one kingdom achievements from standing state — first settlement, roads to the
capital, regions claimed, size and level milestones, recovery from ruin or unrest, trade and war
firsts — and offers their milestone XP in one whispered digest per turn, with **Dismiss all** for a
kingdom adopted mid-campaign. Awarded deeds are dated in a read-only **Chronicle** on Session Prep
and announced in the gazette; declined ones are not.

### Encounter Stager
Turns a rolled combat encounter into a fought one. Curate creatures (a table result that points at
a bestiary actor seeds itself), set the opening distance and whether they arrive hidden, then Stage:
tokens are placed in a ring around the party at that distance, added to combat with initiative
rolled, and the existing combat-track hook supplies the music. The summary whisper carries an Undo
that removes exactly what was spawned. Queued encounters on a hex can be curated and staged from
Session Prep.

Deliberately out of scope: no wall or collision awareness — the ring is geometric, so nudge a token
if one lands badly.

### Party XP Ledger
A running record of party XP on the Party tab, with per-source totals and a reconciliation against
what a character actually holds. Drift is expected and never corrected: combat XP and hand edits
never pass through the ledger.

Beats are recorded automatically as they happen — a site cleared on a hex, a quest completed, an
expedition resolved — and End Turn whispers one digest listing everything unanswered, with
**Confirm** / **Dismiss** per row and for the whole card. Each row's amount is editable before you
confirm, and the ledger records what was actually granted rather than what was proposed. Defaults
follow PF2e accomplishment XP: 10 for a hex, 10/30/80 for a minor/moderate/major site, 30 or 80
for a quest, 30 for an expedition. You can still add rows by hand for anything the module cannot
see.

### Groundwork, not yet usable
Two subsystems have their data model, schema, storage and migrations in place but no content or
tick yet, so nothing surfaces in play: **Settlement Life Events** (three of ten templates ship; the
per-turn cap and probability curve are undecided) and the **Petition Inbox** (the catalog is
intentionally empty until its forty templates are written).

## Recently corrected

Two long-standing behaviours reported an effect the module never applied. Both are now fixed:

- **RP-to-XP conversion.** End Turn announced "XP Awarded: N" and recorded it in turn history and
  the analytics chart, but never added it to the kingdom's XP. End Turn now grants it, using the
  same calculation the kingdom sheet's converter used — including the Vance & Kerenshara XP
  variant, which the engine's own copy ignored. The sheet's separate **Convert RP to XP** button
  has been removed: with the automatic grant in place it could only ever double-grant.
- **The Liquidate Resources penalty.** Liquidating announces that next turn's Resource Dice are
  reduced by four; that reduction now applies to the dice actually rolled. Previously it was
  subtracted from a value the turn tick had already zeroed, so it never landed in any
  configuration.

---

*Note: For technical details on implementation and integration, see the [Official Module Integration](./../README.md#official-module-integration) section of the README.*

### Settlement life events

Each End Turn, every settlement has a chance — rising with its level and population, never a
certainty — to produce one line of town life: a market day, a midwinter feast, a guild theft.
At most two fire kingdom-wide per turn, and a busy capital cannot take both slots every month.
Residents are cast from the settlement's population roster by occupation, so the same brewer
hosts the feast and the same rat-catcher gets accused.

The Turn Wizard's preview shows the exact lines End Turn will write — the draw is seeded from the kingdom, the turn and the settlement, never the clock — and the line is written into the turn gazette whether or not anyone acts on it. Some events also
carry a small mechanical hook (±1 Unrest, +1 RP, create a quest, spread a rumour); those arrive
on **one whispered digest card** per turn with an apply and a dismiss button per row. Nothing
applies on its own. The catalog lives in `data/settlement-life-events/` and ships with ten
templates (three with finished prose, seven with placeholder lines to rewrite); adding one is a
JSON file plus its two gazette strings, and the build fails if either string is missing in any
locale.

### Rival charter parties

A competing adventuring band explores the same hex map off-screen. Each End Turn it chooses a prize
— a landmark, an uncleared lair, explored ground you have not claimed, or plain unexplored hexes,
in that order of preference — and walks toward it at its pace; when it gets there first, that is a
race you lost. Claiming a hex takes it off the board for them, which is how you win one.

Everything the band does that would change the world is a GM-confirmed offer on **one whispered
digest per turn**: it reached a prize (let them have it, race them with a quest, confront them, or
just narrate); its aggression crossed the threshold (raise a war threat, queue an encounter at its
hex with a level budget relative to the party, or dismiss); it picked a new objective (plant a
rumour the players can hear in play, or keep it quiet); the party token is standing in its hex; or a
campaign clock has expired and the charter changes hands. The turn gazette carries a public
headline per moving band. The Trade Agreements board shows where each band was last seen, what it
is making for, the ETA and how many races you have lost; the GM edits the dials there. At most two
bands are in the field at once.
