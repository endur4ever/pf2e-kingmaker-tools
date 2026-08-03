# World Anvil Integration Feasibility

**Date:** 2026-06-04
**Author:** researcher (kanban spike)
**Status:** Spike — no code, feasibility only
**Related TODO:** `docs/todo.md` — "same as obsidian look at integration with world anvil"

---

## 1. Question

Can (and should) the `pf2e-kingmaker-tools` Foundry VTT module integrate with [World Anvil](https://www.worldanvil.com) to let GMs keep campaign notes, kingdom records, NPC rosters, session logs, and worldbuilding lore in a World Anvil world that reads from and/or writes to Foundry?

---

## 2. Context: Module Data That Could Be Exported

The module manages rich, structured campaign data that lives in Foundry's client-side Kotlin → JS runtime. The most World Anvil-relevant entities are:

| Domain | Source (Kotlin-side) | What it contains |
|---|---|---|
| **Kingdom sheet** | `KingdomSheet.kt`, `KingdomSheetData.kt`, `KingdomSheetDataModel.kt` | Control DC, Resource Dice, Size, Skills, Ruins, Reputation, notes per turn |
| **Settlements** | `Settlement.kt`, `RawSettlement.kt`, dialogs | Name, level, type, population roster (NPCs with names), structures, urban grid |
| **Population roster** | Recent work on `PopulationPersistenceTest.kt` | Per-settlement NPC entries (name, gender, culture-backed name generation) |
| **Leaders** | `Leader.kt`, `RawCharacter.kt`, `ConfigureLeaderSkills.kt` | 8 roles, ability modifiers, vacancy penalties |
| **Kingdom events** | `data/events/`, `event-browser.hbs`, `RawEvent.kt` | Random events, XP awards, ongoing effects |
| **Quests** | `RawQuest.kt` | Type, target, rewards, completion notes |
| **Camping** | `CampingSheet.kt`, `CampingActivityData.kt` | Activities, meals, recipes, weather, effects, provisions |
| **Armies** | `ArmyCompendiumEntries.kt`, army data packs | Tactics, stats, modifiers, morale |
| **Structures** | `packs/kingmaker-tools-structures` compendium | Structure data (effects, requirements, group limits) |
| **Journal entries / compendium** | `packs/kingmaker-tools-journals` compendium | Pre-written module journal content (lore, rules, hex descriptions) |
| **Campaign clock** | Planned feature | Chapter deadlines, pressure clocks, event timers |

This data is *not* static — it mutates every kingdom turn. Anything that syncs to World Anvil must be re-exported or patched on change.

---

## 3. Existing Integration Landscape

### 3.1. World Anvil Integration (foundryvtt/world-anvil)

- **What it is:** An official Foundry module that imports article content from World Anvil into Foundry's journal system.
- **How it works:** GM configures their World Anvil API key in the module settings. The module fetches articles from WA and creates/updates corresponding Journal Entries in Foundry. A "WA Sync" button on each imported journal entry refreshes content from WA.
- **Direction:** World Anvil → Foundry (import only).
- **Features:** Article import, secret section support (v1.3.0+), category/folder mapping, periodic sync.
- **Source:** <https://github.com/foundryvtt/world-anvil>
- **Verdict for us:** This is the *opposite* direction of what we need. It brings WA content *into* Foundry. We want to push Foundry kingdom data *out* to WA. However, the module proves the API connection pattern and could potentially be extended or forked.

### 3.2. World Anvil Integration — Actors (foundryvtt/world-anvil-actors)

- **What it is:** Companion module that auto-creates Foundry Actors when importing character articles from World Anvil.
- **Direction:** World Anvil → Foundry (import only).
- **Source:** <https://foundryvtt.com/packages/world-anvil-actors>
- **Verdict for us:** Same direction as 3.1. Not directly useful for export.

### 3.3. Tillerz/world-anvil-foundryvtt-v12-fix

- **What it is:** A community fork of the official module with Foundry v12 compatibility fixes.
- **Verdict for us:** Evidence that the official module has maintenance gaps. Any integration we build should not depend on a third-party fork.

---

## 4. World Anvil API Analysis

### 4.1. API Versions

| Version | Name | Status | Capabilities |
|---|---|---|---|
| v1 | Aragorn | Legacy | Read-only (articles, worlds, categories) |
| v2 | Boromir | Current | Full CRUD: GET, POST, PUT, PATCH, DELETE on articles, categories, blocks, templates, worlds |

### 4.2. Boromir API v2 Capabilities (Relevant to Us)

- **Articles:** Full CRUD — create, read, update, delete articles with BBCode/HTML content
- **Categories:** Create, read, update, delete categories (hierarchical)
- **Blocks:** Create and manage content blocks within articles
- **Templates:** Manage article templates
- **Worlds:** Read world metadata
- **Authentication:** API key + Auth token (per-world)
- **Content format:** BBCode (World Anvil's native format) or HTML

### 4.3. API Access Requirements

- **Cost:** API access is a separate paid add-on at **$34/month** (billed monthly). This is on top of Guild membership ($4.50+/mo).
- **Free tier (Freeman):** No API access. 2 worlds, 42 articles, 100 MB storage.
- **Guild tier:** No API access by default — must purchase the $34/mo API add-on.
- **Implication:** Any integration that writes to World Anvil requires the GM to spend ~$34/month. This is a significant barrier.

### 4.4. Rate Limits

World Anvil does not publish explicit rate limits, but community reports suggest:
- Generous for read operations
- Write operations may be throttled during peak usage
- Bulk operations (e.g., exporting 50+ NPCs) should include delays between requests

---

## 5. Integration Approaches

### Approach A: "Leverage Existing Module" — Extend the official World Anvil Integration module for bidirectional sync

**Description:** Fork or extend the existing `foundryvtt/world-anvil` module to add export capability. The module already handles API authentication and article fetching; add a reverse path that pushes Foundry data to World Anvil as articles.

**How it works:**
1. Reuse the existing API key configuration from the WA Integration module.
2. Add an "Export to World Anvil" button to the Kingdom Sheet.
3. Serialize kingdom data to BBCode/HTML and POST/PUT to the WA API as articles.
4. Map Foundry entities to WA categories (e.g., "Kingdom", "Settlements", "NPCs", "Events").

**Effort:** Medium-high. Requires:
- Understanding and modifying the existing module's codebase.
- BBCode/HTML serialization of kingdom data.
- Category management on first sync.
- Article update logic (create vs. update existing).
- Error handling for API failures.

**Pros:**
- Builds on proven authentication/API pattern.
- Reuses existing user configuration (API key).
- Community could benefit from the enhancement.

**Cons:**
- The official module is maintained by FoundryVTT org — PRs may have long review cycles.
- Forking creates maintenance burden (must track upstream).
- Still requires $34/mo API add-on from the user.
- BBCode serialization is non-trivial for structured data.

**Best for:** Long-term community contribution, if the module maintainer accepts the feature.

---

### Approach B: "Companion Module" — Build a separate pf2e-kingmaker-tools World Anvil export module

**Description:** A standalone Foundry module (separate from the official WA Integration) that focuses exclusively on exporting Kingmaker campaign data to World Anvil.

**How it works:**
1. GM installs both the official WA Integration (for API auth) and our companion module.
2. Companion module has its own API key configuration (or reads from the official module's settings).
3. Provides export UI on the Kingdom Sheet: "Push to World Anvil".
4. Creates/updates a structured set of WA articles mirroring the kingdom state.

**Effort:** High. Requires:
- A new Foundry module (module.json, build pipeline, Foundry module conventions).
- Full API client implementation in JS/TS.
- BBCode/HTML template system for each entity type.
- Category/folder management.
- Settings UI for API key, world ID, export scope.
- Idempotent sync (track which Foundry entities map to which WA article IDs).

**Pros:**
- Clean separation of concerns — doesn't modify the official module.
- Can be tailored specifically to Kingmaker data structures.
- Could be extended to support other campaign types.

**Cons:**
- Highest effort of all approaches.
- Requires $34/mo API add-on.
- Two-module installation for the user.
- Maintenance burden of a separate module.
- Must keep up with Foundry version updates.

**Best for:** If World Anvil integration becomes a core feature request from users.

---

### Approach C: "Export to BBCode/HTML File" — Generate World Anvil-compatible content files for manual import

**Description:** Add an "Export for World Anvil" button that generates BBCode or HTML files matching World Anvil's article format. GM manually imports these into World Anvil via the web UI.

**How it works:**
1. GM clicks "Export for World Anvil" on the Kingdom Sheet.
2. Module generates a ZIP of `.txt` files (one per entity) with BBCode content.
3. Each file is named and structured for easy copy-paste into World Anvil's article editor.
4. Optionally includes a manifest file mapping articles to categories.

**Effort:** Low-medium. Requires:
- BBCode template system for each entity type.
- File generation and download (JS Blob/URL).
- No API key needed.

**Pros:**
- Zero API cost — works with free World Anvil accounts.
- No API authentication or network calls.
- Simple to implement and maintain.
- GM controls when and what gets imported.
- Works with any hosting setup.

**Cons:**
- Manual import step — GM must copy-paste or use WA's import feature.
- No automatic sync — re-export required after each kingdom turn.
- No article linking (WA's `[[article links]]` won't auto-resolve on import).
- BBCode format may not perfectly match WA's expectations.

**Best for:** Low-effort, no-cost option that works for all users regardless of WA subscription level.

---

### Approach D: "Webhook / Companion Script" — External script reads Foundry data, writes to World Anvil via API

**Description:** A standalone script (Python, Node, or shell) that reads Foundry's world data (via the Foundry REST API or by parsing exported JSON) and creates/updates World Anvil articles via the Boromir API.

**How it works:**
1. GM runs a script (manually or via cron) that:
   - Reads Foundry world data via the Foundry VTT REST API (if enabled) or by parsing exported JSON.
   - Generates BBCode/HTML content for each entity.
   - POSTs/PUTs articles to World Anvil via the Boromir API.
2. Optionally runs as a Foundry module hook (server-side) that triggers on data changes.

**Effort:** Medium. Requires:
- A script (Python recommended — `pywaclient` on PyPI wraps the Boromir API).
- Foundry REST API access (requires a world-level API key or admin access).
- BBCode templating.
- Category management logic.

**Pros:**
- Decoupled from Foundry client/server architecture.
- Full control over output format and sync logic.
- Can be run on any schedule.
- No browser/CORS issues.
- Can be version-controlled alongside the module.

**Cons:**
- Requires Foundry REST API to be enabled (not default).
- Or requires file-system access to Foundry data directory.
- External dependency — GM must install and run the script.
- Requires $34/mo API add-on.
- No live sync unless run frequently.

**Best for:** Tech-savvy GMs who want full control and don't mind running a script.

---

### Approach E: "World Anvil URI Protocol" — Deep-link from Foundry to World Anvil

**Description:** Use World Anvil's web URLs to create "Open in World Anvil" links from Foundry. If articles already exist in WA, the module generates direct links to them.

**How it works:**
1. Module renders `https://www.worldanvil.com/w/{world-slug}/a/{article-slug}` links in the Kingdom Sheet UI.
2. GM clicks a link → browser opens the corresponding WA article.
3. Optionally, use WA's "Create Article" URL pattern to link to a pre-filled new article form.

**Effort:** Very low. Requires:
- URL generation in Kotlin/JS.
- A settings field for the WA world slug.
- Optional: article slug tracking if articles already exist.

**Pros:**
- Zero dependencies.
- Zero cost.
- Works with any WA subscription level.
- Instant — no sync, no export step.
- Can deep-link to specific articles.

**Cons:**
- One-way, read-only from Foundry's perspective.
- No automatic content sync.
- GM must manually create articles in WA first (or use another approach to create them).
- No way to push data from Foundry to WA.

**Best for:** Quick navigation — "open my campaign wiki" links from Foundry.

---

## 6. Comparison Matrix

| Criterion | A: Extend Existing Module | B: Companion Module | C: Export to File | D: Companion Script | E: URI Links |
|---|---|---|---|---|---|
| **Effort** | Medium-High | High | Low-Medium | Medium | Very Low |
| **Direction** | Bidirectional | One-way (export) | One-way (export) | One-way (export) | Navigation only |
| **Data scope** | All structured data | All structured data | All structured data | All structured data | Navigation only |
| **WA API required** | Yes ($34/mo) | Yes ($34/mo) | No | Yes ($34/mo) | No |
| **WA subscription needed** | Guild + API add-on | Guild + API add-on | Any (free works) | Guild + API add-on | Any |
| **Server FS access** | No | No | No | Yes (or REST API) | No |
| **Works on The Forge** | Yes | Yes | Yes | Maybe (REST API) | Yes |
| **Per-entity articles** | Yes | Yes | Yes | Yes | No |
| **Live sync** | Near-real-time | Near-real-time | No (manual import) | Scheduled | No |
| **GM friction** | Low | Medium | Medium | Medium-High | Very Low |
| **Maintenance burden** | Medium (upstream tracking) | High (separate module) | Low | Medium | Very Low |

---

## 7. Risks

1. **API cost barrier.** The $34/mo API add-on is a significant expense on top of Guild membership. This limits the addressable user base to GMs who already pay for API access or are willing to add it. Any approach requiring the API should be optional, not core.

2. **API stability.** World Anvil's Boromir API v2 was released in August 2023. While it supports full CRUD, it is still relatively new. Breaking changes to the API could break the integration. The `pywaclient` Python library and the official Foundry module are the only known consumers.

3. **BBCode complexity.** World Anvil uses BBCode as its native content format. Generating well-formed BBCode from Kotlin/JS data structures is error-prone. HTML is also accepted but may not support all WA features (e.g., secret blocks, interactive maps).

4. **Foundry version coupling.** The module targets Foundry v14. Any integration that depends on Foundry JS APIs must be kept in sync with Foundry updates.

5. **Data model volatility.** The kingdom data model is actively evolving (population rosters were just added). An export integration must be resilient to schema changes or versioned.

6. **User environment diversity.** GMs run Foundry on Windows, macOS, Linux, The Forge, Molten, self-hosted, Docker. Any approach requiring file-system access or localhost networking excludes a large fraction of users.

7. **Scope creep.** "Export to World Anvil" can easily expand to "full bidirectional sync with conflict resolution." This spike explicitly recommends against that scope.

8. **Maintenance of a separate module.** If we build Approach B (companion module), it becomes a separate project with its own release cycle, compatibility matrix, and support burden. This is a significant long-term commitment.

---

## 8. Recommendation

**Do not build a dedicated World Anvil integration at this time.** The cost barrier ($34/mo API add-on), maintenance burden, and relatively niche use case do not justify the effort for the current stage of the module.

### Short term (now):
- **Approach E (URI Links)** is trivial to add and costs nothing. Add `worldanvil.com` links to the Kingdom Sheet for quick navigation to the GM's campaign wiki. Effort: ~0.5 days.
- **Approach C (Export to File)** is the best low-cost option. Generate BBCode/HTML files that the GM can manually import into World Anvil. This works with free WA accounts and requires no API key. Effort: ~1-2 days.

### Medium term (if demand exists):
- **Approach A (Extend Existing Module)** — contribute export functionality to the official `foundryvtt/world-anvil` module via PR. This benefits the entire Foundry community and avoids maintaining a separate module. Only pursue if the module maintainer is receptive. Effort: ~3-5 days for a PR-quality contribution.
- **Approach D (Companion Script)** — ship an optional Python script in the repo for tech-savvy GMs who want automated sync and already pay for the API add-on. Effort: ~2-3 days.

### Do NOT pursue:
- **Approach B (Companion Module)** — the maintenance burden of a separate Foundry module is not justified unless World Anvil integration becomes a top-3 feature request from users.
- **Bidirectional sync** — the complexity is not justified. Foundry is the source of truth for campaign state; World Anvil is a read-only consumption layer for worldbuilding lore.

### Rationale:
The module's value is in automating Kingmaker mechanics inside Foundry. World Anvil integration is a "nice to have" for GMs who maintain a campaign wiki, which is a separate concern. The existing official module (foundryvtt/world-anvil) already handles the WA → Foundry direction. The GM can manually create articles in World Anvil for kingdom lore, NPC profiles, and session notes, and use the official module to reference them in Foundry.

If the GM wants structured data (settlements, NPCs, events) pushed to World Anvil, the file export approach (C) is the most practical starting point. It requires no API subscription, works for all users, and can be enhanced later if demand justifies the API cost.

---

## 9. What Would Approach C Look Like (Rough Scope)

For estimation purposes only — not a plan:

1. Add `KingdomWorldAnvilExporter.kt` — serializes `KingdomSheetData` to BBCode:
   - Kingdom stats (level, size, control DC, RP, commodities)
   - Settlements table (name, level, population, structures)
   - Leaders table (role, name, modifiers, vacancy status)
   - Active events list
   - Army summary
   - NPC roster per settlement

2. Add "Export for World Anvil" button to `KingdomSheet.kt`.

3. On click: generate a ZIP of `.txt` files (one per entity) with BBCode content, trigger browser download.

4. Add module settings for:
   - Export scope (kingdom only, kingdom + settlements, full)
   - BBCode vs HTML output format

Estimated effort: 1-2 days including tests.

---

## 10. References

- World Anvil API v2 (Boromir): <https://www.worldanvil.com/api/external/boromir/documentation>
- World Anvil API v2 Swagger: <https://www.worldanvil.com/api/external/boromir/swagger-documentation>
- World Anvil API v1 (Aragorn): <https://www.worldanvil.com/api/aragorn/documentation>
- World Anvil Pricing: <https://www.worldanvil.com/pricing>
- World Anvil Foundry Module: <https://github.com/foundryvtt/world-anvil>
- World Anvil Foundry Module (Actors): <https://foundryvtt.com/packages/world-anvil-actors>
- World Anvil Foundry Integration Guide: <https://www.worldanvil.com/learn/rpg/foundry-integration>
- SoulLink Python API Client: <https://gitlab.com/SoulLink/world-anvil-api-client>
- pywaclient (PyPI): <https://pypi.org/project/pywaclient/>
- World Anvil Free Tier Changes (2024): <https://blog.worldanvil.com/announcements/update-free-account-changes/>
- World Anvil Feature List: <https://www.worldanvil.com/learn/account/list-features>
- Module data model: `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/`
