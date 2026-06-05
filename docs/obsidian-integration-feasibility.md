# Obsidian Integration Feasibility

**Date:** 2026-06-04
**Author:** researcher (kanban spike)
**Status:** Spike — no code, feasibility only
**Related TODO:** `docs/todo.md` — "should we think about having obsidian tie into this module"

---

## 1. Question

Can (and should) the `pf2e-kingmaker-tools` Foundry VTT module integrate with [Obsidian.md](https://obsidian.md) to let GMs keep campaign notes, kingdom records, NPC rosters, session logs, and rules reference in an Obsidian vault that reads from and/or writes to Foundry?

---

## 2. Context: Module Data That Could Be Exported

The module manages rich, structured campaign data that lives in Foundry's client-side Kotlin → JS runtime.  The most Obsidian-relevant entities are:

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

This data is *not* static — it mutates every kingdom turn. Anything that syncs to Obsidian must be re-exported or patched on change.

---

## 3. Existing Integration Landscape

### 3.1. Obsidian Bridge (foundryvtt-obsidian-bridge)

- **What it is:** A Foundry module for *bidirectional* sync between Obsidian vaults and Foundry Journal Entries only.
- **How it works:** Journal sidebar buttons → "Import from Obsidian" / "Export to Obsidian". Converts Obsidian `[[links]]` ↔ Foundry UUIDs. Preserves folder structure. Supports callouts.
- **Limitation:** Journals only. No actors, no structured kingdom data, no settlements, no armies.
- **Source:** <https://github.com/SoSly/foundryvtt-obsidian-bridge>
- **Verdict for us:** Not useful as-is. Would only export/import the module's journal compendium content.

### 3.2. Lava Flow

- **What it is:** Import-only. Reads an Obsidian vault folder and creates Foundry Journal Entries.
- **Features:** Index journal, backlinks section, image import, folder-to-multi-page journal entries.
- **Limitation:** Journals only. One-way.
- **Source:** <https://github.com/Praxxian/lava-flow>
- **Verdict for us:** Not relevant — we want to push campaign *data out* of Foundry, not import journal content in.

### 3.3. fvtt-export-markdown (Farling)

- **What it is:** Exports a journal *tree* (folder + entries) as a ZIP of Markdown files with YAML frontmatter.
- **How it works:** Right-click journal folder → "Export to Markdown". Produces `.md` files with path structure, handles images.
- **Limitation:** Journals only. No structured data.
- **Source:** <https://github.com/farling42/fvtt-export-markdown>
- **Verdict for us:** Same limitation — journal-only.

### 3.4. obsidian-import-foundry (Farling, Obsidian-side)

- **What it is:** Obsidian community plugin that reads a Foundry `journal.db` file (from the server data directory) and converts each journal entry into an Obsidian note.
- **How it works:** Requires direct file-system access to the Foundry server's `journal.db`. No live sync.
- **Limitation:** Journals only. Obsidian-side plugin. File-system coupling.
- **Source:** <https://github.com/farling42/obsidian-import-foundry>
- **Verdict for us:** Interesting pattern (parse Foundry DB directly) but limited to journals.

### 3.5. md-to-journal

- **What it is:** Imports markdown files into Foundry journals.
- **Limitation:** Journal import only. Not export.
- **Verdict for us:** Not relevant for our use case.

### 3.6. Journal Sync (foundry-vtt-journal-sync)

- **What it is:** Stores journal entries as markdown files on the Foundry server file system. Two-way sync.
- **Limitation:** Journals only. Server-side storage.
- **Verdict for us:** Same pattern, not our data.

---

## 4. Integration Approaches

### Approach A: "Journal Export" — Use existing tools, push kingdom state into Journal Entries

**Description:** Build kingdom/settlement/leader state summaries as plain Journal Entries (HTML or Markdown) inside Foundry, then let existing tools (Obsidian Bridge, fvtt-export-markdown, obsidian-import-foundry) carry them into Obsidian.

**How it works:**
1. Add a button to the Kingdom Sheet: "Export kingdom summary to Journal".
2. Render current kingdom state (settlements, leaders, armies, events, next-turn checklist) as HTML into a Journal Entry.
3. User uses existing Obsidian Bridge or fvtt-export-markdown to get that into their vault.

**Effort:** Low-medium. Requires:
- A `KingdomSummaryJournalExporter` Kotlin class that serializes `RawKingdom` data to HTML.
- A Journal API call (`JournalEntry.create(...)` via Foundry JS API).
- A button in `KingdomSheet.kt`.
- A migration/journal entry type for idempotency.

**Pros:**
- No new Obsidian dependency.
- Leverages mature existing export path.
- Minimal maintenance.
- Works offline — Foundry server doesn't need to know about Obsidian.
- Player-safe — journals support Foundry permissions.

**Cons:**
- One-way only.
- Data is static snapshot, not live-linked.
- Markdown quality depends on HTML-to-markdown conversion in the export tool.
- No per-settlement or per-NPC notes — it's a summary page.
- GM must remember to re-export.

**Best for:** Session-prep notes, kingdom status snapshots for campaign wikis.

---

### Approach B: "Webhook / File Drop" — Write markdown files to the server's Obsidian vault directory

**Description:** Add a Foundry module setting for an "Obsidian vault path". On button press (or Hooks.on kingdom turn), serialize campaign data to Markdown files and write them to that path on the server.

**How it works:**
1. GM sets their local/server Obsidian vault path in module settings.
2. On trigger, module serializes kingdom data to a set of Markdown files:
   - `Kingdom/Kingdom-Overview.md`
   - `Kingdom/Settlements/Settlement-Name.md` (one per settlement, with NPC roster)
   - `Kingdom/Leaders.md`
   - `Kingdom/Events/Event-Name.md`
   - `Kingdom/Turn-Log/Turn-NNNN.md`
3. Files are written via `FilePicker.upload()` (browser upload to server) or via a small companion server endpoint.
4. Obsidian picks up the files instantly (local file system).

**Effort:** Medium-high. Requires:
- File serialization logic (HTML → Markdown conversion in Kotlin/JS).
- Either: a) browser-based `FilePicker` uploads (one at a time, awkward), or b) a small server-side companion script that receives POST data and writes files.
- Obsidian vault frontmatter standard (YAML with tags like `kingdom`, `settlement`, `npc`).
- Obsidian `[[link]]` generation between notes.
- A settings UI for vault path and export scope.
- Conflict handling (overwrite vs. merge).

