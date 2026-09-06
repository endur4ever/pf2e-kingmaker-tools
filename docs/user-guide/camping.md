# Camping & Wilderness Exploration Guide (6.4.0)

This guide documents the camping, travel, cooking, and wilderness encounter systems in the Kingmaker Campaign Automation module.

---

## 1. Rations & Subsistence

Wilderness travel requires feeding the party each night. Campers who do not eat face starvation penalties during rest.

### Consuming Rations & The "Paid Tonight" Badge
- On the **Cooking** tab of the Camping sheet, the party can consume rations from inventory via the **Consume Rations** button.
- When clicked, the button spends food from the party inventory to cover all campers who are not assigned to eat a cooked meal.
- If the party has sufficient food, the action stamps the current world day into `Cooking.rationsPaidForDay`.
- Once stamped, the Camping sheet displays a green badge: **"Rations paid for tonight"** (`fa-circle-check` icon) on the Rations recipe tile and in the Eating summary bar.
- When the party rests, the nightly starvation tick (`tickNightlyStarvation`) checks `rationsAlreadyPaidFor(camping.cooking.rationsPaidForDay, currentWorldDay)`. If rations were paid in full, campers eating rations are evaluated as fed **without deducting food from inventory a second time**.
- **Short larder protection:** If the party inventory does not have enough food to cover the entire party, the stamp is not recorded, and unfed campers will be flagged during rest.
- **Day-scoped validity:** Payment is stamped by calendar day. A payment made yesterday does not pay for tonight, and breaking camp without resting simply allows the stamp to expire naturally tomorrow without requiring fragile state clearing.

---

## 2. Cooking, Favourite Meals, & Pinning

Cooking hot meals provides party-wide bonuses and character progression.

### Cooking Activities & Meal Effects
- Campers assigned to the **Cook Meal** activity roll Survival or Cooking Lore against the recipe DC.
- The **Apply Meal Effects** button posts a chat card applying the recipe's mechanical benefits to everyone who ate that meal. All meal chat cards enforce one-shot click protection to prevent double-application.

### Favourite Meal Progression
- Every character can learn a recipe as their favourite meal.
- When an assigned cook achieves a **Critical Success** on a recipe, all campers eating that meal gain progress toward learning it (`FavoriteMealProgression`).
- Accumulating **2 Critical Successes** automatically sets that recipe as the character's favourite meal.

### Pinning & Manual Selection
- In the **Favourite Meals** dialog (accessible via the `fa-utensils` button in the sheet header):
  - GMs and players can view all campers, their current meal, and their favourite meal.
  - If a favourite meal is selected manually from the dropdown, it is marked as **Pinned** (`fixedFavoriteMeal = true`), displayed with a prominent thumbtack icon (`fa-thumbtack`).
  - **Pin Protection:** Automatic progression on cooking crits will **never overwrite** a pinned favourite meal.
  - **Unpin Control:** Each pinned row includes an explicit **Unpin** button (`data-action="unpin-meal"`). Clicking this clears the pin while leaving the current favourite meal selected, allowing automatic progression on future critical successes to resume.

---

## 3. Random Encounters & The Encounter Curator

Wilderness exploration features structured encounter resolution and staging.

### Curated Encounters & Roll Tables
- The module supports both standard Foundry roll tables and the **Encounter Curator**.
- **Encounter Curator Requirements:** The curated generator requires either category weights or a **Proxy Table** configured in Camping Settings (`RegionEncounterTables`). If neither is configured, the curated generator is inert and falls back to standard table draws.
- **Claimed Hex Suppression:** The module can optionally suppress random encounters in hexes claimed by the kingdom, respecting both blanket kingdom rules and per-hex overrides.

### The Encounter Preview Dialog
When an encounter check triggers, the GM receives an interactive preview window:
- **Accept:** Records the encounter in the Travel Journal, closes the preview, and enables combat staging.
- **Reroll / Reject:** Discards the encounter. Rejecting or rerolling writes **nothing** to the travel journal or rumor board, leaving no orphaned notes.
- **Convert to Quest:** Converts the encounter lead into a kingdom quest, storing a bidirectional link between the quest and the original rumor.
- **Reload Resilience:** If the GM's browser reloads or disconnects while an encounter preview is open, the encounter manifest (`lastEncounterManifest`) is restored upon re-opening, preserving drawn creatures, positions, and staging actions.

---

## 4. Rumor Board & Wilderness Leads

The Rumor Board manages rumors and adventure seeds gathered in taverns or found on the road.

- **Aging Lifecycle:** Rumors age across world days through three states: **Fresh** → **Stale** → **Expiring**.
- **Cold Lead Warning:** When an active rumor enters the expiring phase, a whispered GM alert warns that the lead is about to go cold.
- **Pinning Rumors:** GMs can pin valuable rumors to prevent them from aging or expiring.
- **Converting Rumors:** Rumors can be converted into active kingdom quests or pinned as exploration hooks on specific hexes of the kingdom map.

---

## 5. Watches, Resting, & Travel

### Setting Watches
- The party can configure between 2 and 4 watch periods per night.
- Sentry perception modifiers, darkvision, and scent traits are surfaced in the watch grid.
- Watch coverage validation alerts the GM if any period is left unguarded.

### Travel Mode, Forced March, & Hexploration
- **Travel Mode:** Calculates movement pace, terrain travel modifiers, and hexploration budget per day.
- **Forced March:** Pushing the party beyond normal traveling hours accumulates forced march days. These accumulated days persist until the party rests, preventing accidental checkbox unchecking from erasing travel exhaustion.
- **Travel Journal:** Logs route legs, hex arrivals, and wilderness discoveries as the party journeys through the Stolen Lands.

### Resting Pipeline
- A full night's rest restores hit points, resets spell slots, removes the fatigued condition, and resets daily abilities.
- **Clock Pre-compensation:** The rest routine pre-compensates exploration and adventuring time counters before advancing the world clock, ensuring that characters do not wake up already fatigued by the time step that restored them.

---

## 6. Player Permissions vs. GM-Only Controls

To ensure smooth collaborative play without accidental data loss during multi-user sessions:

### GM-Only Controls
- **Current Region** selection dropdown.
- **Travel Mode** checkbox.
- **Forced March** checkbox.
- **Move Party Token on Travel** checkbox.
- These controls render only on the GM's sheet. Submissions from player sheets never overwrite or clear these values.

### Player-Accessible Controls
- Choosing personal camp activities (e.g., Stand Watch, Cook Meal, Learn from Companion, Relax).
- Selecting personal meal choices from known recipes or rations.
- Rolling assigned activity checks and degree-of-success selections.
