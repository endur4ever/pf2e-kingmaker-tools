# Kingmaker Companions

Drag-and-drop PF2e companion actors + kingdom roster entries for the Kingmaker Companion Guide companions. Stat blocks sourced from the **"pathfinder 2e kingmaker"** NotebookLM notebook (Companion Guide), captured 2026-06-19, for pf2e system **8.1.2**.

## Foundry compendium (drag-and-drop actors)

Shipped in the **Companions** compendium pack: `packs/kingmaker-tools-companions` (declared in `module.json`, type `Actor`). Open **Compendium Packs → Companions** and drag a companion onto the canvas or into the party.

| Actor | Level | Class / Ancestry | Notes |
|---|:--:|---|---|
| **Ekundayo** | 1 | Human Ranger | Giant-hunter; longbow + longsword |
| **Dog (Ekundayo's Riding Dog)** | 1 | Riding Dog (Monster Core) | His dog before he gains the Animal Companion feat at L2 |
| **Jubilost Narthropple** | 1 | Gnome Alchemist (Bomber) | Innate cantrip (know direction); lesser alchemist's fire |
| **Nok-Nok** | 1 | Goblin Rogue (Scoundrel) | Sneak Attack 1d6; kukri + shortbow |
| **Tristian** | 1 | Aasimar Cleric of Sarenrae | Cloistered healer; prepared divine list + focus spell |

All companions are built at their **Companion Guide level 1** stat blocks (suitable for a level 2–4 party). Sources: Ekundayo p.21, Jubilost p.32, Nok-Nok p.56, Tristian p.68.

> ⚠️ **Nok-Nok's Perception:** the Guide prints **+14** at level 1 — almost certainly a misprint carried from his higher-level block (a 1st-level Nok-Nok with Wis −1 should be ~+7). The actor uses the printed +14 with a GM note; lower it to +7 if it plays too high.

All actors are set to **alliance: party** (they read as allies in encounters), rarity **unique**, with GM-only Influence notes on the actor's GM Notes tab.

### Activating the pack
A **new** compendium pack only registers when the world (re)launches. After deploying: return to setup and relaunch the world (or restart the Foundry server), then the **Companions** pack appears under Compendium Packs.

## Kingdom roster entries

`ekundayo.import.md` (`type: companions`) adds all four to the kingdom **companion roster** in one click via the kingdom sheet → **Import from Obsidian**. After import, open each Companion Profile to track Influence (0–12)/Discovery and add personal quests.

## Files
- `README.md` — this file
- `ekundayo.md` — full Ekundayo character sheet (Level 1) + his Riding Dog + Influence subsystem
- `ekundayo.import.md` — roster-import Markdown for all four companions

## Rebuilding the pack
The pack is generated from `build-pack.mjs` in this folder, which writes the split-key ClassicLevel format directly. It needs `classic-level` (`npm i classic-level`), then:

```bash
node docs/companions/build-pack.mjs packs/kingmaker-tools-companions
```

Stat data comes from the NotebookLM Kingmaker notebook; see the `kingmaker-companion-sheets` memory for the workflow.