**Pros:**
- One-way, file-based, no Obsidian plugin needed.
- Full control over content structure, frontmatter, linking.
- Per-entity notes — each settlement is a note, linked from kingdom overview.
- Works with any Obsidian setup (local, Sync, Remotely Save).
- Files are version-controllable — `git add vault/` tracks campaign history.

**Cons:**
- Requires server file system access (doesn't work on The Forge / cloud hosting without SSH/SFTP).
- Two-host problem: Foundry server and Obsidian vault are often on different machines.
- No live sync — snapshot only.
- File upload from browser is clunky (one file at a time unless using a ZIP approach).
- HTML→Markdown conversion quality can be uneven.

**Best for:** Self-hosted GMs who keep their Obsidian vault on the same machine/network as the Foundry server.

---

### Approach C: "Obsidian Local REST API" — Push data to Obsidian via HTTP

**Description:** Use the community [obsidian-local-rest-api](https://github.com/coddingtonbear/obsidian-local-rest-api) plugin, which exposes a REST API on `https://127.0.0.1:27124` with full CRUD on vault files.

**How it works:**
1. GM installs the "Local REST API" community plugin in Obsidian.
2. Foundry module POSTs to `https://127.0.0.1:27124/{vault}/` with Markdown content.
3. Create/update/replace notes directly in the vault.

**Effort:** High. Requires:
- HTTPS POST from Foundry client JS to localhost (CORS + self-signed cert issues).
- GM must run Foundry client and Obsidian on the same machine (localhost only).
- API key management and certificate trust.
- Obsidian plugin dependency for the user.

**Pros:**
- Direct file create/update in Obsidian.
- Can update individual notes (patch operations via PUT).
- Clean Markdown with full frontmatter support.
- No file system coupling beyond localhost.

**Cons:**
- **Breaks the Foundry threat model:** web-based Foundry clients (The Forge, Molten) cannot reach localhost.
- Requires Obsidian plugin installation.
- Self-signed TLS cert must be trusted by the browser.
- CORS headers must be configured.
- Only works when GM runs both apps on the same machine.
- High support burden.

**Verdict:** Not recommended. The localhost-only constraint makes this impractical for most users.

---

### Approach D: "Obsidian URI Protocol" — Deep-link from Foundry to Obsidian notes

**Description:** Use the `obsidian://` URI scheme to open specific notes from Foundry. Foundry module generates `obsidian://open?vault=MyVault&file=Kingdom/Overview` links that the GM clicks.

**How it works:**
1. Module renders `obsidian://` links in the Kingdom Sheet UI.
2. GM clicks a link → Obsidian opens the corresponding note.
3. Optionally, use `obsidian://new?vault=MyVault&file=Path&content=...` to create a note with pre-filled content.

**Effort:** Low. Requires:
- URI generation in Kotlin/JS.
- A settings field for vault name.
- Optional: content encoding for `obsidian://new`.

**Pros:**
- Zero dependencies on both sides.
- Works cross-platform (Windows, macOS, Linux).
- Instant — no sync, no export step.
- Can deep-link to specific notes.

**Cons:**
- One-way, read-only from Foundry's perspective.
- `obsidian://new` has URL length limits — can't push large content.
- No automatic sync — GM must click each link.
- URI scheme handling varies by OS/browser.
- No way to read data back from Obsidian.

**Best for:** Quick navigation — "open my campaign wiki" links from Foundry.

---

### Approach E: "Companion CLI / Script" — External script reads Foundry data, writes to Obsidian

**Description:** A standalone script (Python, Node, or shell) that reads Foundry's world data (via the Foundry REST API or by parsing `world.json` / compendium files) and generates/updates Obsidian vault notes.

**How it works:**
1. GM runs a script (manually or via cron) that:
   - Reads Foundry world data via the Foundry VTT REST API (if enabled) or by parsing exported JSON.
   - Generates Markdown files with frontmatter.
   - Writes them to the Obsidian vault directory.
2. Optionally runs as a Foundry module hook (server-side) that triggers on data changes.

**Effort:** Medium. Requires:
- A script (Python recommended for ecosystem familiarity).
- Foundry REST API access (requires a world-level API key or admin access).
- Markdown templating (Jinja2 or similar).
- Frontmatter generation.
- Optional: Foundry module hook to trigger the script.

**Pros:**
- Decoupled from Foundry client/server architecture.
- Full control over output format.
- Can be run on any schedule.
- No browser/CORS issues.
- Can be version-controlled alongside the module.

**Cons:**
- Requires Foundry REST API to be enabled (not default).
- Or requires file-system access to Foundry data directory.
- External dependency — GM must install and run the script.
- No live sync unless run frequently.

**Best for:** Tech-savvy GMs who want full control over their Obsidian vault structure.

---

## 5. Comparison Matrix

| Criterion | A: Journal Export | B: File Drop | C: REST API | D: URI Links | E: Companion Script |
|---|---|---|---|---|---|
| **Effort** | Low-Med | Med-High | High | Low | Medium |
| **Direction** | One-way | One-way | One-way | One-way (nav) | One-way |
| **Data scope** | Journals + summary | All structured data | All structured data | Navigation only | All structured data |
| **Obsidian plugin needed** | No | No | Yes (Local REST API) | No | No |
| **Server FS access** | No | Yes | No (localhost) | No | Yes (or REST API) |
| **Works on The Forge** | Yes | No | No | Yes | Maybe (REST API) |
| **Per-entity notes** | No (summary) | Yes | Yes | No | Yes |
| **Live sync** | No | No | Near-real-time | No | Scheduled |
| **GM friction** | Low | Medium | High | Low | Medium |
| **Maintenance burden** | Low | Medium | High | Low | Medium |

---

## 6. Risks

1. **Foundry version coupling.** The module targets Foundry v14. Any integration that depends on Foundry JS APIs (Hooks, JournalEntry, FilePicker) must be kept in sync with Foundry updates. The existing module already handles this risk for its core features.

2. **Data model volatility.** The kingdom data model is actively evolving (population rosters were just added). An export integration must be resilient to schema changes or versioned.

3. **User environment diversity.** GMs run Foundry on Windows, macOS, Linux, The Forge, Molten, self-hosted, Docker. Any approach requiring file-system access or localhost networking excludes a large fraction of users.

4. **Obsidian plugin fragility.** Community plugins (Local REST API) can break on Obsidian updates. Relying on them creates a support burden.

5. **Scope creep.** "Export to Obsidian" can easily expand to "full bidirectional sync with conflict resolution." This spike explicitly recommends against that scope.

6. **Security.** Writing files to arbitrary paths or opening localhost connections from a browser context has security implications. The Foundry sandbox and browser CORS policies exist for good reasons.

---

## 7. Recommendation

**Do not build a dedicated Obsidian integration at this time.** Instead:

### Short term (now):
- **Approach A (Journal Export)** is the lowest-effort, highest-compatibility option. Add a "Export kingdom summary to Journal" button to the Kingdom Sheet. This produces a Journal Entry that existing tools (Obsidian Bridge, fvtt-export-markdown, obsidian-import-foundry) can carry into Obsidian. Effort: ~1-2 days of work.
- **Approach D (URI Links)** is trivial to add alongside it — render `obsidian://open` links in the sheet for quick navigation. Effort: ~0.5 days.

### Medium term (if demand exists):
- **Approach E (Companion Script)** for self-hosted GMs who want per-settlement notes, NPC rosters, and turn logs in their vault. Ship it as an optional Python script in the repo, not as a module feature. This keeps the module clean and avoids the file-system/CORS problems.

### Do NOT pursue:
- **Approach C (REST API)** — the localhost requirement and plugin dependency make it a non-starter for most users.
- **Bidirectional sync** — the complexity is not justified by the use case. Foundry is the source of truth for campaign state; Obsidian is a read-only consumption layer.

### Rationale:
The module's value is in automating Kingmaking mechanics inside Foundry. Obsidian integration is a "nice to have" for campaign note-taking, which is a separate concern. The existing ecosystem (Obsidian Bridge, fvtt-export-markdown, obsidian-import-foundry) already handles the journal-to-Obsidian path. The module should focus on producing good Journal Entry content and let the existing tools handle the rest.

If the GM wants structured data (settlements, NPCs, events) in Obsidian, the companion script approach (E) is the most flexible and least coupled option. It can be developed and maintained independently of the Foundry module release cycle.

---

## 8. What Would Approach A Look Like (Rough Scope)

For estimation purposes only — not a plan:

1. Add `KingdomSummaryExporter.kt` — serializes `KingdomSheetData` to HTML:
   - Kingdom stats (level, size, control DC, RP, commodities)
   - Settlements table (name, level, population, structures)
   - Leaders table (role, name, modifiers, vacancy status)
   - Active events list
   - Army summary
   - Next-turn checklist

2. Add "Export to Journal" button to `KingdomSheet.kt` (next to existing action buttons).

3. On click: `JournalEntry.create({name: "Kingdom Summary - Turn N", content: html})`.

4. Add module settings for:
   - Journal folder name (default: "Kingdom Export")
   - Whether to overwrite or create new entries each export.

5. Add `obsidian://open` link at the top of the sheet (configurable vault name).

Estimated effort: 1-2 days including tests.

---

## 9. References

- Obsidian Bridge: <https://github.com/SoSly/foundryvtt-obsidian-bridge>
- Lava Flow: <https://github.com/Praxxian/lava-flow>
- fvtt-export-markdown: <https://github.com/farling42/fvtt-export-markdown>
- obsidian-import-foundry: <https://github.com/farling42/obsidian-import-foundry>
- Obsidian Local REST API: <https://github.com/coddingtonbear/obsidian-local-rest-api>
- Obsidian URI Protocol: <https://help.obsidian.md/Extending+Obsidian/Obsidian+URI>
- Foundry Journal docs: <https://foundryvtt.com/article/journal/>
- Module data model: `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/`
- Module journal utils: `src/jsMain/kotlin/at/posselt/pfrpg2e/utils/Journal.kt`
